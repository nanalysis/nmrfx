package org.nmrfx.analyst.gui;

import javafx.scene.control.Menu;

public abstract class MenuActions {
    protected AnalystApp app;
    protected Menu menu;
    protected boolean advancedActive = false;

    public MenuActions(AnalystApp app, Menu menu) {
        this.app = app;
        this.menu = menu;
    }

    public abstract void basic();

    protected  void advanced() {

    }

    public void activateAdvanced() {
        if (!advancedActive) {
            advanced();
            advancedActive = true;
        }
    }
}
