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
package org.nmrfx.processor.gui;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nmrfx.analyst.gui.python.AnalystPythonInterpreter;
import org.nmrfx.processor.datasets.AcquisitionType;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetType;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.datasets.vendor.bruker.BrukerData;
import org.nmrfx.processor.datasets.vendor.nmrpipe.NMRPipeData;
import org.nmrfx.processor.datasets.vendor.nmrview.NMRViewData;
import org.nmrfx.processor.datasets.vendor.rs2d.RS2DProcUtil;
import org.nmrfx.processor.datasets.vendor.varian.VarianData;
import org.nmrfx.processor.gui.utils.FileNameDialog;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.*;
import org.nmrfx.processor.processing.processes.IncompleteProcessException;
import org.nmrfx.processor.processing.processes.ProcessOps;
import org.nmrfx.utils.GUIUtils;
import org.python.core.PyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A ChartProcessor manages the processing of data assigned to a particular
 * PolyChart
 *
 * @author brucejohnson
 */
public class ChartProcessor {
    private static final Logger log = LoggerFactory.getLogger(ChartProcessor.class);

    public static final DatasetType DEFAULT_DATASET_TYPE = DatasetType.NMRFX;

    private final ProcessorController processorController;
    private final SimpleBooleanProperty areOperationListsValid = new SimpleBooleanProperty(false);
    /**
     * List of commands to be executed at beginning of script.
     */
    private final List<String> headerList = new ArrayList<>();
    /**
     * List of Vec objects that contain data used in interactive processing. The
     * number of Vec objects correspond to the number of vectors in the raw data
     * that have the same indirect acquisition times
     */
    private final List<Vec> vectors = new ArrayList<>();
    /**
     * List of Vec objects that save copy of data used in interactive
     * processing. Used to restore vectors when an error happens during
     * processing
     */
    private final List<Vec> saveVectors = new ArrayList<>();
    private final List<?> pyDocs;
    private File datasetFile;
    /**
     * Display chart used for rendering vectors.
     */
    private PolyChart chart;
    private FXMLController fxmlController;
    /**
     * Map of lists of operations with key being the dimension the operations
     * apply to
     */
    private final Map<ProcessingSection, List<ProcessingOperationInterface>> backingMap = new LinkedHashMap<>();
    protected ObservableMap<ProcessingSection, List<ProcessingOperationInterface>> mapOpLists = FXCollections.observableMap(backingMap);
    /**
     * Which Vec of the list of vectors should currently be displayed
     */
    private int iVec = 0;
    /**
     * Array of strings representing the acquisition modes (like hypercomplex or
     * echo-antiecho) that were used in acquiring each indirect dimension.
     * Currently, only used in reading vectors from raw data file for
     * interactive processing.
     */
    private AcquisitionType[] acqMode = null;
    /**
     * Should Bruker FIDs be corrected when loading them for the DSP artifact at
     * beginning of FID.
     */
    private boolean fixDSP = true;

    private Double zeroFreq = null;
    /**
     * How many vectors are present in data file for each unique combination of
     * indirect acquisition times. Typically, 2 for 2D, 4 for 3D etc.
     */
    private int vectorsPerGroup = 2;
    private boolean scriptValid = false;
    private int[] mapToDataset = null;
    private boolean lastWasFreqDomain = false;
    private SimpleObjectProperty<NMRData> nmrDataObj;
    /**
     * The dimension of datasetFile that is in use for interactive processing.
     */
    private int vecDim = 0;
    /**
     * The name of the dimension of datasetFile that is in use for interactive
     * processing. Is a string because it could refer to multiple dimensions
     * (for IST matrix processing, for example).
     */
    private ProcessingSection currentProcessingSection = null;
    /**
     * The name of the datasetFile that will be created when whole data file is
     * processed.
     */
    private DatasetType datasetType = DEFAULT_DATASET_TYPE;
    /**
     * Used to determine mapping of position of FIDs in raw data file..
     */
    private MultiVecCounter multiVecCounter;

    private Map<ProcessingSection, ProcessingSection> processingSectionMap = new HashMap<>();

    public ChartProcessor(ProcessorController processorController) {
        this.processorController = processorController;
        this.pyDocs = AnalystPythonInterpreter.eval("getDocs()", ArrayList.class);
    }

    public ProcessingSection getProcessingSection(int order, int[] dimensions, String name) {
        ProcessingSection testSection = new ProcessingSection(order, dimensions, name);
        return processingSectionMap.computeIfAbsent(testSection, k -> k);
    }
    public SimpleObjectProperty<NMRData> nmrDataProperty() {
        if (nmrDataObj == null) {
            nmrDataObj = new SimpleObjectProperty<>(null);
        }
        return nmrDataObj;
    }

    public SimpleBooleanProperty areOperationListsValidProperty() {
        return areOperationListsValid;
    }

    public NMRData getNMRData() {
        return nmrDataProperty().get();
    }

    private ProcessOps getProcess() {
        return AnalystPythonInterpreter.eval("getCurrentProcess()", ProcessOps.class);
    }

    public PolyChart getChart() {
        return chart;
    }

    public void setChart(PolyChart chart) {
        this.chart = chart;
        chart.getFXMLController().setChartProcessor(this);
        if (chart.getDataset() == null) {
            initEmptyVecs();
            execScript("", false, false);
        }
    }

    public File getDatasetFile() {
        return datasetFile;
    }

    public void setDatasetFile(File datasetFile) {
        this.datasetFile = datasetFile;
    }

    public void setFxmlController(FXMLController fxmlController) {
        this.fxmlController = fxmlController;
    }

    public String getAcqOrder() {
        return getAcqOrder(false);
    }

    public boolean setAcqOrder(String acqOrder) {
        NMRData nmrData = getNMRData();
        boolean ok = false;
        if (nmrData != null) {
            String[] acqOrderArray = acqOrder.split(",");
            // fixme  should have a general acqOrder validator
            ok = true;

            int nDimChars = 0;
            for (int i = 0; i < acqOrderArray.length; i++) {
                acqOrderArray[i] = acqOrderArray[i].trim();
                if (acqOrderArray[i].isEmpty()) {
                    continue;
                }
                if (acqOrderArray[i].charAt(0) == '\'') {
                    int len = acqOrderArray[i].length();
                    acqOrderArray[i] = acqOrderArray[i].substring(1, len - 1);
                }
                // if format like 321 don't do the rest, otherwise format should be like p1,d2,...
                if (acqOrderArray.length == 1) {
                    // fixme, this done just so test at end passes
                    // length of array depends on varian versus Bruker
                    nDimChars = nmrData.getNDim() - 1;
                    break;
                }
                if (acqOrderArray[i].length() != 2) {
                    log.warn("wrong len {}", acqOrderArray[i]);
                    ok = false;
                    break;
                }
                char char0 = acqOrderArray[i].charAt(0);
                if ((char0 != 'p') && (char0 != 'd') && (char0 != 'a')) {
                    log.warn("acq order character not a p,d or a: {}", char0);
                    ok = false;
                    break;
                }
                if (char0 == 'd') {
                    nDimChars++;
                }
                char char1 = acqOrderArray[i].charAt(1);
                if (!Character.isDigit(char1)) {
                    log.warn("not digit {}", char1);
                    ok = false;
                    break;
                }

            }
            if (nDimChars != (nmrData.getNDim() - 1)) {
                log.warn("{} {}", nDimChars, (nmrData.getNDim() - 1));
                ok = false;
            }
            if (ok) {
                nmrData.setAcqOrder(acqOrderArray);
                updateCounter();
            }
        }
        return ok;
    }

    public String getAcqOrder(boolean useQuotes) {
        String acqOrder = "";
        NMRData nmrData = getNMRData();

        if (nmrData != null) {
            acqOrder = nmrData.getAcqOrderShort();
            if (!acqOrder.isEmpty()) {
                if (useQuotes) {
                    acqOrder = "'" + acqOrder + "'";
                }
            } else {
                String[] acqOrderArray = nmrData.getAcqOrder();
                if (acqOrderArray != null) {
                    acqOrder = buildAcqOrder(useQuotes, acqOrderArray);
                }
            }
        }
        return acqOrder;
    }

    private static String buildAcqOrder(boolean useQuotes, String[] acqOrderArray) {
        StringBuilder sBuilder = new StringBuilder();
        for (int i = 0; i < acqOrderArray.length; i++) {
            if (i != 0) {
                sBuilder.append(',');
            }
            if (useQuotes) {
                sBuilder.append("'");
            }
            sBuilder.append(acqOrderArray[i]);
            if (useQuotes) {
                sBuilder.append("'");
            }
        }
        return sBuilder.toString();
    }

    public String getArraySizes() {
        String arraySizes = "";
        NMRData nmrData = getNMRData();

        if (nmrData != null) {
            int nDim = nmrData.getNDim();
            StringBuilder sBuilder = new StringBuilder();
            for (int i = 0; i < nDim; i++) {
                if (i != 0) {
                    sBuilder.append(',');
                }
                sBuilder.append(nmrData.getArraySize(i));
            }
            arraySizes = sBuilder.toString();

        }
        return arraySizes;
    }

    public void setArraySize(String arraySizes) {
        NMRData nmrData = getNMRData();

        if (nmrData != null) {
            String[] arraySizeArray = arraySizes.split(",");
            int iDim = -1;
            for (String sizeArg : arraySizeArray) {
                iDim++;
                if (sizeArg.isEmpty()) {
                    continue;
                }
                try {
                    int size = Integer.parseInt(sizeArg);
                    setArraySize(iDim, size);
                } catch (NumberFormatException nfE) {
                    log.warn(nfE.getMessage(), nfE);
                }
            }
        }
    }

    public void setArraySize(int dim, int arraySize) {
        NMRData nmrData = getNMRData();
        if (nmrData != null) {
            nmrData.setArraySize(dim, arraySize);
            updateCounter();
        }
    }

    public void setAcqMode(int dim, String acqMode) {
        NMRData nmrData = getNMRData();
        if (nmrData != null) {
            nmrData.setAcqMode(dim, acqMode);
            updateCounter();
        }
    }

    public boolean getFixDSP() {
        return fixDSP;
    }

    public void setFixDSP(boolean value) {
        fixDSP = value;
    }

    public Double getZeroFreq() {
        return zeroFreq;
    }

    public void setZeroFreq(Double value) {
        zeroFreq = value;
    }

    private VecIndex getNextIndex(NMRData nmrData, int[] rows) {
        int index = 0;
        if (rows.length > 0) {
            index = rows[0];
        }
        VecIndex vecIndex = null;
        if ((vecDim == 0) && (multiVecCounter != null)) {
            if (rows.length != 1) {
                index = multiVecCounter.findOutGroup(rows);
            }
            vecIndex = multiVecCounter.getNextGroup(index);
            if (nmrData.getSampleSchedule() != null) {
                vecIndex = nmrData.getSampleSchedule().convertToNUSGroup(vecIndex, index);
                if (vecIndex == null) {
                    log.info("No vec");
                }

            }
        }
        return vecIndex;
    }

    public List<VecIndexScore> scanForCorruption(double ratio, int maxN) {
        int iGroup = 0;
        NMRData nmrData = getNMRData();
        int nPoints = nmrData.getNPoints();
        Vec newVec = new Vec(nPoints, nmrData.isComplex(0));
        var stats = new DescriptiveStatistics();
        List<VecIndexScore> vecIndices = new ArrayList<>();
        while (true) {
            VecIndex vecIndex = multiVecCounter.getNextGroup(iGroup++);
            if (vecIndex == null) {
                break;
            }
            double groupMax = Double.NEGATIVE_INFINITY;
            int maxIndex = -1;
            for (int j = 0; j < vectorsPerGroup; j++) {
                newVec.resize(nPoints);
                nmrData.readVector(vecIndex.getInVec(j), newVec);
                double max = newVec.maxIndex().getValue();
                if (max > groupMax) {
                    groupMax = max;
                    maxIndex = j;
                }
            }
            var vecIndexScore = new VecIndexScore(vecIndex, maxIndex, groupMax);
            stats.addValue(groupMax);
            vecIndices.add(vecIndexScore);
        }

        double mean = stats.getMean();
        double sdev = stats.getStandardDeviation();
        double threshold = mean + ratio * sdev;
        vecIndices.sort(Collections.reverseOrder());

        List<VecIndexScore> result = new ArrayList<>();
        int n = Math.min(vecIndices.size(), maxN);
        for (int i = 0; i < n; i++) {
            var vecIndexScore = vecIndices.get(i);
            if (vecIndexScore.score() > threshold) {
                result.add(vecIndexScore);
            }
        }
        return result;
    }

    private void prepDirectVec(NMRData nmrData, VecIndex vecIndex, int[] fileIndices, Vec newVec, int j) {
        if (vecIndex == null) {
            fileIndices[j] = -1;
            nmrData.readVector(0, newVec);
            if (nmrData.getSampleSchedule() != null) {
                newVec.zeros();
            }
        } else {
            fileIndices[j] = vecIndex.getInVec(j);
            nmrData.readVector(vecIndex.getInVec(j), newVec);
        }
    }

    private void prepIndirectVec(NMRData nmrData, int[] rows, int[] fileIndices, Vec newVec, int j) {
        int index = 0;
        if (rows.length > 0) {
            index = rows[0];
        }
        fileIndices[j] = index + j;
        nmrData.readVector(vecDim, index + j, newVec);
        if (nmrData.getGroupSize(vecDim) > 1) {
            AcquisitionType type = acqMode[vecDim];
            if (type == null) {
                newVec.hcCombine();
            } else {
                newVec.eaCombine(type.getCoefficients());
            }
        }
    }

    private int[] loadVectors(int... rows) {
        NMRData nmrData = getNMRData();
        int nPoints = nmrData.getNPoints();
        if (vecDim != 0) {
            nPoints = nmrData.getSize(vecDim) * nmrData.getGroupSize(vecDim);
        }
        ProcessOps process = getProcess();
        process.clearVectors();
        vectors.clear();
        saveVectors.clear();
        VecIndex vecIndex = getNextIndex(nmrData, rows);
        int nVectors = vecDim == 0 ? vectorsPerGroup : 1;
        int[] fileIndices = new int[nVectors];
        for (int j = 0; j < nVectors; j++) {
            Vec newVec = new Vec(nPoints, nmrData.isComplex(vecDim));
            Vec saveVec = new Vec(nPoints, nmrData.isComplex(vecDim));
            if (vecDim == 0) {
                prepDirectVec(nmrData, vecIndex, fileIndices, newVec, j);
            } else {
                if ((nmrData instanceof NMRPipeData) && (vecDim > 1)) {
                     newVec.zeros();
                } else {
                    prepIndirectVec(nmrData, rows, fileIndices, newVec, j);
                }
            }
            newVec.setPh0(0.0);
            newVec.setPh1(0.0);

            newVec.copy(saveVec);
            int[][] pt = new int[1][2];
            int[] dim = new int[1];
            newVec.setPt(pt, dim);
            process.addVec(newVec);
            vectors.add(newVec);
            saveVectors.add(saveVec);
        }
        Vec vec = vectors.get(iVec);
        vec.setName("vec" + iVec);
        Dataset d = new Dataset(vec);
        d.setNucleus(0, nmrData.getTN(vecDim));
        chart.setDataset(d, false, true);
        return fileIndices;
    }

    public void setVector(int value) {
        iVec = value;
        if (iVec > vectors.size() - 1) {
            iVec = vectors.size() - 1;
        }
        if (iVec >= 0) {
            Vec vec = vectors.get(iVec);
            vec.setName("vec" + iVec);
            chart.setDataset(new Dataset(vec), false, true);
            chart.layoutPlotChildren();
        }
    }

    private void initEmptyVecs() {
        vectors.clear();
        saveVectors.clear();
        int nPoints = 2048;
        Vec newVec = new Vec(nPoints, true);
        Vec saveVec = new Vec(nPoints, true);
        saveVec.dwellTime = 1.0 / 1000.0;
        saveVec.centerFreq = 500.0;
        vectors.add(newVec);
        saveVectors.add(saveVec);
        Vec vec = vectors.get(iVec);
        vec.setName("vec" + iVec);
        chart.setDataset(new Dataset(vec));
    }

    public void vecRow(int[] rows) {
        if (getNMRData() != null) {
            int[] fileIndices = loadVectors(rows);
            processorController.navigatorGUI.setFileIndex(fileIndices);
            try {
                ProcessOps process = getProcess();
                process.exec();
            } catch (IncompleteProcessException ipe) {
                log.warn(ipe.getMessage(), ipe);
            }
            chart.layoutPlotChildren();
        }
    }

    public List<ProcessingOperationInterface> getOperations(ProcessingSection section) {
        return mapOpLists.get(section);
    }

    public void clearAllOperations() {
        if (mapOpLists != null) {
            mapOpLists.clear();
        }
    }

    public Map<ProcessingSection, List<ProcessingOperationInterface>> getScriptList() {
        Map<ProcessingSection, List<ProcessingOperationInterface>> copyOfMapOpLists = new LinkedHashMap<>();
        if (mapOpLists != null) {
            for (Map.Entry<ProcessingSection, List<ProcessingOperationInterface>> entry : mapOpLists.entrySet()) {
                List<ProcessingOperationInterface> newList = new ArrayList<>();
                if (entry.getValue() != null) {
                    newList.addAll(entry.getValue());
                }
                copyOfMapOpLists.put(entry.getKey(), newList);
            }
        }
        return copyOfMapOpLists;
    }

    public void setScripts(List<String> newHeaderList, Map<ProcessingSection, List<ProcessingOperationInterface>> opMap) {
        if (opMap == null || opMap.isEmpty()) {
            return;
        }
        processorController.removeOpListener();
        mapOpLists.clear();
        mapOpLists.putAll(opMap);
        headerList.clear();
        headerList.addAll(newHeaderList);
        processorController.refManager.setDataFields(headerList);
        vecDim = 0;
        if (!processorController.isViewingDataset()) {
            chart.full();
            chart.autoScale();
        }
        processorController.addOpListener();
    }

    public boolean isScriptValid() {
        return scriptValid;
    }

    public void setScriptValid(boolean state) {
        scriptValid = state;
        if (!state) {
            processorController.updateScriptDisplay();
        }
    }

    public void updateOpList() {
        scriptValid = false;
        List<ProcessingOperationInterface> newList = new ArrayList<>(processorController.getOperationList());
        boolean clearedOperations = newList.isEmpty() && processorController.getDefaultSection().equals(processorController.getActiveSection().orElse(null));
        areOperationListsValid.set(!clearedOperations);
        ProcessorController pController = processorController;
        if (pController.isViewingDataset() && pController.autoProcess.isSelected()) {
            processorController.processIfIdle();
        } else {
            execScriptList(false);
        }
    }

    public ProcessingSection getCurrentProcessingSection() {
        return currentProcessingSection;
    }

    public int getVecDim() {
        return vecDim;
    }

    public void setCurrentProcessingSection(ProcessingSection processingSection) {
        currentProcessingSection = processingSection;
    }

    public void setVecDim(ProcessingSection section) {
        int value;
        if (section == null) {
            value = 0;
        } else {
            try {
                value = section.getFirstDimension();
            } catch (NumberFormatException nFE) {
                value = 0;
            }
        }
        currentProcessingSection = section;
        vecDim = value;

        updateAcqModeFromTdComb();
        if (!processorController.isViewingDataset()) {
            reloadData();
        }
        fxmlController.setPhaseDimChoice(vecDim);
        processorController.updateSection();
    }

    public DatasetType getDatasetType() {
        return datasetType;
    }

    public void setDatasetType(DatasetType value) {
        datasetType = value;
    }

    private String getScriptFileName() {
        File file = new File(getNMRData().getFilePath());
        String fileName = file.getName();
        String scriptFileName;
        if (!file.isDirectory() && (fileName.endsWith(".dx") || fileName.endsWith(".jdx"))) {
            int lastDot = fileName.lastIndexOf(".");
            String rootName = fileName.substring(0, lastDot);
            scriptFileName = rootName + "_process.py";
        } else {
            scriptFileName = "process.py";
        }
        return scriptFileName;
    }

    public File getScriptDir() {
        File directory = null;
        String locMode = PreferencesController.getLocation();
        File dirFile;
        if (locMode.startsWith("FID") && (getNMRData() != null)) {
            dirFile = new File(getNMRData().getFilePath());
        } else {
            dirFile = PreferencesController.getDatasetDirectory();
        }
        if (dirFile != null) {
            if (dirFile.isDirectory()) {
                directory = dirFile;
            } else {
                directory = dirFile.getParentFile();
            }
        }
        return directory;
    }

    public File getDefaultScriptFile() {
        String scriptName = getScriptFileName();
        String locMode = PreferencesController.getLocation();
        File scriptFile;
        if (!locMode.startsWith("FID")) {
            scriptFile = datasetFile.getParentFile().toPath().resolve(datasetFile.getName() + "_process.py").toFile();
        } else {
            File scriptDir = getScriptDir();
            scriptFile = scriptDir.toPath().resolve(scriptName).toFile();

        }
        return scriptFile;
    }

    public void writeScript(String script) throws IOException {
        File scriptFile = getDefaultScriptFile();
        writeScript(script, scriptFile);
    }

    public void writeScript(String script, File scriptFile) throws IOException {
        Path path = scriptFile.toPath();
        if (!path.getParent().toFile().canWrite()) {
            throw new IOException("Can't write to script directory");
        }
        Files.write(path, script.getBytes());
    }

    private String getDatasetNameFromScript() {
        File file = getDefaultScriptFile();
        StringBuilder resultBuilder = new StringBuilder();
        if (file.exists()) {
            try (Stream<String> lines = Files.lines(file.toPath())) {
                lines.forEach(line -> {
                    if (line.trim().startsWith("CREATE")) {
                        int firstParen = line.indexOf("(");
                        int lastParen = line.lastIndexOf(")");
                        String filePath = line.substring(firstParen + 2, lastParen - 1);
                        File datasetFileFromScript = new File(filePath);
                        String datasetName = datasetFileFromScript.getName();
                        resultBuilder.append(datasetName);
                    }
                });
            } catch (IOException ex) {
                return "";
            }
        }
        return resultBuilder.toString();

    }

    /**
     * Loads the default script if present.
     *
     * @return True if default script is loaded, false if it is not loaded.
     */
    public boolean loadDefaultScriptIfPresent() {
        boolean scriptLoaded = false;
        File scriptFile = getDefaultScriptFile();
        if (scriptFile.exists() && scriptFile.canRead()) {
            processorController.openScript(scriptFile);
            scriptLoaded = true;
            log.info("Default script loaded: {}", scriptFile.getName());
        }
        return scriptLoaded;
    }

    protected String buildScript() {
        if (mapOpLists == null) {
            return "";
        }
        NMRData nmrData = getNMRData();

        if (nmrData == null) {
            return "";
        }
        File nmrDataFile = new File(nmrData.getFilePath());

        int nDim = nmrData.getNDim();
        String lineSep = System.lineSeparator();
        StringBuilder scriptBuilder = new StringBuilder();
        scriptBuilder.append("import os").append(lineSep);
        scriptBuilder.append("from pyproc import *").append(lineSep);
        scriptBuilder.append("procOpts(nprocess=").append(PreferencesController.getNProcesses()).append(")").append(lineSep);
        scriptBuilder.append("FID('").append(nmrDataFile.getPath().replace("\\", "/")).append("')").append(lineSep);

        if (datasetFile != null) {
            scriptBuilder.append("CREATE('").append(datasetFile.getPath().replace("\\", "/")).append("')").append(lineSep);
        } else {
            scriptBuilder.append("CREATE(").append("_DATASET_").append(")").append(lineSep);
        }

        String indent = "";
        scriptBuilder.append(processorController.refManager.getScriptReferenceLines(nDim, indent));
        scriptBuilder.append(processorController.getLSScript());
        String scriptCmds = getScriptCmds(nDim, indent);
        scriptBuilder.append(scriptCmds);
        return scriptBuilder.toString();
    }

    public String buildScript(int nDim) {
        if (mapOpLists == null) {
            return "";
        }
        StringBuilder scriptBuilder = new StringBuilder();
        String indent = "";
        scriptBuilder.append(processorController.refManager.getScriptReferenceLines(nDim, indent));
        String scriptCmds = getScriptCmds(nDim, indent);
        scriptBuilder.append(scriptCmds);
        return scriptBuilder.toString();
    }

    protected boolean scriptHasDataset(String script) {
        return !script.contains("_DATASET_");
    }

    protected String removeDatasetName(String script) {
        return script.replaceFirst("CREATE\\([^)]++\\)", "CREATE(_DATASET_)");
    }

    protected Optional<String> fixDatasetName(String script) {
        final Optional<String> emptyResult = Optional.empty();

        if (!scriptHasDataset(script)) {
            String datasetName = suggestDatasetName();
            String filePath = getNMRData().getFilePath();
            File nmrFile = new File(filePath);
            File directory = nmrFile.isDirectory() ? nmrFile : nmrFile.getParentFile();
            File file;
            if (getDatasetType() == DatasetType.SPINit) {
                Path datasetDir = directory.toPath();
                Path newProcPath = RS2DProcUtil.findNextProcPath(datasetDir);
                file = newProcPath.toFile();
            } else {
                Optional<File> fileOpt = FileNameDialog.getFileName(datasetName, fxmlController.getStage());
                if (fileOpt.isEmpty()) {
                    return emptyResult;
                }
                file = directory.toPath().resolve(fileOpt.get().toPath()).toFile();

                Optional<DatasetType> fileTypeOpt = DatasetType.typeFromFile(file);
                if (fileTypeOpt.isPresent() && fileTypeOpt.get() != getDatasetType()) {
                    GUIUtils.warn("Dataset creation", "File extension not consistent with dataset type");
                    return emptyResult;
                }
            }
            file = getDatasetType().addExtension(file);
            if (!datasetFileOkay(file)) {
                return emptyResult;
            }
            datasetFile = file;
            String fileString = file.getAbsoluteFile().toString();
            fileString = fileString.replace("\\", "/");
            script = script.replace("_DATASET_", "'" + fileString + "'");
        }
        return Optional.of(script);
    }

    private boolean datasetFileOkay(File datasetFileToCheck) {
        if (datasetFileToCheck.exists() && !datasetFileToCheck.canWrite()) {
            GUIUtils.warn("Dataset creation", "Dataset exists and can't be overwritten");
            return false;
        }
        if (!datasetFileToCheck.exists()) {
            File parentFile = datasetFileToCheck.getParentFile();
            boolean canWrite = parentFile.canWrite();
            // For SPINit files check if either of the above 2 parent directories are writable (Proc and the fid data directory)
            if (getDatasetType() == DatasetType.SPINit) {
                File procParent = parentFile.getParentFile();
                File fidParent = procParent.getParentFile();
                canWrite = (!procParent.exists() && fidParent.canWrite()) || procParent.canWrite();
            }
            if (!canWrite) {
                GUIUtils.warn("Dataset creation", "Can't create dataset in this directory");
                return false;
            }
        }
        return true;
    }

    private String suggestDatasetName() {
        String datasetName;
        NMRData nmrData = getNMRData();
        String filePath = getNMRData().getFilePath();
        File file = new File(filePath);
        String fileName = file.getName();
        if (fileName.toLowerCase().endsWith(".dx") || fileName.toLowerCase().endsWith(".jdx")) {
            datasetName = fileName;
        } else {
            File lastFile = NMRDataUtil.findNewestFile(getScriptDir().toPath());
            if ((lastFile != null) && (nmrData instanceof BrukerData)) {
                Pattern pattern = Pattern.compile("[1-9]r+");
                if (pattern.matcher(lastFile.getName()).matches()) {
                    lastFile = null;
                }
            }
            if (lastFile != null) {
                datasetName = lastFile.getName();
            } else {
                datasetName = getDatasetNameFromScript();
                if (datasetName.isEmpty()) {
                    if (nmrData instanceof BrukerData brukerData) {
                        datasetName = brukerData.suggestName();
                    } else if (nmrData instanceof VarianData varianData) {
                        datasetName = varianData.suggestName();
                    } else {
                        datasetName = getNMRData().getSequence();
                    }
                }
            }
        }
        int lastDot = datasetName.lastIndexOf(".");
        if (lastDot != -1) {
            datasetName = datasetName.substring(0, lastDot);
        }
        return datasetName;
    }

    public int mapToDataset(int iDim) {
        int mapDim = iDim;
        if ((mapToDataset != null) && (mapToDataset.length > iDim)) {
            mapDim = mapToDataset[iDim];
        }
        return mapDim;
    }

    public void acqMode(int iDim, AcquisitionType mode) {
        acqMode[iDim] = mode;
    }

    private void updateAcqModeFromTdComb() {
        if (getNMRData() == null) {
            return;
        }

        int nDim = getNMRData().getNDim();
        for (int i = 1; i < nDim; i++) {
            acqMode[i] = getNMRData().getUserSymbolicCoefs(i);
            if (acqMode[i] == null) {
                acqMode[i] = AcquisitionType.HYPER;
            }
        }
    }

    public boolean hasCommands() {
        return !mapOpLists.isEmpty();
    }

    void setupMapToDataset(int nDim) {
        int nDatasetDims = 0;
        mapToDataset = new int[nDim];
        Arrays.fill(mapToDataset, -1);
        for (var entry : mapOpLists.entrySet()) {
            ProcessingSection processingSection = entry.getKey();
            if (processingSection.is1D()) {
                int dimNum = processingSection.getFirstDimension();
                if (dimNum >= nDim) {
                    continue;
                }
                if (mapToDataset[dimNum] == -1) {
                    String dimStr = String.valueOf(dimNum);
                    if (!processorController.refManager.getSkip(dimStr)) {
                        mapToDataset[dimNum] = nDatasetDims++;
                    }
                }
            }
        }
    }

    private String getScriptCmds(int nDim, String indent) {
        String lineSep = System.lineSeparator();
        StringBuilder scriptBuilder = new StringBuilder();
        setupMapToDataset(nDim);
        for (Map.Entry<ProcessingSection, List<ProcessingOperationInterface>> entry : mapOpLists.entrySet()) {
            if (entry.getValue() != null) {
                ProcessingSection processingSection = entry.getKey();
                List<ProcessingOperationInterface> scriptList = entry.getValue();
                if (!scriptList.isEmpty()) {
                    String parDim = processingSection.dimString();
                    scriptBuilder.append(indent).append("DIM(").append(parDim).append(")");
                    scriptBuilder.append(lineSep);
                    addOpLines(indent, scriptList, scriptBuilder, lineSep);
                }
            }
        }
        scriptBuilder.append(indent).append("run()");
        return scriptBuilder.toString();
    }

    private static void addOpLines(String indent, List<ProcessingOperationInterface> scriptList, StringBuilder scriptBuilder, String lineSep) {
        for (ProcessingOperationInterface processingOperation : scriptList) {
            if (processingOperation instanceof ProcessingOperation op) {
                scriptBuilder.append(indent).append(op);
                scriptBuilder.append(lineSep);
            } else if ((processingOperation instanceof ProcessingOperationGroup groupOp) && !groupOp.isDisabled()) {
                for (var op : groupOp.getProcessingOperationList()) {
                    if (!op.isDisabled()) {
                        scriptBuilder.append(indent).append(op);
                        scriptBuilder.append(lineSep);
                    }
                }
            }
        }
    }

    private void setFlags(Map<String, Boolean> flags) {
        getNMRData().setFidFlags(flags);
    }

    private int getArraySize(NMRData nmrData, String[] acqOrder) {
        int nArray = 0;
        int nDim = nmrData.getNDim();
        for (String acqOrderElem : acqOrder) {
            if ((!acqOrderElem.isEmpty()) && (acqOrderElem.charAt(0) == 'a')) {
                nArray++;
            }
        }
        if (nArray == 0) {
            for (int i = 1; i < nDim; i++) {
                int arraySize = nmrData.getArraySize(i);
                if (arraySize != 0) {
                    nArray++;
                }
            }
        }
        return nArray;
    }

    private void updateCounter() {
        NMRData nmrData = getNMRData();
        String[] acqOrder = nmrData.getAcqOrder();

        int nDim = nmrData.getNDim();
        int nArray = getArraySize(nmrData, acqOrder);

        int[] tdSizes = new int[nDim + nArray];
        boolean[] complex = new boolean[nDim + nArray];
        int[] groupSizes = new int[nDim + nArray];
        int j = 0;
        for (int i = 0; i < nDim; i++) {
            tdSizes[j] = nmrData.getSize(i);
            complex[j] = nmrData.isComplex(i);
            groupSizes[i] = nmrData.getGroupSize(i);

            int arraySize = nmrData.getArraySize(i);
            if (arraySize != 0) {
                tdSizes[j] = tdSizes[i] / arraySize;
                tdSizes[j + 1] = arraySize;
                complex[j + 1] = false;
                j++;
            }
            j++;
        }
        for (int i = 0; i < tdSizes.length; i++) {
            if (tdSizes[i] == 0) {
                tdSizes[i] = 1;
            }
        }
        if (nDim > 1) {
            multiVecCounter = new MultiVecCounter(tdSizes, groupSizes, complex, acqOrder, nDim + nArray);
            vectorsPerGroup = multiVecCounter.getGroupSize();
        } else {
            vectorsPerGroup = 1;
        }
    }

    public void setData(NMRData data, boolean clearOps) {
        nmrDataProperty().set(data);
        setDatasetType(data.getPreferredDatasetType());

        datasetFile = null;
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("fixdsp", fixDSP);
        setFlags(flags);
        updateCounter();
        int nDim = getNMRData().getNDim();
        acqMode = new AcquisitionType[nDim];
        processorController.removeOpListener();
        mapOpLists.clear();
        processorController.refManager.clearObjectPropertyMap();
        Map<ProcessingSection, List<ProcessingOperationInterface>> listOfScripts = getScriptList();
        List<String> saveHeaderList = new ArrayList<>(headerList);

        // when setting data reset vecdim back to 0 as it could have been set to
        // a value higher than the number of dimensions
        vecDim = 0;
        currentProcessingSection = processorController.getDefaultSection();
        boolean[] complex = new boolean[nDim];
        for (int iDim = 0; iDim < nDim; iDim++) {
            complex[iDim] = data.getGroupSize(iDim) == 2;
        }
        processorController.updateDimChoice(complex);
        processorController.refManager.updateReferencePane(getNMRData(), nDim);
        reloadData();
        if (!clearOps) {
            setScripts(saveHeaderList, listOfScripts);
        }
        NMRDataUtil.setCurrentData(data);
        addFIDToPython();
        processorController.addOpListener();
    }

    public void reloadData() {
        NMRData nmrData = getNMRData();
        if (nmrData == null) {
            log.info("NMRData is null, unable to reload.");
            return;
        }
        chart.setPh0(0);
        chart.setPh1(0);
        chart.setPivot(null);
        int nDim = nmrData.getNDim();
        iVec = 0;
        execScript("", false, false);
        if ((nmrData instanceof NMRViewData nvData) && !nmrData.isFID()) {
            chart.setDataset(nvData.getDataset());
            chart.getCrossHairs().setStates(true, true, true, true);
            int[] sizes = new int[0];
            processorController.navigatorGUI.vectorStatus(sizes, vecDim);
        } else {
            chart.getFXMLController().setFIDActive(true);

            loadVectors(0);
            chart.getCrossHairs().setStates(false, true, false, true);
            try {
                ProcessOps process = getProcess();
                process.exec();
            } catch (IncompleteProcessException ipe) {
                log.warn(ipe.getMessage(), ipe);
            }
            int[] sizes = new int[1];
            sizes[0] = 1;
            if (nDim > 1) {
                sizes = new int[nDim];
                for (int i = 0; i < nDim; i++) {
                    sizes[i] = nmrData.getSize(i);
                }
            }
            processorController.navigatorGUI.vectorStatus(sizes, vecDim);
        }
        chart.full();
        chart.autoScale();

        fxmlController.getPhaser().setPH0Slider(chart.getPh0());
        fxmlController.getPhaser().setPH1Slider(chart.getPh1());
        chart.layoutPlotChildren();
    }

    public void execScriptList(boolean reloadData) {
        if (currentProcessingSection.is1D()) {
            execScript(processorController.getScript(), true, reloadData);
        }
    }

    void processException(ProcessOps process, Exception pE) {
        if (pE instanceof PyException pyE) {
            if (pyE.getCause() == null) {
                if (pE.getLocalizedMessage() != null) {
                    processorController.setProcessingStatus("pyerror " + pE.getLocalizedMessage(), false, pE);
                } else {
                    processorController.setProcessingStatus("pyerror " + pyE.type.toString(), false, pE);
                }
            } else {
                processorController.setProcessingStatus(pyE.getCause().getMessage(), false, pE);
            }
            log.warn(pyE.getMessage(), pyE);
        } else {
            processorController.setProcessingStatus("error " + pE.getMessage(), false, pE);
        }
        int j = 0;
        for (Vec saveVec : saveVectors) {
            Vec loadVec = vectors.get(j);
            saveVec.copy(loadVec);
            process.addVec(loadVec);
            j++;
        }
    }

    private void execProcess(ProcessOps process) {
        if (!vectors.isEmpty()) {
            process.clearVectors();
            int i = 0;
            for (Vec saveVec : saveVectors) {
                Vec loadVec = vectors.get(i);
                saveVec.copy(loadVec);
                process.addVec(loadVec);
                i++;
            }
            try {
                processorController.clearProcessingTextLabel();
                process.exec();
            } catch (IncompleteProcessException e) {
                log.warn("error message: {}", e.getMessage(), e);
                processorController.setProcessingStatus(e.op + " " + e.index + ": " + e.getMessage(), false, e);
                log.warn(e.getMessage(), e);
                int j = 0;
                for (Vec saveVec : saveVectors) {
                    Vec loadVec = vectors.get(j);
                    saveVec.copy(loadVec);
                    process.addVec(loadVec);
                    j++;
                }
            } catch (Exception pE) {
                processorController.setProcessingStatus(pE.getMessage(), false, pE);
            }
        }
    }

    boolean setupProcess(NMRData nmrData, String script, boolean reloadData) {
        if (nmrData != null) {
            NMRDataUtil.setCurrentData(nmrData);
        }
        AnalystPythonInterpreter.exec("useLocal()");
        if (nmrData != null) {
            AnalystPythonInterpreter.exec("fidInfo = makeFIDInfo()");
        }
        if ((nmrData instanceof NMRViewData) && !nmrData.isFID()) {
            return false;
        }
        if (processorController.refManager == null) {
            log.info("null ref manager");
            return false;
        }
        processorController.clearProcessingTextLabel();
        if (nmrData != null) {
            String parString = processorController.refManager.getScriptReferenceLines(nmrData.getNDim(), "");
            AnalystPythonInterpreter.exec(parString);
        }
        if (reloadData) {
            loadVectors(0);
        }
        if (processorController.isViewingFID()) {
            AnalystPythonInterpreter.exec(script);
        }
        return true;
    }

    public void execScript(String script, boolean doProcess, boolean reloadData) {
        Processor.getProcessor().clearProcessorError();
        ProcessOps process = getProcess();
        process.clearOps();
        process.setDim(vecDim);
        if (processorController == null) {
            log.info("null processor controller.");
            return;
        }
        if (processorController.isViewingDataset()) {
            return;
        }
        NMRData nmrData = getNMRData();
        Processor.getProcessor().addDataset(nmrData);
        try {
            if (!setupProcess(nmrData, script, reloadData)) {
                return;
            }
        } catch (Exception pE) {
            processException(process, pE);
            return;
        }
        if (doProcess) {
            execProcess(process);
            if (!processorController.isViewingDataset() && !vectors.isEmpty()) {
                Vec loadVec = vectors.get(0);
                if (loadVec.getFreqDomain() != lastWasFreqDomain) {
                    chart.autoScale();
                } else {
                    chart.refresh();
                }
                lastWasFreqDomain = loadVec.getFreqDomain();
            }
        }
    }

    private void addFIDToPython() {
        AnalystPythonInterpreter.exec("from pyproc import *");
        AnalystPythonInterpreter.exec("useLocal()");
        AnalystPythonInterpreter.exec("fidInfo = makeFIDInfo()");
    }

    public String getGenScript(boolean arrayed) {
        addFIDToPython();
        String arrayVal = arrayed ? "True" : "False";
        String useApod = PreferencesController.getUseFIDParApodization() ? "True" : "False";
        return AnalystPythonInterpreter.eval("genScript(arrayed=" + arrayVal + ", useapod=" + useApod + ")", String.class);
    }

    public List<?> getDocs() {
        return pyDocs;
    }

    public ProcessorController getProcessorController() {
        return processorController;
    }

    public static String buildInitScript() {
        StringBuilder scriptBuilder = new StringBuilder();
        String lineSep = System.lineSeparator();
        scriptBuilder.append("import os").append(lineSep);
        scriptBuilder.append("from pyproc import *").append(lineSep);
        scriptBuilder.append("useProcessor()").append(lineSep);
        scriptBuilder.append("procOpts(nprocess=").append(PreferencesController.getNProcesses()).append(")").append(lineSep);
        return scriptBuilder.toString();
    }

    public static String buildFileScriptPart(String fidFilePath, String datasetFilePath) {
        StringBuilder scriptBuilder = new StringBuilder();
        String lineSep = System.lineSeparator();
        scriptBuilder.append("useProcessor()").append(lineSep);
        scriptBuilder.append("FID('").append(fidFilePath.replace("\\", "/")).append("')").append(lineSep);
        scriptBuilder.append("CREATE('").append(datasetFilePath.replace("\\", "/")).append("')").append(lineSep);
        return scriptBuilder.toString();
    }

    public record VecIndexScore(VecIndex vecIndex, int maxIndex, double score) implements Comparable<VecIndexScore> {
        @Override
        public int compareTo(VecIndexScore o) {
            // compare on score first
            int scoreComparison = Double.compare(score, o.score());
            if (scoreComparison != 0) {
                return scoreComparison;
            }

            // then on index if the scores are identical
            return Integer.compare(maxIndex, o.maxIndex());
        }
    }

    /**
     * Compare dimensions ("D1", "D2,3", "D2", "D3", ...) grouping by prefix when a comma separator is present.
     * Dimensions with comma are considered lower than the full dimension, ie "D2,3" < "D2".
     * <br>
     * Dimensions with comma are used for NUS. D2,3 must appear before D2 and D3 so that this step can fill in the non-acquired data vectors.
     */
    static class DimensionComparator implements Comparator<String> {
        @Override
        public int compare(String a, String b) {
            if (a == null && b == null)
                return 0;
            if (a == null)
                return 1;
            if (b == null)
                return -1;

            // split strings on ","
            int separatorA = a.indexOf(',');
            int separatorB = b.indexOf(',');
            boolean hasSeparatorA = separatorA >= 0;
            boolean hasSeparatorB = separatorB >= 0;

            // if both strings have a ",", or none have, use a normal string comparison
            if (hasSeparatorA == hasSeparatorB) {
                return a.compareTo(b);
            }

            // one of the string has a prefix, the other doesn't, check if the complete string is the prefix of the other one
            // ex: if a="D2,1" and b="D2", then b is the prefix of a.
            if (hasSeparatorA && a.startsWith(b)) {
                return -1;
            }
            if (hasSeparatorB && b.startsWith(a)) {
                return 1;
            }

            // the strings are unrelated (one isn't a prefix of the other), use normal string comparison
            return a.compareTo(b);
        }
    }
}
