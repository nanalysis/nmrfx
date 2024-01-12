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

import javafx.geometry.Pos;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author brucejohnson
 */
public class AnnoSimpleLine extends AnnoShape {
    private static final Logger log = LoggerFactory.getLogger(AnnoSimpleLine.class);

    double x1;
    double y1;
    double x2;
    double y2;
    double xp1;
    double yp1;
    double xp2;
    double yp2;
    int activeHandle = -1;

    public AnnoSimpleLine() {

    }

    public AnnoSimpleLine(double x1, double y1, double x2, double y2,
                          POSTYPE xPosType, POSTYPE yPosType) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.xPosType = xPosType;
        this.yPosType = yPosType;
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

    public double getX2() {
        return x2;
    }

    public void setX2(double x2) {
        this.x2 = x2;
    }

    public double getY2() {
        return y2;
    }

    public void setY2(double y2) {
        this.y2 = y2;
    }

    @Override
    public boolean hit(double x, double y, boolean selectMode) {
        return false;
    }

    @Override
    public void draw(GraphicsContextInterface gC, double[][] bounds, double[][] world) {
        try {
            gC.setStroke(stroke);
            gC.setLineWidth(lineWidth);
            xp1 = xPosType.transform(x1, bounds[0], world[0]);
            yp1 = yPosType.transform(y1, bounds[1], world[1]);
            xp2 = xPosType.transform(x2, bounds[0], world[0]);
            yp2 = yPosType.transform(y2, bounds[1], world[1]);
            gC.strokeLine(xp1, yp1, xp2, yp2);
            if (isSelected()) {
                drawHandles(gC);
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void drawHandles(GraphicsContextInterface gC) {
        drawHandle(gC, xp1, yp1, Pos.CENTER);
        drawHandle(gC, xp2, yp2, Pos.CENTER);
    }

    @Override
    public int hitHandle(double x, double y) {
        if (hitHandle(x, y, Pos.CENTER, xp1, yp1)) {
            activeHandle = 0;
        } else if (hitHandle(x, y, Pos.CENTER, xp2, yp2)) {
            activeHandle = 1;
        } else {
            activeHandle = -1;
        }
        return activeHandle;
    }

    public void updateXPosType(POSTYPE newType, double[] bounds, double[] world) {
        double x1Pix = xPosType.transform(x1, bounds, world);
        double x2Pix = xPosType.transform(x2, bounds, world);
        x1 = newType.itransform(x1Pix, bounds, world);
        x2 = newType.itransform(x2Pix, bounds, world);
        xPosType = newType;
    }

    public void updateYPosType(POSTYPE newType, double[] bounds, double[] world) {
        double y1Pix = yPosType.transform(y1, bounds, world);
        double y2Pix = yPosType.transform(y2, bounds, world);
        y1 = newType.itransform(y1Pix, bounds, world);
        y2 = newType.itransform(y2Pix, bounds, world);
        yPosType = newType;
    }

}
