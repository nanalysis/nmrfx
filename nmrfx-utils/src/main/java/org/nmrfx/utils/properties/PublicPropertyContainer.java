package org.nmrfx.utils.properties;

import javafx.beans.property.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Declares as an object that expose public properties, to be configured by outside components.
 * This can be used to allow controlling colors, fonts, etc. by python scripts for example.
 * <br>
 * Sensible default implementation are provided, an implementor only needs to declare a collection of exposed properties.
 */
public interface PublicPropertyContainer {
    Logger log = LoggerFactory.getLogger(PublicPropertyContainer.class);

    /**
     * Defines which are the public properties that will be accessible publicly.
     * A component can have other private properties, those would not be available.
     *
     * @return all exposed properties.
     */
    Collection<Property<?>> getPublicProperties();

    /**
     * Set a property from its name and value.
     * If a property is unknown, it is ignored (and logged) but no error is thrown.
     * If a property value is invalid, a default value can be set, or an exception thrown, depending on the property implementation.
     *
     * @param name  the property name
     * @param value the new value
     */
    @SuppressWarnings("unchecked")
    default void setPublicPropertyValue(String name, Object value) {
        for (var property : getPublicProperties()) {
            if (property.getName().equals(name)) {
                ((Property<Object>) property).setValue(value);
                return;
            }
        }

        log.warn("Trying to set {} to {} but property isn't publicly exposed", name, value);
    }

    /**
     * Get a map of all public properties name and their current value.
     *
     * @return public properties and their values
     */
    default Map<String, Object> getPublicPropertiesValues() {
        // null colors will be ignored, while other null values will be returned in the map.
        // This is strange and likely an unwanted side effect, but it was already the case in previous implementations.

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
