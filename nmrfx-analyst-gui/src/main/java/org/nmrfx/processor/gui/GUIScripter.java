package org.nmrfx.processor.gui;

import javafx.scene.Cursor;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.python.AnalystPythonInterpreter;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.peaks.Peak;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.annotations.AnnoPolyLine;
import org.nmrfx.processor.gui.annotations.AnnoShape;
import org.nmrfx.processor.gui.controls.ConsoleUtil;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.KeyBindings;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(GUIScripter.class);

    final PolyChart useChart;
    static FXMLController controller = FXMLController.getActiveController();
    static Map<String, String> keyActions = new HashMap<>();

    public GUIScripter() {
        useChart = null;
    }

    public static void setController(FXMLController controllerValue) {
        controllerValue.setActiveController();
        controller = controllerValue;
    }

    public static void setActiveController() {
        controller = FXMLController.getActiveController();
    }

    public static FXMLController getController() {
        if (controller == null) {
            controller = FXMLController.getActiveController();
        }
        return controller;
    }

    public static FXMLController getActiveController() {
        return FXMLController.getActiveController();
    }

    public GUIScripter(String chartName) {
        Optional<PolyChart> chartOpt = FXMLController.getActiveController().charts.stream().filter(c -> c.getName().equals(chartName)).findFirst();
        if (chartOpt.isPresent()) {
            useChart = chartOpt.get();
        } else {
            throw new IllegalArgumentException("Chart \"" + chartName + "\" doesn't exist");
        }
    }

    PolyChart getChart() {
        PolyChart chart;
        if (useChart != null) {
            chart = useChart;
        } else {
            if (getActiveController() == null) {
                try {
                    FutureTask<Boolean> future = new FutureTask(() -> {
                        newStage();
                        return true;
                    });
                    ConsoleUtil.runOnFxThread(future);
                    future.get();
                } catch (InterruptedException | ExecutionException iE) {
                    log.warn(iE.getMessage(), iE);
                }
            }
            chart = getActiveController().getActiveChart();
        }
        return chart;
    }

    public String active() {
        PolyChart chart;
        if (useChart != null) {
            chart = useChart;
        } else {
            chart = PolyChart.getActiveChart();
        }
        return chart.getName();
    }

    public boolean active(int index) throws IllegalArgumentException {
        if (useChart != null) {
            return false;
        }
        ConsoleUtil.runOnFxThread(() -> {
            List<PolyChart> charts = getActiveController().charts;
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
        ConsoleUtil.runOnFxThread(() -> {
            Optional<PolyChart> chartOpt = getActiveController().charts.stream().filter(c -> c.getName().equals(chartName)).findFirst();
            if (chartOpt.isPresent()) {
                getActiveController().setActiveChart(chartOpt.get());
            }
        });
        return success;
    }

    public void zoom(double factor) {
        ConsoleUtil.runOnFxThread(() -> {
            getChart().zoom(factor);
        });
    }

    public void full() {
        full(-1);
    }

    public void full(int dimNum) {
        ConsoleUtil.runOnFxThread(() -> {
            if (dimNum < 0) {
                getChart().full();
            } else {
                getChart().full(dimNum);
            }
        });
    }

    public void expand() {
        ConsoleUtil.runOnFxThread(() -> {
            getChart().expand();
        });
    }

    public void center(Double[] positions) {
        ConsoleUtil.runOnFxThread(() -> {
            if ((positions == null) || (positions.length == 0)) {
                Double[] crossPositions = getChart().getCrossHairs().getCrossHairPositions();
                getChart().moveTo(crossPositions);
            } else {
                getChart().moveTo(positions);
            }
        });

    }

    public double[] ppm(String axis) {
        double[] result = new double[2];
        ConsoleUtil.runOnFxThread(() -> {
            PolyChart chart = getChart();
            result[0] = chart.xAxis.getLowerBound();
            result[1] = chart.xAxis.getUpperBound();
        });
        return result;
    }

    public void ppm(String axis, double ppm1, double ppm2) {
        ConsoleUtil.runOnFxThread(() -> {
            PolyChart chart = getChart();
            chart.xAxis.setLowerBound(ppm1);
            chart.xAxis.setUpperBound(ppm2);
            chart.refresh();
        });
    }

    public void limit(String dimName, double v1, double v2) {
        ConsoleUtil.runOnFxThread(() -> {
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
        ConsoleUtil.runOnFxThread(() -> {
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
        ConsoleUtil.runOnFxThread(() -> {
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
        ConsoleUtil.runOnFxThread(() -> {
            PolyChart chart = getChart();
            chart.refresh();
        });
    }

    public void drawAll() {
        ConsoleUtil.runOnFxThread(() -> {
            getActiveController().draw();
        });
    }

    public void colorMap(String datasetName, int index, String colorName) {
        ConsoleUtil.runOnFxThread(() -> {
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
        ConsoleUtil.runOnFxThread(() -> {
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
        ConsoleUtil.runOnFxThread(() -> {
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
        ConsoleUtil.runOnFxThread(() -> {
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
        ConsoleUtil.runOnFxThread(() -> {
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
        ConsoleUtil.runOnFxThread(() -> {
            PolyChart chart = getChart();
            List<DatasetAttributes> dataAttts = chart.getDatasetAttributes();
            for (DatasetAttributes dataAttr : dataAttts) {
                if ((datasetName == null) || datasetName.equals(dataAttr.getFileName())) {
                    dataAttr.config(key, value);
                }
            }
        });
    }

    public void config(List<String> datasetNames, Map<String, Object> map) {
        ConsoleUtil.runOnFxThread(() -> {
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
                        dataAttr.config(key, value);
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
                    return dataAttr.config();
                }
            }
            return new HashMap<>();

        });
        ConsoleUtil.runOnFxThread(future);
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
        ConsoleUtil.runOnFxThread(future);
        return future.get();
    }

    public void setDims(String datasetName, int[] dims) throws InterruptedException, ExecutionException {
        ConsoleUtil.runOnFxThread(() -> {
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
                    return peakAttr.config();
                }
            }
            return new HashMap<>();

        });
        ConsoleUtil.runOnFxThread(future);
        return future.get();

    }

    public void pconfig(List<String> peakListNames, Map<String, Object> map) {
        ConsoleUtil.runOnFxThread(() -> {
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
                        peakAttr.config(key, value);
                    }
                }
            });
            chart.refresh();
        });

    }

    public void cconfig(Map<String, Object> map) {
        ConsoleUtil.runOnFxThread(() -> {
            PolyChart chart = getChart();
            map.entrySet().stream().forEach(entry -> {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key.contains("Color") && (value != null)) {
                    value = getColor(value.toString());
                }
                chart.config(key, value);

            });
            chart.refresh();
        });
    }

    public Map<String, Object> cconfig() throws InterruptedException, ExecutionException {
        FutureTask<Map<String, Object>> future = new FutureTask(() -> {
            PolyChart chart = getChart();
            return chart.config();

        });
        ConsoleUtil.runOnFxThread(future);
        return future.get();
    }

    public void sconfig(Map<String, Object> map) {
        ConsoleUtil.runOnFxThread(() -> {
            map.entrySet().stream().forEach(entry -> {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key.contains("Color") && (value != null)) {
                    value = getColor(value.toString());
                }
                getActiveController().config(key, value);

            });
            getActiveController().draw();
        });
    }

    public Map<String, Object> sconfig() throws InterruptedException, ExecutionException {
        FutureTask<Map<String, Object>> future = new FutureTask(() -> {
            return getActiveController().config();

        });
        ConsoleUtil.runOnFxThread(future);
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
        ConsoleUtil.runOnFxThread(future);
        return future.get();
    }

    public void newStage() {
        controller = FXMLController.create();
        PolyChart chartActive = controller.charts.get(0);
        controller.setActiveChart(chartActive);
    }

    public void grid(String orientName) {
        GridPaneCanvas.ORIENTATION orient = GridPaneCanvas.parseOrientationFromString(orientName);
        ConsoleUtil.runOnFxThread(() -> {
            controller.arrange(orient);
            controller.draw();
        });
    }

    public void grid(int rows, int columns) {
        int nCharts = rows * columns;
        GridPaneCanvas.ORIENTATION orient = GridPaneCanvas.parseOrientationFromString("grid");
        ConsoleUtil.runOnFxThread(() -> {
            FXMLController controller = getActiveController();
            PolyChart chart = controller.getActiveChart();
            List<Dataset> datasets = new ArrayList<>();
            controller.setNCharts(nCharts);
            controller.arrange(rows);

            PolyChart chartActive = controller.charts.get(0);
            controller.setActiveChart(chartActive);
            controller.setChartDisable(false);
            controller.draw();
        });
    }

    public void grid(int nCharts, String orientName) {
        GridPaneCanvas.ORIENTATION orient = GridPaneCanvas.parseOrientationFromString(orientName);
        ConsoleUtil.runOnFxThread(() -> {
            FXMLController controller = getActiveController();
            PolyChart chart = controller.getActiveChart();
            List<Dataset> datasets = new ArrayList<>();
            controller.setNCharts(nCharts);
            controller.arrange(orient);
            PolyChart chartActive = controller.charts.get(0);
            controller.setActiveChart(chartActive);
            controller.setChartDisable(false);
            controller.draw();
        });
    }

    public void grid(List<String> datasetNames, String orientName) {
        GridPaneCanvas.ORIENTATION orient = GridPaneCanvas.parseOrientationFromString(orientName);
        ConsoleUtil.runOnFxThread(() -> {
            FXMLController controller = getActiveController();
            PolyChart chart = controller.getActiveChart();
            List<Dataset> datasets = new ArrayList<>();
            controller.setNCharts(datasetNames.size());
            for (int i = 0; i < datasetNames.size(); i++) {
                Dataset dataset = Dataset.getDataset(datasetNames.get(i));
                datasets.add(dataset);
            }
            controller.arrange(orient);
            for (int i = 0; i < datasets.size(); i++) {
                Dataset dataset = datasets.get(i);
                PolyChart chartActive = controller.charts.get(i);
                controller.setActiveChart(chartActive);
                controller.addDataset(dataset, false, false);
            }
            controller.setChartDisable(false);
        });
    }

    public int nCharts() throws InterruptedException, ExecutionException {
        FutureTask<Integer> future = new FutureTask(() -> {
            FXMLController controller = getActiveController();
            return controller.charts.size();
        });
        ConsoleUtil.runOnFxThread(future);
        return future.get();
    }

    public List<String> datasets() throws InterruptedException, ExecutionException {
        FutureTask<List<String>> future = new FutureTask(() -> {
            PolyChart chart = getChart();
            List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
            List<String> datasetNames = dataAttrs.stream().map(d -> d.getFileName()).collect(Collectors.toList());
            return datasetNames;
        });
        ConsoleUtil.runOnFxThread(future);
        return future.get();
    }

    public void datasets(String datasetName) {
        List<String> datasetNames = new ArrayList<>();
        datasetNames.add(datasetName);
        datasets(datasetNames);
    }

    public void addDataset(Dataset dataset) {
        ConsoleUtil.runOnFxThread(() -> {
            FXMLController controller = getActiveController();
            controller.addDataset(dataset, true, false);
        });
    }

    public void openFID(String fidName) {
        ConsoleUtil.runOnFxThread(() -> {
            FXMLController controller = getActiveController();
            controller.openFile(fidName, true, false);
        });
    }

    public void datasets(List<String> datasetNames) {
        ConsoleUtil.runOnFxThread(() -> {
            PolyChart chart = getChart();
            chart.updateDatasets(datasetNames);
        });
    }

    public void gridDatasets(List<String> datasetNames) {
        ConsoleUtil.runOnFxThread(() -> {
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
        ConsoleUtil.runOnFxThread(future);
        return future.get();
    }

    public void peakLists(List<String> peakListNames) {
        ConsoleUtil.runOnFxThread(() -> {
            PolyChart chart = getChart();
            chart.updatePeakLists(peakListNames);
        });
    }

    public List<Double> geometry() throws InterruptedException, ExecutionException {
        FutureTask<List<Double>> future = new FutureTask(() -> {
            PolyChart chart = getChart();
            Stage stage = chart.getController().stage;
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
        ConsoleUtil.runOnFxThread(future);
        return future.get();
    }

    public void geometry(Double x, Double y, Double width, Double height) throws InterruptedException, ExecutionException {
        ConsoleUtil.runOnFxThread(() -> {
            PolyChart chart = getChart();
            Stage stage = chart.getController().stage;
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
        ConsoleUtil.runOnFxThread(() -> {
            FXMLController activeController = getActiveController();
            if (fileName.endsWith(".svg")) {
                activeController.exportSVG(fileName);
            } else if (fileName.endsWith(".pdf")) {
                activeController.exportPDF(fileName);
            }
        });
    }

    public static String toRGBCode(Color color) {
        return String.format("#%02X%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255),
                (int) (color.getOpacity() * 255)
        );
    }

    public static Color getColor(String colorString) {
        return Color.web(colorString);

    }

    public Cursor getCursor() throws InterruptedException, ExecutionException {
        FutureTask<Cursor> future = new FutureTask(() -> {
            return getActiveController().getActiveChart().getCanvasCursor();
        });
        ConsoleUtil.runOnFxThread(future);
        return future.get();
    }

    public void setCursor(String name) {
        Cursor cursor = Cursor.cursor(name);
        ConsoleUtil.runOnFxThread(() -> {
            getActiveController().setCursor(cursor);
        });
    }

    public void setTitle(String title) {
        ConsoleUtil.runOnFxThread(() -> {
            getActiveController().getStage().setTitle(title);
        });
    }

    public static void showPeak(String peakSpecifier) {
        ConsoleUtil.runOnFxThread(() -> {
            FXMLController activeController = getActiveController();
            activeController.refreshPeakView(peakSpecifier);
        });
    }

    public static void showPeak(Peak peak) {
        ConsoleUtil.runOnFxThread(() -> {
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
        ConsoleUtil.runOnFxThread(() -> {
            getChart().addAnnotation(shape);
            getChart().refresh();
        });

        return shape;

    }

    public void bindKeys(String keyStr, String actionStr) {
        ConsoleUtil.runOnFxThread(() -> {
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
