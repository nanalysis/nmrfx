package org.nmrfx.structure.chemistry.predict;

import org.nmrfx.chemistry.*;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.rna.*;
import org.tribuo.*;
import org.tribuo.impl.ArrayExample;
import org.tribuo.regression.Regressor;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author Bruce Johnson
 */
public class RNAAttributes {

    Map<String, Double> svmRMSMap = new HashMap<>();

    static Map<String, String> attrMap = new HashMap<>();
    static Map<String, RNAStats> statMap = new HashMap<>();
    static List<String> rnaAtomSources = Arrays.asList("C1'", "C2'", "C3'", "C4'", "C5'", "P", "OP1", "OP2", "O2'", "O3'", "O4'", "O5'",
            "AN9", "AC8", "AN7", "AC5", "AC4", "AN3", "AC2", "AN1", "AC6", "AN6",
            "CN1", "CC2", "CO2", "CN3", "CC4", "CN4", "CC5", "CC6",
            "GN9", "GC8", "GN7", "GC5", "GC4", "GN3", "GC2", "GN2", "GN1", "GC6", "GO6",
            "UN1", "UC2", "UO2", "UN3", "UC4", "UO4", "UC5", "UC6");
    static List<String> atoms = Arrays.asList(
            "AC2", "AC8", "GC8", "CC5", "UC5", "CC6", "UC6", "AC1p", "GC1p", "CC1p", "UC1p", "AC2p", "GC2p", "CC2p",
            "UC2p", "AC3p", "GC3p", "CC3p", "UC3p", "AC4p", "AC5p", "GC4p", "GC5p", "CC4p", "CC5p", "UC4p", "UC5p",
            "AH8", "GH8", "AH2", "CH5", "UH5", "CH6", "UH6", "AH1p", "GH1p",
            "CH1p", "UH1p", "AH2p", "GH2p", "CH2p", "UH2p", "AH3p", "GH3p", "CH3p", "UH3p", "GH1", "UH3", "UH4p",
            "UH5p", "UH5pp", "CH4p", "CH41", "CH42", "CH5p", "CH5pp", "AH4p", "AH5p", "AH5pp", "AH61", "AH62",
            "GH21", "GH22", "GH4p", "GH5p", "GH5pp", "AN1", "AN3", "AN6", "AN7", "AN9", "CN1", "CN3", "CN4", "GN1",
            "GN2", "GN7", "GN9", "UN1", "UN3");
    static List<String> attrTypes = Arrays.asList("nuc1", "nuc2", "nuc3", "nuc4", "nuc5", "pos1", "pos2", "pos3", "pos4", "pos5", "nuc");

    static Map<String, Integer> baseTokenMap = Map.of("G", 1, "A", 2, "C", 3, "U", 4);

    static Map<String, String> wcMap = Map.of("G", "C", "C", "G", "A", "U", "U", "A", "X", "", "x", "", "P", "p", "p", "P");
    static Map<String, String> wobbleMap = Map.of("G", "U", "U", "G", "A", "", "C", "", "X", "", "x", "", "P", "p", "p", "P");
    static String[] pats = {"GC", "Pp", "GU", "Pp", "GA", "PP", "GG", "PP", "G-", "P-", "AC", "Pm", "AU", "Pp", "AA", "PP",
            "AG", "PP", "A-", "P-", "CC", "pp", "CU", "pp", "CA", "pm", "CG", "pP", "C-", "p-", "UC", "pp",
            "UU", "pp", "UA", "pP", "UG", "pP", "U-", "p-", "- ", "-"};
    static Map<String, String> patMap = new HashMap<>();

    static {
        for (int i = 0; i < pats.length; i += 2) {
            patMap.put(pats[i], pats[i + 1]);
        }
    }

    static List<String> types = new ArrayList<>();
    static Map<String, Integer> rnaAtomSourceMap = new HashMap<>();

    static {
        for (int i = 0; i < rnaAtomSources.size(); i++) {
            rnaAtomSourceMap.put(rnaAtomSources.get(i), i);
        }
    }

    public static void setTypes(List<String> newTypes) {
        types.clear();
        types.addAll(newTypes);
    }

    public static List<String> getTypes() {
        return types;
    }

    public static List<String> getAtomSources() {
        return rnaAtomSources;
    }

    public static int getAtomSourceIndex(Atom atom) {
        String resName = atom.getEntity().getName();
        String atomName = atom.getName();
        String key = resName + atomName;
        if (atomName.contains("'") || atomName.contains("P")) {
            key = atomName;
        }

        Integer value = rnaAtomSourceMap.get(key);
        return value == null ? -1 : value;
    }

    public static void put(String resName, String attributes) {
        attrMap.put(resName, attributes);
    }

    public static String get(String resName) {
        return attrMap.get(resName);
    }

    public static void putStats(Atom atom, RNAStats stats) {
        statMap.put(atom.getFullName(), stats);
    }

    public static RNAStats getStats(Atom atom) {
        return statMap.get(atom.getFullName());
    }

    public static String get(Atom atom) {
        String attr = "";
        if (atom.getEntity() instanceof Residue residue) {
            String resName = residue.getName();
            String resNum = residue.getNumber();
            String polymerName = residue.getPolymer().getName();
            String name = polymerName + ":" + resName + resNum;
            attr = attrMap.get(name);
            if (attr == null) {
                name = resName + resNum;
                attr = attrMap.get(name);
                if (attr == null) {
                    attr = attrMap.get(resNum);
                }
            }
        }
        return attr;
    }

    public static void clear() {
        attrMap.clear();
    }


    public List<Residue> getSeqList(Molecule molecule) {
        List<Residue> seqList = new ArrayList<>();
        for (Polymer polymer : molecule.getPolymers()) {
            if (polymer.isRNA()) {
                seqList.addAll(polymer.getResidues());
            }
        }
        return seqList;
    }

    static boolean wc(String iName, String jName) {
        return wcMap.get(iName).equals(jName);

    }

    static boolean wobble(String iName, String jName) {
        return wobbleMap.get(iName).equals(jName);

    }

    record RNAPair(int i, Residue residue, Residue partner, int j) {
        boolean wc() {
            boolean wc = false;
            if (partner != null) {
                String iName = residue.getName();
                String jName = partner.getName();
                wc = RNAAttributes.wc(iName, jName);
            }
            return wc;
        }

        boolean wobble() {
            boolean wobble = false;
            if (partner != null) {
                String iName = residue.getName();
                String jName = partner.getName();
                wobble = RNAAttributes.wobble(iName, jName);
            }
            return wobble;
        }

        boolean paired() {
            return wc() || wobble();
        }


        String getPpType() {
            String iName = residue.getName();
            String jName = partner != null ? partner.getName() : "-";
            return patMap.get(iName + jName);
        }

        String getBPType() {
            String iName = residue.getName();
            String jName = partner != null ? partner.getName() : "-";
            return iName + jName;
        }

        int getBaseType() {
            return baseTokenMap.get(residue.getName());
        }

        int getPairType() {
            return partner == null ? 0 : baseTokenMap.get(partner.getName());
        }
    }

    int getNToNextBP(int[] basePairs, int index, int max) {
        int nNucs = basePairs.length;
        int n = 0;
        for (int i = index + 1; i < nNucs; i++) {
            int delta = i - index;

            if ((basePairs[i] != -1) || (delta >= max)) {
                n = delta;
                break;
            }
        }
        return n;
    }

    int getNToPrevBP(int[] basePairs, int index, int max) {
        int n = 0;
        for (int i = index - 1; i >= 0; i--) {
            int delta = index - i;
            if ((basePairs[i] != -1) || (delta >= max)) {
                n = delta;
                break;
            }
        }
        return n;
    }

    String checkNC(int[] basePairs, int index) {
        int nNucs = basePairs.length;
        String type = "-";
        if ((index > 0) && (index < nNucs - 1)) {
            int bp0 = basePairs[index - 1];
            int bp1 = basePairs[index];
            int bp2 = basePairs[index + 1];
            if ((bp0 != -1) && (bp1 != -1) && (Math.abs(bp0 - bp1) != 1)) {
                type = "5prime-nc";
            }
            if ((bp2 != -1) && (bp1 != -1) && (Math.abs(bp2 - bp1) != 1)) {
                type = "3prime-nc";
            }
        }
        return type;
    }

    boolean checkWC(String nuc, boolean strict) {
        boolean isWC = false;
        if (!nuc.equals("-")) {
            boolean matched = true;
            String n1 = nuc.substring(0, 1);
            String n2 = nuc.substring(1, 2);
            if (n1.equals("-") || n2.equals("-")) {
                matched = false;
            }
            if (matched && (n2.equals(wcMap.get(n1)) || (!strict && n2.equals(wobbleMap.get(n1))))) {
                isWC = true;
            }
        }
        return isWC;
    }

    int[] getPairs(Molecule molecule) {
        String dotBracket = molecule.getDotBracket();
        var ssLayout = new SSLayout(dotBracket.length());
        ssLayout.interpVienna(dotBracket);
        return ssLayout.getBasePairs();

    }

    public List<Integer> genRNAAttrDeep() {
        Molecule molecule = Molecule.getActive();
        int[] basePairs = getPairs(molecule);
        SSGen ssGen = new SSGen(molecule, molecule.getDotBracket());
        ssGen.analyze();

        List<Residue> rnaResidues = getSeqList(molecule);
        List<RNAPair> rnaPairs = new ArrayList<>();
        for (int i = 0; i < basePairs.length; i++) {
            Residue residue = rnaResidues.get(i);
            Residue partner = basePairs[i] >= 0 ? rnaResidues.get(basePairs[i]) : null;
            var pair = new RNAPair(i, residue, partner, basePairs[i]);
            rnaPairs.add(pair);
        }
        List<Integer> tokens = new ArrayList<>();
        for (int i = 0; i < rnaPairs.size(); i++) {
            int ssToken = ssToken(rnaPairs, i, rnaResidues);
            tokens.add(ssToken);
        }
        return tokens;
    }

    private static int ssToken(List<RNAPair> rnaPairs, int i, List<Residue> rnaResidues) {
        int base = rnaPairs.get(i).getBaseType();
        int pair = rnaPairs.get(i).getPairType();
        Residue residue = rnaResidues.get(i);
        SecondaryStructure secondaryStructure = residue.getSecondaryStructure();
        int ss = switch (secondaryStructure) {
            case RNAHelix j: {
                Residue r1 = rnaPairs.get(i).residue;
                Residue r2 = rnaPairs.get(i).partner;
                Polymer polymer1 = r1.getPolymer();
                Polymer polymer2 = r2.getPolymer();
                final int value;
                if (polymer1 == polymer2) {
                    value = r1.getResNum() < r2.getResNum() ? 1 : 2;
                } else {
                    value = polymer1.getIDNum() < polymer2.getIDNum() ? 1 : 2;
                }
                yield value;
            }
            case Bulge j:
                yield 3;
            case Loop loop:
                yield 4;
            case InternalLoop j:
                yield 5;
            case Junction j:
                yield 7;
            case NonLoop j:
                yield 6;
            default:
                yield 0;
        };
        return ss * 25 + pair * 5 + base;
    }

    public List<List<String>> genRNAData() {
        Molecule molecule = Molecule.getActive();
        int[] basePairs = getPairs(molecule);
        Pattern gnraPat = Pattern.compile("G[AGUC][AG]A");
        Pattern uncgPat = Pattern.compile("U[AGUC]CG");
        List<Residue> rnaResidues = getSeqList(molecule);
        List<RNAPair> rnaPairs = new ArrayList<>();
        for (int i = 0; i < basePairs.length; i++) {
            Residue residue = rnaResidues.get(i);
            Residue partner = basePairs[i] >= 0 ? rnaResidues.get(basePairs[i]) : null;
            var pair = new RNAPair(i, residue, partner, basePairs[i]);
            rnaPairs.add(pair);
        }

        int nNeighbors = 2;
        String[] tetraLoops = new String[rnaPairs.size()];
        for (int i = 2; i < rnaPairs.size() - 6; i++) {
            RNAPair startPair = rnaPairs.get(i);
            RNAPair endPair = rnaPairs.get(i + 5);
            boolean tetraloop = true;
            StringBuilder tetraNucs = new StringBuilder();
            String tetraType = "tetra";
            if (startPair.paired() && endPair.paired() && (startPair.partner == endPair.residue)) {
                for (int j = 0; j < 4; j++) {
                    RNAPair rnaPair = rnaPairs.get(i + j + 1);
                    if (rnaPair.partner != null) {
                        tetraloop = false;
                        break;
                    }
                    tetraNucs.append(rnaPair.residue.getName());
                }
            } else {
                tetraloop = false;
            }
            if (tetraloop) {
                if (uncgPat.matcher(tetraNucs.toString()).matches()) {
                    tetraType = "uncg";
                } else if (gnraPat.matcher(tetraNucs.toString()).matches()) {
                    tetraType = "gnra";
                }

                for (int j = 0; j < 5; j++) {
                    tetraLoops[i + j + 1] = tetraType + (j + 1);
                }
            }
        }

        return getRNAAttributes(rnaPairs, tetraLoops, basePairs, nNeighbors);
    }

    List<List<String>> getRNAAttributes(List<RNAPair> rnaPairs, String[] tetraLoops, int[] basePairs, int nNeighbors) {
        List<List<String>> results = new ArrayList<>();
        for (int i = 0; i < rnaPairs.size(); i++) {
            List<String> pairing = new ArrayList<>();
            List<String> attrs = new ArrayList<>();
            RNAPair rnaPairCentral = rnaPairs.get(i);
            for (int j = -nNeighbors; j <= nNeighbors; j++) {
                int k = i + j;
                if ((k < 0) || (k >= rnaPairs.size())) {
                    pairing.add("-");
                    attrs.add("-");
                } else {
                    RNAPair rnaPair = rnaPairs.get(k);
                    if (rnaPair.residue.polymer != rnaPairCentral.residue.polymer) {
                        pairing.add("-");
                        attrs.add("-");
                    } else {
                        if ((j == -2) || (j == 2)) {
                            pairing.add(rnaPair.getPpType());
                        } else {
                            pairing.add(rnaPair.getBPType());
                        }
                        if (tetraLoops[k] == null) {
                            attrs.add("-");
                        } else {
                            attrs.add(tetraLoops[k]);
                        }
                    }
                }
            }
            String primeType = checkNC(basePairs, i);
            pairing.addAll(attrs);
            pairing.add(primeType);
            results.add(pairing);
        }
        return results;
    }

    void loadRNAShifts() throws IOException {
        if (!RNAStats.loaded()) {
            RNAStats.readFile("data/rnadata.txt");
        }
    }

    double getError(String atomName, PredType predType) {
        String mode = "c";
        if (!predType.helix) {
            if (predType.tetraloop) {
                mode = "nc";
            } else {
                mode = "o";
            }
        } else {
            if (!predType.canonical) {
                mode = "nc";
            }
        }


        return svmRMSMap.get(atomName + "_" + mode);
    }

    record PredType(boolean helix, boolean canonical, boolean tetraloop) {
    }

    PredType getType(List<String> attributes) {
        boolean canonical = true;
        boolean helix = true;
        boolean tetraLoop = false;
        List<String> nucList = attributes.subList(0, 5);
        List<String> attrList = attributes.subList(5, 10);
        for (String attr : attrList.subList(1, 4)) {
            if (!attr.equals("-")) {
                helix = false;
                break;
            }
        }
        for (String attr : attrList.subList(0, 5)) {
            if (!attr.equals("-")) {
                canonical = false;
                break;
            }
        }

        for (String nucVal : nucList.subList(0, 5)) {
            if (!checkWC(nucVal, true)) {
                canonical = false;
            }
        }
        for (String nucVal : nucList.subList(1, 4)) {
            if (!checkWC(nucVal, true)) {
                helix = false;
            }
        }
        for (String attr : attrList.subList(1, 4)) {
            if (attr.startsWith("gnra") || attr.startsWith("uncg") || attr.startsWith("tetr")) {
                tetraLoop = true;
                break;
            }
        }
        return new PredType(helix, canonical, tetraLoop);
    }

    public Example<Regressor> getExample(List<String> attributes) {
        int nNuc = 5;
        String loopType = "";
        List<Feature> features = new ArrayList<>();
        for (int i = 0; i < nNuc; i++) {
            String nucStr = "nuc" + i + "_" + attributes.get(i);
            Feature feature = new Feature(nucStr, 1.0);
            features.add(feature);
        }
        String attr = attributes.get(2 + nNuc);
        if (attr.startsWith("gnra") || attr.startsWith("uncg") || attr.startsWith("tetr")) {
            int loopIndex = Integer.parseInt(attr.substring(attr.length() - 1)) - 1;
            String attrStr = "loop" + loopIndex;
            Feature feature = new Feature(attrStr, 1.0);
            features.add(feature);
            loopType = attr.substring(0, attr.length() - 1);
            Feature loopFeature = new Feature(loopType, 1.0);
            features.add(feature);
        }
        Regressor regressor = new Regressor("cs", Double.NaN);
        Example<Regressor> example = new ArrayExample<>(regressor, features);
        return example;
    }

    public void predictFromAttr(Molecule molecule, int ppmSet) throws IOException {
        List<Residue> rnaResidues = getSeqList(molecule);
        int nRes = rnaResidues.size();
        loadRNAShifts();
        List<List<String>> atomAttributes = genRNAData();
        setTypes(attrTypes);
        molecule.updateAtomArray();
        for (int i = 0; i < nRes; i++) {
            List<String> attributes = atomAttributes.get(i);
            Example<Regressor> example = getExample(attributes);
            Residue residue = rnaResidues.get(i);
            PredType predType = getType(attributes);

            String attrValues = String.join("_", attributes);
            String resID = residue.polymer.getName() + ':' + residue.getName() + residue.getNumber();
            put(resID, attrValues);

            Map<String, String> attrValueMap = new HashMap<>();

            for (int j = 0; j < attrTypes.size(); j++) {
                attrValueMap.put(attrTypes.get(j), attributes.get(j));
            }
            for (String atomNucName : atoms) {
                String targetNuc = atomNucName.substring(0, 1);
                String fileAName = atomNucName.substring(1);
                String fileAName1 = switch (fileAName) {
                    case "H21", "H22" : yield  "H21_H22";
                    case "H61", "H62" : yield  "H61_H62";
                    case "H41", "H42" : yield  "H41_H42";
                    case "H5p", "H5pp" : yield  "H5p_H5pp";
                    default: yield fileAName;
                };

                List<String> nucValues = attributes.subList(0, 5);
                String nuc = nucValues.get(2).substring(0, 1);
                if (Objects.equals(nuc, targetNuc)) {
                    Predictor.getTribuoModel(Predictor.PredictionMolType.RNA, fileAName1 + "_A_G_U_C").ifPresent(model -> {
                        int aLen = fileAName.length();
                        final String aName;
                        if (fileAName.endsWith("pp"))
                            aName = fileAName.substring(0, aLen - 2) + "''";
                        else if (fileAName.endsWith("p")) {
                            aName = fileAName.substring(0, aLen - 1) + "'";
                        } else {
                            aName = fileAName;
                        }
                        String fullName = residue.getNumber() + "." + aName;
                        Atom atom = MoleculeBase.getAtomByName(fullName);
                        Prediction<Regressor> prediction = model.predict(example);
                        Regressor regressor = prediction.getOutput();
                        double rShift = regressor.getValues()[0];
                        Double baseShift = Predictor.getDistBaseShift(atom);
                        if (baseShift != null) {
                            rShift = rShift + baseShift;

                            // double errorValue = getError(atomNucName, predType);
                            double errorValue = 0.1;

                            var shiftError = getRNAStats(atom, atomNucName + "_" + attrValues, rShift, errorValue);
                            double shift = Math.round(shiftError.shift * 100.0) / 100.0;
                            errorValue = Math.round(shiftError.error * 100.0) / 100.0;

                            if (ppmSet < 0) {
                                atom.setRefPPM(-ppmSet - 1, shift);
                                atom.setRefError(-ppmSet - 1, errorValue);
                            } else {
                                atom.setPPM(ppmSet, shift);
                                atom.setPPMError(ppmSet, errorValue);
                            }
                        }
                    });
                }
            }
        }
    }

    record ShiftError(double shift, double error) {
    }

    ShiftError getRNAStats(Atom atom, String atomAttr, double shift, double errorValue) {
        var rStats = RNAStats.get(atomAttr, true);
        RNAAttributes.putStats(atom, rStats);
        if (rStats != null) {
            rStats.setPredValue(shift);
            int nAvg = rStats.getN();
            double mean = rStats.getMean();
            double sdev = rStats.getSDev();
            double nSVM = 3.0;
            double totalShifts = nSVM + nAvg;
            double f = nSVM / totalShifts;
            errorValue = Math.sqrt(f * f * errorValue * errorValue + (1 - f) * (1 - f) * sdev * sdev);
            shift = f * shift + (1.0 - f) * mean;
        }
        return new ShiftError(shift, errorValue);
    }

    public static void dump() {
        for (var entry : attrMap.entrySet()) {
            System.out.println(entry.getKey() + " " + entry.getValue());
        }
    }
}
