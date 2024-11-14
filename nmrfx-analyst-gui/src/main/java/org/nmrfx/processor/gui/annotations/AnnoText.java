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
 * @author brucejohnson
 */
public class AnnoText implements CanvasAnnotation {
    private static final Logger log = LoggerFactory.getLogger(AnnoText.class);
    protected String text;
    double x1;
    double y1;
    double width;
    double startX1;
    double startY1;
    double startWidth;
    boolean selected = false;
    boolean selectable = true;
    int activeHandle = -1;
    POSTYPE xPosType;
    POSTYPE yPosType;
    Bounds bounds2D;
    Font font = Font.font("Liberation Sans", 12);
    Color fill = Color.BLACK;

    public AnnoText() {

    }

    public AnnoText(double x1, double y1, double width, String text, double fontSize,
                    POSTYPE xPosType, POSTYPE yPosType) {
        this.x1 = x1;
        this.y1 = y1;
        this.width = width;
        this.xPosType = xPosType;
        this.yPosType = yPosType;
        this.text = text;
        this.setFontSize(fontSize);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setFont(Font font) {
        this.font = font;
    }

    public double getFontSize() {
        return font.getSize();
    }

    public void setFontSize(double size) {
        this.font = Font.font(font.getFamily(), size);
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

    @Override
    public boolean hit(double x, double y, boolean selectMode) {
        boolean hit = (bounds2D != null) && bounds2D.contains(x, y);
        if (hit) {
            startX1 = x1;
            startY1 = y1;
            startWidth = width;
        }
        if (selectMode && selectable) {
            selected = hit;
        }
        return hit;
    }

    public double getX1() {
        return x1;
    }

    public void setX1(double x1) {
        this.x1 = x1;
    }

    public double getY1() {
        return y1;
    }

    public void setY1(double y1) {
        this.y1 = y1;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double value) {
        width = value;
    }

    public Bounds getBounds() {
        return bounds2D;
    }

    /**
     * Moves the annotext around the canvas. If a handle is selected, the handle can be
     * moved to adjust the size of the molecule but, it cannot be moved past another handle.
     * (i.e. The text cannot be sized to a negative width)
     *
     * @param bounds The bounds of the canvas.
     * @param world  The bounds of the canvas in the units of the canvas axis.
     * @param start  The starting position.
     * @param pos    The new position.
     */
    @Override
    public void move(double[][] bounds, double[][] world, double[] start, double[] pos) {
        double dx = pos[0] - start[0];
        double dy = pos[1] - start[1];
        double handleSeparationLimit = getHandleSeparationLimit(bounds, world);
        if (activeHandle < 0) {
            x1 = xPosType.move(startX1, dx, bounds[0], world[0]);
            y1 = yPosType.move(startY1, dy, bounds[1], world[1]);
        } else if (activeHandle == 0) {
            if (dx < startWidth) {
                x1 = xPosType.move(startX1, dx, bounds[0], world[0]);
                width = startWidth - dx;
            }
            width = Math.max(width, handleSeparationLimit);
        } else if (activeHandle == 1) {
            if (dx > -startWidth) {
                width = startWidth + dx;
            }
            width = Math.max(width, handleSeparationLimit);
        }
    }

    @Override
    public void draw(GraphicsContextInterface gC, double[][] bounds, double[][] world) {
        try {
            if (fill != null) {
                gC.setFill(fill);
            }
            gC.setFont(font);
            gC.setTextAlign(TextAlignment.LEFT);
            gC.setTextBaseline(VPos.BASELINE);

            double xp1 = xPosType.transform(x1, bounds[0], world[0]);
            double yp1 = yPosType.transform(y1, bounds[1], world[1]);

            String[] segments = text.split("\n");
            double topY = yp1 - font.getSize();
            double y = yp1;
            for (String segment : segments) {
                double textWidth = GUIUtils.getTextWidth(segment, font);
                if (textWidth > width) {
                    List<String> strings = GUIUtils.splitToWidth(this.width, segment, font);
                    for (String string : strings) {
                        if (fill != null) {
                            gC.fillText(string, xp1, y);
                        }
                        y += font.getSize() + 3;
                    }
                } else {
                    if (fill != null) {
                        gC.fillText(segment, xp1, y);
                    }
                    y += font.getSize() + 3;
                }
            }
            bounds2D = new BoundingBox(xp1, topY, width, y - topY);
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

    public void setXPosType(POSTYPE xPosType) {
        this.xPosType = xPosType;
    }

    @Override
    public POSTYPE getYPosType() {
        return yPosType;
    }

    public void setYPosType(POSTYPE yPosType) {
        this.yPosType = yPosType;
    }

    @Override
    public void drawHandles(GraphicsContextInterface gC) {
        if (bounds2D != null) {
            drawHandle(gC, bounds2D.getMinX(), (bounds2D.getMinY() + bounds2D.getMaxY()) / 2, Pos.BOTTOM_RIGHT);
            drawHandle(gC, bounds2D.getMaxX(), (bounds2D.getMinY() + bounds2D.getMaxY()) / 2, Pos.BOTTOM_LEFT);
        }
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
    public int hitHandle(double x, double y) {
        if (bounds2D != null) {
            if (hitHandle(x, y, Pos.BOTTOM_RIGHT, bounds2D.getMinX(), (bounds2D.getMinY() + bounds2D.getMaxY()) / 2)) {
                activeHandle = 0;
            } else if (hitHandle(x, y, Pos.BOTTOM_LEFT, bounds2D.getMaxX(), (bounds2D.getMinY() + bounds2D.getMaxY()) / 2)) {
                activeHandle = 1;
            } else {
                activeHandle = -1;
            }
        } else {
            activeHandle = -1;
        }
        return activeHandle;
    }

    public void updateXPosType(POSTYPE newType, double[] bounds, double[] world) {
        double x1Pix = xPosType.transform(x1, bounds, world);
        x1 = newType.itransform(x1Pix, bounds, world);
        xPosType = newType;
    }

    public void updateYPosType(POSTYPE newType, double[] bounds, double[] world) {
        double y1Pix = yPosType.transform(y1, bounds, world);
        y1 = newType.itransform(y1Pix, bounds, world);
        yPosType = newType;
    }

}
