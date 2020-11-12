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

import org.nmrfx.processor.math.Vec;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

public class BucketedMatrix {

    final Array2DRowRealMatrix matrix;
    final int[] rowIndices;
    final int[] colCenters;
    final int[] colIndices;
    final double[] ppms;
    final String[][] dataTbl;
    final int minCol;
    final int maxCol;

    public BucketedMatrix(Array2DRowRealMatrix matrix, int[] rowIndices, int[] colIndices, int[] colCenters, double[] ppms, String[][] dataTbl) {
        this.matrix = matrix;
        this.rowIndices = rowIndices;
        this.colCenters = colCenters;
        this.colIndices = colIndices;
        this.ppms = ppms;
        this.dataTbl = dataTbl;
        int min = colCenters[0];
        int max = colCenters[colCenters.length - 1];
        for (int colIndex : colCenters) {
            if (colIndex < min) {
                min = colIndex;
            }
            if (colIndex > max) {
                max = colIndex;
            }
        }
        minCol = min;
        maxCol = max;
    }

    public Array2DRowRealMatrix getMatrix() {
        return matrix;
    }

    public int[] getRows() {
        return rowIndices;
    }

    public int[] getColumns() {
        return colCenters;
    }

    public double[] getPPMs() {
        return ppms;
    }

    public void makeVectorFromColumn(RealMatrix realMatrix, double[] scales, int column, String vecName, double sf) {
        int vecSize = maxCol - minCol + 1;
        // add some extra so peaks are not right at edge
        int extra = vecSize / 20;
        double extraFrac = 1.0 * extra / vecSize;
        vecSize += 2 * extra;
        Vec vecMat = Vec.createNamedVector(vecSize, vecName, false);
        double ppm0 = ppms[0];
        double ppm1 = ppms[ppms.length - 1];
        double width = ppm0 - ppm1;
        double extraPPM = width * extraFrac;
        ppm0 += extraPPM;
        ppm1 -= extraPPM;
        vecMat.refValue = ppm0;
        vecMat.centerFreq = sf;
        double sw = ((ppm0 - ppm1) * sf);
        vecMat.dwellTime = 1.0 / sw;
        vecMat.setFreqDomain(true);
        int j = 0;
        for (int i : colCenters) {
            double value = realMatrix.getEntry(j, column);
            vecMat.setReal(i - minCol + extra, value * scales[j]);
            j++;
        }
    }

    public void writeMatrix(String fileName) throws IOException {
        int nTableCols = dataTbl[0].length;
        StringBuilder sBuilder = new StringBuilder();
        try (BufferedWriter writer = Files.newBufferedWriter(
                FileSystems.getDefault().getPath(fileName),
                Charset.forName("US-ASCII"),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);) {

            int nRows = matrix.getRowDimension();
            int nCols = matrix.getColumnDimension();
            sBuilder.setLength(0);
            for (int iCol = 0; iCol < nTableCols; iCol++) {
                sBuilder.append(dataTbl[0][iCol]);
                sBuilder.append('\t');
            }
            sBuilder.append("row");
            for (int iCol = 0; iCol < nCols; iCol++) {
                sBuilder.append('\t');
                sBuilder.append(ppms[iCol]);
            }
            sBuilder.append(System.lineSeparator());
            String content = sBuilder.toString();
            writer.write(content, 0, content.length());

            for (int iRow = 0; iRow < nRows; iRow++) {
                sBuilder.setLength(0);
                for (int iCol = 0; iCol < nTableCols; iCol++) {
                    sBuilder.append(dataTbl[iRow + 1][iCol]);
                    sBuilder.append('\t');
                }
                sBuilder.append(rowIndices[iRow] + 1);
                for (int iCol = 0; iCol < nCols; iCol++) {
                    sBuilder.append('\t');
                    sBuilder.append(matrix.getEntry(iRow, iCol));
                }
                sBuilder.append(System.lineSeparator());
                content = sBuilder.toString();
                writer.write(content, 0, content.length());
            }
        }
    }
}
