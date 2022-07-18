package org.nmrfx.analyst.gui;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.nmrfx.processor.gui.DatasetsController;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.MainApp;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.processor.project.Project;

public class ViewMenuItems extends MenuActions {

    private static final String PROCESSOR_MENU_TEXT = "Show Processor";
    DatasetsController datasetController;
    public ViewMenuItems(AnalystApp app, Menu menu) {
        super(app, menu);
    }

    @Override
    public void basic() {
        MenuItem procMenuItem = new MenuItem(PROCESSOR_MENU_TEXT);
        procMenuItem.setOnAction(e -> FXMLController.getActiveController().showProcessorAction(e));

        MenuItem dataMenuItem = new MenuItem("Show Datasets");
        dataMenuItem.setOnAction(this::showDatasetsTable);

        MenuItem consoleMenuItem = new MenuItem("Show Console");
        consoleMenuItem.setOnAction(e -> showConsole());

        MenuItem logConsoleMenuItem = new MenuItem("Show Log Console");
        logConsoleMenuItem.setOnAction(e -> showLogConsole());

        MenuItem attrMenuItem = new MenuItem("Show Attributes");
        attrMenuItem.setOnAction(e -> FXMLController.getActiveController().showSpecAttrAction(e));

        menu.getItems().addAll(consoleMenuItem, logConsoleMenuItem, dataMenuItem, attrMenuItem, procMenuItem);
        menu.onShowingProperty().set(e -> verifyMenuItems());
    }

    private void verifyMenuItems() {
        for(MenuItem menuItem: menu.getItems()) {
            if (PROCESSOR_MENU_TEXT.equals(menuItem.getText())) {
                menuItem.setDisable(!FXMLController.getActiveController().isProcessorControllerAvailable());
            }
        }
    }

    @Override
    protected void advanced() {

    }

    private void showConsole() {
        AnalystApp.getConsoleController().show();
    }

    private void showLogConsole() {
        MainApp.getLogConsoleController().show();
    }

    void showDatasetsTable(ActionEvent event) {
        if (datasetController == null) {
            datasetController = DatasetsController.create();
        }
        GUIProject project = (GUIProject) Project.getActive();
        ObservableList datasetObs = (ObservableList) project.getDatasets();
        datasetController.setDatasetList(datasetObs);
        datasetController.getStage().show();
        datasetController.getStage().toFront();
    }

}
