package org.nmrfx.analyst.gui;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.nmrfx.analyst.gui.regions.RegionsTableController;
import org.nmrfx.processor.gui.DatasetsController;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.project.ProjectBase;

public class ViewMenuItems extends MenuActions {

    private static final String PROCESSOR_MENU_TEXT = "Show Processor";
    DatasetsController datasetController;

    public ViewMenuItems(AnalystApp app, Menu menu) {
        super(app, menu);
    }

    @Override
    public void basic() {
        MenuItem dataMenuItem = new MenuItem("Show Datasets");
        dataMenuItem.setOnAction(this::showDatasetsTable);

        MenuItem consoleMenuItem = new MenuItem("Show Console");
        consoleMenuItem.setOnAction(e -> showConsole());

        MenuItem logConsoleMenuItem = new MenuItem("Show Log Console");
        logConsoleMenuItem.setOnAction(e -> showLogConsole());


        MenuItem integralTableItem = new MenuItem("Show Regions Table");
        integralTableItem.setOnAction(e -> showRegionsTable());

        menu.getItems().addAll(consoleMenuItem, logConsoleMenuItem, dataMenuItem, integralTableItem);
    }

    @Override
    protected void advanced() {

    }

    private void showConsole() {
        AnalystApp.getConsoleController().show();
    }

    private void showLogConsole() {
        AnalystApp.getLogConsoleController().show();
    }

    void showDatasetsTable(ActionEvent event) {
        if (datasetController == null) {
            datasetController = DatasetsController.create();
        }
        GUIProject project = (GUIProject) ProjectBase.getActive();
        ObservableList datasetObs = (ObservableList) project.getDatasets();
        datasetController.setDatasetList(datasetObs);
        datasetController.getStage().show();
        datasetController.getStage().toFront();
    }

    private void showRegionsTable() {
        RegionsTableController rtc = RegionsTableController.getRegionsTableController();
        rtc.show();
    }

}
