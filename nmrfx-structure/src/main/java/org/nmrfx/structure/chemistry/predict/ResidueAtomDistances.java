package org.nmrfx.structure.chemistry.predict;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Compound;
import org.nmrfx.chemistry.PPMv;
import org.nmrfx.chemistry.Point3;
import org.nmrfx.structure.chemistry.Molecule;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResidueAtomDistances {
    List<AtomGraph> atomGraphs = new ArrayList<>();
    Map<String, Integer> atomMap = Map.of("H",1, "C",2,"N",3, "O", 4,"P", 5);

    public record AtomNode(int index, int property, double ppm, int mask) {
        public String getCSV(int iGraph) {
            return String.format("%d,%d,%d,%.3f,%d", iGraph, index, property, ppm, mask);
        }
    }

    public record AtomEdge(int indexA, int indexB, double distance, int bondDis) {
        public String getCSV(int iGraph) {
            return String.format("%d,%d,%d,%.3f,%d", iGraph, indexA, indexB, distance, bondDis);
        }
    }

    public record AtomGraph(List<AtomNode> nodes, List<AtomEdge> edges) {
        public String getNodesCSV(int iGraph) {
            StringBuilder stringBuilder  = new StringBuilder();
            for (AtomNode atomNode : nodes) {
                stringBuilder.append(atomNode.getCSV(iGraph));
                stringBuilder.append("\n");
            }
            return  stringBuilder.toString();
        }
        public String getEdgesCSV(int iGraph) {
            StringBuilder stringBuilder  = new StringBuilder();
            for (AtomEdge atomEdge : edges) {
                stringBuilder.append(atomEdge.getCSV(iGraph));
                stringBuilder.append("\n");
            }
            return  stringBuilder.toString();
        }
    }

    AtomGraph getNodesAndEdges(List<Compound> compounds, int iStruct, double limit) {
        Compound compound0 = compounds.getFirst();
        List<Atom> atoms = compounds.stream().flatMap(compound -> compound.atoms.stream()).toList();
        AtomGraph atomGraph = new AtomGraph(new ArrayList<>(), new ArrayList<>());
        for (int iAtomA = 0; iAtomA < atoms.size(); iAtomA++) {
            Atom atomA = atoms.get(iAtomA);
            int atomicNumber = atomA.getAtomicNumber();
            if (atomicNumber < 1) {
                System.out.println("no atom " + atomicNumber + " " + atomA.getElementName());
                continue;
            }            int useNode = atomA.getEntity() == compound0 ? 1 : 0;
            Point3 pointA = atomA.getPoint(iStruct);
            PPMv ppmV = atomA.getPPM(iStruct);
            double ppm;
            if ((ppmV == null) || !ppmV.isValid()) {
                useNode = 0;
                ppm = 0.0;
            } else {
                ppm = ppmV.getValue();
            }

            AtomNode atomNode = new AtomNode(iAtomA, atomicNumber, ppm, useNode);
            atomGraph.nodes.add(atomNode);
            for (int iAtomB = 0; iAtomB < atoms.size(); iAtomB++) {
                Atom atomB = atoms.get(iAtomB);
                if (atomA != atomB) {
                    Point3 pointB = atomB.getPoint(iStruct);
                    double distance = pointA.distance(pointB);
                    if (distance < limit) {
                        int bondDis = atomA.isBonded(atomB) ? 1 : 0;
                        AtomEdge atomEdge = new AtomEdge(iAtomA, iAtomB, distance, bondDis);
                        atomGraph.edges.add(atomEdge);
                    }
                }
            }
        }
        return atomGraph;
    }

    public void generate(Molecule molecule, double limit, int iStruct) {
        List<Compound> residueList = new ArrayList<>();
        residueList.addAll(molecule.getPolymers().stream()
                .flatMap(polymer -> polymer.getResidues()
                        .stream().map(residue -> (Compound) residue)).toList());
        residueList.addAll(molecule.getLigands());
        for (Compound residueA : residueList) {
            List<Compound> compounds = new ArrayList<>();
            compounds.add(residueA);
            for (Compound residueB : residueList) {
                if (residueA != residueB) {
                    if (residueA.overlaps(residueB, limit, iStruct)) {
                        compounds.add(residueB);
                    }
                }
            }
            atomGraphs.add(getNodesAndEdges(compounds, iStruct, limit));
        }
    }

    public void dumpGraphs(String nodeFileName, String edgeFileName) throws IOException {
        String nodeHeader = "graph_id,node_id,node_type,label,mask\n";
        String edgeHeader = "graph_id,source,target,weight,type\n";
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
