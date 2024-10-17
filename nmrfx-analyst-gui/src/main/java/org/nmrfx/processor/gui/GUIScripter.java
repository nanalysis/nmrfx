package org.nmrfx.processor.gui;

import javafx.scene.Cursor;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.python.AnalystPythonInterpreter;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.peaks.Peak;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.annotations.*;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.KeyBindings;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.utils.GUIUtils;
import org.nmrfx.utils.properties.ColorProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * @author Bruce Johnson
 */
@PythonAPI("gscript")
public class GUIScripter {
    private static final Logger log = LoggerFactory.getLogger(GUIScripter.class);

    private static final String COLOR = "Color";
    private static final Map<String, String> keyActions = new HashMap<>();
    private final PolyChart useChart;

    public GUIScripter() {
        useChart = null;
    }

    public GUIScripter(String chartName) {
        Optional<PolyChart> chartOpt = AnalystApp.getFXMLControllerManager().getOrCreateActiveController().getCharts().stream().filter(c -> c.getName().equals(chartName)).findFirst();
        if (chartOpt.isPresent()) {
            useChart = chartOpt.get();
        } else {
            throw new IllegalArgumentException("Chart \"" + chartName + "\" doesn't exist");
        }
    }

    public static FXMLController getController() {
        // controller may have been closed and unregistered without GUIScripter being notified
        return AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
    }

    public static FXMLController getActiveController() {
        return AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
    }

    public static String toRGBCode(Color color) {
        return ColorProperty.toRGBCode(color);
    }

    public static void showPeak(String peakSpecifier) {
        Fx.runOnFxThread(() -> {
            FXMLController activeController = getActiveController();
            activeController.refreshPeakView(peakSpecifier);
        });
    }

    public static void showPeak(Peak peak) {
        Fx.runOnFxThread(() -> {
            FXMLController activeController = getActiveController();
            activeController.refreshPeakView(peak);
        });
    }

    /**
     * Execute a command for a specific chart. The chart will temporarily become the active one.
     *
     * @param keyStr the action key for the command to execute
     * @param chart  the chart on which the command must be executed
     */
    public static void chartCommand(String keyStr, PolyChart chart) {
        PolyChartManager chartManager = PolyChartManager.getInstance();
        PolyChart currentActive = chartManager.getActiveChart();
        try {
            chartManager.setActiveChart(chart);
            AnalystPythonInterpreter.exec(keyActions.get(keyStr));
        } finally {
            chartManager.setActiveChart(currentActive);
        }
    }

    public PolyChart getChart() {
        return useChart != null ? useChart : getActiveController().getActiveChart();
    }

    public String active() {
        PolyChart chart = useChart != null ? useChart : PolyChartManager.getInstance().getActiveChart();
        return chart.getName();
    }

    public boolean active(int index)  {
        if (useChart != null) {
            return false;
        }
        Fx.runOnFxThread(() -> {
            List<PolyChart> charts = getActiveController().getCharts();
            if (charts.size() >= index) {
                getActiveController().setActiveChart(charts.get(index));
            } else {
                log.error("Invaid chart index");
            }
        });
        return true;
    }

    public boolean active(String chartName) {
        if (useChart != null) {
            return false;
        }
        boolean success = true;
        Fx.runOnFxThread(() -> getActiveController().getCharts().stream()
                .filter(c -> c.getName().equals(chartName))
                .findFirst()
                .ifPresent(polyChart -> getActiveController().setActiveChart(polyChart)));
        return success;
    }

    public void zoom(double factor) {
        Fx.runOnFxThread(() -> getChart().zoom(factor));
    }

    public void full() {
        full(-1);
    }

    public void full(int dimNum) {
        Fx.runOnFxThread(() -> {
            if (dimNum < 0) {
                getChart().full();
            } else {
                getChart().full(dimNum);
            }
        });
    }

    public void expand() {
        Fx.runOnFxThread(() -> getChart().expand());
    }

    public void center(Double[] positions) {
        Fx.runOnFxThread(() -> {
            if ((positions == null) || (positions.length == 0)) {
                Double[] crossPositions = getChart().getCrossHairs().getPositions();
                getChart().moveTo(crossPositions);
            } else {
                getChart().moveTo(positions);
            }
        });

    }

    public void limit(String dimName, double v1, double v2) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            limitOnFx(chart, dimName, v1, v2);
        });
    }

    public void limitOnFx(PolyChart chart, String dimName, double v1, double v2) {
        int axNum = chart.getAxisNum(dimName);
        if (v1 < v2) {
            chart.getAxes().setMinMax(axNum, v1, v2);
        } else {
            chart.getAxes().setMinMax(axNum, v2, v1);
        }
    }

    public void limit(int axNum, double v1, double v2) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            if (v1 < v2) {
                chart.getAxes().setMinMax(axNum, v1, v2);
            } else {
                chart.getAxes().setMinMax(axNum, v2, v1);
            }
        });
    }

    public Map<String, List<Double>> limit() {
        Map<String, List<Double>> result = new HashMap<>();
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            result.putAll(limitOnFx(chart));
        });
        return result;
    }

    public Map<String, List<Double>> limitOnFx(PolyChart chart) {
        Map<String, List<Double>> result = new HashMap<>();
        int nAxes = chart.getAxes().count();
        String dimChars = "xyzabcdefghijk";
        for (int i = 0; i < nAxes; i++) {
            double v1 = chart.getAxes().get(i).getLowerBound();
            double v2 = chart.getAxes().get(i).getUpperBound();
            String axName = dimChars.substring(i, i + 1);
            List<Double> limits = new ArrayList<>();
            limits.add(v1);
            limits.add(v2);
            result.put(axName, limits);
        }
        return result;
    }

    public void draw() {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            chart.refresh();
        });
    }

    public void drawAll() {
        Fx.runOnFxThread(() -> getActiveController().draw());
    }

    public void colorMap(String datasetName, int index, String colorName) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            List<DatasetAttributes> dataAttts = chart.getDatasetAttributes();
            for (DatasetAttributes dataAttr : dataAttts) {
                if ((datasetName == null) || datasetName.equals(dataAttr.getFileName())) {
                    dataAttr.setMapColor(index, colorName);
                }
            }
        });
    }

    public void colorMap(String datasetName, List<Integer> indices, String colorName) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            List<DatasetAttributes> dataAttts = chart.getDatasetAttributes();
            for (DatasetAttributes dataAttr : dataAttts) {
                if ((datasetName == null) || datasetName.equals(dataAttr.getFileName())) {
                    for (int index : indices) {
                        dataAttr.setMapColor(index, colorName);
                    }
                }
            }
        });
    }

    public void offsetMap(String datasetName, int index, double offset) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            List<DatasetAttributes> dataAttts = chart.getDatasetAttributes();
            for (DatasetAttributes dataAttr : dataAttts) {
                if ((datasetName == null) || datasetName.equals(dataAttr.getFileName())) {
                    dataAttr.setMapOffset(index, offset);
                }
            }
        });
    }

    public void offsetMap(String datasetName, List<Integer> indices, double offset) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            List<DatasetAttributes> dataAttts = chart.getDatasetAttributes();
            for (DatasetAttributes dataAttr : dataAttts) {
                if ((datasetName == null) || datasetName.equals(dataAttr.getFileName())) {
                    for (int index : indices) {
                        dataAttr.setMapOffset(index, offset);
                    }
                }
            }
        });
    }

    public void rows(String datasetName, List<Integer> indices) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            List<DatasetAttributes> dataAttts = chart.getDatasetAttributes();
            for (DatasetAttributes dataAttr : dataAttts) {
                if ((datasetName == null) || datasetName.equals(dataAttr.getFileName())) {
                    dataAttr.setDrawList(indices);
                }
            }
            if (!indices.isEmpty()) {
                chart.getFXMLController().getStatusBar().updateRowSpinner(indices.get(0), 1);
            }
            chart.refresh();
        });
    }

    public void config(String datasetName, String key, Object value) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            List<DatasetAttributes> dataAttts = chart.getDatasetAttributes();
            for (DatasetAttributes dataAttr : dataAttts) {
                if ((datasetName == null) || datasetName.equals(dataAttr.getFileName())) {
                    dataAttr.setPublicPropertyValue(key, value);
                }
            }
        });
    }

    public void config(List<String> datasetNames, Map<String, Object> map) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            configOnFx(chart, datasetNames, map);
        });

    }

    public void configOnFx(PolyChart chart, List<String> datasetNames, Map<String, Object> map) {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        map.forEach((key, value) -> {
            if (key.contains(COLOR)) {
                value = GUIUtils.getColor(value.toString());
            }
            for (DatasetAttributes dataAttr : dataAttrs) {
                String testName = dataAttr.getFileName();
                if ((datasetNames == null) || datasetNames.contains(testName)) {
                    dataAttr.setPublicPropertyValue(key, value);
                }
            }
        });
        chart.refresh();

    }

    public Map<String, Object> config(List<String> datasetNames) throws InterruptedException, ExecutionException {
        final String datasetName;
        if ((datasetNames != null) && !datasetNames.isEmpty()) {
            datasetName = datasetNames.get(0);
        } else {
            datasetName = null;
        }
        return Fx.runOnFxThreadAndWait(() -> {
            PolyChart chart = getChart();
            List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
            for (DatasetAttributes dataAttr : dataAttrs) {
                if ((datasetName == null) || dataAttr.getFileName().equals(datasetName)) {
                    return dataAttr.getPublicPropertiesValues();
                }
            }
            return new HashMap<>();
        });
    }

    public Map<String, Object> configOnFx(PolyChart chart, String datasetName) {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        for (DatasetAttributes dataAttr : dataAttrs) {
            if ((datasetName == null) || dataAttr.getFileName().equals(datasetName)) {
                return dataAttr.getPublicPropertiesValues();
            }
        }
        return new HashMap<>();
    }

    public int[] getDims(String datasetName) throws InterruptedException, ExecutionException {
        return Fx.runOnFxThreadAndWait(() -> getDimsOnFx(getChart(), datasetName));
    }

    public int[] getDimsOnFx(PolyChart chart, String datasetName) {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        for (DatasetAttributes dataAttr : dataAttrs) {
            if ((datasetName == null) || dataAttr.getFileName().equals(datasetName)) {
                return dataAttr.getDims();
            }
        }
        return new int[0];
    }

    public void setDims(String datasetName, int[] dims) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            setDimsOnFx(chart, datasetName, dims);
        });
    }

    public void setDimsOnFx(PolyChart chart, String datasetName, int[] dims) {
        List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
        for (DatasetAttributes dataAttr : dataAttrs) {
            if ((datasetName == null) || dataAttr.getFileName().equals(datasetName)) {
                dataAttr.setDims(dims);
                break;
            }
        }
    }

    public Map<String, Object> pconfig(List<String> peakListNames) throws InterruptedException, ExecutionException {
        final String peakListName;
        if ((peakListNames != null) && !peakListNames.isEmpty()) {
            peakListName = peakListNames.get(0);
        } else {
            peakListName = null;
        }
        return Fx.runOnFxThreadAndWait(() -> {
            PolyChart chart = getChart();
            List<PeakListAttributes> peakAttrs = chart.getPeakListAttributes();
            for (PeakListAttributes peakAttr : peakAttrs) {
                if ((peakListName == null) || peakAttr.getPeakListName().equals(peakListName)) {
                    return peakAttr.getPublicPropertiesValues();
                }
            }
            return new HashMap<>();

        });
    }

    public Map<String, Object> pconfigOnFx(PolyChart chart, String peakListName) {
        List<PeakListAttributes> peakAttrs = chart.getPeakListAttributes();
        for (PeakListAttributes peakAttr : peakAttrs) {
            if ((peakListName == null) || peakAttr.getPeakListName().equals(peakListName)) {
                return peakAttr.getPublicPropertiesValues();
            }
        }
        return new HashMap<>();
    }

    public void pconfig(List<String> peakListNames, Map<String, Object> map) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            pconfigOnFx(chart, peakListNames, map);
        });
    }

    public void pconfigOnFx(PolyChart chart, List<String> peakListNames, Map<String, Object> map) {
        List<PeakListAttributes> peakAttrs = chart.getPeakListAttributes();
        map.forEach((key, value) -> {
            if (key.contains(COLOR)) {
                value = GUIUtils.getColor(value.toString());
            }
            if (key.equals("peakLabelType")) {  // some .yaml files were incorrectly written with peakLabelType
                key = "labelType";
            }
            for (PeakListAttributes peakAttr : peakAttrs) {
                String testName = peakAttr.getPeakListName();
                if ((peakListNames == null) || peakListNames.contains(testName)) {
                    peakAttr.setPublicPropertyValue(key, value);
                }
            }
        });
        chart.refresh();
    }

    public void cconfig(Map<String, Object> map) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            cconfigOnFx(chart, map);
        });
    }

    public void cconfigOnFx(PolyChart chart, Map<String, Object> map) {
        map.forEach((key, value) -> {
            if (key.contains(COLOR) && (value != null)) {
                value = GUIUtils.getColor(value.toString());
            }
            chart.getChartProperties().setPublicPropertyValue(key, value);
        });
        chart.refresh();
    }

    public Map<String, Object> cconfig() throws InterruptedException, ExecutionException {
        return Fx.runOnFxThreadAndWait(() -> getChart().getChartProperties().getPublicPropertiesValues());
    }

    public void sconfig(Map<String, Object> map) {
        Fx.runOnFxThread(() -> sconfigOnFx(getActiveController(), map));
    }

    public void sconfigOnFx(FXMLController controller, Map<String, Object> map) {
        map.forEach((key, value) -> {
            if (key.contains(COLOR) && (value != null)) {
                value = GUIUtils.getColor(value.toString());
            }
            controller.setPublicPropertyValue(key, value);

        });
        controller.draw();
    }

    public Map<String, Object> sconfig() throws InterruptedException, ExecutionException {
        return Fx.runOnFxThreadAndWait(() -> getActiveController().getPublicPropertiesValues());
    }

    public String getAnnotations() throws InterruptedException, ExecutionException {
        return Fx.runOnFxThreadAndWait(() -> {
            PolyChart chart = getChart();
            List<CanvasAnnotation> annoTypes = chart.getCanvasAnnotations();
            Yaml yaml = new Yaml();
            return yaml.dump(annoTypes);
        });
    }

    public void loadAnnotations(String yamlData) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            loadAnnotationsOnFx(chart, yamlData);
            getActiveController().draw();
        });
    }

    public void loadAnnotationsOnFx(PolyChart chart, String yamlData) {
        try (InputStream stream = new ByteArrayInputStream(yamlData.getBytes())) {
            Yaml yaml = new Yaml();
            List<CanvasAnnotation> annoTypes = (List<CanvasAnnotation>) yaml.load(stream);
            for (CanvasAnnotation annoType : annoTypes) {
                chart.addAnnotation(annoType);
            }
        } catch (IOException e) {
            log.error("Error loading annotations", e);
        }
    }

    public List<Integer> grid() throws InterruptedException, ExecutionException {
        return Fx.runOnFxThreadAndWait(() -> gridOnFx(getActiveController()));
    }

    public List<Integer> gridOnFx(FXMLController controller) {
        int nRows = controller.arrangeGetRows();
        int nColumns = controller.arrangeGetColumns();
        List<Integer> result = new ArrayList<>();
        result.add(nRows);
        result.add(nColumns);
        return result;
    }

    public void newStage(String title) {
        Fx.runOnFxThread(() -> {
            FXMLController controller = AnalystApp.getFXMLControllerManager().newController(title);
            PolyChart chartActive = controller.getCharts().get(0);
            controller.setActiveChart(chartActive);
        });
    }

    public void grid(String orientName) {
        GridPaneCanvas.ORIENTATION orient = GridPaneCanvas.parseOrientationFromString(orientName);
        Fx.runOnFxThread(() -> {
            var controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
            controller.arrange(orient);
            controller.draw();
        });
    }

    public void grid(int rows, int columns) {
        Fx.runOnFxThread(() -> {
            FXMLController controller1 = getActiveController();
            gridOnFx(controller1, rows, columns, rows * columns);
        });
    }

    public void gridOnFx(FXMLController controller, int rows, int columns, int nCharts) {
        controller.setNCharts(nCharts);
        controller.arrange(rows);

        PolyChart chartActive = controller.getCharts().get(0);
        controller.setActiveChart(chartActive);
        controller.setChartDisable(false);
        controller.draw();
    }

    public void grid(int nCharts, String orientName) {
        GridPaneCanvas.ORIENTATION orient = GridPaneCanvas.parseOrientationFromString(orientName);
        Fx.runOnFxThread(() -> {
            FXMLController controller1 = getActiveController();
            controller1.setNCharts(nCharts);
            controller1.arrange(orient);
            PolyChart chartActive = controller1.getCharts().get(0);
            controller1.setActiveChart(chartActive);
            controller1.setChartDisable(false);
            controller1.draw();
        });
    }

    public void grid(List<String> datasetNames, String orientName) {
        GridPaneCanvas.ORIENTATION orient = GridPaneCanvas.parseOrientationFromString(orientName);
        Fx.runOnFxThread(() -> {
            FXMLController controller1 = getActiveController();
            List<Dataset> datasets = new ArrayList<>();
            controller1.setNCharts(datasetNames.size());
            for (String datasetName : datasetNames) {
                Dataset dataset = Dataset.getDataset(datasetName);
                datasets.add(dataset);
            }
            controller1.arrange(orient);
            for (int i = 0; i < datasets.size(); i++) {
                Dataset dataset = datasets.get(i);
                PolyChart chartActive = controller1.getCharts().get(i);
                controller1.setActiveChart(chartActive);
                controller1.addDataset(chartActive, dataset, false, false);
            }
            controller1.setChartDisable(false);
        });
    }

    public void grid(PolyChart chart, int row, int column, int rowSpan, int columnSpan) {
        Fx.runOnFxThread(() -> {
            FXMLController controller1 = getActiveController();
            controller1.setChartDisable(true);
            GridPaneCanvas gridPaneCanvas = controller1.getGridPaneCanvas();
            gridPaneCanvas.setPosition(chart, row, column, rowSpan, columnSpan);
            controller1.setChartDisable(false);
            controller1.draw();
        });
    }


    public int nCharts() throws InterruptedException, ExecutionException {
        return Fx.runOnFxThreadAndWait(() -> {
            FXMLController controller = getActiveController();
            return controller.getCharts().size();
        });
    }

    public List<String> datasets() throws InterruptedException, ExecutionException {
        return Fx.runOnFxThreadAndWait(() -> {
            PolyChart chart = getChart();
            List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
            return dataAttrs.stream().map(DatasetAttributes::getFileName).toList();
        });
    }

    public void datasets(String datasetName) {
        List<String> datasetNames = new ArrayList<>();
        datasetNames.add(datasetName);
        datasets(datasetNames);
    }

    public void addDataset(Dataset dataset) {
        Fx.runOnFxThread(() -> {
            FXMLController controller1 = getActiveController();
            controller1.addDataset(controller1.getActiveChart(), dataset, true, false);
        });
    }

    public void openFID(String fidName) {
        Fx.runOnFxThread(() -> {
            FXMLController controller1 = getActiveController();
            controller1.openFile(fidName, true, false);
        });
    }

    public void datasets(List<String> datasetNames) {
        Fx.runOnFxThread(() -> getChart().updateDatasets(datasetNames));
    }

    public void gridDatasets(List<String> datasetNames) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
            if (dataAttrs.size() != datasetNames.size()) {
                throw new IllegalArgumentException("Number of datasets not equal to number of charts");

            }
            for (int i = 0; i < dataAttrs.size(); i++) {
                Dataset dataset = Dataset.getDataset(datasetNames.get(i));
                if (dataset == null) {
                    throw new IllegalArgumentException("Dataset \"" + datasetNames.get(i) + "\" doesn't exist");
                }
                dataAttrs.get(i).setDataset(dataset);
            }
        });
    }

    public List<String> peakLists() throws InterruptedException, ExecutionException {
        return Fx.runOnFxThreadAndWait(() -> {
            PolyChart chart = getChart();
            return peakListsOnFx(chart);
        });
    }

    public List<String> peakListsOnFx(PolyChart chart) {
        List<PeakListAttributes> peakAttrs = chart.getPeakListAttributes();
        return peakAttrs.stream().map(PeakListAttributes::getPeakListName).toList();
    }

    public void peakLists(List<String> peakListNames) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            chart.updatePeakListsByName(peakListNames);
        });
    }

    public List<Double> geometry() throws InterruptedException, ExecutionException {
        return Fx.runOnFxThreadAndWait(() -> geometryOnFx(getActiveController()));
    }

    public List<Double> geometryOnFx(FXMLController controller) {
        Stage stage = controller.getStage();
        double x = stage.getX();
        double y = stage.getY();
        double width = stage.getWidth();
        double height = stage.getHeight();
        List<Double> result = new ArrayList<>();
        result.add(x);
        result.add(y);
        result.add(width);
        result.add(height);
        return result;
    }

    public void geometry(Double x, Double y, Double width, Double height)  {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            Stage stage = chart.getFXMLController().getStage();
            if (x != null) {
                stage.setX(x);
            }
            if (y != null) {
                stage.setY(y);
            }
            if (width != null) {
                stage.setWidth(width);
            }
            if (height != null) {
                stage.setHeight(height);
            }
        });
    }

    public void geometryOnFx(Stage stage, List<Double> values) {
        Double x = values.get(0);
        Double y = values.get(1);
        Double width = values.get(2);
        Double height = values.get(3);
        if (x != null) {
            stage.setX(x);
        }
        if (y != null) {
            stage.setY(y);
        }
        if (width != null) {
            stage.setWidth(width);
        }
        if (height != null) {
            stage.setHeight(height);
        }

    }

    public void export(String fileName) {
        Fx.runOnFxThread(() -> {
            FXMLController activeController = getActiveController();
            if (fileName.endsWith(".svg")) {
                activeController.exportSVG(fileName);
            } else if (fileName.endsWith(".pdf")) {
                activeController.exportPDF(fileName);
            }
        });
    }

    public Cursor getCursor() throws InterruptedException, ExecutionException {
        return Fx.runOnFxThreadAndWait(getActiveController().getActiveChart()::getCanvasCursor);

    }

    public void setCursor(String name) {
        Cursor cursor = Cursor.cursor(name);
        Fx.runOnFxThread(() -> getActiveController().setCursor(cursor));
    }

    public void setTitle(String title) {
        Fx.runOnFxThread(() -> getActiveController().getStage().setTitle(title));
    }

    public List<Stage> getStages() {
        return AnalystApp.getStages();
    }


    public AnnoShape addPolyLine(List<Double> xList, List<Double> yList, String strokeName, Double lineWidth) {
        Color stroke = GUIUtils.getColor(strokeName);
        AnnoShape shape = new AnnoPolyLine(xList, yList,
                CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.WORLD);
        shape.setStroke(stroke);
        shape.setLineWidth(lineWidth);
        Fx.runOnFxThread(() -> {
            getChart().addAnnotation(shape);
            getChart().refresh();
        });

        return shape;

    }

    public AnnoShape addRectangle(Double x1, Double y1, Double x2, Double y2, String strokeName, String fillName, Double lineWidth) {
        Color stroke = GUIUtils.getColor(strokeName);
        Color fill = GUIUtils.getColor(fillName);
        AnnoShape shape = new AnnoRectangle(x1, y1, x2, y2,
                CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.WORLD);
        shape.setStroke(stroke);
        shape.setFill(fill);
        shape.setLineWidth(lineWidth);
        Fx.runOnFxThread(() -> {
            getChart().addAnnotation(shape);
            getChart().refresh();
        });

        return shape;

    }

    public AnnoShape addOval(Double x1, Double y1, Double x2, Double y2, String strokeName, String fillName, Double lineWidth) {
        Color stroke = GUIUtils.getColor(strokeName);
        Color fill = GUIUtils.getColor(fillName);
        AnnoShape shape = new AnnoOval(x1, y1, x2, y2,
                CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.WORLD);
        shape.setStroke(stroke);
        shape.setFill(fill);
        shape.setLineWidth(lineWidth);
        Fx.runOnFxThread(() -> {
            getChart().addAnnotation(shape);
            getChart().refresh();
        });

        return shape;

    }

    public AnnoShape addPolygon(List<Double> xList, List<Double> yList, String strokeName, String fillName, Double lineWidth) {
        Color stroke = GUIUtils.getColor(strokeName);
        Color fill = GUIUtils.getColor(fillName);
        AnnoShape shape = new AnnoPolygon(xList, yList,
                CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.WORLD);
        shape.setStroke(stroke);
        shape.setFill(fill);
        shape.setLineWidth(lineWidth);
        Fx.runOnFxThread(() -> {
            getChart().addAnnotation(shape);
            getChart().refresh();
        });

        return shape;

    }

    public AnnoShape addArrowLine(Double x1, Double y1, Double x2, Double y2, Boolean arrowFirst, Boolean arrowLast, String strokeName, String fillName, Double lineWidth) {
        Color stroke = GUIUtils.getColor(strokeName);
        Color fill = GUIUtils.getColor(fillName);
        AnnoShape shape = new AnnoLine(x1, y1, x2, y2, arrowFirst, arrowLast, lineWidth,
                CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.WORLD);
        shape.setStroke(stroke);
        shape.setFill(fill);
        shape.setLineWidth(lineWidth);
        Fx.runOnFxThread(() -> {
            getChart().addAnnotation(shape);
            getChart().refresh();
        });

        return shape;

    }

    public AnnoShape addLineText(Double x1, Double y1, Double x2, Double y2, String text, Double fontSize, String strokeName, String fillName, Double lineWidth) {
        Color stroke = GUIUtils.getColor(strokeName);
        Color fill = GUIUtils.getColor(fillName);
        AnnoShape shape = new AnnoLineText(x1, y1, x2, y2, text, fontSize, lineWidth,
                CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.WORLD);
        shape.setStroke(stroke);
        shape.setFill(fill);
        shape.setLineWidth(lineWidth);
        Fx.runOnFxThread(() -> {
            getChart().addAnnotation(shape);
            getChart().refresh();
        });

        return shape;

    }

    public AnnoText addText(Double x1, Double y1, Double x2, Double y2, String text, Double fontSize) {
        double width = text.length() * fontSize;
        AnnoText annoText = new AnnoText(x1, y1, width, text, fontSize,
                CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.WORLD);
        Fx.runOnFxThread(() -> {
            getChart().addAnnotation(annoText);
            getChart().refresh();
        });

        return annoText;

    }

    public void bindKeys(String keyStr, String actionStr) {
        Fx.runOnFxThread(() -> {
            KeyBindings.registerGlobalKeyAction(keyStr, GUIScripter::chartCommand);
            keyActions.put(keyStr, actionStr);
        });
    }

}
