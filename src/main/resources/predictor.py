import sys
from refine import *
import os
import osfiles
import runpy
import rnapred
import molio
from org.yaml.snakeyaml import Yaml
from java.io import FileInputStream
from org.nmrfx.structure.chemistry.predict import ProteinPredictor
from org.nmrfx.processor.datasets import Nuclei

homeDir =  os.getcwd( )
dataDir = homeDir + '/'
outDir = os.path.join(homeDir,'output')

argFile = sys.argv[-1]

def predictProtein(mol, tableMode=False):
    pred=ProteinPredictor()
    pred.init(mol)
    pred.predict(-1)
    dumpPredictions(mol, tableMode)

def dumpPredictions(mol, tableMode=False):
    polymers = mol.getPolymers()
    if tableMode:
        for atomName in ('N','CA','CB','C','H','HA(2)','HA3'):
            print atomName,
        print ""
    iAtom = 1
    iAtom = 1
    iEntity = 1
    iRes = 1
    for polymer in polymers:
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
                        value = residue.getAtom(atomName).getRefPPM()
                        if value != None:
                            valueStr = "%6.2f" % (value)
                            print valueStr,
                        else:
                            print "  _   ",
                print ""
            else:
                atoms = residue.getAtoms()
                for atom in atoms:
                    atomName = atom.getName()
                    value = atom.getRefPPM()
                    valueErr = atom.getSDevRefPPM()
                    if value != None:
                        #valueStr = "%s.%s %.2f %.2f" % (residue.getNumber(),atomName,value,valueErr)
                        elemName = atom.getElementName()
                        nuclei = Nuclei.findNuclei(elemName)
                        isotope = nuclei.getNumber()
                        valueStr = "     %5d . %1d %1d %3d %3s %3s %-4s %1s %2s %7.3f %5.3f . 1 . . . . %3s %3s %-4s .    . 1" % (iAtom,iEntity,iEntity,iRes, residue.getNumber(),residue.getName(),atomName,elemName,isotope,value,valueErr,residue.getNumber(), residue.getName(),atomName)
                        print valueStr
                        iAtom += 1
            iRes += 1
        iEntity += 1

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
elif argFile.endswith('.pdb') or argFile.endswith('.cif'):
    if argFile.endswith('.pdb'):
        mol = molio.readPDB(argFile)
    elif argFile.endswith('.cif'):
        mol = molio.readMMCIF(argFile)
    else:
        print 'Invalid file type'
        exit(1)

    # fixme, need to do this by polymer so you can have protein-rna complex
    if isRNA(mol):
        refiner=refine()
        shifts = refiner.predictRNAShifts()
        dumpPredictions(mol)
    else:
        predictProtein(mol)
