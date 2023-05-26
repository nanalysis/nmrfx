/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2023 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
