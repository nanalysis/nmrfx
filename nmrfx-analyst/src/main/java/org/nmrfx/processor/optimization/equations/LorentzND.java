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
 * Author: graham Class: LorentzND Desc: -
 */
public class LorentzND extends OptFunction {

    int nSig = 1;
    int nDim = 1;
    int[] nPars = {1};
    int[] jCal = {0};

    public LorentzND(int nSig, int nDim, int[] jCal) {
        this.nSig = nSig;
        this.nDim = nDim;
        this.jCal = jCal;
        for (int i = 0; i < nDim; i++) {
            if (jCal[i] == 0) {
                nPars[i] = 2;
            } else {
                nPars[i] = 3;
            }
        }
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
                        double f = getParamVal(VecID.F, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double foffset = x - f;

                        return ((b * b) / (b * b + 4.0 * foffset * foffset));
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
                        double f = getParamVal(VecID.F, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double foffset = x - f;
                        double bsq = b * b;
                        double denom = bsq + 4 * foffset * foffset;

                        return 2 * a * b * (1.0 / (denom) - bsq / (denom * denom));
                    }
                }, // dY/dF
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
                        double f = getParamVal(VecID.F, pts);
                        double x = ival[getVarIndex(VecID.X) - 1];
                        double foffset = x - f;
                        double denom = (b * b + 4 * foffset * foffset);

                        return -(a * b * b * (8 * f - 8 * x)) / (denom * denom);

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
                return calculate(pts, ival);
            }
        });
    }

    public double calculate(double[] a, double[] xs) {
        double y = 0;

        int nSig = 1;

        int kk = 0;
        nPars[0] = 2;
        double[] ys = new double[nDim];
        for (int k = 0; k < nSig; k++) {
            int start = kk;

            for (int iDim = 0; iDim < nDim; iDim++) {
                ys[iDim] = lShape(a, start, xs[iDim], jCal[iDim]);
            }

            double ypeak = a[start];

            for (int iDim = 0; iDim < nDim; iDim++) {
                ypeak = ypeak * ys[iDim];
            }

            y += ypeak;

            for (int iDim = 0; iDim < nDim; iDim++) {
                kk += nPars[iDim];
            }

            kk++;
        }

        return y;
    }

    public double lShape(double[] a, int start, double x, int jcal) {
        double y = 0.0;
        double freq = a[start + 1];
        double b = a[start + 2] * 0.5;

        if (jcal == 1) {
            double f1 = freq - (a[start + 3] / 2.0);
            double f2 = freq - (a[start + 3] / 2.0);
            double y1 = (b * b) / ((b * b) + ((x - f1) * (x - f1)));
            double y2 = (b * b) / ((b * b) + ((x - f2) * (x - f2)));
            y = y1 + y2;
        } else if (jcal == -1) {
            double f1 = freq - (a[start + 3] / 2.0);
            double f2 = freq - (a[start + 3] / 2.0);
            double y1 = (b * b) / ((b * b) + ((x - f1) * (x - f1)));
            double y2 = (b * b) / ((b * b) + ((x - f2) * (x - f2)));
            y = y1 - y2;
        } else {
            y = (b * b) / ((b * b) + ((x - freq) * (x - freq)));
        }

        return y;
    }

    public void calcGuessParams() {
        EstParam[] eps = getEstParams();
        double xMid = DataUtil.getMidValue1Side(VecID.Y, VecID.X, getDataSetPtr());
        double xAtMaxY = DataUtil.getYAtMaxX(VecID.X, VecID.Y, getDataSetPtr());
        double maxY = DataUtil.getMaxValue(VecID.Y, getDataSetPtr());
        for (int i = 0; i < eps.length; i++) {
            if (eps[i].isPending()) {
                switch (eps[i].getVecID()) {
                    case A:
                        loadParamGuess(VecID.A, maxY);
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

    public String getFunctionName() {
        return "y = a*b^2/(b^2 + 4*(x-f)^2)";
    }
}
