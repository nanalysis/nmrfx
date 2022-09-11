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

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.processor.gui.CanvasAnnotation;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 *
 * @author brucejohnson
 */
public class AnnoText implements CanvasAnnotation {
    private static final Logger log = LoggerFactory.getLogger(AnnoText.class);

    double x1;
    double y1;
    double x2;
    double y2;
    double startX1;
    double startY1;
    double startX2;
    double startY2;
    boolean selected = false;
    boolean selectable = true;
    int activeHandle = -1;

    POSTYPE xPosType;
    POSTYPE yPosType;
    Bounds bounds2D;
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

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
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
    public boolean hit(double x, double y, boolean selectMode) {
        boolean hit = (bounds2D != null) && bounds2D.contains(x, y);
        if (hit) {
            startX1 = x1;
            startX2 = x2;
            startY1 = y1;
            startY2 = y2;
        }
        if (selectMode && selectable) {
            selected = hit;
        }
        return hit;
    }

    public Bounds getBounds() {
        return bounds2D;
    }

    @Override
    public void move(double[][] bounds, double[][] world, double[] start, double[] pos) {
        double dx = pos[0] - start[0];
        double dy = pos[1] - start[1];
        if (activeHandle < 0) {
            x1 = xPosType.move(startX1, dx, bounds[0], world[0]);
            x2 = xPosType.move(startX2, dx, bounds[0], world[0]);
            y1 = yPosType.move(startY1, dy, bounds[1], world[1]);
            y2 = yPosType.move(startY2, dy, bounds[1], world[1]);
        } else if (activeHandle == 0) {
            x1 = xPosType.move(startX1, dx, bounds[0], world[0]);
        } else if (activeHandle == 1) {
            x2 = xPosType.move(startX2, dx, bounds[0], world[0]);
        }
    }

    @Override
    public void draw(GraphicsContextInterface gC, double[][] bounds, double[][] world) {
        try {
            gC.setFill(fill);
            gC.setFont(font);
            gC.setTextAlign(TextAlignment.LEFT);
            gC.setTextBaseline(VPos.BASELINE);

            double xp1 = xPosType.transform(x1, bounds[0], world[0]);
            double yp1 = yPosType.transform(y1, bounds[1], world[1]);
            double xp2 = xPosType.transform(x2, bounds[0], world[0]);
            double regionWidth = xp2 - xp1;
            String[] segments = text.split("\n");
            double topY = yp1-font.getSize();
            double y = yp1;
            for (String segment:segments) {
                double width = GUIUtils.getTextWidth(segment, font);
                if (width > regionWidth) {
                    List<String> strings = GUIUtils.splitToWidth(regionWidth, segment,font);
                    for (String string:strings) {
                        gC.fillText(string, xp1, y);
                        y += font.getSize() + 3;
                    }
                } else {
                    gC.fillText(segment, xp1, y);
                    y += font.getSize() +3;
                }
            }
            bounds2D = new BoundingBox(xp1, topY, regionWidth, y - topY);
            if (isSelected()) {
                drawHandles(gC);
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

    @Override
    public void drawHandles(GraphicsContextInterface gC) {
        drawHandle(gC, bounds2D.getMinX(), (bounds2D.getMinY() + bounds2D.getMaxY())/2, Pos.CENTER_RIGHT);
        drawHandle(gC, bounds2D.getMaxX(), (bounds2D.getMinY() + bounds2D.getMaxY())/2, Pos.CENTER_LEFT);
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public boolean isSelectable() {
        return selectable;
    }

    @Override
    public void setSelectable(boolean state) {
        selectable = state;
    }


    @Override
    public int hitHandle(double x, double y) {
        if (hitHandle(x,y, Pos.CENTER_RIGHT, bounds2D.getMinX(), (bounds2D.getMinY() + bounds2D.getMaxY())/2)) {
            activeHandle = 0;
        } else if (hitHandle(x,y, Pos.CENTER_LEFT, bounds2D.getMaxX(), (bounds2D.getMinY() + bounds2D.getMaxY())/2)) {
            activeHandle = 1;
        } else {
            activeHandle = -1;
        }
        return activeHandle;
    }

    @Override
    public int getActiveHandle() {
        return activeHandle;
    }
}
