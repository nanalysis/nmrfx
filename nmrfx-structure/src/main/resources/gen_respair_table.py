from mscript import *;
import molio;
import os
from org.nmrfx.structure.chemistry import InteractionType
from org.nmrfx.structure.chemistry import SecondaryStructure
from org.nmrfx.structure.chemistry import SSGen
from collections import OrderedDict
import itertools as itools

def getPdbFiles(fileName):
    with open(fileName, 'r') as f:
        lines = f.readlines()
        for line in lines:
            line = line.strip()
            if line == '':
                continue
            splitLine = line.split(' ')
            pdbName, viennaSeq = splitLine if len(splitLine) == 2 else ("","")
            if pdbName.startswith("#"):
                continue
            yield ("./pdb/" + pdbName +  ".pdb", viennaSeq) # ex: ('2KOC.pdb', '((((....))))'

def getStats(dis):
    stats= [] #get average of distances for the lists
    minimum = round(min(dis), 2)
    maximum= round(max(dis), 2)
    avg= sum(dis)/ float(len(dis))
    average = round(avg, 2)
    stats.append(minimum)
    stats.append(maximum)
    stats.append(average)
    stats.append(len(dis))
    return stats

def loopClass(rn, pairs):
    right, left, counter = rn+1, rn-1, 1
    while (pairs.get(right) is not None or pairs.get(left) is not None):
        if (pairs.get(right) == -1):
            right +=1
        elif (pairs.get(left) == -1):
            left -= 1
        else:
            break
        counter += 1
    if counter == 4:
        return "T" # tetra (4 residues)
    elif counter > 4:
        return "L" # larger (4+ residues)
    elif counter < 4 and counter > 1:
        return "S" # smaller (1> residues < 4)
    else:
        return "B" # bulge (1 residue)

def tetraloopLabel(resInd, basePairMap): #also contain bulge
    tetra= []
    i=1
    for rNum in resInd.keys():
    #take in output from inloop function
    #assign T1,T2,T3 and T4
    #get all tetra loop interactions
        loopLabel = loopClass(resInd[rNum], basePairMap) if basePairMap.get(resInd[rNum]) == -1 else None
        if (loopLabel == "T"):
            tetra.append("T"+str(i))
            i+=1
            if (i==5):
                i=1
        elif (loopLabel == "B"):
            tetra.append("B")
        else: tetra.append("")
    return tetra

def fetchPDBFile(pdbPath):
   if os.path.exists(pdbPath) and os.path.isfile(pdbPath):
       return pdbPath
   else:
       import urllib2
       pdbID = os.path.split(pdbPath)[-1].split('.')[0]
       try:
           response = urllib2.urlopen('http://www.rcsb.org/pdb/files/'+pdbID+'.pdb')
           pdbFileInfo = response.read()
       except:
           raise AssertionError("Failed to get pdb file. Check file path -> '{}'".format(pdbID))
       with open(pdbPath, 'w') as saveFile:
           saveFile.write(pdbFileInfo)
       return pdbPath

# Main executor
def mainFunc():

    allTypes = {
                   "SRH": {},
                   "SRB": {},
                   "SRT": {},
                   "SRL": {},
                   "ADJ": {},
                   "BP": {},
                   "OABP": {},
                   "TA": {},
                   "T12": {},
		   "T13": {},
		   "T14": {},
                   "T23": {},
		   "T24": {},
                   "T34": {},
                   "TH": {},
		   "HT": {},
		   "BH": {},
		   "HB": {},
		   "BB": {},
		   "SH": {},
		   "HS": {},
		   "LH": {},
		   "HL": {},
                   "L1": {},
                   "L2": {},
                   "L3": {},
                   "S1": {},
                   "S2": {},

               }

    distanceThres= 6
    used = 0
    actuallyRead = 0
    seenPDBs = set()
    seenCases = set()
    for pdbFilePath, viennaSeq in getPdbFiles('trainfiles2.txt'):
        actuallyRead += 1
        print "PDB file path: ", pdbFilePath
        pdbFile = fetchPDBFile(pdbFilePath)
        mol = molio.readPDB(pdbFile)
        ssGen = SSGen(mol, viennaSeq)
        ssGen.analyze()
 	rnaResidues = [residue for polymer in mol.getPolymers() if polymer.isRNA() for residue in polymer.getResidues()]
        
        for resCombination in itools.combinations_with_replacement(rnaResidues, 2):
            res1, res2 = resCombination
            res1Atoms, res2Atoms = res1.getAtoms("H*"), res2.getAtoms("H*")
            seenAtoms = set() if res1.equals(res2) else None # ensure atom comparison in the same residue is unidirectional
            iType = InteractionType.determineType(res1, res2)
            if iType is None: # skipping
                continue
            for atomCombination in itools.product(res1Atoms, res2Atoms):
                atom1, atom2 = atomCombination
                if seenAtoms is not None:
                    if (atom2, atom1) in seenAtoms:
                        continue
                    else:
                        seenAtoms.add(atomCombination)
                pt1 = atom1.getPoint()
                pt2 = atom2.getPoint()
                dis = Atom.calcDistance(pt1, pt2)
                if (dis <= distanceThres and dis != 0): # create a variable for the numbers
                    atomName1= str(atom1.getName())
                    atomName2= str(atom2.getName())
                    if ( ("O" not in atomName1) and ("O" not in atomName2)):
			nuc1 = str(res1.getName())
                        nuc2 = str(res2.getName())
                        inst = (iType, nuc1, nuc2, atomName1, atomName2)
			if iType in allTypes:
                            typeDict = allTypes[iType]
                            if inst in typeDict:
                                typeDict[inst].append(dis)
                            else:
                                typeDict[inst] = [dis]
                        else:
                            raise KeyError("Key '{}' is not recognized in '{}'.".format(iType, allTypes.keys()))
        used += 1
    with open("output_table.txt", "wb") as wTable:
        wTable.write("Number of PDB files used : {}\n".format(str(used)))
        wTable.write("{:5s}\t{:4s}\t{:4s}\t{:5s}\t{:5s}\t{:4s}\t{:4s}\t{:4s}\t{:5s}\n".format("Type","Res1","Res2","Atom1","Atom2","Min.","Max.","Avg.","# Inst."))
        for types in allTypes.values():
            for key, values in types.items():
                stats = getStats(values)
                tableContent = key + tuple(stats)
                output = "{:5s}\t{:4s}\t{:4s}\t{:5s}\t{:5s}\t{:4.2f}\t{:4.2f}\t{:4.2f}\t{:5d}\n".format(*tableContent)
                wTable.write(output)

mainFunc()
