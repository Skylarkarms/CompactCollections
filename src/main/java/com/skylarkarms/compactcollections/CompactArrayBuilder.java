package com.skylarkarms.compactcollections;

import java.util.Arrays;
import java.util.function.IntFunction;

public interface CompactArrayBuilder<E> {
    static<E> CompactArrayBuilder<E> ofSize(int initialSize, IntFunction<E[]> component) {
        return new CompactArrayBuilderImpl<>(initialSize, component);
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
    }
    private static int getCeil(int length) {
        return (int) Math.ceil(length * 1.5);
    }

}