import sys
from refine import *
import os
import osfiles
import argparse
import runpy
import rnapred
import molio
from org.yaml.snakeyaml import Yaml
from java.io import FileInputStream
from org.nmrfx.structure.chemistry.predict import ProteinPredictor
from org.nmrfx.structure.rna import RNAAnalysis
from org.nmrfx.structure.chemistry.predict import Predictor
from org.nmrfx.datasets import Nuclei

homeDir =  os.getcwd( )
dataDir = homeDir + '/'
outDir = os.path.join(homeDir,'output')

argFile = sys.argv[-1]

tags = '''    _Atom_chem_shift.ID
    _Atom_chem_shift.Assembly_atom_ID
    _Atom_chem_shift.Entity_assembly_ID
    _Atom_chem_shift.Entity_ID
    _Atom_chem_shift.Comp_index_ID
    _Atom_chem_shift.Seq_ID
    _Atom_chem_shift.Comp_ID
    _Atom_chem_shift.Atom_ID
    _Atom_chem_shift.Atom_type
    _Atom_chem_shift.Atom_isotope_number
    _Atom_chem_shift.Val
    _Atom_chem_shift.Val_err
    _Atom_chem_shift.Assign_fig_of_merit
    _Atom_chem_shift.Ambiguity_code
    _Atom_chem_shift.Occupancy
    _Atom_chem_shift.Resonance_ID
    _Atom_chem_shift.Auth_entity_assembly_ID
    _Atom_chem_shift.Auth_asym_ID
    _Atom_chem_shift.Auth_seq_ID
    _Atom_chem_shift.Auth_comp_ID
    _Atom_chem_shift.Auth_atom_ID'''


tags2 = '''    _Atom_chem_shift.Details
    _Atom_chem_shift.Entry_ID
    _Atom_chem_shift.Assigned_chem_shift_list_ID
'''

def predictRNA(mol, outputMode="star"):
    refiner=refine()
    shifts = refiner.predictRNAShifts()
    dumpPredictions(mol)


def predictProtein(mol, iStruct, outputMode="star"):
    pred=ProteinPredictor()
    pred.init(mol, iStruct)
    pred.predict(-1, iStruct)
    dumpPredictions(mol, outputMode)

def dumpLigandPredictions(mol, location=-1):
    ligands = mol.getLigands()
    for ligand in ligands:
        for atom in ligand.getAtoms():
            atomName = atom.getName()
            if (location < 0):
                value = ligand.getAtom(atomName).getRefPPM(-location-1)
            else:
                value = ligand.getAtom(atomName).getPPM(location)
            if value != None and value.isValid():
                value = value.getValue()
                valueErr = atom.getSDevRefPPM()
                valueStr = "%5s %.2f" % (atomName,value)
                print valueStr


def dumpPredictions(mol, location=-1, outputMode="star", selected=False, header=False, model=-1):
    if outputMode == 'star' and header: 
        #tags = tags.split()
        print tags
        if model != -1:
            print "    _Atom_chem_shift.PDB_model_num"
        print tags2

    polymers = mol.getPolymers()
    if outputMode == "protein":
        resStr = "%4s %7s" % ("Num","Name")
        print resStr,
        for atomName in ('N','CA','CB','C','H','HA(2)','HA3'):
            valueStr = "%6s" % atomName
            print valueStr,
        print ""
    iAtom = 1
    iAtom = 1
    iEntity = 1
    iRes = 1
    for polymer in polymers:
        for residue in polymer.iterator():
            if outputMode == "protein":
                resStr = "%4s %7s" % (residue.getNumber(), residue.getName())
                print resStr,
                for atomName in ('N','CA','CB','C','H','HA','HA3'):
                    if residue.getName() == "GLY" and atomName == 'HA':
                        atomName = 'HA2'
                    atom = residue.getAtom(atomName)
                    if atom == None:
                        print "  _   ",
                    else:
                        if (location < 0):
                            value = residue.getAtom(atomName).getRefPPM(-location-1)
                        else:
                            value = residue.getAtom(atomName).getPPM(location)
                        if value != None and value.isValid():
                            valueStr = "%6.2f" % (value.getValue())
                            print valueStr,
                        else:
                            print "  _   ",
                print ""
            else:
                atoms = residue.getAtoms()
                for atom in atoms:
                    if selected and not atom.getSelected():
                        continue
                    atomName = atom.getName()
                    if (location < 0):
                        value = residue.getAtom(atomName).getRefPPM(-location-1)
                    else:
                        value = residue.getAtom(atomName).getPPM(location)
                    if value != None and value.isValid():
                        value = value.getValue()
                        valueErr = atom.getSDevRefPPM()
                        #valueStr = "%s.%s %.2f %.2f" % (residue.getNumber(),atomName,value,valueErr)
                        elemName = atom.getElementName()
                        nuclei = Nuclei.findNuclei(elemName)
                        isotope = nuclei.getNumber()
                        if model != -1:
                            valueStr = "     %5d . %1d %1d %3d %3s %3s %-4s %1s %2s %7.3f %5.3f . 1 . . . . %3s %3s %-4s %5d .    . 1" % (iAtom,iEntity,iEntity,iRes, residue.getNumber(),residue.getName(),atomName,elemName,isotope,value,valueErr,residue.getNumber(), residue.getName(),atomName, model)
                        else:
                            valueStr = "     %5d . %1d %1d %3d %3s %3s %-4s %1s %2s %7.3f %5.3f . 1 . . . . %3s %3s %-4s .    . 1" % (iAtom,iEntity,iEntity,iRes, residue.getNumber(),residue.getName(),atomName,elemName,isotope,value,valueErr,residue.getNumber(), residue.getName(),atomName )
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
                
def predictRNAWithYaml(fileName, location, outputMode, predMode):
    input = FileInputStream(fileName)
    yaml = Yaml()
    data = yaml.load(input)

    refiner=refine()
    osfiles.setOutFiles(refiner,dataDir, 0)
    refiner.rootName = "temp"
    refiner.loadFromYaml(data,0)
    mol = refiner.molecule
    vienna = data['rna']['vienna']

    predictRNAWithAttributes(mol, vienna, location, outputMode, predMode)


def predictRNAWithStructureAttributes(fileName, location = -1, outputMode="star", predMode="dist"):
    if fileName.endswith('.pdb'):
        mol = molio.readPDB(fileName)
    elif fileName.endswith('.cif'):
        mol = molio.readMMCIF(fileName)
    else:
        print 'Invalid file type'
        exit(1)
    if not isRNA(mol):
        print 'Molecule not RNA'
        exit(1)

    vienna = RNAAnalysis.getViennaSequence(mol)
    viennaStr = ""
    for char in vienna:
        viennaStr += char
    predictRNAWithAttributes(mol, viennaStr, location, outputMode, predMode)

def predictRNAWithAttributes(mol, vienna, location = -1, outputMode="star", predMode="dist"):
    rnapred.predictFromSequence(mol, vienna, location)
    if outputMode == 'attr':
        rnapred.dumpPredictions(mol, location)
    else:
        dumpPredictions(mol, location, outputMode)

def predictWithStructure(fileName, xMode=False, iStruct=0, model=0, location = -1, outputMode="star", predMode="dist"):
    if fileName.endswith('.pdb'):
        if (xMode):
            mol = molio.readPDBX(fileName)
            print 'read all coords'
            molio.readPDBXCoords(fileName, -1, True, False)
        else:
            mol = molio.readPDB(fileName, iStruct=iStruct)
    elif fileName.endswith('.cif'):
        mol = molio.readMMCIF(fileName)
    elif fileName.endswith('.sdf'):
        cmpd = molio.readSDF(fileName)
        mol = cmpd.molecule
    elif fileName.endswith('.mol'):
        cmpd = molio.readSDF(fileName)
        mol = cmpd.molecule
    else:
        print 'Invalid file type'
        exit(1)

    pred=Predictor()
    pred.predictMolecule(mol, model, location, predMode == 'rc')
    dumpPredictions(mol, location, outputMode)
    dumpLigandPredictions(mol, location)

def parseArgs():
    parser = argparse.ArgumentParser(description="predictor options")
    parser.add_argument("-x", dest="xMode", default=False, action="store_true", help="Whether to read coordinates without library (False")
    parser.add_argument("-l", dest="location", default=-1, type=int,  help="Location to store prediction in.  Values less than one go into 'reference' locations. (-1)")
    parser.add_argument("-s", dest="iStruct", default=0, type=int,  help="Model to read.  0 reads first, -1 reads all, other values read specified model (0)")
    parser.add_argument("-m", dest="model", default=0, type=int,  help="Model to use for coordinates (0)")
    parser.add_argument("-r", dest="rnaPredMode", default="dist", help="Prediction mode used for rna prediction.  Can be dist, rc or attr.")
    parser.add_argument("-o", dest="outputMode", default="star", help="Output mode.  Can be star, protein or attr.  If set to star the output will be a portion of a BMRB chemical shift save frame.  If protein it will be a table of backbone protein shifts.  Mode attr is only appropriate to RNA predictions done with attributes.")
    parser.add_argument("fileNames",nargs="*")
    args = parser.parse_args()
    for fileName in args.fileNames:
        if fileName.endswith('.yaml'):
             predictRNAWithYaml(fileName, args.location, args.outputMode, args.rnaPredMode)
        else:
             if args.rnaPredMode == "attr":
                 predictRNAWithStructureAttributes(fileName, args.model, args.location, args.outputMode, args.rnaPredMode)
             else:
                 if args.model != args.iStruct:
                     args.iStruct = -1
                 predictWithStructure(fileName, args.xMode, args.iStruct, args.model, args.location, args.outputMode, args.rnaPredMode)
