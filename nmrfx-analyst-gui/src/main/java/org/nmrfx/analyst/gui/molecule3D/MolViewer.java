package org.nmrfx.analyst.gui.molecule3D;

import javafx.collections.ListChangeListener;
import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Bond;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.structure.chemistry.MissingCoordinatesException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.rdc.AlignmentCalc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Dub
 */
public class MolViewer extends Pane {
    private static final Logger log = LoggerFactory.getLogger(MolViewer.class);

    private double mousePosX;
    private double mousePosY;
    private double mouseOldX;
    private double mouseOldY;
    private double mouseDeltaX;
    private double mouseDeltaY;
    private boolean mouseMoved = false;
    private final double cameraDistance = 100;

    MolSceneController controller;
    private Group rootGroup;
    private SubScene subScene;
    private PerspectiveCamera camera;
    private final CameraTransformer cameraTransform = new CameraTransformer();
    Xform molGroup;
    Xform selGroup;
    Text text = null;
    double[] center = {0.0, 0.0, 0.0};
    Affine rotTransform = new Affine();
    Affine transTransform = new Affine();
    List<LabelNode> labelNodes = new ArrayList<>();
    Pane twoDPane;

    ArrayList<MolSelectionListener> selectionListeners = new ArrayList<>();

    public MolViewer(MolSceneController controller, Pane twoDPane) {
        this.controller = controller;
        this.twoDPane = twoDPane;
    }

    class LabelNode {

        final Node node;
        final Label label;

        LabelNode(Node node, Label label) {
            this.node = node;
            this.label = label;
        }
    }

    @Override
    public void layoutChildren() {
        subScene.setWidth(this.getWidth());
        subScene.setHeight(this.getHeight());
        twoDPane.setPrefSize(this.getWidth(), this.getHeight());
        super.layoutChildren();
    }

    public final SubScene initScene(double width, double height) {
        rootGroup = new Group();

        subScene = new SubScene(rootGroup, width, height, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.BLACK);
        subScene.getRoot().requestFocus();
        this.getChildren().add(subScene);
        molGroup = new Xform();
        selGroup = new Xform();

        camera = new PerspectiveCamera(true);
        cameraTransform.setTranslate(0, 0, 0);
        cameraTransform.getChildren().addAll(camera);
        camera.setNearClip(0.1);
        camera.setFarClip(10000.0);
        camera.setFieldOfView(44);
        camera.setTranslateZ(-cameraDistance);
        subScene.setCamera(camera);
        //cameraTransform.ry.setAngle(-45.0);
        //cameraTransform.rx.setAngle(-10.0);

        //add a Point Light for better viewing of the grid coordinate system
        PointLight light = new PointLight(Color.WHITE);
        light.setTranslateX(camera.getTranslateX());
        light.setTranslateY(camera.getTranslateY());
        light.setTranslateZ(camera.getTranslateZ());
        cameraTransform.getChildren().add(light);

        molGroup.setId("molGroup");
        selGroup.setId("selGroup");

        rootGroup.getChildren().addAll(molGroup);
        rootGroup.getChildren().addAll(selGroup);
        rootGroup.getChildren().add(cameraTransform);
        resetTransform();
        addHandlers(subScene, twoDPane);
        ListChangeListener listener = new ListChangeListener() {
            @Override
            public void onChanged(ListChangeListener.Change c) {
                molGroupChanged();
            }
        };
        molGroup.getChildren().addListener(listener);
        twoDPane.setMouseTransparent(true);
        try {
            drawMol();
        } catch (InvalidMoleculeException ex) {
            log.warn(ex.getMessage(), ex);
        }
        return subScene;
    }

    void addHandlers(SubScene root, Pane twoDPane) {

        //root.setFill(Color.TRANSPARENT);
        // scene.setCamera(camera);
        //First person shooter keyboard movement
        twoDPane.setOnKeyPressed(event -> {
            double change = 2.0;
            //Add shift modifier to simulate "Running Speed"
            if (event.isShiftDown()) {
                change = 10.0;
            }
            //What key did the user press?
            KeyCode keycode = event.getCode();
            //Step 2c: Add Zoom controls
            if (keycode == KeyCode.W) {
                camera.setTranslateZ(camera.getTranslateZ() + change);
            }
            if (keycode == KeyCode.S) {
                camera.setTranslateZ(camera.getTranslateZ() - change);
            }

        });
        root.setOnMouseClicked((event) -> {
            if (mouseMoved) {
                return;
            }
            PickResult res = event.getPickResult();
            //you can get a reference to the clicked node like this 
            boolean clearIt = true;
            if (res.getIntersectedNode() instanceof Node) {
                Node node = (Node) res.getIntersectedNode();
                if ((node != null) && (node.getParent() != null)) {
                    if (!(node instanceof SubScene)) {
                        Node parent = node.getParent().getParent();
                        boolean append = event.isShiftDown();
                        String name;
                        if (parent instanceof MolItem) {
                            clearIt = false;
                            MolItem molItem = (MolItem) parent;
                            Node selNode = molItem.getSelectorNode(node, res.getIntersectedPoint());
                            addSelSphere(selNode, append);
                            name = molItem.getNodeName(node, res.getIntersectedPoint());
                            String[] fields = name.split(" ");
                            Label label = new Label();
                            label.setTextFill(Color.WHITE);
                            if (fields.length > 1) {
                                label.setText(fields[1]);
                            } else {
                                label.setText(name);
                            }
                            LabelNode labelNode = new LabelNode(node, label);

                            labelNodes.add(labelNode);
                            showLabels();
                        } else {
                            name = node.getId();
                        }
                        if (!clearIt) {
                            selectionListeners.stream().forEach((listener) -> {
                                listener.processSelection(name, event);
                            });
                        }
                    }
                }
            }
            if (clearIt) {
                clearSelSpheres();
                selectionListeners.stream().forEach((listener) -> {
                    listener.processSelection("clear", event);
                });
                labelNodes.clear();
                showLabels();

            }
        });

        root.setOnMousePressed((MouseEvent me) -> {
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
            mouseOldX = me.getSceneX();
            mouseOldY = me.getSceneY();
            mouseMoved = false;
            twoDPane.requestFocus();

        });
        root.setOnMouseDragged((MouseEvent me) -> {
            double minMove = 5.0;
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
            mouseDeltaX = (mousePosX - mouseOldX);
            mouseDeltaY = (mousePosY - mouseOldY);
            if ((Math.abs(mouseDeltaX) > minMove) || (Math.abs(mouseDeltaY) > minMove)) {
                mouseMoved = true;
                mouseOldX = mousePosX;
                mouseOldY = mousePosY;
                double modifier = 10.0;
                double modifierFactor = 0.1;

                if (me.isControlDown()) {
                    modifier = 0.1;
                }
                if (me.isShiftDown()) {
                    modifier = 50.0;
                }
                if (me.isPrimaryButtonDown()) {
                    rotate3(mouseDeltaX * modifierFactor * modifier * 2.0, mouseDeltaY * modifierFactor * modifier * 2.0);
                } else if (me.isSecondaryButtonDown()) {
                    double z = camera.getTranslateZ();
                    double newZ = z + mouseDeltaX * modifierFactor * modifier;
                    camera.setTranslateZ(newZ);
                }
                showLabels();
            }
        });
        root.setOnZoom((ZoomEvent zoomEvent) -> {
            double zoom = zoomEvent.getZoomFactor();
            double z = camera.getTranslateZ();
            double newZ = z * zoom;
            camera.setTranslateZ(newZ);
        });

    }

    public void showLabels() {
        twoDPane.getChildren().clear();
        for (LabelNode labelNode : labelNodes) {
            Point3D coordinates = labelNode.node.localToScene(javafx.geometry.Point3D.ZERO, true);
            double x = coordinates.getX();
            double y = coordinates.getY();
            Point2D pt2 = twoDPane.sceneToLocal(0, 0);
            labelNode.label.getTransforms().setAll(new Translate(x + pt2.getX(), y + pt2.getY() - 8));
            twoDPane.getChildren().add(labelNode.label);
        }
    }

    public void deleteItems(String mode, String type) {
        if (mode.equals("delete")) {
            final Iterator<Node> iter = molGroup.getChildren().iterator();
            while (iter.hasNext()) {
                Node node = iter.next();
                if (node instanceof MolItem) {
                    if (type.length() == 0) {
                        iter.remove();
                    } else if (type.equalsIgnoreCase("all")) {
                        iter.remove();
                    } else if (type.equals(node.getId())) {
                        iter.remove();
                    } else {
                        String[] tags = node.getId().split(" ");
                        for (String tag : tags) {
                            if (type.equals(tag)) {
                                iter.remove();
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    public void hideItems(String mode, String type) {
        if (mode.equals("hide")) {
            for (Node node : molGroup.getChildren()) {
                boolean matched = false;
                if (node instanceof MolItem) {
                    if (type.length() == 0) {
                        matched = true;
                    } else if (type.equalsIgnoreCase("all")) {
                        matched = true;
                    } else if (type.equals(node.getId())) {
                        matched = true;
                    } else {
                        String[] tags = node.getId().split(" ");
                        for (String tag : tags) {
                            if (type.equals(tag)) {
                                matched = true;
                                break;
                            }
                        }
                    }
                }
                Group group = (Group) node;
                if (matched) {
                    group.setVisible(false);
                }

            }
        }
    }

    void drawMol() throws InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        if (!molecule.getPolymers().isEmpty()) {
            createItems("tube");
        } else {
            createItems("lines");
        }
    }

    class MolPrimitives {

        final ArrayList<Bond> bonds;
        final ArrayList<Atom> atoms;
        final ArrayList<BondLine> bondLines;
        final ArrayList<AtomSphere> atomSpheres;
        final Molecule mol;

        MolPrimitives(Molecule molecule, int iStructure) {
            this.mol = molecule;
            bonds = molecule.getBondList();
            atoms = molecule.getAtomList();
            bondLines = MolCoords.createBondLines(bonds, iStructure);
            atomSpheres = MolCoords.createAtomList(atoms, iStructure);
        }
    }

    private void createItems(String type) throws InvalidMoleculeException {
        int iStructure = 0;
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        molecule.selectAtoms("*:*.*");
        molecule.setAtomProperty(Atom.DISPLAY, true);
        molecule.selectBonds("atoms");
        molecule.setBondProperty(Bond.DISPLAY, true);
        molecule.colorAtomsByType();

        molGroup.getChildren().clear();
        if (type.equals("spheres")) {
            addSpheres(0, 0.5, "spheres");
        } else if (type.equals("lines")) {
            addLines(0, "lines");
        } else if (type.equals("cyls")) {
            addCyls(0, 0.2, 0.1, "cyls");
        } else if (type.equals("tube")) {
            addTube(0, 0.5, "tube");
        }

        try {
            center = molecule.getCenter(iStructure);
            center(-center[0], -center[1], -center[2]);
        } catch (MissingCoordinatesException ex) {
            log.error(ex.getMessage(), ex);
        }
        subScene.getRoot().requestFocus();

    }

    public void centerOnSelection() {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        int iStructure = 0;
        try {
            center = molecule.getCenterOfSelected(iStructure);
            if (center != null) {
                center(-center[0], -center[1], -center[2]);
                updateView();
            }
        } catch (MissingCoordinatesException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    public void addSpheres(int iStructure, double sphereRadius, String tag) {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        molecule.updateBondArray();
        molecule.updateAtomArray();
        MolPrimitives mP = new MolPrimitives(molecule, iStructure);
        MolSpheres spheres = new MolSpheres(mP.mol.getName(), mP.atoms, mP.atomSpheres, sphereRadius, true, tag);
        boolean empty = molGroup.getChildren().isEmpty();
        molGroup.getChildren().add(spheres);
        if (empty) {
            centerOnSelection();
        }
    }

    public void addLines(int iStructure, String tag) {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        molecule.colorAtomsByType();

        molecule.updateBondArray();
        molecule.updateAtomArray();
        MolPrimitives mP = new MolPrimitives(molecule, iStructure);
        MolCylinders cyls = new MolCylinders(mP.mol.getName(), mP.bonds, mP.bondLines, 0.0, tag);
        boolean empty = molGroup.getChildren().isEmpty();
        molGroup.getChildren().add(cyls);
        if (empty) {
            centerOnSelection();
        }
    }

    public void addCyls(int iStructure, double cylRadius, double sphereRadius, String tag) {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            return;
        }
        molecule.colorAtomsByType();

        molecule.updateBondArray();
        molecule.updateAtomArray();
        MolPrimitives mP = new MolPrimitives(molecule, iStructure);
        MolCylinders cyls = new MolCylinders(mP.mol.getName(), mP.bonds, mP.bondLines, cylRadius, tag);
        boolean empty = molGroup.getChildren().isEmpty();
        molGroup.getChildren().add(cyls);
        if (sphereRadius > 0.01) {
            MolSpheres spheres = new MolSpheres(mP.mol.getName(), mP.atoms, mP.atomSpheres, sphereRadius, false, tag);
            molGroup.getChildren().add(spheres);
        }
        if (empty) {
            centerOnSelection();
        }
    }

    /**
     * Adds a box around the molecule.
     *
     * @param iStructure int Structure number
     * @param radius     double Radius of cylinder in plot
     * @param tag        String Tag applied to every associated object
     * @throws InvalidMoleculeException
     */
    public void addBox(int iStructure, double radius, String tag) throws InvalidMoleculeException {
        Molecule mol = Molecule.getActive();
        if (mol == null) {
            return;
        }
        try {
            Vector3D[] cornerVecs = mol.getCorner(iStructure);
            double[] minCorner = cornerVecs[0].toArray();
            double[] maxCorner = cornerVecs[1].toArray();
            double[][] factors = {{1, 1, 1}, {-1, -1, -1}, {-1, 1, 1}, {1, -1, -1}, {1, -1, 1}, {-1, 1, -1}};
            double[][] begin = new double[factors.length][3];
            double[][] end = new double[factors.length][3];
            double[][] diffs = new double[factors.length][3];
            double[] v0 = new double[3];
            double[] v1 = new double[3];
            double[][] ends = new double[2][];
            int[][] ds = {{1, 2}, {0, 2}, {0, 1}};
            for (int i = 0; i < 3; i++) {
                int d1 = ds[i][0];
                int d2 = ds[i][1];
                ends[0] = minCorner.clone();
                ends[1] = maxCorner.clone();
                for (int j = 0; j < 2; j++) {
                    for (int k = 0; k < 2; k++) {
                        v0[i] = ends[0][i];
                        v1[i] = ends[1][i];
                        v0[d1] = ends[j][d1];
                        v1[d1] = ends[j][d1];
                        v0[d2] = ends[k][d2];
                        v1[d2] = ends[k][d2];
                        Color color;
                        if ((j == 0) && (k == 0)) {
                            color = Color.GREEN;
                        } else {
                            color = Color.WHITE;
                        }
                        MolCylinder cyl0 = new MolCylinder(v0, v1, radius, color, tag);
                        molGroup.getChildren().add(cyl0);

                    }
                }
            }

        } catch (MissingCoordinatesException ex) {
            log.warn(ex.getMessage(), ex);
        }
    }

    /**
     * Adds molecular axes. Can be the original axes, or axes rotated based on
     * SVD or RDC calculations.
     *
     * @param iStructure int Structure number
     * @param radius     double Radius of cylinder in plot
     * @param tag        String Tag applied to every associated object
     * @param type       String Axis type (rdc, svd, original).
     * @throws InvalidMoleculeException
     */
    public void addAxes(int iStructure, double radius, String tag, String type) throws InvalidMoleculeException {
        Molecule mol = Molecule.getActive();
        if (mol == null) {
            return;
        }
        try {
            double[] center = mol.getCenter(iStructure);
            RealVector centerVec = new ArrayRealVector(center);
            double[][] endPts = {{1.0, 0, 0}, {0, 1.0, 0}, {0, 0, 1.0}};
            Color[] colors = new Color[3];
            RealMatrix axes = new Array2DRowRealMatrix(endPts);
            RealMatrix svdAxes = mol.calcSVDAxes(endPts);
            double scale = 10.0;

            if (type.equals("rdc")) {
                axes = mol.getRDCAxes(endPts);
                if (axes == null) {
                    return;
                }
                colors[0] = Color.CORAL;
                colors[1] = Color.LIGHTGREEN;
                colors[2] = Color.LIGHTBLUE;
                scale = 15.0;

            } else if (type.equals("svd")) {
                axes = svdAxes;
                scale = 2.0;
                colors[0] = Color.MAGENTA;
                colors[1] = Color.SEAGREEN;
                colors[2] = Color.CYAN;
            } else {
                colors[0] = Color.RED;
                colors[1] = Color.GREEN;
                colors[2] = Color.BLUE;
            }

            RealVector originVec = new ArrayRealVector(centerVec);

            for (int i = 0; i < 3; i++) {
                originVec = originVec.subtract(svdAxes.getRowVector(i));
            }
            for (int i = 0; i < 3; i++) {
                RealVector endVector = new ArrayRealVector(originVec);
                endVector = endVector.add(axes.getRowVector(i).mapMultiply(scale));
                MolCylinder cyl = new MolCylinder(originVec.toArray(),
                        endVector.toArray(), radius, colors[i], tag);
                molGroup.getChildren().add(cyl);
            }

        } catch (MissingCoordinatesException ex) {
            log.warn(ex.getMessage(), ex);
        }
    }

    public void rotateSVDRDC(String type) {
        Molecule mol = Molecule.getActive();
        if (mol == null) {
            return;
        }
        RealMatrix rotMat;
        if (type.equals("rdc")) {
            rotMat = mol.getRDCRotationMatrix(false);
        } else if (type.equals("svd")) {
            rotMat = mol.getSVDRotationMatrix(false);
        } else {
            return;
        }
        double[][] rotMatData = rotMat.getData();

        double mxx = rotMatData[0][0];
        double mxy = rotMatData[0][1];
        double mxz = rotMatData[0][2];
        double myx = -rotMatData[1][0];
        double myy = -rotMatData[1][1];
        double myz = -rotMatData[1][2];
        double mzx = -rotMatData[2][0];
        double mzy = -rotMatData[2][1];
        double mzz = -rotMatData[2][2];
        rotTransform.setToTransform(mxx, mxy, mxz, 0.0, myx, myy, myz, 0.0, mzx, mzy, mzz, 0.0);
        updateView();
    }

    public void addTube(int iStructure, double sphereRadius, String tag) throws InvalidMoleculeException {
        Molecule mol = Molecule.getActive();
        if (mol == null) {
            return;
        }
        for (Polymer polymer : mol.getPolymers()) {
            mol.selectAtoms("*:*.*");
            mol.setAtomProperty(Atom.DISPLAY, false);
            if (polymer.isRNA() || polymer.isDNA()) {
                mol.selectAtoms(polymer.getName() + ":*.P,OP1");
            } else {
                mol.selectAtoms(polymer.getName() + ":*.CA,C");
            }
            mol.setAtomProperty(Atom.DISPLAY, true);
            mol.updateAtomArray();
            MolPrimitives mP = new MolPrimitives(mol, iStructure);

            MolTube tube = new MolTube(mol.getName(), mP.atoms, mP.atomSpheres, sphereRadius, tag);
            molGroup.getChildren().add(tube);
        }

    }

    public void addOrientationSphere(int iStructure, int n, double sphereRadius, int orient, String tag) throws InvalidMoleculeException {
        Molecule mol = Molecule.getActive();
        if (mol == null) {
            return;
        }
        AlignmentCalc aCalc = new AlignmentCalc(mol, true, 2.0);
        aCalc.center();
        Vector3D center = aCalc.getCenter();
        double radius = aCalc.getRadius();
        aCalc = new AlignmentCalc();
        aCalc.genAngles(n, 18, 1.0);
        Vector3D initialVec;
        Color color;
        if (orient == 0) {
            initialVec = new Vector3D(1.0, 0.0, 0.0);
            color = Color.RED;
        } else if (orient == 1) {
            initialVec = new Vector3D(0.0, 1.0, 0.0);
            color = Color.GREEN;
        } else {
            initialVec = new Vector3D(0.0, 0.0, 1.0);
            color = Color.BLUE;
        }
        aCalc.genVectors(initialVec);
        List<Vector3D> vecs = aCalc.getVectors();
        Spheres spheres = new Spheres(tag, vecs, color, 0.3, center, radius + 3.0, tag);
        molGroup.getChildren().add(spheres);
    }

    public void addOrientationCyls(int iStructure, int n, double sphereRadius, int orient, String tag) throws InvalidMoleculeException {
        Molecule mol = Molecule.getActive();
        if (mol == null) {
            return;
        }
        AlignmentCalc aCalc = new AlignmentCalc();
        aCalc.makeCylinder(100, 10.0, 15.0, 100.0);
        aCalc.center();
        Vector3D center = aCalc.getCenter();
        double radius = aCalc.getRadius();
        Vector3D initialVec;
        Color color = Color.RED;
        List<Vector3D> vecs = aCalc.getVectors();
        Spheres spheres = new Spheres(tag, vecs, color, 0.3, center, 1.1, tag);
        molGroup.getChildren().add(spheres);
    }

    public void createItems(String mode, String[] args, ArrayList<Bond> bonds,
                            List<BondLine> bondLines, List<Atom> atoms, List<AtomSphere> atomSpheres) {
        String type = "";
        String text = "";
        double sphereRadius = 0.3;
        double cylRadius = 0.2;
        double[] begin = new double[3];
        double[] end = new double[3];
        String tag = Molecule.getActive().getName();
        Color color = Color.GREEN;
        String molName = Molecule.getActive().getName();
        if (type.equals("molspheres")) {
            MolSpheres spheres = new MolSpheres(molName, atoms, atomSpheres, sphereRadius, true, tag);
            molGroup.getChildren().add(spheres);
        } else if (type.equals("molcyls")) {
            MolCylinders cyls = new MolCylinders(molName, bonds, bondLines, cylRadius, tag);
            molGroup.getChildren().add(cyls);
            if (sphereRadius > 0.01) {
                MolSpheres spheres = new MolSpheres(molName, atoms, atomSpheres, sphereRadius, false, tag);
                molGroup.getChildren().add(spheres);
            }
        } else if (type.equals("molcyl")) {
            MolCylinder cyl = new MolCylinder(begin, end, cylRadius, color, tag);
            molGroup.getChildren().add(cyl);
        } else if (type.equals("molsphere")) {
            MolSphere sphere = new MolSphere(begin, sphereRadius, color, tag);
            molGroup.getChildren().add(sphere);
        } else if (type.equals("moltube")) {
            MolTube tube = new MolTube(molName, atoms, atomSpheres, sphereRadius, tag);
            molGroup.getChildren().add(tube);
        } else if (type.equals("text")) {
            MolText molText = new MolText(begin, text, color, tag);
            molGroup.getChildren().add(molText);
        } else {
            throw new IllegalArgumentException("invalid type " + type);
        }
    }

    public void refresh() {
        for (var obj:molGroup.getChildren()) {
            if (obj instanceof MolItem molItem) {
                molItem.refresh();
            }
        }
    }

    public void addSelectionListener(MolSelectionListener listener) {
        selectionListeners.add(listener);
    }

    double[] parseCoords(String arg) {
        String[] coordStrings = arg.split(" ");
        if (coordStrings.length != 3) {
            throw new IllegalArgumentException("must specify three coordinates");
        }
        double[] coords = new double[3];
        int j = 0;
        for (String coordString : coordStrings) {
            try {
                coords[j++] = Double.parseDouble(coordString);
            } catch (NumberFormatException nfE) {
                throw new IllegalArgumentException(nfE.getMessage());
            }
        }
        return coords;
    }

    public void center(double x, double y, double z) {
        molGroup.setTranslateX(x);
        molGroup.setTranslateY(y);
        molGroup.setTranslateZ(z);
        selGroup.setTranslateX(x);
        selGroup.setTranslateY(y);
        selGroup.setTranslateZ(z);
    }

    public void resetTransform() {
        rotTransform.setToIdentity();
        rotTransform.appendRotation(180.0, 0.0, 0.0, 0.0, new Point3D(1.0, 0.0, 0.0));
        updateView();
        camera.setTranslateZ(-cameraDistance);
        updateView();
    }

    private void rotate3(double dx, double dy) {
        Affine affine = new Affine();
        double delta = Math.sqrt(dx * dx + dy * dy) / 4.0;
        if (false) {
            affine.appendRotation(delta, center[0], center[1], center[2], dy, -dx, 0.0);
            List<Transform> transforms = molGroup.getTransforms();
            Transform newTransform = affine.createConcatenation(transforms.get(0));
            molGroup.getTransforms().setAll(newTransform);
            selGroup.getTransforms().setAll(newTransform);
        }
        rotTransform.prependRotation(delta, 0.0, 0.0, 0.0, dy, -dx, 0.0);
        updateView();
    }

    void updateView() {
        Affine affine2 = new Affine();
        affine2.appendTranslation(center[0], center[1], center[2]);
        affine2.append(rotTransform);
        affine2.appendTranslation(-center[0], -center[1], -center[2]);
        molGroup.getTransforms().setAll(affine2);
        selGroup.getTransforms().setAll(affine2);
    }

    void clearSelSpheres() {
        selGroup.getChildren().clear();
    }

    void addSelSphere(Node node, boolean append) {
        if (!append) {
            clearSelSpheres();
        }
        if (node != null) {
            selGroup.getChildren().add(node);
        }
    }

    void drawText(String textLabel, Node node) {

        Transform cameraTransform = camera.getLocalToSceneTransform();
        Transform nodeTransform = node.getLocalToSceneTransform();

        double cX = nodeTransform.getTx();
        double cY = nodeTransform.getTy();
        double cZ = nodeTransform.getTz();

        Point3D camPos = new Point3D(cameraTransform.getTx(), cameraTransform.getTy(), cameraTransform.getTz());
        Point3D nodePos = new Point3D(cX, cY, cZ);

        Vector3D delta = new Vector3D(
                (nodePos.getX()) - camPos.getX(),
                (nodePos.getY()) - camPos.getY(),
                (nodePos.getZ()) - camPos.getZ());
        text.setText(textLabel);
        text.setTranslateX(delta.getX());
        text.setTranslateY(delta.getY());
    }

    void molGroupChanged() {
        Set<String> items = new LinkedHashSet<>();
        items.add("all");
        for (Node node : molGroup.getChildren()) {
            if (node instanceof MolItem) {
                items.add(node.getId());
            }
        }
        controller.updateRemoveMenu(items);
    }
}
