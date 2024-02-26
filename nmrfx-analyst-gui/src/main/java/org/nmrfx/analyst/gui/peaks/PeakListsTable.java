package org.nmrfx.analyst.gui.peaks;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.events.PeakEvent;
import org.nmrfx.peaks.events.PeakListener;
import org.nmrfx.project.ProjectBase;

import java.util.Comparator;


/**
 * A tableview for displaying PeakLists
 */
public class PeakListsTable extends TableView<PeakList> implements PeakListener {
    private static final String PEAKLIST_COLUMN_NAME = "PeakList";
    private static final String DATASET_COLUMN_NAME = "Dataset";
    private static final String LABELS_COLUMN_NAME = "Labels";
    private static final String NUMBER_PEAKS_COLUMN_NAME = "Total Peaks";
    private static final String NUMBER_DELETED_COLUMN_NAME = "Deleted";
    private static final String NUMBER_ASSIGNED_COLUMN_NAME = "Assigned";
    private static final String NUMBER_PARTIAL_COLUMN_NAME = "Partial";
    private static final String NUMBER_UNASSIGNED_COLUMN_NAME = "Unassigned";
    MapChangeListener<String, PeakList> mapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> updatePeakLists();

    private String getPeakListLabels(PeakList peakList) {
        StringBuilder sBuilder = new StringBuilder();
        for (var sDim : peakList.getSpectralDims()) {
            if (sBuilder.length() != 0) {
                sBuilder.append(" ");
            }
            sBuilder.append(sDim.getDimName());
        }
        return sBuilder.toString();
    }

    public PeakListsTable() {
        setPlaceholder(new Label("No peakLists to display"));

        TableColumn<PeakList, String> peakListsLabelCol = new TableColumn<>(PEAKLIST_COLUMN_NAME);
        peakListsLabelCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        getColumns().add(peakListsLabelCol);


        TableColumn<PeakList, String> datasetCol = new TableColumn<>(DATASET_COLUMN_NAME);
        datasetCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDatasetName()));
        getColumns().add(datasetCol);

        TableColumn<PeakList, String> labelsCol = new TableColumn<>(LABELS_COLUMN_NAME);
        labelsCol.setCellValueFactory(cellData -> new SimpleStringProperty(getPeakListLabels(cellData.getValue())));
        getColumns().add(labelsCol);

        TableColumn<PeakList, Number> peakNumberCol = new TableColumn<>(NUMBER_PEAKS_COLUMN_NAME);
        peakNumberCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().size()));
        getColumns().add(peakNumberCol);

        TableColumn<PeakList, Number> deletedNumberCol = new TableColumn<>(NUMBER_DELETED_COLUMN_NAME);
        deletedNumberCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getAssignmentStatus().get(Peak.AssignmentLevel.DELETED)));
        getColumns().add(deletedNumberCol);

        TableColumn<PeakList, Number> assignedNumberCol = new TableColumn<>(NUMBER_ASSIGNED_COLUMN_NAME);
        assignedNumberCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getNumberAssigned()));
        getColumns().add(assignedNumberCol);

        TableColumn<PeakList, Number> partialNumberCol = new TableColumn<>(NUMBER_PARTIAL_COLUMN_NAME);
        partialNumberCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getNumberPartialAssigned()));
        getColumns().add(partialNumberCol);

        TableColumn<PeakList, Number> unassignedNumberCol = new TableColumn<>(NUMBER_UNASSIGNED_COLUMN_NAME);
        unassignedNumberCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getNumberUnAssigned()));
        getColumns().add(unassignedNumberCol);

        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        updatePeakLists();
        ProjectBase.getActive().addPeakListListener(mapChangeListener);
    }

    /**
     * Clears the current peakLists and updates the list with the new values.
     */
    public void updatePeakLists() {
        ObservableList<PeakList> peakArrayList = FXCollections.observableArrayList();
        ProjectBase.getActive().getPeakLists().stream().sorted(Comparator.comparing(PeakList::getName)).forEach(peakList -> {
            peakList.registerPeakChangeListener(this);
            peakArrayList.add(peakList);
        });
        var currentLists = getItems();
        boolean ok = peakArrayList.size() == currentLists.size();
        if (ok) {
            for (var peakList : peakArrayList) {
                if (!currentLists.contains(peakList)) {
                    ok = false;
                    break;
                }
            }
        }
        if (!ok) {
            setItems(peakArrayList);
        }
        refresh();
    }

    @Override
    public void peakListChanged(PeakEvent peakEvent) {
        updatePeakLists();
    }
}
