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
package org.nmrfx.analyst.gui.tools;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.table.ColumnFilter;
import org.controlsfx.control.table.TableFilter;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetException;
import org.nmrfx.processor.datasets.DatasetMerger;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.gui.ChartProcessor;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.ProcessorController;
import org.nmrfx.processor.gui.controls.FileTableItem;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.utils.FormatUtils;
import org.nmrfx.utils.GUIUtils;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @author Bruce Johnson
 */
public class ScanTable {

    private static final Logger log = LoggerFactory.getLogger(ScanTable.class);

    ScannerTool scannerTool;
    TableView<FileTableItem> tableView;
    TableFilter<FileTableItem> fileTableFilter;
    TableFilter.Builder<FileTableItem> builder = null;
    File scanDir = null;

    PopOver popOver = new PopOver();
    ObservableList<FileTableItem> fileListItems = FXCollections.observableArrayList();
    HashMap<String, String> columnTypes = new HashMap<>();
    HashMap<String, String> columnDescriptors = new HashMap<>();
    boolean processingTable = false;
    Set<String> groupNames = new TreeSet<>();
    Map<String, Map<String, Integer>> groupMap = new HashMap<>();
    int groupSize = 1;
    ListChangeListener<FileTableItem> filterItemListener = c -> {
        getGroups();
        selectionChanged();
    };
    ListChangeListener<Integer> selectionListener;

    static final List<String> standardHeaders = List.of("path", "sequence", "row", "etime", "ndim");

    static final Color[] COLORS = new Color[17];
    static final double[] hues = {0.0, 0.5, 0.25, 0.75, 0.125, 0.375, 0.625, 0.875, 0.0625, 0.1875, 0.3125, 0.4375, 0.5625, 0.6875, 0.8125, 0.9375};

    static {
        COLORS[0] = Color.BLACK;
        int i = 1;
        double brightness = 0.8;
        for (double hue : hues) {
            if (i > 8) {
                brightness = 0.5;
            } else if (i > 4) {
                brightness = 0.65;
            }
            COLORS[i++] = Color.hsb(hue * 360.0, 0.9, brightness);
        }

    }

    public ScanTable(ScannerTool controller, TableView<FileTableItem> tableView) {
        this.scannerTool = controller;
        this.tableView = tableView;
        init();
    }

    private void init() {
        TableColumn<FileTableItem, String> fileColumn = new TableColumn<>("FileName");
        TableColumn<FileTableItem, String> seqColumn = new TableColumn<>("Sequence");
        TableColumn<FileTableItem, String> nDimColumn = new TableColumn<>("nDim");
        TableColumn<FileTableItem, Long> dateColumn = new TableColumn<>("Date");

        fileColumn.setCellValueFactory((e) -> new SimpleStringProperty(e.getValue().getFileName()));
        seqColumn.setCellValueFactory((e) -> new SimpleStringProperty(e.getValue().getSeqName()));
        nDimColumn.setCellValueFactory((e) -> new SimpleStringProperty(String.valueOf(e.getValue().getNDim())));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("Date"));

        tableView.getColumns().addAll(fileColumn, seqColumn, nDimColumn, dateColumn);
        setDragHandlers(tableView);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        selectionListener = c -> selectionChanged();
        tableView.getSelectionModel().getSelectedIndices().addListener(selectionListener);
        columnTypes.put("path", "S");
        columnTypes.put("sequence", "S");
        columnTypes.put("ndim", "I");
        columnTypes.put("row", "I");
        columnTypes.put("dataset", "S");
        columnTypes.put("etime", "I");
        columnTypes.put("group", "I");

    }

    public void refresh() {
        tableView.refresh();
    }

    public static List<String> getStandardHeaders() {
        return standardHeaders;
    }

    final protected String getActiveDatasetName() {
        PolyChart chart = scannerTool.getChart();
        DatasetBase dataset = chart.getDataset();
        String name = "";
        if (dataset != null) {
            name = dataset.getName();
        }
        return name;
    }

    final protected void selectionChanged() {
        if (processingTable) {
            return;
        }
        Map<Integer, Color> colorMap = new HashMap<>();
        Map<Integer, Double> offsetMap = new HashMap<>();
        Set<Integer> groupSet = new HashSet<>();
        List<Integer> selected = tableView.getSelectionModel().getSelectedIndices();
        PolyChart chart = scannerTool.getChart();
        ProcessorController processorController = chart.getProcessorController(false);
        if ((processorController == null)
                || processorController.isViewingDataset()
                || !processorController.isVisible()) {
            List<Integer> showRows = new ArrayList<>();
            if (selected.isEmpty()) {
                for (int i = 0, n = tableView.getItems().size(); i < n; i++) {
                    showRows.add(i);
                }
            } else {
                showRows.addAll(selected);
            }
            Optional<Double> curLvl = Optional.empty();
            if (!chart.getDatasetAttributes().isEmpty()) {
                DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                curLvl = Optional.of(dataAttr.getLvl());
            }

            List<Integer> rows = new ArrayList<>();
            List<String> datasetNames = new ArrayList<>();
            for (Integer index : showRows) {
                FileTableItem fileTableItem = tableView.getItems().get(index);
                Integer row = fileTableItem.getRow();
                String datasetColumnValue = fileTableItem.getDatasetName();
                if (datasetColumnValue.isEmpty()) {
                    continue;
                }
                File datasetFile = new File(scanDir, fileTableItem.getDatasetName());
                String datasetName = datasetFile.getName();

                int iGroup = fileTableItem.getGroup();
                groupSet.add(iGroup);
                Color color = getGroupColor(iGroup);
                double offset = iGroup * 1.0 / groupSize * 0.8;
                DatasetBase dataset = chart.getDataset();
                if ((dataset == null) || (chart.getDatasetAttributes().size() != 1) || !dataset.getName().equals(datasetName)) {
                    dataset = Dataset.getDataset(datasetName);
                    if (dataset == null) {
                        FXMLController.getActiveController().openDataset(datasetFile, false, true);
                    }
                }
                if (!datasetNames.contains(datasetName)) {
                    datasetNames.add(datasetName);
                }
                if (row != null) {
                    colorMap.put(row - 1, color);
                    offsetMap.put(row - 1, offset);
                    rows.add(row - 1); // rows index from 1
                }
            }
            chart.updateDatasets(datasetNames);
            if (chart.getDatasetAttributes().size() == 1) {
                DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                curLvl.ifPresent(dataAttr::setLvl);
                int nDim = dataAttr.nDim;
                chart.full(nDim - 1);
                if ((nDim - dataAttr.getDataset().getNFreqDims()) == 1){
                    chart.setDrawlist(rows);
                } else{
                    chart.clearDrawlist();
                }
                dataAttr.setMapColors(colorMap);
                if (groupSet.size() > 1) {
                    dataAttr.setMapOffsets(offsetMap);
                } else {
                    dataAttr.clearOffsets();
                }
                int curMode = chart.getController().getStatusBar().getMode();
                if (curMode != nDim) {
                    chart.getController().getStatusBar().setMode(nDim);
                }
            } else if (chart.getDatasetAttributes().size() == rows.size()) {
                for (int i=0;i< chart.getDatasetAttributes().size();i++) {
                    var dataAttr = chart.getDatasetAttributes().get(i);
                    dataAttr.setPosColor(colorMap.get(i));
                    curLvl.ifPresent(dataAttr::setLvl);
                }
            }
            chart.refresh();
        } else {
            openSelectedListFile();
        }
    }

    final protected void setDragHandlers(Node mouseNode
    ) {

        mouseNode.setOnDragOver(this::mouseDragOver);
        mouseNode.setOnDragDropped(this::mouseDragDropped);
        mouseNode.setOnDragExited((DragEvent event) -> mouseNode.setStyle("-fx-border-color: #C6C6C6;"));
    }

    private void mouseDragDropped(final DragEvent e) {
        final Dragboard db = e.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            success = true;

            // Only get the first file from the list
            final File file = db.getFiles().get(0);
            if (file.isDirectory()) {
                scanDir = file;
                Platform.runLater(() -> {
                    ArrayList<String> nmrFiles = NMRDataUtil.findNMRDirectories(scanDir.getAbsolutePath());
                    String[] headers = {};
                    initTable();
                    loadScanFiles(nmrFiles);
                });
            } else {
                Platform.runLater(() -> loadScanTable(file));

            }
        }
        e.setDropCompleted(success);
        e.consume();
    }

    private void mouseDragOver(final DragEvent e) {
        final Dragboard db = e.getDragboard();

        List<File> files = db.getFiles();
        if (db.hasFiles()) {
            if (files.size() > 0) {
                boolean isAccepted = false;
                if (files.get(0).isDirectory()) {
                    isAccepted = true;
                } else if (files.get(0).toString().endsWith(".txt")) {
                    isAccepted = true;
                }
                if (isAccepted) {
                    tableView.setStyle("-fx-border-color: green;"
                            + "-fx-border-width: 1;");
                    e.acceptTransferModes(TransferMode.COPY);
                }
            }
        } else {
            e.consume();
        }
    }

    public void setScanDirectory(File selectedDir) {
        scanDir = selectedDir;
    }

    public void loadScanFiles(Stage stage) {
        DirectoryChooser dirChooser = new DirectoryChooser();
        File scanDirFile = dirChooser.showDialog(null);
        if (scanDirFile == null) {
            return;
        }
        scanDir = scanDirFile;
        ArrayList<String> nmrFiles = NMRDataUtil.findNMRDirectories(scanDir.getAbsolutePath());
        String[] headers = {};
        processingTable = true;
        try {
            initTable();
            fileListItems.clear();
            loadScanFiles(nmrFiles);
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        } finally {
            processingTable = false;
        }
    }

    public void processScanDir(ChartProcessor chartProcessor, boolean combineFileMode) {
        if ((chartProcessor == null) || !chartProcessor.hasCommands()) {
            GUIUtils.warn("Scanner Error", "Processing Script Not Configured");
            return;
        }

        if ((scanDir == null) || scanDir.toString().equals("")) {
            GUIUtils.warn("Scanner Error", "No scan directory");
            return;
        }
        String outDirName = GUIUtils.input("Output directory name", "output");
        Path outDirPath = Paths.get(scanDir.toString(), outDirName);
        File scanOutputDir = outDirPath.toFile();
        if (!scanOutputDir.exists() && !scanOutputDir.mkdir()) {
            GUIUtils.warn("Scanner Error", "Could not create output dir");
            return;
        }

        String combineFileName = GUIUtils.input("Output file name", "process");

        if (!scanOutputDir.exists() || !scanOutputDir.isDirectory() || !scanOutputDir.canWrite()) {
            GUIUtils.warn("Scanner Error", "Output dir is not a writable directory");
            return;
        }
        ObservableList<FileTableItem> fileTableItems = tableView.getItems();
        if (fileTableItems.isEmpty()) {
            return;
        }

        if (!combineFileName.contains(".")) {
            combineFileName += ".nv";
        }

        String fileRoot = combineFileName;
        if (fileRoot.contains(".")) {
            fileRoot = fileRoot.substring(0, fileRoot.lastIndexOf("."));
        }

        PolyChart chart = scannerTool.getChart();
        processingTable = true;
        try (PythonInterpreter processInterp = new PythonInterpreter()) {
            List<String> fileNames = new ArrayList<>();

            String initScript = ChartProcessor.buildInitScript();
            processInterp.exec(initScript);

            int nDim = fileTableItems.get(0).getNDim();
            String processScript = chartProcessor.buildScript(nDim);

            int rowNum = 1;
            for (FileTableItem fileTableItem : fileTableItems) {
                File fidFile = new File(scanDir, fileTableItem.getFileName());
                String fidFilePath = fidFile.getAbsolutePath();
                File datasetFile = new File(scanOutputDir, fileRoot + rowNum + ".nv");
                String datasetFilePath = datasetFile.getAbsolutePath();
                String fileScript = ChartProcessor.buildFileScriptPart(fidFilePath, datasetFilePath);
                processInterp.exec(FormatUtils.formatStringForPythonInterpreter(fileScript));
                processInterp.exec(processScript);
                fileNames.add(datasetFilePath);
                fileTableItem.setRow(rowNum++);
                if (combineFileMode) {
                    fileTableItem.setDatasetName(outDirName + "/" + combineFileName);
                } else {
                    fileTableItem.setDatasetName(outDirName + "/" + datasetFile.getName());
                }
            }
            updateFilter();
            if (combineFileMode) {
                // merge datasets into single pseudo-nd dataset
                DatasetMerger merger = new DatasetMerger();
                File mergedFile = new File(scanOutputDir, combineFileName);
                String mergedFilepath = mergedFile.getAbsolutePath();
                try {
                    // merge all the 1D files into a pseudo 2D file
                    merger.merge(fileNames, mergedFilepath);
                    // After merging, remove the 1D files
                    for (String fileName : fileNames) {
                        File file = new File(fileName);
                        FXMLController.getActiveController().closeFile(file);
                        Files.deleteIfExists(file.toPath());
                        String parFileName = fileName.substring(0, fileName.lastIndexOf(".")) + ".par";
                        File parFile = new File(parFileName);
                        Files.deleteIfExists(parFile.toPath());
                    }

                    // load merged dataset
                    FXMLController.getActiveController().openDataset(mergedFile, false, true);
                    List<Integer> rows = new ArrayList<>();
                    rows.add(0);
                    chart.setDrawlist(rows);
                } catch (IOException | DatasetException ex) {
                    ExceptionDialog eDialog = new ExceptionDialog(ex);
                    eDialog.showAndWait();
                }
            } else {
                // load first output dataset
                File datasetFile = new File(scanOutputDir, fileRoot + 1 + ".nv");
                FXMLController.getActiveController().openDataset(datasetFile, false, true);
            }
            chart.full();
            chart.autoScale();

            File saveTableFile = new File(scanDir, "scntbl.txt");
            saveScanTable(saveTableFile);
            scannerTool.miner.setDisableSubMenus(!combineFileMode);

        } finally {
            processingTable = false;
        }
    }

    public void openSelectedListFile() {
        int selItem = tableView.getSelectionModel().getSelectedIndex();
        if (selItem >= 0) {
            if ((scanDir == null) || scanDir.toString().isBlank()) {
                return;
            }
            ProcessorController processorController = scannerTool.getChart().getProcessorController(true);
            if (processorController != null) {
                String scriptString = processorController.getCurrentScript();
                FileTableItem fileTableItem = (FileTableItem) tableView.getItems().get(selItem);
                String fileName = fileTableItem.getFileName();
                String filePath = Paths.get(scanDir.getAbsolutePath(), fileName).toString();

                scannerTool.getChart().getFXMLController().openFile(filePath, false, false);

                processorController.parseScript(scriptString);
            }

        }
    }

    private void loadScanFiles(ArrayList<String> nmrFiles) {
        fileListItems.clear();
        long firstDate = Long.MAX_VALUE;
        List<FileTableItem> items = new ArrayList<>();
        for (String filePath : nmrFiles) {
            File file = new File(filePath);
            NMRData nmrData = null;
            try {
                nmrData = NMRDataUtil.getFID(filePath);
            } catch (IOException ioE) {
                log.warn(ioE.getMessage(), ioE);

            }
            if (nmrData != null) {
                long date = nmrData.getDate();
                if (date < firstDate) {
                    firstDate = date;
                }
                Path relativePath = scanDir.toPath().relativize(file.toPath());
                items.add(new FileTableItem(relativePath.toString(), nmrData.getSequence(), nmrData.getNDim(), nmrData.getDate(), 0, ""));
            }
        }
        items.sort(Comparator.comparingLong(FileTableItem::getDate));
        final long firstDate2 = firstDate;
        items.forEach((FileTableItem item) -> {
            item.setDate(item.getDate() - firstDate2);
            fileListItems.add(item);
        });

        fileTableFilter.resetFilter();
    }

    public void loadScanTable() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(("Open Table File"));
        File file = fileChooser.showOpenDialog(popOver);
        if (file != null) {
            loadScanTable(file);
        }
    }

    public File getScanDir() {
        return scanDir;
    }

    public void loadFromDataset() {
        PolyChart chart = scannerTool.getChart();
        DatasetBase dataset = chart.getDataset();
        if (dataset == null) {
            log.warn("Unable to load dataset, dataset is null.");
            return;
        }
        if (dataset.getNDim() < 2) {
            log.warn("Unable to load dataset, dataset only has 1 dimension.");
            return;
        }
        scanDir = null;
        // Need to disconnect listeners before updating fileListItem or the selectionListener and filterItemListeners
        // will be triggered during every iteration of the loop, greatly reducing performance
        tableView.getSelectionModel().getSelectedIndices().removeListener(selectionListener);
        tableView.getItems().removeListener(filterItemListener);
        fileListItems.clear();
        int nRows = dataset.getSizeTotal(1);
        HashMap<String, String> fieldMap = new HashMap();
        double[] values = dataset.getValues(1);
        for (int iRow = 0; iRow < nRows; iRow++) {
            double value = 0;
            if ((values != null) && (iRow < values.length)) {
                value = values[iRow];
            }
            long eTime = (long) (value * 1000);
            fileListItems.add(new FileTableItem(dataset.getName(), "", 1, eTime, iRow + 1, dataset.getName(), fieldMap));
        }
        tableView.getSelectionModel().getSelectedIndices().addListener(selectionListener);
        tableView.getItems().addListener(filterItemListener);
        String[] headers = {};
        boolean[] notDouble = new boolean[0];
        boolean[] notInteger = new boolean[0];

        for (int i = 0; i < headers.length; i++) {
            if (!notInteger[i]) {
                columnTypes.put(headers[i], "I");
            } else if (!notDouble[i]) {
                columnTypes.put(headers[i], "D");
            } else {
                columnTypes.put(headers[i], "S");
            }
        }
        columnTypes.put("path", "S");
        columnTypes.put("sequence", "S");
        columnTypes.put("ndim", "I");
        columnTypes.put("row", "I");
        columnTypes.put("dataset", "S");
        columnTypes.put("etime", "I");
        columnTypes.put("group", "I");
        Long firstDate = 0L;
        for (FileTableItem item : fileListItems) {
            item.setDate(item.getDate() - firstDate);
            item.setTypes(headers, notDouble, notInteger);
        }
        initTable();
        addHeaders(headers);
        fileTableFilter.resetFilter();
        List<Integer> rows = new ArrayList<>();
        rows.add(0);
        // Load from Dataset assumes an arrayed dataset
        dataset.setNFreqDims(dataset.getNDim() - 1);
        if (dataset.getNDim() > 2) {
            chart.getDisDimProperty().set(PolyChart.DISDIM.TwoD);
        } else {
            chart.getDisDimProperty().set(PolyChart.DISDIM.OneDX);
        }
        chart.setDrawlist(rows);
        chart.full();
        chart.autoScale();
    }

    private void loadScanTable(File file) {
        long firstDate = Long.MAX_VALUE;
        int iLine = 0;
        String[] headers = null;
        HashMap<String, String> fieldMap = new HashMap();
        boolean[] notDouble = null;
        boolean[] notInteger = null;
        String firstDatasetName = "";
        if ((scanDir == null) || scanDir.toString().isBlank()) {
            setScanDirectory(file.getParentFile());
        }

        processingTable = true;
        boolean combineFileMode = true;
        try {
            fileListItems.clear();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (iLine == 0) {
                        headers = line.split("\t");
                        notDouble = new boolean[headers.length];
                        notInteger = new boolean[headers.length];
                    } else {
                        String[] fields = line.split("\t");
                        for (int iField = 0; iField < fields.length; iField++) {
                            fields[iField] = fields[iField].trim();
                            try {
                                 Integer.parseInt(fields[iField]);
                            } catch (NumberFormatException nfE) {
                                notInteger[iField] = true;
                                try {
                                     Double.parseDouble(fields[iField]);
                                } catch (NumberFormatException nfE2) {
                                    notDouble[iField] = true;
                                }
                            }
                            fieldMap.put(headers[iField], fields[iField]);
                        }
                        boolean hasAll = true;
                        int nDim = 1;
                        long eTime = 0;
                        String sequence = "";
                        int row = 0;
                        for (String standardHeader : standardHeaders) {
                            if (!fieldMap.containsKey(standardHeader)) {
                                hasAll = false;
                            } else {
                                switch (standardHeader) {
                                    case "ndim":
                                        nDim = Integer.parseInt(fieldMap.get(standardHeader));
                                        break;
                                    case "row":
                                        row = Integer.parseInt(fieldMap.get(standardHeader));
                                        break;
                                    case "etime":
                                        eTime = Long.parseLong(fieldMap.get(standardHeader));
                                        break;
                                    case "sequence":
                                        sequence = fieldMap.get(standardHeader);
                                        break;
                                }
                            }
                        }
                        String fileName = fieldMap.get("path");
                        String datasetName = "";
                        if (fieldMap.containsKey("dataset")) {
                            datasetName = fieldMap.get("dataset");
                            if (firstDatasetName.equals("")) {
                                firstDatasetName = datasetName;
                            } else if (!firstDatasetName.equals(datasetName)) {
                                combineFileMode = false;
                            }
                        }

                        if (!hasAll) {
                            if ((fileName == null) || (fileName.length() == 0)) {
                                log.info("No path field or value");
                                return;
                            }
                            if ((scanDir == null) || scanDir.toString().isBlank()) {
                                return;
                            }
                            Path filePath = FileSystems.getDefault().getPath(scanDir.toString(), fileName);

                            NMRData nmrData;
                            try {
                                nmrData = NMRDataUtil.getFID(filePath.toString());
                            } catch (IOException ioE) {
                                return;
                            }

                            if (nmrData != null) {
                                if (!fieldMap.containsKey("etime")) {
                                    eTime = nmrData.getDate();
                                }
                                if (!fieldMap.containsKey("sequence")) {
                                    sequence = nmrData.getSequence();
                                }
                                if (!fieldMap.containsKey("ndim")) {
                                    nDim = nmrData.getNDim();
                                }
                            }
                        }
                        if (eTime < firstDate) {
                            firstDate = eTime;
                        }
                       var item = new FileTableItem(fileName, sequence, nDim, eTime, row, datasetName, fieldMap);
                        fileListItems.add(item);
                    }

                    iLine++;
                }
            } catch (IOException ioE) {
                log.warn(ioE.getMessage(), ioE);
            }
            for (int i = 0; i < headers.length; i++) {
                if (!notInteger[i]) {
                    columnTypes.put(headers[i], "I");
                } else if (!notDouble[i]) {
                    columnTypes.put(headers[i], "D");
                } else {
                    columnTypes.put(headers[i], "S");
                }
            }
            columnTypes.put("path", "S");
            columnTypes.put("sequence", "S");
            columnTypes.put("ndim", "I");
            columnTypes.put("row", "I");
            columnTypes.put("dataset", "S");
            columnTypes.put("etime", "I");

            for (FileTableItem item : fileListItems) {
                item.setDate(item.getDate() - firstDate);
                item.setTypes(headers, notDouble, notInteger);
            }
            initTable();
            addHeaders(headers);
            fileTableFilter.resetFilter();
            if (firstDatasetName.length() > 0) {
                File parentDir = file.getParentFile();
                Path path = FileSystems.getDefault().getPath(parentDir.toString(), firstDatasetName);
                Dataset firstDataset = FXMLController.getActiveController().openDataset(path.toFile(), false, true);
                // If there is only one unique dataset name, assume an arrayed experiment
                List<String> uniqueDatasetNames = new ArrayList<>(fileListItems.stream().map(FileTableItem::getDatasetName).collect(Collectors.toSet()));
                if (uniqueDatasetNames.size() == 1 && uniqueDatasetNames.get(0) != null && !uniqueDatasetNames.get(0).equals("")) {
                    firstDataset.setNFreqDims(firstDataset.getNDim() - 1);
                }
                PolyChart chart = scannerTool.getChart();
                List<Integer> rows = new ArrayList<>();
                rows.add(0);
                chart.setDrawlist(rows);
                chart.full();
                chart.autoScale();
            }
            addGroupColumn();
            scannerTool.miner.setDisableSubMenus(!combineFileMode);

        } catch (NumberFormatException e) {
            log.warn(e.getMessage(), e);
        } finally {
            processingTable = false;
        }
    }

    public void saveScanTable() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Table File");
        if (scanDir != null) {
            fileChooser.setInitialDirectory(scanDir);
        }
        File file = fileChooser.showSaveDialog(popOver);
        if (file != null) {
            saveScanTable(file);
        }
    }

    public TableView getTableView() {
        return tableView;
    }

    public List<String> getHeaders() {
        ObservableList<TableColumn<FileTableItem, ?>> columns = tableView.getColumns();
        List<String> headers = new ArrayList<>();
        for (TableColumn column : columns) {
            String name = column.getText();
            headers.add(name);
        }
        return headers;
    }

    private void saveScanTable(File file) {
        Charset charset = Charset.forName("US-ASCII");
        List<String> headers = getHeaders();

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), charset)) {
            boolean first = true;
            for (String header : headers) {
                if (!first) {
                    writer.write('\t');
                } else {
                    first = false;
                }
                writer.write(header, 0, header.length());
            }
            for (FileTableItem item : tableView.getItems()) {
                writer.write('\n');
                String s = item.toString(headers, columnTypes);
                writer.write(s, 0, s.length());
            }
        } catch (IOException x) {
            log.warn(x.getMessage(), x);
        }
    }

    public String getNextColumnName(String name, String columnDescriptor) {
        String columnName = columnDescriptors.get(columnDescriptor);
        int maxColumn = -1;
        if (columnName == null) {
            if (!name.equals("")) {
                columnName = name;
            } else {
                for (String columnType : columnTypes.keySet()) {
                    int columnNum;
                    if (columnType.startsWith("V.")) {
                        try {
                            int colonPos = columnType.indexOf(":");
                            columnNum = Integer.parseInt(columnType.substring(2, colonPos));
                            if (columnNum > maxColumn) {
                                maxColumn = columnNum;
                            }
                        } catch (NumberFormatException nfE) {
                            log.warn("Unable to parse column number.", nfE);
                        }
                    }
                }
                columnName = "V." + (maxColumn + 1);
            }
            columnDescriptors.put(columnDescriptor, columnName);
        }
        return columnName;
    }

    public void addGroupColumn() {
        addTableColumn("group", "I");
    }

    private List<String> headersMissing(String[] headerNames) {
        List<String> missing = new ArrayList<>();
        for (var headerName:headerNames) {
            if (!headerPresent(headerName)) {
                missing.add(headerName);
            }
        }
        return missing;
    }

    private boolean headerPresent(String headerName) {
        ObservableList<TableColumn<FileTableItem, ?>> columns = tableView.getColumns();
        boolean present = false;
        for (TableColumn column : columns) {
            String name = column.getText();
            if (name.equals(headerName)) {
                present = true;
                break;
            }
        }
        return present;
    }

    public void addTableColumn(String newName, String type) {
        if (!headerPresent(newName)) {
            columnTypes.put(newName, type);
            addColumn(newName);
        }
    }

    private void initTable() {
        TableColumn<FileTableItem, String> fileColumn = new TableColumn<>("path");
        TableColumn<FileTableItem, String> seqColumn = new TableColumn<>("sequence");
        TableColumn<FileTableItem, Number> nDimColumn = new TableColumn<>("ndim");
        TableColumn<FileTableItem, Long> dateColumn = new TableColumn<>("etime");
        TableColumn<FileTableItem, Number> rowColumn = new TableColumn<>("row");
        TableColumn<FileTableItem, String> datasetColumn = new TableColumn<>("dataset");

        fileColumn.setCellValueFactory((e) -> new SimpleStringProperty(e.getValue().getFileName()));
        seqColumn.setCellValueFactory((e) -> new SimpleStringProperty(e.getValue().getSeqName()));
        nDimColumn.setCellValueFactory((e) -> new SimpleIntegerProperty(e.getValue().getNDim()));
        rowColumn.setCellValueFactory((e) -> new SimpleIntegerProperty(e.getValue().getRow()));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("Date"));
        datasetColumn.setCellValueFactory((e) -> new SimpleStringProperty(e.getValue().getDatasetName()));

        TableColumn<FileTableItem, Number> groupColumn = new TableColumn<>("group");
        groupColumn.setCellValueFactory((e) -> new SimpleIntegerProperty(e.getValue().getGroup()));

        groupColumn.setCellFactory(column -> {
            return new TableCell<FileTableItem, Number>() {
                @Override
                protected void updateItem(Number group, boolean empty) {
                    super.updateItem(group, empty);
                    if (group == null || empty) {
                        setText(null);
                        setStyle("");
                    } else {
                        // Format date.
                        setText(String.valueOf(group));
                        setTextFill(getGroupColor(group.intValue()));
                    }
                }
            };
        });

        tableView.getColumns().clear();
        tableView.getColumns().addAll(fileColumn, seqColumn, nDimColumn, dateColumn, rowColumn, datasetColumn, groupColumn);
        updateFilter();

        for (TableColumn column : tableView.getColumns()) {
            setColumnGraphic(column);
            column.graphicProperty().addListener(e -> graphicChanged(column));
        }
    }

    private void addHeaders(String[] headers) {
        var missingHeaders = headersMissing(headers);
        for (var header:missingHeaders) {
            addColumn(header);
        }
    }

    private void addColumn(String header) {
        if (!headerPresent(header)) {
            String type = columnTypes.get(header);
            final TableColumn newColumn;
            if (type == null) {
                type = "S";
                log.info("No type for {}", header);
            }
            switch (type) {
                case "D":
                    TableColumn<FileTableItem, Number> doubleExtraColumn = new TableColumn<>(header);
                    newColumn = doubleExtraColumn;
                    doubleExtraColumn.setCellValueFactory((e) -> new SimpleDoubleProperty(e.getValue().getDoubleExtra(header)));
                    doubleExtraColumn.setCellFactory(col
                            -> new TableCell<FileTableItem, Number>() {
                        @Override
                        public void updateItem(Number value, boolean empty) {
                            super.updateItem(value, empty);
                            if (empty) {
                                setText(null);
                            } else {
                                setText(String.format("%.4f", value.doubleValue()));
                            }
                        }
                    });
                    tableView.getColumns().add(doubleExtraColumn);
                    break;
                case "I":
                    TableColumn<FileTableItem, Number> intExtraColumn = new TableColumn<>(header);
                    newColumn = intExtraColumn;
                    intExtraColumn.setCellValueFactory((e) -> new SimpleIntegerProperty(e.getValue().getIntegerExtra(header)));
                    tableView.getColumns().add(intExtraColumn);
                    break;
                default:
                    TableColumn<FileTableItem, String> extraColumn = new TableColumn<>(header);
                    newColumn = extraColumn;
                    extraColumn.setCellValueFactory((e) -> new SimpleStringProperty(String.valueOf(e.getValue().getExtra(header))));
                    tableView.getColumns().add(extraColumn);
                    break;
            }

            updateFilter();
            setColumnGraphic(newColumn);
            newColumn.graphicProperty().addListener(e -> graphicChanged(newColumn));
        }
    }

    private void graphicChanged(TableColumn column) {
        Node node = column.getGraphic();
        boolean isFiltered = (node != null) && !(node instanceof StackPane);
        if ((node == null) || isFiltered) {
            setColumnGraphic(column);
        }
    }

    private void setColumnGraphic(TableColumn column) {
        String text = column.getText().toLowerCase();
        String type = columnTypes.get(column.getText());
        if (!"D".equals(type) && isGroupable(text)) {
            boolean isGrouped = groupNames.contains(text);
            boolean isFiltered = isFiltered(column);
            StackPane stackPane = new StackPane();
            Rectangle rect = new Rectangle(10, 10);
            stackPane.getChildren().add(rect);
            Color color;
            if (isGrouped) {
                color = isFiltered ? Color.RED : Color.BLUE;
            } else {
                color = isFiltered ? Color.ORANGE : Color.WHITE;
            }
            rect.setFill(color);
            rect.setStroke(Color.BLACK);
            rect.setOnMousePressed(e -> hitColumnGrouper(e, rect, text));
            rect.setOnMouseReleased(Event::consume);
            rect.setOnMouseClicked(Event::consume);
            column.setGraphic(stackPane);
        } else if ("D".equals(type) || isData(text)) {
            StackPane stackPane = new StackPane();
            Rectangle rect = new Rectangle(10, 10);
            Line line1 = new Line(1, 1, 10, 10);
            Line line2 = new Line(1, 10, 10, 1);

            line1.setStroke(Color.BLACK);
            line2.setStroke(Color.BLACK);
            line1.setMouseTransparent(true);
            line2.setMouseTransparent(true);
            stackPane.getChildren().addAll(rect, line1, line2);
            rect.setFill(Color.WHITE);
            rect.setStroke(Color.BLACK);
            rect.setOnMousePressed(e -> hitDataDelete(e, column));
            rect.setOnMouseReleased(Event::consume);
            rect.setOnMouseClicked(Event::consume);
            column.setGraphic(stackPane);
        }
    }

    private boolean isFiltered(TableColumn column) {
        boolean filtered = false;
        Optional<ColumnFilter> opt = fileTableFilter.getColumnFilter(column);
        if (opt.isPresent()) {
            filtered = opt.get().isFiltered();
        }
        return filtered;
    }

    private boolean isGroupable(String text) {
        return !standardHeaders.contains(text) && !text.equals("group")
                && !text.contains(":") && !text.equals("dataset");
    }

    public boolean isData(String text) {
        return !standardHeaders.contains(text) && !text.equals("group")
                && text.contains(":") && !text.equals("dataset");
    }

    private void hitDataDelete(MouseEvent e, TableColumn column) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete column " + column.getText());
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                tableView.getColumns().remove(column);
            }
        });
    }

    private void hitColumnGrouper(MouseEvent e, Rectangle rect, String text) {
        e.consume();

        text = text.toLowerCase();
        if (groupNames.contains(text)) {
            groupNames.remove(text);
            rect.setFill(Color.WHITE);
        } else {
            groupNames.add(text);
            rect.setFill(Color.GREEN);
        }
        getGroups();

        selectionChanged();
        tableView.refresh();
    }

    public void saveFilters() {
        fileTableFilter.getColumnFilters();
    }

    public void updateFilter() {
        // Old listener must be removed before setting the items!
        tableView.getItems().removeListener(filterItemListener);
        tableView.setItems(fileListItems);
        builder = TableFilter.forTableView(tableView);
        fileTableFilter = builder.apply();
        fileTableFilter.resetFilter();
        tableView.getItems().addListener(filterItemListener);
        getGroups();
    }

    public ObservableList<FileTableItem> getItems() {
        return fileListItems;
    }

    public void makeGroupMap() {
        groupMap.clear();
        for (String groupName : groupNames) {
            Set<String> group = new TreeSet<>();
            for (FileTableItem item : tableView.getItems()) {
                String value = item.getExtraAsString(groupName);
                group.add(value);
            }
            Map<String, Integer> map = new HashMap<>();
            for (String value : group) {
                map.put(value, map.size());

            }
            groupMap.put(groupName, map);
        }
    }

    public static Color getGroupColor(int index) {
        index = Math.min(index, COLORS.length - 1);
        return COLORS[index];
    }

    public void getGroups() {
        for (var column : tableView.getColumns()) {
            setColumnGraphic(column);
        }
        makeGroupMap();
        int maxValue = 0;
        for (FileTableItem item : tableView.getItems()) {
            int mul = 1;
            int iValue = 0;
            for (String groupName : groupNames) {
                Map<String, Integer> map = groupMap.get(groupName);
                if (!map.isEmpty()) {
                    String value = item.getExtraAsString(groupName);
                    int index = map.get(value);
                    iValue += index * mul;
                    mul *= map.size();
                }
            }
            item.setGroup(iValue);
            maxValue = Math.max(maxValue, iValue);
        }
        groupSize = maxValue + 1;
    }
}
