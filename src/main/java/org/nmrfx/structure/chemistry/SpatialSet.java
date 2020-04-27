/*
 * NMRFx Structure : A Program for Calculating Structures 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

 /*
 * SpatialSet.java
 *
 * Created on October 3, 2003, 8:13 PM
 */
package org.nmrfx.structure.chemistry;

import java.util.*;

/**
 *
 * @author Johnbruc
 */
public class SpatialSet {

    public Atom atom = null;
    public String altPos = null;
    private Point3 pt = null;
    public ArrayList<Point3> points;
    public Vector ppms;
    public List<PPMv> refPPMVs = null;
    public boolean[] properties;
    public float occupancy = 1.0f;
    public float bfactor = 1.0f;
    public float order = 1.0f;
    public int selected = 0;
    public int labelStatus = 0;
    public int displayStatus = 0;
    public float red = 1.0f;
    public float green = 0.0f;
    public float blue = 0.0f;
    public Vector coords = new Vector();

    public SpatialSet(Atom atom) {
        this.atom = atom;
        points = new ArrayList<Point3>(4);
        ppms = new Vector(1, 4);
        properties = new boolean[16];

//        Point3 newPt = new Point3(0.0, 0.0, 0.0);
//        points.add(newPt);
//        pt = newPt;
        PPMv ppmv = new PPMv(0.0);
        ppms.addElement(ppmv);
    }

    public SpatialSet(float x, float y, float z) {
        points = new ArrayList<Point3>(4);
        ppms = new Vector(1, 4);
        properties = new boolean[16];

        Point3 newPt = new Point3(x, y, z);
        points.add(newPt);
        pt = newPt;

        PPMv ppmv = new PPMv(0.0);
        ppms.addElement(ppmv);
    }

    public SpatialSet(double ppmValue) {
        PPMv ppmv = new PPMv(ppmValue);
        ppms = new Vector(1);
        ppms.addElement(ppmv);
    }

    public String getName() {
        String name = "";
        if (atom != null) {
            Entity entity = atom.getEntity();
            if (entity != null) {
                CoordSet coordSet = entity.getCoordSet();
                if (coordSet != null) {
                    name = coordSet.getName();
                }
            }
        }
        return name;
    }

    public String getFullName() {
        if (atom.entity instanceof Residue) {
            String polymerName = ((Residue) atom.entity).polymer.getName();
            return getName() + "." + polymerName + ":" + ((Residue) atom.entity).number + "."
                    + atom.name;
        } else {
            return getName() + "." + atom.entity.getName() + ":." + atom.name;
        }
    }

    public void setSelected(int value) {
        selected = value;
    }

    public int getSelected() {
        return selected;
    }

    public void setLabelStatus(int value) {
        labelStatus = value;
    }

    public void setDisplayStatus(int value) {
        displayStatus = value;
    }

    public void setOccupancy(float value) {
        occupancy = value;
    }

    public float getOccupancy() {
        return occupancy;
    }

    public void setBFactor(float value) {
        bfactor = value;
    }

    public float getBFactor() {
        return bfactor;
    }

    public void setOrder(float value) {
        order = value;
    }

    public float getOrder() {
        return order;
    }

    public int getPointCount() {
        return points.size();
    }

    public boolean isStereo() {
        PPMv ppmv = (PPMv) ppms.elementAt(0);
        short ambigCode = 0;

        if ((ppmv != null) && ppmv.isValid()) {
            ambigCode = ppmv.getAmbigCode();
        }

        if (ambigCode == 1) {
            return Boolean.TRUE;
        } else {
            return Boolean.FALSE;
        }
    }

    public Point3 getPoint() {
        return pt;
    }

    public Point3 getPoint(int i) {
        Point3 gPt;
        if (i == 0) {
            gPt = getPoint();
        } else if (i >= points.size()) {
            gPt = null;
        } else {
            gPt = points.get(i);
        }
        return gPt;
    }

    public boolean getPointValidity(int i) {
        boolean valid = false;
        if (i == 0) {
            valid = getPointValidity();
        } else if (points.size() > i) {
            Point3 pt = points.get(i);
            valid = (pt != null);
        }
        return valid;
    }

    public boolean getPointValidity() {
        boolean valid = false;
        if (pt != null) {
            valid = true;
        }
        return valid;
    }

    public void setPointValidity(int index, boolean validity) {
        if (index == 0) {
            setPointValidity(validity);
        } else {
            if (points.size() <= index) {
                points.ensureCapacity(index + 1);
                int size = points.size();
                for (int i = size; i <= index; i++) {
                    points.add(null);
                }
                Point3 pt = new Point3(0.0, 0.0, 0.0);
                points.set(index, pt);
            }
            Point3 setPt = points.get(index);
            if (validity && (setPt == null)) {
                setPt = new Point3(0, 0, 0);
                points.set(index, setPt);
            } else {
                points.set(index, null);
            }
            atom.changed();
        }
    }

    public void setPointValidity(boolean validity) {
        if (pt != null) {
            if (!validity) {
                pt = null;
            }
        } else if (validity) {
            pt = new Point3(0.0, 0.0, 0.0);
        }
        atom.changed();
    }

    public void setPoint(int index, Point3 ptNew) {
        if (index == 0) {
            setPoint(ptNew);
        } else {
            if (points.size() <= index) {
                points.ensureCapacity(index + 1);
                int size = points.size();
                for (int i = size; i <= index; i++) {
                    points.add(null);
                }
            }
            points.set(index, ptNew);
            atom.changed();
        }
    }

    public void setPoint(Point3 ptNew) {
        pt = ptNew;
        atom.changed();
    }

    public PPMv getRefPPM() {
        return getRefPPM(0);
    }

    public PPMv getRefPPM(int ppmSet) {
        PPMv refPPMv = null;
        if ((refPPMVs != null) && (refPPMVs.size() > ppmSet)) {
            refPPMv = refPPMVs.get(ppmSet);
        }
        if ((refPPMv != null) && refPPMv.isValid()) {
            return (refPPMv);
        } else {
            return (null);
        }
    }

    public void setRefPPM(int structureNum, double value) {
        if (refPPMVs == null) {
            refPPMVs = new ArrayList<>();
        }
        if (refPPMVs.size() <= structureNum) {
            for (int i = 0; i <= structureNum; i++) {
                refPPMVs.add(null);
            }
        }
        PPMv refPPMV = refPPMVs.get(structureNum);
        if (refPPMV == null) {
            refPPMV = new PPMv(value);
        } else {
            refPPMV.setValue(value);
        }
        refPPMV.setValid(true, atom);
        refPPMVs.set(structureNum, refPPMV);
    }

    public void setRefError(int structureNum, double value) {
        PPMv refPPMV = getRefPPM(structureNum);
        if (refPPMV != null) {
            refPPMV.setError(value);
        }

    }

    public void setRefPPMValidity(boolean validity) {
        PPMv refPPMV = getRefPPM(0);
        if (refPPMV == null) {
            setRefPPM(0, 0.0);
            refPPMV = getRefPPM(0);
        }
        refPPMV.setValid(validity, atom);
    }

    public int getPPMSetCount() {
        int last = 0;
        for (int i = 0; i < ppms.size(); i++) {
            PPMv ppmv = (PPMv) ppms.elementAt(i);
            if ((ppmv != null) && ppmv.isValid()) {
                last = i;
            }
        }
        return last + 1;
    }

    public int getRefPPMSetCount() {
        int last = -1;
        if (refPPMVs != null) {
            for (int i = 0; i < refPPMVs.size(); i++) {
                PPMv ppmv = (PPMv) refPPMVs.get(i);
                if ((ppmv != null) && ppmv.isValid()) {
                    last = i;
                }
            }
        }
        return last + 1;
    }

    public PPMv getPPM(int i) {
        PPMv ppmv = null;

        if ((i >= 0) && (i < ppms.size())) {
            ppmv = (PPMv) ppms.elementAt(i);
        }

        if ((ppmv == null) || !ppmv.isValid()) {
            ppmv = null;
            if (atom.isMethyl()) {
                Atom[] partners = atom.getPartners(1, 1);
                for (Atom partner : partners) {
                    SpatialSet spatialSet = (SpatialSet) partner.spatialSet;
                    if ((spatialSet != null) && (spatialSet != this)) {
                        if ((i >= 0) && (i < ppms.size())) {
                            ppmv = (PPMv) spatialSet.ppms.elementAt(i);
                        }
                        if ((ppmv != null) && !ppmv.isValid()) {
                            ppmv = null;
                        }
                        if (ppmv != null) {
                            break;
                        }
                    }
                }
            }
        }
        return ppmv;
    }

    public void setPPM(int structureNum, double value, boolean setError) {
        ArrayList<SpatialSet> spSets = new ArrayList<SpatialSet>();
        spSets.add(this);
        if (atom.isMethyl()) {
            Atom[] partners = atom.getPartners(1, 1);
            for (Atom partner : partners) {
                if (partner != null) {
                    SpatialSet spatialSet = (SpatialSet) partner.spatialSet;
                    if ((spatialSet != null) && (spatialSet != this)) {
                        spSets.add(spatialSet);
                    }
                }
            }
        }
        for (SpatialSet spSet : spSets) {
            PPMv ppmv;
            if (structureNum < 0) {
                spSet.setRefPPMValidity(true);
                ppmv = spSet.getRefPPM();
            } else {
                if (spSet.ppms.size() <= structureNum) {
                    spSet.ppms.setSize(structureNum + 1);
                }
                ppmv = (PPMv) spSet.ppms.elementAt(structureNum);
                if (ppmv == null) {
                    ppmv = new PPMv(0.0);
                    spSet.ppms.setElementAt(ppmv, structureNum);
                }
                ppmv.setValid(true, spSet.atom);
            }
            if (ppmv != null) {
                if (setError) {
                    ppmv.setError(value);
                } else {
                    ppmv.setValue(value);
                }
                spSet.atom.changed();
            }
        }
    }

    public void setPPMValidity(int i, boolean validity) {
        ArrayList<SpatialSet> spSets = new ArrayList<SpatialSet>();
        spSets.add(this);
        if (atom.isMethyl()) {
            Atom[] partners = atom.getPartners(1, 1);
            for (Atom partner : partners) {
                SpatialSet spatialSet = (SpatialSet) partner.spatialSet;
                if (spatialSet != null) {
                    spSets.add(spatialSet);
                }
            }
        }
        for (SpatialSet spatialSet : spSets) {
            if (spatialSet.ppms.size() <= i) {
                spatialSet.ppms.setSize(i + 1);
            }
            PPMv ppmv = (PPMv) spatialSet.ppms.elementAt(i);
            if (ppmv == null) {
                ppmv = new PPMv(0.0);
                spatialSet.ppms.setElementAt(ppmv, i);
            }
            ppmv.setValid(validity, spatialSet.atom);
            spatialSet.atom.changed();
        }
    }

    public void setProperty(int propIndex) {
        if (properties.length <= propIndex) {
            return;
        }

        properties[propIndex] = true;
    }

    public void unsetProperty(int propIndex) {
        if (properties.length <= propIndex) {
            return;
        }

        properties[propIndex] = false;
    }

    public boolean getProperty(int propIndex) {
        if (properties.length <= propIndex) {
            return (false);
        } else {
            return (properties[propIndex]);
        }
    }

    public int pointCount() {
        return points.size();
    }

    public void setColor(float red, float green, float blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    /**
     * Creates a new instance of SpatialSet
     */
// 012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
// ****** recname                                                                                   X
//       ***** serial                                                                               X
//             **** aname                                                                           X
//                 * loc                                                                            X
//                  *** rname                                                                       X
//                      *  chain                                                                    X
//                       ***** seq                                                                  X
//                            *** space                                                             X
//                               ********  x                                                        X
//                                       ******** y                                                 X
//                                               ******** z                                         X
//                                                       ****** occ                                 X
//                                                             ****** bfactor                       X
//                                                                   ******     or                  X
//                                                                         ****    segment          X
//                                                                             **  element          X
//                                                                               ** charge          X
// ATOM      1  N   TYR A 104      23.779   2.277  46.922  1.00 16.26           N                   X
// TER    1272      HIS A  80                                                      
    public String toPDBString(int iAtom, int structureNum) {
        Point3 pt = getPoint(structureNum);
        if (pt == null) {
            return null;
        }
        String eName = atom.getElementName();
        if (eName == null) {
            return null;
        }
        String aname = atom.name;
        if (eName.length() == 1) {
            if (eName.equals("H")) {
                if (aname.length() <= 3) {
                    aname = ' ' + aname;
                }
            } else {
                aname = ' ' + aname;
            }
        }

        StringBuilder sBuild = new StringBuilder();
        if ((atom.entity instanceof Residue) && ((Residue) atom.entity).isStandard()) {
            sBuild.append("ATOM  ");
        } else {
            sBuild.append("HETATM");
        }
        sBuild.append(String.format("%5d", iAtom));
        sBuild.append(' ');
        sBuild.append(String.format("%-4s", aname));
        sBuild.append(' ');
        String resName = ((Compound) atom.entity).name;
        if (resName.length() > 3) {
            resName = resName.substring(0, 3);
        }
        sBuild.append(String.format("%3s", resName));
        sBuild.append(' ');
        char chainID = ' ';
        if (atom.entity instanceof Residue) {
            String polymerName = ((Residue) atom.entity).polymer.getName();
            chainID = polymerName.charAt(0);
        }
        sBuild.append(chainID);
        sBuild.append(String.format("%4s", (((Compound) atom.entity).number)));
        sBuild.append("    ");
        sBuild.append(String.format("%8.3f", pt.getX()));
        sBuild.append(String.format("%8.3f", pt.getY()));
        sBuild.append(String.format("%8.3f", pt.getZ()));
        sBuild.append(String.format("%6.2f", occupancy));
        sBuild.append(String.format("%6.2f", bfactor));
        sBuild.append("      "); // or??
        sBuild.append("    "); // segment??
        sBuild.append(String.format("%2s", eName));
        return sBuild.toString();
    }

    public String toTERString(int iAtom) {
        //TER    1272      HIS A  80

        StringBuilder sBuild = new StringBuilder();
        sBuild.append("TER   ");
        sBuild.append(String.format("%5d", iAtom));
        sBuild.append(' ');
        sBuild.append(String.format("%-4s", " "));
        sBuild.append(' ');
        String resName = ((Compound) atom.entity).name;
        if (resName.length() > 3) {
            resName = resName.substring(0, 3);
        }
        sBuild.append(String.format("%3s", resName));
        sBuild.append(' ');
        char chainID = ' ';
        if (atom.entity instanceof Residue) {
            String polymerName = ((Residue) atom.entity).polymer.getName();
            chainID = polymerName.charAt(0);
        }
        sBuild.append(chainID);
        sBuild.append(String.format("%4s", (((Compound) atom.entity).number)));
        sBuild.append("                                                    ");
        return sBuild.toString();

    }

    public void addToSTARString(StringBuilder result) {
        addToSTARString(result, true);
    }

    public void addToSTARString(StringBuilder result, boolean includeSEQID) {
        char sep = ' ';
        result.append(".");                           //  Assembly_atom_ID
        result.append(sep);

        Entity entity = atom.getEntity();
        int entityID = entity.getIDNum();
        int entityAssemblyID = entity.assemblyID;
        int number = 1;
        if (entity instanceof Residue) {
            entityID = ((Residue) entity).polymer.getIDNum();
            entityAssemblyID = ((Residue) entity).polymer.assemblyID;
            number = entity.getIDNum();
        }
        result.append(entityAssemblyID);                           //  Entity_assembly_ID
        result.append(sep);

        result.append(entityID);                           //  Entity__ID
        result.append(sep);
        result.append(number);    //  Comp_index_ID
        result.append(sep);
        if (includeSEQID) {
            result.append(number);    //  Seq_ID  FIXME
        }
        result.append(sep);
        result.append(atom.getEntity().getName());    //  Comp_ID
        result.append(sep);
        result.append(atom.getName());                //  Atom_ID
        result.append(sep);

        String eName = AtomProperty.getElementName(atom.getAtomicNumber());
        result.append(eName);                //  Atom_type
        result.append(sep);
    }

    public boolean addXYZtoSTAR(StringBuilder result,
            int iStruct) {
        char sep = ' ';

        Point3 pt;
        pt = (Point3) getPoint(iStruct);

        if (pt == null) {
            return false;
        } else {
            result.append(pt.getX());
            result.append(sep);
            result.append(pt.getY());
            result.append(sep);
            result.append(pt.getZ());
            result.append(" . . . "); // Cartn_x_esd etc.
            result.append(occupancy);
            result.append(" . . . . ");  // Occupancy_esd, uncertainty, ordered, footnote
        }
        return true;
    }
}
