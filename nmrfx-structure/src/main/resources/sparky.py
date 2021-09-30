import re
import peakgen
import os.path
import refine
import molio
from org.nmrfx.processor.datasets import Dataset

pattern =  '^<(.*)>$'
r = re.compile(pattern)
labelRE = re.compile('\|([A-Za-z].+)\|(.*)\|')
atomRE = re.compile('^[0-9]+\.[A-Za-z]+[0-9]*$')

state = []
ornaments = {}
spectrumData = {}
seqData = {}
ppmData = {}
peakList = None
dataset = None
pMap = None

def setTypes(types):
  global expTypes
  expTypes = types 

def processLine(line):
   global state
   m = r.match(line)
   if m != None:
       core = m.group(1)
       fields = core.split()
       if fields[0] == 'sparky':
           pass
       elif fields[0] == 'version':
           pass
       elif fields[0] == 'end':
           dispatchEndState(state, line)
           state = state[0:-1]
           #print 'end',state
       else:
           state.append(core)
   else:
        dispatchState(state, line)

def dispatchState(state, line):
    #print 'state',state,line
    if state == ['spectrum']:
        processSpectrum(line)
    elif state == ['spectrum','view']:
        processView(line)
    elif state == ['spectrum','view','params']:
        processParams(line)
    elif state == ['spectrum','ornament']:
        processOrnament(line)
    elif state == ['savefiles']:
        processSaveFiles(line)
    elif state == ['user']:
        processUser(line)
    elif state == ['molecule', 'condition', 'resonances']:
        processResonance(line)
    else:
        pass
        #print 'nadada',state,line

def dispatchEndState(state, line):
    if state == ['spectrum']:
        processEndSpectrum(line)
    elif state == ['spectrum','view']:
        processEndView(line)
    elif state == ['spectrum','view','params']:
        processEndParams(line)
    elif state == ['spectrum','ornament']:
        processEndOrnament(line)
    elif state == ['savefiles']:
        processEndSaveFiles(line)
    elif state == ['user']:
        processEndUser(line)
    elif state == ['molecule', 'condition', 'resonances']:
        processEndResonance(line)
    else:
        pass
        #print 'nadada',state,line

def processSaveFiles(line):
    global spectrumData
    global projectDir
    global state
    fields = line.split()
    comps = os.path.split(line)
    fileName = line
    fileName = os.path.join(projectDir, fileName)
    listName = comps[1][0:-5]
    print projectDir
    print fileName,listName
    startState = state
    state = state[1:]
    loadSaveFile(fileName,listName)
    state = startState

def processEndSaveFiles(line):
    global spectrumData

def processSpectrum(line):
    global spectrumData
    fields = line.split()
    spectrumData[fields[0]] = fields[1:]
    #print 'ps',line

def processEndSpectrum(line):
    global spectrumData
    global projectDir
    if 'pathname' in spectrumData:
        fileName = spectrumData['pathname'][0]
        fileName = os.path.join(projectDir,fileName)
        print 'path is', fileName
        if os.path.exists(fileName):
            dataset = Dataset(fileName,"",False, False)

def processResonance(line):
    global spectrumData
    global seqData
    global ppmData
    fields = line.split()
    resAtom = fields[0].split('|')
    if resAtom != '?':
        resName = resAtom[1]
        resNum = resName[1:]
        resChar = resName[0]
        atomName = resAtom[2]
        if len(resNum) > 0:
            seqData[resNum] = resChar
    
        ppm = float(fields[1])
        ppmData[resNum+'.'+atomName] = ppm
        nucleus = fields[2]

def processEndResonance(line):
    global spectrumData
    global seqData
    global ppmData
    keys = seqData.keys()
    ikeys = [int(key) for key in keys]
    ikeys.sort()
    seq = ''
    residues = []
    for key in ikeys:
       seq += seqData[str(key)]
       residues.append(str(key))
    indexing = ' '.join(residues)
    seqArray = refine.getSequenceArray(indexing, seq, None, 'protein')
    mol = molio.readSequenceString('A',seqArray)
    for key in ppmData:
        m = atomRE.match(key)
        if m != None:
            atom = mol.findAtom(key)
            if atom == None:
                atom = mol.findAtom(key+'2')
            if atom == None:
                atom = mol.findAtom(key+'1')
            if atom == None:
                atom = mol.findAtom(key+'11')
            if atom == None:
                atom = mol.findAtom(key+'12')
            if atom != None:
                ppm = ppmData[key]
                atom.setPPM(ppm)
            else:
                print 'no atom for',key
        

def processView(line):
    pass
    #print 'pv',line

def processEndView(line):
    pass
    #print 'endpv',line

def processOrnament(line):
    global ornaments
    global ornamentType
    fields = line.split()
    if fields[0] == 'type':
        if fields[1] == 'peak':
            ornamentType = 'peak'
            if len(ornaments) != 0:
                addPeak()
            ornaments = {}
        elif fields[1] == 'label':
            ornamentType = ornamentType+'.Label'
    elif fields[0] == '[':
         #print 'lefty'
         pass
    elif fields[0] == ']':
         ornamentType = 'peak'
    else:
        ornaments[ornamentType,fields[0]] = fields[1:]

def processEndOrnament(line):
    global ornaments
    #print 'endpo',line
    addPeak()
    ornaments={}


def makePeakList(ppms, labels):
    global peakListName
    global dataset
    global peakList
    print 'make peakList', peakListName
    ratios = {'C':0.251449530, 'N':0.101329118, 'P':0.404808636, 'D':0.15306088, 'H':1.0}
    sf0 = 600.0
    if pMap != None:
        sfs = pMap['sf']
        sws = pMap['sw']
        dimLabels = pMap['labels']
        peakList = peakgen.makePeakList(peakListName, dimLabels, sfs, sws)
    elif dataset == None:
        dimLabels = []
        sfs = []
        sws = []
        for label in labels:
            if label.find('H') != -1:
                dimLabel = 'H'
                sf = sf0*ratios['H']
                sw = 2000.0
            elif label.find('N') != -1:
                dimLabel = 'N'
                sf = sf0*ratios['N']
                sw = 2000.0
            elif label.find('C') != -1:
                dimLabel = 'C'
                sf = sf0*ratios['C']
                sw = 2000.0
            if dimLabel in dimLabels:
                dimLabel = dimLabel + '_' + str(len(dimLabels) + 1)
            dimLabels.append(dimLabel)
            sfs.append(sf)
            sws.append(sw)
        
        peakList = peakgen.makePeakList(peakListName, dimLabels, sfs, sws)
    else:
        peakList = peakgen.makePeakListFromDataset(peakListName,dataset)

def addPeak():
    global spectrumData
    global peakList
    global expTypes
    #print 'addpeak',peakList, ornaments
    
    widths = spectrumData['integrate.min_linewidth']
    if ('peak','pos') in ornaments:
        labels = []
        if ('peak','rs') in ornaments:
            rs = ornaments['peak','rs']
            for r in rs:
                m = labelRE.match(r)
                if m != None:
                    resName = m.group(1)
                    atomName = m.group(2)
                    atomSpec = resName+'.'+atomName
                    labels.append(atomSpec)
        #print labels
        height = float(ornaments['peak','height'][1])
        ppms = ornaments['peak','pos']
        ppms = [float(ppm) for ppm in ppms]
        eppms = [0.0]*len(ppms)
        widths = [float(width)*10.0 for width in widths]
        bounds = [width * 2.0  for width in widths]
        if peakList == None:
            if len(labels) == 0:
               if peakListName in expTypes:
                   labels = expTypes[peakListName]['labels']
            makePeakList(ppms, labels)
        if len(labels) == 0:
            labels = ['']*len(ppms)
        print labels
        peakgen.addPeak(peakList, ppms, eppms, widths, bounds, height/1.0e-6, labels)
    #print ornaments

def processParams(line):
    pass
    #print 'pa',line

def processEndParams(line):
    pass
    #print 'endpa',line

def processUser(line):
    pass
    #print 'pu',line

def processEndUser(line):
    pass
    #print 'endpu',line

def loadProjectFile(fileName):
    global projectDir
    projectDir = os.path.split(fileName)[0]
    with open(fileName,'r') as f1:
        for line in f1:
            line = line.strip()
            processLine(line)

def loadSaveFile(fileName,listName,aDataset=None):
    global peakListName
    global dataset
    global peakList
    global pMap
    peakList = None
    dataset = aDataset
    print pMap
    print 'dd1',dataset
    if dataset != None:
        print 'dd2',dataset
        if not isinstance(dataset,Dataset):
            dataset = Dataset.getDataset(dataset)
            print 'dd3',dataset
    print fileName, listName
    peakListName = listName
    with open(fileName,'r') as f1:
        for line in f1:
            line = line.strip()
            processLine(line)

