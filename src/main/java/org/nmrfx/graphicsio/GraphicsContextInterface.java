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
 * but WITHOUT ANY WARRANTY throws GraphicsIOException; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.graphicsio;

import javafx.geometry.VPos ;
import javafx.scene.effect.BlendMode ;
import javafx.scene.effect.Effect ;
import javafx.scene.image.Image ;
import javafx.scene.paint.Paint ;
import javafx.scene.shape.ArcType ;
import javafx.scene.shape.FillRule ;
import javafx.scene.shape.StrokeLineCap ;
import javafx.scene.shape.StrokeLineJoin ;
import javafx.scene.text.Font ;
import javafx.scene.text.FontSmoothingType ;
import javafx.scene.text.TextAlignment ;
import javafx.scene.transform.Affine ;

/**
 *
 * @author brucejohnson
 */
public interface GraphicsContextInterface {

    void appendSVGPath(String svgpath) throws GraphicsIOException;

    void applyEffect(Effect e) throws GraphicsIOException;

    void arc(double centerX, double centerY, double radiusX, double radiusY, double startAngle, double length) throws GraphicsIOException;

    void arcTo(double x1, double y1, double x2, double y2, double radius) throws GraphicsIOException;

    void beginPath() throws GraphicsIOException;

    void bezierCurveTo(double xc1, double yc1, double xc2, double yc2, double x1, double y1) throws GraphicsIOException;

    void clearRect(double x, double y, double w, double h) throws GraphicsIOException;

    void clip() throws GraphicsIOException;

    void closePath() throws GraphicsIOException;

    void drawImage(Image img, double x, double y) throws GraphicsIOException;

    void drawImage(Image img, double x, double y, double w, double h) throws GraphicsIOException;

    void drawImage(Image img, double sx, double sy, double sw, double sh, double dx, double dy, double dw, double dh) throws GraphicsIOException;

    void fill() throws GraphicsIOException;

    void fillArc(double x, double y, double w, double h, double startAngle, double arcExtent, ArcType closure) throws GraphicsIOException;

    void fillOval(double x, double y, double w, double h) throws GraphicsIOException;

    void fillPolygon(double[] xPoints, double[] yPoints, int nPoints) throws GraphicsIOException;

    void fillRect(double x, double y, double w, double h) throws GraphicsIOException;

    void fillRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight) throws GraphicsIOException;

    void fillText(String text, double x, double y) throws GraphicsIOException;

    void fillText(String text, double x, double y, double maxWidth) throws GraphicsIOException;

    Effect getEffect(Effect e) throws GraphicsIOException;

    Paint getFill() throws GraphicsIOException;

    FillRule getFillRule() throws GraphicsIOException;

    Font getFont();

    FontSmoothingType getFontSmoothingType() throws GraphicsIOException;

    double getGlobalAlpha() throws GraphicsIOException;

    BlendMode getGlobalBlendMode() throws GraphicsIOException;

    StrokeLineCap getLineCap() throws GraphicsIOException;

    double[] getLineDashes() throws GraphicsIOException;

    double getLineDashOffset() throws GraphicsIOException;

    StrokeLineJoin getLineJoin() throws GraphicsIOException;

    double getLineWidth() throws GraphicsIOException;

    double getMiterLimit() throws GraphicsIOException;

    Paint getStroke() throws GraphicsIOException;

    TextAlignment getTextAlign() throws GraphicsIOException;

    VPos getTextBaseline() throws GraphicsIOException;

    Affine getTransform() throws GraphicsIOException;

    Affine getTransform(Affine xform) throws GraphicsIOException;

    boolean isPointInPath(double x, double y) throws GraphicsIOException;

    void lineTo(double x1, double y1) throws GraphicsIOException;

    void moveTo(double x0, double y0) throws GraphicsIOException;

    void quadraticCurveTo(double xc, double yc, double x1, double y1) throws GraphicsIOException;

    void rect(double x, double y, double w, double h) throws GraphicsIOException;

    void restore();

    void rotate(double degrees) throws GraphicsIOException;

    void save();

    void scale(double x, double y) throws GraphicsIOException;

    void setEffect(Effect e) throws GraphicsIOException;

    void setFill(Paint p) throws GraphicsIOException;

    void setFillRule(FillRule fillRule) throws GraphicsIOException;

    void setFont(Font f) throws GraphicsIOException;

    void setFontSmoothingType(FontSmoothingType fontsmoothing) throws GraphicsIOException;

    void setGlobalAlpha(double alpha) throws GraphicsIOException;

    void setGlobalBlendMode(BlendMode op) throws GraphicsIOException;

    void setLineCap(StrokeLineCap cap) throws GraphicsIOException;

    void setLineDashes(double... dashes) throws GraphicsIOException;

    void setLineDashOffset(double dashOffset) throws GraphicsIOException;

    void setLineJoin(StrokeLineJoin join) throws GraphicsIOException;

    void setLineWidth(double lw) throws GraphicsIOException;

    void setMiterLimit(double ml) throws GraphicsIOException;

    void setStroke(Paint p) throws GraphicsIOException;

    void setTextAlign(TextAlignment align) throws GraphicsIOException;

    void setTextBaseline(VPos baseline) throws GraphicsIOException;

    void setTransform(Affine xform) throws GraphicsIOException;

    void setTransform(double mxx, double myx, double mxy, double myy, double mxt, double myt) throws GraphicsIOException;

    void stroke() throws GraphicsIOException;

    void strokeArc(double x, double y, double w, double h, double startAngle, double arcExtent, ArcType closure) throws GraphicsIOException;

    void strokeLine(double x1, double y1, double x2, double y2) throws GraphicsIOException;

    void strokeOval(double x, double y, double w, double h) throws GraphicsIOException;

    void strokePolygon(double[] xPoints, double[] yPoints, int nPoints) throws GraphicsIOException;

    void strokePolyline(double[] xPoints, double[] yPoints, int nPoints) throws GraphicsIOException;

    void strokeRect(double x, double y, double w, double h) throws GraphicsIOException;

    void strokeRoundRect(double x, double y, double w, double h, double arcWidth, double arcHeight) throws GraphicsIOException;

    void strokeText(String text, double x, double y) throws GraphicsIOException;

    void strokeText(String text, double x, double y, double maxWidth) throws GraphicsIOException;

    void transform(Affine xform) throws GraphicsIOException;

    void transform(double mxx, double myx, double mxy, double myy, double mxt, double myt) throws GraphicsIOException;

    void translate(double x, double y) throws GraphicsIOException;
}
