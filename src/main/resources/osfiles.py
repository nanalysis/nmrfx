import os
import os.path
import glob

def getDataDir(homeDir):
    if homeDir == None:
        homeDir =  os.getcwd( )
    return homeDir


def guessFiles(refiner,homeDir):
    if homeDir == None:
        homeDir =  os.getcwd( )

    seqFile = os.path.join(homeDir,'sequence.seq')
    if os.path.exists(seqFile):
        refiner.readSequence(seqFile)
    else:
        pdbFile = os.path.join(homeDir,'*.pdb')
        pdbFiles = glob.glob(pdbFile)
        if (len(pdbFiles) > 0):
            print pdbFiles[0]
            refiner.readPDBFile(pdbFiles[0])

    angleFiles = glob.glob(os.path.join(homeDir,'dihedral*.tbl'))
    for file in angleFiles:
        print file
        refiner.addAngleFile(file,'nv')
    disFiles = glob.glob(os.path.join(homeDir,'distance*.tbl'))
    for file in disFiles:
        print file
        refiner.addDistanceFile(file,'nv')

    angleFiles = glob.glob(os.path.join(homeDir,'dihedral*.txt'))
    for file in angleFiles:
        refiner.addAngleFile(file,'cyana')
    disFiles = glob.glob(os.path.join(homeDir,'*.upl'))
    for file in disFiles:
        file = file[:-4]
        refiner.addDistanceFile(file,'cyana')


def setOutFiles(refiner,homeDir, seed):
    refiner.outDir = os.path.join(homeDir,'output')
    refiner.eFileRoot = os.path.join(refiner.outDir,'energyDump'+str(seed))

def getAngleFile(refiner):
   angleFile = os.path.join(refiner.outDir,refiner.rootName+str(refiner.seed)+'.ang')
   return angleFile

def getPDBFile(refiner):
   pdbFile = os.path.join(refiner.outDir,refiner.rootName+str(refiner.seed)+'.pdb')
   return pdbFile

def logEnergy(refiner, energyStr):
    energyFile = open(os.path.join(refiner.outDir,"energies.txt"),"a")
    energyFile.write(energyStr)
    energyFile.close()

def getEnergyFile(refiner,mode=None):
   if mode == None:
       energyFile = os.path.join(refiner.outDir,refiner.rootName+str(refiner.seed)+'.txt')
   else:
       energyFile = os.path.join(refiner.outDir,refiner.eFileRoot+'_'+mode+'.txt')
   return energyFile
