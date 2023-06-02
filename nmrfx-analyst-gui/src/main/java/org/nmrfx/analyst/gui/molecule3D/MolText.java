package org.nmrfx.analyst.gui.molecule3D;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import org.nmrfx.chemistry.Point3;

import javax.vecmath.Vector3d;

public class MolText extends Group implements MolItem {

    Color color;
    Point3 p3d;
    String text;
    Font font = new Font("Arial", 4.0);

    public MolText(double[] coords, String text, Color color, String tag) {
        this.text = text;
        p3d = new Point3(coords[0], coords[1], coords[2]);
        this.color = color;
        setId(tag);
        refresh();
    }

    public String getNodeName(Node node, Point3D point) {
        return "sphere";
    }

    public void refresh() {
        this.getChildren().clear();
        Group group = makeText();
        if (group != null) {
            this.getChildren().add(group);
            group.setId("text");
        }
    }

    public String getText() {
        return text;
    }

    /**
     * @param text
     */
    public void setRadius(String text) {
        this.text = text;
        refresh();
    }

    public Group makeText() {
        Vector3d v3da = new Vector3d();
        Xform xform = new Xform();
        Text textItem = new Text(text);
        textItem.setCache(false);
        textItem.setFont(font);
        textItem.setTranslateX(p3d.getX());
        textItem.setTranslateY(p3d.getY());
        textItem.setTranslateZ(p3d.getZ());
        xform.getChildren().add(textItem);
        return xform;
    }

}
