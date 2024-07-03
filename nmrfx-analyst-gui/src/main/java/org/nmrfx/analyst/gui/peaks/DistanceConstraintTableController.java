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
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.constraints.*;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.processor.gui.utils.ToolBarUtils;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.noe.NOECalibrator;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.Format;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * @author johnsonb
 */
public class DistanceConstraintTableController implements Initializable, StageBasedController {

    private static final Logger log = LoggerFactory.getLogger(DistanceConstraintTableController.class);
    private Stage stage;
    @FXML
    private ToolBar toolBar;
    @FXML
    private TableView<DistanceConstraint> tableView;

    MenuButton distanceSetMenuButton;
    ObservableMap<String, DistanceConstraintSet> distanceConstraintSetMap;

    DistanceConstraintSet distanceConstraintSet;
    MoleculeBase molecule = null;
    MolecularConstraints molConstr = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initToolBar();
        initTable();
        molecule = MoleculeFactory.getActive();
        if (molecule != null) {
            molConstr = molecule.getMolecularConstraints();
        }

        distanceConstraintSetMap = FXCollections.observableMap(molConstr.distanceSets);
        MapChangeListener<String, DistanceConstraintSet> mapChangeListener = (MapChangeListener.Change<? extends String, ? extends DistanceConstraintSet> change) -> updateDistanceSetMenu();

        distanceConstraintSetMap.addListener(mapChangeListener);

        updateDistanceSetMenu();
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

    public static DistanceConstraintTableController create() {
        if (MoleculeFactory.getActive() == null) {
            GUIUtils.warn("Distance Constraint Table", "No active molecule");
            return null;
        }

        DistanceConstraintTableController controller = Fxml.load(DistanceConstraintTableController.class, "DistanceConstraintTableScene.fxml")
                .withNewStage("Distance Constraints")
                .getController();
        controller.stage.show();
        return controller;
    }

    void initToolBar() {
        Button exportButton = new Button("Export");
        exportButton.setOnAction(e -> exportNMRFxFile());
        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> clearDistanceSet());

        distanceSetMenuButton = new MenuButton("Distance Sets");
        distanceSetMenuButton.setOnContextMenuRequested(e -> updateDistanceSetMenu());

        toolBar.getItems().addAll(exportButton, clearButton, distanceSetMenuButton,
                ToolBarUtils.makeFiller(20));
        updateDistanceSetMenu();
    }

    public void updateDistanceSetMenu() {
        distanceSetMenuButton.getItems().clear();
        MoleculeBase mol = MoleculeFactory.getActive();

        if (mol != null) {
            MolecularConstraints molecularConstraints = mol.getMolecularConstraints();
            for (String setName : molecularConstraints.getDistanceSetNames()) {
                MenuItem menuItem = new MenuItem(setName);
                menuItem.setOnAction(e -> setDistanceConstraintSet(molecularConstraints.distanceSets.get(setName)));
                distanceSetMenuButton.getItems().add(menuItem);
            }
        }
    }

    private void exportNMRFxFile() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                distanceConstraintSet.writeNMRFxFile(file);
            } catch (IOException ioE) {
                ExceptionDialog exceptionDialog = new ExceptionDialog(ioE);
                exceptionDialog.show();
            }
        }
    }

    private record ColumnFormatter<S, T>(Format format) implements Callback<TableColumn<S, T>, TableCell<S, T>> {

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

    void initTable() {
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        updateColumns();
        tableView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                if (!tableView.getSelectionModel().getSelectedItems().isEmpty()) {
                    DistanceConstraint distanceConstraint = tableView.getSelectionModel().getSelectedItems().get(0);
                }
            }
        });
    }

    void updateColumns() {
        tableView.getColumns().clear();

        TableColumn<DistanceConstraint, Integer> idNumCol = new TableColumn<>("ID");
        idNumCol.setCellValueFactory(new PropertyValueFactory<>("ID"));
        idNumCol.setEditable(false);
        idNumCol.setPrefWidth(50);

        tableView.getColumns().addAll(idNumCol);

        for (int i = 0; i < 2; i++) {
            final int iGroup = i;
            TableColumn<DistanceConstraint, Integer> entityCol = new TableColumn<>("entity" + (iGroup + 1));
            entityCol.setCellValueFactory((CellDataFeatures<DistanceConstraint, Integer> p) -> {
                DistanceConstraint distanceConstraint = p.getValue();
                AtomDistancePair atomPair = distanceConstraint.getAtomPairs()[0];
                Atom atom = iGroup == 0 ? atomPair.getAtoms1()[0] : atomPair.getAtoms2()[0];
                Integer res = atom.getTopEntity().getIDNum();
                return new ReadOnlyObjectWrapper<>(res);
            });
            TableColumn<DistanceConstraint, Integer> resCol = new TableColumn<>("res" + (iGroup + 1));
            resCol.setCellValueFactory((CellDataFeatures<DistanceConstraint, Integer> p) -> {
                DistanceConstraint distanceConstraint = p.getValue();
                AtomDistancePair atomPair = distanceConstraint.getAtomPairs()[0];
                Atom atom = iGroup == 0 ? atomPair.getAtoms1()[0] : atomPair.getAtoms2()[0];
                Integer res = atom.getResidueNumber();
                return new ReadOnlyObjectWrapper<>(res);
            });
            TableColumn<DistanceConstraint, String> atomCol = new TableColumn<>("aname" + (iGroup + 1));
            atomCol.setCellValueFactory((CellDataFeatures<DistanceConstraint, String> p) -> {
                DistanceConstraint distanceConstraint = p.getValue();
                AtomDistancePair atomPair = distanceConstraint.getAtomPairs()[0];
                Atom atom = iGroup == 0 ? atomPair.getAtoms1()[0] : atomPair.getAtoms2()[0];
                String aname = atom.getName();
                return new ReadOnlyObjectWrapper<>(aname);
            });
            tableView.getColumns().addAll(entityCol, resCol, atomCol);
        }

        addConstraintColumns(tableView);

    }

    public static void addConstraintColumns(TableView tableView) {
        TableColumn<? extends DistanceConstraint, Float> lowerCol = new TableColumn<>("Lower");
        lowerCol.setCellValueFactory(new PropertyValueFactory<>("Lower"));
        lowerCol.setCellFactory(new ColumnFormatter<>(new DecimalFormat(".00")));
        lowerCol.setPrefWidth(75);

        TableColumn<DistanceConstraint, Float> upperCol = new TableColumn<>("Upper");
        upperCol.setCellValueFactory(new PropertyValueFactory<>("Upper"));
        upperCol.setCellFactory(new ColumnFormatter<>(new DecimalFormat(".00")));
        upperCol.setPrefWidth(75);

        TableColumn<DistanceConstraint, Double> meanCol = new TableColumn<>("Mean");
        meanCol.setCellValueFactory((CellDataFeatures<DistanceConstraint, Double> p) -> {
            DistanceConstraint distanceConstraint = p.getValue();
            NOECalibrator.updateDistanceStat(Molecule.getActive(), distanceConstraint);
            DistanceStat distanceStat = distanceConstraint.getStat();
            double v = Math.round(distanceStat.getMean() * 10.0) / 10.0;
            return new ReadOnlyObjectWrapper<>(v);
        });



        tableView.getColumns().addAll(lowerCol, upperCol, meanCol);

    }
    public void setDistanceConstraintSet(DistanceConstraintSet distanceConstraintSet) {
        this.distanceConstraintSet = distanceConstraintSet;
        if (tableView == null) {
            log.warn("null table");
        } else {
            if (distanceConstraintSet == null) {
                stage.setTitle("Distance Constraints: ");
            } else {
                ObservableList<DistanceConstraint> constraints = FXCollections.observableList(distanceConstraintSet.get());
                log.info("constraints {}", constraints.size());
                updateColumns();
                tableView.setItems(constraints);
                tableView.refresh();
                stage.setTitle("Distance Constraints: " + distanceConstraintSet.getName());
            }
        }

    }

    void clearDistanceSet() {
        if (GUIUtils.affirm("Clear active set")) {
            Optional<NoeSet> noeSetOpt = molConstr.activeNOESet();
            noeSetOpt.ifPresent(NoeSet::clear);
            refresh();
        }
    }
}
