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
        setChartTypeChoice(Arrays.asList("ScatterPlot", "BarChart"));
        skipColumns = Arrays.asList("Index", "Entity", "Res", "Atom");
    }

    @Override
    protected DataSeries getBarChartData(List<TableItem> items) {
        Map<String, String> nameMap = getNameMap();
        String xElem = getXElem();
        String yElem = getYElem().getFirst();
        DataSeries series = new DataSeries();
        series.clear();
        HashMap<Integer, Double> deltas = new HashMap<>();
        String atomType = ((Atom) items.getFirst()).getName();
        boolean singleAtomType = items.stream()
                .allMatch(item -> ((Atom) item).getName().equals(atomType));
        items.forEach(item -> {
            Double ppm1 = item.getDouble(nameMap.get(xElem));
            Double ppm2 = item.getDouble(nameMap.get(yElem));
            if (ppm1 != null && ppm2 != null) {
                double delta = ppm1 - ppm2;
                int resNum = ((Atom) item).getResidueNumber();
                if (!singleAtomType) {
                    deltas.put(resNum, deltas.getOrDefault(resNum, 0.0) + Math.pow(delta, 2.0));
                } else {
                    series.add(new XYValue(resNum, delta));
                }
            }
        });
        if (!singleAtomType) {
            deltas.forEach((key, value) ->
                    series.add(new XYValue(key, Math.sqrt(value))));
        }
        setYAxisLabel("Delta (ppm)");
        setXAxisLabel("Residue Number");
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
        setYAxisLabel("PPM");
        return series;
    }


}
