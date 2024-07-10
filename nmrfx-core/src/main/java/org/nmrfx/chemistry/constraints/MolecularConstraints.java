package org.nmrfx.chemistry.constraints;

import org.nmrfx.chemistry.MoleculeBase;

import java.util.*;

/**
 * @author brucejohnson
 */
public class MolecularConstraints {

    public final MoleculeBase molecule;

    public final Map<String, NoeSet> noeSets = new HashMap<>();
    Optional<NoeSet> activeNOESet = Optional.empty();

    public final Map<String, DistanceConstraintSet> distanceSets = new HashMap<>();
    Optional<DistanceConstraintSet> activeDistanceSet = Optional.empty();

    public final Map<String, AngleConstraintSet> angleSets = new HashMap<>();
    Optional<AngleConstraintSet> activeAngleSet = Optional.empty();

    public final Map<String, RDCConstraintSet> rdcSets = new HashMap<>();
    Optional<RDCConstraintSet> activeRDCSet = Optional.empty();

    public MolecularConstraints(MoleculeBase molecule) {
        this.molecule = molecule;
    }

    public Optional<NoeSet> activeNOESet() {
        return activeNOESet;
    }

    public void activeNOESet(String name) {
        activeNOESet = noeSets.containsKey(name) ? Optional.of(noeSets.get(name)) : Optional.empty();
    }

    public void addNOESet(NoeSet noeSet) {
        noeSets.put(noeSet.getName(), noeSet);
    }

    public NoeSet newNOESet(String name) {
        NoeSet noeSet = NoeSet.newSet(this, name);
        noeSets.put(noeSet.getName(), noeSet);
        activeNOESet(name);
        return noeSet;
    }
    public NoeSet getNoeSet(String name, boolean create) {
        NoeSet set = noeSets.get(name);
        if ((set == null) && create) {
            set = newNOESet(name);
        }
        return set;
    }

    public Collection<NoeSet> noeSets() {
        return noeSets.values();
    }

    public Set<String> getNOESetNames() {
        return noeSets.keySet();
    }

    public void resetNOESets() {
        noeSets.entrySet().forEach((noeSet) -> {
            noeSet.getValue().clear();
        });
        noeSets.clear();
        newNOESet("default");
    }

    public Optional<AngleConstraintSet> activeAngleSet() {
        return activeAngleSet;
    }

    public void activeAngleSet(String name) {
        activeAngleSet = angleSets.containsKey(name) ? Optional.of(angleSets.get(name)) : Optional.empty();
    }

    public void addAngleSet(AngleConstraintSet angleSet) {
        angleSets.put(angleSet.getName(), angleSet);
    }

    public AngleConstraintSet newAngleSet(String name) {
        AngleConstraintSet angleSet = AngleConstraintSet.newSet(this, name);
        angleSets.put(angleSet.getName(), angleSet);
        activeAngleSet(name);
        return angleSet;
    }

    public Collection<AngleConstraintSet> angleSets() {
        return angleSets.values();
    }

    public Set<String> getAngleSetNames() {
        return angleSets.keySet();
    }

    public AngleConstraintSet getAngleSet(String name) {
        return angleSets.get(name);
    }

    public void resetAngleSets() {
        angleSets.entrySet().forEach((cSet) -> {
            cSet.getValue().clear();
        });
        angleSets.clear();
        newAngleSet("default");
    }

    public Optional<RDCConstraintSet> activeRDCSet() {
        return activeRDCSet;
    }

    public void activeRDCSet(String name) {
        activeRDCSet = rdcSets.containsKey(name) ? Optional.of(rdcSets.get(name)) : Optional.empty();
    }

    public void addRDCSet(RDCConstraintSet rdcSet) {
        rdcSets.put(rdcSet.getName(), rdcSet);
    }

    public RDCConstraintSet newRDCSet(String name) {
        RDCConstraintSet rdcSet = RDCConstraintSet.newSet(this, name);
        rdcSets.put(rdcSet.getName(), rdcSet);
        activeRDCSet(name);
        return rdcSet;
    }

    public Set<String> getRDCSetNames() {
        return rdcSets.keySet();
    }

    public RDCConstraintSet getRDCSet(String name) {
        return rdcSets.get(name);
    }

    public void resetRDCSets() {
        rdcSets.entrySet().forEach((cSet) -> {
            cSet.getValue().clear();
        });
        rdcSets.clear();
        newRDCSet("default");
    }

    public Collection<RDCConstraintSet> rdcSets() {
        return rdcSets.values();
    }

    public Optional<DistanceConstraintSet> activeDistanceSet() {
        return activeDistanceSet;
    }

    public void activeDistanceSet(String name) {
        activeDistanceSet = distanceSets.containsKey(name) ? Optional.of(distanceSets.get(name)) : Optional.empty();
    }

    public void addDistanceSet(DistanceConstraintSet distanceSet) {
        distanceSets.put(distanceSet.getName(), distanceSet);
    }

    public DistanceConstraintSet newDistanceSet(String name) {
        DistanceConstraintSet distanceSet = DistanceConstraintSet.newSet(this, name);
        distanceSets.put(distanceSet.getName(), distanceSet);
        activeDistanceSet(name);
        return distanceSet;
    }

    public Collection<DistanceConstraintSet> distanceSets() {
        return distanceSets.values();
    }

    public Set<String> getDistanceSetNames() {
        return distanceSets.keySet();
    }

    public DistanceConstraintSet getDistanceSet(String name, boolean create) {
        DistanceConstraintSet set = distanceSets.get(name);
        if ((set == null) && create) {
            set = newDistanceSet(name);
        }
        return set;
    }

    public void resetDistanceSets() {
        distanceSets.entrySet().forEach((cSet) -> {
            cSet.getValue().clear();
        });
        distanceSets.clear();
        newDistanceSet("default");
    }

}
