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
import org.nmrfx.processor.gui.annotations.AnnoPolyLine;
import org.nmrfx.processor.gui.annotations.AnnoShape;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.KeyBindings;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.utils.properties.ColorProperty;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;

/**
 *
 * @author Bruce Johnson
 */
@PythonAPI("gscript")
public class GUIScripter {
    final PolyChart useChart;
    static FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
    static Map<String, String> keyActions = new HashMap<>();

    public GUIScripter() {
        useChart = null;
    }

    public static void setController(FXMLController controllerValue) {
        AnalystApp.getFXMLControllerManager().setActiveController(controllerValue);
        controller = controllerValue;
    }

    public static void setActiveController() {
        controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
    }

    public static FXMLController getController() {
        // controller may have been closed and unregistered without GUIScripter being notified
        if (!AnalystApp.getFXMLControllerManager().isRegistered(controller)) {
            controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
        }
        return controller;
    }

    public static FXMLController getActiveController() {
        return AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
    }

    public GUIScripter(String chartName) {
        Optional<PolyChart> chartOpt = AnalystApp.getFXMLControllerManager().getOrCreateActiveController().getCharts().stream().filter(c -> c.getName().equals(chartName)).findFirst();
        if (chartOpt.isPresent()) {
            useChart = chartOpt.get();
        } else {
            throw new IllegalArgumentException("Chart \"" + chartName + "\" doesn't exist");
        }
    }

    PolyChart getChart() {
        return useChart != null ? useChart : getActiveController().getActiveChart();
    }

    public String active() {
        PolyChart chart = useChart != null ? useChart : PolyChart.getActiveChart();
        return chart.getName();
    }

    public boolean active(int index) throws IllegalArgumentException {
        if (useChart != null) {
            return false;
        }
        // fixme throwing from fx thread
        Fx.runOnFxThread(() -> {
            List<PolyChart> charts = getActiveController().getCharts();
            if (charts.size() >= index) {
                getActiveController().setActiveChart(charts.get(index));
            } else {
                // fixme throwing from fx thread
                throw new IllegalArgumentException("Invalid chart index");
            }
        });
        return true;
    }

    public boolean active(String chartName) {
        if (useChart != null) {
            return false;
        }
        // fixme needs to be set on thread
        boolean success = true;
        Fx.runOnFxThread(() -> {
            getActiveController().getCharts().stream()
                    .filter(c -> c.getName().equals(chartName))
                    .findFirst()
                    .ifPresent(polyChart -> getActiveController().setActiveChart(polyChart));
        });
        return success;
    }

    public void zoom(double factor) {
        Fx.runOnFxThread(() -> {
            getChart().zoom(factor);
        });
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
        Fx.runOnFxThread(() -> {
            getChart().expand();
        });
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

    public double[] ppm(String axis) {
        double[] result = new double[2];
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            result[0] = chart.xAxis.getLowerBound();
            result[1] = chart.xAxis.getUpperBound();
        });
        return result;
    }

    public void ppm(String axis, double ppm1, double ppm2) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            chart.xAxis.setLowerBound(ppm1);
            chart.xAxis.setUpperBound(ppm2);
            chart.refresh();
        });
    }

    public void limit(String dimName, double v1, double v2) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            int axNum = chart.getAxisNum(dimName);
            if (v1 < v2) {
                chart.setAxis(axNum, v1, v2);
            } else {
                chart.setAxis(axNum, v2, v1);
            }
        });
    }

    public void limit(int axNum, double v1, double v2) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            if (v1 < v2) {
                chart.setAxis(axNum, v1, v2);
            } else {
                chart.setAxis(axNum, v2, v1);
            }
        });
    }

    public Map<String, List<Double>> limit() {
        Map<String, List<Double>> result = new HashMap<>();
        String dimChars = "xyzabcdefghijk";
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            int nAxes = chart.axes.length;
            for (int i = 0; i < nAxes; i++) {
                double v1 = chart.getAxis(i).getLowerBound();
                double v2 = chart.getAxis(i).getUpperBound();
                String axName = dimChars.substring(i, i + 1);
                List<Double> limits = new ArrayList<>();
                limits.add(v1);
                limits.add(v2);
                result.put(axName, limits);
            }
        });
        return result;
    }

    public void draw() {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            chart.refresh();
        });
    }

    public void drawAll() {
        Fx.runOnFxThread(() -> {
            getActiveController().draw();
        });
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
                chart.getController().getStatusBar().updateRowSpinner(indices.get(0), 1);
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
            List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
            map.entrySet().stream().forEach(entry -> {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key.contains("Color")) {
                    value = getColor(value.toString());
                }
                for (DatasetAttributes dataAttr : dataAttrs) {
                    String testName = dataAttr.getFileName();
                    if ((datasetNames == null) || datasetNames.contains(testName)) {
                        dataAttr.setPublicPropertyValue(key, value);
                    }
                }
            });
            chart.refresh();
        });

    }

    public Map<String, Object> config(List<String> datasetNames) throws InterruptedException, ExecutionException {
        final String datasetName;
        if ((datasetNames != null) && !datasetNames.isEmpty()) {
            datasetName = datasetNames.get(0);
        } else {
            datasetName = null;
        }
        FutureTask<Map<String, Object>> future = new FutureTask(() -> {
            PolyChart chart = getChart();
            List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
            for (DatasetAttributes dataAttr : dataAttrs) {
                if ((datasetName == null) || dataAttr.getFileName().equals(datasetName)) {
                    return dataAttr.getPublicPropertiesValues();
                }
            }
            return new HashMap<>();

        });
        Fx.runOnFxThread(future);
        return future.get();
    }

    public int[] getDims(String datasetName) throws InterruptedException, ExecutionException {
        FutureTask<int[]> future = new FutureTask(() -> {
            PolyChart chart = getChart();
            List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
            for (DatasetAttributes dataAttr : dataAttrs) {
                if ((datasetName == null) || dataAttr.getFileName().equals(datasetName)) {
                    return dataAttr.getDims();
                }
            }
            return new HashMap<>();

        });
        Fx.runOnFxThread(future);
        return future.get();
    }

    public void setDims(String datasetName, int[] dims) throws InterruptedException, ExecutionException {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
            for (DatasetAttributes dataAttr : dataAttrs) {
                if ((datasetName == null) || dataAttr.getFileName().equals(datasetName)) {
                    dataAttr.setDims(dims);
                    break;
                }
            }
        });
    }

    public Map<String, Object> pconfig(List<String> peakListNames) throws InterruptedException, ExecutionException {
        final String peakListName;
        if ((peakListNames != null) && !peakListNames.isEmpty()) {
            peakListName = peakListNames.get(0);
        } else {
            peakListName = null;
        }
        FutureTask<Map<String, Object>> future = new FutureTask(() -> {
            PolyChart chart = getChart();
            List<PeakListAttributes> peakAttrs = chart.getPeakListAttributes();
            for (PeakListAttributes peakAttr : peakAttrs) {
                if ((peakListName == null) || peakAttr.getPeakListName().equals(peakListName)) {
                    return peakAttr.getPublicPropertiesValues();
                }
            }
            return new HashMap<>();

        });
        Fx.runOnFxThread(future);
        return future.get();

    }

    public void pconfig(List<String> peakListNames, Map<String, Object> map) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            List<PeakListAttributes> peakAttrs = chart.getPeakListAttributes();
            map.entrySet().stream().forEach(entry -> {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key.contains("Color")) {
                    value = getColor(value.toString());
                }
                for (PeakListAttributes peakAttr : peakAttrs) {
                    String testName = peakAttr.getPeakListName();
                    if ((peakListNames == null) || peakListNames.contains(testName)) {
                        peakAttr.setPublicPropertyValue(key, value);
                    }
                }
            });
            chart.refresh();
        });

    }

    public void cconfig(Map<String, Object> map) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            map.entrySet().stream().forEach(entry -> {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key.contains("Color") && (value != null)) {
                    value = getColor(value.toString());
                }
                chart.getChartProperties().setPublicPropertyValue(key, value);

            });
            chart.refresh();
        });
    }

    public Map<String, Object> cconfig() throws InterruptedException, ExecutionException {
        FutureTask<Map<String, Object>> future = new FutureTask(() -> {
            PolyChart chart = getChart();
            return chart.getChartProperties().getPublicPropertiesValues();

        });
        Fx.runOnFxThread(future);
        return future.get();
    }

    public void sconfig(Map<String, Object> map) {
        Fx.runOnFxThread(() -> {
            map.entrySet().stream().forEach(entry -> {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key.contains("Color") && (value != null)) {
                    value = getColor(value.toString());
                }
                getActiveController().setPublicPropertyValue(key, value);

            });
            getActiveController().draw();
        });
    }

    public Map<String, Object> sconfig() throws InterruptedException, ExecutionException {
        FutureTask<Map<String, Object>> future = new FutureTask(() -> {
            return getActiveController().getPublicPropertiesValues();

        });
        Fx.runOnFxThread(future);
        return future.get();
    }

    public List<Integer> grid() throws InterruptedException, ExecutionException {
        FutureTask<List<Integer>> future = new FutureTask(() -> {
            PolyChart chart = getChart();
            int nRows = chart.getController().arrangeGetRows();
            int nColumns = chart.getController().arrangeGetColumns();
            List<Integer> result = new ArrayList<>();
            result.add(nRows);
            result.add(nColumns);
            return result;

        });
        Fx.runOnFxThread(future);
        return future.get();
    }

    public void newStage() {
        controller = AnalystApp.getFXMLControllerManager().newController();
        PolyChart chartActive = controller.getCharts().get(0);
        controller.setActiveChart(chartActive);
    }

    public void grid(String orientName) {
        GridPaneCanvas.ORIENTATION orient = GridPaneCanvas.parseOrientationFromString(orientName);
        Fx.runOnFxThread(() -> {
            controller.arrange(orient);
            controller.draw();
        });
    }

    public void grid(int rows, int columns) {
        int nCharts = rows * columns;
        GridPaneCanvas.ORIENTATION orient = GridPaneCanvas.parseOrientationFromString("grid");
        Fx.runOnFxThread(() -> {
            FXMLController controller1 = getActiveController();
            PolyChart chart = controller1.getActiveChart();
            List<Dataset> datasets = new ArrayList<>();
            controller1.setNCharts(nCharts);
            controller1.arrange(rows);

            PolyChart chartActive = controller1.getCharts().get(0);
            controller1.setActiveChart(chartActive);
            controller1.setChartDisable(false);
            controller1.draw();
        });
    }

    public void grid(int nCharts, String orientName) {
        GridPaneCanvas.ORIENTATION orient = GridPaneCanvas.parseOrientationFromString(orientName);
        Fx.runOnFxThread(() -> {
            FXMLController controller1 = getActiveController();
            PolyChart chart = controller1.getActiveChart();
            List<Dataset> datasets = new ArrayList<>();
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
            PolyChart chart = controller1.getActiveChart();
            List<Dataset> datasets = new ArrayList<>();
            controller1.setNCharts(datasetNames.size());
            for (int i = 0; i < datasetNames.size(); i++) {
                Dataset dataset = Dataset.getDataset(datasetNames.get(i));
                datasets.add(dataset);
            }
            controller1.arrange(orient);
            for (int i = 0; i < datasets.size(); i++) {
                Dataset dataset = datasets.get(i);
                PolyChart chartActive = controller1.getCharts().get(i);
                controller1.setActiveChart(chartActive);
                controller1.addDataset(dataset, false, false);
            }
            controller1.setChartDisable(false);
        });
    }

    public int nCharts() throws InterruptedException, ExecutionException {
        FutureTask<Integer> future = new FutureTask(() -> {
            FXMLController controller = getActiveController();
            return controller.getCharts().size();
        });
        Fx.runOnFxThread(future);
        return future.get();
    }

    public List<String> datasets() throws InterruptedException, ExecutionException {
        FutureTask<List<String>> future = new FutureTask(() -> {
            PolyChart chart = getChart();
            List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
            List<String> datasetNames = dataAttrs.stream().map(d -> d.getFileName()).collect(Collectors.toList());
            return datasetNames;
        });
        Fx.runOnFxThread(future);
        return future.get();
    }

    public void datasets(String datasetName) {
        List<String> datasetNames = new ArrayList<>();
        datasetNames.add(datasetName);
        datasets(datasetNames);
    }

    public void addDataset(Dataset dataset) {
        Fx.runOnFxThread(() -> {
            FXMLController controller1 = getActiveController();
            controller1.addDataset(dataset, true, false);
        });
    }

    public void openFID(String fidName) {
        Fx.runOnFxThread(() -> {
            FXMLController controller1 = getActiveController();
            controller1.openFile(fidName, true, false);
        });
    }

    public void datasets(List<String> datasetNames) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            chart.updateDatasets(datasetNames);
        });
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
        FutureTask<List<String>> future = new FutureTask(() -> {
            PolyChart chart = getChart();
            List<PeakListAttributes> peakAttrs = chart.getPeakListAttributes();
            List<String> peakListNames = peakAttrs.stream().map(p -> p.getPeakListName()).collect(Collectors.toList());
            return peakListNames;
        });
        Fx.runOnFxThread(future);
        return future.get();
    }

    public void peakLists(List<String> peakListNames) {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            chart.updatePeakLists(peakListNames);
        });
    }

    public List<Double> geometry() throws InterruptedException, ExecutionException {
        FutureTask<List<Double>> future = new FutureTask(() -> {
            PolyChart chart = getChart();
            Stage stage = chart.getController().getStage();
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
        });
        Fx.runOnFxThread(future);
        return future.get();
    }

    public void geometry(Double x, Double y, Double width, Double height) throws InterruptedException, ExecutionException {
        Fx.runOnFxThread(() -> {
            PolyChart chart = getChart();
            Stage stage = chart.getController().getStage();
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

    public static String toRGBCode(Color color) {
        return ColorProperty.toRGBCode(color);
    }

    public static Color getColor(String colorString) {
        return Color.web(colorString);

    }

    public Cursor getCursor() throws InterruptedException, ExecutionException {
        FutureTask<Cursor> future = new FutureTask(() -> {
            return getActiveController().getActiveChart().getCanvasCursor();
        });
        Fx.runOnFxThread(future);
        return future.get();
    }

    public void setCursor(String name) {
        Cursor cursor = Cursor.cursor(name);
        Fx.runOnFxThread(() -> {
            getActiveController().setCursor(cursor);
        });
    }

    public void setTitle(String title) {
        Fx.runOnFxThread(() -> {
            getActiveController().getStage().setTitle(title);
        });
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

    public List<Stage> getStages() {
        return AnalystApp.getStages();
    }

    public AnnoShape addPolyLine(List<Double> xList, List<Double> yList, String strokeName, double lineWidth) {
        Color stroke = Color.web(strokeName);
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

    public void bindKeys(String keyStr, String actionStr) {
        Fx.runOnFxThread(() -> {
            KeyBindings.registerGlobalKeyAction(keyStr, GUIScripter::chartCommand);
            keyActions.put(keyStr, actionStr);
        });
    }

    public static void chartCommand(String keyStr, PolyChart chart) {
        PolyChart currentActive = PolyChart.getActiveChart();
        chart.setActiveChart();
        AnalystPythonInterpreter.exec(keyActions.get(keyStr));
        currentActive.setActiveChart();
    }

}
