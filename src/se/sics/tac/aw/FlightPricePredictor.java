package se.sics.tac.aw;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.regression.*;

import java.util.Arrays;

/**
 * Predict the behaviour(increase/decrease) of flight tickets depending on the given data.
 */
public class FlightPricePredictor {

    // degree of the plynomial used in curve fitting
    private final int degree;

    // length of the data we have
    private int length;

    // will hold prediction coefs once we get values
    private RealMatrix coef = null;

    /**
     * constructor
     * @param y
     */
    public FlightPricePredictor(double[] y) {

        length = y.length;

        // degree of the polynomial according to
        // the amount of data given
        int l = length / 6;
        if (l <7){
            degree = 1;
        }else if (l < 8){
            degree = 2;
        }else{
            degree = 3;
        }

        double[] x = new double[length];
        double[][] xData = new double[length][];

        // the implementation determines how to produce a vector of predictors from a single x
        for (int i = 0; i < length; i++) {
            x[i] = i;
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
     * @param x
     * @return
     */
    private double[] xVector(double x) {

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
     * @param x
     * @return
     */
    private double predict(double x) {

        // apply coefs to xVector
        double yhat = coef.preMultiply(xVector(x))[0];
        return yhat;
    }

    /**
     * Predict x at which we have min y among of the remaining y values. Upon it, return boolean if to buy now or should wait.
     *
     * @return
     */
    public boolean shouldBuy() {

        int l = 54 - length;
        double[] y = new double[l];
        int x = length;

        double yMin = 1000;
        int xMin = 0;

        for (int i = 0; i < l; i++) {
            y[i] = predict(x);
            if (y[i] < yMin) {
                yMin = y[i];
                xMin = x;
            }
            x ++;
        }

        boolean should_buy = (xMin == length);
        return should_buy;
    }
}