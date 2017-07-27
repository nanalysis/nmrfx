import sys
from refine import *
import os
import osfiles

homeDir =  os.getcwd( )
dataDir = homeDir + '/'
outDir = os.path.join(homeDir,'output')

yamlFile = sys.argv[-2]
seed = int(sys.argv[-1])
refiner=refine()

osfiles.setOutFiles(refiner,dataDir, seed)
refiner.rootName = "temp"
refiner.loadFromYaml(yamlFile, seed)

refiner.anneal(refiner.dOpt)
refiner.output()
