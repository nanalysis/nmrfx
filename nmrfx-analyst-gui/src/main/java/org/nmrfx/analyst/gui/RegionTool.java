/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.peaks.Analyzer;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.ControllerTool;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.CrossHairs;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.MultipletSelection;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.processor.gui.utils.ToolBarUtils;
import org.nmrfx.utils.GUIUtils;
import static org.nmrfx.utils.GUIUtils.affirm;
import static org.nmrfx.utils.GUIUtils.warn;

/**
 *
 * @author brucejohnson
 */
public class RegionTool implements ControllerTool {

    Stage stage = null;
    VBox vBox;
    HBox navigatorToolBar;
    TextField multipletIdField;
    HBox menuBar;
    HBox toolBar;
    HBox regionToolBar;
    HBox peakToolBar;
    HBox fittingToolBar;
    HBox integralToolBar;
    HBox typeToolBar;
    Button splitButton;
    Button splitRegionButton;
    TextField integralField;
    CheckBox restraintPosCheckBox;
    Slider restraintSlider;

    private PolyChart chart;
    Optional<DatasetRegion> activeRegion = Optional.empty();
    boolean ignoreCouplingChanges = false;
    ChangeListener<String> patternListener;
    Analyzer analyzer = null;

    FXMLController controller;
    Consumer<RegionTool> closeAction;

    public RegionTool(FXMLController controller, Consumer<RegionTool> closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
        chart = controller.getActiveChart();
        chart.getDatasetAttributes().addListener((ListChangeListener) c -> {
            if (chart.getDatasetAttributes().isEmpty()) {
                analyzer = null;
            } else {
                Analyzer thisAnalyzer = getAnalyzer();
                thisAnalyzer.setDataset((Dataset) chart.getDatasetAttributes().get(0).getDataset());
            }
        });
        chart.getPeakListAttributes().addListener((ListChangeListener) c -> {
            Analyzer thisAnalyzer = getAnalyzer();
            if (chart.getPeakListAttributes().isEmpty()) {
                thisAnalyzer.setPeakList(null);
            } else {
                thisAnalyzer.setPeakList((PeakList) chart.getPeakListAttributes().get(0).getPeakList());
            }
        });
    }

    public VBox getBox() {
        return vBox;
    }

    public void close() {
        closeAction.accept(this);
    }

    public void initialize(VBox vBox) {
        this.vBox = vBox;
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
        ToolBarUtils.addFiller(toolBar1, 10, 500);

        ToolBar toolBar2 = new ToolBar();
        initTools(toolBar2);

        Separator vsep1 = new Separator(Orientation.HORIZONTAL);
        Separator vsep2 = new Separator(Orientation.HORIZONTAL);
        vBox.getChildren().addAll(toolBar1, vsep1, toolBar2);
        chart.setRegionConsumer(e -> regionAdded(e));
        ChangeListener regionListener = new ChangeListener<DatasetRegion>() {
            @Override
            public void changed(ObservableValue<? extends DatasetRegion> observableValue, DatasetRegion region1, DatasetRegion region2) {
                if (region2 != null) {
                    activeRegion = Optional.of(region2);
                    updateRegion(false);
                }
            }
        };
        chart.addRegionListener(regionListener);
    }

    public void initMenus(ToolBar toolBar) {
        MenuButton menu = new MenuButton("Actions");
        toolBar.getItems().add(menu);

        MenuItem findRegionsMenuItem = new MenuItem("Find Regions");
        findRegionsMenuItem.setOnAction(e -> findRegions());

        MenuItem loadRegionsMenuItem = new MenuItem("Load Regions");
        loadRegionsMenuItem.setOnAction(e -> loadRegions());

        MenuItem pickRegionsMenuItem = new MenuItem("Pick Regions");
        pickRegionsMenuItem.setOnAction(e -> pickRegions());

        MenuItem fitRegionsMenuItem = new MenuItem("Fit Regions");
        fitRegionsMenuItem.setOnAction(e -> fitRegions());

        MenuItem adjustPeakIntegralsMenuItem = new MenuItem("Adjust Peak Integrals");
        adjustPeakIntegralsMenuItem.setOnAction(e -> adjustPeakIntegrals());

        MenuItem clearMenuItem = new MenuItem("Clear");
        clearMenuItem.setOnAction(e -> clearAnalysis());

        MenuItem thresholdMenuItem = new MenuItem("Set Threshold");
        thresholdMenuItem.setOnAction(e -> setThreshold());

        MenuItem clearThresholdMenuItem = new MenuItem("Clear Threshold");
        clearThresholdMenuItem.setOnAction(e -> clearThreshold());

        menu.getItems().addAll(findRegionsMenuItem, loadRegionsMenuItem,
                pickRegionsMenuItem,
                fitRegionsMenuItem, adjustPeakIntegralsMenuItem,
                clearMenuItem, thresholdMenuItem, clearThresholdMenuItem);
    }

    public void initNavigator(ToolBar toolBar) {
        multipletIdField = new TextField();
        multipletIdField.setMinWidth(75);
        multipletIdField.setMaxWidth(75);
        multipletIdField.setPrefWidth(75);

        String iconSize = "12px";
        String fontSize = "7pt";
        ArrayList<Button> buttons = new ArrayList<>();
        Button bButton;

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> lastRegion());
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> nextRegion());
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> previousRegion());
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> firstRegion());
        buttons.add(bButton);

        toolBar.getItems().addAll(buttons);
        toolBar.getItems().add(multipletIdField);
        HBox spacer = new HBox();
        toolBar.getItems().add(spacer);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        multipletIdField.setOnKeyReleased(kE -> {
            if (null != kE.getCode()) {
                switch (kE.getCode()) {
                    case ENTER:
                        gotoClosestRegion(multipletIdField);
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
        button.setOnAction(e -> addRegion());
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

        button = new Button("Fit", getIcon("reload"));
        button.setOnAction(e -> fitSelected());
        fitButtons.add(button);

        button = new Button("BICFit", getIcon("reload"));
        button.setOnAction(e -> objectiveDeconvolution());
        fitButtons.add(button);

        restraintPosCheckBox = new CheckBox("Restraint");
        restraintSlider = new Slider(0.02, 2.0, 1.0);

        Label regionLabel = new Label("Regions:");
        Label peakLabel = new Label("Peaks:");
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
        toolBar.getItems().add(fitLabel);
        for (Button button1 : fitButtons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setFont(font);
            button1.getStyleClass().add("toolButton");
            toolBar.getItems().add(button1);
        }
        toolBar.getItems().addAll(restraintPosCheckBox, restraintSlider);
        Label integralLabel = new Label("N:");
        integralLabel.setPrefWidth(80);
    }

    void initIntegralType(ToolBar toolBar) {
        Label integralLabel = new Label("N: ");
        integralField = new TextField();
        integralField.setPrefWidth(120);
        toolBar.getItems().addAll(integralLabel, integralField);

        integralField.setOnKeyReleased(k -> {
            if (k.getCode() == KeyCode.ENTER) {
                try {
                    double value = Double.parseDouble(integralField.getText().trim());
                    activeRegion.ifPresent(region -> {
                        double integral = region.getIntegral();
                        Optional<Dataset> datasetOpt = getDataset();
                        datasetOpt.ifPresent(d -> d.setNorm(integral / value));
                        analyzer.normalizePeaks(region, value);
                        refresh();
                    });
                } catch (NumberFormatException nfE) {

                }

            }
        });
    }

    void adjustPeakIntegrals() {
        activeRegion.ifPresent(region -> {
            chart = getChart();
            Dataset dataset = (Dataset) chart.getDataset();
            double norm = dataset.getNorm() / dataset.getScale();
            double integral = region.getIntegral();
            double value = integral / norm;
            analyzer.normalizePeaks(region, value);
            refresh();
        });
    }

    public Analyzer getAnalyzer() {
        if (analyzer == null) {
            chart = getChart();
            Dataset dataset = (Dataset) chart.getDataset();
            if ((dataset == null) || (dataset.getNDim() > 1)) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Chart must have a 1D dataset");
                alert.showAndWait();
                return null;
            }
            analyzer = new Analyzer(dataset);
            if (!chart.getPeakListAttributes().isEmpty()) {
                analyzer.setPeakList(chart.getPeakListAttributes().get(0).getPeakList());
            }
        }
        return analyzer;
    }

    private void analyze1D() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            try {
                analyzer.analyze();
                PeakList peakList = analyzer.getPeakList();
                List<String> peakListNames = new ArrayList<>();
                peakListNames.add(peakList.getName());
                chart.chartProps.setRegions(false);
                chart.chartProps.setIntegrals(true);
                chart.updatePeakLists(peakListNames);
            } catch (IOException ex) {
                Logger.getLogger(AnalystApp.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void showPeakList() {
        List<String> peakListNames = new ArrayList<>();
        PeakList peakList = analyzer.getPeakList();
        if (peakList != null) {
            peakListNames.add(peakList.getName());
            chart.updatePeakLists(peakListNames);
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

    private void findRegions() {
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
            chart.chartProps.setRegions(true);
            chart.chartProps.setIntegrals(true);
            activeRegion = Optional.empty();
            chart.setActiveRegion(null);
            chart.refresh();
        }
    }

    private void fitRegions() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            try {
                restrainPosition();
                analyzer.fitRegions();
            } catch (Exception ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
                return;
            }
            refresh();
        }
    }

    private void pickRegions() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            analyzer.peakPickRegions();
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
        }
    }

    private void clearAnalysis() {
        Analyzer regionAnalyzer = getAnalyzer();
        if (regionAnalyzer != null) {
            if (affirm("Clear Analysis")) {
                PeakList peakList = regionAnalyzer.getPeakList();
                if (peakList != null) {
                    peakList.remove();
                    regionAnalyzer.setPeakList(null);
                }
                regionAnalyzer.getDataset().setRegions(null);
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

    public void initMultiplet() {
        TreeSet<DatasetRegion> regions = getRegions();
        if (!regions.isEmpty()) {
            DatasetRegion m = regions.first();
            if (m != null) {
                activeRegion = Optional.of(m);
                RegionTool.this.updateRegion(false);
            }
        }
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

    void updateRegion() {
        RegionTool.this.updateRegion(true);
    }

    void updateRegion(boolean resetView) {
        if (activeRegion.isPresent()) {
            DatasetRegion region = activeRegion.get();
            double center = (region.getRegionStart(0) + region.getRegionEnd(0)) / 2;
            multipletIdField.setText(String.format("%.3f", center));
            if (resetView) {
                resetRegionView(region);
            }
            double scale = getDataset().get().getNorm();
            double value = region.getIntegral() / scale;
            integralField.setText(String.format("%.2f", value));
            chart.setActiveRegion(activeRegion.get());
            chart.refresh();
        } else {
            multipletIdField.setText("");
            chart.setActiveRegion(null);
            chart.refresh();
        }
    }

    void firstRegion() {
        TreeSet<DatasetRegion> regions = getRegions();
        if (!regions.isEmpty()) {
            activeRegion = Optional.of(regions.first());
        } else {
            activeRegion = Optional.empty();
        }
        updateRegion();
    }

    void previousRegion() {
        if (activeRegion.isPresent()) {
            TreeSet<DatasetRegion> regions = getRegions();
            DatasetRegion region = regions.lower(activeRegion.get());
            if (region == null) {
                region = regions.first();
            }
            activeRegion = Optional.of(region);
            updateRegion();
        } else {
            lastRegion();
        }
    }

    void nextRegion() {
        if (activeRegion.isPresent()) {
            TreeSet<DatasetRegion> regions = getRegions();
            DatasetRegion region = regions.higher(activeRegion.get());
            if (region == null) {
                region = regions.first();
            }
            activeRegion = Optional.of(region);
            updateRegion();
        } else {
            lastRegion();
        }
    }

    void lastRegion() {
        TreeSet<DatasetRegion> regions = getRegions();
        if (!regions.isEmpty()) {
            activeRegion = Optional.of(regions.last());
        } else {
            activeRegion = Optional.empty();
        }
        updateRegion();
    }

    void gotoClosestRegion(TextField textField) {
        try {
            double center = Double.valueOf(textField.getText());
            DatasetRegion region = DatasetRegion.findClosest(getRegions(), center, 0);
            if (region != null) {
                activeRegion = Optional.of(region);
                updateRegion();
            }

        } catch (NumberFormatException nfe) {

        }

    }

    public static RegionTool create() {
        FXMLLoader loader = new FXMLLoader(MinerController.class.getResource("/fxml/RegionsScene.fxml"));
        RegionTool controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((BorderPane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<RegionTool>getController();
            controller.stage = stage;
            stage.setTitle("Regions");
            stage.setScene(scene);
            stage.setMinWidth(200);
            stage.setMinHeight(250);
            stage.show();
            stage.toFront();
            controller.chart = controller.getChart();
            controller.initMultiplet();

        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }
        return controller;
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

    Optional<Dataset> getDataset() {
        Optional<Dataset> datasetOpt = Optional.empty();
        Dataset dataset = getAnalyzer().getDataset();
        if (dataset != null) {
            datasetOpt = Optional.of(dataset);
        }
        return datasetOpt;
    }

    TreeSet<DatasetRegion> getRegions() {
        Optional<Dataset> datasetOpt = getDataset();
        TreeSet<DatasetRegion> regions;
        if (datasetOpt.isPresent()) {
            regions = datasetOpt.get().getRegions();
            if (regions == null) {
                regions = new TreeSet<>();
                datasetOpt.get().setRegions(regions);
            }
        } else {
            regions = new TreeSet<>();
        }
        return regions;
    }

    PolyChart getChart() {
        chart = controller.getActiveChart();
        return chart;
    }

    void refresh() {
        chart.refresh();
        RegionTool.this.updateRegion(false);

    }

    List<MultipletSelection> getMultipletSelection() {
        FXMLController controller = FXMLController.getActiveController();
        List<MultipletSelection> multiplets = chart.getSelectedMultiplets();
        return multiplets;
    }

    private void restrainPosition() {
        analyzer.setPositionRestraint(restraintPosCheckBox.isSelected()
                ? restraintSlider.getValue() : null);
    }

    public void fitSelected() {
        Analyzer analyzer = getAnalyzer();
        activeRegion.ifPresent(m -> {
            try {
                restrainPosition();
                Optional<Double> result = analyzer.fitRegion(m);
                refresh();
            } catch (Exception ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        });
    }

    public void splitRegion() {
        double ppm = chart.getVerticalCrosshairPositions()[0];

        activeRegion.ifPresent(r -> {
            DatasetRegion newRegion = r.split(ppm - 0.001, ppm + 0.001);
            getRegions().add(newRegion);
            updateRegion();
        });
        chart.refresh();
    }

    public void adjustRegion() {
        Analyzer analyzer = getAnalyzer();
        double ppm0 = chart.getVerticalCrosshairPositions()[0];
        double ppm1 = chart.getVerticalCrosshairPositions()[1];
        analyzer.removeRegion(ppm0, ppm1);
        analyzer.addRegion(ppm0, ppm1);
        RegionTool.this.updateRegion(false);
        chart.refresh();
    }

    public void regionAdded(DatasetRegion region) {
        addRegion(region);
    }

    public void addRegion() {
        addRegion(null);
    }

    public void addRegion(DatasetRegion region) {
        getAnalyzer();
        double ppm0;
        double ppm1;
        if (region == null) {
            ppm0 = chart.getVerticalCrosshairPositions()[0];
            ppm1 = chart.getVerticalCrosshairPositions()[1];
            Set<DatasetRegion> regions = getRegions();
            DatasetRegion newRegion = new DatasetRegion(ppm0, ppm1);
            regions.add(newRegion);
            activeRegion = Optional.of(newRegion);
        } else {
            ppm0 = region.getRegionStart(0);
            ppm1 = region.getRegionEnd(0);
            activeRegion = Optional.of(region);
        }
        RegionTool.this.updateRegion(false);
        PeakList peakList = analyzer.getPeakList();
        if (peakList != null) {
            analyzer.peakPickRegion(ppm0, ppm1);
        }

        chart.refresh();
    }

    public void removeRegion() {
        activeRegion.ifPresent(region -> {
            TreeSet<DatasetRegion> regions = getRegions();
            DatasetRegion newRegion = regions.lower(region);
            if (newRegion == region) {
                newRegion = regions.higher(region);
            }
            regions.remove(region);
            activeRegion = Optional.of(newRegion);
            chart.refresh();
        });
    }

    public void rms() {
        activeRegion.ifPresent(region -> {
            try {
                Optional<Double> result = analyzer.measureRegion(region, "rms");
                if (result.isPresent()) {
                    System.out.println("rms " + result.get());
                }
            } catch (Exception ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        });
    }

    public void objectiveDeconvolution() {
        activeRegion.ifPresent(region -> {
            try {
                analyzer.objectiveDeconvolution(region);
                showPeakList();
                chart.refresh();
                refresh();
            } catch (Exception ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        });
    }

    public void addAuto() {
        activeRegion.ifPresent(region -> {
            try {
                Optional<Double> result = analyzer.measureRegion(region, "maxdev");
                if (result.isPresent()) {
                    System.out.println("dev pos " + result.get());
                    analyzer.addPeaksToRegion(region, result.get());
                    showPeakList();
                    chart.refresh();
                    refresh();
                }
            } catch (Exception ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
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
        activeRegion.ifPresent(region -> {
            double ppm1 = chart.getVerticalCrosshairPositions()[0];
            double ppm2 = chart.getVerticalCrosshairPositions()[1];
            try {
                if (both) {
                    analyzer.addPeaksToRegion(region, ppm1, ppm2);
                } else {
                    analyzer.addPeaksToRegion(region, ppm1);
                }
                chart.refresh();
                refresh();
            } catch (Exception ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        });
    }

    void removeWeakPeak() {
        activeRegion.ifPresent(region -> {
            try {
                analyzer.removeWeakPeaksInRegion(region, 1);
                refresh();
            } catch (Exception ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        });
    }

    public void resetRegionView(DatasetRegion region) {
        boolean resize = true;
        if (region != null) {
            double start = region.getRegionStart(0);
            double end = region.getRegionEnd(0);
            double center = (start + end) / 2.0;
            double bounds = Math.abs(start - end);
            double widthScale = 2.5;
            if ((chart != null) && !chart.getDatasetAttributes().isEmpty()) {
                DatasetAttributes dataAttr = (DatasetAttributes) chart.getDatasetAttributes().get(0);
                double[][] limits = chart.getRegionLimits(dataAttr);
                Double[] ppms = {center};
                double currentWidth = Math.abs(limits[0][0] - limits[0][1]);
                Double[] widths = {bounds * widthScale};
                if ((currentWidth > 3.0 * widths[0]) || (currentWidth < widths[0])) {
                    resize = true;
                }
                if (resize && (widthScale > 0.0)) {
                    chart.moveTo(ppms, widths);
                } else {
                    chart.moveTo(ppms);
                }
            }
        }
    }

}
