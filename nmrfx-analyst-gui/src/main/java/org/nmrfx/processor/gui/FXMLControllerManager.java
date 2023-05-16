package org.nmrfx.processor.gui;

import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.analyst.gui.AnalystApp;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Manages instances of FXMLControllers, from creation to listing them and keeping a way to know which one is the active one.
 */
public class FXMLControllerManager {
    // The active controller is the last one created, or the one that has focus
    private final SimpleObjectProperty<FXMLController> activeController = new SimpleObjectProperty<>(null);

    // A collection of all known controllers.
    // It is important to keep insertion order: the first controller is considered the main one and is kept when closing all secondary stages
    private final Set<FXMLController> controllers = new LinkedHashSet<>();

    public Collection<FXMLController> getControllers() {
        return Collections.unmodifiableSet(controllers);
    }

    public void register(FXMLController controller, boolean setAsActive) {
        controllers.add(controller);
        if (setAsActive) {
            activeController.set(controller);
        }
    }

    public void unregister(FXMLController controller) {
        controllers.remove(controller);
    }

    public boolean isRegistered(FXMLController controller) {
        return controller != null && controllers.contains(controller);
    }

    @Nonnull
    public FXMLController getOrCreateActiveController() {
        FXMLController active = activeController.get();
        if (active == null) {
            //XXX is there actually a case where not having an active controller is a possibility?
            active = newController();

            //XXX in which case could we not already be the active controller here?
            //XXX it looks like creating a controller already sets it as active
            setActiveController(active);
        }

        return active;
    }

    public SimpleObjectProperty<FXMLController> activeControllerProperty() {
        return activeController;
    }

    public void setActiveController(FXMLController controller) {
        if (!controllers.contains(controller)) {
            throw new IllegalStateException("Trying to set an unregistered controller as the active one!");
        }

        activeController.set(controller);
    }

    public void setActiveControllerFromChart(PolyChart chart) {
        if (chart == null || chart.getController() == null) {
            setActiveController(null);
        } else {
            setActiveController(chart.getController());
            chart.getController().setActiveChart(chart);
        }
    }

    public FXMLController newController() {
        return newController(new Stage(StageStyle.DECORATED));
    }

    public FXMLController newController(Stage stage) {
        FXMLLoader loader = new FXMLLoader(FXMLController.class.getResource("/fxml/NMRScene.fxml"));
        FXMLController controller;


        try {
            //XXX this could be simplified
            Parent parent = loader.load();
            Scene scene = new Scene(parent);
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");
            AnalystApp.setStageFontSize(stage, AnalystApp.REG_FONT_SIZE_STR);
            controller = loader.getController();
            controller.initAfterFxmlLoader(stage);


            AnalystApp.registerStage(stage, controller);
            stage.show();
        } catch (IOException ioE) {
            throw new IllegalStateException("Unable to create controller", ioE);
        }

        return controller;
    }
}
