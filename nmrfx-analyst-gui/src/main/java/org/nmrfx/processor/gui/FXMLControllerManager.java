package org.nmrfx.processor.gui;

import javafx.beans.property.SimpleObjectProperty;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages instances of FXMLControllers
 */
public class FXMLControllerManager {
    private static FXMLControllerManager instance;

    public static FXMLControllerManager getInstance() {
        //XXX keep as singleton, or move to AnalystApp ?
        if (instance == null) {
            instance = new FXMLControllerManager();
        }

        return instance;
    }

    private final SimpleObjectProperty<FXMLController> activeController = new SimpleObjectProperty<>(null);
    private final List<FXMLController> controllers = new ArrayList<>(); //XXX can we use a set instead?

    public List<FXMLController> getControllers() {
        //XXX see if we could make it unmodifiable here
        return controllers;
    }

    @Nonnull
    public FXMLController getOrCreateActiveController() {
        FXMLController active = activeController.get();
        if (active == null) {
            active = FXMLController.create();
            setActiveController(active);

            //XXX see if this call is necessary for its side effects
            active.setActiveController();
        }

        return active;
    }

    @Nullable
    public FXMLController getActiveController() {
        return activeController.get();
    }

    public SimpleObjectProperty<FXMLController> activeControllerProperty() {
        return activeController;
    }

    public void register(FXMLController controller) {
        register(controller, false);
    }

    public void register(FXMLController controller, boolean active) {
        controllers.add(controller);
        if (active) {
            activeController.set(controller);
        }
    }

    public void unregister(FXMLController controller) {
        controllers.remove(controller);
    }

    public void setActiveController(FXMLController controller) {
        //XXX check or add in all controllers list?
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

}
