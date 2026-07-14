package org.nmrfx.analyst.gui.tools;

import javafx.application.Platform;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.chemistry.PPMv;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.structure.chemistry.predict.BMRBStats;
import org.nmrfx.utils.GUIUtils;

import java.io.*;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ResidueLayoutTool {
    Map<String, Map<String, AtomRecord>> residueAtomMap = new HashMap<>();
    Map<String, Map<String, BondRecord>> residueBondMap = new HashMap<>();

    Map<String, Color> colors = Map.of(
            "C", Color.BLACK,
            "H", Color.GREEN,
            "N", Color.BLUE,
            "O", Color.RED,
            "S", Color.ORANGE,
            "P", Color.CYAN);


    BorderPane borderPane = new BorderPane();
    Stage stage = null;
    Scene stageScene = new Scene(borderPane, 600, 550);
    ToggleGroup toggleGroup = new ToggleGroup();
    ToggleGroup labelGroup = new ToggleGroup();
    Pane pane;

    enum Modes {
        ATOM,
        BONDS
    }

    record AtomRecord(double x, double y, String anchor) {
    }

    record BondRecord(String atom1, String atom2, String stereo) {
    }


    public void showResidues() {
        if (stage == null) {
            try {
                loadData();
            } catch (IOException ioException) {
                ExceptionDialog exceptionDialog = new ExceptionDialog(ioException);
                exceptionDialog.showAndWait();
                return;
            }
            stage = new Stage();
            stage.setResizable(false);
            stage.setTitle("Residue Topology and Standard Shifts");
            GUIUtils.applyTheme(stageScene);
            stage.setScene(stageScene);
            init();
        }
        stage.show();
        stage.toFront();
    }

    private void init() {
        ToolBar toolBar = new ToolBar();
        RadioButton nameButton = new RadioButton("Name");
        RadioButton ppmButton = new RadioButton("PPM");
        nameButton.setToggleGroup(labelGroup);
        ppmButton.setToggleGroup(labelGroup);
        labelGroup.selectToggle(nameButton);
        toolBar.getItems().addAll(nameButton, ppmButton);
        borderPane.setTop(toolBar);
        VBox vBox = new VBox();
        pane = new Pane();
        residueAtomMap.keySet().stream()
                .sorted(Comparator.comparingInt(String::length)
                        .thenComparing(Comparator.naturalOrder()))
                .forEach(resName -> {
                    RadioButton radioButton = new RadioButton(resName);
                    vBox.getChildren().add(radioButton);
                    radioButton.setToggleGroup(toggleGroup);
                });
        toggleGroup.selectToggle((RadioButton) vBox.getChildren().getFirst());
        borderPane.setLeft(vBox);
        borderPane.setCenter(pane);
        pane.setStyle("-fx-background-color: white;");
        toggleGroup.selectedToggleProperty().addListener(e -> toggleChanged());
        labelGroup.selectedToggleProperty().addListener(e -> toggleChanged());
        Platform.runLater(this::toggleChanged);
    }

    private void loadData() throws IOException {
        if (!residueAtomMap.isEmpty()) {
            return;
        }

        Map<String, AtomRecord> atomMap = null;
        Map<String, BondRecord> bondMap = null;
        ClassLoader cl = ClassLoader.getSystemClassLoader();

        try (InputStream istream = cl.getResourceAsStream("residue_layout.txt")) {
            if (istream == null) {
                return;
            }
            BufferedReader bf = new BufferedReader(new InputStreamReader(istream));
            var lines = bf.lines().toList();
            Modes mode = null;
            String resName;
            for (String line : lines) {
                line = line.strip();
                String[] fields = line.split("\t");
                if (fields.length < 2) {
                    continue;
                }
                if (fields.length == 2) {
                    if (fields[1].equalsIgnoreCase("atoms")) {
                        mode = Modes.ATOM;
                    } else if (fields[1].equalsIgnoreCase("bonds")) {
                        mode = Modes.BONDS;
                    }
                    resName = fields[0];
                    if (mode == Modes.ATOM) {
                        atomMap = new HashMap<>();
                        residueAtomMap.put(resName, atomMap);
                    } else {
                        bondMap = new HashMap<>();
                        residueBondMap.put(resName, bondMap);
                    }
                } else if (mode == Modes.ATOM) {
                    String aName = fields[0];
                    double x = Double.parseDouble(fields[1]);
                    double y = Double.parseDouble(fields[2]);
                    String anchor = fields[3];
                    AtomRecord atomRecord = new AtomRecord(x, y, anchor);
                    atomMap.put(aName, atomRecord);

                } else if (mode == Modes.BONDS) {
                    String aName1 = fields[0];
                    String aName2 = fields[1];
                    String atomPair = aName1 + "_" + aName2;
                    String stereo = fields[2];
                    BondRecord bondRecord = new BondRecord(aName1, aName2, stereo);
                    bondMap.put(atomPair, bondRecord);

                }
            }

        }
    }

    public static ResidueLayoutTool getTool(PolyChart chart) {
        ResidueLayoutTool residueLayoutTool = (ResidueLayoutTool) chart.getPopoverTool(ResidueLayoutTool.class.getName());
        if (residueLayoutTool == null) {
            residueLayoutTool = new ResidueLayoutTool();
            chart.setPopoverTool(ResidueLayoutTool.class.getName(), residueLayoutTool);
        }
        return residueLayoutTool;
    }

    void toggleChanged() {
        pane.getChildren().clear();
        RadioButton radioButton = (RadioButton) toggleGroup.getSelectedToggle();
        String resName = radioButton.getText();
        Map<String, AtomRecord> atoms = residueAtomMap.get(resName);
        double scale = 45;
        double centerX = 140.0;
        double centerY = pane.getHeight() - 100;
        BMRBStats.loadAllIfEmpty();
        for (Map.Entry<String, AtomRecord> entry : atoms.entrySet()) {
            String aName = entry.getKey().toUpperCase();
            String ppmAtomName = aName.endsWith("*") ? aName.replace("*", "1") : aName;
            Optional<PPMv> ppmVOpt = BMRBStats.getValue(resName.toUpperCase(), ppmAtomName);

            String format = aName.charAt(0) == 'H' ? "%.2f" : "%.1f";
            boolean ppmMode = ((RadioButton) labelGroup.getSelectedToggle()).getText().equalsIgnoreCase("ppm");
            String label = ppmVOpt.isPresent() && ppmMode ?
                    String.format(format, ppmVOpt.get().getValue()) : aName;
            String ppmLabel = "";
            if (ppmVOpt.isPresent()) {
                ppmLabel = String.format(format + " ± " + format, ppmVOpt.get().getValue(), ppmVOpt.get().getError());
            }
            String toolTipLabel;
            if (ppmMode) {
                toolTipLabel = aName;
            } else {
                if (ppmVOpt.isPresent()) toolTipLabel = ppmLabel;
                else toolTipLabel = "";
            }

            Color color = colors.get(aName.toUpperCase().substring(0, 1));
            AtomRecord atomRecord = entry.getValue();
            double x = atomRecord.x * scale + centerX;
            double y = -atomRecord.y * scale + centerY;
            Text text = new Text(label);
            text.setTextAlignment(TextAlignment.CENTER);
            text.setTextOrigin(VPos.CENTER);
            text.setX(x - (text.getLayoutBounds().getWidth() / 2.0));
            text.setY(y);
            text.setFill(color);
            text.setTextAlignment(TextAlignment.CENTER);
            Tooltip tooltip = new Tooltip(toolTipLabel);
            tooltip.setShowDelay(Duration.millis(250));
            Tooltip.install(text, tooltip);

            pane.getChildren().add(text);
        }
        Map<String, BondRecord> bondRecordMap = residueBondMap.get(resName);

        double arrowScale = 3.0;
        for (Map.Entry<String, BondRecord> entry : bondRecordMap.entrySet()) {
            BondRecord bondRecord = entry.getValue();
            String aName1 = bondRecord.atom1;
            String aName2 = bondRecord.atom2;
            String stereo = bondRecord.stereo;
            AtomRecord atomRecord1 = atoms.get(aName1);
            AtomRecord atomRecord2 = atoms.get(aName2);
            double x1 = atomRecord1.x * scale + centerX;
            double y1 = -atomRecord1.y * scale + centerY;
            double x2 = atomRecord2.x * scale + centerX;
            double y2 = -atomRecord2.y * scale + centerY;
            double dx = x2 - x1;
            double dy = y2 - y1;
            x1 = x1 + dx / arrowScale;
            y1 = y1 + dy / arrowScale + 2.0;
            x2 = x2 - dx / arrowScale;
            y2 = y2 - dy / arrowScale + 2.0;

            Shape shape;
            Shape shape2 = null;
            if (stereo.equalsIgnoreCase("d")) {
                shape = createArrow(x1, y1, x2, y2, 5);
            } else if (stereo.equalsIgnoreCase("u")) {
                shape = createArrow(x2, y2, x1, y1, 5);
            } else if (stereo.equalsIgnoreCase("2")) {
                shape = createOffsetLine(x1, y1, x2, y2, 5);
                shape2 = createOffsetLine(x1, y1, x2, y2, -5);
            } else {
                shape = new Line(x1, y1, x2, y2);
            }
            shape.setFill(Color.BLACK);
            pane.getChildren().add(shape);
            if (shape2 != null) {
                shape2.setFill(Color.BLACK);
                pane.getChildren().add(shape2);
            }
        }
    }

    private Polygon createArrow(double startX, double startY, double endX, double endY, double arrowWidth) {
        double deltaX = endX - startX;
        double deltaY = endY - startY;
        double angle = Math.atan2(deltaY, deltaX);

        double x1 = startX + (arrowWidth / 2) * Math.sin(angle);
        double y1 = startY - (arrowWidth / 2) * Math.cos(angle);

        double x2 = startX - (arrowWidth / 2) * Math.sin(angle);
        double y2 = startY + (arrowWidth / 2) * Math.cos(angle);

        Polygon triangleArrow = new Polygon(
                endX, endY,
                x1, y1,
                x2, y2
        );

        triangleArrow.setStrokeWidth(1);

        return triangleArrow;
    }

    private Line createOffsetLine(double startX, double startY, double endX, double endY, double arrowWidth) {
        double deltaX = endX - startX;
        double deltaY = endY - startY;
        double angle = Math.atan2(deltaY, deltaX);

        double xs1 = startX + (arrowWidth / 2) * Math.sin(angle);
        double ys1 = startY - (arrowWidth / 2) * Math.cos(angle);

        double xe1 = endX + (arrowWidth / 2) * Math.sin(angle);
        double ye1 = endY - (arrowWidth / 2) * Math.cos(angle);

        Line line = new Line(xs1, ys1, xe1, ye1);

        line.setStrokeWidth(1);

        return line;
    }
}
