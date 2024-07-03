import sys
import glob
import os.path
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.structure.chemistry import SuperMol
from org.nmrfx.chemistry.io import PDBFile
import molio
from java.util import TreeSet
import argparse, re
from operator import itemgetter
from itertools import groupby
from java.io import FileWriter;
from org.nmrfx.chemistry.io import MMcifWriter

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
    iFile = 0
    for file in files:
        pdb.readCoordinates(molecule, file,iFile,False, False)
        iFile += 1
    return molecule

def parseArgs():
    parser = argparse.ArgumentParser(description="super options")
    parser.add_argument("-r", dest="resListE", default='', help="Residues to exclude from comparison. Can specify residue ranges and individual values (e.g. 2-5 or 10).")
    parser.add_argument("-a", dest="atomListE", default='', help="Atoms to exclude from comparison.")
    parser.add_argument("-R", dest="resListI", default="*", help="Residues to include in comparison")
    parser.add_argument("-A", dest="atomListI", default="*", help="Atoms to include in comparison")
    parser.add_argument("-c", dest="refCompare", action='store_true', help="Whether to compare calculated structures to reference structure, and save output to a file.")
    parser.add_argument("-t", dest="type", default=None, help="Type (cif|pdb) for saved aligned models. No files saved if None (None)")
    parser.add_argument("-b", dest="baseName", default="sup_", help="Base name (prefix) for superimposed file names.")
    parser.add_argument("-n", dest="nCore", default=5, type=int, help="Number of core residue cycles. Default is 5.")
    parser.add_argument("fileNames",nargs="*")
    args = parser.parse_args()
    if (args.nCore < 0):
        print "Error: n must be >= 0."
        sys.exit()
    if args.type is not None and args.type != 'cif' and args.type != 'pdb':
        print "Error: save models file type must be cif or pdb."
        sys.exit()
    if len(args.fileNames) > 1:
        runSuper(args)


def findRepresentative(mol, resNums='*',atomNames="ca,c,n,o,p,o5',c5',c4',c3',o3'"):
    doSelections(mol, resNums,atomNames)
    sup = SuperMol(mol)
    mol.resetActiveStructures()
    superResults = sup.doSuper(-1, -1, False)
    totalRMS = 0.0
    averageToI = {}
    nAvg = {}
    n = len(superResults)
    for superResult in superResults:
        iFix = superResult.getiFix()
        iMove = superResult.getiMove()
        rms = superResult.getRms()
        totalRMS += rms
        if iFix not in averageToI:
            averageToI[iFix] = 0.0
            nAvg[iFix] = 0
        averageToI[iFix] += rms
        nAvg[iFix] += 1
    minRMS = 1.0e6
    active = mol.getActiveStructures()
    for i in active:
        averageToI[i] /= nAvg[i]
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
    target = target - 1
    doSelections(mol, resSelect,atomSelect)
    sup = SuperMol(mol)
    superResults = sup.doSuper(target, -1, True)
    return [result.getRms() for result in superResults]

def sortByStructNum(val):
    sNumSearch = re.search(r'.*final(\d+)[.]pdb', val)
    if sNumSearch is not None:
        sNum = int(sNumSearch.group(1))
    else: #make the reference structure, if there is one, the last item
        sNum = sys.maxint
    return sNum

def saveModels(mol, files, type, prefix):
    active = mol.getActiveStructures()
    if type == 'cif':
        mol.resetActiveStructures()
        if 'final' not in files[-1]: #don't write out reference structure if in file list
            sNums = [i for i in range(len(files)-1)]
            treeSet = TreeSet(sNums)
            mol.setActiveStructures(treeSet)
        molName = mol.getName()
        cifFile = os.path.join(os.getcwd(), prefix + molName + "_all.cif")
        out = FileWriter(cifFile)
        MMcifWriter.writeAll(out, molName)
    elif type == 'pdb':
        for (i,file) in zip(active,files):
            (dir,fileName) = os.path.split(file)
            newFileName = prefix  + fileName
            newFile = os.path.join(dir,newFileName)
            molio.savePDB(mol, newFile, i)

def makeResAtomLists(polymers, excludeRes, excludeAtoms, includeRes, includeAtoms):
    allRes1 = [polymers.get(i).getResidues() for i in range(len(polymers))]
    allRes = set([res for subList in allRes1 for res in subList])
    if excludeRes == '' and includeRes != '':
        if "," in includeRes:
            resSplit = includeRes.split(",")
            resList = [item.strip() for item in resSplit]
        else:
            resList = [includeRes]
    elif excludeRes != '':
        allResNums = [res.getNumber() for res in allRes]
        if "," in excludeRes:
            resSplit = excludeRes.split(",")
            exclResSplit = [item.split("-") for item in resSplit]
        else:
            exclResSplit = [excludeRes.split("-")]
        exclResNums1 = [[str(num) for num in range(int(split[0]), int(split[-1])+1)] for split in exclResSplit]
        exclResNums = set([res for subList in exclResNums1 for res in subList])
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

def runSuper(args):
    files = args.fileNames
    files.sort(key=sortByStructNum)
    mol = loadPDBModels(files)
    polymers = mol.getPolymers()
    # print files
    print args.resListE, args.atomListE, args.resListI, args.atomListI
    resList, atoms = makeResAtomLists(polymers, args.resListE, args.atomListE, args.resListI, args.atomListI)
    if len(polymers) > 0:
        (minI,rms,avgRMS) = findRepresentative(mol, resList, atoms)
        print 'repModel',minI,'rms',rms,'avgrms',avgRMS
        if args.nCore > 0:
            for iCore in range(args.nCore):
                coreRes = findCore(mol, minI)
                print 'coreResidues',coreRes
                doSelections(mol, coreRes,atoms)
        else:
            coreRes = resList
        (minI,rms,avgRMS) = findRepresentative(mol, coreRes, atoms)
        print 'repModel',minI,'rms',rms,'avgrms',avgRMS
        superImpose(mol, minI, coreRes, atoms)
        if args.refCompare:
            calcRefComparisons = superImpose(mol, len(files), coreRes, atoms)
            rmsDict = makeRMSDict(files, calcRefComparisons)
            outFile = 'calcRefCoreRMS.txt'
            makeFormattedRMSFile(rmsDict, outFile)
            print "RMS comparisons to reference structure saved to file:", outFile
    else:
        (minI,rms,avgRMS) = findRepresentative(mol,'*','c*,n*,o*,p*')
        print 'repModel',minI,'rms',rms,'avgrms',avgRMS
        coreRes = ['*']
        superImpose(mol, minI, coreRes,'c*,n*,o*,p*')
    (dir,fileName) = os.path.split(files[0])
    (base,ext) = os.path.splitext(fileName)
    if args.type is not None:
        type = args.type
        prefix = args.baseName
        saveModels(mol, files, type, prefix)

def runAllSuper(files, type, prefix):
    batchArgs = argparse.Namespace(atomListE='', atomListI="ca,c,n,o,p,o5',c5',c4',c3',o3'",
                                fileNames=files, nCore=5, refCompare=False, resListE='',
                                resListI='*', type=type, baseName=prefix)
    runSuper(batchArgs)
