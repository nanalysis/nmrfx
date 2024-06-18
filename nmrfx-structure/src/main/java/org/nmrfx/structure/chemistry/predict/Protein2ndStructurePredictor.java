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

import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.energy.ProteinPropertyGenerator;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.types.TFloat32;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.ndarray.NdArrays;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

/*
 *
 * @author brucejohnson
 */
public class Protein2ndStructurePredictor {

    static SavedModelBundle graphModel;

    public static void load() throws IOException, URISyntaxException {
        if (graphModel == null) {
            String jarPath = Protein2ndStructurePredictor.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();
            File jarFile = new File(jarPath);
            String modelFilePath = jarFile.getParentFile().getParentFile().toPath().resolve("models").resolve("protein_ss_model").toString();
            System.out.println(modelFilePath);
            graphModel = SavedModelBundle.load(modelFilePath);
        }
    }

    public void predict(Molecule mol) throws IOException, URISyntaxException {
        if (graphModel == null) {
            load();
        }
        var pg = new ProteinPropertyGenerator();
        for (var polymer : mol.getPolymers()) {
            for (var residue : polymer.getResidues()) {
                double zIDR = ResidueProperties.calcZIDR(residue, 0, 0);
                var props = pg.getResidueShiftProps(residue, 2, 5, 0, 0);
                if (props == null) {
                    continue;
                }
                var ndArray = NdArrays.ofFloats(Shape.of(1,props.length));
                float[] propsF  = new float[props.length];
                for (int i = 0; i < props.length; i++) {
                    propsF[i] = (float) (props[i]);
                }
                var tProps = TFloat32.vectorOf(propsF);
                ndArray.set(tProps, 0);
                var tensor = TFloat32.tensorOf(ndArray);
                var predicted = (TFloat32) graphModel.function("serving_default").call(tensor);
                int cols = props.length;
                double[] state8 = new double[8];
                for (int i = 0; i < 8; i++) {
                    state8[i] = predicted.get(0).getFloat(i);
                }
                ProteinResidueAnalysis protAnalysis = new ProteinResidueAnalysis(residue, zIDR, state8);
                residue.setPropertyObject("Prot2ndStr", protAnalysis);
            }
        }
    }
}
