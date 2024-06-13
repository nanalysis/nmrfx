from org.nmrfx.processor.gui import FXMLController
from org.nmrfx.processor.gui import GUIScripter
from org.nmrfx.analyst.gui import AnalystApp
from org.nmrfx.peaks import PeakList
from org.nmrfx.processor.datasets import Dataset
from javafx.stage import Stage
from javafx.scene.layout import BorderPane
from javafx.scene import Scene
from javafx.scene.control import ToolBar

import argparse
import dscript

dimNames = ['x', 'y', 'z', 'a', 'b', 'c', 'd']


class NMRFxWindowScripting:
    def __init__(self, winName=None):
        if winName == None:
            self.cmd = GUIScripter()
        else:
            self.cmd = GUIScripter(winName)

    def getDAttrs(self):
        activeController = self.cmd.getController()
        chart = activeController.getActiveChart()
        dAttrs = chart.getDatasetAttributes()
        return dAttrs

    def useActive(self):
        self.cmd.setActiveController()

    def getActiveChart(self):
        activeController = self.cmd.getController()
        chart = activeController.getActiveChart()
        return chart

    def getActiveController(self):
        return self.cmd.getController()

    def getCursor(self):
        return self.cmd.getCursor()

    def setCursor(self, name):
        self.cmd.setCursor(name)

    def setTitle(self, title):
        self.cmd.setTitle(title)

    def bindKeys(self, keyStr, actionStr):
        self.cmd.bindKeys(keyStr, actionStr)

    def new(self, title=None):
        self.cmd.newStage(title)
        return self

    def geometry(self, x=None, y=None, width=None, height=None):
        if (x == None and y == None and width == None and height == None):
            return self.cmd.geometry()
        else:
            self.cmd.geometry(x, y, width, height)

    def grid(self, rows=1, columns=1):
        self.cmd.grid(rows, columns)
        return self

    def gridpos(self, row=0, column=0, rowSpan=1, columnSpan=1, chart=None):
        if chart == None:
            chart = self.active()
        elif isinstance(chart, basestring):
            self.cmd.active(chart)
            chart = self.cmd.getChart()
        elif isinstance(chart, int):
            self.cmd.active(chart)
            chart = self.cmd.getChart()
        self.cmd.grid(chart, row, column, rowSpan, columnSpan)
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

    def datasets(self, datasets=None):
        if datasets == None:
            return self.cmd.datasets()
        else:
            datasetNames = []
            for dataset in datasets:
                if isinstance(dataset, basestring):
                    datasetNames.append(dataset)
                else:
                    datasetNames.append(dataset.getName())
            self.cmd.datasets(datasetNames)

    def openFID(self, fidName):
        self.cmd.openFID(fidName)

    def peakLists(self, peakListNames=None):
        if peakListNames == None:
            return self.cmd.peakLists()
        else:
            self.cmd.peakLists(peakListNames)

    def config(self, datasets=None, pars=None, **kwargs):
        if datasets != None:
            if not isinstance(datasets, list):
                datasets = [datasets]
        if len(kwargs) == 0 and pars == None:
            return self.cmd.config(datasets)
        else:
            configData = {}
            if pars != None:
                configData.update(pars)
            if len(kwargs) != 0:
                configData.update(kwargs)
            self.cmd.config(datasets, configData)

    def pconfig(self, peakLists=None, pars=None, **kwargs):
        if peakLists != None:
            if not isinstance(peakLists, list):
                peakLists = [peakLists]
        if len(kwargs) == 0 and pars == None:
            return self.cmd.pconfig(peakLists)
        else:
            configData = {}
            if pars != None:
                configData.update(pars)
            if len(kwargs) != 0:
                configData.update(kwargs)
            self.cmd.pconfig(peakLists, configData)

    def cconfig(self, pars=None, **kwargs):
        if len(kwargs) == 0 and pars == None:
            return self.cmd.cconfig()
        else:
            configData = {}
            if pars != None:
                configData.update(pars)
            if len(kwargs) != 0:
                configData.update(kwargs)
            self.cmd.cconfig(configData)

    def sconfig(self, pars=None, **kwargs):
        if len(kwargs) == 0 and pars == None:
            return self.cmd.sconfig()
        else:
            configData = {}
            if pars != None:
                configData.update(pars)
            if len(kwargs) != 0:
                configData.update(kwargs)
            self.cmd.sconfig(configData)

    def getAnnotations(self):
        return self.cmd.getAnnotations()

    def loadAnnotations(self, yamlData):
        self.cmd.loadAnnotations(yamlData)

    def getDims(self, dataset):
        if dataset != None:
            return self.cmd.getDims(dataset)
        else:
            return None

    def setDims(self, dataset=None, dims=None):
        if dataset != None:
            if dims != None:
                dataObj = Dataset.getDataset(dataset)
                iDims = []
                for dim in dims:
                    if isinstance(dim, int):
                        iDims.append(dim)
                    else:
                        iDim = dataObj.getDim(dim)
                        iDims.append(iDim)
            self.cmd.setDims(dataset, iDims)

    def colors(self, indices, colorName, dataset=None):
        self.cmd.colorMap(dataset, indices, colorName)
        self.cmd.draw()

    def offsets(self, indices, offset, dataset=None):
        self.cmd.offsetMap(dataset, indices, offset)
        self.cmd.draw()

    def rows(self, indices, dataset=None):
        self.cmd.rows(dataset, indices)
        self.cmd.draw()

    def lim(self, pars=None, **kwargs):
        if (pars == None) and (len(kwargs) == 0):
            return self.cmd.limit()
        else:
            if pars != None:
                for elem in pars:
                    value = pars[elem]
                    if isinstance(value, (float, int)):
                        v1 = value
                        v2 = value
                    elif len(value) == 1:
                        v1 = value[0]
                        v2 = v1
                    else:
                        (v1, v2) = value
                    self.cmd.limit(elem, v1, v2)
            for elem in kwargs:
                value = kwargs[elem]
                if isinstance(value, (float, int)):
                    v1 = value
                    v2 = value
                elif len(value) == 1:
                    v1 = value[0]
                    v2 = v1
                else:
                    (v1, v2) = value
                self.cmd.limit(elem, v1, v2)
        self.cmd.draw()

    def axlim(self, axis, v1, v2):
        self.cmd.limit(axis, v1, v2)
        self.cmd.draw()

    def configOld(self, datasets=None, **kwargs):
        dAttrs = self.getDAttrs()
        if datasets == None:
            useDAttrs = dAttrs
        else:
            if not isinstance(datasets, list):
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
                    dAttr.config(elem, kwargs[elem])
        chart = self.getActiveChart()
        chart.draw()

    def center(self, *pArgs, **dimArgs):
        pos = []
        nUsed = 0
        for dimName, pArg in zip(dimNames, pArgs):
            dimArgs[dimName] = pArg

        for dim in dimNames:
            if dim in dimArgs:
                pos.append(dimArgs[dim])
                nUsed += 1
            else:
                pos.append(None)
            if nUsed == len(dimArgs):
                break
        pos = pos[:nUsed]
        self.cmd.center(pos)

    def zoom(self, factor=1.2):
        self.cmd.zoom(factor)

    def expand(self):
        self.cmd.expand()

    def full(self, dimName=""):
        dimNum = -1
        if dimName in dimNames:
            dimNum = dimNames.index(dimName)
        self.cmd.full(dimNum)

    def showPeak(self, peakSpecifier):
        self.cmd.showPeak(peakSpecifier)

    def draw(self):
        self.cmd.draw()

    def drawAll(self):
        self.cmd.drawAll()

    def pkstrips(self, peaks, dims, xwidth=0.2, row=0):
        nColumns = len(peaks)
        x = []
        z = []
        datasets = []
        for peak in peaks:
            dataset = peak.getPeakList().getDatasetName()
            if dataset != None:
                x.append(peak.getPeakDim(dims[0]).getChemShiftValue())
                z.append(peak.getPeakDim(dims[2]).getChemShiftValue())
                datasets.append(dataset)

        self.strips(datasets, x, xwidth, dims=dims, row=row, z=z)

    def stripTool(self):
        aC = self.getActiveController()
        tool = aC.getTool("rg.nmrfx.processor.gui.StripController")
        return tool

    def strips(self, datasets, x, xwidth=0.2, dims=None, row=0, **kwargs):
        nDatasets = len(datasets)
        nX = len(x)
        if len(datasets) == len(x):
            nColumns = nX
            nRepeats = 1
        else:
            nColumns = nX * nDatasets
            nRepeats = nDatasets
        nRows = row + 1
        self.grid(rows=nRows, columns=nColumns)
        for iChart in range(nColumns):
            self.active(iChart + row * nColumns)
            dataset = datasets[iChart % nDatasets]
            self.datasets([dataset])
            if dims != None:
                self.setDims(dataset, dims=dims)
            xVal = x[iChart / nRepeats]
            x0 = xVal - xwidth / 2.0
            x1 = xVal + xwidth / 2.0
            self.lim(x=[x0, x1])
            yValue = None
            for elem in kwargs:
                if elem == 'y':
                    yValue = kwargs[elem]
                else:
                    values = kwargs[elem]
                    value = values[iChart / nRepeats]
                    self.axlim(elem, value, value)
            if yValue == None:
                self.full('y')
            else:
                self.lim(y=yValue)
        self.drawAll()
        self.drawAll()

    def createStage(self):
        stage = Stage()
        bPane = BorderPane()
        scene = Scene(bPane)
        stage.setScene(scene)
        stage.show()
        return bPane

    def removeLastTool(self):
        cntrl = self.cmd.getController()
        box = cntrl.getBottomBox()
        nTools = box.getChildren().size()
        if nTools > 1:
            lastTool = nTools - 1
            box.getChildren().remove(lastTool)

    def removeTool(self, toolBar):
        cntrl = self.cmd.getController()
        box = cntrl.getBottomBox()
        box.getChildren().remove(toolBar)

    def addTool(self, toolBar):
        cntrl = self.cmd.getController()
        box = cntrl.getBottomBox()
        box.getChildren().add(toolBar)

    def addPolyLine(self, x, y, color="black", width=1.0):
        self.cmd.addPolyLine(x, y, color, width)

    def addRectangle(self, x1, y1, x2, y2, stroke="black", fill=None, width=1.0):
        self.cmd.addRectangle(x1, y1, x2, y2, stroke, fill, width)

    def addOval(self, x1, y1, x2, y2, stroke="black", fill=None, width=1.0):
        self.cmd.addOval(x1, y1, x2, y2, stroke, fill, width)

    def addPolygon(self, x, y, stroke="black", fill=None, width=1.0):
        self.cmd.addPolygon(x, y, stroke, fill, width)

    def addArrowLine(self, x1, y1, x2, y2, arrowFirst, arrowLast, stroke="black", fill=None, width=1.0):
        self.cmd.addArrowLine(x1, y1, x2, y2, arrowFirst, arrowLast, stroke, fill, width)

    def addLineText(self, x1, y1, x2, y2, text, fontSize=12.0, stroke="black", fill=None, width=1.0):
        self.cmd.addLineText(x1, y1, x2, y2, text, fontSize, stroke, fill, width)

    def addText(self, x1, y1, x2, y2, text, fontSize=12.0):
        self.cmd.addText(x1, y1, x2, y2, text, fontSize)

    def export(self, fileName):
        self.cmd.export(fileName)

    def testGCCanvas(self, n, delay=0.3):
        fm=AnalystApp.getFXMLControllerManager()
        fm.controllerTest(n, delay)


def parseArgs(argv):
    nw = NMRFxWindowScripting()
    parser = argparse.ArgumentParser(description="Evaluate NMRFx Command Line Args")
    parser.add_argument("-r", dest="rows", default='1', help="Number of chart rows")
    parser.add_argument("-c", dest="columns", default='1', help="Number of chart columns")
    # parser.add_argument("-g", dest="groupList",default='', help="Residues to fit in groups")
    parser.add_argument("fileNames", nargs="*")
    args = parser.parse_args(args=argv)
    rows = int(args.rows)
    columns = int(args.columns)
    nw.grid(rows, columns)
    nWins = rows * columns
    if (nWins > 1) and len(args.fileNames) != nWins:
        print
        "Number of files must equal number of windows if using a grid"
        exit(1)
    if len(args.fileNames) == 1 and (args.fileNames[0].endswith('ser') or args.fileNames[0].endswith('fid')):
        nw.openFID(args.fileNames[0])
    else:
        for i, fileName in enumerate(args.fileNames):
            dataset = dscript.nd.open(fileName)
            iWin = i % nWins
            nw.active(iWin).cmd.addDataset(dataset)


nw = NMRFxWindowScripting()
