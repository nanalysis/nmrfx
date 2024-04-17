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

/*
 * Datum.java
 *
 * Created on December 15, 1999, 8:44 PM
 */
package org.nmrfx.structure.cluster;

/**
 * @author JOHNBRUC
 */
public class Datum extends Object {

    int nDim;
    public double[] v;
    double w;
    public int next;
    public int last;
    public boolean act;
    public int n;
    public int[] proto;
    public int idNum;
    public int group = -1;

    /**
     * Creates new Datum
     */
    public Datum(int nDim) {
        this.nDim = nDim;
        v = new double[nDim];
        w = 1.0;
        proto = new int[1];
        proto[0] = -1;
        act = true;
        n = 1;
        idNum = -1;
    }

    public int setVector(double[] values) {
        if (v.length != values.length) {
            return (1);
        }

        for (int i = 0; i < nDim; i++) {
            v[i] = values[i];
        }

        return (0);
    }
}
