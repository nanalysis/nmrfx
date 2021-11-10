package org.nmrfx.plugin.api;

import javafx.scene.control.Menu;

import java.util.Collection;
import java.util.Collections;

public interface NMRFxPlugin {
    default String getName() {
        return getClass().getSimpleName();
    }

    default String getVersion() {
        return "undefined";
    }

    default Collection<Menu> getMenus() {
        return Collections.emptyList();
    }
}
