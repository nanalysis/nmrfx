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
public class BoxMark extends ChartMark {

    Orientation orientation;

    public BoxMark(Color fill, Color stroke, Orientation orientation) {
        this.fill = fill;
        this.stroke = stroke;
        this.orientation = orientation;
    }

    void draw(GraphicsContextInterface gC, double position, double thickness,
              BoxPlotData fiveNum, Axis positionAxis, Axis valueAxis) {
        gC.setFill(fill);
        double whiskMin = valueAxis.getDisplayPosition(fiveNum.getMinWhisker());
        double whiskMedian = valueAxis.getDisplayPosition(fiveNum.median);
        double whiskMax = valueAxis.getDisplayPosition(fiveNum.getMaxWhisker());
        double q1Pos = valueAxis.getDisplayPosition(fiveNum.q1);
        double q3Pos = valueAxis.getDisplayPosition(fiveNum.q3);
        double pixPos = positionAxis.getDisplayPosition(position);
        double p1 = pixPos - thickness / 2.0;
        double p2 = pixPos + thickness / 2.0;
        double p1n = pixPos - thickness / 4.0;
        double p2n = pixPos + thickness / 4.0;
        double ratio = 10.0;
        double dia = thickness / ratio;
        gC.setStroke(stroke);
        if (orientation == Orientation.VERTICAL) {
            gC.fillRect(p1, q3Pos, thickness, q1Pos - q3Pos);
            gC.strokeLine(p1n, whiskMin, p2n, whiskMin);
            gC.strokeLine(p1n, whiskMax, p2n, whiskMax);
            gC.strokeLine(pixPos, whiskMin, pixPos, whiskMax);
            gC.setStroke(Color.WHITE);
            gC.strokeLine(p1, whiskMedian, p2, whiskMedian);
            gC.setStroke(stroke);
            for (var v : fiveNum.outliers) {
                double c = valueAxis.getDisplayPosition(v);
                gC.strokeOval(pixPos - dia / 2.0, c - dia / 2.0, dia, dia);
            }
        } else {
            gC.fillRect(q1Pos, p1, q3Pos - q1Pos, thickness);
            gC.strokeLine(whiskMin, pixPos, whiskMax, pixPos);
            gC.strokeLine(whiskMin, p1n, whiskMin, p2n);
            gC.strokeLine(whiskMax, p1n, whiskMax, p2n);
            gC.setStroke(Color.WHITE);
            gC.strokeLine(whiskMedian, p1, whiskMedian, p2);
            gC.setStroke(stroke);
            for (var v : fiveNum.outliers) {
                double c = valueAxis.getDisplayPosition(v);
                gC.strokeOval(c - dia / 2.0, pixPos - dia / 2.0, dia, dia);
            }
        }

    }

    boolean hit(double position, double thickness, BoxPlotData fiveNum,
                PickPoint pt, Axis positionAxis, Axis valueAxis) {
        double whiskMin = valueAxis.getDisplayPosition(fiveNum.getMinWhisker());
        double whiskMax = valueAxis.getDisplayPosition(fiveNum.getMaxWhisker());
        double pixPos = positionAxis.getDisplayPosition(position);
        double p1 = pixPos - thickness / 2.0;
        Rectangle2D rect;
        if (orientation == Orientation.VERTICAL) {
            rect = new Rectangle2D(p1, whiskMin, thickness, whiskMax - whiskMin);
        } else {
            rect = new Rectangle2D(whiskMin, p1, whiskMax - whiskMin, thickness);
        }
        return rect.contains(pt.x, pt.y);
    }

}
