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

    public double dismin = 0.0;
    public double dismax = 0.0;
    public double dis = 0.0;
    double[] dp;
    SpatialSet[] spatialSets;
    double inRange = 0;
    boolean hasDistances = false;

    IdResult(int nDim) {
        super();
        dp = new double[nDim];
        spatialSets = new SpatialSet[nDim];
    }

    void setSpatialSet(int i, SpatialSet value) {
        spatialSets[i] = value;
    }

    public SpatialSet getSpatialSet(final int i) {
        return spatialSets[i];
    }

    public double getPPMError(double weight) {
        double sum = 0.0;
        weight = weight * weight;
        for (int j = 0; j < dp.length; j++) {
            sum += ((dp[j] / 100.0) * (dp[j] / 100.0)) / weight;
        }
        double result = Math.exp(-1.0 * sum / 2.0);
        return result;
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
            if ((spatialSets[j].atom.entity instanceof Residue) && ((Residue) spatialSets[j].atom.entity).isStandard()) {
                oneLetter = ((Residue) spatialSets[j].atom.entity).getOneLetter();
                cName = ((Residue) spatialSets[j].atom.entity).polymer.getName();
            }
            strResult.append(String.format("%s", cName)).append(" ");
            strResult.append(String.format("%s", oneLetter) + " ");
            strResult.append(String.format("%3s", rNum) + ".");
            strResult.append(String.format("%-4s", aName) + " ");
        }

        for (int j = 0; j < nDim; j++) {
            long longVal = (long) (dp[j] + 0.5);
            strResult.append(Format.format30(longVal) + " ");
        }
        if (hasDistances) {
            long longVal = (long) (inRange + 0.5);
            strResult.append(Format.format30(longVal) + " ");
            strResult.append(Format.format2(dis) + " ");
            strResult.append(Format.format2(dismin) + " ");
            strResult.append(Format.format2(dismax) + " ");
        } else {
            strResult.append("{} {} {} {} ");
        }
        double delta = 0.0;
        for (int i = 0; i < dp.length; i++) {
            delta += dp[i] * dp[i];
        }
        delta = Math.sqrt(delta);

        long longVal = (long) (delta + 0.5);
        strResult.append(Format.format30(longVal));

        return strResult.toString();
    }
}
