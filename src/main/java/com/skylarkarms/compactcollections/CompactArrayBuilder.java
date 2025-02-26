package com.skylarkarms.compactcollections;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

public interface CompactArrayBuilder<E> extends Iterable<E> {
    static<E> CompactArrayBuilder<E> ofSize(int initialSize, IntFunction<E[]> component) {
        return new CompactArrayBuilderImpl<>(initialSize, component);
    }

    static<E> CompactArrayBuilder<E> atomic(int initialSize, IntFunction<E[]> component) {
        return new Atomic<>(initialSize, component);
    }

    void add(E element);
    E get(int index);
    int size();
    boolean equals(E[] that);

    E[] publish();

    abstract class IndexMem {
        int index = 0;
    }

    interface OfInt {

        static<E> OfInt ofSize(int initialSize) { return new OfIntImpl(initialSize); }
        void add(int anInt);
        int[] publish();

        final class OfIntImpl
                extends IndexMem
                implements OfInt {

            private int[] intArr;

            OfIntImpl(int initialSize) { this.intArr = new int[initialSize]; }

            @Override
            public void add(int anInt) {
                if (index >= intArr.length) {
                    intArr = Arrays.copyOf(intArr, getCeil(intArr.length));
                }
                intArr[index++] = anInt;
            }

            @Override
            public int[] publish() { return Arrays.copyOf(intArr, index); }
        }
    }

    interface OfIntMatrix {

        static<E> OfIntMatrix ofSize(int rowCapacity, int maxColumns) {
            return new OfIntMatrixImpl(rowCapacity, maxColumns);
        }
        void addRow(int... values);
        /**
         * @throws IndexOutOfBoundsException if the {@code `maxColumn`} parameter is lesser than 2.
         * */
        void addRow(int value1, int value2);
        int[][] publish();

        final class OfIntMatrixImpl
                extends IndexMem
                implements OfIntMatrix {

            private int[][] intArr;

            OfIntMatrixImpl(int rowCapacity, int maxColumns) {
                this.intArr = new int[rowCapacity][maxColumns];
            }

            @Override
            public void addRow(int... values) {
                if (index >= intArr.length) {
                    intArr = Arrays.copyOf(intArr, getCeil(intArr.length));
                }
                System.arraycopy(values, 0, intArr[index++], 0, values.length);
            }

            @Override
            public void addRow(int value1, int value2) {
                if (index >= intArr.length) {
                    intArr = Arrays.copyOf(intArr, getCeil(intArr.length));
                }
                int[] row;
                (row = intArr[index++])[0] = value1;
                row[1] = value2;
            }

            @Override
            public int[][] publish() { return Arrays.copyOf(intArr, index); }
        }
    }

    final class CompactArrayBuilderImpl<E>
            extends IndexMem
            implements CompactArrayBuilder<E> {
        private E[] array;

        CompactArrayBuilderImpl(int initialSize, IntFunction<E[]> component) {
            this.array = component.apply(initialSize);
        }

        @Override
        public void add(E element) {
            if (index >= array.length) {
                array = Arrays.copyOf(array, getCeil(array.length));
            }
            array[index++] = element;
        }

        @Override
        public E get(int index) { return array[index]; }

        @Override
        public int size() { return index; }

        @Override
        public boolean equals(E[] that) { return Arrays.equals(array, that); }

        @Override
        public E[] publish() { return Arrays.copyOf(array, index); }

        @Override
        public Iterator<E> iterator() {
            ArrayWindow<E> window = new ArrayWindow<>(array, 0, index - 1);
            return window.iterator();
        }

        @Override
        public String toString() {
            return "CompactArrayBuilderImpl{" +
                    "array=" + CompactArrayBuilder.toString(array, index) +
                    '}';
        }
    }

    final class Atomic<E> implements CompactArrayBuilder<E> {

        private final AtomicInteger _index = new AtomicInteger();
        private volatile E[] array;
        static final VarHandle ARRAY;
        static {
            try {
                ARRAY = MethodHandles.lookup().findVarHandle(Atomic.class, "array", Object[].class);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
        Atomic(int initialSize, IntFunction<E[]> component) {
            this.array = component.apply(initialSize);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void add(E element) {
            int index =_index.getAndIncrement();
            E[] arr = array;
            if (index >= arr.length) {
                int ceil = getCeil(arr.length);
                E[] next;
                next = Arrays.copyOf(arr, ceil);
                if ((arr = (E[]) ARRAY.compareAndExchange(this, arr, next)) == next) {
                    next[index] = element;
                }
                else {
                    //We must compete for the ceiling instead of competing for an index position.
                    // competing for the index may leave the element placed inside an abandoned array.
                    // while competing for the ceiling ensures that only the last set array is the one receiving the element.
                    if (arr.length < ceil) {
                        do {
                            if (ARRAY.compareAndSet(this, arr, next)) {
                                next[index] = element;
                                return;
                            }
                            arr = array;
                        } while (
                                arr.length < ceil
                        );
                    }
                    arr[index] = element;
                }
            } else {
                arr[index] = element;
            }
        }

        @Override
        public E get(int index) {
            return array[index];
        }

        @Override
        public int size() {
            return array.length;
        }

        @Override
        public boolean equals(E[] that) {
            return Arrays.equals(array, that);
        }

        /**
         * May require read synchronization
         * */
        @Override
        public E[] publish() {
            int l = - 1;
            E[] wit = null;
            E[] res = null;
            while (wit != array || l != _index.get()) {
                wit = array;
                l = _index.get();
                res = Arrays.copyOf(wit, l);
            }
            return res;
        }

        /**
         * May require read synchronization
         * */
        @Override
        public Iterator<E> iterator() {
            int l = - 1;
            E[] wit = null;
            final ArrayWindow<E> res;
            // attempts cross field stabilization.
            while (wit != array || l != _index.get()) {
                wit = array;
                l = _index.get();
            }
            res = new ArrayWindow<>(wit, 0, l - 1);
            return res.iterator();
        }

        /**
         * May require read synchronization.
         * */
        @Override
        public String toString() {
            int l = - 1;
            E[] wit = null;
            // attempts cross field stabilization.
            while (wit != array || l != _index.get()) {
                wit = array;
                l = _index.get();
            }
            return "Atomic{" +
                    "array=" + CompactArrayBuilder.toString(wit, l) +
                    '}';
        }
    }

    private static int getCeil(int length) { return (int) Math.ceil(length * 1.5); }

    private static<S> String toString(S[] arr, int l) {
        assert l > -1;
        if (arr == null || l == 0) return "{}";
        else {
            final StringBuilder builder = new StringBuilder(l);
            builder.append("{").append(arr[0]);
            for (int i = 1; i < l; i++) {
                builder.append(", ").append(arr[i]);
            }
            return builder.append("}").toString();
        }
    }

}