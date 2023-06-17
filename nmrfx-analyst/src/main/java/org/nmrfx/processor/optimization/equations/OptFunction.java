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
 * Generic template for estimation problem functions.
 */
package org.nmrfx.processor.optimization.equations;

import org.apache.commons.math3.analysis.DifferentiableMultivariateVectorFunction;
import org.apache.commons.math3.analysis.MultivariateMatrixFunction;
import org.apache.commons.math3.analysis.MultivariateVectorFunction;
import org.nmrfx.processor.optimization.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author graham
 */
public abstract class OptFunction implements
        DifferentiableMultivariateVectorFunction, MultivariateVectorFunction, Function {

    private static final Map<String, Class> equationMap = new HashMap<>();

    //Data container for value list ie. xlist/xvec, etc.
    private DataRMap dsp;
    //Data container for parameter values
    private EstParamSet psp;
    private VecID[] varList;
    private VecID[] paramList;
    private VecID dVar;
    private int nVar;
    private int nPar;
    private HashMap<VecID, Equation> partialMap;
    private Equation yfuncx;

    public OptFunction() {
        registerEquation(getClass(), getFunctionName());
    }

    /*____________________________________________________________________________________
     * Register variables
     * > Dependent Variable followed by Independent Variables
     */
    public void setVars(VecID dVar, VecID... iVars) {
        this.dVar = dVar;

        nVar = iVars.length + 1;
        varList = new VecID[nVar];
        varList[0] = dVar;

        for (int i = 1; i < nVar; i++) {
            varList[i] = iVars[i - 1];
        }

    }

    public String getFunctionName() {
        return "";
    }

    public void setParams(VecID... params) {
        nPar = params.length;

        //Build Parameter List
        paramList = new VecID[nPar];

        for (int i = 0; i < nPar; i++) {
            paramList[i] = params[i];
        }

    }

    /*____________________________________________________________________________________
     * Register partial differential equations
     * > ex. dF/dA, dF/dB, etc.
     */
    public void setPartialDerivatives(Equation[] partials) {
        partialMap = new HashMap<VecID, Equation>();

        for (Equation p : partials) {
            partialMap.put(p.name(), p);
        }
    }

    /*____________________________________________________________________________________
     * Register actual equation
     * > ex. y = A*e^(-bx)
     */
    public void setFunction(Equation yfuncx) {
        this.yfuncx = yfuncx;
    }

    /*____________________________________________________________________________________
     * For loading individual parameter guesses
     */
    public void loadParamGuess(VecID param, double guess) {
        if (psp == null) {
            psp = new EstParamSet(paramList);
        }

        psp.setValue(param, guess);
    }

    public void updateParam(VecID param, double guess) {
        if (psp == null) {
            psp = new EstParamSet(paramList);
        }

        psp.setValue(param, guess);
    }

    public void updateParam(VecID param, boolean bound) {
        if (psp == null) {
            psp = new EstParamSet(paramList);
        }

        psp.setValue(param, bound);
    }

    public void updateParam(VecID param, boolean bound, boolean pending) {
        if (psp == null) {
            psp = new EstParamSet(paramList);
        }

        psp.setValue(param, bound);
        psp.setPending(param, pending);
    }

    public void updateParam(VecID param, double guess, boolean bound, boolean pending) {
        if (psp == null) {
            psp = new EstParamSet(paramList);
        }

        psp.setValue(param, guess, bound, pending);
    }

    public void updateParamPendingStatus(VecID param, boolean pending) {
        if (psp == null) {
            psp = new EstParamSet(paramList);
        }

        psp.setPending(param, pending);
    }

    public boolean getParamBoundStatus(VecID param) {
        return psp.isBound(param);
    }

    public boolean getParamPendingStatus(VecID param) {
        return psp.isPending(param);
    }

    public EstParam getEstParam(VecID param) {
        return psp.getEstParam(param);
    }

    public double getParamVal(VecID param) {
        return psp.getValue(param);
    }

    public double getParamVal(VecID param, double[] pts) {
        if (getParamBoundStatus(param)) {
            return getParamVal(param);
        } else {
            return pts[getUnboundParamIndex(param)];
        }
    }

    public double[] getAllParamVals() {
        return psp.getParamVals();
    }

    public double[] getAllUnboundParamVals() {
        return psp.getUnboundParamVals();
    }

    public EstParam[] getEstParams() {
        return psp.getEstParams();
    }

    public EstParam[] getUnboundEstParams() {
        return psp.getUnboundEstParams();
    }

    public int getUnboundParamIndex(VecID param) {
        return psp.getUnboundParamIndex(param);
    }

    public EstParamSet getEstParamSetPtr() {
        return psp;
    }

    /*____________________________________________________________________________________
     * Methods applying to experimental data
     */
    public boolean loadData(VecID var, double[] data) {
        if (dsp == null) {
            //Init data mat since we now have both dimensions available
            dsp = new DataRMap(varList, data.length);
        }

        return dsp.load(var, data);
    }

    //Returns specified vector only
    public double[] getData(VecID var) {
        return dsp.getCol(var);
    }

    public double[][] getData() {
        return dsp.getMatrix();
    }

    public int getVarIndex(VecID var) {
        return dsp.getVarIndex(var);
    }

    //TODO - 062409 - ID#0013
    //   > See other ID#0013
    public int getDataLen() {
        return dsp.size();
    }

    public double getPoint(VecID var, int row) {
        return dsp.getValue(var, row);
    }

    public DataRMap getDataSetPtr() {
        return dsp;
    }

    /*____________________________________________________________________________________
     * Object function characteristics
     *
     */
    public VecID getDependentVarName() {
        return dVar;
    }

    //TODO - 062409 ID#0012
    //> Ditch the copy and just return a ref
    public VecID[] getIndependentVarNames() {
        VecID[] copy = new VecID[varList.length - 1];

        for (int n = 1; n < varList.length; n++) {
            copy[n - 1] = varList[n];
        }

        return copy;
    }

    public VecID[] getAllVarNames() {
        return varList;
    }

    public VecID[] getAllParamNames() {
        return paramList;
    }

    public VecID[] getAllUnboundParamNames() {
        return psp.getUnboundVecID();
    }

    /*____________________________________________________________________________________
     * Abstract meths
     */
    public abstract void calcGuessParams();

    /*____________________________________________________________________________________
     * DifferentiableMultivariateVectorFunction Interface meths
     */
    public MultivariateVectorFunction gradient(final int index) {
        return new MultivariateVectorFunction() {
            public double[] value(double[] points) {
                double[][] jcb = jacobian(points);

                return jcb[index];
            }
        };
    }

    public MultivariateVectorFunction partialDerivative(final VecID param) {
        return new MultivariateVectorFunction() {
            public double[] value(double[] pts) {
                double[][] jcb = jacobian(pts);
                double[] jcbCol = new double[getDataLen()];
                int index = param.getVarIndex();

                for (int i = 0; i < jcbCol.length; i++) {
                    jcbCol[i] = jcb[i][index];
                }

                return jcbCol;
            }
        };

    }

    public MultivariateVectorFunction partialDerivative(final int i) {
        return new MultivariateVectorFunction() {
            public double[] value(double[] pts) {
                double[][] j = jacobian(pts);
                return j[i];
            }
        };

    }

    public double[][] jacobian(double[] pts) {
        int ilen = getDataLen();
        VecID[] params = getAllUnboundParamNames();
        double[][] idata = dsp.getSubMatrix(1, 0, dsp.getColCnt() - 1, ilen - 1);   //get submatrix of independent vars only
        double[][] jacob = new double[idata.length][params.length];
        double[] pvals = new double[params.length];

        for (int i = 0; i < params.length; i++) {
            pvals[i] = getParamVal(params[i], pts);
        }

        //For each unbound parameter...
        //1) Obtain the corresponding partial derivative of the function
        //   based on unbound parameter name
        //2) Get the index of the parameter for usage in the jacobian matrix
        //3) Cycle through independent variable data values, passing each to the
        //   partial derivative equation and storing the result in the jacobian matrix
        for (int i = 0; i < params.length; i++) {
            Equation cpartial = partialMap.get(params[i]);
            int pindex = getUnboundParamIndex(params[i]);

            for (int j = 0; j < ilen; j++) {
                jacob[j][pindex] = cpartial.value(pts, idata[j]);
            }
        }

        return jacob;
    }

    public MultivariateMatrixFunction jacobian() {
        return new MultivariateMatrixFunction() {
            public double[][] value(double[] pts) {
                return jacobian(pts);
            }
        };
    }

    public double[] value(double[] pts) throws IllegalArgumentException {
        int len = getDataLen();
        double[] dvals = new double[len];
        double[][] idata = dsp.getSubMatrix(1, 0, dsp.getColCnt() - 1, len - 1);

        for (int i = 0; i < len; i++) {
            dvals[i] = yfuncx.value(pts, idata[i]);
        }

        return dvals;
    }

    public double value(double[] pars, double[] iValues) throws IllegalArgumentException {
        double dval = yfuncx.value(pars, iValues);
        return dval;
    }

    /*____________________________________________________________________________________
     * Methods to facilitate LM optimizer constructor params
     */
    public double[] target() {
        return getData(dVar);
    }

    public double[] startpoint() {
        return psp.getUnboundParamVals();
    }

    static void registerEquation(Class equationClass, String name) {
        equationMap.put(name, equationClass);
    }

    public static Set<Map.Entry<String, Class>> getEquations() {
        if (equationMap.size() < 12) {
            new ExpAB();
            new ExpABC();
            new Gaussian();
            new JMod();
            new LorentzLS();
            new ModHH();
            new Quadratic();
            new Quadratic10();
            new Quadratic10F();
            new RDispESin();
            new RDispSin();
            new Unfolding();
            new Hyperbolic();
            new InvHyperbolic();
        }
        return equationMap.entrySet();
    }
}
