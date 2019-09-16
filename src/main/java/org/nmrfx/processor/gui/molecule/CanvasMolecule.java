/**
 *
 * @author JOHNBRUC
 * @version
 */
package org.nmrfx.processor.gui.molecule;

import org.nmrfx.structure.chemistry.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.processor.gui.CanvasAnnotation;

public class CanvasMolecule implements CanvasAnnotation {

    private static final int BY_ATOM = 0;
    private static final int BY_VALUE = 1;
    private static final int CIRCLE_SHAPE = 0;
    private static final int SQUARE_SHAPE = 1;
    private static final int TRIANGLE_SHAPE = 2;
    float radius = 0.4f;
    boolean closePath = false;
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
    double strokeD = 1.0;
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

    double bx1;
    double by1;
    double bx2;
    double by2;
    POSTYPE xPosType;
    POSTYPE yPosType;

    public CanvasMolecule() {
    }

    public void setPosition(double x1, double y1, double x2, double y2, POSTYPE xPosType, POSTYPE yPosType) {
        bx1 = x1;
        by1 = y1;
        bx2 = x2;
        by2 = y2;
        this.xPosType = xPosType;
        this.yPosType = yPosType;
    }

    public void setPosition(double x1, double y1, double x2, double y2, String xPosType, String yPosType) {
        setPosition(x1, y1, x2, y2, POSTYPE.valueOf(xPosType), POSTYPE.valueOf(yPosType));
    }

    public void setPosition(double x1, double y1, double x2, double y2) {
        setPosition(x1, y1, x2, y2, POSTYPE.PIXEL, POSTYPE.PIXEL);
    }

    public POSTYPE getXPosType() {
        return xPosType;
    }

    public POSTYPE getYPosType() {
        return yPosType;
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
            atoms.forEach((atom) -> {
                atom.setProperty(Atom.DISPLAY);
            });
            molecule.updateBondArray();
            ArrayList<Bond> bonds = molecule.getBondList();
            bonds.forEach((bond) -> {
                bond.setProperty(Bond.DISPLAY);
            });
            getSphereCoords();
            getLineCoords();
            getLabelCoords();
        }
    }

    public void setNanoBytes(String byteString, boolean useString) {
        if (useString) {
            nanoBytes = new byte[2048];

            //nBytes = Molecule.uuDecode(byteString,  nanoBytes);
            //molPrims = new MoleculePrimitives();
            //molPrims.unCompXYZToArray(nanoBytes,nBytes);
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
        /*
         m4Temp.setIdentity();
         float angleDeg = (float) (angle/180.0*Math.PI);
         switch (axis) {
         case 'x':
         m4Temp.rotX(angleDeg);
         break;
         case 'y':
         m4Temp.rotY(angleDeg);
         break;
         case 'z':
         m4Temp.rotZ(angleDeg);
         break;
         default:
         return;
         }
         rotMat.mul(m4Temp,rotMat);
         transformValid = false;
         */
    }

    public void setupTransform() {
        calcBounds();
        double dXMol = maxX - minX;
        double dYMol = maxY - minY;
        double xCenterMol = (maxX + minX) / 2.0;
        double yCenterMol = (maxY + minY) / 2.0;
        double dX = x2 - x1;
        double dY = y2 - y1;
        double aspect = dY / dX;
        double scaleX = dX / dXMol;
        double scaleY = dY / dYMol;
        double scale;
        scale = scaleY;
        if ((scaleY * dXMol) > dX) {
            scale = scaleX;
        }
        scale *= 0.9;
        double xCenter = (x2 + x1) / 2.0;
        double yCenter = (y2 + y1) / 2.0;
        canvasTransform.setToIdentity();
        canvasTransform.appendTranslation(xCenter, yCenter);
        canvasTransform.appendScale(scale, scale);
        canvasTransform.appendTranslation(-xCenterMol, -yCenterMol);

        transformValid = true;
    }

    private boolean getCoordSystemTransform() {
        //AffineTransform shapeTransform = new AffineTransform();
        return true;
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
        int atomNum = genSpheres(gC, true, x, y);

        return atomNum;
    }

    public boolean hitShape(double x, double y) {
        if (!bounds2D.contains(x, y)) {
            hitAtom = -1;

            return false;
        } else {
            hitAtom = pick(null, x, y);

            if (hitAtom == -1) {
                return false;
            } else {
                return true;
            }
        }
    }

    public void draw(GraphicsContextInterface gC, double[][] canvasBounds, double[][] worldBounds) {
        x1 = xPosType.transform(bx1, canvasBounds[0], worldBounds[0]);
        x2 = xPosType.transform(bx2, canvasBounds[0], worldBounds[0]);
        y1 = yPosType.transform(by1, canvasBounds[1], worldBounds[1]);
        y2 = yPosType.transform(by2, canvasBounds[1], worldBounds[1]);


        transformValid = false;
        paintShape(gC);
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
        //System.out.println("spheres " + drawSpheres + " lines " + drawLines + " labels " + drawLabels);
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
                        Logger.getLogger(CanvasMolecule.class.getName()).log(Level.SEVERE, null, ex);
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
            if ((label != null) && !label.equals("C") && !label.equals("")) {
                try {
                    gC.setStroke(Color.BLACK);
                    gC.strokeText(label, x, y);
                } catch (Exception ex) {
                    Logger.getLogger(CanvasMolecule.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        return -1;
    }

    public int genSelectionSymbols(GraphicsContextInterface gC) {

        if (!transformValid) {
            setupTransform();
        }

        int j = 0;
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
                        Logger.getLogger(CanvasMolecule.class.getName()).log(Level.SEVERE, null, ex);
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
                
                x2 = (float) molPrims.lineCoords[(i * 6) + 3];
                y2 = (float) molPrims.lineCoords[(i * 6) + 4];
                z2 = (float) molPrims.lineCoords[(i * 6) + 5];
                
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
                Logger.getLogger(CanvasMolecule.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

}
