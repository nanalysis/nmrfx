package org.nmrfx.processor.datasets;

import org.codehaus.commons.nullanalysis.Nullable;

import java.util.Arrays;

public enum AcquisitionType {
    SEP("sep"),
    REAL("real"),
    COMPLEX("complex"),
    GE("ge", 1, 0, 1, 0, 1, 0, 1, 0),
    ECHO_ANTIECHO("echo-antiecho", 1, 0, -1, 0, 0, 1, 0, 1),
    ECHO_ANTIECHO_R("echo-antiecho-r", 1, 0, 1, 0, 0, 1, 0, -1),
    HYPER("hyper", 1, 0, 0, 0, 0, 0, -1, 0),
    HYPER_R("hyper-r", 1, 0, 0, 0, 0, 0, 1, 0),
    ARRAY("array");

    private final String label;
    private final double[] coefficients;

    AcquisitionType(String label, double... coefficients) {
        this.label = label;
        this.coefficients = coefficients;
    }

    public String getLabel() {
        return label;
    }

    public double[] getCoefficients() {
        return coefficients.clone();
    }

    @Override
    public String toString() {
        return label;
    }

    @Nullable
    public static AcquisitionType fromLabel(String label) {
        return Arrays.stream(AcquisitionType.values())
                .filter(type -> type.getLabel().equalsIgnoreCase(label))
                .findFirst()
                .orElse(null);
    }
}
