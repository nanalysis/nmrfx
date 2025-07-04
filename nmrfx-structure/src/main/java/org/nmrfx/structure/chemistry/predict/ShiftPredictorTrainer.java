package org.nmrfx.structure.chemistry.predict;

import com.oracle.labs.mlrg.olcut.util.Pair;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nmrfx.chemistry.*;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.energy.PropertyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tribuo.*;
import org.tribuo.evaluation.CrossValidation;
import org.tribuo.evaluation.DescriptiveStats;
import org.tribuo.evaluation.EvaluationAggregator;
import org.tribuo.evaluation.metrics.MetricID;
import org.tribuo.evaluation.metrics.MetricTarget;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.DataProvenance;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.RegressionFactory;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.evaluation.RegressionEvaluation;
import org.tribuo.regression.evaluation.RegressionEvaluator;
import org.tribuo.regression.slm.LARSLassoTrainer;

import org.tribuo.regression.sgd.objectives.SquaredLoss;
import org.tribuo.regression.sgd.fm.FMRegressionTrainer;
import org.tribuo.math.optimisers.AdaGrad;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ShiftPredictorTrainer {
    private static final Logger log = LoggerFactory.getLogger(ShiftPredictorTrainer.class);

    Map<String, List<String>> types = Collections.emptyMap();
    Map<String, List<ValuesWithCS>> valueMap = new HashMap<>();
    Map<String, List<Double>> refErrors = new HashMap<>();
    Map<String, Double> pdbMeans = new HashMap<>();
    Map<String, List<String>> propNameMap = new HashMap<>();
    List<String> proteinPropNames = Collections.emptyList();
    List<String> keyList = new ArrayList<>();
    Map<String, Double> refPPMMap = new HashMap<>();

    Map<String, String> testMap = new HashMap<>();

    Map<String, Map<String, DeltaShift>> errorMap = new HashMap<>();
    Map<String, DeltaShift> skipMap = new HashMap<>();

    Map<String, Model<Regressor>> modelMap = new HashMap<>();

    double contactMin = 0.5;
    double trimRatio = 3.0;
    boolean useFactorMachine = false;
    int factorDim = 5;
    int epochs = 1000;
    double factorVariance = 0.050;
    double learningRate = 0.1;

    int nCrossValidate = 0;

    public enum TrainModes {
        TRAIN,
        TEST,
        ALL
    }

    public record FitResult(int n, int nSkip, double rmsd, double mae) {
        @Override
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

    public void factorVariance(double value) {
        factorVariance = value;
    }

    public void learningRate(double value) {
        learningRate = value;
    }

    public void factorDim(int value) {
        factorDim = value;
    }

    public void epochs(int value) {
        epochs = value;
    }

    public void useFactorMachine(boolean state) {
        useFactorMachine = state;
    }

    public void crossValidate(int n) {
        nCrossValidate = n;
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
        proteinPropNames = p.getValueNames();
        int nPolymers = 0;
        int nResidues = 0;
        AtomicInteger nShifts = new AtomicInteger();

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
                            nShifts.getAndIncrement();
                        }
                    });
                }
            }
        }
        long eTime1 = start2 - start;
        long eTime = System.currentTimeMillis() - start2;
        System.out.println("MOL " + molecule.getName() + " npolymers " + nPolymers + " nresidues " + nResidues + " etime1 " + (eTime1 / 1000.0) + " etime2 " + (eTime / 1000.0) + " shifts " + nShifts.get());
    }

    public void saveData(String dirName) throws IOException {
        for (var entry : valueMap.entrySet()) {
            File file = Paths.get(dirName, entry.getKey() + ".txt").toFile();
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("mol").append("\t").append("atom").append("\t");
            for (String propName : proteinPropNames) {
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

    public void loadData(String dirName, String label, String type) throws IOException {
        Path path = Paths.get(dirName, label + ".txt");
        List<String> lines = Files.readAllLines(path);
        String[] header = null;
        for (String line : lines) {
            if (!line.isEmpty()) {
                String[] fields = line.split("\t");
                if (header == null) {
                    header = fields;
                    List<String> propNames = new ArrayList<>(Arrays.asList(header).subList(2, header.length - 2));
                    propNameMap.put(type, propNames);
                    propNameMap.put(label, propNames);
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
        for (var valueList : valueMap.values()) {
            Collections.shuffle(valueList);
        }
    }

    DescriptiveStatistics[] getStats(String atomLabel) {
        List<String> propNames = propNameMap.get(atomLabel);
        if (propNames == null) {
            return new DescriptiveStatistics[0];
        }
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

    public List<Feature> getFeatures(ValuesWithCS valuesWithCS, boolean[] useColumn, String atomLabel) {
        List<Feature> features = new ArrayList<>();
        int i = 0;
        var propNames = propNameMap.get(atomLabel);
        int iContact = propNames.indexOf("contacts");
        double scale = 1.0;
        if (iContact >= 0) {
            double contacts = valuesWithCS.values[iContact];
            double[] minMax = {300.0, 3000.0};
            scale = ProteinPredictor.calcDisorderScale(contacts, minMax);
            scale = Math.min(scale, 1.0);
            scale = Math.max(scale, 0.0);
            scale = 1.0 - (1.0 - scale) * (1.0 - contactMin);
        }
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

    public MutableDataset<Regressor> getDataset(String atomLabel, TrainModes trainMode) {
        RegressionFactory regressionFactory = new RegressionFactory();
        DataProvenance provenance = new SimpleDataSourceProvenance("CS training data", regressionFactory);
        MutableDataset<Regressor> dataset = new MutableDataset<>(provenance, regressionFactory);
        DescriptiveStatistics[] descriptiveStatistics = getStats(atomLabel);
        boolean[] useColumn = getUseColumns(descriptiveStatistics);
        keyList.clear();
        System.out.println(atomLabel);
        for (ValuesWithCS valuesWithCS : valueMap.get(atomLabel)) {
            String key = valuesWithCS.getKey();
            boolean inTest = testMap.containsKey(valuesWithCS.molName);

            if (skipMap.containsKey(key) || ((trainMode == TrainModes.TRAIN) && inTest) || (trainMode == TrainModes.TEST && !inTest)) {
                continue;
            }

            double cs = valuesWithCS.expCS();
            if (pdbMeans.containsKey(valuesWithCS.molName)) {
                cs += pdbMeans.get(valuesWithCS.molName);

            }
            double refCS = valuesWithCS.refCS();
            var features = getFeatures(valuesWithCS, useColumn, atomLabel);
            if (features.isEmpty()) {
                continue;
            }
            refPPMMap.put(key, refCS);

            Regressor regressor = new Regressor("cs", cs - refCS);
            Example<Regressor> example = new ArrayExample<>(regressor, features);  // Assuming the label is "label"

            dataset.add(example);
            keyList.add(key);
        }
        return dataset;
    }

    public Trainer<Regressor> getTrainer() {
        Trainer<Regressor> trainer;
        if (useFactorMachine) {
            var sqLoss = new SquaredLoss();
            var optimizer = new AdaGrad(learningRate);
            int seed = 1;

            trainer = new FMRegressionTrainer(sqLoss, optimizer, epochs, seed, factorDim, factorVariance, true);
        } else {
            trainer = new LARSLassoTrainer();
        }

        return trainer;
    }

    public Model<Regressor> trainDataset(MutableDataset<Regressor> trainData, String name) {
        var trainer = getTrainer();
        System.out.println("train " + name + " " + trainData.size());
        Model<Regressor> model = null;
        try {
           model = trainer.train(trainData);

            var regressionEvaluator = new RegressionEvaluator();
            var evaluation = regressionEvaluator.evaluate(model, trainData);

            var dimension = new Regressor("cs", Double.NaN);
            String outStr = String.format("RMSE %8s %8d %7.4f MAE %7.4f R2 %7.4f", name, trainData.size(), evaluation.rmse(dimension), evaluation.mae(dimension), evaluation.r2(dimension));
            System.out.println(outStr);
        } catch (Exception e) {
            log.error("Error in training", e);
        }
        return model;
    }

    public Double doCrossValidation(MutableDataset<Regressor> trainData) {
        var trainer = getTrainer();
        var regressionEvaluator = new RegressionEvaluator();
        int nCross = nCrossValidate;
        if (trainData.size() < 1000) {
            nCross = Math.min(5, nCross);
        }
        Double rmse = null;
        try {
            CrossValidation<Regressor, RegressionEvaluation> crossValidation = new CrossValidation<>(trainer, trainData, regressionEvaluator, nCross);
            var results = crossValidation.evaluate();
            var evAgg = EvaluationAggregator.summarizeCrossValidation(results);
            for (Map.Entry<MetricID<Regressor>, DescriptiveStats> eventry : evAgg.entrySet()) {
                if (eventry.getKey() instanceof MetricID metricID) {
                    if (metricID.getA() instanceof MetricTarget metricA) {
                        if (metricID.getB().equals("RMSE") && metricA.getOutputTarget().isPresent()) {
                            var obj = eventry.getValue();
                            if (obj instanceof DescriptiveStats stats) {
                                rmse = stats.getMean();
                            }
                        }
                    }
                }
            }
        } catch(Exception e) {
            log.error("Error in cross validation", e);
        }

        return rmse;
    }

    public Map<String, Double> doCrossValidation(List<String> types, String groupName) {
        Map<String, Double> rmseMap = new HashMap<>();
        for (String type : types) {
            var dataset = getDataset(type, TrainModes.ALL);
            Double rmse = null;
            if (dataset.size() > 100) {
                rmse = doCrossValidation(dataset);
            }
            if (rmse == null) {
                System.out.println("NORMSE " + type);
            }
            rmseMap.put(type, rmse);
        }
        return rmseMap;
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
        if (errorMap.containsKey(groupName)) {
            var groupEntryMap = errorMap.get(groupName);
            groupEntryMap.forEach((key, deltaShift) -> {
                double error = deltaShift.delta();
                if (Math.abs(error) > tol) {
                    skipMap.put(key, deltaShift);
                }
            });
        } else {
            System.out.println("can't find " + groupName + " in " + errorMap.keySet());
        }
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
        }
    }

    FitResult getFitResults(String groupName) {
        var groupEntryMap = errorMap.get(groupName);
        if (groupEntryMap == null) {
            System.out.println("can't find " + groupName + " in " + errorMap.keySet());
            return new FitResult(0, 0, 0.0, 0.0);
        }

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
            var dataset = getDataset(type, TrainModes.TRAIN);
            if (dataset.size() > 50) {
                var model = trainDataset(dataset, type);
                if (model != null) {
                    modelMap.put(type, model);
                    predictDataset(model, dataset, groupName);
                }
            }
        }
    }

    Map<String, Double> getCoefficients(String type) {
        var parMap = new HashMap<String, Double>();
        Model<Regressor> model = modelMap.get(type);
        if (model == null) {
            return parMap;
        }
        Map<String, List<Pair<String, Double>>> parameters = model.getTopFeatures(-1);
        var listPar = parameters.get("cs");
        for (Pair<String, Double> pair : listPar) {
            String coefName = pair.getA();
            Double value = pair.getB();
            parMap.put(coefName, value);
        }
        return parMap;
    }

    void doPredict(List<String> types, String groupName, TrainModes trainMode) {
        for (String type : types) {
            var dataset = getDataset(type, trainMode);
            Model model = modelMap.get(type);
            if (model != null) {
                predictDataset(modelMap.get(type), dataset, groupName);
            }
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
        if (!errorMap.containsKey(groupName)) {
            System.out.println("can't find " + groupName + " in " + errorMap.keySet());
            return;
        }
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

    public void writePDBMeans(Path path) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        for (var entry : refErrors.entrySet()) {
            DescriptiveStatistics dStat = new DescriptiveStatistics();
            for (double value : entry.getValue()) {
                dStat.addValue(value);
            }
            String mode = testMap.containsKey(entry.getKey()) ? "TEST" : "TRAIN";
            stringBuilder.append(String.format("ERROR %s %s %d %.3f %.3f %.3f %.3f\n", mode, entry.getKey(), dStat.getN(), dStat.getPercentile(50.0), dStat.getMean(), dStat.getMin(), dStat.getMax()));
        }

        try (FileWriter fileWriter = new FileWriter(path.toFile())) {
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
        fileName = fileName.replace("*", "X");
        fileName = fileName.replace("?", "x");
        File file = path.resolve(fileName).toFile();
        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write(stringBuilder.toString());
        }
    }

    public void serializeToFile(Path path, String groupName) throws IOException {
        for (String type : types.get(groupName)) {
            Model model = modelMap.get(type);
            if (model != null) {
                String fileName = "model_" + type + ".proto";
                fileName = fileName.replace("*", "X");
                fileName = fileName.replace("?", "x");

                Path modelPath = path.resolve(fileName);
                model.serializeToFile(modelPath);
            }
        }
    }

    public void writeCoefficients(String groupName, File file) throws IOException {
        List<String> propNames = propNameMap.get(groupName);

        if (propNames == null) {
            System.out.println(groupName + " " + propNameMap.keySet());
        }
        int j = 0;
        double[][] parValues = new double[propNames.size()][types.get(groupName).size()];
        for (String type : types.get(groupName)) {
            var parMap = getCoefficients(type);
            if (parMap.isEmpty()) {
                continue;
            }
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

    public void fitAll(String valueMapDir, String resultDir, Map<String, List<String>> types, double trimLevel) throws IOException {
        this.types = types;
        errorMap.clear();
        valueMap.clear();
        pdbMeans.clear();
        Path resultDirPath = Path.of(resultDir);
        for (var entry : types.entrySet()) {
            for (String type : entry.getValue()) {
                loadData(valueMapDir, type, entry.getKey());
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
            fileName = fileName.replace("*", "X");
            fileName = fileName.replace("?", "x");
            Path path = Path.of(resultDir, fileName);
            writeCoefficients(entry.getKey(), path.toFile());
            serializeToFile(resultDirPath, entry.getKey());
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
            doPredict(entry.getValue(), entry.getKey(), TrainModes.ALL);
        }
        getErrorsByMolecule();
        Path pdbMeanPath = Path.of(resultDir, "pdboffset.txt");
        writePDBMeans(pdbMeanPath);

        if (nCrossValidate > 1) {
            Path crossValidatePath = Path.of(resultDir, "crossval.txt");
            try (FileWriter fileWriter = new FileWriter(crossValidatePath.toFile())) {
                for (var entry : types.entrySet()) {
                    var rmseMap = doCrossValidation(entry.getValue(), entry.getKey());
                    for (var rmseEntry : rmseMap.entrySet()) {
                        fileWriter.write("CROSSVAL " + rmseEntry.getKey() + " " + rmseEntry.getValue() + "\n");
                    }
                    writeDeltas(resultDirPath, entry.getKey());
                    writeSkips(resultDirPath, entry.getKey());
                }
            }
        } else {
            Path resultsPath = Path.of(resultDir, "results.txt");
            try (FileWriter fileWriter = new FileWriter(resultsPath.toFile())) {
                for (var entry : types.entrySet()) {
                    errorMap.clear();
                    skipMap.clear();
                    doPredict(entry.getValue(), entry.getKey(), TrainModes.TEST);
                    var fitResult = getFitResults(entry.getKey());
                    getSkip(entry.getKey(), fitResult.rmsd * trimRatio);
                    fitResult = getFitResults(entry.getKey());
                    fileWriter.write("FITRESULT " + entry.getKey() + " " + fitResult + "\n");
                    writeDeltas(resultDirPath, entry.getKey());
                    writeSkips(resultDirPath, entry.getKey());
                }
            }
        }
    }
}
