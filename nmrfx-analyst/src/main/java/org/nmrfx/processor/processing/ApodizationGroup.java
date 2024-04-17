package org.nmrfx.processor.processing;

import java.util.List;

public class ApodizationGroup extends ProcessingOperationGroup {
    static final List<String> opNames = List.of(
            "EXPD",
            "GM",
            "SB",
            "BLACKMAN",
            "KAISER",
            "TRI",
            "TM",
            "GMB");

    public ApodizationGroup() {
        super("Apodization");
        addOps(opNames);
    }

    public static boolean opInGroup(String opName) {
        return opNames.contains(opName);
    }
}
