package org.nmrfx.processor.optimization;

import org.nmrfx.peaks.Peak;

import java.util.Map;

public record PeakFitPars(Peak peak, Map<String, FitPar> fitPars) {
    public FitPar fitPar(String name) {
        return fitPars.get(name);
    }

    public boolean hasPar(String parName) {
        return fitPars.containsKey(parName);
    }

    public boolean hasPars(String... parNames) {
        boolean ok = true;
        for (String parName:parNames) {
            if (!hasPar(parName)) {
                ok = false;
                break;
            }
        }
        return ok;
    }
}
