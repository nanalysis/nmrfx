import sys
from osfiles import *
from checke import loadPDBModels
from checke import summary
from optparse import OptionParser
import os;
from org.yaml.snakeyaml import Yaml
from java.io import FileInputStream

homeDir = os.getcwd()

def runTests():
    parser = OptionParser()
    parser.add_option("-p", "--pdbs", dest="pdbPath", default="")
    parser.add_option("-o", "--outDir", dest="outDir", default="analysis")

    
    parser.add_option("-y", "--yaml", dest="yamlFile", default=None)

    #Will now be used to add addition files to parse that are not included in the yaml file
    parser.add_option("-c", "--convert", action='store_true', dest="modifyFileType", default=False)
    parser.add_option("-s", "--shifts", dest="shiftFile", default=None)
    parser.add_option("-d", "--distances", dest="disFile", default=None)
    parser.add_option("-r", "--range", dest="resRange", default="")

    (options, args) = parser.parse_args()
    outDir = os.path.join(homeDir,options.outDir)
    if not os.path.exists(outDir):
        os.mkdir(outDir)

    
    pdbFilePath = options.pdbPath
    if pdbFilePath=="":
        pdbFiles = args
    else:
        pdbFiles = getFiles(pdbFilePath)


    if (options.yamlFile != None):
        input = FileInputStream(options.yamlFile)
        yaml = Yaml() 
        data = yaml.load(input)
    else:
        data = {}
    
    if options.shiftFile != None:
        shift = {}
        arr = options.shiftFile.split(' ')
        if len(arr) == 1:
            shift['type'] = 'nv'
            shift['file'] = arr[0]
        else:
            shift['file'] = arr[0]
            if arr[1] == 's':
                shift['type'] = 'str3'
            elif arr[1] == 'n':
                shift['type'] = 'nv'
        data['shifts'] = shift

    if options.disFile != None:
        dis = {}
        arr = options.disFile.split(' ')
        if len(arr) == 1:
            dis['type'] = 'nv'
            dis['file'] = arr[0]
        else:
            dis['file'] = arr[0]
            if arr[1] == 'a':
                dis['type'] = 'amber'
            elif arr[1] == 'n':
                dis['type'] = 'nv'
        if 'distances' in data:
            data['distances'].append(dis)
        else:
            data['distances'] = [dis]
    outFiles = loadPDBModels(pdbFiles,data,outDir)
    summary(outFiles)

runTests()

