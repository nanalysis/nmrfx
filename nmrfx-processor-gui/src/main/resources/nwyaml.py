import os
from org.yaml.snakeyaml import Yaml
from java.io import FileInputStream
import gscript
import gscript_adv
from scriptutils import formatStringForJava


def dumpYamlWin(fileName=None):
    if fileName != None:
        yamlFile = fileName
    else:
        yamlFile = yamlFileName
    yamlDump = genYamlData()
    with open(yamlFile,'w') as fOut:
        fOut.write(yamlDump.encode("utf-8"))
    
def genYamlData():
    yaml=Yaml()
    win={}
    win['geometry'] = nw.geometry()
    win['title'] = "Test"
    win['grid'] = nw.getGrid()
    win['sconfig']= nw.sconfig()
    (rows, cols) = win['grid']
    spectra=[]
    win['spectra'] = spectra
    nCharts = nw.nCharts()
    for iSpectrum in range(nCharts):
        nw.active(iSpectrum)
        sd = {}
        spectra.append(sd)
        iRow = iSpectrum / cols
        iCol = iSpectrum % cols
        sd['grid'] = [iRow, iCol]
        sd['lim'] = nw.lim()
        sd['cconfig']= nw.cconfig()
        sd['datasets'] = []
        datasets = nw.datasets() 
        for dataset in datasets:
            dset = {}
            dset['name']=dataset
            dset['config']= nw.config(dataset)
            dset['dims'] = nw.getDims(dataset)
            sd['datasets'].append(dset)
        sd['peaklists'] = []
        peakLists = nw.peakLists() 
        for peakList in peakLists:
            pset = {}
            pset['name']=peakList
            pset['config']= nw.pconfig(peakList)
            sd['peaklists'].append(pset)
    strips = nw.strips2()
    if (strips != None) and ("peaklist" in strips):
        win['strips'] = strips

    yamlDump = yaml.dump(win)
    return yamlDump

def loadYamlWin(yamlFile, createNewStage=0):
    with open(yamlFile) as fIn:
        inputData = fIn.read()
        processYamlData(yamlFile, inputData, createNewStage)

def processYamlData(yamlFile, inputData, createNewStage):
    yaml = Yaml()
    if createNewStage > 0:
        nw.new()
        pathComps = os.path.split(yamlFile)
        title = pathComps[1]
        if title.endswith('_fav.yaml'):
            title = title[0:-9]
        elif title.endswith('.yaml'):
            title = title[0:-5]
        nw.setTitle(title)
    data = yaml.load(inputData)
    if 'geometry' in data:
        (x,y,w,h) = data['geometry']
        nw.geometry(x,y,w,h)
    if 'grid' in data:
        (rows,cols) = data['grid']
        nw.grid(rows,cols)
    if 'sconfig' in data:
        sconfig = data['sconfig']
        nw.sconfig(sconfig)
    spectra = data['spectra']
    for v in spectra :
        print v
        datasets = v['datasets']
        if 'grid' in v:
            (iRow, iCol) = v['grid']
        else:
            iRow = 0
            iCol = 0
        activeWin = iRow*cols+iCol
        print 'g',iRow,iCol,activeWin
        nw.active(activeWin)
        if 'cconfig' in v:
            cconfig = v['cconfig']
            nw.cconfig(cconfig)
        datasetValues = []
        for dataset in datasets:
            print dataset
            name = dataset['name']
            datasetValues.append(formatStringForJava(name))
        print 'dv',datasetValues
        nw.cmd.datasets(datasetValues)
        if 'lim' in v:
            lim = v['lim']
            nw.lim(lim)
        for dataset in datasets:
            name = dataset['name']
            if 'config' in dataset:
                cfg = dataset['config']
                nw.config(datasets=[name],pars=cfg)
            if 'dims' in dataset:
                dims = dataset['dims']
                nw.setDims(dataset=name,dims=dims)

        if 'peaklists' in v:
            peakLists = v['peaklists']
            peakListValues = []
            for peakList in peakLists:
                print peakList
                name = peakList['name']
                peakListValues.append(formatStringForJava(name))
            print 'dv',peakListValues
            nw.cmd.peakLists(peakListValues)
            for peakList in peakLists:
                name = peakList['name']
                if 'config' in peakList:
                    cfg = peakList['config']
                    nw.pconfig(peakLists=[name],pars=cfg)
        nw.drawAll()
    if 'strips' in data:
        strips = data['strips']
        nw.strips2(strips["peaklist"], strips["xdim"], strips["zdim"])

nw = gscript_adv.NMRFxWindowAdvScripting()
