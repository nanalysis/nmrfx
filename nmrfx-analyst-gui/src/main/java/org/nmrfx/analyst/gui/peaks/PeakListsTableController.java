package org.nmrfx.analyst.gui.peaks;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.FXMLController;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controller for the PeakLists Table
 */
public class PeakListsTableController implements Initializable, StageBasedController {
    private static PeakListsTableController peakListsTableController = null;
    private PeakListsTable peakListsTable;
    private Stage stage;
    @FXML
    private BorderPane peakListsBorderPane;
    @FXML
    private MenuButton editMenuButton;
    @FXML
    private Button refreshButton;
    @FXML
    private Button inspectorButton;
    @FXML
    private Button tableButton;

    public static PeakListsTableController create() {
        PeakListsTableController controller = Fxml.load(PeakListsTableController.class, "PeakListsScene.fxml")
                .withNewStage("PeakLists")
                .getController();

        PeakListsTableController.peakListsTableController = controller;
        return controller;
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    @Override
    public void initialize(URL location, ResourceBundle rb) {
        peakListsTable = new PeakListsTable();
        peakListsBorderPane.setCenter(peakListsTable);
        refreshButton.setOnAction(event -> peakListsTable.updatePeakLists());
        tableButton.disableProperty().bind(peakListsTable.getSelectionModel().selectedItemProperty().isNull());
        tableButton.setOnAction(event -> showPeakListsTable());
        inspectorButton.disableProperty().bind(peakListsTable.getSelectionModel().selectedItemProperty().isNull());
        inspectorButton.setOnAction(event -> showPeakInspector());


        MenuItem compressMenuItem = new MenuItem("Compress");
        compressMenuItem.setOnAction(e -> compressPeakList());
        editMenuButton.getItems().add(compressMenuItem);

        MenuItem degapMenuItem = new MenuItem("Degap");
        degapMenuItem.setOnAction(e -> renumberPeakList());
        editMenuButton.getItems().add(degapMenuItem);

        MenuItem compressAndDegapMenuItem = new MenuItem("Compress and Degap");
        compressAndDegapMenuItem.setOnAction(e -> compressAndDegapPeakList());
        editMenuButton.getItems().add(compressAndDegapMenuItem);

        MenuItem deleteMenuItem = new MenuItem("Delete List");
        deleteMenuItem.setOnAction(e -> deletePeakList());
        editMenuButton.getItems().add(deleteMenuItem);

        MenuItem duplicateMenuItem = new MenuItem("Duplicate");
        duplicateMenuItem.setOnAction(e -> duplicatePeakList());
        editMenuButton.getItems().add(duplicateMenuItem);
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    void showPeakInspector() {
        PeakList peakList = peakListsTable.getSelectionModel().getSelectedItem();
        if (peakList != null) {
            FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
            controller.showPeakAttr();
            controller.getPeakAttrController().setPeakList(peakList);
        }
    }

    PeakList getPeakList() {
        return peakListsTable.getSelectionModel().getSelectedItem();
    }

    void showPeakListsTable() {
        PeakList peakList = getPeakList();
        if (peakList != null) {
            AnalystApp.getAnalystApp().showPeakTable(peakList);
        }
    }

    /**
     * Gets the PeakListsTableController. A new controller is created if one has not already been made.
     *
     * @return The PeakListsTableController instance.
     */
    public static PeakListsTableController getPeakListsTableController() {
        if (peakListsTableController == null) {
            peakListsTableController = PeakListsTableController.create();
        }
        peakListsTableController.peakListsTable.updatePeakLists();
        return peakListsTableController;
    }

    void compressPeakList() {
        if (getPeakList() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Remove deleted peaks (permanent!)");
            alert.showAndWait().ifPresent(response -> {
                getPeakList().compress();
                peakListsTable.updatePeakLists();
            });
        }
    }

    void renumberPeakList() {
        if (getPeakList() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Renumber peak list (permanent!)");
            alert.showAndWait().ifPresent(response -> {
                getPeakList().reNumber();
                peakListsTable.updatePeakLists();
            });
        }
    }

    void compressAndDegapPeakList() {
        if (getPeakList() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Remove deleted peaks and renumber (permanent!)");
            alert.showAndWait().ifPresent(response -> {
                getPeakList().compress();
                getPeakList().reNumber();
                peakListsTable.updatePeakLists();
            });
        }
    }

    void deletePeakList() {
        if (getPeakList() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete Peak List");
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    PeakList.remove(getPeakList().getName());
                    peakListsTable.updatePeakLists();
                }
            });
        }
    }

    void duplicatePeakList() {
        if (getPeakList() != null) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setContentText("New Peak List Name");
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                PeakList newPeakList = getPeakList().copy(result.get(), false, false, true);
                if (newPeakList != null) {
                    peakListsTable.updatePeakLists();
                }
            }
        }
    }


}
