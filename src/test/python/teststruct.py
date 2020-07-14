import unittest
import math
from org.yaml.snakeyaml import Yaml
from molio import readYaml
from refine import *
import osfiles



def importOp(opName):
    exec '''from org.nmrfx.processor.operations import %s''' % opName
    exec '''globals()[\'%s\'] = %s''' % (opName, opName)

def genYaml():
    input = '''
molecule :
  entities :
    - sequence : GPGAST
      type : nv
      ptype : protein

anneal:
    steps : 1000
    highTemp : 1000.0
'''
    yaml = Yaml()
    data = yaml.load(input)
    return data


class TestStructGen(unittest.TestCase):
    def testDeriv(self):
        data = genYaml()
        refiner=refine()
        dataDir = 'tmp'
        seed = 0
        report = False
        osfiles.setOutFiles(refiner,dataDir, seed)
        refiner.setReportDump(report) # if -r seen == True; else False
        refiner.rootName = "temp"
        refiner.loadFromYaml(data,seed)
        refiner.anneal(refiner.dOpt)
        err = refiner.calcDerivError(1.0e-5)
        self.assertAlmostEqual(0.0, err, 3)

if __name__ == '__main__':
    unittest.main()
