/**
 * @author JOHNBRUC
 * @version
 */
package org.nmrfx.analyst.gui.molecule;


import javafx.geometry.*;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Affine;
import org.nmrfx.chemistry.*;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.processor.gui.CanvasAnnotation;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.ChartMenu;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.MoleculePrimitives;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.nmrfx.analyst.gui.molecule.CanvasMolecule.ValueMode.ATOM;
import static org.nmrfx.analyst.gui.molecule.CanvasMolecule.ValueMode.NONE;

public class CanvasMolecule implements CanvasAnnotation {

    public enum ValueMode {
        NONE,
        ATOM,
        PPM,
        VALUE
    }

    ValueMode valueMode = NONE;
    private static final Logger log = LoggerFactory.getLogger(CanvasMolecule.class);
    private static final int BY_ATOM = 0;
    private static final int BY_VALUE = 1;
    private static final int CIRCLE_SHAPE = 0;
    private static final int SQUARE_SHAPE = 1;
    private static final int TRIANGLE_SHAPE = 2;
    PolyChart chart = null;
    ChartMenu menu = null;
    float radius = 0.4f;
    float valueScale = 1.0f;
    float valueZero = 0.0f;
    int posShape = CIRCLE_SHAPE;
    int negShape = SQUARE_SHAPE;
    Color posColor = Color.GREEN;
    Color negColor = Color.RED;
    int colorBy = BY_VALUE;
    boolean scaleShape = true;
    AtomLabels.LabelTypes drawLabels = AtomLabels.LabelTypes.LABEL_NONHCO;
    int iStructure = 0;
    boolean transformValid = false;
    String molName = null;
    Affine canvasTransform = new Affine();
    float canvasScale = 1.0f;
    double stroke3 = 3.0;
    Rectangle2D bounds2D = Rectangle2D.EMPTY;
    int hitAtom = -1;
    byte[] nanoBytes;
    MoleculePrimitives molPrims = new MoleculePrimitives();
    Point2D transformPt = new Point2D(0.0, 0.0);
    double maxX;
    double maxY;
    double maxZ;
    double minX;
    double minY;
    double minZ;

    double x1;
    double y1;
    double x2;
    double y2;

    double xp1;
    double yp1;
    double xp2;
    double yp2;

    double startX1;
    double startY1;
    double startX2;
    double startY2;
    boolean selected = false;
    boolean selectable = true;
    int activeHandle = -1;

    POSTYPE xPosType;
    POSTYPE yPosType;

    double ppm;

    public CanvasMolecule() {
    }

    public CanvasMolecule(PolyChart chart) {
        this.chart = chart;
    }

    @Override
    public void setChart(PolyChart chart) {
        this.chart = chart;
    }

    public double getX1() {
        return x1;
    }

    public void setX1(double x1) {
        this.x1 = x1;
    }

    public double getY1() {
        return y1;
    }

    public void setY1(double y1) {
        this.y1 = y1;
    }

    public double getX2() {
        return x2;
    }

    public void setX2(double x2) {
        this.x2 = x2;
    }

    public double getY2() {
        return y2;
    }

    public void setY2(double y2) {
        this.y2 = y2;
    }

    public void setPosition(double x1, double y1, double x2, double y2, POSTYPE xPosType, POSTYPE yPosType) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.xPosType = xPosType;
        this.yPosType = yPosType;
    }

    public void setPosition(double x1, double y1, double x2, double y2, String xPosType, String yPosType) {
        setPosition(x1, y1, x2, y2, POSTYPE.valueOf(xPosType), POSTYPE.valueOf(yPosType));
    }

    public void setPosition(double x1, double y1, double x2, double y2) {
        setPosition(x1, y1, x2, y2, POSTYPE.PIXEL, POSTYPE.PIXEL);
    }

    @Override
    public POSTYPE getXPosType() {
        return xPosType;
    }

    public void setXPosType(POSTYPE xPosType) {
        this.xPosType = xPosType;
    }

    @Override
    public POSTYPE getYPosType() {
        return yPosType;
    }

    public void setYPosType(POSTYPE yPosType) {
        this.yPosType = yPosType;
    }

    void calcBounds() {
        if (molName == null) {
            return;
        }

        Molecule molecule = Molecule.get(molName);

        if (molecule == null) {
            return;
        }

        molecule.createLineArray(0, molPrims.lines);
        maxX = Double.NEGATIVE_INFINITY;
        maxY = Double.NEGATIVE_INFINITY;
        maxZ = Double.NEGATIVE_INFINITY;
        minX = Double.MAX_VALUE;
        minY = Double.MAX_VALUE;
        minZ = Double.MAX_VALUE;

        for (Line3 line3 : molPrims.lines) {
            Point3 pt1 = line3.pt1();
            double x = pt1.getX();
            double y = pt1.getY();
            double z = pt1.getZ();
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            Point3 pt2 = line3.pt2();
            x = pt2.getX();
            y = pt2.getY();
            z = pt2.getZ();
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
        }
        transformValid = false;
    }

    public String getMolName() {
        return molName;
    }

    public void setMolName(String molName) {
        setMolName(molName, 0);
    }

    public void setMolName(String molName, int i) {
        this.molName = molName;
        this.iStructure = i;
        nanoBytes = null;
        Molecule molecule = Molecule.get(molName);
        if (molecule != null) {
            molecule.updateAtomArray();
            List<Atom> atoms = molecule.getAtomArray();
            atoms.forEach(atom -> atom.setProperty(Atom.DISPLAY));
            molecule.updateBondArray();
            ArrayList<Bond> bonds = molecule.getBondList();
            bonds.forEach(bond -> bond.setProperty(Bond.DISPLAY));
            getSphereCoords();
            getLineCoords();
        }
    }

    public void setNanoBytes(String byteString, boolean useString) {
        if (useString) {
            nanoBytes = new byte[2048];
        }
    }

    public void setScaleShape(boolean bVal) {
        scaleShape = bVal;
    }

    public void setLabels(AtomLabels.LabelTypes labelTypes) {
        drawLabels = labelTypes;
    }

    public void setValueMode(ValueMode valueMode) {
        this.valueMode = valueMode;
    }

    public ValueMode getValueMode() {
        return valueMode;
    }

    public void setColorBy(String colorString) {
        if ("atom".startsWith(colorString)) {
            colorBy = BY_ATOM;
        } else if ("value".startsWith(colorString)) {
            colorBy = BY_VALUE;
        }
    }

    public void setPosShape(String shapeString) {
        if ("circle".startsWith(shapeString)) {
            posShape = CIRCLE_SHAPE;
        } else if ("square".startsWith(shapeString)) {
            posShape = SQUARE_SHAPE;
        } else if ("triangle".startsWith(shapeString)) {
            posShape = TRIANGLE_SHAPE;
        }
    }

    public void setNegShape(String shapeString) {
        if ("circle".startsWith(shapeString)) {
            negShape = CIRCLE_SHAPE;
        } else if ("square".startsWith(shapeString)) {
            negShape = SQUARE_SHAPE;
        } else if ("triangle".startsWith(shapeString)) {
            negShape = TRIANGLE_SHAPE;
        }
    }

    public void setRadius(double radius) {
        this.radius = (float) radius;
    }

    public void setCenter(double x, double y) {
        transformValid = false;
    }

    public void rotX(double angle) {
        rotate('x', angle);
    }

    public void rotY(double angle) {
        rotate('y', angle);
    }

    public void rotZ(double angle) {
        rotate('z', angle);
    }

    public void rotate(char axis, double angle) {

    }

    public void setupTransform() {
        calcBounds();
        double dXMol = maxX - minX;
        double dYMol = maxY - minY;
        double xCenterMol = (maxX + minX) / 2.0;
        double yCenterMol = (maxY + minY) / 2.0;
        double dX = xp2 - xp1;
        double dY = yp2 - yp1;
        double scaleX = dX / dXMol;
        double scaleY = dY / dYMol;
        double scale;
        scale = scaleY;
        if ((scaleY * dXMol) > dX) {
            scale = scaleX;
        }
        scale *= 0.9;
        double xCenter = (xp2 + xp1) / 2.0;
        double yCenter = (yp2 + yp1) / 2.0;
        canvasTransform.setToIdentity();
        canvasTransform.appendTranslation(xCenter, yCenter);
        canvasTransform.appendScale(scale, -scale);
        canvasTransform.appendTranslation(-xCenterMol, -yCenterMol);
        canvasScale = (float) scale;
        transformValid = true;
    }

    private boolean getCoordSystemTransform() {
        return true;
    }

    @Override
    public ChartMenu getMenu() {
        if ((chart != null) && (menu == null)) {
            menu = new MoleculeMenu(chart, this);
        }
        return menu;
    }

    String getHit() {
        if (hitAtom == -1) {
            return ("");
        } else if (molPrims.pointCs != null) {
            return (molPrims.pointCs.get(hitAtom).atom().getFullName());
        } else {
            return (String.valueOf(hitAtom));
        }
    }

    public int pick(GraphicsContextInterface gC, double x, double y) {
        if (!validate()) {
            return -1;
        } else {
            return genSpheres(gC, true, x, y);
        }
    }

    void selectAtom(boolean selectMode) {
        String aName = getHit();
        Molecule molecule = Molecule.get(molName);
        try {
            molecule.selectAtoms(aName);
        } catch (InvalidMoleculeException ex) {
            log.warn(ex.getMessage(), ex);
        }
        if (selectMode && selectable) {
            selected = true;
            assignPeak(molecule);
        }
    }

    void assignPeak(PeakDim peakDim, List<Atom> atoms) {
        Atom currentAtom = peakDim.getResonance().getAtom();
        if (!atoms.isEmpty() && ((currentAtom == null) || (GUIUtils.affirm("Already assigned, change assignment?")))) {
            for (Atom atom : atoms) {
                peakDim.getResonance().setAtom(atom);
                peakDim.setLabel(atom.getShortName());
                atom.setPPM(peakDim.getChemShiftValue());
                chart.clearSelectedMultiplets();
            }
        }

    }

    void assignPeak(Molecule molecule) {
        var selectedSpatialSets = molecule.selectedSpatialSets();
        var peaks = chart.getSelectedPeaks();
        if (peaks.size() == 1 && selectedSpatialSets.size() == 1) {
            Peak peak = peaks.get(0);
            PeakList peakList = peak.getPeakList();
            SpectralDim spectralDim = peakList.getSpectralDim(0);
            String nucNumberName = spectralDim.getNucleus();
            if (peak.getPeakList().getNDim() == 1) {
                PeakDim peakDim = peak.getPeakDim(0);
                boolean nucOK;
                List<Atom> atoms = new ArrayList<>();
                Atom selectedAtom = selectedSpatialSets.get(0).getAtom();
                if (nucNumberName.equals("1H")) {
                    nucOK = selectedAtom.getAtomicNumber() == 1;
                    if (nucOK) {
                        atoms.add(selectedAtom);
                    } else {
                        List<Atom> children = selectedAtom.getChildren();
                        for (var child : children) {
                            if (child.getAtomicNumber() == 1) {
                                atoms.add(child);
                            }
                        }
                    }
                } else if (nucNumberName.equals("13C")) {
                    nucOK = selectedAtom.getAtomicNumber() == 6;
                    if (nucOK) {
                        atoms.add(selectedAtom);
                    }
                }
                assignPeak(peakDim, atoms);
            }
        }
    }

    void selectMolecule(boolean selectMode) {
        Molecule molecule = Molecule.get(molName);
        if (molecule != null) {
            if (!molecule.globalSelected.isEmpty()) {
                molecule.clearSelected();
            }
            if (selectMode && selectable) {
                selected = true;
            }
        }
    }


    @Override
    public boolean hit(double x, double y, boolean selectMode) {
        if (!bounds2D.contains(x, y)) {
            hitAtom = -1;
            if (selectMode) {
                selected = false;
            }
            return false;
        } else {
            hitAtom = pick(null, x, y);
            startX1 = x1;
            startY1 = y1;
            startX2 = x2;
            startY2 = y2;
            if (hitAtom == -1) {
                selectMolecule(selectMode);
            } else {
                selectAtom(selectMode);
            }
            return true;
        }
    }

    /**
     * Moves the molecule around the canvas. If a handle is selected, the handle can be
     * moved to adjust the size of the molecule but, it cannot be moved past another handle.
     * (i.e. The molecule cannot be sized to a negative width or height)
     *
     * @param bounds The bounds of the canvas.
     * @param world  The bounds of the canvas in the units of the canvas axis.
     * @param start  The starting position.
     * @param pos    The new position.
     */
    @Override
    public void move(double[][] bounds, double[][] world, double[] start, double[] pos) {
        double dx = pos[0] - start[0];
        double dy = pos[1] - start[1];
        double handleSeparationLimit = getHandleSeparationLimit(bounds, world);
        if (activeHandle < 0) {
            x1 = xPosType.move(startX1, dx, bounds[0], world[0]);
            x2 = xPosType.move(startX2, dx, bounds[0], world[0]);
            y1 = yPosType.move(startY1, dy, bounds[1], world[1]);
            y2 = yPosType.move(startY2, dy, bounds[1], world[1]);

        } else if (activeHandle == 0) { // upper left
            x1 = xPosType.move(startX1, dx, bounds[0], world[0]);
            y1 = yPosType.move(startY1, dy, bounds[1], world[1]);
            x1 = Math.min(x1, x2 - handleSeparationLimit);
            y1 = Math.min(y1, y2 - handleSeparationLimit);
        } else if (activeHandle == 1) { // upper right
            x2 = xPosType.move(startX2, dx, bounds[0], world[0]);
            y1 = yPosType.move(startY1, dy, bounds[1], world[1]);
            x2 = Math.max(x1 + handleSeparationLimit, x2);
            y1 = Math.min(y1, y2 - handleSeparationLimit);
        } else if (activeHandle == 2) { // bottom right
            x2 = xPosType.move(startX2, dx, bounds[0], world[0]);
            y2 = yPosType.move(startY2, dy, bounds[1], world[1]);
            x2 = Math.max(x1 + handleSeparationLimit, x2);
            y2 = Math.max(y1 + handleSeparationLimit, y2);
        } else if (activeHandle == 3) { // bottom left
            x1 = xPosType.move(startX1, dx, bounds[0], world[0]);
            y2 = yPosType.move(startY2, dy, bounds[1], world[1]);
            x1 = Math.min(x1, x2 - handleSeparationLimit);
            y2 = Math.max(y1 + handleSeparationLimit, y2);
        }
    }

    public void zoom(double factor) {
        double deltaBx = (factor - 1.0) * (x2 - x1);
        double deltaBy = (factor - 1.0) * (y2 - y1);
        x1 = x1 + deltaBx;
        y1 = y1 + deltaBy;
        x2 = x2 - deltaBx;
        y2 = y2 - deltaBy;
        if (chart != null) {
            chart.drawPeakLists(false);
        }

    }

    public void redraw() {
        if (chart != null) {
            chart.drawPeakLists(false);
        }
    }

    public void draw(GraphicsContextInterface gC, double[][] canvasBounds, double[][] worldBounds) {
        xp1 = xPosType.transform(x1, canvasBounds[0], worldBounds[0]);
        xp2 = xPosType.transform(x2, canvasBounds[0], worldBounds[0]);
        yp1 = yPosType.transform(y1, canvasBounds[1], worldBounds[1]);
        yp2 = yPosType.transform(y2, canvasBounds[1], worldBounds[1]);
        double xMin = Math.min(xp1, xp2);
        double yMin = Math.min(yp1, yp2);
        double width = Math.abs(xp2 - xp1);
        double height = Math.abs(yp2 - yp1);

        bounds2D = new Rectangle2D(xMin, yMin, width, height);
        if (!validate()) {
            return;
        }
        transformValid = false;
        paintShape(gC);
        if (isSelected()) {
            drawHandles(gC);
        }
    }

    boolean validate() {
        Molecule molecule = null;
        if (molName != null) {
            molecule = Molecule.get(molName);
        }
        return molecule != null;
    }

    public void paintShape(GraphicsContextInterface g2) {
        boolean drawSpheres = true;
        boolean drawLines = true;
        Molecule molecule = null;

        if (molName != null) {
            molecule = Molecule.get(molName);
        }

        if (molecule == null) {
            return;
        }
        drawSpheres = false;
        posShape = CIRCLE_SHAPE;
        negShape = CIRCLE_SHAPE;

        if (!molecule.labelsCurrent) {
            molecule.updateLabels();
        }

        if (molecule.display.equals("ball")
                || molecule.display.equals("pball")) {
            drawSpheres = true;

            if (molecule.display.equals("pball")) {
                setPosShape(molecule.posShapeType);
                setNegShape(molecule.negShapeType);
            }
        }

        if (molecule.display.equals("cpk")) {
            drawLines = false;
        }


        genSpheres(g2, false, 0, 0);


        if (drawLines) {
            genLines(g2);
        }

        if (drawLabels != AtomLabels.LabelTypes.LABEL_NONE) {
            genLabels(g2);
        }
        List<SelectionPoint> selectionPoints = new ArrayList<>();
        molecule.createSelectionArray(iStructure, selectionPoints);
        genSelectionSymbols(g2, selectionPoints);

    }

    public void getSphereCoords() {
        if (molName == null) {
            return;
        }

        Molecule molecule = Molecule.get(molName);

        if (molecule == null) {
            return;
        }

        if (iStructure >= molecule.structures.size()) {
            return;
        }

        molecule.createSphereArray(iStructure, molPrims.pointCs);
    }

    public void getLabelCoords() {
        if (molName == null) {
            return;
        }

        Molecule molecule = Molecule.get(molName);

        if (molecule == null) {
            return;
        }

        if (iStructure >= molecule.structures.size()) {
            return;
        }

        molPrims.nLabels = molecule.getLabelCount(iStructure);

        if ((molPrims.labelCoords == null)
                || (molPrims.labelCoords.length != (molPrims.nLabels * 3))) {
            molPrims.labelCoords = new float[molPrims.nLabels * 3];
        }

        molecule.createLabelArray(iStructure,
                molPrims.labelCoords, 0);
    }

    public void showPPM(double ppm) {
        this.ppm = ppm;
        redraw();
    }

    public int genSpheres(GraphicsContextInterface gC, boolean pickMode, double pickX, double pickY) {
        float dX = radius * canvasScale;
        float dY = radius * canvasScale;

        if (!getCoordSystemTransform()) {
            return 0;
        }

        if (!transformValid) {
            setupTransform();
        }
        int i = 0;
        for (Point3C point3C : molPrims.pointCs) {
            Point3 pt1 = point3C.pt1();
            Point3D vecIn = new Point3D(pt1.getX(), pt1.getY(), pt1.getZ());
            Point3D vecOut = canvasTransform.transform(vecIn);

            double x = vecOut.getX() + (float) transformPt.getX();
            double y = vecOut.getY() + (float) transformPt.getY();

            float value = 0;

            if (pickMode) {
                if ((pickX > (x - dX)) && (pickX < (x + dX))
                        && (pickY > (y - dY)) && (pickY < (y + dY))) {
                    return i;
                }
            } else {
                int shapeMode = 0;
                float vRadius = canvasScale * radius;
                float triangleHeight = canvasScale * radius;

                if (valueMode == ValueMode.PPM) {
                    if (!point3C.atom().getElementName().startsWith("H")) {
                        continue;
                    }
                    PPMv ppMv = point3C.atom().getPPM(0);
                    PPMv refPPM = point3C.atom().getRefPPM(0);
                    Double atomPPM = null;
                    if ((ppMv != null) && ppMv.isValid()) {
                        atomPPM = ppMv.getValue();
                    } else if ((refPPM != null) && refPPM.isValid()) {
                        atomPPM = refPPM.getValue();
                    }
                    if (atomPPM != null) {
                        double deltaPPM = Math.min(1.0, Math.abs(ppm - atomPPM));
                        value = (float) (1.0 - deltaPPM);
                    } else {
                        continue;
                    }

                    shapeMode = TRIANGLE_SHAPE;
                } else if (valueMode == ValueMode.VALUE) {
                    value = (float) point3C.value();
                    shapeMode = TRIANGLE_SHAPE;
                } else {
                    shapeMode = CIRCLE_SHAPE;
                    vRadius = radius * canvasScale;
                }


                if (valueMode != ATOM) {
                    if (value > 0) {
                        shapeMode = posShape;
                        triangleHeight = vRadius;
                    } else {
                        triangleHeight = -vRadius;
                        shapeMode = negShape;
                    }
                    if (scaleShape) {
                        vRadius = (canvasScale * radius * Math.abs(value)) / valueScale;
                    }
                }
                Color fill;
                Color outline;
                if ((valueMode != ATOM) && (colorBy == BY_VALUE)) {
                    if (value > 0) {
                        fill = posColor;
                        outline = posColor;
                    } else {
                        fill = negColor;
                        outline = negColor;
                    }
                } else {
                    outline = Color.rgb(point3C.color1().getRed(), point3C.color1().getGreen(), point3C.color1().getBlue());
                    fill = Color.rgb(point3C.color1().getRed(), point3C.color1().getGreen(), point3C.color1().getBlue());
                }
                try {
                    gC.setFill(fill);
                    gC.setStroke(outline);
                    gC.setLineWidth(stroke3);
                    double vWidth = vRadius * 2.0;

                    switch (shapeMode) {
                        case CIRCLE_SHAPE: {
                            gC.fillOval(x - vRadius, y - vRadius, vWidth, vWidth);
                            gC.strokeOval(x - vRadius, y - vRadius, vWidth, vWidth);
                            break;
                        }

                        case SQUARE_SHAPE: {
                            gC.fillRect(x - vRadius, y - vRadius, vWidth, vWidth);
                            gC.strokeRect(x - vRadius, y - vRadius, vWidth, vWidth);
                            break;
                        }

                        case TRIANGLE_SHAPE: {
                            gC.beginPath();
                            gC.moveTo(x - dX, y + triangleHeight);
                            gC.lineTo(x + dX, y + triangleHeight);
                            gC.lineTo(x, y - triangleHeight);
                            gC.closePath();
                            gC.fill();
                            gC.stroke();
                            break;
                        }
                        default: // fixme should throw Exception
                    }
                } catch (Exception ex) {
                    log.error(ex.getMessage(), ex);
                }

            }
            i++;
        }
        return -1;
    }

    public int genLabels(GraphicsContextInterface gC) {

        if (!getCoordSystemTransform()) {
            return 0;
        }

        if (!transformValid) {
            setupTransform();
        }

        for (Point3C point3C : molPrims.pointCs) {
            Point3 pt1 = point3C.pt1();
            Point3D vecIn = new Point3D(pt1.getX(), pt1.getY(), pt1.getZ());
            Point3D vecOut = canvasTransform.transform(vecIn);

            double x = vecOut.getX() + (float) transformPt.getX();
            double y = vecOut.getY() + (float) transformPt.getY();

            String label = null;
            Atom atom = point3C.atom();
            if (true || atom.getProperty(Atom.LABEL)) {
                label = AtomLabels.getAtomLabel(atom, drawLabels);
                gC.setLineWidth(1.0);
                if ((label != null) && !label.equals("C") && !label.equals("")) {
                    try {
                        gC.setStroke(Color.BLACK);
                        gC.setTextAlign(TextAlignment.CENTER);
                        gC.setTextBaseline(VPos.CENTER);
                        gC.strokeText(label, x, y);
                    } catch (Exception ex) {
                        log.error(ex.getMessage(), ex);
                    }
                }
            }
        }


        return -1;
    }

    public void genSelectionSymbols(GraphicsContextInterface gC, List<SelectionPoint> selectionPoints) {

        if (!transformValid) {
            setupTransform();
        }

        int k = 0;
        for (SelectionPoint selectionPoint : selectionPoints) {
            Point3 pt1 = selectionPoint.pt1();
            Point3D vecIn1 = new Point3D(pt1.getX(), pt1.getY(), pt1.getZ());
            Point3D vecOut1 = canvasTransform.transform(vecIn1);
            double x = vecOut1.getX() + transformPt.getX();
            double y = vecOut1.getY() + transformPt.getY();
            if ((Molecule.selCycleCount == 0)
                    || ((Molecule.selCycleCount != 1)
                    && (((k + 1) % Molecule.selCycleCount) == 0))) {
                try {
                    float vRadius = (canvasScale * radius) / 2.0f * (1
                            + selectionPoint.selectionLevel()) + 2;
                    gC.setStroke(Color.ORANGE);
                    gC.strokeOval(x - vRadius, y - vRadius, vRadius * 2,
                            vRadius * 2);

                    break;
                } catch (Exception ex) {
                    log.error(ex.getMessage(), ex);
                }
            }
        }
    }

    public void getLineCoords() {
        if (molName == null) {
            return;
        }

        Molecule molecule = Molecule.get(molName);

        if (molecule == null) {
            return;
        }

        if (iStructure >= molecule.structures.size()) {
            return;
        }

        molecule.createLineArray(0, molPrims.lines);
    }

    public void genLines(GraphicsContextInterface gC) {
        if (!getCoordSystemTransform()) {
            return;
        }

        if (!transformValid) {
            setupTransform();
        }
        gC.setLineWidth(stroke3);

        for (Line3 line3 : molPrims.lines) {
            try {
                Point3 pt1 = line3.pt1();
                Point3D vecIn1 = new Point3D(pt1.getX(), pt1.getY(), pt1.getZ());
                Point3D vecOut1 = canvasTransform.transform(vecIn1);
                double vx1 = vecOut1.getX() + transformPt.getX();
                double vy1 = vecOut1.getY() + transformPt.getY();

                Point3 pt2 = line3.pt2();
                Point3D vecIn2 = new Point3D(pt2.getX(), pt2.getY(), pt2.getZ());
                Point3D vecOut2 = canvasTransform.transform(vecIn2);
                double vx2 = vecOut2.getX() + transformPt.getX();
                double vy2 = vecOut2.getY() + transformPt.getY();

                double xm = (vx1 + vx2) / 2.0f;
                double ym = (vy1 + vy2) / 2.0f;
                Color color = Color.rgb(line3.color1().getRed(), line3.color1().getGreen(), line3.color1().getBlue());

                gC.setStroke(color);
                gC.strokeLine(vx1, vy1, xm, ym);

                Color color2 = Color.rgb(line3.color2().getRed(), line3.color2().getGreen(), line3.color2().getBlue());
                gC.setStroke(color2);
                gC.strokeLine(xm, ym, vx2, vy2);
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }

    @Override
    public void drawHandles(GraphicsContextInterface gC) {
        drawHandle(gC, bounds2D.getMinX(), bounds2D.getMinY(), Pos.BOTTOM_RIGHT);
        drawHandle(gC, bounds2D.getMaxX(), bounds2D.getMinY(), Pos.BOTTOM_LEFT);
        drawHandle(gC, bounds2D.getMaxX(), bounds2D.getMaxY(), Pos.TOP_LEFT);
        drawHandle(gC, bounds2D.getMinX(), bounds2D.getMaxY(), Pos.TOP_RIGHT);
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public boolean isSelectable() {
        return selectable;
    }

    @Override
    public int hitHandle(double x, double y) {
        if (hitHandle(x, y, Pos.BOTTOM_RIGHT, bounds2D.getMinX(), bounds2D.getMinY())) {
            activeHandle = 0;
        } else if (hitHandle(x, y, Pos.BOTTOM_LEFT, bounds2D.getMaxX(), bounds2D.getMinY())) {
            activeHandle = 1;
        } else if (hitHandle(x, y, Pos.TOP_LEFT, bounds2D.getMaxX(), bounds2D.getMaxY())) {
            activeHandle = 2;
        } else if (hitHandle(x, y, Pos.TOP_RIGHT, bounds2D.getMinX(), bounds2D.getMaxY())) {
            activeHandle = 3;
        } else {
            activeHandle = -1;
        }
        return activeHandle;
    }

    public void updateXPosType(POSTYPE newType, double[] bounds, double[] world) {
        double x1Pix = xPosType.transform(x1, bounds, world);
        double x2Pix = xPosType.transform(x2, bounds, world);
        x1 = newType.itransform(x1Pix, bounds, world);
        x2 = newType.itransform(x2Pix, bounds, world);
        xPosType = newType;
    }

    public void updateYPosType(POSTYPE newType, double[] bounds, double[] world) {
        double y1Pix = yPosType.transform(y1, bounds, world);
        double y2Pix = yPosType.transform(y2, bounds, world);
        y1 = newType.itransform(y1Pix, bounds, world);
        y2 = newType.itransform(y2Pix, bounds, world);
        yPosType = newType;
    }
}
