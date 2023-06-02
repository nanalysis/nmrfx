/*
 * MX - Essential Cheminformatics
 *
 * Copyright (c) 2007-2009 Metamolecular, LLC
 *
 * http://metamolecular.com/mx
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.nmrfx.structure.chemistry.ring;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Bond;
import org.nmrfx.chemistry.ITree;
import org.nmrfx.chemistry.Ring;

import java.util.*;

/**
 * @author Richard L. Apodaca <rapodaca at metamolecular.com>
 */


public class HanserRingFinder implements RingFinder {

    private List<Ring> rings;
    private int maxRingSize;

    public HanserRingFinder() {
        rings = new ArrayList<Ring>();
        maxRingSize = -1;
    }

    public void setMaximumRingSize(int max) {
        this.maxRingSize = max;
    }

    public int getMaximumRingSize() {
        return this.maxRingSize;
    }

    public List<Bond> getAllEdges(List<Ring> rings) {
        List<Bond> edges = new ArrayList<>();
        for (Ring ring : rings) {
            int ringSize = ring.size();
            for (int i = 0; i < ringSize; i++) {
                Atom atom1 = ring.getAtom(i);
                Atom atom2 = ring.getAtom(i + 1);
                // for ring closures (like in phe) need to check both atoms
                // for the bond
                Optional<Bond> bondOpt = atom1.getBond(atom2);
                if (!bondOpt.isPresent()) {
                    bondOpt = atom2.getBond(atom1);
                }
                if (bondOpt.isPresent()) {
                    Bond bond = bondOpt.get();
                    if (!edges.contains(bond)) {
                        edges.add(bond);
                    }
                }
            }
        }
        return edges;
    }

    public List<List<Boolean>> generateEdgeMap(List<Ring> rings, List<Bond> edges) {
        List<List<Boolean>> edgeMap = new ArrayList<>();

        for (int i = 0; i < rings.size(); i++) {
            List<Boolean> ringEdgeMap = new ArrayList<>();
            for (int u = 0; u < edges.size(); u++) {  // Initialize all values to false
                ringEdgeMap.add(Boolean.FALSE);
            }
            Ring ring = rings.get(i);
            int ringSize = ring.size();
            for (int j = 0; j < ringSize; j++) {
                Atom atom1 = ring.getAtom(j);
                Atom atom2 = ring.getAtom(j + 1);
                // for ring closures (like in phe) need to check both atoms
                // for the bond
                Optional<Bond> bondOpt = atom1.getBond(atom2);
                if (!bondOpt.isPresent()) {
                    bondOpt = atom2.getBond(atom1);
                }
                if (bondOpt.isPresent()) {
                    Bond bond = bondOpt.get();
                    ringEdgeMap.set(edges.indexOf(bond), Boolean.TRUE);
                }
            }
            edgeMap.add(i, ringEdgeMap);
        }
        return edgeMap;
    }

    public List<Ring> removeLargeRings(List<List<Boolean>> edgeMap) {
        List<Boolean> removeRing = new ArrayList();
        for (int i = rings.size() - 1; i > 1; i--) { // No need to look at first two rings
            List<Boolean> largeRow = edgeMap.get(i);
            Ring largeRing = rings.get(i);
            boolean isLargerRing = false;
            for (int j = 0; j < i - 1; j++) {
                if (isLargerRing) {
                    break;
                }
                List<Boolean> smallRow1 = edgeMap.get(j);
                Ring smallRing1 = rings.get(j);
                for (int k = j + 1; k < i; k++) {
                    if (isLargerRing) {
                        break;
                    }
                    List<Boolean> smallRow2 = edgeMap.get(k);
                    Ring smallRing2 = rings.get(k);
                    int difference = smallRing1.size() + smallRing2.size() - largeRing.size();
                    // The - 1 at the end accounts that each ring has the same atom represented twice. Each size should reflect this with minus 1
                    // -2 - (-1) = -1 
                    if (difference < 0 || difference % 2 != 0) {
                        continue; // Already know the two smaller rings cannot make up larger 
                    }
                    for (int edgeIndex = 0; edgeIndex < largeRow.size(); edgeIndex++) {
                        if (smallRow1.get(edgeIndex) ^ Objects.equals(smallRow2.get(edgeIndex), largeRow.get(edgeIndex))) {
                            isLargerRing = true;
                        } else {
                            isLargerRing = false;
                            break;
                        }
                    }
                }
            }
            removeRing.add(isLargerRing);
        }
        List<Ring> ringSubset = new ArrayList<>();
        for (int i = 0; i < rings.size(); i++) {
            if (i <= 1) {
                ringSubset.add(rings.get(i));
                continue;
            }
            int removeRingIndex = rings.size() - 1 - i;
            if (!removeRing.get(removeRingIndex)) {
                ringSubset.add(rings.get(i));
            }
        }
        return ringSubset;
    }

    public void setAtomRings(List<Atom> atoms) {
        int ringNumber = 0;
        for (Ring ring : rings) {
            ring.setRingNumber(ringNumber);
            List<Atom> ringAtoms = ring.getAtoms();
            for (int i = 0; i < ringAtoms.size() - 1; i++) {
                Atom atom = ringAtoms.get(i);
                List<Ring> rings;
                if (atom.getProperty("rings") == null) {
                    rings = new ArrayList<>();
                } else {
                    rings = (List) atom.getProperty("rings");
                }
                rings.add(ring);
                atom.setProperty("rings", rings);
            }
            ringNumber++;
        }
    }

    public Collection<Ring> findSmallestRings(ITree itree) {
        findRings(itree);

        Collections.sort(rings, new Comparator<Ring>() {
            public int compare(Ring ring1, Ring ring2) {
                return ring1.size() - ring2.size();
            }
        });
        List<Bond> edges = getAllEdges(rings);
        List<List<Boolean>> edgeMap = generateEdgeMap(rings, edges);
        rings = removeLargeRings(edgeMap);

        setAtomRings(itree.getAtomArray());

        return rings;
    }

    public Collection<Ring> findRings(ITree itree) {
        rings.clear();

        PathGraph graph = new PathGraph(itree);

        graph.setMaximumRingSize(maxRingSize);
        List<Atom> atoms = itree.getAtomArray();
        for (Atom atom : atoms) {
            List<PathEdge> edges = graph.remove(atom);
            for (PathEdge edge : edges) {
                Ring ring = new Ring(edge.getAtoms());
                rings.add(ring);
            }
        }
        return rings;
    }
}
