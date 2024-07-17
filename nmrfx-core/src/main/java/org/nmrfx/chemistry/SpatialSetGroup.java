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
package org.nmrfx.chemistry;

import java.util.*;

/**
 * @author brucejohnson
 */
public class SpatialSetGroup {

    private Set<SpatialSet> spSets;
    private String name;

    public SpatialSetGroup(String filter) throws InvalidMoleculeException {
        this.name = filter;
        MolFilter mf = new MolFilter(filter);
        List<SpatialSet> spSetVec = MoleculeBase.matchAtoms(mf);
        spSets = new HashSet(spSetVec.size());
        spSets.addAll(spSetVec);
    }

    public SpatialSetGroup(SpatialSet spSet) {
        spSets = new HashSet(1);
        spSets.add(spSet);
        name = spSet.atom.getFullName();
        if (spSet.atom.isMethyl()) {
            name = name.substring(0, name.length() - 1) + "*";
        }
    }

    public SpatialSetGroup(Atom[] atoms) {
        spSets = new HashSet(1);
        for (Atom atom : atoms) {
            spSets.add(atom.spatialSet);
        }
        name = atoms[0].spatialSet.getFullName();
    }
    public SpatialSetGroup(List<Atom> atoms) {
        spSets = new HashSet(1);
        for (Atom atom : atoms) {
            spSets.add(atom.spatialSet);
        }
        name = atoms.get(0).spatialSet.getFullName();
    }

    public int getSize() {
        return spSets.size();
    }

    public String getFullName() {
        return name;
    }

    public String getName() {
        SpatialSet sp = getSpatialSet();
        return sp.getName();
    }

    public void add(SpatialSet spSet) {
        spSets.add(spSet);
    }

    /**
     * Returns a spatial set from the set spSets. Which spatial set is returned is undefined
     * as spSets is unordered. In most cases, spSets only contains a single SpatialSet.
     *
     * @return A SpatialSet
     */
    public SpatialSet getSpatialSet() {
        Iterator<SpatialSet> it = spSets.iterator();
        return it.hasNext() ? it.next() : null;
    }

    public Atom getAnAtom() {
        SpatialSet sp = getSpatialSet();
        return sp.atom;
    }

    public int compare(SpatialSetGroup spg2) {
        String name2 = spg2.getFullName();
        return name.compareTo(name2);
    }

    public void convertToMethyl() {
        if (spSets.size() == 1) {
            SpatialSet sp = getSpatialSet();
            Atom atom = sp.atom;
            if (atom.isMethyl()) {
                AtomEquivalency aEquiv = atom.equivAtoms.get(0);
                ArrayList<Atom> atoms = aEquiv.getAtoms();
                spSets.clear();
                atoms.forEach(atom2 -> spSets.add(atom2.spatialSet));
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        addToSTARString(stringBuilder);
        return stringBuilder.toString();
    }

    public void addToSTARString(StringBuilder result) {
        char sep = ' ';
        result.append(".");                           //  Assembly_atom_ID
        result.append(sep);
        Atom atom = getSpatialSet().atom;
        Entity entity = atom.getEntity();
        int entityID = entity.getIDNum();
        int entityAssemblyID = entity.assemblyID;
        int seqNum = 1;

        if (entity instanceof Residue) {
            seqNum = Integer.parseInt(((Residue) entity).getNumber());
            entityID = ((Residue) entity).polymer.getIDNum();
            entityAssemblyID = ((Residue) entity).polymer.assemblyID;
        }
        boolean usePseudo = false;
        if (atom.isMethyl()) {
            usePseudo = true;
        } else if (atom.equivAtoms != null) {
            AtomEquivalency aEquiv = atom.equivAtoms.get(0);
            // following true for aromatic
            if ((aEquiv.getShell() == 4) && (aEquiv.getAtoms().size() == 2)) {
                usePseudo = true;
            }
        }
        result.append(entityAssemblyID);                           //  Entity_assembly_ID
        result.append(sep);

        result.append(entityID);                           //  Entity__ID
        result.append(sep);
        int number = atom.getEntity().getIDNum();
        result.append(number);    //  Comp_index_ID
        result.append(sep);
        result.append(seqNum);    //  Seq_ID  FIXME
        result.append(sep);
        result.append(atom.getEntity().getName());    //  Comp_ID
        result.append(sep);
        if (usePseudo) {
            result.append(atom.getPseudoName(1));                //  Atom_ID
        } else {
            result.append(atom.getName());
        }
        result.append(sep);

        String eName = AtomProperty.getElementName(atom.getAtomicNumber());
        result.append(eName);                //  Atom_type
        result.append(sep);

        //FIXME result.append(atom.getIsotopeNumber());                //  Atom_isotope_number
        result.append(".");
        result.append(sep);
    }

    /**
     * @return the spSets
     */
    public Set<SpatialSet> getSpSets() {
        return spSets;
    }

    /**
     * @param spSets the spSets to set
     */
    public void setSpSets(Set<SpatialSet> spSets) {
        this.spSets = spSets;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }
}
