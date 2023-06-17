package org.nmrfx.analyst.gui.molecule3D;

import javafx.scene.paint.Color;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Bond;
import org.nmrfx.chemistry.Point3;
import org.nmrfx.chemistry.SpatialSet;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.ArrayList;

/**
 * @author brucejohnson
 */
public class MolCoords {
    private static final float BOND_SPACE = 12.0f;

    public static ArrayList<AtomSphere> createAtomList(ArrayList<Atom> atoms, int iStructure) {
        ArrayList<AtomSphere> atomList = new ArrayList<>();
        int iAtom = 0;
        for (Atom atom : atoms) {
            atom.unsetProperty(Atom.LABEL);
            if (!atom.isCoarse() && atom.getProperty(Atom.DISPLAY)) {
                Point3 pt = atom.getPoint(iStructure);

                if (pt != null) {
                    Color color = Color.color(atom.getRed(), atom.getGreen(), atom.getBlue());
                    atomList.add(new AtomSphere(iAtom, pt, color, atom.radius, atom.value));
                    atom.setProperty(Atom.LABEL);
                }
            }
            iAtom++;
        }

        return atomList;
    }

    public static int createLabelArray(ArrayList<Atom> atoms, int iStructure, float[] coords, int i) {
        Point3 pt;
        for (Atom atom : atoms) {
            if (!atom.isCoarse() && atom.getProperty(Atom.LABEL)) {
                pt = atom.getPoint(iStructure);

                if (pt != null) {
                    coords[i++] = (float) pt.getX();
                    coords[i++] = (float) pt.getY();
                    coords[i++] = (float) pt.getZ();
                }
            }
        }

        return i;
    }

    public static int createSelectionArray(Molecule molecule, ArrayList<Atom> atoms, int iStructure, float[] coords, int[] levels) {
        int i;
        int j;

        int n = molecule.globalSelected.size();
        j = 0;
        i = 0;

        for (int k = 0; k < n; k++) {
            SpatialSet spatialSet1 = molecule.globalSelected.get(k);

            int selected = spatialSet1.getSelected();

            if (selected > 0) {
                Point3 ptB = spatialSet1.getPoint(iStructure);
                SpatialSet spatialSet2 = null;
                if (ptB != null) {
                    if ((k + 1) < n) {
                        spatialSet2 = molecule.globalSelected.get(k
                                + 1);
                    }

                    if ((spatialSet1 == spatialSet2)
                            || (Molecule.selCycleCount == 0) || ((k + 1) >= n)
                            || ((Molecule.selCycleCount != 1)
                            && (((k + 1) % Molecule.selCycleCount) == 0))) {
                        coords[i++] = (float) ptB.getX();
                        coords[i++] = (float) ptB.getY();
                        coords[i++] = (float) ptB.getZ();
                        coords[i++] = (float) ptB.getX() + 0.2f;
                        coords[i++] = (float) ptB.getY() - 0.2f;
                        coords[i++] = (float) ptB.getZ();
                        coords[i++] = (float) ptB.getX() - 0.2f;
                        coords[i++] = (float) ptB.getY() - 0.2f;
                        coords[i++] = (float) ptB.getZ();
                        coords[i++] = (float) ptB.getX();
                        coords[i++] = (float) ptB.getY();
                        coords[i++] = (float) ptB.getZ();
                        coords[i++] = (float) ptB.getX() + 0.2f;
                        coords[i++] = (float) ptB.getY() - 0.2f;
                        coords[i++] = (float) ptB.getZ();
                        coords[i++] = (float) ptB.getX() - 0.2f;
                        coords[i++] = (float) ptB.getY() - 0.2f;
                        coords[i++] = (float) ptB.getZ();
                        levels[j++] = selected;
                    } else {
                        Point3 ptE = null;
                        if (spatialSet2 != null) {
                            ptE = spatialSet2.getPoint(iStructure);
                        }

                        if (ptE != null) {
                            float dx = (float) (ptE.getX() - ptB.getX());
                            float dy = (float) (ptE.getY() - ptB.getY());
                            float dz = (float) (ptE.getZ() - ptB.getZ());
                            float len = (float) Math.sqrt((dx * dx)
                                    + (dy * dy) + (dz * dz));
                            float xy3 = -dy / len * 0.2f;
                            float yx3 = dx / len * 0.2f;
                            float z3 = dz / len * 0.2f;
                            float xz3 = -dz / len * 0.2f;
                            float y3 = dy / len * 0.2f;
                            float zx3 = dx / len * 0.2f;
                            coords[i++] = (float) (ptB.getX() - xy3);
                            coords[i++] = (float) (ptB.getY() - yx3);
                            coords[i++] = (float) (ptB.getZ() - z3);
                            coords[i++] = (float) (ptB.getX() + xy3);
                            coords[i++] = (float) (ptB.getY() + yx3);
                            coords[i++] = (float) (ptB.getZ() + z3);
                            coords[i++] = (float) ptB.getX() + (dx / len * 0.5f);
                            coords[i++] = (float) ptB.getY() + (dy / len * 0.5f);
                            coords[i++] = (float) ptB.getZ() + (dz / len * 0.5f);
                            coords[i++] = (float) (ptB.getX() + xz3);
                            coords[i++] = (float) (ptB.getY() + y3);
                            coords[i++] = (float) (ptB.getZ() + zx3);
                            coords[i++] = (float) (ptB.getX() - xz3);
                            coords[i++] = (float) (ptB.getY() - y3);
                            coords[i++] = (float) (ptB.getZ() - zx3);
                            coords[i++] = (float) ptB.getX() + (dx / len * 0.5f);
                            coords[i++] = (float) ptB.getY() + (dy / len * 0.5f);
                            coords[i++] = (float) ptB.getZ() + (dz / len * 0.5f);
                            levels[j++] = selected;
                        }
                    }
                }
            }
        }

        return i;
    }

    public static int getSphereCount(ArrayList<Atom> atoms, int iStructure) {
        int i = 0;
        Point3 pt;
        for (Atom atom : atoms) {
            if (!atom.isCoarse() && atom.getProperty(Atom.DISPLAY)) {
                pt = atom.getPoint(iStructure);

                if (pt != null) {
                    i++;
                }
            }
        }
        return i;
    }

    public static int getLabelCount(ArrayList<Atom> atoms, int iStructure) {
        int i = 0;
        Point3 pt;
        for (Atom atom : atoms) {
            if (!atom.isCoarse() && atom.getProperty(Atom.LABEL)) {
                pt = atom.getPoint(iStructure);

                if (pt != null) {
                    i++;
                }
            }
        }

        return i;
    }

    public static int getLineCount(ArrayList<Bond> bonds, int iStructure) {
        int i = 0;
        Atom atomB;
        Atom atomE;
        Point3 ptB;
        Point3 ptE;
        for (Bond bond : bonds) {
            if (bond.getProperty(Bond.DISPLAY)) {
                atomB = bond.begin;
                atomE = bond.end;

                if ((atomB != null) && (atomE != null) && !atomB.isCoarse() && !atomE.isCoarse()) {
                    ptB = atomB.getPoint(iStructure);
                    ptE = atomE.getPoint(iStructure);

                    if ((ptB != null) && (ptE != null)) {
                        if (((bond.stereo == Bond.STEREO_BOND_UP)
                                && (atomE.nonHydrogens < atomB.nonHydrogens))
                                || ((bond.stereo == Bond.STEREO_BOND_DOWN)
                                && (atomE.nonHydrogens > atomB.nonHydrogens))) {
                            i += 3;
                        } else if (((bond.stereo == Bond.STEREO_BOND_DOWN)
                                && (atomE.nonHydrogens < atomB.nonHydrogens))
                                || ((bond.stereo == Bond.STEREO_BOND_UP)
                                && (atomE.nonHydrogens > atomB.nonHydrogens))) {
                            i += 5;
                        } else if (bond.getOrder().getOrderNum() < 4) {
                            i += bond.getOrder().getOrderNum();
                        } else if (bond.getOrder().getOrderNum() == 8) {
                            i += 2;
                        } else {
                            i++;
                        }
                    }
                }
            }
        }

        return i;
    }

    public static ArrayList<BondLine> createBondLines(ArrayList<Bond> bonds, int iStructure) {
        Point3 ptB;
        Point3 ptE;
        ArrayList<BondLine> bondList = new ArrayList<>();
        int iBond = 0;
        for (Bond bond : bonds) {
            if (bond.getProperty(Bond.DISPLAY)) {
                Atom atomB = bond.begin;
                Atom atomE = bond.end;

                if ((atomB != null) && (atomE != null) && !atomB.isCoarse() && !atomE.isCoarse()) {

                    ptB = atomB.getPoint(iStructure);
                    ptE = atomE.getPoint(iStructure);

                    if ((ptB != null) && (ptE != null)) {

                        double dx = ptE.getX() - ptB.getX();
                        double dy = ptE.getY() - ptB.getY();
                        double dz = ptE.getZ() - ptB.getZ();
                        double x3 = -dy / BOND_SPACE;
                        double y3 = dx / BOND_SPACE;
                        double z3 = dz / BOND_SPACE;
                        Point3 deltaP = new Point3(x3, y3, z3);
                        Color colorB = Color.color(atomB.getRed(), atomB.getGreen(), atomB.getBlue());
                        Color colorE = Color.color(atomE.getRed(), atomE.getGreen(), atomE.getBlue());

                        if (((bond.stereo == Bond.STEREO_BOND_UP)
                                && (atomE.nonHydrogens < atomB.nonHydrogens))
                                || ((bond.stereo == Bond.STEREO_BOND_DOWN)
                                && (atomE.nonHydrogens > atomB.nonHydrogens))) {
                            // fixme make into cone for up
                            bondList.add(new BondLine(iBond, ptB, ptE, colorB, colorE));

                        } else if (((bond.stereo == Bond.STEREO_BOND_DOWN)
                                && (atomE.nonHydrogens < atomB.nonHydrogens))
                                || ((bond.stereo == Bond.STEREO_BOND_UP)
                                && (atomE.nonHydrogens > atomB.nonHydrogens))) {
                            // fixme make into cone for down
                            bondList.add(new BondLine(iBond, ptB, ptE, colorB, colorE));
                        } else {
                            int orderNum = bond.getOrder().getOrderNum();
                            if ((orderNum == 1) || (orderNum == 3)
                                    || (orderNum == 7)
                                    || (orderNum == 9)) {
                                atomB.setProperty(Atom.LABEL);
                                atomE.setProperty(Atom.LABEL);
                                bondList.add(new BondLine(iBond, ptB, ptE, colorB, colorE));
                            }

                            if ((orderNum == 2) || (orderNum == 3)
                                    || (orderNum == 8)) {
                                bondList.add(new BondLine(iBond, ptB.add(deltaP), ptE.add(deltaP), colorB, colorE));
                                bondList.add(new BondLine(iBond, ptB.subtract(deltaP), ptE.subtract(deltaP), colorB, colorE));
                            }
                        }
                    }
                }

            }
            iBond++;

        }

        return bondList;
    }

}
