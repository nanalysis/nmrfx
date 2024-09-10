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

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.nmrfx.datasets.MatrixType;
import org.nmrfx.math.Bessel;
import org.nmrfx.processor.processing.ProcessingException;
import org.nmrfx.processor.processing.SampleSchedule;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MatrixND implements MatrixType {

    private double[] data;
    private int[] sizes;
    int[] vSizes;
    private int[] strides;
    private double[] phases0;
    private double[] phases1;
    private boolean[] negatePairss;
    private boolean[] negateImags;
    final int nDim;
    private int nElems;

    /**
     * Output point to write matrix.
     */
    private int[][] pt = null;

    private int[] dim = null;

    public SampleSchedule schedule = null;


    public MatrixND(int... sizes) {
        this.sizes = sizes.clone();
        this.strides = calcStrides(sizes);
        nDim = sizes.length;
        int n = 1;
        for (int i = 0; i < nDim; i++) {
            n *= sizes[i];
        }
        nElems = n;
        data = new double[n];
        vSizes = sizes.clone();
        phases0 = new double[nDim];
        phases1 = new double[nDim];
        negateImags = new boolean[nDim];
        negatePairss = new boolean[nDim];
    }

    public MatrixND(int[][] pt, int... sizes) {
        this(sizes);
        this.pt = pt;
    }

    public MatrixND(int[][] pt, int[] dim, int... sizes) {
        this(sizes);
        this.pt = pt;
        this.dim = dim;
    }

    public MatrixND(MatrixND source) {
        this(source.sizes);
        System.arraycopy(source.data, 0, data, 0, data.length);
    }

    public MatrixND(double[][] data2D) {
        this(data2D.length, data2D[0].length);
        int n = data2D.length;
        int m = data2D[0].length;
        int k = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                data[k++] = data2D[i][j];
            }
        }
    }

    public void setVSizes(int... vSizes) {
        this.vSizes = vSizes.clone();
    }

    public int[] getVSizes() {
        return vSizes.clone();
    }

    public int getVSize(int iDim) {
        return vSizes[iDim];
    }

    @Override
    public String exportData(String rootName, String suffix) throws IOException {
        return exportData(rootName, suffix, false);
    }

    @Override
    public String exportData(String rootName, String suffix, boolean littleEndian) throws IOException {
        int index = getIndex();

        String parFileName = String.format("%s%04d.%s.par", rootName, index + 1, suffix);
        try (FileOutputStream oStream = new FileOutputStream(parFileName)) {
            ByteBuffer byteBuffer = ByteBuffer.allocate((1 + nDim) * Integer.SIZE / 8);
            if (littleEndian) {
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            IntBuffer intBuffer = byteBuffer.asIntBuffer();
            intBuffer.put(0, nDim);
            for (int i = 0; i < nDim; i++) {
                intBuffer.put(1 + i, sizes[i] / 2);
            }
            FileChannel channel = oStream.getChannel();
            channel.write(byteBuffer);
        } catch (IOException ioE) {
            throw ioE;
        }

        String outFileName = String.format("%s%04d.%s", rootName, index + 1, suffix);

        try (FileOutputStream oStream = new FileOutputStream(outFileName)) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * Double.SIZE / 8);
            if (littleEndian) {
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
            doubleBuffer.put(data);
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
            ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * Double.SIZE / 8);
            if (littleEndian) {
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
            FileChannel channel = oStream.getChannel();
            channel.read(byteBuffer);
            doubleBuffer.get(data);
        } catch (IOException ioE) {
            throw ioE;
        }
        return inFileName;
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
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(sizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        int i = 0;
        try (FileWriter fw = fileWriter) {
            while (iterator.hasNext()) {
                iterator.next();
                int[] counts = iterator.getCounts();
                for (int count : counts) {
                    if (fw != null) {
                        fw.write(String.format("%3d ", count));
                    } else {
                        System.out.printf("%3d ", count);
                    }
                }
                if (fw != null) {
                    fw.write(String.format("%3d %.5f%n", i, data[i++]));
                } else {
                    System.out.printf("%3d %.5f%n", i, data[i++]);
                }
            }
        }
    }

    @Override
    public int getIndex() {
        if (pt == null) {
            return 0;
        } else {
            return pt[pt.length - 1][0];
        }
    }

    public void setPt(int[][] pt) {
        this.pt = pt;
    }

    public int[][] getPt() {
        return pt;
    }

    public int[] getDim() {
        return dim;
    }

    public int getNElems() {
        return nElems;
    }

    public int[] getSizes() {
        return sizes.clone();
    }

    public int getSize(int iDim) {
        return sizes[iDim];
    }

    public int getNDim() {
        return nDim;
    }

    public double getPh0(int i) {
        return phases0[i];
    }

    public double getPh1(int i) {
        return phases1[i];
    }

    private static int[] calcStrides(int[] shape) {
        int[] strides = new int[shape.length];
        int stride = 1;
        for (int i = shape.length - 1; i >= 0; i--) {
            strides[i] = stride;
            stride *= shape[i];
        }
        return strides;
    }

    public final double[] getVector(int axis, int... index) {
        int offset = 0;
        for (int k = 0, i = 0; i < nDim; i++) {
            if (i != axis) {
                offset += index[k++] * strides[i];
            }
        }
        int n = sizes[axis];
        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            result[i] = data[offset];
            offset += strides[axis];
        }
        return result;
    }

    private void getVectorRI(int axis, double[][] riVec, int... index) {
        int offset = 0;
        for (int k = 0, i = 0; i < nDim; i++) {
            if (i != axis) {
                offset += index[k++] * strides[i];
            }
        }
        Arrays.fill(riVec[0], 0.0);
        Arrays.fill(riVec[1], 0.0);
        int n = sizes[axis] / 2;
        for (int i = 0; i < n; i++) {
            riVec[0][i] = data[offset];
            offset += strides[axis];
            riVec[1][i] = data[offset];
            offset += strides[axis];
        }
    }

    private void getVectorR(int axis, double[][] riVec, int... index) {
        int offset = 0;
        for (int k = 0, i = 0; i < nDim; i++) {
            if (i != axis) {
                offset += index[k++] * strides[i];
            }
        }
        int n = riVec[0].length;
        for (int i = 0; i < n; i++) {
            riVec[0][i] = data[offset];
            offset += strides[axis];
        }
    }

    public final void getVectorZF(int axis, double[][] riVec, int... index) {
        int offset = 0;
        for (int k = 0, i = 0; i < nDim; i++) {
            if (i != axis) {
                offset += index[k++] * strides[i];
            }
        }
        int n = sizes[axis] / 2;
        for (int i = 0; i < n; i++) {
            riVec[0][i] = data[offset];
            offset += strides[axis];
            riVec[1][i] = data[offset];
            offset += strides[axis];
        }
        for (int i = n; i < riVec[0].length; i++) {
            riVec[0][i] = 0.0;
            riVec[1][i] = 0.0;
        }
    }

    public final void putVectorReal(int axis, double[][] riVec, int... index) {
        int offset = 0;
        for (int k = 0, i = 0; i < nDim; i++) {
            if (i != axis) {
                offset += index[k++] * strides[i];
            }
        }
        int n = riVec[0].length;
        for (int i = 0; i < n; i++) {
            data[offset] = riVec[0][i];
            offset += strides[axis];
        }
    }

    public final void putVectorRI(int axis, double[][] riVec, int... index) {
        int offset = 0;
        for (int k = 0, i = 0; i < nDim; i++) {
            if (i != axis) {
                offset += index[k++] * strides[i];
            }
        }
        int n = sizes[axis] / 2;
        for (int i = 0; i < n; i++) {
            data[offset] = riVec[0][i];
            offset += strides[axis];
            data[offset] = riVec[1][i];
            offset += strides[axis];
        }
    }

    public final int getOffset(int... index) {
        int offset = 0;
        for (int i = 0; i < strides.length; i++) {
            offset += index[i] * strides[i];
        }
        return offset;
    }

    public int[] getSubSizes(int axis) {
        int[] subSizes;
        if (nDim > 1) {
            subSizes = new int[nDim - 1];
            for (int k = 0, i = 0; i < nDim; i++) {
                if (i != axis) {
                    subSizes[k++] = sizes[i];
                }
            }
        } else {
            subSizes = new int[1];
            subSizes[0] = 1;
        }
        return subSizes;
    }

    private void fft(double[][] riVec) {
        FastFourierTransformer.transformInPlace(riVec, DftNormalization.STANDARD, TransformType.FORWARD);
    }

    private void ifft(double[][] riVec) {
        FastFourierTransformer.transformInPlace(riVec, DftNormalization.STANDARD, TransformType.INVERSE);
    }

    private void fftShuffle(double[][] riVec) {
        int mid = riVec[0].length / 2;
        double tmp;
        for (int i = 0; i < mid; i++) {
            tmp = riVec[0][i];
            riVec[0][i] = riVec[0][i + mid];
            riVec[0][i + mid] = tmp;
            tmp = riVec[1][i];
            riVec[1][i] = riVec[1][i + mid];
            riVec[1][i + mid] = tmp;
        }
    }

    private void phase(double[][] riVec, double p0, double p1) {
        double degtorad = Math.PI / 180.0;
        int size = riVec[0].length;

        p0 *= degtorad;
        p1 *= degtorad;

        double tol = 0.0001;

        if (Math.abs(p1) < tol) {
            if (Math.abs(p0) < tol) {
                return;
            }
            if (Math.abs(p0 - Math.PI) < tol) {
            }

            double fReal = Math.cos(p0);
            double fImag = -Math.sin(p0);

            for (int i = 0; i < size; i++) {
                double rVal = riVec[0][i];
                double iVal = riVec[1][i];

                riVec[0][i] = rVal * fReal - iVal * fImag;
                riVec[1][i] = rVal * fImag + iVal * fReal;
            }

            return;
        }

        double dDelta = p1 / (size - 1);
        for (int i = 0; i < size; i++) {
            double fReal = Math.cos(p0 + i * dDelta);
            double fImag = -Math.sin(p0 + i * dDelta);
            double rVal = riVec[0][i];
            double iVal = riVec[1][i];

            riVec[0][i] = rVal * fReal - iVal * fImag;
            riVec[1][i] = rVal * fImag + iVal * fReal;
        }

    }

    private void sineBell(double[][] riVec, int apodSize) {
        double offset = 0.5;
        double end = 0.99;
        double start = offset * Math.PI;
        double power = 1.0;
        double c = 0.5;
        double delta = ((end - offset) * Math.PI) / (apodSize - 1);
        for (int i = 0; i < apodSize; i++) {
            double rVal = riVec[0][i];
            double iVal = riVec[1][i];
            double scale;
            if (power != 1.0) {
                scale = Math.pow(Math.sin(start + (i * delta)), power);
            } else {
                scale = Math.sin(start + (i * delta));
            }
            if (i == 0) {
                scale *= c;
            }
            riVec[0][i] = rVal * scale;
            riVec[1][i] = iVal * scale;
        }
    }

    private void blackman(double[][] riVec, int apodSize) {
        double offset = 0.5;
        double end = 0.99;
        double c = 0.5;
        double start = offset * Math.PI;

        double delta = ((end - offset) * Math.PI) / (apodSize - 1);
        for (int i = 0; i < apodSize; i++) {
            double rVal = riVec[0][i];
            double iVal = riVec[1][i];
            double deltaPos = i;
            double scale = 0.42 - 0.5 * Math.cos(2.0 * start + 2.0 * (deltaPos * delta)) + 0.08 * Math.cos(4.0 * (deltaPos * delta));
            if (i == 0) {
                scale *= c;
            }
            riVec[0][i] = rVal * scale;
            riVec[1][i] = iVal * scale;
        }

    }

    private void kaiser(double[][] riVec, int apodSize) {
        double offset = 0.5;
        double end = 0.99;
        double c = 0.5;
        double beta = 10.0;

        double delta = ((end - offset)) / (apodSize - 1);
        for (int i = 0; i < apodSize; i++) {
            double rVal = riVec[0][i];
            double iVal = riVec[1][i];
            double deltaPos = i;
            double v1 = beta * Math.sqrt(1.0 - Math.pow(2.0 * deltaPos * delta, 2));
            double v2 = beta;
            double scale = Bessel.i0(v1) / Bessel.i0(v2);
            if (i == 0) {
                scale *= c;
            }
            riVec[0][i] = rVal * scale;
            riVec[1][i] = iVal * scale;
        }

    }

    public void doFTtoReal() {
        for (int i = 0; i < nDim; i++) {
            doFTtoReal(i);
        }
    }

    public void doFTtoReal(int axis) {
        int[] subSizes = getSubSizes(axis);
        double[][] riVec = new double[2][sizes[axis]];
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(subSizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            getVectorZF(axis, riVec, counts);
            fft(riVec);
            putVectorReal(axis, riVec, counts);
        }
    }

    @Override
    public void phase(double[] phase) {
        doPhaseTD(phase);
    }

    public void doPhaseTD(double[] phaseValues) {
        for (int i = 0; i < nDim; i++) {
            double ph0 = 0.0;
            double ph1 = 0.0;
            if (i * 2 < phaseValues.length) {
                ph0 = phaseValues[i * 2];
            }
            if ((i * 2 + 1) < phaseValues.length) {
                ph1 = phaseValues[i * 2 + 1];
            }
            doPhaseTD(i, ph0, ph1);
            phases0[i] = ph0;
            phases1[i] = ph1;
        }
    }

    public void doPhaseTD(double[] phaseValues, boolean[] negateImags, boolean[] negatePairss) {
        for (int i = 0; i < nDim; i++) {
            double ph0 = 0.0;
            double ph1 = 0.0;
            if (i * 2 < phaseValues.length) {
                ph0 = phaseValues[i * 2];
            }
            if ((i * 2 + 1) < phaseValues.length) {
                ph1 = phaseValues[i * 2 + 1];
            }

            boolean negateImag = false;
            boolean negatePairs = false;
            if (negateImags != null && i < negateImags.length) {
                negateImag = negateImags[i];
            }
            if (negatePairss != null && i < negatePairss.length) {
                negatePairs = negatePairss[i];
            }
            doPhaseTD(i, ph0, ph1, negateImag, negatePairs);
            phases0[i] = ph0;
            phases1[i] = ph1;
            this.negateImags[i] = negateImag;
            this.negatePairss[i] = negatePairs;
        }
    }

    public void apodize() {
        for (int i = 0; i < nDim; i++) {
            MatrixND.this.apodize(i);
        }
    }

    public void apodize(int axis) {
        int[] subSizes = getSubSizes(axis);
        double[][] riVec = new double[2][sizes[axis]];
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(subSizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        double tol = 0.0001;
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            getVectorRI(axis, riVec, counts);
            sineBell(riVec, vSizes[axis] / 2);
            putVectorRI(axis, riVec, counts);
        }
    }

    public void applyApod(int axis, double[] apodVec) {
        int[] subSizes = getSubSizes(axis);
        double[][] riVec = new double[2][sizes[axis]];
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(subSizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            getVectorRI(axis, riVec, counts);
            applyApod(riVec, apodVec);
            putVectorRI(axis, riVec, counts);
        }
    }

    private void applyApod(double[][] riVec, double[] apodVec) {
        for (int i = 0; i < apodVec.length; i++) {
            double scale = apodVec[i];
            riVec[0][i] *= scale;
            riVec[1][i] *= scale;
        }
        for (int i = apodVec.length; i < riVec[0].length; i++) {
            riVec[0][i] = 0.0;
            riVec[1][i] = 0.0;
        }
    }

    public void doPhaseTD(int axis, double ph0, double ph1) {

        int[] subSizes = getSubSizes(axis);
        double[][] riVec = new double[2][sizes[axis]];
        double tol = 0.0001;
        if ((Math.abs(ph0) < tol) && (Math.abs(ph1) < tol)) {
            return;
        }
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(subSizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            getVectorRI(axis, riVec, counts);
            if (Math.abs(ph1) < tol) {
                phase(riVec, ph0, 0.0);
            } else {
                fft(riVec);
                fftShuffle(riVec);
                phase(riVec, ph0, ph1);
                fftShuffle(riVec);
                ifft(riVec);
            }
            putVectorRI(axis, riVec, counts);
        }
    }

    private MultidimensionalCounter.Iterator axisIterator(int axis) {
        int[] subSizes = getSubSizes(axis);
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(subSizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        return iterator;
    }


    public void doPhaseTD(int axis, double ph0, double ph1, boolean negateImag, boolean negatePairs) {
        int[] subSizes = getSubSizes(axis);
        int size = sizes[axis];
        double[][] riVec = new double[2][size];
        double tol = 0.0001;
        if ((Math.abs(ph0) < tol) && (Math.abs(ph1) < tol)) {
            return;
        }
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(subSizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            getVectorRI(axis, riVec, counts);
            if (negateImag) {
                negateImag(riVec);
            }
            if (negatePairs) {
                negatePairs(riVec);
            }
            fft(riVec);
            fftShuffle(riVec);
            phase(riVec, ph0, ph1);
            fftShuffle(riVec);
            ifft(riVec);
            if (negateImag) {
                negateImag(riVec);
            }
            if (negatePairs) {
                negatePairs(riVec);
            }
            putVectorRI(axis, riVec, counts);
        }
    }

    public void doNegatePairs(int axis) {
        double[][] riVec = new double[2][sizes[axis] / 2];
        var iterator = axisIterator(axis);
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            getVectorRI(axis, riVec, counts);
            negatePairs(riVec);
            putVectorRI(axis, riVec, counts);
        }
    }

    private static void negatePairs(double[][] riVec) {
        int size = riVec[0].length;
        for (int i = 1; i < size; i += 2) {
            riVec[0][i] = -riVec[0][i];
            riVec[1][i] = -riVec[1][i];
        }
    }

    private static void negateImag(double[][] riVec) {
        int size = riVec[0].length;
        for (int i = 0; i < size; ++i) {
            riVec[1][i] = -riVec[1][i];
        }
    }

    public void doHIFT(double fpMul) {
        for (int i = nDim - 1; i >= 0; i--) {
            doHIFT(i, fpMul);
        }
    }

    public void doHIFT(int axis, double fpMul) {
        int[] subSizes = getSubSizes(axis);
        double[][] riVec = new double[2][sizes[axis]];
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(subSizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            getVectorR(axis, riVec, counts);
            VecUtil.hift(riVec, riVec[0].length, fpMul);
            putVectorRI(axis, riVec, counts);
        }
    }

    static int getZfSize(double vecSize, int factor) {
        int size = (int) (Math.pow(2, Math.ceil((Math.log(vecSize) / Math.log(2)) + factor)));
        return size;
    }

    public boolean ensurePowerOf2() {
        boolean needsZF = false;
        int[] zfSizes = new int[getNDim()];
        for (int i = 0; i < getNDim(); i++) {
            int zfSize = getZfSize(getSize(i), 0);
            zfSizes[i] = zfSize;
            if (zfSize != getSize(i)) {
                needsZF = true;
            }
        }
        if (needsZF) {
            zeroFill(zfSizes);
        }
        return needsZF;
    }

    public void zeroFill(int factor) {
        if (factor < 0) {
            factor = 0;
        }
        int[] newSizes = new int[nDim];
        boolean needsZF = false;
        for (int i = 0; i < nDim; i++) {
            newSizes[i] = getZfSize(sizes[i], factor);
            if (newSizes[i] != sizes[i]) {
                needsZF = true;
            }
        }
        if (needsZF) {
            zeroFill(newSizes);
        }
    }

    public void zeroFill(int[] newSizes) {
        MatrixND zfMatrix = new MatrixND(newSizes);
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(sizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            zfMatrix.setValue(getValue(counts), counts);

        }
        data = zfMatrix.data;
        sizes = zfMatrix.sizes;
        strides = zfMatrix.strides;
        nElems = zfMatrix.nElems;
        if (pt != null) {
            for (int i = 0; i < nDim; i++) {
                pt[i][1] = sizes[i] - 1;
            }
        }
    }

    boolean checkShapes(MatrixND matrixND) {
        boolean value = true;
        if (matrixND.nDim != nDim) {
            value = false;
        } else {
            int[] srcSizes = matrixND.getSizes();
            for (int i = 0; i < nDim; i++) {
                if (srcSizes[i] != sizes[i]) {
                    value = false;
                    break;
                }
            }
        }

        return value;
    }

    public void copyFrom(MatrixND src) {
        if (!checkShapes(src)) {
            throw new ProcessingException("copyMatrix dimensions not equal");
        }
        System.arraycopy(src.data, 0, data, 0, data.length);

    }

    public static void zeroValues(double[] values, int[] zeroList) {
        for (int i : zeroList) {
            values[i] = 0.0;
        }
    }

    public void zeroValues(int[] zeroList) {
        for (int i : zeroList) {
            data[i] = 0.0;
        }
    }

    /**
     * Get maximum of absolute value of a matrix.
     *
     * @param input
     * @return position of maximum in input matrix
     * @see Matrix
     */
    private static double getAbsMaxReal(double[] values) {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < values.length; i++) {
            double val = values[i];
            double aval = Math.abs(val);
            if (aval > max) {
                max = aval;
            }
        }
        return max;
    }

    private static void cutRealAboveThreshold(double[] in, double[] add, double threshold) {
        double cutoff = getAbsMaxReal(in);
        double th = threshold * cutoff;
        int n = in.length;
        for (int i = 0; i < n; i++) {
            double value = in[i];
            double avalue = Math.abs(value);
            if (avalue > th) {
                if (value > 0.0) {
                    add[i] += (avalue - th);
                    in[i] = th;
                } else {
                    add[i] -= (avalue - th);
                    in[i] = -th;
                }
            }
        }
    }

    public void cutRealAboveThreshold(double[] add, double threshold) {
        cutRealAboveThreshold(data, add, threshold);
    }

    public static void copyValues(double[] source, double[] target, int[] srcTargetMap) {
        for (int i = 0; i < srcTargetMap.length; i++) {
            target[srcTargetMap[i]] = source[i];
        }
    }

    public void copyValuesFrom(MatrixND source, int[] srcTargetMap) {
        for (int i : srcTargetMap) {
            data[i] = source.data[i];
        }
    }

    public record MatrixDiff(double mabs, double max) {};
    public MatrixDiff calcDifference(MatrixND source, int[] srcTargetMap) {
        double sum = 0.0;
        double max = 0.0;
        for (int i : srcTargetMap) {
            double v1 = source.data[i];
            double v2 = data[i];
            max = Math.max(max, Math.abs(v1));
            sum += FastMath.abs(v1 - v2);
        }
        return new MatrixDiff(sum/srcTargetMap.length, max);
    }

    public double calcSumAbs() {
        double sum = 0.0;
        for (double value : data) {
            sum += FastMath.abs(value);
        }
        return sum / data.length;
    }

    public static void copyData(double[] source, double[] target) {
        System.arraycopy(source, 0, target, 0, target.length);
    }

    public void copyDataTo(double[] target) {
        System.arraycopy(data, 0, target, 0, data.length);
    }

    public void copyDataFrom(double[] values) {
        System.arraycopy(values, 0, data, 0, data.length);
    }

    public void addDataFrom(double[] values) {
        for (int i = 0; i < nElems; i++) {
            data[i] += values[i];
        }
    }

    ArrayIndexOutOfBoundsException getOffsetException(int offset, int[] indices) {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append("Out of bounds for offset ").append(offset);
        int i = 0;

        for (int index : indices) {
            sBuilder.append(" ").append(index);
            sBuilder.append(" ").append(sizes[i++]);
        }
        return new ArrayIndexOutOfBoundsException(sBuilder.toString());
    }

    public void setValue(double value, int... indices) {
        int offset = getOffset(indices);
        try {
            data[offset] = value;
        } catch (ArrayIndexOutOfBoundsException aE) {
            throw getOffsetException(offset, indices);
        }
    }

    public double getValue(int... indices) {
        int offset = getOffset(indices);
        return data[offset];
    }

    public double getValueAtIndex(int index) {
        return data[index];
    }

    public void setValueAtIndex(int index, double value) {
        data[index] = value;
    }

    int[] genOffsets(int nDim) {
        int[] twoSizes = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            twoSizes[i] = 2;
        }
        int n = (int) Math.pow(2, nDim);
        int[] result = new int[n];
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(twoSizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            result[i] = 0;
            for (int j = 0; j < nDim; j++) {
                result[i] += counts[j] * strides[j];
            }
            i++;
        }
        return result;
    }

    public double calcL1AndGradient(double mu) {
        int[] offsets = genOffsets(sizes.length);
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(sizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        double l1Norm = 0.0;
        int i = 0;
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            boolean realPt = true;
            for (int count : counts) {
                if ((count % 2) == 1) {
                    realPt = false;
                    break;
                }
            }
            if (realPt) {
                double sumSq = 0.0;
                for (int offset : offsets) {
                    int index = i + offset;
                    double value = data[index];
                    sumSq += value * value;
                }
                double absValue = Math.sqrt(sumSq);
                l1Norm += absValue;
                double divisor = absValue < mu ? mu : absValue;
                for (int offset : offsets) {
                    int index = i + offset;
                    data[index] /= divisor;
                }
            }
            i++;
        }
        return l1Norm;
    }

    public double calcRealL1AndGradient(double mu) {
        double l1Norm = 0.0;
        for (int i = 0; i < data.length; i++) {
            double absValue = FastMath.abs(data[i]);
            l1Norm += absValue;
            double divisor = absValue < mu ? mu : absValue;  // Equation 3.1 in NESTA paper
            data[i] /= divisor;
        }
        return l1Norm;
    }

    public SummaryStatistics calcRealStats() {
        SummaryStatistics sStats = new SummaryStatistics();
        for (int i = 0; i < data.length; i++) {
            double absValue = FastMath.abs(data[i]);
            sStats.addValue(absValue);
        }
        return sStats;
    }

    public double[] measureReal(double mean, double sdev) {
        double[] measures = {Double.MAX_VALUE, Double.NEGATIVE_INFINITY, 0.0, 0.0};
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(sizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        double sum = 0.0;
        double sum2 = 0.0;
        int n = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] < measures[0]) {
                measures[0] = data[i];
            }
            if (data[i] > measures[1]) {
                measures[1] = data[i];
            }
            double delta = data[i] - mean;
            if (Math.abs(delta) < 3.0 * sdev) {
                sum += data[i];
                sum2 += data[i] * data[i];
                n++;
            }
        }
        sdev = Math.sqrt(n * sum2 - sum * sum) / n;
        mean = sum / n;
        measures[2] = mean;
        measures[3] = sdev;
        return measures;

    }

    public double[] measure(boolean isComplex, double mean, double sdev) {
        if (!isComplex) {
            return measureReal(mean, sdev);
        }
        double[] measures = {Double.MAX_VALUE, Double.NEGATIVE_INFINITY, 0.0, 0.0};
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(sizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        double sum = 0.0;
        double sum2 = 0.0;
        int n = 0;
        for (int i = 0; iterator.hasNext(); i++) {
            iterator.next();
            int[] counts = iterator.getCounts();
            boolean realPt = true;
            if (isComplex) {
                for (int count : counts) {
                    if ((count % 2) == 1) {
                        realPt = false;
                        break;
                    }
                }
            }
            if (realPt) {
                if (data[i] < measures[0]) {
                    measures[0] = data[i];
                }
                if (data[i] > measures[1]) {
                    measures[1] = data[i];
                }
                double delta = data[i] - mean;
                if (Math.abs(delta) < 3.0 * sdev) {
                    sum += data[i];
                    sum2 += data[i] * data[i];
                    n++;
                }
            }
        }
        sdev = Math.sqrt(n * sum2 - sum * sum) / n;
        mean = sum / n;
        measures[2] = mean;
        measures[3] = sdev;
        return measures;
    }

    public int[][] findZeros() {
        int[] complexSizes = new int[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            complexSizes[i] = sizes[i] / 2;
        }
        int nComplex = (int) Math.round(Math.pow(2, getNDim()));
        boolean[] validPositions = new boolean[getNElems()];

        MultidimensionalCounter mdCounter = new MultidimensionalCounter(complexSizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        int nValid = 0;
        while(iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            int[] complexSample = new int[counts.length];
            for (int k = 0; k < nComplex; k++) {
                int divisor = 1;
                for (int j = 0; j < counts.length; j++) {
                    int cDelta = (k / divisor) % 2;
                    divisor *= 2;
                    complexSample[counts.length - j - 1] = counts[counts.length - j - 1] * 2 + cDelta;
                }
                int offset = getOffset(complexSample);
                if (Math.abs(data[offset]) > 1.0e-12){
                    validPositions[offset] = true;
                    nValid++;
                }
            }
        }
        int nZeros = getNElems() - nValid;
        int[] zeroList = new int[nZeros];
        int[] srcTargetMap = new int[nValid];
        int i = 0;
        int k = 0;
        int iValid = 0;
        for (boolean valid : validPositions) {
            if (!valid) {
                zeroList[k++] = i;
            } else {
                srcTargetMap[iValid++] = i;
            }
            i++;
        }
        int[][] result = {zeroList, srcTargetMap};
        return result;
    }

    public ArrayList<MatrixPeak> peakPick(double globalThreshold, double noiseThreshold, boolean includeNegative, boolean isComplex, double scale) {
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(sizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        ArrayList<MatrixPeak> peaks = new ArrayList<>();
        int[][] pts = new int[nDim + 1][3];
        int[][] indices = new int[nDim + 1][3];
        double[][] intensities = new double[nDim + 1][3];
        int[] widthLim = new int[nDim + 1];
        widthLim[0] = 2;
        for (int i = 0; i < sizes.length; i++) {
            widthLim[i + 1] = sizes[i] / 32;
            if (widthLim[i + 1] < 3) {
                widthLim[i + 1] = 3;
            }
        }
        double threshold = FastMath.max(globalThreshold, noiseThreshold);
        int step = isComplex ? 2 : 1;
        double maxValue = Double.NEGATIVE_INFINITY;
        int nPossible = 0;
        for (int i = 0; iterator.hasNext(); i++) {
            iterator.next();
            int[] counts = iterator.getCounts();
            boolean realPt = true;
            if (isComplex) {
                for (int count : counts) {
                    if ((count % 2) == 1) {
                        realPt = false;
                        break;
                    }
                }
            }
            if (realPt) {
                double ptValue = data[i];
                double sign = 1.0;
                if (ptValue < 0.0) {
                    if (!includeNegative) {
                        continue;
                    } else {
                        ptValue *= -1.0;
                        sign = -1.0;
                    }
                }
                if (ptValue > maxValue) {
                    maxValue = ptValue;
                }
                if (ptValue > threshold) {
                    nPossible++;
                    boolean ok = true;
                    pts[0][1] = getIndex();
                    indices[0][1] = getIndex();
                    intensities[0][1] = ptValue * sign;

                    for (int jDim = 0; jDim < nDim; jDim++) {
                        int kDim = jDim + 1;
                        pts[kDim][1] = counts[jDim];
                        indices[kDim][1] = i;
                        intensities[kDim][1] = ptValue * sign;
                        int nBelowThresh = 0;
                        if (counts[jDim] > 0) {
                            int index = i - strides[jDim] * step; // 2 assumes complex
                            double testValue = sign * data[index];
//                            if ((ptValue < testValue) || (testValue < noiseThreshold)) {
                            if ((ptValue < testValue)) {
                                ok = false;
                                break;
                            }
                            if (testValue < noiseThreshold) {
                                nBelowThresh++;
                            }

                            pts[kDim][0] = counts[jDim] - 1;
                            indices[kDim][0] = index;
                            intensities[kDim][0] = testValue * sign;
                        }
                        if (ok && counts[jDim] < (sizes[jDim] - 1)) {
                            int index = i + strides[jDim] * step; // 2 assumes complex
                            double testValue = sign * data[index];
                            if (ptValue < testValue) {
                                ok = false;
                                break;
                            }
                            if (testValue < noiseThreshold) {
                                nBelowThresh++;
                            }
                            pts[kDim][2] = counts[jDim] + 1;
                            indices[kDim][2] = index;
                            intensities[kDim][2] = testValue * sign;
                        }
                    }

                    if (ok) {
                        peaks.add(new MatrixPeak(intensities, indices, pts, scale, widthLim));
                    }
                }
            }
        }
        return peaks;
    }

    public SampleSchedule schedule() {
        return schedule;
    }

    public void schedule(SampleSchedule sampleSchedule) {
        schedule = sampleSchedule;
    }

    // *** Simon's additions ***

    /** A generic method which operates on all RI vectors in a given dimension.
     *
     * `op` should be a `static void` method which accepts a riVec
     * (of size [2, N], where N is the number of complex points in the
     * dimension). Define `op` in the `RIVecOperations` class at the bottom of
     * this file.
     */
    void manipulateRIVecs(int axis, Consumer<double[][]> op) {
        int[] subSizes = getSubSizes(axis);
        double[][] riVec = new double[2][sizes[axis] / 2];
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(subSizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            getVectorRI(axis, riVec, counts);
            op.accept(riVec);
            putVectorRI(axis, riVec, counts);
        }
    }


    <U> void manipulateRIVecs(int axis, U extraArgs, BiConsumer<double[][], U> op) {
        int[] subSizes = getSubSizes(axis);
        double[][] riVec = new double[2][sizes[axis] / 2];
        MultidimensionalCounter mdCounter = new MultidimensionalCounter(subSizes);
        MultidimensionalCounter.Iterator iterator = mdCounter.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            getVectorRI(axis, riVec, counts);
            op.accept(riVec, extraArgs);
            putVectorRI(axis, riVec, counts);
        }
    }

    // TODO: check negateImag as same number of values as dimensions in the matrix
    public void doNegateImag(boolean[] negateImag) {
        for (int axis = 0; axis < nDim; axis++) {
            doNegateImag(axis, negateImag[axis]);
        }
    }

    public void doNegateImag(int axis, boolean negateImag) {
        if (negateImag) {
            manipulateRIVecs(axis, RIVecOperations::negateImag);
        }
    }

    public void doNegatePairs(boolean[] negatePairs) {
        for (int axis = 0; axis < nDim; axis++) {
            doNegatePairs(axis, negatePairs[axis]);
        }
    }

    public void doNegatePairs(int axis, boolean negatePairs) {
        if (negatePairs) {
            manipulateRIVecs(axis, RIVecOperations::negatePairs);
        }
    }

    public void doFourierTransform(int axis, double cValue) {
        manipulateRIVecs(axis, cValue, RIVecOperations::fourierTransform);
    }

    public void doFourierTransform(double[] cValues) {
        for (int axis = 0; axis < nDim; axis++) {
            double cValue = cValues[axis];
            doFourierTransform(axis, cValue);
        }
    }

    public void doInverseFourierTransform(int axis) {
        manipulateRIVecs(axis, RIVecOperations::inverseFourierTransform);
    }

    public void doInverseFourierTransform() {
        for (int axis = 0; axis < nDim; axis++) {
            doInverseFourierTransform(axis);
        }
    }

    public void doPhaseCorrection(int axis, double ph0, double ph1) {
        manipulateRIVecs(axis, new double[]{ph0, ph1}, RIVecOperations::phaseCorrection);
    }

    public void doPhaseCorrection(double[] ph0, double[] ph1) {
        for (int axis = 0; axis < nDim; axis++) {
            doPhaseCorrection(axis, ph0[axis], ph1[axis]);
        }
    }
}

class RIVecOperations {
    private final static double degtorad = Math.PI / 180.0;

    private static int getSize(double[][] riVec) {
        return riVec[0].length;
    }

    public static void negateImag(double[][] riVec) {
        for (int i = 0; i < getSize(riVec); i++) {
            riVec[1][i] *= -1.0;
        }
    }

    public static void negatePairs(double[][] riVec) {
        for (int i = 1; i < getSize(riVec); i += 2) {
            riVec[0][i] *= -1.0;
            riVec[1][i] *= -1.0;
        }
    }

    public static void fourierTransform(double[][] riVec, double cValue) {
        riVec[0][0] *= cValue;
        riVec[1][0] *= cValue;
        FastFourierTransformer.transformInPlace(riVec, DftNormalization.STANDARD, TransformType.FORWARD);
    }

    public static void inverseFourierTransform(double[][] riVec) {
        FastFourierTransformer.transformInPlace(riVec, DftNormalization.STANDARD, TransformType.INVERSE);
    }

    public static void phaseCorrection(double[][] riVec, double[] phases) {
        double ph0 = degtorad * phases[0];
        double ph1 = degtorad * phases[1];
        double size = riVec[0].length;

        double pReal;
        double pImag;
        double vReal;
        double vImag;

        double p = ph0;
        double delta = ph1 / (size - 1);

        for (int n = 0; n < size; n++) {
            pReal = FastMath.cos(p);
            pImag = -FastMath.sin(p);
            vReal = riVec[0][n];
            vImag = riVec[1][n];

            riVec[0][n] = vReal * pReal - vImag * pImag;
            riVec[1][n] = vReal * pImag + vImag * pReal;

            p += delta;
        }
    }
}
