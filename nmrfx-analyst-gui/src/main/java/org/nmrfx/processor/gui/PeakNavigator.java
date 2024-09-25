package org.nmrfx.processor.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.events.PeakEvent;
import org.nmrfx.peaks.events.PeakListener;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakDisplayTool;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.utils.GUIUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author Bruce Johnson
 */
public class PeakNavigator implements PeakListener {
    private static final Background DELETE_BACKGROUND = new Background(new BackgroundFill(Color.RED, CornerRadii.EMPTY, Insets.EMPTY));

    ToolBar navigatorToolBar;
    TextField peakIdField;
    MenuButton peakListMenuButton;
    ToggleButton deleteButton;
    PeakNavigable peakNavigable;
    PeakList peakList;
    Peak currentPeak;
    Background defaultBackground = null;
    Optional<List<Peak>> matchPeaks = Optional.empty();
    int matchIndex = 0;
    Consumer<PeakNavigator> closeAction = null;
    boolean showAtoms = false;
    boolean addShowPeakButton = false;
    Label atomXFieldLabel;
    Label atomYFieldLabel;
    Label intensityFieldLabel;
    Label atomXLabel;
    Label atomYLabel;
    Label intensityLabel;
    ChoiceBox<String> assignModeChoice = new ChoiceBox<>();

    private PeakNavigator(PeakNavigable peakNavigable) {
        this.peakNavigable = peakNavigable;
    }

    public static PeakNavigator create(PeakNavigable peakNavigable) {
        return new PeakNavigator(peakNavigable);
    }

    public PeakNavigator onClose(Consumer<PeakNavigator> closeAction) {
        this.closeAction = closeAction;
        return this;
    }

    public PeakNavigator showAtoms() {
        this.showAtoms = true;
        return this;
    }

    public PeakNavigator addShowPeakButton() {
        this.addShowPeakButton = true;
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
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", AnalystApp.ICON_SIZE_STR, AnalystApp.REG_FONT_SIZE_STR, ContentDisplay.LEFT);
        closeButton.setOnAction(e -> close());

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_BACKWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(navigator::firstPeak);
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BACKWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(navigator::previousPeak);
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FORWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(navigator::nextPeak);
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_FORWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(navigator::lastPeak);
        buttons.add(bButton);
        deleteButton = GlyphsDude.createIconToggleButton(FontAwesomeIcon.BAN, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        // prevent accidental activation when inspector gets focus after hitting space bar on peak in spectrum
        // a second space bar hit would activate
        deleteButton.setOnKeyPressed(Event::consume);
        deleteButton.setOnAction(e -> navigator.setDeleteStatus(deleteButton));

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

        assignModeChoice.getItems().addAll("all", "ok", "deleted", "assigned", "partial", "unassigned", "ambiguous", "invalid");
        assignModeChoice.setValue("all");
        assignModeChoice.valueProperty().addListener(e -> firstPeak(null));
        toolBar.getItems().add(assignModeChoice);
        toolBar.getItems().addAll(buttons);
        toolBar.getItems().add(peakIdField);
        toolBar.getItems().add(deleteButton);

        if (addShowPeakButton) {
            Button showPeakButton = new Button("Goto");
            showPeakButton.setOnAction(e -> PeakDisplayTool.gotoPeak(currentPeak));
            toolBar.getItems().add(showPeakButton);
        }

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
                    case ENTER -> navigator.gotoPeakId(peakIdField);
                    case UP -> navigator.gotoNextMatch(1);
                    case DOWN -> navigator.gotoNextMatch(-1);
                    default -> {
                    }
                }
            }
        });

        peakListMenuButton.setOnShowing(e -> updatePeakListMenu());
        // The different control items end up with different heights based on font and icon size,
        // set all the items to use the same height
        this.navigatorToolBar.heightProperty().addListener((observable, oldValue, newValue) -> GUIUtils.toolbarAdjustHeights(List.of(navigatorToolBar)));
    }

    public void updatePeakListMenu() {
        peakListMenuButton.getItems().clear();

        for (PeakList peakList1 : ProjectBase.getActive().getPeakLists()) {
            String peakListName = peakList1.getName();
            MenuItem menuItem = new MenuItem(peakListName);
            menuItem.setOnAction(e -> PeakNavigator.this.setPeakList(peakListName));
            peakListMenuButton.getItems().add(menuItem);
        }
    }

    public void setPeakList() {
        if (peakList == null) {
            PeakList testList = null;
            FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
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
            assignModeChoice.setValue("all");
            firstPeak(null);
            setPeakIdField();
            peakList.registerPeakChangeListener(this);
        }
        peakNavigable.refreshPeakView(currentPeak);
        peakNavigable.refreshPeakListView(peakList);
        updateDeleteStatus();
    }

    public Peak getPeak() {
        return currentPeak;
    }

    public void setPeak(Peak peak) {
        currentPeak = peak;
        setPeakIdField();
        peakNavigable.refreshPeakView(peak);
        if (peak != null) {
            if (!filterMatches(peak)) {
                assignModeChoice.setValue("all");
            }
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
                FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
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
        if ((currentPeak != null) && (currentPeak.getStatus() < 0)) {
            deleteButton.setSelected(true);
            peakIdField.setBackground(DELETE_BACKGROUND);
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

    private boolean filtered() {
        return !assignModeChoice.getValue().equals("all");
    }

    private boolean filterMatches(Peak peak) {
        return peak != null && Peak.AssignmentLevel.match(peak.getAssignmentLevel(), assignModeChoice.getValue());
    }

    public void previousPeak(ActionEvent event) {
        if (filtered()) {
            previousPeakFiltered();
        } else {

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
    }

    void previousPeakFiltered() {
        if (currentPeak != null) {
            int peakIndex = currentPeak.getIndex();
            previousPeakFiltered(peakIndex);
        }
    }

    void previousPeakFiltered(int peakIndex) {
        peakIndex--;
        Peak peak = null;
        boolean gotPeak = false;
        while (peakIndex >= 0) {
            peak = peakList.getPeak(peakIndex);
            if (filterMatches(peak)) {
                gotPeak = true;
                break;
            }
            peakIndex--;
        }
        if (gotPeak) {
            setPeak(peak);
        } else if (!filterMatches(currentPeak)) {
            setPeak(null);
        }
    }


    public void firstPeak(ActionEvent event) {
        if (filtered()) {
            firstPeakFiltered();
        } else {
            if (peakList != null) {
                Peak peak = peakList.getPeak(0);
                setPeak(peak);
            }
        }
    }

    public void firstPeakFiltered() {
        if (peakList != null) {
            Peak peak = peakList.getPeak(0);
            if (!filterMatches(peak)) {
                nextPeakFiltered(0);
            } else {
                setPeak(peak);
            }
        }
    }

    public void nextPeak(ActionEvent event) {
        if (filtered()) {
            nextPeakFiltered();
        } else {
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
    }

    void nextPeakFiltered() {
        if (currentPeak != null) {
            int peakIndex = currentPeak.getIndex();
            nextPeakFiltered(peakIndex);
        }
    }

    void nextPeakFiltered(int peakIndex) {
        peakIndex++;
        Peak peak = null;
        boolean gotPeak = false;
        while (peakIndex < peakList.size()) {
            peak = peakList.getPeak(peakIndex);
            if (filterMatches(peak)) {
                gotPeak = true;
                break;
            }
            peakIndex++;
        }
        if (gotPeak) {
            setPeak(peak);
        } else if (!filterMatches(currentPeak)) {
            setPeak(null);
        }
    }


    public void lastPeak(ActionEvent event) {
        if (filtered()) {
            lastPeakFiltered();
        } else {
            if (peakList != null) {
                int peakIndex = peakList.size() - 1;
                Peak peak = peakList.getPeak(peakIndex);
                setPeak(peak);
            }
        }
    }

    public void lastPeakFiltered() {
        if (peakList != null) {
            Peak peak = peakList.getPeak(peakList.size() - 1);
            if (!filterMatches(peak)) {
                previousPeakFiltered(peakList.size() - 1);
            } else {
                setPeak(peak);
            }
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
                    gotoPeakId(id);
                }
            }
        }
    }

    public void gotoPeakId(int id) {
        if (id < 0) {
            id = 0;
        } else if (id >= peakList.size()) {
            id = peakList.size() - 1;
        }
        Peak peak = peakList.getPeakByID(id);
        setPeak(peak);
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

    private void handlePeakListChangedEvent() {
        if (currentPeak != null) {
            updateDeleteStatus();
        }
        peakNavigable.refreshPeakView();
    }

    @Override
    public void peakListChanged(PeakEvent peakEvent) {
        if (peakEvent.getSource() instanceof PeakList sourceList && sourceList == peakList) {
            Fx.runOnFxThread(this::handlePeakListChangedEvent);
        }
    }
}
