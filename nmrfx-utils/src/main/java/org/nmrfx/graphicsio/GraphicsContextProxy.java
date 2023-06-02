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
package org.nmrfx.graphicsio;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
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
public class GraphicsContextProxy implements GraphicsContextInterface {

    final private GraphicsContext gC;

    public GraphicsContextProxy(GraphicsContext gC) {
        this.gC = gC;
    }

    @Override
    public void beginPath() {
        gC.beginPath();
    }

    @Override
    public void bezierCurveTo(double xc1, double yc1, double xc2, double yc2, double x1, double y1) {
        gC.bezierCurveTo(xc1, yc1, xc2, yc2, x1, y1);
    }

    @Override
    public void clearRect(double x, double y, double w, double h) {
        gC.clearRect(x, y, w, h);
    }

    @Override
    public void clip() {
        gC.clip();
    }

    @Override
    public void closePath() {
        gC.closePath();
    }

    @Override
    public void fill() {
        gC.fill();
    }

    @Override
    public void fillOval(double x, double y, double w, double h) {
        gC.fillOval(x, y, w, h);
    }

    @Override
    public void fillPolygon(double[] xPoints, double[] yPoints, int nPoints) {
        gC.fillPolygon(xPoints, yPoints, nPoints);
    }

    @Override
    public void fillRect(double x, double y, double w, double h) {
        gC.fillRect(x, y, w, h);
    }

    @Override
    public void fillText(String text, double x, double y) {
        gC.fillText(text, x, y);
    }

    @Override
    public Paint getFill() {
        return gC.getFill();
    }

    @Override
    public Font getFont() {
        return gC.getFont();
    }

    @Override
    public double getLineWidth() {
        return gC.getLineWidth();
    }

    @Override
    public Paint getStroke() {
        return gC.getStroke();
    }

    @Override
    public Affine getTransform() {
        return gC.getTransform();
    }

    @Override
    public void lineTo(double x1, double y1) {
        gC.lineTo(x1, y1);
    }

    @Override
    public void moveTo(double x0, double y0) {
        gC.moveTo(x0, y0);
    }

    @Override
    public void rect(double x, double y, double w, double h) {
        gC.rect(x, y, w, h);
    }

    @Override
    public void restore() {
        gC.restore();
    }

    @Override
    public void rotate(double degrees) {
        gC.rotate(degrees);
    }

    @Override
    public void save() {
        gC.save();
    }

    @Override
    public void setEffect(Effect e) {
        gC.setEffect(e);
    }

    @Override
    public void setFill(Paint p) {
        gC.setFill(p);
    }

    @Override
    public void setFont(Font f) {
        gC.setFont(f);
    }

    @Override
    public void setGlobalAlpha(double alpha) {
        gC.setGlobalAlpha(alpha);
    }

    @Override
    public void setLineCap(StrokeLineCap cap) {
        gC.setLineCap(cap);
    }

    @Override
    public void setLineDashes(double... dashes) {
        gC.setLineDashes(dashes);
    }

    @Override
    public void setLineWidth(double lw) {
        gC.setLineWidth(lw);
    }

    @Override
    public void setStroke(Paint p) {
        gC.setStroke(p);
    }

    @Override
    public void setTextAlign(TextAlignment align) {
        gC.setTextAlign(align);
    }

    @Override
    public void setTextBaseline(VPos baseline) {
        gC.setTextBaseline(baseline);
    }

    @Override
    public void setTransform(Affine xform) {
        gC.setTransform(xform);
    }

    @Override
    public void stroke() {
        gC.stroke();
    }

    @Override
    public void strokeLine(double x1, double y1, double x2, double y2) {
        gC.strokeLine(x1, y1, x2, y2);
    }

    @Override
    public void strokeOval(double x, double y, double w, double h) {
        gC.strokeOval(x, y, w, h);
    }

    @Override
    public void strokePolygon(double[] xPoints, double[] yPoints, int nPoints) {
        gC.strokePolygon(xPoints, yPoints, nPoints);
    }

    @Override
    public void strokePolyline(double[] xPoints, double[] yPoints, int nPoints) {
        gC.strokePolyline(xPoints, yPoints, nPoints);
    }

    @Override
    public void strokeRect(double x, double y, double w, double h) {
        gC.strokeRect(x, y, w, h);
    }

    @Override
    public void strokeText(String text, double x, double y) {
        gC.strokeText(text, x, y);
    }

    @Override
    public void translate(double x, double y) {
        gC.translate(x, y);
    }

}
