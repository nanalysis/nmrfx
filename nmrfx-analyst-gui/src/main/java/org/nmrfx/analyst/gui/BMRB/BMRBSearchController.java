package org.nmrfx.analyst.gui.BMRB;

import javafx.application.HostServices;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.converter.IntegerStringConverter;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.chemistry.io.NMRStarReader;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.star.BMRBio;
import org.nmrfx.star.ParseException;
import org.nmrfx.utilities.BMRBSearchResult;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

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
    private HostServices hostServices;

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
        fetchStarButton.setOnAction(this::getSelected);
        fetchStarButton.setMinWidth(30);

        goToButton.setText("Go to Page");
        goToButton.setOnAction(this::goToSite);
        goToButton.setMinWidth(30);

        hostServices = AnalystApp.getAnalystApp().getHostServices();
    }

    @Override
    public void setStage(Stage stage) {this.stage = stage;}

    public Stage getStage() {return stage;}

    public static BMRBSearchController create() {
        BMRBSearchController controller = Fxml.load(BMRBSearchController.class, "BMRBScene.fxml")
                .withNewStage("Search BMRB")
                .getController();
        controller.getStage().show();
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
    }

    public void getSelected(ActionEvent event) {
        var selected = BMRBSearchTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                int entryID = Integer.valueOf(selected.getEntryID());
                fetchStar(entryID);
            } catch (Exception ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
        }
    }

    private record fetchStarOptions(int entryID, boolean useRef, int ppmSet){}

    public static fetchStarOptions choosePPMSet(boolean promptForEntryID) {
        Dialog<fetchStarOptions> dialog = new Dialog<>();
        dialog.setTitle("Fetch BMRB");
        dialog.setHeaderText("Fetch BMRB:");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        dialog.getDialogPane().setContent(grid);
        int row = 0;
        TextField entryIDField = new TextField();
        grid.add(new Label("BMRB ID: "), 0, row);
        grid.add(entryIDField, 1, row);
        row++;
        if (!promptForEntryID) {
            entryIDField.setDisable(true);
        }
        CheckBox setRef = new CheckBox();
        grid.add(new Label("Use ref set: "), 0, row);
        grid.add(setRef, 1, row);
        row++;
        int comboBoxWidth = 60;
        ComboBox<Integer> comboBoxRows = new ComboBox<>(FXCollections.observableArrayList(IntStream.rangeClosed(0, 4).boxed().toList()));
        comboBoxRows.setValue(0);
        comboBoxRows.setMinWidth(comboBoxWidth);
        comboBoxRows.setMaxWidth(comboBoxWidth);
        comboBoxRows.setEditable(true);
        comboBoxRows.setConverter(new IntegerStringConverter());
        grid.add(new Label("Assign to set"), 0, row);
        grid.add(comboBoxRows, 1, row);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                // The value set in the formatter may not have been set yet so commit the value before retrieving
                comboBoxRows.commitValue();
                int entryID = 0;
                if (!entryIDField.isDisable()) {
                    try {
                        entryID = Integer.parseInt(entryIDField.getText());
                    } catch (Exception ex) {
                        GUIUtils.warn("Invalid Entry", "Numerical entries only");
                        return null;
                    }
                }
                return new fetchStarOptions(entryID, setRef.isSelected(),comboBoxRows.getValue());
            }
            return null;
        });
        fetchStarOptions options = null;
        Optional<fetchStarOptions> result = dialog.showAndWait();
        if (result.isPresent()) {
            options = result.get();
        }
        return options;
    }

    public static void fetchStar(int entryID) {
        boolean promptForEntryID = entryID == 0;
        fetchStarOptions options = choosePPMSet(promptForEntryID);
        if (options == null) {
            return;
        }
        boolean loadShiftsOnly = GUIProject.checkProjectActive(false);
        if (promptForEntryID) {
            entryID = options.entryID;
        }

        int ppmSet = options.useRef ? -options.ppmSet - 1 : options.ppmSet;

        CompletableFuture<HttpResponse<String>> futureResponse;
        try {
            futureResponse = BMRBio.fetchEntryASync(entryID);
        } catch (Exception e) {
            ExceptionDialog dialog = new ExceptionDialog(e);
            dialog.showAndWait();
            return;
        }

        futureResponse.thenApply(r -> {
            Fx.runOnFxThread(() -> {
                try {
                    if (r.statusCode() != 200) {
                        GUIUtils.warn("Invalid BMRB Entry", "Entry not found");
                        return;
                    }
                    if (!loadShiftsOnly) {
                        NMRStarReader.readFromString(r.body());
                    } else {
                        NMRStarReader.readChemicalShiftsFromString(r.body(), ppmSet);
                    }
                } catch (ParseException e) {
                    ExceptionDialog dialog = new ExceptionDialog(e);
                    dialog.showAndWait();
                }
            });
            return true;
        });
    }

    private void goToSite(ActionEvent event) {
        var selected = BMRBSearchTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            String entryID = selected.getEntryID();
            hostServices.showDocument("https://bmrb.io/data_library/summary/index.php?bmrbId=" + entryID);
        }
    }


}
