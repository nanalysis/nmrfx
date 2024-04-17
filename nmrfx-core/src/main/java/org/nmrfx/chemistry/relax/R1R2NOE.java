package org.nmrfx.chemistry.relax;

public class R1R2NOE {
    final Double R1;
    final Double R1err;
    final Double R2;
    final Double R2err;
    final Double NOE;
    final Double NOEerr;
    final Double sf;

    public R1R2NOE(Double r1, Double r1Error, Double r2, Double r2Error, Double noe, Double noeError, Double sf) {
        this.R1 = r1;
        this.R1err = r1Error;
        this.R2 = r2;
        this.R2err = r2Error;
        this.NOE = noe;
        this.NOEerr = noeError;
        this.sf = sf;
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(String.format("%.2f", sf));
        RelaxationValues.appendValueErrorWithSep(sBuilder, R1, R1err, "%.2f", ",");
        RelaxationValues.appendValueErrorWithSep(sBuilder, R2, R2err, "%.2f", ",");
        RelaxationValues.appendValueErrorWithSep(sBuilder, NOE, NOEerr, "%.2f",",");
        return sBuilder.toString();
    }
}
