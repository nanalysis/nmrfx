
package org.nmrfx.processor.gui.tools;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.chart.XYCanvasChart;
import org.nmrfx.processor.datasets.peaks.PathFitter;
import org.nmrfx.processor.datasets.peaks.FreezeListener;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.PeakPath;
import org.nmrfx.processor.datasets.peaks.PeakPath.PATHMODE;
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
import org.nmrfx.processor.optimization.Fitter;

/**
 *
 * @author Bruce Johnson
 */
public class PathTool implements PeakNavigable {

    VBox vBox;
    FXMLController controller;
    PolyChart chart;
    ToolBar toolBar;
    ToolBar fitBar;
    Consumer closeAction;
    MenuButton actionMenu;
    Button drawButton;
    Button findButton;
    Button tweakFreezeButton;
    List<Peak> selPeaks;
    List<FreezeListener> listeners = new ArrayList<>();
    PeakNavigator peakNavigator;
    TextField radiusField;
    TextField tolField;
    Label[] fitFields;
    Button fitButton;
    Button addButton;
    PeakPath peakPath;
    ObservableList<PeakPath.Path> activePaths = FXCollections.observableArrayList();

    PathPlotTool plotTool;

    public PathTool(FXMLController controller, Consumer closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
        this.chart = controller.getActiveChart();
    }

    public VBox getToolBar() {
        return vBox;
    }

    public void close(Object o) {
        closeAction.accept(this);
    }

    public void initPathTool(VBox vBox) {
        toolBar = new ToolBar();
        fitBar = new ToolBar();
        this.vBox = vBox;
        vBox.getChildren().addAll(toolBar, fitBar);
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

        actionMenu = new MenuButton("Actions");
        MenuItem loadTitrationItem = new MenuItem("Load Titration Data...");
        loadTitrationItem.setOnAction(e -> loadPathData(PATHMODE.TITRATION));
        actionMenu.getItems().add(loadTitrationItem);

        MenuItem loadPressureItem = new MenuItem("Load Pressure Data...");
        loadPressureItem.setOnAction(e -> loadPathData(PATHMODE.PRESSURE));
        actionMenu.getItems().add(loadPressureItem);

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
        fillerf1.setMinWidth(20);

        fitButton = new Button("Fit");
        fitButton.setOnAction(e -> fitPath());
        fitButton.setDisable(true);

        addButton = new Button("Add");
        addButton.setOnAction(e -> addPathToTable());
        addButton.setDisable(true);

        fitBar.getItems().addAll(fitButton, addButton, fillerf1);
        setActionMenuDisabled(true);

//        controller.selPeaks.addListener(e -> setActivePeaks(controller.selPeaks.get()));
    }

    void setActionMenuDisabled(boolean state) {
        int i = 0;
        for (MenuItem item : actionMenu.getItems()) {
            if (i > 1) {
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
                fitFields[i].setPrefWidth(140);
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
    }

    void loadPathData(PATHMODE pathMode) {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                peakPath = PeakPath.loadPathData(pathMode, file);
                if (peakPath == null) {
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
            setupChart(peakPath.getDatasetNames());
            setupPlotTable();
            setActionMenuDisabled(false);
        }
    }

    void setupPlotTable() {
        if (peakPath != null) {
            List<String> colNames = new ArrayList<>();
            colNames.add("Peak");
            String[] parNames = peakPath.getParNames();
            if (peakPath.getPathMode() == PATHMODE.PRESSURE) {
                for (String col : parNames) {
                    colNames.add(col);
                    colNames.add(col + "Dev");
                }

            }
            plotTool = new PathPlotTool(this, colNames);
        }

    }

    boolean pathUsable(Path path) {
        return path.getNValid() > 3;
    }

    void fitPath() {
        if (peakPath != null) {

            Peak peak = peakNavigator.getPeak();
            PathFitter fitPath = new PathFitter();
            try {
                Path path = peakPath.getPath(peak);
                if (pathUsable(path)) {
                    fitPath.setup(peakPath, path);
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
        if (peakPath != null) {
            PathFitter fitPath = new PathFitter();
            List<PeakPath.Path> fitPaths = plotTool.getSelected();
            if (fitPaths.isEmpty()) {
                fitPaths.addAll(activePaths);

            }
            if (!fitPaths.isEmpty()) {
                try {
                    fitPath.setup(peakPath, fitPaths);
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
        List<PeakPath.Path> fitPaths = plotTool.getSelected();
        if (fitPaths.isEmpty()) {
            fitPaths.addAll(activePaths);
        }
        if (!fitPaths.isEmpty()) {
            try {
                for (Path path : fitPaths) {
                    fitPath.setup(peakPath, path);
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
        if (peakPath != null) {
            Peak peak = peakNavigator.getPeak();
            Path path = peakPath.getPath(peak);
            if (path != null) {
                if (activePaths.contains(path)) {
                    activePaths.remove(path);

                }
                activePaths.add(path);
                plotTool.updateTable(activePaths);

            }
        }

    }

    void clearPath() {
        if (peakPath != null) {

            Peak startPeak = peakNavigator.getPeak();
            if (startPeak != null) {
                peakPath.initPath(startPeak);
            }
            drawPath();
        }
    }

    void addPeakToPath() {
        if (peakPath != null) {

            Peak startPeak = peakNavigator.getPeak();
            List<Peak> selPeaks = chart.getSelectedPeaks();
            System.out.println("selp " + selPeaks.size() + " " + startPeak);
            if (selPeaks.size() == 1) {
                if (startPeak != null) {
                    peakPath.addPeak(startPeak, selPeaks.get(0));
                    Path path = peakPath.getPath(startPeak);
                    if (path.getNValid() > 3) {
                        path.confirm();
                    }
                }
            }
            drawPath();
        }
    }

    void removePeakFromPath() {
        if (peakPath != null) {

            Peak startPeak = peakNavigator.getPeak();
            List<Peak> selPeaks = chart.getSelectedPeaks();
            if (selPeaks.size() == 1) {
                if (startPeak != null) {
                    peakPath.removePeak(startPeak, selPeaks.get(0));
                }
            }
            drawPath();
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
        peakPath.checkListsForUnambigous(radius);
    }

    void setStatus() {
        double radius = Double.parseDouble(radiusField.getText());
        peakPath.setStatus(radius, 1.0);

    }

    void clearPaths() {
        if (peakPath != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Clear all paths", ButtonType.CANCEL, ButtonType.YES);
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    peakPath.clearPaths();
                }
            });
        }
    }

    void findPaths() {
        if (peakPath != null) {
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
            chart.refresh();
            drawPath();
        }
    }

    void extendPath() {
        if (peakPath != null) {

            double radius = Double.parseDouble(radiusField.getText());
            double lim = Double.parseDouble(tolField.getText());
            Peak startPeak = peakNavigator.getPeak();
            peakPath.extendPath(startPeak, radius, lim);
            peakPath.setStatus(radius, 1.0);
            drawPath();
        }
    }

    void extendPaths() {
        if (peakPath != null) {

            double radius = Double.parseDouble(radiusField.getText());
            double lim = 1.0;
            peakPath.extendPaths(radius, lim);
            peakPath.setStatus(radius, 1.0);
            drawPath();
        }
    }

    void addPath() {
        if (peakPath != null) {

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
                updatePathInfo(startPeak);
            }
        }
    }

    void drawPath() {
        if (peakPath != null) {

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
        }
        chart.refresh();
        chart.refresh();
    }

    void updatePathInfo(Peak peak) {
        updatePathInfo(peak, null);
    }

    void updatePathInfo(Peak peak, PathFitter fitPath) {
        if (peakPath == null) {
            return;
        }

        Path path = peakPath.getPath(peak);
        if (path != null) {
            System.out.println(path.toString());
            if (pathUsable(path)) {
                fitButton.setDisable(false);
                addButton.setDisable(false);

                if (fitPath == null) {
                    fitPath = new PathFitter();
                    fitPath.setup(peakPath, path);
                }

                double[][] xValues = fitPath.getX();
                double[][] yValues = fitPath.getY();
                if (peakPath.getPathMode() == PATHMODE.PRESSURE) {
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
                String[] parNames = peakPath.getParNames();
                if (fitPars != null) {
                    initFitFields(fitPars.length);
                    for (int i = 0; i < fitPars.length; i++) {
                        System.out.printf("%s= %.3f +/- %.3f\n", parNames[i], fitPars[i], fitErrs[i]);
                    }
                    for (int i = 0; i < fitPars.length; i++) {
                        fitFields[i].setText(String.format("%s= %.3f +/- %.3f", parNames[i], fitPars[i], fitErrs[i]));
                    }
                    double first = 0.0;
                    double last = Fitter.getMaxValue(peakPath.getXValues()[0]);
                    if (peakPath.getPathMode() == PATHMODE.TITRATION) {
                        double[][] xy = fitPath.getSimValues(fitPars, first, last, 100, xValues[1][0]);
                        plotTool.getChart().addLines(xy[0], xy[1], false, color);
                    } else if (peakPath.getPathMode() == PATHMODE.PRESSURE) {
                        double[][] xy = fitPath.getPressureSimValues(fitPars, first, last, 100);
                        for (int i = 1; i < xy.length; i++) {
                            plotTool.getChart().addLines(xy[0], xy[i], false, color);
                        }
                    }
                }

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

    public void showXYPaths(List<Path> paths) {
        if (peakPath != null) {
            if (peakPath.getPathMode() == PATHMODE.PRESSURE) {
                plotTool.show("Pressure", "Shift Delta");
            } else {
                plotTool.show("Concentration", "Shift Delta");
            }
            plotTool.clear();
            int iSeries = 0;
            for (Path path : paths) {
                Color color = XYCanvasChart.colors[iSeries % XYCanvasChart.colors.length];
                showXYPath(path, color);
                iSeries++;
            }
        }
    }

    public void showXYPath(Path path, Color color) {
        PathFitter fitPath = new PathFitter();
        fitPath.setup(peakPath, path);

        double[][] xValues = fitPath.getX();
        double[][] yValues = fitPath.getY();
        for (int iY = 0; iY < yValues.length; iY++) {
            plotTool.getChart().addLines(xValues[0], yValues[iY], true, color);
        }

        double[] fitPars = path.getFitPars();
        double[] fitErrs = path.getFitErrs();
        if (fitPars != null) {
            initFitFields(fitPars.length);

            for (int i = 0; i < fitPars.length; i++) {
                fitFields[i].setText(String.format("%.3f +/- %.3f", fitPars[i], fitErrs[i]));
            }
            double first = 0.0;
            double last = Fitter.getMaxValue(peakPath.getXValues()[0]);
            if (peakPath.getPathMode() == PATHMODE.TITRATION) {
                double[][] xy = fitPath.getSimValues(fitPars, first, last, 100, xValues[1][0]);
                plotTool.getChart().addLines(xy[0], xy[1], false, color);
            } else if (peakPath.getPathMode() == PATHMODE.PRESSURE) {
                double[][] xy = fitPath.getPressureSimValues(fitPars, first, last, 100);
                for (int i = 1; i < xy.length; i++) {
                    plotTool.getChart().addLines(xy[0], xy[i], false, color);
                }
            }
        }

    }

    void refreshPaths() {
        peakPath.refreshPaths();
        chart.refresh();
    }

    @Override
    public void refreshPeakView(Peak peak) {

        controller.refreshPeakView(peak);
        if ((peak != null) && (peakPath != null)) {
            Path path = peakPath.getPath(peak);
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

    public void removeActivePaths(List<PeakPath.Path> selPaths) {
        activePaths.removeAll(selPaths);
        plotTool.updateTable(activePaths);
    }
}
