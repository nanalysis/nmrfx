package org.nmrfx.processor.gui;

import org.nmrfx.processor.gui.spectra.NMRAxis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Handles synchronization across several PolyCharts.
 */
public class PolyChartSynchronizer {
    private static final Logger log = LoggerFactory.getLogger(PolyChartSynchronizer.class);

    // use weak references to keep PolyChart instance free for the garbage collector
    record SyncGroup(List<WeakReference<PolyChart>> refs, String dimensionName) {
        public boolean contains(PolyChart chart) {
            return refs.stream().anyMatch(ref -> ref.get() == chart);
        }
    }

    // not using a Map with dimension name as a key, because the same dimension could be used for different independent groups.
    private final List<SyncGroup> groups = new ArrayList<>();

    //FIXME groups are added but never removed
    //we should add a way to remove obsolete groups or stop synchronizing them

    /**
     * Synchronize charts that share the same canvas as the entry point.
     *
     * @param chart the reference chart to use to find related charts to synchronize.
     */
    public void syncSceneMates(PolyChart chart) {
        for (String dimension : chart.getDimNames()) {
            addToSyncGroup(chart, dimension);
        }
    }

    /**
     * Synchronize axes for all charts in groups related to the reference chart. Called when the user moves a chart axis.
     *
     * @param chart     the chart whose axis have been moved
     * @param axisIndex the index of the moved axis
     * @param endNum    which axis bound has been moved
     * @param newBound  the new upper or lower bound for this axis
     */
    //TODO replace endNum with an enum
    public void syncAxes(PolyChart chart, int axisIndex, int endNum, double newBound) {
        if (groups.isEmpty()) {
            return;
        }

        String dimensionName = chart.getDimNames().get(axisIndex);
        SyncGroup group = findGroupForChartAndDimension(chart, dimensionName);
        if (group == null) {
            return;
        }

        group.refs().stream()
                .map(WeakReference::get)
                .filter(candidate -> candidate != chart) // don't apply changes to self
                .filter(Objects::nonNull) // exclude references to garbage-collected charts
                .filter(PolyChartManager.getInstance().getAllCharts()::contains) // excludes unregistered charts (even if not yet garbaged)
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

    /**
     * Add the chart to an existing group, or create a new group with all its scene mates.
     *
     * @param chart         the reference chart for a group
     * @param dimensionName the dimension for which to create a new synchronizationg group
     */
    private void addToSyncGroup(PolyChart chart, String dimensionName) {
        List<PolyChart> relatedCharts = findChartsWithSameCanvas(chart);
        SyncGroup group = findGroupForChartsAndDimension(relatedCharts, dimensionName);

        // there was already a group for these chart and dimension, remove it - it may not contain all charts
        if (group != null) {
            groups.remove(group);
        }

        // create a new group including the current chart
        List<WeakReference<PolyChart>> refs = relatedCharts.stream()
                .map(WeakReference::new)
                .toList();
        groups.add(new SyncGroup(refs, dimensionName));
    }

    @Nullable
    private SyncGroup findGroupForChartAndDimension(PolyChart chart, String dimensionName) {
        return groups.stream()
                .filter(g -> g.dimensionName().equals(dimensionName) && g.contains(chart))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private SyncGroup findGroupForChartsAndDimension(List<PolyChart> charts, String dimensionName) {
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
    private List<PolyChart> findChartsWithSameCanvas(PolyChart chart) {
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
