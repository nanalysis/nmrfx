package org.nmrfx.structure.chemistry.predict;

import ai.onnxruntime.*;
import org.jgrapht.alg.shortestpath.DefaultManyToManyShortestPaths;
import org.jgrapht.graph.DefaultEdge;
import org.junit.Test;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Compound;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.SDFile;
import org.nmrfx.structure.chemistry.miner.AtomPaths;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class OnnxTest {
    ResidueAtomDistances rad = new ResidueAtomDistances();
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

    private double denormalize(int atomType, double output) {
        Double[] values = normValues.get(atomType);
        return (output * values[1]) + values[0];
    }

    public void readFromDataset(int id) throws IOException {
        String nodesFilename = "/Users/ekoag/gatv2/nodes_jnorm.csv";
        String edgesFilename = "/Users/ekoag/gatv2/edges_jnorm.csv";
        List<ResidueAtomDistances.AtomNode> nodes = new ArrayList<>();
        List<ResidueAtomDistances.AtomEdge> edges = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(nodesFilename))) {
            reader.readLine();
            String line;
            while((line = reader.readLine()) != null) {
                String[] values = line.strip().split(",");
                int graphId = Integer.parseInt(values[0]);
                if (graphId == id) {
                    int nodeId = Integer.parseInt(values[1]);
                    int nodeType = Integer.parseInt(values[2]);
                    double label = Double.parseDouble(values[3]);
                    int mask = Integer.parseInt(values[4]);
                    ResidueAtomDistances.AtomNode node = new ResidueAtomDistances.AtomNode(nodeId, nodeType, label, mask);
                    nodes.add(node);
                }
            }
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(edgesFilename))) {
            reader.readLine();
            String line;
            while((line = reader.readLine()) != null) {
                String[] values = line.strip().split(",");
                int graphId = Integer.parseInt(values[0]);
                if (graphId == id) {
                    int source = Integer.parseInt(values[1]);
                    int target = Integer.parseInt(values[2]);
                    double distance = Double.parseDouble(values[6]);
                    int nBonds = Integer.parseInt(values[7]);
                    double jValue = Double.parseDouble(values[8]);
                    jValues.add(jValue);
                    ResidueAtomDistances.AtomEdge edge = new ResidueAtomDistances.AtomEdge(source, target, distance, nBonds);
                    edges.add(edge);
                }
            }
        }
        rad.atomGraphs.add(id, new ResidueAtomDistances.AtomGraph(nodes, edges));

    }

    public void fromSDFile() throws MoleculeIOException {
        String filename = "/Users/ekoag/IMPG2-testing-data/Dataset3/Data3_JESHIZ_NMR.nmredata.sdf";
        SDFile sdf = new SDFile();
        Compound compound = SDFile.read(filename, null, null, null);
        compound.molecule.updateAtomArray();
        MoleculeFactory.setActive(compound.molecule);
        DefaultManyToManyShortestPaths<Atom, DefaultEdge> paths = AtomPaths.getPathAlgorithm(compound, 6, -1, -1);
        rad.generate(compound, paths, 5.0, 0);
    }

    @Test
    public void predictOnnx() throws OrtException, IOException {
        var env = OrtEnvironment.getEnvironment();
        var session = env.createSession("/Users/ekoag/gatv2/jshift.onnx", new OrtSession.SessionOptions());

        readFromDataset(0);

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
            edgeAttr[j][0] = (float) edge.distance();
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
                int nodeType = graphNodes.get(i).property();
                double pshift = denormalize(nodeType, nodeOutputs[i]);
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
                double pjValue = edgeOutputs[i];
                double ejValue = jValues.get(i);
                edgeSb.append(formatStr(pjValue)).append(" ")
                        .append(ejValue).append(" ")
                        .append(formatStr(ejValue - pjValue)).append("\n");
            }
            System.out.println(edgeSb);
        }
    }

    private double[] processOutput(Object obj) {
        return Arrays.stream(Arrays.deepToString((Object[]) obj)
                .replace("[","").replace("]","")
                .split(",")).mapToDouble(Double::parseDouble).toArray();
    }

    private String formatStr(double value) {
        return String.format("%.3f", value);
    }

}
