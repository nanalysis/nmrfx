package org.nmrfx.example.plugin;

import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.nmrfx.analyst.gui.molecule3D.MolSceneController;
import org.nmrfx.analyst.gui.molecule3D.MolViewer;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;

import java.util.Set;

public class NMRFxMoleculeViewerExamplePlugin implements NMRFxPlugin {
    MolSceneController molSceneController;
    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {

        return Set.of(
                EntryPoint.MENU_MOLECULE_VIEWER
        );
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        switch (entryPoint) {
            case MENU_MOLECULE_VIEWER -> addToMoleculeMenu(object);
            case null, default ->
                    throw new IllegalArgumentException("Only " + EntryPoint.MENU_PLUGINS + " is supported by this plugin " + entryPoint);
        }
    }

      private void addToMoleculeMenu(Object object) {
        if (object instanceof MolSceneController controller) {
            this.molSceneController = controller;
            HBox hBox = molSceneController.mol3dHBox;
            hBox.getChildren().add(createExampleMenuButton());
        } else {
            throw new IllegalArgumentException("Expected a menu, but received " + (object == null ? "null" : object.getClass().getName()) + " instead");
        }
    }

    private MenuButton createExampleMenuButton() {
        MenuButton exampleMenu = new MenuButton("Example");
        MenuItem exampleMenuItem = new MenuItem("Center on selection");
        exampleMenuItem.setOnAction(e -> centerOnSelection());
        exampleMenu.getItems().addAll(exampleMenuItem);
        return exampleMenu;
    }

    private void centerOnSelection() {
        MolViewer molViewer = molSceneController.getMolViewer();
        molViewer.centerOnSelection();
    }
}
