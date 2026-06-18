package org.nmrfx.structure.chemistry.predict;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.junit.Test;
import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.PDBFile;
import org.nmrfx.chemistry.io.SDFile;
import org.nmrfx.structure.chemistry.Molecule;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GATV2PredictorTest {
    public void writeToFile(String out) throws IOException {
        String outFilename = "/Users/ellenkoag/case/onnxPredictions.txt";
        try(FileWriter writer = new FileWriter(outFilename)) {
                writer.write(out);
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

        //printShifts(compound.molecule);

        Atom atomI = compound.molecule.getAtomList().getFirst();
        for (AtomCouplingPair pair : atomI.getPredictedCouplingPairs()) {
            if (atomI.getAtomCouplingPair(pair.atom2()).isPresent()) {
                double actual= atomI.getAtomCouplingPair(pair.atom2()).get().coupling();
                System.out.println(atomI.getName() + " " + pair.atom2().getName() + " " + pair.coupling() + " " + actual);
            }
        }
    }

    public Map<String, Map<String, Double>>  readShifts() {
        String fileName = "/Users/ellenkoag/case/cb1.seg1_nomin.1-100.rdb";
        String string;
        String[] header = null;
        boolean row2 = false;
        Map<String, Map<String, Double>> shiftDict = new HashMap<>();
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
                    for (int i = 2; i < header.length - 5; i++) {
                        double ppm = Double.parseDouble(values[i]);
                        shiftDict.computeIfAbsent(header[i], k -> new HashMap<>()).put(atom.getFullName(), ppm);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return shiftDict;
    }

    // duplicate of Predictor.predictWithGATv2
    void runGATv2(Molecule mol, int iStruct) {
        Map<Entity, List<Entity>> compoundListMap = ResidueAtomDistances.generateCompoundListMap(mol, iStruct);
        GATV2Predictor gatv2Predictor;
        try {
            gatv2Predictor = new GATV2Predictor();
            for (var entry : compoundListMap.entrySet()) {
                int iRef = -iStruct - 1;
                gatv2Predictor.predict(entry.getValue(), iRef, GATV2Predictor.SolventCorr.D2O);
            }
        } catch (OrtException ignored) {
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void predictRNA(String molFile, int iStruct) throws MoleculeIOException {
        PDBFile pdbFile = new PDBFile();
        MoleculeBase mol = pdbFile.read(molFile);
        mol.updateAtomArray();
        pdbFile = new PDBFile();
        mol = MoleculeFactory.getActive();
        pdbFile.readCoordinates(mol, molFile, iStruct, true, false);
        MoleculeFactory.setActive(mol);
        mol.updateAtomArray();
        runGATv2((Molecule) mol, iStruct);
    }

    @Test
    public void predictAllRNA() throws IOException {
        String modelDir = "/Users/ellenkoag/case/pdbFiles";
        File file = new File(modelDir);
        Map<String, Map<String, Double>> shiftDict = null;

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:cb1.*.pdb");
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(file.toPath()) ){
            StringBuilder sb = new StringBuilder();
            for (Path path : paths) {
                if (matcher.matches(path.getFileName())) {
                    String pdbId = path.getFileName().toString().replace(".pdb","");
                    predictRNA(path.toString(), 0);
                    if (shiftDict == null) {
                        shiftDict = readShifts();
                    }
                    Map<String, Double> shifts = shiftDict.get(pdbId);
                    MoleculeFactory.getActive()
                            .getAtomList()
                            .forEach(atom -> {
                                Double pred = shifts.getOrDefault(atom.getFullName(), null);
                                PPMv ref = atom.getRefPPM(0);
                                if (pred != null && ref != null) {
                                    double ppm = ref.getValue();
                                    double delta = pred - ppm;
                                    String deltaStr = String.format("%.3f", delta);
                                    String aName = atom.getFullName();
                                    String line = pdbId + " " + aName + " " + ppm + " " + pred + " " + deltaStr;
                                    sb.append(line).append("\n");
                                }
                            });
                    MoleculeFactory.getActive().clearStructures();
                }
            }
            writeToFile(sb.toString());
        } catch (MoleculeIOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void predictFromCSV() throws OrtException {
        String nodeFilename = "/Users/ellenkoag/case/nodes.csv";
        String edgeFilename = "/Users/ellenkoag/case/edges.csv";
        String string;
        int nNodes = 162;
        int nEdges = 5146;
        long[] nodes = new long[nNodes];
        long[][] edgeIndex = new long[2][nEdges];
        float[][] edgeAttr = new float[nEdges][2];

        try (
                BufferedReader nodes_bf = new BufferedReader(new FileReader(nodeFilename));
                LineNumberReader nodes_lf = new LineNumberReader(nodes_bf);
                BufferedReader edges_bf = new BufferedReader(new FileReader(edgeFilename));
                LineNumberReader edges_lf = new LineNumberReader(edges_bf)
        ) {
            nodes_lf.readLine(); // ignore header
            edges_lf.readLine();
            while ((string = nodes_lf.readLine()) != null && nodes_lf.getLineNumber() <= nNodes + 1) {
                String[] values = string.strip().split(",");
                int i = nodes_lf.getLineNumber() - 2;
                int nodeType = Integer.parseInt(values[2]);
                nodes[i] = GATV2Predictor.tokens.indexOf(nodeType);
            }
            while((string = edges_lf.readLine()) != null && edges_lf.getLineNumber() <= nEdges + 1) {
                String[] values = string.strip().split(",");
                int i = edges_lf.getLineNumber() - 2;
                edgeIndex[0][i] = Long.parseLong(values[1]);
                edgeIndex[1][i] = Long.parseLong(values[2]);
                double distance = Double.parseDouble(values[6]);
                edgeAttr[i][0] = (float) distance;
                edgeAttr[i][1] = Float.parseFloat(values[7]);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        new GATV2Predictor(); //init env and session
        OrtEnvironment env = GATV2Predictor.env;
        OrtSession session = GATV2Predictor.session;
        try (var input1 = OnnxTensor.createTensor(env, nodes);
             var input2 = OnnxTensor.createTensor(env, edgeIndex);
             var input3 = OnnxTensor.createTensor(env, edgeAttr))
        {
            var inputs = Map.of("x", input1, "edge_index", input2, "edge_attr", input3);
            try (OrtSession.Result result = session.run(inputs))
            {
                Object nodeOut = result.get(0);
                OnnxTensor nodeTensor = (OnnxTensor) nodeOut;
                float[][] nodeOutputs = (float[][]) nodeTensor.getValue();
                for (int i = 0; i < nNodes; i++) {
                    double pred = nodeOutputs[i][0];
                    int atomType = GATV2Predictor.tokens.get((int) nodes[i]);
                    System.out.println(i + " " + atomType + " " + pred);
                }
            }
        }
    }
}
