package org.nmrfx.analyst.gui.peaks;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import org.controlsfx.control.tableview2.TableView2;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.SpatialSet;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.PeakNavigable;
import org.nmrfx.processor.gui.PeakNavigator;
import org.nmrfx.structure.chemistry.IdPeak;
import org.nmrfx.structure.chemistry.IdResult;
import org.nmrfx.structure.chemistry.MatchCriteria;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.utils.GUIUtils;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class PeakIDController implements Initializable, StageBasedController, PeakNavigable {
    Stage stage;

    @FXML
    TableView2<IdResult> tableView;
    PeakNavigator peakNavigator;

    PeakNavigator originalNavigator;
    @FXML
    ToolBar menuBar;

    int currentDims = 0;

    PeakList activePeakList = null;

    IdPeak idPeak;

    ChoiceBox<Integer> ppmSetChoice;
    ChoiceBox<Integer> refSetChoice;
    MatchCriteria[] matchCriteria;

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initMenuBar();
        initTable();
    }


    public static PeakIDController create(PeakNavigator navigator) {
        PeakIDController controller = Fxml.load(PeakIDController.class, "PeakIDScene.fxml")
                .withNewStage("Peak ID")
                .getController();
        controller.stage.show();
        controller.originalNavigator = navigator;

        return controller;
    }

    void initMenuBar() {
        peakNavigator = PeakNavigator.create(this).initialize(menuBar);
        ppmSetChoice =  new ChoiceBox<>();
        ppmSetChoice.setPrefWidth(40);
        ppmSetChoice.getItems().addAll(-1, 0, 1, 2, 3, 4);
        refSetChoice =  new ChoiceBox<>();
        refSetChoice.setPrefWidth(40);
        refSetChoice.getItems().addAll(-1, 0, 1, 2, 3, 4);
        ppmSetChoice.setValue(0);
        refSetChoice.setValue(-1);
        Button button = new Button("Assign");
        button.setOnAction(e -> assignPeaks());
        Label useLabel = new Label("Use:");
        Label ppmLabel = new Label("Assigned");
        Label refLabel = new Label("Ref.");
        menuBar.getItems().addAll(button, useLabel, ppmLabel, ppmSetChoice, refLabel, refSetChoice);
        peakNavigator.setPeakList();

    }

    void initTable() {
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        updateColumns(2);
    }

    void assignPeaks() {
        var items = tableView.getSelectionModel().getSelectedItems();
        Peak peak = peakNavigator.getPeak();
        if (peak != null) {
            PeakList peakList = peak.getPeakList();
            for (int iDim = 0; iDim < peakList.getNDim(); iDim++) {
                PeakDim peakDim = peak.getPeakDim(iDim);
                List<String> labels = new ArrayList<>();
                for (var idResult : items) {
                    SpatialSet spatialSet = idResult.getSpatialSet(iDim);
                    Atom atom = spatialSet.getAtom();
                    labels.add(atom.getShortName());
                }
                peakDim.setLabel(labels);
            }
        }
    }

    public void gotoPeak(Peak peak) {
        peakNavigator.setPeak(peak);
    }

    @Override
    public void refreshPeakView(Peak peak) {
        tableView.getItems().clear();
        if ((peak != null) && (idPeak != null)) {
            idPeak.setPPMSet(ppmSetChoice.getValue());
            idPeak.setRefSet(refSetChoice.getValue());
            if (peak.getPeakList() != activePeakList) {
                refreshPeakListView(peak.getPeakList());
            }

            for (int i = 0; i < peak.getPeakList().getNDim(); i++) {
                matchCriteria[i].setPPM(peak);
            }
            List<SpatialSet>[] matchList = null;
            try {
                matchList = idPeak.scan(matchCriteria);
            } catch (IllegalArgumentException illegalArgumentException) {
                GUIUtils.warn("Peak ID", "No atoms with chemical shifts match pattern");
            }
            if (matchList != null) {
                List<IdResult> results = idPeak.getIdResults(matchList, matchCriteria);
                updateContributions(results);
                tableView.getItems().addAll(results);
                tableView.sort();
            }
            originalNavigator.setPeak(peak);
        }
    }

    void updateContributions(List<IdResult> idResults) {
        double sum = 0.0;
        for (var result : idResults) {
            sum += result.disExp();
        }
        for (var result : idResults) {
            double contrib = sum > 0.0 ? result.disExp() / sum : 0.0;
            result.setContrib(contrib);
        }

    }

    @Override
    public void refreshPeakView() {

    }

    @Override
    public void refreshPeakListView(PeakList peakList) {
        if (peakList != null) {
            updateColumns(peakList.getNDim());
            idPeak = new IdPeak();
            idPeak.setPPMSet(ppmSetChoice.getValue());
            idPeak.setRefSet(refSetChoice.getValue());
            matchCriteria = idPeak.setup(peakList, Molecule.getActive());
            idPeak.clearAtomList();
            idPeak.getAtomsWithPPMs();
        }
    }

    private static class DimTableColumn<S, T> extends TableColumn<S, T> {

        int peakDim;

        DimTableColumn(String title, int iDim) {
            super(title + ":" + (iDim + 1));
            peakDim = iDim;
        }
    }

    void updateColumns(int nDim) {
        if (nDim == currentDims) {
            return;
        }
        tableView.getItems().clear();
        tableView.getColumns().clear();
        currentDims = nDim;


        for (int i = 0; i < nDim; i++) {
            DimTableColumn<IdResult, String> resCol = new DimTableColumn<>("Res", i);
            resCol.setCellValueFactory((TableColumn.CellDataFeatures<IdResult, String> p) -> {
                IdResult idResult = p.getValue();
                int iDim = resCol.peakDim;
                SpatialSet spatialSet = idResult.getSpatialSet(iDim);
                Atom atom = spatialSet.getAtom();
                String oneChar = "";
                if (atom.getEntity() instanceof Residue residue) {
                    oneChar = String.valueOf(residue.getOneLetter());
                }
                return new ReadOnlyObjectWrapper<>(oneChar);
            });
            resCol.setPrefWidth(20);
            tableView.getColumns().add(resCol);

            DimTableColumn<IdResult, Integer> labelCol = new DimTableColumn<>("Seq", i);
            labelCol.setCellValueFactory((TableColumn.CellDataFeatures<IdResult, Integer> p) -> {
                IdResult idResult = p.getValue();
                int iDim = labelCol.peakDim;
                SpatialSet spatialSet = idResult.getSpatialSet(iDim);
                int residueNumber = spatialSet.getAtom().getResidueNumber();
                return new ReadOnlyObjectWrapper<>(residueNumber);
            });
            labelCol.setPrefWidth(40);


            tableView.getColumns().add(labelCol);
            DimTableColumn<IdResult, String> atomCol = new DimTableColumn<>("Atm", i);
            atomCol.setCellValueFactory((TableColumn.CellDataFeatures<IdResult, String> p) -> {
                IdResult idResult = p.getValue();
                int iDim = labelCol.peakDim;
                SpatialSet spatialSet = idResult.getSpatialSet(iDim);
                String aName = spatialSet.getAtom().getName();
                return new ReadOnlyObjectWrapper<>(aName);
            });
            atomCol.setPrefWidth(40);
            tableView.getColumns().add(atomCol);
        }
        for (int i = 0; i < nDim; i++) {
            DimTableColumn<IdResult, Double> dpCol = new DimTableColumn<>("%Tol", i);
            dpCol.setCellValueFactory((TableColumn.CellDataFeatures<IdResult, Double> p) -> {
                IdResult idResult = p.getValue();
                int iDim = dpCol.peakDim;
                double delta = idResult.getDP(iDim);
                return new ReadOnlyObjectWrapper<>(delta);
            });
            dpCol.setCellFactory(new PeakTableController.ColumnFormatter<>(new DecimalFormat(".0")));
            dpCol.setPrefWidth(50);
            tableView.getColumns().add(dpCol);
        }
        TableColumn<IdResult, Double> tolCol = new TableColumn<>("Tol%");
        tolCol.setCellValueFactory((TableColumn.CellDataFeatures<IdResult, Double> p) -> {
            IdResult idResult = p.getValue();
            double delta = idResult.getDPAvg();
            return new ReadOnlyObjectWrapper<>(delta);
        });
        tolCol.setCellFactory(new PeakTableController.ColumnFormatter<>(new DecimalFormat(".0")));
        tolCol.setPrefWidth(50);
        tableView.getColumns().add(tolCol);

        TableColumn<IdResult, Double> inRangeCol = new TableColumn<>("Str%");
        inRangeCol.setCellValueFactory((TableColumn.CellDataFeatures<IdResult, Double> p) -> {
            IdResult idResult = p.getValue();
            double delta = idResult.inRange();
            return new ReadOnlyObjectWrapper<>(delta);
        });
        inRangeCol.setCellFactory(new PeakTableController.ColumnFormatter<>(new DecimalFormat(".0")));
        inRangeCol.setPrefWidth(50);
        tableView.getColumns().add(inRangeCol);

        TableColumn<IdResult, Double> avgCol = new TableColumn<>("Avg");
        avgCol.setCellValueFactory(new PropertyValueFactory<>("Dis"));
        avgCol.setCellFactory(new PeakTableController.ColumnFormatter<>(new DecimalFormat(".00")));
        avgCol.setPrefWidth(50);
        tableView.getColumns().add(avgCol);

        TableColumn<IdResult, Double> minCol = new TableColumn<>("Min");
        minCol.setCellValueFactory(new PropertyValueFactory<>("DisMin"));
        minCol.setCellFactory(new PeakTableController.ColumnFormatter<>(new DecimalFormat(".00")));
        minCol.setPrefWidth(50);
        tableView.getColumns().add(minCol);

        TableColumn<IdResult, Double> maxCol = new TableColumn<>("Max");
        maxCol.setCellValueFactory(new PropertyValueFactory<>("DisMax"));
        maxCol.setCellFactory(new PeakTableController.ColumnFormatter<>(new DecimalFormat(".00")));
        maxCol.setPrefWidth(50);
        tableView.getColumns().add(maxCol);

        TableColumn<IdResult, Double> contribColumn = new TableColumn<>("Contrib");
        contribColumn.setCellValueFactory(new PropertyValueFactory<>("Contrib"));
        contribColumn.setCellFactory(new PeakTableController.ColumnFormatter<>(new DecimalFormat(".00")));
        contribColumn.setPrefWidth(50);
        tableView.getColumns().add(contribColumn);


    }
}
