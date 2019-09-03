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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.stream.Collectors;
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
import org.nmrfx.processor.processing.LineShapeCatalog;
import org.renjin.sexp.AttributeMap;
import org.renjin.sexp.DoubleVector;
import org.renjin.sexp.DoubleArrayVector;
import org.renjin.sexp.IntArrayVector;
import org.renjin.sexp.SEXP;

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
    private static HashMap<String, Dataset> theFiles = new HashMap<>();
    private final static int NV_HEADER_SIZE = 2048;
    private final static int UCSF_HEADER_SIZE = 180;
    private final static int LABEL_MAX_BYTES = 16;
    private final static int SOLVENT_MAX_BYTES = 24;

    MappedMatrixInterface dataFile = null;
    String details = "";
    private Vec vecMat = null;
    private String fileName;
    private String canonicalName;
    private String title;
    private File file = null;
    private int blockHeaderSize;
    private int fileHeaderSize;
    private int blockElements;
    private int nDim;
    private int[] size;
    private int[] strides;
    private int[] fileDimSizes;
    private int[] vsize;
    private int[] vsize_r;
    private int[] tdSize;
    private int[] zfSize;
    private int[] extFirst;
    private int[] extLast;
    private long fileSize;
    private int magic;
    private int[] blockSize;
    private int[] offsetPoints;
    private int[] offsetBlocks;
    private int[] nBlocks;
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
    private RandomAccessFile raFile = null;
    Set<DatasetRegion> regions;
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
        unsafeSetAttributes(AttributeMap.builder().addAllFrom(getAttributes()).setDim(new IntArrayVector(size)).build());
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
        } catch (IOException ioE) {
            value = DoubleVector.NA;
        }
        return value;
    }

    @Override
    public int length() {
        int length = 1;
        for (int sz : size) {
            length *= sz;
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
     * @throws IOException if an I/O error occurs
     */
    public Dataset(String fullName, String name, boolean writable)
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
        int headerStatus;
        if (fullName.contains(".ucsf")) {
            headerStatus = readHeaderUCSF();
        } else {
            headerStatus = readHeader();
        }
        DatasetParameterFile parFile = new DatasetParameterFile(this);
        parFile.readFile();
        if (headerStatus == 0) {
            dataFile = new BigMappedMatrixFile(this, raFile, writable);
        }
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
        title = fileName;
        nDim = 1;
        size = new int[1];
        strides = new int[1];
        fileDimSizes = new int[1];
        vsize = new int[1];
        vsize_r = new int[1];
        tdSize = new int[1];
        zfSize = new int[1];
        extFirst = new int[1];
        extLast = new int[1];
        size[0] = vector.getSize();
        strides[0] = 1;
        vsize[0] = 0;
        vsize_r[0] = 0;
        tdSize[0] = vector.getTDSize();
        fileSize = size[0];
        newHeader();
        fileHeaderSize = NV_HEADER_SIZE;
        blockSize[0] = vector.getSize();
        nBlocks[0] = 1;
        offsetBlocks[0] = 1;
        offsetPoints[0] = 1;
        blockElements = blockSize[0] * 4;
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
            int[] dimSizes) throws DatasetException {
        //LOGGER.info("Make dataset {}", fullName);
        try {
            raFile = new RandomAccessFile(fullName, "rw");
            file = new File(fullName);

            canonicalName = file.getCanonicalPath();
            fileName = file.getName().replace(' ', '_');

            this.nDim = dimSizes.length;

            int i;
            this.size = new int[this.nDim];
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
                this.size[i] = dimSizes[i];
                fileDimSizes[i] = dimSizes[i];
                fileSize *= this.size[i];
                nBytes *= dimSizes[i];
            }

            this.title = title;
            newHeader();
            if (fullName.contains(".ucsf")) {
                fileHeaderSize = UCSF_HEADER_SIZE + 128 * nDim;
            } else {
                fileHeaderSize = NV_HEADER_SIZE;
            }
            setBlockSize(4096);
            dimDataset();
            writeHeader();
            dataFile = new BigMappedMatrixFile(this, raFile, true);
            dataFile.zero();
            dataFile.close();
            DatasetParameterFile parFile = new DatasetParameterFile(this);
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
            this.size = new int[this.nDim];
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
                this.size[i] = dimSizes[i];
                fileDimSizes[i] = dimSizes[i];
                fileSize *= this.size[i];
                nBytes *= dimSizes[i];
            }

            this.title = title;
            newHeader();
            fileHeaderSize = NV_HEADER_SIZE;
            setBlockSize(4096);
            dimDataset();
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
     * @throws DatasetException if an I/O error occurred when writing out file
     */
    public static void createDataset(String fullName, String title, int[] dimSizes) throws DatasetException {
        Dataset dataset = new Dataset(fullName, title, dimSizes);
        dataset.close();
    }

    public void readParFile() {
        DatasetParameterFile parFile = new DatasetParameterFile(this);
        parFile.readFile();
    }

    public void writeParFile(String fileName) {
        DatasetParameterFile parFile = new DatasetParameterFile(this);
        parFile.writeFile(fileName);
    }

    public void writeParFile() {
        DatasetParameterFile parFile = new DatasetParameterFile(this);
        parFile.writeFile();
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
     * Check if there is an open file with the specified name.
     *
     * @param name the name to check
     * @return true if there is an open file with that name
     */
    public static boolean checkExistingName(final String name) {
        boolean exists = false;
        Dataset existingFile = (Dataset) theFiles.get(name);
        if (existingFile != null) {
            exists = true;
        }
        return exists;
    }

    /**
     * Check if there is an open file with the specified file path
     *
     * @param fullName the full path name to check
     * @return true if the file is open
     * @throws IOException if an I/O error occurs
     */
    public static ArrayList<String> checkExistingFile(final String fullName) throws IOException {
        File file = new File(fullName);
        String testPath;
        testPath = file.getCanonicalPath();
        ArrayList<String> names = new ArrayList<>();
        theFiles.values().stream().filter((dataset) -> (dataset.canonicalName.equals(testPath))).forEach((dataset) -> {
            names.add(dataset.getName());
        });
        return names;
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
        if (dot != -1) {
            ext = rootName.substring(dot);
            rootName = rootName.substring(0, dot);
        }
        int index = 0;
        do {
            index++;
            newName = rootName + "_" + index + ext;
        } while (theFiles.get(newName) != null);
        return newName;
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
                dataFile = new BigMappedMatrixFile(this, raFile, writable);
            }
        }
    }

    /**
     * Writes out 0 bytes into full dataset size, thereby taking up appropriate
     * space in file system.
     */
    public void initialize() {
        int nBytes = 4;

        for (int i = 0; i < this.nDim; i++) {
            nBytes *= this.getSize(i);
        }

        byte[] buffer = new byte[4096];

        for (int i = 0; i < (nBytes / 4096); i++) {
            DataUtilities.writeBytes(raFile, buffer, fileHeaderSize + (i * 4096), 4096);
        }

        initialized = true;
        hasBeenWritten = true;
    }

    /**
     * Resize the file to the specified sizes
     *
     * @param dimSizes The new sizes
     * @throws IOException if an I/O error occurs
     */
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
     * Get the block size of the dataset along the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return the size
     */
    public int getBlockSize(int iDim) {
        final int value;
        if (vecMat == null) {
            value = blockSize[iDim];
        } else {
            value = size[iDim];
        }
        return value;
    }

    /**
     * Get array containing size of block in each dimension
     *
     * @return array of block sizes
     */
    public int[] getBlockSizes() {
        return blockSize.clone();
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
     * Get the offsets for blocks in each dimension
     *
     * @return the offsets as an int array
     */
    public int[] getOffsetBlocks() {
        return offsetBlocks.clone();
    }

    /**
     * Get the offsets for points for each dimension within a block.
     *
     * @return the offsets as an int array
     */
    public int[] getOffsetPoints() {
        return offsetPoints.clone();
    }

    /**
     * Return the number of blocks along each dataset dimension
     *
     * @return an array containing the number of blocks
     */
    public int[] getNBlocks() {
        return nBlocks.clone();
    }

    /**
     * Return the number of elements in each block
     *
     * @return the number of elements
     */
    public int getBlockElements() {
        return blockElements;
    }

    /**
     * Return the file header size
     *
     * @return the size
     */
    public int getFileHeaderSize() {
        return fileHeaderSize;
    }

    /**
     * Set the file header size
     *
     * @param size the header size
     */
    public void setFileHeaderSize(int size) {
        fileHeaderSize = size;
    }

    /**
     * Set the block header size
     *
     * @param size the block header size
     */
    public void setBlockHeaderSize(int size) {
        blockHeaderSize = size;
    }

    /**
     * Return the block header size
     *
     * @return the size
     */
    public int getBlockHeaderSize() {
        return blockHeaderSize;
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

    private void addFile(String fileName) {
        theFiles.put(fileName, this);
        for (DatasetListener observer : observers) {
            try {
                observer.datasetAdded(this);
            } catch (RuntimeException e) {
            }
        }

    }

    private void removeFile(String fileName) {
        theFiles.remove(fileName);
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
        if (null != theFiles.remove(fileName)) {
            fileName = newName;
            title = fileName;
            theFiles.put(newName, this);
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
            raFile = null;
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
        final int value;
        if (vecMat == null) {
            value = size[iDim];
        } else {
            value = vecMat.getSize();
        }
        return value;
    }

    /**
     * Set the size of the dataset along the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param size the size to set
     */
    public void setSize(final int iDim, final int size) {
        this.size[iDim] = size;
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
            value = size[iDim];
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
        int dims = size.length;
        int[] sizes = new int[dims];
        for (int i = 0; i < dims; i++) {
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
        lvlSet = true;
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
            if (values.length != size[iDim]) {
                throw new IllegalArgumentException("Number of values (" + values.length + ") must equal dimension size (" + size[iDim] + ") for dim " + iDim);
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
            if (values.size() != size[iDim]) {
                throw new IllegalArgumentException("Number of values (" + values.size() + ") must equal dimension size (" + size[iDim] + ") for dim " + iDim);
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
            blockSize[0] = vecMat.getSize();
            blockElements = blockSize[0] * 4;
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

        if (vecMat != null) {
            setSize(0, vecMat.getSize());
            blockSize[0] = vecMat.getSize();
            blockElements = blockSize[0] * 4;
        }
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

        if (vecMat != null) {
            setSize(0, vecMat.getSize());
            blockSize[0] = vecMat.getSize();
            blockElements = blockSize[0] * 4;
        }
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
                sizes[iDim] = size[pdim[iDim]] - p2[iDim][0] - p2[iDim][1] + 1;
            }
        }
        int nPoints = 1;
        for (int dimSize : sizes) {
            nPoints *= dimSize;
        }
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
                if (aCounts[j] >= size[pdim[j]]) {
                    aCounts[j] -= size[pdim[j]];
                } else if (aCounts[j] < 0) {
                    aCounts[j] += size[pdim[j]];
                }
                j++;
            }
            if (inDataset) {
                boolean ok = false;
                for (int k = 0; k < cpt.length; k++) {
                    int i = 0;
                    double delta2 = 0.0;
                    for (int value : aCounts) {
                        delta2 += ((value - cpt[k][i]) * (value - cpt[k][i])) / (0.47 * width[k][i] * width[k][i]);
                        i++;
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

    /**
     * Set the number of dimensions for this dataset. Will reset all reference
     * information.
     *
     * @param nDim Number of dataset dimensions
     */
    public void setNDim(int nDim) {
        this.nDim = nDim;
        setNDim();
    }

    /**
     * Will reset all reference fields so they are sized corresponding to
     * current dataset dimension.
     *
     */
    public void setNDim() {
        size = new int[nDim];
        strides = new int[nDim];
        fileDimSizes = new int[nDim];
        vsize = new int[nDim];
        vsize_r = new int[nDim];
        tdSize = new int[nDim];
        zfSize = new int[this.nDim];
        extFirst = new int[this.nDim];
        extLast = new int[this.nDim];
        blockSize = new int[nDim];
        offsetPoints = new int[nDim];

        offsetBlocks = new int[nDim];
        nBlocks = new int[nDim];

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
     * Set size of file along specified dimension. Causes reset of various size
     * related parameters.
     *
     * @param iDim Dataset dimension index
     * @param newSize new size for dimension.
     */
    public void setNewSize(int iDim, int newSize) {
        if ((iDim >= 0) && (iDim < nDim) && (newSize > 0)) {
            setSize(iDim, newSize);
        }

        dimDataset();
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
     * Set the size of blocks along the specified dimension. Setting this will
     * reset various size related parameters.
     *
     * @param iDim Dataset dimension index
     * @param newSize New block size
     */
    public void setBlockSize(int iDim, int newSize) {
        if ((iDim >= 0) && (iDim < nDim) && (newSize > 0)) {
            blockSize[iDim] = newSize;
        }

        dimDataset();
    }

    /**
     * Set the size of blocks along the specified dimension. Doesn't call
     * dimDataset;
     *
     * @param iDim Dataset dimension index
     * @param newSize New block size
     */
    public void setBlockSizeValue(int iDim, int newSize) {
        if ((iDim >= 0) && (iDim < nDim) && (newSize > 0)) {
            blockSize[iDim] = newSize;
        }

    }

    /**
     * Read the header of an NMRView format dataset file into the fields of this
     * Dataset object.
     *
     * @return 0 if successful, 1 if there was an error
     */
    public final synchronized int readHeader() {
        int i;
        byte[] buffer;
        buffer = new byte[NV_HEADER_SIZE];

        boolean checkSwap;
        DataUtilities.readBytes(raFile, buffer, 0, NV_HEADER_SIZE);

        ByteArrayInputStream bis = new ByteArrayInputStream(buffer);
        DataInputStream dis;

        try {
            dis = new DataInputStream(bis);
            checkSwap = false;
            magic = DataUtilities.readSwapInt(dis, checkSwap);

            if (magic != 874032077) {
                bis = new ByteArrayInputStream(buffer);
                dis = new DataInputStream(bis);
                checkSwap = true;

                magic = DataUtilities.readSwapInt(dis, checkSwap);

                if (magic != 874032077) {
                    System.err.println("couldn't read header");

                    return 1;
                }
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());

            return 1;
        }

        littleEndian = checkSwap;
        gotByteOrder = true;

        try {
            //read spare1
            DataUtilities.readSwapInt(dis, checkSwap);
            // read spare2
            DataUtilities.readSwapInt(dis, checkSwap);
            fileHeaderSize = DataUtilities.readSwapInt(dis, checkSwap);
            blockHeaderSize = DataUtilities.readSwapInt(dis, checkSwap);
            blockElements = DataUtilities.readSwapInt(dis, checkSwap) * 4;
            nDim = DataUtilities.readSwapInt(dis, checkSwap);
            setNDim();
            rdims = DataUtilities.readSwapInt(dis, checkSwap);
            if (rdims == 0) {
                rdims = nDim;
            }
            temperature = DataUtilities.readSwapFloat(dis, checkSwap);
            byte[] solventBytes = new byte[SOLVENT_MAX_BYTES];
            dis.read(solventBytes);

            StringBuilder solventBuffer = new StringBuilder();

            for (int j = 0; (j < SOLVENT_MAX_BYTES) && (solventBytes[j] != '\0'); j++) {
                solventBuffer.append((char) solventBytes[j]);
            }

            solvent = solventBuffer.toString();

            dis.skip(992 - 4 - SOLVENT_MAX_BYTES);

            byte[] labelBytes = new byte[LABEL_MAX_BYTES];
            StringBuilder labelBuffer = new StringBuilder();
            offsetPoints[0] = 1;

            offsetBlocks[0] = 1;

            for (i = 0; i < nDim; i++) {
                setSize(i, DataUtilities.readSwapInt(dis, checkSwap));
                fileDimSizes[i] = size[i];
                blockSize[i] = DataUtilities.readSwapInt(dis, checkSwap);
                nBlocks[i] = DataUtilities.readSwapInt(dis, checkSwap);
                nBlocks[i] = getSize(i) / blockSize[i];

                if ((blockSize[i] * nBlocks[i]) < getSize(i)) {
                    nBlocks[i] += 1;
                }

                if (i > 0) {
                    offsetPoints[i] = offsetPoints[i - 1] * blockSize[i - 1];
                    offsetBlocks[i] = offsetBlocks[i - 1] * nBlocks[i - 1];
                }

                // read offblk
                DataUtilities.readSwapInt(dis, checkSwap);
                // read blkmask
                DataUtilities.readSwapInt(dis, checkSwap);
                // read offpt
                DataUtilities.readSwapInt(dis, checkSwap);
                setSf(i, DataUtilities.readSwapFloat(dis, checkSwap));
                setSw(i, DataUtilities.readSwapFloat(dis, checkSwap));
                setSw_r(i, getSw(i));
                refPt[i] = DataUtilities.readSwapFloat(dis, checkSwap);
                refPt_r[i] = refPt[i];
                setRefValue(i, DataUtilities.readSwapFloat(dis, checkSwap));
                setRefValue_r(i, getRefValue(i));
                setRefUnits(i, DataUtilities.readSwapInt(dis, checkSwap));
                foldUp[i] = DataUtilities.readSwapFloat(dis, checkSwap);
                foldDown[i] = DataUtilities.readSwapFloat(dis, checkSwap);
                dis.read(labelBytes);

                int j;
                labelBuffer.setLength(0);

                for (j = 0; (j < LABEL_MAX_BYTES) && (labelBytes[j] != '\0'); j++) {
                    labelBuffer.append((char) labelBytes[j]);
                }

                label[i] = labelBuffer.toString();
                nucleus[i] = null;

                setComplex(i, DataUtilities.readSwapInt(dis, checkSwap) == 1);
                setComplex_r(i, getComplex(i));
                setFreqDomain(i, DataUtilities.readSwapInt(dis, checkSwap) == 1);
                setFreqDomain_r(i, getFreqDomain(i));
                setPh0(i, DataUtilities.readSwapFloat(dis, checkSwap));
                setPh0_r(i, getPh0(i));
                setPh1(i, DataUtilities.readSwapFloat(dis, checkSwap));
                setPh1_r(i, getPh1(i));
                setVSize(i, DataUtilities.readSwapInt(dis, checkSwap));
                setVSize_r(i, getVSize(i));
                setTDSize(i, DataUtilities.readSwapInt(dis, checkSwap));
                setZFSize(i, DataUtilities.readSwapInt(dis, checkSwap));
                setExtFirst(i, DataUtilities.readSwapInt(dis, checkSwap));
                setExtLast(i, DataUtilities.readSwapInt(dis, checkSwap));
                dis.skip(6 * 4);
            }

            rmsd = new double[nDim][];
            values = new double[nDim][];
        } catch (IOException e) {
            System.err.println(e.getMessage());

            return 1;
        }
        setStrides();

        return 0;
    }

    /**
     * Read the header of an UCSF format dataset file into the fields of this
     * Dataset object.
     *
     * @return 0 if successful, 1 if there was an error
     */
    public final synchronized int readHeaderUCSF() {
        byte[] buffer;
        buffer = new byte[UCSF_HEADER_SIZE];
        fFormat = FFORMAT.UCSF;

        DataUtilities.readBytes(raFile, buffer, 0, UCSF_HEADER_SIZE);

        ByteArrayInputStream bis = new ByteArrayInputStream(buffer);
        DataInputStream dis;

        try {
            byte[] magicBytes = new byte[10];
            dis = new DataInputStream(bis);
            dis.read(magicBytes);

            for (int j = 0; j < 8; j++) {
                if (magicBytes[j] != (byte) "UCSF NMR".charAt(j)) {
                    return 1;
                }
            }

        } catch (IOException e) {
            System.err.println(e.getMessage());

            return 1;
        }

        try {
            nDim = dis.readByte();
            int nDataComp = dis.readByte();
            dis.readByte();
            int version = dis.readByte();
            System.out.println(nDim + " " + nDataComp + " " + version);
            if ((nDim < 1) || (nDim > 4)) {
                return 1;
            }
            setNDim();

            dis.skip(UCSF_HEADER_SIZE - 14);

            byte[] labelBytes = new byte[8];
            StringBuilder labelBuffer = new StringBuilder();
            offsetPoints[0] = 1;

            offsetBlocks[0] = 1;
            boolean checkSwap = false;

            for (int iDim = 0; iDim < nDim; iDim++) {
                int i = nDim - iDim - 1;
                buffer = new byte[128];

                DataUtilities.readBytes(raFile, buffer, UCSF_HEADER_SIZE + i * 128, 128);
                bis = new ByteArrayInputStream(buffer);
                dis = new DataInputStream(bis);
                labelBuffer.setLength(0);
                dis.read(labelBytes);

                for (int j = 0; (j < 8) && (labelBytes[j] != '\0'); j++) {
                    labelBuffer.append((char) labelBytes[j]);
                }

                label[iDim] = labelBuffer.toString();

                setSize(iDim, DataUtilities.readSwapInt(dis, checkSwap));
                fileDimSizes[iDim] = size[iDim];
                // skip empty entry
                DataUtilities.readSwapInt(dis, checkSwap);
                blockSize[iDim] = DataUtilities.readSwapInt(dis, checkSwap);
                nBlocks[iDim] = getSize(iDim) / blockSize[iDim];

                if ((blockSize[iDim] * nBlocks[iDim]) < getSize(iDim)) {
                    nBlocks[iDim] += 1;
                }
                System.out.println(size[iDim] + " " + blockSize[iDim] + " " + nBlocks[iDim]);

                if (iDim > 0) {
                    offsetPoints[iDim] = offsetPoints[iDim - 1] * blockSize[iDim - 1];
                    offsetBlocks[iDim] = offsetBlocks[iDim - 1] * nBlocks[iDim - 1];
                }

                setSf(iDim, DataUtilities.readSwapFloat(dis, checkSwap));
                setSw(iDim, DataUtilities.readSwapFloat(dis, checkSwap));
                setSw_r(iDim, getSw(iDim));
                refPt[iDim] = size[iDim] / 2 + 1;
                refPt_r[iDim] = refPt[iDim];
                setRefValue(iDim, DataUtilities.readSwapFloat(dis, checkSwap));
                setRefValue_r(iDim, getRefValue(iDim));

                if (version == 87) {
                    setComplex(i, DataUtilities.readSwapInt(dis, checkSwap) == 1);
                    setComplex_r(i, getComplex(i));
                    setFreqDomain(i, DataUtilities.readSwapInt(dis, checkSwap) == 1);
                    setFreqDomain_r(i, getFreqDomain(i));
                    setPh0(i, DataUtilities.readSwapFloat(dis, checkSwap));
                    setPh0_r(i, getPh0(i));
                    setPh1(i, DataUtilities.readSwapFloat(dis, checkSwap));
                    setPh1_r(i, getPh1(i));
                    setVSize(i, DataUtilities.readSwapInt(dis, checkSwap));
                    setVSize_r(i, getVSize(i));
                } else {
                    complex[iDim] = false;
                    setRefUnits(iDim, 3);
                    setVSize_r(iDim, getSize(iDim));
                    setVSize(iDim, getSize(iDim));
                    setFreqDomain(iDim, true);
                    setFreqDomain_r(iDim, true);
                }
            }
        } catch (IOException e) {
            //LOGGER.error("Can't read header ", e);
            return 1;
        }
        blockElements = 4;
        for (int iDim = 0; iDim < nDim; iDim++) {
            blockElements = blockElements * blockSize[iDim];
        }

        fileHeaderSize = UCSF_HEADER_SIZE + 128 * nDim;
        blockHeaderSize = 0;
        rmsd = new double[nDim][];
        values = new double[nDim][];
        dataType = 0;
        rdims = nDim;
        setStrides();

        return 0;
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
     * Initialize headers to default values based on currently set number of
     * dimensions
     */
    public final synchronized void newHeader() {
        magic = 0x3418abcd;
        fileHeaderSize = 0;
        blockHeaderSize = 0;
        blockSize = new int[nDim];
        offsetPoints = new int[nDim];

        offsetBlocks = new int[nDim];
        nBlocks = new int[nDim];

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
            refPt[i] = size[i] / 2;
            refPt_r[i] = size[i] / 2;
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
        magic = 0x3418abcd;
        fileHeaderSize = 0;
        blockHeaderSize = 0;
        blockSize = new int[nDim];
        offsetPoints = new int[nDim];

        offsetBlocks = new int[nDim];
        nBlocks = new int[nDim];

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

    void setStrides() {
        strides[0] = 1;
        for (int i = 1; i < nDim; i++) {
            strides[i] = strides[i - 1] * size[i - 1];
        }
    }

    final void dimDataset() {
        int iDim;

        blockElements = 4;

        for (iDim = 0; iDim < nDim; iDim++) {
            if ((getSize(iDim) == 0) || (blockSize[iDim] == 0)) {
                return;
            }
        }

        for (iDim = 0; iDim < nDim; iDim++) {
            nBlocks[iDim] = size(iDim) / blockSize[iDim];

            if ((blockSize[iDim] * nBlocks[iDim]) < size(
                    iDim)) {
                nBlocks[iDim] += 1;
            }

            if (iDim > 0) {
                offsetBlocks[iDim] = nBlocks[iDim - 1] * offsetBlocks[iDim
                        - 1];
                offsetPoints[iDim] = blockSize[iDim - 1] * offsetPoints[iDim
                        - 1];
            } else {
                offsetBlocks[iDim] = 1;
                offsetPoints[iDim] = 1;
            }

            blockElements = blockElements * blockSize[iDim];

            foldUp[iDim] = 0.0;
            foldDown[iDim] = 0.0;
        }
        rmsd = new double[nDim][];
        values = new double[nDim][];
        setStrides();
        setDimAttributes();
    }

    /**
     * Set the block sizes based on the specified size of a block.
     *
     * @param blockPoints the size of the block
     */
    public final synchronized void setBlockSize(int blockPoints) {
        long npoints;
        int blksize;
        int blkspdim;
        int blog;
        int[] tsize;
        int[] nbdim;
        int[] bsize;
        int i;
        int j;
        int nblks;
        int imin = 0;
        int imax = 0;
        int bmax;
        int bmin;
        npoints = 1;
        tsize = new int[nDim];
        bsize = new int[nDim];
        nbdim = new int[nDim];

        //Calculate total points in file
        for (i = 0; i < nDim; i++) {
            tsize[i] = getSize(i);
            blog = (int) ((Math.log((double) tsize[i]) / Math.log(2.0)) + 0.5);
            bsize[i] = (int) (Math.exp(blog * Math.log(2.0)) + 0.5);

            if (bsize[i] < tsize[i]) {
                bsize[i] *= 2;
            }

            npoints *= bsize[i];
        }

        /*
         * Use blockPoints as number of points/block.
         */
        nblks = (int) (npoints / blockPoints);
        if ((nblks * blockPoints) < npoints) {
            nblks++;
        }

        /*
         * printf("nblks %d\n",nblks);
         */
 /*
         * With small files block size is dim size
         */
        if (nblks < 2) {
            for (i = 0; i < nDim; i++) {
                blockSize[i] = getSize(i);
            }

            return;
        }

        /*
         * Calculate trial blocks per dim based on total number of blocks and
         * the number of dimensions.
         */
        blkspdim = (int) (Math.exp((1.0 / nDim) * Math.log(
                (double) nblks)));
        blog = (int) ((Math.log((double) blkspdim) / Math.log(2.0)) + 0.5);
        blkspdim = (int) (Math.exp(blog * Math.log(2.0)) + 0.5);

        for (i = 0; i < nDim; i++) {
            nbdim[i] = blkspdim;

            if (nbdim[i] > bsize[i]) {
                nbdim[i] = bsize[i];
            }
        }

        /*
         * Adjust trial block sizes for each dimension so that the number of
         * points per block is blockPoints. If too big, divide the dimension
         * with largest block size in half. If too small, double the size of
         * dimension with largest block size.
         */
        for (j = 0; j < 2; j++) {
            blksize = 1;
            bmax = 0;
            bmin = 100000;

            for (i = 0; i < nDim; i++) {
                if ((bsize[i] / nbdim[i]) > 0) {
                    blksize *= (bsize[i] / nbdim[i]);
                }

                if (bsize[i] == 1) {
                    continue;
                }

                if (nbdim[i] > bmax) {
                    bmax = nbdim[i];
                    imax = i;
                }

                if (nbdim[i] < bmin) {
                    bmin = nbdim[i];
                    imin = i;
                }
            }

            /*
             * ConsoleWrite( "%d\n", blksize);
             */
            if (blksize > blockPoints) {
                nbdim[imax] = nbdim[imax] * 2;
            }

            if (blksize < blockPoints) {
                nbdim[imax] = nbdim[imax] / 2;
            }

            if (nbdim[imin] < 2) {
                nbdim[imin] = 2;
                nbdim[imax] = nbdim[imax] / 2;
            }
        }

        for (i = 0; i < nDim; i++) {
            blockSize[i] = bsize[i] / nbdim[i];
        }
    }

    /**
     * Flush the header values out to the dataset file.
     */
    public final synchronized void writeHeader() {
        if (file.getPath().contains(".ucsf")) {
            writeHeaderUCSF(raFile, true);
        } else {
            writeHeader(raFile);
        }
    }

    /**
     * Flush the header values out to the specified file.
     *
     * @param outFile The file to write to.
     */
    synchronized private void writeHeader(RandomAccessFile outFile) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(NV_HEADER_SIZE);
        if (littleEndian) {
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        byte[] labelBytes = new byte[LABEL_MAX_BYTES];
        byte[] solventBytes = new byte[SOLVENT_MAX_BYTES];
        try {
            magic = 0x3418abcd;
            byteBuffer.putInt(magic);
            byteBuffer.putInt(0);
            byteBuffer.putInt(0);
            byteBuffer.putInt(fileHeaderSize);
            byteBuffer.putInt(blockHeaderSize);
            byteBuffer.putInt(blockElements / 4);
            byteBuffer.putInt(nDim);
            byteBuffer.putInt(rdims);
            byteBuffer.putFloat((float) temperature);
            String solventString = getSolvent();
            int nBytes = solventString.length();
            for (int j = 0; j < SOLVENT_MAX_BYTES; j++) {
                if (j < nBytes) {
                    solventBytes[j] = (byte) solventString.charAt(j);
                } else {
                    solventBytes[j] = 0;
                }
            }
            byteBuffer.put(solventBytes);

            for (int i = 0; i < nDim; i++) {
                byteBuffer.position(1024 + i * 128);
                byteBuffer.putInt(getSize(i));
                byteBuffer.putInt(blockSize[i]);
                byteBuffer.putInt(nBlocks[i]);
                byteBuffer.putInt(0);
                byteBuffer.putInt(0);
                byteBuffer.putInt(0);
                byteBuffer.putFloat((float) getSf(i));
                byteBuffer.putFloat((float) getSw(i));
                byteBuffer.putFloat((float) getRefPt(i));
                byteBuffer.putFloat((float) getRefValue(i));
                byteBuffer.putInt(getRefUnits(i));
                byteBuffer.putFloat((float) foldUp[i]);
                byteBuffer.putFloat((float) foldDown[i]);

                String labelString = label[i];
                nBytes = labelString.length();
                for (int j = 0; j < LABEL_MAX_BYTES; j++) {
                    if (j < nBytes) {
                        labelBytes[j] = (byte) labelString.charAt(j);
                    } else {
                        labelBytes[j] = 0;
                    }
                }

                byteBuffer.put(labelBytes);

                if (getComplex(i)) {
                    byteBuffer.putInt(1);
                } else {
                    byteBuffer.putInt(0);
                }

                if (getFreqDomain(i)) {
                    byteBuffer.putInt(1);
                } else {
                    byteBuffer.putInt(0);
                }
                byteBuffer.putFloat((float) getPh0(i));
                byteBuffer.putFloat((float) getPh1(i));
                byteBuffer.putInt(getVSize(i));
                byteBuffer.putInt(getTDSize(i));
                byteBuffer.putInt(getZFSize(i));
                byteBuffer.putInt(getExtFirst(i));
                byteBuffer.putInt(getExtLast(i));
            }

            if (outFile != null) {
                DataUtilities.writeBytes(outFile, byteBuffer.array(), 0, NV_HEADER_SIZE);
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     * Get the header parameters as a string of text.
     *
     * @return the header as a string
     */
    synchronized public String getHeader() {
        StringBuilder sBuilder = new StringBuilder();
        String sepChar = " ";
        sBuilder.append(littleEndian);
        sBuilder.append(sepChar);

        byte[] labelBytes = new byte[LABEL_MAX_BYTES];
        magic = 0x3418abcd;
        sBuilder.append("magic");
        sBuilder.append(sepChar);
        sBuilder.append(magic);
        sBuilder.append(sepChar);
        sBuilder.append("skip");
        sBuilder.append(sepChar);
        sBuilder.append(0);
        sBuilder.append(sepChar);
        sBuilder.append("skip");
        sBuilder.append(sepChar);
        sBuilder.append(0);
        sBuilder.append(sepChar);
        sBuilder.append("fileHeaderSize");
        sBuilder.append(sepChar);
        sBuilder.append(fileHeaderSize);
        sBuilder.append(sepChar);
        sBuilder.append("blockHeaderSize");
        sBuilder.append(sepChar);
        sBuilder.append(blockHeaderSize);
        sBuilder.append(sepChar);
        sBuilder.append("blockElements");
        sBuilder.append(sepChar);
        sBuilder.append(blockElements / 4);
        sBuilder.append(sepChar);
        sBuilder.append("nDim");
        sBuilder.append(sepChar);
        sBuilder.append(nDim);
        sBuilder.append("\n");

        for (int i = 0; i < nDim; i++) {
            sBuilder.append("dim");
            sBuilder.append(sepChar);
            sBuilder.append(i);
            sBuilder.append(sepChar);
            sBuilder.append("offset");
            sBuilder.append(sepChar);
            sBuilder.append(1024 + i * 128);
            sBuilder.append(sepChar);
            sBuilder.append("size");
            sBuilder.append(sepChar);
            sBuilder.append(getSize(i));
            sBuilder.append(sepChar);
            sBuilder.append("blocksize");
            sBuilder.append(sepChar);
            sBuilder.append(blockSize[i]);
            sBuilder.append(sepChar);

            sBuilder.append("offpoints");
            sBuilder.append(sepChar);
            sBuilder.append(offsetPoints[i]);
            sBuilder.append(sepChar);

            sBuilder.append("offblocks");
            sBuilder.append(sepChar);
            sBuilder.append(offsetBlocks[i]);
            sBuilder.append(sepChar);

            sBuilder.append("nBlocks");
            sBuilder.append(sepChar);
            sBuilder.append(nBlocks[i]);
            sBuilder.append(sepChar);
            sBuilder.append("skip");
            sBuilder.append(sepChar);
            sBuilder.append(0);
            sBuilder.append(sepChar);
            sBuilder.append("skip");
            sBuilder.append(sepChar);
            sBuilder.append(0);
            sBuilder.append(sepChar);
            sBuilder.append("skip");
            sBuilder.append(sepChar);
            sBuilder.append(0);
            sBuilder.append(sepChar);
            sBuilder.append("sf");
            sBuilder.append(sepChar);
            sBuilder.append((float) getSf(i));
            sBuilder.append(sepChar);
            sBuilder.append("sw");
            sBuilder.append(sepChar);
            sBuilder.append((float) getSw(i));
            sBuilder.append(sepChar);
            sBuilder.append("refpt");
            sBuilder.append(sepChar);
            sBuilder.append((float) getRefPt(i));
            sBuilder.append(sepChar);
            sBuilder.append("refval");
            sBuilder.append(sepChar);
            sBuilder.append((float) getRefValue(i));
            sBuilder.append(sepChar);
            sBuilder.append("refunits");
            sBuilder.append(sepChar);
            sBuilder.append(getRefUnits(i));
            sBuilder.append(sepChar);
            sBuilder.append("foldup");
            sBuilder.append(sepChar);
            sBuilder.append((float) foldUp[i]);
            sBuilder.append(sepChar);
            sBuilder.append("folddown");
            sBuilder.append(sepChar);
            sBuilder.append((float) foldDown[i]);
            sBuilder.append(sepChar);
            sBuilder.append("label");
            sBuilder.append(sepChar);

            String labelString = label[i];
            int nBytes = labelString.length();
            for (int j = 0; j < LABEL_MAX_BYTES; j++) {
                if (j < nBytes) {
                    labelBytes[j] = (byte) labelString.charAt(j);
                } else {
                    labelBytes[j] = 0;
                }
            }

            sBuilder.append(labelBytes);
            sBuilder.append(sepChar);
            sBuilder.append("complex");
            sBuilder.append(sepChar);

            if (getComplex(i)) {
                sBuilder.append(1);
            } else {
                sBuilder.append(0);
            }
            sBuilder.append(sepChar);
            sBuilder.append("freqdomain");
            sBuilder.append(sepChar);

            if (getFreqDomain(i)) {
                sBuilder.append(1);
            } else {
                sBuilder.append(0);
            }
            sBuilder.append(sepChar);
            sBuilder.append("ph0");
            sBuilder.append(sepChar);
            sBuilder.append((float) getPh0(i));
            sBuilder.append(sepChar);
            sBuilder.append("ph1");
            sBuilder.append(sepChar);
            sBuilder.append((float) getPh1(i));
            sBuilder.append(sepChar);
            sBuilder.append("vsize");
            sBuilder.append(sepChar);
            sBuilder.append(getVSize(i));
            sBuilder.append("\n");
        }
        return sBuilder.toString();
    }

    /**
     * Write out the header of file in UCSF format
     *
     * @param nvExtra include extra information in header needed for processing
     */
    public final synchronized void writeHeaderUCSF(boolean nvExtra) {
        writeHeaderUCSF(raFile, nvExtra);
    }

//    The 180 byte header contains:
//
//position	bytes	contents	required value
//0	10	file type	= UCSF NMR (8 character null terminated string)
//10	1	dimension of spectrum
//11	1	number of data components	= 1 for real data
//13	1	format version number	= 2 for current format
//The first byte in the file is position 0. A complex spectrum has two components. Sparky only reads real data and I will only describe below the layout for real data so set the number of components to 1. Use format version number 2.
//
//For each axis of the spectrum write a 128 byte header of the following form:
//
//position	bytes	contents
//0	6	nucleus name (1H, 13C, 15N, 31P, ...) null terminated ASCII
//8	4	integer number of data points along this axis
//16	4	integer tile size along this axis
//20	4	float spectrometer frequency for this nucleus (MHz)
//24	4	float spectral width (Hz)
//28	4	float center of data (ppm)
    synchronized private void writeHeaderUCSF(RandomAccessFile outFile, boolean nvExtra) {
        int ucsfFileHeaderSize = UCSF_HEADER_SIZE + nDim * 128;
        ByteBuffer byteBuffer = ByteBuffer.allocate(ucsfFileHeaderSize);
        if (littleEndian) {
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        int version = nvExtra ? 87 : 2;

        try {
            byte[] magicBytes = new byte[10];
            for (int i = 0; i < 8; i++) {
                magicBytes[i] = (byte) "UCSF NMR".charAt(i);
            }

            byteBuffer.put(magicBytes);
            byteBuffer.put((byte) nDim);
            byteBuffer.put((byte) 1);  // real data
            byteBuffer.put((byte) 0);
            byteBuffer.put((byte) version);  // version number

            for (int i = 0; i < nDim; i++) {
                int iDim = nDim - i - 1;
                byteBuffer.position(UCSF_HEADER_SIZE + i * 128);
                String nucName = getNucleus(iDim).getNumberName();
                for (int j = 0; j < nucName.length(); j++) {
                    byteBuffer.put((byte) nucName.charAt(j));
                }
                for (int j = nucName.length(); j < 8; j++) {
                    byteBuffer.put((byte) 0);
                }
                byteBuffer.putInt(getSize(iDim));
                byteBuffer.putInt(0);
                byteBuffer.putInt(blockSize[iDim]);
                byteBuffer.putFloat((float) getSf(iDim));
                byteBuffer.putFloat((float) getSw(iDim));
                byteBuffer.putFloat((float) getRefValue(iDim));

                if (version == 87) {
                    if (getComplex(i)) {
                        byteBuffer.putInt(1);
                    } else {
                        byteBuffer.putInt(0);
                    }

                    if (getFreqDomain(i)) {
                        byteBuffer.putInt(1);
                    } else {
                        byteBuffer.putInt(0);
                    }
                    byteBuffer.putFloat((float) getPh0(i));
                    byteBuffer.putFloat((float) getPh1(i));
                    byteBuffer.putInt(getVSize(i));
                }
            }

            if (outFile != null) {
                DataUtilities.writeBytes(outFile, byteBuffer.array(), 0, ucsfFileHeaderSize);
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
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
            writeHeader(outFile);
            DataUtilities.writeBytes(outFile, buffer, fileHeaderSize, buffer.length);
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
        for (int i = 0; i < nDim - 1; i++) {
            mPoint[i] = pt[i][1] + 1;
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
            return ((Dataset) theFiles.get(fileName));
        }
    }

    /**
     * Return a list of the names of open datasets
     *
     * @return List of names.
     */
    synchronized public static List<String> names() {
        List<String> names = theFiles.keySet().stream().sorted().collect(Collectors.toList());
        return names;
    }

    /**
     * Return a list of the open datasets
     *
     * @return List of datasets.
     */
    synchronized public static List<Dataset> datasets() {
        List<Dataset> datasets = theFiles.values().stream().collect(Collectors.toList());
        return datasets;
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

        for (Dataset dataset : theFiles.values()) {
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
        dataFile.sum();
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
            dataFile.position(points);
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
        createDataset(fullName, datasetName, dimSizes);
        Dataset dataset = new Dataset(fullName, datasetName, true);
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
     * Read data values into a Vec object from specified location in dataset
     *
     * @param pt indices of values to read
     * @param dim Dataset dimensions used by indices
     * @param rwVector the Vec object in which to put values
     * @throws IOException if an I/O error occurs
     */
    public void readVecFromDatasetFile(int[][] pt, int[] dim, Vec rwVector) throws IOException {
        //System.out.println("reading vector from dataset file");
        int n = 0;

        if (vecMat != null) {
            throw new IllegalArgumentException("Don't call this method on a vector type dataset");
        }
        rwVector.resize(rwVector.getSize(), getComplex_r(dim[0]));
        rwVector.centerFreq = getSf(dim[0]);
        rwVector.dwellTime = 1.0 / getSw_r(dim[0]);
        rwVector.setPh0(getPh0_r(dim[0]));
        rwVector.setPh1(getPh1_r(dim[0]));
        rwVector.setTDSize(getTDSize(dim[0]));

        double delRef = ((getRefPt_r(dim[0]) - pt[0][0]) * getSw_r(dim[0])) / getSf(dim[0]) / size[dim[0]];
        rwVector.refValue = getRefValue_r(dim[0]) + delRef;
        rwVector.setFreqDomain(getFreqDomain_r(dim[0]));

        //System.err.println("read sw "+dim[0]+" "+(1.0/rwVector.dwellTime)+" " +getRefValue_r(dim[0]) + " "  +(rwVector.refValue-delRef)+ " " + rwVector.refValue+" "+delRef+" "+refPt_r[dim[0]]+" "+rwVector.isComplex()+" " + size[dim[0]]+ " " + pt[0][0] + " " + (getSw_r(dim[0])/getSf(dim[0])));
        int[] point = new int[nDim];
        for (int i = 1; i < nDim; i++) {
            point[dim[i]] = pt[i][0];
        }
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
        //System.out.println("done reading vector from dataset file");
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

        int j = 0;
        for (int i = pt[0][0]; i <= pt[0][1]; i++) {
            point[dim[0]] = i;
            if (vector.isComplex()) {
                if ((i % 2) != 0) {
                    dataFile.setFloat((float) (vector.getImag(j) * scale), point);
                    j++;
                } else {
                    dataFile.setFloat((float) (vector.getReal(j) * scale), point);
                }
            } else {
                dataFile.setFloat((float) (vector.getReal(j) * scale), point);
                j++;
            }

        }

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
     * Get the number of blocks along each dimension.
     *
     * @return array containing the number of blocks
     */
    public int[] getnBlocks() {
        return nBlocks;
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

    /**
     * Copy dataset to a new file
     *
     * @param newFileName File name of new dataset.
     * @throws IOException if an I/O error occurs
     * @throws DatasetException if an I/O error occured while creating dataset
     */
    public void copyDataset(String newFileName) throws IOException, DatasetException {
        int iDim = 0;
        int[][] pt = new int[nDim][2];
        int[] dim = new int[nDim];
        dim[0] = iDim;
        pt[0][0] = 0;
        pt[0][1] = 0;

        int faceSize = 1;
        int[] datasetSizes = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            dim[i] = i;
            pt[i][0] = 0;
            pt[i][1] = getSize(i) - 1;
            faceSize *= getSize(i);
            datasetSizes[i] = getSize(i);
        }
        int newSize = pt[0][1] - pt[0][0] + 1;

        Dataset.createDataset(newFileName, newFileName, datasetSizes);
        Dataset newDataset = new Dataset(newFileName, newFileName, true);

        Vec scanVec = new Vec(newSize, false);
        ScanRegion scanRegion = new ScanRegion(pt, dim, this);
        int nEntries = scanRegion.buildIndex();
        int winSize = getSize(iDim) / 32;
        int nWin = 4;
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
        }
        newDataset.setSolvent(getSolvent());
        newDataset.setTitle(getTitle());

        newDataset.writeHeader();
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

    public void setRegions(Set<DatasetRegion> regions) {
        this.regions = regions;
    }

    public Set<DatasetRegion> getRegions() {
        return regions;
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
        for (int sz : size) {
            bufferSize *= sz;
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
}
