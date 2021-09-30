from org.nmrfx.chemistry.io import MMcifReader
#from org.nmrfx.chemistry.io import MMcifWriter
from java.io import File

def read(fileName):
    #file = File(fileName)
    return MMcifReader(fileName)

#def write(fileName):
#    MMcifWriter.writeAll(fileName)
