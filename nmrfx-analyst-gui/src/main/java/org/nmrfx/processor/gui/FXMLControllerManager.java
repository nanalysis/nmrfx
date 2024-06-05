package org.nmrfx.processor.gui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.fxutil.Fxml;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Manages instances of FXMLControllers, from creation to listing them and keeping a way to know which one is the active one.
 */
public class FXMLControllerManager {
    // The active controller is the last one created, or the one that has focus
    private static int nControllers = 0;
    private final SimpleObjectProperty<FXMLController> activeController = new SimpleObjectProperty<>(null);

    // A collection of all known controllers.
    // It is important to keep insertion order: the first controller is considered the main one and is kept when closing all secondary stages
    private final Set<FXMLController> controllers = new LinkedHashSet<>();

    /**
     * Get all registered controllers, keeping the insertion order.
     *
     * @return an unmodifiable collection of registered controllers.
     */
    public Collection<FXMLController> getControllers() {
        return Collections.unmodifiableSet(controllers);
    }

    /**
     * Register a new controller and mark it as active.
     *
     * @param controller the controller to register
     */
    private void registerNewController(FXMLController controller) {
        controllers.add(controller);
        activeController.set(controller);
    }

    /**
     * Unregister a controller. To be called when the associated stage is closed.
     *
     * @param controller the controller to unregister.
     */
    public void unregister(FXMLController controller) {
        controllers.remove(controller);
    }

    /**
     * Check if a particular controller is already registered.
     *
     * @return true if the controller is known.
     */
    public boolean isRegistered(@Nullable FXMLController controller) {
        return controller != null && controllers.contains(controller);
    }

    /**
     * Get the active controller, creating one if necessary.
     *
     * @return the active controller.
     */
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

    /**
     * Exposes the active controller as a property, so other components can use it as an event source.
     *
     * @return the active controller property.
     */
    public SimpleObjectProperty<FXMLController> activeControllerProperty() {
        return activeController;
    }

    /**
     * Set the active controller or null.
     *
     * @param active the active controller.
     * @throws IllegalStateException if the controller is not registered
     */
    public void setActiveController(@Nullable FXMLController active) {
        if (active != null && !controllers.contains(active)) {
            throw new IllegalStateException("Trying to set an unregistered controller as the active one!");
        }

        activeController.set(active);

        if (active != null) {
            active.refreshAttributes();
        }
    }

    /**
     * Create a new controller with a default stage.
     *
     * @return the newly created controller.
     */
    public FXMLController newController(Stage stage) {
        return newController(stage, null);
    }

    public FXMLController newController() {
        return newController(new Stage(StageStyle.DECORATED), null);
    }

    public FXMLController newController(String title) {
        return newController(new Stage(StageStyle.DECORATED), title);
    }


    /**
     * Create a new controller with a defined stage.
     *
     * @param stage the stage used to display the scene associated with this controller
     * @return the newly created controller.
     */
    public FXMLController newController(Stage stage, String title) {
        nControllers++;
        if (title == null) {
            title = "NMRFx Spectra " + nControllers;
        }
        FXMLController controller = Fxml.load(FXMLControllerManager.class, "NMRScene.fxml")
                .withStage(stage)
                .getController();
        registerNewController(controller);

        AnalystApp.setStageFontSize(stage, AnalystApp.REG_FONT_SIZE_STR);
        AnalystApp.registerStage(stage);

        stage.focusedProperty().addListener(observable -> focusChanged(controller));
        stage.setOnCloseRequest(e -> {
            closeController(controller);
            AnalystApp.removeStage(stage);
        });
        controller.initStageGeometry();
        stage.setTitle(title);
        stage.show();

        return controller;
    }

    /**
     * Close and unregister a controller. The active controller will be replaced (even if the closed one wasn't the active one).
     *
     * @param controller the controller to close.
     */
    public void closeController(FXMLController controller) {
        controller.close();
        unregister(controller);
        setActiveControllerFromChart();
    }

    private void setActiveControllerFromChart() {
        PolyChart activeChart = PolyChartManager.getInstance().getActiveChart();
        if (activeChart == null) {
            activeChart = PolyChartManager.getInstance().getFirstChart();
        }

        if (activeChart == null || activeChart.getFXMLController() == null) {
            setActiveController(null);
        } else {
            setActiveController(activeChart.getFXMLController());
            activeChart.getFXMLController().setActiveChart(activeChart);
        }
    }

    private void focusChanged(FXMLController controller) {
        if (controller.getStage().isFocused()) {
            setActiveController(controller);
        }
    }

    public  void controllerTest(int nControllers, double delay) {

        Timeline timeline = new Timeline();
        timeline.setCycleCount(Animation.INDEFINITE);
        final Integer[] timeSeconds = {nControllers};
        final FXMLController[] fxmlControllers = new FXMLController[1];
        // KeyFrame event handler
        timeline.getKeyFrames().add(
                new KeyFrame(Duration.seconds(delay),
                        (EventHandler) event -> {
                            timeSeconds[0]--;
                            if (timeSeconds[0] <= 0) {
                                timeline.stop();
                            }
                            if (fxmlControllers[0] != null) {
                                Stage stage = fxmlControllers[0].getStage();
                                closeController(fxmlControllers[0]);
                                stage.close();
                                AnalystApp.removeStage(stage);
                            }
                            fxmlControllers[0] = newController();
                        }));
        timeline.playFromStart();
    }
}
