import array
from org.nmrfx.processor.datasets.peaks import PeakList
from org.nmrfx.processor.datasets.peaks import Peak

class NMRFxPeakScripting:

    def __init__(self):
        self.cmd = PeakList

    def get(self, specifier):
        return self.cmd.get(specifier)

npk = NMRFxPeakScripting()

