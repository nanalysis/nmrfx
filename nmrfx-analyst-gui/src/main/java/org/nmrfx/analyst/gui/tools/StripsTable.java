package org.nmrfx.analyst.gui.tools;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.controlsfx.control.tableview2.FilteredTableColumn;
import org.controlsfx.control.tableview2.FilteredTableView;
import org.controlsfx.control.tableview2.TableColumn2;
import org.controlsfx.control.textfield.TextFields;
import org.nmrfx.analyst.gui.spectra.StripController;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomResonance;
import org.nmrfx.chemistry.Entity;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakLabeller;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.structure.seqassign.SeqFragment;
import org.nmrfx.structure.seqassign.SpinSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class StripsTable {
    VBox vBox;
    FilteredTableView<Peak> tableView = new FilteredTableView<>();
    ToolBar toolBar = new ToolBar();
    FXMLController fxmlController;
    StripController stripController;
    ObservableList<Peak> tablePeaks = FXCollections.observableArrayList();
    SortedList<Peak> sortedPeaks;
    FilteredTableColumn<Peak, Number> fragmentColumn;
    FilteredTableColumn<Peak, String> atomNameColumn;
    TextField peakFields = null;
    ChoiceBox<String> fragmentChoice = new ChoiceBox<>();
    CheckBox assignedCheckBox = new CheckBox();
    Background errorBackground =  new Background(new BackgroundFill(Color.LIGHTYELLOW, null, null));
    Background defaultBackground = new Background(new BackgroundFill(Color.WHITE, null, null));;

    public StripsTable(FXMLController fxmlController, StripController stripController, VBox vBox) {
        this.vBox = vBox;
        this.fxmlController = fxmlController;
        this.stripController = stripController;
        FilteredList<Peak> filteredPeaks = new FilteredList<>(tablePeaks);
        filteredPeaks.predicateProperty().bind(tableView.predicateProperty());
        sortedPeaks = new SortedList<>(filteredPeaks);
        sortedPeaks.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedPeaks);
        initTools();
        VBox.setVgrow(tableView, Priority.ALWAYS);
        vBox.getChildren().addAll(tableView);
    }

    void initTools() {
        MenuButton addMenu = new MenuButton("Actions");

        MenuItem clearMenuItem = new MenuItem("Clear");
        addMenu.getItems().add(clearMenuItem);
        clearMenuItem.setOnAction(e -> stripController.clear());

        MenuItem addAllMenuItem = new MenuItem("All");
        addMenu.getItems().add(addAllMenuItem);
        addAllMenuItem.setOnAction(e -> stripController.addAll());

        MenuItem addAssignedMenuItem = new MenuItem("Assigned");
        addMenu.getItems().add(addAssignedMenuItem);
        addAssignedMenuItem.setOnAction(e -> stripController.addAssigned());

        addAllMenuItem.setOnAction(e -> stripController.addAll());

        fragmentChoice = new ChoiceBox<>();
        toolBar.getItems().addAll(addMenu, new Label("Assigned:"), assignedCheckBox, new Label("Fragment:"), fragmentChoice);
        fragmentChoice.valueProperty().addListener(e -> setFragmentPredicate());
        assignedCheckBox.selectedProperty().addListener(e -> setAssignedPredicate());
        HBox hBox = new HBox();
        Label peakLabel = new Label("Peaks:");
        peakFields = TextFields.createClearableTextField();
        HBox.setHgrow(peakFields, Priority.ALWAYS);
        hBox.getChildren().addAll(peakLabel, peakFields);
        peakFields.textProperty().addListener(e -> peakFieldChanged());
        vBox.getChildren().addAll(toolBar, hBox);

    }

    public Integer getResidue(PeakDim peakDim) {
        Integer result = Integer.MIN_VALUE;
        var resonance = peakDim.getResonance();
        if (resonance instanceof AtomResonance atomResonance) {
            Atom atom = atomResonance.getAtom();
            if (atom != null) {
                Entity entity = atom.getEntity();
                if (entity instanceof Residue residue) {
                    result = residue.getResNum();
                }
            } else {
                String label = peakDim.getLabel();
                Optional<PeakLabeller.ChainResAtomSpecifier> optionalChainResAtomSpecifier = PeakLabeller.parse(label);
                if (optionalChainResAtomSpecifier.isPresent()) {
                    result = optionalChainResAtomSpecifier.get().resNum();
                }
            }
        }
        return result;
    }

    public String getAtomName(PeakDim peakDim) {
        String result = "";
        var resonance = peakDim.getResonance();
        if (resonance instanceof AtomResonance atomResonance) {
            Atom atom = atomResonance.getAtom();
            if (atom != null) {
                result = atom.getName();
            } else {
                String label = peakDim.getLabel();
                Optional<PeakLabeller.ChainResAtomSpecifier> optionalChainResAtomSpecifier = PeakLabeller.parse(label);
                if (optionalChainResAtomSpecifier.isPresent()) {
                    result = optionalChainResAtomSpecifier.get().atomName();
                }
            }
        }
        return result;
    }

    public Integer getFragment(Peak peak) {
        Optional<SpinSystem> spinSys = SpinSystem.spinSystemFromPeak(peak);
        if (spinSys.isPresent()) {
            Optional<SeqFragment> fragment = spinSys.get().getFragment();
            if (fragment.isPresent()) {
                return fragment.get().getId();
            }
        }
        return -1;
    }


    void initTable(int nDim) {
        TableColumn2<Peak, Number> idColumn = new TableColumn2<>("ID");
        idColumn.setCellValueFactory(e -> new SimpleIntegerProperty(e.getValue().getIdNum()));
        tableView.getColumns().clear();
        tableView.getColumns().add(idColumn);
        for (int i = 0; i < nDim; i++) {
            final int iDim = i;
            FilteredTableColumn<Peak, Number> residueColumn = new FilteredTableColumn<>("Res" + (iDim + 1));
            residueColumn.setCellValueFactory(e -> new SimpleIntegerProperty(getResidue(e.getValue().getPeakDim(iDim))));
            residueColumn.setCellFactory(tableColumn -> new TableCell<>() {
                @Override
                protected void updateItem(Number item, boolean empty) {
                    super.updateItem(item, empty);

                    this.setText(null);
                    this.setGraphic(null);

                    if (!empty && (item.intValue() != Integer.MIN_VALUE)) {
                        this.setText(String.valueOf(item));
                    }
                }
            });

            tableView.getColumns().add(residueColumn);

            FilteredTableColumn<Peak, String> label1Column = new FilteredTableColumn<>("Label" + (iDim + 1));
            label1Column.setCellValueFactory(e -> new SimpleStringProperty(getAtomName(e.getValue().getPeakDim(iDim))));

            tableView.getColumns().add(label1Column);
            sortedPeaks.addListener((ListChangeListener<? super Peak>) e -> stripController.updatePeaks());
            if (i == 0) {
                atomNameColumn = label1Column;
            }
        }
        fragmentColumn = new FilteredTableColumn<>("Frag");

        fragmentColumn.setCellValueFactory(e -> new SimpleIntegerProperty(getFragment(e.getValue())));
        fragmentColumn.setCellFactory(tableColumn -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);

                this.setText(null);
                this.setGraphic(null);

                if (!empty && (item.intValue() != -1)) {
                    this.setText(String.valueOf(item));
                }
            }
        });

        tableView.getColumns().add(fragmentColumn);
    }

    private void setFragmentPredicate() {
        String fragmentStr = fragmentChoice.getValue();
        final int fragmentID;
        if ((fragmentStr == null) || fragmentStr.equals("All")) {
            fragmentID = -2;
        } else if (fragmentStr.equals("No")) {
            fragmentID = -1;
        } else {
            fragmentID = Integer.parseInt(fragmentStr);
        }
        fragmentColumn.setPredicate(e -> (fragmentID == -2) || e.intValue() == fragmentID);
    }

    private void setAssignedPredicate() {
        boolean assignedMode = assignedCheckBox.isSelected();
        atomNameColumn.setPredicate(e -> !assignedMode || !e.isBlank());
    }

    public void updatePeaks(ObservableList<Peak> peaks) {
        int currentNColumns = tableView.getColumns().size() - 2;
        if (!peaks.isEmpty()) {
            Peak peak = peaks.get(0);
            int nDim = peak.getPeakList().getNDim();
            nDim = 1;
            if ((nDim * 2) != currentNColumns) {
                initTable(nDim);
            }
        }
        var fragmentIds = peaks.stream().map(this::getFragment).sorted().distinct().filter(id -> id >= 0).map(String::valueOf).toList();
        fragmentChoice.getItems().clear();
        fragmentChoice.getItems().addAll("All", "No");
        fragmentChoice.getItems().addAll(fragmentIds);
        tablePeaks.clear();
        tablePeaks.addAll(peaks);
    }

    void peakFieldChanged() {
        String peakString = peakFields.getText().trim();
        String[] fields = peakString.split("\\s+");
        List<Integer> peakIDs = new ArrayList<>();
        ObservableList<Peak> peaks = FXCollections.observableArrayList();

        PeakList peakList = stripController.getControlList();
        System.out.println(peakString + " " + fields.length);
        if (peakString.isBlank() || (fields.length == 0)){
            peaks.addAll(peakList.peaks());
        } else {
            for (var field : fields) {
                if (!field.isBlank()) {
                    if (field.contains("-")) {
                        String[] rangeFields = field.split("-");
                        try {
                            int start = Integer.parseInt(rangeFields[0]);
                            int end = rangeFields.length == 2 ? Integer.parseInt(rangeFields[1]) : start;
                            for (int iPeak = start; iPeak <= end; iPeak++) {
                                peakIDs.add(iPeak);
                            }
                        } catch (NumberFormatException ignored) {
                            peakFields.setBackground(errorBackground);
                            return;
                        }
                    } else {
                        try {
                            int iPeak = Integer.parseInt(field);
                            peakIDs.add(iPeak);
                        } catch (NumberFormatException ignored) {
                            peakFields.setBackground(errorBackground);
                            return;
                        }
                    }
                }
            }
            peaks.addAll(peakIDs.stream().sorted().distinct().map(peakList::getPeakByID).filter(Objects::nonNull).toList());
        }
        peakFields.setBackground(defaultBackground);
        updatePeaks(peaks);
        peakFields.requestFocus();
    }

    public ObservableList<Peak> getSortedPeaks() {
        return sortedPeaks;
    }
}
