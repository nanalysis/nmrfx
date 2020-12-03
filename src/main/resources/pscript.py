import array
from org.nmrfx.peaks import PeakList
from org.nmrfx.peaks import Peak
from org.nmrfx.peaks.io import PeakReader
from org.nmrfx.peaks.io import PeakWriter
from org.nmrfx.processor.datasets.peaks import NUSConScore

class NMRFxPeakScripting:

    def __init__(self):
        self.cmd = PeakList

    def get(self, specifier):
        return self.cmd.get(specifier)

    def read(self, fileName, doLinks=False):
        pRead = PeakReader()
        peakList = pRead.readPeakList(fileName)
        if doLinks:
            pRead.linkResonances()
        return peakList

    def write(self, peakList, fileName):
        PeakWriter.writePeaksXPK2(fileName, peakList)

    def nuscon(self, peakListName1, peakListName2, dMax = 4.0):
        peakList1 = self.get(peakListName1)
        peakList2 = self.get(peakListName2)
        if peakList1 == None:
            raise "No peakList "+peakListName1
        if peakList2 == None:
            raise "No peakList "+peakListName2
     
        nusConScore = NUSConScore(peakList1, peakList2)
        nusConScore.calculate(dMax)
        accuracy = nusConScore.getAccuracy()
        linearity = nusConScore.getLinearity()
        truePositiveRate = nusConScore.getTruePositiveRate()
        falsePositiveRate = nusConScore.getFalsePositiveRate()
        result = {"accuracy":accuracy, "linearity":linearity, "truePos":truePositiveRate, "falsePos": falsePositiveRate}
        return result
  

    def nusconValley(self, dataset, peakSpec1, peakSpec2, iDim):
        peak1 = PeakList.getAPeak(peakSpec1)
        peak2 = PeakList.getAPeak(peakSpec2)
        score = NUSConScore.valleyToPeak(dataset, peak1,peak2, iDim)
        return score
    
        
npk = NMRFxPeakScripting()

