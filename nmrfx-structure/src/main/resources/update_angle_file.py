import sys 
from shutil import copyfile 

anglesFilename = 'angles.txt' 
measureFilename = sys.argv[1]
resNums = [int(i) for i in sys.argv[2].split(',')]

#resNum : [ss, pos, subtype]
#bulge2 {6:['Helix','0','hB0:B2Xe'],7:['Bulge','0','B2'],8:['Bulge','1','B2'],9:['Helix','0','hB1:B2Xb'],24:['Helix','0','hb1:B2xb'],25:['Helix','0','hb0:B2xe']}
#bulge2, opposite strand {6:['Helix','0','hb1:B2Xe'],7:['Helix','0','hb0:B2Xb],22:['Helix','0','hB0:B2xb'],23:['Bulge','1','B2'],24:['Bulge','0','B2'],25:['Helix','0','hB1:B2xe']}
#bulge3 {6:['Helix','0','hB0:B3Xe'],7:['Bulge','0','B3'],8:['Bulge','1','B3'], 9:['Bulge','2','B3'],10:['Helix','0','hB1:B3Xb'],24:['Helix','0','hb1:B3xb'],25:['Helix','0','hb0:B3xe']}
#bulge3, opposite strand {6:['Helix','0','hb1:B3Xe'],7:['Helix','0','hb0:B3Xb'],22:['Helix','0','hB0:B3xb'],23:['Bulge','0','B3c'], 24:['Bulge','1','B3c'],25:['Bulge','2','B3c'],26:['Helix','0','hB1:B3xe']}

def genBulgeLabels(resNums):
    '''
    generates labels for bulge residues, returns dictionary of resNum : [ss, pos, subtype]
    takes list of residue numbers in order from 5'-> 3', ex: [6,7,8,9,24,25]
    generalized to have 1 helix pair before and after the bulge, ie 4 helix residues total
    '''
    ssDict = {}
    hRes = 4 #2 residues on each end of the ss motif
    fwd = True if resNums[0] == resNums[1] - 1 and resNums[0] == resNums[2] - 2 else False 
    ssLen = len(resNums) - hRes 
    ss = 'B' + str(ssLen)
    for i, resNum in enumerate(resNums):
        if i == 0 or i == len(resNums) - 1:
            if i == 0:
                prefix = 'hB0' if fwd else 'hb1'
            else:
                prefix = 'hb0' if fwd else 'hB1'
            suffix = 'Xe' if i == 0 else 'xe'
            motif,pos = 'Helix','0'
        elif (fwd and i <= ssLen) or (not fwd and len(resNums) - ssLen - 1 <= i < len(resNums) - 1): 
            prefix = ''
            suffix = 'c' if not fwd else ''
            pos = i - 1 if fwd else i - 3 #bulge is followed by 3 helix residues, the closing res, and two on the opposite strand 
            motif = "Bulge"
        else:
            if fwd:
                prefix = 'hB1' if i == ssLen + 1 else 'hb1'
            else:
                prefix = 'hb0' if i == 1 else 'hB0'
            suffix = 'Xb' if (i == ssLen + 1 or (not fwd and i == 1)) else 'xb' 
            motif,pos = 'Helix','0'
        subtype = prefix+":"+ss+suffix if prefix else ss+suffix 
        ssDict[resNum] = [motif, str(pos), subtype] 
    return ssDict

def genLoopLabels(resNums):
    ssDict = {}
    ssLen = str(len(resNums)-2)
    ss = 'T' + ssLen
    for i, resNum in enumerate(resNums):
        if i == 0 or  i  == len(resNums) - 1: #first and last residues are helix residues before and after loop
            prefix = 'hL' if i == 0 else 'hl'
            suffix = 'Xe' if i == 0 else 'xe'
            pos = '0'
            motif = 'Helix'
            subtype = prefix+ssLen+suffix 
        else:
            pos = i-1
            motif = 'Loop'
            subtype = ss
        ssDict[resNum] = [motif, str(pos), subtype]
    return ssDict

def formatLine(line,id):
    ssType, index, rName, hasLink = line[:4]
    values = "{:3s} {:10s} {:3s} {:2s} {:7s}".format(str(id), ssType, index, rName, hasLink)
    values += ''.join([' {:16.10f}'.format(float(value)) if value != "-" else ' {:>16s}'.format(value) for value in line[4:]])
    return values

def getNewLines(ssDict):
    newLines = {}
    with open(measureFilename, 'r') as measureFile:
        header = measureFile.readline().strip().split()
        for lineNum,line in enumerate(measureFile):
            resNum = lineNum + 1
            newLine = []
            if resNum in ssDict:
                line = line.strip().split()
                for col, value in zip(header, line[1:]):
                    if col == "id":
                        continue
                    elif col == "ss":
                        newLine.append(ssDict[resNum][0])
                    elif col == "pos":
                        newLine.append(ssDict[resNum][1]) 
                    elif col == "type":
                        newLine.append(ssDict[resNum][2])
                    else:
                        newLine.append(value)
            if newLine:    
                index = newLine[1]
                ringType = newLine[2] 
                subtype = ssDict[resNum][-1]
                print(index,ringType,subtype)
                ref = index+"."+ringType+"."+subtype
                newLines[ref] = newLine
    return newLines
                
def replicateLine(subtype, keys, header, values):
    '''
    duplicates values except for ring type and N1 and N9 values 
    '''
    n9_idx = header.strip().split().index('N9') - 1
    n1_idx = header.strip().split().index('N1') - 1
    if subtype.replace('P','p') not in keys:
        n9 = float(values[n9_idx])
        values[n1_idx] = n9 + 180.0 if n9 < 0.0 else n9 - 180.0
        values[n9_idx] = '-'
        values[2] = 'p'
    elif subtype.replace('p','P') not in keys:
        n1 = float(values[n1_idx])
        values[n9_idx] = n1 - 180.0 if n1 > 0.0 else n1 + 180.0
        values[n1_idx] = '-'
        values[2] = 'P'
    else:
        return
    return values     

def updateTable(newLines,replicate=False):
    '''
    update angles.txt table based on subtype keys,
    if the subtype is not yet in the table, add new lines 
    saves file to backup before rewriting 
    replicate will generate a line for the other ring type ie P if p
    '''
    updateLines = {} 
    header = ""
    copyfile(anglesFilename, 'angles.backup')
    #replace lines if ref is in file 
    with open(anglesFilename,'r') as anglesFile:
        header = anglesFile.readline()
        for line in anglesFile:
            line = line.strip().split()
            index = line[2]
            ringType = line[3]
            subtype = line[4]
            ref = index+"."+ringType+"."+subtype
            if ref in newLines:
                updateLines[ref] = newLines[ref] 
            else:
                updateLines[ref] = line[1:] 
     #if ref is not in file, add lines 
        for newLine in newLines:
            if newLine not in updateLines:
                updateLines[newLine] = newLines[newLine] 
    id = 0
    replicate = False 
    with open(anglesFilename,'w') as anglesFile:
        anglesFile.write(header)
        for subtype,values in updateLines.items():
            line = formatLine(values,id)
            anglesFile.write(line+"\n")
            id += 1
            if replicate:
                values = replicateLine(subtype,updateLines, header,values)
                if values:
                    line = formatLine(values,id)
                    anglesFile.write(line+"\n")
                    id += 1

if all([resNums[i] == resNums[i+1] - 1 for i in range(len(resNums)-1)]):
    ssDict = genLoopLabels(resNums)                
else:
    ssDict = genBulgeLabels(resNums) 
print(ssDict)
newLines = getNewLines(ssDict)
updateTable(newLines)
 
