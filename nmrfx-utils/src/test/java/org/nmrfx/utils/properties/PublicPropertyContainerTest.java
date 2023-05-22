package org.nmrfx.utils.properties;

import javafx.beans.property.*;
import javafx.scene.paint.Color;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class PublicPropertyContainerTest {
    private static class DummyContainer implements PublicPropertyContainer {
        private final BooleanProperty bool = new SimpleBooleanProperty(this, "bool", true);
        private final IntegerProperty integer = new SimpleIntegerProperty(this, "integer", 42);
        private final StringProperty string = new SimpleStringProperty(this, "string", "initial");
        private final ColorProperty color = new ColorProperty(this, "color", Color.BLACK);

        @Override
        public Collection<Property<?>> getPublicProperties() {
            return Set.of(bool, integer, string, color);
        }
    }

    @Test
    public void setUnknownProperty() {
        var container = new DummyContainer();
        container.setPublicPropertyValue("unknown", "ignored");
        assertTrue("no exception was thrown", true);
    }

    @Test(expected = ClassCastException.class)
    public void setInvalidBoolean() {
        var container = new DummyContainer();
        container.setPublicPropertyValue("bool", "not a boolean");
    }

    @Test(expected = ClassCastException.class)
    public void setInvalidInteger() {
        var container = new DummyContainer();
        container.setPublicPropertyValue("integer", "not an int");
    }

    @Test(expected = ClassCastException.class)
    public void setInvalidString() {
        var container = new DummyContainer();
        container.setPublicPropertyValue("string", false);
    }

    @Test
    public void setValidValues() {
        var container = new DummyContainer();
        container.setPublicPropertyValue("bool", false);
        container.setPublicPropertyValue("integer", 1234);
        container.setPublicPropertyValue("string", "success");
        container.setPublicPropertyValue("color", Color.RED);

        assertFalse(container.bool.get());
        assertEquals(1234, container.integer.get());
        assertEquals("success", container.string.get());
        assertEquals(Color.RED, container.color.get());
    }

    @Test
    public void getCurrentValuesColor() {
        var container = new DummyContainer();
        var expected = Map.of("bool", true, "integer", 42, "string", "initial", "color", ColorProperty.toRGBCode(Color.BLACK));
        assertEquals(expected, container.getPublicPropertiesValues());
    }

    @Test
    public void nullColorsIgnoredInPropertiesValues() {
        var container = new DummyContainer();
        container.setPublicPropertyValue("color", null);
        assertNull("color is set to null", container.color.get());
        assertFalse("null colors are ignored", container.getPublicPropertiesValues().containsKey("color"));
    }
}