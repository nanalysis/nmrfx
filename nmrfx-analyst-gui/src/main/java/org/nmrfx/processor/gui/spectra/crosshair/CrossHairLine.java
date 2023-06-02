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
import javafx.scene.shape.Line;
import org.codehaus.commons.nullanalysis.Nullable;

/**
 * A single line, as a component in a cross-hair cursor.
 */
class CrossHairLine {
    private final Line line = new Line(0, 0, 0, 0);
    private final Orientation orientation;
    private boolean active;
    private double position;

    public CrossHairLine(Orientation orientation) {
        this.orientation = orientation;

        line.setVisible(false);
        line.setStrokeWidth(0.5);
        line.setMouseTransparent(true);
    }

    public void setLine(double startX, double startY, double endX, double endY) {
        line.setStartX(startX);
        line.setStartY(startY);
        line.setEndX(endX);
        line.setEndY(endY);
    }

    public boolean isDisplayed() {
        return active && line.isVisible();
    }

    public void setColor(Color color) {
        line.setStroke(color);
    }

    public void setVisible(boolean visible) {
        this.line.setVisible(visible);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean state) {
        this.active = state;
    }

    public Line getLine() {
        return line;
    }

    public Orientation getOrientation() {
        return orientation;
    }

    public double getPosition() {
        return position;
    }

    /**
     * @return the position if the line is active, or null.
     */
    @Nullable
    public Double getNullablePosition() {
        return active ? position : null;
    }

    public void setPosition(double position) {
        this.position = position;
    }

    public double getStartY() {
        return line.getStartY();
    }

    public double getStartX() {
        return line.getStartX();
    }
}
