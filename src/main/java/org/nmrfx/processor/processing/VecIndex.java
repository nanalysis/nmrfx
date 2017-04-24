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
    void printMe(int vecGroup, int nSteps) {  // for debugging
        if ((vecGroup + 1) % nSteps == 1 || (vecGroup +1) % nSteps == 0) {
            System.out.printf("group %6d in ", vecGroup);
            for (int k : inVecs) {
                System.out.printf(" %4d", k);
            }
            System.out.print(" out ");
            for (int ix = 0; ix < outVecs.length; ix++) {
                for (int jx = 0; jx < outVecs[0].length; jx++) {
                    System.out.printf(" %4d", outVecs[ix][jx][0]);
                }
                System.out.print("      ");
            }
            System.out.println("");
        }
    }
}
