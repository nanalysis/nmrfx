import sys
from refine import *
import os
import osfiles
import runpy
import rnapred
import molio
from org.yaml.snakeyaml import Yaml
from java.io import FileInputStream
from org.nmrfx.structure.chemistry import ProteinPredictor



homeDir =  os.getcwd( )
dataDir = homeDir + '/'
outDir = os.path.join(homeDir,'output')

argFile = sys.argv[-1]

def predictProtein(mol, tableMode=False):
    polymers = mol.getPolymers()
    if tableMode:
        for atomName in ('N','CA','CB','C','H','HA(2)','HA3'):
            print atomName,
        print ""
    for polymer in polymers:
        predictor = ProteinPredictor(polymer)
        for residue in polymer.iterator():
            if tableMode:
                print residue.getNumber(), residue.getName(),
                for atomName in ('N','CA','CB','C','H','HA','HA3'):
                    if residue.getName() == "GLY" and atomName == 'HA':
                        atomName = 'HA2'
                    atom = residue.getAtom(atomName)
                    if atom == None:
                        print "  _   ",
                    else:
                        value = predictor.predict(residue.getAtom(atomName), False)
                        if value != None:
                            valueStr = "%6.2f" % (value)
                            print valueStr,
                        else:
                            print "  _   ",
                print ""
            else:
                for atomName in ('N','CA','CB','C','H','HA','HA3'):
                    if residue.getName() == "GLY" and atomName == 'HA':
                        atomName = 'HA2'
                    atom = residue.getAtom(atomName)
                    if atom != None:
                        value = predictor.predict(residue.getAtom(atomName), False)
                        if value != None:
                            valueStr = "%s.%s %.2f" % (residue.getNumber(),atomName,value)
                            print valueStr




def isRNA(mol):
    polymers = mol.getPolymers()
    rna = False
    for polymer in polymers:
        for residue in polymer.getResidues():
            resName = residue.getName()
            if resName in ["A","C","G","U"]:
                rna = True
                break
    return rna
                



if argFile.endswith('.yaml'):
    input = FileInputStream(argFile)
    yaml = Yaml()
    data = yaml.load(input)

    refiner=refine()
    osfiles.setOutFiles(refiner,dataDir, 0)
    refiner.rootName = "temp"
    refiner.loadFromYaml(data,0)
    mol = refiner.molecule
    vienna = data['rna']['vienna']
    mol.setDotBracket(vienna)
    #rnapred.predictFromSequence(mol,vienna)
    rnapred.predictFromSequence()
    rnapred.dumpPredictions(mol)
elif argFile.endswith('.pdb'):
    mol = molio.readPDB(argFile)
    # fixme, need to do this by polymer so you can have protein-rna complex
    if isRNA(mol):
        refiner=refine()
        shifts = refiner.predictShifts()
        for atomShift in shifts:
            aname,shift = atomShift
            print aname,shift
    else:
        predictProtein(mol)
