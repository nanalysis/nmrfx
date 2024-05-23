package org.nmrfx.analyst.netmatch;

import io.jenetics.Chromosome;
import io.jenetics.Phenotype;
import io.jenetics.EnumGene;

import java.util.function.Predicate;

/**
 * @author Bruce Johnson
 */
public class MatchValidator implements Predicate {

    @Override
    public boolean test(Object t) {
        Phenotype pheno = (Phenotype) t;
        pheno.genotype().chromosome();
        Chromosome chromo = pheno.genotype().chromosome();
        int nGenes = chromo.length();
        for (int i = 0; i < nGenes; i++) {
            int index = getIndex(chromo.get(i));
            if (index < 0) {
                System.out.println(" bad " + i + " " + index + " " + chromo);
                return false;
            }

        }
        return true;
    }

    public static boolean isValid(Phenotype t) {
        Phenotype pheno = t;
        Chromosome chromo = pheno.genotype().chromosome();
        int nGenes = chromo.length();
        for (int i = 0; i < nGenes; i++) {
            int index = getIndex(chromo.get(i));
            if (index < 0) {
                System.out.println(" bad " + i + " " + index + " " + chromo);
                return false;
            }

        }
        return true;
    }

    static int getIndex(Object o) {
        EnumGene gene = (EnumGene) o;
        int iIndex = (Integer) gene.alleleIndex();
        return iIndex;
    }

}
