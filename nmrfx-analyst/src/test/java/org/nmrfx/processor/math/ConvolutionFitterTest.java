package org.nmrfx.processor.math;

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

    private double[] genSignal(ConvolutionFitter convolutionFitter) {
        double[] signal = new double[256];
        signal[40] = 1.0;
        return convolutionFitter.convolutionTest(signal);
    }

    @Test
    public void testConvolve() {
        ConvolutionFitter convolutionFitter = getConvolutionFitter();
        double[] convolved = genSignal(convolutionFitter);
        double max = 0;
        double sum = 0.0;
        int imax = 0;
        for (int i = 0; i < convolved.length; i++) {
            if (convolved[i] > max) {
                max = convolved[i];
                imax = i;
            }
            sum += convolved[i];
        }
        Assert.assertEquals(convolutionFitter.psfMax, max, 1.0e-6);
        Assert.assertEquals(1.0, sum, 1.0e-6);
    }

    @Test
    public void testConvolve2() {
        ConvolutionFitter convolutionFitter = getConvolutionFitter();
        double[] signal = genSignal(convolutionFitter);
        double[] init = new double[signal.length];
        Arrays.fill(init, 1.0);
        System.arraycopy(signal, 0, init, 0, init.length);
        double[] result = ConvolutionFitter.iterativeConvolution(signal, init, convolutionFitter.psf[0], 100);
        Assert.assertEquals(1.0, result[40], 0.5);
    }
    @Test
    public void testConvolve3() {
        ConvolutionFitter convolutionFitter = getConvolutionFitter();
        double[] signal = genSignal(convolutionFitter);
        double[] result = convolutionFitter.iterativeConvolutions(signal, 1.0e-6, 50, true);
        Assert.assertEquals(1.0, result[40], 0.01);
    }

}