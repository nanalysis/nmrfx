package org.nmrfx.processor.gui.tools;

import org.nmrfx.processor.tools.RunAbout;
import org.nmrfx.processor.gui.*;
import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakEvent;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.PeakListener;
import org.nmrfx.processor.datasets.peaks.SpinSystem;
import org.nmrfx.processor.datasets.peaks.SpinSystem.PeakMatch;
import org.nmrfx.processor.gui.annotations.AnnoLine;
import org.nmrfx.processor.gui.annotations.AnnoPolyLine;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import static org.nmrfx.processor.gui.spectra.DatasetAttributes.AXMODE.PPM;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Bruce Johnson
 */
public class RunAboutGUI implements PeakListener {

    FXMLController controller;
    ToolBar navigatorToolBar;
    TextField peakIdField;
    MenuButton peakListMenuButton;
    Menu arrangeMenu;
    ToggleButton deleteButton;
    PeakNavigable peakNavigable;
    PeakList refPeakList;
    Peak currentPeak;
    static Background deleteBackground = new Background(new BackgroundFill(Color.RED, CornerRadii.EMPTY, Insets.EMPTY));
    Background defaultBackground = null;
    Background defaultCellBackground = null;
    Optional<List<Peak>> matchPeaks = Optional.empty();
    int matchIndex = 0;
    Consumer closeAction = null;
    boolean showAtoms = false;
    Label atomXFieldLabel;
    Label atomYFieldLabel;
    Label intensityFieldLabel;
    Label atomXLabel;
    Label atomYLabel;
    Label intensityLabel;
    RunAbout runAbout = new RunAbout();
    int currentSpinSystem = -1;

    boolean useSpinSystem = false;
    Double[][] widths;
    int[] resOffsets = null;
    List<List<String>> winPatterns = new ArrayList<>();
    boolean[] intraResidue = null;
    int minOffset = 0;

    private RunAboutGUI(PeakNavigable peakNavigable) {
        this.peakNavigable = peakNavigable;
    }

    private RunAboutGUI(PeakNavigable peakNavigable, Consumer closeAction) {
        this.peakNavigable = peakNavigable;
        this.closeAction = closeAction;
    }

    public static RunAboutGUI create() {
        FXMLController controller = FXMLController.create();
        controller.getStage();

        ToolBar navBar = new ToolBar();
        controller.getBottomBox().getChildren().add(navBar);
        RunAboutGUI runAbout = new RunAboutGUI(controller);

        runAbout.initialize(navBar);
        runAbout.controller = controller;
        return runAbout;
    }

    public RunAboutGUI onClose(Consumer closeAction) {
        this.closeAction = closeAction;
        return this;
    }

    public RunAboutGUI showAtoms() {
        this.showAtoms = true;
        return this;
    }

    public ToolBar getToolBar() {
        return navigatorToolBar;
    }

    public void close() {
        closeAction.accept(this);
    }

    RunAboutGUI initialize(ToolBar toolBar) {
        initPeakNavigator(toolBar);
        return this;
    }

    void initPeakNavigator(ToolBar toolBar) {
        this.navigatorToolBar = toolBar;
        peakIdField = new TextField();
        peakIdField.setMinWidth(75);
        peakIdField.setMaxWidth(75);
        RunAboutGUI navigator = this;

        String iconSize = "12px";
        String fontSize = "7pt";
        ArrayList<Button> buttons = new ArrayList<>();
        Button bButton;
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        closeButton.setOnAction(e -> close());

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> navigator.firstPeak(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> navigator.previousPeak(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> navigator.nextPeak(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> navigator.lastPeak(e));
        buttons.add(bButton);
        deleteButton = GlyphsDude.createIconToggleButton(FontAwesomeIcon.BAN, fontSize, iconSize, ContentDisplay.GRAPHIC_ONLY);
        // prevent accidental activation when inspector gets focus after hitting space bar on peak in spectrum
        // a second space bar hit would activate
        deleteButton.setOnKeyPressed(e -> e.consume());
        deleteButton.setOnAction(e -> navigator.setDeleteStatus(deleteButton));

        for (Button button : buttons) {
            // button.getStyleClass().add("toolButton");
        }
        if (closeAction != null) {
            navigatorToolBar.getItems().add(closeButton);
        }
        peakListMenuButton = new MenuButton("List");
        navigatorToolBar.getItems().add(peakListMenuButton);
        updatePeakListMenu();

        MenuButton actionMenuButton = new MenuButton("Actions");
        navigatorToolBar.getItems().add(actionMenuButton);
        actionMenuButton.getStyleClass().add("toolButton");

        MenuItem loadItem = new MenuItem("Load");
        loadItem.setOnAction(e -> {
            loadYaml();
        });
        actionMenuButton.getItems().add(loadItem);

        arrangeMenu = new Menu("Arrangement");
        actionMenuButton.getItems().add(arrangeMenu);

        MenuItem assembleItem = new MenuItem("Assemble");
        assembleItem.setOnAction(e -> {
            assemble();
        });
        actionMenuButton.getItems().add(assembleItem);

        toolBar.getItems().addAll(buttons);
        toolBar.getItems().add(peakIdField);
        toolBar.getItems().add(deleteButton);

        if (showAtoms) {
            atomXFieldLabel = new Label("X:");
            atomYFieldLabel = new Label("Y:");
            intensityFieldLabel = new Label("I:");
            atomXLabel = new Label();
            atomXLabel.setMinWidth(75);
            atomYLabel = new Label();
            atomYLabel.setMinWidth(75);
            intensityLabel = new Label();
            intensityLabel.setMinWidth(75);

            Pane filler2 = new Pane();
            filler2.setMinWidth(20);
            Pane filler3 = new Pane();
            filler3.setMinWidth(20);
            Pane filler4 = new Pane();
            filler4.setMinWidth(20);
            Pane filler5 = new Pane();
            HBox.setHgrow(filler5, Priority.ALWAYS);

            toolBar.getItems().addAll(filler2, atomXFieldLabel, atomXLabel, filler3, atomYFieldLabel, atomYLabel, filler4, intensityFieldLabel, intensityLabel, filler5);

        }

        peakIdField.setOnKeyReleased(kE -> {
            if (null != kE.getCode()) {
                switch (kE.getCode()) {
                    case ENTER:
                        navigator.gotoPeakId(peakIdField);
                        break;
                    case UP:
                        navigator.gotoNextMatch(1);
                        break;
                    case DOWN:
                        navigator.gotoNextMatch(-1);
                        break;
                    default:
                        break;
                }
            }
        });
        MapChangeListener<String, PeakList> mapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> {
            updatePeakListMenu();
        };

        PeakList.peakListTable.addListener(mapChangeListener);

    }

    void assemble() {
        runAbout.assemble();
       // useSpinSystem = true;
    }

    public void updatePeakListMenu() {
        peakListMenuButton.getItems().clear();

        for (String peakListName : PeakList.peakListTable.keySet()) {
            MenuItem menuItem = new MenuItem(peakListName);
            menuItem.setOnAction(e -> {
                RunAboutGUI.this.setPeakList(peakListName);
            });
            peakListMenuButton.getItems().add(menuItem);
        }
    }

    public void setPeakList() {
        if (refPeakList == null) {
            PeakList testList = null;
            FXMLController controller = FXMLController.getActiveController();
            PolyChart chart = controller.getActiveChart();
            if (chart != null) {
                ObservableList<PeakListAttributes> attr = chart.getPeakListAttributes();
                if (!attr.isEmpty()) {
                    testList = attr.get(0).getPeakList();
                }
            }
            if (testList == null) {
                testList = PeakList.get(0);
            }
            setPeakList(testList);
        }
    }

    public void removePeakList() {
        if (refPeakList != null) {
            refPeakList.removeListener(this);
        }
        refPeakList = null;
        currentPeak = null;
    }

    public void setPeakList(String listName) {
        refPeakList = PeakList.get(listName);
        PeakList.clusterOrigin = refPeakList;
        RunAboutGUI.this.setPeakList(refPeakList);
    }

    public void setPeakList(PeakList newPeakList) {
        refPeakList = newPeakList;
        if (refPeakList != null) {
            currentPeak = refPeakList.getPeak(0);
            setPeakIdField();
            refPeakList.registerListener(this);
        } else {
        }
        peakNavigable.refreshPeakView(currentPeak);
        peakNavigable.refreshPeakListView(refPeakList);
    }

    public Peak getPeak() {
        return currentPeak;
    }

    public void setSpinSystems(List<SpinSystem> spinSystems) {
        drawSpinSystems(spinSystems);
    }

    public void setPeaks(List<Peak> peaks) {
        if ((peaks == null) || peaks.isEmpty()) {
            currentPeak = null;
            updateAtomLabels(null);
        } else {
            int iPeak = peaks.size() > 1 ? 1 : 0;
            currentPeak = peaks.get(iPeak);
            if ((peaks != null) && !peaks.isEmpty()) {
                drawWins(peaks);
                updateDeleteStatus();
            }
            updateAtomLabels(peaks.get(iPeak));
        }
        setPeakIdField();
    }

    public void setPeak(Peak peak) {
        currentPeak = peak;
        if (peak != null) {
            drawWins(Collections.singletonList(peak));
            updateDeleteStatus();
        }
        updateAtomLabels(peak);
        setPeakIdField();
    }

    void updateAtomLabels(Peak peak) {
        if (showAtoms) {
            if (peak != null) {
                atomXLabel.setText(peak.getPeakDim(0).getLabel());
                intensityLabel.setText(String.format("%.2f", peak.getIntensity()));
                if (peak.getPeakDims().length > 1) {
                    atomYLabel.setText(peak.getPeakDim(1).getLabel());
                }
            } else {
                if (showAtoms) {
                    atomXLabel.setText("");
                    atomYLabel.setText("");
                    intensityLabel.setText("");
                }
            }
        }
    }

    void updateDeleteStatus() {
        if (defaultBackground == null) {
            defaultBackground = peakIdField.getBackground();
        }
        if (!useSpinSystem && (currentPeak == null)) {
            if (currentPeak.getStatus() < 0) {
                deleteButton.setSelected(true);
                peakIdField.setBackground(deleteBackground);
            } else {
                deleteButton.setSelected(false);
                peakIdField.setBackground(defaultBackground);
            }
        }
    }

    private void setPeakIdField() {
        if (useSpinSystem) {
            if (currentSpinSystem < 0) {
                peakIdField.setText("");
            } else {
                peakIdField.setText(String.valueOf(currentSpinSystem));
            }
        } else {
            if (currentPeak == null) {
                peakIdField.setText("");
            } else {
                peakIdField.setText(String.valueOf(currentPeak.getIdNum()));
            }

        }

    }

    public void firstPeak(ActionEvent event) {
        if (useSpinSystem) {
            firstSpinSystem();
        } else {
            firstPeak();
        }
    }

    public void previousPeak(ActionEvent event) {
        if (useSpinSystem) {
            previousSpinSystem();
        } else {
            previousPeak();
        }
    }

    public void nextPeak(ActionEvent event) {
        if (useSpinSystem) {
            nextSpinSystem();
        } else {
            nextPeak();
        }
    }

    public void lastPeak(ActionEvent event) {
        if (useSpinSystem) {
            lastSpinSystem();
        } else {
            lastPeak();
        }
    }

    public void firstSpinSystem() {
        List<SpinSystem> spinSystems = new ArrayList<>();
        currentSpinSystem = 0;
        for (int resOffset : resOffsets) {
            SpinSystem spinSystem = runAbout.getSpinSystems().get(currentSpinSystem, resOffset);
            spinSystems.add(spinSystem);
        }
        setSpinSystems(spinSystems);
        System.out.println("first " + currentSpinSystem);

    }

    public void lastSpinSystem() {
        List<SpinSystem> spinSystems = new ArrayList<>();
        currentSpinSystem = runAbout.getSpinSystems().getSize() - 1;
        for (int resOffset : resOffsets) {
            SpinSystem spinSystem = runAbout.getSpinSystems().get(currentSpinSystem, resOffset);
            spinSystems.add(spinSystem);
        }
        System.out.println("last " + currentSpinSystem);
        setSpinSystems(spinSystems);
    }

    public void nextSpinSystem() {
        List<SpinSystem> spinSystems = new ArrayList<>();
        if (currentSpinSystem >= 0) {
            currentSpinSystem++;
            if (currentSpinSystem >= runAbout.getSpinSystems().getSize()) {
                currentSpinSystem = runAbout.getSpinSystems().getSize() - 1;
            }
            for (int resOffset : resOffsets) {
                SpinSystem spinSystem = runAbout.getSpinSystems().get(currentSpinSystem, resOffset);
                spinSystems.add(spinSystem);
            }
            setSpinSystems(spinSystems);
        }
        System.out.println("next " + currentSpinSystem);
    }

    public void previousSpinSystem() {
        List<SpinSystem> spinSystems = new ArrayList<>();
        if (currentSpinSystem >= 0) {
            currentSpinSystem--;
            if (currentSpinSystem < 0) {
                currentSpinSystem = 0;
            }
            for (int resOffset : resOffsets) {
                SpinSystem spinSystem = runAbout.getSpinSystems().get(currentSpinSystem, resOffset);
                spinSystems.add(spinSystem);
            }
            setSpinSystems(spinSystems);
        }
        System.out.println("prev " + currentSpinSystem);
    }

    void firstPeak() {
        if (refPeakList != null) {
            Peak peak = refPeakList.getPeak(0);
            setPeak(peak);
        }
    }

    public void previousPeak() {
        if (currentPeak != null) {
            int peakIndex = currentPeak.getIndex();
            peakIndex--;
            if (peakIndex < 0) {
                peakIndex = 0;
            }
            Peak peak = refPeakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    public void nextPeak() {
        if (currentPeak != null) {
            int peakIndex = currentPeak.getIndex();
            peakIndex++;
            if (peakIndex >= refPeakList.size()) {
                peakIndex = refPeakList.size() - 1;
            }
            Peak peak = refPeakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    public void lastPeak() {
        if (refPeakList != null) {
            int peakIndex = refPeakList.size() - 1;
            Peak peak = refPeakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    public List<Peak> matchPeaks(String pattern) {
        List<Peak> result;
        if (pattern.startsWith("re")) {
            pattern = pattern.substring(2).trim();
            if (pattern.contains(":")) {
                String[] matchStrings = pattern.split(":");
                result = refPeakList.matchPeaks(matchStrings, true, true);
            } else {
                String[] matchStrings = pattern.split(",");
                result = refPeakList.matchPeaks(matchStrings, true, false);
            }
        } else {
            if (pattern.contains(":")) {
                if (pattern.charAt(0) == ':') {
                    pattern = " " + pattern;
                }
                if (pattern.charAt(pattern.length() - 1) == ':') {
                    pattern = pattern + " ";
                }

                String[] matchStrings = pattern.split(":");
                result = refPeakList.matchPeaks(matchStrings, false, true);
            } else {
                if (pattern.charAt(0) == ',') {
                    pattern = " " + pattern;
                }
                if (pattern.charAt(pattern.length() - 1) == ',') {
                    pattern = pattern + " ";
                }
                String[] matchStrings = pattern.split(",");
                result = refPeakList.matchPeaks(matchStrings, false, false);
            }
        }
        return result;
    }

    public void gotoPeakId(TextField idField) {
        if (refPeakList != null) {
            matchPeaks = Optional.empty();
            int id = Integer.MIN_VALUE;
            String idString = idField.getText().trim();
            if (idString.length() != 0) {
                try {
                    id = Integer.parseInt(idString);
                } catch (NumberFormatException nfE) {
                    List<Peak> peaks = matchPeaks(idString);
                    if (!peaks.isEmpty()) {
                        setPeak(peaks.get(0));
                        matchPeaks = Optional.of(peaks);
                        matchIndex = 0;
                    } else {
                        idField.setText("");
                    }
                }
                if (id != Integer.MIN_VALUE) {
                    if (id < 0) {
                        id = 0;
                    } else if (id >= refPeakList.size()) {
                        id = refPeakList.size() - 1;
                    }
                    Peak peak = refPeakList.getPeakByID(id);
                    setPeak(peak);
                }
            }
        }
    }

    void gotoNextMatch(int dir) {
        if (matchPeaks.isPresent()) {
            List<Peak> peaks = matchPeaks.get();
            if (!peaks.isEmpty()) {
                matchIndex += dir;
                if (matchIndex >= peaks.size()) {
                    matchIndex = 0;
                } else if (matchIndex < 0) {
                    matchIndex = peaks.size() - 1;
                }
                Peak peak = peaks.get(matchIndex);
                setPeak(peak);
            }
        }
    }

    void setDeleteStatus(ToggleButton button) {
        if (currentPeak != null) {
            if (button.isSelected()) {
                currentPeak.setStatus(-1);
            } else {
                currentPeak.setStatus(0);
            }
            peakNavigable.refreshPeakView(currentPeak);
        }
        updateDeleteStatus();
    }

    @Override
    public void peakListChanged(PeakEvent peakEvent) {
        if (peakEvent.getSource() instanceof PeakList) {
            PeakList sourceList = (PeakList) peakEvent.getSource();
            if (sourceList == refPeakList) {
                if (Platform.isFxApplicationThread()) {
                    peakNavigable.refreshPeakView();
                } else {
                    Platform.runLater(() -> {
                        peakNavigable.refreshPeakView();
                    }
                    );
                }
            }
        }
    }

    void loadYaml() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("RunAbout Yaml file");
            File file = fileChooser.showOpenDialog(null);
            if (file != null) {
                runAbout.loadYaml(file.toString());
                Map<String, Object> yamlData = runAbout.getYamlData();
                arrangeMenu.getItems().clear();
                Map<String, Map<String, List<String>>> arrangments = (Map<String, Map<String, List<String>>>) yamlData.get("arrangements");
                for (String arrangment : arrangments.keySet()) {
                    MenuItem item = new MenuItem(arrangment);
                    arrangeMenu.getItems().add(item);
                    item.setOnAction(e -> genWin(arrangment));
                }
            }
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }

    }


    /*
    def getDataset(datasets, type):
    if type in datasets:
        return datasets[type]
    else:
        return None


def getType(types, row, dDir):
    for type in types:
       if type['row'] == row and type['dir'] == dDir:
           return type['name']
    return ""

     */
    List<List<String>> getAtomsFromPatterns(List<String> patElems) {
        List<List<String>> allAtomPats = new ArrayList<>();
        for (int i = 0; i < patElems.size(); i++) {
            String pattern = patElems.get(i).trim();
            String[] resAtoms = pattern.split("\\.");
            String[] atomPats = resAtoms[1].split(",");
            List<String> atomPatList = new ArrayList<>();
            allAtomPats.add(atomPatList);
            for (String atomPat : atomPats) {
                if (atomPat.endsWith("-") || atomPat.endsWith("+")) {
                    int len = atomPat.length();
                    atomPat = atomPat.substring(0, len - 1);
                }
                atomPatList.add(atomPat.toUpperCase());
            }
        }
        return allAtomPats;
    }

    Optional<String> getDatasetName(Map<String, String> typeMap, String typeName) {
        Optional<String> dName = Optional.empty();
        if (typeMap.containsKey(typeName)) {
            dName = Optional.of(typeMap.get(typeName));
        }
        return dName;
    }

    Double[] getDimWidth(List<String> dims) {
        Double[] widths = new Double[dims.size()];
        int j = 0;
        for (String dim : dims) {
            Double width;
            int sepPos = dim.indexOf("_");
            System.out.println(dim + " " + sepPos);
            if (sepPos != -1) {
                width = Double.parseDouble(dim.substring(sepPos + 1));
            } else {
                width = null;
            }
            widths[j] = width;
            j++;
        }
        return widths;
    }

    void genWin(String arrangeName) {
        if (runAbout.isActive()) {
            Map<String, List<String>> rowCols = runAbout.getArrangements().get(arrangeName);
            List<String> rows = rowCols.get("rows");
            List<String> cols = rowCols.get("cols");
            int nCharts = rows.size() * cols.size();
            controller.setNCharts(nCharts);
            controller.arrange(rows.size());
            List<PolyChart> charts = controller.getCharts();
            widths = new Double[nCharts][];
            minOffset = 0;
            resOffsets = new int[cols.size()];
            intraResidue = new boolean[cols.size()];
            int iCol = 0;

            for (String col : cols) {
                String[] colElems = col.split("\\.");
                char resChar = colElems[0].charAt(0);
                int del = resChar - 'i';
                intraResidue[iCol] = !colElems[0].endsWith("-1");
                resOffsets[iCol++] = del;
                minOffset = Math.min(del, minOffset);
            }
            int iChart = 0;
            winPatterns.clear();
            for (String row : rows) {
                for (String col : cols) {
                    PolyChart chart = charts.get(iChart);
                    chart.clearDataAndPeaks();
                    String[] colElems = col.split("\\.");
                    Optional<String> typeName = runAbout.getTypeName(row, colElems[0]);
                    winPatterns.add(runAbout.getPatterns(row, colElems[0]));
                    List<String> dimNames = runAbout.getDimLabel(colElems[1]);
                    System.out.println(typeName);
                    if (typeName.isPresent()) {

                        Optional<Dataset> datasetOpt = runAbout.getDataset(typeName.get());
                        System.out.println(datasetOpt);
                        if (datasetOpt.isPresent()) {
                            Dataset dataset = datasetOpt.get();
                            PeakList peakList = runAbout.getPeakList(typeName.get());
                            String dName = dataset.getName();
                            List<String> datasets = Collections.singletonList(dName);
                            chart.setActiveChart();
                            chart.updateDatasets(datasets);
                            DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);

                            int[] iDims = runAbout.getIDims(dataset, typeName.get(), dimNames);
                            widths[iChart] = getDimWidth(dimNames);
                            for (int id = 0; id < widths[iChart].length; id++) {
                                System.out.println(id + " " + widths[iChart][id]);
                            }
                            dataAttr.setDims(iDims);
                            if (peakList != null) {
                                List<String> peakLists = Collections.singletonList(peakList.getName());
                                chart.updatePeakLists(peakLists);
                            }
                        }
                    }
                    iChart++;
                }
            }
            controller.setChartDisable(false);

            if (currentPeak != null) {
                drawWins(Collections.singletonList(currentPeak));
            } else {
                firstPeak(null);
            }

        }
    }

    void drawWins(List<Peak> peaks) {
        List<PolyChart> charts = controller.getCharts();
        int iChart = 0;
        for (PolyChart chart : charts) {
            int iCol = iChart % resOffsets.length;
            int resOffset = resOffsets[iCol] - minOffset;
            resOffset = resOffset >= peaks.size() ? 0 : resOffset;
            iCol = iCol >= peaks.size() ? 0 : iCol;
            Peak peak = peaks.get(iCol);
            chart.clearAnnotations();
            if ((peak != null) && (chart != null) && !chart.getDatasetAttributes().isEmpty()) {
                refreshChart(chart, iChart, peak);
            }
            iChart++;
        }
    }

    void drawSpinSystems(List<SpinSystem> spinSystems) {
        List<PolyChart> charts = controller.getCharts();
        int iChart = 0;
        for (PolyChart chart : charts) {
            int iCol = iChart % resOffsets.length;
            int resOffset = resOffsets[iCol] - minOffset;
            resOffset = resOffset >= spinSystems.size() ? 0 : resOffset;
            iCol = iCol >= spinSystems.size() ? 0 : iCol;
            SpinSystem spinSystem = spinSystems.get(iCol);
            if (spinSystem == null) {
                iChart++;
                continue;
            }
            Peak peak = spinSystem.getRootPeak();
            chart.clearAnnotations();
            List<List<String>> atomPatterns = getAtomsFromPatterns(winPatterns.get(iChart));
            if ((peak != null) && (chart != null) && !chart.getDatasetAttributes().isEmpty()) {
                refreshChart(chart, iChart, peak);
                DatasetAttributes dataAttr = (DatasetAttributes) chart.getDatasetAttributes().get(0);

                for (PeakMatch peakMatch : spinSystem.peakMatches()) {
                    PeakDim peakDim = peakMatch.getPeak().getPeakDim(dataAttr.getLabel(1));
                    if (peakDim != null) {
                        int iDim = peakDim.getSpectralDim();
                        int atomIndex = peakMatch.getIndex(iDim);
                        String aName = SpinSystem.getAtomName(atomIndex).toUpperCase();
                        System.out.println("aname " + aName + " " + atomPatterns.get(iDim).toString());
                        if (!atomPatterns.get(iDim).contains(aName)) {
                            continue;

                        }
                        boolean isIntra = peakMatch.getIntraResidue(iDim);
                        final double f1;
                        final double f2;
                        Color color;
                        if (intraResidue[iCol]) {
                            if (isIntra) {
                                color = Color.BLUE;
                                f1 = 0.5;
                                f2 = 1.0;
                            } else {
                                color = Color.GREEN;
                                f1 = 0.0;
                                f2 = 0.5;
                            }

                        } else {
                            if (isIntra) {
                                continue;
                            } else {
                                color = Color.GREEN;
                                f1 = 0.0;
                                f2 = 1.0;
                            }
                        }
                        if (isIntra && !intraResidue[iCol]) {
                            continue;
                        }
                        if (isIntra && intraResidue[iCol]) {

                        }

                        double ppm = spinSystem.getValue(isIntra ? 1 : 0, atomIndex);
                        AnnoLine annoLine = new AnnoLine(f1, ppm, f2, ppm, CanvasAnnotation.POSTYPE.FRACTION, CanvasAnnotation.POSTYPE.WORLD);
                        annoLine.setStroke(color);
                        System.out.println("draw at " + iDim + " " + aName + " " + +ppm);
                        chart.addAnnotation(annoLine);
                        chart.refresh();
                    }
                }
            }
            iChart++;
        }
    }

    void refreshChart(PolyChart chart, int iChart, Peak peak) {
        DatasetAttributes dataAttr = (DatasetAttributes) chart.getDatasetAttributes().get(0);
        int cDim = chart.getNDim();
        int aDim = dataAttr.nDim;
        Double[] ppms = new Double[cDim];
        for (int i = 0; i < aDim; i++) {
            PeakDim peakDim = peak.getPeakDim(dataAttr.getLabel(i));
            System.out.println(i + " " + peak.getName() + " " + dataAttr.getLabel(i) + " " + peakDim);
            if ((widths[iChart] != null) && (peakDim != null)) {
                ppms[i] = Double.valueOf(peakDim.getChemShiftValue());
                if (widths[iChart][i] == null) {
                    chart.full(i);
                } else {
                    double pos;
                    if (chart.getAxMode(i) == PPM) {
                        pos = ppms[i];
                    } else {
                        int dDim = dataAttr.getDim(i);
                        pos = dataAttr.getDataset().ppmToDPoint(dDim, ppms[i]);
                        System.out.println(i + " " + aDim + " " + dDim + " " + ppms[i] + " " + pos);
                    }
                    chart.moveTo(i, pos, widths[iChart][i]);
                }
            } else {
                chart.full(i);
            }
        }
        chart.refresh();
    }

    /*
    hPPM = [7.7,8.3]
    hCPPM = 8.0
    nPPM = [117.0,123.0]
    nCPPM = 117.0
    for row in rows:
        for col in cols:
            dDir,label = col.split('.')
            type = getType(yd['types'], row,dDir)
            datasetName = getDataset(yd['datasets'],type)
            dims = yd['dims'][label]
            iDims = getIDims(datasetName,dims)
            print iChart,row,col,type,datasetName,dims,iDims
            nw.active(iChart)
            nw.datasets(datasetName)
            nw.setDims(datasetName, iDims)
            #nw.unsync()
            #nw.proportional()
            if label == "HC":
                nw.lim(x=hPPM,y=nCPPM)
                nw.full('y')
            elif label == "NC":
                nw.lim(x=nPPM,y=hCPPM)
                nw.full('y')
            elif label == "HN":
                nw.full()
                nw.full('z')
            nw.draw()


     */
}
