package org.nmrfx.processor.gui.undo;

import org.nmrfx.peaks.Peak;

public class PeakUndo extends ChartUndo {
    Peak origPeak;
    Peak savePeak;
    public PeakUndo(Peak peak) {
        this.origPeak = peak;
        this.savePeak = peak.copy();
    }
    @Override
    public boolean execute() {
        savePeak.copyTo(origPeak);
        return true;
    }
}
