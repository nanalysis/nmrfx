import os
import os.path
from refine import *
from org.nmrfx.processor.datasets import Dataset
from org.nmrfx.structure.chemistry import CouplingList
from org.nmrfx.processor.datasets.peaks.io import PeakWriter
from org.nmrfx.processor.datasets.peaks import AtomResonanceFactory
from java.io import FileWriter


import rnapred
import rnapeakgen
import peakgen


def writePeakList(peakList, listName=None, folder="genpeaks"):
    if listName == None:
        listName = peakList.getName()
    fileName = os.path.join(folder,listName+'.xpk2')
    writer = FileWriter(fileName)
    peakWriter = PeakWriter()
    peakWriter.writePeaksXPK2(writer, peakList)
    writer.close()

def genTOCSYPeaks(mol, transfers, dataset, listName ):
    peakList = peakgen.makePeakListFromDataset(listName, dataset)

    cList = CouplingList()
    polymers = mol.getPolymers()
    for polymer in polymers:
        #print polymer.getName()
        residues = polymer.getResidues()
        for iRes,aResidue in enumerate(residues):
            resNum = aResidue.getNumber()
            resName = aResidue.getName()
            print iRes,resNum,resName
            cList.generateCouplings(aResidue,4,4)
            tLinks = cList.getTocsyLinks()
            for link in tLinks:
                a0 = link.getAtom(0)
                a1 = link.getAtom(1)
                shell = link.getShell()
                if a0.isActive() and a1.isActive():
                    if (a0.getPPM(0) != None) and (a1.getPPM(0) != None):
                        print link.getAtom(0).getShortName(),link.getAtom(1).getShortName(), link.getShell()
                        ppm0 = a0.getPPM(0).getValue()
                        ppm1 = a1.getPPM(0).getValue()
                        ppms = [ppm0,ppm1]
                        widths = [0.02, 0.02]
                        intensity = 100.0
                        names = [a0.getShortName(), a1.getShortName()]
                        peakgen.addPeak(peakList, ppms, widths, intensity, names)
    return peakList

def genHCPeaks(mol,  dataset, listName, pType):
    peakList = peakgen.makePeakListFromDataset(listName, dataset)

    polymers = mol.getPolymers()
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
                            if (atom.getPPM(0) != None) and (parent.getPPM(0) != None):
                                ppm0 = atom.getPPM(0).getValue()
                                ppm1 = parent.getPPM(0).getValue()
                                ppms = [ppm0,ppm1]
                                widths = [0.02, 0.02]
                                intensity = 100.0
                                print atom.getShortName(),parent.getShortName(),pSym,ppm0,ppm1
                                names = [atom.getShortName(), parent.getShortName()]
                                peakgen.addPeak(peakList, ppms, widths, intensity, names)

    return peakList
