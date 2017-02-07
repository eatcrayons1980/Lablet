package nz.ac.auckland.lablet.views.plotview;

import org.junit.Test;
import org.opencv.core.Point;

import static org.junit.Assert.*;

/**
 * Created by pfre484 on 3/02/17.
 */
public class LinearFitHelperTest {
    private final Point[] array0 = {};
    private final Point[] array1 = {
            new Point(1.0,1.0) };
    private final Point[] array2 = {
            new Point(1.3,6.5),
            new Point(2.1,7.7) };
    private final Point[] array3 = {
            new Point(1.3,6.5),
            new Point(2.1,7.7),
            new Point(-0.3,0.0),
            new Point(5.4,3.1) };

    @Test
    public void calcRSquared() throws Exception {
        assertEquals(true, true);
    }

    @Test
    public void calcRSquared1() throws Exception {
        assertEquals(LinearFitHelper.calcRSquared((x) -> 1.0 * x + 0.0, array0), 0.0, 0.0);
        assertEquals(LinearFitHelper.calcRSquared((x) -> 1.0 * x + 0.0, array1), 1.0, 0.0);
        assertEquals(LinearFitHelper.calcRSquared((x) -> 1.5 * x + 4.55, array2), 1.0, 0.0);
        assertEquals(LinearFitHelper.calcRSquared((x) -> 0.265944 * x + 3.75987, array3), 0.033657, 0.00001);
    }

    @Test
    public void calcYMean() throws Exception {
        assertEquals(LinearFitHelper.calcYMean(array0), 0.0, 0.0);
        assertEquals(LinearFitHelper.calcYMean(array1), 1.0, 0.0);
        assertEquals(LinearFitHelper.calcYMean(array2), 7.1, 0.0);
        assertEquals(LinearFitHelper.calcYMean(array3), 4.325, 0.0);
    }

}
