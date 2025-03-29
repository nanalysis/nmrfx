package org.nmrfx.peaks.types;

import org.nmrfx.annotations.YamlEntity;

import java.util.List;
import java.util.Optional;

@YamlEntity("peakpat")
public class PeakListTypes {
    private List<PeakListType> types;

    public List<PeakListType> getTypes() {
        return types;
    }

    public void setTypes(List<PeakListType> types) {
        this.types = types;
    }

    @Override
    public String toString() {
        return "Types{" +
                ", types=" + types +
                '}';
    }

    public Optional<PeakListType> getType(String name) {
        return types.stream().filter(p -> p.getName().equals(name)).findFirst();
    }
}
