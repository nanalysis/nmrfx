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

import org.nmrfx.processor.optimization.Equation;
import org.nmrfx.processor.optimization.EstParam;
import org.nmrfx.processor.optimization.VecID;

/**
 * Author: graham Class: UnderDampedHarmonicB Desc: -
 */
public class UnderDampedHarmonicB extends OptFunction {

    public UnderDampedHarmonicB() {
        setVars(VecID.Y, VecID.X);
        setParams(VecID.B, VecID.C, VecID.D, VecID.W);

        setPartialDerivatives(new Equation[]{
                // dY/dB
                new Equation() {
                    public VecID name() {
                        return VecID.B;
                    }

                    public int getID() {
                        return getUnboundParamIndex(name());
                    }

                    public double value(double[] pts, double[] ival) {
                        double b = getParamVal(VecID.B, pts);
                        double c = getParamVal(VecID.C, pts);
                        double d = getParamVal(VecID.D, pts);
                        double w = getParamVal(VecID.W, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];

                        double ang = w * x;

                        return -x * Math.exp(-b * x) * (c * Math.cos(ang) + d * Math.sin(ang));
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
                        double w = getParamVal(VecID.W, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];

                        return Math.exp(-b * x) * Math.cos(w * x);
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
                        double w = getParamVal(VecID.W, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];

                        return Math.exp(-b * x) * Math.sin(w * x);
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
                        double b = getParamVal(VecID.B, pts);
                        double c = getParamVal(VecID.C, pts);
                        double d = getParamVal(VecID.D, pts);
                        double w = getParamVal(VecID.W, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];

                        double ang = w * x;

                        return x * Math.exp(-b * x) * (d * Math.cos(ang) + c * Math.sin(ang));
                    }
                }
        });

        // f = exp(-b * t) * [c * cos(w * t) + d * sin(w * t)]
        setFunction(new Equation() {
            public VecID name() {
                return VecID.Y;
            }

            public int getID() {
                return getUnboundParamIndex(name());
            }

            public double value(double[] pts, double[] ival) {
                double b = getParamVal(VecID.B, pts);
                double c = getParamVal(VecID.C, pts);
                double d = getParamVal(VecID.D, pts);
                double w = getParamVal(VecID.W, pts);
                double x = ival[getVarIndex(VecID.X) - 1];

                double ang = w * x;

                return Math.exp(-b * x) * (c * Math.cos(ang) + d * Math.sin(ang));
            }
        });
    }

    @Override
    public void calcGuessParams() {
        EstParam[] eps = getEstParams();

        for (int i = 0; i < eps.length; i++) {
            if (eps[i].isPending()) {
                switch (eps[i].getVecID()) {
                    case B:
                        loadParamGuess(VecID.B, 0.1);
                        break;
                    case C:
                        loadParamGuess(VecID.C, 1.0);
                        break;
                    case D:
                        loadParamGuess(VecID.D, 1.0);
                        break;
                    case W:
                        loadParamGuess(VecID.W, 0.1);
                        break;
                }
            }
        }
    }

    public String getFunctionName() {
        return "y = exp(-B * x) * (Ccos(Wx) + Dsin(Wx)";
    }
}
