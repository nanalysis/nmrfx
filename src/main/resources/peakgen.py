from org.nmrfx.processor.datasets.peaks import Peak
from org.nmrfx.processor.datasets.peaks import PeakList
from org.nmrfx.processor.datasets import Dataset

def makePeakListFromDataset(listName,dataset, nDim=0):
    if nDim == 0:
        nDim = dataset.getNDim()
    peakList = PeakList(listName, nDim)
    for i in range(nDim):
        specDim = peakList.getSpectralDim(i)
        specDim.setSf(dataset.getSf(i))
        specDim.setSw(dataset.getSw(i))
        specDim.setDimName(dataset.getLabel(i))
    peakList.setDatasetName(dataset.getName())
    return peakList

def makePeakList(listName,labels,sfs,sws):
    nDim = len(labels)
    peakList = PeakList(listName, nDim)
    for i,(label,sf,sw) in enumerate(zip(labels,sfs,sws)):
        specDim = peakList.getSpectralDim(i)
        specDim.setSf(sf)
        specDim.setSw(sw)
        specDim.setDimName(label)
    peakList.setDatasetName("peaks")
    return peakList

def addPeak(peakList,ppms,widths,intensity,names):
    peak = peakList.getNewPeak()
    for i,(ppm,width,name) in enumerate(zip(ppms,widths,names)):
        peakDim = peak.getPeakDim(i)
        peakDim.setChemShiftValue(ppm)
        peakDim.setLabel(name)
        peakDim.setLineWidthValue(width)
        peakDim.setBoundsValue(width*2.0)
    peak.setIntensity(intensity)
    peak.setVolume1(intensity)
