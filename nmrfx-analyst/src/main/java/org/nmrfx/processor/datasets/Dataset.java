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

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.descriptive.rank.PSquarePercentile;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.datasets.*;
import org.nmrfx.math.VecBase;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.datasets.vendor.bruker.BrukerData;
import org.nmrfx.processor.datasets.vendor.jcamp.JCAMPData;
import org.nmrfx.processor.datasets.vendor.rs2d.RS2DData;
import org.nmrfx.processor.math.Matrix;
import org.nmrfx.processor.math.MatrixND;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.operations.IDBaseline2;
import org.nmrfx.processor.operations.Util;
import org.nmrfx.processor.processing.LineShapeCatalog;
import org.nmrfx.processor.processing.ProcessingException;
import org.nmrfx.project.ProjectBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Instances of this class represent NMR datasets. The class is typically used
 * for processed NMR spectra (rather than FID data). The actual data values are
 * either stored in random access file, a memory based representation of a file,
 * or an NMRView Vec object.
 *
 * @author brucejohnson
 */
@PythonAPI({"dscript", "nustest", "simfid"})
@PluginAPI("parametric")
public class Dataset extends DatasetBase implements Comparable<Dataset> {

    private static final Logger log = LoggerFactory.getLogger(Dataset.class);
    private static final long BIG_MAP_LIMIT = Integer.MAX_VALUE / 2;
    private static boolean useCacheFile = false;

    private boolean dirty = false;  // flag set if a vector has been written to dataset, should purge bufferVectors
    LineShapeCatalog simVecs = null;
    Map<String, double[]> buffers = new HashMap<>();
    Dataset[] projections = null;
    private Object analyzerObject = null;
    boolean memoryMode = false;
    String script = "";

    /**
     * Create a new Dataset object that refers to an existing random access file
     * in a format that can be described by this class.
     *
     * @param fullName     The full path to the file
     * @param name         The short name (and initial title) to be used for the
     *                     dataset.
     * @param writable     true if the file should be opened in a writable mode.
     * @param useCacheFile true if the file will use StorageCache rather than
     *                     memory mapping file. You should not use StorageCache if the file is to be
     *                     opened for drawing in NMRFx (as thread interrupts may cause it to be
     *                     closed)
     * @throws IOException if an I/O error occurs
     */
    public Dataset(String fullName, String name, boolean writable, boolean useCacheFile, boolean saveToProject)
            throws IOException {
        // fixme  FileUtil class needs to be public file = FileUtil.getFileObj(interp,fullName);
        super(fullName, name, writable, useCacheFile);

        RandomAccessFile raFile;
        if (file.canWrite()) {
            raFile = new RandomAccessFile(file, "rw");
        } else {
            raFile = new RandomAccessFile(file, "r");
        }

        title = fileName;

        scale = 1.0;
        lvl = 0.1;
        posDrawOn = true;
        negDrawOn = true;
        DatasetHeaderIO headerIO = new DatasetHeaderIO(this);
        if (fullName.contains(".ucsf")) {
            layout = headerIO.readHeaderUCSF(raFile);
        } else {
            layout = headerIO.readHeader(raFile);
        }
        memoryMode = false;
        //Only automatically set the freqDomains to true if they are all false
        boolean noFreqDomains = IntStream.range(0, freqDomain.length).allMatch(i -> !freqDomain[i]);
        // Datasets that were not generated in NMRFx don't
        // have freqDomain  attribute set so set freq domain
        // here
        boolean isFID = (getNFreqDims() == 0) && (!getFreqDomain(0) && (getComplex(0)));
        if (!isFID && noFreqDomains) {
            int nFreq = getNFreqDims() == 0 ? nDim : getNFreqDims();
            for (int i = 0; i < nFreq; i++) {
                setFreqDomain(i, true);
            }
        }

        DatasetParameterFile parFile = new DatasetParameterFile(this, layout);
        parFile.readFile();
        if (layout != null) {
            createDataFile(raFile, writable);
        }

        if (saveToProject) {
            addFile(fileName);
        }
        loadLSCatalog();
    }

    /**
     * Create a new Dataset object that refers to an existing random access file
     * in a format that can be described by this class.
     *
     * @param fullName      The full path to the file
     * @param name          The short name (and initial title) to be used for the
     *                      dataset.
     * @param datasetLayout contains information about layout (sizes, blocksizes
     *                      etc. of dataset to be created)
     * @param useCacheFile  true if the file will use StorageCache rather than
     *                      memory mapping file. You should not use StorageCache if the file is to be
     *                      opened for drawing in NMRFx (as thread interrupts may cause it to be
     *                      closed)
     * @param byteOrder     Spcify little or big endian
     * @param dataType      0 is float, 1 is integer
     * @throws IOException if an I/O error occurs
     */
    public Dataset(String fullName, String name, DatasetLayout datasetLayout,
                   boolean useCacheFile, ByteOrder byteOrder, int dataType)
            throws IOException {
        // fixme  FileUtil class needs to be public file = FileUtil.getFileObj(interp,fullName);
        super(fullName, name, false, useCacheFile);
        boolean writable = false;
        RandomAccessFile raFile;
        this.layout = datasetLayout;

        title = fileName;

        scale = 1.0;
        lvl = 0.1;
        posDrawOn = true;
        negDrawOn = true;
        memoryMode = false;
        setDataType(dataType);
        setByteOrder(byteOrder);
        DatasetParameterFile parFile = new DatasetParameterFile(this, layout);
        parFile.readFile();

        if (layout != null) {
            raFile = new RandomAccessFile(file, "r");
            setNDim(layout.nDim);
            createDataFile(raFile, writable);
        }
        addFile(fileName);
    }

    /**
     * Create a new Dataset object that uses data in the specified Vec object
     *
     * @param vector the vector containing data
     */
    public Dataset(VecBase vector) {

        this.vecMat = vector;
        fileName = vector.getName();
        canonicalName = vector.getName();
        dataFile = vector;
        title = fileName;
        nDim = 1;
        vsize = new int[1];
        vsize[0] = vector.getSize();
        vsize_r = new int[1];
        tdSize = new int[1];
        zfSize = new int[1];
        extFirst = new int[1];
        extLast = new int[1];
        tdSize[0] = vector.getTDSize();
        fileSize = vector.getSize();
        layout = DatasetLayout.createFullMatrix(getFileHeaderSize(title), vsize);
        newHeader();
        memoryMode = false;

        foldUp[0] = 0.0;
        foldDown[0] = 0.0;
        scale = 1.0;
        lvl = 0.1;
        posDrawOn = true;
        negDrawOn = true;
        sf[0] = vector.centerFreq;
        sw[0] = 1.0 / vector.dwellTime;
        sw_r[0] = sw[0];
        refValue[0] = vector.getRefValue();
        refValue_r[0] = refValue[0];
        ph0[0] = vector.getPH0();
        ph1[0] = vector.getPH1();
        ph0_r[0] = ph0[0];
        ph1_r[0] = ph1[0];
        refPt[0] = vector.getSize() / 2.0;
        refPt_r[0] = vector.getSize() / 2.0;
        complex[0] = vector.isComplex();
        complex_r[0] = vector.isComplex();
        freqDomain[0] = vector.freqDomain();
        freqDomain_r[0] = vector.freqDomain();
        refUnits[0] = 3;
        rmsd = new double[1][1];
        values = new double[1][];
        addFile(fileName);
    }

    private Dataset(String fullName, String fileName, String title,
                    int[] dimSizes, boolean closeDataset, boolean createFile) throws DatasetException {
        try {
            file = new File(fullName);

            canonicalName = file.getCanonicalPath();
            this.fileName = fileName;

            this.nDim = dimSizes.length;

            int i;
            this.vsize = new int[this.nDim];
            this.vsize_r = new int[this.nDim];
            this.tdSize = new int[this.nDim];
            this.zfSize = new int[this.nDim];
            this.extFirst = new int[this.nDim];
            this.extLast = new int[this.nDim];
            rmsd = new double[nDim][];
            values = new double[nDim][];
            fileSize = 1;
            memoryMode = false;

            for (i = 0; i < this.nDim; i++) {
                fileSize *= dimSizes[i];
            }
            if (createFile) {
                layout = new DatasetLayout(dimSizes);
                layout.setBlockSize((DatasetLayout.calculateBlockSize(dimSizes)));
                layout.dimDataset();
            }
            this.title = title;
            newHeader();
            int fileHeaderSize;
            if (fullName.contains(".ucsf")) {
                fileHeaderSize = UCSF_HEADER_SIZE + 128 * nDim;
            } else {
                fileHeaderSize = NV_HEADER_SIZE;
            }
            if (layout != null) {
                setLayout(layout, closeDataset);
            }
        } catch (IOException ioe) {
            throw new DatasetException("Can't create dataset " + ioe.getMessage());
        }
    }

    public void setFile(File file) throws IOException {
        ProjectBase.getActive().removeDataset(fileName, this);
        this.file = file;
        canonicalName = file.getCanonicalPath();
        fileName = file.getName().replace(' ', '_');
        ProjectBase.getActive().addDataset(this, fileName);
    }

    // create in memory file

    /**
     * Create a dataset in memory for fast access. The dataset is not
     * written to disk so can't be persisted.
     *
     * @param title    Dataset title
     * @param file     The file associated with the dataset, if null, the title will be used as the fileName
     * @param dimSizes Sizes of the dataset dimensions
     * @param addFile  Whether to add the dataset to the active projects
     * @throws DatasetException if an I/O error occurs
     */
    public Dataset(String title, File file, int[] dimSizes, boolean addFile) throws DatasetException {
        try {
            this.nDim = dimSizes.length;

            setNDim(nDim);

            layout = DatasetLayout.createFullMatrix(0, dimSizes);
            this.title = title;
            if (file != null) {
                setFile(file);
            } else {
                this.fileName = title;
            }
            newHeader();
            dataFile = new MemoryFile(this, layout, true);
            dataFile.zero();
            memoryMode = true;
        } catch (IOException ioe) {
            throw new DatasetException("Can't create dataset " + ioe.getMessage());
        }
        if (addFile) {
            addFile(this.fileName);
        }
    }

    public int length() {
        int length = 1;
        for (int i = 0; i < nDim; i++) {
            length *= layout.getSize(i);
        }
        return length;
    }

    @Override
    public int compareTo(Dataset o) {
        return getName().compareTo(o.getName());
    }

    // create in memory file

    /**
     * Create a dataset in memory for fast access. This is an experimental mode,
     * and the dataset is not currently written to disk so can't be persisted.
     *
     * @param fullName Dataset file path
     * @param nDim     Number of dataset dimensions
     * @throws DatasetException if an I/O error occurs
     */
    public Dataset(String fullName, int nDim) throws DatasetException {
        this.nDim = nDim;
        file = new File(fullName);

        int i;
        setNDim(nDim);

        layout = null;
        this.fileName = file.getName();
        this.title = this.fileName;
        newHeader();
        dataFile = null;
        memoryMode = true;
    }

    public void setLayout(DatasetLayout layout, boolean closeDataset) throws IOException {
        int fileHeaderSize;
        String fullName = file.getCanonicalPath();
        if (fullName.contains(".ucsf")) {
            fileHeaderSize = UCSF_HEADER_SIZE + 128 * nDim;
        } else {
            fileHeaderSize = NV_HEADER_SIZE;
        }

        layout.setFileHeaderSize(fileHeaderSize);
        // Cannot close this here as it is used in places outside this try
        RandomAccessFile raFile = new RandomAccessFile(file, "rw");
        createDataFile(raFile, true);
        if (useCacheFile) {
            raFile.setLength(layout.getTotalSize());
        }
        writeHeader();
        if (closeDataset) {
            dataFile.zero();
            dataFile.close();
        } else {
            addFile(fileName);
        }
        DatasetParameterFile parFile = new DatasetParameterFile(this, layout);
        parFile.remove();

    }

    int getFileHeaderSize(String name) {
        int fileHeaderSize;

        if (name.contains(".ucsf")) {
            fileHeaderSize = UCSF_HEADER_SIZE + 128 * nDim;
        } else {
            fileHeaderSize = NV_HEADER_SIZE;
        }

        return fileHeaderSize;
    }

    public void resize(int directDimSize, int[] idSizes) throws DatasetException {
        int[] dimSizes = new int[nDim];
        dimSizes[0] = directDimSize;
        for (int i = 1; i < nDim; i++) {
            dimSizes[i] = idSizes[i - 1];
        }
        try {
            if (memoryMode) {
                layout = DatasetLayout.createFullMatrix(0, dimSizes);
                for (int i = 0; i < nDim; i++) {
                    refPt[i] = getSizeReal(i) / 2.0;
                    refPt_r[i] = getSizeReal(i) / 2.0;
                }
                dataFile = new MemoryFile(this, layout, true);
                dataFile.zero();
            } else {
                layout = DatasetLayout.createBlockMatrix(getFileHeaderSize(file.getName()), dimSizes);
                setLayout(layout, false);
            }
        } catch (IOException ioe) {
            throw new DatasetException("Can't create dataset " + ioe.getMessage());
        }
    }

    public void resizeDim(int iDim, int dimSize) throws DatasetException {
        int[] dimSizes = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            dimSizes[i] = getSizeTotal(i);
            if (i == iDim) {
                dimSizes[i] = dimSize;
            }
        }
        try {
            dataFile = StorageResizer.resizeDim(this, layout, dataFile, dimSizes);
            layout = dataFile.getLayout();
            refPt[iDim] = getSizeReal(iDim) / 2.0;
            refPt_r[iDim] = getSizeReal(iDim) / 2.0;
            memoryMode = dataFile instanceof MemoryFile;
        } catch (IOException ioe) {
            log.error(ioe.getMessage(), ioe);
            throw new DatasetException("Can't resize dataset " + ioe.getMessage());
        }
    }

    public void resizeDims(int[] dimSizes) throws DatasetException {
        try {
            dataFile = StorageResizer.resizeDim(this, layout, dataFile, dimSizes);
            layout = dataFile.getLayout();
            for (int iDim = 0; iDim < dimSizes.length; iDim++) {
                refPt[iDim] = getSizeReal(iDim) / 2.0;
                refPt_r[iDim] = getSizeReal(iDim) / 2.0;
            }
            memoryMode = dataFile instanceof MemoryFile;
        } catch (IOException ioe) {
            log.error(ioe.getMessage(), ioe);
            throw new DatasetException("Can't resize dataset " + ioe.getMessage());
        }
    }

    private void createDataFile(RandomAccessFile raFile, boolean writable) throws IOException {
        dataFile = createDataFile(this, raFile, file, layout, writable);
    }

    public static DatasetStorageInterface createDataFile(Dataset dataset, RandomAccessFile raFile, File file, DatasetLayout newLayout, boolean writable) throws IOException {
        DatasetStorageInterface newDataFile;
        if (useCacheFile) {
            newDataFile = new SubMatrixFile(dataset, file, newLayout, raFile, writable);
        } else {
            if (newLayout.getNDataBytes() > BIG_MAP_LIMIT) {
                newDataFile = new BigMappedMatrixFile(dataset, file, newLayout, raFile, writable);
            } else {
                if (newLayout.isSubMatrix()) {
                    newDataFile = new MappedSubMatrixFile(dataset, file, newLayout, raFile, writable);
                } else {
                    newDataFile = new MappedMatrixFile(dataset, file, newLayout, raFile, writable);
                }
            }
        }
        dataset.layout = newLayout;
        return newDataFile;
    }

    @Override
    public String toString() {
        return fileName;
    }

    /**
     * Create a new dataset file in NMRView format.
     *
     * @param fullName     The full path to the new file
     * @param title        The title to be used for the new dataset
     * @param dimSizes     The sizes of the new dataset.
     * @param closeDataset If true, close dataset after creating
     * @return the created Dataset
     * @throws DatasetException if an I/O error occurred when writing out file
     */
    public static Dataset createDataset(String fullName, String fileName, String title, int[] dimSizes, boolean closeDataset, boolean createFile) throws DatasetException {
        Dataset dataset = new Dataset(fullName, fileName, title, dimSizes, closeDataset, createFile);
        if (closeDataset) {
            dataset.close();
            dataset = null;
        }
        return dataset;
    }

    /**
     * Create a new dataset file from the provided path by first loading as an NMRData and then converting to a dataset.
     *
     * @param name     The name of the new dataset.
     * @param fullName The name of the file containing the path to the dataset file.
     * @return A new dataset or null if no NMRData type was found.
     * @throws IOException      if an IO exception occurs.
     * @throws DatasetException if there is a problem creating the JCAMP dataset.
     */
    public static Dataset newLinkDataset(String name, String fullName) throws IOException, DatasetException {
        File linkFile = new File(fullName);
        log.info(fullName);
        String fileString = Files.readString(linkFile.toPath());
        log.info(fileString);
        NMRData nmrData = NMRDataUtil.getNMRData(new File(fileString));
        log.info("{}", nmrData);
        if (nmrData instanceof BrukerData brukerData) {
            return brukerData.toDataset(name);
        } else if (nmrData instanceof RS2DData rs2DData) {
            return rs2DData.toDataset(name);
        } else if (nmrData instanceof JCAMPData jcampData) {
            return jcampData.toDataset(name);
        }
        return null;
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
     * @throws java.io.IOException if datafile write state can't be changed
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

    /**
     * Get the Vec object. Null if the dataset stores data in a data file,
     * rather than Vec object.
     *
     * @return Vec storing data or null if data file mode.
     */
    @Override
    public Vec getVec() {
        return (Vec) vecMat;
    }

    /**
     * Get the size of the dataset along the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @return the size
     */
    @Override
    public int getSizeTotal(int iDim) {
        if (vecMat != null) {
            return vecMat.getSize() * (vecMat.isComplex() ? 2 : 1);
        } else {
            if (layout == null) {
                return 0;
            } else {
                return layout.getSize(iDim);
            }
        }
    }

    /**
     * Set the size of the dataset along the specified dimension.
     *
     * @param iDim Dataset dimension index
     * @param size the size to set
     */
    @Override
    public void setSizeTotal(final int iDim, final int size) {
        layout.setSize(iDim, size);
    }


    public void addFile() {
        addFile(this.fileName);
    }

    private void addFile(String datasetName) {
        ProjectBase.getActive().addDataset(this, datasetName);
    }

    public void rename(String newName) {
        ProjectBase.getActive().renameDataset(this, newName);
    }

    /**
     * Close this dataset.
     */
    @Override
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
            log.warn("Unable to close dataset", e);
        }
    }

    /**
     * Return whether dataset has a data file.
     *
     * @return true if this dataset has a data file associated with it.
     */
    public boolean hasDataFile() {
        return dataFile != null;
    }

    @Override
    public boolean isMemoryFile() {
        return memoryMode;
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
                points[j] = getSizeReal(dim[j]) - 1;
            }
            f[0] = readPoint(points, dim);
            points[j] = maxPoint[j] + 1;
            if (points[j] >= getSizeReal(dim[j])) {
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

    /**
     * Calculate a noise level for the dataset by analyzing the rms value of
     * data points in a corner of dataset. The resulting value is stored and can
     * be retrieved with getNoiseLevel
     *
     * @return the noise level
     */
    public Double guessNoiseLevel() {
        if (noiseLevel == null) {
            int[][] pt = new int[nDim][2];
            int[] cpt = new int[nDim];
            int[] dim = new int[nDim];
            double[] width = new double[nDim];
            for (int i = 0; i < nDim; i++) {
                dim[i] = i;
                pt[i][0] = 4;
                if (pt[i][0] >= getSizeTotal(i)) {
                    pt[i][0] = getSizeTotal(i) - 1;
                }
                pt[i][1] = getSizeTotal(i) / 8;
                cpt[i] = (pt[i][0] + pt[i][1]) / 2;
                width[i] = Math.abs(pt[i][0] - pt[i][1]);
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
     * @param pt    The bounds of the region in dataset points
     * @param cpt   The center point of each region
     * @param width the width of each region
     * @param dim   the dataset dimensions that the pt, cpt, and width parameters
     *              use
     * @return RegionData with statistical information about the specified
     * region
     * @throws java.io.IOException if an I/O error ocurrs
     */
    @Override
    synchronized public RegionData analyzeRegion(int[][] pt, int[] cpt, double[] width, int[] dim)
            throws IOException {
        int[] iPointAbs = new int[nDim];
        double[] iTol = new double[nDim];

        double fTol = 0.25;
        double threshold = 0.0;
        double threshRatio = 0.25;
        int pass2;

        if (vecMat != null) {
            setSizeTotal(0, vecMat.getSize());
        }

        int[] counterSizes = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            if (pt[i][1] >= pt[i][0]) {
                counterSizes[i] = pt[i][1] - pt[i][0] + 1;
            } else {
                counterSizes[i] = getSizeReal(dim[i]) + (pt[i][1] - pt[i][0] + 1);
            }
            iTol[i] = fTol * Math.abs(counterSizes[i]);
        }
        RegionData rData = new RegionData(this);
        for (pass2 = 0; pass2 < 2; pass2++) {
            if (pass2 == 1) {
                rData.setSVar(0.0);
                rData.setMean(rData.getVolume_r() / rData.getNpoints());
                threshold = threshRatio * rData.getCenter();
            }
            DimCounter counter = new DimCounter(counterSizes);
            for (int[] points : counter) {
                for (int i = 0; i < nDim; i++) {
                    points[i] += pt[i][0];
                    if (points[i] >= getSizeReal(dim[i])) {
                        points[i] = points[i] - getSizeReal(dim[i]);
                    }
                    iPointAbs[i] = points[i];
                }
                rData.setValue(readPoint(points, dim));

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
        rData.setMaxDPoint(optCenter(rData.getMaxPoint(), dim));
        if (rData.getNpoints() == 1) {
            rData.setRMS(0.0);
        } else {
            rData.setRMS(Math.sqrt(rData.getSumSq() / (rData.getNpoints() - 1)));
        }
        return rData;
    }

    public double[] getPercentile(double p, int[][] pt, int[] dim) throws IOException {
        PSquarePercentile pSquarePos = new PSquarePercentile(p);
        PSquarePercentile pSquareNeg = new PSquarePercentile(p);

        int[] counterSizes = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            if (pt[i][1] >= pt[i][0]) {
                counterSizes[i] = pt[i][1] - pt[i][0] + 1;
            } else {
                counterSizes[i] = getSizeTotal(dim[i]) + (pt[i][1] - pt[i][0] + 1);
            }
        }
        DimCounter counter = new DimCounter(counterSizes);
        for (int[] points : counter) {
            for (int i = 0; i < nDim; i++) {
                points[i] += pt[i][0];
                if (points[i] >= getSizeTotal(dim[i])) {
                    points[i] = points[i] - getSizeTotal(dim[i]);
                }
            }
            double value = readPointRaw(points, dim);

            if ((value != Double.MAX_VALUE) && (value >= 0.0)) {
                pSquarePos.increment(value);
            } else if ((value <= 0.0)) {
                pSquareNeg.increment(value);
            }
        }
        return new double[]{pSquarePos.getResult(), pSquareNeg.getResult()};

    }

    synchronized public double measureSDev(int[][] pt, int[] dim, double sDevIn, double ratio)
            throws IOException {

        int[] counterSizes = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            if (pt[i][1] >= pt[i][0]) {
                counterSizes[i] = pt[i][1] - pt[i][0] + 1;
            } else {
                counterSizes[i] = getSizeTotal(dim[i]) + (pt[i][1] - pt[i][0] + 1);
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
                if (points[i] >= getSizeTotal(dim[i])) {
                    points[i] = points[i] - getSizeTotal(dim[i]);
                }
            }
            double value = readPointRaw(points, dim);
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
            pt[i][1] = getSizeTotal(dim[i]) - 1;
            faceSize *= getSizeTotal(dim[i]);
            j++;
        }
        pt[0][1] = getSizeTotal(iDim) - 1;
        int newSize = pt[0][1] - pt[0][0] + 1;

        Vec rmsdVec = new Vec(newSize, false);

        ScanRegion scanRegion = new ScanRegion(pt, dim, this);
        int nEntries = scanRegion.buildIndex();

        int winSize = getSizeTotal(iDim) / 32;
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
                dSize = getSizeTotal(dim[i]);
            }
            rmsd[iDim][index] = sdev;
        }
    }

    /**
     * Check if dataset has stored rmsd values along each dimension as
     * calculated by measureSliceRMSD
     *
     * @return true if has stored rmsd values
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
            dSize = getSizeTotal(dim[i]);
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
     * @param pt    indices of dataset
     * @param dim   dataset dimensions used by pt indices
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

    boolean isDirty() {
        return dirty;
    }

    void setDirty(boolean value) {
        dirty = value;
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
            intensities[iPoint++] = readPointRaw(pValues, dim);
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
            intensities[iPoint++] = readPointRaw(points, dim);
        }
        return intensities;

    }

    /**
     * Get a list of positions that are within an elliptical region around a set
     * of points. Used to find points that should be included when doing peak
     * fitting.
     *
     * @param p2         Bounds of peak regions
     * @param cpt        Array of centers of peak positions
     * @param width      Array of widths of peaks
     * @param pdim       Array of integers indicating mapping of peak dimension to
     *                   dataset dimension
     * @param multiplier multiply width of regions to get elliptical region
     * @param minWidth:  minimum half width (in points) of region.  Ensures that at least a minium number of peaks are used
     * @return List of points near peak centers
     */
    public ArrayList<int[]> getFilteredPositions(final int[][] p2, final int[][] cpt, final double[][] width, int[] pdim, double multiplier, int minWidth) {
        int[] sizes = new int[p2.length];
        for (int iDim = 0; iDim < p2.length; iDim++) {
            if (p2[iDim][1] >= p2[iDim][0]) {
                sizes[iDim] = p2[iDim][1] - p2[iDim][0] + 1;
            } else {
                sizes[iDim] = layout.getSize(iDim) - (p2[iDim][0] - p2[iDim][1]) + 1;
            }
        }
        ArrayList<int[]> posArray = new ArrayList<>();
        DimCounter counter = new DimCounter(sizes);
        for (int[] counts : counter) {
            int[] aCounts = new int[counts.length];
            int j = 0;
            for (int value : counts) {
                aCounts[j] = value + p2[j][0];
                if (aCounts[j] >= getSizeTotal(j)) {
                    aCounts[j] -= getSizeTotal(j);
                } else if (aCounts[j] < 0) {
                    aCounts[j] += getSizeTotal(j);
                }
                j++;
            }
            boolean ok = false;
            for (int iPeak = 0; iPeak < cpt.length; iPeak++) {
                int iDim = 0;
                double delta2 = 0.0;
                for (int value : aCounts) {
                    if ((iDim >= sizes.length) || (iPeak > width.length)) {
                        log.info("{} {} {}", iPeak, sizes.length, width.length);
                        posArray.clear();
                        return posArray;
                    }
                    if (width[iPeak][iDim] != 0.0) {
                        double scaledWidth = multiplier * width[iPeak][iDim] / 2.0;
                        int delta = Math.abs(value - cpt[iPeak][iDim]);
                        if (delta > minWidth) {
                            delta2 += (delta * delta) / (scaledWidth * scaledWidth);
                        }
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
        return posArray;
    }

    /**
     * Set size of dataset to valid size for specified dimension (only used for
     * dimensions above the first)
     *
     * @param iDim Dataset dimension index
     */
    public void syncSize(int iDim) {
        if (iDim > 0) {
            setSizeTotal(iDim, getVSize(iDim));
        }
    }

    public DatasetLayout getLayout() {
        return layout;
    }

    /**
     * Flush the header values out to the dataset file.
     */
    public final synchronized void writeHeader() {
        writeHeader(true);
    }

    /**
     * Flush the header values out to the dataset file.
     *
     * @param nvExtra write extra values to dataset header
     */
    public final synchronized void writeHeader(boolean nvExtra) {
        if (dataFile != null) {
            dataFile.writeHeader(nvExtra);
        }
    }

    /**
     * Read an N dimensional matrix of values within the specified region of the
     * matrix.  The region is specified in complex or real (if dimensionis real)
     * points and translated to raw indices based on whether dimension is complex or not.
     *
     * @param pt     The region to read
     * @param dim    The dataset dimensions used by the region points
     * @param matrix A matrix in which to store the read values. Must be at
     *               least as big as region.
     * @return The maximum of the absolute values of the read values
     * @throws java.io.IOException if an I/O error ocurrs
     */
    synchronized public float readMatrix(int[][] pt,
                                         int[] dim, float[][] matrix) throws IOException {
        float maxValue = Float.NEGATIVE_INFINITY;
        float minValue = Float.MAX_VALUE;
        int[] point = new int[nDim];
        int[] mul = new int[2];
        for (int i = 0; i < 2; i++) {
            mul[i] = getComplex(dim[i]) ? 2 : 1;
        }

        for (int i = 2; i < nDim; i++) {
            point[dim[i]] = pt[i][0];
        }
        for (int i = pt[0][0]; i <= pt[0][1]; i++) {
            int ii = i - pt[0][0];
            for (int j = pt[1][0]; j <= pt[1][1]; j++) {
                int jj = j - pt[1][0];
// fixme is this right for 3D rotated matrix
                if (axisReversed[dim[0]]) {
                    point[dim[0]] = getSizeTotal(dim[0]) - 1 - i * mul[0];
                } else {
                    point[dim[0]] = i * mul[0];
                }
                if (axisReversed[dim[1]]) {
                    point[dim[1]] = getSizeTotal(dim[1]) - 1 - j * mul[1];
                } else {
                    point[dim[1]] = j * mul[1];
                }
                float value = (float) readPointRaw(point);
                matrix[jj][ii] = value;
                if (value > maxValue) {
                    maxValue = value;
                }
                if (value < minValue) {
                    minValue = value;
                }
            }
        }
        return Math.max(Math.abs(maxValue), Math.abs(minValue));
    }

    /**
     * Read an N dimensional matrix of values within the specified region of the
     * matrix
     *
     * @param pt     The region to read
     * @param dim    The dataset dimensions used by the region points
     * @param matrix A matrix in which to store the read values. Must be at
     *               least as big as region.
     * @return The maximum of the absolute values of the read values
     * @throws java.io.IOException if an I/O error ocurrs
     */
    synchronized public double readMatrix(int[][] pt,
                                          int[] dim, double[][] matrix) throws IOException {
        double maxValue = Double.NEGATIVE_INFINITY;
        double minValue = Double.MAX_VALUE;
        int[] point = new int[nDim];
        for (int i = 2; i < nDim; i++) {
            point[dim[i]] = pt[i][0];
        }

        for (int plane = pt[1][0]; plane <= pt[1][1]; plane++) {
            int planeOffset = plane - pt[1][0];
            for (int row = pt[0][0]; row <= pt[0][1]; row++) {
                int rowOffset = row - pt[0][0];
                point[dim[0]] = row;
                point[dim[1]] = plane;
                double value = readPointRaw(point);
                matrix[planeOffset][rowOffset] = value;
                if (value > maxValue) {
                    maxValue = value;
                }
                if (value < minValue) {
                    minValue = value;
                }
            }
        }
        return Math.max(Math.abs(maxValue), Math.abs(minValue));
    }

    /**
     * Read an N dimensional matrix of values within the specified region of the
     * matrix
     *
     * @param pt     The region to read
     * @param dim    The dataset dimensions used by the region points
     * @param matrix A matrix in which to store the read values. Must be at
     *               least as big as region.
     * @return The maximum of the absolute values of the read values
     * @throws java.io.IOException if an I/O error occurs
     */
    synchronized public double readMatrixND(int[][] pt,
                                            int[] dim, MatrixND matrix) throws IOException {
        double maxValue = Double.NEGATIVE_INFINITY;
        double minValue = Double.MAX_VALUE;
        int[] point = new int[nDim];
        int mDims = matrix.getNDim();
        for (int i = mDims; i < nDim; i++) {
            point[dim[i]] = pt[i][0];
        }
        int[] mPoint = new int[mDims];
        for (int i = 0; i < mDims; i++) {
            mPoint[i] = pt[i][1] + 1;
            double sw = getSw(dim[i]);
            matrix.setDwellTime(i, 1.0 / getSw(dim[i]));
        }

        MultidimensionalCounter counter = new MultidimensionalCounter(mPoint);
        MultidimensionalCounter.Iterator iter = counter.iterator();
        while (iter.hasNext()) {
            iter.next();
            int[] index = iter.getCounts();
            for (int i = 0; i < index.length; i++) {
                point[dim[i]] = index[i];
            }
            double value = readPointRaw(point);
            matrix.setValue(value, index);
            if (value > maxValue) {
                maxValue = value;
            }
            if (value < minValue) {
                minValue = value;
            }
        }
        return Math.max(Math.abs(maxValue), Math.abs(minValue));
    }


    /**
     * Write a matrix of values to the dataset
     *
     * @param dim    indices of dimensions to write matrix along
     * @param matrix the values to write
     * @throws IOException if an I/O error occurs
     */
    public void writeMatrixToDatasetFile(int[] dim, Matrix matrix)
            throws IOException {
        int[] point = new int[nDim];
        int[][] pt = matrix.getPt();
        double[][] mat = matrix.getMatrix();
        for (int i = 2; i < nDim; i++) {
            point[dim[i]] = pt[i][0];
        }
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
     * @param dim    indices of dimensions to write matrix along
     * @param matrix the values to write
     * @throws IOException if an I/O error occurs
     */
    public void writeMatrixNDToDatasetFile(int[] dim, MatrixND matrix) throws IOException {
        int[][] pt = matrix.getPt();
        int[] point = new int[nDim];

        int mDims = matrix.getNDim();
        for (int i = mDims; i < nDim; i++) {
            point[dim[i]] = pt[i][0];
        }
        int[] mPoint = new int[mDims];
        for (int i = 0; i < mDims; i++) {
            mPoint[i] = pt[i][1] + 1;
        }


        int[] matVSizes = matrix.getVSizes();
        for (int i = 0; i < mDims; i++) {
            setVSize(dim[i], matVSizes[i]);
            setPh0(dim[i], matrix.getPh0(i));
            setPh1(dim[i], matrix.getPh1(i));
            setPh0_r(dim[i], matrix.getPh0(i));
            setPh1_r(dim[i], matrix.getPh1(i));
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

    public static void writeMatrixToDataset(String fileName, MatrixND matrixND) throws DatasetException, IOException {
        int[] dSizes = new int[matrixND.getNDim()];
        int[] dims = new int[dSizes.length];
        for (int i = 0; i < dSizes.length; i++) {
            dSizes[i] = matrixND.getSize(i);
            dims[i] = i;
        }

        File file = new File(fileName);
        String name = file.getName();
        Dataset dataset = Dataset.createDataset(fileName, fileName, name, dSizes, false, true);
        for (int i = 0; i < dSizes.length; i++) {
            dataset.setComplex(i, false);
            dataset.setFreqDomain(i, true);
        }
        dataset.writeMatrixNDToDatasetFile(dims, matrixND);
        dataset.writeParFile();
        dataset.close();
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
            return (Dataset) ProjectBase.getActive().getDataset(fileName);
        }
    }

    /**
     * Return a list of the names of open datasets
     *
     * @return List of names.
     */
    synchronized public static List<String> names() {
        return ProjectBase.getActive().getDatasetNames();
    }

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
            counterSizes[i] = getSizeTotal(i);
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
        while (cIter.hasNext()) {
            int[] points = cIter.next();
            readPointRaw(points);
        }
        times[5] = System.currentTimeMillis();
        System.out.println("xxx");

        counter = new DimCounter(counterSizes);
        cIter = counter.iterator();
        while (cIter.hasNext()) {
            int[] points = cIter.next();
            readPointRaw(points, dim);
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
     * @param pt       raw indices specifying range of points to read from
     * @param dim      dataset dimensions that are used in pt array
     * @param rwVector the vector to put values in
     * @throws IOException if an I/O error occurs
     */
    public void readVectorFromDatasetFile(int[][] pt, int[] dim, VecBase rwVector) throws IOException {

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
            int vSize = getVSize_r(dim[0]);
            int dSize;
            if (vSize != 0) {
                dSize = getComplex_r(dim[0]) ? vSize / 2 : vSize;
            } else {
                dSize = getSizeReal(dim[0]);
            }
            if (rwVector.getSize() != dSize) {
                rwVector.dwellTime *= (double) dSize / rwVector.getSize();
            }
        } else {
            int dSize = getSizeTotal(dim[0]);
            if (rwVector.getSize() != dSize) {
                if (rwVector.getFreqDomain()) {
                    rwVector.dwellTime *= (double) dSize / rwVector.getSize();
                }
            }

        }
        rwVector.setPh0(getPh0_r(dim[0]));
        rwVector.setPh1(getPh1_r(dim[0]));
        rwVector.setTDSize(getTDSize(dim[0]));
        rwVector.setPt(pt, dim);
        if (getFreqDomain_r(dim[0])) {
            rwVector.setRefValue(getRefValue_r(dim[0]), (getRefPt_r(dim[0]) - pt[0][0]));
        } else {
            rwVector.setRefValue(getRefValue_r(dim[0]));
        }
        rwVector.setFreqDomain(getFreqDomain_r(dim[0]));

        int[] point = new int[nDim];
        for (int i = 1; i < nDim; i++) {
            if (getAxisReversed(dim[i])) {
                point[dim[i]] = getSizeReal(dim[i]) - 1 - pt[i][0];
            } else {
                point[dim[i]] = pt[i][0];
            }
        }
        if (vecMat != null) {
            int j = 0;
            // If the vectors are complex, convert the raw indices to real since the complex can be read from the
            // vecMat in one loop iteration (when reading from the file, it takes 2 loop iterations)
            if (rwVector.isComplex() && vecMat.isComplex()) {
                pt[0][0] /= 2;
                pt[0][1] /= 2;
            }
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
                if (axisReversed[dim[0]]) {
                    point[dim[0]] = getSizeTotal(dim[0]) - 1 - i;
                } else {
                    point[dim[0]] = i;
                }
                if (rwVector.isComplex()) {
                    if ((i % 2) != 0) {
                        double dImaginary = readPointRaw(point);
                        rwVector.set(j, new Complex(dReal, dImaginary));
                        j++;
                    } else {
                        dReal = readPointRaw(point);
                    }
                } else {
                    rwVector.set(j, readPointRaw(point));
                    j++;
                }
            }
        }
    }

    public Optional<Dataset> getExtractSource() {
        int sliceNamePos = getName().indexOf("_slice");
        Dataset sourceDataset = null;
        if (sliceNamePos != -1) {
            String sourceName = getName().substring(0, sliceNamePos);
            sourceDataset = Dataset.getDataset(sourceName);
        }
        return Optional.ofNullable(sourceDataset);
    }

    public void reloadVector(int iDim, int value) throws IOException {
        if (vecMat != null) {
            int[] vdim = vecMat.getDim();
            int[][] vpts = vecMat.getPt();
            value = Math.min(value, getSizeTotal(vdim[iDim]));
            value = Math.max(value, 0);
            vpts[iDim][0] = vpts[iDim][1] = value;
            var sourceOpt = getExtractSource();
            if (sourceOpt.isPresent()) {
                sourceOpt.get().readVectorFromDatasetFile(vpts, vdim, vecMat);
            }
        }
    }

    /**
     * Read values along specified row. Only appropriate for 2D datasets
     *
     * @param row The row to read
     * @return the data values
     * @throws IOException if an I/O error occurs
     */
    public ArrayRealVector getRowVector(int row) throws IOException {
        int vecSize = getSizeTotal(0);
        int[] pt = new int[nDim];
        pt[1] = row;
        ArrayRealVector vector = new ArrayRealVector(vecSize);
        for (int i = 0; i < vecSize; i++) {
            pt[0] = i;
            vector.setEntry(i, readPointRaw(pt));
        }
        return vector;
    }

    /**
     * Read specified values along specified row. Only appropriate for 2D
     * datasets
     *
     * @param row     the row to read
     * @param indices List of points to read along row
     * @return the data values
     * @throws IOException              if an I/O error occurs
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
            vector.setEntry(i, readPointRaw(pt));
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
        int vecSize = getSizeTotal(1);
        int[] pt = new int[nDim];
        pt[0] = column;
        ArrayRealVector vector = new ArrayRealVector(vecSize);
        for (int i = 0; i < vecSize; i++) {
            pt[1] = i;
            vector.setEntry(i, readPointRaw(pt));
        }
        return vector;
    }

    /**
     * Read specified values along specified column. Only appropriate for 2D
     * datasets
     *
     * @param column  the column of dataset to read
     * @param indices List of points to read along column
     * @return the values
     * @throws IOException              if an I/O error occurs
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
            vector.setEntry(i, readPointRaw(pt));
        }
        return vector;
    }

    /**
     * Return a 2D matrix of data values from specified rows and columns
     *
     * @param rowIndices    List of rows
     * @param columnIndices List of columns
     * @return the matrix of values
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if row or column indices is null or
     *                                  empty
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
     * Return a 2D matrix of data values from specified rows and columns
     *
     * @param rowIndices    List of rows
     * @param columnIndices List of columns
     * @return the matrix of values
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if row or column indices is null or
     *                                  empty
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
                matrix.setEntry(i, j, readPointRaw(pt));
            }
        }
        return matrix;
    }

    /**
     * Make a Dataset file from a matrix of values
     *
     * @param matrix      The matrix of values
     * @param fullName    The name of the file to create
     * @param datasetName The name (title) of the dataset.
     * @throws DatasetException if an I/O error occurred while creating dataset
     * @throws IOException      if an I/O error occurs
     */
    public static void makeDatasetFromMatrix(RealMatrix matrix, String fullName, String datasetName) throws DatasetException, IOException {
        int nRows = matrix.getRowDimension();
        int nColumns = matrix.getColumnDimension();
        int[] dimSizes = {nRows, nColumns};
        int[] pt = new int[2];
        Dataset dataset = createDataset(fullName, fullName, datasetName, dimSizes, false, true);
        for (int i = 0; i < nRows; i++) {
            for (int j = 0; j < nColumns; j++) {
                pt[0] = i;
                pt[1] = j;
                dataset.writePoint(pt, matrix.getEntry(i, j));
            }
        }
        dataset.close();
    }

    public BucketedMatrix getBucketedSubMatrixFromRegions(int bucketSize) throws IOException {

        int[] rowIndices = new int[getSizeTotal(1)];
        for (int i = 0; i < rowIndices.length; i++) {
            rowIndices[i] = i;
        }
        var columnSet = new TreeSet<Integer>();
        for (var region : getReadOnlyRegions()) {
            double ppm0 = region.getRegionStart(0);
            int pt1 = ppmToPoint(0, ppm0);
            double ppm1 = region.getRegionEnd(0);
            int pt0 = ppmToPoint(0, ppm1);
            for (int pt = pt0; pt <= pt1; pt++) {
                columnSet.add(pt);
            }
        }
        var columns = columnSet.stream().sorted().collect(Collectors.toList());

        return getBucketedSubMatrix(rowIndices, columns, bucketSize, null);
    }

    /**
     * Create a @see BucketMatrix from this dataset
     *
     * @param rowIndices    Indices of rows to include in bucketing
     * @param columnIndices Indices of columns to include in bucketing
     * @param bucketSize    size of the bucket
     * @param dataTbl       Names for rows and columns
     * @return a BucketMatrix object containing the bucket values from dataset
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if row or column indices is null or
     *                                  empty
     */
    public BucketedMatrix getBucketedSubMatrix(int[] rowIndices, List<Integer> columnIndices, int bucketSize, String[][] dataTbl) throws IOException, IllegalArgumentException {
        if ((rowIndices == null) || (rowIndices.length == 0)) {
            throw new IllegalArgumentException("Empty or null indices");
        }
        if ((columnIndices == null) || (columnIndices.isEmpty())) {
            throw new IllegalArgumentException("Empty or null indices");
        }

        int nRows = rowIndices.length;

        ArrayList<ArrayList<Integer>> bucketList = new ArrayList<>();
        ArrayList<Integer> bucketIndices = new ArrayList<>();
        ArrayList<Integer> colList;

        int lastBucket = -1;
        int jBucket = -1;
        int firstColumn = columnIndices.get(0);

        if (bucketSize == 0) {
            int lastIndex = -2;
            for (var j : columnIndices) {
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
            for (var j : columnIndices) {
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
        double[][] matrix = new double[nRows][nBuckets];
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
                for (Integer integer : colList) {
                    pt[0] = integer;
                    pt[1] = rowIndices[i];
                    sumPPM += pointToPPM(0, pt[0]);
                    sumCol += pt[0];
                    sum += readPointRaw(pt);
                }
                matrix[i][k] = sum;
                ppms[k] = sumPPM / colList.size();
                colCenters[k] = (int) Math.round(sumCol / colList.size());
            }
        }
        return new BucketedMatrix(matrix, rowIndices, colIndices, colCenters, ppms, dataTbl);
    }

    public class Location {

        int[] dim;
        int[][] pt;

        public Location(int[] dim, int[][] pt) {
            this.dim = dim;
            this.pt = pt;
        }
    }

    public Location getLocation(Vec vector, int[] indices, int iDim) {
        int[] dim = new int[nDim];
        int[][] pt = new int[nDim][2];
        dim[0] = iDim;
        int jDim = 0;
        for (int i = 1; i < nDim; i++) {
            if (jDim == iDim) {
                jDim++;
            }
            dim[i] = jDim++;
        }

        for (int i = 0; i < nDim; i++) {
            if (i == 0) {
                pt[i][0] = 0;
                if (vector.isComplex()) {
                    pt[i][1] = 2 * vector.getSize() - 1;
                } else {
                    pt[i][1] = vector.getSize() - 1;
                }
            } else {
                pt[i][0] = indices[i - 1];
                pt[i][1] = indices[i - 1];
            }
        }
        return new Location(dim, pt);
    }

    /**
     * Read vector from two dimensional dataset
     *
     * @param index the index of vector to read
     * @param iDim  read values along this dimension index
     * @return Vec the vector read from dataset
     * @throws IOException if an I/O error occurs
     */
    public Vec readVector(int index, int iDim) throws IOException {
        Vec vector = new Vec(getSizeReal(iDim), getComplex(iDim));
        readVector(vector, index, iDim);
        return vector;

    }

    /**
     * Read vector from a two dimensional dataset
     *
     * @param vector Store dataset values in this vec
     * @param index  the index of vector to read
     * @param iDim   read values along this dimension index
     * @throws IOException if an I/O error occurs
     */
    public void readVector(Vec vector, int index, int iDim) throws IOException {
        int[] indices = {index};
        readVector(vector, indices, iDim);
    }

    /**
     * Read vector from dataset
     *
     * @param vector  Store dataset values in this vec
     * @param indices the indices of vector to read
     * @param iDim    read values along this dimension index
     * @throws IOException if an I/O error occurs
     */
    public void readVector(Vec vector, int[] indices, int iDim) throws IOException {
        Location location = getLocation(vector, indices, iDim);
        readVectorFromDatasetFile(location.pt, location.dim, vector);
    }

    /**
     * Read a vector from the dataset at the location stored in the vector's
     * header
     *
     * @param vector Store data values in this vector object.
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException If the vector doesn't have a location in
     *                                  header.
     */
    public void readVector(Vec vector) throws IOException, IllegalArgumentException {
        if ((vector.getPt() == null) || (vector.getDim() == null)) {
            throw new IllegalArgumentException("Vector doesn't have stored location");
        }
        readVectorFromDatasetFile(vector.getPt(), vector.getDim(), vector);
    }

    /**
     * Write vector to a two dimensional dataset
     *
     * @param vector Store dataset values in this vec
     * @param index  the index of vector to write
     * @param iDim   write values along this dimension index
     * @throws IOException if an I/O error occurs
     */
    public void writeVector(Vec vector, int index, int iDim) throws IOException {
        int[] indices = {index};
        Location location = getLocation(vector, indices, iDim);
        writeVecToDatasetFile(location.pt, location.dim, vector);
    }

    /**
     * Write vector to the dataset at the specified location. The location is
     * specified with indices indicating the offset for each dimension and the
     * vector is written along the specified dimension. The location at which
     * the vector is written is stored in the vector header.
     *
     * @param vector  the vector to write
     * @param indices The location at which to write the vector.
     * @param iDim    Vector is written parallel to this dimension.
     * @throws IOException              if an I/O exception occurs
     * @throws IllegalArgumentException if dataset stores data in a Vec object
     *                                  (not dataset file)
     */
    public void writeVector(Vec vector, int[] indices, int iDim) throws IOException, IllegalArgumentException {
        Location location = getLocation(vector, indices, iDim);
        vector.setPt(location.pt, location.dim);
        writeVector(vector);
    }

    public void writeMatrixType(MatrixType matrixType) throws IOException {
        if (matrixType != null) {
            if (matrixType instanceof Vec) {
                writeVector((Vec) matrixType);
            } else {
                MatrixND matrix = (MatrixND) matrixType;
                writeMatrixNDToDatasetFile(matrix.getDim(), matrix);
            }
        }
    }

    /**
     * Write the vector to the dataset at the location stored in the vector.
     *
     * @param vector the vector to write
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if dataset stores data in a Vec object
     *                                  (not dataset file)
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
     * @param pt     index in points where vector should be written.
     * @param dim    Specify the dimension that each entry in the pt array refers
     *               to
     * @param vector the vector to write
     * @throws IOException if an I/O error occurs
     */
    public void writeVecToDatasetFile(int[][] pt, int[] dim, Vec vector) throws IOException {

        if (vecMat != null) {
            throw new IllegalArgumentException("Don't call this method on a vector type dataset");
        }

        setDirty(true);
        int[] point = new int[nDim];
        for (int i = 1; i < nDim; i++) {
            point[dim[i]] = pt[i][0];
        }
        for (int i = 0; i < nDim; i++) {
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

        double dimRefPoint = vector.freqDomain() ? (vector.getSize() / 2.0) + pt[0][0] : getSizeReal(dim[0]) / 2.0;
        double dimRefValue = vector.getRefValue();

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

    }

    /**
     * Copy header from this dataset to another dataset
     *
     * @param targetDataset dataset to copy header to
     */
    public void copyHeader(Dataset targetDataset) {
        for (int i = 0; i < nDim; i++) {
            copyHeader(targetDataset, i, i);
        }
        targetDataset.setSolvent(getSolvent());
        targetDataset.setTitle(getTitle());
        targetDataset.writeHeader();
    }

    public void copyHeader(Dataset targetDataset, int sDim, int tDim) {
        targetDataset.setSf(tDim, getSf(sDim));
        targetDataset.setSw(tDim, getSw(sDim));
        targetDataset.setSw_r(tDim, getSw_r(sDim));
        targetDataset.setRefValue_r(tDim, getRefValue_r(sDim));
        targetDataset.setRefValue(tDim, getRefValue(sDim));
        targetDataset.setRefPt_r(tDim, getRefPt_r(sDim));
        targetDataset.setRefPt(tDim, getRefPt(sDim));
        targetDataset.setRefUnits(tDim, getRefUnits(sDim));
        targetDataset.setLabel(tDim, getLabel(sDim));
        targetDataset.setDlabel(tDim, getDlabel(sDim));
        targetDataset.setNucleus(tDim, getNucleus(sDim));
        targetDataset.setFreqDomain(tDim, getFreqDomain(sDim));
        targetDataset.setFreqDomain_r(tDim, getFreqDomain_r(sDim));
        targetDataset.setComplex(tDim, getComplex(sDim));
        targetDataset.setComplex_r(tDim, getComplex_r(sDim));
        targetDataset.setVSize_r(tDim, getVSize_r(sDim));
        targetDataset.setVSize(tDim, getVSize(sDim));

    }

    public void copyReducedHeader(Dataset targetDataset, int sDim, int tDim) {
        targetDataset.setComplex(tDim, getComplex(sDim));
        targetDataset.setComplex_r(tDim, getComplex_r(sDim));

        targetDataset.setSf(tDim, getSf(sDim));
        int sSize = getSizeReal(sDim);
        int tSize = targetDataset.getSizeReal(tDim);
        double f = (double) tSize / sSize;


        targetDataset.setSw(tDim, getSw(sDim) * f);
        targetDataset.setSw_r(tDim, getSw_r(sDim) * f);
        targetDataset.setRefValue_r(tDim, getRefValue_r(sDim));
        targetDataset.setRefValue(tDim, getRefValue(sDim));
        targetDataset.setRefPt_r(tDim, getRefPt_r(sDim));
        targetDataset.setRefPt(tDim, getRefPt(sDim));
        targetDataset.setRefUnits(tDim, getRefUnits(sDim));
        targetDataset.setLabel(tDim, getLabel(sDim));
        targetDataset.setDlabel(tDim, getDlabel(sDim));
        targetDataset.setNucleus(tDim, getNucleus(sDim));
        targetDataset.setFreqDomain(tDim, getFreqDomain(sDim));
        targetDataset.setFreqDomain_r(tDim, getFreqDomain_r(sDim));
        targetDataset.setVSize_r(tDim, targetDataset.getSizeTotal(tDim));
    }


    public String saveMemoryFile() throws IOException, DatasetException {
        String path = null;
        if (isMemoryFile()) {
            path = file.getCanonicalPath();
            copyDataset(path, file.getName() + ".memtmp");
        }
        return path;
    }

    /**
     * Copy dataset to a new file
     *
     * @param newFileName File name of new dataset.
     * @param key         Dataset key.
     * @throws IOException      if an I/O error occurs
     * @throws DatasetException if an I/O error occured while creating dataset
     */
    public void copyDataset(String newFileName, String key) throws IOException, DatasetException {
        int[][] pt = new int[nDim][2];
        int[] dim = new int[nDim];
        dim[0] = 0;
        pt[0][0] = 0;
        pt[0][1] = 0;

        int[] datasetSizes = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            dim[i] = i;
            pt[i][0] = 0;
            pt[i][1] = getSizeTotal(i) - 1;
            datasetSizes[i] = getSizeTotal(i);
        }
        int newSize = pt[0][1] - pt[0][0] + 1;
        Dataset newDataset = null;
        try {
            newDataset = Dataset.createDataset(newFileName, key, newFileName, datasetSizes, false, true);

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
            newDataset.sourceFID(sourceFID().orElse(null));
            newDataset.writeHeader(false);
            newDataset.writeParFile();
        } finally {
            if (newDataset != null) {
                newDataset.close();
            }
        }
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
                pt[i][1] = getSizeTotal(dim[i]) - 1;
                j++;
            }
            pt[0][1] = getSizeTotal(iDim) - 1;
            origSize = pt[0][1];
            int newSize = pt[0][1] - pt[0][0] + 1;
            if (getComplex(iDim)) {
                newSize /= 2;
            }
            vec = new Vec(newSize, getComplex(iDim));
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
                pt[i][1] = getSizeTotal(dim[i]) - 1;
                j++;
            }
            pt[0][1] = getSizeTotal(iDim) - 1;
            origSize = pt[0][1];
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
        return new VecIterator(this, iDim);
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
        return new VecIndexIterator(this, iDim);
    }

    /**
     * Get iterator that allows iterating over all the points in the file
     *
     * @return iterator an Iterator to iterate over points in dataset
     */
    synchronized public Iterator pointIterator() {
        int[] mPoint = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            mPoint[nDim - i - 1] = getSizeTotal(i) - 1;
        }
        MultidimensionalCounter counter = new MultidimensionalCounter(mPoint);
        return counter.iterator();
    }

    public List<int[][]> getIndices(int iDim, int start, int end) throws IOException {
        Iterator<int[][]> iter = indexer(iDim);
        List<int[][]> indices = new ArrayList<>();
        if (start < 0) {
            start = 0;
        }
        if (end >= getSizeTotal(iDim)) {
            end = getSizeTotal(iDim) - 1;
        }
        while (iter.hasNext()) {
            int[][] pt = iter.next();
            pt[0][0] = start;
            pt[0][1] = end;
            indices.add(pt);
        }
        return indices;
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
            log.info("simVecs {}", simVecs);
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
        for (int i = 0; i < nDim; i++) {
            counterSizes[i] = getSizeTotal(i);
        }
        DimCounter counter = new DimCounter(counterSizes);
        return counter.iterator();
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
            bufferSize *= getSizeTotal(i);
        }
        if ((buffer == null) || (buffer.length != bufferSize)) {
            buffer = new double[bufferSize];
            buffers.put(bufferName, buffer);
        }
        return buffer;
    }

    public double[] toBuffer(String bufferName) throws IOException {
        double[] buffer = getBuffer(bufferName);
        DimCounter.Iterator cIter = getPointIterator();
        int j = 0;
        while (cIter.hasNext()) {
            int[] points = cIter.next();
            double value = readPointRaw(points);
            buffer[j++] = value;
        }
        return buffer;
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

    public double[] autoPhase(int iDim, boolean firstOrder, int winSize, double ratio, double ph1Limit, IDBaseline2.ThreshMode threshMode, boolean apply) throws IOException {
        if (!isWritable()) {
            changeWriteMode(true);
        }
        DatasetPhaser phaser = new DatasetPhaser(this);
        phaser.setup(iDim, winSize, ratio, threshMode);
        double dph0;
        double dph1 = 0.0;
        if (firstOrder) {
            double[] phases = phaser.getPhase(ph1Limit);
            if (apply) {
                phaser.applyPhases2(iDim, phases[0], phases[1]);
            }
            dph0 = phases[0];
            dph1 = phases[1];
        } else {
            dph0 = phaser.getPhaseZero();
            if (apply) {
                phaser.applyPhases2(iDim, dph0, 0.0);
            }
        }
        if (apply) {
            setPh0(iDim, dph0);
            setPh0_r(iDim, dph0);
            setPh1(iDim, dph1);
            setPh1_r(iDim, dph1);
            writeHeader();
            dataFile.force();
        }
        return new double[]{dph0, dph1};
    }

    public Dataset getProjection(int iDim) {
        if (projections == null) {
            return null;
        } else {
            return projections[iDim];
        }
    }

    public void project(int iDim, int[][] viewPt) throws IOException, DatasetException {
        if (projections == null) {
            projections = new Dataset[getNDim()];
        }
        projections[iDim] = projectND(iDim, viewPt);
    }

    public Dataset projectND(int iDim, int[][] viewPt) throws IOException, DatasetException {
        if (projections == null) {
            projections = new Dataset[getNDim()];
        }

        int projNDim = nDim - 1;
        int[] dimSizes = new int[projNDim];
        int[] dims = new int[projNDim];
        int[] projDims = new int[projNDim];
        String dimLabel = "";
        int[] mPoint = new int[nDim];
        int[] startPoint = new int[nDim];
        for (int i = 0; i < nDim; i++) {
            if (viewPt != null) {
                mPoint[i] = viewPt[i][1] - viewPt[i][0] + 1;
                startPoint[i] = viewPt[i][0];
                if (mPoint[i] == 1) {
                    mPoint[i] = getSizeReal(i);
                    startPoint[i] = 0;
                }
            } else {
                mPoint[i] = getSizeReal(i);
                startPoint[i] = 0;
            }
        }
        boolean adjusted = false;
        for (int i = 0, j = 0; i < nDim; i++) {
            if (i != iDim) {
                dimSizes[j] = mPoint[i];
                if (dimSizes[j] != getSizeReal(i)) {
                    adjusted = true;
                }
                projDims[j] = j;
                dims[j] = i;
                dimLabel += (i + 1);
                j++;
            }
        }
        String projFileName = getFileName();
        String extension = "";
        if (projFileName.endsWith(".nv")) {
            extension = ".nv";
        } else if (projFileName.endsWith(".ucsf")) {
            extension = ".ucsf";
        }
        if (!extension.isEmpty()) {
            projFileName = projFileName.substring(0, projFileName.length() - extension.length());
        }
        projFileName = projFileName + DatasetBase.DATASET_PROJECTION_TAG + dimLabel + extension;
        File projFile = new File(projFileName);

        Dataset currProjection = Dataset.getDataset(projFile.getName());
        if (currProjection != null) {
            currProjection.close();
        }
        Dataset projDataset = Dataset.createDataset(projFileName, projFileName, projFile.getName(), dimSizes, false, true);
        projDataset.clear();
        for (int i = 0; i < projNDim; i++) {
            copyReducedHeader(projDataset, dims[i], i);
        }
        int[] projPoint = new int[projNDim];

        MultidimensionalCounter counter = new MultidimensionalCounter(mPoint);
        MultidimensionalCounter.Iterator iter = counter.iterator();
        int[] point = new int[nDim];
        while (iter.hasNext()) {
            iter.next();
            int[] index = iter.getCounts();
            for (int i = 0; i < index.length; i++) {
                point[i] = index[i] + startPoint[i];
            }
            double value = readPoint(point);
            for (int k = 0; k < projNDim; k++) {
                projPoint[k] = index[dims[k]];
            }
            double pValue = projDataset.readPoint(projPoint, projDims);
            if (value > pValue) {
                projDataset.writePoint(projPoint, value);
            }
        }

        for (int i = 0; i < projNDim; i++) {
            if (!adjusted) {
                projDataset.setRefValue(i, getRefValue(dims[i]));
                projDataset.setRefPt(i, getRefPt(dims[i]));
            } else {
                double zeroPPM = pointToPPM(dims[i], startPoint[dims[i]]);
                projDataset.setRefPt(i, 0);
                projDataset.setRefPt_r(i, 0);
                projDataset.setRefValue(i, zeroPPM);
                projDataset.setRefValue_r(i, zeroPPM);
            }
        }

        projDataset.writeHeader();
        projDataset.close();
        projDataset = new Dataset(projFileName, projFileName, false, false, true);
        return projDataset;
    }

    public Object getAnalyzerObject() {
        return analyzerObject;
    }

    public void setAnalyzerObject(Object analyzerObject) {
        this.analyzerObject = analyzerObject;
    }

    public void script(String script) {
        this.script = script;
    }

    public String script() {
        return script;
    }
}
