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
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import javafx.util.converter.DoubleStringConverter;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.processor.gui.controls.GridPaneCanvas;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.utils.ColumnMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URL;
import java.util.*;
import java.util.function.DoubleUnaryOperator;

/**
 * @author johnsonb
 */
public class DatasetsController implements Initializable, StageBasedController, PropertyChangeListener {

    private static final Logger log = LoggerFactory.getLogger(DatasetsController.class);
    private static final Map<String, double[]> savedValues = new HashMap<>();
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

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    public static DatasetsController create() {
        DatasetsController controller = Fxml.load(DatasetsController.class, "DatasetsScene.fxml")
                .withNewStage("Datasets")
                .getController();
        ProjectBase.addPropertyChangeListener(controller);
        controller.stage.show();
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
        for (var menuItem : drawButton.getItems()) {
            IntegerBinding sizeProperty = Bindings.size(tableView.getSelectionModel().getSelectedIndices());
            menuItem.disableProperty().bind(sizeProperty.lessThan(2));
        }
        buttons.add(drawButton);
        valueButton = new Button("Values");
        valueButton.setOnAction(this::makeValueTable);
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
        if (Objects.equals(evt.getPropertyName(), "project")) {
            List<DatasetBase> datasetList = ProjectBase.getActive().getDatasets();
            if (datasetList instanceof ObservableList) {
                setDatasetList((ObservableList<DatasetBase>) datasetList);
            }
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
                case "level" -> dataset.setLvl(newValue);
                case "scale" -> dataset.setScale(newValue);
                case "ref" -> dataset.setRefValue(getDimNum(), newValue);
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

        TableColumn<DatasetBase, Integer> nDimCol = new TableColumn<>("dim");
        nDimCol.setCellValueFactory(new PropertyValueFactory<>("nDim"));
        nDimCol.setPrefWidth(30);
        nDimCol.setEditable(false);

        TableColumn<DatasetBase, Integer> nFreqDimCol = new TableColumn<>("freq\ndim");
        nFreqDimCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getNFreqDims()));
        nFreqDimCol.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerColumnFormatter()));
        nFreqDimCol.setPrefWidth(30);
        nFreqDimCol.setOnEditCommit(this::nFreqDimsChanged);


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
                    cp.setOnAction((ActionEvent t) -> {
                        getTableView().edit(getTableRow().getIndex(), column);
                        commitEdit(cp.getValue());
                    });
                }
            }

            @Override
            public void commitEdit(Color item) {
                super.commitEdit(item);
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
                    cp.setOnAction((ActionEvent t) -> {
                        getTableView().edit(getTableRow().getIndex(), column);
                        commitEdit(cp.getValue());
                    });
                }
            }

            @Override
            public void commitEdit(Color item) {
                super.commitEdit(item);
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

        TableColumn<DatasetBase, Nuclei> nucleusCol = new TableColumn<>("nucleus");
        Nuclei[] nuclei = Arrays.stream(Nuclei.getNuclei()).sorted(Comparator.comparing(Nuclei::getNumberAsInt)).toArray(Nuclei[]::new);
        nucleusCol.setCellFactory(ComboBoxTableCell.forTableColumn(nuclei));
        nucleusCol.setCellValueFactory(p -> {
            DatasetBase dataset = p.getValue();
            int iDim = getDimNum();
            Nuclei nuc = null;
            if (dataset.getNDim() > iDim) {
                nuc = dataset.getNucleus(iDim);
            }
            return new SimpleObjectProperty<>(nuc);
        });
        nucleusCol.setOnEditCommit(
                (TableColumn.CellEditEvent<DatasetBase, Nuclei> t) -> {
                    int iDim = getDimNum();
                    Nuclei nuc = t.getNewValue();
                    t.getRowValue().setNucleus(iDim, nuc);
                });

        nucleusCol.setPrefWidth(75);

        var positiveColumn = new TableColumn("Positive");
        var negativeColumn = new TableColumn("Negative");
        dim1Column = new TableColumn("Dim1");

        Polygon polygon = new Polygon();
        polygon.getPoints().addAll(2.0, 2.0, 12.0, 2.0, 7.0, 10.0);
        polygon.setFill(Color.BLACK);
        Tooltip tip = new Tooltip("Right click for dimension choice");
        Tooltip.install(polygon, tip);

        dim1Column.setGraphic(polygon);
        dim1Column.setPrefWidth(400);
        positiveColumn.getColumns().setAll(posDrawOnCol, posColorCol);
        negativeColumn.getColumns().setAll(negDrawOnCol, negColorCol);
        dim1Column.getColumns().setAll(labelCol, sizeCol, sfCol, swCol, refCol, nucleusCol);
        ContextMenu menu = new ContextMenu();
        int maxDim = 6;
        for (int i = 0; i < maxDim; i++) {
            MenuItem dimItem = new MenuItem(String.valueOf(i + 1));
            final int iDim = i;
            dimItem.setOnAction(e -> setDimNum(iDim));
            menu.getItems().add(dimItem);
        }
        dim1Column.setContextMenu(menu);
        tableView.getColumns().setAll(fileNameCol, nDimCol, nFreqDimCol, levelCol, scaleCol, noiseCol, positiveColumn, negativeColumn, dim1Column);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setDatasetList((ObservableList<DatasetBase>) ProjectBase.getActive().getDatasets());
    }

    private void nFreqDimsChanged(TableColumn.CellEditEvent<DatasetBase, Integer> event) {
        int newFreqDim = event.getNewValue() != null ? event.getNewValue() : event.getOldValue();
        DatasetBase dataset = event.getRowValue();
        // freq dim must be between 0 and the max number of dimensions
        newFreqDim = newFreqDim < 0 ? 0 : Math.min(newFreqDim, dataset.getNDim());
        dataset.setNFreqDims(newFreqDim);
        tableView.refresh();
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
        if (tableView != null) {
            tableView.setItems(datasets);
        }
    }

    void drawDataset(ActionEvent e) {
        ObservableList<DatasetBase> datasets = tableView.getSelectionModel().getSelectedItems();
        FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
        PolyChart chart = controller.getActiveChart();
        if ((chart != null) && chart.getDataset() != null) {
            controller = AnalystApp.getFXMLControllerManager().newController();
            chart = controller.getActiveChart();
        }
        boolean appendFile = false;
        for (DatasetBase dataset : datasets) {
            controller.addDataset(chart, dataset, appendFile, false);
            appendFile = true;
        }
    }

    void gridDataset(GridPaneCanvas.ORIENTATION orient) {
        ObservableList<DatasetBase> datasets = tableView.getSelectionModel().getSelectedItems();
        FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
        PolyChart chart = controller.getActiveChart();
        if ((chart != null) && chart.getDataset() != null) {
            controller = AnalystApp.getFXMLControllerManager().newController();
        }
        controller.setNCharts(datasets.size());
        controller.arrange(orient);
        for (int i = 0; i < datasets.size(); i++) {
            DatasetBase dataset = datasets.get(i);
            PolyChart chartActive = controller.getCharts().get(i);
            controller.setActiveChart(chartActive);
            controller.addDataset(chartActive, dataset, false, false);
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

    private void saveValueTable() {
        List<ValueItem> items = valueTableView.getItems();
        if (valueDataset != null) {
            double[] values = items.stream().mapToDouble(ValueItem::getValue).toArray();
            int nDim = valueDataset.getNDim();
            int vDim = nDim - 1;
            for (int i = 0; i < nDim; i++) {
                double[] values2 = valueDataset.getValues(i);
                if ((values2 != null) && (values2.length > 1)) {
                    vDim = i;
                }
            }
            valueDataset.setValues(vDim, values);
        }
    }

    private void updateValueTable() {
        ObservableList<DatasetBase> datasets = tableView.getSelectionModel().getSelectedItems();
        ObservableList<ValueItem> valueList = FXCollections.observableArrayList();
        valueDataset = null;
        if (datasets.size() == 1) {
            valueDataset = datasets.get(0);
            int nDim = valueDataset.getNDim();

            for (int i = 0; i < nDim; i++) {
                double[] values = valueDataset.getValues(i);
                if ((values != null) && (values.length > 1)) {
                    for (int j = 0; j < values.length; j++) {
                        ValueItem item = new ValueItem(j, values[j]);
                        valueList.add(item);
                    }
                    break;
                }
            }
            if (valueList.isEmpty() && (valueDataset.getNFreqDims() < nDim)) {
                for (int i = 0; i < valueDataset.getSizeReal(nDim - 1); i++) {
                    valueList.add(new ValueItem(i, 0.0));
                }
            }
            if (!savedValues.containsKey(valueDataset.getName())) {
                double[] saveValues = valueList.stream().mapToDouble(v -> v.value).toArray();
                savedValues.put(valueDataset.getName(), saveValues);
            }
        }
        valueTableView.setItems(valueList);
        valueTableView.refresh();
    }

    private ObservableList<ValueItem> toItems(double[] values) {
        ObservableList<ValueItem> valueList = FXCollections.observableArrayList();
        if ((values != null) && (values.length > 1)) {
            for (int j = 0; j < values.length; j++) {
                ValueItem item = new ValueItem(j, values[j]);
                valueList.add(item);
            }
        }
        return valueList;
    }

    private void resetValues() {
        ObservableList<DatasetBase> datasets = tableView.getSelectionModel().getSelectedItems();
        valueDataset = null;
        if (datasets.size() == 1) {
            valueDataset = datasets.get(0);
            if (savedValues.containsKey(valueDataset.getName())) {
                valueTableView.setItems(toItems(savedValues.get(valueDataset.getName())));
                saveValueTable();
                valueTableView.refresh();
            }
        }
    }

    public void makeValueTable(ActionEvent event) {
        if (valueTableView == null) {
            Node node = (Node) event.getSource();
            Bounds buttonBounds = node.localToScreen(node.getBoundsInLocal());

            DoubleStringConverter dsConverter = new DoubleStringConverter();
            valueStage = new Stage(StageStyle.DECORATED);
            BorderPane borderPane = new BorderPane();
            borderPane.setPrefWidth(225);
            Scene scene = new Scene(borderPane);
            valueStage.setScene(scene);
            valueStage.setTitle("DatasetBase Values");
            valueStage.show();

            valueTableView = new TableView<>();
            valueTableView.setEditable(true);
            Button mathButton = new Button("Calculate");
            mathButton.setOnAction(e -> doMath());
            Button resetButton = new Button("Reset");
            resetButton.setOnAction(e -> resetValues());
            HBox hBox = new HBox();
            hBox.setPrefWidth(225);
            hBox.setSpacing(5);
            hBox.getChildren().addAll(mathButton, resetButton);
            borderPane.setTop(hBox);
            borderPane.setCenter(valueTableView);
            TableColumn<ValueItem, Integer> indexColumn = new TableColumn<>("Index");
            indexColumn.setEditable(false);
            TableColumn<ValueItem, Double> valueColumn = new TableColumn<>("Value");
            valueColumn.setEditable(true);

            indexColumn.setCellValueFactory(new PropertyValueFactory<>("index"));
            valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
            valueColumn.setCellFactory(tc -> new ValueItemDoubleFieldTableCell(dsConverter));

            valueTableView.getColumns().addAll(indexColumn, valueColumn);
            valueTableView.setPrefWidth(225);
            valueTableView.setPrefHeight(400);
            valueStage.setWidth(225);
            valueStage.setHeight(425);
            valueStage.setX(buttonBounds.getCenterX() - 50);
            valueStage.setY(buttonBounds.getMaxY() + 20);
        }
        valueStage.show();
        valueStage.toFront();
        updateValueTable();
    }

    void doMath() {
        ColumnMath columnMath = new ColumnMath();
        Dialog<DoubleUnaryOperator> dialog = columnMath.getDialog();
        dialog.showAndWait().ifPresent(this::doValues);
    }

    void savePars() {
        ObservableList<DatasetBase> datasets = tableView.getSelectionModel().getSelectedItems();
        for (DatasetBase dataset : datasets) {
            dataset.writeParFile();
        }

    }

    void doValues(DoubleUnaryOperator function) {
        for (var item : valueTableView.getItems()) {
            item.setValue(function.applyAsDouble(item.value));
        }
        saveValueTable();
        valueTableView.refresh();
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

    public void refresh() {
        tableView.refresh();
    }

    /**
     * Formatter to change between Integer and Strings in editable columns of Integers
     */
    private static class IntegerColumnFormatter extends javafx.util.converter.IntegerStringConverter {

        @Override
        public String toString(Integer object) {
            return String.format("%d", object);
        }

        @Override
        public Integer fromString(String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

}
