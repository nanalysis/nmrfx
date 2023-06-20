package org.nmrfx.analyst.gui.datasetbrowser;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import org.controlsfx.control.tableview2.TableView2;
import org.nmrfx.utilities.DatasetSummary;

public class DatasetBrowserTableView extends TableView2<DatasetSummary> {
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

    }
}
