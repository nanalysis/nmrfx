package org.nmrfx.processor.gui.undo;

import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PeaksUndo extends ChartUndo {
    Collection<Peak> origPeaks;
    Map<Peak, Peak> savedPeaks;

    public PeaksUndo(Collection<Peak> peaks) {
        this.origPeaks = peaks;
        this.savedPeaks = new HashMap<>();
        for (var peak : peaks) {
            savedPeaks.put(peak, peak.copy());
            for (var peak2: PeakList.getLinks(peak)) {
                savedPeaks.put(peak2, peak2.copy());
            }
        }
    }

    @Override
    public boolean execute() {
        for (var entry : savedPeaks.entrySet()) {
            entry.getValue().copyTo(entry.getKey());
        }
        return true;
    }
}
