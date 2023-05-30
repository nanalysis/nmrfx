package org.nmrfx.processor.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles synchronization across several PolyCharts.
 */
public class PolyChartSynchronizer {
    private static int nSyncGroups = 0;

    private final Map<String, Integer> syncGroups = new HashMap<>();
    private final PolyChart chart;

    public PolyChartSynchronizer(PolyChart chart) {
        this.chart = chart;
    }

    public static int getNSyncGroups() {
        return nSyncGroups;
    }

    public void addSync(String name, int group) {
        if (chart.getDimNames().contains(name)) {
            syncGroups.put(name, group);
        }
    }

    public int getSyncGroup(String name) {
        Integer result = syncGroups.get(name);
        return result == null ? 0 : result;
    }

    public void syncSceneMates() {
        Map<String, Integer> syncMap = new HashMap<>();
        // get sync names for this chart
        for (String name : chart.getDimNames()) {
            int iSync = getSyncGroup(name);
            if (iSync != 0) {
                syncMap.put(name, iSync);
            }
        }
        // add sync names from other charts if not already added
        for (PolyChart chart : getSceneMates()) {
            chart.getDimNames().forEach(name -> {
                int iSync = chart.getSynchronizer().getSyncGroup(name);
                if (iSync != 0) {
                    if (!syncMap.containsKey(name)) {
                        syncMap.put(name, iSync);
                    }
                }
            });
        }
        // now add new group for any missing names
        for (String name : chart.getDimNames()) {
            if (!syncMap.containsKey(name)) {
                nSyncGroups++;
                syncMap.put(name, nSyncGroups);
            }
            addSync(name, syncMap.get(name));
        }
        for (PolyChart mate : getSceneMates()) {
            for (String name : chart.getDimNames()) {
                if (mate.getDimNames().contains(name)) {
                    int group = getSyncGroup(name);
                    mate.getSynchronizer().addSync(name, group);
                }
            }
        }
    }

    /**
     * Find all charts that share the same canvas as the reference one.
     *
     * @return a list of charts sharing the same canvas.
     */
    private List<PolyChart> getSceneMates() {
        List<PolyChart> sceneMates = new ArrayList<>();
        for (PolyChart potential : PolyChartManager.getInstance().getAllCharts()) {
            if (potential != chart && potential.getCanvas() == chart.getCanvas()) {
                sceneMates.add(potential);
            }
        }
        return sceneMates;
    }
}
