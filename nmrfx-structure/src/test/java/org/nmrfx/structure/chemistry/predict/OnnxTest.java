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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class OnnxTest {
    ResidueAtomDistances rad = new ResidueAtomDistances();
    static final int nAtomTypes = 9;
    static final List<Integer> tokens = new ArrayList<>(List.of(6, 8, 7, 1, 16, 9, 17, 35, 15));

    @Test
    public void predictOnnx() throws OrtException, MoleculeIOException {
        String filename = "/Users/ekoag/IMPG2-testing-data/Dataset3/Data3_JESHIZ_NMR.nmredata.sdf";
        var env = OrtEnvironment.getEnvironment();
        var session = env.createSession("/Users/ekoag/multigat.onnx", new OrtSession.SessionOptions());

        SDFile sdf = new SDFile();
        Compound compound = SDFile.read(filename, null, null, null);
        compound.molecule.updateAtomArray();
        MoleculeFactory.setActive(compound.molecule);
        DefaultManyToManyShortestPaths<Atom, DefaultEdge> paths = AtomPaths.getPathAlgorithm(compound, 6, -1, -1);
        rad.generate(compound, paths, 5.0, 0);
        int nAtoms = compound.atoms.size();
        int nEdges = rad.atomGraphs.stream().mapToInt(graph -> graph.edges().size()).sum();
        float[][] nodes = new float[nAtoms][nAtomTypes];
        long[][] edgeIndex = new long[2][nEdges];
        float[][] edgeAttr = new float[nEdges][2];
        for (ResidueAtomDistances.AtomGraph graph : rad.atomGraphs) {
            int i = 0;
            for (ResidueAtomDistances.AtomNode node : graph.nodes()) {
                Arrays.fill(nodes[i], 0);
                nodes[i][tokens.indexOf(node.property())] = 1;
                i++;
            }
            int j = 0;
            for (ResidueAtomDistances.AtomEdge edge : graph.edges()) {
                edgeIndex[0][j] = edge.indexA();
                edgeIndex[1][j] = edge.indexB();
                edgeAttr[j][0] = (float) edge.distance();
                edgeAttr[j][1] = edge.pathLen();
                j++;
            }
        }


        //inputs are x (nodes, 9) edge_index (edges, 2), edge_attr (2, edges)
        var input1 = OnnxTensor.createTensor(env, nodes);
        var input2 = OnnxTensor.createTensor(env, edgeIndex);
        var input3 = OnnxTensor.createTensor(env, edgeAttr);

        var inputs = Map.of("x", input1, "edge_index", input2, "edge_attr", input3);
        try (OrtSession.Result result = session.run(inputs)) {
            Object output = result.get(0).getValue();
            System.out.println(Arrays.deepToString((Object[]) output));

        }
    }

}
