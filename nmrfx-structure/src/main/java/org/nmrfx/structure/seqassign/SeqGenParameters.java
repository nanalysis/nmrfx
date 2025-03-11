package org.nmrfx.structure.seqassign;

public class SeqGenParameters {
    private int populationSize = 500;
    private double mutationRate = 0.1;
    private double crossoverRate = 0.1;
    private int eliteNumber = 100;
    private int maximumPhenoTypeAge = 50;

    private int steadyLimit = 200;
    private int multiMaxLimit = 30;
    private int nGenerations = 2000;

    public SeqGenParameters() {

    }

    public SeqGenParameters(int populationSize, int nGenerations, double mutationRate, double crossoverRate, int eliteNumber,
                     int maximumPhenoTypeAge, int steadyLimit, int multiMaxLimit) {
        this.nGenerations = nGenerations;
        this.populationSize = populationSize;
        this.mutationRate = mutationRate;
        this.crossoverRate = crossoverRate;
        this.eliteNumber = eliteNumber;
        this.maximumPhenoTypeAge = maximumPhenoTypeAge;
        this.steadyLimit = steadyLimit;
        this.multiMaxLimit = multiMaxLimit;

    }

    public int populationSize() {
        return populationSize;
    }

    public double mutationRate() {
        return mutationRate;
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

    public int multiMaxLimit() {
        return multiMaxLimit;
    }

    public int nGenerations() {
        return nGenerations;
    }
}
