from org.nmrfx.processor.gui import FXMLController
from org.nmrfx.processor.gui import GUIScripter


class nvwin:
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

    def active(self, chartName=None):
        if chartName != None:
            self.cmd.active(chartName)
        return self

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

