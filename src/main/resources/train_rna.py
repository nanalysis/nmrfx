import predtrain
import rnapred
import sys, argparse, os
import seqalgs
from org.nmrfx.structure.chemistry.predict import RNAAttributes


allowedAtoms = ['H', 'Hr', 'Hn', 'C', 'Cr', 'Cn']
allowedAtoms += ["H2","H8","H5","H6","\"H1'\"","\"H2'\"","\"H3'\"","\"H4'\"","\"H5'\"","\"H5''\""]
allowedAtoms += ["C2","C8","C5","C6","\"C1'\"","\"C2'\"","\"C3'\"","\"C4'\"","\"C5'\""]

allowedAtomsH = ['H (all H)', 'Hr (ribose H)', 'Hn (base H)', 'C (all C)', 'Cr (ribose C)', 'Cn (base C)']
allowedAtomsH += ["H2","H8","H5","H6","\"H1'\"","\"H2'\"","\"H3'\"","\"H4'\"","\"H5'\"","\"H5''\""]
allowedAtomsH += ["C2","C8","C5","C6","\"C1'\"","\"C2'\"","\"C3'\"","\"C4'\"","and \"C5'\""]

gatomNames = {}
gatomNames['H'] = ["A.H2","A.H8","G.H8","C.H5","U.H5","C.H6","U.H6","A.H1'","G.H1'","C.H1'","U.H1'","H2'","H3'","H4'","H5'","H5''"]
gatomNames['H'] = ["A.H2","A.H8","G.H8","C.H5","U.H5","C.H6","U.H6","H1'","H2'","H3'","H4'","H5'","H5''"]
#gatomNames['C'] = ["A.C2","A.C8","G.C8","C.C5","U.C5","C.C6","U.C6","A.C1'","G.C1'","C.C1'","U.C1'","C2'","C3'","C4'","C5'"]
gatomNames['C'] = ["A.C2","A.C8","G.C8","C.C5","U.C5","C.C6","U.C6","C1'","C2'","C3'","C4'","C5'"]
gatomNames['Hr'] = ["A.H1'","G.H1'","C.H1'","U.H1'","H2'","H3'","H4'","H5'","H5''"]
gatomNames['Hr'] = ["H1'","H2'","H3'","H4'","H5'","H5''"]
#gatomNames['Cr'] = ["A.C1'","G.C1'","C.C1'","U.C1'","C2'","C3'","C4'","C5'"]
gatomNames['Cr'] = ["C1'","C2'","C3'","C4'","C5'"]
gatomNames['Hn'] = ["A.H2","A.H8","G.H8","C.H5","U.H5","C.H6","U.H6"]
gatomNames['Cn'] = ["A.C2","A.C8","G.C8","C.C5","U.C5","C.C6","U.C6"]
gatomNamesAll = ["A.H2","A.H8","G.H8","C.H5","U.H5","C.H6","U.H6","A.H1'","G.H1'","C.H1'","U.H1'","H2'","H3'","H4'","H5'","H5''"]
#gatomNamesAll += ["A.C2","A.C8","G.C8","C.C5","U.C5","C.C6","U.C6","A.C1'","G.C1'","C.C1'","U.C1'","C2'","C3'","C4'","C5'"]
#gatomNamesAll += ["A.C2","A.C8","G.C8","C.C5","U.C5","C.C6","U.C6","A.C1'","G.C1'","C.C1'","U.C1'","C2'","C3'","C4'","C5'"]
gatomNamesAll += ["A.C2","A.C8","G.C8","C.C5","U.C5","C.C6","U.C6","C1'","C2'","C3'","C4'","C5'"]

#predtrain.rmax= 8.0
#predtrain.rmax=15.0
#predtrain.rmax=4.6
#rnapred.intraScale = 5.0


def getAtomNames(atomNameList):
    atomNames = []
    for atoms in atomNameList:
        for atom in atoms.split(','):
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
    parser.add_argument("-d", "--distRange", default=4.6, help="Distance range,4.6")
    parser.add_argument("-i", "--intraScale", default=5.0, help="Scale intraresidue values,5.0")
    parser.add_argument("-l", "--lambdaVal", default=0.001, help="Lambda for Ridge/LASSO,0.001")
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

#    if not all(elem in allowedAtoms for elem in atomNameList):
#        notAllowed = list(set(atomNameList) - set(allowedAtoms))
#        parser.error("Invalid atom(s) " + " ,".join(notAllowed) + ". Allowed atoms are " + ", ".join(allowedAtomsH))

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


def dumpRefShifts(varName, coefDict, fOut):
    keys = coefDict.keys()
    keys.sort()
    #for key in keys:
    #    print varName+'.put("'+key+'", '+str(coefDict[key])+');'
    fOut.write('baseshifts\n')
    for key in keys:
        print key+'\t'+str(coefDict[key])
        outStr = "%s\t%.5f\n" %(key,coefDict[key])
        fOut.write(outStr)
    

def dumpAlphas(eName, alphaDict, allAtomNames, aType, refValues, fOut):
    atomSources = RNAAttributes.getAtomSources()
    keys = alphaDict.keys()
    keys.sort()
    #for key in keys:
    #    print 'double[] '+key+eName+'Alphas = {'+str(alphaDict[key])[1:-2]+'};'
    fOut.write('rmax\t'+str(predtrain.rmax)+'\t'+aType+'\t'+str(rnapred.intraScale)+'\n')
    for key in keys:
        #print 'KKKK',key,allAtomNames[key]
        #print atomSources
        atomNames = allAtomNames[key]
        nNames = len(atomNames)
        nNames = 0
        for i in range(nNames):
            if atomNames[i] == key:
                deltaRef = alphaDict[key][i]
                intercept = alphaDict[key][-1]
                refValue = deltaRef+intercept
        intercept = alphaDict[key][-1]
        refKey = key
        if refKey.endswith("'"):
            refKey = 'U.'+key
        
        refValue  = intercept + refValues[refKey]

        fOut.write('coef\t'+key+'\t'+str(len(alphaDict[key])-1)+'\t'+str(round(refValue,3))+'\n')
        for i,v in enumerate(alphaDict[key]):
            if i < nNames:
                atomSource = atomNames[i]
            elif (i - nNames)  < len(atomSources):
                atomSource = atomSources[i-nNames]
            elif i == (len(alphaDict[key]) - 1):
                #atomSource = 'intercept'
                continue
            else:
                atomSource = "chi"
            outStr = "%d\t%s\t%.5f\n" %(i,atomSource,v)
            fOut.write(outStr)

def readRefs():
    refValues = {}
    with open('refs.txt','r') as f1:
        for line in f1:
            line = line.strip();
            if line[0] == '#':
                continue
            fields = line.split()
            nucName = fields[0]
            aName = fields[1]
            if nucName == 'R': 
                for nucName in ['A','G','C','U']:
                    key = nucName + '.' + aName
                    refValues[key] = float(fields[2])
            else:
                key = nucName + '.' + aName
                refValues[key] = float(fields[2])
    print refValues
    return refValues

def train(atomNameList, trainFile, testFile, matrixFile, ringMode, type):
    #print atomNames
    offsetDict = makeOffsetDict(trainFile, testFile) #{'15857': {'B': 58}, '15858': {'A': 12, 'B': 58}, '18893': {'B': 58}, '19662': {'B': 100}}
    #print "offsetDict = ", offsetDict
    offsets = {}
    allAtomNames={}
    aType = atomNameList[0][0]

    refValues = readRefs()
    predtrain.refValues = refValues

    fileName = 'rna_pred_dist_%s_%.1f.txt' % (aType, predtrain.rmax)
    fOut = open(fileName,'w')
    if type == "rc":
        atomNames = getAtomNames(atomNameList)
        coefDict,ringRatio = predtrain.trainRC(atomNames, trainFile, matrixFile, ringMode, type, aType)
        print 'coef',coefDict
        print 'rings',ringRatio
        for aName in coefDict:
            coefDict[aName] = round(coefDict[aName],3)
        alphasDict = [round(ringValue,3) for ringValue in ringRatio]
        coefDict = refValues
    else:
        alphasDict={}
        coefDict={}
        thisDict={}
        print 'list',atomNameList
        for atomName in atomNameList:
            dictName = 'base'
            if atomName[-1] == 'r':
                dictName = 'ribose'
            atomNames = getAtomNames([atomName])
            print 'BBBB',atomNames
            thisDict,alphas = predtrain.trainRC(atomNames, trainFile, matrixFile, ringMode, type, aType)
            for atomName1 in atomNames:
                alphasDict[atomName1] =  [round(v,3) for v in alphas]
                allAtomNames[atomName1] = atomNames
            for aName in thisDict:
                coefDict[aName] = round(thisDict[aName],3)

        dumpAlphas(atomNameList[0][0], alphasDict, allAtomNames, aType,refValues, fOut)
    print 'dicts'
    print alphasDict
    print coefDict
    bmrbs,pdbs = predtrain.readTestFiles(testFile)
    #dumpRefShifts("RNA_REF_SHIFTS",coefDict, fOut)
    dumpRefShifts("RNA_REF_SHIFTS",refValues, fOut)
    #print alphasDict

    ppmDatas,aNames = predtrain.analyzeFiles(pdbs, bmrbs, type, aType, offsets, coefDict, alphasDict, allAtomNames)
    #predtrain.dumpPPMData(ppmDatas)
    predtrain.reref(ppmDatas, bmrbs, aType)
    nTotal,sumAbs = predtrain.getSumAbs(ppmDatas)
    print "nAtoms %4d MAE %4.2f" % (nTotal,sumAbs)
    predtrain.removeOutliers(aNames, ppmDatas)
    nFinal,sumAbs = predtrain.getSumAbs(ppmDatas)
    print "nAtoms %4d MAE %4.2f" % (nFinal,sumAbs)
    maeValues = predtrain.getAtomStats(aNames, ppmDatas)

    fOut.write('mae\n')
    for aname in maeValues:
        outStr = "%s\t%.3f\n" %(aname,maeValues[aname])
        fOut.write(outStr)
    fOut.close()

def testBuiltin(atomNameList,  testFile,  type):
    aType = atomNameList[0][0]
    offsetDict = makeOffsetDict(None, testFile) #{'15857': {'B': 58}, '15858': {'A': 12, 'B': 58}, '18893': {'B': 58}, '19662': {'B': 100}}
    print "offsetDict = ", offsetDict
    offsets = {}
    bmrbs,pdbs = predtrain.readTestFiles(testFile)
    atomNames = getAtomNames(atomNameList)
    ppmDatas,aNames = predtrain.analyzeFiles(pdbs, bmrbs, type, aType, offsets, None, None,atomNames,  True)

    predtrain.reref(ppmDatas, bmrbs, aType)
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
predtrain.rmax=float(args.distRange)
rnapred.intraScale = float(args.intraScale)
predtrain.lambdaVal = float(args.lambdaVal)

if builtin:
    testBuiltin(atomNameList, testFile, type)
else:
    train(atomNameList, trainFile, testFile, matrixFile, ringMode, type)
