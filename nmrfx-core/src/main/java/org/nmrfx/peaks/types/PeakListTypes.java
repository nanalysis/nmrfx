package org.nmrfx.peaks.types;

import org.nmrfx.annotations.YamlEntity;

import java.util.List;

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
}
