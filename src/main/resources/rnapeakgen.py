from org.nmrfx.processor.datasets.peaks import Peak
from org.nmrfx.processor.datasets.peaks import PeakList
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.structure.chemistry import RNALabels


import peakgen
import rnapred
import os.path

ssAtomPairs = [["H1'","H2'",0],["H2'","H5",1], ["H6","H1'",1],["H1'","H8",1], ["H8,H6","H1',H2',H3'",0],["H6","H5",0],["H2,H3'","H1'",0],["H8,H6","H1',H2',H3',H8,H6",-1],["H8,H6","H5",1],["H2","H1'",1],["H2","H1'","x"]]
filtering = {'ef':(False,True), 'fe':(True,False), 'ee':(True,True), 'ff':(False,False), 'aa':(None, None)}


class RNAPeakGen:

    def __init__(self, mol, vienna):
        self.mol = mol
        self.vienna = vienna
        self.widths = [0.02,0.02]
        self.intensity = 100.0
        self.labelScheme = "All: A.C2',C8,Hn,Hr G.C1',Cn,Hn,Hr U.C2',C6,Hn,Hr C.C1',C6,Hn,Hr"
        self.editSchemes = ["ef","fe","ee","ff","aa"]

    def setEditSchemes(self, schemes):
        self.editSchemes = schemes

    def setLabelScheme(self, scheme):
        self.scheme = scheme

    def setAllProtons(self, scheme):
        self.scheme = "All: A.Hn,Hr G.Hn,Hr C.Hn,Hr U.Hn,Hr"

    def addPeaks(self, peakList, aSelected, bSelected, d1Edited, d2Edited):
        for aAtomName in aSelected:
            aAtom = Molecule.getAtomByName(aAtomName)
            if aAtom.active:
                if ((d1Edited == None) or (d1Edited == aAtom.parent.active)):
                    ppmA = aAtom.getPPM(0).getValue()
                    for bAtomName in bSelected:
                        bAtom = Molecule.getAtomByName(bAtomName)
                        if bAtom.active:
                            if ((d2Edited == None) or (d2Edited == bAtom.parent.active)):
                                ppmB = bAtom.getPPM(0).getValue()
                                ppms = [ppmA,ppmB]
                                names = [[aAtom.getShortName()],[bAtom.getShortName()]]
                                peakgen.addPeak(peakList, ppms, self.widths, self.intensity, names)

    def addPeaksToList(self, peakList, editScheme):
        (d1Edited, d2Edited) = filtering[editScheme]
        pairs = rnapred.getPairs(self.vienna)

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
                    if (editScheme == 'fe') or (editScheme == 'ee'):
                        self.addPeaks(peakList, bSelected, aSelected, d1Edited, d2Edited)

    def activateAtoms(self):
        rnaLabels = RNALabels()
        rnaLabels.parse(self.mol, self.labelScheme)

    def genPeakLists(self, dataset):
        peakLists = []
        for scheme in self.editSchemes:
            peakList = peakgen.makePeakListFromDataset(scheme, dataset)
            peakLists.append(peakList)
            self.addPeaksToList(peakList, scheme)
        return peakLists
