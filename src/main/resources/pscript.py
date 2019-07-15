import array
from org.nmrfx.processor.datasets.peaks import PeakList
from org.nmrfx.processor.datasets.peaks import Peak
from org.nmrfx.processor.datasets.peaks.io import PeakReader

class NMRFxPeakScripting:

    def __init__(self):
        self.cmd = PeakList

    def get(self, specifier):
        return self.cmd.get(specifier)

    def read(self, fileName):
        pRead = PeakReader()
        return pRead.readPeakList(fileName)

npk = NMRFxPeakScripting()

