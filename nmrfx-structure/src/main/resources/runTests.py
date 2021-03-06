import sys
from osfiles import *
from checke import loadPDBModels
from checke import summary
from optparse import OptionParser
import os;
from org.yaml.snakeyaml import Yaml
from java.io import FileInputStream

homeDir = os.getcwd()

def parseArgs():
    parser = OptionParser()
    parser.add_option("-p", "--pdbs", dest="pdbPath", default="")
    parser.add_option("-o", "--outDir", dest="outDir", default="analysis")


    parser.add_option("-y", "--yaml", dest="yamlFile", default=None)

    #Will now be used to add addition files to parse that are not included in the yaml file
#    parser.add_option("-c", "--convert", action='store_true', dest="modifyFileType", default=False) # not used!
    parser.add_option("-s", "--shifts", dest="shiftFile", default=None)
    parser.add_option("-d", "--distances", dest="disFile", default=None)
    parser.add_option("-r", "--range", dest="resRange", default="")
    parser.add_option("-l", dest="limDis", default=0.2, help="Only violations with an error of at least this amount will be reported (0.2)")
    parser.add_option("-n", dest="nViols", default=2, help="Only violations that occur in at least this number of structures will be reported (2).")


    (options, args) = parser.parse_args()
    outDir = os.path.join(homeDir,options.outDir)
    if not os.path.exists(outDir):
        os.mkdir(outDir)

    
    pdbFilePath = options.pdbPath
    if pdbFilePath=="":
        pdbFiles = args
    else:
        pdbFiles = getFiles(pdbFilePath)

    changeRange = (options.resRange != '')
    range = options.resRange
    limDis = float(options.limDis)
    nViols = int(options.nViols)

    if (options.yamlFile != None):
        input = FileInputStream(options.yamlFile)
        yaml = Yaml() 
        data = yaml.load(input)
    else:
        data = {'ribose':True}


    if options.shiftFile != None:
        shift = {}
        if changeRange:
            shift['range'] = range
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
    else:
        if changeRange:
            data['shifts']['range'] = range

    if options.disFile != None:
        dis = {}
        if changeRange:
            dis['range'] = range;

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
            included = checkIncluded(data['distances'],dis)
            if not included:
                data['distances'].append(dis)
            else: 
                print dis['file'] + ' is already included in yaml file. Ignoring Duplicate.'
        else:
            data['distances'] = [dis]
    else:
        if changeRange:
            for dict in data['distances']:
                dict['range'] = range
 
    outFiles = loadPDBModels(pdbFiles,data,outDir)
    summary(outFiles, limDis, nViols)


def checkIncluded(constraintArray,newDict):
    newFile = newDict['file']
    for dict in constraintArray:
        if newFile == dict['file']:
            return True
    return False

parseArgs()
