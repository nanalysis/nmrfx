from org.nmrfx.processor.gui import FXMLController
from org.nmrfx.analyst.gui import GUIScripterAdvanced
from org.nmrfx.peaks import PeakList
from org.nmrfx.processor.datasets import Dataset
from javafx.stage import Stage
from javafx.scene.layout import BorderPane
from javafx.scene import Scene
from javafx.scene.control import ToolBar

import argparse
import dscript
from gscript import NMRFxWindowScripting


class NMRFxWindowAdvScripting(NMRFxWindowScripting):
    def __init__(self,winName=None):
        if winName==None:
            self.cmd = GUIScripterAdvanced()
        else:
            self.cmd = GUIScripterAdvanced(winName)

    def strips2(self, peakListName=None, xDim=None, zDim=None):
        if (peakListName==None and xDim==None and zDim==None):
            return self.cmd.strips()
        else:
            self.cmd.strips(peakListName, xDim, zDim)

    def runabout(self, arrangement=None):
        if (arrangement==None):
            return self.cmd.runabout()
        else:
            self.cmd.runabout(arrangement)

    def genYAML(self):
        return self.cmd.genYAML()

    def dumpYaml(self, fileName):
        yamlDump = self.genYAML()
        with open(fileName,'w') as fOut:
            fOut.write(yamlDump.encode("utf-8"))


def parseArgs(argv):
    nw = NMRFxWindowScripting()
    parser = argparse.ArgumentParser(description="Evaluate NMRFx Command Line Args")
    parser.add_argument("-r", dest="rows",default='1', help="Number of chart rows")
    parser.add_argument("-c", dest="columns",default='1', help="Number of chart columns")
    parser.add_argument("fileNames",nargs="*")
    args = parser.parse_args(args=argv)
    rows = int(args.rows)
    columns = int(args.columns)
    nw.grid(rows,columns)
    nWins = rows*columns
    if (nWins > 1) and len(args.fileNames) != nWins:
        print "Number of files must equal number of windows if using a grid"
        exit(1)
    if len(args.fileNames) == 1 and (args.fileNames[0].endswith('ser') or args.fileNames[0].endswith('fid')):
        nw.openFID(args.fileNames[0])
    else:
        for i,fileName in enumerate(args.fileNames):
           dataset = dscript.nd.open(fileName)
           iWin = i % nWins
           nw.active(iWin).cmd.addDataset(dataset)

nw = NMRFxWindowAdvScripting()
