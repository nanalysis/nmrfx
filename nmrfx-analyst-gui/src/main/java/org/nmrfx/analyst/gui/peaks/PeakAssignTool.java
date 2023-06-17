package org.nmrfx.analyst.gui.peaks;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.peaks.AtomBrowser.AtomDelta;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.*;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.ControllerTool;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.utils.GUIUtils;

import java.util.*;
import java.util.function.Consumer;

/**
 * @author Bruce Johnson
 */
public class PeakAssignTool implements ControllerTool {

    FXMLController controller;
    Consumer<PeakAssignTool> closeAction;
    VBox vBox;
    ToolBar toolBar;
    GridPane gridPane;
    ComboBox<String>[] atomChoices;
    TextField[] atomChoicesTF;
    Map<String, AtomDelta>[] atomDeltaMaps;
    Label[] ppmLabels;
    Peak selPeak = null;
    int[] peakDims;
    int nFields;
    boolean removePeakOnClose = false;
    ASSIGN_MODE mode = ASSIGN_MODE.SIMPLE;

    enum ASSIGN_MODE {
        SIMPLE;
    }

    public PeakAssignTool(FXMLController controller, Consumer<PeakAssignTool> closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
    }

    public VBox getBox() {
        return vBox;
    }

    public void close() {
        closeAction.accept(this);
    }

    public void initialize(VBox vBox) {
        this.vBox = vBox;
        toolBar = new ToolBar();
        this.vBox.getChildren().add(toolBar);
        Button pickButton = new Button("Assign");
        pickButton.setOnAction(e -> doAssign());

        int nDim = 2;
        PolyChart chart = controller.getActiveChart();
        if (chart != null) {
            Dataset dataset = (Dataset) chart.getDataset();
            if (dataset != null) {
                nDim = dataset.getNDim();
            }
        }
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", AnalystApp.ICON_SIZE_STR, AnalystApp.REG_FONT_SIZE_STR, ContentDisplay.LEFT);
        closeButton.setOnAction(e -> close());
        toolBar.getItems().add(closeButton);
        toolBar.getItems().add(pickButton);
        gridPane = new GridPane();
        gridPane.setHgap(5);
        toolBar.getItems().add(gridPane);
        updateGrid(nDim);
        controller.selectedPeaksProperty().addListener(e -> setActivePeaks(controller.getSelectedPeaks()));

        // The different control items end up with different heights based on font and icon size,
        // set all the items to use the same height
        List<Node> items = new ArrayList<>(Arrays.asList(closeButton, pickButton));
        items.addAll(gridPane.getChildren());
        toolBar.heightProperty().addListener(
                (observable, oldValue, newValue) -> GUIUtils.nodeAdjustHeights(items));

    }

    void updateGrid(int nDim) {
        double width1 = 200;
        double width2 = 125;
        atomChoices = new ComboBox[nDim];
        atomChoicesTF = new TextField[nDim];
        ppmLabels = new Label[nDim];
        atomDeltaMaps = new HashMap[nDim];
        gridPane.getChildren().clear();
        nFields = nDim;
        for (int iDim = 0; iDim < nDim; iDim++) {
            final int jDim = iDim;
            Label ppmLabel = new Label(" ppm");
            ppmLabel.setPrefWidth(width1);
            ChoiceBox<String> entityChoice = new ChoiceBox<>();

            entityChoice.setPrefWidth(width2);
            TextField atomField = new TextField();
            atomField.setPrefWidth(width1 - width2);
            gridPane.add(ppmLabel, iDim, 0, 1, 1);
            if (mode == ASSIGN_MODE.SIMPLE) {
                TextField atomChoice = new TextField();
                atomChoicesTF[iDim] = atomChoice;
                gridPane.add(atomChoice, iDim, 1, 1, 1);
                atomChoicesTF[iDim].setOnKeyReleased(e -> {
                    if (e.getCode() == KeyCode.ENTER) {
                        handleEnter(jDim);
                    }
                });
            } else {
                ComboBox<String> atomChoice = new ComboBox<>();
                atomChoice.setEditable(true);
                atomChoice.setPrefWidth(width1);
                gridPane.add(atomChoice, iDim, 1, 1, 1);
                atomChoices[iDim] = atomChoice;
                atomChoices[iDim].setOnKeyReleased(e -> {
                    if (e.getCode() == KeyCode.ENTER) {
                        handleEnter(jDim);
                    }
                });
            }
            ppmLabels[iDim] = ppmLabel;
            atomDeltaMaps[iDim] = new HashMap<>();
        }

    }

    void handleEnter(int iDim) {
        if ((selPeak != null) && (peakDims != null)) {
            assignDim(iDim);
        }
    }

    void assignDim(int iDim) {
        String label = getValue(iDim);
        selPeak.getPeakDim(peakDims[iDim]).setLabel(label);
        PeakDim[] selPeakDims = selPeak.peakDims;
        PeakDim selPeakDim = selPeakDims[peakDims[iDim]];
        if (!label.isBlank()) {
            AtomResPattern.assign(label, selPeakDim, selPeakDims);
            show(selPeak);
        }
    }

    String getValue(int iDim) {
        String value;
        if (mode == ASSIGN_MODE.SIMPLE) {
            value = atomChoicesTF[iDim].getText();
        } else {
            value = atomChoices[iDim].getValue();
        }
        return value;
    }

    void clearValue(int iDim) {
        if (mode == ASSIGN_MODE.SIMPLE) {
            atomChoicesTF[iDim].setText("");
        } else {
            atomChoices[iDim].getItems().clear();
            atomChoices[iDim].getItems().add("");
            atomChoices[iDim].setValue("");
        }
    }

    public void show(Peak peak) {
        removePeakOnClose = peak != null;
        double defaultTol = 0.04;
        FXMLController fxmlController = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
        PolyChart chart = fxmlController.getActiveChart();
        List<Peak> selected = chart.getSelectedPeaks();
        selPeak = null;
        for (int i = 0; i < nFields; i++) {
            clearValue(i);
        }
        if (peak != null) {
            selPeak = peak;
        } else {
            if (selected.size() == 1) {
                selPeak = selected.get(0);
            }
            // fixme if more than one peak selected figure out if they're in row or column and set label
            // for a single (appropriate) dimension"
        }
        if (selPeak != null) {
            PeakList selPeakList = selPeak.getPeakList();
            int nDim = selPeakList.getNDim();
            if (nFields != nDim) {
                updateGrid(nDim);
            }
            PeakListAttributes usePeakAttr = null;
            List<PeakListAttributes> peakAttrs = chart.getPeakListAttributes();
            for (PeakListAttributes peakAttr : peakAttrs) {
                if (selPeak.getPeakList() == peakAttr.getPeakList()) {
                    usePeakAttr = peakAttr;
                    break;
                }
            }
            if (usePeakAttr != null) {
                peakDims = usePeakAttr.getPeakDim();
                int i = 0;
                for (int peakDim : peakDims) {
                    double tol = selPeak.getPeakList().getSpectralDim(peakDim).getTol();
                    if (tol < 1.0e-6) {
                        tol = defaultTol;
                    }
                    double shift = selPeak.getPeakDim(peakDim).getChemShiftValue();
                    ppmLabels[i].setText(String.format("%8.3f ppm +/ %.3f", shift, tol));
                    i++;
                }
                if (mode == ASSIGN_MODE.SIMPLE) {
                    i = 0;
                    for (int peakDim : peakDims) {
                        String label = selPeak.getPeakDim(peakDim).getLabel();
                        if (!label.isBlank()) {
                            atomChoicesTF[i].setText(label);
                        } else {
                            String prompt = selPeakList.getSpectralDim(peakDim).
                                    getPattern();
                            atomChoicesTF[i].setPromptText(prompt);
                        }
                        i++;
                    }
                } else {
                    DatasetBase dataset = chart.getDataset();
                    i = 0;
                    for (int peakDim : peakDims) {
                        double shift = selPeak.getPeakDim(peakDim).getChemShiftValue();
                        double tol = selPeak.getPeakList().getSpectralDim(peakDim).getTol();
                        if (tol < 1.0e-6) {
                            tol = defaultTol;
                        }
                        SpectralDim sDim = selPeak.getPeakList().getSpectralDim(peakDim);
                        List<AtomDelta> atoms1 = AtomBrowser.getMatchingAtomNames(dataset, sDim, shift, tol);
                        System.out.println(atoms1.toString());
                        atomDeltaMaps[i].clear();
                        for (AtomDelta atomDelta : atoms1) {
                            atomChoices[i].getItems().add(atomDelta.toString());
                            atomDeltaMaps[i].put(atomDelta.getName(), atomDelta);
                        }
                        if (!atoms1.isEmpty()) {
                            atomChoices[i].setValue(atoms1.get(0).toString());
                        }
                        i++;
                    }
                }
            }
        }
    }

    public void setActivePeaks(List<Peak> peaks) {
        if ((peaks != null) && !peaks.isEmpty()) {
            Peak peak = peaks.get(0);
            show(peak);
        }
    }

    void cancel() {
        if (removePeakOnClose) {
            if (selPeak != null) {
                PeakList peakList = selPeak.getPeakList();
                peakList.removePeak(selPeak);
                AnalystApp.getFXMLControllerManager().getOrCreateActiveController().getActiveChart().drawPeakLists(true);
            }
        }
    }

    void doAssign() {
        int i = 0;
        for (int iField = 0; iField < nFields; iField++) {
            String value;
            if (mode == ASSIGN_MODE.SIMPLE) {
                value = atomChoicesTF[iField].getText();
            } else {
                value = atomChoices[iField].getValue();
            }
            if (value != null) {
                System.out.println("val " + value + " " + i);
                PeakDim peakDim0 = selPeak.getPeakDim(peakDims[i]);

                if (value.length() > 0) {
                    String[] fields = value.split(" ");
                    if (fields.length > 0) {
                        String atomSpecifier = fields[0];
                        if (mode == ASSIGN_MODE.SIMPLE) {
                            assignDim(iField);
                        } else {
                            System.out.println(atomSpecifier);
                            AtomDelta atomDelta = atomDeltaMaps[i].get(atomSpecifier);
                            peakDim0.setLabel(atomSpecifier);
                            if ((atomDelta != null) && (atomDelta.getPeakDim() != null)) {
                                PeakDim peakDim1 = atomDelta.getPeakDim();
                                if (peakDim1.getLabel().equals("")) {
                                    PeakList.linkPeakDims(peakDim0, peakDim1);
                                    // force a reset of shifts so new peak gets shifted to the groups shift
                                    peakDim0.setChemShift(peakDim0.getChemShift());
                                    peakDim0.setFrozen(peakDim0.isFrozen());
                                    peakDim0.setLabel(atomSpecifier);
                                } else {
                                    PeakList.linkPeakDims(peakDim1, peakDim0);
                                    peakDim0.setChemShift(peakDim1.getChemShift());
                                    peakDim0.setFrozen(peakDim1.isFrozen());
                                }
                            }
                        }
                    }
                }
            }
            i++;
        }
    }
}
