package org.nmrfx.plugin.api;

import javafx.scene.control.Menu;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * A plugin definition.
 */
public interface NMRFxPlugin {
    default String getName() {
        return getClass().getSimpleName();
    }

    default String getVersion() {
        return "undefined";
    }

    /**
     * NMRfx defines several entry points where plugins can be injected.
     * This allows the plugin tell NMRfx where it can be called.
     * @return a set of supported entry points.
     */
    Set<EntryPoint> getSupportedEntryPoints();

    /**
     * Called by NMRfx to let a plugin integrates itself on a supported entry point.
     *
     * @param entryPoint the type of entry point
     * @param object the actual entry point. Its type will depend, and could be a Menu, a Scene, ...
     */
    void registerOnEntryPoint(EntryPoint entryPoint, Object object);
}
