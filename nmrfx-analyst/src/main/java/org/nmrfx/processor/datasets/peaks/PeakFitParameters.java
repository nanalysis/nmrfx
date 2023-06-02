package org.nmrfx.processor.datasets.peaks;

import static org.nmrfx.processor.datasets.peaks.PeakFitParameters.ARRAYED_FIT_MODE.SINGLE;
import static org.nmrfx.processor.datasets.peaks.PeakFitParameters.FITJ_MODE.FIT;
import static org.nmrfx.processor.datasets.peaks.PeakFitParameters.FIT_MODE.ALL;

public class PeakFitParameters {
    boolean doFit;
    FIT_MODE fitMode;
    FITJ_MODE fitJMode;
    boolean updatePeaks;
    double multiplier;
    ShapeParameters shapeParameters;
    boolean lsFit;
    int constrainDim;
    ARRAYED_FIT_MODE arrayedFitMode;
    public PeakFitParameters() {
        this(true, ALL, FIT, true, 3.0,
                false, -1, SINGLE);
    }
    public PeakFitParameters(boolean doFit, FIT_MODE fitMode, FITJ_MODE fitJMode, boolean updatePeaks,
                             double multiplier, boolean lsFit, int constrainDim,
                             ARRAYED_FIT_MODE arrayedFitMode) {
        this.doFit = doFit;
        this.fitMode = fitMode;
        this.fitJMode = fitJMode;
        this.updatePeaks = updatePeaks;
        this.multiplier = multiplier;
        this.shapeParameters = new ShapeParameters(false, false, 0.0, 0.0);
        this.lsFit = lsFit;
        this.constrainDim = constrainDim;
        this.arrayedFitMode = arrayedFitMode;
    }

    public PeakFitParameters copy() {
        PeakFitParameters newFitParameters = new PeakFitParameters(this.doFit, this.fitMode, this.fitJMode,
                this.updatePeaks, this.multiplier, this.lsFit, this.constrainDim, this.arrayedFitMode);
        newFitParameters.shapeParameters = this.shapeParameters;
        return newFitParameters;
    }

    public boolean doFit() {
        return doFit;
    }

    public PeakFitParameters doFit(boolean doFit) {
        this.doFit = doFit;
        return this;
    }

    /*
     * @param doFit Currently unused
     * @param fitMode An int value that specifies whether to fit all parameters
     * or just amplitudes
     * @param updatePeaks If true update the peaks with the fitted parameters
     * otherwise return a list of the fit parameters
     * @param multiplier unused?? should multiply width of regions
     * @param fitShape If true fit using the generalized Lorentzian model
     * @param lsFit If true and a lineshape catalog exists in dataset then use
     * the lineshape catalog to fit
     * @param constrainDim If this is greater than or equal to 0 then the
     * specified all positions and widths of the specified dimension will be
     * constrained to be the same value. Useful for fitting column or row of
     * peaks.

     */

    public FIT_MODE fitMode() {
        return fitMode;
    }

    public PeakFitParameters fitMode(FIT_MODE fitMode) {
        this.fitMode = fitMode;
        return this;
    }

    public FITJ_MODE fitJMode() {
        return fitJMode;
    }

    public PeakFitParameters fitJMode(FITJ_MODE fitJMode) {
        this.fitJMode = fitJMode;
        return this;
    }

    public boolean updatePeaks() {
        return updatePeaks;
    }

    public PeakFitParameters updatePeaks(boolean updatePeaks) {
        this.updatePeaks = updatePeaks;
        return this;
    }

    public double multiplier() {
        return multiplier;
    }

    public PeakFitParameters multiplier(double multiplier) {
        this.multiplier = multiplier;
        return this;
    }

    public ShapeParameters shapeParameters() {
        return shapeParameters;
    }

    public PeakFitParameters shapeParameters(ShapeParameters shapeParameters) {
        this.shapeParameters = shapeParameters;
        return this;
    }

    public PeakFitParameters shapeParameters(boolean fitShape, boolean constrainShape,
                                             double directShapeFactor, double indirectShapeFactor) {
        this.shapeParameters = new ShapeParameters(fitShape, constrainShape, directShapeFactor, indirectShapeFactor);
        return this;
    }

    public boolean lsFit() {
        return lsFit;
    }

    public PeakFitParameters lsFit(boolean lsFit) {
        this.lsFit = lsFit;
        return this;
    }

    public int constrainDim() {
        return constrainDim;
    }

    public PeakFitParameters constrainDim(int constrainDim) {
        this.constrainDim = constrainDim;
        return this;
    }

    public ARRAYED_FIT_MODE arrayedFitMode() {
        return arrayedFitMode;
    }

    public PeakFitParameters arrayedFitMode(ARRAYED_FIT_MODE arrayedFitMode) {
        this.arrayedFitMode = arrayedFitMode;
        return this;
    }

    public enum ARRAYED_FIT_MODE {
        SINGLE,
        PLANES,
        EXP;
    }

    public enum FIT_MODE {
        ALL,
        AMPLITUDES,
        LW_AMPLITUDES,
        MAXDEV,
        RMS
    }

    public enum FITJ_MODE {
        FIT,
        JFIT,
        LFIT
    }

    public record ShapeParameters(boolean fitShape, boolean constrainShape,
                                  double directShapeFactor, double indirectShapeFactor) {

    }

}