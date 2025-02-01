import os
import os.path
import rnapred
import peakgen
import molio
import itertools as itools

from refine import *

from org.nmrfx.datasets import DatasetBase
from org.nmrfx.peaks import Peak
from org.nmrfx.peaks import PeakList
from org.nmrfx.peaks.io import PeakWriter
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.structure.rna import RNALabels
from org.nmrfx.structure.chemistry import CouplingList
from org.nmrfx.structure.rna import InteractionType
from org.nmrfx.chemistry import SecondaryStructure
from org.nmrfx.structure.rna import SSGen

from java.io import FileWriter
import pdb as debugger



def writePeakList(peakList, listName=None, folder="genpeaks"):
    if listName == None:
        listName = peakList.getName()
    fileName = os.path.join(folder,listName+'.xpk2')
    writer = FileWriter(fileName)
    peakWriter = PeakWriter()
    peakWriter.writePeaksXPK2(writer, peakList)
    writer.close()

def determineType(aResObj, bResObj, pairs={}):
    """
    Purpose: This function is meant to determine the interaction type between two residues.

    Parameters:
       - aResObj <Residue object> : residue 1
       - bResObj <Residue object> : residue 2
       - pairs <dict> : Dictionary with residue index as keys and the corresponding pairing residue index as values.
                        If residue index specified in the key doesn't have a pair, then the pairing residue index is -1.

    Return:
       - Type of interaction <str> or None.
    """
    rn1 = aResObj.getPropertyObject("resRNAInd")
    rn2 = bResObj.getPropertyObject("resRNAInd")
    if rn1 in pairs and rn2 in pairs:
        def loopClass(rn):
            if pairs.get(rn) != -1:
                return None
            right, left, counter = rn+1, rn-1, 1
            while (pairs.get(right) is not None or pairs.get(left) is not None):
                if (pairs.get(right) == -1):
                    right +=1
                elif (pairs.get(left) == -1):
                    left -= 1
                else:
                    break
                counter += 1
            if counter == 4:
                return "T" # tetra
            elif counter > 4:
                return "L" # larger
            elif counter < 4 and counter > 1:
                return "S" # smaller
            else:
                return "B" # bulge
        classR1 = loopClass(rn1)
        classR2 = loopClass(rn2)
        dist = rn2-rn1
        sameRes = dist == 0
        inSamePolymer = (aResObj.getPolymer().label == bResObj.getPolymer().label)
        hasBP = lambda rNum: pairs.get(rNum) != -1
        bothInLoop = (not hasBP(rn1) and not hasBP(rn2)) # both are in a loop
        bothInHelix = (hasBP(rn1) and hasBP(rn2)) # both are in a helix
        loopAndHelix = (hasBP(rn1) and not hasBP(rn2)) or (hasBP(rn2) and not hasBP(rn1)) # one res in loop, other in helix
        prevExist = lambda rObj: (rObj.previous is not None)
        #nxtExist = lambda rObj: (rObj.next is not None)
        prev = lambda rObj: (rObj.previous.getPropertyObject("resRNAInd") if prevExist(rObj) else None) 
        #nxt = lambda rObj: (rObj.next.getPropertyObject("resRNAInd") if nxtExist(rObj) else None)
        loop = classR1 if (not sameRes and bothInLoop) else None
        inSameLoop = (bothInLoop and rn1 < rn2 and all([(True if pairs.get(key)==-1 else False) for key in pairs.keys() if key >= rn1 and key <= rn2]))
        loopHelixInter = lambda c: (classR1==c or classR2==c) if loopAndHelix else False

        # switch-case
        switcher = {
            "ADJ" : ((not sameRes) and inSamePolymer and bothInHelix and dist==1),
            "BP" : ((not sameRes) and pairs.get(rn1)==rn2 and pairs.get(rn2)==rn1),
            "OABP" : ((not sameRes) and bothInHelix and (prev(aResObj)==pairs.get(rn2))),
            "L1" : (loop=="L" and inSameLoop and dist==1),
	    "L2" : (loop=="L" and inSameLoop and dist==2),
            "L3" : (loop=="L" and inSameLoop and dist==3),
            "LH" : ((not sameRes) and loopHelixInter("L") and dist==1),
            "SRH" : (sameRes and hasBP(rn1)),
            "SRL" : (sameRes and classR1=="L"),
            "SRT" : (sameRes and classR1=="T"),
            "SRB" : (sameRes and classR1=="B"),
            "S1" : (loop=="S" and inSameLoop and dist==1),
            "S2" : (loop=="S" and inSameLoop and dist==2),
            "SH" : ((not sameRes) and loopHelixInter("S") and dist==1),
            "T1" : (loop=="T" and inSameLoop and dist==1),
            "T2" : (loop=="T" and inSameLoop and dist==2),
            "T3" : (loop=="T" and inSameLoop and dist==3),
            "TB" : ((not sameRes) and bothInLoop and classR1!=classR2 and classR1 in ["B","T"] and classR2 in ["B","T"]),
            "HB" : ((not sameRes) and loopAndHelix and (classR1=="B" or classR2=="B") and (dist == 1)),
            "BB" : ((not sameRes) and bothInLoop and (classR1=="B" and classR2=="B")),
            "TH" : ((not sameRes) and loopHelixInter("T") and dist==1),
            "TA" : ((not sameRes) and inSamePolymer and dist==2 and bothInHelix)
        }
        for retCase, caseBool in switcher.items():
            if caseBool:
                return retCase
        return None # default case 
    else:
        raise KeyError("Residue index(es) '{}' or '{}' are not in {}!".format(rn1, rn2, pairs.keys()))

editingModes = {'ef':(False,True), 'fe':(True,False), 'ee':(True,True), 'ff':(False,False), 'aa':(None, None)}

class MolPeakGen:
    elemWidths = {'H':0.04, 'C':0.4, 'N':0.4}
    widthH = 0.02
    widthC = 0.4
    residueInterMap = {}
    minInst = 10
    maxDist = 5.25
    basePairsMap = {}

    def __init__(self, mol = None):
        if mol == None:
            mol = Molecule.getActive()
        self.mol = mol
        self.widths = [self.widthH, self.widthH]
        self.intensity = 100.0
        self.refMode = False
        #self.labelScheme = "All: A.C2',C8,Hn,Hr G.C1',Cn,Hn,Hr U.C2',C6,Hn,Hr C.C1',C6,Hn,Hr"
        self.labelScheme = ""
        self.editSchemes = ["ef","fe","ee","ff","aa"]
        if mol != None:
            self.vienna = mol.getDotBracket()
            self.mol.activateAtoms()

    def setWidths(self,widths):
        self.widths = widths

    def setLabelScheme(self, scheme):
        self.labelScheme = scheme
        if self.labelScheme != "":
            rnaLabels = RNALabels()
            rnaLabels.parseSelGroups(self.mol, self.labelScheme)
        else:
            self.mol.activateAtoms()

    def getListName(self, dataset, tail="_gen"):
        if not isinstance(dataset,DatasetBase):
            dataset = DatasetBase.getDataset(dataset)
        dataName = dataset.getName()
        index = dataName.find(".")
        if index != -1:
            dataName = dataName[0:index]
        listName = dataName+tail
        return listName
        

    def setAllRNAProtons(self, scheme):
        self.labelScheme = "*:*.Hn,Hr"
        rnaLabels = RNALabels()
        rnaLabels.parseSelGroups(self.mol, self.labelScheme)

    def addProtonPairPeak(self, peakList, aAtom, bAtom, requireActive=False, intensity=None, d1Edited=None, d2Edited=None):
        if intensity==None:
            intensity = self.intensity
            widthScale = 2.0
        else: 
            if intensity > 10.0:
                widthScale = 2.0
            elif intensity > 1.5:
                widthScale = 1.25
            elif intensity > 1.0:
                widthScale = 0.85
            else:
                widthScale = 0.50

        nPeakDim = peakList.getNDim()

        if nPeakDim == 3:
            atoms = [aAtom, aAtom.getParent(), bAtom]
            dEdited = [d1Edited,None,d2Edited]
        else:
            atoms = [aAtom, bAtom]
            dEdited = [d1Edited,d2Edited]

        ok = True
        ppms = []
        eppms = []
        names = []
        widths = []
        bounds = []
        for atom,dEdit in zip(atoms, dEdited):
            if (not atom.isActive()) and requireActive:
                ok = False
                break
            ppmV = atom.getPPM(0)
            if self.refMode or (ppmV == None) or not ppmV.isValid():
                ppmV = atom.getRefPPM(0)
            if ppmV == None:
                ok = False
                break
            if atom.getAtomicNumber() == 1:
                if dEdit != None and (dEdit != atom.parent.isActive()):
                    ok = False
                    break

            atomElem = atom.getElementName()
            width = self.elemWidths[atomElem]

            ppms.append(ppmV.getValue())
            eppms.append(ppmV.getError())
            names.append(atom.getShortName())
            widths.append(width)
            bounds.append(width * widthScale)
        if ok:
            peak = peakgen.addPeak(peakList, ppms, eppms, widths, bounds, intensity, names)

    def addPeaks(self, peakList, d1Edited, d2Edited, atomDistList=None, useN=True, requireActive=False):
        if (atomDistList is None) or (not atomDistList): # none or empty
            return None
        scaleConst = 100.0/math.pow(2.0,-6)
        for aAtom, bAtom, distance in atomDistList:
            intensity = math.pow(distance, -6)*scaleConst
            if (useN or aAtom.getParent().getAtomicNumber() != 7) and (useN or bAtom.getParent().getAtomicNumber() != 7):
                self.addProtonPairPeak(peakList, bAtom, aAtom, requireActive, d1Edited=d1Edited, d2Edited=d2Edited, intensity=intensity)
                self.addProtonPairPeak(peakList, aAtom, bAtom, requireActive, d1Edited=d1Edited, d2Edited=d2Edited, intensity=intensity)

    def addPeak(self, peakList, atoms, requireActive, intensity=None, ppmSet=0):
        if intensity == None:
            intensity = self.intensity
        ok = True
        ppms = []
        eppms = []
        names = []
        bounds = []
        widths = []
        for atom in atoms:
            if not atom.isActive() and requireActive:
                ok = False
                break 
            ppmV = atom.getPPM(ppmSet)
            if self.refMode or (ppmV == None) or not ppmV.isValid():
                ppmV = atom.getRefPPM(0)
            if (ppmV == None or not ppmV.isValid()):
                ok = False
                break 
            ppms.append(ppmV.getValue())
            eppms.append(ppmV.getError())
            names.append(atom.getShortName())
            sym = atom.getSymbol()
            if not sym in self.elemWidths:
                width = 0.01
            else:
                width = self.elemWidths[sym]
            widths.append(width)
            bound = 1.5 * width
            bounds.append(bound)
        if ok:
            peakgen.addPeak(peakList, ppms, eppms, widths, bounds, intensity, names)

    @classmethod
    def getResidueInterMap(cls):
        if cls.residueInterMap: # not empty
            return cls.residueInterMap
        else:
            txtFile = molio.loadResource("data/res_pair_table.txt")
            fileList = txtFile.split("\n")
            nFields = 0
            sums = {}
            nVals = {}
            # calculate averages overall all base types if atom is in ribose
            for iLine, line in enumerate(fileList):
                row = line.split('\t')
                if iLine == 0: # field label line
                    nFields = len(row)
                    if nFields == 9:
                        continue
                    else:
                        raise ValueError("The number of fields is no longer equal to 9.")
                else:
                    row = [fieldStr.strip() for fieldStr in row]
                    if len(row) == nFields:
                        interType, res1, res2 = row[:3]
                        atom1, atom2 = row[3:5]
                        minDis = float(row[5])
                        maxDis = float(row[6])
                        avgDis = float(row[7])
                        nInst = int(row[8])
                        if atom1[-1] == "'":
                            res1 = 'r'
                        if atom2[-1] == "'":
                            res2 = 'r'
                        key = (interType,atom1,atom2,res1,res2)
                        if not key in sums:
                            sums[key] = 0.0
                            nVals[key] = 0
                        sums[key] += avgDis * nInst
                        nVals[key] += nInst
                       
            for iLine, line in enumerate(fileList):
                row = line.split('\t')
                if iLine == 0: # field label line
                    nFields = len(row)
                    if nFields == 9:
                        continue
                    else:
                        raise ValueError("The number of fields is no longer equal to 9.")
                else:
                    row = [fieldStr.strip() for fieldStr in row]
                    if len(row) == nFields:
                        interType, res1, res2 = row[:3]
                        atom1, atom2 = row[3:5]
                        distance = float(row[7])
                        nInst = int(row[8])
                        numScale = 1
                        if atom1[-1] == "'":
                            res1R = 'r'
                            numScale = numScale * 2
                        else:
                            res1R = res1
                        if atom2[-1] == "'":
                            res2R = 'r'
                            numScale = numScale * 2
                        else:
                            res2R = res2
                        keyR = (interType,atom1,atom2,res1R,res2R)
                        distance = sums[keyR] / nVals[keyR]
                        nInst = nVals[keyR] / numScale
                        #if interType[0:2] == "SR":
                        #    if atom1 > atom2:
                        #        atom1, atom2 = (atom2, atom1)
                        #print keyR, distance, nInst
                        if distance < cls.maxDist and nInst > cls.minInst:
                            key = (interType, res1, res2)
                            if key not in cls.residueInterMap:
                                cls.residueInterMap[key] = {(atom1, atom2) : distance}
                            else:
                                atomPairMap = cls.residueInterMap[key]
                                if (atom1, atom2) not in atomPairMap:
                                    atomPairMap[(atom1, atom2)] = distance
                                else:
                                    continue
                            
                    else:
                        print("Evaluate the number of fields in line no. '{}'.".format(iRow+1))
            return cls.residueInterMap

    def stringifyAtomPairs(self, aPolyName, aResNum, bPolyName, bResNum, atomDistList=None):
        if (atomDistList is None) or (not atomDistList): # None or empty
            return None
        retList = []
        for atoms, dist in atomDistList:
            aAtomName, bAtomName = atoms
            aSelect = aPolyName+":"+str(aResNum)+"."+aAtomName
            bSelect = bPolyName+":"+str(bResNum)+"."+bAtomName
            aSelected = self.mol.getAtomByName(aSelect)
            bSelected = self.mol.getAtomByName(bSelect)
            retList.append((aSelected, bSelected, dist))
        return retList

    def addRNASecStrPeaks(self, peakList, editScheme, useN, requireActive):
        (d1Edited, d2Edited) = editingModes[editScheme]
        residueInterTable = self.getResidueInterMap()
        rnaResidues = [residue for polymer in self.mol.getPolymers() if polymer.isRNA()
                       for residue in polymer.getResidues()]
        for resCombination in itools.combinations_with_replacement(rnaResidues, 2):
            aRes, bRes = resCombination
            aResNum = aRes.getNumber()
            aResName = aRes.getName()
            aPolyName = aRes.getPolymer().getName()
            bResNum = bRes.getNumber()
            bResName = bRes.getName()
            bPolyName = bRes.getPolymer().getName()
            iType = InteractionType.determineType(aRes, bRes)
            key = (iType, aResName, bResName) 
            atomPairMap = residueInterTable.get(key) 
            if atomPairMap is None: continue
            atomDistList = atomPairMap.items() 
            stringified = self.stringifyAtomPairs(aPolyName,aResNum,bPolyName,bResNum,atomDistList)
            self.addPeaks(peakList, d1Edited, d2Edited, stringified, useN, requireActive)

    def getPeakList(self, dataset, listName, nPeakDim=0):
        if listName != "":
            peakList = PeakList.get(listName)
        if peakList == None:
            if (dataset == None or dataset == "")  and listName != "":
                peakList = PeakList.get(listName)
            else:
                if listName == "":
                    listName = self.getListName(dataset)
                if not isinstance(dataset,DatasetBase):
                    dataset = DatasetBase.getDataset(dataset)
                peakList = peakgen.makePeakListFromDataset(listName, dataset, nPeakDim)
        return peakList

    def genRNASecStrPeaks(self, dataset, listName="", condition="sim", scheme="", useN=False, requireActive=False):
        self.setWidths([self.widthH, self.widthH])
        peakList = self.getPeakList(dataset, listName)
        
        if scheme == "" and dataset != None:
            if not isinstance(dataset,DatasetBase):
                dataset = DatasetBase.getDataset(dataset)
            if (dataset != None):
                self.setLabelScheme(dataset.getProperty("labelScheme"))
                scheme = dataset.getProperty("editScheme")
        if scheme == "":
            scheme = "aa"
             
	#print(self.vienna)
        ssGen = SSGen(self.mol, self.vienna)
        ssGen.analyze()

        peakList.setSampleConditionLabel(condition)
        self.addRNASecStrPeaks(peakList, scheme, useN, requireActive)
        return peakList

