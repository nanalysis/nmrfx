import sys
import time
import os
import runpy
import os.path
import argparse
from pyproc import *
from string import Template

from org.nmrfx.processor.datasets.vendor import RefInfo
from org.nmrfx.processor.math import Vec
from java.lang import Runtime
from org.python.util import PythonInterpreter;

def autoPhaseFirstRow(vec, doFirst = False):
    regions = [0.025,0.45]
    KAISER(vector=vec)
    ZF(vector=vec)
    FT(vector=vec)
    REGIONS(regions,vector=vec)
    phases = vec.autoPhase(doFirst, 0, 0, 0, 45.0, 1.0)
    return phases

def makeScript(args):
    nProc = Runtime.getRuntime().availableProcessors()
    if nProc > 4:
        nProc -= 2
    fileName = args.fidfile
    if not os.path.exists(fileName):
         raise Exception('FID file "' + fileName + '" doesn\'t exist')

    datasetName = args.dataset

    refInfo = RefInfo()

    if os.path.isabs(fileName):
        filePath = fileName
    else:
        filePath = os.path.join(os.getcwd(),fileName)

    fidInfo = FID(filePath)
    nmrData = fidInfo.fidObj
    np = nmrData.getNPoints()
    vec = Vec(np,True)
    nmrData.readVector(0,vec)
    phases = []
    if args.autoPhase:
        phases.append(autoPhaseFirstRow(vec))
    elif args.phaseArgs1 != "":
        ph = args.phaseArgs1
        phases.append([float(ph[0]), float(ph[1])])
    else:
        phases.append([0.0, 0.0])
    if args.phaseArgs2 != "":
        ph = args.phaseArgs2
        phases.append([float(ph[0]), float(ph[1])])
    else:
        phases.append([0.0, 0.0])
    if args.phaseArgs3 != "":
        ph = args.phaseArgs3
        phases.append([float(ph[0]), float(ph[1])])
    else:
        phases.append([0.0, 0.0])
    if args:
        refInfo.setDirectRef(args.refArg)
    parString = refInfo.getParString(nmrData, nmrData.getNDim(), "")
    scriptOps = autoGenScript(fidInfo, args, phases)
    #scriptOps = Template(scriptOps).substitute()
    script = '''
import os
from pyproc import *
procOpts(nprocess=$nProc)
FID('$filePath')
CREATE('$dataset')
$parString
$scriptOps
'''

    if args.autoPhase:
        script +='DIM()\nDPHASE(dim=0)\n'

    script = Template(script).substitute(nProc=nProc, filePath=filePath, dataset=datasetName,parString=parString,scriptOps=scriptOps)
    script += '\nrun()\n'

    nmrData.close()
    # removes nmrData object from processor so we don't have two when processing is done
    useProcessor()
    return script

def saveScript(script):
    scriptName = 'process_auto.py'
    scriptFile = os.path.join(os.getcwd(),scriptName)

    fOut = open(scriptFile,'w')
    fOut.write(script)
    fOut.close

def execScript(script):
    interpreter = PythonInterpreter()
    interpreter.exec(script)


def autoGenScript(fidInfo, args=None, phases=None):
    coefDicts = {'hy':'hyper','hy-r':'hyper-r','ea':'echo-antiecho','ea-r':'echo-antiecho-r','ge':'ge','sep':'sep','re':'real'}
    script = ''
    if fidInfo.nd < 2:
        script += 'DIM(1)\n'
        script += 'EXPD(lb=0.5)\n'
        script += 'ZF()\n'
        script += 'FT()\n'
        script += 'AUTOPHASE(firstOrder=True)\n'
    else:
        script += 'DIM(1)\n'
        script += 'TDSS()\n'
        gotTDComb = False
        if args and args.tdcombArgs2 != "":
            tdComb = args.tdcombArgs2
            tdComb = coefDicts[tdComb]
            gotTDComb = True
            script += "TDCOMB(dim=2 ,coef='" + tdComb + "')\n"
        if args and args.tdcombArgs3 != "":
            tdComb = args.tdcombArgs3
            tdComb = coefDicts[tdComb]
            gotTDComb = True
            script += "TDCOMB(dim=3 ,coef='" + tdComb + "')\n"
        if not gotTDComb:
            for iDim in range(2,fidInfo.nd+1):
                if not fidInfo.fidObj.isFrequencyDim(iDim-1):
                    continue
                if not fidInfo.isComplex(iDim-1):
                    continue
                if fidInfo.mapToDatasetList[iDim-1] == -1:
                    continue
                fCoef = fidInfo.getSymbolicCoefs(iDim-1)
                if fCoef != None and fCoef != 'hyper' and fCoef != 'sep':
                    script += 'TDCOMB('
                    script += "dim="+str(iDim)
                    script += ",coef='"
                    script += fCoef
                    script += "')\n"
        script += 'SB()\n'
        script += 'ZF()\n'
        script += 'FT()\n'
        if phases and len(phases[0]) == 2:
            ph10 = phases[0][0]
            ph11 = phases[0][1]
            script += 'PHASE(ph0='+str(ph10)+',ph1='+str(ph11)+')\n'
        else:
            script += 'PHASE(ph0=0.0,ph1=0.0)\n'

        if args and args.extractArgs != "":
            extract = args.extractArgs
            script += "EXTRACT(" + extract[0] + "," + extract[1] + ",mode='region')\n"

        fCoef = fidInfo.getSymbolicCoefs(1)

        if fCoef != None and fCoef == 'sep':
            script += "COMB(coef='sep')\n"
        if fidInfo.nd > 2 and fidInfo.fidObj.getSampleSchedule() != None:
            multiDim = 'DIM(2'
            for mDim in range(2,fidInfo.nd):
                multiDim += ',' + str(mDim+1)
            multiDim += ')'
            script += multiDim + '\n'
            script += 'NESTA()\n'
    for iDim in range(2,fidInfo.nd+1):
        if fidInfo.size[iDim-1] < 2:
            continue
        if fidInfo.mapToDatasetList[iDim-1] == -1:
            continue
        if not fidInfo.fidObj.isFrequencyDim(iDim-1):
            continue
        script += 'DIM('+str(iDim)+')\n'
        if iDim == 2 and fidInfo.nd == 2 and fidInfo.fidObj.getSampleSchedule() != None:
            script += 'NESTA()\n'
        script += 'SB(c=0.5)\n'
        script += 'ZF()\n'
        script += 'FT('
        negatePairs = fidInfo.negatePairsFT(iDim-1)
        negateImag = fidInfo.negateImagFT(iDim-1)
        if negatePairs:
            script += 'negatePairs=True'
        if negateImag:
            if negatePairs:
                script += ','
            script += 'negateImag=True'
        script += ')\n'
        fCoef = fidInfo.getSymbolicCoefs(iDim-1)
        if fCoef != None and fCoef == 'sep':
            script += "MAG()\n"
        else:
            if phases and len(phases[iDim-1]) == 2:
                ph10 = phases[iDim-1][0]
                ph11 = phases[iDim-1][1]
                script += 'PHASE(ph0='+str(ph10)+',ph1='+str(ph11)+')\n'
    if  not args:
        script += 'run()'
    return script

