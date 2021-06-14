/*
 * NMRFx Structure : A Program for Calculating Structures 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.structure.chemistry.predict;

import java.io.IOException;
import java.io.InputStream;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.energy.PropertyGenerator;
import org.nd4j.linalg.factory.Nd4j;

/*
         *
         * @author brucejohnson
 */
public class Protein2ndStructurePredictor {

    ComputationGraph graphModel;

    public void load() throws IOException {
        InputStream iStream = Protein2ndStructurePredictor.class.getResourceAsStream("/data/predict/protein/model2ndstr.zip");
        graphModel = ModelSerializer.restoreComputationGraph(iStream, true);
    }

    public void predict(Molecule mol) throws IOException {
        if (graphModel == null) {
            load();
        }
        var pg = new PropertyGenerator();
        for (var polymer : mol.getPolymers()) {
            for (var residue : polymer.getResidues()) {
                double zIDR = ResidueProperties.calcZIDR(residue, 0, 0);
                var props = pg.getResidueShiftProps(residue, 2, 5, 0, 0);
                double[][] props2D = {props};
                var ndArray = Nd4j.create(props2D);
                INDArray predicted = graphModel.output(ndArray)[0];
                int rows = predicted.rows();
                int cols = predicted.columns();
                double[] state8 = new double[cols];
                for (int i = 0; i < cols; i++) {
                    state8[i] = predicted.getDouble(0, i);
                }
                ProteinResidueAnalysis protAnalysis = new ProteinResidueAnalysis(residue, zIDR, state8);
                residue.setPropertyObject("Prot2ndStr", protAnalysis);
                System.out.println(protAnalysis.toString());
            }
        }
    }
}
