/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.processor.gui;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.*;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.datasets.RegionData;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.graphicsio.GraphicsContextProxy;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.graphicsio.SVGGraphicsContext;
import org.nmrfx.math.VecBase;
import org.nmrfx.peaks.events.PeakEvent;
import org.nmrfx.peaks.events.PeakListener;
import org.nmrfx.peaks.*;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.PeakFitException;
import org.nmrfx.processor.datasets.peaks.PeakListTools;
import org.nmrfx.processor.datasets.peaks.PeakListTools.ARRAYED_FIT_MODE;
import org.nmrfx.processor.datasets.peaks.PeakNeighbors;
import org.nmrfx.processor.gui.annotations.AnnoText;
import org.nmrfx.processor.gui.controls.ConsoleUtil;
import org.nmrfx.processor.gui.spectra.*;
import org.nmrfx.processor.gui.spectra.DatasetAttributes.AXMODE;
import org.nmrfx.processor.gui.spectra.mousehandlers.MouseBindings;
import org.nmrfx.processor.gui.spectra.mousehandlers.MouseBindings.MOUSE_ACTION;
import org.nmrfx.processor.gui.undo.ChartUndoLimits;
import org.nmrfx.processor.gui.undo.ChartUndoScale;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.project.ProjectText;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.stream.Collectors;

import static org.nmrfx.processor.gui.PolyChart.DISDIM.TwoD;

public class PolyChart extends Region implements PeakListener {
    private static final Logger log = LoggerFactory.getLogger(PolyChart.class);

    /**
     * @return the hasMiddleMouseButton
     */
    public boolean getHasMiddleMouseButton() {
        return hasMiddleMouseButton;
    }

    /**
     * @param hasMiddleMouseButton the hasMiddleMouseButton to set
     */
    public void setHasMiddleMouseButton(boolean hasMiddleMouseButton) {
        this.hasMiddleMouseButton = hasMiddleMouseButton;
    }

    /**
     * @return the mouseX
     */
    public double getMouseX() {
        return mouseBindings.getMouseX();
    }

    /**
     * @return the mouseY
     */
    public double getMouseY() {
        return mouseBindings.getMouseY();
    }

    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;
    public static final int CROSSHAIR_TOL = 25;
    double minMove = 20;

    public static final ObservableList<PolyChart> CHARTS = FXCollections.observableArrayList();
    static final SimpleObjectProperty<PolyChart> activeChart = new SimpleObjectProperty<>(null);
    static final SimpleBooleanProperty multipleCharts = new SimpleBooleanProperty(false);
    static Consumer<PeakDeleteEvent> manualPeakDeleteAction = null;

    static {
        CHARTS.addListener((ListChangeListener) (e -> multipleCharts.set(CHARTS.size() > 1)));
    }

    ArrayList<Double> dList = new ArrayList<>();
    ArrayList<Double> nList = new ArrayList<>();
    ArrayList<Double> bcList = new ArrayList<>();
    Canvas canvas;
    Canvas peakCanvas;
    Canvas annoCanvas = null;
    Path bcPath = new Path();
    Line[][] crossHairLines = new Line[2][2];
    Rectangle highlightRect = new Rectangle();
    List<Rectangle> canvasHandles = List.of(new Rectangle(), new Rectangle(), new Rectangle(), new Rectangle());
    double[][] crossHairPositions = new double[2][2];
    boolean[][] crossHairStates = new boolean[2][2];
    int crossHairNumH = 0;
    int crossHairNumV = 0;
    private boolean hasMiddleMouseButton = false;
    final NMRAxis xAxis;
    final NMRAxis yAxis;
    NMRAxis[] axes = new NMRAxis[2];
    final Group plotBackground;
    final Pane plotContent;
    final DrawSpectrum drawSpectrum;
    final DrawPeaks drawPeaks;
    SliceAttributes sliceAttributes = new SliceAttributes();
    DatasetAttributes lastDatasetAttr = null;
    List<CanvasAnnotation> canvasAnnotations = new ArrayList<>();
    AnnoText parameterText = null;
    private static int lastId = 0;
    private final int id;
    double leftBorder = 0.0;
    double rightBorder = 0.0;
    double topBorder = 0.0;
    double bottomBorder = 0.0;
    double minLeftBorder = 0.0;
    double minBottomBorder = 0.0;
    String fontFamily = "Liberation Sans";
    Font peakFont = new Font(fontFamily, 12);
    boolean disabled = false;
    public ChartProperties chartProps = new ChartProperties(this);
    FXMLController sliceController = null;
    SimpleObjectProperty<DatasetRegion> activeRegion = new SimpleObjectProperty<>(null);
    SimpleBooleanProperty chartSelected = new SimpleBooleanProperty(false);
    Map<String, Object> popoverMap = new HashMap<>();

    int iVec = 0;
//    Vec vec;
    FileProperty datasetFileProp = new FileProperty();
//    DatasetAttributes datasetAttributes = null;
    ObservableList<DatasetAttributes> datasetAttributesList = FXCollections.observableArrayList();
    ObservableList<PeakListAttributes> peakListAttributesList = FXCollections.observableArrayList();
    ObservableSet<MultipletSelection> selectedMultiplets = FXCollections.observableSet();

    FXMLController controller;
    ProcessorController processorController = null;
    BooleanProperty sliceStatus = new SimpleBooleanProperty(true);
    BooleanProperty peakStatus = new SimpleBooleanProperty(true);
    double level = 1.0;
// fixme 15 should be set automatically and correctly
    double[][] chartPhases = new double[2][15];
    double[] chartPivots = new double[15];
    int datasetPhaseDim = 0;
    int phaseAxis = 0;
    double phaseFraction = 0.0;
    Double[] pivotPosition = new Double[15];
    boolean useImmediateMode = true;
    private final List<ConnectPeakAttributes> peakPaths = new ArrayList<>();
    Consumer<DatasetRegion> newRegionConsumer = null;
    static boolean listenToPeaks = true;


    @Override
    public void peakListChanged(final PeakEvent peakEvent) {
        if (listenToPeaks) {
            if (Platform.isFxApplicationThread()) {
                respondToPeakListChange(peakEvent);
            } else {
                Platform.runLater(() -> {
                    respondToPeakListChange(peakEvent);
                }
                );
            }
        }
    }

    public static void setPeakListenerState(boolean state) {
        listenToPeaks = state;
    }

    public void updateSelectedMultiplets() {
        selectedMultiplets.clear();
        for (PeakListAttributes peakAttr : peakListAttributesList) {
            selectedMultiplets.addAll(peakAttr.getSelectedMultiplets());
        }
    }

    public void addMultipletListener(SetChangeListener listener) {
        selectedMultiplets.addListener(listener);
    }

    public void setPeakStatus(boolean state) {
        peakStatus.set(state);
    }

    private void respondToPeakListChange(PeakEvent peakEvent) {
        // if mouse down we could be dragging peak which will itself cause redraw
        //   no need to call this
        if (mouseBindings.isMouseDown()) {
            return;
        }
        Object source = peakEvent.getSource();
        boolean draw = false;
        PeakListAttributes activeAttr = null;
        if (peakStatus.get()) {
            if (source instanceof PeakList) {
                PeakList peakList = (PeakList) source;
                for (PeakListAttributes peakListAttr : peakListAttributesList) {
                    if (peakListAttr.getPeakList() == peakList) {
                        activeAttr = peakListAttr;
                        draw = true;
                    }
                }
            }
        }
        if (activeAttr != null) {
            drawPeakLists(false);
            drawSelectedPeaks(activeAttr);
        }
    }

    public enum DISDIM {
        OneDX, OneDY, TwoD;
    };
    ObjectProperty<DISDIM> disDimProp = new SimpleObjectProperty(TwoD);
    ChartMenu specMenu;
    ChartMenu peakMenu;
    ChartMenu integralMenu;
    ChartMenu regionMenu;
    KeyBindings keyBindings;
    MouseBindings mouseBindings;
    GestureBindings gestureBindings;
    DragBindings dragBindings;
    CrossHairs crossHairs;

    AXMODE axModes[] = {AXMODE.PPM, AXMODE.PPM};
    Map<String, Integer> syncGroups = new HashMap<>();
    static int nSyncGroups = 0;

    public static double overlapScale = 3.0;

    public PolyChart(FXMLController controller, Pane plotContent, Canvas canvas, Canvas peakCanvas, Canvas annoCanvas) {
        this(controller, plotContent, canvas, peakCanvas, annoCanvas,
                new NMRAxis(Orientation.HORIZONTAL, 0, 100, 200, 50),
                new NMRAxis(Orientation.VERTICAL, 0, 100, 50, 200)
        );

    }

    public PolyChart(FXMLController controller, Pane plotContent, Canvas canvas, Canvas peakCanvas, Canvas annoCanvas, final NMRAxis... AXIS) {
        this.canvas = canvas;
        this.peakCanvas = peakCanvas;
        this.annoCanvas = annoCanvas;
        this.controller = controller;
        xAxis = AXIS[0];
        yAxis = AXIS[1];
        plotBackground = new Group();
        this.plotContent = plotContent;
        drawSpectrum = new DrawSpectrum(axes, canvas);
        id = getNextId();

        initChart();
        drawPeaks = new DrawPeaks(this, peakCanvas);
        setVisible(false);

    }

    public boolean isSelectable() {
        return false;
    }

    public void selectChart(boolean value) {
        chartSelected.set(value);
    }

    public boolean isSelected() {
        return chartSelected.get();
    }

    public double[][] getCorners() {
        double[][] corners = new double[4][2];
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        double width = getWidth();
        double height = getHeight();
        corners[0] = new double[]{xPos, yPos};
        corners[1] = new double[]{xPos, yPos + height};
        corners[2] = new double[]{xPos + width, yPos};
        corners[3] = new double[]{xPos + width, yPos + height};
        return corners;
    }

    private void initChart() {
        axes[0] = xAxis;
        axes[1] = yAxis;
        crossHairLines[0][0] = new Line(0, 50, 400, 50);
        crossHairLines[0][1] = new Line(100, 0, 100, 400);
        crossHairLines[1][0] = new Line(0, 50, 400, 50);
        crossHairLines[1][1] = new Line(100, 0, 100, 400);
        crossHairStates[0][0] = false;
        crossHairStates[0][1] = true;
        crossHairStates[1][0] = false;
        crossHairStates[1][1] = true;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                plotContent.getChildren().add(crossHairLines[i][j]);
                crossHairLines[i][j].setVisible(false);
                crossHairLines[i][j].setStrokeWidth(0.5);
                crossHairLines[i][j].setMouseTransparent(true);
                if (i == 0) {
                    crossHairLines[i][j].setStroke(Color.BLACK);
                } else {
                    crossHairLines[i][j].setStroke(Color.RED);

                }
            }
        }
        highlightRect.setVisible(false);
        highlightRect.setStroke(Color.BLUE);
        highlightRect.setStrokeWidth(1.0);
        highlightRect.setFill(null);
        highlightRect.visibleProperty().bind(activeChart.isEqualTo(this).and(multipleCharts).or(chartSelected));
        plotContent.getChildren().add(highlightRect);
        for (var canvasHanndle:canvasHandles) {
            canvasHanndle.visibleProperty().bind(chartSelected);
        }
        plotContent.getChildren().addAll(canvasHandles);
        loadData();
        xAxis.lowerBoundProperty().addListener(new AxisChangeListener(this, 0, 0));
        xAxis.upperBoundProperty().addListener(new AxisChangeListener(this, 0, 1));
        yAxis.lowerBoundProperty().addListener(new AxisChangeListener(this, 1, 0));
        yAxis.upperBoundProperty().addListener(new AxisChangeListener(this, 1, 1));
        CHARTS.add(this);
        activeChart.set(this);
        canvas.setCursor(Cursor.CROSSHAIR);
        MapChangeListener<String, PeakList> mapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> {
            purgeInvalidPeakListAttributes();
        };
        ProjectBase.getActive().addPeakListListener(mapChangeListener);
        keyBindings = new KeyBindings(this);
        mouseBindings = new MouseBindings(this);
        gestureBindings = new GestureBindings(this);
        dragBindings = new DragBindings(controller, canvas);
        specMenu = new SpectrumMenu(this);
        peakMenu = new PeakMenu(this);
        regionMenu = new RegionMenu(this);
        integralMenu = new IntegralMenu(this);
        crossHairs = new CrossHairs(activeChart.get(), crossHairPositions, crossHairStates, crossHairLines);
        setDragHandlers(canvas);
        canvas.requestFocus();

    }

    private synchronized int getNextId() {
        lastId++;
        return (lastId);
    }

    public String getName() {
        return String.valueOf(id);
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public static Optional<PolyChart> getChart(String name) {
        Optional<PolyChart> result = Optional.empty();
        for (PolyChart chart : CHARTS) {
            if (chart.getName().equals(name)) {
                result = Optional.of(chart);
            }
        }
        return result;
    }

    public FXMLController getFXMLController() {
        return controller;
    }

    public NMRAxis getXAxis() {
        return axes[0];
    }

    public NMRAxis getYAxis() {
        return axes[1];
    }

    public void removeSelected() {
        if (controller.charts.size() > 1) {
            close();
        }
    }

    public void close() {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                plotContent.getChildren().remove(crossHairLines[i][j]);
            }
        }
        highlightRect.visibleProperty().unbind();
        plotContent.getChildren().remove(highlightRect);
        for (var canvasHandle:canvasHandles) {
            plotContent.getChildren().remove(canvasHandle);
            plotContent.visibleProperty().unbind();
        }

        CHARTS.remove(this);
        controller.removeChart(this);
        if (this == activeChart.get()) {
            if (CHARTS.isEmpty()) {
                activeChart.set(null);
            } else {
                activeChart.set(CHARTS.get(0));
            }
        }
        drawSpectrum.clearThreads();
    }

    public void focus() {
        getController().stage.requestFocus();
        //  canvas.setFocused(true);
    }

    public Cursor getCanvasCursor() {
        return canvas.getCursor();
    }

    public boolean contains(double x, double y) {
        return (x > getLayoutX()) && (x < (getLayoutX() + getWidth())) && (y > getLayoutY()) && (y < (getLayoutY() + getHeight()));
    }

    public MouseBindings getMouseBindings() {
        return mouseBindings;
    }

    public GestureBindings getGestureBindings() {
        return gestureBindings;
    }

    public KeyBindings getKeyBindings() {
        return keyBindings;
    }

    public ChartMenu getSpectrumMenu() {
        return specMenu;
    }

    public ChartMenu getPeakMenu() {
        return peakMenu;
    }

    public ChartMenu getIntegralMenu() {
        return integralMenu;
    }

    public ChartMenu getRegionMenu() {
        return regionMenu;
    }

    final protected void setDragHandlers(Node mouseNode) {
        mouseNode.setOnDragOver((DragEvent event) -> {
            dragBindings.mouseDragOver(event);
        });
        mouseNode.setOnDragDropped((DragEvent event) -> {
            dragBindings.mouseDragDropped(event);
        });
        mouseNode.setOnDragExited((DragEvent event) -> {
            mouseNode.setStyle("-fx-border-color: #C6C6C6;");
        });
    }

    public void setActiveChart() {
        activeChart.set(this);
        controller.setActiveChart(this);
    }

    public static PolyChart getActiveChart() {
        return activeChart.get();
    }

    public FXMLController getController() {
        return controller;
    }

    public void handleCrossHair(MouseEvent mEvent, boolean selectCrossNum) {
        if (selectCrossNum) {
            if (mEvent.isMiddleButtonDown()) {
                hasMiddleMouseButton = true;
            }

            int[] crossNums = crossHairs.getCrossHairNum(mEvent.getX(),
                    mEvent.getY(), hasMiddleMouseButton, mEvent.isMiddleButtonDown());
            crossHairNumH = crossNums[0];
            crossHairNumV = crossNums[1];
        }
        if (crossHairNumH >= 0) {
            crossHairs.moveCrosshair(crossHairNumH, HORIZONTAL, mEvent.getY());
        }
        if (crossHairNumV >= 0) {
            crossHairs.moveCrosshair(crossHairNumV, VERTICAL, mEvent.getX());
        }
    }

    public void dragBox(MOUSE_ACTION mouseAction, double[] dragStart, double x, double y) {
        int dragTol = 4;
        if ((Math.abs(x - dragStart[0]) > dragTol) || (Math.abs(y - dragStart[1]) > dragTol)) {
            GraphicsContext annoGC = annoCanvas.getGraphicsContext2D();
            double annoWidth = annoCanvas.getWidth();
            double annoHeight = annoCanvas.getHeight();
            annoGC.clearRect(0, 0, annoWidth, annoHeight);
            double dX = Math.abs(x - dragStart[0]);
            double dY = Math.abs(y - dragStart[1]);
            double startX = x > dragStart[0] ? dragStart[0] : x;
            double startY = y > dragStart[1] ? dragStart[1] : y;
            double yPos = getLayoutY();
            annoGC.setLineDashes(null);
            if (mouseAction == MOUSE_ACTION.DRAG_EXPAND || mouseAction == MOUSE_ACTION.DRAG_ADDREGION) {
                if ((dX < minMove) || (!is1D() && (dY < minMove))) {
                    annoGC.setLineDashes(5);
                }
            }
            Color color;
            if (null == mouseAction) {
                color = Color.DARKORANGE;
            } else {
                switch (mouseAction) {
                    case DRAG_EXPAND:
                        color = Color.DARKBLUE;
                        break;
                    case DRAG_ADDREGION:
                        color = Color.GREEN;
                        break;
                    default:
                        color = Color.DARKORANGE;
                        break;
                }
            }
            annoGC.setStroke(color);
            if (is1D()) {
                annoGC.strokeLine(x, yPos + topBorder, x, yPos + getHeight()-bottomBorder);
                annoGC.strokeLine(dragStart[0], yPos + topBorder, dragStart[0], yPos + getHeight()-bottomBorder);
            } else {
                annoGC.strokeRect(startX, startY, dX, dY);
            }
            annoGC.setLineDashes(null);
        }
    }

    private void swapDouble(double[] values) {
        if (values[0] > values[1]) {
            double hold = values[0];
            values[0] = values[1];
            values[1] = hold;
        }
    }

    public void setRegionConsumer(Consumer<DatasetRegion> consumer) {
        newRegionConsumer = consumer;
    }

    public void clearRegionConsumer() {
        newRegionConsumer = null;
    }

    public void addRegion(double min, double max) {
        DatasetBase dataset = getDataset();
        if (dataset != null) {
            DatasetRegion newRegion = dataset.addRegion(min, max);
            try {
                newRegion.measure(dataset);
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
            chartProps.setRegions(false);
            chartProps.setIntegrals(true);
            if (newRegionConsumer != null) {
                newRegionConsumer.accept(newRegion);
            }
        }
    }

    public void finishBox(MOUSE_ACTION mouseAction, double[] dragStart, double x, double y) {
        GraphicsContext annoGC = annoCanvas.getGraphicsContext2D();
        double annoWidth = annoCanvas.getWidth();
        double annoHeight = annoCanvas.getHeight();
        annoGC.clearRect(0, 0, annoWidth, annoHeight);
        double[][] limits;
        if (is1D()) {
            limits = new double[1][2];
        } else {
            limits = new double[2][2];
        }
        double dX = Math.abs(x - dragStart[0]);
        double dY = Math.abs(y - dragStart[1]);
        limits[0][0] = xAxis.getValueForDisplay(dragStart[0]).doubleValue();
        limits[0][1] = xAxis.getValueForDisplay(x).doubleValue();
        swapDouble(limits[0]);
        if (!is1D()) {
            limits[1][0] = yAxis.getValueForDisplay(y).doubleValue();
            limits[1][1] = yAxis.getValueForDisplay(dragStart[1]).doubleValue();
            swapDouble(limits[1]);
        }

        if (mouseAction == MOUSE_ACTION.DRAG_EXPAND) {
            if (dX > minMove) {
                if (is1D() || (dY > minMove)) {

                    ChartUndoLimits undo = new ChartUndoLimits(this);
                    setAxis(0, limits[0][0], limits[0][1]);
                    if (!is1D()) {
                        setAxis(1, limits[1][0], limits[1][1]);
                    }
                    ChartUndoLimits redo = new ChartUndoLimits(this);
                    controller.undoManager.add("expand", undo, redo);
                    refresh();
                }
            }
        } else if (mouseAction == MOUSE_ACTION.DRAG_ADDREGION) {
            if (dX > minMove) {
                if (is1D()) {
                    addRegion(limits[0][0], limits[0][1]);
                    refresh();
                }
            }
        } else {
            drawPeakLists(false);
            for (PeakListAttributes peakAttr : peakListAttributesList) {
                List<Peak> peaks = peakAttr.selectPeaksInRegion(limits);
                drawSelectedPeaks(peakAttr);
            }
            if (controller == FXMLController.activeController.get()) {
                List<Peak> allSelPeaks = new ArrayList<>();
                for (PolyChart chart : controller.charts) {
                    allSelPeaks.addAll(chart.getSelectedPeaks());
                }
                controller.selPeaks.set(allSelPeaks);
            }

        }
    }

    public NMRAxis getAxis(int iDim) {
        return iDim < axes.length ? axes[iDim] : null;
    }

    public AXMODE getAxMode(int iDim) {
        return iDim < axModes.length ? axModes[iDim] : null;
    }

    public void setController(FXMLController controller) {
        this.controller = controller;
        drawSpectrum.setController(controller);
    }

    public void setProcessorController(ProcessorController controller) {
        this.processorController = controller;
    }

    public void setCrossHairState(boolean h1, boolean v1, boolean h2, boolean v2) {
        crossHairStates[0][0] = h1;
        crossHairStates[0][1] = v1;
        crossHairStates[1][0] = h2;
        crossHairStates[1][1] = v2;
    }

    public Optional<DatasetAttributes> getFirstDatasetAttributes() {
        Optional<DatasetAttributes> firstDatasetAttr = Optional.empty();
        if (!datasetAttributesList.isEmpty()) {
            firstDatasetAttr = Optional.of(datasetAttributesList.get(0));
        }
        return firstDatasetAttr;
    }

    public DatasetBase getDataset() {
        DatasetBase dataset = null;
        if (!datasetAttributesList.isEmpty()) {
            dataset = datasetAttributesList.get(0).getDataset();
        }
        return dataset;
    }

    void removeAllDatasets() {
        if (!datasetAttributesList.isEmpty()) {
            lastDatasetAttr = (DatasetAttributes) ((DatasetAttributes) datasetAttributesList.get(0)).clone();
        }
        datasetAttributesList.clear();
    }

    void remove(DatasetBase dataset) {
        for (Iterator<DatasetAttributes> iterator = datasetAttributesList.iterator(); iterator.hasNext();) {
            DatasetAttributes dataAttr = iterator.next();
            if (dataset == dataAttr.getDataset()) {
                lastDatasetAttr = (DatasetAttributes) dataAttr.clone();
                iterator.remove();
            }
        }
    }

    File getDatasetFile() {
        File file = null;
        DatasetBase dataset = getDataset();
        if (dataset != null) {
            file = dataset.getFile();
        }
        return file;
    }

    protected int getDataSize(int dimNum) {
        int dataSize = 0;
        DatasetBase dataset = getDataset();
        if (dataset != null) {
            dataSize = dataset.getSizeReal(dimNum);
        }
        return dataSize;
    }

    public void zoom(double factor) {
        ConsoleUtil.runOnFxThread(() -> {
            DatasetBase dataset = getDataset();
            if (dataset != null) {
                ChartUndoLimits undo = new ChartUndoLimits(this);
                xZoom(factor);
                if (!is1D()) {
                    yZoom(factor);
                }
                layoutPlotChildren();
                ChartUndoLimits redo = new ChartUndoLimits(this);
                String undoName = factor > 1.0 ? "zoomout" : "zoomin";
                controller.undoManager.add(undoName, undo, redo);

            }
        }
        );
    }

    protected void xZoom(double factor) {
        double min = xAxis.getLowerBound();
        double max = xAxis.getUpperBound();
        double range = max - min;
        double center = (max + min) / 2.0;
        if (factor > 2.0) {
            factor = 2.0;
        } else if (factor < 0.5) {
            factor = 0.5;
        }
        range = range / factor;
        // fixme add check for too small range
        min = center - range / 2.0;
        max = center + range / 2.0;
        double[] limits = getRange(0, min, max);
        setXAxis(limits[0], limits[1]);

//        double[] limits = datasetAttributes.checkLimits(axModes[0], 0, min, max);
//        setXAxis(limits[0], limits[1]);
    }

    public void swapView() {
        if (!is1D()) {
            double minX = xAxis.getLowerBound();
            double maxX = xAxis.getUpperBound();
            double minY = yAxis.getLowerBound();
            double maxY = yAxis.getUpperBound();
            setXAxis(minY, maxY);
            setYAxis(minX, maxX);
            refresh();
        }
    }

    public void popView() {
        FXMLController newController = FXMLController.create();
        PolyChart newChart = newController.getActiveChart();
        copyTo(newChart);
        newController.getStatusBar().setMode(controller.getStatusBar().getMode());
        newChart.refresh();
    }

    public void copyTo(PolyChart newChart) {
        for (DatasetAttributes dataAttr : datasetAttributesList) {
            DatasetAttributes newDataAttr = newChart.setDataset(dataAttr.getDataset(), true, false);
            dataAttr.copyTo(newDataAttr);
        }
        for (PeakListAttributes peakAttr : peakListAttributesList) {
            PeakListAttributes newPeakAttr = newChart.setupPeakListAttributes(peakAttr.getPeakList());
            peakAttr.copyTo(newPeakAttr);
        }
        for (int iAxis = 0; iAxis < axes.length; iAxis++) {
            newChart.axModes[iAxis] = axModes[iAxis];
            newChart.setAxis(iAxis, axes[iAxis].getLowerBound(), axes[iAxis].getUpperBound());
            newChart.axes[iAxis].setLabel(axes[iAxis].getLabel());
            axes[iAxis].copyTo(newChart.axes[iAxis]);
        }
        newChart.refresh();
    }

    protected double[] getRange(int axis, double min, double max) {
        double[] limits = getRange(axis);
        if (min > limits[0]) {
            limits[0] = min;
        }
        if (max < limits[1]) {
            limits[1] = max;
        }
        return limits;
    }

    protected double[] getRange(int axis) {
        double[] limits = {Double.MAX_VALUE, Double.NEGATIVE_INFINITY};
        for (DatasetAttributes dataAttr : datasetAttributesList) {
            if (dataAttr.projection() == -1) {
                dataAttr.checkRange(axModes[axis], axis, limits);
            } else {
                if (dataAttr.projection() == axis) {
                    dataAttr.checkRange(axModes[axis], 0, limits);
                }
            }
        }
        return limits;
    }

    public void scroll(double x, double y) {

        scrollXAxis(x);
        scrollYAxis(y);

        layoutPlotChildren();
    }

    protected void scrollXAxis(double x) {
        double scale = xAxis.getScale();
        if (axModes[0] == AXMODE.PPM) {
            scale *= -1.0;
        }
        double min = xAxis.getLowerBound();
        double max = xAxis.getUpperBound();
        double range = max - min;
        double center = (max + min) / 2.0;
        center -= x / scale;
        // fixme add check for too small range
        min = center - range / 2.0;
        max = center + range / 2.0;
        double[] limits = getRange(0, min, max);

        setXAxis(limits[0], limits[1]);
    }

    protected void scrollYAxis(double y) {
        double scale = yAxis.getScale();
        double min = yAxis.getLowerBound();
        double max = yAxis.getUpperBound();
        double range = max - min;
        double center = (max + min) / 2.0;

        if (is1D()) {
            if (!datasetAttributesList.isEmpty()) {
                datasetAttributesList.stream().forEach(dataAttr -> {
                    double fOffset = dataAttr.getOffset();
                    fOffset -= y / getHeight();
                    dataAttr.setOffset(fOffset);
                });
                setYAxisByLevel();
            }
        } else {
            center -= y / scale;
            min = center - range / 2.0;
            max = center + range / 2.0;
            double[] limits = getRange(1, min, max);
            setYAxis(limits[0], limits[1]);
        }
    }

    protected void adjustScale(double factor) {
        ChartUndoScale undo = new ChartUndoScale(this);
        datasetAttributesList.stream().forEach(dataAttr -> {
            adjustScale(dataAttr, factor);
        });
        layoutPlotChildren();
        ChartUndoScale redo = new ChartUndoScale(this);
        controller.undoManager.add("ascale", undo, redo);

    }

    protected void adjustScale(DatasetAttributes dataAttr, double factor) {
        DatasetBase dataset = dataAttr.getDataset();
        if (is1D()) {
            double oldLevel = dataAttr.getLvl();
            double newLevel = oldLevel * factor;
            dataAttr.setLvl(newLevel);
            setYAxisByLevel();
        } else if (dataset != null) {
            double scale = factor;
            if (scale > 2.0) {
                scale = 2.0;
            } else if (scale < 0.5) {
                scale = 0.5;
            }
            double oldLevel = dataAttr.getLvl();
            dataAttr.setLvl(oldLevel * scale);
        }
    }

    public void scaleY(double y) {
        double factor = (y / 200.0 + 1.0);
        if (factor > 2.0) {
            factor = 2.0;
        } else if (factor < 0.5) {
            factor = 0.5;
        }
        final double scale = factor;
        datasetAttributesList.stream().forEach(dataAttr -> {
            DatasetBase dataset = dataAttr.getDataset();
            if (is1D()) {
                double oldLevel = dataAttr.getLvl();
                dataAttr.setLvl(oldLevel * scale);
                setYAxisByLevel();
            } else if (dataset != null) {
                double oldLevel = dataAttr.getLvl();
                dataAttr.setLvl(oldLevel * scale);
            }
        });
        layoutPlotChildren();
    }

    protected void yZoom(double factor) {
        double min = yAxis.getLowerBound();
        double max = yAxis.getUpperBound();
        double range = max - min;
        double center = (max + min) / 2.0;
        if (factor > 2.0) {
            factor = 2.0;
        } else if (factor < 0.5) {
            factor = 0.5;
        }
        range = range / factor;
        // fixme add check for too small range
        min = center - range / 2.0;
        max = center + range / 2.0;
        double[] limits = getRange(1, min, max);

        setYAxis(limits[0], limits[1]);

    }

    public int getAxisNum(String ax) {
        int axNum = -1;
        char ch = ax.charAt(0);
        if ((ax.length() == 1) && Character.isLetter(ch) && Character.isLowerCase(ch)) {
            axNum = ch - 'x';
            if (axNum < 0) {
                axNum = ch - 'a' + 3;
            }
        }
        if (axNum == -1) {
            throw new IllegalArgumentException("Invalid axis name: \"" + ax + "\"");
        }

        return axNum;
    }

    public int getAxisForLabel(String ax) {
        int axNum = getDimNames().indexOf(ax);
        if (axNum == -1) {
            throw new IllegalArgumentException("Invalid axis name: \"" + ax + "\"");
        }
        return axNum;
    }

    protected void setXAxis(double min, double max) {
        double range = max - min;
        double delta = range / 10;
        xAxis.setMinMax(min, max);
    }

    protected void setYAxis(double min, double max) {
        yAxis.setMinMax(min, max);
    }

    public void setAxis(int iAxis, double min, double max) {
        if (axes.length > iAxis) {
            NMRAxis axis = axes[iAxis];
            axis.setMinMax(min, max);
        }
    }

    public void incrementRow(int amount) {
        DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
        DatasetBase dataset = getDataset();
        if (dataset.getNDim() < 2) {
            return;
        }
        datasetAttributes.incrDrawList(amount);

        layoutPlotChildren();
    }

    public void incrementPlane(int axis, int amount) {
        if (axes.length > axis) {
            ChartUndoLimits undo = new ChartUndoLimits(controller.getActiveChart());
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
            DatasetBase dataset = getDataset();
            int indexL = axModes[axis].getIndex(datasetAttributes, axis, axes[axis].getLowerBound());
            int indexU = axModes[axis].getIndex(datasetAttributes, axis, axes[axis].getUpperBound());
            int[] maxLimits = datasetAttributes.getMaxLimitsPt(axis);

            indexL += amount;
            indexU += amount;
            if (indexL < maxLimits[0]) {
                indexL = maxLimits[0];
            }
            if (indexU < maxLimits[0]) {
                indexU = maxLimits[0];
            }

            if (indexL > maxLimits[1]) {
                indexL = maxLimits[1];
            }
            if (indexU > maxLimits[1]) {
                indexU = maxLimits[1];
            }

            if (axModes[axis] == AXMODE.PTS) {
                axes[axis].setLowerBound(indexL);
                axes[axis].setUpperBound(indexU);

            } else {
                double posL = axModes[axis].indexToValue(datasetAttributes, axis, indexL);
                double posU = axModes[axis].indexToValue(datasetAttributes, axis, indexU);
                axes[axis].setLowerBound(posL);
                axes[axis].setUpperBound(posU);
            }
            layoutPlotChildren();
            ChartUndoLimits redo = new ChartUndoLimits(controller.getActiveChart());
            controller.undoManager.add("plane", undo, redo);
        }
    }

    public void full() {
        ConsoleUtil.runOnFxThread(() -> {
            if (!datasetAttributesList.isEmpty()) {
                ChartUndoLimits undo = new ChartUndoLimits(this);
                double[] limits = getRange(0);
                setXAxis(limits[0], limits[1]);
                if (disDimProp.get() == DISDIM.TwoD) {
                    limits = getRange(1);
                    setYAxis(limits[0], limits[1]);
                }
                ChartUndoLimits redo = new ChartUndoLimits(this);
                controller.undoManager.add("full", undo, redo);
                layoutPlotChildren();
            }
        });
    }

    public void full(int axis) {
        if (axes.length > axis) {
            if (!datasetAttributesList.isEmpty()) {
                double[] limits = getRange(axis);
                setAxis(axis, limits[0], limits[1]);
                updateDatasetAttributeBounds();
                layoutPlotChildren();
            }
        }
    }

    public void center(int axis) {
        if (axes.length > axis) {
            if (!datasetAttributesList.isEmpty()) {
                double[] limits = getRange(axis);
                double center = (limits[0] + limits[1]) / 2.0;
                if (axModes[axis] == AXMODE.PTS) {
                    center = Math.ceil(center);
                }
                setAxis(axis, center, center);
            }
        }
    }

    public boolean isInView(int axis, double position, double edgeFrac) {
        double lim1 = axes[axis].getLowerBound();
        double lim2 = axes[axis].getUpperBound();
        double lower;
        double upper;
        double range = Math.abs(lim2 - lim1);
        if (lim1 < lim2) {
            lower = lim1 + range * edgeFrac;
            upper = lim2 - range * edgeFrac;
        } else {
            lower = lim2 + range * edgeFrac;
            upper = lim1 - range * edgeFrac;
        }
        return position > lower && position < upper;
    }

    public void moveTo(Double[] positions) {
        for (int axis = 0; axis < positions.length; axis++) {
            if (positions[axis] != null) {
                if (axis > 1) {
                    DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
                    if (axModes[axis] == AXMODE.PTS) {
                        int plane = AXMODE.PPM.getIndex(datasetAttributes, axis, positions[axis]);
                        setAxis(axis, plane, plane);
                    } else {
                        setAxis(axis, positions[axis], positions[axis]);
                    }
                } else {
                    double lower = axes[axis].getLowerBound();
                    double upper = axes[axis].getUpperBound();
                    double range = Math.abs(upper - lower);
                    double newLower = positions[axis] - range / 2;
                    double newUpper = positions[axis] + range / 2;
                    setAxis(axis, newLower, newUpper);
                }
            }
        }
        refresh();
    }

    public void moveTo(Double[] positions, Double[] widths) {
        for (int axis = 0; axis < positions.length; axis++) {
            if (positions[axis] != null) {
                if (axis > 1) {
                    DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
                    if (axModes[axis] == AXMODE.PTS) {
                        int plane = AXMODE.PPM.getIndex(datasetAttributes, axis, positions[axis]);
                        setAxis(axis, plane, plane);
                    } else {
                        setAxis(axis, positions[axis], positions[axis]);
                    }
                } else {
                    double range = widths[axis];
                    double newLower = positions[axis] - range / 2;
                    double newUpper = positions[axis] + range / 2;
                    setAxis(axis, newLower, newUpper);
                }
            }
        }
        refresh();
    }

    public void moveTo(int axis, Double position, Double width) {
        if (position != null) {
            if (axis > 1) {
                DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
                setAxis(axis, position, position);
            } else {
                double range = width;
                double newLower = position - range / 2;
                double newUpper = position + range / 2;
                setAxis(axis, newLower, newUpper);
            }
        }
    }

    public void firstPlane(int axis) {
        if (axes.length > axis) {
            if (!datasetAttributesList.isEmpty()) {
                double[] limits = getRange(axis);
                setAxis(axis, limits[0], limits[0]);
            }
        }
    }

    public void lastPlane(int axis) {
        if (axes.length > axis) {
            if (!datasetAttributesList.isEmpty()) {
                double[] limits = getRange(axis);
                setAxis(axis, limits[1], limits[1]);
            }
        }
    }

    protected int[] getPlotLimits(DatasetAttributes datasetAttributes, int iDim) {
        DatasetBase dataset = datasetAttributes.getDataset();

        int min = axModes[iDim].getIndex(datasetAttributes, iDim, axes[iDim].getLowerBound());
        int max = axModes[iDim].getIndex(datasetAttributes, iDim, axes[iDim].getUpperBound());
        if (min > max) {
            int hold = min;
            min = max;
            max = hold;
        }
        int[] limits = {min, max};
        return limits;
    }

    public boolean hasData() {
        DatasetBase dataset = getDataset();
        return dataset != null;
    }

    public boolean is1D() {
        DatasetBase dataset = getDataset();
        return ((dataset != null) && (dataset.getNDim() == 1) || (disDimProp.get() != DISDIM.TwoD));
    }

    public int getNDim() {
        int nDim = 0;
        DatasetBase dataset = getDataset();
        if (dataset != null) {
            nDim = dataset.getNDim();
        }
        if (is1D()) {
            nDim = 1;
        }
        return nDim;
    }

    public void autoScale() {
        ChartUndoScale undo = new ChartUndoScale(this);
        datasetAttributesList.stream().forEach(dataAttr -> {
            autoScale(dataAttr);
        });
        layoutPlotChildren();
        ChartUndoScale redo = new ChartUndoScale(this);
        controller.undoManager.add("ascale", undo, redo);

    }

    protected void autoScale(DatasetAttributes dataAttr) {
        if (is1D()) {
            DatasetBase dataset = dataAttr.getDataset();

            int[] limits = getPlotLimits(dataAttr, 0);
            int nDim = dataset.getNDim();
            int[][] pt = new int[nDim][2];
            int[] cpt = new int[nDim];
            int[] dim = new int[nDim];
            double[] regionWidth = new double[nDim];
            pt[0][0] = limits[0];
            pt[0][1] = limits[1];
            for (int i = 0; i < nDim; i++) {
                dim[i] = i;
                cpt[i] = (pt[i][0] + pt[i][1]) / 2;
                regionWidth[i] = (double) Math.abs(pt[i][0] - pt[i][1]);
            }
            RegionData rData;
            try {
                rData = dataset.analyzeRegion(pt, cpt, regionWidth, dim);
            } catch (IOException ioE) {
                return;
            }
            double min = rData.getMin();
            double max = rData.getMax();

            double range = max - min;
            if (max == min) {
                if (max == 0.0) {
                    range = 1.0;
                } else {
                    range = max;
                }
            }
            max += range / 20.0;
            min -= range / 20.0;

            double delta = max - min;
            double fOffset = (0.0 - min) / delta;
            dataAttr.setOffset(fOffset);
            dataAttr.setLvl(delta);
            setYAxisByLevel();
        } else {
            DatasetBase datasetBase = dataAttr.getDataset();
            if (datasetBase instanceof Dataset) {
                Dataset dataset = (Dataset) datasetBase;
                Double sdev = dataset.guessNoiseLevel();
                double[] percentile = null;
                try {
                    percentile = getPercentile(dataAttr, 90.0);
                } catch (IOException ex) {
                    percentile = null;
                }

                if (sdev != null) {
                    double value = sdev * 5.0;
                    if (percentile != null) {
                        if (value < percentile[0]) {
                            value = percentile[0];
                        }
                    }
                    dataAttr.setLvl(value);
                    level = value;
                }
            }
        }
    }

    protected void setYAxisByLevel() {
        if (!datasetAttributesList.isEmpty()) {
            DatasetAttributes dataAttr = datasetAttributesList.get(0);
            double delta = dataAttr.getLvl();
            double fOffset = dataAttr.getOffset();
            double min = -fOffset * delta;
            double max = min + delta;
            setYAxis(min, max);
        }

    }

    public void updatePhaseDim() {
        if ((controller.chartProcessor == null) || !controller.processControllerVisible.get()) {
            setPhaseDim(phaseAxis);
        }
    }

    protected void setPhaseDim(int phaseDim) {
        String vecDimName = "";
        if ((controller.chartProcessor != null) && controller.processControllerVisible.get()) {
            vecDimName = controller.chartProcessor.getVecDimName();
            datasetPhaseDim = controller.chartProcessor.mapToDataset(phaseDim);
        } else {
            if (datasetAttributesList.isEmpty()) {
                datasetPhaseDim = phaseDim;
            } else {
                DatasetAttributes dataAttr = datasetAttributesList.get(0);
                datasetPhaseDim = dataAttr.dim[phaseDim];
            }
        }

        phaseAxis = 0;
        if (is1D() || vecDimName.equals("D1")) {
            phaseAxis = 0;
        } else if (phaseDim > 0) {
            phaseAxis = 1;
        }
    }

    protected void setPhasePivot() {
        DatasetBase dataset = getDataset();
        if (dataset == null) {
            setPivot(null);
        } else {
            setPivot(crossHairPositions[0][(phaseAxis + 1) % 2]);
        }
    }

    protected void autoPhaseFlat(boolean doFirst) {
        DatasetBase dataset = getDataset();

        if ((dataset == null) || (dataset.getVec() == null)) {
            return;
        }
        VecBase vecBase = dataset.getVec();
        Vec vec;
        if (vecBase instanceof Vec) {
            vec = (Vec) vecBase;
        } else {
            return;
        }
        double[] phases = vec.autoPhase(doFirst, 0, 0, 0, 45.0, 1.0);
        setPh0(phases[0]);
        setPh1(0.0);
        if (phases.length == 2) {
            setPh1(phases[1]);
        }
        log.info("ph0 {} ph1 {}", getPh0(), getPh1());

        double sliderPH0 = getPh0();
        sliderPH0 = getPh0() + vec.getPH0();
        double sliderPH1 = getPh1();
        sliderPH1 = getPh1() + vec.getPH1();
        controller.getPhaser().handlePh1Reset(sliderPH1);
        controller.getPhaser().handlePh0Reset(sliderPH0);
        layoutPlotChildren();
    }

    protected void autoPhaseMax() {
        DatasetBase dataset = getDataset();

        if ((dataset == null) || (dataset.getVec() == null)) {
            return;
        }

        VecBase vecBase = dataset.getVec();
        Vec vec;
        if (vecBase instanceof Vec) {
            vec = (Vec) vecBase;
        } else {
            return;
        }
        setPh0(vec.autoPhaseByMax());
        double sliderPH0 = getPh0();
        sliderPH0 = getPh0() + vec.getPH0();
        double sliderPH1 = getPh1();
        if (vec != null) {
            sliderPH1 = getPh1() + vec.getPH1();
        }
        controller.getPhaser().handlePh1Reset(sliderPH1);
        controller.getPhaser().handlePh0Reset(sliderPH0);

        layoutPlotChildren();
    }

    protected void expand(int cNum) {
        if (!datasetAttributesList.isEmpty()) {
            int dNum = 1;
            if (cNum == 1) {
                dNum = 0;
            }
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
            double[] limits = datasetAttributes.checkLimits(axModes[dNum], dNum, crossHairPositions[0][cNum], crossHairPositions[1][cNum]);
            setAxis(dNum, limits[0], limits[1]);
        }
    }

    public void expand() {
        ChartUndoLimits undo = new ChartUndoLimits(this);
        expand(VERTICAL);
        DatasetBase dataset = getDataset();
        if (dataset != null) {
            if (disDimProp.get() == DISDIM.TwoD) {
                expand(HORIZONTAL);
            }
        }
        layoutPlotChildren();
        crossHairs.hideCrossHairs();
        ChartUndoLimits redo = new ChartUndoLimits(this);
        controller.undoManager.add("expand", undo, redo);

    }

    public double[] getVerticalCrosshairPositions() {
        double[] positions = new double[2];
        positions[0] = crossHairPositions[0][1];
        positions[1] = crossHairPositions[1][1];
        return positions;
    }

    protected double getRefPositionFromCrossHair(double newPPM) {
        DatasetBase dataset = getDataset();
        if (dataset == null) {
            return 0.0;
        }
        DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
        if (controller.chartProcessor == null) {
            return 0.0;
        }
        String vecDimName = controller.chartProcessor.getVecDimName();
        int vecDim = controller.chartProcessor.getVecDim();
        double position;
        double ppmPosition;
        double refPoint;
        double refPPM;
        int size;
        double centerPPM;
        if (is1D() || vecDimName.equals("D1")) {
            position = axModes[0].getIndex(datasetAttributes, 0, crossHairPositions[0][1]);
            size = dataset.getSizeReal(datasetAttributes.dim[0]);
            refPoint = dataset.getRefPt(datasetAttributes.dim[0]);
            refPPM = dataset.getRefValue(datasetAttributes.dim[0]);
            ppmPosition = dataset.pointToPPM(0, position);
            centerPPM = dataset.pointToPPM(0, size / 2);
        } else {
            position = axModes[vecDim].getIndex(datasetAttributes, vecDim, crossHairPositions[0][0]);
            size = dataset.getSizeReal(datasetAttributes.dim[vecDim]);
            refPoint = dataset.getRefPt(datasetAttributes.dim[0]);
            refPPM = dataset.getRefValue(datasetAttributes.dim[0]);
            ppmPosition = dataset.pointToPPM(datasetAttributes.dim[vecDim], position);
            centerPPM = dataset.pointToPPM(datasetAttributes.dim[vecDim], size / 2);
        }
        double newCenter = (newPPM - ppmPosition) + centerPPM;

        double f1 = position / (size - 1);
        log.info("{} {} {} {} {} {} {} {}", vecDim, size, position, ppmPosition, f1, refPoint, refPPM, newCenter);
        return newCenter;
    }

    public void addRegionRange() {
        DatasetBase dataset = getDataset();
        if (dataset == null) {
            return;
        }
        DatasetAttributes datasetAttributes = datasetAttributesList.get(0);

        if (controller.chartProcessor == null) {
            return;
        }
        String vecDimName = controller.chartProcessor.getVecDimName();
        int vecDim = controller.chartProcessor.getVecDim();
        double min;
        double max;
        int size;
        if (is1D() || vecDimName.equals("D1")) {
            min = axModes[0].getIndex(datasetAttributes, 0, crossHairPositions[0][1]);
            max = axModes[0].getIndex(datasetAttributes, 0, crossHairPositions[1][1]);
            size = dataset.getSizeReal(datasetAttributes.dim[0]);
        } else {
            min = axModes[vecDim].getIndex(datasetAttributes, vecDim, crossHairPositions[0][0]);
            max = axModes[vecDim].getIndex(datasetAttributes, vecDim, crossHairPositions[1][0]);
            size = dataset.getSizeReal(datasetAttributes.dim[vecDim]);
        }
        int[] currentRegion = controller.getExtractRegion(vecDimName, size);
        if (min > max) {
            double hold = min;
            min = max;
            max = hold;
        }
        if (min < 0) {
            min = 0.0;
        }
        min += currentRegion[0];
        max += currentRegion[0];
        /*
         if (max >= size) {
         max = size - 1;
         }
         */
        double f1 = min / (size - 1);
        double f2 = max / (size - 1);
        double mul = Math.pow(10.0, Math.ceil(Math.log10(size)));
        f1 = Math.round(f1 * mul) / mul;
        f2 = Math.round(f2 * mul) / mul;
        processorController.propertyManager.addExtractRegion(min, max, f1, f2);
    }

    public void addBaselineRange(boolean clearMode) {
        DatasetBase dataset = getDataset();
        if (dataset == null) {
            return;
        }
        DatasetAttributes datasetAttributes = datasetAttributesList.get(0);

        if (controller.chartProcessor == null) {
            return;
        }
        String vecDimName = controller.chartProcessor.getVecDimName();
        int vecDim = controller.chartProcessor.getVecDim();
        double min;
        double max;
        int size;
        if (is1D() || vecDimName.equals("D1")) {
            min = axModes[0].getIndex(datasetAttributes, 0, crossHairPositions[0][1]);
            max = axModes[0].getIndex(datasetAttributes, 0, crossHairPositions[1][1]);
            size = dataset.getSizeReal(datasetAttributes.dim[0]);
        } else {
            min = axModes[vecDim].getIndex(datasetAttributes, vecDim, crossHairPositions[0][0]);
            max = axModes[vecDim].getIndex(datasetAttributes, vecDim, crossHairPositions[1][0]);
            size = dataset.getSizeReal(datasetAttributes.dim[vecDim]);
        }

        ArrayList<Double> currentRegions = controller.getBaselineRegions(vecDimName);
        if (min > max) {
            double hold = min;
            min = max;
            max = hold;
        }
        if (min < 0) {
            min = 0.0;
        }
        double f1 = min / (size - 1);
        double f2 = max / (size - 1);
        double mul = Math.pow(10.0, Math.ceil(Math.log10(size)));
        f1 = Math.round(f1 * mul) / mul;
        f2 = Math.round(f2 * mul) / mul;
        processorController.propertyManager.addBaselineRegion(currentRegions, f1, f2, clearMode);
    }

    public void clearBaselineRanges() {
        processorController.propertyManager.clearBaselineRegions();
    }

    public void clearDataAndPeaks() {
        datasetAttributesList.clear();
        peakListAttributesList.clear();
        refresh();
    }

    public void updateDatasets(List<String> targets) {
        ObservableList<DatasetAttributes> datasetAttrs = getDatasetAttributes();
        List<DatasetAttributes> newList = new ArrayList<>();
        boolean updated = false;
        int iTarget = 0;
        for (String s : targets) {
            int n = newList.size();
            int jData = 0;
            int addAt = -1;
            for (DatasetAttributes datasetAttr : datasetAttrs) {
                if (datasetAttr.getDataset().getName().equals(s) && !newList.contains(datasetAttr)) {
                    newList.add(datasetAttr);
                    addAt = jData;
                }
                jData++;
            }
            if (iTarget != addAt) {
                updated = true;
            }
            // if didn't add one, then create new DatasetAttributes
            if (newList.size() == n) {
                Dataset dataset = Dataset.getDataset(s);
                if (dataset != null) {
                    int nDim = dataset.getNDim();
                    // fixme kluge as not all datasets that are freq domain have attribute set
                    for (int i = 0; (i < nDim) && (i < 2); i++) {
                        dataset.setFreqDomain(i, true);
                    }
                    DatasetAttributes newAttr = new DatasetAttributes(dataset);
                    newList.add(newAttr);
                    updated = true;
                } else {
                    log.info("No dataset {}", s);
                }
            }
            iTarget++;
        }
        if (newList.size() != datasetAttrs.size()) {
            updated = true;
        }
        if (updated) {
            if (!newList.isEmpty() && datasetAttrs.isEmpty()) {
                // if no datsets present already must use addDataset once to set up
                // various parameters
                controller.addDataset(newList.get(0).getDataset(), false, false);
                newList.remove(0);
                datasetAttrs.addAll(newList);
            } else {
                datasetAttrs.clear();
                datasetAttrs.addAll(newList);
            }
        }
    }

    public void updateProjections() {
        boolean has1D = false;
        boolean hasND = false;
        Optional<DatasetAttributes> firstNDAttr = Optional.empty();
        for (DatasetAttributes datasetAttributes : datasetAttributesList) {
            Dataset dataset = (Dataset) datasetAttributes.getDataset();
            if (dataset != null) {
                if (dataset.getNDim() == 1) {
                    has1D = true;
                } else if (disDimProp.get() == DISDIM.TwoD) {
                    hasND = true;
                    if (firstNDAttr.isEmpty()) {
                        firstNDAttr = Optional.of(datasetAttributes);
                    }
                }
            }
        }
        boolean mixedDim = has1D && hasND;
        List<Integer> alreadyMatchedDims = new ArrayList<>();
        for (DatasetAttributes datasetAttributes : datasetAttributesList) {
            Dataset dataset = (Dataset) datasetAttributes.getDataset();
            if (!mixedDim || (dataset == null) || dataset.getNDim() > 1) {
                datasetAttributes.projection(-1);
            } else {
                int[] matchDim = datasetAttributes.getMatchDim(firstNDAttr.get(), true);
                if (matchDim[0] != -1) {
                    if (!alreadyMatchedDims.contains(matchDim[0])) {
                        datasetAttributes.projection(matchDim[0]);
                        alreadyMatchedDims.add(matchDim[0]);
                    } else {
                        // If the projection is already set for the other axis of a homonuclear experiment, switch the axis
                        datasetAttributes.projection(matchDim[0] == 0 ? 1 : 0);
                    }
                }
            }
        }
    }

    public void setDataset(DatasetBase dataset) {
        setDataset(dataset, false, false);
    }

    public boolean containsDataset(DatasetBase dataset) {
        boolean result = false;
        for (DatasetAttributes dataAttr : datasetAttributesList) {
            if (dataAttr.getDataset() == dataset) {
                result = true;
                break;
            }
        }
        return result;
    }

    public DatasetAttributes setDataset(DatasetBase dataset, boolean append, boolean keepLevel) {
        SpectrumStatusBar statusBar = controller.getStatusBar();
        DatasetAttributes datasetAttributes = null;
        if (dataset != null) {
            if ((dataset.getNDim() == 1) || (dataset.getNFreqDims() == 1)) {
                disDimProp.set(DISDIM.OneDX);
                //statusBar.sliceStatus.setSelected(false);
                setSliceStatus(false);
            } else {
                disDimProp.set(DISDIM.TwoD);
            }
            if (append) {
                datasetAttributes = new DatasetAttributes(dataset);
                if (datasetAttributes.getDataset().isLvlSet()) {
                    datasetAttributes.setLvl(datasetAttributes.getDataset().getLvl());
                    datasetAttributes.setHasLevel(true);
                }
                datasetAttributesList.add(datasetAttributes);
            } else {
                peakListAttributesList.clear();
                if (datasetAttributesList.isEmpty()) {
                    if ((lastDatasetAttr != null) && (lastDatasetAttr.getDataset().getName().equals(dataset.getName()))) {
                        datasetAttributes = lastDatasetAttr;
                        double oldLevel = datasetAttributes.getLvl();
                        datasetAttributes.setDataset(dataset);
                        datasetAttributes.setLvl(oldLevel);
                        datasetAttributes.setHasLevel(true);
                    } else {
                        datasetAttributes = new DatasetAttributes(dataset);
                        if (datasetAttributes.getDataset().isLvlSet()) {
                            datasetAttributes.setLvl(dataset.getLvl());
                            datasetAttributes.setHasLevel(true);
                        }
                    }
                } else {
                    datasetAttributes = datasetAttributesList.get(0);
                    DatasetBase existingDataset = datasetAttributes.getDataset();
                    double oldLevel = datasetAttributes.getLvl();
                    datasetAttributes.setDataset(dataset);
                    if ((existingDataset == null) || (!keepLevel && !existingDataset.getName().equals(dataset.getName()))) {
                        datasetAttributes.setLvl(dataset.getLvl());
                    } else if ((existingDataset != null) && existingDataset.getName().equals(dataset.getName())) {
                        datasetAttributes.setLvl(oldLevel);
                        datasetAttributes.setHasLevel(true);
                    }
                }
                datasetAttributesList.setAll(datasetAttributes);
            }
            // fixme should we do this
            for (int i = 0; i < datasetAttributes.dim.length; i++) {
                datasetAttributes.dim[i] = i;
            }

            updateAxisType();
            datasetFileProp.set(dataset.getFile());
            datasetAttributes.drawList.clear();
        } else {
            //statusBar.sliceStatus.setSelected(false);
            setSliceStatus(false);

            datasetFileProp.setValue(null);
        }
        if (FXMLController.specAttrWindowController != null) {
            FXMLController.specAttrWindowController.updateDims();
        }
        crossHairs.hideCrossHairs();
        return datasetAttributes;
    }

    public int setDrawlist(int value) {
        if (!datasetAttributesList.isEmpty()) {
            for (DatasetAttributes datasetAttributes : datasetAttributesList) {
                datasetAttributes.setDrawListSize(1);
                DatasetBase dataset = datasetAttributes.getDataset();
                if (value < 0) {
                    value = 0;
                }
                if (value >= dataset.getSizeReal(1)) {
                    value = dataset.getSizeReal(1) - 1;
                }

                datasetAttributes.setDrawList(value);
            }
        } else {
            value = 0;
        }
        controller.getStatusBar().updateRowSpinner(value, 1);
        return value;
    }

    public void clearDrawlist() {
        for (DatasetAttributes datasetAttributes : datasetAttributesList) {
            datasetAttributes.setDrawListSize(0);
        }
    }

    public void setDrawlist(List<Integer> selected) {
        for (DatasetAttributes datasetAttributes : datasetAttributesList) {
            datasetAttributes.setDrawList(selected);
        }
        if (!selected.isEmpty()) {
            controller.getStatusBar().updateRowSpinner(selected.get(0), 1);
        }
    }

    /**
     * Gets the draw list from the first Dataset Attribute. If there is more than one dataset
     * attribute and the draw lists are not the same, a warning is logged.
     * @return A list of row indices to draw.
     */
    public List<Integer> getDrawList() {
        if (datasetAttributesList.isEmpty()) {
            log.info("No draw list present.");
        }
        List<Integer> drawList = datasetAttributesList.get(0).drawList;
        for (int i = 1; i < datasetAttributesList.size(); i++ ) {
            if (!drawList.equals(datasetAttributesList.get(i).drawList)) {
                log.warn("Dataset draw lists are not equal. Using draw list for: {}", datasetAttributesList.get(0).getFileName());
                break;
            }
        }
        return drawList;
    }

    public ArrayList<String> getDimNames() {
        ArrayList<String> names = new ArrayList<>();
        if (!datasetAttributesList.isEmpty()) {
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
            int nDim = datasetAttributes.nDim;
            for (int i = 0; i < nDim; i++) {
                String label = datasetAttributes.getLabel(i);
                names.add(label);
            }
        }
        return names;
    }

    void updateDatasetAttributeBounds() {
        for (DatasetAttributes datasetAttributes : datasetAttributesList) {
            datasetAttributes.updateBounds(axModes, axes, disDimProp.getValue());
        }
    }

    public void setAxisState(boolean leftEdge, boolean bottomEdge) {
        yAxis.setShowTicsAndLabels(leftEdge);
        xAxis.setShowTicsAndLabels(bottomEdge);
        xAxis.setTickLabelsVisible(bottomEdge);
        xAxis.setTickMarksVisible(bottomEdge);
        xAxis.setLabelVisible(bottomEdge);
        yAxis.setTickLabelsVisible(leftEdge);
        yAxis.setTickMarksVisible(leftEdge);
        yAxis.setLabelVisible(leftEdge);
    }

    void setAxisState(NMRAxis axis, String axisLabel) {
        boolean state = axis.getShowTicsAndLabels();
        axis.setTickLabelsVisible(state);
        axis.setTickMarksVisible(state);
        axis.setLabelVisible(state);

        axis.setVisible(true);
        if (!state) {
            axis.setLabel("");
        } else {
            if (!axisLabel.equals(axis.getLabel())) {
                axis.setLabel(axisLabel);
            }
        }
    }

    void setDatasetAttr(DatasetAttributes datasetAttrs) {
        DatasetBase dataset = datasetAttrs.getDataset();
        int nDim = dataset.getNDim();
        int nAxes = nDim;
        if (is1D()) {
            nAxes = 2;
        }
        datasetAttributesList.clear();
        datasetAttributesList.add(datasetAttrs);
        if (axes.length != nAxes) {
            axes = new NMRAxis[nAxes];
            axes[0] = xAxis;
            axes[1] = yAxis;
            axModes = new AXMODE[nAxes];
            axModes[0] = AXMODE.PPM;
            axModes[1] = AXMODE.PPM;
            for (int i = 2; i < nAxes; i++) {
                if (axes[i] != null) {
                    //axes[i] = new NMRAxis(Orientation.HORIZONTAL, Position.BOTTOM, datasetAttrs.pt[i][0], datasetAttrs.pt[i][1], 4);
                    if (dataset.getFreqDomain(i)) {
                        axModes[i] = AXMODE.PPM;
                    } else {
                        axModes[i] = AXMODE.PTS;
                    }
                    axes[i].lowerBoundProperty().addListener(new AxisChangeListener(this, i, 0));
                    axes[i].upperBoundProperty().addListener(new AxisChangeListener(this, i, 1));
                }
            }
            drawSpectrum.setAxes(axes);
            drawSpectrum.setDisDim(disDimProp.getValue());
        }
    }

    public void updateAxisType() {
        DatasetBase dataset = getDataset();
        DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
        int nDim = dataset.getNDim();
        int nAxes = nDim;
        if (is1D()) {
            nAxes = 2;
        }
        if (axes.length != nAxes) {
            axes = new NMRAxis[nAxes];
            axes[0] = xAxis;
            axes[1] = yAxis;
            datasetAttributes.dim[0] = 0;
            if (datasetAttributes.dim.length > 1) {
                datasetAttributes.dim[1] = 1;
            }
            axModes = new AXMODE[nAxes];
            axModes[0] = AXMODE.PPM;
            axModes[1] = AXMODE.PPM;
            for (int i = 2; i < nAxes; i++) {
                double[] ppmLimits = datasetAttributes.getMaxLimits(i);
                double centerPPM = (ppmLimits[0] + ppmLimits[1]) / 2.0;
                axes[i] = new NMRAxis(Orientation.HORIZONTAL, centerPPM, centerPPM, 0, 1);
                datasetAttributes.dim[i] = i;
                if (dataset.getFreqDomain(i)) {
                    axModes[i] = AXMODE.PPM;
                } else {
                    axModes[i] = AXMODE.PTS;
                }
                axes[i].lowerBoundProperty().addListener(new AxisChangeListener(this, i, 0));
                axes[i].upperBoundProperty().addListener(new AxisChangeListener(this, i, 1));

            }
            drawSpectrum.setAxes(axes);
            drawSpectrum.setDisDim(disDimProp.getValue());
        }
        if (dataset.getFreqDomain(0)) {
            axModes[0] = AXMODE.PPM;
        } else {
            axModes[0] = AXMODE.TIME;
        }
        boolean reversedAxis = (axModes[0] == AXMODE.PPM);
        boolean autoScale = false;
        if (reversedAxis != xAxis.getReverse()) {
            autoScale = true;
        }
        xAxis.setReverse(reversedAxis);
        String xLabel = axModes[0].getLabel(datasetAttributes, 0);
        setAxisState(xAxis, xLabel);
        if (!is1D()) {
            if (dataset.getFreqDomain(1)) {
                axModes[1] = AXMODE.PPM;
            } else {
                axModes[1] = AXMODE.PTS;
            }
            reversedAxis = (axModes[1] == AXMODE.PPM);
            if (reversedAxis != xAxis.getReverse()) {
                autoScale = true;
            }

            yAxis.setReverse(reversedAxis);
            String yLabel = axModes[0].getLabel(datasetAttributes, 1);
            setAxisState(yAxis, yLabel);

        } else {
            yAxis.setReverse(false);
            setAxisState(yAxis, "Intensity");
        }
        if (autoScale) {
            full();
            if (!datasetAttributes.getHasLevel()) {
                autoScale();
            }
        }
    }

    public void refresh() {
        layoutPlotChildren();
    }

    public void draw() {
        if (Platform.isFxApplicationThread()) {
            refresh();
        } else {
            Platform.runLater(() -> {
                refresh();
            }
            );
        }
    }

    public double[] getMinBorders() {
        xAxis.setTickFontSize(chartProps.getTicFontSize());
        xAxis.setLabelFontSize(chartProps.getLabelFontSize());
        double[] borders = new double[4];

        yAxis.setTickFontSize(chartProps.getTicFontSize());
        yAxis.setLabelFontSize(chartProps.getLabelFontSize());

        borders[0] = is1D() && !chartProps.getIntensityAxis() ? 8 : yAxis.getBorderSize();
        borders[2] = xAxis.getBorderSize();

        borders[1] = borders[0] / 4;
        borders[3] = borders[2] / 4;
        borders[3] = chartProps.getTopBorderSize();
        borders[1] = chartProps.getRightBorderSize();
        return borders;
    }

    public double[] getUseBorders() {
        double[] borders = getMinBorders();
        borders[0] = Math.max(borders[0], minLeftBorder);
        borders[0] = Math.max(borders[0], chartProps.getLeftBorderSize());
        borders[2] = Math.max(borders[2], minBottomBorder);
        borders[2] = Math.max(borders[2], chartProps.getBottomBorderSize());
        if (chartProps.getAspect() && !is1D()) {
            adjustAspect(borders);
        }
        return borders;
    }

    void adjustAspect(double[] borders) {
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        double width = getWidth();
        double height = getHeight();
        if ((axModes[0] == AXMODE.PPM) && (axModes[1] == AXMODE.PPM)) {
            if (!datasetAttributesList.isEmpty()) {
                DatasetAttributes dAttr = datasetAttributesList.get(0);
                DatasetBase dataset = dAttr.getDataset();
                if (dataset.getNDim() > 1) {
                    Nuclei nuc0 = dataset.getNucleus(dAttr.getDim(0));
                    Nuclei nuc1 = dataset.getNucleus(dAttr.getDim(1));
                    if ((nuc0 != null) && (nuc1 != null)) {
                        double fRatio0 = dataset.getNucleus(dAttr.getDim(0)).getFreqRatio();
                        double fRatio1 = dataset.getNucleus(dAttr.getDim(1)).getFreqRatio();
                        double dXAxis = Math.abs(xAxis.getUpperBound() - xAxis.getLowerBound());
                        double dYAxis = Math.abs(yAxis.getUpperBound() - yAxis.getLowerBound());

                        double ppmRatio = dXAxis / dYAxis;
                        double ppmRatioF = fRatio1 / fRatio0;
                        double chartAspectRatio = chartProps.getAspectRatio();
                        double aspectRatio = chartAspectRatio * (ppmRatio / ppmRatioF);
                        double dX = width - borders[0] - borders[1];
                        double dY = height - (borders[2] + borders[3]);
                        double newDX = dY * aspectRatio;

                        if (newDX > dX) {
                            double newDY = dX / aspectRatio;
                            borders[3] = height - borders[2] - newDY;
                        } else {
                            borders[1] = width - borders[0] - newDX;
                        }
                    }
                }
            }
        }
    }

    public void setChartDisabled(boolean state) {
        disabled = state;
    }

    public boolean isChartDisabled() {
        return disabled;
    }

    public static Color chooseBlackWhite(Color color) {
        Color result;
        if (color.getBrightness() > 0.5) {
            result = Color.BLACK;
        } else {
            result = Color.WHITE;
        }
        return result;
    }

    private void setCrossHairColors(Color fillColor) {
        Color color0 = chartProps.getCross0Color();
        if (color0 == null) {
            color0 = chooseBlackWhite(fillColor);
        }
        Color color1 = chartProps.getCross1Color();
        if (color1 == null) {
            if (color0 == Color.BLACK) {
                color1 = Color.RED;
            } else {
                color1 = Color.MAGENTA;
            }
        }

        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                if (i == 0) {
                    crossHairLines[i][j].setStroke(color0);
                } else {
                    crossHairLines[i][j].setStroke(color1);
                }
            }
        }
    }

    void highlightChart() {
        boolean multipleCharts = CHARTS.size() > 1;
        //if (multipleCharts && (activeChart.get() == this)) {
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        double width = getWidth();
        double height = getHeight();
        highlightRect.setX(xPos + 1);
        highlightRect.setY(yPos + 1);
        highlightRect.setWidth(width - 2);
        highlightRect.setHeight(height - 2);
        double[][] corners = getCorners();
        double size = 10;
        for (int i = 0; i < corners.length; i++) {
            Rectangle rect = canvasHandles.get(i);
            double[] corner = corners[i];
            double dX = i < 2 ? 0 : -size;
            double dY = i % 2 == 0 ? 0 : -size;
            rect.setX(corner[0] + dX);
            rect.setY(corner[1] + dY);
            rect.setWidth(size);
            rect.setHeight(size);
            rect.setStroke(Color.BLUE);
            rect.setFill(null);
        }
    }

    public void useImmediateMode(boolean state) {
        useImmediateMode = state;
    }

    protected void layoutPlotChildren() {
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        double width = getWidth();
        double height = getHeight();
        if (disabled) {
            return;
        }
        if (!useImmediateMode) {
            long lastPlotTime = drawSpectrum.getLastPlotTime();
            if ((lastPlotTime != 0) && (lastPlotTime < 1000)) {
                useImmediateMode = true;
            }
        }
        useImmediateMode = false;
        GraphicsContext gCC = canvas.getGraphicsContext2D();
        GraphicsContextInterface gC = new GraphicsContextProxy(gCC);
        GraphicsContextInterface gCPeaks = new GraphicsContextProxy(peakCanvas.getGraphicsContext2D());
        if (is1D()) {
            setYAxisByLevel();
        }
        try {
            gC.save();
            gC.clearRect(xPos, yPos, width, height);
            Color fillColor = Color.WHITE;
            if (chartProps.getBgColor() != null) {
                fillColor = chartProps.getBgColor();
                gC.setFill(fillColor);
                gC.fillRect(xPos, yPos, width, height);
            } else if (controller.getBgColor() != null) {
                fillColor = controller.getBgColor();
                gC.setFill(fillColor);
                gC.fillRect(xPos, yPos, width, height);
            }
            setCrossHairColors(fillColor);

            Color axesColorLocal = chartProps.getAxesColor();
            if (axesColorLocal == null) {
                axesColorLocal = controller.getAxesColor();
                if (axesColorLocal == null) {
                    axesColorLocal = chooseBlackWhite(fillColor);

                }
            }

            xAxis.setTickFontSize(chartProps.getTicFontSize());
            xAxis.setLabelFontSize(chartProps.getLabelFontSize());

            yAxis.setTickFontSize(chartProps.getTicFontSize());
            yAxis.setLabelFontSize(chartProps.getLabelFontSize());
            double[] borders = getUseBorders();
            leftBorder = borders[0];
            rightBorder = borders[1];
            bottomBorder = borders[2];
            topBorder = borders[3];

            xAxis.setWidth(width - leftBorder - rightBorder);
            xAxis.setHeight(bottomBorder);
            xAxis.setOrigin(xPos + leftBorder, yPos + getHeight() - bottomBorder);

            yAxis.setHeight(height - bottomBorder - topBorder);
            yAxis.setWidth(leftBorder);
            yAxis.setOrigin(xPos + leftBorder, yPos + getHeight() - bottomBorder);

            gC.setStroke(axesColorLocal);
            xAxis.setColor(axesColorLocal);
            yAxis.setColor(axesColorLocal);
            if (chartProps.getGrid()) {
                xAxis.setGridLength(yAxis.getHeight());
                yAxis.setGridLength(xAxis.getWidth());
            } else {
                xAxis.setGridLength(0.0);
                yAxis.setGridLength(0.0);

            }
            gC.setLineWidth(xAxis.getLineWidth());
            xAxis.draw(gC);
            if (!is1D() || chartProps.getIntensityAxis()) {
                yAxis.draw(gC);
                gC.strokeLine(xPos + leftBorder, yPos + topBorder, xPos + width - rightBorder, yPos + topBorder);
                gC.strokeLine(xPos + width - rightBorder, yPos + topBorder, xPos + width - rightBorder, yPos + height - bottomBorder);
            }

            peakCanvas.setWidth(canvas.getWidth());
            peakCanvas.setHeight(canvas.getHeight());
            GraphicsContext peakGC = peakCanvas.getGraphicsContext2D();
            peakGC.clearRect(xPos, yPos, width, height);
            gC.beginPath();
//
//        if (annoCanvas != null) {
//            annoCanvas.setWidth(width);
//            annoCanvas.setHeight(height);
//            GraphicsContext annoGC = annoCanvas.getGraphicsContext2D();
//            annoGC.clearRect(0, 0, width, height);
//        }

            if (!drawDatasets(gC)) {
                // if we used immediate mode and didn't finish in time try again
                // useImmediate mode will have been set to false
                Platform.runLater(() -> layoutPlotChildren());
                gC.restore();
                return;
            }

            if (!datasetAttributesList.isEmpty()) {
                drawPeakLists(true);
            }
            drawParameters(chartProps.getParameters());
            drawAnnotations(gCPeaks);
            crossHairs.refreshCrossHairs();
            gC.restore();
            highlightChart();

        } catch (GraphicsIOException ioE) {
            log.warn(ioE.getMessage(), ioE);
        }
    }

    protected void exportVectorGraphics(String fileName, String fileType) throws IOException {
        SVGGraphicsContext svgGC = new SVGGraphicsContext();
        try {
            svgGC.create(true, canvas.getWidth(), canvas.getHeight(), fileName);
            exportVectorGraphics(svgGC);
            svgGC.saveFile();
        } catch (GraphicsIOException ex) {
            throw new IOException(ex.getMessage());
        }
    }

    protected void exportVectorGraphics(GraphicsContextInterface svgGC) throws GraphicsIOException {
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        double width = getWidth();
        double height = getHeight();
        Color fillColor = Color.WHITE;
        if (chartProps.getBgColor() != null) {
            fillColor = chartProps.getBgColor();
            svgGC.setFill(fillColor);
            svgGC.fillRect(xPos, yPos, width, height);
        } else if (controller.getBgColor() != null) {
            fillColor = controller.getBgColor();
            svgGC.setFill(fillColor);
            svgGC.fillRect(xPos, yPos, width, height);
        }

        Color axesColorLocal = chartProps.getAxesColor();
        if (axesColorLocal == null) {
            axesColorLocal = controller.getAxesColor();
            if (axesColorLocal == null) {
                axesColorLocal = chooseBlackWhite(fillColor);
            }
        }
        if (is1D()) {
            setYAxisByLevel();
        }
        svgGC.setStroke(axesColorLocal);
        xAxis.setColor(axesColorLocal);
        yAxis.setColor(axesColorLocal);
        if (chartProps.getGrid()) {
            xAxis.setGridLength(yAxis.getHeight());
            yAxis.setGridLength(xAxis.getWidth());
        } else {
            xAxis.setGridLength(0.0);
            yAxis.setGridLength(0.0);

        }

        xAxis.draw(svgGC);
        if (!is1D() || chartProps.getIntensityAxis()) {
            yAxis.draw(svgGC);
            svgGC.strokeLine(xPos + leftBorder, yPos + topBorder, xPos + width - rightBorder, yPos + topBorder);
            svgGC.strokeLine(xPos + width - rightBorder, yPos + topBorder, xPos + width - rightBorder, yPos + height - bottomBorder);
        }
        drawDatasets(svgGC);
        drawSlices(svgGC);
        if (!datasetAttributesList.isEmpty()) {
            drawPeakLists(true, svgGC);
            drawSelectedPeaks(svgGC);
        }

    }

    boolean drawDatasets(GraphicsContextInterface gC) throws GraphicsIOException {
        double maxTextOffset = -1.0;
        ArrayList<DatasetAttributes> draw2DList = new ArrayList<>();
        updateDatasetAttributeBounds();
        int nDatasets = datasetAttributesList.size();
        int iTitle = 0;
        double firstOffset = 0.0;
        double firstLvl = 1.0;
        updateProjections();
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        for (DatasetAttributes datasetAttributes : datasetAttributesList) {
            try {
                DatasetAttributes firstAttr = datasetAttributesList.get(0);
                DatasetBase dataset = datasetAttributes.getDataset();
                if (datasetAttributes.projection() != -1) {
                    continue;
                }
                if (dataset != null) {
//                datasetAttributes.setLvl(level);
                    datasetAttributes.setDrawReal(true);
                    if (datasetAttributes != firstAttr) {
                        datasetAttributes.syncDims(firstAttr);
                    } else {
                        firstOffset = datasetAttributes.getOffset();
                        firstLvl = datasetAttributes.getLvl();
                        updateAxisType();
                    }

                    if (disDimProp.get() != DISDIM.TwoD) {
                        if (chartProps.getRegions()) {
                            drawRegions(datasetAttributes, gC);
                        }
                        gC.save();
                        double clipExtra = 1;
                        drawSpectrum.setClipRect(xPos + leftBorder + clipExtra, yPos + topBorder + clipExtra,
                                xAxis.getWidth() - 2 * clipExtra, yAxis.getHeight() - 2 * clipExtra);

                        drawSpectrum.clip(gC);
                        try {
                            for (int iMode = 0; iMode < 2; iMode++) {
                                if (iMode == 0) {
                                    datasetAttributes.setDrawReal(true);
                                } else {
                                    if (!controller.getStatusBar().complexStatus.isSelected()) {
                                        break;
                                    }
                                    datasetAttributes.setDrawReal(false);
                                }
                                bcList.clear();
                                drawSpectrum.setToLastChunk(datasetAttributes);
                                boolean ok;
                                do {
                                    bcPath.getElements().clear();
                                    ok = drawSpectrum.draw1DSpectrum(datasetAttributes, firstLvl, firstOffset, HORIZONTAL, axModes[0], getPh0(), getPh1(), bcPath);
                                    double[][] xy = drawSpectrum.getXY();
                                    int nPoints = drawSpectrum.getNPoints();
                                    int rowIndex = drawSpectrum.getRowIndex();
                                    drawSpecLine(datasetAttributes, gC, iMode, rowIndex, nPoints, xy);
                                    gC.setFill(datasetAttributes.getPosColor(rowIndex));
                                    if (chartProps.getIntegrals()) {
                                        draw1DIntegral(datasetAttributes, gC);
                                    }
                                    drawBaseLine(gC, bcPath);

                                } while (ok);
                            }
                            drawSpectrum.drawVecAnno(datasetAttributes, HORIZONTAL, axModes[0]);
                            double[][] xy = drawSpectrum.getXY();
                            int nPoints = drawSpectrum.getNPoints();
                            drawSpecLine(datasetAttributes, gC, 0, -1, nPoints, xy);
                        } finally {
                            gC.restore();
                        }
                        if (chartProps.getTitles()) {
                            drawTitle(gC, datasetAttributes, iTitle++, nDatasets);
                        }

                    } else {
                        draw2DList.add(datasetAttributes);
                    }
                }

            } catch (GraphicsIOException gIO) {
                log.warn(gIO.getMessage(), gIO);
            }
        }
        for (DatasetAttributes datasetAttributes : datasetAttributesList) {
            if (datasetAttributes.projection() != -1) {
                drawProjection(gC, datasetAttributes.projection(), datasetAttributes);
            }
        }
        boolean finished = true;
        if (!draw2DList.isEmpty()) {
            if (chartProps.getTitles()) {
                double fontSize = chartProps.getTicFontSize();
                gC.setFont(Font.font(fontSize));
                gC.setTextAlign(TextAlignment.LEFT);
                double textX = xPos + leftBorder + 10.0;
                double textY;
                if (fontSize > (topBorder - 2)) {
                    gC.setTextBaseline(VPos.TOP);
                    textY = yPos + topBorder + 2;
                } else {
                    gC.setTextBaseline(VPos.BOTTOM);
                    textY = yPos + topBorder - 2;
                }
                for (DatasetAttributes datasetAttributes : draw2DList) {
                    gC.setFill(datasetAttributes.getPosColor());
                    String title = datasetAttributes.getDataset().getTitle();
                    gC.fillText(title, textX, textY);
                    textX += GUIUtils.getTextWidth(title, gC.getFont()) + 10;
                }
            }
            if (gC instanceof GraphicsContextProxy) {
                if (useImmediateMode) {
                    finished = drawSpectrum.drawSpectrumImmediate(gC, draw2DList, axModes);
                    useImmediateMode = finished;
                } else {
                    drawSpectrum.drawSpectrum(draw2DList, axModes, false);
                }
            } else {
                finished = drawSpectrum.drawSpectrumImmediate(gC, draw2DList, axModes);
            }
        }
        return finished;

    }

    void drawTitle(GraphicsContextInterface gC, DatasetAttributes datasetAttributes,
                   int index, int nTitles) {
        gC.setFill(datasetAttributes.getPosColor());
        double fontSize = chartProps.getTicFontSize();
        gC.setFont(Font.font(fontSize));
        double textY;
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        if ((nTitles > 1) || fontSize > (topBorder - 2)) {
            gC.setTextBaseline(VPos.TOP);
            textY = yPos + topBorder + 2;
            double offset = (nTitles - index - 1) * fontSize;
            textY += offset;
        } else {
            gC.setTextBaseline(VPos.BOTTOM);
            textY = yPos + topBorder - 2;
        }
        gC.setTextAlign(TextAlignment.LEFT);
        gC.fillText(datasetAttributes.getDataset().getTitle(),
                xPos + leftBorder + 10, textY);
    }

    void drawParameters(boolean state) {
        if ((state == false) && (parameterText != null)) {
            removeAnnotation(parameterText);
            parameterText = null;
        } else if (state == true) {
            Dataset dataset = (Dataset) getDataset();
            if (dataset != null) {
                String text = ProjectText.genText(dataset);
                if ((parameterText == null) || (!parameterText.getText().equals(text))) {
                     if (parameterText == null) {
                        double textY;
                        double xPos = getLayoutX();
                        double yPos = getLayoutY();
                        textY = yPos + topBorder + chartProps.getTicFontSize() * 2;
                        double textWidth = 200;
                        parameterText = new AnnoText(xPos, textY, textWidth, 200,
                                CanvasAnnotation.POSTYPE.PIXEL, CanvasAnnotation.POSTYPE.PIXEL, text);
                        addAnnotation(parameterText);
                    } else {
                        parameterText.setText(text);
                    }
                }
            }
        }
    }

    public ChartBorder hitBorder(double x, double y) {
        ChartBorder border = ChartBorder.NONE;
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        double width = getWidth();
        double height = getHeight();
        boolean leftX = (x > xPos) && (x < xPos + leftBorder);
        boolean centerX = (x > (leftBorder + xPos) && (x < xPos + width - rightBorder));
        boolean rightX = (x > xPos + width - rightBorder) && (x < xPos + width);
        boolean topY = (y > yPos) && (y < yPos + topBorder);
        boolean centerY = (y > yPos + topBorder) && (y < yPos + height - bottomBorder);
        boolean bottomY = (y > yPos + height - bottomBorder) && (y < yPos + height);
        if (leftX && centerY) {
            border = ChartBorder.LEFT;
        } else if (bottomY && centerX) {
            border = ChartBorder.BOTTOM;
        } else if (rightX && centerY) {
            border = ChartBorder.RIGHT;
        } else if (topY && centerX) {
            border = ChartBorder.TOP;
        }
        return border;
    }

    void drawRegions(DatasetAttributes datasetAttr, GraphicsContextInterface gC) throws GraphicsIOException {
        Set<DatasetRegion> regions = datasetAttr.getDataset().getRegions();
        if (regions == null) {
            return;
        }
        double chartHeight = yAxis.getHeight();
        for (DatasetRegion region : regions) {
            double ppm1 = region.getRegionStart(0);
            double ppm2 = region.getRegionEnd(0);
            double x1 = xAxis.getDisplayPosition(ppm2);
            double x2 = xAxis.getDisplayPosition(ppm1);
            if (x1 > x2) {
                double hold = x1;
                x1 = x2;
                x2 = hold;
            }
            if (region == activeRegion.get()) {
                gC.setFill(Color.YELLOW);
            } else {
                gC.setFill(Color.LIGHTYELLOW);
            }
            gC.fillRect(x1, getLayoutY() + topBorder + 1, x2 - x1, chartHeight - 2);

        }
    }

    void draw1DIntegral(DatasetAttributes datasetAttr, GraphicsContextInterface gC) throws GraphicsIOException {
        Set<DatasetRegion> regions = datasetAttr.getDataset().getRegions();
        if (regions == null) {
            return;
        }
        double xMin = xAxis.getLowerBound();
        double xMax = xAxis.getUpperBound();
        double chartHeight = yAxis.getHeight();
        double integralOffset = chartHeight * 0.75;
        integralOffset = 0.0;
        double norm = datasetAttr.getDataset().getNorm() / datasetAttr.getDataset().getScale();
        double integralMax = getIntegralMaxFromRegions(regions);
        for (DatasetRegion region : regions) {
            double ppm1 = region.getRegionStart(0);
            double ppm2 = region.getRegionEnd(0);
            double[] offsets = new double[2];
            offsets[0] = region.getRegionStartIntensity(0);
            offsets[1] = region.getRegionEndIntensity(0);

            if ((ppm2 > xMin) && (ppm1 < xMax)) {
                Optional<Double> result = drawSpectrum.draw1DIntegrals(datasetAttr,
                        HORIZONTAL, axModes[0], ppm1, ppm2, offsets,
                        integralMax, chartProps.getIntegralLowPos(),
                        chartProps.getIntegralHighPos());
                if (result.isPresent()) {
                    double[][] xy = drawSpectrum.getXY();
                    int nPoints = drawSpectrum.getNPoints();
                    int rowIndex = drawSpectrum.getRowIndex();
                    gC.setTextAlign(TextAlignment.CENTER);
                    gC.setTextBaseline(VPos.BASELINE);
                    drawSpecLine(datasetAttr, gC, 0, rowIndex, nPoints, xy);
                    String text = String.format("%.1f", result.get() / norm);
                    double xCenter = (xy[0][0] + xy[0][nPoints - 1]) / 2.0;
                    double yCenter = (xy[1][0] + xy[1][nPoints - 1]) / 2.0;
                    gC.fillText(text, xCenter, yCenter);
                }
            }
        }
        datasetAttr.getActiveRegion().ifPresent(r -> {
            try {
                drawSpectrum.drawActiveRegion(gC, datasetAttr, r.getDatasetRegion());
            } catch (GraphicsIOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        });
    }

    public Optional<IntegralHit> hitIntegral(double pickX, double pickY) {
        Optional<IntegralHit> hit = Optional.empty();
        for (DatasetAttributes datasetAttr : datasetAttributesList) {
            hit = hitIntegral(datasetAttr, pickX, pickY);
            if (hit.isPresent()) {
                break;
            }
        }
        return hit;
    }

    /**
     * Gets the max absolute value of the region integrals.
     * @param regions The regions to search.
     * @return The max integral value.
     */
    private double getIntegralMaxFromRegions(Set<DatasetRegion> regions) {
        double integralMax = 0.0;
        for (DatasetRegion region : regions) {
            integralMax = Math.max(integralMax, Math.abs(region.getIntegral()));
        }
        return integralMax;
    }

    public Optional<IntegralHit> hitIntegral(DatasetAttributes datasetAttr, double pickX, double pickY) {
        Optional<IntegralHit> hit = Optional.empty();
        Set<DatasetRegion> regions = datasetAttr.getDataset().getRegions();
        if (regions != null) {
            double xMin = xAxis.getLowerBound();
            double xMax = xAxis.getUpperBound();
            int hitRange = 10;
            double integralMax = getIntegralMaxFromRegions(regions);
            for (DatasetRegion region : regions) {
                double ppm1 = region.getRegionStart(0);
                double ppm2 = region.getRegionEnd(0);
                double[] offsets = new double[2];
                offsets[0] = region.getRegionStartIntensity(0);
                offsets[1] = region.getRegionEndIntensity(0);

                if ((ppm2 > xMin) && (ppm1 < xMax)) {
                    Optional<Double> result = drawSpectrum.draw1DIntegrals(datasetAttr,
                            HORIZONTAL, axModes[0], ppm1, ppm2, offsets,
                            integralMax, chartProps.getIntegralLowPos(),
                            chartProps.getIntegralHighPos());
                    if (result.isPresent()) {
                        double[][] xy = drawSpectrum.getXY();
                        int nPoints = drawSpectrum.getNPoints();
                        for (int i = 0; i < nPoints; i++) {
                            double x = xy[0][i];
                            double y = xy[1][i];
                            if ((Math.abs(pickX - x) < hitRange) && (Math.abs(pickY - y) < hitRange)) {
                                int handle = i < nPoints /2 ? -1 : -2;
                                Bounds bounds = new BoundingBox(xy[0][0], xy[1][nPoints-1],xy[0][nPoints-1]-xy[0][0],xy[1][0]-xy[1][nPoints-1]);
                                hit = Optional.of(new IntegralHit(datasetAttr, region, handle, bounds));
                                break;
                            }
                        }
                        if (hit.isPresent()) {
                            break;
                        }
                    }
                }
            }
        }
        return hit;
    }

    public void setActiveRegion(DatasetRegion region) {
        activeRegion.set(region);
    }

    public void clearActiveRegion() {
        activeRegion.set(null);
    }

    public Optional<DatasetRegion> getActiveRegion() {
        Optional<DatasetRegion> region = Optional.empty();
        if (activeRegion.get() != null) {
            region = Optional.of(activeRegion.get());
        }
        return region;
    }

    public boolean selectRegion(boolean controls, double pickX, double pickY) {
        for (DatasetAttributes datasetAttr : datasetAttributesList) {
            datasetAttr.setActiveRegion(null);
        }
        Optional<IntegralHit> hit = hitRegion(controls, pickX, pickY);
        hit.ifPresentOrElse(iHit -> {
            iHit.getDatasetAttr().setActiveRegion(iHit);
            activeRegion.set(hit.get().getDatasetRegion());
        }, () -> activeRegion.set(null));

        return hit.isPresent();
    }

    public boolean selectRegionControls(double pickX, double pickY) {
        for (DatasetAttributes datasetAttr : datasetAttributesList) {
            datasetAttr.setActiveRegion(null);
        }
        Optional<IntegralHit> hit = hitRegion(true, pickX, pickY);
        hit.ifPresentOrElse(iHit -> {
            iHit.getDatasetAttr().setActiveRegion(iHit);
            activeRegion.set(hit.get().getDatasetRegion());
        }, () -> activeRegion.set(null));
        return hit.isPresent();
    }

    public void addRegionListener(ChangeListener<DatasetRegion> listener) {
        activeRegion.addListener(listener);
    }

    public Optional<IntegralHit> selectIntegral(double pickX, double pickY) {
        for (DatasetAttributes datasetAttr : datasetAttributesList) {
            datasetAttr.setActiveRegion(null);
        }
        Optional<IntegralHit> hit = hitIntegral(pickX, pickY);
        hit.ifPresentOrElse(iHit -> {
            iHit.getDatasetAttr().setActiveRegion(iHit);
            activeRegion.set(iHit.getDatasetRegion());
        }, () ->activeRegion.set(null));
        return hit;

    }

    public boolean hasActiveRegion() {
        boolean hasRegion = false;
        for (DatasetAttributes datasetAttr : datasetAttributesList) {
            if (datasetAttr.getActiveRegion().isPresent()) {
                hasRegion = true;
                break;
            }
        }
        return hasRegion;
    }

    public void refreshActiveRegion(boolean hit) {
        if (!hit) {
            boolean hadHit = false;
            for (DatasetAttributes datasetAttr : datasetAttributesList) {
                if (datasetAttr.getActiveRegion().isPresent()) {
                    hadHit = true;
                }
                datasetAttr.setActiveRegion(null);
            }
            if (hadHit) {
                refresh();
            }
        } else {
            refresh();
        }
    }

    public Optional<IntegralHit> hitRegion(boolean controls, double pickX, double pickY) {
        Optional<IntegralHit> hit = Optional.empty();
        for (DatasetAttributes datasetAttr : datasetAttributesList) {

            Set<DatasetRegion> regions = datasetAttr.getDataset().getRegions();
            if (regions == null) {
                continue;
            }
            for (DatasetRegion region : regions) {
                hit = drawSpectrum.hitRegion(datasetAttr, region, controls, pickX, pickY);
                if (hit.isPresent()) {
                    break;
                }
            }
            if (hit.isPresent()) {
                break;
            }

        }

        return hit;
    }

    void drawSpecLine(DatasetAttributes datasetAttributes, GraphicsContextInterface gC, int iMode, int rowIndex, int nPoints, double[][] xy) throws GraphicsIOException {
        if (nPoints > 1) {
            if (iMode == 0) {
                gC.setStroke(datasetAttributes.getPosColor(rowIndex));
                gC.setLineWidth(datasetAttributes.getPosWidth());
            } else {
                gC.setStroke(datasetAttributes.getNegColor());
                gC.setLineWidth(datasetAttributes.getNegWidth());
            }
            gC.setLineCap(StrokeLineCap.BUTT);
            gC.strokePolyline(xy[0], xy[1], nPoints);
        }
    }

    void drawBaseLine(GraphicsContextInterface gC, Path path) throws GraphicsIOException {
        List<PathElement> elems = path.getElements();
        int nMove = 0;
        if (elems.size() > 1) {
            gC.beginPath();
            for (PathElement elem : elems) {
                if (elem instanceof MoveTo) {
                    MoveTo mv = (MoveTo) elem;
                    nMove++;
                    gC.moveTo(mv.getX(), mv.getY());
                } else if (elem instanceof LineTo) {
                    LineTo ln = (LineTo) elem;
                    gC.lineTo(ln.getX(), ln.getY());
                }
            }

            gC.setStroke(Color.ORANGE);
            gC.setLineWidth(3.0);
            gC.stroke();
        }
    }

    void purgeInvalidPeakListAttributes() {
        Iterator<PeakListAttributes> iterator = peakListAttributesList.iterator();
        while (iterator.hasNext()) {
            PeakList peakList = iterator.next().getPeakList();
            if (!peakList.valid()) {
                iterator.remove();
            }
        }

    }

    public PeakListAttributes setupPeakListAttributes(PeakList peakList) {
        purgeInvalidPeakListAttributes();
        boolean present = false;
        String listName = peakList.getName();
        PeakListAttributes newPeakListAttr = null;
        for (PeakListAttributes peakListAttr : peakListAttributesList) {
            if (peakListAttr.peakListNameProperty().get().equals(listName)) {
                if (peakListAttr.getPeakList().peaks() == null) {
                    peakListAttr.setPeakList(peakList);
                }
                newPeakListAttr = peakListAttr;
                present = true;
                break;
            }
        }
        if (!present) {
            if (isPeakListCompatible(peakList, true)) {
                DatasetAttributes matchData = null;
                for (DatasetAttributes dataAttr : datasetAttributesList) {
                    DatasetBase dataset = dataAttr.getDataset();
                    String datasetName = dataset.getName();
                    if (datasetName.length() != 0) {
                        if (peakList.getDatasetName().equals(datasetName)) {
                            matchData = dataAttr;
                            break;
                        }
                    }
                }
                if (matchData == null) {
                    matchData = datasetAttributesList.get(0);
                }
                PeakListAttributes peakListAttr = new PeakListAttributes(this, matchData, peakList);
                peakListAttributesList.add(peakListAttr);
                peakList.registerPeakChangeListener(this);
                newPeakListAttr = peakListAttr;

            }
        }
        return newPeakListAttr;
    }

    public void removeUnusedPeakLists(List<String> targets) {
        ObservableList<PeakListAttributes> peakAttrs = getPeakListAttributes();
        List<PeakListAttributes> newList = new ArrayList<>();
        boolean removeSome = false;
        for (PeakListAttributes peakAttr : peakAttrs) {
            boolean found = false;
            for (String s : targets) {
                if (peakAttr.getPeakListName().equals(s)) {
                    newList.add(peakAttr);
                    found = true;
                    break;
                }
            }
            if (!found) {
                peakAttr.getPeakList().removePeakChangeListener(this);
            }
            removeSome = !found;
        }
        if (removeSome) {
            peakAttrs.clear();
            peakAttrs.addAll(newList);
        }
    }

    public void updatePeakLists(List<String> targets) {
        removeUnusedPeakLists(targets);
        for (String s : targets) {
            PeakList peakList = PeakList.get(s);
            if (peakList != null) {
                setupPeakListAttributes(peakList);
            }
        }
    }

    public void setCanvasCursor() {
        canvas.setCursor(Cursor.CROSSHAIR);
    }

    public void deleteSelectedItems() {
        deleteSelectedPeaks();
        deleteSelectedAnnotations();
    }

    public void deleteSelectedPeaks() {
        List<Peak> deletedPeaks = new ArrayList<>();
        for (PeakListAttributes peakListAttr : peakListAttributesList) {
            Set<Peak> peaks = peakListAttr.getSelectedPeaks();
            for (Peak peak : peaks) {
                peak.setStatus(-1);
                deletedPeaks.add(peak);
            }
        }
        if (!deletedPeaks.isEmpty() && (manualPeakDeleteAction != null)) {
            PeakDeleteEvent peakDeleteEvent = new PeakDeleteEvent(deletedPeaks, this);
            manualPeakDeleteAction.accept(peakDeleteEvent);
        }
    }

    public List<Peak> getSelectedPeaks() {
        List<Peak> selectedPeaks = new ArrayList<>();
        peakListAttributesList.stream().forEach(peakListAttr -> {
            if (peakListAttr.getDrawPeaks()) {
                Set<Peak> peaks = peakListAttr.getSelectedPeaks();
                selectedPeaks.addAll(peaks);
            }
        });
        return selectedPeaks;
    }

    public void clearSelectedMultiplets() {
        peakListAttributesList.stream().forEach(peakListAttr -> {
            if (peakListAttr.getDrawPeaks()) {
                peakListAttr.clearSelectedPeaks();
            }
        });
    }

    public List<MultipletSelection> getSelectedMultiplets() {
        List<MultipletSelection> multiplets = new ArrayList<>();
        peakListAttributesList.stream().forEach(peakListAttr -> {
            if (peakListAttr.getDrawPeaks()) {
                Set<MultipletSelection> mSels = peakListAttr.getSelectedMultiplets();
                multiplets.addAll(mSels);
            }
        });
        return multiplets;
    }

    public void dragRegion(IntegralHit regionHit, double[] dragStart, double x, double y) {
        double[] dragPos = {x, y};
        for (DatasetAttributes datasetAttr : datasetAttributesList) {
            if (datasetAttr.getActiveRegion().isPresent()) {
                datasetAttr.moveRegion(regionHit, axes, dragPos);
                refresh();
            }
        }
    }

    public void dragAnno(double[] dragStart, double x, double y, CanvasAnnotation anno) {
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        double width = getWidth();
        double height = getHeight();
        double[][] bounds = {{xPos + leftBorder, xPos + width - rightBorder}, {yPos + topBorder, yPos + height - bottomBorder}};
        double[][] world = {{axes[0].getUpperBound(), axes[0].getLowerBound()},
                {axes[1].getLowerBound(), axes[1].getUpperBound()}};
        double[] dragPos = {x, y};
        anno.move(bounds, world, dragStart, dragPos);
        drawPeakLists(false);
    }

    public void finishAnno(double[] dragStart, double x, double y, CanvasAnnotation anno) {
        double[] dragPos = {x, y};
        anno.move(dragStart, dragPos);
    }

    public void dragPeak(double[] dragStart, double x, double y, boolean widthMode) {
        boolean draggedAny = false;
        double[] dragPos = {x, y};
        for (PeakListAttributes peakListAttr : peakListAttributesList) {
            Set<Peak> peaks = peakListAttr.getSelectedPeaks();
            for (Peak peak : peaks) {
                draggedAny = true;
                if (widthMode) {
                    peakListAttr.resizePeak(peak, dragStart, dragPos);
                } else {
                    peakListAttr.movePeak(peak, dragStart, dragPos);
                }
            }
            Set<MultipletSelection> multipletItems = peakListAttr.getSelectedMultiplets();
            for (MultipletSelection mSel : multipletItems) {
                peakListAttr.moveMultipletCoupling(mSel, dragStart, dragPos);

            }

        }
        dragStart[0] = dragPos[0];
        dragStart[1] = dragPos[1];
        drawPeakLists(false);
        for (PeakListAttributes peakListAttr : peakListAttributesList) {
            drawSelectedPeaks(peakListAttr);
        }
    }

    int[] getFitRows(PeakListAttributes peakListAttr) {
        int nPeakDims = peakListAttr.getPeakList().getNDim();
        DatasetAttributes dataAttr = peakListAttr.getDatasetAttributes();
        int nDataDims = dataAttr.getDataset().getNDim();
        int nRows = nDataDims - nPeakDims;
        int[] dims = dataAttr.getDims();
        int[] rows = new int[nRows];
        for (int i = 0; i < nRows; i++) {
            int iDim = dims[nPeakDims + i];
            rows[i] = dataAttr.getPoint(iDim)[0];
        }
        return rows;
    }

    double[] getFitValues(PeakListAttributes peakListAttr) {
        int nPeakDims = peakListAttr.getPeakList().getNDim();
        DatasetAttributes dataAttr = peakListAttr.getDatasetAttributes();
        int nDataDims = dataAttr.getDataset().getNDim();
        int nRows = nDataDims - nPeakDims;
        int[] dims = dataAttr.getDims();
        int[] rows = new int[nRows];
        double[] values = null;
        if (nRows == 1) {
            values = dataAttr.getDataset().getValues(dims[nPeakDims]);
            log.info("values {}", values);
        }
        return values;
    }

    public void fitPeakLists(int syncDim) {
        fitPeakLists(syncDim, true, false, ARRAYED_FIT_MODE.SINGLE);
    }

    public void fitPeakLists(int syncDim, boolean fitAll, boolean lsFit, ARRAYED_FIT_MODE arrayedFitMode) {
        peakListAttributesList.forEach((peakListAttr) -> {
            DatasetBase datasetBase = peakListAttr.getDatasetAttributes().getDataset();
            Dataset dataset = null;
            if (datasetBase instanceof Dataset) {
                dataset = (Dataset) datasetBase;
            }
            if (dataset == null) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Peak  fit");
                alert.setContentText("No dataset");
                alert.showAndWait();
                return;
            }
            int[] fitRows = getFitRows(peakListAttr);
            if ((arrayedFitMode != ARRAYED_FIT_MODE.SINGLE) && (fitRows.length == 0)) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Peak array fit");
                alert.setContentText("No arrayed rows or planes");
                alert.showAndWait();
                return;
            }
            double[] delays = null;
            if (arrayedFitMode == ARRAYED_FIT_MODE.EXP) {
                log.info("nrows {}", fitRows[0]);
                delays = getFitValues(peakListAttr);
                if ((delays == null)) {

                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Peak exp fit");
                    alert.setContentText("No dataset values for delays");
                    alert.showAndWait();
                    return;
                }
                log.info("ndel {}", delays.length);
            }
            try {

                Set<Peak> peaks = peakListAttr.getSelectedPeaks();
                if (fitAll && peaks.isEmpty()) {
                    PeakListTools.peakFit(peakListAttr.getPeakList(), dataset, fitRows, delays, lsFit, syncDim, arrayedFitMode);
                } else if (!peaks.isEmpty()) {
                    PeakListTools.peakFit(peakListAttr.getPeakList(), dataset, fitRows, delays, peaks, lsFit, syncDim, arrayedFitMode);
                }
            } catch (IllegalArgumentException | IOException | PeakFitException ex) {
                log.error(ex.getMessage(), ex);
            }
        });
    }

    public void clusterPeakLists(int syncDim) {
        peakListAttributesList.forEach((peakListAttr) -> {
            DatasetBase dataset = peakListAttr.getDatasetAttributes().getDataset();
            if (dataset != null) {
                try {
                    PeakListTools.clusterPeakColumns(peakListAttr.getPeakList(), syncDim);
                } catch (IllegalArgumentException ex) {
                    log.error(ex.getMessage(), ex);
                }
            }
        });
    }

    public void tweakPeaks() {
        peakListAttributesList.forEach((peakListAttr) -> {
            Set<Peak> peaks = peakListAttr.getSelectedPeaks();
            if (!peaks.isEmpty()) {
                DatasetBase datasetBase = peakListAttr.getDatasetAttributes().getDataset();
                Dataset dataset = datasetBase instanceof Dataset ? (Dataset) datasetBase : null;
                if (dataset != null) {
                    try {
                        int[] dim = getPeakDim(peakListAttr.getDatasetAttributes(), peakListAttr.getPeakList(), true);
                        for (int i = 0; i < dim.length; i++) {
                            log.info("{} {}", i, dim[i]);
                        }
                        int nExtra = dim.length - peakListAttr.getPeakList().nDim;
                        int[] planes = new int[nExtra];
                        PeakListTools.tweakPeaks(peakListAttr.getPeakList(), dataset, peaks, planes);

                    } catch (IllegalArgumentException ex) {
                        log.error(ex.getMessage(), ex);
                    }
                }
            }
        });
    }

    public void tweakPeakLists() {
        peakListAttributesList.forEach((peakListAttr) -> {
            DatasetBase datasetBase = peakListAttr.getDatasetAttributes().getDataset();
            Dataset dataset = datasetBase instanceof Dataset ? (Dataset) datasetBase : null;
            if (dataset != null) {
                try {
                    int nExtra = dataset.getNDim() - peakListAttr.getPeakList().nDim;
                    int[] planes = new int[nExtra];
                    PeakListTools.tweakPeaks(peakListAttr.getPeakList(), dataset, planes);

                } catch (IllegalArgumentException ex) {
                    log.error(ex.getMessage(), ex);
                }
            }
        });
    }

    public void duplicatePeakList() {
        ChoiceDialog<PeakList> dialog = new ChoiceDialog<>();
        dialog.setTitle("Duplicate Peak List");
        dialog.setContentText("Origin List:");
        PeakList.peakLists().stream().forEach(p -> dialog.getItems().add(p));

        Optional<PeakList> result = dialog.showAndWait();
        if (result.isPresent()) {
            PeakList peakList = result.get();
            if (peakList != null) {
                String newListName = PeakList.getNameForDataset(getDataset().getName());
                if (PeakList.exists(newListName)) {
                    newListName = GUIUtils.input("New list name");
                    if ((newListName == null) || newListName.trim().equals("")) {
                        return;
                    }
                    newListName = newListName.trim();
                    if (PeakList.exists(newListName)) {
                        GUIUtils.warn("Target List Already Exists", "Target List:" + newListName);
                        return;
                    }
                }
                PeakList newPeakList = peakList.copy(newListName, false, false, true);
                if (newPeakList != null) {
                    newPeakList.setDatasetName(getDataset().getName());
                    updatePeakLists(Collections.singletonList(newPeakList.getName()));
                }
            }
        }
    }

    public void drawPeakLists(boolean clear) {

        if (peakCanvas != null) {
            GraphicsContextInterface peakGC = new GraphicsContextProxy(peakCanvas.getGraphicsContext2D());
            drawPeakLists(clear, peakGC);
        }
    }

    public void drawPeakLists(boolean clear, GraphicsContextInterface peakGC) {
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        double width = getWidth();
        double height = getHeight();
        if (peakCanvas != null) {
            peakCanvas.setWidth(canvas.getWidth());
            peakCanvas.setHeight(canvas.getHeight());
            try {
                if (peakGC instanceof GraphicsContextProxy) {
                    peakGC.clearRect(xPos, yPos, width, height);
                }
                if (peakFont.getSize() != PreferencesController.getPeakFontSize()) {
                    peakFont = new Font(fontFamily, PreferencesController.getPeakFontSize());
                }
                peakGC.setFont(peakFont);

                final Iterator<PeakListAttributes> peakListIterator = peakListAttributesList.iterator();
                while (peakListIterator.hasNext()) {
                    PeakListAttributes peakListAttr = peakListIterator.next();
                    if (peakListAttr.getPeakList().peaks() == null) {
                        peakListAttr.getPeakList().removePeakChangeListener(this);
                        peakListIterator.remove();
                    }
                }
                if (peakStatus.get()) {
                    for (PeakListAttributes peakListAttr : peakListAttributesList) {
                        if (clear) {
                            peakListAttr.clearPeaksInRegion();
                        }
                        if (peakListAttr.getDrawPeaks()) {
                            drawPeakList(peakListAttr, peakGC);
                        }
//                drawSelectedPeaks(peakListAttr);
                    }
                }
                if (!peakPaths.isEmpty()) {
                    drawPeakPaths();
                }
                drawAnnotations(peakGC);

//                peakGC.restore();
            } catch (Exception ioE) {
                log.warn(ioE.getMessage(), ioE);
            }
        }
    }

    void drawSelectedPeaks(GraphicsContextInterface peakGC) {
        for (PeakListAttributes peakListAttr : peakListAttributesList) {
            if (peakListAttr.getDrawPeaks()) {
                drawSelectedPeaks(peakListAttr, peakGC);
            }
        }
    }

    public Optional<Peak> hitPeak(double pickX, double pickY) {
        Optional<Peak> hit = Optional.empty();
        if (peakStatus.get()) {
            // drawPeakLists(false);
            for (PeakListAttributes peakListAttr : peakListAttributesList) {
                if (peakListAttr.getDrawPeaks()) {
                    hit = peakListAttr.hitPeak(drawPeaks, pickX, pickY);
                    if (hit.isPresent()) {
                        break;
                    }
                }
            }
        }
        return hit;
    }

    public Optional<MultipletSelection> hitMultiplet(double pickX, double pickY) {
        Optional<MultipletSelection> hit = Optional.empty();
        if (peakStatus.get()) {
            for (PeakListAttributes peakListAttr : peakListAttributesList) {
                if (peakListAttr.getDrawPeaks()) {
                    hit = peakListAttr.hitMultiplet(drawPeaks, pickX, pickY);
                    if (hit.isPresent()) {
                        break;
                    }
                }
            }
        }
        return hit;
    }

    public void showHitPeak(double pickX, double pickY) {
        Optional<Peak> hit = hitPeak(pickX, pickY);
        if (hit.isPresent()) {
            FXMLController.getActiveController().showPeakAttr();
            FXMLController.peakAttrController.gotoPeak(hit.get());
            FXMLController.peakAttrController.getStage().toFront();
        }
    }

    public boolean selectPeaks(double pickX, double pickY, boolean append) {
        if (!append) {
            for (PolyChart chart : CHARTS) {
                if (chart != this) {
                    boolean hadPeaks = false;
                    for (PeakListAttributes peakListAttr : (List<PeakListAttributes>) chart.getPeakListAttributes()) {
                        if (peakListAttr.clearSelectedPeaks()) {
                            hadPeaks = true;
                        }
                    }
                    if (hadPeaks) {
                        chart.drawPeakLists(false);
                    }
                }
            }
        }

        List<Peak> selPeaks = new ArrayList<>();
        drawPeakLists(false);
        boolean hitPeak = false;
        if (peakStatus.get()) {
            for (PeakListAttributes peakListAttr : peakListAttributesList) {
                if (peakListAttr.getDrawPeaks()) {
                    peakListAttr.selectPeak(drawPeaks, pickX, pickY, append);
                    Set<Peak> peaks = peakListAttr.getSelectedPeaks();
                    if (!peaks.isEmpty()) {
                        selPeaks.addAll(peaks);
                        hitPeak = true;
                    }
                    if (!selectedMultiplets.isEmpty()) {
                        hitPeak = true;
                    }
                    drawSelectedPeaks(peakListAttr);
                }
            }
        }
        if (controller == FXMLController.activeController.get()) {
            List<Peak> allSelPeaks = new ArrayList<>();
            for (PolyChart chart : controller.charts) {
                allSelPeaks.addAll(chart.getSelectedPeaks());
            }
            controller.selPeaks.set(allSelPeaks);
        }
        return hitPeak;
    }

    public double[][] getRegionLimits(DatasetAttributes dataAttr) {
        int nDataDim = dataAttr.nDim;
        double[][] limits = new double[nDataDim][2];
        for (int i = 0; ((i < axes.length) && (i < nDataDim)); i++) {
            if (axModes[i] == AXMODE.PPM) {
                limits[i][0] = axes[i].getLowerBound();
                limits[i][1] = axes[i].getUpperBound();
            } else {
                double lb = axes[i].getLowerBound();
                double ub = axes[i].getUpperBound();
                if (Math.abs(lb - ub) < 0.5) {
                    lb = lb - 1.4;
                    ub = ub + 1.4;
                }
                limits[i][1] = AXMODE.PPM.indexToValue(dataAttr, i, lb);
                limits[i][0] = AXMODE.PPM.indexToValue(dataAttr, i, ub);
            }
        }
        return limits;

    }

    public boolean isPeakListCompatible(PeakList peakList, boolean looseMode) {
        boolean result = false;
        if (!datasetAttributesList.isEmpty()) {
            DatasetAttributes dataAttr = datasetAttributesList.get(0);
            int[] peakDim = getPeakDim(dataAttr, peakList, looseMode);
            if (peakDim[0] != -1) {
                if ((peakDim.length == 1) || (peakDim[1] != -1)) {
                    result = true;
                }
            }
        }
        return result;
    }

    int[] getPeakDim(DatasetAttributes dataAttr, PeakList peakList, boolean looseMode) {
        int nPeakDim = peakList.nDim;
        int nDataDim = dataAttr.nDim;
        int[] dim = new int[nDataDim];
        int nMatch = 0;
        int nShouldMatch = 0;
        boolean[] used = new boolean[nPeakDim];

        for (int i = 0; (i < axes.length) && (i < dim.length); i++) {
            dim[i] = -1;
            nShouldMatch++;
            for (int j = 0; j < nPeakDim; j++) {
                if (dataAttr.getLabel(i).equals(peakList.getSpectralDim(j).getDimName())) {
                    dim[i] = j;
                    nMatch++;
                    used[j] = true;
                    break;
                }
            }
        }

        if ((nMatch != nShouldMatch) && looseMode) {
            for (int i = 0; (i < axes.length) && (i < dim.length); i++) {
                if (dim[i] == -1) {
                    for (int j = 0; j < nPeakDim; j++) {
                        if (!used[j]) {
                            String dNuc = dataAttr.getDataset().getNucleus(i).getNumberName();
                            String pNuc = peakList.getSpectralDim(j).getNucleus();
                            if (dNuc.equals(pNuc)) {
                                dim[i] = j;
                                used[j] = true;
                                nMatch++;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return dim;
    }

    void drawPeakList(PeakListAttributes peakListAttr, GraphicsContextInterface gC) {
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        if (peakListAttr.getDrawPeaks()) {
            gC.save();
            try {
                gC.beginPath();
                gC.rect(xPos + leftBorder, yPos + topBorder, xAxis.getWidth(), yAxis.getHeight());
                gC.clip();
                gC.beginPath();
                DatasetAttributes dataAttr = peakListAttr.getDatasetAttributes();
                List<Peak> peaks = peakListAttr.getPeaksInRegion();
                int[] dim = peakListAttr.getPeakDim();
                double[] offsets = new double[dim.length];
                int[][] limits = new int[dim.length][2];
                for (int iDim = 2; iDim < dim.length; iDim++) {
                    limits[iDim] = getPlotLimits(dataAttr, iDim);
                }
                drawPeaks.clear1DBounds();

                peaks.stream().filter(peak -> peak.getStatus() >= 0).forEach((peak) -> {
                    try {
                        for (int iDim = 2; iDim < dim.length; iDim++) {
                            offsets[iDim] = 0.0;
                            if (limits[iDim][0] == limits[iDim][1]) {
                                if (dim[iDim] >= 0) {
                                    double ppm = peak.getPeakDim(dim[iDim]).getChemShiftValue();
                                    double pt = dataAttr.getDataset().ppmToDPoint(dataAttr.dim[iDim], ppm);
                                    double deltaPt = Math.abs(limits[iDim][0] - pt);
                                    offsets[iDim] = deltaPt;
                                }
                            }
                        }
                        drawPeaks.drawPeak(peakListAttr, gC, peak, dim, offsets, false);
                        for (int iDim : dim) {
                            if (iDim >= 0) {
                                peak.peakDims[iDim].setLinkDrawn(false);
                            }
                        }
                    } catch (GraphicsIOException ex) {
                        log.warn("draw peak exception {}", ex.getMessage(), ex);
                    }
                });
                if (peakListAttr.getDrawLinks()) {
                    peaks.stream().filter(peak -> peak.getStatus() >= 0).forEach((peak) -> {
                        try {
                            drawPeaks.drawLinkLines(peakListAttr, gC, peak, dim, false);
                        } catch (GraphicsIOException ex) {
                            log.warn(ex.getMessage(), ex);
                        }
                    });
                }
                if (dim.length == 1) { // only draw multiples for 1D 
                    List<Peak> roots = new ArrayList<>();
                    drawPeaks.clear1DBounds();
                    peaks.stream().filter(peak -> peak.getStatus() >= 0).forEach((peak) -> {
                        try {
                            drawPeaks.drawMultiplet(peakListAttr, gC, peak.getPeakDim(0).getMultiplet(), dim, offsets, false, 0);
                            roots.add(peak);
                        } catch (GraphicsIOException ex) {
                            log.warn("draw peak exception {}", ex.getMessage());
                        } catch (Exception ex2) {
                            log.warn(ex2.getMessage(), ex2);
                        }
                    });

                    if (peakListAttr.getSimPeaks()) {
                        PeakList.sortPeaks(roots, 0, true);
                        ArrayList overlappedPeaks = new ArrayList();
                        for (int iPeak = 0, n = roots.size(); iPeak < n; iPeak++) {
                            Peak aPeak = roots.get(iPeak);
                            overlappedPeaks.add(aPeak);
                            for (int jPeak = (iPeak + 1); jPeak < n; jPeak++) {
                                Peak bPeak = roots.get(jPeak);
                                if (aPeak.overlaps(bPeak, 0, overlapScale)) {
                                    overlappedPeaks.add(bPeak);
                                    aPeak = roots.get(jPeak);
                                    iPeak++;
                                } else {
                                    break;
                                }
                            }
                            gC.setStroke(Color.RED);
                            drawPeaks.drawSimSum(gC, overlappedPeaks, dim);
                            overlappedPeaks.clear();
                        }

                    }
                }

            } catch (GraphicsIOException gioE) {
                log.warn(gioE.getMessage(), gioE);
            } finally {
                gC.restore();
            }
        }
    }

    void drawSelectedPeaks(PeakListAttributes peakListAttr) {
        GraphicsContext gCC = peakCanvas.getGraphicsContext2D();
        GraphicsContextInterface gC = new GraphicsContextProxy(gCC);
        drawSelectedPeaks(peakListAttr, gC);

    }

    public void clearPeakPaths() {
        peakPaths.clear();
    }

    public void addPeakPaths(List<ConnectPeakAttributes> peaks) {
        peakPaths.addAll(peaks);
    }

    public void addPeakPath(ConnectPeakAttributes peaks) {
        peakPaths.add(peaks);
    }

    public List<ConnectPeakAttributes> getPeakPaths() {
        return peakPaths;
    }

    void drawPeakPaths() {
        if (!peakPaths.isEmpty()) {
            GraphicsContext gCC = peakCanvas.getGraphicsContext2D();
            GraphicsContextInterface gC = new GraphicsContextProxy(gCC);
            gC.save();
            gC.beginPath();
            gC.rect(getLayoutX() + leftBorder, getLayoutY() + topBorder, xAxis.getWidth(), yAxis.getHeight());
            gC.clip();
            gC.beginPath();
            peakPaths.stream().forEach((lPeaks) -> {
                drawPeakPaths(lPeaks, gC);
            });
            gC.restore();
        }
    }

    void drawPeakPaths(ConnectPeakAttributes connPeakAttrs, GraphicsContextInterface gC) {
        int[] dim = {0, 1}; // FIXME: Need to generalize this
        drawPeaks.drawPeakConnection(connPeakAttrs, gC, dim);
    }

    void drawSelectedPeaks(PeakListAttributes peakListAttr, GraphicsContextInterface gC) {
        if (peakListAttr.getDrawPeaks()) {
            Set<Peak> peaks = peakListAttr.getSelectedPeaks();
            Set<MultipletSelection> multiplets = peakListAttr.getSelectedMultiplets();
            if (!peaks.isEmpty() || !multiplets.isEmpty()) {
                int[] dim = peakListAttr.getPeakDim();
                double[] offsets = new double[dim.length];
                peaks.stream().forEach((peak) -> {
                    try {
                        drawPeaks.drawPeak(peakListAttr, gC, peak, dim, offsets, true);
                    } catch (GraphicsIOException ex) {
                        log.warn(ex.getMessage(), ex);
                    }
                    int nPeakDim = peak.peakList.nDim;
                    if (peak.getPeakList().isSlideable() && (nPeakDim > 1)) {
                        try {
                            drawPeaks.drawLinkLines(peakListAttr, gC, peak, dim, true);
                        } catch (GraphicsIOException ex) {
                            log.warn(ex.getMessage(), ex);
                        }
                    }
                });
                multiplets.stream().forEach((multipletSel) -> {
                    Multiplet multiplet = multipletSel.getMultiplet();
                    if (multipletSel.isLine()) {
                        int line = multipletSel.getLine();
                        try {
                            drawPeaks.drawMultiplet(peakListAttr, gC, multiplet, dim, offsets, true, line);
                        } catch (GraphicsIOException ex) {
                            log.warn(ex.getMessage(), ex);
                        }
                    }
                });
            }

        }
    }

    public Optional<CanvasAnnotation> hitAnnotation(double x, double y, boolean selectMode) {
        Optional<CanvasAnnotation> result = Optional.empty();
        for (CanvasAnnotation anno : canvasAnnotations) {
            int handle = anno.hitHandle(x, y);
            if ((handle >= 0) || anno.hit(x, y, selectMode)) {
                result = Optional.of(anno);
                break;
            }

        }
        return result;
    }

    public void deleteSelectedAnnotations() {
        Iterator<CanvasAnnotation> iter = canvasAnnotations.iterator();
        while (iter.hasNext()) {
            CanvasAnnotation anno = iter.next();
            if (anno.isSelected()) {
                iter.remove();
                if (anno == parameterText) {
                    parameterText = null;
                }
            }
        }
    }

    public void clearAnnotations() {
        parameterText = null;
        canvasAnnotations.clear();
    }

    public void addAnnotation(CanvasAnnotation anno) {
        canvasAnnotations.add(anno);
    }

    public void removeAnnotation(CanvasAnnotation anno) {
        canvasAnnotations.remove(anno);
        if ((anno != null) && (anno == parameterText)) {
            parameterText = null;
        }
    }

    public boolean hasAnnoType(Class annoClass) {
        Iterator<CanvasAnnotation> iter = canvasAnnotations.iterator();
        while (iter.hasNext()) {
            CanvasAnnotation anno = iter.next();
            if (anno.getClass() == annoClass) {
                return true;
            }
        }
        return false;
    }

    public List<CanvasAnnotation> findAnnoTypes(Class annoClass) {
        return canvasAnnotations.stream().filter(anno -> anno.getClass() == annoClass).collect(Collectors.toList());
    }

    public void clearAnnoType(Class annoClass) {
        Iterator<CanvasAnnotation> iter = canvasAnnotations.iterator();
        while (iter.hasNext()) {
            CanvasAnnotation anno = iter.next();
            if (anno.getClass() == annoClass) {
                iter.remove();
                if ((anno != null) && (anno == parameterText)) {
                    parameterText = null;
                }
            }
        }
    }

    void drawAnnotations(GraphicsContextInterface gC) {
        if (!canvasAnnotations.isEmpty()) {
            double xPos = getLayoutX();
            double yPos = getLayoutY();
            double width = getWidth();
            double height = getHeight();
            gC.save();
            try {
                gC.beginPath();
                gC.rect(xPos, yPos, xAxis.getWidth() + leftBorder + rightBorder, yAxis.getHeight() + topBorder + bottomBorder);
                gC.clip();
                gC.beginPath();
                double[][] bounds = {{xPos + leftBorder, xPos + width - rightBorder}, {yPos + topBorder, yPos + height - bottomBorder}};
                double[][] world = {{axes[0].getUpperBound(), axes[0].getLowerBound()},
                {axes[1].getLowerBound(), axes[1].getUpperBound()}};
                boolean lastClipAxes = false;

                for (CanvasAnnotation anno : canvasAnnotations) {
                    if (anno.getClipInAxes() && !lastClipAxes) {
                        gC.save();
                        gC.rect(xPos + leftBorder, yPos + topBorder, xAxis.getWidth(), yAxis.getHeight());
                        gC.clip();
                        lastClipAxes = true;
                    } else if (!anno.getClipInAxes() && lastClipAxes) {
                        gC.restore();
                        lastClipAxes = false;
                    }
                    anno.draw(gC, bounds, world);
                }
                if (lastClipAxes) {
                    gC.restore();
                }
            } catch (Exception gioE) {
                log.warn(gioE.getMessage(), gioE);
            } finally {
                gC.restore();
            }
        }
    }

    public List<DatasetAttributes> getActiveDatasetAttributes() {
        List<DatasetAttributes> activeData = new ArrayList<>();
        for (DatasetAttributes dataAttr : datasetAttributesList) {
            if (dataAttr.getPos()) {
                activeData.add(dataAttr);
            }
        }
        return activeData;
    }

    public ObservableList<DatasetAttributes> getDatasetAttributes() {
        return datasetAttributesList;
    }

    public ObservableList<PeakListAttributes> getPeakListAttributes() {
        return peakListAttributesList;
    }

    public void resetChartPhases() {
        for (int i = 0; i < chartPhases[0].length; i++) {
            chartPhases[0][i] = 0.0;
            chartPhases[1][i] = 0.0;
        }
    }

    public double getDataPH0() {
        DatasetBase dataset = getDataset();
        double value = 0.0;
        if (dataset != null) {
            if (datasetPhaseDim != -1) {
                value = dataset.getPh0(datasetPhaseDim);
            }
        }
        return value;
    }

    public double getDataPH1() {
        DatasetBase dataset = getDataset();
        double value = 0.0;
        if (dataset != null) {
            if (datasetPhaseDim != -1) {
                value = dataset.getPh1(datasetPhaseDim);
            }
        }
        return value;
    }

    public void setPh0(double ph0) {
        if (datasetPhaseDim != -1) {
            chartPhases[0][datasetPhaseDim] = ph0;
        }
    }

    public void setPh1(double ph1) {
        if (datasetPhaseDim != -1) {
            chartPhases[1][datasetPhaseDim] = ph1;
        }
    }

    public double getPh0() {
        double value = 0.0;
        if (datasetPhaseDim != -1) {
            value = chartPhases[0][datasetPhaseDim];
        }
        return value;
    }

    public double getPh1() {
        double value = 0.0;
        if (datasetPhaseDim != -1) {
            value = chartPhases[1][datasetPhaseDim];
        }
        return value;
    }

    public double getPh0(int iDim) {
        double value;
        if (iDim == 0) {
            value = chartPhases[0][0];
        } else {
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);

            int datasetDim = datasetAttributes.dim[iDim];
            value = chartPhases[0][datasetDim];
        }
        return value;
    }

    public double getPh1(int iDim) {
        double value;
        if (iDim == 0) {
            value = chartPhases[1][0];
        } else {
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);

            int datasetDim = datasetAttributes.dim[iDim];
            value = chartPhases[1][datasetDim];
        }
        return value;
    }

    public double getPivot() {
        if (datasetPhaseDim != -1) {
            return chartPivots[datasetPhaseDim];
        } else {
            return 0.0;
        }
    }

    /**
     * @param pivot the pivot to set
     */
    public void setPivot(Double pivot) {
        if (!datasetAttributesList.isEmpty()) {
            String vecDimName = "";
            if ((controller.chartProcessor != null) && controller.processControllerVisible.get()) {
                vecDimName = controller.chartProcessor.getVecDimName();
            }
            DatasetBase dataset = getDataset();
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
            int datasetDim = -1;
            if (is1D() || vecDimName.equals("D1")) {
                datasetDim = datasetAttributes.dim[0];
                if (pivot == null) {
                    pivotPosition[datasetDim] = null;
                    phaseFraction = 0;
                } else {
                    int position = axModes[0].getIndex(datasetAttributes, 0, pivot);
                    pivotPosition[datasetDim] = pivot;
                    int size = dataset.getSizeReal(datasetDim);
                    phaseFraction = position / (size - 1.0);
                }
            } else if (datasetPhaseDim >= 0) {
                datasetDim = datasetAttributes.dim[phaseAxis];
                if (pivot == null) {
                    pivotPosition[datasetDim] = null;
                    phaseFraction = 0;
                } else {
                    int position = axModes[phaseAxis].getIndex(datasetAttributes, phaseAxis, pivot);
                    int size = dataset.getSizeReal(datasetDim);
                    phaseFraction = position / (size - 1.0);
                    pivotPosition[datasetDim] = pivot;
                }
            }
        }
    }

    public double getPivotFraction() {
        return phaseFraction;
    }

    protected void loadData() {
        canvas.setCache(true);
        peakCanvas.setCache(true);
        peakCanvas.setMouseTransparent(true);
        annoCanvas.setMouseTransparent(true);

        //getPlotChildren().add(1, canvas);
        //getPlotChildren().add(2, peakCanvas);
        //getPlotChildren().add(3, annoCanvas);
    }

    public void addAnnoCanvas() {
        double width = getWidth();
        double height = getHeight();
        if (annoCanvas != null) {
            annoCanvas = new Canvas(width, height);
            annoCanvas.setCache(true);
            annoCanvas.setMouseTransparent(true);
        }
    }

    public void setSliceStatus(boolean state) {
        crossHairs.refreshCrossHairs();
    }

    public void drawSlices() {
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        double width = getWidth();
        double height = getHeight();
        annoCanvas.setWidth(canvas.getWidth());
        annoCanvas.setHeight(canvas.getHeight());
        GraphicsContext annoGC = annoCanvas.getGraphicsContext2D();
        GraphicsContextInterface gC = new GraphicsContextProxy(annoGC);
        gC.clearRect(xPos, yPos, width, height);
        drawSlices(gC);
    }

    public void drawSlices(GraphicsContextInterface gC) {
        if (annoCanvas != null) {

            if (sliceAttributes.slice1StateProperty().get()) {
                drawSlice(gC, 0, VERTICAL);
                drawSlice(gC, 0, HORIZONTAL);
            }
            if (sliceAttributes.slice2StateProperty().get()) {
                drawSlice(gC, 1, VERTICAL);
                drawSlice(gC, 1, HORIZONTAL);
            }
        }
    }

    public void projectDataset() {
        Dataset dataset = (Dataset) getDataset();
        if (dataset == null) {
            return;
        }
        if (dataset.getNDim() == 2) {
            try {
                List<String> datasetNames = new ArrayList<>();
                datasetNames.add(dataset.getName());
                dataset.project(0);
                dataset.project(1);
                Dataset proj0 = dataset.getProjection(0);
                Dataset proj1 = dataset.getProjection(1);
                if (proj0 != null) {
                    datasetNames.add(proj0.getName());
                }
                if (proj1 != null) {
                    datasetNames.add(proj1.getName());
                }
                updateDatasets(datasetNames);
                updateProjections();
                updateProjectionBorders();
                updateProjectionScale();
                refresh();
            } catch (IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }

    }

    public void drawProjection(GraphicsContextInterface gC, int iProj) {
        if (gC == null) {
            return;
        }
        Dataset dataset = (Dataset) getDataset();
        if (dataset == null) {
            return;
        }
        int nDim = dataset.getNDim();
        if (nDim != 2) {
            return;
        }
        DatasetAttributes dataAttr = datasetAttributesList.get(0);

        for (int i = 0; i < 2; i++) {
            if ((iProj == -1) || (i == iProj)) {
                int dDim = dataAttr.getDim(i);
                Dataset datasetProj = dataset.getProjection(dDim);
                if (datasetProj == null) {
                    return;
                }
                //drawProjection(gC, i, datasetProj);
            }
        }
    }

    public void drawProjection(GraphicsContextInterface gC, int iAxis, DatasetAttributes projectionDatasetAttributes) {
        DatasetAttributes dataAttr = datasetAttributesList.get(0);
        Bounds bounds = plotBackground.getBoundsInParent();
        drawSpectrum.drawProjection(projectionDatasetAttributes, dataAttr, iAxis, bounds);
        double[][] xy = drawSpectrum.getXY();
        int nPoints = drawSpectrum.getNPoints();
        gC.setStroke(dataAttr.getPosColor());
        gC.strokePolyline(xy[0], xy[1], nPoints);

    }

    public void extractSlice(int iOrient) {
        if (annoCanvas == null) {
            return;
        }
        DatasetBase dataset = getDataset();
        if (dataset == null) {
            return;
        }
        int iCross = 0;
        int nDim = dataset.getNDim();
        if ((nDim > 1)) {
            if (iOrient < nDim) {
                int iSlice = 0;
                List<String> sliceDatasets = new ArrayList<>();
                for (DatasetAttributes dataAttr : datasetAttributesList) {
                    Vec sliceVec = new Vec(32, false);
                    sliceVec.setName(dataset.getName() + "_slice_" + iSlice);
                    try {
                        dataAttr.getSlice(sliceVec, iOrient, crossHairPositions[iCross][VERTICAL], crossHairPositions[iCross][HORIZONTAL]);
                        Dataset sliceDataset = new Dataset(sliceVec);
                        sliceDataset.setLabel(0, dataset.getLabel(dataAttr.dim[iOrient]));
                        sliceDatasets.add(sliceDataset.getName());
                    } catch (IOException ex) {
                        log.error(ex.getMessage(), ex);
                    }
                    iSlice++;
                }
                if ((sliceController == null)
                        || (!FXMLController.getControllers().contains(sliceController))) {
                    sliceController = FXMLController.create();
                }
                sliceController.getStage().show();
                sliceController.getStage().toFront();
                PolyChart newChart = sliceController.getActiveChart();
                if (newChart == null) {
                    sliceController.addChart();
                    newChart = sliceController.getActiveChart();
                }
                newChart.clearDataAndPeaks();
                newChart.updateDatasets(sliceDatasets);
                sliceController.getStatusBar().setMode(controller.getStatusBar().getMode());
                newChart.autoScale();
                double lvl = newChart.getDatasetAttributes().get(0).getLvl();
                double offset = newChart.getDatasetAttributes().get(0).getOffset();
                int iAttr = 0;
                for (DatasetAttributes dataAttr : newChart.getDatasetAttributes()) {
                    dataAttr.setLvl(lvl);
                    dataAttr.setOffset(offset);
                    dataAttr.setPosColor(datasetAttributesList.get(iAttr).getPosColor());
                    iAttr++;
                }
                newChart.refresh();
            }
        }

    }

    public void drawSlice(GraphicsContextInterface gC, int iCross, int iOrient) {
        if (gC == null) {
            return;
        }
        DatasetBase dataset = getDataset();
        if (dataset == null) {
            return;
        }
        int nDim = dataset.getNDim();
        Bounds bounds = plotBackground.getBoundsInParent();
        boolean xOn = false;
        boolean yOn = false;
        if (controller.sliceStatus.get() && sliceStatus.get()) {
            xOn = true;
            yOn = true;
        }
        int drawPivotAxis = -1;
        if (controller.isPhaseSliderVisible()) {
            if ((phaseAxis == 0) || (nDim == 1)) {
                yOn = false;
                drawPivotAxis = 0;
            } else {
                xOn = false;
                drawPivotAxis = 1;
            }
        }
        if ((nDim > 1) && controller.sliceStatus.get() && sliceStatus.get()) {
            if (((iOrient == HORIZONTAL) && xOn) || ((iOrient == VERTICAL) && yOn)) {
                for (DatasetAttributes datasetAttributes : datasetAttributesList) {
                    if (datasetAttributes.getDataset().getNDim() > 1) {
                        if (iOrient == HORIZONTAL) {
                            drawSpectrum.drawSlice(datasetAttributes, sliceAttributes, HORIZONTAL, crossHairPositions[iCross][VERTICAL], crossHairPositions[iCross][HORIZONTAL], bounds, getPh0(0), getPh1(0));
                        } else {
                            drawSpectrum.drawSlice(datasetAttributes, sliceAttributes, VERTICAL, crossHairPositions[iCross][VERTICAL], crossHairPositions[iCross][HORIZONTAL], bounds, getPh0(1), getPh1(1));
                        }
                        double[][] xy = drawSpectrum.getXY();
                        int nPoints = drawSpectrum.getNPoints();
                        if (sliceAttributes.useDatasetColorProperty().get()) {
                            gC.setStroke(datasetAttributes.getPosColor());
                        } else {
                            if (iCross == 0) {
                                gC.setStroke(sliceAttributes.getSlice1Color());
                            } else {
                                gC.setStroke(sliceAttributes.getSlice2Color());
                            }
                        }
                        gC.strokePolyline(xy[0], xy[1], nPoints);
                    }
                }
            }
        }
        if (drawPivotAxis == 0) {
            int dataDim = datasetAttributesList.get(0).dim[0];
            if (pivotPosition[dataDim] != null) {
                double dispPos = axes[0].getDisplayPosition(pivotPosition[dataDim]);
                if ((dispPos > 1) && (dispPos < leftBorder + axes[0].getWidth())) {
                    gC.setStroke(Color.GREEN);
                    gC.strokeLine(dispPos - 10, topBorder, dispPos, topBorder + 20);
                    gC.strokeLine(dispPos + 10, topBorder, dispPos, topBorder + 20);
                    gC.strokeLine(dispPos, topBorder + axes[1].getHeight() - 20, dispPos - 10, topBorder + axes[1].getHeight());
                    gC.strokeLine(dispPos, topBorder + axes[1].getHeight() - 20, dispPos + 10, topBorder + axes[1].getHeight());
                }
            }

        } else if (drawPivotAxis == 1) {
            int dataDim = datasetAttributesList.get(0).dim[1];
            if (pivotPosition[dataDim] != null) {
                double dispPos = axes[1].getDisplayPosition(pivotPosition[dataDim]);
                if ((dispPos > 1) && (dispPos < topBorder + axes[1].getHeight())) {
                    gC.setStroke(Color.GREEN);
                    gC.strokeLine(leftBorder, dispPos - 10, leftBorder + 20, dispPos);
                    gC.strokeLine(leftBorder, dispPos + 10, leftBorder + 20, dispPos);
                    gC.strokeLine(leftBorder + axes[0].getWidth(), dispPos + 10, leftBorder + axes[0].getWidth() - 20, dispPos);
                    gC.strokeLine(leftBorder + axes[0].getWidth(), dispPos - 10, leftBorder + axes[0].getWidth() - 20, dispPos);
                }
            }
        }
    }

    public void gotoMaxPlane() {
        DatasetBase dataset = getDataset();
        if (dataset != null) {
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);

            int nDim = dataset.getNDim();
            double cross1x = crossHairPositions[0][VERTICAL];
            double cross1y = crossHairPositions[0][HORIZONTAL];
            if (nDim == 3) {
                int[][] pt = new int[nDim][2];
                int[] cpt = new int[nDim];
                int[] dim = new int[nDim];
                double[] regionWidth = new double[nDim];
                for (int i = 0; i < nDim; i++) {
                    dim[i] = datasetAttributes.getDim(i);
                    switch (i) {
                        case 0:
                            pt[0][0] = axModes[0].getIndex(datasetAttributes, 0, cross1x);
                            pt[0][1] = pt[0][0];
                            break;
                        case 1:
                            pt[1][0] = axModes[1].getIndex(datasetAttributes, 1, cross1y);
                            pt[1][1] = pt[1][0];
                            break;
                        default:
                            pt[i][0] = axModes[i].getIndex(datasetAttributes, i, axes[i].getLowerBound());
                            pt[i][1] = axModes[i].getIndex(datasetAttributes, i, axes[i].getUpperBound());
                            if (pt[i][0] > pt[i][1]) {
                                int hold = pt[i][0];
                                pt[i][0] = pt[i][1];
                                pt[i][1] = hold;
                            }
                            break;
                    }
                    cpt[i] = (pt[i][0] + pt[i][1]) / 2;
                    regionWidth[i] = (double) Math.abs(pt[i][0] - pt[i][1]);
                }
                RegionData rData;
                try {
                    rData = dataset.analyzeRegion(pt, cpt, regionWidth, dim);
                } catch (IOException ioE) {
                    return;
                }
                int[] maxPoint = rData.getMaxPoint();
                double planeValue = axModes[2].indexToValue(datasetAttributes, 2, maxPoint[2]);
                log.info("{} {} {}", rData.getMax(), maxPoint[2], planeValue);
                axes[2].setLowerBound(planeValue);
                axes[2].setUpperBound(planeValue);

            }
        }
    }

    public CrossHairs getCrossHairs() {
        return crossHairs;

    }

    class UpdateCrossHair implements java.util.function.DoubleFunction {

        final int crossHairNum;
        final int orientation;

        public UpdateCrossHair(int crossHairNum, int orientation) {
            this.crossHairNum = crossHairNum;
            this.orientation = orientation;
        }

        @Override
        public Object apply(double value) {
            if (crossHairs.getCrossHairState(crossHairNum, orientation)) {

            }
            crossHairPositions[crossHairNum][orientation] = value;
            crossHairs.refreshCrossHairs();

            return null;
        }

    }

    DoubleFunction getCrossHairUpdateFunction(int crossHairNum, int orientation) {
        UpdateCrossHair function = new UpdateCrossHair(crossHairNum, orientation);
        return function;
    }

    public void printSpectrum() throws IOException {
        DatasetBase dataset = getDataset();
        if (dataset == null) {
            return;
        }
        if (is1D()) {
            if (dataset.getVec() == null) {
                return;
            }
            VecBase vec = dataset.getVec();
            VecBase[] vecs = {vec};
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
            try {
                SpectrumWriter.printVec(datasetAttributes, vecs, axes, axModes);
            } catch (GraphicsIOException ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        } else {
            try {
                SpectrumWriter.printNDSpectrum(drawSpectrum);
            } catch (GraphicsIOException ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        }
    }

    public VecBase getVec() {
        DatasetBase dataset = getDataset();
        if (dataset == null) {
            return null;
        } else {
            return dataset.getVec();
        }
    }

    public void addSync(String name, int group) {
        if (getDimNames().contains(name)) {
            syncGroups.put(name, group);
        }
    }

    public void addSync(int iDim, int group) {
        if (iDim < getDimNames().size()) {
            String label = getDimNames().get(iDim);
            syncGroups.put(label, group);
        }
    }

    public int getSyncGroup(String name) {
        Integer result = syncGroups.get(name);
        return result == null ? 0 : result;
    }

    public int getSyncGroup(int iDim) {
        if (iDim < getDimNames().size()) {
            String label = getDimNames().get(iDim);
            return getSyncGroup(label);
        }
        return 0;
    }

    public static int getNSyncGroups() {
        return nSyncGroups;
    }

    public void syncSceneMates() {
        Map<String, Integer> syncMap = new HashMap<>();
        // get sync names for this chart
        for (String name : getDimNames()) {
            int iSync = getSyncGroup(name);
            if (iSync != 0) {
                syncMap.put(name, iSync);
            }
        }
        // add sync names from other charts if not already added
        for (PolyChart chart : getSceneMates(false)) {
            chart.getDimNames().stream().forEach((obj) -> {
                String name = (String) obj;
                int iSync = chart.getSyncGroup(name);
                if (iSync != 0) {
                    if (!syncMap.containsKey(name)) {
                        syncMap.put(name, iSync);
                    }
                }
            });
        }
        // now add new group for any missing names
        for (String name : getDimNames()) {
            if (!syncMap.containsKey(name)) {
                nSyncGroups++;
                syncMap.put(name, nSyncGroups);
            }
            addSync(name, syncMap.get(name));
        }
        for (PolyChart chart : getSceneMates(false)) {
            for (String name : getDimNames()) {
                if (chart.getDimNames().contains(name)) {
                    chart.addSync(name, getSyncGroup(name));
                }
            }
        }
    }

    List<PolyChart> getSceneMates(boolean includeThis) {
        List<PolyChart> sceneMates = new ArrayList<>();
        for (PolyChart chart : CHARTS) {
            if (chart.canvas == canvas) {
                if (includeThis || (chart != this)) {
                    sceneMates.add(chart);
                }
            }
        }
        return sceneMates;
    }

    public void adjustLabels() {
        if (!datasetAttributesList.isEmpty()) {
            String[] dimNames;
            if (is1D()) {
                dimNames = new String[1];
                dimNames[0] = datasetAttributesList.get(0).getLabel(0);
            } else {
                dimNames = new String[2];
                dimNames[0] = datasetAttributesList.get(0).getLabel(0);
                dimNames[1] = datasetAttributesList.get(0).getLabel(1);
            }
            for (PeakListAttributes peakAttr : peakListAttributesList) {
                PeakList peakList = peakAttr.getPeakList();
                // double[] limits = {0.1, 0.8};
                int nCells = 25;
                try {
                    PeakNeighbors peakNeighbors = new PeakNeighbors(peakList, nCells, dimNames);
                    peakNeighbors.optimizePeakLabelPositions();
                } catch (IllegalArgumentException iAE) {
                    ExceptionDialog dialog = new ExceptionDialog(iAE);
                    dialog.showAndWait();
                }
            }
            if (!peakListAttributesList.isEmpty()) {
                refresh();
            }
        }

    }

    protected Optional<RegionData> analyzeFirst() {
        Optional<RegionData> result = Optional.empty();
        if (!datasetAttributesList.isEmpty()) {
            DatasetAttributes dataAttr = datasetAttributesList.get(0);
            RegionData rData = analyze(dataAttr);
            result = Optional.of(rData);
        }
        return result;
    }

    protected RegionData analyze(DatasetAttributes dataAttr) {
        DatasetBase dataset = dataAttr.getDataset();
        double max = Double.NEGATIVE_INFINITY;
        double min = Double.MAX_VALUE;

        int nDim = dataset.getNDim();
        int[][] pt = new int[nDim][2];
        int[] cpt = new int[nDim];
        int[] dim = new int[nDim];
        double[] regionWidth = new double[nDim];
        for (int iDim = 0; iDim < nDim; iDim++) {
            int[] limits = new int[2];
            if (iDim < 2) {
                int orientation = iDim == 0 ? PolyChart.VERTICAL : PolyChart.HORIZONTAL;
                limits[0] = axModes[iDim].getIndex(dataAttr, iDim, crossHairPositions[0][orientation]);
                limits[1] = axModes[iDim].getIndex(dataAttr, iDim, crossHairPositions[1][orientation]);
            } else {
                limits[0] = axModes[iDim].getIndex(dataAttr, iDim, axes[iDim].getLowerBound());
                limits[1] = axModes[iDim].getIndex(dataAttr, iDim, axes[iDim].getUpperBound());
            }

            if (limits[0] < limits[1]) {
                pt[iDim][0] = limits[0];
                pt[iDim][1] = limits[1];
            } else {
                pt[iDim][0] = limits[1];
                pt[iDim][1] = limits[0];
            }
            dim[iDim] = dataAttr.dim[iDim];
            cpt[iDim] = (pt[iDim][0] + pt[iDim][1]) / 2;
            regionWidth[iDim] = (double) Math.abs(pt[iDim][0] - pt[iDim][1]);
        }
        RegionData rData = null;
        try {
            rData = dataset.analyzeRegion(pt, cpt, regionWidth, dim);
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
        }
        return rData;
    }

    protected double[] getPercentile(DatasetAttributes dataAttr, double p) throws IOException {
        DatasetBase dataset = dataAttr.getDataset();

        int nDim = dataset.getNDim();
        int[][] pt = new int[nDim][2];
        int[] dim = new int[nDim];
        for (int iDim = 0; iDim < nDim; iDim++) {
            int[] limits = getPlotLimits(dataAttr, iDim);

            if (limits[0] < limits[1]) {
                pt[iDim][0] = limits[0];
                pt[iDim][1] = limits[1];
            } else {
                pt[iDim][0] = limits[1];
                pt[iDim][1] = limits[0];
            }
            dim[iDim] = dataAttr.dim[iDim];
        }
        double[] value = dataset instanceof Dataset ? ((Dataset) dataset).getPercentile(p, pt, dim) : null;
        return value;
    }

    public void config(String name, Object value) {
        chartProps.config(name, value);
    }

    public Map<String, Object> config() {
        return chartProps.config();
    }

    public static void registerPeakDeleteAction(Consumer<PeakDeleteEvent> func) {
        manualPeakDeleteAction = func;
    }

    public ProcessorController getProcessorController(boolean createIfNull) {
        if ((processorController == null) && createIfNull) {
            processorController = ProcessorController.create(getFXMLController(), getFXMLController().getProcessorPane(), this);
        }
        return processorController;
    }

    public Object getPopoverTool(String className) {
        return popoverMap.get(className);
    }

    public void setPopoverTool(String name, Object object) {
        popoverMap.put(name, object);
    }

    public void clearPopoverTools() {
        popoverMap.clear();
    }

    /**
     * Checks the list of dataset attributes for projections and sets the default projection border size for each
     * border with a projection.
     */
    public void updateProjectionBorders() {
        List<Integer> projections = getDatasetAttributes().stream().map(DatasetAttributes::projection).filter(projection -> projection >= 0).toList();
        if (projections.contains(0)) {
            chartProps.setTopBorderSize(ChartProperties.PROJECTION_BORDER_DEFAULT_SIZE);
        }
        if (projections.contains(1)) {
            chartProps.setRightBorderSize(ChartProperties.PROJECTION_BORDER_DEFAULT_SIZE);
        }
    }

    /**
     * Update the initial scale value for the projections to fit the highest peak to 95% of the available height.
     * If there are two projections, then the scale for the projection with the higher peak is used for both.
     */
    public void updateProjectionScale() {
        Optional<DatasetAttributes> initialDatasetAttr = getFirstDatasetAttributes();
        if (initialDatasetAttr.isPresent()) {
            Vec projectionVec = new Vec(32, false);
            try {
                List<Integer> borders = Arrays.asList(chartProps.getTopBorderSize(), chartProps.getRightBorderSize());
                for (int i = 0; i < borders.size(); i++) {
                    int projectionDim = i;
                    Optional<DatasetAttributes> projectionDimAttr = getDatasetAttributes().stream().filter(attr -> attr.projection() == projectionDim).findFirst();
                    if (projectionDimAttr.isPresent()) {
                        initialDatasetAttr.get().getProjection((Dataset) projectionDimAttr.get().getDataset(), projectionVec, projectionDim);
                        OptionalDouble maxValue = Arrays.stream(projectionVec.rvec).max();
                        if (maxValue.isPresent()) {
                            double scaleValue = (borders.get(i) * 0.95) / maxValue.getAsDouble();
                            projectionDimAttr.get().setProjectionScale(scaleValue);
                        }
                    }
                }
            } catch (IOException e) {
                log.warn("Unable to update projection scale. {}",e.getMessage(), e);
            }
        }
    }

    /**
     * Updates the projection scale value by adding the scaleDelta value for the provided chart border.
     * @param chartBorder Which chart border to adjust the scale for
     * @param scaleDelta The amount to adjust the scale
     */
    public void updateProjectionScale(ChartBorder chartBorder, double scaleDelta) {
        if (chartBorder == ChartBorder.TOP) {
            Optional<DatasetAttributes> projectionAttr = getDatasetAttributes().stream().filter(attr -> attr.projection() == 0).findFirst();
            projectionAttr.ifPresent(datasetAttributes -> datasetAttributes.setProjectionScale(Math.max(0, datasetAttributes.getProjectionScale() * (1 + scaleDelta))));
        } else if (chartBorder == ChartBorder.RIGHT) {
            Optional<DatasetAttributes> projectionAttr = getDatasetAttributes().stream().filter(attr -> attr.projection() == 1).findFirst();
            projectionAttr.ifPresent(datasetAttributes -> datasetAttributes.setProjectionScale(Math.max(0, datasetAttributes.getProjectionScale() * (1 + scaleDelta))));}
    }

    /**
     * Remove all datasetAttributes that are projections, reset the chart borders back to the empty default
     * and refresh the chart.
     */
    public void removeProjections() {
        getDatasetAttributes().removeIf(datasetAttributes -> datasetAttributes.projection() != -1);
        chartProps.setTopBorderSize(ChartProperties.EMPTY_BORDER_DEFAULT_SIZE);
        chartProps.setRightBorderSize(ChartProperties.EMPTY_BORDER_DEFAULT_SIZE);
        refresh();
    }
}
