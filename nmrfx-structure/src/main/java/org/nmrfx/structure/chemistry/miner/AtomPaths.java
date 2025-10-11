package org.nmrfx.structure.chemistry.miner;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DefaultManyToManyShortestPaths;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Bond;
import org.nmrfx.chemistry.Entity;

import java.util.ArrayList;
import java.util.List;

public class AtomPaths {

    private AtomPaths() {

    }

    public static DefaultManyToManyShortestPaths<Atom, DefaultEdge> getPathAlgorithm(Entity entity, int pathLimit, int elementTypeA, int elementTypeB) {
        return getPathAlgorithm(entity.getAtoms(), entity.getBondList(), pathLimit, elementTypeA, elementTypeB);
    }

    public static DefaultManyToManyShortestPaths<Atom, DefaultEdge> getPathAlgorithm(List<Atom> atoms, List<Bond> bonds, int pathLimit, int elementTypeA, int elementTypeB) {
        SimpleGraph<Atom, DefaultEdge> simpleGraph
                = new SimpleGraph<>(DefaultEdge.class);
        for (Atom atom : atoms) {
            if (atom.getAtomicNumber() > 0) {
                simpleGraph.addVertex(atom);
            }
        }
        for (Bond bond : bonds) {
            if ((bond.begin.getAtomicNumber() > 0) && (bond.end.getAtomicNumber() > 0)) {
                if (simpleGraph.containsVertex(bond.begin) && simpleGraph.containsVertex(bond.end)) {
                    simpleGraph.addEdge(bond.begin, bond.end);
                }
            }
        }
        var pathAlgorithm = new DefaultManyToManyShortestPaths<Atom, DefaultEdge>(simpleGraph);
        return pathAlgorithm;
    }

    public static List<GraphPath<Atom, DefaultEdge>> getPaths(Entity entity, int pathLimit, int elementTypeA, int elementTypeB) {
        return getPaths(entity.getAtoms(), entity.getBondList(), pathLimit, elementTypeA, elementTypeB);
    }

    public static List<GraphPath<Atom, DefaultEdge>> getPaths(List<Atom> atoms, List<Bond> bonds, int pathLimit, int elementTypeA, int elementTypeB) {
        var pathAlgorithm = getPathAlgorithm(atoms, bonds, pathLimit, elementTypeA, elementTypeB);
        List<GraphPath<Atom, DefaultEdge>> allPaths = new ArrayList<>();
        for (Atom atomA : atoms) {
            if ((atomA.getAtomicNumber() < 1) || ((elementTypeA != -1) && (elementTypeA != atomA.getAtomicNumber()))) {
                continue;
            }
            var paths = pathAlgorithm.getPaths(atomA);
            for (Atom atomB : atoms) {
                if ((atomA == atomB) || (atomB.getAtomicNumber() < 1) || ((elementTypeB != -1) && (elementTypeB != atomB.getAtomicNumber()))) {
                    continue;
                }
                var path = paths.getPath(atomB);
                if ((path != null) && (path.getLength() <= pathLimit)) {
                    allPaths.add(path);
                }
            }
        }
        return allPaths;
    }
}
