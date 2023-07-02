package org.nmrfx.datasets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

// fixme add document "Note: this comparator imposes orderings that are inconsistent with equals."
public class DatasetRegion implements Comparator, Comparable {

    private static final Logger log = LoggerFactory.getLogger(DatasetRegion.class);
    private final double[] x;
    private final double[] startIntensity;
    private final double[] endIntensity;
    private double integral;
    private double min;
    private double max;
    private int[] maxLocation;
    private boolean isAuto = false;
    // Listeners for changes in this DatasetRegion
    private final Set<DatasetRegionListener> regionChangeListeners = new HashSet<>();


    public void addListener(DatasetRegionListener regionListener) {
        regionChangeListeners.add(regionListener);
    }

    public void removeListener(DatasetRegionListener regionListener) {
        regionChangeListeners.remove(regionListener);
    }

    public void updateAllListeners() {
        regionChangeListeners.forEach(regionListener -> regionListener.datasetRegionUpdated(this));
    }

    /**
     * Checks the first line of the file to see if it is the long format of the regions file
     *
     * @param file The file to check
     * @return True if it is a long region file, false if it is short or not a region file.
     * @throws IOException
     */
    public static boolean isLongRegionFile(File file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath())) {
            String firstLine = reader.readLine();
            if (firstLine == null) {
                // The file is empty, can be assumed to be a long region file
                return true;
            }
            firstLine = firstLine.trim();
            String[] fields = firstLine.split("\\s+");
            // The first line should be a header with at least 8 fields and the first two fields should start with pos
            return fields.length > 7 && fields[0].contains("pos") && fields[1].contains("pos");
        }
    }

    /**
     * Loads a region file as a list of DatasetRegion.
     *
     * @param file The file to load.
     * @return A List of Dataset Regions
     * @throws IOException
     */
    public static List<DatasetRegion> loadRegions(File file) throws IOException {
        boolean isLongRegionsFile = isLongRegionFile(file);
        if (isLongRegionsFile) {
            return loadRegionsLong(file);
        } else {
            return loadRegionsShort(file);
        }

    }

    /**
     * Loads the long version of the region file as a list of DatasetRegions.
     *
     * @param file The file to load.
     * @return A List of DatasetRegions
     * @throws IOException
     */
    private static List<DatasetRegion> loadRegionsLong(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        boolean firstLine = true;
        List<DatasetRegion> regions = new ArrayList<>();
        int nDim = 0;
        for (String line : lines) {
            line = line.trim();
            if (line.length() == 0) {
                // Ignore blank lines
                continue;
            }
            String[] fields = line.split("\\s+");
            if (firstLine) {
                int nPos = (int) Arrays.stream(fields).filter(field -> field.startsWith("pos")).count();
                nDim = nPos / 2;
                firstLine = false;

            } else {
                double[] x = new double[nDim * 2];
                double[] startIntensity = new double[nDim];
                double[] endIntensity = new double[nDim];
                int k = 0;
                for (int i = 0; i < nDim * 2; i++) {
                    x[k] = Double.parseDouble(fields[k]);
                    k++;

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
        return regions;
    }

    /**
     * Loads the short version of the region file as a list of DatasetRegions.
     *
     * @param file The file to load.
     * @return A list of DatasetRegions
     * @throws IOException
     */
    public static List<DatasetRegion> loadRegionsShort(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        List<DatasetRegion> regions = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            String[] fields = line.split("\\s+");
            double[] x = new double[2];
            x[0] = Double.parseDouble(fields[1]);
            x[1] = Double.parseDouble(fields[2]);
            DatasetRegion region = new DatasetRegion(x[0], x[1]);
            regions.add(region);
        }
        return regions;
    }

    public static void saveRegions(File file, Collection<DatasetRegion> regions) {
        List<DatasetRegion> sortedRegions = regions.stream().sorted().toList();
        if (sortedRegions != null) {
            try (FileWriter writer = new FileWriter(file)) {
                boolean firstLine = true;
                for (DatasetRegion region : sortedRegions) {
                    if (firstLine) {
                        writer.write(region.getHeader());
                        firstLine = false;
                    }
                    writer.write('\n');
                    writer.write(region.toString());
                }

            } catch (IOException ioE) {
                log.warn(ioE.getMessage(), ioE);
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
        updateAllListeners();
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
        updateAllListeners();
    }

    public double getAvgPPM(int dim) {
        return (getRegionStart(dim) + getRegionEnd(0)) / 2;
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
        updateAllListeners();
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
        updateAllListeners();
    }

    @Override
    public int compare(Object o1, Object o2) {
        // FIXME do we need to test type of object?
        int result = 0;
        DatasetRegion r1 = (DatasetRegion) o1;
        DatasetRegion r2 = (DatasetRegion) o2;
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

    @Override
    public int compareTo(Object o2) {
        return compare(this, o2);
    }

    @Override
    public int hashCode() {
        return x == null ? Objects.hashCode(x) : Double.hashCode(x[0]);
    }

    @Override
    public boolean equals(Object o2) {
        if (!(o2 instanceof DatasetRegion)) {
            return false;
        }
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

    public boolean removeOverlapping(Iterable<DatasetRegion> regions) {
        Iterator<DatasetRegion> iter = regions.iterator();
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
        updateAllListeners();
        return newRegion;
    }

    public void setIntegral(double value) {
        integral = value;
        updateAllListeners();
    }

    public double getIntegral() {
        return integral;
    }

    public void setMax(double value) {
        max = value;
        updateAllListeners();
    }

    public double getMax() {
        return max;
    }

    public void setMin(double value) {
        min = value;
        updateAllListeners();
    }

    public double getMin() {
        return min;
    }

    public int[] getMaxLocation() {
        return maxLocation;
    }

    public boolean isAuto() {
        return isAuto;
    }

    public String getAutoText() {
        return isAuto ? "Auto" : "Manual";
    }

    public void setAuto(boolean value) {
        isAuto = value;
        updateAllListeners();
    }

    public void measure(DatasetBase dataset) throws IOException {
        int[] pt = new int[dataset.getNDim()];
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
            if (value > max) {
                max = value;
                maxLocation = pt.clone();
            }
            sum += value;
        }
        setIntegral(sum);
    }

    public static DatasetRegion findClosest(Iterable<DatasetRegion> regions, double ppm, int dim) {
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
