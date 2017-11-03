from org.nmrfx.processor.gui import FXMLController
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

    def grid(self, rows=1, columns=1):
        self.cmd.grid(rows, columns)
        return self

    def active(self, chartName=None):
        if chartName != None:
            self.cmd.active(chartName)
            return self
        else:
            return self.cmd.active()

    def config(self, datasets=None, pars=None,  **kwargs):
        if datasets != None:
            if not isinstance(datasets,list):
                datasets = [datasets]
        if len(kwargs) == 0 and pars == None:
            return self.cmd.config()
        else:
            configData = {}
            if pars != None:
                configData.update(pars)
            if len(kwargs) != 0:
                configData.update(kwargs)
            self.cmd.config(datasets,configData)

    def colors(self, indices, colorName, dataset=None):
        self.cmd.colorMap(dataset, indices, colorName)

    def offsets(self, indices, offset, dataset=None):
        self.cmd.offsetMap(dataset, indices, offset)

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
       FXMLController.addDatasetToList(dataset)
       iWin = i % nWins
       nw.active(iWin).cmd.addDataset(dataset)
       
