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

import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.controls.FractionPane;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.converter.DoubleStringConverter;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.nmrfx.processor.gui.controls.FractionCanvas;

/**
 *
 * @author johnsonb
 */
public class DatasetsController implements Initializable {

    static final DecimalFormat formatter = new DecimalFormat();

    private Stage stage;
    @FXML
    private ToolBar toolBar;
    @FXML
    private TableView<Dataset> tableView;

    private int dimNumber = 0;
    private int maxDim = 6;
    TableColumn dim1Column;
    Button valueButton;
    Button saveParButton;
    Button closeButton;
    Stage valueStage = null;
    TableView<ValueItem> valueTableView = null;
    Dataset valueDataset = null;

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
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<DatasetsController>getController();
            controller.stage = stage;
            stage.setTitle("Datasets");
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }

    void initToolBar() {
        ArrayList<ButtonBase> buttons = new ArrayList<>();
        Button bButton;

        saveParButton = new Button("Save Par");
        buttons.add(saveParButton);
        saveParButton.setOnAction(e -> savePars());
        saveParButton.setDisable(true);

        closeButton = new Button("Close");
        buttons.add(closeButton);
        closeButton.setOnAction(e -> closeDatasets());
        closeButton.setDisable(true);

        MenuButton drawButton = new MenuButton("Draw");
        MenuItem overlayItem = new MenuItem("Overlay");
        overlayItem.setOnAction(e -> drawDataset(e));
        MenuItem gridItem = new MenuItem("Grid");
        gridItem.setOnAction(e -> gridDataset(e, FractionCanvas.ORIENTATION.GRID));
        MenuItem horizontalItem = new MenuItem("Horizontal");
        horizontalItem.setOnAction(e -> gridDataset(e, FractionCanvas.ORIENTATION.HORIZONTAL));
        MenuItem verticalItem = new MenuItem("Vertical");
        verticalItem.setOnAction(e -> gridDataset(e, FractionCanvas.ORIENTATION.VERTICAL));
        ContextMenu drawMenu = new ContextMenu(overlayItem, horizontalItem, verticalItem, gridItem);
        drawButton.setContextMenu(drawMenu);

        buttons.add(drawButton);
        valueButton = new Button("Values");
        valueButton.setOnAction(e -> makeValueTable());
        buttons.add(valueButton);
        valueButton.setDisable(true);

        for (ButtonBase button : buttons) {
            button.getStyleClass().add("toolButton");
        }
        toolBar.getItems().addAll(buttons);
    }

    class DatasetDoubleFieldTableCell extends TextFieldTableCell<Dataset, Double> {

        DatasetDoubleFieldTableCell(StringConverter<Double> converter) {
            super(converter);
        }

        @Override
        public void commitEdit(Double newValue) {
            String column = getTableColumn().getText();
            Dataset dataset = (Dataset) getTableRow().getItem();
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

    class DatasetStringFieldTableCell extends TextFieldTableCell<Dataset, String> {

        DatasetStringFieldTableCell(StringConverter converter) {
            super(converter);
        }

        @Override
        public void commitEdit(String newValue) {
            String column = getTableColumn().getText();
            Dataset dataset = (Dataset) getTableRow().getItem();
            super.commitEdit(newValue);
            switch (column) {
                case "label":
                    dataset.setLabel(getDimNum(), newValue);
                    break;
            }
        }

    }

    void initTable() {
        DoubleStringConverter dsConverter = new DoubleStringConverter();
        StringConverter sConverter = new DefaultStringConverter();
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<Dataset, String> fileNameCol = new TableColumn<>("dataset");
        fileNameCol.setCellValueFactory(new PropertyValueFactory("fileName"));
        fileNameCol.setEditable(false);

        TableColumn<Dataset, Double> levelCol = new TableColumn<>("level");
        levelCol.setCellValueFactory(new PropertyValueFactory("lvl"));
        levelCol.setCellFactory(tc -> new DatasetDoubleFieldTableCell(dsConverter));

        TableColumn<Dataset, Double> scaleCol = new TableColumn<>("scale");
        scaleCol.setCellValueFactory(new PropertyValueFactory("scale"));
        scaleCol.setCellFactory(tc -> new DatasetDoubleFieldTableCell(dsConverter));

        TableColumn<Dataset, Integer> nDimCol = new TableColumn<>("nD");
        nDimCol.setCellValueFactory(new PropertyValueFactory("nDim"));
        nDimCol.setPrefWidth(25);
        nDimCol.setEditable(false);

        TableColumn<Dataset, Boolean> posDrawOnCol = new TableColumn<>("on");
        posDrawOnCol.setCellValueFactory(new PropertyValueFactory("posDrawOn"));
        posDrawOnCol.setCellFactory(tc -> new CheckBoxTableCell<>());
        posDrawOnCol.setPrefWidth(25);
        posDrawOnCol.setMaxWidth(25);
        posDrawOnCol.setResizable(false);

        TableColumn<Dataset, Color> posColorCol = new TableColumn<>("color");
        posColorCol.setPrefWidth(50);
        posColorCol.setCellValueFactory((CellDataFeatures<Dataset, Color> p) -> new ReadOnlyObjectWrapper(Color.web(p.getValue().getPosColor())));
        posColorCol.setCellFactory((TableColumn<Dataset, Color> column) -> new TableCell<Dataset, Color>() {
            @Override
            protected void updateItem(Color item, boolean empty) {
                super.updateItem(item, empty);
                if (item != null) {
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
                Dataset dataset = (Dataset) getTableRow().getItem();
                dataset.setPosColor(item.toString());
            }
        });

        TableColumn<Dataset, Boolean> negDrawOnCol = new TableColumn<>("on");
        negDrawOnCol.setCellValueFactory(new PropertyValueFactory("negDrawOn"));
        negDrawOnCol.setCellFactory(tc -> new CheckBoxTableCell<>());
        negDrawOnCol.setPrefWidth(25);
        negDrawOnCol.setMaxWidth(25);
        negDrawOnCol.setResizable(false);

        TableColumn<Dataset, Color> negColorCol = new TableColumn<>("color");
        negColorCol.setPrefWidth(50);
        negColorCol.setCellValueFactory((CellDataFeatures<Dataset, Color> p) -> new ReadOnlyObjectWrapper(Color.web(p.getValue().getNegColor())));
        negColorCol.setCellFactory((TableColumn<Dataset, Color> column) -> new TableCell<Dataset, Color>() {
            @Override
            protected void updateItem(Color item, boolean empty) {
                super.updateItem(item, empty);
                if (item != null) {
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
                Dataset dataset = (Dataset) getTableRow().getItem();
                dataset.setNegColor(item.toString());
            }
        });

        TableColumn<Dataset, Integer> sizeCol = new TableColumn<>("size");
        sizeCol.setCellValueFactory((CellDataFeatures<Dataset, Integer> p) -> {
            Dataset dataset = p.getValue();
            int size = 0;
            int iDim = getDimNum();
            if (dataset.getNDim() > iDim) {
                size = dataset.getSize(iDim);
            }
            return new ReadOnlyObjectWrapper(size);
        });
        sizeCol.setPrefWidth(50);

        TableColumn<Dataset, String> labelCol = new TableColumn<>("label");
        labelCol.setCellFactory(tc -> new DatasetStringFieldTableCell(sConverter));
        labelCol.setCellValueFactory((CellDataFeatures<Dataset, String> p) -> {
            Dataset dataset = p.getValue();
            String label = "";
            int iDim = getDimNum();
            if (dataset.getNDim() > iDim) {
                label = dataset.getLabel(iDim);
            }
            return new ReadOnlyObjectWrapper(label);
        });

        labelCol.setPrefWidth(50);

        TableColumn<Dataset, Double> sfCol = new TableColumn<>("sf");
        sfCol.setCellValueFactory((CellDataFeatures<Dataset, Double> p) -> {
            Dataset dataset = p.getValue();
            double sf = 0;
            int iDim = getDimNum();
            if (dataset.getNDim() > iDim) {
                sf = dataset.getSf(iDim);
            }
            return new ReadOnlyObjectWrapper(sf);
        });

        sfCol.setPrefWidth(150);

        TableColumn<Dataset, Double> swCol = new TableColumn<>("sw");
        swCol.setCellValueFactory((CellDataFeatures<Dataset, Double> p) -> {
            Dataset dataset = p.getValue();
            double sw = 0;
            int iDim = getDimNum();
            if (dataset.getNDim() > iDim) {
                sw = dataset.getSw(iDim);
            }
            return new ReadOnlyObjectWrapper(sw);
        });

        swCol.setPrefWidth(75);

        TableColumn<Dataset, Double> refCol = new TableColumn<>("ref");
        refCol.setCellFactory(tc -> new DatasetDoubleFieldTableCell(dsConverter));
        refCol.setCellValueFactory((CellDataFeatures<Dataset, Double> p) -> {
            Dataset dataset = p.getValue();
            double ref = 0;
            int iDim = getDimNum();
            if (dataset.getNDim() > iDim) {
                ref = dataset.getRefValue(iDim);
            }
            return new ReadOnlyObjectWrapper(ref);
        });

        refCol.setPrefWidth(75);

        TableColumn positiveColumn = new TableColumn("Positive");
        TableColumn negativeColumn = new TableColumn("Negative");
        dim1Column = new TableColumn("Dim1");
        dim1Column.setPrefWidth(400);
        positiveColumn.getColumns().setAll(posDrawOnCol, posColorCol);
        negativeColumn.getColumns().setAll(negDrawOnCol, negColorCol);
        dim1Column.getColumns().setAll(labelCol, sizeCol, sfCol, swCol, refCol);
        ContextMenu menu = new ContextMenu();
        for (int i = 0; i < maxDim; i++) {
            MenuItem dimItem = new MenuItem(String.valueOf(i + 1));
            final int iDim = i;
            dimItem.setOnAction(e -> setDimNum(iDim));
            menu.getItems().add(dimItem);
        }
        dim1Column.setContextMenu(menu);
        tableView.getColumns().setAll(fileNameCol, nDimCol, levelCol, scaleCol, positiveColumn, negativeColumn, dim1Column);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ListChangeListener listener = new ListChangeListener() {
            @Override
            public void onChanged(ListChangeListener.Change c) {
                int nSelected = tableView.getSelectionModel().getSelectedItems().size();
                boolean state = nSelected == 1;
                valueButton.setDisable(!state);
                saveParButton.setDisable(nSelected == 0);
                closeButton.setDisable(nSelected == 0);
            }
        };
        tableView.getSelectionModel().getSelectedIndices().addListener(listener);
    }

    private int getDimNum() {
        return dimNumber;
    }

    private void setDimNum(int i) {
        dimNumber = i;
        dim1Column.setText("Dim " + (dimNumber + 1));
        tableView.refresh();
    }

    public void setDatasetList(ObservableList<Dataset> datasets) {
        if (tableView == null) {
            System.out.println("null table");
        } else {
            tableView.setItems(datasets);
        }
    }

    void drawDataset(ActionEvent e) {
        ObservableList<Dataset> datasets = tableView.getSelectionModel().getSelectedItems();
        FXMLController controller = FXMLController.getActiveController();
        PolyChart chart = controller.getActiveChart();
        if ((chart != null) && chart.getDataset() != null) {
            controller = FXMLController.create();
        }
        boolean appendFile = false;
        for (Dataset dataset : datasets) {
            controller.addDataset(dataset, appendFile, false);
            appendFile = true;
        }
    }

    void gridDataset(ActionEvent e, FractionCanvas.ORIENTATION orient) {
        ObservableList<Dataset> datasets = tableView.getSelectionModel().getSelectedItems();
        FXMLController controller = FXMLController.getActiveController();
        PolyChart chart = controller.getActiveChart();
        if ((chart != null) && chart.getDataset() != null) {
            controller = FXMLController.create();
        }
        for (int i = 0; i < (datasets.size() - 1); i++) {
            controller.addChart(1);
        }
        controller.arrange(orient);
        for (int i = 0; i < datasets.size(); i++) {
            Dataset dataset = datasets.get(i);
            PolyChart chartActive = controller.charts.get(i);
            controller.setActiveChart(chartActive);
            controller.addDataset(dataset, false, false);
        }
    }

    public class ValueItem {

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
            ValueItem value = (ValueItem) getTableRow().getItem();
            super.commitEdit(newValue);
            switch (column) {
                case "Value":
                    value.setValue(newValue);
//                    System.out.println("value changed to " + newValue);
                    saveValueTable();
                    break;
            }
        }

    }

    void saveValueTable() {
        List<ValueItem> items = valueTableView.getItems();
        if (valueDataset != null) {
            double[] values = items.stream().mapToDouble(v -> v.getValue()).toArray();
            int nDim = valueDataset.getNDim();
            valueDataset.setValues(nDim - 1, values);
        }
    }

    public void updateValueTable(TableView valueTable) {
        ObservableList<Dataset> datasets = tableView.getSelectionModel().getSelectedItems();
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
                    for (int i = 0; i < valueDataset.getSize(nDim - 1); i++) {
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
            valueStage.setTitle("Dataset Values");
            valueStage.show();

            valueTableView = new TableView<>();
            valueTableView.setEditable(true);
            borderPane.setCenter(valueTableView);
            TableColumn<ValueItem, Integer> indexColumn = new TableColumn<>("Index");
            indexColumn.setEditable(false);
            TableColumn<ValueItem, Double> valueColumn = new TableColumn<>("Value");
            valueColumn.setEditable(true);

            indexColumn.setCellValueFactory(new PropertyValueFactory("index"));
            valueColumn.setCellValueFactory(new PropertyValueFactory("value"));
            valueColumn.setCellFactory(tc -> new ValueItemDoubleFieldTableCell(dsConverter));
            //valueColumn.setCellFactory(tc -> new DatasetDoubleFieldTableCell(dsConverter));

            valueTableView.getColumns().addAll(indexColumn, valueColumn);
        }
        valueStage.show();
        valueStage.toFront();
        updateValueTable(valueTableView);
    }

    void savePars() {
        ObservableList<Dataset> datasets = tableView.getSelectionModel().getSelectedItems();
        for (Dataset dataset : datasets) {
            dataset.writeParFile();
        }

    }

    void closeDatasets() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Close selected datasets");
        Optional<ButtonType> response = alert.showAndWait();
        if (response.isPresent() && response.get().getText().equals("OK")) {
            ObservableList<Dataset> datasets = tableView.getSelectionModel().getSelectedItems();
            for (Dataset dataset : datasets) {
                dataset.close();
            }
        }
    }
}
