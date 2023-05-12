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

import org.nmrfx.processor.optimization.*;

/**
 * Author: graham Class: LogisticB Desc: -
 */
public class LogisticB extends OptFunction {

    public LogisticB() {
        setVars(VecID.Y, VecID.X);
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

                    return -x / (x + b) + 1.0;
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

                    double add = x + b;

                    return ((a - c) * x) / (add * add);
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

                    return x / (x + b);
                }
            }
        });

        // y = (c - a)x / (x + b) + a
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

                return x * (c - a) / (x + b) + a;
            }
        });
    }

    @Override
    public void calcGuessParams() {
        EstParam[] eps = getEstParams();

        double xMax = DataUtil.getMaxValue(VecID.X, getDataSetPtr());
        double xMin = DataUtil.getMinValue(VecID.X, getDataSetPtr());

        double yMax = DataUtil.getMaxValue(VecID.Y, getDataSetPtr());
        double yMin = DataUtil.getMinValue(VecID.Y, getDataSetPtr());

        for (int i = 0; i < eps.length; i++) {
            if (eps[i].isPending()) {
                switch (eps[i].getVecID()) {
                    case A:
                        loadParamGuess(VecID.A, (xMin > xMax) ? yMax : yMin);
                        break;
                    case B:
                        double xMid = DataUtil.getMidValue(VecID.Y, VecID.X, getDataSetPtr());
                        loadParamGuess(VecID.B, xMid);
                        break;
                    case C:
                        loadParamGuess(VecID.C, (xMin > xMax) ? yMin : yMax);
                        break;
                }
            }
        }
    }

    public String getFunctionName() {
        return "y = ((c - a) * x) / (x + B) + A";
    }
}
