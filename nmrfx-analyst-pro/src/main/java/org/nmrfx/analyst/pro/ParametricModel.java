package org.nmrfx.analyst.pro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;

import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;
import org.nmrfx.math.VecException;
import org.nmrfx.processor.math.AmplitudeFitResult;
import org.nmrfx.processor.math.FDSignalOpt;
import org.nmrfx.processor.math.Signal;
import org.nmrfx.processor.math.Vec;
import static org.nmrfx.processor.math.Vec.lShape;
import org.nmrfx.processor.math.VecUtil;
import static org.nmrfx.processor.math.VecUtil.nnlsFit;
import org.nmrfx.processor.optimization.NNLSMat;

/**
 *
 * @author brucejohnson
 */
public class ParametricModel {

    List<Signal> signals = new ArrayList<>();
    Vec vec;

    public ParametricModel(Vec vec) {
        this.vec = vec;
    }

    public List<Signal> getSignals() {
        return signals;
    }

    public void genSimVec(Vec vecTarget) {
        genSimVec(vecTarget, signals);
    }

    public void genSimVec(Vec vecTarget, List<Signal> targetSignals) {
        vec.copy(vecTarget);
        vecTarget.fillVec(signals, false);
    }

    public void findSignals(int start, int end, int winSize,
            double threshold) throws VecException {

        //preserve using cvec or ivec/rvec
        int size = vec.getSize();
        if ((winSize < 0) || (winSize > size)) {
            throw new VecException("hsvd: invalid winSize");
        }

        if (start < 0) {
            start = 0;
        }

        if (start > (size - winSize)) {
            start = size - winSize;
        }

        if (end < ((start + winSize) - 1)) {
            end = (start + winSize) - 1;
        }

        if (end >= size) {
            end = size - 1;
        }

        int nPoints = end - start + 1;
        int nRegions = (end - start + 1) / winSize;
        int nSteps = (nRegions * 2) - 1;

        int pad = winSize * 3 / 2;
        pad = 0;
        int winTotal = winSize + 2 * pad;
        int ftSize = winTotal / 2;
        double winScale = ((double) (ftSize)) / nPoints;
        System.out.println("hsvd " + nRegions + " " + nSteps + " " + winSize + " " + winScale + " " + ftSize);
        double[] x1 = new double[winTotal];
        int leftLim = winSize / 4;
        int rightLim = 3 * winSize / 4;
        signals.clear();
        for (int i = 0; i < nSteps; i++) {
            int k = 0;
            for (int j = 0; j < pad; j++) {
                x1[k++] = 0.0;
            }
            for (int j = 0; j < winSize; j++) {
                x1[k++] = vec.getReal(start + j);
            }
            for (int j = 0; j < pad; j++) {
                x1[k++] = 0.0;
            }
            // System.out.println("iter " + i + " start " + start);
            Complex[] ftResult = VecUtil.hift(x1, winTotal, 0.5);

            int nCoef = ftSize / 4;
            Complex[] fd = VecUtil.tlsFreq(ftResult, ftSize, nCoef, threshold);
            //  System.out.println("nfd " + fd.length);
            List<Signal> regionSignals = refineSignals(x1, fd, winSize, threshold);
            //  System.out.println("nsg " + regionSignals.size());
            for (Signal signal : regionSignals) {
                if ((nSteps == 1) || (i == 0) || (i == (nSteps - 1)) || ((signal.frequency >= leftLim) && (signal.frequency < rightLim))) {
                    signal.frequency += start;
                    signal.frequency = vec.pointToPPM(signal.frequency);
                    signal.decay = signal.decay / vec.getSize() * vec.getSW();
                    signals.add(signal);
                }
            }

            start += (winSize / 2);
        }
    }

    public static List<Signal> refineSignals(final double[] x, final Complex[] fd,
            final int winSize, final double threshold) {
        boolean uniformWidth = true;
        double[] f = new double[fd.length];
        double[] d = new double[fd.length];
        for (int iSig = 0; iSig < fd.length; iSig++) {
            Complex zFD = fd[iSig];
            double fR = -Math.atan2(zFD.getImaginary(), zFD.getReal());
            double fPoints = (winSize * (Math.PI - fR)) / (2 * Math.PI);
            f[iSig] = fPoints;
            d[iSig] = -1.0 * Math.log(zFD.abs()) * winSize / Math.PI;
        }

        RealMatrix AR = new Array2DRowRealMatrix(fillMatrix(f, d, winSize));
        RealMatrix BR = new Array2DRowRealMatrix(AR.getRowDimension(), 1);
        for (int i = 0; i < winSize; i++) {
            BR.setEntry(i, 0, x[i]);
        }
        int nMax = AR.getColumnDimension();
        RealMatrix redAR = AR.copy();
        AmplitudeFitResult afR = nnlsFit(redAR, BR.copy());
        //  System.out.println("fit max " + afR.getMaxValue() + " indx " + afR.getMaxIndex() + " thresh " + threshold);
        int[] useColumns = new int[nMax];
        int nUse = nMax;
        // System.out.println("fit all");
        double sumAmp = 0.0;
        double sumW = 0.0;
        for (int i = 0; i < nMax; i++) {
            useColumns[i] = i;
            double height = afR.getCoefs()[i] * 2.0 / Math.PI / d[i];
            // System.out.println(String.format("%4d %8.4f %8.4f %8.4f %8.4f %8.4f", i, afR.getCoefs()[i], height, threshold, f[i], d[i]));
// fixme 2 should be parameter to set minimum width
            sumAmp += afR.getCoefs()[i];
            sumW += d[i] * afR.getCoefs()[i];
            if ((height < 1.0e-16) || (d[i] < 2)) {
//			    useColumns[i] = -1;
                //                           nUse--;
            }
        }
        double lwAvg = sumW / sumAmp;
        //  System.out.println("lwAvg " + lwAvg);
        List<Signal> signals = new ArrayList<>();
        if (nUse == 0) {
            return signals;
        }
        double aicMin = Double.MAX_VALUE;
        double minWidth = 0.0;
        AmplitudeFitResult afRMin = afR;
        int[] columnsMin = useColumns;
        double lineWidth = 0.0;

        SearchInterval searchInterval = new SearchInterval(0.1, 100.0);
        MaxEval maxEval = new MaxEval(100);

        while (nUse > 0) {
            if (uniformWidth) {
                Vec.OptimizeLineWidth olW = new Vec.OptimizeLineWidth(x, fd, columnsMin);
                UnivariateObjectiveFunction fOpt = new UnivariateObjectiveFunction(olW);
                BrentOptimizer brent = new BrentOptimizer(0.01, 0.01);
                try {
                    UnivariatePointValuePair optValue = brent.optimize(fOpt, GoalType.MINIMIZE, searchInterval, maxEval);
                    lineWidth = optValue.getPoint();
                } catch (TooManyEvaluationsException | MathIllegalArgumentException meE) {
                }
                //  System.out.println("fit lw " + lineWidth);
            }
            afR = fitAmplitudes(x, fd, useColumns, x.length, uniformWidth, lineWidth);

            //  System.out.println("fit max " + afR.getMaxValue() + " indx " + afR.getMaxIndex() + " thresh " + threshold);
            if ((true) || (afR.getAic() < aicMin)) {
                aicMin = afR.getAic();
                afRMin = afR;
                columnsMin = useColumns.clone();
                minWidth = lineWidth;
            }
            if (afR.getMaxValue() > threshold) {
                break;
            }
            int k = 0;
            double min = Double.MAX_VALUE;
            int jMin = 0;
            for (int j = 0; j < nMax; j++) {
                if (useColumns[j] != -1) {
                    if (afR.getCoefs()[k] < min) {
                        min = afR.getCoefs()[k];
                        jMin = j;
                    }
                    k++;
                }
            }
            //    System.out.println(String.format("%4d %4d %8.4f %8.4f %8.4f %8.4f %8.4f %4d %8.4f", afR.getK(), jMin, afR.getAic(), min, afR.getRss(), f[jMin], afR.getCoefs()[kMin], afR.getMaxIndex(), afR.getMaxValue()));
            useColumns[jMin] = -1;
            nUse--;
        }
        //    System.out.println(afRMin.getAic() + " " + afRMin.getK());
        int k = 0;
        for (int j = 0; j < nMax; j++) {
            if (columnsMin[j] != -1) {
                double height = afRMin.getCoefs()[k] * 2.0 / Math.PI / d[columnsMin[j]];
                //   System.out.println(" j " + j + " k " + k + " h " + height + " t  " + threshold);
                if (height > (threshold / 2.0)) {
                    if (uniformWidth) {
                        lineWidth = minWidth;
                    } else {
                        lineWidth = d[columnsMin[j]];
                    }
                    Signal signal = new Signal(afRMin.getCoefs()[k], 0.0, f[columnsMin[j]], lineWidth);
                    signals.add(signal);
                    //   System.out.println(signal.toString());
                }
                k++;
            }
        }
        return signals;
    }

    public static AmplitudeFitResult fitAmplitudes(final double[] x, final Complex[] fd, final int[] useColumns, final int winSize, final boolean uniformWidth, final double lineWidth) {
        int nCols = 0;
        for (int j = 0; j < fd.length; j++) {
            if (useColumns[j] != -1) {
                nCols++;
            }
        }
        double[] f = new double[nCols];
        double[] d = new double[nCols];
        int iSig = 0;
        for (int j = 0; j < fd.length; j++) {
            if (useColumns[j] != -1) {
                Complex zFD = fd[j];
                double fR = -Math.atan2(zFD.getImaginary(), zFD.getReal());
                double fPoints = (winSize * (Math.PI - fR)) / (2 * Math.PI);
                f[iSig] = fPoints;
                if (uniformWidth) {
                    d[iSig] = lineWidth;
                } else {
                    d[iSig] = -1.0 * Math.log(zFD.abs()) * winSize / Math.PI;
                }
                iSig++;
            }
        }

        RealMatrix AR = new Array2DRowRealMatrix(fillMatrix(f, d, winSize));
        RealMatrix BR = new Array2DRowRealMatrix(AR.getRowDimension(), 1);
        for (int i = 0; i < winSize; i++) {
            BR.setEntry(i, 0, x[i]);
        }
        RealMatrix redAR = AR.copy();
        AmplitudeFitResult afR = nnlsFit(redAR, BR.copy());
        //  System.out.println("nCols " + nCols + " rss " + afR.getRss() + " fit max " + afR.getMaxValue() + " indx " + afR.getMaxIndex() + " lw " + lineWidth);
        return afR;
    }

    static double[][] fillMatrix(final double[] f, final double d[], final int nRows) {
        int nCols = f.length;
        double[][] A = new double[nRows][nCols];
        for (int iSig = 0; iSig < nCols; iSig++) {
            for (int j = 0; j < nRows; j++) {
                double yTemp = lShape(j, d[iSig], f[iSig]);
                A[j][iSig] = yTemp;
            }
        }
        return A;
    }

    static double[][] fillMatrix(final ArrayList<Signal> signals, final int nRows) {
        int nCols = signals.size();
        double[][] A = new double[nRows][nCols];
        for (int iSig = 0; iSig < nCols; iSig++) {
            Signal signal = signals.get(iSig);
            for (int j = 0; j < nRows; j++) {
                double yTemp = lShape(j, signal.decay, signal.frequency);
                A[j][iSig] = yTemp;
            }
        }
        return A;
    }

    public void findSignals(int winSize, double threshold, double minWidth,
            List<Integer> nonBaseRegions, double filter, boolean constrainWidth, int optStepMultiplier, double stopRadius) {
        int size = vec.getSize();
        if ((winSize < 0) || (winSize > size)) {
            throw new VecException("hsvd: invalid winSize");
        }
        signals.clear();
        if ((nonBaseRegions == null) || nonBaseRegions.isEmpty()) {
            nonBaseRegions = new ArrayList<>();
            nonBaseRegions.add(100);
            nonBaseRegions.add(size - 100);
        }
        for (int iRegion = 0; iRegion < nonBaseRegions.size(); iRegion += 2) {
            int start = nonBaseRegions.get(iRegion);
            int end = nonBaseRegions.get(iRegion + 1);
            if (start < 0) {
                start = 0;
            }

            if (start > (size - winSize)) {
                start = size - winSize;
            }

            /*        if (end < ((start + winSize) - 1)) {
             end = (start + winSize) - 1;
             }
             */
            if (end >= size) {
                end = size - 1;
            }
            int leftPad = 0;
            int rightPad = 0;
            int nPoints = end - start + 1;
            int usePoints = winSize;
            if (nPoints < winSize) {
                leftPad = (winSize - nPoints) / 2;
                rightPad = winSize - nPoints - leftPad;
                usePoints = nPoints;
            }
            int nRegions = (int) Math.ceil((end - start + 1.0) / winSize);
            int nSteps = (nRegions * 2) - 1;
            int ftSize = winSize / 2;
            double[] x1 = new double[winSize];
            int leftLim = start;
            int targetLim = start + 3 * winSize / 4;
            int rightLim = targetLim;
            int limRange = winSize / 32;
            for (int i = 0; i < nSteps; i++) {
                int padStart = start - leftPad;
                int k = 0;
                for (int j = 0; j < leftPad; j++) {
                    x1[k++] = 0.0;
                }
                if ((start + winSize - 1) > size) {
                    break;
                }
                double rangeMin = Double.MAX_VALUE;
                for (int j = 0; j < usePoints; j++) {
                    int jOrig = j + start;
                    x1[k] = vec.getReal(jOrig);
                    if (Math.abs(jOrig - targetLim) < limRange) {
                        if (x1[k] < rangeMin) {
                            rangeMin = x1[k];
                            rightLim = targetLim;
                        }
                    }
                    k++;
                }
                if (i == (nSteps - 1)) {
                    rightLim = start + winSize;
                }
                if (rightLim >= end) {
                    rightLim = end - 1;
                }
                for (int j = 0; j < rightPad; j++) {
                    x1[k++] = 0.0;
                }
                double max = Double.NEGATIVE_INFINITY;
                int imax = 0;
                for (int j = 0; j < winSize; j++) {
                    if (x1[j] > max) {
                        max = x1[j];
                        imax = j + padStart;
                    }
                }
                Complex[] ftResult = VecUtil.hift(x1, winSize, 0.5);

                Complex[] ftvec = new Complex[ftSize];
                System.arraycopy(ftResult, 0, ftvec, 0, ftSize);
                int nCoef = ftSize / 4;
                Complex[] fd = VecUtil.tlsFreq(ftResult, ftSize, nCoef, threshold);
                ArrayList<Signal> regionSignals;
                //int tlsStart = 1;
                //regionSignals = svdTDAmplitudes(ftvec, tlsStart - 1, ftSize - tlsStart, fd, nCoef, winSize, threshold, minWidth);
                regionSignals = freqDomainAmplitudes(x1, fd, winSize, threshold, minWidth, padStart);
                double startValue = 0.0;
                double finalValue = 0.0;
                double deltaValue = 0.0;
                int nEvaluations = 0;
                if (regionSignals.size() > 0) {
                    Collections.sort(regionSignals);
                    FDSignalOpt sigOpt = new FDSignalOpt(x1, x1.length, regionSignals, constrainWidth, leftLim - padStart, (padStart + winSize - 1 - rightLim));
//                    dumpSignals(regionSignals, padStart);
                    if (optStepMultiplier > 0) {
                        regionSignals = sigOpt.refineBOBYQA(regionSignals.size() * 3 * optStepMultiplier, stopRadius);
                        //dumpSignals(regionSignals, padStart);
                        startValue = sigOpt.getStartValue();
                        nEvaluations = sigOpt.getnEvaluations();
                    }
                    if (filter > 0.0) {
//                        System.out.println("size2 " + regionSignals.size());

                        regionSignals = filterSignals(regionSignals, filter, threshold);
//                        System.out.println("size3 " + regionSignals.size());
                    }
                    //dumpSignals(regionSignals, padStart);
                    if (optStepMultiplier > 0) {
                        if (regionSignals.size() > 0) {
                            sigOpt = new FDSignalOpt(x1, x1.length, regionSignals, constrainWidth, leftLim - padStart, (padStart + winSize - 1 - rightLim));
                            regionSignals = sigOpt.refineBOBYQA(regionSignals.size() * 3 * optStepMultiplier, stopRadius);
                            sigOpt = new FDSignalOpt(x1, x1.length, regionSignals, constrainWidth, leftLim - padStart, (padStart + winSize - 1 - rightLim));
                            regionSignals = sigOpt.refineBOBYQA(regionSignals.size() * 3 * optStepMultiplier, stopRadius);
                        }
                        //dumpSignals(regionSignals, padStart);

                        finalValue = sigOpt.getFinalValue();
                        deltaValue = sigOpt.getFinalDelta();
                        nEvaluations += sigOpt.getnEvaluations();
                    }
//                    dumpSignals(regionSignals, padStart);
                }
                double lastPos = -9999.0;
                if (signals.size() > 0) {
                    lastPos = signals.get(signals.size() - 1).frequency;
                }
                for (Signal signal : regionSignals) {
                    signal.frequency += padStart;
                    if (Math.abs(signal.frequency - lastPos) < 1.0) {
                        continue;
                    }
                    double height = signal.amplitude / Math.PI / signal.decay * 2.0;
//                    System.out.printf("%9.5f %9.5f %9.5f\n", signal.frequency, height, threshold);
                    if ((height > threshold) && (signal.frequency >= leftLim) && (signal.frequency < rightLim)) {
                        double ptFreq = signal.frequency;
                        signal.frequency = vec.pointToPPM(signal.frequency);
                        double ptFreq2 = vec.refToPtD(signal.frequency);
                        signal.decay = signal.decay / vec.getSize() * vec.getSW();
                        signals.add(signal);
                    }
                }
                System.out.printf("%3d %3d %4d %5d %5d %5d %5d %5d %4d %4d %4d %5d %9.4f %4d %4d %9.5f %9.5f %9.5f %4d\n", iRegion, nPoints, i, start, leftLim, rightLim, (start + winSize - 1), end, winSize, leftPad, rightPad, imax, max, nCoef, regionSignals.size(), startValue, finalValue, deltaValue, nEvaluations);
                start += (winSize / 2);
                leftLim = rightLim;
                targetLim = start + 3 * winSize / 4;
            }
        }
//        for (Signal sig : allSignals) {
//            System.out.printf("%9.5f %9.5f %9.5f\n", sig.frequency, sig.amplitude, sig.decay);
//        }
    }

    public static ArrayList<Signal> freqDomainAmplitudes(final double[] x, final Complex[] fd, final int winSize,
            final double threshold, final double minWidth, final int start) {
        double[] f = new double[fd.length];
        double[] d = new double[fd.length];
        double dScale = 0.80;
        ArrayList<Signal> signalsPre = new ArrayList<>();
        for (int iSig = 0; iSig < fd.length; iSig++) {
            Complex zFD = fd[iSig];
            double fR = -Math.atan2(zFD.getImaginary(), zFD.getReal());
            double fPoints = ((winSize - 1) * (Math.PI - fR)) / (2 * Math.PI);
            f[iSig] = fPoints;
            d[iSig] = -1.0 * Math.log(zFD.abs()) * (winSize - 1) / Math.PI * dScale;
            signalsPre.add(new Signal(1.0, 0.0, f[iSig], d[iSig]));
        }
        boolean merge = false;
        ArrayList<Signal> signalsPost;
        if (merge) {
            // merge signals with nearly the same frequency
            Collections.sort(signalsPre);
            signalsPost = new ArrayList<>();
            Signal lastSignal = signalsPre.get(0);
            signalsPost.add(lastSignal);
            for (int iSig = 1; iSig < signalsPre.size(); iSig++) {
                Signal iSignal = signalsPre.get(iSig);
                double avgWidth = (iSignal.decay + lastSignal.decay) / 2.0;
                double maxDelta = avgWidth / 4.0;
                if (Math.abs(iSignal.frequency - lastSignal.frequency) < maxDelta) {
                    lastSignal.frequency = (iSignal.frequency + lastSignal.frequency) / 2.0;
                } else {
                    signalsPost.add(iSignal);
                    lastSignal = iSignal;
                }
            }
        } else {
            signalsPost = signalsPre;
        }

        RealMatrix AR = new Array2DRowRealMatrix(fillMatrix(signalsPost, winSize));
        RealMatrix BR = new Array2DRowRealMatrix(AR.getRowDimension(), 1);
        for (int i = 0; i < winSize; i++) {
            BR.setEntry(i, 0, x[i]);
        }
        int nMax = AR.getColumnDimension();

        NNLSMat nnlsMat = new NNLSMat(AR, BR);
        double[] XR = nnlsMat.getX();
        ArrayList<Signal> signals = new ArrayList<>();
        for (int i = 0; i < nMax; i++) {
            Signal iSignal = signalsPost.get(i);
            double height = XR[i] / Math.PI / d[i] * 2.0;
            double amplitude = XR[i];
            //System.out.println(String.format("%4d %8.4f %8.4f %8.4f %8.4f %8.4f", i, amplitude, height, threshold, f[i] + start, d[i]));
            if ((height > threshold) && (iSignal.decay > minWidth)) {
                iSignal.amplitude = amplitude;
                signals.add(iSignal);
            }
        }
        //  System.out.printf("pre %d post %d final %d\n", signalsPre.size(), signalsPost.size(), signals.size());
        return signals;
    }

    public static ArrayList<Signal> filterSignals(ArrayList<Signal> signalsPre, double filter, double threshold) {
        // merge signals with nearly the same frequency
        Collections.sort(signalsPre);
        for (int iSig = signalsPre.size() - 1; iSig >= 0; iSig--) {
            Signal signal = signalsPre.get(iSig);
            double height = signal.amplitude / Math.PI / signal.decay * 2.0;
            //  System.out.printf("%9.5f %9.5f %9.5f %9.5f %9.5f\n", signal.amplitude, signal.frequency, signal.decay, height, threshold);
            if ((height < threshold)) {
                signalsPre.remove(iSig);
            }
        }
        while (signalsPre.size() > 1) {
            Signal lastSignal = signalsPre.get(0);
            int iMin = -1;
            double minDelta = Double.MAX_VALUE;
            double bestPos = 0.0;
            double newAmp = 0.0;
            int iDelta = 0;
//            for (int iSig = 0; iSig < signalsPre.size(); iSig++) {
//                Signal iSignal = signalsPre.get(iSig);
//                System.out.printf("%9.5f %9.5f %9.5f\n", iSignal.amplitude, iSignal.frequency, iSignal.decay);
//            }
            for (int iSig = 1; iSig < signalsPre.size(); iSig++) {
                Signal iSignal = signalsPre.get(iSig);
                double avgWidth = (iSignal.decay + lastSignal.decay) / 2.0;
                boolean lastMax = lastSignal.amplitude > iSignal.amplitude;
                double newPos = (iSignal.frequency * iSignal.amplitude + lastSignal.frequency * lastSignal.amplitude)
                        / (iSignal.amplitude + lastSignal.amplitude);
                double delta;
                double adelta = Math.abs(iSignal.frequency - lastSignal.frequency);
                if (lastMax) {
                    delta = Math.abs(newPos - lastSignal.frequency);
                } else {
                    delta = Math.abs(newPos - iSignal.frequency);

                }
                if ((adelta < avgWidth * filter / 2.0) && (delta < avgWidth * filter) && (delta < minDelta)) {
                    iMin = iSig;
                    minDelta = delta;
                    bestPos = newPos;
                    newAmp = iSignal.amplitude + lastSignal.amplitude;
//                    System.out.printf("%d %9.5f %9.5f %9.5f %9.5f %9.5f %9.5f\n", iSig, iSignal.frequency, lastSignal.frequency, newPos, delta, avgWidth, newAmp);
                    if (lastMax) {
                        iDelta = 0;
                    } else {
                        iDelta = -1;
                    }
                }
                lastSignal = iSignal;
            }
            if (iMin > 0) {
                signalsPre.remove(iMin + iDelta);
                signalsPre.get(iMin - 1).amplitude = newAmp;
                signalsPre.get(iMin - 1).frequency = bestPos;
            } else {
                break;
            }
        }
        return signalsPre;

    }

    static void dumpSignals(ArrayList<Signal> signals, int start) {
        for (Signal signal : signals) {
            System.out.printf("%9.5f %9.5f %9.5f\n", signal.amplitude, signal.frequency + start, signal.decay);

        }
    }

}
