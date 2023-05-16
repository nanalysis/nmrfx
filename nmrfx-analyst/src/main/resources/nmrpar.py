import math
from org.nmrfx.processor.processing import Processor

processor = Processor.getProcessor()

def ppmToFrac(fidInfo, ppm,size,  dim):
    sw = fidInfo.sw[dim]
    sf = fidInfo.sf[dim]
    ref = fidInfo.ref[dim]
    refpt = size / 2
    fsize = fidInfo.size[dim]
    delRef = ref - ppm
    swPPM = sw / sf
    ppmFrac = delRef / swPPM
    refFrac = float(refpt) / size
    f =  ppmFrac + refFrac
    if f < 0.0:
        f = 0.0
    if f > 1.0:
        f = 1.0
    return f

def getTdSizes(nmrData=None):
    '''return time domain sizes'''
    if not nmrData:
        nmrData = processor.getNMRData()
    nDim = nmrData.getNDim()   # getSize dim is 1-based
    sizes = [ nmrData.getSize(i) for i in range(nDim) ]
    return sizes

def getFdSizes(tdSizes = None):
    '''return frequency domain sizes to next power of two'''
    if (tdSizes == None):
        tdSizes = getTdSizes()
    sizes = [ 2 * nextPowerOfTwo(tdSizes[i]) for i in range(len(tdSizes)) ]
    sizes[0] = sizes[0] / 2
    return sizes

def nextPowerOfTwo(size):  # e.g. tdSizes = [640, 128]
    ival = math.log(size) / math.log(2)
    if (ival != int(ival)):
        ival = ival + 1
    pow = int(math.pow(2, int(ival) + 1))
    return pow

def getZfSize(vecSize,factor,size):
    if (size < 0):
        size = int(math.pow(2, math.ceil((math.log(vecSize) / math.log(2)) + factor)))
    return size

def getExtendSize(vecSize,predictEnd,insert):
    if insert:
        size=vecSize    
    else:
        if (predictEnd <= 0):
            predictEnd = 2*vecSize-1
        size = predictEnd+1
    return size

def getExtractSizeP(vsize,fidInfo,curDim,fstart,fend):
    sw = fidInfo.sw[curDim]
    sf = fidInfo.sf[curDim]
    size = int((fstart-fend)*sf/sw*vsize+0.5)+2
    return size

def getExtractSize(vsize,fstart,fend):
    start=0
    end=0
    if (fstart >= 0.0):
        start = int(fstart * vsize)
    if (fend >= 0.0):
        end = int(fend * vsize - 1)
        if (end >= vsize):
            end = vsize - 1
    size = end - start + 1
    return size

def getFilterSize(vsize, ncoefs, factor):
    size = (vsize - int(ncoefs / 2)) / factor
    return size

def getBzSize(vsize, delay, alg):
    if (alg=='sim' or alg=='ph' or alg=='chop'):
        size = int(vsize - delay)
    else:
        size = vsize
    return size

def refByRatio(refSF,refCenter,sf,nucleus):
    nucleus = nucleus.upper()
    ratios = {'C':0.251449530, 'N':0.101329118, 'P':0.404808636, 'D':0.15306088, 'H':1.0}
    refZero = refSF/(1.0+refCenter/1.0e6)
    zeroC = refZero*ratios[nucleus]
    refCenterC = (sf-zeroC)*1.0e6/zeroC
    return refCenterC

def getWaterPPM (temp):
    a = -0.009552
    b = 5.011718
    ppm =   a*(temp - 273.15) + b
    return ppm

def pairList(alist):
    blist = []
    elem = []
    for a in alist:
        elem.append(a)
        if (len(elem) > 1):
#            blist.append(elem)
            blist.append( complex(elem[0], elem[1]) )
            elem = []
    return blist

def unPairList(clist):
    blist = []
    for c in clist:
        blist.append( c.real )
        blist.append( c.imag )
    return blist
