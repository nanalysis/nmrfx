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
 * Author: graham Class: ExpDecayA Desc: -
 */
public class JMod extends OptFunction {

    public JMod() {
        setVars(VecID.Y, VecID.X);
        setParams(VecID.A, VecID.B, VecID.C, VecID.J);

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

                        return -c * Math.exp(-2.0 * b * x);
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
                        double J = getParamVal(VecID.J, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];

                        return (2.0 * c * x * (a - Math.cos(2.0 * J * Math.PI * x))) * Math.exp(-2.0 * b * x);
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
                        double J = getParamVal(VecID.J, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        return -(a - Math.cos(2.0 * J * Math.PI * x)) * Math.exp(-2.0 * b * x);
                    }
                },
                // dY/dJ
                new Equation() {
                    public VecID name() {
                        return VecID.J;
                    }

                    public int getID() {
                        return getUnboundParamIndex(name());
                    }

                    public double value(double[] pts, double[] ival) {
                        double b = getParamVal(VecID.B, pts);
                        double c = getParamVal(VecID.C, pts);
                        double J = getParamVal(VecID.J, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        return -(2.0 * Math.PI * c * x * Math.sin(2.0 * Math.PI * J * x)) * Math.exp(-2.0 * b * x);
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
                double c = getParamVal(VecID.C, pts);
                double J = getParamVal(VecID.J, pts);
                double x = ival[getVarIndex(VecID.X) - 1];

                return c * Math.exp(-2.0 * b * x) * (-a + Math.cos(2.0 * Math.PI * J * x));
            }
        });
    }

    @Override
    public void calcGuessParams() {
        EstParam[] eps = getEstParams();
        double xMin = DataUtil.getXAtMinValue(VecID.Y, VecID.X, getDataSetPtr());
        double yMax = DataUtil.getMaxValue(VecID.Y, getDataSetPtr());
        double yMin = DataUtil.getMinValue(VecID.Y, getDataSetPtr());

        for (int i = 0; i < eps.length; i++) {
            if (eps[i].isPending()) {
                switch (eps[i].getVecID()) {
                    case A:
                        loadParamGuess(VecID.A, 0.02);
                        break;
                    case J:
                        if (xMin > Double.MIN_VALUE) {
                            loadParamGuess(VecID.J, 1.0 / (2.0 * xMin));
                        } else {
                            loadParamGuess(VecID.J, 10.0);
                        }
                        break;
                    case C:
                        loadParamGuess(VecID.C, yMax);
                        break;
                    case B:
                        double b = 5.0;
                        if (xMin > Double.MIN_VALUE) {
                            double decay = Math.abs(yMin) / yMax;
                            b = -Math.log(decay) / xMin;
                        }

                        loadParamGuess(VecID.B, b);
                        break;
                }
            }
        }
    }

    @Override
    public String getFunctionName() {
        return "y = c * exp(-2.0*b*x) * (-a+cos(2.0*PI*J*x)";
    }
}
