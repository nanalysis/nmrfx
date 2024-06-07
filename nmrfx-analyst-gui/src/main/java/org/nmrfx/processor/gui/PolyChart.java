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
import javafx.geometry.*;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.codehaus.commons.nullanalysis.Nullable;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chart.Axis;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.datasets.RegionData;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.graphicsio.GraphicsContextProxy;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.peaks.Multiplet;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.events.PeakEvent;
import org.nmrfx.peaks.events.PeakListener;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.*;
import org.nmrfx.processor.gui.annotations.AnnoText;
import org.nmrfx.processor.gui.spectra.*;
import org.nmrfx.processor.gui.spectra.DatasetAttributes.AXMODE;
import org.nmrfx.processor.gui.spectra.crosshair.CrossHairs;
import org.nmrfx.processor.gui.spectra.mousehandlers.MouseBindings;
import org.nmrfx.processor.gui.spectra.mousehandlers.MouseBindings.MOUSE_ACTION;
import org.nmrfx.processor.gui.undo.*;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingSection;
import org.nmrfx.processor.project.ProjectText;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.utils.GUIUtils;
import org.nmrfx.utils.properties.FileProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.stream.Collectors;

import static org.nmrfx.processor.gui.PolyChart.DISDIM.TwoD;
import static org.nmrfx.processor.gui.utils.GUIColorUtils.toBlackOrWhite;

@PluginAPI("parametric")
public class PolyChart extends Region {
    public static final int HORIZONTAL = 0;
    private static final Logger log = LoggerFactory.getLogger(PolyChart.class);
    private static final int VERTICAL = 1;
    private static final double OVERLAP_SCALE = 3.0;
    private static final double MIN_MOVE = 20;
    private static final String FONT_FAMILY = "Liberation Sans";
    private static boolean listenToPeaks = true;
    private static Consumer<PeakDeleteEvent> manualPeakDeleteAction = null;

    private final ObservableList<DatasetAttributes> datasetAttributesList = FXCollections.observableArrayList();
    private final ObservableList<PeakListAttributes> peakListAttributesList = FXCollections.observableArrayList();
    private final ObservableSet<MultipletSelection> selectedMultiplets = FXCollections.observableSet();
    private final ObjectProperty<DISDIM> disDimProp = new SimpleObjectProperty<>(TwoD);
    private final PeakListener peakListener = this::peakListChanged;
    private final FXMLController controller;
    private final ChartDrawingLayers drawingLayers;
    private final Path bcPath = new Path();
    private final Rectangle highlightRect = new Rectangle();
    private final DrawSpectrum drawSpectrum;
    private final DrawPeaks drawPeaks;
    private final List<CanvasAnnotation> canvasAnnotations = new ArrayList<>();
    private final String name;
    private final SimpleObjectProperty<DatasetRegion> activeRegion = new SimpleObjectProperty<>(null);
    private final Map<String, Object> popoverMap = new HashMap<>();
    private final FileProperty datasetFileProp = new FileProperty();
    private final BooleanProperty sliceStatus = new SimpleBooleanProperty(true);
    // fixme 15 should be set automatically and correctly
    private final double[][] chartPhases = new double[2][15];
    private final Double[] pivotPosition = new Double[15];
    private final List<ConnectPeakAttributes> peakPaths = new ArrayList<>();
    private final SliceAttributes sliceAttributes = new SliceAttributes();
    private final ChartProperties chartProps = new ChartProperties(this);
    private final PolyChartAxes axes = new PolyChartAxes(datasetAttributesList);
    private ProcessorController processorController = null;
    private DatasetAttributes lastDatasetAttr = null;
    private AnnoText parameterText = null;
    private double minLeftBorder = 0.0;
    private double minBottomBorder = 0.0;
    private Insets borders = Insets.EMPTY;
    private double stackWidth = 0.0;
    private Font peakFont = new Font(FONT_FAMILY, 12);
    private boolean disabled = false;
    private FXMLController sliceController = null;
    private int phaseAxis = PolyChartAxes.X_INDEX;
    private int datasetPhaseDim = 0;
    private double phaseFraction = 0.0;
    private boolean useImmediateMode = true;
    private boolean lockAnno = false;
    private Consumer<DatasetRegion> onRegionAdded = null;
    private ChartMenu spectrumMenu;
    private ChartMenu peakMenu;
    private ChartMenu integralMenu;
    private ChartMenu regionMenu;
    private KeyBindings keyBindings;
    private MouseBindings mouseBindings;
    private GestureBindings gestureBindings;
    private CrossHairs crossHairs;
    private List<ChartUndo> undos = new ArrayList<>();
    private List<ChartUndo> redos = new ArrayList<>();
    private  MapChangeListener<String, PeakList> peakListMapChangeListener = change -> purgeInvalidPeakListAttributes();

    protected PolyChart(FXMLController controller, String name, ChartDrawingLayers drawingLayers) {
        this.controller = controller;
        this.name = name;
        this.drawingLayers = drawingLayers;
        drawSpectrum = new DrawSpectrum(axes, drawingLayers.getGraphicsProxyFor(ChartDrawingLayers.Item.Spectrum));
        drawSpectrum.setupHaltButton(controller.getHaltButton());

        initChart();
        drawPeaks = new DrawPeaks(this, drawingLayers.getGraphicsContextFor(ChartDrawingLayers.Item.Peaks));
        setVisible(false);
    }

    /**
     * Called by PeakSlider to prevent handling peak events during peak alignment
     *
     * @param state true to enable peak listener, false to disable it
     */
    public static void setPeakListenerState(boolean state) {
        listenToPeaks = state;
    }

    public static void registerPeakDeleteAction(Consumer<PeakDeleteEvent> func) {
        manualPeakDeleteAction = func;
    }

    public ChartProperties getChartProperties() {
        return chartProps;
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

    public void updateSelectedMultiplets() {
        selectedMultiplets.clear();
        for (PeakListAttributes peakAttr : peakListAttributesList) {
            selectedMultiplets.addAll(peakAttr.getSelectedMultiplets());
        }
    }

    public void addMultipletListener(SetChangeListener<MultipletSelection> listener) {
        selectedMultiplets.addListener(listener);
    }

    private void peakListChanged(PeakEvent peakEvent) {
        if (!listenToPeaks) {
            return;
        }

        Fx.runOnFxThread(() -> {
            // if mouse down we could be dragging peak which will itself cause redraw
            //   no need to call this
            if (mouseBindings.isMouseDown()) {
                return;
            }
            Object source = peakEvent.getSource();
            PeakListAttributes activeAttr = null;
            if (source instanceof PeakList peakList) {
                for (PeakListAttributes peakListAttr : peakListAttributesList) {
                    if (peakListAttr.getPeakList() == peakList) {
                        activeAttr = peakListAttr;
                    }
                }
            }
            if (activeAttr != null) {
                drawPeakLists(false);
                drawSelectedPeaks(activeAttr);
            }
        });
    }

    public ObjectProperty<DISDIM> getDisDimProperty() {
        return disDimProp;
    }

    private void initChart() {
        useImmediateMode = PreferencesController.getUseImmediateMode();
        crossHairs = new CrossHairs(this);
        drawingLayers.getTopPane().getChildren().addAll(crossHairs.getAllGraphicalLines());

        highlightRect.setVisible(false);
        highlightRect.setStroke(Color.BLUE);
        highlightRect.setStrokeWidth(1.0);
        highlightRect.setFill(null);
        highlightRect.visibleProperty().bind(
                PolyChartManager.getInstance().activeChartProperty().isEqualTo(this)
                        .and(PolyChartManager.getInstance().multipleChartsProperty()));
        drawingLayers.getTopPane().getChildren().add(highlightRect);
        axes.init(this);
        drawingLayers.setCursor(CanvasCursor.SELECTOR.getCursor());
        ProjectBase.getActive().addPeakListListener(new WeakMapChangeListener<>(peakListMapChangeListener));
        keyBindings = new KeyBindings(this);
        mouseBindings = new MouseBindings(this);
        gestureBindings = new GestureBindings(this);
        spectrumMenu = new SpectrumMenu(this);
        peakMenu = new PeakMenu(this);
        regionMenu = new RegionMenu(this);
        integralMenu = new IntegralMenu(this);
        drawingLayers.requestFocus();
        ProjectBase.getActive().projectChanged(true);
    }

    public String getName() {
        return name;
    }

    public Canvas getCanvas() {
        return drawingLayers.getBaseCanvas();
    }

    public PolyChartAxes getAxes() {
        return axes;
    }

    public void close() {
        drawingLayers.getTopPane().getChildren().removeAll(crossHairs.getAllGraphicalLines());

        highlightRect.visibleProperty().unbind();
        drawingLayers.getTopPane().getChildren().remove(highlightRect);

        PolyChartManager.getInstance().unregisterChart(this);
        drawSpectrum.clearThreads();
    }

    public void focus() {
        getFXMLController().getStage().requestFocus();
    }

    public Cursor getCanvasCursor() {
        return drawingLayers.getCursor();
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
        return spectrumMenu;
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

    public SliceAttributes getSliceAttributes() {
        return sliceAttributes;
    }

    public FXMLController getFXMLController() {
        return controller;
    }

    public void dragBox(MOUSE_ACTION mouseAction, double[] dragStart, double x, double y) {
        int dragTol = 4;
        if ((Math.abs(x - dragStart[0]) > dragTol) || (Math.abs(y - dragStart[1]) > dragTol)) {
            GraphicsContext gc = drawingLayers.getGraphicsContextFor(ChartDrawingLayers.Item.DragBoxes);
            double annoWidth = drawingLayers.getWidth();
            double annoHeight = drawingLayers.getHeight();
            gc.clearRect(0, 0, annoWidth, annoHeight);
            double dX = Math.abs(x - dragStart[0]);
            double dY = Math.abs(y - dragStart[1]);
            double startX = Math.min(x, dragStart[0]);
            double startY = Math.min(y, dragStart[1]);
            double yPos = getLayoutY();
            gc.setLineDashes(null);
            if (mouseAction == MOUSE_ACTION.DRAG_EXPAND || mouseAction == MOUSE_ACTION.DRAG_ADDREGION || mouseAction == MOUSE_ACTION.DRAG_PEAKPICK) {
                if ((dX < MIN_MOVE) || (!is1D() && (dY < MIN_MOVE))) {
                    gc.setLineDashes(5);
                }
            }
            Color color;
            if (null == mouseAction) {
                color = Color.DARKORANGE;
            } else {
                color = switch (mouseAction) {
                    case DRAG_EXPAND -> Color.DARKBLUE;
                    case DRAG_ADDREGION -> Color.GREEN;
                    default -> Color.DARKORANGE;
                };
            }
            gc.setStroke(color);
            if (is1D()) {
                if (mouseAction == MOUSE_ACTION.DRAG_PEAKPICK) {
                    gc.strokeLine(x, y - 20, x, y + 20);
                    gc.strokeLine(dragStart[0], y - 20, dragStart[0], y + 20);
                    gc.strokeLine(dragStart[0], y, x, y);
                } else {
                    gc.strokeLine(x, yPos + borders.getTop(), x, yPos + getHeight() - borders.getBottom());
                    gc.strokeLine(dragStart[0], yPos + borders.getTop(), dragStart[0], yPos + getHeight() - borders.getBottom());
                }
            } else {
                gc.strokeRect(startX, startY, dX, dY);
            }
            gc.setLineDashes(null);
        }
    }

    private void swapDouble(double[] values) {
        if (values[0] > values[1]) {
            double hold = values[0];
            values[0] = values[1];
            values[1] = hold;
        }
    }

    public void setOnRegionAdded(Consumer<DatasetRegion> consumer) {
        onRegionAdded = consumer;
    }

    private void addRegion(double min, double max) {
        DatasetBase dataset = getDataset();
        if (dataset != null) {
            if (getFXMLController().isScannerToolPresent()) {
                double[] ppms = {min, max};
                getFXMLController().scannerTool.measure(ppms);
            } else {
                DatasetRegion newRegion = dataset.addRegion(min, max);
                try {
                    newRegion.measure(dataset);
                } catch (IOException ex) {
                    log.error(ex.getMessage(), ex);
                }
                chartProps.setRegions(true);
                chartProps.setIntegrals(true);
                if (onRegionAdded != null) {
                    onRegionAdded.accept(newRegion);
                }
            }
        }
    }

    public boolean finishBox(MOUSE_ACTION mouseAction, double[] dragStart, double x, double y) {
        GraphicsContext gc = drawingLayers.getGraphicsContextFor(ChartDrawingLayers.Item.DragBoxes);
        double annoWidth = drawingLayers.getWidth();
        double annoHeight = drawingLayers.getHeight();
        gc.clearRect(0, 0, annoWidth, annoHeight);
        double[][] limits;
        if (is1D()) {
            limits = new double[1][2];
        } else {
            limits = new double[2][2];
        }
        boolean completed = false;
        double dX = Math.abs(x - dragStart[0]);
        double dY = Math.abs(y - dragStart[1]);
        limits[0][0] = axes.getX().getValueForDisplay(dragStart[0]).doubleValue();
        limits[0][1] = axes.getX().getValueForDisplay(x).doubleValue();
        swapDouble(limits[0]);
        if (!is1D()) {
            limits[1][0] = axes.getY().getValueForDisplay(y).doubleValue();
            limits[1][1] = axes.getY().getValueForDisplay(dragStart[1]).doubleValue();
            swapDouble(limits[1]);
        }

        if (mouseAction == MOUSE_ACTION.DRAG_EXPAND) {
            if (dX > MIN_MOVE) {
                if (is1D() || (dY > MIN_MOVE)) {

                    ChartUndoLimits undo = new ChartUndoLimits(this);
                    double[] adjustedLimits = getAxes().getRangeMinimalAdjustment(0, limits[0][0], limits[0][1]);
                    axes.setMinMax(0, adjustedLimits[0], adjustedLimits[1]);
                    if (!is1D()) {
                        adjustedLimits = getAxes().getRangeMinimalAdjustment(1, limits[1][0], limits[1][1]);
                        axes.setMinMax(1, adjustedLimits[0], adjustedLimits[1]);
                    }
                    ChartUndoLimits redo = new ChartUndoLimits(this);
                    controller.getUndoManager().add("expand", undo, redo);
                    refresh();
                    completed = true;
                }
            }
        } else if (mouseAction == MOUSE_ACTION.DRAG_ADDREGION) {
            if (dX > MIN_MOVE) {
                if (is1D()) {
                    addRegion(limits[0][0], limits[0][1]);
                    refresh();
                    completed = true;
                }
            }
        } else if (mouseAction == MOUSE_ACTION.DRAG_PEAKPICK) {
            // nothing
        } else {
            drawPeakLists(false);
            for (PeakListAttributes peakAttr : peakListAttributesList) {
                peakAttr.selectPeaksInRegion(limits);
                drawSelectedPeaks(peakAttr);
            }
            if (controller == AnalystApp.getFXMLControllerManager().getOrCreateActiveController()) {
                List<Peak> allSelPeaks = new ArrayList<>();
                for (PolyChart chart : controller.getCharts()) {
                    allSelPeaks.addAll(chart.getSelectedPeaks());
                }
                controller.selectedPeaksProperty().set(allSelPeaks);
            }
            completed = true;

        }
        return completed;
    }

    public ProcessorController getProcessorController() {
        return processorController;
    }

    public void setProcessorController(ProcessorController controller) {
        this.processorController = controller;
    }

    public Optional<DatasetAttributes> getFirstDatasetAttributes() {
        if (datasetAttributesList.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(datasetAttributesList.get(0));
    }

    @Nullable
    public DatasetBase getDataset() {
        return getFirstDatasetAttributes().map(DatasetAttributes::getDataset).orElse(null);
    }

    public void setDataset(DatasetBase dataset) {
        setDataset(dataset, false, false);
    }

    @Nullable
    public File getDatasetFile() {
        DatasetBase dataset = getDataset();
        return dataset != null ? dataset.getFile() : null;
    }

    void removeAllDatasets() {
        if (!datasetAttributesList.isEmpty()) {
            lastDatasetAttr = (DatasetAttributes) datasetAttributesList.get(0).clone();
        }
        datasetAttributesList.clear();
    }

    public void zoom(double factor) {
        Fx.runOnFxThread(() -> {
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
                controller.getUndoManager().add(undoName, undo, redo);
            }
        });
    }

    protected void xZoom(double factor) {
        double min = axes.getX().getLowerBound();
        double max = axes.getX().getUpperBound();
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
        double[] limits = getAxes().getRange(0, min, max);
        axes.getX().setMinMax(limits[0], limits[1]);
    }

    public void swapView() {
        if (!is1D()) {
            double minX = axes.getX().getLowerBound();
            double maxX = axes.getX().getUpperBound();
            double minY = axes.getY().getLowerBound();
            double maxY = axes.getY().getUpperBound();
            axes.getX().setMinMax(minY, maxY);
            axes.getY().setMinMax(minX, maxX);
            refresh();
        }
    }

    public void popView() {
        FXMLController newController = AnalystApp.getFXMLControllerManager().newController();
        PolyChart newChart = newController.getActiveChart();
        copyTo(newChart);
        newController.getStatusBar().setMode(controller.getStatusBar().getMode(), controller.getStatusBar().getModeDimensions());
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
        axes.copyTo(newChart.axes);
        newChart.refresh();
    }

    public void scroll(double x, double y) {
        scrollXAxis(x);
        scrollYAxis(y);
        layoutPlotChildren();
    }

    protected void scrollXAxis(double x) {
        double scale = axes.getX().getScale();
        if (axes.getMode(0) == AXMODE.PPM) {
            scale *= -1.0;
        }
        double min = axes.getX().getLowerBound();
        double max = axes.getX().getUpperBound();
        double range = max - min;
        double center = (max + min) / 2.0;
        center -= x / scale;
        // fixme add check for too small range
        min = center - range / 2.0;
        max = center + range / 2.0;
        double[] limits = getAxes().getLimits(0, min, max);

        axes.getX().setMinMax(limits[0], limits[1]);
    }

    protected void scrollYAxis(double y) {
        double scale = axes.getY().getScale();
        double min = axes.getY().getLowerBound();
        double max = axes.getY().getUpperBound();
        double range = max - min;
        double center = (max + min) / 2.0;

        if (is1D()) {
            if (!datasetAttributesList.isEmpty()) {
                datasetAttributesList.forEach(dataAttr -> {
                    double fOffset = dataAttr.getOffset();
                    fOffset -= y / getHeight();
                    dataAttr.setOffset(fOffset);
                });
                axes.setYAxisByLevel();
            }
        } else {
            center -= y / scale;
            min = center - range / 2.0;
            max = center + range / 2.0;
            double[] limits = getAxes().getLimits(1, min, max);
            axes.getY().setMinMax(limits[0], limits[1]);
        }
    }

    protected void adjustScale(double factor) {
        ChartUndoScale undo = new ChartUndoScale(this);
        datasetAttributesList.stream().filter(dataAttr -> !dataAttr.isProjection())
                .forEach(dataAttr -> adjustScale(dataAttr, factor));
        layoutPlotChildren();
        ChartUndoScale redo = new ChartUndoScale(this);
        controller.getUndoManager().add("ascale", undo, redo);
    }

    protected void adjustScale(DatasetAttributes dataAttr, double factor) {
        DatasetBase dataset = dataAttr.getDataset();
        if (is1D()) {
            double oldLevel = dataAttr.getLvl();
            double newLevel = oldLevel * factor;
            dataAttr.setLvl(newLevel);
            axes.setYAxisByLevel();
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

    /**
     * Calculates a scaling factor for the y-axis based on a deltaY change. The scaling factor has a range between
     * 0.5 and 2.0
     *
     * @param deltaY A double value of change in the y direction
     * @return A scaling factor between 0.5 and 2
     */
    public double calculateScaleYFactor(double deltaY) {
        double factor = (deltaY / 200.0 + 1.0);
        if (factor > 2.0) {
            factor = 2.0;
        } else if (factor < 0.5) {
            factor = 0.5;
        }
        return factor;
    }

    public void scaleY(double y) {
        final double scale = calculateScaleYFactor(y);
        datasetAttributesList.stream().filter(dataAttr -> !dataAttr.isProjection())
                .forEach(dataAttr -> {
                    DatasetBase dataset = dataAttr.getDataset();
                    if (is1D()) {
                        double oldLevel = dataAttr.getLvl();
                        dataAttr.setLvl(oldLevel * scale);
                        axes.setYAxisByLevel();
                    } else if (dataset != null) {
                        double oldLevel = dataAttr.getLvl();
                        dataAttr.setLvl(oldLevel * scale);
                    }
                });
        layoutPlotChildren();
    }

    protected void yZoom(double factor) {
        double min = axes.getY().getLowerBound();
        double max = axes.getY().getUpperBound();
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
        double[] limits = getAxes().getRange(1, min, max);

        axes.getY().setMinMax(limits[0], limits[1]);

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

    public void incrementRow(int amount) {
        DatasetBase dataset = getDataset();
        if (dataset == null || dataset.getNDim() < 2) {
            return;
        }
        getFirstDatasetAttributes().ifPresent(attr -> attr.incrDrawList(amount));
        layoutPlotChildren();
    }

    public void incrementPlane(int axis, int amount) {
        if (axes.count() > axis) {
            ChartUndoLimits undo = new ChartUndoLimits(controller.getActiveChart());
            getFirstDatasetAttributes().ifPresent(attr -> axes.incrementPlane(axis, attr, amount));
            layoutPlotChildren();
            ChartUndoLimits redo = new ChartUndoLimits(controller.getActiveChart());
            controller.getUndoManager().add("plane", undo, redo);
        }
    }

    public void full() {
        Fx.runOnFxThread(() -> {
            if (!datasetAttributesList.isEmpty()) {
                ChartUndoLimits undo = new ChartUndoLimits(this);
                axes.fullLimits(disDimProp.get());
                ChartUndoLimits redo = new ChartUndoLimits(this);
                controller.getUndoManager().add("full", undo, redo);
                layoutPlotChildren();
            }
        });
    }

    public void full(int axis) {
        if (axes.count() > axis) {
            if (!datasetAttributesList.isEmpty()) {
                double[] limits = getAxes().getRange(axis);
                axes.setMinMax(axis, limits[0], limits[1]);
                updateDatasetAttributeBounds();
                layoutPlotChildren();
            }
        }
    }

    public void center(int axis) {
        if (axes.count() > axis) {
            if (!datasetAttributesList.isEmpty()) {
                double[] limits = getAxes().getRange(axis);
                double center = (limits[0] + limits[1]) / 2.0;
                if (axes.getMode(axis) == AXMODE.PTS) {
                    center = Math.ceil(center);
                }
                axes.setMinMax(axis, center, center);
            }
        }
    }

    public boolean isInView(int axis, double position, double edgeFrac) {
        double lim1 = axes.get(axis).getLowerBound();
        double lim2 = axes.get(axis).getUpperBound();
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
        DatasetAttributes datasetAttributes = getFirstDatasetAttributes().orElse(null);
        if (datasetAttributes == null) {
            return;
        }

        for (int axis = 0; axis < positions.length; axis++) {
            if (positions[axis] != null) {
                if (axis > 1) {
                    if (axes.getMode(axis) == AXMODE.PTS) {
                        int plane = AXMODE.PPM.getIndex(datasetAttributes, axis, positions[axis]);
                        axes.setMinMax(axis, plane, plane);
                    } else {
                        axes.setMinMax(axis, positions[axis], positions[axis]);
                    }
                } else {
                    double lower = axes.get(axis).getLowerBound();
                    double upper = axes.get(axis).getUpperBound();
                    double range = Math.abs(upper - lower);
                    double newLower = positions[axis] - range / 2;
                    double newUpper = positions[axis] + range / 2;
                    double[] bounds = getAxes().getRangeMinimalAdjustment(axis, newLower, newUpper);
                    axes.setMinMax(axis, bounds[0], bounds[1]);
                }
            }
        }
        refresh();
    }

    public void moveTo(Double[] positions, Double[] widths) {
        DatasetAttributes datasetAttributes = getFirstDatasetAttributes().orElse(null);
        if (datasetAttributes == null) {
            return;
        }

        for (int axis = 0; axis < positions.length; axis++) {
            if (positions[axis] != null) {
                if (axis > 1) {
                    if (axes.getMode(axis) == AXMODE.PTS) {
                        int plane = AXMODE.PPM.getIndex(datasetAttributes, axis, positions[axis]);
                        axes.setMinMax(axis, plane, plane);
                    } else {
                        axes.setMinMax(axis, positions[axis], positions[axis]);
                    }
                } else {
                    double range = widths[axis];
                    double newLower = positions[axis] - range / 2;
                    double newUpper = positions[axis] + range / 2;
                    axes.setMinMax(axis, newLower, newUpper);
                }
            }
        }
        refresh();
    }

    public void moveTo(int axis, Double position, Double width) {
        if (position != null) {
            if (axis > 1) {
                axes.setMinMax(axis, position, position);
            } else {
                double range = width;
                double newLower = position - range / 2;
                double newUpper = position + range / 2;
                axes.setMinMax(axis, newLower, newUpper);
            }
        }
    }

    public void firstPlane(int axis) {
        if (axes.count() > axis) {
            if (!datasetAttributesList.isEmpty()) {
                double[] limits = getAxes().getRange(axis);
                int iLim = axes.getMode(axis) == AXMODE.PPM ? 1 : 0;
                axes.setMinMax(axis, limits[iLim], limits[iLim]);
            }
        }
    }

    public void lastPlane(int axis) {
        if (axes.count() > axis) {
            if (!datasetAttributesList.isEmpty()) {
                double[] limits = getAxes().getRange(axis);
                int iLim = axes.getMode(axis) == AXMODE.PPM ? 0 : 1;
                axes.setMinMax(axis, limits[iLim], limits[iLim]);
            }
        }
    }

    protected int[] getPlotLimits(DatasetAttributes datasetAttributes, int iDim) {
        if (axes.count() > iDim) {
            Axis axis = axes.get(iDim);
            if (axis != null) {
                int min = axes.getMode(iDim).getIndex(datasetAttributes, iDim, axis.getLowerBound());
                int max = axes.getMode(iDim).getIndex(datasetAttributes, iDim, axis.getUpperBound());
                if (min > max) {
                    int hold = min;
                    min = max;
                    max = hold;
                }
                return new int[]{min, max};
            }
        }
        return new int[]{0, 0};
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
        datasetAttributesList.forEach(this::autoScale);
        updateProjectionScale();
        layoutPlotChildren();
        ChartUndoScale redo = new ChartUndoScale(this);
        controller.getUndoManager().add("ascale", undo, redo);
    }

    protected void autoScale(DatasetAttributes dataAttr) {
        if (is1D()) {
            DatasetBase dataset = dataAttr.getDataset();

            int nDim = dataset.getNDim();
            int[][] pt = new int[nDim][2];
            int[] cpt = new int[nDim];
            int[] dim = new int[nDim];
            double[] regionWidth = new double[nDim];
            boolean ok = true;
            for (int i = 0; i < nDim; i++) {
                if (dataset.getSizeReal(i) == 0) {
                    ok = false;
                    break;
                }
                dim[i] = i;
                int[] limits = getPlotLimits(dataAttr, i);
                pt[i][0] = i == 0 ? Math.max(0, limits[0]) : 0;
                pt[i][1] = Math.max(0, limits[1]);
                cpt[i] = (pt[i][0] + pt[i][1]) / 2;
                regionWidth[i] = Math.abs(pt[i][0] - pt[i][1]);
            }
            if (!ok) {
                return;
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
            axes.setYAxisByLevel();
        } else {
            DatasetBase datasetBase = dataAttr.getDataset();
            if (datasetBase instanceof Dataset dataset) {
                Double sdev = dataset.guessNoiseLevel();
                double[] percentile;
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
                }
            }
        }
    }

    protected int getPhaseAxis() {
        return phaseAxis;
    }

    public void updatePhaseDim() {
        if ((controller.getChartProcessor() == null) || !controller.isProcessControllerVisible()) {
            setPhaseDim(phaseAxis);
        }
    }

    protected int getPhaseDim() {
        return datasetPhaseDim;
    }

    protected void setPhaseDim(int phaseDim) {
        ProcessingSection section = null;
        if ((controller.getChartProcessor() != null) && controller.isProcessControllerVisible()) {
            section = controller.getChartProcessor().getCurrentProcessingSection();
            datasetPhaseDim = controller.getChartProcessor().mapToDataset(phaseDim);
        } else {
            datasetPhaseDim = getFirstDatasetAttributes().map(attr -> attr.dim[phaseDim]).orElse(phaseDim);
        }

        phaseAxis = PolyChartAxes.X_INDEX;
        if (is1D() || ((section != null) && (section.getFirstDimension() == 0))) {
            phaseAxis = PolyChartAxes.X_INDEX;
        } else if (phaseDim > 0) {
            phaseAxis = PolyChartAxes.Y_INDEX;
        }
    }

    protected void resetPhaseDim() {
        datasetPhaseDim = 0;
    }

    protected void setPhasePivot() {
        DatasetBase dataset = getDataset();
        if (dataset == null) {
            setPivot(null);
        } else {
            Orientation orientation = phaseAxis % 2 == 0 ? Orientation.VERTICAL : Orientation.HORIZONTAL;
            setPivot(crossHairs.getPosition(0, orientation));
        }
    }

    private Optional<Vec> getFirstVec() {
        Dataset dataset = (Dataset) getDataset();
        Vec vec = null;
        if (dataset != null) {
            if (dataset.getVec() != null) {
                vec = dataset.getVec();
            } else {
                try {
                    vec = dataset.readVector(0, 0);
                } catch (IOException ioE) {
                    log.error("Can't read vector", ioE);
                }
            }
        }
        return Optional.ofNullable(vec);
    }

    protected void setPivotToMax() {
        if (is1D()) {
            getFirstVec().ifPresent(vec -> {
                int maxIndex = vec.maxIndex().getIndex();
                double ppm = vec.pointToPPM(maxIndex);
                setPivot(ppm);
            });
        } else {
            Vec sliceVec = new Vec(32, false);
            sliceVec.setName("phasing slice");
            int iOrient = 0;
            int iCross = 0;
            DatasetAttributes dataAttr = datasetAttributesList.get(0);

            try {
                dataAttr.getSlice(sliceVec, iOrient,
                        crossHairs.getPosition(iCross, Orientation.VERTICAL),
                        crossHairs.getPosition(iCross, Orientation.HORIZONTAL));
            } catch (IOException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }

    protected void autoPhase(boolean doMax, boolean doFirst) {
        if (is1D()) {
            getFirstVec().ifPresent(vec -> {
                if (doMax) {
                    setPh0(vec.autoPhaseByMax());
                } else {
                    double[] phases = vec.autoPhase(doFirst, 0, 0, 2, 180.0, 1.0);
                    setPh0(phases[0]);
                    setPh1(0.0);
                    if (phases.length == 2) {
                        setPh1(phases[1]);
                    }
                }

                double sliderPH0 = getPh0() + vec.getPH0();
                double sliderPH1 = getPh1() + vec.getPH1();
                controller.getPhaser().handlePh1Reset(sliderPH1);
                controller.getPhaser().handlePh0Reset(sliderPH0);
                layoutPlotChildren();
            });
        }
    }

    protected void expand(Orientation orientation) {
        getFirstDatasetAttributes().ifPresent(attributes -> {
            int dNum = orientation == Orientation.VERTICAL ? 0 : 1;
            double[] limits = attributes.checkLimits(axes.getMode(dNum), dNum,
                    crossHairs.getPosition(0, orientation),
                    crossHairs.getPosition(1, orientation));
            axes.setMinMax(dNum, limits[0], limits[1]);
        });
    }

    public void expand() {
        ChartUndoLimits undo = new ChartUndoLimits(this);
        expand(Orientation.VERTICAL);
        DatasetBase dataset = getDataset();
        if (dataset != null) {
            if (disDimProp.get() == DISDIM.TwoD) {
                expand(Orientation.HORIZONTAL);
            }
        }
        layoutPlotChildren();
        crossHairs.hideAll();
        ChartUndoLimits redo = new ChartUndoLimits(this);
        controller.getUndoManager().add("expand", undo, redo);

    }

    protected double getRefPositionFromCrossHair(double newPPM) {
        DatasetBase dataset = getDataset();
        DatasetAttributes datasetAttributes = getFirstDatasetAttributes().orElse(null);
        if (dataset == null || datasetAttributes == null || controller.getChartProcessor() == null) {
            return 0.0;
        }

        ProcessingSection section = controller.getChartProcessor().getCurrentProcessingSection();
        int vecDim = controller.getChartProcessor().getVecDim();
        double position;
        double ppmPosition;
        double refPoint;
        double refPPM;
        int size;
        double centerPPM;
        if (is1D() || section.getFirstDimension() == 0) {
            position = axes.getMode(0).getIndex(datasetAttributes, 0, crossHairs.getPosition(0, Orientation.VERTICAL));
            size = dataset.getSizeReal(datasetAttributes.dim[0]);
            refPoint = dataset.getRefPt(datasetAttributes.dim[0]);
            refPPM = dataset.getRefValue(datasetAttributes.dim[0]);
            ppmPosition = dataset.pointToPPM(0, position);
            centerPPM = dataset.pointToPPM(0, size / 2);
        } else {
            position = axes.getMode(vecDim).getIndex(datasetAttributes, vecDim, crossHairs.getPosition(0, Orientation.HORIZONTAL));
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

    public Optional<RegionRange> addRegionRange(boolean offsetByExtractRegion) {
        DatasetBase dataset = getDataset();
        DatasetAttributes datasetAttributes = getFirstDatasetAttributes().orElse(null);
        if (dataset == null || datasetAttributes == null || controller.getChartProcessor() == null) {
            return Optional.empty();
        }
        ProcessingSection section = controller.getChartProcessor().getCurrentProcessingSection();
        int vecDim = controller.getChartProcessor().getVecDim();
        double pt0;
        double pt1;
        double ppm0;
        double ppm1;
        int size;
        if (is1D() || section.getFirstDimension() == 0) {
            ppm0 = crossHairs.getPosition(0, Orientation.VERTICAL);
            ppm1 = crossHairs.getPosition(1, Orientation.VERTICAL);
            pt0 = axes.getMode(0).getIndex(datasetAttributes, 0, ppm0);
            pt1 = axes.getMode(0).getIndex(datasetAttributes, 0, ppm1);
            size = dataset.getSizeReal(datasetAttributes.dim[0]);
        } else {
            ppm0 = crossHairs.getPosition(0, Orientation.HORIZONTAL);
            ppm1 = crossHairs.getPosition(1, Orientation.HORIZONTAL);
            pt0 = axes.getMode(0).getIndex(datasetAttributes, 0, ppm0);
            pt1 = axes.getMode(0).getIndex(datasetAttributes, 0, ppm1);
            size = dataset.getSizeReal(datasetAttributes.dim[vecDim]);
        }
        if (pt0 > pt1) {
            double hold = pt0;
            pt0 = pt1;
            pt1 = hold;
        }
        if (pt0 < 0) {
            pt0 = 0.0;
        }
        if (offsetByExtractRegion) {
            int[] currentRegion = controller.getExtractRegion(section, size);
            pt0 += currentRegion[0];
            pt1 += currentRegion[0];
        }
        double f0 = pt0 / (size - 1);
        double f1 = pt1 / (size - 1);
        double mul = Math.pow(10.0, Math.ceil(Math.log10(size)));
        f0 = Math.round(f0 * mul) / mul;
        f1 = Math.round(f1 * mul) / mul;
        var result = new RegionRange(pt0, pt1, ppm0, ppm1, f0, f1);
        return Optional.of(result);
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
            sortDatasetsByDimensions(newList);
            PolyChartManager.getInstance().currentDatasetProperty().set(getDataset());
        }
    }

    /**
     * If the dimensions are compatible, sets the dimension for each axis for the newAttr to match with the
     * old attribute. If dimensions are not compatible, the current/default value in newAttr remains unchanged.
     *
     * @param originalAttr The attribute to get the current dimensions from.
     * @param newAttr      The new attribute to update the dimensions from.
     * @return True if the newAttr dimensions were updated.
     */
    private boolean adjustDimensionsIfAttributesDifferent(DatasetAttributes originalAttr, DatasetAttributes newAttr) {
        if (originalAttr == newAttr) {
            return false;
        }
        List<String> axisNucleusNames = new ArrayList<>();
        axisNucleusNames.add(originalAttr.getDataset().getNucleus(originalAttr.getDims()[0]).getNumberName());
        if (originalAttr.getDataset().getNDim() > 1) {
            axisNucleusNames.add(originalAttr.getDataset().getNucleus(originalAttr.getDims()[1]).getNumberName());
        }
        if (!isDatasetAttributesIncompatible(axisNucleusNames, newAttr)) {
            for (int index = 0; index < axisNucleusNames.size(); index++) {
                String axisName = axes.get(index).getOrientation() == Orientation.HORIZONTAL ? "X" : "Y";
                newAttr.setDim(axisName, axisNucleusNames.get(index));
            }
            return true;
        }
        return false;
    }

    /**
     * Sorts the datasets in descending order based on the number of dimensions, sets the sorted list to dataset
     * attributes and updates the dimension and axis type. If disDimProp is updated, the chart is full and autoscaled.
     *
     * @param newAttributes the DatasetAttributes to sort.
     */
    private void sortDatasetsByDimensions(List<DatasetAttributes> newAttributes) {
        boolean fullChart = false;
        if (!newAttributes.isEmpty()) {
            DatasetAttributes originalFirst = newAttributes.get(0);
            // Sort the datasets by dimension and by datasets
            newAttributes.sort(Comparator.comparingInt((DatasetAttributes a) -> a.getDataset().getNDim()).reversed());
            // See what previous dims values were, if compatible, try to keep those dims, otherwise just use the default
            fullChart = adjustDimensionsIfAttributesDifferent(originalFirst, newAttributes.get(0));
        }
        ObservableList<DatasetAttributes> datasetAttrs = getDatasetAttributes();

        if (!newAttributes.isEmpty() && datasetAttrs.isEmpty()) {
            // if no datsets present already must use addDataset once to set up
            // various parameters
            controller.addDataset(this, newAttributes.get(0).getDataset(), false, false);
            newAttributes.remove(0);
            datasetAttrs.addAll(newAttributes);
        } else {
            datasetAttrs.clear();
            datasetAttrs.addAll(newAttributes);
        }
        if (!newAttributes.isEmpty()) {
            AnalystApp.getFXMLControllerManager().getOrCreateActiveController().updateSpectrumStatusBarOptions(false);
            DISDIM newDISDIM = newAttributes.get(0).getDataset().getNDim() == 1 ? DISDIM.OneDX : TwoD;
            // If the display has switched dimensions, full the chart otherwise the axis might be much larger than the current dataset
            fullChart = fullChart || newDISDIM != disDimProp.get();
            disDimProp.set(newDISDIM);
            updateAxisType(false);
        }
        if (fullChart) {
            autoScale();
            full();
        }

    }

    public void updateProjections() {
        boolean has1D = false;
        boolean hasND = false;
        Optional<DatasetAttributes> firstNDAttr = Optional.empty();
        for (DatasetAttributes datasetAttributes : datasetAttributesList) {
            // Clear previous projection values
            datasetAttributes.projection(-1);
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
                if (matchDim[0] != -1 && matchDim[0] < 2) {
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
        DatasetAttributes datasetAttributes = null;
        if (dataset != null) {
            if (append) {
                datasetAttributes = new DatasetAttributes(dataset);
                if (datasetAttributes.getDataset().isLvlSet()) {
                    datasetAttributes.setLvl(datasetAttributes.getDataset().getLvl());
                    datasetAttributes.setHasLevel(true);
                }
                datasetAttributesList.add(datasetAttributes);
                if (datasetAttributesList.size() > 1) {
                    sortDatasetsByDimensions(new ArrayList<>(datasetAttributesList));
                }
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
                    int[] oldDims = datasetAttributes.getDims();
                    datasetAttributes.setDataset(dataset);
                    if ((existingDataset == null) || (!keepLevel && !existingDataset.getName().equals(dataset.getName()))) {
                        datasetAttributes.setLvl(dataset.getLvl());
                    } else if ((existingDataset != null) && existingDataset.getName().equals(dataset.getName())) {
                        datasetAttributes.setLvl(oldLevel);
                        datasetAttributes.setHasLevel(true);
                        datasetAttributes.setDims(oldDims);
                    }
                }
                datasetAttributesList.setAll(datasetAttributes);
            }
            // Set disDimProp after updating datasetAttributesList as it can trigger listeners
            // that get the dataset from this chart
            if ((dataset.getNDim() == 1) || (dataset.getNFreqDims() == 1)) {
                disDimProp.set(DISDIM.OneDX);
                setSliceStatus(false);
            } else {
                disDimProp.set(DISDIM.TwoD);
            }
            updateAxisType(true);
            datasetFileProp.set(dataset.getFile());
            datasetAttributes.drawList.clear();
            PolyChartManager.getInstance().currentDatasetProperty().set(dataset);
        } else {
            setSliceStatus(false);

            datasetFileProp.setValue(null);
        }

        crossHairs.hideAll();
        return datasetAttributes;
    }

    public int setDrawlist(int value) {
        if (!datasetAttributesList.isEmpty()) {
            for (DatasetAttributes datasetAttributes : datasetAttributesList) {
                if (datasetAttributes.isProjection() || datasetAttributes.getDataset().getNDim() < 2) {
                    datasetAttributes.setDrawListSize(0);
                    continue;
                }
                datasetAttributes.setDrawListSize(1);
                DatasetBase dataset = datasetAttributes.getDataset();
                if (dataset.getNDim() > 1) {
                    int iDim = dataset.getNDim() - 1;
                    if (value < 0) {
                        value = 0;
                    }
                    if (value >= dataset.getSizeReal(iDim)) {
                        value = dataset.getSizeReal(iDim) - 1;
                    }
                    datasetAttributes.setDrawList(value);
                } else {
                    datasetAttributes.setDrawListSize(0);
                }
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
     *
     * @return A list of row indices to draw.
     */
    public List<Integer> getDrawList() {
        if (datasetAttributesList.isEmpty()) {
            log.info("No draw list present.");
            return Collections.emptyList();
        }

        List<Integer> first = datasetAttributesList.get(0).drawList;
        for (int i = 1; i < datasetAttributesList.size(); i++) {
            if (!first.equals(datasetAttributesList.get(i).drawList)) {
                log.warn("Dataset draw lists are not equal. Using draw list for: {}", datasetAttributesList.get(0).getFileName());
                break;
            }
        }
        return first;
    }

    public List<String> getDimNames() {
        List<String> names = new ArrayList<>();
        getFirstDatasetAttributes().ifPresent(attr -> {
            for (int i = 0; i < attr.nDim; i++) {
                String label = attr.getLabel(i);
                names.add(label);
            }
        });
        return names;
    }

    void updateDatasetAttributeBounds() {
        for (DatasetAttributes datasetAttributes : datasetAttributesList) {
            datasetAttributes.updateBounds(axes, disDimProp.getValue());
        }
    }

    void setDatasetAttr(DatasetAttributes datasetAttrs) {
        DatasetBase dataset = datasetAttrs.getDataset();
        int nAxes = dataset.getNDim();
        if (is1D()) {
            nAxes = 2;
        }
        datasetAttributesList.clear();
        datasetAttributesList.add(datasetAttrs);
        if (axes.count() != nAxes) {
            axes.resetFrom(this, datasetAttrs, nAxes);
        }
    }

    public void updateAxisType(boolean alwaysUpdate) {
        DatasetBase dataset = getDataset();
        DatasetAttributes datasetAttributes = getFirstDatasetAttributes().orElse(null);
        if (dataset == null || datasetAttributes == null) {
            log.warn("Trying to update axis type but no dataset present!");
            return;
        }

        int nAxes = is1D() ? 2 : dataset.getNDim();
        int[] dims = datasetAttributes.getDims();
        if (alwaysUpdate || (axes.count() != nAxes)) {
            axes.updateAxisType(this, datasetAttributes, nAxes);
        }
        if (dataset.getFreqDomain(dims[0])) {
            axes.setMode(0, AXMODE.PPM);
        } else {
            axes.setMode(0, AXMODE.TIME);
        }
        boolean reversedAxis = (axes.getMode(0) == AXMODE.PPM);
        boolean autoScale = reversedAxis != axes.getX().isReversed();
        axes.getX().setReverse(reversedAxis);
        String xLabel = axes.getMode(0).getLabel(datasetAttributes, 0);
        axes.getX().updateLabel(xLabel);
        if (!is1D()) {
            if (dataset.getFreqDomain(dims[1])) {
                axes.setMode(1, AXMODE.PPM);
            } else {
                axes.setMode(1, AXMODE.PTS);
            }
            reversedAxis = (axes.getMode(1) == AXMODE.PPM);
            if (reversedAxis != axes.getX().isReversed()) {
                autoScale = true;
            }

            axes.getY().setReverse(reversedAxis);
            String yLabel = axes.getMode(0).getLabel(datasetAttributes, 1);
            axes.getY().updateLabel(yLabel);
        } else {
            axes.getY().setReverse(false);
            axes.getY().updateLabel("Intensity");
        }
        if (autoScale) {
            axes.fullLimits(disDimProp.get());
            if (!datasetAttributes.getHasLevel()) {
                autoScale(datasetAttributes);
            }
        }
    }

    public void refresh() {
        layoutPlotChildren();
    }

    public void draw() {
        Fx.runOnFxThread(this::refresh);
    }

    public void setMinBorders(double bottom, double left) {
        minLeftBorder = left;
        minBottomBorder = bottom;
    }

    public Insets getMinBorders() {
        // A bit misleading: this also sets axis font sizes.
        // necessary because axis border size depends on font size...
        axes.getX().setTickFontSize(chartProps.getTicFontSize());
        axes.getX().setLabelFontSize(chartProps.getLabelFontSize());
        axes.getY().setTickFontSize(chartProps.getTicFontSize());
        axes.getY().setLabelFontSize(chartProps.getLabelFontSize());

        double top = chartProps.getTopBorderSize();
        double right = chartProps.getRightBorderSize();
        double bottom = axes.getX().getBorderSize();
        double left = is1D() && !chartProps.getIntensityAxis() ? 8 : axes.getY().getBorderSize();
        return new Insets(top, right, bottom, left);
    }

    private Insets getUseBorders() {
        Insets min = getMinBorders();
        double left = Math.max(min.getLeft(), minLeftBorder);
        left = Math.max(left, chartProps.getLeftBorderSize());
        double bottom = Math.max(min.getBottom(), minBottomBorder);
        bottom = Math.max(bottom, chartProps.getBottomBorderSize());
        Insets borders = new Insets(min.getTop(), min.getRight(), bottom, left);
        if (chartProps.getAspect() && !is1D()) {
            borders = adjustAspect(borders);
        }
        return borders;
    }

    private Insets adjustAspect(Insets borders) {
        double width = getWidth();
        double height = getHeight();
        double adjustedTop = borders.getTop();
        double adjustedRight = borders.getRight();
        DatasetBase dataset = getDataset();
        DatasetAttributes dAttr = getFirstDatasetAttributes().orElse(null);
        if (dataset != null && dAttr != null && axes.getMode(0) == AXMODE.PPM && axes.getMode(1) == AXMODE.PPM && dataset.getNDim() > 1) {
            Nuclei nuc0 = dataset.getNucleus(dAttr.getDim(0));
            Nuclei nuc1 = dataset.getNucleus(dAttr.getDim(1));
            if ((nuc0 != null) && (nuc1 != null)) {
                double fRatio0 = dataset.getNucleus(dAttr.getDim(0)).getFreqRatio();
                double fRatio1 = dataset.getNucleus(dAttr.getDim(1)).getFreqRatio();
                double dXAxis = Math.abs(axes.getX().getUpperBound() - axes.getX().getLowerBound());
                double dYAxis = Math.abs(axes.getY().getUpperBound() - axes.getY().getLowerBound());

                double ppmRatio = dXAxis / dYAxis;
                double ppmRatioF = fRatio1 / fRatio0;
                double chartAspectRatio = chartProps.getAspectRatio();
                double aspectRatio = chartAspectRatio * (ppmRatio / ppmRatioF);
                double dX = width - borders.getLeft() - adjustedRight;
                double dY = height - (borders.getBottom() + adjustedTop);
                double newDX = dY * aspectRatio;

                if (newDX > dX) {
                    double newDY = dX / aspectRatio;
                    adjustedTop = height - borders.getBottom() - newDY;
                } else {
                    adjustedRight = width - borders.getLeft() - newDX;
                }
            }
        }

        return new Insets(adjustedTop, adjustedRight, borders.getBottom(), borders.getLeft());
    }

    public boolean isChartDisabled() {
        return disabled;
    }

    public void setChartDisabled(boolean state) {
        disabled = state;
    }

    void highlightChart() {
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        double width = getWidth();
        double height = getHeight();
        highlightRect.setX(xPos + 1);
        highlightRect.setY(yPos + 1);
        highlightRect.setWidth(width - 2);
        highlightRect.setHeight(height - 2);
    }

    public static void updateImmediateModes(boolean state) {
        AnalystApp.getFXMLControllerManager().getControllers().stream()
                .forEach(controller -> {
                    controller.getCharts().stream().forEach(chart -> chart.useImmediateMode(state));
                });
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
        if (is1D()) {
            axes.setYAxisByLevel();
        }

        GraphicsContextInterface gC = drawingLayers.getGraphicsProxyFor(ChartDrawingLayers.Item.Spectrum);
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
            crossHairs.setLineColors(fillColor, chartProps.getCross0Color(), chartProps.getCross1Color());

            Color axesColorLocal = chartProps.getAxesColor();
            if (axesColorLocal == null) {
                axesColorLocal = controller.getAxesColor();
                if (axesColorLocal == null) {
                    axesColorLocal = toBlackOrWhite(fillColor);

                }
            }

            axes.getX().setTickFontSize(chartProps.getTicFontSize());
            axes.getX().setLabelFontSize(chartProps.getLabelFontSize());

            axes.getY().setTickFontSize(chartProps.getTicFontSize());
            axes.getY().setLabelFontSize(chartProps.getLabelFontSize());
            borders = getUseBorders();
            stackWidth = 0.0;
            double axWidth = width - borders.getLeft() - borders.getRight();
            if (disDimProp.get() != DISDIM.TwoD) {
                int n1D = datasetAttributesList.stream().filter(d -> !d.isProjection() && d.getPos())
                        .mapToInt(d -> d.getLastChunk(0) + 1).sum();
                if (n1D > 1) {
                    double fWidth = 0.9 * axWidth / n1D;
                    stackWidth = (axWidth - fWidth) * chartProps.getStackX();
                }
            }

            axes.getX().setWidth(axWidth - stackWidth);
            axes.getX().setHeight(borders.getBottom());
            axes.getX().setOrigin(xPos + borders.getLeft(), yPos + getHeight() - borders.getBottom());

            axes.getY().setHeight(height - borders.getBottom() - borders.getTop());
            axes.getY().setWidth(borders.getLeft());
            axes.getY().setOrigin(xPos + borders.getLeft(), yPos + getHeight() - borders.getBottom());

            gC.setStroke(axesColorLocal);
            axes.getX().setColor(axesColorLocal);
            axes.getY().setColor(axesColorLocal);
            if (chartProps.getGrid()) {
                axes.getX().setGridLength(axes.getY().getHeight());
                axes.getY().setGridLength(axes.getX().getWidth());
            } else {
                axes.getX().setGridLength(0.0);
                axes.getY().setGridLength(0.0);

            }
            gC.setLineWidth(axes.getX().getLineWidth());

            // Draw the datasets before the axis since drawing the datasets may adjust the axis range
            if (!drawDatasets(gC)) {
                // if we used immediate mode and didn't finish in time try again
                // useImmediate mode will have been set to false
                Platform.runLater(this::layoutPlotChildren);
                gC.restore();
                return;
            }

            axes.getX().draw(gC);
            if (!is1D() || chartProps.getIntensityAxis()) {
                axes.getY().draw(gC);
                gC.strokeLine(xPos + borders.getLeft(), yPos + borders.getTop(), xPos + width - borders.getRight(), yPos + borders.getTop());
                gC.strokeLine(xPos + width - borders.getRight(), yPos + borders.getTop(), xPos + width - borders.getRight(), yPos + height - borders.getBottom());
            }

            GraphicsContext peakGC = drawingLayers.getGraphicsContextFor(ChartDrawingLayers.Item.Peaks);
            peakGC.clearRect(xPos, yPos, width, height);
            gC.beginPath();

            drawParameters(chartProps.getParameters());
            if (!datasetAttributesList.isEmpty()) {
                drawPeakLists(true);
            }

            GraphicsContextInterface gcAnnotations = drawingLayers.getGraphicsProxyFor(ChartDrawingLayers.Item.Annotations);
            drawAnnotations(gcAnnotations);
            crossHairs.refresh();
            gC.restore();
            highlightChart();
            getFXMLController().updateDatasetAttributeControls();

        } catch (GraphicsIOException ioE) {
            log.warn(ioE.getMessage(), ioE);
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
                axesColorLocal = toBlackOrWhite(fillColor);
            }
        }
        if (is1D()) {
            axes.setYAxisByLevel();
        }
        svgGC.setStroke(axesColorLocal);
        axes.getX().setColor(axesColorLocal);
        axes.getY().setColor(axesColorLocal);
        if (chartProps.getGrid()) {
            axes.getX().setGridLength(axes.getY().getHeight());
            axes.getY().setGridLength(axes.getX().getWidth());
        } else {
            axes.getX().setGridLength(0.0);
            axes.getY().setGridLength(0.0);

        }

        axes.getX().draw(svgGC);
        if (!is1D() || chartProps.getIntensityAxis()) {
            axes.getY().draw(svgGC);
            svgGC.strokeLine(xPos + borders.getLeft(), yPos + borders.getTop(), xPos + width - borders.getRight(), yPos + borders.getTop());
            svgGC.strokeLine(xPos + width - borders.getRight(), yPos + borders.getTop(), xPos + width - borders.getRight(), yPos + height - borders.getBottom());
        }
        drawDatasets(svgGC);
        drawSlices(svgGC);
        if (!datasetAttributesList.isEmpty()) {
            drawPeakLists(true, svgGC);
            drawSelectedPeaks(svgGC);
        }
    }

    boolean drawDatasets(GraphicsContextInterface gC) throws GraphicsIOException {
        boolean finished = true;
        updateDatasetAttributeBounds();
        if (disDimProp.get() != DISDIM.TwoD) {
            drawDatasetsTrace(gC);
        } else {
            finished = drawDatasetsContours(gC);
        }
        return finished;
    }

    void drawDatasetsTrace(GraphicsContextInterface gC) {
        int nDatasets = datasetAttributesList.size();
        int iTitle = 0;
        double firstOffset;
        double firstLvl = 1.0;
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        drawSpectrum.setStackWidth(stackWidth);
        drawSpectrum.setStackY(chartProps.getStackY());
        // Only draw compatible datasets but do not remove incompatible attributes from datasetAttributes as the chart
        // datasets may only be incompatible in a certain display mode.
        List<DatasetAttributes> compatibleAttributes = new ArrayList<>(datasetAttributesList);
        removeIncompatibleDatasetAttributes(compatibleAttributes);
        int n1D = 0;
        if (disDimProp.get() != DISDIM.TwoD) {
            n1D = compatibleAttributes.stream().filter(d -> !d.isProjection() && d.getPos())
                    .mapToInt(d -> d.getLastChunk(0) + 1).sum();
        }
        int i1D = 0;
        DatasetAttributes firstAttr = null;
        for (int iData = compatibleAttributes.size() - 1; iData >= 0; iData--) {
            DatasetAttributes datasetAttributes = compatibleAttributes.get(iData);
            DatasetBase dataset = datasetAttributes.getDataset();
            if ((dataset.getSizeReal(0) == 0) || datasetAttributes.isProjection() || !datasetAttributes.getPos() || (dataset == null)) {
                continue;
            }
            if (firstAttr == null) {
                firstAttr = datasetAttributes;
            }
            datasetAttributes.setDrawReal(true);
            if (datasetAttributes == firstAttr) {
                firstLvl = datasetAttributes.getLvl();
                updateAxisType(false);
            } else {
                datasetAttributes.syncDims(firstAttr);
            }
            firstOffset = datasetAttributes.getOffset();
            try {
                if (chartProps.getRegions()) {
                    drawRegions(datasetAttributes, gC);
                }
                gC.save();
                double clipExtra = 1;
                drawSpectrum.setClipRect(xPos + borders.getLeft() + clipExtra, yPos + borders.getTop() + clipExtra,
                        axes.getX().getWidth() - 2 * clipExtra + stackWidth, axes.getY().getHeight() - 2 * clipExtra);

                drawSpectrum.clip(gC);
                try {
                    for (int iMode = 0; iMode < 2; iMode++) {
                        if (iMode == 0) {
                            datasetAttributes.setDrawReal(true);
                        } else {
                            if (!controller.getStatusBar().isComplex()) {
                                break;
                            }
                            datasetAttributes.setDrawReal(false);
                        }
                        drawSpectrum.setToLastChunk(datasetAttributes);
                        boolean ok;
                        do {
                            bcPath.getElements().clear();
                            ok = drawSpectrum.draw1DSpectrum(datasetAttributes, firstLvl, firstOffset, i1D, n1D, HORIZONTAL,
                                    axes.getMode(0), getPh0(), getPh1(), bcPath);
                            double[][] xy = drawSpectrum.getXY();
                            int nPoints = drawSpectrum.getNPoints();
                            int rowIndex = drawSpectrum.getRowIndex();
                            boolean selected = datasetAttributes.isSelected(rowIndex);
                            drawSpecLine(datasetAttributes, gC, iMode, rowIndex, nPoints, xy, selected);
                            gC.setFill(datasetAttributes.getPosColor(rowIndex));
                            if (chartProps.getIntegrals()) {
                                draw1DIntegral(datasetAttributes, gC);
                            }
                            drawBaseLine(gC, bcPath);
                            if (iMode == 0) {
                                i1D++;
                            }

                        } while (ok);
                    }
                    drawSpectrum.drawVecAnno(datasetAttributes, HORIZONTAL, axes.getMode(0));
                    double[][] xy = drawSpectrum.getXY();
                    int nPoints = drawSpectrum.getNPoints();
                    drawSpecLine(datasetAttributes, gC, 0, -1, nPoints, xy, false);
                } finally {
                    gC.restore();
                }
                if (chartProps.getTitles()) {
                    drawTitle(gC, datasetAttributes, iTitle++, nDatasets);
                }
            } catch (GraphicsIOException gIO) {
                log.warn(gIO.getMessage(), gIO);
            }
        }
    }

    boolean drawDatasetsContours(GraphicsContextInterface gC) {
        List<DatasetAttributes> compatibleAttributes = new ArrayList<>(datasetAttributesList);
        removeIncompatibleDatasetAttributes(compatibleAttributes);

        if (compatibleAttributes.isEmpty()) {
            return true;
        }
        updateProjections();

        ArrayList<DatasetAttributes> draw2DList = new ArrayList<>();
        DatasetAttributes firstAttr = compatibleAttributes.get(0);
        updateAxisType(false);
        compatibleAttributes.stream()
                .filter(d -> (d.getDataset() != null) && !d.isProjection() && (d.getDataset().getNDim() > 1))
                .forEach(d -> {
                    if (d != firstAttr) {
                        d.syncDims(firstAttr);
                    }
                    draw2DList.add(d);
                });
        boolean finished = true;
        if (!draw2DList.isEmpty()) {
            for (DatasetAttributes datasetAttributes : datasetAttributesList) {
                if (datasetAttributes.isProjection()) {
                    drawProjection(gC, datasetAttributes.projection(), datasetAttributes);
                }
            }
            if (chartProps.getTitles()) {
                double xPos = getLayoutX();
                double yPos = getLayoutY();
                drawTitles(gC, draw2DList, xPos, yPos);
            }
            if (gC instanceof GraphicsContextProxy) {
                if (useImmediateMode) {
                    finished = drawSpectrum.drawSpectrumImmediate(gC, draw2DList);
                    useImmediateMode = finished;
                } else {
                    drawSpectrum.drawSpectrum(draw2DList, false);
                }
            } else {
                finished = drawSpectrum.drawSpectrumImmediate(gC, draw2DList);
            }
        }
        return finished;
    }

    void drawTitles(GraphicsContextInterface gC, ArrayList<DatasetAttributes> draw2DList, double xPos, double yPos) {
        double fontSize = chartProps.getTicFontSize();
        gC.setFont(Font.font(fontSize));
        gC.setTextAlign(TextAlignment.LEFT);
        double textX = xPos + borders.getLeft() + 10.0;
        double textY;
        if (fontSize > (borders.getTop() - 2)) {
            gC.setTextBaseline(VPos.TOP);
            textY = yPos + borders.getTop() + 2;
        } else {
            gC.setTextBaseline(VPos.BOTTOM);
            textY = yPos + borders.getTop() - 2;
        }
        for (DatasetAttributes datasetAttributes : draw2DList) {
            gC.setFill(datasetAttributes.getPosColor());
            String title = datasetAttributes.getDataset().getTitle();
            gC.fillText(title, textX, textY);
            textX += GUIUtils.getTextWidth(title, gC.getFont()) + 10;
        }
    }

    /**
     * Check whether the axis of the dataset attributes are compatible with the first element of the provided attributes
     * list. Incompatible attributes are removed from the list and the range is adjusted based on the remaining
     * attributes.
     *
     * @param attributes The attributes list to remove incompatible datasets from.
     */
    private void removeIncompatibleDatasetAttributes(List<DatasetAttributes> attributes) {
        if (attributes.size() < 2) {
            return;
        }
        Iterator<DatasetAttributes> attributesIterator = attributes.iterator();
        DatasetAttributes firstAttr = attributesIterator.next();
        List<String> axisNucleusNames = new ArrayList<>();
        axisNucleusNames.add(getDataset().getNucleus(firstAttr.getDims()[0]).getNumberName());
        if (firstAttr.getDataset().getNDim() > 1) {
            axisNucleusNames.add(getDataset().getNucleus(firstAttr.getDims()[1]).getNumberName());
        }
        while (attributesIterator.hasNext()) {
            DatasetAttributes datasetAttributes = attributesIterator.next();
            if (isDatasetAttributesIncompatible(axisNucleusNames, datasetAttributes)) {
                if (!datasetAttributes.isProjection()) {
                    log.info("Mismatched dimensions. Unable to display dataset: {}", datasetAttributes.getDataset().getName());
                }
                attributesIterator.remove();
            }
        }
        // No incompatible datasets were found
        if (attributes.size() == getDatasetAttributes().size()) {
            return;
        }
        double[] limits = axes.getRangeFromDatasetAttributesList(attributes, 0);
        if (!axes.currentRangeWithinNewRange(limits, 0)) {
            axes.getX().setMinMax(limits[0], limits[1]);
        }
        if (disDimProp.get() == DISDIM.TwoD) {
            limits = axes.getRangeFromDatasetAttributesList(attributes, 1);
            if (!axes.currentRangeWithinNewRange(limits, 1)) {
                axes.getY().setMinMax(limits[0], limits[1]);
            }
        }
    }

    /**
     * Counts the number of matches of the DatasetAttributes nucleus number names to the provided list of nucleus number
     * names and returns true if they have matching nuclei for all the axis names. Attributes that are projections are
     * considered to not be incompatible since they will not be displayed in the chart centre and will return false
     *
     * @param axisNamesToMatch  The axis nucleus names for the x and y axis.
     * @param attributesToMatch The DatasetAttributes to check the compatibility of
     * @return true if the dataset is incompatible
     */
    private boolean isDatasetAttributesIncompatible(List<String> axisNamesToMatch, DatasetAttributes attributesToMatch) {
        List<String> nucleusNames = new ArrayList<>(axisNamesToMatch);
        int numberOfMatches = 0;
        for (int dim : attributesToMatch.getDims()) {
            String attributeNucleusName = attributesToMatch.getDataset().getNucleus(dim).getNumberName();
            if (nucleusNames.contains(attributeNucleusName)) {
                nucleusNames.remove(attributeNucleusName);
                numberOfMatches++;
            }
        }
        return numberOfMatches < Math.min(attributesToMatch.getDataset().getNDim(), 2) || attributesToMatch.isProjection();
    }

    void drawTitle(GraphicsContextInterface gC, DatasetAttributes datasetAttributes,
                   int index, int nTitles) {
        gC.setFill(datasetAttributes.getPosColor());
        double fontSize = chartProps.getTicFontSize();
        gC.setFont(Font.font(fontSize));
        double textY;
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        if ((nTitles > 1) || fontSize > (borders.getTop() - 2)) {
            gC.setTextBaseline(VPos.TOP);
            textY = yPos + borders.getTop() + 2;
            double offset = (nTitles - index - 1) * fontSize;
            textY += offset;
        } else {
            gC.setTextBaseline(VPos.BOTTOM);
            textY = yPos + borders.getTop() - 2;
        }
        gC.setTextAlign(TextAlignment.LEFT);
        gC.fillText(datasetAttributes.getDataset().getTitle(),
                xPos + borders.getLeft() + 10, textY);
    }

    void drawParameters(boolean state) {
        if (!state && parameterText != null) {
            removeAnnotation(parameterText);
            parameterText = null;
        } else if (state) {
            Dataset dataset = (Dataset) getDataset();
            if (dataset != null) {
                String text = ProjectText.genText(dataset);
                if ((parameterText == null) || (!parameterText.getText().equals(text))) {
                    if (parameterText == null) {
                        double xPos = 10;
                        double yPos = chartProps.getTicFontSize() * 2;
                        double textWidth = 200;
                        parameterText = new AnnoText(xPos, yPos, textWidth, text, 12.0,
                                CanvasAnnotation.POSTYPE.PIXEL, CanvasAnnotation.POSTYPE.PIXEL);
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
        boolean leftX = (x > xPos) && (x < xPos + borders.getLeft());
        boolean centerX = (x > (borders.getLeft() + xPos) && (x < xPos + width - borders.getRight()));
        boolean rightX = (x > xPos + width - borders.getRight()) && (x < xPos + width);
        boolean topY = (y > yPos) && (y < yPos + borders.getTop());
        boolean centerY = (y > yPos + borders.getTop()) && (y < yPos + height - borders.getBottom());
        boolean bottomY = (y > yPos + height - borders.getBottom()) && (y < yPos + height);
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
        List<DatasetRegion> regions = datasetAttr.getDataset().getReadOnlyRegions();
        if (regions == null) {
            return;
        }
        double chartHeight = axes.getY().getHeight();
        for (DatasetRegion region : regions) {
            double ppm1 = region.getRegionStart(0);
            double ppm2 = region.getRegionEnd(0);
            double x1 = axes.getX().getDisplayPosition(ppm2);
            double x2 = axes.getX().getDisplayPosition(ppm1);
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
            gC.fillRect(x1, getLayoutY() + borders.getTop() + 1, x2 - x1, chartHeight - 2);

        }
    }

    void draw1DIntegral(DatasetAttributes datasetAttr, GraphicsContextInterface gC) throws GraphicsIOException {
        List<DatasetRegion> regions = datasetAttr.getDataset().getReadOnlyRegions();
        if (regions == null) {
            return;
        }
        double xMin = axes.getX().getLowerBound();
        double xMax = axes.getX().getUpperBound();
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
                        ppm1, ppm2, offsets,
                        integralMax, chartProps.getIntegralLowPos(),
                        chartProps.getIntegralHighPos());
                if (result.isPresent()) {
                    double[][] xy = drawSpectrum.getXY();
                    int nPoints = drawSpectrum.getNPoints();
                    int rowIndex = drawSpectrum.getRowIndex();
                    gC.setTextAlign(TextAlignment.CENTER);
                    gC.setTextBaseline(VPos.BASELINE);
                    drawSpecLine(datasetAttr, gC, 0, rowIndex, nPoints, xy, false);
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
     *
     * @param regions The regions to search.
     * @return The max integral value.
     */
    private double getIntegralMaxFromRegions(List<DatasetRegion> regions) {
        double integralMax = 0.0;
        for (DatasetRegion region : regions) {
            integralMax = Math.max(integralMax, Math.abs(region.getIntegral()));
        }
        return integralMax;
    }

    public Optional<IntegralHit> hitIntegral(DatasetAttributes datasetAttr, double pickX, double pickY) {
        Optional<IntegralHit> hit = Optional.empty();
        List<DatasetRegion> regions = datasetAttr.getDataset().getReadOnlyRegions();
        if (regions != null) {
            double xMin = axes.getX().getLowerBound();
            double xMax = axes.getX().getUpperBound();
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
                            ppm1, ppm2, offsets,
                            integralMax, chartProps.getIntegralLowPos(),
                            chartProps.getIntegralHighPos());
                    if (result.isPresent()) {
                        double[][] xy = drawSpectrum.getXY();
                        int nPoints = drawSpectrum.getNPoints();
                        for (int i = 0; i < nPoints; i++) {
                            double x = xy[0][i];
                            double y = xy[1][i];
                            if ((Math.abs(pickX - x) < hitRange) && (Math.abs(pickY - y) < hitRange)) {
                                int handle = i < nPoints / 2 ? -1 : -2;
                                Bounds bounds = new BoundingBox(xy[0][0], xy[1][nPoints - 1], xy[0][nPoints - 1] - xy[0][0], xy[1][0] - xy[1][nPoints - 1]);
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

    public Optional<DatasetRegion> getActiveRegion() {
        Optional<DatasetRegion> region = Optional.empty();
        if (activeRegion.get() != null) {
            region = Optional.of(activeRegion.get());
        }
        return region;
    }

    public void setActiveRegion(DatasetRegion region) {
        activeRegion.set(region);
    }

    public boolean selectRegion(boolean controls, double pickX, double pickY) {
        for (DatasetAttributes datasetAttr : datasetAttributesList) {
            datasetAttr.setActiveRegion(null);
        }
        Optional<IntegralHit> hit = hitRegion(controls, pickX, pickY);
        hit.ifPresentOrElse(iHit -> {
            iHit.getDatasetAttr().setActiveRegion(iHit);
            activeRegion.set(iHit.getDatasetRegion());
        }, () -> activeRegion.set(null));

        return hit.isPresent();
    }

    public void addRegionListener(ChangeListener<DatasetRegion> listener) {
        activeRegion.addListener(listener);
    }

    public void removeRegionListener(ChangeListener<DatasetRegion> listener) {
        activeRegion.removeListener(listener);
    }

    public Optional<IntegralHit> selectIntegral(DatasetRegion datasetRegion) {
        for (DatasetAttributes datasetAttr : datasetAttributesList) {
            datasetAttr.setActiveRegion(null);
        }
        setActiveRegion(null);
        if (datasetRegion != null) {
            for (DatasetAttributes datasetAttr : datasetAttributesList) {
                if (datasetAttr.getDataset().getReadOnlyRegions().contains(datasetRegion)) {
                    IntegralHit newHit = new IntegralHit(datasetAttr, datasetRegion, -1);
                    datasetAttr.setActiveRegion(newHit);
                    setActiveRegion(datasetRegion);
                    return Optional.of(newHit);
                }
            }
        }

        return Optional.empty();
    }

    public Optional<IntegralHit> selectIntegral(double pickX, double pickY) {
        for (DatasetAttributes datasetAttr : datasetAttributesList) {
            datasetAttr.setActiveRegion(null);
        }
        Optional<IntegralHit> hit = hitIntegral(pickX, pickY);
        hit.ifPresentOrElse(iHit -> {
            iHit.getDatasetAttr().setActiveRegion(iHit);
            activeRegion.set(iHit.getDatasetRegion());
        }, () -> activeRegion.set(null));
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

    public Optional<IntegralHit> hitRegion(boolean controls, double pickX, double pickY) {
        Optional<IntegralHit> hit = Optional.empty();
        for (DatasetAttributes datasetAttr : datasetAttributesList) {

            List<DatasetRegion> regions = datasetAttr.getDataset().getReadOnlyRegions();
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

    void drawSpecLine(DatasetAttributes datasetAttributes, GraphicsContextInterface gC, int iMode, int rowIndex, int nPoints, double[][] xy, boolean selected) throws GraphicsIOException {
        if (nPoints > 1) {
            double widthScale = selected ? 3.0 : 1.0;
            if (iMode == 0) {
                gC.setStroke(datasetAttributes.getPosColor(rowIndex));
                gC.setLineWidth(datasetAttributes.getPosWidth() * widthScale);
            } else {
                gC.setStroke(datasetAttributes.getNegColor());
                gC.setLineWidth(datasetAttributes.getNegWidth() * widthScale);
            }
            gC.setLineCap(StrokeLineCap.BUTT);
            gC.strokePolyline(xy[0], xy[1], nPoints);
        }
    }

    void drawBaseLine(GraphicsContextInterface gC, Path path) throws GraphicsIOException {
        List<PathElement> elems = path.getElements();
        if (elems.size() > 1) {
            gC.beginPath();
            for (PathElement elem : elems) {
                if (elem instanceof MoveTo mv) {
                    gC.moveTo(mv.getX(), mv.getY());
                } else if (elem instanceof LineTo ln) {
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
        String listName = peakList.getName();

        // check in existing peak lists first
        for (PeakListAttributes peakListAttr : peakListAttributesList) {
            if (peakListAttr.peakListNameProperty().get().equals(listName)) {
                if (peakListAttr.getPeakList().peaks() == null) {
                    peakListAttr.setPeakList(peakList);
                }
                return peakListAttr;
            }
        }

        // not found, search in dataset attributes list
        if (isPeakListCompatible(peakList, true)) {
            DatasetAttributes matchData = getFirstDatasetAttributes().orElse(null);
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
            PeakListAttributes peakListAttr = new PeakListAttributes(this, matchData, peakList);
            peakListAttributesList.add(peakListAttr);
            peakList.registerPeakChangeListener(peakListener);
            return peakListAttr;
        }

        // not found in dataset attributes either
        return null;
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
                peakAttr.getPeakList().removePeakChangeListener(peakListener);
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

    public void deleteSelectedItems() {
        deleteSelectedPeaks();
        deleteSelectedAnnotations();
    }

    public void deleteSelectedPeaks() {
        List<Peak> deletedPeaks = new ArrayList<>();
        resetUndoGroup();
        for (PeakListAttributes peakListAttr : peakListAttributesList) {
            Set<Peak> peaks = peakListAttr.getSelectedPeaks();
            addPeaksUndo(peaks);
            for (Peak peak : peaks) {
                peak.setStatus(-1);
                deletedPeaks.add(peak);
            }
            addPeaksRedo(peaks);
        }
        if (!deletedPeaks.isEmpty()) {
            addUndoGroup("Delete Peaks");
        }
        if (!deletedPeaks.isEmpty() && (manualPeakDeleteAction != null)) {
            PeakDeleteEvent peakDeleteEvent = new PeakDeleteEvent(deletedPeaks, this);
            manualPeakDeleteAction.accept(peakDeleteEvent);
        }
    }

    public List<Peak> getSelectedPeaks() {
        List<Peak> selectedPeaks = new ArrayList<>();
        peakListAttributesList.forEach(peakListAttr -> {
            if (peakListAttr.getDrawPeaks()) {
                Set<Peak> peaks = peakListAttr.getSelectedPeaks();
                selectedPeaks.addAll(peaks);
            }
        });
        return selectedPeaks;
    }

    public void clearSelectedMultiplets() {
        peakListAttributesList.forEach(peakListAttr -> {
            if (peakListAttr.getDrawPeaks()) {
                peakListAttr.clearSelectedPeaks();
            }
        });
    }

    public List<MultipletSelection> getSelectedMultiplets() {
        List<MultipletSelection> multiplets = new ArrayList<>();
        peakListAttributesList.forEach(peakListAttr -> {
            if (peakListAttr.getDrawPeaks()) {
                Set<MultipletSelection> mSels = peakListAttr.getSelectedMultiplets();
                multiplets.addAll(mSels);
            }
        });
        return multiplets;
    }

    public void dragRegion(IntegralHit regionHit, double x, double y) {
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
        double[][] bounds = {{xPos + borders.getLeft(), xPos + width - borders.getRight()}, {yPos + borders.getTop(), yPos + height - borders.getBottom()}};
        double[][] world = getWorld();
        double[] dragPos = {x, y};
        anno.move(bounds, world, dragStart, dragPos);
        drawPeakLists(false);
    }

    public void finishAnno(double[] dragStart, double x, double y, CanvasAnnotation anno) {
        double[] dragPos = {x, y};
        anno.move(dragStart, dragPos);
    }

    public void dragPeak(double[] dragStart, double x, double y, boolean widthMode) {
        double[] dragPos = {x, y};
        for (PeakListAttributes peakListAttr : peakListAttributesList) {
            Set<Peak> peaks = peakListAttr.getSelectedPeaks();
            for (Peak peak : peaks) {
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
        double[] values = null;
        if (nRows == 1) {
            values = dataAttr.getDataset().getValues(dims[nPeakDims]);
            log.info("values {}", values);
        }
        return values;
    }

    public void fitPeakLists(int syncDim) {
        PeakFitParameters fitPars = new PeakFitParameters();
        fitPars.constrainDim(syncDim);
        AnalystApp.getShapePrefs(fitPars);
        fitPeakLists(fitPars, true);
    }

    void addPeaksUndo(Collection<Peak> peaks) {
        if (undos.isEmpty()) {
            redos.clear();
        }
        PeaksUndo undo = new PeaksUndo(peaks);
        undos.add(undo);
    }

    void addPeaksRedo(Collection<Peak> peaks) {
        PeaksUndo undo = new PeaksUndo(peaks);
        redos.add(undo);
    }

    void addPeakListUndo(PeakList peakList) {
        if (undos.isEmpty()) {
            redos.clear();
        }
        PeakListUndo undo = new PeakListUndo(peakList);
        undos.add(undo);
    }

    void addPeakListRedo(PeakList peakList) {
        PeakListUndo undo = new PeakListUndo(peakList);
        redos.add(undo);
    }

    void addUndoGroup(String label) {
        if (!undos.isEmpty() && (undos.size() == redos.size())) {
            GroupUndo groupUndo = new GroupUndo(undos);
            GroupUndo groupRedo = new GroupUndo(redos);
            undos.clear();
            redos.clear();
            controller.getUndoManager().add(label, groupUndo, groupRedo);
        }
    }

    void resetUndoGroup() {
        undos.clear();
        redos.clear();
    }

    public void fitPeakLists(PeakFitParameters fitPars, boolean getShapePars) {
        if (getShapePars) {
            AnalystApp.getShapePrefs(fitPars);
        }
        resetUndoGroup();
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
            if ((fitPars.arrayedFitMode() != PeakFitParameters.ARRAYED_FIT_MODE.SINGLE) && (fitRows.length == 0)) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Peak array fit");
                alert.setContentText("No arrayed rows or planes");
                alert.showAndWait();
                return;
            }
            double[] delays = null;
            if ((fitPars.arrayedFitMode() == PeakFitParameters.ARRAYED_FIT_MODE.EXP) ||
                    (fitPars.arrayedFitMode() == PeakFitParameters.ARRAYED_FIT_MODE.ZZ_SHAPE) || (fitPars.arrayedFitMode() == PeakFitParameters.ARRAYED_FIT_MODE.ZZ_INTENSITY)) {
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
                if ((fitPars.arrayedFitMode() == PeakFitParameters.ARRAYED_FIT_MODE.ZZ_SHAPE) || (fitPars.arrayedFitMode() == PeakFitParameters.ARRAYED_FIT_MODE.ZZ_INTENSITY)) {
                    if (peaks.size() == 1) {
                        Peak peak = peaks.stream().findFirst().get();
                        peaks = PeakLinker.getLinkedGroup(peak);
                    }
                    if (peaks.size() != 4) {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Peak ZZ fit");
                        alert.setContentText("Must select exactly 4 peaks or one linked group of 4");
                        alert.showAndWait();
                        return;
                    }
                    addPeaksUndo(peaks);
                    PeakListTools.fitZZPeaks(peakListAttr.getPeakList(), dataset, peaks, fitPars, fitRows, delays);
                    addPeaksRedo(peaks);
                } else if ((fitPars.fitMode() == PeakFitParameters.FIT_MODE.ALL) && peaks.isEmpty()) {
                    addPeaksUndo(peakListAttr.getPeakList().peaks());
                    PeakListTools.groupPeakListAndFit(peakListAttr.getPeakList(), dataset, fitRows, delays, fitPars);
                    addPeaksRedo(peakListAttr.getPeakList().peaks());
                } else if (!peaks.isEmpty()) {
                    addPeaksUndo(peaks);
                    PeakListTools.groupPeaksAndFit(peakListAttr.getPeakList(), dataset, fitRows, delays, peaks, fitPars);
                    addPeaksRedo(peaks);
                }
            } catch (IllegalArgumentException | IOException | PeakFitException ex) {
                log.error(ex.getMessage(), ex);
            }
        });
        addUndoGroup("Peaks Fit");
    }

    public void tweakPeaks() {
        resetUndoGroup();
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
                        addPeaksUndo(peaks);
                        PeakListTools.tweakPeaks(peakListAttr.getPeakList(), dataset, peaks, planes);
                        addPeaksRedo(peaks);
                    } catch (IllegalArgumentException ex) {
                        log.error(ex.getMessage(), ex);
                    }
                }
            }
        });
        addUndoGroup("Tweak Peaks");
    }

    public void tweakPeakLists() {
        resetUndoGroup();
        peakListAttributesList.forEach((peakListAttr) -> {
            DatasetBase datasetBase = peakListAttr.getDatasetAttributes().getDataset();
            Dataset dataset = datasetBase instanceof Dataset ? (Dataset) datasetBase : null;
            if (dataset != null) {
                try {
                    int nExtra = dataset.getNDim() - peakListAttr.getPeakList().nDim;
                    int[] planes = new int[nExtra];
                    addPeakListUndo(peakListAttr.getPeakList());
                    PeakListTools.tweakPeaks(peakListAttr.getPeakList(), dataset, planes);
                    addPeakListRedo(peakListAttr.getPeakList());

                } catch (IllegalArgumentException ex) {
                    log.error(ex.getMessage(), ex);
                }
            }
        });
        addUndoGroup("Tweak Peaks");

    }

    public void duplicatePeakList() {
        ChoiceDialog<PeakList> dialog = new ChoiceDialog<>();
        dialog.setTitle("Duplicate Peak List");
        dialog.setContentText("Origin List:");
        PeakList.peakLists().forEach(p -> dialog.getItems().add(p));

        Optional<PeakList> result = dialog.showAndWait();
        if (result.isPresent()) {
            PeakList peakList = result.get();
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

    public void drawPeakLists(boolean clear) {
        GraphicsContextInterface peakGC = drawingLayers.getGraphicsProxyFor(ChartDrawingLayers.Item.Peaks);
        drawPeakLists(clear, peakGC);
    }

    public void drawPeakLists(boolean clear, GraphicsContextInterface peakGC) {
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        double width = getWidth();
        double height = getHeight();

        try {
            if (peakGC instanceof GraphicsContextProxy) {
                peakGC.clearRect(xPos, yPos, width, height);
            }
            if (peakFont.getSize() != PreferencesController.getPeakFontSize()) {
                peakFont = new Font(FONT_FAMILY, PreferencesController.getPeakFontSize());
            }
            peakGC.setFont(peakFont);

            final Iterator<PeakListAttributes> peakListIterator = peakListAttributesList.iterator();
            while (peakListIterator.hasNext()) {
                PeakListAttributes peakListAttr = peakListIterator.next();
                if (peakListAttr.getPeakList().peaks() == null) {
                    peakListAttr.getPeakList().removePeakChangeListener(peakListener);
                    peakListIterator.remove();
                }
            }
            for (PeakListAttributes peakListAttr : peakListAttributesList) {
                if (clear) {
                    peakListAttr.clearPeaksInRegion();
                }
                if (peakListAttr.getDrawPeaks()) {
                    drawPeakList(peakListAttr, peakGC);
                }
            }
            if (!peakPaths.isEmpty()) {
                drawPeakPaths();
            }
            drawAnnotations(peakGC);
        } catch (Exception ioE) {
            log.warn(ioE.getMessage(), ioE);
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
        for (PeakListAttributes peakListAttr : peakListAttributesList) {
            if (peakListAttr.getDrawPeaks()) {
                hit = peakListAttr.hitPeak(drawPeaks, pickX, pickY);
                if (hit.isPresent()) {
                    break;
                }
            }
        }
        return hit;
    }

    public Optional<MultipletSelection> hitMultiplet(double pickX, double pickY) {
        Optional<MultipletSelection> hit = Optional.empty();
        for (PeakListAttributes peakListAttr : peakListAttributesList) {
            if (peakListAttr.getDrawPeaks()) {
                hit = peakListAttr.hitMultiplet(drawPeaks, pickX, pickY);
                if (hit.isPresent()) {
                    break;
                }
            }
        }
        return hit;
    }

    public void showHitPeak(double pickX, double pickY) {
        Optional<Peak> hit = hitPeak(pickX, pickY);
        if (hit.isPresent()) {
            FXMLController.showPeakAttr();
            FXMLController.getPeakAttrController().gotoPeak(hit.get());
            FXMLController.getPeakAttrController().getStage().toFront();
        }
    }

    public boolean selectPeaks(double pickX, double pickY, boolean append) {
        if (!append) {
            for (PolyChart chart : PolyChartManager.getInstance().getAllCharts()) {
                if (chart != this) {
                    boolean hadPeaks = false;
                    for (PeakListAttributes peakListAttr : chart.getPeakListAttributes()) {
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

        drawPeakLists(false);
        boolean hitPeak = false;
        for (PeakListAttributes peakListAttr : peakListAttributesList) {
            if (peakListAttr.getDrawPeaks()) {
                peakListAttr.selectPeak(drawPeaks, pickX, pickY, append);
                Set<Peak> peaks = peakListAttr.getSelectedPeaks();
                if (!peaks.isEmpty()) {
                    hitPeak = true;
                }
                if (!selectedMultiplets.isEmpty()) {
                    hitPeak = true;
                }
                drawSelectedPeaks(peakListAttr);
            }
        }
        if (controller == AnalystApp.getFXMLControllerManager().getOrCreateActiveController()) {
            List<Peak> allSelPeaks = new ArrayList<>();
            for (PolyChart chart : controller.getCharts()) {
                allSelPeaks.addAll(chart.getSelectedPeaks());
            }
            controller.selectedPeaksProperty().set(allSelPeaks);
        }
        return hitPeak;
    }

    public boolean isPeakListCompatible(PeakList peakList, boolean looseMode) {
        DatasetAttributes dataAttr = getFirstDatasetAttributes().orElse(null);
        if (dataAttr != null && dataAttr.nDim >= peakList.getNDim()) {
            int[] peakDim = getPeakDim(dataAttr, peakList, looseMode);
            return peakDim[0] != -1 && (peakDim.length == 1 || peakDim[1] != -1);
        }
        return false;
    }

    int[] getPeakDim(DatasetAttributes dataAttr, PeakList peakList, boolean looseMode) {
        int nPeakDim = peakList.nDim;
        int nDataDim = dataAttr.nDim;
        int[] dim = new int[nDataDim];
        int nMatch = 0;
        int nShouldMatch = 0;
        boolean[] used = new boolean[nPeakDim];
        int nAxes = is1D() ? 1 : axes.count();

        for (int i = 0; (i < nAxes) && (i < dim.length); i++) {
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
            for (int i = 0; (i < axes.count()) && (i < dim.length); i++) {
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
                gC.rect(xPos + borders.getLeft(), yPos + borders.getTop(), axes.getX().getWidth(), axes.getY().getHeight());
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
                        ArrayList<Peak> overlappedPeaks = new ArrayList<>();
                        for (int iPeak = 0, n = roots.size(); iPeak < n; iPeak++) {
                            Peak aPeak = roots.get(iPeak);
                            overlappedPeaks.add(aPeak);
                            for (int jPeak = (iPeak + 1); jPeak < n; jPeak++) {
                                Peak bPeak = roots.get(jPeak);
                                if (aPeak.overlaps(bPeak, 0, OVERLAP_SCALE)) {
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
        GraphicsContextInterface gC = drawingLayers.getGraphicsProxyFor(ChartDrawingLayers.Item.Peaks);
        drawSelectedPeaks(peakListAttr, gC);
    }

    public void clearPeakPaths() {
        peakPaths.clear();
    }

    public void addPeakPath(ConnectPeakAttributes peaks) {
        peakPaths.add(peaks);
    }

    void drawPeakPaths() {
        if (!peakPaths.isEmpty()) {
            GraphicsContextInterface gC = drawingLayers.getGraphicsProxyFor(ChartDrawingLayers.Item.Peaks);
            gC.save();
            gC.beginPath();
            gC.rect(getLayoutX() + borders.getLeft(), getLayoutY() + borders.getTop(), axes.getX().getWidth(), axes.getY().getHeight());
            gC.clip();
            gC.beginPath();
            peakPaths.forEach(lPeaks -> drawPeakPaths(lPeaks, gC));
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
                peaks.forEach(peak -> {
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
                multiplets.forEach(multipletSel -> {
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

    public void setLockAnno(boolean state) {
        lockAnno = state;
    }

    public Optional<CanvasAnnotation> hitAnnotation(double x, double y, boolean selectMode) {
        Optional<CanvasAnnotation> result = Optional.empty();
        if (lockAnno) {
            return result;
        }
        for (CanvasAnnotation anno : canvasAnnotations) {
            boolean alreadySelected = anno.isSelected();
            int handle = anno.hitHandle(x, y);
            if ((handle >= 0) || anno.hit(x, y, selectMode)) {
                result = Optional.of(anno);
                if (selectMode) {
                    getFXMLController().getToolController().getAnnotationController().setChart(this);
                    getFXMLController().getToolController().getAnnotationController().annotationSelected(anno);
                }
            } else {
                if (selectMode && alreadySelected) {
                    getFXMLController().getToolController().getAnnotationController().annotationDeselected(anno);
                }
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

    public boolean hasAnnoType(Class<org.nmrfx.analyst.gui.annotations.AnnoJournalFormat> annoClass) {
        return canvasAnnotations.stream().anyMatch(anno -> anno.getClass() == annoClass);
    }

    public List<CanvasAnnotation> findAnnoTypes(Class<org.nmrfx.analyst.gui.molecule.CanvasMolecule> annoClass) {
        return canvasAnnotations.stream().filter(anno -> anno.getClass() == annoClass).collect(Collectors.toList());
    }

    public void clearAnnoType(Class annoClass) {
        Iterator<CanvasAnnotation> iter = canvasAnnotations.iterator();
        while (iter.hasNext()) {
            CanvasAnnotation anno = iter.next();
            if (anno != null && anno.getClass() == annoClass) {
                iter.remove();
                if (anno == parameterText) {
                    parameterText = null;
                }
            }
        }
    }

    public List<CanvasAnnotation> getCanvasAnnotations() {
        return canvasAnnotations;
    }

    public double[][] getBounds() {
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        double width = getWidth();
        double height = getHeight();
        double[][] bounds = {{xPos + borders.getLeft(), xPos + width - borders.getRight()}, {yPos + borders.getTop(), yPos + height - borders.getBottom()}};
        return bounds;
    }

    public double[][] getWorld() {
        double x1, x2, y1, y2;
        if (axes.getX().isReversed()) {
            x1 = axes.getX().getUpperBound();
            x2 = axes.getX().getLowerBound();
        } else {
            x1 = axes.getX().getLowerBound();
            x2 = axes.getX().getUpperBound();
        }
        if (axes.getY().isReversed()) {
            y1 = axes.getY().getLowerBound();
            y2 = axes.getY().getUpperBound();
        } else {
            y1 = axes.getY().getUpperBound();
            y2 = axes.getY().getLowerBound();
        }
        return new double[][]{{x1, x2}, {y1, y2}};
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
                gC.rect(xPos, yPos, axes.getX().getWidth() + borders.getLeft() + borders.getRight(), axes.getY().getHeight() + borders.getTop() + borders.getBottom());
                gC.clip();
                gC.beginPath();
                double[][] bounds = {{xPos + borders.getLeft(), xPos + width - borders.getRight()}, {yPos + borders.getTop(), yPos + height - borders.getBottom()}};

                double[][] world = getWorld();
                boolean lastClipAxes = false;

                for (CanvasAnnotation anno : canvasAnnotations) {
                    if (anno.getClipInAxes() && !lastClipAxes) {
                        gC.save();
                        gC.rect(xPos + borders.getLeft(), yPos + borders.getTop(), axes.getX().getWidth(), axes.getY().getHeight());
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

    public double getPh0() {
        double value = 0.0;
        if (datasetPhaseDim != -1) {
            value = chartPhases[0][datasetPhaseDim];
        }
        return value;
    }

    public void setPh0(double ph0) {
        if (datasetPhaseDim != -1) {
            chartPhases[0][datasetPhaseDim] = ph0;
        }
    }

    public double getPh1() {
        double value = 0.0;
        if (datasetPhaseDim != -1) {
            value = chartPhases[1][datasetPhaseDim];
        }
        return value;
    }

    public void setPh1(double ph1) {
        if (datasetPhaseDim != -1) {
            chartPhases[1][datasetPhaseDim] = ph1;
        }
    }

    public double getPh0(int iDim) {
        int datasetDim = 0;
        if (iDim != 0) {
            datasetDim = getFirstDatasetAttributes().map(attr -> attr.dim[iDim]).orElse(0);
        }
        return chartPhases[0][datasetDim];
    }

    public double getPh1(int iDim) {
        int datasetDim = 0;
        if (iDim != 0) {
            datasetDim = getFirstDatasetAttributes().map(attr -> attr.dim[iDim]).orElse(0);
        }
        return chartPhases[1][datasetDim];
    }

    /**
     * @param pivot the pivot to set
     */
    public void setPivot(Double pivot) {
        DatasetAttributes datasetAttributes = getFirstDatasetAttributes().orElse(null);
        DatasetBase dataset = getDataset();
        if (datasetAttributes != null && dataset != null) {
            ProcessingSection section = null;
            if ((controller.getChartProcessor() != null) && controller.isProcessControllerVisible()) {
                section = controller.getChartProcessor().getCurrentProcessingSection();
            }
            if (is1D() || ((section != null) && (section.getFirstDimension() == 0))) {
                int datasetDim = datasetAttributes.dim[0];
                if (pivot == null) {
                    pivotPosition[datasetDim] = null;
                    phaseFraction = 0;
                } else {
                    int position = axes.getMode(0).getIndex(datasetAttributes, 0, pivot);
                    pivotPosition[datasetDim] = pivot;
                    int size = dataset.getSizeReal(datasetDim);
                    phaseFraction = position / (size - 1.0);
                }
            } else if (datasetPhaseDim >= 0) {
                int datasetDim = datasetAttributes.dim[phaseAxis];
                if (pivot == null) {
                    pivotPosition[datasetDim] = null;
                    phaseFraction = 0;
                } else {
                    int position = axes.getMode(phaseAxis).getIndex(datasetAttributes, phaseAxis, pivot);
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

    public void setSliceStatus(boolean state) {
        crossHairs.refresh();
    }

    public void drawSlices() {
        double xPos = getLayoutX();
        double yPos = getLayoutY();
        double width = getWidth();
        double height = getHeight();
        GraphicsContextInterface gC = drawingLayers.getGraphicsProxyFor(ChartDrawingLayers.Item.Slices);
        gC.clearRect(xPos, yPos, width, height);
        drawSlices(gC);
    }

    public void drawSlices(GraphicsContextInterface gC) {
        if (sliceAttributes.slice1StateProperty().get()) {
            drawSlice(gC, 0, VERTICAL);
            drawSlice(gC, 0, HORIZONTAL);
        }
        if (sliceAttributes.slice2StateProperty().get()) {
            drawSlice(gC, 1, VERTICAL);
            drawSlice(gC, 1, HORIZONTAL);
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

    public void drawProjection(GraphicsContextInterface gC, int iAxis, DatasetAttributes projectionDatasetAttributes) {
        getFirstDatasetAttributes().ifPresent(attr -> {
            drawSpectrum.drawProjection(projectionDatasetAttributes, attr, iAxis);
            double[][] xy = drawSpectrum.getXY();
            int nPoints = drawSpectrum.getNPoints();
            gC.setStroke(attr.getPosColor());
            gC.strokePolyline(xy[0], xy[1], nPoints);
        });
    }

    public void extractSlice(int iOrient) {
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
                        dataAttr.getSlice(sliceVec, iOrient,
                                crossHairs.getPosition(iCross, Orientation.VERTICAL),
                                crossHairs.getPosition(iCross, Orientation.HORIZONTAL));
                        Dataset sliceDataset = new Dataset(sliceVec);
                        sliceDataset.setLabel(0, dataset.getLabel(dataAttr.dim[iOrient]));
                        sliceDatasets.add(sliceDataset.getName());
                    } catch (IOException ex) {
                        log.error(ex.getMessage(), ex);
                    }
                    iSlice++;
                }
                if (!AnalystApp.getFXMLControllerManager().isRegistered(sliceController)) {
                    sliceController = AnalystApp.getFXMLControllerManager().newController();
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
                sliceController.getStatusBar().setMode(controller.getStatusBar().getMode(), controller.getStatusBar().getModeDimensions());
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

    private void drawSlice(GraphicsContextInterface gC, int iCross, int iOrient) {
        DatasetBase dataset = getDataset();
        DatasetAttributes attributes = getFirstDatasetAttributes().orElse(null);
        if (gC == null || dataset == null || attributes == null) {
            return;
        }

        int nDim = dataset.getNDim();
        boolean xOn = false;
        boolean yOn = false;
        if (controller.sliceStatusProperty().get() && sliceStatus.get()) {
            xOn = true;
            yOn = true;
        }
        int drawPivotAxis = -1;
        if (controller.isPhaseSliderVisible()) {
            if ((phaseAxis == PolyChartAxes.X_INDEX) || (nDim == 1)) {
                yOn = false;
                drawPivotAxis = 0;
            } else {
                xOn = false;
                drawPivotAxis = 1;
            }
        }
        if ((nDim > 1) && controller.sliceStatusProperty().get() && sliceStatus.get()) {
            if (((iOrient == HORIZONTAL) && xOn) || ((iOrient == VERTICAL) && yOn)) {
                for (DatasetAttributes datasetAttributes : datasetAttributesList) {
                    if (datasetAttributes.getDataset().getNDim() > 1) {
                        if (iOrient == HORIZONTAL) {
                            drawSpectrum.drawSlice(datasetAttributes, sliceAttributes, HORIZONTAL,
                                    crossHairs.getPosition(iCross, Orientation.VERTICAL),
                                    crossHairs.getPosition(iCross, Orientation.HORIZONTAL),
                                    getPh0(0), getPh1(0));
                        } else {
                            drawSpectrum.drawSlice(datasetAttributes, sliceAttributes, VERTICAL,
                                    crossHairs.getPosition(iCross, Orientation.VERTICAL),
                                    crossHairs.getPosition(iCross, Orientation.HORIZONTAL),
                                    getPh0(1), getPh1(1));
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
            int dataDim = attributes.dim[0];
            if (pivotPosition[dataDim] != null) {
                double dispPos = axes.getX().getDisplayPosition(pivotPosition[dataDim]);
                if ((dispPos > 1) && (dispPos < borders.getLeft() + axes.get(0).getWidth())) {
                    gC.setStroke(Color.GREEN);
                    gC.strokeLine(dispPos - 10, borders.getTop(), dispPos, borders.getTop() + 20);
                    gC.strokeLine(dispPos + 10, borders.getTop(), dispPos, borders.getTop() + 20);
                    gC.strokeLine(dispPos, borders.getTop() + axes.getY().getHeight() - 20, dispPos - 10, borders.getTop() + axes.getY().getHeight());
                    gC.strokeLine(dispPos, borders.getTop() + axes.getY().getHeight() - 20, dispPos + 10, borders.getTop() + axes.getY().getHeight());
                }
            }

        } else if (drawPivotAxis == 1) {
            int dataDim = attributes.dim[1];
            if (pivotPosition[dataDim] != null) {
                double dispPos = axes.getY().getDisplayPosition(pivotPosition[dataDim]);
                if ((dispPos > 1) && (dispPos < borders.getTop() + axes.getY().getHeight())) {
                    gC.setStroke(Color.GREEN);
                    gC.strokeLine(borders.getLeft(), dispPos - 10, borders.getLeft() + 20, dispPos);
                    gC.strokeLine(borders.getLeft(), dispPos + 10, borders.getLeft() + 20, dispPos);
                    gC.strokeLine(borders.getLeft() + axes.getX().getWidth(), dispPos + 10, borders.getLeft() + axes.getX().getWidth() - 20, dispPos);
                    gC.strokeLine(borders.getLeft() + axes.getX().getWidth(), dispPos - 10, borders.getLeft() + axes.getX().getWidth() - 20, dispPos);
                }
            }
        }
    }

    public void gotoMaxPlane() {
        DatasetAttributes datasetAttributes = getFirstDatasetAttributes().orElse(null);
        DatasetBase dataset = getDataset();
        if (datasetAttributes != null && dataset != null) {
            int nDim = dataset.getNDim();
            double cross1x = crossHairs.getPosition(0, Orientation.VERTICAL);
            double cross1y = crossHairs.getPosition(0, Orientation.HORIZONTAL);
            if (nDim == 3) {
                int[][] pt = new int[nDim][2];
                int[] cpt = new int[nDim];
                int[] dim = new int[nDim];
                double[] regionWidth = new double[nDim];
                for (int i = 0; i < nDim; i++) {
                    dim[i] = datasetAttributes.getDim(i);
                    switch (i) {
                        case 0 -> {
                            pt[0][0] = axes.getMode(0).getIndex(datasetAttributes, 0, cross1x);
                            pt[0][1] = pt[0][0];
                        }
                        case 1 -> {
                            pt[1][0] = axes.getMode(1).getIndex(datasetAttributes, 1, cross1y);
                            pt[1][1] = pt[1][0];
                        }
                        default -> {
                            pt[i][0] = axes.getMode(i).getIndex(datasetAttributes, i, axes.get(i).getLowerBound());
                            pt[i][1] = axes.getMode(i).getIndex(datasetAttributes, i, axes.get(i).getUpperBound());
                            if (pt[i][0] > pt[i][1]) {
                                int hold = pt[i][0];
                                pt[i][0] = pt[i][1];
                                pt[i][1] = hold;
                            }
                        }
                    }
                    cpt[i] = (pt[i][0] + pt[i][1]) / 2;
                    regionWidth[i] = Math.abs(pt[i][0] - pt[i][1]);
                }
                RegionData rData;
                try {
                    rData = dataset.analyzeRegion(pt, cpt, regionWidth, dim);
                } catch (IOException ioE) {
                    return;
                }
                int[] maxPoint = rData.getMaxPoint();
                double planeValue = axes.getMode(2).indexToValue(datasetAttributes, 2, maxPoint[2]);
                log.info("{} {} {}", rData.getMax(), maxPoint[2], planeValue);
                axes.get(2).setLowerBound(planeValue);
                axes.get(2).setUpperBound(planeValue);
            }
        }
    }

    public CrossHairs getCrossHairs() {
        return crossHairs;
    }

    DoubleFunction getCrossHairUpdateFunction(int crossHairNum, Orientation orientation) {
        return value -> {
            crossHairs.updatePosition(crossHairNum, orientation, value);
            return null;
        };
    }

    public void adjustLabels() {
        getFirstDatasetAttributes().ifPresent(attr -> {
            String[] dimNames;
            if (is1D()) {
                dimNames = new String[]{attr.getLabel(0)};
            } else {
                dimNames = new String[]{attr.getLabel(0), attr.getLabel(1)};
            }
            for (PeakListAttributes peakAttr : peakListAttributesList) {
                PeakList peakList = peakAttr.getPeakList();
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
        });
    }

    protected Optional<RegionData> analyzeFirst() {
        return getFirstDatasetAttributes().map(this::analyze);
    }

    protected RegionData analyze(DatasetAttributes dataAttr) {
        DatasetBase dataset = dataAttr.getDataset();

        int nDim = dataset.getNDim();
        int[][] pt = new int[nDim][2];
        int[] cpt = new int[nDim];
        int[] dim = new int[nDim];
        double[] regionWidth = new double[nDim];
        for (int iDim = 0; iDim < nDim; iDim++) {
            int[] limits = new int[2];
            if (iDim < 2) {
                Orientation orientation = iDim == 0 ? Orientation.VERTICAL : Orientation.HORIZONTAL;
                limits[0] = axes.getMode(iDim).getIndex(dataAttr, iDim, crossHairs.getPosition(0, orientation));
                limits[1] = axes.getMode(iDim).getIndex(dataAttr, iDim, crossHairs.getPosition(1, orientation));
            } else {
                limits[0] = axes.getMode(iDim).getIndex(dataAttr, iDim, axes.get(iDim).getLowerBound());
                limits[1] = axes.getMode(iDim).getIndex(dataAttr, iDim, axes.get(iDim).getUpperBound());
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
            regionWidth[iDim] = Math.abs(pt[iDim][0] - pt[iDim][1]);
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

    public ProcessorController getProcessorController(boolean createIfNull) {
        if ((processorController == null) && createIfNull) {
            processorController = ProcessorController.create(getFXMLController(), getFXMLController().getNmrControlRightSidePane(), this);
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
            try {
                List<Integer> borders = Arrays.asList(chartProps.getTopBorderSize(), chartProps.getRightBorderSize());
                for (int i = 0; i < borders.size(); i++) {
                    int projectionDim = i;
                    Optional<DatasetAttributes> projectionDimAttr = getDatasetAttributes().stream().filter(attr -> attr.projection() == projectionDim).findFirst();
                    if (projectionDimAttr.isPresent()) {
                        Vec projectionVec = new Vec(32, projectionDimAttr.get().getDataset().getComplex(0));
                        initialDatasetAttr.get().getProjection((Dataset) projectionDimAttr.get().getDataset(), projectionVec, projectionDim);
                        OptionalDouble maxValue = Arrays.stream(projectionVec.getReal()).max();
                        if (maxValue.isPresent()) {
                            double scaleValue = maxValue.getAsDouble() / (borders.get(i) * 0.95);
                            projectionDimAttr.get().setLvl(scaleValue);
                        }
                    }
                }
            } catch (IOException e) {
                log.warn("Unable to update projection scale. {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Updates the projection scale value by adding the scaleDelta value for the provided chart border.
     *
     * @param chartBorder Which chart border to adjust the scale for
     * @param scaleDelta  The amount to adjust the scale
     */
    public void updateProjectionScale(ChartBorder chartBorder, double scaleDelta) {
        double scalingFactor = calculateScaleYFactor(scaleDelta);
        if (chartBorder == ChartBorder.TOP) {
            Optional<DatasetAttributes> projectionAttr = getDatasetAttributes().stream().filter(attr -> attr.projection() == 0).findFirst();
            projectionAttr.ifPresent(datasetAttributes -> datasetAttributes.setLvl(Math.max(0, datasetAttributes.getLvl() * scalingFactor)));
        } else if (chartBorder == ChartBorder.RIGHT) {
            Optional<DatasetAttributes> projectionAttr = getDatasetAttributes().stream().filter(attr -> attr.projection() == 1).findFirst();
            projectionAttr.ifPresent(datasetAttributes -> datasetAttributes.setLvl(Math.max(0, datasetAttributes.getLvl() * scalingFactor)));
        }
    }

    /**
     * Remove all datasetAttributes that are projections, reset the chart borders back to the empty default
     * and refresh the chart.
     */
    public void removeProjections() {
        if (getDatasetAttributes().removeIf(DatasetAttributes::isProjection)) {
            chartProps.setTopBorderSize(ChartProperties.EMPTY_BORDER_DEFAULT_SIZE);
            chartProps.setRightBorderSize(ChartProperties.EMPTY_BORDER_DEFAULT_SIZE);
            refresh();
        }
    }

    public enum DISDIM {
        OneDX, OneDY, TwoD
    }

    public record RegionRange(double pt0, double pt1, double ppm0, double ppm1, double f0, double f1) {
    }


}
