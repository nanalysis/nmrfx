package org.nmrfx.analyst.gui.molecule3D;

import javafx.geometry.Point3D;
import javafx.scene.Node;

/**
 * @author brucejohnson
 */
public interface MolItem {

    String getNodeName(Node node, Point3D point);

    public default Node getSelectorNode(Node node, Point3D point) {
        return null;
    }

    public void refresh();

}
