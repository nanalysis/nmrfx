import sys
from osfiles import *
from checke import loadPDBModels
from checke import summary
from optparse import OptionParser
import os;

homeDir = os.getcwd()


def getDataFiles(options):
    shiftFile = options.shiftFile
    disFile = options.disFile

    modifyFileType = bool(options.modifyFileType)
    outDir = options.outDir

    shiftFileExists = (shiftFile != None)
    disFileExists = (disFile != None)

    pattern = options.resRange
    limRes = (pattern != "")

    if shiftFileExists:
        shiftFile = getFile(shiftFile,'shift')
        if shiftFile == None:
            raise Exception('Improper shift file path')
        if modifyFileType:
            shiftFile = convertStarFile(shiftFile, outDir)
        if limRes:
            shiftFile = limResidues(pattern,shiftFile,outDir,'shift')

    if disFileExists:
        disFile = getFile(disFile,'distance constraint')
        if disFile== None:
            raise Exception('Improper distance constraints file path')
        if modifyFileType:
            disFile = convertConstraintFile(disFile,outDir)
        if limRes:
            disFile = limResidues(pattern,disFile,outDir,'dis')
    return {'disFile':disFile, 'shiftFile':shiftFile}


def runTests():
    parser = OptionParser()
    parser.add_option("-p", "--pdbs", dest="pdbPath", default="")
    parser.add_option("-o", "--outDir", dest="outDir", default="analysis")
    parser.add_option("-c", "--convert", action='store_true', dest="modifyFileType", default=False)
    parser.add_option("-s", "--shifts", dest="shiftFile", default=None)
    parser.add_option("-d", "--distances", dest="disFile", default=None)
    parser.add_option("-r", "--range", dest="resRange", default="")

    (options, args) = parser.parse_args()
    outDir = os.path.join(homeDir,options.outDir)
    if not os.path.exists(outDir):
        os.mkdir(outDir)

    dataFiles = getDataFiles(options)

    dataFiles['outDir'] = options.outDir
    pdbFilePath = options.pdbPath
    if pdbFilePath=="":
        pdbFiles = args
    else:
        pdbFiles = getFiles(pdbFilePath)
    outFiles = loadPDBModels(pdbFiles,dataFiles=dataFiles)
    summary(outFiles)

runTests()

