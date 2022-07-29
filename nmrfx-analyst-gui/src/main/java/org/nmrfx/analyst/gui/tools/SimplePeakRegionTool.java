package org.nmrfx.analyst.gui.tools;

import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.stage.FileChooser;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.annotations.AnnoJournalFormat;
import org.nmrfx.analyst.gui.molecule.CanvasMolecule;
import org.nmrfx.analyst.peaks.Analyzer;
import org.nmrfx.analyst.peaks.JournalFormat;
import org.nmrfx.analyst.peaks.JournalFormatPeaks;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.events.PeakEvent;
import org.nmrfx.peaks.events.PeakListener;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.*;
import org.nmrfx.processor.gui.controls.ConsoleUtil;
import org.nmrfx.processor.gui.spectra.CrossHairs;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.nmrfx.utils.GUIUtils.affirm;
import static org.nmrfx.utils.GUIUtils.warn;

public class SimplePeakRegionTool implements ControllerTool, PeakListener {
    private static final Logger log = LoggerFactory.getLogger(SimplePeakRegionTool.class);
    FXMLController controller;
    PolyChart chart;
    CheckMenuItem journalCheckBox;
    CanvasMolecule cMol = null;


    public SimplePeakRegionTool(FXMLController controller, PolyChart chart) {
        this.controller = controller;
        this.chart = chart;
    }

    @Override
    public void close() {

    }

    public void addButtons(SpectrumStatusBar statusBar) {

        var regionButton = new SplitMenuButton();
        regionButton.setText("Integrate");

        MenuItem clearRegionsItem = new MenuItem("Clear");
        clearRegionsItem.setOnAction(e -> clearAnalysis(true));

        MenuItem thresholdMenuItem = new MenuItem("Set Threshold");
        thresholdMenuItem.setOnAction(e -> setThreshold());

        MenuItem clearThresholdMenuItem = new MenuItem("Clear Threshold");
        clearThresholdMenuItem.setOnAction(e -> clearThreshold());

        MenuItem saveRegionsMenuItem = new MenuItem("Save Regions");
        saveRegionsMenuItem.setOnAction(e -> saveRegions());

        MenuItem loadRegionsMenuItem = new MenuItem("Load Regions");
        loadRegionsMenuItem.setOnAction(e -> loadRegions());

        regionButton.getItems().addAll(clearRegionsItem, saveRegionsMenuItem, loadRegionsMenuItem,
                thresholdMenuItem, clearThresholdMenuItem);
        regionButton.setOnAction(e -> findRegions());

        var peakButton = new SplitMenuButton();
        peakButton.setText("PeakPick");
        peakButton.setOnAction(e -> peakPick());
        MenuItem clearPeakListItem = new MenuItem("Clear PeakList");
        clearPeakListItem.setOnAction(e -> clearPeakList());
        peakButton.getItems().add(clearPeakListItem);

        var wizardButton = new SplitMenuButton();
        wizardButton.setText("Analyze");
        wizardButton.setOnAction(e -> analyzeMultiplets());

        journalCheckBox = new CheckMenuItem("Show Report");
        MenuItem copyJournalFormatMenuItem = new MenuItem("Copy Report");
        journalCheckBox.setOnAction(e -> toggleJournalFormatDisplay());

        copyJournalFormatMenuItem.setOnAction(e -> journalFormatToClipboard());
        wizardButton.getItems().addAll(journalCheckBox, copyJournalFormatMenuItem);

        var moleculeButton = new SplitMenuButton();
        moleculeButton.setText("Molecule");
        moleculeButton.setOnAction(e -> addMolecule());
        MenuItem delCanvasMolMenuItem = new MenuItem("Remove Molecule");
        delCanvasMolMenuItem.setOnAction(e -> removeMolecule());
        moleculeButton.getItems().add(delCanvasMolMenuItem);

        statusBar.addToolBarButtons(regionButton, peakButton, wizardButton, moleculeButton);
    }

    PolyChart getChart() {
        chart = controller.getActiveChart();
        return chart;
    }

    public Analyzer getAnalyzer() {
        chart = getChart();
        Dataset dataset = (Dataset) chart.getDataset();
        if ((dataset == null) || (dataset.getNDim() > 1)) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("Chart must have a 1D dataset");
            alert.showAndWait();
            return null;
        }
        Analyzer analyzer = Analyzer.getAnalyzer(dataset);
        if (!chart.getPeakListAttributes().isEmpty()) {
            analyzer.setPeakList(chart.getPeakListAttributes().get(0).getPeakList());
        }
        return analyzer;
    }

    public boolean clearAnalysis(boolean prompt) {
        if (!prompt || affirm("Clear Analysis")) {
            Analyzer analyzer = getAnalyzer();
            if (analyzer != null) {
                analyzer.clearAnalysis();
            }
            chart.chartProps.setRegions(false);
            chart.chartProps.setIntegrals(false);
            chart.refresh();
            return true;
        } else {
            return false;
        }
    }

    public boolean hasRegions() {
        Set<DatasetRegion> regions = chart.getDataset().getRegions();
        return (regions != null) && !regions.isEmpty();
    }

    public void findRegions() {
        if (hasRegions()) {
            if (!clearAnalysis(true)) {
                return;
            }
        }
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            analyzer.calculateThreshold();
            analyzer.getThreshold();
            analyzer.autoSetRegions();
            try {
                analyzer.integrate();
            } catch (IOException ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
                return;
            }
            Set<DatasetRegion> regions = chart.getDataset().getRegions();
            Dataset dataset = (Dataset) chart.getDataset();
            if (!regions.isEmpty()) {
                // normalize to the smallest integral, but don't count very small integrals (less than 0.001 of max
                // to avoid artifacts

                double threshold = regions.stream().mapToDouble(r -> r.getIntegral()).max().orElse(1.0) / 1000.0;
                regions.stream().mapToDouble(r -> r.getIntegral()).filter(r -> r > threshold).min().ifPresent(min -> {
                    dataset.setNorm(min * dataset.getScale() / 1.0);
                });
            }
            chart.refresh();
            chart.chartProps.setRegions(true);
            chart.chartProps.setIntegrals(true);
            chart.setActiveRegion(null);
            chart.refresh();
        }
    }

    private void setThreshold() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            CrossHairs crossHairs = chart.getCrossHairs();
            if (!crossHairs.hasCrosshairState("h0")) {
                warn("Threshold", "Must have horizontal crosshair");
                return;
            }
            Double[] pos = crossHairs.getCrossHairPositions(0);
            System.out.println(pos[0] + " " + pos[1]);
            analyzer.setThreshold(pos[1]);
        }
    }

    private void clearThreshold() {
        if (getAnalyzer() != null) {
            getAnalyzer().clearThreshold();
        }
    }


    private void saveRegions() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            TreeSet<DatasetRegion> regions = analyzer.getDataset().getRegions();
            if (regions.isEmpty()) {
                GUIUtils.warn("Regions Save", "No regions to save");
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Regions File");
            File regionFile = chooser.showSaveDialog(null);
            if (regionFile != null) {
                try {
                    analyzer.saveRegions(regionFile);
                } catch (IOException ioE) {
                    GUIUtils.warn("Error writing regions file", ioE.getMessage());
                }
            }
        }
    }

    private void loadRegions() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Read Regions File");
            File regionFile = chooser.showOpenDialog(null);
            if (regionFile != null) {
                try {
                    analyzer.loadRegions(regionFile);
                    getChart().chartProps.setIntegrals(true);
                    getChart().chartProps.setRegions(true);
                    getChart().refresh();
                } catch (IOException ioE) {
                    GUIUtils.warn("Error reading regions file", ioE.getMessage());
                }
            }
        }
    }

    public void peakPick() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            Set<DatasetRegion> regions = chart.getDataset().getRegions();
            if ((regions == null) || regions.isEmpty()) {
                analyzer.calculateThreshold();
                double threshold = analyzer.getThreshold();
                PeakPicking.peakPickActive(controller, threshold);
                analyzer.setPeakList(chart.getPeakListAttributes().get(0).getPeakList());
            } else {
                analyzer.peakPickRegions();
            }
            PeakList peakList = analyzer.getPeakList();
            List<String> peakListNames = new ArrayList<>();
            peakListNames.add(peakList.getName());
            chart.chartProps.setRegions(false);
            chart.chartProps.setIntegrals(true);
            chart.updatePeakLists(peakListNames);
            var dStat = peakList.widthDStats(0);
            double minWidth = dStat.getPercentile(10);
            double maxWidth = dStat.getPercentile(90);
            peakList.setProperty("minWidth", String.valueOf(minWidth));
            peakList.setProperty("maxWidth", String.valueOf(maxWidth));
            chart.refresh();
        }

    }

    public void clearPeakList() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            PeakList peakList = analyzer.getPeakList();
            if (peakList != null) {
                PeakList.remove(peakList.getName());
                analyzer.setPeakList(null);
                analyzer.resetAnalyzed();
                List<String> peakListNames = new ArrayList<>();
                chart.updatePeakLists(peakListNames);
                chart.refresh();
            }
        }
    }

    public void analyzeMultiplets() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            try {
                if (analyzer.isAnalyzed()) {
                    if (affirm("Clear Analysis")) {
                        clearAnalysis(false);
                    } else {
                        return;
                    }
                }
                analyzer.analyze();
                PeakList peakList = analyzer.getPeakList();
                List<String> peakListNames = new ArrayList<>();
                peakListNames.add(peakList.getName());
                chart.chartProps.setRegions(false);
                chart.chartProps.setIntegrals(true);
                chart.updatePeakLists(peakListNames);
                chart.refresh();
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }

    public void journalFormatToClipboard() {
        JournalFormat format = JournalFormatPeaks.getFormat("JMedCh");
        getAnalyzer();
        if (getAnalyzer() != null) {
            PeakList peakList = getAnalyzer().getPeakList();
            String journalText = format.genOutput(peakList);
            String plainText = JournalFormatPeaks.formatToPlain(journalText);
            String rtfText = JournalFormatPeaks.formatToRTF(journalText);

            Clipboard clipBoard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.put(DataFormat.PLAIN_TEXT, plainText);
            content.put(DataFormat.RTF, rtfText);
            clipBoard.setContent(content);
        }
    }

    public void toggleJournalFormatDisplay() {
        if (journalCheckBox.isSelected()) {
            showJournalFormatOnChart();
        } else {
            removeJournalFormatOnChart();
        }
    }

    public void showJournalFormatOnChart() {
        getAnalyzer();
        if (getAnalyzer() != null) {
            PeakList peakList = getAnalyzer().getPeakList();
            if (peakList == null) {
                removeJournalFormatOnChart();
            } else {
                peakList.registerPeakChangeListener(this);
                AnnoJournalFormat annoText = new AnnoJournalFormat(0.1, 20, 0.9, 100,
                        CanvasAnnotation.POSTYPE.FRACTION,
                        CanvasAnnotation.POSTYPE.PIXEL,
                        peakList.getName());
                chart.chartProps.setTopBorderSize(50);

                chart.clearAnnoType(AnnoJournalFormat.class);
                chart.addAnnotation(annoText);
                chart.refresh();
            }
        }
    }

    public void removeJournalFormatOnChart() {
        getAnalyzer();
        PeakList peakList = getAnalyzer().getPeakList();
        if (peakList != null) {
            peakList.removePeakChangeListener(this);
        }

        chart.chartProps.setTopBorderSize(7);
        chart.clearAnnoType(AnnoJournalFormat.class);
        chart.refresh();
    }

    @Override
    public void peakListChanged(PeakEvent peakEvent) {
        ConsoleUtil.runOnFxThread(() -> {
            if (journalCheckBox.isSelected()) {
                showJournalFormatOnChart();
            }
        });
    }

    void addMolecule() {
        removeMolecule();
        Molecule activeMol = Molecule.getActive();
        if (activeMol == null) {
            ((AnalystApp) AnalystApp.getMainApp()).readMolecule("mol");
            activeMol = Molecule.getActive();
        }
        if (activeMol != null) {
            if (cMol == null) {
                cMol = new CanvasMolecule(FXMLController.getActiveController().getActiveChart());
                cMol.setPosition(0.1, 0.1, 0.3, 0.3, "FRACTION", "FRACTION");
            }

            cMol.setMolName(activeMol.getName());
            activeMol.label = Molecule.LABEL_NONHC;
            activeMol.clearSelected();

            PolyChart chart = FXMLController.getActiveController().getActiveChart();
            chart.clearAnnoType(CanvasMolecule.class);
            chart.addAnnotation(cMol);
            chart.refresh();
        }
    }

    void removeMolecule() {
        PolyChart chart = FXMLController.getActiveController().getActiveChart();
        chart.clearAnnoType(CanvasMolecule.class);
        chart.refresh();
    }
}
