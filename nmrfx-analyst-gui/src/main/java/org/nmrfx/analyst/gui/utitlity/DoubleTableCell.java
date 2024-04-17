package org.nmrfx.analyst.gui.utitlity;

import javafx.scene.control.TableCell;

/**
 * Table cell formatter to format non-editable columns of doubles
 */
public class DoubleTableCell<S> extends TableCell<S, Double> {
    private final String formatString;

    public DoubleTableCell(int decimalPlaces) {
        formatString = "%." + decimalPlaces + "f";
    }

    @Override
    protected void updateItem(Double value, boolean empty) {
        super.updateItem(value, empty);
        if (empty) {
            setText(null);
        } else {
            setText(String.format(formatString, value));
        }
    }
}
