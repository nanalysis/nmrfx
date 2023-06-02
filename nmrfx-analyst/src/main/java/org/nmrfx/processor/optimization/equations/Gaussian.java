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
 * Author: graham Class: Gaussian Desc: -
 */
public class Gaussian extends OptFunction {

    public Gaussian() {
        setVars(VecID.Y, VecID.X);
        setParams(VecID.A, VecID.B, VecID.F);

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
                        double fr = getParamVal(VecID.F, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double dX = (fr - x);
                        return 1 / Math.exp(dX * dX / b);
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
                        double fr = getParamVal(VecID.F, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double dX = (fr - x);
                        return (a * dX * dX) / (b * b * Math.exp(dX * dX / b));

                    }
                },
                // dY/dC
                new Equation() {
                    public VecID name() {
                        return VecID.F;
                    }

                    public int getID() {
                        return getUnboundParamIndex(name());
                    }

                    public double value(double[] pts, double[] ival) {
                        double a = getParamVal(VecID.A, pts);
                        double b = getParamVal(VecID.B, pts);
                        double fr = getParamVal(VecID.F, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double dX = fr - x;
                        return -(a * (2 * fr - 2 * x)) / (b * Math.exp(dX * dX / b));

                    }
                }
        });

        // y = a * (exp(-x / b) + exp(-x / C))
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
                double fr = getParamVal(VecID.F, pts);
                double x = ival[getVarIndex(VecID.X) - 1];
                double dX = (fr - x);
                return a * Math.exp(-dX * dX / b);
            }
        });
    }

    @Override
    public void calcGuessParams() {
        EstParam[] eps = getEstParams();
        double xMid = DataUtil.getMidValue1Side(VecID.Y, VecID.X, getDataSetPtr());
        double xAtMaxY = DataUtil.getYAtMaxX(VecID.X, VecID.Y, getDataSetPtr());

        for (int i = 0; i < eps.length; i++) {
            if (eps[i].isPending()) {
                switch (eps[i].getVecID()) {
                    case A:
                        loadParamGuess(VecID.A, DataUtil.getMaxValue(VecID.Y, getDataSetPtr()));
                        break;
                    case B:
                        loadParamGuess(VecID.B, Math.abs((xMid - xAtMaxY) * 2.0));
                        break;
                    case F:
                        loadParamGuess(VecID.F, xAtMaxY);
                        break;
                }
            }
        }
    }

    @Override
    public String getFunctionName() {
        return "y = a * (exp(-(x-f)^2 / b)";
    }
}
