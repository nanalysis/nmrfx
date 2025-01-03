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
from org.nmrfx.chemistry import MoleculeFactory
from org.nmrfx.structure.rna import SSGen
from org.nmrfx.chemistry import Atom
from org.nmrfx.structure.chemistry.energy import EnergyLists
from org.nmrfx.structure.chemistry.energy import ForceWeight
from org.nmrfx.structure.chemistry.energy import Dihedral
from org.nmrfx.structure.chemistry.energy import GradientRefinement
from org.nmrfx.structure.chemistry.energy import CmaesRefinement
#from org.nmrfx.structure.chemistry.energy import FireflyRefinement
from org.nmrfx.structure.chemistry.energy import RNARotamer
from org.nmrfx.chemistry.io import PDBFile
from org.nmrfx.chemistry.io import SDFile
from org.nmrfx.chemistry.io import Sequence
from org.nmrfx.structure.chemistry.io import TrajectoryWriter
from org.nmrfx.structure.rna import SSLayout
from org.nmrfx.chemistry import Polymer
from org.nmrfx.chemistry import Compound
from org.nmrfx.structure.rna import AllBasePairs
from org.nmrfx.structure.chemistry.energy import NEFSTARStructureCalculator

from org.nmrfx.structure.chemistry.miner import PathIterator
from org.nmrfx.structure.chemistry.miner import NodeValidator
from org.nmrfx.structure.chemistry.energy import AngleTreeGenerator

#from tcl.lang import NvLiteShell
#from tcl.lang import Interp
from java.lang import String, NullPointerException, IllegalArgumentException
from java.util import ArrayList
from org.nmrfx.chemistry.constraints import RDCConstraint
from org.nmrfx.chemistry.constraints import RDCConstraintSet
from org.nmrfx.chemistry import SpatialSet

from anneal import getAnnealStages
from anneal import runStage

#tclInterp = Interp()
#tclInterp.eval("puts hello")
#tclInterp.eval('package require java')
#tclInterp.eval('java::load org.nmrfx.structure.chemistry.ChemistryExt')


protein3To1 = {"ALA":"A","ASP":"D","ASN":"N","ARG":"R","CYS":"C","GLU":"E","GLN":"Q","ILE":"I",
    "VAL":"V","LEU":"L","PRO":"P","PHE":"F","TYR":"Y","TRP":"W","LYS":"K","MET":"M",
    "HIS":"H","GLY":"G","SER":"S","THR":"T"}

bondOrders = ('SINGLE','DOUBLE','TRIPLE','QUAD')

protein1To3 = {protein3To1[key]: key for key in protein3To1}


rnaBPPlanarity = {
    'GC':[['C6p','C4',0.5],['C2','C2p',0.2]],
    'CG':[['C4','C6p',0.5],['C2p','C2',0.2]],
    'AU':[['C6p','C4',0.5],['C2','C2p',0.2]],
    'UA':[['C4','C6p',0.5],['C2p','C2',0.2]]
}

gnraHBonds = [["N2","N7",2.4,2.8],["N3","N6",2.7,5.0],["N2","OP1",2.4,2.9]]

addPlanarity = False

class Cluster:
    def __init__(self,name,regex,suites,chi,bp,hb):
        self.name = name
        self.regex = regex
        self.suites = suites
        self.chi = chi
        self.bp = bp
        self.hb = hb
        self.repSeqs = []

def getClusterDict():
    clusterDict = {}
    cluster_table_file = "cluster_table.txt"
    lines = loadFile(cluster_table_file)
    currCluster = None
    for nLine, line in enumerate(lines):
        if nLine%2 != 0:
            currCluster.repSeqs += [seq for seq in line.strip('\n').split()]
            continue
        line = line.strip('\n').split('\t')
        name,regex,suites,chi = line[:4]
        nSuites = int(len(suites)/2)
        suites = [suites[i*2:(i+1)*2] for i in range(nSuites)]
        bp = [item[3:] for item in line[4:] if item.startswith("bp")]
        hb = [item[3:] for item in line[4:] if item.startswith("hb")]
        clusterDict[name] = Cluster(name,regex,suites,chi,bp,hb)
        currCluster = clusterDict[name]
    return clusterDict

def getCluster(tetraLoopSeq):
    matched_cluster = None
    clusterDict = getClusterDict()
    search_representative_seqs = [cluster for cluster in clusterDict.values() if any([tetraLoopSeq == seq for seq in cluster.repSeqs])]
    if search_representative_seqs:
        matched_cluster  = search_representative_seqs[0]
    else:
        search_regex = [cluster for cluster in clusterDict.values() if re.compile(cluster.regex).match(tetraLoopSeq)]
        if search_regex:
            matched_cluster = search_regex[0]
    return matched_cluster

def getLoopType(ss):
   residues = ss.getResidues()
   if ss.getName() == "Loop":
       if len(residues) == 4:
           residues = [residues[0].getPrevious()] + residues + [residues[-1].getNext()]
           loopSeq = ''.join([residue.getName() for residue in residues])
           cluster = getCluster(loopSeq) 
           if cluster:
               return ":"+cluster.name
       return str(len(residues))
   return ""
   
def getRNAResType(ss, residues, residue):
    ssType = ss.getName()
    res = int(residue.getNumber())
    subType = 'c'
    rName = residue.getName()
    nextRes = residue.getNext()
    prevRes = residue.getPrevious()
    pairRes = residue.pairedTo
    ssNextName = ""
    ssPrevName = ""
    if nextRes != None:
       ssNextName = nextRes.getSecondaryStructure().getName()
    if prevRes != None:
       ssPrevName = prevRes.getSecondaryStructure().getName()
       if ssNextName == "Loop":
           ssNextType = getLoopType(nextRes.getSecondaryStructure())
           subType = "hL:" + ssNextType 
    if ssType == "Helix":
        subType = 'h'
        if prevRes == None:
            subType = "h5"
        elif ssNextName == "Loop":
            ssNextType = getLoopType(nextRes.getSecondaryStructure())
            subType = "hL" + ssNextType
        elif ssPrevName == "Loop":
            ssPrevType = getLoopType(prevRes.getSecondaryStructure())
            subType = "hl" + ssPrevType
        elif ssNextName == "Bulge":
            subType = "hB0"+":"'B'+str(len(nextRes.getSecondaryStructure().getResidues()))
        elif ssPrevName == "Bulge":
            subType = "hB1"+":"'B'+str(len(prevRes.getSecondaryStructure().getResidues()))
        elif pairRes.getPrevious():
            if pairRes.getPrevious().getSecondaryStructure().getName() == "Bulge":
                subType = "hb1"+":"'B'+str(len(pairRes.getPrevious().getSecondaryStructure().getResidues()))
        if pairRes.getNext():
            if pairRes.getNext().getSecondaryStructure().getName() == "Bulge":
                subType = "hb0"+":"'B'+str(len(pairRes.getNext().getSecondaryStructure().getResidues()))

    elif ssType == "Loop":
        loopType = getLoopType(ss)
        subType = 'T' + loopType 
    elif ssType == "Bulge":
        subType = 'B'+str(len(ss.getResidues()))
        #check whether res num is greater than paired res num, to determine orientation of bulge
        subType = subType+'c' if int(ss.getResidues()[0].getPrevious().getNumber()) > int(ss.getResidues()[0].getPrevious().pairedTo.getNumber()) else subType
    if pairRes:
        rName2 = pairRes.getName()
        rName += rName2
        if pairRes.getAtom('X1') != None:
            if pairRes == residues[0]:
                subType = subType+'xb'
            else:
                subType = subType+'xe'
    if residue.getAtom('X1') != None:
        if residue == residues[0]:
            subType = subType+'Xb'
        else:
            subType = subType+'Xe'
    return subType


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
    if linkers != None and len(linkers) > 0:
        seqArray.append('-nocap')
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

def loadFile(fileName):
    if os.path.exists(fileName):
        with open(fileName, 'r') as f1:
            lines = f1.read().split('\n')
    else:
        resourceName = "data/" + fileName
        content = molio.loadResource(resourceName)
        lines = content.split('\n')
    return lines

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
        'cffSteps'         : 0,
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
    allowedKeys['param'] = ['coarse', 'useh', 'hardSphere', 'start', 'end', 'shrinkValue', 'shrinkHValue', 'dislim', 'swap','updateAt']
    allowedKeys['force'] = ['elec', 'cffnb', 'nbmin', 'repel', 'dis', 'tors', 'dih', 'irp', 'shift', 'bondWt','stack']
    allowedKeys = allowedKeys[type]

    strictDict = StrictDict(defaultErr=type+'s')
    strictDict.setInclusions(allowedKeys)
    strictDict.strictUpdate(initDict)
    return strictDict

class refine:
    def __init__(self):
        self.NEFfile = ''
        self.energyLists = None
        self.dihedral = None
        self.ssGen = None
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
        self.molecule = MoleculeFactory.getActive()
        self.entityEntryDict = {} # map of entity to linker atom
        self.reportDump = False
        self.mode = 'gen'
        if self.molecule != None:
            self.molName = self.molecule.getName()

    def setAngleDelta(self,value):
        self.angleDelta = value

    def writeAngles(self,fileName, includePseudo=False):
        """
        # Parameters:

        fileName (string); The name of file with angles/dihedral constraints

        # Returns:

        _ (None); This method serves as a setter method to write dihedral angles to a specified file.

        See also: `writeDihedrals(fileName)`
        """
        self.dihedral.writeDihedrals(fileName, includePseudo)

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

    def calcDerivError(self,delta):
        grefine = GradientRefinement(self.dihedral)
        return grefine.calcDerivError(delta)

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

    def setForces(self,forceDict):
        #XXX: Need to complete docstring
        """
        # Parameters:

        * cffnb (_);
        * nbmin (_);
        * repel (_);
        * elec (_);
        * dis (_);
        * tors (_);
        * dih (_);
        * shift (_);
        * bondWt (_);
        * stack (_);
        """

        if not forceDict:
            return
        forceWeightOrig = self.energyLists.getForceWeight()
        getOrigWeight = {
            'elec'   : forceWeightOrig.getElectrostatic(),
            'cffnb' : forceWeightOrig.getCFFNB(),
            'nbmin' : forceWeightOrig.getNBMin(),
            'repel'  : forceWeightOrig.getRepel(),
            'dis'    : forceWeightOrig.getNOE(),
            'tors'   : forceWeightOrig.getDihedralProb(),
            'dih'    : forceWeightOrig.getDihedral(),
            'irp'    : forceWeightOrig.getIrp(),
            'shift'  : forceWeightOrig.getShift(),
            'stack'  : forceWeightOrig.getStacking(),
            'bondWt' : forceWeightOrig.getBondWt()
        }
        forces = ('elec','cffnb','repel','dis','tors','dih','irp','shift','bondWt','stack','nbmin')
        forceWeights = []
        if 'cffnb' in forceDict:
            if forceDict['cffnb'] > 0.0:
                forceDict['repel'] = -1.0
        elif getOrigWeight['cffnb'] > 0.0:
                forceDict['repel'] = -1.0

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
        output = "cffnb %5.2f nbmin %5.2f repel %5.2f elec %5.2f dis %5.2f dprob %5.2f dih %5.2f irp %5.2f shift %5.2f bondWt %5.2f stack %5.2f" % (fW.getCFFNB(),fW.getNBMin(), fW.getRepel(),fW.getElectrostatic(),fW.getNOE(),fW.getDihedralProb(),fW.getDihedral(),fW.getIrp(), fW.getShift(), fW.getBondWt(), fW.getStacking())
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
            except TypeError as e:
                print repr(e)
                self.readLinkerDict(linkerList)

    def getDistanceConstraintSet(self, name):
        molConstraints = self.molecule.getMolecularConstraints()
        disCon = molConstraints.getNoeSet(name, True)
        return disCon

    def addDistanceConstraint(self, atomName1,atomName2,lower,upper,bond=False):
        if bond == False:
            disCon = self.getDistanceConstraintSet("noe_restraint_list")
        else:
            disCon = self.getDistanceConstraintSet("bond_restraint_list")
            disCon.containsBonds(bond)
        disCon.addDistanceConstraint(atomName1,atomName2,lower,upper,bond)

    def getAngleConstraintSet(self):
        molConstraints = self.molecule.getMolecularConstraints()
        optSet = molConstraints.activeAngleSet()
        angleCon = None
        if optSet.isPresent():
            angleCon = optSet.get()
        else:
            angleCon = molConstraints.newAngleSet("default")
        return angleCon

    def addAngleConstraintAtoms(self, atoms, lower, upper, scale):
        self.getAngleConstraintSet().addAngleConstraint(atoms, lower, upper, scale)

    def addAngleConstraintAtomNames(self, atomNames, lower, upper, scale):
        atoms = [self.molecule.getAtomByName(atomName) for atomName in atomNames]
        self.getAngleConstraintSet().addAngleConstraint(atoms, lower, upper, scale)

    def addAngleConstraint(self, angleConstraint):
        self.getAngleConstraintSet().add(angleConstraint)

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
            nLinks = linkerDict['n'] if 'n' in linkerDict else 6
            linkLen = linkerDict['length'] if 'length' in linkerDict else 5.0
            valAngle = linkerDict['valAngle'] if 'valAngle' in linkerDict else 110.0
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
                    print 'createLinkA'
                    self.molecule.createLinker(startAtom, endAtom, order, length)
                else:
                    atomName1, atomName2 = bondDict['atoms']
                    startAtom = self.molecule.getAtomByName(atomName1)
                    endAtom = self.molecule.getAtomByName(atomName2)
                    atomName1 = startAtom.getFullName()
                    atomName2 = endAtom.getFullName()
                    lower = length - .0001
                    upper = length + .0001
                    self.addDistanceConstraint(atomName1,atomName2,lower,upper,True)
        else:
            if 'rna' in linkerDict:
                self.addRNALinker(startAtom, endAtom, nLinks, linkLen, valAngle,  dihAngle)
            else:
                newAtoms = self.molecule.createLinker(startAtom, endAtom,  nLinks, linkLen, valAngle,  dihAngle)

    def readRNALinkerDict(self, rnaLinkerDict, formLinks=True):
        linkAtoms = []
        for connection in rnaLinkerDict:
            connectors = connection['connect']
            if len(connectors) != 2:
                raise ValueError('Should be two elements in connection')
            atoms = []
            for i, connector in enumerate(connectors):
                if connector.find(':') == -1:
                    polymer = self.molecule.getEntity(connector)
                    residues = polymer.getResidues()
                    if i == 0:
                        residue = residues[-1]
                        atom = residue.getAtom("H3'")
                    else:
                        residue = residues[0]
                        atom = residue.getAtom("O5'")
                else:
                    if i == 0:
                        aName = connector+".H3'"
                        atom = self.molecule.getAtomByName(aName)
                    else:
                        aName = connector+".P"
                        atom = self.molecule.getAtomByName(aName)
                        if atom == None:
                            aName = connector+".O5'"
                            atom = self.molecule.getAtomByName(aName)
                atoms.append(atom)

            res0 = atoms[0].getEntity()
            res1 = atoms[1].getEntity()
            poly0 = atoms[0].getTopEntity()
            poly1 = atoms[1].getTopEntity()

            linkAtoms.append(atoms)
            if formLinks:
                nLinks = connection['n'] if 'n' in connection else 6
                linkLen = connection['length'] if 'length' in connection else 5.0
                valAngle = connection['valAngle'] if 'valAngle' in connection else 110.0
                dihAngle = connection['dihAngle'] if 'dihAngle' in connection else 135.0
                if poly0 == poly1:
                    print 'break bond ', poly0, poly1
                    self.breakBond(atoms[1].getParent(), atoms[1])
                self.addRNALinker(atoms[0], atoms[1], nLinks, linkLen, valAngle,  dihAngle)
        return linkAtoms
            
    def addRNALinker(self, startAtom, endAtom,  nLinks, linkLen, valAngle, dihAngle):
        if endAtom.getName() == "P":
            self.addRNALinkerTurn(startAtom, endAtom, nLinks, linkLen, valAngle, dihAngle)
        else:
            self.addRNALinkerHelix(startAtom, endAtom, nLinks, linkLen, valAngle, dihAngle)


    def addRNALinkerHelix(self, startAtom, endAtom, nLinks, linkLen, valAngle, dihAngle):
#            linkLens = [5.0,5.0,5.0,5.0,5.0,5.0,1.41,1.6, 1.59]
#            valAngles = [110.0,110.0,110.0,110.0,110.0,110.0,112.21,120.58, 104.38]
#            linkNames = ["X1","X2","X3","X4","X5","X6","XC3'","XO3'","XP"]

            linkNames = [""]*(nLinks+3)
            linkLens = [linkLen]*(nLinks+3)
            valAngles = [valAngle]*(nLinks+3)

            linkLens[-3] = 1.41
            linkLens[-2] = 1.60
            linkLens[-1] = 1.59
            valAngles[-3] = 112.21
            valAngles[-2] = 120.58
            valAngles[-1] = 104.38
            for i in range(nLinks):
                linkNames[i] = "X"+str(i+1)
            linkNames[-3] = "XC3'"
            linkNames[-2] = "XO3'"
            linkNames[-1] = "XP"

            newAtoms = self.molecule.createLinker(startAtom, endAtom,  linkLens, valAngles, linkNames, dihAngle)

    def addRNALinkerTurn(self, startAtom, endAtom, nLinks, linkLen, valAngle, dihAngle):
#            linkLens = [5.0,5.0,5.0,5.0,5.0,5.0,1.52,1.41,1.6]
#            valAngles = [110.0,110.0,110.0,110.0,110.0,110.0,116.47,112.21,120.58]
#            linkNames = ["X1","X2","X3","X4","X5","X6","XC4'","XC3'","XO3'"]

            linkNames = [""]*(nLinks+3)
            linkLens = [linkLen]*(nLinks+3)
            valAngles = [valAngle]*(nLinks+3)

            linkLens[-2] = 1.52
            linkLens[-1] = 1.41
            valAngles[-2] = 116.47
            valAngles[-1] = 112.21
            for i in range(nLinks):
                linkNames[i] = "X"+str(i+1)
            linkNames[-3] = "XC4'"
            linkNames[-2] = "XC3'"
            linkNames[-1] = "XO3'"

            print endAtom,endAtom.getParent()

            oParent1 = endAtom.getParent()
            oParent2 = oParent1.getParent()
            oParent3 = oParent2.getParent()
            newAtoms = self.molecule.createLinker(startAtom, endAtom,  linkLens, valAngles, linkNames, dihAngle)

            atomName1 = newAtoms[-1].getFullName() 
            atomName2 = oParent1.getFullName()
            self.addDistanceConstraint(atomName1,atomName2,0.0,0.01,True)
            atomName1 = newAtoms[-2].getFullName() 
            atomName2 = oParent2.getFullName()
            self.addDistanceConstraint(atomName1,atomName2,0.0,0.01,True)
            atomName1 = newAtoms[-3].getFullName() 
            atomName2 = oParent3.getFullName()
            print atomName1,atomName2
            self.addDistanceConstraint(atomName1,atomName2,0.0,0.01,True)


    def processBonds(self,bondDict, phase):
        for bondInfo in bondDict:
            bondMode = bondInfo['mode'] if 'mode' in bondInfo else 'add'
            if phase == 'add' and bondMode == 'add':
                distances = bondInfo['length'] if 'length' in bondInfo else 1.08
                if not isinstance(distances,float):
                    lower,upper = distances
                else:
                    lower = distances - 0.001
                    upper = distances + 0.001
                atomName1, atomName2 = bondInfo['atoms']
                self.addDistanceConstraint(atomName1,atomName2,lower,upper,True)
            elif phase == 'float' and bondMode == 'float':
                atomName1, atomName2 = bondInfo['atoms']
                atom1 = self.molecule.getAtomByName(atomName1)
                atom2 = self.molecule.getAtomByName(atomName2)
                self.floatBond(atom1, atom2)
            elif phase == 'float' and bondMode == 'constrain':
                atomName1, atomName2 = bondInfo['atoms']
                atom1 = self.molecule.getAtomByName(atomName1)
                atom2 = self.molecule.getAtomByName(atomName2)
                self.constrainDistance(atom1, atom2)
            elif phase == 'break' and (bondMode == 'break' or bondMode == 'float'):
                atomName1, atomName2 = bondInfo['atoms']
                atom1 = self.molecule.getAtomByName(atomName1)
                atom2 = self.molecule.getAtomByName(atomName2)
                self.breakBond(atom1, atom2)

    def floatBond(self, atom1, atom2):
        ringClosures = self.molecule.getRingClosures()
        AngleTreeGenerator.addRingClosureSet(ringClosures, atom1, atom2)

    def constrainDistance(self, atom1, atom2):
        ringClosures = self.molecule.getRingClosures()
        AngleTreeGenerator.addConstrainDistance(ringClosures, atom1, atom2)
                
    def breakBond(self, atom1, atom2):
        atom1.removeBondTo(atom2)
        atom2.removeBondTo(atom1)
        atom1.daughterAtom = None
        atom1.rotActive = False
        atom1.rotUnit = -1
        atom1.rotGroup = None
                
    def addCyclicBond(self, polymer):
        # to return a list atomName1, atomName2, distance
        distanceConstraints = polymer.getCyclicConstraints()
        for distanceConstraint in distanceConstraints:
            self.bondConstraints.append(distanceConstraint)


    def getPars(self):
        el = self.energyLists
        coarseGrain = el.getCourseGrain()
        includeH = el.getIncludeH()
        updateAt = el.getUpdateAt()
        hardSphere = el.getHardSphere()
        deltaStart = el.getDeltaStart()
        deltaEnd = el.getDeltaEnd()
        shrinkValue = el.getShrinkValue()
        shrinkHValue = el.getShrinkHValue()
        disLim = el.getDistanceLimit()
        fW = self.energyLists.getForceWeight()
        output = "coarse %5s includeH %5s hard %5.2f shrinkValue %5.2f shrinkHValue %5.2f updateAt %3d deltaStart %4d deltaEnd %4d disLim %5.2f" % (coarseGrain,includeH,hardSphere,shrinkValue,shrinkHValue,updateAt, deltaStart,deltaEnd,disLim)
        return output

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
            'updateAt'      : self.energyLists.setUpdateAt,
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

    def refineNonDeriv(self,nsteps=10000,stopFitness=0.0,radius=0.01,alg="cmaes",ninterp=1.2,lambdaMul=1, nFireflies=18, diagOnly=1.0,useDegrees=False):
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
                self.addAngleConstraintAtoms(atoms, lower, upper, scale)
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
                self.addAngleConstraintAtoms(atoms, lower, upper, scale)
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
        self.addDistanceConstraint(atomName1,atomName2,lower,upper)

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
            entryAtom = self.molecule.getAtomByName(entryAtomName)
            startEntity = entryAtom.getEntity()
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
                atoms = []
                for atomName in atomSpecs:
                    atom = self.molecule.getAtomByName(atomName)
                    if atom == None:
                        msg = 'Linker atom "' + atomName + '" doesn\'t exist'
                        raise ValueError(msg)
                    atoms.append(atom)
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

    def validateLinkerList(self,linkerList, treeDict, rnaLinkerDict):
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
        if rnaLinkerDict:
            linkerList = []
            linkerAtoms = self.readRNALinkerDict(rnaLinkerDict, False)
            for linkPair in linkerAtoms:
                (startAtom, endAtom) = linkPair
                newLinker = {'atoms': [startAtom.getFullName(), endAtom.getFullName()]}
                linkerList.append(newLinker)
                for i,atom in enumerate(linkPair):
                    entName = atom.getTopEntity().getName()
                    if entName in unusedEntities:
                        unusedEntities.remove(entName)
        elif linkerList:
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
            startAtom = firstEntity.getLastAtom().getFullName()
            endAtom = self.getEntityTreeStartAtom(entity).getFullName()
            newLinker = {'atoms': [startAtom, endAtom]}
            linkerList.append(newLinker)
        return linkerList

    def loadFromYaml(self,data, seed, fileName=""):
        #XXX: Need to complete docstring
        """
        This procedure grabs the data presented in the YAML file and executes a series
        of programs to set up the structure parameters (i.e read sequence, distance files, etc.)
        """

        molData = {}
        residues = None
        rnaLinkerDict = None

        if fileName != '':
            if fileName.endswith('.pdb'):
                molio.readPDB(fileName)
            elif fileName.endswith('.nef'):
                self.setupBMRBNEFMolecule(fileName, True)
            elif fileName.endswith('.str'):
                self.setupBMRBNEFMolecule(fileName, False)
            else:
                raise ValueError("Filename must end in .pdb, .str or .nef")
        else:
            # Checks if NEF file is specified to process it.
            # Even if NEF file is specified, this control flow
            # still checks whether 'molecule' data block is specified
            # in the YAML file.
            if 'nef' in data:
                fileName = data['nef']
                self.setupBMRBNEFMolecule(fileName, True)
            elif 'star' in data:
                fileName = data['star']
                self.setupBMRBNEFMolecule(fileName, False)

            # Checks if 'molecule' data block is specified.
            if 'molecule' in data:
                molData = data['molecule']
                # Checks if a residue library is included in 'molecule' code block
                # for information about different entities.
                self.reslib = molData['reslib'] if 'reslib' in molData else None
                if self.reslib:
                    PDBFile.setLocalResLibDir(self.reslib)
                if 'rna' in data:
                    if 'links' in data['rna']:
                        rnaLinkerDict = data['rna']['links']

                # Different entities can be specified. Via sequence files
                # or input residues.
                if not 'link' in molData and not rnaLinkerDict:
                    seqReader = Sequence()
                else:
                    seqReader = None
                if 'entities' in molData:
                    molList = molData['entities']
                    molList = prioritizePolymers(molList)
                    for molDict in molList:
                        residues = ",".join(molDict['residues'].split()) if 'residues' in molDict else None
                        self.readMoleculeDict(seqReader, molDict)
                    self.molecule = MoleculeFactory.getActive()
                    if 'rna' in data:
                        self.findRNAHelices(data['rna'])
                        if not 'link' in molData:
                            if 'rna' in data and 'autolink' in data['rna'] and data['rna']['autolink']:
                                rnaLinks,rnaBonds = self.findSSLinks()
                                molData['link'] = rnaLinks
                                data['bonds'] = rnaBonds
                else:
                    #Only one entity in the molecule
                    residues = ",".join(molData['residues'].split()) if 'residues' in molData else None
                    self.readMoleculeDict(seqReader, molData)
                self.molecule = MoleculeFactory.getActive()
                if 'edit' in molData:
                    self.readMolEditDict(seqReader, molData['edit'])


        if 'rna' in data:
            if not self.ssGen:
                self.findRNAHelices(data['rna'])
            if 'vienna' in data['rna']:
                self.vienna = data['rna']['vienna']
                self.addHelicesRestraints(self.vienna)
            if 'rna' in data and 'autolink' in data['rna'] and data['rna']['autolink']:
                rnaLinks,rnaBonds = self.findSSLinks()
                molData['link'] = rnaLinks
                data['bonds'] = rnaBonds
        self.molecule = MoleculeFactory.getActive()
        self.molName = self.molecule.getName()

        treeDict = data['tree'] if 'tree' in data else None
        linkerList = molData['link'] if 'link' in molData else None
        nEntities = len(self.molecule.getEntities())
        nPolymers = len(self.molecule.getPolymers())

        if 'bonds' in data:
            self.processBonds(data['bonds'], 'float')

        if rnaLinkerDict:
            self.molecule.fillEntityCoords()
            rnaLinkerAtoms = self.readRNALinkerDict(rnaLinkerDict, False)
            for atoms in rnaLinkerAtoms:
                atom = atoms[1]
                if atom.getParent() == None:
                    print 'null atom parent', atom
                else:
                    print 'float',atoms[1].getParent(),atoms[1]
                    self.floatBond(atoms[1].getParent(), atoms[1])
                
        # Check to auto add tree in case where there are ligands
        if nEntities > nPolymers:
            if not 'tree' in data:
                data['tree'] = None

        if 'tree' in data and not 'nef' in data:
            if len(self.molecule.getEntities()) > 1:
                linkerList = self.validateLinkerList(linkerList, treeDict, rnaLinkerDict)
            treeDict = self.setEntityEntryDict(linkerList, treeDict)
            self.measureTree()
        else:
            if nEntities > 1:
                if not 'nef' in data:
                    self.molecule.invalidateAtomTree()
                    self.molecule.setupGenCoords()
                    self.molecule.genCoords(False)
                    self.molecule.setupRotGroups()
                    #raise TypeError("Tree mode must be run on molecules with more than one entity")

        if not 'nef' in data:
            for entity in self.molecule.getEntities():
                self.setupAtomProperties(entity)

        if rnaLinkerDict:
            self.readRNALinkerDict(rnaLinkerDict)
        elif linkerList:
            self.addLinkers(linkerList)

        if 'bonds' in data:
            self.processBonds(data['bonds'], 'break')
            self.processBonds(data['bonds'], 'add')

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
        if self.bondConstraints:
            for bondConstraint in self.bondConstraints:
                atomName1, atomName2, distance = bondConstraint.split()
                distance = float(distance)
                self.addDistanceConstraint(atomName1, atomName2, distance - .0001, distance + .0001, True)


        if 'shifts' in data:
            self.readShiftDict(data['shifts'],residues)

        if 'rna' in data:
            self.readRNADict(data)
        self.readSuiteAngles()

        self.readAngleFiles()
        self.readDistanceFiles()

        if 'anneal' in data:
            self.dOpt = self.readAnnealDict(data['anneal'])
        self.energy()

        if 'initialize' in data:
            self.initializeData = (data['initialize'])
        else:
            self.initializeData = None

    def initAnneal(self, data):
        seed = 0
        self.setup('./',seed,writeTrajectory=False, usePseudo=False)
        self.energy()
        self.setSeed(0)
        if 'anneal' in data:
            self.dOpt = self.readAnnealDict(data['anneal'])

    def prepAngles(self):
        print 'Initializing angles'
        ranfact=20.0
        self.setSeed(self.seed)
        self.randomizeAngles()
        data = self.initializeData
        if data != None:
            self.readInitAngles()
            if 'vienna' in data:
                print 'Setting angles based on Vienna sequence'
                self.setAnglesVienna(data['vienna'])
            if 'doublehelix' in data:
                print 'Setting angles based on double helix information'
                self.setAnglesDoubleHelix(data,data['doublehelix'][0]['restrain'] )
            if 'loop' in data:
                print 'Setting angles based on loop zone information'
                self.setAnglesLoop(data,data['loop'][0]['restrain'] )
            if 'file' in data:
                print 'Setting angles based from file '
                self.readAngles(data['file'])
            self.restart()
        self.molecule.genCoords()

    def readInitAngles(self):
        global angleDict
        helixDictFileName = 'angles.txt'
        lines = loadFile('angles.txt')
        headerAtoms = None
        angleDict = {}
        for line in lines:
            fields = line.strip().split()
            if len(fields) < 6:
                continue
            if headerAtoms == None:
                headerAtoms = fields[5:]
            else:
                id = fields[0]
                ssType = fields[1]
                posType = fields[2]
                nucType = fields[3]
                subType = fields[4]
                d = {}
                for field,headerAtom in zip(fields[5:], headerAtoms):
                    if field != '-':
                        d[headerAtom] = float(field)
                angleDict[ssType+':'+posType+':'+nucType+':'+subType] = d
                
    def setAnglesDoubleHelix(self,data,doLock):
        if(doLock):
            print 'Locking angles based on double helix information after setting'
        else:
            print 'Not locking angles after setting based on double helix information'


        # Set angles based on double helix information
        polymers = self.molecule.getPolymers()
        for poly in polymers:
            for entry in data['doublehelix']:
                inHelix = False
                for res in poly.getResidues():
                    if (str(res) == entry['first'][0]) or (str(res) == entry['second'][0]):
                        print 'Helical segment start found at residue: ' + str(res)
                        print 'First 1: ' + entry['first'][1]
                        inHelix = True
                    csplit = str(res).split(':')
                    isplit = csplit[1:]
                    resLett = str(isplit)[2]
                    try:
                        resNum = int(str(isplit)[3:-2])
                    except:
                        print 'Unexpected format for residue name: ' + str(isplit) + '... Using previous residue name'
                    if inHelix:
                        if resLett in ['G', 'A']:
                            nucType = 'GC'
                        else:
                            nucType = 'UA'
                        anglesToSet = angleDict['helix'+':0:'+nucType].copy()
                        if (str(res) == entry['first'][1]):
                            print 'Helix segment end found at residue: ' + str(res)
                            anglesToSet = angleDict['tetra-link'+':0:'+'U'].copy()
                            inHelix = False
                            RNARotamer.setDihedrals(res,anglesToSet, 0.0, doLock and entry['locklast'])
                            print 'Residue: ' + str(res) + ' set with angles: ' + str(anglesToSet)
                        elif (str(res) == entry['first'][0]):
                            RNARotamer.setDihedrals(res,anglesToSet, 0.0, doLock and entry['lockfirst'])
                            print 'Residue: ' + str(res) + ' set with angles: ' + str(anglesToSet)
                        elif (str(res) == entry['second'][0]):
                            anglesToSet = angleDict['tetra-endlink'+':5:'+'A'].copy()
                            print 'Opposite helical segment start found at residue: ' + str(res)
                            RNARotamer.setDihedrals(res,anglesToSet, 0.0, doLock and entry['locklast'])
                            print 'Residue: ' + str(res) + ' set with angles: ' + str(anglesToSet)
                        elif  (str(res) == entry['second'][1]):
                            print 'Helix segment end found at residue: ' + str(res)
                            inHelix = False
                            RNARotamer.setDihedrals(res,anglesToSet, 0.0, doLock and entry['lockfirst'])
                            print 'Residue: ' + str(res) + ' set with angles: ' + str(anglesToSet)
                        else:
                            RNARotamer.setDihedrals(res,anglesToSet, 0.0, doLock)
                            print 'Residue: ' + str(res) + ' set with angles: ' + str(anglesToSet)

    def setAnglesLoop(self,data,doLock):
        if(doLock):
            print 'Locking angles based on loop information after setting'
        else:
            print 'Not locking angles after setting based on loop information'
        # Set angles based on loop information
        polymers = self.molecule.getPolymers()
        loopResDone = 0
        for poly in polymers:
            for entry in data['loop']:
                inLoop = False
                for res in poly.getResidues():
                    if (str(res) == entry['start']):
                        print 'Loop zone start found at residue: ' + str(res)
                        inLoop = True
                    csplit = str(res).split(':')
                    isplit = csplit[1:]
                    resLett = str(isplit)[2]
                    try:
                        resNum = int(str(isplit)[3:-2])
                    except:
                        print 'Unexpected format for residue name: ' + str(isplit) + '... Using previous residue name'
                    if inLoop:
                        loopIndices = ['tetraloop1','tetraloop2','tetraloop3','tetraloop4']
                        nucType = res.getName()
                        anglesToSet = angleDict['tetra'+':'+str(loopResDone+1)+':'+nucType].copy()
                        RNARotamer.setDihedrals(res,anglesToSet, 0.0, doLock)
                        print 'Residue: ' + str(res) + ' set with angles: ' + str(anglesToSet)
                        loopResDone = loopResDone + 1
                        if(loopResDone == len(loopIndices)):
                            return
                    if (str(res) == entry['end']):
                        print 'Loop zone end found at residue: ' + str(res)
                        inLoop = False

    def isStemLoop(self, ssGroups, helix):
        residues = helix.getResidues()
        strandI = residues[0::2]
        strandJ = residues[1::2]
        iLast = strandI[-1]
        jLast = strandJ[-1]
        print(strandI)
        print(strandJ)
        isStem = None
        for ss in ssGroups:
            if ss.getName() == "Loop":
                residues = ss.getResidues()
                if residues[0].getPrevious() == iLast and residues[-1].getNext() == jLast:
                    print('isstem')
                    isStem = ss
                    break
        return isStem

    def setAnglesVienna(self, data):
        doLock = data['restrain']
        lockFirst = data['lockfirst']
        lockLast = data['locklast']
        if 'lockbulge' in data:
            lockBulge = data['lockbulge']
        else:
            lockBulge = doLock
        if 'lockloop' in data:
            lockLoop = data['lockloop']
        else:
            lockLoop = doLock
        polymers = self.molecule.getPolymers()
        allResidues = []
        for polymer in polymers:
            allResidues += polymer.getResidues()
        ssGen = SSGen(self.molecule, self.vienna)
        ssGen.analyze()
        subType='0'
        for ss in ssGen.structures():
            residues = ss.getResidues()
        stemLoops = []
        for ss in ssGen.structures():
            residues = ss.getResidues()
            if ss.getName() == "Helix":
                stemLoop = self.isStemLoop(ssGen.structures(), ss)
                if stemLoop:
                    stemLoops.append(stemLoop)
                strandI = residues[0::2]
                strandJ = residues[1::2]
                for res in residues:
                    subType = getRNAResType(ss, residues, res)
                    firstResI = res == strandI[0]
                    firstResJ = res == strandJ[0]
                    lastResI = res == strandI[-1]
                    lastResJ = res == strandJ[-1]
                    firstRes = firstResI or firstResJ
                    lastRes = lastResI or lastResJ
                    resLett = res.getName()
                    pairLett = res.pairedTo.getName()
                    nucType = resLett
                    if nucType == 'A' or nucType == 'G':
                        nucType = "P"
                    else:
                        nucType = "p"
                    key = 'Helix'+':0:'+nucType+':'+subType
                    if key in angleDict:
                        anglesToSet = angleDict[key].copy()
                        lock = doLock
                        if firstRes and not lockFirst:
                            lock = False
                        if lastRes and not lockLast:
                            lock = False
                        if lastRes and ("hL" in subType or "hl" in subType) and lockLoop:
                            lock = True 
                        RNARotamer.setDihedrals(res,anglesToSet, 0.0, lock)
                    else:
                        subType = 'hL:GNRAXe' if subType[-2] =='X' else 'hl:GNRAxe'
                        genericHelixLinker = 'Helix'+':0:'+nucType+':'+subType
                        anglesToSet = angleDict[genericHelixLinker].copy()
                        lock = True if lastRes and lockLoop else False
                        RNARotamer.setDihedrals(res,anglesToSet,0.0,lock)
            elif ss.getName() == "Loop":
                for iLoop,res in enumerate(residues):
                    subType = getRNAResType(ss, residues, res)
                    nucType = res.getName()
                    if nucType == 'A' or nucType == 'G':
                        nucType = "P"
                    else:
                        nucType = "p"
                    key = 'Loop'+':'+str(iLoop)+':'+nucType+':'+subType
                    if key in angleDict:
                        anglesToSet = angleDict[key].copy()
                        RNARotamer.setDihedrals(res,anglesToSet, 0.0, lockLoop)
            elif ss.getName() == "Bulge":
                for iLoop,res in enumerate(residues):
                    subType = getRNAResType(ss, residues, res)
                    nucType = res.getName()
                    if nucType == 'A' or nucType == 'G':
                        nucType = "P"
                    else:
                        nucType = "p"
                    key = 'Bulge'+':'+str(iLoop)+':'+nucType+':'+subType
                    if key in angleDict:
                        anglesToSet = angleDict[key].copy()
                        RNARotamer.setDihedrals(res,anglesToSet, 0.0, lockBulge)
 
    def readMolEditDict(self,seqReader, editDict):
        for entry in editDict:
            if 'remove' in entry:
                atomToRemove = entry['remove']
                atom = self.molecule.getAtomByName(atomToRemove)
                entity = atom.getEntity()
                entity.removeAtom(atom);
            if 'cis' in entry:
                resToChange = entry['cis']
                atomToChange = resToChange+'.H'
                atom = self.molecule.getAtomByName(atomToChange)
                atom.setDihedral(180.0)
        self.molecule.genCoords(False)
        self.molecule.setupRotGroups()


    def readMoleculeDict(self,seqReader, molDict):
        linkLen = 5.0;
        valAngle = 110.0;
        dihAngle = 135.0;
        if seqReader != None and seqReader.getMolecule() != None:
            mol = seqReader.getMolecule()
            for entity in mol.getEntities():
                self.setupAtomProperties(entity)
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
            elif type == 'pdbx':
                compound = molio.readPDBX(file, not 'ptype' in molDict)
            elif type == 'sdf' or type == 'mol':
                compound = molio.readSDF(file)
            elif type == 'mol2':
                compound = molio.readMol2(file)
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

    def findRNAHelices(self, rnaDict):
        if 'vienna' in rnaDict:
            self.findHelices(rnaDict['vienna'])
            self.vienna = rnaDict['vienna']

    def readRNADict(self, data):
        rnaDict = data['rna']
        if 'vienna' in rnaDict:
            self.vienna = rnaDict['vienna']
        if 'ribose' in rnaDict:
            if not 'tree' in data and rnaDict['ribose'] == "Constrain":
                polymers = self.molecule.getPolymers()
                for polymer in polymers:
                    self.addRiboseRestraints(polymer)
        if 'suite' in rnaDict:
            self.addSuiteAngles(rnaDict['suite'])
        if 'planarity' in rnaDict:
            self.addPlanarity = rnaDict['planarity']
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
                    self.addDistanceConstraint(atomName1,atomName2,lower,upper,True)
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
                    atom = self.molecule.getAtomByName(atomName)
                    fullAtoms.append(atom)

                scale = 1.0
                try:
                    self.addAngleConstraintAtoms(fullAtoms, lower, upper, scale)
                except:
                    print "Error adding angle constraint",fullAtoms
                    pass

    def setupBMRBNEFMolecule(self, fileName, nefMode):
        self.molecule = NEFSTARStructureCalculator.setup(fileName, nefMode)
        self.dihedral = self.molecule.getDihedrals()
        self.energyLists = self.dihedral.energyList

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
                        molName = 'A'
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
               molName = 'A' #Teddy
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
               self.addAngleConstraintAtomNames(fullAtoms, lower, upper, scale)
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
                if line.startswith("#"):
                    continue
                (residueNum, rotamerName) = line.split()
                if rotamerName == "..":
                    continue
                angleBoundaries = RNARotamer.getAngleBoundaries(polymer, residueNum, rotamerName, mul)
                for angleBoundary in angleBoundaries:
                    self.addAngleConstraint(angleBoundary)
            fIn.close()

    def addSuiteBoundary(self,polymer, residueNum,rotamerName, mul=0.5):
        angleBoundaries = RNARotamer.getAngleBoundaries(polymer, str(residueNum), rotamerName, mul)
        for angleBoundary in angleBoundaries:
            self.addAngleConstraint(angleBoundary)

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
                        self.addDistanceConstraint(atomNameI, atomNameJ, 1.8, 5.0)


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
                    self.addDistanceConstraint(atomNameI, atomNameI3, 16.5, 20.0)
                resJ3 = strandJ[i+3]
                if (resJ3.getAtom("P") != None) and (resJ3.getAtom("P") != None):
                    atomNameJ = self.getAtomName(resJ,"P")
                    atomNameJ3 = self.getAtomName(resJ3,"P")
                    self.addDistanceConstraint(atomNameJ, atomNameJ3, 16.5, 20.0)
            if ((i+5) < nRes):
                resJ5 = strandJ[i+5]
                if (resI.getAtom("P") != None) and (resJ5.getAtom("P") != None):
                    atomNameI = self.getAtomName(resI,"P")
                    atomNameJ5 = self.getAtomName(resJ5,"P")
                    self.addDistanceConstraint(atomNameI, atomNameJ5, 10, 12.0)

    def findSSLinks(self):
        links = []
        bonds = []
        for ss in self.ssGen.structures():
            if ss.getName() == "Helix":
                residues = ss.getResidues()
                strandI = residues[0::2]
                strandJ = residues[1::2]

                resI = strandI[0]
                resJ = strandJ[0]
                if resI.getPrevious():
                    atomI = resI.getAtom("H3'")
                    atomJ = resJ.getAtom("P")
                    link = {'atoms':[atomI.getShortName(), atomJ.getShortName()],'rna':''}
                    links.append(link)
                    
                    bond = {'atoms':[atomJ.getParent().getShortName(), atomJ.getShortName()],'mode':'float'}
                    bonds.append(bond)

                resI = strandI[-1]
                resJ = strandJ[-1]
                atomI = resI.getAtom("H3'")
                atomJ = resJ.getAtom("P")
                link = {'atoms':[atomI.getShortName(), atomJ.getShortName()],'rna':''}
                links.append(link)
                bond = {'atoms':[atomJ.getParent().getShortName(), atomJ.getShortName()],'mode':'float'}
                bonds.append(bond)
        return links, bonds
       

    def findHelices(self,vienna):
        self.ssGen = SSGen(self.molecule, vienna)
        self.ssGen.analyze()

    def addHelicesRestraints(self, vienna):
        for ss in self.ssGen.structures():
            if ss.getName() == "Helix":
                residues = ss.getResidues()
                self.addHelix(residues)
                self.addHelixPP(residues)

        polymers = self.molecule.getPolymers()
        allResidues = []
        for polymer in polymers:
            allResidues += polymer.getResidues()
        pat = re.compile('\(\(\.\.\.\.\)\)')
        for m in pat.finditer(vienna):
            start = m.start()+1
            tetraLoopSeq = ""
            tetraLoopRes = []
            for iRes in range(start,start+6):
                residue = allResidues[iRes]
                tetraLoopSeq += residue.getName()
                tetraLoopRes.append(residue.getNumber())

            matched_cluster = getCluster(tetraLoopSeq)

            if matched_cluster:            
                loopResidues = [allResidues[start+i] for i in range(6)]
                if loopResidues[1].getPolymer() == loopResidues[-1].getPolymer():
                    for i, suite in enumerate(matched_cluster.suites):
                        if suite != "..":
                            res = loopResidues[i+1]
                            self.addSuiteBoundary(polymer, res.getNumber(), suite)

                bps = matched_cluster.bp
                for bp in bps:
                    (resNum1, resNum2) = bp.split(':')
                    res1 = loopResidues[int(resNum1) - 1]
                    res2 = loopResidues[int(resNum2) - 1]
                    self.addBasePair(res1, res2)

    def restart(self):
        print 'restart'
        self.energyLists = None
        self.molecule.invalidateAtomTree()
        self.molecule.setupRotGroups()
        self.molecule.setupAngles()
        eCoords = self.molecule.getEnergyCoords()
        if eCoords != None:
            eCoords.resetFixed()
        self.setup('./',self.seed)
        self.energyLists.setupConstraints()
        self.rDyn = self.rinertia()
        self.rDyn.setKinEScale(self.dOpt['kinEScale'])

    def lockRNALinkers(self, state):
        mol = self.molecule
        atomList = mol.atoms
        for atom in atomList:
            atom.setLinkerRotationActive(state)
        self.molecule.setupRotGroups()
        self.molecule.setupAngles()

    def lockRNAHelices(self, lockLast=False):
        ssGen = SSGen(self.molecule, self.vienna)
        ssGen.analyze()
        for ss in ssGen.structures():
            if ss.getName() == "Helix":
                helixResidues = ss.getResidues()
                strandI = helixResidues[0::2]
                strandJ = helixResidues[1::2]
                nRes = len(strandI)
                for i in range(nRes):
                    resI = strandI[i]
                    resJ = strandJ[i]
                    if i == nRes-1:
                        if lockLast:
                            RNARotamer.setDihedrals(resI,'1a', 0.0, True)
                            RNARotamer.setDihedrals(resJ,'1a', 0.0, True)
                    else:
                        RNARotamer.setDihedrals(resI,'1a', 0.0, True)
                        RNARotamer.setDihedrals(resJ,'1a', 0.0, True)
              
                    self.restart()
                    stages = getAnnealStages(self.dOpt, self.settings,'refine')
                    runStage(stages['stage_refine'], self, self.rDyn)

    def refineLockedRNA(self, nsteps=4000, dev1=3.0, dev2=4.0, skipEnd=True):
        linkAtoms = RNARotamer.getLinkAtoms(self.molecule, skipEnd)
        cmaes = CmaesRefinement(self.dihedral, linkAtoms)
        stopFitness=0.0
        radius=0.01
        alg="cmaes"
        ninterp=1.2
        lambdaMul=1
        diagOnly=0.2
        useDegrees=False
        diagOnly = int(round(nsteps*diagOnly))
        cmaes.refineCMAESWithLinkedAtoms(nsteps,stopFitness,radius,lambdaMul,diagOnly,useDegrees,dev1,dev2)

    def addBasePair(self, residueI, residueJ, type=1):
        resNameI = residueI.getName()
        resNameJ = residueJ.getName()
        resNumI = residueI.getNumber()
        resNumJ = residueJ.getNumber()
        basePairs = AllBasePairs.getBasePair(type, resNameI, resNameJ)
        if (basePairs == None):
            return
        for bp in basePairs.getBPConstraints():
            lowAtomAtomDis = bp.getLower()
            atomAtomDis = bp.getUpper()
            lowAtomParentDis = bp.getLowerHeavy()
            atomParentDis = bp.getUpperHeavy()
            atom1Names = []
            atom2Names = []
            allAtomNames =  bp.getAtomNames()
            for atomNames in allAtomNames:
                atom1Name = self.getAtomName(residueI, atomNames[0])
                atom2Name = self.getAtomName(residueJ, atomNames[1])
                atom1Names.append(atom1Name)
                atom2Names.append(atom2Name)
            atomI = allAtomNames[0][0]
            atomJ = allAtomNames[0][1]
            if atomI.startswith("H"):
                parentAtom = residueI.getAtom(atomI).parent.getName()
                parentAtomName = self.getAtomName(residueI,parentAtom)
                self.addDistanceConstraint(parentAtomName, atom2Names[0] ,lowAtomParentDis,atomParentDis)
            elif atomJ.startswith("H"):
                parentAtom = residueJ.getAtom(atomJ).parent.getName()
                parentAtomName = self.getAtomName(residueJ,parentAtom)
                self.addDistanceConstraint(parentAtomName, atom1Names[0] ,lowAtomParentDis,atomParentDis)
            self.addDistanceConstraint(atom1Names, atom2Names ,lowAtomAtomDis,atomAtomDis)
        if type == 1:
            atomPI = residueI.getAtom("P")
            atomPJ = residueJ.getAtom("P")
            if (atomPI != None) and (atomPJ != None):
                atomPIName = self.getAtomName(residueI, "P")
                atomPJName = self.getAtomName(residueJ, "P")
                self.addDistanceConstraint(atomPIName, atomPJName ,14.0, 20.0)
            if addPlanarity:
                bpRes = resNameI+resNameJ
                if bpRes in rnaBPPlanarity:
                    planeValues = rnaBPPlanarity[bpRes]
                    for (aNameI,aNameJ,dis) in planeValues:
                        atomIName = self.getAtomName(residueI, aNameI)
                        atomJName = self.getAtomName(residueJ, aNameJ)
                        self.addDistanceConstraint(atomIName, atomJName ,0.0, dis)



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
        typeAtomPairs = [AllBasePairs.getBasePair(int(typee), residueI.getName(), residueJ.getName()).atomPairs for typee in types]
        restraints =  [AllBasePairs.getBasePair(int(typee), residueI.getName(), residueJ.getName()).distances for typee in types]
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
                self.addDistanceConstraint(atomList1, atomList2, min(distances),max(distances))
                self.addDistanceConstraint(parentAtomList1, parentAtomList2, min(parentDistances),max(parentDistances))
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
                self.addDistanceConstraint(atomList1, atomList2, min(distances),max(distances))
                self.addDistanceConstraint(parentAtomList1, parentAtomList2, min(parentDistances),max(parentDistances))

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
            self.addDistanceConstraint(atomNameI,atomNameJ,lowerIntra,intra)

        stacks = stackTo[resNameJ]
        for stack in stacks:
            aNameI, aNameJ,intra,inter = stack
            lowerInter = inter - 1.0
            atomNameI = polyI.getName()+':'+resNumI+'.'+aNameI
            atomNameJ = polyJ.getName()+':'+resNumJ+'.'+aNameJ
            self.addDistanceConstraint(atomNameI,atomNameJ,lowerInter,inter)

        pairName = resNameI+resNameJ
        if not pairName in stackPairs:
            print("No data for pair " + pairName)
        else:
            pairs = stackPairs[pairName]
            for pair in pairs:
                aNameI, aNameJ,lower,upper = pair
                atomNameI = polyI.getName()+':'+resNumI+'.'+aNameI
                atomNameJ = polyJ.getName()+':'+resNumJ+'.'+aNameJ
                self.addDistanceConstraint(atomNameI,atomNameJ,lower,upper)

    def measureTree(self):
        for entity in [entity for entity in self.molecule.getEntities()]:
            if type(entity) is Polymer:
                prfStartAtom = self.getEntityTreeStartAtom(entity)
                treeStartAtom = self.entityEntryDict[entity]
                if prfStartAtom == treeStartAtom:
                    if len(self.molecule.getEntities()) > 1:
                        continue
                else:
                    ### To remeasure, coordinates should be generated for the entity ###
                    AngleTreeGenerator.genCoordinates(entity, prfStartAtom)
            self.setupAtomProperties(entity)
            if entity in self.entityEntryDict:
                atom = self.entityEntryDict[entity]
                AngleTreeGenerator.genMeasuredTree(entity, atom)

    def dumpProperties(self, nodeValidator):
        atoms = self.molecule.getAtoms()
        props = nodeValidator.dumpProps()
        print props[0]
        for atom,line in zip(atoms,props[1:]):
            print atom,line


    def setupAtomProperties(self, compound):
        pI = PathIterator(compound)
        nodeValidator = NodeValidator()
        pI.init(nodeValidator)
        pI.processPatterns()
        pI.setProperties("ar", "AROMATIC");
        pI.setProperties("res", "RESONANT");
        pI.setProperties("namide", "AMIDE");
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
            if isinstance(start,Atom):
                startAtom = start
            else:
                startAtom = self.molecule.getAtomByName(start)
        else:
            startAtom = None
        if end:
            if isinstance(end,Atom):
                endAtom = end
            else:
                endAtom = self.molecule.getAtomByName(end)
        else:
            endAtom = None
        #MoleculeBase.makeAtomList()
        mol = self.molecule
        mol.resetGenCoords()
        mol.invalidateAtomArray()
        mol.invalidateAtomTree()
        atree = AngleTreeGenerator()
        aTreeVals = atree.genTree(mol,startAtom, endAtom)
        atree.dumpAtomTree(aTreeVals)
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
            xplorFile.readXPLORAngleConstraints(self.molecule, resNames)
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
                self.addDistanceConstraint(atomNames1, atomNames2, lower, upper)
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
                rdcObj = RDCConstraint(rdcSet, spSets1[0], spSets1[1], rdc, err)
                rdcSet.remove(newAtom1Ind);
                rdcSet.add(newAtom1Ind, rdcObj);
            else:
                atom1 = Molecule.getAtomByName(atomName1)
                atom2 = Molecule.getAtomByName(atomName2)
                if atom1 != None and atom2 != None:
                    spSet1 = atom1.getSpatialSet()
                    spSet2 = atom2.getSpatialSet()
                    rdcObj = RDCConstraint(rdcSet, spSet1, spSet2, rdc, err)
                    rdcSet.add(rdcObj)

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
                    predictor.predictRNAWithDistances(polymer, 0, -1, False)
                else:
                    predictor.predictRNAWithRingCurrent(polymer, 0, -1)

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
        self.addRingClosures() # Broken bonds are stored in molecule after tree generation. This is to fix broken bonds  # fixme should not happen with rna riboses as they are added
        self.setForces({'repel':0.5,'dis':1,'dih':5})
        self.setPars({'coarse':False,'useh':False,'dislim':self.disLim,'end':1000,'hardSphere':0.15,'shrinkValue':0.20})
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
                    self.addDistanceConstraint(atomName1, atomName2, ringClosures[atom1][atom2]-.01, ringClosures[atom1][atom2]+.01, True)


    def prepare(self,steps=1000, gsteps=300, alg='cmaes'):
        ranfact=20.0
        self.setSeed(self.seed)
        self.putPseudo(18.0,45.0)
        self.randomizeAngles()
        energy = self.energy()
        self.setForces({'repel':0.5,'dis':1,'dih':5})
        self.setPars({'useh':False,'dislim':self.disLim,'end':2,'hardSphere':0.0,'shrinkValue':0.20,'updateAt':5})
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
        self.randomizeAngles() # Angles randomized here
        energy = self.energy()
        forceDict = self.settings.get('force')
        irp = forceDict.get('irp', 0.015) if forceDict else 0.015

        self.setForces({'repel':0.5,'dis':1,'dih':5,'irp':irp})
        forceString = self.getForces()
        print "FORCES " + forceString

        for end in [3,10,20,1000]:
            self.setPars({'useh':False,'dislim':self.disLim,'end':end,'hardSphere':0.15,'shrinkValue':0.20,'updateAt':5})
            parString = self.getPars()
            print "PARS   " + parString
            self.gmin(nsteps=steps,tolerance=1.0e-6)
        if self.eFileRoot != None and self.reportDump:
            self.dump(-1.0,-1.0,self.eFileRoot+'_prep.txt')

    def init(self,dOpt=None,save=True):
        from anneal import runStage
        from anneal import getAnnealStages
        dOpt = dOpt if dOpt else dynOptions()
        self.mode = 'refine'

        self.rDyn = self.rinertia()
        self.rDyn.setKinEScale(dOpt['kinEScale'])
        energy = self.energy()
        print 'start energy is', energy

        self.prepAngles()
        if save:
            self.output()

    def refine(self,dOpt=None,mode='refine'):
        from anneal import runStage
        from anneal import getAnnealStages
        dOpt = dOpt if dOpt else dynOptions()
        self.restart()
        self.mode = mode
        if mode == 'cff':
            dOpt['cffSteps'] = 1000
        self.rDyn = self.rinertia()
        self.rDyn.setKinEScale(dOpt['kinEScale'])
        energy = self.energy()
        print('start energy is', energy)

        stages = getAnnealStages(dOpt, self.settings,mode)
        for stageName in stages:
            stage = stages[stageName]
            runStage(stage, self, self.rDyn)

        self.gmin(nsteps=dOpt['polishSteps'],tolerance=1.0e-6)
        if dOpt['dfreeSteps']> 0:
            self.refineNonDeriv(nsteps=dOpt['dfreeSteps'],radius=20, alg=dOpt['dfreeAlg']);
        ec = self.molecule.getEnergyCoords()
        #ec.exportConstraintPairs('constraints.txt')

    def anneal(self,dOpt=None,stage1={},stage2={}):
        from anneal import runStage
        from anneal import getAnnealStages
        dOpt = dOpt if dOpt else dynOptions()

        self.rDyn = self.rinertia()
        self.rDyn.setKinEScale(dOpt['kinEScale'])

        stages = getAnnealStages(dOpt, self.settings)
        for stageName in stages:
            print stageName
            stage = stages[stageName]
            runStage(stage, self, self.rDyn)

        self.gmin(nsteps=dOpt['polishSteps'],tolerance=1.0e-6)
        if dOpt['dfreeSteps']> 0:
            self.refineNonDeriv(nsteps=dOpt['dfreeSteps'],radius=20, alg=dOpt['dfreeAlg']);
        ec = self.molecule.getEnergyCoords()
        #ec.exportConstraintPairs('constraints.txt')

    def cdynamics(self, steps, hiTemp, medTemp, timeStep=1.0e-3):
        self.updateAt(20)
        self.setForces({'repel':5.0,'dis':1.0,'dih':5})
        self.setPars({'coarse':True, 'end':1000,'useh':False,'hardSphere':0.15,'shrinkValue':0.20})
        self.rDyn = self.rinertia()
        steps0 =  5000
        steps1 = (steps-steps0)/3
        steps2 = steps-steps0-steps1

        self.rDyn.initDynamics(hiTemp,hiTemp,steps0,timeStep)
        self.rDyn.run(1.0)

        timeStep = self.rDyn.getTimeStep()/2.0
        self.rDyn.continueDynamics(hiTemp,medTemp,steps1,timeStep)
        self.rDyn.run(1.0)

        timeStep = self.rDyn.getTimeStep()/2.0
        self.rDyn.continueDynamics(medTemp,2.0,steps2,timeStep)
        self.rDyn.run(0.65)

        self.setPars({'useh':True,'shrinkValue':0.05,'shrinkHValue':0.05})

        timeStep = self.rDyn.getTimeStep()/2.0
        self.rDyn.continueDynamics(timeStep)
        self.rDyn.run(0.35)


        if self.eFileRoot != None:
            self.dump(0.1,self.eFileRoot+'_dyn.txt')


    def dynrun(self, steps, temp, timeStep=1.0e-3, timePower=4.0, stage1={}):
        self.updateAt(20)
        self.setForces({'repel':0.5,'dis':1.0,'dih':5})
        self.setPars({'end':1000,'useh':False,'hardSphere':0.15,'shrinkValue':0.20})
        self.setPars(stage1)
        self.rDyn = self.rinertia()
        self.rDyn.initDynamics(temp,temp,steps,timeStep, timePower)
        self.rDyn.run(1.0)

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
