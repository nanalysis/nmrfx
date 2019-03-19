package org.nmrfx.processor.gui.tools;

import org.nmrfx.processor.gui.*;
import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakEvent;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.PeakListener;
import org.nmrfx.processor.datasets.peaks.SpinSystem;
import org.nmrfx.processor.datasets.peaks.SpinSystems;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.yaml.snakeyaml.Yaml;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Bruce Johnson
 */
public class RunAbout implements PeakListener {

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
    SpinSystems spinSystems = new SpinSystems();
    boolean useSpinSystem = false;
    Map<String, Object> yamlData = null;
    Double[][] widths;

    private RunAbout(PeakNavigable peakNavigable) {
        this.peakNavigable = peakNavigable;
    }

    private RunAbout(PeakNavigable peakNavigable, Consumer closeAction) {
        this.peakNavigable = peakNavigable;
        this.closeAction = closeAction;
    }

    public static RunAbout create() {
        FXMLController controller = FXMLController.create();
        controller.getStage();

        ToolBar navBar = new ToolBar();
        controller.getBottomBox().getChildren().add(navBar);
        RunAbout runAbout = new RunAbout(controller);

        runAbout.initialize(navBar);
        runAbout.controller = controller;
        return runAbout;
    }

    public RunAbout onClose(Consumer closeAction) {
        this.closeAction = closeAction;
        return this;
    }

    public RunAbout showAtoms() {
        this.showAtoms = true;
        return this;
    }

    public ToolBar getToolBar() {
        return navigatorToolBar;
    }

    public void close() {
        closeAction.accept(this);
    }

    RunAbout initialize(ToolBar toolBar) {
        initPeakNavigator(toolBar);
        return this;
    }

    void initPeakNavigator(ToolBar toolBar) {
        this.navigatorToolBar = toolBar;
        peakIdField = new TextField();
        peakIdField.setMinWidth(75);
        peakIdField.setMaxWidth(75);
        RunAbout navigator = this;

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
        List<PeakList> peakLists = new ArrayList<>();
        peakLists.add(PeakList.get("hnco"));
        spinSystems.assemble(peakLists);
    }

    public void updatePeakListMenu() {
        peakListMenuButton.getItems().clear();

        for (String peakListName : PeakList.peakListTable.keySet()) {
            MenuItem menuItem = new MenuItem(peakListName);
            menuItem.setOnAction(e -> {
                RunAbout.this.setPeakList(peakListName);
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
        RunAbout.this.setPeakList(refPeakList);
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

    public void setPeak(Peak peak) {
        currentPeak = peak;
        if (peak != null) {
            drawWins(peak);
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
        if (currentPeak.getStatus() < 0) {
            deleteButton.setSelected(true);
            peakIdField.setBackground(deleteBackground);
        } else {
            deleteButton.setSelected(false);
            peakIdField.setBackground(defaultBackground);
        }

    }

    private void setPeakIdField() {
        if (currentPeak == null) {
            peakIdField.setText("");
        } else {
            peakIdField.setText(String.valueOf(currentPeak.getIdNum()));
        }

    }

    public void previousPeak(ActionEvent event) {
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

    public void firstPeak(ActionEvent event) {
        Peak peak = null;
        if (useSpinSystem) {
            SpinSystem spinSystem = spinSystems.get(0);
            peak = spinSystem.getRootPeak();
        } else if (refPeakList != null) {
            peak = refPeakList.getPeak(0);
        }
        if (peak != null) {
            setPeak(peak);
        }

    }

    public void nextPeak(ActionEvent event) {
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

    public void lastPeak(ActionEvent event) {
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
            loadYaml("sandbox/runabout.yaml");
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
    }

    void loadYaml(String fileName) throws FileNotFoundException, IOException {
        try (InputStream input = new FileInputStream(fileName)) {
            Yaml yaml = new Yaml();
            yamlData = (Map<String, Object>) yaml.load(input);
        }
        arrangeMenu.getItems().clear();
        Map<String, Map<String, List<String>>> arrangments = (Map<String, Map<String, List<String>>>) yamlData.get("arrangements");
        for (String arrangment : arrangments.keySet()) {
            MenuItem item = new MenuItem(arrangment);
            arrangeMenu.getItems().add(item);
            item.setOnAction(e -> genWin(arrangment));
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
    Optional<String> getTypeName(List<Map<String, String>> typeList, String row, String dDir) {
        Optional<String> typeName = Optional.empty();
        for (Map<String, String> typeMap : typeList) {
            String typeRow = typeMap.get("row");
            String typeDir = typeMap.get("dir");
            if (row.equals(typeRow) && dDir.equals(typeDir)) {
                typeName = Optional.of(typeMap.get("name"));
                break;
            }
        }
        return typeName;
    }

    Optional<String> getDatasetName(Map<String, String> typeMap, String typeName) {
        Optional<String> dName = Optional.empty();
        if (typeMap.containsKey(typeName)) {
            dName = Optional.of(typeMap.get(typeName));
        }
        return dName;
    }

    int[] getIDims(String datasetName, List<String> dims) {
        Dataset dataset = Dataset.getDataset(datasetName);
        int nDim = dataset.getNDim();
        int[] iDims = new int[dims.size()];
        int j = 0;
        for (String dim : dims) {
            String dimName;
            int sepPos = dim.indexOf("_");
            if (sepPos != -1) {
                dimName = dim.substring(0, sepPos);
            } else {
                dimName = dim;
            }
            for (int iDim = 0; iDim < nDim; iDim++) {
                // fixme need more sophisticated test of label match
                if (dimName.charAt(0) == dataset.getLabel(iDim).charAt(0)) {
                    iDims[j] = iDim;
                }
            }
            j++;
        }
        return iDims;
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
        if (yamlData == null) {

        } else {
            Map<String, Map<String, List<String>>> arrange = (Map<String, Map<String, List<String>>>) yamlData.get("arrangements");
            Map<String, List<String>> rowCols = arrange.get(arrangeName);
            List<String> rows = rowCols.get("rows");
            List<String> cols = rowCols.get("cols");
            int nCharts = rows.size() * cols.size();
            controller.setNCharts(nCharts);
            controller.arrange(rows.size());
            List<PolyChart> charts = controller.getCharts();
            List<Map<String, String>> typeList = (List<Map<String, String>>) yamlData.get("types");
            Map<String, String> datasetMap = (Map<String, String>) yamlData.get("datasets");
            Map<String, List<String>> dimLabels = (Map<String, List<String>>) yamlData.get("dims");
            widths = new Double[nCharts][];

            int iChart = 0;
            for (String row : rows) {
                for (String col : cols) {
                    System.out.println("row " + row + " col " + col);
                    String[] colElems = col.split("\\.");
                    Optional<String> typeName = getTypeName(typeList, row, colElems[0]);
                    List<String> dimNames = dimLabels.get(colElems[1]);
                    if (typeName.isPresent()) {
                        Optional<String> dName = getDatasetName(datasetMap, typeName.get());
                        if (dName.isPresent()) {
                            Dataset dataset = Dataset.getDataset(dName.get());
                            if (dataset != null) {
                                List<String> datasets = Collections.singletonList(dName.get());
                                PolyChart chart = charts.get(iChart);
                                chart.setActiveChart();
                                System.out.println("add " + dName.get() + " " + chart);
                                chart.updateDatasets(datasets);
                                System.out.println("nda " + chart.getDatasetAttributes().size());
                                DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                                int[] iDims = getIDims(dName.get(), dimNames);
                                widths[iChart] = getDimWidth(dimNames);
                                for (int id = 0; id < widths[iChart].length; id++) {
                                    System.out.println(id + " " + widths[iChart][id]);
                                }
                                dataAttr.setDims(iDims);
                                PeakList peakList = PeakList.getPeakListForDataset(dName.get());
                                if (peakList != null) {
                                    List<String> peakLists = Collections.singletonList(peakList.getName());
                                    chart.updatePeakLists(peakLists);
                                }
                            }
                        }
                    }
                    iChart++;
                }
            }
            controller.setChartDisable(false);

            if (currentPeak != null) {
                drawWins(currentPeak);
            } else {
                firstPeak(null);
            }

        }
    }

    void drawWins(Peak peak) {
        List<PolyChart> charts = controller.getCharts();
        int iChart = 0;
        for (PolyChart chart : charts) {
            if ((chart != null) && !chart.getDatasetAttributes().isEmpty()) {
                DatasetAttributes dataAttr = (DatasetAttributes) chart.getDatasetAttributes().get(0);
                int cDim = chart.getNDim();
                int aDim = dataAttr.nDim;
                Double[] ppms = new Double[cDim];
                System.out.println("chart " + iChart + " " + chart);
                for (int i = 0; i < aDim; i++) {
                    PeakDim peakDim = peak.getPeakDim(dataAttr.getLabel(i));
                    if (peakDim != null) {
                        ppms[i] = Double.valueOf(peakDim.getChemShiftValue());
                        if (widths[iChart][i] == null) {
                            System.out.println("full " + i);
                            chart.full(i);
                        } else {
                            chart.moveTo(i, ppms[i], widths[iChart][i]);
                            System.out.println("goto " + ppms[i] + " " + widths[iChart][i]);
                        }
                    } else {
                        System.out.println("fullx " + i);
                        chart.full(i);
                    }
                }
                chart.refresh();
            }
            iChart++;
        }
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
