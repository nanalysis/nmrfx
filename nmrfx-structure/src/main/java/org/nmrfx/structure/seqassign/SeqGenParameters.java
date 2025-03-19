package org.nmrfx.structure.seqassign;

public class SeqGenParameters {
    private int populationSize = 500;
    private double mutationRate = 0.1;

    private boolean mutationProfile = true;
    private double crossoverRate = 0.1;
    private int eliteNumber = 100;
    private int maximumPhenoTypeAge = 50;

    private int steadyLimit = 200;
    private int nGenerations = 2000;
    private double sdevRatio = 1.5;

    public SeqGenParameters() {

    }

    public SeqGenParameters(int populationSize, int nGenerations, double mutationRate, boolean mutationProfile, double crossoverRate, int eliteNumber,
                     int maximumPhenoTypeAge, int steadyLimit) {
        this.nGenerations = nGenerations;
        this.populationSize = populationSize;
        this.mutationRate = mutationRate;
        this.mutationProfile = mutationProfile;
        this.crossoverRate = crossoverRate;
        this.eliteNumber = eliteNumber;
        this.maximumPhenoTypeAge = maximumPhenoTypeAge;
        this.steadyLimit = steadyLimit;
    }

    public int populationSize() {
        return populationSize;
    }

    public double mutationRate() {
        return mutationRate;
    }
    public boolean mutationProfile() {
        return mutationProfile;
    }

    public double crossoverRate() {
        return crossoverRate;
    }

    public int eliteNumber() {
        return eliteNumber;
    }

    public int maximumPhenoTypeAge() {
        return maximumPhenoTypeAge;
    }

    public int steadyLimit() {
        return steadyLimit;
    }

    public int nGenerations() {
        return nGenerations;
    }
}
