package org.nmrfx.structure.chemistry.predict;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.jgrapht.alg.shortestpath.DefaultManyToManyShortestPaths;
import org.jgrapht.graph.DefaultEdge;
import org.nmrfx.chemistry.*;
import org.nmrfx.structure.chemistry.miner.AtomPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GATV2Predictor {
    static OrtSession session = null;
    static OrtEnvironment env = null;
    static final List<Integer> tokens = new ArrayList<>(List.of(6, 8, 7, 1, 16, 9, 17, 35, 15));

    Map<Integer, Double[]> normValues = Map.of(
            1, new Double[]{3.4, 4.9},
            6, new Double[]{123.6, 84.4},
            7, new Double[]{-234.3, 183.3},
            8, new Double[]{129.1, 306.9},
            9, new Double[]{273.4, 50.1},
            15, new Double[]{301.1, 23.9},
            16, new Double[]{293.7, 225.0},
            17, new Double[]{736.0, 97.3},
            35, new Double[]{2054.2, 79.2});

    public GATV2Predictor() throws OrtException {
        if (session == null) {
            getOnnxSession();
        }
    }

    private double denormalize(int atomType, double output) {
        Double[] values = normValues.get(atomType);
        return (output * values[1]) + values[0];
    }


    public ResidueAtomDistances getRAD(Entity compound) {
        compound.molecule.updateAtomArray();
        MoleculeFactory.setActive(compound.molecule);
        DefaultManyToManyShortestPaths<Atom, DefaultEdge> paths = AtomPaths.getPathAlgorithm(compound, 6, -1, -1);
        ResidueAtomDistances rad = new ResidueAtomDistances();
        rad.generate(compound, paths, 5.0, 0);
        return rad;
    }


    public static void getOnnxSession() throws OrtException {
        env = OrtEnvironment.getEnvironment();
        String homeDirPath = System.getProperty("user.home");
        Path path = Path.of(homeDirPath, "nmrfx_models", "jshift_v1.onnx");
        File file = path.toFile();
        if (file.exists()) {
            session = env.createSession(file.toString(), new OrtSession.SessionOptions());
        } else {
            InputStream modelStream = ClassLoader.getSystemResourceAsStream("data/jshift_v1.onnx");
            if (modelStream == null) {
                throw new IllegalArgumentException("Model file not found in classpath");
            }

            byte[] modelBytes;
            try (InputStream in = modelStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                modelBytes = out.toByteArray();
                session = env.createSession(modelBytes);
            } catch (IOException ioException) {

            }

        }
    }

    private void averageMethyls(Entity entity, int iRef) {
        for (Atom atom : entity.getAtoms()) {
            if (atom.isMethyl() && atom.isFirstInMethyl()) {
                var methylH = atom.getMethylProtons();
                double sum = 0.0;
                double sumError = 0.0;
                int n = 0;
                for (Atom hAtom : methylH) {
                    PPMv ppMv = iRef < 0 ? hAtom.getRefPPM(-iRef - 1) : hAtom.getPPM(iRef);
                    if ((ppMv != null) && ppMv.isValid()) {
                        sum += ppMv.getValue();
                        sumError += ppMv.getError();
                        n++;
                    }
                }
                for (Atom hAtom : methylH) {
                    setPPM(hAtom, sum / n, sumError / n, iRef);
                }
            }
        }
    }

    private void setPPM(Atom atom, double shift, Double error, int iRef) {
        if (iRef < 0) {
            atom.setRefPPM(-iRef - 1, shift);
            if (error != null) {
                atom.setRefError(-iRef - 1, error);
            }
        } else {
            atom.setPPM(iRef, shift);
            if (error != null) {
                atom.setRefError(iRef, error);
            }
        }

    }

    private void setShift(ResidueAtomDistances.AtomNode atomNode, double value, int iRef) {
        int nodeType = atomNode.property();
        double shift = Math.round(denormalize(nodeType, value) * 1000.0) / 1000.0;
        double error;
        if (nodeType == 1) {
            error = 0.1;
        } else {
            error = 0.8;
        }
        Atom atom = atomNode.atom();
        setPPM(atom, shift, error, iRef);
    }

    private void setCoupling(Atom atomI, Atom atomJ, int pathLen, double value) {
        double jScale = pathLen > 1.1 ? 1.0 : 10.0;
        double pjValue = Math.round(value * jScale * 100.0) / 100.0;
        String couplingName;
        if (atomI.getAtomicNumber() < atomJ.getAtomicNumber()) {
            couplingName = pathLen + "J" + atomI.getElementName() + atomJ.getElementName();
        } else {
            couplingName = pathLen + "J" + atomJ.getElementName() + atomI.getElementName();
        }
        atomI.addAtomCouplingPair(new AtomCouplingPair(atomI, atomJ, pjValue, couplingName));
        atomJ.addAtomCouplingPair(new AtomCouplingPair(atomJ, atomI, pjValue, couplingName));

    }

    public void predict(Entity compound, int iRef) throws OrtException {
        ResidueAtomDistances rad = getRAD(compound);
        ResidueAtomDistances.AtomGraph graph = rad.atomGraphs.getFirst();

        int nNodes = graph.nodes().size();
        int nEdges = graph.edges().size();

        long[] nodes = graph.nodes().stream().mapToLong(node -> tokens.indexOf(node.property())).toArray();
        long[][] edgeIndex = new long[2][nEdges];
        float[][] edgeAttr = new float[nEdges][2];
        for (int j = 0; j < graph.edges().size(); j++) {
            ResidueAtomDistances.AtomEdge edge = graph.edges().get(j);
            edgeIndex[0][j] = edge.indexA();
            edgeIndex[1][j] = edge.indexB();
            double scaledDistance = (edge.distance() - 3.3) / 1.8;
            edgeAttr[j][0] = (float) scaledDistance;
            edgeAttr[j][1] = edge.pathLen();
        }

        //inputs are x (nNodes) edge_index (2, nEdges), edge_attr (nEdges, 2)
        try (var input1 = OnnxTensor.createTensor(env, nodes); var input2 = OnnxTensor.createTensor(env, edgeIndex); var input3 = OnnxTensor.createTensor(env, edgeAttr)) {
            List<ResidueAtomDistances.AtomNode> graphNodes = rad.atomGraphs.getFirst().nodes();
            var inputs = Map.of("x", input1, "edge_index", input2, "edge_attr", input3);
            try (OrtSession.Result result = session.run(inputs)) {
                Object nodeOut = result.get(0).getValue();
                Object edgeOut = result.get(1).getValue();

                double[] nodeOutputs = processOutput(nodeOut);
                double[] edgeOutputs = processOutput(edgeOut);

                for (int i = 0; i < nNodes; i++) {
                    ResidueAtomDistances.AtomNode atomNode = graphNodes.get(i);
                    int nodeType = atomNode.property();
                    if ((nodeType == 1) || (nodeType == 6) || (nodeType == 7) || (nodeType == 9) || (nodeType == 15)) {
                        setShift(atomNode, nodeOutputs[i], iRef);
                    }
                }
                averageMethyls(compound, iRef);
                for (int i = 0; i < nEdges; i++) {
                    float pathLen = edgeAttr[i][1];
                    String cName = graph.edges().get(i).couoplingName();
                    if ((pathLen < 5) && !cName.isBlank()) {
                        int iIndex = (int) edgeIndex[0][i];
                        int jIndex = (int) edgeIndex[1][i];
                        Atom atomI = graphNodes.get(iIndex).atom();
                        Atom atomJ = graphNodes.get(jIndex).atom();
                        setCoupling(atomI, atomJ, Math.round(pathLen), edgeOutputs[i]);
                    }
                }
            }
        }
    }

    private double[] processOutput(Object obj) {
        return Arrays.stream(Arrays.deepToString((Object[]) obj)
                .replace("[", "").replace("]", "")
                .split(",")).mapToDouble(Double::parseDouble).toArray();
    }
}
