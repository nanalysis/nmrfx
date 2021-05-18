package org.nmrfx.analyst.gui;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.graphicsio.GraphicsContextProxy;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.graphicsio.PDFGraphicsContext;
import org.nmrfx.graphicsio.SVGGraphicsContext;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.utils.GUIUtils;

/**
 * FXML Controller class
 *
 * @author brucejohnson
 */
public class SeqDisplayController implements Initializable {

    private final static Map<String, Double> SCALES = new HashMap<>();
    Color[] colors = {Color.BLUE, Color.RED, Color.BLACK, Color.GREEN, Color.CYAN, Color.MAGENTA, Color.YELLOW};

    static {
        SCALES.put("N", 0.627);
        SCALES.put("C", 0.310);
        SCALES.put("CB", 0.219);
        SCALES.put("CA", 0.281);
        SCALES.put("H", 0.102);
        SCALES.put("HA", 0.0566);
        SCALES.put("HB", 0.0546);

    }

    static final String[] RNA_ATOMS = {"H5,H8", "H6,H2", "H1'", "H2'", "H3'", "H4'", "H5'",
        "C5,C8", "C6,C2", "C1'", "C2'", "C3'", "C4'", "C5'"
    };

    static final String[] PROTEIN_ATOMS = {"H", "N", "HA", "C", "CA", "CB"};
    Stage stage = null;
    @FXML
    BorderPane seqDisplayPane;
    @FXML
    BorderPane atomsPane;
    @FXML
    Slider nResiduesSlider;
    @FXML
    Label nResiduesLabel;
    @FXML
    Slider fontSizeSlider;
    @FXML
    Label fontSizeLabel;

    Pane canvasPane;
    Canvas seqCanvas;

    SimpleIntegerProperty nResProp = new SimpleIntegerProperty();
    SimpleIntegerProperty fontSizeProp = new SimpleIntegerProperty();
    double smallGap = 5.0;
    boolean drawVienna = true;
    boolean verticalResNums = false;

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        canvasPane = new Pane();
        seqCanvas = new Canvas();
        canvasPane.getChildren().add(seqCanvas);
        seqDisplayPane.setCenter(canvasPane);
        canvasPane.widthProperty().addListener(ss -> refresh());
        canvasPane.heightProperty().addListener(ss -> refresh());
        nResiduesSlider.setMin(20);
        nResiduesSlider.setMax(200);
        nResiduesSlider.valueProperty().bindBidirectional(nResProp);
        nResiduesSlider.setValue(100);
        nResProp.addListener(e -> {
            nResiduesLabel.setText(String.valueOf(nResProp.get()));
            refresh();
        });
        fontSizeSlider.setMin(5);
        fontSizeSlider.setMax(30);
        fontSizeSlider.valueProperty().bindBidirectional(fontSizeProp);
        fontSizeSlider.setValue(12);
        fontSizeProp.addListener(e -> {
            fontSizeLabel.setText(String.valueOf(fontSizeProp.get()));
            refresh();
        });

    }

    public static SeqDisplayController create() {
        FXMLLoader loader = new FXMLLoader(MinerController.class.getResource("/fxml/SeqDisplayScene.fxml"));
        SeqDisplayController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((BorderPane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<SeqDisplayController>getController();
            controller.stage = stage;
            stage.setTitle("Seq Display");
            stage.setScene(scene);
//            stage.setMinWidth(200);
//            stage.setMinHeight(250);
            stage.show();
            stage.toFront();

        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }
        return controller;
    }

    public Stage getStage() {
        return stage;
    }

    public List<String> getValidAtoms(Polymer polymer) {
        String[] testNames;
        if (polymer.isPeptide()) {
            testNames = PROTEIN_ATOMS;
        } else if (polymer.isRNA()) {
            testNames = RNA_ATOMS;
        } else {
            testNames = new String[0];
        }
        List<String> useNames = new ArrayList<>();
        for (String aName : testNames) {
            for (Residue residue : polymer.getResidues()) {
                String[] splitAtoms = aName.split(",");
                boolean gotAtom = false;
                for (String sName : splitAtoms) {
                    Atom atom = residue.getAtom(sName);
                    if (atom != null) {
                        if (atom.getPPM() != null) {
                            useNames.add(aName);
                            gotAtom = true;
                            break;
                        }
                    }
                }
                if (gotAtom) {
                    break;
                }
            }
        }
        return useNames;
    }

    void drawAtomLabels(GraphicsContextInterface gC, List<String> aNames, double x, double y, double height, boolean symbols) {
        gC.setTextAlign(TextAlignment.RIGHT);
        gC.setTextBaseline(VPos.CENTER);
        y = y + height / 2.0 + smallGap;
        int iAtom = 0;
        double symSize = 6.0;
        for (String aName : aNames) {
            if (symbols) {
                Color color = colors[iAtom % colors.length];
                gC.setFill(color);
                gC.setStroke(color);
                double x1 = x - symSize - 5;
                gC.strokeOval(x1, y - symSize / 2, symSize, symSize);
                gC.fillOval(x1, y - symSize / 2, symSize, symSize);
                gC.setFill(Color.BLACK);
                gC.fillText(aName, x1 - 5, y);
            } else {
                gC.setFill(Color.BLACK);
                gC.fillText(aName, x - 5, y);
            }

            y += height + smallGap;
            iAtom++;
        }
    }

    void drawAtomScores(GraphicsContextInterface gC, Residue residue, List<String> aNames, double x, double y, double height) {
        y = y + height / 2.0 + smallGap;
        for (String aName : aNames) {
            Atom atom = residue.getAtom(aName);
            if (atom != null) {
                Double ppm = atom.getPPM();
                Double rppm = atom.getRefPPM();
                if ((ppm != null) && (rppm != null)) {
                    String scaleName = aName;
                    if (aName.length() > 2) {
                        scaleName = aName.substring(0, 2);
                    }
                    double scale;
                    if (SCALES.containsKey(scaleName)) {
                        scale = SCALES.get(scaleName);
                    } else {
                        switch (atom.getElementNumber()) {
                            case 1:
                                scale = 0.05;
                                break;
                            case 6:
                                scale = 0.3;
                                break;
                            default:
                                scale = 0.6;
                                break;
                        }
                    }
                    scale *= 4.0;
                    double delta = (ppm - rppm) / scale;
                    if (delta > 1.0) {
                        delta = 1.0;
                    } else if (delta < -1.0) {
                        delta = -1.0;
                    }
                    double width = 6.0;
                    double halfWidth = width / 2.0;
                    double x1 = x - halfWidth;
                    double w = width;

                    double h = Math.abs(delta * height / 2.0);
                    double y1;
                    if (delta < 0.0) {
                        y1 = y;
                        gC.setFill(Color.RED);
                    } else {
                        y1 = y - h;
                        gC.setFill(Color.BLUE);
                    }
                    gC.fillRect(x1, y1, w, h);
                }
            }
            y += height + smallGap;
        }
        gC.setFill(Color.BLACK);
    }

    void drawRNAAtomScores(GraphicsContextInterface gC, Residue residue, List<String> aNames, double x, double y, double height) {
        height = (height + smallGap) * aNames.size();
        y = y + height / 2.0 + smallGap;
        int iAtom = 0;
        for (String aName : aNames) {
            String[] splitAtoms = aName.split(",");
            for (String sName : splitAtoms) {
                Atom atom = residue.getAtom(sName);
                if (atom != null) {
                    Double ppm = atom.getPPM();
                    Double rppm = atom.getRefPPM();
                    if ((ppm != null) && (rppm != null)) {
                        double scale = atom.getElementNumber() == 1 ? 0.5 : 2.0;
                        double delta = (ppm - rppm) / scale;
                        Color color;
                        if (delta > 1.0) {
                            delta = 1.0;
                            color = Color.RED;
                        } else if (delta < -1.0) {
                            delta = -1.0;
                            color = Color.RED;
                        } else {
                            color = Color.BLUE;
                        }
                        color = colors[iAtom % colors.length];

                        double width = 6.0;
                        double halfSize = width / 2.0;
                        double x1 = x - halfSize - halfSize / 2.0 + (iAtom % 2) * halfSize;
                        double w = width;

                        double h = Math.abs(delta * height / 2.0);
                        double y1;
                        if (delta < 0.0) {
                            y1 = y - h - halfSize;
                            gC.setFill(Color.RED);
                        } else {
                            y1 = y + h - halfSize;
                        }
                        gC.setStroke(Color.DARKGRAY);
                        gC.strokeLine(x, y - height / 2.0, x, y + height / 2.0);
                        gC.setFill(color);
                        gC.setStroke(color);
                        gC.strokeOval(x1, y1, w, w);
                        gC.fillOval(x1, y1, w, w);
                    }
                }
            }
            iAtom++;
            //y += height + smallGap;
        }
        gC.setFill(Color.BLACK);
        gC.setStroke(Color.BLACK);
    }

    void drawResNumLabel(GraphicsContextInterface gC, double x, double y, int resNum) {
        double fontHeight = gC.getFont().getSize();
        gC.setTextBaseline(VPos.BASELINE);
        gC.setTextAlign(TextAlignment.CENTER);
        String resNumStr = String.valueOf(resNum);
        if (verticalResNums) {
            int len = resNumStr.length();
            for (int iNum = 0; iNum < len; iNum++) {
                gC.fillText(resNumStr.substring(iNum, iNum + 1), x, y - fontHeight * (len - iNum) - 5);
            }
        } else {
            gC.fillText(resNumStr, x, y - fontHeight - 5);

        }
    }

    void drawSeqCharLabel(GraphicsContextInterface gC, double x, double y, Residue residue) {
        String text = String.valueOf(residue.getOneLetter());
        gC.setTextBaseline(VPos.BASELINE);
        gC.setTextAlign(TextAlignment.CENTER);
        gC.fillText(text, x, y);

    }

    void drawLabel(GraphicsContextInterface gC, double x, double y, String text) {
        gC.setTextBaseline(VPos.BASELINE);
        gC.setTextAlign(TextAlignment.CENTER);
        gC.fillText(text, x, y);

    }

    public void refresh() {
        var gC2D = seqCanvas.getGraphicsContext2D();
        GraphicsContextInterface gC = new GraphicsContextProxy(gC2D);
        drawCanvas(gC);
    }

    public void drawCanvas(GraphicsContextInterface gC) {

        double cWidth = canvasPane.getWidth();
        double cHeight = canvasPane.getHeight();
        seqCanvas.setWidth(cWidth);
        seqCanvas.setHeight(cHeight);
        gC.setFill(Color.WHITE);
        gC.fillRect(0, 0, cWidth, cHeight);
        gC.setFill(Color.BLACK);
        Molecule mol = (Molecule) MoleculeFactory.getActive();
        if (mol == null) {
            return;
        }
        gC.setFont(Font.font(fontSizeProp.get()));
        double fontHeight = gC.getFont().getSize();
        double fontWidth = GUIUtils.getTextWidth("M", gC.getFont());
        double heightMultiplier = 1.0;
        double width = fontWidth + 2;
        double atomBarHeight = fontHeight * heightMultiplier + 10;
        double xOrigin = 15 + 6 * fontWidth;
        double resNumHeight;
        if (verticalResNums) {
            resNumHeight = 4 * fontHeight + 5;
        } else {
            resNumHeight = fontHeight + 5;
        }
        double sectionGap = fontHeight * 1.0;
        double yOrigin = sectionGap + resNumHeight + fontHeight;
        double y = yOrigin;
        double curY = y;
        String dotBracket = mol.getDotBracket();
        int resIndex = 0;
        for (Polymer polymer : mol.getPolymers()) {
            Residue firstRes = polymer.getFirstResidue();
            int firstResNum = firstRes.getResNum();
            int iRes = (firstResNum % 10) - 1;
            boolean drawLabels = true;
            var aNames = getValidAtoms(polymer);
            curY = y;
            for (Residue residue : polymer.getResidues()) {
                double x = iRes * width + width / 2.0 + xOrigin;

                int resNum = residue.getResNum();

                if ((resNum % 10) == 0) {
                    drawResNumLabel(gC, x, y, resNum);
                }

                drawSeqCharLabel(gC, x, y, residue);
                if (polymer.isRNA() && drawVienna && (dotBracket != null)) {
                    y += fontHeight;
                    drawLabel(gC, x, y, dotBracket.substring(resIndex, resIndex + 1));
                    y += smallGap;
                }

                if (drawLabels) {
                    drawAtomLabels(gC, aNames, xOrigin, y, atomBarHeight, true);
                    drawLabels = false;
                }

                if (polymer.isPeptide()) {
                    drawAtomScores(gC, residue, aNames, x, y, atomBarHeight);
                    y += (atomBarHeight + smallGap) * aNames.size();
                } else {
                    drawRNAAtomScores(gC, residue, aNames, x, y, atomBarHeight);
                    y += (atomBarHeight + smallGap) * aNames.size();
                }

                iRes++;
                if ((iRes >= nResProp.get()) || (residue == polymer.getLastResidue())) {
                    drawLabels = true;
                    iRes = 0;
                    y = y + resNumHeight + fontHeight + sectionGap;
                    curY = y;
                } else {
                    y = curY;
                }
                resIndex++;
            }
        }

    }

    @FXML
    public void exportPDFAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to PDF");
        fileChooser.setInitialDirectory(FXMLController.getInitialDirectory());
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            exportPDF(selectedFile);
        }
    }

    public void exportPDF(File file) {
        exportPDF(file.toString());
    }

    public void exportPDF(String fileName) {
        if (fileName != null) {
            try {
                PDFGraphicsContext pdfGC = new PDFGraphicsContext();
                pdfGC.create(true, seqCanvas.getWidth(), seqCanvas.getHeight(), fileName);
                drawCanvas(pdfGC);
                pdfGC.saveFile();
            } catch (GraphicsIOException ex) {
                Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        stage.setResizable(true);
    }

    @FXML
    public void exportSVGAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to SVG");
        fileChooser.setInitialDirectory(FXMLController.getInitialDirectory());
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            exportSVG(selectedFile);
        }
    }

    public void exportSVG(File file) {
        exportSVG(file.toString());
    }

    public void exportSVG(String fileName) {
        if (fileName != null) {
            SVGGraphicsContext svgGC = new SVGGraphicsContext();
            svgGC.create(true, seqCanvas.getWidth(), seqCanvas.getHeight(), fileName);
            drawCanvas(svgGC);
            svgGC.saveFile();
        }
        stage.setResizable(true);
    }

}
