/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.chemistry;

/**
 *
 * @author brucejohnson
 */
public class OrderPar {

    static final String[] PAR_NAMES = {"Order_param", "Tau_e", "Tau_f", "Tau_s", "Rex", "Sf2", "Ss2"};

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

    public OrderPar(Double[] values, Double[] errs, Double sumSqErr, String model) {
        this(
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

    public OrderPar(Double value, Double error, Double TauE, Double TauEerr, Double TauF, Double TauFerr, Double TauS, Double TauSerr, Double Rex, Double Rexerr, Double Sf2, Double Sf2err, Double Ss2, Double Ss2err, Double sumSqErr, String model) {
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

    public static String[] getParNames() {
        return PAR_NAMES;
    }
}
