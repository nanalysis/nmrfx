package org.nmrfx.analyst.gui.molecule;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.QuadCurve;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.structure.chemistry.predict.RNAAttributes;
import org.nmrfx.structure.chemistry.predict.RNAStats;
import org.nmrfx.structure.rna.SSLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SSViewer extends Pane {

    private static final Logger log = LoggerFactory.getLogger(SSViewer.class);
    private static final int N_ATOMS = 7;

    record AtomCoord(double x, double y) {
    }

    Group drawingGroup;
    Group infoGroup;
    Pane pane;
    SSLayout ssLayout;

    ArrayList<Point2D> points = new ArrayList<>();
    int[] basePairs = null;
    int[] basePairAtoms = null;
    ArrayList<String> sequence = new ArrayList<>();
    ArrayList<Integer> chainList = new ArrayList<>();
    ArrayList<String> constraintPairs = new ArrayList<>();
    Map<String, AtomCoord> atomMap = new HashMap<>();
    AtomCoord[] deltaCoords = null;

    // if true, draw lines connecting bases in sequence
    boolean seqState = true;
    boolean basePairState = true;
    boolean constraintPairState = true;
    private final SimpleBooleanProperty drawNumbersProp = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty showActiveProp = new SimpleBooleanProperty(true);
    private final SimpleStringProperty constraintTypeProp = new SimpleStringProperty("All");
    private final List<String> displayAtomTypes = new ArrayList<>();
    private final SimpleBooleanProperty hydrogenPredictionProp = new SimpleBooleanProperty(true);

    double centerX;
    double centerY;
    double paneCenterX;
    double paneCenterY;
    double paneWidth;
    double paneHeight;
    double scale = 10.0;

    public SSViewer() {
        initScene();
    }

    @Override
    public void layoutChildren() {
        pane.setPrefWidth(this.getWidth());
        pane.setPrefHeight(this.getHeight());
        super.layoutChildren();
        drawSS();
    }

    public SimpleBooleanProperty getDrawNumbersProp() {
        return drawNumbersProp;
    }

    public SimpleBooleanProperty getShowActiveProp() {
        return showActiveProp;
    }

    public SimpleStringProperty getConstraintTypeProp() {
        return constraintTypeProp;
    }

    public SimpleBooleanProperty getHydrogenPredictionProp() {
        return hydrogenPredictionProp;
    }

    public void updateAtoms(List<String> atomNames) {
        displayAtomTypes.clear();
        displayAtomTypes.addAll(atomNames);
        layoutChildren();
    }

    public final void initScene() {
        pane = new Pane();
        drawingGroup = new Group();
        infoGroup = new Group();
        double boxDim = 600.0;
        pane.setPrefSize(boxDim, boxDim);
        pane.getChildren().add(drawingGroup);
        pane.getChildren().add(infoGroup);
        this.getChildren().add(pane);
        drawNumbersProp.addListener(e -> drawSS());
        showActiveProp.addListener(e -> drawSS());
        constraintTypeProp.addListener(e -> drawSS());
        drawSS();
    }

    public void drawSS() {
        drawingGroup.getChildren().clear();
        try {
            layoutStructure(drawingGroup);
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }
    }

    void updateScale() {
        paneWidth = pane.getWidth();
        paneHeight = pane.getHeight();
        paneCenterX = paneWidth / 2.0;
        paneCenterY = paneHeight / 2.0;
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Point2D point : points) {
            double x = point.getX();
            double y = point.getY();
            minX = Math.min(x, minX);
            minY = Math.min(y, minY);
            maxX = Math.max(x, maxX);
            maxY = Math.max(y, maxY);
        }
        centerX = (maxX + minX) / 2.0;
        centerY = (maxY + minY) / 2.0;
        double widthX = maxX - minX;
        double widthY = maxY - minY;
        double scaleX = paneWidth / widthX;
        double scaleY = paneHeight / widthY;
        scale = Math.min(scaleX, scaleY);
        scale *= (0.85 - N_ATOMS * 0.02);
    }

    Node drawLabelledCircle(double width, String text, int fontSize, Color color, double x, double y) {
        StackPane stack = new StackPane();
        Circle circle = new Circle(width / 2.0, color);
        Text textItem = new Text(text);
        textItem.setFill(Color.BLACK);
        textItem.setFont(Font.font(fontSize));
        stack.getChildren().addAll(circle, textItem);
        stack.setAlignment(Pos.CENTER);     // Right-justify nodes in stack
        stack.setTranslateX(x - width / 2 + 1);
        stack.setTranslateY(y - width / 2 + 1);
        return stack;

    }

    Node drawLabelledNode(int iRes, String resName, String text, double x, double y) {
        double width = scale * 0.5;
        if (width < 12) {
            width = 12.0;
        }

        int fontSize = (int) Math.round(width);
        Color color = switch (text) {
            case "G" -> Color.ORANGE;
            case "C" -> Color.LIGHTGREEN;
            case "A" -> Color.YELLOW;
            case "U", "T" -> Color.LIGHTBLUE;
            default -> Color.WHITE;
        };
        String label;
        if (drawNumbersProp.get()) {
            label = resName.substring(1);
            fontSize /= 2;
        } else {
            label = text;
        }

        atomMap.put(iRes + "", new AtomCoord(x, y));
        return drawLabelledCircle(width, label, fontSize, color, x, y);
    }

    Node drawAtom(String resNum, String text, double x, double y, boolean active) {
        double width = scale * 0.2;
        if (width < 4) {
            width = 4.0;
        }

        int fontSize = (int) Math.round(width);
        Color color = switch (text) {
            case "5''", "5'", "4'", "3'", "2'", "1'" -> Color.SALMON;
            case "8", "6", "5", "2" -> Color.RED;
            default -> Color.WHITE;
        };
        if (!active) {
            color = Color.LIGHTGRAY;
        }
        Node node = drawLabelledCircle(width, text, fontSize, color, x, y);
        node.setOnMousePressed(e -> showInfo(e, resNum, text));

        atomMap.put(resNum + "." + "H" + text, new AtomCoord(x, y));

        return node;
    }

    void hideInfo() {
        infoGroup.getChildren().clear();
    }

    void showInfo(MouseEvent e, String resNum, String aName) {
        String aType = hydrogenPredictionProp.get() ? ".H" : ".C";
        String atomSpec = resNum + aType + aName;
        Node node = (Node) e.getSource();
        double x = node.getBoundsInParent().getMinX();
        double y = node.getBoundsInParent().getMinY();
        Atom atom = MoleculeBase.getAtomByName(atomSpec);
        if (atom != null) {
            Double ppm = atom.getPPM();
            Double refPPM = atom.getRefPPM();
            String ppmStr = ppm == null ? "      " : String.format("%6.2f", ppm);
            String refppmStr = refPPM == null ? "      " : String.format("%6.2f", refPPM);
            String attributes = RNAAttributes.get(atom);
            RNAStats stats = RNAAttributes.getStats(atom);
            String statString = stats != null ? stats.toString() : "0";
            String result = atomSpec + " Meas: " + ppmStr + " Pred: " + refppmStr + "\n" + attributes + "\n" + statString;
            infoGroup.getChildren().clear();
            StackPane stack;
            Text textItem;
            double fontSize = 11;
            double height = 4 * fontSize;
            double width = 250;

            if (infoGroup.getChildren().isEmpty()) {
                textItem = new Text();
                Rectangle rect = new Rectangle(width, height, Color.WHITE);
                rect.setStroke(Color.BLACK);
                textItem.setFill(Color.BLACK);
                textItem.setFont(Font.font(fontSize));
                textItem.setMouseTransparent(true);
                stack = new StackPane();
                stack.getChildren().addAll(rect, textItem);
                stack.setAlignment(Pos.CENTER);     // Right-justify nodes in stack
                infoGroup.getChildren().add(stack);
                rect.setOnMousePressed(mE -> hideInfo());
            } else {
                stack = (StackPane) infoGroup.getChildren().get(0);
                textItem = (Text) stack.getChildren().get(1);
            }
            textItem.setText(result);
            double fx = x / paneWidth;
            double px = x - width * fx;
            stack.setTranslateX(px);
            double py;
            if (y < (paneHeight / 2)) {
                py = y + 20;
            } else {
                py = y - 10 - height;
            }
            stack.setTranslateY(py);
        }
    }

    Line getLine(int i, int j) {
        Point2D iPoint = points.get(i);
        Point2D jPoint = points.get(j);
        double iX = iPoint.getX();
        double iY = iPoint.getY();
        double jX = jPoint.getX();
        double jY = jPoint.getY();
        iX = (iX - centerX) * scale + paneCenterX;
        iY = (centerY - iY) * scale + paneCenterY;
        jX = (jX - centerX) * scale + paneCenterX;
        jY = (centerY - jY) * scale + paneCenterY;
        return new Line(iX, iY, jX, jY);
    }

    void addLines(Group group, int[] lineConnections) {
        if (lineConnections != null) {
            for (int i = 0; i < lineConnections.length; i += 2) {
                int source = lineConnections[i];
                int target = lineConnections[i + 1];
                Line line = getLine(source, target);
                line.setStrokeWidth(1.0);
                group.getChildren().add(line);
            }
        }
    }

    double toX(double x) {
        x = (x - centerX) * scale + paneCenterX;
        return x;
    }

    double toY(double y) {
        y = (centerY - y) * scale + paneCenterY;
        return y;
    }

    void fillOval() {
        int nRes = points.size();
        double sumX = 0.0;
        double sumY = 0.0;
        for (Point2D point1 : points) {
            sumX += point1.getX();
            sumY += point1.getY();
        }
        double x1 = sumX / nRes;
        double y1 = sumY / nRes;
        for (int j = 0; j < nRes; j++) {
            Point2D point1 = points.get(j);
            double x2 = point1.getX();
            double y2 = point1.getY();
            double deltaX = x2 - x1;
            double deltaY = y2 - y1;
            double len1 = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            double len2 = 1.0;
            deltaX = deltaX * len2 / len1;
            deltaY = deltaY * len2 / len1;
            deltaCoords[j] = new AtomCoord(deltaX, -deltaY);
        }
    }

    void fillEnds() {
        int nRes = points.size();

        int firstSet = 0;
        for (int i = 0; i < nRes - 1; i++) {
            if (ssLayout.ssClass[i] == 1) {
                firstSet = i;
                break;
            }
        }
        if (firstSet != 0) {
            Point2D point1 = points.get(firstSet);
            Point2D point4 = points.get(basePairAtoms[firstSet]);
            double x2 = point1.getX();
            double y2 = point1.getY();
            double x4 = point4.getX();
            double y4 = point4.getY();
            double deltaX = x2 - x4;
            double deltaY = y2 - y4;
            double len1 = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            if (len1 > 0.01) {
                double len2 = 1.0;
                deltaX = deltaX * len2 / len1;
                deltaY = deltaY * len2 / len1;
            }
            for (int j = firstSet - 1; j >= 0; j--) {
                deltaCoords[j] = new AtomCoord(deltaX, -deltaY);
            }
        }
        int lastSet = nRes - 1;
        for (int i = nRes - 1; i > 0; i--) {
            if (ssLayout.ssClass[i] == 1) {
                lastSet = i;
                break;
            }
        }

        if (lastSet != (nRes - 1)) {
            Point2D point1 = points.get(lastSet);
            Point2D point4 = points.get(basePairAtoms[lastSet]);
            double x2 = point1.getX();
            double y2 = point1.getY();
            double x4 = point4.getX();
            double y4 = point4.getY();
            double deltaX = x2 - x4;
            double deltaY = y2 - y4;
            double len1 = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            if (len1 > 0.01) {
                double len2 = 1.0;
                deltaX = deltaX * len2 / len1;
                deltaY = deltaY * len2 / len1;
            }

            for (int j = lastSet + 1; j < nRes; j++) {
                deltaCoords[j] = new AtomCoord(deltaX, -deltaY);
            }
        }
    }

    void getVectors() {
        if (points.isEmpty()) {
            return;
        }
        double lastX = 0.0;
        double lastY = 0.0;
        int n = points.size();
        deltaCoords = new AtomCoord[n];
        boolean gotBP = false;
        for (int iRes = 0; iRes < n; iRes++) {
            if (ssLayout.ssClass[iRes] == 0) {
                continue;
            }
            gotBP = true;
            Point2D point2 = points.get(iRes);
            double x2 = point2.getX();
            double y2 = point2.getY();
            double deltaX;
            double deltaY;
            boolean ok = false;
            int mode = 0;
            if (basePairAtoms[iRes] != -1) {
                Point2D point4 = points.get(basePairAtoms[iRes]);
                double x4 = point4.getX();
                double y4 = point4.getY();
                if ((iRes != 0) && (iRes != (n - 1))) {
                    Point2D point1 = points.get(iRes - 1);
                    double x1 = point1.getX();
                    double y1 = point1.getY();
                    Point2D point3 = points.get(iRes + 1);
                    double x3 = point3.getX();
                    double y3 = point3.getY();
                    double lmx = (x1 + x3) / 2;
                    double lmy = (y1 + y3) / 2;
                    deltaX = (lmx - x4);
                    deltaY = (lmy - y4);
                } else {
                    deltaX = x2 - x4;
                    deltaY = y2 - y4;
                }
            } else {
                double x1;
                double y1;
                double x3;
                double y3;
                double lmx;
                double lmy;
                if (iRes == 0) {
                    Point2D point3 = points.get(iRes + 1);
                    x3 = point3.getX();
                    y3 = point3.getY();
                    Point2D point4 = points.get(iRes + 2);
                    double x4 = point4.getX();
                    double y4 = point4.getY();
                    lmx = (x2 + x4) / 2;
                    lmy = (y2 + y4) / 2;
                    deltaX = (lmx - x3);
                    deltaY = (lmy - y3);
                    mode = 1;
                } else if (iRes == (n - 1)) {
                    Point2D point0 = points.get(iRes - 2);
                    double x0 = point0.getX();
                    double y0 = point0.getY();
                    Point2D point1 = points.get(iRes - 1);
                    x1 = point1.getX();
                    y1 = point1.getY();
                    lmx = (x2 + x0) / 2;
                    lmy = (y2 + y0) / 2;
                    deltaX = (x1 - lmx);
                    deltaY = (y1 - lmy);
                    mode = 2;
                } else {
                    Point2D point1 = points.get(iRes - 1);
                    x1 = point1.getX();
                    y1 = point1.getY();
                    Point2D point3 = points.get(iRes + 1);
                    x3 = point3.getX();
                    y3 = point3.getY();
                    lmx = (x1 + x3) / 2;
                    lmy = (y1 + y3) / 2;
                    deltaX = (x2 - lmx);
                    deltaY = (y2 - lmy);
                    mode = 3;
                }
            }
            double len1 = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            if (len1 > 0.01) {
                ok = true;
                double len2 = 1.0;
                deltaX = deltaX * len2 / len1;
                deltaY = deltaY * len2 / len1;
            }
            if (ok) {
                if ((mode != 0) && ((lastX != 0.0) || (lastY != 0.0))) {
                    double d1 = (deltaX - lastX) * (deltaX - lastX) + (deltaY - lastY) * (deltaY - lastY);
                    double d2 = (deltaX + lastX) * (deltaX + lastX) + (deltaY + lastY) * (deltaY + lastY);
                    if (d2 < d1) {
                        deltaX = -deltaX;
                        deltaY = -deltaY;
                    }
                }
                deltaCoords[iRes] = new AtomCoord(deltaX, -deltaY);
                lastX = deltaX;
                lastY = deltaY;
            } else {
                deltaCoords[iRes] = new AtomCoord(lastX, -lastY);
            }
        }
        if (gotBP) {
            fillEnds();
        } else {
            fillOval();
        }
    }

    void drawVectors(Group group) {
        int n = points.size();
        for (int iRes = 0; iRes < n; iRes++) {
            String resName = sequence.get(iRes);
            String resNum = resName.substring(1);
            char resChar = resName.charAt(0);
            Point2D point = points.get(iRes);
            double x1 = point.getX();
            double y1 = point.getY();
            AtomCoord aCoord = deltaCoords[iRes];
            int startAtom = -2;
            int iDrawn = 0;
            for (int j = startAtom; j < N_ATOMS; j++) {
                if ((j == -2) && (resChar != 'G')) {
                    continue;
                }
                double x;
                double y;
                if ((j < 0) && !displayAtomTypes.contains("Exchangeable")) {
                    continue;
                }
                if ((j >= 0) && (j < 2) && !displayAtomTypes.contains("Base")) {
                    continue;
                }
                if (j > 1) {
                    String text = "H" + (j - 1) + "'";
                    if (!displayAtomTypes.contains(text)) {
                        continue;
                    }
                }
                if (j >= 0) {
                    x = x1 + aCoord.x / 5 * (iDrawn + 1.5);
                    y = y1 - aCoord.y / 5 * (iDrawn + 1.5);
                    iDrawn++;
                } else {
                    x = x1 + (aCoord.y / 5.0 * (j + 1.5)) + aCoord.x / 5 * (-1 - 0.5);
                    y = y1 + (aCoord.x / 5.0 * (j + 1.5)) - aCoord.y / 5 * (-1 - 0.5);
                }
                String text = "";
                if (j > 1) {
                    text = (j - 1) + "'";
                } else if (j == 0) {
                    if (resChar == 'A') {
                        text = "2";
                    } else if (resChar == 'C') {
                        text = "5";
                    } else if (resChar == 'U') {
                        text = "5";
                    }
                } else if (j == 1) {
                    if (resChar == 'A') {
                        text = "8";
                    } else if (resChar == 'G') {
                        text = "8";
                    } else {
                        text = "6";
                    }
                } else if (j == -1) {
                    if (resChar == 'U') {
                        text = "3";
                    } else if (resChar == 'C') {
                        text = "41";
                    } else if (resChar == 'A') {
                        text = "62";
                    } else if (resChar == 'G') {
                        text = "21";
                    }
                } else {
                    text = "1";
                }
                if (!text.equals("")) {
                    boolean active = true;
                    if (showActiveProp.get()) {
                        active = false;
                        String atomSpec = resName + ".H" + text;
                        Atom atom = MoleculeBase.getAtomByName(atomSpec);
                        if (atom != null) {
                            Atom parent = atom.getParent();
                            if (parent != null) {
                                active = parent.isActive();
                            }
                        }
                    }

                    Node node = drawAtom(resNum, text, toX(x), toY(y), active);
                    group.getChildren().add(node);
                }
            }
        }

    }

    void markBasePairAtoms(int[] basePairs) {
        if (basePairs != null) {
            basePairAtoms = new int[sequence.size()];
            Arrays.fill(basePairAtoms, -1);
            for (int i = 0; i < basePairs.length; i += 2) {
                basePairAtoms[basePairs[i]] = basePairs[i + 1];
                basePairAtoms[basePairs[i + 1]] = basePairs[i];
            }
        }
    }

    void layoutStructure(Group group) {
        if (points.isEmpty()) {
            return;
        }
        if (sequence.isEmpty()) {
            return;
        }
        updateScale();
        atomMap.clear();
        if (basePairState) {
            addLines(group, basePairs);
        }
        markBasePairAtoms(basePairs);

        if (seqState) {
            double lastX = 0.0;
            double lastY = 0.0;
            int i = 0;
            for (Point2D point : points) {
                double x = point.getX();
                double y = point.getY();
                x = (x - centerX) * scale + paneCenterX;
                y = (centerY - y) * scale + paneCenterY;
                if ((i != 0) && chainList.get(i - 1).equals(chainList.get(i))) {
                    Line line = new Line(lastX, lastY, x, y);
                    line.setStrokeWidth(3.0);
                    group.getChildren().add(line);
                }
                lastX = x;
                lastY = y;
                i++;
            }
        }
        int iRes = 0;
        for (Point2D point : points) {
            String resName = sequence.get(iRes);
            char resChar = resName.charAt(0);
            double x = point.getX();
            double y = point.getY();
            x = toX(x);
            y = toY(y);
            Node node = drawLabelledNode(iRes, resName, String.valueOf(resChar), x, y);
            group.getChildren().add(node);
            iRes++;
        }
        getVectors();
        drawVectors(group);
        if (constraintPairState) {
            drawConstraints(group);
        }

    }

    void drawConstraints(Group group) {
        for (int i = 0; i < constraintPairs.size(); i += 3) {
            connect(group, constraintPairs.get(i),
                    constraintPairs.get(i + 1),
                    constraintPairs.get(i + 2));
        }
    }

    String getResNum(String atom) {
        return atom.substring(0, atom.indexOf("."));
    }

    String getAtomName(String atom) {
        return atom.substring(atom.indexOf(".") + 1);
    }

    int getAtomIndex(String aName) {
        char c1 = aName.charAt(1);
        if ((aName.length() == 3) && (aName.charAt(2) == '\'')) {
            return c1 - '1' + 2;
        } else if (aName.length() == 3) {
            return -1;
        } else if ((c1 == '2') || (c1 == '5')) {
            return 0;
        } else if ((c1 == '1') || (c1 == '3')) {
            return -1;
        } else {
            return 1;
        }
    }

    void connect(Group group, String a1, String a2, String intMode) {
        double width = scale * 0.2;
        if (width < 4) {
            width = 4.0;
        }
        String r1 = getResNum(a1);
        String r2 = getResNum(a2);
        int r1Num = Integer.parseInt(r1);
        int r2Num = Integer.parseInt(r2);

        boolean allOK = constraintTypeProp.getValue().equals("All");
        boolean intraOK = constraintTypeProp.getValue().equals("Intraresidue") && (r1Num == r2Num);
        boolean interOK = constraintTypeProp.getValue().equals("Interresidue") && (r1Num != r2Num);

        if (!allOK && !intraOK && !interOK) {
            return;
        }
        String aName1 = getAtomName(a1);
        String aName2 = getAtomName(a2);
        if ((getAtomIndex(aName1) >= N_ATOMS) || (getAtomIndex(aName2) >= N_ATOMS)) {
            return;
        }
        if ((r1Num > r2Num) || (a1.compareTo(a2) > 0)) {
            String hold = a2;
            a2 = a1;
            a1 = hold;
            r1 = getResNum(a1);
            r2 = getResNum(a2);
        }
        width *= 0.4;
        AtomCoord c1 = atomMap.get(a1);
        AtomCoord c2 = atomMap.get(a2);
        if ((c1 != null) && (c2 != null)) {
            double div = 5.0;
            if (r1.equals(r2)) {
                div = 1.0;
            }
            double dX = c2.x - c1.x;
            double dY = c2.y - c1.y;
            double cmx = (c1.x + c2.x) / 2 - dY / div;
            double cmy = (c1.y + c2.y) / 2 + dX / div;
            double ddX = c1.x - cmx;
            double ddY = c1.y - cmy;
            double len = Math.sqrt(ddX * ddX + ddY * ddY);
            double x1 = c1.x - ddX * width / len;
            double y1 = c1.y - ddY * width / len;

            ddX = c2.x - cmx;
            ddY = c2.y - cmy;
            len = Math.sqrt(ddX * ddX + ddY * ddY);
            double x2 = c2.x - ddX * width / len;
            double y2 = c2.y - ddY * width / len;

            QuadCurve curve = new QuadCurve(x1, y1, cmx, cmy, x2, y2);

            curve.setFill(null);
            Color color = switch (intMode) {
                case "s" -> Color.RED;
                case "m" -> Color.ORANGE;
                case "w" -> Color.BLUE;
                default -> Color.DARKGRAY;
            };
            curve.setStroke(color);
            group.getChildren().add(curve);
        }
    }

    public void loadCoordinates(SSLayout ssLayout) {
        List<List<String>> sequences = ssLayout.getSequences();
        this.sequence.clear();
        this.chainList.clear();
        this.ssLayout = ssLayout;
        int k = 0;
        for (List<String> seq : sequences) {
            this.sequence.addAll(seq);
            for (int j = 0; j < seq.size(); j++) {
                chainList.add(k);
            }
            k++;
        }
        points.clear();
        double[] coords = ssLayout.getValues();
        for (int i = 0; i < coords.length; i += 2) {
            Point2D point = new Point2D(coords[i], coords[i + 1]);
            points.add(point);
        }
        setBasePairs(ssLayout.getBasePairs());
    }

    void setBasePairs(int[] basePairs) {
        List<Integer> bpList = new ArrayList<>();
        for (int i = 0; i < basePairs.length; i++) {
            int target = basePairs[i];
            if ((target >= 0) && (i < target)) {
                bpList.add(i);
                bpList.add(target);
            }
        }
        this.basePairs = new int[bpList.size()];
        for (int i = 0; i < bpList.size(); i++) {
            this.basePairs[i] = bpList.get(i);
        }
    }

    public void setConstraintPairs(List<String> constraintPairs) {
        this.constraintPairs.clear();
        this.constraintPairs.addAll(constraintPairs);
        constraintPairState = true;
    }
}
