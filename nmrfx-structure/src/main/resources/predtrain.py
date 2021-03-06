import os
import os.path
import math
import seqalgs
import molio
import super
import rnapred
import urllib2
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.chemistry import MolFilter
from org.nmrfx.structure.chemistry.energy import RingCurrentShift
from org.nmrfx.chemistry.io import PDBFile
from org.nmrfx.structure.chemistry.predict import RNAAttributes
from org.nmrfx.structure.tools import SMILECrossValidator



from org.apache.commons.math3.stat.descriptive import DescriptiveStatistics
from org.apache.commons.math3.linear import Array2DRowRealMatrix
from org.apache.commons.math3.linear import ArrayRealVector
from org.apache.commons.math3.linear import SingularValueDecomposition

from java.io import File
from smile.io import Read
from smile.io import Write
from org.apache.commons.csv import CSVFormat
from smile.regression import RidgeRegression
from smile.regression import LASSO
from smile.regression import OLS
from smile.validation import CrossValidation
from smile.validation.metric import RMSE
from smile.data import DataFrame
from smile.data.formula import Formula





ringTypes = ["A0","A1","G0","G1","C0","U0"]
#rmax = 4.6

deltaB = None

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
    format = CSVFormat.TDF.withFirstRecordAsHeader()
    trainDf = Read.csv(fileName, format)
    #data = dFrame.toArray()
    return trainDf

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
    global writeHeader
    plusRingMode = False
    chiMode = True
    refMode = True
    atomSources = RNAAttributes.getAtomSources()
    if  (typeRCDist.lower()=='rc') or plusRingMode:
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
        mol.calcLCMB(0, True, True)
    molFilter = MolFilter(filterString)
    spatialSets = Molecule.matchAtoms(molFilter)
    ringRatio = 1.0
    for sp in spatialSets:
        atom = sp.atom
        name = atom.getShortName()
        aName = atom.getName()
        nucName = atom.getEntity().getName()
        name = nucName+'.'+aName
        header = ['AName']
        fullName = mol.getName()+":"+atom.getFullName() 
        row = [fullName]
        found = False
        for atomName in atomNames:
            if name == atomName or aName == atomName:
                row.append('1.0')
                found = True
            else:
                row.append('0.0')
            header.append('is'+atomName)

        if not found:
            continue
        if refMode:
            row = [name]
            header = ['AName']
        if  (typeRCDist.lower()=='rc'):
            if not ringMode:
                header.append('Ring')
        else:
            for aSrc in atomSources:
                header.append(aSrc)
        ppm = atom.getRefPPM()
        origPPM = ppm
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
                    header.append(ringType)
                    s = "%.6f" % (factor * 5.45)
                    row.append(s)
            else:
                s = "%.3f" % (ringPPM)
                row.append(s)
            if abs(ppm - refValues[name]) > 6.0:
                print 'skip',atom.getFullName(),ppm
                continue
            ppm -= refValues[name]
        elif (typeRCDist.lower()=='dist'):
            if abs(ppm - refValues[name]) > 6.0:
                print 'skip',atom.getFullName(),ppm
                continue
            ppm -= refValues[name]
            distances = mol.calcDistanceInputMatrixRow(0, rmax, atom, rnapred.intraScale)#/ 5.45
            s = [ '%.6f' % elem for elem in distances ] # "%.3f" % (distances)
            row += s
            if plusRingMode:
                ringPPM = ringShifts.calcRingContributions(sp,0,ringRatio)
                s = "%.3f" % (ringPPM)
                row.append(s)
            if chiMode:
                chi = atom.getEntity().calcChi()
                sinchi = math.sin(chi)
                coschi = math.cos(chi)
                s = "%.3f" % (coschi)
                header.append('coschi')
                row.append(s)
                s = "%.3f" % (sinchi)
                header.append('sinchi')
                row.append(s)
                nu2 = atom.getEntity().calcNu2()
                sinnu2 = math.sin(nu2)
                cosnu2 = math.cos(nu2)
                header.append('cosnu2')
                s = "%.3f" % (cosnu2)
                row.append(s)
                header.append('sinnu2')
                s = "%.3f" % (sinnu2)
                row.append(s)

        s = "%.3f" % (ppm)
        row.append(s)
        header.append('cs')
        if writeHeader:
            f1.write('\t'.join(header)+'\n')
            writeHeader = False
        f1.write('\t'.join(row)+'\n')

def predictWithRCFromPDB(pdbFile, refShifts, ringRatio, typeRCDist, atomNames, builtin):
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
    mol = molio.readPDB(pdbFile)
    #pdb.readCoordinates(pdbFile,-1,False, False)
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
    if builtin:
        shifts = rnapred.predictBuiltIn(mol, atomNames, typeRCDist, structList)
    else:
        if typeRCDist.lower() == 'rc':
            shifts = rnapred.predictRCShifts(mol, structList, refShifts, ringRatio, ringTypes)
        elif typeRCDist.lower() == 'dist':
            alphas = ringRatio
            #shifts = rnapred.predictDistShifts(mol, rmax, structList, refShifts, alphas)
            shifts = rnapred.predictDistShifts(mol, rmax, atomNames, structList, refValues, alphas)

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

def analyzeFiles(pdbs, bmrbs, typeRCDist, aType, offsets, refShifts=None, ringRatio=None, atomNames=None, builtin=False, report=False):
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
        #print pdbID,bmrbID
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
        mol, shifts = predictWithRCFromPDB(pdb, refShifts, ringRatio, typeRCDist, atomNames, builtin)
        for bID in shiftDict.keys():
            for chain in shiftDict[bID]:
                chainName = chains[chain]
                offset = 0
                if bID in offsets:
                    if chainName in offsets[bID]:
                        offset = offsets[bID][chainName]
                for res in shiftDict[bID][chain]:
                    for aname in shiftDict[bID][chain][res]:
                        if aname[0] != aType:
                            continue
                        atomSpec = chainName+':'+str(res+offset)+'.'+aname
                        atom = Molecule.getAtomByName(atomSpec)
                        if atom == None:
                            print 'no atom',chain,atomSpec
                        elif atom.getName() in ['H61', 'H41', 'H62', 'H21', 'H42', 'H22', 'H3']:
                            continue
                        else:
                            ppmV = atom.getRefPPM(0)
                            if (ppmV != None):
                                predPPM = ppmV.getValue()
                                expPPM = shiftDict[bID][chain][res][aname]
                                delta = predPPM-expPPM
                                deltaAbs = abs(predPPM-expPPM)
                                ppmDatas.append(PPMData(predPPM, expPPM,bID,pdbID,chain,res,aname))
                                aNames[aname] = 1
                                if report:
                                    outStr = "PREDICT %8s %4s %3s %4s %-4s %7.2f %7.2f %7.2f" % (bID,pdbID,chain,res,aname,expPPM,predPPM,delta)
                                    print outStr
                                #print 'XXXX',bID,chain,res,aname,expPPM,predPPM,delta

    return (ppmDatas,aNames.keys())


def getReref(aType):
    global deltaB
    if deltaB == None:
        deltaB = {}
        fileName = aType+'reref.txt'
        if os.path.exists(fileName):
            deltaB = loadReref(fileName)

def loadReref(fileName):
    deltaB = {}
    with open(fileName,'r') as fIn:
        for line in fIn:
            line = line.strip()
            fields = line.split()
            if len(fields) > 2 and fields[2] != "nan":
                deltaB[fields[0]] = float(fields[2])
    return deltaB
    

def reref(ppmDatas, bmrbs, aType):
    """
    Adjust referencing of bmrb data.  Use the average deviation between predicted
    and experimental data to adjust experimental shifts.

    # Parameters:

    ppmDatas (list); the chemical shift data as list of PPMData values
    bmrbs (list); a list of bmrb values to use

    # Returns:

    _ (None); Adjusts the exp field of each PPMData entry in provided list.

    """
    fileName = aType+'reref.txt'
    if os.path.exists(fileName):
        deltaB = loadReref(fileName)
    else:
        fOut = open(fileName,'w')
        deltaB = {}
        for bID in bmrbs:
            deltaValues = [ppmData.exp-ppmData.pred for ppmData in ppmDatas if ppmData.bID == bID]
            dStat = getDStat(deltaValues)
            deltaB[bID] = dStat.getMean()
            outStr =  "%s %3d %.3f" % (bID,len(deltaValues),deltaB[bID])
            print outStr
            fOut.write(outStr+'\n')
        fOut.close()

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
    print "%4s	%4s	%4s	%4s" % ("Atm","N","MAE","RMS")
    maeValues={}
    for aname in aNames:
        deltaValues = [ppmData.exp-ppmData.pred for ppmData in ppmDatas if ppmData.aName == aname and ppmData.valid]
        dStat = getDStat(deltaValues)
        meanDelta = dStat.getMean()
        sumDV2 = dStat.getSumsq()
        rms[aname] = math.sqrt(sumDV2/len(deltaValues))
        deltaMeanSum += meanAbs(deltaValues)
        print "%-4s	%4d	%4.2f	%4.2f" % (aname,len(deltaValues),meanAbs(deltaValues),rms[aname])
        maeValues[aname]=meanAbs(deltaValues)
    print "%s %.2f" % ("avg Delta Values =", deltaMeanSum/len(aNames))
    return maeValues

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
            pdb,bmrb = line.split()[0], line.split()[1]
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
            pdb,dotBracket = line.split()[0], line.split()[1]
            pdbs.append(pdb)
            dotBrackets.append(dotBracket)
    return pdbs,dotBrackets

def setRefShiftsFromBMRB(bmrbID, offsets, aType):
    getReref(aType)
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
                        if bmrbID in deltaB:
                            ppm -= deltaB[bmrbID]
                        atom.setRefPPM(ppm)
                        #atom.setRefError(errorVal)


def genRCTrainingMatrix(outFileName, pdbFiles, shiftSources, atomNames, ringMode, typeRCDist, aType):
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
    global writeHeader
    writeHeader = True
    with open(outFileName,'a') as f1:
        for pdbID,shiftSource in zip(pdbFiles,shiftSources):
            Molecule.removeAll()
            pdbFile = 'pdbfiles/'+pdbID+'.pdb'
            if not getPDBFile(pdbID):
                print 'skip',pdbFile
                continue
            #print 'train',pdbFile
            pdb = PDBFile()
            mol = molio.readPDB(pdbFile)
            if shiftSource[0]=="." or shiftSource[0]=="(":
                rnapred.predictFromSequence(mol,shiftSource)
            else:
                setRefShiftsFromBMRB(shiftSource, {}, aType)
            genRCMat(mol,atomNames,f1, ringMode, typeRCDist)

def trainRC(atomNames, trainingFileName, matrixFileName, ringMode, typeRCDist, aType):
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
    genRCTrainingMatrix(matrixFileName, pdbFiles, dotBrackets, atomNames, ringMode, typeRCDist, aType)
    trainDf = loadRCTrainingMatrix(matrixFileName)
    atomSpecifiers = trainDf.column(0)
    trainDf = trainDf.drop(0)


    formula = Formula.lhs("cs")
#    lassoModel = RidgeRegression.fit(formula, trainDf, lambdaVal)
    lassoModel = LASSO.fit(formula, trainDf, lambdaVal)
    print lassoModel
    rms = math.sqrt(lassoModel.RSS() / trainDf.nrows())
    print lassoModel.RSS(),rms

    smileCV = SMILECrossValidator(formula, trainDf, lambdaVal)
    xValidPreds = smileCV.cv(10)
    print xValidPreds

    coefs = lassoModel.coefficients()
    coefs = list(coefs)
    intercept = lassoModel.intercept()
    coefs.append(intercept)
    deltaB = lassoModel.residuals()
    nValues = trainDf.nrows()
#    for i in range(nValues):
#        print 'DDDD',atomSpecifiers.get(i),deltaB[i]
    #print coefs
    #print intercept



    if (typeRCDist.lower()=='rc'):
        if ringMode:
             nRings = len(ringTypes)
             ringRatios = list(coefs[-nRings-1:-1])
        else:
             nRings = 1;
             ringRatios = coefs[-1]
        coefs = coefs[0:-nRings]
        #print 'rccoef', coefs
        #print 'rcrings', ringRatios
    elif (typeRCDist.lower()=='dist'):
        alphas = coefs[len(atomNames):]
        alphas = coefs
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
