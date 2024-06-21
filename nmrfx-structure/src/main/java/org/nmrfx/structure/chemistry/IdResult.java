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

package org.nmrfx.structure.chemistry;

import org.nmrfx.chemistry.Compound;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.SpatialSet;
import org.nmrfx.utilities.Format;

public class IdResult {

    /**
     * @return the dismin
     */
    public double getDisMin() {
        return dismin;
    }

    /**
     * @return the dismax
     */
    public double getDisMax() {
        return dismax;
    }

    /**
     * @return the dis
     */
    public double getDis() {
        return dis;
    }

    private double dismin = 0.0;
    private double dismax = 0.0;
    private double dis = 0.0;
    double[] dp;
    SpatialSet[] spatialSets;
    double inRange = 0;
    boolean hasDistances = false;

    IdResult(int nDim) {
        super();
        dp = new double[nDim];
        spatialSets = new SpatialSet[nDim];
    }

    public void setDistances(double dis, double disMin, double disMax) {
        this.dis = dis;
        this.dismin = disMin;
        this.dismax = disMax;
    }
    void setSpatialSet(int i, SpatialSet value) {
        spatialSets[i] = value;
    }

    public SpatialSet getSpatialSet(final int i) {
        return spatialSets[i];
    }

    public double getDP(int i) {
        return dp[i];
    }

    public double getDPAvg() {
        double sum = 0.0;
        for (double v:dp) {
            sum += v*v;
        }
        return Math.sqrt(sum);
    }

    public double getPPMError(double weight) {
        double sum = 0.0;
        weight = weight * weight;
        for (double v : dp) {
            sum += ((v / 100.0) * (v / 100.0)) / weight;
        }
        return Math.exp(-1.0 * sum / 2.0);
    }

    @Override
    public String toString() {
        StringBuilder strResult = new StringBuilder();
        strResult.setLength(0);
        int nDim = dp.length;
        for (int j = 0; j < nDim; j++) {
            String rNum = ((Compound) spatialSets[j].atom.entity).number;
            String aName = spatialSets[j].atom.name;
            String cName = "X";
            char oneLetter = 'X';
            if ((spatialSets[j].atom.entity instanceof Residue residue) && residue.isStandard()) {
                oneLetter = residue.getOneLetter();
                cName = residue.polymer.getName();
            }
            strResult.append(String.format("%s", cName)).append(" ");
            strResult.append(String.format("%s", oneLetter)).append(" ");
            strResult.append(String.format("%3s", rNum)).append(".");
            strResult.append(String.format("%-4s", aName)).append(" ");
        }

        for (int j = 0; j < nDim; j++) {
            long longVal = (long) (dp[j] + 0.5);
            strResult.append(Format.format30(longVal)).append(" ");
        }
        if (hasDistances) {
            long longVal = (long) (inRange + 0.5);
            strResult.append(Format.format30(longVal)).append(" ");
            strResult.append(Format.format2(dis)).append(" ");
            strResult.append(Format.format2(dismin)).append(" ");
            strResult.append(Format.format2(dismax)).append(" ");
        } else {
            strResult.append("{} {} {} {} ");
        }
        double delta = 0.0;
        for (double v : dp) {
            delta += v * v;
        }
        delta = Math.sqrt(delta);

        long longVal = (long) (delta + 0.5);
        strResult.append(Format.format30(longVal));

        return strResult.toString();
    }
}
