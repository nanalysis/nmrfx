package org.nmrfx.structure.chemistry.predict;

import org.jgrapht.alg.shortestpath.DefaultManyToManyShortestPaths;
import org.jgrapht.graph.DefaultEdge;
import org.nmrfx.chemistry.*;
import org.nmrfx.structure.chemistry.JCoupling;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.miner.AtomPaths;
import org.nmrfx.structure.chemistry.energy.ConstraintCreator;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class ResidueAtomDistances {
    List<AtomGraph> atomGraphs = new ArrayList<>();

    public record AtomNode(Atom atom, int index, int property, double ppm, int mask, int iStruct) {
        public String getCSV(int iGraph) {
            String molName = MoleculeFactory.getActive().getName().split("\\.")[0];
            String atomId = molName + "."
                    + atom.getResidueName() + "."
                    + atom.getFullName() + "."
                    + iStruct;
            int aromatic = atom.getFlag(Atom.AROMATIC) ? 1 : 0;
            int hyb = atom.getHybridizationNumber();
            int nNeighbors = atom.getConnected().size();
            Object obj = atom.getProperty("elec");
            double elec = obj == null ? 0.5 : Double.parseDouble(obj.toString());
            elec = (elec - 20) / (60.0 - 20.0);
            obj = atom.getProperty("hard");
            double hard = obj == null ? 0.5 : Double.parseDouble(obj.toString());
            hard = hard / 200.0;

            obj = atom.getProperty("charge");
            double charge = obj == null ? 0.0 : Double.parseDouble(obj.toString());
            return String.format("%d,%d,%d,%d,%d,%d,%.3f,%.3f,%.1f,%.3f,%d,%s", iGraph, index, property, aromatic, hyb, nNeighbors, elec, hard, charge, ppm, mask, atomId);
        }
    }

    public record AtomEdge(List<Integer> iAtomList, double distance, int pathLen, double couplingValue,
                           String couplingName, Atom atomA, Atom atomB, int iStruct, int isBonded) {
        public String getCSV(int iGraph) {
            StringBuilder stringBuilder = new StringBuilder();
            for (Integer i : iAtomList) {
                if (!stringBuilder.isEmpty()) {
                    stringBuilder.append(",");
                }
                stringBuilder.append(i);
            }
            return String.format("%d,%s,%.3f,%d,%.2f,%s,%d", iGraph, stringBuilder, distance, pathLen, couplingValue, couplingName, isBonded);
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

    record CouplingPath(org.jgrapht.GraphPath<Atom, DefaultEdge> path, String couplingName) {
    }

    CouplingPath getPath(DefaultManyToManyShortestPaths<Atom, DefaultEdge> paths,
                         Atom atomA, Atom atomB) {
        org.jgrapht.GraphPath<Atom, DefaultEdge> path;
        try {
            path = paths.getPath(atomA, atomB);
        } catch (IllegalArgumentException illegalArgumentException) {
            path = null;
        }
        String couplingName = "";
        if (path != null) {
            List<Atom> pathAtoms = path.getVertexList();
            Optional<JCoupling.CouplingName> couplingOpt = JCoupling.CouplingName.getCoupling(pathAtoms);
            if (couplingOpt.isPresent()) {
                couplingName = couplingOpt.get().name().trim();
            }
        }
        return new CouplingPath(path, couplingName);
    }

    double getAtomCoupling(Atom atomA, Atom atomB, int pathLen) {
        var atomCouplingPairOpt = atomA.getAtomCouplingPair(atomB);
        double coupling = 0.0;
        if (atomCouplingPairOpt.isPresent()) {
            coupling = atomCouplingPairOpt.get().coupling();
            double jScale = pathLen > 1.1 ? 10.0 : 100.0;
            coupling = coupling / jScale;
        }
        return coupling;
    }

    void addEdge(AtomGraph atomGraph, DefaultManyToManyShortestPaths<Atom, DefaultEdge> paths,
                 List<Atom> atoms, Atom atomA, Atom atomB, double limit, int iStruct) {
        Point3 pointA = atomA.getPoint(iStruct);
        Point3 pointB = atomB.getPoint(iStruct);
        double distance = pointA.distance(pointB);
        if (distance < limit) {
            CouplingPath couplingPath = getPath(paths, atomA, atomB);
            org.jgrapht.GraphPath<Atom, DefaultEdge> path = couplingPath.path;

            int pathLen = Math.min(7, path != null ? path.getLength() : 8);
            double coupling = 0.0;
            List<Atom> vertexList = new ArrayList<>();
            vertexList.add(atomA);
            vertexList.add(atomB);
            if (path != null) {
                var pList = path.getVertexList();
                for (int i = 1; i < pList.size() - 1; i++) {
                    vertexList.add(pList.get(i));
                }
                coupling = getAtomCoupling(atomA, atomB, pathLen);
            }
            List<Integer> iAtomList = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                int index = -1;
                if (i < vertexList.size()) {
                    index = atoms.indexOf(vertexList.get(i));
                }
                iAtomList.add(index);
            }
            distance = (distance - 0.8) / 5.2;
            int isBonded = atomA.isBonded(atomB) ? 1 : 0;

            AtomEdge atomEdge = new AtomEdge(iAtomList, distance, pathLen, coupling, couplingPath.couplingName, atomA, atomB, iStruct, isBonded);
            atomGraph.edges.add(atomEdge);
        }
    }

    AtomGraph getAtomGraph(List<Entity> compounds, DefaultManyToManyShortestPaths<Atom, DefaultEdge> paths,
                           int iStruct, double limit) {
        Entity compound0 = compounds.getFirst();
        List<Atom> atoms = compounds.stream().flatMap(compound -> compound.atoms.stream())
                .filter(atom -> atom.getAtomicNumber() > 0).toList();
        AtomGraph atomGraph = new AtomGraph(new ArrayList<>(), new ArrayList<>());
        for (int iAtomA = 0; iAtomA < atoms.size(); iAtomA++) {
            Atom atomA = atoms.get(iAtomA);
            int atomicNumber = atomA.getAtomicNumber();
            int useNode = atomA.getEntity() == compound0 ? 1 : 0;
            PPMv ppmV = atomA.getPPM(iStruct);
            double ppm;
            if ((ppmV == null) || !ppmV.isValid()) {
                useNode = 0;
                ppm = 0.0;
            } else {
                ppm = ppmV.getValue();
                ppm = GATV2Predictor.normalize(atomicNumber, ppm);
            }

            AtomNode atomNode = new AtomNode(atomA, iAtomA, atomicNumber, ppm, useNode, iStruct);
            atomGraph.nodes.add(atomNode);
            for (int iAtomB = 0; iAtomB < atoms.size(); iAtomB++) {
                Atom atomB = atoms.get(iAtomB);
                if (atomA != atomB) {
                    addEdge(atomGraph, paths, atoms, atomA, atomB, limit, iStruct);
                }
            }
        }
        return atomGraph;
    }

    public void generate(Molecule molecule, DefaultManyToManyShortestPaths<Atom, DefaultEdge> paths, double limit, int iStruct) {
        for (Entity entity : molecule.getEntities()) {
            ConstraintCreator.setupAtomProperties(entity);
        }
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
            atomGraphs.add(getAtomGraph(compounds, paths, iStruct, limit));
        }
    }

    public void generate(List<Entity> compoundList, DefaultManyToManyShortestPaths<Atom, DefaultEdge> paths, double limit, int iStruct) {
        for (Entity entity : compoundList) {
            ConstraintCreator.setupAtomProperties(entity);
        }
        atomGraphs.add(getAtomGraph(compoundList, paths, iStruct, limit));
    }


    public void generate(Entity compound, DefaultManyToManyShortestPaths<Atom, DefaultEdge> paths, double limit, int iStruct) {
        ConstraintCreator.setupAtomProperties(compound);
        List<Entity> compounds = List.of(compound);
        atomGraphs.add(getAtomGraph(compounds, paths, iStruct, limit));
    }

    static class RADWriter {
        FileWriter nodeWriter;
        FileWriter edgeWriter;
        int iGraph = 0;

        RADWriter(FileWriter nodeWriter, FileWriter edgeWriter) throws IOException {
            this.nodeWriter = nodeWriter;
            this.edgeWriter = edgeWriter;
            dumpHeader(nodeWriter, edgeWriter);
        }

        void write(List<AtomGraph> graphs) throws IOException {
            for (AtomGraph atomGraph : graphs) {
                nodeWriter.write(atomGraph.getNodesCSV(iGraph));
                edgeWriter.write(atomGraph.getEdgesCSV(iGraph));
                iGraph++;
            }
        }

        void dumpHeader(FileWriter nodeWriter, FileWriter edgeWriter) throws IOException {
            String nodeHeader = "graph_id,node_id,node_type,aromatic,hybridization,nNeighbors,elec,hard,charge,label,mask,node_name\n";
            String edgeHeader = "graph_id,source,target,node1,node2,node3,weight,nbonds,jvalue,cname,bond\n";
            nodeWriter.write(nodeHeader);
            edgeWriter.write(edgeHeader);
        }
    }

    public static ResidueAtomDistances getRAD(List<Entity> entities, int iStruct) {
        entities.getFirst().molecule.updateAtomArray();
        MoleculeFactory.setActive(entities.getFirst().molecule);

        DefaultManyToManyShortestPaths<Atom, DefaultEdge> paths = AtomPaths.getPathAlgorithm(entities, 6, -1, -1);
        ResidueAtomDistances rad = new ResidueAtomDistances();
        rad.generate(entities, paths, 5.0, iStruct);
        return rad;
    }

    public static void generateMoleculeGraphs(Molecule molecule, List<Integer> istructs, String nodeFileName, String edgeFileName) throws IOException {
        for (Entity entity : molecule.getEntities()) {
            ConstraintCreator.setupAtomProperties(entity);
        }
        try (FileWriter nodeWriter = new FileWriter(nodeFileName); FileWriter edgeWriter = new FileWriter(edgeFileName)) {
            RADWriter radWriter = new RADWriter(nodeWriter, edgeWriter);
            for (int iStruct : istructs) {
                Map<Entity, List<Entity>> compoundListMap = generateCompoundListMap(molecule, iStruct);
                for (var entry : compoundListMap.entrySet()) {
                    ResidueAtomDistances rad = getRAD(entry.getValue(), iStruct);
                    radWriter.write(rad.atomGraphs);
                }
            }
        }
    }

    public static Map<Entity, List<Entity>> generateCompoundListMap(Molecule molecule, int iStruct) {
        List<Compound> compoundList = new ArrayList<>();
        compoundList.addAll(molecule.getPolymers().stream()
                .flatMap(polymer -> polymer.getResidues()
                        .stream().map(Compound.class::cast)).toList());
        compoundList.addAll(molecule.getLigands());

        Map<Entity, List<Entity>> compoundListMap = new HashMap<>();
        double limit = 5.0;
        for (Compound compoundA : compoundList) {
            List<Entity> neighbors = new ArrayList<>();
            neighbors.add(compoundA);
            neighbors.addAll(compoundList.stream().filter(compound -> compound != compoundA)
                    .filter(compound -> compoundA.overlaps(compound, limit, iStruct))
                    .map(Entity.class::cast).toList());
            compoundListMap.put(compoundA, neighbors);
        }
        return compoundListMap;
    }

    public void dumpGraphs(String nodeFileName, String edgeFileName) throws IOException {
        try (FileWriter nodeWriter = new FileWriter(nodeFileName); FileWriter edgeWriter = new FileWriter(edgeFileName)) {
            RADWriter radWriter = new RADWriter(nodeWriter, edgeWriter);
            radWriter.write(atomGraphs);
        }
    }
}
