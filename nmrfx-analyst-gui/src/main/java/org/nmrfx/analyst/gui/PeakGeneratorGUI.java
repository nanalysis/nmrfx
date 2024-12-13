package org.nmrfx.analyst.gui;

import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.analyst.gui.peaks.PeakMenuActions;
import org.nmrfx.analyst.peaks.PeakGenerator;
import org.nmrfx.analyst.peaks.PeakGenerator.PeakGeneratorTypes;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.PeakLinker;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.utils.ToolBarUtils;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.rna.RNALabels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.nmrfx.analyst.peaks.PeakGenerator.PeakGeneratorTypes.*;

public class PeakGeneratorGUI {
    private static final Logger log = LoggerFactory.getLogger(PeakGeneratorGUI.class);
    Stage stage;
    private TextField simPeakListNameField;
    private ComboBox<String> simDatasetNameField;
    ChoiceBox<Integer> ppmSetChoice;
    ChoiceBox<Integer> refSetChoice;

    private Label typeLabel;
    private Label subTypeLabel;
    private GridPane peakListParsPane;
    TextField[][] peakListParFields;
    private VBox optionBox;
    Button generateButton;
    Button inspectButton;
    Button tableButton;
    Label statusLabel;
    SimpleObjectProperty<PeakList> peakListProperty = new SimpleObjectProperty<>(null);

    private final Slider distanceSlider = new Slider(2, 7.0, 5.0);
    private final ChoiceBox<Integer> transferLimitChoice = new ChoiceBox<>();
    private final CheckBox useNCheckBox = new CheckBox("UseN");
    private final CheckBox requireActiveCheckBox = new CheckBox("Require Active");
    PeakGeneratorTypes peakGeneratorType = null;
    double sfH = 700.0;

    public void create() {
        stage = new Stage(StageStyle.DECORATED);
        BorderPane borderPane = new BorderPane();
        Scene scene = new Scene(borderPane);
        stage.setScene(scene);
        scene.getStylesheets().add("/styles/Styles.css");
        stage.setTitle("Peak Generator");
        ToolBar toolBar = new ToolBar();
        ToolBar toolBar2 = new ToolBar();
        VBox toolBarBox = new VBox();
        toolBarBox.getChildren().addAll(toolBar, toolBar2);
        borderPane.setTop(toolBarBox);
        borderPane.setPrefWidth(500);
        borderPane.setPrefHeight(350);
        MenuButton simTypeMenu = new MenuButton("Experiment Type");
        Label datasetLabel = new Label("Dataset:");
        simDatasetNameField = new ComboBox<>();
        simDatasetNameField.setMinWidth(150);
        toolBar.getItems().addAll(simTypeMenu, ToolBarUtils.makeFiller(20), datasetLabel, simDatasetNameField);

        ppmSetChoice = new ChoiceBox<>();
        refSetChoice = new ChoiceBox<>();
        for (int iSet = -1; iSet < 5; iSet++) {
            ppmSetChoice.getItems().add(iSet);
            refSetChoice.getItems().add(iSet);
        }
        ppmSetChoice.setValue(0);
        refSetChoice.setValue(0);
        toolBar2.getItems().addAll(new Label("PPM Set:"), ppmSetChoice,
                new Label("Ref Set:"), refSetChoice);

        VBox vBox = new VBox();
        vBox.setSpacing(15);
        borderPane.setCenter(vBox);
        HBox hBox = new HBox();
        typeLabel = new Label();
        typeLabel.setMinWidth(150);
        subTypeLabel = new Label();
        subTypeLabel.setMinWidth(150);
        hBox.getChildren().addAll(typeLabel, subTypeLabel);
        peakListParsPane = new GridPane();
        optionBox = new VBox();
        vBox.getChildren().addAll(hBox, peakListParsPane, optionBox);
        Label listLabel = new Label("List Name");
        simPeakListNameField = new TextField();
        HBox.setHgrow(simPeakListNameField, Priority.ALWAYS);
        generateButton = new Button("Generate");
        generateButton.setOnAction(e -> genPeaksAction());

        tableButton = new Button("Table");
        tableButton.setOnAction(e -> showPeakTable());
        tableButton.disableProperty().bind(peakListProperty.isNull());

        inspectButton = new Button("Inspect");
        inspectButton.setOnAction(e -> showPeakInspector());
        inspectButton.disableProperty().bind(peakListProperty.isNull());

        VBox bottomBox = new VBox();
        borderPane.setBottom(bottomBox);
        HBox controlBox = new HBox();
        controlBox.getChildren().addAll(listLabel, simPeakListNameField, generateButton, tableButton, inspectButton);
        HBox statusBox = new HBox();
        statusLabel = new Label();
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        statusBox.getChildren().add(statusLabel);
        bottomBox.getChildren().addAll(controlBox, statusBox);

        simDatasetNameField.setOnShowing(e -> updateSimDatasetNames());
        simDatasetNameField.setOnAction(e -> selectSimDataset());
        PeakGeneratorTypes[] basicTypes = {Proton_1D, HSQC_13C, HSQC_15N, HMBC, TOCSY, NOESY};
        PeakGeneratorTypes[] rnaTypes = {RNA_NOESY_2nd_str};
        PeakGeneratorTypes[] proteinTypes = {HNCO, HNCOCA, HNCOCACB, HNCACO, HNCA, HNCACB};
        String[] types = {"Basic", "Protein", "RNA"};
        Map<String, PeakGeneratorTypes[]> subTypeMap = new HashMap<>();
        subTypeMap.put("Basic", basicTypes);
        subTypeMap.put("RNA", rnaTypes);
        subTypeMap.put("Protein", proteinTypes);
        for (String type : types) {
            Menu menu = new Menu(type);
            simTypeMenu.getItems().add(menu);
            PeakGeneratorTypes[] subTypes = subTypeMap.get(type);
            for (PeakGeneratorTypes subType : subTypes) {
                MenuItem item = new MenuItem(subType.name());
                item.setOnAction(e -> setType(type, subType));
                menu.getItems().add(item);
            }
        }
        initOptions();
    }

    public void show(double x, double y) {
        stage.show();
        stage.toFront();
        double screenWidth = Screen.getPrimary().getBounds().getWidth();
        if (x > (screenWidth / 2)) {
            x = x - stage.getWidth() - 0;
        } else {
            x = x + 100;
        }

        y = y - stage.getHeight() / 2.0;

        stage.setX(x);
        stage.setY(y);
        peakListProperty.set(null);
        statusLabel.setText("");
    }

    public void updateSimDatasetNames() {
        simDatasetNameField.getItems().clear();
        simDatasetNameField.getItems().add("Sim");

        DatasetBase.datasets().stream().sorted().forEach(d -> simDatasetNameField.getItems().add(d.getName()));
        peakListProperty.set(null);
        statusLabel.setText("");
    }


    void selectSimDataset() {
        String name = simDatasetNameField.getValue();
        if (name != null) {
            if (!name.equals("Sim")) {
                Dataset dataset = Dataset.getDataset(name);
                if (dataset != null) {
                    int nDim = dataset.getNDim();
                    if (peakListParFields.length < nDim) {
                        initPeakListParFields(nDim);
                    }
                    for (int i = 0; i < nDim; i++) {
                        peakListParFields[i][0].setText(String.valueOf(dataset.getSw(i)));
                        peakListParFields[i][1].setText(String.valueOf(dataset.getSf(i)));
                        peakListParFields[i][2].setText(dataset.getLabel(i));
                        for (int j = 0; j < peakListParFields[i].length; j++) {
                            peakListParFields[i][j].setDisable(true);
                        }
                    }

                }
                int dotIndex = name.indexOf(".");
                if (dotIndex != -1) {
                    name = name.substring(0, dotIndex);
                }
                simPeakListNameField.setText(name + "_sim");
                generateButton.setDisable(false);

            } else {
                simPeakListNameField.setText(peakGeneratorType + "_sim");
                generateButton.setDisable(false);
            }
        }
        peakListProperty.set(null);
        statusLabel.setText("");
    }

    void initOptions() {
        transferLimitChoice.getItems().add(1);
        transferLimitChoice.getItems().add(2);
        transferLimitChoice.getItems().add(3);
        transferLimitChoice.getItems().add(4);
        transferLimitChoice.getItems().add(5);
        transferLimitChoice.setValue(2);
        distanceSlider.setShowTickLabels(true);
        distanceSlider.setShowTickMarks(true);
        distanceSlider.setMajorTickUnit(1.0);
        distanceSlider.setMinorTickCount(9);
        distanceSlider.setMinWidth(250.0);

    }

    void setType(String type, PeakGeneratorTypes subType) {
        this.peakGeneratorType = subType;
        typeLabel.setText(type);
        subTypeLabel.setText(subType.name());
        generateButton.setDisable(true);
        simDatasetNameField.setValue(null);
        updateOptions();
    }

    void initPeakListParFields(int nDim) {
        peakListParsPane.getChildren().clear();
        String[] headers = {"Dim", "SW", "SF", "Label"};
        int col = 0;
        for (String header : headers) {
            peakListParsPane.add(new Label(header), col++, 0);
        }
        peakListParFields = new TextField[nDim][3];
        for (int iDim = 0; iDim < nDim; iDim++) {
            Label dimLabel = new Label(String.valueOf(iDim + 1));
            dimLabel.setMinWidth(50);
            peakListParsPane.add(dimLabel, 0, iDim + 1);
            for (int i = 0; i < 3; i++) {
                peakListParFields[iDim][i] = new TextField();
                peakListParsPane.add(peakListParFields[iDim][i], 1 + i, iDim + 1);

            }
        }
    }

    void updateOptions() {
        optionBox.getChildren().clear();
        List<String> labels = new ArrayList<>();
        double swP = 10.0;
        double[] ratios = {1, 1, 1};

        if (peakGeneratorType != null) {
            switch (peakGeneratorType) {
                case TOCSY -> {
                    Label label = new Label("Transfers");
                    HBox hBox = new HBox();
                    hBox.setSpacing(10);
                    hBox.getChildren().add(label);
                    hBox.getChildren().add(transferLimitChoice);
                    optionBox.getChildren().add(hBox);
                    labels.add("H");
                    labels.add("H2");
                }
                case NOESY -> {
                    Label label = new Label("Distance");
                    HBox hBox = new HBox();
                    hBox.setSpacing(10);
                    hBox.getChildren().add(label);
                    hBox.getChildren().add(distanceSlider);
                    optionBox.getChildren().add(hBox);
                    optionBox.getChildren().add(useNCheckBox);
                    optionBox.getChildren().add(requireActiveCheckBox);

                    labels.add("H");
                    labels.add("H2");
                }
                case HSQC_15N -> {
                    labels.add("H");
                    labels.add("N");
                    ratios[1] = 2.0;
                }
                case HSQC_13C -> {
                    labels.add("H");
                    labels.add("C");
                    ratios[1] = 10.0;
                }
                case HMBC -> {
                    Label label = new Label("Transfers");
                    HBox hBox = new HBox();
                    hBox.setSpacing(10);
                    hBox.getChildren().add(label);
                    hBox.getChildren().add(transferLimitChoice);
                    optionBox.getChildren().add(hBox);
                    labels.add("H");
                    labels.add("C");
                    ratios[1] = 10.0;
                }
                case RNA_NOESY_2nd_str -> {
                    labels.add("H");
                    labels.add("H2");
                    optionBox.getChildren().add(useNCheckBox);
                    optionBox.getChildren().add(requireActiveCheckBox);
                }
                case Proton_1D -> labels.add("H");
                case HNCO, HNCACO -> {
                    labels.add("H");
                    labels.add("N");
                    labels.add("C");
                    ratios[1] = 2.0;
                    ratios[2] = 2.0;
                }
                case HNCOCA, HNCOCACB, HNCA, HNCACB -> {
                    labels.add("H");
                    labels.add("N");
                    ratios[1] = 2.0;
                    labels.add("CA");
                    ratios[2] = 5.0;
                }
            }
            int nDim = labels.size();
            initPeakListParFields(nDim);
            for (int iDim = 0; iDim < nDim; iDim++) {
                for (int i = 0; i < 3; i++) {
                    double sf = sfH * Nuclei.findNuclei(labels.get(iDim).substring(0, 1)).getFreqRatio();
                    switch (i) {
                        case 0 -> {
                            double sw = swP * ratios[i] * sf;
                            peakListParFields[iDim][i].setText(String.valueOf(sw));
                        }
                        case 1 -> peakListParFields[iDim][i].setText(String.valueOf(sf));
                        case 2 -> peakListParFields[iDim][i].setText(labels.get(iDim));
                        default -> throw new IllegalStateException("Unexpected value: " + i);
                    }
                    peakListParFields[iDim][i].setDisable(false);
                }
            }
            peakListParFields[0][1].setOnKeyPressed(k -> {
                if (k.getCode() == KeyCode.ENTER) {
                    try {
                        sfH = Double.parseDouble(peakListParFields[0][1].getText());
                    } catch (NumberFormatException nfE) {
                        log.warn("Unable to parse sfH.", nfE);
                    }
                    updateOptions();
                }
            });
        }
        peakListProperty.set(null);
        statusLabel.setText("");
    }


    PeakList makePeakListFromPars() {
        String listName = simPeakListNameField.getText();
        int nDim = peakListParFields.length;
        PeakList newPeakList = new PeakList(listName, nDim);
        try {
            for (int iDim = 0; iDim < nDim; iDim++) {
                newPeakList.getSpectralDim(iDim).setSw(Double.parseDouble(peakListParFields[iDim][0].getText()));
                newPeakList.getSpectralDim(iDim).setSf(Double.parseDouble(peakListParFields[iDim][1].getText()));
                newPeakList.getSpectralDim(iDim).setDimName(peakListParFields[iDim][2].getText());
            }
            newPeakList.setSampleConditionLabel("sim");
        } catch (NumberFormatException nfE) {
            PeakList.remove(listName);
            newPeakList = null;
        }
        return newPeakList;
    }

    PeakList makePeakListFromDataset(Dataset dataset) {
        String listName = simPeakListNameField.getText();
        int nDim = dataset.getNDim();
        PeakList newPeakList = new PeakList(listName, nDim);
        for (int iDim = 0; iDim < nDim; iDim++) {
            newPeakList.getSpectralDim(iDim).setSw(dataset.getSw(iDim));
            newPeakList.getSpectralDim(iDim).setSf(dataset.getSf(iDim));
            newPeakList.getSpectralDim(iDim).setDimName(dataset.getLabel(iDim));
        }
        newPeakList.setSampleConditionLabel("sim");
        newPeakList.setDatasetName(dataset.getName());
        return newPeakList;
    }

    public void genPeaksAction() {
        if (peakGeneratorType != null) {
            PeakList newPeakList;
            Dataset dataset = null;
            String datasetName;
            if ((simDatasetNameField.getValue() == null) || simDatasetNameField.getValue().equals("Sim")) {
                newPeakList = makePeakListFromPars();
                if (newPeakList == null) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setContentText("Could not make peakList, invalid parameters");
                    alert.showAndWait();
                    return;
                }
            } else {
                datasetName = simDatasetNameField.getValue();
                dataset = Dataset.getDataset(datasetName);
                newPeakList = makePeakListFromDataset(dataset);
            }
            peakListProperty.set(newPeakList);
            switch (peakGeneratorType) {
                case HNCO, HNCOCA, HNCOCACB, HNCACO, HNCA, HNCACB ->
                        makeProteinPeakList(dataset, newPeakList, peakGeneratorType);
                case NOESY -> {
                    double range = distanceSlider.getValue();
                    try {
                        makeNOESY(newPeakList, range);
                    } catch (InvalidMoleculeException e) {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setContentText("Could not make NOESY list " + e.getMessage());
                        alert.showAndWait();
                        return;
                    }
                }
                case TOCSY -> {
                    int tocsyLimit = transferLimitChoice.getValue();
                    makeTocsy(newPeakList, tocsyLimit);
                }
                case HMBC -> {
                    int hmbcLimit = transferLimitChoice.getValue();
                    makeHMBC(newPeakList, hmbcLimit);
                }
                case HSQC_13C -> makeHSQC(newPeakList, 6);
                case HSQC_15N -> makeHSQC(newPeakList, 7);
                case RNA_NOESY_2nd_str -> makeRNANOESYSecStr(dataset, newPeakList);
                case Proton_1D -> makeProton1D(newPeakList);
            }
            statusLabel.setText(String.format("Created %d peaks", newPeakList.size()));
            PeakLinker linker = new PeakLinker();
            linker.linkAllPeakListsByLabel("sim");
            newPeakList.setSlideable(true);
        }
    }

    void setSelectedAtoms() {
        String name = simDatasetNameField.getValue();
        if (name != null) {
            if (!name.equals("Sim")) {
                Dataset dataset = Dataset.getDataset(name);
                Molecule molecule = Molecule.getActive();
                if ((dataset != null) && (molecule != null)){
                    String labelScheme = dataset.getProperty("labelScheme");
                    RNALabels rnaLabels = new RNALabels();
                    rnaLabels.parseSelGroups(molecule, labelScheme);
                }
            }
        }
    }
    private void makeProteinPeakList(Dataset dataset, PeakList peakList, PeakGeneratorTypes expType) {
        PeakGenerator peakGenerator = new PeakGenerator(ppmSetChoice.getValue(), refSetChoice.getValue());
        peakGenerator.generateProteinPeaks(dataset, peakList, expType);
    }

    private void makeTocsy(PeakList peakList, int limit) {
        peakList.getSpectralDim(0).setPattern("i.H*");
        peakList.getSpectralDim(1).setPattern("i.H*");
        PeakGenerator peakGenerator = new PeakGenerator(ppmSetChoice.getValue(), refSetChoice.getValue());
        peakGenerator.generateTOCSY(peakList, limit);
    }

    private void makeHSQC(PeakList peakList, int parentElement) {
        peakList.getSpectralDim(0).setPattern("i.H*");
        String idPattern = parentElement == 6 ? "i.C*" : "i.N*";
        peakList.getSpectralDim(1).setPattern(idPattern);
        peakList.getSpectralDim(0).setRelation(peakList.getSpectralDim(1).getDimName());
        PeakGenerator peakGenerator = new PeakGenerator(ppmSetChoice.getValue(), refSetChoice.getValue());
        peakGenerator.generateHSQC(peakList, parentElement);
    }

    private void makeHMBC(PeakList peakList, int limit) {
        peakList.getSpectralDim(0).setPattern("i.H*");
        peakList.getSpectralDim(1).setPattern("i.H*");
        PeakGenerator peakGenerator = new PeakGenerator(ppmSetChoice.getValue(), refSetChoice.getValue());
        peakGenerator.generateHMBC(peakList, limit);
    }

    private void makeProton1D(PeakList peakList) {
        peakList.getSpectralDim(0).setPattern("i.H*");
        PeakGenerator peakGenerator = new PeakGenerator(ppmSetChoice.getValue(), refSetChoice.getValue());
        peakGenerator.generate1DProton(peakList);
    }

    private void makeNOESY(PeakList peakList, double tol) throws InvalidMoleculeException {
        peakList.getSpectralDim(0).setPattern("i.H*");
        peakList.getSpectralDim(1).setPattern("j.H*");
        PeakGenerator peakGenerator = new PeakGenerator(ppmSetChoice.getValue(), refSetChoice.getValue());
        if (requireActiveCheckBox.isSelected()) {
            setSelectedAtoms();
        }
        peakGenerator.generateNOESY(peakList, tol, useNCheckBox.isSelected(), requireActiveCheckBox.isSelected());
    }

    private void makeRNANOESYSecStr(Dataset dataset, PeakList peakList) {
        peakList.getSpectralDim(0).setPattern("i.H*");
        peakList.getSpectralDim(1).setPattern("j.H*");
        boolean useN = useNCheckBox.isSelected();
        boolean reqActive = requireActiveCheckBox.isSelected();
        PeakGenerator peakGenerator = new PeakGenerator(ppmSetChoice.getValue(), refSetChoice.getValue());
        peakGenerator.generateRNANOESYSecStr(dataset, peakList, useN, reqActive);

    }

    void showPeakTable() {
        PeakMenuActions.showPeakTable(peakListProperty.get());
    }

    void showPeakInspector() {
        FXMLController controller = AnalystApp.getFXMLControllerManager().getOrCreateActiveController();
        controller.showPeakAttr();
        controller.getPeakAttrController().setPeakList(peakListProperty.get());
    }
}
