import unittest
import math
import molio
import molpeakgen
import peakgen
import predictor
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.peaks import PeakList
from org.nmrfx.project import ProjectBase


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

if __name__ == '__main__':
    testProgram = unittest.main(exit=False)
    result = testProgram.result

