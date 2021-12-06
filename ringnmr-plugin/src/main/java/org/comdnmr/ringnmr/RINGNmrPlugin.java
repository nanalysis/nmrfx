package org.comdnmr.ringnmr;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.comdnmr.gui.PyController;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;

import java.util.Set;

public class RINGNmrPlugin implements NMRFxPlugin {
    public static PyController ringNMRController;

    @Override
    public Set<EntryPoint> getSupportedEntryPoints() {
        return Set.of(EntryPoint.MENU_PLUGINS);
    }

    @Override
    public void registerOnEntryPoint(EntryPoint entryPoint, Object object) {
        if(entryPoint != EntryPoint.MENU_PLUGINS) {
            throw new IllegalArgumentException("Only " + EntryPoint.MENU_PLUGINS + " is supported by RingNMR");
        }
        if(!(object instanceof Menu)) {
            throw new IllegalArgumentException("Expected a menu, but received " + (object == null ? "null" : object.getClass().getName()) + " instead");
        }

        Menu menu = (Menu) object;
        menu.getItems().add(createDynamicsMenu());
    }

    private Menu createDynamicsMenu() {
        Menu dynamicsMenu = new Menu("Dynamics");
        MenuItem ringNMRMenuItem = new MenuItem("Show RINGNMRGui");
        ringNMRMenuItem.setOnAction(e -> showRING());
        dynamicsMenu.getItems().addAll(ringNMRMenuItem);
        return dynamicsMenu;
    }

    private void showRING() {
        if (ringNMRController == null) {
            Stage stage = new Stage(StageStyle.DECORATED);
            ringNMRController = PyController.create(stage);
        }
        Stage stage = ringNMRController.getStage();
        stage.toFront();
        stage.show();
    }
}
