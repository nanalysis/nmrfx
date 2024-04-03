package org.nmrfx.processor.gui.undo;

import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;

import java.util.*;

public class PeakListUndo extends ChartUndo {

    PeakList peakList;
    List<Peak> savedPeaks = new ArrayList<>();

    public PeakListUndo(PeakList peakList) {
        this.peakList = peakList;
        for (var peak : peakList.peaks()) {
            savedPeaks.add(peak.copy());
        }
    }

    @Override
    public boolean execute() {
        peakList.peaks().clear();
        for (Peak peak:savedPeaks) {
            peak.peakList = peakList;
            peakList.peaks().add(peak);
        }
        return true;
    }
}
