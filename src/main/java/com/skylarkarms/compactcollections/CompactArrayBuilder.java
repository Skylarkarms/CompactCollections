package com.skylarkarms.compactcollections;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

public interface CompactArrayBuilder<E> extends Iterable<E> {
    static<E> CompactArrayBuilder<E> ofSize(int initialSize, IntFunction<E[]> component) {
        return new CompactArrayBuilderImpl<>(initialSize, component);
    }

    /**
     * @param maxSize the maximum size of this collection.
     *                Once the size has been reached... the index will go back to 0 and begin counting again.
     * @param initialCapacity the initial capacity of the inner array, before
     *                        incurring in a {@link System#arraycopy(Object, int, Object, int, int)} for array expansion.
     * */
    static<E> CompactArrayBuilder<E> atomic(int maxSize, int initialCapacity, IntFunction<E[]> component) {
        return new Atomic<>(maxSize, initialCapacity, component);
    }

    static<E> CompactArrayBuilder<E> atomic(int initialCapacity, IntFunction<E[]> component) {
        return new Atomic<>(Integer.MAX_VALUE, initialCapacity, component);
    }

    void add(E element);
    int indexedAdd(E element);
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
                int[] cur = intArr;
                int il = cur.length;
                int nextI = index++;
                if (nextI >= il) {
                    int newLength = getCeil(il);
                    int[] copy = new int[newLength];
                    System.arraycopy(cur, 0, copy, 0,
                                     il
                    );
                    copy[nextI] = anInt;
                    intArr = copy;
                } else {
                    cur[nextI] = anInt;
                }
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

            OfIntMatrixImpl(int rowCapacity, int maxColumns) { this.intArr = new int[rowCapacity][maxColumns]; }

            @Override
            public void addRow(int... values) {
                if (index >= intArr.length) {
                    intArr = Arrays.copyOf(intArr, getCeil(intArr.length), intArr.getClass());
                }
                System.arraycopy(values, 0, intArr[index++], 0, values.length);
            }

            @Override
            public void addRow(int value1, int value2) {
                if (index >= intArr.length) {
                    intArr = Arrays.copyOf(intArr, getCeil(intArr.length), intArr.getClass());
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
        private final IntFunction<E[]> copyProvider;

        CompactArrayBuilderImpl(int initialSize, IntFunction<E[]> component) {
            this.array = component.apply(initialSize);
            this.copyProvider = component;
        }

        @Override
        public void add(E element) {
            E[] cur = array;
            int cl = cur.length;
            int curI = index++;
            if (curI >= cl) {
                int newCeil = getCeil(cl);
                E[] copy = copyProvider.apply(newCeil);
                System.arraycopy(cur, 0, copy, 0, cl);
                copy[curI] = element;
                array = copy;
            } else {
                cur[curI] = element;
            }
        }

        @Override
        public int indexedAdd(E element) {
            E[] cur = array;
            int cl = cur.length;
            int toInd = index++;
            if (toInd >= cl) {
                int newCeil = getCeil(cl);
                E[] copy = copyProvider.apply(newCeil);
                System.arraycopy(cur, 0, copy, 0, cl);
                copy[toInd] = element;
                array = copy;
            } else {
                cur[toInd] = element;
            }
            return toInd;
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

        private final AtomicInteger _index = new AtomicInteger(-1);
        private final int maxSize;
        private final IntSupplier indexSupplier;
        private final IntFunction<E[]> copyProvider;


        private volatile E[] array;
        static final VarHandle ARRAY;
        static {
            try {
                ARRAY = MethodHandles.lookup().findVarHandle(Atomic.class, "array", Object[].class);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
        Atomic(int maxSize, int initialCapacity, IntFunction<E[]> component) {
            if (maxSize < 0) throw new IllegalStateException("maxSie cannot be lesser than 0");
            if (initialCapacity > maxSize) throw new IllegalStateException("`initialCapacity` cannot be larger than `maxSize`");
            this.indexSupplier = maxSize == Integer.MAX_VALUE ?
                    _index::incrementAndGet
                    :
                    () -> {
                        int prev = _index.get(), index = prev == maxSize ? 0 : (prev + 1);
                        if (!_index.weakCompareAndSetVolatile(prev, index)) {
                            int wit;
                            do {
                                wit = _index.get();
                                if (wit != prev) {
                                    prev = wit;
                                    index = wit == maxSize ? 0 : (wit + 1);
                                }
                            } while (!_index.weakCompareAndSetVolatile(prev, index));
                        }
                        return index;
                    };
            this.maxSize = maxSize;
            this.array = component.apply(initialCapacity);
            this.copyProvider = component;
        }


        @SuppressWarnings("unchecked")
        @Override
        public void add(E element) {
            int index = indexSupplier.getAsInt();
            E[] arr = array;
            int al = arr.length;
            if (index >= al) {
                int ceil = getCeil(al);

                E[] next = copyProvider.apply(ceil);
                System.arraycopy(arr, 0, next, 0, al);

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

        @SuppressWarnings("unchecked")
        @Override
        public int indexedAdd(E element) {
            int index = indexSupplier.getAsInt();
            E[] arr = array;
            int al = arr.length;
            if (index >= al) {
                int ceil = getCeil(al);

                E[] next = copyProvider.apply(ceil);
                System.arraycopy(arr, 0, next, 0, al);

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
                                return index;
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
            return index;
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
            E[] res;
            while (wit != array || l != _index.get()) {
                wit = array;
                l = _index.get();
            }
            assert wit != null;
            int newLength = l + 1;
            res = copyProvider.apply(newLength);
            System.arraycopy(wit, 0, res, 0, newLength);
            return res;
        }

        /**
         * May require read synchronization
         * */
        @Override
        public Iterator<E> iterator() {
            int l = -2;
            E[] wit = null;
            final ArrayWindow<E> res;
            // attempts cross field stabilization.
            while (wit != array || l != _index.get()) {
                wit = array;
                l = _index.get();
            }
            res = new ArrayWindow<>(wit, 0, l);
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
                    "maxSize=" + maxSize +
                    ", array=" + CompactArrayBuilder.toString(wit, l + 1) +
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