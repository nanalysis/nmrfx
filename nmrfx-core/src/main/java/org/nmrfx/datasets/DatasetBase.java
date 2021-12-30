package org.nmrfx.datasets;

import org.apache.commons.lang3.StringUtils;
import org.nmrfx.math.VecBase;
import org.nmrfx.project.ProjectBase;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatasetBase {

    //    private static final Logger LOGGER = LogManager.getLogger();
    //    static {
    //        try {
    //            logger.addHandler(new FileHandler("%t/nmrview%g.log"));
    //        } catch (IOException ioE) {
    //        }
    //    }
    public final static int NV_HEADER_SIZE = 2048;
    public final static int UCSF_HEADER_SIZE = 180;
    public final static int LABEL_MAX_BYTES = 16;
    public final static int SOLVENT_MAX_BYTES = 24;
    protected static List<DatasetListener> observers = new ArrayList<>();
    public DatasetLayout layout = null;
    /**
     *
     */
    public FFORMAT fFormat = FFORMAT.NMRVIEW;
    protected VecBase vecMat = null;
    protected String fileName;
    protected String canonicalName;
    protected String title;
    protected File file = null;
    protected int nDim;
    protected int[] strides;
    protected int[] fileDimSizes;
    protected int[] size;
    protected int[] vsize;
    protected int[] vsize_r;
    protected int[] tdSize;
    protected int[] zfSize;
    protected int[] extFirst;
    protected int[] extLast;
    protected long fileSize;
    protected double[] sf;
    protected double[] sw;
    protected double[] refPt;
    protected double[] refValue;
    protected double[] ph0;
    protected double[] ph1;
    protected double[] sw_r;
    protected double[] refPt_r;
    protected double[] refValue_r;
    protected double[] ph0_r;
    protected double[] ph1_r;
    protected int[] refUnits;
    protected double[] foldUp;
    protected double[] foldDown;
    protected String[] label;
    protected String[] dlabel;
    protected Nuclei[] nucleus;
    protected boolean[] complex;
    protected boolean[] complex_r;
    protected boolean[] freqDomain;
    protected boolean[] freqDomain_r;
    protected boolean[] axisReversed;
    protected double[][] rmsd;
    protected int posneg;
    protected double lvl;
    protected double scale = 1.0;
    protected int rdims;
    protected Double noiseLevel = null;
    protected double[][] values;
    String details = "";
    TreeSet<DatasetRegion> regions;
    protected DatasetStorageInterface dataFile = null;
    private boolean lvlSet = false;
    private double norm = 1.0;
    private String solvent = null;
    private double temperature = 0.0;
    private int dataType = 0;
    private HashMap properties = new HashMap();
    private String posColor = "black";
    private String negColor = "red";
    private boolean littleEndian = false;
    private boolean gotByteOrder = false;

    public DatasetBase() {

    }

    public DatasetBase(String fullName, String name, boolean writable, boolean useCacheFile) throws IOException {
        file = new File(fullName);

        String newName;

        if ((name == null) || name.equals("") || name.equals(fullName)) {
            newName = file.getName();
        } else {
            newName = name;
        }
        newName = newName.replace(' ', '_');

        canonicalName = file.getCanonicalPath();
        fileName = newName;

        if (!file.exists()) {
            throw new IllegalArgumentException(
                    "File " + fullName + " doesn't exist");
        }
    }

    /**
     * Check if there is an open file with the specified name.
     *
     * @param name the name to check
     * @return true if there is an open file with that name
     */
    public static boolean checkExistingName(final String name) {
        return ProjectBase.getActive().isDatasetPresent(name);
    }

    /**
     * Check if there is an open file with the specified file path
     *
     * @param fullName the full path name to check
     * @return true if the file is open
     * @throws IOException if an I/O error occurs
     */
    public static boolean checkExistingFile(final String fullName) throws IOException {
        File file = new File(fullName);
        return ProjectBase.getActive().isDatasetPresent(file);
    }

    /**
     * Is data file in Big Endian mode
     *
     * @return true if Big Endian
     */
    public boolean isBigEndian() {
        return littleEndian == false;
    }

    /**
     * Is data file in Little Endian mode
     *
     * @return true if Little Endian
     */
    public boolean isLittleEndian() {
        return littleEndian == true;
    }

    /**
     * Set mode to be Big Endian. This does not change actual data file, so the
     * existing data format must be consistent with this.
     */
    public void setBigEndian() {
        littleEndian = false;
    }

    /**
     * Set mode to be Little Endian This does not change actual data file, so
     * the existing data format must be consistent with this.
     */
    public void setLittleEndian() {
        littleEndian = true;
    }

    /**
     * Set the byte order. This does not change the actual data file, so the
     * existing data format must be consistent with the specified value.
     *
     * @param order Byte Order
     */
    public final void setByteOrder(ByteOrder order) {
        littleEndian = order == ByteOrder.LITTLE_ENDIAN;
    }

    /**
     * Get the byte order for this dataset.
     *
     * @return the byte order
     */
    public ByteOrder getByteOrder() {
        return littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
    }

    public void close() {

    }

    /**
     * Set the number of dimensions for this dataset. Will reset all reference
     * information.
     *
     * @param nDim Number of dataset dimensions
     */
    public final void setNDim(int nDim) {
        this.nDim = nDim;
        setNDim();
    }

    /**
     * Will reset all reference fields so they are sized corresponding to
     * current dataset dimension.
     *
     */
    public final void setNDim() {
        strides = new int[nDim];
        fileDimSizes = new int[nDim];
        vsize = new int[nDim];
        vsize_r = new int[nDim];
        tdSize = new int[nDim];
        zfSize = new int[this.nDim];
        extFirst = new int[this.nDim];
        extLast = new int[this.nDim];
        sf = new double[nDim];
        sw = new double[nDim];
        sw_r = new double[nDim];
        refPt = new double[nDim];
        refPt_r = new double[nDim];
        refValue = new double[nDim];
        refValue_r = new double[nDim];
        refUnits = new int[nDim];
        ph0 = new double[nDim];
        ph0_r = new double[nDim];
        ph1 = new double[nDim];
        ph1_r = new double[nDim];
        label = new String[nDim];
        dlabel = new String[nDim];
        nucleus = new Nuclei[nDim];

        foldUp = new double[nDim];
        foldDown = new double[nDim];
        complex = new boolean[nDim];
        complex_r = new boolean[nDim];
        axisReversed = new boolean[nDim];
        freqDomain = new boolean[nDim];
        freqDomain_r = new boolean[nDim];
        rmsd = new double[nDim][];
        values = new double[nDim][];
    }

    /**
     * Initialize headers to default values based on currently set number of
     * dimensions
     */
    public final synchronized void newHeader() {
        sf = new double[nDim];
        sw = new double[nDim];
        sw_r = new double[nDim];
        refPt = new double[nDim];
        refPt_r = new double[nDim];
        refValue = new double[nDim];
        refValue_r = new double[nDim];
        refUnits = new int[nDim];
        ph0 = new double[nDim];
        ph0_r = new double[nDim];
        ph1 = new double[nDim];
        ph1_r = new double[nDim];

        foldUp = new double[nDim];
        foldDown = new double[nDim];
        complex = new boolean[nDim];
        complex_r = new boolean[nDim];
        axisReversed = new boolean[nDim];
        freqDomain = new boolean[nDim];
        freqDomain_r = new boolean[nDim];
        label = new String[nDim];
        dlabel = new String[nDim];
        nucleus = new Nuclei[nDim];
        rmsd = new double[nDim][];
        values = new double[nDim][];

        int i;

        for (i = 0; i < nDim; i++) {
            refUnits[i] = 3;
            sw[i] = 7000.0;
            sw_r[i] = 7000.0;
            sf[i] = 600.0;
            refPt[i] = getSizeReal(i) / 2;
            refPt_r[i] = getSizeReal(i) / 2;
            refValue[i] = 4.73;
            refValue_r[i] = 4.73;
            complex[i] = true;
            complex_r[i] = true;
            freqDomain[i] = false;
            freqDomain_r[i] = false;
            label[i] = "D" + i;
            nucleus[i] = null;
        }

        freqDomain[0] = true;
        freqDomain_r[0] = true;
        lvl = 0.0;
        scale = 1.0;
        rdims = nDim;
        posneg = 1;

        //rdims = 0;
        //theFile.dataType = 0;
    }

    public DatasetStorageInterface getDataFile() {
        return dataFile;
    }

    /**
     * Get the Vec object. Null if the dataset stores data in a data file,
     * rather than Vec object.
     *
     * @return Vec storing data or null if data file mode.
     */
    public VecBase getVec() {
        return null;
    }

    /**
     * Get the value of the dataset at a specified point.  The point is specified
     * as a raw index, ignoring whether the dataset is real or complex.
     *
     * @param pt indices of point to read
     * @return the dataset value
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if point is outside the range of dataset
     * ( less than 0 or greater than or equal to size)
     */
    public double readPointRaw(int[] pt) throws IOException, IllegalArgumentException {
        int i;

        if (vecMat != null) {
            i = pt[0];
            return vecMat.getReal(i) / scale;
        }

        for (i = 0; i < nDim; i++) {
            if (pt[i] < 0) {
                throw new IllegalArgumentException("point < 0 " + i + " " + pt[i]);
            } else if (pt[i] >= getSizeTotal(i)) {
                throw new IllegalArgumentException("point >= size " + i + " " + pt[i] + " " + getSizeTotal(i));
            }
        }
        return dataFile.getFloat(pt) / scale;

    }

    /**
     * Get the value of the dataset at a specified point.  The point index is specified in
     * complex points if the dataset is complex, and real points if it is real.
     *
     * @param pt indices of point to read
     * @return the dataset value
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if point is outside the range of dataset
     *                                  ( less than 0 or greater than or equal to size)
     */
    public double readPoint(int[] pt) throws IOException, IllegalArgumentException {
        int i;

        if (vecMat != null) {
            i = pt[0];
            return vecMat.getReal(i) / scale;
        }
        int[] pt2 = pt.clone();
        for (i = 0; i < nDim; i++) {
            if (pt[i] < 0) {
                throw new IllegalArgumentException("point < 0 " + i + " " + pt[i]);
            } else if (pt[i] >= getSizeReal(i)) {
                throw new IllegalArgumentException("point >= size " + i + " " + pt[i] + " " + getSizeReal(i));
            }
            pt2[i] = pt[i];
            if (complex[i]) {
                pt2[i] *= 2;
            }
            if (axisReversed[i]) {
                rPt[i] = getSizeTotal(i) - 1 - rPt[i];
            }
        }
        return dataFile.getFloat(pt2) / scale;
    }

    /**
     * Get the value of the dataset at a specified point.  The point is specified
     * as a raw index, ignoring whether the dataset is real or complex.
     *
     * @param pt indices of point to read
     * @param dim dimension indices that used for the point values
     * @return the dataset value
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if point is outside range of dataset (
     * less than 0 or greater than or equal to size)
     */
    public double readPointRaw(int[] pt, int[] dim) throws IOException, IllegalArgumentException {
        int i;

        if (vecMat != null) {
            i = pt[0];
            return vecMat.getReal(i) / scale;
        }

        for (i = 0; i < nDim; i++) {
            if (pt[i] < 0) {
                throw new IllegalArgumentException("pointd < 0 " + i + " " + pt[i]);
            } else if (pt[i] >= getSizeTotal(dim[i])) {
                throw new IllegalArgumentException("pointd >= size " + i + " " + dim[i] + " " + pt[i] + " " + getSizeTotal(dim[i]));
            }
        }
        int[] rPt = new int[nDim];
        for (i = 0; i < nDim; i++) {
            rPt[dim[i]] = pt[i];
        }
        return dataFile.getFloat(rPt) / scale;
    }
    /**
     * Get the value of the dataset at a specified point.  The point index is specified in
     * complex points if the dataset is complex, and real points if it is real.
     *
     * @param pt indices of point to read
     * @param dim dimension indices that used for the point values
     * @return the dataset value
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if point is outside range of dataset (
     * less than 0 or greater than or equal to size)
     */
    public double readPoint(int[] pt, int[] dim) throws IOException, IllegalArgumentException {
        int i;

        if (vecMat != null) {
            i = pt[0];
            return vecMat.getReal(i) / scale;
        }

        for (i = 0; i < nDim; i++) {
            if (pt[i] < 0) {
                throw new IllegalArgumentException("pointd < 0 " + i + " " + pt[i]);
            } else if (pt[i] >= getSizeReal(dim[i])) {
                throw new IllegalArgumentException("pointd >= size " + i + " " + dim[i] + " " + pt[i] + " " + getSizeReal(dim[i]));
            }
        }
        int[] rPt = new int[nDim];
        for (i = 0; i < nDim; i++) {
            rPt[dim[i]] = pt[i];
            if (complex[dim[i]]) {
                rPt[dim[i]] *= 2;
            }
            if (axisReversed[dim[i]]) {
                rPt[dim[i]] = getSizeTotal(dim[i]) - 1 - rPt[dim[i]];
            }
        }
        return dataFile.getFloat(rPt) / scale;

    }
    /**
     * Write a value into the dataset at the specified point
     *
     * @param pt indices of point to write
     * @param value to write
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if point is outside range of dataset (
     * less than 0 or greater than or equal to size)
     */
    public void writePoint(int[] pt, double value) throws IOException, IllegalArgumentException {
        int i;

        if (vecMat != null) {
            i = pt[0];
            vecMat.setReal(i, value * scale);
        } else {
            for (i = 0; i < nDim; i++) {
                if (pt[i] < 0) {
                    throw new IllegalArgumentException("point < 0 " + i + " " + pt[i]);
                } else if (pt[i] >= getSizeTotal(i)) {
                    throw new IllegalArgumentException("point >= size " + i + " " + pt[i] + " " + getSizeTotal(i));
                }
            }
            dataFile.setFloat((float) (value * scale), pt);
        }
    }

    /**
     * Return the Dataset object with the specified name.
     *
     * @param fileName name of Dataset to find
     * @return the Dataset or null if it doesn't exist
     */
    synchronized public static DatasetBase getDataset(String fileName) {
        if (fileName == null) {
            return null;
        } else {
            return (DatasetBase) ProjectBase.getActive().getDataset(fileName);
        }
    }

    /**
     * Get a a name that can be used for the file that hasn't already been used
     *
     * @param fileName The root part of the name. An integer number will be
     * appended so that the name is unique among open files.
     * @return the new name
     */
    public static String getUniqueName(final String fileName) {
        String newName;
        int dot = fileName.lastIndexOf('.');
        String ext = "";
        String rootName = fileName;
        List<String> datasetNames = ProjectBase.getActive().getDatasetNames();
        if (dot != -1) {
            ext = rootName.substring(dot);
            rootName = rootName.substring(0, dot);
        }
        int index = 0;
        do {
            index++;
            newName = rootName + "_" + index + ext;
        } while (datasetNames.contains(newName));
        return newName;
    }

    public static double foldPPM(double ppm, double[] foldLimits) {
        double min = foldLimits[0];
        double max = foldLimits[1];
        if (min > max) {
            double hold = min;
            min = max;
            max = hold;
        }
        if ((ppm < min) || (ppm > max)) {
            double fDelta = max - min;
            if (min != max) {
                while (ppm > max) {
                    ppm -= fDelta;
                }
                while (ppm < min) {
                    ppm += fDelta;
                }
            }
        }
        return ppm;
    }

    /**
     * Return a list of the open datasets
     *
     * @return List of datasets.
     */
    synchronized public static Collection<DatasetBase> datasets() {
        return ProjectBase.getActive().getDatasets();
    }

    /**
     * Return a Set containing all the property names used by open datasets
     *
     * @return TreeSet containing the property names
     */
    public static TreeSet getPropertyNames() {
        TreeSet<String> nameSet = new TreeSet<>();
        Collection<DatasetBase> datasets = ProjectBase.getActive().getDatasets();

        for (DatasetBase dataset : datasets) {
            Iterator iter = dataset.properties.keySet().iterator();
            while (iter.hasNext()) {
                String propName = (String) iter.next();
                nameSet.add(propName);
            }
        }
        return nameSet;
    }

    public static void setMinimumTitles() {
        String[] names = new String[DatasetBase.datasets().size()];
        int i = 0;
        for (DatasetBase dataset : DatasetBase.datasets()) {
            names[i++] = dataset.getName();
        }
        String prefix = StringUtils.getCommonPrefix(names);
        System.out.println("prefix " + prefix);
        DatasetBase.datasets().forEach((dataset) -> {
            String name = dataset.getName();
            String title = StringUtils.removeStart(name, prefix);
            title = StringUtils.removeEndIgnoreCase(title, ".nv");
            System.out.println("title " + title);
            dataset.setTitle(title);
        });
    }

    /**
     * Set the type of the data values. At present, only single precision float
     * values are used in the dataset. This is indicated with a return value of
     * 0.
     *
     * @param value the datatype to set
     */
    public final void setDataType(int value) {
        dataType = value;
    }

    /**
     * Get the canonical file path for this Dataset
     *
     * @return String object, null if data stored in Vec
     */
    public String getCanonicalFile() {
        return canonicalName;
    }

    /**
     * Get the file name
     *
     * @return the file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Get the type of the data values. At present, only single precision float
     * values are used in the dataset. This is indicated with a return value of
     * 0.
     *
     * @return the data value, 0 for single precision floats
     */
    public int getDataType() {
        return dataType;
    }

    protected void removeFile(String datasetName) {
        ProjectBase.getActive().removeDataset(datasetName);
        for (DatasetListener observer : observers) {
            try {
                observer.datasetRemoved(this);
            } catch (RuntimeException e) {
            }
        }

    }

    /**
     * Change the name (and title) of this dataset
     *
     * @param newName the name to be used.
     */
    synchronized public void rename(String newName) {
        boolean removed = ProjectBase.getActive().removeDataset(fileName);
        if (removed) {
            fileName = newName;
            title = fileName;
            ProjectBase.getActive().addDataset(this, newName);
            for (DatasetListener observer : observers) {
                try {
                    observer.datasetRenamed(this);
                } catch (RuntimeException e) {
                    // FIXME log this!
                    observers.remove(observer);
                }
            }
        }
    }

    /**
     * Get the file name of this dataset
     *
     * @return file name
     */
    public String getName() {
        return fileName;
    }

    /**
     * Convert width in points to width in PPM
     *
     * @param iDim dataset dimension index
     * @param pt width to convert
     * @return width in ppm
     */
    public double ptWidthToPPM(int iDim, double pt) {
        return ((pt * (getSw(iDim) / size(iDim))) / getSf(iDim));
    }

    /**
     * Convert width in Hz to width in points
     *
     * @param iDim dataset dimension index
     * @param hz width in Hz
     * @return width in points
     */
    public double hzWidthToPoints(int iDim, double hz) {
        return (hz / getSw(iDim) * size(iDim));
    }

    /**
     * Convert width in points to width in Hz
     *
     * @param iDim dataset dimension index
     * @param pts width in points
     * @return width in Hz
     */
    public double ptWidthToHz(int iDim, double pts) {
        return (pts / size(iDim) * getSw(iDim));
    }

    /**
     * Convert dataset position in points to position in PPM
     *
     * @param iDim dataset dimension index
     * @param pt position in points
     * @return position in PPM
     */
    public double pointToPPM(int iDim, double pt) {
        double ppm = 0.0;
        double aa;

        aa = (getSf(iDim) * size(iDim));

        if (aa == 0.0) {
            ppm = 0.0;

            return ppm;
        }

        if (getRefUnits(iDim) == 3) {
            ppm = (-(pt - refPt[iDim]) * (getSw(iDim) / aa)) + getRefValue(iDim);
        } else if (getRefUnits(iDim) == 1) {
            ppm = pt + 1;
        }

        return (ppm);
    }

    /**
     * Convert dataset position in PPM to rounded position in points
     *
     * @param iDim dataset dimension index
     * @param ppm position in PPM
     * @return position in points
     */
    public int ppmToPoint(int iDim, double ppm) {
        int pt = 0;

        if (iDim < refUnits.length) {
            if (getRefUnits(iDim) == 3) {
                pt = (int) (((getRefValue(iDim) - ppm) * ((getSf(iDim) * size(iDim)) / getSw(iDim)))
                        + getRefPt(iDim) + 0.5);
            } else if (getRefUnits(iDim) == 1) {
                pt = (int) (ppm - 0.5);
            }

            if (pt < 0) {
                pt = 0;
            }

            if (pt > (size(iDim) - 1)) {
                pt = size(iDim) - 1;
            }
        }

        return (pt);
    }

    /**
     * Convert a chemical shift to a point position. The point is always aliased
     * so that it is within the size of the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param ppm position in PPM
     * @return position in points
     */
    public int ppmToFoldedPoint(int iDim, double ppm) {
        double pt = 0;

        if (iDim < refUnits.length) {
            if (getRefUnits(iDim) == 3) {
                pt = ((getRefValue(iDim) - ppm) * ((getSf(iDim) * size(iDim)) / getSw(iDim)))
                        + getRefPt(iDim);
            } else if (getRefUnits(iDim) == 1) {
                pt = ppm;
            }
            pt = fold(iDim, pt);
        }

        return ((int) (pt + 0.5));
    }

    /**
     * Convert dataset position in PPM to position in points.
     *
     * @param iDim dataset dimension index
     * @param ppm position in PPM
     * @return position in points
     */
    public double ppmToDPoint(int iDim, double ppm) {
        double pt = 0;

        if (getRefUnits(iDim) == 3) {
            pt = ((getRefValue(iDim) - ppm) * ((getSf(iDim) * size(iDim)) / getSw(iDim)))
                    + getRefPt(iDim);
        } else if (getRefUnits(iDim) == 1) {
            pt = ppm;
        }

        return (pt);
    }

    /**
     * Convert dataset position in PPM to position in Hz
     *
     * @param iDim dataset dimension index
     * @param ppm position in PPM
     * @return position in Hz
     */
    public double ppmToHz(int iDim, double ppm) {
        double pt = ppmToDPoint(iDim, ppm);
        double hz = pt / size(iDim) * getSw(iDim);

        return (hz);
    }

    /**
     * Convert dataset position in points to position in Hz
     *
     * @param iDim dataset dimension index
     * @param pt position in points
     * @return position in Hz
     */
    public double pointToHz(int iDim, double pt) {
        double hz = pt / size(iDim) * getSw(iDim);

        return (hz);
    }

    /**
     * Get size of dataset along specified dimension
     *
     * @param iDim dataset dimension index
     * @return size of dataset dimension
     */
    synchronized public int size(int iDim) {
        if ((iDim == 0) && (vecMat != null)) {
            setSizeReal(0, vecMat.getSize());

            return (vecMat.getSize());
        } else {
            return (getSizeReal(iDim));
        }
    }

    int fold(int iDim, int pt) {
        while (pt < 0) {
            pt += getSizeReal(iDim);
        }
        while (pt >= getSizeReal(iDim)) {
            pt -= getSizeReal(iDim);
        }
        return pt;
    }

    double fold(int iDim, double pt) {
        while (pt < 0) {
            pt += getSizeReal(iDim);
        }
        while (pt >= getSizeReal(iDim)) {
            pt -= getSizeReal(iDim);
        }
        return pt;
    }

    /**
     * Get the valid size for the specified dimension. Valid size is the index
     * of the last vector written to, plus 1.
     *
     * @param iDim Dimension number.
     * @return the valid size of this dimension
     */
    public int getVSize(int iDim) {
        final int value;
        if (vecMat == null) {
            value = vsize[iDim];
        } else {
            value = vecMat.getSize();
        }
        return value;
    }

    /**
     * Get the valid size for the specified dimension after last parameter sync.
     * Valid size is the index of the last vector written to, plus 1.
     *
     * @param iDim Dimension number.
     * @return the valid size of this dimension
     */
    public int getVSize_r(int iDim) {
        final int value;
        if (vecMat == null) {
            value = vsize_r[iDim];
        } else {
            value = vecMat.getSize();
        }
        return value;
    }

    /**
     * Get the File object corresponding to the data file for this Dataset
     *
     * @return File object, null if data stored in Vec
     */
    public File getFile() {
        return file;
    }

    public void readParFile() {
        DatasetParameterFile parFile = new DatasetParameterFile(this, layout);
        parFile.readFile();
    }

    public void writeParFile(String fileName) {
        DatasetParameterFile parFile = new DatasetParameterFile(this, layout);
        parFile.writeFile(fileName);
    }

    public void writeParFile() {
        if (file != null) {
            DatasetParameterFile parFile = new DatasetParameterFile(this, layout);
            parFile.writeFile();
        }
    }

    /**
     * Get the size of the dataset along the specified dimension. If the data is real
     * this is the number of points.  If the data is complex it is the number of
     * real plus imaginary points (twice the number of complex points).
     *
     * @param iDim Dataset dimension index
     * @return the size
     */
    public int getSizeTotal(int iDim) {
        return size[iDim];
    }

    /**
     * Set the size of the dataset along the specified dimension.  If the data is real
     *      * this is the number of points.  If the data is complex it is the number of
     *      * real plus imaginary points (twice the number of complex points).
     *
     * @param iDim Dataset dimension index
     * @param newSize the size to set
     */
    public void setSizeTotal(final int iDim, final int newSize) {
        size[iDim] = newSize;
    }

    /**
     * Get the size of the dataset along the specified dimension. If the data is real
     * this is the number of points.  If the data is complex it is the number of
     * complex points).
     *
     * @param iDim Dataset dimension index
     * @return the size
     */
    public int getSizeReal(int iDim) {
        return getSizeTotal(iDim)/ (getComplex(iDim) ? 2 : 1);
    }

    /**
     * Set the size of the dataset along the specified dimension.  If the data is real
     *      * this is the number of points.  If the data is complex it is the number of
     *      * complex points).
     *
     * @param iDim Dataset dimension index
     * @param newSize the size to set
     */
    public void setSizeReal(final int iDim, final int newSize) {
       setSizeTotal(iDim, newSize * (getComplex(iDim) ? 2 : 1));
    }

    /**
     * Get the size of the time domain data along the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return the tdSize
     */
    public int getTDSize(int iDim) {
        final int value;
        if (vecMat == null) {
            value = tdSize[iDim];
        } else {
            value = vecMat.getTDSize();
        }
        return value;
    }

    /**
     * Set the size of the time domain data along the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param size the tdSize to set
     */
    public void setTDSize(final int iDim, final int size) {
        this.tdSize[iDim] = size;
    }

    /**
     * Get the size of zero-filled data along the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return the zfSize
     */
    public int getZFSize(int iDim) {
        final int value;
        if (vecMat == null) {
            value = zfSize[iDim];
        } else {
            value = vecMat.getZFSize();
        }
        return value;
    }

    /**
     * Get the size of zero-filled data along the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return the zfSize
     */
    public int getExtFirst(int iDim) {
        final int value;
        if (vecMat == null) {
            value = extFirst[iDim];
        } else {
            value = vecMat.getExtFirst();
        }
        return value;
    }

    /**
     * Get the size of zero-filled data along the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return the zfSize
     */
    public int getExtLast(int iDim) {
        final int value;
        if (vecMat == null) {
            value = extLast[iDim];
        } else {
            value = vecMat.getExtLast();
        }
        return value;
    }

    /**
     * Set the size of the zero-filled time domain data along the specified
     * dimension.
     *
     * @param iDim Dataset dimension index
     * @param size the zfSize to set
     */
    public void setZFSize(final int iDim, final int size) {
        this.zfSize[iDim] = size;
    }

    /**
     * Set the first point extracted region.
     *
     * @param iDim Dataset dimension index
     * @param point the point to set
     */
    public void setExtFirst(final int iDim, final int point) {
        this.extFirst[iDim] = point;
    }

    /**
     * Set the last point extracted region.
     *
     * @param iDim Dataset dimension index
     * @param point the point to set
     */
    public void setExtLast(final int iDim, final int point) {
        this.extLast[iDim] = point;
    }

    /**
     * Get an array containing the sizes of all dimensions.
     *
     * @return the size array
     */
    public int[] getSizes() {
        int[] sizes = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            sizes[i] = getSizeTotal(i);
        }
        return sizes;
    }

    /**
     * Get the spectrometer frequency for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return the spectrometer frequency
     */
    public double getSf(final int iDim) {
        final double value;
        if (vecMat == null) {
            value = sf[iDim];
        } else {
            value = vecMat.centerFreq;
        }
        return value;
    }

    /**
     * Set the spectrometer frequency for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param sf the sf to set
     */
    public void setSf(final int iDim, final double sf) {
        this.sf[iDim] = sf;
        if (vecMat != null) {
            vecMat.centerFreq = sf;
        }
    }

    /**
     * Get the sweep width for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return the sweep width
     */
    public double getSw(final int iDim) {
        final double value;
        if (vecMat == null) {
            value = sw[iDim];
        } else {
            value = 1.0 / vecMat.dwellTime;
        }
        return value;
    }

    /**
     * Set the sweep width for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param sw the sweep width to set
     */
    public void setSw(final int iDim, final double sw) {
        this.sw[iDim] = sw;
        if (vecMat != null) {
            vecMat.dwellTime = 1.0 / sw;
        }
    }

    /**
     * Get the dataset point at which the reference value is set for the
     * specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return the reference point
     */
    public double getRefPt(final int iDim) {
        final double value;
        if (vecMat == null) {
            value = refPt[iDim];
        } else {
            value = refPt[0];
        }
        return value;
    }

    /**
     * Set the dataset point at which the reference value is set for the
     * specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param refPt the reference point to set
     */
    public void setRefPt(final int iDim, final double refPt) {
        this.refPt[iDim] = refPt;
        if (vecMat != null) {
            double delRef = getRefPt(iDim) * getSw(iDim) / getSf(iDim) / getSizeReal(iDim);
            vecMat.refValue = refValue[iDim] + delRef;
        }

    }

    /**
     * Get the reference value for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return the reference value.
     */
    public double getRefValue(final int iDim) {
        final double value;
        if (vecMat == null) {
            value = refValue[iDim];
        } else {
            double delRef = getRefPt(iDim) * getSw(iDim) / getSf(iDim) / getSizeReal(iDim);
            value = vecMat.refValue - delRef;
        }
        return value;
    }

    /**
     * Set the reference value for the specified dimension
     *
     * @param iDim Dataset dimension index
     * @param refValue the reference value to set
     */
    public void setRefValue(final int iDim, final double refValue) {
        this.refValue[iDim] = refValue;
        if (vecMat != null) {
            double delRef = getRefPt(iDim) * getSw(iDim) / getSf(iDim) / getSizeReal(iDim);
            vecMat.refValue = refValue + delRef;
        }
    }

    /**
     * Get the zero order phase parameter that was used in processing the
     * specified dimension
     *
     * @param iDim Dataset dimension index
     * @return the phase value
     */
    public double getPh0(final int iDim) {
        final double value;
        if (vecMat == null) {
            value = ph0[iDim];
        } else {
            value = vecMat.getPH0();
        }
        return value;
    }

    /**
     * Set the zero order phase parameter that was used in processing the
     * specified dimension
     *
     * @param iDim Dataset dimension index
     * @param ph0 the phase value to set
     */
    public void setPh0(final int iDim, final double ph0) {
        this.ph0[iDim] = ph0;
        if (vecMat != null) {
            vecMat.setPh0(ph0);
        }
    }

    /**
     * Get the first order phase parameter that was used in processing the
     * specified dimension
     *
     * @param iDim Dataset dimension index
     * @return the first order phase
     */
    public double getPh1(final int iDim) {
        final double value;
        if (vecMat == null) {
            value = ph1[iDim];
        } else {
            value = vecMat.getPH1();
        }
        return value;
    }

    /**
     * Set the first order phase parameter that was used in processing the
     * specified dimension
     *
     * @param iDim Dataset dimension index
     * @param ph1 the first order phase
     */
    public void setPh1(final int iDim, final double ph1) {
        this.ph1[iDim] = ph1;
        if (vecMat != null) {
            vecMat.setPh1(ph1);
        }
    }

    /**
     * Get the sweep width for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return the sweep width
     */
    public double getSw_r(final int iDim) {
        final double value;
        if (vecMat == null) {
            value = sw_r[iDim];
        } else {
            value = 1.0 / vecMat.dwellTime;
        }
        return value;
    }

    /**
     * Set the sweep width for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param sw_r the sweep width to set
     */
    public void setSw_r(final int iDim, final double sw_r) {
        this.sw_r[iDim] = sw_r;
        if (vecMat != null) {
            vecMat.dwellTime = 1.0 / sw_r;
        }
    }

    /**
     * Get the dataset point at which the reference value is set for the
     * specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return the reference point
     */
    public double getRefPt_r(final int iDim) {
        final double value;
        if (vecMat == null) {
            value = refPt_r[iDim];
        } else {
            value = refPt_r[0];
        }
        return value;
    }

    /**
     * Set the dataset point at which the reference value is set for the
     * specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param refPt_r the reference point to set
     */
    public void setRefPt_r(final int iDim, final double refPt_r) {
        this.refPt_r[iDim] = refPt_r;
        if (vecMat != null) {
            double delRef = getRefPt_r(iDim) * getSw(iDim) / getSf(iDim) / getSizeReal(iDim);
            vecMat.refValue = refValue_r[iDim] + delRef;
        }
    }

    /**
     * Get the reference value for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return the reference value
     */
    public double getRefValue_r(final int iDim) {
        final double value;
        if (vecMat == null) {
            value = refValue_r[iDim];
        } else {
            double delRef = getRefPt_r(iDim) * getSw_r(iDim) / getSf(iDim) / getSizeReal(iDim);
            value = vecMat.refValue - delRef;
        }
        return value;
    }

    /**
     * Set the reference value for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param refValue_r the reference value to set
     */
    public void setRefValue_r(final int iDim, final double refValue_r) {
        this.refValue_r[iDim] = refValue_r;
        if (vecMat != null) {
            double delRef = getRefPt_r(iDim) * getSw_r(iDim) / getSf(iDim) / getSizeReal(iDim);
            vecMat.refValue = refValue_r + delRef;
        }
    }

    /**
     * Set the folding up value for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param value the folding value to set
     */
    public void setFoldUp(final int iDim, final double value) {
        foldUp[iDim] = value;
    }

    public double getFoldUp(int iDim) {
        return foldUp[iDim];
    }

    public double getFoldDown(int iDim) {
        return foldDown[iDim];
    }

    public double[] getLimits(int iDim) {
        return new double[]{
            pointToPPM(iDim, size(iDim) - 1),
            pointToPPM(iDim, 0)
        };
    }

    /**
     * Set the folding down value for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param value the folding value to set
     */
    public void setFoldDown(final int iDim, final double value) {
        foldDown[iDim] = value;
    }

    /**
     * Get the zero order phase parameter that was used in processing the
     * specified dimension
     *
     * @param iDim Dataset dimension index
     * @return the zeroth order phase
     */
    public double getPh0_r(final int iDim) {
        final double value;
        if (vecMat == null) {
            value = ph0_r[iDim];
        } else {
            value = vecMat.getPH0();
        }
        return value;
    }

    /**
     * Set the zero order phase parameter that was used in processing the
     * specified dimension
     *
     * @param iDim Dataset dimension index
     * @param ph0_r the zeroth order phase to set
     */
    public void setPh0_r(final int iDim, final double ph0_r) {
        this.ph0_r[iDim] = ph0_r;
        if (vecMat != null) {
            vecMat.setPh0(ph0_r);
        }
    }

    /**
     * Get the first order phase parameter that was used in processing the
     * specified dimension
     *
     * @param iDim Dataset dimension index
     * @return the first order phase
     */
    public double getPh1_r(final int iDim) {
        final double value;
        if (vecMat == null) {
            value = ph1_r[iDim];
        } else {
            value = vecMat.getPH1();
        }
        return value;
    }

    /**
     * Set the first order phase parameter that was used in processing the
     * specified dimension
     *
     * @param iDim Dataset dimension index
     * @param ph1_r the first order phase to set
     */
    public void setPh1_r(final int iDim, final double ph1_r) {
        this.ph1_r[iDim] = ph1_r;
        if (vecMat != null) {
            vecMat.setPh0(ph1_r);
        }
    }

    /**
     * Get the units used for the reference value
     *
     * @param iDim Dataset dimension index
     * @return the refUnits
     */
    public int getRefUnits(final int iDim) {
        return refUnits[iDim];
    }

    /**
     * Set the units used for the reference value
     *
     * @param iDim Dataset dimension index
     * @param refUnits the refUnits to set
     */
    public void setRefUnits(final int iDim, final int refUnits) {
        this.refUnits[iDim] = refUnits;
    }

    /**
     * Get the complex mode for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return true if data in the specified dimension is complex
     */
    public boolean getComplex(final int iDim) {
        final boolean value;
        if (vecMat == null) {
            value = complex[iDim];
        } else {
            value = vecMat.isComplex();
        }
        return value;
    }

    /**
     * Set the complex mode for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param complex the complex to set
     */
    public void setComplex(final int iDim, final boolean complex) {
        this.complex[iDim] = complex;
    }

    /**
     * Get the complex mode for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return true if data in the specified dimension is complex
     */
    public boolean getComplex_r(final int iDim) {
        final boolean value;
        if (vecMat == null) {
            value = complex_r[iDim];
        } else {
            value = vecMat.isComplex();
        }
        return value;
    }

    /**
     * Set the complex mode for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param complex_r the complex_r to set
     */
    public void setComplex_r(final int iDim, final boolean complex_r) {
        this.complex_r[iDim] = complex_r;
    }

    /**
     * Get the axis reversed mode for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return true if data in the specified dimension is written to file in reversed order
     */
    public boolean getAxisReversed(final int iDim) {
        return axisReversed[iDim];
    }

    /**
     * Set the axis reversed mode for the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param axisReversed the value to set
     */
    public void setAxisReversed(final int iDim, final boolean axisReversed) {
        this.axisReversed[iDim] = axisReversed;
    }


    /**
     * Get the frequency domain mode of the specified dimension
     *
     * @param iDim Dataset dimension index
     * @return true if the data in the specified dimension is in the frequency
     * domain
     */
    public boolean getFreqDomain(final int iDim) {
        final boolean value;
        if (vecMat == null) {
            value = freqDomain[iDim];
        } else {
            value = vecMat.getFreqDomain();
        }
        return value;
    }

    /**
     * Set the frequency domain mode of the specified dimension
     *
     * @param iDim Dataset dimension index
     * @param freqDomain value to set
     */
    public void setFreqDomain(final int iDim, final boolean freqDomain) {
        if (vecMat == null) {
            this.freqDomain[iDim] = freqDomain;
        } else {
            this.vecMat.setFreqDomain(freqDomain);
        }
    }

    /**
     * Get the frequency domain mode of the specified dimension
     *
     * @param iDim Dataset dimension index
     * @return true if the data in the specified dimension is in the frequency
     * domain
     */
    public boolean getFreqDomain_r(final int iDim) {
        final boolean value;
        if (vecMat == null) {
            value = freqDomain_r[iDim];
        } else {
            value = vecMat.getFreqDomain();
        }
        return value;
    }

    /**
     * Set the frequency domain mode of the specified dimension
     *
     * @param iDim Dataset dimension index
     * @param freqDomain_r the freqDomain_r to set
     */
    public void setFreqDomain_r(final int iDim, final boolean freqDomain_r) {
        this.freqDomain_r[iDim] = freqDomain_r;
    }

    /**
     * Get the display label to be used on the axis for this dataset dimension.
     *
     * @param iDim Dataset dimension index
     * @return the displayLabel
     */
    public String getDlabel(final int iDim) {
        if (dlabel[iDim] == null) {
            return (getStdLabel(iDim));
        } else {
            return dlabel[iDim];
        }
    }

    /**
     * Set the display label to be used on the axis for this dataset dimension.
     *
     * @param iDim Dataset dimension index
     * @param dlabel the display label to set
     */
    public void setDlabel(final int iDim, final String dlabel) {
        this.dlabel[iDim] = dlabel;
    }

    /**
     * Get the nucleus detected on this dimension of the dataset
     *
     * @param iDim Dataset dimension index
     * @return the nucleus
     */
    public Nuclei getNucleus(final int iDim) {
        String labelTest = label[iDim];
        if ((nucleus[iDim] == null)) {
            if (labelTest.length() == 0) {
                nucleus = Nuclei.findNuclei(sf);
            } else if ((labelTest.charAt(0) == 'F') && (labelTest.length() == 2) && (Character.isDigit(labelTest.charAt(1)))) {
                nucleus = Nuclei.findNuclei(sf);
            } else if ((labelTest.charAt(0) == 'D') && (labelTest.length() == 2) && (Character.isDigit(labelTest.charAt(1)))) {
                nucleus = Nuclei.findNuclei(sf);
            } else {
                for (Nuclei nucValue : Nuclei.values()) {
                    if (labelTest.contains(nucValue.getNameNumber())) {
                        nucleus[iDim] = nucValue;
                        break;
                    } else if (labelTest.contains(nucValue.getNumberName())) {
                        nucleus[iDim] = nucValue;
                        break;
                    } else if (labelTest.contains(nucValue.getName())) {
                        nucleus[iDim] = nucValue;
                        break;
                    }
                }
            }
        }
        if ((nucleus[iDim] == null)) {
            nucleus[iDim] = Nuclei.H1;
        }
        return nucleus[iDim];
    }

    /**
     * Set the nucleus that was used for detection of this dataset dimension
     *
     * @param iDim Dataset dimension index
     * @param nucleus the name of the nucleus to set
     */
    public void setNucleus(final int iDim, final String nucleus) {
        this.nucleus[iDim] = Nuclei.findNuclei(nucleus);
    }

    /**
     * Set the nucleus that was used for detection of this dataset dimension
     *
     * @param iDim Dataset dimension index
     * @param nucleus the nucleus to set
     */
    public void setNucleus(final int iDim, final Nuclei nucleus) {
        this.nucleus[iDim] = nucleus;
    }

    /**
     * Get value indicating whether default drawing mode should include positive
     * and/or negative contours. 0: no contours, 1: positive, 2: negative, 3:
     * both
     *
     * @return the mode
     */
    public int getPosneg() {
        return posneg;
    }

    /**
     * Set value indicating whether default drawing mode should include positive
     * and/or negative contours. 0: no contours, 1: positive, 2: negative, 3:
     * both
     *
     * @param posneg the posneg to set
     */
    public void setPosneg(int posneg) {
        this.posneg = posneg;
    }

    /**
     * Get the display level (contour level or 1D scale) to be used as a default
     * when displaying dataset.
     *
     * @return the default display level
     */
    public double getLvl() {
        return lvl;
    }

    /**
     * Set the display level (contour level or 1D scale) to be used as a default
     * when displaying dataset.
     *
     * @param lvl the display level to set
     */
    public void setLvl(double lvl) {
        this.lvl = lvl;
        if (lvl > 1.0e-6) {
            lvlSet = true;
        }
    }

    /**
     * Get whether lvl has been set (so that GUI programs can decide to do an
     * auto level)
     *
     * @return true if lvl value has been explicitly set.
     */
    public boolean isLvlSet() {
        return lvlSet;
    }

    /**
     * Get the scale value used to divide intensity values in dataset.
     *
     * @return the scale
     */
    public double getScale() {
        return scale;
    }

    /**
     * Set the scale value used to divide intensity values in dataset.
     *
     * @param scale the scale to set
     */
    public void setScale(double scale) {
        this.scale = scale;
    }

    /**
     * Get the norm value used to divide intensity values in dataset. This is
     * used to translate dataset intensities of regions into atom numbers.
     *
     * @return the norm
     */
    public double getNorm() {
        return norm;
    }

    /**
     * Set the norm value used to divide intensity values in dataset. This is
     * used to translate dataset intensities of regions into atom numbers.
     *
     * @param norm the norm to set
     */
    public void setNorm(double norm) {
        this.norm = norm;
    }

    /**
     * Get the default color to be used for positive contours and 1D vectors
     *
     * @return name of color
     */
    public String getPosColor() {
        return posColor;
    }

    /**
     * Set the default color to be used for positive contours and 1D vectors
     *
     * @param posColor name of the color
     */
    public void setPosColor(String posColor) {
        this.posColor = posColor;
    }

    /**
     * Get the default color to be used for negative contours
     *
     * @return name of color
     */
    public String getNegColor() {
        return negColor;
    }

    /**
     * Set the default color to be used for negative contours
     *
     * @param negColor name of the color
     */
    public void setNegColor(String negColor) {
        this.negColor = negColor;
    }

    /**
     * Get the stored noise level for this dataset
     *
     * @return noise level
     */
    public Double getNoiseLevel() {
        return noiseLevel == null ? null : noiseLevel / scale;
    }

    /**
     * Store a noise level for dataset
     *
     * @param level noise level
     */
    public void setNoiseLevel(Double level) {
        if (level != null) {
            noiseLevel = level * scale;
        }
    }

    public void setFreqDims(int n) {
        rdims = n;
    }

    /**
     * Some parameters (complex, refvalue, refpt, sw, phase,valid size) have two
     * copies. One that is set when writing to file, and one that is read from
     * file. This command synchronizes the two by copying the written values to
     * the read values.
     *
     * @param iDim Datatset dimension index
     */
    public void syncPars(int iDim) {
        setFreqDomain_r(iDim, getFreqDomain(iDim));

        if (getFreqDomain(iDim)) {
            setRefUnits(iDim, 3);
        }

        setComplex_r(iDim, getComplex(iDim));
        setRefValue_r(iDim, getRefValue(iDim));
        setRefPt_r(iDim, getRefPt(iDim));
        setSw_r(iDim, getSw(iDim));
        setPh0_r(iDim, getPh0(iDim));
        setPh1_r(iDim, getPh1(iDim));
        setVSize_r(iDim, getVSize(iDim));
    }

    /**
     * Get the number of data points i file
     *
     * @return the number of data points.
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Set valid size for specified dimension. Normally used to track number of
     * rows, columns etc. that have had valid data written to.
     *
     * @param iDim Dataset dimension index
     * @param newSize Valid size for dimension.
     */
    public void setVSize(int iDim, int newSize) {
        if ((iDim >= 0) && (iDim < nDim) && (newSize >= 0)) {
            vsize[iDim] = newSize;
        }
    }

    /**
     * Set valid size for specified dimension. Normally used to track number of
     * rows, columns etc. that have had valid data written to.
     *
     * @param iDim Dataset dimension index
     * @param newSize Valid size for dimension
     */
    public void setVSize_r(int iDim, int newSize) {
        if ((iDim >= 0) && (iDim < nDim) && (newSize >= 0)) {
            vsize_r[iDim] = newSize;
        }
    }

    /**
     * Get the name of the solvent.
     *
     * @return solvent name
     */
    public String getSolvent() {
        if (solvent == null) {
            return "";
        } else {
            return (solvent);
        }
    }

    /**
     * Set the name of the solvent.
     *
     * @param solvent Name of solvent
     */
    public void setSolvent(String solvent) {
        this.solvent = solvent;
    }

    /**
     * Get the temperature (K).
     *
     * @return temperature
     */
    public double getTempK() {
        return temperature;
    }

    /**
     * Set the temperature (K).
     *
     * @param temperature The temperature
     */
    public void setTempK(double temperature) {
        this.temperature = temperature;
    }

    /**
     * Get the title of the dataset.
     *
     * @return title of dataset
     */
    public String getTitle() {
        if (title == null) {
            return "";
        } else {
            return (title);
        }
    }

    /**
     * Get the title of the dataset.
     *
     * @param title The title to set
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Get a standard label for the specified dimension. The label is based on
     * the name of the nucleus detected on this dimension.
     *
     * @param iDim Dataset dimension index
     * @return The name of the nucleus
     */
    public String getStdLabel(final int iDim) {
        return "\u03B4 " + getNucleus(iDim).toLatexString();
    }

    public final void setStrides() {
        strides[0] = 1;
        for (int i = 1; i < nDim; i++) {
            strides[i] = strides[i - 1] * getSizeTotal(i - 1);
        }
    }

    /**
     * Get the properties used by this dataset
     *
     * @return A map containing property names as keys and properties as values.
     */
    public Map getPropertyList() {
        return properties;
    }

    /**
     * Add a new property to this file
     *
     * @param name The name of the property
     * @param value The value for the property
     */
    public void addProperty(String name, String value) {
        properties.put(name, value);
    }

    /**
     * Return the value for a specified property
     *
     * @param name The name of the property
     * @return the value for the property or empty string if property doesn't
     * exist
     */
    public String getProperty(String name) {
        String value = (String) properties.get(name);

        if (value == null) {
            value = "";
        }

        return value;
    }

    /**
     * Get the default color to be used in displaying dataset.
     *
     * @param getPositive true if the color for positive values should be
     * returned and false if the color for negative values should be returned
     * @return the color
     */
    public String getColor(boolean getPositive) {
        if (getPositive) {
            return posColor;
        } else {
            return negColor;
        }
    }

    /**
     * Set the default color.
     *
     * @param setPositive If true set the color for positive values, if false
     * set the value for negative values.
     * @param newColor the new color
     */
    public void setColor(boolean setPositive, String newColor) {
        if (setPositive) {
            posColor = newColor;
        } else {
            negColor = newColor;
        }
    }

    /**
     * Get the number of dimensions in this dataset.
     *
     * @return the number of dimensions
     */
    public int getNDim() {
        return nDim;
    }

    /**
     * Get the number of dimensions represent frequencies (as opposed to, for
     * example, relaxation time increments).
     *
     * @return the number of frequency dimensions.
     */
    public int getNFreqDims() {
        return rdims;
    }

    /**
     * Set the number of dimensions represent frequencies (as opposed to, for
     * example, relaxation time increments).
     *
     * @param rdims the number of frequency dimensions.
     */
    public void setNFreqDims(int rdims) {
        this.rdims = rdims;
    }

    /**
     * Set the label for the specified axis
     *
     * @param iDim Dataset dimension index
     * @param label the display label to set
     */
    public void setLabel(final int iDim, final String label) {
        this.label[iDim] = label;
    }

    /**
     * Get the label for the specified axis
     *
     * @param iDim Dataset dimension index
     * @return label
     */
    public String getLabel(final int iDim) {
        return this.label[iDim];
    }

    public int getDim(String testLabel) {
        int iDim = -1;
        for (int i = 0; i < label.length; i++) {
            if (label[i].equals(testLabel)) {
                iDim = i;
                break;
            }
        }
        return iDim;
    }

    public double[] getNValues(int nValues) {
        double[] pValues = null;
        boolean ok = false;
        for (int iDim = 0; iDim < getNDim(); iDim++) {
            pValues = getValues(iDim);
            if ((pValues != null) && (pValues.length == nValues)) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            pValues = null;
        }
        return pValues;
    }

    /**
     * Get the stored values for the specified dimension of this dataset
     *
     * @param iDim the dataset dimension
     * @return values
     */
    public double[] getValues(int iDim) {
        return values[iDim];
    }

    /**
     * Store a set of values for a dimension of dataset
     *
     * @param iDim the dataset dimension
     * @param values the values
     */
    public void setValues(int iDim, double[] values) {
        if ((iDim < 0) || (iDim >= nDim)) {
            throw new IllegalArgumentException("Invalid dimension in setValues");
        }
        if (values == null) {
            this.values[iDim] = null;
        } else {
            if (values.length != getSizeReal(iDim)) {
                throw new IllegalArgumentException("Number of values (" + values.length + ") must equal dimension size (" + getSizeReal(iDim) + ") for dim " + iDim);
            }
            this.values[iDim] = values.clone();
        }
    }

    /**
     * Store a set of values for a dimension of dataset
     *
     * @param iDim the dataset dimension
     * @param values the values
     */
    public void setValues(int iDim, List<Double> values) {
        if ((iDim < 0) || (iDim >= nDim)) {
            throw new IllegalArgumentException("Invalid dimension in setValues");
        }
        if ((values == null) || values.isEmpty()) {
            this.values[iDim] = null;
        } else {
            if (values.size() != getSizeReal(iDim)) {
                throw new IllegalArgumentException("Number of values (" + values.size() + ") must equal dimension size (" + getSizeReal(iDim) + ") for dim " + iDim);
            }
            this.values[iDim] = new double[values.size()];
            for (int i = 0; i < values.size(); i++) {
                this.values[iDim][i] = values.get(i);
            }
        }
    }

    /**
     * Set the size of the dataset along the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param size the size to set
     */
    public void setFileDimSize(final int iDim, final int size) {
        this.fileDimSizes[iDim] = size;
    }

    /**
     * Get the size of the dataset along the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return the size
     */
    public int getFileDimSize(int iDim) {
        int value = fileDimSizes[iDim];
        if (value == 0) {
            value = layout.getSize(iDim);
        }
        return value;
    }

    public boolean hasLayout() {
        return layout != null;
    }

    synchronized public RegionData analyzeRegion(int[][] pt, int[] cpt, double[] width, int[] dim)
            throws IOException {
        return null;
    }

    public void setRegions(TreeSet<DatasetRegion> regions) {
        this.regions = regions;
    }

    public TreeSet<DatasetRegion> getRegions() {
        return regions;
    }

    public DatasetRegion addRegion(double min, double max) {
        TreeSet<DatasetRegion> regions = getRegions();
        if (regions == null) {
            regions = new TreeSet<>();
            setRegions(regions);
        }

        DatasetRegion newRegion = new DatasetRegion(min, max);
        newRegion.removeOverlapping(regions);
        regions.add(newRegion);
        try {
            newRegion.measure(this);
        } catch (IOException ex) {
            Logger.getLogger(DatasetBase.class.getName()).log(Level.SEVERE, null, ex);
        }
        return newRegion;
    }

    /**
     * Enum for possible file types consistent with structure available in the
     * Dataset format
     */
    public static enum FFORMAT {

        /**
         * Indicates dataset is in NMRView format
         */
        NMRVIEW,
        /**
         * Indicates dataset is in UCSF format
         */
        UCSF;
    }
}
