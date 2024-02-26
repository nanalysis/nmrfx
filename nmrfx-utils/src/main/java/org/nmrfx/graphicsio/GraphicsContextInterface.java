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
 * but WITHOUT ANY WARRANTY ; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.graphicsio;

import javafx.geometry.VPos;
import javafx.scene.effect.Effect;
import javafx.scene.paint.Paint;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Affine;

/**
 * @author brucejohnson
 */
//TODO uncomment when core & utils are regrouped
//@PluginAPI("ring")
public interface GraphicsContextInterface {

    default void nativeCoords(boolean state) {

    }

    void beginPath();

    void bezierCurveTo(double xc1, double yc1, double xc2, double yc2, double x1, double y1);

    void clearRect(double x, double y, double w, double h);

    void clip();

    void closePath();

    void fill();

    void fillOval(double x, double y, double w, double h);

    void fillPolygon(double[] xPoints, double[] yPoints, int nPoints);

    void fillRect(double x, double y, double w, double h);

    void fillText(String text, double x, double y);

    Paint getFill();

    Font getFont();

    double getLineWidth();

    Paint getStroke();

    Affine getTransform();

    void lineTo(double x1, double y1);

    void moveTo(double x0, double y0);

    void rect(double x, double y, double w, double h);

    void restore();

    void rotate(double degrees);

    void save();

    void setEffect(Effect e);

    void setFill(Paint p);

    void setFont(Font f);

    void setGlobalAlpha(double alpha);

    void setLineCap(StrokeLineCap cap);

    void setLineDashes(double... dashes);

    void setLineWidth(double lw);

    void setStroke(Paint p);

    void setTextAlign(TextAlignment align);

    void setTextBaseline(VPos baseline);

    void setTransform(Affine xform);

    void stroke();

    void strokeLine(double x1, double y1, double x2, double y2);

    void strokeOval(double x, double y, double w, double h);

    void strokePolygon(double[] xPoints, double[] yPoints, int nPoints);

    void strokePolyline(double[] xPoints, double[] yPoints, int nPoints);

    void strokeRect(double x, double y, double w, double h);

    void strokeText(String text, double x, double y);

    void translate(double x, double y);
}
