/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.gui.tools;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.controlsfx.control.PopOver;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.peaks.Analyzer;
import org.nmrfx.analyst.peaks.Multiplets;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.peaks.*;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.IconUtilities;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.MultipletSelection;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.processor.gui.spectra.crosshair.CrossHairs;
import org.nmrfx.processor.gui.undo.PeakListUndo;
import org.nmrfx.processor.gui.undo.PeakUndo;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author brucejohnson
 */
public class MultipletTool implements SetChangeListener<MultipletSelection> {
    private static final Logger log = LoggerFactory.getLogger(MultipletTool.class);

    Stage stage = null;
    VBox vBox;
    TextField multipletIdField;
    HBox typeToolBar = new HBox();
    GridPane gridPane = new GridPane();
    ChoiceBox<String> peakTypeChoice;
    ChoiceBox<String>[] patternChoices;
    TextField integralField;
    TextField[] couplingFields;
    TextField[] slopeFields;
    FXMLController controller;
    CheckBox restraintPosCheckBox;
    Slider restraintSlider;
    Button mergeButton;
    Button extractButton;
    Button transferButton;

    private final PolyChart chart;
    Optional<Multiplet> activeMultiplet = Optional.empty();
    boolean ignoreCouplingChanges = false;
    ChangeListener<String> patternListener;
    PopOver popOver = null;

    private MultipletTool(PolyChart chart) {
        this.chart = chart;
        this.controller = chart.getFXMLController();
    }

    public VBox getBox() {
        return vBox;
    }

    public boolean popoverInitialized() {
        return vBox != null;
    }

    public static MultipletTool getTool(PolyChart chart) {
        MultipletTool multipletTool = (MultipletTool) chart.getPopoverTool(MultipletTool.class.getName());
        if (multipletTool == null) {
            multipletTool = new MultipletTool(chart);
            chart.setPopoverTool(MultipletTool.class.getName(), multipletTool);
        }
        return multipletTool;
    }

    public void initializePopover(PopOver popOver) {
        this.vBox = new VBox();
        vBox.setPadding(new Insets(0, 1, 0, 1));
        ToolBar topBar = new ToolBar();
        initIntegralType(topBar);
        initCouplingFields(Orientation.VERTICAL, 3);
        ToolBar buttonBar1 = new ToolBar();
        ToolBar buttonBar2 = new ToolBar();
        initBasicButtons(buttonBar1, buttonBar2);
        HBox hbox = new HBox();
        hbox.setMinHeight(10);
        HBox.setHgrow(hbox, Priority.ALWAYS);

        vBox.getChildren().addAll(hbox, topBar, gridPane, buttonBar1, buttonBar2);
        chart.addMultipletListener(this);
        getAnalyzer();
        chart.setOnRegionAdded(this::regionAdded);
        patternListener = (ObservableValue<? extends String> observable, String oldValue, String newValue) -> couplingChanged();
        addPatternListener();
        restraintPosCheckBox = new CheckBox("Restraint");
        restraintSlider = new Slider(0.02, 2.0, 1.0);
        popOver.setContentNode(vBox);
        this.popOver = popOver;
    }


    private void updateCouplingGrid(int nCouplings) {
        if (nCouplings + 1 != patternChoices.length) {
            initCouplingFields(Orientation.VERTICAL, nCouplings + 1);
        }
    }

    private void initCouplingFields(Orientation orientation, int nCouplings) {
        String[] patterns = {"d", "t", "q", "p", "h", "dd", "ddd", "dddd"};
        double width2 = 80;
        double width3 = 60;
        gridPane.getChildren().clear();
        patternChoices = new ChoiceBox[nCouplings];
        couplingFields = new TextField[nCouplings];
        slopeFields = new TextField[nCouplings];
        for (int iCoupling = 0; iCoupling < nCouplings; iCoupling++) {
            patternChoices[iCoupling] = new ChoiceBox<>();
            patternChoices[iCoupling].setPrefWidth(width2);
            if (iCoupling == 0) {
                patternChoices[iCoupling].getItems().add("");
                patternChoices[iCoupling].getItems().add("m");
                patternChoices[iCoupling].getItems().add("s");
            } else {
                patternChoices[iCoupling].getItems().add("");
            }
            patternChoices[iCoupling].getItems().addAll(patterns);
            patternChoices[iCoupling].setValue(patternChoices[iCoupling].getItems().get(0));
            couplingFields[iCoupling] = new TextField();
            slopeFields[iCoupling] = new TextField();
            couplingFields[iCoupling].setPrefWidth(width3);
            final int couplingIndex = iCoupling;
            couplingFields[iCoupling].setOnKeyReleased(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    couplingValueTyped(couplingIndex);
                }
            });
            slopeFields[iCoupling].setPrefWidth(width3);
            int iRow = orientation == Orientation.HORIZONTAL ? 0 : iCoupling;
            int iColumn = orientation == Orientation.HORIZONTAL ? iCoupling * 3 : 0;

            gridPane.add(patternChoices[iCoupling], iColumn, iRow);
            gridPane.add(couplingFields[iCoupling], iColumn + 1, iRow);
            gridPane.add(slopeFields[iCoupling], iColumn + 2, iRow);
        }
    }

    public void regionAdded(DatasetRegion region) {
        addRegion(region);
    }

    void initBasicButtons(ToolBar toolBar1, ToolBar toolBar2) {
        Button button;
        List<Button> peakButtons = new ArrayList<>();
        List<Button> multipletButtons = new ArrayList<>();

        button = new Button("AutoAdd", IconUtilities.getIcon("peak_auto"));
        button.setOnAction(e -> addAuto());
        peakButtons.add(button);

        button = new Button("Delete", IconUtilities.getIcon("editdelete"));
        button.setOnAction(e -> removeWeakPeak());
        peakButtons.add(button);

        button = new Button("Split", IconUtilities.getIcon("region_split"));
        button.setOnAction(e -> split());
        peakButtons.add(button);

        button = new Button("Adjust", IconUtilities.getIcon("region_adjust"));
        button.setOnAction(e -> adjustRegion());
        peakButtons.add(button);


        Button doubletButton = new Button("Doublets", IconUtilities.getIcon("tree"));
        doubletButton.setOnAction(e -> toDoublets());
        multipletButtons.add(doubletButton);

        button = new Button("Fit", IconUtilities.getIcon("reload"));
        button.setOnAction(e -> fitSelected());
        peakButtons.add(button);

        extractButton = new Button("Extract", IconUtilities.getIcon("extract"));
        extractButton.setOnAction(e -> extractMultiplet());
        multipletButtons.add(extractButton);

        mergeButton = new Button("Merge", IconUtilities.getIcon("merge"));
        mergeButton.setOnAction(e -> mergePeaks());
        multipletButtons.add(mergeButton);

        transferButton = new Button("Transfer", IconUtilities.getIcon("transfer"));
        transferButton.setOnAction(e -> transferPeaks());
        multipletButtons.add(transferButton);
        for (Button button1 : peakButtons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setStyle("-fx-font-size:" + AnalystApp.ICON_FONT_SIZE_STR);
            button1.getStyleClass().add("toolButton");
            toolBar1.getItems().add(button1);
        }
        for (Button button1 : multipletButtons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setStyle("-fx-font-size:" + AnalystApp.ICON_FONT_SIZE_STR);
            button1.getStyleClass().add("toolButton");
            toolBar2.getItems().add(button1);
        }

    }

    public MenuButton makeNormalizeMenu() {
        MenuButton integralMenu = new MenuButton("N");
        int[] norms = {1, 2, 3, 4, 5, 6, 9, 100};

        for (var norm : norms) {
            MenuItem menuItem = new MenuItem(String.valueOf(norm));
            integralMenu.getItems().add(menuItem);
            menuItem.setOnAction(e -> setPeakN(norm));
        }
        return integralMenu;
    }

    void setPeakNToValue() {
        String normString = GUIUtils.input("Integral Norm Value");
        double norm;
        try {
            norm = Double.parseDouble(normString);
        } catch (NumberFormatException ignored) {
            return;
        }
        setPeakN(norm);
    }

    void setPeakN(double value) {
        activeMultiplet.ifPresent(m -> {
            double volume = m.getVolume();
            PeakList peakList = m.getOrigin().getPeakList();
            peakList.scale = volume / value;
            refresh();
        });
    }

    void initIntegralType(ToolBar toolBar) {
        integralField = new TextField();
        integralField.setPrefWidth(50);
        integralField.setOnKeyReleased(k -> {
            if (k.getCode() == KeyCode.ENTER) {
                try {
                    double value = Double.parseDouble(integralField.getText().trim());
                    setPeakN(value);
                } catch (NumberFormatException nfE) {
                    log.warn("Unable to parse integral field.", nfE);
                }

            }
        });

        MenuButton normButton = makeNormalizeMenu();
        peakTypeChoice = new ChoiceBox<>();
        typeToolBar.getChildren().addAll(peakTypeChoice);
        peakTypeChoice.getItems().addAll(Peak.getPeakTypes());
        peakTypeChoice.valueProperty().addListener(e -> setPeakType());
        peakTypeChoice.setPrefWidth(100);
        toolBar.getItems().addAll(normButton, integralField);
        toolBar.getItems().addAll(peakTypeChoice);
    }

    public Analyzer getAnalyzer() {
        Dataset dataset = (Dataset) chart.getDataset();
        if ((dataset == null) || (dataset.getNFreqDims() > 1)) {
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

    void updateMultipletField() {
        updateMultipletField(true);
    }

    void updateMultipletField(boolean resetView) {
        getAnalyzer();
        if (activeMultiplet.isPresent()) {
            Multiplet multiplet = activeMultiplet.get();
            if (multipletIdField != null) {
                multipletIdField.setText(String.valueOf(multiplet.getIDNum()));
                if (resetView) {
                    refreshPeakView(multiplet, false);
                }
            }
            Coupling coup = multiplet.getCoupling();
            updateCouplingChoices(coup);
            mergeButton.setDisable(!mergeable());
            transferButton.setDisable(!transferable());
            extractButton.setDisable(!extractable());
            String peakType = Peak.typeToString(multiplet.getOrigin().getType());
            peakTypeChoice.setValue(peakType);
            double scale = multiplet.getOrigin().getPeakList().scale;
            double value = multiplet.getVolume() / scale;
            integralField.setText(String.format("%.2f", value));
        } else {
            if (multipletIdField != null) {
                multipletIdField.setText("");
            }
        }
    }

    void setPeakType() {
        getAnalyzer();
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
            cBox.valueProperty().removeListener(patternListener);
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
        if (coup instanceof ComplexCoupling) {
            updateCouplingGrid(1);
            clearCouplingChoices();
            patternChoices[0].setValue("m");
        } else if (coup instanceof CouplingPattern) {
            CouplingPattern couplingPattern = (CouplingPattern) coup;
            double[] values = couplingPattern.getValues();
            double[] slopes = couplingPattern.getSin2Thetas();
            int[] nCoup = couplingPattern.getNValues();
            int nCouplings = values.length;
            updateCouplingGrid(nCouplings);
            clearCouplingChoices();
            for (int i = 0; i < nCouplings; i++) {
                couplingFields[i].setText(String.format("%.2f", values[i]));
                slopeFields[i].setText(String.format("%.2f", slopes[i]));
                patternChoices[i].setValue(couplingNames[nCoup[i]]);
            }
        } else if (coup instanceof Singlet) {
            updateCouplingGrid(1);
            clearCouplingChoices();
            patternChoices[0].setValue("s");
        }
        addPatternListener();
    }

    public void setActiveMultiplet(Multiplet multiplet) {
        activeMultiplet = Optional.of(multiplet);
        updateMultipletField(false);
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
            Analyzer analyzer = getAnalyzer();
            if (analyzer != null) {
                PeakList peakList = analyzer.getPeakList();
                if (peakList != null) {
                    List<String> peakListNames = new ArrayList<>();
                    peakListNames.add(peakList.getName());
                    chart.updatePeakListsByName(peakListNames);
                    attrs = chart.getPeakListAttributes();
                    peakListOpt = Optional.of(attrs.get(0).getPeakList());
                }
            }
        }
        return peakListOpt;

    }

    void refresh() {
        chart.refresh();
        updateMultipletField(false);

    }

    public void fitSelected() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            activeMultiplet.ifPresent(m -> {
                PeakUndo undo = new PeakUndo(m.getOrigin());
                Optional<Double> result = analyzer.fitMultiplet(m);
                PeakUndo redo = new PeakUndo(m.getOrigin());
                controller.getUndoManager().add("Fit Multiplet", undo, redo);

            });
            refresh();
        }
    }

    private void restrainPosition() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            analyzer.setPositionRestraint(restraintPosCheckBox.isSelected()
                    ? restraintSlider.getValue() : null);
        }
    }

    public void split() {
        CrossHairs crossHairs = chart.getCrossHairs();
        PeakListUndo undo = getPeakListUndo();
        if (crossHairs.hasState("v0")) {
            splitMultipletRegion();
        } else {
            if (getAnalyzer().getPeakList() != null) {
                splitMultiplet();
            }
        }
        PeakListUndo redo = getPeakListUndo();
        if ((undo != null) && (redo != null)) {
            controller.getUndoManager().add("Split", undo, redo);
        }

        activeMultiplet = Optional.empty();
        if (popOver != null) {
            popOver.hide();
        }
    }

    public void splitMultipletRegion() {
        double ppm = chart.getCrossHairs().getVerticalPositions()[0];
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            try {
                List<Multiplet> multiplets = analyzer.splitRegion(ppm);
                if (!multiplets.isEmpty()) {
                    activeMultiplet = Optional.of(multiplets.get(0));
                } else {
                    activeMultiplet = Optional.empty();
                }
                if (getAnalyzer().getPeakList() != null) {
                    updateMultipletField(false);
                }
            } catch (IOException ex) {
                log.error("Failure to split region", ex);
            }
            chart.refresh();
        }
    }

    public void splitMultiplet() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            if (activeMultiplet.isPresent()) {
                Multiplets.findMultipletMidpoint(activeMultiplet.get()).ifPresent(ppmCenter -> {
                    try {
                        activeMultiplet.get().setGenericMultiplet();
                        List<Multiplet> multiplets = analyzer.splitRegion(ppmCenter);
                        if (!multiplets.isEmpty()) {
                            activeMultiplet = Optional.of(multiplets.get(0));
                        } else {
                            activeMultiplet = Optional.empty();
                        }
                        updateMultipletField(false);
                        chart.refresh();
                    } catch (IOException ex) {
                    }
                });
            }
        }
    }

    public void adjustRegion() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            double ppm0;
            double ppm1;
            if (chart.getCrossHairs().hasState("||")) {
                ppm0 = chart.getCrossHairs().getVerticalPositions()[0];
                ppm1 = chart.getCrossHairs().getVerticalPositions()[1];
            } else {
                if (activeMultiplet.isPresent()) {
                    Multiplet multiplet = activeMultiplet.get();
                    var optRegion = analyzer.getRegion(multiplet.getCenter());
                    if (optRegion.isPresent()) {
                        var region = optRegion.get();
                        ppm0 = region.getRegionStart(0);
                        ppm1 = region.getRegionEnd(0);
                    } else {
                        double[] minMax = Multiplets.getBoundsOfMultiplet(multiplet, analyzer.getTrimRatio());
                        ppm0 = minMax[0];
                        ppm1 = minMax[1];
                    }
                } else {
                    return;
                }
            }
            analyzer.removeRegion(ppm0, ppm1, true);
            analyzer.addRegion(ppm0, ppm1, true);

            PeakListUndo undo = getPeakListUndo();
            PeakListUndo redo = null;
            try {
                activeMultiplet = analyzer.analyzeRegion((ppm0 + ppm1) / 2);
                updateMultipletField(false);
                if (undo != null) {
                    redo = getPeakListUndo();
                }
                chart.refresh();
                if ((undo != null) && (redo != null)) {
                    controller.getUndoManager().add("Update regions ", undo, redo);
                }
            } catch (IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }
    }

    PeakListUndo getPeakListUndo() {
        PeakListUndo undo = null;
        if (getPeakList().isPresent()) {
            undo = new PeakListUndo(getPeakList().get());
        }
        return undo;
    }

    public void mergeRegion(List<Peak> peaks) {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            double ppmMin = Double.MAX_VALUE;
            double ppmMax = Double.NEGATIVE_INFINITY;
            for (Peak peak : peaks) {
                Multiplet multiplet = peak.getPeakDim(0).getMultiplet();
                var optRegion = analyzer.getRegion(multiplet.getCenter());
                double ppm0;
                double ppm1;
                if (optRegion.isPresent()) {
                    var region = optRegion.get();
                    ppm0 = region.getRegionStart(0);
                    ppm1 = region.getRegionEnd(0);
                } else {
                    double[] minMax = Multiplets.getBoundsOfMultiplet(multiplet, analyzer.getTrimRatio());
                    ppm0 = minMax[0];
                    ppm1 = minMax[1];
                }
                analyzer.removeRegion(ppm0, ppm1, false);
                ppmMin = Math.min(ppmMin, ppm0);
                ppmMax = Math.max(ppmMax, ppm1);
            }
            analyzer.addRegion(ppmMin, ppmMax, false);
        }
    }


    public void addRegion(DatasetRegion region) {
        Analyzer analyzer = getAnalyzer();
        if (chart.getPeakListAttributes().isEmpty()) {
            return;
        }
        if (analyzer != null) {
            double ppm0;
            double ppm1;
            PeakListUndo undo = getPeakListUndo();
            if (region == null) {
                ppm0 = chart.getCrossHairs().getVerticalPositions()[0];
                ppm1 = chart.getCrossHairs().getVerticalPositions()[1];
                analyzer.addRegion(ppm0, ppm1, true);
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
                        peakList.scale = activeMultiplet.get().getVolume();
                    }
                }
                PeakListUndo redo = getPeakListUndo();
                if ((undo != null) && (redo != null)) {
                    controller.getUndoManager().add("Add Region", undo, redo);
                }

                updateMultipletField(false);
                chart.refresh();

            } catch (IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }
    }

    public void rms() {
        activeMultiplet.ifPresent(m -> {
            Optional<Double> result = Multiplets.rms(m);
            if (result.isPresent()) {
            }
        });
    }

    public void objectiveDeconvolution() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            activeMultiplet.ifPresent(m -> {
                analyzer.objectiveDeconvolution(m);
                chart.refresh();
                refresh();
            });
        }
    }

    public void addAuto() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            activeMultiplet.ifPresent(m -> {
                Optional<Double> result = Multiplets.deviation(m);
                if (result.isPresent()) {
                    PeakUndo undo = new PeakUndo(m.getOrigin());
                    Multiplets.addPeaksToMultiplet(m, result.get());
                    analyzer.fitMultiplet(m);
                    PeakUndo redo = new PeakUndo(m.getOrigin());
                    controller.getUndoManager().add("Auto Add Peak", undo, redo);
                    chart.refresh();
                    refresh();

                }
            });
        }

    }

    public void addPeak() {
        addPeaks(false);
    }

    public void addTwoPeaks() {
        addPeaks(true);
    }

    public void addPeaks(boolean both) {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            activeMultiplet.ifPresent(m -> {
                PeakUndo undo = new PeakUndo(m.getOrigin());
                double ppm1 = chart.getCrossHairs().getVerticalPositions()[0];
                double ppm2 = chart.getCrossHairs().getVerticalPositions()[1];
                if (both) {
                    Multiplets.addPeaksToMultiplet(m, ppm1, ppm2);
                } else {
                    Multiplets.addPeaksToMultiplet(m, ppm1);

                }
                PeakUndo redo = new PeakUndo(m.getOrigin());
                controller.getUndoManager().add("Add Peaks", undo, redo);
                analyzer.fitMultiplet(m);
                chart.refresh();
                refresh();
            });
        }
    }

    void removeWeakPeak() {
        activeMultiplet.ifPresent(m -> {
            Multiplets.removeWeakPeaksInMultiplet(m, 1);
            refresh();
        });
    }

    public void toDoublets() {
        activeMultiplet.ifPresent(m -> {
            PeakUndo undo = new PeakUndo(m.getOrigin());
            Multiplets.toDoublets(m);
            PeakUndo redo = new PeakUndo(m.getOrigin());
            controller.getUndoManager().add("Multiplets to Doublets", undo, redo);

        });
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

    public Multiplet extractMultiplet(List<MultipletSelection> mSet, Multiplet multiplet) {
        List<AbsMultipletComponent> comps1 = new ArrayList<>();
        List<AbsMultipletComponent> comps2 = new ArrayList<>();

        List<AbsMultipletComponent> comps = multiplet.getAbsComponentList();

        for (MultipletSelection mSel : mSet) {
            if (mSel.isLine()) {
                comps1.add(comps.get(mSel.getLine()));
            }
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
        return multiplet;
    }

    private boolean extractable() {
        if (activeMultiplet.isPresent()) {
            var m = activeMultiplet.get();
            return extractable(m);
        } else {
            return false;
        }
    }

    private boolean extractable(Multiplet m) {
        boolean result = false;
        if (m.isGenericMultiplet()) {
            List<MultipletSelection> mSet = chart.getSelectedMultiplets();
            List<Multiplet> multiplets = getSelMultiplets(mSet);
            if (multiplets.size() == 1) {
                var extractMultiplet = multiplets.get(0);
                if (extractMultiplet == m) {
                    List<AbsMultipletComponent> comps = m.getAbsComponentList();
                    List<AbsMultipletComponent> comps1 = new ArrayList<>();
                    for (MultipletSelection mSel : mSet) {
                        if (mSel.isLine()) {
                            comps1.add(comps.get(mSel.getLine()));
                        }
                    }
                    result = !comps1.isEmpty() && comps.size() > comps1.size();
                }
            }
        }

        return result;
    }

    public void extractMultiplet() {
        if (extractable()) {
            List<MultipletSelection> mSet = chart.getSelectedMultiplets();
            List<Multiplet> multiplets = getSelMultiplets(mSet);
            if (multiplets.size() == 1) {
                Multiplet multiplet = multiplets.get(0);
                PeakListUndo undo = getPeakListUndo();
                extractMultiplet(mSet, multiplet);
                activeMultiplet = Optional.empty();
                PeakListUndo redo = getPeakListUndo();
                if ((undo != null) && (redo != null)) {
                    controller.getUndoManager().add("Add Region", undo, redo);
                }

                chart.clearSelectedMultiplets();
                if (popOver != null) {
                    popOver.hide();
                }
                refresh();
            }
        }
    }


    public List<Multiplet> getTransferMultiplets() {
        List<MultipletSelection> mSet = chart.getSelectedMultiplets();
        List<Multiplet> multiplets = getSelMultiplets(mSet);
        return multiplets;
    }

    private boolean transferable() {
        boolean result = false;
        if (activeMultiplet.isPresent()) {
            List<Multiplet> multiplets = getTransferMultiplets();
            if (multiplets.size() == 1) {
                Multiplet multiplet = multiplets.get(0);
                if (multiplet != activeMultiplet.get()) {
                    result = extractable(multiplet);
                }
            }
        }
        return result;
    }


    public void transferPeaks() {
        List<Multiplet> multiplets = getTransferMultiplets();

        if (multiplets.size() == 1) {
            Multiplet multiplet = multiplets.get(0);
            List<MultipletSelection> mSet = chart.getSelectedMultiplets();
            PeakListUndo undo = getPeakListUndo();
            Multiplet newMultiplet = extractMultiplet(mSet, multiplet);
            activeMultiplet.ifPresent(m -> activeMultiplet = Multiplets.transferPeaks(m, List.of(newMultiplet.getPeakDim().getPeak())));
            PeakListUndo redo = getPeakListUndo();
            if ((undo != null) && (redo != null)) {
                controller.getUndoManager().add("Add Region", undo, redo);
            }

            chart.clearSelectedMultiplets();
            if (popOver != null) {
                popOver.hide();
            }

        }
        refresh();
    }

    private List<Peak> getMergePeaks(Peak activePeak) {
        List<Peak> peaks = chart.getSelectedPeaks();
        List<MultipletSelection> mSet = chart.getSelectedMultiplets();
        List<Multiplet> multiplets = getSelMultiplets(mSet);
        for (Multiplet multiplet : multiplets) {
            if (!peaks.contains(multiplet.getOrigin())) {
                peaks.add(multiplet.getOrigin());
            }
        }
        if (!peaks.contains(activePeak)) {
            peaks.add(activePeak);
        }
        return peaks;
    }

    private boolean mergeable() {
        if (activeMultiplet.isPresent()) {
            var m = activeMultiplet.get();
            return getMergePeaks(m.getOrigin()).size() > 1;
        } else {
            return false;
        }
    }

    public void mergePeaks() {
        activeMultiplet.ifPresent(m -> {
            List<Peak> peaks = getMergePeaks(m.getOrigin());
            if (peaks.size() > 1) {
                mergeRegion(peaks);
                Multiplets.mergePeaks(peaks);
                activeMultiplet = Optional.empty();
                chart.clearSelectedMultiplets();
                if (popOver != null) {
                    popOver.hide();
                }
                refresh();
            }
        });
    }

    public void refreshPeakView(Multiplet multiplet, boolean resize) {
        if (multiplet != null) {
            double bounds = multiplet.getBoundsValue();
            double center = multiplet.getCenter();
            double widthScale = 2.5;
            if ((chart != null) && !chart.getDatasetAttributes().isEmpty()) {
                DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
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
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
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
                if (!multNew.equals(multOrig)) {
                    PeakUndo undo = new PeakUndo(m.getOrigin());
                    Multiplets.convertMultiplicity(m, multOrig, multNew);
                    analyzer.fitMultiplet(m);
                    PeakUndo redo = new PeakUndo(m.getOrigin());
                    controller.getUndoManager().add("Change Multiplicity", undo, redo);
                    refresh();
                }
            });
        }
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
                        PeakUndo undo = new PeakUndo(multiplet.getOrigin());
                        Coupling coupling = multiplet.getCoupling();
                        if (coupling instanceof CouplingPattern) {
                            CouplingPattern cPattern = (CouplingPattern) coupling;
                            cPattern.adjustCouplings(iRow, newValue);
                        }
                        PeakUndo redo = new PeakUndo(multiplet.getOrigin());
                        controller.getUndoManager().add("Change Multiplicity", undo, redo);
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
}
