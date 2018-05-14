package org.nmrfx.structure.chemistry;

import javafx.geometry.Point2D;
import javafx.scene.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.QuadCurve;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import org.nmrfx.structure.chemistry.predict.RNAAttributes;
import org.nmrfx.structure.chemistry.predict.RNAStats;

public class SSViewer extends Pane {

    class AtomCoord {

        final double x;
        final double y;

        AtomCoord(double x, double y) {
            this.x = x;
            this.y = y;
        }

    }
//    Group root;
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
    public SimpleBooleanProperty drawNumbersProp = new SimpleBooleanProperty(false);
    public SimpleBooleanProperty showActiveProp = new SimpleBooleanProperty(true);
    public SimpleIntegerProperty nAtomsProp = new SimpleIntegerProperty(7);

    double centerX;
    double centerY;
    double paneCenterX;
    double paneCenterY;
    double paneWidth;
    double paneHeight;
    double scale = 10.0;

    public SSViewer() {
        initScene();
//        this.getChildren().add(pane);
    }

    @Override
    public void layoutChildren() {
        pane.setPrefWidth(this.getWidth());
        pane.setPrefHeight(this.getHeight());
        super.layoutChildren();
        drawSS();
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
        nAtomsProp.addListener(e -> drawSS());
        drawSS();
    }

    public void drawSS() {
        //System.out.println("refresh " + scene.getWidth() + " " + scene.getHeight());
        drawingGroup.getChildren().clear();
        try {
            layoutStructure(drawingGroup);
        } catch (Exception e) {
            e.printStackTrace();
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
            minX = x < minX ? x : minX;
            minY = y < minY ? y : minY;
            maxX = x > maxX ? x : maxX;
            maxY = y > maxY ? y : maxY;
        }
        centerX = (maxX + minX) / 2.0;
        centerY = (maxY + minY) / 2.0;
        double widthX = maxX - minX;
        double widthY = maxY - minY;
        double scaleX = paneWidth / widthX;
        double scaleY = paneHeight / widthY;
        scale = scaleX < scaleY ? scaleX : scaleY;
        scale *= (0.85 - nAtomsProp.get() * 0.02);
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
        StackPane stack = new StackPane();
        double width = scale * 0.5;
        if (width < 12) {
            width = 12.0;
        }

        int fontSize = (int) Math.round(width);
        //Rectangle helpIcon = new Rectangle(width,width);
        Color color;
        switch (text) {
            case "G":
                color = Color.ORANGE;
                break;
            case "C":
                color = Color.LIGHTGREEN;
                break;
            case "A":
                color = Color.YELLOW;
                break;
            case "U":
                color = Color.LIGHTBLUE;
                break;
            case "T":
                color = Color.LIGHTBLUE;
                break;
            default:
                color = Color.WHITE;

        }
        String label;
        if (drawNumbersProp.get()) {
            label = resName.substring(1);
            fontSize /= 2;
        } else {
            label = text;
        }

        atomMap.put(iRes + "", new AtomCoord(x, y));
        Node node = drawLabelledCircle(width, label, fontSize, color, x, y);
        return node;
//       Circle helpIcon = new Circle(width / 2.0, color);
//        //helpIcon.setFill(Color.RED);
//        Text textItem;
//        if (drawNumbers) {
//            textItem = new Text(resName.substring(1));
//            textItem.setFont(Font.font(fontSize / 2));
//        } else {
//            textItem = new Text(text);
//            textItem.setFont(Font.font(fontSize));
//        }
//
//        stack.getChildren().addAll(helpIcon, textItem);
//        stack.setAlignment(Pos.CENTER);     // Right-justify nodes in stack
//        stack.setTranslateX(x - width / 2 + 1);
//        stack.setTranslateY(y - width / 2 + 1);
//        System.out.println("label " + text + " " + fontSize);
//        atomMap.put(iRes + "", new AtomCoord(x, y));
//        return stack;
    }

    Node drawAtom(String resNum, String text, double x, double y, boolean active) {
//        StackPane stack = new StackPane();
        double width = scale * 0.2;
        if (width < 4) {
            width = 4.0;
        }

        int fontSize = (int) Math.round(width);
        //Rectangle helpIcon = new Rectangle(width,width);
        Color color;
        switch (text) {
            case "5''":
                color = Color.SALMON;
                break;
            case "5'":
                color = Color.SALMON;
                break;
            case "4'":
                color = Color.SALMON;
                break;
            case "3'":
                color = Color.SALMON;
                break;
            case "2'":
                color = Color.SALMON;
                break;
            case "1'":
                color = Color.SALMON;
                break;
            case "8":
                color = Color.RED;
                break;
            case "6":
                color = Color.RED;
                break;
            case "5":
                color = Color.RED;
                break;
            case "2":
                color = Color.RED;
                break;
            default:
                color = Color.WHITE;

        }
        if (!active) {
            color = Color.LIGHTGRAY;
        }
        Node node = drawLabelledCircle(width, text, fontSize, color, x, y);
        node.setOnMousePressed(e -> {
            showInfo(e, resNum, text);
        });

//        Circle helpIcon = new Circle(width / 2.0, color);
//        //helpIcon.setFill(Color.RED);
//        Text helpText = new Text(text);
//        helpText.setFont(Font.font(fontSize));
//        //helpText.setFill(Color.WHITE);
//        //helpText.setStroke(Color.web("#7080A0"));
//
//        stack.getChildren().addAll(helpIcon, helpText);
//        stack.setAlignment(Pos.CENTER);     // Right-justify nodes in stack
//        stack.setTranslateX(x - width / 2);
//        stack.setTranslateY(y - width / 2);
        atomMap.put(resNum + "." + "H" + text, new AtomCoord(x, y));

        return node;
    }

    void hideInfo() {
        infoGroup.getChildren().clear();
    }

    void showInfo(MouseEvent e, String resNum, String aName) {
        String atomSpec = resNum + ".H" + aName;
        Node node = (Node) e.getSource();
        double x = node.getBoundsInParent().getMinX();
        double y = node.getBoundsInParent().getMinY();
        Atom atom = Molecule.getAtomByName(atomSpec);
        if (atom != null) {
            Double ppm = atom.getPPM();
            Double refPPM = atom.getRefPPM();
            String ppmStr = ppm == null ? "      " : String.format("%6.2f", ppm);
            String refppmStr = refPPM == null ? "      " : String.format("%6.2f", refPPM);
            String aString = atom.getShortName();
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
                rect.setOnMousePressed(mE -> {
                    hideInfo();
                });
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

    Line getAtoms(int i, int j) {
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
        double dX = (jX - iX);
        double dY = (jY - iY);
        for (int iText = 0; iText < 5; iText++) {
            double x = iX + dX / 5.0;
        }
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

    void addAtoms(Group group, int[] lineConnections) {
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
        for (int j = 0; j < nRes; j++) {
            Point2D point1 = points.get(j);
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
            double deltaX = 0.0;
            double deltaY = 0.0;
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
////            System.out.printf("%3d %1d %8.2f %8.2f %8.2f\n",iRes,mode, len1,deltaX,deltaY);
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
//            Line line = new Line(x1, y1, x1 + aCoord.x * scale, y1 + aCoord.y * scale);
//            group.getChildren().add(line);
            int nAtoms = nAtomsProp.get();
            for (int j = 0; j < nAtoms; j++) {
                double x = x1 + aCoord.x / 5 * (j + 1.5);
                double y = y1 - aCoord.y / 5 * (j + 1.5);
//                System.out.println(x1 + " " + y1 + " " + aCoord.x + " " + aCoord.y + " " + x + " " + y);
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
                } else if (resChar == 'A') {
                    text = "8";
                } else if (resChar == 'G') {
                    text = "8";
                } else {
                    text = "6";
                }
                if (!text.equals("")) {
                    boolean active = true;
                    if (showActiveProp.get()) {
                        String atomSpec = resName + ".H" + text;
                        Atom atom = Molecule.getAtomByName(atomSpec);
                        if (atom != null) {
                            active = atom.isActive();
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
            for (int i = 0; i < basePairAtoms.length; i++) {
                basePairAtoms[i] = -1;
            }
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
                if ((i != 0) && (chainList.get(i - 1) == chainList.get(i))) {
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
        for (int iPoint = 0; iPoint < points.size(); iPoint++) {
            Point2D point = points.get(iPoint);
            Point2D point2;
            if (iPoint == 0) {
                point2 = points.get(iPoint + 1);
            } else {
                point2 = points.get(iPoint - 1);
            }
            String resName = sequence.get(iRes);
            char resChar = resName.charAt(0);
            double x = point.getX();
            double y = point.getY();
            double x2 = point2.getX();
            double y2 = point2.getY();
            x = toX(x);
            y = toY(y);
            x2 = toX(x2);
            y2 = toY(y2);
            double dX = -1.0 * (y2 - y);
            double dY = x2 - x;
            if (iPoint == 0) {
                dX *= -1.0;
                dY *= -1.0;
            }
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
        for (int i = 0; i < constraintPairs.size(); i += 2) {
            connect(group, constraintPairs.get(i), constraintPairs.get(i + 1));
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
        if (aName.length() == 3) {
            return c1 - '1' + 2;
        } else if ((c1 == '2') || (c1 == '5')) {
            return 0;
        } else {
            return 1;
        }
    }

    void connect(Group group, String a1, String a2) {
        double width = scale * 0.2;
        if (width < 4) {
            width = 4.0;
        }
        String r1 = getResNum(a1);
        String r2 = getResNum(a2);
        int r1Num = Integer.parseInt(r1);
        int r2Num = Integer.parseInt(r2);
        String aName1 = getAtomName(a1);
        String aName2 = getAtomName(a2);
        int nAtoms = nAtomsProp.get();
        if ((getAtomIndex(aName1) >= nAtoms) || (getAtomIndex(aName2) >= nAtoms)) {
            return;
        }
        if (r1Num > r2Num) {
            String hold = a2;
            a2 = a1;
            a1 = hold;
            r1 = getResNum(a1);
            r2 = getResNum(a2);
        } else if (a1.compareTo(a2) > 0) {
            String hold = a2;
            a2 = a1;
            a1 = hold;
            r1 = getResNum(a1);
            r2 = getResNum(a2);
        }
        width *= 0.4;
        AtomCoord c1 = atomMap.get(a1);
        AtomCoord c2 = atomMap.get(a2);
        if (c1 == null) {
        } else if (c2 == null) {
        } else {
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
            curve.setStroke(Color.BLACK);
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
            if (target >= 0) {
                if (i < target) {
                    bpList.add(i);
                    bpList.add(target);
                }
            }
        }
        this.basePairs = new int[bpList.size()];
        for (int i = 0; i < bpList.size(); i++) {
            this.basePairs[i] = bpList.get(i);
        }
    }

    void setConstraints() {

    }

    void setConstraintPairs(ArrayList<String> constraintPairs) {
        this.constraintPairs.clear();
        this.constraintPairs.addAll(constraintPairs);
    }

    void loadCoordinates(String fileName) {
        sequence = null;
        basePairs = null;
        File file = new File(fileName);
        try {
            Scanner in = new Scanner(file);
            while (in.hasNextLine()) {
                String line = in.nextLine();
                String[] fields = line.split("\t");
                double x = Double.parseDouble(fields[0]);
                double y = Double.parseDouble(fields[1]);
                Point2D point = new Point2D(x, y);
                points.add(point);
            }
        } catch (IOException ioE) {
            System.out.println(ioE.getMessage());
        }
    }

    void setDisplay(String mode, int nAtoms) {
        nAtomsProp.set(nAtoms);

    }

    void setDisplay(String mode, boolean state) {
        switch (mode) {
            case "bpairs":
                basePairState = state;
                break;
            case "cpairs":
                constraintPairState = state;
                break;
            case "seq":
                seqState = state;
                break;
            case "active":
                showActiveProp.set(state);
                break;
            case "numbers":
                drawNumbersProp.set(state);
                break;
            default:
                throw new IllegalArgumentException("Unknown display mode \"" + mode + "\"");
        }
    }
}
