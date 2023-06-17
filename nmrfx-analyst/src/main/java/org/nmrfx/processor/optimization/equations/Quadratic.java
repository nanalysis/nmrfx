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
 * Author: graham Class: Quadratic Desc: -
 */
public class Quadratic extends OptFunction {

    public Quadratic() {
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
                        double b = getParamVal(VecID.B, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double p = ival[getVarIndex(VecID.P) - 1];

                        double n1 = p + x + b;
                        double s1 = Math.sqrt(n1 * n1 - 4.0 * x * p);

                        return 1.0 - (n1 - s1) / (2.0 * p);
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

                        double n1 = p + x + b;
                        double s1 = Math.sqrt(n1 * n1 - 4.0 * x * p);
                        return ((a - c) * ((2 * n1) / (2 * s1) - 1)) / (2 * p);
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
                        double b = getParamVal(VecID.B, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double p = ival[getVarIndex(VecID.P) - 1];

                        double n1 = p + x + b;
                        double s1 = Math.sqrt(n1 * n1 - 4.0 * x * p);

                        return (n1 - s1) / (2.0 * p);
                    }
                }
        });

        // f(x, y) = (n1 - s1) / 2y where
        // > n1 = y + x + b
        // > s1 = sqrt(n1^2 - 4xy)
        setFunction(new Equation() {
            public VecID name() {
                return VecID.P;
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

                double delta = c - a;
                double n1 = p + x + b;
                double s1 = Math.sqrt(n1 * n1 - 4.0 * x * p);

                return a + delta * (n1 - s1) / (2.0 * p);
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
                        double yMin = DataUtil.getYAtMinX(VecID.Y, VecID.X, getDataSetPtr());
                        loadParamGuess(VecID.A, yMin);
                        break;
                    case B:
                        double xMid = DataUtil.getMidValue(VecID.Y, VecID.X, getDataSetPtr());
                        loadParamGuess(VecID.B, xMid);
                        break;
                }
            }
        }

    }

    @Override
    public String getFunctionName() {
        return "f = A + (C - A) * "
                + "((p + x + b) - "
                + "((p + x + b)^2 - "
                + "4px)^0.5)) / (2p)";
    }
}
