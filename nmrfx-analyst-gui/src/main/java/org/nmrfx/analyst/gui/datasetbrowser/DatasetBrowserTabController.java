package org.nmrfx.analyst.gui.datasetbrowser;

import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;


public abstract class DatasetBrowserTabController {
    private final Tab tab;
    protected final BorderPane borderPane = new BorderPane();
    protected final VBox vBox = new VBox();
    protected final ToolBar toolBar = new ToolBar();
    protected final HBox hBox = new HBox();
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
        Button fidButton = new Button("FID");
        fidButton.setOnAction(e -> openFile(true));
        Button datasetButton = new Button("Dataset");
        datasetButton.setOnAction(e -> openFile(false));

        toolBar.getItems().addAll(retrieveIndexButton, fidButton, datasetButton);
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

    public void setTableFilter(String filterText) {
        if (tableView != null) {
            tableView.setFilter(filterText);
        }
    }
}
