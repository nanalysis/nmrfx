package org.nmrfx.peaks.types;

import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PeakListType {
    String name;
    List<PeakListTypeDim> dims;

    public List<PeakListTypeDim> getDims() {
        return dims;
    }

    public void setDims(List<PeakListTypeDim> dims) {
        this.dims = dims;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static void setPeakList(PeakList peakList, String name) {
        for (int i = 0; i < peakList.getNDim(); i++) {
            var sDim = peakList.getSpectralDim(i);
            sDim.setRelation("");
            sDim.setPattern("");
        }
        peakList.setType(name);
    }
    public void setPeakList(PeakList peakList) throws IllegalArgumentException {
        if (getDims().size() != peakList.getNDim()) {
            throw new IllegalArgumentException(("Peak list type has wrong number of dimensions"));
        }
        for (int i = 0; i < peakList.getNDim(); i++) {
            var sDim = peakList.getSpectralDim(i);
            sDim.setRelation("");
            sDim.setPattern("");
        }
        Map<String, SpectralDim> sDims = new HashMap<>();
        for (PeakListTypeDim dim : dims) {
            for (int i = 0; i < peakList.getNDim(); i++) {
                var sDim = peakList.getSpectralDim(i);
                if (dim.getName().equals("H") && sDim.getDimName().contains("H")) {
                    sDims.put(dim.getName(), sDim);
                    break;
                } else if (dim.getName().equals("N") && sDim.getDimName().contains("N") && !sDim.getDimName().contains("H")) {
                    sDims.put(dim.getName(), sDim);
                    break;
                } else if (dim.getName().contains("C") && sDim.getDimName().contains("C") && !sDim.getDimName().contains("H")) {
                    sDims.put(dim.getName(), sDim);
                    break;
                }
            }
        }
        for (int i = 0; i < dims.size(); i++) {
            PeakListTypeDim dim = dims.get(i);
            if (sDims.containsKey(dim.getName())) {
                var sDim = sDims.get(dim.getName());
                sDim.setPattern(dim.getPattern());
                String bond = dim.getBonds();
                if ((bond != null) && !bond.isBlank()) {
                    var sDim2 = sDims.get(bond);
                    sDim.setRelation(sDim2.getDimName());
                }
            }
        }
        peakList.setType(name);
    }
    @Override
    public String toString() {
        return "Type{" +
                "name='" + name + '\'' +
                ", dims=" + dims +
                '}';
    }
}
