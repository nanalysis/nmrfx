/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.gui.tools;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.PeakListTools;
import org.nmrfx.processor.datasets.peaks.PeakPickParameters;
import org.nmrfx.processor.gui.ControllerTool;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PeakPicking;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.ConnectPeakAttributes;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.KeyBindings;
import org.nmrfx.math.BipartiteMatcher;
import org.nmrfx.processor.optimization.PeakCluster;
import org.nmrfx.processor.optimization.PeakClusterMatcher;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * @author Bruce Johnson
 */
public class PeakSlider implements ControllerTool {

    private static final Logger log = LoggerFactory.getLogger(PeakSlider.class);
    private static final String SLIDER_MENU_NAME = "Slider";
    VBox vBox;
    ToolBar sliderToolBar;
    FXMLController controller;
    Consumer<PeakSlider> closeAction;
    Button shiftFreezeButton;
    Button freezeButton;
    Button thawButton;
    Button tweakFreezeButton;
    Button linkButton;
    Button unlinkButton;
    Label atomXFieldLabel;
    Label atomYFieldLabel;
    Label intensityFieldLabel;
    Label atomXLabel;
    Label atomYLabel;
    Label intensityLabel;
    List<Peak> selPeaks;
    PeakClusterMatcher[] matchers = new PeakClusterMatcher[2];
    RandomDataGenerator rand = new RandomDataGenerator();
    private InvalidationListener selectedPeaksListener;
    private final ListChangeListener<PolyChart> chartsListener = this::updateKeyBindings;

    enum PeakDisplayMode {
        NONE,
        SIM,
        EXP,
        BOTH;

        void set(PolyChart chart) {

        }
    }

    public PeakSlider(FXMLController controller, Consumer<PeakSlider> closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
        setupLists(true);
    }

    public VBox getBox() {
        return this.vBox;
    }

    public void close() {
        setupLists(false);
        closeAction.accept(this);
    }

    public void initSlider(VBox vBox) {
        this.vBox = vBox;
        sliderToolBar = new ToolBar();
        vBox.getChildren().add(sliderToolBar);

        ArrayList<Button> buttons = new ArrayList<>();
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", AnalystApp.ICON_SIZE_STR, AnalystApp.REG_FONT_SIZE_STR, ContentDisplay.LEFT);
        closeButton.setOnAction(e -> close());

        shiftFreezeButton = new Button("Shift+Freeze");
        shiftFreezeButton.setOnMouseClicked(e -> evalMatchCriteria(selPeaks));
        buttons.add(shiftFreezeButton);

        freezeButton = new Button("Freeze");
        freezeButton.setOnAction(this::freezePeaks);
        freezeButton.setOnMouseClicked(this::freezePeaks);
        buttons.add(freezeButton);

        thawButton = new Button("Thaw");
        thawButton.setOnAction(this::thawPeaks);
        thawButton.setOnMouseClicked(this::thawPeaks);
        buttons.add(thawButton);

        tweakFreezeButton = new Button("Tweak+Freeze");
        tweakFreezeButton.setOnAction(this::tweakPeaks);
        tweakFreezeButton.setOnMouseClicked(this::tweakPeaks);
        buttons.add(tweakFreezeButton);

        linkButton = new Button("Link");
        linkButton.setOnAction(e -> linkDims());
        buttons.add(linkButton);

        unlinkButton = new Button("UnLink");
        unlinkButton.setOnAction(e -> unlinkDims());
        buttons.add(unlinkButton);

        buttons.forEach(button -> button.getStyleClass().add("toolButton"));

        atomXFieldLabel = new Label("X:");
        atomYFieldLabel = new Label("Y:");
        intensityFieldLabel = new Label("I:");
        atomXLabel = new Label();
        atomXLabel.setMinWidth(55);
        atomYLabel = new Label();
        atomYLabel.setMinWidth(55);
        intensityLabel = new Label();
        intensityLabel.setMinWidth(55);

        MenuButton actionMenu = new MenuButton("Actions");
        MenuItem thawAllItem = new MenuItem("Thaw All");
        thawAllItem.setOnAction(e -> thawAllPeaks());
        MenuItem restoreAllItem = new MenuItem("Restore All Peaks");
        restoreAllItem.setOnAction(e -> restoreAllPeaks(false));
        MenuItem randomizeAllItem = new MenuItem("Randomize All Peaks");
        randomizeAllItem.setOnAction(e -> restoreAllPeaks(true));
        MenuItem restoreItem = new MenuItem("Restore Peaks");
        restoreItem.setOnAction(e -> restorePeaks());
        MenuItem pickItem = new MenuItem("Pick Peaks");
        pickItem.setOnAction(e -> pickDisplayedCharts());
        Menu peakDisplayMenu = new Menu("Peak Lists");
        for (var peakMode : PeakDisplayMode.values()) {
            MenuItem modeMenuItem = new MenuItem(peakMode.name());
            modeMenuItem.setOnAction(e -> setPeakMode(peakMode));
            peakDisplayMenu.getItems().add(modeMenuItem);
        }

        Menu matchingMenu = new Menu("Perform Match");
        MenuItem matchColumnItem = new MenuItem("Do Match Columns");
        matchColumnItem.setOnAction(e -> matchClusters(0, true));
        MenuItem matchRowItem = new MenuItem("Do Match Rows");
        matchRowItem.setOnAction(e -> matchClusters(1, true));
        MenuItem matchExpPredItem = new MenuItem("Match Exp/Pred Lists");
        matchExpPredItem.setOnAction(e -> matchPredWithExpPeakList(false));
        MenuItem clearMatchItem = new MenuItem("Clear Matches");
        clearMatchItem.setOnAction(e -> clearMatches());
        MenuItem autoItem = new MenuItem("Auto");
        autoItem.setOnAction(e -> autoAlign());
        matchingMenu.getItems().addAll(matchColumnItem, matchRowItem, clearMatchItem, autoItem, matchExpPredItem);

        actionMenu.getItems().addAll(thawAllItem, restoreItem, restoreAllItem, randomizeAllItem, pickItem, peakDisplayMenu, matchingMenu);

        Pane filler1 = new Pane();
        HBox.setHgrow(filler1, Priority.ALWAYS);
        Pane filler2 = new Pane();
        filler2.setMinWidth(20);
        Pane filler3 = new Pane();
        filler3.setMinWidth(20);
        Pane filler4 = new Pane();
        filler4.setMinWidth(20);
        Pane filler5 = new Pane();
        HBox.setHgrow(filler5, Priority.ALWAYS);

        sliderToolBar.getItems().add(closeButton);
        sliderToolBar.getItems().add(filler1);
        sliderToolBar.getItems().add(actionMenu);
        sliderToolBar.getItems().addAll(buttons);
        sliderToolBar.getItems().add(filler2);
        sliderToolBar.getItems().addAll(atomXFieldLabel, atomXLabel, filler3, atomYFieldLabel, atomYLabel, filler4, intensityFieldLabel, intensityLabel);
        sliderToolBar.getItems().add(filler5);

        // The different control items end up with different heights based on font and icon size,
        // set all the items to use the same height
        sliderToolBar.heightProperty().addListener((observable, oldValue, newValue) -> GUIUtils.toolbarAdjustHeights(List.of(sliderToolBar)));

        // Setup listeners
        selectedPeaksListener = event -> setActivePeaks(controller.getSelectedPeaks());
        controller.selectedPeaksProperty().addListener(selectedPeaksListener);
        for (PolyChart chart : controller.getCharts()) {
            addKeyBindingsToChart(chart);
        }
        ((ObservableList<PolyChart>) controller.getCharts()).addListener(chartsListener);
    }

    /**
     * Add key bindings to newly added charts.
     *
     * @param change The change to the FXMLController charts list.
     */
    private void updateKeyBindings(ListChangeListener.Change<? extends PolyChart> change) {
        if (change.next()) {
            for (PolyChart chart : change.getAddedSubList()) {
                addKeyBindingsToChart(chart);
            }
        }
    }

    /**
     * Adds keybindings for df, dt and ds to the provided chart as well as adding the slider
     * to the chart's peak menu.
     *
     * @param chart The PolyChart to modify.
     */
    private void addKeyBindingsToChart(PolyChart chart) {
        KeyBindings keyBindings = chart.getKeyBindings();
        keyBindings.registerKeyAction("df", this::freezePeaks);
        keyBindings.registerKeyAction("dt", this::thawPeaks);
        keyBindings.registerKeyAction("ds", this::tweakPeaks);
        addSliderToPeakMenu(chart);
    }

    public void removeListeners() {
        controller.selectedPeaksProperty().removeListener(selectedPeaksListener);
        ((ObservableList<PolyChart>) controller.getCharts()).removeListener(chartsListener);
        for (PolyChart chart : controller.getCharts()) {
            KeyBindings keyBindings = chart.getKeyBindings();
            keyBindings.deregisterKeyAction("df");
            keyBindings.deregisterKeyAction("dt");
            keyBindings.deregisterKeyAction("ds");
            removeSliderFromPeakMenu(chart);
        }
    }

    public void freezePeaks(PolyChart chart) {
        freezePeaks(false);
    }

    public void thawPeaks(PolyChart chart) {
        thawPeaks(false);
    }

    public void tweakPeaks(PolyChart chart) {
        tweakPeaks(false);
    }

    public final void setupLists(final boolean state) {
        controller.getCharts().forEach(chart -> chart.getPeakListAttributes()
                .forEach(peakListAttr -> peakListAttr.getPeakList().setSlideable(state)));
    }

    boolean getAltState(Event event) {
        return (event instanceof MouseEvent mEvent) && mEvent.isAltDown();
    }

    boolean shouldRespond(Event event) {
        boolean shouldRespond = event instanceof ActionEvent;
        if ((event instanceof MouseEvent mEvent) && mEvent.isAltDown()) {
            shouldRespond = true;
        }
        return shouldRespond;
    }

    public void freezePeaks(Event event) {
        if (shouldRespond(event)) {
            freezePeaks(getAltState(event));
        }
    }

    public void freezePeaks(boolean useAllConditions) {
        // do setup because we could have added a peak list after adding slider controller.  Should be a better way
        setupLists(true);
        controller.getCharts().forEach(chart -> {
            List<Peak> selected = chart.getSelectedPeaks();
            selected.forEach(peak -> {
                peak.setFrozen(true, useAllConditions);
                PeakList.notifyFreezeListeners(peak, true);
            });
        });

    }

    public void thawPeaks(Event event) {
        if (shouldRespond(event)) {
            thawPeaks(getAltState(event));
        }
    }

    public void thawPeaks(boolean useAllConditions) {
        // do setup because we could have added a peak list after adding slider controller.  Should be a better way
        setupLists(true);
        controller.getCharts().forEach(chart -> {
            List<Peak> selected = chart.getSelectedPeaks();
            selected.forEach(peak -> {
                peak.setFrozen(false, useAllConditions);
                PeakList.notifyFreezeListeners(peak, false);
            });
        });

    }

    public void thawAllPeaks() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Thaw all peaks?");
        alert.showAndWait().ifPresent(response -> {
            for (PeakList peakList : PeakList.peakLists()) {
                if (peakList.isSimulated()) {
                    peakList.peaks().forEach(peak -> peak.setFrozen(false, true));
                }
            }
        });
    }

    public void tweakPeaks(Event event) {
        if (shouldRespond(event)) {
            tweakPeaks(getAltState(event));
        }
    }

    public void tweakPeaks(boolean useAllConditions) {
        // do setup because we could have added a peak list after adding slider controller.  Should be a better way
        setupLists(true);
        controller.getCharts().forEach(chart -> {
            List<Peak> selected = chart.getSelectedPeaks();
            selected.forEach(peak -> tweakPeak(peak, useAllConditions));
        });
    }

    public void tweakPeak(Peak peak, boolean useAllConditions) {
        Set<Peak> peakSet = new HashSet<>();
        int nDim = peak.getPeakList().getNDim();
        for (int i = 0; i < nDim; i++) {
            List<Peak> peaks = PeakList.getLinks(peak, i);
            peakSet.addAll(peaks);
        }

        List<Peak> peaksB = new ArrayList<>();
        // find all peaks that are linked in all dimensions to original peak
        // These are the peaks that should be tweaked when original peak is tweaked.
        for (Peak speak : peakSet) {
            if (speak == peak) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < nDim; i++) {
                if (!PeakList.isLinked(peak, i, speak)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                peaksB.add(speak);
            }
        }

        PeakList peakList = peak.getPeakList();
        Dataset dataset = Dataset.getDataset(peakList.fileName);
        if (dataset != null) {
            int[] pdim = peakList.getDimsForDataset(dataset, true);
            int nExtraDim = dataset.getNDim() - peakList.getNDim();
            int[] planes = new int[nExtraDim];

            try {
                peak.tweak(dataset, pdim, planes);
                peak.setFrozen(true, false);
                PeakList.notifyFreezeListeners(peak, true);
                for (Peak lPeak : peaksB) {
                    peakList = lPeak.getPeakList();
                    dataset = Dataset.getDataset(peakList.fileName);
                    if (dataset != null) {
                        lPeak.tweak(dataset, pdim, planes);
                    }
                }
                peak.setFrozen(true, useAllConditions);
                PeakList.notifyFreezeListeners(peak, true);
            } catch (IOException ioE) {
                log.warn(ioE.getMessage(), ioE);
            }
        }
    }

    public void restorePeaks() {
        // do setup because we could have added a peak list after adding slider controller.  Should be a better way
        setupLists(true);
        controller.getCharts().forEach(chart -> {
            List<Peak> selected = chart.getSelectedPeaks();
            selected.forEach(peak -> {
                for (PeakDim peakDim : peak.getPeakDims()) {
                    String label = peakDim.getLabel();
                    Atom atom = MoleculeBase.getAtomByName(label);
                    if (atom != null) {
                        Double refPPM = atom.getRefPPM();
                        if (refPPM != null) {
                            peakDim.setChemShift(refPPM.floatValue());
                        }
                    }
                }
                peak.setFrozen(false, true);
            });
        });
    }

    public Map<Atom, Double> getShiftMap(boolean randomize) {
        Molecule mol = Molecule.getActive();
        List<Atom> atoms = mol.getAtoms();
        Map<Atom, Double> shiftMap = new HashMap<>();
        for (Atom atom : atoms) {
            Double refPPM = atom.getRefPPM();
            if (refPPM != null) {
                if (randomize) {
                    Double errPPM = atom.getSDevRefPPM();
                    if (errPPM != null) {
                        refPPM += rand.nextGaussian(0, errPPM * 0.5);
                    }
                }
                shiftMap.put(atom, refPPM);
            }
        }
        return shiftMap;
    }

    public void restoreAllPeaks(boolean randomize) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Move all peaks back to predicted positions?");
        alert.showAndWait().ifPresent(response -> {
            Map<Atom, Double> shiftMap = getShiftMap(randomize);
            for (PeakList peakList : PeakList.peakLists()) {
                if (peakList.isSimulated()) {
                    peakList.peaks().forEach(peak -> {
                        for (PeakDim peakDim : peak.getPeakDims()) {
                            String label = peakDim.getLabel();
                            Atom atom = MoleculeBase.getAtomByName(label);
                            if (atom != null) {
                                Double refPPM = shiftMap.get(atom);
                                if (refPPM != null) {
                                    peakDim.setChemShift(refPPM.floatValue());
                                }
                            }
                        }
                        peak.setFrozen(false, true);
                    });
                }
            }
        });
    }

    public void unlinkDims() {
        if (selPeaks.size() == 1) {
            PeakDim peakDim00 = selPeaks.get(0).getPeakDim(0);
            PeakDim peakDim10 = selPeaks.get(0).getPeakDim(1);
            peakDim00.unLink();
            peakDim00.setFrozen(false);
            peakDim10.unLink();
            peakDim10.setFrozen(false);
        }
    }

    public void linkDims() {
        if (selPeaks.size() == 2) {
            // figure out which dimension to link
            PeakDim peakDim00 = selPeaks.get(0).getPeakDim(0);
            PeakDim peakDim01 = selPeaks.get(1).getPeakDim(0);
            PeakDim peakDim10 = selPeaks.get(0).getPeakDim(1);
            PeakDim peakDim11 = selPeaks.get(1).getPeakDim(1);
            double delta0 = Math.abs(peakDim00.getChemShift() - peakDim01.getChemShift());
            double delta1 = Math.abs(peakDim10.getChemShift() - peakDim11.getChemShift());
            // scale delta ppm between peaks by the bounds of the peaks so we can
            //  compare axes with different nuclei
            double delta0F = Math.abs(delta0 / peakDim00.getBounds());
            double delta1F = Math.abs(delta1 / peakDim10.getBounds());
            PeakDim peakDim0;
            PeakDim peakDim1;

            // the peak dimension with smallest delta between peak is used for linking
            double delta;
            if (delta0F < delta1F) {
                peakDim0 = peakDim00;
                peakDim1 = peakDim01;
                delta = delta0F;
            } else {
                peakDim0 = peakDim10;
                peakDim1 = peakDim11;
                delta = delta1F;
            }

            // fixme  should query user if link should be made
            if (Math.abs(delta) > 2.0) {
                return;

            }

            // format link peak dims should link the unlabelled to the labelled
            if (peakDim1.getLabel().isEmpty()) {
                PeakList.linkPeakDims(peakDim0, peakDim1);
                // force a reset of shifts so new peak gets shifted to the groups shift
                peakDim0.setChemShift(peakDim0.getChemShift());
                peakDim0.setFrozen(peakDim0.isFrozen());
            } else {
                PeakList.linkPeakDims(peakDim1, peakDim0);
                peakDim1.setChemShift(peakDim1.getChemShift());
                peakDim1.setFrozen(peakDim1.isFrozen());
            }
        }
    }

    public void setActivePeaks(List<Peak> peaks) {
        selPeaks = peaks;
        controller.getCharts()
                .forEach(PolyChart::clearPeakPaths);
        if ((peaks == null) || peaks.isEmpty()) {
            atomXLabel.setText("");
            atomYLabel.setText("");
            intensityLabel.setText("");
            shiftFreezeButton.setDisable(true);
            freezeButton.setDisable(true);
            thawButton.setDisable(true);
            tweakFreezeButton.setDisable(true);
            linkButton.setDisable(true);
            unlinkButton.setDisable(false);
        } else {
            // fixme axes could be swapped
            Peak peak = peaks.get(peaks.size() - 1);
            atomXLabel.setText(peak.getPeakDim(0).getLabel());
            intensityLabel.setText(String.format("%.2f", peak.getIntensity()));
            if (peak.getPeakDims().length > 1) {
                atomYLabel.setText(peak.getPeakDim(1).getLabel());
            }
            shiftFreezeButton.setDisable(false);
            freezeButton.setDisable(false);
            thawButton.setDisable(false);
            tweakFreezeButton.setDisable(false);
            if (peaks.size() == 2) {
                linkButton.setDisable(false);
            }
            if (peaks.size() == 1) {
                unlinkButton.setDisable(false);
            }
        }

        if ((peaks != null) && !peaks.isEmpty()) {
            controller.getCharts().forEach(chart -> {
                chart.clearPeakPaths();
                for (PeakClusterMatcher matcher : matchers) {
                    if (matcher != null) {
                        Peak p0 = peaks.get(0);
                        PeakCluster clust2 = matcher.getClusterWithPeak(p0);
                        if (clust2 != null) {
                            PeakCluster[] expClusters = matcher.getExpPeakClus();
                            for (PeakCluster expCluster : expClusters) {
                                if (clust2.isInTol(expCluster)) {
                                    double score = expCluster.comparisonScore(clust2);
                                }
                            }
                        }

                        PeakCluster clus = matcher.getCluster(p0);
                        if (clus != null) {
                            List<ConnectPeakAttributes> matchingPeaks = getPeakMatchingAttrs(clus);
                            matchingPeaks.forEach(pairedPeaksAttrs -> {
                                if (pairedPeaksAttrs != null) {
                                    chart.addPeakPath(pairedPeaksAttrs);
                                }
                            });
                        }
                    }
                }
            });
        }
        controller.redrawChildren();
    }

    List<ConnectPeakAttributes> getPeakMatchingAttrs(PeakCluster clus) {
        List<ConnectPeakAttributes> matchingPeaks = new ArrayList<>();
        PeakCluster pairedClus = clus.getPairedTo();
        if (pairedClus != null) {
            clus.getLinkedPeaks().forEach(pInClus -> {
                for (PeakClusterMatcher matcher : matchers) {
                    if (matcher != null) {
                        Peak mPeak = matcher.getMatchingPeak(pInClus);
                        List<Peak> pair = new ArrayList<>(Arrays.asList(pInClus, mPeak));
                        boolean isColumn = matcher.getMatchDim() == 0;
                        ConnectPeakAttributes connPair = setPeakPairAttrs(isColumn, pair);
                        matchingPeaks.add(connPair);
                    }
                }
            });
        }
        return matchingPeaks;
    }

    ConnectPeakAttributes setPeakPairAttrs(boolean isColumn, List<Peak> pairedPeaks) {
        if (pairedPeaks.isEmpty()) {
            return null;
        }
        Color color = (isColumn) ? Color.ORANGE : Color.BLUE;
        double opacity = (isColumn) ? 0.7 : 0.4;
        double width = (isColumn) ? 3.0 : 4.0;
        ConnectPeakAttributes connPeakAttrs = new ConnectPeakAttributes(pairedPeaks);
        connPeakAttrs.setWidth(width);
        connPeakAttrs.setColor(color.toString(), opacity);
        return connPeakAttrs;
    }

    public void evalMatchCriteria(List<Peak> peaks) {
        if (isMatcherNull()) {
            log.warn("Both matchers must be valid (none should be null)");
            return;
        }
        if (peaks != null && !peaks.isEmpty()) {
            Peak clickedPeak = peaks.get(0);
            boolean c1ClickedPeak = satisfyCriteria1(clickedPeak);
            boolean c2ClickedPeak = satisfyCriteria2(clickedPeak, c1ClickedPeak);
            log.info("\tClicked peak '{}' meets criteria 1 ({}) and 2 ({})", clickedPeak, c1ClickedPeak, c2ClickedPeak);

            if (c1ClickedPeak && c2ClickedPeak) {
                shiftAndFreezePeak(clickedPeak);

                for (PeakClusterMatcher matcher : matchers) {
                    PeakCluster simClus = matcher.getCluster(clickedPeak);
                    freezeClusters(clickedPeak, matcher);

                    simClus.getLinkedPeaks().forEach(p -> { // sim peak
                        if (!p.equals(clickedPeak)) {
                            boolean c1AssocPeak = satisfyCriteria1(p);
                            boolean c2AssocPeak = satisfyCriteria2(p, c1AssocPeak);
                            log.info(String.format("\tPeak '%s' linked to the clicked peak in dimension '%d' satisfies criteria 1 (%s) and 2 (%s)", p, matcher.getMatchDim(), c1AssocPeak, c2AssocPeak));
                            int evalDim = (matcher.getMatchDim() == 0) ? 1 : 0;
                            if (c1AssocPeak && c2AssocPeak) {
                                freezeMatchPeakDim(p, matcher.getMatchingPeak(p), evalDim);
                            } else {
                                PeakCluster bestCluster = calcClusterScores(p, clickedPeak, evalDim);
                                if (bestCluster == null) {
                                    return; // continues to next iteration
                                }
                                log.info(String.format("\tThe cluster containing peak '%s' best matches cluster containing '%s'", p, bestCluster.rootPeak));
                                Peak matchingPeak = matcher.getMatchingPeak(p); // experimental peak matching sim peak
                                if (bestCluster.contains(matchingPeak) && !p.getPeakDim(evalDim).isFrozen()) {
                                    freezeMatchPeakDim(p, matchingPeak, evalDim);
                                }

                            }
                        }
                    });
                }
                printClusterScore(clickedPeak, 0);
                printClusterScore(clickedPeak, 1);
                updateMatchers(true);
            }
        }
    }

    void freezeClusters(Peak clickedPredPeak, PeakClusterMatcher matcher) {
        PeakCluster simClus = matcher.getCluster(clickedPredPeak);
        PeakCluster expClus = simClus.getPairedTo();
        if (expClus != null) {
            simClus.setFreeze(true);
            expClus.setFreeze(true);
        }
    }

    void updateMatchers(boolean drawMatches) {
        clearPeakConnections();
        matchClusters(0, drawMatches);
        matchClusters(1, drawMatches);
    }

    void freezeMatchPeakDim(Peak peakToFreeze, Peak assocPeak, int iDim) {
        if (peakToFreeze == null || assocPeak == null) {
            return;
        }
        PeakDim peakDim = peakToFreeze.getPeakDim(iDim);
        PeakDim assocPeakDim = assocPeak.getPeakDim(iDim);
        if (peakDim != null && assocPeakDim != null && !peakDim.isFrozen()) {
            peakDim.setChemShiftValue(assocPeakDim.getChemShiftValue());
            peakDim.setFrozen(true);
        }
        boolean allDimsFrozen = true;
        for (PeakDim p : peakToFreeze.getPeakDims()) {
            if (!p.isFrozen()) {
                allDimsFrozen = false;
                break;
            }
        }
        if (allDimsFrozen) {
            PeakList.notifyFreezeListeners(peakToFreeze, false);
        }
    }

    void shiftMatchPeakDim(Peak peak) {
        if (isMatcherNull()) {
            return;
        }
        for (PeakClusterMatcher matcher : matchers) {
            PeakCluster pClus = matcher.getCluster(peak);
            int evalDim = pClus.iDim;
            pClus.getLinkedPeaks().forEach(p -> {
                if (!p.equals(peak)) {
                    freezeMatchPeakDim(p, peak, evalDim);
                }
            });
        }
    }

    // FIXME: ASSUMES CRITERIA 1 AND 2 ARE MET. SHOULDN'T RUN INDEPENDENTLY.
    public void shiftAndFreezePeak(Peak p0) {
        if (isMatcherNull()) {
            return;
        }
        PeakClusterMatcher colMatcher = matchers[0];
        Peak p0MatchPeak = colMatcher.getMatchingPeak(p0);
        // shift and setFreeze the selected peak
        for (int i = 0; i < p0.peakDims.length; i++) {
            freezeMatchPeakDim(p0, p0MatchPeak, i);
        }
        // shift all other peaks based on selected peak
        shiftMatchPeakDim(p0);
    }

    /**
     * Criteria #1: Given a selected peak 1) Peak must be a simulated peak 2)
     * Both row and column matchers must've been instantiated 3) Both the row
     * and column peak matches for the given peak must be the same, e.g:
     * simulated peak must match the same experimental peak in both row and
     * column peak matches
     */
    boolean satisfyCriteria1(Peak peak) {
        boolean criteria1Met = false;
        if (peak.getPeakList().isSimulated() && matchers.length == 2) {
            Peak tempMatchingPeak = null;
            for (PeakClusterMatcher matcher : matchers) {
                Peak currentMatchingPeak = matcher.getMatchingPeak(peak);
                if (currentMatchingPeak != null) {
                    if (tempMatchingPeak == null) {
                        tempMatchingPeak = currentMatchingPeak;
                        continue;
                    }
                    if (currentMatchingPeak.equals(tempMatchingPeak)) {
                        criteria1Met = true;
                    }
                }
            }
        }
        return criteria1Met;
    }

    /**
     * Criteria #2: Given a selected peak 1) Peak must have passed criteria #1
     * 2) Experimental (exp) peak matched to given simulated (sim) peak doesn't
     * have other exp peaks within a 0.10 ppm tolerance 3) If #2 is false, then
     * calculate the weights b/t sim peak and the exp peaks within tol and
     * display them
     */
    boolean satisfyCriteria2(Peak peak, boolean criteria1Met) {
        boolean criteria2Met = false;
        if (criteria1Met) {
            for (PeakClusterMatcher matcher : matchers) {
                double tolerance = 0.10;
                int matchDim = matcher.getMatchDim();
                int dim = (matchDim == 0) ? 1 : 0;
                Peak matchingPeak = matcher.getMatchingPeak(peak); // experimental peak
                if (matchingPeak != null) {
                    List<Peak> peaksInClus = matcher.getCluster(matchingPeak).getLinkedPeaks();
                    List<Peak> peaksWithinTol = new ArrayList<>();
                    double matchPeakShift = matchingPeak.getPeakDim(dim).getChemShift();
                    peaksInClus.forEach(p -> {
                        double peakShift = p.getPeakDim(dim).getChemShift();
                        double shiftDiff = Math.abs(peakShift - matchPeakShift);
                        log.info(String.format("\t\tIn dimension '%d', shift difference b/t experimental peak '%s' compared to peak '%s' is '%f'.", matchDim, matchingPeak, p, shiftDiff));
                        if ((shiftDiff > 0.0) && (shiftDiff < tolerance)) {
                            peaksWithinTol.add(p);
                        }
                    });
                    if (peaksWithinTol.isEmpty()) {
                        criteria2Met = true;
                    } else {
                        peaksWithinTol.forEach(peakInTol -> {
                            double w = PeakCluster.calcWeight(peak, peakInTol, peak.getPeakList().scale);
                            log.info(String.format("\t\t* Predicted peak '%s' has experimental peak '%s' within a '%f' ppm tolerance. The weight b/t the 2 peaks is '%f'.", peak, peakInTol, tolerance, w));
                        });
                    }
                }
            }
        }
        return criteria2Met;
    }

    void printClusterScore(Peak predPeak, int iDim) {
        if (isMatcherNull()) {
            return;
        }
        String label = (iDim == 1) ? "Row" : "Column";
        int jDim = (iDim == 1) ? 0 : 1;
        PeakClusterMatcher iMatcher = matchers[iDim];
        PeakClusterMatcher jMatcher = matchers[jDim];
        log.info("\n\n{} Cluster Score Calculation", label);
        PeakCluster iPredClus = iMatcher.getCluster(predPeak);
        Peak expPeak = iMatcher.getMatchingPeak(predPeak);
        List<Peak> peaksPairedToExp = jMatcher.getCluster(expPeak).getLinkedPeaks();

        peaksPairedToExp.stream()
                .map(assocExpPeak -> {
                    PeakCluster jAssocExpClus = iMatcher.getCluster(assocExpPeak);
                    log.info(String.format("Weights b/t predicted %s cluster of '%s' and experimental cluster containing '%s'", label, predPeak, assocExpPeak));

                    iPredClus.getLinkedPeaks()
                            .forEach(predP -> {
                                if (jAssocExpClus != null) {
                                    jAssocExpClus.getLinkedPeaks()
                                            .forEach(eP -> {
                                                double w = PeakCluster.calcWeight(predP, eP, predP.getPeakList().scale);
                                                log.info(String.format("\tPred Peak ('%s') and Exp Peak ('%s'), weight : '%f'", predP, eP, w));
                                            });
                                } else {
                                    log.info("Associated peak '{}' returned a null cluster.", assocExpPeak);
                                }
                            });
                    return jAssocExpClus;
                })
                .map(iPredClus::comparisonScore)
                .forEachOrdered(clusScore -> log.info("Cluster score: {}", clusScore));
    }

    /**
     * Given a peak, calculate scores between clusters and retrieve the cluster
     * with best score.
     *
     * @param assocPeak (simulated peak)
     * @param iDim      (Dimension [0 (column) or 1 (row)])
     * @return bestPairedPeakClus (Cluster with best score, or null)
     */
    PeakCluster calcClusterScores(Peak assocPeak, Peak clickedPeak, int iDim) {
        if (isMatcherNull()) {
            return null;
        }
        PeakCluster bestPairedPeakClus = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        PeakClusterMatcher colMatcher = matchers[0];
        PeakClusterMatcher rowMatcher = matchers[1];
        boolean doRow = iDim == 1;
        PeakCluster p1Clus = (doRow)
                ? rowMatcher.getCluster(assocPeak) : colMatcher.getCluster(assocPeak);
        Peak clickedMatchPeak = (doRow)
                ? rowMatcher.getMatchingPeak(clickedPeak) : colMatcher.getMatchingPeak(clickedPeak);
        if (p1Clus != null && clickedMatchPeak != null) {
            PeakCluster matchPeakClus = (doRow)
                    ? colMatcher.getCluster(clickedMatchPeak) : rowMatcher.getCluster(clickedMatchPeak);
            List<Peak> matchPeakLinkedList = (matchPeakClus != null)
                    ? matchPeakClus.getLinkedPeaks() : (new ArrayList<>());
            for (Peak p : matchPeakLinkedList) {
                PeakCluster pClus = (doRow)
                        ? rowMatcher.getCluster(p) : colMatcher.getCluster(p);
                double currScore = p1Clus.comparisonScore(pClus);
                if (bestPairedPeakClus == null || currScore > bestScore) {
                    bestScore = currScore;
                    bestPairedPeakClus = pClus;
                }
            }
        }
        return ((bestScore >= 0) ? bestPairedPeakClus : null);
    }

    public void matchClusters(boolean drawMatches) {
        matchClusters(0, drawMatches);
        matchClusters(1, drawMatches);
    }

    public void matchClusters(int iDim, boolean drawMatches) {
        // storing peak list attributes which contain information about the peak lists
        // in each chart.
        if (matchers[iDim] == null) {
            createNewMatcher(iDim);
        }
        PeakClusterMatcher matcher = matchers[iDim];

        matcher.runMatch();

        if (drawMatches) {
            drawAllMatches(iDim);
        }
    }

    void createNewMatcher(int iDim) {
        List<PeakList> predLists = new ArrayList<>();
        List<PeakList> expLists = new ArrayList<>();
        controller.getCharts().forEach(chart -> {
                    for (DatasetAttributes dataAttr : chart.getDatasetAttributes()) {
                        double xMin = chart.getAxes().getX().getLowerBound();
                        double xMax = chart.getAxes().getX().getUpperBound();
                        double yMin = chart.getAxes().getY().getLowerBound();
                        double yMax = chart.getAxes().getY().getUpperBound();
                        double[][] limits = {{xMin, xMax}, {yMin, yMax}};
                        Optional<PeakList> expListOpt = Optional.empty();
                        Optional<PeakList> predListOpt = Optional.empty();
                        for (PeakList peakList : ProjectBase.getActive().getPeakLists()) {
                            if (peakList.getDatasetName().equals(dataAttr.getDataset().getName())) {
                                if (peakList.isSimulated()) {
                                    predListOpt = Optional.of(peakList);
                                } else {
                                    expListOpt = Optional.of(peakList);
                                }
                            }
                        }
                        if (expListOpt.isPresent() && predListOpt.isPresent()) {
                            PeakCluster.prepareList(expListOpt.get(), limits);
                            PeakCluster.prepareList(predListOpt.get(), limits);
                            expLists.add(expListOpt.get());
                            predLists.add(predListOpt.get());
                            log.info("create {} {} {}", iDim, expListOpt.get().getName(), predListOpt.get().getName());
                        }
                    }
                }
        );
        matchers[iDim] = new PeakClusterMatcher(expLists, predLists, iDim);

    }

    void drawAllMatches(int iDim) {
        controller.getCharts().stream()
                .filter(chart -> chart.getPeakListAttributes().size() == 2)
                .forEach(chart -> {
                    PeakClusterMatcher matcher = matchers[iDim];
                    if (matcher != null) {
                        List<PeakCluster[]> clusterMatches = matcher.getClusterMatch(); // list of matched clusters
                        if (clusterMatches != null) {
                            clusterMatches.stream()
                                    .map(clusMatch -> {
                                        PeakCluster expCluster = clusMatch[0];
                                        PeakCluster predCluster = clusMatch[1];
                                        return expCluster.getPeakMatches(predCluster);
                                    })
                                    .forEachOrdered(matchingPeaks -> matchingPeaks.forEach(pairedPeaks -> {
                                                boolean isColumn = matcher.getMatchDim() == 0;
                                                ConnectPeakAttributes connPeakAttrs = setPeakPairAttrs(isColumn, pairedPeaks);
                                                if (connPeakAttrs != null) {
                                                    chart.addPeakPath(connPeakAttrs);
                                                }
                                            }
                                    ));
                            chart.drawPeakLists(true);
                        }
                    }
                });

    }

    public void clearMatches() {
        Arrays.fill(matchers, null);
        clearPeakConnections();
    }

    public void clearPeakConnections() {
        controller.getCharts().forEach(chart -> {
            chart.clearPeakPaths();
            chart.refresh();
        });
    }

    boolean isMatcherNull() {
        boolean isNull = false;
        for (PeakClusterMatcher matcher : matchers) {
            if (matcher == null) {
                isNull = true;
                break;
            }
        }
        return isNull;
    }

    record MatchListPair(PeakList refList, PeakList movingList) {
    }

    Optional<MatchListPair> createNDMatcher() {
        PolyChart chart = controller.getActiveChart();
        List<PeakList> movingLists = new ArrayList<>();
        List<PeakList> refLists = new ArrayList<>();
        boolean gotMoving = false;
        if (chart.getPeakListAttributes().size() == 1) {
            movingLists.add(chart.getPeakListAttributes().get(0).getPeakList());
            gotMoving = true;
        }
        for (DatasetAttributes dataAttr : chart.getDatasetAttributes()) {
            for (PeakList peakList : ProjectBase.getActive().getPeakLists()) {
                if (peakList.getDatasetName().equals(dataAttr.getDataset().getName())) {
                    if (peakList.isSimulated()) {
                        if (!gotMoving) {
                            movingLists.add(peakList);
                        }
                    } else {
                        refLists.add(peakList);
                    }
                }
            }
        }
        Optional<MatchListPair> result;
        if (movingLists.isEmpty()) {
            GUIUtils.warn("Peak Matching", "No Simulated List found");
            result = Optional.empty();
        }
        if ((movingLists.size() == 1) && (refLists.size() == 1)) {
            var pair = new MatchListPair(refLists.get(0), movingLists.get(0));
            result = Optional.of(pair);
        } else {
            var pair = getMatchingPeakLists(refLists, movingLists);
            result = Optional.of(pair);
        }
        return result;
    }

    void matchPredWithExpPeakList(boolean draw) {
        Optional<MatchListPair> optionalPair = createNDMatcher();
        optionalPair.ifPresent(matchListPair -> {
            int[] dims = new int[matchListPair.refList.getNDim()];
            for (int i = 0; i < dims.length; i++) {
                dims[i] = i;
            }
            List<Peak[]> peakMatches = PeakListTools.getExpPredMatches(matchListPair.movingList, matchListPair.refList, dims, null);
            if (draw) {
                for (var peakMatch : peakMatches) {
                    List<Peak> pairedPeaks = List.of(peakMatch[0], peakMatch[1]);
                    ConnectPeakAttributes connPeakAttrs = setPeakPairAttrs(false, pairedPeaks);
                    if (connPeakAttrs != null) {
                        controller.getActiveChart().addPeakPath(connPeakAttrs);
                    }
                }
                controller.getActiveChart().drawPeakLists(true);
            } else {
                PeakListTools.shiftAndFreezePeakList(peakMatches, dims);
            }
        });
    }

    record PeakListPair(PeakList experiental, PeakList predicted) {
    }

    public void autoAlign() {
        Optional<PeakList> hmqcPredListOpt = Optional.empty();
        Optional<PeakList> hmqcExpListOpt = Optional.empty();
        Optional<PeakList> tocsyPredListOpt = Optional.empty();
        Optional<PeakList> tocsyExpListOpt = Optional.empty();
        Optional<PeakList> noesyPredListOpt = Optional.empty();
        Optional<PeakList> noesyExpListOpt = Optional.empty();
        for (PeakList peakList : ProjectBase.getActive().getPeakLists()) {
            if (peakList.getExperimentType().equalsIgnoreCase("13C-HSQC") || peakList.getName().contains("hmqc")) {
                if (peakList.isSimulated()) {
                    hmqcPredListOpt = Optional.of(peakList);
                } else {
                    hmqcExpListOpt = Optional.of(peakList);
                }
            } else if (peakList.getExperimentType().equalsIgnoreCase("COSY") || peakList.getName().contains("tocsy") || peakList.getName().contains("mlev")) {
                if (peakList.isSimulated()) {
                    tocsyPredListOpt = Optional.of(peakList);
                } else {
                    tocsyExpListOpt = Optional.of(peakList);
                }
            } else if (peakList.getExperimentType().equalsIgnoreCase("NOESY") || peakList.getName().contains("noesy")) {
                if (peakList.isSimulated()) {
                    noesyPredListOpt = Optional.of(peakList);
                } else {
                    noesyExpListOpt = Optional.of(peakList);
                }
            }
        }
        log.debug("{}", hmqcPredListOpt);
        log.debug("{}", hmqcExpListOpt);
        log.debug("{}", tocsyPredListOpt);
        log.debug("{}", tocsyExpListOpt);
        log.debug("{}", noesyPredListOpt);
        log.debug("{}", noesyExpListOpt);
        double[] tocsyScale = {0.5, 0.5};
        double[] hmqcScale = {0.5, 5.0};
        if (hmqcExpListOpt.isPresent() && hmqcPredListOpt.isPresent() &&
                tocsyExpListOpt.isPresent() && tocsyPredListOpt.isPresent() &&
                noesyExpListOpt.isPresent() && noesyPredListOpt.isPresent()

        ) {
            alignPeakList(hmqcPredListOpt.get(), hmqcExpListOpt.get(),
                    tocsyPredListOpt.get(), tocsyExpListOpt.get(),
                    noesyPredListOpt.get(), noesyExpListOpt.get(),
                    hmqcScale, tocsyScale);

        }
    }

    BipartiteMatcher compareHMQC(PeakList hmqcPred, PeakList hmqcExp) {

        BipartiteMatcher matcher = new BipartiteMatcher();
        int N = hmqcPred.size() + hmqcExp.size();
        matcher.reset(N, true);
        // init
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                matcher.setWeight(i, j, 0.0);
            }
        }

        int sizeE = hmqcExp.size();
        int sizeP = hmqcPred.size();
        double[] scale = {0.5, 10.0};

        for (int iE = 0; iE < sizeE; iE++) {
            for (int jP = 0; jP < sizeP; jP++) {
                Peak expPeak = hmqcExp.getPeak(iE);
                Peak predPeak = hmqcPred.getPeak(jP);
                double distance = expPeak.distance(predPeak, scale);
                double weight = 0.0;
                if (distance < 1.0) {
                    weight = 1.0 - distance;
                }
                matcher.setWeight(iE, jP, weight);
            }
        }
        return matcher;
    }

    double calcNOEClusterAdj(PeakList predNOEPeakList, Peak predHMQCPeak, Peak expHMQCPeak) {
        double maxScore = Double.NEGATIVE_INFINITY;
        List<PeakDim> peakDims = PeakList.getLinkedPeakDims(predHMQCPeak, 0);
        for (PeakDim peakDim : peakDims) {
            if (peakDim.getPeakList() == predNOEPeakList) {
                PeakClusterMatcher matcher = matchers[peakDim.getSpectralDim()];
                PeakCluster peakCluster = matcher.getClusterWithPeak(peakDim.getPeak());
                if (peakCluster != null) {
                    peakCluster.setShift(expHMQCPeak.getPeakDim(0).getChemShiftValue());
                    PeakCluster[] expClusters = matcher.getExpPeakClus();
                    for (PeakCluster expCluster : expClusters) {
                        if (peakCluster.isInTol(expCluster)) {
                            double score = expCluster.comparisonScore(peakCluster);
                            if (score > maxScore) {
                                maxScore = score;
                            }
                        }
                    }
                    peakCluster.restoreShift();
                    break;
                }
            }
        }

        return maxScore;
    }

    double calcTOCSYAdj(PeakList predTOCSYList, PeakList expTOCSYList,
                        Peak predHMQCPeak, Peak expHMQCPeak,
                        double[] tocsyScale, double smallTol) {
        double weightAdj = 1.0;
        double[][] limits = new double[2][2];

        if (predHMQCPeak.getPeakDim(0).getLabel().endsWith("H5") || predHMQCPeak.getPeakDim(0).getLabel().endsWith("H6")) {
            double expPPM = expHMQCPeak.getPeakDim(0).getChemShiftValue();
            List<PeakDim> peakDims = PeakList.getLinkedPeakDims(predHMQCPeak, 0);
            for (PeakDim peakDim : peakDims) {
                if (peakDim.getPeakList() == predTOCSYList) {
                    Peak predTOCSYPeak = peakDim.getPeak();
                    double[] ppms = {predTOCSYPeak.getPeakDim(0).getChemShiftValue(),
                            predTOCSYPeak.getPeakDim(1).getChemShiftValue()};
                    int ppmDim;
                    double origPPM;
                    if (peakDim.getSpectralDim() == 1) {
                        limits[0][1] = ppms[0] - tocsyScale[0];
                        limits[0][0] = ppms[0] + tocsyScale[0];
                        limits[1][1] = expPPM - smallTol;
                        limits[1][0] = expPPM + smallTol;
                        ppmDim = 0;
                        origPPM = ppms[0];
                    } else {
                        limits[0][1] = expPPM - smallTol;
                        limits[0][0] = expPPM + smallTol;
                        limits[1][1] = ppms[1] - tocsyScale[1];
                        limits[1][0] = ppms[1] + tocsyScale[1];
                        ppmDim = 1;
                        origPPM = ppms[1];
                    }
                    int[] searchDims = {0, 1};
                    List<Peak> nearPeaks = expTOCSYList.locatePeaks(limits, searchDims);
                    if (!nearPeaks.isEmpty()) {
                        double ppmNear = nearPeaks.get(0).getPeakDim(ppmDim).getChemShiftValue();
                        weightAdj = Math.abs(ppmNear - origPPM) / tocsyScale[0];
                    }
                }
            }
        }
        return weightAdj;
    }

    void alignTOCSY(PeakList predTOCSYList, PeakList expTOCSYList, double[] tocsyScale) {

        BipartiteMatcher matcher = new BipartiteMatcher();
        int N = predTOCSYList.size() + expTOCSYList.size();
        matcher.reset(N, true);
        // init
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                matcher.setWeight(i, j, 0.0);
            }
        }
        int sizeE = expTOCSYList.size();
        int sizeP = predTOCSYList.size();

        for (int iE = 0; iE < sizeE; iE++) {
            for (int jP = 0; jP < sizeP; jP++) {
                Peak expPeak = expTOCSYList.getPeak(iE);
                Peak predPeak = predTOCSYList.getPeak(jP);
                double distance = expPeak.distance(predPeak, tocsyScale); // if predPeak frozen tighten tolerance
                double weight = -1.0;
                if ((distance < 1.0) && (expPeak.getIntensity() > 0.0)) {
                    weight = 2.0 - distance;
                }
                matcher.setWeight(jP, iE, weight);
            }
        }
        int[] matching = matcher.getMatching();
        for (int i = 0; i < sizeP; i++) {
            int match = matching[i];
            if ((match >= 0) && (match < sizeE)) {
                Peak predPeak = predTOCSYList.getPeak(i);
                Peak expPeak = expTOCSYList.getPeak(match);
                for (int dim = 0; dim < 2; dim++) {
                    predPeak.getPeakDim(dim).setChemShiftValue(
                            expPeak.getPeakDim(dim).getChemShiftValue());
                }
            }
        }
    }

    void alignPeakList(PeakList predHMQCList, PeakList expHMQCList,
                       PeakList predTOCSYList, PeakList expTOCSYList,
                       PeakList predNOESYList, PeakList expNOESYList,
                       double[] scale, double[] tocsyScale) {
        try {
            alignTOCSY(predTOCSYList, expTOCSYList, tocsyScale);
            PolyChart.setPeakListenerState(false);
            createNewMatcher(0);
            matchers[0].setupClusters();
            createNewMatcher(1);
            matchers[1].setupClusters();

            BipartiteMatcher matcher = new BipartiteMatcher();
            int nPred = predHMQCList.size() + expHMQCList.size();
            matcher.reset(nPred, true);
            // init
            for (int i = 0; i < nPred; i++) {
                for (int j = 0; j < nPred; j++) {
                    matcher.setWeight(i, j, 0.0);
                }
            }

            int sizeE = expHMQCList.size();
            int sizeP = predHMQCList.size();
            double smallTol = expTOCSYList.widthStatsPPM(0).getAverage() / 2.0;
            for (int iE = 0; iE < sizeE; iE++) {
                for (int jP = 0; jP < sizeP; jP++) {
                    Peak expPeak = expHMQCList.getPeak(iE);
                    Peak predPeak = predHMQCList.getPeak(jP);
                    double distance = expPeak.distance(predPeak, scale); // if predPeak frozen tighten tolerance
                    double weight = -1.0;
                    if ((distance < 1.0) && (expPeak.getIntensity() > 0.0)) {
                        weight = 2.0 - distance;
                        double weightAdj = calcTOCSYAdj(predTOCSYList, expTOCSYList, predPeak, expPeak, tocsyScale, smallTol);
                        double noeAdj = calcNOEClusterAdj(predNOESYList, predPeak, expPeak);
                        noeAdj /= 30.0;
                        weight -= weightAdj;
                        weight += noeAdj;
                    }
                    matcher.setWeight(jP, iE, weight);
                }
            }
            int[] matching = matcher.getMatching();
            double minWeight = matcher.getMinWeight();

            double score = matcher.getMaxWtSum(matching, minWeight);
            log.debug("aligned {}", score);

            for (int i = 0; i < sizeP; i++) {
                int match = matching[i];
                if ((match >= 0) && (match < sizeE)) {
                    Peak predPeak = predHMQCList.getPeak(i);
                    Peak expPeak = expHMQCList.getPeak(match);
                    for (int dim = 0; dim < 2; dim++) {
                        predPeak.getPeakDim(dim).setChemShiftValue(
                                expPeak.getPeakDim(dim).getChemShiftValue());
                        predPeak.getPeakDim(dim).setFrozen(true);
                    }
                }
            }
        } finally {
            PolyChart.setPeakListenerState(true);
            for (FXMLController fxmlController : AnalystApp.getFXMLControllerManager().getControllers()) {
                fxmlController.redrawChildren();
            }
        }
    }

    void addSliderToPeakMenu(PolyChart chart) {
        boolean hasSliderMenu = false;
        ContextMenu menu = chart.getPeakMenu().chartMenu;
        for (var menuItem : menu.getItems()) {
            if (menuItem.getText().equals(SLIDER_MENU_NAME)) {
                hasSliderMenu = true;
                break;
            }
        }
        if (!hasSliderMenu) {
            Menu cascade = new Menu(SLIDER_MENU_NAME);
            menu.getItems().add(cascade);
            MenuItem tweakFreezeItem = new MenuItem("Tweak & Freeze");
            tweakFreezeItem.setOnAction(this::tweakPeaks);
            MenuItem thawItem = new MenuItem("Thaw");
            thawItem.setOnAction(this::thawPeaks);
            MenuItem freezeItem = new MenuItem("Freeze");
            freezeItem.setOnAction(this::freezePeaks);

            cascade.getItems().addAll(tweakFreezeItem, thawItem, freezeItem);
        }

    }

    private void removeSliderFromPeakMenu(PolyChart chart) {
        MenuItem sliderMenuItem = null;
        ContextMenu menu = chart.getPeakMenu().chartMenu;
        for (var menuItem : menu.getItems()) {
            if (menuItem.getText().equals(SLIDER_MENU_NAME)) {
                sliderMenuItem = menuItem;
                break;
            }
        }
        if (sliderMenuItem != null) {
            menu.getItems().remove(sliderMenuItem);
        }
    }

    public static MatchListPair getMatchingPeakLists(List<PeakList> refLists, List<PeakList> movingLists) {
        Dialog<MatchListPair> dialog = new Dialog<>();
        dialog.setTitle("Peak List Match");
        dialog.setHeaderText("Enter peaklists:");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        dialog.getDialogPane().setContent(grid);
        int comboBoxWidth = 200;
        ComboBox<PeakList> comboBoxRef = new ComboBox<>(FXCollections.observableArrayList(refLists.stream().toList()));
        comboBoxRef.setValue(refLists.get(0));
        comboBoxRef.setMinWidth(comboBoxWidth);
        comboBoxRef.setMaxWidth(comboBoxWidth);
        grid.add(new Label("Reference List"), 0, 0);
        grid.add(comboBoxRef, 1, 0);

        ComboBox<PeakList> comboBoxMoving = new ComboBox<>(FXCollections.observableArrayList(movingLists.stream().toList()));
        comboBoxMoving.setValue(movingLists.get(0));
        comboBoxMoving.setMinWidth(comboBoxWidth);
        comboBoxMoving.setMaxWidth(comboBoxWidth);
        grid.add(new Label("Moving List"), 0, 1);
        grid.add(comboBoxMoving, 1, 1);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                // The value set in the formatter may not have been set yet so commit the value before retrieving
                comboBoxRef.commitValue();
                comboBoxMoving.commitValue();
                return new MatchListPair(comboBoxRef.getValue(), comboBoxMoving.getValue());
            }
            return null;
        });

        MatchListPair gd = null;
        Optional<MatchListPair> result = dialog.showAndWait();
        if (result.isPresent()) {
            gd = result.get();
        }
        return gd;
    }

    private void setPeakMode(PeakDisplayMode mode) {
        for (PolyChart chart : controller.getCharts()) {
            Dataset dataset = (Dataset) chart.getDataset();
            String listName = PeakList.getNameForDataset(dataset.getName());
            final String expName;
            final String simName;
            if (listName.endsWith("_sim")) {
                simName = listName;
                expName = listName.substring(0, listName.indexOf("_sim"));
            } else {
                simName = listName + "_sim";
                expName = listName;
            }
            PeakList expList = PeakList.get(expName);
            PeakList simList = PeakList.get(simName);
            List<PeakList> peakLists = new ArrayList<>();
            switch (mode) {
                case EXP -> {
                    if (expList != null) peakLists.add(expList);
                }
                case SIM -> {
                    if (simList != null) peakLists.add(simList);
                }

                case BOTH -> {
                    if (expList != null) peakLists.add(expList);
                    if (simList != null) peakLists.add(simList);
                }
                case NONE -> {
                }
            }
            chart.updatePeakLists(peakLists);
            chart.refresh();
            setupLists(true);
        }
    }

    private void pickDisplayedCharts() {
        if (!GUIUtils.affirm("Pick all charts\nthis will remove existing peaklists")) {
            return;
        }
        var existingLists = PeakList.peakLists().stream().filter(peakList -> !peakList.isSimulated()).toList();
        for (PeakList peakList : existingLists) {
            peakList.remove();
        }
        List<PeakList> peakLists = new ArrayList<>();
        for (PolyChart chart : controller.getCharts()) {
            Dataset dataset = (Dataset) chart.getDataset();
            PeakPickParameters peakPickParameters = new PeakPickParameters();
            peakPickParameters.level(chart.getDatasetAttributes().get(0).getLvl());
            peakPickParameters.mode = PeakPickParameters.PickMode.APPENDIF;
            PeakList peaklist = PeakPicking.peakPickActive(chart, chart.getDatasetAttributes().get(0),
                    null, peakPickParameters);
            String listName = peaklist.getName().toLowerCase();
            String type = "";
            if (listName.contains("noesy")) {
                type = "NOESY";
            } else if (listName.contains("mlev")) {
                type = "COSY";
            } else if (listName.contains("cosy")) {
                type = "COSY";
            } else if (listName.contains("tocsy")) {
                type = "COSY";
            } else if (listName.contains("hmqc")) {
                type = "13C-HSQC";
            }
            peaklist.setExperimentType(type);
            peakLists.add(peaklist);
            peaklist.setSlideable(true);
        }
        double widthScale = 0.25;

        for (int iDim = 0;iDim < 2;iDim++) {
            double widthPPM = Double.MAX_VALUE;
            for (PeakList peakList : peakLists) {
                DescriptiveStatistics dStat = peakList.widthDStats(iDim);
                widthPPM = Math.min( widthPPM, dStat.getPercentile(50.0) / peakList.getSpectralDim(iDim).getSf());
            }
            PeakListTools.clusterPeakLists(peakLists, iDim, widthPPM * widthScale);
        }
    }
}
