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

/**
 * @author brucejohnson
 */
public abstract class AnnoShape implements CanvasAnnotation {

    Color stroke = Color.BLACK;
    Color fill = Color.BLACK;
    double lineWidth = 1.0;
    boolean clipInAxes = false;
    boolean selected = false;
    boolean selectable = false;
    POSTYPE xPosType;
    POSTYPE yPosType;

    /**
     * @return the stroke
     */
    public Color getStroke() {
        return stroke;
    }

    /**
     * @param stroke the stroke to set
     */
    public void setStroke(Color stroke) {
        this.stroke = stroke;
    }

    /**
     * @return the fill
     */
    public Color getFill() {
        return fill;
    }

    /**
     * @param fill the fill to set
     */
    public void setFill(Color fill) {
        this.fill = fill;
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

    @Override
    public POSTYPE getYPosType() {
        return yPosType;
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

    @Override
    public void setSelectable(boolean state) {
        selectable = state;
    }

}
