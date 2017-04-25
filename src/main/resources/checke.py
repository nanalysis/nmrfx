import sys
import glob
import os.path
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.structure.chemistry import SuperMol
from org.nmrfx.structure.chemistry.energy import AtomEnergyProp
from org.nmrfx.structure.chemistry.io import PDBFile
from refine import *
homeDir =  os.getcwd( )
outDir = os.path.join(homeDir,'analysis')

site.addsitedir(homeDir)

if not os.path.exists(outDir):
    os.mkdir(outDir)

dataDir = homeDir + '/'

def setupFile(fileName):
    print 'setup',fileName
    refiner = refine()
    refiner.readPDBFile(fileName)
    refiner.addDistanceFile('distances',mode='cyana')
    seed = 1
    refiner.setup(homeDir,seed)
    refiner.outDir = outDir
    return refiner

def loadPDBModels(files):
    iFile = 1
    refiner = setupFile(files[0])
    pdb = PDBFile()
    outFiles = []
    for file in files:
        pdb.readCoordinates(file,0,False)
        refiner.setPars(coarse=False,useh=True,dislim=5.0,end=10000,hardSphere=0.0,shrinkValue=0.0,shrinkHValue=0.00)
        refiner.setForces(repel=2.0,dis=1,dih=5,irp=0.001)

        energy = refiner.energy()
        outFile = os.path.join(outDir,'output'+str(iFile)+'.txt')
        outFiles.append(outFile)
        refiner.dump(0.1,outFile)
        iFile += 1
    return outFiles
#Rep:   .A:412.N   .A:413.N  2.80   0.005  2.70  0.10

class Viol:
    def __init__(self,struct,bound,viol):
        self.struct = struct
        self.bound = bound
        self.viol = viol

def analyzeViols(nStruct,viols):
    sum = 0.0
    structIndicators = [' ']*nStruct
    max = 0.0
    bound = viols[0].bound
    for viol in viols:
        sum += viol.viol
        structIndicators[viol.struct]='+'
        if abs(viol.viol) > max:
            max = viol.viol
    mean = sum / nStruct
    return len(viols),bound,mean,max,''.join(structIndicators)

def summary(outFiles):
    iFile = 0
    viols = {}
    viols['Rep:'] = {}
    viols['Dis:'] = {}
    for outFile in outFiles:
        f1 = open(outFile,'r')
        for line in f1:
            line = line.strip()
            fields = line.split()
            if fields[0] == 'Rep:' or fields[0] == 'Dis:':
                type = fields[0]
                atoms = fields[1]+'_'+fields[2]
                if not atoms in viols[type]:
                    viols[type][atoms] = []
                viol = Viol(iFile,float(fields[3]),float(fields[-1]))
                viols[type][atoms].append(viol)
                #print fields
        iFile += 1
    nStruct = len(outFiles)
    for type in ['Dis:','Rep:']:
        for atoms in viols[type]:
            #sum
            structIndicators = [' ']*nStruct
            (nViol,bound,mean,max,structIndicators) = analyzeViols(nStruct,viols[type][atoms])
            if (nViol > 2):
                atom1,atom2 = atoms.split("_")
                print "   %3s %16s - %16s %2d %.2f %.2f %.2f %s" % (type,atom1,atom2,nViol,bound,mean,max,structIndicators)

