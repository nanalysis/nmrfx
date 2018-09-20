import math
import time
import array
import random
import seqalgs
import re
import xplor

from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.structure.chemistry.energy import EnergyLists
from org.nmrfx.structure.chemistry.energy import ForceWeight
from org.nmrfx.structure.chemistry.energy import Dihedral
from org.nmrfx.structure.chemistry.energy import GradientRefinement
from org.nmrfx.structure.chemistry.energy import StochasticGradientDescent
from org.nmrfx.structure.chemistry.energy import CmaesRefinement
#from org.nmrfx.structure.chemistry.energy import FireflyRefinement
from org.nmrfx.structure.chemistry.energy import RNARotamer
from org.nmrfx.structure.chemistry.io import PDBFile
from org.nmrfx.structure.chemistry.io import SDFile
from org.nmrfx.structure.chemistry.io import Sequence
from org.nmrfx.structure.chemistry.io import TrajectoryWriter
from org.nmrfx.structure.chemistry import SSLayout
from org.nmrfx.structure.chemistry.miner import PathIterator
from org.nmrfx.structure.chemistry.miner import NodeValidator
from org.nmrfx.structure.chemistry.energy import AngleTreeGenerator

#from tcl.lang import NvLiteShell
#from tcl.lang import Interp
from java.lang import String
from java.util import ArrayList

#tclInterp = Interp()
#tclInterp.eval("puts hello")
#tclInterp.eval('package require java')
#tclInterp.eval('java::load org.nmrfx.structure.chemistry.ChemistryExt')
PDBFile.putReslibDir('IUPAC','resource:/reslib_iu')

protein3To1 = {"ALA":"A","ASP":"D","ASN":"N","ARG":"R","CYS":"C","GLU":"E","GLN":"Q","ILE":"I",
    "VAL":"V","LEU":"L","PRO":"P","PHE":"F","TYR":"Y","TRP":"W","LYS":"K","MET":"M",
    "HIS":"H","GLY":"G","SER":"S","THR":"T"}

bondOrders = ('SINGLE','DOUBLE','TRIPLE','QUAD')

protein1To3 = {}
for key in protein3To1:
   protein1To3[protein3To1[key]] = key


def tcl(cmd):
    global tclInterp
    #runs the file using tcl interpreter
    tclInterp.eval(cmd)
    #returns string value of the object
    return tclInterp.getResult().toString()

def openTclChannel(fileName,mode):
    chanName = tcl('open '+fileName+' '+mode)
    return chanName

def closeTclChannel(handle):
    tcl('close '+handle)

def savePDB(molecule, fileName,structureNum=0):
    molecule.writeXYZToPDB(fileName, structureNum)

def getHelix(pairs,vie):
    inHelix = False
    iHelix = -1
    length = len(pairs)
    helices = [-1]*length
    helixStarts = {}
    helixEnds = {}
    for i,pair in enumerate(pairs):
        if not inHelix:
            if i == pairs[pair]:
                if helices[i] == -1:
                    inHelix = True
                    iHelix += 1
                    helices[i] = iHelix
                    helices[pair] = iHelix
                    helixStarts[iHelix] = (i,pair)
                lastI = i
                lastJ = pair
        else:
            if pair == -1:
                inHelix = False
                helixEnds[iHelix] = (lastI, lastJ)
            else:
                if pair != lastJ-1:
                    if helices[i] == -1:
                        helixEnds[iHelix] = (lastI,lastJ)
                        iHelix += 1
                        helixStarts[iHelix] = (i,pair)
                if helices[i] == -1:
                    helices[i] = iHelix
                if helices[pair] == -1:
                    helices[pair] = iHelix
                lastI = i
                lastJ = pair

        print i,pair,pairs[pair],inHelix,iHelix,vie[i]
    nHelix = len(helixStarts)
    return helixStarts,helixEnds

def generateResNums(residues,seqString,linker,polyType):
    bases = []
    for char in seqString:
        if char.isalpha():
            if polyType == "RNA":
                bases.append(char.upper())
            else:
                bases.append(protein1To3[char.upper()])

    if isinstance(residues,int):
        startIndex = residues
        resNums = range(len(bases))
        resNums = list(map(lambda x: str(x+startIndex),resNums))
    else:
        arr = residues.split()
        resNums = []
        for resIndices in arr:
            resIndices = resIndices.split(':')
            if len(resIndices) == 1:
                resNums += resIndices
            else:
                startIndex = int(resIndices[0])
                endIndex = int(resIndices[1])
                diff = endIndex - startIndex
                residues = range(diff+1)
                residues = list(map(lambda x: str(x+startIndex),residues))
                resNums += residues
    indexError = len(resNums) != len(bases)
    if indexError:
        raise IndexError('The residues string does not match the inputted sequence')

    residues = []
    if linker != None:
        linker = linker.split(':')
        insertionIndex = int(linker[0])
        insertionLength = int(linker[1])

    for i in xrange(len(bases)):
        residue = bases[i] + " " + resNums[i]
        residues.append(residue)
        if linker != None:
            if int(resNums[i]) == insertionIndex:
                for j in range(insertionLength):
                    if j == 0 or (j == insertionLength-1):
                        residue = 'ln2 ' + str(j+1+int(resNums[i]))
                    else:
                        residue = 'ln5 ' + str(j+1+int(resNums[i]))
                    residues.append(residue)
    return residues

def prioritizePolymers(molList):
    containsSmallMolecule = False
    for molDict in molList:
        if "ptype" not in molDict:
            containsSmallMolecule = True
    if (not containsSmallMolecule):
        return molList
    smallMoleculeList = []
    returnList = []
    for molDict in molList:
        if "ptype" in molDict:
            returnList.append(molDict)
        else:
            smallMoleculeList.append(molDict)
    returnList += smallMoleculeList
    return returnList





class dynOptions:
    def __init__(self,steps=15000,highTemp=5000.0,medFrac=0.05,update=20,highFrac=0.3,toMedFrac=0.5,switchFrac=0.65):
        self.steps = steps
        self.highTemp = highTemp
        self.medFrac = medFrac
        self.update = update
        self.highFrac = highFrac
        self.toMedFrac = toMedFrac
        self.switchFrac = switchFrac
        self.timeStep = 4.0
        self.stepsEnd = 100
        self.econHigh = 0.005
        self.econLow = 0.001
        self.timePowerHigh = 4.0
        self.timePowerMed = 4.0
        self.minSteps = 100
        self.polishSteps = 500
        self.irpWeight = 0.015
        self.kinEScale = 200.0
        self.swap = 0

class refine:
    def __init__(self):
        self.cyanaAngleFiles = []
        self.xplorAngleFiles = []
        self.nvAngleFiles = []
        self.cyanaDistanceFiles = []
        self.xplorDistanceFiles = []
        self.suiteAngleFiles = []
        self.nvDistanceFiles = []
        self.angleStrings = []
        self.bondConstraints = []
        self.disLim = 4.6
        self.angleDelta = 30
        self.molecule = None
        self.trajectoryWriter = None
        self.molecule = Molecule.getActive()
        if self.molecule != None:
            self.molName = self.molecule.getName()

    def setAngleDelta(self,value):
        self.angleDelta = value

    def writeAngles(self,fileName):
        self.dihedral.writeDihedrals(fileName)

    def readAngles(self,fileName):
        self.dihedral.readDihedrals(fileName)

    def numericalDerivatives(self,delta,report):
        grefine = GradientRefinement(self.dihedral)

        grefine.numericalDerivatives(delta,report)

    def setSeed(self,seed):
        self.dihedral.seed(seed)
        ranGen = self.dihedral.getRandom()
        newSeed = ranGen.nextInt()
        self.dihedral.seed(newSeed)

    def putPseudo(self,angle1,angle2):
        self.dihedral.putPseudoAngle(angle1,angle2)

    def setAngles(self,ranfact,mode):
        self.dihedral.putInitialAngles(mode)

    def randomizeAngles(self):
        self.dihedral.randomizeAngles()

    def updateAt(self,n):
        self.dihedral.updateAt(n)

    def setForces(self,robson=None,repel=None,elec=None,dis=None,tors=None,dih=None,irp=None, shift=None, bondWt=None):
        forceWeightOrig = self.energyLists.getForceWeight()
        if robson == None:
            robson = forceWeightOrig.getRobson()
        if repel == None:
            repel = forceWeightOrig.getRepel()
        if elec == None:
            elec = forceWeightOrig.getElectrostatic()
        if dis == None:
            dis = forceWeightOrig.getNOE()
        if tors == None:
            tors = forceWeightOrig.getDihedralProb()
        if dih == None:
            dih = forceWeightOrig.getDihedral()
        if irp == None:
            irp = forceWeightOrig.getIrp()
        if shift == None:
            shift = forceWeightOrig.getShift()
        if bondWt == None:
            bondWt = forceWeightOrig.getBondWt()
        else:
            if bondWt < 1:
                raise ValueError('The bond weight should not be less than 1')
        forceWeight = ForceWeight(elec,robson,repel,dis,tors,dih,irp,shift,bondWt)
        self.energyLists.setForceWeight(forceWeight)

    def getForces(self):
        fW = self.energyLists.getForceWeight()
        output = "robson %5.2f repel %5.2f elec %5.2f dis %5.2f dprob %5.2f dih %5.2f irp %5.2f shift %5.2f bondWt %5.2f" % (fW.getRobson(),fW.getRepel(),fW.getElectrostatic(),fW.getNOE(),fW.getDihedralProb(),fW.getDihedral(),fW.getIrp(), fW.getShift(), fW.getBondWt())
        return output

    def dump(self,limit,shiftLim, fileName):
        if fileName != None:
            self.energyLists.dump(limit,shiftLim,fileName)

    def getEnergyDump(self,limit):
        return self.energyLists.dump(limit)

    def rinertia(self):
        self.rDyn = self.dihedral.getRotationalDyamics()
        self.rDyn.setTrajectoryWriter(self.trajectoryWriter)
        return self.rDyn

    def getAtom(self, atomTuple):
        entity, atomName = atomTuple
        atomArr = atomName.split('.')

        if len(atomArr) > 1:
            resNum = atomArr.pop(0)
            if resNum:
                entity = entity.getResidue(resNum)
        atomName = atomArr[0]
        atom = entity.getAtom(atomName)

        if not atom:
            print atomName , "was not found in", entity.getName()
            raise ValueError
        return atom

    def addLinkers(self, linkerList):
        entities = self.molecule.getEntities()
        entityDict = {}
        usedEntity = {}
        for entity in entities:
            entityDict[entity.getName()] = entity # stores entity objects from the entity name
            usedEntity[entity] = False

        for i, entity in enumerate(entities):
            if i == len(entities) - 1:
                break
            startAtom = entity.getLastAtom()
            endAtom = entities[i+1].getLastAtom()
            self.molecule.createLinker(startAtom, endAtom, 6)
            return
        if linkerList:
            try:
                for linkerDict in linkerList:
                    usedEntities = self.readLinkerDict(linkerDict, entityDict) # returns used Entities to mark them
                    for entity in usedEntities:
                        usedEntity[entity] = True

            except TypeError:
                usedEntities = self.readLinkerDict(linkerList, entityDict)
                for entity in usedEntities:
                    usedEntity[entity] = True
                # linkerList is just a dict

        #Adds linkers to all entities that do not have one
        usedEntities = [entity for entity in usedEntity.keys() if usedEntity[entity]]
        if len(usedEntity.keys()) > 1:
            for entity in usedEntity:
                used = usedEntity[entity]
                if not used:
                    print entity.getName() + " had no defined linker."
                    firstEntity = usedEntities[0]
                    startAtom = firstEntity.getLastAtom()
                    endAtom = entity.getLastAtom()
                    print "linker added between " + startAtom.getFullName() + " and " + endAtom.getFullName()
                    self.molecule.createLinker(startAtom, endAtom, 6)

    def readLinkerDict(self, linkerDict, entityDict):
        if not linkerDict:
            return
        if 'start' in linkerDict:
            startEntName, startAtom = linkerDict['start'].split(':')
            endEntName, endAtom = linkerDict['end'].split(':')
            n = linkerDict['n'] if 'n' in linkerDict else 6
            if startEnt not in entityDict:
                print startEnt + " not found within molecule"
                raise ValueError
            if endEnt not in entityDict:
                print endEnt + " not found within molecule"
                raise ValueError

            startEnt = entityDict[startEntName]
            endEnt = entityDict[endEntName]

            startTuple = (startEnt, startAtom)
            endTuple = (endEnt, endAtom)
            startAtom = self.getAtom(startTuple)
            endAtom = self.getAtom(endTuple)

        if 'bond' in linkerDict:
            bondDict = linkerDict['bond']
            if "cyclic" in bondDict and bondDict["cyclic"]:
                polymers = self.molecule.getPolymers()
                if len(polymers) != 1 and pName not in bondDict:
                    print "Multiple polymers in structure but no specification for which to by made cyclic"
                    return []
                polymer = None
                if "pName" in bondDict:
                    polymerNames = [polymer.getName() for polymer in polymers]
                    try:
                        entity = entityDict[bondDict["pName"]]
                    except KeyError:
                         print bondDict["pName"], "is not an entity within the molecule"
                         return []
                    entName = entity.getName();
                    try:
                        polymer = polymers[polymerNames.index(entName)]
                    except ValueError:
                        print entName, "is not a polymer and cannot be made cyclic"
                else:
                    polymer = polymers[0]
                self.addCyclicBond(polymer)
                return []

            else:
                sameEnt = startEnt == endEnt;
                length = bondDict['length'] if 'length' in bondDict else 1.08
                order = bondDict['order'] if 'order' in bondDict else 'SINGLE'
                if not sameEnt:
                    global bondOrders
                    if (order < 0 or order > 4) and order not in bondOrders:
                        print "Bad bond order, automatically converting to SINGLE bond"
                        order = "SINGLE"
                    try:
                        order = bondOrders[order-1]
                    except:
                        order = order.upper()
                        self.molecule.createLinker(startAtom, endAtom, order, length)

                else:
                    atomName1 = startAtom.getFullName()
                    atomName2 = endAtom.getFullName()
                    lower = length - .0001
                    upper = length + .0001
                    self.energyLists.addDistanceConstraint(atomName1,atomName2,lower,upper,True)
        else:
            self.molecule.createLinker(startAtom, endAtom, n)
        return (startEnt, endEnt)

    def addCyclicBond(self, polymer):
        # to return a list atomName1, atomName2, distance
        distanceConstraints = polymer.getCyclicConstraints()
        for distanceConstraint in distanceConstraints:
            self.bondConstraints.append(distanceConstraint)
            #atomName1, atomName2, distance = distanceConstraint.split()
            #self.energyLists.addDistanceConstraint(atomName1, atomName2, distance - .0001, distance + .0001, True)


    def testy(self):
        print('testynow')

    def printPars(self):
         el = self.energyLists
         coarseGrain = el.getCourseGrain()
         includeH = el.getIncludeH()
         hardSphere = el.getHardSphere()
         deltaStart = el.getDeltaStart()
         deltaEnd = el.getDeltaEnd()
         shrinkValue = el.getShrinkValue()
         shrinkHValue = el.getShrinkHValue()
         disLim = el.getDistanceLimit()
         print coarseGrain,includeH,hardSphere,shrinkValue,shrinkHValue,deltaStart,deltaEnd,disLim

    def setPars(self,coarse=None,useh=None,dislim=-1,end=-1,start=-1,hardSphere=-1.0,shrinkValue=-1.0,shrinkHValue=-1.0,swap=None,optDict={}):
        #  there must be a better way to do this
        for opt in optDict:
            if opt == 'hardSphere':
                hardSphere = optDict[opt]
            elif opt == 'shrinkValue':
                shrinkValue = optDict[opt]
            elif opt == 'shrinkHValue':
                shrinkHValue = optDict[opt]

        if (coarse != None):
            self.energyLists.setCourseGrain(coarse)
        if (useh != None):
            self.energyLists.setIncludeH(useh)
        if (hardSphere >=0):
            self.energyLists.setHardSphere(hardSphere)
        if (start >= 0):
            self.energyLists.setDeltaStart(start)
        if (end >=0):
            self.energyLists.setDeltaEnd(end)
        if (shrinkValue >=0):
            self.energyLists.setShrinkValue(shrinkValue)
        if (shrinkHValue >=0):
            self.energyLists.setShrinkHValue(shrinkHValue)
        if (dislim >=0):
            self.energyLists.setDistanceLimit(dislim)
        if (swap != None):
            self.energyLists.setSwap(swap)
        self.energyLists.resetConstraints()

    def setupEnergy(self,molName,eList=None, useH=False,usePseudo=True,useCourseGrain=False,useShifts=False):
        #creates a EnergyList object
        if eList == None:
            energyLists = EnergyLists()
            #calls makeCompound List method to create a list of compound molecules
            energyLists.makeCompoundList(molName)
            energyLists.clear()
        else:
            energyLists = eList
        self.energyLists = energyLists
        if self.bondConstraints:
            for bondConstraint in self.bondConstraints:
                atomName1, atomName2, distance = bondConstraint.split()
                distance = float(distance)
                self.energyLists.addDistanceConstraint(atomName1, atomName2, distance - .0001, distance + .0001, True)


        refine.setForces(self,repel=0.5,dis=1)
        energyLists.setCourseGrain(useCourseGrain)
        energyLists.setIncludeH(useH)
        energyLists.setHardSphere(0.15)
        energyLists.setDistanceLimit(5.0)
        energyLists.setDeltaStart(0)
        energyLists.setDeltaEnd(1000)
        energyLists.makeAtomListFast()
        if useShifts:
            energyLists.setRingShifts()
        energy=energyLists.energy()
        self.dihedral = Dihedral(energyLists,usePseudo)

    def usePseudo(self,usePseudo):
        self.dihedral.setUsePseudo(usePseudo);

    def energy(self):
        self.energyLists.makeAtomListFast()
        self.dihedral.setupAngleRestraints()
        energy=self.energyLists.energy()
        return(energy)

    def gmin(self,nsteps=100,tolerance=1.0e-5):
        self.refiner = GradientRefinement(self.dihedral)
        self.refiner.setTrajectoryWriter(self.trajectoryWriter)
        self.refiner.gradMinimize(nsteps, tolerance)

    def sgdmin(self,nsteps=100,tolerance=1.0e-5):
        self.refiner = StochasticGradientDescent(self.dihedral)
        self.refiner.setTrajectoryWriter(self.trajectoryWriter)
        self.refiner.gradMinimize(nsteps, tolerance)

    def refine(self,nsteps=10000,stopFitness=0.0,radius=0.01,alg="cmaes",ninterp=1.2,lambdaMul=1, nFireflies=18, diagOnly=1.0,useDegrees=False):
        print self.energyLists.energy()
        self.energyLists.makeAtomListFast()
        print self.energyLists.energy()
        diagOnly = int(round(nsteps*diagOnly))
        if (alg == "cmaes"):
            self.refiner = CmaesRefinement(self.dihedral)
            self.refiner.refineCMAES(nsteps,stopFitness,radius,lambdaMul,diagOnly,useDegrees)
        elif (alg == "firefly"):
            #self.refiner = FireflyRefinement(self.dihedral)
            #self.refiner.refineFirefly(nsteps,stopFitness,radius,nFireflies,useDegrees)
            pass
        else:
            self.dihedral.refineBOBYQA(nsteps,ninterp,radius)

    def loadDihedralsFromFile(self,fileName):
       file = open(fileName,"r")
       data= file.read()
       file.close()
       lines = data.split('\n')
       self.loadDihedrals(lines)

    def loadDihedrals(self,lines):
        for line in lines:
            line = line.strip()
            if (len(line) == 0):
                continue
            if (line[0] == '#'):
                continue
            values = line.split()
            if (len(values)==4):
                (atomName,s1,s2,s3) = values
                lower = float(s1)
                upper = float(s2)
                scale = float(s3)
                atom3 = Molecule.getAtomByName(atomName)
                atom2 = atom3.getParent()
                atom1 = atom2.getParent()
                atom0 = atom1.getParent()
                atoms = [atom0, atom1, atom2, atom3]
                self.dihedral.addBoundary(atoms, lower,upper,scale)
            elif (len(values)==2):
                (atomName,s1) = values
                lower = float(s1)-angleDelta
                upper = float(s1)+angleDelta
                if (lower < -180):
                    lower += 360
                    upper += 360
                scale = 0.05
                atom3 = Molecule.getAtomByName(atomName)
                atom2 = atom3.getParent()
                atom1 = atom2.getParent()
                atom0 = atom1.getParent()
                atoms = [atom0, atom1, atom2, atom3]
                self.dihedral.addBoundary(atoms, lower,upper,scale)
            else:
                atomName = values[0]
                lower = float(values[1])
                upper = float(values[2])
                scale = float(values[3])
                i = 4
                sigma = []
                height = []
                center = []
                while (i<len(values)):
                    center.append(float(values[i]))
                    sigma.append(float(values[i+1]))
                    height.append(float(values[i+2]))
                    i+=3
                #bound = AngleBoundary(atomName,lower,upper,scale,center,sigma,height)
                #self.dihedral.addBoundary(atomName,bound)

    def loadDistancesFromFile(self,fileName):
       file = open(fileName,"r")
       data= file.read()
       file.close()
       self.loadDistances(data)

    def loadDistances(self,data):
        lines = data.split('\n')
        for line in lines:
            line = line.strip()
            if (len(line) == 0):
                continue
            if (line[0] == '#'):
                continue
            values = line.split()
            (atomName1,atomName2,s2,s3) = values
            lower = float(s2)
            upper = float(s3)
            self.energyLists.addDistanceConstraint(atomName1,atomName2,lower,upper)

    def addDisCon(self, atomName1, atomName2, lower, upper):
        self.energyLists.addDistanceConstraint(atomName1,atomName2,lower,upper)

    def loadFromYaml(self,data, seed, pdbFile=""):
        if pdbFile != '':
            self.readPDBFile(pdbFile)
            residues = None
        else:
            if 'molecule' in data:
                molData = data['molecule']
                self.reslib = molData['reslib'] if 'reslib' in molData else None
                if 'entities' in molData:
                    molList = molData['entities']
                    molList = prioritizePolymers(molList)
                    for molDict in molList:
                        residues = ",".join(molDict['residues'].split()) if 'residues' in molDict else None
                        self.readMoleculeDict(molDict)
                else:
                    #Only one entity in the molecule
                    residues = ",".join(molDict['residues'].split()) if 'residues' in molData else None
                    self.readMoleculeDict(molData)

                linkerList = molData['link'] if 'link' in molData else None
                self.addLinkers(linkerList)

        mol = self.molecule;
        if 'distances' in data:
            disWt = self.readDistanceDict(data['distances'],residues)
        if 'angles' in data:
            angleWt = self.readAngleDict(data['angles'])

        if 'tree' in data:
            treeDict = data['tree']
            if treeDict == None:
                start = None
                end = None
            else:
                if 'start' in treeDict:
                    start = treeDict['start']
                else:
                    start = None
                if 'end' in treeDict:
                    end = treeDict['end']
                else:
                    end = None
            self.setupTree(start, end)

        self.setup('./',seed,writeTrajectory=False, usePseudo=False)
        self.energy()

        if 'molecule' in data:
            molDict = data['molecule']
            if 'ss' in molDict:
                for ss in molDict['ss']:
                    if 'type' in ss:
                        type = ss['type']
                        if type == "helix":
                            self.setPeptideDihedrals(-60,-60)
                        elif type == "sheet":
                            self.setPeptideDihedrals(-120,120)
        if 'shifts' in data:
            self.readShiftDict(data['shifts'],residues)

        if 'rna' in data:
            self.readRNADict(data['rna'])
        self.readSuiteAngles()

        self.readAngleFiles()
        self.readDistanceFiles()

        if 'anneal' in data:
            self.dOpt = self.readAnnealDict(data['anneal'])
        self.energy()

    def readMoleculeDict(self,molDict):
        #if sequence exists it takes priority over the file and the sequence will be used instead
        polyType = "PROTEIN"
        if 'ptype' in molDict:
            polyType = molDict['ptype'].upper()
        if 'sequence' in molDict:
            import java.util.ArrayList
            from org.nmrfx.structure.chemistry.io import Sequence
            seqString = molDict['sequence']
            if 'link' in molDict:
                linker = molDict['link']
            else:
                linker = None
            if 'residues' in molDict:
                 resNums = generateResNums(molDict['residues'],seqString,linker,polyType)
            else:
                 resNums = generateResNums(1,seqString,linker,polyType)
            arrayList = ArrayList()
            arrayList.addAll(resNums)
            sequenceReader = Sequence()
            self.molecule = sequenceReader.read('p',arrayList,'')
            self.molName = self.molecule.getName()
        else:
            file = molDict['file']
            if 'type' in molDict:
                type = molDict['type']
                if type == 'fasta':
                    import os
                    import osfiles
                    dir = os.path.dirname(file)
                    file = osfiles.convertSeqFile(file,dir)
                    type = 'nv'
                elif type == 'pdb':
                    self.readPDBFile(file)
                elif type == 'sdf':
                    self.readSDFile(file)
                elif type == 'mol':
                    self.readSDFile(file)

            else:
                type = 'nv'
            if type == 'nv':
                self.readSequence(file)

            if 'tree' in molDict:
                treeDict = molDict['tree']
                if treeDict == None:
                    start = None
                    end = None
                else:
                    if 'start' in treeDict:
                        start = treeDict['start']
                    else:
                        start = None
                    if 'end' in treeDict:
                        end = treeDict['end']
                    else:
                        end = None
                self.setupTree(start, end)
            mol = self.molecule;

    def readDistanceDict(self,disDict,residues):
        wt = -1.0
        for dic in disDict:
            file = dic['file']
            if 'type' in dic:
                type = dic['type']
                if type == 'amber':
                    import os
                    import osfiles
                    dir = os.path.dirname(file)
                    file = osfiles.convertConstraintFile(file,dir)
                    type = 'nv'
            else:
                type = 'nv'
            if 'weight' in dic:
                wt = dic['weight']
            if 'range' in dic and type == 'nv':
                import os
                import osfiles
                range = dic['range']
                dir = os.path.dirname(file)

                changeResNums = residues != range
                file = osfiles.limResidues(range,file,dir,'dis',changeResNums)
            self.addDistanceFile(file,mode=type)
        return wt

    def readAngleDict(self,disDict):
        wt = -1.0
        for dic in disDict:
            file = dic['file']
            if 'type' in dic:
                type = dic['type']
            else:
                type = 'nv'
            if 'weight' in dic:
                wt = dic['weight']
            self.addAngleFile(file,mode=type)
        return wt

    def readRNADict(self, rnaDict):
        if 'ribose' in rnaDict:
            if rnaDict['ribose'] == "Constrain":
                polymers = self.molecule.getPolymers()
                for polymer in polymers:
                    self.addRiboseRestraints(polymer)
        if 'suite' in rnaDict:
            self.addSuiteAngles(rnaDict['suite'])
        if 'vienna' in rnaDict:
            self.findHelices(rnaDict['vienna'])

    def readAnnealDict(self, annealDict):
        dOpt = dynOptions()
        if 'steps' in annealDict:
            dOpt.steps = annealDict['steps']
        if 'highTemp' in annealDict:
            dOpt.highTemp = annealDict['highTemp']
        if 'highFrac' in annealDict:
            dOpt.highFrac = annealDict['highFrac']
        if 'kinEScale' in annealDict:
            dOpt.kinEScale = annealDict['kinEScale']
        if 'irpWeight' in annealDict:
            dOpt.irpWeight = annealDict['irpWeight']
        if 'swap' in annealDict:
            dOpt.swap = annealDict['swap']

        return dOpt

    def readShiftDict(self, shiftDict,residues):
        wt = -1.0
        file = shiftDict['file']
        if 'type' in shiftDict:
            type = shiftDict['type']
            if type == 'str3':
                import os
                import osfiles
                dir = os.path.dirname(file)
                file = osfiles.convertStarFile(file,dir)
                type = 'nv'
        else:
            type = 'nv'
        if type == 'nv':
            if 'range' in shiftDict:
                import os
                import osfiles
                range = shiftDict['range']
                dir = os.path.dirname(file)
                changeResNums = range!=residues
                file = osfiles.limResidues(range,file,dir,'shift',changeResNums)
            self.setShifts(file)
            ringShifts = self.setBasePPMs()
            self.energyLists.setRingShifts()
        if 'weight' in shiftDict:
            wt = shiftDict['weight']
        return wt

    def addRiboseRestraints(self,polymer):
        for residue in polymer.getResidues():
            resNum = residue.getNumber()
            resName = residue.getName()
            upper = 1.46
            lower = 1.44
            restraints = []
            restraints.append(("C4'", "O4'",1.44, 1.46))
            restraints.append(("C4'", "C1'",2.30, 2.36))
            restraints.append(("C5'", "O4'",2.37,  2.43))
            restraints.append(("C5'", "C3'",2.48, 2.58))
            restraints.append(("C3'", "O4'",2.32,  2.35))
            #fixme Verify constraints
            restraints.append(("O4'", "H4'", 2.05, 2.2))
            # restraints.append(("O4'", "H4'", 1.97, 2.05))
            if resName in ('C','U', 'URA', 'RCYT'):
                restraints.append(("N1", "O4'",2.40, 2.68))
            else:
                restraints.append(("N9", "O4'",2.40, 2.68))
            #restraints.append(("C5'", "C3'",2.56, 2.58))
            for restraint in restraints:
                (a1,a2,lower,upper) = restraint
                atomName1 = str(resNum)+'.'+a1
                atomName2 = str(resNum)+'.'+a2
                try:
                    self.energyLists.addDistanceConstraint(atomName1,atomName2,lower,upper,True)
                except:
                    print 'error adding ribose restraint',atomName1,atomName2,lower,upper
#DELTA: C5'-C4'-C3'-O3'            60 140
#NU2:  C4'-C3'-C2'-C1'             -40 40
#NU1:  C3'-C2'-C1'-N9'             60 140

            restraints = []
            restraints.append((("C5'","C4'","C3'","O3'"), 70, 155))
            restraints.append((("C4'","C3'","C2'","C1'"), -40, 40))
            if resName in ('C','U','URA','RCYT'):
                restraints.append((["C3'","C2'","C1'","N1"], 60, 140))
            else:
                restraints.append((["C3'","C2'","C1'","N9"], 60, 140))
            for restraint in restraints:
                (a1,lower,upper) = restraint
                lower = float(lower)
                upper = float(upper)
                if (lower < -180):
                     lower += 360
                     upper += 360
                fullAtoms = []
                for aName in a1:
                    fullAtoms.append(str(resNum)+'.'+aName)

                scale = 1.0
                try:
                    self.dihedral.addBoundary(fullAtoms,lower,upper,scale)
                except:
                    print "err",fullAtoms
                    pass


#set dc [list 283.n3 698.h1 1.8 2.0]
# 283  RCYT  H6          283  RCYT  H2'          4.00    1.00E+00
# 283  RCYT  H6          283  RCYT  H3'          3.00    1.00E+00
# 283  RCYT  H3'   283  RCYT  H5"          3.30    1.00E+00

    def readCYANADistances(self,fileNames,molName):
        defaultLower = 1.8
        atomPairs  = []
        distances  = []
        lowers  = {}
        i=0
        for fileName in fileNames:
            fIn = open(fileName,'r')
            if fileName.endswith('.lol'):
                mode = 'lower'
            else:
                mode = 'upper'
            for line in fIn:
                line = line.strip()
                if len(line) == 0:
                    continue
                if line[0] == '#':
                    continue
                fields = line.split()
                res1 = fields[0]
                atom1 = fields[2]
                res2 = fields[3]
                atom2 = fields[5]
                distance  = float(fields[6])
                fullAtom1 = molName+':'+res1+'.'+atom1
                fullAtom2  =molName+':'+res2+'.'+atom2
                fullAtom1 = fullAtom1.replace('"',"''")
                fullAtom2 = fullAtom2.replace('"',"''")
                if fullAtom1 < fullAtom2:
                    atomPair = fullAtom1+' '+fullAtom2
                else:
                    atomPair = fullAtom2+' '+fullAtom1
                if mode == 'upper':
                    if distance > 0.0:
                        distances.append(distance)
                        atomPairs.append([atomPair])
                        i += 1
                    else:
                        atomPairs[i-1].append(atomPair)
                else:
                    lowers[atomPair] = distance
            fIn.close()
        for atomPair,upper in zip(atomPairs,distances):
            lower = defaultLower
            for atomPairElem in atomPair:
                if atomPairElem in lowers:
                    lower = lowers[atomPairElem]
            # fixme doesn't work right with ambiguous constraints
            atomNames1 = ArrayList()
            atomNames2 = ArrayList()
            for atomPairElem in atomPair:
                (atomName1,atomName2) = atomPairElem.split()
                atomNames1.add(atomName1)
                atomNames2.add(atomName2)
            self.energyLists.addDistanceConstraint(atomNames1,atomNames2,lower,upper)


#ZETA:  C3'(i-1)-O3'(i-1)-P-O5'   -73
#ALPHA: O3'(i-1)-P-O5'-C5'        -62
#BETA:  P-O5'-C5'-C4'             180
#GAMMA: O5'-C5'-C4'-C3'            48
#DELTA: C5'-C4'-C3'-O3'            60 140
#NU2:  C4'-C3'-C2'-C1'             -40 40
#HOXI:  C3'-C2'-O2'-HO2''         -140
#NU1:  C3'-C2'-C1'-N9'             60 140
#CHI:  C2'-C1'-N? - C?             97
#EPSI:  C4'-C3'-O3'-P(i+1)       -152
#283   RCYT  GAMMA     23.0    73.0  1.00E+00


    def readCYANAAngles(self, fileName,mol):
        angleMap = {}
        angleMap['DELTA']= ["C5'","C4'","C3'","O3'"]
        angleMap['EPSI']= ["C4'","C3'","O3'","1:P"]
        angleMap['ZETA']= ["-1:C3'","-1:O3'","P","O5'"]
        angleMap['ALPHA']= ["-1:O3'","P","O5'","C5'"]
        angleMap['BETA']= ["P","O5'","C5'","C4'"]
        angleMap['GAMMA']= ["O5'","C5'","C4'","C3'"]
        angleMap['NU2']= ["C1'","C2'","C3'","C4'"]
        angleMap['PHI']= ["-1:C","N","CA","C"]
        angleMap['PSI']= ["N","CA","C","1:N"]
        angleMap['NU1']= ["O4'","C1'","C2'","C3'"]
        angleMap['CHI','U']= ["O4'","C1'","N1","C2"]
        angleMap['CHI','C']= ["O4'","C1'","N1","C2"]
        angleMap['CHI','G']= ["O4'","C1'","N9","C4"]
        angleMap['CHI','A']= ["O4'","C1'","N9","C4"]
        angleMap['CHI','URA']= ["O4'","C1'","N1","C2"]
        angleMap['CHI','RGUA']= ["O4'","C1'","N9","C4"]
        angleMap['CHI','RCYT']= ["O4'","C1'","N1","C2"]
        angleMap['CHI','RADE']= ["O4'","C1'","N9","C4"]
        angleMap['CHI1','ARG']= ["N","CA","CB","CG"]
        angleMap['CHI1','ASN']= ["N","CA","CB","CG"]
        angleMap['CHI1','ASP']= ["N","CA","CB","CG"]
        angleMap['CHI1','CYS']= ["N","CA","CB","SG"]
        angleMap['CHI1','GLN']= ["N","CA","CB","CG"]
        angleMap['CHI1','GLU']= ["N","CA","CB","CG"]
        angleMap['CHI1','HIS']= ["N","CA","CB","CG"]
        angleMap['CHI1','ILE']= ["N","CA","CB","CG1"]
        angleMap['CHI1','LEU']= ["N","CA","CB","CG"]
        angleMap['CHI1','LYS']= ["N","CA","CB","CG"]
        angleMap['CHI1','MET']= ["N","CA","CB","CG"]
        angleMap['CHI1','PHE']= ["N","CA","CB","CG"]
        angleMap['CHI1','SER']= ["N","CA","CB","OG"]
        angleMap['CHI1','THR']= ["N","CA","CB","OG1"]
        angleMap['CHI1','TRP']= ["N","CA","CB","CG"]
        angleMap['CHI1','TYR']= ["N","CA","CB","CG"]
        angleMap['CHI1','VAL']= ["N","CA","CB","CG1"]
        angleMap['CHI2','LEU']= ["CA","CB","CG","CD1"]
        angleMap['CHI2','ASP']= ["CA","CB","CG","OD1"]
        angleMap['CHI2','PHE']= ["CA","CB","CG","CD1"]
        angleMap['CHI2','GLU']= ["CA","CB","CG","CD"]
        angleMap['CHI2','GLN']= ["CA","CB","CG","CD"]
        angleMap['CHI2','MET']= ["CA","CB","CG","SD"]
        angleMap['CHI2','TYR']= ["CA","CB","CG","CD1"]
        angleMap['CHI2','TRP']= ["CA","CB","CG","CD1"]
        angleMap['CHI21','ILE']= ["CA","CB","CG1","CD1"]
        angleMap['CHI21','THR']= ["CA","CB","OG1","HG1"]
        angleMap['CHI2','HIS']= ["CA","CB","CG","CD2"]
        angleMap['CHI2','LYS']= ["CA","CB","CG","CD"]
        angleMap['CHI2','ARG']= ["CA","CB","CG","CD"]
        angleMap['CHI3','ARG']= ["CB","CG","CD","NE"]
        angleMap['CHI3','GLN']= ["CB","CG","CD","OE1"]
        angleMap['CHI3','GLU']= ["CB","CG","CD","OE1"]
        angleMap['CHI3','LYS']= ["CB","CG","CD","CE"]
        angleMap['CHI3','MET']= ["CB","CG","SD","CE"]
        angleMap['CHI4','ARG']= ["CG","CD","NE","CZ"]
        angleMap['CHI4','LYS']= ["CG","CD","CE","NZ"]
        angleMap['CHI4','ARG']= ["CD","NE","CZ","NH1"]

        fIn = open(fileName,'r')
        for line in fIn:
           line = line.strip()
           if len(line) == 0:
               continue
           if line[0] == '#':
               continue
           (res,resName, angle,lower,upper) = line.split()
           if angle in angleMap:
               atoms = angleMap[angle]
           elif (angle,resName) in angleMap:
               atoms = angleMap[angle,resName]
           else:
               print "no such angle",angle,resName
               exit()
           res = int(res)
           fullAtoms = []
           for atom in atoms:
               if ':' in atom:
                   split_atom = atom.split(':')
                   dRes = int(split_atom[0])
                   atom = split_atom[1]
               else:
                   dRes = 0
               fullAtom = mol+':'+str(res + dRes)+'.'+atom
               fullAtom = fullAtom.replace('"',"''")
               fullAtoms.append(fullAtom)
           scale = 1.0
           lower = float(lower)
           upper = float(upper)
           if lower == upper:
               lower = lower - 20
               upper = upper + 20
           if (lower < -180) and (upper < 0.0):
                lower += 360
                upper += 360
           self.dihedral.addBoundary(fullAtoms,lower,upper,scale)
        fIn.close()

    def addSuiteAngles(self, fileName):
        self.suiteAngleFiles.append(fileName)

    def readSuiteAngles(self, mul = 0.5):
        for fileName in self.suiteAngleFiles:
            polymers = self.molecule.getPolymers()
            polymer = polymers[0]

            fIn = open(fileName,'r')
            for line in fIn:
                line = line.strip()
                (residueNum, rotamerName) = line.split()
                angleBoundaries = RNARotamer.getAngleBoundaries(polymer, residueNum, rotamerName, mul)
                for angleBoundary in angleBoundaries:
                    self.dihedral.addBoundary(angleBoundary)
            fIn.close()

    def addSuiteBoundary(self,polymer, residueNum,rotamerName, mul=0.5):
        angleBoundaries = RNARotamer.getAngleBoundaries(polymer, str(residueNum), rotamerName, mul)
        for angleBoundary in angleBoundaries:
            self.dihedral.addBoundary(angleBoundary)

    def getSuiteAngles(self, molecule):
        angles  = [
              ["0.C5'","0.C4'","0.C3'","0.O3'"],
              ["0.C4'","0.C3'","0.O3'","1.P"],
              ["0.C3'","0.O3'","1.P","1.O5'"],
              ["0.O3'","1.P","1.O5'","1.C5'"],
              ["1.P","1.O5'","1.C5'","1.C4'"],
              ["1.O5'","1.C5'","1.C4'","1.C3'"],
              ["1.C5'","1.C4'","1.C3'","1.O3'"],
              ["1.C3'","1.C2'","1.C1'","1.N?"]
              ]

        polymers = molecule.getPolymers()
        for polymer in polymers:
            polName = polymer.getName()
            residues = polymer.getResidues()
            rName = residues[0].getName()
            if not rName in ['C','G','A','U']:
                continue
            print 'pdbid',molecule.getName(),polName,len(residues)
            for (residueP,residue) in zip(residues[:-1],residues[1:]):
                p = polName + ':'+str(residueP.getNumber())
                r = polName + ':'+str(residue.getNumber())
                rName = residue.getName()
                output = ""
                for angleAtoms in angles:
                    am=[""]*4
                    for i,atom in enumerate(angleAtoms):
                        if atom[0] == '0':
                            am[i] = p + '.' + atom[2:]
                        else:
                            am[i] = r + '.' + atom[2:]
                        if am[i][-1] == "?":
                            if rName == "A":
                                am[i] = am[i][0:-1]+"9"
                            elif rName == "G":
                                am[i] = am[i][0:-1]+"9"
                            elif rName == "C":
                                am[i] = am[i][0:-1]+"1"
                            elif rName == "U":
                                am[i] = am[i][0:-1]+"1"
                    try:
                        d = molecule.calcDihedral(am[0],am[1],am[2],am[3])
                        output += "%s %7.1f" % (rName,d*180.0/math.pi)
                    except:
                        pass
                print 'dihedral',output

    def addHelices(self,polymer,helixStarts, helixEnds):
        residues = polymer.getResidues()
        nHelices = len(helixStarts)
        for i in range(nHelices):
            (start,startPair) = helixStarts[i]
            startRes = residues[start].getNumber()
            startPairRes = residues[startPair].getNumber()
            (end,endPair) = helixEnds[i]
            endRes = residues[end].getNumber()
            endPairRes = residues[endPair].getNumber()
            self.addHelix(polymer,int(startRes),int(startPairRes),int(endRes),int(endPairRes))

    def addHelix(self, polymer, hStart, hStartPair, hEnd, hEndPair,convertNums=True):
        residues = polymer.getResidues()
        if not convertNums:
            iStart = hStart
            iEnd = hEnd
            iStartPair = hStartPair
            iEndPair = hEndPair
        else:
            hStart = str(hStart)
            hStartPair = str(hStartPair)
            hEnd = str(hEnd)
            hEndPair = str(hEndPair)
            for i,residue in enumerate(residues):
                resNum = residue.getNumber()
                if resNum == hStart:
                    iStart = i
                if resNum == hStartPair:
                    iStartPair = i
                if resNum == hEnd:
                    iEnd = i
                if resNum == hEndPair:
                    iEndPair = i
        length = iEnd-iStart+1
        for i in range(length):
            resI = residues[iStart+i].getNumber()
            resJ = residues[iStartPair-i].getNumber()
            self.addSuiteBoundary(polymer, resI,"1a")
            import java.lang
            try:
                self.addSuiteBoundary(polymer, resJ,"1a")
            except:
                print "Preceding residue is not defined"
            resJName = residues[iStartPair-i].getName()
            self.addBasePair(polymer, resI, resJ)
            if (i+3) < length:
                resJ = residues[iStart+i+3].getNumber()
                self.energyLists.addDistanceConstraint(str(resI)+'.P', str(resJ)+'.P',16.5, 20.0)
            if (i+5) < length:
                resJ = residues[iStartPair-i-5].getNumber()
                self.energyLists.addDistanceConstraint(str(resI)+'.P', str(resJ)+'.P',10.0, 12.0)
            if (i+1) < length:
                resJ = residues[iStart+i+1].getNumber()
                self.addStackPair(polymer, resI, resJ)
                resI = residues[iEndPair+i].getNumber()
                resJ = residues[iEndPair+i+1].getNumber()
                self.addStackPair(polymer, resI, resJ)
                resI = residues[iEnd-i].getNumber()
                resJ = residues[iEndPair+i+1].getNumber()
                resJName = residues[iEndPair+i+1].getName()
                if (resJName == "A"):
                    self.energyLists.addDistanceConstraint(str(resI)+".H1'", str(resJ)+".H2",1.8, 5.0)
    def findHelices(self,vienna):
        gnraPat = re.compile('G[AGUC][AG]A')
        uncgPat = re.compile('U[AGUC]CG')

        pairs = self.getPairs(vienna)

        i = 0
        sets = []

        helix = False
        beginSet=[]
        endSet=[]
        while i != len(pairs)-1:
            if pairs[i] != -1:
                if i > pairs[i]:
                    i+=1
                    continue
                if not helix:
                    helix = True
                    beginSet.append(i)
                    endSet.append(pairs[i])
            else:
                if helix:
                    helix = False
                    beginSet.append(i-1)
                    endSet.insert(0,pairs[i-1])
                    sets.append(beginSet+endSet)
                    beginSet = []
                    endSet = []
            i+=1
        polymers = self.molecule.getPolymers()
        allResidues = []
        for polymer in polymers:
            allResidues += polymer.getResidues()
            for set in sets:
                self.addHelix(polymer,set[0],set[3],set[1],set[2],False)
        pat = re.compile('\(\(\.\.\.\.\)\)')
        for m in pat.finditer(vienna):
            gnraStart = m.start()+2
            tetraLoopSeq = ""
            tetraLoopRes = []
            for iRes in range(gnraStart,gnraStart+4):
                residue = allResidues[iRes]
                tetraLoopSeq += residue.getName()
                tetraLoopRes.append(residue.getNumber())
            if gnraPat.match(tetraLoopSeq):
                res2 = allResidues[m.start()+3].getNumber()
                res3 = allResidues[m.start()+4].getNumber()
                res4 = allResidues[m.start()+5].getNumber()
                res5 = allResidues[m.start()+6].getNumber()
                self.addSuiteBoundary(polymer, res2,"1g")
                self.addSuiteBoundary(polymer, res3,"1a")
                self.addSuiteBoundary(polymer, res4,"1a")
                self.addSuiteBoundary(polymer, res5,"1c")

    def addBasePair(self, polymer, resNumI, resNumJ):
        resNumI = str(resNumI)
        resNumJ = str(resNumJ)
        resI = polymer.getResidue(resNumI)
        resJ = polymer.getResidue(resNumJ)
        resNameI = resI.getName()
        resNameJ = resJ.getName()
        dHN = 1.89
        dHNlow = 1.8
        dHO = 1.89
        dHOlow = 1.8
        dNN = 3.0
        dNNlow = 2.9
        dNO = 3.0
        dNOlow = 2.9
        namePairs = {}
        namePairs['CG'] = (("N3","H1", dHNlow,dHN), ("N3","N1",dNNlow,dNN),("O2","H21",dHOlow,dHO),("O2","N2", dNOlow,dNO),
                           ("H41","O6",dHOlow,dHO),("N4","O6",dNOlow,dNO),
                           ("C6","C4",8.3,11.3),("H6","N9",10.75,13.75))
        namePairs['GC'] = (("H1","N3", dHNlow,dHN),("N1","N3",dNNlow,dNN),("H21","O2",dHOlow,dHO),("N2","O2", dNOlow,dNO),
                           ("O6","H41",dHOlow,dHO), ("O6","N4",dNOlow,dNO),
                           ("C4","C6",8.3,11.3),("N9","H6",10.75,13.75))
        namePairs['UA'] = (("H3","N1", dHNlow,dHN), ("N3","N1",dNNlow,dNN),("O4","H61",dHOlow,dHO),("O4","N6",dNOlow,dNO),
                           ("C6","C4",8.3,11.3),("H6","N9",10.75,13.75))
        namePairs['AU'] = (("N1","H3", dHNlow,dHN), ("N1","N3",dNNlow,dNN),("H61","O4",dHOlow,dHO),("N6","O4",dNOlow,dNO),
                           ("C4","C6",8.3,11.3),("N9","H6",10.75,13.75))
        namePairs['UG'] = (("O2","H1", dHOlow,dHO), ("O2","N1",dNOlow,dNO),("H3","O6",dHOlow,dHO),("N3","O6",dNOlow,dNO),
                           ("C6","C8",9.8,12.8),("C5","N9",9.5,12.5))
        namePairs['GU'] = (("H1","O2", dHOlow,dHO), ("N1","O2",dNOlow,dNO),("O6","H3",dHOlow,dHO),("O6","N3",dNOlow,dNO),
                           ("C8","C6",9.8,12.8),("N9","C5",9.5,12.5))



        pairName = resNameI+resNameJ
        if not pairName in namePairs:
            print("No data for pair " + pairName)
        else:
            pairs = namePairs[pairName]
            for pair in pairs:
                aNameI, aNameJ,lower,upper = pair
                atomNameI = resNumI+'.'+aNameI
                atomNameJ = resNumJ+'.'+aNameJ
                self.energyLists.addDistanceConstraint(atomNameI,atomNameJ,lower,upper)

    def addStackPair(self, polymer, resNumI, resNumJ):
        resNumI = str(resNumI)
        resNumJ = str(resNumJ)
        resI = polymer.getResidue(resNumI)
        resJ = polymer.getResidue(resNumJ)
        resNameI = resI.getName()
        resNameJ = resJ.getName()
        dHN = 1.89
        dHO = 1.89
        dNN = 3.0
        dNO = 3.0

        stackTo = {}
        stackTo['C'] = (("H2'","H6",4.0,2.7), ("H3'","H6",3.0,3.3))
        stackTo['U'] = (("H2'","H6",4.0,2.7), ("H3'","H6",3.0,3.3))
        stackTo['G'] = (("H2'","H8",4.0,2.7), ("H3'","H8",3.0,3.3))
        stackTo['A'] = (("H2'","H8",4.0,2.7), ("H3'","H8",3.0,3.3))

        stackPairs = {}
        stackPairs['CU'] = [("H6","H5",1.8,5.0), ("H6","H6",1.8,5.0)]
        stackPairs['CC'] = [("H6","H5",1.8,5.0), ("H6","H6",1.8,5.0)]
        stackPairs['CG'] = [("H6","H8",1.8,5.0), ("H5","H8",1.8,5.0)]
        stackPairs['CA'] = [("H6","H8",1.8,5.0), ("H5","H8",1.8,5.0)]

        stackPairs['UU'] = [("H6","H5",1.8,5.0), ("H6","H6",1.8,5.0)]
        stackPairs['UC'] = [("H6","H5",1.8,5.0), ("H6","H6",1.8,5.0)]
        stackPairs['UG'] = [("H6","H8",1.8,5.0), ("H5","H8",1.8,5.0)]
        stackPairs['UA'] = [("H6","H8",1.8,5.0), ("H5","H8",1.8,5.0)]

        stackPairs['GU'] = [("H8","H6",1.8,5.0), ("H8","H5",1.8,5.0)]
        stackPairs['GC'] = [("H8","H6",1.8,5.0), ("H8","H5",1.8,5.0)]
        stackPairs['GG'] = [("H8","H8",1.8,5.0)]
        stackPairs['GA'] = [("H8","H8",1.8,5.0)]

        stackPairs['AU'] = [("H8","H6",1.8,5.0), ("H8","H5",1.8,5.0),("H2","H1'",1.8,5.0)]
        stackPairs['AC'] = [("H8","H6",1.8,5.0), ("H8","H5",1.8,5.0),("H2","H1'",1.8,5.0)]
        stackPairs['AG'] = [("H8","H8",1.8,5.0),("H2","H1'",1.8,5.0)]
        stackPairs['AA'] = [("H8","H8",1.8,5.0),("H2","H1'",1.8,5.0)]

        stacks = stackTo[resNameI]
        for stack in stacks:
            aNameI, aNameJ,intra,inter = stack
            lowerIntra = intra - 1.0
            atomNameI = resNumI+'.'+aNameI
            atomNameJ = resNumI+'.'+aNameJ
            self.energyLists.addDistanceConstraint(atomNameI,atomNameJ,lowerIntra,intra)

        stacks = stackTo[resNameJ]
        for stack in stacks:
            aNameI, aNameJ,intra,inter = stack
            lowerInter = inter - 1.0
            atomNameI = resNumI+'.'+aNameI
            atomNameJ = resNumJ+'.'+aNameJ
            self.energyLists.addDistanceConstraint(atomNameI,atomNameJ,lowerInter,inter)

        pairName = resNameI+resNameJ
        if not pairName in stackPairs:
            print("No data for pair " + pairName)
        else:
            pairs = stackPairs[pairName]
            for pair in pairs:
                aNameI, aNameJ,lower,upper = pair
                atomNameI = resNumI+'.'+aNameI
                atomNameJ = resNumJ+'.'+aNameJ
                self.energyLists.addDistanceConstraint(atomNameI,atomNameJ,lower,upper)


    def readSequenceString(self, molName, sequence):
        seqAList = ArrayList()
        for res in sequence:
            seqAList.add(res)
        seqReader = Sequence()
        self.molecule = seqReader.read(molName, seqAList, "")
        self.molName = self.molecule.getName()
        return self.molecule

    def readSequence(self,seqFile):
        seqReader = Sequence()
        if (self.reslib):
            PDBFile.setLocalResLibDir(self.reslib)
        self.molecule = seqReader.read(seqFile)
        self.molecule.selectAtoms('*.*')
        self.molName = self.molecule.getName()
        return self.molecule

    def readPDBFile(self,fileName):
        pdb = PDBFile()
        pdb.readSequence(fileName,0)
        self.molecule = Molecule.getActive()
        self.molName = self.molecule.getName()
        self.molecule.selectAtoms('*.*')
        return self.molecule

    def readPDBFiles(self,files):
        fileName = files[0]
        pdb = PDBFile()
        pdb.readSequence(fileName,0)
        self.molecule = Molecule.getActive()
        iFile = 1
        for file in files:
            pdb.readCoordinates(file,iFile,False)
            iFile += 1
        return self.molecule

    def readPDBFileNL(self,fileName):
        pdb = PDBFile()
        pdb.read(fileName)
        self.molecule = Molecule.getActive()

        self.molName = self.molecule.getName()
        self.molecule.selectAtoms('*.*')
        return self.molecule

    def readSDFile(self,fileName):
        pdb = SDFile()
        if (not self.molecule):
            pdb.read(fileName, None)
            self.molecule = Molecule.getActive()
            self.molName = self.molecule.getName()
            self.molecule.selectAtoms('*.*')
        else:
            pdb.readResidue(fileName, None, self.molecule, None)
        return self.molecule

    def setupTree(self, start, end):
        mol = self.molecule
        ligands = mol.getLigands()
        polymers = mol.getPolymers()
        for polymer in polymers:
            pI = PathIterator(polymer)
            nodeValidator = NodeValidator()
            pI.init(nodeValidator)
            pI.processPatterns()
            pI.setProperties("ar", "AROMATIC");
            pI.setProperties("res", "RESONANT");
            pI.setProperties("r", "RING");
            pI.setHybridization();
            atoms = polymer.getAtoms()
            if start != None:
                startAtom = polymer.getAtom(start)
            else:
                startAtom = None
            if end != None:
                endAtom = polymer.getAtom(end)
            else:
                endAtom = None
        for ligand in ligands:
            pI = PathIterator(ligand)
            nodeValidator = NodeValidator()
            pI.init(nodeValidator)
            pI.processPatterns()
            pI.setProperties("ar", "AROMATIC");
            pI.setProperties("res", "RESONANT");
            pI.setProperties("r", "RING");
            pI.setHybridization();
            atoms = ligand.getAtoms()
            if start != None:
                startAtom = ligand.getAtom(start)
            else:
                startAtom = None
            if end != None:
                endAtom = ligand.getAtom(end)
            else:
                endAtom = None
        if start != None:
            startAtom = Molecule.getAtomByName(start)
        else:
            startAtom = None
        mol.genMeasuredTree(startAtom)

    def addAngleFile(self,file, mode='nv'):
        if mode == 'cyana':
            self.cyanaAngleFiles.append(file)
        elif mode == 'xplor':
            self.xplorAngleFiles.append(file)
        else:
            self.nvAngleFiles.append(file)

    def setMolecule(self, molecule):
        Molecule.setActive(molecule)
        self.molecule = molecule
        self.molName = self.molecule.getName()

    def addAngle(self,angleString):
        self.angleStrings.append(angleString)

    def addDistanceFile(self, file, mode='nv'):
        if mode == 'cyana':
            self.cyanaDistanceFiles.append(file)
        elif mode == 'xplor':
            self.xplorDistanceFiles.append(file)
        else:
            self.nvDistanceFiles.append(file)

    def readAngleFiles(self):
        for file in self.cyanaAngleFiles:
            self.readCYANAAngles(file,self.molName)
        for file in self.xplorAngleFiles:
            xplorFile = xplor.XPLOR(file)
            xplorFile.readXPLORAngleConstraints(self.dihedral)
        for file in self.nvAngleFiles:
            self.loadDihedralsFromFile(file)

    def readDistanceFiles(self):
        for file in self.cyanaDistanceFiles:
            lowerFileName = file+'.lol'
            upperFileName = file+'.upl'
            self.readCYANADistances([lowerFileName, upperFileName], self.molName)
        for file in self.xplorDistanceFiles:
            xplorFile = xplor.XPLOR(file)
            xplorFile.readXPLORDistanceConstraints(self.energyLists)
        for file in self.nvDistanceFiles:
            self.loadDistancesFromFile(file)

    def predictShifts(self):
        from org.nmrfx.structure.chemistry.energy import RingCurrentShift
        from org.nmrfx.structure.chemistry import MolFilter
        refShifts = {"A.H2":7.93, "A.H8":8.33, "G.H8":7.87, "C.H5":5.84, "U.H5":5.76,
            "C.H6":8.02, "U.H6":8.01, "A.H1'":5.38, "G.H1'":5.37, "C.H1'":5.45,
            "U.H1'":5.50, "A.H2'":4.54, "G.H2'":4.59, "C.H2'":4.54, "U.H2'":4.54,
            "A.H3'":4.59, "G.H3'":4.59, "C.H3'":4.59, "U.H3'":4.59
        }

        ringShifts = RingCurrentShift()
        ringShifts.makeRingList(self.molecule)
        filterString = "*.H8,H6,H2,H1',H2'"

        molFilter = MolFilter(filterString)
        spatialSets = Molecule.matchAtoms(molFilter)

        ringRatio = 0.56
        shifts = []
        for sp in spatialSets:
            name = sp.atom.getShortName()
            aName = sp.atom.getName()
            nucName = sp.atom.getEntity().getName()

            basePPM = refShifts[nucName+"."+aName]
            ringPPM = ringShifts.calcRingContributions(sp,0,ringRatio)
            ppm = basePPM+ringPPM

            atom = Molecule.getAtomByName(name)
            atom.setRefPPM(ppm)

            shift = []
            shift.append(str(name))
            shift.append(ppm)
            shifts.append(shift)
        return shifts

    def setBasePPMs(self,filterString="*.H8,H6,H5,H2,H1',H2',H3'"):
        self.energyLists.setRingShifts(filterString)
        atoms = self.energyLists.getRefAtoms()

    def setShifts(self,shiftFile):
        file = open(shiftFile,"r")
        data = file.read()
        file.close()
        lines = data.split('\n')
        shifts = []
        for line in lines:
            if line == "":
                 continue
            arr = line.split()
            atomName = arr[0]
            atom = Molecule.getAtomByName(atomName)
            self.energyLists.addAtomRef(atom)
            ppm = float(arr[1])
            atom.setPPM(ppm)
            shifts.append(arr)
        return shifts



    def setup(self,homeDir,seed,writeTrajectory=False,usePseudo=False, useShifts = False):
        self.seed = seed
        self.eTimeStart = time.time()
        self.useDegrees = False

        self.setupEnergy(self.molName,usePseudo=usePseudo,useShifts=useShifts)
        self.loadDihedrals(self.angleStrings)
        self.addRingClosures() # Broken bonds are stored in molecule after tree generation. This is to fix broken bonds
        self.setForces(repel=0.5,dis=1,dih=5)
        self.setPars(coarse=False,useh=False,dislim=self.disLim,end=2,hardSphere=0.15,shrinkValue=0.20)
        if writeTrajectory:
            self.trajectoryWriter = TrajectoryWriter(self.molecule,"output.traj","traj")
            selection = "*.ca,c,n,o,p,o5',c5',c4',c3',o3'"
            self.molecule.selectAtoms(selection)
            self.molecule.setAtomProperty(2,True)

    def addRingClosures(self):
        ringClosures = self.molecule.getRingClosures();
        if ringClosures:
            for atom1 in ringClosures:
                atomName1 = atom1.getFullName()
                for atom2 in ringClosures[atom1]:
                    atomName2 = atom2.getFullName()
                    self.energyLists.addDistanceConstraint(atomName1, atomName2, ringClosures[atom1][atom2]-.01, ringClosures[atom1][atom2]+.01, True)


    def prepare(self,steps=1000, gsteps=300, alg='cmaes'):
        ranfact=20.0
        self.setSeed(self.seed)
        self.putPseudo(18.0,45.0)
        self.randomizeAngles()
        energy = self.energy()
        self.updateAt(5)
        self.setForces(repel=0.5,dis=1,dih=5)
        self.setPars(useh=False,dislim=self.disLim,end=2,hardSphere=0.0,shrinkValue=0.20)
        if steps > 0:
            self.refine(nsteps=steps,radius=20, alg=alg);
        if gsteps > 0:
            self.gmin(nsteps=gsteps,tolerance=1.0e-10)
        self.setPars(useh=False,dislim=self.disLim,end=1000,hardSphere=0.0,shrinkValue=0.20)
        self.gmin(nsteps=100,tolerance=1.0e-6)
        if self.eFileRoot != None:
            self.dump(0.1,self.eFileRoot+'_prep.txt')


    def annealPrep(self,dOpt, steps=100):
        ranfact=20.0
        self.setSeed(self.seed)
        #self.putPseudo(18.0,45.0)

        self.randomizeAngles()
        energy = self.energy()
        irp = dOpt.irpWeight

        self.updateAt(5)
        self.setForces(repel=0.5,dis=1,dih=5,irp=irp)

        for end in [3,10,20,1000]:
            self.setPars(useh=False,dislim=self.disLim,end=end,hardSphere=0.15,shrinkValue=0.20)
            self.gmin(nsteps=steps,tolerance=1.0e-6)

        if self.eFileRoot != None:
            self.dump(-1.0,-1.0,self.eFileRoot+'_prep.txt')

    def anneal(self,dOpt=None,stage1={},stage2={}):
        if (dOpt==None):
            dOpt = dynOptions()

        self.annealPrep(dOpt, 100)

        self.updateAt(dOpt.update)
        irp = dOpt.irpWeight
        swap = dOpt.swap
        self.setForces(repel=0.5,dis=1.0,dih=5,irp=irp)
        self.setPars(end=1000,useh=False,hardSphere=0.15,shrinkValue=0.20, swap=swap)
        self.setPars(optDict=stage1)
        energy = self.energy()

        rDyn = self.rinertia()
        rDyn.setKinEScale(dOpt.kinEScale)

        steps = dOpt.steps
        stepsEnd = dOpt.stepsEnd
        stepsHigh = int(round(steps*dOpt.highFrac))
        stepsAnneal1 = int(round((steps-stepsEnd-stepsHigh)*dOpt.toMedFrac))
        stepsAnneal2 = steps-stepsHigh-stepsEnd-stepsAnneal1

        timeStep = dOpt.timeStep
        highTemp = dOpt.highTemp
        medTemp = round(dOpt.highTemp * dOpt.medFrac)
        econHigh = dOpt.econHigh
        econLow = dOpt.econLow
        switchFrac = dOpt.switchFrac
        timePowerHigh = dOpt.timePowerHigh
        timePowerMed = dOpt.timePowerMed
        minSteps = dOpt.minSteps
        polishSteps = dOpt.polishSteps

        rDyn.initDynamics2(highTemp,econHigh,stepsHigh,timeStep)
        rDyn.run()

        timeStep = rDyn.getTimeStep()/2.0

        tempLambda = lambda f: (highTemp - medTemp) * pow((1.0 - f), timePowerHigh) + medTemp

        rDyn.continueDynamics2(tempLambda,econHigh,stepsAnneal1,timeStep)
        rDyn.run()

        self.setPars(useh=False,hardSphere=0.0,shrinkValue=0.0, swap=swap)
        self.gmin(nsteps=minSteps,tolerance=1.0e-6)

        timeStep = rDyn.getTimeStep()/2.0
        tempLambda = lambda f: (medTemp - 1.0) * pow((1.0 - f), timePowerMed) + 1.0
        econLambda = lambda f: econHigh*(pow(0.5,f))

        rDyn.continueDynamics2(tempLambda,econLambda,stepsAnneal2,timeStep)
        rDyn.run(switchFrac)

        self.setForces(repel=1.0, bondWt = 25.0)
        self.setPars(useh=True,hardSphere=0.0,shrinkValue=0.0,shrinkHValue=0.0, swap=swap)
        self.setPars(optDict=stage2)

        timeStep = rDyn.getTimeStep()/2.0
        self.gmin(nsteps=minSteps,tolerance=1.0e-6)
        rDyn.continueDynamics2(timeStep)
        rDyn.run()

        timeStep = rDyn.getTimeStep()/2.0
        self.setForces(repel=2.0)
        self.gmin(nsteps=minSteps,tolerance=1.0e-6)
        rDyn.continueDynamics2(0.0,econLow,stepsEnd,timeStep )
        rDyn.run()
        self.gmin(nsteps=polishSteps,tolerance=1.0e-6)

    def cdynamics(self, steps, hiTemp, medTemp, timeStep=1.0e-3):
        self.updateAt(20)
        self.setForces(repel=5.0,dis=1.0,dih=5)
        self.setPars(coarse=True, end=1000,useh=False,hardSphere=0.15,shrinkValue=0.20)
        rDyn = self.rinertia()
        steps0 =  5000
        steps1 = (steps-steps0)/3
        steps2 = steps-steps0-steps1

        rDyn.initDynamics(hiTemp,hiTemp,steps0,timeStep)
        rDyn.run(1.0)

        timeStep = rDyn.getTimeStep()/2.0
        rDyn.continueDynamics(hiTemp,medTemp,steps1,timeStep)
        rDyn.run(1.0)

        timeStep = rDyn.getTimeStep()/2.0
        rDyn.continueDynamics(medTemp,2.0,steps2,timeStep)
        rDyn.run(0.65)

        self.setPars(useh=True,shrinkValue=0.05,shrinkHValue=0.05)

        timeStep = rDyn.getTimeStep()/2.0
        rDyn.continueDynamics(timeStep)
        rDyn.run(0.35)


        if self.eFileRoot != None:
            self.dump(0.1,self.eFileRoot+'_dyn.txt')


    def dynrun(self, steps, temp, timeStep=1.0e-3, timePower=4.0, stage1={}):
        self.updateAt(20)
        self.setForces(repel=0.5,dis=1.0,dih=5)
        self.setPars(end=1000,useh=False,hardSphere=0.15,shrinkValue=0.20)
        self.setPars(optDict=stage1)
        rDyn = self.rinertia()
        rDyn.initDynamics(temp,temp,steps,timeStep, timePower)
        rDyn.run(1.0)

    def sgd(self,dOpt=None,stage1={},stage2={}):
        if (dOpt==None):
            dOpt = dynOptions()

        self.annealPrep(dOpt, 100)

        self.updateAt(dOpt.update)
        irp = dOpt.irpWeight
        self.setForces(repel=0.5,dis=1.0,dih=5,irp=irp)
        self.setPars(end=1000,useh=False,hardSphere=0.15,shrinkValue=0.20)
        self.setPars(optDict=stage1)
        energy = self.energy()

        steps = dOpt.steps
        self.sgdmin(2*steps/3)
        self.setPars(useh=True,hardSphere=0.0,shrinkValue=0.0,shrinkHValue=0.0)
        self.sgdmin(steps/3)


    def polish(self, steps, usePseudo=False, stage1={}):
        self.refine(nsteps=steps/2,useDegrees=self.useDegrees,radius=0.1);
        self.setForces(repel=2.0,dis=1.0,dih=5)
        self.setPars(dislim=self.disLim,end=1000,useh=True,shrinkValue=0.07,shrinkHValue=0.00)
        self.setPars(optDict=stage1)
        self.usePseudo(usePseudo)
        self.refine(nsteps=steps/2,radius=0.01);
        self.gmin(nsteps=800,tolerance=1.0e-10)

        self.usePseudo(usePseudo)
        self.refine(nsteps=500,radius=0.01);
        self.gmin(nsteps=400,tolerance=1.0e-10)
        self.gmin(nsteps=400,tolerance=1.0e-10)
        if self.eFileRoot != None:
            self.dump(0.1,self.eFileRoot+'_polish.txt')

    def output(self):
        energy = self.energy()
        if self.outDir != None:
            import osfiles
            angleFile = osfiles.getAngleFile(self)
            pdbFile = osfiles.getPDBFile(self)
            energyFile = osfiles.getEnergyFile(self)

            self.writeAngles(angleFile)
            savePDB(self.molecule, pdbFile)
            strOutput = "%d %.2f\n" % (self.seed,energy)
            osfiles.logEnergy(self, strOutput)
            self.dump(0.1,0.2,energyFile)

        eTimeTotal = time.time()-self.eTimeStart
        print 'energy is', energy
        print 'etime ',eTimeTotal

    def getPairs(self, vienna):
        sArray = [len(vienna)]
        seqSize = array.array('i',sArray)
        ssLay = SSLayout(seqSize)
        ssLay.interpVienna(vienna)
        pairs = ssLay.getBasePairs()
        return pairs

    def dumpDis(self, fileName, delta=0.5, atomPat='*.H*',maxDis=4.5,prob=1.1,fixLower=0.0):
        molecule = self.molecule
        self.molecule.selectAtoms(atomPat)
        pairs =  molecule.getDistancePairs(maxDis,False)
        with open(fileName,'w') as fOut:
            for pair in pairs:
                if prob < 1.0:
                    r = random.random()
                    if r > prob:
                        continue
                (atom1,atom2,distance) = pair.toString().split()
                (res1,aname1) = atom1[2:].split('.')
                (res2,aname2) = atom2[2:].split('.')
                atom1 = res1+'.'+aname1
                atom2 = res2+'.'+aname2
                distance = float(distance)
                if res1 != res2:
                    upper = distance + delta
                    if fixLower > 1.0:
                        lower = fixLower
                    else:
                        lower = distance - delta
                    outStr = "%s %s %.1f %.1f\n" % (atom1,atom2,lower,upper)
                    fOut.write(outStr)

    def dumpAngles(self, fileName, delta=10):
        molecule = self.molecule
        molecule.setupAngles()
        angleAtoms = molecule.getAngleAtoms()
        with open(fileName,'w') as fOut:
            for atom in angleAtoms:
                parent = atom.getParent()
                if parent != None:
                    grandParent = parent.getParent()
                    if grandParent != None:
                        greatGrandParent = grandParent.getParent()
                        if greatGrandParent != None:
                            atoms = [greatGrandParent,grandParent,parent,atom]
                            dihedral = molecule.calcDihedral(atoms) * 180.0/math.pi
                            lower = dihedral - delta
                            upper = dihedral + delta
                            if (lower < -180):
                                lower += 360
                                upper += 360

                            scale = 0.05
                            outStr = "%s %.1f %.1f %.2f\n" % (atom.getShortName(),lower, upper, scale)
                            fOut.write(outStr)

    def setPeptideDihedrals(self, phi, psi):
        molecule = self.molecule
        polymers = self.molecule.getPolymers()
        for polymer in polymers:
            for residue in polymer.getResidues():
                resNum = residue.getNumber()
                atom = residue.getAtom("C")
                atom.dihedralAngle = math.pi*phi/180.0
                atom = residue.getAtom("N")
                atom.dihedralAngle = math.pi*psi/180.0
        molecule.genCoords()

def compileLCMB(mol):
    polymers = mol.getPolymers()

    for polymer in polymers:
        lcmbs = []
        residues = []
        for residue in polymer.iterator():
            sum = 0.0
            atoms = residue.getAtoms()
            if False:
                atomNames = ('N','CA','C')
                atomNames = ("P","C3'","C4'")
                atoms = []
                for atomName in atomNames:
                    atom = residue.getAtom(atomName)
                    atoms.append(atom)
            for atom in atoms:
                if (atom != None) and (atom.getAtomicNumber() > 1):
                    sum += atom.getBFactor()
            f = 1.0*sum
            lcmbs.append(f)
            residues.append(residue.getNumber())
        lcmbs = seqalgs.smoothShifts(lcmbs)
        for residue,lcmb in zip(residues,lcmbs):
            print residue,lcmb


def calcLCMB(mol, scaleEnds, iStruct=0):
    mol.calcLCMB(iStruct, scaleEnds)

def calcOrder(mol, scaleEnds, iStruct=0):
    mol.calcContactOrder(iStruct, scaleEnds)

def doAnneal(seed,dOpt=None,homeDir=None, writeTrajectory=False):
    import osfiles
    refiner = refine()
    dataDir = osfiles.getDataDir(homeDir)
    osfiles.setOutFiles(refiner,dataDir, seed)
    osfiles.guessFiles(refiner, homeDir)
    refiner.molecule.setMethylRotationActive(True)
    refiner.molecule.setRiboseActive(True)
    refiner.setup(dataDir,seed,writeTrajectory)
    refiner.rootName = "temp"
    if dOpt == None:
        dOpt = dynOptions(highFrac=0.4)
    refiner.anneal(dOpt)
    refiner.output()

def doSGD(seed,homeDir=None):
    import osfiles
    refiner = refine()
    dataDir = osfiles.getDataDir(homeDir)
    osfiles.setOutFiles(refiner,dataDir, seed)
    osfiles.guessFiles(refiner, homeDir)
    refiner.molecule.setMethylRotationActive(True)
    refiner.setup(dataDir,seed)
    refiner.rootName = "temp"
    dOpt = dynOptions(150000,highFrac=0.4)
    refiner.sgd(dOpt)
    refiner.output()
