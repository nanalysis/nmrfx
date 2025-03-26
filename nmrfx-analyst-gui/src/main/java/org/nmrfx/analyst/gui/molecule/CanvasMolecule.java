/**
 * @author JOHNBRUC
 * @version
 */
package org.nmrfx.analyst.gui.molecule;


import javafx.geometry.*;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Affine;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Bond;
import org.nmrfx.chemistry.InvalidMoleculeException;
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

public class CanvasMolecule implements CanvasAnnotation {
    private static final Logger log = LoggerFactory.getLogger(CanvasMolecule.class);
    private static final int BY_ATOM = 0;
    private static final int BY_VALUE = 1;
    private static final int CIRCLE_SHAPE = 0;
    private static final int SQUARE_SHAPE = 1;
    private static final int TRIANGLE_SHAPE = 2;
    PolyChart chart = null;
    ChartMenu menu = null;
    float radius = 0.4f;
    float valueScale = 10.0f;
    float valueZero = 0.0f;
    boolean valueMode = true;
    int posShape = CIRCLE_SHAPE;
    int negShape = SQUARE_SHAPE;
    Color posColor = Color.RED;
    Color negColor = Color.BLUE;
    int colorBy = BY_VALUE;
    boolean scaleShape = true;
    boolean drawLabels = true;
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

    public CanvasMolecule() {
    }

    public CanvasMolecule(PolyChart chart) {
        this.chart = chart;
    }

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

        molPrims.nLines = molecule.getLineCount(0);

        int nCoords = molPrims.nLines * 2 * 3;
        float[] coords = new float[nCoords];
        float[] colors = new float[nCoords];
        molecule.createLineArray(0, coords, 0, colors);
        maxX = Double.NEGATIVE_INFINITY;
        maxY = Double.NEGATIVE_INFINITY;
        maxZ = Double.NEGATIVE_INFINITY;
        minX = Double.MAX_VALUE;
        minY = Double.MAX_VALUE;
        minZ = Double.MAX_VALUE;

        for (int i = 0; i < molPrims.nLines; i++) {
            double x = molPrims.lineCoords[i * 6];
            double y = molPrims.lineCoords[(i * 6) + 1];
            double z = molPrims.lineCoords[(i * 6) + 2];
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            x = molPrims.lineCoords[i * 6 + 3];
            y = molPrims.lineCoords[(i * 6) + 4];
            z = molPrims.lineCoords[(i * 6) + 5];
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
            getLabelCoords();
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

    public void setLabels(boolean bVal) {
        drawLabels = bVal;
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
        } else if (molPrims.atoms != null) {
            return (molPrims.atoms[hitAtom].getFullName());
        } else {
            return (String.valueOf(hitAtom));
        }
    }

    public int pick(GraphicsContextInterface gC, double x, double y) {
        validate();
        return genSpheres(gC, true, x, y);
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
            for (Atom atom: atoms) {
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

    public void draw(GraphicsContextInterface gC, double[][] canvasBounds, double[][] worldBounds) {
        validate();
        xp1 = xPosType.transform(x1, canvasBounds[0], worldBounds[0]);
        xp2 = xPosType.transform(x2, canvasBounds[0], worldBounds[0]);
        yp1 = yPosType.transform(y1, canvasBounds[1], worldBounds[1]);
        yp2 = yPosType.transform(y2, canvasBounds[1], worldBounds[1]);
        double xMin = Math.min(xp1, xp2);
        double yMin = Math.min(yp1, yp2);
        double width = Math.abs(xp2 - xp1);
        double height = Math.abs(yp2 - yp1);

        bounds2D = new Rectangle2D(xMin, yMin, width, height);
        transformValid = false;
        paintShape(gC);
        if (isSelected()) {
            drawHandles(gC);
        }
    }

    void validate() {
        Molecule molecule = null;
        if (molName != null) {
            molecule = Molecule.get(molName);
        }

        boolean ok = true;
        if (molecule != null) {
            int nSpheres = molecule.getSphereCount(iStructure);
            if (molPrims.nSpheres != nSpheres) {
                ok = false;
            }
            if (ok) {
                int nLines = molecule.getLineCount(iStructure);
                if (molPrims.nLines != nLines) {
                    ok = false;
                }
            }
            if (ok) {
                int nLabels = molecule.getLineCount(iStructure);
                if (molPrims.nLabels != nLabels) {
                    ok = false;
                }
            }
            if (!ok) {
                setMolName(molName, iStructure);
            }
        }

        }
    public void paintShape(GraphicsContextInterface g2) {
        boolean drawSpheres = true;
        boolean drawLines = true;
        Molecule molecule = null;

        if (molName != null) {
            molecule = Molecule.get(molName);
        }

        if (molecule != null) {
            drawSpheres = false;
            drawLabels = true;
            valueMode = false;
            posShape = CIRCLE_SHAPE;
            negShape = CIRCLE_SHAPE;

            if (!molecule.labelsCurrent) {
                molecule.updateLabels();
            }

            if (molecule.display.equals("ball")
                    || molecule.display.equals("pball")) {
                drawSpheres = true;

                if (molecule.display.equals("pball")) {
                    valueMode = true;
                    setPosShape(molecule.posShapeType);
                    setNegShape(molecule.negShapeType);
                }
            }

            if (molecule.display.equals("cpk")) {
                drawLines = false;
            }

            if (molecule.label == 0) {
                drawLabels = false;
            }
        }
        if (drawSpheres) {
            genSpheres(g2, false, 0, 0);
        }

        if (drawLines) {
            genLines(g2);
        }

        if (drawLabels) {
            genLabels(g2);
        }
        getSelectionCoords();
        genSelectionSymbols(g2);

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

        molPrims.nSpheres = molecule.getSphereCount(iStructure);

        if ((molPrims.sphereCoords == null)
                || (molPrims.sphereCoords.length != (molPrims.nSpheres * 3))) {
            molPrims.sphereCoords = new float[molPrims.nSpheres * 3];
            molPrims.sphereColors = new float[molPrims.nSpheres * 3];
            molPrims.sphereValues = new float[molPrims.nSpheres];
        }

        int nVertices = molecule.createSphereArray(iStructure,
                molPrims.sphereCoords, 0, molPrims.sphereColors,
                molPrims.sphereValues);
        molPrims.atoms = new Atom[nVertices];
        molecule.getAtoms(iStructure, molPrims.atoms);
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

    public void getSelectionCoords() {
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

        molPrims.nSelected = molecule.globalSelected.size();

        int n = molPrims.nSelected;

        if ((molPrims.selectionCoords == null)
                || (molPrims.selectionCoords.length != (n * 18))) {
            molPrims.selectionCoords = new float[n * 18];
            molPrims.selectionLevels = new int[n];
        }

        molecule.createSelectionArray(iStructure,
                molPrims.selectionCoords, molPrims.selectionLevels);
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
        for (int i = 0; i < molPrims.nSpheres; i++) {
            double x = molPrims.sphereCoords[i * 3];
            double y = molPrims.sphereCoords[(i * 3) + 1];
            double z = molPrims.sphereCoords[(i * 3) + 2];
            Point3D vecIn = new Point3D(x, y, z);
            Point3D vecOut = canvasTransform.transform(vecIn);

            x = vecOut.getX() + (float) transformPt.getX();
            y = vecOut.getY() + (float) transformPt.getY();

            z = vecOut.getZ();

            float value = 0;

            if (pickMode) {
                if ((pickX > (x - dX)) && (pickX < (x + dX))
                        && (pickY > (y - dY)) && (pickY < (y + dY))) {
                    return i;
                }
            } else {
                float[] sphereColors = molPrims.sphereColors;
                int shapeMode = 0;
                float vRadius = canvasScale * radius;
                float triangleHeight = canvasScale * radius;

                if (valueMode && (molPrims.sphereValues != null)) {
                    value = molPrims.sphereValues[i] - valueZero;

                    if (scaleShape) {
                        vRadius = (canvasScale * radius * Math.abs(value)) / valueScale;
                    }

                    if (value > 0) {
                        shapeMode = posShape;
                        triangleHeight = vRadius;
                    } else {
                        triangleHeight = -vRadius;
                        shapeMode = negShape;
                    }
                } else {
                    valueMode = false;
                    shapeMode = 0;
                    vRadius = radius * canvasScale;
                }
                Color fill;
                Color outline;
                if (valueMode && (colorBy == BY_VALUE)) {
                    if (value > 0) {
                        fill = posColor;
                        outline = posColor;
                    } else {
                        fill = negColor;
                        outline = negColor;
                    }
                } else {
                    try {
                        outline = Color.color(sphereColors[i * 3],
                                sphereColors[(i * 3) + 1], sphereColors[(i * 3)
                                        + 2]);
                        fill = Color.color(sphereColors[i * 3],
                                sphereColors[(i * 3) + 1], sphereColors[(i * 3)
                                        + 2]);
                        gC.setFill(fill);
                        gC.setStroke(outline);
                        gC.setLineWidth(stroke3);

                        switch (shapeMode) {
                            case CIRCLE_SHAPE: {
                                gC.fillOval(x, y, vRadius, vRadius);
                                gC.strokeOval(x, y, vRadius, vRadius);
                                break;
                            }

                            case SQUARE_SHAPE: {
                                gC.fillRect(x - vRadius, y - vRadius, vRadius * 2, vRadius * 2);
                                gC.strokeRect(x - vRadius, y - vRadius, vRadius * 2, vRadius * 2);
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
            }
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

        float[] anchor = {0.5f, 0.5f};
        for (int i = 0; i < molPrims.nLabels; i++) {
            double x = molPrims.labelCoords[i * 3];
            double y = molPrims.labelCoords[(i * 3) + 1];
            double z = molPrims.labelCoords[(i * 3) + 2];
            Point3D vecIn = new Point3D(x, y, z);
            Point3D vecOut = canvasTransform.transform(vecIn);

            x = vecOut.getX() + transformPt.getX();
            y = vecOut.getY() + transformPt.getY();

            z = vecOut.getZ();

            float vRadius = canvasScale * radius;

            String label = null;

            if (molPrims.atoms != null) {
                label = molPrims.atoms[i].label;
            } else {
                label = molPrims.labels[i];
            }
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

        return -1;
    }

    public int genSelectionSymbols(GraphicsContextInterface gC) {

        if (!transformValid) {
            setupTransform();
        }

        int j;
        int k = 0;

        for (int i = 0; i < molPrims.nSelected; i++) {
            for (j = 0; j < 6; j++) {
                double x = molPrims.selectionCoords[(i * 18) + (j * 3)];
                double y = molPrims.selectionCoords[(i * 18) + (j * 3)
                        + 1];
                double z = molPrims.selectionCoords[(i * 18) + (j * 3)
                        + 2];

                Point3D vecIn = new Point3D(x, y, z);
                Point3D vecOut = canvasTransform.transform(vecIn);

                x = vecOut.getX() + transformPt.getX();
                y = vecOut.getY() + transformPt.getY();

                z = vecOut.getZ();

                if ((Molecule.selCycleCount == 0)
                        || ((k + 1) >= molPrims.nSelected)
                        || ((Molecule.selCycleCount != 1)
                        && (((k + 1) % Molecule.selCycleCount) == 0))) {
                    try {
                        float vRadius = (canvasScale * radius) / 2.0f * (1
                                + molPrims.selectionLevels[i]) + 2;
                        gC.setStroke(Color.ORANGE);
                        gC.strokeOval(x - vRadius, y - vRadius, vRadius * 2,
                                vRadius * 2);

                        break;
                    } catch (Exception ex) {
                        log.error(ex.getMessage(), ex);
                    }
                }
            }

            k++;
        }

        return -1;
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

        molPrims.nLines = molecule.getLineCount(0);

        int nCoords = molPrims.nLines * 2 * 3;
        molPrims.lineCoords = new float[nCoords];
        molPrims.lineColors = new float[nCoords];

        molecule.createLineArray(0, molPrims.lineCoords, 0,
                molPrims.lineColors);
    }

    public void genLines(GraphicsContextInterface gC) {
        double x1;
        double y1;
        double z1;
        double x2;
        double y2;
        double z2;
        double xm;
        double ym;

        if (!getCoordSystemTransform()) {
            return;
        }

        if (!transformValid) {
            setupTransform();
        }
        gC.setLineWidth(stroke3);

        for (int i = 0; i < molPrims.nLines; i++) {
            try {
                x1 = molPrims.lineCoords[i * 6];
                y1 = molPrims.lineCoords[(i * 6) + 1];
                z1 = molPrims.lineCoords[(i * 6) + 2];

                Point3D vecIn1 = new Point3D(x1, y1, z1);
                Point3D vecOut1 = canvasTransform.transform(vecIn1);
                x1 = vecOut1.getX() + transformPt.getX();
                y1 = vecOut1.getY() + transformPt.getY();
                z1 = vecOut1.getZ();

                x2 = molPrims.lineCoords[(i * 6) + 3];
                y2 = molPrims.lineCoords[(i * 6) + 4];
                z2 = molPrims.lineCoords[(i * 6) + 5];

                Point3D vecIn2 = new Point3D(x2, y2, z2);
                Point3D vecOut2 = canvasTransform.transform(vecIn2);

                x2 = vecOut2.getX() + transformPt.getX();
                y2 = vecOut2.getY() + transformPt.getY();
                z2 = vecOut2.getZ();

                xm = (x1 + x2) / 2.0f;
                ym = (y1 + y2) / 2.0f;
                Color color = Color.color(molPrims.lineColors[i * 6],
                        molPrims.lineColors[(i * 6) + 1],
                        molPrims.lineColors[(i * 6) + 2]);

                gC.setStroke(color);

                gC.strokeLine(x1, y1, xm, ym);

                Color color2 = Color.color(molPrims.lineColors[(i * 6) + 3],
                        molPrims.lineColors[(i * 6) + 4],
                        molPrims.lineColors[(i * 6) + 5]);
                gC.setStroke(color2);
                gC.strokeLine(xm, ym, x2, y2);
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

    //@Override
    //public void setSelectable(boolean state) {
     //   selectable = state;
    //}

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
