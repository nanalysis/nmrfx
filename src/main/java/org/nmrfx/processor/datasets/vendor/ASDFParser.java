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
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.vendor;

import org.nmrfx.processor.math.Vec;
import java.util.ArrayList;

/**
 *
 * @author brucejohnson
 */
public class ASDFParser {

    enum ASDFMODE {

        NUMERIC,
        SQZ,
        DIF,
        DIFDUP,
        DUP;
    }
    final double firstX;
    final double lastX;
    final double deltaX;
    final double xFactor;
    final double yFactor;
    final int nPoints;

    /*
##XUNITS= HZ
##YUNITS= ARBITRARY UNITS
##XFACTOR= 1.46728315937252
##YFACTOR= 1
##FIRSTX= 24038.5
##LASTX= 0
##DELTAX= -1.46728315937252
##MAXY= 972201806
##MINY= -27593530
##NPOINTS= 16384
##FIRSTY= 2259260
     */
    ArrayList<Double> xValues = new ArrayList<>();
    ArrayList<Double> yValues = new ArrayList<>();

    public ASDFParser(double firstX, double lastX, double xFactor, double yFactor, int nPoints) {
        this.firstX = firstX;
        this.lastX = lastX;
        this.xFactor = xFactor;
        this.yFactor = yFactor;
        this.nPoints = nPoints;
        deltaX = (firstX - lastX) / (nPoints - 1);
    }

    public ArrayList<Double> getXValues() {
        return xValues;
    }

    public ArrayList<Double> getYValues() {
        return yValues;
    }

    public void dump() {
        int n = xValues.size();
        for (int i = 0; i < n; i++) {
            System.out.println(xValues.get(i) + " " + yValues.get(i));
        }
    }

    public void setVecMatData(Vec vecMat) {
        int n = yValues.size();
        vecMat.resize(n, false);
        for (int i = 0; i < n; i++) {
            vecMat.set(i, yValues.get(i));
        }
    }

    private void scaleY() {
        int n = xValues.size();
        for (int i = 0; i < n; i++) {
            yValues.set(i, yValues.get(i) * yFactor);
        }
    }

    public void fromASDF(String asdfString) {
        int stringLength = asdfString.length();
        StringBuilder sbuf = new StringBuilder();
        boolean startLine = true;
        boolean firstOrdinate = true;
        boolean firstChar = true;
        int i = 0;
        double xValue = firstX;
        ASDFMODE mode = ASDFMODE.NUMERIC;
        ASDFMODE lastMode = ASDFMODE.NUMERIC;
        while (i < stringLength) {
            char ch = asdfString.charAt(i);
            i++;

            if (startLine) {
                firstOrdinate = true;

                if ((ch == '\n') || (ch == '\r')) {
                    continue;
                }
                if (firstChar && (ch == ' ')) {
                    continue;
                }

                if (firstChar && ((ch == '+') || (ch == '-') || (ch == '.') || Character.isDigit(ch))) {
                    sbuf.append(ch);
                    firstChar = false;
                } else if (Character.isDigit(ch) || (ch == '.')) {
                    sbuf.append(ch);
                    firstChar = false;
                } else {

                    i--;
                    startLine = false;
                    firstChar = true;

                    if (sbuf.length() > 0) {
                        xValue = xFactor * Double.parseDouble(sbuf.toString());
                        sbuf.setLength(0);
                    }
                }
            } else if (firstChar) {
                firstChar = false;
                if (Character.isDigit(ch) || (ch == '-') || (ch == '+')) {
                    sbuf.append(ch);
                    mode = ASDFMODE.NUMERIC;
                } else if (ch == '@') {
                    mode = ASDFMODE.SQZ;
                    sbuf.append('0');
                } else if ((ch >= 'A') && (ch <= 'I')) {
                    mode = ASDFMODE.SQZ;
                    sbuf.append(ch - 'A' + 1);
                } else if ((ch >= 'a') && (ch <= 'i')) {
                    mode = ASDFMODE.SQZ;
                    sbuf.append('-');
                    sbuf.append(ch - 'a' + 1);
                } else if (ch == '%') {
                    mode = ASDFMODE.DIF;
                    sbuf.append('0');
                } else if ((ch >= 'J') && (ch <= 'R')) {
                    mode = ASDFMODE.DIF;
                    sbuf.append(ch - 'J' + 1);
                } else if ((ch >= 'j') && (ch <= 'r')) {
                    mode = ASDFMODE.DIF;
                    sbuf.append('-');
                    sbuf.append(ch - 'j' + 1);
                } else if (((ch >= 'S') && (ch <= 'Z')) || (ch == 's')) {
                    if (mode == ASDFMODE.DIF) {
                        mode = ASDFMODE.DIFDUP;
                    } else {
                        mode = ASDFMODE.DUP;
                    }
                    if (ch == 's') {
                        sbuf.append('9');
                    } else {
                        sbuf.append(ch - 'S' + 1);
                    }
                } else if ((ch == '\n') || (ch == '\r')) {
                    startLine = true;
                    firstChar = true;
                }
            } else {
                boolean gotDigit = false;
                if (Character.isDigit(ch) || (ch == '.')) {
                    sbuf.append(ch);
                    firstChar = false;
                    gotDigit = true;
                }
                // need this to handle last case where last char is digit
                if (!gotDigit || (i == stringLength)) {
                    firstChar = true;
                    i--;
                    if (sbuf.length() > 0) {
                        if ((mode == ASDFMODE.DUP) || (mode == ASDFMODE.DIFDUP)) {
                            int dupLevel = Integer.parseInt(sbuf.toString()) - 1;
                            sbuf.setLength(0);
                            firstChar = true;
                            for (int iLevel = 0; iLevel < dupLevel; iLevel++) {
                                double newValue;
                                if (mode == ASDFMODE.DIFDUP) {
                                    newValue = 2 * yValues.get(yValues.size() - 1) - yValues.get(yValues.size() - 2);
                                } else {
                                    newValue = yValues.get(yValues.size() - 1);
                                }
                                xValues.add(xValue);
                                xValue -= deltaX;
                                yValues.add(newValue);
                            }
                            lastMode = mode;
                            firstOrdinate = false;
                        } else {
                            double value = Double.parseDouble(sbuf.toString());
                            sbuf.setLength(0);
                            firstChar = true;
                            final double newValue;
                            if (mode == ASDFMODE.DIF) {
                                newValue = yValues.get(yValues.size() - 1) + value;
                            } else {
                                newValue = value;
                            }
                            if (firstOrdinate && (lastMode == ASDFMODE.DIF)) {
                                double lastValue = yValues.get(yValues.size() - 1);
                                if (newValue != lastValue) {
                                    System.out.println("check value error " + lastValue + " " + newValue);
                                }
                                xValue -= deltaX;
                            } else {
                                xValues.add(xValue);
                                xValue -= deltaX;
                                yValues.add(newValue);
                                lastMode = mode;
                            }
                            //System.out.println("yValue " + newValue);
                        }
                        firstOrdinate = false;
                    }
                }
            }
        }
        scaleY();
    }
}
