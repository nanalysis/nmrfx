package org.nmrfx.analyst.gui.tools;

import org.nmrfx.annotations.YamlEntity;

import java.util.List;

@YamlEntity("sliderlayouts")
public class SliderLayoutTypes {
    private String name;
    private List<SliderLayoutChart> layout;
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<SliderLayoutChart> getLayout() {
        return layout;
    }

    public void setLayout(List<SliderLayoutChart> layout) {
        this.layout = layout;
    }

    @Override
    public String toString() {
        return "Layouts{" +
                ", layouts=" + layout +
                '}';
    }
}
