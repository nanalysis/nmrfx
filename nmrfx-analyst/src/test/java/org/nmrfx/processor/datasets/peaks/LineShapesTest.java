package org.nmrfx.processor.datasets.peaks;

import org.junit.Assert;
import org.junit.Test;

public class LineShapesTest {

    @Test
    public void calculateLorentzianCenter() {
        double y = LineShapes.LORENTZIAN.calculate(0.0, 1.0, 0.0, 1.0);
        Assert.assertEquals(1.0, y, 1.0e-9);
    }

    @Test
    public void calculateLorentzianCenterHeight() {
        double y = LineShapes.LORENTZIAN.calculate(0.0, 2.0, 0.0, 1.0);
        Assert.assertEquals(2.0, y, 1.0e-9);
    }

    @Test
    public void calculateLorentzianHalf() {
        double y = LineShapes.LORENTZIAN.calculate(0.5, 1.0, 0.0, 1.0);
        Assert.assertEquals(0.5, y, 1.0e-9);
    }

    @Test
    public void calculateLorentzianHalfOffset() {
        double y = LineShapes.LORENTZIAN.calculate(2.0, 1.0, 1.0, 2.0);
        Assert.assertEquals(0.5, y, 1.0e-9);
    }

    @Test
    public void calculateGaussianCenter() {
        double y = LineShapes.GAUSSIAN.calculate(0.0, 1.0, 0.0, 1.0);
        Assert.assertEquals(1.0, y, 1.0e-9);
    }

    @Test
    public void calculateGaussianCenterHeight() {
        double y = LineShapes.GAUSSIAN.calculate(0.0, 2.0, 0.0, 1.0);
        Assert.assertEquals(2.0, y, 1.0e-9);
    }

    @Test
    public void calculateGaussianHalf() {
        double y = LineShapes.GAUSSIAN.calculate(0.5, 1.0, 0.0, 1.0);
        Assert.assertEquals(0.5, y, 1.0e-9);
    }

    @Test
    public void calculateGaussianHalfOffset() {
        double y = LineShapes.GAUSSIAN.calculate(2.0, 1.0, 1.0, 2.0);
        Assert.assertEquals(0.5, y, 1.0e-9);
    }

    @Test
    public void calculateGLorentzianCenter() {
        double y = LineShapes.G_LORENTZIAN.calculate(0.0, 1.0, 0.0, 1.0, 0.0);
        Assert.assertEquals(1.0, y, 1.0e-9);
    }

    @Test
    public void calculateGLorentzianCenterHeight() {
        double y = LineShapes.G_LORENTZIAN.calculate(0.0, 2.0, 0.0, 1.0, 0.0);
        Assert.assertEquals(2.0, y, 1.0e-9);
    }

    @Test
    public void calculateGLorentzianHalf() {
        double y = LineShapes.G_LORENTZIAN.calculate(0.5, 1.0, 0.0, 1.0, 0.0);
        Assert.assertEquals(0.5, y, 1.0e-9);
    }

    @Test
    public void calculateGLorentzianHalfOffset() {
        double y = LineShapes.G_LORENTZIAN.calculate(2.0, 1.0, 1.0, 2.0, 0.0);
        Assert.assertEquals(0.5, y, 1.0e-9);
    }

    @Test
    public void calculateGLorentzianCenter1() {
        double y = LineShapes.G_LORENTZIAN.calculate(0.0, 1.0, 0.0, 1.0, 1.0);
        Assert.assertEquals(1.0, y, 1.0e-9);
    }

    @Test
    public void calculateGLorentzianCenterHeight1() {
        double y = LineShapes.G_LORENTZIAN.calculate(0.0, 2.0, 0.0, 1.0, 1.0);
        Assert.assertEquals(2.0, y, 1.0e-9);
    }

    @Test
    public void calculateGLorentzianHalf1() {
        double y = LineShapes.G_LORENTZIAN.calculate(0.5, 1.0, 0.0, 1.0, 1.0);
        Assert.assertEquals(0.5, y, 1.0e-9);
    }

    @Test
    public void calculateGLorentzianHalfOffset1() {
        double y = LineShapes.G_LORENTZIAN.calculate(2.0, 1.0, 1.0, 2.0, 1.0);
        Assert.assertEquals(0.5, y, 1.0e-9);
    }

    @Test
    public void calculateGLorentzianFullOffset0() {
        double y = LineShapes.G_LORENTZIAN.calculate(1.0, 1.0, 0.0, 1.0, 0.0);
        double yL = LineShapes.LORENTZIAN.calculate(1.0, 1.0, 0.0, 1.0);
        Assert.assertEquals(yL, y, 1.0e-9);
    }

    @Test
    public void calculateGLorentzianTwoOffset1() {
        double y = LineShapes.G_LORENTZIAN.calculate(2.0, 1.0, 0.0, 1.0, 1.5);
        double yG = LineShapes.GAUSSIAN.calculate(2.0, 1.0, 0.0, 1.0);
        Assert.assertEquals(yG, y, 0.025);
    }

    @Test
    public void calculateGLorentzianQuarterOffset1() {
        double y = LineShapes.G_LORENTZIAN.calculate(0.25, 1.0, 0.0, 1.0, 1.5);
        double yG = LineShapes.GAUSSIAN.calculate(0.25, 1.0, 0.0, 1.0);
        Assert.assertEquals(yG, y, 0.05);
    }

    @Test
    public void calculatePseudoVoigtL() {
        double y = LineShapes.PSEUDOVOIGT.calculate(0.5, 1.0, 0.0, 1.0, 1.0);
        double yL = LineShapes.LORENTZIAN.calculate(0.5, 1.0, 0.0, 1.0);
        Assert.assertEquals(yL, y, 1.0e-9);
    }

    @Test
    public void calculatePseudoVoigtG() {
        double y = LineShapes.PSEUDOVOIGT.calculate(0.5, 1.0, 0.0, 1.0, 0.0);
        double yG = LineShapes.GAUSSIAN.calculate(0.5, 1.0, 0.0, 1.0);
        Assert.assertEquals(yG, y, 1.0e-9);
    }

    @Test
    public void calculateFullPseudoVoigtL() {
        double y = LineShapes.PSEUDOVOIGT.calculate(1.0, 1.0, 0.0, 1.0, 1.0);
        double yL = LineShapes.LORENTZIAN.calculate(1.0, 1.0, 0.0, 1.0);
        Assert.assertEquals(yL, y, 1.0e-9);
    }

    @Test
    public void calculateFullPseudoVoigtG() {
        double y = LineShapes.PSEUDOVOIGT.calculate(1.0, 1.0, 0.0, 1.0, 0.0);
        double yG = LineShapes.GAUSSIAN.calculate(1.0, 1.0, 0.0, 1.0);
        Assert.assertEquals(yG, y, 1.0e-9);
    }
}