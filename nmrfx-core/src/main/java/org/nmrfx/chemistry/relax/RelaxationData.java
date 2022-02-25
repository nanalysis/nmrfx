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

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

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

    public String id;
    public relaxTypes expType;
    ResonanceSource resSource;
    public double field;
    public double temperature;
    public Double value;
    public Double error;
    public Map<String, String> extras;
    private final String key;

    public RelaxationData(String id, relaxTypes expType, ResonanceSource resSource, double field, double temperature,
            Double value, Double error, Map<String, String> extras) {

        this.id = id;
        this.expType = expType;
        this.resSource = resSource;
        this.field = field;
        this.temperature = temperature;
        this.value = value;
        this.error = error;
        this.extras = extras;
        this.key = toKey();
    }

    @Override
    public String toString() {
        return "RelaxationData{" + "ID=" + id + ", expType=" + expType
                + ", atom=" + resSource.toString() + ", field=" + field
                + ", temperature=" + temperature
                + ", value=" + value + ", error=" + error + '}';
    }

    private String toKey() {
        char sepChar = ':';
        var stringBuilder = new StringBuilder();
        stringBuilder.append(id).append(sepChar).
                append(expType.getName()).append(sepChar).
                append(Math.round(field)).append(sepChar).
                append(Math.round(temperature));
        return stringBuilder.toString();

    }

    public String getKey() {
        return key;
    }

    public static void add(String id, String type, ResonanceSource resSource, double field, double value, double error) {
        type = type.toUpperCase();
        if (type.equals("T1")) {
            type = "R1";
        } else if (type.equals("T2")) {
            type = "R2";
        }
        relaxTypes relaxType = relaxTypes.valueOf(type.toUpperCase());
        RelaxationData rData = new RelaxationData(id, relaxType, resSource,
                field,
                25.0, value, error, Collections.EMPTY_MAP);
        resSource.getAtom().addRelaxationData(id, rData);
    }

    @Override
    public String getName() {
        return expType.getName();
    }

    public String getID() {
        return id;
    }

    @Override
    public ResonanceSource getResonanceSource() {
        return resSource;
    }

    public relaxTypes getExpType() {
        return expType;
    }

    @Override
    public String[] getParNames() {
        String[] parNames = {expType.getName()};
        return parNames;
    }

    @Override
    public Double getValue(String name) {
        if (name.equals(expType.getName())) {
            return value;
        } else {
            return null;
        }
    }

    @Override
    public Double getError(String name) {
        if (name.equals(expType.getName())) {
            return error;
        } else {
            return null;
        }
    }

    public double getField() {
        return field;
    }

    public double getTemperature() {
        return temperature;
    }

    @Override
    public Double getValue() {
        return value;
    }

    @Override
    public Double getError() {
        return error;
    }

    public Map<String, String> getExtras() {
        return extras;
    }

    public static  Map<Long, R1R2NOE>  assembleAtomData(Atom atom) {
        var relaxData = atom.getRelaxationData();
        Map<Long, R1R2NOE> dataMap = new HashMap<>();
        Set<Long> fields = new HashSet<>();
        for (var entry : relaxData.entrySet()) {
            RelaxationData data = entry.getValue();
            fields.add(Math.round(data.getField()));
        }
        for (var field : fields) {
            Double r1 = null, r1Error = null, r2 = null, r2Error = null, noe = null, noeError = null;
            Double dField = null;
            for (var entry : relaxData.entrySet()) {
                RelaxationData data = entry.getValue();
                if (Math.round(data.getField()) == field) {
                    switch (data.getExpType()) {
                        case R1:
                            r1 = data.getValue();
                            dField  = data.getField();
                            r1Error = data.getError();
                            break;
                        case R2:
                            r2 = data.getValue();
                            r2Error = data.getError();
                            break;
                        case NOE:
                            noe = data.getValue();
                            noeError = data.getError();
                            break;
                    }
                }
            }
            R1R2NOE r1R2NOE = new R1R2NOE(r1, r1Error, r2, r2Error, noe, noeError, dField);
            dataMap.put(field, r1R2NOE);
        }
        return dataMap;
    }

    public static void writeToFile(File file) throws IOException {
        MoleculeBase moleculeBase = MoleculeFactory.getActive();
        List<Atom> atoms = moleculeBase.getAtomArray();
        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write("Chain\tResidue\tAtom\tfield\tR1\tR1_err\tR2\tR2_err\tNOE\tNOE_err\n");
            for (Atom atom : atoms) {
                var dataMap = assembleAtomData(atom);
                if (!dataMap.isEmpty()) {
                    for (var r1R1NOE : dataMap.values()) {
                        String polymer = atom.getTopEntity().getName();
                        polymer = polymer == null ? "A" : polymer;
                        String resNum = String.valueOf(atom.getResidueNumber());
                        fileWriter.write(polymer + "\t" + resNum+"\t" + atom.getName() + "\t");
                        fileWriter.write(r1R1NOE.toString());
                        fileWriter.write("\n");
                    }
                }
            }
        }
    }

    public static Map<String, List<RelaxationData>> getRelaxationData(List<Atom> atoms) {
        var relaxationData = new HashMap<String, List<RelaxationData>>();
        atoms.forEach((atom) -> {
            for (var relaxEntry : atom.getRelaxationData().entrySet()) {
                String relaxKey = relaxEntry.getValue().getKey();
                List<RelaxationData> relaxList = relaxationData.get(relaxKey);
                if (!relaxationData.containsKey(relaxKey)) {
                    relaxList = new ArrayList<>();
                    relaxationData.put(relaxKey, relaxList);
                }
                relaxList.add(relaxEntry.getValue());
            }
        });
        return relaxationData;
    }
}
