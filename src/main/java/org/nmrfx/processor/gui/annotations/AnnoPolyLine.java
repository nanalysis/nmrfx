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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.processor.gui.CanvasAnnotation;

/**
 *
 * @author brucejohnson
 */
public class AnnoPolyLine extends AnnoShape {

    final double[] xPoints;
    final double[] yPoints;
    final double[] xCPoints;
    final double[] yCPoints;
    POSTYPE xPosType;
    POSTYPE yPosType;

    public AnnoPolyLine(List<Double> xList, List<Double> yList,
            POSTYPE xPosType, POSTYPE yPosType) {
        xPoints = new double[xList.size()];
        yPoints = new double[yList.size()];
        for (int i=0;i<xPoints.length;i++) {
            xPoints[i] = xList.get(i);
            yPoints[i] = yList.get(i);
        }

        xCPoints = new double[xPoints.length];
        yCPoints = new double[yPoints.length];
        this.xPosType = xPosType;
        this.yPosType = yPosType;
    }

    public AnnoPolyLine(double[] xPoints, double[] yPoints,
            POSTYPE xPosType, POSTYPE yPosType) {
        this.xPoints = xPoints;
        this.yPoints = yPoints;
        xCPoints = new double[xPoints.length];
        yCPoints = new double[yPoints.length];
        this.xPosType = xPosType;
        this.yPosType = yPosType;
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
        } catch (GraphicsIOException ex) {
            Logger.getLogger(AnnoPolyLine.class.getName()).log(Level.SEVERE, null, ex);
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
