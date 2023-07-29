package org.nmrfx.processor.processing;

import java.util.List;

public class BaselineGroup extends ProcessingOperationGroup {
    static final List<String> opNames = List.of(
            "DC",
            "REGIONS",
            "AUTOREGIONS",
            "BCWHIT",
            "BCPOLY",
            "BCMED",
            "BCSINE"
            );

    public BaselineGroup() {
        super("Baseline Correction");
        addOps(opNames);
    }

    public static boolean opInGroup(String opName) {
        return opNames.contains(opName);
    }

}
