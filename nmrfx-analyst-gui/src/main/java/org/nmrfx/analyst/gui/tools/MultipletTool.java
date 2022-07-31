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
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.controlsfx.control.PopOver;
import org.nmrfx.analyst.peaks.Analyzer;
import org.nmrfx.analyst.peaks.Multiplets;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.peaks.*;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.CrossHairs;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.MultipletSelection;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

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
    Consumer<MultipletTool> closeAction;
    CheckBox restraintPosCheckBox;
    Slider restraintSlider;


    private PolyChart chart;
    Optional<Multiplet> activeMultiplet = Optional.empty();
    boolean ignoreCouplingChanges = false;
    ChangeListener<String> patternListener;
    PopOver popOver = null;

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

    public void initializePopover(PopOver popOver) {
        this.vBox = new VBox();
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
        chart = controller.getActiveChart();
        chart.addMultipletListener(this);
        getAnalyzer();
        chart.setRegionConsumer(this::regionAdded);
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
                System.out.println(e.getCode());
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


    ImageView getIcon(String name) {
        Image imageIcon = new Image("/images/" + name + ".png", true);
        return new ImageView(imageIcon);
    }

    void initBasicButtons(ToolBar toolBar1, ToolBar toolBar2) {
        Button button;
        Font font = new Font(7);
        List<Button> peakButtons = new ArrayList<>();
        List<Button> multipletButtons = new ArrayList<>();

        button = new Button("AutoAdd", getIcon("peak_auto"));
        button.setOnAction(e -> addAuto());
        peakButtons.add(button);

        button = new Button("Delete", getIcon("editdelete"));
        button.setOnAction(e -> removeWeakPeak());
        peakButtons.add(button);

        button = new Button("Split", getIcon("region_split"));
        button.setOnAction(e -> split());
        peakButtons.add(button);

        button = new Button("Adjust", getIcon("region_adjust"));
        button.setOnAction(e -> adjustRegion());
        peakButtons.add(button);


        Button doubletButton = new Button("Doublets", getIcon("tree"));
        doubletButton.setOnAction(e -> toDoublets());
        multipletButtons.add(doubletButton);

        button = new Button("Fit", getIcon("reload"));
        button.setOnAction(e -> fitSelected());
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
        for (Button button1 : peakButtons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setFont(font);
            button1.getStyleClass().add("toolButton");
            toolBar1.getItems().add(button1);
        }
        for (Button button1 : multipletButtons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setFont(font);
            button1.getStyleClass().add("toolButton");
            toolBar2.getItems().add(button1);
        }

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

        peakTypeChoice = new ChoiceBox();
        typeToolBar.getChildren().addAll(peakTypeChoice);
        peakTypeChoice.getItems().addAll(Peak.getPeakTypes());
        peakTypeChoice.valueProperty().addListener(e -> setPeakType());
        peakTypeChoice.setPrefWidth(120);
        toolBar.getItems().addAll(integralLabel, integralField);
        toolBar.getItems().addAll(peakTypeChoice);
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
            PeakList peakList = analyzer.getPeakList();
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
        return controller.getActiveChart();
    }

    void refresh() {
        chart.refresh();
        updateMultipletField(false);

    }

    public void fitSelected() {
        Analyzer analyzer = getAnalyzer();
        activeMultiplet.ifPresent(m -> {
            Optional<Double> result = analyzer.fitMultiplet(m);
        });
        refresh();
    }
    private void restrainPosition() {
        Analyzer analyzer = getAnalyzer();
        analyzer.setPositionRestraint(restraintPosCheckBox.isSelected()
                ? restraintSlider.getValue() : null);
    }

    public void split() {
        CrossHairs crossHairs = chart.getCrossHairs();
        if (crossHairs.hasCrosshairState("v0")) {
            splitMultipletRegion();
        } else {
            if (getAnalyzer().getPeakList() != null) {
                splitMultiplet();
            }
        }
        if (popOver != null) {
            popOver.hide();
        }
    }

    public void splitMultipletRegion() {
        double ppm = chart.getVerticalCrosshairPositions()[0];
        Analyzer analyzer = getAnalyzer();
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
        Double ppm0;
        Double ppm1;
        if (chart.getCrossHairs().hasCrosshairState("||")) {
            ppm0 = chart.getVerticalCrosshairPositions()[0];
            ppm1 = chart.getVerticalCrosshairPositions()[1];
        } else {
            if (activeMultiplet.isPresent()) {
                Multiplet multiplet = activeMultiplet.get();
                var optRegion = analyzer.getRegion(multiplet.getCenter());
                if (optRegion.isPresent()) {
                    var region = optRegion.get();
                    ppm0 = region.getRegionStart(0);
                    ppm1 = region.getRegionEnd(0);
                } else {
                    double[] minMax = Multiplets.getBoundsOfMultiplet(multiplet,analyzer.getTrimRatio());
                    ppm0 = minMax[0];
                    ppm1 = minMax[1];
                }
            } else {
                return;
            }
        }
        if ((ppm0 != null) && (ppm1 != null)) {
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
    }


    public void addRegion(DatasetRegion region) {
        Analyzer analyzer = getAnalyzer();
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
                    peakList.scale = activeMultiplet.get().getVolume();
                }
            }
            updateMultipletField(false);
            chart.refresh();

        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }
    }

    public void rms() {
        activeMultiplet.ifPresent(m -> {
            Optional<Double> result = Multiplets.rms(m);
            if (result.isPresent()) {
                System.out.println("rms " + result.get());
            }
        });
    }

    public void objectiveDeconvolution() {
        Analyzer analyzer = getAnalyzer();
        activeMultiplet.ifPresent(m -> {
            analyzer.objectiveDeconvolution(m);
            chart.refresh();
            refresh();
        });
    }

    public void addAuto() {
        Analyzer analyzer = getAnalyzer();
        activeMultiplet.ifPresent(m -> {
            Optional<Double> result = Multiplets.deviation(m);
            if (result.isPresent()) {
                System.out.println("dev pos " + result.get());
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
        Analyzer analyzer = getAnalyzer();
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

    public void extractMultiplet() {
        List<MultipletSelection> mSet = chart.getSelectedMultiplets();
        List<Multiplet> multiplets = getSelMultiplets(mSet);
        if (multiplets.size() == 1) {
            Multiplet multiplet = multiplets.get(0);
            extractMultiplet(mSet, multiplet);
            refresh();
        }
    }

    public void transferPeaks() {
        List<Peak> peaks = chart.getSelectedPeaks();
        List<MultipletSelection> mSet = chart.getSelectedMultiplets();
        List<Multiplet> multiplets = getSelMultiplets(mSet);

        System.out.println("selected peaks " + peaks);
        System.out.println("selected mult " + multiplets);
        if (peaks.size() > 0) {
            activeMultiplet.ifPresent(m -> {
                activeMultiplet = Multiplets.transferPeaks(m, peaks);
            });
        } else if (multiplets.size() == 1) {
            Multiplet multiplet = multiplets.get(0);

            Multiplet newMultiplet = extractMultiplet(mSet, multiplet);
            activeMultiplet.ifPresent(m -> activeMultiplet = Multiplets.transferPeaks(m, List.of(newMultiplet.getPeakDim().getPeak())));
        }
        refresh();
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
        Analyzer analyzer = getAnalyzer();
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
            System.out.println("convert " + multOrig + " " + multNew);
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
}
