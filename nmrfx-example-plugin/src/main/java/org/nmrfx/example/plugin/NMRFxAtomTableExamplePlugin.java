package org.nmrfx.example.plugin;

import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import org.nmrfx.analyst.gui.molecule.AtomController;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;

import java.util.List;
import java.util.Set;

public class NMRFxAtomTableExamplePlugin implements NMRFxPlugin {
    AtomController atomController;
    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {

        return Set.of(
                EntryPoint.ATOM_MENU
        );
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        switch (entryPoint) {
            case ATOM_MENU -> addMenu(object);
            case null, default ->
                    throw new IllegalArgumentException("Only " + EntryPoint.MENU_PLUGINS + " is supported by this plugin " + entryPoint);
        }
    }

      private void addMenu(Object object) {
        if (object instanceof AtomController controller) {
            this.atomController = controller;
            ToolBar toolBar = atomController.getToolBar();
            toolBar.getItems().add(createExampleMenuButton());
        } else {
            throw new IllegalArgumentException("Expected a menu, but received " + (object == null ? "null" : object.getClass().getName()) + " instead");
        }
    }

    private MenuButton createExampleMenuButton() {
        MenuButton exampleMenu = new MenuButton("Example");
        MenuItem exampleMenuItem = new MenuItem("Atom action");
        exampleMenuItem.setOnAction(e -> analyzeAtoms());
        exampleMenu.getItems().addAll(exampleMenuItem);
        return exampleMenu;
    }

    private void analyzeAtoms() {
        List<Atom> atoms = atomController.getAtoms();
    }
}
