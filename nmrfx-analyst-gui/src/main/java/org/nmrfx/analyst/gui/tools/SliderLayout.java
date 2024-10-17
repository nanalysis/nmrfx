package org.nmrfx.analyst.gui.tools;

import javafx.stage.FileChooser;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.GUIScripter;
import org.nmrfx.processor.gui.PolyChart;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

    static void loadLayoutFromFile()  {
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
        controller.setNCharts(n);
        GUIScripter scripter = new GUIScripter();
        int i = 0;
        for (SliderLayoutChart layout: sliderLayoutTypes.getLayout()) {
            PolyChart chart = controller.getCharts().get(i++);
            scripter.grid(chart, layout.row(), layout.column(), layout.rowspan(), layout.columnspan());
            var datasetOpt = getDatasetForType(layout.type().toLowerCase());
            datasetOpt.ifPresent(d -> {
                chart.setDataset(d);
                var x = layout.x();
                chart.getAxes().setMinMax(0, x.get(0), x.get(1));
                var y = layout.y();
                chart.getAxes().setMinMax(1, y.get(0), y.get(1));
                chart.copyChartLimits();
            });
        }
    }

    Optional<DatasetBase> getDatasetForType(String type) {
        return DatasetBase.datasets().stream().filter(d -> d.getName().toLowerCase().contains(type)).findFirst();

    }

}
