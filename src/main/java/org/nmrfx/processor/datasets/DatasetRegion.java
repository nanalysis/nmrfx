package org.nmrfx.processor.datasets;

import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;

// fixme add document "Note: this comparator imposes orderings that are inconsistent with equals."
public class DatasetRegion implements Comparator, Comparable {

    private final double[] x;
    private final double[] startIntensity;
    private final double[] endIntensity;
    double integral;
    double min;
    double max;
    boolean isAuto = true;

    public DatasetRegion() {
        x = null;
        startIntensity = new double[0];
        endIntensity = new double[0];
    }

    public DatasetRegion(final double x0, final double x1) {
        x = new double[2];
        startIntensity = new double[1];
        endIntensity = new double[1];
        x[0] = x0;
        x[1] = x1;
        sortEachDim();
    }

    public DatasetRegion(final double x0, final double x1, final double y0, final double y1) {
        x = new double[4];
        startIntensity = new double[2];
        endIntensity = new double[2];
        x[0] = x0;
        x[1] = x1;
        x[2] = y0;
        x[3] = y1;
        sortEachDim();
    }

    public DatasetRegion(final double[] newRegion) {
        x = new double[newRegion.length];
        startIntensity = new double[x.length / 2];
        endIntensity = new double[x.length / 2];
        System.arraycopy(newRegion, 0, x, 0, x.length);
        sortEachDim();
    }

    public DatasetRegion(final double[] newRegion, final double[] newIntensities) {
        x = new double[newRegion.length];
        startIntensity = new double[x.length / 2];
        endIntensity = new double[x.length / 2];
        System.arraycopy(newRegion, 0, x, 0, x.length);
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

    public double getRegionStart(int dim) {
        if (dim >= getNDims()) {
            throw new IllegalArgumentException("Invalid dimension");
        }
        return x[dim * 2];
    }

    public void setRegionStart(int dim, double value) {
        if (dim >= getNDims()) {
            throw new IllegalArgumentException("Invalid dimension");
        }
        x[dim * 2] = value;
    }

    public double getRegionEnd(int dim) {
        if (dim >= getNDims()) {
            throw new IllegalArgumentException("Invalid dimension");
        }
        return x[dim * 2 + 1];
    }

    public void setRegionEnd(int dim, double value) {
        if (dim >= getNDims()) {
            throw new IllegalArgumentException("Invalid dimension");
        }
        x[dim * 2 + 1] = value;
    }

    public double getRegionStartIntensity(int dim) {
        if (dim >= getNDims()) {
            throw new IllegalArgumentException("Invalid dimension");
        }
        return startIntensity[dim * 2];
    }

    public void setRegionStartIntensity(int dim, double value) {
        if (dim >= getNDims()) {
            throw new IllegalArgumentException("Invalid dimension");
        }
        startIntensity[dim * 2] = value;
    }

    public double getRegionEndIntensity(int dim) {
        if (dim >= getNDims()) {
            throw new IllegalArgumentException("Invalid dimension");
        }
        return endIntensity[dim * 2];
    }

    public void setRegionEndIntensity(int dim, double value) {
        if (dim >= getNDims()) {
            throw new IllegalArgumentException("Invalid dimension");
        }
        endIntensity[dim * 2] = value;
    }

    @Override
    public String toString() {
        if (x == null) {
            return "";
        } else {
            // fixme only works for x.length = 2;
            return x[0] + " " + x[1];
        }
    }

    @Override
    public int compare(Object o1, Object o2) {
        // FIXME do we need to test type of object?
        int result = 0;
        DatasetRegion r1 = (DatasetRegion) o1;
        DatasetRegion r2 = (DatasetRegion) o2;
        if ((r1 != null) || (r2 != null)) {
            if (r1 == null) {
                result = -1;
            } else if (r2 == null) {
                result = 1;
            } else if (r1.x[0] < r2.x[0]) {
                result = -1;
            } else if (r2.x[0] < r1.x[0]) {
                result = 1;
            }
        }

        return result;
    }

    @Override
    public int compareTo(Object o2) {
        return compare(this, o2);
    }

    @Override
    public boolean equals(Object o2) {
        return (compare(this, o2) == 0);
    }

    public boolean overlapOnDim(Object o2, int iDim) {
        boolean result = true;
        DatasetRegion r2 = (DatasetRegion) o2;

        if (this.getRegionEnd(iDim) < r2.getRegionStart(iDim)) {
            result = false;
        } else if (this.getRegionStart(iDim) > r2.getRegionEnd(iDim)) {
            result = false;
        }
        return result;
    }

    public boolean overlaps(Object o2) {
        boolean result = true;
        DatasetRegion r2 = (DatasetRegion) o2;
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
            DatasetRegion tRegion = (DatasetRegion) iter.next();

            if (overlaps(tRegion)) {
                result = true;

                break;
            } else if (tRegion.x[0] > x[1]) {
                break;
            }
        }

        return result;
    }

    public boolean removeOverlapping(SortedSet set) {
        Iterator iter = set.iterator();
        boolean result = false;

        while (iter.hasNext()) {
            DatasetRegion tRegion = (DatasetRegion) iter.next();

            if (overlaps(tRegion)) {
                result = true;
                iter.remove();
            } else if (tRegion.x[0] > x[1]) {
                break;
            }
        }

        return result;
    }

    public DatasetRegion split(double splitPosition0, double splitPosition1) {
        DatasetRegion newRegion = new DatasetRegion(splitPosition1, x[1]);
        x[1] = splitPosition0;
        return newRegion;
    }

    public void setIntegral(double value) {
        integral = value;
    }

    public double getIntegral() {
        return integral;
    }

    public void setMax(double value) {
        max = value;
    }

    public double getMax() {
        return max;
    }

    public void setMin(double value) {
        min = value;
    }

    public double getMin() {
        return min;
    }

    public boolean isAuto() {
        return isAuto;
    }

    public void setAuto(boolean value) {
        isAuto = value;
    }
}
