package org.nmrfx.analyst.gui.BMRB;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.ProjectMenuActions;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.utilities.BMRBSearchResult;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;

public class BMRBSearchController implements Initializable, StageBasedController {
    private static final Logger log = LoggerFactory.getLogger(BMRBSearchController.class);
    private Stage stage;
    @FXML
    private TextField searchField;
    @FXML
    private Button searchButton;
    @FXML
    private Button fetchStarButton;
    @FXML
    private Button goToButton;
    @FXML
    TableView<BMRBSearchResult> BMRBSearchTableView;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initTable();
        searchField.setMinWidth(400);
        searchField.setPromptText("Enter search term here");
        searchField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                doSearch();
            }
        });
        searchButton.setText("Search");
        searchButton.setOnMouseClicked(e -> doSearch());
        searchButton.setMinWidth(30);

        fetchStarButton.setText("Fetch STAR");
        fetchStarButton.setMinWidth(30);

        goToButton.setText("Go to Page");
        goToButton.setMinWidth(30);
    }

    @Override
    public void setStage(Stage stage) {this.stage = stage;}

    public Stage getStage() {return stage;}

    public static BMRBSearchController create() {
        BMRBSearchController controller = Fxml.load(BMRBSearchController.class, "BMRBScene.fxml")
                .withNewStage("Search BMRB")
                .getController();
        controller.stage.show();

        return controller;
    }

    private void doSearch() {
        String text = searchField.getText();
        if (!text.isBlank()) {
            BMRBSearchTableView.getItems().clear();
            try {
                List<BMRBSearchResult> results = BMRBSearchResult.getSearchResults(text);
                if (results == null) {
                    Fx.runOnFxThread(() ->
                            GUIUtils.warn("Search BMRB", "No results found")
                    );
                    return;
                }
                BMRBSearchTableView.getItems().addAll(results);
                BMRBSearchTableView.refresh();
            } catch (ExecutionException | InterruptedException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
        }
    }

    public void initTable() {
        TableColumn<BMRBSearchResult, String> entryIDCol = new TableColumn<>("Entry ID");
        entryIDCol.setCellValueFactory(new PropertyValueFactory<>("entryID"));

        TableColumn<BMRBSearchResult, String> releaseDateCol = new TableColumn<>("Release Date");
        releaseDateCol.setCellValueFactory(new PropertyValueFactory<>("releaseDate"));

        TableColumn<BMRBSearchResult, String> dataSummaryCol = new TableColumn<>("Data Summary");
        dataSummaryCol.setCellValueFactory((TableColumn.CellDataFeatures<BMRBSearchResult, String> p) ->
        {
            BMRBSearchResult entry = p.getValue();
            List<HashMap> summary = entry.getDataSummary();
            StringBuilder str = new StringBuilder();
            if (summary != null) {
                for (HashMap<Object, Object> e : summary) {
                    String type = e.get("type") instanceof String s ? s : null;
                    String count = e.get("count") instanceof String s ? s : null;
                    if (type != null && count != null) {
                        str.append(type.replace("_", " ")).append(": ");
                        str.append(count);
                        if (Integer.parseInt(count) == 1) {
                            str.append(" set\n");
                        } else {
                            str.append(" sets\n");
                        }
                    }
                }
            }
            return new SimpleStringProperty(str.toString());
        });
        dataSummaryCol.setMinWidth(200);

        TableColumn<BMRBSearchResult, String> entryTitleCol = new TableColumn<>("Entry Title");
        entryTitleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        entryTitleCol.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BMRBSearchResult, String> call(TableColumn<BMRBSearchResult, String> param) {
                final TableCell<BMRBSearchResult, String> cell = new TableCell<>() {
                    private Text text;
                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (!isEmpty()) {
                            text = new Text(item.toString());
                            text.wrappingWidthProperty().bind(getTableColumn().widthProperty());
                            setGraphic(text);
                        }
                    }

                };
                return cell;
            }
        });
        entryTitleCol.setMinWidth(300);

        TableColumn<BMRBSearchResult, String> authorsCol = new TableColumn<>("Authors");
        authorsCol.setCellValueFactory((TableColumn.CellDataFeatures<BMRBSearchResult, String> p) ->
                {
                    BMRBSearchResult entry = p.getValue();
                    List<String> authors = entry.getAuthors();
                    String str = authors.toString().substring(1,authors.toString().length() - 1);
                    return new SimpleStringProperty(str);
                });
        authorsCol.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BMRBSearchResult, String> call(TableColumn<BMRBSearchResult, String> param) {
                final TableCell<BMRBSearchResult, String> cell = new TableCell<>() {
                    private Text text;
                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (!isEmpty()) {
                            text = new Text(item.toString());
                            text.wrappingWidthProperty().bind(getTableColumn().widthProperty());
                            setGraphic(text);
                        }
                    }

                };
                return cell;
            }
        });
        authorsCol.setMinWidth(300);

        BMRBSearchTableView.getColumns().addAll(entryIDCol, releaseDateCol, dataSummaryCol, entryTitleCol, authorsCol);
        BMRBSearchTableView.getSelectionModel().selectedItemProperty().addListener(
                (observable -> selectSearchRow())
        );
    }

    public void selectSearchRow() {
        try {
            var selected = BMRBSearchTableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            int entryID = Integer.valueOf(selected.getEntryID());
            System.out.println(entryID);
        } catch (Exception ex) {
            ExceptionDialog dialog = new ExceptionDialog(ex);
            dialog.showAndWait();
        }
    }

}
