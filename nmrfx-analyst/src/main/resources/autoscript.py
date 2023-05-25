import sys
import time
import os
import re
import runpy
import os.path
import argparse
import psspecial
from pyproc import *
from scriptutils import formatStringForJava
from string import Template

from java.lang import Runtime
from org.python.util import PythonInterpreter;
from org.nmrfx.processor.datasets.vendor import RefInfo
from org.nmrfx.processor.math import Vec
from org.nmrfx.processor.processing import ProcessingLib
from org.nmrfx.processor.datasets.vendor import NMRDataUtil

def getLibraryScript(fidFileName, nvFileName, args):
    nmrData = NMRDataUtil.getFID(formatStringForJava(fidFileName)
    sequence = nmrData.getSequence()
    nDim = nmrData.getNDim()
    vendor = nmrData.getVendor()

    autoPhaseVars = []
    if args.autoPhase:
        np = nmrData.getNPoints()
        vec = Vec.createNamedVector(np,'autophase', True)
        nmrData.readVector(0,vec)
        autoPhaseVars = autoPhaseFirstRow(vec)
    nmrData.close()

    seqScript = ProcessingLib.findSequence(sequence, vendor, nDim)

    script = ''
    if seqScript != None:
        header = 'from pyproc import *\n'
        script = header

        script += 'FID("' + fidFileName + '")\n'
        script += 'CREATE("' + nvFileName + '")\n'
        script +=  seqScript.getScript()
       
        vars = seqScript.getVars()
        vars = updateVars(vars, autoPhaseVars, nDim)
        vars = updateVars(vars, args.vars, nDim)
        script = replaceVars(script, vars)
    return script

def getDefaultVars(nDim):
    vars = {}
    for iDim in range(1, nDim+1):
        var = 'ph0.'+str(iDim)
        vars[var] = 0.0
        var = 'ph1.'+str(iDim)
        vars[var] = 0.0
    vars['ref.1'] = "'H2O'"
    vars['fstart.1'] = 10000.0
    vars['fend.1'] = -10000.0
    return vars

def updateVars(vars, varArgs, nDim):
    varPat = re.compile("(.+)=(.+)")
    for arg in varArgs:
        m = varPat.match(arg)
        if m:
           var,value = (m.group(1),m.group(2))
           if not var in vars:
               sys.exit('Error: unknown var specified on command line:' + var)
 
           for iDim in range(1,nDim+1):
               if var.startswith('ph0.'+str(iDim)):
                   vars['autoPh.'+str(iDim)] = 0
               if var.startswith('ph1.'+str(iDim)):
                   vars['autoPh.'+str(iDim)] = 0
           vars[var] = value
    return vars

def replaceVars(script, vars):
    for var in vars:
       value = str(vars[var])
       var = '$'+var
       script = script.replace(var,value)
    return script

def autoPhaseFirstRow(vec, doFirst = False):
    regions = [0.025,0.45]
    KAISER(vector=vec)
    ZF(vector=vec)
    FT(vector=vec)
    REGIONS(regions,vector=vec)
    phases = vec.autoPhase(doFirst, 0, 0, 0, 45.0, 1.0)
    autoPhaseVars = []
    autoPhaseVars.append('ph0.1='+str(phases[0]))
    if doFirst:
       autoPhaseVars.append('ph1.1='+str(phases[1]))
    return autoPhaseVars


def getAutoScript(fileName, datasetName, args):
    refInfo = RefInfo()

    if os.path.isabs(fileName):
        filePath = fileName
    else:
        filePath = os.path.join(os.getcwd(),fileName)

    fidInfo = FID(filePath)
    nmrData = fidInfo.fidObj
    nDim = nmrData.getNDim()
    np = nmrData.getNPoints()
    vec = Vec.createNamedVector(np,'autophase', True)
    nmrData.readVector(0,vec)
    autoPhaseVars = []
    if args.autoPhase:
        autoPhaseVars = autoPhaseFirstRow(vec)

    refInfo.setDirectRef("$ref.1")
    parString = refInfo.getParString(nmrData, nmrData.getNDim(), "")
    scriptOps = autoGenScript(fidInfo, args)
    script = '''
import os
from pyproc import *
procOpts(nprocess=$nProc)
FID('$filePath')
CREATE('$dataset')
$parString
$scriptOps
'''

    nProc = Runtime.getRuntime().availableProcessors()
    if nProc > 4:
        nProc -= 2
    script = Template(script).substitute(filePath=filePath, dataset=datasetName,nProc=nProc, parString=parString,scriptOps=scriptOps)
    script += '\nrun()\n'

    nmrData.close()
    # removes nmrData object from processor so we don't have two when processing is done
    useProcessor()
    vars = getDefaultVars(nDim)
    vars = updateVars(vars, autoPhaseVars, nDim)
    vars = updateVars(vars, args.vars, nDim)
    script = replaceVars(script, vars)
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


def autoGenScript(fidInfo, args=None):
    coefDicts = {'hy':'hyper','hy-r':'hyper-r','ea':'echo-antiecho','ea-r':'echo-antiecho-r','ge':'ge','sep':'sep','re':'real'}
    script = ''
    if fidInfo.nd < 2:
        script += 'DIM(1)\n'
        script += 'SUPPRESS(disabled=True)\n'
        script += 'APODIZE(lbOn=True, lb=0.5)\n'
        script += 'ZF()\n'
        script += 'FT()\n'
        script += 'PHASE()\n'
        script += 'BC(disabled=True)\n'
    else:
        script += psspecial.scriptMods(fidInfo, 0)
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
        script += 'PHASE(ph0=$ph0.1,ph1=$ph1.1)\n'
        script += 'EXTRACTP(fstart=$fstart.1,fend=$fend.1)\n'

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
        if fidInfo.fidObj.getFTType(iDim-1) == "rft":
            script += 'RFT('
        else:
            script += 'FT('

        negateImag = fidInfo.negateImagFT(iDim-1)
        negatePairs = fidInfo.negatePairsFT(iDim-1)
        if negatePairs:
            script += 'negatePairs=True'
        if negateImag:
            if negatePairs:
                script += ','
            if fidInfo.fidObj.getFTType(iDim-1) == "rft":
                script += 'negateOdd=True'
            else:
                script += 'negateImag=True'
        script += ')\n'
        fCoef = fidInfo.getSymbolicCoefs(iDim-1)
        if fCoef != None and fCoef == 'sep':
            script += "MAG()\n"
        else:
            script += 'PHASE(ph0=$ph0.'+str(iDim)+', ph1=$ph1.'+str(iDim)+')\n'
    if  not args:
        script += 'run()'
    return script

def makeScript(args):
    fileName = args.fidfile
    if not os.path.exists(fileName):
         raise Exception('FID file "' + fileName + '" doesn\'t exist')

    datasetName = args.dataset
    script = getLibraryScript(fileName, datasetName, args)
    if script == '':
        script = getAutoScript(fileName, datasetName, args)
    return script
