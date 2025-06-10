package org.nmrfx.processor.math;

import com.google.common.util.concurrent.AtomicDouble;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.junit.Assert;
import org.junit.Test;

public class ConvolutionFitterTest {

    private ConvolutionFitter getConvolutionFitter() {
        int n = 17;
        int widthPt = 4;
        double shapeFactor = 0.0;
        return new ConvolutionFitter(n, widthPt, shapeFactor);
    }

    private ConvolutionFitter getConvolutionFitter2D() {
        int n = 17;
        int m = 17;
        int[] psfSize = {n, m};
        int widthPt = 4;
        double[] widths = {widthPt, widthPt};
        double shapeFactor = 0.0;
        return new ConvolutionFitter(psfSize, widths, shapeFactor);
    }

    private void dumpPSF(MatrixND matrixND, double limit) {
        MultidimensionalCounter mCounter = new MultidimensionalCounter(matrixND.getSizes());
        var iterator = mCounter.iterator();
        double sum = 0.0;
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            double value = matrixND.getValue(counts);
            sum += value;
            if (value > limit) {
                for (int i = 0; i < counts.length; i++) {
                    System.out.print(counts[i] + " ");
                }
                System.out.println(value);
            }
        }
        System.out.println("sum " + sum);
    }
    private MatrixND genSignal(ConvolutionFitter convolutionFitter, int iSig) {
        double[] signal = new double[256];
        signal[iSig] = 1.0;
        return convolutionFitter.convolutionTest(signal);
    }

    private MatrixND genSignal2D(ConvolutionFitter convolutionFitter, int n, int m, int iSig, int jSig) {
        double[][] signal = new double[n][m];
        signal[iSig][jSig] = 1.0;
        return convolutionFitter.convolutionTest2D(signal);
    }

    @Test
    public void testConvolve() {
        ConvolutionFitter convolutionFitter = getConvolutionFitter();
        int iSig = 10;
        MatrixND convolved = genSignal(convolutionFitter, iSig);
        double max = 0;
        double sum = 0.0;
        int imax = 0;
        for (int i = 0; i < convolved.getSize(0); i++) {
            if (convolved.getValue(i) > max) {
                max = convolved.getValue(i);
                imax = i;
            }
            sum += convolved.getValue(i);
        }
        Assert.assertEquals(convolutionFitter.psfMax, max, 1.0e-6);
        Assert.assertEquals(1.0, sum, 1.0e-6);
        Assert.assertEquals(iSig, imax);
    }

    int[] findMax(MatrixND matrixND) {
        int[] imax = new int[matrixND.getNDim()];
        final AtomicDouble max = new AtomicDouble(0.0);
        matrixND.stream().forEach(counts -> {
            double value = matrixND.getValue(counts);
            if (value > max.get()) {
                max.set(value);
                System.arraycopy(counts, 0, imax, 0, imax.length);
            }
        });
        return imax;
    }
    @Test
    public void testConvolve2D() {
        ConvolutionFitter convolutionFitter = getConvolutionFitter2D();
        int iSig = 50;
        int jSig = 60;
        MatrixND convolved = genSignal2D(convolutionFitter, 256, 128, iSig, jSig);
        double max = 0;
        double sum = 0.0;
        MultidimensionalCounter mCounter = new MultidimensionalCounter(convolved.getSizes());
        var iterator = mCounter.iterator();
        int[] imax = new int[convolved.getNDim()];
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            double value = convolved.getValue(counts);
            if (value > max) {
                max = value;
                System.arraycopy(counts, 0, imax, 0, imax.length);
            }
            sum += value;

        }
        Assert.assertEquals(convolutionFitter.psfMax, max, 1.0e-6);
        Assert.assertEquals(1.0, sum, 0.01);
        Assert.assertEquals(iSig, imax[0]);
        Assert.assertEquals(jSig, imax[1]);
    }
    @Test
    public void testConvolve2D1() {
        ConvolutionFitter convolutionFitter = getConvolutionFitter2D();
        int iSig = 90;
        int jSig = 53;
        MatrixND signal = genSignal2D(convolutionFitter, 128, 64, iSig, jSig);
        MatrixND matrixND = new MatrixND(signal);
        MatrixND initMatrix = new MatrixND(signal);
        MatrixND psfMatrix =convolutionFitter.psfMatrix;
        MatrixND result = ConvolutionFitter.iterativeConvolution(matrixND, initMatrix, psfMatrix, 200);
        dumpPSF(result, 0.01);
        int[] imax = findMax(result);
        Assert.assertEquals(1.0, result.getValue(iSig, jSig), 0.06);
        Assert.assertEquals(iSig, imax[0]);
        Assert.assertEquals(jSig, imax[1]);
    }

    @Test
    public void testConvolve2() {
        ConvolutionFitter convolutionFitter = getConvolutionFitter();
        int iSig = 40;
        MatrixND signal = genSignal(convolutionFitter, iSig);
        MatrixND matrixND = new MatrixND(signal);
        MatrixND initMatrix = new MatrixND(signal);
        MatrixND psfMatrix =convolutionFitter.psfMatrix;
        MatrixND result = ConvolutionFitter.iterativeConvolution(matrixND, initMatrix, psfMatrix, 100);
        Assert.assertEquals(1.0, result.getValue(iSig), 0.5);
    }

    @Test
    public void testConvolve3() {
        ConvolutionFitter convolutionFitter = getConvolutionFitter();
        int iSig = 40;
        MatrixND signal = genSignal(convolutionFitter, iSig);
        MatrixND result = convolutionFitter.iterativeConvolutions(signal, 1.0e-6, 50, true);
        Assert.assertEquals(1.0, result.getValue(iSig), 0.01);
    }

}