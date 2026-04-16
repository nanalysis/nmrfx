package org.nmrfx.structure.chemistry.predict;

import org.jgrapht.alg.shortestpath.DefaultManyToManyShortestPaths;
import org.jgrapht.graph.DefaultEdge;
import org.nmrfx.chemistry.*;
import org.nmrfx.structure.chemistry.JCoupling;
import org.nmrfx.structure.chemistry.Molecule;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ResidueAtomDistances {
    List<AtomGraph> atomGraphs = new ArrayList<>();

    public record AtomNode(Atom atom, int index, int property, double ppm, int mask) {
        public String getCSV(int iGraph) {
            return String.format("%d,%d,%d,%.3f,%d", iGraph, index, property, ppm, mask);
        }
    }

    public record AtomEdge(List<Integer> iAtomList, double distance, int pathLen, double couplingValue, String couplingName) {
        public String getCSV(int iGraph) {
            StringBuilder stringBuilder = new StringBuilder();
            for (Integer i: iAtomList) {
                if (!stringBuilder.isEmpty()) {
                    stringBuilder.append(",");
                }
                stringBuilder.append(i);
            }
            return String.format("%d,%s,%.3f,%d,%.2f,%s", iGraph, stringBuilder, distance, pathLen, couplingValue, couplingName);
        }

        public int indexA() {
            return iAtomList.getFirst();
        }
        public int indexB() {
            return iAtomList.get(1);
        }
    }

    public record AtomGraph(List<AtomNode> nodes, List<AtomEdge> edges) {
        public String getNodesCSV(int iGraph) {
            StringBuilder stringBuilder = new StringBuilder();
            for (AtomNode atomNode : nodes) {
                stringBuilder.append(atomNode.getCSV(iGraph));
                stringBuilder.append("\n");
            }
            return stringBuilder.toString();
        }

        public String getEdgesCSV(int iGraph) {
            StringBuilder stringBuilder = new StringBuilder();
            for (AtomEdge atomEdge : edges) {
                stringBuilder.append(atomEdge.getCSV(iGraph));
                stringBuilder.append("\n");
            }
            return stringBuilder.toString();
        }
    }

    AtomGraph getNodesAndEdges(List<Entity> compounds, DefaultManyToManyShortestPaths<Atom, DefaultEdge> paths, int iStruct, double limit) {
        Entity compound0 = compounds.getFirst();
        List<Atom> atoms = compounds.stream().flatMap(compound -> compound.atoms.stream()).toList();
        AtomGraph atomGraph = new AtomGraph(new ArrayList<>(), new ArrayList<>());
        for (int iAtomA = 0; iAtomA < atoms.size(); iAtomA++) {
            Atom atomA = atoms.get(iAtomA);
            int atomicNumber = atomA.getAtomicNumber();
            if (atomicNumber < 1) {
                continue;
            }
            int useNode = atomA.getEntity() == compound0 ? 1 : 0;
            Point3 pointA = atomA.getPoint(iStruct);
            PPMv ppmV = atomA.getPPM(iStruct);
            double ppm;
            if ((ppmV == null) || !ppmV.isValid()) {
                useNode = 0;
                ppm = 0.0;
            } else {
                ppm = ppmV.getValue();
            }

            AtomNode atomNode = new AtomNode(atomA, iAtomA, atomicNumber, ppm, useNode);
            atomGraph.nodes.add(atomNode);
            for (int iAtomB = 0; iAtomB < atoms.size(); iAtomB++) {
                Atom atomB = atoms.get(iAtomB);
                if (atomA != atomB) {
                    Point3 pointB = atomB.getPoint(iStruct);
                    double distance = pointA.distance(pointB);
                    //distance = (distance - 3.3) / 1.8;
                    if (distance < limit) {
                        var path = paths.getPath(atomA, atomB);
                        List<Atom> pathAtoms = path.getVertexList();
                        var couplingOpt = JCoupling.CouplingName.getCoupling(pathAtoms);
                        String couplingName = "";
                        if (couplingOpt.isPresent()) {
                            couplingName = couplingOpt.get().name().trim();
                        }
                        int pathLen = Math.min(6, path != null ? path.getLength() : 6);
                        double coupling = 0.0;
                        List<Atom> vertexList = new ArrayList<>();
                        vertexList.add(atomA);
                        vertexList.add(atomB);
                        if (path != null) {
                            var pList = path.getVertexList();
                            for (int i=1;i<pList.size() - 1 ;i++) {
                                vertexList.add(pList.get(i));
                            }
                            var atomCouplingPairOpt = atomA.getAtomCouplingPair(atomB);
                            if (atomCouplingPairOpt.isPresent()) {
                                coupling = atomCouplingPairOpt.get().coupling();
                            }
                        }
                        List<Integer> iAtomList = new ArrayList<>();
                        for (int i=0;i<5;i++) {
                            int index = -1;
                            if (i < vertexList.size()) {
                                index = atoms.indexOf(vertexList.get(i));
                            }
                            iAtomList.add(index);
                        }

                        AtomEdge atomEdge = new AtomEdge(iAtomList, distance, pathLen, coupling, couplingName);
                        atomGraph.edges.add(atomEdge);
                    }
                }
            }
        }
        return atomGraph;
    }

    public void generate(Molecule molecule, DefaultManyToManyShortestPaths<Atom, DefaultEdge> paths, double limit, int iStruct) {
        List<Compound> residueList = new ArrayList<>();
        residueList.addAll(molecule.getPolymers().stream()
                .flatMap(polymer -> polymer.getResidues()
                        .stream().map(Compound.class::cast)).toList());
        residueList.addAll(molecule.getLigands());
        for (Compound residueA : residueList) {
            List<Entity> compounds = new ArrayList<>();
            compounds.add(residueA);
            for (Compound residueB : residueList) {
                if ((residueA != residueB) && (residueA.overlaps(residueB, limit, iStruct))) {
                    compounds.add(residueB);
                }
            }
            atomGraphs.add(getNodesAndEdges(compounds, paths, iStruct, limit));
        }
    }

    public void generate(Entity compound, DefaultManyToManyShortestPaths<Atom, DefaultEdge> paths, double limit, int iStruct) {
        List<Entity> compounds = List.of(compound);
        atomGraphs.add(getNodesAndEdges(compounds, paths, iStruct, limit));
    }

    public void dumpGraphs(String nodeFileName, String edgeFileName) throws IOException {
        String nodeHeader = "graph_id,node_id,node_type,label,mask\n";
        String edgeHeader = "graph_id,source,target,node1,node2,node3,weight,nbonds,jvalue, cname\n";
        try (FileWriter fileWriter = new FileWriter(nodeFileName)) {
            fileWriter.write(nodeHeader);
            int i = 0;
            for (AtomGraph atomGraph : atomGraphs) {
                fileWriter.write(atomGraph.getNodesCSV(i++));
            }
        }
        try (FileWriter fileWriter = new FileWriter(edgeFileName)) {
            fileWriter.write(edgeHeader);
            int i = 0;
            for (AtomGraph atomGraph : atomGraphs) {
                fileWriter.write(atomGraph.getEdgesCSV(i++));
            }
        }
    }
}
