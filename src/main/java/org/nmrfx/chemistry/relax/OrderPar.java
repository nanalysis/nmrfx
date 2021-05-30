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

    static final String[] PAR_NAMES = {"Order_param", "Tau_e", "Tau_f", "Tau_s", "Rex", "Sf2", "Ss2"};
    Atom atom;
    Double value;
    Double error;
    Double TauE;
    Double TauEerr;
    Double TauF;
    Double TauFerr;
    Double TauS;
    Double TauSerr;
    Double Rex;
    Double Rexerr;
    Double Sf2;
    Double Sf2err;
    Double Ss2;
    Double Ss2err;
    Double sumSqErr;
    String model;

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

    @Override
    public String getName() {
        return "OrderPar";
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
            case "Order_param":
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
            default:
                return null;
        }
    }

    @Override
    public Double getError(String name) {
        switch (name) {
            case "Order_param":
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
}
