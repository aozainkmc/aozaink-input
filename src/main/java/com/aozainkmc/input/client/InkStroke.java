package com.aozainkmc.input.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InkStroke {
    private final List<InkStrokePoint> points = new ArrayList<>();
    private final boolean fromBack;

    public InkStroke() {
        this(false);
    }

    public InkStroke(boolean fromBack) {
        this.fromBack = fromBack;
    }

    public void add(InkStrokePoint point) { points.add(point); }
    public boolean isEmpty() { return points.isEmpty(); }
    public InkStrokePoint last() { return points.isEmpty() ? null : points.get(points.size() - 1); }
    public List<InkStrokePoint> points() { return Collections.unmodifiableList(points); }
    public int size() { return points.size(); }
    public boolean fromBack() { return fromBack; }
}
