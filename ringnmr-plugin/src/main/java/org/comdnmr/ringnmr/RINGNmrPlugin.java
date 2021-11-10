package org.comdnmr.ringnmr;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.comdnmr.gui.PyController;
import org.nmrfx.plugin.api.NMRFxPlugin;

import java.util.Collection;
import java.util.List;

public class RINGNmrPlugin implements NMRFxPlugin {
    public static PyController ringNMRController;

    @Override
    public Collection<Menu> getMenus() {
        return List.of(createMenu());
    }

    private Menu createMenu() {
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
