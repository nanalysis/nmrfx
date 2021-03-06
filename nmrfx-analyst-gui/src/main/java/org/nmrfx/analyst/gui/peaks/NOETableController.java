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

import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
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
import java.text.Format;
import java.util.Optional;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.SpatialSetGroup;
import org.nmrfx.chemistry.constraints.MolecularConstraints;
import org.nmrfx.chemistry.constraints.Noe;
import org.nmrfx.chemistry.constraints.NoeSet;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.project.Project;
import org.nmrfx.structure.noe.NOEAssign;
import org.nmrfx.structure.noe.NOECalibrator;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author johnsonb
 */
public class NOETableController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(NOETableController.class);
    private Stage stage;
    @FXML
    private ToolBar toolBar;
    @FXML
    private TableView<Noe> tableView;
    private NoeSet noeSet;

    Button valueButton;
    Button saveParButton;
    Button closeButton;
    MenuButton noeSetMenuItem;
    MenuButton peakListMenuButton;
    ObservableMap<String, NoeSet> noeSetMap;
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

        noeSetMap = FXCollections.observableMap(molConstr.noeSets);
        MapChangeListener<String, NoeSet> mapChangeListener = (MapChangeListener.Change<? extends String, ? extends NoeSet> change) -> {
            updateNoeSetMenu();
        };

        noeSetMap.addListener(mapChangeListener);
        MapChangeListener<String, PeakList> peakmapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> {
            updatePeakListMenu();
        };
        Project.getActive().addPeakListListener(peakmapChangeListener);

        updateNoeSetMenu();
        updatePeakListMenu();

    }

    public Stage getStage() {
        return stage;
    }

    public static NOETableController create() {
        if (MoleculeFactory.getActive() == null) {
            GUIUtils.warn("NOE Table", "No active molecule");        
            return null;
        }

        
        FXMLLoader loader = new FXMLLoader(NOETableController.class.getResource("/fxml/NoeTableScene.fxml"));
        NOETableController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<NOETableController>getController();
            controller.stage = stage;
            stage.setTitle("Peaks");
            stage.show();
        } catch (IOException ioE) {
            log.warn(ioE.getMessage(), ioE);
        }

        return controller;

    }

    void initToolBar() {

        noeSetMenuItem = new MenuButton("NoeSets");
        peakListMenuButton = new MenuButton("PeakLists");
        toolBar.getItems().addAll(noeSetMenuItem, peakListMenuButton);
        updateNoeSetMenu();
    }

    public void updateNoeSetMenu() {
        noeSetMenuItem.getItems().clear();
        MoleculeBase mol = MoleculeFactory.getActive();

        if (mol != null) {
            MolecularConstraints molConstr = mol.getMolecularConstraints();
            for (String noeSetName : molConstr.getNOESetNames()) {
                MenuItem menuItem = new MenuItem(noeSetName);
                menuItem.setOnAction(e -> {
                    setNoeSet(molConstr.noeSets.get(noeSetName));
                });
                noeSetMenuItem.getItems().add(menuItem);
            }
        }
    }

    public void updatePeakListMenu() {
        peakListMenuButton.getItems().clear();

        for (String peakListName : Project.getActive().getPeakListNames()) {
            MenuItem menuItem = new MenuItem(peakListName);
            menuItem.setOnAction(e -> {
                extractPeakList(PeakList.get(peakListName));
            });
            peakListMenuButton.getItems().add(menuItem);
        }
    }

    public void refreshPeakView() {
        tableView.refresh();
    }

    public NoeSet getNoeSet() {
        return noeSet;
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
        updateColumns();
        ListChangeListener listener = (ListChangeListener) (ListChangeListener.Change c) -> {
            int nSelected = tableView.getSelectionModel().getSelectedItems().size();
            boolean state = nSelected == 1;
        };
        tableView.getSelectionModel().getSelectedIndices().addListener(listener);
//        tableView.setOnMouseClicked(e -> {
//            if (e.getClickCount() == 2) {
//                if (!tableView.getSelectionModel().getSelectedItems().isEmpty()) {
//                    Noe peak = tableView.getSelectionModel().getSelectedItems().get(0);
//                    showPeakInfo(peak);
//                }
//            }
//        });
    }

    void updateColumns() {
        tableView.getColumns().clear();
        StringConverter sConverter = new DefaultStringConverter();

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

        tableView.getColumns().addAll(lowerCol, upperCol, ppmCol, contribCol, networkCol);

    }

    public void setNoeSet(NoeSet noeSet) {
        log.info("set noes {}", noeSet);
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

    void extractPeakList(PeakList peakList) {
        int nDim = peakList.nDim;
        try {
            for (int i = 0; i < nDim; i++) {
                NOEAssign.findMax(peakList, i, 0);
            }
            Optional<NoeSet> noeSetOpt = molConstr.activeNOESet();
            if (noeSetOpt.isEmpty()) {
                noeSet = molConstr.newNOESet("default");
                noeSetOpt = Optional.of(noeSet);
            } else {
                noeSet = noeSetOpt.get();
            }
            NOEAssign.extractNoePeaks2(noeSetOpt, peakList, 2, false, 0);
            if (noeSetOpt.isPresent()) {
                NOECalibrator noeCalibrator = new NOECalibrator(noeSetOpt.get());
                noeCalibrator.updateContributions(false, false);
                noeSet = noeSetOpt.get();
                log.info("active {}", noeSet.getName());
            }
            setNoeSet(noeSet);
            updateNoeSetMenu();
        } catch (InvalidMoleculeException ex) {
            ExceptionDialog exD = new ExceptionDialog(ex);
            exD.show();
        }
    }

    void showPeakInfo(Noe noe) {

    }

    void closePeak() {

    }
}
