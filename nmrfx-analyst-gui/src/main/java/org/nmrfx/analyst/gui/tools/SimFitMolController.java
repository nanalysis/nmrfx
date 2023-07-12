/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.gui.tools;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.apache.commons.math3.linear.RealVector;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.TextFields;
import org.nmrfx.analyst.compounds.CompoundData;
import org.nmrfx.analyst.compounds.CompoundFitter;
import org.nmrfx.analyst.compounds.CompoundMatch;
import org.nmrfx.analyst.compounds.FitResult;
import org.nmrfx.analyst.dataops.SimData;
import org.nmrfx.analyst.dataops.SimDataVecPars;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.AnalystPrefs;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.SpectrumStatusBar;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author brucejohnson
 */
public class SimFitMolController extends SimMolController {

    private static final Logger log = LoggerFactory.getLogger(SimFitMolController.class);
    ComboBox<String> activeField;
    Dataset currentDataset = null;
    Dataset sumDataset = null;
    VBox vBox;
    Spinner<Integer> spinner;
    Slider shiftSlider;
    CheckBox activeBox;
    Consumer<SimFitMolController> closeAction;
    TextField shiftField;
    TextField scaleField;
    CompoundMatch activeMatch = null;
    double sliderRange = 100.0;

    public SimFitMolController(FXMLController controller, Consumer<SimFitMolController> closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
    }

    public VBox getBox() {
        return vBox;
    }

    public void initialize(VBox vBox, ToolBar toolBar, ToolBar fitBar) {
        this.vBox = vBox;
        this.browserToolBar = toolBar;
        toolBar.setPrefWidth(900.0);

        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", AnalystApp.ICON_SIZE_STR, AnalystApp.REG_FONT_SIZE_STR, ContentDisplay.LEFT);
        closeButton.setOnAction(e -> close());

        toolBar.getItems().add(closeButton);
        addFiller(toolBar);

        atomFieldLabel = new Label("Molecule:");
        molNameField = new TextField();
        molNameField.setPrefWidth(300);
        molNameField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                createCmpdData();
            }
        });
        toolBar.getItems().add(atomFieldLabel);
        toolBar.getItems().add(molNameField);
        addFiller(toolBar);

        Callback<AutoCompletionBinding.ISuggestionRequest, Collection<String>> suggestionProvider = new Callback<AutoCompletionBinding.ISuggestionRequest, Collection<String>>() {
            @Override
            public Collection<String> call(AutoCompletionBinding.ISuggestionRequest param) {
                List<String> suggestions = getMatchingNames(param.getUserText());
                return suggestions;
            }
        };
        TextFields.bindAutoCompletion(molNameField, suggestionProvider);
        Button fitButton = new Button("Fit");
        fitButton.setOnAction(e -> fit());
        toolBar.getItems().add(fitButton);
        Button optimizeButton = new Button("Optimize");
        optimizeButton.setOnAction(e -> optimize());
        toolBar.getItems().add(optimizeButton);

        Label activeLabel = new Label("Active");
        activeField = new ComboBox();
        activeField.setPrefWidth(300);
        activeField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                showActiveData();
            }
        });
        fitBar.getItems().addAll(activeLabel, activeField);

        Callback<AutoCompletionBinding.ISuggestionRequest, Collection<String>> activeProvider = new Callback<AutoCompletionBinding.ISuggestionRequest, Collection<String>>() {
            @Override
            public Collection<String> call(AutoCompletionBinding.ISuggestionRequest param) {
                List<String> suggestions = getActiveNames(param.getUserText());
                return suggestions;
            }
        };
        TextFields.bindAutoCompletion(activeField.getEditor(), activeProvider);
        activeField.setEditable(true);
        activeField.valueProperty().addListener(e -> showActiveData());

        spinner = new Spinner<>(0, 0, 0);
        addFiller(fitBar);
        fitBar.getItems().add(spinner);
        spinner.getValueFactory().valueProperty().addListener(c -> regionChanged());
        spinner.setMaxWidth(100);
        shiftSlider = new Slider(-sliderRange / 2, sliderRange / 2, 0);
        shiftSlider.setBlockIncrement(0.5);
        shiftSlider.setOrientation(Orientation.HORIZONTAL);
        shiftSlider.valueProperty().addListener(e -> sliderChanged());
        shiftSlider.setOnMouseReleased(e -> updateSliderRanges());
        shiftSlider.setMaxWidth(100);
        fitBar.getItems().add(shiftSlider);

        shiftField = new TextField("0.0");
        shiftField.setMaxWidth(100);
        fitBar.getItems().add(shiftField);

        scaleField = new TextField("0.0");
        scaleField.setMaxWidth(100);
        fitBar.getItems().add(scaleField);
        scaleField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                setScale();
            }
        });

        activeBox = new CheckBox();
        fitBar.getItems().add(activeBox);
        activeBox.setOnAction(e -> setActive());
        toolBar.heightProperty().addListener((observable, oldValue, newValue) -> GUIUtils.toolbarAdjustHeights(Arrays.asList(toolBar, fitBar)));
    }

    public void close() {
        closeAction.accept(this);
    }

    Dataset getExpDataset() {
        PolyChart chart = controller.getActiveChart();

        Dataset currData = null;
        for (PolyChart pChart : controller.getCharts()) {
            currData = (Dataset) pChart.getDataset();
            if (currData != null) {
                break;
            }
        }
        return currData;
    }

    Vec getExpVec() {
        Dataset dataset = getExpDataset();
        Vec vec = dataset.getVec();
        if (vec == null) {
            vec = new Vec(dataset.getSizeTotal(0));
            try {
                dataset.readVector(vec, 0, 0);
            } catch (IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }
        return vec;
    }

    public void createCmpdData() {
        String name = molNameField.getText().toLowerCase();
        List<String> names = Arrays.asList("sum", "current");
        Dataset[] datasets = new Dataset[names.size()];
        System.out.println("Mol Name: " + name);
        if (SimData.contains(name)) {
            PolyChart chart = controller.getActiveChart();
            for (int iDataset = 0; iDataset < datasets.length; iDataset++) {
                datasets[iDataset] = Dataset.getDataset(names.get(iDataset));
            }

            Dataset currData = getExpDataset();
            SimDataVecPars pars;
            if (currData != null) {
                pars = new SimDataVecPars(currData);
            } else {
                pars = defaultPars();
            }
            double lb = AnalystPrefs.getLibraryVectorLB();

            /*
                public static CompoundData genCompoundData(String cmpdID, String name, int n,
            double refConc, double cmpdConc,
            double sf, double sw, double centerPPM, double lb, double frac) {

             */
            double refConc = 1.0;
            double cmpdConc = 1.0;
            CompoundData cData = SimData.genCompoundData(name, name, pars, lb, refConc, cmpdConc);
            CompoundData.put(cData, name);
            activeField.getItems().add(name);
            cmpdMatcher.addMatch(cData);
            for (int iDataset = 0; iDataset < datasets.length; iDataset++) {
                if (datasets[iDataset] == null) {
                    Vec vec = SimData.prepareVec(names.get(iDataset), pars);
                    vec.setFreqDomain(true);
                    vec.setRefValue(pars.getRef());
                    datasets[iDataset] = new Dataset(vec);
                    datasets[iDataset].setLabel(0, pars.getLabel());
                }
            }
            Vec sumVec = datasets[0].getVec();
            cmpdMatcher.updateVec(sumVec);
            Vec currentVec = datasets[1].getVec();
            currentVec.zeros();
            cData.addToVec(currentVec, 1.0);
            if (currData != null) {
                Vec expVec = currData.getVec();
            }
            sumDataset = datasets[names.indexOf("sum")];
            currentDataset = datasets[names.indexOf("current")];

            controller.getStatusBar().setMode(SpectrumStatusBar.DataMode.DATASET_1D);
            for (Dataset dataset : datasets) {
                if (!chart.containsDataset(dataset)) {
                    chart.setDataset(dataset, true, false);
                }
            }
            updateColors(chart);
            molNameField.setText("");
            chart.refresh();
            activeField.setValue(name);
            showActiveData();
        }
    }

    void sliderChanged() {
        int iRegion = spinner.getValue();
        double shift = shiftSlider.getValue();
        shiftField.setText(String.format("%.1f", shift));
        if (activeMatch != null) {
            activeMatch.setShift(iRegion, shift);
            updateSumData();
            showActiveData(activeMatch);
        }

    }

    void regionChanged() {
        int iRegion = spinner.getValue();
        if (activeMatch != null) {
            double shift = activeMatch.getShifts()[iRegion];
            updateSliderRanges(shift);
            shiftField.setText(String.format("%.1f", shift));
            double center = activeMatch.getData().getRegion(iRegion).getAvgPPM();
            PolyChart chart = controller.getActiveChart();
            if (!chart.isInView(0, center, 0.2)) {
                Double[] positions = {center};
                chart.moveTo(positions);
            }
            activeBox.setSelected(activeMatch.getActive(iRegion));
        }
    }

    void activateCurrent() {
        activeBox.setSelected(true);
        setActive();
    }

    void setActive() {
        setActive(activeBox.isSelected());
    }

    void setActive(boolean state) {
        int iRegion = spinner.getValue();
        if (activeMatch != null) {
            activeMatch.setActive(iRegion, state);
        }
    }

    void updateSliderRanges() {
        updateSliderRanges(shiftSlider.getValue());
    }

    void updateSliderRanges(double shift) {
        shiftSlider.setMin(Math.round(shift) - sliderRange / 2);
        shiftSlider.setMax(Math.round(shift) + sliderRange / 2);
        shiftSlider.setValue(shift);
    }

    void updateSumData() {
        if (sumDataset != null) {
            Vec sumVec = sumDataset.getVec();
            cmpdMatcher.updateVec(sumVec);
        }
    }

    void showActiveData() {
        if (currentDataset != null) {
            activeMatch = cmpdMatcher.getMatch(activeField.getValue());
            showActiveData(activeMatch);
            SpinnerValueFactory.IntegerSpinnerValueFactory iFactory = (SpinnerValueFactory.IntegerSpinnerValueFactory) spinner.getValueFactory();
            iFactory.setMax(activeMatch.getData().getRegionCount() - 1);
            iFactory.setValue(0);
        }
    }

    void showActiveData(CompoundMatch activeMatch) {
        if (activeMatch != null) {
            CompoundData cData = activeMatch.getData();
            Vec currentVec = currentDataset.getVec();
            currentVec.zeros();
            cData.addToVec(currentVec, activeMatch.getShifts(), activeMatch.getScale());
            PolyChart chart = controller.getActiveChart();
            chart.refresh();
            scaleField.setText(String.format("%.3f", activeMatch.getScale()));

        }
    }

    List<String> getActiveNames(String pattern) {
        if (!SimData.loaded()) {
            SimData.load();
        }

        List<String> names = cmpdMatcher.getNames(pattern);
        return names;

    }

    void setScale() {
        String scaleStr = scaleField.getText().trim();
        try {
            double scale = Double.parseDouble(scaleStr);
            if (currentDataset != null) {
                activeMatch = cmpdMatcher.getMatch(activeField.getValue());
                if (activeMatch != null) {
                    activeMatch.setScale(scale);
                    updateSumData();
                    regionChanged();
                    showActiveData(activeMatch);
                }
            }
        } catch (NumberFormatException nfE) {
            log.warn("Unable to parse scale.", nfE);
        }
    }

    void fit() {
        activateCurrent();
        CompoundFitter cFitter = CompoundFitter.setup(cmpdMatcher.getMatches());
        Vec fitVec = getExpVec();
        // zeroNonRegions

        cFitter.setVec(fitVec);
        cFitter.scoreAbs();
        RealVector vecResult = cFitter.getX();
        for (int i = 0; i < vecResult.getDimension(); i++) {
            System.out.println(i + " " + vecResult.getEntry(i));
        }
        updateSumData();
        regionChanged();
        showActiveData(activeMatch);
    }

    void optimize() {
        activateCurrent();
        CompoundFitter cFitter = CompoundFitter.setup(cmpdMatcher.getMatches());
        Vec fitVec = getExpVec();
        // zeroNonRegions

        cFitter.setVec(fitVec);
        List<FitResult> fitResults = cFitter.optimizeAlignment();
        int iMatch = 0;
        for (FitResult fitResult : fitResults) {
            System.out.println(fitResult.getShift() + " " + fitResult.getScale());
        }
        updateSumData();
        regionChanged();
        showActiveData(activeMatch);
    }
}
