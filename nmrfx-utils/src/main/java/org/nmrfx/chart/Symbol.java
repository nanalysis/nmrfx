/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2018 One Moon Scientific, Inc., Westfield, N.J., USA
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

import javafx.scene.paint.Color;
import org.nmrfx.graphicsio.GraphicsContextInterface;

/**
 * @author brucejohnson
 */
public enum Symbol {
    CIRCLE() {
        public void draw(GraphicsContextInterface gC, double x, double y, double radius, Color stroke, Color fill) {
            double x1 = x - radius;
            double y1 = y - radius;
            double diameter = 2 * radius;
            if (fill != null) {
                gC.setFill(fill);
                gC.fillOval(x1, y1, diameter, diameter);
            }
            if (stroke != null) {
                gC.setStroke(stroke);
                gC.strokeOval(x1, y1, diameter, diameter);
            }
        }

    },
    TRIANGLE_UP() {
        public void draw(GraphicsContextInterface gC, double x, double y, double radius, Color stroke, Color fill) {
            double[] xValues = {x, x - radius * TRI_X, x + radius * TRI_X, x};
            double[] yValues = {y - radius, y + radius * TRI_Y, y + radius * TRI_Y, y - radius};
            if (stroke != null) {
                gC.setStroke(stroke);
                gC.strokePolygon(xValues, yValues, xValues.length);
            }
            if (fill != null) {
                gC.setFill(fill);
                gC.fillPolygon(xValues, xValues, xValues.length);
            }
        }
    },
    TRIANGLE_DOWN() {
        public void draw(GraphicsContextInterface gC, double x, double y, double radius, Color stroke, Color fill) {
            double[] xValues = {x, x - radius * TRI_X, x + radius * TRI_X, x};
            double[] yValues = {y + radius, y - radius * TRI_Y, y - radius * TRI_Y, y + radius};
            if (stroke != null) {
                gC.setStroke(stroke);
                gC.strokePolygon(xValues, yValues, xValues.length);
            }
            if (fill != null) {
                gC.setFill(fill);
                gC.fillPolygon(xValues, xValues, xValues.length);
            }
        }

    };

    static final double TRI_X = Math.cos(Math.toRadians(30.0));
    static final double TRI_Y = Math.sin(Math.toRadians(30.0));

    abstract public void draw(GraphicsContextInterface gC, double x, double y, double radius, Color stroke, Color fill);
}
