package org.nmrfx.processor.gui.spectra.mousehandlers;

import javafx.scene.input.MouseEvent;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.PeakPicking;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.NMRAxis;
import org.nmrfx.processor.math.Vec;

import java.util.List;
import java.util.Optional;

public class PeakPickHandler extends MouseHandler {
    MouseBindings.MOUSE_ACTION mouseAction;
    boolean singlePick = true;
    PeakList displayList = null;
    PeakList tempList = null;

    public PeakPickHandler(MouseBindings mouseBindings) {
        super(mouseBindings);
    }

    public static Optional<MouseHandler> handler(MouseBindings mouseBindings) {
        PeakPickHandler handler = new PeakPickHandler(mouseBindings);
        return Optional.of(handler);
    }


    @Override
    public void mousePressed(MouseEvent mouseEvent) {
        mouseAction = MouseBindings.MOUSE_ACTION.DRAG_PEAKPICK;
        displayList = getPeakList();
    }

    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        boolean completed = mouseBindings.getChart().finishBox(mouseAction, mouseBindings.getDragStart(), mouseBindings.getMouseX(), mouseBindings.getMouseY());
        PolyChart chart = mouseBindings.getChart();
        if (singlePick) {
            List<DatasetAttributes> activeData = chart.getActiveDatasetAttributes();
            if (activeData.size() == 1) {
                DatasetAttributes datasetAttr = activeData.get(0);
                double pickX = chart.getAxis(0).getValueForDisplay(chart.getMouseX()).doubleValue();
                double pickY = chart.getAxis(1).getValueForDisplay(chart.getMouseY()).doubleValue();
                PeakPicking.pickAtPosition(chart, datasetAttr, pickX, pickY, false, false);
                completed = true;
            }
        } else {
            double x = mouseBindings.getMouseX();
            double y = mouseBindings.getMouseY();
            double dX = Math.abs(x - mouseBindings.dragStart[0]);
            double minMove = 20;
            if ((dX > minMove) && !chart.getDatasetAttributes().isEmpty()) {
                NMRAxis xAxis = chart.getXAxis();
                NMRAxis yAxis = chart.getYAxis();
                if (chart.is1D()) {
                    double xLim0 = xAxis.getValueForDisplay(mouseBindings.dragStart[0]).doubleValue();
                    double xLim1 = xAxis.getValueForDisplay(x).doubleValue();
                    double threshold = yAxis.getValueForDisplay(y).doubleValue();
                    PeakList peakList = pick1DRegion(chart, xLim0, xLim1, threshold, false);
                    completed = peakList != null;
                } else {
                    double xLim0 = xAxis.getValueForDisplay(mouseBindings.dragStart[0]).doubleValue();
                    double xLim1 = xAxis.getValueForDisplay(x).doubleValue();
                    double yLim0 = yAxis.getValueForDisplay(mouseBindings.dragStart[1]).doubleValue();
                    double yLim1 = yAxis.getValueForDisplay(y).doubleValue();
                    double[][] region = {{xLim0, xLim1}, {yLim0, yLim1}};
                    PeakList peaklist = PeakPicking.peakPickActive(chart, chart.getDatasetAttributes().get(0),
                            region, chart.getDatasetAttributes().get(0).getLvl());
                    completed = peaklist != null;
                }
                PeakList.remove("tmpList");
            }
        }
        if (completed) {
            chart.setPeakStatus(true);
            chart.drawPeakLists(true);
        }
    }

    @Override
    public void mouseMoved(MouseEvent mouseEvent) {
        // unused in PeakPickHandler
    }

    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
        double deltaX = Math.abs(mouseBindings.getDragStart()[0] - mouseBindings.getMouseX());
        double deltaY = Math.abs(mouseBindings.getDragStart()[1] - mouseBindings.getMouseY());
        if ((deltaX > 5) || (deltaY > 5)) {
            singlePick = false;
        }
        mouseBindings.getChart().dragBox(mouseAction, mouseBindings.getDragStart(), mouseBindings.getMouseX(), mouseBindings.getMouseY());

        PolyChart chart = mouseBindings.getChart();
        if (chart.is1D()) {
            double xLim0 = chart.getAxis(0).getValueForDisplay(mouseBindings.dragStart[0]).doubleValue();
            double xLim1 = chart.getAxis(0).getValueForDisplay(mouseBindings.getMouseX()).doubleValue();
            double threshold = chart.getAxis(1).getValueForDisplay(mouseBindings.getMouseY()).doubleValue();
            PeakList peakList = pick1DRegion(chart, xLim0, xLim1, threshold, true);
            if (peakList != null) {
                chart.setPeakStatus(true);
                chart.drawPeakLists(true);
            }
        }
    }

    private PeakList getPeakList() {
        PolyChart chart = mouseBindings.getChart();
        var peakAttrs = chart.getPeakListAttributes();
        PeakList peakList = null;
        if (!peakAttrs.isEmpty()) {
            peakList = peakAttrs.get(0).getPeakList();
        }
        return peakList;
    }

    private PeakList pick1DRegion(PolyChart chart, double xLim0, double xLim1, double threshold, boolean tempMode) {
        chart.getPeakListAttributes().clear();
        String listName = null;
        if ((displayList != null) && tempMode) {
            if (tempList != null) {
                tempList.remove();
            }
            listName = displayList.getName() + ".tmp";
            tempList = displayList.copy(listName, false, false, true);
        }
        Dataset dataset = (Dataset) chart.getDatasetAttributes().get(0).getDataset();
        if (dataset.getVec() != null) {
            if (dataset.getNoiseLevel() == null) {
                Vec vec = dataset.getVec();
                double stdDev = vec.sdev(32);
                dataset.setNoiseLevel(stdDev);
            }
            threshold = Math.max(dataset.getNoiseLevel() * 10, threshold);
        }

        double[][] region = {{xLim0, xLim1}};
        PeakList peakList = PeakPicking.peakPickActive(chart, chart.getDatasetAttributes().get(0), region, false,
                false, threshold, false, listName);
        if (displayList == null) {
            displayList = peakList;
        }
        return peakList;
    }
}
