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
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.optimization;

import java.util.ArrayList;

/**
 * @author graham
 */
public class EstParamSet {

    private String name;
    private double val;
    private EstParam[] psp;
    private int vectorSize;
    private VecID[] params;

    public EstParamSet(VecID[] params) {
        this.params = params;
        psp = new EstParam[params.length];

        for (int i = 0; i < params.length; i++) {
            params[i].setVarIndex(i);
            psp[params[i].getVarIndex()] = new EstParam(params[i]);
        }

        vectorSize = params.length;
    }

    public EstParam getEstParam(VecID varName) {
        return psp[varName.getVarIndex()];
    }

    public double getValue(VecID varName) {
        return psp[varName.getVarIndex()].getEstimate();
    }

    public void setValue(VecID varName, EstParam newVal) {
        psp[varName.getVarIndex()] = newVal;
    }

    public void setValue(VecID varName, double val) {
        psp[varName.getVarIndex()].setEstimate(val);
    }

    public void setValue(VecID varName, boolean bound) {
        psp[varName.getVarIndex()].setBound(bound);
    }

    public void setValue(VecID varName, double val, boolean bound) {
        EstParam ep = psp[varName.getVarIndex()];
        ep.setEstimate(val);
        ep.setBound(bound);
    }

    public void setValue(VecID varName, double val, boolean bound, boolean pending) {
        EstParam ep = psp[varName.getVarIndex()];

        ep.setEstimate(val);
        ep.setBound(bound);
        ep.setPendingStatus(pending);
    }

    public void setPending(VecID varName, boolean pending) {
        psp[varName.getVarIndex()].setPendingStatus(pending);
    }

    public void setBound(VecID varName, boolean bound) {
        psp[varName.getVarIndex()].setBound(bound);
    }

    public boolean isBound(VecID varName) {
        return psp[varName.getVarIndex()].isBound();
    }

    public boolean isPending(VecID varName) {
        return psp[varName.getVarIndex()].isPending();
    }

    public void load(VecID varName, EstParam param) {
        psp[varName.getVarIndex()] = param;
    }

    public VecID[] getVecID() {
        return params;
    }

    public VecID[] getUnboundVecID() {
        EstParam[] up = getUnboundEstParams();
        VecID[] uv = new VecID[up.length];

        for (int i = 0; i < up.length; i++) {
            uv[i] = up[i].getVecID();
        }

        return uv;
    }

    public double[] getParamVals() {
        double[] pv = new double[vectorSize];

        for (int i = 0; i < size(); i++) {
            pv[i] = psp[params[i].getVarIndex()].getEstimate();
        }

        return pv;
    }

    public double[] getUnboundParamVals() {
        EstParam[] up = getUnboundEstParams();
        double[] upv = new double[up.length];

        for (int i = 0; i < up.length; i++) {
            upv[i] = up[i].getEstimate();
        }

        return upv;
    }

    public EstParam[] getEstParams() {
        return psp;
    }

    //TODO - 062409 - ID#0014
    //     > Kinda clunky - optimize please
    public EstParam[] getUnboundEstParams() {
        ArrayList<EstParam> upa = new ArrayList<EstParam>();

        for (EstParam e : psp) {
            if (!e.isBound()) {
                upa.add(e);
            }
        }

        EstParam[] ups = new EstParam[upa.size()];
        for (int i = 0; i < upa.size(); i++) {
            ups[i] = upa.get(i);
        }

        return ups;
    }

    //TODO - 062409
    //>      ID#0011 - make this routine and the one in DataRMap more efficient
    public int getUnboundParamIndex(VecID param) {
        int index = -1;
        EstParam[] uest = getUnboundEstParams();

        for (int i = 0; i < uest.length; i++) {
            if (uest[i].getVecID() == param) {
                index = i;
                break;
            }
        }

        return index;
    }

    public int size() {
        return vectorSize;
    }
}
