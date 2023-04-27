package org.nmrfx.analyst.gui.datastore;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import org.nmrfx.processor.gui.PreferencesController;
import org.nmrfx.utils.properties.BooleanOperationItem;
import org.nmrfx.utils.properties.TextOperationItem;

import static org.nmrfx.processor.gui.MainApp.preferencesController;

public class DatastorePrefs {
    private static final String CATEGORY = "Datastore";

    private static BooleanProperty enabled = null;

    public static Boolean isEnabled() {
        enabled = PreferencesController.getBoolean(enabled, "DATASTORE_ENABLED", false);
        return enabled.getValue();
    }

    public static void setEnabled(boolean value) {
        enabled.setValue(value);
        PreferencesController.setBoolean("DATASTORE_ENABLED", value);
    }

    private static StringProperty url = null;

    public static String getUrl() {
        url = PreferencesController.getString(url, "DATASTORE_URL", "http://localhost:8080");
        return url.getValue();
    }

    public static void setUrl(String name) {
        url.setValue(name);
        PreferencesController.setString("DATASTORE_URL", name);
    }

    private static StringProperty username = null;

    public static String getUsername() {
        username = PreferencesController.getString(username, "DATASTORE_USERNAME", "admin");
        return username.getValue();
    }

    public static void setUsername(String name) {
        username.setValue(name);
        PreferencesController.setString("DATASTORE_USERNAME", name);
    }

    private static StringProperty password = null;

    public static String getPassword() {
        password = PreferencesController.getString(password, "DATASTORE_PASSWORD", "password");
        return password.getValue();
    }

    public static void setPassword(String password) {
        DatastorePrefs.password.setValue(password);
        PreferencesController.setString("DATASTORE_PASSWORD", password);
    }

    public static void addPrefs() {
        preferencesController.getPrefSheet().getItems().addAll(
                new BooleanOperationItem((observable, old, value) -> setEnabled((Boolean) value), isEnabled(),
                        CATEGORY, "Enabled", "Enable datastore feature"),
                new TextOperationItem((observable, old, value) -> setUrl((String) value), getUrl(),
                        CATEGORY, "URL", "Datastore base URL"),
                new TextOperationItem((observable, old, value) -> setUsername((String) value), getUsername(),
                        CATEGORY, "User name", "Datastore API user"),
                new TextOperationItem((observable, old, value) -> setPassword((String) value), getPassword(),
                        CATEGORY, "Password", "Datastore API password")
        );
    }
}
