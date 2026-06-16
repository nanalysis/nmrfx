package org.nmrfx.example.plugin;

import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.DatasetsController;

import java.util.List;
import java.util.Set;

public class NMRFxDatasetTableExamplePlugin implements NMRFxPlugin {
    DatasetsController datasetsController;
    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {

        return Set.of(
                EntryPoint.DATASET_MENU
        );
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        switch (entryPoint) {
            case DATASET_MENU -> addMenu(object);
            case null, default ->
                    throw new IllegalArgumentException("Only " + EntryPoint.MENU_PLUGINS + " is supported by this plugin " + entryPoint);
        }
    }

      private void addMenu(Object object) {
        if (object instanceof DatasetsController controller) {
            this.datasetsController = controller;
            ToolBar toolBar = datasetsController.getToolBar();
            toolBar.getItems().add(createExampleMenuButton());
        } else {
            throw new IllegalArgumentException("Expected a menu, but received " + (object == null ? "null" : object.getClass().getName()) + " instead");
        }
    }

    private MenuButton createExampleMenuButton() {
        MenuButton exampleMenu = new MenuButton("Example");
        MenuItem exampleMenuItem = new MenuItem("Dataset action");
        exampleMenuItem.setOnAction(e -> analyzeDatasets());
        exampleMenu.getItems().addAll(exampleMenuItem);
        return exampleMenu;
    }

    private void analyzeDatasets() {
        List<Dataset> datasets = datasetsController.getSelectedDatasets();
    }
}
