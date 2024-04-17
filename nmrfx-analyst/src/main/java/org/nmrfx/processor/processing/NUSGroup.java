package org.nmrfx.processor.processing;

import java.util.List;

public class NUSGroup extends ProcessingOperationGroup {
    static final List<String> opNames = List.of(
            "NESTA",
            "GRINS",
            "IST"
            );

    public NUSGroup() {
        super("Non-Uniform Sampling Methods");
        addOps(opNames);
    }

    public static boolean opInGroup(String opName) {
        return opNames.contains(opName);
    }

}
