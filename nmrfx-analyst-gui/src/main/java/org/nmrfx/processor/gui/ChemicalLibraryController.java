package org.nmrfx.processor.gui;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import org.apache.commons.math3.linear.RealVector;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.TextFields;
import org.nmrfx.analyst.compounds.*;
import org.nmrfx.analyst.dataops.DBData;
import org.nmrfx.analyst.dataops.SimData;
import org.nmrfx.analyst.dataops.SimDataVecPars;
import org.nmrfx.analyst.gui.AnalystPrefs;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.utils.ColorSchemes;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class ChemicalLibraryController {
    private static final Logger log = LoggerFactory.getLogger(ChemicalLibraryController.class);
    FXMLController fxmlController;
    PolyChart chart;
    Label searchLabel;
    TextField searchField;

    CheckBox autoNameCheckBox;
    TextField nameField;
    ChoiceBox<ChemicalLibraryController.LIBRARY_MODE> modeChoiceBox;

    VBox vBox;
    Spinner<Integer> spinner;
    Slider shiftSlider;
    CheckBox activeBox;
    TextField shiftField;
    TextField scaleField;
    GridPane gridPane;
    Slider ppmSlider;
    Slider couplingSlider;

    ToggleGroup atomToggleGroup = new ToggleGroup();
    ToggleGroup jToggleGroup = new ToggleGroup();
    List<SimpleDoubleProperty> ppmProperties = new ArrayList<>();
    List<SimpleDoubleProperty> jProperties = new ArrayList<>();

    CompoundMatch activeMatch = null;
    double sliderRange = 100.0;
    ComboBox<String> activeField;
    Dataset currentDataset = null;
    Dataset sumDataset = null;
    CompoundMatcher cmpdMatcher = new CompoundMatcher();
    SimpleObjectProperty<SimData> currentSimData = new SimpleObjectProperty<>();

    ChangeListener propChangeListener;
    enum LIBRARY_MODE {
        GISSMO,
        SEGMENTS
    }


    public void setup(FXMLController fxmlController, TitledPane annoPane) {
        this.fxmlController = fxmlController;
        this.chart = fxmlController.getActiveChart();
        vBox = new VBox();
        vBox.setSpacing(10);
        annoPane.setContent(vBox);

        Label label = new Label("Mode:");
        label.setPrefWidth(70);

        modeChoiceBox = new ChoiceBox<>();
        modeChoiceBox.setValue(ChemicalLibraryController.LIBRARY_MODE.GISSMO);
        modeChoiceBox.getItems().addAll(ChemicalLibraryController.LIBRARY_MODE.values());
        HBox hBox1 = new HBox();
        hBox1.setAlignment(Pos.CENTER_LEFT);
        hBox1.setSpacing(10);
        hBox1.getChildren().addAll(label, modeChoiceBox);


        searchLabel = new Label("Molecule:");
        searchLabel.setPrefWidth(60);
        searchField = new TextField();
        searchField.setPrefWidth(200);
        searchField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                setMol();
                //createCmpdData();
            }
        });
        HBox hBox2 = new HBox();
        hBox2.setAlignment(Pos.CENTER_LEFT);
        hBox2.setSpacing(10);
        hBox2.getChildren().addAll(searchLabel, searchField);

        vBox.getChildren().addAll(hBox1, hBox2);

        Callback<AutoCompletionBinding.ISuggestionRequest, Collection<String>> suggestionProvider = param -> {
            List<String> suggestions = getMatchingNames(param.getUserText());
            return suggestions;
        };
        TextFields.bindAutoCompletion(searchField, suggestionProvider);


        TitledPane adjusterPane = new TitledPane();
        adjusterPane.setText("Adjuster");
        vBox.getChildren().add(adjusterPane);
        VBox adjusterBox = new VBox();
        adjusterBox.setSpacing(10);
        adjusterPane.setContent(adjusterBox);

        Button fitButton = new Button("Fit");
        fitButton.setOnAction(e -> fit());

        ToolBar toolBar = new ToolBar();
        adjusterBox.getChildren().add(toolBar);

        HBox fitBar = new HBox();
        fitBar.setAlignment(Pos.CENTER_LEFT);
        adjusterBox.getChildren().add(fitBar);

        HBox fitBar2 = new HBox();
        fitBar2.setAlignment(Pos.CENTER_LEFT);
        gridPane = new GridPane();
        adjusterBox.getChildren().add(fitBar2);
        HBox fitBar3 = new HBox();
        fitBar3.setAlignment(Pos.CENTER_LEFT);
        adjusterBox.getChildren().add(fitBar3);

        toolBar.getItems().add(fitButton);
        Button optimizeButton = new Button("Optimize");
        optimizeButton.setOnAction(e -> optimize());
        toolBar.getItems().add(optimizeButton);

        Label activeLabel = new Label("Active");
        activeLabel.setPrefWidth(60);
        activeField = new ComboBox();
        activeField.setPrefWidth(200);
        activeField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                showActiveData();
            }
        });
        fitBar.getChildren().addAll(activeLabel, activeField);

        activeField.setEditable(true);
        activeField.valueProperty().addListener(e -> showActiveData());

        Label offsetLabel = new Label("Offset:");
        offsetLabel.setPrefWidth(60);
        spinner = new Spinner<>(0, 0, 0);
        spinner.getValueFactory().valueProperty().addListener(c -> regionChanged());
        spinner.setMaxWidth(60);
        shiftSlider = new Slider(-sliderRange / 2, sliderRange / 2, 0);
        shiftSlider.setBlockIncrement(0.5);
        shiftSlider.setOrientation(Orientation.HORIZONTAL);
        shiftSlider.valueProperty().addListener(e -> sliderChanged());
        shiftSlider.setOnMouseReleased(e -> updateSliderRanges());
        shiftSlider.setPrefWidth(100);
        shiftSlider.setMaxWidth(100);

        shiftField = new TextField("0.0");
        shiftField.setMaxWidth(50);
        shiftField.setPrefWidth(50);
        fitBar2.getChildren().addAll(offsetLabel, spinner, shiftSlider, shiftField);

        Label scaleLabel = new Label("Scale:");
        scaleLabel.setPrefWidth(60);

        scaleField = new TextField("0.0");
        scaleField.setMaxWidth(60);
        fitBar3.getChildren().addAll(scaleLabel, scaleField);
        scaleField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                setScale();
            }
        });

        VBox gissmoBox = new VBox();
        adjusterBox.getChildren().add(gissmoBox);
        makeAdjuster(gissmoBox);

        activeBox = new CheckBox();
        fitBar.getChildren().add(activeBox);
        activeBox.setOnAction(e -> setActive());
        toolBar.heightProperty().addListener((observable, oldValue, newValue) -> GUIUtils.toolbarAdjustHeights(Arrays.asList(toolBar)));
        setupListeners();

    }

    private Slider createSlider(double min, double max, double value) {
        Slider slider = new Slider(min, max, value);
        slider.setBlockIncrement(0.5);
        slider.setOrientation(Orientation.HORIZONTAL);
        slider.setOnMouseReleased(e -> updateSliderRanges());
        slider.setPrefWidth(200);
        slider.setMaxWidth(200);
        return slider;

    }
    private void makeAdjuster(VBox vBox) {
        ppmSlider = createSlider(-sliderRange /2, sliderRange /2, 0);
        couplingSlider = createSlider(0, 20, 10);
        HBox ppmBox = new HBox();
        Label ppmLabel = new Label("Shift");
        ppmBox.setSpacing(10);
        ppmBox.getChildren().addAll(ppmLabel, ppmSlider);
        HBox jBox = new HBox();
        Label jLabel = new Label("Coupling");
        jBox.setSpacing(10);
        jBox.getChildren().addAll(jLabel, couplingSlider);
        vBox.getChildren().add(ppmBox);
        vBox.getChildren().add(jBox);
        vBox.getChildren().add(gridPane);
    }

    public VBox getvBox() {
        return vBox;
    }

    public SimpleObjectProperty<SimData> getCurrentSimData() {
        return currentSimData;
    }

    SimDataVecPars defaultPars() {
        String label = "1H";
        double sf = AnalystPrefs.getLibraryVectorSF();
        double sw = AnalystPrefs.getLibraryVectorSW();
        int size = (int) Math.pow(2, AnalystPrefs.getLibraryVectorSize());
        double ref = AnalystPrefs.getLibraryVectorREF();
        return new SimDataVecPars(sf, sw, size, ref, label);

    }

    void updateColors(PolyChart chart) {
        if (chart.getDatasetAttributes().size() > 1) {
            chart.getChartProperties().setTitles(true);
            int nData = chart.getDatasetAttributes().size() - 1;
            String colScheme = (nData <= 10) ? "category10" : "category20";
            List<Color> colors = ColorSchemes.getColors(colScheme, nData);
            int i = 0;
            for (DatasetAttributes dataAttr : chart.getActiveDatasetAttributes()) {
                if (i > 0) {
                    dataAttr.setPosColor(colors.get(i - 1));
                }
                i++;
            }
        }

    }

    List<String> getMatchingNames(String pattern) {
        if (modeChoiceBox.getValue() == ChemicalLibraryController.LIBRARY_MODE.SEGMENTS) {
            String dbPath = AnalystPrefs.getSegmentLibraryFile();
            try {
                DBData.loadData(Path.of(dbPath));
            } catch (IOException e) {
            }

            return DBData.getNames(pattern);
        } else {
            if (!SimData.loaded()) {
                SimData.load();
            }
            return SimData.getNames(pattern);

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
            PolyChart chart = fxmlController.getActiveChart();
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
            PolyChart chart = fxmlController.getActiveChart();
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

    Dataset getExpDataset() {
        PolyChart chart = fxmlController.getActiveChart();

        Dataset currData = null;
        for (PolyChart pChart : fxmlController.getCharts()) {
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

    private boolean loadData(ChemicalLibraryController.LIBRARY_MODE mode) {
        if (mode == ChemicalLibraryController.LIBRARY_MODE.SEGMENTS) {
            String dbPath = AnalystPrefs.getSegmentLibraryFile();
            if (dbPath == null) {
                GUIUtils.warn("Spectrum Library", "No segment library set");
                return false;
            } else {
                try {
                    DBData.loadData(Path.of(dbPath));
                } catch (IOException ioE) {
                    GUIUtils.warn("Spectrum Library", "Error loading library file");
                    return false;
                }
            }
        }
        return true;
    }

    private Dataset makeDataset(ChemicalLibraryController.LIBRARY_MODE mode, Dataset currData, String name) {
        Dataset newDataset;
        String label = currData == null ? "1H" : currData.getLabel(0);
        SimDataVecPars pars;
        if (currData != null) {
            pars = new SimDataVecPars(currData);
        } else {
            pars = defaultPars();
        }
        if (mode == ChemicalLibraryController.LIBRARY_MODE.SEGMENTS) {
            CompoundData compoundData = DBData.makeData(name, pars);
            newDataset = new Dataset(compoundData.getVec());
        } else {
            double lb = AnalystPrefs.getLibraryVectorLB();
            newDataset = SimData.genDataset(name, pars, lb);
        }
        newDataset.setFreqDomain(0, true);
        newDataset.addProperty("SIM", name);
        return newDataset;
    }

    private Dataset makeDataset(Dataset currData, SimData simData, String name) {
        Dataset newDataset;
        String label = currData == null ? "1H" : currData.getLabel(0);
        SimDataVecPars pars;
        if (currData != null) {
            pars = new SimDataVecPars(currData);
        } else {
            pars = defaultPars();
        }
        double lb = AnalystPrefs.getLibraryVectorLB();
        newDataset = SimData.genDataset(simData, name, pars, lb);

        newDataset.setFreqDomain(0, true);
        newDataset.addProperty("SIM", name);
        return newDataset;
    }

    public SimpleObjectProperty<SimData> getSimData() {
        return currentSimData;
    }

    private void updateGrid(SimData simData) {
        gridPane.getChildren().clear();
        int nBlocks = simData.nBlocks();
        ppmProperties.clear();
        jProperties.clear();
        for (int iBlock = 0;iBlock<nBlocks;iBlock++) {
            SimData.AtomBlock atomBlock = simData.atomBlock(iBlock);
            int nPPMs = atomBlock.nPPMs();
            for (int iPPM=0;iPPM<nPPMs;iPPM++) {
                RadioButton ppmRadioButton = new RadioButton();
                ppmRadioButton.setToggleGroup(atomToggleGroup);
                ppmRadioButton.setText(String.valueOf(atomBlock.id(iPPM)));
                ppmRadioButton.setUserData(iPPM);
                gridPane.add(ppmRadioButton, 0, iPPM);
                if (iPPM== 0) {
                    atomToggleGroup.selectToggle(ppmRadioButton);
                }
                SimpleDoubleProperty ppmProperty = new SimpleDoubleProperty(atomBlock.ppm(iPPM));
                ppmProperties.add(ppmProperty);
                ppmProperty.addListener(propChangeListener);

                TextField ppmField = GUIUtils.getDoubleTextField(ppmProperty, 3);
                ppmField.setPrefWidth(70);
                gridPane.add(ppmField, 1, iPPM);
                SimpleDoubleProperty jProperty = new SimpleDoubleProperty(0.0);
                jProperties.add(jProperty);
                jProperty.addListener(propChangeListener);

                TextField jField = GUIUtils.getDoubleTextField(jProperty, 2);
                jField.setPrefWidth(70);
                gridPane.add(jField, 2, iPPM);
                RadioButton jRadioButton = new RadioButton();
                jRadioButton.setUserData(iPPM);
                jRadioButton.setToggleGroup(jToggleGroup);
                jRadioButton.setText(String.valueOf(atomBlock.id(iPPM)));
                gridPane.add(jRadioButton, 3, iPPM);

            }
        }
    }

    private void setupListeners() {
        atomToggleGroup.selectedToggleProperty().addListener(e -> updateSelectedAtom(true));
        jToggleGroup.selectedToggleProperty().addListener(e -> updateSelectedAtom(false));
        propChangeListener = new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                updateActiveData();
            }
        };

    }

    private void updatePPMSlider(int iAtom) {
        if ((iAtom >= 0) && (iAtom < ppmProperties.size())) {
            for (var ppmProp : ppmProperties) {
                ppmSlider.valueProperty().unbindBidirectional(ppmProp);
            }
            var ppmValue = ppmProperties.get(iAtom);
            double shift = ppmValue.doubleValue();
            double range = 0.5;
            ppmSlider.setMin(shift - range / 2.0);
            ppmSlider.setMax(shift + range / 2.0);
            ppmSlider.setValue(shift);
            ppmSlider.valueProperty().bindBidirectional(ppmValue);
        }
    }

    private void updateJProperties(int iAtom, int iCoup) {
        if (currentSimData.get() != null) {
            SimData.AtomBlock block = currentSimData.get().atomBlock(0);
            for (var couplingProp : jProperties) {
                couplingSlider.valueProperty().unbindBidirectional(couplingProp);
                couplingProp.removeListener(propChangeListener);
            }
            for (int jCoup = 0; jCoup < jProperties.size(); jCoup++) {
                Double jValue = block.j(iAtom, jCoup);
                jProperties.get(jCoup).set(jValue);
            }
            if (iCoup >= 0) {
                couplingSlider.setValue(jProperties.get(iCoup).doubleValue());
                jProperties.get(iCoup).bindBidirectional(couplingSlider.valueProperty());
            }
            for (var couplingProp : jProperties) {
                couplingProp.addListener(propChangeListener);
            }
        }
    }

    private void updateCouplingSlider(int iAtom, int jAtom) {
        if ((iAtom >= 0) && (iAtom < jProperties.size())) {
            for (var couplingProp : jProperties) {
                couplingSlider.valueProperty().unbindBidirectional(couplingProp);
            }
            var jProp = jProperties.get(jAtom);
            jProp.removeListener(propChangeListener);
            double jValue = jProp.doubleValue();
            couplingSlider.setMin(0);
            couplingSlider.setMax(20.0);
            couplingSlider.setValue(jValue);
            jProp.addListener(propChangeListener);
            jProperties.get(jAtom).bindBidirectional(couplingSlider.valueProperty());
        }
    }

    private void updateSelectedAtom(boolean ppmMode) {
        int iAtom = -1;
        int jAtom = -1;
        RadioButton atomRadioButton = (RadioButton) atomToggleGroup.getSelectedToggle();
        if (atomRadioButton != null) {
            iAtom = (Integer) atomRadioButton.getUserData();
        }
        RadioButton jRadioButton = (RadioButton) jToggleGroup.getSelectedToggle();
        if (jRadioButton != null) {
            jAtom = (Integer) jRadioButton.getUserData();
        }
        if (ppmMode) {
            updatePPMSlider(iAtom);
            updateJProperties(iAtom, jAtom);
        } else {
            updateCouplingSlider(iAtom, jAtom);
        }
    }

    void updateActiveData() {
        Toggle atomToggle = atomToggleGroup.getSelectedToggle();
        Toggle couplingToggle = jToggleGroup.getSelectedToggle();

        int activeI = atomToggle != null ? (Integer) atomToggle.getUserData() : -1;
        int activeJ = couplingToggle != null ? (Integer) couplingToggle.getUserData() : -1;
        SimData simData = currentSimData.get();
        if (simData != null) {
            Dataset testDataset = Dataset.getDataset(simData.getName().toLowerCase());
            if (testDataset != null) {
                int j = 0;
                for (int iBlock = 0; iBlock < simData.nBlocks(); iBlock++) {
                    SimData.AtomBlock atomBlock = simData.atomBlock(iBlock);
                    int nPPMs = atomBlock.nPPMs();
                    for (int iPPM = 0; iPPM < nPPMs; iPPM++) {
                        var ppmProp = ppmProperties.get(j);
                        atomBlock.ppm(iPPM, ppmProp.doubleValue());
                        var jProp = jProperties.get(j);
                        if ((activeI != iPPM) && (iPPM == activeJ)) {
                            atomBlock.j(activeI, iPPM, jProp.doubleValue());
                        }
                        j++;
                    }
                }
                updateDataset(simData, testDataset);
            }
        }
    }

    public void setMol() {
        ChemicalLibraryController.LIBRARY_MODE mode = modeChoiceBox.getValue();
        if (!loadData(mode)) {
            return;
        }
        String name = searchField.getText().toLowerCase();
        currentSimData.set(null);
        boolean dataInLibrary = mode == ChemicalLibraryController.LIBRARY_MODE.SEGMENTS ? DBData.contains(name) : SimData.contains(name);
        if (dataInLibrary) {
            Dataset testDataset = Dataset.getDataset(name);
            PolyChart chart = fxmlController.getActiveChart();
            boolean appendMode = false;
            final Dataset newDataset;
            if (testDataset == null) {
                Dataset currData = null;
                for (PolyChart pChart : fxmlController.getCharts()) {
                    currData = (Dataset) pChart.getDataset();
                    if (currData != null) {
                        break;
                    }
                }
                if (currData != null) {
                    appendMode = true;
                }
                newDataset = makeDataset(mode, currData, name);
            } else {
                newDataset = testDataset;
            }
            if (mode == LIBRARY_MODE.GISSMO) {
                SimData.getSimData(name).ifPresent(simData -> {
                    SimData activeSimData = simData.copy();
                    currentSimData.set(activeSimData);
                    updateDataset(activeSimData, newDataset);
                    updateGrid(activeSimData);
                });
            }

            fxmlController.getStatusBar().setMode(SpectrumStatusBar.DataMode.DATASET_1D);
            chart.setDataset(newDataset, appendMode, false);
            activeField.getItems().add(name);
            activeField.setValue(name);
            updateColors(chart);
            searchField.setText("");
            chart.refresh();
        } else {
            GUIUtils.warn("Spectrum Library", "Entry not present " + name);
        }
    }



    public void updateDataset(SimData simData, Dataset newDataset) {
        PolyChart chart = fxmlController.getActiveChart();
        boolean appendMode = false;
        Vec vec = newDataset.getVec();
        double lb = AnalystPrefs.getLibraryVectorLB();
        SimData.genVec(simData, vec, lb);

        fxmlController.getStatusBar().setMode(SpectrumStatusBar.DataMode.DATASET_1D);
        chart.setDataset(newDataset, appendMode, false);

        updateColors(chart);
        searchField.setText("");
        chart.refresh();
    }

    public void createCmpdData() {
        ChemicalLibraryController.LIBRARY_MODE mode = modeChoiceBox.getValue();
        if (!loadData(mode)) {
            return;
        }
        currentSimData.set(null);

        String name = searchField.getText().toLowerCase();
        List<String> names = Arrays.asList("sum", "current");
        Dataset[] datasets = new Dataset[names.size()];
        boolean dataInLibrary = mode == ChemicalLibraryController.LIBRARY_MODE.SEGMENTS ? DBData.contains(name) : SimData.contains(name);
        if (dataInLibrary) {
            PolyChart chart = fxmlController.getActiveChart();
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

            double refConc = 1.0;
            double cmpdConc = 1.0;
            CompoundData cData;
            if (mode == LIBRARY_MODE.SEGMENTS) {
                cData = DBData.makeData(name, pars);
            } else {
                var simDataOpt = SimData.getSimData(name);
                if (simDataOpt.isPresent()) {
                    SimData simData = simDataOpt.get();
                    currentSimData.set(simData);
                    cData = SimData.genCompoundData(name, name, simData, pars, lb, refConc, cmpdConc);
                } else {
                    return;
                }
            }
            CompoundData.put(cData, name);
            activeField.getItems().add(name);
            cmpdMatcher.addMatch(cData);
            for (int iDataset = 0; iDataset < datasets.length; iDataset++) {
                if (datasets[iDataset] == null) {
                    Vec vec = new Vec(cData.getVec().getSize());
                    cData.getVec().copy(vec);
                    if (iDataset == 0) {
                        vec.setName("sum");
                    } else if (iDataset == 1) {
                        vec.setName("current");
                    }
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
            sumDataset = datasets[names.indexOf("sum")];
            currentDataset = datasets[names.indexOf("current")];

            fxmlController.getStatusBar().setMode(SpectrumStatusBar.DataMode.DATASET_1D);
            for (Dataset dataset : datasets) {
                if (!chart.containsDataset(dataset)) {
                    chart.setDataset(dataset, true, false);
                }
            }
            updateColors(chart);
            searchField.setText("");
            chart.refresh();
            activeField.setValue(name);
            showActiveData();
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
            activeMatch = cmpdMatcher.getMatch(activeField.getValue());
            if (activeMatch != null) {
                activeMatch.setScale(vecResult.getEntry(i));
            }
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
