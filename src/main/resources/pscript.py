import array
from org.nmrfx.processor.datasets.peaks import PeakList
from org.nmrfx.processor.datasets.peaks import Peak
from org.nmrfx.processor.datasets.peaks.io import PeakReader
from org.nmrfx.processor.datasets.peaks.io import PeakWriter

class NMRFxPeakScripting:

    def __init__(self):
        self.cmd = PeakList

    def get(self, specifier):
        return self.cmd.get(specifier)

    def read(self, fileName, doLinks=False):
        pRead = PeakReader(doLinks)
        peakList = pRead.readPeakList(fileName)
        if doLinks: pRead.linkResonances()
        return peakList

    def write(self, peakList, fileName):
        PeakWriter.writePeaksXPK2(fileName, peakList)

npk = NMRFxPeakScripting()

