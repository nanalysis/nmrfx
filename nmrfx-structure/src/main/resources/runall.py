import subprocess
import time
import sys
import re
import os
import os.path
import glob
import shutil
import imp
import checke
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
    """
    Returns errStatus (int)
    - zero if calculations successful
    - nonzero otherwise
    """
    makeDirs()
    cmd = getCmd()
    print cmd
    nSubmitted = 0
    processes = [None]*nProcesses
    startTimes = [None]*nProcesses
    seedNums = [None]*nProcesses
    fOut = [None]*nProcesses
    myEnv = os.environ.copy()
    myEnv["NMRFXSTR_MEMORY"] = str(heapMemory)
    errStatus = 0
    errExit = False
    eTimes = []
    nSeed = 0

    while (True):

        # Submission for-loop
        for i in range(nProcesses):
            # errStatus is initially zero to indicate no errors and this for-loop is "good" for submission
            # [submit process] if processes[i]==None and errStatus==0
            # [DONT submit process] otherwise
            if (processes[i]==None and (not errStatus)):
                if nSubmitted < nStructures:
                    strNum = nSeed+startStructure
                    fOutName = os.path.join(outDir,'cmdout_'+str(strNum)+'.txt')
                    fOut[i] = open(fOutName,'w')
                    processes[i] = subprocess.Popen([cmd,"gen","-d",dir,"-s",str(strNum), "-Y", yamlFile, calcScript,],stdout=fOut[i],stderr=subprocess.STDOUT,env=myEnv)
                    startTimes[i] = time.time()
                    seedNums[i] = strNum
                    pid = processes[i].pid
                    outStr =  "submit %d seed: %3d Structure # %3d of %3d pid %7d" % (i,strNum,(nSubmitted+1),nStructures,pid)
                    print outStr
                    nSubmitted += 1
                    nSeed += 1
                else:
                    #print "Submitted all",nStructures
                    break

        gotProcess = False
        # Status checking for-loop
        for i in range(nProcesses):
            if (processes[i] != None):
                retCode = processes[i].poll()
                eTime = time.time() - startTimes[i]
                if (retCode == None):
                    # still running, process hasn't terminated
                    if len(eTimes) > 1 and eTime > (3.0 * sum(eTimes)/len(eTimes)):
                        nSubmitted -= 1
                        strNum = seedNums[i]
                        print "Kill",i,"seed",strNum, "PID",processes[i].pid,"eTime",eTime
                        try:
                            processes[i].kill()
                        finally:
                            fOut[i].close()
                            processes[i] = None
                    else:
                        gotProcess = True
                else:
                    # if return code is nonzero, then an error occurred
		    if retCode != 0:
                        # set the error status to nonzero to signal the next preceding for-loop
                        # that there's an error so it shouldn't submit any more processes
                        errStatus = retCode
                        print "Error captured", i, processes[i].pid
                        print "Please see '{}'".format(fOut[i].name)
                    else:
                        eTimes.append(eTime)
                        nFinished = len(eTimes)
                        strNum = seedNums[i]
                        outStr =  "Finish %d seed: %3d Finished    %3d of %3d pid %7d eTime %6.1f" % (i,strNum,nFinished,nStructures,processes[i].pid,eTime)
                        print outStr
                    processes[i] = None
                    fOut[i].close()
            elif (processes[i] == None and errStatus):
                # If a process hasn't been submitted for processing and error status is nonzero,
                # then we have a problem...
                # Thus, we have to set a signal to exit due to error.
                errExit = True

        if errExit:
           # A job was not submitted for processing even though
           # there were more structures to calculate, and the error status
           # was nonzero...So we must break
           break

        if not gotProcess:
            if  (nSubmitted == nStructures):
                print "Done"
                break
        time.sleep(1)
    return errStatus

def keepStructures(nStructures,newName='final',rootName=''):
    pat = re.compile(r'.*\D([0-9]+).pdb')
    ePat = re.compile(r'.*\sTotal\s+([0-9\.\0\-]+)')
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
    sumFileName = os.path.join(finDir, 'summary.txt')
    if eValues:
        fOut = open(sumFileName,'w')
        for fileInfo in eValues[0:nStructures]:
            (pdbFile,eFile,aFile,structNum,energy,eLine) = fileInfo
            shutil.copyfile(pdbFile,os.path.join(finDir,newName+str(iFile)+'.pdb'))
            shutil.copyfile(eFile,os.path.join(finDir,newName+str(iFile)+'.txt'))

            if os.path.exists(aFile):
                shutil.copyfile(aFile,os.path.join(finDir,newName+str(iFile)+'.ang'))
            outLine = "%-3s %-3s %s" % (str(iFile), structNum, (eLine+'\n'))
            fOut.write(outLine);
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
    global yamlFile
    homeDir = os.getcwd()

    nProcesses = Runtime.getRuntime().availableProcessors()

    parser = OptionParser()
    parser.add_option("-n", "--nstructures", dest="nStructures",default='0', help="Number of structures to calculate (0)")
    parser.add_option("-k", "--nkeep", dest="nKeep",default='-1', help="Number of structures to keep (nstructures/4)")
    parser.add_option("-s", "--start", dest="start",default='0', help="Starting number for structures (0)")
    parser.add_option("-p", "--nprocesses", dest="nProcesses",default=nProcesses, help="Number of simultaneous processes (nCpu)")
    parser.add_option("-a", "--align", action="store_true", dest="align", default=False, help="Align structures (False)")
    parser.add_option("-b", "--base", dest="baseName",default='sup_', help="Base name (prefix) for superimposed files (sup_)")
    parser.add_option("-t", "--type", dest="type",default='cif', help="File type for superimposed files (cif)")
    parser.add_option("-d", "--directory", dest="directory",default=homeDir, help="Base directory for output files ")
    parser.add_option("-c", "--clean", action="store_true", dest="clean", default=False, help="Clean Directories (False)")
    parser.add_option("-m", "--memory", dest="heapMemory",default='512', help="Amount of heap memory to use in MBytes")
    parser.add_option("-y", "--yaml", dest="readYaml",default="", help="Read yaml file")

    (options, args) = parser.parse_args()
    print 'args',args
    homeDir = options.directory
    outDir = os.path.join(homeDir,'output')
    finDir = os.path.join(homeDir,'final')
    nStructures = int(options.nStructures)
    clean = options.clean
    yamlFile = options.readYaml

    if clean:
        cleanDirs()
        if nStructures == 0:
            exit(0)
    retCode = 0
    nKeep = int(options.nKeep)
    heapMemory = int(options.heapMemory)
    start = int(options.start)
    nProcesses = int(options.nProcesses)
    align = options.align
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
        retCode = calcStructures(calcScript,start,nStructures,homeDir,nProcesses,heapMemory)
    if nKeep > 0 and (not retCode):
        keepStructures(nKeep)
        checke.outDir = homeDir
        checke.summary()
    if align and (not retCode):
        if nStructures == 0 and len(args) > 0:
            files = args
        else:
            files = glob.glob(os.path.join(finDir,'final*.pdb'))
        if len(files) > 1:
            runAllSuper(files, options.type, options.baseName)

parseArgs()

#    return (calcScript,nStructures,nProcesses,nKeep, align)
#(calcScript,nStructures,nProcesses,nKeep, align) = parseArgs()
