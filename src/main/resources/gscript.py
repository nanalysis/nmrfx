from org.nmrfx.processor.gui import FXMLController
from org.nmrfx.processor.gui import MainApp
from org.nmrfx.processor.gui import GUIScripter
import argparse
import dscript


class NMRFxWindowScripting:
    def __init__(self,winName=None):
        if winName==None:
            self.cmd = GUIScripter()
        else:
            self.cmd = GUIScripter(winName)

    def getDAttrs(self):
        activeController = FXMLController.getActiveController()
        chart=activeController.getActiveChart()
        dAttrs = chart.getDatasetAttributes()
        return dAttrs

    def getActiveChart(self):
        activeController = FXMLController.getActiveController()
        chart=activeController.getActiveChart()
        return chart

    def new(self):
        self.cmd.newStage()
        return self

    def geometry(self, x=None, y=None, width=None, height=None):
        if (x==None and y==None and width==None and height==None):
            return self.cmd.geometry()
        else:
            self.cmd.geometry(x, y, width, height)

    def grid(self, rows=1, columns=1):
        self.cmd.grid(rows, columns)
        return self

    def getGrid(self):
        return self.cmd.grid()

    def stages(self):
        return self.cmd.stages()

    def nCharts(self):
        return self.cmd.nCharts()

    def active(self, chartName=None):
        if chartName != None:
            self.cmd.active(chartName)
            return self
        else:
            return self.cmd.active()

    def datasets(self, datasetNames=None):
        if datasetNames == None:
            return self.cmd.datasets()
        else:
            self.cmd.datasets(datasetNames)

    def peakLists(self, peakListNames=None):
        if peakListNames == None:
            return self.cmd.peakLists()
        else:
            self.cmd.peakLists(peakListNames)

    def config(self, datasets=None, pars=None,  **kwargs):
        if datasets != None:
            if not isinstance(datasets,list):
                datasets = [datasets]
        if len(kwargs) == 0 and pars == None:
            return self.cmd.config(datasets)
        else:
            configData = {}
            if pars != None:
                configData.update(pars)
            if len(kwargs) != 0:
                configData.update(kwargs)
            self.cmd.config(datasets,configData)

    def pconfig(self, peakLists=None, pars=None,  **kwargs):
        if peakLists != None:
            if not isinstance(peakLists,list):
                peakLists = [peakLists]
        if len(kwargs) == 0 and pars == None:
            return self.cmd.pconfig(peakLists)
        else:
            configData = {}
            if pars != None:
                configData.update(pars)
            if len(kwargs) != 0:
                configData.update(kwargs)
            self.cmd.pconfig(peakLists,configData)

    def colors(self, indices, colorName, dataset=None):
        self.cmd.colorMap(dataset, indices, colorName)

    def offsets(self, indices, offset, dataset=None):
        self.cmd.offsetMap(dataset, indices, offset)

    def lim(self,pars=None,**kwargs):
        if (pars==None) and (len(kwargs) == 0):
            return self.cmd.limit()
        else:
            if pars != None:
                print 'pars',pars
                for elem in pars:
                    (v1,v2) = pars[elem] 
                    self.cmd.limit(elem, v1, v2)
            for elem in kwargs:
                (v1,v2) = kwargs[elem]
                self.cmd.limit(elem, v1, v2)
        self.cmd.draw()

    def axlim(self, axis, v1, v2):
        self.cmd.limit(axis, v1, v2)
        self.cmd.draw()

    def configOld(self,datasets=None, **kwargs):
        dAttrs = self.getDAttrs()
        if datasets == None:
            useDAttrs = dAttrs
        else:
            if not isinstance(datasets,list):
                datasets = [datasets]
            useDAttrs = []
            for dAttr in dAttrs:
                fileName = dAttr.getFileName()
                if fileName in datasets:
                    useDAttrs.append(dAttr)

        if len(kwargs) == 0:
            return useDAttrs[0].config()
        else:
            for elem in kwargs:
                for dAttr in useDAttrs:
                    dAttr.config(elem,kwargs[elem])
        chart = self.getActiveChart()
        chart.draw()

def parseArgs(argv):
    nw = NMRFxWindowScripting()
    parser = argparse.ArgumentParser(description="Evaluate NMRFx Command Line Args")
    parser.add_argument("-r", dest="rows",default='1', help="Number of chart rows")
    parser.add_argument("-c", dest="columns",default='1', help="Number of chart columns")
    #parser.add_argument("-g", dest="groupList",default='', help="Residues to fit in groups")
    parser.add_argument("fileNames",nargs="*")
    args = parser.parse_args(args=argv)
    rows = int(args.rows)
    columns = int(args.columns)
    nw.grid(rows,columns)
    nWins = rows*columns
    if (nWins > 1) and len(args.fileNames) != nWins:
        print "Number of files must equal number of windows if using a grid"
        exit(1)
    for i,fileName in enumerate(args.fileNames):
       dataset = dscript.nd.open(fileName)
       iWin = i % nWins
       nw.active(iWin).cmd.addDataset(dataset)
       
