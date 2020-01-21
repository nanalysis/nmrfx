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

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.text.DecimalFormat;
import java.text.Format;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import org.nmrfx.processor.datasets.peaks.Multiplet;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;

/**
 *
 * @author johnsonb
 */
public class PeakTableController implements PeakMenuTarget, Initializable {

    private Stage stage;
    @FXML
    private ToolBar toolBar;
    @FXML
    private TableView<Peak> tableView;
    private PeakList peakList;

    private int currentDims = 0;
    PeakMenuBar peakMenuBar;
    Button valueButton;
    Button saveParButton;
    Button closeButton;
    MenuButton peakListMenuButton;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initToolBar();
        initTable();

    }

    public Stage getStage() {
        return stage;
    }

    public static PeakTableController create() {
        FXMLLoader loader = new FXMLLoader(PeakTableController.class.getResource("/fxml/PeakTableScene.fxml"));
        PeakTableController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<PeakTableController>getController();
            controller.stage = stage;
            stage.setTitle("Peaks");
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }

    void initToolBar() {

        peakListMenuButton = new MenuButton("List");
        toolBar.getItems().add(peakListMenuButton);
        updatePeakListMenu();
        peakMenuBar = new PeakMenuBar(this);
        peakMenuBar.initMenuBar(toolBar);
        MapChangeListener<String, PeakList> mapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> {
            updatePeakListMenu();
        };

        PeakList.peakListTable.addListener(mapChangeListener);
    }

    public void updatePeakListMenu() {
        peakListMenuButton.getItems().clear();

        for (String peakListName : PeakList.peakListTable.keySet()) {
            MenuItem menuItem = new MenuItem(peakListName);
            menuItem.setOnAction(e -> {
                setPeakList(PeakList.get(peakListName));
            });
            peakListMenuButton.getItems().add(menuItem);
        }
    }

    @Override
    public void refreshPeakView() {
        tableView.refresh();
    }

    @Override
    public PeakList getPeakList() {
        return peakList;
    }

    private class DimTableColumn<S, T> extends TableColumn<S, T> {

        int peakDim;

        DimTableColumn(String title, int iDim) {
            super(title + ":" + (iDim + 1));
            peakDim = iDim;
        }
    }

    private class ColumnFormatter<S, T> implements Callback<TableColumn<S, T>, TableCell<S, T>> {

        private Format format;

        public ColumnFormatter(Format format) {
            super();
            this.format = format;
        }

        @Override
        public TableCell<S, T> call(TableColumn<S, T> arg0) {
            return new TableCell<S, T>() {
                @Override
                protected void updateItem(T item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item == null || empty) {
                        setGraphic(null);
                    } else {
                        setGraphic(new Label(format.format(item)));
                    }
                }
            };
        }
    }

    class PeakStringFieldTableCell extends TextFieldTableCell<Peak, String> {

        PeakStringFieldTableCell(StringConverter converter) {
            super(converter);
        }

    }

    void initTable() {
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        updateColumns(0);
        ListChangeListener listener = (ListChangeListener) (ListChangeListener.Change c) -> {
            int nSelected = tableView.getSelectionModel().getSelectedItems().size();
            boolean state = nSelected == 1;
        };
        tableView.getSelectionModel().getSelectedIndices().addListener(listener);
    }

    void updateColumns(int nDim) {
        if (nDim == currentDims) {
            return;
        }
        tableView.getItems().clear();
        tableView.getColumns().clear();
        currentDims = nDim;
        StringConverter sConverter = new DefaultStringConverter();

        TableColumn<Peak, Integer> idNumCol = new TableColumn<>("id");
        idNumCol.setCellValueFactory(new PropertyValueFactory("IdNum"));
        idNumCol.setEditable(false);
        idNumCol.setPrefWidth(50);

        TableColumn<Peak, Float> intensityCol = new TableColumn<>("intensity");
        intensityCol.setCellValueFactory(new PropertyValueFactory("Intensity"));
        intensityCol.setPrefWidth(75);

        TableColumn<Peak, Float> volumeCol = new TableColumn<>("volume");
        volumeCol.setCellValueFactory(new PropertyValueFactory("Volume1"));
        volumeCol.setPrefWidth(75);

        TableColumn<Peak, Color> posColorCol = new TableColumn<>("color");
        posColorCol.setPrefWidth(50);
        posColorCol.setCellValueFactory((CellDataFeatures<Peak, Color> p) -> new ReadOnlyObjectWrapper(p.getValue().getColor()));
        posColorCol.setCellFactory((TableColumn<Peak, Color> column) -> new TableCell<Peak, Color>() {
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
                Peak peak = (Peak) getTableRow().getItem();
                peak.setColor(item.toString());
            }
        });
        tableView.getColumns().addAll(idNumCol, intensityCol, volumeCol);

        for (int i = 0; i < nDim; i++) {
            DimTableColumn<Peak, String> labelCol = new DimTableColumn<>("label", i);
            labelCol.setCellFactory(tc -> new PeakStringFieldTableCell(sConverter));
            labelCol.setCellValueFactory((CellDataFeatures<Peak, String> p) -> {
                Peak peak = p.getValue();
                int iDim = labelCol.peakDim;
                String label = peak.getPeakDim(iDim).getLabel();
                return new ReadOnlyObjectWrapper(label);
            });

            labelCol.setPrefWidth(75);
            tableView.getColumns().add(labelCol);
        }
        for (int i = 0; i < nDim; i++) {
            DimTableColumn<Peak, Float> shiftCol = new DimTableColumn<>("shift", i);
            shiftCol.setCellValueFactory((CellDataFeatures<Peak, Float> p) -> {
                Peak peak = p.getValue();
                int iDim = shiftCol.peakDim;
                float ppm = peak.getPeakDim(iDim).getChemShiftValue();
                return new ReadOnlyObjectWrapper(ppm);
            });
            shiftCol.setCellFactory(new ColumnFormatter<>(new DecimalFormat(".000")));

            shiftCol.setPrefWidth(75);
            tableView.getColumns().addAll(shiftCol);
        }
        if (nDim == 1) {
            TableColumn<Peak, String> multipletCol = new TableColumn<>("multiplet");
            multipletCol.setCellFactory(tc -> new PeakStringFieldTableCell(sConverter));
            multipletCol.setCellValueFactory((CellDataFeatures<Peak, String> p) -> {
                Peak peak = p.getValue();
                Multiplet multiplet = peak.getPeakDim(0).getMultiplet();
                String couplingString = multiplet.getCouplingsAsSimpleString();
                double normVal = 0.0;
                if (peak.peakList.scale > 0.0) {
                    normVal = multiplet.getVolume() / peak.peakList.scale;
                }
                String label = String.format("%.2f", normVal) + " " + multiplet.getMultiplicity() + " " + couplingString;

                return new ReadOnlyObjectWrapper(label);
            });
            tableView.getColumns().add(multipletCol);
            tableView.setPrefWidth(150);

        }
    }

    public void setPeakList(PeakList peakList) {
        this.peakList = peakList;
        if (tableView == null) {
            System.out.println("null table");
        } else {
            if (peakList == null) {
                tableView.getItems().clear();
                stage.setTitle("Peaks: ");
            } else {
                ObservableList<Peak> peaks = FXCollections.observableList(peakList.peaks());
                updateColumns(peakList.getNDim());
                tableView.setItems(peaks);
                stage.setTitle("Peaks: " + peakList.getName());
            }
        }
    }

    void closePeak() {

    }
}
