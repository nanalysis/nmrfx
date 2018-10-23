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
package org.nmrfx.processor.datasets;

class SimDataGenerator extends DataGenerator {

    public boolean selected;
    public boolean intSelected;

    public float[][] Matrix(int iChunk, int[] offset) {
        float[][] matrix;

        if (iChunk < 50) {
            matrix = new float[32][32];
            matrix[16][16] = (float) 1.0;
            matrix[20][16] = (float) 1.0;
            matrix[16][20] = (float) 1.0;

            return (matrix);
        } else {
            return (null);
        }
    }

    public float[][] Matrix2(int iChunk, String chunkLabelStr, int[][] apt) {
        return (null);
    }

    public int getMatrixRegion(int iChunk, int maxChunkSize, int mode, int[][] apt,
            double[] offset, StringBuffer chunkLabel) {
        return 0;
    }

    public int[][] bounds(int iChunk) {
        return (pt);
    }

    public DataCoordTransformer setBounds(double[][] limits) {
        return null;
    }

    public int nRows(int iChunk) {
        return (32);
    }

    public int nCols(int iChunk) {
        return (32);
    }

    public void setSelected(boolean state) {
        selected = state;
    }

    public void setSelectedElem(int iElem) {
    }

    public boolean getSelected() {
        return selected;
    }

    public void setIntegralSelected(boolean state) {
        intSelected = state;
    }

    public boolean getIntegralSelected() {
        return intSelected;
    }
}
