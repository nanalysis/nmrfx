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
package org.nmrfx.peaks;

import org.nmrfx.utilities.Format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
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

    CouplingPattern(final Multiplet multiplet, double intensity, CouplingItem[] couplingItems) {
        this.multiplet = multiplet;
        this.intensity = intensity;
        this.couplingItems = new CouplingItem[couplingItems.length];
        System.arraycopy(couplingItems, 0, this.couplingItems, 0, couplingItems.length);
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
        multiplet.setIntensity(intensity);
        // fixme  should count lines and make sure values.length, n.length and intensities.length are appropriate
    }

    public CouplingPattern(final Multiplet multiplet, final List<Double> values, final List<String> types, final List<Double> sin2thetas, final double intensity) {
        this.multiplet = multiplet;

        couplingItems = new CouplingItem[values.size()];
        for (int i = 0; i < values.size(); i++) {
            double sin2theta = 0.0;
            if (i < sin2thetas.size()) {
                sin2theta = sin2thetas.get(i);
            }
            int nSplits = COUPLING_CHARS.indexOf(types.get(i)) + 1;
            couplingItems[i] = new CouplingItem(values.get(i), sin2theta, nSplits);
        }
        this.intensity = intensity;
        multiplet.setIntensity(intensity);
        // fixme  should count lines and make sure values.length, n.length and intensities.length are appropriate
    }

    @Override
    Coupling copy(Multiplet multiplet) {
        CouplingPattern couplingPattern = new CouplingPattern(multiplet, this.intensity, this.couplingItems);
        return couplingPattern;
    }

    @Override
    public String getMultiplicity() {
        StringBuilder sBuilder = new StringBuilder();
        for (CouplingItem cItem : couplingItems) {
            int index = cItem.nSplits() - 1;
            if ((index >= COUPLING_CHARS.length()) || (index < 1)) {
                sBuilder.append('m');
            } else {
                sBuilder.append(COUPLING_CHARS.charAt(index));
            }
        }
        return sBuilder.toString();
    }

    public static char toCouplingChar(int nSplits) {
        return COUPLING_CHARS.charAt(nSplits - 1);
    }

    @Override
    public boolean isCoupled() {
        return true;
    }

    public double getValueAt(int i) {
        double value = 0.0;
        if (i < couplingItems.length) {
            value = couplingItems[i].coupling();
        }
        return value;
    }

    public int getNValue(int i) {
        int nValue = 0;
        if (i < couplingItems.length) {
            nValue = couplingItems[i].nSplits();
        }
        return nValue;
    }

    public double[] getValues() {
        double[] values = new double[couplingItems.length];
        int i = 0;
        for (CouplingItem couplingItem : couplingItems) {
            values[i++] = couplingItem.coupling();
        }
        return values;
    }

    public double getSin2Theta(int i) {
        double value = 0.0;
        if (i < couplingItems.length) {
            value = couplingItems[i].sin2Theta();
        }
        return value;
    }

    public double[] getSin2Thetas() {
        double[] values = new double[couplingItems.length];
        int i = 0;
        for (CouplingItem couplingItem : couplingItems) {
            values[i++] = couplingItem.sin2Theta();
        }
        return values;
    }

    public int[] getNValues() {
        int[] n = new int[couplingItems.length];
        int i = 0;
        for (CouplingItem couplingItem : couplingItems) {
            n[i++] = couplingItem.nSplits();
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
        StringBuilder sbuf = new StringBuilder();
        for (int i = 0; i < couplingItems.length; i++) {
            if (i > 0) {
                sbuf.append(" ");
            }

            sbuf.append(String.format("%.2f", couplingItems[i].coupling()));
            sbuf.append(" ");
            sbuf.append(couplingItems[i].nSplits() - 1);
            sbuf.append(" ");
            sbuf.append(String.format("%.2f", couplingItems[i].sin2Theta()));
        }
        return sbuf.toString();
    }

    @Override
    public String getCouplingsAsSimpleString() {
        StringBuilder sbuf = new StringBuilder();

        for (int i = 0; i < couplingItems.length; i++) {
            if (i > 0) {
                sbuf.append(" ");
            }

            sbuf.append(Format.format1(couplingItems[i].coupling()));
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
                if (newValue > (couplingItems[iCoupling - 1].coupling() - minValue)) {
                    newValue = couplingItems[iCoupling - 1].coupling() - minValue;
                }
            }
            if ((iCoupling + 1) < couplingItems.length) {
                if (newValue < (couplingItems[iCoupling + 1].coupling() + minValue)) {
                    newValue = couplingItems[iCoupling + 1].coupling() + minValue;
                }
            }
            CouplingItem oldItem = couplingItems[iCoupling];
            CouplingItem newItem = new CouplingItem(newValue, oldItem.sin2Theta(), oldItem.nSplits());
            couplingItems[iCoupling] = newItem;
        }
    }

    @Override
    List<AbsMultipletComponent> getAbsComponentList() {
        List<AbsMultipletComponent> comps = new ArrayList<>();
        int nFreqs = 1;
        for (CouplingItem couplingItem : couplingItems) {
            nFreqs *= couplingItem.nSplits();
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
            double lineWidth = multiplet.getPeakDim().getLineWidth();
            double volume = (jAmps[i] * lineWidth * (Math.PI / 2.0) / 1.05);
            AbsMultipletComponent comp = new AbsMultipletComponent(multiplet,
                    centerPPM - freqs[i], jAmps[i], volume, lineWidth);
            comps.add(comp);
        }
        return comps;
    }

    @Override
    List<RelMultipletComponent> getRelComponentList() {
        List<RelMultipletComponent> comps = new ArrayList<>();
        int nFreqs = 1;
        for (CouplingItem couplingItem : couplingItems) {
            nFreqs *= couplingItem.nSplits();
        }
        double[] freqs = new double[nFreqs];
        double[] jAmps = new double[nFreqs];
        jSplittings(couplingItems, freqs, jAmps);
        PeakDim peakDim = multiplet.getPeakDim();
        double sf = multiplet.getPeakDim().getSpectralDimObj().getSf();
        for (int i = 0; i < nFreqs; i++) {
            jAmps[i] *= intensity;
            double lineWidth = multiplet.getPeakDim().getLineWidth();
            double volume = (jAmps[i] * lineWidth * (Math.PI / 2.0) / 1.05);
            lineWidth *= sf;

            RelMultipletComponent comp = new RelMultipletComponent(multiplet, freqs[i], jAmps[i], volume, lineWidth);
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
        freqs[0] = cplItem[0].freq();
        jAmps[0] = 1.0;
        for (int i = 0; i < nCouplings; i++) {
            double jCoup = cplItem[i].coupling();
            double sin2Theta = cplItem[i].sin2Theta();
            int nSplits = cplItem[i].nSplits();
            int last = (cplItem[i].nSplits() * current) - 1;
            for (int j = 0; j < current; j++) {
                double offset = jCoup * ((nSplits / 2.0) - 0.5);
                for (int k = 0; k < nSplits; k++) {
                    double pascalAmp = PASCALS_TRIANGLE[nSplits - 1][k];
                    freqs[last] = freqs[current - j - 1] + offset;
                    if (offset > smallCoup) {
                        jAmps[last] = jAmps[current - j - 1] * (pascalAmp + sin2Theta);
                    } else if (offset < -smallCoup) {
                        jAmps[last] = jAmps[current - j - 1] * (pascalAmp - sin2Theta);
                    } else {
                        jAmps[last] = jAmps[current - j - 1] * pascalAmp;
                    }
                    last--;
                    offset -= jCoup;
                }
            }
            current *= nSplits;
        }
    }

    @Override
    public ArrayList<TreeLine> getSplittingGraph() {

        ArrayList<TreeLine> lines = new ArrayList<>();

        int[] splitCount = getNValues();

        if ((couplingItems != null) && (couplingItems.length > 0)
                && (splitCount != null)) {
            if ((couplingItems.length > 1) || (couplingItems[0].coupling() != 0.0)) {
                PeakDim peakDim = multiplet.getPeakDim();
                double sf = peakDim.getPeak().peakList.getSpectralDim(peakDim.getSpectralDim()).getSf();
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
                        double offset = (couplingItems[i].coupling() / sf) * ((splitCount[i] / 2.0)
                                - 0.5);
                        double origin = freqs[current - j - 1];
                        for (int k = 0; k < splitCount[i]; k++) {
                            double freq = freqs[current - j - 1] + offset;
                            freqs[last--] = freq;
                            lines.add(new TreeLine(origin, i * 1.0, -freq, i * 1.0));
                            offset -= (couplingItems[i].coupling() / sf);
                        }
                    }

                    current *= splitCount[i];
                }

            }
        }

        return lines;
    }
}
