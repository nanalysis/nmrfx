import os
import os.path
import glob
import sys.argv
import argparse
from itertools import izip

from org.nmrfx.processor.datasets import Dataset
from pyproc import *
from dscript import nd

def parseArgs():
    parser = argparse.ArgumentParser(description="Test NUS Processing")
    parser.add_argument("-b",dest='beta', type=float, default=12.0,help="Kaiser beta value")
    parser.add_argument("-t",dest='tolFinal', type=float, default=2.5,help="Final Tolerance")
    parser.add_argument("-T",dest='threshold', type=float, default=0.0,help="Skip Threshold")
    parser.add_argument("-f",dest='istFraction', type=float, default=0.9,help="IST Fractional Threshold")
    parser.add_argument("-m",dest='muFinal', type=float, default=6.0,help="Final mu")
    parser.add_argument("-i",dest='nInner',type=int, default=20,help="Number of inner iterations")
    parser.add_argument("-o",dest='nOuter',type=int, default=15,help="Number of outer iterations")
    parser.add_argument("-l",dest='schedLines',default="125",help="Number of lines in schedule")
    parser.add_argument("-s",dest='schedType',default='pg',help="Schedule type")
    parser.add_argument("-n",dest='schedNum',default='0',help="Schedule number")
    parser.add_argument("-a",dest='nusAlg',default='NESTA',help="NUS Mode")
    parser.add_argument("-w",dest='apod',default='kaiser',help="Apodization Window")
    parser.add_argument("-u",dest='uniform',action='store_true', help="NUS Mode")
    parser.add_argument("-c",dest='compare',action='store_true', help="NUS Mode")
    parser.add_argument("-p",dest='saveToPipe',action='store_true', help="Save to nmrPipe format")
    parser.add_argument("-x",dest='extractNUS',action='store_true', help="Extract sample from uniform")
    parser.add_argument("-z",dest='zf',type=int,default=1,help="Zero Filling Factor (all dimensions)")
    parser.add_argument("fileNames",nargs="*")
    args = parser.parse_args()
    return args

def report(args):
        result = "alg %s beta %f apod %s tolFinal %.1f muFinal %.1f nOuter %d nInner %d" % (args.nusAlg, args.beta, args.apod, args.tolFinal, args.muFinal, args.nOuter, args.nInner)
        return result

def analyzeLog(fidDirName):
    fileName = os.path.join(fidDirName,'nesta','*.log')
    files = glob.glob(fileName)
    sum = 0.0
    for file in files:
        with open(file,'r') as f1:
            for line in f1:
                if line.startswith('iNorm'):
                   fields = line.split()
                   fNorm = float(fields[3].strip())
                   sum += fNorm
    return sum

def compareFiles(dName1, dName2, threshold):
    dataset1 = Dataset(dName1,'data1.nv',False)
    dataset2 = Dataset(dName2,'data2.nv',False)
    iDim=0

    sum = 0.0
    for (vec1,vec2) in izip(dataset1.vectors(iDim), dataset2.vectors(iDim)):
        vec1.softThreshold(threshold)
        vec2.softThreshold(threshold)
        vec3 = vec1 - vec2
        vec3.abs()
        sum += vec3.sum().getReal()
    return sum

def getFileNames(fidRootDir, datasetDir, challenge, sample, expName, args):
    schedType=args.schedType
    schedLines=args.schedLines
    schedNum=args.schedNum
    mode=args.nusAlg

    fidFileDir = os.path.join(fidRootDir,challenge,sample+"-"+expName,"US_data")
    schedFileDir = os.path.join(fidRootDir,challenge,sample+"-"+expName,"sample_schedules")
    uniformFileName=sample+"-"+expName+"-uniform.nv"
    #statFileName=sample+"-"+expName+"-uniform.txt"
    pipeFileName = "pipe-"+sample+"-"+expName+"-uniform"
    if args.uniform:
        scheduleFileName = None
        datasetFileName=uniformFileName
        mode = "ft"
    else:
        scheduleName=sample+"-"+expName+"-"+schedType+"-"+schedLines+"-"+schedNum
        scheduleFileName = os.path.join(schedFileDir,scheduleName+".txt")
        fileRootName = scheduleName+"-"+mode
        datasetFileName = fileRootName+".nv"
        #statFileName = fileRootName+".txt"
        pipeFileName = "pipe-"+fileRootName

    if not os.path.exists(datasetDir):
        os.mkdir(datasetDir)

    statFileName = sample + "-" + expName + "-" + "report.txt"

    datasetFile = os.path.join(datasetDir, datasetFileName)
    uniformFile = os.path.join(datasetDir, uniformFileName)
    statFile = os.path.join(datasetDir, statFileName)
    return (fidFileDir,datasetFile,scheduleFileName,uniformFile,statFile)

def saveToPipe(datasetFile, pipeFile=None):
    datasetDir,fileName = os.path.split(datasetFile)
    fileRootName,ext = os.path.splitext(fileName)
    if pipeFile == None:
        pipeFileName = 'pipe-'+fileRootName
        pipeFileDir = os.path.join(datasetDir, pipeFileName)
        if not os.path.exists(pipeFileDir):
            os.mkdir(pipeFileDir)
        pipeFile = os.path.join(pipeFileDir, "test%03d.ft")
    else:
        pipeFileDir = os.path.dirname(pipeFile)
        if not os.path.exists(pipeFileDir):
            os.mkdir(pipeFileDir)

    dataset=nd.open(datasetFile)
    nd.toPipe(dataset, pipeFile)

def doCompare(fidFileDir, datasetFile, uniformFile,statFile, args, threshold):
        fNormSum = analyzeLog(fidFileDir)
        compareSum = compareFiles(datasetFile,uniformFile, threshold)
        with open(statFile,'a') as fStat:
            outStr = os.path.split(datasetFile)[-1] + " l1 " + str(fNormSum) + " diff " +  str(compareSum) + " " + report(args) + "\n"
            fStat.write(outStr)

def testData(pars, challenge=None, sample=None, expName=None):
    args = parseArgs()
    if args.uniform:
        args.nusAlg = "ft"
    print 'arfi',args.fileNames
    if len(args.fileNames) > 1:
        fidFileDir = args.fileNames[0]
        datasetFile = args.fileNames[1]
        if len(args.fileNames) > 2:
            scheduleFileName = args.fileNames[2]
    else:
        fidRootDir = os.environ.get('FIDDIR')
        datasetDir = os.environ.get('DATADIR')
        fidFileDir,datasetFile,scheduleFileName,uniformFile,statFile = getFileNames(fidRootDir, datasetDir, challenge, sample, expName, args)


    if scheduleFileName != None and not os.path.exists(scheduleFileName):
               raise Exception("Schedule file " + scheduleFileName + " doesn't exist")

    execNUS(fidFileDir, datasetFile, scheduleFileName, pars, args)
    if args.saveToPipe:
        saveToPipe(datasetFile)
    if args.compare:
        doCompare(fidFileDir, datasetFile, uniformFile,statFile, args, pars['threshold'])

def getNegation(pars, varName, nDim=3):
    if varName in pars:
        result = pars[varName]
    else:
        result = [False]*nDim
    return result 
        
def execNUS(fidDirName, datasetName, scheduleName,  pars, args=None):
    if args == None:
        args = parseArgs()
    if args.extractNUS:
        FID(fidDirName, nusFileName=None)
    else:
        FID(fidDirName, nusFileName=scheduleName)

    pipeMode = "%" in fidDirName

    CREATE(datasetName)
    kOffset = 0.495
    ph=pars['phases']
    lab=pars['labels']
    prangeMode = False
    if 'prange' in pars:
        prangeMode = True
        (start,end)=pars['prange']
        print pars['prange'],start,end
    else:
        (start,end)=pars['range']
    tdcomb = pars['tdcomb']
    negI=pars['negI']
    negP=pars['negP']
    refValues=pars['ref']
    mode = args.nusAlg
    zfFactor = args.zf
    if args.extractNUS:
        if scheduleName != None:
            readNUS(scheduleName)
    acqOrder('321')
    acqarray(0,0,0)
    skip(0,0,0)
    label(lab[0], lab[1], lab[2])
    acqsize(0,0,0)
    tdsize(0,0,0)
    if pipeMode:
        sf('FDF2OBS','FDF1OBS','FDF3OBS')
        sw('FDF2SW','FDF1SW','FDF3SW')
    else:
        sf('SFO1,1','SFO1,2','SFO1,3')
        sw('SW_h,1','SW_h,2','SW_h,3')


    ref(refValues[0],refValues[1],refValues[2])
    DIM(1)
    if tdcomb != "":
        TDCOMB(coef=tdcomb)
    if args.apod == "blackman":
        BLACKMAN()
    else:
        KAISER(beta=args.beta,offset=kOffset)
    ZF(factor=zfFactor)
    FT()
    PHASE(ph0=ph[0][0],ph1=ph[0][1],dimag=False)
    if prangeMode:
        EXTRACTP(fstart=start,fend=end)
    else:
        EXTRACT(start=start,end=end,mode='region')
    DIM(2,3)
    if args.apod == "blackman":
        BLACKMAN(c=0.5, dim=1)
        BLACKMAN(c=0.5, dim=2)
    else:
        KAISER(c=0.5, offset=kOffset, beta=args.beta, dim=1)
        KAISER(c=0.5, offset=kOffset, beta=args.beta, dim=2)
    PHASEND(ph0=ph[1][0],ph1=ph[1][1],dim=1)
    PHASEND(ph0=ph[2][0],ph1=ph[2][1],dim=2)
    if mode == "NESTA":
        NESTA(tolFinal=args.tolFinal, muFinal=args.muFinal, threshold=args.threshold, nOuter=args.nOuter, nInner=args.nInner, logToFile=False)
    elif mode == "IST":
        ISTMATRIX(threshold=args.istFraction, iterations=args.nInner, alg="std")
    DIM(2)
    ZF(factor=zfFactor)
    FT(negatePairs=negP[1], negateImag=negI[1])
    REAL()
    DIM(3)
    ZF(factor=zfFactor)
    FT(negatePairs=negP[2], negateImag=negI[2])
    REAL()
    run()
