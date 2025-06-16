package org.nmrfx.analyst.gui.molecule;

import javafx.scene.control.TableView;
import javafx.scene.paint.Color;
import org.nmrfx.analyst.gui.TablePlotGUI;
import org.nmrfx.chart.*;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.PPMv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PPMPlotGUI extends TablePlotGUI {
    AtomController atomController = null;

    public PPMPlotGUI(TableView<Atom> atomTableView, AtomController atomController) {
        super(atomTableView, null, false);
        this.atomController = atomController;
    }

    private List<DataSeries> getDeltas(List<AtomController.PPMSet> ppmSets) {
        List<DataSeries> data = new ArrayList<>();
        DataSeries dataseries = new DataSeries();

        AtomController.PPMSet set1 = ppmSets.getFirst();
        AtomController.PPMSet set2 = ppmSets.getLast();
        HashMap<Double, Double> deltas = new HashMap<>();

        atomController.atoms.stream()
                .filter(atom -> atom.getPPMByMode(set1.iSet, set1.refMode) != null &&
                        atom.getPPMByMode(set2.iSet, set2.refMode) != null)
                .forEach( atom -> {
                            double y = atom.getDeltaPPM2(set1.iSet, set2.iSet, set1.refMode, set2.refMode);
                            double x = atom.getResidueNumber();
                            deltas.put(x, deltas.getOrDefault(x, 0.0) + Math.pow(y,2.0));
                        });
        deltas.forEach((key, value) ->
            dataseries.add(new XYValue(key, Math.sqrt(value)))
        );

        dataseries.setFill(Color.DARKORANGE);
        data.add(dataseries);
        return data;
    }

    private DataSeries plotShifts(List<AtomController.PPMSet> ppmSets) {
        AtomController.PPMSet set1 = ppmSets.getFirst();
        AtomController.PPMSet set2 = ppmSets.getLast();
        DataSeries dataseries = new DataSeries();
        atomController.atoms.stream()
                .filter(atom -> atom.getPPMByMode(set1.iSet, set1.refMode) != null &&
                                atom.getPPMByMode(set2.iSet, set2.refMode) != null)
                .forEach(atom -> {
                    PPMv x = atom.getPPMByMode(set1.iSet, set1.refMode);
                    PPMv y = atom.getPPMByMode(set2.iSet, set2.refMode);
                    XYValue xyValue1 = new XYValue(x.getValue(), y.getValue());
                    dataseries.add(xyValue1);
                });
        dataseries.drawSymbol(true);
        dataseries.setFill(Color.DARKORANGE);
        return dataseries;
    }
}
