import sys
from refine import *
import os
import osfiles
import runpy
from org.yaml.snakeyaml import Yaml
from java.io import FileInputStream
from optparse import OptionParser

def parseArgs():
    homeDir = os.getcwd()
    parser = OptionParser()
    parser.add_option("-s", "--seed", dest="seed",default='0', help="Random number generator seed")
    parser.add_option("-d", "--directory", dest="directory",default=homeDir, help="Base directory for output files ")

    (options, args) = parser.parse_args()
    print 'args',args
    homeDir = options.directory
    outDir = os.path.join(homeDir,'output')
    finDir = os.path.join(homeDir,'final')
    seed = long(options.seed)



    argFile = args[0]

    dataDir = homeDir + '/'
    outDir = os.path.join(homeDir,'output')
    if not os.path.exists(outDir):
        os.mkdir(outDir)

    if argFile.endswith('.yaml'):
        input = FileInputStream(argFile)
        yaml = Yaml()
        data = yaml.load(input)

        refiner=refine()
        osfiles.setOutFiles(refiner,dataDir, seed)
        refiner.rootName = "temp"
        refiner.loadFromYaml(data,seed)
        if 'anneal' in data:
            refiner.anneal(refiner.dOpt)
        refiner.output()

    else:
        runpy.run_path(argFile)

parseArgs()
sys.exit()
