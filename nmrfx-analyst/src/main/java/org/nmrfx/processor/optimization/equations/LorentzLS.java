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
package org.nmrfx.processor.optimization.equations;

import org.nmrfx.processor.optimization.DataUtil;
import org.nmrfx.processor.optimization.Equation;
import org.nmrfx.processor.optimization.EstParam;
import org.nmrfx.processor.optimization.VecID;

/**
 * Author: graham Class: LorentzLS Desc: -
 */
public class LorentzLS extends OptFunction {

    public LorentzLS() {
        setVars(VecID.Y, VecID.X);
        setParams(VecID.A, VecID.B, VecID.F);

        setPartialDerivatives(new Equation[]{
                // dY/dA
                new Equation() {
                    public VecID name() {
                        return VecID.A;
                    }

                    public int getID() {
                        return getUnboundParamIndex(name());
                    }

                    public double value(double[] pts, double[] ival) {
                        double b = getParamVal(VecID.B, pts);
                        double f = getParamVal(VecID.F, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double foffset = x - f;

                        return ((b * b) / (b * b + 4.0 * foffset * foffset));
                    }
                },
                // dY/dB
                new Equation() {
                    public VecID name() {
                        return VecID.B;
                    }

                    public int getID() {
                        return getUnboundParamIndex(name());
                    }

                    public double value(double[] pts, double[] ival) {
                        double a = getParamVal(VecID.A, pts);
                        double b = getParamVal(VecID.B, pts);
                        double f = getParamVal(VecID.F, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double foffset = x - f;
                        double bsq = b * b;
                        double denom = bsq + 4 * foffset * foffset;

                        return 2 * a * b * (1.0 / (denom) - bsq / (denom * denom));
                    }
                }, // dY/dF
                new Equation() {
                    public VecID name() {
                        return VecID.F;
                    }

                    public int getID() {
                        return getUnboundParamIndex(name());
                    }

                    public double value(double[] pts, double[] ival) {
                        double a = getParamVal(VecID.A, pts);
                        double b = getParamVal(VecID.B, pts);
                        double f = getParamVal(VecID.F, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double foffset = x - f;
                        double denom = (b * b + 4 * foffset * foffset);

                        return -(a * b * b * (8 * f - 8 * x)) / (denom * denom);
                    }
                }
        });

        setFunction(new Equation() {
            public VecID name() {
                return VecID.Y;
            }

            public int getID() {
                return getUnboundParamIndex(name());
            }

            public double value(double[] pts, double[] ival) {
                double a = getParamVal(VecID.A, pts);
                double b = getParamVal(VecID.B, pts);
                double f = getParamVal(VecID.F, pts);
                double x = ival[getVarIndex(VecID.X) - 1];
                double foffset = x - f;
                return a * b * b / (b * b + 4 * foffset * foffset);
            }
        });
    }

    public void calcGuessParams() {
        EstParam[] eps = getEstParams();
        double xMid = DataUtil.getMidValue1Side(VecID.Y, VecID.X, getDataSetPtr());
        double xAtMaxY = DataUtil.getYAtMaxX(VecID.X, VecID.Y, getDataSetPtr());
        double maxY = DataUtil.getMaxValue(VecID.Y, getDataSetPtr());
        for (int i = 0; i < eps.length; i++) {
            if (eps[i].isPending()) {
                switch (eps[i].getVecID()) {
                    case A:
                        loadParamGuess(VecID.A, maxY);
                        break;
                    case B:
                        loadParamGuess(VecID.B, Math.abs((xMid - xAtMaxY) * 2.0));
                        break;
                    case F:
                        loadParamGuess(VecID.F, xAtMaxY);
                        break;
                }
            }
        }
    }

    public String getFunctionName() {
        return "y = a*b^2/(b^2 + 4*(x-f)^2)";
    }
}
