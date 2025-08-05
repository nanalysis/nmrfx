package org.nmrfx.analyst.gui.molecule;

import javafx.scene.control.TableView;
import org.nmrfx.analyst.gui.TablePlotGUI;
import org.nmrfx.chart.*;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.utils.TableItem;

import java.util.*;

public class PPMPlotGUI extends TablePlotGUI {

    public PPMPlotGUI(TableView<Atom> atomTableView) {
        super(atomTableView, null, false);
        setChartTypeChoice(Arrays.asList("ScatterPlot", "BarChart", "BarChart-Euclidean"));
        skipColumns = Arrays.asList("Index", "Entity", "Res", "Atom");
    }

@Override
    protected DataSeries getBarChartData(List<TableItem> items, String yElem) {
        Map<String, String> nameMap = getNameMap();
        DataSeries series = new DataSeries();
        series.clear();
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
                int resNum = ((Atom) item).getResidueNumber();
                double ppm = xElem.equals("Seq") ? yValue : xValue;
                if (item.getGroup() != 1.0) {
                    ppm /= 10.0;
                }
                series.add(new XYValue(resNum, ppm));
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
        HashMap<Integer, Double> values = new HashMap<>();

        items.forEach(item -> {
            int resNum = ((Atom) item).getResidueNumber();
            Double xValue = item.getDouble(nameMap.get(xElem));
            Double yValue = item.getDouble(nameMap.get(yElem));
            if (xValue != null && yValue != null) {
                double ppm = xElem.equals("Seq") ? yValue : xValue;
                if (item.getGroup() != 1.0) {
                    ppm /= 10.0;}
                values.put(resNum, values.getOrDefault(resNum, 0.0) + Math.pow(ppm, 2.0));
            }
        });
        values.forEach((key, value) ->
                series.add(new XYValue(key, Math.sqrt(value))));
        return series;
    }

    @Override
    protected DataSeries getScatterPlotData(List<TableItem> items, String yElem) {
        Map<String, String> nameMap = getNameMap();
        String xElem = getXElem();
        DataSeries series = new DataSeries();
        series.clear();
        items.forEach(item -> {
            Double ppm1 = item.getDouble(nameMap.get(xElem));
            Double ppm2 = item.getDouble(nameMap.get(yElem));
            if (ppm1 != null && ppm2 != null) {
                series.add(new XYValue(ppm1, ppm2));
            }
        });
        setYAxisLabel(yElem);
        return series;
    }


}
