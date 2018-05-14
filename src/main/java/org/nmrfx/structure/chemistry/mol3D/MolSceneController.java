package org.nmrfx.structure.chemistry.mol3D;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
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
import org.nmrfx.processor.gui.AtomController;
import org.nmrfx.processor.gui.molecule.MoleculeCanvas;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Bond;
import org.nmrfx.structure.chemistry.Compound;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.SSLayout;
import org.nmrfx.structure.chemistry.SSViewer;

/**
 * FXML Controller class
 *
 * @author Bruce Johnson
 */
public class MolSceneController implements Initializable, MolSelectionListener {

    private Stage stage;
    SSViewer ssViewer;
    MolViewer molViewer;

    @FXML
    BorderPane ssBorderPane;
    @FXML
    BorderPane molBorderPane;
    @FXML
    BorderPane ligandBorderPane;
    @FXML
    CheckBox numbersCheckBox;
    @FXML
    CheckBox activeCheckBox;
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
    static Background errorBackground = new Background(new BackgroundFill(Color.ORANGE, CornerRadii.EMPTY, Insets.EMPTY));
    Background defaultBackground = new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY));
    StackPane stackPane = new StackPane();
    Pane twoDPane = new Pane();
    Pane ligandCanvasPane;

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
                    mol.setDotBracket(dotBracket);
                    layoutSS();
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
            mol.setDotBracket(newDotBracket.toString());
            layoutSS();
        }
    }

    public void hideAll() throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        Molecule.selectAtoms("*:*.*");
        Molecule.setAtomProperty(Atom.DISPLAY, false);
        Molecule.selectBonds("atoms");
        molecule.setBondProperty(Bond.DISPLAY, false);
    }

    public void selectBackbone() throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        hideAll();
        for (Polymer polymer : molecule.getPolymers()) {
            if (polymer.isRNA()) {
                Molecule.selectAtoms(polymer.getName() + ":*.P,O5',C5',C4',C3',O3'");
            } else {
                Molecule.selectAtoms(polymer.getName() + ":*.CA,C,N");
            }
            Molecule.setAtomProperty(Atom.DISPLAY, true);
            Molecule.selectBonds("atoms");
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
            Molecule.selectAtoms(ligand.getName() + ":*.*'");
            Molecule.setAtomProperty(Atom.DISPLAY, true);
            Molecule.selectBonds("atoms");
            molecule.setBondProperty(Bond.DISPLAY, true);
        }
    }

    public void selectHeavy() throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        hideAll();
        Molecule.selectAtoms("*:*.H*", false, true);
        Molecule.setAtomProperty(Atom.DISPLAY, true);
        Molecule.selectBonds("atoms");
        molecule.setBondProperty(Bond.DISPLAY, true);

    }

    public void selectAll() throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        hideAll();
        Molecule.selectAtoms("*:*.*");
        Molecule.setAtomProperty(Atom.DISPLAY, true);
        Molecule.selectBonds("atoms");
        molecule.setBondProperty(Bond.DISPLAY, true);
    }

    public void drawLines() {
        molViewer.addLines(0, "lines");
    }

    public void drawCyls() {
        molViewer.addCyls(0, 0.1, 0.1, "lines");
    }

    public void drawSticks() {
        molViewer.addCyls(0, 0.3, 0.5, "sticks");
    }

    public void drawSpheres() {
        molViewer.addSpheres(0, 0.8, "spheres");
    }

    public void drawTubes() throws InvalidMoleculeException {
        molViewer.addTube(0, 0.7, "tubes");
    }

    public void removeAll() {
        molViewer.deleteItems("delete", "all");
    }

    public void updateRemoveMenu(Collection<String> items) {
        removeMenuButton.getItems().clear();
        for (String item : items) {
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
        String[] fields = nodeDescriptor.split(" ");
        if (fields.length > 0) {
            if (fields[0].equals("atom") && (fields.length > 1)) {
                try {
                    Molecule.selectAtoms(fields[1]);
                } catch (InvalidMoleculeException ex) {
                    Logger.getLogger(MolSceneController.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else if (fields[0].equals("clear")) {
                Molecule.clearSelected();

            }
        }
    }

    @FXML
    void drawLigand() {
        ligandCanvas.setupMolecules();
        ligandCanvas.layoutChildren(ligandCanvasPane);
    }

}
