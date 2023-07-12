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
 * Author: graham Class: Logistic Desc: -
 */
public class LogisticA extends OptFunction {

    public LogisticA() {
        setVars(VecID.Y, VecID.X);
        setParams(VecID.A, VecID.B, VecID.C, VecID.D);

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
                        double c = getParamVal(VecID.C, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];

                        double xc = Math.pow(x, c);

                        return -xc / (xc + Math.pow(b, c));
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
                        double c = getParamVal(VecID.C, pts);
                        double d = getParamVal(VecID.D, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];

                        double xc = Math.pow(x, c);
                        double bc = Math.pow(b, c);
                        double xcpbc = xc + bc;

                        return c / b * bc * xc * (a - d) / (xcpbc * xcpbc);
                    }
                },
                // dY/dC
                new Equation() {
                    public VecID name() {
                        return VecID.C;
                    }

                    public int getID() {
                        return getUnboundParamIndex(name());
                    }

                    public double value(double[] pts, double[] ival) {
                        double a = getParamVal(VecID.A, pts);
                        double b = getParamVal(VecID.B, pts);
                        double c = getParamVal(VecID.C, pts);
                        double d = getParamVal(VecID.D, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];

                        double xc = Math.pow(x, c);
                        double alpha = xc / (xc + Math.pow(b, c));
                        double beta = (a - d) * Math.log(x);

                        return alpha * beta * (alpha - 1.0);
                    }
                },
                // dY/dD
                new Equation() {
                    public VecID name() {
                        return VecID.D;
                    }

                    public int getID() {
                        return getUnboundParamIndex(name());
                    }

                    public double value(double[] pts, double[] ival) {
                        double b = getParamVal(VecID.B, pts);
                        double c = getParamVal(VecID.C, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];

                        double xc = Math.pow(x, c);

                        return xc / (xc + Math.pow(b, c));
                    }
                }
        });

        // y = ((d - a) * x^c) / (x^c * b^c)
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
                double c = getParamVal(VecID.C, pts);
                double d = getParamVal(VecID.D, pts);
                double x = ival[getVarIndex(VecID.X) - 1];

                double xc = Math.pow(x, c);

                return (d - a) * xc / (xc + Math.pow(b, c));
            }
        });
    }

    @Override
    public void calcGuessParams() {
        EstParam[] eps = getEstParams();

        for (int i = 0; i < eps.length; i++) {
            switch (eps[i].getVecID()) {
                case A:
                    double yMax = DataUtil.getMaxValue(VecID.Y, getDataSetPtr());
                    loadParamGuess(VecID.A, yMax);
                    break;
                case B:
                    double xMid = DataUtil.getMidValue(VecID.Y, VecID.X, getDataSetPtr());
                    loadParamGuess(VecID.B, xMid);
                    break;
                case C:
                    loadParamGuess(VecID.C, 1.0);
                    break;
                case D:
                    double yMin = DataUtil.getMinValue(VecID.Y, getDataSetPtr());
                    loadParamGuess(VecID.D, yMin);
                    break;
            }
        }
    }

    public String getFunctionName() {
        return "y = ((d - a) * x^c) / (x^c * b^c)";
    }
}
