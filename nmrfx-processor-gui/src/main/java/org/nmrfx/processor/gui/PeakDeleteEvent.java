package org.nmrfx.processor.gui;

import org.nmrfx.peaks.Peak;

import java.util.Collection;

public class PeakDeleteEvent {
    Collection<Peak> peaks;
    PolyChart chart;

    public PeakDeleteEvent(Collection<Peak> peaks, PolyChart chart) {
        this.peaks = peaks;
        this.chart = chart;
    }

    public Collection<Peak> getPeaks() {
        return peaks;
    }
}
