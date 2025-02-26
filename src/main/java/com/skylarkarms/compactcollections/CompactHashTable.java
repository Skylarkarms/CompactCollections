package com.skylarkarms.compactcollections;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class CompactHashTable<K, T> implements Iterable<CompactHashTable<K, T>.Node> {
    private static final int MIN_SIZE = 16;

    private final Node[] table;
    private final int last_i;
    private Node head, tail;
    private int size;

    record NodeRecord<K, V>(K key, V value){}

    @SuppressWarnings("unchecked")
    @SafeVarargs
    public CompactHashTable(NodeRecord<K, T>... elements) {
        this.table = new CompactHashTable.Node[tableSizeFor(elements.length)];
        last_i = table.length - 1;
        for (int i = 0; i < elements.length; i++) {
            NodeRecord<K, T> e = elements[i];
            put(e.key, e.value);
        }
    }

    public CompactHashTable() { this(MIN_SIZE); }

    @SuppressWarnings("unchecked")
    public CompactHashTable(int capacity) {
        int l;
        this.table = new CompactHashTable.Node[l = tableSizeFor(capacity)];
        last_i = l - 1;
    }

    public final class Node {
        final int hash;
        public final K key;
        T value;
        Node next = null;        // Global spine pointer
        Node bucketNext = null;  // Collision chain pointer
        Node bucketTail;

        public T getValue() { return value; }

        Node(
                int hash,
                K key, T value
        ) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.bucketTail = this;
        }

        @Override
        public String toString() {
            return "Node{" +
                    "\n key=" + key +
                    "\n value=" + value +
                    "\n }";
        }
    }

    public void put(K key, T element) {
        final int hash;
        final int i = last_i &
                ((hash = key.hashCode()) ^ (hash >>> 16));

        if (size != 0) {
            Node bucket;
            if ((bucket = table[i]) == null) {
                // First entry in bucket
                tail = tail.next = table[i] = new Node(hash, key, element);
            } else {
                K t_k;
                if (
                        bucket.hash == hash
                                &&
                                ((t_k = bucket.key) == key
                                        || t_k.equals(key))
                )   {
                    // Update existing key's value
                    bucket.value = element;
                    return;
                }
                else if (bucket.bucketNext != null) {
                    do {
                        bucket = bucket.bucketNext;
                        if (bucket.hash == hash
                                &&
                                ((t_k = bucket.key) == key
                                        || t_k.equals(key))
                        ) {
                            // Update existing key's value
                            bucket.value = element;
                            return;
                        }
                    } while (bucket.bucketNext != null);
                }
                tail = tail.next = bucket.bucketNext = new Node(hash, key, element);
            }
        } else {
            tail = head = table[i] = new Node(hash, key, element);
        }
        size++;
    }

    /**
     * Should only be used on collections where it is assured uniqueness between Keys.
     * <p> If keys of same hashcode are added, the element will be appended at the end of the bucket.
     * NO UPDATES will EVER be performed on Key matches.
     * <p> {@link #contains(Object)} and {@link #get(Object)} behavior:
     * <p> On hash collisions the Key must be different from other keys of similar hashes by address comparison ({@code `==`})
     * or by {@link Object#equals(Object)} distinction.
     * If the exact same key instance is added twice, the {@link #contains(Object)} or {@link #get(Object)} will return the
     * FIRST object added to the bucket that was inserted in the collection.
     * The key will never be able to be retrieved... unless the entire collection is iterated.
     * */
    //explicit assignment faster than "duped" (chained assignment)
    public void addDistinct(K key, T element) {
        final int hash = key.hashCode();
        final int i = last_i & (hash ^ (hash >>> 16));
        if (size != 0) {
            Node h;
            if ((h = table[i]) == null) {
                // First entry in bucket
                Node nh = new Node(hash, key, element);
                table[i] = nh;
                Node prevTail = tail;
                tail = nh;
                prevTail.next = nh;
            } else {
                Node newNode = new Node(hash, key, element);
                h.bucketTail.bucketNext = newNode;
                h.bucketTail = newNode;
                Node prevTail = tail;
                tail = newNode;
                prevTail.next = newNode;
            }
        } else {
            Node nn = new Node(hash, key, element);
            table[i] = nn;
            head = nn;
            tail = nn;
        }
        size++;
    }

    public boolean contains(K key) {
        Node h;
        final int hash = key.hashCode(); //hoisting before operation seems to perform better
        if ((h = table[
                last_i & (hash ^ (hash >>> 16))
                ]) != null) {
            K t_k = h.key;
            if (
                    h.hash == hash
                            &&
                            (
                                    t_k == key
                                            ||
                                            t_k.equals(key)
                            )
            ) return true;
            h = h.bucketNext;
            while (h != null) {
                t_k = h.key;
                if (
                        h.hash == hash
                                &&
                                (
                                        t_k == key
                                                ||
                                                t_k.equals(key)
                                )
                ) return true;
                h = h.bucketNext;
            }
        }
        return false;
    }

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * Returns a power of two size for the given target capacity.
     */
    static final int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    public T get(K key) {
        assert key != null;
        int hash = key.hashCode();
        Node bucket;
        if ((bucket = table[
                last_i & (hash ^ (hash >>> 16))
                ]) != null) {
            K t_k;
            if (
                    bucket.hash == hash
                            &&
                            (
                                    (t_k = bucket.key) == key
                                            ||
                                            t_k.equals(key)
                            )
            ) return bucket.value;
            while ((bucket = bucket.bucketNext) != null) {
                if (
                        bucket.hash == hash
                                &&
                                (
                                        (t_k = bucket.key) == key
                                                ||
                                                t_k.equals(key)
                                )
                ) return bucket.value;
            }
        }
        return null;
    }

    public Node getNode(K key) {
        assert key != null;
        int hash = key.hashCode();
        Node bucket;
        if ((bucket = table[
                last_i & (hash ^ (hash >>> 16))
                ]) != null) {
            K t_k;
            if (
                    bucket.hash == hash
                            &&
                            (
                                    (t_k = bucket.key) == key
                                            ||
                                            t_k.equals(key)
                            )
            ) return bucket;
            while ((bucket = bucket.bucketNext) != null) {
                if (
                        bucket.hash == hash
                                &&
                                (
                                        (t_k = bucket.key) == key
                                                ||
                                                t_k.equals(key)
                                )
                ) return bucket;
            }
        }
        return null;
    }

    public int size() { return size; }

    public boolean isEmpty() { return size == 0; }

    // Iterator remains the same as it uses the global spine
    public class ValueIterator implements Iterator<T> {
        private Node current = head;

        @Override
        public boolean hasNext() { return current != null; }

        @Override
        public T next() {
            if (!hasNext()) throw new NoSuchElementException();
            Node lastReturned = current;
            current = current.next;
            return lastReturned.value;
        }
    }

    public class KeyIterator implements Iterator<K> {
        private Node current = head;

        @Override
        public boolean hasNext() { return current != null; }

        @Override
        public K next() {
            if (!hasNext()) throw new NoSuchElementException();
            Node lastReturned = current;
            current = current.next;
            return lastReturned.key;
        }
    }

    public class NodeIterator implements Iterator<Node> {
        private Node current = head;

        @Override
        public boolean hasNext() { return current != null; }

        @Override
        public Node next() {
            if (!hasNext()) throw new NoSuchElementException();
            Node lastReturned = current;
            current = current.next;
            return lastReturned;
        }
    }

    @Override
    public NodeIterator iterator() { return new NodeIterator(); }

    public ValueIterator valueIterator() { return new ValueIterator(); }

    public KeyIterator keyIterator() { return new KeyIterator(); }

}