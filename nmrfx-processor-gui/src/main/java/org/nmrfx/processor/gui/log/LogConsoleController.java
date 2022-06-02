package org.nmrfx.processor.gui.log;

import ch.qos.logback.classic.Level;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.controlsfx.control.MasterDetailPane;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Controller for the Log Console.
 */
public class LogConsoleController implements Initializable {

    private static LogConsoleController logConsoleController = null;

    private static final String ANY_LEVEL = "-- LEVEL --";
    private static final String ANY_SECTION = "-- SECTION --";
    private static final String CONSOLE_TITLE = "Log Console";
    private LogListener logListener = null;
    private Stage stage;

    /* Choices for filtering by log level*/
    @FXML
    private ChoiceBox<String> logLevelChoice;
    /* Choices for filtering by sections */
    @FXML
    private ChoiceBox<String> sectionChoice;
    @FXML
    private TextField filterTextField;
    @FXML
    private MasterDetailPane logDisplayMasterDetail;

    private LogTable table;

    protected LogConsoleController() {}

    /**
     * Creates a new LogConsoleController and sets the static LogConsoleController.
     * @return The newly created LogConsoleController
     */
    public static LogConsoleController create() {
        FXMLLoader loader = new FXMLLoader(LogConsoleController.class.getResource("/fxml/LogConsoleScene.fxml"));
        loader.setControllerFactory(controller -> new LogConsoleController());
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);
            logConsoleController = loader.getController();
            logConsoleController.stage = stage;
            logConsoleController.logListener = logConsoleController::logPublished;
            stage.setTitle(CONSOLE_TITLE);
            // Only listen for new log messages if the log console is showing
            stage.showingProperty().addListener((observable, oldValue, newValue) -> {
                if (logConsoleController != null){
                    if (Boolean.TRUE.equals(newValue)) {
                        logConsoleController.addLogRecords();
                        Log.addLogListener(logConsoleController.logListener);
                    } else {
                        Log.removeLogListener(logConsoleController.logListener);
                    }
                }
            });

        } catch (IOException e) {
            throw new IllegalStateException("Unable to create the Log Console controller.", e);
        }
        return logConsoleController;
    }

    /**
     * Sets values in filter controls and adds listeners to changes.
     * @param location
     * The location used to resolve relative paths for the root object, or
     * {@code null} if the location is not known.
     *
     * @param resources
     * The resources used to localize the root object, or {@code null} if
     * the root object was not localized.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        table = new LogTable();
        LogDetailsView logDetails = new LogDetailsView();
        logLevelChoice.getItems().add(ANY_LEVEL);
        logLevelChoice.setValue(ANY_LEVEL);
        Arrays.stream(LogLevel.values())
                .filter(level -> level != LogLevel.OFF && level != LogLevel.ALL)
                .map(LogLevel::toString)
                .forEach(logLevelChoice.getItems()::add);
        logLevelChoice.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> filterChanged());

        sectionChoice.getItems().add(ANY_SECTION);
        sectionChoice.setValue(ANY_SECTION);
        Stream.of(LogSection.values()).forEach(section -> sectionChoice.getItems().add(section.getSectionName()));
        sectionChoice.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> filterChanged());

        logDisplayMasterDetail.setMasterNode(table);
        logDisplayMasterDetail.setDetailNode(logDetails);

        table.getSelectionModel().selectedItemProperty().addListener((o, oldRow, newRow) -> logDetails.setDetails(newRow));

        filterTextField.textProperty().addListener((observable, oldValue, newValue) -> filterChanged());
    }

    /**
     * Shows the stage and brings it to the front.
     */
    public void show() {
        stage.show();
        stage.toFront();
    }

    /**
     * Listener for changes in the logLevelChoice, sectionChoice and filterTextfield.
     * Updates the filter in the LogTable.
     */
    private void filterChanged() {
        int levelValue = getSelectedLevelIntValue();
        LogSection section = getSelectedSectionValue();
        String filterText = filterTextField.getText();
        table.setFilter(buildFilter(levelValue, section, filterText));
    }

    /**
     * Creates a filter for LogRecords from an integer logLevel value, LogSection and a search string inputs.
     * @return Predicate to be used to filter LogRecords.
     */
    protected Predicate<LogRecord> buildFilter(int levelValue, LogSection section, String filterText) {
        return logRecord -> {
            if (logRecord.getLevel().getLogbackLevel().toInteger() < levelValue)
                return false;

            if (section != null && section != LogSection.fromLoggerNameString(logRecord.getLoggerName()))
                return false;

            String textFormatted = filterText.trim().toLowerCase();
            return textFormatted.isEmpty()
                    || logRecord.getLoggerName().toLowerCase().contains(textFormatted)
                    || logRecord.getSourceMethodName().toLowerCase().contains(textFormatted)
                    || (logRecord.getMessage() != null && logRecord.getMessage().toLowerCase().contains(textFormatted));
        };
    }

    /**
     * Gets the selected choice from the sectionChoice and converts it to a LogSection.
     * @return The selected LogSection or null if ANY_SECTION is selected.
     */
    private LogSection getSelectedSectionValue() {
        String sectionName = sectionChoice.getSelectionModel().getSelectedItem();
        if(ANY_SECTION.equals(sectionName)) {
            return null;
        }
        return LogSection.fromLogSectionNameString(sectionName);
    }

    /**
     * Gets the selected level from the logLevelChoice and converts it to an integer.
     * @return The int value of the log level or the value of ALL_INT if ANY_LEVEL is selected.
     */
    private int getSelectedLevelIntValue() {
        String selectedLevel = logLevelChoice.getSelectionModel().getSelectedItem();
        if (ANY_LEVEL.equals(selectedLevel)) {
            return Level.ALL_INT;
        }
        return LogLevel.valueOf(selectedLevel).getLogbackLevel().toInteger();
    }

    /**
     * Listener for newly published LogRecords.
     * @param logRecord The new record to add.
     */
    private void logPublished(LogRecord logRecord) {
        int recordsSize = Log.getRecordsFromMemory().size();
        table.addLogRecord(logRecord, recordsSize);
    }

    /**
     * Sets all records in LogTable
     */
    private void addLogRecords() {
        table.setLogRecords(Log.getRecordsFromMemory());
    }

    /**
     * Clear current records from memory and resets all the records in the LogTable.
     * Note: This method is called from the fxml file.
     */
    @FXML
    private void clearButtonClicked() {
        Log.clearRecordsFromMemory();
        addLogRecords();
    }

    /**
     * If the static LogConsoleController has not been set, creates and sets a new
     * LogConsoleController before returning.
     * @return The static LogConsoleController object.
     */
    public static LogConsoleController getLogConsoleController() {
        if (logConsoleController == null) {
            logConsoleController = LogConsoleController.create();
        }
        return logConsoleController;
    }
}
