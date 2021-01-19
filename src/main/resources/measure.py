import sys
import os
import osfiles
import argparse
import molio

def dumpDis(mol, fileName='distanes.txt', delta=0.5, atomPat='*.H*',maxDis=4.5,prob=1.1,fixLower=0.0):
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

def loadStructure(fileName, xMode=False):
    if fileName.endswith('.pdb'):
        if (xMode):
            mol = molio.readPDBX(fileName)
            print 'read all coords'
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
    #parser.add_argument("-d", dest="xMode", default=False, action="store_true", help="Whether to read coordinates without library (False")
    parser.add_argument("fileNames",nargs="*")
    args = parser.parse_args()
    for fileName in args.fileNames:
        mol = loadStructure(fileName)
        dumpDis(mol) 
        
