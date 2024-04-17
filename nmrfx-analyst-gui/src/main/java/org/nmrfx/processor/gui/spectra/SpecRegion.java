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
package org.nmrfx.processor.gui.spectra;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.SortedSet;

// fixme add document "Note: this comparator imposes orderings that are inconsistent with equals."
public class SpecRegion implements Comparator, Comparable {

    private final double[] x;
    private final double[] startIntensity;
    private final double[] endIntensity;

    public SpecRegion() {
        x = null;
        startIntensity = new double[0];
        endIntensity = new double[0];
    }

    public SpecRegion(final double x0, final double x1) {
        x = new double[2];
        startIntensity = new double[1];
        endIntensity = new double[1];
        x[0] = x0;
        x[1] = x1;
        sortEachDim();
    }

    public SpecRegion(final double x0, final double x1, final double y0, final double y1) {
        x = new double[4];
        startIntensity = new double[2];
        endIntensity = new double[2];
        x[0] = x0;
        x[1] = x1;
        x[2] = y0;
        x[3] = y1;
        sortEachDim();
    }

    public SpecRegion(final double[] newSpecRegion) {
        x = new double[newSpecRegion.length];
        startIntensity = new double[x.length / 2];
        endIntensity = new double[x.length / 2];
        for (int i = 0; i < x.length; i++) {
            x[i] = newSpecRegion[i];
        }
        sortEachDim();
    }

    public SpecRegion(final double[] newSpecRegion, final double[] newIntensities) {
        x = new double[newSpecRegion.length];
        startIntensity = new double[x.length / 2];
        endIntensity = new double[x.length / 2];
        for (int i = 0; i < x.length; i++) {
            x[i] = newSpecRegion[i];
        }
        for (int i = 0; i < newIntensities.length; i += 2) {
            startIntensity[i / 2] = newIntensities[2 * i];
            endIntensity[i / 2] = newIntensities[2 * i + 1];
        }
        sortEachDim();
    }

    private void sortEachDim() {
        int n = getNDims();
        for (int i = 0; i < n; i++) {
            int j = i * 2;
            int k = i * 2 + 1;
            if (x[j] >= x[k]) {
                double hold = x[j];
                x[j] = x[k];
                x[k] = hold;
            }
        }
    }

    public int getNDims() {
        return x.length / 2;
    }

    public double getSpecRegionStart(int dim) {
        if (dim >= getNDims()) {
            throw new IllegalArgumentException("Invalid dimension");
        }
        return x[dim * 2];
    }

    public double getSpecRegionEnd(int dim) {
        if (dim >= getNDims()) {
            throw new IllegalArgumentException("Invalid dimension");
        }
        return x[dim * 2 + 1];
    }

    public double getSpecRegionStartIntensity(int dim) {
        if (dim >= getNDims()) {
            throw new IllegalArgumentException("Invalid dimension");
        }
        return startIntensity[dim * 2];
    }

    public double getSpecRegionEndIntensity(int dim) {
        if (dim >= getNDims()) {
            throw new IllegalArgumentException("Invalid dimension");
        }
        return endIntensity[dim * 2];
    }

    public String toString() {
        if (x == null) {
            return "";
        } else {
            // fixme only works for x.length = 2;
            return x[0] + " " + x[1];
        }
    }

    public int compare(Object o1, Object o2) {
        // FIXME do we need to test type of object?
        int result = 0;
        SpecRegion r1 = (SpecRegion) o1;
        SpecRegion r2 = (SpecRegion) o2;
        if ((r1 != null) || (r2 != null)) {
            if (r1 == null || r1.x == null) {
                result = -1;
            } else if (r2 == null || r2.x == null) {
                result = 1;
            } else if (r1.x[0] < r2.x[0]) {
                result = -1;
            } else if (r2.x[0] < r1.x[0]) {
                result = 1;
            }
        }

        return result;
    }

    public int compareTo(Object o2) {
        return compare(this, o2);
    }

    @Override
    public int hashCode() {
        return x == null ? Objects.hashCode(x) : Double.hashCode(x[0]);
    }

    public boolean equals(Object o2) {
        if (!(o2 instanceof SpecRegion)) {
            return false;
        }
        return (compare(this, o2) == 0);
    }

    public boolean overlapOnDim(Object o2, int iDim) {
        boolean result = true;
        SpecRegion r2 = (SpecRegion) o2;

        if (this.getSpecRegionEnd(iDim) < r2.getSpecRegionStart(iDim)) {
            result = false;
        } else if (this.getSpecRegionStart(iDim) > r2.getSpecRegionEnd(iDim)) {
            result = false;
        }
        return result;
    }

    public boolean overlaps(Object o2) {
        boolean result = true;
        SpecRegion r2 = (SpecRegion) o2;
        for (int i = 0, n = getNDims(); i < n; i++) {
            if (!overlapOnDim(r2, i)) {
                result = false;
                break;
            }
        }
        return result;
    }

    public boolean overlaps(SortedSet set) {
        Iterator iter = set.iterator();
        boolean result = false;

        while (iter.hasNext()) {
            SpecRegion tSpecRegion = (SpecRegion) iter.next();

            if (overlaps(tSpecRegion)) {
                result = true;

                break;
            } else if (tSpecRegion.x[0] > x[1]) {
                break;
            }
        }

        return result;
    }

    public boolean removeOverlapping(SortedSet set) {
        Iterator iter = set.iterator();
        boolean result = false;

        while (iter.hasNext()) {
            SpecRegion tSpecRegion = (SpecRegion) iter.next();

            if (overlaps(tSpecRegion)) {
                result = true;
                iter.remove();
            } else if (tSpecRegion.x[0] > x[1]) {
                break;
            }
        }

        return result;
    }
}
