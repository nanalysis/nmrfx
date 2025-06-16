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
package org.nmrfx.chemistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * @author Johnbruc
 */
public class SpatialSet {

    class Coords {

        Point3 pt;
        float occupancy = 1.0f;
        float bfactor = 1.0f;
        float order = 1.0f;

        Coords() {
            pt = new Point3(0.0, 0.0, 0.0);
        }

        Coords(double x, double y, double z, double occupancy, double bfactor) {
            this.pt = new Point3(x, y, z);
            this.occupancy = (float) occupancy;
            this.bfactor = (float) bfactor;
        }

        Coords(Point3 pt) {
            this.pt = new Point3(pt);
        }

        void setPoint(Point3 pt) {
            this.pt = new Point3(pt);
        }

    }

    public Atom atom = null;
    public String altPos = null;
    List<PPMv> ppms;
    List<PPMv> refPPMVs = null;
    List<Coords> coordsList;
    public boolean[] properties;
    public int selected = 0;
    public int labelStatus = 0;
    public int displayStatus = 0;
    public float red = 1.0f;
    public float green = 0.0f;
    public float blue = 0.0f;

    public SpatialSet(Atom atom) {
        this.atom = atom;
        coordsList = new ArrayList<>();
        ppms = new ArrayList<>();
        properties = new boolean[16];
        PPMv ppmv = new PPMv(0.0);
        ppms.add(ppmv);
    }

    public SpatialSet(double ppmValue) {
        PPMv ppmv = new PPMv(ppmValue);
        ppms = new Vector(1);
        ppms.add(ppmv);
    }

    public Atom getAtom() {
        return atom;
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
        setOccupancy(0, value);
    }

    public void setOccupancy(int index, float value) {
        if (getPointValidity(index)) {
            Coords coords = coordsList.get(index);
            coords.occupancy = value;
        }
    }

    public float getOccupancy() {
        return getOccupancy(0);
    }

    public float getOccupancy(int index) {
        return getPointValidity(index) ? coordsList.get(index).occupancy : 1.0f;
    }

    public void setBFactor(float value) {
        setBFactor(0, value);
    }

    public void setBFactor(int index, float value) {
        if (getPointValidity(index)) {
            Coords coord = coordsList.get(index);
            coord.bfactor = value;
        }
    }

    public float getBFactor() {
        return getBFactor(0);
    }

    public float getBFactor(int index) {
        return getPointValidity(index) ? coordsList.get(index).bfactor : 1.0f;
    }

    public void setOrder(float value) {
        setOrder(0, value);
    }

    public void setOrder(int index, float value) {
        if (getPointValidity(index)) {
            Coords coord = coordsList.get(index);
            coord.order = value;
        }
    }

    public float getOrder() {
        return getOrder(0);
    }

    public float getOrder(int index) {
        return getPointValidity(index) ? coordsList.get(index).order : 1.0f;
    }

    public int getPointCount() {
        return coordsList.size();
    }

    public boolean isStereo() {
        PPMv ppmv = ppms.get(0);
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

    public void addCoords(double x, double y, double z,
                          double occupancy, double bfactor) {
        Coords coords = new Coords(x, y, z, occupancy, bfactor);
        coordsList.add(coords);
    }

    public Point3 getPoint() {
        return getPoint(0);
    }

    public Point3 getPoint(int i) {
        Point3 gPt = null;
        if (i < coordsList.size()) {
            Coords coord = coordsList.get(i);
            if (coord != null) {
                gPt = coord.pt;
            }
        }
        return gPt;
    }

    public Coords getCoords(int i) {
        Coords coord = null;
        if (i < coordsList.size()) {
            coord = coordsList.get(i);
        }
        return coord;
    }

    public void clearCoords() {
        coordsList.clear();
    }

    public boolean getPointValidity(int i) {
        return getPoint(i) != null;
    }

    public boolean getPointValidity() {
        return getPointValidity(0);
    }

    public void setPointValidity(int index, boolean validity) {

        if (validity && (coordsList.size() <= index)) {
            int size = coordsList.size();
            for (int i = size; i <= index; i++) {
                coordsList.add(null);
            }
        }
        if (validity) {
            if (coordsList.get(index) == null) {
                Coords coord = new Coords();
                coordsList.set(index, coord);
            }
        } else if (index < coordsList.size()) {
            coordsList.set(index, null);
        }
        atom.changed();
    }

    public void setPointValidity(boolean validity) {
        setPointValidity(0, validity);
    }

    public void setPoint(int index, Point3 ptNew) {
        setPointValidity(index, true);
        Coords coord = coordsList.get(index);
        if (coord == null) {
            coord = new Coords(ptNew);
        } else {
            coord.setPoint(ptNew);
        }
        coordsList.set(index, coord);
        atom.changed();
    }

    public void setPoint(Point3 ptNew) {
        setPoint(0, ptNew);
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
        getAtom().getEntity().molecule.setPPMSetActive("REF", structureNum);
        atom.changed();
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
            PPMv ppmv = ppms.get(i);
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
            ppmv = ppms.get(i);
        }

        if ((ppmv == null) || !ppmv.isValid()) {
            ppmv = null;
            if (atom.isMethyl()) {
                Atom[] partners = atom.getPartners(1, 1);
                for (Atom partner : partners) {
                    SpatialSet spatialSet = (SpatialSet) partner.spatialSet;
                    if ((spatialSet != null) && (spatialSet != this)) {
                        if ((i >= 0) && (i < ppms.size())) {
                            ppmv = spatialSet.ppms.get(i);
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

    public void setPPM(int ppmSet, double value, boolean setError) {
        List<SpatialSet> spSets = new ArrayList<>();
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
        spSets.forEach((spSet) -> {
            PPMv ppmv;
            if (ppmSet < 0) {
                spSet.setRefPPMValidity(true);
                ppmv = spSet.getRefPPM();
            } else {
                if (spSet.ppms.size() <= ppmSet) {
                    int size = spSet.ppms.size();
                    for (int i = size; i <= ppmSet; i++) {
                        spSet.ppms.add(null);
                    }
                }
                ppmv = spSet.ppms.get(ppmSet);
                if (ppmv == null) {
                    ppmv = new PPMv(0.0);
                    spSet.ppms.set(ppmSet, ppmv);
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
        });
        getAtom().getEntity().molecule.setPPMSetActive("PPM", ppmSet);
    }

    public void setPPMValidity(int ppmSet, boolean validity) {
        List<SpatialSet> spSets = new ArrayList<>();
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
            if (spatialSet.ppms.size() <= ppmSet) {
                int size = spatialSet.ppms.size();
                for (int i = size; i <= ppmSet; i++) {
                    spatialSet.ppms.add(null);
                }
            }
            PPMv ppmv = spatialSet.ppms.get(ppmSet);
            if (ppmv == null) {
                ppmv = new PPMv(0.0);
                spatialSet.ppms.set(ppmSet, ppmv);
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
        return coordsList.size();
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
        Coords coord = getCoords(structureNum);
        if (coord == null) {
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
        sBuild.append(String.format("%8.3f", coord.pt.getX()));
        sBuild.append(String.format("%8.3f", coord.pt.getY()));
        sBuild.append(String.format("%8.3f", coord.pt.getZ()));
        sBuild.append(String.format("%6.2f", coord.occupancy));
        sBuild.append(String.format("%6.2f", coord.bfactor));
        sBuild.append("      "); // or??
        sBuild.append("    "); // segment??
        sBuild.append(String.format("%2s", eName));
        return sBuild.toString();
    }

    public String toMMCifString(int iAtom, int iStruct) {
        StringBuilder sBuilder = new StringBuilder();

        Coords coord = getCoords(iStruct);

        if (getPointCount() < 1 || coord == null) {
            return null;
        }

        // group_PDB
        String group = "ATOM";
        // type symbol
        String aType = atom.getSymbol().toUpperCase();
        // atom ID
        String aName = atom.name;
        if (aName.contains("'")) {
            aName = "\"" + aName + "\"";
        }
        // residue name
        String resName = "";
        //  chain code
        char chainID = 'A';
        // entity ID
        int entityID = 1;
        // sequence code
        String seqCode = "1";
        //pdb ins code
        Object pdbInsCode = atom.entity.getPropertyObject("pdbInsCode");
        // cartn x
        double x = coord.pt.getX();
        // cartn y
        double y = coord.pt.getY();
        // cartn z
        double z = coord.pt.getZ();
        // occupancy
        double occupancy = coord.occupancy;
        // B factor
        double bFactor = coord.bfactor;
        //auth seq code
        Object authSeq = atom.entity.getPropertyObject("authSeqID");
        //auth res name 
        Object authResName = atom.entity.getPropertyObject("authResName");
        //auth chain id 
        Object authChainID = atom.entity.getPropertyObject("authChainCode");
        //auth atom name
        Object authAName = atom.getProperty("authAtomName");

        if (atom.entity instanceof Residue) {
            if (atom.getResidueName().equals("MSE")) {
                group = "HETATM";
            }
            // residue name
            resName = ((Residue) atom.entity).name;
            //  chain code
            String polymerName = ((Residue) atom.entity).polymer.getName();
            chainID = polymerName.charAt(0);
            // entity ID
            entityID = ((Residue) atom.entity).polymer.entityID;
            // sequence code
            seqCode = String.valueOf(((Residue) atom.entity).getIDNum());
        } else if (atom.entity instanceof Compound) {
            group = "HETATM";
            resName = atom.entity.label;
            chainID = ((Compound) atom.entity).getName().charAt(0);
            entityID = ((Compound) atom.entity).getIDNum();
            String number = ((Compound) atom.entity).getNumber();
            if (number.equals("0")) {
                seqCode = ".";
            }
        }
        sBuilder.append(String.format("%-7s", group));
        sBuilder.append(String.format("%-8d", iAtom + 1)); //index
        sBuilder.append(String.format("%-3s", aType));
        sBuilder.append(String.format("%-7s", aName));
        sBuilder.append(String.format("%-2s", "."));
        sBuilder.append(String.format("%-4s", resName));
        sBuilder.append(String.format("%-2s", chainID));
        sBuilder.append(String.format("%-2d", entityID));
        sBuilder.append(String.format("%-6s", seqCode));
        if (pdbInsCode != null) {
            sBuilder.append(String.format("%-2s", pdbInsCode.toString()));
        } else {
            sBuilder.append(String.format("%-2s", "?"));
        }
        sBuilder.append(String.format("%-9.3f", x));
        sBuilder.append(String.format("%-9.3f", y));
        sBuilder.append(String.format("%-9.3f", z));
        sBuilder.append(String.format("%-5.2f", occupancy));
        sBuilder.append(String.format("%-8.2f", bFactor));
        sBuilder.append(String.format("%-2s", "?"));
        if (authSeq != null) {
            sBuilder.append(String.format("%-5d", (Integer) authSeq));
        } else {
            sBuilder.append(String.format("%-5s", seqCode));
        }
        if (authResName != null) {
            sBuilder.append(String.format("%-5s", authResName.toString()));
        } else {
            sBuilder.append(String.format("%-5s", resName));
        }
        if (authChainID != null) {
            sBuilder.append(String.format("%-2s", authChainID.toString()));
        } else {
            sBuilder.append(String.format("%-2s", chainID));
        }
        if (authAName != null) {
            String authANameS = authAName.toString();
            if (authANameS.contains("'")) {
                authANameS = "\"" + authANameS + "\"";
            }
            sBuilder.append(String.format("%-7s", authANameS));
        } else {
            sBuilder.append(String.format("%-7s", aName));
        }
        sBuilder.append(String.format("%-2d", iStruct + 1)); //PDB model num

        return sBuilder.toString();
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

        Coords coord = getCoords(iStruct);
        if (coord == null) {
            return false;
        } else {
            result.append(coord.pt.getX());
            result.append(sep);
            result.append(coord.pt.getY());
            result.append(sep);
            result.append(coord.pt.getZ());
            result.append(" . . . "); // Cartn_x_esd etc.
            result.append(coord.occupancy);
            result.append(" . . . . ");  // Occupancy_esd, uncertainty, ordered, footnote
        }
        return true;
    }
}
