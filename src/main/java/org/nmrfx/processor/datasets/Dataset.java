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
package org.nmrfx.processor.datasets;

import org.nmrfx.processor.math.Matrix;
import org.nmrfx.processor.math.MatrixND;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;
import org.nmrfx.processor.operations.Util;
import java.io.*;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.descriptive.rank.PSquarePercentile;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.math.MatrixType;
import org.nmrfx.processor.operations.IDBaseline2;
import org.nmrfx.processor.processing.LineShapeCatalog;
import org.renjin.sexp.AttributeMap;
import org.renjin.sexp.DoubleVector;
import org.renjin.sexp.DoubleArrayVector;
import org.renjin.sexp.IntArrayVector;
import org.renjin.sexp.SEXP;
import org.nmrfx.project.Project;

/**
 * Instances of this class represent NMR datasets. The class is typically used
 * for processed NMR spectra (rather than FID data). The actual data values are
 * either stored in random access file, a memory based representation of a file,
 * or an NMRView Vec object.
 *
 * @author brucejohnson
 */
public class Dataset extends DoubleVector implements Comparable<Dataset> {

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
    static boolean useCacheFile = false;

    DatasetStorageInterface dataFile = null;
    DatasetLayout layout = null;
    String details = "";
    private Vec vecMat = null;
    private String fileName;
    private String canonicalName;
    private String title;
    private File file = null;
    private int nDim;
    private int[] strides;
    private int[] fileDimSizes;
    private int[] vsize;
    private int[] vsize_r;
    private int[] tdSize;
    private int[] zfSize;
    private int[] extFirst;
    private int[] extLast;
    private long fileSize;
    private double[] sf;
    private double[] sw;
    private double[] refPt;
    private double[] refValue;
    private double[] ph0;
    private double[] ph1;
    private double[] sw_r;
    private double[] refPt_r;
    private double[] refValue_r;
    private double[] ph0_r;
    private double[] ph1_r;
    private int[] refUnits;
    private double[] foldUp;
    private double[] foldDown;
    private String[] label;
    private String[] dlabel;
    private Nuclei[] nucleus;
    private boolean[] complex;
    private boolean[] complex_r;
    private boolean[] freqDomain;
    private boolean[] freqDomain_r;
    private double[][] rmsd;
    private double[][] values;
    private int posneg;
    private double lvl;
    private boolean lvlSet = false;
    private double scale = 1.0;
    private double norm = 1.0;
    private int rdims;
    private String solvent = null;
    private double temperature = 0.0;
    private boolean littleEndian = false;
    private boolean gotByteOrder = false;
    private int dataType = 0;
    private boolean initialized = false;
    private boolean hasBeenWritten = false;
    private HashMap paths = null;
    private HashMap properties = new HashMap();
    private String posColor = "black";
    private String negColor = "red";
    private static List<DatasetListener> observers = new ArrayList<>();
    private Double noiseLevel = null;
    static private LRUMap vectorBuffer = new LRUMap(512);
    private boolean dirty = false;  // flag set if a vector has been written to dataset, should purge bufferVectors
    TreeSet<DatasetRegion> regions;
    LineShapeCatalog simVecs = null;
    Map<String, double[]> buffers = new HashMap<>();

    @Override
    protected SEXP cloneWithNewAttributes(AttributeMap newAttributes) {
        // fixme should clone to in-memory Dataset or Dataset pointing to same raFile etc. not DoubleArrayVector??
        double[] arrayValues = toDoubleArray();
        DoubleArrayVector clone = DoubleArrayVector.unsafe(arrayValues, newAttributes);
        return clone;
    }

    private void setDimAttributes() {
        unsafeSetAttributes(AttributeMap.builder().addAllFrom(getAttributes()).setDim(new IntArrayVector(getSizes())).build());
    }

    @Override
    public double getElementAsDouble(int offset) {
        int[] indices = new int[nDim];
        double value;
        for (int i = nDim - 1; i >= 0; i--) {
            indices[i] = offset / strides[i];
            offset = offset % strides[i];
        }

        try {
            value = readPoint(indices);
        } catch (IOException | NullPointerException ioE) {
            value = DoubleVector.NA;
        }
        return value;
    }

    @Override
    public int length() {
        int length = 1;
        for (int i = 0; i < nDim; i++) {
            length *= layout.getSize(i);
        }
        return length;
    }

    @Override
    public boolean isConstantAccessTime() {
        return true;
    }

    @Override
    public int compareTo(Dataset o) {
        return getName().compareTo(o.getName());
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

    /**
     *
     */
    public FFORMAT fFormat = FFORMAT.NMRVIEW;

    /**
     * Create a new Dataset object that refers to an existing random access file
     * in a format that can be described by this class.
     *
     * @param fullName The full path to the file
     * @param name The short name (and initial title) to be used for the
     * dataset.
     * @param writable true if the file should be opened in a writable mode.
     * @param useCacheFile true if the file will use StorageCache rather than
     * memory mapping file. You should not use StorageCache if the file is to be
     * opened for drawing in NMRFx (as thread interrupts may cause it to be
     * closed)
     * @throws IOException if an I/O error occurs
     */
    public Dataset(String fullName, String name, boolean writable, boolean useCacheFile)
            throws IOException {
        // fixme  FileUtil class needs to be public file = FileUtil.getFileObj(interp,fullName);
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
        RandomAccessFile raFile;
        if (file.canWrite()) {
            raFile = new RandomAccessFile(file, "rw");
        } else {
            raFile = new RandomAccessFile(file, "r");
        }
        if (raFile == null) {
            throw new IllegalArgumentException(
                    "Couldn't open file \"" + fullName + "\"");
        }

        initialized = true;
        title = fileName;

        scale = 1.0;
        lvl = 0.1;
        posneg = 1;
        DatasetHeaderIO headerIO = new DatasetHeaderIO(this);
        if (fullName.contains(".ucsf")) {
            layout = headerIO.readHeaderUCSF(raFile);
        } else {
            layout = headerIO.readHeader(raFile);
        }
        DatasetParameterFile parFile = new DatasetParameterFile(this, layout);
        parFile.readFile();
        if (layout != null) {
            if (useCacheFile) {
                dataFile = new SubMatrixFile(this, file, layout, raFile, writable);
            } else {
                if (layout.getNDataBytes() > 512e6) {
                    dataFile = new BigMappedMatrixFile(this, file, layout, raFile, writable);
                } else {
                    if (layout.isSubMatrix()) {
                        dataFile = new MappedSubMatrixFile(this, file, layout, raFile, writable);
                    } else {
                        dataFile = new MappedMatrixFile(this, file, layout, raFile, writable);
                    }
                }
            }
        }

        setStrides();
        addFile(fileName);
        setDimAttributes();
        loadLSCatalog();
    }

    /**
     * Create a new Dataset object that uses data in the specified Vec object
     *
     * @param vector the vector containing data
     */
    public Dataset(Vec vector) {

        FloatBuffer floatBuffer = FloatBuffer.allocate(vector.getSize());
        this.vecMat = vector;
        fileName = vector.getName();
        canonicalName = vector.getName();
        dataFile = vector;
        title = fileName;
        nDim = 1;
        strides = new int[1];
        fileDimSizes = new int[1];
        vsize = new int[1];
        vsize_r = new int[1];
        tdSize = new int[1];
        zfSize = new int[1];
        extFirst = new int[1];
        extLast = new int[1];
        strides[0] = 1;
        vsize[0] = 0;
        vsize_r[0] = 0;
        tdSize[0] = vector.getTDSize();
        fileSize = vector.getSize();
        layout = DatasetLayout.createFullMatrix(vsize);
        newHeader();

        foldUp[0] = 0.0;
        foldDown[0] = 0.0;
        scale = 1.0;
        lvl = 0.1;
        posneg = 1;
        sf[0] = vector.centerFreq;
        sw[0] = 1.0 / vector.dwellTime;
        sw_r[0] = sw[0];
        refValue[0] = vector.refValue;
        refValue_r[0] = refValue[0];
        ph0[0] = vector.getPH0();
        ph1[0] = vector.getPH1();
        ph0_r[0] = ph0[0];
        ph1_r[0] = ph1[0];
        refPt[0] = 0;
        refPt_r[0] = 0;
        complex[0] = vector.isComplex();
        complex_r[0] = vector.isComplex();
        freqDomain[0] = vector.freqDomain();
        freqDomain_r[0] = vector.freqDomain();
        refUnits[0] = 3;
        rmsd = new double[1][1];
        values = new double[1][];
        //System.err.println("Opened file " + fileName + " with " + this.nDim + " dimensions");
        initialized = true;
        addFile(fileName);
    }

    private Dataset(String fullName, String title,
            int[] dimSizes, boolean closeDataset) throws DatasetException {
        //LOGGER.info("Make dataset {}", fullName);
        try {
            RandomAccessFile raFile = new RandomAccessFile(fullName, "rw");
            file = new File(fullName);

            canonicalName = file.getCanonicalPath();
            fileName = file.getName().replace(' ', '_');

            this.nDim = dimSizes.length;

            int i;
            this.strides = new int[this.nDim];
            this.fileDimSizes = new int[this.nDim];
            this.vsize = new int[this.nDim];
            this.vsize_r = new int[this.nDim];
            this.tdSize = new int[this.nDim];
            this.zfSize = new int[this.nDim];
            this.extFirst = new int[this.nDim];
            this.extLast = new int[this.nDim];
            rmsd = new double[nDim][];
            values = new double[nDim][];
            int nBytes = 4;
            fileSize = 1;

            for (i = 0; i < this.nDim; i++) {
                fileDimSizes[i] = dimSizes[i];
                fileSize *= dimSizes[i];
                nBytes *= dimSizes[i];
            }
            layout = new DatasetLayout(dimSizes);
            layout.setBlockSize(4096);
            layout.dimDataset();

            this.title = title;
            newHeader();
            int fileHeaderSize;
            if (fullName.contains(".ucsf")) {
                fileHeaderSize = UCSF_HEADER_SIZE + 128 * nDim;
            } else {
                fileHeaderSize = NV_HEADER_SIZE;
            }
            if (layout != null) {
                layout.setFileHeaderSize(fileHeaderSize);
                if (useCacheFile) {
                    dataFile = new SubMatrixFile(this, file, layout, raFile, true);
                    raFile.setLength(layout.getTotalSize());
                } else {
                    if (layout.getNDataBytes() > 512e6) {
                        dataFile = new BigMappedMatrixFile(this, file, layout, raFile, true);
                    } else {
                        if (layout.isSubMatrix()) {
                            dataFile = new MappedSubMatrixFile(this, file, layout, raFile, true);
                        } else {
                            dataFile = new MappedMatrixFile(this, file, layout, raFile, true);
                        }
                    }
                }
            }
            setStrides();
            writeHeader();
            if (closeDataset) {
                dataFile.zero();
                dataFile.close();
            } else {
                addFile(fileName);
            }
            DatasetParameterFile parFile = new DatasetParameterFile(this, layout);
            parFile.remove();
        } catch (IOException ioe) {
            //LOGGER.error(ioe.getMessage());
            throw new DatasetException("Can't create dataset " + ioe.getMessage());
        }
    }

    // create in memory file
    /**
     * Create a dataset in memory for fast access. This is an experimental mode,
     * and the dataset is not currently written to disk so can't be persisted.
     *
     * @param title Dataset title
     * @param dimSizes Sizes of the dataset dimensions
     * @throws DatasetException if an I/O error occurs
     */
    public Dataset(String title, int[] dimSizes) throws DatasetException {
        try {
            this.nDim = dimSizes.length;

            int i;
            setNDim(nDim);

            for (i = 0; i < this.nDim; i++) {
                fileDimSizes[i] = dimSizes[i];
            }
            layout = DatasetLayout.createFullMatrix(dimSizes);
            this.title = title;
            this.fileName = title;
            setStrides();
            newHeader();
            dataFile = new MemoryFile(this, true);
            dataFile.zero();
        } catch (IOException ioe) {
            throw new DatasetException("Can't create dataset " + ioe.getMessage());
        }
    }

    @Override
    public String toString() {
        return fileName;
    }

    /**
     * Create a new dataset file in NMRView format.
     *
     * @param fullName The full path to the new file
     * @param title The title to be used for the new dataset
     * @param dimSizes The sizes of the new dataset.
     * @param closeDataset If true, close dataset after creating
     * @throws DatasetException if an I/O error occurred when writing out file
     */
    public static Dataset createDataset(String fullName, String title, int[] dimSizes, boolean closeDataset) throws DatasetException {
        Dataset dataset = new Dataset(fullName, title, dimSizes, closeDataset);
        if (closeDataset) {
            dataset.close();
            dataset = null;
        }
        return dataset;
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
     * Set the type of the data values. At present, only single precision float
     * values are used in the dataset. This is indicated with a return value of
     * 0.
     *
     * @param value the datatype to set
     */
    public void setDataType(int value) {
        dataType = value;
    }

    /**
     * Set cacheFile mode. If true data will be written to a Random access file
     * buffered through the Storage Cache. If false, data will be written to a
     * Random Access File using memory mapping.
     *
     * @param value the cacheFile mode
     */
    public static void useCacheFile(boolean value) {
        useCacheFile = value;
    }

    /**
     * Check if there is an open file with the specified name.
     *
     * @param name the name to check
     * @return true if there is an open file with that name
     */
    public static boolean checkExistingName(final String name) {
        return Project.getActive().isDatasetPresent(name);
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
        return Project.getActive().isDatasetPresent(file);
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
        List<String> datasetNames = Project.getActive().getDatasetNames();
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

    public boolean isCacheFile() {
        return dataFile instanceof SubMatrixFile;
    }

    /**
     * Return whether the dataset is writable. Datasets that store data in a Vec
     * object are always writable. Datasets that store data in a file are only
     * writable if the underlying file has been opened writable.
     *
     * @return true if the dataset can be written to.
     */
    public boolean isWritable() {
        boolean writable;
        if (vecMat != null) {
            writable = true;
        } else {
            writable = dataFile.isWritable();
        }
        return writable;
    }

    /**
     * Change the writable state of the file to the specified value
     *
     * @param writable The new writable state for the file
     * @throws java.io.IOException
     */
    public void changeWriteMode(boolean writable) throws IOException {
        if (dataFile != null) {
            if (writable != dataFile.isWritable()) {
                if (dataFile.isWritable()) {
                    dataFile.force();
                }
                dataFile.setWritable(writable);
            }
        }
    }

    /*
      
     * Resize the file to the specified sizes
     *
     * @param dimSizes The new sizes
     * @throws IOException if an I/O error occurs
    public void resize(int[] dimSizes) throws IOException {
        if (nDim != dimSizes.length) {
            throw new IllegalArgumentException("Can't change dataset dimensions when resizing");
        }

        if (hasBeenWritten) {
            throw new IllegalArgumentException("Can't resize after writing");
        }

        boolean sizeChanged = false;

        for (int i = 0; i < nDim; i++) {
            if (getSize(i) != dimSizes[i]) {
                sizeChanged = true;

                break;
            }
        }

        if (!sizeChanged) {
            return;
        }

        int nBytes = 4;
        fileSize = 1;

        for (int i = 0; i < nDim; i++) {
            setSize(i, dimSizes[i]);
            fileSize *= dimSizes[i];
            nBytes *= dimSizes[i];
        }

        reInitHeader();

        //newHeader(this);
        fileHeaderSize = NV_HEADER_SIZE;
        setBlockSize(4096);
        dimDataset();
        writeHeader();
        dataFile = new BigMappedMatrixFile(this, raFile, true);
        dataFile.zero();
        initialized = true;
        hasBeenWritten = true;
    }

     */
    /**
     * Get the Vec object. Null if the dataset stores data in a data file,
     * rather than Vec object.
     *
     * @return Vec storing data or null if data file mode.
     */
    public Vec getVec() {
        return vecMat;
    }

    /**
     * Get the File object corresponding to the data file for this Dataset
     *
     * @return File object, null if data stored in Vec
     */
    public File getFile() {
        return file;
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
    public void setByteOrder(ByteOrder order) {
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

    private void addFile(String datasetName) {
        Project.getActive().addDataset(this, datasetName);
        for (DatasetListener observer : observers) {
            try {
                observer.datasetAdded(this);
            } catch (RuntimeException e) {
            }
        }

    }

    private void removeFile(String datasetName) {
        Project.getActive().removeDataset(datasetName);
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
        boolean removed = Project.getActive().removeDataset(fileName);
        if (removed) {
            fileName = newName;
            title = fileName;
            Project.getActive().addDataset(this, newName);
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
     * Add a listener to that will be notified when Datasets are opened.
     *
     * @param datasetListener the DatasetListener object
     */
    public static void addObserver(DatasetListener datasetListener) {
        observers.add(datasetListener);
    }

    /**
     * Remove a DatasetListener from list of listeners.
     *
     * @param datasetListener the DatasetListener object
     */
    public static void removeObserver(DatasetListener datasetListener) {
        observers.remove(datasetListener);
    }

    /**
     * Close this dataset.
     */
    public void close() {
        removeFile(fileName);
        try {
            if (dataFile != null) {
                if (dataFile.isWritable()) {
                    dataFile.force();
                }
                dataFile.close();
            }
            dataFile = null;
        } catch (IOException e) {
            //LOGGER.warn(e.getMessage());
        }

        for (DatasetListener observer : observers) {
            try {
                observer.datasetRemoved(this);
            } catch (RuntimeException e) {
                System.err.println("error removingFile " + e.getMessage());
                // FIXME log this!
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

//    if {$parfile eq ""} {
//        eval set fullName [nv_dataset name $dataset]
//        set parfile [file root $fullName].par
//    }
//    set f1 [open $parfile w]
//    set ndim [nv_dataset ndim $dataset]
//    set sizes ""
//    set blksizes ""
//    for {set i 1} {$i <= $ndim} {incr i} {
//        lappend sizes [nv_dataset size $dataset $i]
//        lappend blksizes [nv_dataset blksize $dataset $i]
//    }
//
//
//    puts $f1 "dim $ndim $sizes $blksizes"
//    for {set i 1} {$i <= $ndim} {incr i} {
//        foreach par  "sw sf label dlabel nucleus complex"  {
//            set value [nv_dataset $par $dataset $i]
//            if {$par eq "dlabel"} {
//                set value [::nv::util::escapeUnicode $value]
//            }
//            puts $f1 "$par $i $value"
//        }
//
//    }
//    foreach par  "posneg lvl scale rdims datatype poscolor negcolor"  {
//        puts $f1 "$par [nv_dataset $par $dataset]"
//    }
//
//
//    for {set i 1} {$i <= $ndim} {incr i} {
//        puts $f1 "ref $i [nv_dataset ref $dataset $i] [nv_dataset refpt $dataset $i]"
//    }
//    foreach "prop value" [nv_dataset property $dataset] {
//        puts $f1 "property $prop $value"
//    }
//    if {($cmd ne "") && ($handle ne "")} {
//        puts $f1 "parid $cmd $parID"
//        ::nv::data::io::writeVendorPar $cmd $handle $f1
//        set fileText [::nv::util::getIfExists ::dcs::filemgr::datasetInfo($parID,text) ""]
//        if {$fileText ne ""} {
//            foreach line [split $fileText \n] {
//                puts $f1 "text $line"
//            }
//            puts $f1 [list textend $parID]
//        }
//    }
//    close $f1
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
        double hz = pt / getSize(iDim) * getSw(iDim);

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
        double hz = pt / getSize(iDim) * getSw(iDim);

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
            setSize(0, vecMat.getSize());

            return (vecMat.getSize());
        } else {
            return (getSize(iDim));
        }
    }

    int fold(int iDim, int pt) {
        while (pt < 0) {
            pt += getSize(iDim);
        }
        while (pt >= getSize(iDim)) {
            pt -= getSize(iDim);
        }
        return pt;
    }

    double fold(int iDim, double pt) {
        while (pt < 0) {
            pt += getSize(iDim);
        }
        while (pt >= getSize(iDim)) {
            pt -= getSize(iDim);
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
     * Get the size of the dataset along the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return the size
     */
    public int getSize(int iDim) {
        return vecMat == null ? layout.getSize(iDim) : vecMat.getSize();
    }

    /**
     * Set the size of the dataset along the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param size the size to set
     */
    public void setSize(final int iDim, final int size) {
        layout.setSize(iDim, size);
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
            sizes[i] = getSize(i);
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
            double delRef = getRefPt(iDim) * getSw(iDim) / getSf(iDim) / getSize(iDim);
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
            double delRef = getRefPt(iDim) * getSw(iDim) / getSf(iDim) / getSize(iDim);
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
            double delRef = getRefPt(iDim) * getSw(iDim) / getSf(iDim) / getSize(iDim);
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
            double delRef = getRefPt_r(iDim) * getSw(iDim) / getSf(iDim) / getSize(iDim);
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
            double delRef = getRefPt_r(iDim) * getSw_r(iDim) / getSf(iDim) / getSize(iDim);
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
            double delRef = getRefPt_r(iDim) * getSw_r(iDim) / getSf(iDim) / getSize(iDim);
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
        double[] limits = {
            pointToPPM(iDim, size(iDim) - 1),
            pointToPPM(iDim, 0)
        };
        return limits;
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
            value = vecMat.freqDomain();
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
            value = vecMat.freqDomain();
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
     * Return whether dataset has a data file.
     *
     * @return true if this dataset has a data file associated with it.
     */
    public boolean hasDataFile() {
        return dataFile != null;
    }

    /**
     * Return whether dataset is a memory file.
     *
     * @return true if this dataset has a data file associated with it.
     */
    public boolean isMemoryFile() {
        return file == null;
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
            if (values.length != getSize(iDim)) {
                throw new IllegalArgumentException("Number of values (" + values.length + ") must equal dimension size (" + getSize(iDim) + ") for dim " + iDim);
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
            if (values.size() != getSize(iDim)) {
                throw new IllegalArgumentException("Number of values (" + values.size() + ") must equal dimension size (" + getSize(iDim) + ") for dim " + iDim);
            }
            this.values[iDim] = new double[values.size()];
            for (int i = 0; i < values.size(); i++) {
                this.values[iDim][i] = values.get(i);
            }
        }
    }

    double[] optCenter(int[] maxPoint, int[] dim) throws IOException {
        double[] dmaxPoint = new double[nDim];
        int[] points = new int[nDim];
        double[] f = new double[2];
        double centerValue = readPoint(maxPoint, dim);
        for (int j = 0; j < nDim; j++) {
            System.arraycopy(maxPoint, 0, points, 0, nDim);
            points[j] = maxPoint[j] - 1;
            if (points[j] < 0) {
                points[j] = getSize(dim[j]) - 1;
            }
            f[0] = readPoint(points, dim);
            points[j] = maxPoint[j] + 1;
            if (points[j] >= getSize(dim[j])) {
                points[j] = 0;
            }
            f[1] = readPoint(points, dim);
            double fPt = maxPoint[j];
            double delta = ((f[1] - f[0]) / (2.0 * ((2.0 * centerValue) - f[1]
                    - f[0])));
            // Polynomial interpolated max should never be more than half a point from grid max
            if (Math.abs(delta) < 0.5) {
                fPt += delta;
            }
            dmaxPoint[j] = fPt;

        }

        return dmaxPoint;
    }
    //    public void applyToRegion(final Interp interp, final int[][] pt, final int[] dim, final int iChunk, final int nChunk, final String script)
    //            throws TclException {
    //        ScanRegion scanRegion = new ScanRegion(pt, dim, this);
    //        int nEntries = scanRegion.buildIndex();
    //        String vecName = "applyVec" + iChunk;
    //        int newSize = pt[0][1] - pt[0][0] + 1;
    //        if (getComplex_r(dim[0])) {
    //            newSize /= 2;
    //        }
    //
    //        Vec regionVector = new Vec(newSize, false);
    //        int origSize = pt[0][1];
    //        int chunkSize = nEntries / nChunk;
    //        if ((chunkSize * nChunk) < nEntries) {
    //            chunkSize++;
    //        }
    //        int start = iChunk * chunkSize;
    //        int end = start + chunkSize;
    //        if (end > nEntries) {
    //            end = nEntries;
    //        }
    //        for (int i = start; i < end; i++) {
    //            int[] iE = scanRegion.getIndexEntry(i);
    //            pt[0][1] = origSize;
    //            for (int iDim = 1; iDim < nDim; iDim++) {
    //                pt[iDim][0] = iE[iDim];
    //                pt[iDim][1] = iE[iDim];
    //            }
    //            regionVector.resize(newSize);
    //            try {
    //                readVectorFromDatasetFile(pt, dim, regionVector);
    //            } catch (IOException ioE) {
    //                throw new TclException(interp, ioE.getMessage());
    //            }
    //            interp.eval(script);
    //            pt[0][1] = regionVector.getSize() - 1;
    //            try {
    //                readVectorFromDatasetFile(pt, dim, regionVector);
    //            } catch (IOException ioE) {
    //                throw new TclException(interp, ioE.getMessage());
    //            }
    //        }
    //    }

    /**
     * Calculate a noise level for the dataset by analyzing the rms value of
     * data points in a corner of dataset. The resulting value is stored and can
     * be retrieved with getNoiseLevel
     *
     * @return the noise level
     */
    public double guessNoiseLevel() {
        if (noiseLevel == null) {
            int[][] pt = new int[nDim][2];
            int[] cpt = new int[nDim];
            int[] dim = new int[nDim];
            double[] width = new double[nDim];
            for (int i = 0; i < nDim; i++) {
                dim[i] = i;
                pt[i][0] = 4;
                if (pt[i][0] >= getSize(i)) {
                    pt[i][0] = getSize(i) - 1;
                }
                pt[i][1] = getSize(i) / 8;
                cpt[i] = (pt[i][0] + pt[i][1]) / 2;
                width[i] = (double) Math.abs(pt[i][0] - pt[i][1]);
            }
            try {
                RegionData rData = analyzeRegion(pt, cpt, width, dim);
                noiseLevel = rData.getRMS() * scale;
            } catch (IOException ioE) {
                noiseLevel = null;
            }
        }
        return noiseLevel == null ? null : noiseLevel / scale;
    }

    /**
     * Calculate basic descriptive statistics on the specified region of the
     * dataset.
     *
     * @param pt The bounds of the region in dataset points
     * @param cpt The center point of each region
     * @param width the width of each region
     * @param dim the dataset dimensions that the pt, cpt, and width parameters
     * use
     * @return RegionData with statistical information about the specified
     * region
     * @throws java.io.IOException if an I/O error ocurrs
     */
    synchronized public RegionData analyzeRegion(int[][] pt, int[] cpt, double[] width, int[] dim)
            throws IOException {
        int[] iPointAbs = new int[nDim];
        double[] iTol = new double[nDim];

        double fTol = 0.25;
        double threshold = 0.0;
        double threshRatio = 0.25;
        int pass2;
        int temp;

        if (vecMat != null) {
            setSize(0, vecMat.getSize());
        }

        int[] counterSizes = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            if (pt[i][1] >= pt[i][0]) {
                counterSizes[i] = pt[i][1] - pt[i][0] + 1;
            } else {
                counterSizes[i] = getSize(dim[i]) + (pt[i][1] - pt[i][0] + 1);
            }
            iTol[i] = fTol * Math.abs(counterSizes[i]);
        }
        RegionData rData = new RegionData(this);
        for (pass2 = 0; pass2 < 2; pass2++) {
            if (pass2 == 1) {
                rData.svar = 0.0;
                rData.mean = rData.getVolume_r() / rData.getNpoints();
                threshold = threshRatio * rData.getCenter();
            }
            DimCounter counter = new DimCounter(counterSizes);
            DimCounter.Iterator cIter = counter.iterator();
            while (cIter.hasNext()) {
                int[] points = cIter.next();
                for (int i = 0; i < nDim; i++) {
                    points[i] += pt[i][0];
                    if (points[i] >= getSize(dim[i])) {
                        points[i] = points[i] - getSize(dim[i]);
                    }
                    iPointAbs[i] = points[i];
                }
                rData.value = readPoint(points, dim);

                if (rData.getValue() == Double.MAX_VALUE) {
                    continue;
                }

                if (pass2 == 1) {
                    rData.calcPass1(iPointAbs, cpt, width, dim, threshold, iTol);
                } else {
                    rData.calcPass0(iPointAbs, cpt, width, dim);
                }

            }

        }
        rData.dmaxPoint = optCenter(rData.maxPoint, dim);
        if (rData.getNpoints() == 1) {
            rData.rms = 0.0;
        } else {
            rData.rms = Math.sqrt(rData.getSumSq() / (rData.getNpoints() - 1));
        }
        return rData;
    }

//    public static void analyzeRegion(Interp interp, Dataset dataset,
//            int[][] pt, int[] cpt, double[] width, int[] dim)
//            throws TclException {
//        RegionData rData;
//        try {
//            rData = dataset.analyzeRegion(pt, cpt, width, dim);
//        } catch (IOException ioE) {
//            throw new TclException(interp, ioE.getMessage());
//        }
//        TclObject list = TclList.newInstance();
//
//        TclList.append(interp, list, TclString.newInstance("center"));
//        TclList.append(interp, list, TclDouble.newInstance(rData.getCenter()));
//        interp.setVar("Nv_Value(center)", TclDouble.newInstance(rData.getCenter()),
//                TCL.GLOBAL_ONLY);
//
//        TclList.append(interp, list, TclString.newInstance("jitter"));
//        TclList.append(interp, list, TclDouble.newInstance(rData.getJitter()));
//        interp.setVar("Nv_Value(jitter)", TclDouble.newInstance(rData.getJitter()),
//                TCL.GLOBAL_ONLY);
//
//        TclList.append(interp, list, TclString.newInstance("min"));
//        TclList.append(interp, list, TclDouble.newInstance(rData.getMin()));
//        interp.setVar("Nv_Value(min)", TclDouble.newInstance(rData.getMin()),
//                TCL.GLOBAL_ONLY);
//
//        TclList.append(interp, list, TclString.newInstance("max"));
//        TclList.append(interp, list, TclDouble.newInstance(rData.getMax()));
//        interp.setVar("Nv_Value(max)", TclDouble.newInstance(rData.getMax()),
//                TCL.GLOBAL_ONLY);
//
//        TclList.append(interp, list, TclString.newInstance("extreme"));
//        TclList.append(interp, list, TclDouble.newInstance(rData.getExtreme()));
//        interp.setVar("Nv_Value(extreme)", TclDouble.newInstance(rData.getExtreme()),
//                TCL.GLOBAL_ONLY);
//
//        TclList.append(interp, list, TclString.newInstance("volume"));
//        TclList.append(interp, list, TclDouble.newInstance(rData.getVolume_r()));
//        interp.setVar("Nv_Value(volume)", TclDouble.newInstance(rData.getVolume_r()),
//                TCL.GLOBAL_ONLY);
//
//        TclList.append(interp, list, TclString.newInstance("evolume"));
//        TclList.append(interp, list, TclDouble.newInstance(rData.getVolume_e()));
//        interp.setVar("Nv_Value(evolume)", TclDouble.newInstance(rData.getVolume_e()),
//                TCL.GLOBAL_ONLY);
//
//        TclList.append(interp, list, TclString.newInstance("mean"));
//        TclList.append(interp, list, TclDouble.newInstance(rData.getMean()));
//        interp.setVar("Nv_Value(mean)", TclDouble.newInstance(rData.getMean()),
//                TCL.GLOBAL_ONLY);
//
//        TclList.append(interp, list, TclString.newInstance("sdev"));
//        TclList.append(interp, list, TclDouble.newInstance(rData.getSumSq()));
//        interp.setVar("Nv_Value(sdev)", TclDouble.newInstance(rData.getSumSq()),
//                TCL.GLOBAL_ONLY);
//
//        TclList.append(interp, list, TclString.newInstance("scale"));
//        TclList.append(interp, list, TclDouble.newInstance(dataset.scale));
//        interp.setVar("Nv_Value(scale)", TclDouble.newInstance(dataset.scale),
//                TCL.GLOBAL_ONLY);
//
//        TclList.append(interp, list, TclString.newInstance("n"));
//        TclList.append(interp, list, TclInteger.newInstance(rData.getNpoints()));
//        interp.setVar("Nv_Value(n)", TclInteger.newInstance(rData.getNpoints()),
//                TCL.GLOBAL_ONLY);
//
//        for (int i = 0; i < dataset.getNDim(); i++) {
//            TclList.append(interp, list, TclString.newInstance("p" + i + "b"));
//            TclList.append(interp, list, TclInteger.newInstance(pt[i][0]));
//
//            TclList.append(interp, list, TclString.newInstance("p" + i + "e"));
//            TclList.append(interp, list, TclInteger.newInstance(pt[i][1]));
//        }
//        for (int i = 0; i < dataset.getNDim(); i++) {
//            TclList.append(interp, list, TclString.newInstance("max" + i));
//            double maxPointPPM = dataset.pointToPPM(dim[i], rData.dmaxPoint[i]);
//            TclList.append(interp, list, TclDouble.newInstance(maxPointPPM));
//        }
//
//        interp.setResult(list);
//    }
    public double[] getPercentile(double p, int[][] pt, int[] dim) throws IOException {
        PSquarePercentile pSquarePos = new PSquarePercentile(p);
        PSquarePercentile pSquareNeg = new PSquarePercentile(p);

        int[] counterSizes = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            if (pt[i][1] >= pt[i][0]) {
                counterSizes[i] = pt[i][1] - pt[i][0] + 1;
            } else {
                counterSizes[i] = getSize(dim[i]) + (pt[i][1] - pt[i][0] + 1);
            }
        }
        DimCounter counter = new DimCounter(counterSizes);
        DimCounter.Iterator cIter = counter.iterator();
        while (cIter.hasNext()) {
            int[] points = cIter.next();
            for (int i = 0; i < nDim; i++) {
                points[i] += pt[i][0];
                if (points[i] >= getSize(dim[i])) {
                    points[i] = points[i] - getSize(dim[i]);
                }
            }
            double value = readPoint(points, dim);

            if ((value != Double.MAX_VALUE) && (value >= 0.0)) {
                pSquarePos.increment(value);
            } else if ((value != Double.MAX_VALUE) && (value <= 0.0)) {
                pSquareNeg.increment(value);
            }
        }
        double[] result = {pSquarePos.getResult(), pSquareNeg.getResult()};
        return result;

    }

    synchronized public double measureSDev(int[][] pt, int[] dim, double sDevIn, double ratio)
            throws IOException {

        int[] counterSizes = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            if (pt[i][1] >= pt[i][0]) {
                counterSizes[i] = pt[i][1] - pt[i][0] + 1;
            } else {
                counterSizes[i] = getSize(dim[i]) + (pt[i][1] - pt[i][0] + 1);
            }
        }
        DimCounter counter = new DimCounter(counterSizes);
        DimCounter.Iterator cIter = counter.iterator();
        double sumSq = 0.0;
        double sum = 0.0;
        int n = 0;
        while (cIter.hasNext()) {
            int[] points = cIter.next();
            for (int i = 0; i < nDim; i++) {
                points[i] += pt[i][0];
                if (points[i] >= getSize(dim[i])) {
                    points[i] = points[i] - getSize(dim[i]);
                }
            }
            double value = readPoint(points, dim);
            if (value != Double.MAX_VALUE) {
                sum += value;
                sumSq += value * value;
                n++;
            }
        }
        double sDev = 0.0;
        if (n > 1) {
            sDev = Math.sqrt(sumSq / n - ((sum / n) * (sum / n)));
        }
        return sDev;
    }

    /**
     * Get an array representing the dimensions that will be used in specifying
     * slices through the dataset. The specified dimension will be in the first
     * position [0] of array and remaining dimensions in increasing order in the
     * rest of the array.
     *
     * @param iDim Dataset dimension index
     * @return Array of dimension indices
     */
    public int[] getSliceDims(int iDim) {
        int[] dim = new int[nDim];
        dim[0] = iDim;

        int j = 0;
        for (int i = 1; i < nDim; i++) {
            if (j == iDim) {
                j++;
            }

            dim[i] = j;
            j++;
        }
        return dim;

    }

    /**
     * Measure the rmsd value of vectors along the specified dataset dimension.
     * These values can be used to form a local threshold during peak picking
     *
     * @param iDim index of the dataset dimension
     * @throws java.io.IOException if an I/O error ocurrs
     */
    public void measureSliceRMSD(int iDim) throws IOException {
        int[][] pt = new int[nDim][2];
        int[] dim = new int[nDim];
        dim[0] = iDim;
        pt[0][0] = 0;
        pt[0][1] = 0;

        int j = 0;
        int faceSize = 1;
        for (int i = 1; i < nDim; i++) {
            if (j == iDim) {
                j++;
            }

            dim[i] = j;
            pt[i][0] = 0;
            pt[i][1] = getSize(dim[i]) - 1;
            faceSize *= getSize(dim[i]);
            j++;
        }
        pt[0][1] = getSize(iDim) - 1;
        int newSize = pt[0][1] - pt[0][0] + 1;

        Vec rmsdVec = new Vec(newSize, false);

        ScanRegion scanRegion = new ScanRegion(pt, dim, this);
        int nEntries = scanRegion.buildIndex();

        int winSize = getSize(iDim) / 32;
        int nWin = 4;
        rmsd[iDim] = new double[faceSize];
        int origSize = pt[0][1];
        for (int iEntry = 0; iEntry < nEntries; iEntry++) {
            int[] iE = scanRegion.getIndexEntry(iEntry);
            pt[0][1] = origSize;
            for (int jDim = 1; jDim < nDim; jDim++) {
                pt[jDim][0] = iE[jDim];
                pt[jDim][1] = iE[jDim];
            }
            readVectorFromDatasetFile(pt, dim, rmsdVec);
            double sdev = Util.sdev(rmsdVec, winSize, nWin);
            int dSize = 1;
            int index = 0;
            for (int i = 1; i < pt.length; i++) {
                index += pt[i][0] * dSize;
                dSize = getSize(dim[i]);
            }
            rmsd[iDim][index] = sdev;
        }
    }

    /**
     * Check if dataset has stored rmsd values along each dimension as
     * calculated by measureSliceRMSD
     *
     * @return true if has stored rmsd values
     *
     */
    public boolean sliceRMSDValid() {
        boolean valid = true;
        for (int j = 0; j < nDim; j++) {
            if (rmsd[j] == null) {
                valid = false;
                break;
            }
        }
        return valid;
    }

    private Double getSliceRMSD(int iDim, int[] pt) {
        int index = 0;
        int dSize = 1;
        int[] dim = getSliceDims(iDim);
        for (int i = 1; i < pt.length; i++) {
            index += pt[i] * dSize;
            dSize = getSize(dim[i]);
        }
        if ((rmsd[iDim] == null) || (index >= rmsd[iDim].length)) {
            return null;
        } else {
            return rmsd[iDim][index];
        }

    }

    /**
     * Get the rmsd value for a point in the dataset. The value is calculated as
     * the maximum value of all the rmsd values for vectors that intersect at
     * the specified point
     *
     * @param pt indices of point
     * @return rmsd value for point
     */
    public Double getRMSDAtPoint(int[] pt) {
        double maxRMSD = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < nDim; i++) {
            int[] gPt = new int[nDim];
            int k = 1;
            for (int j = 0; j < nDim; j++) {
                if (j != i) {
                    gPt[k] = pt[j];
                    k++;
                }
            }
            double sliceRMSD = getSliceRMSD(i, gPt);
            if (sliceRMSD > maxRMSD) {
                maxRMSD = sliceRMSD;
            }
        }
        return maxRMSD;

    }

    /**
     * Calculate ratio of slice based rmsd to a specified level. The ratio is
     * used in peak picker to determine if a point should be picked at specified
     * point.
     *
     * @param level Peak picking level
     * @param pt indices of dataset
     * @param dim dataset dimensions used by pt indices
     * @return ratio of point level to threshold level
     */
    public double checkNoiseLevel(double level, int[] pt, int[] dim) {
        int[] gPt = new int[nDim];
        for (int j = 0; j < nDim; j++) {
            gPt[dim[j]] = pt[j];
        }
        double rmsdAtPoint = getRMSDAtPoint(gPt);
        return level / rmsdAtPoint;
    }

//    public void scaleByNoise(Interp interp) throws TclException {
//        String rmsdVec = "rmsdVec";
//        int iDim = 1;
//        int[][] pt = new int[nDim][2];
//        int[] dim = new int[nDim];
//        dim[0] = iDim;
//        pt[0][0] = 0;
//        pt[0][1] = 0;
//
//        int j = 0;
//        for (int i = 1; i < nDim; i++) {
//            if (j == iDim) {
//                j++;
//            }
//
//            dim[i] = j;
//            pt[i][0] = 0;
//            pt[i][1] = getSize(dim[i]) - 1;
//            j++;
//        }
//
//        Vec rmsdVec = Vec.get(rmsdVec);
//
//        if (rmsdVec == null) {
//            rmsdVec = new Vec(32, rmsdVec);
//        }
//
//        scanRegion = initScanner();
//        scanRegion.setup(interp, this, pt, dim, rmsdVec);
//        int vecSize = rmsdVec.size;
//        int[] gPt = new int[nDim];
//        while (true) {
//            int mpt[][] = scanRegion.scanGet(interp);
//            if (mpt == null) {
//                break;
//            }
//            for (j = 1; j < nDim; j++) {
//                gPt[dim[j]] = mpt[j][0];
//            }
//            // adjust for scanDim
//            for (int i = 0; i < vecSize; i++) {
//                gPt[dim[0]] = i;
//                double noise = getRMSDAtPoint(gPt);
//                rmsdVec.vec[i] /= noise;
//            }
//            scanRegion.scanPut(interp);
//        }
//    }
    boolean isDirty() {
        return dirty;
    }

    void setDirty(boolean value) {
        dirty = value;
    }

    private static int getBufferSize() {
        return vectorBuffer.size();
    }

    private synchronized static void purgeBufferVectors(Dataset dataset) {
        System.err.println("purge buffer vectors " + dataset.fileName);
        Iterator iter = vectorBuffer.keySet().iterator();
        while (iter.hasNext()) {
            BlockID id = (BlockID) iter.next();
            if (id.getFile() == dataset) {
                iter.remove();
                id.close();
            }
        }
        dataset.setDirty(false);
    }

    /**
     * Get the intensities in the dataset at the specified points.
     *
     * @param posArray List of points
     * @return array of intensities at specified point values
     * @throws java.io.IOException if an I/O error ocurrs
     */
    public double[] getIntensities(final ArrayList<int[]> posArray) throws IOException {
        double[] intensities = new double[posArray.size()];
        int[] dim = new int[nDim];
        for (int iDim = 0; iDim < nDim; iDim++) {
            dim[iDim] = iDim;
        }
        int iPoint = 0;
        for (int[] pValues : posArray) {
            intensities[iPoint++] = readPoint(pValues, dim);
        }
        return intensities;
    }

    /**
     * Get the intensities in the dataset within the specified region.
     *
     * @param region Region of the dataset specified in dataset points
     * @return array of intensities within region
     * @throws java.io.IOException if an I/O error ocurrs
     */
    public double[] getIntensities(final int[][] region) throws IOException {
        int[] dim = new int[nDim];
        int[] sizes = new int[nDim];
        for (int iDim = 0; iDim < nDim; iDim++) {
            dim[iDim] = iDim;
            sizes[iDim] = region[iDim][1] - region[iDim][0] + 1;
        }
        DimCounter counter = new DimCounter(sizes);
        int nPoints = counter.getSize();
        double[] intensities = new double[nPoints];
        DimCounter.Iterator cIter = counter.iterator();
        int iPoint = 0;
        while (cIter.hasNext()) {
            int[] points = cIter.next();
            for (int i = 0; i < nDim; i++) {
                points[i] += region[i][0];
            }
            intensities[iPoint++] = readPoint(points, dim);
        }
        return intensities;

    }

    /**
     * Get a list of positions that are within an elliptical region around a set
     * of points. Used to find points that should be included when doing peak
     * fitting.
     *
     * @param p2 Bounds of peak regions
     * @param cpt Array of centers of peak positions
     * @param width Array of widths of peaks
     * @param pdim Array of integers indicating mapping of peak dimension to
     * dataset dimension
     * @param multiplier unused?? should multiply width of regions
     * @return List of points near peak centers
     */
    public ArrayList<int[]> getFilteredPositions(final int[][] p2, final int[][] cpt, final double[][] width, int[] pdim, double multiplier) {
        int[] sizes = new int[p2.length];
        for (int iDim = 0; iDim < p2.length; iDim++) {
            if (p2[iDim][1] >= p2[iDim][0]) {
                sizes[iDim] = p2[iDim][1] - p2[iDim][0] + 1;
            } else {
                sizes[iDim] = layout.getSize(iDim) - p2[iDim][0] - p2[iDim][1] + 1;
            }
//            System.out.println("size " + iDim + " " + p2[iDim][0] + " " + p2[iDim][1] + " " + sizes[iDim]);
        }
        int nPoints = 1;
        for (int dimSize : sizes) {
            nPoints *= dimSize;
        }
//        System.out.println("np " + nPoints);
        ArrayList<int[]> posArray = new ArrayList<>();
        DimCounter counter = new DimCounter(sizes);
        DimCounter.Iterator iterator = counter.iterator();
        while (iterator.hasNext()) {
            int[] counts = iterator.next();
            int[] aCounts = new int[counts.length];
            int j = 0;
            boolean inDataset = true;
            for (int value : counts) {
                aCounts[j] = value + p2[j][0];
                if (aCounts[j] >= getSize(j)) {
                    aCounts[j] -= getSize(j);
                } else if (aCounts[j] < 0) {
                    aCounts[j] += getSize(j);
                }
                j++;
            }
            if (inDataset) {
                boolean ok = false;
                for (int iPeak = 0; iPeak < cpt.length; iPeak++) {
                    int iDim = 0;
                    double delta2 = 0.0;
                    for (int value : aCounts) {
                        if ((iDim >= sizes.length) || (iPeak > width.length)) {
                            System.out.println(iPeak + " " + sizes.length + " " + width.length);
                            posArray.clear();
                            return posArray;
                        }
                        if (width[iPeak][iDim] != 0.0) {
                            delta2 += ((value - cpt[iPeak][iDim]) * (value - cpt[iPeak][iDim])) / (0.47 * width[iPeak][iDim] * width[iPeak][iDim]);
                        }
                        iDim++;
                    }
                    if (delta2 < 1.0) {
                        ok = true;
                        break;
                    }
                }
                if (ok) {
                    posArray.add(aCounts);
                }
            }
        }
        return posArray;
    }

    public void setFreqDims(int n) {
        rdims = n;
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
        freqDomain = new boolean[nDim];
        freqDomain_r = new boolean[nDim];
        rmsd = new double[nDim][];
        values = new double[nDim][];
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
     * Set size of dataset to valid size for specified dimension (only used for
     * dimensions above the first)
     *
     * @param iDim Dataset dimension index
     */
    public void syncSize(int iDim) {
        if (iDim > 0) {
            setSize(iDim, getVSize(iDim));
        }
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

    public DatasetLayout getLayout() {
        return layout;
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
            refPt[i] = getSize(i) / 2;
            refPt_r[i] = getSize(i) / 2;
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

    /**
     * Get a standard label for the specified dimension. The label is based on
     * the name of the nucleus detected on this dimension.
     *
     * @param iDim Dataset dimension index
     * @return The name of the nucleus
     */
    public String getStdLabel(final int iDim) {
        return getNucleus(iDim).toString();
    }

    synchronized private void reInitHeader() {

        //sf = new double[nDim];
        //sw = new double[nDim];
        //sw_r = new double[nDim];
        //refPt = new double[nDim];
        //refPt_r = new double[nDim];
        //refValue = new double[nDim];
        //refValue_r = new double[nDim];
        //refUnits = new int[nDim];
        foldUp = new double[nDim];
        foldDown = new double[nDim];
        complex = new boolean[nDim];
        complex_r = new boolean[nDim];
        freqDomain = new boolean[nDim];
        freqDomain_r = new boolean[nDim];
        label = new String[nDim];
        dlabel = new String[nDim];
        nucleus = new Nuclei[nDim];

        int i;

        for (i = 0; i < nDim; i++) {
            //refUnits[i] = 3;
            //sw[i] = 7000.0;
            //sw_r[i] = 7000.0;
            //sf[i] = 600.0;
            //refPt[i] = size[i] / 2;
            //refPt_r[i] = size[i] / 2;
            //refValue[i] = 4.73;
            //refValue_r[i] = 4.73;
            setComplex(i, true);
            setComplex_r(i, true);
            setFreqDomain(i, false);
            setFreqDomain_r(i, false);
            label[i] = "D" + i;
            nucleus[i] = null;
        }

        setFreqDomain(0, true);
        setFreqDomain_r(0, true);
        lvl = 0.0;
        scale = 1.0;
        rdims = nDim;
        posneg = 1;

        //rdims = 0;
        //dataType = 0;
    }

    public final void setStrides() {
        strides[0] = 1;
        for (int i = 1; i < nDim; i++) {
            strides[i] = strides[i - 1] * getSize(i - 1);
        }
    }

    /**
     * Flush the header values out to the dataset file.
     */
    public final synchronized void writeHeader() {
        writeHeader(true);
    }

    /**
     * Flush the header values out to the dataset file.
     */
    public final synchronized void writeHeader(boolean nvExtra) {
        dataFile.writeHeader(nvExtra);
    }

    /**
     * Get the value of the dataset at a specified point
     *
     * @param pt indices of point to read
     * @return the dataset value
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if point is outside the range of dataset
     * ( less than 0 or greater than or equal to size)
     */
    public double readPoint(int[] pt) throws IOException, IllegalArgumentException {
        int i;

        if (vecMat != null) {
            i = pt[0];
            return vecMat.getReal(i) / scale;
        }

        for (i = 0; i < nDim; i++) {
            if (pt[i] < 0) {
                throw new IllegalArgumentException("point < 0 " + i + " " + pt[i]);
            } else if (pt[i] >= getSize(i)) {
                throw new IllegalArgumentException("point >= size " + i + " " + pt[i] + " " + getSize(i));
            }
        }
        return dataFile.getFloat(pt) / scale;

    }

    /**
     * Get the value of the dataset at a specified point
     *
     * @param pt indices of point to read
     * @param dim dimension indices that used for the point values
     * @return the dataset value
     * @throws java.io.IOException if an I/O error occurs
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
            } else if (pt[i] >= getSize(dim[i])) {
                throw new IllegalArgumentException("pointd >= size " + i + " " + dim[i] + " " + pt[i] + " " + getSize(dim[i]));
            }
        }
        int[] rPt = new int[nDim];
        for (i = 0; i < nDim; i++) {
            rPt[dim[i]] = pt[i];
        }
        return dataFile.getFloat(rPt) / scale;

    }

    /**
     * Write a value into the dataset at the specified point
     *
     * @param pt indices of point to write
     * @param value to write
     * @throws java.io.IOException if an I/O error occurs
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
                } else if (pt[i] >= getSize(i)) {
                    throw new IllegalArgumentException("point >= size " + i + " " + pt[i] + " " + getSize(i));
                }
            }
            dataFile.setFloat((float) (value * scale), pt);
        }
    }

    /**
     * Read an N dimensional matrix of values within the specified region of the
     * matrix
     *
     * @param theFile The dataset to read. Redundant since this is an instance
     * method. fixme
     * @param pt The region to read
     * @param dim The dataset dimensions used by the region points
     * @param matrix A matrix in which to store the read values. Must be at
     * least as big as region.
     * @return The maximum of the absolute values of the read values
     * @throws java.io.IOException if an I/O error ocurrs
     */
    synchronized public float readMatrix(Dataset theFile, int[][] pt,
            int[] dim, float[][] matrix) throws IOException {
        float maxValue = Float.NEGATIVE_INFINITY;
        float minValue = Float.MAX_VALUE;
        int[] point = new int[nDim];
        for (int i = 2; i < nDim; i++) {
            point[dim[i]] = pt[i][0];
        }
        for (int i = pt[0][0]; i <= pt[0][1]; i++) {
            int ii = i - pt[0][0];
            for (int j = pt[1][0]; j <= pt[1][1]; j++) {
                int jj = j - pt[1][0];
// fixme is this right for 3D rotated matrix
                point[dim[0]] = i;
                point[dim[1]] = j;
                float value = (float) readPoint(point);
                matrix[jj][ii] = value;
                if (value > maxValue) {
                    maxValue = value;
                }
                if (value < minValue) {
                    minValue = value;
                }
            }
        }
        return (Math.abs(maxValue) > Math.abs(minValue)) ? Math.abs(maxValue)
                : Math.abs(minValue);
    }

    /**
     * Read an N dimensional matrix of values within the specified region of the
     * matrix
     *
     * @param theFile The dataset to read. Redundant since this is an instance
     * method. fixme
     * @param pt The region to read
     * @param dim The dataset dimensions used by the region points
     * @param matrix A matrix in which to store the read values. Must be at
     * least as big as region.
     * @return The maximum of the absolute values of the read values
     * @throws java.io.IOException if an I/O error ocurrs
     */
    synchronized public double readMatrix(Dataset theFile, int[][] pt,
            int[] dim, double[][] matrix) throws IOException {
        double maxValue = Double.NEGATIVE_INFINITY;
        double minValue = Double.MAX_VALUE;
        int[] point = new int[nDim];
        for (int i = 2; i < nDim; i++) {
            point[dim[i]] = pt[i][0];
        }
//        for (int i = 0; i < nDim; i++) {
//            System.out.printf("%3d %3d %3d %3d\n", i, dim[i], pt[i][0], pt[i][1]);
//        }

        for (int plane = pt[1][0]; plane <= pt[1][1]; plane++) {
            int planeOffset = plane - pt[1][0];
            for (int row = pt[0][0]; row <= pt[0][1]; row++) {
                int rowOffset = row - pt[0][0];
                point[dim[0]] = row;
                point[dim[1]] = plane;
                double value = readPoint(point);
                matrix[planeOffset][rowOffset] = value;
                if (value > maxValue) {
                    maxValue = value;
                }
                if (value < minValue) {
                    minValue = value;
                }
            }
        }
        return (Math.abs(maxValue) > Math.abs(minValue)) ? Math.abs(maxValue)
                : Math.abs(minValue);
    }

    /**
     * Read an N dimensional matrix of values within the specified region of the
     * matrix
     *
     * @param pt The region to read
     * @param dim The dataset dimensions used by the region points
     * @param matrix A matrix in which to store the read values. Must be at
     * least as big as region.
     * @return The maximum of the absolute values of the read values
     * @throws java.io.IOException if an I/O error occurs
     */
    synchronized public double readMatrixND(int[][] pt,
            int[] dim, MatrixND matrix) throws IOException {
        double maxValue = Double.NEGATIVE_INFINITY;
        double minValue = Double.MAX_VALUE;
        int[] point = new int[nDim];
        point[dim[nDim - 1]] = pt[nDim - 1][0];
        int[] mPoint = new int[nDim - 1];
        // fixme should mPoint be pt +1 
        for (int i = 0; i < nDim - 1; i++) {
            mPoint[i] = pt[i][1] + 1;
        }
//        for (int i = 0; i < nDim; i++) {
//            if (i < (nDim - 1)) {
//                System.out.printf("%3d %3d %3d %3d %3d\n", i, dim[i], pt[i][0], pt[i][1], mPoint[i]);
//            } else {
//                System.out.printf("%3d %3d %3d %3d\n", i, dim[i], pt[i][0], pt[i][1]);
//            }
//        }

        MultidimensionalCounter counter = new MultidimensionalCounter(mPoint);
        MultidimensionalCounter.Iterator iter = counter.iterator();
        while (iter.hasNext()) {
            iter.next();
            int[] index = iter.getCounts();
            for (int i = 0; i < index.length; i++) {
                point[dim[i]] = index[i];
            }
            double value = readPoint(point);
            matrix.setValue(value, index);
            if (value > maxValue) {
                maxValue = value;
            }
            if (value < minValue) {
                minValue = value;
            }
        }
        return (Math.abs(maxValue) > Math.abs(minValue)) ? Math.abs(maxValue)
                : Math.abs(minValue);
    }

    /**
     * Write the data values to a file. Only works if the dataset values are in
     * a Vec object (not dataset file)
     *
     * @param fullName Name of file to write
     * @throws java.io.IOException if an I/O error ocurrs
     */
    public void writeVecMat(String fullName) throws IOException {
        File newFile = new File(fullName);
        try (RandomAccessFile outFile = new RandomAccessFile(newFile, "rw")) {
            byte[] buffer = vecMat.getBytes();
            DatasetHeaderIO headerIO = new DatasetHeaderIO(this);
            headerIO.writeHeader(layout, outFile);
            DataUtilities.writeBytes(outFile, buffer, layout.getFileHeaderSize(), buffer.length);
        }
    }

    /**
     * Write a matrix of values to the dataset
     *
     * @param dim indices of dimensions to write matrix along
     * @param matrix the values to write
     * @throws IOException if an I/O error occurs
     */
    public void writeMatrixToDatasetFile(int[] dim, Matrix matrix)
            throws IOException {
//        setDirty(true);
        int[] point = new int[nDim];
        int[][] pt = matrix.getPt();
        double[][] mat = matrix.getMatrix();
        for (int i = 2; i < nDim; i++) {
            point[dim[i]] = pt[i][0];
        }
//        for (int i = 0; i < nDim; i++) {
//            if (pt[i][0] == pt[i][1]) {
//                if ((pt[i][0] + 1) > vsize[dim[i]]) {
//                    vsize[dim[i]] = (pt[i][0] + 1);
//                }
//            } else {
//                if ((pt[i][1] + 1) > vsize[dim[i]]) {
//                    vsize[dim[i]] = (pt[i][1] + 1);
//                }
//            }
//        }
        for (int plane = pt[1][0]; plane <= pt[1][1]; plane++) {
            int planeOffset = plane - pt[1][0];
            for (int row = pt[0][0]; row <= pt[0][1]; row++) {
                int rowOffset = row - pt[0][0];
                point[dim[0]] = row;
                point[dim[1]] = plane;
                dataFile.setFloat((float) (mat[planeOffset][rowOffset] * scale), point);
            }
        }
    }

    /**
     * Write a matrix of values to the dataset
     *
     * @param dim indices of dimensions to write matrix along
     * @param matrix the values to write
     * @throws IOException if an I/O error occurs
     */
    public void writeMatrixNDToDatasetFile(int[] dim, MatrixND matrix) throws IOException {
        int[][] pt = matrix.getPt();
        int[] point = new int[nDim];
        point[dim[nDim - 1]] = pt[nDim - 1][0];
        int[] mPoint = new int[nDim - 1];
        int[] matVSizes = matrix.getVSizes();
        for (int i = 0; i < nDim - 1; i++) {
            mPoint[i] = pt[i][1] + 1;
            setVSize(dim[i], matVSizes[i]);
            setPh0(dim[i], matrix.getPh0(i));
            setPh1(dim[i], matrix.getPh1(i));
            setPh0_r(dim[i], matrix.getPh0(i));
            setPh1_r(dim[i], matrix.getPh1(i));
//            System.out.println("write ph " +i + " " + dim[i] + " " +  matrix.getPh0(i) + " " + matrix.getPh1(i));
        }

        MultidimensionalCounter counter = new MultidimensionalCounter(mPoint);
        MultidimensionalCounter.Iterator iter = counter.iterator();
        while (iter.hasNext()) {
            iter.next();
            int[] index = iter.getCounts();
            for (int i = 0; i < index.length; i++) {
                point[dim[i]] = index[i];
            }
            dataFile.setFloat((float) (matrix.getValue(index) * scale), point);
        }
    }

    /**
     * Return the Dataset object with the specified name.
     *
     * @param fileName name of Dataset to find
     * @return the Dataset or null if it doesn't exist
     */
    synchronized public static Dataset getDataset(String fileName) {
        if (fileName == null) {
            return null;
        } else {
            return Project.getActive().getDataset(fileName);
        }
    }

    /**
     * Return a list of the names of open datasets
     *
     * @return List of names.
     */
    synchronized public static List<String> names() {
        return Project.getActive().getDatasetNames();
    }

    /**
     * Return a list of the open datasets
     *
     * @return List of datasets.
     */
    synchronized public static Collection<Dataset> datasets() {
        return Project.getActive().getDatasets();
    }

    /**
     * Reads a contour file. Unused in current version
     *
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if Contour object can't be marshalled
     */
    public void readContours() throws IOException, ClassNotFoundException {
        String contourFileName = file.getPath() + ".ser";

        FileInputStream in;
        in = new FileInputStream(contourFileName);
        ObjectInputStream is = new ObjectInputStream(in);
        paths = (HashMap) is.readObject();
        in.close();
    }

    /**
     * Write contours to a file
     *
     * @throws IOException if an I/O error occurs
     */
    public void writeContours() throws IOException {
        if (paths == null) {
            return;
        }

        String contourFileName = file.getPath() + ".ser";

        try (FileOutputStream out = new FileOutputStream(contourFileName)) {
            ObjectOutputStream os = new ObjectOutputStream(out);
            os.writeObject(paths);
            os.flush();
        }
    }

    /**
     * Return a Set containing all the property names used by open datasets
     *
     * @return TreeSet containing the property names
     */
    public static TreeSet getPropertyNames() {
        TreeSet nameSet = new TreeSet();
        Collection<Dataset> datasets = Project.getActive().getDatasets();

        for (Dataset dataset : datasets) {
            Iterator iter = dataset.properties.keySet().iterator();
            while (iter.hasNext()) {
                String propName = (String) iter.next();
                nameSet.add(propName);
            }
        }
        return nameSet;
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
    static String[] exptListLoopString = {
        "_Experiment.ID",
        "_Experiment.Name",
        "_Experiment.Raw_data_flag",
        "_Experiment.NMR_spec_expt_ID",
        "_Experiment.NMR_spec_expt_label",
        "_Experiment.Sample_ID",
        "_Experiment.Sample_label",
        "_Experiment.Sample_state",
        "_Experiment.Sample_volume",
        "_Experiment.Sample_volume_units",
        "_Experiment.Sample_condition_list_ID",
        "_Experiment.Sample_condition_list_label",
        "_Experiment.Sample_spinning_rate",
        "_Experiment.Sample_angle",
        "_Experiment.NMR_tube_type",
        "_Experiment.NMR_spectrometer_ID",
        "_Experiment.NMR_spectrometer_label",
        "_Experiment.NMR_spectrometer_probe_ID",
        "_Experiment.NMR_spectrometer_probe_label",
        "_Experiment.NMR_spectral_processing_ID",
        "_Experiment.NMR_spectral_processing_label",
        "_Experiment.Experiment_list_ID",};
    static String[] exptFileLoopString = {
        "_Experiment_file.ID",
        "_Experiment_file.Name",
        "_Experiment_file.Type",
        "_Experiment_file.Directory_path",
        "_Experiment_file.Details",
        "_Experiment_file.Experiment_list_ID",};

//    public static void writeDatasetsToSTAR3(Interp interp, String chanName) throws TclException {
//        if (theFiles.isEmpty()) {
//            return;
//        }
//        FileChannel chan = (FileChannel) TclIO.getChannel(interp, chanName);
//
//        if (chan == null) {
//            throw new TclException(interp, "Couln't find channel " + chanName);
//        }
//
//        try {
//            chan.write(interp, "save_experiment_list_1\n");
//
//            chan.write(interp, "_Experiment_list.Sf_category                 ");
//            chan.write(interp, "experiment_list\n");
//
//            chan.write(interp, "_Experiment_list.Sf_framecode                 ");
//            chan.write(interp, "experiment_list_1\n");
//
//            chan.write(interp, "_Experiment_list.ID                          ");
//            chan.write(interp, "1\n");
//        } catch (IOException ioE) {
//            throw new TclException(interp, "Error writing spectral dim data");
//        }
//        String[] loopStrings = exptListLoopString;
//        String sep = " ";
//        char stringQuote = '"';
//        try {
//            chan.write(interp, "loop_\n");
//            for (int j = 0; j < loopStrings.length; j++) {
//                chan.write(interp, loopStrings[j] + "\n");
//            }
//            chan.write(interp, "\n");
//            int i = 1;
//            for (Dataset dataset : theFiles.values()) {
//                if (dataset.file == null) {
//                    continue;
//                }
//                chan.write(interp, (i++) + sep);
//                chan.write(interp, dataset.getName());
//                chan.write(interp, sep);
//
//                chan.write(interp, "no");
//                chan.write(interp, sep);
//
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//
//                chan.write(interp, "1");
//                chan.write(interp, "\n");
//
//            }
//            chan.write(interp, "stop_\n");
//            chan.write(interp, "\n");
//        } catch (IOException ioE) {
//            throw new TclException(interp, "Error writing dataset info");
//        }
//        loopStrings = exptFileLoopString;
//        try {
//            chan.write(interp, "loop_\n");
//            for (int j = 0; j < loopStrings.length; j++) {
//                chan.write(interp, loopStrings[j] + "\n");
//            }
//            chan.write(interp, "\n");
//            int i = 1;
//            for (Dataset dataset : theFiles.values()) {
//                if (dataset.file == null) {
//                    continue;
//                }
//                chan.write(interp, (i++) + sep);
//                chan.write(interp, stringQuote + dataset.file.getName() + stringQuote);
//                chan.write(interp, sep);
//
//                chan.write(interp, "nmrview");
//                chan.write(interp, sep);
//                URI uri = dataset.file.getParentFile().toURI();
//                String dirName = uri.getPath();
//                if (dirName == null) {
//                    chan.write(interp, "?");
//                } else {
//                    if (dirName.charAt(2) == ':') {
//                        dirName = uri.getPath().substring(1);
//                    }
//                    chan.write(interp, stringQuote + dirName + stringQuote);
//                }
//                chan.write(interp, sep);
//
//                chan.write(interp, "?");
//                chan.write(interp, sep);
//
//                chan.write(interp, "1");
//                chan.write(interp, "\n");
//            }
//            chan.write(interp, "stop_\n");
//            chan.write(interp, "\n");
//            chan.write(interp, "save_\n");
//            chan.write(interp, "\n\n");
//        } catch (IOException ioE) {
//            throw new TclException(interp, "Error writing dataset info");
//        }
//    }
    /**
     * Test of speed of accessing data in file
     *
     * @throws IOException if an I/O error occurs
     */
    public void speedTest() throws IOException {
        long[] times = new long[7];
        times[0] = System.currentTimeMillis();
        System.out.println("xxx");
        dataFile.sumValues();
        times[1] = System.currentTimeMillis();
        System.out.println("xxx");

        dataFile.sumFast();
        times[2] = System.currentTimeMillis();
        System.out.println("xxx");

        int[] counterSizes = new int[nDim];
        int[] dim = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            counterSizes[i] = getSize(i);
            dim[i] = i;
        }
        DimCounter counter = new DimCounter(counterSizes);
        DimCounter.Iterator cIter = counter.iterator();
        while (cIter.hasNext()) {
            int[] points = cIter.next();
            dataFile.bytePosition(points);
        }
        times[4] = System.currentTimeMillis();
        System.out.println("xxx");

        counter = new DimCounter(counterSizes);
        cIter = counter.iterator();
        int last = -1;
        while (cIter.hasNext()) {
            int[] points = cIter.next();
            readPoint(points);
        }
        times[5] = System.currentTimeMillis();
        System.out.println("xxx");

        counter = new DimCounter(counterSizes);
        cIter = counter.iterator();
        while (cIter.hasNext()) {
            int[] points = cIter.next();
            readPoint(points, dim);
        }
        times[6] = System.currentTimeMillis();
        System.out.println("xxx");
        for (int i = 1; i < times.length; i++) {
            System.out.println(i + " " + (times[i] - times[i - 1]));
        }
    }

    //new version
    /**
     * Read a vector of data values from dataset
     *
     * @param pt indices specifying range of points to read from
     * @param dim dataset dimensions that are used in pt array
     * @param rwVector the vector to put values in
     * @throws IOException if an I/O error occurs
     */
    public void readVectorFromDatasetFile(int[][] pt, int[] dim, Vec rwVector) throws IOException {
        //System.out.println("reading vector from dataset file");
        int n = 0;

//        if (vector != null) {
//            throw new IllegalArgumentException("Don't call this method on a vector type dataset");
//        }
        rwVector.resize(rwVector.getSize(), getComplex_r(dim[0]));
        rwVector.centerFreq = getSf(dim[0]);
        rwVector.dwellTime = 1.0 / getSw_r(dim[0]);
        // if reading a vector that is not full size we need to adjust the sweep width.
        //   used when reading integral vectors etc.
        //   Only do this adjustment when the dataset is not complex.  If it is complex we're probably processing it and
        //   it's possible we'll change a correct sweep width if we don't check sizes properly
        //   It is important to check the valid size, not full dataset size or we'll
        //     incorrectly adjust sweep width
        if (getComplex_r(dim[0])) {
            int dSize = getComplex_r(dim[0]) ? getVSize_r(dim[0]) / 2 : getVSize_r(dim[0]);
            if (rwVector.getSize() != dSize) {
                rwVector.dwellTime *= (double) dSize / rwVector.getSize();
            }
        } else {
            int dSize = getSize(dim[0]);
            if (rwVector.getSize() != dSize) {
                rwVector.dwellTime *= (double) dSize / rwVector.getSize();
            }

        }
        rwVector.setPh0(getPh0_r(dim[0]));
        rwVector.setPh1(getPh1_r(dim[0]));
        rwVector.setTDSize(getTDSize(dim[0]));
        rwVector.setPt(pt, dim);

        double delRef = ((getRefPt_r(dim[0]) - pt[0][0]) * getSw_r(dim[0])) / getSf(dim[0]) / getSize(dim[0]);
        rwVector.refValue = getRefValue_r(dim[0]) + delRef;
        rwVector.setFreqDomain(getFreqDomain_r(dim[0]));

        //System.err.printf("read %d %d %4d %4d %4d %4d %7.3f %7.3f %7.3f %7.3f %7.3f cmplx %b fd %b\n", dim[0],dim[1],pt[0][0],pt[0][1],pt[1][0],pt[1][1],(1.0/rwVector.dwellTime),(rwVector.refValue-delRef),rwVector.refValue,delRef,refPt_r[dim[0]],rwVector.isComplex(),rwVector.getFreqDomain());
        //System.err.println("read " + pt[dim[1]][1] + " sw "+dim[0]+" "+(1.0/rwVector.dwellTime)+" "+(rwVector.refValue-delRef)+" " + rwVector.refValue+" "+delRef+" "+refPt_r[dim[0]]+" cmplx "+rwVector.isComplex()+" fd " + rwVector.getFreqDomain());
        int[] point = new int[nDim];
        for (int i = 1; i < nDim; i++) {
            point[dim[i]] = pt[i][0];
        }
//        for (int i = 0; i < nDim; i++) {
//            System.out.printf("rv i %4d dim %4d pt0 %4d pt1 %4d size %4d vsize %4d fsize %4d\n",i,dim[i],pt[i][0],pt[i][1],size[dim[i]],vsize[dim[i]],fileDimSizes[dim[i]]);
//        }
        if (vecMat != null) {
            int j = 0;
            for (int i = pt[0][0]; i <= pt[0][1]; i++) {
                if (rwVector.isComplex()) {
                    rwVector.set(j, vecMat.getComplex(i));
                } else {
                    rwVector.setReal(j, vecMat.getReal(i));
                }
                j++;
            }

        } else {
            double dReal = 0.0;
            int j = 0;
            for (int i = pt[0][0]; i <= pt[0][1]; i++) {
                point[dim[0]] = i;
                if (rwVector.isComplex()) {
                    if ((i % 2) != 0) {
                        double dImaginary = readPoint(point);
                        rwVector.set(j, new Complex(dReal, dImaginary));
                        j++;
                    } else {
                        dReal = readPoint(point);
                    }
                } else {
                    rwVector.set(j, readPoint(point));
                    j++;
                }
            }
        }
        //System.out.println("done reading vector from dataset file");
    }

    /**
     * Read values along specified row. Only appropriate for 2D datasets
     *
     * @param row The row to read
     * @return the data values
     * @throws IOException if an I/O error occurs
     */
    public ArrayRealVector getRowVector(int row) throws IOException {
        int vecSize = getSize(0);
        int[] pt = new int[nDim];
        pt[1] = row;
        ArrayRealVector vector = new ArrayRealVector(vecSize);
        for (int i = 0; i < vecSize; i++) {
            pt[0] = i;
            vector.setEntry(i, readPoint(pt));
        }
        return vector;
    }

    /**
     * Read specified values along specified row. Only appropriate for 2D
     * datasets
     *
     * @param row the row to read
     * @param indices List of points to read along row
     * @return the data values
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if indices is null or empty
     */
    public ArrayRealVector getRowVector(int row, ArrayList<Integer> indices) throws IOException, IllegalArgumentException {
        if ((indices == null) || indices.isEmpty()) {
            throw new IllegalArgumentException("Empty or null indices");
        }
        int vecSize = indices.size();
        ArrayRealVector vector = new ArrayRealVector(vecSize);
        int[] pt = new int[nDim];
        pt[1] = row;
        for (int i = 0; i < vecSize; i++) {
            pt[0] = indices.get(i);
            vector.setEntry(i, readPoint(pt));
        }
        return vector;
    }

    /**
     * Read values along specified column. Only appropriate for 2D datasets
     *
     * @param column the column to read
     * @return the data values
     * @throws IOException if an I/O error occurs
     */
    public ArrayRealVector getColumnVector(int column) throws IOException {
        int vecSize = getSize(1);
        int[] pt = new int[nDim];
        pt[0] = column;
        ArrayRealVector vector = new ArrayRealVector(vecSize);
        for (int i = 0; i < vecSize; i++) {
            pt[1] = i;
            vector.setEntry(i, readPoint(pt));
        }
        return vector;
    }

    /**
     * Read specified values along specified column. Only appropriate for 2D
     * datasets
     *
     * @param column the column of dataset to read
     * @param indices List of points to read along column
     * @return the values
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if indices is null or empty
     */
    public ArrayRealVector getColumnVector(int column, ArrayList<Integer> indices) throws IOException, IllegalArgumentException {
        if ((indices == null) || indices.isEmpty()) {
            throw new IllegalArgumentException("Empty or null indices");
        }
        int vecSize = indices.size();
        ArrayRealVector vector = new ArrayRealVector(vecSize);
        int[] pt = new int[nDim];
        pt[0] = column;
        for (int i = 0; i < vecSize; i++) {
            pt[1] = indices.get(i);
            vector.setEntry(i, readPoint(pt));
        }
        return vector;
    }

    /**
     * Return a 2D matrix of data values from specified rows and columns
     *
     * @param rowIndices List of rows
     * @param columnIndices List of columns
     * @return the matrix of values
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if row or column indices is null or
     * empty
     */
    public Array2DRowRealMatrix getSubMatrix(ArrayList<Integer> rowIndices, ArrayList<Integer> columnIndices) throws IOException, IllegalArgumentException {
        if ((rowIndices == null) || rowIndices.isEmpty()) {
            throw new IllegalArgumentException("Empty or null rowIndices");
        }
        if ((columnIndices == null) || columnIndices.isEmpty()) {
            throw new IllegalArgumentException("Empty or null columnIndices");
        }
        int nRows = rowIndices.size();
        int nColumns = columnIndices.size();
        int[] rows = new int[nRows];
        int[] columns = new int[nColumns];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = rowIndices.get(i);
        }
        for (int i = 0; i < columns.length; i++) {
            columns[i] = columnIndices.get(i);
        }
        return getSubMatrix(rows, columns);
    }

    /**
     *
     * Return a 2D matrix of data values from specified rows and columns
     *
     * @param rowIndices List of rows
     * @param columnIndices List of columns
     * @return the matrix of values
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if row or column indices is null or
     * empty
     */
    public Array2DRowRealMatrix getSubMatrix(int[] rowIndices, int[] columnIndices) throws IOException, IllegalArgumentException {
        if ((rowIndices == null) || (rowIndices.length == 0)) {
            throw new IllegalArgumentException("Empty or null indices");
        }
        if ((columnIndices == null) || (columnIndices.length == 0)) {
            throw new IllegalArgumentException("Empty or null indices");
        }

        int nRows = rowIndices.length;
        int nColumns = columnIndices.length;
        Array2DRowRealMatrix matrix = new Array2DRowRealMatrix(nRows, nColumns);
        int[] pt = new int[nDim];
        for (int i = 0; i < nRows; i++) {
            for (int j = 0; j < nColumns; j++) {
                pt[0] = columnIndices[j];
                pt[1] = rowIndices[i];
                matrix.setEntry(i, j, readPoint(pt));
            }
        }
        return matrix;
    }

    /**
     * Make a Dataset file from a matrix of values
     *
     * @param matrix The matrix of values
     * @param fullName The name of the file to create
     * @param datasetName The name (title) of the dataset.
     * @throws DatasetException if an I/O error occurred while creating dataset
     * @throws IOException if an I/O error occurs
     */
    public static void makeDatasetFromMatrix(RealMatrix matrix, String fullName, String datasetName) throws DatasetException, IOException {
        int nRows = matrix.getRowDimension();
        int nColumns = matrix.getColumnDimension();
        int[] dimSizes = {nRows, nColumns};
        int[] pt = new int[2];
        Dataset dataset = createDataset(fullName, datasetName, dimSizes, false);
        for (int i = 0; i < nRows; i++) {
            for (int j = 0; j < nColumns; j++) {
                pt[0] = i;
                pt[1] = j;
                dataset.writePoint(pt, matrix.getEntry(i, j));
            }
        }
        dataset.close();
    }

    /**
     * Create a @see BucketMatrix from this dataset
     *
     * @param rowIndices Indices of rows to include in bucketing
     * @param columnIndices Indices of columns to include in bucketing
     * @param bucketSize size of the bucket
     * @param dataTbl Names for rows and columns
     * @return a BucketMatrix object containing the bucket values from dataset
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if row or column indices is null or
     * empty
     */
    public BucketedMatrix getBucketedSubMatrix(int[] rowIndices, int[] columnIndices, int bucketSize, String[][] dataTbl) throws IOException, IllegalArgumentException {
        if ((rowIndices == null) || (rowIndices.length == 0)) {
            throw new IllegalArgumentException("Empty or null indices");
        }
        if ((columnIndices == null) || (columnIndices.length == 0)) {
            throw new IllegalArgumentException("Empty or null indices");
        }

        int nRows = rowIndices.length;
        int nColumns = columnIndices.length;

        ArrayList<ArrayList<Integer>> bucketList = new ArrayList<>();
        ArrayList<Integer> bucketIndices = new ArrayList<>();
        ArrayList<Integer> colList;

        int lastBucket = -1;
        int jBucket = -1;
        int firstColumn = columnIndices[0];

        if (bucketSize == 0) {
            int lastIndex = -2;
            for (int i = 0; i < columnIndices.length; i++) {
                int j = columnIndices[i];
                if (j > (lastIndex + 1)) {
                    colList = new ArrayList<>();
                    bucketList.add(colList);
                    jBucket = bucketList.size() - 1;
                    bucketIndices.add(j - firstColumn);
                }
                lastIndex = j;
                colList = bucketList.get(jBucket);
                colList.add(j);
            }
        } else {
            for (int i = 0; i < columnIndices.length; i++) {
                int j = columnIndices[i];
                int iBucket = (j - firstColumn) / bucketSize;
                if (iBucket != lastBucket) {
                    lastBucket = iBucket;
                    colList = new ArrayList<>();
                    bucketList.add(colList);
                    jBucket = bucketList.size() - 1;
                    bucketIndices.add(iBucket);
                }
                colList = bucketList.get(jBucket);
                colList.add(j);
            }
        }
        int nBuckets = bucketList.size();
        Array2DRowRealMatrix matrix = new Array2DRowRealMatrix(nRows, nBuckets);
        int[] pt = new int[nDim];
        double[] ppms = new double[nBuckets];
        // colCenters contains the centers of each buckets in units of original dataset
        int[] colCenters = new int[nBuckets];
        // colIndices contains the position of each bucket in the reduced (bucketed) matrix
        int[] colIndices = new int[nBuckets];
        for (int k = 0; k < nBuckets; k++) {
            colIndices[k] = bucketIndices.get(k);
        }
        for (int i = 0; i < nRows; i++) {
            for (int k = 0; k < nBuckets; k++) {
                double sum = 0.0;
                colList = bucketList.get(k);
                double sumPPM = 0.0;
                double sumCol = 0.0;
                for (int j = 0; j < colList.size(); j++) {
                    pt[0] = colList.get(j);
                    pt[1] = rowIndices[i];
                    sumPPM += pointToPPM(0, pt[0]);
                    sumCol += pt[0];
                    sum += readPoint(pt);
                }
                matrix.setEntry(i, k, sum);
                ppms[k] = sumPPM / colList.size();
                colCenters[k] = (int) Math.round(sumCol / colList.size());
            }
        }
        BucketedMatrix bMat = new BucketedMatrix(matrix, rowIndices, colIndices, colCenters, ppms, dataTbl);
        return bMat;
    }

    /**
     * Read vector from dataset
     *
     * @param vector Store dataset values in this vec
     * @param index the index of vector to read
     * @param iDim read values along this dimension index
     * @throws IOException if an I/O error occurs
     */
    public void readVector(Vec vector, int index, int iDim) throws IOException {
        int[] dim = new int[nDim];
        int[][] pt = new int[nDim][2];
        for (int i = 0; i < nDim; i++) {
            dim[i] = i;
            if (iDim == i) {
                pt[i][0] = 0;
                if (vector.isComplex()) {
                    pt[i][1] = 2 * vector.getSize() - 1;
                } else {
                    pt[i][1] = vector.getSize() - 1;
                }
            } else {
                pt[i][0] = index;
                pt[i][1] = index;
            }
        }
        readVectorFromDatasetFile(pt, dim, vector);
    }

    /**
     * Read vector from dataset
     *
     * @param vector Store dataset values in this vec
     * @param indices the indices of vector to read
     * @param iDim read values along this dimension index
     * @throws IOException if an I/O error occurs
     */
    public void readVector(Vec vector, int[] indices, int iDim) throws IOException {
        int[] dim = new int[nDim];
        int[][] pt = new int[nDim][2];
        for (int i = 0; i < nDim; i++) {
            dim[i] = i;
            if (iDim == i) {
                pt[i][0] = 0;
                if (vector.isComplex()) {
                    pt[i][1] = 2 * vector.getSize() - 1;
                } else {
                    pt[i][1] = vector.getSize() - 1;
                }
            } else {
                pt[i][0] = indices[i];
                pt[i][1] = indices[i];
            }
        }
        readVectorFromDatasetFile(pt, dim, vector);
    }

    /**
     * Read a vector from the dataset at the location stored in the vector's
     * header
     *
     * @param vector Store data values in this vector object.
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException If the vector doesn't have a location in
     * header.
     */
    public void readVector(Vec vector) throws IOException, IllegalArgumentException {
        if ((vector.getPt() == null) || (vector.getDim() == null)) {
            throw new IllegalArgumentException("Vector doesn't have stored location");
        }
        readVectorFromDatasetFile(vector.getPt(), vector.getDim(), vector);
    }

    /**
     * Write vector to dataset
     *
     * @param vector Store dataset values in this vec
     * @param index the index of vector to write
     * @param iDim write values along this dimension index
     * @throws IOException if an I/O error occurs
     */
    public void writeVector(Vec vector, int index, int iDim) throws IOException {
        int[] dim = new int[nDim];
        int[][] pt = new int[nDim][2];
        for (int i = 0; i < nDim; i++) {
            dim[i] = i;
            if (iDim == i) {
                pt[i][0] = 0;
                if (vector.isComplex()) {
                    pt[i][1] = 2 * vector.getSize() - 1;
                } else {
                    pt[i][1] = vector.getSize() - 1;
                }
            } else {
                pt[i][0] = index;
                pt[i][1] = index;
            }
        }
        writeVecToDatasetFile(pt, dim, vector);
    }

    /**
     * Write vector to the dataset at the specified location. The location is
     * specified with indices indicating the offset for each dimension and the
     * vector is written along the specified dimension. The location at which
     * the vector is written is stored in the vector header.
     *
     * @param vector the vector to write
     * @param indices The location at which to write the vector.
     * @param iDim Vector is written parallel to this dimension.
     * @throws IOException if an I/O exception occurs
     * @throws IllegalArgumentException if dataset stores data in a Vec object
     * (not dataset file)
     */
    public void writeVector(Vec vector, int[] indices, int iDim) throws IOException, IllegalArgumentException {
        int[] dim = new int[nDim];
        int[][] pt = new int[nDim][2];
        for (int i = 0, j = 0; i < nDim; i++) {
            if (iDim == i) {
                pt[i][0] = 0;
                if (vector.isComplex()) {
                    pt[i][1] = 2 * vector.getSize() - 1;
                } else {
                    pt[i][1] = vector.getSize() - 1;

                }
                dim[0] = iDim;
            } else {
                dim[j + 1] = i;
                pt[i][0] = indices[j];
                pt[i][1] = indices[j];
                j++;
            }
        }
        vector.setPt(pt, dim);
        writeVector(vector);
    }

    public void writeMatrixType(MatrixType matrixType) throws IOException {
        if (matrixType instanceof Vec) {
            writeVector((Vec) matrixType);
        } else {
            MatrixND matrix = (MatrixND) matrixType;
            writeMatrixNDToDatasetFile(matrix.getDim(), matrix);
        }
    }

    /**
     * Write the vector to the dataset at the location stored in the vector.
     *
     * @param vector the vector to write
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if dataset stores data in a Vec object
     * (not dataset file)
     */
    public void writeVector(Vec vector) throws IOException, IllegalArgumentException {
        if ((vector.getPt() == null) || (vector.getDim() == null)) {
            throw new IllegalArgumentException("Vector doesn't have stored location");
        }
        writeVecToDatasetFile(vector.getPt(), vector.getDim(), vector);
    }

    /**
     * Write the vector out to the specified location of dataset. The vector is
     * written along the dimension specified in the first entry of the dim
     * array.
     *
     * @param pt index in points where vector should be written.
     * @param dim Specify the dimension that each entry in the pt array refers
     * to
     * @param vector the vector to write
     * @throws IOException if an I/O error occurs
     */
    public void writeVecToDatasetFile(int[][] pt, int[] dim, Vec vector) throws IOException {

        int n = 0;
        if (vecMat != null) {
            throw new IllegalArgumentException("Don't call this method on a vector type dataset");
        }

        setDirty(true);
        int[] point = new int[nDim];
        for (int i = 1; i < nDim; i++) {
            point[dim[i]] = pt[i][0];
        }
        for (int i = 0; i < nDim; i++) {
//            System.out.printf("wv i %4d dim %4d pt0 %4d pt1 %4d size %4d vsize %4d fsize %4d\n",i,dim[i],pt[i][0],pt[i][1],size[dim[i]],vsize[dim[i]],fileDimSizes[dim[i]]);
            if (pt[i][0] == pt[i][1]) {
                if ((pt[i][0] + 1) > getFileDimSize(dim[i])) {
                    throw new ProcessingException("dataset size for DIM(" + (dim[i] + 1) + ") = "
                            + getFileDimSize(dim[i]) + " too small, should be at least " + (pt[i][0] + 1));
                }
                if ((pt[i][0] + 1) > vsize[dim[i]]) {
                    setVSize(dim[i], (pt[i][0] + 1));
                }
            } else {
                if ((pt[i][1] + 1) > getFileDimSize(dim[i])) {
                    throw new ProcessingException("dataset size for DIM(" + (dim[i] + 1) + ") = "
                            + getFileDimSize(dim[i]) + " too small, should be at least " + (pt[i][1] + 1));
                }
                if ((pt[i][1] + 1) > vsize[dim[i]]) {
                }
                setVSize(dim[i], (pt[i][1] - pt[i][0] + 1));
            }
        }
        dataFile.writeVector(pt[0][0], pt[0][1], point, dim[0], scale, vector);

        setSf(dim[0], vector.centerFreq);
        setSw(dim[0], 1.0 / vector.dwellTime);

        //  FIXME should have flag to allow/disallow updating reference
        double dimRefPoint = (getVSize(dim[0]) / 2);
        double delRef = dimRefPoint * getSw(dim[0]) / getSf(dim[0]) / getVSize(dim[0]);
        double dimRefValue = vector.refValue - delRef;

        setRefValue(dim[0], dimRefValue);
        setRefPt(dim[0], dimRefPoint);
        setRefUnits(dim[0], 3);

        setFreqDomain(dim[0], vector.getFreqDomain());
        setComplex(dim[0], vector.isComplex());
        setPh0(dim[0], vector.getPH0());
        setPh1(dim[0], vector.getPH1());
        setExtFirst(dim[0], vector.getExtFirst());
        setExtLast(dim[0], vector.getExtLast());
        setZFSize(dim[0], vector.getZFSize());
        setTDSize(dim[0], vector.getTDSize());

//        System.err.printf("write %d %d %4d %4d %4d %4d %7.3f %7.3f %7.3f %7.3f %7.3f cmplx %b fd %b\n", dim[0],dim[1],pt[0][0],pt[0][1],pt[1][0],pt[1][1],(1.0/rwVector.dwellTime),(rwVector.refValue-delRef),rwVector.refValue,delRef,refPt_r[dim[0]],rwVector.isComplex(),rwVector.getFreqDomain());
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

    /**
     * Copy header from this dataset to another dataset
     *
     * @param targetDataset dataset to copy header to
     */
    public void copyHeader(Dataset targetDataset) {
        for (int i = 0; i < nDim; i++) {
            copyHeader(targetDataset, i);
        }
        targetDataset.setSolvent(getSolvent());
        targetDataset.setTitle(getTitle());
        targetDataset.writeHeader();
    }

    public void copyHeader(Dataset targetDataset, int iDim) {
        targetDataset.setSf(iDim, getSf(iDim));
        targetDataset.setSw(iDim, getSw(iDim));
        targetDataset.setSw_r(iDim, getSw_r(iDim));
        targetDataset.setRefValue_r(iDim, getRefValue_r(iDim));
        targetDataset.setRefValue(iDim, getRefValue(iDim));
        targetDataset.setRefPt_r(iDim, getRefPt_r(iDim));
        targetDataset.setRefPt(iDim, getRefPt(iDim));
        targetDataset.setRefUnits(iDim, getRefUnits(iDim));
        targetDataset.setLabel(iDim, getLabel(iDim));
        targetDataset.setDlabel(iDim, getDlabel(iDim));
        targetDataset.setNucleus(iDim, getNucleus(iDim));
        targetDataset.setFreqDomain(iDim, getFreqDomain(iDim));
        targetDataset.setFreqDomain_r(iDim, getFreqDomain_r(iDim));
        targetDataset.setComplex(iDim, getComplex(iDim));
        targetDataset.setComplex_r(iDim, getComplex_r(iDim));
        targetDataset.setVSize_r(iDim, getVSize_r(iDim));
        targetDataset.setVSize(iDim, getVSize(iDim));

    }

    public void saveMemoryFile() throws IOException, DatasetException {
        if (isMemoryFile()) {
            copyDataset(fileName);
        }
    }

    /**
     * Copy dataset to a new file
     *
     * @param newFileName File name of new dataset.
     * @throws IOException if an I/O error occurs
     * @throws DatasetException if an I/O error occured while creating dataset
     */
    public void copyDataset(String newFileName) throws IOException, DatasetException {
        int[][] pt = new int[nDim][2];
        int[] dim = new int[nDim];
        dim[0] = 0;
        pt[0][0] = 0;
        pt[0][1] = 0;

        int[] datasetSizes = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            dim[i] = i;
            pt[i][0] = 0;
            pt[i][1] = getSize(i) - 1;
            datasetSizes[i] = getSize(i);
        }
        int newSize = pt[0][1] - pt[0][0] + 1;

        Dataset newDataset = Dataset.createDataset(newFileName, newFileName, datasetSizes, false);

        Vec scanVec = new Vec(newSize, false);
        ScanRegion scanRegion = new ScanRegion(pt, dim, this);
        int nEntries = scanRegion.buildIndex();
        int origSize = pt[0][1];
        for (int iEntry = 0; iEntry < nEntries; iEntry++) {
            int[] iE = scanRegion.getIndexEntry(iEntry);
            pt[0][1] = origSize;
            for (int jDim = 1; jDim < nDim; jDim++) {
                pt[jDim][0] = iE[jDim];
                pt[jDim][1] = iE[jDim];
            }
            readVectorFromDatasetFile(pt, dim, scanVec);
            newDataset.writeVector(scanVec);
        }
        for (int i = 0; i < nDim; i++) {
            newDataset.setSf(i, getSf(i));
            newDataset.setSw(i, getSw(i));
            newDataset.setSw_r(i, getSw_r(i));
            newDataset.setRefValue_r(i, getRefValue_r(i));
            newDataset.setRefValue(i, getRefValue(i));
            newDataset.setRefPt_r(i, getRefPt_r(i));
            newDataset.setRefPt(i, getRefPt(i));
            newDataset.setRefUnits(i, getRefUnits(i));
            newDataset.setLabel(i, getLabel(i));
            newDataset.setDlabel(i, getDlabel(i));
            newDataset.setNucleus(i, getNucleus(i));
            newDataset.setValues(i, getValues(i));
            newDataset.setComplex(i, getComplex(i));
            newDataset.setFreqDomain(i, getFreqDomain(i));
            newDataset.setPh0(i, getPh0(i));
            newDataset.setPh1(i, getPh1(i));
            newDataset.setPh0_r(i, getPh0_r(i));
            newDataset.setPh1_r(i, getPh1_r(i));
        }
        newDataset.setNFreqDims(getNFreqDims());
        newDataset.setSolvent(getSolvent());
        newDataset.setTitle(getTitle());

        newDataset.writeHeader();
        newDataset.writeParFile();
        newDataset.close();
    }

    /**
     * Iterator for looping over vectors in dataset
     */
    private class VecIterator implements Iterator<Vec> {

        ScanRegion scanRegion;
        Vec vec;
        int[][] pt = new int[nDim][2];
        int[] dim = new int[nDim];
        int origSize;

        VecIterator(Dataset dataset, int iDim) {
            dim[0] = iDim;
            pt[0][0] = 0;
            pt[0][1] = 0;
            int j = 0;
            for (int i = 1; i < nDim; i++) {
                if (j == iDim) {
                    j++;
                }

                dim[i] = j;
                pt[i][0] = 0;
                pt[i][1] = getSize(dim[i]) - 1;
                j++;
            }
            pt[0][1] = getSize(iDim) - 1;
            origSize = pt[0][1];
            int newSize = pt[0][1] - pt[0][0] + 1;
            vec = new Vec(newSize, false);
            scanRegion = new ScanRegion(pt, dim, dataset);
        }

        @Override
        public boolean hasNext() {
            nextVector();
            return vec != null;
        }

        @Override
        public Vec next() {
            return vec;
        }

        /**
         *
         */
        public synchronized void nextVector() {
            int[] iE = scanRegion.nextPoint();
            if (iE.length == 0) {
                vec = null;
            } else {
                pt[0][1] = origSize;
                for (int jDim = 1; jDim < nDim; jDim++) {
                    pt[jDim][0] = iE[jDim];
                    pt[jDim][1] = iE[jDim];
                }
                try {
                    readVectorFromDatasetFile(pt, dim, vec);
                } catch (IOException ioE) {
                    vec = null;
                }
            }
        }
    }

    /**
     * Iterator for looping over vectors in dataset
     */
    private class VecIndexIterator implements Iterator<int[][]> {

        ScanRegion scanRegion;
        int[][] pt = new int[nDim][2];
        int[] dim = new int[nDim];
        int origSize;

        VecIndexIterator(Dataset dataset, int iDim) {
            dim[0] = iDim;
            pt[0][0] = 0;
            pt[0][1] = 0;
            int j = 0;
            for (int i = 1; i < nDim; i++) {
                if (j == iDim) {
                    j++;
                }

                dim[i] = j;
                pt[i][0] = 0;
                pt[i][1] = getSize(dim[i]) - 1;
                j++;
            }
            pt[0][1] = getSize(iDim) - 1;
            origSize = pt[0][1];
            int newSize = pt[0][1] - pt[0][0] + 1;
            scanRegion = new ScanRegion(pt, dim, dataset);
        }

        public int[] getDim() {
            return dim;
        }

        @Override
        public boolean hasNext() {
            nextVector();
            return pt != null;
        }

        @Override
        public int[][] next() {
            int[][] result = new int[pt.length][2];
            for (int i = 0; i < pt.length; i++) {
                result[i] = pt[i].clone();
            }
            return result;
        }

        /**
         *
         */
        public synchronized void nextVector() {
            int[] iE = scanRegion.nextPoint();
            if (iE.length == 0) {
                pt = null;
            } else {
                pt[0][1] = origSize;
                for (int jDim = 1; jDim < nDim; jDim++) {
                    pt[jDim][0] = iE[jDim];
                    pt[jDim][1] = iE[jDim];
                }
            }
        }
    }

    /**
     * Get iterator that allows iterating over all the vectors along the
     * specified dimension of the dataset.
     *
     * @param iDim Index of dataset dimension to read vectors from
     * @return iterator an Iterator to iterate over vectors in dataset
     * @throws IOException if an I/O error occurs
     */
    synchronized public Iterator<Vec> vectors(int iDim) throws IOException {
        VecIterator vecIter = new VecIterator(this, iDim);
        return vecIter;
    }

    /**
     * Get iterator that allows iterating over the indices of all the vectors
     * along the specified dimension of the dataset.
     *
     * @param iDim Index of dataset dimension to read vectors from
     * @return iterator an Iterator to iterate over vectors in dataset
     * @throws IOException if an I/O error occurs
     */
    synchronized public Iterator<int[][]> indexer(int iDim) throws IOException {
        VecIndexIterator vecIter = new VecIndexIterator(this, iDim);
        return vecIter;
    }

    /**
     * Get iterator that allows iterating over all the points in the file
     *
     * @return iterator an Iterator to iterate over points in dataset
     * @throws IOException if an I/O error occurs
     */
    synchronized public Iterator pointIterator() throws IOException {
        int[] mPoint = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            mPoint[nDim - i - 1] = getSize(i) - 1;
        }
        MultidimensionalCounter counter = new MultidimensionalCounter(mPoint);
        MultidimensionalCounter.Iterator iter = counter.iterator();
        return iter;
    }

    public List<int[][]> getIndices(int iDim, int start, int end) throws IOException {
        Iterator<int[][]> iter = indexer(iDim);
        List<int[][]> indices = new ArrayList<>();
        if (start < 0) {
            start = 0;
        }
        if (end >= getSize(iDim)) {
            end = getSize(iDim) - 1;
        }
        while (iter.hasNext()) {
            int[][] pt = iter.next();
            pt[0][0] = start;
            pt[0][1] = end;
            indices.add(pt);
        }
        return indices;
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
        newRegion.removeOverlapping((TreeSet) regions);
        regions.add(newRegion);
        try {
            newRegion.measure(this);
        } catch (IOException ex) {
            Logger.getLogger(Dataset.class.getName()).log(Level.SEVERE, null, ex);
        }
        return newRegion;
    }

    public static void setMinimumTitles() {
        String[] names = new String[datasets().size()];
        int i = 0;
        for (Dataset dataset : datasets()) {
            names[i++] = dataset.getName();
        }
        String prefix = StringUtils.getCommonPrefix(names);
        System.out.println("prefix " + prefix);
        datasets().forEach((dataset) -> {
            String name = dataset.getName();
            String title = StringUtils.removeStart(name, prefix);
            title = StringUtils.removeEndIgnoreCase(title, ".nv");
            System.out.println("title " + title);
            dataset.setTitle(title);
        });
    }

    public LineShapeCatalog getLSCatalog() {
        return simVecs;
    }

    public final void loadLSCatalog() throws IOException {
        String dirName = file.getParent();
        String datasetFileName = file.getName();

        int index = datasetFileName.lastIndexOf(".");
        String shapeName = datasetFileName.substring(0, index) + "_lshapes.txt";
        String shapeFileName = dirName + File.separator + shapeName;
        File shapeFile = new File(shapeFileName);
        if (shapeFile.exists() && shapeFile.canRead()) {
            simVecs = LineShapeCatalog.loadSimFids(shapeFileName, nDim);
            System.out.println("simVecs " + simVecs);
        }
    }

    public void subtractPeak(Peak peak) throws IOException {
        if (simVecs != null) {
            simVecs.addToDataset(this, peak, -1.0);
        }
    }

    public void addPeakList(PeakList peakList, double scale) throws IOException {
        if (simVecs != null) {
            simVecs.addToDataset(this, peakList, scale);
        }

    }

    DimCounter.Iterator getPointIterator() {
        int[] counterSizes = new int[nDim];
        int[] dim = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            counterSizes[i] = getSize(i);
            dim[i] = i;
        }
        DimCounter counter = new DimCounter(counterSizes);
        DimCounter.Iterator cIter = counter.iterator();
        return cIter;
    }

    public void clear() throws IOException {
        DimCounter.Iterator cIter = getPointIterator();
        while (cIter.hasNext()) {
            int[] points = cIter.next();
            writePoint(points, 0.0);
        }
    }

    public boolean bufferExists(String bufferName) {
        return buffers.containsKey(bufferName);
    }

    public boolean removeBuffer(String bufferName) {
        return buffers.remove(bufferName) != null;
    }

    public double[] getBuffer(String bufferName) {
        double[] buffer = buffers.get(bufferName);
        int bufferSize = 1;
        for (int i = 0; i < nDim; i++) {
            bufferSize *= getSize(i);
        }
        if ((buffer == null) || (buffer.length != bufferSize)) {
            buffer = new double[bufferSize];
            buffers.put(bufferName, buffer);
        }
        return buffer;
    }

    public void toBuffer(String bufferName) throws IOException {
        double[] buffer = getBuffer(bufferName);
        DimCounter.Iterator cIter = getPointIterator();
        int j = 0;
        while (cIter.hasNext()) {
            int[] points = cIter.next();
            double value = readPoint(points);
            buffer[j++] = value;
        }
    }

    public void fromBuffer(String bufferName) throws IOException {
        if (bufferExists(bufferName)) {
            double[] buffer = getBuffer(bufferName);
            DimCounter.Iterator cIter = getPointIterator();
            int j = 0;
            while (cIter.hasNext()) {
                int[] points = cIter.next();
                double value = buffer[j++];
                writePoint(points, value);
            }
        } else {
            throw new IllegalArgumentException("No buffer named " + bufferName);
        }
    }

    public void phaseDim(int iDim, double ph0, double ph1) throws IOException {
        Iterator<Vec> vecIter = vectors(iDim);
        if (!isWritable()) {
            changeWriteMode(true);
        }
        while (vecIter.hasNext()) {
            Vec vec = vecIter.next();
            if (vec.isReal()) {
                vec.hft();
            }
            vec.phase(ph0, ph1, false, true);
            writeVector(vec);
        }
        double dph0 = Util.phaseMin(getPh0(iDim) + ph0);
        double dph1 = Util.phaseMin(getPh1(iDim) + ph1);
        setPh0(iDim, dph0);
        setPh0_r(iDim, dph0);
        setPh1(iDim, dph1);
        setPh1_r(iDim, dph1);
        writeHeader();
        dataFile.force();
    }

    public double[] autoPhase(int iDim, boolean firstOrder, int winSize, double ratio, double ph1Limit, IDBaseline2.ThreshMode threshMode) throws IOException {
        if (!isWritable()) {
            changeWriteMode(true);
        }
        DatasetPhaser phaser = new DatasetPhaser(this);
        phaser.setup(iDim, winSize, ratio, threshMode);
        double dph0 = 0.0;
        double dph1 = 0.0;
        if (firstOrder) {
            double[] phases = phaser.getPhase(ph1Limit);
            phaser.applyPhases2(iDim, phases[0], phases[1]);
            dph0 = phases[0];
            dph1 = phases[1];
        } else {
            dph0 = phaser.getPhaseZero();
            phaser.applyPhases2(iDim, dph0, 0.0);
        }
        setPh0(iDim, dph0);
        setPh0_r(iDim, dph0);
        setPh1(iDim, dph1);
        setPh1_r(iDim, dph1);
        writeHeader();
        dataFile.force();
        double[] result = {dph0, dph1};
        return result;
    }

}
