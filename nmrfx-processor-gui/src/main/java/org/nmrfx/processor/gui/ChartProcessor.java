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

import javafx.beans.property.SimpleObjectProperty;
import javafx.stage.FileChooser;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetType;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.datasets.vendor.nmrview.NMRViewData;
import org.nmrfx.processor.datasets.vendor.rs2d.RS2DProcUtil;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.MultiVecCounter;
import org.nmrfx.processor.processing.Processor;
import org.nmrfx.processor.processing.VecIndex;
import org.nmrfx.processor.processing.processes.IncompleteProcessException;
import org.nmrfx.processor.processing.processes.ProcessOps;
import org.nmrfx.utils.GUIUtils;
import org.python.core.PyException;
import org.python.core.PyObject;
import org.python.util.InteractiveInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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

    private SimpleObjectProperty nmrDataObj;

    public SimpleObjectProperty nmrDataProperty() {
        if (nmrDataObj == null) {
            nmrDataObj = new SimpleObjectProperty(null);
        }
        return nmrDataObj;
    }

    public void setNMRData(NMRData value) {
        nmrDataProperty().set(value);
    }

    public NMRData getNMRData() {
        return (NMRData) nmrDataProperty().get();
    }

    File datasetFile;
    File datasetFileTemp;
    /**
     * The InteractiveInterpreter in which processing commands will be executed.
     */
    private final InteractiveInterpreter interpreter;
    /**
     * The dimension of datasetFile that is in use for interactive processing.
     */
    private int vecDim = 0;
    /**
     * The name of the dimension of datasetFile that is in use for interactive
     * processing. Is a string because it could refer to multiple dimensions
     * (for IST matrix processing, for example).
     */
    private String vecDimName = "D1";
    /**
     * The name of the datasetFile that will be created when whole data file is
     * processed.
     */
    private DatasetType datasetType = DEFAULT_DATASET_TYPE;

    /**
     * List of commands to be executed at beginning of script.
     */
    List<String> headerList = new ArrayList<>();

    /**
     * Map of lists of operations with key being the dimension the operations
     * apply to
     */
    Map<String, List<String>> mapOpLists = new TreeMap<>(new DimComparator());

    /**
     * List of Vec objects that contain data used in interactive processing. The
     * number of Vec objects correspond to the number of vectors in the raw data
     * that have the same indirect acquisition times
     */
    ArrayList<Vec> vectors = new ArrayList<>();
    /**
     * List of Vec objects that save copy of data used in interactive
     * processing. Used to restore vectors when an error happens during
     * processing
     */
    ArrayList<Vec> saveVectors = new ArrayList<>();
    /**
     * Which Vec of the list of vectors should currently be displayed
     */

    int iVec = 0;
    /**
     * Array of strings representing the acquisition modes (like hypercomplex or
     * echo-antiecho) that were used in acquiring each indirect dimension.
     * Currently, only used in reading vectors from raw data file for
     * interactive processing.
     */

    String[] acqMode = null;
    /**
     * Should Bruker FIDs be corrected when loading them for the DSP artifact at
     * beginning of FID.
     */
    boolean fixDSP = true;
    /**
     * Used to determine mapping of position of FIDs in raw data file..
     */
    private MultiVecCounter multiVecCounter;
    /**
     * How many vectors are present in data file for each unique combination of
     * indirect acquisition times. Typically 2 for 2D, 4 for 3D etc.
     */
    int vectorsPerGroup = 2;
    /**
     * Display chart used for rendering vectors.
     */
    PolyChart chart;

    ArrayList pyDocs = null;
    boolean scriptValid = false;
    int[] mapToDataset = null;

    final ProcessorController processorController;
    FXMLController fxmlController;

    static double[] echoAntiEchoCoefs = {1.0, 0.0, -1.0, 0.0, 0.0, 1.0, 0.0, 1.0};
    static double[] echoAntiEchoRCoefs = {1.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, -1.0};
    static double[] hyperCoefs = {1.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0};
    static double[] hyperRCoefs = {1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0};

    public ChartProcessor(ProcessorController processorController) {
        interpreter = MainApp.interpreter;
        this.processorController = processorController;
        PyObject pyDocObject = interpreter.eval("getDocs()");
        pyDocs = (ArrayList) pyDocObject.__tojava__(java.util.ArrayList.class);
    }

    class DimComparator implements Comparator {

        @Override
        public int compare(Object o1, Object o2) {
            int result = 0;
            if ((o1 == null) && (o2 == null)) {
                result = 0;
            } else if (o1 == null) {
                result = 1;
            } else if (o2 == null) {
                result = -1;
            } else {
                String s1 = (String) o1;
                String s2 = (String) o2;
                if (!s1.equals("s2")) {
                    int comma1 = s1.indexOf(',');
                    int comma2 = s2.indexOf(',');
                    String s1c = s1;
                    if (comma1 != -1) {
                        s1c = s1.substring(0, comma1);
                    }
                    String s2c = s2;
                    if (comma2 != -1) {
                        s2c = s2.substring(0, comma2);
                    }
                    if ((comma1 == -1) && (comma2 == -1)) {
                        result = s1.compareTo(s2);
                    } else if ((comma1 != -1) && (comma2 != -1)) {
                        result = s1.compareTo(s2);
                    } else if (s1c.equals(s2c)) {
                        if (comma1 != -1) {
                            result = -1;
                        } else {
                            result = 1;
                        }
                    } else {
                        result = s1c.compareTo(s2c);
                    }
                }
            }
            return result;
        }
    }

    InteractiveInterpreter getInterpreter() {
        return interpreter;
    }

    org.nmrfx.processor.processing.processes.ProcessOps getProcess() {
        PyObject pObject = interpreter.eval("getCurrentProcess()");
        ProcessOps process = (ProcessOps) pObject.__tojava__(ProcessOps.class);
        return process;
    }

    public void setChart(PolyChart chart) {
        this.chart = chart;
        chart.controller.chartProcessor = this;
        initEmptyVecs();
        execScript("", false, false);
    }

    public PolyChart getChart() {
        return chart;
    }

    public void setEchoAntiEcho(boolean value) {
        //if (value) {
        //acqMode[vecDim] = "ea";
        //} else {
        //acqMode[vecDim] = "hc";
        //}
    }

    public String getAcqOrder() {
        return getAcqOrder(false);
    }

    public String getAcqOrder(boolean useQuotes) {
        String acqOrder = "";
        NMRData nmrData = getNMRData();

        if (nmrData != null) {
            acqOrder = nmrData.getAcqOrderShort();
            if (!acqOrder.equals("")) {
                if (useQuotes) {
                    acqOrder = "'" + acqOrder + "'";
                }
            } else {
                String[] acqOrderArray = nmrData.getAcqOrder();
                if (acqOrderArray != null) {
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
                    acqOrder = sBuilder.toString();
                }
            }
        }
        return acqOrder;
    }

    public void setAcqOrder(String acqOrder) {
        NMRData nmrData = getNMRData();
        if (nmrData != null) {
            String[] acqOrderArray = acqOrder.split(",");
            // fixme  should have a general acqOrder validator

            boolean ok = true;
            int nDimChars = 0;
            for (int i = 0; i < acqOrderArray.length; i++) {
                acqOrderArray[i] = acqOrderArray[i].trim();
                if (acqOrderArray[i].length() == 0) {
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
                if (sizeArg.length() == 0) {
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

    public boolean getEchoAntiEcho() {
        return (acqMode[vecDim] != null) && acqMode[vecDim].equals("ea");
    }

    public void setFixDSP(boolean value) {
        fixDSP = value;
    }

    public boolean getFixDSP() {
        return fixDSP;
    }

    public int[] loadVectors(int i) {
        int[] rows = {i};
        return loadVectors(1, rows);
    }

    public int[] loadVectors(int iDim, int[] rows) {
        //setFlags();
        NMRData nmrData = getNMRData();
        int nPoints = nmrData.getNPoints();
        if (vecDim != 0) {
            nPoints = nmrData.getSize(vecDim);
            if (nmrData.isComplex(vecDim)) {
                nPoints *= 2;
            }
        }
        ProcessOps process = getProcess();
        process.clearVectors();
        vectors.clear();
        saveVectors.clear();
        int nVectors = 1;
        VecIndex vecIndex = null;
        int index = 0;
        if (rows.length > 0) {
            index = rows[0];
        }
        if (vecDim == 0) {
            nVectors = vectorsPerGroup;
            if (multiVecCounter != null) {
                if (rows.length == 1) {

                    vecIndex = multiVecCounter.getNextGroup(index);
                } else {
                    index = multiVecCounter.findOutGroup(rows);
                    vecIndex = multiVecCounter.getNextGroup(index);
                }
                if (nmrData.getSampleSchedule() != null) {
                    vecIndex = nmrData.getSampleSchedule().convertToNUSGroup(vecIndex, index);
                    if (vecIndex != null) {
                        vecIndex.printMe(index, 1);
                    } else {
                        log.info("No vec");
                    }

                }
            }
        }
        int[] fileIndices = new int[nVectors];
        for (int j = 0; j < nVectors; j++) {
            Vec newVec = new Vec(nPoints, nmrData.isComplex(vecDim));
            Vec saveVec = new Vec(nPoints, nmrData.isComplex(vecDim));

            if (vecDim == 0) {
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
            } else {
                fileIndices[j] = index + j;
                nmrData.readVector(vecDim, index + j, newVec);
                if ((acqMode[vecDim] != null) && acqMode[vecDim].equals("echo-antiecho")) {
                    newVec.eaCombine(echoAntiEchoCoefs);
                } else if ((acqMode[vecDim] != null) && acqMode[vecDim].equals("echo-antiecho-r")) {
                    newVec.eaCombine(echoAntiEchoRCoefs);
                } else if ((acqMode[vecDim] != null) && acqMode[vecDim].equals("hyper")) {
                    newVec.eaCombine(hyperCoefs);
                } else if ((acqMode[vecDim] != null) && acqMode[vecDim].equals("hyper-r")) {
                    newVec.eaCombine(hyperRCoefs);
                } else {
                    newVec.hcCombine();
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
        chart.setDataset(new Dataset(vec), false, true);
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

    public void initEmptyVecs() {
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

    public void vecRow(int iDim, int i) {
        if (getNMRData() != null) {
            int nDim = getNMRData().getNDim();
            int size = 1;
            if (nDim > 1) {
                size = getNMRData().getSize(iDim);
            }

            if (i >= size) {
                i = size - 1;
            }
            if (i < 0) {
                i = 0;
            }
            processorController.setRowLabel(i + 1, size);
            int[] rows = processorController.getRows();
            int[] fileIndices = loadVectors(1, rows);
            processorController.setFileIndex(fileIndices);
            try {
                ProcessOps process = getProcess();
                process.exec();
            } catch (IncompleteProcessException ipe) {
                log.warn(ipe.getMessage(), ipe);
            }

            chart.layoutPlotChildren();
        }
    }

    public void setupProcess() {
        // process.addOp(new Phase(0.0,216*360.0));
        //process.addOp(new Tdss(31, 3, 216.0));

//        process.addOp(new Zf(1, -1));
//        process.addOp(new Ft(false, false));
    }

    public Map<String, List<String>> getOperations() {
        return mapOpLists;
    }

    public List<String> getOperations(String dimName) {
        return mapOpLists.get(dimName);
    }

    public void clearAllOperations() {
        if (mapOpLists != null) {
            mapOpLists.clear();
        }
    }

    public Map<String, List<String>> getScriptList() {
        Map<String, List<String>> copyOfMapOpLists = new TreeMap<>(new DimComparator());
        if (mapOpLists != null) {
            for (Map.Entry<String, List<String>> entry : mapOpLists.entrySet()) {
                List<String> newList = new ArrayList<>();
                if (entry.getValue() != null) {
                    newList.addAll(entry.getValue());
                }
                copyOfMapOpLists.put(entry.getKey(), newList);
            }
        }
        return copyOfMapOpLists;
    }

    public void setScripts(List<String> newHeaderList, Map<String, List<String>> opMap) {
        if ((opMap.size() == 0) || (opMap == null)) {
            return;
        }
        mapOpLists = new TreeMap<>(new DimComparator());
        for (Map.Entry<String, List<String>> entry : opMap.entrySet()) {
            mapOpLists.put(entry.getKey(), entry.getValue());
        }
        ArrayList<String> opList = (ArrayList<String>) mapOpLists.get(vecDimName);
        headerList.clear();
        headerList.addAll(newHeaderList);
        processorController.refManager.setDataFields(headerList);
        vecDim = 0;
        processorController.refManager.setupItems(0);
        if (opList == null) {
            opList = new ArrayList<>();
        }
        processorController.setOperationList(opList);
        //execScriptList(false);
        if (!processorController.isViewingDataset()) {
            chart.full();
            chart.autoScale();
        }
    }

    void setOp(String op) {
        setOp(op, false, -1);
    }

    void setOp(String op, boolean appendOp, int index) {
        List<String> listItems = mapOpLists.get(vecDimName);
        op = op.trim();
        op = OperationInfo.fixOp(op);
        if (op.length() != 0) {
            int opIndex = index;
            if (opIndex == -1) {
                opIndex = OperationInfo.getPosition(listItems, op);
            }
            if (opIndex < 0) {
                log.warn("bad op");
            } else if (opIndex >= listItems.size()) {
                listItems.add(op);
            } else {
                String curOp = OperationInfo.trimOp(listItems.get(opIndex));
                String trimOp = OperationInfo.trimOp(op);
                if (!appendOp && trimOp.equals(curOp)) {
                    listItems.set(opIndex, op);
                } else {
                    listItems.add(opIndex, op);
                }
            }
        }
    }

    public boolean isScriptValid() {
        return scriptValid;
    }

    public void setScriptValid(boolean state) {
        scriptValid = state;
    }

    public void updateOpList() {
        if (vecDimName.equals("")) {
            return;
        }
        scriptValid = false;
        List<String> oldList = new ArrayList<>();
        oldList.addAll(processorController.getOperationList());
        mapOpLists.put(vecDimName, oldList);
        ProcessorController pController = processorController;
        if (pController.isViewingDataset() && pController.autoProcess.isSelected()) {
            processorController.processIfIdle();
        } else {
            execScriptList(false);
        }

    }

    public void setVecDim(String dimName) {
        int value;
        boolean isDim;
        try {
            value = Integer.parseInt(dimName.substring(1));
            value--;
            isDim = true;
        } catch (NumberFormatException nFE) {
            value = 0;
            isDim = false;
        }
        vecDimName = dimName;
        vecDim = value;
        ArrayList<String> oldList = new ArrayList<>();

        if (isDim) {
            if (mapOpLists.get(vecDimName) != null) {
                oldList.addAll(mapOpLists.get(vecDimName));
            }
        } else if (mapOpLists.containsKey(dimName)) {
            oldList.addAll(mapOpLists.get(dimName));
        }
        getCombineMode();
        if (!processorController.isViewingDataset()) {
            reloadData();
        }
        processorController.setOperationList(oldList);
        fxmlController.setPhaseDimChoice(vecDim);
        if (!processorController.isViewingDataset()) {
            chart.full();
            chart.autoScale();
        }
    }

    public String getVecDimName() {
        return vecDimName;
    }

    public int getVecDim() {
        return vecDim;
    }

    public void setDatasetType(DatasetType value) {
        datasetType = value;
    }

    public DatasetType getDatasetType() {
        return datasetType;
    }

    public void writeScript() throws IOException {
        String script = buildScript();
        writeScript(script);
    }

    public String getScriptFileName() {
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

    public String getDatasetNameFromScript() {
        File file = getDefaultScriptFile();
        StringBuilder resultBuilder = new StringBuilder();
        if (file.exists()) {
            try (Stream<String> lines = Files.lines(file.toPath())){
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

    String buildScript() {
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
        scriptBuilder.append(processorController.refManager.getParString(nDim, indent));
        scriptBuilder.append(processorController.getLSScript());
        String scriptCmds = getScriptCmds(nDim, indent, true);
        scriptBuilder.append(scriptCmds);
        return scriptBuilder.toString();
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

    public String buildScript(int nDim) {
        if (mapOpLists == null) {
            return "";
        }
        String lineSep = System.lineSeparator();
        StringBuilder scriptBuilder = new StringBuilder();
        String indent = "";
        scriptBuilder.append(processorController.refManager.getParString(nDim, indent));
        String scriptCmds = getScriptCmds(nDim, indent, true);
        scriptBuilder.append(scriptCmds);
        return scriptBuilder.toString();
    }

    public String buildMultiScript(String baseDir, String outputDir, ArrayList<String> fileNames, boolean combineFiles) {
        boolean useIFile = true;
        String baseName = "data";
        int nDim = getNMRData().getNDim();
        String lineSep = System.lineSeparator();
        StringBuilder scriptBuilder = new StringBuilder();
        scriptBuilder.append("import os").append(lineSep);
        scriptBuilder.append("from pyproc import *").append(lineSep);
        scriptBuilder.append("getMeasureMap().clear()").append(lineSep);
        scriptBuilder.append("fileNames=[").append(lineSep);
        int i = 0;
        int nPerLine = 5;
        int last = fileNames.size() - 1;
        for (String filePath : fileNames) {
            if ((i % nPerLine) == 0) {
                scriptBuilder.append("          ");
            }
            scriptBuilder.append("\"").append(filePath).append("\"");
            if (i != last) {
                scriptBuilder.append(",");
                if ((i % nPerLine) == (nPerLine - 1)) {
                    scriptBuilder.append(lineSep);
                }
            }
            i++;
        }
        int nFIDs = fileNames.size();
        scriptBuilder.append("]").append(lineSep);
        scriptBuilder.append("setupScanTable(");
        scriptBuilder.append("'").append(outputDir).append("/scantbl.txt')").append(lineSep);
        String indent = "    ";
        scriptBuilder.append("for (iFile,fileName) in enumerate(fileNames):").append(lineSep);
        scriptBuilder.append(indent).append("(fullFileName,filePath,fullDataName,dataName)=makeDataNames(fileName,baseDir=");
        scriptBuilder.append("'").append(baseDir).append("'");
        scriptBuilder.append(",outDir='").append(outputDir).append("'");
        if (useIFile) {
            scriptBuilder.append(",iFile=").append("iFile");
        }
        if (baseName.length() > 0) {
            scriptBuilder.append(",baseName=").append("'").append(baseName).append("'");
        }
        if (combineFiles) {
            scriptBuilder.append(",multiMode=True");
        }
        scriptBuilder.append(")").append(lineSep);

        scriptBuilder.append(indent).append("useProcessor()").append(lineSep);
        scriptBuilder.append(indent).append("FID(").append("fullFileName").append(")").append(lineSep);
        if (combineFiles) {
            scriptBuilder.append(indent).append("CREATE(").append("fullDataName").append(",extra=").append(nFIDs).append(")").append(lineSep);
        } else {
            scriptBuilder.append(indent).append("CREATE(").append("fullDataName").append(")").append(lineSep);
        }

        scriptBuilder.append(processorController.refManager.getParString(nDim, indent));
        String scriptCmds = getScriptCmds(nDim, indent, false);
        scriptBuilder.append(scriptCmds);
        if (combineFiles) {
            scriptBuilder.append(indent).append("WRITE(index=iFile)").append(lineSep);
        }
        scriptBuilder.append(indent).append("run()").append(lineSep);
        scriptBuilder.append(indent).append("writeToScanTable(iFile,filePath,dataName,getMeasureMap())").append(lineSep);
        scriptBuilder.append(lineSep);
        if (combineFiles) {
            scriptBuilder.append("closeDataset()").append(lineSep);
            datasetFile = new File(outputDir, "multi.nv");
        }
        scriptBuilder.append("closeScanTable()").append(lineSep);
        return scriptBuilder.toString();
    }

    boolean scriptHasDataset(String script) {
        return !script.contains("_DATASET_");
    }

    String removeDatasetName(String script) {
        return script.replaceFirst("CREATE\\([^\\)]++\\)", "CREATE(_DATASET_)");
    }

    Optional<String> fixDatasetName(String script) {
        final Optional<String> emptyResult = Optional.empty();
        final Optional<String> result;

        if (!scriptHasDataset(script)) {
            String datasetName = suggestDatasetName();
            String filePath = getNMRData().getFilePath();
            File nmrFile = new File(filePath);
            File directory = nmrFile.isDirectory() ? nmrFile : nmrFile.getParentFile();
            File file;
            if (getDatasetType()== DatasetType.SPINit) {
                Path datasetDir = directory.toPath();
                Path newProcPath = RS2DProcUtil.findNextProcPath(datasetDir);
                try {
                    Files.createDirectories(newProcPath);
                } catch (IOException e) {
                    GUIUtils.warn("Dataset creation", "Unable to create new dataset directory");
                    return emptyResult;
                }
                file = newProcPath.toFile();
            } else {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setInitialDirectory(directory);
                fileChooser.setInitialFileName(datasetName);
                file = fileChooser.showSaveDialog(null);
                if (file == null) {
                    return emptyResult;
                }
                Optional<DatasetType> fileTypeOpt = DatasetType.typeFromFile(file);
                if (fileTypeOpt.isPresent()) {
                    DatasetType fileType = fileTypeOpt.get();
                    if (fileType != getDatasetType()) {
                        GUIUtils.warn("Dataset creation", "File extension not consistent with dataset type");
                        return emptyResult;
                    }
                }
            }

            file = getDatasetType().addExtension(file);
            if (file.exists() && !file.canWrite()) {
                GUIUtils.warn("Dataset creation", "Dataset exists and can't be overwritten");
                return emptyResult;
            }
            if (!file.exists()) {
                File parentFile = file.getParentFile();
                if (!parentFile.canWrite()) {
                    GUIUtils.warn("Dataset creation", "Can't create dataset in this directory");
                    return emptyResult;
                }
            }
            datasetFile = file;
            String fileString = file.getAbsoluteFile().toString();
            datasetFileTemp = new File(fileString + ".tmp");
            fileString = fileString.replace("\\", "/");
            script = script.replace("_DATASET_", "'" + fileString + "'");
            result = Optional.of(script);
        } else {
            result = Optional.of(script);
        }
        return result;
    }

    private String suggestDatasetName() {
        String datasetName = "";
        String filePath = getNMRData().getFilePath();
        File file = new File(filePath);
        String fileName = file.getName();
        if (fileName.toLowerCase().endsWith(".dx") || fileName.toLowerCase().endsWith(".jdx")) {
            datasetName = fileName;
        } else {
            File lastFile = NMRDataUtil.findNewestFile(getScriptDir().toPath());
            if (lastFile != null) {
                datasetName = lastFile.getName();
            } else {
                datasetName = getDatasetNameFromScript();
                if (datasetName.isEmpty()) {
                    datasetName = getNMRData().getSequence();
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

    public String[] getCombineMode() {
        if (getNMRData() == null) {
            return null;
        }
        int nDim = getNMRData().getNDim();
        String[] result = new String[nDim];
        for (int i = 1; i < nDim; i++) {
            acqMode[i] = "hyper";
        }

        for (Map.Entry<String, List<String>> entry : mapOpLists.entrySet()) {
            if (entry.getValue() != null) {
                ArrayList<String> scriptList = (ArrayList<String>) entry.getValue();
                if ((scriptList != null) && (!scriptList.isEmpty())) {
                    for (String string : scriptList) {
                        if (string.contains("TDCOMB")) {
                            Map<String, String> values = PropertyManager.parseOpString(string);
                            if (values != null) {
                                int dim = 1;
                                if (values.containsKey("dim")) {
                                    String value = values.get("dim");
                                    dim = Integer.parseInt(value) - 1;
                                }
                                if (values.containsKey("coef")) {
                                    String value = values.get("coef");
                                    value = value.replace("'", "");
                                    acqMode[dim] = value;
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    public boolean hasCommands() {
        return !mapOpLists.isEmpty();
    }

    public String getScriptCmds(int nDim, String indent, boolean includeRun) {
        String lineSep = System.lineSeparator();
        StringBuilder scriptBuilder = new StringBuilder();
        // List<String> oldList = new ArrayList<>();
        // oldList.addAll(fxmlController.getOperationList());
        // dimOpLists[vecDim] = oldList;
        int nDatasetDims = 0;
        mapToDataset = new int[nDim];
        for (Map.Entry<String, List<String>> entry : mapOpLists.entrySet()) {
            if (entry.getValue() != null) {
                String dimMode = entry.getKey().substring(0, 1);
                String parDim = entry.getKey().substring(1);
                if (dimMode.equals("D")) {
                    int dimNum = -1;
                    try {
                        dimNum = Integer.parseInt(parDim) - 1;
                        if (dimNum >= nDim) {
                            break;
                        }
                        mapToDataset[dimNum] = -1;
                    } catch (NumberFormatException nFE) {
                        log.warn("Unable to parse dimension number.", nFE);
                    }
                    if (!processorController.refManager.getSkip(parDim)) {
                        if (dimMode.equals("D") && (dimNum != -1)) {
                            mapToDataset[dimNum] = nDatasetDims++;
                        }
                    }
                }
                ArrayList<String> scriptList = (ArrayList<String>) entry.getValue();
                if ((scriptList != null) && (!scriptList.isEmpty())) {
                    if (parDim.equals("_ALL")) {
                        parDim = "";
                    }
                    scriptBuilder.append(indent).append("DIM(").append(parDim).append(")");
                    scriptBuilder.append(lineSep);
                    for (String string : scriptList) {
                        scriptBuilder.append(indent).append(string);
                        scriptBuilder.append(lineSep);
                    }
                }
            }
        }
        if (includeRun) {
            scriptBuilder.append(indent).append("run()");
        }
        return scriptBuilder.toString();
    }

    void setFlags() {
        Map<String, Boolean> flags = new HashMap<>();
        String flagString = processorController.getFlagString().trim();
        String[] flagStrings = flagString.split("\\s");
        for (String flag : flagStrings) {
            String[] flagParts = flag.split("=");
            if (flagParts.length == 2) {
                if (flagParts[0].equals("mode")) {
                    //    if (flagParts[1].equals("ea")) {
                    //       acqMode[vecDim] = "ea";
                    //  } else if (flagParts[1].equals("hc")) {
                    //     acqMode[vecDim] = "hc";
                    //} else {
                    //   acqMode[vecDim] = "";
                    //}
                } else {
                    boolean flagValue = flagParts[1].equals("1");
                    flags.put(flagParts[0], flagValue);
                }
            }
        }
        setFlags(flags);
    }

    public void setFlags(Map<String, Boolean> flags) {
        getNMRData().setFidFlags(flags);
    }

    void updateCounter() {
        NMRData nmrData = getNMRData();
        String[] acqOrder = nmrData.getAcqOrder();

        int nDim = nmrData.getNDim();
        int nArray = 0;
        for (String acqOrderElem : acqOrder) {
            if ((acqOrderElem.length() > 0) && (acqOrderElem.charAt(0) == 'a')) {
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

        int[] tdSizes = new int[nDim + nArray];
        boolean[] complex = new boolean[nDim + nArray];
        int j = 0;
        for (int i = 0; i < nDim; i++) {
            tdSizes[j] = nmrData.getSize(i);
            complex[j] = nmrData.isComplex(i);

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
            multiVecCounter = new MultiVecCounter(tdSizes, complex, acqOrder, nDim + nArray);
            vectorsPerGroup = multiVecCounter.getGroupSize();
        } else {
            vectorsPerGroup = 1;
        }
        fxmlController.updateRowDimMenu(nDim);
    }

    public void setData(NMRData data, boolean clearOps) {
        setNMRData(data);
        setDatasetType(data.getPreferredDatasetType());

        datasetFile = null;
        datasetFileTemp = null;
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("fixdsp", fixDSP);
        setFlags(flags);
        updateCounter();
        int nDim = getNMRData().getNDim();
        acqMode = new String[nDim];

        if ((mapOpLists == null) || (mapOpLists.size() != nDim)) {
            mapOpLists = new TreeMap<>(new DimComparator());
        }
        Map<String, List<String>> listOfScripts = getScriptList();
        List<String> saveHeaderList = new ArrayList<>();
        saveHeaderList.addAll(headerList);

        // when setting data reset vecdim back to 0 as it could have been set to
        // a value higher than the number of dimensions
        vecDim = 0;
        vecDimName = "D1";
        boolean[] complex = new boolean[nDim];
        for (int iDim = 0; iDim < nDim; iDim++) {
            complex[iDim] = data.isComplex(iDim);
        }
        processorController.updateDimChoice(complex);
        reloadData();
        processorController.refManager.resetData();
        processorController.refManager.setupItems(0);
        processorController.updateParTable(data);
        if (!clearOps) {
            setScripts(saveHeaderList, listOfScripts);
        }
        NMRDataUtil.setCurrentData(data);
        addFIDToPython();
    }

    public void reloadData() {
        chart.setPh0(0);
        chart.setPh1(0);
        chart.setPivot(null);
        NMRData nmrData = getNMRData();
        int nDim = nmrData.getNDim();
        iVec = 0;
        execScript("", false, false);
        if ((nmrData instanceof NMRViewData) && !nmrData.isFID()) {
            NMRViewData nvData = (NMRViewData) nmrData;
            chart.setDataset(nvData.getDataset());
//            chart.datasetAttributes = null;
            chart.setCrossHairState(true, true, true, true);
            int[] sizes = new int[0];
            processorController.vectorStatus(sizes, vecDim);
        } else {
            chart.controller.isFID = true;

//            chart.setDataset(null);
//            chart.datasetAttributes = null;
            loadVectors(0);
            chart.setCrossHairState(false, true, false, true);
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
            processorController.vectorStatus(sizes, vecDim);
        }
        chart.full();
        chart.autoScale();

        fxmlController.getPhaser().setPH0Slider(chart.getPh0());
        fxmlController.getPhaser().setPH1Slider(chart.getPh1());
        processorController.clearOperationList();
        chart.layoutPlotChildren();
    }

    public void execScriptList(boolean reloadData) {
        if (vecDimName.startsWith("D") && (vecDimName.indexOf(',') == -1)) {
            execScript(processorController.getScript(), true, reloadData);
        }
    }

    public void execScript(String script, boolean doProcess, boolean reloadData) {
        Processor.getProcessor().clearProcessorError();
        ProcessOps process = getProcess();
        process.clearOps();
        if (processorController == null) {
            log.info("null processor controller.");
            return;
        }
        if (processorController.isViewingDataset()) {
            return;
        }
        NMRData nmrData = getNMRData();
        try {
            if (nmrData != null) {
                NMRDataUtil.setCurrentData(nmrData);
            }
            interpreter.exec("useLocal()");
            if (nmrData != null) {
                interpreter.exec("fidInfo = makeFIDInfo()");
            }
            if ((nmrData instanceof NMRViewData) && !nmrData.isFID()) {
                return;
            }
            if (processorController.refManager == null) {
                log.info("null ref manager");
                return;
            }
            processorController.clearProcessingTextLabel();
            if (nmrData != null) {
                String parString = processorController.refManager.getParString(nmrData.getNDim(), "");
                interpreter.exec(parString);
            }
            if (reloadData) {
                loadVectors(0);
            }
            interpreter.exec(script);
        } catch (Exception pE) {
            if (pE instanceof IncompleteProcessException) {
                OperationListCell.failedOperation(((IncompleteProcessException) pE).index);
                processorController.setProcessingStatus(pE.getMessage(), false, pE);
            } else if (pE instanceof PyException) {
                PyException pyE = (PyException) pE;
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
            return;
        }
        if (doProcess) {
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
                    OperationListCell.resetCells();
                    process.exec();
                } catch (IncompleteProcessException e) {
                    OperationListCell.failedOperation(e.index);
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
            if (!processorController.isViewingDataset()) {
                chart.layoutPlotChildren();
            }

        }
    }

    public void addFIDToPython() {
        interpreter.exec("from pyproc import *");
        interpreter.exec("useLocal()");
        interpreter.exec("fidInfo = makeFIDInfo()");

    }

    public String getGenScript(boolean arrayed) {
        addFIDToPython();
        String arrayVal = arrayed ? "True" : "False";
        PyObject pyDocObject = interpreter.eval("genScript(arrayed=" + arrayVal + ")");
        String scriptString = (String) pyDocObject.__tojava__(String.class);
        return scriptString;
    }

    public Object getInterpVariable(String name) {
        try {
            return interpreter.get(name);
        } catch (Exception e) {
            return null;
        }
    }

    public ArrayList getDocs() {
        return pyDocs;
    }

    public ProcessorController getProcessorController() {
        return processorController;
    }
}
