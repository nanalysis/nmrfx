package org.nmrfx.structure.chemistry.predict;

import com.oracle.labs.mlrg.olcut.util.Pair;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nmrfx.chemistry.*;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.energy.PropertyGenerator;
import org.tribuo.*;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.DataProvenance;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.RegressionFactory;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.evaluation.RegressionEvaluator;
import org.tribuo.regression.slm.LARSLassoTrainer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ProteinPredictorTrainer {

    Map<String, List<String>> types = Collections.emptyMap();
    Map<String, List<ValuesWithCS>> valueMap = new HashMap<>();
    Map<String, List<Double>> refErrors = new HashMap<>();
    Map<String, Double> pdbMeans = new HashMap<>();
    List<String> propNames = Collections.emptyList();
    List<String> keyList = new ArrayList<>();
    Map<String, Double> refPPMMap = new HashMap();

    Map<String, String> testMap = new HashMap<>();

    Map<String, Map<String, DeltaShift>> errorMap = new HashMap<>();
    Map<String, DeltaShift> skipMap = new HashMap<>();

    Map<String, Model<Regressor>> modelMap = new HashMap<>();

    double contactMin = 0.5;
    double trimRatio = 3.0;

    public record FitResult(int n, int nSkip, double rmsd, double mae) {
        public String toString() {
            return String.format("n %d nskip %d rmsd %.2f mae %.2f", n, nSkip, rmsd, mae);
        }
    }

    public Map<String, List<ValuesWithCS>> getValueMap() {
        return valueMap;
    }

    public void contactMin(double value) {
        contactMin = value;
    }

    public void trimRatio(double value) {
        trimRatio = value;
    }

    public void dumpValueMap() {
        for (var entry : valueMap.entrySet()) {
            System.out.println(entry.getKey() + " " + entry.getValue().size());
        }
    }

    public record ValuesWithCS(String molName, String aName, Double expCS, Double refCS, double[] values) {
        public String getKey() {
            return molName + ":" + aName;
        }

        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(molName).append("\t").append(aName).append("\t");
            for (double value : values) {
                stringBuilder.append(String.format("%.5f", value)).append("\t");
            }
            stringBuilder.append(String.format("%.3f", refCS)).append("\t");
            stringBuilder.append(String.format("%.3f", expCS));
            return stringBuilder.toString();
        }
    }

    public void addTrainData(Molecule molecule, int iStructure) throws InvalidMoleculeException, IOException {
        long start = System.currentTimeMillis();
        PropertyGenerator propertyGenerator = new PropertyGenerator();
        ProteinPredictorGen p = new ProteinPredictorGen();
        ProteinPredictor.initMinMax();
        propertyGenerator.init(molecule, iStructure);
        long start2 = System.currentTimeMillis();
        propNames = p.getValueNames();
        int nPolymers = 0;
        int nResidues = 0;

        for (Polymer polymer : molecule.getPolymers()) {
            nPolymers++;
            for (Residue residue : polymer.getResidues()) {
                nResidues++;
                propertyGenerator.getResidueProperties(polymer, residue, iStructure);
                for (Atom atom : residue.atoms) {
                    Optional<String> atomTypeOpt = ProteinPredictor.getAtomNameType(atom);
                    atomTypeOpt.ifPresent(atomType -> {
                        PPMv expPPM = atom.getPPM(0);
                        PPMv refPPM = atom.getPPM(1);
                        if ((expPPM != null) && expPPM.isValid() && (refPPM != null) && refPPM.isValid()) {
                            propertyGenerator.getAtomProperties(atom, iStructure);
                            var atomValueMap = propertyGenerator.getValues();
                            double[] values = p.getValues(atomValueMap);
                            List<ValuesWithCS> valueList = valueMap.computeIfAbsent(atomType, k -> new ArrayList<>());
                            ValuesWithCS valuesWithCS = new ValuesWithCS(molecule.getName(), atom.getFullName(), expPPM.getValue(), refPPM.getValue(), values);
                            valueList.add(valuesWithCS);
                        }
                    });
                }
            }
        }
        long eTime1 = start2 - start;
        long eTime = System.currentTimeMillis() - start2;
        System.out.println("npolymers " + nPolymers + " nresidues " + nResidues + " etime " + (eTime1 / 1000.0) + " " + (eTime / 1000.0));
    }

    public void saveData(String dirName) throws IOException {
        for (var entry : valueMap.entrySet()) {
            File file = Paths.get(dirName, entry.getKey() + ".txt").toFile();
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("mol").append("\t").append("atom").append("\t");
            for (String propName : propNames) {
                stringBuilder.append(propName).append("\t");
            }
            stringBuilder.append("refCS").append("\t");
            stringBuilder.append("cs");
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(stringBuilder.toString());
                writer.write("\n");
                for (ValuesWithCS values : entry.getValue()) {
                    writer.write(values.toString());
                    writer.write("\n");
                }
            }
        }
    }

    public void loadData(String dirName, String label) throws IOException {
        Path path = Paths.get(dirName, label + ".txt");
        List<String> lines = Files.readAllLines(path);
        String[] header = null;
        for (String line : lines) {
            if (!line.isEmpty()) {
                String[] fields = line.split("\t");
                if (header == null) {
                    header = fields;
                    propNames = new ArrayList<>();
                    propNames.addAll(Arrays.asList(header).subList(2, header.length - 2));
                } else {
                    double[] values = new double[fields.length - 4];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = Double.parseDouble(fields[i + 2]);
                    }
                    String molName = fields[0];
                    String aName = fields[1];
                    int nFields = fields.length;
                    double refCS = Double.parseDouble(fields[nFields - 2]);
                    double expCS = Double.parseDouble(fields[nFields - 1]);
                    ValuesWithCS valuesWithCS = new ValuesWithCS(molName, aName, expCS, refCS, values);
                    List<ValuesWithCS> valueList = valueMap.computeIfAbsent(label, k -> new ArrayList<>());
                    valueList.add(valuesWithCS);
                }
            }
        }
    }

    DescriptiveStatistics[] getStats(String atomLabel) {
        DescriptiveStatistics[] descriptiveStatistics = new DescriptiveStatistics[propNames.size()];
        for (int i = 0; i < propNames.size(); i++) {
            descriptiveStatistics[i] = new DescriptiveStatistics();
        }
        for (ValuesWithCS valuesWithCS : valueMap.get(atomLabel)) {
            int i = 0;
            for (double value : valuesWithCS.values) {
                descriptiveStatistics[i++].addValue(value);
            }
        }
        return descriptiveStatistics;
    }

    boolean[] getUseColumns(DescriptiveStatistics[] descriptiveStatistics) {
        boolean[] useColumn = new boolean[descriptiveStatistics.length];
        for (int i = 0; i < useColumn.length; i++) {
            double min = descriptiveStatistics[i].getMin();
            double max = descriptiveStatistics[i].getMax();
            if (Math.abs(max - min) > 1.0e-6) {
                useColumn[i] = true;
            }
        }
        return useColumn;
    }

    public List<Feature> getFeatures(ValuesWithCS valuesWithCS, boolean[] useColumn) {
        List<Feature> features = new ArrayList<>();
        int i = 0;
        int iContact = propNames.indexOf("contacts");
        double contacts = valuesWithCS.values[iContact];
        double[] minMax = {300.0, 3000.0};
        double scale = ProteinPredictor.calcDisorderScale(contacts, minMax);
        scale = Math.min(scale, 1.0);
        scale = Math.max(scale, 0.0);
        scale = 1.0 - (1.0 - scale) * (1.0 - contactMin);
        for (double value : valuesWithCS.values) {
            if (useColumn[i]) {
                String propName = propNames.get(i);
                value *= scale;
                if (!propName.equals("contacts")) {
                    Feature feature = new Feature(propName, value);
                    features.add(feature);
                }
            }
            i++;
        }
        return features;
    }

    public MutableDataset<Regressor> getDataset(String atomLabel, boolean trainMode) {
        RegressionFactory regressionFactory = new RegressionFactory();
        DataProvenance provenance = new SimpleDataSourceProvenance("CS training data", regressionFactory);
        MutableDataset<Regressor> dataset = new MutableDataset<>(provenance, regressionFactory);
        DescriptiveStatistics[] descriptiveStatistics = getStats(atomLabel);
        boolean[] useColumn = getUseColumns(descriptiveStatistics);
        keyList.clear();
        for (ValuesWithCS valuesWithCS : valueMap.get(atomLabel)) {
            String key = valuesWithCS.getKey();
            if (skipMap.containsKey(key) || (trainMode && testMap.containsKey(valuesWithCS.molName)) || (!trainMode && !testMap.containsKey(valuesWithCS.molName))) {
                continue;
            }

            double cs = valuesWithCS.expCS();
            if (pdbMeans.containsKey(valuesWithCS.molName)) {
                cs += pdbMeans.get(valuesWithCS.molName);

            }
            double refCS = valuesWithCS.refCS();
            var features = getFeatures(valuesWithCS, useColumn);
            refPPMMap.put(key, refCS);

            Regressor regressor = new Regressor("cs", cs - refCS);
            Example<Regressor> example = new ArrayExample<>(regressor, features);  // Assuming the label is "label"
            dataset.add(example);
            keyList.add(key);
        }
        return dataset;
    }

    public Model<Regressor> trainDataset(MutableDataset<Regressor> trainData) {
        var lassoTrainer = new LARSLassoTrainer();
        var model = lassoTrainer.train(trainData);

        var regressionEvaluator = new RegressionEvaluator();
        var evaluation = regressionEvaluator.evaluate(model, trainData);

        var dimension = new Regressor("cs", Double.NaN);
        String outStr = String.format("RMSE %7.4f MAE %7.4f R2 %7.4f", evaluation.rmse(dimension), evaluation.mae(dimension), evaluation.r2(dimension));
        System.out.println(outStr);
        return model;
    }

    record DeltaShift(double predValue, double expValue) {
        double delta() {
            return predValue - expValue;
        }
    }

    public void predictDataset(Model<Regressor> model, MutableDataset<Regressor> data, String groupName) {
        List<Prediction<Regressor>> predictions = model.predict(data);
        int i = 0;
        for (var prediction : predictions) {
            Regressor regressor = prediction.getOutput();
            double value = regressor.getValues()[0];
            Regressor oValue = data.getExample(i).getOutput();
            String key = keyList.get(i);
            int colonPos = key.indexOf(":");
            String molName = key.substring(0, colonPos);
            double expValue = oValue.getValues()[0];
            double delta = value - expValue;
            double refPPM = refPPMMap.get(key);
            DeltaShift deltaShift = new DeltaShift(value + refPPM, expValue + refPPM);
            Map<String, DeltaShift> errorMap2 = errorMap.computeIfAbsent(groupName, k -> new HashMap<>());
            errorMap2.put(key, deltaShift);

            List<Double> errorList = refErrors.computeIfAbsent(molName, k -> new ArrayList<>());
            errorList.add(delta);
            i++;
        }
    }

    void getSkip(String groupName, double tol) {
        var groupEntryMap = errorMap.get(groupName);
        groupEntryMap.forEach((key, deltaShift) -> {
            double error = deltaShift.delta();
            if (Math.abs(error) > tol) {
                skipMap.put(key, deltaShift);
            }
        });
    }

    public void getErrorsByMolecule() {
        pdbMeans.clear();
        for (var entry : refErrors.entrySet()) {
            DescriptiveStatistics dStat = new DescriptiveStatistics();
            for (double value : entry.getValue()) {
                dStat.addValue(value);
            }
            if (dStat.getN() > 20) {
                pdbMeans.put(entry.getKey(), dStat.getPercentile(50));
            }
            String mode = testMap.containsKey(entry.getKey()) ? "TEST" : "TRAIN";
            System.out.println("ERROR " + mode + " " + entry.getKey() + " " + dStat.getN() + " " + dStat.getPercentile(50.0) + " " + dStat.getMean() + " " + dStat.getMin() + " " + dStat.getMax());
        }
    }

    FitResult getFitResults(String groupName) {
        var groupEntryMap = errorMap.get(groupName);
        double sumSq = 0.0;
        double sumAbs = 0.0;
        int n = 0;
        int nSkip = 0;
        for (var entry : groupEntryMap.entrySet()) {
            if (skipMap.containsKey(entry.getKey())) {
                nSkip++;
            } else {
                DeltaShift deltaShift = entry.getValue();
                double delta = deltaShift.delta();
                sumSq += delta * delta;
                sumAbs += Math.abs(delta);
                n++;
            }
        }
        double rmsd = n > 0 ? Math.sqrt(sumSq / n) : 0.0;
        double mae = n > 0 ? sumAbs / n : 0.0;
        return new FitResult(n, nSkip, rmsd, mae);
    }

    void doTrainAndPredict(List<String> types, String groupName) {
        for (String type : types) {
            var dataset = getDataset(type, true);
            var model = trainDataset(dataset);
            modelMap.put(type, model);
            predictDataset(model, dataset, groupName);
        }
    }

    Map<String, Double> getCoefficients(String type) {
        var parMap = new HashMap<String, Double>();
        Model<Regressor> model = modelMap.get(type);
        Map<String, List<Pair<String, Double>>> parameters = model.getTopFeatures(-1);
        var listPar = parameters.get("cs");
        for (Pair<String, Double> pair : listPar) {
            String coefName = pair.getA();
            Double value = pair.getB();
            parMap.put(coefName, value);
        }

        return parMap;
    }

    void doPredict(List<String> types, String groupName) {
        for (String type : types) {
            var dataset = getDataset(type, false);
            predictDataset(modelMap.get(type), dataset, groupName);
        }
    }

    public void loadTestSet(String fileName) throws IOException {
        Path path = Path.of(fileName);
        var lines = Files.readAllLines(path);
        testMap.clear();
        for (String line : lines) {
            String[] fields = line.split("\t");
            testMap.put(fields[0].trim(), fields[1].trim());
        }
    }

    public void writeDeltas(Path path, String groupName) throws IOException {
        String fileName = "error_" + groupName + ".txt";
        fileName = fileName.replace("*", "X");
        fileName = fileName.replace("?", "x");
        File file = path.resolve(fileName).toFile();
        StringBuilder stringBuilder = new StringBuilder();
        errorMap.get(groupName).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            DeltaShift deltaShift = e.getValue();
            String s = String.format("%s %.2f %.2f %.2f\n", e.getKey(), deltaShift.predValue, deltaShift.expValue, deltaShift.delta());
            stringBuilder.append(s);
        });
        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write(stringBuilder.toString());
        }
    }

    public void writeSkips(Path path, String groupName) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        for (var entry : skipMap.entrySet()) {
            String key = entry.getKey();
            DeltaShift deltaShift = entry.getValue();
            String s = String.format("%s %.2f %.2f %.2f\n", key, deltaShift.predValue, deltaShift.expValue, deltaShift.delta());
            stringBuilder.append(s);
        }
        String fileName = "skips_" + groupName + ".txt";
        File file = path.resolve(fileName).toFile();
        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write(stringBuilder.toString());
        }
    }

    public void writeCoefficients(String groupName, File file) throws IOException {

        int j = 0;
        double[][] parValues = new double[propNames.size()][types.get(groupName).size()];
        for (String type : types.get(groupName)) {
            var parMap = getCoefficients(type);
            int i = 0;
            for (String propName : propNames) {
                Double propValue = parMap.get(propName);
                parValues[i++][j] = propValue != null ? propValue : 0.0;
            }
            j++;
        }


        String sepChar = "\t";
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("coef");
        for (String type : types.get(groupName)) {
            stringBuilder.append(sepChar).append(type);
        }
        stringBuilder.append("\n");

        for (int i = 0; i < parValues.length; i++) {
            stringBuilder.append(propNames.get(i));
            for (int k = 0; k < parValues[0].length; k++) {
                stringBuilder.append(sepChar).append((String.format("%.9f", parValues[i][k])));
            }
            stringBuilder.append("\n");
        }
        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write(stringBuilder.toString());
        }
    }

    public void fitAll(String dirName, Map<String, List<String>> types, double trimLevel) throws IOException {
        this.types = types;
        ProteinPredictorGen p = new ProteinPredictorGen();
        propNames = p.getValueNames();

        errorMap.clear();
        valueMap.clear();
        pdbMeans.clear();
        for (var entry : types.entrySet()) {
            for (String type : entry.getValue()) {
                loadData(dirName, type);
            }
        }
        for (var entry : types.entrySet()) {
            doTrainAndPredict(entry.getValue(), entry.getKey());
        }
        getErrorsByMolecule();
        for (var entry : types.entrySet()) {
            getSkip(entry.getKey(), trimLevel);
        }
        refErrors.clear();

        for (var entry : types.entrySet()) {
            doTrainAndPredict(entry.getValue(), entry.getKey());
            getSkip(entry.getKey(), trimLevel);
        }
        getErrorsByMolecule();
        for (var entry : types.entrySet()) {
            var fitResult = getFitResults(entry.getKey());
            getSkip(entry.getKey(), fitResult.rmsd * trimRatio);
        }
        refErrors.clear();

        for (var entry : types.entrySet()) {
            doTrainAndPredict(entry.getValue(), entry.getKey());
            String fileName = "coef_" + entry.getKey() + ".txt";
            File file = new File(fileName);
            writeCoefficients(entry.getKey(), file);
        }
        getErrorsByMolecule();
        for (var entry : types.entrySet()) {
            var fitResult = getFitResults(entry.getKey());
            getSkip(entry.getKey(), fitResult.rmsd * trimRatio);
            getFitResults(entry.getKey());
        }

        errorMap.clear();
        refErrors.clear();
        for (var entry : types.entrySet()) {
            doPredict(entry.getValue(), entry.getKey());
        }
        getErrorsByMolecule();
        for (var entry : types.entrySet()) {
            errorMap.clear();
            skipMap.clear();
            doPredict(entry.getValue(), entry.getKey());
            var fitResult = getFitResults(entry.getKey());
            getSkip(entry.getKey(), fitResult.rmsd * trimRatio);
            fitResult = getFitResults(entry.getKey());
            System.out.println("FITRESULT " + entry.getKey() + " " + fitResult);
            Path path = Path.of(dirName);
            writeDeltas(path, entry.getKey());
            writeSkips(path, entry.getKey());
        }
    }
}
