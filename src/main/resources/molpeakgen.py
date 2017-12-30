import os
import os.path
import rnapred
import peakgen
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

ssAtomPairs = [
["H1'","H2'",0],
["H8,H6","H1',H2',H3'",0],
["H6","H5",0],
["H2,H3'","H1'",0],
["H8,H6","H1',H2',H3',H8,H6",-1],
["H2'","H5",1],
["H6","H1'",1],
["H1'","H8",1],
["H8,H6","H5",1],
["H2","H1'",1],
["H2","H1'","x"]]

ssAtomPairs = [
["H1'","H2',H3'",0],
["H2'","H3'",0],
["H1'","H2",0],
["H1',H2',H3'","H6,H8",0],
["H5","H6",0],
["H1',H2',H3',H6,H8","H6,H8",1],
["H2'","H5",1], 
["H6","H1'",1],
["H1'","H8",1], 
["H8,H6","H5",1],
["H2","H1'",1],
["H2","H1'","x"]]


editingModes = {'ef':(False,True), 'fe':(True,False), 'ee':(True,True), 'ff':(False,False), 'aa':(None, None)}




class MolPeakGen:

    def __init__(self, mol):
        self.mol = mol
        self.widths = [0.02,0.02]
        self.intensity = 100.0
        self.refMode = True
        self.labelScheme = "All: A.C2',C8,Hn,Hr G.C1',Cn,Hn,Hr U.C2',C6,Hn,Hr C.C1',C6,Hn,Hr"
        self.editSchemes = ["ef","fe","ee","ff","aa"]
        self.vienna = mol.getDotBracket()

    def setVienna(self,vienna):
        self.vienna = vienna

    def setWidths(self,widths):
        self.widths = widths

    def setEditSchemes(self, schemes):
        self.editSchemes = schemes

    def setLabelScheme(self, scheme):
        self.scheme = scheme

    def setAllRNAProtons(self, scheme):
        self.scheme = "All: A.Hn,Hr G.Hn,Hr C.Hn,Hr U.Hn,Hr"


    def addProtonPairPeak(self, peakList, aAtom, bAtom, intensity=None, d1Edited=None, d2Edited=None):
            ppmAV = aAtom.getPPM(0)
            if intensity==None:
                intensity = self.intensity
            if self.refMode or (ppmAV == None) or not ppmAV.isValid():
                ppmAV = aAtom.getRefPPM(0)
            if (ppmAV != None) and aAtom.active and ((d1Edited == None) or (d1Edited == aAtom.parent.active)):
                ppmBV = bAtom.getPPM(0)
                if self.refMode or (ppmBV == None) or not ppmBV.isValid():
                    ppmBV = bAtom.getRefPPM(0)
                if (ppmBV != None) and bAtom.active and ((d2Edited == None) or (d2Edited == bAtom.parent.active)):
                    ppms = [ppmAV.getValue(),ppmBV.getValue()]
                    names = [[aAtom.getShortName()],[bAtom.getShortName()]]
                    peakgen.addPeak(peakList, ppms, self.widths, intensity, names)

    def addPeaks(self, peakList, aSelected, bSelected, d1Edited, d2Edited):
        for aAtomName in aSelected:
            aAtom = Molecule.getAtomByName(aAtomName)
            for bAtomName in bSelected:
                bAtom = Molecule.getAtomByName(bAtomName)
                self.addProtonPairPeak(peakList, bAtom, aAtom, d1Edited, d2Edited)
                self.addProtonPairPeak(peakList, aAtom, bAtom, d1Edited, d2Edited)



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

    def genDistancePeaks(self, dataset, listName, tol=5.0, requireActive=True):
        peakList = peakgen.makePeakListFromDataset(listName, dataset)
        self.mol.selectAtoms("*.H*")
        protonPairs = self.mol.getDistancePairs(tol, requireActive)
        for protonPair in protonPairs:
            dis = protonPair.getDistance()
            if dis > 4.0:
                volume = 0.2
            elif dis > 3.0:
                volume = 0.5
            else:
                volume = 1.0
            intensity = volume
            self.addProtonPairPeak(peakList, protonPair.getAtom1(), protonPair.getAtom2(), intensity)
            self.addProtonPairPeak(peakList, protonPair.getAtom2(), protonPair.getAtom1(), intensity)
        return peakList

    def genTOCSYPeaks(self,  transfers, dataset, listName):
        peakList = peakgen.makePeakListFromDataset(listName, dataset)
        peakList.setSampleConditionLabel("sim")

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

    def genHCPeaks(self,  dataset, listName, pType, refMode=True):
        peakList = peakgen.makePeakListFromDataset(listName, dataset)
        peakList.setSampleConditionLabel("sim")

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


    def addRNASecStrPeaks(self, peakList, editScheme, pairs):
        (d1Edited, d2Edited) = editingModes[editScheme]

        polymers = self.mol.getPolymers()
        for polymer in polymers:
            #print polymer.getName()
            residues = polymer.getResidues()
            for iRes,aResidue in enumerate(residues):
                resNum = aResidue.getNumber()
                resName = aResidue.getName()
                #print iRes,resNum,resName,pairs[iRes]
                for (aSet, bSet, delta) in ssAtomPairs:
                    #print aSet,bSet,delta
                    self.mol.selectAtoms(resNum+"."+aSet)
                    aSelected = self.mol.listAtoms()
                    #print "A Selected", aSelected

                    if delta == "x":
                       kRes = iRes-1

                       if pairs[kRes] != -1:
                          jRes = pairs[kRes]
                       else:
                          continue
                    else:
                          jRes = iRes+delta

                    if jRes>=len(residues):
                          continue
                    if jRes<0:
                          continue

                    bResidue = residues[jRes]
                    bResNum = bResidue.getNumber()
                    self.mol.selectAtoms(bResNum+"."+bSet)
                    bSelected = self.mol.listAtoms()
                    #print "B Selected", bSelected
                    self.addPeaks(peakList, aSelected, bSelected, d1Edited, d2Edited)

    def genRNASecStrPeaks(self, dataset):
        rnaLabels = RNALabels()
        rnaLabels.parse(self.mol, self.labelScheme)
        pairs = rnapred.getPairs(self.vienna)
        peakLists = []
        for scheme in self.editSchemes:
            peakList = peakgen.makePeakListFromDataset(scheme, dataset)
            peakList.setSampleConditionLabel("sim")
            peakLists.append(peakList)
            self.addRNASecStrPeaks(peakList, scheme, pairs)
        return peakLists

