package org.nmrfx.chemistry.relax;

import org.nmrfx.chemistry.Atom;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class OrderParSet implements ValueSet {
    private final String name;
    private boolean active = true;
    private final Map<ResonanceSource, OrderPar> data = new TreeMap<>((a, b) -> Atom.compareByIndex(a.getAtom(), b.getAtom()));

    public OrderParSet(String name) {
        this.name = name;
    }

    public void add(OrderPar orderPar) {
        synchronized (data) {
            data.put(orderPar.getResonanceSource(), orderPar);
        }
    }

    public String name() {
        return  name;
    }

    @Override
    public Set<ResonanceSource> resonanceSources() {
        return data.keySet();
    }

    @Override
    public List<? extends RelaxationValues> rValues() {
        return data.values().stream().toList();
    }

    @Override
    public void active(boolean state) {
        active = state;
    }

    @Override
    public boolean active() {
        return active;
    }

    public List<OrderPar> values() {
        return data.values().stream().toList();
    }

    public Map<ResonanceSource, OrderPar> data() {
        return data;
    }

}
