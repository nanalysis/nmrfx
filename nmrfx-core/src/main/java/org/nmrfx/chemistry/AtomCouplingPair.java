package org.nmrfx.chemistry;

public record AtomCouplingPair(Atom atom1, Atom atom2, double coupling, String couplingName) {
}
