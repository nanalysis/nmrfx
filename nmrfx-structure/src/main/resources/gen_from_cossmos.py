import os 
import sys
from urllib.request import urlopen
import subprocess
import glob

'''
This script will take txt files of two formats
    1. COSSMOS query search results file
    2. A file with two columns - nucleotide_sequence  pdbId_startingResidue 
The files should be named [ssMotif]_[cossmos | cluster].txt 
    ex: gnra_cluster.txt, bulge3_cossmos.txt
The following directories will be written to:
    *a subdirectory of the same name as the motif should be created in each dir
    pdb2 - for files of extracted lines 
    yaml - yaml files with the sequence of interest
    measurements - saved output of nmrfxs measure -suite 
Check vienna sequence, which should represent the only the span of the motif
with two matching parantheses on each end. 
'''

if len(sys.argv) > 1:
    filename = sys.argv[1]
    motif = os.path.split(filename)[-1].split("_")[0]
wc_bp = {'A':'U','U':'A','G':'C','C':'G'} 
#vienna = ["((...((","))))"]
vienna = "((......))"
template_filename = "./template.yaml"
pdb_home = "/data/pdb/" #gz pdb files 
pdb_dir = "/data/fitshifts/pdb" #dir of unzipped pdb files
yaml_dir = os.path.join("yaml",motif)
abbr_pdb_path = "./pdb2" #files with extracted lines from pdb 

#find gz file
def find_pdb(pdbID: str):
    zippedFile = pdb_home + pdbID[1:3] + '/pdb' + pdbID + '.ent.gz'
    pdbFile = os.path.join(pdb_dir+ pdbID + '.pdb')
    if(os.path.exists(zippedFile)):
        with gzip.open(zippedFile,'rb') as fin:
            with open(pdbFile, 'w') as fout:
                shutil.copyfileobj(fin,fout)
    else:
        return False
    return True

#checks if pdb exists and download if it doesn't
def pdb_exists(pdbID: str):
    pdbFile = os.path.join(pdb_dir, pdbID + '.pdb')
    if(not os.path.exists(pdbFile)):
        print('fetch PDB ' +pdbID+'\n')
        if find_pdb(pdbID):
            return True
        else:
            try:
                response = urlopen('http://www.rcsb.org/pdb/files/' + pdbID + '.pdb')
                pdbEntry = response.read()
            except Exception as e:
                print(str(e))
                return False
            with open(pdbFile, 'w') as tmp_file:
                    tmp_file.write(pdbEntry.decode('utf-8'))
    return True

def parse_cossmos(limit=25,extend=2):
    '''
    returns the pdbId, first seq, second seq, start residue num for seq a, and start residue num for seq b
    '''
    results = []
    with open(filename, 'r') as f:
        headers = f.readline().strip('\n').split('\t')
        for lineNum, line in enumerate(f):
            print(lineNum)
            if lineNum == limit:
                break
            line = line.strip('\n').split('\t')
            seq_start = []
            res_range = []
            if "Bseq" in headers:
                pdb, aseq, bseq, aseq_start, bseq_start = line[1:6]
                seq_start.append(int(bseq_start.split("-")[-2].split()[-1])) 
                res_range.append(len(bseq)+extend) #get read range and extend on each end 
                closing_bp = aseq[:2] + aseq[-2:] 
                sequence = aseq + bseq[::-1]
            else:
                pdb, aseq, aseq_start = line[1:4]
                closing_bp = aseq[:2]
                bseq = aseq[-1:-3:-1]
                sequence = aseq
            seq_start.insert(0,int(aseq_start.split("-")[0])) 
            res_range.insert(0,len(aseq)+extend)
            cWW = [seq1 == wc_bp[seq2] for seq1, seq2 in zip(closing_bp,bseq)]
            if all(cWW):
                result = [pdb,sequence,seq_start,res_range]
                results.append(result)
    return results 

def extract_from_pdb(pdb,seq_start,res_range):
    '''
    extracts lines of residues of interest from original pdb
    writes to file at abbr_pdb path
    sequence start and end is moved one residue before and after, respectively 
    '''
    write = False
    count = 0 #counts residues read
    end = False #exit after finding both sequences

    irange = res_range[0] #range of residues read
    if len(seq_start) > 1:
        aseq_start, bseq_start = seq_start
    else:
        aseq_start = seq_start[0]
        bseq_start = None
        end = True
        
    start = aseq_start - 1 #starting res num

    pdbFilePath = os.path.join(pdb_dir, pdb + '.pdb')
    pdbFile = open(pdbFilePath,'r')
    abbr_pdb = os.path.join(abbr_pdb_path,motif,pdb + '_' + str(aseq_start) + '.pdb')
    f = open(abbr_pdb,'w')

    if bseq_start and min(aseq_start,bseq_start) != aseq_start:
        start = bseq_start - 1
        irange = res_range[1]

    for line in pdbFile:
        cols = line.split()
        if line.startswith('ATOM'): 
            if not write and len(cols[3]) == 1 and len(cols[2]) < 4: #filter out amino acid lines 
                if str(start) in cols[4]:
                    nCol = 4
                elif str(start) in cols[5]:
                    nCol = 5
                else:
                    continue
                f.write(line)
                write = True #start lines of interest 
            elif write and count < irange:
                if str(start) in cols[nCol]:
                    f.write(line)
                else:
                    count += 1
                    start += 1
                    if count < irange:
                        f.write(line)
                    else:
                        if end:
                            break
                        write = False
                        start = bseq_start - 1
                        count = 0
                        irange = res_range[1]
                        end = True
                        if min(aseq_start,bseq_start) != aseq_start:
                            start = aseq_start - 1
                            irange = res_range[0]
    pdbFile.close()
    f.close()

def create_template():
    '''
    read template yaml file into memory
    '''
    template = []
    with open(template_filename,'r') as f:
        template = [line for line in f]
    return template

def prep(write_yaml=True,extract_pdb=True,measure=True):
    '''
    runs the entire workflow
    '''
    if "cossmos" in filename:
        results = parse_cossmos()
    else:
        results = get_from_table()
    for result in results:
        pdb, sequence, seq_start, res_range = result
        if not pdb_exists(pdb):
            continue 
        if write_yaml:
            gen_yaml(pdb,sequence,seq_start)
        if extract_pdb:
            extract_from_pdb(pdb,seq_start,res_range)
    if measure:
        call_measure()

def gen_yaml(pdb,sequence,seq_start):
    '''
    generate new yaml file for sequence of interest
    '''
    template = create_template()
    seq_start = seq_start[0]
    yaml_filename = os.path.join(yaml_dir,pdb+"_"+str(seq_start)+".yaml")
    write_vienna = True
    seqTemp = ['CUGAC', 'GGAGUAAUCC', 'GUCAG']
    viennaTemp = ['(((((', '(((....)))', ')))))']
    with open(yaml_filename, 'w') as outf:
        for line in template:
            if "sequence" in line:
                line = line.split(":") 
                if "bulge" in motif:
                    line = line[0] + ": " + seqTemp[0] + sequence[:-4] + seqTemp[1] + sequence[-4:] + seqTemp[2] + "\n"
                else:
                    line = line[0] + ": " + seqTemp[0] + sequence + seqTemp[-1] + "\n"
            if write_vienna and "vienna" in line:
                line = line.split(":")
                if "bulge" in motif:
                    line = line[0] + ": " + viennaTemp[0] + vienna[0] + viennaTemp[1] + vienna[1] + viennaTemp[2] + "\n"
                else: 
                    line = line[0] + ": " + viennaTemp[0] + vienna + viennaTemp[-1] + "\n"
                write_vienna = False
            outf.write(line)

def call_measure(pdb=None):
    '''
    measures the structures in the entire directory unless given a specific pdb 
    '''
    nmrfxs = "/data/nmrfx/nmrfx/nmrfx-structure/target/nmrfx-structure-11.3.1-SNAPSHOT-bin/nmrfx-structure-11.3.1-SNAPSHOT/nmrfxs"
    if pdb:
        tail = pdb+"_*.pdb"
    else:
        tail = "*.pdb"
    
    pdb_files = glob.glob(os.path.join(abbr_pdb_path,motif,tail))
    for pdb_file in pdb_files:
        angles_file = os.path.join("measurements", motif, os.path.split(pdb_file)[-1][:-4]+".txt")
        f = open(angles_file,'w')
        subprocess.call([nmrfxs,"measure","-suite",pdb_file],stdout=f)

def get_from_table(extend=2):
    '''
    pdb samples from the Richardson paper
    Identification and characterization of new RNA tetraloop sequence families
    residue range is extended by 2 on each end
    '''
    results = []
    with open(filename,"r") as f:
        for line in f:
            line = line.strip("\n").split()
            sequence = line[0]
            pdb, seq_start = line[-1].split("_") 
            seq_start = [int(seq_start[1:])-2]
            res_range = [len(sequence) + 2*extend] 
            results.append([pdb, sequence, seq_start, res_range])
    return results

prep()
