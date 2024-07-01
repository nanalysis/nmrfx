package org.nmrfx.analyst.gui.peaks;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TableColumn;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.controlsfx.control.tableview2.TableView2;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.SpatialSet;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.PeakNavigable;
import org.nmrfx.processor.gui.PeakNavigator;
import org.nmrfx.structure.chemistry.IdPeak;
import org.nmrfx.structure.chemistry.IdResult;
import org.nmrfx.structure.chemistry.MatchCriteria;
import org.nmrfx.structure.chemistry.Molecule;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.List;
import java.util.ResourceBundle;

public class PeakIDController implements Initializable, StageBasedController, PeakNavigable {
    static final Background ERROR_BACKGROUND = new Background(new BackgroundFill(Color.RED, null, null));

    Stage stage;

    @FXML
    TableView2<IdResult> tableView;
    PeakNavigator peakNavigator;
    @FXML
    ToolBar menuBar;

    int currentDims = 0;

    PeakList activePeakList = null;

    IdPeak idPeak;

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


    public static PeakIDController create() {
        PeakIDController controller = Fxml.load(PeakIDController.class, "PeakIDScene.fxml")
                .withNewStage("Peak ID")
                .getController();
        controller.stage.show();

        return controller;
    }

    void initMenuBar() {

        peakNavigator = PeakNavigator.create(this).initialize(menuBar);
        peakNavigator.setPeakList();

    }

    void initTable() {
        updateColumns(2);
    }

    public void gotoPeak(Peak peak) {
        peakNavigator.setPeak(peak);
    }
    @Override
    public void refreshPeakView(Peak peak) {
        if ((peak != null) && (idPeak != null)) {
            if (peak.getPeakList() != activePeakList) {
                refreshPeakListView(peak.getPeakList());
            }

            for (int i = 0; i < peak.getPeakList().getNDim(); i++) {
                matchCriteria[i].setPPM(peak);
            }
            List<SpatialSet>[] matchList = idPeak.scan(matchCriteria);
            List<IdResult> results = idPeak.getIdResults(matchList, matchCriteria);
            updateContributions(results);

            tableView.getItems().clear();
            tableView.getItems().addAll(results);
            tableView.sort();
        }
    }

    void updateContributions(List<IdResult> idResults) {
        double sum = 0.0;
        for (var result: idResults) {
            sum += result.disExp();
        }
        for (var result: idResults) {
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
