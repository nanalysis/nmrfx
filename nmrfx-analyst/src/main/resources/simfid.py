#n-D data making multiple datasets pertaining to different concentrations of ligand of randomized/Bmrb Data
from org.nmrfx.processor.datasets import Dataset
from org.nmrfx.processor.math import Vec
import random
import math

# should replace jarray with array
import jarray
import parsebmrb


class SignalRng:
    def __init__(self,frMin, frMax, amp, ampSd, lw, lwSd):
        self.frMin = frMin
        self.frMax = frMax
        self.amp = amp
        self.ampSd = ampSd
        self.lw = lw
        self.lwSd = lwSd

class NMRSignal:
    def __init__(self,id,fr,amp=None,lw=None):
        self.id = id
        self.amp = amp
        self.fr = fr
        self.lw = lw
        self.rShift = 0.0
        self.c = 0.0
       
    def copy(self):
        newSig = NMRSignal(self.id, self.fr,self.amp,self.lw)
        newSig.rShift = self.rShift
        newSig.c = self.c
        return newSig

    def setFr(self,fr):
        self.fr = fr

    def setAmp(self,amp):
        self.amp = amp

    def setLW(self,lw):
        self.lw = lw

    def setPar(self,par,value):
        if par=="amp":
            self.amp = value
        elif par=="fr":
            self.fr = value
        elif par=="lw":
            self.lw = value

    def getPar(self,par):
        value = None
        if par=="amp":
            value = self.amp
        elif par=="fr":
            value = self.fr
        elif par=="lw":
            value = self.lw
        return value

    def ranShift(self,fr):
        self.rShift = fr

    def randomize(self, rng):
        if self.fr == None:
            nDim = len(rng.frMax)
        else:
            nDim = len(self.fr)

        if self.lw == None:
            self.lw = []
            lwG  = random.gauss(rng.lw[0],rng.lwSd[0])
            for i in range(nDim):
                self.lw.append(lwG*rng.lw[i]/rng.lw[0])
        if self.amp == None:
            self.amp = random.gauss(rng.amp, rng.ampSd)

        if self.fr == None:
            self.fr = []
            for i in range(nDim):
                f = random.random()
                delta = rng.frMax[i] - rng.frMin[i] 
                shift = f*delta+rng.frMin[i]
                self.fr.append(shift)

class DatasetPars:
    def __init__(self, dimSizes, sf, sw, dimLabels, ref=None):
        self.dimSizes = dimSizes
        self.sf = sf
        self.sw = sw
        self.dimLabels = dimLabels
        self.sw = sw
        self.ref = ref
        self.nDims = len(dimSizes)

    def setRef(self, ref):
        self.ref = ref

def loadBMRBShifts(fileName, atomNames=['H','N']):
    assignData = parsebmrb.scanBMRB(fileName)
    shifts = parsebmrb.processAssign(assignData,atomNames)
    return shifts

def getSZ(dims = []):
        SZ = [1] * (len(dims)-2)
        dims = dims[1:len(dims)-1]
        i = 0
        while len(dims) != 0:
                for j in dims:
                        SZ[i] *= j
                dims.pop()
                i += 1
        return SZ

def getPosition(i,SZ, pos):
        j = list(SZ)
        if len(j)== 0:
                pos.append(i);
                return pos
        if len(j) != 1:
                pos.append(i/j[0])
                i %= j[0]
                j.pop(0)
                return(getPosition(i,j,pos))
        pos.append(i/j[0])
        i %= j[0]
        pos.append(i)
        pos.reverse()
        return pos

def randomizeSignals(signals,sigRng):
        parameters = ['amp','fr','lw']
        for signal in signals:
            signal.randomize(sigRng)

def genRand(signals,rngSet,dims):
        parameters = ['amp','fr','lw']
        for signal in signals:
                for parameter in parameters:
                        if signal.getPar(parameter) != None:
                                continue
                        else:
                                if parameter == 'amp':
                                        signal.setAmp(random.gauss(rngSet['amp'][0],rngSet['amp'][1]))
                                else:
                                        value = []
                                        for dim in range(dims):
                                                value.append(random.gauss(rngSet[parameter][2*dim],rngSet[parameter][2*dim+1]))
                                        signal.setPar(parameter,value)

def makeSignals(shiftSets):
    signals = []
    for shiftSet in shiftSets:
        (seq,shifts) = shiftSet
        signal = NMRSignal(seq,shifts)
        signals.append(signal)
    return signals

def makeEmptySignals(nSig):
    signals = []
    for i in range(nSig):
        signal = NMRSignal(i,None)
        signals.append(signal)
    return signals


#Builds parameters to fill dictionaries with missing information
def setRanges():
    rngSet = {}
    rngSet['amp'] = [ampAve[0],ampEr[0]]
    rngSet['fr'] = [6.0,10.0,100.0,130.0]  #This is in PPM
    rngSet['lw'] =[]
    for dim in range(nDims):
        rngSet['lw'].append(lwAve[dim])
        rngSet['lw'].append(lwEr[dim])
    return rngSet



########################################################################################################################################
#Finds the center of the ppm values to center the graphing of the peaks

def findCenter(nDims, signals):
    center = []
    for dim in range(nDims):
        freqs = [sig.fr[dim] for sig in signals]
        center.append((max(freqs)-min(freqs))/2.0+min(freqs))
    return center

def decaySignal(signals, delay, rates):
    newSignals = []
    for signal in signals:
        newSig = signal.copy()
        newSig.amp = signal.amp * math.exp(-delay*rates)
        newSignals.append(newSig)
    return newSignals

def applyDecay(signals, decayDict, plane):
    newSignals = []
    for signal in signals:
        newSig = signal.copy()
        key = str(signal.id)
        decayValues = decayDict[key]
        newSig.amp = signal.amp * decayValues[plane]
        newSignals.append(newSig)
    return newSignals
##################
def convertToHz(dataPars, signals, center):
    nDims = len(center)
    if nDims > 2:
        nDims = 2
    for signal in signals:
        for dim in range(nDims):
            ppmCtr = signal.fr[dim] - dataPars.ref[dim]
            signal.fr[dim] = -1.0 * ppmCtr * dataPars.sf[dim]

#COMPUTATIONS FOR MULTIDIMENSIONAL DATA
def loopNDims(dataset, dataPars):
    iterations = 1;
    for dim in range(1,dataPars.nDims):
        if not dim in dataPars.pseudoDims:
            iterations *= dataPars.dimSizes[dim]
    return iterations

def createDataset(dataPars, datasetName):
    dimSizeArray=jarray.array(dataPars.dimSizes,'i')
    dataset = Dataset.createDataset(datasetName,'hsqc.nv',dimSizeArray)
    dataset = Dataset(datasetName, 'hsqc.nv', True)
    for dim in range(dataPars.nDims):
        dataset.setSf(dim,dataPars.sf[dim])
        dataset.setSw(dim,dataPars.sw[dim])
        dataset.setLabel(dim,dataPars.dimLabels[dim])
        dataset.setRefValue(dim,dataPars.ref[dim])
        dataset.setRefPt(dim,dataPars.dimSizes[dim]/2)
        dataset.syncPars(dim)
    if dataPars.nDims > 2:
        dataset.setComplex(2,False)
        dataset.setFreqDomain(2,False)
    dataset.close

def addDatasetSignals(dataset, dataPars, datasetName, signals, plane, noise, delay=None, fp=0.5, frMul=None, offset=0.0, ph=0.0, pep=False):
    dimSizes = dataPars.dimSizes
    dLabels = dataPars.dimLabels
    sf = dataPars.sf
    #sz is an array that helps finding the position in space for a row of data to be generated
    #Should be empty array if only 2 dimensions
    sz = getSZ(dataPars.dimSizes)

    #Random shift errors are generated for each signal, both in decay or in titration experiments
    for signal in signals:
        #ranShift = [random.gauss(0,(errorShifts[0]))*sf[0],random.gauss(0,(errorShifts[1]))*sf[1]]
        ranShift = [0.0, 0.0]
        signal.ranShift(ranShift)

    #Iterations represents the number of times data is generated for a row.  This is based on the size of all dimensions beyond the first generation
    nPhases = 2
    iterations = loopNDims(dataset, dataPars);
    iterations /= nPhases
    iTimes = [0] * (dataPars.nDims-1)
    if frMul == None:
        frMul = [1.0]*dataPars.nDims
    sw = dataPars.sw
    vectors = []
    for j in range(nPhases):
        vector = Vec(dimSizes[0]/2,True)
        vector.setSW(sw[0])
        vector.setSF(sf[0])
        vectors.append(vector)
    i = 0
    for iGroup in range(iterations):
        for j in range(nPhases):
            SZ = list(sz)
            i = iGroup*nPhases+j
            position = getPosition(i,SZ,[])
            for dim in range(len(position)):
                deltaT = 1.0/(sw[dim+1])
                iTimes[dim] = (position[dim]/2)*deltaT
                if delay != None:
                   iTimes[dim] += delay[dim+1] * deltaT
            vectors[j].zeros()
            vectors[j].makeComplex()
            f = [0] * dataPars.nDims
            for signal in signals:
                amp = signal.amp
                f[0] = (signal.fr[0]+signal.rShift[0]) * frMul[0]
                lw = signal.lw

                #for dim in range(len(position)):
                for dim in range(1):
                    f[dim+1] = (signal.fr[dim+1]+signal.rShift[dim+1]) * frMul[dim+1]
                    fR = f[dim+1]
                    #  pep not working yet
                    if pep:
                        if (j % 2) == 0:
                            amp *= (math.cos(2*math.pi*fR*iTimes[dim]))*math.exp(-iTimes[dim]*lw[dim+1]*math.pi)
                        else:
                            amp *= -1.0*(math.cos(2*math.pi*fR*iTimes[dim]))*math.exp(-iTimes[dim]*lw[dim+1]*math.pi)
                    else:
                        if (j % 2) == 0:
                            amp *= (math.cos(2*math.pi*fR*iTimes[dim]))*math.exp(-iTimes[dim]*lw[dim+1]*math.pi)
                        else:
                            amp *= (math.sin(2*math.pi*fR*iTimes[dim]))*math.exp(-iTimes[dim]*lw[dim+1]*math.pi)

                #The above manipulation allows signals to be generated as if real data is collected.  Below each signal is mapped into the nv file
                fR = random.gauss(f[0],lw[0]*0.10)
                lwR = lw[0]
                vectors[j].genSignalHz(fR,lwR,amp,ph)
            vectors[j].fp(fp)
            vectors[j].add(offset)
            vectors[j].genNoise(noise)
        for j in range(nPhases):
            i = iGroup*nPhases+j
            position = getPosition(i,SZ,[])
            position[1] = plane
            indice=jarray.array(position,'i')
            dataset.writeVector(vectors[j], indice, 0)

def saveRefPars(dataset, dataPars):
    for dim in range(dataPars.nDims):
        dataset.setRefValue(dim,dataPars.ref[dim])
        dataset.setRefPt(dim,dataPars.dimSizes[dim]/2)
        dataset.syncPars(dim)
        #Resetting labels to correct values
        dataset.setSf(dim,dataPars.sf[dim])
        dataset.setSw(dim,dataPars.sw[dim])
        dataset.setLabel(dim,dataPars.dimLabels[dim])
        dataset.setRefPt(dim,dataPars.dimSizes[dim]/2)
        dataset.setRefValue(dim,dataPars.ref[dim])
        dataset.syncPars(dim)
        dataset.close
    if dataPars.nDims > 2:
        dataset.setComplex(2,False)
    dataset.writeHeader()
    dataset.writeParFile()
    dataset.close()

def doSim(datasetName, bmrbFileName=None, nSigs=20, sigRng=None, noise=0.00005, dataPars=None, delay=None, fp = 0.5, frMul=None, offset=0.0, ph=0.0, pep=False, decayFile=None):
    if dataPars == None:
        dimSizes=[2048,512]
        sfH = 600.0
        sf = [sfH,sfH*0.101329118]
        sw = [3000.0,3000.0]
        dLabels = ['H','N']
        dataPars = DatasetPars(dimSizes, sf, sw, dLabels)

    nDims = len(dataPars.dimSizes)
    dataPars.pseudoDims=[2]


    if bmrbFileName != None:
        shiftSets = loadBMRBShifts(bmrbFileName)
        signals = makeSignals(shiftSets)
    else:
        signals = makeEmptySignals(nSigs)

    if sigRng == None:
        sigRng = SignalRng(frMin=[6.0,90.0], frMax=[11.0,125.0], amp=0.01, ampSd=0.002, lw=[20,18], lwSd=[5,4])

    randomizeSignals(signals, sigRng)
    center = findCenter(2, signals)
    center = [0.0,0.0]
    fcenter = list(center)
    fcenter.append(1)
    #dataPars.setRef(fcenter)
    convertToHz(dataPars, signals, center);
    if not decayFile:
        createDataset(dataPars, datasetName)
        dataset = Dataset(datasetName, 'hsqc.nv', True)
        for plane in range(dataPars.dimSizes[2]):
            newSignals = decaySignal(signals, plane*100, 0.003)
            addDatasetSignals(dataset, dataPars, datasetName, newSignals, plane, noise, delay, fp, frMul, offset, ph, pep)
        values=[1.0]*dataPars.dimSizes[2]
    else:
        headValues, decayValues, planes = readDecayFile(decayFile)
        if planes != dataPars.dimSizes[2]:
            print "Changing size of dim 3 from", dataPars.dimSizes[2], "to", planes
            dataPars.dimSizes = dataPars.dimSizes[0:2] + [planes]
        createDataset(dataPars, datasetName)
        dataset = Dataset(datasetName, 'hsqc.nv', True)
        for plane in range(dataPars.dimSizes[2]):
            newSignals = applyDecay(signals, decayValues, plane)
            addDatasetSignals(dataset, dataPars, datasetName, newSignals, plane, noise, delay, fp, frMul, offset, ph, pep)
        if headValues != []:
            values = headValues
            if "CPMG" in decayFile:
                values.insert(0, 0.0)
        else:
            values=[1.0]*dataPars.dimSizes[2]

    dataset.setValues(2, values)

    saveRefPars(dataset, dataPars)

def readDecayFile(file):
    decayValues = {}
    for line in open(file,'r'):
        arr = line.split()
        if arr[0] == 'Residue':  #Header line
            #print 'header = ', arr[1:]
            headValues = [float(x) for x in arr[1:] if not any(c.isalpha() for c in x)]
            #print 'header values = ', headValues
        if arr[0] != 'Residue':
            decayValues[arr[0]] = [float(x) for x in arr[1:]]
            planes = len(decayValues[arr[0]])
    return [headValues, decayValues, planes]

