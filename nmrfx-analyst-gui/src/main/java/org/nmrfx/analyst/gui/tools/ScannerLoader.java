package org.nmrfx.analyst.gui.tools;

import javafx.collections.ObservableList;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.controlsfx.control.PopOver;
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
import org.nmrfx.processor.gui.controls.FileTableItem;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.processing.Processor;
import org.nmrfx.utils.FormatUtils;
import org.nmrfx.utils.GUIUtils;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ScannerLoader {
    private static final Logger log = LoggerFactory.getLogger(ScannerLoader.class);
    ScanTable scanTable;
    private File scanDir;
    PopOver popOver = new PopOver();

    public ScannerLoader(ScanTable scanTable) {
        this.scanTable = scanTable;
        scanTable.scannerLoader = this;
    }

    public void setScanDir(File file) {
        this.scanDir = file;
    }

    public File getScanDir() {
        return scanDir;
    }

    public void loadScanTable() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(("Open Table File"));
        File file = fileChooser.showOpenDialog(popOver);
        if (file != null) {
            loadScanTable(file, scanTable);
        }
    }

    public void loadScanFiles(ScanTable scanTable) {
        DirectoryChooser dirChooser = new DirectoryChooser();
        File scanDirFile = dirChooser.showDialog(null);
        if (scanDirFile == null) {
            return;
        }
        scanDir = scanDirFile;
        List<String> nmrFiles = NMRDataUtil.findNMRDirectories(scanDirFile.getAbsolutePath());
        scanTable.processingTable = true;
        try {
            scanTable.initTable();
            scanTable.fileListItems.clear();
            loadScanFiles(scanDirFile, nmrFiles);
            scanTable.checkColumnsForMultiple();
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        } finally {
            scanTable.processingTable = false;
        }
    }

    void loadScanFiles(File scanDirFile, List<String> nmrFiles) {
        scanDir = scanDirFile;
        scanTable.fileListItems.clear();
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
            scanTable.fileListItems.add(item);
        });

        scanTable.fileTableFilter.resetFilter();
    }

    void loadScanTable(File scanTableFile, ScanTable scanTable) {
        long firstDate = Long.MAX_VALUE;
        int iLine = 0;
        String[] headers = new String[0];
        HashMap<String, String> fieldMap = new HashMap<>();
        boolean[] notDouble = null;
        boolean[] notInteger = null;
        String firstDatasetName = "";
        if ((scanDir == null) || scanDir.toString().isBlank()) {
            scanDir = scanTableFile.getParentFile();
        }

        scanTable.processingTable = true;
        boolean combineFileMode = true;
        try {
            scanTable.fileListItems.clear();
            try (BufferedReader br = new BufferedReader(new FileReader(scanTableFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (iLine == 0) {
                        headers = line.split("\t");
                        notDouble = new boolean[headers.length];
                        notInteger = new boolean[headers.length];
                    } else {
                        parseLine(line, notInteger, notDouble, fieldMap, headers);
                        Optional<FileTableItem> stdInfoOpt = parseStandardInfo(scanDir, fieldMap);
                        if (stdInfoOpt.isEmpty()) {
                            return;
                        }
                        FileTableItem item = stdInfoOpt.get();
                        if (item.getDate() < firstDate) {
                            firstDate = item.getDate();
                        }
                        if (firstDatasetName.isEmpty()) {
                            firstDatasetName = item.getDatasetName();
                        } else if (!firstDatasetName.equals(item.getDatasetName())) {
                            combineFileMode = false;
                        }
                        scanTable.fileListItems.add(item);
                    }

                    iLine++;
                }
            } catch (IOException ioE) {
                log.warn(ioE.getMessage(), ioE);
            }
            scanTable.processColumns(scanTableFile, headers, notInteger, notDouble, firstDate, firstDatasetName);
            scanTable.addGroupColumn();
            scanTable.scannerTool.miner.setDisableSubMenus(!combineFileMode);

        } catch (NumberFormatException e) {
            log.warn(e.getMessage(), e);
        } finally {
            scanTable.processingTable = false;
        }
    }

    private static Optional<FileTableItem> parseStandardInfo(File scanDir, HashMap<String, String> fieldMap) {
        boolean hasAll = true;
        int nDim = 1;
        long eTime = 0;
        String sequence = "";
        int row = 0;
        boolean active = true;
        String fileName = "";
        for (String standardHeader : ScanTable.standardHeaders) {
            if (!fieldMap.containsKey(standardHeader)) {
                hasAll = false;
            } else {
                switch (standardHeader) {
                    case ScanTable.PATH_COLUMN_NAME -> fileName = fieldMap.get(standardHeader);
                    case ScanTable.NDIM_COLUMN_NAME -> nDim = Integer.parseInt(fieldMap.get(standardHeader));
                    case ScanTable.ROW_COLUMN_NAME -> row = Integer.parseInt(fieldMap.get(standardHeader));
                    case ScanTable.ETIME_COLUMN_NAME -> eTime = Long.parseLong(fieldMap.get(standardHeader));
                    case ScanTable.SEQUENCE_COLUMN_NAME -> sequence = fieldMap.get(standardHeader);
                    case ScanTable.ACTIVE_COLUMN_NAME -> active = fieldMap.get(standardHeader).equals("1");
                }
            }
        }
        String datasetName = "";
        if (fieldMap.containsKey(ScanTable.DATASET_COLUMN_NAME)) {
            datasetName = fieldMap.get(ScanTable.DATASET_COLUMN_NAME);
        }
        boolean noPath = (fileName == null) || fileName.isBlank();
        if (!hasAll && !noPath) {
            if ((scanDir == null) || scanDir.toString().isBlank()) {
                GUIUtils.warn("Load scan table", "No scan directory");
                return Optional.empty();
            }
            Path filePath = FileSystems.getDefault().getPath(scanDir.toString(), fileName);

            NMRData nmrData;
            try {
                nmrData = NMRDataUtil.getFID(filePath.toFile());
            } catch (IOException ioE) {
                GUIUtils.warn("Load scan table", "Couldn't load this file: " + filePath);
                return Optional.empty();
            }

            if (!fieldMap.containsKey(ScanTable.ETIME_COLUMN_NAME)) {
                eTime = nmrData.getDate();
            }
            if (!fieldMap.containsKey(ScanTable.SEQUENCE_COLUMN_NAME)) {
                sequence = nmrData.getSequence();
            }
            if (!fieldMap.containsKey(ScanTable.NDIM_COLUMN_NAME)) {
                nDim = nmrData.getNDim();
            }
        }
        var item = new FileTableItem(fileName, sequence, nDim, eTime, row, datasetName, fieldMap);
        item.setActive(active);

        return Optional.of(item);
    }

    private static void parseLine(String line, boolean[] notInteger, boolean[] notDouble, HashMap<String, String> fieldMap, String[] headers) {
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
    }

    void ensureAllDatasetsAdded() {
        PolyChart chart = scanTable.scannerTool.getChart();
        List<String> datasetNames = new ArrayList<>();
        for (var fileTableItem : scanTable.getItems()) {
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
        chart.updateDatasetsByNames(datasetNames);
    }

    public void combineDatasets(ScanTable scanTable) {
        List<Dataset> datasets = scanTable.getDatasetAttributesList().stream().map(DatasetAttributes::getDataset).toList();
        if (scanTable.currentChart.getDatasetAttributes().size() < 2) {
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
                scanTable.scannerLoader.loadFromDataset(scanTable);
            }
        }
    }

    public void processScanDir(ChartProcessor chartProcessor, boolean combineFileMode, ScanTable scanTable) {
        if ((chartProcessor == null) || !chartProcessor.hasCommands()) {
            GUIUtils.warn(ScanTable.SCANNER_ERROR, "Processing Script Not Configured");
            return;
        }

        if ((scanDir == null) || scanDir.toString().isEmpty()) {
            GUIUtils.warn(ScanTable.SCANNER_ERROR, "No scan directory");
            return;
        }
        String outDirName = GUIUtils.input("Output directory name", "output");
        Path outDirPath = Paths.get(scanDir.toString(), outDirName);
        File scanOutputDir = outDirPath.toFile();
        if (!scanOutputDir.exists() && !scanOutputDir.mkdir()) {
            GUIUtils.warn(ScanTable.SCANNER_ERROR, "Could not create output dir");
            return;
        }

        String combineFileName = GUIUtils.input("Output file name", "process");

        if (!scanOutputDir.exists() || !scanOutputDir.isDirectory() || !scanOutputDir.canWrite()) {
            GUIUtils.warn(ScanTable.SCANNER_ERROR, "Output dir is not a writable directory");
            return;
        }
        ObservableList<FileTableItem> fileTableItems = scanTable.tableView.getItems();
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

        PolyChart chart = scanTable.scannerTool.getChart();
        scanTable.processingTable = true;
        scanTable.tableView.getSelectionModel().getSelectedIndices().removeListener(scanTable.selectionListener);
        scanTable.tableView.getItems().removeListener(scanTable.filterItemListener);

        try (PythonInterpreter processInterp = new PythonInterpreter()) {
            List<String> fileNames = new ArrayList<>();

            String initScript = ChartProcessor.buildInitScript();
            processInterp.exec(initScript);

            int nDim = fileTableItems.getFirst().getNDim();
            String processScript = chartProcessor.buildScript(nDim);
            Processor processor = Processor.getProcessor();
            processor.keepDatasetOpen(false);

            int rowNum = 1;
            for (FileTableItem fileTableItem : fileTableItems) {
                fileTableItem.setDatasetName(null);
                fileTableItem.setDatasetAttributes(null);
            }
            scanTable.activeDatasetAttributes.clear();
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
            scanTable.updateFilter();
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
            scanTable.saveScanTable(saveTableFile);
            scanTable.scannerTool.miner.setDisableSubMenus(!combineFileMode);

        } finally {
            scanTable.tableView.getSelectionModel().getSelectedIndices().addListener(scanTable.selectionListener);
            scanTable.tableView.getItems().addListener(scanTable.filterItemListener);
            scanTable.getGroups();
            ensureAllDatasetsAdded();
            scanTable.selectionChanged();
            scanTable.processingTable = false;
            scanTable.refresh();
        }
    }

    public void saveScanTable(ScanTable scanTable) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Table File");
        if (scanDir != null) {
            fileChooser.setInitialDirectory(scanDir);
        }
        File file = fileChooser.showSaveDialog(popOver);
        if (file != null) {
            scanTable.saveScanTable(file);
        }
    }

    public void loadFromDataset(ScanTable scanTable) {
        PolyChart chart = scanTable.scannerTool.getChart();
        if (chart.getDatasetAttributes().size() > 1) {
            scanTable.scannerLoader.loadMultipleDatasets(scanTable);
            return;
        } else if (!chart.getDatasetAttributes().isEmpty()) {
            Dataset dataset = (Dataset) chart.getDataset();
            if (!chart.is1D() && ((dataset.getNDim() > 1) && (dataset.getNDim() == dataset.getNFreqDims()))) {
                scanTable.scannerLoader.loadMultipleDatasets(scanTable);
                return;
            }

        }
        DatasetBase dataset = chart.getDataset();
        if (dataset == null) {
            log.warn("Unable to load dataset, dataset is null.");
            return;
        }
        if (dataset.getNDim() < 2) {
            scanTable.scannerLoader.loadMultipleDatasets(scanTable);
            return;
        }
        setScanDir(null);
        // Need to disconnect listeners before updating fileListItem or the selectionListener and filterItemListeners
        // will be triggered during every iteration of the loop, greatly reducing performance
        scanTable.tableView.getSelectionModel().getSelectedIndices().removeListener(scanTable.selectionListener);
        scanTable.tableView.getItems().removeListener(scanTable.filterItemListener);
        scanTable.fileListItems.clear();
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
            scanTable.fileListItems.add(fileTableItem);
        }


        scanTable.tableView.getSelectionModel().getSelectedIndices().addListener(scanTable.selectionListener);
        scanTable.tableView.getItems().addListener(scanTable.filterItemListener);
        scanTable.columnTypes.put(ScanTable.PATH_COLUMN_NAME, "S");
        scanTable.columnTypes.put(ScanTable.SEQUENCE_COLUMN_NAME, "S");
        scanTable.columnTypes.put(ScanTable.NDIM_COLUMN_NAME, "I");
        scanTable.columnTypes.put(ScanTable.ROW_COLUMN_NAME, "I");
        scanTable.columnTypes.put(ScanTable.DATASET_COLUMN_NAME, "S");
        scanTable.columnTypes.put(ScanTable.ETIME_COLUMN_NAME, "I");
        scanTable.columnTypes.put(ScanTable.GROUP_COLUMN_NAME, "I");
        scanTable.columnTypes.put(ScanTable.ACTIVE_COLUMN_NAME, "B");
        Long firstDate = 0L;
        for (FileTableItem item : scanTable.fileListItems) {
            item.setDate(item.getDate() - firstDate);
        }
        scanTable.initTable();
        if (values != null) {
            scanTable.addTableColumn("value", "D");
        }
        scanTable.updateFilter();
        List<Integer> rows = new ArrayList<>();
        rows.add(0);
        // Load from Dataset assumes an arrayed dataset
        if ((dataset.getNFreqDims() > 2) || (dataset.getNFreqDims() == 0) && (dataset.getNDim() > 1)) {
            chart.getDisDimProperty().set(PolyChart.DISDIM.TwoD);
        } else {
            chart.getDisDimProperty().set(PolyChart.DISDIM.OneDX);
            chart.setDrawlist(rows);
        }

        scanTable.setDatasetAttributes();
    }

    public void loadMultipleDatasets(ScanTable scanTable) {
        PolyChart chart = scanTable.scannerTool.getChart();
        var datasetAttributesList = chart.getDatasetAttributes();
        int iRow = 0;
        HashMap<String, String> fieldMap = new HashMap<>();
        scanTable.tableView.getSelectionModel().getSelectedIndices().removeListener(scanTable.selectionListener);
        scanTable.tableView.getItems().removeListener(scanTable.filterItemListener);
        scanTable.fileListItems.clear();
        for (var datasetAttributes : datasetAttributesList) {
            Dataset dataset = datasetAttributes.getDataset();
            long eTime = 0;
            FileTableItem fileTableItem = new FileTableItem(dataset.getName(), "", dataset.getNDim(),
                    eTime, iRow + 1, dataset.getName(), fieldMap);
            fileTableItem.setDatasetAttributes(datasetAttributes);
            scanTable.fileListItems.add(fileTableItem);
            iRow++;
        }
        scanTable.tableView.getSelectionModel().getSelectedIndices().addListener(scanTable.selectionListener);
        scanTable.tableView.getItems().addListener(scanTable.filterItemListener);
        scanTable.columnTypes.put(ScanTable.PATH_COLUMN_NAME, "S");
        scanTable.columnTypes.put(ScanTable.SEQUENCE_COLUMN_NAME, "S");
        scanTable.columnTypes.put(ScanTable.NDIM_COLUMN_NAME, "I");
        scanTable.columnTypes.put(ScanTable.ROW_COLUMN_NAME, "I");
        scanTable.columnTypes.put(ScanTable.DATASET_COLUMN_NAME, "S");
        scanTable.columnTypes.put(ScanTable.ETIME_COLUMN_NAME, "I");
        scanTable.columnTypes.put(ScanTable.GROUP_COLUMN_NAME, "I");
        scanTable.columnTypes.put(ScanTable.ACTIVE_COLUMN_NAME, "B");
        scanTable.initTable(false);
        scanTable.updateFilter();
        scanTable.tableView.refresh();
    }
}
