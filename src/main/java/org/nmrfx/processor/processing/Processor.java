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

import java.io.File;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetException;
import org.nmrfx.processor.processing.processes.ProcessOps;
import org.nmrfx.processor.datasets.ScanRegion;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.datasets.vendor.BrukerData;
import org.nmrfx.processor.math.Matrix;
import org.nmrfx.processor.math.MatrixND;
import org.nmrfx.processor.math.MatrixType;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.operations.Invertible;
import org.nmrfx.processor.operations.Operation;
import org.nmrfx.processor.processing.processes.IncompleteProcessException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Processor contains all processes. It also contains the "current
 * ProcessOps", which is the process which operations will be added to if a
 * process is not specified.
 *
 * @author johnsonb
 */
public class Processor {

    public static boolean showDebugInfo = false;
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
    private int vecGroupsRead = 0;
    /**
     * The number of vectors or matrices which have been written
     */
    private int mathObjectsWritten = 0;
    /**
     * The number of vectors which need to be written
     */
    private int vectorsToWrite = 0;
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
    private int totalMatrices = 0;
    /**
     * The number of matrices that have been read.
     */
    private int matricesRead = 0;
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
    private boolean endOfFile;
    /**
     * If True then processes will stop querying for unprocessed vectors to
     * process.
     */
    public static boolean stopProcessing = false;
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
     * Each LinkedList<Vec> will hold one set of arraylists for a process. The
     * outer List is synchronized but the inner List is not synchronized.
     */
    private LinkedBlockingQueue<ArrayList<Vec>> unprocessedVectorQueue;
    /**
     * Each LinkedList<Vec> will be written to a file.
     */
    private LinkedBlockingQueue<ArrayList<Vec>> processedVectorQueue;

    /**
     * This flag is to test IOController / non-IOController IO for debugging.
     */
    public boolean useIOController = false;

    private boolean processorError = false;
    private String errorMessage = "";

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
     * The sizes of dimensions in dataset.
     */
    public int[] datasetSizes = null;
    /**
     * The sizes of dataset to actually populate.
     */
    public int[] useSizes = null;
    /**
     * The sizes of time domain data.
     */
    public int[] tdSizes = null;
    /**
     * The complex nature of time domain data.
     */
    boolean[] complex = null;
    int[] newTDSizes;
    boolean[] newComplex;
    String[] newAcqOrder;

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

    private static int vecReadCount = 0;

    private MultiVecCounter tmult;

    private static ProgressUpdater progressUpdater;
    boolean modeND = true;
    private double elapsedTime = 0.0;

    private void addVecReadCount() {
        vecReadCount++;
    }

    private void resetVecReadCount() {
        vecReadCount = 0;
    }

    private void printVecReadCount() {
        if (dim[0] < 1) {
            System.err.println("read FID vector count: " + vecReadCount);
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
        if (useIOController) {
            unprocessedVectorQueue = new LinkedBlockingQueue<>();
            processedVectorQueue = new LinkedBlockingQueue<>();
        }
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
            System.err.println("Do not recognize filetype " + filetype);
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
     * @param pt Points to read from, or null to read whole file
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
                dataset = new Dataset(fileName, fileName, writeable);
            } catch (IOException ex) {
                System.err.println("could not create dataset");
                ex.printStackTrace();
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

    public void adjustSizes() {
        NMRData nmrData = nmrDataSets.get(0);
        if (acqOrder == null) {
            acqOrder = nmrData.getAcqOrder();
        }
        int nDim = nmrData.getNDim();
        int nArray = 0;
        for (int i = 1; i < nDim; i++) {
            int arraySize = nmrData.getArraySize(i);
            if (arraySize != 0) {
                nArray++;
            }
        }

        if (nArray > 0) {
            newTDSizes = new int[nDim + nArray];
            newComplex = new boolean[nDim + nArray];
            newTDSizes[0] = tdSizes[0];
            newComplex[0] = complex[0];
            int j = 1;
            for (int i = 1; i < nDim; i++) {
                int arraySize = nmrData.getArraySize(i);
                if (arraySize != 0) {
                    newTDSizes[j] = tdSizes[i];
                    if (nmrData instanceof BrukerData) {
                        newTDSizes[j] /= arraySize;
                    }
                    newTDSizes[j + 1] = arraySize;
                    newComplex[j] = complex[i];
                    newComplex[j + 1] = false;
                    j += 2;
                } else {
                    newTDSizes[j++] = tdSizes[i];
                    newComplex[j - 1] = complex[i];
                }
            }
            newAcqOrder = acqOrder;
            useSizes = newTDSizes;
        } else {
            newTDSizes = tdSizes;
            newComplex = complex;
            newAcqOrder = acqOrder;
        }
        if (showDebugInfo) {
            for (int i = 0; i < (nDim + nArray); i++) {
                System.out.println("new td " + i + " " + newTDSizes[i] + " " + newComplex[i] + " ");
            }
            for (int i = 0; i < newAcqOrder.length; i++) {
                System.out.print(newAcqOrder[i] + " ");
            }
            System.out.println("");
        }

        tdSizes = new int[newTDSizes.length];
        useSizes = new int[newTDSizes.length];
        System.arraycopy(newTDSizes, 0, tdSizes, 0, tdSizes.length);
        System.arraycopy(newTDSizes, 0, useSizes, 0, tdSizes.length);
    }

    public int[] getNewSizes() {
        NMRData nmrData = nmrDataSets.get(0);
        int nDim = nmrData.getNDim();
        int[] newSize = new int[nDim];
        System.arraycopy(newTDSizes, 0, newSize, 0, newSize.length);
        return newSize;
    }

    void setupDirectDim() {
        int vectorsMultiDataMin = 1;
        NMRData nmrData = nmrDataSets.get(0);
        if (acqOrder == null) {
            acqOrder = nmrData.getAcqOrder();
        }
        if (nDim > 1) {
            if (useSizes == null) {
                tmult = new MultiVecCounter(newTDSizes, newComplex, newAcqOrder, dataset.getNDim());
            } else if (newTDSizes == null) {
                adjustSizes();
                System.out.println("use newTDSize and useSizes " + useSizes.length + " new acqorder " + newAcqOrder.length);
// fixme
                tmult = new MultiVecCounter(newTDSizes, useSizes, newComplex, newAcqOrder, dataset.getNDim());
            } else if (useSizes.length <= newTDSizes.length) {
                System.out.println("use newTDSize and useSizes " + useSizes.length);
// fixme
                tmult = new MultiVecCounter(newTDSizes, useSizes, newComplex, newAcqOrder, dataset.getNDim());
            } else {
                // String[] acqOrder = {"d2", "p1", "d1", "p2"};
                //String[] acqOrder = {"p2", "d2", "p1", "d1"};
                System.out.println("use2 " + useSizes.length);
                tmult = new MultiVecCounter(newTDSizes, newComplex, newAcqOrder, useSizes.length);
            }
            if (isNUS()) {
                sampleSchedule = nmrData.getSampleSchedule();
                sampleSchedule.setOutMult(complex, newAcqOrder);
            }
            vectorsToWrite = nmrData.getNVectors();
            if (useSizes != null) {
                vectorsToWrite = 1;
                for (int i = 1; i < useSizes.length; i++) {
                    System.out.println("use " + i + " " + useSizes[i] + " " + newComplex.length);
                    if ((i < newComplex.length) && newComplex[i]) {
                        vectorsToWrite *= useSizes[i] * 2;
                    } else {
                        vectorsToWrite *= useSizes[i];
                    }
                }
            }
            totalVecGroups = vectorsToWrite / tmult.getGroupSize();
            vectorsMultiDataMin = nmrDataSets.size() * tmult.getGroupSize();
        } else {
            totalVecGroups = 1;
        }
        vectorsPerProcess = totalVecGroups / numProcessors;
        System.out.println("totalVecGroups " + totalVecGroups + " vectorsPerProcess " + vectorsPerProcess + " vecto " + vectorsToWrite);
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
     * file
     * @return True if the file is opened
     */
    public boolean setDim(int iDim) {
        return setDim(null, iDim);
    }

    /**
     * Set dataset dimension for already opened file.
     *
     * @param newPt Points to read from, or null to read whole file
     * @param iDim Dimensions corresponding to points, or null to read whole
     * file
     * @return True if the file is opened
     */
    public boolean setDim(int[][] newPt, int iDim) {
        if (processor.getProcessorError()) {
            System.err.println("proc error");
            return false;
        }
        // fixme remove + 1 when we convert nmrdata dim indexing to 0 start
        iDim = mapToDataset(iDim);
        if ((!nvDataset) && (iDim > 0)) {
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
        System.out.println("complex " + nvComplex + " vecSize " + this.vectorSize + " nVectors " + totalVecGroups);
        scanregion = new ScanRegion(this.pt, this.dim, dataset);

        vectorsPerProcess = totalVecGroups / numProcessors;
        if (vectorsPerProcess > maxVectorsPerProcess) {
            vectorsPerProcess = maxVectorsPerProcess;
        }

//        nvDataset = true;
        return true;
    }

    public boolean setMatDims(int[] dims) {
        if (processor.getProcessorError()) {
            System.err.println("proc error");
            return false;
        }
        if (!nvDataset) {
            throw new ProcessingException("First process using DIM(1)");
        }
        if (dataset.getNDim() < 3 || dims.length < 2) {
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
//        for (int i=0; i<dataset.getNDim(); i++) {
//            System.out.println(i + " vsize " + dataset.getVSize(dim[i]) +  " vsize_r " + dataset.getVSize_r(dim[i]) + " size " + dataset.getSize(dim[i]));
//        }
        totalMatrices = pt[pt.length - 1][1];  // pt[2][1];
/*
        System.out.print("matrix");
        for (int i=0;i< pt.length-1;i++) {
            int sz = 1 + pt[i][1];
            int sz2 = Vec.checkPowerOf2(sz);
            System.out.print(" ["+i + " " + sz + " " + sz2 + "]");
        }
        System.out.println(" nMats " + totalMatrices);
         */
        this.vectorSize = 1 + pt[0][1] - pt[0][0];

        if (dataset.getComplex_r(dim[0])) {
            this.vectorSize /= 2;
            nvComplex = true;
        } else {
            nvComplex = false;
        }

        matricesRead = 0;
        return true;
    }

    private int[][] calcPt(int[] dim) {        // see setDim()
        int[][] pt = new int[dataset.getNDim()][2];
        for (int i = 0; i < dataset.getNDim(); ++i) {  // read dims from dataset
            pt[i][0] = 0;
            if (dim[i] == 0) {
                pt[i][1] = dataset.getSize(dim[i]) - 1;
            } else {
                pt[i][1] = dataset.getVSize(dim[i]) - 1;
                // fixme should we use vsize this.pt[i][1] = dataset.getVSize_r(dim[i]) - 1;
            }
//            System.out.println(i + " vsize " + dataset.getVSize(dim[i]) +  " vsize_r " + dataset.getVSize_r(dim[i]) + " size " + dataset.getSize(dim[i]));
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
     * @param tdSizes - time domain sizes
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
            throw new ProcessingException("Cannot open dataset " + filename);
        }

        //nmrDataSets.clear();
        nmrDataSets.add(nmrData);
        System.err.println("nmr data sets " + nmrDataSets.size());
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
            throw new ProcessingException("Cannot open dataset \"" + filename + "\" because: " + ex.getMessage());
        }
        // read parameters
        int nDim = nmrData.getNDim();
        int tdSizes[] = new int[nDim];
        for (int i = 0; i < nDim; ++i) {
            tdSizes[i] = nmrData.getSize(i);
        }

        //nmrDataSets.clear();
        nmrDataSets.add(nmrData);
        System.err.println("nmr data sets " + nmrDataSets.size());
        resetVecReadCount();
        setFidDimensions(nmrData, tdSizes);
        return nmrData;
    }

    public void setSizes(int[] tdSizes) {
        for (NMRData nmrData : nmrDataSets) {
            setFidDimensions(nmrData, tdSizes);
        }
    }

    private void setFidDimensions(NMRData nmrData, int tdSizes[]) {
        nDim = tdSizes.length;
        dim = new int[nDim];
        for (int i = 0; i < nDim; ++i) {
            dim[i] = i;
        }
        nvDataset = false;  // openfid() must first process FID vectors

        vectorSize = nmrData.getNPoints(); //complex
        this.tdSizes = tdSizes;

        // pt not needed here, not used
        pt = new int[nDim][2];
        complex = new boolean[nDim];
        int[] mSizes = new int[nDim];
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

    private int mapToDataset(int i) {
        return mapToDataset[i];
    }

    private int mapToFID(int i) {
        return mapToFID[i];
    }

    public boolean createNV(String outputFile, int datasetSizes[], int[] useSizes, Map flags) {
        createNV(outputFile, datasetSizes, useSizes);
        setFidFlags(flags);
        return true;
    }

    public boolean createNV(String outputFile, int datasetSizes[], int[] useSizes) {
        if (progressUpdater != null) {
            progressUpdater.updateStatus("Create output dataset");
        }
        this.datasetSizes = datasetSizes;
        this.useSizes = useSizes;  // fixme
        if (nDim > datasetSizes.length) {
            if (useSizes == null) {
                Logger.getLogger(Processor.class.getName()).log(Level.SEVERE, null, "specify useSizes if not using all dimensions");
                return false;
            }
        }

        try {
            Dataset.createDataset(outputFile, outputFile, datasetSizes);
        } catch (DatasetException ex) {
            ex.printStackTrace();
            Logger.getLogger(Processor.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }

        try {
            this.dataset = new Dataset(outputFile, outputFile, true);
        } catch (IOException ex) {
            ex.printStackTrace();
            Logger.getLogger(Processor.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        dataset.setScale(1.0);

        if (!nmrDataSets.isEmpty()) {
            NMRData nmrData = nmrDataSets.get(0);
            mapToFID = new int[dataset.getNDim()];
            mapToDataset = new int[nmrData.getNDim()];
            int j = 0;
            for (int i = 0; i < mapToDataset.length; i++) {
                mapToDataset[i] = -1;
                if (useSizes[i] > 1) {
                    mapToDataset[i] = j;
                    mapToFID[j] = i;
                    j++;
                }
            }
            for (int i = 0; i < dataset.getNDim(); i++) {
                dataset.setLabel(i, nmrData.getTN(mapToFID(i)));
                dataset.setSf(i, nmrData.getSF(mapToFID(i)));
                dataset.setSw(i, nmrData.getSW(mapToFID(i)));
                dataset.setRefValue(i, nmrData.getRef(mapToFID(i)));
                dataset.setRefPt(i, nmrData.getRefPoint(mapToFID(i)));
                dataset.setTDSize(i, useSizes[mapToFID(i)]);
                //dataset.setPh0(i, nmrData.getPH0(mapToFID(i)));
                //dataset.setPh1(i, nmrData.getPH1(mapToFID(i)));
                dataset.setValues(i, nmrData.getValues(i));
                dataset.setComplex(i, nmrData.isComplex(mapToFID(i)));
            }
            dataset.setSolvent(nmrData.getSolvent());
            dataset.setTempK(nmrData.getTempK());
        }
        dataset.writeHeader();
        return true;
    }

    public boolean createNVInMemory(String outputFile, int datasetSizes[], int[] useSizes) {
        this.datasetSizes = datasetSizes;
        this.useSizes = useSizes;
        if (nDim > datasetSizes.length) {
            if (useSizes == null) {
                Logger.getLogger(Processor.class.getName()).log(Level.SEVERE, null, "specify useSizes if not using all dimensions");
                return false;
            }
        }

        try {
            this.dataset = new Dataset(outputFile, datasetSizes);
        } catch (DatasetException ex) {
            ex.printStackTrace();
            Logger.getLogger(Processor.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }

        dataset.setScale(1.0);

        if (!nmrDataSets.isEmpty()) {
            NMRData nmrData = nmrDataSets.get(0);
            mapToFID = new int[dataset.getNDim()];
            mapToDataset = new int[nmrData.getNDim()];
            int j = 0;
            for (int i = 0; i < mapToDataset.length; i++) {
                mapToDataset[i] = -1;
                if (useSizes[i] > 1) {
                    mapToDataset[i] = j;
                    mapToFID[j] = i;
                    j++;
                }
            }
            for (int i = 0; i < dataset.getNDim(); i++) {
                dataset.setLabel(i, nmrData.getTN(mapToFID(i)));
                dataset.setSf(i, nmrData.getSF(mapToFID(i)));
                dataset.setSw(i, nmrData.getSW(mapToFID(i)));
                dataset.setRefValue(i, nmrData.getRef(mapToFID(i)));
                dataset.setRefPt(i, nmrData.getRefPoint(mapToFID(i)));
                dataset.setTDSize(i, useSizes[mapToFID(i)]);
                //dataset.setPh0(i, nmrData.getPH0(mapToFID(i)));
                //dataset.setPh1(i, nmrData.getPH1(mapToFID(i)));
            }
            dataset.setSolvent(nmrData.getSolvent());
        }
        dataset.writeHeader();
        return true;
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
        System.out.print("  matPrint " + msg + " dim=[");
        for (int i = 0; i < dim.length; i++) {
            System.out.print(" " + dim[i]);
        }
        System.out.print(" ] ");
//        if ((nDim==3 && pt[1][0]<2 && pt[2][0]<2)) {
        System.out.print(" pt=[");
        for (int j = 0; j < pt.length; j++) {
            for (int k = 0; k < pt[j].length; k++) {
                System.out.print(" " + pt[j][k]);
            }
            System.out.print(";");
        }
        System.out.println(" ]");
//        }
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
//        try {
//            if (matrix != null) {
//                fileName = fileName + matrix.getIndex() + ".txt";
//                matrix.dump(fileName);
//            }
//        } catch (IOException ioE) {
//
//        }
        return matrix;
    }

    public synchronized Matrix getMatrix2DFromFile() {
        Matrix matrix = null;
        if (!nvDataset) {
            throw new ProcessingException("Cannot get matrix, not indirect dimension.");
        }
        if (endOfFile) {
            return matrix;
        }
        int matrixCount = incrementMatricesRead();  // increment matrix count
        if (matrixCount > getTotalMatrices()) {
            setEndOfFile();  // matrix is null
        } else {
            // zerofill matrix size for processing and writing
            int nPlanes = Vec.checkPowerOf2(1 + pt[1][1]);
            int nRows = Vec.checkPowerOf2(1 + pt[0][1]);
            int[][] writePt = calcPt(dim);
            writePt[1][1] = nPlanes - 1;
            writePt[0][1] = nRows - 1;
            writePt[2][0] = matrixCount;
            pt[2][0] = matrixCount;
            try {
                matrix = new Matrix(nPlanes, nRows, writePt);
                dataset.readMatrix(dataset, pt, dim, matrix.getMatrix());
            } catch (IOException ex) {
                ex.printStackTrace();
//                Logger.getLogger(Processor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return matrix;
    }

    public synchronized MatrixND getMatrixNDFromFile() {
        MatrixND matrix = null;
        if (!nvDataset) {
            throw new ProcessingException("Cannot get matrix, not indirect dimension.");
        }
        if (endOfFile) {
            return matrix;
        }
        int matrixCount = incrementMatricesRead();  // increment matrix count
        if (matrixCount > getTotalMatrices()) {
            setEndOfFile();  // matrix is null
        } else {
            // zerofill matrix size for processing and writing
            int[][] writePt = calcPt(dim);
            int[] matrixSizes = new int[pt.length - 1];
            // size in points of valid data (used for apodizatin etc.)
            int[] vSizes = new int[pt.length - 1];
            for (int i = 0; i < pt.length - 1; i++) {
                matrixSizes[i] = Vec.checkPowerOf2(1 + pt[i][1]);
                vSizes[i] = (pt[i][1] + 1);
                if (dataset.getComplex(dim[0])) {
                    vSizes[i] /= 2;
                }
            }
            for (int i = 0; i < pt.length - 1; i++) {
                writePt[i][1] = matrixSizes[i] - 1;
            }
            writePt[pt.length - 1][0] = matrixCount;
            pt[pt.length - 1][0] = matrixCount;
            try {
                matrix = new MatrixND(writePt, matrixSizes);
                matrix.setVSizes(vSizes);
//                printDimPt("getMatrix", dim, matrix.getPt());  // for debug
                dataset.readMatrixND(pt, dim, matrix);
            } catch (IOException ex) {
                ex.printStackTrace();
//                Logger.getLogger(Processor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return matrix;
    }

    /**
     * Gets the next 'vectorsPerProcess' Vecs from the data file and returns
     * them to the calling ProcessOps.
     *
     * @return ArrayList of Vecs from the dataset.
     */
    public synchronized ArrayList<Vec> getVectorsFromFile() {
        Vec temp;
        ArrayList<Vec> vectors = new ArrayList<>();

// pt[][] : coordinates in data matrix
// pt[0][0] to pt[0][1] is start/end coords for vec column
// pt[1][0] to pt[1][1] is start/end coords for orthogonal row
        if (nvDataset) {  // indirect dimensions
            int[][] pt;
            if (endOfFile) {
                return vectors;
            }
            for (int i = 0; i < vectorsPerProcess; ++i) {
                pt = scanregion.nextPoint2();
                if (pt.length == 0) {
                    endOfFile = true;
                    break;
                }
                try {
                    temp = new Vec(vectorSize, pt, dim, nvComplex);
                    dataset.readVectorFromDatasetFile(pt, dim, temp);
                    if (temp.checkExtreme(1.0e16)) {
                        System.err.println("extreme read");
                    }
                    vectors.add(temp);
                } catch (IOException ex) {
                    throw new ProcessingException(ex.getMessage());
                } catch (Exception e) {
                    throw new ProcessingException(e.getMessage());
                }
                vectorsToWrite = totalVecGroups;
            }
        } else {  // direct dimension, read FIDs
            if (tmult == null) {
                //setupDirectDim();
            }
            int vectorsPerGroup = 1;
            if (tmult != null) {
                vectorsPerGroup = tmult.getGroupSize();
            }
            int nSteps = vectorsPerProcess / vectorsPerGroup;
            for (int iStep = 0; iStep < nSteps;) {
                int vecGroup = incrementVecGroupsRead();
                if (vecGroup > getTotalVecGroups() - 1) {
                    setEndOfFile();
                    break;
                }
                VecIndex vecIndex = getNextGroup(vecGroup);
                if (vecIndex != null) {
                    iStep++;
                    if (showDebugInfo) {
                        vecIndex.printMe(vecGroup, 1);
                    }
                    for (int j = 0; j < vectorsPerGroup; j++) {
                        try {
                            for (NMRData nmrData : nmrDataSets) {
                                temp = new Vec(vectorSize, nmrData.isComplex(dim[0]));
                                nmrData.readVector(vecIndex.inVecs[j], temp);
                                temp.setPt(vecIndex.outVecs[j], dim);
                                vectors.add(temp);
                            }
                        } catch (Exception e) {
                            throw new ProcessingException(e.getMessage());
                        }
                        addVecReadCount();
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
            int[][][] outVecs = new int[1][1][2];
            outVecs[0][0][0] = vecNum;
            outVecs[0][0][1] = vecNum;
            return new VecIndex(inVecs, outVecs);
        }
    }

    /**
     * Adds vectors to the unprocessed vector queue.
     */
    public synchronized boolean addNewVectors() {
        ArrayList<Vec> vectors = getVectorsFromFile();
        if (!endOfFile) {
            unprocessedVectorQueue.add(vectors);
            return true;
        }
        return false;
    }

    public void addVectorsToWriteList(ArrayList<Vec> vectors) {
        processedVectorQueue.add(vectors);
    }

    public ArrayList<Vec> getVectorsFromWriteList() {
        try {
            return processedVectorQueue.take();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public void addVectorsToUnprocessedList(ArrayList<Vec> vectors) {
        unprocessedVectorQueue.add(vectors);
    }

    public ArrayList<Vec> getVectorsFromUnprocessedList() {
        try {
            return unprocessedVectorQueue.take();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Writes all of the vectors from the processedVectorQueue to file.
     */
    public void writeVectors() {
        ArrayList<Vec> temp;
        if (processedVectorQueue.size() != 0) {
            while (true) {
                temp = processedVectorQueue.poll();
                for (Vec vector : temp) {
                    try {
                        dataset.writeVector(vector);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        Logger.getLogger(Processor.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                if (processedVectorQueue.isEmpty()) {
                    break;
                }
            }
        }
    }

    public void writeNVectors(int n) {
        if (processedVectorQueue.size() != 0) {
            for (int i = 0; i < n; ++i) {
                for (Vec vector : processedVectorQueue.poll()) {
                    try {
                        dataset.writeVector(vector);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        Logger.getLogger(Processor.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                if (processedVectorQueue.isEmpty()) {
                    break;
                }
            }
        }
    }

    public synchronized void writeVector(Vec vector) {
        mathObjectsWritten++;
        if (progressUpdater != null) {
            if ((mathObjectsWritten == vectorsToWrite) || ((mathObjectsWritten % 16) == 0)) {
                double f = (1.0 * mathObjectsWritten) / vectorsToWrite;
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
            dataset.writeVector(vector);
        } catch (IOException ex) {
            throw new ProcessingException(ex.getMessage());
        } catch (NullPointerException npe) {
            throw new ProcessingException("Dataset not open: can't write vector");
        }
    }

    public synchronized void writeMatrix(MatrixType matrix, String fileName) {

    }

    public synchronized void writeMatrix(MatrixType matrix) {
        if (matrix instanceof Matrix) {
            writeMatrix((Matrix) matrix);
        } else {
            writeMatrixND((MatrixND) matrix);

        }

    }

    public synchronized void writeMatrix(Matrix matrix) {  // called by WRITE op
        mathObjectsWritten++;
        if (progressUpdater != null) {
            if ((mathObjectsWritten == totalMatrices) || ((mathObjectsWritten % 16) == 0)) {
                double f = (1.0 * matricesRead) / totalMatrices;
                progressUpdater.updateProgress(f);
            }
        }
        try {
            if (matrix != null) {
//                printDimPt(dim, matrix.getPt());  // for debug
                dataset.writeMatrixToDatasetFile(dim, matrix);
            }
        } catch (IOException ex) {
            throw new ProcessingException(ex.getMessage());
        } catch (NullPointerException npe) {
            throw new ProcessingException("Dataset not open: can't write matrix");
        }
    }

    public synchronized void writeMatrixND(MatrixND matrix) {  // called by WRITE op
        mathObjectsWritten++;
        if (progressUpdater != null) {
            if ((mathObjectsWritten == totalMatrices) || ((mathObjectsWritten % 16) == 0)) {
                double f = (1.0 * matricesRead) / totalMatrices;
                progressUpdater.updateProgress(f);
            }
        }
        try {
            if (matrix != null) {
//                printDimPt("write matrix", dim, matrix.getPt());  // for debug
                dataset.writeMatrixNDToDatasetFile(dim, matrix);
            }
        } catch (IOException ex) {
            throw new ProcessingException(ex.getMessage());
        } catch (NullPointerException npe) {
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
        long startTime = System.currentTimeMillis();
        clearProcessorError();
        int nDimsProcessed = 0;
        for (ProcessOps p : dimProcesses) {
            // check if this process corresponds to dimension that should be skipped
            if (mapToDataset(p.getDim()) == -1) {
                System.err.println("Skip dim " + (p.getDim() + 1));
                continue;
            }
            if (p.hasOperations()) {
                mathObjectsWritten = 0;
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
        if (!keepDatasetOpen) {
            if (dataset.fFormat == Dataset.FFORMAT.UCSF) {
                dataset.writeHeaderUCSF(false);
            }
            dataset.setNFreqDims(nDimsProcessed);
            for (int i = nDimsProcessed; i < dataset.getNDim(); i++) {
                dataset.setFreqDomain(i, false);
                dataset.setComplex(i, false);
            }
            dataset.writeParFile();
            dataset.close();
            dataset = null;
        }
        System.err.printf("Elapsed time %.2f\n", elapsedTime);
    }

    public void closeDataset() {
        if (dataset != null) {
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

    /**
     * Allows a user to create a process and then run it explicitly. This allows
     * multiple processes to be created and then run sequentially.
     *
     * @param p
     */
    public void run(ProcessOps p) {
        if (processor.getProcessorError()) {
            return;
        }
        synchronized (isRunning) {
            isRunning = true;

//            System.out.println("Num Processes: " + numProcessors);
//            defaultProcess = p;
            setupPool(p);
            vecGroupsRead = 0;
            endOfFile = false;

            ArrayList<Future> completedProcesses = new ArrayList<>();

            for (Runnable process : processes) {
                completedProcesses.add(pool.submit(process));
            }

            for (Future future : completedProcesses) {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException ex) {
                    ex.printStackTrace();
                    throw new ProcessingException(ex.getMessage());
                }
            }
            if (!getProcessorError()) {
                if (p.isMatrix()) {
                    System.err.println("Processed dimensions " + (dim[0] + 1) + "," + (dim[1] + 1)
                            + " with " + numProcessors + " threads.");
                } else {
                    System.err.println("Processed dimension " + (dim[0] + 1) + " with "
                            + numProcessors + " threads.");
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
            processes.clear();
            ProcessOps.resetNumProcessesCreated();
            p.getOperations().clear();
            isRunning = false;
            if (getProcessorError()) {
                throw new ProcessingException(errorMessage);
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
                        System.err.println(e.getMessage());
                        if (!pool.isShutdown()) {
                            pool.shutdown();
                        }
                    }
                    //System.out.println(p.getCompletionMessage());
                }
            });
        }
        pool = Executors.newFixedThreadPool(useProcessors);
    }

    public ArrayList<Runnable> getProcesses() {
        return processes;
    }

    public synchronized boolean getEndOfFile() {
        return endOfFile;
    }

    public int getUnprocessedVectorQueueSize() {
        return unprocessedVectorQueue.size();
    }

    public int getProcessedVectorQueueSize() {
        return processedVectorQueue.size();
    }

    public synchronized void setEndOfFile() {
        endOfFile = true;
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
        return nmrDataSets.get(0);
    }

    public boolean isNVDataset() {
        return nvDataset;
    }

    public synchronized int getTotalVecGroups() {
        return totalVecGroups;
    }

    public synchronized int getVecGroupsRead() {
        return vecGroupsRead;
    }

    public synchronized int incrementVecGroupsRead() {
        int temp = vecGroupsRead;
        vecGroupsRead++;
        return temp;
    }

    public synchronized int getTotalMatrices() {
        return totalMatrices;
    }

    public synchronized int incrementMatricesRead() {
        int temp = matricesRead;
        matricesRead++;
        return temp;
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

    public synchronized void clearProcessorError() {
        processorError = false;
        errorMessage = "";
    }

    public synchronized void setProcessorErrorMessage(String message) {
        errorMessage = message;
    }

    public synchronized boolean setProcessorError() {
        boolean temp = processorError;
        processorError = true;
        return temp;
    }

    public synchronized boolean getProcessorError() {
        return processorError;
    }

    public void keepDatasetOpen(boolean state) {
        keepDatasetOpen = state;
    }

    public boolean isDatasetOpen() {
        return dataset != null;
    }
}
