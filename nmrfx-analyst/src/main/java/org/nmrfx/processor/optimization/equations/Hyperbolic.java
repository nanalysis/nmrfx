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

/*
 * Specific equation class derived from EstFunction
 * and open the template in the editor.
 */
package org.nmrfx.processor.optimization.equations;

import org.nmrfx.processor.optimization.DataUtil;
import org.nmrfx.processor.optimization.Equation;
import org.nmrfx.processor.optimization.EstParam;
import org.nmrfx.processor.optimization.VecID;

/**
 * @author graham stewart
 */
public class Hyperbolic extends OptFunction {

    /* Requirements
     * User Defined:____________________________________________________________________
     * > Define function variables
     * > Define function parameters that will be estimated.
     */
    public Hyperbolic() {
        setVars(VecID.Y, VecID.X);
        setParams(VecID.A, VecID.B);

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
                        double bParam = getParamVal(VecID.B, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        return x / (bParam + x);
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
                        double sum = x + bParam;
                        return -1.0 * aParam * x / (sum * sum);
                    }
                }
        });

        // y = a * x / (b + x)
        setFunction(new Equation() {
            public VecID name() {
                return VecID.Y;
            }

            public int getID() {
                return 0;
            }

            public double value(double[] pts, double[] ival) {
                double aParam = getParamVal(VecID.A, pts);
                double bParam = getParamVal(VecID.B, pts);
                double x = ival[getVarIndex(VecID.X) - 1];
                return aParam * x / (bParam + x);
            }
        });
    }

    public String getFunctionName() {
        return "y = a*x/(b+x)";
    }

    public void calcGuessParams() {
        EstParam[] eps = getEstParams();

        for (int i = 0; i < eps.length; i++) {
            if (eps[i].isPending()) {
                switch (eps[i].getVecID()) {
                    case A:
                        double yMax = DataUtil.getMaxValue(VecID.Y, getDataSetPtr());
                        loadParamGuess(VecID.A, yMax);
                        break;
                    case B:
                        double xMid = DataUtil.getMidValue(VecID.Y, VecID.X, getDataSetPtr());
                        loadParamGuess(VecID.B, xMid);
                        break;
                }
            }
        }

    }
}
