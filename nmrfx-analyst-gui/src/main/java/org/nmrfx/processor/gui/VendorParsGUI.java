package org.nmrfx.processor.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.VendorPar;

import java.util.Comparator;
import java.util.List;

public class VendorParsGUI {
    Stage stage = null;
    BorderPane borderPane = new BorderPane();
    Scene stageScene = new Scene(borderPane, 500, 500);
    TableView<VendorPar> fidParTableView = new TableView<>();

    void showStage() {
        //Create new Stage for popup window
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("Vendor Pars");
            stage.setWidth(500);
            stage.setScene(stageScene);
            borderPane.setCenter(fidParTableView);
            initTable();
        }
        stage.show();
        stage.toFront();
    }

    void initTable() {
        TableColumn<VendorPar, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("Name"));
        nameCol.setEditable(false);
        nameCol.setPrefWidth(125);

        TableColumn<VendorPar, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(new PropertyValueFactory<>("Value"));
        valueCol.setEditable(false);
        valueCol.setPrefWidth(260);
        fidParTableView.getColumns().setAll(nameCol, valueCol);

    }

    public void updateParTable(NMRData data) {
        List<VendorPar> vPars = data.getPars();
        vPars.sort(Comparator.comparing(VendorPar::getName));
        ObservableList<VendorPar> pars = FXCollections.observableArrayList(vPars);
        fidParTableView.setItems(pars);
    }
}
