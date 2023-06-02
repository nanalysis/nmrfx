# Removed code

We decided to remove unused code to reduce cognitive load and maintenance cost.
However, some of it may still be useful later.
We can retrieve it from git history, but we will need some sort of index to know what we could search for.

This is what this file aims to be.

Only code that looks complex or relevant for future work has been noted.
Trivial code or obsolete UI functions can be removed without note.

Removal dates in YYYY-MM-DD format.

## Complete classes

### 2023-05-12

* org.nmrfx.processor.datasets.DataBuffer and subclasses
* org.nmrfx.processor.math.MatrixUtil
* org.nmrfx.processor.optimization.PeakMatcher
* org.nmrfx.structure.chemistry.energy.LayoutMolecule
* org.nmrfx.structure.chemistry.miner.NanoMol
* org.nmrfx.structure.chemistry.miner.PMol

## 2023-05-16

* org.nmrfx.chart.ChartStage
* org.nmrfx.utils.properties.ListPropertyEditor
* org.nmrfx.processor.gui.controls.ConsoleUtil

## 2023-06-01

* org.nmrfx.processor.gui.spectra.SpectrumWriter

## Methods in UI classes

### 2023-05-16

* ChartProcessor.setOp(String op, boolean appendOp, int index)
* ChartProcessor.buildMultiScript(String baseDir, String outputDir, ArrayList<String> fileNames, boolean combineFiles)
* ChartProcessor.setFlags()
* FXMLController.makeNewWinIcon()
* FXMLController.addChart(Integer pos)
* IconUtilities.create(char fontChar) + many char icon constants
* SpectrumMeasureBar.makeIcon(int i, int j, boolean boundMode)
* GridPaneCanvas.getLocal(double x, double y)
* GridPaneCanvas.getFraction(double x, double y)
* Symbol.hit() + commented drawing code for triangle, cross, square diamond
* GraphicsContextInterface, GraphicsContextProxy, PDFGraphicsContext, SVGGraphicsContext:
    * multiple methods, most of which were also unimplemented in at least one implementing class
* GraphicsIO, PDFWriter, SVGWriter:
    * drawText, drawPolyLine, drawRect unused variants

### 2023-06-01

* DrawSpectrum
    * getContours(..), genContourPath(..), setColorGradient(..), getMarchingSquares
    * getRegionAsArray(..), getOffsetsAsArray(..)
    * drawVector(..), drawRegion(..) variants