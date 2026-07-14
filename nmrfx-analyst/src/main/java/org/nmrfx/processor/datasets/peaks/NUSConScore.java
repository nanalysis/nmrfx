package org.nmrfx.processor.datasets.peaks;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.math.BipartiteMatcher;

import java.io.IOException;
import java.util.DoubleSummaryStatistics;
import java.util.List;

/**
 * @author brucejohnson
 */
@PythonAPI("pscript")
public class NUSConScore {

    List<Peak> peaksM;
    List<Peak> peaksR;
    PeakList peakListM;
    PeakList peakListR;
    double[] peakWidths;
    private double accuracy;
    private double linearity;
    private double truePositiveRate;
    private double falsePositiveRate;
    private double valleyToPeak;

    public NUSConScore(List<Peak> peaksM, List<Peak> peaksR) {
        this.peaksM = peaksM;
        this.peaksR = peaksR;
        peakListM = peaksM.get(0).getPeakList();
        peakListR = peaksR.get(0).getPeakList();
    }

    public NUSConScore(PeakList peakListM, PeakList peakListR) {
        this.peakListM = peakListM;
        this.peakListR = peakListR;
        this.peaksM = peakListM.peaks();
        this.peaksR = peakListR.peaks();
    }

    /**
     * @return the accuracy
     */
    public double getAccuracy() {
        return accuracy;
    }

    /**
     * @return the linearity
     */
    public double getLinearity() {
        return linearity;
    }

    /**
     * @return the truePositiveRate
     */
    public double getTruePositiveRate() {
        return truePositiveRate;
    }

    /**
     * @return the falsePositiveRate
     */
    public double getFalsePositiveRate() {
        return falsePositiveRate;
    }

    /**
     * @return the valleyToPeak
     */
    public double getValleyToPeak() {
        return valleyToPeak;
    }

    public void calculate(double dMax) {
        peakWidths = new double[peakListM.getNDim()];
        for (int i = 0; i < peakWidths.length; i++) {
            DoubleSummaryStatistics widthStats = peakListM.widthStatsPPM(i);
            peakWidths[i] = widthStats.getAverage();
        }

        accuracy = normSymHausDorff(peaksM, peaksR, peakWidths, dMax);
        int[] matching = doBPMatch(peaksM, peaksR, peakWidths, dMax);
        linearity = intensityCorrelation(peaksM, peaksR, matching);
        double[] rates = getRates(matching, peaksM.size(), peaksR.size());
        truePositiveRate = rates[0];
        falsePositiveRate = rates[1];
    }

    public void vps(Dataset dataset, Peak peak1, Peak peak2, int iDim) throws IOException {
        valleyToPeak = valleyToPeak(dataset, peak1, peak2, iDim);
    }

    public static double normSymHausDorff(List<Peak> peaksM, List<Peak> peaksR, double[] scale, double dMax) {
        return 1.0 - (symHausDorff(peaksM, peaksR, scale, dMax) / dMax);
    }

    public static double symHausDorff(List<Peak> peaksM, List<Peak> peaksR, double[] scale, double dMax) {
        double mR = hausDorff(peaksM, peaksR, scale, dMax);
        double rM = hausDorff(peaksR, peaksM, scale, dMax);
        return (mR + rM) / 2.0;
    }

    public static double hausDorff(List<Peak> peaksM, List<Peak> peaksR, double[] scale, double dMax) {
        double sumSq = 0.0;
        for (Peak peakM : peaksM) {
            double disMin = Double.MAX_VALUE;
            for (Peak peakR : peaksR) {
                double dis = Math.min(peakM.distance(peakR, scale), dMax);
                disMin = Math.min(dis, disMin);
            }
            sumSq += disMin * disMin;
        }
        double score = Math.sqrt(sumSq / peaksM.size());
        return score;
    }

    public static double intensityCorrelation(List<Peak> peaksM,
                                              List<Peak> peaksR, int[] matching) {
        int nM = peaksM.size();
        double[] intensitiesM = new double[nM];
        double[] intensitiesR = new double[nM];
        for (int i = 0; i < nM; i++) {
            Peak peakM = peaksM.get(i);
            intensitiesM[i] = peakM.getIntensity();
            int iMatch = matching[i];
            if (iMatch != -1) {
                Peak peakR = peaksR.get(iMatch);
                intensitiesR[i] = peakR.getIntensity();
            } else {
                intensitiesR[i] = 0.0;
            }
        }
        PearsonsCorrelation pCorr = new PearsonsCorrelation();
        double corr = pCorr.correlation(intensitiesM, intensitiesR);
        return corr;
    }

    public static int[] doBPMatch(List<Peak> peaksM,
                                  List<Peak> peaksR, double[] scale, double dMax) {
        int nM = peaksM.size();
        int nR = peaksR.size();
        int nPeaks = nM + nR;
        BipartiteMatcher bpMatch = new BipartiteMatcher();
        bpMatch.reset(nPeaks, true);
        for (int iM = 0; iM < nM; iM++) {
            bpMatch.setWeight(iM, nR + iM, -1.0);
        }
        for (int iR = 0; iR < nR; iR++) {
            bpMatch.setWeight(nM + iR, iR, -1.0);
        }
        for (int iM = 0; iM < nM; iM++) {
            Peak peakM = peaksM.get(iM);
            for (int iR = 0; iR < nR; iR++) {
                Peak peakR = peaksR.get(iR);
                double weight = Double.NEGATIVE_INFINITY;
                double distance = peakM.distance(peakR, scale);
                if (distance < dMax) {
                    weight = Math.exp(-distance * distance);
                }
                bpMatch.setWeight(iM, iR, weight);
            }
        }
        int[] matching = bpMatch.getMatching();
        int[] result = new int[nM];
        for (int i = 0; i < nM; i++) {
            int iMatch = matching[i];
            if (iMatch < nR) {
                result[i] = iMatch;
            } else {
                result[i] = -1;
            }
        }
        return result;
    }

    public static double[] getRates(int[] matching, int nM, int nR) {
        int nMatches = 0;
        for (int i = 0; i < nM; i++) {
            if (matching[i] != -1) {
                nMatches++;
            }

        }
        double[] rates = {(double) nMatches / nM, (double) nMatches / nR};
        return rates;
    }

    public static double valleyToPeak(Dataset dataset, Peak peakM1, Peak peakM2, int iDim) throws IOException {
        int nDim = dataset.getNDim();
        int[] pt = new int[nDim];
        int[] dim = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            dim[i] = i;
        }
        int first = 0;
        int last = 0;
        double int1 = peakM1.getIntensity();
        double int2 = peakM2.getIntensity();
        for (int i = 0; i < nDim; i++) {
            double p1 = peakM1.getPeakDim(i).getChemShiftValue();
            double p2 = peakM2.getPeakDim(i).getChemShiftValue();
            if (i == iDim) {
                first = dataset.ppmToPoint(iDim, p1);
                last = dataset.ppmToPoint(iDim, p2);
                if (last < first) {
                    int hold = first;
                    first = last;
                    last = hold;
                }
            } else {
                pt[i] = dataset.ppmToPoint(i, p1);
            }
        }
        double min = Double.MAX_VALUE;
        for (int i = first; i <= last; i++) {
            pt[iDim] = i;
            double value = dataset.readPointRaw(pt, dim);
            min = Math.min(value, min);
        }
        double vps = 1.0 - ((2.0 * min) / (Math.abs(int1) + Math.abs(int2)));
        return vps;
    }
}
