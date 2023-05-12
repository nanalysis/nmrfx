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
 * Author: graham Class: ExpDecayA Desc: -
 */
public class UnderDampedHarmonicA extends OptFunction {

    public UnderDampedHarmonicA() {
        setVars(VecID.Y, VecID.X);
        setParams(VecID.A, VecID.B, VecID.W, VecID.S);

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
                    double w = getParamVal(VecID.W, pts);
                    double s = getParamVal(VecID.S, pts);
                    double x = ival[getVarIndex(VecID.X) - 1];

                    return Math.exp(-b * x) * Math.cos(w * x - s);
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
                    double w = getParamVal(VecID.W, pts);
                    double s = getParamVal(VecID.S, pts);
                    double x = ival[getVarIndex(VecID.X) - 1];

                    return -a * x * Math.exp(-b * x) * Math.cos(w * x - s);
                }
            },
            // dY/dW
            new Equation() {
                public VecID name() {
                    return VecID.W;
                }

                public int getID() {
                    return getUnboundParamIndex(name());
                }

                public double value(double[] pts, double[] ival) {
                    double a = getParamVal(VecID.A, pts);
                    double b = getParamVal(VecID.B, pts);
                    double w = getParamVal(VecID.W, pts);
                    double s = getParamVal(VecID.S, pts);
                    double x = ival[getVarIndex(VecID.X) - 1];

                    return -a * x * Math.exp(-b * x) * Math.sin(w * x - s);
                }
            },
            // dY/dS
            new Equation() {
                public VecID name() {
                    return VecID.S;
                }

                public int getID() {
                    return getUnboundParamIndex(name());
                }

                public double value(double[] pts, double[] ival) {
                    double a = getParamVal(VecID.A, pts);
                    double b = getParamVal(VecID.B, pts);
                    double w = getParamVal(VecID.W, pts);
                    double s = getParamVal(VecID.S, pts);
                    double x = ival[getVarIndex(VecID.X) - 1];

                    return a * Math.exp(-b * x) * Math.sin(w * x - s);
                }
            }
        });

        // ID#0009
        // SPECIAL NOTE!!! changing -> y = A * exp(-x * C) * exp(-i * x * B)
        //                 to       -> f = A * exp(-Bt) * cos(Wt - S) (4 param: a,b,w,s)
        // w/ one less     to       -> f = exp(-Bt)(Ccos(Wt) + Dsin(Wt))
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
                double w = getParamVal(VecID.W, pts);
                double s = getParamVal(VecID.S, pts);
                double x = ival[getVarIndex(VecID.X) - 1];

                return a * Math.exp(-b * x) * Math.cos(w * x - s);
            }
        });
    }

    @Override
    public void calcGuessParams() {
        EstParam[] eps = getEstParams();

        for (int i = 0; i < eps.length; i++) {
            if (eps[i].isPending()) {
                switch (eps[i].getVecID()) {
                    case A:
                        loadParamGuess(VecID.A, 1.0);
                        break;
                    case B:
                        loadParamGuess(VecID.B, 0.1);
                        break;
                    case W:
                        loadParamGuess(VecID.W, 0.1);
                        break;
                    case S:
                        loadParamGuess(VecID.S, 0.0);
                        break;
                }
            }
        }
    }

    public String getFunctionName() {
        return "y = a * exp(-bx) * cos(wx - s)";
    }
}
