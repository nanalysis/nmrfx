package org.nmrfx.analyst.gui.molecule3D;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.molecule.SSViewer;
import org.nmrfx.analyst.models.ModelFetcher;
import org.nmrfx.analyst.peaks.PeakGenerator;
import org.nmrfx.chemistry.*;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.events.FreezeListener;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.PreferencesController;
import org.nmrfx.processor.gui.utils.AtomUpdater;
import org.nmrfx.processor.tools.RNAMatcher;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.rna.RNAAnalysis;
import org.nmrfx.structure.rna.RNALabels;
import org.nmrfx.structure.rna.SSLayout;
import org.nmrfx.structure.rna.SSPredictor;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.nmrfx.analyst.gui.molecule3D.StructureCalculator.StructureMode.NUCLEIC_ACID;

public class RNASSViewController implements Initializable, StageBasedController, MolSelectionListener, FreezeListener, MoleculeListener {
    private static final Logger log = LoggerFactory.getLogger(RNASSViewController.class);
    private static final Background ERROR_BACKGROUND = new Background(new BackgroundFill(Color.ORANGE, CornerRadii.EMPTY, Insets.EMPTY));
    private Stage stage;
    SSViewer ssViewer;
    MolSceneController molSceneController;

    @FXML
    TextField dotBracketField;
    @FXML
    Pane dotBracketPane;
    @FXML
    ChoiceBox<SecondaryStructureEntry> ssChoiceBox;
    @FXML
    MenuButton atomMenu;
    @FXML
    BorderPane ssBorderPane;
    @FXML
    CheckBox ssDisplayCheckBox;
    @FXML
    Slider thresholdSlider;
    @FXML
    TextField thresholdField;
    @FXML
    CheckBox pseudoKnotCheckBox;
    @FXML
    CheckBox mapDisplayCheckBox;
    CheckMenuItem frozenCheckBox = new CheckMenuItem("Frozen");
    CheckMenuItem activeCheckBox = new CheckMenuItem("Active");
    CheckMenuItem numbersCheckBox = new CheckMenuItem("Numbers");
    CheckMenuItem probabilitiesCheckBox = new CheckMenuItem("Probabilities");
    ToggleGroup predictionTypeGroup = new ToggleGroup();
    @FXML
    ChoiceBox<String> constraintTypeChoiceBox;
    @FXML
    MenuButton peakListMenuButton;
    @FXML
    MenuButton modeMenuButton;
    @FXML
    Label rnaSecStructureScoreLabel;
    SSPredictor ssPredictor = null;
    PeakList peakList = null;

    List<String> fileSecondaryStructures = new ArrayList<>();


    Map<String, Double> rnaStructureScores = new HashMap<>();
    List<CheckMenuItem> atomCheckItems = new ArrayList<>();
    List<CheckMenuItem> peakClassCheckItems = new ArrayList<>();
    Background defaultBackground = new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY));


    enum SSOrigin {
        PRED,
        FILE,
        BOTH
    }

    record SecondaryStructureEntry(String dotBracket, SSOrigin type, int pIindex, int fIndex) {
        @Override
        public String toString() {
            return switch (type) {
                case PRED -> type + ":" + pIindex;
                case FILE -> type + ":" + fIndex;
                case BOTH -> SSOrigin.PRED + ":" + pIindex + " " + SSOrigin.FILE + ":" + fIndex;
            };
        }
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    public static RNASSViewController create(MolSceneController molSceneController) {
        RNASSViewController controller = Fxml.load(RNASSViewController.class, "RNASSScene.fxml")
                .withNewStage("Nucleic Acid Viewer")
                .getController();
        controller.molSceneController = molSceneController;
        controller.stage.show();
        if (Molecule.getActive() != null) {
            try {
                controller.layoutSS();
            } catch (InvalidMoleculeException e) {
                // catch exception if initial creation fails
            }
        }
        return controller;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        ssViewer = new SSViewer();
        ssBorderPane.setCenter(ssViewer);
        ssViewer.getDrawNumbersProp().bind(numbersCheckBox.selectedProperty());
        ssViewer.getDrawProbabilitiesProp().bind(probabilitiesCheckBox.selectedProperty());
        ssViewer.getShowActiveProp().bind(activeCheckBox.selectedProperty());
        dotBracketField.setEditable(true);
        dotBracketField.textProperty().addListener(e -> dotBracketFieldChanged());
        constraintTypeChoiceBox.getItems().addAll("All", "Intraresidue", "Interresidue");
        constraintTypeChoiceBox.setValue("All");
        ssViewer.getConstraintTypeProp().bind(constraintTypeChoiceBox.valueProperty());


        ssViewer.getDrawMapProp().bindBidirectional(mapDisplayCheckBox.selectedProperty());
        ssViewer.getDrawSSProp().bindBidirectional(ssDisplayCheckBox.selectedProperty());
        mapDisplayCheckBox.setSelected(false);
        ssDisplayCheckBox.setSelected(true);
        ssDisplayCheckBox.setOnAction(e -> ssViewer.resizeWindow());
        mapDisplayCheckBox.setOnAction(e -> ssViewer.resizeWindow());
        GUIUtils.bindSliderField(thresholdSlider, thresholdField,"#.##");
        thresholdSlider.setValue(0.5);


        peakListMenuButton.showingProperty().addListener((a, b, c) -> {
            if (c) {
                updatePeakListMenu();
            }
        });

        updatePeakListMenu();
        modeMenuButton.getItems().add(numbersCheckBox);
        modeMenuButton.getItems().add(probabilitiesCheckBox);
        modeMenuButton.getItems().add(activeCheckBox);
        modeMenuButton.getItems().add(frozenCheckBox);
        Menu predictionMenu = new Menu("Predictions");
        modeMenuButton.getItems().add(predictionMenu);
        RadioMenuItem hydrogenMenuItem = new RadioMenuItem("Show H-predictions");
        RadioMenuItem carbonMenuItem = new RadioMenuItem("Show C-predictions");
        hydrogenMenuItem.setToggleGroup(predictionTypeGroup);
        carbonMenuItem.setToggleGroup(predictionTypeGroup);
        hydrogenMenuItem.setSelected(true);
        carbonMenuItem.setSelected(false);
        predictionMenu.getItems().addAll(hydrogenMenuItem, carbonMenuItem);
        hydrogenMenuItem.selectedProperty().bindBidirectional(ssViewer.getHydrogenPredictionProp());

        frozenCheckBox.selectedProperty().addListener(e -> updatePeaks());

        String[] atomMenuNames = {"Ribose", "Base", "Exchangeable"};
        String[] riboseAtoms = {"H1'", "H2'", "H3'", "H4'", "H5'"};
        for (String name : atomMenuNames) {
            CheckMenuItem menuItem = new CheckMenuItem(name);
            atomCheckItems.add(menuItem);
            atomMenu.getItems().add(menuItem);
            menuItem.selectedProperty().addListener(
                    (a, b, c) -> updateAtoms(name, c));
        }
        Menu riboseMenu = new Menu("Ribose Atoms");
        atomMenu.getItems().add(riboseMenu);
        for (String name : riboseAtoms) {
            CheckMenuItem menuItem = new CheckMenuItem(name);
            atomCheckItems.add(menuItem);
            riboseMenu.getItems().add(menuItem);
            menuItem.selectedProperty().addListener(
                    (a, b, c) -> updateAtoms());
        }
        Menu peakClassMenu = new Menu("Peak Intensities");
        modeMenuButton.getItems().add(peakClassMenu);
        String[] peakClasses = {"s", "m", "w", "vw"};
        for (String name : peakClasses) {
            CheckMenuItem menuItem = new CheckMenuItem(name);
            menuItem.setSelected(true);
            peakClassCheckItems.add(menuItem);
            peakClassMenu.getItems().add(menuItem);
            menuItem.selectedProperty().addListener(
                    (a, b, c) -> updatePeaks());
        }
        ssChoiceBox.setDisable(true);
        ssChoiceBox.setOnAction(e -> showSelectedSS());
        thresholdSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging) {
                updateThreshold();
            }
        });
        pseudoKnotCheckBox.setOnAction(e -> updateThreshold());

    }
    public void moleculeChanged(MoleculeEvent e) {
        Fx.runOnFxThread(ssViewer::drawSS);
    }

    public void scoreSecStructures(ActionEvent event) {
        RNAMatcher rnaMatcher = new RNAMatcher();
        Molecule molecule = Molecule.getActive();
        String currentDotBracket = molecule.getDotBracket();
        rnaStructureScores.clear();
        ssChoiceBox.getItems().forEach(ss -> {
            molecule.setDotBracket(ss.dotBracket);
            rnaMatcher.predict();
            rnaMatcher.genPeaks();
            double score = rnaMatcher.score();
            rnaStructureScores.put(ss.dotBracket, score);
        });
        molecule.setDotBracket(currentDotBracket);
        showRNAPeakScore(currentDotBracket);
    }

    @FXML
    void loadFromFile() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                List<String> lines = Files.readAllLines(file.toPath());
                storeSecondaryStructures(lines);
                updateSSChoiceBox();
            } catch (IOException e) {
                GUIUtils.warn("Error reading Secondary Structure File", e.getMessage());
            }
        }
    }

    void storeSecondaryStructures(List<String> lines) {
        fileSecondaryStructures.clear();
        for (String line : lines) {
            if (!line.isBlank()) {
                fileSecondaryStructures.add(line.trim());
            }
        }
    }

    public void selectSecStructure() {
        Molecule molecule = Molecule.getActive();
        molecule.setDotBracket(ssChoiceBox.getValue().dotBracket);
        RNAMatcher rnaMatcher = new RNAMatcher();
        rnaMatcher.predict();
        rnaMatcher.genPeaks();
    }

    private void updateAtoms(String name, boolean selected) {
        if (name.equals("Ribose")) {
            for (var menuItem : atomCheckItems) {
                if (menuItem.getText().endsWith("'")) {
                    menuItem.setSelected(selected);
                }
            }
        } else {
            updateAtoms();
        }
    }

    private void updateAtoms() {
        List<String> atomNames = new ArrayList<>();
        for (var menuItem : atomCheckItems) {
            if (menuItem.isSelected()) {
                atomNames.add(menuItem.getText());
            }
        }
        ssViewer.updateAtoms(atomNames);
    }

    @FXML
    void printSS() {
        ssViewer.print();
    }

    @FXML
    void layoutSS() throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No molecule present", ButtonType.CLOSE);
            alert.showAndWait();
        } else {

            if (molecule.getDotBracket().isEmpty()) {
                initWithAllDots();
            }
            String dotBracket = molecule.getDotBracket();
            if (dotBracket.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "No RNA present", ButtonType.CLOSE);
                alert.showAndWait();
            } else {
                updateDotBracket(dotBracket);
                SSLayout ssLayout = SSLayout.createLayout(molecule);
                ssLayout.interpVienna(molecule.getDotBracket());
                ssLayout.fillPairsNew();

                ssViewer.loadCoordinates(ssLayout);
                ssViewer.drawSS();
            }
            AtomUpdater atomUpdater = new AtomUpdater(Molecule.getActive());
            Molecule.getActive().registerUpdater(atomUpdater);
            Molecule.getActive().registerAtomChangeListener(this);
        }
    }

    void initWithAllDots() {
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            List<List<String>> seqs = SSLayout.setupSequence(molecule);
            int nChars = 0;
            for (List<String> seq : seqs) {
                nChars += seq.size();
            }
            molecule.setDotBracket(".".repeat(Math.max(0, nChars)));
        }
    }

    public boolean updateDotBracket(String dotBracket) throws InvalidMoleculeException {
        dotBracketPane.getChildren().clear();
        Molecule molecule = Molecule.getActive();
        boolean ok = false;

        if (molecule != null) {
            List<List<String>> seqs = SSLayout.setupSequence(molecule);
            double width = dotBracketPane.getWidth();
            int nChars = 0;
            for (List<String> seq : seqs) {
                nChars += seq.size();
            }
            int fontSize = (int) Math.round(width / nChars);
            if (fontSize > 20) {
                fontSize = 20;
            } else if (fontSize < 6) {
                fontSize = 6;
            }
            Font font = Font.font(fontSize);
            int iChar = 0;
            double start = (width - nChars * fontSize) / 2;
            for (List<String> seq : seqs) {
                for (String seqChar : seq) {
                    Text textItem = new Text(start + iChar * fontSize, fontSize, seqChar.substring(0, 1));
                    textItem.setFont(font);
                    dotBracketPane.getChildren().add(textItem);
                    iChar++;
                }
            }
            int nLeft = 0;
            int nRight = 0;
            for (int i = 0; i < dotBracket.length(); i++) {
                String dotChar = dotBracket.substring(i, i + 1);
                Text textItem = new Text(start + i * fontSize, 2 * fontSize, dotChar);
                textItem.setFont(font);
                final int dPos = i;
                textItem.setOnMouseClicked(e -> {
                    try {
                        toggleChar(dotBracket, dPos);
                    } catch (InvalidMoleculeException ex) {
                        log.error(ex.getMessage(), ex);
                    }
                });
                dotBracketPane.getChildren().add(textItem);
                if (dotChar.equals(")")) {
                    nRight++;
                } else if (dotChar.equals("(")) {
                    nLeft++;
                }
            }
            ok = (dotBracket.length() == nChars) && (nLeft == nRight);
            if ((dotBracket.length() == nChars) && (nLeft == nRight)) {
                dotBracketPane.setBackground(defaultBackground);
            } else {
                dotBracketPane.setBackground(ERROR_BACKGROUND);
            }

        }
        return ok;
    }

    void dotBracketFieldChanged() {
        try {
            String dotBracket = dotBracketField.getText().trim();
            if (!dotBracket.isEmpty()) {
                boolean ok = updateDotBracket(dotBracket);
                if (ok) {
                    Molecule mol = Molecule.getActive();
                    if (mol != null) {
                        mol.setDotBracket(dotBracket);
                        layoutSS();
                    }
                }
            }
        } catch (InvalidMoleculeException ex) {
            log.error(ex.getMessage(), ex);
        }

    }

    void toggleChar(String dotBracket, int iChar) throws InvalidMoleculeException {
        int nChar = dotBracket.length();
        char leftChar = iChar > 0 ? dotBracket.charAt(iChar - 1) : '(';
        char rightChar = iChar < nChar - 1 ? dotBracket.charAt(iChar + 1) : '_';
        char dChar = dotBracket.charAt(iChar);
        char newChar;
        switch (dChar) {
            case '(':
                newChar = ')';
                break;
            case ')':
                newChar = '.';
                break;
            case '.':
                if (leftChar == '(') {
                    newChar = '(';
                } else if (rightChar == ')') {
                    newChar = ')';
                } else if (iChar < (nChar / 2)) {
                    newChar = '(';
                } else {
                    newChar = ')';
                }
                break;
            default:
                newChar = '.';
        }
        StringBuilder newDotBracket = new StringBuilder(dotBracket);
        newDotBracket.setCharAt(iChar, newChar);
        boolean ok = updateDotBracket(newDotBracket.toString());
        if (ok) {
            Molecule mol = Molecule.getActive();
            if (mol != null) {
                mol.setDotBracket(newDotBracket.toString());
                layoutSS();
            }
        }
    }


    @FXML
    void ssFrom3D() throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No molecule present", ButtonType.CLOSE);
            alert.showAndWait();
        } else {
            char[] vienna = RNAAnalysis.getViennaSequence(molecule);
            String newDotBracket = new String(vienna);
            molecule.setDotBracket(newDotBracket);

            if (molecule.getDotBracket().isEmpty()) {
                initWithAllDots();
            }
            String dotBracket = molecule.getDotBracket();
            if (dotBracket.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "No RNA present", ButtonType.CLOSE);
                alert.showAndWait();
            } else {
                updateDotBracket(dotBracket);
                SSLayout ssLayout = SSLayout.createLayout(molecule);
                ssLayout.interpVienna(molecule.getDotBracket());
                ssLayout.fillPairsNew();

                ssViewer.loadCoordinates(ssLayout);
                ssViewer.drawSS();
            }
        }
    }

    public void clearSS() {
        dotBracketPane.getChildren().clear();
        ssViewer.clear();
        dotBracketField.clear();
    }

    @FXML
    private void ssTo3D() {
        molSceneController.structureCalculator.setMode(NUCLEIC_ACID);
        molSceneController.calcStructure();
    }


    boolean fetchSSModel() {
        try {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            File outDirectory = directoryChooser.showDialog(null);
            if (outDirectory != null) {
                String modelName = "rna_bp_v1";
                ModelFetcher.fetch(outDirectory.toPath(), modelName);
                Path path = outDirectory.toPath().resolve(modelName);
                PreferencesController.setRNAModelDirectory(path.toString());
                SSPredictor.setModelFile(path.toString());
                return true;
            }
        } catch (IOException e) {
            ExceptionDialog exceptionDialog = new ExceptionDialog(e);
            exceptionDialog.showAndWait();
        }
        return false;
    }

    @FXML
    private void seqTo2D() {
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            ssPredictor = new SSPredictor();
            String rnaModelDir = PreferencesController.getRNAModelDirectory();
            if (rnaModelDir.isEmpty()) {
                DirectoryChooser directoryChooser = new DirectoryChooser();
                File file = directoryChooser.showDialog(null);
                if (file == null) {
                    return;
                } else {
                    PreferencesController.setRNAModelDirectory(file.toString());
                    SSPredictor.setModelFile(file.toString());
                }
            } else {
                SSPredictor.setModelFile(rnaModelDir);
            }
            if (!ssPredictor.hasValidModelFile()) {
                if (GUIUtils.affirm("No model in directory\nFetch one from NMRFx.org?")) {
                    if (!fetchSSModel()) {
                        return;
                    }
                } else {
                    return;
                }
            }
            StringBuilder seqBuilder = new StringBuilder();
            for (Polymer polymer : molecule.getPolymers()) {
                if (polymer.isRNA()) {
                    for (Residue residue : polymer.getResidues()) {
                        seqBuilder.append(residue.getName());
                    }
                }
            }
            String sequence = seqBuilder.toString();
            try {
                double threshold = thresholdSlider.getValue();
                ssPredictor.predict(sequence, threshold, pseudoKnotCheckBox.isSelected());
                ssViewer.setSSPredictor(ssPredictor);
                ssPredictor.bipartiteMatch(threshold, 0.1, 10);
                updateSSChoiceBox();
                showSS(ssChoiceBox.getItems().getFirst());
            } catch (IllegalArgumentException | InvalidMoleculeException e) {
                ExceptionDialog exceptionDialog = new ExceptionDialog(e);
                exceptionDialog.showAndWait();
            }
        }
    }

    void updateThreshold() {
        if ((ssPredictor != null) && !thresholdSlider.isValueChanging()) {
            double threshold = thresholdSlider.getValue();
            ssPredictor.updateBasePairs(threshold, pseudoKnotCheckBox.isSelected());
            ssPredictor.bipartiteMatch(threshold, 0.1, 10);
            updateSSChoiceBox();
            try {
                showSS(ssChoiceBox.getItems().getFirst());
            } catch (IllegalArgumentException | InvalidMoleculeException e) {
                ExceptionDialog exceptionDialog = new ExceptionDialog(e);
                exceptionDialog.showAndWait();
            }

        }
    }

    void updateSSChoiceBox() {
        ssChoiceBox.getItems().clear();
        Set<String> commonEntries = new HashSet<>();
        if (ssPredictor != null) {
            int n = ssPredictor.getNExtents();
            for (int i = 0; i < n; i++) {
                SSPredictor.BasePairsMatching basePairsMatching = ssPredictor.getExtentBasePairs(i);
                String dotBracket = ssPredictor.getDotBracket(basePairsMatching.basePairsSet());
                int fileIndex = fileSecondaryStructures.indexOf(dotBracket);
                SSOrigin origin = fileIndex != -1 ? SSOrigin.BOTH : SSOrigin.PRED;
                SecondaryStructureEntry secondaryStructureEntry = new SecondaryStructureEntry(dotBracket, origin, i, fileIndex);
                ssChoiceBox.getItems().add(secondaryStructureEntry);
                if (fileIndex != -1) {
                    commonEntries.add(dotBracket);
                }
            }
        }
        int i = 0;
        for (String dotBracket : fileSecondaryStructures) {
            if (!commonEntries.contains(dotBracket)) {
                SecondaryStructureEntry secondaryStructureEntry = new SecondaryStructureEntry(dotBracket, SSOrigin.FILE, -1, i);
                ssChoiceBox.getItems().add(secondaryStructureEntry);
            }
            i++;
        }
        ssChoiceBox.setValue(ssChoiceBox.getItems().getFirst());
        ssChoiceBox.setDisable(false);
    }

    void showSelectedSS() {
        SecondaryStructureEntry secondaryStructureEntry = ssChoiceBox.getValue();
        if (secondaryStructureEntry != null) {
            try {
                showSS(secondaryStructureEntry);
                showRNAPeakScore(secondaryStructureEntry.dotBracket);
            } catch (InvalidMoleculeException ignored) {
            }
        }
    }

    void showRNAPeakScore(String dotBracket) {
        if (rnaStructureScores.containsKey(dotBracket)) {
            rnaSecStructureScoreLabel.setText(String.format("Peak Match Score: %.2f", rnaStructureScores.get(dotBracket)));
        }
    }

    void showSS(SecondaryStructureEntry secondaryStructureEntry) throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            final String dotBracket;
            if ((ssPredictor != null) && (secondaryStructureEntry.type == SSOrigin.PRED || secondaryStructureEntry.type == SSOrigin.BOTH)) {
                Set<SSPredictor.BasePairProbability> basePairsExt = ssPredictor.getExtentBasePairs(secondaryStructureEntry.pIindex).basePairsSet();
                dotBracket = ssPredictor.getDotBracket(basePairsExt);
            } else {
                dotBracket = secondaryStructureEntry.dotBracket;
            }
            molecule.setDotBracket(dotBracket);
            layoutSS();
        }
    }

    @FXML
    private void zoomIn() {
        ssViewer.zoom(1.05);
    }

    @FXML
    private void zoomOut() {
        ssViewer.zoom(0.95);

    }

    public void updatePeakListMenu() {
        peakListMenuButton.getItems().clear();

        for (String peakListName : ProjectBase.getActive().getPeakListNames()) {
            MenuItem menuItem = new MenuItem(peakListName);
            menuItem.setOnAction(e -> {
                PeakList peakList1 = PeakList.get(peakListName);
                if (peakList1.getNDim() == 2) {
                    setPeakList(peakListName);
                }
            });
            peakListMenuButton.getItems().add(menuItem);
        }
    }

    void setPeakList(String peakListName) {
        peakList = PeakList.get(peakListName);
        PeakList.registerFreezeListener(this);
        updatePeaks();
    }

    void updatePeaks() {
        Set<String> peakClasses = new HashSet<>();
        for (var menuItem : peakClassCheckItems) {
            if (menuItem.isSelected()) {
                peakClasses.add(menuItem.getText());
            }
        }
        double exponent = PeakGenerator.EXPONENT;

        if (peakList != null) {
            List<String> constraintPairs = new ArrayList<>();
            boolean onlyFrozen = frozenCheckBox.isSelected();
            if (peakList.valid()) {
                double max = Double.NEGATIVE_INFINITY;
                for (Peak peak : peakList.peaks()) {
                    if (peak.getIntensity() > max) {
                        max = peak.getIntensity();
                    }
                }
                for (Peak peak : peakList.peaks()) {
                    boolean frozen1 = peak.getPeakDim(0).isFrozen();
                    boolean frozen2 = peak.getPeakDim(1).isFrozen();
                    if (!onlyFrozen || (frozen1 && frozen2)) {
                        String name1 = peak.getPeakDim(0).getLabel();
                        String name2 = peak.getPeakDim(1).getLabel();
                        if (!name1.isEmpty() && !name2.isEmpty()) {
                            String intMode = getIntensityMode(peak, exponent);
                            if (peakClasses.contains(intMode)) {
                                constraintPairs.add(name1);
                                constraintPairs.add(name2);
                                constraintPairs.add(intMode);
                            }
                        }
                    }
                }
                String datasetName = peakList.getDatasetName();
                if ((datasetName != null) && !datasetName.isEmpty() && (Molecule.getActive() != null)) {
                    Dataset dataset = Dataset.getDataset(datasetName);
                    if (dataset != null) {
                        String labelScheme = dataset.getProperty("labelScheme");
                        RNALabels rnaLabels = new RNALabels();
                        rnaLabels.parseSelGroups(Molecule.getActive(), labelScheme);
                    }
                }
            }
            ssViewer.setConstraintPairs(constraintPairs);
            ssViewer.drawSS();
        }
    }

    private String getIntensityMode(Peak peak, double exponent) {
        double intensity = peak.getIntensity();
        double normIntensity = intensity / peakList.getScale();
        double distance = Math.exp(-1.0 / exponent * Math.log(normIntensity));
        String intMode;
        if (distance < 2.8) {
            intMode = "s";
        } else if (distance < 3.8) {
            intMode = "m";
        } else if (distance < 5.0) {
            intMode = "w";
        } else {
            intMode = "vw";
        }
        return intMode;
    }

    @Override
    public void freezeHappened(Peak peak, boolean state) {
        if (frozenCheckBox.isSelected() && peak.getPeakList() == peakList) {
            updatePeaks();
        }
    }

}