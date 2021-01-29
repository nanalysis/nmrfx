import sys
import os
import osfiles
import argparse
import molio
from org.nmrfx.structure.chemistry.energy import RNARotamer
from org.nmrfx.structure.rna import RNAAnalysis

def dumpDis(mol, fileName='distances.txt', delta=0.5, atomPat='*.H*',maxDis=4.5,prob=1.1,fixLower=0.0):
    """ Writes a dump file containing distance violations based on input distance
        constraints and actual distance between atoms.

    # Parameters:

    * fileName (string); name of the output dump file
    * delta (float);
    * atomPat (string);
    * maxDis (float);
    * prob (float);
    * fixLower (float);
    """

    mol.selectAtoms(atomPat)
    pairs =  mol.getDistancePairs(maxDis,False)
    with open(fileName,'w') as fOut:
        for pair in pairs:
            if prob < 1.0:
                r = random.random()
                if r > prob:
                    continue
            (atom1,atom2,distance) = pair.toString().split()
            (res1,aname1) = atom1[2:].split('.')
            (res2,aname2) = atom2[2:].split('.')
            atom1 = res1+'.'+aname1
            atom2 = res2+'.'+aname2
            distance = float(distance)
            if res1 != res2:
                upper = distance + delta
                if fixLower > 1.0:
                    lower = fixLower
                else:
                    lower = distance - delta
                outStr = "%s %s %.1f %.1f\n" % (atom1,atom2,lower,upper)
                fOut.write(outStr)


def genResidueList(resString):
    if resString == '':
        return None
    residues = None
    if resString != '':
        residues = []
        elems = resString.split(',')
        for elem in elems:
            elem = elem.strip()
            if elem[0] == '-':
                sign = '-'
                elem = elem[1:]
            else:
                sign = ''

            if '-' in elem:
                if '--' in elem:
                    r1,r2 = elem.split('--')
                    r1 = sign+r1 
                    r2 = '-'+r2 
                else:
                    r1,r2 = elem.split('-')
                    r1 = sign+r1 
                r1 = int(r1)
                r2 = int(r2)
                for r in range(r1,r2+1):
                    residues.append(str(r))
            else:
                residues.append(sign+elem)
    return set(residues)
            
def rnaDotBracket(mol, fileName='stdout'):
   rnaResidues = [residue for polymer in mol.getPolymers() if polymer.isRNA() for residue in polymer.getResidues()]
   vienna = RNAAnalysis.getViennaSequence(mol)
   dotBracketDict = {}
   for rnaResidue,dotBracket in zip(rnaResidues, vienna):
       polyName = rnaResidue.getPolymer().getName()
       resNum = rnaResidue.getNumber()
       dotBracketDict[polyName+resNum]=str(dotBracket)
   vienna = ''.join(vienna) 
   return vienna,dotBracketDict

def sequence(mol, fileName='stdout'):
    for polymer in mol.getPolymers():
        type = polymer.getPolymerType()
        for residue in polymer.getResidues():
            print polymer.getName(),residue.getName()

def rnaSuite(mol, includeResidues, fileName='stdout'):
    if fileName == 'stdout':
        fOut = sys.stdout
    else:
        fOut = open(fileName,'w')
    rnaDotBracketDict = None
    for polymer in mol.getPolymers():
        if polymer.isRNA():
            if rnaDotBracketDict == None:
                vienna, rnaDotBracketDict = rnaDotBracket(mol)
            polyName = polymer.getName()
            for residue in polymer.getResidues():
                if includeResidues != None:
                    if residue.getNumber() not in includeResidues:
                        continue
                resNum = residue.getNumber()
                dotBracket = rnaDotBracketDict[polyName+resNum]
                rotamerScore = RNARotamer.scoreResidue(residue)
                if rotamerScore != None:
                    outStr = "%4s %4s %2s %2s %s\n" %(polyName, residue.getNumber(),residue.getName(),dotBracket,rotamerScore.toString())
                    fOut.write(outStr)
    if fileName != 'stdout':
        fOut.close()
  
def loadStructure(fileName, xMode=True, iStruct=0):
    if fileName.endswith('.pdb'):
        if (xMode):
            mol = molio.readPDBX(fileName)
            molio.readPDBXCoords(fileName, -1, True, False)
        else:
            mol = molio.readPDB(fileName, iStruct=iStruct)
    elif fileName.endswith('.cif'):
        mol = molio.readMMCIF(fileName)
    elif fileName.endswith('.sdf'):
        cmpd = molio.readSDF(fileName)
        mol = cmpd.molecule
    elif fileName.endswith('.mol'):
        cmpd = molio.readSDF(fileName)
        mol = cmpd.molecule
    else:
        print 'Invalid file type'
        exit(1)
    return mol

def parseArgs():
    parser = argparse.ArgumentParser(description="predictor options")
    parser.add_argument("-x", dest="xMode", default=False, action="store_true", help="Whether to read without library(False")
    parser.add_argument("-dis", dest="disMode", default=False, action="store_true", help="Whether to output  distances(False")
    parser.add_argument("-suite", dest="suiteMode", default=False, action="store_true", help="Whether to output  RNA suites(False")
    parser.add_argument("-dot", dest="dotMode", default=False, action="store_true", help="Whether to output  RNA dot-bracket (Vienna) sequence(False")
    parser.add_argument("-seq", dest="seqMode", default=False, action="store_true", help="Whether to output  sequence(False")
    parser.add_argument("-residues", dest="includeResidues", default='',  help="Limit residues to these (use all)")
    parser.add_argument("fileNames",nargs="*")
    args = parser.parse_args()
    includeResidues = genResidueList(args.includeResidues)
    for fileName in args.fileNames:
        mol = loadStructure(fileName, args.xMode)
        if args.disMode:
            dumpDis(mol) 
        if args.suiteMode:
            rnaSuite(mol, includeResidues)
        if args.dotMode:
            vienna, rnaDotBracketDict = rnaDotBracket(mol)
            print vienna
        if args.seqMode:
            sequence(mol)
            
        
