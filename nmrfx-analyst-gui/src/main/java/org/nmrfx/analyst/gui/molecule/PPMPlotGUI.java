package org.nmrfx.analyst.gui.molecule;

import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import org.nmrfx.analyst.gui.TablePlotGUI;
import org.nmrfx.chart.*;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.utils.TableItem;

import java.util.*;

public class PPMPlotGUI extends TablePlotGUI {

    Label statusField = new Label();
    public PPMPlotGUI(TableView<Atom> atomTableView) {
        super(atomTableView, null, false);
        setChartTypeChoice(Arrays.asList("ScatterPlot", "BarChart", "BarChart-Euclidean"));
        skipColumns = Arrays.asList("Index", "Entity", "Res", "Atom");
        statusField.setPrefWidth(300);
        setBottom(statusField);
    }

    @Override
    protected DataSeries getBarChartData(List<TableItem> items, String yElem) {
        Map<String, String> nameMap = getNameMap();
        DataSeries series = new DataSeries();
        series.clear();
        series.setName(yElem);
        String xElem = getXElem();
        String firstAtomType = ((Atom) items.getFirst()).getName();
        if (!items.stream().allMatch(item ->
                Objects.equals(((Atom) item).getName(), firstAtomType))) {
            return getEuclideanDistance(items);
        }
        items.forEach(item -> {
            Double xValue = item.getDouble(nameMap.get(xElem));
            Double yValue = item.getDouble(nameMap.get(yElem));
            if (xValue != null && yValue != null) {
                if (item.getGroup() != 1) {
                    yValue /= 5.0;
                }
                XYValue xyValue = new XYValue(xValue, yValue);
                xyValue.setExtraValue(item);
                series.add(xyValue);
            }
        });
        return series;
    }

    protected DataSeries getEuclideanDistance(List<TableItem> items) {
        Map<String, String> nameMap = getNameMap();
        String xElem = getXElem();
        String yElem = getYElem().getFirst();
        DataSeries series = new DataSeries();
        series.clear();
        series.setName(yElem);
        HashMap<Integer, Double> values = new HashMap<>();
        HashMap<Integer, TableItem> plotItems = new HashMap<>();

        items.forEach(item -> {
            int xValue = item.getDouble(nameMap.get(xElem)).intValue();
            Double yValue = item.getDouble(nameMap.get(yElem));
            if (yValue != null) {
                if (item.getGroup() != 1) {
                    yValue /= 10.0;
                }
                values.put(xValue, values.getOrDefault(xValue, 0.0) + Math.pow(yValue, 2.0));
                plotItems.put(xValue, item);
            }
        });
        values.forEach((key, value) -> {
            XYValue xyValue = new XYValue(key, Math.sqrt(value));
            xyValue.setExtraValue(plotItems.get(key));
            series.add(xyValue);
        });
        return series;
    }

    @Override
    protected DataSeries getScatterPlotData(List<TableItem> items, String yElem) {
        Map<String, String> nameMap = getNameMap();
        String xElem = getXElem();
        DataSeries series = new DataSeries();
        series.clear();
        series.setName(yElem);
        items.forEach(item -> {
            Double ppm1 = item.getDouble(nameMap.get(xElem));
            Double ppm2 = item.getDouble(nameMap.get(yElem));
            if (ppm1 != null && ppm2 != null) {
                XYValue xyValue = new XYValue(ppm1, ppm2);
                xyValue.setExtraValue(item);
                series.add(xyValue);
            }
        });
        setYAxisLabel(yElem);
        return series;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        Optional<XYCanvasChart.Hit> hitOpt = activeChart.pickChart(e.getX(), e.getY(), 5);
        if (hitOpt.isPresent()) {
            XYCanvasChart.Hit hit = hitOpt.get();
            Atom atom = (Atom) hit.getValue().getExtraValue();
            statusField.setText("HIT: " + hit + " " + atom);
        } else {
            statusField.setText("");
        }
    }

}
