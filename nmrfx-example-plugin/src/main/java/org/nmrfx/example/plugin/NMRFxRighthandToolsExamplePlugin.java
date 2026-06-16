package org.nmrfx.example.plugin;

import javafx.scene.control.*;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;
import org.nmrfx.processor.gui.ToolController;

import java.util.Set;

public class NMRFxRighthandToolsExamplePlugin implements NMRFxPlugin {

    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {

        return Set.of(
                EntryPoint.RIGHT_TOOLS
        );
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        switch (entryPoint) {
            case RIGHT_TOOLS -> addToToolController((ToolController) object);
            case null, default ->
                    throw new IllegalArgumentException("Only " + EntryPoint.MENU_PLUGINS + " is supported by this plugin " + entryPoint);
        }
    }

    private void addToToolController(ToolController toolController) {
        TitledPane titledPane = new TitledPane();
        titledPane.setText("Plugin example");
        toolController.getAccordion().getPanes().add(titledPane);
        Label label = new Label("Hello");
        titledPane.setContent(label);
    }
}
