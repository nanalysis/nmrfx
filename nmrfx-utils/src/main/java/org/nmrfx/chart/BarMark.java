/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2021 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.chart;

import javafx.geometry.Orientation;
import javafx.geometry.Rectangle2D;
import javafx.scene.paint.Color;
import org.nmrfx.chart.XYCanvasChart.PickPoint;
import org.nmrfx.graphicsio.GraphicsContextInterface;

/**
 * @author brucejohnson
 */
public class BarMark extends ChartMark {

    Orientation orientation;

    public BarMark(Color fill, Color stroke, Orientation orientation) {
        this.fill = fill;
        this.stroke = stroke;
        this.orientation = orientation;
    }

    void draw(GraphicsContextInterface gC, double x, double y, double w,
              double h, boolean disabled) {
        draw(gC, x, y, w, h, false, 0.0, 0.0, disabled);
    }

    void draw(GraphicsContextInterface gC, double x, double y, double thickness,
              double length, boolean hasError, double low, double high, boolean disabled) {
        gC.setFill(fill);
        if (orientation == Orientation.VERTICAL) {
            double x1 = x - thickness / 2.0;
            if (length < 0) {
                y += length;
                length = -length;
            }
            if (!disabled) {
                gC.fillRect(x1, y, thickness, length);
                if (hasError) {
                    gC.setStroke(stroke);
                    gC.strokeLine(x, low, x, high);
                }
            } else {
                gC.strokeRect(x1, y, thickness, length);
            }
        } else {
            double y1 = y - thickness / 2.0;
            if (length < 0) {
                x += length;
                length = -length;
            }
            if (!disabled) {
                gC.fillRect(x, y1, length, thickness);

                if (hasError) {
                    gC.setStroke(stroke);
                    gC.strokeLine(low, y, high, y);
                }
            } else {
                gC.strokeRect(x, y1, length, thickness);
            }

        }

    }

    boolean hit(double x, double y, double thickness,
                double length, PickPoint pt) {
        if (length < 0) {
            y += length;
            length = -length;
        }
        Rectangle2D rect;
        if (orientation == Orientation.VERTICAL) {
            double x1 = x - thickness / 2.0;
            if (length < 0) {
                y += length;
                length = -length;
            }
            rect = new Rectangle2D(x1, y, thickness, length);
        } else {
            double y1 = y - thickness / 2.0;
            if (length < 0) {
                x += length;
                length = -length;
            }
            rect = new Rectangle2D(x, y1, length, thickness);
        }
        return rect.contains(pt.x, pt.y);
    }

}
