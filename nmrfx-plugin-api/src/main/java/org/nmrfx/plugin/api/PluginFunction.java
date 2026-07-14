package org.nmrfx.plugin.api;

import java.util.function.Function;

public record PluginFunction(Object guiObject, Function<String, Object> pluginFunction) {
}
