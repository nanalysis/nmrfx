import math
import time
import array
import random
import seqalgs
import re
import xplor
import molio
import os

import pdb
from org.nmrfx.structure.chemistry.energy import EnergyCoords
from org.nmrfx.structure.chemistry import Molecule
from org.nmrfx.structure.chemistry import SSGen
from org.nmrfx.structure.chemistry import Atom
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
from org.nmrfx.structure.chemistry import Polymer
from org.nmrfx.structure.chemistry import AllBasePairs

from org.nmrfx.structure.chemistry.miner import PathIterator
from org.nmrfx.structure.chemistry.miner import NodeValidator
from org.nmrfx.structure.chemistry.energy import AngleTreeGenerator

#from tcl.lang import NvLiteShell
#from tcl.lang import Interp
from java.lang import String, NullPointerException, IllegalArgumentException
from java.util import ArrayList
from org.nmrfx.structure.chemistry.constraints import RDC
from org.nmrfx.structure.chemistry.constraints import RDCConstraintSet
from org.nmrfx.structure.chemistry import SpatialSet

#tclInterp = Interp()
#tclInterp.eval("puts hello")
#tclInterp.eval('package require java')
#tclInterp.eval('java::load org.nmrfx.structure.chemistry.ChemistryExt')


protein3To1 = {"ALA":"A","ASP":"D","ASN":"N","ARG":"R","CYS":"C","GLU":"E","GLN":"Q","ILE":"I",
    "VAL":"V","LEU":"L","PRO":"P","PHE":"F","TYR":"Y","TRP":"W","LYS":"K","MET":"M",
    "HIS":"H","GLY":"G","SER":"S","THR":"T"}

bondOrders = ('SINGLE','DOUBLE','TRIPLE','QUAD')

protein1To3 = {protein3To1[key]: key for key in protein3To1}

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

def getSequenceArray(indexing,seqString,linkers,polyType):
    ''' getSequenceArray takes a seqString and returns a list of strings with
        the residue name followed by the residue number. The residue number is
        determined from the indexing parameter passed in, which can be either a
        single int or a string of multiple indexing partitions. Linkers can also
        be added using a string. The returned value is a java arrayList type to
        be passed into a sequenceReader
    '''
    indexing = indexing if indexing else 1;
    resNames = [char.upper() if polyType == "RNA" else protein1To3[char.upper()] for char in seqString]
    linkers = [linker.split(':') for linker in linkers.split()] if linkers else []
    linkers = {int(i): int(n) for i,n in linkers} # resNum to number of linkers
    try :
        resNums = range(int(indexing),int(indexing)+len(seqString))
    except ValueError:
        resNums = []
        for section in indexing.split():
            indices = section.split(':')
            if len(indices) == 1:
               startIndex = int(indices[0])
               endIndex = startIndex
            else: 
                startIndex, endIndex = [int(i) for i in indices]
            resNums += range(startIndex, endIndex+1)

    if len(resNums) != len(resNames):
        raise IndexError('The indexing method cannot be applied to the given sequence string')

    seqArray = []
    for i, resNum in enumerate(resNums):
        resString = ' '.join([resNames[i], str(resNum)])
        seqArray.append(resString)
        nLinkers = linkers.get(resNum)
        if not nLinkers :
            continue
        for j in range(1, nLinkers + 1):
            linkerName = 'ln2' if j == 1 or j == nLinkers else 'ln5'
            linkerIndex = resNum + j
            if (linkerIndex == resNums[i+1]):
                raise IndexError('Linker numbers overlap with residue numbers')
            resString = ' '.join([linkerName, str(linkerIndex)])
            seqArray.append(resString)
        del linkers[resNum]
    if len(linkers) > 0:
        raise IndexError('Linkers not created. No residues have a specified linker index')

    return seqArray

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

class Constraint:
    def __init__(self, pair, distance, mode, setting = None):
        Constraint.lastViewed = self
        if not isinstance(distance,float):
            raise ValueError("Value " + distance + " not float")
        self.pairs = [pair]
        self.lower = distance if mode == 'lower' else None
        self.upper = distance if mode == 'upper' else None
        self.tester = 0 if not setting else -1 if setting == 'narrow' else 1
        self.rdc = None
        self.err = None

    def addBound(self, distance, mode):
        if not isinstance(distance,float):
            raise ValueError("Value " + distance + " not float")
        self.lower = distance if mode == 'lower' and (not self.lower or ((self.lower-distance)*self.tester >= 0 )) else self.lower
        self.upper = distance if mode == 'upper' and (not self.upper or ((distance - self.upper)*self.tester >= 0)) else self.upper
        Constraint.lastViewed = self

    def addRDC(self, rdc, err):
        if not isinstance(rdc,float):
            raise ValueError("Value " + rdc + " not float")
        if not isinstance(err,float):
            raise ValueError("Value " + err + " not float")
        self.rdc = rdc
        self.err = err
        Constraint.lastViewed = self

    def addPair(self, pair):
        if pair not in self.pairs:
            self.pairs.append(pair)


class StrictDict(dict):
    """
    This subclass inherits from dict.
    Helps prevent the creation of keys that do not exist in a map of default parameters.

    # Attributes:

    * defaultErr (string); a string used to convey descriptive errors about invalid input settings
    * defaultDict (dict); dictionary that contains parameters and default values for dynamic simulation

    # Methods:

    * setInclusions
    * strictUpdate
    """


    def __init__(self, defaultErr='this setting', defaultDict={}):
        self.update(defaultDict)
        self.defaultErr = defaultErr
        self.allowedKeys = []

    def setInclusions(self, allowedKeys):
        """Based on type (param or forces) specified into createStrictDict, a list of valid parameters will be initialized.
        If user specifies a key to update in input dictionary which is not in this list, an error will be raised.

	# Parameters:

        allowedKeys (list); list to identify allowed keys based on input specification 
        for the type of parameters/keys to update. 

        # Returns:

        _ (None); sets the self.allowedKeys empty list to a list of parameters that are allowed to update.

        See also: `createStrictDict(...)`
        """
        self.allowedKeys = allowedKeys

    def strictUpdate(self, changesDict={}):
        """
        # Parameters:

        changesDict (dict); a dictionary provided to update specified parameters/keys with new values. 

        # Returns:

        _ (None); If valid parameter/key in changesDict, then value will be updated. Otherwise, throws error.
    
        See also: `createStrictDict(...)`
        """
        if changesDict is None:
            changesDict = {}
        for key in changesDict:
            if key not in self and key not in self.allowedKeys:
                raise KeyError("{} is not a valid option for {}".format(key,self.defaultErr))
            else:
                self[key] = changesDict[key]

class dynOptions(StrictDict):
    """
    This subclass inherits from the StrictDict subclass. 
    Contains a dictionary with default parameters and values used for the dynamic simulation. 
    These default dictionary keys cannot be changed or removed. Users can only update the strict 
    dictionary only if the keys specifed in dictionary used to update parameters exists within the 
    list referenced by the strictDict instance variable 'allowedKeys'. Otherwise an error will be thrown. 
    The values can be updated by instantiating a dictionary of known key parameters and values 
    which would override the default values.

    # Attributes:

    * defaults (dict); keys correspond to parameters used in the dynamic simulation, values are default values chosen
    * initDict (dict); empty dictionary that can be used to update the values of parameters using method in StrictDict
    """

    defaults = {
        'steps'         : 15000,
        'highTemp'      : 5000.0,
        'medFrac'       : 0.05,
        'update'        : 20,
        'highFrac'      : 0.3,
        'toMedFrac'     : 0.5,
        'switchFrac'    : 0.65,
        'timeStep'      : 4.0,
        'stepsEnd'      : 100,
        'econHigh'      : 0.005,
        'econLow'       : 0.001,
        'timePowerHigh' : 4.0,
        'timePowerMed'  : 4.0,
        'minSteps'      : 100,
        'polishSteps'   : 500,
        'dfreeSteps'    :  0,
        'dfreeAlg'      : 'cmaes',
        'kinEScale'     : 200.0,
	'irpWeight'     : 0.0
    }
    def __init__(self,initDict={}):
        if initDict is None:
            initDict = {}
        StrictDict.__init__(self,defaultErr="dynamics", defaultDict=dynOptions.defaults)
        #self.update(dynOptions.defaults)
        self.strictUpdate(initDict)

def createStrictDict(initDict, type):
    """
    # Parameters:

    * initDict (dict); a dictionary of 
    * type (string); 

    # Returns:

    strictDict (dict); a dictionary that contains values to update the dynamic simulation parameters (if they are valid)
    """
    if initDict is None:
        initDict = {}
    allowedKeys = {}
    allowedKeys['param'] = ['coarse', 'useh', 'hardSphere', 'start', 'end', 'shrinkValue', 'shrinkHValue', 'dislim', 'swap'] 
    allowedKeys['force'] = ['elec', 'robson', 'repel', 'dis', 'tors', 'dih', 'irp', 'shift', 'bondWt']
    allowedKeys = allowedKeys[type]

    strictDict = StrictDict(defaultErr=type+'s')
    strictDict.setInclusions(allowedKeys)
    strictDict.strictUpdate(initDict)
    return strictDict

class refine:
    def __init__(self):
	self.NEFfile = ''
        self.energyLists = None
        self.dihedrals = None
        self.cyanaAngleFiles = []
        self.xplorAngleFiles = []
        self.nvAngleFiles = []
	self.cyanaDistanceFiles = {}
        self.xplorDistanceFiles = {}
        self.cyanaRDCFiles = {}
        self.xplorRDCFiles = {}
        self.suiteAngleFiles = []
        self.nmrfxDistanceFiles = {}
        self.nvDistanceFiles = {}
        self.constraints = {} # map of atom pairs to
        self.angleStrings = []
        self.bondConstraints = []
        self.disLim = 4.6
        self.angleDelta = 30
        self.molecule = None
        self.trajectoryWriter = None
        self.molecule = Molecule.getActive()
        self.entityEntryDict = {} # map of entity to linker atom
        self.reportDump = False
        if self.molecule != None:
            self.molName = self.molecule.getName()

    def setAngleDelta(self,value):
        self.angleDelta = value

    def writeAngles(self,fileName):
        """
        # Parameters:

        fileName (string); The name of file with angles/dihedral constraints

        # Returns:

        _ (None); This method serves as a setter method to write dihedral angles to a specified file.

        See also: `writeDihedrals(fileName)`
        """
        self.dihedral.writeDihedrals(fileName)

    def readAngles(self,fileName):
        """
        # Parameters:

        fileName (string); The name of file with angles/dihedral constraints

        # Return:

        _ (None); This method serves as a helper that reads from a specified file.

        See also: `readDihedrals(fileName)`
        """
        self.dihedral.readDihedrals(fileName)

    def numericalDerivatives(self,delta,report):
        grefine = GradientRefinement(self.dihedral)

        grefine.numericalDerivatives(delta,report)

    def setReportDump(self, value):
        self.reportDump = value

    def setSeed(self,seed):
        self.dihedral.seed(seed)
        ranGen = self.dihedral.getRandom()
        newSeed = ranGen.nextInt()
        self.dihedral.seed(newSeed)

    def putPseudo(self,angle1,angle2):
        self.dihedral.putPseudoAngle(angle1,angle2)

    def setAngles(self,ranfact,mode):
        """
        # Parameters:

        * ranfact (_); 
        * mode (bool); Describes whether or not to use random initial angles.
	
	# Returns:

	_ (None);

        See also: `putInitialAngles(...)` in Dihedrals.java
        """

        self.dihedral.putInitialAngles(mode)

    def randomizeAngles(self):
        """ Generates random angles

        See also: `randomizeAngles(...)` in Dihedrals.java
        """

        self.dihedral.randomizeAngles()

    def updateAt(self,n):
        #XXX: Need to complete docstring
        """
        # Parameters:

        n (int); 

        See also: `updateAt(...)` in Dihedrals.java
        """
        self.dihedral.updateAt(n)

    def setForces(self,forceDict):
        #XXX: Need to complete docstring
        """
        # Parameters:

        * robson (_);
        * repel (_);
        * elec (_);
        * dis (_);
        * tors (_);
        * dih (_);
        * shift (_);
        * bondWt (_);
        """

        if not forceDict:
            return
        forceWeightOrig = self.energyLists.getForceWeight()
        getOrigWeight = {
            'elec'   : forceWeightOrig.getElectrostatic(),
            'robson' : forceWeightOrig.getRobson(),
            'repel'  : forceWeightOrig.getRepel(),
            'dis'    : forceWeightOrig.getNOE(),
            'tors'   : forceWeightOrig.getDihedralProb(),
            'dih'    : forceWeightOrig.getDihedral(),
            'irp'    : forceWeightOrig.getIrp(),
            'shift'  : forceWeightOrig.getShift(),
            'bondWt' : forceWeightOrig.getBondWt()
        }
        forces = ('elec','robson','repel','dis','tors','dih','irp','shift','bondWt')
        forceWeights = []
        for force in forces:
            forceWeight = forceDict[force] if force in forceDict else getOrigWeight[force]
            if force == 'bondWt' and forceWeight < 1:
                raise ValueError('The bond weight should not be less than 1')
            forceWeights.append(forceWeight)
        forceWeight = ForceWeight(*forceWeights)
        self.energyLists.setForceWeight(forceWeight)

    def getForces(self): 
        #XXX: Need to complete docstring
        """
        # Return:

        output (string); string that contains values of the calculated forces
        """

        fW = self.energyLists.getForceWeight()
        output = "robson %5.2f repel %5.2f elec %5.2f dis %5.2f dprob %5.2f dih %5.2f irp %5.2f shift %5.2f bondWt %5.2f" % (fW.getRobson(),fW.getRepel(),fW.getElectrostatic(),fW.getNOE(),fW.getDihedralProb(),fW.getDihedral(),fW.getIrp(), fW.getShift(), fW.getBondWt())
        return output

    def dump(self,limit,shiftLim, fileName):
        if fileName != None:
            self.energyLists.dump(limit,shiftLim,fileName)

    def getEnergyDump(self,limit):
        return self.energyLists.dump(limit,-1.0,"")

    def rinertia(self):
        self.rDyn = self.dihedral.getRotationalDyamics()
        self.rDyn.setTrajectoryWriter(self.trajectoryWriter)
        return self.rDyn

    def addLinkers(self, linkerList):
        if linkerList:
            try :
                for linkerDict in linkerList:
                    self.readLinkerDict(linkerDict) # returns used Entities to mark them
            except TypeError:
                self.readLinkerDict(linkerList)

    def readLinkerDict(self, linkerDict):
        entityNames = [entity.getName() for entity in self.molecule.getEntities()]
        if not linkerDict:
            return
        if 'atoms' in linkerDict:
            atomName1, atomName2 = linkerDict['atoms']
            atom1 = self.molecule.getAtomByName(atomName1)
            atom2 = self.molecule.getAtomByName(atomName2)
            entity1 = atom1.getTopEntity()
            entity2 = atom2.getTopEntity()
            if self.entityEntryDict[entity1] == atom1:
                startEntity, startAtom = entity2, atom2
                endEntity, endAtom = entity1, atom1
            else:
                startEntity, startAtom = entity1, atom1
                endEntity, endAtom = entity2, atom2

	    # n is the number of rotational points within a link established between any 2 entities.
            # default is 6.
            n = linkerDict['n'] if 'n' in linkerDict else 6
            linkLen = linkerDict['length'] if 'length' in linkerDict else 5.0
            valAngle = linkerDict['valAngle'] if 'valAngle' in linkerDict else 90.0
            dihAngle = linkerDict['dihAngle'] if 'dihAngle' in linkerDict else 135.0

        else:
            if 'bond' not in linkerDict or 'cyclic' not in linkerDict['bond']:
                raise KeyError("atoms must be defined within the linker object")

        if 'bond' in linkerDict:
            bondDict = linkerDict['bond']
            if "cyclic" in bondDict and bondDict["cyclic"]:
                polymers = self.molecule.getPolymers()
                polymerNames = [polymer.getName() for polymer in polymers]
                if len(polymers) != 1 and "pName" not in bondDict:
                    raise ValueError("Multiple polymers in structure but no specification for which to by made cyclic")
                    #return []
                polymer = None
                if "pName" in bondDict:
                    if bondDict["pName"] in polymerNames:
                        polymer = self.molecule.getEntity(bondDict["pName"])
                    else:
                        raise ValueError(bondDict["pName"] + " is not a polymer within the molecule")
                else:
                    polymer = polymers[0]
                self.addCyclicBond(polymer)
                return []

            else:
                sameEnt = startEntity == endEntity;
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
            self.molecule.createLinker(startAtom, endAtom, n, linkLen, valAngle, dihAngle)

    def addCyclicBond(self, polymer):
        # to return a list atomName1, atomName2, distance
        distanceConstraints = polymer.getCyclicConstraints()
        for distanceConstraint in distanceConstraints:
            self.bondConstraints.append(distanceConstraint)
            #atomName1, atomName2, distance = distanceConstraint.split()
            #self.energyLists.addDistanceConstraint(atomName1, atomName2, distance - .0001, distance + .0001, True)


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

    def setPars(self,parsDict):
        if not parsDict:
            return
        parFuncs = {
            'coarse'      : self.energyLists.setCourseGrain,
            'useh'        : self.energyLists.setIncludeH,
            'hardSphere'  : self.energyLists.setHardSphere,
            'start'       : self.energyLists.setDeltaStart,
            'end'         : self.energyLists.setDeltaEnd,
            'shrinkValue' : self.energyLists.setShrinkValue,
            'shrinkHValue': self.energyLists.setShrinkHValue,
            'dislim'      : self.energyLists.setDistanceLimit,
            'swap'        : self.energyLists.setSwap
        }
        for par,parValue in parsDict.iteritems():
            parFunc = parFuncs.get(par)
            if not parFunc:
                raise ValueError('There is no ' + par + ' parameter to alter')
            parFunc(parValue)
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

        self.setForces({'repel':0.5,'dis':1})
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

    def loadDistancesFromFile(self,fileName, keepSetting=None):
       file = open(fileName,"r")
       data= file.read()
       file.close()
       self.loadDistances(data, keepSetting=None)

    def loadDistances(self,data, keepSetting=None):
        lines = data.split('\n')
        for line in lines:
            line = line.strip()
            if (len(line) == 0):
                continue
            if (line[0] == '#'):
                continue
            values = line.split()
            (fullAtom1,fullAtom2,s2,s3) = values
            lower = float(s2)
            upper = float(s3)
            atomPair = ' '.join([fullAtom1,fullAtom2]) if fullAtom1 < fullAtom2 else ' '.join([fullAtom2, fullAtom1])
            if atomPair not in self.constraints:
                constraint = Constraint(atomPair, lower, 'lower', setting=keepSetting)
                self.constraints[atomPair] = constraint
                self.constraints[atomPair].addBound(upper, 'upper')
            else:
                self.constraints[atomPair].addBound(lower, 'lower')
                self.constraints[atomPair].addBound(upper, 'upper')

    def getResNameLookUpDict(self):
        resNames = {}
        for polymer in self.molecule.getPolymers():
            for residue in polymer.getResidues():
                resNames[residue.getNumber()] = residue.getName()
        for ligand in self.molecule.getLigands():
            resNames[ligand.getNumber()] = ligand.getName()
        return resNames

    def readXPLORDistanceConstraints(self, fileName, keepSetting=None):
        xplorFile = xplor.XPLOR(fileName)
        resNames = self.getResNameLookUpDict()
        constraints = xplorFile.readXPLORDistanceConstraints(resNames)
        for constraint in constraints:
            lower = constraint['lower']
            upper = constraint['upper']
            atomPairs = constraint['atomPairs']
            firstAtomPair = atomPairs[0]

            if firstAtomPair not in self.constraints:
                constraint = Constraint(firstAtomPair, lower, 'lower', setting=keepSetting)
                self.constraints[firstAtomPair] = constraint
                if len(atomPairs) > 1:
                    for atomPair in atomPairs[1:]:
                        self.constraints[atomPair] = constraint
                        constraint.addPair(atomPair)
                self.constraints[firstAtomPair].addBound(upper,'upper');
            else:
                self.constraints[firstAtomPair].addBound(lower, 'lower');
                self.constraints[firstAtomPair].addBound(upper, 'upper');

    def readXPLORrdcConstraints(self, fileName, keepSetting=None):
        xplorFile = xplor.XPLOR(fileName)
        resNames = self.getResNameLookUpDict()
        constraints = xplorFile.readXPLORrdcConstraints(resNames)
        for constraint in constraints:
            rdc = constraint['rdc']
            err = constraint['err']
            atomPairs = constraint['atomPairs']
            firstAtomPair = atomPairs[0]

            if firstAtomPair not in self.constraints:
                constraint = Constraint(firstAtomPair, 0.0, 'rdc', setting=keepSetting)
                self.constraints[firstAtomPair] = constraint
                if len(atomPairs) > 1:
                    for atomPair in atomPairs[1:]:
                        self.constraints[atomPair] = constraint
                        constraint.addPair(atomPair)
                self.constraints[firstAtomPair].addRDC(rdc, err);
            else:
                self.constraints[firstAtomPair].addRDC(rdc, err);

    def addDisCon(self, atomName1, atomName2, lower, upper):
        self.energyLists.addDistanceConstraint(atomName1,atomName2,lower,upper)

    def getEntityTreeStartAtom(self, entity):
        ''' getEntityTreeStartAtom returns an atom that would be picked up
            by AngleTreeGenerator if no atom is specified.
        '''
        aTree = AngleTreeGenerator()
        entryAtom = aTree.findStartAtom(entity)
        return entryAtom

    def setEntityEntryDict(self, linkerList, treeDict):
        entities = [entity for entity in self.molecule.getEntities()]
        visitedEntities = []
        if treeDict:
            entryAtomName = treeDict['start'] if 'start' in treeDict else None
        elif not treeDict or not entryAtomName:
            startEntity = self.molecule.getEntities()[0]
            entryAtom = self.getEntityTreeStartAtom(startEntity)
            entryAtomName = entryAtom.getShortName()
            treeDict = {'start':entryAtom}

        self.entityEntryDict[startEntity] = entryAtom
        visitedEntities.append(startEntity)

        if linkerList:
            import copy
            linkerList = copy.deepcopy(linkerList)
            linkerList = [linkerDict for linkerDict in linkerList if 'atoms' in linkerDict]
            while len(linkerList) > 0:
                linkerDict = linkerList[0]
                atomSpecs = linkerDict['atoms']
                atoms = [self.molecule.getAtomByName(atom) for atom in atomSpecs]
                linkerEntities = [atom.getTopEntity() for atom in atoms]

                if linkerEntities[0] in visitedEntities and linkerEntities[1] in visitedEntities:
                    linkerList.pop(0)
                    continue
                elif linkerEntities[0] in visitedEntities:
                    entryAtom = atoms[1]
                    linkerList.pop(0)
                elif linkerEntities[1] in visitedEntities:
                    entryAtom = atoms[0]
                    linkerList.pop(0)
                else:
                    linkerList.pop(0)
                    linkerList.append(linkerDict)
                    continue
                entity = entryAtom.getTopEntity()
                self.entityEntryDict[entity] = entryAtom
                visitedEntities.append(entity)
        return treeDict

    def getAtom(self, atomTuple):
        """
	Gets atom from a tuple that contains entity and atom name.

        # Parameters:

        atomTuple (tuple); contains atom entity and name of the atom

        # Returns:

        atom (Entity); retrieves atom entity
        """

        entityName, atomName = atomTuple
        entity = self.molecule.getEntity(entityName)
        atomArr = atomName.split('.')

        if len(atomArr) > 1:
            resNum = atomArr.pop(0)
            if resNum:
                entity = entity.getResidue(resNum)
        atomName = atomArr[0]
        atom = entity.getAtom(atomName)
        if not atom:
            raise ValueError(atomName, "was not found in", entityName)
        return atom

    def validateLinkerList(self,linkerList, treeDict):
        ''' validateLinkerList goes over all linkers and the treeDict to make
            sure all entities in the molecule are connected in some way.
            If no linker is provided for an entity, one will be created for
            the entity.  This function also has a few break points to help users
            troubleshoot invalid data in their config file'''
        unusedEntities = [entity.getName() for entity in self.molecule.getEntities()]
        allEntities = tuple(unusedEntities)

        entryAtomName = treeDict.get('start') if treeDict else None
        firstEntityName = entryAtomName.split(':')[0] if entryAtomName else unusedEntities[0]
        firstEntity = self.molecule.getEntity(firstEntityName)
        unusedEntities.remove(firstEntityName)
        if linkerList:
            linkerList = linkerList if type(linkerList) is ArrayList else [linkerList]
            linkerAtoms = reduce(lambda total, linkerDict : total + list(linkerDict.get('atoms')), linkerList, [])
            for atomName in linkerAtoms:
                entName = atomName.split(':')[0]
                if entName not in allEntities:
                    raise ValueError(entName + " is not a valid entitiy. Entities within molecule are " + ', '.join(allEntities))
                if entName in unusedEntities:
                    unusedEntities.remove(entName)
        else:
            if len(unusedEntities) > 0:
                linkerList = ArrayList()
        for entityName in unusedEntities:
            entity = self.molecule.getEntity(entityName)
            #print entityName + " had no defined linker."
            startAtom = firstEntity.getLastAtom().getFullName()
            endAtom = self.getEntityTreeStartAtom(entity).getFullName()
            newLinker = {'atoms': [startAtom, endAtom]}
            linkerList.append(newLinker)
            #print "linker added between " + startAtom + " and " + endAtom
        return linkerList

    def loadFromYaml(self,data, seed, pdbFile=""):
        #XXX: Need to complete docstring
        """
        This procedure grabs the data presented in the YAML file and executes a series
        of programs to set up the structure parameters (i.e read sequence, distance files, etc.)
        """

        molData = {}
	residues = None

        if pdbFile != '':
            molio.readPDB(pdbFile)
        else:
	    # Checks if NEF file is specified to process it. 
	    # Even if NEF file is specified, this control flow
	    # still checks whether 'molecule' data block is specified
            # in the YAML file.
	    if 'nef' in data:
		fileName = data['nef']
		self.NEFReader(fileName)

	    # Checks if 'molecule' data block is specified.
            if 'molecule' in data:
                molData = data['molecule']
		# Checks if a residue library is included in 'molecule' code block
		# for information about different entities.
                self.reslib = molData['reslib'] if 'reslib' in molData else None
                if self.reslib:
                    PDBFile.setLocalResLibDir(self.reslib)

		# Different entities can be specified. Via sequence files
		# or input residues.
                if not 'link' in molData:
                    seqReader = Sequence()
                else:
                    seqReader = None
                if 'entities' in molData:
                    molList = molData['entities']
                    molList = prioritizePolymers(molList)
                    for molDict in molList:
                        residues = ",".join(molDict['residues'].split()) if 'residues' in molDict else None
                        print molDict
                        self.readMoleculeDict(seqReader, molDict)
                else:
                    #Only one entity in the molecule
                    residues = ",".join(molData['residues'].split()) if 'residues' in molData else None
                    self.readMoleculeDict(seqReader, molData)

        
        self.molecule = Molecule.getActive()
        self.molName = self.molecule.getName()

        treeDict = data['tree'] if 'tree' in data else None
        linkerList = molData['link'] if 'link' in molData else None


        if 'tree' in data:
            if len(self.molecule.getEntities()) > 1:
                linkerList = self.validateLinkerList(linkerList, treeDict)
            treeDict = self.setEntityEntryDict(linkerList, treeDict)
            self.measureTree()
        else:
            if len(self.molecule.getEntities()) > 1:
	        if not 'nef' in data:
                    print 'multiseq'
                    self.molecule.invalidateAtomTree()
                    self.molecule.setupGenCoords()
                    self.molecule.genCoords(False)
                    self.molecule.setupRotGroups()
                    #raise TypeError("Tree mode must be run on molecules with more than one entity")

        if linkerList:
            self.addLinkers(linkerList)

        if 'distances' in data:
            disWt = self.readDistanceDict(data['distances'],residues)
        if 'angles' in data:
            angleWt = self.readAngleDict(data['angles'])
        if 'tree' in data:
            self.setupTree(treeDict)

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

    def readMoleculeDict(self,seqReader, molDict):
        linkLen = 5.0;
        valAngle = 90.0;
        dihAngle = 135.0;
        if seqReader != None and seqReader.getMolecule() != None:
            print 'createlink'
            seqReader.createLinker(7, linkLen, valAngle, dihAngle);
        #if sequence exists it takes priority over the file and the sequence will be used instead
        polyType = molDict.get('ptype','protein').upper()
        if 'sequence' in molDict:
            seqString = molDict['sequence']
            linkers = molDict.get('link')
            index = molDict.get('indexing')
            resStrings = getSequenceArray(index, seqString, linkers, polyType)
            chain = molDict.get('chain')
            if chain == None:
                chain = 'A'
            molio.readSequenceString(chain, resStrings, seqReader=seqReader)
        else:
            file = molDict['file']
            type = molDict.get('type','nv')
            compound = None
            if type == 'fasta':
                molio.readSequence(file, True)
            elif type == 'pdb':
                compound = molio.readPDB(file, not 'ptype' in molDict)
            elif type == 'sdf' or type == 'mol':
                compound = molio.readSDF(file)
            else:
                if 'chain' in molDict:
                    pName = molDict['chain']
                    molio.readSequence(file, polymerName=pName,seqReader=seqReader)
                else:
                    molio.readSequence(file,seqReader=seqReader)
            resNum = molDict.get('resnum')
            if resNum and compound:
                compound.setNumber(str(resNum))

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
            keepMethod = None
            if 'keep' in dic:
                keepMethod = dic['keep']
            self.addDistanceFile(file,mode=type,keep=keepMethod)
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
        if 'bp' in rnaDict:
	    polymers = self.molecule.getPolymers()
            bps = rnaDict['bp']
            for bp in bps:
		res1, res2 = bp['res1'].split(":"), bp['res2'].split(":")
		resNum1, resNum2 = res1[1], res2[1]
		polymer1, polymer2 = self.molecule.getEntity(res1[0]), self.molecule.getEntity(res2[0])
		residue1, residue2 = polymer1.getResidue(str(resNum1)), polymer2.getResidue(str(resNum2))
		types = bp['type']
		if len(types) == 1:
		    self.addBasePair(residue1, residue2, types[0])
		else:
		    self.addBasePairs(residue1, residue2, types)    
	  	
    def readAnnealDict(self, annealDict):
        dynDict = annealDict.get('dynOptions')
        dOpt = dynOptions(dynDict)
        if dynDict:
            del annealDict['dynOptions']
        self.settings = annealDict
        return dOpt

    def readShiftDict(self, shiftDict,residues):
        wt = -1.0
        file = shiftDict['file']
        if 'type' in shiftDict:
            type = shiftDict['type']
            if type == 'str3':
                import os
                shifts = seqalgs.readBMRBShifts('shifts',file)
                for key in shifts:
                    for key2 in shifts[key]:
                        print key, key2, shifts[key][key2]
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
            #ringShifts = self.setBasePPMs()
            #self.energyLists.setRingShifts()
        if 'weight' in shiftDict:
            wt = shiftDict['weight']
        return wt

    def getAtomName(self, residue, aName):
        return residue.getPolymer().getName() +':' + str(residue.getNumber()) + '.' + aName

    def addRiboseRestraints(self,polymer):
        for residue in polymer.getResidues():
            if not residue.isStandard():
                continue
            resName = residue.getName()
            upper = 1.46
            lower = 1.44
            restraints = []
            restraints.append(("C4'", "O4'",1.44, 1.46))
            restraints.append(("C4'", "C1'",2.30, 2.36))
            restraints.append(("C5'", "O4'",2.37,  2.43))
            restraints.append(("C3'", "O4'",2.32,  2.35))
            restraints.append(("O4'", "H4'", 2.05, 2.2))
            for restraint in restraints:
                (a1,a2,lower,upper) = restraint
                atomName1 = self.getAtomName(residue, a1)
                atomName2 = self.getAtomName(residue, a2)
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
                    atomName = self.getAtomName(residue, aName)
                    fullAtoms.append(atomName)

                scale = 1.0
                try:
                    self.dihedral.addBoundary(fullAtoms,lower,upper,scale)
                except:
                    print "err",fullAtoms
                    pass

    def NEFReader(self, fileName):
        from java.io import FileReader
        from java.io import BufferedReader
        from java.io import File
        from org.nmrfx.processor.star import STAR3
        from org.nmrfx.structure.chemistry.io import NMRStarReader
        from org.nmrfx.structure.chemistry.io import NMRStarWriter
        fileReader = FileReader(fileName)
        bfR = BufferedReader(fileReader)
        star = STAR3(bfR,'star3')
        star.scanFile()
        file = File(fileName)
        reader = NMRStarReader(file, star)
        self.dihedrals = reader.processNEF()
        self.energyLists = self.dihedrals.energyList

    def readNMRFxDistanceConstraints(self, fileName, keepSetting=None):
	"""
	# Parameters:

	* fileName (string); the name of the distance constraint file that'll be passed to reader function
	* keepSettings (None);

	# Returns:

	_ (None); processes the constraint dictionary provided by nmrfxDistReader and add distance constraints

	See also: `nmrfxDistReader(...)` and `Constraints.addBound(...)`
	"""
	# constList is a list of dictionaries w/ keys: 'lower', 'upper', and 'atomPairs'
	constList = self.nmrfxDistReader(fileName)
	for groupID in constList:
            constraint = constList[groupID]
            print constraint
	    lower = constraint['lower']
            upper = constraint['upper']
            atomPairs = constraint['atomPairs']
            firstAtomPair = atomPairs[0]
            if firstAtomPair not in self.constraints:
                constraint = Constraint(firstAtomPair, lower, 'lower', setting=keepSetting)
                self.constraints[firstAtomPair] = constraint
                for atomPair in atomPairs[1:]:
                    self.constraints[atomPair] = constraint
                    constraint.addPair(atomPair)
                self.constraints[firstAtomPair].addBound(upper,'upper');
            else:
                self.constraints[firstAtomPair].addBound(lower, 'lower');
                self.constraints[firstAtomPair].addBound(upper, 'upper');

    def nmrfxDistReader(self, fileName):
	"""Reads the distance constraint from an input file and assembles a dictionary with contraint information.

	# Parameters:

	fileName (string); the name of a distance constraint file with the nmrfx format

	# Returns:

	constraintDicts (dict); dictionary containing atom pairs, lower and upper bounds of each constraint
	"""
        constraintDicts = []
	checker = {}

	with open(fileName, 'r') as fInput:
            fRead = fInput.readlines()
            for line in fRead:
                splitList = line.split("\t")
                group = splitList[1]
                atomPair = tuple(splitList[2:4])
		atomPair = ' '.join(atomPair) if atomPair[0] < atomPair[1] else ' '.join([atomPair[1], atomPair[0]])

		if group in checker:
		    checker[group]['atomPairs'].append(atomPair)
		    continue

		lower, upper = tuple(map(float, splitList[4:6]))
		# checks lower and upper bound to make sure they are positive values
		constraints = {'atomPairs': [],'lower': lower,'upper': upper}
		constraints['atomPairs'].append(atomPair)
		checker[group] = constraints
        return checker 

#set dc [list 283.n3 698.h1 1.8 2.0]
# 283  RCYT  H6          283  RCYT  H2'          4.00    1.00E+00
# 283  RCYT  H6          283  RCYT  H3'          3.00    1.00E+00
# 283  RCYT  H3'   283  RCYT  H5"          3.30    1.00E+00
    def readCYANADistances(self, fileNames, molName, keepSetting=None):
        for fileName in fileNames:
            if os.path.exists(fileName):
                mode = 'lower' if fileName.endswith('.lol') else 'upper'
                with open(fileName,'r') as fIn:
                    for lineNum, line in enumerate(fIn):
                        line = line.strip()
                        if len(line) == 0:
                            continue
                        if line[0] == '#':
                            continue
                        fields = line.split()
                        nFields = len(fields)
                        if nFields == 7:
                            res1, _, atom1, res2, _, atom2, distance = fields
                        elif nFields == 8:
                            res1, _, atom1, res2, _, atom2, distance, weight = fields
                        elif nFields > 8:
                            res1, _, atom1, res2, _, atom2, distance, weight = fields[:8]
                        else:
                            errMsg = "Invalid number of fields: %d [file -> '%s']" % (len(fields), fIn.name)
                            errMsg += "\n\tLine : (%d) '%s'" % (lineNum+1, line)
                            raise ValueError(errMsg)
                  
                        distance = float(distance)
                        # FIXME : Sometimes molecule name should be polymer name, or vice versa. ('A' instead of '2MQN')
                        # During NEF testing, the full name of atom was prefixed using the polymer name instead of the 
                        # molecule name. Thus, it required the quick fix below. Same had to be done in 'readCYANAAngles(..)' 
	    	        #molName = 'A'
                        fullAtom1 = molName+':'+res1+'.'+atom1
                        fullAtom2 = molName+':'+res2+'.'+atom2
                        fullAtom1 = fullAtom1.replace('"',"''")
                        fullAtom2 = fullAtom2.replace('"',"''")
                        atomPair = ' '.join([fullAtom1,fullAtom2]) if fullAtom1 < fullAtom2 else ' '.join([fullAtom2, fullAtom1])
                        if distance != 0.0:
                            if atomPair not in self.constraints:
                                constraint = Constraint(atomPair, distance, mode, setting=keepSetting)
                                self.constraints[atomPair] = constraint
                            else:
                                self.constraints[atomPair].addBound(distance, mode)
                        else:
                            if mode == 'upper':
                                Constraint.lastViewed.addPair(atomPair)
                                self.constraints[atomPair] = Constraint.lastViewed

    def readCYANARDCs(self, fileNames, molName, keepSetting=None):
        for fileName in fileNames:
            if os.path.exists(fileName):
                with open(fileName,'r') as fIn:
                    for lineNum, line in enumerate(fIn):
                        line = line.strip()
                        if len(line) == 0:
                            continue
                        if line[0] == '#':
                            continue
                        fields = line.split()
                        nFields = len(fields)

                        rdc, err, res1, atom1, res2, atom2 = 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
                        if fields[0].isdigit():
                            res1, _, atom1, res2, _, atom2, rdc, err = fields[:8]

                        rdc = float(rdc)
                        err = float(err)

                        if rdc != 0.0:
                            fullAtom1 = molName+':'+res1+'.'+atom1
                            fullAtom2 = molName+':'+res2+'.'+atom2
                            fullAtom1 = fullAtom1.replace('"',"''")
                            fullAtom2 = fullAtom2.replace('"',"''")
                            atomPair = ' '.join([fullAtom1,fullAtom2]) if fullAtom1 < fullAtom2 else ' '.join([fullAtom2, fullAtom1])
                            if atomPair not in self.constraints:
                                constraint = Constraint(atomPair, 0.0, 'rdc', setting=keepSetting)
                                self.constraints[atomPair] = constraint
                                if len(atomPairs) > 1:
                                    for atomPair in atomPairs[1:]:
                                        self.constraints[atomPair] = constraint
                                        constraint.addPair(atomPair)
                                self.constraints[firstAtomPair].addRDC(rdc, err);
                            else:
                                self.constraints[firstAtomPair].addRDC(rdc, err);

    def readXplorRDCs(self, fileNames, keepSetting=None):
        for fileName in fileNames:
            if os.path.exists(fileName):
                readXPLORrdcConstraints(fileName, keepSetting)

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


    def readCYANAAngles(self, fileName,molName):
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
        for lineNum, line in enumerate(fIn):
           line = line.strip()
           if len(line) == 0:
               continue
           if line[0] == '#':
               continue
           splitLines = line.split()
           nFields = len(splitLines)
           weight = None
           if nFields == 5:
               (res, resName, angle, lower, upper) = splitLines
           elif nFields == 6:
               (res, resName, angle, lower, upper, weight) = splitLines
           else:
               errorString = "Number of fields in line (%d) '%s' could not be processed. [file -> '%s']" % (lineNum+1, line, fIn.name)
               fIn.close()
               raise ValueError(errorString) 
           if angle in angleMap:
               atoms = angleMap[angle]
           elif (angle,resName) in angleMap:
               atoms = angleMap[angle,resName]
           else:
               raise ValueError("No such angle: %s, %s in line (%d) '%s'" % (angle, resName, lineNum+1, line))
           res = int(res)

           fullAtoms = []
           for atom in atoms:
               if ':' in atom:
                   split_atom = atom.split(':')
                   dRes = int(split_atom[0])
                   atom = split_atom[1]
               else:
                   dRes = 0
	       #molName = 'A' #Teddy 
               fullAtom = molName + ':' + str(res + dRes) + '.' + atom
               fullAtom = fullAtom.replace('"',"''")
               fullAtoms.append(fullAtom)
           scale = float(weight) if weight else 1.0
           lower = float(lower)
           upper = float(upper)
           if lower == upper:
               lower = lower - 20
               upper = upper + 20
           if (lower < -180.0) and (upper < 0.0):
                lower += 360.0
                upper += 360.0
	   try:
               self.dihedral.addBoundary(fullAtoms,lower,upper,scale)
	   except IllegalArgumentException as IAE:
               atoms = ' -> '.join(map(lambda x: x.split(':')[-1], fullAtoms))
	       err = IAE.getMessage()
               errMsg = "\nPlease evaluate dihedral constraints for the following boundary information [file -> '%s']" % (fIn.name)
               errMsg += "\n\tLine : (%d) '%s'" % (lineNum+1, line)
	       errMsg += "\n\tAtoms : %s\n\t(Note : 'x.y' == x: residue num, y: residue name.)" % (atoms)
	       errMsg += "\n\nJava Error Msg : %s" % (err)
	       raise ValueError(errMsg)
	   except NullPointerException:
               atoms = ' -> '.join(map(lambda x: x.split(':')[-1], fullAtoms))
               errMsg = "\nPlease evaluate the dihedral constraints for the following boundary information [{0}]:".format(fileName)
	       errMsg += "\n\t- resNum.resName: {}".format(atoms)
	       errMsg += "\n\nHere's a list of things that could've gone wrong:\n"
	       errMsg += "\t1) Atoms provided do not have required format to properly calculate dihedral angle(s).\n"
	       errMsg += "\t2) Information in the constraint file does not match the information in the sequence file or the NMRFxStructure residue library." 
               raise ValueError(errMsg)
	   except:
	       print("Internal Java Error: Need to evaluate addBoundary(...) method in Dihedral.java\n")
	       raise
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

    def addHelix(self, helixResidues):
        strandI = helixResidues[0::2]
        strandJ = helixResidues[1::2]
        nRes = len(strandI)
        for i in range(nRes):
            resI = strandI[i]
            resJ = strandJ[i]
            resINum = resI.getNumber()
            resJNum = resJ.getNumber()
            polymerI = resI.getPolymer()
            polymerJ = resJ.getPolymer()
            self.addSuiteBoundary(polymerI, resINum,"1a")
            self.addSuiteBoundary(polymerJ, resJNum,"1a")
            self.addBasePair(resI, resJ)
            if ((i+1) < nRes):
                resINext = strandI[i+1]
                resJPrev = strandJ[i+1]
                #  make sure we're not in bulge before adding stack
                if resINext.getPrevious() == resI and resJPrev.getNext() == resJ:
                    self.addStackPair(resI, resINext)
                    self.addStackPair(resJPrev, resJ)
                    resJName = resJ.getName()
                    if (resJName == "A"):
                        atomNameI = self.getAtomName(resINext,"H1'")
                        atomNameJ = self.getAtomName(resJ,"H2")
                        self.energyLists.addDistanceConstraint(atomNameI, atomNameJ, 1.8, 5.0)


    def addHelixPP(self, helixResidues):
        strandI = helixResidues[0::2]
        strandJ = helixResidues[1::2]
        nRes = len(strandI)
        for i in range(nRes):
            resI = strandI[i]
            resJ = strandJ[i]
            if ((i+3) < nRes):
                resI3 = strandI[i+3]
                if (resI.getAtom("P") != None) and (resI3.getAtom("P") != None):
                    atomNameI = self.getAtomName(resI,"P")
                    atomNameI3 = self.getAtomName(resI3,"P")
                    self.energyLists.addDistanceConstraint(atomNameI, atomNameI3, 16.5, 20.0)
                resJ3 = strandJ[i+3]
                if (resJ3.getAtom("P") != None) and (resJ3.getAtom("P") != None):
                    atomNameJ = self.getAtomName(resJ,"P")
                    atomNameJ3 = self.getAtomName(resJ3,"P")
                    self.energyLists.addDistanceConstraint(atomNameJ, atomNameJ3, 16.5, 20.0)
            if ((i+5) < nRes):
                resJ5 = strandJ[i+5]
                if (resI.getAtom("P") != None) and (resJ5.getAtom("P") != None):
                    atomNameI = self.getAtomName(resI,"P")
                    atomNameJ5 = self.getAtomName(resJ5,"P")
                    self.energyLists.addDistanceConstraint(atomNameI, atomNameJ5, 10, 12.0)
                    
    def findHelices(self,vienna):
        polymers = self.molecule.getPolymers()
        allResidues = []
        for polymer in polymers:
            allResidues += polymer.getResidues()
        ssGen = SSGen(self.molecule, vienna)
        ssGen.secondaryStructGen()
        for ss in ssGen.structures:
            if ss.getName() == "Helix":
                residues = ss.getResidues()
                self.addHelix(residues)
                self.addHelixPP(residues)
        gnraPat = re.compile('G[AGUC][AG]A')
        uncgPat = re.compile('U[AGUC]CG')

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

    def addBasePair(self, residueI, residueJ, type=1):
        resNameI = residueI.getName()
        resNameJ = residueJ.getName()
        resNumI = residueI.getNumber()
        resNumJ = residueJ.getNumber()
	bp = AllBasePairs.getBP(type, resNameI, resNameJ)
   	numBp = len(bp.atomPairs)
	for i in range(0, numBp):
	    restraints = bp.distances[i].split(":")
	    atoms = bp.atomPairs[i].split(":")
	    atomI = atoms[0].split("/")[0]
            atom1Name = self.getAtomName(residueI, atomI)						
	    atomJ = atoms[1].split("/")[0]
            atom2Name = self.getAtomName(residueJ, atomJ)						
	    atomAtomDis= float(restraints[1])
	    lowAtomAtomDis= float(restraints[0])
            atomParentDis= float(restraints[3])
            lowAtomParentDis= float(restraints[2])
	    if atomI.startswith("H"):
	        parentAtom = residueI.getAtom(atomI).parent.getName()
                parentAtomName = self.getAtomName(residueI,parentAtom)
		self.energyLists.addDistanceConstraint(parentAtomName, atom2Name ,lowAtomParentDis,atomParentDis)
	    elif atomJ.startswith("H"):
	        parentAtom = residueJ.getAtom(atomJ).parent.getName()
                parentAtomName = self.getAtomName(residueJ,parentAtom)
		self.energyLists.addDistanceConstraint(parentAtomName, atom1Name ,lowAtomParentDis,atomParentDis) 
	    self.energyLists.addDistanceConstraint(atom1Name, atom2Name ,lowAtomAtomDis,atomAtomDis)
        atomPI = residueI.getAtom("P")
        atomPJ = residueJ.getAtom("P")
        if (atomPI != None) and (atomPJ != None):
            atomPIName = self.getAtomName(residueI, "P")						
            atomPJName = self.getAtomName(residueJ, "P")						
            self.energyLists.addDistanceConstraint(atomPIName, atomPJName ,14.0, 20.0)
           

    def atomListGen(self, atomPair, restraints, residueI, residueJ):
	atom1 = atomPair[0].split("/")[0]
	atom2 = atomPair[1].split("/")[0]
	atom1Name = self.getAtomName(residueI, atom1)
	atom2Name = self.getAtomName(residueJ, atom2)
	atomList1 = []
	atomList2 = []
	parentAtomList1 = []
	parentAtomList2 = []
	disRestraints = []
	parentAtomDisRestraints = []
	disRestraints.append(float(restraints[0]))
	disRestraints.append(float(restraints[1]))
	parentAtomDisRestraints.append(float(restraints[2]))
	parentAtomDisRestraints.append(float(restraints[3]))
	if atom1.startswith("H"):
            parentAtom = residueI.getAtom(atom1).parent.getName()
            parentAtomName = self.getAtomName(residueI,parentAtom)
            parentAtomList1.append(parentAtomName)
            parentAtomList2.append(atom2Name)
        elif atom2.startswith("H"):
            parentAtom = residueJ.getAtom(atom2).parent.getName()
            parentAtomName = self.getAtomName(residueJ,parentAtom)
            parentAtomList1.append(atom1Name)
            parentAtomList2.append(parentAtomName)
	atomList1.append(atom1Name)
	atomList2.append(atom2Name)
	return atomList1, atomList2, parentAtomList1, parentAtomList2, disRestraints, parentAtomDisRestraints 

    def addBasePairs(self, residueI, residueJ, types):
        resNameI = residueI.getName()
        resNameJ = residueJ.getName()
	typeAtomPairs = [AllBasePairs.getBP(int(typee), residueI.getName(), residueJ.getName()).atomPairs for typee in types]
	restraints =  [AllBasePairs.getBP(int(typee), residueI.getName(), residueJ.getName()).distances for typee in types]
	typeAtomPairs.sort(key = lambda x:len(x), reverse = True)
	restraints.sort(key = lambda x:len(x), reverse = True)
	atomPairNum = len(max(typeAtomPairs, key=lambda item: len(item)))
	pairTypesNum = len(typeAtomPairs)
	atoms = []
	disRes = []
        for iPair in range(atomPairNum):
	    pairs = []
	    dis = []
	    for iType in range(pairTypesNum):
	        try:
		    dis.append(restraints[iType][iPair])
	            pairs.append(typeAtomPairs[iType][iPair])
		except:
		    continue
	    atoms.append(pairs)
	    disRes.append(dis)
	for i in range(atomPairNum):
            atomList1 = []
            atomList2 = []
	    parentAtomList1 = []
	    parentAtomList2 = []
	    distances = []
	    parentDistances = []
            for j in range(pairTypesNum):
		try:
		    atomPair = atoms[i][j].split(":")
		    disRestraints = disRes[i][j].split(":")
		    atomLists = self.atomListGen(atomPair, disRestraints, residueI, residueJ)
		    atomList1.extend(atomLists[0])
		    atomList2.extend(atomLists[1])
		    parentAtomList1.extend(atomLists[2])
		    parentAtomList2.extend(atomLists[3])
		    distances.extend(atomLists[4])
		    parentDistances.extend(atomLists[5])
		except:
		    continue
	    if len(atomList1) != 1: 
                self.energyLists.addDistanceConstraint(atomList1, atomList2, min(distances),max(distances))
	        self.energyLists.addDistanceConstraint(parentAtomList1, parentAtomList2, min(parentDistances),max(parentDistances))
	    else: 
		atomPair = typeAtomPairs[0][0]
		disRestraint = restraints[0][0]
	        atomLists = self.atomListGen(atomPair, disRestraint, residueI, residueJ)
		atomList1.extend(atomLists[0])
		atomList2.extend(atomLists[1])
		parentAtomList1.extend(atomLists[2])
                parentAtomList2.extend(atomLists[3])
		distances.extend(atomLists[4])
		parentDistances.extend(atomLists[5])
		self.energyLists.addDistanceConstraint(atomList1, atomList2, min(distances),max(distances))
                self.energyLists.addDistanceConstraint(parentAtomList1, parentAtomList2, min(parentDistances),max(parentDistances))
		
    def addStackPair(self, resI, resJ):
        resNameI = resI.getName()
        resNameJ = resJ.getName()
        resNumI = resI.getNumber()
        resNumJ = resJ.getNumber()
        polyI = resI.getPolymer()
        polyJ = resJ.getPolymer()
        if polyI != polyJ:
            return
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
            atomNameI = polyI.getName()+':'+resNumI+'.'+aNameI
            atomNameJ = polyI.getName()+':'+resNumI+'.'+aNameJ
            self.energyLists.addDistanceConstraint(atomNameI,atomNameJ,lowerIntra,intra)

        stacks = stackTo[resNameJ]
        for stack in stacks:
            aNameI, aNameJ,intra,inter = stack
            lowerInter = inter - 1.0
            atomNameI = polyI.getName()+':'+resNumI+'.'+aNameI
            atomNameJ = polyJ.getName()+':'+resNumJ+'.'+aNameJ
            self.energyLists.addDistanceConstraint(atomNameI,atomNameJ,lowerInter,inter)

        pairName = resNameI+resNameJ
        if not pairName in stackPairs:
            print("No data for pair " + pairName)
        else:
            pairs = stackPairs[pairName]
            for pair in pairs:
                aNameI, aNameJ,lower,upper = pair
                atomNameI = polyI.getName()+':'+resNumI+'.'+aNameI
                atomNameJ = polyJ.getName()+':'+resNumJ+'.'+aNameJ
                self.energyLists.addDistanceConstraint(atomNameI,atomNameJ,lower,upper)

    def measureTree(self):
        for entity in [entity for entity in self.molecule.getEntities()]:
            if type(entity) is Polymer:
                prfStartAtom = self.getEntityTreeStartAtom(entity)
                treeStartAtom = self.entityEntryDict[entity]
                if prfStartAtom == treeStartAtom:
                    continue
                else:
                    ### To remeasure, coordinates should be generated for the entity ###
                    entity.genCoordinates(None)
            self.setupAtomProperties(entity)
            if entity in self.entityEntryDict:
                atom = self.entityEntryDict[entity]
                entity.genMeasuredTree(atom)

    def setupAtomProperties(self, compound):
        pI = PathIterator(compound)
        nodeValidator = NodeValidator()
        pI.init(nodeValidator)
        pI.processPatterns()
        pI.setProperties("ar", "AROMATIC");
        pI.setProperties("res", "RESONANT");
        pI.setProperties("r", "RING");
        pI.setHybridization();


    def setupTree(self, treeDict):
        """Creates the tree path and setups the coordinates of the molecule from all the entities

        Keyword arguments:
        treeDict -- A dictionary that might contain start, end, and measure

        Dictionary Keys:
        start   -- Full name of the first atom in building the tree
        end     -- Full name of the last atom in building the tree
        """
        (start, end) = ((treeDict['start'] if 'start' in treeDict else None,
                                 treeDict['end'] if 'end' in treeDict else None)
                                 if treeDict else (None, None))

        if start:
            startAtom = start
        else:
            startAtom = None
        if end:
            endAtom = end
        else:
            endAtom = None
        Molecule.makeAtomList()
        mol = self.molecule
        mol.resetGenCoords()
        mol.invalidateAtomArray()
        mol.invalidateAtomTree()
        atree = AngleTreeGenerator()
        atree.genTree(mol,startAtom, endAtom)
        mol.setupRotGroups()
        mol.genCoords()

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

    def addDistanceFile(self,file, mode='nv', keep=None):
        if mode == 'cyana':
            self.cyanaDistanceFiles[file] = keep
        elif mode == 'xplor':
            self.xplorDistanceFiles[file] = keep
	elif mode == 'nmrfx':
	    self.nmrfxDistanceFiles[file] = keep
        else:
            self.nvDistanceFiles[file] = keep

    def addRDCFile(self,file, mode='nv', keep=None):
        if mode == 'cyana':
            self.cyanaRDCFiles[file] = keep
        elif mode == 'xplor':
            self.xplorRDCFiles[file] = keep

    def readAngleFiles(self):
        for file in self.cyanaAngleFiles:
            self.readCYANAAngles(file,self.molName)
        for file in self.xplorAngleFiles:
            xplorFile = xplor.XPLOR(file)
            resNames = self.getResNameLookUpDict()
            xplorFile.readXPLORAngleConstraints(self.dihedral, resNames)
        for file in self.nvAngleFiles:
            self.loadDihedralsFromFile(file)

    def readDistanceFiles(self):
        for file in self.cyanaDistanceFiles.keys():
            lowerFileName = file+'.lol'
            upperFileName = file+'.upl'
            self.readCYANADistances([lowerFileName, upperFileName],self.molName, keepSetting=self.cyanaDistanceFiles[file])
	
	for file in self.nmrfxDistanceFiles.keys():
	    self.readNMRFxDistanceConstraints(file, keepSetting = self.nmrfxDistanceFiles[file])

        for file in self.nvDistanceFiles.keys():
            self.loadDistancesFromFile(file, keepSetting=self.nvDistanceFiles[file])

        for file in self.xplorDistanceFiles.keys():
            xplorConstraints = self.readXPLORDistanceConstraints(file, keepSetting = self.xplorDistanceFiles[file])

        # FIXME : should return the name of the file in which an error is found!
        self.addDistanceConstraints()

    def readRDCFiles(self):
        for file in self.cyanaRDCFiles.keys():
            fileName = file
            self.readCYANARDCs(fileName, self.molName, keepSetting=self.cyanaRDCFiles[file])

        for file in self.xplorRDCFiles.keys():
            xplorConstraints = self.readXPLORrdcConstraints(file, keepSetting = self.xplorRDCFiles[file])

        # FIXME : should return the name of the file in which an error is found!
        self.addRDCConstraints()
        

    def addDistanceConstraints(self):
        alreadyAdded = []
        for constraint in self.constraints.values():
            if constraint in alreadyAdded:
                continue
            alreadyAdded.append(constraint)
            lower = constraint.lower
            upper = constraint.upper
            atomNames1 = ArrayList()
            atomNames2 = ArrayList()
            if lower == None:
                lower = 1.5
            if upper == None:
                upper = 100.0
            for pair in constraint.pairs:
                atomName1, atomName2 = pair.split()
                atomNames1.add(atomName1)
                atomNames2.add(atomName2)
	    if (lower < 0.0 and upper <= 0.0):
	        newAtomStr = map(lambda x: "(residue number: '{0}' and residue name: '{1}')".format(x.split('.')[0], x.split('.')[-1]), [atomName1, atomName2])
	        strAtomNames = ', '.join(newAtomStr)
	        errMsg = "\nPlease evaluate bound(s) at atom pair:\n\t- {0}".format(strAtomNames)
	        errMsg += "\n\nNote:"
	        errMsg += " The lower bound ({0}) and the upper bound ({1}) should be nonnegative.".format(lower, upper)
	        errMsg += " The upper bound should be greater than zero."
	        errMsg += " These values were calculate from the constraints provided in the constraint file"
	        errMsg += " for the atom pair seen above."
                raise AssertionError(errMsg)
            try:
                self.energyLists.addDistanceConstraint(atomNames1, atomNames2, lower, upper)
            except IllegalArgumentException as IAE:
                errMsg = "Illegal Argument received." 
                errMsg += "\nJava Error Msg : %s" % (IAE.getMessage())
                raise ValueError(errMsg)

    def addRDCConstraints(self):
        alreadyAdded = []
        if len(RDCConstraintSet.getNames()) > 0:
            rdcSetName = RDCConstraintSet.getNames().get(0)
        else:
            rdcSetName = "test"
            RDCConstraintSet.addSet(rdcSetName)
        rdcSet = RDCConstraintSet.getSet(rdcSetName)
        setAtoms = [[set.getSpSets()[0].getFullName().split(":")[1], set.getSpSets()[1].getFullName().split(":")[1]] for set in rdcSet]
        for constraint in self.constraints.values():
            if constraint in alreadyAdded:
                continue
            alreadyAdded.append(constraint)
            rdc = constraint.rdc
            err = constraint.err
            atomName1, atomName2 = constraint.pairs[0].split()
            newAtom1Ind = -1
            if setAtoms != []:
                newAtom1Ind = setAtoms[0].index(atomName1);
                if newAtom1Ind == -1:
                    newAtom1Ind = setAtoms[1].index(atomName1);
            if newAtom1Ind >= 0:
                spSets1 = rdcSet.get(newAtom1Ind).getSpSets()
                rdcObj = RDC(rdcSet, spSets1[0], spSets1[1], rdc, err)
                rdcSet.remove(newAtom1Ind);
                rdcSet.add(newAtom1Ind, rdcObj);
            else:
                atom1 = Molecule.getAtomByName(atomName1)
                atom2 = Molecule.getAtomByName(atomName2)
                if atom1 != None and atom2 != None:
                    spSet1 = atom1.getSpatialSet()
                    spSet2 = atom2.getSpatialSet()
                    rdcObj = RDC(rdcSet, spSet1, spSet2, rdc, err)
                    rdcSet.add(rdcObj)
        #for constraint in rdcSet.get():
        #    print constraint.getSpSets()[0].getFullName(), constraint.getSpSets()[1].getFullName(), constraint.getValue(), constraint.getErr()

    def predictRNAShifts(self, typeRCDist="dist"):
        #XXX: Need to complete docstring
        """Predict chemical shifts 

        # Returns:
        shifts (list);
        """
        from org.nmrfx.structure.chemistry.predict import Predictor
        predictor = Predictor()
        for polymer in self.molecule.getPolymers():
            if polymer.isRNA():
                if  (typeRCDist.lower()=='dist'):
                    predictor.predictRNAWithDistances(polymer, 0, 0, False)
                else:
                    predictor.predictRNAWithRingCurrent(polymer, 0, 0)

        shifts = []
        atoms = self.molecule.getAtoms()
        for atom in atoms:
            name = atom.getShortName()
            ppm = atom.getRefPPM()
            if ppm != None:
                shift = []
                shift.append(str(name))
                shift.append(ppm)
                shifts.append(shift)
        return shifts

    def setBasePPMs(self,filterString="*.H8,H6,H5,H2,H1',H2',H3'"):
        self.energyLists.setRingShifts(filterString)
        atoms = self.energyLists.getRefAtoms()

    def setShifts(self,shiftFile):
        #XXX: Need to complete docstring
        """Reads a chemical shifts from file and returns array of shifts

        # Parameters:

        shiftFile (string); chemical shifts file

        # Returns:

        shifts (list);
        """

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
        #XXX: Need to complete docstring
        """ Set up parameters and methods to run program and generate structure.

        # Parameters:

        homeDir (string);
        seed (int);
        writeTrajectory (bool);
        usePseudo (bool);
        useShifts (bool);
        """

        self.seed = seed
        self.eTimeStart = time.time()
        self.useDegrees = False
        self.setupEnergy(self.molName,self.energyLists, usePseudo=usePseudo,useShifts=useShifts)
        self.loadDihedrals(self.angleStrings)
        self.addRingClosures() # Broken bonds are stored in molecule after tree generation. This is to fix broken bonds
        self.setForces({'repel':0.5,'dis':1,'dih':5})
        self.setPars({'coarse':False,'useh':False,'dislim':self.disLim,'end':2,'hardSphere':0.15,'shrinkValue':0.20})
        if writeTrajectory:
            self.trajectoryWriter = TrajectoryWriter(self.molecule,"output.traj","traj")
            selection = "*.ca,c,n,o,p,o5',c5',c4',c3',o3'"
            self.molecule.selectAtoms(selection)
            self.molecule.setAtomProperty(2,True)

    def addRingClosures(self):
        """ Close ring structure using distance constraint on specified atoms within ring."""
        ringClosures = self.molecule.getRingClosures();
        entityIDs = [entity.getIDNum() for entity in self.molecule.getEntities()] 
        entityNames = [entity.getName() for entity in self.molecule.getEntities()] 
        
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
        self.setForces({'repel':0.5,'dis':1,'dih':5})
        self.setPars({'useh':False,'dislim':self.disLim,'end':2,'hardSphere':0.0,'shrinkValue':0.20})
        if steps > 0:
            self.refine(nsteps=steps,radius=20, alg=alg);
        if gsteps > 0:
            self.gmin(nsteps=gsteps,tolerance=1.0e-10)
        self.setPars({'useh':False,'dislim':self.disLim,'end':1000,'hardSphere':0.0,'shrinkValue':0.20})
        self.gmin(nsteps=100,tolerance=1.0e-6)
        if self.eFileRoot != None:
            self.dump(0.1,self.eFileRoot+'_prep.txt')

    def annealPrep(self,dOpt, steps=100):
        ranfact=20.0
        self.setSeed(self.seed)
        #self.putPseudo(18.0,45.0)
        #raise ValueError()
        self.randomizeAngles()
        energy = self.energy()
        forceDict = self.settings.get('force')
        irp = forceDict.get('irp', 0.015) if forceDict else 0.015

        self.updateAt(5)
        self.setForces({'repel':0.5,'dis':1,'dih':5,'irp':irp})

        for end in [3,10,20,1000]:
            self.setPars({'useh':False,'dislim':self.disLim,'end':end,'hardSphere':0.15,'shrinkValue':0.20})
            self.gmin(nsteps=steps,tolerance=1.0e-6)
        if self.eFileRoot != None and self.reportDump:
            self.dump(-1.0,-1.0,self.eFileRoot+'_prep.txt')
	#exit()

    def anneal(self,dOpt=None,stage1={},stage2={}):
        from anneal import runStage
        from anneal import getAnnealStages
        dOpt = dOpt if dOpt else dynOptions()
        self.annealPrep(dOpt, 100)
        self.updateAt(dOpt['update'])
        energy = self.energy()
        rDyn = self.rinertia()
        rDyn.setKinEScale(dOpt['kinEScale'])
        stages = getAnnealStages(dOpt, self.settings)
        for stage in stages:
            runStage(stage, self, rDyn)
				
        self.gmin(nsteps=dOpt['polishSteps'],tolerance=1.0e-6)
        if dOpt['dfreeSteps']> 0:
            self.refine(nsteps=dOpt['dfreeSteps'],radius=20, alg=dOpt['dfreeAlg']);
        ec = self.molecule.getEnergyCoords()

    def cdynamics(self, steps, hiTemp, medTemp, timeStep=1.0e-3):
        self.updateAt(20)
        self.setForces({'repel':5.0,'dis':1.0,'dih':5})
        self.setPars({'coarse':True, 'end':1000,'useh':False,'hardSphere':0.15,'shrinkValue':0.20})
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

        self.setPars({'useh':True,'shrinkValue':0.05,'shrinkHValue':0.05})

        timeStep = rDyn.getTimeStep()/2.0
        rDyn.continueDynamics(timeStep)
        rDyn.run(0.35)


        if self.eFileRoot != None:
            self.dump(0.1,self.eFileRoot+'_dyn.txt')


    def dynrun(self, steps, temp, timeStep=1.0e-3, timePower=4.0, stage1={}):
        self.updateAt(20)
        self.setForces({'repel':0.5,'dis':1.0,'dih':5})
        self.setPars({'end':1000,'useh':False,'hardSphere':0.15,'shrinkValue':0.20})
        self.setPars(stage1)
        rDyn = self.rinertia()
        rDyn.initDynamics(temp,temp,steps,timeStep, timePower)
        rDyn.run(1.0)

    def sgd(self,dOpt=None,stage1={},stage2={}):
        if (dOpt==None):
            dOpt = dynOptions()

        self.annealPrep(dOpt, 100)

        self.updateAt(dOpt['update'])
        irp = dOpt['irpWeight']
        self.setForces({'repel':0.5,'dis':1.0,'dih':5,'irp':irp})
        self.setPars({'end':1000,'useh':False,'hardSphere':0.15,'shrinkValue':0.20})
        self.setPars(stage1)
        energy = self.energy()

        steps = dOpt['steps']
        self.sgdmin(2*steps/3)
        self.setPars({'useh':True,'hardSphere':0.0,'shrinkValue':0.0,'shrinkHValue':0.0})
        self.sgdmin(steps/3)


    def polish(self, steps, usePseudo=False, stage1={}):
        #XXX: Need to complete docstring
        """ Calls the functions relevant for generation of structures...?

        # Parameters:

        * steps (int);
        * usePseudo (bool);
        * stage1 (dict);

        """

        self.refine(nsteps=steps/2,useDegrees=self.useDegrees,radius=0.1);
        self.setForces({'repel':2.0,'dis':1.0,'dih':5})
        self.setPars({'dislim':self.disLim,'end':1000,'useh':True,'shrinkValue':0.07,'shrinkHValue':0.00})
        self.setPars(stage1)
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
        """ Prints out energy and execution time to the commandline.
            Handles parameters for various methods if output directory has been generated.
        """

        energy = self.energy()
        if self.outDir != None:
            import osfiles
            angleFile = osfiles.getAngleFile(self)
            pdbFile = osfiles.getPDBFile(self)
            energyFile = osfiles.getEnergyFile(self)

            self.writeAngles(angleFile)
            molio.savePDB(self.molecule, pdbFile)
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
        """ Writes a dump file containing distance violations based on input distance
            constraints and actual distance between atoms.

        # Parameters:

        * fileName (string); name of the output dump file
        * delta (float);
        * atomPat (string);
        * maxDis (float);
        * prob (float);
        * fixLower (float);
        """

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
        """ Writes a dump file containing dihedral violations based on input dihedral constraints and actual dihedral.

        # Parameters:

        * fileName (string); name of angles file
        * delta (int); 

	# Returns:

	_ (None);
        """

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
        #XXX: Need to complete docstring
        """Using specified phi and psi angles to calculate dihedral angle and generate coordinates.

        # Parameters:

        * phi (float); rotation angles around bonds b/t N - Calpha
        * psi (float); rotation angles around bonds b/t Calpha - C

        # Returns:

	_ (None);
        """

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
        dOpt = dynOptions({'highFrac':0.4})
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
    dOpt = dynOptions({'steps':150000,'highFrac':0.4})
    refiner.sgd(dOpt)
    refiner.output()
