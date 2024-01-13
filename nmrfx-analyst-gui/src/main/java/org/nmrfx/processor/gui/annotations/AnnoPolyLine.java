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

import java.util.List;

/**
 * @author brucejohnson
 */
public class AnnoPolyLine extends AnnoShape {
    private static final Logger log = LoggerFactory.getLogger(AnnoPolyLine.class);

    final double[] xPoints;
    final double[] yPoints;
    final double[] xCPoints;
    final double[] yCPoints;
    int activeHandle = -1;

    public AnnoPolyLine(List<Double> xList, List<Double> yList,
                        POSTYPE xPosType, POSTYPE yPosType) {
        xPoints = new double[xList.size()];
        yPoints = new double[yList.size()];
        for (int i = 0; i < xPoints.length; i++) {
            xPoints[i] = xList.get(i);
            yPoints[i] = yList.get(i);
        }

        xCPoints = new double[xPoints.length];
        yCPoints = new double[yPoints.length];
        this.xPosType = xPosType;
        this.yPosType = yPosType;
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
            for (int i = 0; i < xPoints.length; i++) {
                xCPoints[i] = xPosType.transform(xPoints[i], bounds[0], world[0]);
                yCPoints[i] = yPosType.transform(yPoints[i], bounds[1], world[1]);
            }
            gC.strokePolyline(xCPoints, yCPoints, xCPoints.length);
            if (isSelected()) {
                drawHandles(gC);
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void drawHandles(GraphicsContextInterface gC) {
        int last = xCPoints.length - 1;
        drawHandle(gC, xCPoints[0], yCPoints[0], Pos.CENTER);
        drawHandle(gC, xCPoints[last], yCPoints[last], Pos.CENTER);
    }

    @Override
    public int hitHandle(double x, double y) {
        int last = xCPoints.length - 1;
        if (hitHandle(x, y, Pos.CENTER, xCPoints[0], yCPoints[0])) {
            activeHandle = 0;
        } else if (hitHandle(x, y, Pos.CENTER, xCPoints[last], yCPoints[last])) {
            activeHandle = 1;
        } else {
            activeHandle = -1;
        }
        return activeHandle;
    }
    public void updateXPosType(POSTYPE newType, double[] bounds, double[] world) {
        for (int i = 0; i < xPoints.length; i++) {
            double xPix = xPosType.transform(xPoints[i], bounds, world);
            xPoints[i] = newType.itransform(xPix, bounds, world);
        }
        xPosType = newType;
    }
    public void updateYPosType(POSTYPE newType, double[] bounds, double[] world) {
        for (int i=0; i < yPoints.length; i++) {
            double yPix = yPosType.transform(yPoints[i], bounds, world);
            yPoints[i] = newType.itransform(yPix, bounds, world);
        }
        yPosType = newType;
    }

}
