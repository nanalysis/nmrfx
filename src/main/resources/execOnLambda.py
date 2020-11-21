import refine
import time
from java.io import FileReader
from java.io import BufferedReader
from java.io import InputStreamReader

from org.nmrfx.processor.star import STAR3
from org.nmrfx.chemistry.io import NMRStarReader
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.structure.lambda import LambdaIO

def execOnLambda(nefBucket, pdbBucket, rootName, seed):
    stime = time.time()
    inKey = rootName+'.nef'
    outKey = rootName+'_'+str(seed)+'.pdb'
    energyKey = rootName+'_'+str(seed)+'.txt'

    inputStream = LambdaIO.getInputStream(nefBucket,inKey)
    inputStreamReader = InputStreamReader(inputStream)
    bfR = BufferedReader(inputStreamReader)

    star = STAR3(bfR,'star3')
    star.scanFile()
    reader = NMRStarReader(star)

    dihedral = reader.processNEF()
    energyList = dihedral.energyList

    mol = energyList.getMolecule()
    molName =  mol.getName()

    refiner = refine.refine()
    refiner.molecule = mol
    refiner.seed = seed

    refiner.outDir = None
    refiner.eFileRoot = None

    refiner.eTimeStart = time.time()
    refiner.useDegrees = False

    refiner.setupEnergy(molName,eList=energyList)
    refiner.setForces({'repel':0.5,'dis':1.0,'dih':5})
    refiner.setPars({'coarse':False,'useh':False,'dislim':refiner.disLim,'end':500,'hardSphere':0.15,'shrinkValue':0.20})

    dOpt = refine.dynOptions(highFrac=0.4)
    refiner.anneal(dOpt)

    outputStr = mol.writeXYZToPDBString(0)
    energy = refiner.energy()
    ftime = time.time()
    etime = ftime-stime
    energyStr = 'REMARK ENERGY %7.2f\n' % (energy)
    timeStr = 'REMARK TIME START %26s END %26s ELAPSED %7.2f\n' % (time.ctime(stime), time.ctime(ftime),etime)
    outputStr = energyStr + timeStr + outputStr
    LambdaIO.putObject(pdbBucket, outKey, outputStr)
    energyString = refiner.getEnergyDump(0.1)
    LambdaIO.putObject(pdbBucket, energyKey, energyString)
