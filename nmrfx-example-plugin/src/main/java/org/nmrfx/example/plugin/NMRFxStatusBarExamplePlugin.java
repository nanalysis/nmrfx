package org.nmrfx.example.plugin;

import javafx.scene.control.*;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;
import org.nmrfx.processor.gui.SpectrumStatusBar;

import java.util.Set;

public class NMRFxStatusBarExamplePlugin implements NMRFxPlugin {
    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {

        return Set.of(
                EntryPoint.STATUS_BAR_TOOLS
        );
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        switch (entryPoint) {
            case STATUS_BAR_TOOLS -> addToStatusBar(object);
            case null, default ->
                    throw new IllegalArgumentException("Only " + EntryPoint.MENU_PLUGINS + " is supported by this plugin " + entryPoint);
        }
    }


    private void addToStatusBar(Object object) {
        if (object instanceof SpectrumStatusBar statusBar) {
            Menu newMenu = new Menu("Example Menu");
            MenuItem testMenuItem = new MenuItem("Example Menu Item");
            newMenu.getItems().addAll(testMenuItem);
            statusBar.addToToolMenu(newMenu);
        }
    }
}
