package org.nmrfx.analyst.gui.datasetbrowser;

import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.controlsfx.control.tableview2.TableView2;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.analyst.gui.utitlity.DoubleTableCell;
import org.nmrfx.utilities.DatasetSummary;

import java.util.List;
import java.util.Optional;


public class DatasetBrowserTableView extends TableView2<DatasetSummary> {
    private static final int NUMBER_DECIMAL_PLACES_FREQUENCY = 1;
    /* Keeps track of the summaries, new summaries are added to this list. */
    private final ObservableList<DatasetSummary> unfilteredDatasetSummaries = FXCollections.observableArrayList();
    private Runnable datasetSelectionListener = null;
    FilteredList<DatasetSummary> filteredList;

    public DatasetBrowserTableView(boolean addCacheColumn) {
        TableColumn<DatasetSummary, String> pathCol = new TableColumn<>("FID");
        pathCol.setCellValueFactory(new PropertyValueFactory<>("Path"));
        pathCol.setMinWidth(150);

        TableColumn<DatasetSummary, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("Time"));
        dateCol.setPrefWidth(150);

        TableColumn<DatasetSummary, Boolean> presentCol = new TableColumn<>("InCache");
        presentCol.setCellValueFactory(new PropertyValueFactory<>("Present"));

        TableColumn<DatasetSummary, List<String>> processedCol = new TableColumn<>("Dataset");
        processedCol.setCellValueFactory(new PropertyValueFactory<>("Processed"));
        processedCol.setCellFactory(column -> new ProcessedDatasetComboBoxTableCell());
        processedCol.setPrefWidth(150);

        TableColumn<DatasetSummary, String> sequenceCol = new TableColumn<>("Sequence");
        sequenceCol.setCellValueFactory(new PropertyValueFactory<>("Seq"));
        sequenceCol.setPrefWidth(150);

        TableColumn<DatasetSummary, Integer> ndCol = new TableColumn<>("NDim");
        ndCol.setCellValueFactory(new PropertyValueFactory<>("nd"));
        ndCol.setPrefWidth(50);

        TableColumn<DatasetSummary, Double> sfCol = new TableColumn<>("Frequency");
        sfCol.setCellValueFactory(new PropertyValueFactory<>("sf"));
        sfCol.setCellFactory(column -> new DoubleTableCell<>(NUMBER_DECIMAL_PLACES_FREQUENCY));
        sfCol.setPrefWidth(70);

        getColumns().addAll(pathCol, dateCol);
        if (addCacheColumn) {
            getColumns().add(presentCol);
        }
        getColumns().addAll(processedCol, sequenceCol, ndCol, sfCol);
       // setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        filteredList = new FilteredList<>(unfilteredDatasetSummaries);
        SortedList<DatasetSummary> sortedData = new SortedList<>(filteredList);
        sortedData.comparatorProperty().bind(comparatorProperty());

        setItems(sortedData);
    }

    public void setDatasetSummaries(List<DatasetSummary> summaries) {
        unfilteredDatasetSummaries.setAll(summaries);
        // This is required as a fix for header columns misalignment when tableview has a scrollbar, is in a tab
        // and uses TableView.CONSTRAINED_RESIZE_POLICY
        Fx.runOnFxThread(() -> scrollTo(0));
    }

    /**
     * Creates a predicate from the string filter by comparing to path, time, user and seq columns and sets the
     * predicate of the filter.
     * @param filter The string filter
     */
    public void setFilter(String filter) {
        String textFormatted = filter.trim().toLowerCase();
        filteredList.setPredicate(datasetSummary -> textFormatted.isEmpty()
                || datasetSummary.getPath().toLowerCase().contains(textFormatted)
                || datasetSummary.getTime().toLowerCase().contains(textFormatted)
                || datasetSummary.getSeq().toLowerCase().contains(textFormatted)
                || datasetSummary.getProcessed().stream().anyMatch(datasetPath -> datasetPath.toLowerCase().contains(textFormatted)));
    }

    private class ProcessedDatasetComboBoxTableCell extends TableCell<DatasetSummary, List<String>> {
        private final ComboBox<String> combo = new ComboBox<>();

        ProcessedDatasetComboBoxTableCell() {
            combo.prefWidthProperty().bind(this.widthProperty());
            // Select the row when the combo box is clicked so it has same behaviour as just clicking elsewhere along the row
            combo.setOnMouseClicked(event -> getTableView().getSelectionModel().select(getIndex()));
            combo.valueProperty().addListener(this::comboValueChangeListener);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        private void comboValueChangeListener(ObservableValue<? extends String> observable, String oldValue, String newValue) {
            DatasetSummary datasetSummary = getTableRow().getItem();
            if (datasetSummary == null || newValue == null) {
                return;
            }
            datasetSummary.setSelectedProcessedDataIndex(datasetSummary.getProcessed().indexOf(newValue));
        }

        @Override
        protected void updateItem(List<String> items, boolean empty) {
            super.updateItem(items, empty);
            if (empty || items.isEmpty()) {
                setGraphic(null);
            } else {
                combo.getItems().setAll(items);
                DatasetSummary summary = getTableRow().getItem();
                if (summary != null) {
                    Optional<String> selectedProcessedData = summary.getSelectedProcessedData();
                    selectedProcessedData.ifPresent(combo::setValue);
                }
                setGraphic(combo);
            }
        }
    }
}
