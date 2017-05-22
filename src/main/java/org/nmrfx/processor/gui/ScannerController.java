/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.processor.gui.controls.FileTableItem;
import org.nmrfx.processor.gui.controls.ScanTable;

/**
 * FXML Controller class
 *
 * @author Bruce Johnson
 */
public class ScannerController implements Initializable {

    @FXML
    private VBox mainBox;
    @FXML
    private VBox opBox;
    @FXML
    private Button scanDirChooserButton;
    @FXML
    private Button loadTableChooserButton;
    @FXML
    private Button processScanDirButton;
    @FXML
    private CheckBox combineFiles;
    @FXML
    private TableView<FileTableItem> tableView;
    FXMLController fxmlController;
    PolyChart chart;
    Stage stage;
    ScanTable scanTable;

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        scanTable = new ScanTable(this, tableView);
    }

    public static ScannerController create(FXMLController fxmlController, Stage parent, PolyChart chart) {
        FXMLLoader loader = new FXMLLoader(SpecAttrWindowController.class.getResource("/fxml/ScannerScene.fxml"));
        ScannerController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);

        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<ScannerController>getController();
            controller.fxmlController = fxmlController;
            controller.stage = stage;
            controller.chart = chart;
            stage.setTitle("NMRFx Processor Scanner");

            stage.initOwner(parent);
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }

    @FXML
    private void processScanDir(ActionEvent event) {
        ChartProcessor chartProcessor = fxmlController.getChartProcessor();
        scanTable.processScanDir(stage, chartProcessor, combineFiles.isSelected());
    }

    @FXML
    private void scanDirAction(ActionEvent event) {
        scanTable.loadScanFiles(stage);
    }

    @FXML
    private void loadTableAction(ActionEvent event) {
        scanTable.loadScanTable();
    }

    @FXML
    private void openSelectedListFile(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2) {
            ProcessorController processorController = fxmlController.getProcessorController();

            String scriptString = processorController.getCurrentScript();
            scanTable.openSelectedListFile(scriptString);
        }
    }

    @FXML
    private void loadScriptTab(Event event) {
    }

    public Stage getStage() {
        return stage;
    }

    public PolyChart getChart() {
        return chart;
    }

    public FXMLController getFXMLController() {
        return fxmlController;
    }
}
