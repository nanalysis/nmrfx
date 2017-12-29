from org.yaml.snakeyaml import Yaml
from java.io import FileInputStream
import gscript

def dumpYamlWin(yamlFile):
    yaml=Yaml()
    win={}
    win['geometry'] = nw.geometry()
    win['title'] = "Test"
    win['grid'] = nw.getGrid()
    (rows, cols) = win['grid']
    spectra=[]
    win['spectra'] = spectra
    nCharts = nw.nCharts()
    for iSpectrum in range(nCharts):
        nw.active(iSpectrum)
        sd = {}
        spectra.append(sd)
        sd['name'] = "duck"
        iRow = iSpectrum / cols
        iCol = iSpectrum % cols
        sd['grid'] = [iRow, iCol]
        sd['lim'] = nw.lim()
        sd['datasets'] = []
        datasets = nw.datasets() 
        for dataset in datasets:
            dset = {}
            dset['name']=dataset
            dset['config']= nw.config(dataset)
            sd['datasets'].append(dset)
        sd['peakLists'] = []
        peakLists = nw.peakLists() 
        for peakList in peakLists:
            pset = {}
            pset['name']=peakList
            pset['config']= nw.pconfig(peakList)
            sd['peakLists'].append(pset)

    print win
    yamlDump = yaml.dump(win)
    with open(yamlFile,'w') as fOut:
        fOut.write(yamlDump)

def loadYamlWin(yamlFile):
    with open(yamlFile) as fIn:
        inputData = fIn.read()
    yaml = Yaml()
    data = yaml.load(inputData)
    if 'geometry' in data:
        (x,y,w,h) = data['geometry']
        nw.geometry(x,y,w,h)
    if 'grid' in data:
        (rows,cols) = data['grid']
        nw.grid(rows,cols)
    spectra = data['spectra']
    for v in spectra :
        print v
        datasets = v['datasets']
        (iRow, iCol) = v['grid']
        activeWin = iRow*cols+iCol
        print 'g',iRow,iCol,activeWin
        nw.active(activeWin)
        lim = v['lim']
        datasetValues = []
        for dataset in datasets:
            print dataset
            name = dataset['name']
            datasetValues.append(name)
        print 'dv',datasetValues
        nw.cmd.datasets(datasetValues)
        nw.lim(lim)
        for dataset in datasets:
            name = dataset['name']
            cfg = dataset['config']
            nw.config(datasets=[name],pars=cfg)

        if 'peakLists' in v:
            peakLists = v['peakLists']
            peakListValues = []
            for peakList in peakLists:
                print peakList
                name = peakList['name']
                peakListValues.append(name)
            print 'dv',peakListValues
            nw.cmd.peakLists(peakListValues)
            for peakList in peakLists:
                name = peakList['name']
                cfg = peakList['config']
                nw.pconfig(peakLists=[name],pars=cfg)

nw = gscript.NMRFxWindowScripting()
