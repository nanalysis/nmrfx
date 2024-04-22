package org.nmrfx.analyst.dataops;

import berlin.yuna.typemap.model.TypeList;
import berlin.yuna.typemap.model.TypeMap;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.nmrfx.analyst.compounds.CompoundData;
import org.nmrfx.processor.math.Vec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class DBData {
    private static final Logger log = LoggerFactory.getLogger(DBData.class);
    private static final Map<String, SpectralData> simDataMap = new TreeMap<>();
    private static final Map<String, CompoundDescription> compoundMap = new TreeMap<>();
    private static final Map<Integer, SampleComponent> componentMap = new TreeMap<>();
    private static final Map<Integer, Composition> compositionMap = new TreeMap<>();

    private static final Map<Integer, Sample> sampleMap = new TreeMap<>();

    private static final Map<Integer, String> compoundNames = new HashMap<>();
    private static Path dbPath = null;

    private static int compare(SpectralSegment a, SpectralSegment b) {
        return b.segmentRegion().ppm.compareTo(a.segmentRegion.ppm());
    }

    public record SegmentRegion(Double startPPM, Double endPPM, Integer startPt, Integer endPt, Double ppm,
                                Double range,
                                Double width) {
    }

    public record SegmentData(Double integral, Double volume, Double max, Double nAtoms) {
    }

    public record SegmentCoupling(String jData, String multiplicity) {
    }

    public record SpectralSegment(SegmentRegion segmentRegion, SegmentData segmentData, SegmentCoupling segmentCoupling,
                                  List<Double> values) {

    }

    public record SpectralData(String id, String name, int compoundID, int sampleID, Double ref, Double sf, Double sw,
                               int size, List<SpectralSegment> spectralSegments) {
    }

    public record CompoundDescription(String name, String inChiKey, String hmdbID, String keggID, String pubChemID,
                                      String formula, Double weight, Double xLogP) {
    }

    public record SampleComponent(int id, String name, String casID, int compoundID, String type) {
    }

    public record Composition(int id, int scompID, Double concentration, String units) {
    }

    public record Sample(int id, List<Composition> compositions) {

    }


    public static boolean contains(String name) {
        return simDataMap.containsKey(name);
    }

    public static void genVec(Vec vec, SpectralData spectralData, CompoundData cData) {
        for (SpectralSegment spectralSegment : spectralData.spectralSegments) {
            SegmentRegion segmentRegion = spectralSegment.segmentRegion;
            int firstPt = segmentRegion.startPt;
            int lastPt = segmentRegion.endPt;
            double firstPPM = segmentRegion.startPPM;
            double lastPPM = segmentRegion.endPPM;

            int n = lastPt - firstPt + 1;
            double hzPt1 = spectralData.sw / spectralData.size;
            double hzPt2 = vec.getSW() / vec.getSize();
            int newSize =  (int) Math.round(n * hzPt1 / hzPt2);
            List<Double> values = spectralSegment.values();
            double[] intensities = new double[values.size()];
            for (int i = 0; i < values.size(); i++) {
                intensities[i] = values.get(i);
            }
            double[] newIntensities = getInterpolated(intensities, newSize);
            int pt1 = vec.refToPt(firstPPM);
            int pt2 = vec.refToPt(lastPPM);
            pt1 = (pt1 + pt2) /2 - newSize / 2;
            pt2 = pt1 + newSize - 1;
            for (int i = 0; i < newSize; i++) {
                vec.add(pt1 + i, newIntensities[i]);
            }
            cData.addRegion(newIntensities, pt1, pt2, spectralSegment.segmentRegion.startPPM, spectralSegment.segmentRegion.endPPM);
        }
    }

    public Sample getSample(String specID) {
        SpectralData spectralData = simDataMap.get(specID);
        int sampleID = spectralData.sampleID;
        Sample sample = sampleMap.get(sampleID);
        return sample;
    }

    public static CompoundData makeData(String specID, SimDataVecPars pars) {
        double refNProtons = 9.0;
        double refConc = 1.0;
        double cmpdConc = 1.0;
        if (simDataMap.containsKey(specID)) {
            SpectralData spectralData = simDataMap.get(specID);

            double ref = spectralData.ref - spectralData.sw / spectralData.sf / 2.0;
            CompoundData cData = new CompoundData(specID, specID, ref, pars.getSf(), pars.getSw(), pars.getN(), refConc, cmpdConc, refNProtons);
            CompoundData.put(cData, specID);

            Vec vec = SimData.prepareVec(specID + "_segments", pars);
            genVec(vec, spectralData, cData);
            cData.setVec(vec);
            return cData;
        }
        return null;
    }

    public static List<String> getNames(String pattern) {
        pattern = pattern.trim();
        List<String> names = new ArrayList<>();
        boolean startsWith = false;
        if (!pattern.isEmpty()) {
            if (Character.isUpperCase(pattern.charAt(0))
                    || Character.isDigit(pattern.charAt(0))) {
                startsWith = true;
            }
            pattern = pattern.toLowerCase();
            for (String name : simDataMap.keySet()) {
                boolean match = startsWith ? name.startsWith(pattern) : name.contains(pattern);
                if (match) {
                    names.add(name);
                }
            }

        }
        return names;
    }

    public static void loadSampleComposition(Path path) throws IOException {
        String content = Files.readString(path);
        TypeList typeMap = new TypeList(content);
        for (var entry : typeMap) {
            var typeMap2 = typeMap.getMap(entry);
            int id = typeMap2.get(Integer.class, "id");
            Integer sampleid = typeMap2.get(Integer.class, "sampleid");
            Integer scompID = typeMap2.get(Integer.class, "scompid");
            Double concentration = typeMap2.get(Double.class, "conc");
            String units = typeMap2.get(String.class, "units");
            Composition composition = new Composition(id, scompID, concentration, units);
            compositionMap.put(id, composition);
            Sample sample = sampleMap.computeIfAbsent(sampleid, k -> new Sample(id, new ArrayList<>()));
            sample.compositions.add(composition);
        }
    }

    public static void loadSampleComponents(Path path) throws IOException {
        String content = Files.readString(path);
        TypeList typeMap = new TypeList(content);
        for (var entry : typeMap) {
            var typeMap2 = typeMap.getMap(entry);
            int id = typeMap2.get(Integer.class, "id");
            String name = typeMap2.get(String.class, "name");
            String casID = typeMap2.get(String.class, "cas");
            String type = typeMap2.get(String.class, "type");
            Integer compoundID = typeMap2.get(Integer.class, "cmpdid");
            SampleComponent compoundDescription = new SampleComponent(id, name, casID, compoundID, type);
            componentMap.put(id, compoundDescription);
        }
    }

    public static void loadCompoundData(Path path) throws IOException {
        String content = Files.readString(path);
        TypeList typeMap = new TypeList(content);
        for (var entry : typeMap) {
            var typeMap2 = typeMap.getMap(entry);
            int id = typeMap2.get(Integer.class, "id");
            String name = typeMap2.get(String.class, "name");
            String inChiKey = typeMap2.get(String.class, "InChIKey");
            String formula = typeMap2.get(String.class, "MolecularFormula");
            String hmdbID = typeMap2.get(String.class, "hmdbID");
            String keggID = typeMap2.get(String.class, "keggID");
            String pubchemID = typeMap2.get(String.class, "pubchemID");
            Double weight = typeMap2.get(Double.class, "MolecularWeight");
            Double xLogP = typeMap2.get(Double.class, "XLogP");
            CompoundDescription compoundDescription = new CompoundDescription(name, inChiKey, hmdbID, keggID, pubchemID, formula, weight, xLogP);
            compoundMap.put(name, compoundDescription);
            compoundNames.put(id, name);
        }
    }

    public static void loadData(Path path) throws IOException {
        if ((dbPath != null) && dbPath.equals(path)) {
            return;
        }
        dbPath = path;
        Path parentPath = dbPath.toFile().getParentFile().toPath();
        loadCompoundData(parentPath.resolve("compound.tsv.json"));
        loadSampleComponents(parentPath.resolve("samplecomp.tsv.json"));
        loadSampleComposition(parentPath.resolve("composition.tsv.json"));
        String content = Files.readString(path);
        TypeMap typeMap = new TypeMap(content);

        for (var key : typeMap.keySet()) {
            var typeMap2 = typeMap.getMap(key);
            String name = typeMap2.get(String.class, "name");
            Integer compoundID = typeMap2.get(Integer.class, "cmpdID");
            Integer sample = typeMap2.get(Integer.class, "sampleID");
            Double ref = typeMap2.get(Double.class, "ref");
            Double sf = typeMap2.get(Double.class, "sf");
            Double sw = typeMap2.get(Double.class, "sw");
            Integer size = typeMap2.get(Integer.class, "size");

            var typeMap3 = typeMap2.getMap("shifts");
            List<SpectralSegment> spectralSegments = new ArrayList<>();
            for (var key2 : typeMap3.keySet()) {
                var typeMap4 = typeMap3.getMap(key2);

                Double startPPM = typeMap4.get(Double.class, "startPPM");
                Double endPPM = typeMap4.get(Double.class, "endPPM");
                Integer startPt = typeMap4.get(Integer.class, "startPt");
                Integer endPt = typeMap4.get(Integer.class, "endPt");
                Double range = typeMap4.get(Double.class, "range");
                Double volume = typeMap4.get(Double.class, "volume");
                Double width = typeMap4.get(Double.class, "width");
                Double ppm = typeMap4.get(Double.class, "ppm");
                SegmentRegion segmentRegion = new SegmentRegion(startPPM, endPPM, startPt, endPt, ppm, range, width);

                Double integral = typeMap4.get(Double.class, "integral");
                Double max = typeMap4.get(Double.class, "max");
                Double nAtoms = typeMap4.get(Double.class, "natoms");

                SegmentData segmentData = new SegmentData(integral, volume, max, nAtoms);

                String jData = typeMap4.get(String.class, "jdata");
                String multiplicity = typeMap4.get(String.class, "multiplicity");

                SegmentCoupling segmentCoupling = new SegmentCoupling(jData, multiplicity);

                var values = typeMap4.getList(Double.class, "values");

                SpectralSegment spectralSegment = new SpectralSegment(segmentRegion, segmentData, segmentCoupling, values);
                spectralSegments.add(spectralSegment);
            }
            spectralSegments.sort(DBData::compare);
            String compoundName = compoundNames.get(compoundID);
            SpectralData spectralData = new SpectralData(compoundName, name, compoundID, sample, ref, sf, sw, size, spectralSegments);
            simDataMap.put(compoundName.toLowerCase().replace(' ', '-'), spectralData);
        }
    }

    public static double[] getInterpolated(double[] intensities, int newSize) {
        int n = intensities.length;
        double[] interpIntensities = new double[newSize];
        // we use two extra points, assumed to be y=0, so we can interpolate first and last points
        double[] x = new double[n + 2];
        double[] y = new double[n + 2];
        for (int i = 0; i < n; i++) {
            double f = (double) i / (n - 1);
            x[i + 1] = f;
            y[i + 1] = intensities[i];
        }
        y[0] = 0.0;
        x[0] = -1.0 / (n -1);
        y[n + 1] = 0.0;
        x[n + 1] = (double) n / (n - 1);

        SplineInterpolator sInterp = new SplineInterpolator();
        var pSF = sInterp.interpolate(x, y);


        try {
            for (int i = 0; i < newSize; i++) {
                double f = (double) i / (newSize - 1);
                interpIntensities[i] = pSF.value(f);
            }
        } catch (OutOfRangeException adE) {
            log.warn(adE.getMessage(), adE);
        }
        return interpIntensities;
    }

}
