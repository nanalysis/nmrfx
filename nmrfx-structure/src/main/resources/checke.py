import sys
import os
import glob
import os.path
import argparse
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.structure.chemistry.energy import EnergyLists
from org.nmrfx.structure.chemistry import SuperMol
from org.nmrfx.chemistry import AtomEnergyProp
from org.nmrfx.chemistry.io import PDBFile
import molio
from refine import *
from osfiles import writeLines
from osfiles import getFileName

homeDir =  os.getcwd( )
global outDir;

def setupFile(fileName,disFile=''):

    refiner = refine()
    molecule = molio.readPDB(fileName)
    if disFile != '':
        refiner.addDistanceFile(disFile,mode='NV')
    else:
        disFile = 'distances'
        refiner.addDistanceFile(disFile,mode='cyana')

    seed = 0
    refiner.setup(homeDir,seed)

    refiner.outDir = outDir
    refiner.energy()
    return refiner

def loadPDBModels(files, yaml, out):
    global outDir
    outDir = out
    outDir += '/'

    iFile = 1
    refiner = refine()
    refiner.loadFromYaml(yaml,0,fileName=files[0])

    if not os.path.exists(outDir):
        os.mkdir(outDir)
    pdb = PDBFile()
    referenceFile = outDir + '/referenceFile.txt'

    outFiles = []
    data = []
    for file in files:
        outFile = os.path.join(outDir,'output'+str(iFile)+'.txt')
        mol = Molecule.getActive()
        pdb.readCoordinates(mol, file,0,False, True)
        refiner.setPars({'coarse':False,'useh':True,'dislim':4.6,'end':10000,'hardSphere':0.0,'shrinkValue':0.0, 'shrinkHValue':0.0})

        refiner.setForces(yaml['anneal']['force'])

        refiner.energy()

        inFileName=getFileName(file)
        outFileName=getFileName(outFile)

        datum = [inFileName,outFileName]
        refiner.molecule.updateVecCoords()
        refiner.molecule.getEnergyCoords().updateNOEPairs()
        distanceEnergy=refiner.molecule.getEnergyCoords().energy()
        datum.append("%.1f" % (distanceEnergy))
        if ("shifts" in yaml):
            shiftEnergy = refiner.energyLists.calcShift(False)
            datum.append("%.1f" % (shiftEnergy))

        data.append(datum)

        refiner.dump(0.1,.20,outFile)
        outFiles.append(outFile)
        iFile += 1
    data.sort(key=lambda x: x[0])
    header = ['PDB File Name','Output File']
    header.append('Dis Viol')
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
    structIndicators = ['.']*nStruct
    max = 0.0
    bound = viols[0].bound
    nViol = 0
    for viol in viols:
        aViol = abs(viol.viol)
        sum += aViol
        if aViol > abs(max):
            max = viol.viol
        if aViol > limit:
            structIndicators[viol.struct]='+'
            nViol += 1
    mean = sum / nStruct
    return nViol,bound,mean,max,''.join(structIndicators)

def summary(outFiles=[],limit=0.2, minN=2):
    global outDir
    if len(outFiles) == 0:
        outFiles = glob.glob(os.path.join(outDir,'final','final*.txt'))
        summaryFile = os.path.join(outDir,'final','analysis.txt')
    else:
        summaryFile = os.path.join(outDir,'analysis.txt')
    iFile = 0
    viols = {}
    viols['Rep:'] = {}
    viols['Dis:'] = {}
    viols['Shi:'] = {}
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
            elif fields[0] == 'Shi:':
                type = fields[0]
                atoms = fields[1]
                if not atoms in viols[type]:
                    viols[type][atoms] = []
                viol = Viol(iFile,float(fields[2]),float(fields[-2]))
                viols[type][atoms].append(viol)
        iFile += 1
    nStruct = len(outFiles)
    outLine =  "  %4s %16s   %16s %4s %10s %10s %10s Structures\n" % ("Type","Atom1","Atom2","nViol","Bound","Mean","Max")
    fOut.write(outLine)
    for type in ['Dis:','Rep:','Shi:']:
        for atoms in viols[type]:
            #sum
            structIndicators = [' ']*nStruct
            (nViol,bound,mean,max,structIndicators) = analyzeViols(nStruct,viols[type][atoms],limit)
            if (nViol > minN):
                if type=="Shi:":
                    atom1 = atoms
                    outLine =  "   %3s %16s - %16s %4d %10.2f %10.2f %10.2f " % (type,atom1,"",nViol,bound,mean,max)
                else:
                    atom1,atom2 = atoms.split("_")
                    outLine =  "   %3s %16s - %16s %4d %10.2f %10.2f %10.2f " % (type,atom1,atom2,nViol,bound,mean,max)
                fOut.write(outLine)
                if nStruct <= 100:
                    fOut.write('|'+structIndicators+'|')
                fOut.write('\n')
    fOut.close()


def parseArgs():
    parser = argparse.ArgumentParser(description="summary options")
    parser.add_argument("-l", dest="limDis", default=0.2, help="Only violations with an error of at least this amount will be reported (0.2)")
    parser.add_argument("-n", dest="nViols", default=2, help="Only violations that occur in at least this number of structures will be reported (2).")
    parser.add_argument("fileNames",nargs="*")

    args = parser.parse_args()

    summary(args.fileNames, float(args.limDis), int(args.nViols))
