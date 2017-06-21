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

 /*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui.controls;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.table.TableFilter;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.gui.ChartProcessor;
import org.nmrfx.processor.gui.ConsoleController;
import org.nmrfx.processor.gui.MainApp;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.ProcessorController;
import org.nmrfx.processor.gui.ScannerController;
import org.python.util.PythonInterpreter;
import org.renjin.primitives.vector.RowNamesVector;
import org.renjin.sexp.AttributeMap;
import org.renjin.sexp.DoubleVector;
import org.renjin.sexp.Environment;
import org.renjin.sexp.IntVector;
import org.renjin.sexp.ListVector;
import org.renjin.sexp.SEXP;
import org.renjin.sexp.StringArrayVector;
import org.renjin.sexp.StringVector;
import org.renjin.sexp.Symbols;

/**
 *
 * @author Bruce Johnson
 */
public class ScanTable {

    ScannerController scannerController;
    TableView<FileTableItem> tableView;
    TableFilter fileTableFilter;
    TableFilter.Builder builder = null;
    String scanDir = null;
    String scanOutputDir = null;
    PopOver popOver = new PopOver();
    ObservableList<FileTableItem> fileListItems = FXCollections.observableArrayList();
    HashMap<String, String> columnTypes = new HashMap<>();
    HashMap<String, String> columnDescriptors = new HashMap<>();

    public ScanTable(ScannerController controller, TableView<FileTableItem> tableView) {
        this.scannerController = controller;
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
//        TableFilter.Builder builder = TableFilter.forTableView(tableView);
//        fileTableFilter = builder.apply();
        setDragHandlers(tableView);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.getSelectionModel().selectedIndexProperty().addListener(e -> {
            ObservableList<Integer> selected = tableView.getSelectionModel().getSelectedIndices();
            selectionChanged(selected);

        });
        columnTypes.put("path", "S");
        columnTypes.put("sequence", "S");
        columnTypes.put("ndim", "I");
        columnTypes.put("row", "I");
        columnTypes.put("etime", "I");

    }

    final protected void selectionChanged(ObservableList<Integer> selected) {
        ProcessorController processorController = scannerController.getFXMLController().getProcessorController(false);
        if ((processorController == null) || processorController.isViewingDataset()) {
            if (!selected.isEmpty()) {
                PolyChart chart = scannerController.getChart();
                List<Integer> rows = new ArrayList<>();
                for (Integer index : selected) {
                    FileTableItem fileTableItem = (FileTableItem) tableView.getItems().get(index);
                    Integer row = fileTableItem.getRow();
                    if (row != null) {
                        rows.add(row - 1); // rows index from 1
                    }
                }
                chart.setDrawlist(rows);
                chart.refresh();
            }
        }

    }

    final protected void setDragHandlers(Node mouseNode) {

        mouseNode.setOnDragOver(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                mouseDragOver(event);
            }
        }
        );
        mouseNode.setOnDragDropped(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                mouseDragDropped(event);
            }
        }
        );
        mouseNode.setOnDragExited(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                mouseNode.setStyle("-fx-border-color: #C6C6C6;");
            }
        }
        );
    }

    private void mouseDragDropped(final DragEvent e) {
        final Dragboard db = e.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            success = true;

            // Only get the first file from the list
            final File file = db.getFiles().get(0);
            if (file.isDirectory()) {
                scanDir = file.getAbsolutePath();
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        int beginIndex = scanDir.length() + 1;
                        ArrayList<String> nmrFiles = NMRDataUtil.findNMRDirectories(scanDir);
                        String[] headers = {};
                        updateTable(headers);
                        loadScanFiles(nmrFiles, beginIndex);
                    }
                });
            } else {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        loadScanTable(file);
                    }
                });

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

    public void loadScanFiles(Stage stage) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Scan Directory");
        File selectedDir = directoryChooser.showDialog(stage);
        if (selectedDir != null) {
            scanDir = selectedDir.getPath();
            int beginIndex = scanDir.length() + 1;
            ArrayList<String> nmrFiles = NMRDataUtil.findNMRDirectories(scanDir);
            String[] headers = {};
            updateTable(headers);
            loadScanFiles(nmrFiles, beginIndex);
        }
    }

    public void processScanDir(Stage stage, ChartProcessor chartProcessor, boolean combineFileMode) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Scan Output Directory");
        File selectedDir = directoryChooser.showDialog(stage);
        if (selectedDir != null) {
            scanOutputDir = selectedDir.getPath();
            ObservableList<FileTableItem> fileTableItems = tableView.getItems();
            ArrayList<String> fileNames = new ArrayList<>();
            int rowNum = 1;
            for (FileTableItem fileTableItem : fileTableItems) {
                fileNames.add(fileTableItem.getFileName());
                fileTableItem.setRow(rowNum++);
            }
            String script = chartProcessor.buildMultiScript(scanDir, scanOutputDir, fileNames, combineFileMode);
            PythonInterpreter processInterp = new PythonInterpreter();
            processInterp.exec("from pyproc import *");
            processInterp.exec("useProcessor()");
            processInterp.exec(script);
            ProcessorController processorController = scannerController.getFXMLController().getProcessorController(true);

            processorController.viewDatasetInApp();
        }
    }

    public void openSelectedListFile(String scriptString) {
        int selItem = tableView.getSelectionModel().getSelectedIndex();
        if (selItem >= 0) {
            FileTableItem fileTableItem = (FileTableItem) tableView.getItems().get(selItem);
            String fileName = fileTableItem.getFileName();
            String filePath = Paths.get(scanDir, fileName).toString();

            scannerController.getChart().getFXMLController().openFile(filePath, false, false);
            ProcessorController processorController = scannerController.getFXMLController().getProcessorController(true);
            processorController.parseScript(scriptString);
        }
    }

    private void loadScanFiles(ArrayList<String> nmrFiles, int beginIndex) {
        fileListItems.clear();
        long firstDate = Long.MAX_VALUE;
        for (String filePath : nmrFiles) {
            NMRData nmrData = null;
            try {
                nmrData = NMRDataUtil.getNMRData(filePath);
            } catch (IOException ioE) {

            }
            if (nmrData != null) {
                long date = nmrData.getDate();
                if (date < firstDate) {
                    firstDate = date;
                }
                fileListItems.add(new FileTableItem(filePath.substring(beginIndex), nmrData.getSequence(), nmrData.getNDim(), nmrData.getDate(), 0));
            }
        }
        for (FileTableItem item : fileListItems) {
            item.setDate(item.getDate() - firstDate);
        }
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

    private void loadScanTable(File file) {
        fileListItems.clear();
        scanDir = file.getParent();
        long firstDate = Long.MAX_VALUE;
        int iLine = 0;
        String[] headers = null;
        HashMap<String, String> fieldMap = new HashMap();
        boolean[] notDouble = null;
        boolean[] notInteger = null;
        String[] standardHeaders = {"path", "sequence", "row", "etime", "ndim"};
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (iLine == 0) {
                    headers = line.split("\t");
                    notDouble = new boolean[headers.length];
                    notInteger = new boolean[headers.length];
//                    for (int i = 0; i < headers.length; i++) {
//                        headers[i] = getNextColumnName(headers[i]);
//
//                    }
//                    updateTable(headers);
                } else {
                    String[] fields = line.split("\t");
                    for (int iField = 0; iField < fields.length; iField++) {
                        fields[iField] = fields[iField].trim();
                        try {
                            int fieldValue = Integer.parseInt(fields[iField]);
                        } catch (NumberFormatException nfE) {
                            notInteger[iField] = true;
                            try {
                                double fieldValue = Double.parseDouble(fields[iField]);
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
                    if (!hasAll) {
                        if ((fileName == null) || (fileName.length() == 0)) {
                            System.out.println("No path field or value");
                            return;
                        }
                        Path filePath = FileSystems.getDefault().getPath(scanDir, fileName);

                        NMRData nmrData = null;
                        try {
                            nmrData = NMRDataUtil.getNMRData(filePath.toString());
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

                    fileListItems.add(new FileTableItem(fileName, sequence, nDim, eTime, row, fieldMap));
                }

                iLine++;
            }
        } catch (IOException ioE) {

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
        columnTypes.put("etime", "I");

        for (FileTableItem item : fileListItems) {
            item.setDate(item.getDate() - firstDate);
            item.setTypes(headers, notDouble, notInteger);
        }
        updateTable(headers);
        fileTableFilter.resetFilter();
        updateDataFrame();

    }

    public void saveScanTable() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Table File");
        File file = fileChooser.showSaveDialog(popOver);
        if (file != null) {
            saveScanTable(file);
        }
    }

    private void saveScanTable(File file) {
        Charset charset = Charset.forName("US-ASCII");
        ObservableList<TableColumn<FileTableItem, ?>> columns = tableView.getColumns();
        List<String> headers = new ArrayList<>();
        for (TableColumn column : columns) {
            String name = column.getText();
            headers.add(name);
        }

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
        }
    }

    public String getNextColumnName(String columnDescriptor) {
        int nChars = columnDescriptor.length();
        boolean measureType = false;
        if ((nChars > 4)) {
            String start = columnDescriptor.substring(0, 4);
            if (start.equals("vol_") || start.equals("min_") || start.equals("max_") || start.equals("ext_")) {
                measureType = true;
            }
        }
        if (!measureType) {
            return "";
        }
        String columnName = columnDescriptors.get(columnDescriptor);
        int maxColumn = -1;
        if (columnName == null) {
            for (String name : columnTypes.keySet()) {
                Integer columnNum = null;
                if (name.startsWith("V.")) {
                    try {
                        int colonPos = name.indexOf(":");
                        columnNum = Integer.parseInt(name.substring(2,colonPos));
                        if (columnNum > maxColumn) {
                            maxColumn = columnNum;
                        }
                    } catch (NumberFormatException nfE) {
                        columnNum = null;
                    }
                }
            }
            columnName = "V." + (maxColumn + 1);
            columnDescriptors.put(columnDescriptor, columnName);
        }
        return columnName;
    }

    public void addTableColumn(String newName, String type) {
        ObservableList<TableColumn<FileTableItem, ?>> columns = tableView.getColumns();
        boolean present = false;
        String[] headers = new String[columns.size() + 1];
        int i = 0;
        for (TableColumn column : columns) {
            String name = column.getText();
            if (name.equals(newName)) {
                present = true;
                break;
            }
            headers[i++] = name;
        }

        if (!present) {
            headers[i] = newName;
            columnTypes.put(headers[i], type);
            updateTable(headers);
        } else {
        }

    }

    private void updateTable(String[] headers) {
        TableColumn<FileTableItem, String> fileColumn = new TableColumn<>("path");
        TableColumn<FileTableItem, String> seqColumn = new TableColumn<>("sequence");
        TableColumn<FileTableItem, Number> nDimColumn = new TableColumn<>("ndim");
        TableColumn<FileTableItem, Long> dateColumn = new TableColumn<>("etime");
        TableColumn<FileTableItem, Number> rowColumn = new TableColumn<>("row");

        fileColumn.setCellValueFactory((e) -> new SimpleStringProperty(e.getValue().getFileName()));
        seqColumn.setCellValueFactory((e) -> new SimpleStringProperty(e.getValue().getSeqName()));
        nDimColumn.setCellValueFactory((e) -> new SimpleIntegerProperty(e.getValue().getNDim()));
        rowColumn.setCellValueFactory((e) -> new SimpleIntegerProperty(e.getValue().getRow()));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("Date"));
        tableView.getColumns().clear();
        tableView.getColumns().addAll(fileColumn, seqColumn, nDimColumn, dateColumn, rowColumn);
        for (String header : headers) {
            if (header.equalsIgnoreCase("path") || header.equalsIgnoreCase("Sequence") || header.equalsIgnoreCase("nDim") || header.equalsIgnoreCase("eTime") || header.equalsIgnoreCase("row") || header.equalsIgnoreCase("fid")) {
                continue;
            }
            String type = columnTypes.get(header);
            if (type == null) {
                type = "S";
                System.out.println("No type for " + header);
            }
            if (type.equals("D")) {
                TableColumn<FileTableItem, Number> doubleExtraColumn = new TableColumn<>(header);
                doubleExtraColumn.setCellValueFactory((e) -> new SimpleDoubleProperty(e.getValue().getDoubleExtra(header)));
                tableView.getColumns().add(doubleExtraColumn);
            } else if (type.equals("I")) {
                TableColumn<FileTableItem, Number> intExtraColumn = new TableColumn<>(header);
                intExtraColumn.setCellValueFactory((e) -> new SimpleIntegerProperty(e.getValue().getIntegerExtra(header)));
                tableView.getColumns().add(intExtraColumn);
            } else {
                TableColumn<FileTableItem, String> extraColumn = new TableColumn<>(header);
                extraColumn.setCellValueFactory((e) -> new SimpleStringProperty(String.valueOf(e.getValue().getExtra(header))));
                tableView.getColumns().add(extraColumn);

            }
        }
        updateFilter();
        updateDataFrame();
    }

    public void saveFilters() {
        fileTableFilter.getColumnFilters();
    }

    public void updateFilter() {
        tableView.setItems(fileListItems);
        builder = TableFilter.forTableView(tableView);
        fileTableFilter = builder.apply();
        fileTableFilter.resetFilter();
    }

    public ObservableList<FileTableItem> getItems() {
        return fileListItems;
    }

    class DoubleColumnVector extends DoubleVector {

        String name;
        Function<FileTableItem, Double> getter;

        public DoubleColumnVector(AttributeMap attributes) {
            super(attributes);
        }

        DoubleColumnVector(String name, Function<FileTableItem, Double> getter) {
            this.name = name;
            this.getter = getter;
        }

        @Override
        protected SEXP cloneWithNewAttributes(AttributeMap am) {
            DoubleColumnVector clone = new DoubleColumnVector(am);
            clone.name = name;
            clone.getter = getter;
            return clone;
        }

        @Override
        public double getElementAsDouble(int i) {
            FileTableItem item = fileListItems.get(i);
            return getter.apply(item);
        }

        @Override
        public int length() {
            return fileListItems.size();
        }

        @Override
        public boolean isConstantAccessTime() {
            return true;
        }

    }

    class IntColumnVector extends IntVector {

        String name;
        Function<FileTableItem, Integer> getter;

        public IntColumnVector(AttributeMap attributes) {
            super(attributes);
        }

        IntColumnVector(String name, Function<FileTableItem, Integer> getter) {
            this.name = name;
            this.getter = getter;
        }

        @Override
        protected SEXP cloneWithNewAttributes(AttributeMap am) {
            IntColumnVector clone = new IntColumnVector(am);
            clone.name = name;
            clone.getter = getter;
            return clone;
        }

        @Override
        public int getElementAsInt(int i) {
            FileTableItem item = fileListItems.get(i);
            return getter.apply(item);
        }

        @Override
        public int length() {
            return fileListItems.size();
        }

        @Override
        public boolean isConstantAccessTime() {
            return true;
        }

    }

    class StringColumnVector extends StringVector {

        String name;
        Function<FileTableItem, String> getter;

        public StringColumnVector(AttributeMap attributes) {
            super(attributes);
        }

        public StringColumnVector(String name, Function<FileTableItem, String> getter) {
            super(AttributeMap.EMPTY);
            this.name = name;
            this.getter = getter;
        }

        @Override
        public int length() {
            return fileListItems.size();
        }

        @Override
        public boolean isConstantAccessTime() {
            return true;
        }

        @Override
        public String getElementAsString(int i) {
            FileTableItem item = fileListItems.get(i);
            if (item == null) {
                System.out.println("null item " + i + " column " + name);
                return NA;
            }
            return getter.apply(item);
        }

        @Override
        protected StringColumnVector cloneWithNewAttributes(AttributeMap am) {
            StringColumnVector clone = new StringColumnVector(am);
            clone.name = name;
            clone.getter = getter;
            return clone;
        }

    }

    public void updateDataFrame() {
        ObservableList<TableColumn<FileTableItem, ?>> columns = tableView.getColumns();

        ListVector.NamedBuilder builder = new ListVector.NamedBuilder();

        int iCol = 0;
        for (TableColumn column : columns) {
            String fullName = column.getText();
            int colonPos = fullName.indexOf(":");
            final String name;
            if (colonPos != -1) {
                name = fullName.substring(0,colonPos);
            } else {
                name = fullName;
            }
            String type = columnTypes.get(fullName);
            if (type == null) {
                System.out.println("null type " + name);
                type = "S";
            }
            switch (type) {
                case "D": {
                    DoubleColumnVector dVec = new DoubleColumnVector(name, item -> item.getDoubleExtra(fullName));
                    builder.add(name, dVec);
                    break;
                }
                case "I": {
                    IntColumnVector iVec;
                    if (name.equalsIgnoreCase("row")) {
                        iVec = new IntColumnVector(name, item -> item.getRow());
                    } else if (name.equalsIgnoreCase("ndim")) {
                        iVec = new IntColumnVector(name, item -> item.getNDim());
                    } else if (name.equalsIgnoreCase("etime")) {
                        iVec = new IntColumnVector(name, item -> item.getDate().intValue());
                    } else {
                        iVec = new IntColumnVector(name, item -> item.getIntegerExtra(fullName));
                    }
                    builder.add(name, iVec);
                    break;
                }

                case "S": {
                    StringColumnVector sVec;
                    if (name.equalsIgnoreCase("path")) {
                        sVec = new StringColumnVector(name, item -> item.getFileName());
                    } else if (name.equalsIgnoreCase("sequence")) {
                        sVec = new StringColumnVector(name, item -> item.getSeqName());
                    } else {
                        sVec = new StringColumnVector(name, item -> item.getExtra(fullName));
                    }
                    builder.add(name, sVec);
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Invalid column type");
                }

            }
            iCol++;
        }
        builder.setAttribute(Symbols.ROW_NAMES, new RowNamesVector(fileListItems.size()));
        builder.setAttribute(Symbols.CLASS, StringArrayVector.valueOf("data.frame"));

        ListVector dFrame = builder.build();

        ProcessorController processorController = scannerController.getFXMLController().getProcessorController(false);
        ConsoleController consoleController = MainApp.getConsoleController();
        if (consoleController == null) {
            System.out.println("null proccon");
        } else {
            Environment env = consoleController.getREnvironment();
            if (env == null) {
                System.out.println("null env");
            } else {
                env.setVariableUnsafe("scntbl", dFrame);
            }
        }
    }
}
