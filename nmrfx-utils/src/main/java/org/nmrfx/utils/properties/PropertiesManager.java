package org.nmrfx.utils.properties;

import javafx.beans.property.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

//TODO find better names for interface and especially for config(..) methods (if possible, maybe we can't for compatibility reasons?)
//TODO unit tests
public interface PropertiesManager {
    Logger log = LoggerFactory.getLogger(PropertiesManager.class);

    Collection<Property<?>> getPublicProperties();

    @SuppressWarnings("unchecked")
    default void config(String name, Object value) {
        for (var property : getPublicProperties()) {
            if (property.getName().equals(name)) {
                ((Property<Object>) property).setValue(value);
                return;
            }
        }

        log.warn("Trying to set {} to {} but property isn't publicly exposed", name, value);
    }

    default Map<String, Object> config() {
        Map<String, Object> map = new HashMap<>();

        for (var property : getPublicProperties()) {
            if (property instanceof ColorProperty colorProperty) {
                // special conversion for colors
                String colorName = colorProperty.getColorAsRGB();
                if (colorName != null) {
                    map.put(property.getName(), colorName);
                }
            } else {
                map.put(property.getName(), property.getValue());
            }
        }

        return map;
    }
}
