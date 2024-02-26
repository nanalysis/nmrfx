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
 * Author: graham Class: Quadratic10F Desc: -
 */
public class Quadratic10F extends OptFunction {

    public Quadratic10F() {
        setVars(VecID.Y, VecID.X, VecID.P);
        setParams(VecID.A, VecID.B, VecID.C);

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
                        double a = getParamVal(VecID.A, pts);
                        double b = getParamVal(VecID.B, pts);
                        double c = getParamVal(VecID.C, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double p = ival[getVarIndex(VecID.P) - 1];

                        double n1 = a * p + x + Math.pow(10.0, b);
                        double s1 = Math.sqrt(n1 * n1 - 4.0 * x * a * p);

                        return (c * (p + (4.0 * p * x - 2.0 * p * n1) / (2.0 * s1))) / (2.0 * a * p) - (c * (x + a * p - s1 + Math.pow(10, b))) / (2.0 * a * a * p);
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
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double p = ival[getVarIndex(VecID.P) - 1];
                        double b10 = Math.pow(10., b);
                        double n1 = a * p + x + b10;
                        double s1 = Math.sqrt(n1 * n1 - 4.0 * x * a * p);

                        return ((-c) * (b10 * Math.log(10) * (n1) / (s1) - b10 * Math.log(10))) / (2 * a * p);
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
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double p = ival[getVarIndex(VecID.P) - 1];

                        double n1 = a * p + x + Math.pow(10.0, b);
                        double s1 = Math.sqrt(n1 * n1 - 4.0 * x * a * p);

                        return (n1 - s1) / (2.0 * a * p);
                    }
                }
        });

        // f(x, y) = (n1 - s1) / 2y where
        // > n1 = y + x + b
        // > s1 = sqrt(n1^2 - 4xy)
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
                double x = ival[getVarIndex(VecID.X) - 1];
                double p = ival[getVarIndex(VecID.P) - 1];

                double n1 = a * p + x + Math.pow(10.0, b);
                double s1 = Math.sqrt(n1 * n1 - 4.0 * x * a * p);

                return c * (n1 - s1) / (2.0 * a * p);
            }
        });
    }

    public void calcGuessParams() {
        EstParam[] eps = getEstParams();

        for (int i = 0; i < eps.length; i++) {
            if (eps[i].isPending()) {
                switch (eps[i].getVecID()) {
                    case C:
                        double yMax = DataUtil.getYAtMaxX(VecID.Y, VecID.X, getDataSetPtr());
                        loadParamGuess(VecID.C, yMax);
                        break;
                    case A:
                        loadParamGuess(VecID.A, 1.0);
                        break;
                    case B:
                        double xMid = DataUtil.getMidValue(VecID.Y, VecID.X, getDataSetPtr());
                        loadParamGuess(VecID.B, Math.log10(xMid));
                        break;
                }
            }
        }

    }

    public String getFunctionName() {
        return "f = C * "
                + "((Ap + x + 10^b) - "
                + "((Ap + x + 10^b)^2 - "
                + "4Apx)^0.5)) / (2Ap)";
    }
}
