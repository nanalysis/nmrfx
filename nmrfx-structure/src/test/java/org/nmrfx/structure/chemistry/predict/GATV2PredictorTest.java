package org.nmrfx.structure.chemistry.predict;

import ai.onnxruntime.OrtException;
import org.junit.Test;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.AtomCouplingPair;
import org.nmrfx.chemistry.Compound;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.SDFile;

import java.io.IOException;


public class GATV2PredictorTest {
    @Test
    public void predictTest() throws OrtException, MoleculeIOException, IOException {
        GATV2Predictor gatv2Predictor = new GATV2Predictor();
        String molFile = "/Users/ekoag/IMPG2-testing-data/Holdout/Data6_D7118510241.nmredata.sdf";
        SDFile.IS_SPECIAL_BUTTS = true;
        Compound compound = SDFile.read(molFile, null, null, null);
        compound.molecule.updateAtomArray();
        MoleculeFactory.setActive(compound.molecule);
        Atom atomI = compound.molecule.getAtomList().getFirst();
        gatv2Predictor.predict(compound, -1, GATV2Predictor.SolventCorr.Chloroform);
        for (Atom atom : compound.molecule.getAtomList()) {
            double ppm = atom.getPPM();
            if (atom.getRefPPM() != null) {
                double ref = atom.getRefPPM();
                double delta = ppm - ref;
                System.out.println(ppm + " " + ref + " " + delta);
            }
        }
        for (Object obj : atomI.getPredictedCouplingPairs()) {
            AtomCouplingPair pair = (AtomCouplingPair) obj;
            if (atomI.getAtomCouplingPair(pair.atom2()).isPresent()) {
                double actual= atomI.getAtomCouplingPair(pair.atom2()).get().coupling();
                System.out.println(atomI.getName() + " " + pair.atom2().getName() + " " + pair.coupling() + " " + actual);
            }
        }
    }
}