package org.nmrfx.structure.chemistry.predict;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.jgrapht.alg.shortestpath.DefaultManyToManyShortestPaths;
import org.jgrapht.graph.DefaultEdge;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Entity;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.structure.chemistry.miner.AtomPaths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GATV2Predictor {
    OrtEnvironment env;
    OrtSession session;
    static final List<Integer> tokens = new ArrayList<>(List.of(6, 8, 7, 1, 16, 9, 17, 35, 15));
    private List<Double> jValues = new ArrayList<>();

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
        getOnnxSession();
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

    public void getOnnxSession() throws OrtException {
        env = OrtEnvironment.getEnvironment();
        session = env.createSession("/Users/brucejohnson/Development/nanalysis/models/jshift.onnx", new OrtSession.SessionOptions());
    }

    public void predict(Entity compound, int iRef) throws OrtException {
        ResidueAtomDistances rad = getRAD(compound);

        int nNodes = rad.atomGraphs.getFirst().nodes().size();
        int nEdges = rad.atomGraphs.getFirst().edges().size();

        ResidueAtomDistances.AtomGraph graph = rad.atomGraphs.getFirst();
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
        var input1 = OnnxTensor.createTensor(env, nodes);
        var input2 = OnnxTensor.createTensor(env, edgeIndex);
        var input3 = OnnxTensor.createTensor(env, edgeAttr);

        List<ResidueAtomDistances.AtomNode> graphNodes = rad.atomGraphs.getFirst().nodes();
        double[] labels = rad.atomGraphs.getFirst().nodes().stream().mapToDouble(ResidueAtomDistances.AtomNode::ppm).toArray();
        var inputs = Map.of("x", input1, "edge_index", input2, "edge_attr", input3);
        try (OrtSession.Result result = session.run(inputs)) {
            Object nodeOut = result.get(0).getValue();
            Object edgeOut = result.get(1).getValue();

            double[] nodeOutputs = processOutput(nodeOut);
            double[] edgeOutputs = processOutput(edgeOut);

            System.out.println("nodeId pshift eshift dshift");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < nNodes; i++) {
                ResidueAtomDistances.AtomNode atomNode = graphNodes.get(i);
                int nodeType = atomNode.property();
                double pshift = denormalize(nodeType, nodeOutputs[i]);
                double error;
                if (atomNode.property() == 1) {
                    error = 0.1;
                } else {
                    error = 0.8;
                }
                Atom atom = atomNode.atom();
                if (iRef < 0) {
                    atom.setRefPPM(-iRef - 1, pshift);
                    atom.setRefError(-iRef - 1, error);
                } else {
                    atom.setPPM(iRef, pshift);
                    atom.setRefError(iRef, error);
                }
                double eshift = denormalize(nodeType, labels[i]);
                sb.append(nodeType).append(" ")
                        .append(formatStr(pshift)).append(" ")
                        .append(formatStr(eshift)).append(" ")
                        .append(formatStr(eshift - pshift))
                        .append("\n");
            }
            System.out.println(sb);
            StringBuilder edgeSb = new StringBuilder();
            for (int i = 0; i < nEdges; i++) {
                double ejValue = 0.0;
                float pathLen = edgeAttr[i][1];
                double jScale = pathLen > 1.1 ? 1.0 : 10.0;
                double pjValue = edgeOutputs[i] * jScale;
                edgeSb.append(formatStr(pjValue)).append(" ")
                        .append(ejValue).append(" ")
                        .append(formatStr(ejValue - pjValue)).append("\n");
            }
            System.out.println(edgeSb);
        }
    }

    private double[] processOutput(Object obj) {
        return Arrays.stream(Arrays.deepToString((Object[]) obj)
                .replace("[", "").replace("]", "")
                .split(",")).mapToDouble(Double::parseDouble).toArray();
    }

    private String formatStr(double value) {
        return String.format("%.3f", value);
    }

}
