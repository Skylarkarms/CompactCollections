package com.skylarkarms.compactcollections;

import java.util.Iterator;

/**
 * Offers a windowed view of an array
 * */
public class ArrayWindow<T> implements Iterable<T> {
    final T[] original;
    final int start, end, width, finalIndex;

    /**
     * @param start from inclusive
     * @param end to inclusive
     * */
    public ArrayWindow(T[] original, int start, int end) {
        this.original = original;
        if (start < 0) throw new IllegalStateException("start cannot be less than zero.");
        this.start = start;
        if (end > original.length) throw new IllegalStateException("end cannot be greater than the length of the original array.");
        this.end = end;
        this.finalIndex = end + 1;
        width = finalIndex - start;
        if (width < 0) throw new IllegalStateException("end cannot be lesser than start.");
    }

    public T get(int index) {
        if (index < 0) throw new IndexOutOfBoundsException("Index cannot be less than 0");
        if (index >= width) throw new IndexOutOfBoundsException("index [" + index + "] greater than length [" + width + "]");
        int windowed = index + start;
        return original[windowed];
    }

    public int length() { return width; }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            int i_start = start;
            @Override
            public boolean hasNext() {
                int next = i_start + 1;
                return next <= finalIndex;
            }

            @Override
            public T next() { return original[i_start++]; }
        };
    }
}
