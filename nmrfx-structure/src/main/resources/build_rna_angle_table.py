import os
import sys
import molio
import measure
import collections
import refine

from org.nmrfx.structure.rna import SSGen

def dumpLine(ss, residues, residue, index):
    res = int(residue.getNumber())
    ssType = ss.getName()
    hasLink = refine.getRNAResType(ss, residues, residue)
    rName = residue.getName()
    if rName == 'A' or rName == 'G':
        rName = 'P'
    else:
        rName = 'p'
    if ssType == "Helix":
        index = 0
    outLine = "%10s %3d %2s %7s" % (ssType, index, rName,  hasLink)
    shiftDict={}
    for aName in aNames:
        if aName in resDict[res]:
            angle = resDict[res][aName]
            outLine += " %16.10f" % (angle)
            shiftDict[aName] = angle
        else:
            outLine += " %16s" % ('-')
    return shiftDict,outLine

def hasLink(residue, resDict):
    res = int(residue.getNumber())
    if 'X1' in resDict[res]:
        return True
    return False

def areShiftsUnique(globalDict, shiftDict):
    matches = False
    iMatch = -1
    for i,sDict in enumerate(globalDict):
        matches = True
        for key in sDict:
            if key in shiftDict:
                delta = abs(sDict[key] - shiftDict[key])
                if delta > 1e-4:
                    matches = False
            else:
                matches = False
        for key in shiftDict:
            if key in sDict:
                delta = abs(sDict[key] - shiftDict[key])
                if delta > 1e-4:
                    matches = False
            else:
                matches = False
        if matches:
            iMatch = -i
            break
        
    if not matches:
        iMatch = len(globalDict)
        globalDict.append(shiftDict)
    return iMatch
                

def scanPDB(fileName, aNames, allMode, vienna=None):
    mol = molio.readPDB(fileName)
    if vienna == None:
        vienna,dotBracketDict = measure.rnaDotBracket(mol)
    ssGen = SSGen(mol, vienna)
    ssGen.analyze()
    allShifts=[]
    for ss in ssGen.structures():
        start = 0
        residues = ss.getResidues()
        for residue in residues:
            index = start
            if ss.getName() == "Helix":
                index /= 2
            shiftDict, outLine = dumpLine(ss,residues, residue,index)
            iMatch = areShiftsUnique(allShifts, shiftDict)
            if iMatch >= 0 or allMode:
                print(str(iMatch)+' '+outLine)
            start += 1

def scanAngles(fileName):
    atomDict = collections.OrderedDict()
    resDict = {}
    residues = {}
    with open(fileName, 'r') as f1:
        for line in f1:
             line = line.strip()
             fields = line.split()
             fullSpec = fields[0]
             chainRes = fullSpec.split('.')[0]
             (chain, res) = chainRes.split(':')
             res = int(res)
             atom = fullSpec.split('.')[1]
             if atom == "O2'":
                 continue
             if atom == "O3'":
                 atom = "-1:O3'"
                 res = res + 1
    
             if not atom in atomDict:
                 atomDict[atom] = {}
             atomDict[atom][res] = float(fields[1])
             if not res in resDict:
                 resDict[res] = {}
             resDict[res][atom] =  float(fields[1])
    return atomDict,resDict

def writeHeader():
    outLine = "%3s %10s %3s %2s %7s" % ('id', 'ss', 'pos','nuc','type')
    for aName in aNames:
        outLine += " %16s" % (aName)
    print(outLine)



if len(sys.argv) > 2:
    allMode = False
    if len(sys.argv) > 3:
        allMode = sys.argv[3] == 'all'
    angleFile = sys.argv[1]
    pdbFile = sys.argv[2]
    atomDict,resDict = scanAngles(angleFile)
    aNames = atomDict.keys()
    writeHeader()
    scanPDB(pdbFile, aNames, allMode)
