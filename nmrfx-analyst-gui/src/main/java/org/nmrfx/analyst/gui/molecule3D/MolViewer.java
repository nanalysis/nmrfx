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
import org.nmrfx.chemistry.*;
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
    enum RenderType {
        TUBE,
        LINES,
        CYLS,
        STICKS,
        SPHERES,
        CONSTRAINTS;
        final String name;
        RenderType() {
            name = this.name().toLowerCase();
        }
    }
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
    private SubScene subScene;
    private PerspectiveCamera camera;
    private final CameraTransformer cameraTransform = new CameraTransformer();
    Xform molGroup;
    Xform selGroup;
    Text text = null;
    double[] center = {0.0, 0.0, 0.0};
    Affine rotTransform = new Affine();
    List<LabelNode> labelNodes = new ArrayList<>();
    Pane twoDPane;

    Molecule currentMolecule = null;
    double cornerDistance = 100.0;
    double defaultScale = 1.0;

    ArrayList<MolSelectionListener> selectionListeners = new ArrayList<>();

    public MolViewer(MolSceneController controller, Pane twoDPane) {
        this.controller = controller;
        this.twoDPane = twoDPane;
    }

    static class LabelNode {

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
        Group rootGroup = new Group();

        subScene = new SubScene(rootGroup, width, height, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.WHITE);
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
        ListChangeListener listener = c -> molGroupChanged();
        molGroup.getChildren().addListener(listener);
        twoDPane.setMouseTransparent(true);
        try {
            drawMol();
        } catch (InvalidMoleculeException | MissingCoordinatesException ex) {
            log.warn(ex.getMessage(), ex);
        }
        return subScene;
    }

    void addHandlers(SubScene root, Pane twoDPane) {

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
                setCameraZ(camera.getTranslateZ() + change);
            }
            if (keycode == KeyCode.S) {
                setCameraZ(camera.getTranslateZ() - change);
            }

        });
        root.setOnMouseClicked(event -> {
            if (mouseMoved) {
                return;
            }
            PickResult res = event.getPickResult();
            //you can get a reference to the clicked node like this 
            boolean clearIt = true;
            Node node = res.getIntersectedNode();
            if ((node != null) && (node.getParent() != null) && !(node instanceof SubScene)) {
                Node parent = node.getParent().getParent();
                boolean append = event.isShiftDown();
                String name;
                if (parent instanceof MolItem molItem) {
                    clearIt = false;
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
                    selectionListeners.forEach(listener -> listener.processSelection(name, event));
                }
            }

            if (clearIt) {
                clearSelSpheres();
                selectionListeners.forEach(listener -> listener.processSelection("clear", event));
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
                    setCameraZ(newZ);
                }
                showLabels();
            }
        });
        root.setOnZoom((ZoomEvent zoomEvent) -> {
            double zoom = zoomEvent.getZoomFactor();
            double z = camera.getTranslateZ();
            double newZ = z * zoom;
            setCameraZ(newZ);
        });

    }

    Molecule getCurrentMolecule() {
        if (currentMolecule != Molecule.getActive()) {
            currentMolecule = Molecule.getActive();
            if (currentMolecule != null) {
                try {
                    int iStructure = controller.getFirstStructure();
                    var vec3Ds = currentMolecule.getCorner(iStructure);
                    cornerDistance = vec3Ds[0].distance(vec3Ds[1]);
                } catch (MissingCoordinatesException mE) {
                    cornerDistance = 100.0;
                }
            }
        }
        return currentMolecule;
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

    public void clearAll() {
        molGroup.getChildren().clear();
    }

    public void deleteItems(String mode, String type) {
        if (mode.equals("delete")) {
            final Iterator<Node> iter = molGroup.getChildren().iterator();
            while (iter.hasNext()) {
                Node node = iter.next();
                if (node instanceof MolItem) {
                    if (type.isEmpty()) {
                        iter.remove();
                    } else if (type.equalsIgnoreCase("all")) {
                        iter.remove();
                        controller.clearModes();
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
                    if (type.isEmpty()) {
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

    void drawMol() throws InvalidMoleculeException, MissingCoordinatesException {
        Molecule molecule = getCurrentMolecule();
        if (molecule == null) {
            return;
        }
        setCameraZ(-defaultScale * cornerDistance);
        if (!molecule.getPolymers().isEmpty()) {
            createItems(RenderType.TUBE);
        } else {
            createItems(RenderType.LINES);
        }
        resetTransform();
    }

    static class MolPrimitives {

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

    private void createItems(RenderType renderType) throws InvalidMoleculeException {
        int iStructure = controller.getFirstStructure();
        Molecule molecule = getCurrentMolecule();
        if (molecule == null) {
            return;
        }
        molecule.selectAtoms("*:*.*");
        molecule.setAtomProperty(Atom.DISPLAY, true);
        molecule.selectBonds("atoms");
        molecule.setBondProperty(Bond.DISPLAY, true);
        molecule.colorAtomsByType();

        molGroup.getChildren().clear();
        switch (renderType) {
            case SPHERES -> addSpheres(List.of(iStructure), 0.5, renderType.name, 0);
            case LINES -> addLines(iStructure, "lines");
            case CYLS -> addCyls(List.of(iStructure), 0.2, 0.1, renderType.name, 0);
            case TUBE -> addTube(List.of(iStructure), 0.5, renderType.name, 0);
            case CONSTRAINTS -> addConstraintLines(controller.getFirstStructure(),  renderType.name);
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
        Molecule molecule = getCurrentMolecule();
        if (molecule == null) {
            return;
        }
        int iStructure = controller.getFirstStructure();
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

    public void addSpheres(List<Integer> structures, double sphereRadius, String tag, int index) {
        Molecule molecule = getCurrentMolecule();
        if (molecule == null) {
            return;
        }
        molecule.updateBondArray();
        molecule.updateAtomArray();
        List<MolSpheres> allSpheres = new ArrayList<>();
        for (int iStructure : structures) {
            MolPrimitives mP = new MolPrimitives(molecule, iStructure);
            MolSpheres spheres = new MolSpheres(mP.mol.getName(), mP.atoms, mP.atomSpheres, sphereRadius, true, tag + " " + index++);
            allSpheres.add(spheres);
        }
        boolean empty = molGroup.getChildren().isEmpty();
        molGroup.getChildren().addAll(allSpheres);
        if (empty) {
            centerOnSelection();
        }
    }

    public void addLines(int iStructure, String tag) {
        addLines(List.of(iStructure), tag, 0);
    }

    public void addLines(List<Integer> structures, String tag, int index) {
        Molecule molecule = getCurrentMolecule();
        if (molecule == null) {
            return;
        }
        molecule.colorAtomsByType();

        molecule.updateBondArray();
        molecule.updateAtomArray();
        List<MolCylinders> allCyls = new ArrayList<>();
        for (Integer iStructure : structures) {
            MolPrimitives mP = new MolPrimitives(molecule, iStructure);
            MolCylinders cyls = new MolCylinders(mP.mol.getName(), mP.bonds, mP.bondLines, 0.0, tag + " " + index++);
            allCyls.add(cyls);
        }
        boolean empty = molGroup.getChildren().isEmpty();
        molGroup.getChildren().addAll(allCyls);
        if (empty) {
            centerOnSelection();
        }
    }

    public void drawAtomTree() {
        Molecule molecule = getCurrentMolecule();
        if (molecule == null) {
            return;
        }
        List<List<Atom>> atomTree = molecule.getAtomTree();
        Color color1 = Color.RED;
        Color color2 = Color.YELLOW;
        int iBranch = 0;
        for (List<Atom> branch : atomTree) {
            Atom startAtom = branch.get(2);
            if (startAtom == null) {
                continue;
            }
            var v1 = startAtom.getPoint();
            for (int i = 3; i < branch.size(); i++) {
                Atom endAtom = branch.get(i);
                var v2 = endAtom.getPoint();
                boolean rotatable = endAtom.rotActive && endAtom.irpIndex > 0;
                Color color;
                if (rotatable) {
                    color = color1.interpolate(color2, (double) iBranch / atomTree.size());
                } else {
                    color = Color.BLUE;
                }
                MolCylinder cyl0 = new MolCylinder(v1.toArray(), v2.toArray(), 0.1, color, "tree");
                molGroup.getChildren().add(cyl0);
            }
            iBranch++;
        }
    }

    public void addConstraintLines(int iStructure, String tag) {
        Molecule molecule = getCurrentMolecule();
        if (molecule == null) {
            return;
        }
        var molecularConstraints = molecule.getMolecularConstraints();
        for (var disSet : molecularConstraints.noeSets.values()) {
            for (var noe : disSet.getConstraints()) {
                if (!noe.isBond()) {
                    for (var atomPair : noe.getAtomPairs()) {
                        var center1 = atomPair.getCenter1();
                        var center2 = atomPair.getCenter2();
                        double upper = noe.getUpper();
                        double distance = atomPair.getDistanceToCenters();
                        if (distance > (upper + 0.1)) {
                            MolCylinder cyl0 = new MolCylinder(center1.toArray(), center2.toArray(), 0.05, Color.RED, tag);
                            molGroup.getChildren().add(cyl0);
                            var centerVec = center1.add(center2).scalarMultiply(0.5);
                            var delVec = center2.subtract(center1);
                            var normVec = delVec.normalize();
                            var v1 = centerVec.add(normVec.scalarMultiply(upper / 2.0));
                            var v2 = centerVec.subtract(normVec.scalarMultiply(upper / 2.0));
                            MolCylinder cyl1 = new MolCylinder(v1.toArray(), v2.toArray(), 0.1, Color.LIGHTGREEN, tag);
                            molGroup.getChildren().add(cyl1);
                        } else {
                            MolCylinder cyl0 = new MolCylinder(center1.toArray(), center2.toArray(), 0.05, Color.LIGHTGREEN, tag);
                            molGroup.getChildren().add(cyl0);
                        }
                    }
                }
            }
        }
    }

    public void addCyls(List<Integer> structures, double cylRadius, double sphereRadius, String tag, int index) {
        Molecule molecule = getCurrentMolecule();
        if (molecule == null) {
            return;
        }
        molecule.colorAtomsByType();

        molecule.updateBondArray();
        molecule.updateAtomArray();
        List<MolCylinders> allCyls = new ArrayList<>();
        List<MolSpheres> allSpheres = new ArrayList<>();
        for (int iStructure : structures) {
            MolPrimitives mP = new MolPrimitives(molecule, iStructure);
            MolCylinders cyls = new MolCylinders(mP.mol.getName(), mP.bonds, mP.bondLines, cylRadius, tag + " " + index);
            allCyls.add(cyls);
            if (sphereRadius > 0.01) {
                MolSpheres spheres = new MolSpheres(mP.mol.getName(), mP.atoms, mP.atomSpheres, sphereRadius, false, tag + " " + index);
                allSpheres.add(spheres);
            }
            index++;
        }
        boolean empty = molGroup.getChildren().isEmpty();
        molGroup.getChildren().addAll(allCyls);
        molGroup.getChildren().addAll(allSpheres);
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
     */
    public void addBox(int iStructure, double radius, String tag) {
        Molecule mol = getCurrentMolecule();
        if (mol == null) {
            return;
        }
        try {
            Vector3D[] cornerVecs = mol.getCorner(iStructure);
            double[] minCorner = cornerVecs[0].toArray();
            double[] maxCorner = cornerVecs[1].toArray();
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
     */
    public void addAxes(int iStructure, double radius, String tag, String type) {
        Molecule mol = getCurrentMolecule();
        if (mol == null) {
            return;
        }
        try {
            double[] center = mol.getCenter(iStructure);
            RealVector centerVec = new ArrayRealVector(center);
            double[][] endPts = {{1.0, 0, 0}, {0, 1.0, 0}, {0, 0, 1.0}};
            Color[] colors = new Color[3];
            RealMatrix axes = new Array2DRowRealMatrix(endPts);
            RealMatrix svdAxes = mol.calcSVDAxes(iStructure, endPts);
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
        int iStructure = controller.getFirstStructure();
        Molecule mol = getCurrentMolecule();
        if (mol == null) {
            return;
        }
        RealMatrix rotMat;
        if (type.equals("rdc")) {
            rotMat = mol.getRDCRotationMatrix(false);
        } else if (type.equals("svd")) {
            rotMat = mol.getSVDRotationMatrix(iStructure, false);
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

    public void addTube(List<Integer> structures, double sphereRadius, String tag, int index) throws InvalidMoleculeException {
        Molecule mol = getCurrentMolecule();
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
            List<MolTube> allTubes = new ArrayList<>();
            for (int iStructure : structures) {
                MolPrimitives mP = new MolPrimitives(mol, iStructure);
                MolTube tube = new MolTube(mol.getName(), mP.atoms, mP.atomSpheres, sphereRadius, tag + " " + index++);
                allTubes.add(tube);
            }
            molGroup.getChildren().addAll(allTubes);
        }

    }

    public void addNucleicAcidBases(List<Integer> structures, double radius, String tag, int index) throws InvalidMoleculeException {
        Molecule mol = getCurrentMolecule();
        if (mol == null) {
            return;
        }
        Map<String, String> endAtomMap = Map.of("G", "N1", "C", "N3", "A", "N1", "U", "N3", "T", "N3");
        for (Polymer polymer : mol.getPolymers()) {
            mol.updateAtomArray();
            for (int iStructure : structures) {
                if (polymer.isRNA() || polymer.isDNA()) {
                    for (Residue residue : polymer.getResidues()) {
                        Atom pAtom = residue.getAtom("P");
                        Atom endAtom = residue.getAtom(endAtomMap.get(residue.getName()));
                        if ((pAtom != null) && (endAtom != null)) {
                            MolCylinder cyl = new MolCylinder(pAtom.getPoint(iStructure).toArray(),
                                    endAtom.getPoint(iStructure).toArray(), radius, Color.BLUE, tag);
                            molGroup.getChildren().add(cyl);
                        }
                    }
                }
            }
        }

    }

    public void addOrientationSphere(int iStructure, int n, double sphereRadius, int orient, String tag) {
        Molecule mol = getCurrentMolecule();
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

    public void addOrientationCyls(int iStructure, int n, double sphereRadius, int orient, String tag) {
        Molecule mol = getCurrentMolecule();
        if (mol == null) {
            return;
        }
        AlignmentCalc aCalc = new AlignmentCalc();
        aCalc.makeCylinder(100, 10.0, 15.0, 100.0);
        aCalc.center();
        Vector3D center = aCalc.getCenter();
        Color color = Color.RED;
        List<Vector3D> vecs = aCalc.getVectors();
        Spheres spheres = new Spheres(tag, vecs, color, 0.3, center, 1.1, tag);
        molGroup.getChildren().add(spheres);
    }

    public void refresh() {
        for (var obj : molGroup.getChildren()) {
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
        Molecule molecule = getCurrentMolecule();
        if (molecule != null) {
            setCameraZ(-defaultScale * cornerDistance);
        } else {
            camera.setTranslateZ(-cameraDistance);
        }
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

    void setCameraZ(double z) {
        z = Math.min(-10, z);
        z = Math.max(-cornerDistance * 4, z);
        camera.setTranslateZ(z);
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
