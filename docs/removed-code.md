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

## Methods in UI classes

### 2023-05-16

* ChartProcessor.setOp(String op, boolean appendOp, int index)
* ChartProcessor.buildMultiScript(String baseDir, String outputDir, ArrayList<String> fileNames, boolean combineFiles)
* ChartProcessor.setFlags()
* FXMLController.makeNewWinIcon()
* FXMLController.addChart(Integer pos)
* IconUtilities.create(char fontChar) + many char icon constants
* SpectrumMeasureBar.makeIcon(int i, int j, boolean boundMode)
* ConsoleUtil: everything except runOnFxThread(..)
* GridPaneCanvas.getLocal(double x, double y)
* GridPaneCanvas.getFraction(double x, double y)