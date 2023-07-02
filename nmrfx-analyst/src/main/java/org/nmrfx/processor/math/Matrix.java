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
package org.nmrfx.processor.math;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.nmrfx.datasets.MatrixType;
import org.nmrfx.processor.processing.ProcessingException;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.channels.FileChannel;

/**
 * Math routines for Matrix data and FFT2D processing.
 *
 * @author bfetler
 */
public class Matrix implements MatrixType {

    /**
     * Output point to write matrix.
     */
    private int[][] pt = null;

    /**
     * Matrix data store.
     */
    private double[][] matrix;

    public Matrix(int nRows, int nColumns) {
        matrix = new double[nRows][nColumns];
    }

    public Matrix(int nRows, int nColumns, int[][] pt) {
        this.pt = pt;
        matrix = new double[nRows][nColumns];
    }

    @Override
    public String exportData(String rootName, String suffix) throws IOException {
        return exportData(rootName, suffix, false);
    }

    @Override
    public String exportData(String rootName, String suffix, boolean littleEndian) throws IOException {
        int index = getIndex();
        String outFileName = String.format("%s%04d.%s", rootName, index + 1, suffix);

        try (FileOutputStream oStream = new FileOutputStream(outFileName)) {
            int size = pt.length * pt[0].length;
            ByteBuffer byteBuffer = ByteBuffer.allocate(size * Double.SIZE / 8);
            if (littleEndian) {
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
            int k = 0;
            for (int i = 0; i < (matrix.length); i++) {
                for (int j = 0; j < (matrix[0].length); j++) {
                    doubleBuffer.put(k++, matrix[i][j]);
                }
            }
            FileChannel channel = oStream.getChannel();
            channel.write(byteBuffer);
        } catch (IOException ioE) {
            throw ioE;
        }
        return outFileName;
    }

    @Override
    public String importData(String rootName, String suffix) throws IOException {
        return importData(rootName, suffix, false);
    }

    @Override
    public String importData(String rootName, String suffix, boolean littleEndian) throws IOException {
        int index = getIndex();
        String inFileName = String.format("%s%04d.%s", rootName, index + 1, suffix);
        try (FileInputStream oStream = new FileInputStream(inFileName)) {
            int size = pt.length * pt[0].length;
            ByteBuffer byteBuffer = ByteBuffer.allocate(size * Double.SIZE / 8);
            if (littleEndian) {
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
            FileChannel channel = oStream.getChannel();
            channel.read(byteBuffer);
            int k = 0;
            for (int i = 0; i < (matrix.length); i++) {
                for (int j = 0; j < (matrix[0].length); j++) {
                    matrix[i][j] = doubleBuffer.get(k++);
                }
            }
        } catch (IOException ioE) {
            throw ioE;
        }
        return inFileName;

    }

    public Matrix zeroFill() {
        int msize1 = matrix.length;
        int msize2 = matrix[0].length;
        int msize12 = msize1 * 2;
        int msize22 = msize2 * 2;
        Matrix zfMatrix = new Matrix(msize12, msize22);
        for (int j = 0; j < msize1; j++) {
            System.arraycopy(matrix[j], 0, zfMatrix.matrix[j], 0, msize2);
        }
        return zfMatrix;
    }

    public int[] getDims() {
        int[] dd = new int[2];
        dd[0] = matrix.length;
        dd[1] = matrix[0].length;
        return dd;
    }

    public void setMatrix(double[][] matrix) {
        this.matrix = matrix;
    }

    public double[][] getMatrix() {
        return matrix;
    }

    public void setPt(int[][] pt) {
        this.pt = pt;
    }

    public int[][] getPt() {
        return pt;
    }

    @Override
    public int getIndex() {
        return pt[pt.length - 1][0];
    }

    public void copyMatrix(double[][] src) {
        if (src.length != matrix.length || src[0].length != matrix[0].length) {
            throw new ProcessingException("copyMatrix dimensions not equal");
        }
        for (int i = 0; i < (matrix.length); i++) {
            System.arraycopy(src[i], 0, matrix[i], 0, matrix[0].length);
        }
    }

    public void copyPartFrom(double[][] src) {
        int msize1 = matrix.length < src.length ? matrix.length : src.length;
        int msize2 = matrix[0].length < src[0].length ? matrix[0].length : src[0].length;
        for (int i = 0; i < (matrix.length); i++) {
            System.arraycopy(src[i], 0, matrix[i], 0, matrix[0].length);
        }
    }

    public void copyPartFrom(Matrix source) {
        copyPartFrom(source.getMatrix());
    }

    public void copyMatrix(Matrix source) {
        copyMatrix(source.getMatrix());
    }

    public void printMatrix() {
        for (int i = 0; i < 4; i++) {
            System.out.print("  MatrixPrint " + i + ": ");
            for (int j = 0; j < 4; j++) {
                System.out.print(" " + matrix[j][i]);
            }
            System.out.println(";");
        }
    }

    public void dump() throws IOException {
        dump(null);
    }

    @Override
    public void dump(String outName) throws IOException {
        FileWriter fileWriter = null;
        if (outName != null) {
            fileWriter = new FileWriter(outName);
        }
        try (FileWriter fw = fileWriter) {
            int k = 0;
            for (int i = 0; i < matrix.length; i++) {
                for (int j = 0; j < matrix[i].length; j++) {
                    String dump = String.format("%3d %3d %3d %.5f%n", i, j, k++, matrix[i][j]);
                    if (fw != null) {
                        fw.write(dump);
                    } else {
                        System.out.printf(dump);
                    }

                }
            }
        }
    }

    private void row2ri(int row, double[][] dataRI) {
        int mid = dataRI[0].length;
        for (int i = 0; i < mid; i++) {
            dataRI[0][i] = matrix[row][2 * i];
            dataRI[1][i] = matrix[row][2 * i + 1];
        }
    }

    private void row2r(int row, double[][] dataRI) {
        int size = dataRI[0].length;
        System.arraycopy(matrix[row], 0, dataRI[0], 0, size);
    }

    private void ri2row(int row, double[][] dataRI) {
        int size = matrix[row].length / 2;
        for (int i = 0; i < size; i++) {
            matrix[row][2 * i] = dataRI[0][i];
            matrix[row][2 * i + 1] = dataRI[1][i];
        }
    }

    private void row2riZf(int row, double[][] dataRI) {
        int size = matrix[row].length / 2;
        for (int i = 0; i < size; i++) {
            dataRI[0][i] = matrix[row][2 * i];
            dataRI[1][i] = matrix[row][2 * i + 1];
        }
        for (int i = size; i < dataRI[0].length; i++) {
            dataRI[0][i] = 0.0;
            dataRI[1][i] = 0.0;
        }
    }

    private void riReal2row(int row, double[][] dataRI) {
        int size = dataRI[0].length;
        System.arraycopy(dataRI[0], 0, matrix[row], 0, size);
    }

    private void column2ri(int col, double[][] dataRI) {
        int mid = dataRI[0].length;
        for (int i = 0; i < mid; i++) {
            dataRI[0][i] = matrix[2 * i][col];
            dataRI[1][i] = matrix[2 * i + 1][col];
        }
    }

    private void column2r(int col, double[][] dataRI) {
        int size = dataRI[0].length;
        for (int i = 0; i < size; i++) {
            dataRI[0][i] = matrix[i][col];
        }
    }

    private void ri2column(double[][] dataRI, int col) {
        int size = matrix.length / 2;
        for (int i = 0; i < size; i++) {
            matrix[2 * i][col] = dataRI[0][i];
            matrix[2 * i + 1][col] = dataRI[1][i];
        }
    }

    private void column2riZf(int col, double[][] dataRI) {
        int size = matrix.length / 2;
        for (int i = 0; i < size; i++) {
            dataRI[0][i] = matrix[2 * i][col];
            dataRI[1][i] = matrix[2 * i + 1][col];
        }
        for (int i = size; i < dataRI[0].length; i++) {
            dataRI[0][i] = 0.0;
            dataRI[1][i] = 0.0;
        }
    }

    private void riReal2column(int col, double[][] dataRI) {
        int size = dataRI[0].length;
        for (int i = 0; i < size; i++) {
            matrix[i][col] = dataRI[0][i];
        }
    }

    private void apache_fftd(double[][] dataRI) {
        FastFourierTransformer.transformInPlace(dataRI, DftNormalization.STANDARD, TransformType.FORWARD);
    }

    private void apache_iftd(double[][] dataRI) {
        FastFourierTransformer.transformInPlace(dataRI, DftNormalization.STANDARD, TransformType.INVERSE);
    }

    private void ftRow(int row, double[][] dataRI) {
        row2ri(row, dataRI);
        apache_fftd(dataRI);
        ri2row(row, dataRI);
    }

    private void iftRow(int row, double[][] dataRI) {
        row2ri(row, dataRI);
        apache_iftd(dataRI);
        ri2row(row, dataRI);
    }

    private void ftZFRow(int row, double[][] dataRI) {
        row2riZf(row, dataRI);
        apache_fftd(dataRI);
        riReal2row(row, dataRI);
    }

    private void hiftZFRow(int row, double[][] dataRI) {
        row2r(row, dataRI);
        VecUtil.hift(dataRI, dataRI[0].length, 0.5);
        ri2row(row, dataRI);
    }

    private void ftColumn(int col, double[][] dataRI) {
        column2ri(col, dataRI);
        apache_fftd(dataRI);
        ri2column(dataRI, col);
    }

    private void iftColumn(int col, double[][] dataRI) {
        column2ri(col, dataRI);
        apache_iftd(dataRI);
        ri2column(dataRI, col);
    }

    private void ftZFColumn(int col, double[][] dataRI) {
        column2riZf(col, dataRI);
        apache_fftd(dataRI);
        riReal2column(col, dataRI);
    }

    private void hiftZFColumn(int col, double[][] dataRI) {
        column2r(col, dataRI);
        VecUtil.hift(dataRI, dataRI[0].length, 0.5);
        ri2column(dataRI, col);
    }

    private void swapHalfRows() {
        int mid = matrix[0].length / 2;
        int msize1 = matrix.length;
        double tmp;
        for (int i = 0; i < msize1; i++) {
            for (int j = 0; j < mid; j++) {
                tmp = matrix[i][j];
                matrix[i][j] = matrix[i][j + mid];
                matrix[i][j + mid] = tmp;
            }
        }
    }

    private void swapHalfCols() {
        int mid = matrix.length / 2;
        int msize2 = matrix[0].length;
        double tmp;
        for (int j = 0; j < msize2; j++) {
            for (int i = 0; i < mid; i++) {
                tmp = matrix[i][j];
                matrix[i][j] = matrix[i + mid][j];
                matrix[i + mid][j] = tmp;
            }
        }
    }

    public void swapHalfMatrix() {
        swapHalfRows();
        swapHalfCols();
    }

    // assume all data is hypercomplex for now
    // ft ift do not swap left and right half, for fast ft-ift IST op
    public void ft2dNoswap() {
        int msize1 = matrix.length;
        int msize2 = matrix[0].length;
        int i;
        int mid = msize2 / 2;
        double[][] dataRI = new double[2][mid];  // new row buffer
        for (i = 0; i < msize1; i++) {
            ftRow(i, dataRI);
        }
        mid = msize1 / 2;
        dataRI = new double[2][mid];    // new column buffer
        for (i = 0; i < msize2; i++) {
            ftColumn(i, dataRI);
        }
    }

    public void ift2dNoswap() {
        int msize1 = matrix.length;
        int msize2 = matrix[0].length;
        int i;
        int mid = msize2 / 2;
        double[][] dataRI = new double[2][mid];  // new row buffer
        for (i = 0; i < msize1; i++) {
            iftRow(i, dataRI);
        }
        mid = msize1 / 2;
        dataRI = new double[2][mid];    // new column buffer
        for (i = 0; i < msize2; i++) {
            iftColumn(i, dataRI);
        }
    }

    // assume all data is hypercomplex for now
    // ft ift do not swap left and right half, for fast ft-ift IST op
    public void ft2dNoswapZF() {
        int msize1 = matrix.length;
        int msize2 = matrix[0].length;
        int i;
        double[][] dataRI = new double[2][msize2];  // new row buffer
        for (i = 0; i < msize1; i++) {
            ftZFRow(i, dataRI);
        }
        dataRI = new double[2][msize1];    // new column buffer
        for (i = 0; i < msize2; i++) {
            ftZFColumn(i, dataRI);
        }
    }

    public void hift2dNoswapZF() {
        int msize1 = matrix.length;
        int msize2 = matrix[0].length;
        int i;
        double[][] dataRI = new double[2][msize2];  // new row buffer
        for (i = 0; i < msize1; i++) {
            hiftZFRow(i, dataRI);
        }
        dataRI = new double[2][msize1];    // new column buffer
        for (i = 0; i < msize2; i++) {
            hiftZFColumn(i, dataRI);
        }
    }

    public void ft2d() {
        ft2dNoswap();
        swapHalfMatrix();
    }

    public void ift2d() {
        swapHalfMatrix();
        ift2dNoswap();
    }

    private void phaseRows(double p0, double p1, boolean f1abs) {
        int mid = matrix[0].length / 2;
        int msize1 = matrix.length;
        Vec vec = new Vec(mid, true);
        for (int i = 0; i < msize1; i++) {
            for (int j = 0; j < mid; j++) {
                vec.set(j, new Complex(matrix[i][2 * j], matrix[i][2 * j + 1]));
            }
            vec.phase(p0, p1, f1abs, false);
            for (int j = 0; j < mid; j++) {
                matrix[i][2 * j] = vec.getReal(j);
                matrix[i][2 * j + 1] = vec.getImag(j);
            }
        }
    }

    private void phaseCols(double p0, double p1, boolean f2abs) {
        int mid = matrix.length / 2;
        int msize2 = matrix[0].length;
        Vec vec = new Vec(mid, true);
        for (int j = 0; j < msize2; j++) {
            for (int i = 0; i < mid; i++) {
                vec.set(i, new Complex(matrix[2 * i][j], matrix[2 * i + 1][j]));
            }
            vec.phase(p0, p1, f2abs, false);
            for (int i = 0; i < mid; i++) {
                matrix[2 * i][j] = vec.getReal(i);
                matrix[2 * i + 1][j] = vec.getImag(i);
            }
        }
    }

    private final static int NPHASE = 4;

    @Override
    public void phase(double[] ph) {
        if (ph.length < 1) {
            return;  // no need to do phasing
        }
        double[] phase = new double[NPHASE];  // init zeroes
        int len = ph.length < NPHASE ? ph.length : NPHASE;
        int i = 0;
        while (i < len) {  // copy up to 4 values
            phase[i] = ph[i];
            i++;
        }
        phaseRows(phase[0], phase[1], false);  // p0, p1 for rows
        phaseCols(phase[2], phase[3], false);  // p0, p1 for columns
    }

}
