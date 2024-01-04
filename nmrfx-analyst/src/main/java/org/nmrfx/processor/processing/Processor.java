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
package org.nmrfx.processor.processing;

import org.greenrobot.eventbus.EventBus;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.MatrixType;
import org.nmrfx.math.VecBase;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetException;
import org.nmrfx.processor.datasets.MatrixTypeService;
import org.nmrfx.processor.datasets.ScanRegion;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.datasets.vendor.bruker.BrukerData;
import org.nmrfx.processor.datasets.vendor.rs2d.RS2DData;
import org.nmrfx.processor.events.DatasetSavedEvent;
import org.nmrfx.processor.math.Matrix;
import org.nmrfx.processor.math.MatrixND;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.operations.Invertible;
import org.nmrfx.processor.operations.Operation;
import org.nmrfx.processor.processing.processes.IncompleteProcessException;
import org.nmrfx.processor.processing.processes.ProcessOps;
import org.nmrfx.utilities.ProgressUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Processor contains all processes. It also contains the "current
 * ProcessOps", which is the process which operations will be added to if a
 * process is not specified.
 *
 * @author johnsonb
 */
@PythonAPI({"nmrpar", "pyproc"})
public class Processor {
    private static final Logger log = LoggerFactory.getLogger(Processor.class);
    private static long memoryModeLimit = 536870912L;
    private static boolean testCorruptionMode = false;
    private static int iDataNum = 0;


    private String fileName;
    private Dataset dataset;
    public ScanRegion scanregion;
    /**
     * The points to read Vectors from. Can be passed in as null.
     */
    private int[][] pt;
    /**
     * Dimension array for reading data.
     */
    private int[] dim;

    /**
     * Dimension array for mapping dataset dimension to FID dimension.
     */
    private int[] mapToFID;

    /**
     * Dimension array for mapping FID dimension to dataset dimension.
     */
    private int[] mapToDataset;
    /**
     * The total number of vector groups which will be read from file.
     */
    private int totalVecGroups = 0;
    /**
     * The number of vector groups which have been read.
     */
    private AtomicInteger vecGroupsRead = new AtomicInteger(0);
    /**
     * The number of vectors or matrices which have been written
     */
    private AtomicInteger mathObjectsWritten = new AtomicInteger(0);

    /**
     * The number of vectors which need to be written
     */
    private int itemsToWrite = 0;

    /**
     * The number of vectors which need to be read
     */
    private int itemsToRead = 0;
    /**
     * The maximum amount of vectors which will be given to each process.
     */
    private int maxVectorsPerProcess = 64;
    /**
     * The maximum amount of vectors which will be given to each process.
     */
    private int vectorsPerProcess = maxVectorsPerProcess;
    /**
     * The total number of matrices that will be read from file.
     */
    private AtomicInteger totalMatrices = new AtomicInteger(0);
    /**
     * The number of matrices that have been read.
     */
    private AtomicInteger matricesRead = new AtomicInteger(0);
    /**
     * The number of processes to create.
     */
    private static int numProcessors;
    /**
     * The size of each vector which will be read.
     */
    private int vectorSize;
    /**
     * True if the file has been completely read so that loading vectors will
     * stop.
     */
    private final AtomicBoolean endOfFile = new AtomicBoolean(false);
    /**
     * Processor is a singleton which is able to control and communicate with
     * processes.
     */
    private static Processor processor;
    /**
     * List of processes for thread pool.
     */
    private static ArrayList<Runnable> processes = null;
    private ExecutorService pool;
    /**
     * List of processes, one for each dimension. Used with runProcesses().
     */
    private static ArrayList<ProcessOps> dimProcesses = null;
    /**
     * The name of the current ProcessOps that Operations and Vec vector will
     * automatically be added to.
     */
    private static ProcessOps defaultProcess;

    /**
     * This flag is to test IOController / non-IOController IO for debugging.
     */
    public boolean useIOController = true;

    private AtomicBoolean processorError = new AtomicBoolean(false);
    private AtomicReference<String> errorMessage = new AtomicReference("");
    private AtomicBoolean matrixMode = new AtomicBoolean(false);

    private ArrayList<NMRData> nmrDataSets = new ArrayList<>();
    private boolean nvDataset = false;
    private boolean nvComplex;
    public boolean resizeFlag = false;
    private boolean keepDatasetOpen = false;
    /**
     * The number of dimensions in dataset.
     */
    public int nDim = 0;
    /**
     * The sizes of dataset to actually populate.
     */
    public int[] acqSizesToUse = null;
    /**
     * The sizes of time domain data.
     */
    public int[] acquiredTDSizes = null;
    /**
     * The complex nature of time domain data.
     */
    boolean[] complex = null;
    boolean[] newComplex = null;
    int[] adjustedTDSizes;
    int[] groupSizes;
    String[] acqOrderToUse;

    /**
     * Acquisition order array.
     */
    private String[] acqOrder = null;
    /**
     * Sample Schedule for Non-Uniform Sampling.
     */
    private SampleSchedule sampleSchedule = null;
    /**
     * Flag that signifies if the processor is currently processing processes.
     */
    private Boolean isRunning = false;

    private AtomicBoolean doneWriting = new AtomicBoolean(false);

    private AtomicInteger vecReadCount = new AtomicInteger(0);

    AtomicInteger vectorsRead = new AtomicInteger(0);

    private MultiVecCounter tmult;

    private static ProgressUpdater progressUpdater;
    boolean modeND = true;
    private double elapsedTime = 0.0;

    MatrixTypeService datasetWriter;

    LineShapeCatalog simVecProcessor = null;

    private final List<ProcessorAvailableStatusListener> listeners = new ArrayList<>();
    private final AtomicBoolean processorAvailable = new AtomicBoolean(true);
    private boolean tempFileMode = false;

    private void resetVecReadCount() {
        vecReadCount.set(0);
    }

    private void printVecReadCount() {
        if (dim[0] < 1) {
            log.warn("read FID vector count: {}", vecReadCount.get());
        }
    }

    public static void setUpdater(ProgressUpdater updater) {
        progressUpdater = updater;
    }

    /**
     * @return the only processor
     */
    public static Processor getProcessor() {
        if (processor == null) {
            processor = new Processor();
            createDefaultProcess();
        }
        return processor;
    }

    /**
     * @create and return only processor
     */
    public void reset() {
        nvDataset = false;
        createDefaultProcess();
        dimProcesses.clear();
        processes.clear();
    }

    private static void createDefaultProcess() {
        if (processes == null) {
            processes = new ArrayList<>();
        }
        if (dimProcesses == null) {
            dimProcesses = new ArrayList<>();
        }
        defaultProcess = new ProcessOps();
    }

    private Processor() {
        processes = new ArrayList<>();
        defaultProcess = null;

        //force NvLiteShell to be created
        numProcessors = Runtime.getRuntime().availableProcessors() / 2;
        if (numProcessors < 1) {
            numProcessors = 1;
        }
    }

    public ProcessOps getCurrent() throws ProcessingException {
        return defaultProcess;
    }

    /**
     * Return the current (Active) process.
     *
     * @return The current process which will be updated by unnamed commands.
     */
    public ProcessOps getDefaultProcess() {
        return defaultProcess;
    }

    public ProcessOps createProcess() {
        ProcessOps temp = new ProcessOps();
        return temp;
    }

    /**
     * Creates process with name.
     *
     * @param name
     * @return
     */
    public ProcessOps createProcess(String name) {
        ProcessOps temp = new ProcessOps(name);
        return temp;
    }

    private void setDefaultProcess(ProcessOps p) {
        defaultProcess = p;
    }

    /**
     * Get the name of the current process which operations and vectors will be
     * added to by default.
     *
     * @return Name of the current default process.
     */
    public String getCurrentName() {
        return defaultProcess.getName();
    }

    public void clearDatasets() {
        nmrDataSets.clear();
        acqOrder = null;
    }

    public void addDataset(NMRData nmrData) {
        nmrDataSets.clear();
        nmrDataSets.add(nmrData);
    }

    /**
     * Open a NMRView or Varian file in read-only mode.
     *
     * @param fileName
     * @return
     */
    public boolean opendata(String fileName) {
        return opendata(fileName, true);
    }

    /**
     * Open a NMRView or Varian file, specifying writability.
     *
     * @param fileName
     * @param writeable
     * @return
     */
    public boolean opendata(String fileName, boolean writeable) {
        String filetype = getFileType(fileName);
        if ("nv".equals(filetype)) {
            return openNV(fileName, null, writeable);
        } //        else if ("fid".equals(filetype)) {
        //            return openFID(fileName, null);
        //        } 
        else {
            log.warn("Do not recognize filetype {}", filetype);
            return false;
        }
    }

    public boolean openNV(String fileName) {
        return openNV(fileName, null, true);
    }

    public boolean openNV(String fileName, int[][] pt) {
        return openNV(fileName, pt, true);
    }

    /**
     * Open a NMRView file, specifying the points to read from and writability.
     * If pt is null, read whole file.
     *
     * @param fileName
     * @param pt        Points to read from, or null to read whole file
     * @param writeable
     * @return True if the file is opened
     */
    public boolean openNV(String fileName, int[][] pt, boolean writeable) {
        if (fileName == null || "".equals(fileName)) {
            return false;
        }
        this.fileName = fileName;
        nvDataset = true;

        String fileType = getFileType(fileName);
        if ("nv".equals(fileType)) {
            try {
                dataset = new Dataset(fileName, fileName, writeable, true);
            } catch (IOException ex) {
                log.warn("Could not create dataset. {}", ex.getMessage(), ex);
                return false;
            }
            mapToFID = new int[dataset.getNDim()];
            mapToDataset = new int[dataset.getNDim()];
            int j = 0;
            for (int i = 0; i < mapToDataset.length; i++) {
                mapToDataset[i] = -1;
                mapToDataset[i] = j;
                mapToFID[j] = i;
                j++;
            }
            return true;
        }

        return false;
    }

    public void setTempFileMode(boolean value) {
        tempFileMode = value;
    }

    public void adjustSizes() {
        NMRData nmrData = nmrDataSets.get(0);
        if (acqOrder == null) {
            acqOrder = nmrData.getAcqOrder();
        }
        int nDim = nmrData.getNDim();
        int nArray = 0;
        groupSizes = new int[nDim + nArray];
        for (int i = 1; i < nDim; i++) {
            int arraySize = nmrData.getArraySize(i);
            if (arraySize != 0) {
                nArray++;
            }
            complex[i] = nmrData.isComplex(i);
            groupSizes[i] = nmrData.getGroupSize(i);
        }

        if (nArray > 0) {
            adjustedTDSizes = new int[nDim + nArray];
            newComplex = new boolean[nDim + nArray];
            adjustedTDSizes[0] = acquiredTDSizes[0];
            newComplex[0] = complex[0];
            int j = 1;
            for (int i = 1; i < nDim; i++) {
                int arraySize = nmrData.getArraySize(i);
                if (arraySize != 0) {
                    adjustedTDSizes[j] = acquiredTDSizes[i];
                    if (nmrData instanceof BrukerData) {
                        adjustedTDSizes[j] /= arraySize;
                    }
                    adjustedTDSizes[j + 1] = arraySize;
                    newComplex[j] = complex[i];
                    newComplex[j + 1] = false;
                    j += 2;
                } else {
                    adjustedTDSizes[j++] = acquiredTDSizes[i];
                    newComplex[j - 1] = complex[i];
                }
            }
            acqOrderToUse = acqOrder;
            acqSizesToUse = adjustedTDSizes;
        } else {
            adjustedTDSizes = acquiredTDSizes;
            newComplex = complex;
            acqOrderToUse = acqOrder;
        }
        if (log.isDebugEnabled()) {
            StringBuilder acqOrderStr = new StringBuilder();
            for (int i = 0; i < acqOrderToUse.length; i++) {
                acqOrderStr.append(acqOrderToUse[i]).append(" ");
            }
            log.debug(acqOrderStr.toString());
        }

        acqSizesToUse = new int[adjustedTDSizes.length];
        System.arraycopy(adjustedTDSizes, 0, acqSizesToUse, 0, adjustedTDSizes.length);
    }

    public int[] getNewSizes() {
        NMRData nmrData = nmrDataSets.get(0);
        int nDim = nmrData.getNDim();
        int[] newSize = new int[nDim];
        System.arraycopy(adjustedTDSizes, 0, newSize, 0, newSize.length);
        return newSize;
    }

    void setupDirectDim() {
        int vectorsMultiDataMin = 1;
        NMRData nmrData = nmrDataSets.get(0);
        if (acqOrder == null) {
            acqOrder = nmrData.getAcqOrder();
        }
        if (nDim > 1) {
            if (acqSizesToUse.length <= adjustedTDSizes.length) {
                log.info("useSizes <= than newTDSizes {}", acqSizesToUse.length);
                int[] xOutSizes = acqSizesToUse.clone();
                boolean[] oComplex = newComplex.clone();
                int[] swapIn = new int[acqSizesToUse.length];
                boolean gotNeg = false;
                for (int i = 0; i < mapToDataset.length; i++) {
                    if (mapToDataset[i] == -1) {
                        gotNeg = true;
                    }
                }
                if (gotNeg) {
                    for (int i = 0; i < swapIn.length; i++) {
                        swapIn[i] = i;
                    }
                } else {
                    for (int i = 0; i < swapIn.length; i++) {
                        swapIn[i] = mapToDataset(i);
                    }
                }
                for (int i = 0; i < acqSizesToUse.length; i++) {
                    int iMap = i;
                    if (i < mapToDataset.length) {
                        iMap = mapToDataset(i);
                    }
                    if (iMap == -1) {
                        xOutSizes[i] = 1;
                    } else {
                        xOutSizes[i] = acqSizesToUse[swapIn[i]];
                        oComplex[i] = newComplex[swapIn[i]];
                    }
                }
                tmult = new MultiVecCounter(adjustedTDSizes, xOutSizes, groupSizes, oComplex, acqOrderToUse, swapIn, dataset.getNDim());
            } else {
                log.info("use newTDSize with useSize length {}", acqSizesToUse.length);
                tmult = new MultiVecCounter(adjustedTDSizes, groupSizes, newComplex, acqOrderToUse, acqSizesToUse.length);
            }
            int totalVecs = nmrData.getNVectors();
            if (acqSizesToUse != null) {
                totalVecs = 1;
                itemsToWrite = 1;
                int[] osizes = tmult.getOutSizes();
                for (var sz : osizes) {
                    totalVecs *= sz;
                }
                for (int i = 1; i < acqSizesToUse.length; i++) {
                    if ((i >= mapToDataset.length) || (mapToDataset[i] != -1)) {
                        if ((i < newComplex.length) && newComplex[i]) {
                            itemsToWrite *= acqSizesToUse[i] * 2;
                        } else {
                            itemsToWrite *= acqSizesToUse[i];
                        }
                    }
                }
                itemsToRead = totalVecs;
            }
            log.info(" total vecs {} {} {} {}", totalVecs, tmult.getGroupSize(), nmrData.getNVectors(), itemsToWrite);
            if (isNUS()) {
                sampleSchedule = nmrData.getSampleSchedule();
                sampleSchedule.setOutMult(groupSizes, complex, acqOrderToUse);
                itemsToWrite = sampleSchedule.getTotalSamples() * tmult.getGroupSize();
                itemsToRead = itemsToWrite;
            }

            totalVecGroups = totalVecs / tmult.getGroupSize();
            vectorsMultiDataMin = nmrDataSets.size() * tmult.getGroupSize();
        } else {
            totalVecGroups = nmrData.getNVectors();
            itemsToWrite = nmrData.getNVectors();
            itemsToRead = itemsToWrite;
        }
        vectorsPerProcess = totalVecGroups / numProcessors;
        log.info("totalVecGroups {} vectorsPerProcess {} vectin {} vectout {}", totalVecGroups, vectorsPerProcess, itemsToRead, itemsToWrite);
        if (vectorsPerProcess > maxVectorsPerProcess) {
            vectorsPerProcess = maxVectorsPerProcess;
        }
        if (vectorsPerProcess < Math.pow(2, nDim - 1)) {
            vectorsPerProcess = (int) Math.pow(2, nDim - 1);
        }
        if ((vectorsPerProcess % vectorsMultiDataMin) != 0) {
            vectorsPerProcess = vectorsMultiDataMin;
        }
    }

    /**
     * Set dataset dimension for already opened file
     *
     * @param iDim Dimensions corresponding to points, or null to read whole
     *             file
     * @return True if the file is opened
     */
    public boolean setDim(int iDim) {
        return setDim(null, iDim);
    }

    /**
     * Set dataset dimension for already opened file.
     *
     * @param newPt Points to read from, or null to read whole file
     * @param iDim  Dimensions corresponding to points, or null to read whole
     *              file
     * @return True if the file is opened
     */
    public boolean setDim(int[][] newPt, int iDim) {
        if (processor.getProcessorError()) {
            setProcessorAvailableStatus(true);
            log.warn("proc error");
            return false;
        }
        // fixme remove + 1 when we convert nmrdata dim indexing to 0 start
        iDim = mapToDataset(iDim);
        if ((!nvDataset) && (iDim > 0)) {
            setProcessorAvailableStatus(true);
            throw new ProcessingException("First DIM command must be DIM(1) not " + (iDim + 1));
        }
        this.dim = new int[dataset.getNDim()];
        dim[0] = iDim;
        // dim[0] is active dim, others in dim[1..len]
        int jDim = 0;
        for (int i = 1; i < dim.length; ++i) {
            if (jDim == iDim) {
                jDim++;
            }
            dim[i] = jDim;
            jDim++;
        }
        if ((!nvDataset) && (iDim < 1)) {
            setupDirectDim();
            return true;
        }

        // pt[i][0] pt[i][1] specify start/end points of vec in matrix
        if (newPt == null || newPt.length == 0) {
            this.pt = calcPt(dim);
        } else {
            this.pt = newPt.clone();
        }

        this.vectorSize = 1 + this.pt[0][1] - this.pt[0][0];

        if (dataset.getComplex_r(dim[0])) {
            this.vectorSize /= 2;
            nvComplex = true;
        } else {
            nvComplex = false;
        }

        // this is totalVecs for indirect dims, totalVecGroups for direct dim
        // not used in getVectorsFromFile if (nvDataset), not needed
        totalVecGroups = 1;
        for (int i = 1; i < this.pt.length; ++i) {
            totalVecGroups *= (this.pt[i][1] - this.pt[i][0] + 1);
        }
        log.info("complex {} vecSize {} nVectors {}", nvComplex, this.vectorSize, totalVecGroups);
        itemsToWrite = totalVecGroups;
        itemsToRead = itemsToWrite;
        scanregion = new ScanRegion(this.pt, this.dim, dataset);

        vectorsPerProcess = totalVecGroups / numProcessors;
        if (vectorsPerProcess > maxVectorsPerProcess) {
            vectorsPerProcess = maxVectorsPerProcess;
        }

        return true;
    }

    public int getNVectors() {
        return itemsToWrite;
    }

    public int[] getIndirectSizes() {
        if (tmult == null) {
            if (itemsToWrite > 1) {
                int[] idSizes = {itemsToWrite};
                return idSizes;
            } else {
                return new int[0];
            }
        } else {
            return tmult.getIndirectSizes();
        }
    }

    public boolean setMatDims(int[] dims) {
        if (processor.getProcessorError()) {
            setProcessorAvailableStatus(true);
            log.warn("proc error");
            return false;
        }
        if (!nvDataset) {
            setProcessorAvailableStatus(true);
            throw new ProcessingException("First process using DIM(1)");
        }
        if (dataset.getNDim() < 2 || dims.length < 2) {
            setProcessorAvailableStatus(true);
            throw new ProcessingException("Number of dimensions must be greater than two.");
        }
        this.dim = new int[dataset.getNDim()];
        for (int i = 0; i < dims.length; ++i) {
            dim[i] = dims[i];
        }
        int jDim = 0;
        for (int i = dims.length; i < dim.length; ++i) {
            for (int d : dims) {
                if (jDim == d) {
                    jDim++;
                }
            }
            dim[i] = jDim;
            jDim++;
        }

        this.pt = calcPt(dim);

        totalMatrices.set(pt[pt.length - 1][1] + 1);
        itemsToWrite = totalMatrices.get();
        itemsToRead = itemsToWrite;
        this.vectorSize = 1 + pt[0][1] - pt[0][0];

        if (dataset.getComplex_r(dim[0])) {
            this.vectorSize /= 2;
            nvComplex = true;
        } else {
            nvComplex = false;
        }

        matricesRead.set(0);
        return true;
    }

    private int[][] calcPt(int[] dim) {        // see setDim()
        int[][] pt = new int[dataset.getNDim()][2];
        for (int i = 0; i < dataset.getNDim(); ++i) {  // read dims from dataset
            pt[i][0] = 0;
            if (dim[i] == 0) {
                pt[i][1] = dataset.getSizeTotal(dim[i]) - 1;
            } else {
                pt[i][1] = dataset.getVSize(dim[i]) - 1;
                // fixme should we use vsize this.pt[i][1] = dataset.getVSize_r(dim[i]) - 1;
            }
        }
        return pt;
    }

    public String getFileType(String fileName) {
        String prefix = "";
        fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        if (fileName.equals("fid")) {
            return "fid";
        }
        if (fileName.contains(".")) {
            prefix = fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        return prefix;
    }

    /**
     * Open a FID file.
     *
     * @param filename
     * @param tdSizes  - time domain sizes
     * @return
     */
    public NMRData openfid(String filename, String nusFileName, int tdSizes[]) {
        NMRData nmrData;
        File nusFile = null;
        if (nusFileName != null) {
            nusFile = new File(nusFileName);
        }
        try {
            nmrData = NMRDataUtil.getFID(filename, nusFile);
        } catch (IOException ex) {
            setProcessorAvailableStatus(true);
            throw new ProcessingException("Cannot open FID " + filename);
        }

        nmrDataSets.add(nmrData);
        resetVecReadCount();
        setFidDimensions(nmrData, tdSizes);
        return nmrData;
    }

    /**
     * Open a FID file.
     *
     * @param filename
     * @return
     */
    public NMRData openfid(String filename, String nusFileName) {
        NMRData nmrData;
        File nusFile = null;
        if (nusFileName != null) {
            nusFile = new File(nusFileName);
        }
        try {
            nmrData = NMRDataUtil.getFID(filename, nusFile);
        } catch (IOException ex) {
            setProcessorAvailableStatus(true);
            throw new ProcessingException("Cannot open dataset \"" + filename + "\" because: " + ex.getMessage());
        }
        // read parameters
        int nDim = nmrData.getNDim();
        int tdSizes[] = new int[nDim];
        for (int i = 0; i < nDim; ++i) {
            tdSizes[i] = nmrData.getSize(i);
        }

        nmrDataSets.add(nmrData);
        resetVecReadCount();
        setFidDimensions(nmrData, tdSizes);
        adjustSizes();
        return nmrData;
    }

    public void setSizes(int[] tdSizes) {
        for (NMRData nmrData : nmrDataSets) {
            setFidDimensions(nmrData, tdSizes);
        }
    }

    public void setAcqOrder(String[] newOrder) {
        acqOrder = newOrder.clone();
    }

    private void setFidDimensions(NMRData nmrData, int tdSizes[]) {
        nDim = tdSizes.length;
        dim = new int[nDim];
        for (int i = 0; i < nDim; ++i) {
            dim[i] = i;
        }
        nvDataset = false;  // openfid() must first process FID vectors
        vectorSize = nmrData.getNPoints(); //complex
        this.acquiredTDSizes = tdSizes;

        // pt not needed here, not used
        pt = new int[nDim][2];
        complex = new boolean[nDim];
        for (int i = 0; i < nDim; ++i) {
            pt[i][0] = 0;
            pt[i][1] = tdSizes[i] - 1;
            complex[i] = nmrData.isComplex(i);
        }
        tmult = null;
    }

    /**
     * Is Non-Uniform Sampling used.
     *
     * @return true if Non-Uniform Sampling is used.
     */
    public boolean isNUS() {
        return (!nmrDataSets.isEmpty() && nmrDataSets.get(0).getSampleSchedule() != null);
    }

    private void setFidFlags(Map flags) {
        for (NMRData nmrData : nmrDataSets) {
            nmrData.setFidFlags(flags);
        }
    }

    // used from python
    public void setMapToDataset(int[] mapToDataset) {
        this.mapToDataset = mapToDataset.clone();
        mapToFID = new int[mapToDataset.length];
        for (int i = 0; i < mapToDataset.length; i++) {
            if (mapToDataset[i] != -1) {
                mapToFID[mapToDataset[i]] = i;
            }
        }
    }

    private int mapToDataset(int i) {
        return mapToDataset[i];
    }

    private int mapToFID(int i) {
        return mapToFID[i];
    }

    public static boolean useMemoryMode(int[] datasetSizes) {
        long size = Float.BYTES;
        for (int i = 0; i < datasetSizes.length; i++) {
            size *= datasetSizes[i];
        }
        return useMemoryMode(size);
    }

    public static boolean useMemoryMode(long size) {
        return size <= memoryModeLimit;
    }

    // used from Python for testing
    public static void setMemoryModeLimit(long size) {
        memoryModeLimit = size;
    }

    // called from Python
    public boolean createNV(String outputFile, int[] useSizes, int[] mapToDataset) {
        boolean memoryMode = useMemoryMode(useSizes);
        createNV(outputFile, useSizes, mapToDataset, memoryMode);
        return true;
    }

    // called from Python
    public boolean createNV(String outputFile, int[] useSizes, Map flags) {
        boolean memoryMode = useMemoryMode(useSizes);
        createNV(outputFile, useSizes, memoryMode);
        setFidFlags(flags);
        return true;
    }

    // called from Python
    public boolean createNVInMemory(String outputFile, int[] useSizes, int[] mapToDataset) {
        createNV(outputFile, useSizes, mapToDataset, true);
        return true;
    }

    public boolean createNV(String outputFile, int[] useSizes, boolean inMemory) {
        return createNV(outputFile, useSizes, null, inMemory);
    }

    public boolean createNV(String outputFile, int[] useSizes, int[] mapToDataset, boolean inMemory) {
        if (progressUpdater != null) {
            progressUpdater.updateStatus("Create output dataset");
        }
        int nDimToUse = 0;
        for (var map : mapToDataset) {
            if (map != -1) {
                nDimToUse++;
            }
        }

        this.acqSizesToUse = useSizes;
        File file = new File(outputFile);
        String key = file.getName();
        try {
            if (inMemory) {
                if (tempFileMode) {
                    key += ".tmp." + iDataNum++;
                }
                this.dataset = new Dataset(outputFile, nDimToUse);
            } else {
                int[] idSizes = getIndirectSizes();
                for (int i = 0; i < idSizes.length; i++) {
                    useSizes[i + 1] = idSizes[i];
                }
                this.dataset = Dataset.createDataset(outputFile, key, outputFile, useSizes, false, false);
            }
        } catch (DatasetException ex) {
            log.error(ex.getMessage(), ex);
            return false;
        }
        dataset.setScale(1.0);

        if (!nmrDataSets.isEmpty()) {
            NMRData nmrData = nmrDataSets.get(0);
            mapToFID = new int[dataset.getNDim()];
            if (mapToDataset == null) {
                mapToDataset = new int[nmrData.getNDim()];
                int j = 0;
                for (int i = 0; i < mapToDataset.length; i++) {
                    mapToDataset[i] = -1;
                    if (useSizes[i] > 1) {
                        mapToDataset[i] = j;
                        j++;
                    }
                }
            }
            for (int i = 0; i < mapToDataset.length; i++) {
                if (mapToDataset[i] != -1) {
                    mapToFID[mapToDataset[i]] = i;
                }
            }
            this.mapToDataset = mapToDataset.clone();
            for (int i = 0; i < dataset.getNDim(); i++) {
                int fidDim = mapToFID(i);
                if (fidDim < nmrData.getNDim()) {
                    dataset.setLabel(i, nmrData.getTN(mapToFID(i)));
                    dataset.setSf(i, nmrData.getSF(mapToFID(i)));
                    dataset.setSw(i, nmrData.getSW(mapToFID(i)));
                    dataset.setRefValue(i, nmrData.getRef(mapToFID(i)));
                    dataset.setRefPt(i, nmrData.getRefPoint(mapToFID(i)));
                    dataset.setComplex(i, nmrData.isComplex(mapToFID(i)));
                }
                dataset.setValues(i, nmrData.getValues(mapToFID(i)));
                dataset.setTDSize(i, useSizes[mapToFID(i)]);
            }
            dataset.setSolvent(nmrData.getSolvent());
            dataset.setTempK(nmrData.getTempK());
        }
        dataset.writeHeader();
        return true;
    }

    public void setupSim(double[] minWidths, double[] maxWidths, int[] nWidths, int[] nPoints,
                         int nFrac, String datasetName) {
        double[][] simWidths = new double[minWidths.length][2];
        for (int i = 0; i < minWidths.length; i++) {
            simWidths[i][0] = minWidths[i];
            simWidths[i][1] = maxWidths[i];
        }
        int[] nSimWidths = nWidths.clone();
        int[] nKeep = nPoints.clone();
        File file = new File(datasetName);
        String dirName = file.getParent();
        String fileName = file.getName();

        int index = fileName.indexOf(".nv");
        if (index == -1) {
            index = fileName.indexOf(".ucsf");
        }
        String shapeName;
        if (index != -1) {
            shapeName = fileName.substring(0, index) + "_lshapes.txt";
        } else {
            shapeName = fileName + "_lshapes.txt";
        }
        log.info(fileName);
        log.info(shapeName);
        log.info("{}", index);
        simVecProcessor = new LineShapeCatalog(nmrDataSets.get(0), simWidths, nSimWidths,
                nKeep, nFrac, dirName + File.separator + shapeName);

    }

    public String getFidProjectName(String filename) {
        return filename.substring(
                filename.substring(0, filename.lastIndexOf('/') - 1).lastIndexOf('/') + 1,
                filename.lastIndexOf("/fid"));
    }

    public void printDimPt(int[] dim, final int[][] pt) {
        printDimPt("", dim, pt);
    }

    public void printDimPt(String msg, int[] dim, final int[][] pt) {
        if (log.isDebugEnabled()) {
            StringBuilder matPrint = new StringBuilder("  matPrint " + msg + " dim=[");
            for (int i = 0; i < dim.length; i++) {
                matPrint.append(" ").append(dim[i]);
            }
            matPrint.append(" ] ");
            matPrint.append(" pt=[");
            for (int j = 0; j < pt.length; j++) {
                for (int k = 0; k < pt[j].length; k++) {
                    matPrint.append(" ").append(pt[j][k]);
                }
                matPrint.append(";");
            }
            matPrint.append(" ]");
            log.debug(matPrint.toString());
        }
    }

    public MatrixType getMatrixFromFile() {
        MatrixType matrix;
        String fileName;
        if (modeND) {
            matrix = getMatrixNDFromFile();
            fileName = "ND";
        } else {
            matrix = getMatrix2DFromFile();
            fileName = "2D";
        }
        return matrix;
    }

    public synchronized Matrix getMatrix2DFromFile() {
        Matrix matrix = null;
        if (!nvDataset) {
            setProcessorAvailableStatus(true);
            throw new ProcessingException("Cannot get matrix, not indirect dimension.");
        }
        if (endOfFile.get()) {
            return matrix;
        }
        int matrixCount = incrementMatricesRead();  // increment matrix count
        if (matrixCount >= getTotalMatrices()) {
            setEndOfFile();  // matrix is null
        } else {
            // zerofill matrix size for processing and writing
            int nPlanes = VecBase.checkPowerOf2(1 + pt[1][1]);
            int nRows = VecBase.checkPowerOf2(1 + pt[0][1]);
            int[][] writePt = calcPt(dim);
            writePt[1][1] = nPlanes - 1;
            writePt[0][1] = nRows - 1;
            writePt[2][0] = matrixCount;
            pt[2][0] = matrixCount;
            try {
                matrix = new Matrix(nPlanes, nRows, writePt);
                dataset.readMatrix(pt, dim, matrix.getMatrix());
            } catch (IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }
        return matrix;
    }

    public synchronized MatrixND getMatrixNDFromFile() {
        MatrixND matrix = null;
        if (!nvDataset) {
            setProcessorAvailableStatus(true);
            throw new ProcessingException("Cannot get matrix, not indirect dimension.");
        }
        if (endOfFile.get()) {
            return matrix;
        }
        int matrixCount = incrementMatricesRead();  // increment matrix count
        if (matrixCount >= getTotalMatrices()) {
            setEndOfFile();  // matrix is null
        } else {
            // zerofill matrix size for processing and writing
            int[][] writePt = calcPt(dim);
            int matrixDim = pt.length - 1;
            if (dim[0] == 0) {
                matrixDim++;
            }
            int[] matrixSizes = new int[matrixDim];
            // size in points of valid data (used for apodizatin etc.)
            int[] vSizes = new int[matrixDim];
            for (int i = 0; i < matrixDim; i++) {
                matrixSizes[i] = pt[i][1] + 1;
                vSizes[i] = (pt[i][1] + 1);
            }
            for (int i = 0; i < matrixDim; i++) {
                writePt[i][1] = matrixSizes[i] - 1;
            }
            if (matrixDim < nDim) {
                writePt[matrixDim][0] = matrixCount;
                pt[matrixDim][0] = matrixCount;
            }
            try {
                matrix = new MatrixND(writePt, dim, matrixSizes);
                matrix.setVSizes(vSizes);
                dataset.readMatrixND(pt, dim, matrix);
            } catch (IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }
        return matrix;
    }

    public List<Vec> getNextVectors() {
        if (useIOController) {
            while (true) {
                if (datasetWriter.finished()) {
                    return Collections.EMPTY_LIST;
                } else if (datasetWriter.hasError()) {
                    throw new ProcessingException("Error processing");
                } else {
                    List<MatrixType> matrixTypes = datasetWriter.getItemsFromUnprocessedList(100);
                    if (matrixTypes != null) {
                        int nRead = vectorsRead.addAndGet(matrixTypes.size());
                        List<Vec> vecs = new ArrayList<>();
                        for (MatrixType mat : matrixTypes) {
                            vecs.add((Vec) mat);
                        }
                        return vecs;
                    }
                }
            }
        } else {
            return getVectorsFromFile();
        }
    }

    public MatrixType getNextMatrix() {
        if (useIOController) {
            while (true) {
                if (datasetWriter.finished()) {
                    return null;
                } else if (datasetWriter.hasError()) {
                    throw new ProcessingException("Error processing");
                } else {
                    List<MatrixType> matrixTypes = datasetWriter.getItemsFromUnprocessedList(100);
                    if (matrixTypes != null) {
                        int nRead = vectorsRead.addAndGet(matrixTypes.size());
                        return matrixTypes.get(0);
                    }
                }
            }
        } else {
            return getMatrixFromFile();
        }
    }

    public List<MatrixType> getMatrixTypesFromFile() {
        List<MatrixType> mats = new ArrayList<>();
        if (matrixMode.get()) {
            mats.add(getMatrixNDFromFile());
        } else {

            List<Vec> vecs = getVectorsFromFile();
            for (Vec vec : vecs) {
                mats.add(vec);
            }
        }
        return mats;
    }

    /**
     * Gets the next 'vectorsPerProcess' Vecs from the data file and returns
     * them to the calling ProcessOps.
     *
     * @return ArrayList of Vecs from the dataset.
     */
    public synchronized List<Vec> getVectorsFromFile() {
        Vec temp;
        ArrayList<Vec> vectors = new ArrayList<>();

// pt[][] : coordinates in data matrix
// pt[0][0] to pt[0][1] is start/end coords for vec column
// pt[1][0] to pt[1][1] is start/end coords for orthogonal row
        if (nvDataset) {  // indirect dimensions
            int[][] pt;
            if (endOfFile.get()) {
                return vectors;
            }
            for (int i = 0; i < vectorsPerProcess; ++i) {
                pt = scanregion.nextPoint2();
                if (pt.length == 0) {
                    endOfFile.set(true);
                    break;
                }
                try {
                    temp = new Vec(vectorSize, pt, dim, nvComplex);
                    dataset.readVectorFromDatasetFile(pt, dim, temp);
                    if (temp.checkExtreme(1.0e16)) {
                        log.warn("extreme read");
                    }
                    vectors.add(temp);
                } catch (Exception ex) {
                    setProcessorAvailableStatus(true);
                    throw new ProcessingException(ex.getMessage(), ex);
                }
            }
        } else {  // direct dimension, read FIDs
            int vectorsPerGroup = 1;
            if (tmult != null) {
                vectorsPerGroup = tmult.getGroupSize();
            }
            int nSteps = vectorsPerProcess / vectorsPerGroup;
            for (int iStep = 0; iStep < nSteps; ) {
                int vecGroup = incrementVecGroupsRead();
                if (vecGroup > getTotalVecGroups() - 1) {
                    setEndOfFile();
                    break;
                }
                VecIndex vecIndex = null;
                try {
                    vecIndex = getNextGroup(vecGroup);
                } catch (Exception pEx) {
                    throw pEx;
                }

                if (vecIndex != null) {
                    iStep++;
                    for (int j = 0; j < vectorsPerGroup; j++) {
                        try {
                            for (NMRData nmrData : nmrDataSets) {
                                temp = new Vec(vectorSize, nmrData.isComplex(dim[0]));
                                nmrData.readVector(vecIndex.inVecs[j], temp);
                                if (testCorruptionMode) {
                                    for (int[] rowSkip : nmrData.getSkipIndices()) {
                                        if (rowSkip[0] == vecIndex.getOutVec(j)[1][0]) {
                                            temp.rand();
                                        }
                                    }
                                }
                                temp.setPt(vecIndex.outVecs[j], dim);
                                vectors.add(temp);
                            }
                        } catch (Exception e) {
                            setProcessorAvailableStatus(true);
                            throw new ProcessingException(e.getMessage(), e);
                        }
                        vecReadCount.incrementAndGet();
                    }
                }
            }
        }
        return vectors;
    }

    /**
     * Get the next group of input Vecs to be read, and set where they should be
     * written to. input Vecs may be used with Combine operation.
     *
     * @param vecNum
     * @return VecIndex: inVecs outVecs arrays
     * @see VecIndex
     * @see Vec
     */
    private VecIndex getNextGroup(final int vecNum) {
        if (nDim > 1) {
            VecIndex vecIndex = tmult.getNextGroup(vecNum);
            if (isNUS()) {
                return sampleSchedule.convertToNUSGroup(vecIndex, vecNum);
            } else {
                return vecIndex;
            }
        } else {
            int[] inVecs = {vecNum};
            int[][][] outVecs = new int[1][2][2];
            outVecs[0][1][0] = vecNum;
            outVecs[0][1][1] = vecNum;
            return new VecIndex(inVecs, outVecs);
        }
    }

    public void writeVector(Vec vector) {
        int nObj = mathObjectsWritten.incrementAndGet();
        if (progressUpdater != null) {
            if ((nObj == itemsToWrite) || ((nObj % 16) == 0)) {
                double f = (1.0 * nObj) / itemsToWrite;
                progressUpdater.updateProgress(f);
            }
        }
        vector.checkExtreme(1.0e16);
        int[][] pt = vector.getPt();
        for (int i = 0; i < pt.length; i++) {
            if (pt[i][0] < 0) {
                return;
            }
        }
        try {
            if (useIOController) {
                List<MatrixType> writeVectors = new ArrayList<>();
                writeVectors.add(vector);
                datasetWriter.addItemsToWriteList(writeVectors);
            } else {
                dataset.writeVector(vector);
            }
        } catch (IOException ex) {
            setProcessorAvailableStatus(true);
            throw new ProcessingException(ex.getMessage());
        } catch (NullPointerException npe) {
            setProcessorAvailableStatus(true);
            throw new ProcessingException("Dataset not open: can't write vector");
        }
    }

    public synchronized void writeMatrix(MatrixType matrix, String fileName) {

    }

    public synchronized void writeMatrix(MatrixType matrix) {
        if (useIOController) {
            int nObj = mathObjectsWritten.incrementAndGet();
            if (progressUpdater != null) {
                if ((nObj == totalMatrices.get()) || ((nObj % 16) == 0)) {
                    double f = (1.0 * nObj) / totalMatrices.get();
                    progressUpdater.updateProgress(f);
                }
            }
            List<MatrixType> mats = new ArrayList<>();
            mats.add(matrix);
            datasetWriter.addItemsToWriteList(mats);
        } else {
            if (matrix instanceof Matrix) {
                writeMatrix((Matrix) matrix);
            } else {
                writeMatrixND((MatrixND) matrix);
            }

        }

    }

    public synchronized void writeMatrix(Matrix matrix) {  // called by WRITE op
        int nObj = mathObjectsWritten.incrementAndGet();
        if (progressUpdater != null) {
            if ((nObj == totalMatrices.get()) || ((nObj % 16) == 0)) {
                double f = (1.0 * nObj) / totalMatrices.get();
                progressUpdater.updateProgress(f);
            }
        }
        try {
            if (matrix != null) {
                dataset.writeMatrixToDatasetFile(dim, matrix);
            }
        } catch (IOException ex) {
            setProcessorAvailableStatus(true);
            throw new ProcessingException(ex.getMessage());
        } catch (NullPointerException npe) {
            setProcessorAvailableStatus(true);
            throw new ProcessingException("Dataset not open: can't write matrix");
        }
    }

    public synchronized void writeMatrixND(MatrixND matrix) {  // called by WRITE op
        int nObj = mathObjectsWritten.incrementAndGet();
        if (progressUpdater != null) {
            if ((nObj == totalMatrices.get()) || ((nObj % 16) == 0)) {
                double f = (1.0 * nObj) / totalMatrices.get();
                progressUpdater.updateProgress(f);
            }
        }
        try {
            if (matrix != null) {
                dataset.writeMatrixNDToDatasetFile(dim, matrix);
            }
        } catch (IOException ex) {
            setProcessorAvailableStatus(true);
            throw new ProcessingException(ex.getMessage());
        } catch (NullPointerException npe) {
            setProcessorAvailableStatus(true);
            throw new ProcessingException("Dataset not open: can't write matrix");
        }
    }

    public void addDimProcess(int dim) {
        dimProcesses.add(new ProcessOps(dim));
    }

    public void addMatProcess(int... dims) {
        dimProcesses.add(new ProcessOps(dims));
    }

    public void addDatasetProcess() {
        dimProcesses.add(ProcessOps.createDatasetProcess());
    }

    public void addUndoDimProcess(int dim) {
        int nProcesses = dimProcesses.size();
        for (int iProcess = (nProcesses - 1); iProcess >= 0; iProcess--) {
            ProcessOps process = dimProcesses.get(iProcess);
            int[] dims = process.getDims();
            if ((dims[0] == dim) && (dims.length == 1)) {
                addUndoProcess(process);
                break;
            }
        }
    }

    public void addUndoProcess(ProcessOps process) {
        ProcessOps undoProcess = new ProcessOps(process.getDim(), true);
        dimProcesses.add(undoProcess);
        ArrayList<Operation> ops = process.getOperations();
        int nOps = ops.size();
        for (int iOp = (nOps - 1); iOp >= 0; iOp--) {
            Operation op = ops.get(iOp);
            if (op instanceof Invertible) {
                Operation inverseOp = ((Invertible) op).getInverseOp();
                undoProcess.add(inverseOp);
            }

        }
    }

    public ProcessOps getCurrentProcess() {
        ProcessOps p;
        if (dimProcesses.isEmpty()) {
            p = getDefaultProcess();  // may be null or gc
        } else {
            int n = dimProcesses.size() - 1;
            p = dimProcesses.get(n);
        }
        return p;
    }

    public int getCurrentDim() {
        ProcessOps p = getCurrentProcess();
        return p.getDim();
    }

    public void runProcesses() throws IncompleteProcessException {
        setProcessorAvailableStatus(false);
        if ((simVecProcessor != null) && !nmrDataSets.isEmpty()) {
            runSimVecProcessor(simVecProcessor, dimProcesses);
        }
        long startTime = System.currentTimeMillis();
        clearProcessorError();
        int nDimsProcessed = 0;
        for (ProcessOps p : dimProcesses) {
            p.firstProcess(!nvDataset);
            // check if this process corresponds to dimension that should be skipped
            if (mapToDataset(p.getDim()) == -1) {
                log.warn("Skip dim {}", (p.getDim() + 1));
                continue;
            }
            if (p.hasOperations()) {
                mathObjectsWritten.set(0);
                if (progressUpdater != null) {
                    int[] dims = p.getDims();
                    String dimString = "dim ";
                    if (p.isDataset()) {
                        dimString = "dataset";
                    } else {
                        dimString = String.valueOf(dims[0] + 1);
                        for (int iDim = 1; iDim < dims.length; iDim++) {
                            dimString = dimString + "," + String.valueOf(dims[iDim] + 1);
                        }
                    }
                    progressUpdater.updateStatus("Process " + dimString);
                }
                if (p.isMatrix()) {
                    setMatDims(p.getDims());
                } else if (p.isDataset()) {
                } else {
                    setDim(p.getDim());
                }
                run(p);
                nDimsProcessed = Math.max(nDimsProcessed, p.getDim() + 1);
                nvDataset = true;
            }
        }
        dimProcesses.clear();
        elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0;
        if (progressUpdater != null) {
            progressUpdater.updateProgress(1.0);
            progressUpdater.updateStatus("Done in " + String.format("%.1f", elapsedTime) + "s");
        }
        if (dataset.fFormat == DatasetBase.FFORMAT.UCSF) {
            dataset.writeHeader(false);
        }

        int freqDimsProcessed = 0;
        for (int i = 0; i < dataset.getNDim(); i++) {
            if (dataset.getFreqDomain(i)) {
                freqDimsProcessed++;
            }
        }
        dataset.setNFreqDims(freqDimsProcessed);
        for (int i = nDimsProcessed; i < dataset.getNDim(); i++) {
            dataset.setComplex(i, false);
        }
        if (getNMRData() != null) {
            dataset.sourceFID(new File(getNMRData().getFilePath()));
        }
        if (!keepDatasetOpen || !dataset.isMemoryFile()) {
            if (!dataset.isMemoryFile()) {
                dataset.writeParFile();
            }
            closeDataset(true);
        }
        String elapsedTimeStr = String.format("Elapsed time %.2f", elapsedTime);
        log.info(elapsedTimeStr);
        setProcessorAvailableStatus(true);
    }

    public void runSimVecProcessor(LineShapeCatalog simVecProcessor, ArrayList<ProcessOps> dimProcesses) {
        for (ProcessOps p : dimProcesses) {
            int iDim = p.getDim();
            if (p.hasOperations()) {
                if (!p.isMatrix() && !p.isDataset()) {
                    p.applyToSimVectors(simVecProcessor.getSimVectors(iDim));
                }
            }
        }
        simVecProcessor.saveSimFids();
    }

    public void clearDataset() {
        if (dataset != null) {
            dataset.close();
            dataset = null;
        }
    }

    public Dataset releaseDataset(String newName) {
        Dataset releasedDataset = dataset;
        if (newName != null) {
            releasedDataset.rename(newName);
        }
        dataset = null;
        return releasedDataset;
    }

    public void closeDataset(boolean saveDataset) {
        if (dataset != null) {
            if (dataset.isMemoryFile() && saveDataset) {
                try {
                    if (dataset.getFileName().endsWith(RS2DData.DATA_FILE_NAME) && (getNMRData() instanceof RS2DData)) {
                        RS2DData rs2DData = (RS2DData) getNMRData();
                        Path procNumPath = rs2DData.saveDataset(dataset);
                        EventBus.getDefault().post(new DatasetSavedEvent(RS2DData.DATASET_TYPE, procNumPath));
                    } else {
                        dataset.saveMemoryFile();
                    }
                } catch (IOException | DatasetException ex) {
                    log.error(ex.getMessage(), ex);
                }
            }
            dataset.close();
            dataset = null;
        }
    }

    public void run() {
        clearProcessorError();
        run(defaultProcess);
    }

    public double getElapsedTime() {
        return elapsedTime;
    }

    public boolean doneWriting() {
        return doneWriting.get();
    }

    /**
     * Allows a user to create a process and then run it explicitly. This allows
     * multiple processes to be created and then run sequentially.
     *
     * @param p
     */
    public void run(ProcessOps p) {
        if (processor.getProcessorError()) {
            setProcessorAvailableStatus(true);
            return;
        }
        synchronized (isRunning) {
            doneWriting.set(false);
            matrixMode.set(p.isMatrix());

            isRunning = true;

            setupPool(p);
            vecGroupsRead.set(0);
            vectorsRead.set(0);
            endOfFile.set(false);

            ArrayList<Future> completedProcesses = new ArrayList<>();
            if (useIOController && !p.isDataset()) {
                int queueLimit = p.isMatrix() ? 4 : 128;
                if (datasetWriter != null) {
                    datasetWriter.shutdown();
                }
                datasetWriter = new MatrixTypeService(this, queueLimit, itemsToRead, itemsToWrite);
            }

            for (Runnable process : processes) {
                completedProcesses.add(pool.submit(process));
            }

            for (Future future : completedProcesses) {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException ex) {
                    setProcessorAvailableStatus(true);
                    throw new ProcessingException(ex.getMessage());
                }
            }
            doneWriting.set(true);
            boolean doneFlushed = true;
            if (useIOController && !p.isDataset()) {
                doneFlushed = datasetWriter.isDone(10000);
                log.info("done flushed {}", doneFlushed);
            }
            if (!getProcessorError() && doneFlushed) {
                if (p.isMatrix()) {
                    log.info("Processed dimensions {}, {} with {} threads.", (dim[0] + 1), (dim[1] + 1), numProcessors);
                } else {
                    log.info("Processed dimension {} with {} threads.", (dim[0] + 1), numProcessors);
                }
                for (int i = 0; i < dataset.getNDim(); ++i) {
                    dataset.syncPars(i);
                }
                int iDim = p.getDim();
                if (!p.isMatrix() && !p.isUndo()) {
                    int jDim = mapToDataset(iDim);
                    dataset.syncSize(jDim);
                }
                dataset.writeHeader();
            }
            printVecReadCount();
            pool.shutdown();
            if (useIOController && !p.isDataset()) {
                log.info("shutdown now");
                datasetWriter.shutdown();
                datasetWriter = null;
            }
            processes.clear();
            ProcessOps.resetNumProcessesCreated();
            p.getOperations().clear();
            isRunning = false;
            if (getProcessorError()) {
                setProcessorAvailableStatus(true);
                closeDataset(false);
                throw new ProcessingException(errorMessage.get());
            }
        }
    }

    public void setupPool(ProcessOps proc) {
        processes = new ArrayList<>();
        final ProcessOps poolProcess = proc;
        int iDim = proc.getDim();
        int useProcessors = numProcessors;
        if (iDim == 0) {
            if (totalVecGroups < useProcessors) {
                useProcessors = totalVecGroups;
            }
        }
        if (proc.isDataset()) {
            useProcessors = 1;
        }
        for (int i = 0; i < useProcessors; ++i) {
            processes.add(new Runnable() {
                final ProcessOps p = poolProcess.cloneProcess(createProcess());

                public void run() {
                    try {
                        p.call();
                    } catch (ProcessingException e) {
                        setProcessorAvailableStatus(true);
                        log.warn(e.getMessage(), e);
                        if (!pool.isShutdown()) {
                            pool.shutdown();
                        }
                    }
                }
            });
        }
        pool = Executors.newFixedThreadPool(useProcessors);
    }

    public ArrayList<Runnable> getProcesses() {
        return processes;
    }

    public boolean getEndOfFile() {
        return endOfFile.get();
    }

    public void setEndOfFile() {
        endOfFile.set(true);
    }

    /**
     * The size of the vectors in the file.
     *
     * @return
     */
    public int getVectorSize() {
        return vectorSize;
    }

    public final int[] getDim() {
        return dim;
    }

    public String getFileName() {
        return fileName;
    }

    public Dataset getDataset() {
        return dataset;
    }

    public ExecutorService getPool() {
        return pool;
    }

    public NMRData getNMRData() {
        return nmrDataSets.size() > 0 ? nmrDataSets.get(0) : null;
    }

    public boolean isNVDataset() {
        return nvDataset;
    }

    public int getTotalVecGroups() {
        return totalVecGroups;
    }

    public int getVecGroupsRead() {
        return vecGroupsRead.get();
    }

    public int incrementVecGroupsRead() {
        return vecGroupsRead.getAndIncrement();
    }

    public int getTotalMatrices() {
        return totalMatrices.get();
    }

    public int incrementMatricesRead() {
        return matricesRead.getAndIncrement();
    }

    public void setNumProcessors(int n) {
        numProcessors = n;
        if (numProcessors < 1) {
            numProcessors = 1;
        }
    }

    public int getNumProcessors() {
        return numProcessors;
    }

    public void setVectorsPerProcess(int n) {
        vectorsPerProcess = n;
    }

    public int getVectorsPerProcess() {
        return vectorsPerProcess;
    }

    public boolean getNvComplex() {
        return nvComplex;
    }

    public synchronized boolean getResizeFlag() {
        return resizeFlag;
    }

    public synchronized void setResizeFlag() {
        resizeFlag = true;
    }

    public void runLock() {
        synchronized (isRunning) {
            ;
        }
    }

    public void clearProcessorError() {
        processorError.set(false);
        setProcessorErrorMessage("");
    }

    public void setProcessorErrorMessage(String message) {
        errorMessage.set(message);
    }

    public boolean setProcessorError() {
        return processorError.getAndSet(true);
    }

    public boolean getProcessorError() {
        return processorError.get();
    }

    public void keepDatasetOpen(boolean state) {
        keepDatasetOpen = state;
    }

    public boolean isDatasetOpen() {
        return dataset != null;
    }

    public void setProcessorAvailableStatus(boolean status) {
        processorAvailable.set(status);
        updateProcessorAvailableStatus(status);
    }

    public void updateProcessorAvailableStatus(boolean newStatus) {
        listeners.forEach((listener -> listener.processorAvailableStatusUpdated(newStatus)));
    }

    public void addProcessorAvailableStatusListener(ProcessorAvailableStatusListener listener) {
        listeners.add(listener);
    }

    public void removeProcessorAvailableStatusListener(ProcessorAvailableStatusListener listener) {
        listeners.remove(listener);
    }

    public boolean isProcessorAvailable() {
        return processorAvailable.get();
    }

    public static void setTestCorruptionMode(boolean state) {
        testCorruptionMode = state;
    }
}
