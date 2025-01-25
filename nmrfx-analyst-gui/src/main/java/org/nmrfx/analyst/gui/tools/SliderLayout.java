package org.nmrfx.analyst.gui.tools;

import javafx.stage.FileChooser;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.GUIScripter;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PolyChartManager;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SliderLayout {

    static Map<String, SliderLayoutTypes> layouts = new HashMap<>();

    static void loadLayouts() throws IOException {
        if (layouts.isEmpty()) {
            SliderLayoutGroup sliderLayoutGroup = SliderLayoutReader.loadYaml();
            for (var layout : sliderLayoutGroup.getLayouts()) {
                layouts.put(layout.getName(), layout);
            }
        }
    }

    static void loadLayoutFromFile() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                SliderLayoutGroup sliderLayoutGroup = SliderLayoutReader.loadYaml(file);
                for (var layout : sliderLayoutGroup.getLayouts()) {
                    layouts.put(layout.getName(), layout);
                }
            } catch (IOException e) {
                ExceptionDialog exceptionDialog = new ExceptionDialog(e);
                exceptionDialog.showAndWait();
            }
        }
    }

    public static Collection<String> getLayoutNames() {
        try {
            loadLayouts();
        } catch (IOException e) {
        }
        return layouts.keySet();
    }

    public void apply(String name, FXMLController controller) throws IOException {
        if (layouts.isEmpty()) {
            loadLayouts();
        }
        SliderLayoutTypes sliderLayoutTypes = layouts.get(name);
        int n = sliderLayoutTypes.getLayout().size();
        for (var chart : controller.getCharts()) {
            chart.clearDataAndPeaks();
        }
        controller.setNCharts(1);
        controller.setNCharts(n);
        controller.getCharts().getFirst().clearDataAndPeaks();
        var charts = controller.getCharts();
        GUIScripter scripter = new GUIScripter();
        int i = 0;
        for (SliderLayoutChart layout : sliderLayoutTypes.getLayout()) {
            PolyChart chart = charts.get(i++);
            scripter.grid(chart, layout.row(), layout.column(), layout.rowspan(), layout.columnspan());
        }
        i = 0;
        for (SliderLayoutChart layout : sliderLayoutTypes.getLayout()) {
            PolyChart chart = charts.get(i++);
            var datasets = getDatasetForType(layout.types());
            Boolean loadPeakLists = layout.loadpeaks();
            chart.updateDatasets(datasets);
            if (loadPeakLists == Boolean.TRUE) {
                List<PeakList> peakLists = new ArrayList<>();
                for (DatasetBase dataset : datasets) {
                    PeakList peakList = PeakList.getPeakListForDataset(dataset.getName());
                    if (peakList != null) {
                        peakLists.add(peakList);
                    }
                }
                chart.updatePeakLists(peakLists);
            }
        }
        i = 0;
        for (SliderLayoutChart layout : sliderLayoutTypes.getLayout()) {
            PolyChart chart = charts.get(i++);
            var x = layout.x();
            chart.getAxes().setMinMax(0, x.get(0), x.get(1));
            var y = layout.y();
            chart.getAxes().setMinMax(1, y.get(0), y.get(1));
            chart.copyChartLimits();
        }
        Map<String, List<PolyChart>> xsyncMap = new HashMap<>();
        Map<String, List<PolyChart>> ysyncMap = new HashMap<>();
        i = 0;
        for (SliderLayoutChart layout : sliderLayoutTypes.getLayout()) {
            PolyChart chart = charts.get(i++);
            String xsync = layout.xsync();
            addToSync(chart, xsync, xsyncMap);
            String ysync = layout.ysync();
            addToSync(chart, ysync, ysyncMap);
        }
        addSyncs(xsyncMap, 0);
        addSyncs(ysyncMap, 1);
    }

    private void addToSync(PolyChart chart, String sync, Map<String, List<PolyChart>> syncMap) {
        if ((sync != null) && !sync.isEmpty()) {
            List<PolyChart> syncList = syncMap.computeIfAbsent(sync, k -> new ArrayList<>());
            syncList.add(chart);
        }

    }

    private void addSyncs(Map<String, List<PolyChart>> syncMap, int iDim) {
        var synchronizer = PolyChartManager.getInstance().getSynchronizer();
        for (var entry : syncMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                PolyChart chart = entry.getValue().get(0);
                var dimNames = chart.getDimNames();
                if (iDim < dimNames.size()) {
                    String dimName = chart.getDimNames().get(iDim);
                    synchronizer.addToSyncGroup(entry.getValue(), dimName);
                }
            }
        }
    }

    List<Dataset> getDatasetForType(List<String> types) {
        List<Dataset> datasets = new ArrayList<>();
        for (String type : types) {
            var datasetOpt = DatasetBase.datasets().stream().filter(d -> d.getName().toLowerCase().contains(type)).findFirst();
            datasetOpt.ifPresent(d -> datasets.add((Dataset) d));
        }
        return datasets;
    }

}
