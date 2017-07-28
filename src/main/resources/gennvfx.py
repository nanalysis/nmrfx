import sys
from refine import *
import os
import osfiles
import runpy

homeDir =  os.getcwd( )
dataDir = homeDir + '/'
outDir = os.path.join(homeDir,'output')

argFile = sys.argv[-2]
seed = int(sys.argv[-1])

if argFile.endswith('.yaml'):
    refiner=refine()

    osfiles.setOutFiles(refiner,dataDir, seed)
    refiner.rootName = "temp"
    refiner.loadFromYaml(argFile, seed)
    refiner.anneal(refiner.dOpt)
    refiner.output()

else:
    runpy.run_path(argFile)
