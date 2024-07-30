package org.nmrfx.processor.gui.spectra;

import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.utils.GUIUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class PeakDisplayTool {
    private static final double ND_WIDTH_SCALE = 10.0;
    private static final double ONED_WIDTH_SCALE = 25.0;

    private PeakDisplayTool() {

    }

    private static double getWidthScale(Peak peak) {
        return peak.getPeakDims().length == 1 ? ONED_WIDTH_SCALE : ND_WIDTH_SCALE;
    }

    private static boolean chartHasPeakList(PolyChart chart, PeakList peakList) {
        return chart.getPeakListAttributes().stream().map(PeakListAttributes::getPeakList).anyMatch(peakList1 -> peakList1 == peakList);
    }

    private static Optional<PolyChart> checkActiveChart(Peak peak) {
        FXMLController activeController = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
        PolyChart chart = activeController.getActiveChart();
        if ((chart != null) && (chartHasPeakList(chart, peak.getPeakList()))) {
            return Optional.of(chart);
        }
        return Optional.empty();
    }

    private static Optional<PolyChart> checkController(FXMLController controller, Peak peak) {
        Optional<PolyChart> optChart = Optional.empty();
        for (var chart : controller.getCharts()) {
            if (chartHasPeakList(chart, peak.getPeakList())) {
                optChart = Optional.of(chart);
                break;
            }
        }
        return optChart;
    }

    private static Optional<PolyChart> findChartWithPeakList(Peak peak) {
        var optChart = checkActiveChart(peak);
        if (optChart.isPresent()) {
            return optChart;
        } else {
            FXMLController activeController = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
            optChart = checkController(activeController, peak);
            if (optChart.isEmpty()) {
                for (var controller : AnalystApp.getFXMLControllerManager().getControllers()) {
                    optChart = checkController(controller, peak);
                    if (optChart.isPresent()) {
                        controller.getStage().toFront();
                        break;
                    }
                }
            }
        }
        return optChart;
    }

    public static void gotoPeak(Peak peak) {
        if (peak != null) {
            var optChart = findChartWithPeakList(peak);
            if (optChart.isPresent()) {
                gotoPeak(peak, optChart.get(), getWidthScale(peak));
            } else {
                if (GUIUtils.affirm("No chart displays that peak list, create new chart?")) {
                    createChart(peak);
                }
            }
        }
    }

    public static void gotoPeak(Peak peak, PolyChart chart, double widthScale) {
        gotoPeak(peak, List.of(chart), widthScale);
    }

    private static Set<String> getDims(PeakList peakList, List<PolyChart> charts) {
        Set<String> dimsUsed = new HashSet<>();
        int nDim = peakList.getNDim();
        for (int i = 0; i < nDim; i++) {
            String peakLabel = peakList.getSpectralDim(i).getDimName();
            boolean ok1 = true;
            for (PolyChart chart : charts) {
                if ((chart != null) && !chart.getDatasetAttributes().isEmpty()) {
                    DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                    int aDim = dataAttr.nDim;
                    boolean ok2 = false;
                    for (int j = 0; j < aDim; j++) {
                        if (dataAttr.getLabel(j).equals(peakLabel)) {
                            ok2 = true;
                            break;
                        }
                    }
                    if (!ok2) {
                        ok1 = false;
                        break;
                    }
                }
            }
            if (ok1) {
                dimsUsed.add(peakLabel);
            }
        }
        return dimsUsed;
    }

    public static void gotoPeak(Peak peak, List<PolyChart> charts, double widthScale) {
        if (peak != null) {
            PeakList peakList = peak.getPeakList();
            Set<String> dimsUsed = getDims(peakList, charts);
            for (PolyChart chart : charts) {
                if ((chart != null) && !chart.getDatasetAttributes().isEmpty()) {
                    DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                    int cDim = chart.getNDim();
                    int aDim = dataAttr.nDim;
                    Double[] ppms = new Double[cDim];
                    Double[] widths = new Double[cDim];
                    for (int i = 0; i < aDim; i++) {
                        if (!dimsUsed.contains(dataAttr.getLabel(i))) {
                            continue;
                        }
                        PeakDim peakDim = peak.getPeakDim(dataAttr.getLabel(i));
                        if (peakDim != null) {
                            double peakWidth = peakDim.getSpectralDimObj().getMeanWidthPPM();
                            ppms[i] = (double) peakDim.getChemShiftValue();
                            widths[i] = widthScale * peakWidth;
                        }
                    }
                    if (widthScale > 0.0) {
                        chart.moveTo(ppms, widths);
                    } else {
                        chart.moveTo(ppms);
                    }
                }
            }
        }
    }

    private static void createChart(Peak peak) {
        if (peak != null) {
            PeakList peakList = peak.getPeakList();
            String datasetName = peakList.getDatasetName();
            if ((datasetName != null) && !datasetName.isBlank()) {
                Dataset dataset = Dataset.getDataset(datasetName);
                if (dataset != null) {
                    FXMLController controller = AnalystApp.getFXMLControllerManager().newController();
                    PolyChart chart = controller.getActiveChart();
                    controller.addDataset(chart, dataset, false, false);
                    chart.updatePeakLists(List.of(peakList.getName()));
                    gotoPeak(peak, chart, getWidthScale(peak));
                }
            }
        }
    }
}
