from org.nmrfx.structure.chemistry.io import NMRStarReader
from java.io import File

def read(fileName):
    file = File(fileName)
    NMRStarReader.read(file)
