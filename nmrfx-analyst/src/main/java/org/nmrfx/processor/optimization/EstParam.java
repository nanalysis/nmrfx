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

/**
 * @author graham
 */
public class EstParam {

    private double val;
    private String name;
    private VecID vecID;
    private boolean bound;
    private boolean pendingApproximation;

    public EstParam(EstParam estParam) {
        this.val = estParam.val;
        this.name = estParam.name;
        this.vecID = estParam.vecID;
        this.bound = estParam.bound;
        this.pendingApproximation = estParam.pendingApproximation;
    }

    public EstParam(VecID param) {
        this(param, 0.0, false, false);
    }

    public EstParam(VecID param, double val) {
        this(param, val, false, false);
    }

    public EstParam(VecID param, double val, boolean bound) {
        this(param, val, bound, false);
    }

    public EstParam(VecID param, double val, boolean bound, boolean pendingApproximation) {
        this.vecID = param;
        this.name = param.toString();
        this.val = val;
        this.bound = bound;
        this.pendingApproximation = pendingApproximation;

    }

    public void setPendingStatus(boolean status) {
        pendingApproximation = status;
    }

    public void setBound(boolean bound) {
        this.bound = bound;
    }

    public boolean isPending() {
        return pendingApproximation;
    }

    public boolean isBound() {
        return bound;
    }

    public double getEstimate() {
        return val;
    }

    public void setEstimate(double newVal) {
        val = newVal;
    }

    public String getName() {
        return name;
    }

    public VecID getVecID() {
        return vecID;
    }
}
