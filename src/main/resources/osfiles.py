import os
import os.path
import glob

def getDataDir(homeDir):
    if homeDir == None:
        homeDir =  os.getcwd( )
    return homeDir


def guessFiles(refiner,homeDir):
    if homeDir == None:
        homeDir =  os.getcwd( )

    seqFile = os.path.join(homeDir,'sequence.seq')
    if os.path.exists(seqFile):
        refiner.readSequence(seqFile)
    else:
        pdbFile = os.path.join(homeDir,'*.pdb')
        pdbFiles = glob.glob(pdbFile)
        if (len(pdbFiles) > 0):
            refiner.readPDBFile(pdbFiles[0])

    angleFiles = glob.glob(os.path.join(homeDir,'dihedral*.tbl'))
    for file in angleFiles:
        refiner.addAngleFile(file,'nv')
    disFiles = glob.glob(os.path.join(homeDir,'distance*.tbl'))
    for file in disFiles:
        refiner.addDistanceFile(file,'nv')

    angleFiles = glob.glob(os.path.join(homeDir,'dihedral*.txt'))
    for file in angleFiles:
        refiner.addAngleFile(file,'cyana')
    disFiles = glob.glob(os.path.join(homeDir,'*.upl'))
    for file in disFiles:
        file = file[:-4]
        refiner.addDistanceFile(file,'cyana')


def setOutFiles(refiner,homeDir, seed):
    refiner.outDir = os.path.join(homeDir,'output')
    refiner.eFileRoot = os.path.join(refiner.outDir,'energyDump'+str(seed))

def getAngleFile(refiner):
   angleFile = os.path.join(refiner.outDir,refiner.rootName+str(refiner.seed)+'.ang')
   return angleFile

def getPDBFile(refiner):
   pdbFile = os.path.join(refiner.outDir,refiner.rootName+str(refiner.seed)+'.pdb')
   return pdbFile

def logEnergy(refiner, energyStr):
    energyFile = open(os.path.join(refiner.outDir,"energies.txt"),"a")
    energyFile.write(energyStr)
    energyFile.close()

def getEnergyFile(refiner,mode=None):
   if mode == None:
       energyFile = os.path.join(refiner.outDir,refiner.rootName+str(refiner.seed)+'.txt')
   else:
       energyFile = os.path.join(refiner.outDir,refiner.eFileRoot+'_'+mode+'.txt')
   return energyFile

def getFiles(dataPath):
    files = glob.glob(dataPath)
    return files

def getFile(dataPath,type):
    files = glob.glob(dataPath)
    if len(files) == 1:
        return files[0] 
    elif len(files) == 0:
        print "Error, no files found for " + type
        print "Modify the search"
 
    else:
        print "Error, more than one file was identified for " + type
        print "Rerun with more selective search"

def getLines(inFile):
    file = open(inFile,'r')
    data = file.read()
    file.close()
    lines = data.split('\n')
    return lines

def splitLine(line):
    arr = line.split()
    return arr

def getFileName(file):
    return os.path.basename(os.path.normpath(file))

def writeHeader(outFile,headerInfo):
    target = open(outFile,'w')
    line = '\t'.join(headerInfo) + '\n'
    target.write(line)
    target.close()

def writeLines(data, outFile,header):
    if header == '':
        target = open(outFile,'w')
    else: 
        writeHeader(outFile,header)
        target = open(outFile,'a')
    for datum in data:
        datum = list(map(lambda i: str(i), datum)) 
        line = '\t'.join(datum) +'\n'
        target.write(line)
    target.close()

fileTypeLine = {'shift': ['atom','data'],
                'dis': ['atom','atom','data','data'],
                'seq': ['data','index']}
fileName = {'shift': 'ppm.out', 'dis':'dis.tbl','seq':'sequence.seq'}
def getPatternRanges(inputPattern):
    #Parses the input range as a string and returns a nested list of integers for residue indices
    ranges = inputPattern.split(',')
    for i in xrange(len(ranges)):
        resIndices = ranges[i].split(':')
        for j in xrange(len(resIndices)):
            resIndices[j] = int(resIndices[j])
        ranges[i] = resIndices
    return ranges

def limResidues(pattern,inFile,dataDir,fileType):
#where fileType will be dis, shift, or seq
    ranges = getPatternRanges(pattern)
    ptrnMap = makePatternMap(ranges)
    lines = getLines(inFile)
    limLines = []
    for line in lines:
        if line == "":
            continue
        fixedLine = correctLine(line,fileType,ptrnMap)
        if fixedLine == -1:
            continue
        limLines.append(fixedLine)
    outFile = dataDir + fileName[fileType]
    writeLines(limLines,outFile,'')
    return outFile

def correctLine(line,fileType,ptrnMap):
    arr = line.split()
    correctArr = []
    lineFormat = fileTypeLine[fileType]
    for i in xrange(len(lineFormat)):
        resIndex = 0
        if lineFormat[i] == 'atom':
            resIndex = arr[i].split('.')[0]
            atomType = arr[i].split('.')[1]
            resIndex = getNewResidueIndex(resIndex,ptrnMap)

            correctArr.append(getAtomName([resIndex,atomType]))
        elif lineFormat[i] == 'index':
            resIndex = arr[i]
            resIndex = getNewResidueIndex(resIndex,ptrnMap)

            correctArr.append(resIndex)
        else:
            correctArr.append(arr[i])
        resIndex = int(resIndex)
        if int(resIndex) > len(ptrnMap) or resIndex == -1:
            return -1
    return correctArr

def getNewResidueIndex(resIndex,ptrnMap):
    resIndex = int(resIndex)
    if resIndex > len(ptrnMap):
        return -1
    resIndex = ptrnMap[resIndex-1]
    return str(resIndex)

def makePatternMap(ranges):
    max = ranges[len(ranges)-1][1]
    original = map(lambda x: x+1,range(max))
    mapped = []
    newResidues = 1;
    for i in range(len(original)):
        if checkInclude(ranges,original[i]):
            mapped.append(newResidues)
            newResidues+=1
        else:
            mapped.append(-1)
    return mapped

def checkInclude(ranges, resIndex):
    if isinstance(resIndex,list):
        return checkInclude(ranges,resIndex[0]) and checkInclude(ranges,resIndex[1])
    for range in ranges:
        if resIndex >= range[0] and resIndex <= range[1]:
            return 1
    return 0



def convertStarFile(inFile,dataDir):
    lines = getLines(inFile)
    data = []
    for line in lines:
        arr = line.split()

        if len(arr) != 5 or arr[0] == '#':
             continue
        resIndex = arr[1]
        aType = arr[3]
        shift = arr[4]
        if shift == '.':
            continue
        name = '.'.join([resIndex,aType])
        data.append([name,shift])
    outFile = os.path.join(dataDir,fileName['shift'])
    writeLines(data,outFile,'')
    return outFile

def convertSeqFile(inFile,dataDir):
    lines = getLines(inFile)
    data = []
    resIndex = 1
    for res in lines[1]:
        residue = []
        residue.append(res.upper())
        residue.append(resIndex)
        data.append(residue)
        resIndex += 1
    outFile = os.path.join(dataDir,fileName['seq'])
    writeLines(data,outFile,'')
    return outFile

def convertConstraintFile(inFile,dataDir):
    lines = getLines(inFile)

    constraints = []
    for line in lines:
        line = line.strip();
        if len(line)<=1:
            continue;
        if line[0] != '#':
            continue;
        data =  line.split()
        constraint = getConstraint(data)
        constraints.append(constraint)
    
    outFile = os.path.join(dataDir,fileName['dis'])
    writeLines(constraints, outFile,'')
    return outFile


def getConstraint(data):
    constraintParams = []
    distances = data[-2:]
    atom1 = getAtomName(data[1:4])
    atom2 = getAtomName(data[4:7])
    constraintParams.append(atom1)
    constraintParams.append(atom2)
    constraintParams += distances;
    return constraintParams

def getAtomName(atomData):
    if len(atomData) == 3:
        atom = str(atomData[0]) + "." + atomData[2]
    elif len(atomData) == 2:
        atom = str(atomData[0]) + "." + atomData[1]
    return atom;

#TESTCASE
#limResidues('1:20,27:41','../data/descriptions/sl2.tbl','../data/descriptions/','tbl')
