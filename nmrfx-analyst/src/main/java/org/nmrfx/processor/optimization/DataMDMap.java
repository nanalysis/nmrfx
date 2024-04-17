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

/*Updates
 * TODO
 * 03/03/09
 * -Try to implement enumMap instead of fixed array for alpha lookup
 * -Move DataMap,DataSet,and DataPoint to a more general position
 *  in the onemoonsci package.
 */

/* DataMap
 *  Provides elementary hash map via VecID to allow arbitrary dimensioning
 * of data.  Currently implemented by DataSet.
 *  Basically a very light weight replacement of VectMat(VectMat.java).s
 */
package org.nmrfx.processor.optimization;

public class DataMDMap<T> {

    private final VectorDim<T>[] vect;
    private final VecID[] paramList;
    private int vectorSize;

    public DataMDMap(VecID[] paramList) {
        this.paramList = paramList;

        for (int i = 0; i < paramList.length; i++) {
            paramList[i].setVarIndex(i);
        }

        vect = new VectorDim[paramList.length];
    }

    public int nDim() {
        return paramList.length;
    }

    public T getValue(VecID varName, int index) {
        return vect[varName.getVarIndex()].val[index];
    }

    //TODO (3/11) implement t/f based on index #
    public void setValue(VecID varName, int index, T newVal) {
        vect[varName.getVarIndex()].val[index] = newVal;

    }

    public boolean load(VecID varName, T[] data) {
        if (vectorSize == 0 || data.length == vectorSize) {
            if (vectorSize == 0) {
                vectorSize = data.length;
            }

            vect[varName.getVarIndex()] = new VectorDim(data);
            vectorSize = data.length;

            return true;
        } else {
            return false;
        }
    }

    private class VectorDim<T> {

        public T[] val;

        VectorDim(T[] newVector) {
            val = newVector;
        }
    }
}
