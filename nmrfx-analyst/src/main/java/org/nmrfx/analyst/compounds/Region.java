/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.compounds;

import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author brucejohnson
 */
public class Region {

    private static final Logger log = LoggerFactory.getLogger(Region.class);
    private final CompoundData cData;
    private final double[] intensities;
    private final int start;
    private final int end;
    private final double sum;
    private final double ppm1;
    private final double ppm2;
    private final double regionMax;
    private final int maxPt;
    private PolynomialSplineFunction pSF = null;

    public Region(CompoundData cData, double[] intensities, int start, int end, double startPPM, double endPPM) {
        this(cData, intensities, start, end, startPPM, endPPM, 1.0, 1.0);
    }

    public Region(CompoundData cData, double[] intensities, int start, int end, double startPPM, double endPPM, double intMax, double max) {
        this.cData = cData;
        this.intensities = intensities.clone();
        this.start = start;
        this.end = end;
        double[] scaleResult = scale(intMax, max);
        sum = scaleResult[0];
        regionMax = scaleResult[1];
        maxPt = (int) scaleResult[2];
        ppm1 = startPPM;
        ppm2 = endPPM;
        System.out.println(toString());
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append("n ");
        sBuilder.append(intensities.length);
        sBuilder.append(" start ");
        sBuilder.append(start);
        sBuilder.append(" end ");
        sBuilder.append(end);
        sBuilder.append(" ppm1 ");
        sBuilder.append(ppm1);
        sBuilder.append(" ppm2 ");
        sBuilder.append(ppm2);
        sBuilder.append(" regionMax ");
        sBuilder.append(regionMax);
        sBuilder.append(" sum ");
        sBuilder.append(sum);
        sBuilder.append(" maxPt ");
        sBuilder.append(maxPt);

        return sBuilder.toString();
    }

    public double[] getIntensities() {
        return intensities.clone();
    }

    double scoreCorr(double[] vData, int pt1, int pt2, int range) {
        if (pt1 > pt2) {
            int hold = pt1;
            pt1 = pt2;
            pt2 = hold;
        }

        int nSample = pt2 - pt1 + 1;
        int nRef = intensities.length;
        int nPoints = Math.max(nSample, nRef);
        double[] refData = new double[nPoints];
        int cStart = 0;
        if (nRef < nSample) {
            cStart = (nSample - nRef) / 2;
        }
        System.arraycopy(intensities, 0, refData, cStart, nRef);

        double[] vDataSection = new double[nPoints];

        double corrMax = -1.0;
        for (int i = 0; i < range; i++) {
            PearsonsCorrelation pCorr = new PearsonsCorrelation();
            System.arraycopy(vData, pt1 + i - range / 2, vDataSection, 0, nPoints);
            double corr = pCorr.correlation(vDataSection, refData);
            if (corr > corrMax) {
                corrMax = corr;
            }
        }
        return corrMax;

    }

    double scorePPM(double ppm, double tol) {
        double ppmCenter = getAvgPPM();
        double delta = Math.abs(ppm - ppmCenter);
        return delta;

    }

    //               foreach ppmCenter $ppms tol $tols {
//                 set match 0
//                 set score 0.0
//                 set max 0.0
//                 set min 1.0e6
//                 set iMax -1
//                 set iMin -1
//                 set j 0
//                 foreach ppm $ppmAvgs {
//                    if {![info exists used($j)]} {
//                        set delta [expr {abs($ppm-$ppmCenter)}]
//                        if {$delta < $gTol} {
//                            if {$delta < $min} {
//                                 set min $delta
//                                 set iMin $j
//                            }
//                        }
//                    }
//                    incr j
//                 }
//                 if {$iMin  != -1} {
//                       set used($iMin) $k
//                       incr nMatch
//                  }
//                  incr k
//
//            }
//            if {$nMatch == [llength $ppms]} {
//                set elems  [array names used]
//                set corrProd 1.0
//                foreach elem $elems {
//                     set ppm [lindex $ppms $used($elem)]
//                     set tol [lindex $tols $used($elem)]
//                     set ppm1 [expr {$ppm+$tol/2}]
//                     set ppm2 [expr {$ppm-$tol/2}]
//                     set corr [lindex [loadCorrSpec $cmpdID $elem $ppm1 $ppm2] 0]
//                     set corrProd [expr {$corr*$corrProd}]
//                     #puts "$cmpdID $elem $ppm~$tol $corr"
//                }
//                stdTbl set $i $cCol [format %.3f $corrProd]
//            } else {
//                 stdTbl set $i $cCol 0.0
//            }
//            stdTbl set $i $sCol [format %d $nMatch]
//        }
    public double getAvgPPM() {
        return (ppm1 + ppm2) / 2.0;
    }

    private double[] scale(double intMax, double max) {
        int n = intensities.length;
        double sumS = 0.0;
        double maxValue = Double.NEGATIVE_INFINITY;
        int iMax = 0;

        for (int i = 0; i < n; i++) {
            double value = intensities[i];
            if (value > maxValue) {
                maxValue = value;
                iMax = i;
            }
            // fixme turned off in new db (h2) version
            // should we still use this  value = (value / intMax) * max;
            sumS += value;
            intensities[i] = value;
        }
        maxValue = (maxValue / intMax) * max;
        double[] result = {sumS, maxValue, iMax};
        return result;
    }

    public double[] getInterpolated(double fraction) {
        if (Math.abs(fraction) < 1.0e-3) {
            return intensities.clone();
        }

        int n = intensities.length;
        double[] interpIntensities = new double[n];
        if (pSF == null) {
            // we use two extra points, assumed to be y=0, so we can interpolate first and last points
            double[] x = new double[n + 2];
            double[] y = new double[n + 2];
            for (int i = 0; i < n; i++) {
                x[i + 1] = i;
                y[i + 1] = intensities[i];
            }
            y[0] = 0.0;
            x[0] = -1;
            y[n + 1] = 0.0;
            x[n + 1] = n;

            SplineInterpolator sInterp = new SplineInterpolator();
            pSF = sInterp.interpolate(x, y);
        }
        try {
            for (int i = 0; i < n; i++) {
                double xValue = i + fraction;
                interpIntensities[i] = pSF.value(xValue);
            }
        } catch (OutOfRangeException adE) {
            log.warn(adE.getMessage(), adE);
        }
        return interpIntensities;
    }

    /**
     * @return the start
     */
    public int getStart() {
        return start;
    }

    /**
     * @return the end
     */
    public int getEnd() {
        return end;
    }

    public double getWidthHz(double deltaPPM) {
        double ppm = deltaPPM / (cData.getSW() / cData.getSF()) * cData.getSW();
        return ppm;
    }

    public double pointToPPM(double pt) {
        double ppm = cData.getRef() - ((1.0 * pt / cData.getN())) * cData.getSW() / cData.getSF();
        return ppm;
    }

    public int ppmToPt(double ppm) {
        int pt = (int) ((cData.getRef() - ppm) * cData.getSF() / cData.getSW() * cData.getN() + 0.5);
        return pt;
    }

    /**
     * @return the sum
     */
    public double getSum() {
        return sum;
    }

    /**
     * @return the ppm1
     */
    public double getPPM1() {
        return ppm1;
    }

    /**
     * @return the ppm2
     */
    public double getPPM2() {
        return ppm2;
    }

    /**
     * @return the regionMax
     */
    public double getRegionMax() {
        return regionMax;
    }

    /**
     * @return the maxPt
     */
    public int getMaxPt() {
        return maxPt;
    }
}
