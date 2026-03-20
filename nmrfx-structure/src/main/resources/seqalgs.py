import math
import sys
from org.nmrfx.star import STAR3Base
def loadResProps(fileName):
    global resProps
    resProps = {}
    f1 = open(fileName,'r')
    headers = f1.readline().strip().split(',')
    for line in f1:
        fields = line.strip().split(',')
        for (header,value) in zip(headers[1:],fields[1:]):
            resProps[fields[0],header] = float(value)

def readBMRBShifts(bmrbID, fileName):
    f1 = open(fileName)
    lines = f1.read().split('\n')
    readSequence(bmrbID,lines)
    shiftDict = processBMRBLines(bmrbID,lines)
    f1.close()
    return shiftDict

def readBMRBOffsets(bmrbID, fileName):
    f1 = open(fileName)
    lines = f1.read().split('\n')
    readSequence(bmrbID,lines)
    offsetDict = makeOffsetDict(bmrbID,lines)
    f1.close()
    return offsetDict

def readSequence(bmrbID,lines):
    global shiftDict
    global resDict
    shiftDict = {}
    resDict = {}
    chain = 1
    shiftDict[bmrbID] = {}
    shiftDict[bmrbID][chain] = {}
    start_trigger=0
    end_trigger=0
    assignment_list=[]
    state = 'searching'
    hasAuthCode = False
    for line in lines:
        fields = line.strip().split()
        if len(fields) > 0:
            if (fields[0] == '_Saveframe_category' and fields[1] == 'monomeric_polymer'):
                state = 'inSave'
            elif state == 'inSave':
                if fields[0] == "_Residue_author_seq_code":
                    hasAuthCode = True
                if fields[0] == "_Residue_label":
                    state = 'inSeq'
            elif state == 'inSeq':
                if fields[0] == STAR3Base.STOP:
                    break

                if len(fields) > 2:
                    if hasAuthCode:
                        for (resStr,resAuthStr,resName) in zip(fields[0::3],fields[1::3],fields[2::3]):
                            res = int(resStr)
                            if not res in shiftDict[bmrbID][chain]:
                                shiftDict[bmrbID][chain][res] = {}
                                resDict[bmrbID,chain,res] = resName
                    else:
                        for (resStr,resName) in zip(fields[0::2],fields[1::2]):
                            res = int(resStr)
                            if not res in shiftDict[bmrbID][chain]:
                                shiftDict[bmrbID][chain][res] = {}
                                resDict[bmrbID,chain,res] = resName


def processBMRBLines(bmrbID, lines):
    global shiftDict
    global resDict
    start_trigger=0
    end_trigger=0
    assignment_list=[]
    state = 'preShifts'
    shiftLoopTags = []
    for line in lines:
        fields = line.strip().split()
        if len(fields) > 0:
            if state == 'preShifts':
                if fields[0] == "_Atom_shift_assign_ID":
                    state = 'inShiftCat'
                    starMode = 2
                elif fields[0] == "_Atom_chem_shift.ID":
                    state = 'inShiftCat'
                    starMode = 3
            if state == 'inShiftCat':
                if fields[0].startswith("_"):
                    shiftLoopTags += fields
                else:
                    state = "inShifts"
                
            if state == 'inShifts':
                if fields[0] == STAR3Base.STOP:
                    break
                #print shiftLoopTags
                #print fields
                if starMode == 3:
                    chainID = shiftLoopTags.index("_Atom_chem_shift.Entity_ID")
                    chain = int(fields[chainID])
                    resI = shiftLoopTags.index("_Atom_chem_shift.Seq_ID")
                    resIa = shiftLoopTags.index("_Atom_chem_shift.Auth_seq_ID")
                    anameI = shiftLoopTags.index("_Atom_chem_shift.Atom_ID")
                    resNameI = shiftLoopTags.index("_Atom_chem_shift.Comp_ID")
                    shiftI = shiftLoopTags.index("_Atom_chem_shift.Val")
                else:
                    chain = 1
                    resI = shiftLoopTags.index("_Residue_seq_code")
                    resIa = shiftLoopTags.index("_Residue_author_seq_code")
                    anameI = shiftLoopTags.index("_Atom_name")
                    resNameI = shiftLoopTags.index("_Residue_label")
                    shiftI = shiftLoopTags.index("_Chem_shift_value")
                if not chain in shiftDict[bmrbID]:
                    shiftDict[bmrbID][chain] = {}
                res = int(fields[resI])
                if (fields[resIa] != ".") and (fields[resIa] != fields[resI]):
                    res = int(fields[resIa])
                shift = float(fields[shiftI])
                aname = fields[anameI].strip('"')
                resName = fields[resNameI]
                if not res in shiftDict[bmrbID][chain]:
                    shiftDict[bmrbID][chain][res] = {}
                    resDict[bmrbID,chain,res] = resName
                shiftDict[bmrbID][chain][res][aname] = shift
    return shiftDict

offsetDict = {}
def makeOffsetDict(bmrbID, lines):
    state = 'preShifts'
    shiftLoopTags = []
    for line in lines:
        fields = line.strip().split()
        if len(fields) > 0:
            if state == 'preShifts':
                if fields[0] == "_Atom_shift_assign_ID":
                    state = 'inShiftCat'
                    starMode = 2
                elif fields[0] == "_Atom_chem_shift.ID":
                    state = 'inShiftCat'
                    starMode = 3
            if state == 'inShiftCat':
                if fields[0].startswith("_"):
                    shiftLoopTags += fields
                else:
                    state = "inShifts"
                
            if state == 'inShifts':
                if fields[0] == STAR3Base.STOP:
                    break
                #print shiftLoopTags
                #print fields
                if starMode == 3:
                    chainID = shiftLoopTags.index("_Atom_chem_shift.Entity_ID")
                    chain = int(fields[chainID])
                    resI = shiftLoopTags.index("_Atom_chem_shift.Seq_ID")
                    resIa = shiftLoopTags.index("_Atom_chem_shift.Auth_seq_ID")
                else:
                    chain = 1
                    resI = shiftLoopTags.index("_Residue_seq_code")
                    resIa = shiftLoopTags.index("_Residue_author_seq_code")
                res = int(fields[resI])
                if (fields[resIa] != ".") and (fields[resIa] != fields[resI]):
                    res = int(fields[resIa])
                    offset = float(fields[resIa]) - float(fields[resI])
                    if not bmrbID in offsetDict:
                        offsetDict[bmrbID] = {}
                    if not chain in offsetDict[bmrbID]:
                        offsetDict[bmrbID][chr(chain+64)] = offset   
    
    return offsetDict

def openCorrTable(fileName):
    global corrTable
    f1 = open(fileName,'r')
    line = f1.readline()
    line = line.strip()
    headers = line.split('\t')
    anames = ('N','CA','CB','C','HA','H')
    corrTable = {}
    for line in f1:
        line = line.strip()
        fields = line.split('\t')
        values = {}
        aa = fields[1]
        for (header,field) in zip(headers[2:],fields[2:]):
            if field == '':
                values[header] = ''
            else:
                values[header] = float(field)
        for aname in anames:
            for dname,delta in zip(('A','B','C','D'),(-2,-1,1,2)):
                corrTable[aname,delta,aa] = values[aname+'_'+dname]
            corrTable[aname,aa] = values[aname]
    f1.close()
       
 
def loadShiftFile(fileName, aname):
    global shiftDict
    global resDict
    f1 = open(fileName,'r')
    for line in f1:
        fields = line.strip().split(',')
        if len(fields) > 5:
            pdbFile = fields[0].split('/')[-1]
            #print pdbFile,fields[1],fields[-1]
            resName = fields[1].strip()
            (pdb,chain,res) = pdbFile.split(':')
            res = int(res)
            if not pdb in shiftDict:
                shiftDict[pdb] = {}
            if not chain in shiftDict[pdb]:
                shiftDict[pdb][chain] = {}
            if not res in shiftDict[pdb][chain]:
                shiftDict[pdb][chain][res] = {}
            shiftDict[pdb][chain][res][aname] = float(fields[-1])
            resDict[pdb,chain,res] = resName

def absShifts(shiftValues):
    n = len(shiftValues)
    corrShifts = []
    for i in range(n):
        shift = None
        if shiftValues[i] != None:
            shift = abs(shiftValues[i])
        corrShifts.append(shift)
    return corrShifts


def offsetShifts(residueNames,shiftValues, aname):
    global corrTable
    n = len(shiftValues)
    corrShifts = []
    corrValues = []
    for i in range(n):
        shift = None
        corr = 0.0
        if shiftValues[i] != None:
            resName = residueNames[i]
            corr = corrTable[aname,resName]
            
            for j in (-2,-1,1,2):
                k = i+j
                if k >= 0 and k < n:
                    corrRes = residueNames[k]
                    corr += corrTable[aname,-j,corrRes]
            shift = shiftValues[i] - corr
        corrValues.append(corr)
        corrShifts.append(shift)
    return (corrShifts,corrValues)
        
def scaleShiftsByFreq(shiftValues,scale,min):
    n = len(shiftValues)
    for i in range(n):
        if shiftValues[i] != None:
            shiftValues[i] = shiftValues[i]*scale
            if abs(shiftValues[i]) < min:
                shiftValues[i] = math.copysign(min,shiftValues[i])

def scaleShifts(shiftValues,scale):
    n = len(shiftValues)
    for i in range(n):
        if shiftValues[i] != None:
            shiftValues[i] =shiftValues[i]*scale
    
def addShifts(sumValues,counts,shiftValues):
    n = len(shiftValues)
    for i in range(n):
        if shiftValues[i] != None:
            sumValues[i] += shiftValues[i]
            counts[i] += 1
    
def invertSums(sumValues,counts,maxValue):
    n = len(sumValues)
    for i in range(n):
        if sumValues[i] != None and counts[i] != 0:
            sumValues[i] /= counts[i]
            sumValues[i] = 1.0/(sumValues[i]**1.5)
            if sumValues[i] > maxValue:
                sumValues[i] = maxValue 

    

# need to consider if there are gaps in residues
def smoothShiftsSavGol(shiftValues):
    n = len(shiftValues)
    corrShifts = []
    weights = (-3,12,17,12,-3)
    deltas = (-2,-1,0,1,2)
    for i in range(n):
        sum = 0.0
        nShifts = 0
        for (delta,weight) in zip(deltas,weights):
            k = i+delta
            if k >= 0 and k < n:
                if shiftValues[k] != None:
                    sum += shiftValues[k]*weight
                    nShifts += weight
        avgShift = None
        if nShifts > 0:
            avgShift = sum/nShifts
        corrShifts.append(avgShift)
    return corrShifts
    
# need to consider if there are gaps in residues
def smoothShifts(shiftValues):
    n = len(shiftValues)
    corrShifts = []
    weights = (1,1,1,1,1)
    deltas = (-2,-1,0,1,2)
    weights = (1,2,1)
    deltas = (-1,0,1)
    weights = (1,1,1)
    deltas = (-1,0,1)
    for i in range(n):
        sum = 0.0
        nShifts = 0
        for (delta,weight) in zip(deltas,weights):
            k = i+delta
            if k >= 0 and k < n:
                if shiftValues[k] != None:
                    sum += shiftValues[k]*weight
                    nShifts += weight
        avgShift = None
        if nShifts > 0:
            avgShift = sum/nShifts
        corrShifts.append(avgShift)
    return corrShifts

def correctEnds(shiftValues,maxRCI):
    n = len(shiftValues)
    corrShifts = []
    endShifts = shiftValues[0:4]
    maxValue = max(endShifts)
    maxIndex = endShifts.index(maxValue)
    for i in range(maxIndex):
        value= shiftValues[i]
        if (value < maxValue):
            delta = abs(value-maxValue)
            shiftValues[i] += delta*2.0
            if (shiftValues[i] > maxRCI):
                shiftValues[i] = maxRCI
    endShifts = shiftValues[-4:]
    maxValue = max(endShifts)
    maxIndex = endShifts.index(maxValue)
    n = len(shiftValues)
    for i in range(4-maxIndex):
        j = n-i-1
        value= shiftValues[j]
        if (value < maxValue):
            delta = abs(value-maxValue)
            shiftValues[j] += delta*2.0
            if (shiftValues[j] > maxRCI):
                shiftValues[j] = maxRCI
    
def loadShifts(mode):
    global shiftDict
    global resDict
    global shifts
    shiftDict = {}
    resDict = {}
    anames = ('CA','N','CB','C','HA','H')
    for aname in anames:
        loadShiftFile('all'+aname+mode+'.arff',aname)

def fixCYSOx(residueNames, cbShifts):
    n = len(residueNames)
    for i in range(n):
        resName = residueNames[i]
        if resName == 'CYS':
            if cbShifts[i] > 35.0:
                residueNames[i] = 'CYD'

def calcRCI():    
    global shiftDict
    global resDict
    global shifts
    shifts = {}
    corrValues = {}
    offshifts = {}
    offshiftsAbs = {}
    anames = ('CA','CB','C','N','H','HA')
    scale = {'C':2.5,'N':1.0,'H':10}
    multipliers = {'CA':0.72,'CB':0.15,'C':0.72,'N':0.59,'HA':0.85,'H':0.13}
    multipliers = {'CA':0.81,'CB':0.15,'C':0.78,'N':0.43,'HA':0.90,'H':0.18}
    useAtom = {'CA':1,'CB':1,'C':1,'N':1,'HA':1,'H':0}

    for pdb in shiftDict:
        chains = shiftDict[pdb]
        for chain in chains:
            residues = shiftDict[pdb][chain].keys()
            residues.sort()
            residueNames = []
            for residue in residues:
                resName = resDict[pdb,chain,residue]
                residueNames.append(resName)
            for aname in anames:
                shifts[aname] = []
                for residue,resName in zip(residues,residueNames):
                    shift = None
                    if residue in shiftDict[pdb][chain]:
                        if resName == "GLY" and aname == "HA":
                            sumHA = 0.0
                            nHA = 0
                            if "HA2" in shiftDict[pdb][chain][residue]:
                                sumHA += shiftDict[pdb][chain][residue]["HA2"]
                                nHA += 1
                            if "HA3" in shiftDict[pdb][chain][residue]:
                                sumHA += shiftDict[pdb][chain][residue]["HA3"]
                                nHA += 1
                            if nHA > 0:
                                shift = sumHA/nHA
                        else:
                            if aname in shiftDict[pdb][chain][residue]:
                                shift = shiftDict[pdb][chain][residue][aname]
                    shifts[aname].append(shift)
            fixCYSOx(residueNames, shifts['CB'])
            sumShifts = [0.0]*len(shifts['CA'])
            counts = [0]*len(shifts['CA'])
            for aname in anames:
                (offshifts[aname],corrValues[aname]) = offsetShifts(residueNames, shifts[aname], aname)
                offshiftsAbs[aname] = absShifts(offshifts[aname])

                #offshifts[aname] = smoothShifts(offshifts[aname])
                scaleShiftsByFreq(offshifts[aname],scale[aname[0]], 0.5)
                #scaleShifts(offshifts[aname],5.0*multipliers[aname])

                offshiftsAbs[aname] = smoothShifts(offshiftsAbs[aname])
                scaleShiftsByFreq(offshiftsAbs[aname],scale[aname[0]], 0.5)
                scaleShifts(offshiftsAbs[aname],5.0*multipliers[aname])
                if useAtom[aname]:
                    addShifts(sumShifts,counts,offshiftsAbs[aname])
            invertSums(sumShifts,counts,0.8)
            correctEnds(sumShifts, 0.6)
            sumShifts = smoothShiftsSavGol(sumShifts)
            if True:
                i = 0
                for (residueNum, residueName, rci, count) in zip(residues,residueNames,sumShifts,counts):
                    if (count > 0):
                        rci = rci 
                        mdRMSD = rci*29.55
                        nmrRMSD = rci*16.44
                        s2 = 1.0-0.4*math.log(1+rci*17.7)
                        print "%s %s %d %3s %.5f %.5f %.5f %.5f" % (pdb,chain,residueNum,residueName,rci,mdRMSD,nmrRMSD,s2),
                        for aname in anames:
                            print aname,shifts[aname][i],corrValues[aname][i],
                        for aname in anames:
                            print aname,offshiftsAbs[aname][i],
                        print ""
                        i += 1

        ok = True
#loadResProps('resprops.txt')
#openCorrTable('corrtable.txt')
#calcRCI
