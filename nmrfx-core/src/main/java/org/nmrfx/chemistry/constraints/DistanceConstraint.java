/*
 * NMRFx Structure : A Program for Calculating Structures
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
package org.nmrfx.chemistry.constraints;

public class DistanceConstraint implements Constraint {
    private static final DistanceStat DEFAULT_STAT = new DistanceStat();
    private boolean isBond;
    protected double lower;
    protected double upper;
    protected double weight;
    protected double target;
    protected double targetErr;
    public DistanceStat disStat = DEFAULT_STAT;
    DistanceStat disStatAvg = DEFAULT_STAT;

    public DistanceConstraint() {
        this.lower = 1.8;
        this.upper = 5.0;
        this.isBond = false;
        this.weight = 1.0;
        this.target = (lower + upper) / 2.0;
        this.targetErr = (upper - lower) / 2.0;

    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(lower);
        sBuilder.append(" ");
        sBuilder.append(upper);
        sBuilder.append(" ");
        sBuilder.append(weight);
        sBuilder.append(" ");
        sBuilder.append(target);
        sBuilder.append(" ");
        sBuilder.append(targetErr);
        return sBuilder.toString();
    }

    public double getLower() {
        return lower;
    }

    public double getUpper() {
        return upper;
    }

    public double getWeight() {
        return weight;
    }

    public double getTarget() {
        return target;
    }

    public double getTargetError() {
        return targetErr;
    }

    public boolean isBond() {
        return isBond;
    }
    public void isBond(boolean value) {
        isBond = value;
    }

    public double getTargetErr() {
        return targetErr;
    }

    @Override
    public int getID() {
        return 0;
    }

    @Override
    public boolean isUserActive() {
        return false;
    }

    @Override
    public DistanceStat getStat() {
        return disStat;
    }

    @Override
    public double getValue() {
        return 0;
    }

    @Override
    public String toSTARString(int id, int memberId) {
        return null;
    }
}
