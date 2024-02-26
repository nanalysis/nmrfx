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
package org.nmrfx.processor.gui.annotations;

import javafx.scene.paint.Color;
import org.nmrfx.processor.gui.CanvasAnnotation;
import org.nmrfx.utils.GUIUtils;

/**
 * @author brucejohnson
 */
public abstract class AnnoShape implements CanvasAnnotation {

    Color stroke = Color.BLACK;
    Color fill = Color.BLACK;
    double lineWidth = 1.0;
    boolean clipInAxes = false;
    boolean selected = false;
    boolean selectable = true;
    POSTYPE xPosType = POSTYPE.WORLD;
    POSTYPE yPosType = POSTYPE.WORLD;

    /**
     * @return the stroke
     */
    public String getStroke() {
        return stroke == null ? "" : stroke.toString();
    }

    /**
     * @param stroke the stroke to set
     */
    public void setStroke(Color stroke) {
        this.stroke = stroke;
    }

    public void setStroke(String stroke) {
        this.stroke = GUIUtils.getColor(stroke);
    }

    public Color getStrokeColor() {
        return this.stroke;
    }

    /**
     * @return the fill
     */
    public String getFill() {
        return fill == null ? "" : fill.toString();
    }

    /**
     * @param fill the fill to set
     */
    public void setFill(Color fill) {
        this.fill = fill;
    }

    public void setFill(String fill) {
        this.fill = GUIUtils.getColor(fill);
    }

    public Color getFillColor() {
        return this.fill;
    }

    /**
     * @return the lineWidth
     */
    public double getLineWidth() {
        return lineWidth;
    }

    /**
     * @param lineWidth the lineWidth to set
     */
    public void setLineWidth(double lineWidth) {
        this.lineWidth = lineWidth;
    }

    @Override
    public POSTYPE getXPosType() {
        return xPosType;
    }

    public void setXPosType(POSTYPE xPosType) {
        this.xPosType = xPosType;
    }

    public void setXPosType(String xPosType) {
        this.xPosType = POSTYPE.valueOf(xPosType);
    }

    @Override
    public POSTYPE getYPosType() {
        return yPosType;
    }

    public void setYPosType(POSTYPE yPosType) {
        this.yPosType = yPosType;
    }

    public void setYPosType(String yPosType) {
        this.yPosType = POSTYPE.valueOf(yPosType);
    }

    @Override
    public boolean getClipInAxes() {
        return clipInAxes;
    }

    public void setClipInAxes(boolean state) {
        clipInAxes = state;
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public boolean isSelectable() {
        return selected;
    }
}
