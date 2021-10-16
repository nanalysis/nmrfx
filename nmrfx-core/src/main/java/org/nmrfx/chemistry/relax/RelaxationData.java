/*
 * NMRFx Analyst : 
 * Copyright (C) 2004-2021 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.chemistry.relax;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeBase;

/**
 *
 * @author mbeckwith
 */
public class RelaxationData implements RelaxationValues {

    public enum relaxTypes {
        R1("R1"), R2("R2"), T1RHO("T1rho"), NOE("NOE"), S2("S2");

        private final String name;

        private relaxTypes(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public String ID;
    public relaxTypes expType;
    public List<Atom> extraAtoms;
    Atom atom;
    public double field;
    public double temperature;
    public Double value;
    public Double error;
    public Map<String, String> extras;

    public RelaxationData(String ID, relaxTypes expType, Atom atom, List<Atom> extraAtoms, double field, double temperature,
            Double value, Double error, Map<String, String> extras) {

        this.ID = ID;
        this.expType = expType;
        this.atom = atom;
        this.extraAtoms = extraAtoms;
        this.field = field;
        this.temperature = temperature;
        this.value = value;
        this.error = error;
        this.extras = extras;
    }

    public static void add(String id, String type, Atom atom, double field, double value, double error) {
        if (type.equalsIgnoreCase("t1")) {
            type = "R1";
        } else if (type.equalsIgnoreCase("t2")) {
            type = "R2";
        }
        relaxTypes relaxType = relaxTypes.valueOf(type.toUpperCase());
        RelaxationData rData = new RelaxationData(id, relaxType, atom,
                Collections.EMPTY_LIST, field,
                25.0, value, error, Collections.EMPTY_MAP);
        atom.addRelaxationData(id, rData);
    }

    public String getName() {
        return expType.getName();
    }

    public String getID() {
        return ID;
    }

    public Atom getAtom() {
        return atom;
    }

    public relaxTypes getExpType() {
        return expType;
    }

    public String[] getParNames() {
        String[] parNames = {expType.getName()};
        return parNames;
    }

    public Double getValue(String name) {
        if (name.equals(expType.getName())) {
            return value;
        } else {
            return null;
        }
    }

    public Double getError(String name) {
        if (name.equals(expType.getName())) {
            return error;
        } else {
            return null;
        }
    }

    public List<Atom> getExtraAtoms() {
        return extraAtoms;
    }

    public double getField() {
        return field;
    }

    public double getTemperature() {
        return temperature;
    }

    public Double getValue() {
        return value;
    }

    public Double getError() {
        return error;
    }

    public Map<String, String> getExtras() {
        return extras;
    }

    public static Collection<RelaxationData> getRelaxationData(List<Atom> atoms) {
        List<RelaxationData> relaxDataSet = new ArrayList<>();
        atoms.forEach((atom) -> {
            atom.getRelaxationData().keySet().forEach((key) -> {
                relaxDataSet.add(atom.getRelaxationData(key));
            });
        });
        return relaxDataSet;
    }

    public static Set<relaxTypes> getExpTypes(MoleculeBase molecule) {
        Set<relaxTypes> expTypeSet = new TreeSet<>();
        Collection<RelaxationData> molRelaxData = getRelaxationData(molecule.getAtomArray());
        molRelaxData.forEach((relaxData) -> {
            expTypeSet.add(relaxData.getExpType());
        });

        return expTypeSet;
    }
}
