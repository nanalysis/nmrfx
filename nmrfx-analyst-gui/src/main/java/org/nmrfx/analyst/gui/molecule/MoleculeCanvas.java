/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.gui.molecule;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.graphicsio.GraphicsContextProxy;
import org.nmrfx.processor.gui.CanvasAnnotation;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bruce Johnson
 */
public class MoleculeCanvas extends Canvas {

    List<CanvasMolecule> canvasMolecules = new ArrayList<>();

    public void setupMolecules() {
        Molecule molecule = (Molecule) MoleculeFactory.getActive();
        if (molecule != null) {
            molecule.clearSelected();
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
        gC.setFill(Color.WHITE);
        gC.fillRect(0, 0, getWidth(), getHeight());
        GraphicsContextProxy gCProxy = new GraphicsContextProxy(gC);
        for (CanvasMolecule canvasMol : canvasMolecules) {
            canvasMol.draw(gCProxy, bounds, world);
        }

    }
}
