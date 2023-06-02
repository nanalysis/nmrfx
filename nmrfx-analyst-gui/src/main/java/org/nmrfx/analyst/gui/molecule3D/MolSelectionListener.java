package org.nmrfx.analyst.gui.molecule3D;

import javafx.scene.input.MouseEvent;

/**
 * @author brucejohnson
 */
public interface MolSelectionListener {

    public default void processSelection(String nodeDescriptor, MouseEvent event) {
        System.out.println("nodedes " + nodeDescriptor);
    }

}
