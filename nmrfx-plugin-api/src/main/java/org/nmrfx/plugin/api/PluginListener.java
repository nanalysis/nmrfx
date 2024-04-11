package org.nmrfx.plugin.api;

import java.util.function.Function;

public record PluginListener(Object guiObject, Function<String, String> pluginFunction) {
}
