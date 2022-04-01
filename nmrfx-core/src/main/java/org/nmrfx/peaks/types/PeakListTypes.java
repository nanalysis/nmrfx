package org.nmrfx.peaks.types;

import java.util.List;

public class PeakListTypes {
    List<PeakListType> types;

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
