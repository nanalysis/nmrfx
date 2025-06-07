package org.nmrfx.processor.math;

import org.apache.commons.math3.util.MultidimensionalCounter;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

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

    private void dumpPSF(ConvolutionFitter convolutionFitter) {
        MatrixND psfMatrix = convolutionFitter.psfMatrix;
        MultidimensionalCounter mCounter = new MultidimensionalCounter(psfMatrix.getSizes());
        var iterator = mCounter.iterator();
        double sum = 0.0;
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            double value = psfMatrix.getValue(counts);
            sum += value;
            if (value > 0.001) {
                for (int i = 0; i < counts.length; i++) {
                    System.out.print(counts[i] + " ");
                }
                System.out.println(value);
            }
        }
        System.out.println("sum " + sum);
    }
    private MatrixND genSignal(ConvolutionFitter convolutionFitter) {
        double[] signal = new double[256];
        signal[40] = 1.0;
        return convolutionFitter.convolutionTest(signal);
    }

    private MatrixND genSignal2D(ConvolutionFitter convolutionFitter) {
        double[][] signal = new double[256][256];
        signal[40][40] = 1.0;
        return convolutionFitter.convolutionTest2D(signal);
    }

    @Test
    public void testConvolve() {
        ConvolutionFitter convolutionFitter = getConvolutionFitter();
        MatrixND convolved = genSignal(convolutionFitter);
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
    }

    @Test
    public void testConvolve2D() {
        ConvolutionFitter convolutionFitter = getConvolutionFitter2D();
        MatrixND convolved = genSignal2D(convolutionFitter);
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
        Assert.assertEquals(1.0, sum, 1.0e-6);
    }

    @Test
    public void testConvolve2() {
        ConvolutionFitter convolutionFitter = getConvolutionFitter();
        MatrixND signal = genSignal(convolutionFitter);
        MatrixND matrixND = new MatrixND(signal);
        MatrixND initMatrix = new MatrixND(signal);
        MatrixND psfMatrix =convolutionFitter.psfMatrix;
        MatrixND result = ConvolutionFitter.iterativeConvolution(matrixND, initMatrix, psfMatrix, 100);
        Assert.assertEquals(1.0, result.getValue(40), 0.5);
    }

    @Test
    public void testConvolve3() {
        ConvolutionFitter convolutionFitter = getConvolutionFitter();
        MatrixND signal = genSignal(convolutionFitter);
        MatrixND result = convolutionFitter.iterativeConvolutions(signal, 1.0e-6, 50, true);
        Assert.assertEquals(1.0, result.getValue(40), 0.01);
    }

}