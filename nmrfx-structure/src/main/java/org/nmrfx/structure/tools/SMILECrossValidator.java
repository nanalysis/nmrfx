package org.nmrfx.structure.tools;

import smile.validation.CrossValidation;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.regression.LASSO;
import smile.regression.LinearModel;
import smile.validation.RegressionValidations;


public class SMILECrossValidator {
    Formula formula;
    DataFrame dataFrame;
    double lamVal;


    public SMILECrossValidator(Formula formula, DataFrame dataFrame, double lamVal) {
        this.formula = formula;
        this.dataFrame = dataFrame;
        this.lamVal = lamVal;
    }

    public RegressionValidations<LinearModel> cv(int n) {
        return CrossValidation.regression(n, formula, dataFrame, (f, d) -> LASSO.fit(f, d, lamVal));
    }

}
