package org.nmrfx.analyst.gui.tools;

import org.nmrfx.annotations.YamlEntity;

import java.util.List;

@YamlEntity("sliderlayouts")
public class SliderLayoutGroup {
    private String name;
    private List<SliderLayoutTypes> layouts;
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<SliderLayoutTypes> getLayouts() {
        return layouts;
    }

    public void setLayouts(List<SliderLayoutTypes> layouts) {
        this.layouts = layouts;
    }

    @Override
    public String toString() {
        return "Layouts{" +
                ", layouts=" + layouts +
                '}';
    }
}
