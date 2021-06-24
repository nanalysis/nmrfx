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

import org.nmrfx.chemistry.Atom;

/**
 *
 * @author brucejohnson
 */
public class OrderPar implements RelaxationValues {

    static final String[] PAR_NAMES = {"S2", "Tau_e", "Tau_f", "Tau_s", "Rex", "Sf2", "Ss2", "model"};
    private Atom atom;
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
    private Double sumSqErr;
    private String model;
    private Double modelNum;

    public OrderPar(Atom atom, Double[] values, Double[] errs, Double sumSqErr, String model) {
        this(atom,
                values[0], errs[0],
                values[1], errs[1],
                values[2], errs[2],
                values[3], errs[3],
                values[4], errs[4],
                values[5], errs[5],
                values[6], errs[6],
                sumSqErr, model
        );
    }

    public OrderPar(Atom atom, Double value, Double error, Double sumSqErr, String model) {
        this(atom, value, error, null, null, null, null, null, null, null,
                null, null, null, null, null, sumSqErr, model);

    }

    public OrderPar(Atom atom, Double sumSqErr, String model) {
        this(atom, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, sumSqErr, model);

    }

    public OrderPar(Atom atom, Double value, Double error, Double TauE,
            Double TauEerr, Double TauF, Double TauFerr, Double TauS,
            Double TauSerr, Double Rex, Double Rexerr, Double Sf2,
            Double Sf2err, Double Ss2, Double Ss2err,
            Double sumSqErr, String model) {
        this.atom = atom;
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
        this.model = model;
    }

    public OrderPar rEx(Double val, Double err) {
        return new OrderPar(atom, value, error,
                TauE, TauEerr,
                TauF, TauFerr,
                TauS, TauSerr,
                val, err,
                Sf2, Sf2err,
                Ss2, Ss2err,
                sumSqErr, model);
    }

    public OrderPar set(String name, Double val, Double err) {
        OrderPar newPar = new OrderPar(atom, value, error,
                TauE, TauEerr,
                TauF, TauFerr,
                TauS, TauSerr,
                Rex, Rexerr,
                Sf2, Sf2err,
                Ss2, Ss2err,
                sumSqErr, model);
        switch (name) {
            case "S2":
                newPar.value = val;
                newPar.error = err;
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
                break;
            case "Ss2":
                newPar.Ss2 = val;
                newPar.Ss2err = err;
                break;
            case "model":
                newPar.modelNum = val;
                break;
        }
        return newPar;
    }

    @Override
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
    public Atom getAtom() {
        return atom;
    }

    @Override
    public Double getValue() {
        return value;
    }

    @Override
    public Double getError() {
        return error;
    }
//    static final String[] PAR_NAMES = {"Order_param", "Tau_e", "Tau_f", "Tau_s", "Rex", "Sf2", "Ss2"};

    @Override
    public Double getValue(String name) {
        switch (name) {
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
            default:
                return null;
        }
    }

    @Override
    public Double getError(String name) {
        switch (name) {
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

    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(value).append(" ");
        sBuilder.append(TauE).append(" ");
        sBuilder.append(TauF).append(" ");
        sBuilder.append(TauS).append(" ");
        sBuilder.append(Rex).append(" ");
        sBuilder.append(Sf2).append(" ");
        sBuilder.append(Ss2);
        return sBuilder.toString();
    }
}
