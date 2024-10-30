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
import org.controlsfx.dialog.ExceptionDialog;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

public class ChemicalLibraryController {
    private static final Logger log = LoggerFactory.getLogger(ChemicalLibraryController.class);
    FXMLController fxmlController;
    PolyChart chart;
    Label searchLabel;
    TextField searchField;

    ChoiceBox<ChemicalLibraryController.LIBRARY_MODE> modeChoiceBox;

    VBox vBox;
    Spinner<Integer> spinner;
   // Slider shiftSlider;
    CheckBox activeBox;
    //TextField shiftField;
    //TextField scaleField;
    GridPane gridPane;

    Slider fieldSlider;
    Slider lwSlider;
    Slider ppmSlider;
    Slider couplingSlider;

    ToggleGroup atomToggleGroup = new ToggleGroup();
    ToggleGroup jToggleGroup = new ToggleGroup();
    List<SimpleDoubleProperty> ppmProperties = new ArrayList<>();
    List<SimpleDoubleProperty> jProperties = new ArrayList<>();
    List<RadioButton> ppmButtons = new ArrayList<>();
    List<RadioButton> coupButtons = new ArrayList<>();
    List<BlockIndex> blockIndices = new ArrayList<>();

    CompoundMatch activeMatch = null;
    double sliderRange = 100.0;
    ComboBox<String> activeField;
    Dataset currentDataset = null;
    Dataset sumDataset = null;
    CompoundMatcher cmpdMatcher = new CompoundMatcher();

    Map<String, SimData> currentSimMap = new HashMap<>();
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
            }
        });
        HBox hBox2 = new HBox();
        hBox2.setAlignment(Pos.CENTER_LEFT);
        hBox2.setSpacing(10);
        hBox2.getChildren().addAll(searchLabel, searchField);

        vBox.getChildren().addAll(hBox1, hBox2);

        Callback<AutoCompletionBinding.ISuggestionRequest, Collection<String>> suggestionProvider = param -> getMatchingNames(param.getUserText());
        TextFields.bindAutoCompletion(searchField, suggestionProvider);


        TitledPane adjusterPane = new TitledPane();
        adjusterPane.setText("Adjuster");
        vBox.getChildren().add(adjusterPane);
        VBox adjusterBox = new VBox();
        adjusterBox.setSpacing(10);
        adjusterPane.setContent(adjusterBox);



        HBox fitBar = new HBox();
        fitBar.setAlignment(Pos.CENTER_LEFT);
        adjusterBox.getChildren().add(fitBar);

        gridPane = new GridPane();

//        HBox fitBar2 = new HBox();
//        fitBar2.setAlignment(Pos.CENTER_LEFT);
//        adjusterBox.getChildren().add(fitBar2);
//        HBox fitBar3 = new HBox();
//        fitBar3.setAlignment(Pos.CENTER_LEFT);
//        adjusterBox.getChildren().add(fitBar3);

//        ToolBar toolBar = new ToolBar();
//        adjusterBox.getChildren().add(toolBar);
//        Button fitButton = new Button("Fit");
//        fitButton.setOnAction(e -> fit());
//        toolBar.getItems().add(fitButton);
//        Button optimizeButton = new Button("Optimize");
//        optimizeButton.setOnAction(e -> optimize());
//        toolBar.getItems().add(optimizeButton);
//       toolBar.heightProperty().addListener((observable, oldValue, newValue) -> GUIUtils.toolbarAdjustHeights(List.of(toolBar)));

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

//        Label offsetLabel = new Label("Offset:");
//        offsetLabel.setPrefWidth(60);
//        spinner = new Spinner<>(0, 0, 0);
//        spinner.getValueFactory().valueProperty().addListener(c -> regionChanged());
//        spinner.setMaxWidth(60);
//        shiftSlider = new Slider(-sliderRange / 2, sliderRange / 2, 0);
//        shiftSlider.setBlockIncrement(0.5);
//        shiftSlider.setOrientation(Orientation.HORIZONTAL);
//        shiftSlider.valueProperty().addListener(e -> sliderChanged());
//        shiftSlider.setOnMouseReleased(e -> updateSliderRanges());
//        shiftSlider.setPrefWidth(100);
//        shiftSlider.setMaxWidth(100);
//
//        shiftField = new TextField("0.0");
//        shiftField.setMaxWidth(50);
//        shiftField.setPrefWidth(50);
//        fitBar2.getChildren().addAll(offsetLabel, spinner, shiftSlider, shiftField);
//
//        Label scaleLabel = new Label("Scale:");
//        scaleLabel.setPrefWidth(60);
//
//        scaleField = new TextField("0.0");
//        scaleField.setMaxWidth(60);
//        fitBar3.getChildren().addAll(scaleLabel, scaleField);
//        scaleField.setOnKeyReleased(e -> {
//            if (e.getCode() == KeyCode.ENTER) {
//                setScale();
//            }
//        });

        VBox gissmoBox = new VBox();
        adjusterBox.getChildren().add(gissmoBox);
        makeAdjuster(gissmoBox);

        activeBox = new CheckBox();
        fitBar.getChildren().add(activeBox);
        activeBox.setOnAction(e -> setActive());
        setupListeners();
        fieldSlider.valueProperty().addListener(propChangeListener);
        lwSlider.valueProperty().addListener(propChangeListener);

    }

    private Slider createSlider(double min, double max, double value) {
        Slider slider = new Slider(min, max, value);
        slider.setBlockIncrement(0.5);
        slider.setOrientation(Orientation.HORIZONTAL);
      //  slider.setOnMouseReleased(e -> updateSliderRanges());
        slider.setPrefWidth(200);
        slider.setMaxWidth(200);
        return slider;

    }
    private void makeAdjuster(VBox vBox) {
        fieldSlider = createSlider(40, 1200, 600);
        fieldSlider.setBlockIncrement(10);
        lwSlider = createSlider(0.1, 20, 1.0);
        lwSlider.setBlockIncrement(0.1);
        ppmSlider = createSlider(-sliderRange /2, sliderRange /2, 0);
        ppmSlider.setBlockIncrement(0.005);
        couplingSlider = createSlider(0, 20, 10);
        couplingSlider.setBlockIncrement(0.1);

        HBox fieldBox = new HBox();
        Label fieldLabel = new Label("Field");
        TextField fieldText = new TextField();
        fieldText.setPrefWidth(70);
        fieldBox.setSpacing(10);
        fieldBox.getChildren().addAll(fieldLabel, fieldSlider, fieldText);
        GUIUtils.bindSliderField(fieldSlider, fieldText, "0.##");

        HBox lwBox = new HBox();
        Label lwLabel = new Label("LW (Hz)");
        TextField lwText = new TextField();
        lwText.setPrefWidth(70);
        lwBox.setSpacing(10);
        lwBox.getChildren().addAll(lwLabel, lwSlider, lwText);
        GUIUtils.bindSliderField(lwSlider, lwText, "0.##");

        fieldSlider.setValue(600.0);
        lwSlider.setValue(1.0);


        HBox ppmBox = new HBox();
        Label ppmLabel = new Label("Shift");
        ppmBox.setSpacing(10);
        ppmBox.getChildren().addAll(ppmLabel, ppmSlider);
        HBox jBox = new HBox();
        Label jLabel = new Label("Coupling");
        jBox.setSpacing(10);
        jBox.getChildren().addAll(jLabel, couplingSlider);

        vBox.setSpacing(10);
        vBox.getChildren().add(fieldBox);
        vBox.getChildren().add(lwBox);
        vBox.getChildren().add(ppmBox);
        vBox.getChildren().add(jBox);
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(gridPane);
        vBox.getChildren().add(scrollPane);
    }

    public VBox getvBox() {
        return vBox;
    }

    public SimpleObjectProperty<SimData> getCurrentSimData() {
        return currentSimData;
    }

    SimDataVecPars defaultPars() {
        return defaultPars(null);
    }
    SimDataVecPars defaultPars(Double sfValue) {
        String label = "1H";
        double sf = sfValue == null ? AnalystPrefs.getLibraryVectorSF() : sfValue;
        double swPPM = AnalystPrefs.getLibraryVectorSWPPM();
        double sw = swPPM * sf;
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
                loadSimData();
            }
            return SimData.getNames(pattern);

        }
    }

    void loadSimData() {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        InputStream istream = cl.getResourceAsStream("data/bmse.yaml");
        SimData.load(istream);
        String fileName = AnalystPrefs.getGissmoFile();
        if (!fileName.isEmpty()) {
            File file = new File(fileName);
            if (file.exists()) {
                try (FileInputStream fileInputStream = new FileInputStream(file)) {
                    SimData.load(fileInputStream);
                } catch (IOException ioException) {
                    ExceptionDialog exceptionDialog = new ExceptionDialog(ioException);
                    exceptionDialog.showAndWait();
                }
            }
        }

    }

 //   void sliderChanged() {
//        int iRegion = spinner.getValue();
//        double shift = shiftSlider.getValue();
//        shiftField.setText(String.format("%.1f", shift));
//        if (activeMatch != null) {
//            activeMatch.setShift(iRegion, shift);
//            updateSumData();
//            showActiveData(activeMatch);
//        }
//
//    }
//
//    void regionChanged() {
//        int iRegion = spinner.getValue();
//        if (activeMatch != null) {
//            double shift = activeMatch.getShifts()[iRegion];
//            updateSliderRanges(shift);
//            shiftField.setText(String.format("%.1f", shift));
//            double center = activeMatch.getData().getRegion(iRegion).getAvgPPM();
//            PolyChart chart = fxmlController.getActiveChart();
//            if (!chart.isInView(0, center, 0.2)) {
//                Double[] positions = {center};
//                chart.moveTo(positions);
//            }
//            activeBox.setSelected(activeMatch.getActive(iRegion));
//        }
//    }

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

//    void updateSliderRanges() {
//        updateSliderRanges(shiftSlider.getValue());
//    }
//
//    void updateSliderRanges(double shift) {
//        shiftSlider.setMin(Math.round(shift) - sliderRange / 2);
//        shiftSlider.setMax(Math.round(shift) + sliderRange / 2);
//        shiftSlider.setValue(shift);
//    }

    void updateSumData() {
        if (sumDataset != null) {
            Vec sumVec = sumDataset.getVec();
            cmpdMatcher.updateVec(sumVec);
        }
    }

    void showActiveData() {
        String activeDataName = activeField.getValue().toLowerCase();
        if (!activeDataName.isEmpty() && (currentSimData.get() != null) && !activeDataName.equalsIgnoreCase(currentSimData.get().getName())) {
            SimData simData = currentSimMap.get(activeDataName);
            if (simData != null) {
                currentSimData.set(simData);
                updateGrid(simData);
            }
        }
    }
    void showActiveDataCmpd() {
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
        //    scaleField.setText(String.format("%.3f", activeMatch.getScale()));

        }
    }

    List<String> getActiveNames(String pattern) {
        if (!SimData.loaded()) {
            loadSimData();
        }

        return cmpdMatcher.getNames(pattern);

    }

//    void setScale() {
//        String scaleStr = scaleField.getText().trim();
//        try {
//            double scale = Double.parseDouble(scaleStr);
//            if (currentDataset != null) {
//                activeMatch = cmpdMatcher.getMatch(activeField.getValue());
//                if (activeMatch != null) {
//                    activeMatch.setScale(scale);
//                    updateSumData();
//                    regionChanged();
//                    showActiveData(activeMatch);
//                }
//            }
//        } catch (NumberFormatException nfE) {
//            log.warn("Unable to parse scale.", nfE);
//        }
//    }

    Dataset getExpDataset() {
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
        SimDataVecPars pars;
        if (currData != null) {
            pars = new SimDataVecPars(currData);
        } else {
            pars = defaultPars(fieldSlider.getValue());
        }
        if (mode == ChemicalLibraryController.LIBRARY_MODE.SEGMENTS) {
            CompoundData compoundData = DBData.makeData(name, pars);
            newDataset = new Dataset(compoundData.getVec());
        } else {
            double lb = lwSlider.getValue();
            newDataset = SimData.genDataset(name, pars, lb);
        }
        newDataset.setFreqDomain(0, true);
        newDataset.addProperty("SIM", name);
        return newDataset;
    }

    private Dataset makeDataset(Dataset currData, SimData simData, String name) {
        Dataset newDataset;
        SimDataVecPars pars;
        if (currData != null) {
            pars = new SimDataVecPars(currData);
        } else {
            pars = defaultPars();
        }
        double lb = lwSlider.getValue();
        newDataset = SimData.genDataset(simData, name, pars, lb);

        newDataset.setFreqDomain(0, true);
        newDataset.addProperty("SIM", name);
        return newDataset;
    }

    public SimpleObjectProperty<SimData> getSimData() {
        return currentSimData;
    }

    record BlockIndex(int block, int index, int iProp) {}

    private void updateGrid(SimData simData) {
        unBindProperties();
        gridPane.getChildren().clear();
        int nBlocks = simData.nBlocks();
        ppmProperties.clear();
        jProperties.clear();
        blockIndices.clear();
        ppmButtons.clear();
        coupButtons.clear();
        int iRow = 0;
        for (int iBlock = 0;iBlock<nBlocks;iBlock++) {
            SimData.AtomBlock atomBlock = simData.atomBlock(iBlock);
            int nPPMs = atomBlock.nPPMs();
            for (int iPPM=0;iPPM<nPPMs;iPPM++) {
                BlockIndex blockIndex = new BlockIndex(iBlock, iPPM, iRow);
                blockIndices.add(blockIndex);
                RadioButton ppmRadioButton = new RadioButton();
                ppmButtons.add(ppmRadioButton);
                ppmRadioButton.setToggleGroup(atomToggleGroup);
                ppmRadioButton.setText(String.valueOf(atomBlock.id(iPPM)));
                ppmRadioButton.setUserData(blockIndex);
                gridPane.add(ppmRadioButton, 0, iRow);
                if (iPPM== 0) {
                    atomToggleGroup.selectToggle(ppmRadioButton);
                }
                SimpleDoubleProperty ppmProperty = new SimpleDoubleProperty(atomBlock.ppm(iPPM));
                ppmProperties.add(ppmProperty);
                ppmProperty.addListener(propChangeListener);

                TextField ppmField = GUIUtils.getDoubleTextField(ppmProperty, 3);
                ppmField.setPrefWidth(70);
                gridPane.add(ppmField, 1, iRow);
                SimpleDoubleProperty jProperty = new SimpleDoubleProperty(0.0);
                jProperties.add(jProperty);
                jProperty.addListener(propChangeListener);

                TextField jField = GUIUtils.getDoubleTextField(jProperty, 2);
                jField.setPrefWidth(70);
                gridPane.add(jField, 2, iRow);
                RadioButton jRadioButton = new RadioButton();
                coupButtons.add(jRadioButton);
                jRadioButton.setUserData(blockIndex);
                jRadioButton.setToggleGroup(jToggleGroup);
                jRadioButton.setText(String.valueOf(atomBlock.id(iPPM)));
                gridPane.add(jRadioButton, 3, iRow);
                iRow++;

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

    private void updatePPMSlider(BlockIndex atomBlockIndex) {
        if ((atomBlockIndex != null) && (atomBlockIndex.iProp < ppmProperties.size())) {
            for (var ppmProp : ppmProperties) {
                ppmSlider.valueProperty().unbindBidirectional(ppmProp);
            }
            var ppmValue = ppmProperties.get(atomBlockIndex.iProp);
            double shift = ppmValue.doubleValue();
            double range = 0.1;
            ppmSlider.setMin(shift - range / 2.0);
            ppmSlider.setMax(shift + range / 2.0);
            ppmSlider.setValue(shift);
            ppmSlider.valueProperty().bindBidirectional(ppmValue);
        }
    }

    private void updateJProperties(BlockIndex atomBlockIndex, BlockIndex coupBlockIndex) {
        if (currentSimData.get() != null) {
            SimData.AtomBlock block = currentSimData.get().atomBlock(atomBlockIndex.block);
            for (var couplingProp : jProperties) {
                couplingSlider.valueProperty().unbindBidirectional(couplingProp);
                couplingProp.removeListener(propChangeListener);
            }
            for (BlockIndex blockIndex : blockIndices) {
                Double jValue;
                if (atomBlockIndex.block != blockIndex.block) {
                    jValue = 0.0;
                } else {
                    jValue = block.j(atomBlockIndex.index, blockIndex.index);
                }
                jValue = Math.abs(jValue);
                if (blockIndex.iProp < jProperties.size()) {
                    jProperties.get(blockIndex.iProp).set(jValue);
                }
            }
            if ((atomBlockIndex != null) && (coupBlockIndex != null) && (coupBlockIndex.iProp) < jProperties.size()) {
                couplingSlider.setValue(jProperties.get(coupBlockIndex.iProp).doubleValue());
                jProperties.get(coupBlockIndex.iProp).bindBidirectional(couplingSlider.valueProperty());
            }
            for (var couplingProp : jProperties) {
                couplingProp.addListener(propChangeListener);
            }
        }
    }

    private void unBindProperties() {
        for (var ppmProp : ppmProperties) {
            ppmSlider.valueProperty().unbindBidirectional(ppmProp);
        }
        for (var couplingProp : jProperties) {
            couplingSlider.valueProperty().unbindBidirectional(couplingProp);
            couplingProp.removeListener(propChangeListener);
        }
    }

    private void updateCouplingSlider(BlockIndex atomBlockIndex, BlockIndex coupBlockIndex) {
        if ((atomBlockIndex != null) && (atomBlockIndex.iProp < jProperties.size())) {
            for (var couplingProp : jProperties) {
                couplingSlider.valueProperty().unbindBidirectional(couplingProp);
            }
            var jProp = jProperties.get(coupBlockIndex.iProp);
            jProp.removeListener(propChangeListener);
            double jValue = jProp.doubleValue();
            couplingSlider.setMin(0);
            couplingSlider.setMax(20.0);
            couplingSlider.setValue(jValue);
            jProp.addListener(propChangeListener);
            jProperties.get(coupBlockIndex.iProp).bindBidirectional(couplingSlider.valueProperty());
        }
    }

    int getActiveJ(BlockIndex atomBlockIndex) {
        int jMax = -1;
        if (currentSimData.get() != null) {
            SimData.AtomBlock block = currentSimData.get().atomBlock(atomBlockIndex.block);
            int i = atomBlockIndex.index;
            double maxJ = 0.0;
            for (BlockIndex blockIndex : blockIndices) {
                if (blockIndex.block == atomBlockIndex.block) {
                    double jValue = block.j(i, blockIndex.index);
                    if (jValue > maxJ) {
                        maxJ = jValue;
                        jMax = blockIndex.iProp;
                    }
                }
            }
        }
        return jMax;
    }

    private void updateSelectedAtom(boolean ppmMode) {
        BlockIndex atomBlockIndex = null;
        BlockIndex coupBlockIndex = null;
        RadioButton atomRadioButton = (RadioButton) atomToggleGroup.getSelectedToggle();
        if (atomRadioButton != null) {
            atomBlockIndex = (BlockIndex) atomRadioButton.getUserData();
        }
        RadioButton jRadioButton = (RadioButton) jToggleGroup.getSelectedToggle();
        if (jRadioButton != null) {
            coupBlockIndex = (BlockIndex) jRadioButton.getUserData();
        }
        if (ppmMode) {
            updatePPMSlider(atomBlockIndex);
            updateJProperties(atomBlockIndex, coupBlockIndex);
            int jMax = getActiveJ(atomBlockIndex);
            if ((jMax != -1) && (jMax < coupButtons.size())) {
                coupButtons.get(jMax).setSelected(true);
            }
        } else {
            updateCouplingSlider(atomBlockIndex, coupBlockIndex);
        }
    }

    void updateActiveData() {
        Toggle atomToggle = atomToggleGroup.getSelectedToggle();

        BlockIndex atomBlockIndex = atomToggle != null ? (BlockIndex)  atomToggle.getUserData() : null;

        SimData simData = currentSimData.get();
        if (simData != null) {
            Dataset testDataset = Dataset.getDataset(simData.getName().toLowerCase());
            if (testDataset != null) {
                for (BlockIndex blockIndex : blockIndices) {
                    var ppmProp = ppmProperties.get(blockIndex.iProp);
                    simData.atomBlock(blockIndex.block).ppm(blockIndex.index, ppmProp.doubleValue());
                }
                for (BlockIndex blockIndex : blockIndices) {
                    int iBlock = atomBlockIndex.block;
                    int iIndex = atomBlockIndex.index;
                    int jBlock = blockIndex.block;
                    int jIndex = blockIndex.index;
                    if (iBlock == jBlock) {
                        simData.atomBlock(iBlock).j(iIndex, jIndex, jProperties.get(blockIndex.iProp).doubleValue());
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
                    currentSimMap.put(activeSimData.getName().toLowerCase(), activeSimData);
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

        double lb = lwSlider.getValue();
        double field = fieldSlider.getValue();
        double sw = AnalystPrefs.getLibraryVectorSWPPM() * field;
        newDataset.setSf(0, field);
        newDataset.setSw(0, sw);
        Vec vec = newDataset.getVec();
        SimData.genVec(simData, vec, lb);

        fxmlController.getStatusBar().setMode(SpectrumStatusBar.DataMode.DATASET_1D);

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
            double lb = lwSlider.getValue();

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
      //  regionChanged();
        showActiveData(activeMatch);
    }

    void optimize() {
        activateCurrent();
        CompoundFitter cFitter = CompoundFitter.setup(cmpdMatcher.getMatches());
        Vec fitVec = getExpVec();

        cFitter.setVec(fitVec);
        List<FitResult> fitResults = cFitter.optimizeAlignment();
        for (FitResult fitResult : fitResults) {
            System.out.println(fitResult.getShift() + " " + fitResult.getScale());
        }
        updateSumData();
      //  regionChanged();
        showActiveData(activeMatch);
    }
}
