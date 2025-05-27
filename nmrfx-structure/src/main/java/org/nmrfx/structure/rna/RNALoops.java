package org.nmrfx.structure.rna;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class RNALoops {

    private static final List<RNALoops> RNA_LOOPS_LIST = new ArrayList<>();
    String name;
    Pattern regex;
    String[] suites;
    String chi;

    List<int[]> basePairs = new ArrayList<>();
    List<String[]> hbAtomNames = new ArrayList<>();
    List<String> repSeqs;

    RNALoops(String name, String regexStr, String suiteStr, String chi) {
        this.name = name;
        this.regex = Pattern.compile(regexStr);
        int nSuites = suiteStr.length() / 2;
        this.suites = new String[nSuites];
        for (int i =0;i< nSuites;i++) {
            suites[i] = suiteStr.substring(i * 2, i * 2 + 2);
        }
    }

    public String getName() {
        return name;
    }

    public String[] getSuites() {
        return suites;
    }

    public List<int[]> getBasePairs() {
        return basePairs;
    }

    static void init() {
        RNALoops loop1 = new RNALoops("RMSA", "^(CGAGAG|UACCAA)", "5d1a1a1a1a", "AAAA");
        loop1.repSeqs = List.of("CGAGAG", "UACCAA");
        RNA_LOOPS_LIST.add(loop1);

        RNALoops loop2 = new RNALoops("GNRA", "^[AGUC]G[AGUC][AG][AGUC][AGUC]", "1a1a1g1a1a1c", "AAAA");
        loop2.repSeqs = List.of("CGCAAG", "CGCGAG", "UGAAAA", "CGAAGG", "GGCAAC", "CGGGAG", "GGUGAC", "UGAGAG",
                "GGCGAC", "AGCAAU", "CGUAGG", "CGAGUG", "CUCACG", "UGUGAA", "CGAAAG", "CGUGAG", "CCAAAG", "CUAACG",
                "AAAAAU", "UGAAAG", "GGAGAC", "GGUAAC", "AGAAAU", "CGCAGG", "UGCAAG", "AGUGAU GGAAAC", "CGUAAG", "UUAGCG");
        loop2.basePairs.add(new int[]{2, 5});
        loop2.hbAtomNames.addAll(List.of(new String[]{"2.N2", "5.N7"}, new String[]{"2.O2", "4.N7"}, new String[]{"2.N2", "5.OP2"}));
        RNA_LOOPS_LIST.add(loop2);

        RNALoops loop3 = new RNALoops("UWCG", "^[GC]U[AU]CG[GC][AGUC]", "1a1a1z2[6n1c", "AAAS");
        loop3.repSeqs = List.of("GUACGG", "GUUCGC", "CUCCGG", "GAUCGC", "CUUCGG", "AUUUGU");
        loop3.basePairs.add(new int[]{2, 5});
        loop3.hbAtomNames.addAll(List.of(new String[]{"2.O5", "5.O6"}, new String[]{"2.O2", "5.O6"}));
        RNA_LOOPS_LIST.add(loop3);

        RNALoops loop4 = new RNALoops("AUNA", "^GAU[AGUC]AC", "1a1z2[4b6n", "AAAA");
        loop4.repSeqs = List.of("GAUUAC", "GAUCAC", "GAUAAC", "GAUGAC", "CAACAG");
        RNA_LOOPS_LIST.add(loop4);

        RNALoops loop5 = new RNALoops("YGAR", "^U[CU]GA[AG]G", "1g..1a..2a", "ASAA");
        loop5.repSeqs = List.of("UCGAAG", "UUGAGG");
        loop5.hbAtomNames.add(new String[]{"3.O2", "4.O4"});
        RNA_LOOPS_LIST.add(loop5);

        RNALoops loop6 = new RNALoops("UGGU", "^[GU]UGGU[AC]", "1a..4b....", "AAAA");
        loop6.repSeqs = List.of("GUGGUC", "UUGGUA");
        loop6.hbAtomNames.addAll(List.of(new String[]{"2.O2", "4.OP1"}, new String[]{"5.OP1", "6.O2"},
                new String[]{"4.N2", "5.O4"}, new String[]{"4.O2", "5.OP2"}));
        RNA_LOOPS_LIST.add(loop6);
    }

    public static Optional<RNALoops> getRNALoop(String sequence) {
        if (RNA_LOOPS_LIST.isEmpty()) {
            init();
        }
        var rnaLoopOpt = RNA_LOOPS_LIST.stream().filter(r -> r.repSeqs.contains(sequence)).findFirst();
        if (rnaLoopOpt.isEmpty()) {
            rnaLoopOpt = RNA_LOOPS_LIST.stream().filter(r -> r.regex.matcher(sequence).matches()).findFirst();
        }
        return rnaLoopOpt;
    }

}
