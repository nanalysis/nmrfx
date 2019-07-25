/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui.tools;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.FreezeListener;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.PeakPath;
import org.nmrfx.processor.datasets.peaks.PeakPath.Path;
import org.nmrfx.processor.datasets.peaks.PeakPath.PeakDistance;
import org.nmrfx.processor.gui.CanvasAnnotation;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PeakNavigable;
import org.nmrfx.processor.gui.PeakNavigator;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.annotations.AnnoPolyLine;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakDisplayParameters;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;

/**
 *
 * @author Bruce Johnson
 */
public class PathTool implements PeakNavigable {

    ToolBar sliderToolBar;
    FXMLController controller;
    PolyChart chart;
    Consumer closeAction;
    Button drawButton;
    Button findButton;
    Button tweakFreezeButton;
    List<Peak> selPeaks;
    List<FreezeListener> listeners = new ArrayList<>();
    List<String> datasetNames = new ArrayList<>();
    List<String> peakListNames = new ArrayList<>();
    PeakNavigator peakNavigator;
    TextField radiusField;

    PeakPath peakPath;

    public PathTool(FXMLController controller, Consumer closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
        this.chart = controller.getActiveChart();
    }

    public ToolBar getToolBar() {
        return sliderToolBar;
    }

    public void close(Object o) {
        closeAction.accept(this);
    }

    public void initPathTool(ToolBar toolBar) {
        this.sliderToolBar = toolBar;
        peakNavigator = PeakNavigator.create(this).onClose(this::close).initialize(toolBar);
        peakNavigator.setPeakList();
        controller.addScaleBox(peakNavigator, toolBar);

        String iconSize = "16px";
        String fontSize = "7pt";
        ArrayList<Button> buttons = new ArrayList<>();
        Button bButton;

        buttons.forEach((button) -> {
            button.getStyleClass().add("toolButton");
        });

        ArrayList<Button> dataButtons = new ArrayList<>();
        Button allButton = new Button("All");
        allButton.setOnAction(e -> allDatasets());
        allButton.getStyleClass().add("toolButton");
        dataButtons.add(allButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> firstDataset(e));
        dataButtons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> previousDataset(e));
        dataButtons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> nextDataset(e));
        dataButtons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> lastDataset(e));
        dataButtons.add(bButton);

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.PLUS, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> addPeakToPath());
        dataButtons.add(bButton);

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> removePeakFromPath());
        dataButtons.add(bButton);

        MenuButton actionMenu = new MenuButton("Actions");
        MenuItem loadData = new MenuItem("Load Data...");
        loadData.setOnAction(e -> loadPathData());
        actionMenu.getItems().add(loadData);

        MenuItem findPathMenuItem = new MenuItem("Find Paths");
        findPathMenuItem.setOnAction(e -> findPaths());
        actionMenu.getItems().add(findPathMenuItem);

        MenuItem extendPathMenuItem = new MenuItem("Extend Paths");
        extendPathMenuItem.setOnAction(e -> extendPaths());
        actionMenu.getItems().add(extendPathMenuItem);

        MenuItem drawMenuItem = new MenuItem("Draw Paths");
        drawMenuItem.setOnAction(e -> drawPath());
        actionMenu.getItems().add(drawMenuItem);

        MenuItem addPathMenuItem = new MenuItem("Add Path");
        addPathMenuItem.setOnAction(e -> addPath());
        actionMenu.getItems().add(addPathMenuItem);

        MenuItem clearPathsMenuItem = new MenuItem("Clear Paths");
        clearPathsMenuItem.setOnAction(e -> clearPaths());
        actionMenu.getItems().add(clearPathsMenuItem);

        MenuItem clearPathMenuItem = new MenuItem("Clear Path");
        clearPathMenuItem.setOnAction(e -> clearPath());
        actionMenu.getItems().add(clearPathMenuItem);

        Pane filler1 = new Pane();
        HBox.setHgrow(filler1, Priority.ALWAYS);
        Pane filler2 = new Pane();
        filler2.setMinWidth(50);
        Pane filler3 = new Pane();
        filler3.setMinWidth(50);
        Pane filler4 = new Pane();
        filler4.setMinWidth(50);
        Pane filler5 = new Pane();
        HBox.setHgrow(filler5, Priority.ALWAYS);

        toolBar.getItems().add(filler1);
        toolBar.getItems().add(actionMenu);
        toolBar.getItems().addAll(buttons);
        toolBar.getItems().add(filler2);
        toolBar.getItems().addAll(dataButtons);

        toolBar.getItems().add(filler5);
        radiusField = new TextField("0.5");
        toolBar.getItems().add(radiusField);

//        controller.selPeaks.addListener(e -> setActivePeaks(controller.selPeaks.get()));
    }

    boolean getAltState(Event event) {
        boolean altState = false;
        if (event instanceof MouseEvent) {
            MouseEvent mEvent = (MouseEvent) event;
            if (mEvent.isAltDown()) {
                altState = true;
            }
        }
        return altState;
    }

    boolean shouldRespond(Event event) {
        boolean shouldRespond = event instanceof ActionEvent;
        if (event instanceof MouseEvent) {
            MouseEvent mEvent = (MouseEvent) event;
            if (mEvent.isAltDown()) {
                shouldRespond = true;
            }
        }
        return shouldRespond;
    }

    void setupChart(List<String> datasetNames) {
        chart.updateDatasets(datasetNames);
    }

    void loadPathData() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        datasetNames.clear();
        peakListNames.clear();
        List<Double> x0List = new ArrayList<>();
        List<Double> x1List = new ArrayList<>();
        String sepChar = " +";
        if (file != null) {
            try {
                List<String> lines = Files.readAllLines(file.toPath());
                if (lines.size() > 0) {
                    if (lines.get(0).contains("\t")) {
                        sepChar = "\t";
                    }
                    for (String line : lines) {
                        String[] fields = line.split(sepChar);
                        if ((fields.length > 1) && !fields[0].startsWith("#")) {
                            datasetNames.add(fields[0]);
                            x0List.add(Double.parseDouble(fields[1]));
                            if (fields.length > 2) {
                                x1List.add(Double.parseDouble(fields[2]));
                            }
                        }
                    }
                }
                double[] x0 = new double[x0List.size()];
                double[] x1 = new double[x0List.size()];
                for (int i = 0; i < datasetNames.size(); i++) {
                    String datasetName = datasetNames.get(i);
                    Dataset dataset = Dataset.getDataset(datasetName);
                    if (dataset == null) {
                        Alert alert = new Alert(Alert.AlertType.ERROR, "Dataset " + datasetName + " doesn't exist");
                        alert.showAndWait();
                        return;
                    }
                    String peakListName = "";
                    PeakList peakList = PeakList.getPeakListForDataset(datasetName);
                    if (peakList == null) {
                        peakListName = PeakList.getNameForDataset(datasetName);
                        peakList = PeakList.get(peakListName);
                    } else {
                        peakListName = peakList.getName();
                    }
                    if (peakList == null) {
                        Alert alert = new Alert(Alert.AlertType.ERROR, "PeakList " + peakList + " doesn't exist");
                        alert.showAndWait();
                        return;
                    }
                    peakListNames.add(peakListName);
                    x0[i] = x0List.get(i);
                    if (!x1List.isEmpty()) {
                        x1[i] = x1List.get(i);
                    } else {
                        x1[i] = 100.0;
                    }
                }
                double[] weights = {1.0, 5.0};  // fixme  need to figure out from nuclei
                peakPath = new PeakPath(peakListNames, x0, x1, weights);
                peakPath.initPaths();
                setupChart(datasetNames);

            } catch (IOException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Error reading file");
                alert.showAndWait();
            }
        }
    }

    void clearPath() {
        Peak startPeak = peakNavigator.getPeak();
        if (startPeak != null) {
            peakPath.initPath(startPeak);
        }
        drawPath();
    }

    void addPeakToPath() {
        Peak startPeak = peakNavigator.getPeak();
        List<Peak> selPeaks = chart.getSelectedPeaks();
        System.out.println("selp " + selPeaks.size() + " " + startPeak);
        if (selPeaks.size() == 1) {
            if (startPeak != null) {
                peakPath.addPeak(startPeak, selPeaks.get(0));
            }
        }
        drawPath();
    }

    void removePeakFromPath() {
        Peak startPeak = peakNavigator.getPeak();
        List<Peak> selPeaks = chart.getSelectedPeaks();
        if (selPeaks.size() == 1) {
            if (startPeak != null) {
                peakPath.removePeak(startPeak, selPeaks.get(0));
            }
        }
    }

    void setPeakStates() {
        List<PeakListAttributes> peakAttrs = chart.getPeakListAttributes();
        for (PeakListAttributes peakAttr : peakAttrs) {
            peakAttr.setDrawPeaks(peakAttr.getDatasetAttributes().getPos());
        }

    }

    void firstLastPeakLists() {
        List<PeakListAttributes> peakAttrs = chart.getPeakListAttributes();
        int i = 0;
        for (PeakListAttributes peakAttr : peakAttrs) {
            boolean state = (i == 0) || (i == peakAttrs.size() - 1);
            peakAttr.setDrawPeaks(state);
            if (i == peakAttrs.size() - 1) {
                peakAttr.setDisplayType(PeakDisplayParameters.DisplayTypes.Cross);
            }
            i++;
        }
    }

    void allDatasets() {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        for (DatasetAttributes dataAttr : dataAttrs) {
            dataAttr.setPos(true);
        }
        firstLastPeakLists();
        chart.refresh();
    }

    void setDatasetState(int active) {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        if (active < 0) {
            active = 0;
        }
        if (active >= dataAttrs.size()) {
            active = dataAttrs.size() - 1;
        }
        int i = 0;
        for (DatasetAttributes dataAttr : dataAttrs) {
            boolean state = (i == active) || (i == 0);
            dataAttr.setPos(state);
            i++;
        }
        setPeakStates();
        chart.refresh();
    }

    int getActiveDataset() {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        int i = 0;
        int active = 0;
        for (DatasetAttributes dataAttr : dataAttrs) {
            if (dataAttr.getPos()) {
                if (i != 0) {
                    active = i;
                    break;
                }
            }
            i++;
        }
        return active;
    }

    void firstDataset(ActionEvent e) {
        setDatasetState(0);
    }

    void lastDataset(ActionEvent e) {
        setDatasetState(chart.getDatasetAttributes().size() - 1);
    }

    void previousDataset(ActionEvent e) {
        int active = getActiveDataset();
        setDatasetState(active - 1);

    }

    void nextDataset(ActionEvent e) {
        int active = getActiveDataset();
        setDatasetState(active + 1);

    }

    /*
    print 'check'
checkLists(pp, 0.25, True)
setStatus(0.05,0.75)
checkLists(pp, 0.25, False)
setStatus(0.05,0.75)
checkLists(pp, 0.25, False)

     */
    void checkLists() {
        double radius = Double.parseDouble(radiusField.getText());
        peakPath.checkListsForUnambigous(radius);
    }

    void setStatus() {
        double radius = Double.parseDouble(radiusField.getText());
        peakPath.setStatus(radius, 1.0);

    }

    void clearPaths() {
        peakPath.clearPaths();
    }

    void findPaths() {
        int n = 10;
        double minRadius = 0.1;
        double maxRadius = Double.parseDouble(radiusField.getText());
        double radius = minRadius;
        double minLim = 0.7;
        double maxLim = 1.0;
        double lim = minLim;
        for (int i = 0; i < n; i++) {
            radius = minRadius + i * (maxRadius - minRadius) / (n - 1);
            lim = minLim + i * (maxLim - minLim) / (n - 1);
            peakPath.checkListsForUnambigous(radius);
            peakPath.setStatus(radius, lim);

        }
        drawPath();
//        peakPath.checkListsForUnambigous(0.25);
//        peakPath.setStatus(0.05, 0.75);
//        peakPath.checkListsForUnambigous(0.25);
//        peakPath.setStatus(0.1, 0.75);
//        peakPath.checkListsForUnambigous(0.25);
//        peakPath.setStatus(0.15, 0.85);
//        peakPath.checkListsForUnambigous(0.25);
//        peakPath.setStatus(0.25, 0.95);
//        peakPath.checkListsForUnambigous(0.30);
//        peakPath.setStatus(0.30, 0.95);
//        peakPath.checkListsForUnambigous(0.4);
//        peakPath.setStatus(0.4, 1.00);
//        peakPath.checkListsForUnambigous(0.5);
//        peakPath.setStatus(0.5, 1.00);
    }

    void extendPaths() {
        double radius = Double.parseDouble(radiusField.getText());
        double lim = 1.0;
        peakPath.extendPaths(radius, lim);
        peakPath.setStatus(radius, lim);
        drawPath();
    }

    void addPath() {

        Peak lastPeak = null;
        List<Peak> selPeaks = chart.getSelectedPeaks();
        if (selPeaks.size() == 1) {
            lastPeak = selPeaks.get(0);
        }

        double radius = Double.parseDouble(radiusField.getText());
        Peak startPeak = peakNavigator.getPeak();
        if (startPeak != null) {
            List<PeakDistance> peakDists = peakPath.scan(startPeak, radius, 2.0, 4, lastPeak, true);
            for (PeakDistance peakDist : peakDists) {
                if (peakDist != null) {
                    System.out.println(peakDist.getPeak());
                }
            }
            drawPath();
        }
    }

    void drawPath() {
        chart.clearAnnotations();
        Collection<Path> paths = peakPath.getPaths();

        for (Path path : paths) {
            if (path.confirmed()) {
                List<Double> x = new ArrayList<>();
                List<Double> y = new ArrayList<>();
                for (PeakDistance peakDist : path.getPeakDistances()) {
                    if (peakDist != null) {
                        Peak peak = peakDist.getPeak();
                        x.add((double) peak.getPeakDim(0).getChemShiftValue());
                        y.add((double) peak.getPeakDim(1).getChemShiftValue());
                    }
                }
                if (!x.isEmpty()) {
                    AnnoPolyLine annoPolyLine = new AnnoPolyLine(x, y,
                            CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.WORLD);
                    annoPolyLine.setStroke(Color.RED);
                    annoPolyLine.setLineWidth(3.0);
                    chart.addAnnotation(annoPolyLine);
                }
            }
        }
        chart.refresh();
    }

    @Override
    public void refreshPeakView(Peak peak) {

        controller.refreshPeakView(peak);
        if ((peak != null) && (peakPath != null)) {
            Path path = peakPath.getPath(peak);
            if (path != null) {
                System.out.println(path.toString());
            }
        }
    }

    @Override
    public void refreshPeakView() {
        controller.refreshPeakView();
    }

    @Override
    public void refreshPeakListView(PeakList peakList) {
        controller.refreshPeakListView(peakList);
    }
}
