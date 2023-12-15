/*
 * NMRFx Analyst :
 * Copyright (C) 2004-2021 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.chemistry.relax;

import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author brucejohnson
 */
@PluginAPI("ring")
public class OrderPar implements RelaxationValues {

    static final String[] PAR_NAMES = {"S2", "Tau_e", "Tau_f", "Tau_s", "Rex", "Sf2", "Ss2", "model", "rms"};
    static final String DEFAULT_FLOAT_FORMAT = " %10.6f";
    public static final String[] orderParLoopStrings = {
            "Order_param_val", DEFAULT_FLOAT_FORMAT,
            "Order_param_val_fit_err", DEFAULT_FLOAT_FORMAT,
            "Tau_e_val", DEFAULT_FLOAT_FORMAT,
            "Tau_e_val_fit_err", DEFAULT_FLOAT_FORMAT,
            "Tau_f_val", DEFAULT_FLOAT_FORMAT,
            "Tau_f_val_fit_err", DEFAULT_FLOAT_FORMAT,
            "Tau_s_val", DEFAULT_FLOAT_FORMAT,
            "Tau_s_val_fit_err", DEFAULT_FLOAT_FORMAT,
            "Rex_val", DEFAULT_FLOAT_FORMAT,
            "Rex_val_fit_err", DEFAULT_FLOAT_FORMAT,
            "Model_free_sum_squared_errs", DEFAULT_FLOAT_FORMAT,
            "Model_free_n_values", " %4d",
            "Model_free_n_pars", " %4d",
            "Model_fit", " %10s",
            "Sf2_val", DEFAULT_FLOAT_FORMAT,
            "Sf2_val_fit_err", DEFAULT_FLOAT_FORMAT,
            "Ss2_val", DEFAULT_FLOAT_FORMAT,
            "Ss2_val_fit_err", DEFAULT_FLOAT_FORMAT,
            "SH2_val", DEFAULT_FLOAT_FORMAT,
            "SH2_val_fit_err", DEFAULT_FLOAT_FORMAT,
            "SN2_val", DEFAULT_FLOAT_FORMAT,
            "SN2_val_fit_err", DEFAULT_FLOAT_FORMAT,
            "Resonance_ID", " %5d"};

    private final OrderParSet orderParSet;

    private final ResonanceSource resSource;
    private Double value;
    private Double error;
    private Double TauE;
    private Double TauEerr;
    private Double TauF;
    private Double TauFerr;
    private Double TauS;
    private Double TauSerr;
    private Double Rex;
    private Double Rexerr;
    private Double Sf2;
    private Double Sf2err;
    private Double Ss2;
    private Double Ss2err;
    private final Double sumSqErr;
    private final Integer nValues;
    private final Integer nPars;
    private String model;
    private Double modelNum;

    public OrderPar(OrderParSet orderParSet, ResonanceSource resSource, Double sumSqErr, Integer nValues, Integer nPars, String model) {
        this(orderParSet, resSource, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, sumSqErr, nValues, nPars, model);

    }

    public OrderPar(OrderParSet orderParSet, ResonanceSource resSource, Double value, Double error, Double TauE,
                    Double TauEerr, Double TauF, Double TauFerr, Double TauS,
                    Double TauSerr, Double Rex, Double Rexerr, Double Sf2,
                    Double Sf2err, Double Ss2, Double Ss2err,
                    Double sumSqErr, Integer nValues, Integer nPars, String model) {
        this.orderParSet = orderParSet;
        this.resSource = resSource;
        this.value = value;
        this.error = error;
        this.TauE = TauE;
        this.TauEerr = TauEerr;
        this.TauF = TauF;
        this.TauFerr = TauFerr;
        this.TauS = TauS;
        this.TauSerr = TauSerr;
        this.Rex = Rex;
        this.Rexerr = Rexerr;
        this.Sf2 = Sf2;
        this.Sf2err = Sf2err;
        this.Ss2 = Ss2;
        this.Ss2err = Ss2err;
        this.sumSqErr = sumSqErr;
        this.nValues = nValues;
        this.nPars = nPars;
        this.model = model;
        orderParSet.add(this);
    }

    public OrderPar rEx(Double val, Double err) {
        return new OrderPar(orderParSet, resSource, value, error,
                TauE, TauEerr,
                TauF, TauFerr,
                TauS, TauSerr,
                val, err,
                Sf2, Sf2err,
                Ss2, Ss2err,
                sumSqErr, nValues, nPars, model);
    }

    public OrderPar setModel() {
        OrderPar newPar = new OrderPar(orderParSet, resSource, value, error,
                TauE, TauEerr,
                TauF, TauFerr,
                TauS, TauSerr,
                Rex, Rexerr,
                Sf2, Sf2err,
                Ss2, Ss2err,
                sumSqErr, nValues, nPars, model);
        double delSf = Sf2 == null ? 0.0 : 1.0 - Sf2;
        double delSs = Ss2 == null ? 0.0 : 1.0 - Ss2;
        double minTauLimit = 5.0e-3;
        double minDelSLimit = 0.01;
        double slowLimit = 0.15;
        if ((delSf > minDelSLimit) && (delSs > minDelSLimit)) {
            if ((TauF != null) && (TauF > minTauLimit)
                    && (TauS != null) && (TauS > minTauLimit)) {
                newPar.modelNum = 6.0;
            } else if ((TauF != null) && (TauF > slowLimit)) {
                newPar.modelNum = 5.0;
            } else if ((TauS != null) && (TauS > slowLimit)) {
                newPar.modelNum = 5.0;
            } else {
                newPar.modelNum = 4.0;
            }
        } else {
            if (delSs > minDelSLimit) {
                if ((TauS != null) && (TauS > minTauLimit)) {
                    if (TauS > slowLimit) {
                        newPar.modelNum = 3.0;
                    } else {
                        newPar.modelNum = 2.0;
                    }
                } else {
                    newPar.modelNum = 1.0;
                }
            } else if (delSf > minDelSLimit) {
                if ((TauF != null) && (TauF > minTauLimit)) {
                    if (TauF > slowLimit) {
                        newPar.modelNum = 3.0;
                    } else {
                        newPar.modelNum = 2.0;
                    }
                } else {
                    newPar.modelNum = 1.0;
                }
            }
        }
        return newPar;
    }

    public OrderPar set(String name, Double val, Double err) {
        OrderPar newPar = new OrderPar(orderParSet, resSource, value, error,
                TauE, TauEerr,
                TauF, TauFerr,
                TauS, TauSerr,
                Rex, Rexerr,
                Sf2, Sf2err,
                Ss2, Ss2err,
                sumSqErr, nValues, nPars, model);
        switch (name) {
            case "Order_param":
            case "S2":
                newPar.value = val;
                newPar.error = err;
                if ((val != null) && (Sf2 != null) && (Ss2 == null)) {
                    newPar.Ss2 = val / Sf2;
                }
                break;
            case "Tau_e":
                newPar.TauE = val;
                newPar.TauEerr = err;
                break;
            case "Tau_f":
                newPar.TauF = val;
                newPar.TauFerr = err;
                break;
            case "Tau_s":
                newPar.TauS = val;
                newPar.TauSerr = err;
                break;
            case "Rex":
                newPar.Rex = val;
                newPar.Rexerr = err;
                break;
            case "Sf2":
                newPar.Sf2 = val;
                newPar.Sf2err = err;
                if ((val != null)) {
                    if (newPar.Ss2 != null) {
                        newPar.value = newPar.Sf2 * newPar.Ss2;
                        if ((newPar.Sf2err != null) && (newPar.Ss2err != null)) {
                            double dF = newPar.Sf2err / newPar.Sf2;
                            double dS = newPar.Ss2err / newPar.Ss2;
                            newPar.error = newPar.value * Math.sqrt(dF * dF + dS * dS);
                        }
                    } else {
                        newPar.value = newPar.Sf2;
                        newPar.error = newPar.Sf2err;
                    }
                }
                break;
            case "Ss2":
                newPar.Ss2 = val;
                newPar.Ss2err = err;
                if ((val != null)) {
                    if (newPar.Sf2 != null) {
                        newPar.value = newPar.Sf2 * newPar.Ss2;
                        if ((newPar.Sf2err != null) && (newPar.Ss2err != null)) {
                            double dF = newPar.Sf2err / newPar.Sf2;
                            double dS = newPar.Ss2err / newPar.Ss2;
                            newPar.error = newPar.value * Math.sqrt(dF * dF + dS * dS);
                        }
                    } else {
                        newPar.value = newPar.Ss2;
                        newPar.error = newPar.Ss2err;
                    }
                }
                break;

            case "model":
                newPar.modelNum = val;
                break;
        }
        return newPar;
    }

    public String getName() {
        return "S2";
    }

    public static String[] getNames() {
        return PAR_NAMES;

    }

    @Override
    public String[] getParNames() {
        return PAR_NAMES;
    }

    @Override
    public ResonanceSource getResonanceSource() {
        return resSource;
    }

    public String getModel() {
        return model;
    }

    @Override
    public Double getValue() {
        return value;
    }

    @Override
    public Double getError() {
        return error;
    }

    @Override
    public Double getValue(String name) {
        switch (name) {
            case "Order_param":
            case "S2":
                return value;
            case "Tau_e":
                return TauE;
            case "Tau_f":
                return TauF;
            case "Tau_s":
                return TauS;
            case "Rex":
                return Rex;
            case "Sf2":
                return Sf2;
            case "Ss2":
                return Ss2;
            case "model":
                return modelNum;
            case "rms":
                if (nValues != null) {
                    return Math.sqrt(sumSqErr / nValues);
                } else {
                    return null;
                }
            case "chisq":
                return sumSqErr;
            case "rchisq":
                if ((nValues != null) && (nPars != null)) {
                    return sumSqErr / (nValues - nPars);
                } else {
                    return null;
                }
            default:
                return null;
        }
    }

    public double getChiSqr() {
        return sumSqErr;
    }

    public double getReducedChiSqr() {
        if ((nValues != null) && (nPars != null)) {
            return sumSqErr / (nValues - nPars);
        } else {
            return 0.0;
        }
    }

    public double getRMS() {
        return Math.sqrt(sumSqErr/nValues);
    }

    public double getAIC() {
        if ((nValues != null) && (nPars != null) && (sumSqErr != null)) {
            return 2 * (nPars + 1) + nValues * Math.log(sumSqErr / nValues);
        } else {
            return 0.0;
        }
    }

    public double getAICC() {
        int k = nPars;
        return getAIC() + 2.0 * (k + 1) * (k + 2) / (nValues - k );
    }

    public int getN() {
        return nValues;
    }

    @Override
    public Double getError(String name) {
        switch (name) {
            case "Order_param":
            case "S2":
                return error;
            case "Tau_e":
                return TauEerr;
            case "Tau_f":
                return TauFerr;
            case "Tau_s":
                return TauSerr;
            case "Rex":
                return Rexerr;
            case "Sf2":
                return Sf2err;
            case "Ss2":
                return Ss2err;
            default:
                return null;
        }
    }

    public static Map<OrderParSet, List<OrderPar>> getOrderParameters(List<Atom> atoms) {
        var orderParData = new HashMap<OrderParSet, List<OrderPar>>();
        atoms.forEach((atom) -> {
            for (var orderPars : atom.getOrderPars().entrySet()) {
                OrderParSet orderParSet = orderPars.getKey();
                OrderPar orderPar = orderPars.getValue();
                List<OrderPar> orderParList = orderParData.get(orderParSet);
                if (!orderParData.containsKey(orderParSet)) {
                    orderParList = new ArrayList<>();
                    orderParData.put(orderParSet, orderParList);
                }
                orderParList.add(orderPar);
            }
        });
        return orderParData;
    }

    public static void writeToFile(File file) throws IOException {
        MoleculeBase moleculeBase = MoleculeFactory.getActive();
        var orderParSetMap = moleculeBase.orderParSetMap();
        System.out.println(orderParSetMap);
        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write("Set\tChain\tResidue\tAtom\tS2\tS2_err\tTauE\tTauE_err\tSf2\tSf2_err\tSs2\tSs2_err\tTauF\t" +
                    "TauF_err\tTauS\tTauS_err\tRex\tRex_err\tmodel\tmodelNum\tchiSq\tredChiSq\tAIC\tnValues\tnPars\n");

            for (var entry : orderParSetMap.entrySet()) {
                writeToFile(fileWriter, entry.getKey(), entry.getValue().values());
            }
        }
    }

    public static void writeToFile(FileWriter fileWriter, String setName, List<OrderPar> orderPars) throws IOException {
        for (var orderPar : orderPars) {
            if (!setName.isEmpty()) {
                fileWriter.write(setName + "\t");
            }
            fileWriter.write(orderPar.toString());
            fileWriter.write("\n");
        }
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        Atom atom = resSource.getAtom();
        String polymer = atom.getTopEntity().getName();
        polymer = (polymer == null) || ("null".equals(polymer)) ? "A" : polymer;
        String resNum = String.valueOf(atom.getResidueNumber());
        sBuilder.append(polymer).append("\t").append(resNum).append("\t").append(atom.getName());
        RelaxationValues.appendValueError(sBuilder, value, error, "%.2f");
        RelaxationValues.appendValueError(sBuilder, TauE, TauEerr, "%.2f");
        RelaxationValues.appendValueError(sBuilder, Sf2, Sf2err, "%.2f");
        RelaxationValues.appendValueError(sBuilder, Ss2, Ss2err, "%.2f");
        RelaxationValues.appendValueError(sBuilder, TauF, TauFerr, "%.4f");
        RelaxationValues.appendValueError(sBuilder, TauS, TauSerr, "%.4f");
        RelaxationValues.appendValueError(sBuilder, Rex, Rexerr, "%.2f");
        sBuilder.append("\t").append(model).append("\t").append(modelNum);
        sBuilder.append("\t").append(String.format("%.4f", sumSqErr)).append("\t").
                append(String.format("%.4f", getReducedChiSqr())).
                append("\t").append(String.format("%.4f", getAIC())).append("\t").
                append(nValues).append("\t").append(nPars);
        return sBuilder.toString();
    }

    public void valuesToStarString(StringBuilder sBuilder) {
        String defaultValue = "      . ";

        for (int i = 0; i < orderParLoopStrings.length; i += 2) {
            String fullName = orderParLoopStrings[i];
            String format = orderParLoopStrings[i + 1];
            if (fullName.endsWith("_val")) {
                String parName = fullName.substring(0, fullName.length() - 4);
                RelaxationValues.appendValue(sBuilder, getValue(parName), format, defaultValue);
            } else if (fullName.endsWith("_val_fit_err")) {
                String parName = fullName.substring(0, fullName.length() - 12);
                RelaxationValues.appendValue(sBuilder, getError(parName), format, defaultValue);
            } else {
                switch (fullName) {
                    case "Model_free_sum_squared_errs":
                        if (sumSqErr != null) {
                            sBuilder.append(String.format(format, sumSqErr));
                        } else {
                            sBuilder.append(defaultValue);
                        }
                        break;
                    case "Model_free_n_values":
                        if (nValues != null) {
                            sBuilder.append(String.format(format, nValues));
                        } else {
                            sBuilder.append(defaultValue);
                        }
                        break;
                    case "Model_free_n_pars":
                        if (nPars != null) {
                            sBuilder.append(String.format(format, nPars));
                        } else {
                            sBuilder.append(defaultValue);
                        }
                        break;
                    case "Model_fit":
                        sBuilder.append(String.format(" %8s", model));
                        break;
                    default:
                        sBuilder.append(defaultValue);
                }
            }
        }
    }

    public static List<String> getOrderParLoopString() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < orderParLoopStrings.length; i += 2) {
            result.add(orderParLoopStrings[i]);
        }
        return result;
    }
}
