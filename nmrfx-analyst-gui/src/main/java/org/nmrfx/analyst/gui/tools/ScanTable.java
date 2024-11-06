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

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.converter.IntegerStringConverter;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.table.ColumnFilter;
import org.controlsfx.control.table.TableFilter;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetException;
import org.nmrfx.processor.datasets.DatasetMerger;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.gui.ChartProcessor;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.ProcessorController;
import org.nmrfx.processor.gui.controls.FileTableItem;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.utils.ColorSchemes;
import org.nmrfx.processor.gui.utils.TableColors;
import org.nmrfx.processor.processing.Processor;
import org.nmrfx.utils.FormatUtils;
import org.nmrfx.utils.GUIUtils;
import org.nmrfx.utils.TableUtils;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.DoubleConsumer;
import java.util.stream.Collectors;

/**
 * @author Bruce Johnson
 */
public class ScanTable {

    private static final Logger log = LoggerFactory.getLogger(ScanTable.class);
    static final String PATH_COLUMN_NAME = "path";
    static final String SEQUENCE_COLUMN_NAME = "sequence";
    static final String ROW_COLUMN_NAME = "row";
    static final String ETIME_COLUMN_NAME = "etime";
    static final String NDIM_COLUMN_NAME = "ndim";
    static final String DATASET_COLUMN_NAME = "dataset";
    static final String GROUP_COLUMN_NAME = "group";
    static final String ACTIVE_COLUMN_NAME = "active";
    static final String COLOR_COLUMN_NAME = "Color";
    static final String POSITIVE_COLUMN_NAME = "Positive";
    static final String NEGATIVE_COLUMN_NAME = "Negative";
    static final String LVL_COLUMN_NAME = "Lvl";
    static final String CLM_COLUMN_NAME = "CLM";
    static final String NLVL_COLUMN_NAME = "NLevels";
    static final String OFFSET_COLUMN_NAME = "Offset";
    static final String SCANNER_ERROR = "Scanner Error";

    static final List<String> standardHeaders = List.of(PATH_COLUMN_NAME, SEQUENCE_COLUMN_NAME, ROW_COLUMN_NAME, ETIME_COLUMN_NAME, NDIM_COLUMN_NAME, ACTIVE_COLUMN_NAME);
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

    ScannerTool scannerTool;
    TableView<FileTableItem> tableView;
    TableFilter<FileTableItem> fileTableFilter;
    TableFilter.Builder<FileTableItem> builder = null;
    File scanDir = null;

    PopOver popOver = new PopOver();
    ObservableList<FileTableItem> fileListItems = FXCollections.observableArrayList();
    Map<String, DatasetAttributes> activeDatasetAttributes = new HashMap<>();
    HashMap<String, String> columnTypes = new HashMap<>();
    HashMap<String, String> columnDescriptors = new HashMap<>();
    boolean processingTable = false;
    Set<String> groupNames = new TreeSet<>();
    Map<String, Map<String, Integer>> groupMap = new HashMap<>();
    int groupSize = 1;
    ListChangeListener<FileTableItem> filterItemListener = c -> {
        getGroups();
        ensureAllDatasetsAdded();
        selectionChanged();
    };
    ListChangeListener<? super DatasetAttributes> datasetListener = c -> datasetsInChartChanged();

    ListChangeListener<Integer> selectionListener;
    PolyChart currentChart;
    Map<TableColumn<FileTableItem, ?>, ContextMenu> columnMenus = new HashMap<>();
    Set<TableColumn<FileTableItem,?>> columnsToCheckForMulti = new HashSet<>();


    public ScanTable(ScannerTool controller, TableView<FileTableItem> tableView) {
        this.scannerTool = controller;
        this.tableView = tableView;
        currentChart = controller.getChart();
        init();
    }

    private void init() {
        TableColumn<FileTableItem, String> fileColumn = new TableColumn<>("FileName");
        TableColumn<FileTableItem, String> seqColumn = new TableColumn<>(SEQUENCE_COLUMN_NAME);
        TableColumn<FileTableItem, String> nDimColumn = new TableColumn<>(NDIM_COLUMN_NAME);
        TableColumn<FileTableItem, Long> dateColumn = new TableColumn<>("Date");

        fileColumn.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getFileName()));
        seqColumn.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getSeqName()));
        nDimColumn.setCellValueFactory(e -> new SimpleStringProperty(String.valueOf(e.getValue().getNDim())));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("Date"));

        tableView.getColumns().addAll(fileColumn, seqColumn, nDimColumn, dateColumn);
        setDragHandlers(tableView);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        selectionListener = c -> selectionChanged();
        tableView.getSelectionModel().getSelectedIndices().addListener(selectionListener);
        columnTypes.put(PATH_COLUMN_NAME, "S");
        columnTypes.put(SEQUENCE_COLUMN_NAME, "S");
        columnTypes.put(NDIM_COLUMN_NAME, "I");
        columnTypes.put(ROW_COLUMN_NAME, "I");
        columnTypes.put(DATASET_COLUMN_NAME, "S");
        columnTypes.put(ETIME_COLUMN_NAME, "I");
        columnTypes.put(GROUP_COLUMN_NAME, "I");
        columnTypes.put(ACTIVE_COLUMN_NAME, "B");
        if (currentChart != null) {
            currentChart.getDatasetAttributes().addListener(datasetListener);
        }


    }

    public void refresh() {
        tableView.refresh();
    }

    private void datasetsInChartChanged() {
    }

    private void ensureAllDatasetsAdded() {
        PolyChart chart = scannerTool.getChart();
        List<String> datasetNames = new ArrayList<>();
        for (var fileTableItem : getItems()) {
            String datasetColumnValue = fileTableItem.getDatasetName();
            if (datasetColumnValue.isEmpty()) {
                continue;
            }

            String datasetPath = fileTableItem.getDatasetName();
            File file = new File(datasetPath);
            String datasetName = file.getName();
            if (!datasetNames.contains(datasetName)) {
                Dataset dataset = Dataset.getDataset(datasetName);
                if (dataset == null) {
                    File datasetFile = new File(scanDir, datasetPath);
                    if (datasetFile.exists()) {
                        dataset = AnalystApp.getFXMLControllerManager().getOrCreateActiveController().openDataset(datasetFile, false, true);
                    }
                }
                if (dataset != null) {
                    datasetNames.add(datasetName);
                }
            }
        }
        chart.updateDatasets(datasetNames);
    }

    private void setDatasetAttributes() {
        PolyChart chart = scannerTool.getChart();
        List<DatasetAttributes> datasetAttributesList = chart.getDatasetAttributes();
        activeDatasetAttributes.clear();
        for (var datasetAttributes : datasetAttributesList) {
            activeDatasetAttributes.put(datasetAttributes.getDataset().getName(), datasetAttributes);
        }
        for (var fileTableItem : getItems()) {
            String datasetPath = fileTableItem.getDatasetName();
            if (datasetPath == null) {
                continue;
            }
            File file = new File(datasetPath);
            String datasetName = file.getName();
            DatasetAttributes datasetAttributes = activeDatasetAttributes.get(datasetName);
            fileTableItem.setDatasetAttributes(datasetAttributes);
        }
    }

    boolean arrayed(DatasetAttributes datasetAttributes) {
        Dataset dataset = (Dataset) datasetAttributes.getDataset();
        int nFreqDim = dataset.getNFreqDims();
        int nDim = dataset.getNDim();
        return (nFreqDim != 0) && (nFreqDim < nDim);
    }
    private void setDatasetVisibility(List<FileTableItem> showRows, Double curLvl) {
        if (activeDatasetAttributes.isEmpty()) {
            ensureAllDatasetsAdded();
            setDatasetAttributes();
        }
        PolyChart chart = scannerTool.getChart();
        List<DatasetAttributes> datasetAttributesList = chart.getDatasetAttributes();
        List<Integer> rows = new ArrayList<>();
        Map<Integer, Double> offsetMap = new HashMap<>();
        Set<Integer> groupSet = new HashSet<>();
        datasetAttributesList.forEach(d -> d.setPos(false));
        boolean singleData = datasetAttributesList.size() == 1;
        if (singleData) {
            DatasetAttributes dataAttr = datasetAttributesList.get(0);
            if (arrayed(dataAttr)) {
                dataAttr.setMapColor(0, dataAttr.getMapColor(0)); // ensure colorMap is not empty
            } else {
                dataAttr.clearColors();
            }
        }
        showRows.forEach(fileTableItem -> {
                    var dataAttr = fileTableItem.getDatasetAttributes();
                    if ((dataAttr != null) && fileTableItem.getActive()) {
                        dataAttr.setPos(true);
                        if (curLvl != null) {
                            dataAttr.setLvl(curLvl);
                        }
                        Integer row = fileTableItem.getRow();
                        if (singleData && (row != null)) {
                            int iGroup = fileTableItem.getGroup();
                            groupSet.add(iGroup);
                            double offset = iGroup * 1.0 / groupSize * 0.8;
                            offsetMap.put(row - 1, offset);
                            rows.add(row - 1); // rows index from 1
                        } else {
                            dataAttr.clearColors();
                        }
                    }
                }
        );

        if (singleData) {
            DatasetAttributes dataAttr = datasetAttributesList.get(0);
            int nDim = dataAttr.nDim;
            chart.full(nDim - 1);
            if ((nDim - dataAttr.getDataset().getNFreqDims()) == 1) {
                chart.setDrawlist(rows);
            } else {
                chart.clearDrawlist();
            }
            if (groupSet.size() > 1) {
                dataAttr.setMapOffsets(offsetMap);
            } else {
                dataAttr.clearOffsets();
            }
        }
    }

    private void colorByGroup() {
        getItems().forEach(fileTableItem -> {
            var dataAttr = fileTableItem.getDatasetAttributes();
            if (dataAttr != null) {
                Integer row = fileTableItem.getRow();
                if (row != null) {
                    int iGroup = fileTableItem.getGroup();
                    Color color = getGroupColor(iGroup);
                    dataAttr.setMapColor(row - 1, color);
                } else {
                    dataAttr.clearColors();
                }
            }
        });
        PolyChart chart = scannerTool.getChart();
        chart.refresh();
    }

    protected final void selectionChanged() {
        if (processingTable) {
            return;
        }
        List<FileTableItem> selected = tableView.getSelectionModel().getSelectedItems();
        PolyChart chart = scannerTool.getChart();
        ProcessorController processorController = chart.getProcessorController(false);
        if ((processorController == null)
                || processorController.isViewingDataset()
                || !processorController.isVisible()) {
            List<FileTableItem> showRows = new ArrayList<>();
            for (var item : getItems()) {
                item.setSelected(false);
            }
            ScannerTool.TableSelectionMode tableSelectionMode = scannerTool.tableSelectionMode();
            if (tableSelectionMode == ScannerTool.TableSelectionMode.HIGHLIGHT) {
                for (var item : selected) {
                    item.setSelected(true);
                }
            }

            boolean showAll = tableSelectionMode == ScannerTool.TableSelectionMode.ALL || tableSelectionMode == ScannerTool.TableSelectionMode.HIGHLIGHT;
            if (selected.isEmpty() || showAll) {
                showRows.addAll(tableView.getItems());
            } else {
                showRows.addAll(selected);
            }

            boolean hasDataset = false;
            for (var item : showRows) {
                if (!item.getDatasetName().isBlank()) {
                    hasDataset = true;
                    break;
                }
            }

            if (hasDataset) {
                setDatasetVisibility(showRows, null);
                refresh();
                chart.refresh();
            } else {
                openSelectedListFile();
                chart.refresh();
            }
        } else {
            openSelectedListFile();
        }
    }

    protected final void setDragHandlers(Node mouseNode) {
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
                    List<String> nmrFiles = NMRDataUtil.findNMRDirectories(scanDir.getAbsolutePath());
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
            if (!files.isEmpty() && (files.get(0).isDirectory() || files.get(0).toString().endsWith(".txt"))) {
                tableView.setStyle("-fx-border-color: green;"
                        + "-fx-border-width: 1;");
                e.acceptTransferModes(TransferMode.COPY);
            }
        } else {
            e.consume();
        }
    }

    public void setScanDirectory(File selectedDir) {
        scanDir = selectedDir;
    }

    public void loadScanFiles() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        File scanDirFile = dirChooser.showDialog(null);
        if (scanDirFile == null) {
            return;
        }
        scanDir = scanDirFile;
        List<String> nmrFiles = NMRDataUtil.findNMRDirectories(scanDir.getAbsolutePath());
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
            GUIUtils.warn(SCANNER_ERROR, "Processing Script Not Configured");
            return;
        }

        if ((scanDir == null) || scanDir.toString().isEmpty()) {
            GUIUtils.warn(SCANNER_ERROR, "No scan directory");
            return;
        }
        String outDirName = GUIUtils.input("Output directory name", "output");
        Path outDirPath = Paths.get(scanDir.toString(), outDirName);
        File scanOutputDir = outDirPath.toFile();
        if (!scanOutputDir.exists() && !scanOutputDir.mkdir()) {
            GUIUtils.warn(SCANNER_ERROR, "Could not create output dir");
            return;
        }

        String combineFileName = GUIUtils.input("Output file name", "process");

        if (!scanOutputDir.exists() || !scanOutputDir.isDirectory() || !scanOutputDir.canWrite()) {
            GUIUtils.warn(SCANNER_ERROR, "Output dir is not a writable directory");
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
        tableView.getSelectionModel().getSelectedIndices().removeListener(selectionListener);
        tableView.getItems().removeListener(filterItemListener);

        try (PythonInterpreter processInterp = new PythonInterpreter()) {
            List<String> fileNames = new ArrayList<>();

            String initScript = ChartProcessor.buildInitScript();
            processInterp.exec(initScript);

            int nDim = fileTableItems.get(0).getNDim();
            String processScript = chartProcessor.buildScript(nDim);
            Processor processor = Processor.getProcessor();
            processor.keepDatasetOpen(false);

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
                try {
                    // merge all the 1D files into a pseudo 2D file
                    merger.mergeFiles(fileNames, mergedFile);
                    // After merging, remove the 1D files
                    for (String fileName : fileNames) {
                        File file = new File(fileName);
                        AnalystApp.getFXMLControllerManager().getOrCreateActiveController().closeFile(file);
                        Files.deleteIfExists(file.toPath());
                        String parFileName = fileName.substring(0, fileName.lastIndexOf(".")) + ".par";
                        File parFile = new File(parFileName);
                        Files.deleteIfExists(parFile.toPath());
                    }

                    // load merged dataset
                    AnalystApp.getFXMLControllerManager().getOrCreateActiveController().openDataset(mergedFile, false, true);
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
                AnalystApp.getFXMLControllerManager().getOrCreateActiveController().openDataset(datasetFile, false, true);
            }
            chart.full();
            chart.autoScale();

            File saveTableFile = new File(scanDir, "scntbl.txt");
            saveScanTable(saveTableFile);
            scannerTool.miner.setDisableSubMenus(!combineFileMode);

        } finally {
            tableView.getSelectionModel().getSelectedIndices().addListener(selectionListener);
            tableView.getItems().addListener(filterItemListener);
            getGroups();
            ensureAllDatasetsAdded();
            selectionChanged();
            processingTable = false;
            refresh();
        }
    }

    public void combineDatasets() {
        List<Dataset> datasets = getDatasetAttributesList().stream().map(dAttr -> (Dataset) dAttr.getDataset()).toList();
        if (currentChart.getDatasetAttributes().size() < 2) {
            GUIUtils.warn("Combine", "Need more than one dataset to combine");
        } else {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle(("Output File"));
            File file = fileChooser.showSaveDialog(null);
            if (file != null) {
                DatasetMerger datasetMerger = new DatasetMerger();
                try {
                    datasetMerger.mergeDatasets(datasets, file);
                } catch (IOException | DatasetException e) {
                    ExceptionDialog exceptionDialog = new ExceptionDialog(e);
                    exceptionDialog.showAndWait();
                    return;
                }
                AnalystApp.getFXMLControllerManager().getOrCreateActiveController().openDataset(file, false, true);
                loadFromDataset();
            }
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
                FileTableItem fileTableItem = tableView.getItems().get(selItem);
                String fileName = fileTableItem.getFileName();
                String filePath = Paths.get(scanDir.getAbsolutePath(), fileName).toString();

                scannerTool.getChart().getFXMLController().openFile(filePath, false, false);
            }

        }
    }

    private void loadScanFiles(List<String> nmrFiles) {
        fileListItems.clear();
        long firstDate = Long.MAX_VALUE;
        List<FileTableItem> items = new ArrayList<>();
        for (String filePath : nmrFiles) {
            File file = new File(filePath);
            NMRData nmrData = null;
            try {
                nmrData = NMRDataUtil.getFID(file);
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

    public void loadMultipleDatasets() {
        PolyChart chart = scannerTool.getChart();
        var datasetAttributesList = chart.getDatasetAttributes();
        int iRow = 0;
        HashMap<String, String> fieldMap = new HashMap<>();
        tableView.getSelectionModel().getSelectedIndices().removeListener(selectionListener);
        tableView.getItems().removeListener(filterItemListener);
        fileListItems.clear();
        for (var datasetAttributes : datasetAttributesList) {
            Dataset dataset = (Dataset) datasetAttributes.getDataset();
            long eTime = 0;
            FileTableItem fileTableItem = new FileTableItem(dataset.getName(), "", dataset.getNDim(),
                    eTime, iRow + 1, dataset.getName(), fieldMap);
            fileTableItem.setDatasetAttributes(datasetAttributes);
            fileListItems.add(fileTableItem);
            iRow++;
        }
        tableView.getSelectionModel().getSelectedIndices().addListener(selectionListener);
        tableView.getItems().addListener(filterItemListener);
        columnTypes.put(PATH_COLUMN_NAME, "S");
        columnTypes.put(SEQUENCE_COLUMN_NAME, "S");
        columnTypes.put(NDIM_COLUMN_NAME, "I");
        columnTypes.put(ROW_COLUMN_NAME, "I");
        columnTypes.put(DATASET_COLUMN_NAME, "S");
        columnTypes.put(ETIME_COLUMN_NAME, "I");
        columnTypes.put(GROUP_COLUMN_NAME, "I");
        columnTypes.put(ACTIVE_COLUMN_NAME, "B");
        initTable(false);
        updateFilter();
        tableView.refresh();
    }

    public void loadFromDataset() {
        PolyChart chart = scannerTool.getChart();
        if (chart.getDatasetAttributes().size() > 1) {
            loadMultipleDatasets();
            return;
        } else if (!chart.getDatasetAttributes().isEmpty()) {
            Dataset dataset = (Dataset) chart.getDataset();
            if (!chart.is1D() && ((dataset.getNDim() > 1) && (dataset.getNDim() == dataset.getNFreqDims()))) {
                loadMultipleDatasets();
                return;
            }

        }
        DatasetBase dataset = chart.getDataset();
        if (dataset == null) {
            log.warn("Unable to load dataset, dataset is null.");
            return;
        }
        if (dataset.getNDim() < 2) {
            loadMultipleDatasets();
            return;
        }
        scanDir = null;
        // Need to disconnect listeners before updating fileListItem or the selectionListener and filterItemListeners
        // will be triggered during every iteration of the loop, greatly reducing performance
        tableView.getSelectionModel().getSelectedIndices().removeListener(selectionListener);
        tableView.getItems().removeListener(filterItemListener);
        fileListItems.clear();
        int nDim = dataset.getNDim();
        int nDataRows = dataset.getSizeTotal(nDim - 1);
        int nRows = (dataset.getNFreqDims() == 0) || (dataset.getNFreqDims() == dataset.getNDim()) ? 1 : nDataRows;
        HashMap<String, String> fieldMap = new HashMap<>();
        double[] values = dataset.getValues(nDim - 1);
        for (int iRow = 0; iRow < nRows; iRow++) {
            double value = 0;
            if ((values != null) && (iRow < values.length)) {
                value = values[iRow];
            }
            FileTableItem fileTableItem = new FileTableItem(dataset.getName(), "", 1, 0, iRow + 1, dataset.getName(), fieldMap);
            if (values != null) {
                fileTableItem.setExtra("value", value);
            }
            fileListItems.add(fileTableItem);
        }


        tableView.getSelectionModel().getSelectedIndices().addListener(selectionListener);
        tableView.getItems().addListener(filterItemListener);
        columnTypes.put(PATH_COLUMN_NAME, "S");
        columnTypes.put(SEQUENCE_COLUMN_NAME, "S");
        columnTypes.put(NDIM_COLUMN_NAME, "I");
        columnTypes.put(ROW_COLUMN_NAME, "I");
        columnTypes.put(DATASET_COLUMN_NAME, "S");
        columnTypes.put(ETIME_COLUMN_NAME, "I");
        columnTypes.put(GROUP_COLUMN_NAME, "I");
        columnTypes.put(ACTIVE_COLUMN_NAME, "B");
        Long firstDate = 0L;
        for (FileTableItem item : fileListItems) {
            item.setDate(item.getDate() - firstDate);
        }
        initTable();
        if (values != null) {
            addTableColumn("value", "D");
        }
        updateFilter();
        List<Integer> rows = new ArrayList<>();
        rows.add(0);
        // Load from Dataset assumes an arrayed dataset
        if ((dataset.getNFreqDims() > 2) || (dataset.getNFreqDims() == 0) && (dataset.getNDim() > 1)) {
            chart.getDisDimProperty().set(PolyChart.DISDIM.TwoD);
        } else {
            chart.getDisDimProperty().set(PolyChart.DISDIM.OneDX);
            chart.setDrawlist(rows);
        }

        setDatasetAttributes();
    }

    private void loadScanTable(File file) {
        long firstDate = Long.MAX_VALUE;
        int iLine = 0;
        String[] headers = new String[0];
        HashMap<String, String> fieldMap = new HashMap<>();
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
                            fieldMap.put(headers[iField].toLowerCase(), fields[iField]);
                        }
                        boolean hasAll = true;
                        int nDim = 1;
                        long eTime = 0;
                        String sequence = "";
                        int row = 0;
                        boolean active = true;
                        String fileName = "";
                        for (String standardHeader : standardHeaders) {
                            if (!fieldMap.containsKey(standardHeader)) {
                                hasAll = false;
                            } else {
                                switch (standardHeader) {
                                    case PATH_COLUMN_NAME -> fileName = fieldMap.get(standardHeader);
                                    case NDIM_COLUMN_NAME -> nDim = Integer.parseInt(fieldMap.get(standardHeader));
                                    case ROW_COLUMN_NAME -> row = Integer.parseInt(fieldMap.get(standardHeader));
                                    case ETIME_COLUMN_NAME -> eTime = Long.parseLong(fieldMap.get(standardHeader));
                                    case SEQUENCE_COLUMN_NAME -> sequence = fieldMap.get(standardHeader);
                                    case ACTIVE_COLUMN_NAME -> active = fieldMap.get(standardHeader).equals("1");
                                }
                            }
                        }
                        String datasetName = "";
                        if (fieldMap.containsKey(DATASET_COLUMN_NAME)) {
                            datasetName = fieldMap.get(DATASET_COLUMN_NAME);
                            if (firstDatasetName.isEmpty()) {
                                firstDatasetName = datasetName;
                            } else if (!firstDatasetName.equals(datasetName)) {
                                combineFileMode = false;
                            }
                        }
                        boolean noPath = (fileName == null) || fileName.isBlank();
                        if (!hasAll && !noPath) {
                            if ((scanDir == null) || scanDir.toString().isBlank()) {
                                GUIUtils.warn("Load scan table", "No scan directory");
                                return;
                            }
                            Path filePath = FileSystems.getDefault().getPath(scanDir.toString(), fileName);

                            NMRData nmrData;
                            try {
                                nmrData = NMRDataUtil.getFID(filePath.toFile());
                            } catch (IOException ioE) {
                                GUIUtils.warn("Load scan table", "Couldn't load this file: " + filePath);
                                return;
                            }

                            if (!fieldMap.containsKey(ETIME_COLUMN_NAME)) {
                                eTime = nmrData.getDate();
                            }
                            if (!fieldMap.containsKey(SEQUENCE_COLUMN_NAME)) {
                                sequence = nmrData.getSequence();
                            }
                            if (!fieldMap.containsKey(NDIM_COLUMN_NAME)) {
                                nDim = nmrData.getNDim();
                            }
                        }
                        if (eTime < firstDate) {
                            firstDate = eTime;
                        }
                        var item = new FileTableItem(fileName, sequence, nDim, eTime, row, datasetName, fieldMap);
                        item.setActive(active);
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
            columnTypes.put(PATH_COLUMN_NAME, "S");
            columnTypes.put(SEQUENCE_COLUMN_NAME, "S");
            columnTypes.put(NDIM_COLUMN_NAME, "I");
            columnTypes.put(ROW_COLUMN_NAME, "I");
            columnTypes.put(DATASET_COLUMN_NAME, "S");
            columnTypes.put(ETIME_COLUMN_NAME, "I");

            for (FileTableItem item : fileListItems) {
                item.setDate(item.getDate() - firstDate);
                item.setTypes(headers, notDouble, notInteger);
            }
            initTable();
            addHeaders(headers);
            fileTableFilter.resetFilter();
            if (!firstDatasetName.isEmpty()) {
                File parentDir = file.getParentFile();
                Path path = FileSystems.getDefault().getPath(parentDir.toString(), firstDatasetName);
                if (path.toFile().exists()) {
                    Dataset firstDataset = AnalystApp.getFXMLControllerManager().getOrCreateActiveController().openDataset(path.toFile(), false, true);
                    // If there is only one unique dataset name, assume an arrayed experiment
                    List<String> uniqueDatasetNames = fileListItems.stream().map(FileTableItem::getDatasetName).distinct().toList();
                    if (uniqueDatasetNames.size() == 1 && uniqueDatasetNames.get(0) != null && !uniqueDatasetNames.get(0).isEmpty()) {
                        firstDataset.setNFreqDims(firstDataset.getNDim() - 1);
                    }
                    PolyChart chart = scannerTool.getChart();
                    List<Integer> rows = new ArrayList<>();
                    rows.add(0);
                    chart.setDrawlist(rows);
                    chart.full();
                    chart.autoScale();
                }
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

    public TableView<FileTableItem> getTableView() {
        return tableView;
    }

    public List<String> getHeaders() {
        ObservableList<TableColumn<FileTableItem, ?>> columns = tableView.getColumns();
        List<String> headers = new ArrayList<>();
        for (TableColumn<FileTableItem, ?> column : columns) {
            String name = column.getText();
            headers.add(name);
        }
        return headers;
    }

    private void saveScanTable(File file) {
        Charset charset = StandardCharsets.US_ASCII;
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
            if (!name.isEmpty()) {
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
        addTableColumn(GROUP_COLUMN_NAME, "I");
    }

    private List<String> headersMissing(String[] headerNames) {
        List<String> missing = new ArrayList<>();
        for (var headerName : headerNames) {
            if (headerAbsent(headerName)) {
                missing.add(headerName);
            }
        }
        return missing;
    }

    private boolean headerAbsent(String headerName) {
        ObservableList<TableColumn<FileTableItem, ?>> columns = tableView.getColumns();
        boolean present = false;
        for (TableColumn<FileTableItem, ?> column : columns) {
            String name = column.getText();
            if (name.equals(headerName)) {
                present = true;
                break;
            }
        }
        return !present;
    }

    public void addTableColumn(String newName, String type) {
        if (headerAbsent(newName)) {
            columnTypes.put(newName, type);
            addColumn(newName);
        }
    }

    private void initTable() {
        initTable(true);

    }

    private boolean columnHasMultiple(TableColumn<FileTableItem, ?> column) {
        int nRows = tableView.getItems().size();
        if (nRows < 2) {
            return false;
        }
        String firstRow = column.getCellData(0) == null ? "" : column.getCellData(0).toString();
        boolean hasMulti = false;
        for (int i = 1; i < nRows; i++) {
            String thisRow = column.getCellData(i) == null ? "" : column.getCellData(i).toString();
            if (!firstRow.equals(thisRow)) {
                hasMulti = true;
                break;
            }
        }
        return hasMulti;
    }

    private void checkColumnsForMultiple() {
        for (var column : columnsToCheckForMulti) {
            column.setVisible(columnHasMultiple(column));
        }
    }

    private void initTable(boolean arrayDataset) {
        tableView.setEditable(true);
        tableView.getColumns().clear();
        TableColumn<FileTableItem, String> datasetColumn = new TableColumn<>(DATASET_COLUMN_NAME);
        datasetColumn.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getDatasetName()));
        tableView.getColumns().add(datasetColumn);

        TableColumn<FileTableItem, String> fileColumn = new TableColumn<>(PATH_COLUMN_NAME);
        TableColumn<FileTableItem, String> seqColumn = new TableColumn<>(SEQUENCE_COLUMN_NAME);
        TableColumn<FileTableItem, Number> nDimColumn = new TableColumn<>(NDIM_COLUMN_NAME);
        TableColumn<FileTableItem, Long> dateColumn = new TableColumn<>(ETIME_COLUMN_NAME);
        TableColumn<FileTableItem, Number> rowColumn = new TableColumn<>(ROW_COLUMN_NAME);
        fileColumn.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getFileName()));
        seqColumn.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getSeqName()));
        nDimColumn.setCellValueFactory(e -> new SimpleIntegerProperty(e.getValue().getNDim()));
        rowColumn.setCellValueFactory(e -> new SimpleIntegerProperty(e.getValue().getRow()));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("Date"));
        tableView.getColumns().addAll(seqColumn, nDimColumn, dateColumn, rowColumn);
        fileColumn.setVisible(false);
        seqColumn.setVisible(false);
        columnsToCheckForMulti.add(fileColumn);
        columnsToCheckForMulti.add(seqColumn);
        columnsToCheckForMulti.add(nDimColumn);
        columnsToCheckForMulti.add(dateColumn);

        TableColumn<FileTableItem, Double> levelCol = makeLvlColumn(scannerTool);
        TableColumn<FileTableItem, Double> offsetCol = makeOffsetColumn(scannerTool);
        TableColumn<FileTableItem, Integer> nLevelsCol = makeNLvlsColumn();
        TableColumn<FileTableItem, Double> clmCol = makeCLMColumn(scannerTool);

        tableView.getColumns().addAll(levelCol, offsetCol, nLevelsCol, clmCol);

        TableColumn<FileTableItem, Color> posColorCol = makeColorColumns(scannerTool, true);
        TableColumn<FileTableItem, Color> negColorCol = makeColorColumns(scannerTool, false);

      //  TableColumn<FileTableItem, Boolean> posDrawOnCol = makePosDrawColumn(scannerTool, true);
        TableColumn<FileTableItem, Boolean> negDrawOnCol = makePosDrawColumn(scannerTool, false);


        TableColumn<FileTableItem, TableColumn> posColumn = new TableColumn<>(POSITIVE_COLUMN_NAME);
        TableColumn<FileTableItem, TableColumn> negColumn = new TableColumn<>(NEGATIVE_COLUMN_NAME);
        posColumn.getColumns().addAll(posColorCol);
        negColumn.getColumns().addAll(negDrawOnCol, negColorCol);

        TableColumn<FileTableItem, Boolean> activeColumn = makeActiveColumn(scannerTool);
        TableColumn<FileTableItem, Number> groupColumn = makeGroupColumn();

        tableView.getColumns().addAll(posColumn, negColumn, activeColumn, groupColumn);

        updateFilter();

        for (TableColumn<FileTableItem, ?> column : tableView.getColumns()) {
            String columnText = column.getText();
            if (!isAttributeColumn(columnText) && setColumnGraphic(column)) {
                column.graphicProperty().addListener(e -> graphicChanged(column));
            }

        }
    }

    private TableColumn<FileTableItem, Color> makeColorColumns(ScannerTool scannerTool, boolean posMode) {
        TableColumn<FileTableItem, Color> colorCol = new TableColumn<>(COLOR_COLUMN_NAME);
        colorCol.setEditable(true);
        colorCol.setSortable(false);
        if (posMode) {
            colorCol.setCellValueFactory(e -> new SimpleObjectProperty<>(e.getValue().getPosColor()));
            TableUtils.addColorColumnEditor(colorCol, (item, color) -> {
                item.setPosColor(color);
                scannerTool.getChart().refresh();
            });
        } else {
            colorCol.setCellValueFactory(e -> new SimpleObjectProperty<>(e.getValue().getNegColor()));
            TableUtils.addColorColumnEditor(colorCol, (item, color) -> {
                item.setNegColor(color);
                scannerTool.getChart().refresh();
            });
        }
        ContextMenu columnMenu = createColorContextMenu(posMode);
        columnMenus.put(colorCol, columnMenu);
        return colorCol;
    }

    private TableColumn<FileTableItem, Double> makeCLMColumn(ScannerTool scannerTool) {
        TableColumn<FileTableItem, Double> clmCol = new TableColumn<>(CLM_COLUMN_NAME);
        clmCol.setSortable(false);
        clmCol.setCellValueFactory(new PropertyValueFactory<>("clm"));
        TableUtils.addDatasetTextEditor(clmCol, TableUtils.getDoubleColumnFormatter(2), (item, value) -> {
            item.setClm(value);
            scannerTool.getChart().refresh();
        });

        clmCol.setPrefWidth(50);

        ContextMenu clmMenu = new ContextMenu();
        MenuItem adjustCLMItem = new MenuItem("Adjust...");
        adjustCLMItem.setOnAction(e -> adjust(ATTR_COLUMNS.CLM, clmMenu, this::adjustCLM));
        MenuItem unifyCLMItem = new MenuItem("unify");
        unifyCLMItem.setOnAction(e -> unifyCLM());
        clmCol.setContextMenu(clmMenu);
        clmMenu.getItems().addAll(adjustCLMItem, unifyCLMItem);
        columnMenus.put(clmCol, clmMenu);
        return clmCol;
    }

    private TableColumn<FileTableItem, Integer> makeNLvlsColumn() {
        IntegerStringConverter isConverter = new IntegerStringConverter();
        TableColumn<FileTableItem, Integer> nLevelsCol = new TableColumn<>(NLVL_COLUMN_NAME);
        nLevelsCol.setSortable(false);
        nLevelsCol.setCellValueFactory(new PropertyValueFactory<>("nlvls"));
        nLevelsCol.setCellFactory(tc -> new TextFieldTableCell<>(isConverter));
        nLevelsCol.setOnEditCommit(
                (TableColumn.CellEditEvent<FileTableItem, Integer> t) -> {
                    Integer value = t.getNewValue();
                    if (value != null) {
                        t.getRowValue().setNlvls(value);
                    }
                });
        ContextMenu nLvlMenu = new ContextMenu();
        MenuItem adjustNLvlItem = new MenuItem("Adjust...");
        adjustNLvlItem.setOnAction(e -> adjust(ATTR_COLUMNS.NLVL, nLvlMenu, this::adjustNLevels));
        MenuItem unifyNLvlItem = new MenuItem("unify");
        unifyNLvlItem.setOnAction(e -> unifyNLvl());
        nLevelsCol.setContextMenu(nLvlMenu);
        columnMenus.put(nLevelsCol, nLvlMenu);
        nLvlMenu.getItems().addAll(adjustNLvlItem, unifyNLvlItem);

        nLevelsCol.setPrefWidth(35);
        nLevelsCol.setEditable(true);
        return nLevelsCol;

    }

    private TableColumn<FileTableItem, Double> makeOffsetColumn(ScannerTool scannerTool) {
        TableColumn<FileTableItem, Double> offsetCol = new TableColumn<>(OFFSET_COLUMN_NAME);
        offsetCol.setSortable(false);
        offsetCol.setCellValueFactory(new PropertyValueFactory<>("offset"));
        TableUtils.addDatasetTextEditor(offsetCol, TableUtils.getDoubleColumnFormatter(2), (item, value) -> {
            item.setOffset(value);
            scannerTool.getChart().refresh();
        });

        ContextMenu offsetMenu = new ContextMenu();
        MenuItem adjustOffsetItem = new MenuItem("Adjust...");
        adjustOffsetItem.setOnAction(e -> adjust(ATTR_COLUMNS.OFFSET, offsetMenu, this::adjustOffset));
        MenuItem unifyOffsetItem = new MenuItem("unify");
        unifyOffsetItem.setOnAction(e -> unifyOffset());
        MenuItem rampOffsetItem = new MenuItem("ramp");
        rampOffsetItem.setOnAction(e -> rampOffset());
        MenuItem popOffsetItem = new MenuItem("pop");
        popOffsetItem.setOnAction(e -> popOffset());
        offsetMenu.getItems().addAll(adjustOffsetItem, unifyOffsetItem, rampOffsetItem, popOffsetItem);
        offsetCol.setContextMenu(offsetMenu);
        offsetCol.setPrefWidth(50);
        columnMenus.put(offsetCol, offsetMenu);
        return offsetCol;
    }

    private TableColumn<FileTableItem, Double> makeLvlColumn(ScannerTool scannerTool) {
        TableColumn<FileTableItem, Double> levelCol = new TableColumn<>(LVL_COLUMN_NAME);
        levelCol.setSortable(false);
        levelCol.setCellValueFactory(new PropertyValueFactory<>("lvl"));
        TableUtils.addDatasetTextEditor(levelCol, TableUtils.getDoubleColumnFormatter(4), (item, value) -> {
            item.setLvl(value);
            scannerTool.getChart().refresh();
        });

        ContextMenu levelMenu = new ContextMenu();
        MenuItem adjustLevelItem = new MenuItem("Adjust...");
        adjustLevelItem.setOnAction(e -> adjust(ATTR_COLUMNS.LVL, levelMenu, this::adjustLevel));
        MenuItem unifyLevelItem = new MenuItem("unify");
        unifyLevelItem.setOnAction(e -> unifyLevel());
        levelCol.setContextMenu(levelMenu);
        levelMenu.getItems().addAll(adjustLevelItem, unifyLevelItem);
        columnMenus.put(levelCol, levelMenu);
        return levelCol;
    }

    private static TableColumn<FileTableItem, Boolean> makePosDrawColumn(ScannerTool scannerTool, boolean posMode) {

        TableColumn<FileTableItem, Boolean> drawOnCol = new TableColumn<>("on");
        drawOnCol.setSortable(false);
        drawOnCol.setEditable(true);
        if (posMode) {
            drawOnCol.setCellValueFactory(e -> new SimpleBooleanProperty(e.getValue().getPos()));
            TableUtils.addCheckBoxEditor(drawOnCol, (item, b) -> {
                item.setPos(b);
                scannerTool.getChart().refresh();
            });
        } else {
            drawOnCol.setCellValueFactory(e -> new SimpleBooleanProperty(e.getValue().getNeg()));
            TableUtils.addCheckBoxEditor(drawOnCol, (item, b) -> {
                item.setNeg(b);
                scannerTool.getChart().refresh();
            });

        }

        drawOnCol.setPrefWidth(25);
        drawOnCol.setMaxWidth(25);
        drawOnCol.setResizable(false);
        return drawOnCol;
    }

    private static TableColumn<FileTableItem, Boolean> makeActiveColumn (ScannerTool scannerTool) {

        TableColumn<FileTableItem, Boolean> activeCol = new TableColumn<>(ACTIVE_COLUMN_NAME);
        activeCol.setSortable(false);
        activeCol.setEditable(true);
        activeCol.setCellValueFactory(e -> new SimpleBooleanProperty(e.getValue().getActive()));
            TableUtils.addCheckBoxEditor(activeCol, (item, b) -> {
                item.setActive(b);
                scannerTool.scanTable.selectionChanged();
            });

        activeCol.setPrefWidth(50);
        activeCol.setMaxWidth(50);
        activeCol.setResizable(false);
        return activeCol;
    }
    private static TableColumn<FileTableItem, Number> makeGroupColumn() {
        TableColumn<FileTableItem, Number> groupColumn = new TableColumn<>(GROUP_COLUMN_NAME);
        groupColumn.setCellValueFactory(e -> new SimpleIntegerProperty(e.getValue().getGroup()));

        groupColumn.setCellFactory(column -> new TableCell<>() {
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
        });
        return groupColumn;
    }

    private void addHeaders(String[] headers) {
        var missingHeaders = headersMissing(headers);
        for (var header : missingHeaders) {
            addColumn(header);
        }
    }

    private void addColumn(String header) {
        if (headerAbsent(header)) {
            String type = columnTypes.get(header);
            final TableColumn<FileTableItem, ?> newColumn;
            if (type == null) {
                type = "S";
                log.info("No type for {}", header);
            }
            switch (type) {
                case "D":
                    TableColumn<FileTableItem, Number> doubleExtraColumn = new TableColumn<>(header);
                    newColumn = doubleExtraColumn;
                    doubleExtraColumn.setCellValueFactory(e -> new SimpleDoubleProperty(e.getValue().getDoubleExtra(header)));
                    doubleExtraColumn.setCellFactory(col
                            -> new TableCell<>() {
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
                    intExtraColumn.setCellValueFactory(e -> new SimpleIntegerProperty(e.getValue().getIntegerExtra(header)));
                    tableView.getColumns().add(intExtraColumn);
                    break;
                default:
                    TableColumn<FileTableItem, String> extraColumn = new TableColumn<>(header);
                    newColumn = extraColumn;
                    extraColumn.setCellValueFactory(e -> new SimpleStringProperty(String.valueOf(e.getValue().getExtra(header))));
                    tableView.getColumns().add(extraColumn);
                    break;
            }

            updateFilter();
            setColumnGraphic(newColumn);
            newColumn.graphicProperty().addListener(e -> graphicChanged(newColumn));
        }
    }

    private void graphicChanged(TableColumn<FileTableItem, ?> column) {
        Node node = column.getGraphic();
        boolean isFiltered = (node != null) && !(node instanceof StackPane);
        if ((node == null) || isFiltered) {
            setColumnGraphic(column);
        }
    }

    private ContextMenu createColorContextMenu(boolean posColorMode) {
        ContextMenu colorMenu = new ContextMenu();
        MenuItem unifyColorItem = new MenuItem("unify");
        unifyColorItem.setOnAction(e -> unifyColors(posColorMode));
        MenuItem interpColor = new MenuItem("interpolate");
        interpColor.setOnAction(e -> interpolateColors(posColorMode));
        MenuItem schemaPosColor = new MenuItem("schema...");
        schemaPosColor.setOnAction(e -> setColorsToSchema(posColorMode));
        colorMenu.getItems().addAll(unifyColorItem, interpColor, schemaPosColor);
        return colorMenu;
    }

    private void unifyColors(boolean posColorMode) {
        if (!getItems().isEmpty()) {
            var selectedItem = tableView.getSelectionModel().getSelectedItem();
            if (selectedItem == null) {
                selectedItem = getItems().get(0);
            }
            TableColors.unifyColor(getItems(), selectedItem.getColor(posColorMode), (item, color) -> item.setColor(color, posColorMode));
            scannerTool.getChart().refresh();
            refresh();
        }
    }

    private void interpolateColors(boolean posColorMode) {
        int size = getItems().size();
        if (size > 1) {
            Color color1 = getItems().get(0).getColor(posColorMode);
            Color color2 = getItems().get(size - 1).getColor(posColorMode);
            TableColors.interpolateColors(getItems(), color1, color2,
                    (item, color) -> item.setColor(color, posColorMode));
            scannerTool.getChart().refresh();
            refresh();
        }
    }

    void setColorsToSchema(boolean posColorMode) {
        double x = tableView.getLayoutX();
        double y = tableView.getLayoutY() + tableView.getHeight() + 10;
        ColorSchemes.showSchemaChooser(s -> updateColorsWithSchema(s, posColorMode), x, y);
    }

    public void updateColorsWithSchema(String colorName, boolean posColors) {
        var items = getItems();
        if (items.size() < 2) {
            return;
        }
        int i = 0;
        List<Color> colors = ColorSchemes.getColors(colorName, items.size());
        for (var item : items) {
            Color color = colors.get(i++);
            item.setColor(color, posColors);
        }
        refresh();
        scannerTool.getChart().refresh();
    }

    private void setMenuGraphics(TableColumn<FileTableItem, ?> column, ContextMenu menu) {
        Text text = GlyphsDude.createIcon(FontAwesomeIcon.BARS);
        text.setMouseTransparent(true);
        column.setGraphic(text);
        column.setContextMenu(menu);
    }

    private boolean setColumnGraphic(TableColumn<FileTableItem, ?> column) {
        String text = column.getText().toLowerCase();
        if (isAttributeColumn(text)) {
            return false;
        }
        String type = columnTypes.get(column.getText());
        if (!"D".equals(type) && isGroupable(text)
                && !text.equalsIgnoreCase(COLOR_COLUMN_NAME)
                && !text.equalsIgnoreCase(ACTIVE_COLUMN_NAME)
        ) {
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
            return true;
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
            rect.setOnMousePressed(e -> hitDataDelete(column));
            rect.setOnMouseReleased(Event::consume);
            rect.setOnMouseClicked(Event::consume);
            column.setGraphic(stackPane);
            return true;
        } else {
            return false;
        }
    }

    private boolean isAttributeColumn(String columnText) {
        return columnText.equalsIgnoreCase(COLOR_COLUMN_NAME) || columnText.equalsIgnoreCase(POSITIVE_COLUMN_NAME) ||
                columnText.equalsIgnoreCase(NEGATIVE_COLUMN_NAME) || columnText.equalsIgnoreCase(LVL_COLUMN_NAME) ||
                columnText.equalsIgnoreCase(CLM_COLUMN_NAME) || columnText.equalsIgnoreCase(NLVL_COLUMN_NAME)
                || columnText.equalsIgnoreCase(OFFSET_COLUMN_NAME);
    }

    private boolean isFiltered(TableColumn column) {
        boolean filtered = false;
        Optional<ColumnFilter<FileTableItem, ?>> opt = fileTableFilter.getColumnFilter(column);
        if (opt.isPresent()) {
            filtered = opt.get().isFiltered();
        }
        return filtered;
    }

    private boolean isGroupable(String text) {
        return !standardHeaders.contains(text) && !text.equalsIgnoreCase(GROUP_COLUMN_NAME)
                && !text.contains(":") && !text.equalsIgnoreCase(DATASET_COLUMN_NAME) &&
                !isAttributeColumn(text);
    }

    public boolean isData(String text) {
        return !standardHeaders.contains(text) && !text.equals(GROUP_COLUMN_NAME)
                && text.contains(":") && !text.equals(DATASET_COLUMN_NAME) && !isAttributeColumn(text);
    }

    private void hitDataDelete(TableColumn<FileTableItem, ?> column) {
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
        colorByGroup();
        selectionChanged();
        tableView.refresh();
    }

    public void setChart() {
        PolyChart chart = scannerTool.getChart();
        if (currentChart != chart) {
            if (currentChart != null) {
                currentChart.getDatasetAttributes().removeListener(datasetListener);
            }
            chart.getDatasetAttributes().addListener(datasetListener);
            currentChart = chart;
            loadFromDataset();
        }
        updateFilter();
    }

    public void updateFilter() {
        // Old listener must be removed before setting the items!
        tableView.getItems().removeListener(filterItemListener);
        tableView.setItems(fileListItems);
        checkColumnsForMultiple();
        builder = TableFilter.forTableView(tableView);
        fileTableFilter = builder.apply();
        fileTableFilter.resetFilter();
        tableView.getItems().addListener(filterItemListener);
        getGroups();
        for (var entry : columnMenus.entrySet()) {
            setMenuGraphics(entry.getKey(), entry.getValue());
        }
    }

    public ObservableList<FileTableItem> getItems() {
        return fileListItems;
    }

    public List<DatasetAttributes> getDatasetAttributesList() {
        return fileListItems.stream().map(FileTableItem::getDatasetAttributes).
                filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<DatasetAttributes> getSelectedDatasetAttributesList() {
        List<FileTableItem> selectedItems = tableView.getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty()) {
            selectedItems = tableView.getItems();
        }
        return selectedItems.stream().map(FileTableItem::getDatasetAttributes).
                filter(Objects::nonNull).toList();
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

    Optional<DatasetAttributes> getSelectedAttributes() {
        List<org.nmrfx.processor.gui.spectra.DatasetAttributes> datasetAttributesList = getDatasetAttributesList();
        DatasetAttributes dataAttr0 = null;
        if (!datasetAttributesList.isEmpty()) {
            FileTableItem item0 = tableView.getSelectionModel().getSelectedItem();
            if (item0 == null) {
                item0 = getItems().get(0);
            }
            if (item0 != null) {
                dataAttr0 = item0.getDatasetAttributes();
            }
        }
        return Optional.ofNullable(dataAttr0);
    }

    void unifyWidth(boolean pos) {
        List<DatasetAttributes> datasetAttributesList = getDatasetAttributesList();
        getSelectedAttributes().ifPresent(dataAttr0 -> {
            datasetAttributesList.forEach(datasetAttributes -> {
                if (pos) {
                    datasetAttributes.setPosWidth(dataAttr0.getPosWidth());
                } else {
                    datasetAttributes.setNegWidth(dataAttr0.getNegWidth());
                }
            });
            tableView.refresh();
        });
    }

    void adjust(ATTR_COLUMNS columnType, ContextMenu menu, DoubleConsumer consumer) {
        double x = menu.getX();
        double y = menu.getY();
        double width = menu.getWidth();
        Double currentValue = getValue(columnType);
        var sliderRange = columnType.getSliderRange(currentValue);
        Optional<Double> optValue = GUIUtils.getSliderValue(columnType.title, x + width, y,
                sliderRange, consumer);
        double value = optValue.orElse(currentValue);
        consumer.accept(value);
        tableView.refresh();
    }

    Double getValue(ATTR_COLUMNS attrColumn) {
        Double value = null;
        List<DatasetAttributes> datasetAttributesList = getSelectedDatasetAttributesList();
        if (!datasetAttributesList.isEmpty()) {
            value = attrColumn.getValue(datasetAttributesList.get(0)).doubleValue();
        }
        return value;
    }

    void setValue(ATTR_COLUMNS attrColumn, Double value) {
        List<DatasetAttributes> datasetAttributesList = getSelectedDatasetAttributesList();
        if (value != null) {
            datasetAttributesList.forEach(datasetAttributes -> attrColumn.setValue(datasetAttributes, value));
            PolyChart chart = scannerTool.getChart();
            chart.refresh();
        }
    }

    void adjustLevel(Double value) {
        setValue(ATTR_COLUMNS.LVL, value);
    }

    void unifyLevel() {
        List<DatasetAttributes> datasetAttributesList = getDatasetAttributesList();
        getSelectedAttributes().ifPresent(dataAttr0 -> {
            datasetAttributesList.forEach(datasetAttributes -> datasetAttributes.setLvl(dataAttr0.getLvl()));
            tableView.refresh();
            PolyChart chart = scannerTool.getChart();
            chart.refresh();
        });
    }

    void adjustCLM(Double value) {
        setValue(ATTR_COLUMNS.CLM, value);
    }

    void unifyCLM() {
        List<DatasetAttributes> datasetAttributesList = getDatasetAttributesList();
        getSelectedAttributes().ifPresent(dataAttr0 -> {
            datasetAttributesList.forEach(datasetAttributes -> datasetAttributes.setClm(dataAttr0.getClm()));
            tableView.refresh();
            PolyChart chart = scannerTool.getChart();
            chart.refresh();
        });
    }

    void adjustNLevels(Double value) {
        setValue(ATTR_COLUMNS.NLVL, value);
    }

    void unifyNLvl() {
        List<DatasetAttributes> datasetAttributesList = getDatasetAttributesList();
        getSelectedAttributes().ifPresent(dataAttr0 -> {
            datasetAttributesList.forEach(datasetAttributes -> datasetAttributes.setNlvls(dataAttr0.getNlvls()));
            tableView.refresh();
            PolyChart chart = scannerTool.getChart();
            chart.refresh();
        });
    }

    void adjustOffset(Double value) {
        setValue(ATTR_COLUMNS.OFFSET, value);
    }

    void unifyOffset() {
        List<DatasetAttributes> datasetAttributesList = getDatasetAttributesList();
        getSelectedAttributes().ifPresent(dataAttr0 -> {
            datasetAttributesList.forEach(datasetAttributes -> datasetAttributes.setOffset(dataAttr0.getOffset()));
            tableView.refresh();
            PolyChart chart = scannerTool.getChart();
            chart.refresh();
        });
    }

    void rampOffset() {
        List<DatasetAttributes> datasetAttributesList = getDatasetAttributesList();
        getSelectedAttributes().ifPresent(dataAttr0 -> {
            int nItems = datasetAttributesList.size();
            if (nItems > 0) {
                double offset = dataAttr0.getOffset();
                double offsetIncr = 0.0;
                if (nItems > 1) {
                    offsetIncr = 0.8 / (nItems);
                }
                for (DatasetAttributes dataAttr : datasetAttributesList) {
                    dataAttr.setOffset(offset);
                    offset += offsetIncr;
                }
            }
            tableView.refresh();
            PolyChart chart = scannerTool.getChart();
            chart.refresh();
        });
    }

    void popOffset() {
        List<DatasetAttributes> datasetAttributesList = getDatasetAttributesList();
        getSelectedAttributes().ifPresent(dataAttr0 -> {
            int nItems = datasetAttributesList.size();
            if (nItems > 0) {
                double min = datasetAttributesList.stream().mapToDouble(DatasetAttributes::getOffset).min().orElse(0.0);
                for (DatasetAttributes dataAttr : datasetAttributesList) {
                    dataAttr.setOffset(min);
                }
                double offset = (1.0 - min) / 2.0 + min;
                dataAttr0.setOffset(offset);
            }
            tableView.refresh();
            PolyChart chart = scannerTool.getChart();
            chart.refresh();
        });
    }

    enum ATTR_COLUMNS {
        LVL(LVL_COLUMN_NAME, "Level") {
            GUIUtils.SliderRange getSliderRange(double value) {
                return new GUIUtils.SliderRange(value / 10.0, value, value * 10.0, value / 100.0);
            }

            Number getValue(DatasetAttributes datasetAttributes) {
                return datasetAttributes.getLvl();
            }

            void setValue(DatasetAttributes datasetAttributes, Number value) {
                datasetAttributes.setLvl(value.doubleValue());
            }

        },
        OFFSET(OFFSET_COLUMN_NAME, "Offset") {
            GUIUtils.SliderRange getSliderRange(double value) {
                return new GUIUtils.SliderRange(0.0, value, 1.0, 0.01);
            }

            Number getValue(DatasetAttributes datasetAttributes) {
                return datasetAttributes.getOffset();
            }

            void setValue(DatasetAttributes datasetAttributes, Number value) {
                datasetAttributes.setOffset(value.doubleValue());
            }
        },
        CLM(OFFSET_COLUMN_NAME, "Contour Level Multiplier") {
            GUIUtils.SliderRange getSliderRange(double value) {
                return new GUIUtils.SliderRange(1.01, value, 4.0, 0.01);
            }

            Number getValue(DatasetAttributes datasetAttributes) {
                return datasetAttributes.getClm();
            }

            void setValue(DatasetAttributes datasetAttributes, Number value) {
                datasetAttributes.setClm(value.doubleValue());
            }
        },
        NLVL(NLVL_COLUMN_NAME, "Number of Levels") {
            GUIUtils.SliderRange getSliderRange(double value) {
                return new GUIUtils.SliderRange(1.0, value, 50.0, 1.0);
            }

            Number getValue(DatasetAttributes datasetAttributes) {
                return datasetAttributes.getNlvls();
            }

            void setValue(DatasetAttributes datasetAttributes, Number value) {
                datasetAttributes.setNlvls(value.intValue());
            }
        };
        final String name;
        final String title;

        ATTR_COLUMNS(String name, String title) {
            this.name = name;
            this.title = title;
        }

        abstract GUIUtils.SliderRange getSliderRange(double value);

        abstract Number getValue(DatasetAttributes datasetAttributes);

        abstract void setValue(DatasetAttributes datasetAttributes, Number value);
    }
}
