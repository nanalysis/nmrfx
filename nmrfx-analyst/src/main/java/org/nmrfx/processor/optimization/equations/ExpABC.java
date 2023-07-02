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
 * Author: graham Class: ExpABC Desc: -
 */
public class ExpABC extends OptFunction {

    public ExpABC() {
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

                    /*SUICIDE CHANGE[REM COMMENT IF NO PROB]
                     //double idb;

                     /*if(bParam == 0.0){
                     idb = Double.NEGATIVE_INFINITY;
                     }else{
                     idb = -ival / bParam;
                     }*/
                        return Math.exp(-x * b);
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
                        double aParam = getParamVal(VecID.A, pts);
                        double bParam = getParamVal(VecID.B, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];

                        return -1.0 * aParam * x * Math.exp(-1.0 * bParam * x);
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
                        return 1.0;
                    }
                }
        });

        // y = a * exp(-x * b) + c
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

                return a * Math.exp(-x * b) + c;
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
                        double yAtMaxX = DataUtil.getYAtMaxX(VecID.Y, VecID.X, getDataSetPtr());
                        loadParamGuess(VecID.A, yAtMinX - yAtMaxX);
                        break;
                    case B:
                        double xMid = DataUtil.getMidValue(VecID.Y, VecID.X, getDataSetPtr());
                        loadParamGuess(VecID.B, 1.0 / (xMid / 0.693));
                        break;
                    case C:
                        yAtMaxX = DataUtil.getYAtMaxX(VecID.Y, VecID.X, getDataSetPtr());
                        loadParamGuess(VecID.C, yAtMaxX);
                        break;
                }
            }
        }
    }

    public String getFunctionName() {
        return "y = a * exp(-x * b) + c";
    }
}
