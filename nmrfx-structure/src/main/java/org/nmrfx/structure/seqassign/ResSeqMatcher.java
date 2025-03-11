package org.nmrfx.structure.seqassign;

import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.MatchingAlgorithm;
import org.jgrapht.alg.matching.MaximumWeightBipartiteMatching;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ResSeqMatcher {
    List<Matching> initMatches = new ArrayList<>();
    double[][] residueScores;
    double[][] adjScores;

    List<List<Integer>> residueSysList = new ArrayList<>();
    List<List<Integer>> sysResidueList = new ArrayList<>();

    Matching bestMatching = null;


    int nResidues;
    int nSys;

    int nProline;

    boolean[] possibleResidues;
    Map<Residue, Integer> residueIndexMap = new HashMap<>();
    List<Residue> residueList = new ArrayList<>();
    Random random = new Random();
    PartionedGraph paritionedGraph = null;
    SeqGeneticAlgorithm seqGeneticAlgorithm;
    Consumer<SeqGeneticAlgorithm.Progress> updateConsumer;

    AtomicBoolean stopWork = new AtomicBoolean(false);

    public void setUpdater(Consumer<SeqGeneticAlgorithm.Progress> consumer) {
        this.updateConsumer = consumer;
    }

    record PartionedGraph(SimpleWeightedGraph<Integer, DefaultWeightedEdge> simpleGraph,
                          Set<Integer> partition1, Set<Integer> partition2) {
    }

    public List<List<Integer>> getSysResidueList() {
        return sysResidueList;
    }

    public List<List<Integer>> getResidueSysList() {
        return residueSysList;
    }

    public void compareMatrix(SpinSystems spinSystems) {
        List<SpinSystem> spinSystemList = spinSystems.getSystems();
        int i = 0;

        for (SpinSystem spinSysA : spinSystemList) {
            spinSysA.setIndex(i++);
        }

        nSys = spinSystemList.size();
        spinSystems.compare();
        adjScores = new double[nSys][nSys];
        for (SpinSystem spinSysA : spinSystemList) {
            for (SpinSystemMatch spinSystemMatch : spinSysA.spinMatchS) {
                int iSys = spinSysA.getIndex();
                int jSys = spinSystemMatch.spinSystemB.getIndex();
                adjScores[iSys][jSys] = spinSystemMatch.getScore();
                adjScores[jSys][iSys] = spinSystemMatch.getScore();
            }
        }
        Molecule molecule = Molecule.getActive();

        nResidues = 0;
        nProline = 0;
        List<Integer> prolinePos = new ArrayList<>();
        residueIndexMap.clear();
        residueList.clear();
        for (Polymer polymer : molecule.getPolymers()) {
            for (Residue residue : polymer.getResidues()) {
                if (residue.getName().equalsIgnoreCase("pro")) {
                    prolinePos.add(nResidues);
                    nProline++;
                }
                residueIndexMap.put(residue, nResidues++);
                residueList.add(residue);
                residueSysList.add(new ArrayList<>());
            }
        }
        possibleResidues = new boolean[nResidues];
        Arrays.fill(possibleResidues, true);
        for (int iRes : prolinePos) {
            possibleResidues[iRes] = false;
        }
        int nPossibleRes = nResidues - nProline;
        double priorScale = 1.0 / nPossibleRes;
        residueScores = new double[nResidues][nSys];
        for (SpinSystem spinSystem : spinSystemList) {
            List<ResidueSeqScore> residueSeqScores = spinSystem.score();
            List<Integer> resList = new ArrayList<>();
            sysResidueList.add(resList);
            for (var residueSeqScore : residueSeqScores) {
                Residue residue = residueSeqScore.getFirstResidue();
                int iSys = spinSystem.getIndex();
                int iRes = residueIndexMap.get(residue);
                if (possibleResidues[iRes]) {
                    var sysList = residueSysList.get(iRes);
                    sysList.add(iSys);
                    resList.add(iRes);
                    residueScores[iRes][iSys] = residueSeqScore.getScore() * priorScale;
                }
            }
        }
        for (int iSys = 0; iSys < nSys; iSys++) {
            double sum = 0.0;
            for (int iRes = 0; iRes < nResidues; iRes++) {
                sum += residueScores[iRes][iSys];
            }
            for (int iRes = 0; iRes < nResidues; iRes++) {
                residueScores[iRes][iSys] /= sum;
            }
        }
        double ecsMin = -50.0;
        double ecsMax = 100.0;
        for (int iSys = 0; iSys < nSys; iSys++) {
            for (int iRes = 0; iRes < nResidues; iRes++) {
                if (residueScores[iRes][iSys] < 1.0e-6) {
                    residueScores[iRes][iSys] = ecsMax;
                } else {
                    residueScores[iRes][iSys] = Math.min(ecsMax, ecsMin / Math.log(nResidues) * Math.log(residueScores[iRes][iSys] * nResidues));
                }
            }
        }
    }

    public double score(int[] sysToRes, int[] resToSys) {
        double score = 0.0;
        for (int iSys = 0; iSys < nSys; iSys++) {
            int iRes = sysToRes[iSys];
            if ((iRes >= 0) && (iRes < nResidues)) {
                double resScore = residueScores[iRes][iSys];
                score += resScore;

                int nextRes = iRes + 1;
                if (nextRes < nResidues) {
                    int nextSys = resToSys[nextRes];
                    if ((nextSys >= 0) && (nextSys < nSys)) {
                        double adjScore = adjScores[iSys][nextSys];
                        if (adjScore > 100.0) {
                            adjScore += 200.0;
                        }
                        score += adjScore;
                    }
                }
            }
        }
        return score;
    }

    public double score(Residue residue, int i, int j) {
        int iRes = residueIndexMap.get(residue);
        double resScore = residueScores[iRes][i];
        double adjScore = adjScores[i][j];
        return resScore + adjScore;
    }
    public String reportScore(Residue residue, int i, int j) {
        int iRes = residueIndexMap.get(residue);
        double resScore = residueScores[iRes][i];
        double adjScore = adjScores[i][j];
        return i + " " + j + " " + iRes + " " + resScore + " " + adjScore + " " + (resScore + adjScore);
    }

    public double matcher(int[] sysToRes) {
        int[] resToSys = new int[nResidues];
        Arrays.fill(resToSys, -1);
        for (int iSys = 0; iSys < nSys; iSys++) {
            int iRes = sysToRes[iSys];
            if ((iRes >= 0) && (iRes < nResidues)) {
                resToSys[iRes] = iSys;
            }
        }
        return matcher(sysToRes, resToSys);
    }

    public double matcher(int[] sysToRes, int[] resToSys) {
        return score(sysToRes, resToSys);
    }

    public void guessMatch() {
        boolean[] usedRes = new boolean[nResidues];
        int[] sysToRes = new int[sysResidueList.size()];
        Arrays.fill(sysToRes, -1);
        int[] resToSys = new int[nResidues];
        Arrays.fill(sysToRes, -1);
        int iSys = 0;
        for (List<Integer> possibleResidues : sysResidueList) {
            List<Integer> shuffledResidues = new ArrayList<>(possibleResidues);
            shuffledResidues.add(-1);
            Collections.shuffle(shuffledResidues);
            for (int iRes : shuffledResidues) {
                if ((iRes == -1) || (!usedRes[iRes])) {
                    sysToRes[iSys] = iRes;
                    if (iRes != -1) {
                        resToSys[iRes] = iSys;
                        usedRes[iRes] = true;
                    }
                    break;
                }
            }
            iSys++;
        }
        double score = matcher(sysToRes, resToSys);
        System.out.println("global score " + score);
    }

    void updateProgress(SeqGeneticAlgorithm.Progress value) {
        if (updateConsumer != null) {
            updateConsumer.accept(value);
        }
    }
    private void assignFragment(SpinSystem startSys, int nRes, Residue startResidue, double score) {
        var fragmentOpt = startSys.getFragment();
        final Residue residue = startResidue;
        final double pScore = score;
        fragmentOpt.ifPresent(seqFragment -> {
            ResidueSeqScore resScore = new ResidueSeqScore(residue, nRes, pScore);
            seqFragment.setResSeqScore(resScore);
            seqFragment.freezeFragment(resScore);
            seqFragment.dump();
        });

    }

    public void assignMatches(SpinSystems spinSystems) {
        for (SpinSystem spinSystem : spinSystems.getSystems()) {
            spinSystem.confirmP().ifPresent(spinSystemMatch -> spinSystem.unconfirm(spinSystemMatch, true));
            spinSystem.confirmS().ifPresent(spinSystemMatch -> spinSystem.unconfirm(spinSystemMatch, false));
        }
        int[] residues = new int[nResidues];
        Arrays.fill(residues, -1);
        for (int i = 0; i < nSys; i++) {
            int iRes = bestMatching.matches[i];
            if ((iRes >= 0) && (iRes < nResidues)) {
                residues[iRes] = i;
            }
        }
        int startRes = -1;
        Residue startResidue = null;
        SpinSystem startSys = null;
        Optional<SeqFragment> fragmentOpt = Optional.empty();
        double score = 0.0;
        for (int i = 1; i < nResidues; i++) {
            int iSys = residues[i - 1];
            int jSys = residues[i];
            SpinSystem spinSystemA = iSys != -1 ? spinSystems.get(iSys) : null;
            SpinSystem spinSystemB = jSys != -1 ? spinSystems.get(jSys) : null;
            boolean ok = true;
            double residueScore = 0.0;
            Residue residue = residueList.get(i);
            if ((spinSystemA == null) || (spinSystemB == null) || !spinSystemA.getMatchToNext(spinSystemB).isPresent()) {
                ok = false;
            } else {
                residueScore = score(residue, iSys, jSys);
                if (residueScore >= 10.0) {
                    ok = false;
                    String scoreReport = reportScore(residue, iSys, jSys);
                    System.out.println(scoreReport);
                }
            }
            if (ok) {
                if (spinSystemA.getMatchToNext(spinSystemB).isEmpty()) {
                    System.out.println("no match " + i + " " + iSys + " " + jSys);
                }
                if (startRes == -1) {
                    startRes = i - 1 - 1;
                    startSys = spinSystemA;
                    startResidue = residueList.get(startRes);
                }
                score += residueScore;
                Optional<SpinSystemMatch> spinSystemMatchOpt = spinSystemB.getMatchToPrevious(spinSystemA);
                if (!spinSystemMatchOpt.isPresent()) {
                    spinSystemMatchOpt = spinSystemA.compare(spinSystemB, false);
                }
                spinSystemMatchOpt.ifPresent(spinSystemMatch -> spinSystemA.confirm(spinSystemMatch, false));
            } else {
                if (startResidue != null) {
                    final int nRes = i - startRes + 1;
                    assignFragment(startSys, nRes, startResidue, score);
                }
                startRes = -1;
                startResidue = null;
                startSys = null;
                score = 0.0;
            }
        }
        if (startResidue != null) {
            final int nRes = nResidues - startRes;
            assignFragment(startSys, nRes, startResidue, score);
        }
    }

    public record Matching(double score, int[] matches) {
    }

    public void stopWork() {
        stopWork.set(true);
    }
    public double graphMatch(int nTries, SeqGenParameters seqGenParameters) {
        bestMatching = null;
        List<Matching> matchings = new ArrayList<>();
        stopWork.set(false);

        List<Matching> currentBestMatchings = new ArrayList<>();
        int nStart = 5;
        for (int i = 0; i < nStart; i++) {
            Matching matching = runGraphGenetics(seqGenParameters, Collections.emptyList(), (v) -> updateProgress((SeqGeneticAlgorithm.Progress) v));
            System.out.println("matching " + matching.score);
            matchings.add(matching);
            currentBestMatchings.add(matching);
            if ((bestMatching == null) || (matching.score < bestMatching.score)) {
                bestMatching = matching;
            }
        }
        for (int i = 0; i < nTries-nStart; i++) {
            Matching matching = runGraphGenetics(seqGenParameters, currentBestMatchings, (v) -> updateProgress((SeqGeneticAlgorithm.Progress) v));
            System.out.println("matching " + matching.score);
            matchings.add(matching);
            currentBestMatchings.add(matching);
            if ((bestMatching == null) || (matching.score < bestMatching.score)) {
                bestMatching = matching;
            }
            if (stopWork.get()) {
                break;
            }
        }
        int[] count = new int[nSys];
        int[] bestMatches = bestMatching.matches;
        System.out.println(bestMatching.score);
        for (int i = 0; i < matchings.size(); i++) {
            int[] current = matchings.get(i).matches;
            for (int j = 0; j < count.length; j++) {
                if (current[j] == bestMatches[j]) {
                    count[j]++;
                }
            }
        }
        return bestMatching.score;
    }

    public void genInitMatches(int nInitial, int nReplace) {
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> simpleGraph = paritionedGraph.simpleGraph;
        double ranFrac = 0.0;
        List<Integer> replaceList = new ArrayList<>();
        for (int i=0;i<nInitial;i++) {
            replaceList.add(i);
        }
        boolean fillFirst = initMatches.isEmpty();
        Collections.shuffle(replaceList);
        int n = initMatches.isEmpty() ? nInitial : nReplace;
        for (int iTry = 0; iTry < n; iTry++) {
            setGraphWeights(simpleGraph, ranFrac);
            var matcher = new MaximumWeightBipartiteMatching<>(simpleGraph,
                    paritionedGraph.partition1, paritionedGraph.partition2);
            MatchingAlgorithm.Matching<Integer, DefaultWeightedEdge> matchResult = matcher.getMatching();
            List<Integer> matches = getMatches(matchResult);
            int[] sysToRes = new int[nSys];
            for (int i = 0; i < nSys; i++) {
                sysToRes[i] = matches.get(i);
            }
            double score = matcher(sysToRes);
            Matching matching = new Matching(score, sysToRes);
            if (fillFirst) {
                initMatches.add(matching);
            } else {
                initMatches.set(replaceList.get(iTry), matching);
            }
            ranFrac = 0.1;
            System.out.println("try " + iTry + " " + score);
        }

    }
    public Matching runGraphGenetics(SeqGenParameters seqGenParameters, List<Matching> currentBestMatchings, Consumer consumer) {
        buildGraph();
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> simpleGraph = paritionedGraph.simpleGraph;
        int nInitial = seqGenParameters.populationSize();
        System.out.println("run gen");
        genInitMatches(nInitial, nInitial / 5);
        int i = 0;
        for (Matching currentBestMatching : currentBestMatchings) {
            initMatches.set(i++, currentBestMatching);
        }

        seqGeneticAlgorithm = new SeqGeneticAlgorithm(this, seqGenParameters);
        SeqGeneticAlgorithm.seqResMatches = sysResidueList;
        Matching seqToResMatch = seqGeneticAlgorithm.apply(initMatches, consumer);
        return seqToResMatch;
    }

    List<Integer> getMatches(MatchingAlgorithm.Matching<Integer, DefaultWeightedEdge> matchResult) {
        var simpleGraph = matchResult.getGraph();
        List<Integer> result = new ArrayList<>();
        int[] sysToRes = new int[nSys];
        Arrays.fill(sysToRes, -1);
        matchResult.getEdges()
                .forEach(edge -> {
                    int sys = simpleGraph.getEdgeSource(edge);
                    int res = simpleGraph.getEdgeTarget(edge) - nSys;
                    sysToRes[sys] = res;
                });
        for (int iSeq = 0; iSeq < nSys; iSeq++) {
            int iRes = sysToRes[iSeq];
            result.add(iRes);
        }
        return result;
    }

    private void buildGraph() {
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> simpleGraph
                = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        Set<Integer> partition1 = new HashSet<>();
        Set<Integer> partition2 = new HashSet<>();
        for (int i = 0; i < nSys; i++) {
            simpleGraph.addVertex(i);
            partition1.add(i);
        }
        int nTotal = nSys + nResidues;
        for (int i = 0; i < nTotal; i++) {
            simpleGraph.addVertex(i + nSys);
            partition2.add(i + nSys);
        }
        for (int iSys = 0; iSys < nSys; iSys++) {
            for (int iRes : sysResidueList.get(iSys)) {
                DefaultWeightedEdge weightedEdge1 = new DefaultWeightedEdge();
                simpleGraph.addEdge(iSys, iRes + nSys, weightedEdge1);
            }
            DefaultWeightedEdge weightedEdge1 = new DefaultWeightedEdge();
            simpleGraph.addEdge(iSys, nSys + nResidues + iSys, weightedEdge1);
        }
        paritionedGraph = new PartionedGraph(simpleGraph, partition1, partition2);
    }

    private double getTriScore(int iRes, int iSys) {
        double scale = 20.0;
        double score = residueScores[iRes][iSys];
        int nextRes = iRes + 1;
        if (nextRes < nResidues) {
            var nextScoreOpt = residueSysList.get(nextRes).stream().mapToDouble(jSys -> adjScores[iSys][jSys]).sorted().findFirst();
            if (nextScoreOpt.isPresent()) {
                score += nextScoreOpt.getAsDouble() / scale;
            }
        }
        int prevRes = iRes - 1;
        if (prevRes >= 0) {
            var prevScoreOpt = residueSysList.get(prevRes).stream().mapToDouble(jSys -> adjScores[jSys][iSys]).sorted().findFirst();
            if (prevScoreOpt.isPresent()) {
                score += prevScoreOpt.getAsDouble() / scale;
            }
        }
        return score;
    }

    private void setGraphWeights(Graph<Integer, DefaultWeightedEdge> graph, double randomLimit) {
        for (var edge : graph.edgeSet()) {
            int iSys = graph.getEdgeSource(edge);
            int iRes = graph.getEdgeTarget(edge) - nSys;
            double score;
            if (iRes >= nResidues) {
                score = 0.0;
            } else {
                score = getTriScore(iRes, iSys);
            }

            double weight = 500.0 - score;
            if (random.nextDouble() < randomLimit) {
                weight /= 2.0;
            }

            graph.setEdgeWeight(iSys, iRes + nSys, weight);
        }
    }
}
