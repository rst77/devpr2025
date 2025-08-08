package com.r13a.devpr2025.lista;

import java.util.Arrays;
import java.util.List;

public class ListChangeEvent<T> {
    private final List<T> source;
    private final List<T> changeList;
    private final boolean wasAdded;
    private final int to, from;

    public ListChangeEvent(List<T> source, int from, int to, boolean wasAdded, T... changeItems) {
        this(source, from, to, wasAdded, Arrays.asList(changeItems));
    }

    public ListChangeEvent(List<T> source, int from, int to, boolean wasAdded, List<T> changeItems) {
        this.source = source;
        this.changeList = changeItems;
        this.wasAdded = wasAdded;
        this.to = to;
        this.from = from;
    }

    public ListChangeEvent(List<T> source) {
        this.source = source;
        this.changeList = null;
        this.wasAdded = false;
        this.to = -1;
        this.from = -1;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }

    public List<T> getSource() {
        return source;
    }

    public List<T> getChangeList() {
        return changeList;
    }

    public boolean wasAdded() {
        return wasAdded;
    }

    public boolean wasRemoved() {
        return !wasAdded;
    }
}