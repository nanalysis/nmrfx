
from org.tribuo import MutableDataset
from org.tribuo.data.csv import CSVLoader
from org.tribuo.evaluation import TrainTestSplitter
from org.tribuo.evaluation import CrossValidation
from org.tribuo.regression import RegressionFactory
from org.tribuo.regression import Regressor
from org.tribuo.regression.evaluation import RegressionEvaluator
from org.tribuo.regression.slm import LARSLassoTrainer
from  java.nio.file import Paths
from java.lang import Double

from org.nmrfx.structure.chemistry.energy import PropertyGenerator
from org.nmrfx.structure.chemistry.predict import ProteinPredictor
from org.nmrfx.structure.chemistry.predict import Predictor
from org.nmrfx.structure.chemistry.predict import ProteinPredictorTrainer
from org.nmrfx.structure.chemistry import Molecule
from org.apache.commons.math3.linear import Array2DRowRealMatrix
from org.nmrfx.structure.chemistry import SmithWatermanBioJava
from org.nmrfx.structure.project import StructureProject
from org.nmrfx.chemistry.io import NMRStarWriter
import array
import molio
import star
import os
import os.path
import glob
import math
import sys
import re
import gzip
import shutil
from itertools import izip
import argparse

proteinPredictor = None

#set directories
bmrbHome = '/data/star3/bmr'
bmrbHome = '/mnt/data/star3a/bmr'
pdbHome = '../pdb/'

#local directory for unzipped pdb files
pdbDir = '../pdb/'

#set paths
homeDir = '../output/080620'
datasetFileName = 'train.txt'
datasetFileName = 'train_and_test_dataset_clean.txt'
testSetFile = 'testSet.txt'

useContacts = True
reportAtom = None
reportAtom = "1:1.CA"
lamVal = 0.005

#select which group of atom types to generate attributes for
nativeAtoms = ["C", "CA", "CB", "CG", "CD", "CE", "CZ", "H", "HA", "HB", "HG", "HD", "HE", "HZ", "N"]
atoms = ["MHB", "MHG", "MHD", "MHE", "MCB", "MCG", "MCD","MCE", "ACE","ACD","AHD","AHE","C", "CA", "CB", "CG", "CD", "CE", "ACZ", "H", "HA", "HB", "HG", "HD", "HE", "AHZ", "N"]
aaS = ['ALA', 'ARG', 'ASN', 'ASP', 'CYS', 'GLU', 'GLN', 'GLY', 'HIS', 'ILE', 'LEU', 'LYS', 'MET', 'PHE', 'PRO', 'SER', 'THR', 'TRP', 'TYR', 'VAL']
attrDir = 'baseAttrs'
aaGroups = {}

aaGroups['C'] = [['ASP', 'ASN'], ['GLU', 'GLN'], ['HIS', 'TRP', 'PHE', 'TYR'], ['ALA'], ['PRO'], ['GLY'], ['ILE'], ['VAL'], ['LEU'], ['THR'], ['CYS', 'SER'], ['ARG', 'LYS'], ['MET']]
aaGroups['CA'] = [['ASP', 'ASN'], ['GLU', 'GLN'], ['HIS', 'TRP', 'PHE', 'TYR'], ['ALA'], ['PRO'], ['GLY'], ['ILE'], ['VAL'], ['LEU'], ['THR'], ['CYS', 'SER'], ['MET'], ['ARG', 'LYS']]
aaGroups['CB'] = [['ASP', 'ASN'], ['GLU', 'GLN'], ['HIS', 'TRP', 'PHE', 'TYR'],  ['PRO'], ['ILE'], ['VAL'], ['LEU'], ['THR'], ['CYS'], ['SER'], ['MET'], ['ARG', 'LYS']]
aaGroups['CD'] = [['LYS'],['ARG'], ['PRO']]
aaGroups['ACD'] = [['HIS'], ['TRP'],[ 'PHE', 'TYR']]
aaGroups['ACD'] = [['TRP'], ['PHE', 'TYR']]
aaGroups['CE'] = [['LYS']]
aaGroups['ACE'] = [['PHE', 'TYR'],['HIS']]
aaGroups['ACE'] = [['PHE', 'TYR']]
aaGroups['ACZ'] = [['PHE']]
aaGroups['CG'] = [['GLU'], ['GLN'], ['VAL'], ['LEU'], ['ILE'], ['PRO'], ['ARG'], ['LYS'], ['MET']]
aaGroups['H'] = [['ALA', 'GLY'], ['ASP'], ['ASN'], ['GLU'], ['GLN'], ['HIS'], ['TRP'], ['PHE'], ['TYR'], ['ILE'], ['VAL'], ['LEU'], ['THR'], ['SER'], ['CYS'], ['ARG'], ['LYS'], ['MET']]
aaGroups['HA'] = [['ALA'], ['GLY'], ['ASP'], ['ASN'], ['GLU'], ['GLN'], ['HIS'], ['TRP'], ['PHE'], ['TYR'], ['ILE'], ['VAL'], ['LEU'], ['THR'], ['SER'], ['CYS'], ['ARG'], ['LYS'], ['MET'], ['PRO']]
aaGroups['HB'] = [['ASP'], ['ASN'], ['GLU'], ['GLN'], ['HIS'], ['TRP'], ['PHE'], ['TYR'], ['ALA'], ['ILE'], ['VAL'], ['LEU'], ['THR'], ['SER'], ['CYS'], ['ARG'], ['LYS'], ['MET'], ['PRO']]
aaGroups['HD'] = [['ARG'],  ['PRO'], ['LEU'], ['LYS']]
aaGroups['AHD'] = [['HIS'], ['TRP', 'PHE', 'TYR']]
aaGroups['AHD'] = [['TRP'], ['PHE', 'TYR']]
aaGroups['AHE'] = [['HIS'], ['PHE', 'TYR']]
aaGroups['AHE'] = [['PHE', 'TYR']]
aaGroups['HE'] = [['ARG'], ['LYS']]
aaGroups['HG'] = [['GLU'], ['GLN'], ['VAL'], ['LEU'], ['ILE'], ['PRO'], ['ARG'], ['LYS'], ['MET']]
aaGroups['AHZ'] = [['PHE']]
aaGroups['MCB'] = [['ALA']]
aaGroups['MCD'] = [['ILE'], ['LEU']]
aaGroups['MCG'] = [['ILE'], ['THR'], ['VAL']]
aaGroups['MHB'] = [['ALA']]
aaGroups['MHD'] = [['ILE'], ['LEU']]
aaGroups['MHG'] = [['ILE'], ['THR'], ['VAL']]
aaGroups['MHE'] = [['MET']]
aaGroups['MCE'] = [['MET']]
aaGroups['N'] = [['ALA'], ['ARG'], ['ASN'], ['ASP'], ['CYS'], ['GLN'], ['GLU'], ['GLY'], ['HIS'], ['ILE'], ['LEU'], ['LYS'], ['MET'], ['PHE'], ['SER'], ['THR'], ['TRP'], ['TYR'], ['VAL']]
atoms = ["MHB",  "ACE","ACD","AHD","AHE","C", "CA", "CB", "CG", "CD", "CE", "ACZ", "H", "HA", "HB", "HG", "HD", "HE", "AHZ", "N"]



#min and max values used for disorder calculation
minMaxSums = {'N':[97.37,752.63],
    'CA':[191.66,1550.0],
    'CB':[209.09,1254.55],
    'C':[800.0,1100.0],
    'H':[483.33,1788.89],
    'HA':[431.58,3178.95],
    'HB':[500.0,2000.0],
    'HG':[500.0,2000.0],
    'HD':[500.0,2000.0],
    'AHD':[500.0,2000.0],
    'HE':[500.0,2000.0],
    'AHE':[500.0,2000.0],
    'CG':[500.0,2000.0],
    'CD':[500.0,2000.0],
    'ACD':[500.0,2000.0],
    'CE':[500.0,2000.0],
    'ACE':[500.0,2000.0],
    'MCB':[500.0,2000.0],
    'MCG':[500.0,2000.0],
    'MCD':[500.0,2000.0],
    'MCE':[500.0,2000.0],
    'MHB':[500.0,2000.0],
    'MHG':[500.0,2000.0],
    'MHD':[500.0,2000.0],
    'MHE':[500.0,2000.0],
    'ACZ':[500.0,2000.0],
    'AHZ':[500.0,2000.0]}

#used in generating attr files
iLine = 0

#find gz file
def findPDB(pdbID, localPdbFile):
    zippedFile = os.path.join(pdbHome, pdbID[1:3], 'pdb' + pdbID + '.ent.gz')
    if(os.path.exists(zippedFile)):
        with gzip.open(zippedFile,'rb') as fin:
            with open(localPdbFile, 'w') as fout:
                shutil.copyfileobj(fin,fout)
    else:
        return False
    return True

#checks if pdb exists and download if it doesn't
def pdbExists(pdbID):
    localPdbFile = os.path.join(pdbDir, pdbID + '.pdb')
    if(not os.path.exists(localPdbFile)):
        print 'pdb not in local directory'
        if findPDB(pdbID, localPdbFile):
            print 'pdb fetched'
            return True
        else:
            try:
                response = urllib2.urlopen('http://www.rcsb.org/pdb/files/' + pdbID + '.pdb')
                pdbEntry = response.read()
            except:
                print 'could not fetch pdb'
                return False
            with open(localPdbFile, 'w') as tmp_file:
                    tmp_file.write(pdbEntry)
    return True

def getPDBFile(pdbID):
    if pdbExists(pdbID):
        return os.path.join(pdbDir, pdbID + '.pdb')
    return None

def getSaveFrame(svfs,catName):
    for svfName in svfs:
       svf = svfs[svfName]
       if svf.getCategoryName() == catName:
           return svf
    return None

#star.read() will not return error if path/filename does not exist
def loadBMRB(bmrbId):
    fileName = os.path.join(bmrbHome + bmrbId, 'bmr' + bmrbId + '_3.str')
    if not os.path.exists(fileName):
        print 'cannot locate star file, check path', fileName
    star3 = star.read(fileName)
    svfs = star3.getSaveFrames()
    svf = getSaveFrame(svfs,'assembly')
    paramagnetic = svf.getCategory('_Assembly').get('Paramagnetic')
    if paramagnetic == "no":
        mol = Molecule.getActive()
    else:
        mol = None
    if mol != None:
        svf = getSaveFrame(svfs,'entry_information')
        subType = svf.getCategory('_Entry').get('Experimental_method_subtype')
        subType = subType.lower()
        if 'solid' in subType:
            mol = None
        elif 'state' in subType:
            mol = None
        elif 'magic' in subType:
            mol = None
        elif 'theor' in subType:
            mol = None
    return mol

def getUseFields2(fields, aName, aa, residues):
    use = []
    skipChi = False
    skipChi2 = False
    if aa in ['ALA','GLY','PRO']:
        skipChi2 = True
        skipChi = True
    if aa in ['SER','CYS','THR','ASP','ASN','SER','VAL']:
        skipChi2 = True
    for (i,field) in enumerate(fields):
        ok = True
        if skipChi2 and field.find('chi2C') != -1:
            ok = False
        if skipChi and field.find('chiC') != -1:
            ok = False
        if (not 'CYS' in residues)  and field == "DIS":
            ok = False
        if not (aName.startswith("H") or aName.startswith("M")) and field == "h3":
            ok = False
        if field in aaS:
            if not field in residues[1:]:
                ok = False
        if ok:
            use.append(i)
    return use

def disorderCalc(contactSum, minS, maxS):
    if not useContacts:
        return 1.0
    sValue = -2.0 * (contactSum - minS) / (maxS - minS)
    eValue = math.exp(sValue)
    return (1.0 - eValue) / (1.0 + eValue)

def filterAttr(fileName, outFileName, use, aName, aa, residues, errDict, trimDict, testMode):
    global iLine
    global homeDir
    writeHeader = False
    if not os.path.exists(outFileName):
        writeHeader = True
    use = None
    dir, file = os.path.split(fileName)
    mapFileName = os.path.join(homeDir, "maps", file)
    if testMode:
        testpdbs = getTestSet()
    hMode = aName[0] == 'H' or aName[0] == 'M'
    with open(outFileName, 'a') as fOut, open(mapFileName, 'a') as fOut2:
        with open(fileName, 'r') as f1:
            header = f1.readline().strip()
            header = header.strip()
            headerFields = header.split('\t')

            for line in f1:
               line = line.strip()
               if len(line) > 0:
                   fields = line.split('\t')
                   pdbID = fields[0]
                   if testMode and not pdbID in testpdbs:
                       continue
                   elif not testMode and pdbID in testpdbs:
                       continue
                   atomName = fields[2]
                   #check whether atom should be trimmed
                   if trimDict and (pdbID, atomName) in trimDict[aName]:
                      continue
                   v = PropVar2(headerFields, fields)
                   if (v.N1 > 0.1):
                       continue
                   if (v.C1 > 0.1):
                       continue
                   if errDict and pdbID in errDict:
                       v.cs -= float(errDict[pdbID])
                   a = getAttr(v, aa, hMode)
                   fields = []
                   names = []
                   for (name,value) in zip(a[0::2],a[1::2]):
                       fields.append(str(value))
                       names.append(str(name))
                   if use == None:
                       use = getUseFields2(names, aName, aa, residues)
                   if writeHeader:
                       firstHead = True
                       for i in use:
                           if not firstHead:
                               fOut.write('\t')
                           else:
                               firstHead = False
                           fOut.write(names[i])
                       fOut.write('\n')
                       writeHeader = False
                   first = 1
                   for i in use:
                       if not first:
                           fOut.write('\t')
                       else:
                           first = 0
                       fOut.write(fields[i])
                   fOut.write('\n')
                   fOut2.write('{}\t{}\n'.format(pdbID, atomName))
                   iLine += 1

def writeHeader(fileName, a):
    with open(fileName,"w") as f1:
        first = 1
        for (name,value) in zip(a[0::2],a[1::2]):
            if not first:
                f1.write(',')
            else:
                first = 0
            f1.write(name)
        f1.write('\n')

def writeBaseHeader(fileName, a):
    with open(fileName,"w") as f1:
        f1.write('pdb\tres\tatom')
        for (name,value) in zip(a[0::2],a[1::2]):
            f1.write('\t')
            f1.write(name)
        f1.write('\n')

def saveBaseAttr(pdbID, atom, mode, attrDir, rootAName, resName, a):
    global homeDir
    fileName = os.path.join(homeDir, attrDir, mode + "_" + rootAName + "_" + resName + ".txt")
    if not os.path.exists(fileName):
        writeBaseHeader(fileName, a)
    with open(fileName, 'a') as f1:
        f1.write(pdbID + '\t' + resName + '\t' + atom.getShortName())
        for (name, value) in zip(a[0::2], a[1::2]):
            f1.write('\t')
            if value == None:
                f1.write('nan')
            elif math.isnan(value):
                f1.write('nan')
            else:
                svalue = "%.5f" % (value)
                f1.write(svalue)
        f1.write('\n')

def isRes(resName, resTest):
    if resName == resTest:
        return 1.0
    else:
        return 0.0

def safeSin(v):
    if math.isnan(v):
        return 0.0
    else:
        return math.sin(v)

def safeCos(v):
    if math.isnan(v):
        return 0.0
    else:
        return math.cos(v)

def getAttr(v, resName, hMode):
    a = []
    hMode = False
    if useContacts:
        a += ['ring',v.ring]
        if hMode:
            a += ['hshift3',v.hshift3]
            a += ['hshift2',v.hshift2]
            a += ['hshift1',v.hshift1]
            a += ['eshift',v.eshift]

    a += ['cos(chiC)',safeCos(v.chiC)]
    a += ['cos(chi2C)',safeCos(v.chi2C)]
    a += ['cos(2*chiC)',safeCos(2*v.chiC)]
    a += ['cos(2*chi2C)',safeCos(2*v.chi2C)]
    a += ['sin(chiC)',safeSin(v.chiC)]
    a += ['sin(chi2C)',safeSin(v.chi2C)]
    a += ['sin(2*chiC)',safeSin(2*v.chiC)]
    a += ['sin(2*chi2C)',safeSin(2*v.chi2C)]

    a += ['sin(chiC)*sin(chi2C)',safeSin(v.chiC)*safeSin(v.chi2C)]
    a += ['cos(chiC)*sin(chi2C)',safeCos(v.chiC)*safeSin(v.chi2C)]
    a += ['sin(chiC)*cos(chi2C)',safeSin(v.chiC)*safeCos(v.chi2C)]
    a += ['cos(chiC)*cos(chi2C)',safeCos(v.chiC)*safeCos(v.chi2C)]

    a += ['sin(phiC)*cos(chiC)',safeSin(v.phiC)*safeCos(v.chiC)]
    a += ['sin(phiC)*cos(chi2C)',safeSin(v.phiC)*safeCos(v.chi2C)]
    a += ['sin(phiC)*sin(chiC)',safeSin(v.phiC)*safeSin(v.chiC)]
    a += ['sin(phiC)*sin(chi2C)',safeSin(v.phiC)*safeSin(v.chi2C)]
    a += ['cos(phiC)*sin(chiC)',safeCos(v.phiC)*safeSin(v.chiC)]
    a += ['cos(phiC)*sin(chi2C)',safeCos(v.phiC)*safeSin(v.chi2C)]
    a += ['cos(phiC)*cos(chiC)',safeCos(v.phiC)*safeCos(v.chiC)]
    a += ['cos(phiC)*cos(chi2C)',safeCos(v.phiC)*safeCos(v.chi2C)]

    a += ['sin(psiC)*cos(chiC)',safeSin(v.psiC)*safeCos(v.chiC)]
    a += ['sin(psiC)*cos(chi2C)',safeSin(v.psiC)*safeCos(v.chi2C)]
    a += ['sin(psiC)*sin(chiC)',safeSin(v.psiC)*safeSin(v.chiC)]
    a += ['sin(psiC)*sin(chi2C)',safeSin(v.psiC)*safeSin(v.chi2C)]
    a += ['cos(psiC)*sin(chiC)',safeCos(v.psiC)*safeSin(v.chiC)]
    a += ['cos(psiC)*sin(chi2C)',safeCos(v.psiC)*safeSin(v.chi2C)]
    a += ['cos(psiC)*cos(chiC)',safeCos(v.psiC)*safeCos(v.chiC)]
    a += ['cos(psiC)*cos(chi2C)',safeCos(v.psiC)*safeCos(v.chi2C)]

    a += ['cos(phiC)',safeCos(v.phiC)]
    a += ['sin(phiC)',safeSin(v.phiC)]
    a += ['cos(2*phiC)',safeCos(2*v.phiC)]
    a += ['sin(2*phiC)',safeSin(2*v.phiC)]
    a += ['cos(3*phiC)',safeCos(3*v.phiC)]
    a += ['sin(3*phiC)',safeSin(3*v.phiC)]
    a += ['cos(psiC)',safeCos(v.psiC)]
    a += ['sin(psiC)',safeSin(v.psiC)]
    a += ['cos(2*psiC)',safeCos(2*v.psiC)]
    a += ['sin(2*psiC)',safeSin(2*v.psiC)]
    a += ['cos(3*psiC)',safeCos(3*v.psiC)]
    a += ['sin(3*psiC)',safeSin(3*v.psiC)]

    a += ['cos(psiC)*cos(phiC)',safeCos(v.psiC)*safeCos(v.phiC)]
    a += ['cos(psiC)*sin(phiC)',safeCos(v.psiC)*safeSin(v.phiC)]
    a += ['sin(psiC)*cos(phiC)',safeSin(v.psiC)*safeCos(v.phiC)]
    a += ['sin(psiC)*sin(phiC)',safeSin(v.psiC)*safeSin(v.phiC)]
#######output/02 all
#output/03
    a += ['cos(psiC)*cos(psiP)',safeCos(v.psiC)*safeCos(v.psiP)]
    a += ['cos(psiC)*sin(psiP)',safeCos(v.psiC)*safeSin(v.psiP)]
    a += ['sin(psiC)*cos(psiP)',safeSin(v.psiC)*safeCos(v.psiP)]
    a += ['sin(psiC)*sin(psiP)',safeSin(v.psiC)*safeSin(v.psiP)]
#output/05
    #a += ['cos(phiC)*cos(phiS)',safeCos(v.phiC)*safeCos(v.phiS)]
    #a += ['cos(phiC)*sin(phiS)',safeCos(v.phiC)*safeSin(v.phiS)]
    #a += ['sin(phiC)*cos(phiS)',safeSin(v.phiC)*safeCos(v.phiS)]
    #a += ['sin(phiC)*sin(phiS)',safeSin(v.phiC)*safeSin(v.phiS)]
#output/04
    #a += ['cos(psiC)*cos(phiS)',safeCos(v.psiC)*safeCos(v.phiS)]
    #a += ['cos(psiC)*sin(phiS)',safeCos(v.psiC)*safeSin(v.phiS)]
    #a += ['sin(psiC)*cos(phiS)',safeSin(v.psiC)*safeCos(v.phiS)]
    #a += ['sin(psiC)*sin(phiS)',safeSin(v.psiC)*safeSin(v.phiS)]
#output/06
    #a += ['cos(chiC)*cos(psiP)',safeCos(v.chiC)*safeCos(v.psiP)]
    #a += ['cos(chiC)*sin(psiP)',safeCos(v.chiC)*safeSin(v.psiP)]
    #a += ['sin(chiC)*cos(psiP)',safeSin(v.chiC)*safeCos(v.psiP)]
    #a += ['sin(chiC)*sin(psiP)',safeSin(v.chiC)*safeSin(v.psiP)]
#output/07
    #a += ['cos(chiC)*ARO_P',safeCos(v.chiC)*v.ARO_P]
    #a += ['cos(chiC)*BULK_P',safeCos(v.chiC)*v.BULK_P]
    #a += ['cos(chiC)*CHRG_P',safeCos(v.chiC)*v.CHRG_P]
    #a += ['cos(chiC)*HPHB_P',safeCos(v.chiC)*v.HPHB_P]
    #a += ['cos(chiC)*PRO_P',safeCos(v.chiC)*v.PRO_P]
    #a += ['sin(chiC)*ARO_P',safeSin(v.chiC)*v.ARO_P]
    #a += ['sin(chiC)*BULK_P',safeSin(v.chiC)*v.BULK_P]
    #a += ['sin(chiC)*CHRG_P',safeSin(v.chiC)*v.CHRG_P]
    #a += ['sin(chiC)*HPHB_P',safeSin(v.chiC)*v.HPHB_P]
    #a += ['sin(chiC)*PRO_P',safeSin(v.chiC)*v.PRO_P]
######
    a += ['cos(phiC)*ARO_P',safeCos(v.phiC)*v.ARO_P]
    a += ['cos(phiC)*BULK_P',safeCos(v.phiC)*v.BULK_P]
    a += ['cos(phiC)*CHRG_P',safeCos(v.phiC)*v.CHRG_P]
    a += ['cos(phiC)*HPHB_P',safeCos(v.phiC)*v.HPHB_P]
    a += ['cos(phiC)*PRO_P',safeCos(v.phiC)*v.PRO_P]
    a += ['sin(phiC)*ARO_P',safeSin(v.phiC)*v.ARO_P]
    a += ['sin(phiC)*BULK_P',safeSin(v.phiC)*v.BULK_P]
    a += ['sin(phiC)*CHRG_P',safeSin(v.phiC)*v.CHRG_P]
    a += ['sin(phiC)*HPHB_P',safeSin(v.phiC)*v.HPHB_P]
    a += ['sin(phiC)*PRO_P',safeSin(v.phiC)*v.PRO_P]

    a += ['cos(psiC)*ARO_S',safeCos(v.psiC)*v.ARO_S]
    a += ['cos(psiC)*BULK_S',safeCos(v.psiC)*v.BULK_S]
    a += ['cos(psiC)*CHRG_S',safeCos(v.psiC)*v.CHRG_S]
    a += ['cos(psiC)*HPHB_S',safeCos(v.psiC)*v.HPHB_S]
    a += ['cos(psiC)*PRO_S',safeCos(v.psiC)*v.PRO_S]
    a += ['sin(psiC)*ARO_S',safeSin(v.psiC)*v.ARO_S]
    a += ['sin(psiC)*BULK_S',safeSin(v.psiC)*v.BULK_S]
    a += ['sin(psiC)*CHRG_S',safeSin(v.psiC)*v.CHRG_S]
    a += ['sin(psiC)*HPHB_S',safeSin(v.psiC)*v.HPHB_S]
    a += ['sin(psiC)*PRO_S',safeSin(v.psiC)*v.PRO_S]

    a += ['cos(phiS)',safeCos(v.phiS)]
    a += ['sin(phiS)',safeSin(v.phiS)]
    a += ['cos(2*phiS)',safeCos(2*v.phiS)]
    a += ['sin(2*phiS)',safeSin(2*v.phiS)]
    a += ['cos(3*phiS)',safeCos(3*v.phiS)]
    a += ['sin(3*phiS)',safeSin(3*v.phiS)]
    a += ['cos(psiS)',safeCos(v.psiS)]
    a += ['sin(psiS)',safeSin(v.psiS)]
    a += ['cos(2*psiS)',safeCos(2*v.psiS)]
    a += ['sin(2*psiS)',safeSin(2*v.psiS)]
    a += ['cos(3*psiS)',safeCos(3*v.psiS)]
    a += ['sin(3*psiS)',safeSin(3*v.psiS)]

    a += ['cos(phiP)',safeCos(v.phiP)]
    a += ['sin(phiP)',safeSin(v.phiP)]
    a += ['cos(2*phiP)',safeCos(2*v.phiP)]
    a += ['sin(2*phiP)',safeSin(2*v.phiP)]
    a += ['cos(3*phiP)',safeCos(3*v.phiP)]
    a += ['sin(3*phiP)',safeSin(3*v.phiP)]
    a += ['cos(psiP)',safeCos(v.psiP)]
    a += ['sin(psiP)',safeSin(v.psiP)]
    a += ['cos(2*psiP)',safeCos(2*v.psiP)]
    a += ['sin(2*psiP)',safeSin(2*v.psiP)]
    a += ['cos(3*psiP)',safeCos(3*v.psiP)]
    a += ['sin(3*psiP)',safeSin(3*v.psiP)]

    #a += ['cos(omega)',safeCos(v.omega)]
    #a += ['sin(omega)',safeSin(v.omega)]
    a += ['DIS',v.DIS]
    for aa in aaS:
        a += [aa,isRes(resName,aa)]
#    a += ['h3',v.h3]
    #a += ['methyl',v.methyl]
    #a += ['fRandom',v.fRandom]
    #a += ['acs',v.acs]
    #a += ['acscorr',v.acscorr]
    a += ['cs',v.cs]
#    a += ['contacts',v.contacts]
    return a

def getBaseAttr(values, resName, proP, proS):
    a = []
    for key in values:
        a += [key, values[key]]
    for aa in aaS:
        a += [aa, isRes(resName, aa)]
    a += ['PRO', isRes(resName, 'PRO')]
    a += ['PRO_P',  proP]
    a += ['PRO_S',  proS]
    return a

class PropVar:
    def __init__(self, values, resName, proP, proS):
        for key in values:
            self.__dict__[key] = values[key]
        self.__dict__['PRO'] = isRes(resName,'PRO')
        self.__dict__['PRO_P'] = proP
        self.__dict__['PRO_S'] = proS

class PropVar2:
    def __init__(self, header, values):
        for key,value in zip(header, values):
            if key != "pdb" and key != "atom" and key != "res":
                value = float(value)
            self.__dict__[key] = value

def isStdRes(polymer):
    standardResList = ['A', 'G', 'L', 'I', 'P', 'V', 'F', 'W', 'Y', 'D', 'E', 'R', 'H', 'K', 'S', 'T', 'C', 'M', 'N', 'Q']
    for letter in polymer.getOneLetterCode():
        if str(letter) not in standardResList:
            return False
    return True

def alignPair(pdbMol, bmrbMol):
    highestSeqIdentity = 0.85
    highestNumIdentical = None
    bestPdbPolymer = None
    bestBmrbPolymer = None
    alignment = None
    for bmrbPolymer in bmrbMol.getPolymers():
        if not isStdRes(bmrbPolymer) or bmrbPolymer is None:
            print 'non std res in bmrb'
            continue
        for pdbPolymer in pdbMol.getPolymers() or pdbPolymer is None:
            print pdbPolymer.getOneLetterCode
            if not isStdRes(pdbPolymer):
                print 'non std res in pdb', pdbPolymer.getOneLetterCode()
                continue
            print 'pdb polymer name: ', pdbPolymer.getName()
            print 'pdb polymer seq: ', pdbPolymer.getOneLetterCode()
            print 'bmrb polymer name: ', bmrbPolymer.getName()
            print 'bmrb polymer seq: ', bmrbPolymer.getOneLetterCode()
            sw = SmithWatermanBioJava(pdbPolymer.getOneLetterCode(), bmrbPolymer.getOneLetterCode())
            swp = sw.doAlignment()
            numIdentical = float(swp.getNumIdenticals())
            seqIdentity = numIdentical / min(len(pdbPolymer.getOneLetterCode()), len(bmrbPolymer.getOneLetterCode()))
            if seqIdentity >= highestSeqIdentity and numIdentical > highestNumIdentical:
                highestSeqIdentity = seqIdentity
                highestNumIdentical = numIdentical
                bestPdbPolymer = pdbPolymer.getName()
                bestBmrbPolymer = bmrbPolymer.getName()
                alignment = sw.getA()

    print 'seq identity', highestSeqIdentity, 'pdb polymer:', bestPdbPolymer, 'bmrb polymer:', bestBmrbPolymer
    return bestPdbPolymer, bestBmrbPolymer, alignment

def setChemShifts(pdbMol, bmrbMol, refSet='R'):
    pdbChain, bmrbChain, alignIndex = alignPair(pdbMol, bmrbMol)
    if pdbChain is None or bmrbChain is None:
        return None, None
    pdbResidues = pdbMol.getEntity(pdbChain).getResidues()
    bmrbResidues = bmrbMol.getEntity(bmrbChain).getResidues()
    for atom in pdbMol.getAtoms():
        atom.setPPMValidity(refSet, False)
    for pdbResidue, index in izip(pdbResidues, alignIndex):
        if index is None:
            continue
        bmrbResidue = bmrbResidues[index - 1]
        pdbResName = pdbResidue.getName()
        bmrbResName = bmrbResidue.getName()
        if pdbResName == bmrbResName:
            for bmrbAtom in bmrbResidue.getAtoms():
                aName = bmrbAtom.getName()

                pdbResNum = pdbResidue.getNumber()
                pdbAtomName = pdbChain + ':' + str(pdbResNum) + '.' + aName
                pdbAtom = pdbMol.findAtom(pdbAtomName)
                if pdbAtom is None:
                    continue

                ppm = bmrbAtom.getPPM()
                if ppm is None:
                    continue
                print(aName, ppm, pdbAtom)
                if refSet == 'R':
                    pdbAtom.setRefPPM(ppm)
                else:
                    pdbAtom.setPPM(refSet, ppm)
                print('pdbatom', pdbAtom, pdbAtom.getPPM()) 

    return pdbChain, bmrbChain

def getAtomNameType(atom):
    result = None
    aName = atom.getName()
    aLen = len(aName)
    if atom.isMethyl():
        if atom.getAtomicNumber() == 1:
            if atom.isFirstInMethyl():
                result = "MH" + aName[1]
    elif atom.isMethylCarbon():
        result = "MC"+aName[1]
    elif atom.isAAAromatic():
        if atom.getAtomicNumber() == 1:
            result = "AH"+aName[1]
        else:
            result = "AC"+aName[1]
    else:
       if aLen > 2:
          aName = aName[0:2]
       if aName in atoms:
           result = aName
    if not result in atoms:
        result = None
    return result

#loads molecule object and generates attr file
def doProtein(mol, chain, pg):
    results = {}
    polymer = mol.getEntity(chain)
    residues = polymer.getResidues()
    mol.setActive()
    for residue in residues:
        res = int(residue.getNumber())
        resName = residue.getName()
        proP = 0
        proS = 0
        if residue.previous != None:
            if residue.previous.getName() == "PRO":
                proP = 1
        if residue.next != None:
            if residue.next.getName() == "PRO":
                proS = 1
        if not pg.getResidueProperties(polymer, residue, 0):
            print "skip", res, resName
            continue
        resAtoms = residue.getAtoms()
        for atom in resAtoms:
            atomName = atom.getName()
            aType = getAtomNameType(atom)
            if aType != None:
                if not pg.getAtomProperties(polymer, res, resName, atomName, 0):
                    print "skip", res, resName, atomName
                    continue

                values = pg.getValues()
                v = PropVar(values, resName, proP, proS)
                if v.cs > -90.0 and abs(v.cs) > 0.001:
                    baseAttr = getBaseAttr(values, resName, proP, proS)
                    results[atom] = baseAttr
    return results

#loads molecule object and generates attr file
def doProteinOn(mol, chain, pg):
    results = {}
    polymer = mol.getEntity(chain)
    residues = polymer.getResidues()
    mol.setActive()
    for residue in residues:
        res = int(residue.getNumber())
        resName = residue.getName()
        proP = 0
        proS = 0
        if residue.previous != None:
            if residue.previous.getName() == "PRO":
                proP = 1
        if residue.next != None:
            if residue.next.getName() == "PRO":
                proS = 1
        if not pg.getResidueProperties(polymer, residue, 0):
            print "skip", res, resName
            continue
        for rootAName in nativeAtoms:
            atomNames = [rootAName]
            if rootAName == "HA":
                if resName == "GLY":
                    atomNames = ["HA2","HA3"]
            elif len(rootAName) > 1:
                resAtoms = residue.getAtoms()
                atomNames = []
                for resAtom in resAtoms:
                    if resAtom.getName().startswith(rootAName):
                       if resAtom.isMethyl():
                           if resAtom.isFirstInMethyl():
                               atomNames.append(resAtom.getName())
                       else:
                           atomNames.append(resAtom.getName())
            for atomName in atomNames:
                atom = residue.getAtom(atomName)
                if atom == None:
                    continue
                if not pg.getAtomProperties(atom, 0):
                    continue

                values = pg.getValues()
                v = PropVar(values, resName, proP, proS)
                if v.cs > -90.0 and abs(v.cs) > 0.001:
                    baseAttr = getBaseAttr(values, resName, proP, proS)
                    results[atom] = baseAttr
    return results

def saveAllBaseAttrs(pdbId, mode, attrsDict):
    for atom, baseAttr in attrsDict.items():
        rootAName = atom.getName()
        resName = atom.getEntity().getName()
        aType = getAtomNameType(atom)
        if aType is None:
            continue
        saveBaseAttr(pdbId, atom, mode, attrDir, aType, resName, baseAttr)

def setShifts(pdbMol, bmrbId, refSet='R'):
    for atom in pdbMol.getAtoms():
        if refSet == 'R':
            atom.setRefPPM(0.0)
        else:
            atom.setPPM(refSet, 0.0)

    bmrbMol = loadBMRB(bmrbId)
    if bmrbMol == None:
        return None, None,None

    pdbChain, bmrbChain = setChemShifts(pdbMol, bmrbMol, refSet)
    return pdbChain, bmrbChain, bmrbMol

def doAtoms(mol, chain):
    attrsDict = {}
    pg = PropertyGenerator()
    try:
        pg.init(mol, 0)
        atomDict = doProteinOn(mol, chain, pg)
    except Exception, e:
        print("Caught Python Exception:", e)
        return None
    attrsDict.update(atomDict)
    return attrsDict

def genAttrsFromList(fileName):
    mode = 'sngl'

    with open(fileName, 'r') as f1:
        for line in f1:
            line = line.split('\t')
            pdbId = line[0].strip()
            bmrbId = line[1].strip()
            Molecule.removeAll()
            if pdbExists(pdbId) is False:
                print 'cannot locate pdb', pdbId
                continue
            pdbFile = getPDBFile(pdbId)
            pdbMol = molio.readPDB(pdbFile)
            print('setshifts')
            pdbChain, bmrbChain, bmrbMol = setShifts(pdbMol, bmrbId)
            if pdbChain and bmrbChain:
                pdbMol.setActive()
                molDict = doAtoms(pdbMol, pdbChain)
                if molDict is None:
                    saveError(pdbId, bmrbId)
                    continue
                print('savebase')
                saveAllBaseAttrs(pdbId, mode, molDict)
                print('savedbase')
            else:
                saveError(pdbId, bmrbId)

def genSTARFromList(fileName):
    global homeDir
    global proteinPredictor
    if proteinPredictor == None:
        proteinPredictor = ProteinPredictor()
    predictor = Predictor()
    with open(fileName, 'r') as f1:
        for line in f1:
            line = line.split('\t')
            pdbId = line[0].strip()
            bmrbId = line[1].strip()
            Molecule.removeAll()
            StructureProject.getActive().clearAllMolecules()
            StructureProject.getActive().clearAllPeakLists()
            StructureProject.getActive().clearAllDatasets()
            if pdbExists(pdbId) is False:
                print 'cannot locate pdb', pdbId
                continue
            pdbFile = getPDBFile(pdbId)
            pdbMol = molio.readPDB(pdbFile)
            print('setshifts',pdbId, bmrbId)
            pdbChain, bmrbChain, bmrbMol = setShifts(pdbMol, bmrbId, 0)
            StructureProject.getActive().clearAllPeakLists()
            StructureProject.getActive().clearAllDatasets()
            if pdbChain and bmrbChain:
                bmrbMol.remove()
                pdbMol.setActive()
                predictor.setToBMRBValues(1)
                proteinPredictor.predictRandom(pdbMol, 1)
                outFile = os.path.join(homeDir,'starshift',bmrbId+'.str')
                NMRStarWriter.writeAll(outFile)
            else:
                saveError(pdbId, bmrbId)

def genAttrFromSTAR():
    attrsPath = os.path.join(homeDir, 'starshift', '*.str')
    print(attrsPath)
    files = glob.glob(attrsPath)
    trainer = ProteinPredictorTrainer()
    iStructure = 0
    for i,filename in enumerate(files):
        print(filename)
        Molecule.removeAll()
        star3 = star.read(filename)
        molecule = Molecule.getActive()
        print("gen data")
        trainer.addTrainData(molecule, iStructure)
        print("gen data")
    #    if i > 20:
    #        break
    trainer.saveData("valuemaps")

def saveError(pdbId, bmrbId):
    with open('errorEntries.txt', 'a') as f:
        f.write(pdbId + '\t' + bmrbId + '\n')

def getPredictAttrs(resName, baseAttr):
    headerFields = baseAttr[0::2]
    fields = baseAttr[1::2]
    fields = [field if ((field is not None )) else field == 0.0 for field in fields]
    v = PropVar2(headerFields,fields)
    if (v.N1 > 0.1):
        pass
    if (v.C1 > 0.1):
        pass
    a = getAttr(v, resName)
    return a

#text files required: coefficients and trimmed atoms
def assessPdbs(fileName):
    global homeDir
    rms_limits = {"C" : 1.158, "CA" : 1.011, "CB" : 1.130, "N" : 2.725, "H" : 0.451,
                  "HA" : 0.267, "HB" : 0.239, "HG" : 0.194, "HD" : 0.25, "HE" : 0.25,
                   "CG" : 0.927, "CD" : 1.2, "CE" :1.2}

    rmsFile = 'all_pdb_rms.txt'
    newFile = 'quality_assessed_nmr_entries.txt'

    with open(fileName, 'r') as f1, open(rmsFile, 'w') as f2, open(newFile, 'w') as f3:
        for line in f1:
            line = line.split()
            pdbId = line[0]
            bmrbId = line[1]
            nullCount = 0
            count = 0
            print pdbId, bmrbId
            rms = assessPdbInternal(pdbId, bmrbId)
            rmsValues = rms[::2]
            rmsString = ''.join(['\t{:20}'.format(rmsValue) for rmsValue in rmsValues])
            for atom, value in zip(atoms, rmsValues):
                if isinstance(value, str):
                    nullCount += 1
                    continue
                if value > rms_limits[atom]:
                    count += 1
                    break
            f2.write('{}\t{}{}\n'.format(pdbId, bmrbId, rmsString))
            if nullCount < len(rms_limits) and count == 0:
                f3.write('{}\t{}\n'.format(pdbId, bmrbId))
    exit(0)

def assessPdbInternal(pdbId, bmrbId):
    global proteinPredictor
    if proteinPredictor == None:
        proteinPredictor = ProteinPredictor()

    Molecule.removeAll()
    pdbFile = getPDBFile(pdbId)
    pdbMol = molio.readPDB(pdbFile)
    pdbChain, bmrbChain, bmrbMol = setShifts(pdbMol, bmrbId, 0)
    proteinPredictor.init(pdbMol)
    proteinPredictor.setReportAtom(reportAtom)
    proteinPredictor.predict(-1)
    rerefPdbInternal(pdbMol)

    molDict = doAtoms(pdbMol, pdbChain)
    dictOfCoefDict = readCoefs()
    predShiftDict = getPredShifts(molDict, dictOfCoefDict)

    pAtoms = pdbMol.getAtoms()
    rmsDict = {}
    for pAtom in pAtoms:
        ppmPred = pAtom.getRefPPM()
        ppmBMRB = pAtom.getPPM(0)
        if (ppmPred != None and ppmPred != 0.0 and ppmBMRB != None and  ppmBMRB.isValid()) and ppmBMRB.getValue() != 0.0:
            atomtype = getAtomType(pAtom)
            if not atomtype in atoms:
                continue
            predShift = ppmPred
            expShift = ppmBMRB.getValue()
            predShiftCoef = 'XX'
            delta2 = 1000.0
            if pAtom in predShiftDict:
                predShiftCoef = predShiftDict[pAtom]
                delta2 = predShiftCoef - predShift
            delta = expShift - predShift

            print pAtom.getFullName(),atomtype,predShiftCoef,predShift,expShift,round(delta,3),round(delta2,3)
            if doStdTrim(delta, atomtype) is True:
                continue
            sumOfSquares = math.pow((predShift-expShift),2)
            if atomtype not in rmsDict.keys():
                rmsDict[atomtype] = [sumOfSquares,1.0]
            else:
                rmsDict[atomtype][0] += sumOfSquares
                rmsDict[atomtype][1] += 1.0
    pdbRmsDict = {atomtype : [math.sqrt(values[0] / values[1]), values[1]] for atomtype, values in rmsDict.items()}
    rms = []
    for atom in atoms:
        if atom in pdbRmsDict.keys():
            rms.append(pdbRmsDict[atom][0])
            rms.append(pdbRmsDict[atom][1])
        else:
            rms.append('N/A')
            rms.append('N/A')
    return rms

def getAtomType(atom):
    atomtype = atom.getName()
    if len(atomtype) > 2:
        atomtype = atomtype[:2]
    return atomtype

def getShortName(aName):
    if aName[0] == 'M':
        if len(aName) > 3:
            aName = aName[:3]
    elif aName[0] == 'A':
        if len(aName) > 3:
            aName = aName[:3]
    else:
        if len(aName) > 2:
            aName = aName[:2]
    return aName

def doStdTrim(delta, atomType):
    limitsStd = {'C':5.0,'CA':5.0,'CB':5.0,'CG':5.0,'CD':5.0,'CE':5.0,'ACZ':5.0,'ACD':5.0,'ACE':5.0,
                 'H':2.0,'HA':0.7,'HB':2.0,'HG':2.0,'HD':2.0,'HE':2.0,'AHZ':2.0,'AHD':2.0,'AHE':2.0,
                 'MCB':5.0,'MCG':5.0,'MCD':5.0,'MCE':5.0,
                 'MHB':2.0,'MHG':2.0,'MHD':2.0,'MHE':2.0,
                 'N':10}
    if abs(delta) > limitsStd[atomType]:
        return True
    return False

def getPredShifts(molDict, dictOfCoefDict):
    pdbDict = {}
    for atom, baseAttr in molDict.items():
        atomtype = getAtomType(atom)
        atomName = atom.getShortName()
        resName = atom.getEntity().getName()
        keyName = atomtype + '_' + resName
        if keyName not in dictOfCoefDict.keys():
            continue
        coefDict = dictOfCoefDict[keyName]
        a = getPredictAttrs(resName, baseAttr)
        if atom.getFullName() == reportAtom:
            print baseAttr
            print a
        headerFields = a[0::2]
        fields = a[1::2]
        contacts = fields[-2]
        (minContact, maxContact) = minMaxSums[atomtype]
        disorder = disorderCalc(contacts, minContact, maxContact)
        intercept = float(coefDict['intercept'])
        predShift = intercept
        for headerField, field in zip(headerFields, fields):
            try:
                coef = coefDict[headerField]
                contribution = float(coef) * float(field) * disorder
                predShift += contribution
                if atom.getFullName() == reportAtom:
                    print headerField,coef,float(field),contribution,intercept,predShift
            except KeyError:
                continue
        expShift = atom.getRefPPM()
        if expShift <= 0.0:
            continue
        delta = expShift - predShift
        if doStdTrim(delta, atomtype) is True:
            continue
        pdbDict[atom] = predShift
    return pdbDict

def rerefPdbInternal(pdbMol):
    print 'rerefPdbInternal'
    errDict = {}
    pAtoms = pdbMol.getAtoms()
    for pAtom in pAtoms:
        ppmPred = pAtom.getRefPPM()
        ppmBMRB = pAtom.getPPM(0)
        if (ppmPred != None and ppmPred != 0.0 and ppmBMRB != None and  ppmBMRB.isValid()) and ppmBMRB.getValue() != 0.0:
            atomType = getAtomType(pAtom)
            predShift = ppmPred
            expShift = ppmBMRB.getValue()
            err = expShift - predShift
            if atomType not in errDict.keys():
                errDict[atomType] = [err, 1.0]
            else:
                errDict[atomType][0] += err
                errDict[atomType][1] += 1.0
    rerefDict = {atomType:values[0]/values[1] for atomType, values in errDict.items()}
    for pAtom in pAtoms:
        ppmPred = pAtom.getRefPPM()
        ppmBMRB = pAtom.getPPM(0)
        if (ppmPred != None and ppmPred != 0.0 and ppmBMRB != None and  ppmBMRB.isValid()) and ppmBMRB.getValue() != 0.0:
            atomType = getAtomType(pAtom)
            adj = rerefDict[atomType]
            pAtom.setPPM(0, pAtom.getPPM(0).getValue() - adj)

def calcRms(corrExpShiftDict, predShiftDict):
    rmsDict = {}
    for atom, expShift in corrExpShiftDict.items():
        atomtype = getAtomType(atom)
        predShift = predShiftDict[atom]
        delta = expShift - predShift
        if doStdTrim(delta, atomtype) is True:
            continue
        sumOfSquares = math.pow((predShift-expShift),2)
        if atomtype not in rmsDict.keys():
            rmsDict[atomtype] = [sumOfSquares,1.0]
        else:
            rmsDict[atomtype][0] += sumOfSquares
            rmsDict[atomtype][1] += 1.0
    pdbRmsDict = {atomtype : [math.sqrt(values[0] / values[1]), values[1]] for atomtype, values in rmsDict.items()}
    return pdbRmsDict

#checks that pdb and star files of the dataset entries can be read
def filterEntries(pdbIds, bmrbIds):
    skippedEntriesFileName = os.path.join('skippedEntries.txt')
    filteredEntriesFileName = os.path.join('validEntries.txt')

    with open(skippedEntriesFileName, 'w') as skippedEntriesFile, open(filteredEntriesFileName, 'w') as cleanFile:
        for pdbId, bmrbId in zip(pdbIds, bmrbIds):
            Molecule.removeAll()
            pdbEntry = getPDBFile(pdbId)
            pdbReadable = True
            bmrbReadable = True
            try:
                molio.readPDB(pdbEntry)
            except:
                print 'cannot read pdb', pdbId
                pdbReadable = False
                skippedEntriesFile.write(pdbId + '\n')
            try:
                loadBMRB(bmrbId)
                print 'bmrb entry loaded'
            except:
                print 'cannot read bmrb', bmrbId
                bmrbReadable = False
                skippedEntriesFile.write(bmrbId + '\n')

            if pdbReadable is True and bmrbReadable is True:
                print 'writing to file', pdbId, bmrbId
                cleanFile.write('{}\t{}\n'.format(pdbId, bmrbId))
    exit(0)

def combine(trimDict=None, reRef=False, testMode=False):
    global homeDir
    cmode = 'all'
    combineMethylene()
    for mode in ['sngl']:
        for aName in atoms:
            for aaGroup in aaGroups[aName]:
                groupName = '_'.join(aaGroup)
                atomType = getShortName(aName)
                outFile = os.path.join(homeDir, 'attrs', cmode + '_' + atomType + '_' + groupName + '.txt')
                mapFilePath = os.path.join(homeDir, 'maps', mode + '_' + atomType + '_*.txt')
                mapFiles = glob.glob(mapFilePath)
                if os.path.exists(outFile):
                    os.remove(outFile)
                for mapFile in mapFiles:
                    if os.path.exists(mapFile):
                        os.remove(mapFile)
    for mode in ['sngl']:
        for aName in atoms:
            atomType = getShortName(aName)
            for aaGroup in aaGroups[aName]:
                groupName = '_'.join(aaGroup)
                outFile = os.path.join(homeDir, 'attrs', cmode + '_' + atomType + '_' + groupName)
                if testMode:
                    outFile += '_test'
                outFile += '.txt'
                use = None
                for aa in aaGroup:
                    fileName = os.path.join(homeDir, attrDir, mode + '_' + atomType + '_' + aa + '.txt')
                    if not os.path.exists(fileName):
                        continue
                    if reRef is True:
                        errDict = getCorr(aName)
                    else:
                        errDict = {}
                    filterAttr(fileName, outFile, use, atomType, aa, aaGroup, errDict, trimDict, testMode)

def scaleMatrix(matrix, contacts, minSum, maxSum):
    rows = len(matrix)
    cols = len(matrix[0])
    newMat = Array2DRowRealMatrix(rows,cols)
    for row in range(rows):
        scale = disorderCalc(contacts[row], minSum, maxSum)
        for col in range(cols):
            if col == cols - 1:
                newMat.setEntry(row, col, matrix[row][col])
            else:
                newMat.setEntry(row, col, matrix[row][col] * scale)
    return newMat.getData()

def readMap(fileName):
    global homeDir
    map = []
    dir, tail = os.path.split(fileName)
    tail = tail.split('.')[0].split('_')
    atom = tail[1]
    residues = tail[2:]
    for residue in residues:
        mapFileName = os.path.join(homeDir, 'maps', 'sngl_' + atom + '_' + residue + '.txt')
        with open(mapFileName,'r') as mapFile:
            for mapline in mapFile:
                mapline = mapline.strip('\n')
                pdbID, atomName = mapline.split('\t')
                map.append((pdbID, atomName))
    return map

def getMatrix(fileName, splitMode=False):
    regressionFactory = RegressionFactory()
    csvLoader = CSVLoader('\t',regressionFactory)
    dataSource = csvLoader.loadDataSource(Paths.get(fileName),"cs")
    if splitMode:
        splitter = TrainTestSplitter(dataSource, 0.7, 0)
        trainData = MutableDataset(splitter.getTrain())
        evalData = MutableDataset(splitter.getTest())
    else:
        trainData = MutableDataset(dataSource)
        evalData = None
    return(trainData, evalData)


def processMatrix(headerFields, matrix):
    dict = {}
    noAttr = []
    for header in headerFields:
        dict[header] = 0
    for row in matrix:
        for header, value in zip(headerFields, row):
            if value == 0.0 or value == -0.0:
                dict[header] += 1
    for header in headerFields:
        if dict[header] == len(matrix):
            noAttr.append(header)
    return noAttr

def train(name, trainer, trainData):
    model = trainer.train(trainData)

    eval = RegressionEvaluator()
    evaluation = eval.evaluate(model,trainData)
 
    dimension = Regressor("DIM-0",Double.NaN)
    outStr = "RMSE %7.4f MAE %7.4f R2 %7.4f" % (evaluation.rmse(dimension), evaluation.mae(dimension), evaluation.r2(dimension))
    print(outStr)
    return model

def crossVal(name, trainer, trainData):
    eval = RegressionEvaluator()
    crossValidator = CrossValidation(trainer, trainData, eval, 10)
    evs = crossValidator.evaluate()
    for ev in evs:
        print(ev)
    return evs
 

def evaluate(model, testData):
    eval = RegressionEvaluator()
    evaluation = eval.evaluate(model,testData)
    dimension = Regressor("DIM-0",Double.NaN)
    return evaluation.mae(dimension)


# generates matrix from combined attrs file, passes scaled matrix to lasso predictor
# maps pdbId and atomName to experimental shift and predicted shift
def fit(fileName, minSum, maxSum, testMode=False, coefsSave=False, optMinMax=False):
    map = readMap(fileName)
    trainData, evalData = getMatrix(fileName)
    lasso = LARSLassoTrainer()
    model = train(fileName, lasso, trainData)
    predictions = model.predict(trainData)
    yValues = []
    pValues = []
    for i,pred in enumerate(predictions):
        mValue = map[i]
        example = pred.getExample()
        eValue = example.getOutput().getValues()[0] 
        pValue = pred.getOutput().getValues()[0] 
        yValues.append(eValue)
        pValues.append(pValue)
        delta = pValue - eValue

    mae = evaluate(model, trainData)

    dir, file = os.path.split(fileName)
    resultsPath = os.path.join(homeDir, 'results', file)

    with open(resultsPath, 'w') as fOut:
        for yValue, pValue, descript in zip(yValues,pValues, map):
            delta = yValue - pValue
            fOut.write('{}\t{}\t{}\t{}\t{}\n'.format(yValue, pValue, delta, descript[0], descript[1]))
            # fOut.write('{}\t{}\t{}\n'.format(yValue, predValue, delta))
    if coefsSave:
        dir, file = os.path.split(fileName)
        file = file[0:-4] + '.model'
        modelFile = os.path.join(homeDir, 'models', file)
        model.serializeToFile(Paths.get(modelFile))

    return mae, trainData.size(), mae

def saveCoefs(filename, headerFields, coefs, cols, intercept):
    coefDir = os.path.join(homeDir, 'coefs', filename+'.txt')
    with open(coefDir, 'w') as coefFile:
        coefFile.write('{}\t{}\t{}\n'.format(-1, 'intercept', intercept))
        for i in range(cols):
            coefFile.write('{}\t{}\t{}\n'.format(i, headerFields[i], coefs[i]))

#change coefFile path to path where files are located
def readCoefs():
    dictOfDict={}
    for atom in atoms:
        coefFile = os.path.join(homeDir, 'coefs', atom + '_' + '*.txt')
        fileNames = glob.glob(coefFile)
        for fileName in fileNames:
            coefDict = {}
            dir, file = os.path.split(fileName)
            file = file[:-4].split('_')
            resNames = file[1:]
            with open(fileName, 'r') as f1:
                for line in f1:
                    index, header, value = line.strip('\n').split('\t')
                    coefDict[header] = value
            for resName in resNames:
                keyName = atom + '_' + resName
                dictOfDict[keyName] = coefDict
    return dictOfDict

def getTestSet():
    testpdbs = []
    global testSetFile
    with open(testSetFile, 'r') as testSet:
        for line in testSet:
            pdbId, bmrbId = line.strip().split('\t')
            testpdbs.append(pdbId)
    return testpdbs

def combineMethylene():
    mode = 'sngl'
    baseAttrsPath = os.path.join(homeDir, attrDir, mode + "_" + 'H[BGD][23]' + "_" + '*' + ".txt")
    files = glob.glob(baseAttrsPath)
    atomNameMap = {}
    for filename in files:
        dir,file = os.path.split(filename)
        parts = file.split('_')
        aname = parts[-2]
        if aname[0] == 'H' and len(aname) >  2:
            rootName = aname[0:-1]
        else:
            rootName = aname
        resName = parts[-1][0:-4]
        resRoot = (resName, rootName)
        if not resRoot in atomNameMap:
            atomNameMap[resRoot] = []
        atomNameMap[resRoot].append(aname)
    for resRoot in atomNameMap:
        (resName,rootName) = resRoot
        baseAttrsPath = os.path.join(homeDir, attrDir, mode + "_" + rootName + "_" + resName + ".txt")
        print('make', baseAttrsPath)
        with open(baseAttrsPath, 'w') as fOut:
            first = True
            for aName in atomNameMap[resRoot]:
                baseAttrsPath2 = os.path.join(homeDir, attrDir, mode + "_" + aName + "_" + resName + ".txt")
                print('add', baseAttrsPath2)
                with open(baseAttrsPath2, 'r') as fIn:
                    if not first:
                        fIn.readline()
                    else:
                        first = False
                    for line in fIn:
                        fOut.write(line)


def fitAll(testMode=False, coefsSave=False, getRMS=False, optMinMax=False):
    fitFilename = os.path.join(homeDir, 'fitOutput.txt')
    rmsDict = {}
    with open(fitFilename, 'w') as fitFile:
        for atom in atoms:
            print 'fit',atom
            minSum, maxSum = minMaxSums[atom]
            sum = 0.0
            sumx = 0.0
            total = 0.0
            totalx = 0.0
            attrsPath = os.path.join(homeDir, 'attrs', 'all_' + atom + '_*.txt')
            files = glob.glob(attrsPath)
            for filename in files:
                rms, size, xrms = fit(filename, minSum, maxSum, testMode, coefsSave, optMinMax)
                sum += rms * rms * size
                if size > 40:
                    sumx += xrms  * xrms  * size
                    totalx += size
                total += size
            if totalx > 1:
                xrms = math.sqrt(sumx / totalx)
            else:
                xrms = 0.0
            if total == 0:
                print atom,attrsPath
            rmsDict[atom] = math.sqrt(sum / total)
            fitFile.write('{}\t{}\t{}\t{}\n'.format(atom, rmsDict[atom], xrms, totalx))
    if getRMS:
        return rmsDict

def getCorrType(atomName):
    if len(atomName) == 1:
        fileName = atomName
    elif len(atomName) == 2:
        if atomName == 'CA':
            fileName = 'CACB'
        elif atomName == 'CB':
            fileName = 'CACB'
        elif atomName == 'HA':
            fileName = 'HAHB'
        elif atomName == 'HB':
            fileName = 'HAHB'
        elif atomName[0] == 'H':
            fileName = 'Hali'
        else:
            fileName = 'Cali'
    else:
        fileName = atomName[0:2]

#    if atomName == 'CA':
#        fileName = 'CACB'
#    elif atomName == 'CB':
#        fileName = 'CACB'
#    else:
#        fileName = atomName

    return fileName


#calculates chemical shift correction using delta value
def calCorr(trimDict):
    global homeDir
    atomGroup = [["MHB"], ["MHG"], ["MHD"], ["MCB"], ["MCG"], ["MCD"], ['MCE'], ['MHE'], ["C"],
                 ["CA", "CB"], ["N"], ["H"], ["HA"], ["HB"], ["HG"], ["HD"], ["AHZ"],['AHD'],['AHE'],
                 ["HE"], ["CG"], ["CD"], ["CE"], ["ACZ"],['ACD'],['ACE']]

    refErrorDict = {}
    for atoms in atomGroup:
        corrType = getCorrType(atoms[0])
        if not corrType in refErrorDict:
            refErrorDict[corrType] = {}
        corrDict = refErrorDict[corrType]

        for atom in atoms:
            resultsPath = os.path.join(homeDir, 'results', 'all_' + atom + '_*.txt')
            files = glob.glob(resultsPath)
            for filename in files:
                print 'calculate ref error: ', atom, filename
                with open(filename,'r') as f:
                    for line in f:
                        exp, pred, delta, pdbId, atomName = line.strip('\n').split('\t')
                        if (pdbId, atomName) in trimDict[atom]:
                            continue
                        if not pdbId in corrDict:
                            corrDict[pdbId] = [0,0.0]
                        currValues = corrDict[pdbId]
                        currValues[0] += 1
                        currValues[1] += float(delta)
    
    for corrType in refErrorDict:
        refErrorFile = os.path.join(homeDir, 'refErrors', corrType + '.txt')
        errDict = getCorrFromType(corrType)
        with open(refErrorFile, 'w') as f1:
            corrDict = refErrorDict[corrType]
            for pdbId in corrDict:
                values = corrDict[pdbId]
                currValue = 0.0
                hasCurr = 0
                if pdbId in errDict:
                    currValue = float(errDict[pdbId])
                    hasCurr = 1
                newCorr = 0.0
                if values[0] > 20:
                    newCorr = values[1] / values[0]
                error = newCorr + currValue
                f1.write('{}\t{}\t{}\t{}\n'.format(pdbId, error, values[0], hasCurr))

#reads average reference error per atom type per pdbId
def getCorr(aName):
    corrType = getCorrType(aName)
    return getCorrFromType(corrType)

def getCorrFromType(corrType):
    global homeDir
    refErrorFile = os.path.join(homeDir, 'refErrors', corrType + '.txt')
    errDict = {}
    if os.path.exists(refErrorFile):
        with open(refErrorFile, 'r') as f:
            for line in f:
                pdb, value, n, hasCurr = line.strip('\n').split('\t')
                errDict[pdb] = value
    return errDict

#trims rows with delta(exp-pred) greater than set ppm limit
def bigTrim(limits=None, readOnly=False):
    limitsStd = {'C':5.0,'CA':5.0,'CB':5.0,'CG':5.0,'H':2.0,'HA':0.7,'HB':2.0,
                 'HG':2.0,'N':10, 'MCB':5.0,'MCD':5.0,'MCG':5.0,'MHG':2.0,
                 'MHD':2.0,'MHG':2.0,'MCE':5.0,'MHE':2.0,"ACZ":5.0,"AHZ":2.0,
                 'AHD':2.0, 'AHE':2.0, 'ACD':5.0,'ACE':5.0}

    if limits == None:
        limits = limitsStd

    trimDict = {'C':{},'CA':{},'CB':{},'CG':{},'CD':{},'CE':{},
                'H':{},'HA':{},'HB':{},'HG':{},'HD':{},'HE':{},
                'N':{},'MHB':{},'MHG':{},'MHD':{},'MCB':{},'MCG':{},
                'MCD':{},'MCE':{},'MHE':{},"ACZ":{},"AHZ":{},'ACD':{},'ACE':{},'AHD':{},'AHE':{}}

    #read atoms from previous trim into dictionary
    trimPath = os.path.join(homeDir, 'trimmed.txt')
    if os.path.exists(trimPath):
        with open(trimPath, 'r') as trimFile:
            for line in trimFile:
                atom, pdbId, atomName,delta = line.strip('\n').split('\t')
                trimDict[atom][pdbId,atomName] = delta
    if readOnly:
        print 'read trim dict only'
        return trimDict

    #append atoms to be trimmed to dictionary
    for atom in limits.keys():
        files = glob.glob(homeDir + "/results/" + "all_" + atom + "_*.txt")
        for filename in files:
            dir,file = os.path.split(filename)
            dLimit = limits[atom]
            resultsPath = os.path.join(homeDir, 'results', file)
            attrsPath = os.path.join(homeDir, 'attrs', file)
            with open(resultsPath, 'r') as descriptFile, open(attrsPath, 'r') as attrsFile:
                    attrsFile.readline()
                    for resultLine, attrLine in izip(descriptFile, attrsFile):
                        exp, pred, delta, pdbId, atomName = resultLine.strip('\n').split('\t')
                        trimDict2 = trimDict[atom]
                        if abs(float(delta)) > dLimit and (pdbId,atomName) not in trimDict2:
                            #print pdbId, atomName, 'exp ', exp, 'pred: ', pred, 'delta: ', delta, 'limit: ', dLimit
                            trimDict[atom][pdbId,atomName] = delta

    #write out new trim file
    with open(trimPath,'w') as trimFile:
        for k,v in trimDict.items():
            for pdb,atomName in v:
                delta = trimDict[k][pdb,atomName]
                trimFile.write('{}\t{}\t{}\t{}\n'.format(k, pdb, atomName,delta))
    return trimDict

def loadLimits(devValue):
    rmsDict = fitAll(False, False, True)
    limitDict = {}
    for atom in atoms:
        limitDict[atom] = rmsDict[atom] * devValue
    return limitDict

#removes test entries from the main dataset
def removeTest(fileName):
    testSetFile = 'testSet.txt'
    test_pdbIds = []
    test_bmrbIds = []
    train_pdbIds = []
    train_bmrbIds = []
    with open(fileName, 'r') as initialFile, open(testSetFile, 'r') as testFile:
        for line in testFile:
            pdbId, bmrbId = line.strip('\n').split('\t')
            test_pdbIds.append(pdbId)
            test_bmrbIds.append(bmrbId)
        for line in initialFile:
            pdbId, bmrbId = line.strip('\n').split()[:2]
            if bmrbId in test_bmrbIds or pdbId in test_pdbIds:
                print bmrbId, pdbId
                continue
            train_pdbIds.append(pdbId)
            train_bmrbIds.append(bmrbId)

    return train_pdbIds + test_pdbIds, train_bmrbIds + test_bmrbIds

def newDir(newHomeDir=homeDir):
    if not os.path.exists(newHomeDir):
        print 'creating new home directory', newHomeDir
        os.mkdir(newHomeDir)
        dirNames = [attrDir, 'attrs', 'maps', 'coefs', 'results', 'testresults', 'refErrors']
        for dirName in dirNames:
            dirPath = os.path.join(newHomeDir, dirName)
            print 'creating', dirPath
            os.makedirs(dirPath)

def copyBaseAttrFiles(dir1, dir2, atom=None, aa=None):
    if atom and aa:
        attrFilePath = glob.glob(os.path.join(dir1, attrDir, "sngl_" + atom + "_*" + aa + "*.txt"))
    elif atom:
        attrFilePath = glob.glob(os.path.join(dir1, attrDir, "sngl_" + atom + "_*.txt"))
    elif aa: 
        attrFilePath = glob.glob(os.path.join(dir1, attrDir, "sngl_*" + aa + "*.txt"))
    else:
        attrFilePath = glob.glob(os.path.join(dir1, attrDir, "*.txt"))
    for filePath in attrFilePath:
        dir, filename = os.path.split(filePath)
        attrFileDest = os.path.join(dir2, attrDir, filename)
        shutil.copyfile(filePath, attrFileDest)

#genAttrsFromList() generates sngl attribute txt files per atom type in atoms[] per residue
#combine() generates all attr files based on aaGroups[], generates map of pdbIDs and atomNames
#fit() loads attrs into matrix, calls LASSO, generates map 2.0 (adds cols of experimental values, predicted values, delta)
#work flow: gen --> init --> corr --> corr --> final

def prepData(args):
    #removes pdb and bmrb entries in the test set from the generated dataset
    pdbIds, bmrbIds = removeTest(args.filename)
    #runs all pdb and bmrb files to make sure they can be loaded into molecule objects
    filterEntries(pdbIds, bmrbIds)
    #predicts based on existing coefficients, preliminary quality measure


def gen(args):
    global bmrbHome
    global pdbHome
    global pdbDir
    global homeDir
    if args.bmrb:
        bmrbHome = args.bmrb
    if args.pdb: 
        pdbHome = args.pdb
    if args.pdbDir:
        pdbDir = args.pdbDir
    homeDir = args.homeDir
    newDir(homeDir)
    print homeDir, args.dataset, 'gen'
    #genAttrsFromList(args.dataset)
    genSTARFromList(args.dataset)
    #genAttrFromSTAR()


def cpAttrs(args):
    global homeDir
    homeDir = args.d
    newDir(homeDir)
    copyBaseAttrFiles(args.o, args.d, args.atom, args.res)

def doAll(args):
    global bmrbHome
    global pdbHome
    global pdbDir 
    global homeDir
    global useContacts
    global lamVal
    homeDir = args.homeDir
    if args.bmrb:
        bmrbHome = args.bmrb
    if args.pdb:
        pdbHome = args.pdb
    if args.pdbDir:
        pdbDir = args.pdbDir 
    newDir(homeDir)
    if args.gen:
        gen(args)
    lamVal = args.lambda
    useContacts = not args.nocontacts
    initialProp(args)
    errorGlob = os.path.join(homeDir, 'refErrors', '*.txt')
    errorFiles = glob.glob(errorGlob)
    for fileName in errorFiles:
        os.remove(fileName)
    doCorrection(args)
    doCorrection(args)
    finalProp(args)

def initialProp(args):
    global homeDir
    homeDir = args.homeDir
    #combine sngl attr files from genAttrs() into all files based on aa groups
    combine(testMode=args.test)
    #initial fit to generate result files, subsequent trim and rereference is based on the delta value
    fitAll()

#initial ref error correction
def doCorrection(args):
    global homeDir
    homeDir = args.homeDir
    #tight trim based on ppm limits set by shiftx2
    print 'bigtrim'
    trimDict = bigTrim()
    print 'bigtrimed'
    #generates txt files of reference errors by pdbID
    calCorr(trimDict)
    #rereference atoms and skip over trimmed atoms
    combine(trimDict, True)
    #generates result files with rereferenced values and new delta
    fitAll()


def stdTrim(args):
    # trim based on std dev
    limits = loadLimits(args.stdDev)
    trimAgain = bigTrim(limits)
    #incorporate second trim (skips over trimmed atoms)
    combine(trimAgain)

#perform fit on specific attr file
def doSingleFit(args):
    global homeDir
    global lamVal
    global useContacts
    lamVal = args.lambda
    useContacts = not args.nocontacts
    homeDir = args.homeDir
    attrFile = glob.glob(os.path.join(homeDir, 'attrs', 'all_' + args.atom + '_*' + args.aa + '*.txt'))[0]
    minSum, maxSum = minMaxSums[args.atom]

    fit(attrFile, minSum, maxSum)


#perform fit on all attr files
def finalProp(args):
    global homeDir
    global testSet
    global lamVal
    global useContacts
    lamVal = args.lambda
    useContacts = not args.nocontacts
    testMode = False
    homeDir = args.homeDir
    print homeDir
    if args.test:
        testSet = args.test
        testMode = True
    fitAll(testMode, args.coefs)

def assess(args):
    global bmrbHome
    global pdbHome
    global pdbDir
    global homeDir
    if args.bmrb:
        bmrbHome = args.bmrb
    if args.pdb:
        pdbHome = args.pdb
    if args.pdbDir:
        pdbDir = args.pdbDir
    if args.filename:
        assessPdbs(args.filename)
    if args.pdbid and args.bmrbid:
        assessPdbInternal(args.pdbid, args.bmrbid)

parser = argparse.ArgumentParser(usage='')
subparsers = parser.add_subparsers(title='subcommands')

parser_prep = subparsers.add_parser('prep', help='filter unreadable entries and combine with test set, pass in paired pdb - bmrb entries, filename required')
parser_prep.add_argument('filename', type=str)
parser_prep.set_defaults(func=prepData)

parser_gen = subparsers.add_parser('gen', help='generate base attributes, home dir')
parser_gen.add_argument('homeDir', nargs='?', default=homeDir, type=str)
parser_gen.add_argument('-dataset', default=datasetFileName, type=str)
parser_gen.add_argument('-bmrb', type=str)
parser_gen.add_argument('-pdb', type=str)
parser_gen.add_argument('-pdbDir', type=str)
parser_gen.set_defaults(func=gen)

parser_copy = subparsers.add_parser('copy', help='copy attrs from other directory, origin dir, destination dir, -atom, -res')
parser_copy.add_argument('-o', type=str)
parser_copy.add_argument('-d', type=str)
parser_copy.add_argument('-atom', type=str)
parser_copy.add_argument('-res', type=str)
parser_copy.set_defaults(func=cpAttrs)

parser_doall = subparsers.add_parser('doall', help='run entire workflow, home dir, dataset, -bmrb, -pdb, -pdbDir,  -gen, -test')
parser_doall.add_argument('homeDir', nargs='?', default=homeDir, type=str)
parser_doall.add_argument('-dataset', default=datasetFileName, type=str)
parser_doall.add_argument('-bmrb', type=str)
parser_doall.add_argument('-pdb', type=str)
parser_doall.add_argument('-pdbDir', type=str)
parser_doall.add_argument('-gen', action='store_true')
parser_doall.add_argument('-coefs', action='store_true')
parser_doall.add_argument('-lambda', type=float,default=0.005)
parser_doall.add_argument('-nocontacts', action='store_true', default=False)
parser_doall.add_argument('-test', action='store_true', default=False)
parser_doall.set_defaults(func=doAll)

parser_init = subparsers.add_parser('init', help='initial fit from base attr files, home dir, test')
parser_init.add_argument('-test', action='store_true', default=False)
parser_init.add_argument('homeDir', nargs='?', default=homeDir, type=str)
parser_init.set_defaults(func=initialProp)

parser_corr = subparsers.add_parser('corr', help='trim and rereference, home dir')
parser_corr.add_argument('homeDir', nargs='?', default=homeDir, type=str)
parser_corr.set_defaults(func=doCorrection)

parser_stdTrim = subparsers.add_parser('stdTrim', help='standard deviation trim, int required')
parser_stdTrim.add_argument('stdDev', default=3.0, type=float)
parser_stdTrim.set_defaults(func=stdTrim)

parser_fit = subparsers.add_parser('fit', help='fit single attr file, home dir, atom, aa')
parser_fit.add_argument('homeDir', nargs='?', default=homeDir, type=str)
parser_fit.add_argument('atom', type=str)
parser_fit.add_argument('aa', type=str)
parser_fit.add_argument('-lambda', type=float,default=0.005)
parser_fit.add_argument('-nocontacts', action='store_true', default=False)
parser_fit.set_defaults(func=doSingleFit)

parser_fitAll = subparsers.add_parser('fitall', help='fit all attr files, home dir, -test, -coefs')
parser_fitAll.add_argument('-test', action='store_true', default=False)
parser_fitAll.add_argument('-coefs', action='store_true', default=False)
parser_fitAll.add_argument('-lambda', type=float,default=0.005)
parser_fitAll.add_argument('-nocontacts', action='store_true', default=False)
parser_fitAll.add_argument('homeDir', nargs='?', default=homeDir, type=str)
parser_fitAll.set_defaults(func=finalProp)

parser_assess = subparsers.add_parser('assess', help='assess entries, -filename, -bmrb, -pdb')
parser_assess.add_argument('-filename', type=str)
parser_assess.add_argument('-bmrbid', type=str)
parser_assess.add_argument('-pdbid', type=str)
parser_assess.add_argument('-bmrb', type=str)
parser_assess.add_argument('-pdb', type=str)
parser_assess.add_argument('-pdbDir', type=str)
parser_assess.set_defaults(func=assess)

if len(sys.argv) < 2:
    print parser.print_help()
else:
    sys.argv.pop(0)
    args = parser.parse_args(sys.argv)
    args.func(args)
exit(0)
