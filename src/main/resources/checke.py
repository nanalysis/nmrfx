import sys
import os
import glob
import os.path
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.structure.chemistry.energy import EnergyLists
from org.nmrfx.structure.chemistry import SuperMol
from org.nmrfx.structure.chemistry.energy import AtomEnergyProp
from org.nmrfx.structure.chemistry.io import PDBFile
from refine import *
from osfiles import writeLines
from osfiles import getFileName

homeDir =  os.getcwd( )
global outDir;

def setupFile(fileName,**op):
        
    refiner = refine()
    molecule = refiner.readPDBFile(fileName)

    if 'disFile' in op and op['disFile'] != None:
        disFile = op['disFile']
        refiner.addDistanceFile(disFile,mode='NV')
    else:
        disFile = 'distances'
        refiner.addDistanceFile(disFile,mode='cyana')

    seed = 1
    refiner.setup(homeDir,seed)

    if 'addRibose' in op:
        if op['addRibose']:
            polymers = molecule.getPolymers()
            for polymer in polymers:
                refiner.addRiboseRestraints(polymer)
    refiner.outDir = outDir
    refiner.energy()
    return refiner

def loadPDBModels(files, *pp, **op):
    global outDir
    if 'dataFiles' in op:
        disFile = op['dataFiles']['disFile']
        shiftFile = op['dataFiles']['shiftFile']
        outDir = op['dataFiles']['outDir']
  
    outDir = os.path.join(homeDir,outDir)
    if not os.path.exists(outDir):
        os.mkdir(outDir)
    iFile = 1    
    refiner = setupFile(files[0],disFile=disFile,addRibose=True)
    pdb = PDBFile()
    referenceFile = outDir + '/referenceFile.txt'

    if shiftFile != None:
        refiner.setShifts(shiftFile)

    outFiles = []
    data = []
    for file in files:
        outFile = os.path.join(outDir,'output'+str(iFile)+'.txt')
        
        pdb.readCoordinates(file,0,False)
        refiner.setPars(coarse=False,useh=True,dislim=5.0,end=10000,hardSphere=0.0,shrinkValue=0.0,shrinkHValue =0.00)
        
        if shiftFile != None:
            refiner.energyLists.setRingShifts()
            refiner.setForces(repel=2.0,dis=1,dih=-1,irp=0.001, shift=1.0)
        else:
            refiner.setForces(repel=2.0,dis=1,dih=-1,irp=0.001, shift=-1)
        refiner.energy()
        
        inFileName=getFileName(file)
        outFileName=getFileName(outFile)
        datum = [inFileName,outFileName]
        
        if disFile != None:
            distanceEnergy = refiner.molecule.getEnergyCoords().calcNOE(False,1.0)
            datum.append(distanceEnergy)
        
        if shiftFile != None:
            shiftEnergy = refiner.energyLists.calcShift(False)
            datum.append(shiftEnergy)
        
        data.append(datum)

        refiner.dump(0.1,.20,outFile)

        outFiles.append(outFile)
        iFile += 1
    data.sort(key=lambda x: x[0])
    header = ['PDB File Name','Output File']
    if disFile != None:
        header.append('Dis Viol')
    if shiftFile != None:
        header.append('Shift Viol')
    writeLines(data,referenceFile, header)
    return outFiles

class Viol:
    def __init__(self,struct,bound,viol):
        self.struct = struct
        self.bound = bound
        self.viol = viol

def analyzeViols(nStruct,viols,limit):
    sum = 0.0
    structIndicators = [' ']*nStruct
    max = 0.0
    bound = viols[0].bound
    nViol = 0
    for viol in viols:
        sum += viol.viol
        aViol = abs(viol.viol)
        if aViol > max:
            max = viol.viol
        if aViol > limit:
            structIndicators[viol.struct]='+'
            nViol += 1
    mean = sum / nStruct
    return nViol,bound,mean,max,''.join(structIndicators)

def summary(outFiles,limit=0.2):
    global outDir
    iFile = 0
    viols = {}
    viols['Rep:'] = {}
    viols['Dis:'] = {}
    viols['Shi:'] = {}
    summaryFile = os.path.join(outDir,'analysis.txt')
    fOut = open(summaryFile,'w')
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
                viol = Viol(iFile,float(fields[3]),float(fields[-2]))
                viols[type][atoms].append(viol)
                #print fields
            elif fields[0] == 'Shi:':
                type = fields[0]
                atoms = fields[1]
                if not atoms in viols[type]:
                    viols[type][atoms] = []
                viol = Viol(iFile,float(fields[2]),float(fields[-2]))
                viols[type][atoms].append(viol)
        iFile += 1
    nStruct = len(outFiles)
    for type in ['Dis:','Rep:','Shi:']:
        for atoms in viols[type]:
            #sum
            structIndicators = [' ']*nStruct
            (nViol,bound,mean,max,structIndicators) = analyzeViols(nStruct,viols[type][atoms],limit)
            if (nViol > 2):
                if type=="Shi:":
                    atom1 = atoms
                    outLine =  "   %3s %16s - %16s %4d %10.2f %10.2f %10.2f" % (type,atom1,"",nViol,bound,mean,max)
                else:
                    atom1,atom2 = atoms.split("_")
                    outLine =  "   %3s %16s - %16s %4d %10.2f %10.2f %10.2f" % (type,atom1,atom2,nViol,bound,mean,max)
                fOut.write(outLine)
                if nStruct <= 100:
                    fOut.write(structIndicators)
                fOut.write('\n')
    fOut.close()

