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
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.controlsfx.control.MasterDetailPane;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.control.tableview2.FilteredTableColumn;
import org.controlsfx.control.tableview2.FilteredTableView;
import org.controlsfx.control.tableview2.filter.filtereditor.SouthFilter;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.SpatialSetGroup;
import org.nmrfx.chemistry.constraints.*;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.processor.gui.utils.ToolBarUtils;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.noe.NOEAssign;
import org.nmrfx.structure.noe.NOECalibrator;
import org.nmrfx.utils.GUIUtils;
import org.nmrfx.utils.properties.*;
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

    private static final String CONSTRAINT_GENERATION_STRING = "Constraint Generation";
    private static final String EXP_CALIBRATION_STRING = "Calibration-Exponential";
    private static final String ACTIVE_LIMIT_STRING = "Active-Limits";
    private Stage stage;
    @FXML
    private ToolBar toolBar;
    @FXML
    MasterDetailPane masterDetailPane;
    @FXML
    private FilteredTableView<Noe> tableView;
    private NoeSet noeSet;

    MenuButton noeSetMenuItem;
    MenuButton peakListMenuButton;
    ObservableMap<String, NoeSet> noeSetMap;
    MoleculeBase molecule = null;
    MolecularConstraints molConstr = null;
    PropertySheet propertySheet;
    CheckBox detailsCheckBox;
    IntRangeOperationItem maxAmbigItem;
    BooleanOperationItem strictItem;
    BooleanOperationItem unambiguousItem;
    BooleanOperationItem includeDiagItem;
    BooleanOperationItem useDistancesItem;
    BooleanOperationItem autoAssignItem;

    BooleanOperationItem onlyFrozenItem;
    DoubleRangeOperationItem refDistanceItem;
    DoubleRangeOperationItem expItem;
    DoubleRangeOperationItem minDisItem;

    DoubleRangeOperationItem maxViolationItem;

    DoubleRangeOperationItem minContributionItem;
    DoubleRangeOperationItem minPPMErrorItem;
    DoubleRangeOperationItem maxDisItem;
    DoubleRangeOperationItem fErrorItem;
    ChoiceOperationItem modeItem;
    FilteredList<Noe> filteredNOEs;
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

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initToolBar();
        tableView = new FilteredTableView<>();
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
        GUIProject.getActive().addPeakListSubscription(this::updatePeakListMenu);

        updateNoeSetMenu();
        updatePeakListMenu();
        masterDetailPane.showDetailNodeProperty().bindBidirectional(detailsCheckBox.selectedProperty());

        maxAmbigItem = new IntRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                20, 1, 40, CONSTRAINT_GENERATION_STRING, "Maximum Ambiguity", "Maximum Ambiguity");

        autoAssignItem = new BooleanOperationItem(propertySheet, (a, b, c) -> refresh(), false, CONSTRAINT_GENERATION_STRING, "Auto-Assign", "Autoassign unassigned peaks");

        onlyFrozenItem = new BooleanOperationItem(propertySheet, (a, b, c) -> refresh(), false, CONSTRAINT_GENERATION_STRING, "Only Frozen", "Only extract from frozen peaks");

        strictItem = new BooleanOperationItem(propertySheet, (a, b, c) -> refresh(), false, CONSTRAINT_GENERATION_STRING, "Strictly Assign", "Only extract assigned peaks");

        unambiguousItem = new BooleanOperationItem(propertySheet, (a, b, c) -> refresh(), false, CONSTRAINT_GENERATION_STRING, "Only Unambiguous", "Only extract unambiguous peaks");

        includeDiagItem = new BooleanOperationItem(propertySheet, (a, b, c) -> refresh(), false, CONSTRAINT_GENERATION_STRING, "Include Diagonal", "Include diagonal peaks");

        useDistancesItem = new BooleanOperationItem(propertySheet, (a, b, c) -> refresh(), false, CONSTRAINT_GENERATION_STRING, "Use Distances", "Use distances in contributions");

        List<String> intVolChoice = List.of("Intensity", "Volume");
        modeItem = new ChoiceOperationItem(propertySheet, (a, b, c) -> refresh(), "intensity", intVolChoice, EXP_CALIBRATION_STRING, "Mode", "Reference Distance");
        refDistanceItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                3.0, 1.0, 6.0, false, EXP_CALIBRATION_STRING, "Ref Distance", "Reference Distance");
        expItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                6.0, 1.0, 6.0, false, EXP_CALIBRATION_STRING, "Exp Factor", "Exponent value");
        minDisItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                2.0, 1.0, 3.0, false, EXP_CALIBRATION_STRING, "Min Distance", "Minimum bound");
        maxDisItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                6.0, 3.0, 6.0, false, EXP_CALIBRATION_STRING, "Max Distance", "Maximum bound");
        fErrorItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                0.125, 0.0, 0.2, false, EXP_CALIBRATION_STRING, "Tolerance", "Fractional additional bound");

        refDistanceItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                3.0, 1.0, 6.0, false, EXP_CALIBRATION_STRING, "Ref Distance", "Reference Distance");

        maxViolationItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                1.5, 0.5, 10.0, false, ACTIVE_LIMIT_STRING,
                "Max Violation", "Maximum violation of upper bound");

        minContributionItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                0.5, 0.0, 1.0, false, ACTIVE_LIMIT_STRING,
                "Min Contribution", "Minimum contribution allowed");
        minPPMErrorItem = new DoubleRangeOperationItem(propertySheet, (a, b, c) -> refresh(),
                0.5, 0.0, 1.0, false, ACTIVE_LIMIT_STRING,
                "Min PPM Error", "Minimum ppmError allowed");

        propertySheet.getItems().addAll(
                autoAssignItem, strictItem, onlyFrozenItem, includeDiagItem, unambiguousItem, useDistancesItem, maxAmbigItem,
                modeItem, refDistanceItem, expItem, minDisItem, maxDisItem, fErrorItem,
                maxViolationItem, minContributionItem, minPPMErrorItem
        );
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
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> update());
        noeSetMenuItem = new MenuButton("NoeSets");
        peakListMenuButton = new MenuButton("PeakLists");
        detailsCheckBox = new CheckBox("Options");
        noeSetMenuItem.setOnContextMenuRequested(e -> updateNoeSetMenu());

        toolBar.getItems().addAll(exportButton, clearButton, noeSetMenuItem, peakListMenuButton,
                calibrateButton, refreshButton, ToolBarUtils.makeFiller(20), detailsCheckBox);
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

    void initTable() {
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        updateColumns();
        tableView.setOnMouseClicked(e -> {
            if ((e.getClickCount() == 2) && !tableView.getSelectionModel().getSelectedItems().isEmpty()){
                Noe noe = tableView.getSelectionModel().getSelectedItems().get(0);
                showPeakInfo(noe);
            }
        });
    }
    public static void addConstraintColumns(TableView tableView) {
        TableColumn<? extends Noe, Float> lowerCol = new TableColumn<>("Lower");
        lowerCol.setCellValueFactory(new PropertyValueFactory<>("Lower"));
        lowerCol.setCellFactory(new ColumnFormatter<>(new DecimalFormat(".00")));
        lowerCol.setPrefWidth(75);

        TableColumn<Noe, Float> upperCol = new TableColumn<>("Upper");
        upperCol.setCellValueFactory(new PropertyValueFactory<>("Upper"));
        upperCol.setCellFactory(new ColumnFormatter<>(new DecimalFormat(".00")));
        upperCol.setPrefWidth(75);

        TableColumn<Noe, Double> meanCol = new TableColumn<>("Mean");
        meanCol.setCellValueFactory((CellDataFeatures<Noe, Double> p) -> {
            Noe distanceConstraint = p.getValue();
            NOECalibrator.updateDistanceStat(Molecule.getActive(), distanceConstraint);
            DistanceStat distanceStat = distanceConstraint.getStat();
            double v = Math.round(distanceStat.getMean() * 10.0) / 10.0;
            return new ReadOnlyObjectWrapper<>(v);
        });



        tableView.getColumns().addAll(lowerCol, upperCol, meanCol);

    }

    void updateColumns() {
        tableView.getColumns().clear();

        TableColumn<Noe, Integer> idNumCol = new TableColumn<>("id");
        idNumCol.setCellValueFactory(new PropertyValueFactory<>("ID"));
        idNumCol.setEditable(false);
        idNumCol.setPrefWidth(50);

        TableColumn<Noe, String> peakListCol = new TableColumn<>("PeakList");
        peakListCol.setCellValueFactory(new PropertyValueFactory<>("PeakListName"));

        FilteredTableColumn<Noe, Integer> peakNumCol = new FilteredTableColumn<>("PeakNum");
        peakNumCol.setCellValueFactory(new PropertyValueFactory<>("PeakNum"));
        SouthFilter<Noe, Integer> peakNumFilter = new SouthFilter<>(peakNumCol, Integer.class);
        peakNumCol.setSouthNode(peakNumFilter);

        TableColumn<Noe, Integer> nPossibleCol = new TableColumn<>("N");
        nPossibleCol.setCellValueFactory(new PropertyValueFactory<>("NPossible"));

        FilteredTableColumn<Noe, Boolean> activeColumn = new FilteredTableColumn<>("Active");
        activeColumn.setCellValueFactory(new PropertyValueFactory<>("Active"));
        activeColumn.setPrefWidth(50);
        SouthFilter<Noe, Boolean> editorFirstNameFilter = new SouthFilter<>(activeColumn, Boolean.class);
        activeColumn.setSouthNode(editorFirstNameFilter);

        tableView.getColumns().addAll(idNumCol, peakListCol, peakNumCol, nPossibleCol, activeColumn);

        for (int i = 0; i < 2; i++) {
            final int iGroup = i;
            FilteredTableColumn<Noe, Integer> entityCol = new FilteredTableColumn<>("entity" + (iGroup + 1));
            entityCol.setCellValueFactory((CellDataFeatures<Noe, Integer> p) -> {
                Noe noe = p.getValue();
                SpatialSetGroup spg = iGroup == 0 ? noe.getSpg1() : noe.getSpg2();
                Integer res = spg.getSpatialSet().atom.getTopEntity().getIDNum();
                return new ReadOnlyObjectWrapper<>(res);
            });
            SouthFilter<Noe, Integer> entityFilter = new SouthFilter<>(entityCol, Integer.class);
            entityCol.setSouthNode(entityFilter);

            FilteredTableColumn<Noe, Integer> resCol = new FilteredTableColumn<>("res" + (iGroup + 1));
            resCol.setCellValueFactory((CellDataFeatures<Noe, Integer> p) -> {
                Noe noe = p.getValue();
                SpatialSetGroup spg = iGroup == 0 ? noe.getSpg1() : noe.getSpg2();
                Integer res = spg.getSpatialSet().atom.getResidueNumber();
                return new ReadOnlyObjectWrapper<>(res);
            });
            SouthFilter<Noe, Integer> resFilter = new SouthFilter<>(resCol, Integer.class);
            resCol.setSouthNode(resFilter);

            FilteredTableColumn<Noe, String> atomCol = new FilteredTableColumn<>("aname" + (iGroup + 1));
            atomCol.setCellValueFactory((CellDataFeatures<Noe, String> p) -> {
                Noe noe = p.getValue();
                SpatialSetGroup spg = iGroup == 0 ? noe.getSpg1() : noe.getSpg2();
                String aname = spg.getSpatialSet().atom.getName();
                return new ReadOnlyObjectWrapper<>(aname);
            });
            SouthFilter<Noe, String> anameFilter = new SouthFilter<>(atomCol, String.class);
            atomCol.setSouthNode(anameFilter);

            tableView.getColumns().addAll(entityCol, resCol, atomCol);
        }

        addConstraintColumns(tableView);


        TableColumn<Noe, Double> boundsColumn = new TableColumn<>("Bounds");

        boundsColumn.setCellFactory((tableColumn) -> {
            TableCell<Noe, Double> tableCell = new TableCell<>() {
                @Override
                protected void updateItem(Double item, boolean empty) {
                    super.updateItem(item, empty);
                    var tableRow = getTableRow();
                    if (tableRow != null) {
                        Noe distanceConstraint = tableRow.getItem();
                        if (distanceConstraint != null) {
                            DistanceStat distanceStat = distanceConstraint.getStat();
                            double scale = 30.0;
                            double width = getTableColumn().getWidth();
                            double lower = distanceConstraint.getLower() * scale;
                            double upper = distanceConstraint.getUpper() * scale;
                            double disMin = distanceStat.getMin() * scale;
                            double disMax = distanceStat.getMax() * scale;
                            double disMean = distanceStat.getMean() * scale;

                            double height = 20;
                            Pane pane = new Pane();
                            double top = 2;
                            double bottom = height - 2;
                            Rectangle rect = new Rectangle(disMin, top, disMax - disMin, bottom - top);
                            Color color;
                            if (disMax < upper) {
                                color = Color.LIGHTGREEN;
                            } else if (disMean < upper) {
                                color = Color.ORANGE;
                            } else {
                                color = Color.RED;
                            }
                            rect.setFill(color);
                            Line line = new Line(disMean, top, disMean, bottom);
                            Line lineH = new Line(2, height / 2.0, width - 2, height / 2.0);
                            line.setStrokeWidth(3);
                            Polygon lowerArrow = new Polygon();
                            double delta = 5.0;
                            lowerArrow.getPoints().addAll(new Double[]{
                                    lower - delta, bottom,
                                    lower, height / 2.0,
                                    lower + delta, bottom});
                            Polygon upperArrow = new Polygon();
                            upperArrow.getPoints().addAll(new Double[]{
                                    upper - delta, top,
                                    upper, height / 2.0,
                                    upper + delta, top});

                            pane.getChildren().addAll(rect, line, lineH, lowerArrow, upperArrow);
                            this.setText(null);
                            this.setGraphic(pane);
                        }
                    }
                }
            };

            return tableCell;
        });
        boundsColumn.setPrefWidth(150);
        boundsColumn.widthProperty().addListener(e -> tableView.refresh());
        TableColumn<Noe, Float> ppmCol = new TableColumn<>("PPM");
        ppmCol.setCellValueFactory(new PropertyValueFactory<>("PpmError"));
        ppmCol.setCellFactory(new ColumnFormatter<>(new DecimalFormat(".00")));
        ppmCol.setPrefWidth(75);

        TableColumn<Noe, Float> contribCol = new TableColumn<>("Contrib");
        contribCol.setCellValueFactory(new PropertyValueFactory<>("Contribution"));
        contribCol.setCellFactory(new ColumnFormatter<>(new DecimalFormat(".00")));
        contribCol.setPrefWidth(75);

        TableColumn<Noe, Float> networkCol = new TableColumn<>("Network");
        networkCol.setCellValueFactory(new PropertyValueFactory<>("NetworkValue"));
        networkCol.setCellFactory(new ColumnFormatter<>(new DecimalFormat(".00")));
        networkCol.setPrefWidth(75);

        TableColumn<Noe, Float> disContribCol = new TableColumn<>("Dis");
        disContribCol.setCellValueFactory(new PropertyValueFactory<>("DisContrib"));
        disContribCol.setCellFactory(new ColumnFormatter<>(new DecimalFormat(".00")));
        disContribCol.setPrefWidth(75);

        TableColumn<Noe, String> flagCol = new TableColumn<>("Flags");
        flagCol.setCellValueFactory(new PropertyValueFactory<>("ActivityFlags"));
        flagCol.setPrefWidth(75);


        tableView.getColumns().addAll(boundsColumn, ppmCol, disContribCol, networkCol, contribCol, flagCol);

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
                filteredNOEs = new FilteredList<>(noes);
                filteredNOEs.predicateProperty().bind(tableView.predicateProperty());

                tableView.setItems(filteredNOEs);

                tableView.refresh();
                stage.setTitle("Noes: " + noeSet.getName());
            }
        }

    }

    void applyLimits(NOECalibrator noeCalibrator) {
        noeCalibrator.limitToMaxViol(maxViolationItem.getValue());
        noeCalibrator.limitToMinContrib(minContributionItem.getValue());
        noeCalibrator.limitToMinPPMError(minPPMErrorItem.getValue());
    }
    void calibrate() {
        if (noeSet == null) {
            Optional<NoeSet> noeSetOpt = molConstr.activeNOESet();
            noeSet = noeSetOpt.orElseGet(() -> molConstr.newNOESet("default"));
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
            applyLimits(noeCalibrator);

            setNoeSet(noeSet);
            refresh();
        }
    }
    void update() {
        if (noeSet == null) {
            Optional<NoeSet> noeSetOpt = molConstr.activeNOESet();
            noeSet = noeSetOpt.orElseGet(() -> molConstr.newNOESet("default"));
        }
        if (noeSet != null) {
            NOECalibrator noeCalibrator = new NOECalibrator(noeSet);
            noeCalibrator.updateContributions(true, true, false);
            setNoeSet(noeSet);
            refresh();
        }
    }

    void extractPeakList(PeakList peakList) {
        if (NOEAssign.getProtonDims(peakList).isEmpty()) {
            GUIUtils.warn("Extract Peaks", "Peak list " + peakList.getName() + " doesn't have two proton dimensions");
            return;
        }
        int nDim = peakList.nDim;
        try {
            if (autoAssignItem.getValue()) {
                for (int i = 0; i < nDim; i++) {
                    NOEAssign.findMax(peakList, i, 0, onlyFrozenItem.getValue());
                }
            }
            Optional<NoeSet> noeSetOpt = molConstr.activeNOESet();
            if (noeSetOpt.isEmpty()) {
                noeSet = molConstr.newNOESet("default");
                noeSetOpt = Optional.of(noeSet);
            }
            if (!autoAssignItem.getValue()) {
                NOEAssign.extractNoePeaks(noeSet, peakList, unambiguousItem.getValue(), onlyFrozenItem.getValue(),
                        includeDiagItem.getValue());
            } else {
                NOEAssign.extractNoePeaks2(noeSetOpt, peakList, maxAmbigItem.get(), strictItem.getValue(), 0, onlyFrozenItem.getValue());
            }
            NOECalibrator noeCalibrator = new NOECalibrator(noeSetOpt.get());
            noeCalibrator.updateContributions(useDistancesItem.getValue(), false, true);
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
            if (filteredNOEs != null) {
                filteredNOEs.predicateProperty().unbind();
            }
            tableView.setItems(FXCollections.emptyObservableList());
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
