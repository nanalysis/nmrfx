import sys
from refine import *
from molio import readYaml
import os
import osfiles
import runpy
from optparse import OptionParser
from org.yaml.snakeyaml import Yaml

yamlStr = '''  
anneal :
    dynOptions :
        steps : 15000
        highTemp : 5000.0
    param :
        swap : 20
    force :
        irp : 0.05
'''


def genNEFYaml(fileName):

    input = 'nef : ' + fileName + '\n'
    input += yamlStr

    print input
    
    yaml = Yaml()
    data = yaml.load(input)
    return data


def parseArgs():
    homeDir = os.getcwd()
    parser = OptionParser()
    parser.add_option("-s", "--seed", dest="seed",default='0', help="Random number generator seed")
    parser.add_option("-d", "--directory", dest="directory",default=homeDir, help="Base directory for output files ")
    parser.add_option("-r", "--report", action="store_true",dest="report",default=False, help="Report violations in energy dump file ")

    (options, args) = parser.parse_args()
    homeDir = options.directory
    outDir = os.path.join(homeDir,'output')
    finDir = os.path.join(homeDir,'final')
    seed = long(options.seed)
    report = options.report



    argFile = args[0]

    dataDir = homeDir + '/'
    outDir = os.path.join(homeDir,'output')
    if not os.path.exists(outDir):
        os.mkdir(outDir)

    data = None
    if argFile.endswith('.yaml'):
        data = readYaml(argFile)
    elif argFile.endswith('.nef'):
        data = genNEFYaml(argFile)

    if data != None:
        global refiner
        refiner=refine()
        osfiles.setOutFiles(refiner,dataDir, seed)
        refiner.setReportDump(report) # if -r seen == True; else False
        refiner.rootName = "temp"
        refiner.loadFromYaml(data,seed)
        if 'anneal' in data:
            refiner.anneal(refiner.dOpt)
        if 'script' in data:
            runpy.run_path(data['script'], init_globals=globals())
        if len(args) > 1:
            scriptFile = args[1]
            runpy.run_path(scriptFile, init_globals=globals())
        refiner.output()
    else:
        runpy.run_path(argFile)

parseArgs()
sys.exit()
