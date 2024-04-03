package org.nmrfx.analyst.gui.tools;

import java.util.ArrayList;
import java.util.List;

public class RunAboutArrangement {
    List<String> rows;
    List<String> columns;
    List<String> widths;
    List<String> dims;
    List<List<String>> xdims;

    public List<String> getRows() {
        return rows;
    }

    public void setRows(List<String> rows) {
        this.rows = rows;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<List<String>> getXdims() {
        return xdims;
    }

    public void setXdims(List<List<String>> xdims) {
        this.xdims = xdims;
    }

    public List<String> getWidths() {
        return widths;
    }

    public void setWidths(List<String> widths) {
        this.widths = widths;
    }

    public List<String> getDims() {
        return dims;
    }

    public void setDims(List<String> dims) {
        this.dims = dims;
    }

    public List<RunAboutDim> getColumnArrangement() {
        System.out.println("getxdims " + xdims);
        List<RunAboutDim> result = new ArrayList<>();
        for (String column : columns) {
            int iColumn = result.size();
            RunAboutDim runAboutDim = new RunAboutDim();
            runAboutDim.setDir(column);
            runAboutDim.setWidths(widths);
            runAboutDim.setDims(dims);
            if (xdims != null) {
                List<String> xdim = xdims.get(iColumn);
                if ((xdim != null) && !xdim.isEmpty()) {
                    runAboutDim.setDims(xdim);
                }
            }
            result.add(runAboutDim);
        }
        return result;
    }
}
