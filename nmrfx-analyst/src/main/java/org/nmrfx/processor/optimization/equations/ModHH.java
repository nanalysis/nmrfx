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
 * Author: graham Class: ModHH Desc: -
 */
public class ModHH extends OptFunction {

    public ModHH() {
        setVars(VecID.Y, VecID.X);
        setParams(VecID.A, VecID.B, VecID.C, VecID.N);

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
                        double n = getParamVal(VecID.N, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double f = (1.0 + Math.pow(10.0, n * (c - x)));
                        return (1 - 1.0 / f);
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
                        double n = getParamVal(VecID.N, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double f = (1.0 + Math.pow(10.0, n * (c - x)));
                        return 1.0 / f;
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
                        double n = getParamVal(VecID.N, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double f1 = Math.pow(10.0, n * (c - x));
                        double f = (1.0 + f1);
                        return (f1 * a * n * Math.log(10)) / (f * f) - (f1 * b * n * Math.log(10)) / (f * f);
                    }
                },
                // dY/dN
                new Equation() {
                    public VecID name() {
                        return VecID.N;
                    }

                    public int getID() {
                        return getUnboundParamIndex(name());
                    }

                    public double value(double[] pts, double[] ival) {
                        double a = getParamVal(VecID.A, pts);
                        double b = getParamVal(VecID.B, pts);
                        double c = getParamVal(VecID.C, pts);
                        double n = getParamVal(VecID.N, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double f1 = Math.pow(10.0, n * (c - x));
                        double f = (1.0 + f1);
                        return (f1 * a * Math.log(10) * (c - x)) / (f * f) - (f1 * b * Math.log(10) * (c - x)) / (f * f);
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
                double n = getParamVal(VecID.N, pts);
                double x = ival[getVarIndex(VecID.X) - 1];

                double f = (1.0 + Math.pow(10.0, n * (c - x)));

                return a * (1 - 1.0 / f) + b / f;

            }
        });
    }

    public void calcGuessParams() {
        EstParam[] eps = getEstParams();

        for (int i = 0; i < eps.length; i++) {
            if (eps[i].isPending()) {
                switch (eps[i].getVecID()) {
                    case C:
                        double yMax = DataUtil.getMaxValue(VecID.Y, getDataSetPtr());
                        loadParamGuess(VecID.C, yMax);
                        break;
                    case A:
                        double yMin = DataUtil.getMinValue(VecID.Y, getDataSetPtr());
                        loadParamGuess(VecID.A, yMin);
                        break;
                    case B:
                        double xMid = DataUtil.getMidValue(VecID.Y, VecID.X, getDataSetPtr());
                        loadParamGuess(VecID.B, Math.log10(xMid));
                        break;
                }
            }
        }

    }

    @Override
    public String getFunctionName() {
        return "y = A*(1- (1+10^(n*(pK-pH)))^-1) + B*(1+10^(n*(pK-pH)))^-1";
    }
}
