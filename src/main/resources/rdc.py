import molio
import math
import sys
import argparse

from org.nmrfx.structure.rdc import AlignmentCalc
from org.nmrfx.structure.rdc import AlignmentMatrix
from org.nmrfx.structure.rdc import RDCVectors
#VARS    RESID_I RESNAME_I ATOMNAME_I RESID_J RESNAME_J ATOMNAME_J DI D_OBS D D_DIFF DD W

def readPalesTab(fileName, delta):
    inData = False
    rdcDict = {}
    with open(fileName,'r') as f1:
        for line in f1:
            line = line.strip()
            if not inData:
                if line.startswith("FORMAT"):
                    inData = True
                elif line.startswith("VARS"):
                    header = line.split()
            else:
                if line != "":
                    fields = line.split()
                    resNum = int(fields[0]) + delta
                    if 'D_OBS' in header:
                        idx = header.index('D_OBS') - 1
                        rdc = float(fields[idx])
                        rdcDict[resNum,'exp'] = rdc
                    if 'D' in header:
                        idx = header.index('D') - 1
                        rdcFit = float(fields[idx])
                        rdcDict[resNum,'pales'] = rdcFit
    return rdcDict


def calcAlign(mol,args):
    aCalc = AlignmentCalc(mol)
    aCalc.center()
    aCalc.genAngles(244,36,1.0)
    aCalc.findMinimums()

    slabWidth = args.width
    f = args.fWV
    d = 2.0 * args.radius

    aCalc.calcExclusions(slabWidth, f, d)
    nConstr = aCalc.getConstrained()

    aCalc.calcTensor(args.lcS)

    aMat = aCalc.getAlignment()
    sMat = aMat.getSMatrix()
    print 'smat',sMat

    ssMat = aMat.getScaledMatrix()
    print 'ssmat',ssMat

    trace = ssMat.getEntry(0,0) + ssMat.getEntry(1,1) + ssMat.getEntry(2,2)
    print 'ccccccalc'
    print "%10.4e %10.4e %10.4e %10.4e %10.4e %10.4e" % (ssMat.getEntry(2,2),ssMat.getEntry(0,0)-ssMat.getEntry(1,1),ssMat.getEntry(0,1),ssMat.getEntry(0,2),ssMat.getEntry(1,2), trace)
    return aCalc


def calcRDC(aMat,rdcVec):
    aMat.calcAlignment()
    #aMat.dump()

    rMat = AlignmentMatrix.setupDirectionMatrix(rdcVec.getRDCVectors())
    aMat.calcRDC(rMat, rdcVec.getRDCVectors())

#    2  THR   HN    2  THR    N -21523.11    1.4640    0.3636    1.1004  1.0000 1.00

def dumpRDC(outtable, rdcVec, rdcDict):
    with open(outtable,'w') as f1:
        for rdcVec in rdcVec.getRDCVectors():
            atom1 = rdcVec.getAtom1() 
            atom2 = rdcVec.getAtom2() 
            aName1 = atom1.getName()
            aName2 = atom2.getName()
            resNum1 = int(atom1.getEntity().getNumber())
            resNum2 = int(atom2.getEntity().getNumber())
            resName1 = atom1.getEntity().getName()
            resName2 = atom2.getEntity().getName()
            rdcPred = -rdcVec.getRDC()
            maxRDC = rdcVec.getMaxRDC()
            if rdcDict:
                if  (resNum1,'exp') in rdcDict:
                    rdcExp = rdcDict[resNum1,'exp']
                    rdcDelta = rdcExp - rdcPred
                    if  (resNum1,'pales') in rdcDict:
                        rdcPales = rdcDict[resNum1,'pales']
                        outStr = "%4d %4s %4s %4d %4s %4s %9.2f %9.3f %9.3f %9.3f %9.3f %.2f %.2f\n" % (resNum1,resName1,aName1,resNum2,resName2,aName2,maxRDC,rdcExp,rdcPred,rdcDelta,rdcPales, 1.0,1.0)
                    else:
                        outStr = "%4d %4s %4s %4d %4s %4s %9.2f %9.3f %9.3f %9.3f %.2f %.2f\n" % (resNum1,resName1,aName1,resNum2,resName2,aName2,maxRDC,rdcExp,rdcPred,rdcDelta, 1.0,1.0)
                    f1.write(outStr)
            else:
                outStr = "%d %.1f\n" % (resNum1, rdcPred)
                f1.write(outStr)

def parseArgs():
    parser = argparse.ArgumentParser(description="predictor options")
    parser.add_argument("-f", dest="fWV", type=float, default=0.05,  help="Weight/volume fraction of alignment media(0.05")
    parser.add_argument("-r", dest="radius", type=float, default=20.0,  help="Radius fo alignment media (20")
    parser.add_argument("-w", dest="width", type=float, default=0.2,  help="Width of slabs (20")
    parser.add_argument("-l", dest="lcS", type=float, default=0.8,  help="Order of liquid crystal (0.8")
    parser.add_argument("-d", dest="delta", type=int, default=0,  help="Offset of table residue number from pdb residue number (0")
    parser.add_argument("-t", dest="rdctable",  default="",  help="RDC input table (\"\")")
    parser.add_argument("-o", dest="outtable",  default="",  help="RDC output table (\"\")")
    parser.add_argument("-a", nargs=2, dest="anames",  default=["H","N"],  help="Atom names (\"\")")
    parser.add_argument("-s",  dest="saupe5",  default='',  help="Saupe matrix (\"\")")
    parser.add_argument("fileNames",nargs=1)
    args = parser.parse_args()
    print args
    mol = molio.readPDB(args.fileNames[0])
    if args.saupe5 != '':
        print 'saupe',args.saupe5
        sV = [-1.0*float(v) for v in args.saupe5.split()]
        aMat = AlignmentMatrix(sV[0],sV[1],sV[2],sV[3],sV[4])
    else:
        aCalc = calcAlign(mol,args)
        aMat = aCalc.getAlignment()


    aName1,aName2 = args.anames

    rdcVec = RDCVectors(mol, aName1, aName2)
    calcRDC(aMat, rdcVec)
    rdcDict = None
    if args.rdctable != "":
        rdcDict = readPalesTab(args.rdctable, args.delta)
    if args.outtable != "":
        dumpRDC(args.outtable, rdcVec, rdcDict)

