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

import javafx.geometry.VPos;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.processor.gui.CanvasAnnotation;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author brucejohnson
 */
public class AnnoText implements CanvasAnnotation {

    private static final Logger log = LoggerFactory.getLogger(AnnoText.class);

    final double x1;
    final double y1;
    final double x2;
    final double y2;
    POSTYPE xPosType;
    POSTYPE yPosType;
    protected String text;
    Font font = Font.font("Liberation Sans", 12);

    Color fill = Color.BLACK;

    public AnnoText(double x1, double y1, double x2, double y2,
            POSTYPE xPosType, POSTYPE yPosType, String text) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.xPosType = xPosType;
        this.yPosType = yPosType;
        this.text = text;
    }

    public Font getFont() {
        return font;
    }

    public void setFont(Font font) {
        this.font = font;
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

    @Override
    public void draw(GraphicsContextInterface gC, double[][] bounds, double[][] world) {
        try {
            gC.setFill(fill);
            gC.setFont(font);
            gC.setTextAlign(TextAlignment.LEFT);
            gC.setTextBaseline(VPos.BASELINE);
            double width = GUIUtils.getTextWidth(text, font);

            double xp1 = xPosType.transform(x1, bounds[0], world[0]);
            double yp1 = yPosType.transform(y1, bounds[1], world[1]);
            double xp2 = xPosType.transform(x2, bounds[0], world[0]);
            double yp2 = yPosType.transform(y2, bounds[1], world[1]);
            double regionWidth = xp2 - xp1;
            if (width > regionWidth) {
                double charWidth = width / text.length();
                int start = 0;
                int end;
                double yOffset = 0.0;
                while (true) {
                    end = start + (int) (regionWidth / charWidth);
                    if (end > text.length()) {
                        end = text.length();
                    }
                    String subStr = text.substring(start, end);
                    gC.fillText(subStr, xp1, yp1 + yOffset);
                    start = end;
                    yOffset += font.getSize() + 3;
                    if (start >= text.length()) {
                        break;
                    }
                }
            } else {
                gC.fillText(text, xp1, yp1);
            }
        } catch (Exception ex) {
            log.warn(ex.getMessage(), ex);
        }
    }

    @Override
    public POSTYPE getXPosType() {
        return xPosType;
    }

    @Override
    public POSTYPE getYPosType() {
        return yPosType;
    }

}
