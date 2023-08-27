package org.nmrfx.analyst.gui.datasetbrowser;

import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import org.nmrfx.utilities.DatasetSummary;
import javafx.scene.layout.*;


public abstract class DatasetBrowserTabController {
    private final Tab tab;
    protected final BorderPane borderPane = new BorderPane();
    protected final VBox vBox = new VBox();
    private final ToolBar toolBar = new ToolBar();
    protected final HBox hBox = new HBox();
    protected final Region toolbarSpacer = new Region();
    protected final TextField filterTextField = new TextField();
    protected final TextField directoryTextField = new TextField();
    protected DatasetBrowserTableView tableView;

    protected DatasetBrowserTabController(String tabName) {
        tab = new Tab(tabName, borderPane);
        hBox.getChildren().add(directoryTextField);
        vBox.getChildren().addAll(toolBar, hBox);
        borderPane.setTop(vBox);
        borderPane.setCenter(tableView);
        HBox.setHgrow(directoryTextField, Priority.ALWAYS);
        directoryTextField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                updatePreferences();
            }
        });
        initToolbar();
    }

    private void initToolbar() {
        Button retrieveIndexButton = new Button("Index");
        retrieveIndexButton.setOnAction(e -> retrieveIndex());
        HBox.setHgrow(toolbarSpacer, Priority.ALWAYS);
        filterTextField.textProperty().addListener((observable, oldValue, newValue) -> filterChanged());
        filterTextField.setPrefWidth(200);
        toolBar.getItems().addAll(retrieveIndexButton, toolbarSpacer, filterTextField);
    }

    /**
     * Add button to toolbar in the left grouping of buttons.
     */
    protected void addToolbarButton(Button button) {
        int spacerIndex = toolBar.getItems().indexOf(toolbarSpacer);
        toolBar.getItems().add(spacerIndex, button);
    }

    protected void setTableView(DatasetBrowserTableView tableView) {
        this.tableView = tableView;
        borderPane.setCenter(tableView);
        tableView.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown() && event.getClickCount() == 2) {
                DatasetSummary datasetSummary = tableView.getSelectionModel().getSelectedItem();
                if (datasetSummary != null) {
                    // always try to open fid if control is down
                    openFile(event.isControlDown() || datasetSummary.getProcessed().isEmpty());
                }
            }
        });
    }

    /**
     * Retrieves a list of the data files present in the directory specified by the directoryTextField and displays
     * the list in the tableview.
     */
    protected abstract void retrieveIndex();

    /**
     * Opens the selected file.
     * @param isFid Whether to open as FID or not
     */
    protected abstract void openFile(boolean isFid);

    protected abstract void loadIndex();

    protected abstract void updatePreferences();

    public Tab getTab() {
        return tab;
    }

    protected void filterChanged() {
        if (tableView != null) {
            tableView.setFilter(filterTextField.getText());
        }
    }
}
