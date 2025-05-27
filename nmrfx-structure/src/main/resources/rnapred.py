import sys
import re
import jarray
import array
import refine
import subprocess
import math
import os 
import osfiles
import molio
from refine import *
from difflib import SequenceMatcher
from java.util import ArrayList
from org.nmrfx.peaks import Peak
from org.nmrfx.peaks import PeakList
from org.nmrfx.chemistry import MolFilter
from org.nmrfx.chemistry.io import Sequence
from org.nmrfx.structure.rna import RNALabels
from org.nmrfx.structure.rna import SSLayout
from org.nmrfx.structure.rna import RNAAnalysis
from org.nmrfx.structure.chemistry import SVMPredict
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.structure.chemistry.predict import RNAStats
from org.nmrfx.structure.chemistry.predict import RNAAttributes
from org.nmrfx.structure.chemistry.predict import Predictor
from org.nmrfx.structure.chemistry.energy import RingCurrentShift
from java.io import FileWriter
from subprocess import check_output

def dumpPredictions(molecule, iRef):
    polymers = molecule.getPolymers()
    if len(polymers) > 1:
        useFull = True
    else:
        useFull = False
    for polymer in polymers:
        for residue in polymer.getResidues():
            for atom in residue.getAtoms():
                if iRef < 0:
                    ppmV = atom.getRefPPM(-iRef-1)
                else:
                    ppmV = atom.getPPM(iRef)
                if (ppmV != None):
                    if atom.parent.active and atom.active:
                         if useFull:
                             name = atom.getFullName()
                         else:
                             name = atom.getShortName()
                         print "%s %.2f %s %s" % (name,ppmV.getValue(),RNAAttributes.getStats(atom),RNAAttributes.get(atom))

def predictFromSequence(molecule = None, vienna = None, ppmSet=-1):
    if molecule == None:
        molecule = Molecule.getActive()

    rnaAttr = RNAAttributes()
    rnaAttr.predictFromAttr(molecule, ppmSet)


def predictRCShifts(mol, structureNum=0, refShifts=None, ringRatio=None, ringTypes=None):
    defaultRefShifts = {
        "U.H6":8.00,"U.H3'":4.56,"U.H5":5.80,"U.H5'":4.36,
        "A.H5'":4.36,"G.H5''":4.11,"U.H1'":5.49,"A.H3'":4.56,
        "G.H1'":5.43,"G.H3'":4.56,"G.H5'":4.36,"A.H5''":4.11,
        "C.H2'":4.48,"C.H4'":4.38,"G.H8":7.77,"A.H1'":5.51,
        "U.H4'":4.38,"A.H8":8.21,"C.H6":7.94,"C.H5''":4.11,
        "C.H5":5.85,"U.H2'":4.48,"A.H4'":4.38,"G.H2'":4.48,
        "A.H2":7.79,"C.H5'":4.36,"G.H4'":4.38,"U.H5''":4.11,
        "C.H1'":5.46,"C.H3'":4.56,"A.H2'":4.4}

    if refShifts == None:
        refShifts = defaultRefShifts
    if ringRatio == None:
        ringRatio = 0.475
    filterString = ""
    inFilter = {}
    for atomId in refShifts:
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

    ringShifts = RingCurrentShift()
    ringShifts.makeRingList(mol)
    if isinstance(ringRatio,(tuple,list,array)):
        for ringType,factor in zip(ringTypes,ringRatio):
            ringShifts.setRingFactor(ringType,factor)
        ringRatio = 1.0

    molFilter = MolFilter(filterString)
    spatialSets = Molecule.matchAtoms(molFilter)

    shifts = []
    for sp in spatialSets:
        name = sp.atom.getShortName()
        aName = sp.atom.getName()
        nucName = sp.atom.getEntity().getName()

        if not nucName+"."+aName in refShifts:
            continue
        basePPM = refShifts[nucName+"."+aName]
        if isinstance(structureNum,(list,tuple)):
            ringPPM = 0.0
            for iStruct in structureNum:
                ringPPM += ringShifts.calcRingContributions(sp,iStruct,ringRatio)
            ringPPM /= len(structureNum)
        else:
            ringPPM = ringShifts.calcRingContributions(sp,structureNum,ringRatio)
        ppm = basePPM+ringPPM

        atom = Molecule.getAtomByName(name)
        atom.setRefPPM(ppm)

        shift = []
        shift.append(str(name))
        shift.append(ppm)
        shifts.append(shift)
    return shifts
def predictDistShifts(mol, rmax, allAtomNames, structureNum=0, refShifts=None, alphaDict=None):
        defaultRefShifts = {
         "U.H1'": 5.702,
         "U.H2'": 4.449,
         "U.H3'": 4.341,
         "U.H4'": 4.357,
         "U.H5'": 4.275,
         "U.H5''": 4.274,
         'U.H5': 5.642,
         'U.H6': 8.061,
         "A.H1'": 6.606,
         "A.H2'": 4.449,
         "A.H3'": 4.341,
         "A.H4'": 4.357,
         "A.H5'": 4.275,
         "A.H5''": 4.274,
         'A.H2': 7.977,
         'A.H8': 8.316,
         "G.H1'": 6.234,
         "G.H2'": 4.449,
         "G.H3'": 4.341,
         "G.H4'": 4.357,
         "G.H5'": 4.275,
         "G.H5''": 4.274,
         'G.H8': 7.871,
         "C.H1'": 5.7,
         "C.H2'": 4.449,
         "C.H3'": 4.341,
         "C.H4'": 4.357,
         "C.H5'": 4.275,
         "C.H5''": 4.274,
         'C.H5': 5.698,
         'C.H6': 7.978
        }

        defaultAlphas = {'ribose': [2.629, -1.978, -2.491, -0.551, 2.6, 2.402, -0.884, 0.028, 0.39, 1.681, -0.218, -1.22, -2.413, 7.099, 5.023, -26.883, 11.952, -0.527, -7.7, 28.734, -50.508, 19.122, -3.53, -4.062, 0.709, 8.823, -36.481, 21.023, 6.634, 1.267, -2.01, 6.7, 12.972, -65.587, 9.095, 8.952, -9.218, 4.321, 0.207, 14.587, 10.079, -3.146, -3.358, 1.418, -3.314, -5.648, 6.943, -0.543], 'base': [6.865, -3.892, -1.983, -0.507, 4.033, 1.264, -0.721, -0.055, 0.83, 0.705, -0.346, -0.859, -17.689, 19.241, -4.373, -34.864, 0.819, 0.957, 0.521, -1.355, 20.992, 2.978, -7.787, -1.922, 1.409, 10.776, -9.739, -0.055, 5.104, -2.825, -14.755, 12.592, -2.459, -26.824, 2.379, 5.485, -8.897, 5.564, -2.356, 23.225, -5.205, -5.813, 17.198, -6.817, -20.967, 25.346, -11.519, -0.974]}

        refMode = True
        if refShifts == None:
            refShifts = defaultRefShifts
        if alphaDict == None:
            alphaDict = defaultAlphas
            #alphaDict['ribose'] = [0.54 for i in range(len(refShifts))]
            #alphaDict['base'] = [0.54 for i in range(len(refShifts))]
        else:
            if not isinstance(alphaDict,(dict)):
                 alphaDict = {'ribose':alphaDict,'base':alphaDict}
        filterString = ""
        inFilter = {}
        for atomId in refShifts:
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

        molFilter = MolFilter(filterString)
        spatialSets = Molecule.matchAtoms(molFilter)

        plusRingMode = False
        chiMode = True
        if plusRingMode:
            ringShifts = RingCurrentShift()
            ringShifts.makeRingList(mol)

        shifts = []
        for sp in spatialSets:
            name = sp.atom.getShortName()
            aName = sp.atom.getName()
            if aName[-1] == "'":
                aType = 'ribose'
            else:
                aType = 'base'
            nucName = sp.atom.getEntity().getName()
            atomValues = []
            atomSpec = nucName+"."+aName
            found = False
            if atomSpec in allAtomNames:
                atomNames = allAtomNames[atomSpec]
            elif aName in allAtomNames:
                atomNames = allAtomNames[aName]
            else:
                continue
            for atomName in atomNames:
                if name == atomName or aName == atomName or atomSpec == atomName:
                    atomValues.append(1)
                    found = True
                else:
                    atomValues.append(0)
            if refMode:
                atomValues = []
            if not found:
                print 'nonatom',name,atomNames
                continue

            if aName[-1] == "'":
                if not aName in alphaDict:
                    continue
                alphas = alphaDict[aName]
            else:
                if not atomSpec in alphaDict:
                    continue
                alphas = alphaDict[atomSpec]
            if (atomSpec in refShifts):
                basePPM = refShifts[nucName+"."+aName]
            else: continue

            if isinstance(structureNum,(list,tuple)):
                distPPM = 0.0
                ringPPM = 0.0
                for iStruct in structureNum:
                    distances = mol.calcDistanceInputMatrixRow(iStruct, rmax, sp.atom, intraScale)
                    distances = atomValues + list(distances)
                    if plusRingMode:
                        alphasOnly = alphas[0:-2]
                        ringRatio = alphas[-1]
                    else:
                        alphasOnly = alphas[:-1]
                        intercept = alphas[-1]
                    if chiMode:
                        chi = sp.atom.getEntity().calcChi()
                        sinchi = math.sin(chi)
                        coschi = math.cos(chi)
                        distances.append(coschi)
                        distances.append(sinchi)
                        nu2 = sp.atom.getEntity().calcNu2()
                        sinnu2 = math.sin(nu2)
                        cosnu2 = math.cos(nu2)
                        distances.append(cosnu2)
                        distances.append(sinnu2)
                    distPPM += sum([alphasOnly[i] * distances[i] for i in range(len(alphasOnly))])
                    distPPM += intercept
                    
                    if plusRingMode:
                        ringPPM += ringShifts.calcRingContributions(sp,iStruct,ringRatio)
                distPPM = (distPPM+ringPPM) / len(structureNum)
            else:
                alphas = alphas[:-1]
                intercept = alphas[-1]
                distances = mol.calcDistanceInputMatrixRow(structureNum, rmax, sp.atom, intraScale)
                distances = atomValues + list(distances)
                distPPM = sum([alphas[i] * distances[i] for i in range(len(alphas))]) + intercept

            ppm = basePPM+distPPM

            atom = Molecule.getAtomByName(name)
            atom.setRefPPM(ppm)

            shift = []
            shift.append(str(name))
            shift.append(ppm)
            shifts.append(shift)
        return shifts

def predictBuiltIn(mol, atomNames, typeRCDist, structureNum=0):
    predictor = Predictor()
    if typeRCDist == "attr":
        vienna = RNAAnalysis.getViennaSequence(mol)
        viennaStr = ""
        for char in vienna:
            viennaStr += char

        print viennaStr
        predictFromSequence(mol, viennaStr, -1)
    else:
        for polymer in mol.getPolymers():
            if  (typeRCDist.lower()=='dist'):
                predictor.predictRNAWithDistances(polymer, 0, -1, False)
            else:
                predictor.predictRNAWithRingCurrent(polymer, 0, -1)

    filterString = ""
    inFilter = {}
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

    molFilter = MolFilter(filterString)
    spatialSets = Molecule.matchAtoms(molFilter)

    shifts = []
    for sp in spatialSets:
        ppm = sp.atom.getRefPPM()
        name = sp.atom.getShortName()
        if ppm != None:
            shift = []
            shift.append(str(name))
            shift.append(ppm)
            shifts.append(shift)
    return shifts


