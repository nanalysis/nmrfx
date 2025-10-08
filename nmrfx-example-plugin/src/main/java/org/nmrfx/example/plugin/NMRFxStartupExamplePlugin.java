package org.nmrfx.example.plugin;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;
import org.nmrfx.plugin.api.PluginFunction;
import org.nmrfx.processor.gui.SpectrumStatusBar;
import org.nmrfx.processor.gui.ToolController;

import java.util.Set;
import java.util.function.Function;

public class NMRFxStartupExamplePlugin implements NMRFxPlugin {
    Stage stage = null;

    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {

        return Set.of(
                EntryPoint.STARTUP
        );
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        switch (entryPoint) {
            case STARTUP -> startSomethingUp();
            case null, default ->
                    throw new IllegalArgumentException("Only " + EntryPoint.MENU_PLUGINS + " is supported by this plugin " + entryPoint);
        }
    }

    private void startSomethingUp() {
        System.out.println("start something (like a server) up");
    }
   }
