/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.nmrfx.analyst.peaks.Analyzer;
import org.nmrfx.analyst.pro.ParametricModel;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.processor.datasets.BucketedMatrix;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.ControllerTool;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.utils.ToolBarUtils;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.utils.GUIUtils;

/**
 *
 * @author brucejohnson
 */
public class ParametricTool implements ControllerTool {

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
    TextField[] couplingFields;
    TextField[] slopeFields;
    private PolyChart chart;
    Optional<DatasetRegion> activeRegion = Optional.empty();
    boolean ignoreCouplingChanges = false;
    ChangeListener<String> patternListener;
    BucketedMatrix matrix;
    FXMLController controller;
    Consumer<ParametricTool> closeAction;

    double sdevMultiplier = 10;
    int winSize = 512;
    double minWidth = 2.0;
    double filter = 1.3;
    boolean constrainWidth = true;
    int optStepMultiplier = 30;
    double stopRadius = -2.0;

    public ParametricTool(FXMLController controller, Consumer<ParametricTool> closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
    }

    public VBox getBox() {
        return vBox;
    }

    @Override
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
        ToolBarUtils.addFiller(toolBar1, 10, 500);

        ToolBar toolBar2 = new ToolBar();
        initTools(toolBar2);

        Separator vsep1 = new Separator(Orientation.HORIZONTAL);
        Separator vsep2 = new Separator(Orientation.HORIZONTAL);
        vBox.getChildren().addAll(toolBar1, vsep1, toolBar2);
        chart = this.controller.getActiveChart();
    }

    public void initMenus(ToolBar toolBar) {
        MenuButton menu = new MenuButton("Actions");
        toolBar.getItems().add(menu);

        MenuItem analyzeSpectrumItem = new MenuItem("Analyze");
        analyzeSpectrumItem.setOnAction(e -> analyzeSpectrum());

        menu.getItems().addAll(analyzeSpectrumItem);
    }

    public void initNavigator(ToolBar toolBar) {

    }

    ImageView getIcon(String name) {
        Image imageIcon = new Image("/images/" + name + ".png", true);
        ImageView imageView = new ImageView(imageIcon);
        return imageView;
    }

    void initTools(ToolBar toolBar) {
    }

    private void analyzeSpectrum() {
        Dataset dataset = (Dataset) chart.getDataset();
        Vec vec;
        try {
            vec = dataset.readVector(0, 0);
        } catch (IOException ex) {
            GUIUtils.warn("Parametric Analysis", "Could not read vector");
            return;
        }
        var size = vec.getSize();

        stopRadius = Math.pow(10.0, stopRadius);
        Analyzer analyzer = new Analyzer(dataset);
        analyzer.calculateThreshold();

        var regions = dataset.getRegions();
        if ((regions == null) || regions.isEmpty()) {
            try {
                analyzer.findRegions();
                regions = dataset.getRegions();
            } catch (IOException ex) {
                GUIUtils.warn("Parametric Analysis", "Could not read vector");
                return;
            }
        }
        List<Integer> regionPts = new ArrayList<>();
        if ((regions != null) && !regions.isEmpty()) {
            for (var region : regions) {
                double ppm0 = region.getRegionStart(0);
                double ppm1 = region.getRegionEnd(0);
                int pt0 = dataset.ppmToPoint(0, ppm0);
                int pt1 = dataset.ppmToPoint(0, ppm1);
                if (pt0 < pt1) {
                    regionPts.add(pt0);
                    regionPts.add(pt1);
                } else {
                    regionPts.add(pt1);
                    regionPts.add(pt0);
                }
            }
        } else {
            Double[] ppms0 = chart.getCrossHairs().getCrossHairPositions(0);
            Double[] ppms1 = chart.getCrossHairs().getCrossHairPositions(1);
            if ((ppms0[0] == null) || (ppms1[0] == null)) {
            } else {
                int pt0 = ppms0[0] != null ? dataset.ppmToPoint(0, ppms0[0]) : 100;
                int pt1 = ppms1[0] != null ? dataset.ppmToPoint(0, ppms1[0]) : size - 100;
                if (pt0 < pt1) {
                    regionPts.add(pt0);
                    regionPts.add(pt1);
                } else {
                    regionPts.add(pt1);
                    regionPts.add(pt0);
                }
            }
        }

        Vec vec2 = new Vec(size);
        var maxIndex = vec.maxIndex();
        double sdev = vec.sdev(32);
        double threshold = sdev * sdevMultiplier;
        var pModel = new ParametricModel(vec);
        pModel.findSignals(winSize, threshold, minWidth, regionPts, filter, constrainWidth, optStepMultiplier, stopRadius);
        pModel.genSimVec(vec2);

        String datasetName = dataset.getName();
        int dotIndex = datasetName.lastIndexOf(".");
        String simName = dotIndex >= 0 ? datasetName.substring(0, dotIndex)
                + "_sim" + datasetName.substring(dotIndex)
                : datasetName + "_sim";
        vec2.setName(simName);
        Dataset simDataset = new Dataset(vec2);

        DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
        double lvl = dataAttr.getLvl();
        double offset = dataAttr.getOffset();

        var targets = List.of(datasetName, simName);
        chart.updateDatasets(targets);

        DatasetAttributes dataAttrSim = chart.getDatasetAttributes().get(1);
        dataAttrSim.setLvl(lvl);
        dataAttrSim.setOffset(offset);
        dataAttrSim.setPosColor(Color.BLUE);
        chart.refresh();
    }

    public Stage getStage() {
        return stage;
    }

    PolyChart getChart() {
        return chart;
    }

    void refresh() {
        chart.refresh();

    }

}
