package org.nmrfx.processor.gui.spectra.crosshair;

import javafx.geometry.Orientation;
import javafx.scene.paint.Color;

/**
 * A single cross-hair cursor, made of two crossing lines.
 */
class CrossHair {
    // XXX temporary, for compatibility with code working with cursor indexes
    private final int id;

    private final CrossHairLine horizontal = new CrossHairLine(Orientation.HORIZONTAL);
    private final CrossHairLine vertical = new CrossHairLine(Orientation.VERTICAL);

    public CrossHair(int id) {
        this.id = id;
    }

    public void setColor(Color color) {
        horizontal.setColor(color);
        vertical.setColor(color);
    }

    public CrossHairLine getLine(Orientation orientation) {
        return switch (orientation) {
            case HORIZONTAL -> horizontal;
            case VERTICAL -> vertical;
        };
    }

    public Double getNullablePosition(Orientation orientation) {
        return getLine(orientation).getNullablePosition();
    }

    public void setHorizontalLine(double startX, double startY, double endX, double endY) {
        horizontal.setLine(startX, startY, endX, endY);
    }

    public void setHorizontalActive(boolean state) {
        horizontal.setActive(state);
    }

    public void setVerticalLine(double startX, double startY, double endX, double endY) {
        vertical.setLine(startX, startY, endX, endY);
    }

    public void setVerticalActive(boolean state) {
        vertical.setActive(state);
    }

    public double getVerticalPosition() {
        return vertical.getPosition();
    }

    public int getId() {
        return id;
    }

    public boolean isVerticalDisplayed() {
        return vertical.isDisplayed();
    }

    public boolean isHorizontalDisplayed() {
        return horizontal.isDisplayed();
    }
}
