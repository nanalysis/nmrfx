package org.nmrfx.processor.optimization;

import org.nmrfx.peaks.Peak;

import java.util.Map;

public record PeakFitPars(Peak peak, Map<String, FitPar> fitPars) {
    public FitPar fitPar(String name) {
        return fitPars.get(name);
    }
}
