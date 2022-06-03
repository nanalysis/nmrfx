package org.nmrfx.processor.gui.log;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.ChoiceBoxTableCell;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;


public class ChangeLogLevelController implements Initializable {

    private static final String TITLE = "Change log level...";
    private Stage stage;
    @FXML
    private TableView<Map.Entry<String, LogLevel>> changeLogLevelTable;

    /**
     * Creates a new ChangeLogLevelController
     * @param parentStage The parent stage of the new ChangeLogLevelController
     * @return ChangeLogLevelController
     */
    public static ChangeLogLevelController create(Stage parentStage) {
        FXMLLoader loader = new FXMLLoader(ChangeLogLevelController.class.getResource("/fxml/ChangeLogLevelScene.fxml"));
        Stage stage = new Stage(StageStyle.DECORATED);
        ChangeLogLevelController controller;
        try {
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);
            controller = loader.getController();
            controller.stage = stage;
            stage.setTitle(TITLE);
            stage.setResizable(false);
            stage.initOwner(parentStage);
            stage.initModality(Modality.WINDOW_MODAL);
            // Center the stage on the parent once its rendered
            stage.widthProperty().addListener(((observable, oldValue, newValue) -> stage.setX(parentStage.getX() + (parentStage.getWidth() - newValue.doubleValue()) / 2)));
            stage.heightProperty().addListener(((observable, oldValue, newValue) -> stage.setY(parentStage.getY() + (parentStage.getHeight() - newValue.doubleValue()) / 2)));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create the Change Log Level controller.", e);
        }
        return controller;
    }

    /**
     * Sets up the table view.
     *
     * @param location  The location used to resolve relative paths for the root object, or
     *                  {@code null} if the location is not known.
     * @param resources The resources used to localize the root object, or {@code null} if
     *                  the root object was not localized.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        changeLogLevelTable.setEditable(true);
        changeLogLevelTable.setPlaceholder(new Label("No custom log levels are set."));

        TableColumn<Map.Entry<String, LogLevel>, LogLevel> logLevelCol = new TableColumn<>("Log Level");
        logLevelCol.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getValue()));
        logLevelCol.setCellFactory(ChoiceBoxTableCell.forTableColumn(
                Arrays.stream(LogLevel.values()).filter(level -> level != LogLevel.ALL).toArray(LogLevel[]::new)));
        logLevelCol.setEditable(true);
        logLevelCol.setMinWidth(90);
        logLevelCol.setMaxWidth(90);
        logLevelCol.setOnEditCommit(this::logLevelChanged);
        changeLogLevelTable.getColumns().add(logLevelCol);

        TableColumn<Map.Entry<String, LogLevel>, String> loggerCol = new TableColumn<>("Logger");
        loggerCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getKey()));
        changeLogLevelTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        loggerCol.prefWidthProperty().bind(changeLogLevelTable.widthProperty().subtract(logLevelCol.widthProperty().get()));
        changeLogLevelTable.getColumns().add(loggerCol);

        changeLogLevelTable.setItems(FXCollections.observableArrayList(Log.getModifiedLogLevels().entrySet()));
    }

    public void showAndWait() {
        stage.showAndWait();
    }

    /**
     * Listener for a cell edit event in the Log Level column that sets the new LogLevel.
     * @param event
     */
    private void logLevelChanged(TableColumn.CellEditEvent<Map.Entry<String, LogLevel>, LogLevel> event) {
        setLogLevel(event.getRowValue().getKey(), event.getNewValue());
    }

    @FXML
    private void closeButtonClicked() {
        stage.close();
    }

    /**
     * Creates a text input prompt and sets the log level to DEBUG for valid string
     * inputs (does not contain only whitespace).
     */
    @FXML
    private void addButtonClicked() {
        TextInputDialog loggerNamePrompt = new TextInputDialog();
        loggerNamePrompt.initOwner(stage);
        loggerNamePrompt.setTitle("Customize logger...");
        loggerNamePrompt.setHeaderText("Enter a package or class name: ");
        Optional<String> input = loggerNamePrompt.showAndWait();
        if (input.isPresent()) {
            String loggerName = input.get().trim();
            if (loggerName.length() > 0) {
                setLogLevel(loggerName, LogLevel.DEBUG);
            }
        }
    }

    /**
     * Sets the log level and refreshes the custom loggers in the table.
     * @param loggerName The name of the logger to set the level for.
     * @param newLevel The new LogLevel to use.
     */
    private void setLogLevel(String loggerName, LogLevel newLevel) {
        Log.setLogLevel(loggerName, newLevel);
        changeLogLevelTable.getItems().clear();
        changeLogLevelTable.setItems(FXCollections.observableArrayList(Log.getModifiedLogLevels().entrySet()));
    }
}