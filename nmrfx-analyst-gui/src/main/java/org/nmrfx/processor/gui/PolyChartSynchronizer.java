package org.nmrfx.processor.gui;

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

    public void addSync(String name, int group) {
        //XXX we never remove sync groups?!
        //even when charts get removed apparently. Maybe that's why maps were stored in charts, but the counter would be wrong

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
        return PolyChartManager.getInstance().getAllCharts().stream()
                .filter(potential -> potential != chart)
                .filter(potential -> potential.getCanvas() == chart.getCanvas())
                .toList();
    }


    public void syncAxes(int axNum, int endNum, double newBound) {
        if (nSyncGroups <= 0) {
            return;
        }

        List<String> names = chart.getDimNames();
        String name = names.get(axNum);
        int syncGroup = chart.getSynchronizer().getSyncGroup(name);

        PolyChartManager.getInstance().getAllCharts().stream().filter((otherChart) -> (otherChart != chart)).forEach((otherChart) -> {
            List<String> otherNames = otherChart.getDimNames();
            int i = 0;
            for (String otherName : otherNames) {
                if (otherName.equals(name)) {
                    int otherGroup = otherChart.getSynchronizer().getSyncGroup(otherName);
                    if ((otherGroup > 0) && (syncGroup == otherGroup)) {
                        if (endNum == 0) {
                            otherChart.axes[i].setLowerBound(newBound);
                        } else {
                            otherChart.axes[i].setUpperBound(newBound);
                        }
                        otherChart.refresh();
                    }
                }
                i++;
            }
        });
    }
}
