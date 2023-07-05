package org.nmrfx.analyst.gui.datasetbrowser;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.utilities.DatasetSummary;

import java.util.List;
import java.util.Optional;

public class DatasetBrowserTableView extends TableView<DatasetSummary> {
    /* Keeps track of the summaries, new summaries are added to this list. */
    private final ObservableList<DatasetSummary> unfilteredDatasetSummaries = FXCollections.observableArrayList();
    private Runnable datasetSelectionListener = null;

    public DatasetBrowserTableView(boolean addCacheColumn) {
        TableColumn<DatasetSummary, String> pathCol = new TableColumn<>("FID");
        pathCol.setCellValueFactory(new PropertyValueFactory<>("Path"));

        TableColumn<DatasetSummary, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("Time"));

        TableColumn<DatasetSummary, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(new PropertyValueFactory<>("User"));

        TableColumn<DatasetSummary, Boolean> presentCol = new TableColumn<>("InCache");
        presentCol.setCellValueFactory(new PropertyValueFactory<>("Present"));

        TableColumn<DatasetSummary, List<String>> processedCol = new TableColumn<>("Dataset");
        processedCol.setCellValueFactory(new PropertyValueFactory<>("Processed"));
        processedCol.setCellFactory(column -> new ProcessedDatasetComboBoxTableCell());

        TableColumn<DatasetSummary, String> sequenceCol = new TableColumn<>("Sequence");
        sequenceCol.setCellValueFactory(new PropertyValueFactory<>("Seq"));

        TableColumn<DatasetSummary, Integer> ndCol = new TableColumn<>("NDim");
        ndCol.setCellValueFactory(new PropertyValueFactory<>("nd"));

        TableColumn<DatasetSummary, Double> sfCol = new TableColumn<>("SF");
        sfCol.setCellValueFactory(new PropertyValueFactory<>("sf"));

        getColumns().addAll(pathCol, userCol, dateCol);
        if (addCacheColumn) {
            getColumns().add(presentCol);
        }
        getColumns().addAll(processedCol, sequenceCol, ndCol, sfCol);
        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        setItems(new FilteredList<>(unfilteredDatasetSummaries));
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
        ((FilteredList<DatasetSummary>) getItems()).setPredicate(datasetSummary -> textFormatted.isEmpty()
                || datasetSummary.getPath().toLowerCase().contains(textFormatted)
                || datasetSummary.getTime().toLowerCase().contains(textFormatted)
                || datasetSummary.getUser().toLowerCase().contains(textFormatted)
                || datasetSummary.getSeq().toLowerCase().contains(textFormatted));
    }

    public void setDatasetSelectionListener(Runnable runnable) {
        this.datasetSelectionListener = runnable;
    }

    private class ProcessedDatasetComboBoxTableCell extends TableCell<DatasetSummary, List<String>> {
        private final ComboBox<String> combo = new ComboBox<>();
        private final ChangeListener<String> comboValueChangeListener = this::comboValueChangeListener;

        ProcessedDatasetComboBoxTableCell() {
            combo.prefWidthProperty().bind(this.widthProperty());
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        private void comboValueChangeListener(ObservableValue<? extends String> observable, String oldValue, String newValue) {
            DatasetSummary datasetSummary = getTableRow().getItem();
            if (datasetSummary == null || newValue == null) {
                return;
            }
            datasetSummary.setSelectedProcessedDataIndex(datasetSummary.getProcessed().indexOf(newValue));
            if (datasetSelectionListener != null) {
                getTableView().getSelectionModel().select(getIndex());
                datasetSelectionListener.run();
            }
        }

        @Override
        protected void updateItem(List<String> items, boolean empty) {
            super.updateItem(items, empty);
            if (empty || items.isEmpty()) {
                setGraphic(null);
            } else {
                // Don't call the change listener when programmatically updating the combo box values
                combo.valueProperty().removeListener(comboValueChangeListener);
                combo.getItems().setAll(items);
                DatasetSummary summary = getTableRow().getItem();
                if (summary != null) {
                    Optional<String> selectedProcessedData = summary.getSelectedProcessedData();
                    selectedProcessedData.ifPresent(combo::setValue);
                }
                setGraphic(combo);
                combo.valueProperty().addListener(comboValueChangeListener);
            }
        }
    }
}
