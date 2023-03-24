import unittest
import math
import molio
import molpeakgen
import peakgen
import predictor
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.peaks import PeakList
from org.nmrfx.project import ProjectBase
from org.nmrfx.structure.chemistry.predict import Predictor



class TestMolPeakGen(unittest.TestCase):

    def testrnaattr(self):
        Molecule.removeAll()
        seq = 'GGCUCUGGUGAGAGCCAGAGCC'
        mol = molio.readSequenceString('A', seq)
        mol.setDotBracket('(((((((((....)))))))))')
        listName = 'test'
        labels = ['H1','H2']
        sfs = [500.0, 500.0]
        sws = [2000.0, 2000.0]
        peakList = peakgen.makePeakList(listName,labels,sfs,sws)
        predictor.predictRNA(mol,'')

        molGen=molpeakgen.MolPeakGen()
        molGen.genRNASecStrPeaks(None, listName=listName)
        peaks = peakList.peaks()
        nPeaks = len(peaks)
        project = ProjectBase.getActive();
        project.removePeakList(listName);

        Molecule.removeAll()
        self.assertEqual(1592,nPeaks)

    def testrnahmbc(self):
        Molecule.removeAll()
        seq = 'C'
        mol = molio.readSequenceString('A', seq)
        mol.setDotBracket('.')
        listName = 'test'
        labels = ['H1','H2']
        sfs = [500.0, 500.0]
        sws = [2000.0, 2000.0]
        peakList = peakgen.makePeakList(listName,labels,sfs,sws)
        predictor.predictRNA(mol,'')

        molGen=molpeakgen.MolPeakGen()
        molGen.genHMBCPeaks(None, listName, condition="sim", transfers=3)
        peaks = peakList.peaks()
        nPeaks = len(peaks)
        project = ProjectBase.getActive();
        project.removePeakList(listName);

        Molecule.removeAll()
        self.assertEqual(22,nPeaks)

    def testsdfhmbc(self):
        Molecule.removeAll()
        cmpd = molio.readSDF('src/test/data/taccE.sdf',True)
        mol = cmpd.molecule
        listName = 'testtacc'
        labels = ['H1','H2']
        sfs = [500.0, 500.0]
        sws = [2000.0, 2000.0]
        peakList = peakgen.makePeakList(listName,labels,sfs,sws)
        pred = Predictor()
        pred.predictWithShells(cmpd,-1)

        molGen=molpeakgen.MolPeakGen()
        molGen.genHMBCPeaks(None, listName, condition="sim", transfers=3)
        peaks = peakList.peaks()
        nPeaks = len(peaks)
        project = ProjectBase.getActive();
        project.removePeakList(listName);

        Molecule.removeAll()
        self.assertEqual(139,nPeaks)


if __name__ == '__main__':
    testProgram = unittest.main(exit=False)
    result = testProgram.result

