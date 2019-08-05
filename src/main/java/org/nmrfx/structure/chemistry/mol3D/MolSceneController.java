package org.nmrfx.structure.chemistry.mol3D;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.collections.MapChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.FreezeListener;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.gui.AtomController;
import org.nmrfx.processor.gui.molecule.MoleculeCanvas;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Bond;
import org.nmrfx.structure.chemistry.Compound;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.RNALabels;
import org.nmrfx.structure.chemistry.SSLayout;
import org.nmrfx.structure.chemistry.SSViewer;
import org.nmrfx.structure.chemistry.SpatialSet;

/**
 * FXML Controller class
 *
 * @author Bruce Johnson
 */
public class MolSceneController implements Initializable, MolSelectionListener, FreezeListener {

    private Stage stage;
    SSViewer ssViewer;
    MolViewer molViewer;

    @FXML
    TextField selectField;

    @FXML
    BorderPane ssBorderPane;
    @FXML
    BorderPane molBorderPane;
    @FXML
    BorderPane ligandBorderPane;
    @FXML
    ChoiceBox<Integer> nAtomsChoiceBox;
    @FXML
    TextField dotBracketField;
    @FXML
    Pane dotBracketPane;
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

    static Background errorBackground = new Background(new BackgroundFill(Color.ORANGE, CornerRadii.EMPTY, Insets.EMPTY));
    Background defaultBackground = new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY));
    StackPane stackPane = new StackPane();
    Pane twoDPane = new Pane();
    Pane ligandCanvasPane;
    PeakList peakList = null;
    int itemIndex = 0;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        ssViewer = new SSViewer();
        ssBorderPane.setCenter(ssViewer);
        molViewer = new MolViewer(this, twoDPane);
        SubScene subScene = molViewer.initScene(500, 500);
        stackPane.getChildren().addAll(molViewer, twoDPane);
        molBorderPane.setCenter(stackPane);
        ssViewer.drawNumbersProp.bind(numbersCheckBox.selectedProperty());
        ssViewer.showActiveProp.bind(activeCheckBox.selectedProperty());
        nAtomsChoiceBox.getItems().addAll(0, 1, 2, 3, 4, 5, 6, 7);
        nAtomsChoiceBox.setValue(0);
        ssViewer.nAtomsProp.bind(nAtomsChoiceBox.valueProperty());
        dotBracketField.setEditable(true);
        dotBracketField.textProperty().addListener(e -> {
            dotBracketFieldChanged();
        });
        constraintTypeChoiceBox.getItems().addAll("All", "Intraresidue", "Interresidue");
        constraintTypeChoiceBox.setValue("All");
        ssViewer.constraintTypeProp.bind(constraintTypeChoiceBox.valueProperty());

        molBorderPane.widthProperty().addListener(ss -> molViewer.layoutChildren());
        molBorderPane.heightProperty().addListener(ss -> molViewer.layoutChildren());
        molViewer.addSelectionListener(this);

        // kluge to prevent tabpane from getting focus.  This allows key presses to go through to molviewer
        // see JDK bug JDK-8092266
        molBorderPane.setOnMousePressed(MouseEvent::consume);
        ligandCanvasPane = new Pane();
        ligandCanvas = new MoleculeCanvas();
        ligandCanvasPane.getChildren().add(ligandCanvas);
        ligandBorderPane.setCenter(ligandCanvasPane);
        ligandCanvasPane.widthProperty().addListener(ss -> ligandCanvas.layoutChildren(ligandCanvasPane));
        ligandCanvasPane.heightProperty().addListener(ss -> ligandCanvas.layoutChildren(ligandCanvasPane));
        MapChangeListener<String, PeakList> mapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> {
            updatePeakListMenu();
        };

        PeakList.peakListTable.addListener(mapChangeListener);
        updatePeakListMenu();
        modeMenuButton.getItems().add(numbersCheckBox);
        modeMenuButton.getItems().add(activeCheckBox);
        modeMenuButton.getItems().add(frozenCheckBox);

        frozenCheckBox.selectedProperty().addListener(e -> updatePeaks());
        selectField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                try {
                    selectAction(selectField.getText());
                    selectField.clear();
                } catch (InvalidMoleculeException ex) {
                }
            }
        });

    }

    public Stage getStage() {
        return stage;
    }

    public static MolSceneController create() {
        FXMLLoader loader = new FXMLLoader(AtomController.class.getResource("/fxml/MolScene.fxml"));
        MolSceneController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<MolSceneController>getController();
            controller.stage = stage;
            stage.setTitle("Molecular Viewer");
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

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
    void drawMol(ActionEvent event) throws InvalidMoleculeException {
        molViewer.drawMol();
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
                        Logger.getLogger(MolSceneController.class.getName()).log(Level.SEVERE, null, ex);
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
                dotBracketPane.setBackground(errorBackground);
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
                    //dotBracketField.clear();
                }
            }
        } catch (InvalidMoleculeException ex) {
            Logger.getLogger(MolSceneController.class.getName()).log(Level.SEVERE, null, ex);
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
            molecule.selectAtoms(ligand.getName() + ":*.*'");
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
        molViewer.addLines(0, "lines " + getIndex());
    }

    public void drawCyls() {
        molViewer.addCyls(0, 0.1, 0.1, "lines " + getIndex());
    }

    public void drawSticks() {
        molViewer.addCyls(0, 0.3, 0.5, "sticks " + getIndex());
    }

    public void drawSpheres() {
        molViewer.addSpheres(0, 0.8, "spheres " + getIndex());
    }

    public void drawTubes() throws InvalidMoleculeException {
        molViewer.addTube(0, 0.7, "tubes " + getIndex());
    }

    public void drawBox() throws InvalidMoleculeException {
        System.out.println("add box");
        molViewer.deleteItems("delete", "box");
        molViewer.addBox(0, 0.3, "box " + getIndex());
    }

    public void removeAll() {
        molViewer.deleteItems("delete", "all");
        itemIndex = 0;
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
                if (!added.contains(fields[0])) {
                    removeItems.add(fields[0]);
                    added.add(fields[0]);
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
            String[] fields = nodeDescriptor.split(" ");
            if (fields.length > 0) {
                if (fields[0].equals("atom") && (fields.length > 1)) {
                    try {
                        molecule.selectAtoms(fields[1]);
                    } catch (InvalidMoleculeException ex) {
                        Logger.getLogger(MolSceneController.class.getName()).log(Level.SEVERE, null, ex);
                    }
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

        for (String peakListName : PeakList.peakListTable.keySet()) {
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
        if (peakList != null) {
            List<String> constraintPairs = new ArrayList<>();
            boolean onlyFrozen = frozenCheckBox.isSelected();
            if (peakList.valid()) {
                for (Peak peak : peakList.peaks()) {
                    boolean frozen1 = peak.getPeakDim(0).isFrozen();
                    boolean frozen2 = peak.getPeakDim(1).isFrozen();
                    if (!onlyFrozen || (frozen1 && frozen2)) {
                        String name1 = peak.getPeakDim(0).getLabel();
                        String name2 = peak.getPeakDim(1).getLabel();
                        if (!name1.equals("") && !name2.equals("")) {
                            constraintPairs.add(name1);
                            constraintPairs.add(name2);
                        }
                    }
                }
                String datasetName = peakList.getDatasetName();
                if ((datasetName != null) && !datasetName.equals("") && (Molecule.getActive() != null)) {
                    Dataset dataset = Dataset.getDataset(datasetName);
                    String labelScheme = dataset.getProperty("labelScheme");
                    RNALabels rnaLabels = new RNALabels();
                    rnaLabels.parseSelGroups(Molecule.getActive(), labelScheme);
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
}
