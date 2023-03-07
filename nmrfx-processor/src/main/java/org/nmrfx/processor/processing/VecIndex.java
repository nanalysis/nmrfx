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
package org.nmrfx.processor.processing;

import java.util.Arrays;

public class VecIndex {

    int[] inVecs;       // input Vec indices
    int[][][] outVecs;  // output Vec pt[][] indices

    public VecIndex(int[] iVecs, int[][][] oVecs) {
        this.inVecs = iVecs;
        this.outVecs = oVecs;
    }

    public int getInVec(int i) {
        return inVecs[i];
    }

    public int[][] getOutVec(int i) {
        return outVecs[i];
    }


    public String toString(int vecGroup, int nSteps) {  // for debugging
        StringBuilder sBuilder = new StringBuilder();
        if ((vecGroup + 1) % nSteps == 1 || (vecGroup + 1) % nSteps == 0) {
            sBuilder.append(String.format("group %6d in ", vecGroup));
            for (int k : inVecs) {
                sBuilder.append(String.format(" %4d", k));
            }
            sBuilder.append(" out ");
            for (int ix = 0; ix < outVecs.length; ix++) {
                for (int jx = 1; jx < outVecs[0].length; jx++) {
                    sBuilder.append(String.format(" %4d", outVecs[ix][jx][0]));
                }
                sBuilder.append("      ");
            }
            sBuilder.append("");
        }
        return sBuilder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VecIndex vecIndex = (VecIndex) o;
        return Arrays.equals(inVecs, vecIndex.inVecs) && Arrays.equals(outVecs, vecIndex.outVecs);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(inVecs);
        result = 31 * result + Arrays.hashCode(outVecs);
        return result;
    }
}
