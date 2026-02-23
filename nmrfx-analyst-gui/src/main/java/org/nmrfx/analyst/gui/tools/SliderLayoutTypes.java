package org.nmrfx.analyst.gui.tools;

import org.nmrfx.annotations.YamlEntity;

import java.util.List;
import java.util.Map;

@YamlEntity("sliderlayouts")
public class SliderLayoutTypes {
    private String name;
    private List<Integer> grid;
    private Map<String, List<Map<String, Double>>> gridconstraints;
    private List<SliderLayoutChart> layout;

    public String getName() {
        return name;
    }

    public void setGrid(List<Integer> grid) {
        this.grid = grid;
    }
    public void setGridconstraints(Map<String, List<Map<String, Double>>> gridConstraints) {
        this.gridconstraints = gridConstraints;
    }

    public Map<String, List<Map<String, Double>>> getGridconstraints() {
        return gridconstraints;
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
