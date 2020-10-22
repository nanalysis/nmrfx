from org.nmrfx.structure.chemistry.io import NMRStarReader
from org.nmrfx.structure.chemistry.io import NMRStarWriter
from java.io import File

def read(fileName):
    file = File(fileName)
    star3 = NMRStarReader.read(file)
    return star3

def write(fileName):
    NMRStarWriter.writeAll(fileName)
