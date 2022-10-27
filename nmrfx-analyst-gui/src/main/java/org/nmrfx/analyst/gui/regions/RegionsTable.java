package org.nmrfx.analyst.gui.regions;

import javafx.beans.Observable;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import org.nmrfx.analyst.gui.tools.IntegralTool;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.processor.gui.PolyChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * A tableview for displaying DatasetRegions
 */
public class RegionsTable extends TableView<DatasetRegion> {
    private static final Logger log = LoggerFactory.getLogger(RegionsTable.class);
    private static final String REGION_START_COLUMN_NAME = "Start";
    private static final String REGION_END_COLUMN_NAME = "End";
    private static final String INTEGRAL_COLUMN_NAME = "Value";
    private static final String INTEGRAL_TYPE_COLUMN_NAME = "Type";
    private static final int NUMBER_DECIMAL_PLACES = 2;
    private PolyChart chart;
    private final ObservableList<DatasetRegion> unsortedRegions;
    TableColumn<DatasetRegion, Double> startPosCol;

    /**
     * Table cell formatter to format non-editable columns of doubles
     */
    private static class DoubleTableCell extends TableCell<DatasetRegion, Double> {
        String formatString;

        public DoubleTableCell() {
            formatString = "%." + NUMBER_DECIMAL_PLACES + "f";
        }

        @Override
        protected void updateItem(Double value, boolean empty) {
            super.updateItem(value, empty);
            if (empty) {
                setText(null);
            } else {
                setText(String.format(formatString, value));
            }
        }
    }

    /**
     * Formatter to change between Double and Strings in editable columns of Doubles
     */
    private static class DoubleColumnFormatter extends javafx.util.converter.DoubleStringConverter {
        @Override
        public String toString(Double object) {
            return String.format("%." + NUMBER_DECIMAL_PLACES + "f", object);
        }

        @Override
        public Double fromString(String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public RegionsTable() {
        setPlaceholder(new Label("No regions to display"));

        this.unsortedRegions = FXCollections.observableList(new ArrayList<>(), (DatasetRegion dr) -> new Observable[]{dr.getIntegralProperty()});
        SortedList<DatasetRegion> sortedRegions = new SortedList<>(this.unsortedRegions);
        sortedRegions.comparatorProperty().bind(comparatorProperty());
        unsortedRegions.addListener((ListChangeListener<DatasetRegion>) c -> {
            while(c.next()) {
                // Update the chart if a dataset region object has been modified
                if (c.wasUpdated()) {
                    refresh();
                }
            }
        });

        setEditable(true);
        startPosCol = new TableColumn<>(REGION_START_COLUMN_NAME);
        startPosCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getRegionStart(0)));
        startPosCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleColumnFormatter()));
        startPosCol.setEditable(true);
        startPosCol.setOnEditCommit(this::regionBoundChanged);
        getSortOrder().add(startPosCol);
        getColumns().add(startPosCol);

        TableColumn<DatasetRegion, Double> endPosCol = new TableColumn<>(REGION_END_COLUMN_NAME);
        endPosCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getRegionEnd(0)));
        endPosCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleColumnFormatter()));
        endPosCol.setEditable(true);
        endPosCol.setOnEditCommit(this::regionBoundChanged);
        getColumns().add(endPosCol);

        TableColumn<DatasetRegion, Double> integralCol = new TableColumn<>(INTEGRAL_COLUMN_NAME);
        integralCol.setCellValueFactory(new PropertyValueFactory<>("integral"));
        integralCol.setCellFactory(column -> new DoubleTableCell());
        getColumns().add(integralCol);

        TableColumn<DatasetRegion, Double> normalizedIntegralCol = new TableColumn<>("Normalized");
        normalizedIntegralCol.setCellValueFactory(param -> {
            double norm = chart.getDataset().getNorm() / chart.getDataset().getScale();
            Double normProp = norm != 0 ? param.getValue().getIntegral() / norm : null;
            return new SimpleObjectProperty<>(normProp);
        });
        normalizedIntegralCol.setCellFactory(column -> new DoubleTableCell());
        normalizedIntegralCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleColumnFormatter()));
        normalizedIntegralCol.setEditable(true);
        normalizedIntegralCol.setOnEditCommit(this::normalizedIntegralChanged);
        getColumns().add(normalizedIntegralCol);

        TableColumn<DatasetRegion, String> integralTypeCol = new TableColumn<>(INTEGRAL_TYPE_COLUMN_NAME);
        integralTypeCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getAutoText()));
        getColumns().add(integralTypeCol);

        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Only allow a single row to be selected since only one integral can be selected at a time on the chart
        getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        setItems(sortedRegions);

        getSortOrder().addListener((ListChangeListener<TableColumn<DatasetRegion, ?>>) c -> {
            if (c.getList().isEmpty()) {
                getSortOrder().add(startPosCol);
                sort();
            }
        });
        chart = PolyChart.getActiveChart();
        PolyChart.getActiveChartProperty().addListener((observable, oldValue, newValue) -> chart = newValue);
    }

    /**
     * Listener for edits of the normalized integral column that updates the norm in the dataset.
     * @param event The normalized integral column cell edit event
     */
    private void normalizedIntegralChanged(TableColumn.CellEditEvent<DatasetRegion, Double> event) {
        chart.getDataset().setNorm((event.getRowValue().getIntegral() * chart.getDataset().getScale()) / event.getNewValue());
        chart.refresh();
        refresh();
    }

    /**
     * Listener for edits of the start or ending region bounds. The value is updated in the DatasetRegion for that
     * row and the new integral is measured.
     * @param event Edit event for the start or ending region bounds
     */
    private void regionBoundChanged(TableColumn.CellEditEvent<DatasetRegion, Double> event) {
        double newRegionBound = event.getNewValue() != null ? event.getNewValue() : event.getOldValue();
        if (event.getTableColumn().getText().equals(REGION_START_COLUMN_NAME)) {
            event.getRowValue().setRegionStart(0, newRegionBound);
        } else {
            event.getRowValue().setRegionEnd(0, newRegionBound);
        }
        try {
            event.getRowValue().measure(chart.getDataset());
        } catch (IOException e) {
            log.warn("Error measuring new region bounds. {}", e.getMessage(), e);
        }
        chart.refresh();
        refresh();
    }

    /**
     * Adds a region to the table and selects it.
     * @param region The region to add
     */
    public void addRegion(DatasetRegion region) {
        unsortedRegions.add(region);
        getSelectionModel().select(region);
    }

    /**
     * Clears the current regions and updates the list with the new values.
     * @param regions The new regions to set
     */
    public void setRegions(List<DatasetRegion> regions) {
        unsortedRegions.clear();
        unsortedRegions.addAll(regions);
    }

    /**
     * Removes the selected region from the regions table and from the dataset.
     */
    public void removeSelectedRegion() {
        List<DatasetRegion> selectedRows = getSelectionModel().getSelectedItems();
        IntegralTool.getTool(chart).deleteRegion(selectedRows.get(0));
        unsortedRegions.removeAll(selectedRows);
    }

    /**
     * Removes the provided region from the regions table.
     * @param region The region to remove
     */
    public void removeRegion(DatasetRegion region) {
        unsortedRegions.remove(region);
        refresh();
    }

    /**
     * Selects the row of the provided region in the table.
     * @param regionToSelect The region to select.
     */
    public void selectRegion(DatasetRegion regionToSelect) {
        getSelectionModel().clearSelection();
        getSelectionModel().select(regionToSelect);
    }
}
