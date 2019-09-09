import re
import peakgen
from org.nmrfx.processor.datasets import Dataset

pattern =  '^<(.*)>$'
r = re.compile(pattern)
labelRE = re.compile('\|([A-Za-z].+)\|(.*)\|')

state = []
ornaments = {}
spectrumData = {}
peakList = None
dataset = None
pMap = None

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
    else:
        pass
        #print 'nadada',state,line

def processSpectrum(line):
    global spectrumData
    fields = line.split()
    spectrumData[fields[0]] = fields[1:]
    #print 'ps',line

def processEndSpectrum(line):
    global spectrumData
    #print 'endps',line
    #print spectrumData

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
    #print 'endpo',line
    addPeak()

def makePeakList(ppms, labels):
    global peakListName
    global dataset
    global peakList
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
    
    widths = spectrumData['integrate.min_linewidth']
    if ('peak','rs') in ornaments:
        rs = ornaments['peak','rs']
        labels = []
        for r in rs:
            m = labelRE.match(r)
            if m != None:
                resName = m.group(1)
                atomName = m.group(2)
                atomSpec = resName+'.'+atomName
                labels.append(atomSpec)
        #print labels
        ppms = ornaments['peak','pos']
        ppms = [float(ppm) for ppm in ppms]
        widths = [float(width)*10.0 for width in widths]
        bounds = [width * 2.0  for width in widths]
        if peakList == None:
            makePeakList(ppms, labels)
        peakgen.addPeak(peakList, ppms, widths, bounds, 1.0, labels)
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

def loadSaveFile(fileName,listName,aDataset=None):
    global peakListName
    global dataset
    global pMap
    dataset = aDataset
    print pMap
    print 'dd1',dataset
    if dataset != None:
        print 'dd2',dataset
        if not isinstance(dataset,Dataset):
            dataset = Dataset.getDataset(dataset)
            print 'dd3',dataset

    peakListName = listName
    with open(fileName,'r') as f1:
        for line in f1:
            line = line.strip()
            processLine(line)

