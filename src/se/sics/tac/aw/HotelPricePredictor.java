package se.sics.tac.aw;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.regression.*;

import java.util.Arrays;

/**
 * Predict the behaviour(increase/decrease) of flight tickets depending on the given data.
 */
public class HotelPricePredictor {

    // degree of the plynomial used in curve fitting
    private final int degree = 1;

    // length of the data we have
    private int length;

    // will hold prediction coefs once we get values
    private RealMatrix coef = null;

    /**
     * constructor
     *
     * @param y
     */
    public HotelPricePredictor(long[] x, double[] y) {

        length = y.length;
        double[][] xData = new double[length][];

        // the implementation determines how to produce a vector of predictors from a single x
        for (int i = 0; i < length; i++) {
            xData[i] = xVector(x[i]);
        }

        OLSMultipleLinearRegression ols = new OLSMultipleLinearRegression();
        // let the implementation include a constant in xVector if desired
        ols.setNoIntercept(true);
        // provide the data to the model
        ols.newSampleData(y, xData);
        // get our coefs
        coef = MatrixUtils.createColumnRealMatrix(ols.estimateRegressionParameters());
    }

    /**
     * create vector of values from x
     *
     * @param x
     * @return
     */
    private double[] xVector(long x) {

        // {1, x, x*x, x*x*x, ...}
        double[] poly = new double[degree + 1];
        double xi = 1;
        for (int i = 0; i <= degree; i++) {
            poly[i] = xi;
            xi *= x;
        }
        return poly;
    }

    /**
     * predict the value of y given x
     *
     * @param x
     * @return
     */
    public int predict(long x) {

        // apply coefs to xVector
        int yhat = (int) coef.preMultiply(xVector(x))[0];
        return yhat;
    }
}