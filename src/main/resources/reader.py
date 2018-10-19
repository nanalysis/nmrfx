from org.nmrfx.structure.chemistry.io import PDBFile
from org.nmrfx.structure.chemistry.io import Sequence
def updateAtomArray():
    ''' Updates the molecule atom array '''
    mol = Molecule.getActive()
    mol.updateAtomArray()

def readPDB(fileName, isCompound = False):
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
        pdb.readSequence(fileName,0)
    else:
        compound = pdb.readResidue(fileName, None, Molecule.getActive(), None)
    updateAtomArray()
    return compound
def readSequence(seqFile):
    seqReader = Sequence()
    seqReader.read(seqFile)
    updateAtomArray()
