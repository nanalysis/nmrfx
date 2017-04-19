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
package org.nmrfx.processor.datasets.peaks;

import java.awt.geom.Line2D;
import java.util.ArrayList;

public abstract class Coupling {

    Multiplet multiplet;

    abstract String getMultiplicity();

    abstract Coupling adjustCouplings(final Multiplet multiplet, final int iCoupling, final double newValue);

    abstract FreqIntensities getFreqIntensitiesFromSplittings();

    abstract ArrayList<Line2D> getSplittingGraph();

//    abstract TclObject getCouplingsAsTclObject(Interp interp) throws TclException;
    abstract String getCouplingsAsString();

    abstract String getCouplingsAsSimpleString();

    abstract boolean isCoupled();

    abstract Coupling update(double[] v1, double[] intensities);

    abstract Coupling update(double[] v1, double intensity, double[] sin2Thetas);

    /*
     void setValue(double[] newValues, int[] newN) {
     frequencyOffsets = new double[0];
     values = (double[]) newValues.clone();

     int[] order = NvUtil.arraySorter(values, false);
     n = new int[values.length];
     for (int i=0;i<n.length;i++) {
     n[i] = newN[order[i]];
     }

     }


     void setIntensities(double[] intensities) {
     this.intensities = (double[]) intensities.clone();
     }

     void setFrequencyOffsets(double[] freqs) {
     values = new double[0];
     this.frequencyOffsets = (double[]) freqs.clone();
     }

     public boolean isCoupled() {
     boolean result = false;
     if ((values.length > 0) && (values[0] > 0.02)) {
     result = true;
     }

     return result;
     }

     void setSinglet() {
     values = new double[0];
     frequencyOffsets = new double[0];
     n = new int[0];
     }

     public boolean isSinglet() {
     boolean result = false;
     if (isGenericMultiplet()) {
     result = (frequencyOffsets.length < 2);
     } else if (values.length == 0) {
     result = true;
     } else if ((values.length == 1) && (Math.abs(values[0]) < 0.02)) {
     result = true;
     } else {
     result = false;
     }

     return result;
     }

     public boolean isGenericMultiplet() {
     return (frequencyOffsets.length > 0);
     }

     double getValue() {
     return values[0];
     }
     double getValueAt(int i) {
     double value = 0.0;
     if (i < values.length) {
     value = values[i];
     }
     return value;
     }
     public int getNValue(int i) {
     int nValue=0;
     if (i < values.length) {
     nValue = n[i];
     }
     return nValue;
     }
     public double[] getValues() {
     return values;
     }

     public double[] getIntensities() {
     return intensities.clone();
     }
     public double getIntensity(int i) {
     double value = 0.0;
     if (i < intensities.length) {
     value = intensities[i];
     }
     return value;
     }
     public double getFrequencyOffset(int i) {
     return frequencyOffsets[i];
     }
     private double[] getFrequencyOffsets() {
     return frequencyOffsets.clone();
     }
     public int getFrequencyCount() {
     return frequencyOffsets.length;
     }

     int[] getNs() {
     return (int[]) n.clone();
     }

     int getNValues() {
     if (values == null) {
     return 0;
     } else {
     return values.length;
     }
     }
     * */
}
