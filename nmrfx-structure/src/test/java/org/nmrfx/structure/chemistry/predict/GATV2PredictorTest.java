package org.nmrfx.structure.chemistry.predict;

import ai.onnxruntime.OrtException;
import org.junit.Test;
import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.PDBFile;
import org.nmrfx.chemistry.io.SDFile;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class GATV2PredictorTest {
    public void printShifts(MoleculeBase mol) {
        for (Atom atom : mol.getAtomList()) {
            if (atom.getRefPPM() != null && atom.getPPM() != null) {
                double ppm = atom.getPPM();
                double ref = atom.getRefPPM();
                double delta = ppm - ref;
                System.out.println(atom.getFullName() + " " +ppm + " " + ref + " " + delta);
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

    public void readShifts(Map<String, Integer> pdbDict) {
        String fileName = "/Users/ellenkoag/case/cb1.seg1_nomin.1-100.rdb";
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
                    for (Map.Entry<String, Integer> entry : pdbDict.entrySet()) {
                        int iStruct = entry.getValue();
                        int i = Arrays.stream(header).toList().indexOf(entry.getKey());

                        double ppm = Double.parseDouble(values[i]);
                        atom.setPPM(iStruct, ppm);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void predictAllRNA() throws IOException, OrtException {
        String modelDir = "/Users/ellenkoag/case/pdbFiles";
        File file = new File(modelDir);
        PDBFile pdbFile = new PDBFile();
        MoleculeBase mol = null;
        Map<String, Integer> pdbDict = new HashMap<>();

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:cb1.*.pdb");
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(file.toPath()) ){
            int iStruct = 0;
            for (Path path : paths) {
                if (matcher.matches(path.getFileName())) {
                    if (iStruct == 0) {
                        mol = pdbFile.read(String.valueOf(path));
                        mol.updateAtomArray();
                    }
                    pdbFile.readCoordinates(mol, String.valueOf(path), iStruct, true, false);
                    MoleculeFactory.setActive(mol);
                    mol.updateAtomArray();
                    String pdbId = path.getFileName().toString().replace(".pdb","");
                    pdbDict.put(pdbId, iStruct);
                    iStruct++;
                }
            }
        } catch (MoleculeIOException e) {
            throw new RuntimeException(e);
        }
        readShifts(pdbDict);
        GATV2Predictor gatv2Predictor = new GATV2Predictor();
        assert mol != null;
        gatv2Predictor.predict(mol.getEntities().getFirst(), -1, GATV2Predictor.SolventCorr.Chloroform, true);
        printShifts(mol);
    }

    @Test
    public void predictRNA() throws OrtException, MoleculeIOException, IOException {
        GATV2Predictor gatv2Predictor = new GATV2Predictor();
        String molFile = "/Users/ellenkoag/case/pdbfiles/cb1.1.pdb";
        PDBFile pdbFile = new PDBFile();
        MoleculeBase mol = pdbFile.read(molFile);
        mol.updateAtomArray();
        pdbFile.readCoordinates(mol, molFile, 0, true, false);
        MoleculeFactory.setActive(mol);
        mol.updateAtomArray();
        Map<String, Integer> pdbDict = new HashMap<>();
        pdbDict.put("cb1.1", 0);
        readShifts(pdbDict);
        gatv2Predictor.predict(mol.getEntities().getFirst(), -1, GATV2Predictor.SolventCorr.Chloroform, true);
        printShifts(mol);
    }
}