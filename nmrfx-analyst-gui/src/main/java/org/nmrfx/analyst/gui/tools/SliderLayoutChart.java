package org.nmrfx.analyst.gui.tools;

import org.nmrfx.annotations.YamlEntity;

import java.util.List;

@YamlEntity("sliderlayouts")
public class SliderLayoutChart {
    private String type;
    private Integer row;
    private Integer column;
    private Integer rowspan;
    private Integer columnspan;
    private List<Double> x;
    private List<Double> y;

    private String xsync = null;

    private String ysync = null;

    public String type() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
    public String xsync() {
        return xsync;
    }

    public void setXsync(String xsync) {
        this.xsync = xsync;
    }
    public String ysync() {
        return ysync;
    }

    public void setYsync(String ysync) {
        this.ysync = ysync;
    }

    public Integer row() {
        return row;
    }

    public void setRow(Integer row) {
        this.row = row;
    }

    public Integer column() {
        return column;
    }

    public void setColumn(Integer column) {
        this.column = column;
    }

    public Integer rowspan() {
        return rowspan;
    }

    public void setRowspan(Integer rowspan) {
        this.rowspan = rowspan;
    }

    public Integer columnspan() {
        return columnspan;
    }

    public void setColumnspan(Integer columnspan) {
        this.columnspan = columnspan;
    }

    public List<Double> x() {
        return x;
    }

    public void setX(List<Double> x) {
        this.x = x;
    }

    public List<Double> y() {
        return y;
    }

    public void setY(List<Double> y) {
        this.y = y;
    }
}
