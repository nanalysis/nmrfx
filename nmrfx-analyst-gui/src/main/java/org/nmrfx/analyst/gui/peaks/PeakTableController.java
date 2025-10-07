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
package org.nmrfx.analyst.gui.peaks;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.plugin.PluginLoader;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.peaks.Multiplet;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.events.PeakEvent;
import org.nmrfx.peaks.events.PeakListener;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PeakMenuBar;
import org.nmrfx.processor.gui.PeakMenuTarget;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.utils.TableUtils;

import javax.tools.Tool;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.Format;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * @author johnsonb
 */
public class PeakTableController implements PeakMenuTarget, PeakListener, Initializable, StageBasedController {

    static final Background ERROR_BACKGROUND = new Background(new BackgroundFill(Color.RED, null, null));
    private Stage stage;

    @FXML
    private ToolBar toolBar;

    @FXML
    private TableView<Peak> tableView;

    private PeakList peakList;

    private int currentDims = 0;
    PeakMenuBar peakMenuBar;
    MenuButton peakListMenuButton;
    private final KeyCodeCombination copyKeyCodeCombination = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);


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

    public static PeakTableController create() {
        PeakTableController controller = Fxml.load(PeakListsTableController.class, "PeakTableScene.fxml")
                .withNewStage("Peaks")
                .getController();
        controller.stage.show();
        return controller;
    }

    void initToolBar() {
        peakListMenuButton = new MenuButton("List");
        toolBar.getItems().add(peakListMenuButton);
        updatePeakListMenu();
        peakMenuBar = new PeakMenuBar(this);
        peakMenuBar.initMenuBar(toolBar, false);
        GUIProject.getActive().addPeakListSubscription(this::updatePeakListMenu);
        PluginLoader.getInstance().registerPluginsOnEntryPoint(EntryPoint.PEAK_MENU, this);
    }

    public ToolBar getToolBar() {
        return toolBar;
    }

    public void updatePeakListMenu() {
        peakListMenuButton.getItems().clear();

        for (String peakListName : ProjectBase.getActive().getPeakListNames()) {
            MenuItem menuItem = new MenuItem(peakListName);
            menuItem.setOnAction(e -> setPeakList(PeakList.get(peakListName)));
            peakListMenuButton.getItems().add(menuItem);
        }
    }

    @Override
    public void copyPeakTableView() {
        TableUtils.copyTableToClipboard(tableView, true);
    }

    @Override
    public void deletePeaks() {
        deleteSelectedPeaks();
    }

    @Override
    public void restorePeaks() {
        List<Peak> selectedPeaks = tableView.getSelectionModel().getSelectedItems();
        for (Peak peak : selectedPeaks) {
            peak.setStatus(0);
        }
        tableView.getSelectionModel().clearSelection();
    }

    @Override
    public void refreshPeakView() {
        tableView.refresh();
    }

    @Override
    public void refreshChangedListView() {
        tableView.refresh();
    }

    @Override
    public Optional<Peak> getPeak() {
        Peak peak = tableView.getSelectionModel().getSelectedItem();
        return Optional.ofNullable(peak);
    }
    @Override
    public PeakList getPeakList() {
        return peakList;
    }

    @Override
    public void peakListChanged(PeakEvent peakEvent) {
        refreshPeakView();
    }

    private static class DimTableColumn<S, T> extends TableColumn<S, T> {

        int peakDim;

        DimTableColumn(String title, int iDim) {
            super(title + ":" + (iDim + 1));
            peakDim = iDim;
        }
    }

    static class ColumnFormatter<S, T> implements Callback<TableColumn<S, T>, TableCell<S, T>> {

        private Format format;

        public ColumnFormatter(Format format) {
            super();
            this.format = format;
        }

        @Override
        public TableCell<S, T> call(TableColumn<S, T> arg0) {
            return new TableCell<>() {
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

    private static class PeakStringFieldTableCell extends TextFieldTableCell<Peak, String> {

        PeakStringFieldTableCell(StringConverter<String> converter) {
            super(converter);
        }

        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(item);
        }

    }

    void initTable() {
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        updateColumns(0);
        tableView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                if (!tableView.getSelectionModel().getSelectedItems().isEmpty()) {
                    Peak peak = tableView.getSelectionModel().getSelectedItems().get(0);
                    showPeakInfo(peak);
                }
            }
        });
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableView.setOnKeyPressed(this::keyPressed);
        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            public void updateItem(Peak item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || !item.isDeleted()) {
                    setStyle("");
                } else {
                    setStyle("-fx-background-color: rgba(255, 0, 0, 0.4);");
                }
            }
        });

    }

    public void keyPressed(KeyEvent keyEvent) {
        KeyCode code = keyEvent.getCode();
        if (code == null) {
            return;
        }
        if (code == KeyCode.C) {
            // Paste command is shortcut + V, so make sure the KeyEvent matches that combination
            if (copyKeyCodeCombination.match(keyEvent)) {
                TableUtils.copyTableToClipboard(tableView, false);
            }
            keyEvent.consume();
        } else if (code == KeyCode.DELETE) {
            deleteSelectedPeaks();
        }
    }

    private void deleteSelectedPeaks() {
        List<Peak> selectedPeaks = new ArrayList<>(tableView.getSelectionModel().getSelectedItems());
        for (Peak peak : selectedPeaks) {
            peak.delete();
        }
        tableView.getSelectionModel().clearSelection();
    }

    void updateColumns(int nDim) {
        if (nDim == currentDims) {
            return;
        }
        tableView.getItems().clear();
        tableView.getColumns().clear();
        currentDims = nDim;

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
        posColorCol.setCellFactory((TableColumn<Peak, Color> column) -> new TableCell<>() {
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
            labelCol.setCellValueFactory((CellDataFeatures<Peak, String> p) -> {
                Peak peak = p.getValue();
                int iDim = labelCol.peakDim;
                String label = iDim < peak.getPeakDims().length ? peak.getPeakDim(iDim).getLabel() : "";
                return new ReadOnlyObjectWrapper(label);
            });

            labelCol.setCellFactory((TableColumn<Peak, String> column) -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(null);
                    Peak peak = getTableRow().getItem();
                    if (peak != null) {
                        int iDim = labelCol.peakDim;
                        if (iDim < peak.getPeakDims().length) {
                            PeakDim peakDim = peak.getPeakDim(iDim);
                            if (!peakDim.isLabelValid()) {
                                setBackground(ERROR_BACKGROUND);
                            } else {
                                setBackground(Background.EMPTY);
                            }
                            if (item != null) {
                                setText(item);
                            }
                        }
                    }
                }
            });

            labelCol.setPrefWidth(75);
            tableView.getColumns().add(labelCol);
        }
        for (int i = 0;
             i < nDim;
             i++) {
            DimTableColumn<Peak, Float> shiftCol = new DimTableColumn<>("shift", i);
            shiftCol.setCellValueFactory((CellDataFeatures<Peak, Float> p) -> {
                Peak peak = p.getValue();
                int iDim = shiftCol.peakDim;
                float ppm;
                if (iDim < peak.getPeakDims().length) {
                    ppm = peak.getPeakDim(iDim).getChemShiftValue();
                } else {
                    ppm = 0.0f;
                }
                return new ReadOnlyObjectWrapper(ppm);
            });
            shiftCol.setCellFactory(new ColumnFormatter<>(new DecimalFormat(".000")));

            shiftCol.setPrefWidth(75);
            tableView.getColumns().addAll(shiftCol);
        }
        if (nDim == 1) {
            StringConverter<String> sConverter = new DefaultStringConverter();
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

    @Override
    public void setPeakList(PeakList peakList) {
        if (this.peakList != null) {
            this.peakList.removePeakChangeListener(this);
        }
        this.peakList = peakList;
        if (tableView != null) {
            if (peakList == null) {
                tableView.getItems().clear();
                stage.setTitle("Peaks: ");
            } else {
                ObservableList<Peak> peaks = FXCollections.observableList(peakList.peaks());
                updateColumns(peakList.getNDim());
                tableView.setItems(peaks);
                stage.setTitle("Peaks: " + peakList.getName());
                peakList.registerPeakChangeListener(this);
            }
        }

    }

    void showPeakInfo(Peak peak) {
        FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
        controller.showPeakAttr();
        controller.getPeakAttrController().gotoPeak(peak);
        controller.getPeakAttrController().getStage().toFront();

    }
}
