from org.nmrfx.peaks import Peak
from org.nmrfx.peaks import PeakList

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

def addPeak(peakList,ppms,eppms, widths,bounds,intensity,names):
    peak = peakList.getNewPeak()
    for i,(ppm,eppm,width,bound,name) in enumerate(zip(ppms,eppms,widths,bounds,names)):
        peakDim = peak.getPeakDim(i)
        peakDim.setChemShiftValue(ppm)
        peakDim.setChemShiftErrorValue(eppm)
        peakDim.setLabel(name)
        peakDim.setLineWidthValue(width)
        peakDim.setBoundsValue(bound)
    peak.setIntensity(intensity)
    peak.setVolume1(intensity)
    return peak
