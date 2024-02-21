import math
import ast
import inspect
import re
import os.path
import sys
import subprocess
from scriptutils import formatStringForJava
from org.nmrfx.math.units import Fraction
from org.nmrfx.math.units import Frequency
from org.nmrfx.math.units import Index
from org.nmrfx.math.units import PPM
from org.nmrfx.math.units import Point
from org.nmrfx.math.units import Time
from org.nmrfx.processor.operations import Add
from org.nmrfx.processor.operations import BasicApodization
from org.nmrfx.processor.operations import Asmooth
from org.nmrfx.processor.operations import AutoPhase
from org.nmrfx.processor.operations import AutoPhaseDataset
from org.nmrfx.processor.operations import BCAUTO
from org.nmrfx.processor.operations import BcMed
from org.nmrfx.processor.operations import BcPoly
from org.nmrfx.processor.operations import BcSine
from org.nmrfx.processor.operations import Bcwhit
from org.nmrfx.processor.operations import Blackman
from org.nmrfx.processor.operations import Bucket
from org.nmrfx.processor.operations import Bz
from org.nmrfx.processor.operations import CShift
from org.nmrfx.processor.operations import CoAdd
from org.nmrfx.processor.operations import Cwtd
from org.nmrfx.processor.operations import Combine
from org.nmrfx.processor.operations import Dc
from org.nmrfx.processor.operations import Dept
from org.nmrfx.processor.operations import Dcfid
from org.nmrfx.processor.operations import Dx
from org.nmrfx.processor.operations import Exp
from org.nmrfx.processor.operations import EACombine
from org.nmrfx.processor.operations import ESmooth
from org.nmrfx.processor.operations import Extend
from org.nmrfx.processor.operations import Extract
from org.nmrfx.processor.operations import Expd
from org.nmrfx.processor.operations import Fdss
from org.nmrfx.processor.operations import FFilter
from org.nmrfx.processor.operations import Ft
from org.nmrfx.processor.operations import Ft2d
from org.nmrfx.processor.operations import GapSmooth
from org.nmrfx.processor.operations import Gen
from org.nmrfx.processor.operations import Gf
from org.nmrfx.processor.operations import Gm
from org.nmrfx.processor.operations import Gmb
from org.nmrfx.processor.operations import GRINSOp
from org.nmrfx.processor.operations import Hft
from org.nmrfx.processor.operations import Ift
from org.nmrfx.processor.operations import Ift2d
from org.nmrfx.processor.operations import Imag
from org.nmrfx.processor.operations import Integrate
from org.nmrfx.processor.operations import IO
from org.nmrfx.processor.operations import IstMatrix
from org.nmrfx.processor.operations import IstVec
from org.nmrfx.processor.operations import Kaiser
from org.nmrfx.processor.operations import Mag
from org.nmrfx.processor.operations import Measure
from org.nmrfx.processor.operations import Merge
from org.nmrfx.processor.operations import Mult
from org.nmrfx.processor.operations import NESTANMREx
from org.nmrfx.processor.operations import NESTANMR
from org.nmrfx.processor.operations import Ones
from org.nmrfx.processor.operations import Phase
from org.nmrfx.processor.operations import Phase2d
from org.nmrfx.processor.operations import Power
from org.nmrfx.processor.operations import PythonScript
from org.nmrfx.processor.operations import Rand
from org.nmrfx.processor.operations import RandN
from org.nmrfx.processor.operations import Range
from org.nmrfx.processor.operations import Real
from org.nmrfx.processor.operations import Regions
from org.nmrfx.processor.operations import Reverse
from org.nmrfx.processor.operations import Rft
from org.nmrfx.processor.operations import Schedule
from org.nmrfx.processor.operations import Shift
from org.nmrfx.processor.operations import Sign
from org.nmrfx.processor.operations import SinebellApod
from org.nmrfx.processor.operations import Sqrt
from org.nmrfx.processor.operations import Stack
from org.nmrfx.processor.operations import DGRINSOp
from org.nmrfx.processor.operations import TDCombine
from org.nmrfx.processor.operations import TDPoly
from org.nmrfx.processor.operations import Tilt45
from org.nmrfx.processor.operations import Tm
from org.nmrfx.processor.operations import Tri
from org.nmrfx.processor.operations import Tdss
from org.nmrfx.processor.operations import VecRef
from org.nmrfx.processor.operations import WriteVector
from org.nmrfx.processor.operations import Zeros
from org.nmrfx.processor.operations import Zf
from org.nmrfx.processor.processing.processes import ProcessOps
from org.nmrfx.processor.processing import Processor
from org.nmrfx.processor.datasets.vendor import NMRDataUtil
from org.nmrfx.processor.datasets.vendor import NMRData
from org.nmrfx.math.units import UnitFactory
from org.nmrfx.processor.math import Vec
from org.nmrfx.processor.datasets import DatasetPhaser
from java.util.concurrent import ConcurrentHashMap

from java.util import ArrayList
from java.util import HashMap
import java.lang.Double as Double
import java.lang.Integer as Integer

import psspecial
from nmrpar import ppmToFrac
from nmrpar import getFdSizes
from nmrpar import getTdSizes
from nmrpar import getBzSize
from nmrpar import getExtendSize
from nmrpar import getExtractSize
from nmrpar import getExtractSizeP
from nmrpar import getFilterSize
from nmrpar import getZfSize
from nmrpar import refByRatio
from nmrpar import getWaterPPM

import re

session_globals = {}

processor = Processor.getProcessor()
defaultProcess = processor.getDefaultProcess()
localProcess = None
useLocalProcess = False
fidInfo = None
nestaExecutable='/usr/local/bin/NESTANMR'
argFile = None

# Create dictionary of standard coefficients
#    1, 0, 1, 0, 0, 1, 0,-1],
StdCoefs={
'real':[
    1, 0,0, 0],
'sep':[
    1, 0,0, 1],
'echo-antiecho':[
    1, 0,-1, 0, 0, 1, 0, 1],
'echo-antiecho-r':[
    1, 0, 1, 0, 0, 1, 0,-1],
'hyper':[
    1, 0, 0, 0, 0, 0,-1, 0],
'hyper-r':[
    1, 0, 0, 0, 0, 0, 1, 0],
'ge':[
    1, 0, 1, 0, 1, 0, 1, 0],
'ea3d12':[
    1, 0, 1, 0, 0, 0, 0, 0,
    0,-1, 0, 1, 0, 0, 0, 0,
    0, 0, 0, 0, 1, 0, 1, 0,
    0, 0, 0, 0, 0,-1, 0, 1],
'ea3d21':[
    1, 0, 0, 0, 1, 0, 0, 0,
    0,-1, 0, 0, 0, 1, 0, 0,
    0, 0, 1, 0, 0, 0, 1, 0,
    0, 0, 0,-1, 0, 0, 0, 1],
'ea3d21x':[
    1, 0, 0, 0, 1, 0, 0, 0,
    0, 0,-1, 0, 0, 0,-1, 0,
    0, 1, 0, 0, 0,-1, 0, 0,
    0, 0, 0,-1, 0, 0, 0, 1]
}

class FIDInfo:
    size=[]
    refpt=[]
    label=[]
    mapToFIDList=[]
    mapToDatasetList=[]
    solvent='H2O'
    flags = {}
    acqOrder = []
    acqArray = []
    fidObj = None
    
    def checkParDim(self,pars):
        nd = self.fidObj.getNDim()
        if (len(pars) > nd):
            raise Exception("Number of parameters must be < "+str(nd))

    def printInfo(self):
        print "  size",  self.size
        print "  useSize", self.useSize
        print "  acqArray", self.acqArray
        print "  label", self.label
        print "  refpt", self.refpt
        print "  flags", self.flags
        print "  acqOrder", self.acqOrder
        print "  mapToFID", self.mapToFIDList
        print "  mapToDataset", self.mapToDatasetList

    def getPar(self,par):
        value = self.fidObj.getParDouble(par)
        return value

    def setFlags(self,flags):
        NMRDataUtil.setFlags(self.fidObj,flags)

    def setFixDSP(self,value):
        self.fidObj.setFixDSP(value)

    def setZeroFreq(self,value):
        self.fidObj.setZeroFreq(value)
        
    def setSW(self,pars):
        self.checkParDim(pars)
        for i,par in enumerate(pars):
            self.fidObj.setSW(i,par)

    def setSF(self,pars):
        self.checkParDim(pars)
        for i,par in enumerate(pars):
            self.fidObj.setSF(i,par)

    def setRef(self,pars):
        self.checkParDim(pars)
        for i,par in enumerate(pars):
            self.fidObj.setRef(i, par)

    def setLabel(self,pars):
        self.checkParDim(pars)
        for i,par in enumerate(pars):
            self.label[i] = par

    def read(self,index=0,name="work"):
        global fidInfo
        fidObj = self.fidObj
        size = fidObj.getNPoints()
        v1 = Vec(size, name,  True)
        fidObj.readVector(index,v1)
        return v1

    def isComplex(self, dim):
        '''return whether or not current dimension is complex'''
        fidObj = self.fidObj
        return fidObj.isComplex(dim)   # dim is 1-based

    def setComplex(self, pars):
        '''set whether or not current dimension is complex'''
        self.checkParDim(pars)
        for i,par in enumerate(pars):
            self.fidObj.setComplex(i,par)

    def negatePairsFT(self,dim):
        '''return whether or not to negatePairs for FT for current dimension'''
        fidObj = self.fidObj
        negate = fidObj.getNegatePairs(dim)   # dim is 1-based
        return negate

    def negateImagFT(self,dim):
        '''return whether or not to negateImag for FT for current dimension'''
        fidObj = self.fidObj
        negate = fidObj.getNegateImag(dim)   # dim is 1-based
        return negate

    def getSymbolicCoefs(self,dim):
        '''return indirect dimension coefficients'''
        fidObj = self.fidObj
        return fidObj.getSymbolicCoefs(dim);

    def getCoefs(self,dim='all'):
        '''return indirect dimension coefficients'''
        fidObj = self.fidObj
        nDim = fidObj.getNDim()

        if (dim != 'all'):
            coefs = fidObj.getCoefs(dim)  # not guaranteed to exist

        else:
            coefs = [1.0, 0.0]  # complex
            if (nDim > 1):         # calculate coefs from all dimensions
                for iDim in range(1, nDim):
                    dcoefs = fidObj.getCoefs(iDim)
                    cf = pairList(coefs)
                    df = pairList(dcoefs)
                    # complex array multiplication - not quite correct?
                    koeffs = [ c * d for c in cf for d in df ]
                    coefs = unPairList( koeffs )

        return coefs

    def getPhases(self,dim):
        '''return 0th and 1st order phase corrections for current dimension'''
        fidObj = self.fidObj
        dataset = processor.getDataset()
        if (dataset != None):
            ph0 = dataset.getPh0(dim)   # dim is zero-based
            ph1 = dataset.getPh1(dim)
        else:
            ph0 = fidObj.getPH0(dim)   # dim is 1-based
            ph1 = fidObj.getPH1(dim)
        return ph0, ph1

    def setFIDMap(self,values):
        self.mapToFIDList = list(values)
        processor.adjustSizes();

    def setDatasetMap(self,values):
        self.mapToDatasetList = list(values)
        processor.setMapToDataset(self.mapToDatasetList)
        processor.adjustSizes();

    def mapToFID0(self, iDim):
        if iDim < len(self.mapToFIDList):
            return self.mapToFIDList[iDim]
        else:
            return -1

    def mapToDataset(self, iDim):
        return self.mapToDatasetList[iDim]

def printInfo():
    '''Print out reference info. Useful to see what values the automatic parameter extraction found.'''
    fidInfo.printInfo()

def printDataInfo():
    '''Print out reference info. Useful to see what values the automatic parameter extraction found.'''
    dataInfo.printInfo()

def fixdsp(value):
    ''' Set whether to fix dsp charge-up when reading FID.  Only used for Bruker data.
    '''
    fidInfo.setFixDSP(value)
 
def sw(*pars):
    ''' Sweep width values to set for each dimension.<br>
    Values can be either a numeric value (2000.0) or the name of a vendor specific parameter (in quotes). 
    <br>Examples:
       <ul>
       <li>sw(5000.0,2000.0) Numeric values</li>
       <li>sw('sw','sw1') Agilent VNMR</li>
       <li>sw('SW_h,1','SW_h,2') Bruker</li>
       </ul>
    '''
    fidInfo.setSW(pars)

def sf(*pars):
    ''' Spectrometer frequency at center of spectrum to set for each dimension.<br>
    Values can be either a numeric value (2000.0) or the name of a vendor specific parameter (in quotes). 
    <br>Examples:
       <ul>
       <li>sf(5000.0,2000.0) Numeric values</li>
       <li>sf('sfrq','dfrq') Agilent VNMR</li>
       <li>sf('SFO1,1','SFO1,2') Bruker</li>
       </ul>
    '''
    fidInfo.setSF(pars)

def tdcomplex(*pars):
    ''' Complex values to set for each dimension.<br>
    Values can be True or False. 
    <br>Examples:
       <ul>
       <li>tdcomplex(True,False) Boolean values</li>
       </ul>
    '''
    fidInfo.setComplex(pars)

def zerofreq(zf):
    fidInfo.setZeroFreq(zf)

def ref(*pars):
    ''' Reference position (in ppm) at center of spectrum to set for each dimension.<br>
    Values can be either a numeric value (2000.0), the name of a vendor specific parameter (in quotes), or symbolic values. 
    If symbolic values ('h2o', 'C', 'N') are used the sf and sw values must already be set correctly.
    <br>Examples:
       <ul>
       <li>ref(4.73,115.0) Numeric values</li>
       <li>ref('h2o','N') Set dimension 1 to the shift of water at the experimental temperature, and dimension 2 to value for N using indirect refrence ratio</li>
       </ul>
    '''
    fidInfo.setRef(pars)

def label(*pars):
    ''' Set the label to be used for each dimension.<br>
    <br>Examples:
       <ul>
       <li>label('HN','N15') </li>
       <li>label('H','CA','N') </li>
       </ul>
    '''

    fidInfo.setLabel(pars)

def genLSCatalog(lwMin, lwMax,  nLw, nKeep, nFrac):
    datasetName = dataInfo.filename
    processor.setupSim(lwMin, lwMax, nLw, nKeep, nFrac, datasetName)

def flags(**keywords):
    fidInfo.setFlags(keywords)

# set acquisition order, e.g. acqOrder('p2','p1','d1','d2')
def acqOrder(*order):
    ''' Set acquisiton order used by experiment, including phase and time increments.<br>
    
    <br>Examples:
       <ul>
       <li>acqOrder('p1','d1','p2','d2')</li>
       <li>acqOrder('p2','p1','d1','d2')</li>
       </ul>
    '''
    global fidInfo
    fidInfo.acqOrder = []
    for par in order:
        fidInfo.acqOrder.append(par)
    fidInfo.fidObj.resetAcqOrder() 
    fidInfo.fidObj.setAcqOrder(order)
    processor.setAcqOrder(fidInfo.fidObj.getAcqOrder())

def setupScanTable(fileName):
    global scanTableName
    global scanTable
    scanTableName = fileName
    scanTable = None

def closeScanTable():
    global scanTableName
    global scanTable
    if scanTable != None:
        scanTable.close()
        scanTable = None


def writeToScanTable(iFile, filePath, dataName, map):
    global scanTableName
    global scanTable
    if scanTable == None:
        scanTable = open(scanTableName,'w')
        # write header
        scanTable.write('index\tfid\tdataset')
        if map != None:
            header = getMeasureMapHeader(map)
            if header != None:
                scanTable.write(header)
        scanTable.write('\n')

    outStr = str(iFile) + '\t' + filePath + '\t' + dataName
    if map != None:
        outStr += getMeasureMapData(map)
    scanTable.write(outStr + '\n')

def setMeasureMap(map):
    global gmap
    gmap = map

def getMeasureMap():
    global gmap
    try:
        if gmap == None:
            gmap = ConcurrentHashMap()
    except:
        gmap = ConcurrentHashMap()

    return gmap

def getMeasureMapHeader(map):
    # loop over keys in measure map and get values
    result = ""
    for key in map:
       if key.startswith('measures_'):
           dataValues =  map.get(key)
           for (i,dataValue) in enumerate(dataValues):
               outStr = "\tIntegral%d_%.3f_%.3f\tMax%d_%.3f_%.3f" % (i,dataValue.getStartPPM(),dataValue.getEndPPM(),i,dataValue.getStartPPM(),dataValue.getEndPPM())
               result += outStr
           return result
    return None


def getMeasureMapData(map):
    # loop over keys in measure map and get values
    result = ""
    for key in map:
       if key.startswith('measures_'):
           dataValues =  map.get(key)
           for (i,dataValue) in enumerate(dataValues):
               dataValue.setScale(1.0)
               outStr = "\t%.3f\t%.3f" % (dataValue.getCorrectedSum(),dataValue.getMax())
               result += outStr

    # clear map for next spectrum
    map.clear()
    return result

def inMemory(mode=True):
    global dataInfo
    dataInfo.inMemory = mode

def acqarray(*pars):
    ''' Set acquired array size. 
    '''
    global fidInfo
    global dataInfo
    if dataInfo.extra != 0:
        return
    size = list(fidInfo.maxSize)
    fidInfo.acqArray = []
    for i,par in enumerate(pars):
        if (i != 0) and (par != ''):
            fidInfo.acqArray.append(par)
            fidInfo.fidObj.setArraySize(i,par)
            if par != 0:
                dataInfo.extra = fidInfo.acqArray[i]
        else:
            fidInfo.acqArray.append(0)
            fidInfo.fidObj.setArraySize(i,0)
    size = list(fidInfo.maxSize)
    acqsize(size)

# set fid size limits 
def acqsize(*pars):
    ''' Set acquired size. This is not normally needed, but might be useful if the experiment did not run to completion 
        and you need to specify the number of rows of data that were actually acquired.  Only the size for the indirect
        dimensions can be changed.  Specifying a value of 0, or an empty value indicates that the actual acquired size
        should be used.
    '''
    global fidInfo
    global dataInfo
    global useLocalProcess
    size = list(fidInfo.maxSize)
    for i,par in enumerate(pars):
        if (i != 0) and (par != ''):
            if (fidInfo.maxSize[i] < par):
                size[i] = fidInfo.maxSize[i]
            elif par == 0:
                size[i] = fidInfo.maxSize[i]
            else:
                size[i] = par
    if not useLocalProcess:
        processor.setSizes(size)
        processor.adjustSizes();
        newSizes = processor.getNewSizes()
        size = [s for s in newSizes]

    fidInfo.size = list(size)
    fidInfo.useSize = list(size)
    dataInfo.size = list(size)
    dataInfo.msize = initMSize(fidInfo, size)
            
# set fid size limits 
def tdsize(*size):
    ''' Set time domain size that should actually be used.  Normally set to a value less than or equal to the acqsize value. 
        Only the size for the indirect dimensions can be changed.  Specifying a value of 0, or an empty value indicates 
        that the actual acquired size should be used.  Useful if you want to see what the processed data would be like if fewer
        data rows were acquired, or if there is some corruption of data (by changes to sample or fault in instrument) after
        a certain point.
    '''
    global fidInfo
    fidInfo.useSize = []
    for i,par in enumerate(size):
        #  at present, can't change size of direct dimension
        if i == 0:
            fidInfo.useSize.append(fidInfo.size[i])
        elif par == '':
            fidInfo.useSize.append(fidInfo.size[i])
        elif par == 0:
            fidInfo.useSize.append(fidInfo.size[i])
        else:
            fidInfo.useSize.append(par)

def acqmode(*modes):
    ''' Set the acquisition modes (complex, hypercomplex, echo-antiecho etc. for each dimension. 
    '''
    global fidInfo
    for i,par in enumerate(modes):
        fidInfo.fidObj.setAcqMode(i, par)

def p(par):
    return fidInfo.getPar(par)

def getCurrentProcess():
    global localProcess
    global useLocalProcess
    if (useLocalProcess and localProcess):
        process =  localProcess
    else:
        process =  processor.getCurrentProcess()
    return process

def clearLocalProcess():
    global localProcess
    if localProcess:
        localProcess.clearOps()

def readNUS(fileName, demo=True):
    global fidInfo
    fidObj = fidInfo.fidObj
    fidObj.readSampleSchedule(fileName, demo)

def genNUS(sizes):
    global fidInfo
    fidObj = fidInfo.fidObj
    fidObj.createUniformSchedule(sizes)

def genZFNUS(sizes,newSizes):
    global fidInfo
    fidObj = fidInfo.fidObj
    fidObj.createZFSchedule(sizes, newSizes)

def markrows(*args):
    global fidInfo
    fidObj = fidInfo.fidObj
    for row in args:
        newRow = []
        for v in row[0:-1]:
            if v >= 0:
                v = v -1
            newRow.append(v)
        groupIndex = row[-1]
        fidObj.addSkipGroup(newRow, groupIndex)

class genericOperation(object):
    def __init__(self, f):
        self.f = f
        self.__doc__ = f.__doc__
        self.defs = {}
        name_val = zip(inspect.getargspec(f)[0], inspect.getargspec(f)[-1])
        for name,val in name_val:
            self.defs[name] = val
        self.arguments = inspect.getargspec(self.f)[0]

    def __call__(self,*args,**kwargs):
        if argFile != None:
            self.dumpArgs(argFile,self.f.__name__,*args,**kwargs)
        op = self.f(*args,**kwargs)
        if op != None:
            if 'vector' in kwargs and kwargs['vector'] != None:
                op.eval(kwargs['vector'])
            else:
                process.add(op)
        return op

    def dumpArgs(self,argFile,opName,*args,**kwargs):
        argFile.write(opName)
        nArgSupplied = len(args)
        for iArg,arg in enumerate(self.arguments):
            if iArg < nArgSupplied:
                value = args[iArg]
            elif arg in kwargs:
                value = kwargs[arg]
            elif arg in self.defs:
                value = self.defs[arg]
            else:
                raise 'No value'
            argFile.write('\t'+arg+'\t'+str(value))
        argFile.write('\n')

class DataInfo:
    filename = 'data.nv'
    curDim   = -1
    size     = []
    createdSize     = []
    useSize     = None
    resizeable = True    # size may change or not
    inMemory = False
    extra     = 0

    def printInfo(self):
        print "     size", self.size
        print "    msize", self.msize
        print "  useSize", self.useSize
        print "resizable", self.resizeable
        print "    extra", self.extra



def initLocal():
    global fidInfo
    global dataInfo
    global localProcess
    global useLocalProcess
    useLocalProcess = True
    dataInfo = DataInfo()
    dataInfo.curDim = 1
    dataInfo.resizeable = False
    localProcess = ProcessOps()

def useLocal():
    global useLocalProcess
    useLocalProcess = True
    processor.clearProcessorError();
    dataInfo.resizeable = False

def useProcessor(inNMRFx=False):
    global dataInfo
    global useLocalProcess
    global processor
    global defaultProcess
    global nmrFxMode
    nmrFxMode = inNMRFx
    useLocalProcess = False
    processor.reset()
    processor.clearDatasets()
    processor.clearProcessorError();
    defaultProcess = processor.getDefaultProcess()
    if dataInfo.extra == 0:
        dataInfo = DataInfo()
    dataInfo.resizeable = True

# should FID be done by open with testing for file type
def FID(fidFileName, tdSize=None, nusFileName=None, **keywords):
    ''' Open a raw  NMR dataset (FID file).<br>
    Parameters
    ---------
    fidFileName : string
        Name of the file to open. 
    tdSize : array 
        Size of each time domain dimension.  Automatically determined from paramter files if not specified.
    keywords : keywords
        Optional list of arguments describing data
    '''
    if (tdSize):
        fidObj = processor.openfid(formatStringForJava(fidFileName), nusFileName, tdSize)
    else:
        fidObj = processor.openfid(formatStringForJava(fidFileName), nusFileName)

    fidInfo = makeFIDInfo(fidObj,tdSize)
    if (keywords):  # may use keywords for flags
        fidInfo.flags = keywords
    return fidInfo

def makeFIDInfo(fidObj=None, tdSize=None, **keywords):
    global fidInfo
    fidInfo = FIDInfo()
    if (not fidObj):
        fidObj = NMRDataUtil.getCurrentData()
    if (not fidObj):
        return None
    if (not tdSize):
        tdSize = getTdSizes(fidObj)
    fidInfo.size = list(tdSize)
    fidInfo.useSize = list(tdSize)

    fidInfo.fidObj = fidObj 

    fidInfo.solvent = fidObj.getSolvent()
    fidInfo.nd = fidObj.getNDim()
    fidInfo.sf = []
    fidInfo.ref = []
    fidInfo.refpt = []
    fidInfo.label = []
    fidInfo.maxSize = []

    fidInfo.mapToFIDList = []
    fidInfo.mapToDatasetList = []
    for i in range(fidInfo.nd):
        fidInfo.mapToDatasetList.append(-1)

    j = 0
    for i in range(fidInfo.nd):
        fidInfo.label.append('D'+str(i))
        fidInfo.maxSize.append(fidObj.getMaxSize(i))
        fidInfo.acqArray.append(0)
        if fidObj.getSize(i) > 1:
            fidInfo.mapToFIDList.append(i)
            fidInfo.mapToDatasetList[i] = j
            j += 1

    acqOrder = fidObj.getAcqOrder()
    fidInfo.acqOrder = acqOrder

    if (keywords):  # may use keywords for flags
        fidInfo.flags = keywords

    return fidInfo

def CREATE(nvFileName, dSize=None, extra=0):
    ''' Create a new NMRViewJ format dataset.  If file already exists it will be erased first.<br>
    Parameters
    ---------
    nvFileName : string
        Name of the dataset file to create. 
    dSize : array 
        The size of the dimensions.  If not specified the size automatically determined from processing script.
    '''
    global fidInfo
    global dataInfo
    global nmrFxMode
    try:
        if nmrFxMode:
            #nvFileName += ".tmp"
            pass
    except:
        pass
    dataInfo.filename = nvFileName
    if (dSize == None):
        dSize = fidInfo.size
        dataInfo.size = list(dSize)
        dataInfo.msize = initMSize(fidInfo, dSize)
    else:
        dataInfo.size = list(dSize)
        dataInfo.msize = initMSize(fidInfo, dSize)
        createDataset()

    aSize = fidInfo.fidObj.getArraySize(0)
    if aSize != 0:
        dataInfo.extra = aSize
    else:
        dataInfo.extra = extra
        if dataInfo.extra != 0:
            processor.keepDatasetOpen(True)
    DIM(1)  # default start dim

def initMSize(fidInfo, size):
    msize = []
    for i,sz in enumerate(size):
        nsz = sz
        if fidInfo.isComplex(i):
            nsz = sz * 2;
        msize.append(nsz)
    return msize


def createDataset(nvFileName=None):
    global fidInfo
    global dataInfo

    if (nvFileName == None):
        nvFileName = dataInfo.filename
    useSize = []
    j=0
    for i,sz in enumerate(dataInfo.msize):
        fidDim = fidInfo.mapToFID0(i)
        useSize.append(fidInfo.useSize[i])

    if dataInfo.extra != 0:
        useSize.append(dataInfo.extra)
        fidInfo.mapToDatasetList.append(len(useSize) - 1)

    if not processor.isDatasetOpen():
        try:
            os.remove(nvFileName)
        except OSError:
            pass

        parFileName = os.path.splitext(nvFileName)[0]+'.par'
        try:
            os.remove(parFileName)
        except OSError:
            pass
        if dataInfo.inMemory:
            processor.createNVInMemory(formatStringForJava(nvFileName), useSize, fidInfo.mapToDatasetList)
        elif (fidInfo and fidInfo.flags):
            processor.createNV(formatStringForJava(nvFileName), useSize, fidInfo.flags)
        else:
            processor.createNV(formatStringForJava(nvFileName), useSize, fidInfo.mapToDatasetList)

        dataset = processor.getDataset()
        psspecial.datasetMods(dataset, fidInfo)

    dataInfo.resizeable = False  # dataInfo.size is fixed, createNV has been run

def closeDataset():
    if not processor.isDatasetOpen():
        processor.closeDataset()

def setDataInfo(dSize):
    global fidInfo
    dataset = processor.getDataset()
    nDim = dataset.getNDim()
    if (fidInfo):
        dataset.setSolvent(fidInfo.solvent)
        for iDim in range(nDim):
            fidDim = fidInfo.mapToFID0(iDim)
            if fidDim != -1:
                dataset.setRefValue(iDim,fidInfo.fidObj.getRef(fidDim))
                dataset.setRefValue_r(iDim,fidInfo.fidObj.getRef(fidDim))
                dataset.setSw(iDim,fidInfo.fidObj.getSW(fidDim))
                dataset.setSf(iDim,fidInfo.fidObj.getSF(fidDim))
                if fidInfo.label:
                    dataset.setLabel(iDim,fidInfo.label[fidDim])
        if fidInfo.label:
            if (nDim > len(fidInfo.label)):
                dataset.setLabel(nDim-1,'array')

def getAcqOrder():
    '''return nmrData acquisition order'''
    global fidInfo
    return fidInfo.acqOrder

def OPEN(nvFileName, resize=False):
    global fidInfo
    global dataInfo
    processor.openNV(formatStringForJava(nvFileName))
    dataset = processor.getDataset()
    fidInfo = FIDInfo()
    fidInfo.size = dataset.getSizes()
    dataInfo = DataInfo()
    dataInfo.dataset = dataset
    dataInfo.filename = nvFileName
    dataInfo.size = dataset.getSizes()
    dataInfo.msize = list(dataInfo.size)
    dataInfo.resizeable = resize
    return dataInfo
# to set pars, use OPEN, then sw() sf() etc, then setDataInfo()

def skip(*args):
    global dataInfo
    global fidInfo
    j = 0
    newSize = []
    fidInfo.mapToFIDList = []
    for i,skip in enumerate(args):
        if skip:
            fidInfo.mapToDatasetList[i] = -1
        else:
            fidInfo.mapToDatasetList[i] = j
            fidInfo.mapToFIDList.append(i)
            j += 1


def DIM(*args):
    ''' Subsequent operations in script apply to the specified dimension number.'''
    global dataInfo
    global fidInfo
    maxDim = len(dataInfo.size)

    if len(args) == 0:
        processor.addDatasetProcess()
    else:
        iDim = args[0]
        if (iDim < 1 or iDim > maxDim):
            raise Exception("DIM("+str(iDim)+"): should be between 1 and "+str(maxDim))
        if len(args) == 1:
            dataInfo.curDim = iDim-1
            processor.addDimProcess(dataInfo.curDim)
        else:
            dims = []
            for dim in args:
                if (dim < 1 or dim > maxDim):
                    raise Exception("DIM("+str(dim)+"): should be between 1 and "+str(maxDim))
                dims.append(int(dim)-1)
            processor.addMatProcess(*dims)

def UNDODIM(iDim):
    ''' Adds a process which undoes the operations in the last instance of the specified dimension number.'''
    global dataInfo
    global fidInfo
    maxDim = len(dataInfo.size)
    if (iDim < 1 or iDim > maxDim):
        raise Exception("DIM("+str(iDim)+"): should be between 1 and "+str(maxDim))
    dataInfo.curDim = iDim-1
    processor.addUndoDimProcess(dataInfo.curDim)
    dataInfo.size[dataInfo.curDim] = fidInfo.size[dataInfo.curDim]

def generic_operation(operation):
    '''decorator to make a basic operation function easier.  Code ends up looking like:
    @generic_operation
    def FUNCTION(arg1, arg2, ...):
        op = FUNCTION(arg1, arg2, ...)
        return op'''

    #The actual decorator.  Any code from the original python operation function
    # is called between getting the process and the execution / addOp phase.
    def inner(*args, **kwargs):
        process = kwargs['process'] if ('process' in kwargs) and (kwargs['process'] != None) else getCurrentProcess()
        #call the originally declared python function with the arguments that
        #the function is called with.
        op = operation(*args, **kwargs)
        if op == None:
            return None
        if 'vector' in kwargs and kwargs['vector'] != None:
            op.eval(kwargs['vector'])
        else:
            process.add(op)
        return op
    sig = list(inspect.getargspec(operation))

    #get arguments of the operation
    arguments = [inspect.getargspec(operation)[0]]

    if arguments != None:
        arguments = inspect.formatargspec(arguments[0])
    else:
        arguments = ""

    #this makes a zip with tuples of the strings of the variable names, and the key value, so its ((variable_name_1, variable_1_default_value), ...)
    #inspect.formatargspec will turn a list into a string 
    name_val = inspect.formatargspec(zip(inspect.getargspec(operation)[0],
                                    inspect.getargspec(operation)[-1]))

    #substitute a tuple with a variable name and its value into a string with:
    #((a=1), (b=2), (c=3), ...)
    def sub_arg_name_and_value(t):
        '''Tuple with ((vector, vector), value, 0.0 + 0.0j).  Convert it to:
        (vector = vector, value=0.0+0.0j)'''
        return re.sub("\([^)]*\)", lambda x: x.group(0).replace(',', '='), t)

    #for the arguments of the decorated function, we pass in a tuple of variable    #names and their default values (as declared in the original operation).
    arguments = sub_arg_name_and_value(name_val)
    #get rid of all parentheses and surround the string with parenthesis again
    arguments = '(' + arguments.replace('(', '').replace(')', '') + ')'

    #key = value passed in from outer function
    #vector=vector, pt1 = pt1
    #variable names are again the variables from the original function,
    #but the values are the values that are passed in, not the default
    #values.  This means that the key is the name of the parameter,
    # but the value is the variable that the function is called with.
    #this is used to make the function call:
    #   inner(value=value, vector=vector, process=process)
    # Obviously, if no value is passed in, it defaults to the default value
    # from the "arguments" variable
    key_val = inspect.formatargspec(zip(inspect.getargspec(operation)[0],
                                    inspect.getargspec(operation)[0]))

    call_values = sub_arg_name_and_value(key_val)
    call_values = '(' + call_values.replace('(', '').replace(')', '') + ')'


    #create function declaration that contains the operation name, that
    #calls "inner" with the correct arguments (that also contains defaults
    #arguments from the operation if none were passed in

    #this declares a function with the original operation name, taking
    #the original arguments, and calling the decorated function.
    src = 'def %s%s :\n' % (operation.__name__, arguments)
    src += '    return inner %s\n' % call_values

    #namespace to declare the function in
    evaldict = {'inner': inner}

    #declare inside evaldict so that we can retain the function name
    #after decorating the function
    exec src in evaldict
    inner = evaldict[operation.__name__]

    #bind docstring from original function to inner
    inner.__doc__ = operation.__doc__

    return inner


@generic_operation
def ADD(value=0 + 0j, first=0, last=-1, disabled=False, vector=None, process=None):
    '''Add value to the vector at all points between first and last, where value can either be an integer (real) or a complex number written as (1.0 + 3j).
    Parameters
    ---------
    value : complex
        The value to add to each data point.
    first : int
        min : 0
        max : size - 1
        The first point of the vector to add to.
    last : int
        min : -1
        max : size - 1
        The last point of the vector to add to.
'''
    if disabled:
        return None
    value = complex(value)
    op = Add(value.real, value.imag, first, last)
    return op

def BCMED(frac=0.1,wrap=False, disabled=False, vector=None, process=None):
    '''Correct the baseline of the vector using the median method.
    Parameters
    ---------
    frac : double
        amin : 0.001
        min : 0.001
        max : 0.50
        amax : 1.00
        window size is set by multiplying frac times the number of extrema in the vector
    wrap : bool
        Wrap baseline fit around edge of spectrum.
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = BcMed(frac,wrap)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op
    

def REGIONS(regions = None,type='frac', signal=False, disabled=False, vector=None, process=None):
    '''Baseline correction using a polynomial fit.
    Parameters
    ---------
    regions : []
        Specify the points of the vector to perform baseline correction on.
    type : {'frac','pts','ppms'}
        Specify the units for the region values.
    signal : bool
        Specify the boundary of peaks instead of the baseline.
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()
    realPoints = ArrayList()

    if regions == None:
        pass
    else:
        for value in regions:
            realPoints.add(float(value))

    op = Regions(realPoints, type, signal)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def AUTOREGIONS(mode='sdev', winSize=16, minBase=12, ratio=10.0, disabled=False, vector=None, process=None):
    '''Baseline correction using a polynomial fit.
    Parameters
    ---------
    mode : {'sdev','cwtd','cwtdf'}
        Specify the mode for auto identifying baseline regions.
    winSize : int
        min : 4
        max : 256
        Size of window used in searching for baseline regions;
    minBase : int
        min : 4
        max : 256
        Baseline regions must be at least this big;
    ratio : real
        amin : 1.0
        min : 1.0
        max : 100.0
        Ratio relative to noise used in determining if region is signal or baseline, or percent baseline in cwtdf mode.
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()

    op = Regions(mode,  winSize, minBase, ratio)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op


def BC(ratio=10.0, disabled=False, vector=None, process=None):
    '''Baseline correction using a polynomial fit.
    Parameters
    ---------
    ratio : real
        amin : 1.0
        min : 1.0
        max : 100.0
        Ratio relative to noise used in determining if region is signal or baseline, or percent baseline in cwtdf mode.
    ''' 
    if disabled:
        return None
    process = process or getCurrentProcess()

    op = BCAUTO(ratio)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op


def BCPOLY(order=2, winSize=16, disabled=False, vector=None, process=None):
    '''Baseline correction using a polynomial fit.
    Parameters
    ---------
    order : int
        min : 1
        max : 8
        Order of the polynomial used in fit;
    winSize : int
        min : 4
        max : 256
        Size of window used in searching for baseline regions;
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()
            
    op = BcPoly(order, winSize)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)   
    return op

def BCSINE(order=1, winSize=16,  disabled=False, vector=None, process=None):
    '''Baseline correction using a sine curve.
    Parameters
    ---------
    order : int
        min : 1
        max : 8
        Order of the polynomial used in fit;
    winSize : int
        min : 4
        max : 256
        Size of window used in searching for baseline regions;
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()

    op = BcSine(order, winSize)

    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def BCWHIT(lamb=5000, order=1, baseline=False, disabled=False, vector=None, process=None):
    '''Baseline correction using a smoother.
    Parameters
    ---------
    lamb : real
        amin : 10.0
        min : 1000.0
        max : 20000.0
        Parameter controlling how close the fit to the baseline should be
    order : int
        min : 1
        max : 2
        Order of the polynomial used in fit;
    baseline : bool
        If true, return the calculated baseline, rather than the corrected vector
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()

    realPoints = ArrayList()

    op = Bcwhit(lamb, order, baseline )

    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

@generic_operation
def BUCKET(buckets=256, disabled=False, vector=None, process=None):
    '''The vector is bucketed by adding adjacent data points.  The vector size after this operation will be equal to the specified number of buckets. The original vector size must be a multiple of the number of buckets.  Each resulting data point will represent the sum of winSize data points where winSize is equal to size/nBuckets
    Parameters
    ---------
    buckets : int
        min : 0
        max : size
        Number of buckets to place data points into.  Vector size must be a multiple of this number.
'''
    if disabled:
        return None
    op = Bucket(buckets)
    return op

def COMB(coef=None, numInVec=0, numOutVec=0, inVec=None, outVec=None, keepImag=False,disabled=False, process=None):
    '''combine inVec and outVec with a list of coefficients
    Parameters
    ---------
    coef : {'hyper','hyper-r','echo-antiecho','echo-antiecho-r','ge','sep','real','auto'}
        How to combine data rows with different phases.
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()
    global fidInfo

    if (coef == None):
        coef = StdCoefs['hyper']
    elif (coef == 'auto'):
        coef = fidInfo.getCoefs()
    else:
        if isinstance(coef, str) and coef in StdCoefs:
            coef = StdCoefs[coef]
        elif isinstance(coef, str):
            coef = coef.split()
            if len(coef) < 2:
                raise Exception("Coefficients "+str(coef)+" are not a valid value")
            else:
                ncoef = []
                for c in coef:
                    ncoef.append(float(c))
                coef = ncoef
    if (not isinstance(coef, (list, tuple))):
        raise Exception("Coefficients "+coef+" are not a list variable")

    if (numInVec == 0):
        nCoef = len(coef)
        numInVec = int(math.log(nCoef/2)/math.log(2))
        numOutVec=numInVec
        if nCoef == 4:
            numOutVec=2
    op = Combine(numInVec, numOutVec, coef,keepImag)
    if (inVec != None): #and outVec != None):
        arrList = Combine.getArrayList()
        for v in inVec:
            arrList.add(v)
        op.eval(arrList)
    else:
        process.addOperation(op)
    return op

def TDCOMB(dim=2,coef=None, numInVec=0, numOutVec=0, inVec=None, outVec=None, disabled=False, process=None):
    '''combine complex inVec and outVec time domain vectors using a list of coefficients
    Parameters
    ---------
    dim : {2,3,4,5}
        Indirect dimension of dataset to combine vectors in.  Use 2 for 2D, 2 or 3 for 3D, etc.
    coef : {'hyper','hyper-r','echo-antiecho','echo-antiecho-r','ge','sep','real','auto'}
        How to combine data rows with different phases.
    '''

    if disabled:
        return None
    process = process or getCurrentProcess()
    global fidInfo

    if (coef == None):
        coef = StdCoefs['hyper']
    elif (coef == 'auto'):
        coef = fidInfo.getCoefs()
    else:
        if isinstance(coef, str) and coef in StdCoefs:
            coef = StdCoefs[coef]
        elif isinstance(coef, str):
            coef = coef.split()
            if len(coef) < 2:
                raise Exception("Coefficients "+str(coef)+" are not a valid value")
            else:
                ncoef = []
                for c in coef:
                    ncoef.append(float(c))
                coef = ncoef
    if (not isinstance(coef, (list, tuple))):
        raise Exception("Coefficients "+coef+" are not a list variable")

    if (numInVec == 0):
        nCoef = len(coef)
        numInVec = int(math.log(nCoef/2)/math.log(2))
        numOutVec=numInVec
         
    op = TDCombine(dim-1,numInVec, numOutVec, coef)
    if (inVec != None): #and outVec != None):
        arrList = TDCombine.getArrayList()
        for v in inVec:
            arrList.add(v)
        op.eval(arrList)
    else:
        process.addOperation(op)
    return op

@generic_operation
def CSHIFT(shift=0, adjref=False, disabled=False, vector=None, process=None):
    '''Circular shift of the data points in the vector by the specified amount.
    Parameters
    ---------
    shift : position
        min : -128
        max : 128
        Amount to shift the vector by.  If float or int use points.  If string, convert units
    adjref : bool
        If true, adjust the referencing of the vector based on shift
'''
    if disabled:
        return None
    shiftObj = convertUnitStringToObject(shift)
    op = CShift(shiftObj, adjref)
    return op

@generic_operation
def TILT45(disabled=False, vector=None, process=None):
    '''Perform 45 degree shear (tilt) on a 2D dataset, as is commonly done on 2D J-Resolved
    datasets.
'''
    if disabled:
        return None

    global fidInfo
    global dataInfo
    fidObj = fidInfo.fidObj
    sw1 = fidObj.getSW(0)
    sw2 = fidObj.getSW(1)
    n1 = fidObj.getSize(0);
    n2 = fidObj.getSize(1);

    # The shift of each row n2 is given by round(m * n2 + c)
    m = (sw2 * n1) / (sw1 * n2)
    c = -(sw2 * n1) / (2 * sw1)

    op = Tilt45(m, c)
    return op


@generic_operation
def COADD(coef=None, disabled=False, vector=None, process=None):
    '''Coaddition of a set of vectors to yield one result vector.
    Parameters
    ---------
    coef : []
        List of coefficients to scale each vector by.
'''
    if disabled:
        return None
    if (not isinstance(coef, (list, tuple))):
        raise Exception("Coefficients "+coef+" are not a list variable")
    if (len(coef) == 0):
        raise Exception("Coefficients list is empty")

    op = CoAdd(coef)
    return op

@generic_operation
def STACK(count=1,group=2,disabled=False):
    '''Stack vectors from rows into planes.
    Parameters
    ---------
    count : int
        amin : 1 
        min : 1 
        max : 32 
        Count of planes in stack.
    group : int
        amin : 1 
        min : 1 
        max : 32 
        Number of vectors in group (kept in plane together).
'''
    if disabled:
        return None

    op = Stack(count,group)
    return op

def CWTD(winSize=32, disabled=False, vector=None, process=None):
    '''Continuous Wavelet Transform Derivative.
    Parameters
    ---------
    winSize : int
        min : 1
        max : 1024
        Size of the window.
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Cwtd(winSize)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def DC(fraction=0.05, disabled=False, vector = None, process=None):
    '''Shifts the spectrum so edges are centered.  DC Offset.
    Parameters
    ---------
    fraction : real
        amin : 0
        min : 0
        max : .33
        amax : .33
        The fraction of points from the beginning and end of a spectrum that will be used to create the offset.
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Dc(fraction)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def MEASURE(key="measures_", map=None, disabled=False, vector = None, process=None):
    '''Measures regions in spectrum.
    Parameters
    ---------
    key : "measures_"
        Prefix to key used to store measure values in a map (dictionary).  Key will have vector row appended.
    map : None
        Map in which to store results.  If not specified (or = None) the default map will be used.  Get the default map with "getMeasureMap()"
'''
    if disabled:
        return None
    map = map or getMeasureMap()

    process = process or getCurrentProcess()
    op = Measure(map, key)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def SCHEDULE(fraction=0.05, endOnly=False, fileName="", disabled=False, vector = None, process=None):
    '''Sets a sample schedule for a 1D vector and zeros points not on schedule.  Used for testing IST.
    Parameters
    ---------
    fraction : real
        amin : 0.05
        min : 0.05
        max : 1.0
        amax : 1.0
        The fraction of points that are collected. Ignored if fileName specified.
    endOnly : bool
        If true, only zero values at end of vector
    fileName : file
        Name of the schedule file to open if set.
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Schedule(fraction, endOnly, formatStringForJava(fileName))
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op


def LP(fitStart=0, fitEnd=0, predictStart=0, predictEnd=0, npred=0, ncoef=0, 
    threshold=5, backward=True, forward=True, mirror=None,disabled=False,vector=None, 
    process=None):
    '''Extend the vector using Linear Prediction.
    Forward or backward linear prediction can be done.  If both are specified
    then both are done and coefficients averaged (forward-backward LP).
    Parameters
    ---------
    fitStart : int
        min : 0
        max : size-1
        First point used in fit. Defaults to 0 or 1 (depending on forward/backward mode) if 0;
    fitEnd : int
        min : 0
        max : size-1
        Last point used in fit.  Defaults to size-1 if 0.
    predictStart : int
        min : 0
        max : size-1
        Position of first predicted point.  Defaults to size if 0.
    predictEnd : int
        min : 0
        max : size*2-1
        Position of last predicted point.  Defaults to 2*size-1 if 0.
    npred : int
        min : 0
        max : size*2-1
        Number of points to predict, only used if predictEnd is 0.
    ncoef : int
        min : 0
        max : size-1
        Number of coefficients.  Defaults to size/2 if 0.
    threshold : int
        min : 4
        max : 10
        Threshold of singular values used in keeping coefficients.  Check this??
    backward : bool
        Do backwards linear prediction.
    forward : bool
        Do forwards linear prediction.
    mirror : {None, 'odd', 'even'}
        Do mirror image linear prediction.
    '''
    '''If fitEnd is equal to zero, then it will be set to the size of the vector.'''
    global dataInfo
    if disabled:
        return None
    process = process or getCurrentProcess()
    mirrorInt = 0
    if mirror:
       if mirror == "even":
           mirrorInt = 2 
       elif mirror == "odd":
           mirrorInt = 1
       elif mirror == "ps90-180":
           mirrorInt = 2
       elif mirror == "ps0-0":
           mirrorInt = 1
       else:
           raise Exception("Invalid mirror option: "+mirror)
    threshold = pow(10.0,-threshold)
    op = Extend(fitStart, fitEnd, predictStart, predictEnd, npred, ncoef, threshold, backward, forward, False, mirrorInt)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def LPR(fitStart=0, fitEnd=0, predictStart=0, predictEnd=0, npred=0, ncoef=0,
    threshold=5, backward=True, forward=True, disabled=False, vector=None,
    process=None):
    '''Replace starting points of the vector using Linear Prediction.
    Forward or backward linear prediction can be done.  If both are specified
    then both are done and coefficients averaged (forward-backward LP).
    Parameters
    ---------
    fitStart : int
        min : 1
        max : size-1
        First point used in fit. Defaults to 0 if 0;
    fitEnd : int
        min : 0
        max : size-1
        Last point used in fit.  Defaults to size-1 if 0.
    predictStart : int
        min : 0
        max : size/4
        Position of first predicted point.  Defaults to 0 if < 0.
    predictEnd : int
        min : 0
        max : size/4
        Position of last predicted point.  Defaults to 0 if 0.
    npred : int
        min : 0
        max : size*2-1
        Number of points to predict, only used if predictEnd is 0.
    ncoef : int
        min : 0
        max : size-1
        Number of coefficients.  Defaults to size/2 if 0.
    threshold : int
        min : 3
        max : 10
        Threshold of singular values used in keeping coefficients.  Value used is 10^-threshold
    backward : bool
        Do backwards linear prediction.
    forward : bool
        Do forwards linear prediction.
    '''
    '''If fitEnd is equal to zero, then it will be set to the size of the vector.'''
    global dataInfo
    if disabled:
        return None
    process = process or getCurrentProcess()
    threshold = pow(10.0,-threshold)
    op = Extend(fitStart, fitEnd, predictStart, predictEnd, npred, ncoef, threshold, backward, forward, True, 0)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op


def EXTRACTP(start=0.0, end=0.0, mode='left',  disabled=False, vector=None, process=None):
    '''Extract a specified range of the vector.
    Parameters
    ---------
    start : real
        min : -1.0
        max : 15.0
        Start point of region to extract
    end : real
        min : -1.0
        max : 15.0
        End point of region to extract
    mode : {'left', 'right', 'all', 'middle','region'}
        Extract a named region (left,right,all,middle) instead of using start and end points
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    fmode = False
    if (mode == 'left'):
        fstart = 0.0
        fend = 0.5
        fmode = True
    elif (mode == 'all'):
        fstart = 0.0
        fend = 1.0
        fmode = True
    elif (mode == 'right'):
        fstart = 0.5
        fend = 1.0
        fmode = True
    elif (mode == 'middle'):
        fstart = 0.25
        fend = 0.75
        fmode = True
    if (fmode):
        op = Extract(fstart,fend)
    else:
        op = Extract(start,end, True)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)


def EXTRACT(start=0, end=0, mode='left', disabled=False, vector=None, process=None):
    '''Extract a specified range of points.
    Parameters
    ---------
    start : int
        min : 0
        max : size-1 
        Start point of region to extract
    end : int
        min : 0
        max : size-1
        End point of region to extract
    mode : {'left', 'right', 'all', 'middle','region'}
        Extract a named region (left,right,all,middle) instead of using start and end points
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    fmode = False
    if (mode == 'left'):
        fstart = 0.0
        fend = 0.5
        fmode = True
    elif (mode == 'all'):
        fstart = 0.0
        fend = 1.0
        fmode = True
    elif (mode == 'right'):
        fstart = 0.5
        fend = 1.0
        fmode = True
    elif (mode == 'middle'):
        fstart = 0.25
        fend = 0.75
        fmode = True
    else:
        global dataInfo
        if (end == 0):
            try:
                end = dataInfo.size[dataInfo.curDim] - 1
            except:
                pass
    if (fmode):
        op = Extract(fstart,fend)
    else:
        op = Extract(start,end)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)

def TRIM(ftrim=0.1, disabled=False, vector=None, process=None):
    '''Trim a fraction of vector from each end.
    Parameters
    ---------
    ftrim : real
        amin : 0
        min : 0
        max : 0.4
        amax : 0.4
        Fraction of size to trim on each side
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    fmode = True
    fstart = ftrim
    fend = 1.0-ftrim
    op = Extract(fstart,fend)

    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)

def DCFID(fraction=0.06, disabled=False, vector=None, process=None):
    ''' Correct DC offset of FID real and imaginary channels 
    Parameters
    ---------
    fraction : real
        amin : 0.01
        min : 0.01
        max : 0.25
        amax : 0.35
        Fraction of end of FID to average to calculate offset
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Dcfid(fraction)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

@generic_operation
def DX(disabled=False, vector=None, process=None):
    '''Numerical Derivative.
    '''
    op = Dx()
    return op

def SUPPRESS(winSize=31, shift='0.0f',disabled=False, vector=None, process=None):
    ''' Time domain signal suppression.
    Parameters
    ---------
    winSize : int
        min : 1
        max : 128
        Window size of moving average filter (+/- this value).
    shift : position
        min : -0.5
        max : 0.5
        Position of frequency to suppress.  Default is in fractional units with zero at center..
    '''
    if disabled:
        return None
    nPasses = 3
    process = process or getCurrentProcess()
    shiftObj = convertUnitStringToObject(shift)
    op = Tdss(winSize,nPasses,shiftObj)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op


def TDSS(winSize=31, nPasses=3, shift='0.0f',disabled=False, vector=None, process=None):
    ''' Time domain solvent suppression.
    Parameters
    ---------
    winSize : int
        min : 1
        max : 128
        Window size of moving average filter (+/- this value).
    nPasses : int
        min : 1
        max : 3
        Number of passes of filter.  Three is optimal.
    shift : position
        min : -0.5
        max : 0.5
        Position of frequency to suppress.  Default is in fractional units with zero at center..
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()
    shiftObj = convertUnitStringToObject(shift)
    op = Tdss(winSize,nPasses,shiftObj)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def EXPD(lb=1.0, fPoint=1.0, inverse=False, disabled=False, vector=None, process=None):
    '''Exponential Decay Apodization.
    Parameters
    ---------
    lb : real
        amin : -20.0
        min : 0.0
        max : 20.0
        Line broadening factor.
    fPoint : real
        amin : 0.0
        min : 0.5
        max : 1.0
        First point multiplication.
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Expd(lb, fPoint, inverse)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def BZ(alg='ph', phase=0.0, scale=1.0, pt2=0.0, delay=None, disabled=False, vector=None, process=None):
    '''Zero Bruker DSP baseline and associated algorithms: <i>sim, ph, dspph, chop</i>.
    Parameters
    ---------
    alg : {'ph','sim', 'dspph', 'chop'}
        Algorithm to correct Bruker DSP artifact.
    phase : real
        min : -180
        max : 180
        Phase adjust (sim, ph only).
    scale : real
        min : -1
        max : 3
        Scale factor (sim only).
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()
    global fidInfo
    global dataInfo
    curDim = dataInfo.curDim
    if (delay == None):
        delay = 67.984    # default group delay
        try:
            delay = p('GRPDLY,1')  # read from Bruker pars
        except:
            delay = 0.0
            pass
    op = Bz(alg, delay, scale, phase, pt2)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def FDSS(center='0.0f', start='0.005f', end='0.015f', autoCenter=False, disabled=False, vector=None, process=None):
    '''Frequency Domain Solvent Suppression.
    Parameters
    ---------
    center : position
        amin : -0.5
        min : -0.5
        max : 0.5
        amax : 0.5
        Position of frequency to suppress.  Default is in fractional units with zero at center..
    start : position
        amin : 0.00
        min : 0.00
        max : 0.010
        amax : 0.1
        The beginning of the peak.
    end : position
        amin : 0.000
        min : 0.00
        max : 0.02
        amax : 0.15
        The end of the peak.
    autoCenter : bool
        Find the largest peak in spectrum and center on that.
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    shiftObj = convertUnitStringToObject(center)
    startObj = convertUnitStringToObject(start)
    endObj = convertUnitStringToObject(end)
    op = Fdss(shiftObj, startObj, endObj, autoCenter)

    if (vector!=None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def FILTER(type='notch', offset=0, width=0.05, factor=4, groupFactor=8, mode='zero', ncoefs=None,  disabled=False, vector=None, process=None):
    '''Generic filter, type is <i>notch</i> or <i>lowpass</i>.
    Parameters
    ---------
    type : {'notch', 'lowpass'}
        Filter type.
    offset : real
        min : -0.5
        max : 0.5
        Frequency offset in fraction of sw.
    width : real
        min : 0.01
        max : 0.09
        Notch width in fraction of sw (notch only).
    factor : int
        min : 3
        max : 20
        Decimation factor (lowpass only).
    groupFactor : real
        min : 4
        max : 40
        Filter sharpness.
    mode : {'zero', 'reflect','profile'}
        Filter type.
    '''
    if disabled:
        return None
#   typical usage: type='notch' width=0.05 or type='lowpass' factor=4
    process = process or getCurrentProcess()
    global dataInfo
    curDim = dataInfo.curDim
    if (type == 'notch' or type == 'n'):
        nc = 0.5 * groupFactor / width + 0.2
        nc = (int(nc)/2) * 2 + 1    # ensure odd integer
        ncoefs = ncoefs or nc
        ncoefs = int(ncoefs)    # groupDelay is ncoefs/2, not groupFactor
        op = FFilter(type, mode, 1.0-width, ncoefs, offset)
    else:  # type = 'lowpass'
        factor = int(factor)
        nc = 2 * groupFactor * factor + 1
        nc = (int(nc)/2) * 2 + 1    # ensure odd integer
        ncoefs = ncoefs or nc
        ncoefs = int(ncoefs)    # groupDelay is ncoefs/2, groupFactor
        op = FFilter(type, mode, factor, ncoefs, offset)

    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def FT(negateImag=False, negatePairs=False, auto=False, disabled=False, vector=None, process=None):
    '''Fourier Transform.
    Parameters
    ---------
    negateImag : bool
        Negate imaginary values before the FT
    negatePairs : bool
        Negate alternate complex real/imaginary values before the FT
    auto : bool
        Determine negatePairs from FID parameters
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()
    global dataInfo
    global fidInfo

    if (auto == True):
        negatePairs = fidInfo.negatePairsFT(dataInfo.curDim)
        negateImag = fidInfo.negateImagFT(dataInfo.curDim)

    op = Ft(negateImag, negatePairs)
    if (vector != None):
        if (not vector.isComplex()):
            raise Exception("Cannot perform FT: vector not complex")
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def FT2D(process=None):
    process = process or getCurrentProcess()
    op = Ft2d()
    process.addOperation(op)
    return op

def IFT2D(process=None):
    process = process or getCurrentProcess()
    op = Ift2d()
    process.addOperation(op)
    return op

@generic_operation
def RANDN(mean=0.0, stdev=1.0, seed=0, disabled=False, vector=None, process=None):
    '''Add a Gaussian to a vector.
    Parameters
    ---------
    mean : double
        min : 0.0
        max : 100.0
        Mean of the Gaussian.
    stdev : double
        amin : 0.0
        min : 0.1
        max : 100.0
        Standard deviation of the Gaussian.
    seed : int
        min : 0
        Seed for the RNG.
'''
    if disabled:
        return None
    op = RandN(mean, stdev, seed)
    return op

@generic_operation
def GAPSMOOTH(center=-1, start=-1, end=-1, autoCenter=False, disabled=False, vector=None, process=None):
    '''Solvent suppression by removing signal and filling the gap with a smoothing function.
    Parameters
    ---------
    center : int
        Center point of the solvent peak.
    start : int
        Beginning point of the solvent peak.
    end : int
        End point of the solvent peak.
    autoCenter : bool
        Find largest peak in spectrum and set that as center
'''
    if disabled:
        return None
    op = GapSmooth(center, start, end, autoCenter)
    return op

def GF(gf=1.0, gfs=1.0, fPoint=1.0, inverse=False, disabled=False, vector=None, process=None):
    '''Lorentz-to-Gauss.
    Parameters
    ---------
    gf : double
        amin : 0.0
        min : 0.0
        max : 20.0
        gf: Gaussian broadening 
    gfs : double
        amin : 0.0
        min : 0.0
        max : 1.0
        gfs: Gaussian center
    fPoint : double
        amin : 0.0
        min : 0.0
        max : 1.0
        amax : 5.0
        fpoint: First point multiplier
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Gf(gf, gfs, fPoint, inverse)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def GM(g1=1.0, g2=1.0, g3=0.0, fPoint=1.0, inverse=False, disabled=False, vector=None, process=None):
    '''Lorentz-to-Gauss.
    Parameters
    ---------
    g1 : double
        amin : 0.0
        min : 0.0
        max : 20.0
        g1: Exponential line narrowing
    g2 : double
        amin : 0.0
        min : 0.0
        max : 20.0
        g2: Gaussian broadening
    g3 : double
        amin : 0.0
        min : 0.0
        max : 1.0
        amax : 1.0
        g3: Gaussian center
    fPoint : double
        amin : 0.0
        min : 0.0
        max : 1.0
        amax : 5.0
        fpoint: First point multiplier 
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Gm(g1, g2, g3, fPoint, inverse)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def GMB(gb=0.0, lb=0.0, fPoint=1.0, inverse=False, disabled=False, vector=None, process=None):
    '''Gauss Broaden Window.
    Parameters
    ---------
    gb : real
        amin : 0.0
        min : 0.0
        max : 1.0
        amax : 1.0
        Gaussian Broadening Coefficient.
    lb : real
        min : -20.0
        max : 20.0
        Line broadening.
    fPoint : real
        amin : 0.0
        min : 0.0
        max : 1.0
        Factor multiplied with the first point.
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Gmb(gb, lb, fPoint, inverse)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op
    
def APODIZE(lbOn=False,lb=0.5, gmOn=False, gm=1.0, sbOn=False, sbSqOn=False, sbOffset=0.5, fPoint=1.0, apodSize=0, inverse=False, disabled=False, vector=None, process=None):
    '''Lorentz-to-Gauss.
    Parameters
    ---------
    lbOn : bool
        Use exponential line broadening.
    lb : real
        amin : -2.0
        min : -2.0
        max : 5.0
        amax : 100.0
        Line broadening factor.
    gmOn : bool
        Use gaussian line broadening.
    gm : double
        amin : 0.0
        min : 0.0
        max : 20.0
        g2: Gaussian broadening
    sbOn : bool
        Use sine-bell multiplication.
    sbSqOn : bool
        Use sine-bell squared multiplication.
    sbOffset : real
        amin : 0.0
        min : 0.0
        max : 0.5
        amax : 0.5
        Offset of sine window.
    fPoint : double
        amin : 0.0
        min : 0.0
        max : 1.0
        amax : 5.0
        fpoint: First point multiplier
    apodSize : int
        min : 0
        max : size
        Size of apodization window.  Default 0f 0 uses entire FID.
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = BasicApodization(lbOn, lb, gmOn, gm, sbOn, sbSqOn, sbOffset, fPoint, apodSize, inverse)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op


def HFT(disabled=False, vector=None, process=None):
    '''Hilbert Transform
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Hft()
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def IFT(disabled=False, vector=None, process=None):
    '''Inverse Fourier Transform'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Ift()
    if (vector != None):
        if (not vector.isComplex()):
            raise Exception("Cannot perform IFT: vector not complex")
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

@generic_operation
def IMAG(disabled=False, vector=None, process=None):
    '''Set the real values equal to the imaginary values and discard the rest.'''
    op = Imag()
    return op

@generic_operation
def INTEGRATE(first=0, last=-1, disabled=False, vector=None, process=None):
    '''Set the signal equal to its integral.
    int : first
        First point of integration region
    int : last
        Last point of integration region
'''
    if disabled:
        return None
    op = Integrate(first, last)
    return op

def SAMPLE_SCHEDULE(filename="/tmp/sample_schedule.txt", mode='read', dims=[], demo=False, fraction=0.25):
    '''Read or write a sample schedule from/to a file
    filename : string
        Name of file.
    mode : {'read','create'}
        Whether to read or write a schedule.
    dims : int
        integer array of time domain data sizes after NUS processing, e.g. [40, 24].
    demo : bool
        Demonstration mode (False if non-uniformly sampled, True if regular fid).
    fraction : real
        Fraction of total points that are sampled (create mode only).
    '''
    
    global fidInfo
    fidObj = fidInfo.fidObj
    if (mode == 'create'):      # for 2D NUS
        size = fidInfo.size[1]  # too small unless demo
        if (len(dims) > 0):
            size = dims[0]
        schedule = fidObj.createSampleSchedule(size, fraction, formatStringForJava(filename), demo, fidObj)
    else:   # mode='read'
        schedule = fidObj.readSampleSchedule(formatStringForJava(filename), demo, fidObj)
    if (len(dims) > 0):
        schedule.setDims(dims)

def ISTMATRIX(threshold=0.90, iterations=500, alg='std', phase=None, timeDomain=True,  disabled=False, process=None):
    '''Iterative Soft Threshold for 2D Matrix.
    Parameters
    ---------
    threshold : real
        amin : 0.1
        min : 0.1
        max : 1.0
        amax: 1.0
        Values above this threshold (multiplied times largest peak) are transfered to IST add buffer.
    iterations : int
        min : 1
        max : 1000
        Number of iterations to perform.
    alg : {'std','abs','phased','phasedpos'}
        Name of algorithm to use.
    phase : []
        Array of phase values, 2 per indirect dimension.
    '''
    if disabled:
        return None
    phaseList = ArrayList()
    if phase == None:
        pass
    else:
        for value in phase:
            phaseList.add(float(value))
    process = process or getCurrentProcess()
    global fidInfo
    if fidInfo == None or fidInfo.fidObj == None:
        schedule = None
    else:
        schedule = fidInfo.fidObj.getSampleSchedule()
    if (len(phaseList) > 0):
        op = IstMatrix(threshold, iterations, schedule, alg, timeDomain, phaseList)
    else:
        op = IstMatrix(threshold, iterations, schedule, alg, timeDomain)
    process.addOperation(op)
    return op

def IST(threshold=0.98, iterations=500, alg='std', timeDomain=True, ph0=None, ph1=None, 
    adjustThreshold=False, all=False, disabled=False, vector=None, process=None):
    '''Iterative Soft Threshold.
    Parameters
    ---------
    threshold : real
        amin : 0.1
        min : 0.89
        max : 0.99
        amax: 0.99
        Values above this threshold (multiplied times largest peak) are transfered to IST add buffer.
    iterations : int
        min : 1
        max : 2000
        Number of iterations to perform.
    alg : {'std','abs','phased','phasedpos'}
        Name of algorithm to use.
    timeDomain : bool
        Is the end result of the operation in time domain
    ph0 : real
        min : -360.0
        max : 360.0
        Apply this zero order phase correction to data before IST.
    ph1 : real
        min : -360.0
        max : 360.0
        Apply this first order phase correction to data before IST.
    adjustThreshold : bool
        Adjust threshold during IST calculation
    all : bool
        Replace all values in FID (including actually sampled)
'''
    if disabled:
        return None
    zeroFill = True
    process = process or getCurrentProcess()
    global fidInfo
    global dataInfo
    if fidInfo == None or fidInfo.fidObj == None:
        schedule = None
    else:
        schedule = fidInfo.fidObj.getSampleSchedule()

#    gph0, gph1 = getPhases(dataInfo.curDim)
#    if (ph0 == None):
#        ph0 = gph0
#    if (ph1 == None):
#        ph1 = gph1

# if nDim == 2 do op, otherwise do IST command (3d, 4d)
    if (ph0 != None or ph1 != None):
        if (ph1 == None):
            ph1 = 0.0
        if (ph0 == None):
            ph1 = 0.0
        op = IstVec(threshold, iterations, schedule, alg, timeDomain, zeroFill, all, adjustThreshold, ph0, ph1)
    else:
        op = IstVec(threshold, iterations, schedule, alg, timeDomain, zeroFill, all, adjustThreshold)
# eventually add ter, alternate to iterations
    if (vector != None):
        if (not vector.isComplex()):
            raise Exception("Cannot perform IST: vector not complex")
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def EXTEND(alg='nesta', factor=1, phase=None, disabled=False, vector=None, process=None):
    ''' Experimental implementation of NESTA algorithm for extending data as alternative to Linear Prediction.
    This version requires that the data be in-phase.  Use the phase argument to provide a list of phase values.
  
    Parameters
    ---------
    alg : {'nesta','grins'}
        Name of algorithm to use.
    factor : int
        amin : 0
        min : 0
        max : 2
        amax : 2
        Zero fill factor
    phase : []
        Array of phase values, 2 per indirect dimension.

    '''
    nOuter=15
    nInner=20
    tolFinal=2.5
    muFinal=6
    phase=None
    logToFile=False
    zeroAtStart=True
    threshold=0.0
    nGrins=128
    shapeFactor = 0.5

    noise = 0.0
    phase = None
    scale = 0.5
    preserve = False
    synthetic = False

    if disabled:
        return None
    phaseList = ArrayList()
    if phase == None:
        pass
    else:
        for value in phase:
            phaseList.add(float(value))
    tolFinalReal = math.pow(10.0,-tolFinal)
    muFinalReal = math.pow(10.0,-muFinal)
    process = process or getCurrentProcess()
    global fidInfo
    if fidInfo == None or fidInfo.fidObj == None:
        skipIndices = None
    else:
        skipIndices = fidInfo.fidObj.getSkipIndices()


    if alg == 'nesta':
        op = NESTANMR(nOuter, nInner, tolFinalReal, muFinalReal, phaseList, zeroAtStart, threshold, factor, skipIndices)
    elif alg == 'grins':
        op = GRINSOp(noise, scale, factor, nGrins, shapeFactor,  False, phaseList, preserve, skipIndices)
    else:
        raise Exception("Invalid algorithm for EXTEND: " + alg)

    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)

    return op


def NESTA(nOuter=15, nInner=20, tolFinal=2.5, muFinal=6,phase=None, logToFile=False, zeroAtStart=True, threshold=0.0, disabled=False, vector=None, process=None):
    ''' Experimental implementation of NESTA algorithm for NUS processing.  This version
    requires that the data be in-phase.  Use the phase argument to provide a list of phase values.
   
    Parameters
    ---------
    nOuter : int
        min : 1
        max : 100
        Number of outer iterations (continuations) to perform.
    nInner : int
        min : 1
        max : 100
        Number of inner iterations to perform.
    tolFinal : real
        amin : 0
        min : 0
        max : 10
        amax : 10
        Final tolerance for inner iterations is 10 raised to the negative of this number.  For example, 5 gives 1.0e-5.
    muFinal : real
        amin : -2
        min : -2
        max : 9
        Final mu value is 10 raised to the negative of this number.  For example, 5 gives 1.0e-5.
    phase : []
        Array of phase values, 2 per indirect dimension.
    logToFile : bool
        Write log files containing information about progress of NESTA.
    zeroAtStart : bool
        Set unsampled values to zero at start of operation
    threshold : real
        min : 0
        Threshold for absolute value.  If less than  this skip this hyperplane.
    '''
    if disabled:
        return None
    phaseList = ArrayList()
    if phase == None:
        pass
    else:
        for value in phase:
            phaseList.add(float(value))
    tolFinalReal = math.pow(10.0,-tolFinal)
    muFinalReal = math.pow(10.0,-muFinal)
    process = process or getCurrentProcess()
    global fidInfo
    logFileName = None
    if fidInfo == None or fidInfo.fidObj == None:
        schedule = None
    else:
        schedule = fidInfo.fidObj.getSampleSchedule()
        if logToFile:
            rootdir = fidInfo.fidObj.getFilePath()
            logDir = os.path.join(rootdir,"nesta")
            if not os.path.exists(logDir):
                os.mkdir(logDir)
            logFileName = os.path.join(logDir,"log")

    op = NESTANMR(nOuter, nInner, tolFinalReal, muFinalReal, schedule, phaseList, zeroAtStart, threshold, logFileName)

    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)

    return op

def NESTA_EX_SCR(iterations=30, execName='', disabled=False, vector=None, process=None):
    '''NUS Processing with external NESTANMR program.
    Parameters
    ---------
    iterations : int
        min : 1
        max : 2000
        Number of iterations to perform.
    execName : string 
        Full path to NESTANMR executable.
'''
    global nestaExecutable
    if disabled:
        return None
    if execName != '':
        nestaExecutable = execName
    initialScript = 'import os;import subprocess;import pyproc'
    script='pyproc.execNESTA(vecmat,'+str(iterations)+')'

    process = process or getCurrentProcess()

    op=PythonScript(script, initialScript, False)

    if (vector != None):
        op.eval(vector)
    else:
        process.add(op)
    return op

def NESTA_L1_EXT(iter=30, rwiter=1, rootdir='', nestdir='nestaL1', schedFile='', phase=None, disabled=False, vector=None, process=None):
    '''NUS Processing with external NESTANMR program.
    Parameters
    ---------
    iter : int
        min : 1
        max : 200
        Number of iterations to perform.
    rwiter : int
        min : 1
        max : 20
        Number of re-weighted iterations to perform.
    rootdir : string
        Root directory for NESTA working files.  If empty, defaults to directory of FID.
    nestdir : string
        Sub- directory for NESTA working files.
    schedFile : string
        Schedule file. If empty, it defaults to value stored in FID file object.
    phase : []
        Array of phase values, 2 per indirect dimension.
'''
    if disabled:
        return None

    global fidInfo
    if schedFile == '':
        if fidInfo == None or fidInfo.fidObj == None:
            schedFile = None
        else:
            schedFile = fidInfo.fidObj.getSampleSchedule().getFile()

    phaseList = ArrayList()
    if phase == None:
        pass
    else:
        for value in phase:
            phaseList.add(float(value))

    process = process or getCurrentProcess()
    if rootdir == '':
        if fidInfo == None or fidInfo.fidObj == None:
            rootdir = "."
        else:
            rootdir = fidInfo.fidObj.getFilePath()

    if not os.path.exists(rootdir):
        raise Exception('Directory "'+rootdir+'" does not exist')

    nestDir = os.path.join(rootdir,nestdir)
    if not os.path.exists(nestDir):
        os.mkdir(nestDir)

    op=NESTANMREx(iter,rwiter,nestDir,schedFile, phaseList)

    if (vector != None):
        op.eval(vector)
    else:
        process.add(op)
    return op


def NESTA_L0_EXT(iter=5000, scaling=0.98, cutoff=0.1, rootdir='', nestdir='nestaL0', schedFile='', phase=None, disabled=False, vector=None, process=None):
    '''NUS Processing with external NESTANMR program.
    Parameters
    ---------
    iter : int
        amin : 1
        min : 1
        max : 6000
        Number of iterations to perform.
    scaling : real
        amin : 0.94
        min : 0.94
        max : 0.99
        amax : 0.99
        Scaling of threshold at each iteration.
    cutoff : real
        amin : 0.0
        min : 0.1
        max : 0.5
        Stop iterations when threshold is at this value
    rootdir : string
        Root directory for NESTA working files.  If empty, defaults to directory of FID.
    nestdir : string
        Sub- directory for NESTA working files.
    schedFile : string
        Schedule file. If empty, it defaults to value stored in FID file object.
    phase : []
        Array of phase values, 2 per indirect dimension.
'''
    if disabled:
        return None

    global fidInfo
    if schedFile == '':
        if fidInfo == None or fidInfo.fidObj == None:
            schedFile = None
        else:
            schedFile = fidInfo.fidObj.getSampleSchedule().getFile()

    phaseList = ArrayList()
    if phase == None:
        pass
    else:
        for value in phase:
            phaseList.add(float(value))

    process = process or getCurrentProcess()
    if rootdir == '':
        rootdir = fidInfo.fidObj.getFilePath()

    if not os.path.exists(rootdir):
        raise Exception('Directory "'+rootdir+'" does not exist')

    nestDir = os.path.join(rootdir,nestdir)
    if not os.path.exists(nestDir):
        os.mkdir(nestDir)

    op=NESTANMREx(iter,scaling,cutoff,nestDir,schedFile, phaseList)

    if (vector != None):
        op.eval(vector)
    else:
        process.add(op)
    return op



def MAG(disabled=False, vector=None, process=None):
    '''Magnitude Calculation of a Vector. Each point is updated with its Complex magnitude.
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Mag()
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def EXP(disabled=False, vector=None, process=None):
    '''Exponential Calculation of a Vector. Each point is updated with the exponential value of the point .
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Exp()
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def POWER(disabled=False, vector=None, process=None):
    '''Power Calculation of a Vector. Each point is updated with its power value.
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Power()
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def SQRT(disabled=False, vector=None, process=None):
    '''Sqrt Calculation of a Vector. Each point is updated with its square root.
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Sqrt()
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op


@generic_operation
def MULT(value=1.0+0j, first = 0, last = -1, disabled=False, vector=None, process=None):
    '''Multiply the points in a vector by a Real or Complex number.
    Parameters
    ---------
    value : complex
        Number to multiply the points by.
    first : int
        min : 0
        max : size - 1
        Points starting from this will be multiplied by value.  Default is 0.
    last : int
        min : -1
        max : size - 1
        Last point to multiply the data by.  Default is the end of the vector.
'''
    if disabled:
        return None
    #last = vector.getSize() - 1 if vector != None and last == -1
    value = complex(value)
    op = Mult(value.real, value.imag, first, last)
    return op

@generic_operation
def ONES(disabled=False, vector=None, process=None):
    '''Set all points in a vector to 1.0'''
    op = Ones()
    return op

@generic_operation
def GEN(freq=100.0,lw=1.0,amp=50.0,phase=0.0, disabled=False, vector=None, process=None):
    '''Generate a simulated signal and add it to the vector.
    Parameters
    ---------
    freq : real
        min : -500
        max : 500.0
        Frequency in Hz.
    lw : real
        amin : 0
        min : 0
        max : 10.0
        Linewidth in Hz.
    amp : real
        amin : 0
        min : 0
        max : 100.0
        Amplitude of signal.
    phase : real
        min : -180 
        max : 180.0
        Phase of signal in degrees.
'''
    if disabled:
        return None
    op = Gen(freq,lw,amp,phase)
    return op


def PRINT(disabled=False, vector=None, process=None):
    '''Print vector.'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = IO()
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def WRITE(index=-1, dimag=True, disabled=False, vector=None, process=None):
    '''Write vector to dataset (normally done automatically).
    Parameters
    ---------
    index : int
        Index of vector to write.  A value of -1 uses index stored in vector.
    dimag : bool
        Discard imaginary values (make vector real).
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = WriteVector(dimag, index)
    if (vector != None):
        if (vector.pt != None):
            op.eval(vector)
    else:
        process.addOperation(op)
    return op

def AUTOPHASE(firstOrder=False, maxMode=False, winSize=2, ratio=25.0, mode='flat', ph1Limit=90.0, negativePenalty=1.0, disabled=False, vector=None, process=None):
    '''Auto Phase shift.
    Parameters
    ---------
    firstOrder : bool
        Do first order phase correction.
    maxMode : bool
        Autophase by maximizing positive signal.
    winSize : int
        amin : 1
        min : 1
        max : 32
        Size of each half of window used in doing CWTD.  Full window is 2 x this value.
    ratio : real
        amin : 1.0
        min : 1.0
        max : 100.0
        Ratio relative to noise used in determining if region is signal or baseline.
    mode : {'flat','entropy','leastneg'}
        Name of algorithm to use.
    ph1Limit : real
        amin : 0.0
        min : 1.0
        max : 100.0
        amax :540.0
        Limit ph1 value so its absolute value is less than this range.
    negativePenalty : real
        amin : 0.01
        min : 0.1
        max : 100.0
        amax : 200.0
        How much to weight to use in penalizing negative values in entropy mode (actual value is multiplied by 1.0e-5).
'''
    if disabled:
        return None

    process = process or getCurrentProcess()
    imode = 0
    if mode == 'entropy':
        imode = 1
    elif mode == 'leastneg':
        imode = 2

    op = AutoPhase(firstOrder, maxMode, winSize, ratio, imode, ph1Limit, negativePenalty)

    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op
def DPHASE(dim=0,firstOrder=False, winSize=2, ratio=25.0, ph1Limit=90.0, disabled=False, dataset=None, process=None):
    '''Auto Phase shift.
    Parameters
    ---------
    dim : {0,1,2,3,4,5,6}
        Dataset dimension to phase. (0 does all dimensions)
    firstOrder : bool
        Do first order phase correction.
    winSize : int
        amin : 1
        min : 1
        max : 32
        Size of each half of window used in doing CWTD.  Full window is 2 x this value.
    ratio : real
        amin : 1.0
        min : 1.0
        max : 100.0
        Ratio relative to noise used in determining if region is signal or baseline.
    ph1Limit : real
        amin : 0.0
        min : 1.0
        max : 100.0
        amax :540.0
        Limit ph1 value so its absolute value is less than this range.
'''
    if disabled:
        return None

    process = process or getCurrentProcess()
    dim -= 1

    op = AutoPhaseDataset(dim, firstOrder, winSize, ratio, ph1Limit)

    if (dataset != None):
        op.eval(dataset)
    else:
        process.addOperation(op)
    return op


def DEPT( disabled=False, dataset=None, process=None):
    ''' DEPT : combine rows.
    Parameters
    ---------
'''
    if disabled:
        return None

    process = process or getCurrentProcess()

    op = Dept()

    if (dataset != None):
        op.eval(dataset)
    else:
        process.addOperation(op)
    return op

def DGRINS(noise=5, logToFile=False, disabled=False, dataset=None, process=None):
    ''' Experimental GRINS.
    Parameters
    ---------
    noise : real
        amin : 0.0
        Noise estimate
'''
    if disabled:
        return None

    global fidInfo

    if fidInfo == None or fidInfo.fidObj == None:
        schedule = None
    else:
        schedule = fidInfo.fidObj.getSampleSchedule()
        if logToFile:
            rootdir = fidInfo.fidObj.getFilePath()
            logDir = os.path.join(rootdir,"nesta")
            if not os.path.exists(logDir):
                os.mkdir(logDir)
            logFileName = os.path.join(logDir,"log")

    process = process or getCurrentProcess()

    op = DGRINSOp(schedule, noise)

    if (dataset != None):
        op.eval(dataset)
    else:
        process.addOperation(op)
    return op


def GRINS(noiseRatio=5.0, scale=0.25, zf=0, iterations=64, shapeFactor=0.5, apodize=True, phase=None, preserve=True, synthetic=False, logToFile=False, disabled=False, dataset=None, process=None):
    ''' Experimental GRINS.
    Parameters
    ---------
    noiseRatio : real
        amin : 2.0
        min : 2.0
        max : 10.0
        Threshold calculated from noise * noiseRatio 
    scale : real
        amin : 0.1
        min : 0.2
        max : 2.0
        amax : 10.0
        Parabola to Lorentzian scale 
    zf : int
        amin : 0
        min : 0
        max : 2
        amax : 2
        Zero fill factor 
    iterations : int
        amin : 1
        min : 1
        max : 512
        amax : 512
        Iterations 
    shapeFactor : real
        amin : 0.0
        min : 0.0
        max : 1.0
        amax : 1.0
        Lineshape factor 
    apodize : bool
        Do Kaiser apodization during GRINS
    phase : []
        Array of phase values, 2 per indirect dimension.
    preserve : bool
        Add fitted signals to the residual signal (rather than replacing it)
    synthetic : bool
        Replace measured values with synthetic values. 
    logToFile : bool
        Write log files containing information about progress of NESTA.
'''
    if disabled:
        return None

    global fidInfo

    phaseList = ArrayList()
    if phase == None:
        pass
    else:
        for value in phase:
            phaseList.add(float(value))

    logFileName = None

    if fidInfo == None or fidInfo.fidObj == None:
        schedule = None
    else:
        schedule = fidInfo.fidObj.getSampleSchedule()
        if logToFile:
            rootdir = fidInfo.fidObj.getFilePath()
            if not os.path.isdir(rootdir):
                rootdir = os.path.dirname(rootdir)
            logDir = os.path.join(rootdir,"grins_log")
            if not os.path.exists(logDir):
                os.mkdir(logDir)
            logFileName = os.path.join(logDir,"log")

    process = process or getCurrentProcess()

    op = GRINSOp(noiseRatio, scale, zf, iterations, shapeFactor, apodize, phaseList, preserve, synthetic, schedule, logFileName)

    if (dataset != None):
        op.eval(dataset)
    else:
        process.addOperation(op)
    return op


def PHASE(ph0=0.0, ph1=0.0, dimag=False, disabled=False, vector=None, process=None):
    '''Phase shift.
    Parameters
    ---------
    ph0 : real
        min : -90.0
        max : 90.0
        slider : 0
        Zero order phase value
    ph1 : real
        min : -180.0
        max : 180.0
        slider : 0
        First order phase value
    dimag : bool
        Discard imaginary values
'''
    if disabled:
        return None
    global fidInfo
    process = process or getCurrentProcess()
    if (ph0 == None):
        
        try:
            gph0, gph1 = fidInfo.getPhases(dataInfo.curDim)
        except:
            gph0=0.0
        ph0 = gph0

    if (ph1 == None):
        try:
            gph0, gph1 = fidInfo.getPhases(dataInfo.curDim)
        except:
            gph1=0.0;
        ph1 = gph1
    op = Phase(ph0, ph1, dimag)

    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def PHASE2D(phase=None, disabled=False, process=None):
    '''Phase ND matrix.
    Parameters
    ---------
    phase : []
        Array of phase values, 2 per indirect dimension.
'''
    if disabled:
        return None

    process = process or getCurrentProcess()
    op = Phase2d(phase)
    process.addOperation(op)
    return op

def PHASEND(ph0=0.0,ph1=0.0,dim=0, disabled=False, process=None):
    '''Phase ND matrix.
    Parameters
    ---------
    ph0 : real
        min : -360.0
        max : 360.0
        Zero order phase value
    ph1 : real
        min : -360.0
        max : 360.0
        First order phase value
    dim : {0,1,2,3,4,5,6}
        Dataset dimension to phase. (0 does all dimensions)
'''
    if disabled:
        return None
    dim -= 1
    process = process or getCurrentProcess()
    op = Phase2d(ph0, ph1, dim)
    process.addOperation(op)
    return op


@generic_operation
def RAND(disabled=False, vector=None, process=None):
    '''Set all points in a vector to a uniformly distributed random number between 0.0 and 1.0.'''
    if disabled:
        return None
    op = Rand()
    return op
    

@generic_operation
def RANGE(value=0 + 0j, first=0, last=-1,  max=False, min=False, disabled=False, process=None, vector=None):
    '''Sets the values in the vector from first to last inclusive to either the specified value (which can be real or complex (written as 1.0 + 3j) or Double Min or Double Max.
    Parameters
    ---------
    value : complex
        Vector will have this value from the 'first' to 'last' elements
    first : int
        min : 0
        max : size-1 
        The first point of the vector to set.
    last : int
        min : -1
        max : size-1
        The last point of the vector to set.
    max : bool
        Set the value to Double.MAX (instead of min or value).  If True, overrides value.
    min : bool
        Set the value to Double.MIN (instead of max or value).  If True, overrides value.
    '''
    if disabled:
        return None
    if min:
        value = Double.MIN_VALUE
    if max:
        value = Double.MAX_VALUE

    op = Range(first, last, value.real, value.imag)
    return op

def MERGE(disabled=False, process=None, vector=None):
    '''Make the vector complex, by merging alternate values into a complex number'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Merge()
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op
    
def REAL(disabled=False, process=None, vector=None):
    '''Make the vector real, discarding the imaginary part'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Real()
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def EA(disabled=False, process=None, vector=None):
    '''Do echo-anti echo combination'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = EACombine()
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

@generic_operation
def ESMOOTH(winSize=256, lambd=5000, order=2, baseline=False, disabled=False, process=None, vector=None):
    '''Envelope smoothing.
    Parameters
    ---------
    winSize : int
        Size of the window
    lambd : real
        amin : 10.0
        min : 1000.0
        max : 50000.0
        Parameter controlling how close the fit to the baseline should be
    order : int
        min : 1
        max : 2
        Parameter controlling the order of the baseline fit
    baseline : bool
        If true, return the calculated baseline, rather than the corrected vector
'''
    if disabled:
        return None

    op = ESmooth(winSize, lambd, order, baseline)
    return op

def REVERSE(disabled=False, process=None, vector=None):
    '''Reverse points in a vector'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Reverse()
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def RFT(inverse=False, negatePairs=False, negateOdd=False, disabled=False, process=None, vector=None):
    '''Real fourier transform
    Parameters
    ---------
    inverse : bool
        True if inverse RFT, False if forward RFT.
    negatePairs : bool
        Negate alternate pairs of values before the FT
    negateOdd : bool
        Negate odd (alternate) values before the FT
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Rft(inverse, negatePairs, negateOdd)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def SIGN(mode='i', disabled=False, process=None, vector=None):
    '''Change sign of values
    Parameters
    ---------
    mode : {'i','r','alt'}
        What elements of vector to change .
    '''
    if disabled:
        return None

    process = process or getCurrentProcess()
    op = Sign(mode)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def SB(offset=0.5, end=1.0, power=2.0, c=1.0, apodSize=0, dim=0, inverse=False, disabled=False, vector=None, process=None):
    '''Sine Bell Apodization
    Parameters
    ---------
    offset : real
        amin : 0.0
        min : 0.0
        max : 0.5
        Offset of sine window.
    end : real
        amin : 0.5
        min : 0.5
        max : 1.0
        amax : 1.0
        End value of sine window argument.
    power : real
        amin : 1.0
        min : 1.0
        max : 2.0
        amax : 2.0
        Exponential power.
    c : real
        amin : 0.5
        min : 0.5
        max : 1.0
        amax : 1.0
        First point multiplier.
    apodSize : int
        min : 0
        max : size
        Size of apodization window.  Default 0f 0 uses entire FID.
    dim : {0,1,2,3,4,5,6}
        Dataset dimension to apodize. Only applicable for matrix operations. 0:do all dimensions
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = SinebellApod(offset, end, power, c, apodSize, dim-1, inverse)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def BLACKMAN(offset=0.5,end=1.0,c=1.0,apodSize=0,dim=1, inverse=False,disabled=False, vector=None, process=None):
    '''Blackman Apodization
    Parameters
    ---------
    offset : real
        amin : 0.0
        min : 0.0
        max : 0.5
        Offset of Blackman window.
    end : real
        amin : 0.5
        min : 0.5
        max : 1.0
        amax : 1.0
        End value of Blackman window argument.
    c : real
        amin : 0.5
        min : 0.5
        max : 1.0
        amax : 1.0
        First point multiplier.
    apodSize : int
        min : 0
        max : size
        Size of apodization window.  Default 0f 0 uses entire FID.
    dim : {1,2,3,4,5,6}
        Dataset dimension to apodize. Only applicable for matrix operations.
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Blackman(offset, end, c, apodSize, dim-1, inverse)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def KAISER(offset=0.5, beta=10.0, end=1.0,c=1.0,apodSize=0, dim=0, inverse=False,disabled=False, vector=None, process=None):
    '''Kaiser Apodization
    Parameters
    ---------
    offset : real
        amin : 0.0
        min : 0.0
        max : 0.5
        Offset of Kaiser window.
    beta : real
        amin : 0.0
        min : 0.0
        max : 20.0
        amax : 20.0
        Beta.
    end : real
        amin : 0.5
        min : 0.5
        max : 1.0
        amax : 1.0
        End value of window 
    c : real
        amin : 0.5
        min : 0.5
        max : 1.0
        amax : 1.0
        First point multiplier.
    apodSize : int
        min : 0
        max : size
        Size of apodization window.  Default 0f 0 uses entire FID.
    dim : {0,1,2,3,4,5,6}
        Dataset dimension to apodize. Only applicable for matrix operations. 0:do all dimensions
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Kaiser(offset, beta, end, c, apodSize, dim-1, inverse)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

@generic_operation
def SHIFT(shift=0, adjref=False, disabled=False, vector=None, process=None):
    '''Left or right shift of the data points in the vector by the specified amount.
    Parameters
    ---------
    shift : int
        min : -2048
        max : 2048
        Amount of points to shift the vector by.
    adjref : bool
        If true, adjust the referencing of the vector based on shift
'''
    if disabled:
        return None

    op = Shift(shift, adjref)
    return op

def SCRIPT(script="", initialScript="", execFileName="", encapsulate=False, disabled=False, vector=None, process=None):
    '''Execute a Python script as an Operation. Current vector is available as object named "vec". 
    Parameters
    ---------
    script : wstring
        The script that will be run on each Vec at the stage in the processing queue.
    initialScript : wstring
        Any initial declarations that will be executed on initialization.
    execFileName : file
        An initial file that will be executed on initialization.
    encapsulate : bool
        Whether the interpreter should persist between evaluations or be reinitialized for each evaluation.
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op=PythonScript(script, formatStringForJava(initialScript), formatStringForJava(execFileName), encapsulate)
    if (vector != None):
        op.eval(vector)
    else:
        process.add(op)
    return op

@generic_operation
def TDPOLY(order=4, winSize=32, start=0, disabled=False, vector=None, process=None):
    '''Time Domain Polynomial.
    Parameters
    ---------
    order : int
        min : 1
        max : 10
        Order of the polynomial.
    winSize : int
        min : 1
        max : size-1
        Size of the window
    start : int
        min : 0
        max : size-1
        First point
'''
    if disabled:
        return None
    op = TDPoly(order, winSize, start)
    return op
    

def TM(pt1=0, pt2=-1, inverse=False, disabled=False, vector=None, process=None):
    '''Trapezoid Multiply.
    Parameters
    ---------
    pt1 : int
        min : 0
        max : size-1
        First point to multiply.
    pt2 : int
        min : -1
        max : size-1
        Last point to multiply.
'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    op = Tm(pt1, pt2, inverse)
    if vector != None:
        op.eval(vector)
    else:
        process.add(op)
    return op

@generic_operation
def TRI(pt1=0, lHeight=1.0, rHeight=0.0, inverse=False, disabled=False, vector=None, process=None):
    '''Triangle Window
    Parameters
    ---------
    pt1 : int
        min : 0
        max : size-1
        Middle point of the triangle.
    lHeight : real
        amin : 0.0
        min : 0.0
        max : 1.0
        amax : 1.0
        Height of the left side.
    rHeight : real
        amin : 0.0
        min : 0.0
        max : 1.0
        amax : 1.0
        Height of the right side.
'''
    if disabled:
        return None
    op = Tri(pt1, lHeight, rHeight, inverse)
    return op

def VECREF(size=8, sf=500.0, sw=5000.0,disabled=False, process=None, vector=None):
    '''Sets size, spectrometer frequency and sweep width of vector.  Used for simulated FIDs for testing and demonstration.
    Parameters
    ---------
    size : int
        min : 3
        max : 16
        Size of vector specified as a power of 2.
    sf : real
        amin : 0.0
        min : 0.0
        max : 1200.0
        amax : 1200.0
        Spectrometer frequency (in MHz).
    sw : real
        amin : 0.0
        min : 0.0
        max : 10000.0
        amax : 10000.0
        Sweep width of spectrum (in Hz).
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()
    global dataInfo
    curDim = dataInfo.curDim
    size = pow(2,size)

    op = VecRef(size,sf,sw)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def ZEROS(disabled=False, process=None, vector=None):
    '''Zeros a vector.'''
    if disabled:
        return None
    process = process or getCurrentProcess()
    global dataInfo
    curDim = dataInfo.curDim

    op = Zeros()
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def ZF(factor=1, size=-1, pad=-1, disabled=False, process=None, vector=None):
    '''Zero Fill. 
factor is the 'factor' power of 2 that the vector size is increased to, so if the vector has 513 elements and factor = 1, it will increase to 1024, the next power of 2, but if factor = 2, it will increase to 2048, which is two powers of two greater.
A size can be specified instead of a factor which will be the exact number of points the vector will have, and the increased elements will all be zero.
    Parameters
    ---------
    factor : int
        min : -1
        max : 4
        Number of powers of 2 to zero fill to.
    size : int
        min : -1
        max : 65536
        Size after zero filling.  If -1 (default), calculate from factor value.
    pad : int
        min : -1
        max : 128
        Increase size by this amount.  If -1 (default) use size or factor value.
    '''
    if disabled:
        return None
    process = process or getCurrentProcess()
    global dataInfo
    curDim = dataInfo.curDim

    op = Zf(factor,size,pad)
    if (vector != None):
        op.eval(vector)
    else:
        process.addOperation(op)
    return op

def makeDataNames(filePath,baseDir=None,outDir=None,iFile=None,baseName='data',multiMode=False):
   (dirName,tail) = os.path.split(filePath)
   (rootName,ext) = os.path.splitext(tail)
   print 'fp',filePath
   print 'ro',rootName
   print 'ex',ext
   if iFile:
       rootName =rootName+'_'+str(iFile)
   if baseName:
       rootName = baseName+"_"+rootName
   if multiMode:
       dataName = 'multi.nv'
   else:
       dataName = rootName+'.nv'
   print 'da',dataName
   if (baseDir):
       fullFidName = os.path.join(baseDir,filePath)
   else:
       fullFidName = filePath
   if (outDir):
       fullDataName = os.path.join(outDir,dataName)
   else:
       fullDataName = os.path.join(dirName,dataName)

   return fullFidName,filePath,fullDataName,dataName


def newvector(size=32):
    return Vec(size)

def addvector(vector,process=None):
    '''add the vector to the process'''
    process = process or getCurrentProcess()
    process.addVec(vector)

def copy(process=None):
    '''return a new ProcessOps instance that is a copy of process'''
    process = process or getCurrentProcess()
    temp = processor.createProcess()
    return process.cloneProcess(temp)

def copy(processFrom, processTo):
    '''Clone processFrom to processTo and return processTo.  Will modify
operations in processTo, but will not modify vectors.'''
    return processFrom.cloneProcess(processTo)

def create(name=None):
    if (name == None):
        return processor.createProcess()
    else:
        return processor.createProcess(name)

def defaultName():
    '''return the name of the default process'''
    return processor.getDefaultName()

def getDefault():
    '''return the default process'''
    return processor.getDefaultProcess()

def run(process=None):
    '''Execute the series of operations that have been added to processor. Return true if it executed successfully.
      The run command must be present at the end of processing operations or no processing will happen.'''
    if (dataInfo.resizeable):
        createDataset()
        setDataInfo(dataInfo.createdSize)
    else:
        setDataInfo(dataInfo.createdSize)
    if (process == None):
        processor.runProcesses()
    else:
        processor.run(process)

def list_ops(process=None):
    process = process or getCurrentProcess()
    return process.getOperationString()

def list_processes():
    return processor.getListOfProcessNames()

def list_vectors(process=None):
    process = process or getCurrentProcess()
    return process.getVectorString()

def status(process=None):
    '''Return status of a process''' 
    process = process or getCurrentProcess()
    return process.getStatus()

def procOpts(nprocess=None,nvectors=None):
    ''' Set and get various options in the Processor
    Parameters
    ---------
    nprocess : int
        The number of processes to run simultaneously.  Defaults to number of cpu cores (x2 with hyper-threaded)
    nvectors : int
        The number of vectors each process should grab at one time.
    '''
    if (nprocess != None):
        processor.setNumProcessors(nprocess)
    if (nvectors != None):
        processor.setVectorsPerProcess(nvectors)
    return {'nprocess':processor.getNumProcessors(),'nvectors':processor.getVectorsPerProcess()}

def writeVec(vector,fileName):
    f = open(fileName,'w')
    size = vector.getSize()
    for i in range(size):
        rx = vector.get(i,True)
        output = str(rx)
        if (vector.isComplex()):
            ix = vector.get(i,False)
            output += ' '+str(ix)
        f.write(output+'\n')
    f.close()

def execNESTA(vecMat,iterations):
    global fidInfo
    global nestaExecutable
    filePath = fidInfo.fidObj.getFilePath()
    nestDir = os.path.join(filePath,'nestaL1')
    rootName = os.path.join(nestDir,'test')
    vecFileName = vecMat.exportData(rootName,"nestain",True)
    # do external processing, for example
    nusListFile = fidInfo.fidObj.getSampleSchedule().getFile()
    fileIndex = str(vecMat.getIndex()+1)
    nestArgs = [nestaExecutable,"-q","-D","-b","-i",str(iterations),"-t","1","-n",nusListFile,"-d",nestDir,"-e",fileIndex,"-f",vecFileName]
    try:
        retcode = subprocess.call(nestArgs)
    except OSError as e:
        print "Execution failed:", e
        raise(e)
    except:
        e = sys.exc_info()[0]
        print "Execution failed:", e
        raise(e)

    if retcode < 0:
        raise("Child was terminated by signal " + retcode)

    vecMat.importData(rootName,"nestaout",True)
    os.remove(vecFileName)
    os.remove(vecFileName+'.par')

coefs3d = [1, 0, 1, 0, 0, 0, 0, 0,
           0,-1, 0, 1, 0, 0, 0, 0,
           0, 0, 0, 0, 1, 0, 1, 0,
           0, 0, 0, 0, 0,-1, 0, 1]

def convertUnitStringToObject(unitString):
    '''Return a Unit object (Fraction, Frequency, Index, PPM, Point, Time) from a string of the unit.  Proper format is a number, with optional decimal place, followed by a token.  f for Fraction, h for frequency (Hz), no token for index, P for PPM, p or no token for point, s for second.'''
    #token = unitString.strip(' \t')[-1]
    if isinstance(unitString,(float,int)):
        return unitString
    token = filter(lambda x: x != '', re.findall('[a-zA-z]*', unitString))
    if len(token) > 1:
        raise Exception("Poorly formatted Unit String.  Cannot convert %s.  Unit must be supplied as a number followed by f, h, p, or s." % (unitString))
    num = filter(lambda x: x != '', re.findall('[\d.\-]*', unitString))
    if len(num) != 1:
        raise Exception("Poorly formatted Unit String.  Cannot convert %s.  Unit must be supplied as a number followed by f, h, p, or s." % unitString)
    
    if (len(token)):
        token = token[0]
    else:
        token = unitString[-1]
    num = num[0]
    unit = None

    if token == 'f':
        unit = Fraction(num)
    
    elif token == 'h':
        unit = Frequency(num)

    elif token == 'P':
        unit = PPM(num)

    elif token == 'p':
        unit = Point(num)

    elif token == 's':
        unit = Time(num)

    elif token in '0123456789.':
        if '.' in unitString:
            unit = Point(num)
        else:
            unit = Index(num)
    return unit

def genScript(arrayed=False):
    global fidInfo
    script = ''
    sequence = fidInfo.fidObj.getSequence()
    if fidInfo.nd < 2:
        script += 'DIM(1)\n'
        script += 'SUPPRESS(disabled=True)\n'
        script += 'EXPD(lb=1.0)\n'
        script += 'ZF()\n'
        script += 'FT()\n'

        trim = fidInfo.fidObj.getTrim()
        if trim > 1.0e-3:
            script += 'TRIM(ftrim=' + str(trim) +')\n'
        phases = NMRDataUtil.autoPhase(fidInfo.fidObj);
        script += 'PHASE(ph0='+str(round(phases[0],1))+',ph1='+str(round(phases[1]))+')\n'
        script += 'BaselineGroup()\n'
    else:
        script += psspecial.scriptMods(fidInfo, 0)
        script += 'DIM(1)\n'
        for iDim in range(2,fidInfo.nd+1):
            if not fidInfo.fidObj.isFrequencyDim(iDim-1):
                continue
            if not fidInfo.isComplex(iDim-1):
                continue
            if fidInfo.mapToDatasetList[iDim-1] == -1:
                continue
        script += 'SUPPRESS(disabled=True)\n'
        script += 'SB()\n'
        script += 'ZF()\n'
        script += 'FT()\n'
        script += 'PHASE(ph0=0.0,ph1=0.0)\n'
        fCoef = fidInfo.getSymbolicCoefs(1)
        if fCoef != None and fCoef == 'sep' and not arrayed:
            script += "COMB(coef='sep')\n"
        script += 'EXTRACTP(disabled=True)\n'
        if fidInfo.nd > 2 and fidInfo.fidObj.getSampleSchedule() != None:
            multiDim = 'DIM(2'
            for mDim in range(2,fidInfo.nd):
                multiDim += ',' + str(mDim+1)
            multiDim += ')'
            script += multiDim + '\n'
            script += 'SB(dim=0, c=0.5)\n'
            script += 'NESTA()\n'
    for iDim in range(2,fidInfo.nd+1):
        if fidInfo.size[iDim-1] < 2:
            continue
        if fidInfo.mapToDatasetList[iDim-1] == -1:
            continue
        if not fidInfo.fidObj.isFrequencyDim(iDim-1):
            continue
        if (iDim >= fidInfo.nd) and arrayed:
            continue
        fCoef = fidInfo.getSymbolicCoefs(iDim-1)
        if fCoef == "array":
            continue
        script += 'DIM('+str(iDim)+')\n'
        if iDim == 2 and fidInfo.nd == 2 and fidInfo.fidObj.getSampleSchedule() != None:
            script += 'NUSGroup()\n'
        else:
            script += 'EXTEND(disabled=True)\n'
        script += 'SB(c=0.5)\n'
        script += 'ZF()\n'

        if fidInfo.fidObj.getFTType(iDim-1) == "rft":
            script += 'RFT('
        else:
            script += 'FT('

        negateImag = fidInfo.negateImagFT(iDim-1)
        negatePairs = fidInfo.negatePairsFT(iDim-1)
        if negatePairs:
            script += 'negatePairs=True'
        if negateImag:
            if negatePairs:
                script += ','
            if fidInfo.fidObj.getFTType(iDim-1) == "rft":
                script += 'negateOdd=True'
            else:
                script += 'negateImag=True'
        script += ')\n'
        fCoef = fidInfo.getSymbolicCoefs(iDim-1)
        if fCoef != None and fCoef == 'sep':
            script += "MAG()\n"
        else:
            script += 'PHASE(ph0=0.0,ph1=0.0)\n'
    if fidInfo.nd > 1:
        for iDim in range(1,fidInfo.nd+1):
            if fidInfo.size[iDim-1] < 2:
                continue
            if fidInfo.mapToDatasetList[iDim-1] == -1:
                continue
            if not fidInfo.fidObj.isFrequencyDim(iDim-1):
                continue
            if (iDim >= fidInfo.nd) and arrayed:
                continue
            fCoef = fidInfo.getSymbolicCoefs(iDim-1)
            if fCoef == "array":
                continue
            if iDim == 1:
                script += 'DIM()\n'
                script += 'DPHASE(disabled=True)\n'
            script += 'DIM('+str(iDim)+')\n'
            script += 'BaselineGroup()\n'
    script += 'run()'
    return script


def ddoc(op,opList):
   argspec = inspect.getargspec(op)
   argNames = argspec[0]
   keyWordArgs =  argspec[2]
   defaults =  argspec[3]
   nArgs = len(argNames)
   if defaults == None:
       nDefaults = 0
   else:
       nDefaults = len(defaults)
   inPar = False
   s=op.__doc__.split('\n') 
   iArg = -1
   opDesc = ''
   opMap = HashMap()
   opList.add(op.__name__)
   example = op.__name__+"("
   nNoDefaults = len(argNames)
   if defaults != None:
      nNoDefaults -= len(defaults)
   for i in range(nNoDefaults):
       example += argNames[i]+','

   if defaults != None :
       for (argName,defaultValue) in zip(argNames[nNoDefaults:],defaults):
           if argName == "disabled":
               break
           if isinstance(defaultValue,str):
               example += argName+"="+"'" + defaultValue + "'"
           else:
               example += argName+"="+str(defaultValue)
           example += ","
   example = example.strip(',')+")"
   opList.add(example)
   parList = ArrayList()
   opList.add(parList)
   for line in s:
       n4space = line.count('    ')
       line = line.strip()
       
       if line.startswith('----'):
           continue
       if line == '':
           inPar = False
           continue
       if line.startswith('Parameters'):
           inPar = True
       else:
           if not inPar:
               opDesc = opDesc + ' ' +line
           else:
               if (n4space == 1):
                   parMap = HashMap()
                   parList.add(parMap)
                   parMap.clear()
                   iArg += 1
                   pars = line.split(' : ')
                   #ast.literal_eval
                   parName = pars[0].strip()
                   if ((parName != 'keywords') and (parName != argNames[iArg])):
                       print parName,' not equal to ',argNames[iArg]
                       exit()
                   iDefault = nArgs-iArg
                   hasDefault = True
                   #print nArgs,iArg,iDefault,nDefaults
                   default = None
                   if (iDefault > nDefaults):
                       hasDefault = False
                   else:
                       default = defaults[-iDefault]
#                   (parType,parOptional)= pars[1].split(',')
                   #print pars[1].strip()
                   parTypeList = ArrayList()
                   if (pars[1][0] == '{'):
                       parTypeString = pars[1].strip()
                       #parTypeString = "set([" + parTypeString[1:-1] + "])"
                       parTypeString = "(" + parTypeString[1:-1] + ")"
                       #print parTypeString
                       #parTypes = ast.literal_eval(parTypeString)
                       parTypes = eval(parTypeString)
                       if isinstance(parTypes,tuple):
                          for parType in parTypes:
                              parTypeList.add(parType)
                   elif (pars[1][0] == '['):
                        parTypeList.add('list')
                        lst = pars[1]
                        listTypes = eval(lst)
                        listTypeList = ArrayList()
                        for listType in listTypes:
                            listTypeList.add(listType)
                        parMap.put('listTypes', listTypeList)
                   else:
                        parTypeList.add(pars[1].strip())
                          
                   #parOptional = parOptional.strip()=='optional'
                   parOptional = hasDefault;
                   #print 'parName ',parName,'type ',parType,'optional ', parOptional
                   parMap.put('name',parName)
                   parMap.put('type',parTypeList)
                   parMap.put('optional',parOptional)
               else:
                   if line.find(' : ') == -1:
                       parMap.put('desc',line)
                       #print 'desc',line
                       if hasDefault:
                           parMap.put('default',default)
                           #print 'default ',default
                   else:
                       #print 'opts',line
                       opts = line.split(' : ')
                       optName = opts[0].strip()
                       optValue = opts[1].strip()
                       parMap.put(optName,optValue)

   opList.add(opDesc.strip())

def getOperationList():
    '''Get a list of all Operations that have a name such that NAME.toupper() is True, and they are also python functions, and their signature includes parameters "vector" and "process"'''
    operation_list = []
    for op in globals():
        if op.isupper():
            #print globals()[op]
            try:
                arg_spec = inspect.getargspec(globals()[op])[0]
                if (('dataset' in arg_spec) or ('vector' in arg_spec) or ('inVec' in arg_spec))  and 'process' in arg_spec:
                    operation_list.append(op)
                elif op == "ISTMATRIX":
                    operation_list.append(op)
            except TypeError:
                #argument is not a python function (like IO)
                continue

    #These functions don't have documentation, so they should be excluded
    #(can check to see if the function has documentation and dynamically
    #add to the list as well.)
    exclude_operations = []
    exclude_operations += ['ISTCL',] #currently broken
    
    #return all operations that are not excluded
    opList = filter(lambda op: op not in exclude_operations, operation_list)
    opList.sort()
    return opList

def getDocs():
    opList = ArrayList()
    for operation in getOperationList():
        ddoc(globals()[operation], opList)
    return opList

def getRefDocs(ops):
   opList = ArrayList()
   for op in ops:
      ddoc(globals()[op], opList)
   return opList

def parseFileArgs():
    if (len(sys.argv) < 3):
        sys.exit("usage: script FIDDIR datasetName")
    fidDir,datasetName = sys.argv[1:3]
    fidInfo = FID(fidDir)
    datasetInfo = CREATE(datasetName)
    return fidInfo,datasetInfo

def setTestLocations(fidHome, tmpHome):
    global FIDHOME
    global TMPHOME
    FIDHOME = fidHome
    TMPHOME = tmpHome

def getTestLocations():
    global FIDHOME
    global TMPHOME
    try:
        fidHome = FIDHOME
        tmpHome = TMPHOME
    except NameError:
        fidHome = os.getenv("FIDHOME")
        tmpHome = os.getenv("TMPHOME")
        if fidHome == None:
            fidHome = "../../nmrfx-test-data/testfids"
        if tmpHome == None:
            tmpHome = "../../nmrfx-test-data-gen"
            if not os.path.exists(tmpHome):
                tmpHome = None
        
    return (fidHome, tmpHome)

dataInfo = DataInfo()
