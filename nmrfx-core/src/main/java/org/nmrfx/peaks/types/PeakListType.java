package org.nmrfx.peaks.types;

import org.nmrfx.annotations.YamlEntity;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@YamlEntity("peakpat")
public class PeakListType {
    private String name;
    private List<PeakListTypeDim> dims;

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
        peakList.setExperimentType(name);
    }

    private SpectralDim getBondDim(Map<String, SpectralDim> sDims, String bondDim) {
        for (var entry : sDims.entrySet()) {
            String key = entry.getKey();
            String dim = key.substring(0, key.indexOf(":"));
            if (dim.equals(bondDim)) {
                return entry.getValue();
            }
        }
        return null;
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
        boolean[] used = new boolean[peakList.getNDim()];
        int j = 0;
        for (PeakListTypeDim dim : dims) {
            String dimName = dim.getName() + ":" + j;
            for (int i = 0; i < peakList.getNDim(); i++) {
                if (used[i]) {
                    continue;
                }
                var sDim = peakList.getSpectralDim(i);
                if (dim.getName().startsWith("H") && sDim.getDimName().contains("H")) {
                    sDims.put(dimName, sDim);
                    used[i] = true;
                    break;
                } else if (dim.getName().startsWith("N") && sDim.getDimName().contains("N") && !sDim.getDimName().contains("H")) {
                    used[i] = true;
                    sDims.put(dimName, sDim);
                    break;
                } else if (dim.getName().startsWith("C") && sDim.getDimName().contains("C") && !sDim.getDimName().contains("H")) {
                    used[i] = true;
                    sDims.put(dimName, sDim);
                    break;
                }
            }
            j++;
        }
        j = 0;
        for (PeakListTypeDim dim : dims) {
            String dimName = dim.getName() + ":" + j;
            if (sDims.containsKey(dimName)) {
                var sDim = sDims.get(dimName);
                sDim.setPattern(dim.getPattern());
                String bond = dim.getBonds();
                if ((bond != null) && !bond.isBlank()) {
                    var sDim2 = getBondDim(sDims, bond);
                    if (sDim2 != null) {
                        sDim.setRelation(sDim2.getDimName());
                    }
                }
            }
            j++;
        }
        peakList.setExperimentType(name);
    }

    @Override
    public String toString() {
        return "Type{" +
                "name='" + name + '\'' +
                ", dims=" + dims +
                '}';
    }
}
