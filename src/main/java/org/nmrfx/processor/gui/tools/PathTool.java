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

/**
 *
 * @author Bruce Johnson
 */
public class PathTool implements PeakNavigable {

    ToolBar sliderToolBar;
    FXMLController controller;
    Consumer closeAction;
    Button drawButton;
    Button findButton;
    Button tweakFreezeButton;
    Button linkButton;
    Label atomXFieldLabel;
    Label atomYFieldLabel;
    Label intensityFieldLabel;
    Label atomXLabel;
    Label atomYLabel;
    Label intensityLabel;
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

        drawButton = GlyphsDude.createIconButton(FontAwesomeIcon.LOCK, "Draw",
                iconSize, fontSize, ContentDisplay.TOP);
        drawButton.setOnAction(e -> drawPath());

//        freezeButton.setOnMouseClicked(e -> freezePeaks(e));
        buttons.add(drawButton);

        findButton = GlyphsDude.createIconButton(FontAwesomeIcon.UNLOCK, "Find",
                iconSize, fontSize, ContentDisplay.TOP);
        findButton.setOnAction(e -> findPaths());
//        thawButton.setOnMouseClicked(e -> thawPeaks(e));
        buttons.add(findButton);

        Button scanButton = new Button("Scan");
        scanButton.setOnAction(e -> scanPath());
        buttons.add(scanButton);

        buttons.forEach((button) -> {
            button.getStyleClass().add("toolButton");
        });

        atomXFieldLabel = new Label("X:");
        atomYFieldLabel = new Label("Y:");
        intensityFieldLabel = new Label("I:");
        atomXLabel = new Label();
        atomXLabel.setMinWidth(75);
        atomYLabel = new Label();
        atomYLabel.setMinWidth(75);
        intensityLabel = new Label();
        intensityLabel.setMinWidth(75);

        MenuButton actionMenu = new MenuButton("Actions");
        MenuItem loadData = new MenuItem("Load Data...");
        loadData.setOnAction(e -> loadPathData());
        actionMenu.getItems().add(loadData);

        MenuItem findPath = new MenuItem("Find");
        findPath.setOnAction(e -> checkLists());
        actionMenu.getItems().add(findPath);

        MenuItem setStatusItem = new MenuItem("Set Status");
        setStatusItem.setOnAction(e -> setStatus());
        actionMenu.getItems().add(setStatusItem);

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
        toolBar.getItems().addAll(atomXFieldLabel, atomXLabel, filler3, atomYFieldLabel, atomYLabel, filler4, intensityFieldLabel, intensityLabel);

        toolBar.getItems().add(filler5);
        radiusField = new TextField("0.24");
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
        PolyChart chart = controller.getActiveChart();
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

    void findPaths() {
        peakPath.checkListsForUnambigous(0.25);
        peakPath.setStatus(0.05, 0.75);
        peakPath.checkListsForUnambigous(0.25);
        peakPath.setStatus(0.1, 0.75);
        peakPath.checkListsForUnambigous(0.25);
        peakPath.setStatus(0.15, 0.85);
        peakPath.checkListsForUnambigous(0.25);
        peakPath.setStatus(0.25, 0.95);
        peakPath.checkListsForUnambigous(0.30);
        peakPath.setStatus(0.30, 0.95);
        peakPath.checkListsForUnambigous(0.4);
        peakPath.setStatus(0.4, 1.00);
        peakPath.checkListsForUnambigous(0.5);
        peakPath.setStatus(0.5, 1.00);
        peakPath.extendPaths(0.5, 1.0);
        peakPath.setStatus(0.5, 1.00);
    }

    void scanPath() {
        Peak startPeak = peakNavigator.getPeak();
        if (startPeak != null) {
            List<PeakDistance> peakDists = peakPath.scan(startPeak, 0.5, 2.0, 3, null, true);
            for (PeakDistance peakDist : peakDists) {
                if (peakDist != null) {
                    System.out.println(peakDist.getPeak());
                }
            }
            drawPath();
        }
    }

    void drawPath() {
        PolyChart chart = controller.getActiveChart();
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
            Path path = peakPath.getPath(peak.getName());
            System.out.println(path.toString());
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
