package org.nmrfx.chemistry.relax;

import org.nmrfx.chemistry.Atom;

import java.util.*;

public class RelaxationSet implements ValueSet {
    Map<ResonanceSource, RelaxationData> data = new TreeMap<>((a, b) -> Atom.compareByIndex(a.getAtom(), b.getAtom()));
    private final RelaxTypes relaxType;
    private final String name;

    private boolean active = true;

    private final double field;
    private final double temperature;

    private final Map<String, String> extras;

    public RelaxationSet(String name, RelaxTypes relaxType, double field, double temperature, Map<String, String> extras) {
        this.name = name;
        this.relaxType = relaxType;
        this.field = field;
        this.temperature = temperature;
        this.extras = extras;
    }

    public void add(RelaxationData relaxationData) {
        data.put(relaxationData.getResonanceSource(), relaxationData);
    }

    public Map<String, String> extras() {
        return extras;
    }

    public String name() {
        return name;
    }

    @Override
    public void active(boolean state) {
        active = state;
    }

    @Override
    public boolean active() {
        return active;
    }

    public Set<ResonanceSource> resonanceSources() {
        return data.keySet();
    }

    public RelaxTypes relaxType() {
        return relaxType;
    }

    public Map<ResonanceSource, RelaxationData> data() {
        return data;
    }

    public List<RelaxationData> values() {
        return data.values().stream().toList();
    }

    public List<? extends RelaxationValues> rValues() {
        return data.values().stream().toList();
    }

    public double field() {
        return field;
    }

    public double temperature() {
        return temperature;
    }

    public Map<Atom, ValueWithError> getAtomValueWithErrorMap() {
        Map<Atom, ValueWithError> atomValueMap = new HashMap<>();
        for (var entry : data.entrySet()) {
            Atom atom = entry.getKey().getAtom();
            RelaxationData relaxationData = entry.getValue();
            ValueWithError valueWithError = new ValueWithError(relaxationData.getValue(), relaxationData.getError());
            atomValueMap.put(atom, valueWithError);
        }
        return atomValueMap;
    }
}
