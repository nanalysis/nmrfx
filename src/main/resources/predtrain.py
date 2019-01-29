import os
import os.path
import math
import seqalgs
import molio
import super
import rnapred
import urllib2
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.structure.chemistry import MolFilter
from org.nmrfx.structure.chemistry.energy import RingCurrentShift
from org.nmrfx.structure.chemistry.io import PDBFile
from org.apache.commons.math3.stat.descriptive import DescriptiveStatistics
from org.apache.commons.math3.linear import Array2DRowRealMatrix
from org.apache.commons.math3.linear import ArrayRealVector
from org.apache.commons.math3.linear import SingularValueDecomposition

ringTypes = ["A0","A1","G0","G1","C0","U0"]

def getPDBFile(pdbID):
    pdbFile="pdbfiles/"+pdbID+".pdb"

    if ( not os.path.exists(pdbFile)):
        print 'fetch PDB '+pdbID
        try:
            pdbEntry = urllib2.urlopen('http://www.rcsb.org/pdb/files/'+str(pdbID)+'.pdb').read()
        except:
            return(False)
        with open(pdbFile, 'w') as tmp_file:
            tmp_file.write(str(pdbEntry))
    return(True)

def loadRCTrainingMatrix(fileName):
    """
    Reads file of training data and creates Apache Commons Math Matrix and Vector.
    These will be used in solving linear problem Ax=b
    First column of matrix is atom specifier and is skipped
    Last column is the chemical shift value and will be in the b vector
    Remaining columns are values for A matrix

    # Parameters:

    fileName (str); the name of the file

    # Returns:

    A,b,atomCts,atomIDs (Matrix, Vector, Dictionary, Vector) the matrix and vector to be used in fitting, a dictionary of atom counts, and a vector of atom names
    """

    A = []
    b = []
    atomCts = {}
    atomIDs = []
    with open(fileName,'r') as f1:
        for line in f1:
            line = line.strip()
            fields = line.split('\t')
            row = [float(v) for v in fields[1:-1]]
            A.append(row)
            b.append(float(fields[-1]))
            key = fields[0][2:]
            atomIDs.append(key)
            if not key in atomCts.keys():
                atomCts[key] = 1
            elif key in atomCts.keys():
                atomCts[key] += 1
    AMat = Array2DRowRealMatrix(A)
    bVec = ArrayRealVector(b)
    return AMat, bVec, atomCts, atomIDs

def genRCMat(mol, atomNames, f1, ringMode, typeRCDist):
    """
    Generate training data from molecule for parameterizing ring-current shifts.
    Data is appended to the specified file.

    # Parameters:

    mol (Molecule); the molecule to be analyzed
    atoms (list); a list of atoms to be used
    f1 (File); the output file
    ringMode (boolean):
    typeRCDist (String): Type of analysis to perform, 'rc' (ring current) or 'dist' (distances)

    # Returns:

    _ (None);

    See also: `loadRCTrainingMatrix(...)`
    """

    if  (typeRCDist.lower()=='rc'):
        ringShifts = RingCurrentShift()
        ringShifts.makeRingList(mol)
    inFilter = {}
    filterString = ""
    for atomId in atomNames:
        dotIndex =  atomId.find(".")
        if dotIndex != -1:
            atomId = atomId[2:]
        if atomId in inFilter:
            continue
        inFilter[atomId] = True
        if filterString == "":
            filterString = "*."+atomId
        else:
            filterString += ","+atomId
    if  (typeRCDist.lower()=='rc'):
        mol.calcLCMB(0, True)
    molFilter = MolFilter(filterString)
    spatialSets = Molecule.matchAtoms(molFilter)
    ringRatio = 1.0
    for sp in spatialSets:
        atom = sp.atom
        name = atom.getShortName()
        aName = atom.getName()
        nucName = atom.getEntity().getName()
        name = nucName+'.'+aName
        row = [name]
        found = False
        for atomName in atomNames:
            if name == atomName or aName == atomName:
                row.append('1')
                found = True
            else:
                row.append('0')
        if not found:
            continue
        ppm = atom.getRefPPM()
        if ppm == None:
            continue
        if  (typeRCDist.lower()=='rc'):
            ringPPM = ringShifts.calcRingContributions(sp,0,ringRatio)
            ringFactors = ringShifts.calcRingGeometricFactors(sp, 0)
            if ringMode:
                for ringType in ringTypes:
                    if ringType in ringFactors:
                        factor = ringFactors[ringType]
                    else:
                        factor = 0.0
                    s = "%.6f" % (factor * 5.45)
                    row.append(s)
            else:
                s = "%.3f" % (ringPPM)
                row.append(s)
        elif (typeRCDist.lower()=='dist'):
            rmax = 15.0
            distances = mol.calcDistanceInputMatrixRow(0, rmax, atom)#/ 5.45
            s = [ '%.6f' % elem for elem in distances ] # "%.3f" % (distances)
            row += s
        s = "%.3f" % (ppm)
        row.append(s)
        f1.write('\t'.join(row)+'\n')

def predictWithRCFromPDB(pdbFile, refShifts, ringRatio, typeRCDist):
    """
    Reads an RNA molecule from PDB file and predict chemical shifts.

    # Parameters:

    pdbFile (str); the name of the PDB file
    refShifts (dict); the reference shifts for each atom to be predicted.
    ringRatio (float); A scale parameter to multiply the ring-current contributions by
    typeRCDist (String): Type of analysis to perform, 'rc' (ring current) or 'dist' (distances)

    # Returns:

    shifts (dict) the chemical shifts that were predicted. The reference ppm
        value of each atom is also updated with shift
    """

    Molecule.removeAll()
    pdb = PDBFile()
    #mol = molio.readPDB(pdbFile)
    mol = pdb.read(pdbFile)
    pdb.readCoordinates(pdbFile,-1,False, False)
    activeStructures = mol.getActiveStructures()
    avgOverStructures = False
    if not avgOverStructures:
        if len(activeStructures) > 0:
            repI = super.findRepresentative(mol)
            iStruct = repI[0]
        else:
             iStruct=0
        structList=[iStruct]
    else:
        if len(activeStructures) > 0:
            structList = list(activeStructures)
        else:
            structList=[0]
    if typeRCDist.lower() == 'rc':
        shifts = rnapred.predictRCShifts(mol, structList, refShifts, ringRatio, ringTypes)
    elif typeRCDist.lower() == 'dist':
        alphas = ringRatio
        shifts = rnapred.predictDistShifts(mol, structList, refShifts, alphas)

    return mol, shifts

def getDStat(values):
   dStat = DescriptiveStatistics(values)
   return dStat

def meanAbs(values):
    sum = 0.0
    for v in values:
        sum += abs(v)
    return sum /len(values)

class PPMData:
    def __init__(self, p,e,b,pdb,c,r,a):
        self.pred = p
        self.exp = e
        self.bID = b
        self.pdbID = pdb
        self.chain = c
        self.rName = r
        self.aName = a
        self.valid = True

def analyzeFiles(pdbs, bmrbs, typeRCDist, offsets, refShifts=None, ringRatio=None):
    """
    Analyze a whole set of pdb files and associated chemical shifts in bmrb files
    Chemical shifts will be predicted with 3D Ring Current shift code
    and the result will contain predicted and experimental
    shifts which can then be statistically analyzed.

    # Parameters:

    pdbs (list); The list of pdb identifiers. The actual files must be in a pdbfiles subdirectory
    bmrbs (list); The list of bmrb identifiers. The actual files must be in a star2 subdirectory
    typeRCDist (String): Type of analysis to perform, 'rc' (ring current) or 'dist' (distances)
    offsets (dict): the offset values for certain bmrb files
    refShifts (dict); the reference shifts for each atom to be predicted.
    ringRatio (float); A scale parameter to multiply the ring-current contributions by

    # Returns:
    ppmDatas,aNames (list,  list) the list of PPMData values with
        predicted and experimental values and atom names that were used

    """

    ppmDatas=[]
    aNames = {}
    chains = ['','A','B','C','D']

    for pdbID,bmrbID in zip(pdbs,bmrbs):
        print pdbID,bmrbID
        bmrbFile = 'star/bmr'+bmrbID+'.str'
        if not os.path.exists(bmrbFile):
            bmrbFile = 'star2/bmr'+bmrbID+'.str'
            if not os.path.exists(bmrbFile):
                print 'skip',bmrbFile
                continue
        pdb = 'pdbfiles/'+pdbID+'.pdb'
        if not getPDBFile(pdbID):
            print 'skip',pdb
            continue
        shiftDict = seqalgs.readBMRBShifts(bmrbID, bmrbFile)

        if (typeRCDist.lower() == 'rc'):
            mol, shifts = predictWithRCFromPDB(pdb, refShifts, ringRatio, typeRCDist)
            polymers = mol.getPolymers()
            chainP = [polymer.getName() for polymer in polymers]
        elif typeRCDist.lower() == 'dist':
            alphas = ringRatio
            #shifts = predictWithDistFromPDB(pdb, refShifts, alphas)
            mol, shifts = predictWithRCFromPDB(pdb, refShifts, ringRatio, typeRCDist)
        for bID in shiftDict.keys():
            for chain in shiftDict[bID]:
                chainName = chains[chain]
                offset = 0
                if bID in offsets:
                    if chainName in offsets[bID]:
                        offset = offsets[bID][chainName]
                for res in shiftDict[bID][chain]:
                    for aname in shiftDict[bID][chain][res]:
                        atomSpec = chainName+':'+str(res+offset)+'.'+aname
                        atom = Molecule.getAtomByName(atomSpec)
                        if atom == None:
                            print 'no atom',chain,atomSpec
                        else:
                            ppmV = atom.getRefPPM(0)
                            if (ppmV != None):
                                predPPM = ppmV.getValue()
                                expPPM = shiftDict[bID][chain][res][aname]
                                delta = predPPM-expPPM
                                deltaAbs = abs(predPPM-expPPM)
                                ppmDatas.append(PPMData(predPPM, expPPM,bID,pdbID,chain,res,aname))
                                aNames[aname] = 1
                                #print bID,chain,res,aname,expPPM,predPPM,delta
    return (ppmDatas,aNames.keys())


def reref(ppmDatas, bmrbs):
    """
    Adjust referencing of bmrb data.  Use the average deviation between predicted
    and experimental data to adjust experimental shifts.

    # Parameters:

    ppmDatas (list); the chemical shift data as list of PPMData values
    bmrbs (list); a list of bmrb values to use

    # Returns:

    _ (None); Adjusts the exp field of each PPMData entry in provided list.

    """

    deltaB = {}
    for bID in bmrbs:
        deltaValues = [ppmData.exp-ppmData.pred for ppmData in ppmDatas if ppmData.bID == bID]
        dStat = getDStat(deltaValues)
        deltaB[bID] = dStat.getMean()
        print "%s %3d %.3f" % (bID,len(deltaValues),deltaB[bID])

    sumAbs = 0.0
    for ppmData in ppmDatas:
        ppmData.exp = ppmData.exp - deltaB[ppmData.bID]
        delta= ppmData.exp-ppmData.pred
        sumAbs += abs(delta)

def dumpPPMData(ppmDatas):
    for ppmData in ppmDatas:
        print ppmData.pdbID, ppmData.bID,ppmData.rName,ppmData.aName,ppmData.exp,ppmData.pred,ppmData.pred-ppmData.exp

def getSumAbs(ppmDatas):
    sumAbs = 0.0
    n = 0;
    for ppmData in ppmDatas:
        if ppmData.valid:
            delta= ppmData.exp-ppmData.pred
            sumAbs += abs(delta)
            n += 1
    sumAbs /= n
    return n,sumAbs

def removeOutliers(aNames, ppmDatas, mul=3.0):
    """
    Remove outliers.  An outlier is a value whose experimental shift is mul (default 3.0) times the
    standard deviation of the fit deviation for that atom type.
    The valid flag in the PPMData object will be set to False for outliers

    # Parameters:

    aNames (list); list of atom names to use
    ppmDatas (list); list of PPMData objects to analyze
    mul (float); standard deviation is multiplied by this value

    # Returns:

    _ (None); Sets the valid flag of each PPMData entry to indicate whether its an outlier.
    """

    rms = {}
    for aname in aNames:
        deltaValues = [ppmData.exp-ppmData.pred for ppmData in ppmDatas if ppmData.aName == aname]
        dStat = getDStat(deltaValues)
        meanDelta = dStat.getMean()
        sumDV2 = dStat.getSumsq()
        rms[aname] = math.sqrt(sumDV2/len(deltaValues))

    for ppmData in ppmDatas:
        delta= ppmData.exp-ppmData.pred
        if abs(delta) > mul * rms[ppmData.aName]:
            ppmData.valid = False

def getAtomStats(aNames, ppmDatas):
    """
    Measure statistics of predicted vs. experimental value deviations.

    # Parameters:

    aNames (list); list of atom names to use
    ppmDatas (list); list of PPMData objects to analyze

    # Returns:

    _ (None); Prints out the atom name, number of values, MAE, RMS.
    """

    rms = {}
    deltaMeanSum = 0.0
    for aname in aNames:
        deltaValues = [ppmData.exp-ppmData.pred for ppmData in ppmDatas if ppmData.aName == aname and ppmData.valid]
        dStat = getDStat(deltaValues)
        meanDelta = dStat.getMean()
        sumDV2 = dStat.getSumsq()
        rms[aname] = math.sqrt(sumDV2/len(deltaValues))
        deltaMeanSum += meanAbs(deltaValues)
        print "%s %3d %.3f %.3f" % (aname,len(deltaValues),meanAbs(deltaValues),rms[aname])
    print "%s %.3f" % ("avg Delta Values =", deltaMeanSum/len(aNames))

def readTestFiles(fileName):
    bmrbs = []
    pdbs = []
    with open(fileName,'r') as f1:
        for line in f1:
            line = line.strip()
            if len(line) == 0:
                continue
            if line.startswith("#"):
                continue
            pdb,bmrb = line.split()
            bmrbs.append(bmrb)
            pdbs.append(pdb)
    return bmrbs,pdbs

def readTrainingFiles(fileName):
    pdbs = []
    dotBrackets = []
    with open(fileName,'r') as f1:
        for line in f1:
            line = line.strip()
            if len(line) == 0:
                continue
            if line.startswith("#"):
                continue
            pdb,dotBracket = line.split()
            pdbs.append(pdb)
            dotBrackets.append(dotBracket)
    return pdbs,dotBrackets

def setRefShiftsFromBMRB(bmrbID, offsets):
    bmrbFile = 'star/bmr'+bmrbID+'.str'
    shiftDict = seqalgs.readBMRBShifts(bmrbID, bmrbFile)
    chains = ['','A','B','C','D']
    for bID in shiftDict.keys():
        for chain in shiftDict[bID]:
            chainName = chains[chain]
            offset = 0
            if bID in offsets:
                if chainName in offsets[bID]:
                    offset = offsets[bID][chainName]
            for res in shiftDict[bID][chain]:
                for aname in shiftDict[bID][chain][res]:
                    atomSpec = chainName+':'+str(res+offset)+'.'+aname
                    atom = Molecule.getAtomByName(atomSpec)
                    ppm = shiftDict[bID][chain][res][aname]
                    if atom == None:
                        print 'no atom',chain,atomSpec
                    else:
                        atom.setRefPPM(ppm)
                        #atom.setRefError(errorVal)


def genRCTrainingMatrix(outFileName, pdbFiles, shiftSources, atomNames, ringMode, typeRCDist):
    """
    Generate the training data from a list of pdbFiles and dotBracket values.
    Each file is predicted using the attribute method based on a specified
    dot-bracket string and the output is appended to the training matrix

    # Parameters:

    outFileName (str); The output file name.  File is deleted if present already.
    pdbFiles (list); list of PDB Files to use
    shiftSources (list); list of sources for shifts.  Either dot-bracket values (vienna string) or bmrb id to use for each pdb file 
    atomNames (list): list of atom names
    ringMode
    typeRCDist (String): Type of analysis to perform, 'rc' (ring current) or 'dist' (distances)

    # Returns:

    _ (None); Training data is written to specified file
    """

    try:
        os.remove(outFileName)
    except:
        pass

    with open(outFileName,'a') as f1:
        for pdbID,shiftSource in zip(pdbFiles,shiftSources):
            Molecule.removeAll()
            pdbFile = 'pdbfiles/'+pdbID+'.pdb'
            if not getPDBFile(pdbID):
                print 'skip',pdbFile
                continue
            print 'train',pdbFile
            mol = molio.readPDB(pdbFile)
            if shiftSource[0]=="." or shiftSource[0]=="(":
                rnapred.predictFromSequence(mol,shiftSource)
            else:
                setRefShiftsFromBMRB(shiftSource, {})
            genRCMat(mol,atomNames,f1, ringMode, typeRCDist)

def trainRC(atomNames, trainingFileName, matrixFileName, ringMode, typeRCDist):
    """
    Training the ring-current shift model using specified training data.

    # Parameters:

    trainingFileName (str); the name of the file containing training pdb file and dot-bracket entries
    matrixFileName (str); the name of the output file for training matrix
    ringMode
    typeRCDist (String): Type of analysis to perform, 'rc' (ring current) or 'dist' (distances)

    # Returns:

    coefDict, ringRatio (dict, float); a dictionary of reference shifts for each atom type and the scaling ratio.
    """

    pdbFiles,dotBrackets = readTrainingFiles(trainingFileName)
    genRCTrainingMatrix(matrixFileName, pdbFiles, dotBrackets, atomNames, ringMode, typeRCDist)
    AMat, bVec, atomCtsDict, atomIDs = loadRCTrainingMatrix(matrixFileName)
    atomTot = float(sum(atomCtsDict.values()))
    atomFract = [float(atomCtsDict.values()[i])/atomTot for i in range(len(atomCtsDict))]
    atomNormDict = {atomCtsDict.keys()[i]: (1.0/len(atomCtsDict))/(atomFract[i]) for i in range(len(atomCtsDict))}
    atomNorm = [atomNormDict[atomID] for atomID in atomIDs]
    AMatNRows = AMat.getRowDimension()
    AMatNCols = AMat.getColumnDimension()
    AMat = Array2DRowRealMatrix([[AMat.getEntry(i,j) * atomNorm[i] for j in range(AMatNCols)] for i in range(AMatNRows)])
    bVec = bVec.ebeMultiply(ArrayRealVector(atomNorm))
    svd = SingularValueDecomposition(AMat)
    coefVec = svd.getSolver().solve(bVec)
    bPred = AMat.operate(coefVec)
    deltaB = bPred.subtract(bVec)
    dotProd = deltaB.dotProduct(deltaB)
    rms = math.sqrt(dotProd/bPred.getDimension())
    print 'fitrms',rms,'rank',svd.getRank()
    coefs = coefVec.getDataRef()
    if (typeRCDist.lower()=='rc'):
        if ringMode:
             nRings = len(ringTypes)
             ringRatios = list(coefs[-nRings:])
        else:
             nRings = 1;
             ringRatios = coefs[-1]
        coefs = coefs[0:-nRings]
    elif (typeRCDist.lower()=='dist'):
        alphas = coefs[len(atomNames):-1]
        coefs = coefs[0:len(atomNames)]
        ringRatios = alphas
    coefDict = {}
    for (aName,coef) in zip(atomNames,coefs):
        if aName.find(".") == -1:
            aDotNames = [rName+"."+aName for rName in ["A","G","C","U"]]
        else:
            aDotNames = [aName]
        for aDotName in aDotNames:
            coefDict[aDotName] = coef

    return coefDict,ringRatios
