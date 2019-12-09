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

 /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.peaks;

import org.nmrfx.processor.utilities.Format;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author brucejohnson
 */
public class CouplingPattern extends Coupling {

    public final static String COUPLING_CHARS = "sdtqphx";

    private final CouplingItem[] couplingItems;
    private final double intensity;
    static final private int[][] PASCALS_TRIANGLE = new int[16][];

    static {
        for (int iRow = 0; iRow < PASCALS_TRIANGLE.length; iRow++) {
            PASCALS_TRIANGLE[iRow] = new int[iRow + 1];
            PASCALS_TRIANGLE[iRow][0] = 1;
            PASCALS_TRIANGLE[iRow][iRow] = 1;
            for (int iCol = 1; iCol < iRow; iCol++) {
                PASCALS_TRIANGLE[iRow][iCol] = PASCALS_TRIANGLE[iRow - 1][iCol] + PASCALS_TRIANGLE[iRow - 1][iCol - 1];
            }
        }
    }

    CouplingPattern(final Multiplet multiplet, final double[] values, final int[] n, final double intensity, final double[] sin2thetas) {
        this.multiplet = multiplet;

        couplingItems = new CouplingItem[values.length];
        for (int i = 0; i < values.length; i++) {
            double sin2theta = 0.0;
            if (i < sin2thetas.length) {
                sin2theta = sin2thetas[i];
            }
            couplingItems[i] = new CouplingItem(values[i], sin2theta, n[i]);
        }
        this.intensity = intensity;
        // fixme  should count lines and make sure values.length, n.length and intensities.length are appropriate
    }

    @Override
    public String getMultiplicity() {
        StringBuilder sBuilder = new StringBuilder();
        for (CouplingItem cItem : couplingItems) {
            int index = cItem.getNSplits() - 1;
            if ((index >= COUPLING_CHARS.length()) || (index < 1)) {
                sBuilder.append('m');
            } else {
                sBuilder.append(COUPLING_CHARS.charAt(index));
            }
        }
        return sBuilder.toString();
    }

    @Override
    public boolean isCoupled() {
        return true;
    }

    double getValueAt(int i) {
        double value = 0.0;
        if (i < couplingItems.length) {
            value = couplingItems[i].getCoupling();
        }
        return value;
    }

    public int getNValue(int i) {
        int nValue = 0;
        if (i < couplingItems.length) {
            nValue = couplingItems[i].getNSplits();
        }
        return nValue;
    }

    public double[] getValues() {
        double[] values = new double[couplingItems.length];
        int i = 0;
        for (CouplingItem couplingItem : couplingItems) {
            values[i++] = couplingItem.getCoupling();
        }
        return values;
    }

    public double[] getSin2Thetas() {
        double[] values = new double[couplingItems.length];
        int i = 0;
        for (CouplingItem couplingItem : couplingItems) {
            values[i++] = couplingItem.getSin2Theta();
        }
        return values;
    }

    public int[] getNValues() {
        int[] n = new int[couplingItems.length];
        int i = 0;
        for (CouplingItem couplingItem : couplingItems) {
            n[i++] = couplingItem.getNSplits();
        }
        return n;
    }

    public double getIntensity() {
        return intensity;
    }

    public int getNCouplingValues() {
        return couplingItems.length;
    }

    @Override
    public String getCouplingsAsString() {

        if ((couplingItems.length == 1) && (couplingItems[0].getNSplits() == 2)) {
            return String.valueOf(couplingItems[0].getCoupling());
        } else {
            StringBuilder sbuf = new StringBuilder();

            for (int i = 0; i < couplingItems.length; i++) {
                if (i > 0) {
                    sbuf.append(" ");
                }

                sbuf.append(couplingItems[i].getCoupling());
                sbuf.append(" ");
                sbuf.append(couplingItems[i].getNSplits() - 1);
            }

            return sbuf.toString();
        }
    }

    @Override
    public String getCouplingsAsSimpleString() {
        StringBuilder sbuf = new StringBuilder();

        for (int i = 0; i < couplingItems.length; i++) {
            if (i > 0) {
                sbuf.append(" ");
            }

            sbuf.append(Format.format2(couplingItems[i].getCoupling()));
//            sbuf.append(" ");
//            sbuf.append(couplingItems[i].getNSplits() - 1);
//            sbuf.append(" ");
//            sbuf.append(Format.format2(couplingItems[i].getSin2Theta()));
        }

        return sbuf.toString();
    }

    public void adjustCouplings(final int iCoupling, double newValue) {
        double minValue = 0.1;
        if ((iCoupling >= 0) && (couplingItems.length > iCoupling)) {
            if (newValue < minValue) {
                newValue = minValue;
            }
            if ((iCoupling - 1) >= 0) {
                if (newValue > (couplingItems[iCoupling - 1].getCoupling() - minValue)) {
                    newValue = couplingItems[iCoupling - 1].getCoupling() - minValue;
                }
            }
            if ((iCoupling + 1) < couplingItems.length) {
                if (newValue < (couplingItems[iCoupling + 1].getCoupling() + minValue)) {
                    newValue = couplingItems[iCoupling + 1].getCoupling() + minValue;
                }
            }
            CouplingItem oldItem = couplingItems[iCoupling];
            CouplingItem newItem = new CouplingItem(newValue, oldItem.getSin2Theta(), oldItem.getNSplits());
            couplingItems[iCoupling] = newItem;
        }
    }

    @Override
    List<MultipletComponent> getAbsComponentList() {
        List<MultipletComponent> comps = new ArrayList<>();
        int nFreqs = 1;
        for (CouplingItem couplingItem : couplingItems) {
            nFreqs *= couplingItem.getNSplits();
        }
        double[] freqs = new double[nFreqs];
        double[] jAmps = new double[nFreqs];
        jSplittings(couplingItems, freqs, jAmps);
        PeakDim peakDim = multiplet.getPeakDim();
        double centerPPM = peakDim.getChemShiftValue();
        double sf = peakDim.getPeak().peakList.getSpectralDim(peakDim.getSpectralDim()).getSf();
        for (int i = 0; i < nFreqs; i++) {
            freqs[i] *= 1.0 / sf;
            jAmps[i] *= intensity;
            MultipletComponent comp = new MultipletComponent(freqs[i] + centerPPM, jAmps[i], multiplet.getPeakDim().getLineWidth());
            comps.add(comp);
        }
        return comps;
    }

    @Override
    List<MultipletComponent> getRelComponentList() {
        List<MultipletComponent> comps = new ArrayList<>();
        int nFreqs = 1;
        for (CouplingItem couplingItem : couplingItems) {
            nFreqs *= couplingItem.getNSplits();
        }
        double[] freqs = new double[nFreqs];
        double[] jAmps = new double[nFreqs];
        jSplittings(couplingItems, freqs, jAmps);
        PeakDim peakDim = multiplet.getPeakDim();
        for (int i = 0; i < nFreqs; i++) {
            jAmps[i] *= intensity;
            MultipletComponent comp = new MultipletComponent(freqs[i], jAmps[i], multiplet.getPeakDim().getLineWidth());
            comps.add(comp);
        }
        return comps;
    }

    public void jSplittings(double[] freqs, double[] jAmps) {
        jSplittings(couplingItems, freqs, jAmps);
    }

    public static void jSplittings(CouplingItem[] cplItem, double[] freqs, double[] jAmps) {
        int current = 1;
        int nCouplings = cplItem.length;
        if (nCouplings == 0) {
            return;
        }
        Arrays.sort(cplItem);
        final double smallCoup = 0.01;
        freqs[0] = cplItem[0].getFrequency();
        jAmps[0] = 1.0;
        for (int i = 0; i < nCouplings; i++) {
            double jCoup = cplItem[i].getCoupling();
            double sin2Theta = cplItem[i].getSin2Theta();
            int nSplits = cplItem[i].getNSplits();
            int last = (cplItem[i].getNSplits() * current) - 1;
            for (int j = 0; j < current; j++) {
                double offset = jCoup * ((nSplits / 2.0) - 0.5);
                for (int k = 0; k < nSplits; k++) {
                    double pascalAmp = PASCALS_TRIANGLE[nSplits - 1][k];
                    // System.out.println(j + " " + current + " " + jCoup + " " + offset + " " + (current - j - 1) + " " + last + " " + freqs[last] + " " + freqs[current - j - 1]);
                    freqs[last] = freqs[current - j - 1] + offset;
                    if (offset > smallCoup) {
                        jAmps[last] = jAmps[current - j - 1] * pascalAmp + sin2Theta;
                    } else if (offset < -smallCoup) {
                        jAmps[last] = jAmps[current - j - 1] * pascalAmp - sin2Theta;
                    } else {
                        jAmps[last] = jAmps[current - j - 1] * pascalAmp;
                    }
                    //System.out.println(i + " " + nSplits + " " + k + " " + pascalAmp + " " + freqs[last] + " " + jAmps[last]);
                    last--;
                    offset -= jCoup;
                }
            }
            current *= nSplits;
        }
    }

    @Override
    public ArrayList<Line2D> getSplittingGraph() {

        ArrayList<Line2D> lines = new ArrayList<>();

        int[] splitCount = getNValues();

        if ((couplingItems != null) && (couplingItems.length > 0)
                && (splitCount != null)) {
            if ((couplingItems.length > 1) || (couplingItems[0].getCoupling() != 0.0)) {
                PeakDim peakDim = multiplet.getPeakDim();
                double sf = peakDim.myPeak.peakList.getSpectralDim(peakDim.getSpectralDim()).getSf();
                int nFreqs = 1;

                for (int j = 0; j < splitCount.length; j++) {
                    nFreqs = nFreqs * splitCount[j];
                }

                double[] freqs = new double[nFreqs];

                int current = 1;
                int nCouplings = couplingItems.length;

                for (int i = 0; i < nCouplings; i++) {
                    int last = (splitCount[i] * current) - 1;

                    for (int j = 0; j < current; j++) {
                        double offset = (couplingItems[i].getCoupling() / sf) * ((splitCount[i] / 2.0)
                                - 0.5);
                        double origin = freqs[current - j - 1];
                        for (int k = 0; k < splitCount[i]; k++) {
                            double freq = freqs[current - j - 1] + offset;
                            freqs[last--] = freq;
                            lines.add(new Line2D.Double(origin, i * 1.0, -freq, i * 1.0));
                            offset -= (couplingItems[i].getCoupling() / sf);
                        }
                    }

                    current *= splitCount[i];
                }

            }
        }

        return lines;
    }
}
