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

import java.io.IOException;

public abstract class DataGenerator {

    public int[][] pt;
    public double[][] ptd;
    public int xmin;
    public int ymin;
    public int nDim = 1;
    public int disDim = 0;

    public abstract float[][] Matrix(int iChunk, int[] offset) throws IOException;

    public abstract float[][] readMatrix(int iChunk, String chunkLabelStr,
                                         int[][] apt, float[][] matrix) throws IOException;

    public abstract int getMatrixRegion(int iChunk, int maxChunkSize, int mode, int[][] apt,
                                        double[] offset, StringBuffer chunkLabel);

    public abstract int[][] bounds(int iChunk);

    public abstract DataCoordTransformer setBounds(double[][] limits);

    public abstract int nRows(int iChunk);

    public abstract int nCols(int iChunk);
}
