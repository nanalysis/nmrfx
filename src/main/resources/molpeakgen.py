import os
import os.path
import itertools
import rnapred
import peakgen
import molio
from refine import *

from org.nmrfx.processor.datasets import Dataset
from org.nmrfx.processor.datasets.peaks import Peak
from org.nmrfx.processor.datasets.peaks import PeakList
from org.nmrfx.processor.datasets.peaks.io import PeakWriter
from org.nmrfx.processor.datasets.peaks import AtomResonanceFactory
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.structure.chemistry import RNALabels
from org.nmrfx.structure.chemistry import CouplingList
from java.io import FileWriter



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
    Purpose: This function is meant to determine the interaction type between two residues in the same polymer.

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
        sameLoopRef = [False] # determine whether two residues are in the same loop
        def loopClass(rn):
            right, left, counter = rn+1, rn-1, 1
            while (pairs.get(right) is not None or pairs.get(left) is not None):
                if (pairs.get(right) == -1):
                    if right == rn2:
                        sameLoopRef[0] = True # Only way to change the value w/out getting an Assignment error
                    right +=1
                elif (pairs.get(left) == -1):
                    if left == rn2:
                        sameLoopRef[0] = True
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
                return None
        inSameLoop = sameLoopRef[0] # extracting bool from container
        prevExist = lambda rObj: (rObj.previous is not None)
        nxtExist = lambda rObj: (rObj.next is not None)
        prev = lambda rObj: (rObj.previous.iRes if prevExist(rObj) else None) 
        nxt = lambda rObj: (rObj.next.iRes if nxtExist(rObj) else None)
        dist = lambda x, y: abs(x-y)
        sameRes = dist(rn1,rn2) == 0
        resNoPair = lambda rNum: pairs.get(rNum)==-1 # does rn1 or rn2 have a pair?
        isBulge = lambda rObj: pairs.get(rObj.iRes)==-1 and (pairs.get(prev(rObj))!=-1 and (pairs.get(nxt(rObj))!=-1))
        loop = loopClass(rn1) if ((not sameRes) and (resNoPair(rn1) and resNoPair(rn2))) else None
        # switch-case
        switcher = {
            "ADJ" : ((not sameRes) and (not resNoPair(rn1) and not resNoPair(rn2)) and dist(rn1,rn2)==1),
            "BP" : ((not sameRes) and pairs.get(rn1)==rn2 and pairs.get(rn2)==rn1),
            "OABP" : ((not sameRes) and (not resNoPair(rn1) and not resNoPair(rn2)) and ((prev(aResObj)==pairs.get(rn2)) or (nxt(aResObj)==pairs.get(rn2)))),
            "L1" : ((loop=="L") and inSameLoop and dist(rn1,rn2)==1),
	    "L2" : (loop=="L" and inSameLoop and dist(rn1,rn2)==2),
            "L3" : (loop=="L" and inSameLoop and dist(rn1,rn2)==3),
            "LH" : ((not sameRes) and (not resNoPair(rn1)) and resNoPair(rn2) and loopClass(rn2)=="L" and dist(rn1,rn2)==1),
            "SRH" : (sameRes and (not resNoPair(rn1))),
            "SRL" : (sameRes and resNoPair(rn1) and loopClass(rn1)=="L"),
            "SRT" : (sameRes and resNoPair(rn1) and loopClass(rn1)=="T"),
            "SRB" : (sameRes and resNoPair(rn1) and isBulge(aResObj)),
            "S1" : (loop=="S" and inSameLoop and dist(rn1,rn2)==1),
            "S2" : (loop=="S" and inSameLoop and dist(rn1,rn2)==2),
            "SH" : ((not sameRes) and (not resNoPair(rn1)) and resNoPair(rn2) and loopClass(rn2)=="S" and dist(rn1,rn2)==1),
            "T1" : (loop=="T" and inSameLoop and dist(rn1,rn2)==1),
            "T2" : (loop=="T" and inSameLoop and dist(rn1,rn2)==2),
            "T3" : (loop=="T" and inSameLoop and dist(rn1,rn2)==3),
            "TB" : ((not sameRes) and resNoPair(rn1) and loopClass(rn1)=="T" and isBulge(bResObj)),
            "TH" : ((not sameRes) and (not resNoPair(rn1)) and resNoPair(rn2) and loopClass(rn2)=="T" and dist(rn1,rn2)==1),
            "TA" : ((not sameRes) and dist(rn1,rn2)==2 and (not resNoPair(rn1) and not resNoPair(rn2)))
        }
        for retCase, caseBool in switcher.items():
            if caseBool:
                #print(retCase, caseBool)
                return retCase
        return None # default case 
    else:
        raise KeyError("Residue index(es) '{}' or '{}' are not in {}!".format(rn1, rn2, pairs.keys()))

editingModes = {'ef':(False,True), 'fe':(True,False), 'ee':(True,True), 'ff':(False,False), 'aa':(None, None)}

class MolPeakGen:
    widthH = 0.02
    widthC = 0.4
    residueInterMap = {}
    basePairsMap = {}

    def __init__(self, mol = None):
        if mol == None:
            mol = Molecule.getActive()
        self.mol = mol
        self.widths = [self.widthH, self.widthH]
        self.intensity = 100.0
        self.refMode = True
        #self.labelScheme = "All: A.C2',C8,Hn,Hr G.C1',Cn,Hn,Hr U.C2',C6,Hn,Hr C.C1',C6,Hn,Hr"
        self.labelScheme = ""
        self.editSchemes = ["ef","fe","ee","ff","aa"]
        if mol != None:
            self.vienna = mol.getDotBracket()
            self.mol.activateAtoms()

    def setVienna(self,vienna):
        self.vienna = vienna

    def setWidths(self,widths):
        self.widths = widths

    def setEditSchemes(self, schemes):
        if schemes == "":
            schemes = ["aa"]
        self.editSchemes = schemes

    def setLabelScheme(self, scheme):
        self.labelScheme = scheme
        if self.labelScheme != "":
            rnaLabels = RNALabels()
            rnaLabels.parseSelGroups(self.mol, self.labelScheme)
        else:
            self.mol.activateAtoms()

    def getListName(self, dataset, tail="_gen"):
        if not isinstance(dataset,Dataset):
            dataset = Dataset.getDataset(dataset)
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

    def addProtonPairPeak(self, peakList, aAtom, bAtom, intensity=None, d1Edited=None, d2Edited=None):
        ppmAV = aAtom.getPPM(0)
        if intensity==None:
            intensity = self.intensity
        if self.refMode or (ppmAV == None) or not ppmAV.isValid():
            ppmAV = aAtom.getRefPPM(0)
        if (ppmAV != None) and aAtom.isActive() and ((d1Edited == None) or (d1Edited == aAtom.parent.isActive())):
            ppmBV = bAtom.getPPM(0)
            if self.refMode or (ppmBV == None) or not ppmBV.isValid():
                ppmBV = bAtom.getRefPPM(0)
            if (ppmBV != None) and bAtom.isActive() and ((d2Edited == None) or (d2Edited == bAtom.parent.isActive())):
                ppms = [ppmAV.getValue(),ppmBV.getValue()]
                names = [[aAtom.getShortName()],[bAtom.getShortName()]]
                peakgen.addPeak(peakList, ppms, self.widths, intensity, names)

    def addPeaks(self, peakList, d1Edited, d2Edited, atomDistList=None):
        if (atomDistList is None) or (not atomDistList): # none or empty
            return None
        scaleConst = 10000
        for aAtom, bAtom, distance in atomDistList:
            intensity = math.pow(distance, -6)*scaleConst
            print(intensity)
            self.addProtonPairPeak(peakList, bAtom, aAtom, d1Edited=d1Edited, d2Edited=d2Edited, intensity=intensity)
            self.addProtonPairPeak(peakList, aAtom, bAtom, d1Edited=d1Edited, d2Edited=d2Edited, intensity=intensity)

    def addPeak(self, peakList, a0, a1, intensity=None):
        if intensity == None:
            intensity = self.intensity
        if a0.isActive() and a1.isActive():
            ppm0V = a0.getPPM(0)
            if self.refMode or (ppm0V == None) or not ppm0V.isValid():
                ppm0V = a0.getRefPPM(0)

            ppm1V = a1.getPPM(0)
            if self.refMode or (ppm1V == None) or not ppm1V.isValid():
                ppm1V = a1.getRefPPM(0)

            if (ppm0V != None) and (ppm1V != None):
                ppms = [ppm0V.getValue(),ppm1V.getValue()]
                names = [a0.getShortName(), a1.getShortName()]
                peakgen.addPeak(peakList, ppms, self.widths, intensity, names)

    def genDistancePeaks(self, dataset, listName="", condition="sim", scheme="", tol=5.0):
        self.setWidths([self.widthH, self.widthH])
        if not isinstance(dataset,Dataset):
            dataset = Dataset.getDataset(dataset)
        if listName == "":
            listName = self.getListName(dataset)
        labelScheme = dataset.getProperty("labelScheme")
        self.setLabelScheme(labelScheme)
        if scheme == "":
            scheme = dataset.getProperty("editScheme")
        if scheme == "":
            scheme = "aa"
        
        (d1Edited, d2Edited) = editingModes[scheme]
        peakList = peakgen.makePeakListFromDataset(listName, dataset)
        self.mol.selectAtoms("*.H*")
        protonPairs = self.mol.getDistancePairs(tol, False)
        for protonPair in protonPairs:
            dis = protonPair.getDistance()
            if dis > 4.0:
                volume = 0.2
            elif dis > 3.0:
                volume = 0.5
            else:
                volume = 1.0
            intensity = volume
            self.addProtonPairPeak(peakList, protonPair.getAtom1(), protonPair.getAtom2(), intensity, d1Edited, d2Edited)
            self.addProtonPairPeak(peakList, protonPair.getAtom2(), protonPair.getAtom1(), intensity, d1Edited, d2Edited)
        return peakList

    def genTOCSYPeaks(self, dataset, listName="", condition="sim", transfers=2):
        self.setWidths([self.widthH, self.widthH])
        if not isinstance(dataset,Dataset):
            dataset = Dataset.getDataset(dataset)
        if listName == "":
            listName = self.getListName(dataset)
        labelScheme = dataset.getProperty("labelScheme")
        self.setLabelScheme(labelScheme)
        peakList = peakgen.makePeakListFromDataset(listName, dataset)
        peakList.setSampleConditionLabel(condition)

        polymers = self.mol.getPolymers()
        for polymer in polymers:
            #print polymer.getName()
            residues = polymer.getResidues()
            for iRes,aResidue in enumerate(residues):
                resNum = aResidue.getNumber()
                resName = aResidue.getName()
                print iRes,resNum,resName
                cList = CouplingList()
                cList.generateCouplings(aResidue,3,2)
                tLinks = cList.getTocsyLinks()
                for link in tLinks:
                    a0 = link.getAtom(0)
                    a1 = link.getAtom(1)
                    shell = link.getShell()
                    self.addPeak(peakList, a0, a1)
        return peakList

    def genHCPeaks(self, dataset, listName="", condition="sim"):
        self.setWidths([self.widthH*2, self.widthC])
        if not isinstance(dataset,Dataset):
            dataset = Dataset.getDataset(dataset)
        if listName == "":
            listName = self.getListName(dataset)
        labelScheme = dataset.getProperty("labelScheme")
        self.setLabelScheme(labelScheme)
        peakList = peakgen.makePeakListFromDataset(listName, dataset, 2)
        peakList.setSampleConditionLabel(condition)

        sf1 = dataset.getSf(0)
        sf2 = dataset.getSf(1)
        if sf1/sf2 > 5:
            pType = "N"
        else:
            pType = "C"

        polymers = self.mol.getPolymers()
        for polymer in polymers:
            #print polymer.getName()
            residues = polymer.getResidues()
            for iRes,aResidue in enumerate(residues):
                for atom in aResidue:
                    sym = atom.getElementName()
                    if sym == "H":
                        parent = atom.getParent()
                        if parent != None:
                            pSym = parent.getElementName()
                            if pType == pSym:
                                self.addPeak(peakList, atom, parent)

        return peakList

    @classmethod
    def getResidueInterMap(cls):
        if cls.residueInterMap: # not empty
            return cls.residueInterMap
        else:
            csvFile = molio.loadResource("data/res_pair_table.csv")
            fileList = csvFile.split("\n")
            nFields = 0
            for iLine, line in enumerate(fileList):
                row = line.split(',')
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
                        distance = row[7]
                        key = (interType, res1, res2)
                        if key not in cls.residueInterMap:
                            cls.residueInterMap[key] = {(atom1, atom2) : float(distance)}
                        else:
                            atomPairMap = cls.residueInterMap[key]
                            if (atom1, atom2) not in atomPairMap:
                                atomPairMap[(atom1, atom2)] = float(distance) 
                            else:
                                continue
                            
                    else:
                        print("Evaluate the number of fields in line no. '{}'.".format(iRow+1))
            return cls.residueInterMap

    def stringifyAtomPairs(self, aResNum, bResNum, atomDistList=None):
        if (atomDistList is None) or (not atomDistList): # None or empty
            return None
        retList = []
        for atoms, dist in atomDistList:
            aAtomName, bAtomName = atoms
            aSelect = '.'.join([str(aResNum), aAtomName])
            aSelected = self.mol.getAtomByName(aSelect)
            bSelect = '.'.join([str(bResNum), bAtomName])
            bSelected = self.mol.getAtomByName(aSelect)
            retList.append((aSelected, bSelected, dist))
        return retList

    def addRNASecStrPeaks(self, peakList, editScheme, pairs):
        (d1Edited, d2Edited) = editingModes[editScheme]
        residueInterTable = self.getResidueInterMap()
        rnaResidues = [residue for polymer in self.mol.getPolymers() if polymer.isRNA()
                       for residue in polymer.getResidues()]
        setUpInfo = lambda (i,r): (r.setPropertyObject("resRNAInd",i), self.basePairsMap.__setitem__(i, pairs[i]))
        map(setUpInfo, enumerate(rnaResidues))
        for iRes, aRes in enumerate(rnaResidues):
            aResNum = aRes.getNumber()
            aResName = aRes.getName()
            for jRes in range(iRes, len(rnaResidues)):
                bRes = rnaResidues[jRes]
                bResNum = bRes.getNumber()
                bResName = bRes.getName()
                iType = determineType(aRes, bRes, self.basePairsMap)
                key = (iType, aResName, bResName) 
                atomPairMap = residueInterTable.get(key) 
                if atomPairMap is None: continue
                atomDistList = atomPairMap.items() 
                stringified = self.stringifyAtomPairs(aResNum,bResNum,atomDistList)
                self.addPeaks(peakList, d1Edited, d2Edited, stringified)

    def genRNASecStrPeaks(self, dataset, listName="", condition="sim", scheme=""):
        self.setWidths([self.widthH, self.widthH])
        if not isinstance(dataset,Dataset):
            dataset = Dataset.getDataset(dataset)
        if listName == "":
            listName = self.getListName(dataset)
        self.setLabelScheme(dataset.getProperty("labelScheme"))
        if scheme == "":
            scheme = dataset.getProperty("editScheme")
        if scheme == "":
            scheme = "aa"
             
	#print(self.vienna)
        pairs = rnapred.getPairs(self.vienna)
        peakList = peakgen.makePeakListFromDataset(listName, dataset)
        peakList.setSampleConditionLabel(condition)
        self.addRNASecStrPeaks(peakList, scheme, pairs)
        return peakList

