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

import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author brucejohnson
 */
public class AnnoLine extends AnnoShape {
    private static final Logger log = LoggerFactory.getLogger(AnnoLine.class);

    final double x1;
    final double y1;
    final double x2;
    final double y2;
    POSTYPE xPosType;
    POSTYPE yPosType;

    public AnnoLine(double x1, double y1, double x2, double y2,
            POSTYPE xPosType, POSTYPE yPosType) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.xPosType = xPosType;
        this.yPosType = yPosType;
    }

    @Override
    public void draw(GraphicsContextInterface gC, double[][] bounds, double[][] world) {
        try {
            gC.setStroke(stroke);
            gC.setLineWidth(lineWidth);
            double xp1 = xPosType.transform(x1, bounds[0], world[0]);
            double yp1 = yPosType.transform(y1, bounds[1], world[1]);
            double xp2 = xPosType.transform(x2, bounds[0], world[0]);
            double yp2 = yPosType.transform(y2, bounds[1], world[1]);
            gC.strokeLine(xp1, yp1, xp2, yp2);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
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
