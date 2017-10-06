package org.nmrfx.processor.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;
import javafx.scene.paint.Color;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.controls.ConsoleUtil;
import org.nmrfx.processor.gui.controls.FractionPane;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;

/**
 *
 * @author Bruce Johnson
 */
public class GUIScripter {

    final PolyChart useChart;

    public GUIScripter() {
        useChart = null;
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
            chart = FXMLController.getActiveController().getActiveChart();
        }
        return chart;
    }

    public String active() {
        PolyChart chart;
        if (useChart != null) {
            chart = useChart;
        } else {
            chart = FXMLController.getActiveController().getActiveChart();
        }
        return chart.getName();
    }

    public boolean active(String chartName) {
        if (useChart != null) {
            return false;
        }
        Optional<PolyChart> chartOpt = FXMLController.getActiveController().charts.stream().filter(c -> c.getName().equals(chartName)).findFirst();
        boolean success = false;
        if (chartOpt.isPresent()) {
            ConsoleUtil.runOnFxThread(() -> {
                FXMLController.getActiveController().setActiveChart(chartOpt.get());
            });
            success = true;
        }
        return success;
    }

    public void zoom(double factor) {
        ConsoleUtil.runOnFxThread(() -> {
            getChart().zoom(factor);
        });
    }

    public void full() {
        ConsoleUtil.runOnFxThread(() -> {
            getChart().full();
        });
    }

    public void expand() {
        ConsoleUtil.runOnFxThread(() -> {
            getChart().expand();
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
        });

    }

    public Map<String, Object> config() throws InterruptedException, ExecutionException {
        FutureTask<Map<String, Object>> future = new FutureTask(() -> {
            PolyChart chart = getChart();
            List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
            for (DatasetAttributes dataAttr : dataAttrs) {
                return dataAttr.config();
            }
            return new HashMap<>();

        });
        ConsoleUtil.runOnFxThread(future);
        return future.get();

    }

    public void grid(String orientName) {
        FractionPane.ORIENTATION orient = FractionPane.getOrientation(orientName);
        ConsoleUtil.runOnFxThread(() -> {
            FXMLController controller = FXMLController.getActiveController();
            controller.arrange(orient);
        });

    }

    public void grid(int nCharts, String orientName) {
        FractionPane.ORIENTATION orient = FractionPane.getOrientation(orientName);
        ConsoleUtil.runOnFxThread(() -> {
            FXMLController controller = FXMLController.activeController;
            PolyChart chart = controller.getActiveChart();
            if ((chart != null) && chart.getDataset() != null) {
                controller = FXMLController.create();
            }
            List<Dataset> datasets = new ArrayList<>();
            int nCurrent = controller.charts.size();
            for (int i = nCurrent; i < nCharts; i++) {
                controller.addChart(1);
            }
            controller.arrange(orient);
            PolyChart chartActive = controller.charts.get(0);
            controller.setActiveChart(chartActive);
        });
    }

    public void grid(List<String> datasetNames, String orientName) {
        FractionPane.ORIENTATION orient = FractionPane.getOrientation(orientName);
        ConsoleUtil.runOnFxThread(() -> {
            FXMLController controller = FXMLController.activeController;
            PolyChart chart = controller.getActiveChart();
            if ((chart != null) && chart.getDataset() != null) {
                controller = FXMLController.create();
            }
            List<Dataset> datasets = new ArrayList<>();
            int nCurrent = controller.charts.size();
            for (int i = 0; i < datasetNames.size(); i++) {
                Dataset dataset = Dataset.getDataset(datasetNames.get(i));
                datasets.add(dataset);
                if (i >= nCurrent) {
                    controller.addChart(1);
                }
            }
            controller.arrange(orient);
            for (int i = 0; i < datasets.size(); i++) {
                Dataset dataset = datasets.get(i);
                PolyChart chartActive = controller.charts.get(i);
                controller.setActiveChart(chartActive);
                controller.addDataset(dataset, false, false);
            }
        });
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

    public void datasets(List<String> datasetNames) {
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

}
