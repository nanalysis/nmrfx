import sys
from refine import *
import os
import osfiles
import runpy
from org.yaml.snakeyaml import Yaml
from java.io import FileInputStream


homeDir =  os.getcwd( )
dataDir = homeDir + '/'
outDir = os.path.join(homeDir,'output')
if not os.path.exists(outDir):
    os.mkdir(outDir)
argFile = sys.argv[-2]
seed = int(sys.argv[-1])

if argFile.endswith('.yaml'):
    input = FileInputStream(argFile)
    yaml = Yaml()
    data = yaml.load(input)

    refiner=refine()
    osfiles.setOutFiles(refiner,dataDir, seed)
    refiner.rootName = "temp"
    refiner.loadFromYaml(data,seed)

    refiner.anneal(refiner.dOpt)
    refiner.output()

else:
    runpy.run_path(argFile)
