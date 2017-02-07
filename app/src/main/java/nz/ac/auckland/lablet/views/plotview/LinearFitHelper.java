package nz.ac.auckland.lablet.views.plotview;

import org.jetbrains.annotations.Contract;
import org.opencv.core.Point;

/**
 * Created by pfre484 on 3/02/17.
 */

public class LinearFitHelper {
    /**
     * Interface for lambda expressions fitting the standard linear form:
     *      y = mx + b
     */
    interface LinearFunction {
        double eval(double x);
    }

    /**
     * Calculates R-squared value for the linear fit.
     *
     * This is an overloaded method to provide R-squared values
     * for {@link AbstractXYDataAdapter} objects.
     *
     * @param function  lambda function for the linear function
     * @param data      the XY data being considered
     * @return          the coefficient of determination (R-squared)
     */
    public static double calcRSquared(LinearFunction function, AbstractXYDataAdapter data) {
        if (data == null || function == null)
            return 0;
        Point[] list = new Point[data.getSize()];
        for (int i = 0; i < data.getSize(); i++) {
            list[i].x = data.getX(i).doubleValue();
            list[i].y = data.getY(i).doubleValue();
        }
        return calcRSquared(function, list);
    }

    /**
     * Calculates R-squared value for the linear fit.
     *
     * Algorithm mirrors that from
     * <a href="https://en.wikipedia.org/wiki/Coefficient_of_determination">Wikipedia</a>.
     *
     * @param function  lambda function for the linear function
     * @param array     array of points to use for calculation
     * @return          the coefficient of determination (R-squared)
     */
    @Contract(pure = true)
    public static double calcRSquared(LinearFunction function, Point[] array) {
        if (function == null)
            return 0;
        double yBar = calcYMean(array);
        double ss_tot = 0;
        double ss_res = 0;
        for (Point p : array) {
            double f = function.eval(p.x);
            ss_tot += (p.y - yBar) * (p.y - yBar);
            ss_res += (p.y - f) * (p.y - f);
        }
        return ss_tot == 0 ? 1 : 1 - (ss_res / ss_tot);
    }

    /**
     * Returns the mean (average) of all y-values in the array.
     *
     * @param data  array of points
     * @return      mean value of y-values
     */
    @Contract(pure = true)
    public static double calcYMean(Point[] data) {
        double sumY = 0;
        for (Point p : data) {
            sumY += p.y;
        }
        return data.length == 0 ? 0 : sumY / data.length;
    }

}
