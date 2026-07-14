package org.nmrfx.analyst.gui.regions;

import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import org.nmrfx.analyst.gui.tools.IntegralTool;
import org.nmrfx.analyst.gui.utitlity.DoubleTableCell;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.DatasetRegion;
import org.nmrfx.datasets.DatasetRegionListener;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PolyChartManager;
import org.nmrfx.utils.TableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


/**
 * A tableview for displaying DatasetRegions
 */
public class RegionsTable extends TableView<DatasetRegion> {
    private static final Logger log = LoggerFactory.getLogger(RegionsTable.class);
    private static final String REGION_START_COLUMN_NAME = "Start";
    private static final String REGION_END_COLUMN_NAME = "End";
    private static final String INTEGRAL_COLUMN_NAME = "Value";
    private static final String NORMALIZED_INTEGRAL_COLUMN_NAME = "Normalized";
    private static final String INTEGRAL_TYPE_COLUMN_NAME = "Type";
    private static final int NUMBER_DECIMAL_PLACES_REGION_BOUNDS = 4;
    private static final int NUMBER_DECIMAL_PLACES_INTEGRAL = 1;
    private PolyChart chart;
    private final ObservableList<DatasetRegion> datasetRegions;
    private final Comparator<DatasetRegion> startingComparator = Comparator.comparing(dr -> dr.getRegionStart(0));
    private final DatasetRegionListener regionListener;

    /**
     * Formatter to change between Double and Strings in editable columns of Doubles
     */
    private static class DoubleColumnFormatter extends javafx.util.converter.DoubleStringConverter {
        String formatString;

        public DoubleColumnFormatter(int decimalPlaces) {
            formatString = "%." + decimalPlaces + "f";
        }

        @Override
        public String toString(Double object) {
            return String.format(formatString, object);
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

        this.datasetRegions = FXCollections.observableList(new ArrayList<>());
        SortedList<DatasetRegion> sortedRegions = new SortedList<>(this.datasetRegions);
        sortedRegions.comparatorProperty().bind(comparatorProperty());
        regionListener = updateRegion -> {
            datasetRegions.sort(Comparator.comparing(dr -> dr.getRegionStart(0)));
            refresh();
        };

        setEditable(true);

        TableColumn<DatasetRegion, Integer> groupCol = new TableColumn<>("Region");
        groupCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>( cellData.getValue().getGroup() + 1));
        getColumns().add(groupCol);

        TableColumn<DatasetRegion, String> datasetCol = new TableColumn<>("Dataset");
        datasetCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getDatasetName()));
        getColumns().add(datasetCol);

        TableColumn<DatasetRegion, Double> startPosCol = new TableColumn<>(REGION_START_COLUMN_NAME);
        startPosCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getRegionStart(0)));
        startPosCol.setCellFactory(TextFieldTableCell.forTableColumn(TableUtils.getDoubleColumnFormatter(NUMBER_DECIMAL_PLACES_REGION_BOUNDS)));
        startPosCol.setEditable(true);
        startPosCol.setOnEditCommit(this::regionBoundChanged);
        getColumns().add(startPosCol);

        TableColumn<DatasetRegion, Double> endPosCol = new TableColumn<>(REGION_END_COLUMN_NAME);
        endPosCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getRegionEnd(0)));
        endPosCol.setCellFactory(TextFieldTableCell.forTableColumn(TableUtils.getDoubleColumnFormatter(NUMBER_DECIMAL_PLACES_REGION_BOUNDS)));
        endPosCol.setEditable(true);
        endPosCol.setOnEditCommit(this::regionBoundChanged);
        getColumns().add(endPosCol);

        TableColumn<DatasetRegion, Double> integralCol = new TableColumn<>(INTEGRAL_COLUMN_NAME);
        integralCol.setCellValueFactory(new PropertyValueFactory<>("integral"));
        integralCol.setCellFactory(column -> new DoubleTableCell<>(NUMBER_DECIMAL_PLACES_INTEGRAL));
        getColumns().add(integralCol);

        TableColumn<DatasetRegion, Double> normalizedIntegralCol = new TableColumn<>(NORMALIZED_INTEGRAL_COLUMN_NAME);
        normalizedIntegralCol.setCellValueFactory(param -> {
            double norm = chart.getDataset().getNorm() / chart.getDataset().getScale();
            Double normProp = Math.abs(norm) > 1.0e-9 ? param.getValue().getIntegral() / norm : null;
            return new SimpleObjectProperty<>(normProp);
        });
        normalizedIntegralCol.setCellFactory(column -> new DoubleTableCell<>(NUMBER_DECIMAL_PLACES_INTEGRAL));
        normalizedIntegralCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleColumnFormatter(NUMBER_DECIMAL_PLACES_INTEGRAL)));
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

        chart = PolyChartManager.getInstance().getActiveChart();
        PolyChartManager.getInstance().activeChartProperty().addListener((observable, oldValue, newValue) -> chart = newValue);
    }

    /**
     * Listener for edits of the normalized integral column that updates the norm in the dataset.
     *
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
     *
     * @param event Edit event for the start or ending region bounds
     */
    private void regionBoundChanged(TableColumn.CellEditEvent<DatasetRegion, Double> event) {
        double newRegionBound = event.getNewValue() != null ? event.getNewValue() : event.getOldValue();
        DatasetRegion regionChanged = event.getRowValue();
        if (event.getTableColumn().getText().equals(REGION_START_COLUMN_NAME)) {
            regionChanged.setRegionStart(0, newRegionBound);
        } else {
            regionChanged.setRegionEnd(0, newRegionBound);
        }
        try {
            for (DatasetRegion region : datasetRegions) {
                if (region.getGroup() == regionChanged.getGroup()) {
                    region.measure();
                }
            }
        } catch (IOException e) {
            log.warn("Error measuring new region bounds. {}", e.getMessage(), e);
        }
        // If the region bound has changed it is no longer an auto set region
        regionChanged.setAuto(false);
        chart.refresh();
    }

    /**
     * Clears the current regions and updates the list with the new values.
     *
     * @param regions The new regions to set
     */
    public void setRegions(List<DatasetRegion> regions) {
        datasetRegions.clear();
        regions.forEach(datasetRegion -> datasetRegion.addListener(regionListener));
        datasetRegions.addAll(regions);
        int i = 0;
        int group = 0;
        for (var region : regions) {
            if (region.getLinkRegion() == null) {
                region.setGroup(group++);
            }
        }
        for (var region:regions) {
            region.setIndex(i++);
            if (region.getLinkRegion() != null) {
                region.setGroup(region.getLinkRegion().getGroup());
            }
        }
        datasetRegions.sort(startingComparator);
    }

    /**
     * Removes the selected region from the regions table and from the dataset.
     */
    public void removeSelectedRegion() {
        List<DatasetRegion> selectedRows = getSelectionModel().getSelectedItems();
        IntegralTool.getTool(chart).deleteRegion(selectedRows.get(0));
        datasetRegions.removeAll(selectedRows);
    }

    /**
     * Selects the row of the provided region in the table.
     *
     * @param regionToSelect The region to select.
     */
    public void selectRegion(DatasetRegion regionToSelect) {
        getSelectionModel().clearSelection();
        getSelectionModel().select(regionToSelect);
    }
}
