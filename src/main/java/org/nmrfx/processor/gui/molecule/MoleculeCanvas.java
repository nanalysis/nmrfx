/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui.molecule;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import org.nmrfx.processor.gui.CanvasAnnotation;
import org.nmrfx.structure.chemistry.Molecule;

/**
 *
 * @author Bruce Johnson
 */
public class MoleculeCanvas extends Canvas {

    List<CanvasMolecule> canvasMolecules = new ArrayList<>();

    public void setupMolecules() {
        Molecule molecule = Molecule.activeMol;
        if (molecule != null) {
            CanvasMolecule canvasMol = new CanvasMolecule();
            canvasMol.setMolName(molecule.getName());
            molecule.label = Molecule.LABEL_NAME;
            molecule.updateLabels();
            canvasMol.setPosition(0.1, 0.1, 0.9, 0.9, CanvasAnnotation.POSTYPE.FRACTION, CanvasAnnotation.POSTYPE.FRACTION);
            canvasMolecules.clear();
            canvasMolecules.add(canvasMol);
        }
    }

    public void layoutChildren(Pane pane) {
        setWidth(pane.getWidth());
        setHeight(pane.getHeight());
        double[][] bounds = {{0, getWidth() - 1}, {0, getHeight() - 1}};
        double[][] world = {{0, 1.0}, {0, 1.0}};

        GraphicsContext gC = getGraphicsContext2D();
        gC.setFill(Color.DARKGRAY);
        gC.fillRect(0, 0, getWidth(), getHeight());
        for (CanvasMolecule canvasMol : canvasMolecules) {
            canvasMol.draw(this, bounds, world);
        }

    }
}
