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
 * Author: graham Class: Unfolding Desc: -
 */
public class Unfolding extends OptFunction {

    public Unfolding() {
        setVars(VecID.Y, VecID.X);
        setParams(VecID.A, VecID.B, VecID.C, VecID.M);

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
                        double c = getParamVal(VecID.C, pts);
                        double m = getParamVal(VecID.M, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];

                        return 1.0 / (1.0 + Math.exp(-m * (c - x)));
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
                        double c = getParamVal(VecID.C, pts);
                        double m = getParamVal(VecID.M, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];

                        return 1.0 - 1.0 / (1.0 + Math.exp(-m * (c - x)));
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
                        double m = getParamVal(VecID.M, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double expMCX = Math.exp(m * (c - x));
                        return (m * (a - b)) / (expMCX * (1 / expMCX + 1) * (1 / expMCX + 1));
                    }
                }, // dY/dM
                new Equation() {
                    public VecID name() {
                        return VecID.M;
                    }

                    public int getID() {
                        return getUnboundParamIndex(name());
                    }

                    public double value(double[] pts, double[] ival) {
                        double a = getParamVal(VecID.A, pts);
                        double b = getParamVal(VecID.B, pts);
                        double c = getParamVal(VecID.C, pts);
                        double m = getParamVal(VecID.M, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double expMCX = Math.exp(m * (c - x));
                        return ((a - b) * (c - x)) / (expMCX * (1 / expMCX + 1) * (1 / expMCX + 1));
                    }
                }
        });

        // y = (a-b)/(1.0+exp(-m*(c-x)))+b
        setFunction(new Equation() {
            public VecID name() {
                return VecID.Y;
            }

            public int getID() {
                return 1;
            }

            public double value(double[] pts, double[] ival) {
                double a = getParamVal(VecID.A, pts);
                double b = getParamVal(VecID.B, pts);
                double c = getParamVal(VecID.C, pts);
                double m = getParamVal(VecID.M, pts);
                double x = ival[getVarIndex(VecID.X) - 1];
                double deltaG_over_RT = m * (c - x);
                double p = 1.0 / (1.0 + Math.exp(-deltaG_over_RT));
                double y = (a - b) * p + b;
                return y;
            }
        });
    }

    public void calcGuessParams() {
        EstParam[] eps = getEstParams();

        for (int i = 0; i < eps.length; i++) {
            if (eps[i].isPending()) {
                switch (eps[i].getVecID()) {
                    case A:
                        double yAtMinX = DataUtil.getYAtMinX(VecID.Y, VecID.X, getDataSetPtr());
                        loadParamGuess(VecID.A, yAtMinX);
                        break;
                    case B:
                        double yAtMaxX = DataUtil.getYAtMaxX(VecID.Y, VecID.X, getDataSetPtr());
                        loadParamGuess(VecID.B, yAtMaxX);
                        break;
                    case C:
                        double xMid = DataUtil.getMidValue(VecID.Y, VecID.X, getDataSetPtr());
                        loadParamGuess(VecID.C, xMid);
                        break;
                    case M:
                        loadParamGuess(VecID.M, 1.0);
                        break;
                }
            }
        }
    }

    public String getFunctionName() {
        return "y = (a-b)/(1.0+exp(-m*(c-x)))+b";
    }
}
