package org.nmrfx.processor.datasets;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

// fixme add document "Note: this comparator imposes orderings that are inconsistent with equals."
public class DatasetRegion implements Comparator, Comparable {

    private final double[] x;
    private final double[] startIntensity;
    private final double[] endIntensity;
    double integral;
    double min;
    double max;
    boolean isAuto = true;

    public static TreeSet<DatasetRegion> loadRegions(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        boolean firstLine = true;
        TreeSet<DatasetRegion> regions = new TreeSet<>();
        int nDim = 0;
        for (String line : lines) {
            line = line.trim();
            if (line.length() > 0) {
                String[] fields = line.split("\t");
                if (firstLine) {
                    int nPos = 0;
                    for (String field : fields) {
                        if (field.startsWith("pos")) {
                            nPos++;
                        } else {
                            break;
                        }
                    }
                    nDim = nPos / 2;
                    firstLine = false;

                } else {
                    double[] x = new double[nDim * 2];
                    double[] startIntensity = new double[nDim];
                    double[] endIntensity = new double[nDim];
                    int k = 0;
                    for (int i = 0; i < nDim; i++) {
                        for (int j = 0; j < 2; j++) {
                            x[k] = Double.parseDouble(fields[k]);
                            k++;
                        }
                    }
                    for (int i = 0; i < Math.pow(2, nDim - 1); i++) {
                        startIntensity[i] = Double.parseDouble(fields[k++]);
                    }
                    for (int i = 0; i < Math.pow(2, nDim - 1); i++) {
                        endIntensity[i] = Double.parseDouble(fields[k++]);
                    }
                    DatasetRegion region = new DatasetRegion(x, startIntensity, endIntensity);
                    region.setIntegral(Double.parseDouble(fields[k++]));
                    region.setMin(Double.parseDouble(fields[k++]));
                    region.setMax(Double.parseDouble(fields[k++]));
                    region.setAuto(fields[k].equals("1"));
                    regions.add(region);
                }
            }

        }
        return regions;
    }

    public static void saveRegions(File file, TreeSet<DatasetRegion> regions) {
        if (regions != null) {
            try (FileWriter writer = new FileWriter(file)) {
                boolean firstLine = true;
                for (DatasetRegion region : regions) {
                    if (firstLine) {
                        writer.write(region.getHeader());
                        firstLine = false;
                    }
                    writer.write('\n');
                    writer.write(region.toString());
                }

            } catch (IOException ioE) {
            }
        } else {
            if (file.canWrite()) {
                file.delete();
            }
        }
    }

    public static File getRegionFile(String fileName) {
        int len = fileName.length();
        String parFileName;
        int extLen = 0;
        if (fileName.endsWith(".nv")) {
            extLen = 3;
        } else if (fileName.endsWith(".ucsf")) {
            extLen = 5;
        }
        parFileName = fileName.substring(0, len - extLen) + "_regions.txt";
        return new File(parFileName);
    }

    public String getHeader() {
        char sepChar = '\t';
        StringBuilder sBuilder = new StringBuilder();
        int nDim = x.length / 2;
        for (int i = 0; i < nDim; i++) {
            for (int j = 0; j < 2; j++) {
                sBuilder.append("pos").append(j).append('_').append(i).append(sepChar);
            }
        }
        for (int i = 0; i < Math.pow(2, nDim - 1); i++) {
            for (int j = 0; j < 2; j++) {
                sBuilder.append("int").append(j).append('_').append(i).append(sepChar);
            }
        }
        sBuilder.append("integral").append(sepChar);
        sBuilder.append("min").append(sepChar);
        sBuilder.append("max").append(sepChar);
        sBuilder.append("auto");
        return sBuilder.toString();
    }

    public String toString() {
        char sepChar = '\t';
        StringBuilder sBuilder = new StringBuilder();
        for (double value : x) {
            sBuilder.append(value).append(sepChar);
        }
        for (double value : startIntensity) {
            sBuilder.append(value).append(sepChar);
        }
        for (double value : endIntensity) {
            sBuilder.append(value).append(sepChar);
        }
        sBuilder.append(integral).append(sepChar);
        sBuilder.append(min).append(sepChar);
        sBuilder.append(max).append(sepChar);
        sBuilder.append(isAuto ? 1 : 0);
        return sBuilder.toString();
    }

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

    public DatasetRegion(final double[] newRegion, final double[] startIntensity, final double[] endIntensity) {
        x = newRegion.clone();
        this.startIntensity = startIntensity.clone();
        this.endIntensity = endIntensity.clone();
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

    public boolean overlapOnDim(double ppm, int iDim) {
        boolean result = true;

        if (ppm < getRegionStart(iDim)) {
            result = false;
        } else if (ppm > getRegionEnd(iDim)) {
            result = false;
        }
        return result;
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

    public boolean removeOverlapping(SortedSet<DatasetRegion> set) {
        Iterator<DatasetRegion> iter = set.iterator();
        boolean result = false;

        while (iter.hasNext()) {
            DatasetRegion tRegion = iter.next();
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

    public void measure(Dataset dataset) throws IOException {
        int[] pt = new int[1];
        double start = getRegionStart(0);
        double end = getRegionEnd(0);
        int istart = dataset.ppmToPoint(0, start);
        int iend = dataset.ppmToPoint(0, end);
        if (istart > iend) {
            int hold = istart;
            istart = iend;
            iend = hold;
        }
        double sum = 0.0;
        min = Double.MAX_VALUE;
        max = Double.NEGATIVE_INFINITY;
        double offset = startIntensity[0];
        double delta = (endIntensity[0] - startIntensity[0]) / (iend - istart);

        for (int i = istart; i <= iend; i++) {
            pt[0] = i;

            double value = dataset.readPoint(pt);
            value -= offset;
            offset += delta;
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
        }
        setIntegral(sum);
    }

    public static DatasetRegion findOverlap(TreeSet<DatasetRegion> regions, double ppm, int dim) {
        for (DatasetRegion region : regions) {
            if (region.overlapOnDim(ppm, dim)) {
                return region;
            }
        }
        return null;
    }

    public static DatasetRegion findClosest(TreeSet<DatasetRegion> regions, double ppm, int dim) {
        DatasetRegion closest = null;
        double minDis = Double.MAX_VALUE;
        for (DatasetRegion region : regions) {
            double delta = Math.abs(ppm - (region.getRegionStart(dim) + region.getRegionEnd(dim)) / 2);
            if (delta < minDis) {
                closest = region;
                minDis = delta;
            }
        }
        return closest;
    }
}
