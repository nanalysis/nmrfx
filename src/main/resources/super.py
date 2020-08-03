import sys
import glob
import os.path
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.structure.chemistry import SuperMol
from org.nmrfx.structure.chemistry.io import PDBFile
import molio
from java.util import TreeSet
import argparse, re
from operator import itemgetter
from itertools import groupby

def median(values):
    values.sort()
    if (len(values) == 0):
        return None
    elif (len(values) == 1):
        return values[0]
    else:
        n = len(values)
        v1 = values[n/2]
        v2 = values[(n-1)/2]
        return (v1+v2)/2.0

def loadPDBModels(files):
    try:
        fileName = files[0]
    except IndexError:
	errMsg = "\nCan't load final PDB models. Please inspect the 'final' and 'output' directories."
        errMsg += "\nNote: problem related to the retrieval of summary lines in temp*.txt."
        raise LookupError(errMsg)
    pdb = PDBFile()
    molecule = pdb.read(fileName)
    iFile = 1
    for file in files:
        pdb.readCoordinates(file,iFile,False, False)
        iFile += 1
    return molecule

def parseArgs():
    parser = argparse.ArgumentParser(description="super options")
    parser.add_argument("-r", dest="resListE", default='', help="Residues to exclude from comparison. Can specify semi-colon-separated chains and comma-separated residue ranges and individual values (e.g. A: 2, 3; B: 2-5, 10).")
    parser.add_argument("-a", dest="atomListE", default='', help="Atoms to exclude from comparison.")
    parser.add_argument("-R", dest="resListI", default="*", help="Residues to include in comparison")
    parser.add_argument("-A", dest="atomListI", default="*", help="Atoms to include in comparison")
    parser.add_argument("--c", dest="refCompare", nargs="?", help="Optional: Whether to compare calculated structures to reference structure, with optional output to a specified file.")
    parser.add_argument("fileNames",nargs="*")
    args = parser.parse_args()
    files = args.fileNames
    if len(files) > 1:
        runSuper(files, 'super', args.resListE, args.atomListE, args.resListI, args.atomListI, args.refCompare)


def getResList(resArg):
    if ";" in resArg: #multiple chains separated by ; (e.g. A: 2-5, 10; B: 1-4, 12)
        resList1 = []
        chainSplit = resArg.split(";")
        for chainArgs in chainSplit:
            chain = chainArgs.split(":")[0].strip();
            resArgs = chainArgs.split(":")[1].strip();
            resList1.append(makeResList(resArgs, chain))
        resList = [item for sublist in resList1 for item in sublist]
    else: #single chain
        if ":" in resArg: #chain specified (e.g. B: 5-10)
            chain = resArg.split(":")[0].strip();
            resArgs = resArg.split(":")[1].strip();
            resList = makeResList(resArgs, chain)
        else: #chain not specified (e.g. 2-5). Defaults to chain A
            resList = makeResList(resArg, "A")
    return resList

def makeResList(resArg, chain):
    # print resArg
    if resArg == "":
        resList = []
    elif resArg == "*":
        resList = [resArg]
    else:
        if ("-" in resArg) and ("," in resArg): #comma-separated ranges (e.g. 2-5, 10-12) or ranges and individual residues (e.g. 2-5, 7, 10-12, 20)
            resList1 = []
            resArgSplit = resArg.split(",")
            for val in resArgSplit:
                if "-" in val: #range of residues
                    valSplit = val.split("-")
                    firstRes = int(valSplit[0].strip())
                    lastRes = int(valSplit[1].strip()) + 1
                    resList1.append([res for res in range(firstRes, lastRes)])
                else: #single residue
                    resList1.append([val.strip()])
            resList = ["{}.{}".format(chain, item) for sublist in resList1 for item in sublist]
        elif "-" in resArg: #single range of residues (e.g. 2-5)
            resArgSplit = resArg.split("-")
            firstRes = int(resArgSplit[0].strip())
            lastRes = int(resArgSplit[1].strip()) + 1
            resList = ["{}.{}".format(chain, res) for res in range(firstRes, lastRes)]
        elif "," in resArg: #comma-separated individual residues (e.g. 2, 3, 4, 5)
            resListS = resArg.split(",")
            resList = ["{}.{}".format(chain, res) for res in resListS if res != ""]

    return resList

def makeFileList(fileArg):
    # print fileArg
    if fileArg == "":
        fileList = []
    elif fileArg == "*": #use all final.pdb files
        fileList = glob.glob(os.path.join('final','final*.pdb'))
    elif "," in fileArg: #comma-separated individual files (e.g. final1.pdb, final2.pdb)
        fileListS = fileArg.split(",")
        fileList = [os.path.join('final',file.strip()) for file in fileListS if file != ""]
    else: #single final.pdb file
        fileList = [os.path.join('final',fileArg.strip())]

    return fileList

def findRepresentative(mol, resNums='*',atomNames="ca,c,n,o,p,o5',c5',c4',c3',o3'"):
    treeSet = TreeSet()
    structures = mol.getStructures()
    for structure in structures:
        if structure != 0:
            treeSet.add(structure)
    nFiles = treeSet.size()
    doSelections(mol, resNums,atomNames)
    sup = SuperMol(mol)
    mol.setActiveStructures(treeSet)

    superResults = sup.doSuper(-1, -1, False)
    totalRMS = 0.0
    averageToI = {}
    n = len(superResults)
    for superResult in superResults:
        iFix = superResult.getiFix()
        iMove = superResult.getiMove()
        rms = superResult.getRms()
        totalRMS += rms
        if iFix not in averageToI:
            averageToI[iFix] = 0.0
        averageToI[iFix] += rms
    minRMS = 1.0e6
    #for i in range(1,nFiles+1):
    for i in treeSet:
        averageToI[i] /= nFiles-1
        if averageToI[i] < minRMS:
            minRMS = averageToI[i]
            minIndex = i
    avgRMS = totalRMS/len(superResults)
    return (minIndex, minRMS, avgRMS)

def findCore(mol, minIndex):
    sup = SuperMol(mol)
    superResults = sup.doSuper(minIndex, -1, True)
    mol.calcRMSD()
    polymers = mol.getPolymers()
    resRMSs = []
    polymerValues = {}
    superAtoms = mol.getAtomsByProp(2)
    residuesAtomRMS = {}
    residueRMS = {}
    for spSet in superAtoms:
        atom = spSet.getAtom()
        residue = atom.getEntity()
        if not residue in residuesAtomRMS:
            residuesAtomRMS[residue] = []
        residuesAtomRMS[residue].append(atom.getBFactor())
    resRMSs=[]
    for residue in residuesAtomRMS:
        rms = sum(residuesAtomRMS[residue])/len(residuesAtomRMS[residue])
        resRMSs.append(rms)
        residueRMS[residue] = rms
    med = median(resRMSs)

    coreRes = []
    for polymer in polymers:
        polyName = polymer.getName()
        resValues = []
        polymerValues[polymer.getName()] = resValues
        residues = polymer.getResidues()
        chainCode = polymer.getName()
        state = 'out'
        lastNum = len(residues)-1
        for iRes,residue in enumerate(residues):
            newState = 'out'
            num = residue.getNumber()
            rms = 0.0
            if residue in residueRMS:
                 rms = residueRMS[residue]
                 if rms < 2.0*med:
                     newState = 'in'

            if state == "out":
                if newState == "out":
                    pass
                else:
                    start = num
                    #coreRes.append(num)
            else:
                if newState == "out":
                    coreRes.append((polyName+':'+start,lastRes))
                elif iRes == lastNum:
                    coreRes.append((polyName+':'+start,num))
                    #coreRes.append(num)
            state = newState
            lastRes = num


    resSelect = []
    for (start,end) in coreRes:
        if (start == end):
           resSelect.append(start)
        else:
           resSelect.append(start+'-'+end)
    return resSelect

def doSelections(mol, resSelects, atomSelect):
    mol.selectAtoms("*.*")
    mol.setAtomProperty(2,False)
    for resSelect in resSelects:
        selection = resSelect+'.'+atomSelect
        nsel = mol.selectAtoms(selection)
        mol.setAtomProperty(2,True)


def superImpose(mol, target,resSelect,atomSelect="ca,c,n,o,p,o5',c5',c4',c3',o3'"):
    doSelections(mol, resSelect,atomSelect)
    sup = SuperMol(mol)
    superResults = sup.doSuper(target, -1, True)
    return [result.getRms() for result in superResults]

def saveModels(mol, files):
    active = mol.getActiveStructures()
    for (i,file) in zip(active,files):
        (dir,fileName) = os.path.split(file)
        newFileName = 'sup_' + fileName
        newFile = os.path.join(dir,newFileName)
        molio.savePDB(mol, newFile, i)

def makeResAtomLists(polymers, excludeRes, excludeAtoms, includeRes, includeAtoms):
    allRes1 = [polymers.get(i).getResidues() for i in range(len(polymers))]
    allRes = set([res for subList in allRes1 for res in subList])
    if excludeRes == '' and includeRes != '':
        if "," in includeRes:
            resSplit = includeRes.split(",")
            resList = [resRange.strip() for resRange in resSplit]
        else:
            resList = [includeRes]
    elif excludeRes != '':
        allResNums = [res.getNumber() for res in allRes]
        exclResSplit = excludeRes.split("-")
        exclResNums = [str(num) for num in range(int(exclResSplit[0]), int(exclResSplit[1])+1)]
        resNums = [int(resNum) for resNum in allResNums if resNum not in exclResNums]
        resGroups = [map(itemgetter(1), g) for k, g in groupby(enumerate(resNums), lambda (i,x):i-x)]
        resList = ['{}-{}'.format(list[0], list[-1]) for list in resGroups]
    if excludeAtoms == '' and includeAtoms != '':
        flagAtoms = includeAtoms
    elif excludeAtoms != '':
        flagAtoms = excludeAtoms
    flagAtomSplit = flagAtoms.split(",")
    flagAtoms = [atom.lower() for atom in flagAtomSplit]
    flagAtomLists = [res.getAtoms(aFlag.upper()) for aFlag in flagAtoms for res in allRes if res.getAtoms(aFlag.upper()) != []]
    allFlagAtoms = set([atom.getName().lower() for subList in flagAtomLists for atom in subList])
    if excludeAtoms == '' and includeAtoms != '':
        atoms = ','.join(allFlagAtoms)
    elif excludeAtoms != '':
        allAtomLists = [res.getAtoms() for res in allRes]
        allUniqAtoms = set([j.getName().lower() for subList in allAtomLists for j in subList])
        atomList = [aName for aName in allUniqAtoms if aName not in allFlagAtoms]
        atoms = ','.join(atomList)

    return resList, atoms

def makeRMSDict(files, rmsVals):
    rmsDict = {}
    for i in range(len(rmsVals)):
        sNum = int(re.findall(r'\d+', files[i])[0])
        rmsDict[sNum] = rmsVals[i]
    return rmsDict

def makeFormattedRMSFile(rmsDict, outFileName):
    with open(outFileName, 'w') as formattedFile:
        formattedFile.write("{}\t{}\n".format("Structure", "RMS"))
        for key in sorted(rmsDict.keys()):
            formattedFile.write("{}\t{}\n".format(key, rmsDict[key]))

def runSuper(files, newBase='super', excludeRes='', excludeAtoms='', includeRes='*', includeAtoms='*', compareRef=None):
    mol = loadPDBModels(files)
    polymers = mol.getPolymers()
    print excludeRes, excludeAtoms, includeRes, includeAtoms
    resList, atoms = makeResAtomLists(polymers, excludeRes, excludeAtoms, includeRes, includeAtoms)
    nCore = 5
    if len(polymers) > 0:
        (minI,rms,avgRMS) = findRepresentative(mol, resList, atoms)
        print 'repModel',minI,'rms',rms,'avgrms',avgRMS
        for iCore in range(nCore):
            coreRes = findCore(mol, minI)
            print 'coreResidues',coreRes
            doSelections(mol, coreRes,atoms)
        (minI,rms,avgRMS) = findRepresentative(mol, coreRes, atoms)
        print 'repModel',minI,'rms',rms,'avgrms',avgRMS
        superImpose(mol, minI, coreRes, atoms)
        if compareRef is not None: #--c included as last argument before PDB files
            calcRefComparisons = superImpose(mol, len(files), coreRes, atoms)
            rmsDict = makeRMSDict(files, calcRefComparisons)
            if ".pdb" in compareRef: #--c specified before PDB files with no output filename, i.e. do comparison but don't output to a file
                print("{}\t{}".format("Structure", "RMS"))
                for key in sorted(rmsDict.keys()):
                    print("{}\t{}".format(key, rmsDict[key]))
            elif ".pdb" not in compareRef: #--c specified with output filename
                makeFormattedRMSFile(rmsDict, compareRef)
                print "RMS comparisons to reference structure saved to file:", compareRef
    else:
        (minI,rms,avgRMS) = findRepresentative(mol,'*','c*,n*,o*,p*')
        print 'repModel',minI,'rms',rms,'avgrms',avgRMS
        coreRes = ['*']
        superImpose(mol, minI, coreRes,'c*,n*,o*,p*')
    (dir,fileName) = os.path.split(files[0])
    (base,ext) = os.path.splitext(fileName)

    saveModels(mol, files)

def runAllSuper(files, newBase='super', excludeRes='', excludeAtoms='', includeRes='*', includeAtoms="ca,c,n,o,p,o5',c5',c4',c3',o3'"):
    runSuper(files, newBase, excludeRes, excludeAtoms, includeRes, includeAtoms)
