package org.nmrfx.processor.optimization;
import smile.validation.Validation;
import smile.validation.CrossValidation;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.regression.DataFrameRegression;
import smile.regression.LASSO;


public class SMILECrossValidator {
     Formula formula;
     DataFrame dataFrame;
     double lamVal;


     public SMILECrossValidator(Formula formula, DataFrame dataFrame, double lamVal) {
         this.formula = formula;
         this.dataFrame = dataFrame;
         this.lamVal = lamVal;
     }

    public double[] cv(int n) {
          return CrossValidation.regression(n, formula, dataFrame, (f,d) -> LASSO.fit(f,d,lamVal));
    }
    
}
