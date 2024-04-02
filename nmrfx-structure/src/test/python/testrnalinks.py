import unittest
import math
from org.yaml.snakeyaml import Yaml
from molio import readYaml
from refine import *
import osfiles
import glob



def importOp(opName):
    exec '''from org.nmrfx.processor.operations import %s''' % opName
    exec '''globals()[\'%s\'] = %s''' % (opName, opName)

def genYaml():
    input = '''
molecule :
  entities :
      - sequence : GGCAGAUCUGAGCCUGGGAGCUCUCUGCC
        ptype : RNA
        chain : A

rna:
    ribose : Constrain
    vienna : ((((((...((((......))))))))))
    planarity : 1
    autolink : True

tree :

initialize:
    vienna :
        restrain : True
        lockfirst: False
        locklast: False
        lockloop: False
        lockbulge: False

anneal:
    dynOptions :
        steps : 15000
        highTemp : 5000.0
        dfreeSteps : 0
        cffSteps : 5000
    force :
        tors : 0.2
        dih : 10
        irp : -0.2
        stack : 0.3
'''
    yaml = Yaml()
    data = yaml.load(input)
    return data


class TestStructGen(unittest.TestCase):
    def testRNALinks(self):
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
        energy = refiner.energy()
        self.assertLess(energy, 1.0)

if __name__ == '__main__':
    testProgram = unittest.main(exit=False)
    result = testProgram.result

