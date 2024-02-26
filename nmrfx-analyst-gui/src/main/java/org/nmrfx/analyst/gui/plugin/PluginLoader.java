package org.nmrfx.analyst.gui.plugin;

import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.plugin.api.NMRFxPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Loads and manages plugins, using java's ServiceLoader (SPI) mechanism.
 */
public class PluginLoader {
    private static final Logger log = LoggerFactory.getLogger(PluginLoader.class);
    private static final PluginLoader instance = new PluginLoader();

    /**
     * Get the single instance.
     *
     * @return the plugin loader instance.
     */
    public static PluginLoader getInstance() {
        return instance;
    }

    private final List<NMRFxPlugin> plugins;

    private PluginLoader() {
        this.plugins = ServiceLoader.load(NMRFxPlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toList());

        String pluginNames = plugins.stream().map(NMRFxPlugin::getName).collect(Collectors.joining(", "));
        log.info("Loaded plugins: {}", pluginNames);
    }

    /**
     * Get all known plugins.
     *
     * @return a collection of plugins.
     */
    public Collection<NMRFxPlugin> getPlugins() {
        return plugins;
    }

    /**
     * Let all plugins that support a specific entrypoint register themselves
     *
     * @param entryPoint the type of entry point
     * @param object     the actual entry point. Its type will depend, and could be a Menu, a Scene, ...
     */
    public void registerPluginsOnEntryPoint(EntryPoint entryPoint, Object object) {
        plugins.stream()
                .filter(plugin -> plugin.getSupportedEntryPoints().contains(entryPoint))
                .forEach(plugin -> plugin.registerOnEntryPoint(entryPoint, object));
    }
}
