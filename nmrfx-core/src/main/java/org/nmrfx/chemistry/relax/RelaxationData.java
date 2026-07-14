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

import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author mbeckwith
 */
@PluginAPI("ring")
public class RelaxationData implements RelaxationValues {

    private final RelaxationSet relaxationSet;
    private final ResonanceSource resSource;
    private final Double value;
    private final Double error;
    private final String key;

    public RelaxationData(RelaxationSet relaxationSet, ResonanceSource resSource,
                          Double value, Double error) {

        this.relaxationSet = relaxationSet;
        this.resSource = resSource;
        this.value = value;
        this.error = error;
        this.key = toKey();
        relaxationSet.add(this);
    }

    @Override
    public String toString() {
        String name = relaxationSet.name();
        double field = relaxationSet.field();
        double temperature = relaxationSet.temperature();
        return "RelaxationData{" + "ID=" + name + ", expType=" + relaxationSet.relaxType().getName()
                + ", atom=" + getResonanceSource().toString() + ", field=" + field
                + ", temperature=" + temperature
                + ", value=" + getValue() + ", error=" + getError() + '}';
    }

    private String toKey() {
        char sepChar = ':';
        var stringBuilder = new StringBuilder();
        stringBuilder.append(relaxationSet.name()).append(sepChar).
                append(relaxationSet.relaxType().getName()).append(sepChar).
                append(Math.round(relaxationSet.field())).append(sepChar).
                append(Math.round(relaxationSet.temperature()));
        return stringBuilder.toString();

    }

    public String getKey() {
        return key;
    }

    public static void add(RelaxationSet relaxationSet, ResonanceSource resSource, double value, double error) {
        RelaxationData rData = new RelaxationData(relaxationSet, resSource,
                value, error);
        resSource.getAtom().addRelaxationData(relaxationSet, rData);
    }

    public RelaxationSet getRelaxationSet() {
        return relaxationSet;
    }


    @Override
    public ResonanceSource getResonanceSource() {
        return resSource;
    }

    @Override
    public String[] getParNames() {
        return new String[]{relaxationSet.relaxType().getName()};
    }

    @Override
    public Double getValue(String name) {
        if (name.equals(relaxationSet.relaxType().getName())) {
            return getValue();
        } else {
            return null;
        }
    }

    @Override
    public Double getError(String name) {
        if (name.equals(relaxationSet.relaxType().getName())) {
            return getError();
        } else {
            return null;
        }
    }

    @Override
    public Double getValue() {
        return value;
    }

    @Override
    public Double getError() {
        return error;
    }

    public static Map<Long, EnumMap<RelaxTypes, RelaxationData>> assembleAtomData(Atom atom) {
        var relaxData = atom.getRelaxationData();
        Map<Long, EnumMap<RelaxTypes, RelaxationData>> dataMap = new HashMap<>();
        Set<Long> fields = new HashSet<>();
        for (var entry : relaxData.entrySet()) {
            fields.add(Math.round(entry.getKey().field()));
        }
        for (var field : fields) {
            EnumMap<RelaxTypes, RelaxationData> values = new EnumMap<>(RelaxTypes.class);
            for (var entry : relaxData.entrySet()) {
                RelaxationData data = entry.getValue();
                RelaxationSet relaxationSet = entry.getKey();
                if (Math.round(relaxationSet.field()) == field && !relaxationSet.relaxType().getName().startsWith("S") && data.getValue() != null) {
                    values.put(relaxationSet.relaxType(), data);
                }
            }
            if (!values.isEmpty()) {
                dataMap.put(field, values);
            }
        }
        return dataMap;
    }

    private static String toFileString(Map<RelaxTypes, RelaxationData> values, List<RelaxTypes> types, String sepChar) {
        StringBuilder sBuilder = new StringBuilder();
        for (var type : types) {
            var data = values.get(type);
            if ((sBuilder.length() == 0) && (data != null)) {
                sBuilder.append(String.format("%.2f", data.getRelaxationSet().field()));
            }
            sBuilder.append(sepChar);
            Double value = data != null ? data.getValue() : null;
            Double error = data != null ? data.getError() : null;
            RelaxationValues.appendValueError(sBuilder, value, error, "%.3f", ",", sepChar);
        }
        return sBuilder.toString();
    }

    // This method is called from RING NMR Dynamics
    public static void writeToFile(File file) throws IOException {
        MoleculeBase moleculeBase = MoleculeFactory.getActive();
        List<Atom> atoms = moleculeBase.getAtomArray();
        Set<RelaxTypes> typesUsed = EnumSet.noneOf(RelaxTypes.class);
        String sepChar = "\t";


        // figure out what relaxtypes are used so header can be setup
        for (Atom atom : atoms) {
            var dataMap = assembleAtomData(atom);
            for (var relaxData : dataMap.values()) {
                for (var relaxType : relaxData.entrySet()) {
                    typesUsed.add(relaxType.getKey());
                }
            }
        }
        List<RelaxTypes> typeList = Arrays.stream(RelaxTypes.values())
                .filter(typesUsed::contains).collect(Collectors.toList());

        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write("Chain" + sepChar + "Residue" + sepChar + "Atom" + sepChar + "field");
            for (var type : typeList) {
                fileWriter.write(sepChar + type.getName() + sepChar + type.getName() + "_err");
            }
            fileWriter.write("\n");
            for (Atom atom : atoms) {
                var dataMap = assembleAtomData(atom);
                if (!dataMap.isEmpty()) {
                    for (var r1R1NOE : dataMap.values()) {
                        String polymer = atom.getTopEntity().getName();
                        polymer = polymer == null ? "A" : polymer;
                        String resNum = String.valueOf(atom.getResidueNumber());
                        fileWriter.write(polymer + sepChar + resNum + sepChar + atom.getName() + sepChar);
                        fileWriter.write(toFileString(r1R1NOE, typeList, sepChar));
                        fileWriter.write("\n");
                    }
                }
            }
        }
    }

    public static Map<RelaxationSet, List<RelaxationData>> getRelaxationData(List<Atom> atoms) {
        var relaxationData = new HashMap<RelaxationSet, List<RelaxationData>>();
        atoms.forEach(atom -> {
            for (var relaxEntry : atom.getRelaxationData().entrySet()) {
                RelaxationSet relaxationSet = relaxEntry.getKey();
                List<RelaxationData> relaxList = relaxationData.get(relaxationSet);
                if (!relaxationData.containsKey(relaxationSet)) {
                    relaxList = new ArrayList<>();
                    relaxationData.put(relaxationSet, relaxList);
                }
                relaxList.add(relaxEntry.getValue());
            }
        });
        return relaxationData;
    }
}
