package org.nmrfx.analyst.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.tools.ScanTable;
import org.nmrfx.analyst.gui.tools.ScannerTool;
import org.nmrfx.chart.XYCanvasChart;
import org.nmrfx.peaks.*;
import org.nmrfx.peaks.PeakPaths.PATHMODE;
import org.nmrfx.processor.datasets.peaks.PathFitter;
import org.nmrfx.processor.datasets.peaks.PeakPathAnalyzer;
import org.nmrfx.processor.gui.*;
import org.nmrfx.processor.gui.annotations.AnnoPolyLine;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakDisplayParameters;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.processor.optimization.FitUtils;
import org.nmrfx.utils.GUIUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 *
 * @author Bruce Johnson
 */
public class PathTool implements PeakNavigable, ControllerTool {

    static Color INACTIVE_COLOR = Color.web("#681A4A");
    static Color ACTIVE_COLOR = Color.web("#EE442F");

    VBox vBox;
    FXMLController controller;
    PolyChart chart;
    ToolBar toolBar;
    ToolBar fitBar;
    Consumer closeAction;
    MenuButton actionMenu;
    Menu selectMenu;
    PeakNavigator peakNavigator;
    TextField radiusField;
    TextField tolField;
    Label[] fitFields;
    Label nField;
    Button fitButton;
    Button addButton;
    PeakPaths peakPaths;
    ObservableList<PeakPath> activePaths = FXCollections.observableArrayList();

    PathPlotTool plotTool;

    public PathTool(FXMLController controller, Consumer<PathTool> closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
        this.chart = controller.getActiveChart();
    }

    private void close(Object o) {
        closeAction.accept(this);
    }

    @Override
    public void close() {
        closeAction.accept(this);
    }

    public VBox getBox() {
        return vBox;
    }

    public void initialize(VBox vBox) {
        toolBar = new ToolBar();
        fitBar = new ToolBar();
        this.vBox = vBox;
        vBox.getChildren().addAll(toolBar, fitBar);
        peakNavigator = PeakNavigator.create(this).onClose(this::close).initialize(toolBar);
        peakNavigator.setPeakList();
        controller.addScaleBox(peakNavigator, toolBar);

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
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_BACKWARD, "", MainApp.ICON_SIZE_STR, MainApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> firstDataset(e));
        dataButtons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BACKWARD, "", MainApp.ICON_SIZE_STR, MainApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> previousDataset(e));
        dataButtons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FORWARD, "", MainApp.ICON_SIZE_STR, MainApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> nextDataset(e));
        dataButtons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_FORWARD, "", MainApp.ICON_SIZE_STR, MainApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> lastDataset(e));
        dataButtons.add(bButton);

        Button plusButton = GlyphsDude.createIconButton(FontAwesomeIcon.PLUS, "", MainApp.ICON_SIZE_STR, MainApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        plusButton.setOnAction(e -> addPeakToPath());
        dataButtons.add(plusButton);

        Button minusButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS, "", MainApp.ICON_SIZE_STR, MainApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        minusButton.setOnAction(e -> removePeakFromPath());
        dataButtons.add(minusButton);

        actionMenu = new MenuButton("Actions");
        MenuItem loadTitrationItem = new MenuItem("Load Titration Data...");
        loadTitrationItem.setOnAction(e -> loadPathData(PATHMODE.TITRATION));
        actionMenu.getItems().add(loadTitrationItem);

        MenuItem loadPressureItem = new MenuItem("Load Pressure Data...");
        loadPressureItem.setOnAction(e -> loadPathData(PATHMODE.PRESSURE));
        actionMenu.getItems().add(loadPressureItem);

        selectMenu = new Menu("Select...");
        actionMenu.getItems().add(selectMenu);

        MenuItem findPathMenuItem = new MenuItem("Find Paths");
        findPathMenuItem.setOnAction(e -> findPaths());
        actionMenu.getItems().add(findPathMenuItem);

        MenuItem extendPathMenuItem = new MenuItem("Extend Paths");
        extendPathMenuItem.setOnAction(e -> extendPaths());
        actionMenu.getItems().add(extendPathMenuItem);

        MenuItem extendAPathMenuItem = new MenuItem("Extend Path");
        extendAPathMenuItem.setOnAction(e -> extendPath());
        actionMenu.getItems().add(extendAPathMenuItem);

        MenuItem drawMenuItem = new MenuItem("Draw Paths");
        drawMenuItem.setOnAction(e -> drawPath());
        actionMenu.getItems().add(drawMenuItem);

        MenuItem addPathMenuItem = new MenuItem("Add Path");
        addPathMenuItem.setOnAction(e -> addPath());
        actionMenu.getItems().add(addPathMenuItem);

        MenuItem refreshPathsMenuItem = new MenuItem("Refresh Paths");
        refreshPathsMenuItem.setOnAction(e -> refreshPaths());
        actionMenu.getItems().add(refreshPathsMenuItem);

        MenuItem clearPathsMenuItem = new MenuItem("Clear Paths");
        clearPathsMenuItem.setOnAction(e -> clearPaths());
        actionMenu.getItems().add(clearPathsMenuItem);

        MenuItem clearPathMenuItem = new MenuItem("Clear Path");
        clearPathMenuItem.setOnAction(e -> clearPath());
        actionMenu.getItems().add(clearPathMenuItem);

        MenuItem fitAllPathsMenuItem = new MenuItem("Fit All Paths");
        fitAllPathsMenuItem.setOnAction(e -> fitAllPathes());
        actionMenu.getItems().add(fitAllPathsMenuItem);

        MenuItem addAllPathsMenuItem = new MenuItem("Add All Paths");
        addAllPathsMenuItem.setOnAction(e -> addAllPaths());
        actionMenu.getItems().add(addAllPathsMenuItem);

        actionMenu.showingProperty().addListener(e -> updatePathSelectMenu());

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
        tolField = new TextField("0.2");
        toolBar.getItems().add(tolField);

        Pane fillerf1 = new Pane();
        fillerf1.setMinWidth(5);
        Pane fillerf2 = new Pane();
        fillerf2.setMinWidth(20);

        fitButton = new Button("Fit");
        fitButton.setOnAction(e -> fitPath());
        fitButton.setDisable(true);

        addButton = new Button("Add");
        addButton.setOnAction(e -> addPathToTable());
        addButton.setDisable(true);

        Button showButton = new Button("Show");
        showButton.setOnAction(e -> showPlotTool());

        nField = new Label("");
        nField.setPrefWidth(100);

        fitBar.getItems().addAll(nField, fillerf1, fitButton, addButton, showButton, fillerf2);
        setActionMenuDisabled(true);
        // The different control items end up with different heights based on font and icon size,
        // set all the items to use the same height
        toolBar.heightProperty().addListener((observable, oldValue, newValue) -> GUIUtils.toolbarAdjustHeights(Arrays.asList(toolBar, fitBar)));
        ChangeListener<List<Peak>> selPeakListener = new ChangeListener<List<Peak>>() {

            @Override
            public void changed(ObservableValue<? extends List<Peak>> observable, List<Peak> oldValue, List<Peak> newValue) {
                boolean isEmpty = newValue.isEmpty();
                plusButton.setDisable(isEmpty);
                minusButton.setDisable(isEmpty);
            }
        };
        controller.addSelectedPeakListener(selPeakListener);

    }

    void updatePathSelectMenu() {
        selectMenu.getItems().clear();
        Collection<String> peakPathNames = PeakPaths.getNames();
        for (String name : peakPathNames) {
            MenuItem menuItem = new MenuItem(name);
            selectMenu.getItems().add(menuItem);
            menuItem.setOnAction(e -> selectPathData(name));
        }

    }

    void setActionMenuDisabled(boolean state) {
        int i = 0;
        for (MenuItem item : actionMenu.getItems()) {
            if (i > 2) {
                item.setDisable(state);
            }
            i++;
        }

    }

    public void initFitFields(int nPars) {
        Background bg = new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY));
        if ((fitFields == null) || (fitFields.length != nPars)) {
            if (fitFields != null) {
                fitBar.getItems().remove(3, 3 + fitFields.length);
            }
            fitFields = new Label[nPars];
            for (int i = 0; i < fitFields.length; i++) {
                fitFields[i] = new Label("");
                fitFields[i].setPrefWidth(170);
                fitFields[i].setBackground(bg);
                Pane fillerf2 = new Pane();
                fillerf2.setMinWidth(15);

                fitBar.getItems().addAll(fillerf2, fitFields[i]);
            }
        } else {
            for (int i = 0; i < fitFields.length; i++) {
                fitFields[i].setText("");
            }
        }

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
        for (PeakListAttributes peakAttr : chart.getPeakListAttributes()) {
            peakAttr.setColorType(PeakDisplayParameters.ColorTypes.Status);
        }
    }

    void selectPathData(String name) {
        activePaths.clear();
        peakPaths = PeakPaths.get(name);
        setupChart(peakPaths.getDatasetNames());
        setupPlotTable();
        if (peakPaths.getPathMode() == PATHMODE.PRESSURE) {
            plotTool.show("Pressure", "Shift Delta");
        } else {
            plotTool.show("Concentration", "Shift Delta");
        }
        setActionMenuDisabled(false);
        addActivePathsToTable();
        drawPath();
    }

    void loadPathDataFromScanTable(PATHMODE pathMode) {
        ScannerTool scannerTool = (ScannerTool) AnalystApp.getAnalystApp().getScannerTool();
        activePaths.clear();
        ScanTable scanTable = scannerTool.getScanTable();
        List<Double> x0List = new ArrayList<>();
        List<Double> x1List = new ArrayList<>();
        List<String> datasetNames = new ArrayList<>();
        String x0ColumnName = pathMode == PATHMODE.PRESSURE ? "pressure" : "ligand";
        String x1ColumnName = "target";
        if (!scanTable.getHeaders().contains(x0ColumnName)) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No column named " + x0ColumnName);
            alert.showAndWait();
            return;
        }
        scanTable.getItems().forEach(item -> {
            File file = new File(item.getDatasetName());
            datasetNames.add(file.getName());
            item.getExtraAsDouble(x0ColumnName).ifPresent(value -> x0List.add(value));
            if (scanTable.getHeaders().contains(x1ColumnName)) {
                item.getExtraAsDouble(x1ColumnName).ifPresent(value -> x1List.add(value));
            }
        });
        if (datasetNames.isEmpty() || (datasetNames.size() != x0List.size())) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error getting data from Scanner");
            alert.showAndWait();
            return;
        }
        peakPaths = PeakPaths.loadPathData(pathMode, datasetNames, x0List, x1List, scanTable.getScanDir().getName());
    }

    void loadPathDataFromFile(PATHMODE pathMode) {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            activePaths.clear();
            try {
                peakPaths = PeakPaths.loadPathData(pathMode, file);
                if (peakPaths == null) {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Error reading peak path file ");
                    alert.showAndWait();
                    return;
                }
            } catch (IOException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Error reading file " + ex.getMessage());
                alert.showAndWait();
                return;
            } catch (IllegalArgumentException iaE) {
                Alert alert = new Alert(Alert.AlertType.ERROR, iaE.getMessage());
                alert.showAndWait();
                return;
            }
        }
    }
    void loadPathData(PATHMODE pathMode) {
        peakPaths = null;
        ScannerTool scannerTool = (ScannerTool) AnalystApp.getAnalystApp().getScannerTool();
        if (scannerTool != null) {
            loadPathDataFromScanTable(pathMode);
        } else {
            loadPathDataFromFile(pathMode);
        }
        if (peakPaths != null) {
            setupChart(peakPaths.getDatasetNames());
            setupPlotTable();
            setActionMenuDisabled(false);
        }
    }

    void setupPlotTable() {
        if (peakPaths != null) {
            List<String> colNames = new ArrayList<>();
            colNames.add("Peak");
            String[] parNames = peakPaths.getParNames();
            for (String col : parNames) {
                colNames.add(col);
                colNames.add(col + "Dev");
            }
            plotTool = new PathPlotTool(this, colNames);
        }

    }

    boolean pathUsable(PeakPath path) {
        return path.getNValid() > 3;
    }

    void fitAllPathes() {
        if (peakPaths != null) {
            for (PeakPath path : peakPaths.getPaths()) {
                try {
                    if (pathUsable(path)) {
                        PathFitter fitPath = new PathFitter();
                        fitPath.setup(peakPaths, path);
                        fitPath.fit();
                    }
                } catch (Exception ex) {
                    ExceptionDialog eDialog = new ExceptionDialog(ex);
                    eDialog.showAndWait();
                    break;
                }
            }
        }

    }

    void addAllPaths() {
        if (peakPaths != null) {
            for (PeakPath path : peakPaths.getPaths()) {
                if (pathUsable(path)) {
                    if (path.hasPars()) {
                        if (activePaths.contains(path)) {
                            activePaths.remove(path);
                        }
                        activePaths.add(path);
                    }
                }
            }
            plotTool.updateTable(activePaths);
        }
    }

    void fitPath() {
        if (peakPaths != null) {

            Peak peak = peakNavigator.getPeak();
            PathFitter fitPath = new PathFitter();
            try {
                PeakPath path = peakPaths.getPath(peak);
                if (pathUsable(path)) {
                    fitPath.setup(peakPaths, path);
                    fitPath.fit();
                    updatePathInfo(peak, fitPath);
                }
            } catch (Exception ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        }
    }

    void fitPathsGrouped() {
        if (peakPaths != null) {
            PathFitter fitPath = new PathFitter();
            List<PeakPath> fitPaths = plotTool.getSelected();
            if (fitPaths.isEmpty()) {
                fitPaths.addAll(activePaths);

            }
            if (!fitPaths.isEmpty()) {
                try {
                    fitPath.setup(peakPaths, fitPaths);
                    fitPath.fit();
                    plotTool.tableView.refresh();
                    showXYPaths(fitPaths);
                } catch (Exception ex) {
                    ExceptionDialog eDialog = new ExceptionDialog(ex);
                    eDialog.showAndWait();
                }
            }
        }
    }

    void fitPathsIndividual() {
        PathFitter fitPath = new PathFitter();
        List<PeakPath> fitPaths = plotTool.getSelected();
        if (fitPaths.isEmpty()) {
            fitPaths.addAll(activePaths);
        }
        if (!fitPaths.isEmpty()) {
            try {
                for (PeakPath path : fitPaths) {
                    fitPath.setup(peakPaths, path);
                    fitPath.fit();
                }
                plotTool.tableView.refresh();
                showXYPaths(fitPaths);
            } catch (Exception ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        }
    }

    void addPathToTable() {
        if (peakPaths != null) {
            Peak peak = peakNavigator.getPeak();
            PeakPath path = peakPaths.getPath(peak);
            if (path != null) {
                if (activePaths.contains(path)) {
                    activePaths.remove(path);

                }
                activePaths.add(path);
                plotTool.updateTable(activePaths);
                path.setActive(true);
                plotTool.selectRow(path);

            }
        }
    }

    void addActivePathsToTable() {
        activePaths.clear();
        for (PeakPath path : peakPaths.getPaths()) {
            if (path.isActive()) {
                activePaths.add(path);
            }
        }
        plotTool.updateTable(activePaths);
    }

    void clearPath() {
        if (peakPaths != null) {
            Peak startPeak = peakNavigator.getPeak();
            if (startPeak != null) {
                peakPaths.clearPath(startPeak);
                peakPaths.initPath(startPeak);
            }
            drawPath();
        }
    }

    void addPeakToPath() {
        if (peakPaths != null) {

            Peak startPeak = peakNavigator.getPeak();
            List<Peak> selPeaks = chart.getSelectedPeaks();
            System.out.println("selp " + selPeaks.size() + " " + startPeak);
            if (selPeaks.size() == 1) {
                if (startPeak != null) {
                    peakPaths.addPeak(startPeak, selPeaks.get(0));
                    PeakPath path = peakPaths.getPath(startPeak);
                    if (path.getNValid() > 3) {
                        path.confirm();
                    }
                    updatePathInfo(startPeak);
                }
            }
            drawPath();
        }
    }

    void removePeakFromPath() {
        if (peakPaths != null) {

            Peak startPeak = peakNavigator.getPeak();
            List<Peak> selPeaks = chart.getSelectedPeaks();
            if (selPeaks.size() == 1) {
                if (startPeak != null) {
                    peakPaths.removePeak(startPeak, selPeaks.get(0));
                }
            }
            drawPath();
            updatePathInfo(startPeak);

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
            boolean state = (i == active);
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
                active = i;
                break;
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
        PeakPathAnalyzer.checkListsForUnambigous(peakPaths, radius);
    }

    void setStatus() {
        double radius = Double.parseDouble(radiusField.getText());
        PeakPathAnalyzer.setStatus(peakPaths.getPathMap(), radius, 1.0);

    }

    void clearPaths() {
        if (peakPaths != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Clear all paths", ButtonType.CANCEL, ButtonType.YES);
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    peakPaths.clearPaths();
                }
            });
        }
    }

    void findPaths() {
        if (peakPaths != null) {
            peakPaths.clearPaths();
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
                PeakPathAnalyzer.checkListsForUnambigous(peakPaths, radius);
                PeakPathAnalyzer.setStatus(peakPaths.getPathMap(), radius, lim);

            }
            chart.refresh();
            drawPath();
        }
    }

    void extendPath() {
        if (peakPaths != null) {

            double radius = Double.parseDouble(radiusField.getText());
            double lim = Double.parseDouble(tolField.getText());
            Peak startPeak = peakNavigator.getPeak();
            PeakPathAnalyzer.extendPath(peakPaths, startPeak, radius, lim);
            PeakPathAnalyzer.setStatus(peakPaths.getPathMap(), radius, 1.0);
            drawPath();
        }
    }

    void extendPaths() {
        if (peakPaths != null) {

            double radius = Double.parseDouble(radiusField.getText());
            double lim = 1.0;
            PeakPathAnalyzer.extendPaths(peakPaths, radius, lim);
            PeakPathAnalyzer.setStatus(peakPaths.getPathMap(), radius, 1.0);
            drawPath();
        }
    }

    void addPath() {
        if (peakPaths != null) {

            Peak lastPeak = null;
            List<Peak> selPeaks = chart.getSelectedPeaks();
            if (selPeaks.size() == 1) {
                lastPeak = selPeaks.get(0);
            }

            double radius = Double.parseDouble(radiusField.getText());
            Peak startPeak = peakNavigator.getPeak();
            if (startPeak != null) {
                List<PeakDistance> peakDists = PeakPathAnalyzer.scan(peakPaths, startPeak, radius, 2.0, 4, lastPeak, true);
                for (PeakDistance peakDist : peakDists) {
                    if (peakDist != null) {
                        peakDist.getPeak().setStatus(1);
                        System.out.println(peakDist.getPeak());
                    }
                }
                drawPath();
                updatePathInfo(startPeak);
            }
        }
    }

    void drawPath() {
        if (peakPaths != null) {

            chart.clearAnnotations();
            Collection<PeakPath> paths = peakPaths.getPaths();

            for (PeakPath path : paths) {
                int nValid = path.getNValid();

                if (nValid > 2) {
                    Color color = path.confirmed() ? ACTIVE_COLOR : INACTIVE_COLOR;
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
                        annoPolyLine.setStroke(color);
                        annoPolyLine.setLineWidth(3.0);
                        annoPolyLine.setClipInAxes(true);
                        chart.addAnnotation(annoPolyLine);
                    }
                }
            }
        }
        chart.refresh();
        chart.refresh();
    }

    void showPlotTool() {
        if (peakPaths == null) {
            return;
        }
        if (peakPaths.getPathMode() == PATHMODE.PRESSURE) {
            plotTool.show("Pressure", "Shift Delta");
        } else {
            plotTool.show("Concentration", "Shift Delta");
        }
    }

    void updatePathInfo(Peak peak) {
        updatePathInfo(peak, null);
    }

    void updatePathInfo(Peak peak, PathFitter fitPath) {
        if (peakPaths == null) {
            return;
        }

        PeakPath path = peakPaths.getPath(peak);
        nField.setText("");
        if (path != null) {
            int nPeaks = path.getPeakDistances().size();
            int nValid = path.getNValid();
            boolean confirmed = path.confirmed();
            boolean isComplete = path.isComplete();
            boolean isFree = path.isFree();
            nField.setText(String.format("%2d of %2d", nValid, nPeaks));
            if (fitFields != null) {
                for (Label label : fitFields) {
                    label.setText("");
                }
            }
            if (pathUsable(path)) {
                fitButton.setDisable(false);
                addButton.setDisable(false);

                if (fitPath == null) {
                    fitPath = new PathFitter();
                    fitPath.setup(peakPaths, path);
                }

                double[][] xValues = fitPath.getX();
                double[][] yValues = fitPath.getY();
                if (peakPaths.getPathMode() == PATHMODE.PRESSURE) {
                    plotTool.show("Pressure", "Shift Delta");
                } else {
                    plotTool.show("Concentration", "Shift Delta");
                }
                plotTool.clear();
                Color color = XYCanvasChart.colors[0];

                for (int iY = 0; iY < yValues.length; iY++) {
                    plotTool.getChart().addLines(xValues[0], yValues[iY], true, color);
                }

                double[] fitPars = path.getFitPars();
                double[] fitErrs = path.getFitErrs();
                String[] parNames = peakPaths.getParNames();
                if (fitPars != null) {
                    initFitFields(fitPars.length);

                    for (int i = 0; i < fitPars.length; i++) {
                        fitFields[i].setText(String.format("%s= %.3f +/- %.3f", parNames[i], fitPars[i], fitErrs[i]));
                    }
                    double first = 0.0;
                    double last = FitUtils.getMaxValue(peakPaths.getXValues()[0]);
                    if (peakPaths.getPathMode() == PATHMODE.TITRATION) {
                        double[][] xy = fitPath.getSimValues(fitPars, first, last, 100, xValues[1][0]);
                        plotTool.getChart().addLines(xy[0], xy[1], false, color);
                    } else if (peakPaths.getPathMode() == PATHMODE.PRESSURE) {
                        double[][] xy = fitPath.getPressureSimValues(fitPars, first, last, 100);
                        for (int i = 1; i < xy.length; i++) {
                            plotTool.getChart().addLines(xy[0], xy[i], false, color);
                        }
                    }
                }
                plotTool.selectRow(path);

            } else {
                fitButton.setDisable(true);
                addButton.setDisable(true);
                plotTool.clear();
            }
        }
    }

    public void clearXYPath() {
        plotTool.clear();
    }

    public void showXYPaths(List<PeakPath> paths) {
        if (peakPaths != null) {
            if (peakPaths.getPathMode() == PATHMODE.PRESSURE) {
                plotTool.show("Pressure", "Shift Delta");
            } else {
                plotTool.show("Concentration", "Shift Delta");
            }
            plotTool.clear();
            int iSeries = 0;
            for (PeakPath path : paths) {
                Color color = XYCanvasChart.colors[iSeries % XYCanvasChart.colors.length];
                showXYPath(path, color);
                iSeries++;
            }
        }
    }

    public void showXYPath(PeakPath path, Color color) {
        PathFitter fitPath = new PathFitter();
        fitPath.setup(peakPaths, path);

        double[][] xValues = fitPath.getX();
        double[][] yValues = fitPath.getY();
        for (int iY = 0; iY < yValues.length; iY++) {
            plotTool.getChart().addLines(xValues[0], yValues[iY], true, color);
        }

        double[] fitPars = path.getFitPars();
        double[] fitErrs = path.getFitErrs();
        if (fitPars != null) {
//            initFitFields(fitPars.length);

//            for (int i = 0; i < fitPars.length; i++) {
//                fitFields[i].setText(String.format("%.3f +/- %.3f", fitPars[i], fitErrs[i]));
//            }
            double first = 0.0;
            double last = FitUtils.getMaxValue(peakPaths.getXValues()[0]);
            if (peakPaths.getPathMode() == PATHMODE.TITRATION) {
                double[][] xy = fitPath.getSimValues(fitPars, first, last, 100, xValues[1][0]);
                plotTool.getChart().addLines(xy[0], xy[1], false, color);
            } else if (peakPaths.getPathMode() == PATHMODE.PRESSURE) {
                double[][] xy = fitPath.getPressureSimValues(fitPars, first, last, 100);
                for (int i = 1; i < xy.length; i++) {
                    plotTool.getChart().addLines(xy[0], xy[i], false, color);
                }
            }
        }

    }

    void refreshPaths() {
        peakPaths.refreshPaths();
        chart.refresh();
    }

    @Override
    public void refreshPeakView(Peak peak) {

        controller.refreshPeakView(peak);
        if ((peak != null) && (peakPaths != null)) {
            PeakPath path = peakPaths.getPath(peak);
            updatePathInfo(peak);
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

    public void removeActivePaths(List<PeakPath> selPaths) {
        for (PeakPath path : peakPaths.getPaths()) {
            path.setActive(false);

        }
        activePaths.removeAll(selPaths);
        plotTool.updateTable(activePaths);
    }
}
