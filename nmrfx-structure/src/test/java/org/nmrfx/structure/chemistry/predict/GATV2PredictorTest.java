package org.nmrfx.structure.chemistry.predict;

import ai.onnxruntime.OrtException;
import org.junit.Test;
import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.PDBFile;
import org.nmrfx.chemistry.io.SDFile;

import java.io.*;
import java.nio.file.*;

public class GATV2PredictorTest {
    public void printShifts(MoleculeBase mol) {
        for (Atom atom : mol.getAtomList()) {
            double ppm = atom.getPPM();
            if (atom.getRefPPM() != null) {
                double ref = atom.getRefPPM();
                double delta = ppm - ref;
                System.out.println(ppm + " " + ref + " " + delta);
                System.out.println(atom.getFullName() + " " + ref);
            }
        }
    }

    @Test
    public void predictTest() throws OrtException, MoleculeIOException, IOException {
        GATV2Predictor gatv2Predictor = new GATV2Predictor();
        String molFile = "/Users/ekoag/IMPG2-testing-data/Holdout/Data6_D7118510241.nmredata.sdf";
        Compound compound = SDFile.read(molFile, null, null, null);
        compound.molecule.updateAtomArray();
        MoleculeFactory.setActive(compound.molecule);
        gatv2Predictor.predict(compound, -1, GATV2Predictor.SolventCorr.Chloroform);

        printShifts(compound.molecule);

        Atom atomI = compound.molecule.getAtomList().getFirst();
        for (AtomCouplingPair pair : atomI.getPredictedCouplingPairs()) {
            if (atomI.getAtomCouplingPair(pair.atom2()).isPresent()) {
                double actual= atomI.getAtomCouplingPair(pair.atom2()).get().coupling();
                System.out.println(atomI.getName() + " " + pair.atom2().getName() + " " + pair.coupling() + " " + actual);
            }
        }
    }

    public void readShifts() {
        String fileName = "/Users/ellenkoag/rna/cb1.seg1_nomin.1-100.rdb";
        String string;
        String[] header = null;
        boolean row2 = false;
        try (
                BufferedReader bf = new BufferedReader(new FileReader(fileName))
        ) {
            while ((string = bf.readLine()) != null) {
                String[] values = string.strip().split("\t");

                if (header == null) {
                    header = values;
                    continue;
                }
                if (!row2) {
                    row2 = true;
                    continue;
                }
                String aName = values[1];
                String resNum = values[0];
                String atomSpec = resNum + "." + aName;

                Atom atom = MoleculeBase.getAtomByName(atomSpec);
                if (atom != null) {
                    for (int iStruct = 0; iStruct < values.length - 2; iStruct++) {
                        double ppm = Double.parseDouble(values[iStruct]);
                        atom.setPPM(iStruct, ppm);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void predictAllRNA() throws IOException, OrtException {
        String modelDir = "";
        File file = new File(modelDir);
        PDBFile pdbFile = new PDBFile();
        MoleculeBase mol = null;

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:pdbFiles/*.pdb");
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(file.toPath()) ){
            int iStruct = 0;
            for (Path path : paths) {
                if (matcher.matches(path)) {
                    if (iStruct == 0) {
                        mol = pdbFile.read(String.valueOf(path));
                        mol.updateAtomArray();
                    }
                    pdbFile.readCoordinates(mol, String.valueOf(path), iStruct, true, false);
                    MoleculeFactory.setActive(mol);
                    mol.updateAtomArray();
                    iStruct++;
                }
            }
        } catch (MoleculeIOException e) {
            throw new RuntimeException(e);
        }
        readShifts();
        GATV2Predictor gatv2Predictor = new GATV2Predictor();
        String nodeFile = "/Users/ellenkoag/rna/testnode.csv";
        String edgeFile = "/Users/ellenkoag/rna/testedge.csv";
        gatv2Predictor.predict(mol.getEntities().getFirst(), -1, GATV2Predictor.SolventCorr.Chloroform);
        printShifts(mol);
    }

    @Test
    public void predictRNA() throws OrtException, MoleculeIOException, IOException {
        GATV2Predictor gatv2Predictor = new GATV2Predictor();
        String molFile = "/Users/ellenkoag/rna/cb1.1.pdb";
        PDBFile pdbFile = new PDBFile();
        MoleculeBase mol = pdbFile.read(molFile);
        mol.updateAtomArray();
        pdbFile.readCoordinates(mol, molFile, 0, true, false);
        MoleculeFactory.setActive(mol);
        mol.updateAtomArray();
        readShifts();
        String nodeFile = "/Users/ellenkoag/rna/testnode.csv";
        String edgeFile = "/Users/ellenkoag/rna/testedge.csv";
        gatv2Predictor.predict(mol.getEntities().getFirst(), -1, GATV2Predictor.SolventCorr.Chloroform);
        printShifts(mol);
    }
}