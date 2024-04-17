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
package org.nmrfx.processor.optimization;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

/* DataRMap
 *  Provides elementary hash map via VecID to allow arbitrary dimensioning
 *  of data.
 *  Differs from previous maps in that it wraps the VecID functionality around
 *  RealMatrix found in apache commons math linear.  Thus it is not generic like
 *  the previous, bound to double now.
 */
public class DataRMap {

    private int vectorSize;
    private RealMatrix map;
    private VecID[] pList;

    public DataRMap(VecID[] pList, int length) {
        this.pList = pList;
        vectorSize = length;

        for (int i = 0; i < pList.length; i++) {
            this.pList[i].setVarIndex(i);
        }

        map = new Array2DRowRealMatrix(length, pList.length);
    }

    public VecID[] getVecID() {
        return pList;
    }

    public double getValue(VecID v, int rowNo) {
        return map.getEntry(rowNo, v.getVarIndex());
    }

    public double getValue(VecID v) {
        return map.getEntry(0, v.getVarIndex());
    }

    public double[] getCol(VecID v) {
        int vIndex = v.getVarIndex();

        //if(pCache[vIndex] == false){
        //    pCache[vIndex] = (++pCacheHits[vIndex] == cacheThreshold) ? true : false;
        //    if(pCache[vIndex]){
        //        vCache[vIndex] = map.getColumn(vIndex);
        //        return vCache[vIndex];
        //    }else{
        return map.getColumn(vIndex);
        //    }
        //}else{
        //     return vCache[vIndex];
        // }

    }

    public double[] getRow(int rowNo) {
        return map.getRow(rowNo);
    }

    public double[][] getSubMatrix(int colA, int rowA, int colB, int rowB) {
        //start row, end row, start col, end col
        return map.getSubMatrix(rowA, rowB, colA, colB).getData();

    }

    public double[][] getMatrix() {
        return map.getData();
    }

    //>ID#0006 - I naturally reverse function input of rows and columns
    //           RealMatrix takes rows then cols, all my functions take cols, then rows
    //           Might want to change this for clarity reasons later.
    public boolean setValue(VecID v, int rowNo, double newVal) {
        try {
            map.setEntry(rowNo, v.getVarIndex(), newVal);
            return true;
        } catch (Exception ex) {
            return false;

        }
    }

    public boolean load(VecID v, double[] data) {
        try {
            map.setColumn(v.getVarIndex(), data);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean setCol(VecID v, double[] data) {
        try {
            int vIndex = v.getVarIndex();
            map.setColumn(vIndex, data);

            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean setRow(int rowNo, double[] data) {
        try {
            map.setRow(rowNo, data);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    //TODO - 062409 - ID#0013
    //     > Both this and 'size()' return the same value.
    //       Let's get rid of 'size()' later.
    public int getRowCnt() {
        return map.getRowDimension();
    }

    public int getColCnt() {
        return map.getColumnDimension();
    }
    //TODO - 062409
    //>      ID#0011 - make this routine and the one in EstParamSet more efficient

    public int getVarIndex(VecID var) {
        int index = -1;
        VecID[] vars = getVecID();

        for (int i = 0; i < vars.length; i++) {
            if (vars[i] == var) {
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
