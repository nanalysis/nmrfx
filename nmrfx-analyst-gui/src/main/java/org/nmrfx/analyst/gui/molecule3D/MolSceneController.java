package org.nmrfx.analyst.gui.molecule3D;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.SubScene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.StatusBar;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.molecule.MoleculeCanvas;
import org.nmrfx.analyst.gui.molecule.SSViewer;
import org.nmrfx.analyst.gui.plugin.PluginLoader;
import org.nmrfx.analyst.models.ModelFetcher;
import org.nmrfx.analyst.peaks.PeakGenerator;
import org.nmrfx.chemistry.*;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.events.FreezeListener;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.PreferencesController;
import org.nmrfx.processor.gui.utils.AtomUpdater;
import org.nmrfx.processor.tools.RNAMatcher;
import org.nmrfx.project.ProjectBase;
import org.nmrfx.structure.chemistry.MissingCoordinatesException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.OpenChemLibConverter;
import org.nmrfx.structure.chemistry.energy.AngleTreeGenerator;
import org.nmrfx.structure.chemistry.energy.GradientRefinement;
import org.nmrfx.structure.chemistry.energy.RotationalDynamics;
import org.nmrfx.structure.rna.RNAAnalysis;
import org.nmrfx.structure.rna.RNALabels;
import org.nmrfx.structure.rna.SSLayout;
import org.nmrfx.structure.rna.SSPredictor;
import org.nmrfx.utilities.ProgressUpdater;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.nmrfx.analyst.gui.molecule3D.StructureCalculator.StructureMode.*;

public class MolSceneController implements Initializable, StageBasedController, MolSelectionListener, FreezeListener, ProgressUpdater, MoleculeListener {
    private static final Logger log = LoggerFactory.getLogger(MolSceneController.class);
    private static final Background ERROR_BACKGROUND = new Background(new BackgroundFill(Color.ORANGE, CornerRadii.EMPTY, Insets.EMPTY));

    static Random random = new Random();
    private Stage stage;
    SSViewer ssViewer;
    MolViewer molViewer;
    @FXML
    public HBox mol3dHBox;

    @FXML
    TextField selectField;

    @FXML
    BorderPane ssBorderPane;
    @FXML
    CheckBox ssDisplayCheckBox;
    @FXML
    CheckBox mapDisplayCheckBox;
    @FXML
    BorderPane molBorderPane;
    @FXML
    BorderPane ligandBorderPane;
    @FXML
    MenuButton atomMenu;
    @FXML
    TextField dotBracketField;
    @FXML
    Pane dotBracketPane;
    @FXML
    ChoiceBox<SecondaryStructureEntry> ssChoiceBox;
    @FXML
    MenuButton removeMenuButton;
    @FXML
    MoleculeCanvas ligandCanvas;
    @FXML
    MenuButton peakListMenuButton;
    @FXML
    MenuButton modeMenuButton;
    @FXML
    ChoiceBox<String> constraintTypeChoiceBox;
    @FXML
    CheckMenuItem frozenCheckBox = new CheckMenuItem("Frozen");
    CheckMenuItem activeCheckBox = new CheckMenuItem("Active");
    CheckMenuItem numbersCheckBox = new CheckMenuItem("Numbers");
    CheckMenuItem probabilitiesCheckBox = new CheckMenuItem("Probabilities");
    ToggleGroup predictionTypeGroup = new ToggleGroup();

    @FXML
    private StatusBar statusBar;
    @FXML
    private ToolBar lowerToolBar;
    private Circle statusCircle = new Circle(10.0, Color.GREEN);
    Throwable processingThrowable;

    List<CheckMenuItem> atomCheckItems = new ArrayList<>();
    List<CheckMenuItem> peakClassCheckItems = new ArrayList<>();

    Background defaultBackground = new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY));
    StackPane stackPane = new StackPane();
    Pane twoDPane = new Pane();
    Pane ligandCanvasPane;
    PeakList peakList = null;
    int itemIndex = 0;
    public Label rnaSecStructureScoreLabel;

    SimpleIntegerProperty activeStructureProp = new SimpleIntegerProperty(-1);
    private StructureCalculator structureCalculator = new StructureCalculator(this);
    SSPredictor ssPredictor = null;

    List<String> fileSecondaryStructures = new ArrayList<>();

    List<MolViewer.RenderType> currentDrawingModes = new ArrayList<>();

    Map<String, Double> rnaStructureScores = new HashMap<>();

    enum SSOrigin {
        PRED,
        FILE,
        BOTH
    }

    record SecondaryStructureEntry(String dotBracket, SSOrigin type, int pIindex, int fIndex) {
        public String toString() {
            return switch (type) {
                case PRED -> type + ":" + pIindex;
                case FILE -> type + ":" + fIndex;
                case BOTH -> SSOrigin.PRED + ":" + pIindex + " " + SSOrigin.FILE + ":" + fIndex;
            };
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        ssViewer = new SSViewer();
        ssBorderPane.setCenter(ssViewer);
        molViewer = new MolViewer(this, twoDPane);
        SubScene subScene = molViewer.initScene(500, 500);
        stackPane.getChildren().addAll(molViewer, twoDPane);
        molBorderPane.setCenter(stackPane);
        ssViewer.getDrawNumbersProp().bind(numbersCheckBox.selectedProperty());
        ssViewer.getDrawProbabilitiesProp().bind(probabilitiesCheckBox.selectedProperty());
        ssViewer.getShowActiveProp().bind(activeCheckBox.selectedProperty());
        dotBracketField.setEditable(true);
        dotBracketField.textProperty().addListener(e -> dotBracketFieldChanged());
        constraintTypeChoiceBox.getItems().addAll("All", "Intraresidue", "Interresidue");
        constraintTypeChoiceBox.setValue("All");
        ssViewer.getConstraintTypeProp().bind(constraintTypeChoiceBox.valueProperty());

        molBorderPane.widthProperty().addListener(ss -> molViewer.layoutChildren());
        molBorderPane.heightProperty().addListener(ss -> molViewer.layoutChildren());
        molViewer.addSelectionListener(this);
        addStructureSelectionTools();
        ssViewer.getDrawMapProp().bindBidirectional(mapDisplayCheckBox.selectedProperty());
        ssViewer.getDrawSSProp().bindBidirectional(ssDisplayCheckBox.selectedProperty());
        mapDisplayCheckBox.setSelected(false);
        ssDisplayCheckBox.setSelected(true);
        ssDisplayCheckBox.setOnAction(e -> ssViewer.resizeWindow());
        mapDisplayCheckBox.setOnAction(e -> ssViewer.resizeWindow());


        // kluge to prevent tabpane from getting focus.  This allows key presses to go through to molviewer
        // see JDK bug JDK-8092266
        molBorderPane.setOnMousePressed(MouseEvent::consume);
        ligandCanvasPane = new Pane();
        ligandCanvas = new MoleculeCanvas();
        ligandCanvasPane.getChildren().add(ligandCanvas);
        ligandBorderPane.setCenter(ligandCanvasPane);
        ligandCanvasPane.widthProperty().addListener(ss -> ligandCanvas.layoutChildren(ligandCanvasPane));
        ligandCanvasPane.heightProperty().addListener(ss -> ligandCanvas.layoutChildren(ligandCanvasPane));
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
        selectField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                try {
                    selectAction(selectField.getText());
                    selectField.clear();
                } catch (InvalidMoleculeException ex) {
                    log.warn(ex.getMessage(), ex);
                }
            }
        });
        statusBar.setProgress(0.0);

        statusBar.getLeftItems().add(statusCircle);
        statusCircle.setOnMousePressed((Event d) -> {
            if (processingThrowable != null) {
                ExceptionDialog dialog = new ExceptionDialog(processingThrowable);
                dialog.showAndWait();
            }
        });
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
        ssChoiceBox.setOnAction(e -> {
            showSelectedSS();
        });

        try {
            molViewer.drawMol();
        } catch (Exception ex) {
            log.warn(ex.getMessage(), ex);
        }
        PluginLoader.getInstance().registerPluginsOnEntryPoint(EntryPoint.MENU_MOLECULE_VIEWER, this);

    }

    public MolViewer getMolViewer() {
        return molViewer;
    }

    private void selectedResidue(MouseEvent event) {

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

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    public static MolSceneController create() {
        MolSceneController controller = Fxml.load(MolSceneController.class, "MolScene.fxml")
                .withNewStage("Molecular Viewer")
                .getController();
        controller.stage.show();
        return controller;
    }

    void addStructureSelectionTools() {
        ArrayList<Button> dataButtons = new ArrayList<>();
        Button allButton = new Button("All");
        allButton.setOnAction(this::allStructures);
        allButton.getStyleClass().add("toolButton");
        dataButtons.add(allButton);
        Button bButton;
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_BACKWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(this::firstStructure);
        dataButtons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BACKWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(this::previousStructure);
        dataButtons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FORWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(this::nextStructure);
        dataButtons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_FORWARD, "", AnalystApp.ICON_SIZE_STR, AnalystApp.ICON_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(this::lastStructure);
        dataButtons.add(bButton);

        TextField textField = GUIUtils.getIntegerTextField(activeStructureProp);
        textField.setPrefWidth(40);
        lowerToolBar.getItems().addAll(dataButtons);
        lowerToolBar.getItems().add(textField);
    }

    void allStructures(ActionEvent event) {
        activeStructureProp.set(-1);
        refresh();
    }

    void firstStructure(ActionEvent event) {
        activeStructureProp.set(getActiveStructures().get(0));
        refresh();
    }

    void previousStructure(ActionEvent event) {
        List<Integer> activeStructures = getActiveStructures();
        int index = Math.max(0, activeStructures.indexOf(activeStructureProp.get()) - 1);

        activeStructureProp.set(activeStructures.get(index));
        refresh();
    }

    void nextStructure(ActionEvent event) {
        List<Integer> activeStructures = getActiveStructures();
        int n = activeStructures.size();
        int index = Math.min(n - 1, activeStructures.indexOf(activeStructureProp.get()) + 1);
        if (index < 0) {
            index = 0;
        }
        activeStructureProp.set(activeStructures.get(index));
        refresh();
    }

    void lastStructure(ActionEvent event) {
        List<Integer> activeStructures = getActiveStructures();
        int n = activeStructures.size();
        activeStructureProp.set(activeStructures.get(n - 1));
        refresh();
    }

    void refresh() {
        molViewer.clearAll();
        itemIndex = 0;
        List<MolViewer.RenderType> modes = new ArrayList<>();
        modes.addAll(currentDrawingModes);
        for (MolViewer.RenderType mode : modes) {
            switch (mode) {
                case LINES -> drawLines();
                case SPHERES -> drawSpheres();
                case TUBE -> {
                    try {
                        drawTubes();
                    } catch (InvalidMoleculeException e) {
                        throw new RuntimeException(e);
                    }
                }
                case STICKS -> drawSticks();
            }
        }
    }


    @FXML
    void getDotBracket() {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No molecule present", ButtonType.CLOSE);
            alert.showAndWait();
        } else {
            String dotBracket = molecule.getDotBracket();
            TextInputDialog textDialog = new TextInputDialog(dotBracket);
            Optional<String> result = textDialog.showAndWait();
            if (result.isPresent()) {
                dotBracket = result.get().trim();
                molecule.setDotBracket(dotBracket);
            } else {
                return;
            }
        }
    }

    @FXML
    void drawMol(ActionEvent event) throws InvalidMoleculeException, MissingCoordinatesException {
        molViewer.drawMol();
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

            if (molecule.getDotBracket().equals("")) {
                initWithAllDots();
            }
            String dotBracket = molecule.getDotBracket();
            if (dotBracket.length() == 0) {
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

            if (molecule.getDotBracket().equals("")) {
                initWithAllDots();
            }
            String dotBracket = molecule.getDotBracket();
            if (dotBracket.length() == 0) {
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

    void initWithAllDots() throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            List<List<String>> seqs = SSLayout.setupSequence(molecule);
            int nChars = 0;
            for (List<String> seq : seqs) {
                nChars += seq.size();
            }
            StringBuilder sBuilder = new StringBuilder();
            for (int i = 0; i < nChars; i++) {
                sBuilder.append('.');
            }
            molecule.setDotBracket(sBuilder.toString());
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
            if (dotBracket.length() > 0) {
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

    public void hideAll() throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        molecule.selectAtoms("*:*.*");
        molecule.setAtomProperty(Atom.DISPLAY, false);
        int nBonds = molecule.selectBonds("atoms");
        molecule.setBondProperty(Bond.DISPLAY, false);
    }

    public void selectResidues() throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        List<SpatialSet> selected = new ArrayList<>();
        selected.addAll(molecule.globalSelected);
        int nPrevious = selected.size();
        hideAll();
        molecule.globalSelected.clear();
        molecule.globalSelected.addAll(selected);
        int nAtoms = molecule.selectResidues();
        molecule.setAtomProperty(Atom.DISPLAY, true);
        int nBonds = molecule.selectBonds("atoms");
        molecule.setBondProperty(Bond.DISPLAY, true);

    }

    public void selectAction(String selection) throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        hideAll();
        String[] fields = selection.split("\\s+");
        for (String field : fields) {
            if (field.length() > 0) {
                molecule.selectAtoms(field);
                molecule.setAtomProperty(Atom.DISPLAY, true);
                molecule.selectBonds("atoms");
                molecule.setBondProperty(Bond.DISPLAY, true);
            }
        }

    }

    public void selectBackbone() throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        hideAll();
        for (Polymer polymer : molecule.getPolymers()) {
            if (polymer.isRNA()) {
                molecule.selectAtoms(polymer.getName() + ":*.P,O5',C5',C4',C3',O3'");
            } else {
                molecule.selectAtoms(polymer.getName() + ":*.CA,C,N");
            }
            molecule.setAtomProperty(Atom.DISPLAY, true);
            molecule.selectBonds("atoms");
            molecule.setBondProperty(Bond.DISPLAY, true);
        }
    }

    public void selectLigand() throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        hideAll();
        for (Compound ligand : molecule.getLigands()) {
            molecule.selectAtoms(ligand.getName() + ":*.*");
            molecule.setAtomProperty(Atom.DISPLAY, true);
            molecule.selectBonds("atoms");
            molecule.setBondProperty(Bond.DISPLAY, true);
        }
    }

    public void selectHeavy() throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        hideAll();
        molecule.selectAtoms("*:*.H*", false, true);
        molecule.setAtomProperty(Atom.DISPLAY, true);
        molecule.selectBonds("atoms");
        molecule.setBondProperty(Bond.DISPLAY, true);

    }

    public void selectAll() throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        hideAll();
        molecule.selectAtoms("*:*.*");
        molecule.setAtomProperty(Atom.DISPLAY, true);
        molecule.selectBonds("atoms");
        molecule.setBondProperty(Bond.DISPLAY, true);
    }

    public int getIndex() {
        itemIndex++;
        return itemIndex;
    }

    public void drawLines() {
        molViewer.addLines(getStructures(), "lines", getIndex());
    }

    public void drawCyls() {
        molViewer.addCyls(getStructures(), 0.1, 0.1, "lines", getIndex());
    }

    public void drawSticks() {
        molViewer.addCyls(getStructures(), 0.3, 0.5, "sticks", getIndex());
    }

    public void drawSpheres() {
        molViewer.addSpheres(getStructures(), 0.8, "spheres", getIndex());
    }

    public List<Integer> getActiveStructures() {
        return molViewer.getCurrentMolecule().getActiveStructureList();
    }

    public List<Integer> getStructures() {
        List<Integer> structures = getActiveStructures();
        if (activeStructureProp.get() == -1) {
            return structures;
        } else {
            return List.of(activeStructureProp.get());
        }
    }

    public int getFirstStructure() {
        List<Integer> structures = getStructures();
        return structures.isEmpty() ? 0 : structures.get(0);
    }

    public void drawCartoon() throws InvalidMoleculeException {
        if (!molViewer.getCurrentMolecule().getPolymers().isEmpty()) {
            molViewer.addTube(getStructures(), 1.0, "tube", getIndex());
            molViewer.addNucleicAcidBases(getStructures(), 0.3, "bases", getIndex());
        }
        selectLigand();
        drawSticks();
    }

    public void drawTubes() throws InvalidMoleculeException {
        molViewer.addTube(getStructures(), 1.0, "tube", getIndex());
        molViewer.addNucleicAcidBases(getStructures(), 0.3, "bases", getIndex());
    }

    public void drawOrientationSpheresX() throws InvalidMoleculeException {
        molViewer.addOrientationSphere(getFirstStructure(), 122, 3.0, 0, "osphereX");
    }

    public void drawOrientationSpheresY() throws InvalidMoleculeException {
        molViewer.addOrientationSphere(getFirstStructure(), 122, 3.0, 1, "osphereY");
    }

    public void drawOrientationSpheresZ() throws InvalidMoleculeException {
        molViewer.addOrientationSphere(getFirstStructure(), 122, 3.0, 2, "osphereZ");
    }

    public void drawOrientationCyl() throws InvalidMoleculeException {
        molViewer.addOrientationCyls(getFirstStructure(), 122, 3.0, 2, "ocyls");
    }

    public void drawBox() throws InvalidMoleculeException {
        molViewer.deleteItems("delete", "box");
        molViewer.addBox(getFirstStructure(), 0.3, "box " + getIndex());
    }

    public void drawConstraints() {
        molViewer.deleteItems("delete", "constraints");
        molViewer.addConstraintLines(getFirstStructure(), "constraints " + getIndex());
    }

    public void drawTree() {
        molViewer.deleteItems("delete", "tree");
        molViewer.drawAtomTree();
    }

    /**
     * Draws the original axes.
     *
     * @throws InvalidMoleculeException
     */
    public void drawAxes() throws InvalidMoleculeException {
        molViewer.deleteItems("delete", "axes");
        molViewer.addAxes(getFirstStructure(), 0.3, "axes " + getIndex(), "original");
    }

    /**
     * Draws the rotated axes from an SVD calculation.
     *
     * @throws InvalidMoleculeException
     */
    public void drawSVDAxes() throws InvalidMoleculeException {
        molViewer.deleteItems("delete", "svdaxes");
        molViewer.addAxes(getFirstStructure(), 0.3, "svdaxes " + getIndex(), "svd");
    }

    /**
     * Draws the rotated axes from an RDC calculation.
     *
     * @throws InvalidMoleculeException
     */
    public void drawRDCAxes() throws InvalidMoleculeException {
        molViewer.deleteItems("delete", "rdcaxes");
        molViewer.addAxes(getFirstStructure(), 0.3, "rdcaxes " + getIndex(), "rdc");
    }

    public void rotateMoleculeRDC() throws InvalidMoleculeException {
        molViewer.resetTransform();
        molViewer.rotateSVDRDC("rdc");
    }

    public void rotateMoleculeSVD() throws InvalidMoleculeException {
        molViewer.resetTransform();
        molViewer.rotateSVDRDC("svd");
    }

    public void removeAll() {
        molViewer.deleteItems("delete", "all");
        itemIndex = 0;
    }

    public void clearModes() {
        currentDrawingModes.clear();
    }

    public void updateRemoveMenu(Collection<String> items) {
        removeMenuButton.getItems().clear();
        // items can be like "spheres 3", so we want to add an entry to get
        // all spheres and one to get ones just with tag 3
        Set<String> added = new HashSet<>();
        List<String> removeItems = new ArrayList<>();
        for (String item : items) {
            String[] fields = item.split(" ");
            if (fields.length > 1) {
                MolViewer.RenderType renderType;
                try {
                    renderType = MolViewer.RenderType.valueOf(fields[0].toUpperCase());
                } catch (IllegalArgumentException illegalArgumentException) {
                    renderType = null;
                }
                if (!added.contains(fields[0])) {
                    removeItems.add(fields[0]);
                    added.add(fields[0]);
                }
                if (renderType != null) {
                    if (!currentDrawingModes.contains(renderType)) {
                        currentDrawingModes.add(renderType);
                    }
                }
            }
        }
        for (String item : items) {
            removeItems.add(item);
        }

        for (String item : removeItems) {
            MenuItem menuItem = new MenuItem(item);
            menuItem.setOnAction(e -> {
                molViewer.deleteItems("delete", item);
            });
            removeMenuButton.getItems().add(menuItem);
        }
    }

    public void centerOnSelection() {
        molViewer.centerOnSelection();
    }

    public void resetTransform() {
        molViewer.resetTransform();
    }

    @Override
    public void processSelection(String nodeDescriptor, MouseEvent event) {
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            boolean append = event.isShiftDown();
            String[] fields = nodeDescriptor.split(" ");
            if (fields.length > 0) {
                if (fields[0].equals("atom") && (fields.length > 1)) {
                    try {
                        molecule.selectAtoms(fields[1], append, false);
                    } catch (InvalidMoleculeException ex) {
                        log.error(ex.getMessage(), ex);
                    }
                } else if (fields[0].equals("bond") && (fields.length > 2)) {
                    molecule.selectBonds(fields[1], fields[2], append);
                } else if (fields[0].equals("clear")) {
                    molecule.clearSelected();
                }
            }
        }
    }

    @FXML
    void drawLigand() {
        ligandCanvas.setupMolecules();
        ligandCanvas.layoutChildren(ligandCanvasPane);
    }

    public void updatePeakListMenu() {
        peakListMenuButton.getItems().clear();

        for (String peakListName : ProjectBase.getActive().getPeakListNames()) {
            MenuItem menuItem = new MenuItem(peakListName);
            menuItem.setOnAction(e -> {
                PeakList peakList = PeakList.get(peakListName);
                if (peakList.getNDim() == 2) {
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
                        if (!name1.equals("") && !name2.equals("")) {
                            double intensity = peak.getIntensity();
                            double normIntensity = intensity / peakList.getScale();
                            double distance = Math.exp(-1.0/exponent*Math.log(normIntensity));
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
                            if (peakClasses.contains(intMode)) {
                                constraintPairs.add(name1);
                                constraintPairs.add(name2);
                                constraintPairs.add(intMode);
                            }
                        }
                    }
                }
                String datasetName = peakList.getDatasetName();
                if ((datasetName != null) && !datasetName.equals("") && (Molecule.getActive() != null)) {
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

    @Override
    public void freezeHappened(Peak peak, boolean state) {
        if (frozenCheckBox.isSelected() && peak.getPeakList() == peakList) {
            updatePeaks();
        }
    }

    @FXML
    private void initStructureAction() {
        structureCalculator.setMode(INIT);
        calcStructure();
    }

    @FXML
    private void refineCFFStructureAction() {
        structureCalculator.setMode(CFF);
        calcStructure();
    }

    @FXML
    private void calcStructureAction() {
        structureCalculator.setMode(ANNEAL);
        calcStructure();
    }

    @FXML
    private void refineStructureAction() {
        structureCalculator.setMode(REFINE);
        calcStructure();
    }

    @FXML
    private void ssTo3D() {
        structureCalculator.setMode(NUCLEIC_ACID);
        calcStructure();
    }

    @FXML
    private void to3DAction() {
        to3D();
    }
    @FXML
    private void minimizeAction() {
        minimizeCompound();
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
                ssPredictor.predict(sequence);
                ssViewer.setSSPredictor(ssPredictor);
                ssPredictor.bipartiteMatch(0.7, 0.05, 20);
                updateSSChoiceBox();
                showSS(ssChoiceBox.getItems().get(0));

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
        ssChoiceBox.setValue(ssChoiceBox.getItems().get(0));
        ssChoiceBox.setDisable(false);
    }

    void showSelectedSS() {
        SecondaryStructureEntry secondaryStructureEntry = ssChoiceBox.getValue();
        if (secondaryStructureEntry != null) {
            try {
                showSS(secondaryStructureEntry);
                showRNAPeakScore(secondaryStructureEntry.dotBracket);
            } catch (InvalidMoleculeException e) {
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
            String dotBracket = "";
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

    @FXML
    private void activateBondAction() {
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            for (Bond bond : molecule.getBondList()) {
                bond.unsetProperty(Bond.DEACTIVATE);
            }
        }
    }

    @FXML
    private void deactivateBondAction() {
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            List<Bond> bonds = molecule.selectedBonds();
            for (var bond : bonds) {
                bond.setProperty(Bond.DEACTIVATE);
            }
        }
    }

    public void addStrongDistanceConstraint() {
        addDistanceConstraint(3.0);
    }

    public void addMediumDistanceConstraint() {
        addDistanceConstraint(4.0);
    }

    public void addWeakDistanceConstraint() {
        addDistanceConstraint(5.0);
    }

    public void addDistanceConstraint(double upper) {
        Molecule molecule = Molecule.getActive();
        if ((molecule != null) && (molecule.globalSelected.size() == 2)) {
            var molConstraints = molecule.getMolecularConstraints();
            var disCon = molConstraints.getNoeSet("noe_restraint_list", true);
            Atom atom1 = molecule.globalSelected.get(0).getAtom();
            Atom atom2 = molecule.globalSelected.get(1).getAtom();
            disCon.addDistanceConstraint(atom1.getFullName(), atom2.getFullName(), 1.8, upper, false);
        }
        drawConstraints();
    }

    @FXML
    private void genPRF() {
        genAngleTree();
    }

    private void genAngleTree() {
        Molecule molecule = Molecule.getActive();
        if (molecule.globalSelected.size() == 1) {
            Atom startAtom = molecule.globalSelected.get(0).getAtom();
            AngleTreeGenerator angleGen = new AngleTreeGenerator();
            List<List<Atom>> aTree = angleGen.genTree(molecule, startAtom, null);
        }
    }

    private void to3D() {
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            OpenChemLibConverter.to3D(molecule);
            removeAll();
            try {
                selectAll();
                drawSticks();
            } catch (InvalidMoleculeException imE) {

            }
        }
    }

    private void minimizeCompound() {
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            OpenChemLibConverter.minimize(molecule);
            removeAll();
            try {
                selectAll();
                drawSticks();
            } catch (InvalidMoleculeException imE) {
            }
        }
    }

    private void calcStructure() {
        RotationalDynamics.setUpdater(this);
        GradientRefinement.setUpdater(this);
        setProcessingOn();
        statusBar.setProgress(0.0);
        structureCalculator.restart();
    }

    public void setProcessingStatus(String s, boolean ok) {
        setProcessingStatus(s, ok, null);
    }

    public void setProcessingStatus(String s, boolean ok, Throwable throwable) {
        if (s == null) {
            statusBar.setText("");
        } else {
            statusBar.setText(s);
        }
        if (ok) {
            statusCircle.setFill(Color.GREEN);
            processingThrowable = null;
        } else {
            statusCircle.setFill(Color.RED);
            processingThrowable = throwable;
        }
        statusBar.setProgress(0.0);
    }

    public void clearProcessingTextLabel() {
        statusBar.setText("");
        statusCircle.setFill(Color.GREEN);
    }

    public void moleculeChanged(MoleculeEvent e) {
        Fx.runOnFxThread(ssViewer::drawSS);
    }

    @Override
    public void updateProgress(double f) {
    }

    @Override
    public void updateStatus(String s) {
        Fx.runOnFxThread(() -> {
            setProcessingStatus(s, true);
            updateView();
        });
    }

    void updateView() {
        removeAll();
        try {
            drawCartoon();
            molViewer.centerOnSelection();
        } catch (InvalidMoleculeException ex) {
            log.error(ex.getMessage(), ex);
        }

    }

    void setProcessingOff() {

    }

    void setProcessingOn() {

    }

    public void scoreSecStructures(ActionEvent event) {
        RNAMatcher rnaMatcher = new RNAMatcher();
        Molecule molecule = Molecule.getActive();
        String currentDotBracket = molecule.getDotBracket();
        rnaStructureScores.clear();
        ssChoiceBox.getItems().forEach(ss -> {
            molecule.setDotBracket(ss.dotBracket);
            System.out.println(ss.dotBracket);
            rnaMatcher.predict();
            rnaMatcher.genPeaks();
            double score = rnaMatcher.score();
            rnaStructureScores.put(ss.dotBracket, score);
        });
        molecule.setDotBracket(currentDotBracket);
        showRNAPeakScore(currentDotBracket);
    }

    public void selectSecStructure() {
        Molecule molecule = Molecule.getActive();
        molecule.setDotBracket(ssChoiceBox.getValue().dotBracket);
        RNAMatcher rnaMatcher = new RNAMatcher();
        rnaMatcher.predict();
        rnaMatcher.genPeaks();
    }

}
