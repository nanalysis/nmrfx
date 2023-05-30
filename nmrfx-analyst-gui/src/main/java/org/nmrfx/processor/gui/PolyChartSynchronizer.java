package org.nmrfx.processor.gui;

import org.nmrfx.processor.gui.spectra.NMRAxis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Handles synchronization across several PolyCharts.
 */
public class PolyChartSynchronizer {
    private static final Logger log = LoggerFactory.getLogger(PolyChartSynchronizer.class);

    record SyncGroup(List<String> chartNames, String dimensionName) {
    }

    private final List<SyncGroup> groups = new ArrayList<>();

    //FIXME groups are added but never removed
    //we should add a way to remove obsolete groups or stop synchronizing them
    public void syncSceneMates(PolyChart chart) {
        for (String name : chart.getDimNames()) {
            addToSyncGroup(chart, name);
        }
    }

    //TODO replace endNum with an enum
    public void syncAxes(PolyChart chart, int axNum, int endNum, double newBound) {
        if (groups.isEmpty()) {
            return;
        }

        String dimensionName = chart.getDimNames().get(axNum);
        SyncGroup group = findGroupForChartAndDimension(chart, dimensionName);
        if (group == null) {
            return;
        }

        PolyChartManager.getInstance().getAllCharts().stream()
                .filter(candidate -> candidate != chart)
                .filter(candidate -> group.chartNames().contains(candidate.getName()))
                .forEach(toSync -> {
                    var axis = findAxis(toSync, dimensionName);
                    if (axis != null) {
                        if (endNum == 0) {
                            axis.setLowerBound(newBound);
                        } else {
                            axis.setUpperBound(newBound);
                        }
                        toSync.refresh();
                    }
                });
    }

    private void addToSyncGroup(PolyChart chart, String dimensionName) {
        List<PolyChart> sceneCharts = getSceneMates(chart);
        SyncGroup group = findGroupForChartsAndDimension(sceneCharts, dimensionName);
        if (group == null) {
            List<String> chartNames = sceneCharts.stream().map(PolyChart::getName).toList();
            groups.add(new SyncGroup(new ArrayList<>(chartNames), dimensionName));
        } else {
            group.chartNames().add(chart.getName());
        }
    }


    @Nullable
    private SyncGroup findGroupForChartAndDimension(PolyChart chart, String dimensionName) {
        return groups.stream()
                .filter(g -> g.dimensionName().equals(dimensionName) && g.chartNames().contains(chart.getName()))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private SyncGroup findGroupForChartsAndDimension(List<PolyChart> charts, String dimensionName) {
        //TODO error if more than one group found
        return charts.stream().map(chart -> findGroupForChartAndDimension(chart, dimensionName))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Find all charts that share the same canvas as the reference one.
     *
     * @return a list of charts sharing the same canvas.
     */
    private List<PolyChart> getSceneMates(PolyChart chart) {
        return PolyChartManager.getInstance().getAllCharts().stream()
                .filter(potential -> potential.getCanvas() == chart.getCanvas())
                .toList();
    }

    private NMRAxis findAxis(PolyChart chart, String dimensionName) {
        List<String> dimNames = chart.getDimNames();
        for (int i = 0; i < dimNames.size(); i++) {
            if (dimNames.get(i).equals(dimensionName)) {
                return chart.getAxis(i);
            }
        }

        log.warn("No {} axis found for chart {}!", dimensionName, chart.getName());
        return null;
    }
}
