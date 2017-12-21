import sys
import re
import jarray
import refine
import subprocess
import math
import os 
import osfiles
from refine import *
from difflib import SequenceMatcher
from java.io import InputStreamReader
from java.io import BufferedReader
from java.lang import ClassLoader
from java.util import ArrayList
from org.nmrfx.structure.chemistry import SVMPredict
from org.nmrfx.structure.chemistry import SSLayout
from org.nmrfx.structure.chemistry.io import Sequence
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.structure.chemistry import RNALabels
from org.nmrfx.processor.datasets.peaks import Peak
from org.nmrfx.processor.datasets.peaks import PeakList
from java.io import FileWriter
from subprocess import check_output

rnaDataValues = {}

def getPairs( vienna):
    sArray = [len(vienna)]
    seqSize = array.array('i',sArray)
    ssLay = SSLayout(seqSize)
    ssLay.interpVienna(vienna)
    pairs = ssLay.getBasePairs()
    return pairs


def genRNAData(seqList, pairs):
    pairInfo = []
    for i, pair in enumerate(pairs):
        if pair == -1:
            partner = -1
        else:
            partner = seqList[pair]
        pairInfo.append((i, seqList[i], pair, partner))

    gnraPat = re.compile('G-[AGUC]-[AG]-A-')
    uncgPat = re.compile('U-[AGUC]-C-G-')

    wc = {'G': 'C', 'C': 'G', 'A': 'U', 'U': 'A', 'X': '', 'x': ''}
    wobble = {'G': 'U', 'U': 'G', 'A': '', 'C': '', 'X': '', 'x': ''}

    matchPats = {'GC': 'Pp', 'GU': 'Pp', 'GA': 'PP', 'GG': 'PP', 'G-': 'P-', 'AC': 'Pm', 'AU': 'Pp', 'AA': 'PP',
                 'AG': 'PP', 'A-': 'P-', 'CC': 'pp', 'CU': 'pp', 'CA': 'pm', 'CG': 'pP', 'C-': 'p-', 'UC': 'pp',
                 'UU': 'pp', 'UA': 'pP', 'UG': 'pP', 'U-': 'p-', '- ': '-'}

    lastCoordEntity = ""
    elems = ('nucs', 'bp', 'str', 'influ', 'attr')
    rnaData = {}
    for elem in elems:
        rnaData[elem] = []
    for pair in pairInfo:
        # print pair
        (index, start, match, end) = pair
        nucRes = start.split(':')[-1]
        nuc = nucRes[0]
        coordEntity = start.split(':')[0]
        if (coordEntity != lastCoordEntity):
            if lastCoordEntity != "":
                for elem in elems:
                    rnaData[elem].append('-')
                    rnaData[elem].append('-')
            for elem in elems:
                rnaData[elem].append('-')
                rnaData[elem].append('-')

            lastCoordEntity = coordEntity

        strType = "wc"
        influ = "-"
        attr = "-"
        type = end
        endNuc = ''
        endNucRes = ''
        bp = '-'
        if type != -1:
            endNuc = end
            endNucRes = endNuc.split(':')[-1]
            bp = endNucRes[0]

        # print endNuc,strType,influ,endNucRes,nuc,bp
        rnaData['nucs'].append(nuc + bp)
        rnaData['bp'].append(matchPats[nuc + bp])
        wcMode = 0
        if wc[nuc] == bp:
            wcMode = 1
        if wobble[nuc] == bp:
            wcMode = 1
        if wcMode == 0:
            if bp == '-':
                wcMode = 0
            else:
                wcMode = 2
        rnaData['str'].append(wcMode)
        rnaData['influ'].append(influ)
        rnaData['attr'].append(attr)

    nNucs = len(rnaData['nucs'])
    lastRes = nNucs - 7
    for iRes in range(lastRes):
        strPat = rnaData['str'][iRes + 1:iRes + 7]
        if strPat == [1, 0, 0, 0, 0, 1]:
            nucList = rnaData['nucs'][iRes + 2:iRes + 6]
            loopRes = iRes + 2
            cNuc = rnaData['nucs'][loopRes]
            if cNuc == '-':
                continue
            loopType = 'tetra'
            nucTest = ''.join(nucList)
            if gnraPat.match(nucTest):
                loopType = 'gnra'
            elif uncgPat.match(nucTest):
                loopType = 'uncg'
            for loopIndex in (0, 1, 2, 3, 4):
                rnaData['attr'][loopRes + loopIndex] = loopType + str(loopIndex + 1)

    for elem in elems:
        rnaData[elem].append('-')
        rnaData[elem].append('-')

    outLines = []
    for iRes in range(nNucs):
        outLine = ""
        nuc = rnaData['nucs'][iRes + 2]
        if nuc == '-':
            continue
        for jRes in range(-2, 3):
            kRes = iRes + jRes + 2
            if (jRes == -2) or (jRes == 2):
                nuc = rnaData['bp'][kRes]
            else:
                nuc = rnaData['nucs'][kRes]
            outLine += nuc + ","
        for jRes in range(-2, 3):
            kRes = iRes + jRes + 2
            attr = rnaData['attr'][kRes]
            outLine += attr + ","
        outLine = outLine[0:-1]
        outLines.append(outLine)
    return outLines


def predictFromAttr(seqList, outLines):
    global rnaDataValues
    atoms = (
        'AH2', 'AH8', 'GH8', 'CH5', 'UH5', 'CH6', 'UH6', 'AH1p', 'GH1p', 'CH1p', 'UH1p', 'AH2p', 'GH2p', 'CH2p', 'UH2p',
        'AH3p', 'GH3p', 'CH3p', 'UH3p', 'AC2', 'AC8', 'GC8', 'CC5', 'UC5', 'CC6', 'UC6', 'AC1p', 'GC1p', 'CC1p', 'UC1p',
        'AC2p', 'GC2p', 'CC2p', 'UC2p', 'AC3p', 'GC3p', 'CC3p', 'UC3p')
    types = ('nuc1', 'nuc2', 'nuc3', 'nuc4', 'nuc5', 'pos1', 'pos2', 'pos3', 'pos4', 'pos5')
    result = {}
    for (outLine, res) in zip(outLines, seqList):
        values = outLine.split(',')
        attrValues = '_'.join(values)
        attributes = {}
        for type, value in zip(types, values):
            attributes[type] = value
        for atom in atoms:
            targetNuc = atom[0]
            targetAtom = atom[1:]
            nucValues = values[0:5]
            nuc = nucValues[2][0]
            if nuc == targetNuc:
                shift = svmPredict(atom, attributes)
                #print attributes,shift,res,atom
                atomAttr = atom + '_' + attrValues
                if atomAttr in rnaDataValues:
                    sValues = rnaDataValues[atomAttr].split()
                    nAvg = int(sValues[0])
                    mean = float(sValues[1])
                    sdev = float(sValues[2])
                    min = float(sValues[3])
                    max = float(sValues[4])
                    nSVM = 4.0
                    totalShifts = nSVM + float(nAvg)
                    f = nSVM / totalShifts
                    wshift = f * shift + (1.0 - f) * mean
                    shift = wshift
                    #print res,targetNuc,targetAtom,attrValues,shift
                result[res + '.' + targetAtom] = shift
    return result


def loadResource(resourceName):
    cl = ClassLoader.getSystemClassLoader()
    istream = cl.getResourceAsStream(resourceName)
    lines = ""
    if istream == None:
        raise Exception("Cannot find '" + resourceName + "' on classpath")
    else:
        reader = InputStreamReader(istream)
        breader = BufferedReader(reader)
        while True:
            line = breader.readLine()
            if line == None:
                break
            if lines != '':
                lines += '\n'
            lines += line
        breader.close()
    return lines


# following should start working in Jython 2.7.1
# def loadResource(resourceName):
#    data = pyproc.__loader__.get_data(resourceName)
#    return data

def setupRNASVM():
    global svm
    global svmAttrMap
    svm = SVMPredict()
    svmAttrMap = {}
    resourceName = "data/rnasvm/svattr.txt"
    content = loadResource(resourceName)
    lines = content.split('\n')
    for line in lines:
        fields = line.split()
        atomName = fields[0]
        attrType = fields[1]
        attrValues = fields[2:]
        svmAttrMap[atomName, attrType] = attrValues
        if not (atomName, 'attrs') in svmAttrMap:
            svmAttrMap[atomName, 'attrs'] = []
        svmAttrMap[atomName, 'attrs'].append(attrType)

def loadRNAShifts():
    global rnaDataValues
    rnaDataValues = {}
    resourceName = "data/rnashifts.txt"
    content = loadResource(resourceName)
    lines = content.split('\n')
    for line in lines:
        line = line.strip()
        values = line[line.find('"') + 1:-2]
        fields = line.strip().split()
        atomAttr = '_'.join(fields[0].split(','))
        rnaDataValues[atomAttr] = values


def svmGetAttrs(atomName, attrs):
    global svmAttrMap
    output = []
    for attrType in svmAttrMap[atomName, 'attrs']:
        for attrValue in svmAttrMap[atomName, attrType]:
            if attrValue == attrs[attrType]:
                output.append(1)
            else:
                output.append(0)
    return output


def svmPredict(atomName, attributes):
    global svm
    try:
        isinstance(svm, SVMPredict)
    except:
        setupRNASVM()

    output = svmGetAttrs(atomName, attributes)
    nValues = len(output)
    p = jarray.array(output, 'd')
    result = svm.predict(atomName, p)
    return result


def getFullSequence(molecule):
    seqList = []
    for polymer in molecule.getPolymers():
        for residue in polymer.getResidues():
            seqList.append(polymer.getName() + ':' + residue.getName() + residue.getNumber())
    return seqList

def setPredictions(molecule, predPPMs, refMode=True):
    molecule.updateAtomArray()
    for atomName in predPPMs:
        ppm = predPPMs[atomName]
        if atomName[-1] == 'p':
            atomName = atomName[0:-1] + "'"
        atom = Molecule.getAtomByName(atomName)
        if refMode:
            atom.setRefPPM(ppm)
        else:
            atom.setPPM(ppm)


def dumpPredictions(molecule, refMode=True):
    polymers = molecule.getPolymers()
    if len(polymers) > 1:
        useFull = True
    else:
        useFull = False
    for polymer in polymers:
        for residue in polymer.getResidues():
            for atom in residue.getAtoms():
                if refMode:
                    ppmV = atom.getRefPPM(0)
                else:
                    ppmV = atom.getPPM(0)
                if (ppmV != None):
                    if atom.parent.active and atom.active:
                         if useFull:
                             name = atom.getFullName()
                         else:
                             name = atom.getShortName()
                         print name,ppmV.getValue()

def predictFromSequence(molecule = None, vienna = None):
    if molecule == None:
        molecule = Molecule.getActive()
    if vienna == None:
        vienna = molecule.getDotBracket()
    pairs = getPairs(vienna)
    seqList = getFullSequence(molecule)
    outLines = genRNAData(seqList, pairs)
    predPPMs = predictFromAttr(seqList, outLines)
    setPredictions(molecule, predPPMs)
