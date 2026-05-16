package org.nmrfx.structure.chemistry.predict;

import org.nmrfx.chemistry.*;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.SmithWatermanBioJava;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalInt;

public class PredictWithHomolog {
    final ProteinPredictor proteinPredictor;

    public PredictWithHomolog() {
        BMRBStats.loadAllIfEmpty();
        proteinPredictor = new ProteinPredictor();
    }

    private void setPPM(Atom atom, int iRef, PPMv ppmV) {
        if ((ppmV != null) && ppmV.isValid()) {
            if (iRef < 0) {
                atom.setRefPPM(-iRef - 1, ppmV.getValue());
                atom.setRefError(-iRef - 1, ppmV.getError());
            } else {
                atom.setPPM(iRef, ppmV.getValue());
                atom.setPPMError(iRef, ppmV.getError());
            }
        }
    }

    private Double getRandom(Atom atom) throws IOException {
        Double rShift = null;
        if (atom.getEntity() instanceof Residue residue) {
            rShift = proteinPredictor.predictRandom(residue, atom.getName(), 298.0);
            if (rShift == null) {
                Optional<PPMv> ppmVOpt = BMRBStats.getValue(residue.getName(), atom.getName());
                if (ppmVOpt.isPresent()) {
                    rShift = ppmVOpt.get().getValue();
                }
            }
        }
        return rShift;
    }

    private void setIdentical(Residue residue, Residue homologResidue, int iRef) {
        for (Atom atom : residue.getAtoms()) {
            Atom homologAtom = homologResidue.getAtom(atom.getName());
            if (homologAtom != null) {
                PPMv ppmV = homologAtom.getPPM(0);
                setPPM(atom, iRef, ppmV);
            }
        }
    }

    private void setSimilar(Residue residue, Residue homologResidue, int iRef) throws IOException {
        for (Atom atom : residue.getAtoms()) {
            Atom homologAtom = homologResidue.getAtom(atom.getName());
            if (homologAtom != null) {
                PPMv homologPPM = homologAtom.getPPM(0);
                Double homologRandom = getRandom(homologAtom);
                Double atomRandom = getRandom(atom);
                if ((homologPPM != null) && homologPPM.isValid() && (homologRandom != null) && (atomRandom != null)) {
                    double delta = homologPPM.getValue() - homologRandom;
                    double atomShift = delta + atomRandom;
                    PPMv ppmV = new PPMv(atomShift);
                    ppmV.setValid(true, atom);
                    setPPM(atom, iRef, ppmV);
                }
            }
        }
    }

    public void predict(Molecule homolog, int iRef) throws IOException {
        MoleculeBase activeMol = MoleculeFactory.getActive();
        if (activeMol != null) {
            Polymer polymerA = activeMol.getPolymers().getFirst();
            String seqA = polymerA.getOneLetterCode();
            Polymer homologPolymer = homolog.getPolymers().getFirst();
            String seqB = homologPolymer.getOneLetterCode();
            SmithWatermanBioJava aligner = new SmithWatermanBioJava(seqA, seqB);
            var alignmentPair = aligner.doAlignment();
            var aList = aligner.getA();
            for (int i = 0; i < aList.size(); i++) {
                OptionalInt tResIndex = aligner.getTargetIndex(i);
                if (tResIndex.isPresent()) {
                    Residue residue = polymerA.getResidue(i);
                    Residue homologResidue = homologPolymer.getResidue(tResIndex.getAsInt());
                    short blosum = aligner.getBlosum(i+1, tResIndex.getAsInt()+1);
                    if (residue.getName().equals(homologResidue.getName())) {
                        setIdentical(residue, homologResidue, iRef);
                    } else if (blosum > 0){
                        setSimilar(residue, homologResidue, iRef);
                    }
                }
            }
        }
    }
}
