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
 * Author: graham Class: RDispSin Desc: -
 */
public class RDispESin extends OptFunction {

    public RDispESin() {
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
                        double a = getParamVal(VecID.A, pts);
                        double b = getParamVal(VecID.B, pts);
                        double c = getParamVal(VecID.C, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        return Math.exp((a * x * Math.sin(b / x)) / b - c - a) * ((x * Math.sin(b / x)) / b - 1);

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

                        return -Math.exp(-(a - a * Math.sin(b / x) / (b / x) + c))
                                * ((a * Math.sin(b / x)) / (b * b / x) - (a * Math.cos(b / x)) / b);
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
                        double x = ival[getVarIndex(VecID.X) - 1];
// fixme check for x=0;
                        return -Math.exp(-(a - a * Math.sin(b / x) / (b / x) + c));
                    }
                }
        });

        // y = a-a*sin(b*x)/(b*x)+c
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
                double x = ival[getVarIndex(VecID.X) - 1];
// fixme check for x=0;
                return Math.exp(-(a - a * Math.sin(b / x) / (b / x) + c));
            }
        });
    }

    public void calcGuessParams() {
        EstParam[] eps = getEstParams();
        double yAtMinX = DataUtil.getYAtMinX(VecID.Y, VecID.X, getDataSetPtr());
        double yAtMaxX = DataUtil.getYAtMaxX(VecID.Y, VecID.X, getDataSetPtr());

        for (int i = 0; i < eps.length; i++) {
            if (eps[i].isPending()) {
                switch (eps[i].getVecID()) {
                    case A:
                        loadParamGuess(VecID.A, (Math.log(yAtMaxX) - Math.log(yAtMinX)));
                        break;
                    case B:
                        double xMid = DataUtil.getMidValue(VecID.Y, VecID.X, getDataSetPtr());
                        loadParamGuess(VecID.B, 2.0 * xMid);
                        break;
                    case C:
                        loadParamGuess(VecID.C, -Math.log(yAtMaxX));
                        break;
                }
            }
        }
    }

    public String getFunctionName() {
        return "y = exp(-(A-A*sin(B/x)/(B/x)+C)";
    }
}
