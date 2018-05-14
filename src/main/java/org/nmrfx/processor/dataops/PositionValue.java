package org.nmrfx.processor.dataops;

public class PositionValue {

    private final int position;
    private final double value;

    public PositionValue(final int position, final double value) {
        this.position = position;
        this.value = value;
    }

    public int getPosition() {
        return position;
    }

    public double getValue() {
        return value;
    }
}
