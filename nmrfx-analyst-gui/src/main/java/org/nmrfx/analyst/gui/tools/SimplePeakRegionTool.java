package org.nmrfx.analyst.gui.tools;

import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.stage.FileChooser;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.annotations.AnnoJournalFormat;
import org.nmrfx.analyst.gui.molecule.MoleculeUtils;
import org.nmrfx.analyst.gui.regions.RegionsTableController;
import org.nmrfx.analyst.peaks.Analyzer;
import org.nmrfx.analyst.peaks.JournalFormat;
import org.nmrfx.analyst.peaks.JournalFormatPeaks;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.events.PeakEvent;
import org.nmrfx.peaks.events.PeakListener;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.PeakListTools;
import org.nmrfx.processor.datasets.peaks.PeakPickParameters;
import org.nmrfx.processor.gui.*;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakDisplayParameters;
import org.nmrfx.processor.gui.spectra.crosshair.CrossHairs;
import org.nmrfx.processor.gui.utils.FileUtils;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.nmrfx.utils.GUIUtils.affirm;
import static org.nmrfx.utils.GUIUtils.warn;

public class SimplePeakRegionTool implements ControllerTool, PeakListener {
    private static final Logger log = LoggerFactory.getLogger(SimplePeakRegionTool.class);
    FXMLController controller;

    private Menu changeMoleculeMenu;


    public SimplePeakRegionTool(FXMLController controller) {
        this.controller = controller;
    }

    @Override
    public void close() {
    }

    public void addButtons(SpectrumStatusBar statusBar) {

        var regionButton = new SplitMenuButton();
        regionButton.setText("Integrate");

        MenuItem openRegionsTableItem = new MenuItem("Show Regions Table");
        openRegionsTableItem.setOnAction(e -> RegionsTableController.getRegionsTableController().show());

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

        regionButton.getItems().addAll(openRegionsTableItem, clearRegionsItem, saveRegionsMenuItem, loadRegionsMenuItem,
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

        MenuItem showJournalItem = new MenuItem("Display Report");
        showJournalItem.setOnAction(e -> showJournalFormatOnChart());
        MenuItem removeJournalItem = new MenuItem("Remove Report");
        removeJournalItem.setOnAction(e -> removeJournalFormatOnChart());
        MenuItem copyJournalFormatMenuItem = new MenuItem("Copy Report");
        copyJournalFormatMenuItem.setOnAction(e -> journalFormatToClipboard());

        wizardButton.getItems().addAll(showJournalItem, removeJournalItem, copyJournalFormatMenuItem);

        var moleculeButton = new SplitMenuButton();
        moleculeButton.setText("Molecule");
        moleculeButton.setOnAction(e -> addMolecule());
        MenuItem delCanvasMolMenuItem = new MenuItem("Remove Molecule");
        delCanvasMolMenuItem.setOnAction(e -> removeMolecule());
        moleculeButton.getItems().add(delCanvasMolMenuItem);
        changeMoleculeMenu = new Menu("Change Molecule");
        moleculeButton.setOnShowing(this::adjustMenuOptions);
        statusBar.addToolBarButtons(regionButton, peakButton, wizardButton, moleculeButton);
    }

    /**
     * Adds/Populates the changeMoleculeMenu to the "Molecule" SplitMenuButton if atleast one
     * molecule is loaded into memory, otherwise the menu is removed.
     *
     * @param event The on showing event.
     */
    private void adjustMenuOptions(Event event) {
        SplitMenuButton moleculeButton = (SplitMenuButton) event.getSource();
        if (MoleculeFactory.getMoleculeNames().isEmpty()) {
            moleculeButton.getItems().remove(changeMoleculeMenu);
        } else {
            populateChangeMoleculeMenu();
            if (!moleculeButton.getItems().contains(changeMoleculeMenu)) {
                moleculeButton.getItems().add(changeMoleculeMenu);
            }
        }
    }

    /**
     * Clears the contents of the changeMoleculeMenu and adds the names of the current molecules loaded
     * into memory as MenuItems.
     */
    private void populateChangeMoleculeMenu() {
        changeMoleculeMenu.getItems().clear();
        Set<String> moleculeNames = (Set<String>) MoleculeFactory.getMoleculeNames();
        MenuItem moleculeMenuItem;
        for (String moleculeName : moleculeNames) {
            moleculeMenuItem = new MenuItem(moleculeName);
            moleculeMenuItem.setOnAction(this::moleculeSelected);
            changeMoleculeMenu.getItems().add(moleculeMenuItem);
        }
    }

    /**
     * Sets the selected molecule as the active molecule and updates it on the active chart.
     *
     * @param actionEvent
     */
    private void moleculeSelected(ActionEvent actionEvent) {
        MenuItem selectedMoleculeMenuItem = (MenuItem) actionEvent.getSource();
        MoleculeBase selectedMolecule = MoleculeFactory.getMolecule(selectedMoleculeMenuItem.getText());
        MoleculeFactory.setActive(selectedMolecule);
        MoleculeUtils.addActiveMoleculeToCanvas();
    }

    PolyChart getChart() {
        return controller.getActiveChart();
    }

    public Analyzer getAnalyzer() {
        PolyChart chart = getChart();
        MultipletTool multipletTool = MultipletTool.getTool(chart);
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
        AnalystApp.getShapePrefs(analyzer.getFitParameters());
        return analyzer;
    }

    public boolean clearAnalysis(boolean prompt) {
        PolyChart chart = getChart();
        if (!prompt || affirm("Clear Analysis")) {
            Analyzer analyzer = getAnalyzer();
            if (analyzer != null) {
                analyzer.clearAnalysis();
            }
            chart.getChartProperties().setRegions(false);
            chart.getChartProperties().setIntegralValues(false);
            chart.getChartProperties().setIntegrals(false);
            AnalystApp.getAnalystApp().hidePopover(true);
            chart.refresh();
            return true;
        } else {
            return false;
        }
    }

    public boolean hasRegions() {
        PolyChart chart = getChart();
        if (!chart.hasData()) {
            return false;
        } else {
            List<DatasetRegion> regions = chart.getDataset().getReadOnlyRegions();
            return (regions != null) && !regions.isEmpty();
        }
    }

    public void findRegions() {
        PolyChart chart = getChart();
        if (!chart.hasData()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("Chart must have a 1D dataset");
            alert.showAndWait();
            return;
        }

        if (hasRegions() && !clearAnalysis(true)) {
            return;
        }
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            analyzer.calculateThreshold();
            analyzer.autoSetRegions();
            try {
                analyzer.integrate();
                List<DatasetRegion> regions = chart.getDataset().getReadOnlyRegions();
                Dataset dataset = (Dataset) chart.getDataset();
                if (!regions.isEmpty()) {
                    dataset.setNormFromRegions(regions);
                }
                for (DatasetAttributes datasetAttributes : chart.getDatasetAttributes()) {
                    Dataset thisDataset = (Dataset) datasetAttributes.getDataset();
                    if (dataset != thisDataset) {
                        analyzer.integrate(thisDataset);
                    }

                }
            } catch (IOException ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
                return;
            }
            chart.refresh();
            chart.getChartProperties().setIntegralValues(true);
            chart.setActiveRegion(null);
            chart.refresh();
        }
    }

    private void setThreshold() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            PolyChart chart = getChart();
            CrossHairs crossHairs = chart.getCrossHairs();
            if (!crossHairs.hasState("h0")) {
                warn("Threshold", "Must have horizontal crosshair");
                return;
            }
            Double[] pos = crossHairs.getPositions(0);
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
            List<DatasetRegion> regions = analyzer.getDataset().getReadOnlyRegions();
            if (regions.isEmpty()) {
                GUIUtils.warn("Regions Save", "No regions to save");
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Regions File");
            File regionFile = chooser.showSaveDialog(null);
            if (regionFile != null) {
                analyzer.saveRegions(FileUtils.addFileExtensionIfMissing(regionFile, "txt"));
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
                    getChart().getChartProperties().setIntegralValues(true);
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
            PolyChart chart = getChart();
            List<DatasetRegion> regions = chart.getDataset().getReadOnlyRegions();
            if ((regions == null) || regions.isEmpty()) {
                analyzer.calculateThreshold();
                double threshold = analyzer.getThreshold();
                PeakPickParameters peakPickParameters = new PeakPickParameters();
                peakPickParameters.level(threshold);
                PeakPicking.peakPickActive(controller, peakPickParameters);
                analyzer.setPeakList(chart.getPeakListAttributes().get(0).getPeakList());
            } else {
                analyzer.peakPickRegions();
            }
            PeakList peakList = analyzer.getPeakList();
            PeakListTools.quantifyPeaks(peakList, "evolume");
            List<String> peakListNames = new ArrayList<>();
            peakListNames.add(peakList.getName());
            chart.getChartProperties().setRegions(false);
            chart.getChartProperties().setIntegralValues(true);
            chart.updatePeakListsByName(peakListNames);
            chart.getPeakListAttributes().get(0).setLabelType(PeakDisplayParameters.LabelTypes.Atom);
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
                PolyChart chart = getChart();
                chart.updatePeakListsByName(peakListNames);
                chart.refresh();
                AnalystApp.getAnalystApp().hidePopover(true);
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
                AnalystApp.getShapePrefs(analyzer.getFitParameters());
                analyzer.analyze();
                PeakList peakList = analyzer.getPeakList();

                List<String> peakListNames = new ArrayList<>();
                peakListNames.add(peakList.getName());
                PolyChart chart = getChart();
                chart.getChartProperties().setRegions(false);
                chart.getChartProperties().setIntegralValues(true);
                chart.updatePeakListsByName(peakListNames);
                chart.getPeakListAttributes().get(0).setLabelType(PeakDisplayParameters.LabelTypes.Atom);
                chart.refresh();
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }

    public void journalFormatToClipboard() {
        JournalFormat format = JournalFormatPeaks.getFormat("JMedCh");
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            PeakList peakList = analyzer.getPeakList();
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

    public void showJournalFormatOnChart() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            PeakList peakList = analyzer.getPeakList();
            if (peakList == null) {
                removeJournalFormatOnChart();
            } else {
                peakList.registerPeakChangeListener(this);
                AnnoJournalFormat annoText = new AnnoJournalFormat(0.1, 20, 300,
                        CanvasAnnotation.POSTYPE.FRACTION,
                        CanvasAnnotation.POSTYPE.PIXEL,
                        peakList.getName());
                PolyChart chart = getChart();
                chart.getChartProperties().setTopBorderSize(50);

                chart.clearAnnoType(AnnoJournalFormat.class);
                chart.addAnnotation(annoText);
                chart.refresh();
            }
        }
    }

    public void removeJournalFormatOnChart() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            PeakList peakList = analyzer.getPeakList();
            if (peakList != null) {
                peakList.removePeakChangeListener(this);
            }

            PolyChart chart = getChart();
            chart.getChartProperties().setTopBorderSize(7);
            chart.clearAnnoType(AnnoJournalFormat.class);
            chart.refresh();
        }
    }

    @Override
    public void peakListChanged(PeakEvent peakEvent) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            if (chart.hasAnnoType(AnnoJournalFormat.class)) {
                showJournalFormatOnChart();
            }
        });
    }

    void addMolecule() {
        removeMolecule();
        Molecule activeMol = Molecule.getActive();
        if (activeMol == null) {
            AnalystApp.getAnalystApp().readMolecule("mol");
        }
        MoleculeUtils.addActiveMoleculeToCanvas();
    }

    void removeMolecule() {
        MoleculeUtils.removeMoleculeFromCanvas();
    }
}
