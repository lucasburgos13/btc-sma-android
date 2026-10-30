package org.btcsma.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SignalsTest {
    private static final long DAY = 86400;
    @Test public void averagesExactly200Closes() {
        List<Signals.Candle> candles = new ArrayList<>();
        for (int i = 1; i <= 201; i++) candles.add(new Signals.Candle(i * DAY, i));
        var points = Signals.points(candles);
        assertEquals(2, points.size());
        assertEquals(100.5, points.get(0).sma(), 1e-9);
        assertEquals(101.5, points.get(1).sma(), 1e-9);
    }
    @Test(expected = IllegalArgumentException.class) public void requires200Candles() {
        Signals.points(Collections.singletonList(new Signals.Candle(1, 10)));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsUnorderedData() {
        Signals.points(Arrays.asList(new Signals.Candle(2, 10), new Signals.Candle(1, 10)));
    }
    @Test public void firstLoadDoesNotAlert() {
        var result = Signals.evaluate(Arrays.asList(new Signals.Point(DAY, 99, 100), new Signals.Point(2 * DAY, 101, 100)), null, DAY);
        assertTrue(result.crossings().isEmpty()); assertEquals(1, result.state().side());
    }
    @Test public void detectsBothDirectionsAndDoesNotRepeat() {
        var points = Arrays.asList(new Signals.Point(2 * DAY, 101, 100), new Signals.Point(3 * DAY, 99, 100));
        var result = Signals.evaluate(points, new Signals.State(DAY, -1), DAY);
        assertEquals(2, result.crossings().size());
        assertEquals(1, result.crossings().get(0).direction());
        assertEquals(-1, result.crossings().get(1).direction());
        assertTrue(Signals.evaluate(points, result.state(), DAY).crossings().isEmpty());
    }
    @Test public void touchingAverageDoesNotLosePreviousSide() {
        var result = Signals.evaluate(Arrays.asList(new Signals.Point(2 * DAY, 100, 100), new Signals.Point(3 * DAY, 101, 100)), new Signals.State(DAY, -1), DAY);
        assertEquals(1, result.crossings().size());
        assertEquals(3 * DAY, result.crossings().get(0).point().time());
    }
    @Test public void noCrossingWhenTouchingAndReturning() {
        var result = Signals.evaluate(Arrays.asList(new Signals.Point(2 * DAY, 100, 100), new Signals.Point(3 * DAY, 99, 100)), new Signals.State(DAY, -1), DAY);
        assertTrue(result.crossings().isEmpty());
    }
    @Test public void recoversMissedWeeklyCloses() {
        long week = 7 * DAY;
        var result = Signals.evaluate(Arrays.asList(new Signals.Point(2 * week, 110, 100), new Signals.Point(3 * week, 90, 100)), new Signals.State(week, -1), week);
        assertEquals(2, result.crossings().size()); assertFalse(result.gap());
    }
    @Test public void missingHistoryResetsBaseline() {
        var result = Signals.evaluate(Collections.singletonList(new Signals.Point(5 * DAY, 110, 100)), new Signals.State(DAY, -1), DAY);
        assertTrue(result.gap()); assertTrue(result.crossings().isEmpty()); assertEquals(1, result.state().side());
    }
    @Test(expected = IllegalArgumentException.class) public void staleResponseCannotRollBackState() {
        Signals.evaluate(Collections.singletonList(new Signals.Point(DAY, 110, 100)), new Signals.State(2 * DAY, -1), DAY);
    }
}
