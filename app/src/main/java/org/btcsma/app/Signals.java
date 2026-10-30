package org.btcsma.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/** Pure calculation: all input candles must already be CLOSED, in ascending order. */
public final class Signals {
    public static final int PERIOD = 200;
    public record Candle(long time, double close) {}
    public record Point(long time, double close, double sma) {}
    public record Crossing(Point point, int direction) {}
    public record State(long time, int side) {}
    public record Evaluation(State state, List<Crossing> crossings, boolean gap) {}

    public static List<Point> points(List<Candle> candles) {
        List<Point> result = new ArrayList<>();
        double sum = 0;
        long previous = -1;
        for (int i = 0; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            if (candle.time <= previous || !Double.isFinite(candle.close) || candle.close <= 0)
                throw new IllegalArgumentException("Candles inválidos ou fora de ordem");
            previous = candle.time;
            sum += candle.close;
            if (i >= PERIOD) sum -= candles.get(i - PERIOD).close;
            if (i >= PERIOD - 1) result.add(new Point(candle.time, candle.close, sum / PERIOD));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("São necessários 200 candles fechados");
        return result;
    }

    public static Evaluation evaluate(List<Point> points, State previous, long intervalSeconds) {
        if (points.isEmpty()) throw new IllegalArgumentException("Sem dados");
        Point last = points.get(points.size() - 1);
        // First run establishes a baseline; no historical alert flood.
        if (previous == null) return new Evaluation(new State(last.time, side(last)), Collections.emptyList(), false);
        if (last.time < previous.time) throw new IllegalArgumentException("Resposta antiga da fonte");
        int side = previous.side;
        long time = previous.time;
        List<Crossing> events = new ArrayList<>();
        boolean gap = false;
        for (Point point : points) {
            if (point.time <= time) continue;
            int next = side(point);
            if (point.time - time != intervalSeconds) {
                // Cannot infer a crossing through missing candles: restart the baseline.
                gap = true;
                side = next;
            } else if (next != 0) {
                if (side != 0 && side != next) events.add(new Crossing(point, next));
                side = next;
            }
            time = point.time;
        }
        return new Evaluation(new State(time, side), events, gap);
    }

    private static int side(Point point) {
        double delta = point.close - point.sma;
        return Math.abs(delta) < 0.0000001 ? 0 : delta > 0 ? 1 : -1;
    }
}
