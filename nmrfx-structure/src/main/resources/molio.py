from org.nmrfx.chemistry.io import PDBFile
from org.nmrfx.chemistry.io import SDFile
from org.nmrfx.chemistry.io import Mol2File
from org.nmrfx.chemistry.io import Sequence
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.chemistry import MoleculeFactory
from org.nmrfx.chemistry.io import MMcifReader

from java.util import ArrayList
from java.lang import ClassLoader
from java.io import BufferedReader
from java.io import InputStreamReader



def updateAtomArray(mol=None):
    ''' Updates the molecule atom array '''
    if mol == None:
        mol = MoleculeFactory.getActive()
    mol.updateAtomArray()

def readMMCIF(fileName):
    ''' Reads a pdb file and modifies the static Molecule object.
    '''
    compound = None
    mol = MMcifReader.read(fileName)
    updateAtomArray(mol)
    return mol

def readPDB(fileName, isCompound = False, iStruct=0):
    ''' Reads a pdb file and modifies the static Molecule object.
        isCompound is used to specify if the file should be read in
        as a ligand or small molecule.
        Important note to take into consideration:
           if isCompound is false, HETATM fields will be ignored in file and
           the file will be read in as a sequence, ultimately creating
           polyer(s)

        This command returns either None or a compound depending on whether
        the isCompound is true.
    '''
    compound = None
    pdb = PDBFile()
    if not isCompound:
        mol = pdb.readSequence(fileName, iStruct)
        updateAtomArray(mol)
        return mol
    else:
        cmpd = pdb.readResidue(fileName, None, MoleculeFactory.getActive(), None)
        updateAtomArray(cmpd.molecule)
        return cmpd

def readPDBX(fileName, isCompound = False):
    ''' Reads a pdb file and modifies the static Molecule object.
        isCompound is used to specify if the file should be read in
        as a ligand or small molecule.
        Important note to take into consideration:
           if isCompound is false, HETATM fields will be ignored in file and
           the file will be read in as a sequence, ultimately creating
           polyer(s)

        This command returns either None or a compound depending on whether
        the isCompound is true.
    '''
    compound = None # FIXME: This parameter is not used
    pdb = PDBFile()
    mol = pdb.read(fileName)
    updateAtomArray(mol)
    return mol

def readPDBXCoords(fileName, structNum, noComplain, genCoords):
    ''' Reads a pdb file and modifies the static Molecule object.
        structNum is the structure number, noComplain is a boolean for
        printing out an error message, and genCoords is a boolean for 
        generating coordinates.
    '''

    mol = MoleculeFactory.getActive()
    pdb = PDBFile()
    pdb.readCoordinates(mol, fileName, structNum, noComplain, genCoords)
    MoleculeFactory.setActive(mol)
    updateAtomArray(mol)
    return mol

def readSDF(fileName, newMolecule = False):
    sdf = SDFile()
    molecule = MoleculeFactory.getActive() if not newMolecule else None
    compound = sdf.read(fileName, None, molecule, None)
    updateAtomArray(compound.molecule)
    MoleculeFactory.setActive(compound.molecule)
    return compound

def readMol2(fileName, newMolecule = False):
    mol2 = Mol2File()
    molecule = MoleculeFactory.getActive() if not newMolecule else None
    compound = mol2.read(fileName, None, molecule, None)
    MoleculeFactory.setActive(compound.molecule)
    updateAtomArray(compound.molecule)
    return compound

def readSequenceString(polymerName, sequence, seqReader=None):
    ''' Creates a polymer from the sequence provided with the name of polymerName
        The sequence input can either be a chain of characters but will only work
        if the desired polymer is RNA. If creating a polymer for a protein,
        sequence must be a list using the 3 letter code.
    '''
    seqAList = ArrayList()
    seqAList.addAll(sequence)
    if (seqReader == None):
        seqReader = Sequence()
    seqReader.newPolymer()
    mol = seqReader.read(polymerName, seqAList, "")
    MoleculeFactory.setActive(mol)
    updateAtomArray(mol)
    return mol


def readSequence(seqFile, convert=False, polymerName=None,seqReader=None):
    if convert:
        import os
        import osfiles
        dir = os.path.dirname(seqFile)
        seqFile = osfiles.convertSeqFile(seqFile,dir)
    if (seqReader == None):
        seqReader = Sequence()
    seqReader.newPolymer()
    mol = seqReader.read(seqFile, polymerName) if polymerName else seqReader.read(seqFile)
    MoleculeFactory.setActive(mol)
    updateAtomArray(mol)
    return mol

def readYaml(file):
    from java.io import FileInputStream
    from org.yaml.snakeyaml import Yaml

    input = FileInputStream(file)
    yaml = Yaml()
    data = yaml.load(input)
    return data

def readYamlString(yamlString):
    from org.yaml.snakeyaml import Yaml

    yaml = Yaml()
    data = yaml.load(yamlString)
    return data



def loadResource(resourceName):
    cl = ClassLoader.getSystemClassLoader()
    istream = cl.getResourceAsStream(resourceName)
    lines = ""
    if istream == None:
        raise Exception("Cannot find '" + resourceName + "' on classpath")
    else:
        reader = InputStreamReader(istream)
        breader = BufferedReader(reader)
        while True:
            line = breader.readLine()
            if line == None:
                break
            if lines != '':
                lines += '\n'
            lines += line
        breader.close()
    return lines

def savePDB(molecule, fileName,structureNum=0):
    molecule.writeXYZToPDB(fileName, structureNum)

def readResiduePairCSV(csvFile=None):
    if csvFile:
        import os
        if os.path.isfile(csvFile):
            loadResource(csvFile)
            
