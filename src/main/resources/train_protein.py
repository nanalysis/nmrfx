import smile.validation.Validation;
import smile.validation.CrossValidation;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.regression.DataFrameRegression;
import smile.regression.LASSO;

from org.nmrfx.processor.optimization import SMILECrossValidator
from org.nmrfx.structure.chemistry.energy import PropertyGenerator
from org.nmrfx.structure.chemistry.predict import ProteinPredictor
from org.nmrfx.structure.chemistry import Molecule
from smile.regression import LASSO
from smile.validation import Validation
from smile.validation import CrossValidation
from smile.validation import RMSE
from smile.data import DataFrame
from smile.data.formula import Formula
from org.apache.commons.math3.linear import Array2DRowRealMatrix
from org.nmrfx.structure.chemistry import SmithWatermanBioJava
from org.nmrfx.project import StructureProject
from java.util.function import BiFunction
import array
import molio
import star
import os
import os.path
import glob
import math
import sys
import re
import gzip
import shutil
from itertools import izip
import argparse

proteinPredictor = None

#set directories
bmrbHome = '/data/star3/bmr'
pdbHome = '/data/pdb/'

#local directory for unzipped pdb files
pdbDir = '../pdb/'

#set paths
homeDir = '../output/080620'
datasetFileName = 'train_and_test_dataset_clean.txt'
testSetFile = 'testSet.txt'

useContacts = True
reportAtom = None
reportAtom = "1:1.CA"

#select which group of atom types to generate attributes for
atoms = ["MHB", "MHG", "MHD", "MHE", "MCB", "MCG", "MCD","MCE", "C", "CA", "CB", "CG", "CD", "CE", "CZ", "H", "HA", "HB", "HG", "HD", "HE", "HZ", "N"]
aaS = ['ALA', 'ARG', 'ASN', 'ASP', 'CYS', 'GLU', 'GLN', 'GLY', 'HIS', 'ILE', 'LEU', 'LYS', 'MET', 'PHE', 'PRO', 'SER', 'THR', 'TRP', 'TYR', 'VAL']

attrDir = 'baseAttrs'
aaGroups = {}

aaGroups['C'] = [['ASP', 'ASN'], ['GLU', 'GLN'], ['HIS', 'TRP', 'PHE', 'TYR'], ['ALA'], ['PRO'], ['GLY'], ['ILE', 'VAL', 'LEU'], ['THR'], ['CYS', 'SER'], ['ARG', 'LYS'], ['MET']]
aaGroups['CA'] = [['ASP', 'ASN'], ['GLU', 'GLN'], ['HIS', 'TRP', 'PHE', 'TYR'], ['ALA'], ['PRO'], ['GLY'], ['ILE'], ['VAL'], ['LEU'], ['THR'], ['CYS', 'SER', 'MET'], ['ARG', 'LYS']]
aaGroups['CB'] = [['ASP', 'ASN'], ['GLU', 'GLN'], ['HIS', 'TRP', 'PHE', 'TYR'],  ['PRO'], ['ILE'], ['VAL'], ['LEU'], ['THR'], ['CYS'], ['SER'], ['MET'], ['ARG', 'LYS']]
aaGroups['CD'] = [['HIS', 'TRP', 'PHE', 'TYR'], ['LEU'], ['LYS'],['ARG'], ['PRO']]
aaGroups['CE'] = [['PHE', 'TYR'],['HIS'],['LYS']]
aaGroups['CZ'] = [['PHE', 'TYR']]
aaGroups['CG'] = [['GLU'], ['GLN'], ['VAL'], ['LEU'], ['ILE'], ['PRO'], ['ARG'], ['LYS'], ['MET']]
aaGroups['H'] = [['ALA', 'GLY'], ['ASP'], ['ASN'], ['GLU'], ['GLN'], ['HIS'], ['TRP'], ['PHE'], ['TYR'], ['ILE'], ['VAL'], ['LEU'], ['THR'], ['SER'], ['CYS'], ['ARG'], ['LYS'], ['MET']]
aaGroups['HA'] = [['ALA', 'GLY'], ['ASP'], ['ASN'], ['GLU'], ['GLN'], ['HIS'], ['TRP'], ['PHE'], ['TYR'], ['ILE'], ['VAL'], ['LEU'], ['THR'], ['SER'], ['CYS'], ['ARG'], ['LYS'], ['MET'], ['PRO']]
aaGroups['HB'] = [['ASP'], ['ASN'], ['GLU'], ['GLN'], ['HIS'], ['TRP'], ['PHE'], ['TYR'], ['ALA'], ['ILE'], ['VAL'], ['LEU'], ['THR'], ['SER'], ['CYS'], ['ARG'], ['LYS'], ['MET'], ['PRO']]
aaGroups['HD'] = [['ARG'],  ['PRO'], ['HIS', 'PHE', 'TYR'], ['LEU'], ['LYS']]
aaGroups['HE'] = [['ARG'], ['LYS'],  ['HIS'], ['PHE', 'TYR']]
aaGroups['HG'] = [['GLU'], ['GLN'], ['VAL'], ['LEU'], ['ILE'], ['PRO'], ['ARG'], ['LYS'], ['MET']]
aaGroups['HZ'] = [['PHE']]
aaGroups['MCB'] = [['ALA']]
aaGroups['MCD'] = [['ILE'], ['LEU']]
aaGroups['MCG'] = [['ILE'], ['THR'], ['VAL']]
aaGroups['MHB'] = [['ALA']]
aaGroups['MHD'] = [['ILE'], ['LEU']]
aaGroups['MHG'] = [['ILE'], ['THR'], ['VAL']]
aaGroups['MHE'] = [['MET']]
aaGroups['MCE'] = [['MET']]
aaGroups['N'] = [['ALA'], ['ARG'], ['ASN'], ['ASP'], ['CYS'], ['GLN'], ['GLU'], ['GLY'], ['HIS'], ['ILE'], ['LEU'], ['LYS'], ['MET'], ['PHE'], ['SER'], ['THR'], ['TRP'], ['TYR'], ['VAL']]



#min and max values used for disorder calculation
minMaxSums = {'N':[97.37,752.63],
    'CA':[191.66,1550.0],
    'CB':[209.09,1254.55],
    'C':[800.0,1100.0],
    'H':[483.33,1788.89],
    'HA':[431.58,3178.95],
    'HB':[500.0,2000.0],
    'HG':[500.0,2000.0],
    'HD':[500.0,2000.0],
    'HE':[500.0,2000.0],
    'CG':[500.0,2000.0],
    'CD':[500.0,2000.0],
    'CE':[500.0,2000.0],
    'MCB':[500.0,2000.0],
    'MCG':[500.0,2000.0],
    'MCD':[500.0,2000.0],
    'MCE':[500.0,2000.0],
    'MHB':[500.0,2000.0],
    'MHG':[500.0,2000.0],
    'MHD':[500.0,2000.0],
    'MHE':[500.0,2000.0],
    'CZ':[500.0,2000.0],
    'HZ':[500.0,2000.0]}

#used in generating attr files
iLine = 0

#find gz file
def findPDB(pdbID, localPdbFile):
    zippedFile = os.path.join(pdbHome, pdbID[1:3], 'pdb' + pdbID + '.ent.gz')
    if(os.path.exists(zippedFile)):
        with gzip.open(zippedFile,'rb') as fin:
            with open(localPdbFile, 'w') as fout:
                shutil.copyfileobj(fin,fout)
    else:
        return False
    return True

#checks if pdb exists and download if it doesn't
def pdbExists(pdbID):
    localPdbFile = os.path.join(pdbDir, pdbID + '.pdb')
    if(not os.path.exists(localPdbFile)):
        print 'pdb not in local directory'
        if findPDB(pdbID, localPdbFile):
            print 'pdb fetched'
            return True
        else:
            try:
                response = urllib2.urlopen('http://www.rcsb.org/pdb/files/' + pdbID + '.pdb')
                pdbEntry = response.read()
            except:
                print 'could not fetch pdb'
                return False
            with open(localPdbFile, 'w') as tmp_file:
                    tmp_file.write(pdbEntry)
    return True

def getPDBFile(pdbID):
    if pdbExists(pdbID):
        return os.path.join(pdbDir, pdbID + '.pdb')
    return None

def getSaveFrame(svfs,catName):
    for svfName in svfs:
       svf = svfs[svfName]
       if svf.getCategoryName() == catName:
           return svf
    return None

#star.read() will not return error if path/filename does not exist
def loadBMRB(bmrbId):
    fileName = os.path.join(bmrbHome + bmrbId, 'bmr' + bmrbId + '_3.str')
    if not os.path.exists(fileName):
        print 'cannot locate star file, check path', fileName
    star3 = star.read(fileName)
    svfs = star3.getSaveFrames()
    svf = getSaveFrame(svfs,'assembly')
    paramagnetic = svf.getCategory('_Assembly').get('Paramagnetic')
    if paramagnetic == "no":
        mol = Molecule.getActive()
    else:
        mol = None
    if mol != None:
        svf = getSaveFrame(svfs,'entry_information')
        subType = svf.getCategory('_Entry').get('Experimental_method_subtype')
        subType = subType.lower()
        if 'solid' in subType:
            mol = None
        elif 'state' in subType:
            mol = None
        elif 'magic' in subType:
            mol = None
        elif 'theor' in subType:
            mol = None
    return mol

def getUseFields2(fields, aName, aa, residues):
    use = []
    skipChi2 = False
    if aa in ['ALA','GLY']:
        skipChi2 = True
    for (i,field) in enumerate(fields):
        ok = True
        if skipChi2 and field.find('chi2C') != -1:
            ok = False
        if (not 'CYS' in residues)  and field == "DIS":
            ok = False
        if not (aName.startswith("H") or aName.startswith("M")) and field == "h3":
            ok = False
        if field in aaS:
            if not field in residues[1:]:
                ok = False
        if ok:
            use.append(i)
    return use

def disorderCalc(contactSum, minS, maxS):
    if not useContacts:
        return 1.0
    sValue = -2.0 * (contactSum - minS) / (maxS - minS)
    eValue = math.exp(sValue)
    return (1.0 - eValue) / (1.0 + eValue)

def filterAttr(fileName, outFileName, use, aName, aa, residues, errDict, trimDict):
    global iLine
    global homeDir
    writeHeader = False
    if not os.path.exists(outFileName):
        writeHeader = True
    use = None
    dir, file = os.path.split(fileName)
    mapFileName = os.path.join(homeDir, "maps", file)
    print fileName
    with open(outFileName, 'a') as fOut, open(mapFileName, 'a') as fOut2:
        with open(fileName, 'r') as f1:
            header = f1.readline().strip()
            header = header.strip()
            headerFields = header.split('\t')

            for line in f1:
               line = line.strip()
               if len(line) > 0:
                   fields = line.split('\t')
                   pdbID = fields[0]
                   atomName = fields[2]
                   #check whether atom should be trimmed
                   if trimDict and [pdbID, atomName] in trimDict[aName]:
                      continue
                   v = PropVar2(headerFields, fields)
                   if (v.N1 > 0.1):
                       continue
                   if (v.C1 > 0.1):
                       continue
                   if errDict:
                       v.cs -= float(errDict[pdbID])
                   a = getAttr(v, aa)
                   fields = []
                   names = []
                   for (name,value) in zip(a[0::2],a[1::2]):
                       fields.append(str(value))
                       names.append(str(name))
                   if use == None:
                       use = getUseFields2(names, aName, aa, residues)
                   if writeHeader:
                       firstHead = True
                       for i in use:
                           if not firstHead:
                               fOut.write('\t')
                           else:
                               firstHead = False
                           fOut.write(names[i])
                       fOut.write('\n')
                       writeHeader = False
                   first = 1
                   for i in use:
                       if not first:
                           fOut.write('\t')
                       else:
                           first = 0
                       fOut.write(fields[i])
                   fOut.write('\n')
                   fOut2.write('{}\t{}\n'.format(pdbID, atomName))
                   iLine += 1

def writeHeader(fileName, a):
    with open(fileName,"w") as f1:
        first = 1
        for (name,value) in zip(a[0::2],a[1::2]):
            if not first:
                f1.write(',')
            else:
                first = 0
            f1.write(name)
        f1.write('\n')

def writeBaseHeader(fileName, a):
    with open(fileName,"w") as f1:
        f1.write('pdb\tres\tatom')
        for (name,value) in zip(a[0::2],a[1::2]):
            f1.write('\t')
            f1.write(name)
        f1.write('\n')

def saveBaseAttr(pdbID, atom, mode, attrDir, rootAName, resName, a):
    global homeDir
    fileName = os.path.join(homeDir, attrDir, mode + "_" + rootAName + "_" + resName + ".txt")
    if not os.path.exists(fileName):
        writeBaseHeader(fileName, a)
    with open(fileName, 'a') as f1:
        f1.write(pdbID + '\t' + resName + '\t' + atom.getShortName())
        for (name, value) in zip(a[0::2], a[1::2]):
            f1.write('\t')
            if value == None:
                f1.write('nan')
            elif math.isnan(value):
                f1.write('nan')
            else:
                svalue = "%.5f" % (value)
                f1.write(svalue)
        f1.write('\n')

def isRes(resName, resTest):
    if resName == resTest:
        return 1.0
    else:
        return 0.0

def safeSin(v):
    if math.isnan(v):
        return 0.0
    else:
        return math.sin(v)

def safeCos(v):
    if math.isnan(v):
        return 0.0
    else:
        return math.cos(v)

def getAttr(v, resName):
    a = []
    if useContacts:
        a += ['ring',v.ring]
        a += ['hshift3',v.hshift3]
        a += ['hshift2',v.hshift2]
        a += ['hshift1',v.hshift1]
        a += ['eshift',v.eshift]

    a += ['cos(chiC)',safeCos(v.chiC)]
    a += ['cos(chi2C)',safeCos(v.chi2C)]
    a += ['cos(2*chiC)',safeCos(2*v.chiC)]
    a += ['cos(2*chi2C)',safeCos(2*v.chi2C)]
    a += ['sin(chiC)',safeSin(v.chiC)]
    a += ['sin(chi2C)',safeSin(v.chi2C)]
    a += ['sin(2*chiC)',safeSin(2*v.chiC)]
    a += ['sin(2*chi2C)',safeSin(2*v.chi2C)]

    a += ['sin(chiC)*sin(chi2C)',safeSin(v.chiC)*safeSin(v.chi2C)]
    a += ['cos(chiC)*sin(chi2C)',safeCos(v.chiC)*safeSin(v.chi2C)]
    a += ['sin(chiC)*cos(chi2C)',safeSin(v.chiC)*safeCos(v.chi2C)]
    a += ['cos(chiC)*cos(chi2C)',safeCos(v.chiC)*safeCos(v.chi2C)]

    a += ['sin(phiC)*cos(chiC)',safeSin(v.phiC)*safeCos(v.chiC)]
    a += ['sin(phiC)*cos(chi2C)',safeSin(v.phiC)*safeCos(v.chi2C)]
    a += ['sin(phiC)*sin(chiC)',safeSin(v.phiC)*safeSin(v.chiC)]
    a += ['sin(phiC)*sin(chi2C)',safeSin(v.phiC)*safeSin(v.chi2C)]
    a += ['cos(phiC)*sin(chiC)',safeCos(v.phiC)*safeSin(v.chiC)]
    a += ['cos(phiC)*sin(chi2C)',safeCos(v.phiC)*safeSin(v.chi2C)]
    a += ['cos(phiC)*cos(chiC)',safeCos(v.phiC)*safeCos(v.chiC)]
    a += ['cos(phiC)*cos(chi2C)',safeCos(v.phiC)*safeCos(v.chi2C)]

    a += ['sin(psiC)*cos(chiC)',safeSin(v.psiC)*safeCos(v.chiC)]
    a += ['sin(psiC)*cos(chi2C)',safeSin(v.psiC)*safeCos(v.chi2C)]
    a += ['sin(psiC)*sin(chiC)',safeSin(v.psiC)*safeSin(v.chiC)]
    a += ['sin(psiC)*sin(chi2C)',safeSin(v.psiC)*safeSin(v.chi2C)]
    a += ['cos(psiC)*sin(chiC)',safeCos(v.psiC)*safeSin(v.chiC)]
    a += ['cos(psiC)*sin(chi2C)',safeCos(v.psiC)*safeSin(v.chi2C)]
    a += ['cos(psiC)*cos(chiC)',safeCos(v.psiC)*safeCos(v.chiC)]
    a += ['cos(psiC)*cos(chi2C)',safeCos(v.psiC)*safeCos(v.chi2C)]

    a += ['cos(phiC)',safeCos(v.phiC)]
    a += ['sin(phiC)',safeSin(v.phiC)]
    a += ['cos(2*phiC)',safeCos(2*v.phiC)]
    a += ['sin(2*phiC)',safeSin(2*v.phiC)]
    a += ['cos(3*phiC)',safeCos(3*v.phiC)]
    a += ['sin(3*phiC)',safeSin(3*v.phiC)]
    a += ['cos(psiC)',safeCos(v.psiC)]
    a += ['sin(psiC)',safeSin(v.psiC)]
    a += ['cos(2*psiC)',safeCos(2*v.psiC)]
    a += ['sin(2*psiC)',safeSin(2*v.psiC)]
    a += ['cos(3*psiC)',safeCos(3*v.psiC)]
    a += ['sin(3*psiC)',safeSin(3*v.psiC)]

    a += ['cos(psiC)*cos(phiC)',safeCos(v.psiC)*safeCos(v.phiC)]
    a += ['cos(psiC)*sin(phiC)',safeCos(v.psiC)*safeSin(v.phiC)]
    a += ['sin(psiC)*cos(phiC)',safeSin(v.psiC)*safeCos(v.phiC)]
    a += ['sin(psiC)*sin(phiC)',safeSin(v.psiC)*safeSin(v.phiC)]
#######output/02 all
#output/03
    a += ['cos(psiC)*cos(psiP)',safeCos(v.psiC)*safeCos(v.psiP)]
    a += ['cos(psiC)*sin(psiP)',safeCos(v.psiC)*safeSin(v.psiP)]
    a += ['sin(psiC)*cos(psiP)',safeSin(v.psiC)*safeCos(v.psiP)]
    a += ['sin(psiC)*sin(psiP)',safeSin(v.psiC)*safeSin(v.psiP)]
#output/05
    #a += ['cos(phiC)*cos(phiS)',safeCos(v.phiC)*safeCos(v.phiS)]
    #a += ['cos(phiC)*sin(phiS)',safeCos(v.phiC)*safeSin(v.phiS)]
    #a += ['sin(phiC)*cos(phiS)',safeSin(v.phiC)*safeCos(v.phiS)]
    #a += ['sin(phiC)*sin(phiS)',safeSin(v.phiC)*safeSin(v.phiS)]
#output/04
    #a += ['cos(psiC)*cos(phiS)',safeCos(v.psiC)*safeCos(v.phiS)]
    #a += ['cos(psiC)*sin(phiS)',safeCos(v.psiC)*safeSin(v.phiS)]
    #a += ['sin(psiC)*cos(phiS)',safeSin(v.psiC)*safeCos(v.phiS)]
    #a += ['sin(psiC)*sin(phiS)',safeSin(v.psiC)*safeSin(v.phiS)]
#output/06
    #a += ['cos(chiC)*cos(psiP)',safeCos(v.chiC)*safeCos(v.psiP)]
    #a += ['cos(chiC)*sin(psiP)',safeCos(v.chiC)*safeSin(v.psiP)]
    #a += ['sin(chiC)*cos(psiP)',safeSin(v.chiC)*safeCos(v.psiP)]
    #a += ['sin(chiC)*sin(psiP)',safeSin(v.chiC)*safeSin(v.psiP)]
#output/07
    #a += ['cos(chiC)*ARO_P',safeCos(v.chiC)*v.ARO_P]
    #a += ['cos(chiC)*BULK_P',safeCos(v.chiC)*v.BULK_P]
    #a += ['cos(chiC)*CHRG_P',safeCos(v.chiC)*v.CHRG_P]
    #a += ['cos(chiC)*HPHB_P',safeCos(v.chiC)*v.HPHB_P]
    #a += ['cos(chiC)*PRO_P',safeCos(v.chiC)*v.PRO_P]
    #a += ['sin(chiC)*ARO_P',safeSin(v.chiC)*v.ARO_P]
    #a += ['sin(chiC)*BULK_P',safeSin(v.chiC)*v.BULK_P]
    #a += ['sin(chiC)*CHRG_P',safeSin(v.chiC)*v.CHRG_P]
    #a += ['sin(chiC)*HPHB_P',safeSin(v.chiC)*v.HPHB_P]
    #a += ['sin(chiC)*PRO_P',safeSin(v.chiC)*v.PRO_P]
######
    a += ['cos(phiC)*ARO_P',safeCos(v.phiC)*v.ARO_P]
    a += ['cos(phiC)*BULK_P',safeCos(v.phiC)*v.BULK_P]
    a += ['cos(phiC)*CHRG_P',safeCos(v.phiC)*v.CHRG_P]
    a += ['cos(phiC)*HPHB_P',safeCos(v.phiC)*v.HPHB_P]
    a += ['cos(phiC)*PRO_P',safeCos(v.phiC)*v.PRO_P]
    a += ['sin(phiC)*ARO_P',safeSin(v.phiC)*v.ARO_P]
    a += ['sin(phiC)*BULK_P',safeSin(v.phiC)*v.BULK_P]
    a += ['sin(phiC)*CHRG_P',safeSin(v.phiC)*v.CHRG_P]
    a += ['sin(phiC)*HPHB_P',safeSin(v.phiC)*v.HPHB_P]
    a += ['sin(phiC)*PRO_P',safeSin(v.phiC)*v.PRO_P]

    a += ['cos(psiC)*ARO_S',safeCos(v.psiC)*v.ARO_S]
    a += ['cos(psiC)*BULK_S',safeCos(v.psiC)*v.BULK_S]
    a += ['cos(psiC)*CHRG_S',safeCos(v.psiC)*v.CHRG_S]
    a += ['cos(psiC)*HPHB_S',safeCos(v.psiC)*v.HPHB_S]
    a += ['cos(psiC)*PRO_S',safeCos(v.psiC)*v.PRO_S]
    a += ['sin(psiC)*ARO_S',safeSin(v.psiC)*v.ARO_S]
    a += ['sin(psiC)*BULK_S',safeSin(v.psiC)*v.BULK_S]
    a += ['sin(psiC)*CHRG_S',safeSin(v.psiC)*v.CHRG_S]
    a += ['sin(psiC)*HPHB_S',safeSin(v.psiC)*v.HPHB_S]
    a += ['sin(psiC)*PRO_S',safeSin(v.psiC)*v.PRO_S]

    a += ['cos(phiS)',safeCos(v.phiS)]
    a += ['sin(phiS)',safeSin(v.phiS)]
    a += ['cos(2*phiS)',safeCos(2*v.phiS)]
    a += ['sin(2*phiS)',safeSin(2*v.phiS)]
    a += ['cos(3*phiS)',safeCos(3*v.phiS)]
    a += ['sin(3*phiS)',safeSin(3*v.phiS)]
    a += ['cos(psiS)',safeCos(v.psiS)]
    a += ['sin(psiS)',safeSin(v.psiS)]
    a += ['cos(2*psiS)',safeCos(2*v.psiS)]
    a += ['sin(2*psiS)',safeSin(2*v.psiS)]
    a += ['cos(3*psiS)',safeCos(3*v.psiS)]
    a += ['sin(3*psiS)',safeSin(3*v.psiS)]

    a += ['cos(phiP)',safeCos(v.phiP)]
    a += ['sin(phiP)',safeSin(v.phiP)]
    a += ['cos(2*phiP)',safeCos(2*v.phiP)]
    a += ['sin(2*phiP)',safeSin(2*v.phiP)]
    a += ['cos(3*phiP)',safeCos(3*v.phiP)]
    a += ['sin(3*phiP)',safeSin(3*v.phiP)]
    a += ['cos(psiP)',safeCos(v.psiP)]
    a += ['sin(psiP)',safeSin(v.psiP)]
    a += ['cos(2*psiP)',safeCos(2*v.psiP)]
    a += ['sin(2*psiP)',safeSin(2*v.psiP)]
    a += ['cos(3*psiP)',safeCos(3*v.psiP)]
    a += ['sin(3*psiP)',safeSin(3*v.psiP)]

    #a += ['cos(omega)',safeCos(v.omega)]
    #a += ['sin(omega)',safeSin(v.omega)]
    a += ['DIS',v.DIS]
    for aa in aaS:
        a += [aa,isRes(resName,aa)]
    a += ['h3',v.h3]
    #a += ['methyl',v.methyl]
    #a += ['fRandom',v.fRandom]
    #a += ['acs',v.acs]
    #a += ['acscorr',v.acscorr]
    a += ['cs',v.cs]
    a += ['contacts',v.contacts]
    return a

def getBaseAttr(values, resName, proP, proS):
    a = []
    for key in values:
        a += [key, values[key]]
    for aa in aaS:
        a += [aa, isRes(resName, aa)]
    a += ['PRO', isRes(resName, 'PRO')]
    a += ['PRO_P',  proP]
    a += ['PRO_S',  proS]
    return a

class PropVar:
    def __init__(self, values, resName, proP, proS):
        for key in values:
            self.__dict__[key] = values[key]
        self.__dict__['PRO'] = isRes(resName,'PRO')
        self.__dict__['PRO_P'] = proP
        self.__dict__['PRO_S'] = proS

class PropVar2:
    def __init__(self, header, values):
        for key,value in zip(header, values):
            if key != "pdb" and key != "atom" and key != "res":
                value = float(value)
            self.__dict__[key] = value

def isStdRes(polymer):
    standardResList = ['A', 'G', 'L', 'I', 'P', 'V', 'F', 'W', 'Y', 'D', 'E', 'R', 'H', 'K', 'S', 'T', 'C', 'M', 'N', 'Q']
    for letter in polymer.getOneLetterCode():
        if str(letter) not in standardResList:
            return False
    return True

def alignPair(pdbMol, bmrbMol):
    highestSeqIdentity = 0.85
    highestNumIdentical = None
    bestPdbPolymer = None
    bestBmrbPolymer = None
    alignment = None
    for bmrbPolymer in bmrbMol.getPolymers():
        if not isStdRes(bmrbPolymer) or bmrbPolymer is None:
            print 'non std res in bmrb'
            continue
        for pdbPolymer in pdbMol.getPolymers() or pdbPolymer is None:
            print pdbPolymer.getOneLetterCode
            if not isStdRes(pdbPolymer):
                print 'non std res in pdb', pdbPolymer.getOneLetterCode()
                continue
            print 'pdb polymer name: ', pdbPolymer.getName()
            print 'pdb polymer seq: ', pdbPolymer.getOneLetterCode()
            print 'bmrb polymer name: ', bmrbPolymer.getName()
            print 'bmrb polymer seq: ', bmrbPolymer.getOneLetterCode()
            sw = SmithWatermanBioJava(pdbPolymer.getOneLetterCode(), bmrbPolymer.getOneLetterCode())
            swp = sw.doAlignment()
            numIdentical = float(swp.getNumIdenticals())
            seqIdentity = numIdentical / min(len(pdbPolymer.getOneLetterCode()), len(bmrbPolymer.getOneLetterCode()))
            if seqIdentity >= highestSeqIdentity and numIdentical > highestNumIdentical:
                highestSeqIdentity = seqIdentity
                highestNumIdentical = numIdentical
                bestPdbPolymer = pdbPolymer.getName()
                bestBmrbPolymer = bmrbPolymer.getName()
                alignment = sw.getA()

    print 'seq identity', highestSeqIdentity, 'pdb polymer:', bestPdbPolymer, 'bmrb polymer:', bestBmrbPolymer
    return bestPdbPolymer, bestBmrbPolymer, alignment

def setChemShifts(pdbMol, bmrbMol, refSet='R'):
    pdbChain, bmrbChain, alignIndex = alignPair(pdbMol, bmrbMol)
    if pdbChain is None or bmrbChain is None:
        return None, None
    pdbResidues = pdbMol.getEntity(pdbChain).getResidues()
    bmrbResidues = bmrbMol.getEntity(bmrbChain).getResidues()
    for pdbResidue, index in izip(pdbResidues, alignIndex):
        if index is None:
            continue
        bmrbResidue = bmrbResidues[index - 1]
        pdbResName = pdbResidue.getName()
        bmrbResName = bmrbResidue.getName()
        if pdbResName == bmrbResName:
            for bmrbAtom in bmrbResidue.getAtoms():
                aName = bmrbAtom.getName()
                ppm = bmrbAtom.getPPM()
                pdbResNum = pdbResidue.getNumber()
                if ppm is None:
                    continue
                pdbAtomName = pdbChain + ':' + str(pdbResNum) + '.' + aName
                pdbAtom = pdbMol.findAtom(pdbAtomName)
                if pdbAtom is None:
                    continue
                if refSet == 'R':
                    pdbAtom.setRefPPM(ppm)
                else:
                    pdbAtom.setPPM(refSet, ppm)
    return pdbChain, bmrbChain

def getAtomNameType(atom):
    result = None
    aName = atom.getName()
    aLen = len(aName)
    if atom.isMethyl():
        if atom.getAtomicNumber() == 1:
            if atom.isFirstInMethyl():
                result = "MH" + aName[1]
    elif atom.isMethylCarbon():
            result = "MC"+aName[1]
    else:
       if aLen > 2:
          aName = aName[0:2]
       if aName in atoms:
           result = aName
    if not result in atoms:
        result = None
    return result

#loads molecule object and generates attr file
def doProtein(mol, chain, pg):
    print 'do Protein'
    results = {}
    polymer = mol.getEntity(chain)
    residues = polymer.getResidues()
    mol.setActive()
    for residue in residues:
        res = int(residue.getNumber())
        resName = residue.getName()
        proP = 0
        proS = 0
        if residue.previous != None:
            if residue.previous.getName() == "PRO":
                proP = 1
        if residue.next != None:
            if residue.next.getName() == "PRO":
                proS = 1
        if not pg.getResidueProperties(polymer, residue):
            print "skip", res, resName
            continue
        resAtoms = residue.getAtoms()
        for atom in resAtoms:
            atomName = atom.getName()
            aType = getAtomNameType(atom)
            if aType != None:
                if not pg.getAtomProperties(polymer, res, resName, atomName):
                    print "skip", res, resName, atomName
                    continue

                values = pg.getValues()
                v = PropVar(values, resName, proP, proS)
                if v.cs > -90.0 and abs(v.cs) > 0.001:
                    baseAttr = getBaseAttr(values, resName, proP, proS)
                    results[atom] = baseAttr
    return results

#loads molecule object and generates attr file
def doProteinOn(mol, chain, pg):
    print 'do Protein'
    results = {}
    polymer = mol.getEntity(chain)
    residues = polymer.getResidues()
    mol.setActive()
    for residue in residues:
        res = int(residue.getNumber())
        resName = residue.getName()
        proP = 0
        proS = 0
        if residue.previous != None:
            if residue.previous.getName() == "PRO":
                proP = 1
        if residue.next != None:
            if residue.next.getName() == "PRO":
                proS = 1
        if not pg.getResidueProperties(polymer, residue):
            print "skip", res, resName
            continue
        for rootAName in atoms:
            atomNames = [rootAName]
            if rootAName == "HA":
                if resName == "GLY":
                    atomNames = ["HA2","HA3"]
            elif len(rootAName) > 1:
                resAtoms = residue.getAtoms()
                atomNames = []
                for resAtom in resAtoms:
                    if resAtom.getName().startswith(rootAName):
                       if resAtom.isMethyl():
                           if resAtom.isFirstInMethyl():
                               atomNames.append(resAtom.getName())
                       else:
                           atomNames.append(resAtom.getName())
            for atomName in atomNames:
                atom = residue.getAtom(atomName)
                if atom == None:
                    continue

                if not pg.getAtomProperties(polymer, res, resName, atomName):
                    continue

                values = pg.getValues()
                v = PropVar(values, resName, proP, proS)
                print resName, atomName, v.cs
                if v.cs > -90.0 and abs(v.cs) > 0.001:
                    baseAttr = getBaseAttr(values, resName, proP, proS)
                    results[atom] = baseAttr
    return results

def saveAllBaseAttrs(pdbId, mode, attrsDict):
    for atom, baseAttr in attrsDict.items():
        rootAName = atom.getName()
        resName = atom.getEntity().getName()
        aType = getAtomNameType(atom)
        if aType is None:
            continue
        saveBaseAttr(pdbId, atom, mode, attrDir, aType, resName, baseAttr)

def setShifts(pdbMol, bmrbId, refSet='R'):
    for atom in pdbMol.getAtoms():
        if refSet == 'R':
            atom.setRefPPM(0.0)
        else:
            atom.setPPM(refSet, 0.0)

    bmrbMol = loadBMRB(bmrbId)
    if bmrbMol == None:
        return None, None

    pdbChain, bmrbChain = setChemShifts(pdbMol, bmrbMol, refSet)
    return pdbChain, bmrbChain

def doAtoms(mol, chain):
    attrsDict = {}
    pg = PropertyGenerator()
    try:
        pg.init(mol)
        atomDict = doProteinOn(mol, chain, pg)
    except:
        return None
    attrsDict.update(atomDict)
    return attrsDict

def genAttrsFromList(fileName):
    mode = 'sngl'

    with open(fileName, 'r') as f1:
        for line in f1:
            line = line.split('\t')
            pdbId = line[0].strip()
            bmrbId = line[1].strip()
            print pdbId, bmrbId
            Molecule.removeAll()
            if pdbExists(pdbId) is False:
                print 'cannot locate pdb', pdbId
                continue
            pdbFile = getPDBFile(pdbId)
            pdbMol = molio.readPDB(pdbFile)
            pdbChain, bmrbChain = setShifts(pdbMol, bmrbId)
            if pdbChain and bmrbChain:
                print pdbChain, bmrbChain
                pdbMol.setActive()
                molDict = doAtoms(pdbMol, pdbChain)
                if molDict is None:
                    saveError(pdbId, bmrbId)
                    continue
                saveAllBaseAttrs(pdbId, mode, molDict)
            else:
                saveError(pdbId, bmrbId)

def saveError(pdbId, bmrbId):
    with open('errorEntries.txt', 'a') as f:
        f.write(pdbId + '\t' + bmrbId + '\n')

def getPredictAttrs(resName, baseAttr):
    headerFields = baseAttr[0::2]
    fields = baseAttr[1::2]
    fields = [field if ((field is not None )) else field == 0.0 for field in fields]
    v = PropVar2(headerFields,fields)
    if (v.N1 > 0.1):
        pass
    if (v.C1 > 0.1):
        pass
    a = getAttr(v, resName)
    return a

#text files required: coefficients and trimmed atoms
def assessPdbs(fileName):
    global homeDir
    rms_limits = {"C" : 1.158, "CA" : 1.011, "CB" : 1.130, "N" : 2.725, "H" : 0.451,
                  "HA" : 0.267, "HB" : 0.239, "HG" : 0.194, "HD" : 0.25, "HE" : 0.25,
                   "CG" : 0.927, "CD" : 1.2, "CE" :1.2}

    rmsFile = 'all_pdb_rms.txt'
    newFile = 'quality_assessed_nmr_entries.txt'

    with open(fileName, 'r') as f1, open(rmsFile, 'w') as f2, open(newFile, 'w') as f3:
        for line in f1:
            line = line.split()
            pdbId = line[0]
            bmrbId = line[1]
            nullCount = 0
            count = 0
            print pdbId, bmrbId
            rms = assessPdbInternal(pdbId, bmrbId)
            rmsValues = rms[::2]
            rmsString = ''.join(['\t{:20}'.format(rmsValue) for rmsValue in rmsValues])
            for atom, value in zip(atoms, rmsValues):
                if isinstance(value, str):
                    nullCount += 1
                    continue
                if value > rms_limits[atom]:
                    count += 1
                    break
            f2.write('{}\t{}{}\n'.format(pdbId, bmrbId, rmsString))
            if nullCount < len(rms_limits) and count == 0:
                f3.write('{}\t{}\n'.format(pdbId, bmrbId))
    exit(0)

def assessPdbInternal(pdbId, bmrbId):
    global proteinPredictor
    if proteinPredictor == None:
        proteinPredictor = ProteinPredictor()

    Molecule.removeAll()
    pdbFile = getPDBFile(pdbId)
    pdbMol = molio.readPDB(pdbFile)
    pdbChain, bmrbChain = setShifts(pdbMol, bmrbId, 0)
    proteinPredictor.init(pdbMol)
    proteinPredictor.setReportAtom(reportAtom)
    proteinPredictor.predict(-1)
    rerefPdbInternal(pdbMol)

    molDict = doAtoms(pdbMol, pdbChain)
    dictOfCoefDict = readCoefs()
    predShiftDict = getPredShifts(molDict, dictOfCoefDict)

    pAtoms = pdbMol.getAtoms()
    rmsDict = {}
    for pAtom in pAtoms:
        ppmPred = pAtom.getRefPPM()
        ppmBMRB = pAtom.getPPM(0)
        if (ppmPred != None and ppmPred != 0.0 and ppmBMRB != None and  ppmBMRB.isValid()) and ppmBMRB.getValue() != 0.0:
            atomtype = getAtomType(pAtom)
            if not atomtype in atoms:
                continue
            predShift = ppmPred
            expShift = ppmBMRB.getValue()
            predShiftCoef = 'XX'
            delta2 = 1000.0
            if pAtom in predShiftDict:
                predShiftCoef = predShiftDict[pAtom]
                delta2 = predShiftCoef - predShift
            delta = expShift - predShift

            print pAtom.getFullName(),atomtype,predShiftCoef,predShift,expShift,round(delta,3),round(delta2,3)
            if doStdTrim(delta, atomtype) is True:
                continue
            sumOfSquares = math.pow((predShift-expShift),2)
            if atomtype not in rmsDict.keys():
                rmsDict[atomtype] = [sumOfSquares,1.0]
            else:
                rmsDict[atomtype][0] += sumOfSquares
                rmsDict[atomtype][1] += 1.0
    pdbRmsDict = {atomtype : [math.sqrt(values[0] / values[1]), values[1]] for atomtype, values in rmsDict.items()}
    rms = []
    for atom in atoms:
        if atom in pdbRmsDict.keys():
            rms.append(pdbRmsDict[atom][0])
            rms.append(pdbRmsDict[atom][1])
        else:
            rms.append('N/A')
            rms.append('N/A')
    return rms

def getAtomType(atom):
    atomtype = atom.getName()
    if len(atomtype) > 2:
        atomtype = atomtype[:2]
    return atomtype

def doStdTrim(delta, atomType):
    limitsStd = {'C':5.0,'CA':5.0,'CB':5.0,'CG':5.0,'CD':5.0,'CE':5.0,'CZ':5.0,
                 'H':2.0,'HA':0.7,'HB':2.0,'HG':2.0,'HD':2.0,'HE':2.0,'HZ':2.0,
                 'MCB':5.0,'MCG':5.0,'MCD':5.0,'MCE':5.0,
                 'MHB':2.0,'MHG':2.0,'MHD':2.0,'MHE':2.0,
                 'N':10}
    if abs(delta) > limitsStd[atomType]:
        return True
    return False

def getPredShifts(molDict, dictOfCoefDict):
    pdbDict = {}
    for atom, baseAttr in molDict.items():
        atomtype = getAtomType(atom)
        atomName = atom.getShortName()
        resName = atom.getEntity().getName()
        keyName = atomtype + '_' + resName
        if keyName not in dictOfCoefDict.keys():
            continue
        coefDict = dictOfCoefDict[keyName]
        a = getPredictAttrs(resName, baseAttr)
        if atom.getFullName() == reportAtom:
            print baseAttr
            print a
        headerFields = a[0::2]
        fields = a[1::2]
        contacts = fields[-2]
        (minContact, maxContact) = minMaxSums[atomtype]
        disorder = disorderCalc(contacts, minContact, maxContact)
        intercept = float(coefDict['intercept'])
        predShift = intercept
        for headerField, field in zip(headerFields, fields):
            try:
                coef = coefDict[headerField]
                contribution = float(coef) * float(field) * disorder
                predShift += contribution
                if atom.getFullName() == reportAtom:
                    print headerField,coef,float(field),contribution,intercept,predShift
            except KeyError:
                continue
        expShift = atom.getRefPPM()
        if expShift <= 0.0:
            continue
        delta = expShift - predShift
        if doStdTrim(delta, atomtype) is True:
            continue
        pdbDict[atom] = predShift
    return pdbDict

def rerefPdbInternal(pdbMol):
    print 'rerefPdbInternal'
    errDict = {}
    pAtoms = pdbMol.getAtoms()
    for pAtom in pAtoms:
        ppmPred = pAtom.getRefPPM()
        ppmBMRB = pAtom.getPPM(0)
        if (ppmPred != None and ppmPred != 0.0 and ppmBMRB != None and  ppmBMRB.isValid()) and ppmBMRB.getValue() != 0.0:
            atomType = getAtomType(pAtom)
            predShift = ppmPred
            expShift = ppmBMRB.getValue()
            err = expShift - predShift
            if atomType not in errDict.keys():
                errDict[atomType] = [err, 1.0]
            else:
                errDict[atomType][0] += err
                errDict[atomType][1] += 1.0
    rerefDict = {atomType:values[0]/values[1] for atomType, values in errDict.items()}
    for pAtom in pAtoms:
        ppmPred = pAtom.getRefPPM()
        ppmBMRB = pAtom.getPPM(0)
        if (ppmPred != None and ppmPred != 0.0 and ppmBMRB != None and  ppmBMRB.isValid()) and ppmBMRB.getValue() != 0.0:
            atomType = getAtomType(pAtom)
            adj = rerefDict[atomType]
            pAtom.setPPM(0, pAtom.getPPM(0).getValue() - adj)

def calcRms(corrExpShiftDict, predShiftDict):
    rmsDict = {}
    for atom, expShift in corrExpShiftDict.items():
        atomtype = getAtomType(atom)
        predShift = predShiftDict[atom]
        delta = expShift - predShift
        if doStdTrim(delta, atomtype) is True:
            continue
        sumOfSquares = math.pow((predShift-expShift),2)
        if atomtype not in rmsDict.keys():
            rmsDict[atomtype] = [sumOfSquares,1.0]
        else:
            rmsDict[atomtype][0] += sumOfSquares
            rmsDict[atomtype][1] += 1.0
    pdbRmsDict = {atomtype : [math.sqrt(values[0] / values[1]), values[1]] for atomtype, values in rmsDict.items()}
    return pdbRmsDict

#checks that pdb and star files of the dataset entries can be read
def filterEntries(pdbIds, bmrbIds):
    skippedEntriesFileName = os.path.join('skippedEntries.txt')
    filteredEntriesFileName = os.path.join('validEntries.txt')

    with open(skippedEntriesFileName, 'w') as skippedEntriesFile, open(filteredEntriesFileName, 'w') as cleanFile:
        for pdbId, bmrbId in zip(pdbIds, bmrbIds):
            Molecule.removeAll()
            pdbEntry = getPDBFile(pdbId)
            pdbReadable = True
            bmrbReadable = True
            try:
                molio.readPDBX(pdbEntry)
            except:
                print 'cannot read pdb', pdbId
                pdbReadable = False
                skippedEntriesFile.write(pdbId + '\n')

            try:
                loadBMRB(bmrbId)
                print 'bmrb entry loaded'
            except:
                print 'cannot read bmrb', bmrbId
                bmrbReadable = False
                skippedEntriesFile.write(bmrbId + '\n')

            if pdbReadable is True and bmrbReadable is True:
                print 'writing to file', pdbId, bmrbId
                cleanFile.write('{}\t{}\n'.format(pdbId, bmrbId))
    exit(0)

def combine(trimDict=None, reRef=False):
    global homeDir
    cmode = 'all'
    for mode in ['sngl']:
        for aName in atoms:
            print "atoms", atoms, "aaGroups", aaGroups
            for aaGroup in aaGroups[aName]:
                groupName = '_'.join(aaGroup)
                outFile = os.path.join(homeDir, 'attrs', cmode + '_' + aName + '_' + groupName + '.txt')
                mapFilePath = os.path.join(homeDir, 'maps', mode + '_' + aName + '_*.txt')
                mapFiles = glob.glob(mapFilePath)
                if os.path.exists(outFile):
                    os.remove(outFile)
                for mapFile in mapFiles:
                    if os.path.exists(mapFile):
                        print 'removing: ', mapFile
                        os.remove(mapFile)
    for mode in ['sngl']:
        for aName in atoms:
            for aaGroup in aaGroups[aName]:
                groupName = '_'.join(aaGroup)
                outFile = os.path.join(homeDir, 'attrs', cmode + '_' + aName + '_' + groupName + '.txt')
                use = None
                for aa in aaGroup:
                    fileName = os.path.join(homeDir, attrDir, mode + '_' + aName + '_' + aa + '.txt')
                    if not os.path.exists(fileName):
                        continue
                    print fileName
                    if reRef is True:
                        print 'Rereferencing'
                        errDict = getCorr(aName)
                    else:
                        errDict = {}
                    filterAttr(fileName, outFile, use, aName, aa, aaGroup, errDict, trimDict)

def scaleMatrix(matrix, contacts, minSum, maxSum):
    rows = len(matrix)
    cols = len(matrix[0])
    newMat = Array2DRowRealMatrix(rows,cols)
    for row in range(rows):
        scale = disorderCalc(contacts[row], minSum, maxSum)
        for col in range(cols):
            if col == cols - 1:
                newMat.setEntry(row, col, matrix[row][col])
            else:
                newMat.setEntry(row, col, matrix[row][col] * scale)
    return newMat.getData()

def readMap(fileName):
    global homeDir
    map = []
    dir, tail = os.path.split(fileName)
    tail = tail.split('.')[0].split('_')
    atom = tail[1]
    residues = tail[2:]
    for residue in residues:
        mapFileName = os.path.join(homeDir, 'maps', 'sngl_' + atom + '_' + residue + '.txt')
        with open(mapFileName,'r') as mapFile:
            for mapline in mapFile:
                mapline = mapline.strip('\n')
                pdbID, atomName = mapline.split('\t')
                map.append((pdbID, atomName))
    return map

def getMatrix(fileName, map, testMode=False):
    if testMode:
        testpdbs = getTestSet()
    with open(fileName, 'r') as attrFile:
        header = attrFile.readline()
        headerFields = header.strip().split('\t')
        trainMatrix = []
        traincValues = []
        testMatrix = []
        testcValues = []
        for attrline, mapItem in zip(attrFile, map):
            pdb, atom = mapItem
            attrline = attrline.strip()
            fields = attrline.split('\t')
            if len(fields) != len(headerFields):
                print fileName
                print line
                exit(0)
            values = [float(v) for v in fields]
            contacts = values[-1]
            if testMode and pdb in testpdbs:
                xRow = array.array('d', values[:-1])
                testMatrix.append(xRow)
                testcValues.append(contacts)
            else:
                xRow = array.array('d', values[:-1])
                trainMatrix.append(xRow)
                traincValues.append(contacts)
        if testMode:
            return headerFields, trainMatrix, traincValues, testMatrix, testcValues
        else:
            return headerFields, trainMatrix, traincValues

def processMatrix(headerFields, matrix):
    dict = {}
    noAttr = []
    for header in headerFields:
        dict[header] = 0
    for row in matrix:
        for header, value in zip(headerFields, row):
            if value == 0.0 or value == -0.0:
                dict[header] += 1
    for header in headerFields:
        if dict[header] == len(matrix):
            noAttr.append(header)
    return noAttr

# generates matrix from combined attrs file, passes scaled matrix to lasso predictor
# maps pdbId and atomName to experimental shift and predicted shift
def fit(fileName, minSum, maxSum, testMode=False, coefsSave=False, optMinMax=False):
    map = readMap(fileName)

    if testMode:
        headerFields, trainMatrix, traincValues, testMatrix, testcValues = getMatrix(fileName, map, testMode)
    else:
        headerFields, trainMatrix, traincValues = getMatrix(fileName, map)

    dir, file = os.path.split(fileName)
    tail = '_'.join('{}'.format(i) for i in file.split('.')[0].split('_')[1:])

    if optMinMax:
        bestRMS = 1000.0
        for j in range(0, 10):
            minSum = 100 * j
            for k in range(j+1, 20):
                maxSum = 200 * k
                trainscaledMatrix = scaleMatrix(trainMatrix, traincValues, minSum, maxSum)
                ols = LASSO(trainscaledMatrix, trainyValues, 0.001)
                intercept = ols.intercept()
                rms = math.sqrt(ols.RSS() / len(trainyValues))
                if rms < bestRMS:
                    bestMin = minSum
                    bestMax = maxSum
                    bestRMS = rms
        minSum = bestMin
        maxSum = bestMax

    trainscaledMatrix = scaleMatrix(trainMatrix, traincValues, minSum, maxSum)
    dropHeaders = processMatrix(headerFields[:-1], trainscaledMatrix)
    trainDf = DataFrame.of(trainscaledMatrix, headerFields)
    trainDf = trainDf.drop(dropHeaders)
    lamVal = 0.005

    formula = Formula("cs")
    lassoModel = LASSO.fit(formula, trainDf, lamVal)


    rms = math.sqrt(lassoModel.RSS() / trainDf.nrows())
    smileCV = SMILECrossValidator(formula, trainDf, lamVal)
    xValidPreds = smileCV.cv(10)
    xrms = RMSE.of(trainDf.column("cs").array(), xValidPreds)

    if testMode:
        testscaledMatrix = scaleMatrix(testMatrix, testcValues, minSum, maxSum)
        testDf = DataFrame.of(testscaledMatrix, headerFields)
        testDf = testDf.drop(dropHeaders)
        testValidPreds = Validation.test(lassoModel, testDf)
        testrms = RMSE.of(testDf.column("cs").array(), testValidPreds)

    intercept = lassoModel.intercept()
    coefs = lassoModel.coefficients()
    cols = len(coefs)

    if testMode:
        yValues = testDf.column("cs").array()
        dataframe = testDf.toArray()
        resultsPath = os.path.join(homeDir,'testresults', file)
        print '{:20}\t{}\t{}\t{}'.format(tail, testrms, testDf.nrows(), xrms)
    else:
        yValues = trainDf.column("cs").array()
        dataframe = trainDf.toArray()
        resultsPath = os.path.join(homeDir, 'results', file)
        print '{:20}\t{}\t{}\t{}'.format(tail, rms, xrms, trainDf.nrows())

    with open(resultsPath, 'w') as fOut:
        for yValue, row,  descript in zip(yValues, dataframe, map):
            predValue = lassoModel.predict(row[:-1])
            delta = yValue - predValue
            fOut.write('{}\t{}\t{}\t{}\t{}\n'.format(yValue, predValue, delta, descript[0], descript[1]))
    if coefsSave:
        saveCoefs(tail, headerFields, coefs, cols, intercept)
    if testMode:
        return testrms, testDf.nrows(), xrms
    return rms, trainDf.nrows(), xrms

def saveCoefs(filename, headerFields, coefs, cols, intercept):
    coefDir = os.path.join(homeDir, 'coefs', filename+'.txt')
    with open(coefDir, 'w') as coefFile:
        coefFile.write('{}\t{}\t{}\n'.format(-1, 'intercept', intercept))
        for i in range(cols):
            coefFile.write('{}\t{}\t{}\n'.format(i, headerFields[i], coefs[i]))

#change coefFile path to path where files are located
def readCoefs():
    dictOfDict={}
    for atom in atoms:
        coefFile = os.path.join(homeDir, 'coefs', atom + '_' + '*.txt')
        fileNames = glob.glob(coefFile)
        for fileName in fileNames:
            coefDict = {}
            dir, file = os.path.split(fileName)
            file = file[:-4].split('_')
            resNames = file[1:]
            with open(fileName, 'r') as f1:
                for line in f1:
                    index, header, value = line.strip('\n').split('\t')
                    coefDict[header] = value
            for resName in resNames:
                keyName = atom + '_' + resName
                dictOfDict[keyName] = coefDict
    return dictOfDict

def getTestSet():
    testpdbs = []
    global testSetFile
    with open(testSetFile, 'r') as testSet:
        for line in testSet:
            pdbId, bmrbId = line.strip().split('\t')
            testpdbs.append(pdbId)
    return testpdbs

def fitAll(testMode=False, coefsSave=False, getRMS=False, optMinMax=False):
    fitFilename = os.path.join(homeDir, 'fitOutput.txt')
    rmsDict = {}
    with open(fitFilename, 'w') as fitFile:
        for atom in atoms:
            minSum, maxSum = minMaxSums[atom]
            sum = 0.0
            sumx = 0.0
            total = 0.0
            totalx = 0.0
            attrsPath = os.path.join(homeDir, 'attrs', 'all_' + atom + '_*.txt')
            files = glob.glob(attrsPath)
            for filename in files:
                rms, size, xrms = fit(filename, minSum, maxSum, testMode, coefsSave, optMinMax)
                sum += rms * rms * size
                if size > 100:
                    sumx += xrms  * xrms  * size
                    totalx += size
                total += size
            if totalx > 1:
                xrms = math.sqrt(sumx / totalx)
            else:
                xrms = 0.0
            rmsDict[atom] = math.sqrt(sum / total)
            print atom, rmsDict[atom]
            fitFile.write('{}\t{}\t{}\t{}\n'.format(atom, rmsDict[atom], xrms, totalx))
    if getRMS:
        return rmsDict

#calculates chemical shift correction using delta value
def calCorr(trimDict):
    global homeDir
    atomGroup = [["MHB"], ["MHG"], ["MHD"], ["MCB"], ["MCG"], ["MCD"], ['MCE'], ['MHE'], ["C"],
                 ["CA", "CB"], ["N"], ["H"], ["HA"], ["HB"], ["HG"], ["HD"], ["HZ"],
                 ["HE"], ["CG"], ["CD"], ["CE"], ["CZ"]]

    for atoms in atomGroup:
        refErrorDict = {}
        if len(atoms) > 1:
            fileName = 'CACB'
        else:
            fileName = atoms[0]
        refErrorFile = os.path.join(homeDir, 'refErrors', fileName + '.txt')
        for atom in atoms:
            resultsPath = os.path.join(homeDir, 'results', 'all_' + atom + '_*.txt')

            if os.path.exists(refErrorFile):
                previousErrDict = getCorr(atom)
            else:
                previousErrDict = {}

            files = glob.glob(resultsPath)
            for filename in files:
                print 'calculate ref error: ', atom, filename
                with open(filename,'r') as f:
                    for line in f:
                        exp, pred, delta, pdbId, atomName = line.strip('\n').split('\t')
                        if [pdbId, atomName] in trimDict[atom]:
                            continue
                        if pdbId not in refErrorDict.keys():
                            if previousErrDict:
                                prevErr = float(previousErrDict[pdbId])
                            else:
                                prevErr = 0.0
                            refErrorDict[pdbId] = [float(delta), 1, prevErr]
                        else:
                            refErrorDict[pdbId][0] += float(delta)
                            refErrorDict[pdbId][1] += 1

        with open(refErrorFile, 'w') as f1:
            for k, v in refErrorDict.items():
                error = v[0] / v[1]
                total = error + v[2]
                f1.write('{}\t{}\t{}\n'.format(k, error, total))
                #print 'pdbId', k, 'error', error, 'total', total

#reads average reference error per atom type per pdbId
def getCorr(aName):
    global homeDir
    if aName == 'CA' or aName == 'CB':
        aName = 'CACB'
    refErrorFile = os.path.join(homeDir, 'refErrors', aName + '.txt')
    errDict = {}
    if os.path.exists(refErrorFile):
        with open(refErrorFile, 'r') as f:
            for line in f:
                pdb, value, total = line.strip('\n').split('\t')
                #errDict[pdb] = value
                errDict[pdb] = total
    return errDict

#trims rows with delta(exp-pred) greater than set ppm limit
def bigTrim(limits=None, readOnly=False):
    limitsStd = {'C':5.0,'CA':5.0,'CB':5.0,'CG':5.0,'H':2.0,'HA':0.7,'HB':2.0,
                 'HG':2.0,'N':10, 'MCB':5.0,'MCD':5.0,'MCG':5.0,'MHG':2.0,
                 'MHD':2.0,'MHG':2.0,'MCE':5.0,'MHE':2.0,"CZ":5.0,"HZ":5.0}

    if limits == None:
        limits = limitsStd

    trimDict = {'C':[],'CA':[],'CB':[],'CG':[],'CD':[],'CE':[],
                'H':[],'HA':[],'HB':[],'HG':[],'HD':[],'HE':[],
                'N':[],'MHB':[],'MHG':[],'MHD':[],'MCB':[],'MCG':[],
                'MCD':[],'MCE':[],'MHE':[],"CZ":[],"HZ":[]}

    #read atoms from previous trim into dictionary
    trimPath = os.path.join(homeDir, 'trimmed.txt')
    if os.path.exists(trimPath):
        with open(trimPath, 'r') as trimFile:
            for line in trimFile:
                atom, pdbId, atomName = line.strip('\n').split('\t')
                trimDict[atom].append([pdbId, atomName])
    if readOnly:
        print 'read trim dict only'
        return trimDict

    #append atoms to be trimmed to dictionary
    for atom in limits.keys():
        files = glob.glob(homeDir + "/results/" + "all_" + atom + "_*.txt")
        for filename in files:
            dir,file = os.path.split(filename)
            dLimit = limits[atom]
            resultsPath = os.path.join(homeDir, 'results', file)
            attrsPath = os.path.join(homeDir, 'attrs', file)
            with open(resultsPath, 'r') as descriptFile, open(attrsPath, 'r') as attrsFile:
                    attrsFile.readline()
                    for resultLine, attrLine in izip(descriptFile, attrsFile):
                        exp, pred, delta, pdbId, atomName = resultLine.strip('\n').split('\t')
                        if abs(float(delta)) > dLimit and [pdbId,atomName] not in trimDict[atom]:
                            #print pdbId, atomName, 'exp ', exp, 'pred: ', pred, 'delta: ', delta, 'limit: ', dLimit
                            trimDict[atom].append([pdbId,atomName])

    #write out new trim file
    with open(trimPath,'w') as trimFile:
        for k,v in trimDict.items():
            for pdb, atomName in v:
                trimFile.write('{}\t{}\t{}\n'.format(k, pdb, atomName))
    return trimDict

def loadLimits(devValue):
    rmsDict = fitAll(False, False, True)
    limitDict = {}
    for atom in atoms:
        limitDict[atom] = rmsDict[atom] * devValue
    return limitDict

#removes test entries from the main dataset
def removeTest(fileName):
    testSetFile = 'testSet.txt'
    test_pdbIds = []
    test_bmrbIds = []
    train_pdbIds = []
    train_bmrbIds = []
    with open(fileName, 'r') as initialFile, open(testSetFile, 'r') as testFile:
        for line in testFile:
            pdbId, bmrbId = line.strip('\n').split('\t')
            test_pdbIds.append(pdbId)
            test_bmrbIds.append(bmrbId)
        for line in initialFile:
            pdbId, bmrbId = line.strip('\n').split()[:2]
            if bmrbId in test_bmrbIds or pdbId in test_pdbIds:
                print bmrbId, pdbId
                continue
            train_pdbIds.append(pdbId)
            train_bmrbIds.append(bmrbId)

    return train_pdbIds + test_pdbIds, train_bmrbIds + test_bmrbIds

def newDir(newHomeDir=homeDir):
    if not os.path.exists(newHomeDir):
        print 'creating new home directory', newHomeDir
        os.mkdir(newHomeDir)
        dirNames = [attrDir, 'attrs', 'maps', 'coefs', 'results', 'testresults', 'refErrors']
        for dirName in dirNames:
            dirPath = os.path.join(newHomeDir, dirName)
            print 'creating', dirPath
            os.makedirs(dirPath)

def copyBaseAttrFiles(dir1, dir2, atom=None, aa=None):
    if aa:
        attrFilePath = glob.glob(os.path.join(dir1, attrDir, "sngl_" + atom + "_*" + aa + "*.txt"))
    elif atom:
        attrFilePath = glob.glob(os.path.join(dir1, attrDir, "sngl_" + atom + "_*.txt"))
    else:
        attrFilePath = glob.glob(os.path.join(dir1, attrDir, "*.txt"))
    for filePath in attrFilePath:
        dir, filename = os.path.split(filePath)
        attrFileDest = os.path.join(dir2, attrDir, filename)
        shutil.copyfile(filePath, attrFileDest)

#genAttrsFromList() generates sngl attribute txt files per atom type in atoms[] per residue
#combine() generates all attr files based on aaGroups[], generates map of pdbIDs and atomNames
#fit() loads attrs into matrix, calls LASSO, generates map 2.0 (adds cols of experimental values, predicted values, delta)
#work flow: gen --> init --> corr --> corr --> final

parser = argparse.ArgumentParser(usage='')
subparsers = parser.add_subparsers(title='subcommands', description='positional args: home directory, optional flags: -test testMode=True, -coefs write coefficients to file')

def prepData(args):
    #removes pdb and bmrb entries in the test set from the generated dataset
    pdbIds, bmrbIds = removeTest(args.filename)
    #runs all pdb and bmrb files to make sure they can be loaded into molecule objects
    filterEntries(pdbIds, bmrbIds)
    #predicts based on existing coefficients, preliminary quality measure

parser_prep = subparsers.add_parser('prep', help='filter unreadable entries and combine with test set, pass in paired pdb - bmrb entries, filename required')
parser_prep.add_argument('filename', type=str)
parser_prep.set_defaults(func=prepData)

def gen(args):
    global bmrbHome
    global pdbHome
    global pdbDir
    global homeDir
    if args.bmrb:
        bmrbHome = args.bmrb
    if args.pdb: 
        pdbHome = args.pdb
    if args.pdbDir:
        pdbDir = args.pdbDir
    homeDir = args.homeDir
    newDir(homeDir)
    print homeDir, args.dataset, 'gen'
    genAttrsFromList(args.dataset)

parser_gen = subparsers.add_parser('gen', help='generate base attributes, home dir')
parser_gen.add_argument('homeDir', nargs='?', default=homeDir, type=str)
parser_gen.add_argument('dataset', nargs='?', default=datasetFileName, type=str)
parser_gen.set_defaults(func=gen)


def cpAttrs(args):
    global homeDir
    homeDir = args.dest
    newDir(homeDir)
    copyBaseAttrFiles(args.orig, args.dest, args.atom, args.res)

parser_copy = subparsers.add_parser('copy', help='copy attrs from other directory, origin dir, destination dir, -atom, -res')
parser_copy.add_argument('orig', type=str)
parser_copy.add_argument('dest', type=str)
parser_copy.add_argument('-atom', type=str)
parser_copy.add_argument('-res', type=str)
parser_copy.set_defaults(func=cpAttrs)

def doAll(args):
    global bmrbHome
    global pdbHome
    global pdbDir 
    global homeDir
    homeDir = args.homeDir
    if args.bmrb:
        bmrbHome = args.bmrb
    if args.pdb:
        pdbHome = args.pdb
    if args.pdbDir:
        pdbDir = args.pdbDir 
    newDir(homeDir)
    if args.gen:
        gen(args)
    initialProp(args)
    doCorrection(args)
    doCorrection(args)
    finalProp(args)

parser_doall = subparsers.add_parser('doall', help='run entire workflow, home dir, dataset, -bmrb, -pdb, -pdbDir,  -gen, -test')
parser_doall.add_argument('homeDir', nargs='?', default=homeDir, type=str)
parser_doall.add_argument('dataset', nargs='?', default=datasetFileName, type=str)
parser_doall.add_argument('-bmrb', type=str)
parser_doall.add_argument('-pdb', type=str)
parser_doall.add_argument('-pdbDir', type=str)
parser_doall.add_argument('-gen', action='store_true')
parser_doall.add_argument('-coefs', action='store_true')
parser_doall.add_argument('-test', type=str)
parser_doall.set_defaults(func=doAll)

def initialProp(args):
    global homeDir
    homeDir = args.homeDir
    #combine sngl attr files from genAttrs() into all files based on aa groups
    combine()
    #initial fit to generate result files, subsequent trim and rereference is based on the delta value
    fitAll()

parser_init = subparsers.add_parser('init', help='initial fit from base attr files, home dir')
parser_init.add_argument('homeDir', nargs='?', default=homeDir, type=str)
parser_init.set_defaults(func=initialProp)

#initial ref error correction
def doCorrection(args):
    global homeDir
    homeDir = args.homeDir
    #tight trim based on ppm limits set by shiftx2
    trimDict = bigTrim()
    #generates txt files of reference errors by pdbID
    calCorr(trimDict)
    #rereference atoms and skip over trimmed atoms
    combine(trimDict, True)
    #generates result files with rereferenced values and new delta
    fitAll()

parser_corr = subparsers.add_parser('corr', help='trim and rereference, home dir')
parser_corr.add_argument('homeDir', nargs='?', default=homeDir, type=str)
parser_corr.set_defaults(func=doCorrection)

def stdTrim(args):
    # trim based on std dev
    limits = loadLimits(args.stdDev)
    trimAgain = bigTrim(limits)
    #incorporate second trim (skips over trimmed atoms)
    combine(trimAgain)

parser_stdTrim = subparsers.add_parser('stdTrim', help='standard deviation trim, int required')
parser_stdTrim.add_argument('stdDev', default=3.0, type=float)
parser_stdTrim.set_defaults(func=stdTrim)

#perform fit on specific attr file
def doSingleFit(args):
    global homeDir
    homeDir = args.homeDir
    attrFile = glob.glob(os.path.join(homeDir, 'attrs', 'all_' + args.atom + '_*' + args.aa + '*.txt'))[0]
    minSum, maxSum = minMaxSums[args.atom]
    fit(attrFile, minSum, maxSum)

parser_fit = subparsers.add_parser('fit', help='fit single attr file, home dir, atom, aa')
parser_fit.add_argument('homeDir', nargs='?', default=homeDir, type=str)
parser_fit.add_argument('atom', type=str)
parser_fit.add_argument('aa', type=str)
parser_fit.set_defaults(func=doSingleFit)

#perform fit on all attr files
def finalProp(args):
    global homeDir
    global testSet
    testMode = False
    homeDir = args.homeDir
    if args.test:
        testSet = args.test
        testMode = True
    fitAll(testMode, args.coefs)

parser_fitAll = subparsers.add_parser('final', help='fit all attr files, home dir, -test, -coefs')
parser_fitAll.add_argument('homeDir', nargs='?', default=homeDir, type=str)
parser_fitAll.add_argument('-test', type=str)
parser_fitAll.add_argument('-coefs', action='store_true', default=False)
parser_fitAll.set_defaults(func=finalProp)

def assessAll(args):
    assessPdbs(args.filename)

parser_assessAll = subparsers.add_parser('assessall', help='assess list of entries, filename')
parser_assessAll.add_argument('filename', type=str)
parser_assessAll.set_defaults(func=assessAll)

def assessPdb(args):
    assessPdbInternal(args.pdbId, args.bmrbId)

parser_assessPdb = subparsers.add_parser('assess', help='assess single pdb-bmrb entry, pdbId, bmrbId')
parser_assessPdb.add_argument('pdbId', type=str)
parser_assessPdb.add_argument('bmrbId', type=str)
parser_assessPdb.set_defaults(func=assessPdb)

if len(sys.argv) < 2:
    print parser.print_help()
else:
    sys.argv.pop(0)
    args = parser.parse_args(sys.argv)
    args.func(args)

