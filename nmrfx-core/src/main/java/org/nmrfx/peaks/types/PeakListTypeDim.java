package org.nmrfx.peaks.types;

import org.nmrfx.annotations.YamlEntity;

@YamlEntity("peakpat")
public class PeakListTypeDim {
    private String name;
    private String pattern;
    private String bonds;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getBonds() {
        return bonds;
    }

    public void setBonds(String bonds) {
        this.bonds = bonds;
    }
}
