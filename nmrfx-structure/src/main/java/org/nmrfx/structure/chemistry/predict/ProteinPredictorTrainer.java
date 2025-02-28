package org.nmrfx.structure.chemistry.predict;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.nmrfx.chemistry.*;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.energy.PropertyGenerator;
import org.tribuo.Example;
import org.tribuo.Feature;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.DataProvenance;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.RegressionFactory;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.evaluation.RegressionEvaluator;
import org.tribuo.regression.slm.LARSLassoTrainer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ProteinPredictorTrainer {
    Map<String, List<ValuesWithCS>> valueMap = new HashMap<>();
    List<String> propNames = Collections.emptyList();

    public Map<String, List<ValuesWithCS>> getValueMap() {
        return valueMap;
    }

    public void dumpValueMap() {
        for (var entry : valueMap.entrySet()) {
            System.out.println(entry.getKey() + " " + entry.getValue().size());
        }
    }

    public record ValuesWithCS(String name, String aName, Double expCS, Double refCS, double[] values) {
        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(name).append("\t").append(aName).append("\t");
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
                            String type = atomType;
                            //var map = p.getValueMap(atomValueMap);
                            double[] values = p.getValues(atomValueMap);
                            List<ValuesWithCS> valueList = valueMap.computeIfAbsent(type, k -> new ArrayList<>());
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
                    for (int i =0;i< header.length - 2;i++) {
                        propNames.add(header[i]);
                    }
                } else {
                    double[] values = new double[fields.length - 4];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = Double.parseDouble(fields[i + 2]);
                    }
                    String molName = fields[0];
                    String aName = fields[1];
                    double refCS = Double.parseDouble(fields[values.length]);
                    double expCS = Double.parseDouble(fields[values.length + 1]);
                    ValuesWithCS valuesWithCS = new ValuesWithCS(molName, aName, expCS, refCS, values);
                    List<ValuesWithCS> valueList = valueMap.computeIfAbsent(label, k -> new ArrayList<>());
                    valueList.add(valuesWithCS);
                }
            }
        }
    }

    DescriptiveStatistics[] getStats(String atomLabel) {
        DescriptiveStatistics[] descriptiveStatistics = new DescriptiveStatistics[propNames.size()];
        SummaryStatistics summaryStatistics = new SummaryStatistics();
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
            } else {
                System.out.println("skip " + propNames.get(i));
            }
        }
        return useColumn;
    }

    public MutableDataset getDataset(String atomLabel) throws IOException {
        RegressionFactory regressionFactory = new RegressionFactory();
        DataProvenance provenance = new SimpleDataSourceProvenance("CS training data", regressionFactory);
        MutableDataset dataset = new MutableDataset(provenance, regressionFactory);
        DescriptiveStatistics[] descriptiveStatistics = getStats(atomLabel);
        boolean[] useColumn = getUseColumns(descriptiveStatistics);
        int iContact = propNames.indexOf("contacts");
        for (ValuesWithCS valuesWithCS : valueMap.get(atomLabel)) {
            double cs = valuesWithCS.expCS();
            double refCS = valuesWithCS.refCS();
            List<Feature> features = new ArrayList<>();
            int i = 0;
            double contacts = valuesWithCS.values[iContact];
            double scale = ProteinPredictor.calcDisorderScale(contacts, ProteinPredictor.getMinMax(atomLabel));
            for (double value : valuesWithCS.values) {
                if (useColumn[i]) {
                    String propName = propNames.get(i);
                    if (propName.startsWith("hshift")) {
                        value /= 35.0;
                    }
                    value *= scale;
                    if (!propName.equals("contacts")) {
                        Feature feature = new Feature(propName, value);
                        features.add(feature);
                    }
                }
                i++;
            }
            Regressor regressor = new Regressor("cs", cs - refCS);
            Example example = new ArrayExample(regressor, features);  // Assuming the label is "label"
            dataset.add(example);
        }
        return dataset;
    }

    public Model trainDataset(MutableDataset trainData) {
        var lassoTrainer = new LARSLassoTrainer();
        var model = lassoTrainer.train(trainData);

        var regressionEvaluator = new RegressionEvaluator();
        var evaluation = regressionEvaluator.evaluate(model, trainData);

        var dimension = new Regressor("cs", Double.NaN);
        String outStr = String.format("RMSE %7.4f MAE %7.4f R2 %7.4f", evaluation.rmse(dimension), evaluation.mae(dimension), evaluation.r2(dimension));
        System.out.println(outStr);
        System.out.println(evaluation.rmse());
        return model;
    }
}
