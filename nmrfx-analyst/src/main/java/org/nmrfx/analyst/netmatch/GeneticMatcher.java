package org.nmrfx.analyst.netmatch;

import org.apache.commons.math3.genetics.*;

/**
 *
 * @author brucejohnson
 */
public class GeneticMatcher {

    CrossoverPolicy xPolicy = new OrderedCrossover();
    SelectionPolicy sPolicy = new TournamentSelection(2);
    int maxGenerations;
    double stopFitness;
    double bestFitness = 0.0;
    int bestGeneration = 0;
    GeneticMatcherAlgorithm genAlg;
    final int chromoLength;
    StoppingCondition cond = new StoppingCondition() {
        @Override
        public boolean isSatisfied(Population pltn) {
            boolean done = false;
            int currentGeneration = genAlg.getGenerationsEvolved();
            if (currentGeneration > maxGenerations) {
                done = true;
            }
            double currentFitness = pltn.getFittestChromosome().getFitness();
            if ((stopFitness != 0) && (currentFitness > stopFitness)) {
                done = true;
            }
            if (currentFitness > bestFitness) {
                bestFitness = currentFitness;
                bestGeneration = genAlg.getGenerationsEvolved();
            }
            if ((currentGeneration - bestGeneration) > 0.1 * maxGenerations) {
                done = true;
            }
            double rate = 1.0 / (2.0 + (chromoLength - 2.0) / (maxGenerations - 1) * currentGeneration);
            genAlg.setMutationRate(rate);
            return done;
        }
    };

    public GeneticMatcher(MutationPolicy mPolicy, double stopFitness, int maxGenerations, int chromoLength) {
        double crossoverRate = 0.05;
        double mutationRate = 0.4;
        this.chromoLength = chromoLength;
        genAlg = new GeneticMatcherAlgorithm(xPolicy, crossoverRate, mPolicy, mutationRate, sPolicy);
        this.maxGenerations = maxGenerations;
        this.stopFitness = stopFitness;
    }

    public Population evolve(Population population) {
        return genAlg.evolve(population, cond);
    }

    public int generations() {
        return genAlg.getGenerationsEvolved();
    }
}
