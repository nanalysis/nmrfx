package org.nmrfx.example.plugin;

import javafx.scene.control.*;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;

import java.util.Set;

public class NMRFxFileMenuExamplePlugin implements NMRFxPlugin {

    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {

        return Set.of(
                EntryPoint.MENU_FILE
        );
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        switch (entryPoint) {
            case MENU_FILE -> addToMenu(object);
            case null, default ->
                    throw new IllegalArgumentException("Only " + EntryPoint.MENU_PLUGINS + " is supported by this plugin " + entryPoint);
        }
    }


    private void addToMenu(Object object) {
        if (object instanceof Menu menu) {
            menu.getItems().add(createExampleMenu());
        } else {
            throw new IllegalArgumentException("Expected a menu, but received " + (object == null ? "null" : object.getClass().getName()) + " instead");
        }
    }

    private Menu createExampleMenu() {
        Menu exampleMenu = new Menu("Example");
        MenuItem exampleMenuItem = new MenuItem("Do Example");
        exampleMenu.getItems().addAll(exampleMenuItem);
        return exampleMenu;
    }
}
