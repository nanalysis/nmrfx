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
package org.nmrfx.processor.gui;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import javafx.util.converter.DoubleStringConverter;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;
import org.nmrfx.project.ProjectBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 *
 * @author johnsonb
 */
public class DatasetsController implements Initializable, PropertyChangeListener {

    private static final Logger log = LoggerFactory.getLogger(DatasetsController.class);

    private Stage stage;
    @FXML
    private ToolBar toolBar;
    @FXML
    private TableView<DatasetBase> tableView;

    private int dimNumber = 0;
    TableColumn dim1Column;
    Button valueButton;
    Button saveParButton;
    Button closeButton;
    Stage valueStage = null;
    TableView<ValueItem> valueTableView = null;
    DatasetBase valueDataset = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initToolBar();
        initTable();

    }

    public Stage getStage() {
        return stage;
    }

    public static DatasetsController create() {
        FXMLLoader loader = new FXMLLoader(DatasetsController.class.getResource("/fxml/DatasetsScene.fxml"));
        DatasetsController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.getController();
            controller.stage = stage;
            ProjectBase.addPropertyChangeListener(controller);
            stage.setTitle("Datasets");
            stage.show();
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
        }

        return controller;

    }

    void initToolBar() {
        ArrayList<ButtonBase> buttons = new ArrayList<>();

        saveParButton = new Button("Save Par");
        buttons.add(saveParButton);
        saveParButton.setOnAction(e -> savePars());
        saveParButton.setDisable(true);

        closeButton = new Button("Close");
        buttons.add(closeButton);
        closeButton.setOnAction(e -> closeDatasets());
        closeButton.setDisable(true);

        SplitMenuButton drawButton = new SplitMenuButton();
        drawButton.setText("Draw");
        drawButton.setOnAction(this::drawDataset);
        MenuItem overlayItem = new MenuItem("Overlay");
        overlayItem.setOnAction(this::drawDataset);
        MenuItem gridItem = new MenuItem("Grid");
        gridItem.setOnAction(e -> gridDataset(GridPaneCanvas.ORIENTATION.GRID));
        MenuItem horizontalItem = new MenuItem("Horizontal");
        horizontalItem.setOnAction(e -> gridDataset(GridPaneCanvas.ORIENTATION.HORIZONTAL));
        MenuItem verticalItem = new MenuItem("Vertical");
        verticalItem.setOnAction(e -> gridDataset(GridPaneCanvas.ORIENTATION.VERTICAL));
        drawButton.getItems().addAll(overlayItem, horizontalItem, verticalItem, gridItem);
        for (var menuItem: drawButton.getItems()) {
            IntegerBinding sizeProperty = Bindings.size(tableView.getSelectionModel().getSelectedIndices());
            menuItem.disableProperty().bind(sizeProperty.lessThan(2));
        }
        buttons.add(drawButton);
        valueButton = new Button("Values");
        valueButton.setOnAction(e -> makeValueTable());
        buttons.add(valueButton);
        valueButton.setDisable(true);


        for (ButtonBase button : buttons) {
            button.getStyleClass().add("toolButton");
            IntegerBinding sizeProperty = Bindings.size(tableView.getSelectionModel().getSelectedIndices());
            if (button == valueButton) {
                button.disableProperty().bind(sizeProperty.isNotEqualTo(1));
            } else {
                button.disableProperty().bind(sizeProperty.isEqualTo(0));
            }
        }
        toolBar.getItems().addAll(buttons);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        List<DatasetBase> datasetList = ProjectBase.getActive().getDatasets();
        if (datasetList instanceof ObservableList) {
            setDatasetList((ObservableList<DatasetBase>) datasetList);
        }
    }

    class DatasetDoubleFieldTableCell extends TextFieldTableCell<DatasetBase, Double> {

        DatasetDoubleFieldTableCell(StringConverter<Double> converter) {
            super(converter);
        }

        @Override
        public void commitEdit(Double newValue) {
            String column = getTableColumn().getText();
            DatasetBase dataset = getTableRow().getItem();
            super.commitEdit(newValue);
            switch (column) {
                case "level":
                    dataset.setLvl(newValue);
                    break;
                case "scale":
                    dataset.setScale(newValue);
                    break;
                case "ref":
                    dataset.setRefValue(getDimNum(), newValue);
                    break;
            }
        }

    }

    class DatasetStringFieldTableCell extends TextFieldTableCell<DatasetBase, String> {

        DatasetStringFieldTableCell(StringConverter<String> converter) {
            super(converter);
        }

        @Override
        public void commitEdit(String newValue) {
            String column = getTableColumn().getText();
            DatasetBase dataset = getTableRow().getItem();
            super.commitEdit(newValue);
            if ("label".equals(column)) {
                dataset.setLabel(getDimNum(), newValue);
            }
        }

    }

    void initTable() {
        DoubleStringConverter dsConverter = new DoubleStringConverter();
        StringConverter<String> sConverter = new DefaultStringConverter();
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<DatasetBase, String> fileNameCol = new TableColumn<>("dataset");
        fileNameCol.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        fileNameCol.setEditable(false);

        TableColumn<DatasetBase, Double> levelCol = new TableColumn<>("level");
        levelCol.setCellValueFactory(new PropertyValueFactory<>("lvl"));
        levelCol.setCellFactory(tc -> new DatasetDoubleFieldTableCell(dsConverter));

        TableColumn<DatasetBase, Double> scaleCol = new TableColumn<>("scale");
        scaleCol.setCellValueFactory(new PropertyValueFactory<>("scale"));
        scaleCol.setCellFactory(tc -> new DatasetDoubleFieldTableCell(dsConverter));

        TableColumn<DatasetBase, Double> noiseCol = new TableColumn<>("noise");
        noiseCol.setCellValueFactory(new PropertyValueFactory<>("noiseLevel"));
        noiseCol.setCellFactory(tc -> new DatasetDoubleFieldTableCell(dsConverter));

        TableColumn<DatasetBase, Integer> nDimCol = new TableColumn<>("nD");
        nDimCol.setCellValueFactory(new PropertyValueFactory<>("nDim"));
        nDimCol.setPrefWidth(25);
        nDimCol.setEditable(false);

        TableColumn<DatasetBase, Boolean> posDrawOnCol = new TableColumn<>("on");
        posDrawOnCol.setCellValueFactory(new PropertyValueFactory<>("negDrawOn"));
        posDrawOnCol.setCellFactory(col -> new CheckBoxTableCell<>(index -> {
            BooleanProperty active = new SimpleBooleanProperty(tableView.getItems().get(index).getPosDrawOn());
            active.addListener((obs, wasActive, isNowActive) -> {
                DatasetBase item = tableView.getItems().get(index);
                item.setPosDrawOn(isNowActive);
            });
            return active;
        }));
        posDrawOnCol.setPrefWidth(25);
        posDrawOnCol.setMaxWidth(25);
        posDrawOnCol.setResizable(false);

        TableColumn<DatasetBase, Color> posColorCol = new TableColumn<>("color");
        posColorCol.setPrefWidth(50);
        posColorCol.setCellValueFactory((CellDataFeatures<DatasetBase, Color> p) -> new ReadOnlyObjectWrapper<>(Color.web(p.getValue().getPosColor())));
        posColorCol.setCellFactory((TableColumn<DatasetBase, Color> column) -> new TableCell<>() {
            @Override
            protected void updateItem(Color item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || (item == null)) {
                    setGraphic(null);
                } else {
                    final ColorPicker cp = new ColorPicker();
                    cp.setValue(item);
                    setGraphic(cp);
                    cp.setOnAction((javafx.event.ActionEvent t) -> {
                        getTableView().edit(getTableRow().getIndex(), column);
                        commitEdit(cp.getValue());
                    });
                }
            }

            @Override
            public void commitEdit(Color item) {
                super.commitEdit(item);
                System.out.println("commit " + item.toString() + " " + getTableRow().getItem());
                DatasetBase dataset = getTableRow().getItem();
                dataset.setPosColor(item.toString());
            }
        });

        TableColumn<DatasetBase, Boolean> negDrawOnCol = new TableColumn<>("on");
        negDrawOnCol.setCellValueFactory(new PropertyValueFactory<>("negDrawOn"));
        negDrawOnCol.setCellFactory(col -> new CheckBoxTableCell<>(index -> {
            BooleanProperty active = new SimpleBooleanProperty(tableView.getItems().get(index).getNegDrawOn());
            active.addListener((obs, wasActive, isNowActive) -> {
                DatasetBase item = tableView.getItems().get(index);
                item.setNegDrawOn(isNowActive);
            });
            return active;
        }));
        negDrawOnCol.setPrefWidth(25);
        negDrawOnCol.setMaxWidth(25);
        negDrawOnCol.setResizable(false);

        TableColumn<DatasetBase, Color> negColorCol = new TableColumn<>("color");
        negColorCol.setPrefWidth(50);
        negColorCol.setCellValueFactory((CellDataFeatures<DatasetBase, Color> p) -> new ReadOnlyObjectWrapper<>(Color.web(p.getValue().getNegColor())));
        negColorCol.setCellFactory((TableColumn<DatasetBase, Color> column) -> new TableCell<>() {
            @Override
            protected void updateItem(Color item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || (item == null)) {
                    setGraphic(null);
                } else {
                    final ColorPicker cp = new ColorPicker();
                    cp.setValue(item);
                    setGraphic(cp);
                    cp.setOnAction((javafx.event.ActionEvent t) -> {
                        getTableView().edit(getTableRow().getIndex(), column);
                        commitEdit(cp.getValue());
                    });
                }
            }

            @Override
            public void commitEdit(Color item) {
                super.commitEdit(item);
                System.out.println("commit " + item.toString() + " " + getTableRow().getItem());
                DatasetBase dataset = getTableRow().getItem();
                dataset.setNegColor(item.toString());
            }
        });

        TableColumn<DatasetBase, Integer> sizeCol = new TableColumn<>("size");
        sizeCol.setCellValueFactory((CellDataFeatures<DatasetBase, Integer> p) -> {
            DatasetBase dataset = p.getValue();
            int size = 0;
            int iDim = getDimNum();
            if (dataset.getNDim() > iDim) {
                size = dataset.getSizeReal(iDim);
            }
            return new ReadOnlyObjectWrapper<>(size);
        });
        sizeCol.setPrefWidth(50);

        TableColumn<DatasetBase, String> labelCol = new TableColumn<>("label");
        labelCol.setCellFactory(tc -> new DatasetStringFieldTableCell(sConverter));
        labelCol.setCellValueFactory((CellDataFeatures<DatasetBase, String> p) -> {
            DatasetBase dataset = p.getValue();
            String label = "";
            int iDim = getDimNum();
            if (dataset.getNDim() > iDim) {
                label = dataset.getLabel(iDim);
            }
            return new ReadOnlyObjectWrapper<>(label);
        });

        labelCol.setPrefWidth(50);

        TableColumn<DatasetBase, Double> sfCol = new TableColumn<>("sf");
        sfCol.setCellValueFactory((CellDataFeatures<DatasetBase, Double> p) -> {
            DatasetBase dataset = p.getValue();
            double sf = 0;
            int iDim = getDimNum();
            if (dataset.getNDim() > iDim) {
                sf = dataset.getSf(iDim);
            }
            return new ReadOnlyObjectWrapper<>(sf);
        });

        sfCol.setPrefWidth(150);

        TableColumn<DatasetBase, Double> swCol = new TableColumn<>("sw");
        swCol.setCellValueFactory((CellDataFeatures<DatasetBase, Double> p) -> {
            DatasetBase dataset = p.getValue();
            double sw = 0;
            int iDim = getDimNum();
            if (dataset.getNDim() > iDim) {
                sw = dataset.getSw(iDim);
            }
            return new ReadOnlyObjectWrapper<>(sw);
        });

        swCol.setPrefWidth(75);

        TableColumn<DatasetBase, Double> refCol = new TableColumn<>("ref");
        refCol.setCellFactory(tc -> new DatasetDoubleFieldTableCell(dsConverter));
        refCol.setCellValueFactory((CellDataFeatures<DatasetBase, Double> p) -> {
            DatasetBase dataset = p.getValue();
            double ref = 0;
            int iDim = getDimNum();
            if (dataset.getNDim() > iDim) {
                ref = dataset.getRefValue(iDim);
            }
            return new ReadOnlyObjectWrapper<>(ref);
        });

        refCol.setPrefWidth(75);

        var positiveColumn = new TableColumn("Positive");
        var negativeColumn = new TableColumn("Negative");
        dim1Column = new TableColumn("Dim1");

        Polygon polygon = new Polygon();
        polygon.getPoints().addAll(2.0, 2.0,
                12.0, 2.0,
                7.0, 10.0);
        polygon.setFill(Color.BLACK);
        Tooltip tip = new Tooltip("Right click for dimension choice");
        Tooltip.install(polygon, tip);

        dim1Column.setGraphic(polygon);
        dim1Column.setPrefWidth(400);
        positiveColumn.getColumns().setAll(posDrawOnCol, posColorCol);
        negativeColumn.getColumns().setAll(negDrawOnCol, negColorCol);
        dim1Column.getColumns().setAll(labelCol, sizeCol, sfCol, swCol, refCol);
        ContextMenu menu = new ContextMenu();
        int maxDim = 6;
        for (int i = 0; i < maxDim; i++) {
            MenuItem dimItem = new MenuItem(String.valueOf(i + 1));
            final int iDim = i;
            dimItem.setOnAction(e -> setDimNum(iDim));
            menu.getItems().add(dimItem);
        }
        dim1Column.setContextMenu(menu);
        tableView.getColumns().setAll(fileNameCol, nDimCol, levelCol, scaleCol, noiseCol, positiveColumn, negativeColumn, dim1Column);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setDatasetList((ObservableList<DatasetBase>) ProjectBase.getActive().getDatasets());
    }

    private int getDimNum() {
        return dimNumber;
    }

    private void setDimNum(int i) {
        dimNumber = i;
        dim1Column.setText("Dim " + (dimNumber + 1));
        tableView.refresh();
    }

    public void setDatasetList(ObservableList<DatasetBase> datasets) {
        if (tableView == null) {
            System.out.println("null table");
        } else {
            tableView.setItems(datasets);
        }
    }

    void drawDataset(ActionEvent e) {
        ObservableList<DatasetBase> datasets = tableView.getSelectionModel().getSelectedItems();
        FXMLController controller = FXMLController.getActiveController();
        PolyChart chart = controller.getActiveChart();
        if ((chart != null) && chart.getDataset() != null) {
            controller = FXMLController.create();
        }
        boolean appendFile = false;
        for (DatasetBase dataset : datasets) {
            controller.addDataset(dataset, appendFile, false);
            appendFile = true;
        }
    }

    void gridDataset(GridPaneCanvas.ORIENTATION orient) {
        ObservableList<DatasetBase> datasets = tableView.getSelectionModel().getSelectedItems();
        FXMLController controller = FXMLController.getActiveController();
        PolyChart chart = controller.getActiveChart();
        if ((chart != null) && chart.getDataset() != null) {
            controller = FXMLController.create();
        }
        controller.setNCharts(datasets.size());
        controller.arrange(orient);
        for (int i = 0; i < datasets.size(); i++) {
            DatasetBase dataset = datasets.get(i);
            PolyChart chartActive = controller.charts.get(i);
            controller.setActiveChart(chartActive);
            controller.addDataset(dataset, false, false);
        }
    }

    public static class ValueItem {

        int index;
        double value;

        public ValueItem(int index, double value) {
            this.index = index;
            this.value = value;
        }

        public double getValue() {
            return value;
        }

        public void setValue(double value) {
            this.value = value;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }
    }

    class ValueItemDoubleFieldTableCell extends TextFieldTableCell<ValueItem, Double> {

        ValueItemDoubleFieldTableCell(StringConverter<Double> converter) {
            super(converter);
        }

        @Override
        public void commitEdit(Double newValue) {
            String column = getTableColumn().getText();
            ValueItem value = getTableRow().getItem();
            super.commitEdit(newValue);
            if ("Value".equals(column)) {
                value.setValue(newValue);
                saveValueTable();
            }
        }

    }

    void saveValueTable() {
        List<ValueItem> items = valueTableView.getItems();
        if (valueDataset != null) {
            double[] values = items.stream().mapToDouble(ValueItem::getValue).toArray();
            int nDim = valueDataset.getNDim();
            valueDataset.setValues(nDim - 1, values);
        }
    }

    public void updateValueTable() {
        ObservableList<DatasetBase> datasets = tableView.getSelectionModel().getSelectedItems();
        ObservableList<ValueItem> valueList = FXCollections.observableArrayList();
        valueDataset = null;
        if (datasets.size() == 1) {
            valueDataset = datasets.get(0);
            int nDim = valueDataset.getNDim();

            for (int i = 0; i < nDim; i++) {
                double[] values = valueDataset.getValues(i);
                if ((values != null) && (values.length > 1)) {
                    System.out.println(i + " " + values.length);
                    for (int j = 0; j < values.length; j++) {
                        ValueItem item = new ValueItem(j, values[j]);
                        valueList.add(item);
                    }
                    break;
                }
            }
            if (valueList.isEmpty()) {
                if (valueDataset.getNFreqDims() < nDim) {
                    for (int i = 0; i < valueDataset.getSizeReal(nDim - 1); i++) {
                        valueList.add(new ValueItem(i, 0.0));
                    }
                }
            }
        }
        valueTableView.setItems(valueList);
    }

    public void makeValueTable() {
        if (valueTableView == null) {
            DoubleStringConverter dsConverter = new DoubleStringConverter();
            valueStage = new Stage(StageStyle.DECORATED);
            BorderPane borderPane = new BorderPane();
            Scene scene = new Scene(borderPane);
            valueStage.setScene(scene);
            valueStage.setTitle("DatasetBase Values");
            valueStage.show();

            valueTableView = new TableView<>();
            valueTableView.setEditable(true);
            borderPane.setCenter(valueTableView);
            TableColumn<ValueItem, Integer> indexColumn = new TableColumn<>("Index");
            indexColumn.setEditable(false);
            TableColumn<ValueItem, Double> valueColumn = new TableColumn<>("Value");
            valueColumn.setEditable(true);

            indexColumn.setCellValueFactory(new PropertyValueFactory<>("index"));
            valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
            valueColumn.setCellFactory(tc -> new ValueItemDoubleFieldTableCell(dsConverter));
            //valueColumn.setCellFactory(tc -> new DatasetDoubleFieldTableCell(dsConverter));

            valueTableView.getColumns().addAll(indexColumn, valueColumn);
        }
        valueStage.show();
        valueStage.toFront();
        updateValueTable();
    }

    void savePars() {
        ObservableList<DatasetBase> datasets = tableView.getSelectionModel().getSelectedItems();
        for (DatasetBase dataset : datasets) {
            dataset.writeParFile();
        }

    }

    void closeDatasets() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Close selected datasets");
        Optional<ButtonType> response = alert.showAndWait();
        if (response.isPresent() && response.get().getText().equals("OK")) {
            ObservableList<DatasetBase> datasets = tableView.getSelectionModel().getSelectedItems();
            for (DatasetBase dataset : datasets) {
                dataset.close();
            }
        }
    }

    void refresh() {
        tableView.refresh();
    }
}
