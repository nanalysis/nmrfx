package org.nmrfx.example.plugin;

import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import org.nmrfx.analyst.gui.peaks.PeakTableController;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;

import java.util.Set;

public class NMRFxPeakTableExamplePlugin implements NMRFxPlugin {
    PeakTableController peakTableController;
    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {

        return Set.of(
                EntryPoint.PEAK_MENU
        );
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        switch (entryPoint) {
            case PEAK_MENU -> addMenu(object);
            case null, default ->
                    throw new IllegalArgumentException("Only " + EntryPoint.MENU_PLUGINS + " is supported by this plugin " + entryPoint);
        }
    }

      private void addMenu(Object object) {
        if (object instanceof PeakTableController controller) {
            this.peakTableController = controller;
            ToolBar toolBar = peakTableController.getToolBar();
            toolBar.getItems().add(createExampleMenuButton());
        } else {
            throw new IllegalArgumentException("Expected a menu, but received " + (object == null ? "null" : object.getClass().getName()) + " instead");
        }
    }

    private MenuButton createExampleMenuButton() {
        MenuButton exampleMenu = new MenuButton("Example");
        MenuItem exampleMenuItem = new MenuItem("Peak list action");
        exampleMenuItem.setOnAction(e -> analyzePeaks());
        exampleMenu.getItems().addAll(exampleMenuItem);
        return exampleMenu;
    }

    private void analyzePeaks() {
        PeakList peakList = peakTableController.getPeakList();
    }
}
