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
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.controlsfx.control.MasterDetailPane;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.SpatialSetGroup;
import org.nmrfx.chemistry.constraints.MolecularConstraints;
import org.nmrfx.chemistry.constraints.Noe;
import org.nmrfx.chemistry.constraints.NoeSet;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.structure.noe.NOEAssign;
import org.nmrfx.structure.noe.NOECalibrator;
import org.nmrfx.utils.GUIUtils;
import org.nmrfx.utils.properties.ChoiceOperationItem;
import org.nmrfx.utils.properties.DoubleRangeOperationItem;
import org.nmrfx.utils.properties.NvFxPropertyEditorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.Format;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * @author johnsonb
 */
public class NOETableController implements Initializable, StageBasedController {

    private static final Logger log = LoggerFactory.getLogger(NOETableController.class);
    private Stage stage;
    @FXML
    private ToolBar toolBar;
    @FXML
    MasterDetailPane masterDetailPane;
    @FXML
    private TableView<Noe> tableView;
    private NoeSet noeSet;

    MenuButton noeSetMenuItem;
    MenuButton peakListMenuButton;
    ObservableMap<String, NoeSet> noeSetMap;
    MoleculeBase molecule = null;
    MolecularConstraints molConstr = null;
    PropertySheet propertySheet;
    CheckBox detailsCheckBox;
    CheckBox onlyFrozenCheckBox;
    DoubleRangeOperationItem refDistanceItem;
    DoubleRangeOperationItem expItem;
    DoubleRangeOperationItem minDisItem;
    DoubleRangeOperationItem maxDisItem;
    DoubleRangeOperationItem fErrorItem;
    ChoiceOperationItem modeItem;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initToolBar();
        tableView = new TableView<>();
        masterDetailPane.setMasterNode(tableView);
        propertySheet = new PropertySheet();
        masterDetailPane.setDetailSide(Side.RIGHT);
        masterDetailPane.setDetailNode(propertySheet);
        masterDetailPane.setShowDetailNode(true);
        masterDetailPane.setDividerPosition(0.7);
        propertySheet.setPrefWidth(400);
        propertySheet.setPropertyEditorFactory(new NvFxPropertyEditorFactory());
        propertySheet.setMode(PropertySheet.Mode.CATEGORY);
        propertySheet.setModeSwitcherVisible(false);
        propertySheet.setSearchBoxVisible(false);

        initTable();
        molecule = MoleculeFactory.getActive();
        if (molecule != null) {
            molConstr = molecule.getMolecularConstraints();
        }

        noeSetMap = FXCollections.observableMap(molConstr.noeSets);
        MapChangeListener<String, NoeSet> mapChangeListener = (MapChangeListener.Change<? extends String, ? extends NoeSet> change) -> updateNoeSetMenu();

        noeSetMap.addListener(mapChangeListener);
        MapChangeListener<String, PeakList> peakmapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> updatePeakListMenu();
        ProjectBase.getActive().addPeakListListener(peakmapChangeListener);

        updateNoeSetMenu();
        updatePeakListMenu();
        masterDetailPane.showDetailNodeProperty().bindBidirectional(detailsCheckBox.selectedProperty());
        List<String> intVolChoice = List.of("Intensity", "Volume");
        modeItem = new ChoiceOperationItem(propertySheet, (a, b, c) -> refresh(), "intensity", intVolChoice, "Exp Calibrate", "Mode", "Reference Distance");
        refDistanceItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                3.0, 1.0, 6.0, false, "Exp Calibrate", "Ref Distance", "Reference Distance");
        expItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                6.0, 1.0, 6.0, false, "Exp Calibrate", "Exp Factor", "Exponent value");
        minDisItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                2.0, 1.0, 3.0, false, "Exp Calibrate", "Min Distance", "Minimum bound");
        maxDisItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                6.0, 3.0, 6.0, false, "Exp Calibrate", "Max Distance", "Maximum bound");
        fErrorItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                0.125, 0.0, 0.2, false, "Exp Calibrate", "Tolerance", "Fractional additional bound");
        propertySheet.getItems().addAll(modeItem, refDistanceItem, expItem, minDisItem, maxDisItem, fErrorItem);
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    private void refresh() {
        tableView.refresh();
    }

    public static NOETableController create() {
        if (MoleculeFactory.getActive() == null) {
            GUIUtils.warn("NOE Table", "No active molecule");
            return null;
        }

        NOETableController controller = Fxml.load(NOETableController.class, "NoeTableScene.fxml")
                .withNewStage("Peaks")
                .getController();
        controller.stage.show();
        return controller;
    }

    void initToolBar() {
        Button exportButton = new Button("Export");
        exportButton.setOnAction(e -> exportNMRFxFile());
        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> clearNOESet());
        Button calibrateButton = new Button("Calibrate");
        calibrateButton.setOnAction(e -> calibrate());
        noeSetMenuItem = new MenuButton("NoeSets");
        peakListMenuButton = new MenuButton("PeakLists");
        detailsCheckBox = new CheckBox("Details");
        onlyFrozenCheckBox = new CheckBox("Only Frozen");
        toolBar.getItems().addAll(exportButton, clearButton, noeSetMenuItem, peakListMenuButton, calibrateButton, onlyFrozenCheckBox, detailsCheckBox);
        updateNoeSetMenu();
    }

    public void updateNoeSetMenu() {
        noeSetMenuItem.getItems().clear();
        MoleculeBase mol = MoleculeFactory.getActive();

        if (mol != null) {
            MolecularConstraints molecularConstraints = mol.getMolecularConstraints();
            for (String noeSetName : molecularConstraints.getNOESetNames()) {
                MenuItem menuItem = new MenuItem(noeSetName);
                menuItem.setOnAction(e -> setNoeSet(molecularConstraints.noeSets.get(noeSetName)));
                noeSetMenuItem.getItems().add(menuItem);
            }
        }
    }

    public void updatePeakListMenu() {
        peakListMenuButton.getItems().clear();
        for (String peakListName : ProjectBase.getActive().getPeakListNames()) {
            MenuItem menuItem = new MenuItem(peakListName);
            menuItem.setOnAction(e -> extractPeakList(PeakList.get(peakListName)));
            peakListMenuButton.getItems().add(menuItem);
        }
    }

    private void exportNMRFxFile() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                noeSet.writeNMRFxFile(file);
            } catch (IOException ioE) {
                ExceptionDialog exceptionDialog = new ExceptionDialog(ioE);
                exceptionDialog.show();
            }
        }
    }

    private record ColumnFormatter<S, T>(Format format) implements Callback<TableColumn<S, T>, TableCell<S, T>> {

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

    void initTable() {
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        updateColumns();
        tableView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                if (!tableView.getSelectionModel().getSelectedItems().isEmpty()) {
                    Noe noe = tableView.getSelectionModel().getSelectedItems().get(0);
                    showPeakInfo(noe);
                }
            }
        });
    }

    void updateColumns() {
        tableView.getColumns().clear();

        TableColumn<Noe, Integer> idNumCol = new TableColumn<>("id");
        idNumCol.setCellValueFactory(new PropertyValueFactory("ID"));
        idNumCol.setEditable(false);
        idNumCol.setPrefWidth(50);

        TableColumn<Noe, String> peakListCol = new TableColumn<>("PeakList");
        peakListCol.setCellValueFactory(new PropertyValueFactory("PeakListName"));

        TableColumn<Noe, Integer> peakNumCol = new TableColumn<>("PeakNum");
        peakNumCol.setCellValueFactory(new PropertyValueFactory("PeakNum"));
        tableView.getColumns().addAll(idNumCol, peakListCol, peakNumCol);

        for (int i = 0; i < 2; i++) {
            final int iGroup = i;
            TableColumn<Noe, Integer> entityCol = new TableColumn<>("entity" + (iGroup + 1));
            entityCol.setCellValueFactory((CellDataFeatures<Noe, Integer> p) -> {
                Noe noe = p.getValue();
                SpatialSetGroup spg = iGroup == 0 ? noe.spg1 : noe.spg2;
                Integer res = spg.getSpatialSet().atom.getTopEntity().getIDNum();
                return new ReadOnlyObjectWrapper(res);
            });
            TableColumn<Noe, Integer> resCol = new TableColumn<>("res" + (iGroup + 1));
            resCol.setCellValueFactory((CellDataFeatures<Noe, Integer> p) -> {
                Noe noe = p.getValue();
                SpatialSetGroup spg = iGroup == 0 ? noe.spg1 : noe.spg2;
                Integer res = spg.getSpatialSet().atom.getResidueNumber();
                return new ReadOnlyObjectWrapper(res);
            });
            TableColumn<Noe, String> atomCol = new TableColumn<>("aname" + (iGroup + 1));
            atomCol.setCellValueFactory((CellDataFeatures<Noe, String> p) -> {
                Noe noe = p.getValue();
                SpatialSetGroup spg = iGroup == 0 ? noe.spg1 : noe.spg2;
                String aname = spg.getSpatialSet().atom.getName();
                return new ReadOnlyObjectWrapper(aname);
            });
            tableView.getColumns().addAll(entityCol, resCol, atomCol);
        }

        TableColumn<Noe, Float> lowerCol = new TableColumn<>("Lower");
        lowerCol.setCellValueFactory(new PropertyValueFactory("Lower"));
        lowerCol.setCellFactory(new ColumnFormatter<>(new DecimalFormat(".00")));
        lowerCol.setPrefWidth(75);

        TableColumn<Noe, Float> upperCol = new TableColumn<>("Upper");
        upperCol.setCellValueFactory(new PropertyValueFactory("Upper"));
        upperCol.setCellFactory(new ColumnFormatter<>(new DecimalFormat(".00")));
        upperCol.setPrefWidth(75);
        TableColumn<Noe, Float> ppmCol = new TableColumn<>("PPM");
        ppmCol.setCellValueFactory(new PropertyValueFactory("PpmError"));
        ppmCol.setCellFactory(new ColumnFormatter<>(new DecimalFormat(".00")));
        ppmCol.setPrefWidth(75);

        TableColumn<Noe, Float> contribCol = new TableColumn<>("Contrib");
        contribCol.setCellValueFactory(new PropertyValueFactory("Contribution"));
        contribCol.setCellFactory(new ColumnFormatter<>(new DecimalFormat(".00")));
        contribCol.setPrefWidth(75);

        TableColumn<Noe, Float> networkCol = new TableColumn<>("Network");
        networkCol.setCellValueFactory(new PropertyValueFactory("NetworkValue"));
        networkCol.setCellFactory(new ColumnFormatter<>(new DecimalFormat(".00")));
        networkCol.setPrefWidth(75);

        TableColumn<Noe, String> flagCol = new TableColumn<>("Flags");
        flagCol.setCellValueFactory(new PropertyValueFactory("ActivityFlags"));
        flagCol.setPrefWidth(75);

        tableView.getColumns().addAll(lowerCol, upperCol, ppmCol, contribCol, networkCol, flagCol);

    }

    public void setNoeSet(NoeSet noeSet) {
        log.info("set noes {}", noeSet != null ? noeSet.getName() : "empty");
        this.noeSet = noeSet;
        if (tableView == null) {
            log.warn("null table");
        } else {
            if (noeSet == null) {
                stage.setTitle("Noes: ");
            } else {

                ObservableList<Noe> noes = FXCollections.observableList(noeSet.getConstraints());
                log.info("noes {}", noes.size());
                updateColumns();
                tableView.setItems(noes);
                tableView.refresh();
                stage.setTitle("Noes: " + noeSet.getName());
            }
        }

    }

    void calibrate() {
        if (noeSet == null) {
            Optional<NoeSet> noeSetOpt = molConstr.activeNOESet();
            if (noeSetOpt.isEmpty()) {
                noeSet = molConstr.newNOESet("default");
            } else {
                noeSet = noeSetOpt.get();
            }
        }
        if (noeSet != null) {
            log.info("Calibrate {} {}", noeSet.getName(), noeSet);
            String intVolChoice = modeItem.getValue().toLowerCase();
            double referenceDistance = refDistanceItem.doubleValue();
            double expValue = expItem.doubleValue();
            double minDistance = minDisItem.doubleValue();
            double maxDistance = maxDisItem.doubleValue();
            double fError = fErrorItem.doubleValue();
            noeSet.setCalibratable(true);
            NOECalibrator noeCalibrator = new NOECalibrator(noeSet);
            noeCalibrator.setScale(intVolChoice, referenceDistance, expValue, minDistance, maxDistance, fError);
            noeCalibrator.calibrateExp(null);

            setNoeSet(noeSet);
            refresh();
        }
    }

    void extractPeakList(PeakList peakList) {
        if (NOEAssign.getProtonDims(peakList).isEmpty()) {
            GUIUtils.warn("Extract Peaks", "Peak list " + peakList.getName() + " doesn't have two proton dimensions");
            return;
        }
        boolean onlyFrozen = onlyFrozenCheckBox.isSelected();
        int nDim = peakList.nDim;
        try {
            for (int i = 0; i < nDim; i++) {
                NOEAssign.findMax(peakList, i, 0, onlyFrozen);
            }
            Optional<NoeSet> noeSetOpt = molConstr.activeNOESet();
            if (noeSetOpt.isEmpty()) {
                noeSet = molConstr.newNOESet("default");
                noeSetOpt = Optional.of(noeSet);
            }
            NOEAssign.extractNoePeaks2(noeSetOpt, peakList, 2, false, 0, onlyFrozen);
            NOECalibrator noeCalibrator = new NOECalibrator(noeSetOpt.get());
            noeCalibrator.updateContributions(false, false);
            noeSet = noeSetOpt.get();
            log.info("active {}", noeSet.getName());

            setNoeSet(noeSet);
            updateNoeSetMenu();
        } catch (InvalidMoleculeException | IllegalArgumentException ex) {
            ExceptionDialog exD = new ExceptionDialog(ex);
            exD.show();
        }
    }

    void clearNOESet() {
        if (GUIUtils.affirm("Clear active set")) {
            Optional<NoeSet> noeSetOpt = molConstr.activeNOESet();
            noeSetOpt.ifPresent(NoeSet::clear);
            refresh();
        }
    }

    void showPeakInfo(Noe noe) {
        Peak peak = noe.peak;
        if (peak != null) {
            FXMLController.showPeakAttr();
            FXMLController.getPeakAttrController().gotoPeak(peak);
            FXMLController.getPeakAttrController().getStage().toFront();
        }
    }
}
