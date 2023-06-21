package org.nmrfx.analyst.gui.datasetbrowser;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import org.controlsfx.control.tableview2.TableView2;
import org.nmrfx.utilities.DatasetSummary;

import java.util.List;

public class DatasetBrowserTableView extends TableView2<DatasetSummary> {
    /* Keeps track of the summaries, new summaries are added to this list. */
    private final ObservableList<DatasetSummary> unfilteredDatasetSummaries = FXCollections.observableArrayList();
    /* Keeps track of the filtered view of unfilteredDatasetSummaries, new filters should be added to this list. */
    private final FilteredList<DatasetSummary> filteredDatasetSummaries = new FilteredList<>(unfilteredDatasetSummaries);

    public DatasetBrowserTableView(boolean addCacheColumn) {
        TableColumn<DatasetSummary, String> pathCol = new TableColumn<>("Path");
        pathCol.setCellValueFactory(new PropertyValueFactory<>("Path"));

        TableColumn<DatasetSummary, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("Time"));

        TableColumn<DatasetSummary, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(new PropertyValueFactory<>("User"));

        TableColumn<DatasetSummary, Boolean> presentCol = new TableColumn<>("InCache");
        presentCol.setCellValueFactory(new PropertyValueFactory<>("Present"));

        TableColumn<DatasetSummary, String> processedCol = new TableColumn<>("Dataset");
        processedCol.setCellValueFactory(new PropertyValueFactory<>("Processed"));

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
        setItems(filteredDatasetSummaries);
    }

    public void setDatasetSummaries(List<DatasetSummary> summaries) {
        unfilteredDatasetSummaries.clear();
        unfilteredDatasetSummaries.addAll(summaries);
    }

    /**
     * Creates a predicate from the string filter by comparing to path, time, user and seq columns and sets the
     * predicate of the filter.
     * @param filter The string filter
     */
    public void setFilter(String filter) {
        String textFormatted = filter.trim().toLowerCase();
        filteredDatasetSummaries.setPredicate(datasetSummary -> textFormatted.isEmpty()
                || datasetSummary.getPath().toLowerCase().contains(textFormatted)
                || datasetSummary.getTime().toLowerCase().contains(textFormatted)
                || datasetSummary.getUser().toLowerCase().contains(textFormatted)
                || datasetSummary.getSeq().toLowerCase().contains(textFormatted));
    }
}
