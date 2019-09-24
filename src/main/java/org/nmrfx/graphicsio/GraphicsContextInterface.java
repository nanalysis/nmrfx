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
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.Effect;
import javafx.scene.image.Image;
import javafx.scene.paint.Paint;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Affine;

/**
 *
 * @author brucejohnson
 */
public interface GraphicsContextInterface {

    void appendSVGPath(String svgpath);

    void applyEffect(Effect e);

    void arc(double centerX, double centerY, double radiusX, double radiusY, double startAngle, double length);

    void arcTo(double x1, double y1, double x2, double y2, double radius);

    void beginPath();

    void bezierCurveTo(double xc1, double yc1, double xc2, double yc2, double x1, double y1);

    void clearRect(double x, double y, double w, double h);

    void clip();

    void closePath();

    void drawImage(Image img, double x, double y);

    void drawImage(Image img, double x, double y, double w, double h);

    void drawImage(Image img, double sx, double sy, double sw, double sh, double dx, double dy, double dw, double dh);

    void fill();

    void fillArc(double x, double y, double w, double h, double startAngle, double arcExtent, ArcType closure);

    void fillOval(double x, double y, double w, double h);

    void fillPolygon(double[] xPoints, double[] yPoints, int nPoints);

    void fillRect(double x, double y, double w, double h);

    void fillRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight);

    void fillText(String text, double x, double y);

    void fillText(String text, double x, double y, double maxWidth);

    Effect getEffect(Effect e);

    Paint getFill();

    FillRule getFillRule();

    Font getFont();

    FontSmoothingType getFontSmoothingType();

    double getGlobalAlpha();

    BlendMode getGlobalBlendMode();

    StrokeLineCap getLineCap();

    double[] getLineDashes();

    double getLineDashOffset();

    StrokeLineJoin getLineJoin();

    double getLineWidth();

    double getMiterLimit();

    Paint getStroke();

    TextAlignment getTextAlign();

    VPos getTextBaseline();

    Affine getTransform();

    Affine getTransform(Affine xform);

    boolean isPointInPath(double x, double y);

    void lineTo(double x1, double y1);

    void moveTo(double x0, double y0);

    void quadraticCurveTo(double xc, double yc, double x1, double y1);

    void rect(double x, double y, double w, double h);

    void restore();

    void rotate(double degrees);

    void save();

    void scale(double x, double y);

    void setEffect(Effect e);

    void setFill(Paint p);

    void setFillRule(FillRule fillRule);

    void setFont(Font f);

    void setFontSmoothingType(FontSmoothingType fontsmoothing);

    void setGlobalAlpha(double alpha);

    void setGlobalBlendMode(BlendMode op);

    void setLineCap(StrokeLineCap cap);

    void setLineDashes(double... dashes);

    void setLineDashOffset(double dashOffset);

    void setLineJoin(StrokeLineJoin join);

    void setLineWidth(double lw);

    void setMiterLimit(double ml);

    void setStroke(Paint p);

    void setTextAlign(TextAlignment align);

    void setTextBaseline(VPos baseline);

    void setTransform(Affine xform);

    void setTransform(double mxx, double myx, double mxy, double myy, double mxt, double myt);

    void stroke();

    void strokeArc(double x, double y, double w, double h, double startAngle, double arcExtent, ArcType closure);

    void strokeLine(double x1, double y1, double x2, double y2);

    void strokeOval(double x, double y, double w, double h);

    void strokePolygon(double[] xPoints, double[] yPoints, int nPoints);

    void strokePolyline(double[] xPoints, double[] yPoints, int nPoints);

    void strokeRect(double x, double y, double w, double h);

    void strokeRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight);

    void strokeText(String text, double x, double y);

    void strokeText(String text, double x, double y, double maxWidth);

    void transform(Affine xform);

    void transform(double mxx, double myx, double mxy, double myy, double mxt, double myt);

    void translate(double x, double y);
}
