/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.optimization;

/**
 * @author graham
 */
public class DataUtil {

    public static double getMinValue(VecID varName, DataRMap drm) {
        double minVal = Double.MAX_VALUE;
        double curVal;

        for (int i = 0; i < drm.size(); i++) {
            curVal = drm.getValue(varName, i);
            if (curVal < minVal) {
                minVal = curVal;
            }
        }

        return minVal;
    }

    public static double getMaxValue(VecID varName, DataRMap drm) {
        double maxVal = Double.NEGATIVE_INFINITY;
        double curVal;

        for (int i = 0; i < drm.size(); i++) {
            curVal = drm.getValue(varName, i);
            if (curVal > maxVal) {
                maxVal = curVal;
            }
        }

        return maxVal;
    }

    public static double getMidValue(VecID dVarName, VecID iVarName, DataRMap drm) {
        double hh = (DataUtil.getMaxValue(dVarName, drm) + DataUtil.getMinValue(dVarName, drm)) / 2.0;
        double deltaUp = Double.MAX_VALUE;
        double deltaDown = Double.MAX_VALUE;
        double dUp, iUp;
        double dDown, iDown;

        dUp = iUp = dDown = iDown = 0;

        for (int i = 0; i < drm.size(); i++) {
            double dvar = drm.getValue(dVarName, i);
            double ivar = drm.getValue(iVarName, i);
            double ddvar = dvar - hh;

            if (ddvar >= 0 && ddvar < deltaUp) {
                deltaUp = ddvar;
                dUp = dvar;
                iUp = ivar;
            } else if (ddvar < 0 && -ddvar < deltaDown) {
                deltaDown = -ddvar;
                dDown = dvar;
                iDown = ivar;
            }
        }

        double mid;

        if (dUp == dDown) {
            mid = (iUp + iDown) / 2.0;
        } else {
            mid = ((hh - dDown) / (dUp - dDown) * (iUp - iDown)) + iDown;
        }

        return mid;
    }

    public static double getMidValue1Side(VecID dVarName, VecID iVarName, DataRMap drm) {
        double hh = DataUtil.getMaxValue(dVarName, drm) / 2.0;
        double deltaMin = Double.MAX_VALUE;
        double iMid = 0;

        for (int i = 0; i < drm.size(); i++) {
            double dvar = drm.getValue(dVarName, i);
            double ivar = drm.getValue(iVarName, i);
            double ddvar = Math.abs(dvar - hh);

            if (ddvar < deltaMin) {
                deltaMin = ddvar;
                iMid = ivar;
            }
        }

        return iMid;
    }

    public static double getXAtMinValue(VecID yVarName, VecID xVarName, DataRMap drm) {
        double minVal = drm.getValue(yVarName, 0);
        double curVal;
        double minX = 0;
        for (int i = 0; i < drm.size(); i++) {
            curVal = drm.getValue(yVarName, i);
            if (curVal < minVal) {
                minVal = curVal;
                minX = drm.getValue(xVarName, i);
            }
        }

        return minX;
    }

    public static double getYAtMaxX(VecID yVarName, VecID xVarName, DataRMap drm) {
        double maxVal = Double.NEGATIVE_INFINITY;
        double curVal;
        double maxY = 0;
        for (int i = 0; i < drm.size(); i++) {
            curVal = drm.getValue(xVarName, i);
            if (curVal > maxVal) {
                maxVal = curVal;
                maxY = drm.getValue(yVarName, i);
            }
        }
        return maxY;
    }

    public static double getYAtMinX(VecID yVarName, VecID xVarName, DataRMap drm) {
        double minVal = Double.MAX_VALUE;
        double curVal;
        double minY = 0;
        for (int i = 0; i < drm.size(); i++) {
            curVal = drm.getValue(xVarName, i);
            if (curVal < minVal) {
                minVal = curVal;
                minY = drm.getValue(yVarName, i);
            }
        }
        return minY;
    }
}
