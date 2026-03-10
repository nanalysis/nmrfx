package org.nmrfx.analyst.gui.molecule3D;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.SubScene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.controlsfx.control.StatusBar;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.analyst.gui.molecule.MoleculeCanvas;
import org.nmrfx.analyst.gui.plugin.PluginLoader;
import org.nmrfx.chemistry.*;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.fxutil.Fxml;
import org.nmrfx.fxutil.StageBasedController;
import org.nmrfx.plugin.api.EntryPoint;
import org.nmrfx.structure.chemistry.MissingCoordinatesException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.OpenChemLibConverter;
import org.nmrfx.structure.chemistry.energy.AngleTreeGenerator;
import org.nmrfx.structure.chemistry.energy.GradientRefinement;
import org.nmrfx.structure.chemistry.energy.RotationalDynamics;
import org.nmrfx.utilities.ProgressUpdater;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;

import static org.nmrfx.analyst.gui.molecule3D.StructureCalculator.StructureMode.*;

public class MolSceneController implements Initializable, StageBasedController, MolSelectionListener, ProgressUpdater {
    private static final Logger log = LoggerFactory.getLogger(MolSceneController.class);

    private Stage stage;
    MolViewer molViewer;
    @FXML
    public HBox mol3dHBox;

    @FXML
    TextField selectField;

    @FXML
    BorderPane molBorderPane;
    @FXML
    BorderPane ligandBorderPane;

    @FXML
    MenuButton removeMenuButton;
    @FXML
    MoleculeCanvas ligandCanvas;


    @FXML
    private StatusBar statusBar;
    @FXML
    private ToolBar lowerToolBar;
    private final Circle statusCircle = new Circle(10.0, Color.GREEN);
    Throwable processingThrowable;

    StackPane stackPane = new StackPane();
    Pane twoDPane = new Pane();
    Pane ligandCanvasPane;
    int itemIndex = 0;

    SimpleIntegerProperty activeStructureProp = new SimpleIntegerProperty(-1);
    StructureCalculator structureCalculator = new StructureCalculator(this);

    List<MolViewer.RenderType> currentDrawingModes = new ArrayList<>();


    @Override
    public void initialize(URL url, ResourceBundle rb) {
        molViewer = new MolViewer(this, twoDPane);
        SubScene subScene = molViewer.initScene(500, 500);
        stackPane.getChildren().addAll(molViewer, twoDPane);
        molBorderPane.setCenter(stackPane);

        molBorderPane.widthProperty().addListener(ss -> molViewer.layoutChildren());
        molBorderPane.heightProperty().addListener(ss -> molViewer.layoutChildren());
        molViewer.addSelectionListener(this);
        addStructureSelectionTools();


        // kluge to prevent tabpane from getting focus.  This allows key presses to go through to molviewer
        // see JDK bug JDK-8092266
        molBorderPane.setOnMousePressed(MouseEvent::consume);
        ligandCanvasPane = new Pane();
        ligandCanvas = new MoleculeCanvas();
        ligandCanvasPane.getChildren().add(ligandCanvas);
        ligandBorderPane.setCenter(ligandCanvasPane);
        ligandCanvasPane.widthProperty().addListener(ss -> ligandCanvas.layoutChildren(ligandCanvasPane));
        ligandCanvasPane.heightProperty().addListener(ss -> ligandCanvas.layoutChildren(ligandCanvasPane));
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
        activeStructureProp.set(getActiveStructures().getFirst());
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
        List<MolViewer.RenderType> modes = new ArrayList<>(currentDrawingModes);
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
            }
        }
    }

    @FXML
    void drawMol(ActionEvent event) throws InvalidMoleculeException, MissingCoordinatesException {
        molViewer.drawMol();
    }


    public void hideAll() throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        molecule.selectAtoms("*:*.*");
        molecule.setAtomProperty(Atom.DISPLAY, false);
        molecule.selectBonds("atoms");
        molecule.setBondProperty(Bond.DISPLAY, false);
    }

    public void selectResidues() throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        List<SpatialSet> selected = new ArrayList<>(molecule.globalSelected);
        hideAll();
        molecule.globalSelected.clear();
        molecule.globalSelected.addAll(selected);
        molecule.selectResidues();
        molecule.setAtomProperty(Atom.DISPLAY, true);
        molecule.selectBonds("atoms");
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
            if (!field.isEmpty()) {
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
        return structures.isEmpty() ? 0 : structures.getFirst();
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

    public void drawOrientationSpheresX() {
        molViewer.addOrientationSphere(getFirstStructure(), 122, 3.0, 0, "osphereX");
    }

    public void drawOrientationSpheresY() {
        molViewer.addOrientationSphere(getFirstStructure(), 122, 3.0, 1, "osphereY");
    }

    public void drawOrientationSpheresZ() {
        molViewer.addOrientationSphere(getFirstStructure(), 122, 3.0, 2, "osphereZ");
    }

    public void drawOrientationCyl() {
        molViewer.addOrientationCyls(getFirstStructure(), 122, 3.0, 2, "ocyls");
    }

    public void drawBox() {
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
     */
    public void drawSVDAxes() {
        molViewer.deleteItems("delete", "svdaxes");
        molViewer.addAxes(getFirstStructure(), 0.3, "svdaxes " + getIndex(), "svd");
    }

    /**
     * Draws the rotated axes from an RDC calculation.
     *
     */
    public void drawRDCAxes() {
        molViewer.deleteItems("delete", "rdcaxes");
        molViewer.addAxes(getFirstStructure(), 0.3, "rdcaxes " + getIndex(), "rdc");
    }

    public void rotateMoleculeRDC() {
        molViewer.resetTransform();
        molViewer.rotateSVDRDC("rdc");
    }

    public void rotateMoleculeSVD() {
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
        removeItems.addAll(items);

        for (String item : removeItems) {
            MenuItem menuItem = new MenuItem(item);
            menuItem.setOnAction(e -> molViewer.deleteItems("delete", item));
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
            Atom startAtom = molecule.globalSelected.getFirst().getAtom();
            AngleTreeGenerator angleGen = new AngleTreeGenerator();
            List<List<Atom>> aTree = angleGen.genTree(molecule, startAtom, null);
        }
    }

    @FXML
    private void to3DAction() {
        to3D();
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

    @FXML
    private void minimizeAction() {
        minimizeCompound();
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

    void calcStructure() {
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


}
