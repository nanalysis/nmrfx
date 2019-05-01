import predtrain
import sys, argparse, os
import seqalgs

allowedAtoms = ['H', 'Hr', 'Hn', 'C', 'Cr', 'Cn']
allowedAtoms += ["H2","H8","H5","H6","\"H1'\"","\"H2'\"","\"H3'\"","\"H4'\"","\"H5'\"","\"H5''\""]
allowedAtoms += ["C2","C8","C5","C6","\"C1'\"","\"C2'\"","\"C3'\"","\"C4'\"","\"C5'\""]

allowedAtomsH = ['H (all H)', 'Hr (ribose H)', 'Hn (base H)', 'C (all C)', 'Cr (ribose C)', 'Cn (base C)']
allowedAtomsH += ["H2","H8","H5","H6","\"H1'\"","\"H2'\"","\"H3'\"","\"H4'\"","\"H5'\"","\"H5''\""]
allowedAtomsH += ["C2","C8","C5","C6","\"C1'\"","\"C2'\"","\"C3'\"","\"C4'\"","and \"C5'\""]

gatomNames = {}
gatomNames['H'] = ["A.H2","A.H8","G.H8","C.H5","U.H5","C.H6","U.H6","A.H1'","G.H1'","C.H1'","U.H1'","H2'","H3'","H4'","H5'","H5''"]
gatomNames['C'] = ["A.C2","A.C8","G.C8","C.C5","U.C5","C.C6","U.C6","A.C1'","G.C1'","C.C1'","U.C1'","C2'","C3'","C4'","C5'"]
gatomNames['Hr'] = ["A.H1'","G.H1'","C.H1'","U.H1'","H2'","H3'","H4'","H5'","H5''"]
gatomNames['Cr'] = ["A.C1'","G.C1'","C.C1'","U.C1'","C2'","C3'","C4'","C5'"]
gatomNames['Hn'] = ["A.H2","A.H8","G.H8","C.H5","U.H5","C.H6","U.H6"]
gatomNames['Cn'] = ["A.C2","A.C8","G.C8","C.C5","U.C5","C.C6","U.C6"]
gatomNamesAll = ["A.H2","A.H8","G.H8","C.H5","U.H5","C.H6","U.H6","A.H1'","G.H1'","C.H1'","U.H1'","H2'","H3'","H4'","H5'","H5''"]
gatomNamesAll += ["A.C2","A.C8","G.C8","C.C5","U.C5","C.C6","U.C6","A.C1'","G.C1'","C.C1'","U.C1'","C2'","C3'","C4'","C5'"]

def getAtomNames(atomNameList):
    atomNames = []
    for atom in atomNameList:
        if atom in gatomNames:
            atomNames = gatomNames[atom]
        else:
            indices = [i for i, e in enumerate(gatomNamesAll) if (e[2:] == atom) or (e == atom)]
            atomNames += [gatomNamesAll[index] for index in indices]
    return atomNames


def addParseArgs(parser):
    """
    Add allowed command line arguments to the argument parser.

    # Parameters:

    parser (Argument Parser); Parser to add arguments to.

    # Returns:
    N/A. Modifies the parser in place.
    """

    parser.add_argument('atomNameList', nargs='*', help="List of atom types. Allowed types are " + ", ".join(allowedAtomsH))
    parser.add_argument("-c", "--calcType", default="rc", help="Calculation type: distance (dist) or ring current shift (rc). Default is rc.")
    parser.add_argument("-r", "--ringMode", action="store_true", help="Whether to use ringMode=True")
    parser.add_argument("-b", "--builtin", action="store_true",default=False, help="Whether to skip training and use built-in values=False")
    parser.add_argument("-t", "--trainFile", default="trainfiles.txt",
                            help="Text file with training set file information. Default is trainfiles.txt")
    parser.add_argument("-T", "--testFile", default="testfiles.txt",
                            help="Text file with test set file information. Default is testfiles.txt")
    parser.add_argument("-m", "--matrixFile", default="rcvals.txt",
                            help="Text file with the training matrix. Default is rcvals.txt")

def defineParseArgs(args):
    """
    Define variables based on command line arguments.

    # Parameters:

    parser (Argument Parser); Parser to add arguments to.

    # Returns:
    type (String); Calculation type: dist or rc
    ringMode (boolean); True: use ringMode=True, False: use ringMode=False.
    atomNames (list); List of atom names.
    trainFile (String); Name of the file that contains the training set information.
    testFile (String); Name of the file that contains the testing set information.
    matrixFile (String); Name of the file that contains the training matrix information.
    """

    type = args.calcType
    ringMode = args.ringMode
    atomNameList = args.atomNameList
    trainFile = args.trainFile
    testFile = args.testFile
    matrixFile = args.matrixFile
    builtin = args.builtin

    #print atomNameList
    # if type != 'dist' and type != 'rc':
    #     parser.error("Invalid calculation type " + type + ". Allowed types are dist or rc.")

    if not all(elem in allowedAtoms for elem in atomNameList):
        notAllowed = list(set(atomNameList) - set(allowedAtoms))
        parser.error("Invalid atom(s) " + " ,".join(notAllowed) + ". Allowed atoms are " + ", ".join(allowedAtomsH))

    print 'type is', type
    print 'ringMode is', ringMode
    print trainFile, testFile, matrixFile

    return type, ringMode, atomNameList, trainFile, testFile, matrixFile, builtin

def appendOffsets(trainFileName, testFileName, offsetDict):
    """
    Append offset values from a dictionary to the corresponding lines in the training and testing files.

    # Parameters:

    trainFileName (String); Text file with the PDB and BMRB ID numbers of the files in the training set.
    testFileName (String); Text file with the PDB and BMRB ID numbers of the files in the test set.
    offsetDict (dict); The dictionary of the offset values (author seq ID - seq ID).

    # Returns:
    N/A. Modifies the training and test files in place.
    """

    fileNames = [trainFileName, testFileName]
    for name in fileNames:
        lines = []
        with open(name,'r') as f1:
            for line in f1:
                line = line.strip()
                if (len(line) == 0) or (line.startswith("#")):
                    lines.append(line)
                    continue
                pdb,bmrb = line.split()[0], line.split()[1]
                if bmrb in offsetDict.keys():
                    for key in offsetDict[bmrb].keys():
                        if key not in line:
                            line += " " + key + " " + str(offsetDict[bmrb][key])
                lines.append(line)
        f1.close()

        with open(name, 'w') as fw:
            for line in lines:
                fw.write(line+"\n")
        fw.close()

def makeOffsetDict(trainFileName, testFileName):
    """
    Create a dictionary of offset values needed to account for discrepancies in the sequence ID numbers in the BMRB files.

    # Parameters:

    trainFileName (String); Text file with the PDB and BMRB ID numbers of the files in the training set.
    testFileName (String); Text file with the PDB and BMRB ID numbers of the files in the test set.

    # Returns:
    offsetDict (dict); The dictionary of the offset values (author seq ID - seq ID).
    """

    bmrbs = []
    fileNames = [trainFileName, testFileName]
    for name in fileNames:
        if name == None:
            continue
        with open(name,'r') as f1:
            for line in f1:
                line = line.strip()
                if (len(line) == 0) or (line.startswith("#")):
                    continue
                bmrb = line.split()[1]
                if bmrb.startswith(".") or bmrb.startswith("(") or bmrb.startswith(")"):
                    continue
                else:
                    bmrbs.append(bmrb)
    for bmrbID in bmrbs:
        bmrbFile = 'star/bmr'+bmrbID+'.str'
        if not os.path.exists(bmrbFile):
            bmrbFile = 'star2/bmr'+bmrbID+'.str'
            if not os.path.exists(bmrbFile):
                print 'skip',bmrbFile
                continue
        offsetDict = seqalgs.readBMRBOffsets(bmrbID, bmrbFile)
    return offsetDict


def dumpRefShifts(varName, coefDict):
    keys = coefDict.keys()
    keys.sort()
    for key in keys:
        print varName+'.put("'+key+'", '+str(coefDict[key])+');'

def dumpAlphas(eName, alphaDict):
    keys = alphaDict.keys()
    keys.sort()
    for key in keys:
        print 'double[] '+key+eName+'Alphas = {'+str(alphaDict[key])[1:-2]+'};'

def train(atomNameList, trainFile, testFile, matrixFile, ringMode, type):
    #print atomNames
    offsetDict = makeOffsetDict(trainFile, testFile) #{'15857': {'B': 58}, '15858': {'A': 12, 'B': 58}, '18893': {'B': 58}, '19662': {'B': 100}}
    print "offsetDict = ", offsetDict
    offsets = {}
    aType = atomNameList[0][0]


    if type == "rc":
        atomNames = getAtomNames(atomNameList)
        coefDict,ringRatio = predtrain.trainRC(atomNames, trainFile, matrixFile, ringMode, type)
        for aName in coefDict:
            coefDict[aName] = round(coefDict[aName],3)
        alphasDict = round(ringRatio,3)
    else:
        alphasDict={}
        coefDict={}
        thisDict={}
        for atomName in atomNameList:
            dictName = 'base'
            if atomName[-1] == 'r':
                dictName = 'ribose'
            thisDict,alphasDict[dictName] = predtrain.trainRC(gatomNames[atomName], trainFile, matrixFile, ringMode, type)
            for aName in thisDict:
                coefDict[aName] = round(thisDict[aName],3)

        if not 'ribose' in alphasDict:
            alphasDict['ribose'] = alphasDict['base']
        alphasDict['ribose'] = [round(v,3) for v in alphasDict['ribose']]
        alphasDict['base'] = [round(v,3) for v in alphasDict['base']]
        dumpAlphas(atomNameList[0][0], alphasDict)

    print coefDict,alphasDict
    bmrbs,pdbs = predtrain.readTestFiles(testFile)
    dumpRefShifts("RNA_REF_SHIFTS",coefDict)

    ppmDatas,aNames = predtrain.analyzeFiles(pdbs, bmrbs, type, aType, offsets, coefDict, alphasDict)
    #predtrain.dumpPPMData(ppmDatas)
    predtrain.reref(ppmDatas, bmrbs)
    nTotal,sumAbs = predtrain.getSumAbs(ppmDatas)
    print "nAtoms %4d MAE %4.2f" % (nTotal,sumAbs)
    predtrain.removeOutliers(aNames, ppmDatas)
    nFinal,sumAbs = predtrain.getSumAbs(ppmDatas)
    print "nAtoms %4d MAE %4.2f" % (nFinal,sumAbs)
    predtrain.getAtomStats(aNames, ppmDatas)

def testBuiltin(atomNameList,  testFile,  type):
    aType = atomNameList[0][0]
    offsetDict = makeOffsetDict(None, testFile) #{'15857': {'B': 58}, '15858': {'A': 12, 'B': 58}, '18893': {'B': 58}, '19662': {'B': 100}}
    print "offsetDict = ", offsetDict
    offsets = {}
    bmrbs,pdbs = predtrain.readTestFiles(testFile)
    atomNames = getAtomNames(atomNameList)
    ppmDatas,aNames = predtrain.analyzeFiles(pdbs, bmrbs, type, aType, offsets, None, None,atomNames,  True)
    predtrain.reref(ppmDatas, bmrbs)
    nTotal,sumAbs = predtrain.getSumAbs(ppmDatas)
    print "nAtoms %4d MAE %4.2f" % (nTotal,sumAbs)
    predtrain.removeOutliers(aNames, ppmDatas)
    nFinal,sumAbs = predtrain.getSumAbs(ppmDatas)
    print "nAtoms %4d MAE %4.2f" % (nFinal,sumAbs)
    predtrain.getAtomStats(aNames, ppmDatas)

parser = argparse.ArgumentParser()
addParseArgs(parser)
args = parser.parse_args()
type, ringMode, atomNameList, trainFile, testFile, matrixFile, builtin = defineParseArgs(args)

if builtin:
    testBuiltin(atomNameList, testFile, type)
else:
    train(atomNameList, trainFile, testFile, matrixFile, ringMode, type)
