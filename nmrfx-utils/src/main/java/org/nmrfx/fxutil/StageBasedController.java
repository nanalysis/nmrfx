package org.nmrfx.fxutil;

import javafx.stage.Stage;

/**
 * Serves to indicate controllers that need a Stage to be set.
 * This works in conjunction with our own FxmlLoader to set it directly after loading the controller class,
 * to avoid manipulating "incomplete" controller instances.
 */
public interface StageBasedController {
    void setStage(Stage stage);
}
