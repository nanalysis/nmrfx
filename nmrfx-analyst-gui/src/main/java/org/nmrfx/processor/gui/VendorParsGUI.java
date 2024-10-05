package org.nmrfx.processor.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
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
    FilteredList<VendorPar> filteredList;
    protected final TextField filterTextField = new TextField();

    void showStage() {
        //Create new Stage for popup window
        if (stage == null) {
            stage = new Stage();
            stage.setTitle("Vendor Pars");
            stage.setWidth(500);
            stage.setScene(stageScene);
            borderPane.setCenter(fidParTableView);
            initTable();
            Label filterLabel = new Label("Filter:");
            filterTextField.textProperty().addListener((observable, oldValue, newValue) -> filterChanged());
            filterTextField.setPrefWidth(200);
            HBox hBox = new HBox();
            hBox.setAlignment(Pos.CENTER_LEFT);
            hBox.setSpacing(5);
            hBox.getChildren().addAll(filterLabel, filterTextField);
            borderPane.setTop(hBox);
        }
        stage.show();
        stage.toFront();
    }
    protected void filterChanged() {
        if (fidParTableView != null) {
            setFilter(filterTextField.getText());
        }
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

    public void setFilter(String filter) {
        String textFormatted = filter.trim().toLowerCase();
        filteredList.setPredicate(vendorPar -> textFormatted.isEmpty()
                || vendorPar.getName().toLowerCase().contains(textFormatted));
    }

    public void updateParTable(NMRData data) {
        List<VendorPar> vPars = data.getPars();
        vPars.sort(Comparator.comparing(VendorPar::getName));
        ObservableList<VendorPar> pars = FXCollections.observableArrayList(vPars);
        filteredList = new FilteredList<>(pars);
        fidParTableView.setItems(filteredList);
    }
}
