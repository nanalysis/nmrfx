package org.nmrfx.structure.seqassign;

import io.jenetics.*;
import io.jenetics.util.MSeq;
import io.jenetics.util.Seq;
import org.apache.commons.math3.random.MersenneTwister;
import org.nmrfx.structure.seqassign.ResSeqMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * @author Bruce Johnson
 */
public class ConstrainedSwapMutatorResSeq<
        G extends Gene<?, G>,
        C extends Comparable<? super C>
        > extends SwapMutator<G, C> {

    MersenneTwister twister = new MersenneTwister();
    ArrayList<Integer> mutatable = new ArrayList<>();
    ResSeqMatcher resSeqMatcher;

    SeqGeneticAlgorithm seqGeneticAlgorithm;
    List<List<Integer>> sysResidueList;
    List<List<Integer>> resSysList;
    double startProbability;
    double probability;

    public ConstrainedSwapMutatorResSeq(double p) {
        super(p);
        probability = p;
        startProbability = p;
    }

    ConstrainedSwapMutatorResSeq(ResSeqMatcher resSeqMatcher,  SeqGeneticAlgorithm seqGeneticAlgorithm, double p) {
        super(p);
        this.resSeqMatcher = resSeqMatcher;
        this.seqGeneticAlgorithm = seqGeneticAlgorithm;
        // build a list of all the indices of atoms that have more than 1 peak match
        int i = 0;
        sysResidueList = resSeqMatcher.getSysResidueList();
        resSysList = resSeqMatcher.getResidueSysList();


        for (var matches : sysResidueList) {
            if (matches.size() > 1) {
                mutatable.add(i);
            }
            i++;
        }
        probability = p;
        startProbability = p;
    }

    int getIndex(Object o) {
        EnumGene gene = (EnumGene) o;
        return (Integer) gene.allele();
    }

    private synchronized void updateProbability(int nGenes, long generation) {
        int nGenerations = seqGeneticAlgorithm.getNGenerations();
        probability = 0.1 / (2.0 + generation * (nGenes - 2.0) / (nGenerations - 1.0));
    }


    @Override
    public AltererResult alter(Seq population, long generation) {
        alterMe(population, generation);
        return super.alter(population, generation);
    }

    public void alterMe(Seq<Phenotype> population, long generation) {
        int nGenes = population.size();
        updateProbability(nGenes, generation);
    }

    @Override
    protected MutatorResult<Chromosome<G>> mutate(Chromosome<G> chromosome, double mProbability, RandomGenerator random) {

        int nGenes = chromosome.length();
        int[] sysToRes = new int[nGenes];
        int[] resToSys = new int[nGenes];
        Arrays.fill(sysToRes, -1);
        Arrays.fill(resToSys, -1);
        for (int i = 0;i<chromosome.length();i++) {
            int jCurrent = getIndex(chromosome.get(i));
            resToSys[jCurrent] = i;
            sysToRes[i] = jCurrent;
        }
        int nSwap = 0;
        MSeq genes = MSeq.of(chromosome);
        for (int iSite : mutatable) {
            int iRes = sysToRes[iSite];
            if (twister.nextDouble() > probability) {
                continue;
            }
            List<Integer> matches = sysResidueList.get(iSite);
            int k = twister.nextInt(matches.size());
            int jRes = matches.get(k);
            if (jRes < 0) {
                jRes = 0;
            }
            if (jRes != iRes) {
                int jSite = resToSys[jRes];
              //  System.out.println("swap " + iSite + " " + jSite);
                if (jSite != -1) {
                    genes.swap(jSite, iSite);
                    resToSys[iRes] = jSite;
                    resToSys[jRes] = iSite;
                    sysToRes[iSite] = jRes;
                    sysToRes[jSite] = iRes;
                } else {
                    EnumGene<Integer> enumGene1 = ((EnumGene<Integer>) genes.get(iSite));
                    EnumGene<Integer> enumGene = enumGene1.newInstance(jRes);
                    genes.set(iSite, enumGene);
                    resToSys[jRes] = iSite;
                    sysToRes[iSite] = jRes;
                }
                nSwap++;
            }
        }
        final MutatorResult<Chromosome<G>> result;
        if (nSwap > 0) {
            result = new MutatorResult<>(
                    chromosome.newInstance(genes.toISeq()),
                    nSwap
            );
        } else {
            result = new MutatorResult<>(chromosome, nSwap);
        }
        return result;
    }
}
