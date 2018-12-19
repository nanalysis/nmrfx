import subprocess
import time
import sys
import re
import os
import os.path
import glob
import shutil
import imp
from super import *
from java.lang import System
from java.lang import Runtime
from optparse import OptionParser

def makeDirs():
    if not os.path.exists(outDir):
        os.mkdir(outDir)
    if not os.path.exists(finDir):
        os.mkdir(finDir)

def cleanDirs():
    if os.path.exists(outDir):
        existingFiles = glob.glob(os.path.join(outDir,'*'))
        for file in existingFiles:
            os.remove(file)
    if os.path.exists(finDir):
        existingFiles = glob.glob(os.path.join(finDir,'*'))
        for file in existingFiles:
            os.remove(file)

def getCmd():
    classPath = System.getProperties().get('java.class.path').split(':')
    cmd = System.getenv("pgm")
    if cmd == None:
        jarPath = classPath[1]
        print jarPath
        dir = os.path.dirname(jarPath)
        dir = os.path.dirname(dir)
        print dir
        cmd = os.path.join(dir,'nmrfxs')
    return cmd

def calcStructures(calcScript,startStructure,nStructures,dir,nProcesses=4, heapMemory=512):
    makeDirs()
    cmd = getCmd()
    print cmd
    nSubmitted = 0
    processes = [None]*nProcesses
    fOut = [None]*nProcesses
    myEnv = os.environ.copy()
    myEnv["NMRFXSTR_MEMORY"] = str(heapMemory)
    while (True):
        for i in range(nProcesses):
            if (processes[i]==None):
                if nSubmitted < nStructures:
                    strNum = nSubmitted+startStructure
                    fOutName = os.path.join(outDir,'cmdout_'+str(strNum)+'.txt')
                    fOut[i] = open(fOutName,'w')
                    processes[i] = subprocess.Popen([cmd,"gen","-d",dir,"-s",str(strNum),calcScript,],stdout=fOut[i],stderr=subprocess.STDOUT,env=myEnv)
                    pid = processes[i].pid
                    outStr =  "submit %d seed: %3d Structure # %3d of %3d pid %7d" % (i,strNum,(nSubmitted+1),nStructures,pid)
                    print outStr
                    nSubmitted += 1
                #else:
                    #print "Submitted all",nStructures
        gotProcess = False
        for i in range(nProcesses):
            if (processes[i] != None):
                if (processes[i].poll() == None):
                    # still running
                    gotProcess = True
                else:
                    print "Finished",i,processes[i].pid
                    processes[i] = None
                    fOut[i].close()
        if not gotProcess:
            if  (nSubmitted == nStructures):
                print "Done"
                break
        time.sleep(1)

def keepStructures(nStructures,newName='final',rootName=''):
    pat = re.compile('.*\D([0-9]+).pdb')
    ePat = re.compile('.*\sTotal\s([0-9\.\0]+)')
    pdbFiles = glob.glob(os.path.join(outDir,rootName+'*.pdb'))
    eValues = []
    for pdbFile in pdbFiles:
        (root,ext) = os.path.splitext(pdbFile)
        structNum = pat.match(pdbFile).group(1)
        eFile = root+'.txt'
        aFile = root+'.ang'
        f1 = open(eFile,'r')
        for line in f1:
            line = line.strip()
            lineMatch = ePat.match(line)
            if lineMatch:
                energy = lineMatch.group(1)
                eValues.append((pdbFile,eFile,aFile,structNum,float(energy),line))
        f1.close()
    eValues.sort(key=lambda tup: tup[4])
    iFile = 1
    fOut = open(os.path.join(finDir,'summary.txt'),'w')
    for fileInfo in eValues[0:nStructures]:
        (pdbFile,eFile,aFile,structNum,energy,eLine) = fileInfo
        shutil.copyfile(pdbFile,os.path.join(finDir,newName+str(iFile)+'.pdb'))
        shutil.copyfile(eFile,os.path.join(finDir,newName+str(iFile)+'.txt'))

        if os.path.exists(aFile):
            shutil.copyfile(aFile,os.path.join(finDir,newName+str(iFile)+'.ang'))
        fOut.write(str(iFile)+' '+eLine+'\n');
        iFile += 1
    fOut.close()


def parseArgs():
    global calcScript
    global nStructures
    global nProcesses
    global nKeep
    global clean
    global homeDir
    global outDir
    global finDir
    homeDir = os.getcwd()

    nProcesses = Runtime.getRuntime().availableProcessors()

    parser = OptionParser()
    parser.add_option("-n", "--nstructures", dest="nStructures",default='0', help="Number of structures to calculate (0)")
    parser.add_option("-k", "--nkeep", dest="nKeep",default='-1', help="Number of structures to keep (nstructures/4)")
    parser.add_option("-s", "--start", dest="start",default='0', help="Starting number for structures (0)")
    parser.add_option("-p", "--nprocesses", dest="nProcesses",default=nProcesses, help="Number of simultaneous processes (nCpu)")
    parser.add_option("-a", "--align", action="store_true", dest="align", default=False, help="Align structures (False)")
    parser.add_option("-b", "--base", dest="base",default='super', help="Base name for superimposed files (super)")
    parser.add_option("-d", "--directory", dest="directory",default=homeDir, help="Base directory for output files ")
    parser.add_option("-c", "--clean", action="store_true", dest="clean", default=False, help="Clean Directories (False)")
    parser.add_option("-m", "--memory", dest="heapMemory",default='512', help="Amount of heap memory to use in MBytes")

    (options, args) = parser.parse_args()
    print 'args',args
    homeDir = options.directory
    outDir = os.path.join(homeDir,'output')
    finDir = os.path.join(homeDir,'final')

    nStructures = int(options.nStructures)
    clean = options.clean

    if clean:
        cleanDirs()
        if nStructures == 0:
            exit(0)

    nKeep = int(options.nKeep)
    heapMemory = int(options.heapMemory)
    start = int(options.start)
    nProcesses = int(options.nProcesses)
    align = options.align
    base = options.base
    if nKeep == 0:
        if nStructures <= 10:
            nKeep = nStructures
        else:
           nKeep = nStructures/4
           if nKeep == 0:
               nKeep = nStructures

    if nKeep > nStructures and (nStructures != 0):
        nKeep = nStructures
    if nProcesses > nStructures:
        nProcesses = nStructures

    if nStructures > 0:
        if len(args) > 0:
           calcScript = args[0]
        else:
           print 'Must specify script'
           exit()
        calcStructures(calcScript,start,nStructures,homeDir,nProcesses,heapMemory)
    if nKeep > 0:
        keepStructures(nKeep)
    if align:
        if nStructures == 0 and len(args) > 0:
            files = args
        else:
            files = glob.glob(os.path.join(finDir,'final*.pdb'))
        runSuper(files, base)

parseArgs()

#    return (calcScript,nStructures,nProcesses,nKeep, align)
#(calcScript,nStructures,nProcesses,nKeep, align) = parseArgs()
