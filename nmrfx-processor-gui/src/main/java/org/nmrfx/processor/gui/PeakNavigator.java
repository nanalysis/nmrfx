package org.nmrfx.processor.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.events.PeakEvent;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.events.PeakListener;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.project.ProjectBase;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Bruce Johnson
 */
public class PeakNavigator implements PeakListener {

    ToolBar navigatorToolBar;
    TextField peakIdField;
    MenuButton peakListMenuButton;
    ToggleButton deleteButton;
    PeakNavigable peakNavigable;
    PeakList peakList;
    Peak currentPeak;
    static Background deleteBackground = new Background(new BackgroundFill(Color.RED, CornerRadii.EMPTY, Insets.EMPTY));
    Background defaultBackground = null;
    Background defaultCellBackground = null;
    Optional<List<Peak>> matchPeaks = Optional.empty();
    int matchIndex = 0;
    Consumer closeAction = null;
    boolean showAtoms = false;
    Label atomXFieldLabel;
    Label atomYFieldLabel;
    Label intensityFieldLabel;
    Label atomXLabel;
    Label atomYLabel;
    Label intensityLabel;

    private PeakNavigator(PeakNavigable peakNavigable) {
        this.peakNavigable = peakNavigable;
    }

    private PeakNavigator(PeakNavigable peakNavigable, Consumer closeAction) {
        this.peakNavigable = peakNavigable;
        this.closeAction = closeAction;
    }

    public static PeakNavigator create(PeakNavigable peakNavigable) {
        return new PeakNavigator(peakNavigable);
    }

    public PeakNavigator onClose(Consumer closeAction) {
        this.closeAction = closeAction;
        return this;
    }

    public PeakNavigator showAtoms() {
        this.showAtoms = true;
        return this;
    }

    public ToolBar getToolBar() {
        return navigatorToolBar;
    }

    public void close() {
        closeAction.accept(this);
    }

    public PeakNavigator initialize(ToolBar toolBar) {
        initPeakNavigator(toolBar, null, null);
        return this;
    }
    public PeakNavigator initialize(ToolBar toolBar, MenuButton peakListMenuButton) {
        initPeakNavigator(toolBar, null, peakListMenuButton);
        return this;
    }

    void initPeakNavigator(ToolBar toolBar, PeakNavigator parentNavigator, MenuButton peakListMenuButton) {
        this.navigatorToolBar = toolBar;
        peakIdField = new TextField();
        peakIdField.setMinWidth(75);
        peakIdField.setMaxWidth(75);
        PeakNavigator navigator = parentNavigator == null ? this : parentNavigator;

        ArrayList<Button> buttons = new ArrayList<>();
        Button bButton;
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", MainApp.ICON_SIZE_STR, MainApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        closeButton.setOnAction(e -> close());

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_BACKWARD, "", MainApp.ICON_SIZE_STR, MainApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> navigator.firstPeak(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BACKWARD, "", MainApp.ICON_SIZE_STR, MainApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> navigator.previousPeak(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FORWARD, "", MainApp.ICON_SIZE_STR, MainApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> navigator.nextPeak(e));
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_FORWARD, "", MainApp.ICON_SIZE_STR, MainApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> navigator.lastPeak(e));
        buttons.add(bButton);
        deleteButton = GlyphsDude.createIconToggleButton(FontAwesomeIcon.BAN, "", MainApp.ICON_SIZE_STR, MainApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        // prevent accidental activation when inspector gets focus after hitting space bar on peak in spectrum
        // a second space bar hit would activate
        deleteButton.setOnKeyPressed(e -> e.consume());
        deleteButton.setOnAction(e -> navigator.setDeleteStatus(deleteButton));

        for (Button button : buttons) {
            // button.getStyleClass().add("toolButton");
        }
        if (closeAction != null) {
            toolBar.getItems().add(closeButton);
        }

        if (parentNavigator == null) {
            if (peakListMenuButton == null) {
                this.peakListMenuButton = new MenuButton("List");
                toolBar.getItems().add(this.peakListMenuButton);
            } else {
                this.peakListMenuButton = peakListMenuButton;
            }
            updatePeakListMenu();
        } else {
            parentNavigator.peakIdField.textProperty().bindBidirectional(peakIdField.textProperty());
        }
        toolBar.getItems().addAll(buttons);
        toolBar.getItems().add(peakIdField);
        toolBar.getItems().add(deleteButton);

        if (showAtoms) {
            atomXFieldLabel = new Label("X:");
            atomYFieldLabel = new Label("Y:");
            intensityFieldLabel = new Label("I:");
            atomXLabel = new Label();
            atomXLabel.setMinWidth(75);
            atomYLabel = new Label();
            atomYLabel.setMinWidth(75);
            intensityLabel = new Label();
            intensityLabel.setMinWidth(75);

            Pane filler2 = new Pane();
            filler2.setMinWidth(20);
            Pane filler3 = new Pane();
            filler3.setMinWidth(20);
            Pane filler4 = new Pane();
            filler4.setMinWidth(20);
            Pane filler5 = new Pane();
            HBox.setHgrow(filler5, Priority.ALWAYS);

            toolBar.getItems().addAll(filler2, atomXFieldLabel, atomXLabel, filler3, atomYFieldLabel, atomYLabel, filler4, intensityFieldLabel, intensityLabel, filler5);

        }

        peakIdField.setOnKeyReleased(kE -> {
            if (null != kE.getCode()) {
                switch (kE.getCode()) {
                    case ENTER:
                        navigator.gotoPeakId(peakIdField);
                        break;
                    case UP:
                        navigator.gotoNextMatch(1);
                        break;
                    case DOWN:
                        navigator.gotoNextMatch(-1);
                        break;
                    default:
                        break;
                }
            }
        });
        MapChangeListener<String, PeakList> mapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> {
            updatePeakListMenu();
        };

        ProjectBase.getActive().addPeakListListener(mapChangeListener);
        // The different control items end up with different heights based on font and icon size,
        // set all the items to use the same height
        this.navigatorToolBar.heightProperty().addListener((observable, oldValue, newValue) -> {
            // don't adjust the height of the close button which is always at index 0
            List<Node> navToolBarItems = this.navigatorToolBar.getItems().subList(1, this.navigatorToolBar.getItems().size());
            Optional<Double> height = navToolBarItems.stream().map(node -> node.prefHeight(Region.USE_COMPUTED_SIZE)).max(Double::compare);
            if (height.isPresent()) {
                for (Node node : navToolBarItems) {
                    if (node instanceof Control) {
                        ((Control) node).setPrefHeight(height.get());
                    }
                }
            }
        });
    }

    public void updatePeakListMenu() {
        peakListMenuButton.getItems().clear();

        for (PeakList peakList : ProjectBase.getActive().getPeakLists()) {
            String peakListName = peakList.getName();
            MenuItem menuItem = new MenuItem(peakListName);
            menuItem.setOnAction(e -> {
                PeakNavigator.this.setPeakList(peakListName);
            });
            peakListMenuButton.getItems().add(menuItem);
        }
    }

    public void setPeakList() {
        if (peakList == null) {
            PeakList testList = null;
            FXMLController controller = FXMLController.getActiveController();
            PolyChart chart = controller.getActiveChart();
            if (chart != null) {
                ObservableList<PeakListAttributes> attr = chart.getPeakListAttributes();
                if (!attr.isEmpty()) {
                    testList = attr.get(0).getPeakList();
                }
            }
            if (testList == null) {
                Optional<PeakList> firstListOpt = PeakList.getFirst();
                if (firstListOpt.isPresent()) {
                    testList = firstListOpt.get();
                }
            }
            setPeakList(testList);
        }
    }

    public void removePeakList() {
        if (peakList != null) {
            peakList.removePeakChangeListener(this);
        }
        peakList = null;
        currentPeak = null;
    }

    public void setPeakList(String listName) {
        peakList = PeakList.get(listName);
        PeakNavigator.this.setPeakList(peakList);
    }

    public void setPeakList(PeakList newPeakList) {
        peakList = newPeakList;
        if (peakList != null) {
            currentPeak = peakList.getPeak(0);
            setPeakIdField();
            peakList.registerPeakChangeListener(this);
        } else {
        }
        peakNavigable.refreshPeakView(currentPeak);
        peakNavigable.refreshPeakListView(peakList);
    }

    public Peak getPeak() {
        return currentPeak;
    }

    public void setPeak(Peak peak) {
        currentPeak = peak;
        setPeakIdField();
        peakNavigable.refreshPeakView(peak);
        if (peak != null) {
            if (peakList != peak.getPeakList()) {
                peakList = peak.getPeakList();
                peakList.registerPeakChangeListener(this);
                peakNavigable.refreshPeakListView(peakList);
            }
            updateDeleteStatus();
        }
        updateAtomLabels(peak);
    }

    void updateAtomLabels(Peak peak) {
        if (showAtoms) {
            if (peak != null) {
                FXMLController controller = FXMLController.getActiveController();
                PolyChart chart = controller.getActiveChart();
                ObservableList<PeakListAttributes> peakAttrs = chart.getPeakListAttributes();
                ObservableList<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
                PeakDim peakDimX = null;
                PeakDim peakDimY = null;
                if (!peakAttrs.isEmpty()) {
                    PeakListAttributes peakAttr = peakAttrs.get(0);
                    int[] pdims = peakAttr.getPeakDim();
                    peakDimX = peak.getPeakDim(pdims[0]);
                    if (peak.getPeakDims().length > 1) {
                        peakDimY = peak.getPeakDim(pdims[1]);
                    }
                } else if (!dataAttrs.isEmpty()) {
                    DatasetAttributes dataAttr = dataAttrs.get(0);
                    peakDimX = peak.getPeakDim(dataAttr.getLabel(0));
                    if (peak.getPeakDims().length > 1) {
                        peakDimY = peak.getPeakDim(dataAttr.getLabel(1));
                    }
                }
                if (peakDimX != null) {
                    atomXLabel.setText(peakDimX.getLabel());
                }
                if (peakDimY != null) {
                    atomYLabel.setText(peakDimY.getLabel());
                }
                intensityLabel.setText(String.format("%.2f", peak.getIntensity()));
            } else {
                atomXLabel.setText("");
                atomYLabel.setText("");
                intensityLabel.setText("");

            }
        }
    }

    void updateDeleteStatus() {
        if (defaultBackground == null) {
            defaultBackground = peakIdField.getBackground();
        }
        if (currentPeak.getStatus() < 0) {
            deleteButton.setSelected(true);
            peakIdField.setBackground(deleteBackground);
        } else {
            deleteButton.setSelected(false);
            peakIdField.setBackground(defaultBackground);
        }

    }

    private void setPeakIdField() {
        if (currentPeak == null) {
            peakIdField.setText("");
        } else {
            peakIdField.setText(String.valueOf(currentPeak.getIdNum()));
        }

    }

    public void previousPeak(ActionEvent event) {
        if (currentPeak != null) {
            int peakIndex = currentPeak.getIndex();
            peakIndex--;
            if (peakIndex < 0) {
                peakIndex = 0;
            }
            Peak peak = peakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    public void firstPeak(ActionEvent event) {
        if (peakList != null) {
            Peak peak = peakList.getPeak(0);
            setPeak(peak);
        }
    }

    public void nextPeak(ActionEvent event) {
        if (currentPeak != null) {
            int peakIndex = currentPeak.getIndex();
            peakIndex++;
            if (peakIndex >= peakList.size()) {
                peakIndex = peakList.size() - 1;
            }
            Peak peak = peakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    public void lastPeak(ActionEvent event) {
        if (peakList != null) {
            int peakIndex = peakList.size() - 1;
            Peak peak = peakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    public List<Peak> matchPeaks(String pattern) {
        List<Peak> result;
        if (pattern.startsWith("re")) {
            pattern = pattern.substring(2).trim();
            if (pattern.contains(":")) {
                String[] matchStrings = pattern.split(":");
                result = peakList.matchPeaks(matchStrings, true, true);
            } else {
                String[] matchStrings = pattern.split(",");
                result = peakList.matchPeaks(matchStrings, true, false);
            }
        } else {
            if (pattern.contains(":")) {
                if (pattern.charAt(0) == ':') {
                    pattern = " " + pattern;
                }
                if (pattern.charAt(pattern.length() - 1) == ':') {
                    pattern = pattern + " ";
                }

                String[] matchStrings = pattern.split(":");
                result = peakList.matchPeaks(matchStrings, false, true);
            } else {
                if (pattern.charAt(0) == ',') {
                    pattern = " " + pattern;
                }
                if (pattern.charAt(pattern.length() - 1) == ',') {
                    pattern = pattern + " ";
                }
                String[] matchStrings = pattern.split(",");
                result = peakList.matchPeaks(matchStrings, false, false);
            }
        }
        return result;
    }

    public void gotoPeakId(TextField idField) {
        if (peakList != null) {
            matchPeaks = Optional.empty();
            int id = Integer.MIN_VALUE;
            String idString = idField.getText().trim();
            if (idString.length() != 0) {
                try {
                    id = Integer.parseInt(idString);
                } catch (NumberFormatException nfE) {
                    List<Peak> peaks = matchPeaks(idString);
                    if (!peaks.isEmpty()) {
                        setPeak(peaks.get(0));
                        matchPeaks = Optional.of(peaks);
                        matchIndex = 0;
                    } else {
                        idField.setText("");
                    }
                }
                if (id != Integer.MIN_VALUE) {
                    if (id < 0) {
                        id = 0;
                    } else if (id >= peakList.size()) {
                        id = peakList.size() - 1;
                    }
                    Peak peak = peakList.getPeakByID(id);
                    setPeak(peak);
                }
            }
        }
    }

    void gotoNextMatch(int dir) {
        if (matchPeaks.isPresent()) {
            List<Peak> peaks = matchPeaks.get();
            if (!peaks.isEmpty()) {
                matchIndex += dir;
                if (matchIndex >= peaks.size()) {
                    matchIndex = 0;
                } else if (matchIndex < 0) {
                    matchIndex = peaks.size() - 1;
                }
                Peak peak = peaks.get(matchIndex);
                setPeak(peak);
            }
        }
    }

    void setDeleteStatus(ToggleButton button) {
        if (currentPeak != null) {
            if (button.isSelected()) {
                currentPeak.setStatus(-1);
            } else {
                currentPeak.setStatus(0);
            }
            peakNavigable.refreshPeakView(currentPeak);
        }
        updateDeleteStatus();
    }

    @Override
    public void peakListChanged(PeakEvent peakEvent) {
        if (peakEvent.getSource() instanceof PeakList) {
            PeakList sourceList = (PeakList) peakEvent.getSource();
            if (sourceList == peakList) {
                if (Platform.isFxApplicationThread()) {
                    peakNavigable.refreshPeakView();
                } else {
                    Platform.runLater(() -> {
                        peakNavigable.refreshPeakView();
                    }
                    );
                }
            }
        }
    }
}
