package org.nmrfx.example.plugin;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;
import org.nmrfx.plugin.api.PluginFunction;

import java.util.Set;
import java.util.function.Function;

public class NMRFxPluginMenuExamplePlugin implements NMRFxPlugin {
    Stage stage = null;

    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {

        return Set.of(
                EntryPoint.MENU_PLUGINS
        );
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        switch (entryPoint) {
            case MENU_PLUGINS -> addToPluginMenu(object);
            case null, default ->
                    throw new IllegalArgumentException("Only " + EntryPoint.MENU_PLUGINS + " is supported by this plugin " + entryPoint);
        }
    }

    private void addToPluginMenu(Object object) {
        Menu menu;
        Function<String, String> nmrfxFunction;
        if (object instanceof PluginFunction(Object guiObject, Function<String, String> pluginFunction)) {
            menu = (Menu) guiObject;
            nmrfxFunction = pluginFunction;
        } else if ((object instanceof Menu)) {
            menu = (Menu) object;
        } else {
            throw new IllegalArgumentException("Expected a menu, but received " + (object == null ? "null" : object.getClass().getName()) + " instead");
        }
        menu.getItems().add(createExampleMenu());
    }

    private Menu createExampleMenu() {
        Menu exampleMenu = new Menu("Example");
        MenuItem exampleMenuItem = new MenuItem("Show Plugin");
        exampleMenuItem.setOnAction(e -> showPlugin());
        exampleMenu.getItems().addAll(exampleMenuItem);
        return exampleMenu;
    }

    private void showPlugin() {
        if (stage == null) {
            stage = new Stage(StageStyle.DECORATED);
        }
        stage.toFront();
        stage.show();
        BorderPane borderPane = new BorderPane();
        Scene scene = new Scene(borderPane);
        Button button = new Button("Test");
        ToolBar toolBar = new ToolBar();
        borderPane.setTop(toolBar);
        toolBar.getItems().add(button);
        stage.setScene(scene);
    }

   }
