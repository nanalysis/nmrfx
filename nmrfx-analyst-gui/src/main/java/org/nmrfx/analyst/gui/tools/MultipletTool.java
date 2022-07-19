/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.gui.tools;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.peaks.Analyzer;
import org.nmrfx.analyst.peaks.JournalFormat;
import org.nmrfx.analyst.peaks.JournalFormatPeaks;
import org.nmrfx.analyst.peaks.Multiplets;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.peaks.AbsMultipletComponent;
import org.nmrfx.peaks.ComplexCoupling;
import org.nmrfx.peaks.Coupling;
import org.nmrfx.peaks.CouplingPattern;
import org.nmrfx.peaks.Multiplet;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.events.PeakEvent;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.events.PeakListener;
import org.nmrfx.peaks.Singlet;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.analyst.gui.annotations.AnnoJournalFormat;
import org.nmrfx.processor.gui.AnalyzerTool;
import org.nmrfx.processor.gui.controls.ConsoleUtil;
import org.nmrfx.analyst.gui.molecule.CanvasMolecule;
import org.nmrfx.processor.gui.CanvasAnnotation;
import org.nmrfx.processor.gui.ControllerTool;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.CrossHairs;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.MultipletSelection;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.processor.gui.utils.ToolBarUtils;
import org.nmrfx.structure.chemistry.Molecule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.nmrfx.utils.GUIUtils.affirm;
import static org.nmrfx.utils.GUIUtils.warn;

/**
 *
 * @author brucejohnson
 */
public class MultipletTool implements SetChangeListener<MultipletSelection>, ControllerTool, PeakListener, AnalyzerTool {
    private static final Logger log = LoggerFactory.getLogger(MultipletTool.class);

    Stage stage = null;
    VBox vBox;
    HBox navigatorToolBar = new HBox();
    TextField multipletIdField;
    HBox menuBar = new HBox();
    HBox toolBar = new HBox();
    HBox regionToolBar = new HBox();
    HBox peakToolBar = new HBox();
    HBox multipletToolBar = new HBox();
    HBox fittingToolBar = new HBox();
    HBox integralToolBar = new HBox();
    HBox typeToolBar = new HBox();
    GridPane gridPane = new GridPane();
    Button splitButton;
    Button splitRegionButton;
    ChoiceBox<String> peakTypeChoice;
    ChoiceBox<String>[] patternChoices;
    TextField integralField;
    TextField[] couplingFields;
    TextField[] slopeFields;
    FXMLController controller;
    Consumer<MultipletTool> closeAction;

    private PolyChart chart;
    Optional<Multiplet> activeMultiplet = Optional.empty();
    boolean ignoreCouplingChanges = false;
    ChangeListener<String> patternListener;
    Analyzer analyzer = null;
    CheckBox journalCheckBox;
    CheckBox molButton;
    CanvasMolecule cMol = null;

    public MultipletTool(FXMLController controller, Consumer<MultipletTool> closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
    }

    public VBox getBox() {
        return vBox;
    }

    public void close() {
        closeAction.accept(this);
    }

    public void initialize(VBox vBox) {
        this.vBox = vBox;
        String[] patterns = {"d", "t", "q", "p", "h", "dd", "ddd", "dddd"};
        int nCouplings = 5;
        double width1 = 30;
        double width2 = 80;
        double width3 = 60;
        patternChoices = new ChoiceBox[nCouplings];
        couplingFields = new TextField[nCouplings];
        slopeFields = new TextField[nCouplings];
        for (int iRow = 0; iRow < nCouplings; iRow++) {
            patternChoices[iRow] = new ChoiceBox<>();
            patternChoices[iRow].setPrefWidth(width2);
            if (iRow == 0) {
                patternChoices[iRow].getItems().add("");
                patternChoices[iRow].getItems().add("m");
                patternChoices[iRow].getItems().add("s");
            } else {
                patternChoices[iRow].getItems().add("");
            }
            patternChoices[iRow].getItems().addAll(patterns);
            patternChoices[iRow].setValue(patternChoices[iRow].getItems().get(0));
            couplingFields[iRow] = new TextField();
            slopeFields[iRow] = new TextField();
            couplingFields[iRow].setPrefWidth(width3);
            final int couplingIndex = iRow;
            couplingFields[iRow].setOnKeyReleased(e -> {
                log.info("{}", e.getCode());
                if (e.getCode() == KeyCode.ENTER) {
                    couplingValueTyped(couplingIndex);
                }
            });
            slopeFields[iRow].setPrefWidth(width3);
            gridPane.add(patternChoices[iRow], iRow * 3 + 0, 0);
            gridPane.add(couplingFields[iRow], iRow * 3 + 1, 0);
            gridPane.add(slopeFields[iRow], iRow * 3 + 2, 0);
        }
        String iconSize = "12px";
        String fontSize = "7pt";
        ToolBar toolBar1 = new ToolBar();
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", iconSize, fontSize, ContentDisplay.TOP);
        closeButton.setOnAction(e -> close());
        toolBar1.getItems().add(closeButton);
        initMenus(toolBar1);
        ToolBarUtils.addFiller(toolBar1, 10, 500);
        initNavigator(toolBar1);
        ToolBarUtils.addFiller(toolBar1, 10, 20);
        initIntegralType(toolBar1);
        ToolBarUtils.addFiller(toolBar1, 25, 50);

        journalCheckBox = new CheckBox("Text");
        journalCheckBox.setOnAction(e -> toggleJournalFormatDisplay());
        journalCheckBox.getStyleClass().add("toolButton");

        molButton = new CheckBox("Molecule");
        molButton.getStyleClass().add("toolButton");
        molButton.setOnAction(e -> toggleMoleculeDisplay());

        toolBar1.getItems().addAll(journalCheckBox, molButton);

        ToolBarUtils.addFiller(toolBar1, 10, 500);

        ToolBar toolBar2 = new ToolBar();
        initTools(toolBar2);

        Separator vsep1 = new Separator(Orientation.HORIZONTAL);
        Separator vsep2 = new Separator(Orientation.HORIZONTAL);
        vBox.getChildren().addAll(toolBar1, vsep1, toolBar2, vsep2, gridPane);

        patternListener = (ObservableValue<? extends String> observable, String oldValue, String newValue) -> {
            couplingChanged();
        };
        addPatternListener();
        FXMLController controller = FXMLController.getActiveController();
        controller.selPeaks.addListener(e -> setActivePeaks(controller.selPeaks.get()));
        setChart(controller.getActiveChart());
        initMultiplet();
        chart.setRegionConsumer(e -> regionAdded(e));

    }

    public void regionAdded(DatasetRegion region) {
        addRegion(region);
    }

    public void initMenus(ToolBar toolBar) {
        MenuButton menu = new MenuButton("Actions");
        toolBar.getItems().add(menu);
        MenuItem analyzeMenuItem = new MenuItem("Analyze");
        analyzeMenuItem.setOnAction(e -> analyze1D(true));

        Menu stepMenu = new Menu("Stepwise");

        MenuItem findRegionsMenuItem = new MenuItem("Find Regions");
        findRegionsMenuItem.setOnAction(e -> findRegions());

        MenuItem pickRegionsMenuItem = new MenuItem("Pick Regions");
        pickRegionsMenuItem.setOnAction(e -> pickRegions());

        MenuItem analyzePeaksMenuItem = new MenuItem("Analyze Peaks");
        analyzePeaksMenuItem.setOnAction(e -> analyze1D(false));

        MenuItem clearMenuItem = new MenuItem("Clear");
        clearMenuItem.setOnAction(e -> clearAnalysis(true));

        Menu thresholdMenu = new Menu("Threshold");

        MenuItem thresholdMenuItem = new MenuItem("Set Threshold");
        thresholdMenuItem.setOnAction(e -> setThreshold());

        MenuItem clearThresholdMenuItem = new MenuItem("Clear Threshold");
        clearThresholdMenuItem.setOnAction(e -> clearThreshold());

        thresholdMenu.getItems().addAll(thresholdMenuItem, clearThresholdMenuItem);

        Menu reportMenu = new Menu("Report");
        MenuItem copyJournalFormatMenuItem = new MenuItem("Copy");

        copyJournalFormatMenuItem.setOnAction(e -> journalFormatToClipboard());
        reportMenu.getItems().addAll(copyJournalFormatMenuItem);

        menu.getItems().addAll(analyzeMenuItem, stepMenu, clearMenuItem, thresholdMenu, reportMenu);
        stepMenu.getItems().addAll(findRegionsMenuItem, pickRegionsMenuItem, analyzePeaksMenuItem);
    }

    public void initNavigator(ToolBar toolBar) {
        multipletIdField = new TextField();
        multipletIdField.setMinWidth(35);
        multipletIdField.setMaxWidth(35);
        multipletIdField.setPrefWidth(35);

        String iconSize = "12px";
        String fontSize = "7pt";
        ArrayList<Button> buttons = new ArrayList<>();
        Button bButton;

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> firstMultiplet(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> previousMultiplet(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> nextMultiplet(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> lastMultiplet(e));
        buttons.add(bButton);
        Button deleteButton = GlyphsDude.createIconButton(FontAwesomeIcon.BAN, "", fontSize, iconSize, ContentDisplay.GRAPHIC_ONLY);

        // prevent accidental activation when inspector gets focus after hitting space bar on peak in spectrum
        // a second space bar hit would activate
        deleteButton.setOnKeyPressed(e -> e.consume());
        deleteButton.setOnAction(e -> deleteMultiplet());

        toolBar.getItems().addAll(buttons);
        toolBar.getItems().add(multipletIdField);
        toolBar.getItems().add(deleteButton);
        HBox spacer = new HBox();
        toolBar.getItems().add(spacer);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        multipletIdField.setOnKeyReleased(kE -> {
            if (null != kE.getCode()) {
                switch (kE.getCode()) {
                    case ENTER:
                        gotoPeakId(multipletIdField);
                        break;
                    default:
                        break;
                }
            }
        });

    }

    ImageView getIcon(String name) {
        Image imageIcon = new Image("/images/" + name + ".png", true);
        ImageView imageView = new ImageView(imageIcon);
        return imageView;
    }

    void initTools(ToolBar toolBar) {
        Font font = new Font(7);
        List<Button> peakButtons = new ArrayList<>();
        List<Button> regionButtons = new ArrayList<>();
        List<Button> multipletButtons = new ArrayList<>();
        List<Button> fitButtons = new ArrayList<>();
        Button button;

        button = new Button("Add", getIcon("region_add"));
        button.setOnAction(e -> addRegion(null));
        regionButtons.add(button);

        button = new Button("Adjust", getIcon("region_adjust"));
        button.setOnAction(e -> adjustRegion());
        regionButtons.add(button);

        button = new Button("Split", getIcon("region_split"));
        button.setOnAction(e -> splitRegion());
        regionButtons.add(button);

        button = new Button("Delete", getIcon("region_delete"));
        button.setOnAction(e -> removeRegion());
        regionButtons.add(button);

        button = new Button("Add 1", getIcon("peak_add1"));
        button.setOnAction(e -> addPeak());
        peakButtons.add(button);

        button = new Button("Add 2", getIcon("peak_add2"));
        button.setOnAction(e -> addTwoPeaks());
        peakButtons.add(button);

        button = new Button("AutoAdd", getIcon("peak_auto"));
        button.setOnAction(e -> addAuto());
        peakButtons.add(button);

        button = new Button("Delete", getIcon("editdelete"));
        button.setOnAction(e -> removeWeakPeak());
        peakButtons.add(button);

        button = new Button("Extract", getIcon("extract"));
        button.setOnAction(e -> extractMultiplet());
        multipletButtons.add(button);

        button = new Button("Merge", getIcon("merge"));
        button.setOnAction(e -> mergePeaks());
        multipletButtons.add(button);

        button = new Button("Transfer", getIcon("transfer"));
        button.setOnAction(e -> transferPeaks());
        multipletButtons.add(button);

        Button doubletButton = new Button("Doublets", getIcon("tree"));
        doubletButton.setOnAction(e -> toDoublets());
        multipletButtons.add(doubletButton);

        button = new Button("Fit", getIcon("reload"));
        button.setOnAction(e -> fitSelected());
        fitButtons.add(button);

        button = new Button("BICFit", getIcon("reload"));
        button.setOnAction(e -> objectiveDeconvolution());
        fitButtons.add(button);
        Label regionLabel = new Label("Regions:");
        Label peakLabel = new Label("Peaks:");
        Label multipletLabel = new Label("Multiplets:");
        Label fitLabel = new Label("Fit: ");

        ToolBarUtils.addFiller(toolBar, 2, 200);
        toolBar.getItems().add(regionLabel);

        for (Button button1 : regionButtons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setFont(font);
            button1.getStyleClass().add("toolButton");
            toolBar.getItems().add(button1);
        }
        ToolBarUtils.addFiller(toolBar, 5, 20);
        toolBar.getItems().add(peakLabel);
        for (Button button1 : peakButtons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setFont(font);
            button1.getStyleClass().add("toolButton");
            toolBar.getItems().add(button1);
        }
        ToolBarUtils.addFiller(toolBar, 5, 20);
        toolBar.getItems().add(multipletLabel);
        for (Button button1 : multipletButtons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setFont(font);
            button1.getStyleClass().add("toolButton");
            toolBar.getItems().add(button1);
        }
        ToolBarUtils.addFiller(toolBar, 5, 20);
        toolBar.getItems().add(fitLabel);
        for (Button button1 : fitButtons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setFont(font);
            button1.getStyleClass().add("toolButton");
            toolBar.getItems().add(button1);
        }
        ToolBarUtils.addFiller(toolBar, 2, 200);

    }

    void initIntegralType(ToolBar toolBar) {
        Label integralLabel = new Label("N: ");
        integralField = new TextField();
        integralField.setPrefWidth(70);

        integralField.setOnKeyReleased(k -> {
            if (k.getCode() == KeyCode.ENTER) {
                try {
                    double value = Double.parseDouble(integralField.getText().trim());
                    activeMultiplet.ifPresent(m -> {
                        double volume = m.getVolume();
                        PeakList peakList = m.getOrigin().getPeakList();
                        peakList.scale = volume / value;
                        refresh();
                    });
                } catch (NumberFormatException nfE) {
                    log.warn("Unable to parse integral field.", nfE);
                }

            }
        });

        Label peakTypeLabel = new Label("Type: ");
        peakTypeChoice = new ChoiceBox();
        typeToolBar.getChildren().addAll(peakTypeLabel, peakTypeChoice);
        peakTypeChoice.getItems().addAll(Peak.getPeakTypes());
        peakTypeChoice.valueProperty().addListener(e -> setPeakType());
        peakTypeChoice.setPrefWidth(120);
        toolBar.getItems().addAll(integralLabel, integralField);
        toolBar.getItems().addAll(peakTypeLabel, peakTypeChoice);
    }

    /**
     * Updates the tool's chart and analyzer. If the analyzer is null after the update
     * the tool will be closed.
     */
    @Override
    public void updateTool() {
        getAnalyzer();
        if (analyzer == null) {
            log.info("Analyzer is null, closing multiplet tool.");
            close();
        }
    }

    /**
     * Gets the currently active chart and sets the analyzer based on the chart's
     * dataset. A popup to the user will be displayed if the analyzer is set to null.
     */
    public void getAnalyzer() {
        setChart(getChart());
        Dataset dataset = (Dataset) chart.getDataset();
        PeakList activePeaklist = null;
        if (!chart.getPeakListAttributes().isEmpty()) {
            activePeaklist = chart.getPeakListAttributes().get(0).getPeakList();
        }
        if (analyzer == null) {
            if ((dataset == null) || (dataset.getNDim() > 1)) {
                invalidDatasetAlert();
                analyzer = null;
                return;
            }
            analyzer = new Analyzer(dataset);
        } else {
            if (dataset == null || dataset.getNDim() > 1){
                invalidDatasetAlert();
                analyzer = null;
                return;
            }
            if (analyzer.getDataset() != dataset && dataset.getNDim() <= 1) {
                analyzer = new Analyzer(dataset);
            }
        }
        if (activePeaklist != null) {
            analyzer.setPeakList(activePeaklist);
        }
    }

    private void invalidDatasetAlert() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setContentText("Chart must have a 1D dataset to use multiplet tool.");
        alert.showAndWait();
    }

    private void analyze1D(boolean clear) {
        if (analyzer != null) {
            if (clear) {
                clearAnalysis(false);
            }
            try {
                analyzer.analyze();
                PeakList peakList = analyzer.getPeakList();
                List<String> peakListNames = new ArrayList<>();
                peakListNames.add(peakList.getName());
                chart.chartProps.setRegions(false);
                chart.chartProps.setIntegrals(true);
                chart.updatePeakLists(peakListNames);
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }

    private void findRegions() {
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
            chart.chartProps.setRegions(false);
            chart.chartProps.setIntegrals(true);
            chart.refresh();
        }
    }

    private void pickRegions() {
        if (analyzer != null) {
            analyzer.peakPickRegions();
            analyzer.renumber();
            analyzer.setVolumesFromIntegrals();
            try {
                analyzer.fitRegions();
            } catch (Exception ex) {
                log.warn("Exception occured while fitting regions.", ex);
            }
            PeakList peakList = analyzer.getPeakList();
            List<String> peakListNames = new ArrayList<>();
            peakListNames.add(peakList.getName());
            chart.chartProps.setRegions(false);
            chart.chartProps.setIntegrals(true);
            chart.updatePeakLists(peakListNames);
        }
    }

    private void clearAnalysis(boolean prompt) {
        if (analyzer != null) {
            if (!prompt || affirm("Clear Analysis")) {
                PeakList peakList = analyzer.getPeakList();
                if (peakList != null) {
                    PeakList.remove(peakList.getName());
                }
                analyzer.clearRegions();
                chart.chartProps.setRegions(false);
                chart.chartProps.setIntegrals(false);
                chart.refresh();
            }
        }
    }

    private void clearThreshold() {
        if (analyzer != null) {
            analyzer.clearThreshold();
        }
    }

    private void setThreshold() {
        if (analyzer != null) {
            CrossHairs crossHairs = chart.getCrossHairs();
            if (!crossHairs.hasCrosshairState("h0")) {
                warn("Threshold", "Must have horizontal crosshair");
                return;
            }
            Double[] pos = crossHairs.getCrossHairPositions(0);
            log.info("{} {}", pos[0], pos[1]);
            analyzer.setThreshold(pos[1]);
        }
    }

    void deleteMultiplet() {
        if (analyzer != null) {
            activeMultiplet.ifPresent(m -> {
                double shift = m.getCenter();
                analyzer.removeRegion(shift);
            });
            refresh();
        }

    }

    public void initMultiplet() {
        getAnalyzer();
        if (analyzer == null) {
            log.warn("Analyzer is null, unable to init multiplet.");
            close();
        }
        if (!gotoSelectedMultiplet()) {
            List<Peak> peaks = getPeaks();
            if (!peaks.isEmpty()) {
                Multiplet m = peaks.get(0).getPeakDim(0).getMultiplet();
                activeMultiplet = Optional.of(m);
                updateMultipletField(false);
            }
        }
    }

    public boolean gotoSelectedMultiplet() {
        boolean result = false;
        List<MultipletSelection> multiplets = chart.getSelectedMultiplets();
        if (!multiplets.isEmpty()) {
            Multiplet m = multiplets.get(0).getMultiplet();
            activeMultiplet = Optional.of(m);
            updateMultipletField(false);
            result = true;
        }
        return result;
    }

    List<Peak> getPeaks() {
        List<Peak> peaks = Collections.EMPTY_LIST;
        Optional<PeakList> peakListOpt = getPeakList();
        if (peakListOpt.isPresent()) {
            PeakList peakList = peakListOpt.get();
            peaks = peakList.peaks();
        }
        return peaks;
    }

    void updateMultipletField() {
        updateMultipletField(true);
    }

    void updateMultipletField(boolean resetView) {
        if (activeMultiplet.isPresent()) {
            Multiplet multiplet = activeMultiplet.get();
            multipletIdField.setText(String.valueOf(multiplet.getIDNum()));
            if (resetView) {
                refreshPeakView(multiplet, false);
            }
            String mult = multiplet.getMultiplicity();
            Coupling coup = multiplet.getCoupling();
            updateCouplingChoices(coup);
            String peakType = Peak.typeToString(multiplet.getOrigin().getType());
            peakTypeChoice.setValue(peakType);
            double scale = multiplet.getOrigin().getPeakList().scale;
            double value = multiplet.getVolume() / scale;
            integralField.setText(String.format("%.2f", value));
//            if (multiplet.isGenericMultiplet()) {
//                splitButton.setDisable(true);
//            } else {
//                splitButton.setDisable(false);
//            }
        } else {
            multipletIdField.setText("");
        }
    }

    void setPeakType() {
        activeMultiplet.ifPresent(m -> {
            String peakType = peakTypeChoice.getValue();
            m.getOrigin().setType(Peak.getType(peakType));
        });

    }

    void clearPatternListener() {
        for (ChoiceBox<String> cBox : patternChoices) {
            cBox.valueProperty().removeListener(patternListener);
        }
    }

    void addPatternListener() {
        for (ChoiceBox<String> cBox : patternChoices) {
            cBox.valueProperty().addListener(patternListener);
        }
    }

    void clearCouplingChoices() {
        for (int i = 0; i < patternChoices.length; i++) {
            patternChoices[i].setValue("");
            couplingFields[i].setText("");
            slopeFields[i].setText("");
        }
        ignoreCouplingChanges = false;
    }

    void updateCouplingChoices(Coupling coup) {
        clearPatternListener();
        String[] couplingNames = {"", "s", "d", "t", "q", "p", "h"};
        clearCouplingChoices();
        if (coup instanceof ComplexCoupling) {
            patternChoices[0].setValue("m");
        } else if (coup instanceof CouplingPattern) {
            CouplingPattern couplingPattern = (CouplingPattern) coup;
            double[] values = couplingPattern.getValues();
            double[] slopes = couplingPattern.getSin2Thetas();
            int[] nCoup = couplingPattern.getNValues();
            for (int i = 0; i < values.length; i++) {
                couplingFields[i].setText(String.format("%.2f", values[i]));
                slopeFields[i].setText(String.format("%.2f", slopes[i]));
                patternChoices[i].setValue(couplingNames[nCoup[i]]);
            }
        } else if (coup instanceof Singlet) {
            patternChoices[0].setValue("s");
        }
        addPatternListener();
    }

    int getCurrentIndex() {
        int id = activeMultiplet.get().getPeakDim().getPeak().getIndex();
        return id;
    }

    void firstMultiplet(ActionEvent e) {
        List<Peak> peaks = getPeaks();
        if (!peaks.isEmpty()) {
            activeMultiplet = Optional.of(peaks.get(0).getPeakDim(0).getMultiplet());
        } else {
            activeMultiplet = Optional.empty();
        }
        updateMultipletField();
    }

    void previousMultiplet(ActionEvent e) {
        if (activeMultiplet.isPresent()) {
            int id = getCurrentIndex();
            id--;
            if (id < 0) {
                id = 0;
            }
            List<Peak> peaks = getPeaks();
            activeMultiplet = Optional.of(peaks.get(id).getPeakDim(0).getMultiplet());
            updateMultipletField();
        } else {
            firstMultiplet(e);
        }

    }

    void gotoPrevious(int id) {
        id--;
        if (id < 0) {
            id = 0;
        }
        List<Peak> peaks = getPeaks();
        activeMultiplet = Optional.of(peaks.get(id).getPeakDim(0).getMultiplet());
        updateMultipletField(false);

    }

    void nextMultiplet(ActionEvent e) {
        if (activeMultiplet.isPresent()) {
            List<Peak> peaks = getPeaks();
            int id = getCurrentIndex();
            int last = peaks.size() - 1;
            id++;
            if (id > last) {
                id = last;
            }
            activeMultiplet = Optional.of(peaks.get(id).getPeakDim(0).getMultiplet());
            updateMultipletField();
        } else {
            firstMultiplet(e);
        }
    }

    void lastMultiplet(ActionEvent e) {
        List<Peak> peaks = getPeaks();
        if (!peaks.isEmpty()) {
            activeMultiplet = Optional.of(peaks.get(peaks.size() - 1).getPeakDim(0).getMultiplet());
        }
        updateMultipletField();
    }

    void gotoPeakId(TextField textField) {

    }

    public Stage getStage() {
        return stage;
    }

    Optional<PeakList> getPeakList() {
        Optional<PeakList> peakListOpt = Optional.empty();
        List<PeakListAttributes> attrs = chart.getPeakListAttributes();
        if (!attrs.isEmpty()) {
            peakListOpt = Optional.of(attrs.get(0).getPeakList());
        } else {
            PeakList peakList = null;
            if (analyzer != null) {
                peakList = analyzer.getPeakList();
            }
            if (peakList != null) {
                List<String> peakListNames = new ArrayList<>();
                peakListNames.add(peakList.getName());
                chart.updatePeakLists(peakListNames);
                attrs = chart.getPeakListAttributes();
                peakListOpt = Optional.of(attrs.get(0).getPeakList());
            }
        }
        return peakListOpt;

    }

    PolyChart getChart() {
        FXMLController controller = FXMLController.getActiveController();
        PolyChart activeChart = controller.getActiveChart();
        return activeChart;
    }

    /**
     * Sets the chart with provided chart. If there is a previously set chart,
     * the listener wil be removed before setting the new chart. The multiplet listener
     * is added to the chart.
     * @param chart
     */
    void setChart(PolyChart chart) {
        if (this.chart != chart) {
            if (this.chart != null) {
                this.chart.removeMultipletListener(this);
            }
            this.chart = chart;
            this.chart.addMultipletListener(this);
            updateMultipletField();
        }
    }

    void refresh() {
        chart.refresh();
        updateMultipletField(false);

    }

    List<MultipletSelection> getMultipletSelection() {
        return chart.getSelectedMultiplets();
    }

    public void fitSelected() {
        if (analyzer != null) {
            activeMultiplet.ifPresent(m -> {
                Optional<Double> result = analyzer.fitMultiplet(m);
            });
            refresh();
        }
    }

    public void splitSelected() {
        activeMultiplet.ifPresent(m -> {
            if (m.isGenericMultiplet()) {
            } else {
                Multiplets.splitToMultiplicity(m, "d");
                Multiplets.updateAfterMultipletConversion(m);
            }
        });
        refresh();
    }

    public void splitRegion() {
        if (analyzer == null) {
            return;
        }
        double ppm = chart.getVerticalCrosshairPositions()[0];
        try {
            List<Multiplet> multiplets = analyzer.splitRegion(ppm);
            if (!multiplets.isEmpty()) {
                activeMultiplet = Optional.of(multiplets.get(0));
            } else {
                activeMultiplet = Optional.empty();
            }
            updateMultipletField(false);
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }
        chart.refresh();
    }

    public void adjustRegion() {
        if (analyzer == null) {
            return;
        }
        double ppm0 = chart.getVerticalCrosshairPositions()[0];
        double ppm1 = chart.getVerticalCrosshairPositions()[1];
        analyzer.removeRegion(ppm0, ppm1);
        analyzer.addRegion(ppm0, ppm1);
        try {
            activeMultiplet = analyzer.analyzeRegion((ppm0 + ppm1) / 2);
            updateMultipletField(false);
            chart.refresh();
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }
    }

    public void addRegion(DatasetRegion region) {
        if (analyzer == null) {
            return;
        }
        double ppm0;
        double ppm1;
        if (region == null) {
            ppm0 = chart.getVerticalCrosshairPositions()[0];
            ppm1 = chart.getVerticalCrosshairPositions()[1];
            analyzer.addRegion(ppm0, ppm1);
        } else {
            ppm0 = region.getRegionStart(0);
            ppm1 = region.getRegionEnd(0);
            analyzer.peakPickRegion(ppm0, ppm1);

        }
        try {
            activeMultiplet = analyzer.analyzeRegion((ppm0 + ppm1) / 2);
            // this will force updating peaklist and adding to chart if not there
            Optional<PeakList> peakListOpt = getPeakList();
            if (peakListOpt.isPresent()) {
                PeakList peakList = peakListOpt.get();
                if (peakList.peaks().size() == 1) {
                    double volume = activeMultiplet.get().getVolume();
                    peakList.scale = volume;
                }
            }
            updateMultipletField(false);
            chart.refresh();

        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }
    }

    public void removeRegion() {
        if (analyzer == null) {
            return;
        }
        activeMultiplet.ifPresent(m -> {
            int id = m.getIDNum();
            double ppm = m.getCenter();
            analyzer.removeRegion(ppm);
            gotoPrevious(id);
            chart.refresh();
        });
    }

    public void rms() {
        if (analyzer == null) {
            return;
        }
        activeMultiplet.ifPresent(m -> {
            Optional<Double> result = Multiplets.rms(m);
            if (result.isPresent()) {
                log.info("rms {}", result.get());
            }
        });
    }

    public void objectiveDeconvolution() {
        if (analyzer == null) {
            return;
        }
        activeMultiplet.ifPresent(m -> {
            analyzer.objectiveDeconvolution(m);
            chart.refresh();
            refresh();
        });
    }

    public void addAuto() {
        if (analyzer == null) {
            return;
        }
        activeMultiplet.ifPresent(m -> {
            Optional<Double> result = Multiplets.deviation(m);
            if (result.isPresent()) {
                log.info("dev pos {}", result.get());
                Multiplets.addPeaksToMultiplet(m, result.get());
                analyzer.fitMultiplet(m);
                chart.refresh();
                refresh();

            }

        });

    }

    public void addPeak() {
        addPeaks(false);
    }

    public void addTwoPeaks() {
        addPeaks(true);
    }

    public void addPeaks(boolean both) {
        if (analyzer == null) {
            return;
        }
        activeMultiplet.ifPresent(m -> {
            double ppm1 = chart.getVerticalCrosshairPositions()[0];
            double ppm2 = chart.getVerticalCrosshairPositions()[1];
            if (both) {
                Multiplets.addPeaksToMultiplet(m, ppm1, ppm2);
            } else {
                Multiplets.addPeaksToMultiplet(m, ppm1);

            }
            analyzer.fitMultiplet(m);
            chart.refresh();
            refresh();
        });
    }

    void removeWeakPeak() {
        activeMultiplet.ifPresent(m -> {
            Multiplets.removeWeakPeaksInMultiplet(m, 1);
            refresh();
        });
    }

    public void toDoublets() {
        activeMultiplet.ifPresent(Multiplets::toDoublets);
        refresh();
    }

    public void guessGeneric() {
        activeMultiplet.ifPresent(Multiplets::guessMultiplicityFromGeneric);
        refresh();
    }

    List<Multiplet> getSelMultiplets(List<MultipletSelection> mSet) {
        List<Multiplet> multiplets = new ArrayList<>();
        for (MultipletSelection mSel : mSet) {
            if (!multiplets.contains(mSel.getMultiplet())) {
                multiplets.add(mSel.getMultiplet());
            }
        }
        return multiplets;
    }

    public void extractMultiplet() {
        List<MultipletSelection> mSet = chart.getSelectedMultiplets();
        List<Multiplet> multiplets = getSelMultiplets(mSet);
        if (multiplets.size() > 1) {

        } else if (!multiplets.isEmpty()) {

            Multiplet multiplet = multiplets.get(0);
            List<AbsMultipletComponent> comps1 = new ArrayList<>();
            List<AbsMultipletComponent> comps2 = new ArrayList<>();

            List<AbsMultipletComponent> comps = multiplet.getAbsComponentList();

            for (MultipletSelection mSel : mSet) {
                comps1.add(comps.get(mSel.getLine()));
            }
            for (AbsMultipletComponent comp : comps) {
                if (!comps1.contains(comp)) {
                    comps2.add(comp);
                }
            }
            PeakList peakList = multiplet.getPeakList();
            multiplet.updateCoupling(comps1);
            Peak newPeak = multiplet.getOrigin().copy(peakList);
            peakList.addPeak(newPeak);
            Multiplet newMultiplet = newPeak.getPeakDim(0).getMultiplet();
            newMultiplet.updateCoupling(comps2);
            refresh();
        }
    }

    public void transferPeaks() {
        List<Peak> peaks = chart.getSelectedPeaks();
        if (!peaks.isEmpty()) {
            activeMultiplet.ifPresent(m -> activeMultiplet = Multiplets.transferPeaks(m, peaks));
            refresh();
        }
    }

    public void mergePeaks() {
        List<Peak> peaks = chart.getSelectedPeaks();
        if (!peaks.isEmpty()) {
            activeMultiplet = Multiplets.mergePeaks(peaks);
            refresh();
        }
    }

    public void refreshPeakView(Multiplet multiplet, boolean resize) {
        if (multiplet != null) {
            double bounds = multiplet.getBoundsValue();
            double center = multiplet.getCenter();
            double widthScale = 2.5;
            if ((chart != null) && !chart.getDatasetAttributes().isEmpty()) {
                DatasetAttributes dataAttr = (DatasetAttributes) chart.getDatasetAttributes().get(0);
                Double[] ppms = {center};
                Double[] widths = {bounds * widthScale};
                if (resize && (widthScale > 0.0)) {
                    chart.moveTo(ppms, widths);
                } else {
                    chart.moveTo(ppms);
                }
            }
        }
    }

    private void couplingChanged() {
        if (analyzer == null) {
            return;
        }
        activeMultiplet.ifPresent(m -> {
            StringBuilder sBuilder = new StringBuilder();
            for (ChoiceBox<String> choice : patternChoices) {
                String value = choice.getValue().trim();
                if (value.length() > 0) {
                    sBuilder.append(value);
                }
            }
            String multNew = sBuilder.toString();

            String multOrig = m.getMultiplicity();
            log.info("convert {} {}", multOrig, multNew);
            if (!multNew.equals(multOrig)) {
                Multiplets.convertMultiplicity(m, multOrig, multNew);
                analyzer.fitMultiplet(m);
                refresh();
            }
        });
    }

    private void couplingValueTyped(int iRow) {
        if (activeMultiplet.isPresent()) {
            String couplingStr = couplingFields[iRow].getText();
            String couplingType = patternChoices[iRow].getValue();
            if ((couplingType != null) && !couplingType.equals("")) {
                try {
                    double newValue = Double.parseDouble(couplingStr);
                    if (newValue > 0.1) {
                        Multiplet multiplet = activeMultiplet.get();
                        Coupling coupling = multiplet.getCoupling();
                        if (coupling instanceof CouplingPattern) {
                            CouplingPattern cPattern = (CouplingPattern) coupling;
                            cPattern.adjustCouplings(iRow, newValue);
                        }
                        refresh();
                    }
                } catch (NumberFormatException nfE) {
                    log.warn("Unable to parse new coupling value.", nfE);

                }
            }
        }
    }

    @Override
    public void onChanged(Change<? extends MultipletSelection> change) {
        ObservableSet<MultipletSelection> mSet = (ObservableSet<MultipletSelection>) change.getSet();
        boolean allreadyPresent = false;
        if (!mSet.isEmpty()) {
            if (activeMultiplet.isPresent()) {
                for (MultipletSelection mSel : mSet) {
                    if (mSel.getMultiplet() == activeMultiplet.get()) {
                        // current active multiplet in selection so don't change anything
                        allreadyPresent = true;
                        break;
                    }
                }

            }
            if (!allreadyPresent) {
                mSet.stream().findFirst().ifPresent(mSel -> {
                    activeMultiplet = Optional.of(mSel.getMultiplet());
                    updateMultipletField();
                });
            }
        }
    }

    public void setActivePeaks(List<Peak> peaks) {
        if ((peaks != null) && !peaks.isEmpty()) {
            Peak peak = peaks.get(0);
            activeMultiplet = Optional.of(peak.getPeakDim(0).getMultiplet());
            updateMultipletField(false);
        }
    }

    public void journalFormatToClipboard() {
        JournalFormat format = JournalFormatPeaks.getFormat("JMedCh");
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

    public void toggleMoleculeDisplay() {
        if (molButton.isSelected()) {
            addMolecule();
        } else {
            removeMolecule();
        }
    }

    void addMolecule() {
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

    public void toggleJournalFormatDisplay() {
        if (journalCheckBox.isSelected()) {
            showJournalFormatOnChart();
        } else {
            removeJournalFormatOnChart();
        }
    }

    public void showJournalFormatOnChart() {
        if (analyzer != null) {
            PeakList peakList = analyzer.getPeakList();
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
        if (analyzer == null) {
            return;
        }
        PeakList peakList = analyzer.getPeakList();
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
}
