package com.elijahsarte.celtools.main.util.datastructures;

import com.elijahsarte.celtools.main.util.ProgrammingEx;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

// https://github.com/openjdk/jdk14/blob/master/src/java.base/share/classes/java/util/TreeMap.java
@SuppressWarnings("unchecked")
// allows for: retrieving by index, (later do probabilistic path searching)
public class EnhancedTreeMap<K, V> extends AbstractMap<K, V> implements NavigableMap<K, V>, Cloneable, Serializable {

    private final Comparator<? super K> comparator;
    private transient Entry<K, V> root = null;
    // Cache
    private transient int size = 0;
    private transient Entry<K, V> firstEntry, lastEntry;
    private transient int modCount = 0;

    // Temp
    private transient Entry<K, V> heldEntry;

    private static final Object UNBOUNDED = new Object();

    @Serial
    private static final long serialVersionUID = 919286545866124006L;


    public EnhancedTreeMap() {
        this.comparator = null;
    }
    public EnhancedTreeMap(Comparator<? super K> comparator) {
        this.comparator = comparator;
    }
    public EnhancedTreeMap(Map<? extends K, ? extends V> m) {
        this.comparator = null;
        putAll(m);
    }


    public EnhancedTreeMap(SortedMap<K, ? extends V> m) {
        this.comparator = m.comparator();
        try {
            buildFromSorted(m.size(), m.entrySet().iterator(), null, null);
        } catch (IOException | ClassNotFoundException cannotHappen) {}
        if (root != null) mutSize(root);
    }

    public EnhancedTreeMap(SortedMap<K, ? extends V> m,
                           Function<? super K, ? extends K> keyCloner,
                           Function<? super V, ? extends V> valueCloner) {
        this.comparator = m.comparator();
        try {
            buildFromSorted(
                    m.size(),
                    m.entrySet().iterator(),
                    null,
                    null,
                    keyCloner,
                    valueCloner
            );
        } catch (IOException | ClassNotFoundException cannotHappen) {}
    }




    public int size() {
        return size;
    }


    public boolean containsKey(Object key) {
        return (this.heldEntry = getEntry(key)) != null;
    }
    public boolean containsValue(Object value) {
        for (Entry<K, V> e = getFirstEntry(); e != null; e = successor(e))
            if (valEquals(value, e.value))
                return true;
        return false;
    }

    public V get(Object key) {
        return getOrDefault(key, null);
    }
    public V getOrDefault(Object key, V defVal) {
        return Optional.ofNullable(getEntry(key)).map(Map.Entry::getValue).orElse(defVal);
    }
    public V getAndHold(Object key) {
        return getAndHoldOrDefault(key, null);
    }
    public V getAndHoldOrDefault(Object key, V defVal) {
        return Optional.ofNullable(getAndHoldEntry(key)).map(Map.Entry::getValue).orElse(defVal);
    }
    public Entry<K, V> getHeld() {
        return this.heldEntry;
    }
    public K getHeldK() {
        return Optional.ofNullable(getHeld()).map(Map.Entry::getKey).orElse(null);
    }
    public V getHeldV() {
        return Optional.ofNullable(getHeld()).map(Map.Entry::getValue).orElse(null);
    }
    public void deleteHeld() {
        deleteEntry(this.heldEntry);
    }
    public boolean anyHeld() {
        return this.heldEntry != null;
    }
    public void clearHold() {
        this.heldEntry = null;
    }

    public Comparator<? super K> comparator() {
        return this.comparator;
    }


    public K firstKey() {
        return Optional.ofNullable(getFirstEntry()).map(EnhancedTreeMap::key).orElse(null);
    }
    public K lastKey() {
        return Optional.ofNullable(getLastEntry()).map(EnhancedTreeMap::key).orElse(null);
    }

    public void putAll(Map<? extends K, ? extends V> map) {
        int mapSize = map.size();
        this.firstEntry = null;
        this.lastEntry = null;
        if (size==0 && mapSize!=0 && map instanceof SortedMap) {
            if (Objects.equals(comparator, ((SortedMap<?,?>)map).comparator())) {
                ++modCount;
                try {
                    buildFromSorted(mapSize, map.entrySet().iterator(),null, null);
                } catch (IOException | ClassNotFoundException cannotHappen) {}
                return;
            }
        }
        super.putAll(map);
    }


    public final Entry<K, V> getEntry(Object key) {
        // Offload comparator-based version for sake of performance
        if (key == firstKey()) return firstEntry;
        if (key == lastKey()) return lastEntry;
        if (comparator != null) return getEntryUsingComparator(key);
        Comparable<? super K> k = (Comparable<? super K>) key;
        Entry<K, V> p = root;
        while (p != null) {
            int cmp = k.compareTo(p.key);
            if (cmp < 0) p = p.left;
            else if (cmp > 0) p = p.right;
            else return p;
        }
        return null;
    }
    final Entry<K, V> getAndHoldEntry(Object key) {
        return (this.heldEntry = getEntry(key));
    }

    final Entry<K, V> getEntryUsingComparator(Object key) {
        K k = (K) key;
        if (k == firstKey()) return firstEntry;
        if (k == lastKey()) return lastEntry;
        Comparator<? super K> cpr = comparator;
        if (cpr != null) {
            Entry<K, V> p = root;
            while (p != null) {
                int cmp = cpr.compare(k, p.key);
                if (cmp < 0) p = p.left;
                else if (cmp > 0) p = p.right;
                else return p;
            }
        }
        return null;
    }

    final Entry<K, V> getCeilingEntry(K key) {
        Entry<K, V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp < 0) {
                if (p.left != null) p = p.left;
                else return p;
            } else if (cmp > 0) {
                if (p.right != null) {
                    p = p.right;
                } else {
                    Entry<K, V> parent = p.parent;
                    Entry<K, V> ch = p;
                    while (parent != null && ch == parent.right) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            } else
                return p;
        }
        return null;
    }


    final Entry<K, V> getFloorEntry(K key) {
        Entry<K, V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp > 0) {
                if (p.right != null) p = p.right;
                else return p;
            } else if (cmp < 0) {
                if (p.left != null) {
                    p = p.left;
                } else {
                    Entry<K, V> parent = p.parent;
                    Entry<K, V> ch = p;
                    while (parent != null && ch == parent.left) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            } else
                return p;

        }
        return null;
    }




    final Entry<K, V> getHigherEntry(K key) {
        Entry<K, V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp < 0) {
                if (p.left != null) p = p.left;
                else return p;
            } else {
                if (p.right != null) {
                    p = p.right;
                } else {
                    Entry<K, V> parent = p.parent;
                    Entry<K, V> ch = p;
                    while (parent != null && ch == parent.right) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            }
        }
        return null;
    }


    final Entry<K, V> getLowerEntry(K key) {
        Entry<K, V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp > 0) {
                if (p.right != null) p = p.right;
                else return p;
            } else {
                if (p.left != null) {
                    p = p.left;
                } else {
                    Entry<K, V> parent = p.parent;
                    Entry<K, V> ch = p;
                    while (parent != null && ch == parent.left) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            }
        }
        return null;
    }

    private int mutSize(Entry<K, V> e) {
        // account for root node
        int size = 1;
        if (e.left != null) size += mutSize(e.left);
        if (e.right != null) size += mutSize(e.right);
        e.size = size;
        return size;
    }

    public V put(K key, V value) {
        Entry<K, V> t = this.root;
        if (t == null) {
            this.root = firstEntry = lastEntry = new Entry<>(key, value, null);
            size++;
            modCount++;
            return null;
        }

        Comparator<? super K> cpr = comparator == null ? (a, b) -> ((Comparable<? super K>) a).compareTo(b) : comparator;
        Entry<K, V> prevT;
        int cmp;
        // prevent variable not being initialized
        do {
            t.incApparentSize();
            prevT = t;
            cmp = cpr.compare(key, t.key);
            if (cmp < 0) {
                t = t.left;
            }
            else if (cmp > 0) {
                t = t.right;
            }
            else {
                t.incApparentSize();
                t.decSize();
                return t.setValue(value);
            }
        } while (t != null);
        Entry<K, V> e = new Entry<>(key, value, prevT);
        if (cmp < 0) prevT.left = e;
        else prevT.right = e;
        if (firstEntry == null || cpr.compare(e.key, firstEntry.key) < 0) firstEntry = e;
        else if (lastEntry == null || cpr.compare(e.key, lastEntry.key) > 0) lastEntry = e;

        fixAfterInsertion(e);
        size++;
        modCount++;
        return null;
    }

    public boolean putSuccess(K key, V value) {
        return put(key, value) != null || !containsKey(key);
    }

    public V remove(Object key) {
        Entry<K, V> p = getEntry(key);
        if (p == null) return null;

        V oldValue = p.value;
        deleteEntry(p);
        return oldValue;
    }
    public V removeOrDefault(Object key, V defVal) {
        return Optional.ofNullable(remove(key)).orElse(defVal);
    }
    public V removeAndHold(Object key) {
        return removeAndHoldOrDefault(key, null);
    }
    public V removeAndHoldOrDefault(Object key, V defVal) {
        this.heldEntry = getEntry(key);
        if (heldEntry == null) return defVal;
        V oldValue = heldEntry.value;
        deleteHeld();
        return oldValue;
    }

    // assumed to be continuous and sorted
    public List<V> remove(List<Object> keySequence) {
        Entry<K, V> currParent = getEntry(keySequence.get(keySequence.size() / 2));
        while (keySequence.contains(currParent.parent.getKey())) {
            currParent = currParent.parent;
        }
        currParent.left = null;
        currParent.right = null;
        deleteEntry(currParent);
        return new ArrayList<>();
    }

    public void clear() {
        modCount++;
        size = 0;
        root = null;
    }


    public Object clone() {
        EnhancedTreeMap<K, V> clone = ProgrammingEx.tryCat(() -> (EnhancedTreeMap<K, V>) super.clone(), InternalError::new);
        // Put clone into "virgin" state (except for comparator)
        clone.root = null;
        clone.size = 0;
        clone.modCount = 0;
        clone.entrySet = null;
        clone.navigableKeySet = null;
        clone.descendingMap = null;

        // Initialize clone with our mappings
        try {
            clone.buildFromSorted(size, entrySet().iterator(), null, null);
        } catch (IOException | ClassNotFoundException cannotHappen) {}
        return clone;
    }


    public Map.Entry<K, V> firstEntry() {
        return exportEntry(getFirstEntry());
    }
    public Map.Entry<K, V> lastEntry() {
        return exportEntry(getLastEntry());
    }
    public Map.Entry<K, V> pollFirstEntry() {
        Entry<K, V> p = getFirstEntry();
        Map.Entry<K, V> result = exportEntry(p);
        if (p != null) deleteEntry(p);
        return result;
    }
    public Map.Entry<K, V> pollLastEntry() {
        Entry<K, V> p = getLastEntry();
        Map.Entry<K, V> result = exportEntry(p);
        if (p != null) deleteEntry(p);
        return result;
    }
    public Map.Entry<K, V> lowerEntry(K key) {
        return exportEntry(getLowerEntry(key));
    }
    public K lowerKey(K key) {
        return keyOrNull(getLowerEntry(key));
    }
    public Map.Entry<K, V> floorEntry(K key) {
        return exportEntry(getFloorEntry(key));
    }
    public K floorKey(K key) {
        return keyOrNull(getFloorEntry(key));
    }
    public Map.Entry<K, V> ceilingEntry(K key) {
        return exportEntry(getCeilingEntry(key));
    }
    public K ceilingKey(K key) {
        return keyOrNull(getCeilingEntry(key));
    }
    public Map.Entry<K, V> higherEntry(K key) {
        return exportEntry(getHigherEntry(key));
    }
    public K higherKey(K key) {
        return keyOrNull(getHigherEntry(key));
    }

    public K getKey(int index) {
        if (index < 0 || index >= size()) throw new ArrayIndexOutOfBoundsException();
        if (index == 0) return firstKey();
        if (index == size() - 1) return lastKey();
        return getKey(root, index);
    }

    private K getKey(Entry<K, V> e, int index) {
        if (e.left == null && (index == 0 || e.right == null)) {
            return e.key;
        }
        if (e.left != null && e.left.size == index) {
            return e.key;
        }
        if (e.left != null && e.left.size > index) {
            return getKey(e.left, index);
        }
        return getKey(e.right, index - (e.left == null ? 0 : e.left.size) - 1);
    }

    public int indexOf(K key) {
        // Offload comparator-based version for sake of performance
        if (key == firstKey()) return 0;
        if (key == lastKey()) return size() - 1;
        Comparable<? super K> k = (Comparable<? super K>) key;
        Entry<K, V> p = root;
        int currSum = 0;
        while (p != null) {
            int cmp = k.compareTo(p.key);
            if (cmp < 0) {
                p = p.left;
            } else if (cmp > 0) {
                currSum += sizeOf(p.left) + 1;
                p = p.right;
            } else {
                return currSum + sizeOf(p.left);
            }
        }
        return -1;
    }


    public Entry<K, V> getIdx(int index) {
        if (index == 0) return firstEntry;
        if (index == size() - 1) return lastEntry;
        return getIdx(root, index);
    }
    public Entry<K, V> getIdxOrDefault(int index, Entry<K, V> defVal) {
        return Optional.ofNullable(getIdx(index)).orElse(defVal);
    }
    public Entry<K, V> getAndHoldIdx(int index) {
        return getAndHoldIdxOrDefault(index, null);
    }
    public Entry<K, V> getAndHoldIdxOrDefault(int index, Entry<K, V> defVal) {
        return Optional.ofNullable(this.heldEntry = getIdx(index)).orElse(defVal);
    }

    private Entry<K, V> getIdx(Entry<K, V> e, int index) {
        if (e.left == null && (index == 0 || e.right == null)) {
            return e;
        }
        if (e.left != null && e.left.size == index) {
            return e;
        }
        if (e.left != null && e.left.size > index) {
            return getIdx(e.left, index);
        }
        return getIdx(e.right, index - (e.left == null ? 0 : e.left.size) - 1);
    }

    public void forEachIdx(BiConsumer<Integer, V> fn) {
        ProgrammingEx.varExec(new AtomicInteger(0), i -> forEach((k, v) -> fn.accept(i.getAndIncrement(), v)));
    }

    private transient EntrySet entrySet = null;
    private transient KeySet<K> navigableKeySet = null;
    private transient NavigableMap<K, V> descendingMap = null;

    public Set<K> keySet() {
        return navigableKeySet();
    }
    public NavigableSet<K> navigableKeySet() {
        KeySet<K> nks = navigableKeySet;
        return (nks != null) ? nks : (navigableKeySet = new KeySet<>(this));
    }
    public NavigableSet<K> descendingKeySet() {
        return descendingMap().navigableKeySet();
    }

    public Collection<V> values() {
        return super.values();
    }

    public Set<Map.Entry<K, V>> entrySet() {
        EntrySet es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySet());
    }

    public NavigableMap<K, V> descendingMap() {
        NavigableMap<K, V> km = descendingMap;
        return (km != null) ? km :
                (descendingMap = new DescendingSubMap<>(this,
                        true, null, true,
                        true, null, true));
    }

    public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive,
                                     K toKey, boolean toInclusive) {
        return new AscendingSubMap<>(this,
                false, fromKey, fromInclusive,
                false, toKey, toInclusive);
    }

    public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
        return new AscendingSubMap<>(this,
                true, null, true,
                false, toKey, inclusive);
    }

    public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
        return new AscendingSubMap<>(this,
                false, fromKey, inclusive,
                true, null, true);
    }

    public SortedMap<K, V> subMap(K fromKey, K toKey) {
        return subMap(fromKey, true, toKey, false);
    }

    public SortedMap<K, V> headMap(K toKey) {
        return headMap(toKey, false);
    }

    public SortedMap<K, V> tailMap(K fromKey) {
        return tailMap(fromKey, true);
    }

    class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        public Iterator<Map.Entry<K, V>> iterator() {
            return new EntryIterator(getFirstEntry());
        }

        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> entry)) return false;
            Entry<K, V> p = getEntry(entry.getKey());
            return p != null && valEquals(p.getValue(), entry.getValue());
        }

        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry)) return false;
            Map.Entry<K, V> entry = (Map.Entry<K, V>) o;
            Entry<K, V> p = getEntry(entry.getKey());
            if (p != null && valEquals(p.getValue(), entry.getValue())) {
                deleteEntry(p);
                return true;
            }
            return false;
        }

        public int size() {
            return EnhancedTreeMap.this.size();
        }
        public void clear() {
            EnhancedTreeMap.this.clear();
        }
    }

    Iterator<K> keyIterator() {
        return new KeyIterator(getFirstEntry());
    }

    Iterator<K> descendingKeyIterator() {
        return new DescendingKeyIterator(getLastEntry());
    }

    static final class KeySet<E> extends AbstractSet<E> implements NavigableSet<E> {
        private final NavigableMap<E, ?> m;
        KeySet(NavigableMap<E,?> map) { m = map; }

        public Iterator<E> iterator() {
            if (m instanceof EnhancedTreeMap)
                return ((EnhancedTreeMap<E,?>)m).keyIterator();
            else
                return ((EnhancedTreeMap.NavigableSubMap<E,?>)m).keyIterator();
        }

        public Iterator<E> descendingIterator() {
            if (m instanceof EnhancedTreeMap)
                return ((EnhancedTreeMap<E,?>)m).descendingKeyIterator();
            else
                return ((EnhancedTreeMap.NavigableSubMap<E,?>)m).descendingKeyIterator();
        }

        public int size() { return m.size(); }
        public boolean isEmpty() { return m.isEmpty(); }
        public boolean contains(Object o) { return m.containsKey(o); }
        public void clear() { m.clear(); }
        public E lower(E e) { return m.lowerKey(e); }
        public E floor(E e) { return m.floorKey(e); }
        public E ceiling(E e) { return m.ceilingKey(e); }
        public E higher(E e) { return m.higherKey(e); }
        public E first() { return m.firstKey(); }
        public E last() { return m.lastKey(); }
        public Comparator<? super E> comparator() { return m.comparator(); }
        public E pollFirst() {
            Map.Entry<E,?> e = m.pollFirstEntry();
            return (e == null) ? null : e.getKey();
        }
        public E pollLast() {
            Map.Entry<E,?> e = m.pollLastEntry();
            return (e == null) ? null : e.getKey();
        }
        public boolean remove(Object o) {
            int oldSize = size();
            m.remove(o);
            return size() != oldSize;
        }
        public NavigableSet<E> subSet(E fromElement, boolean fromInclusive,
                                      E toElement,   boolean toInclusive) {
            return new KeySet<>(m.subMap(fromElement, fromInclusive,
                    toElement,   toInclusive));
        }
        public NavigableSet<E> headSet(E toElement, boolean inclusive) {
            return new KeySet<>(m.headMap(toElement, inclusive));
        }
        public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
            return new KeySet<>(m.tailMap(fromElement, inclusive));
        }
        public SortedSet<E> subSet(E fromElement, E toElement) {
            return subSet(fromElement, true, toElement, false);
        }
        public SortedSet<E> headSet(E toElement) {
            return headSet(toElement, false);
        }
        public SortedSet<E> tailSet(E fromElement) {
            return tailSet(fromElement, true);
        }
        public NavigableSet<E> descendingSet() {
            return new KeySet<>(m.descendingMap());
        }

        @Override
        public Spliterator<E> spliterator() {
            if (!(m instanceof EnhancedTreeMap<?, ?>)) return m.keySet().spliterator();
            EnhancedTreeMap<E, ?> map = (EnhancedTreeMap<E, ?>) m;
            return new KeySetSpliterator<>(map, leftmost(map.root), map.root != null ? map.root.size : 0);
        }

    }

    static final class KeySetSpliterator<E> implements Spliterator<E> {
        private Entry<E, ?> current;
        private final EnhancedTreeMap<E, ?> map;
        private long est;

        KeySetSpliterator(EnhancedTreeMap<E, ?> map, Entry<E, ?> start, long est) {
            this.map = map;
            this.current = start;
            this.est = est;
        }

        private Entry<E, ?> successor(Entry<E, ?> t) {
            if (t == null) return null;
            if (t.right != null) return map.leftmost(t.right);
            Entry<E, ?> p = t.parent;
            Entry<E, ?> ch = t;
            while (p != null && ch == p.right) {
                ch = p;
                p = p.parent;
            }
            return p;
        }

        private Entry<E, ?> predecessor(Entry<E, ?> t) {
            if (t == null) return null;
            if (t.left != null) return map.rightmost(t.left);
            Entry<E, ?> p = t.parent;
            Entry<E, ?> ch = t;
            while (p != null && ch == p.left) {
                ch = p;
                p = p.parent;
            }
            return p;
        }

        @Override
        public boolean tryAdvance(Consumer<? super E> action) {
            if (current == null) return false;
            action.accept(current.key);
            current = successor(current);
            est--;
            return true;
        }

        @Override
        public Spliterator<E> trySplit() {
            if (current == null || current.size <= 1) return null;

            // Try to split at roughly half of the current subtree
            Entry<E, ?> splitNode = current;
            long leftSize = splitNode.left != null ? splitNode.left.size : 0;

            if (leftSize == 0) return null;

            Entry<E, ?> leftSubtree = splitNode.left;
            splitNode.left = null; // detach left portion
            est -= leftSize;

            return new KeySetSpliterator<>(map, leftSubtree, leftSize);
        }

        @Override
        public long estimateSize() {
            return est;
        }

        @Override
        public int characteristics() {
            return Spliterator.DISTINCT | Spliterator.SORTED | Spliterator.ORDERED | Spliterator.SIZED | Spliterator.NONNULL;
        }

        @Override
        public Comparator<? super E> getComparator() {
            return map.comparator(); // null if natural order
        }
    }



    abstract class PrivateEntryIterator<T> implements Iterator<T> {
        Entry<K, V> next;
        Entry<K, V> lastReturned;
        int expectedModCount;

        PrivateEntryIterator(Entry<K, V> first) {
            expectedModCount = modCount;
            lastReturned = null;
            next = first;
        }

        public final boolean hasNext() {
            return next != null;
        }

        final Entry<K, V> nextEntry() {
            Entry<K, V> e = next;
            if (e == null)
                throw new NoSuchElementException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            next = successor(e);
            lastReturned = e;
            return e;
        }

        final Entry<K, V> prevEntry() {
            Entry<K, V> e = next;
            if (e == null)
                throw new NoSuchElementException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            next = predecessor(e);
            lastReturned = e;
            return e;
        }

        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            if (lastReturned.left != null && lastReturned.right != null)
                next = lastReturned;
            deleteEntry(lastReturned);
            expectedModCount = modCount;
            lastReturned = null;
        }
    }

    final class EntryIterator extends PrivateEntryIterator<Map.Entry<K,V>> {
        EntryIterator(Entry<K,V> first) {
            super(first);
        }
        public Map.Entry<K,V> next() {
            return nextEntry();
        }
    }

    final class KeyIterator extends PrivateEntryIterator<K> {
        KeyIterator(Entry<K,V> first) {
            super(first);
        }
        public K next() {
            return nextEntry().key;
        }
    }

    final class DescendingKeyIterator extends PrivateEntryIterator<K> {
        DescendingKeyIterator(Entry<K,V> first) {
            super(first);
        }
        public K next() {
            return prevEntry().key;
        }
        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            deleteEntry(lastReturned);
            lastReturned = null;
            expectedModCount = modCount;
        }
    }

    final int compare(Object k1, Object k2) {
        return comparator == null ? ((Comparable<? super K>) k1).compareTo((K) k2)
                : comparator.compare((K) k1, (K) k2);
    }

    static boolean valEquals(Object o1, Object o2) {
        return Objects.equals(o1, o2);
    }

    static <K, V> Map.Entry<K, V> exportEntry(EnhancedTreeMap.Entry<K, V> e) {
        return e == null ? null :
                new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue());
    }

    static <K, V> K keyOrNull(EnhancedTreeMap.Entry<K, V> e) {
        return e == null ? null : e.key;
    }

    static <K> K key(Entry<K, ?> e) {
        if (e == null) throw new NoSuchElementException();
        return e.key;
    }


    abstract static class NavigableSubMap<K,V> extends AbstractMap<K,V>
            implements NavigableMap<K,V>, Serializable {
        @Serial
        private static final long serialVersionUID = -2102997345730753016L;
        final EnhancedTreeMap<K,V> m;

        // Conditionally serializable
        final K lo;
        // Conditionally serializable
        final K hi;
        final boolean fromStart, toEnd;
        final boolean loInclusive, hiInclusive;

        NavigableSubMap(EnhancedTreeMap<K,V> m,
                        boolean fromStart, K lo, boolean loInclusive,
                        boolean toEnd,     K hi, boolean hiInclusive) {
            if (!fromStart && !toEnd) {
                if (m.compare(lo, hi) > 0)
                    throw new IllegalArgumentException("fromKey > toKey");
            } else {
                if (!fromStart) // type check
                    m.compare(lo, lo);
                if (!toEnd)
                    m.compare(hi, hi);
            }

            this.m = m;
            this.fromStart = fromStart;
            this.lo = lo;
            this.loInclusive = loInclusive;
            this.toEnd = toEnd;
            this.hi = hi;
            this.hiInclusive = hiInclusive;
        }

        // internal utilities

        final boolean tooLow(Object key) {
            if (!fromStart) {
                int c = m.compare(key, lo);
                return c < 0 || (c == 0 && !loInclusive);
            }
            return false;
        }

        final boolean tooHigh(Object key) {
            if (!toEnd) {
                int c = m.compare(key, hi);
                return c > 0 || (c == 0 && !hiInclusive);
            }
            return false;
        }

        final boolean inRange(Object key) {
            return !tooLow(key) && !tooHigh(key);
        }

        final boolean inClosedRange(Object key) {
            return (fromStart || m.compare(key, lo) >= 0)
                    && (toEnd || m.compare(hi, key) >= 0);
        }

        final boolean inRange(Object key, boolean inclusive) {
            return inclusive ? inRange(key) : inClosedRange(key);
        }

        /*
         * Absolute versions of relation operations.
         * Subclasses map to these using like-named "sub"
         * versions that invert senses for descending maps
         */

        final EnhancedTreeMap.Entry<K,V> absLowest() {
            EnhancedTreeMap.Entry<K,V> e =
                    (fromStart ?  m.getFirstEntry() :
                            (loInclusive ? m.getCeilingEntry(lo) :
                                    m.getHigherEntry(lo)));
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final EnhancedTreeMap.Entry<K,V> absHighest() {
            EnhancedTreeMap.Entry<K,V> e =
                    (toEnd ?  m.getLastEntry() :
                            (hiInclusive ?  m.getFloorEntry(hi) :
                                    m.getLowerEntry(hi)));
            return (e == null || tooLow(e.key)) ? null : e;
        }

        final EnhancedTreeMap.Entry<K,V> absCeiling(K key) {
            if (tooLow(key))
                return absLowest();
            EnhancedTreeMap.Entry<K,V> e = m.getCeilingEntry(key);
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final EnhancedTreeMap.Entry<K,V> absHigher(K key) {
            if (tooLow(key))
                return absLowest();
            EnhancedTreeMap.Entry<K,V> e = m.getHigherEntry(key);
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final EnhancedTreeMap.Entry<K,V> absFloor(K key) {
            if (tooHigh(key))
                return absHighest();
            EnhancedTreeMap.Entry<K,V> e = m.getFloorEntry(key);
            return (e == null || tooLow(e.key)) ? null : e;
        }

        final EnhancedTreeMap.Entry<K,V> absLower(K key) {
            if (tooHigh(key))
                return absHighest();
            EnhancedTreeMap.Entry<K,V> e = m.getLowerEntry(key);
            return (e == null || tooLow(e.key)) ? null : e;
        }

        /** Returns the absolute high fence for ascending traversal */
        final EnhancedTreeMap.Entry<K,V> absHighFence() {
            return (toEnd ? null : (hiInclusive ?
                    m.getHigherEntry(hi) :
                    m.getCeilingEntry(hi)));
        }

        /** Return the absolute low fence for descending traversal  */
        final EnhancedTreeMap.Entry<K,V> absLowFence() {
            return (fromStart ? null : (loInclusive ?
                    m.getLowerEntry(lo) :
                    m.getFloorEntry(lo)));
        }

        // Abstract methods defined in ascending vs descending classes
        // These relay to the appropriate absolute versions

        abstract EnhancedTreeMap.Entry<K,V> subLowest();
        abstract EnhancedTreeMap.Entry<K,V> subHighest();
        abstract EnhancedTreeMap.Entry<K,V> subCeiling(K key);
        abstract EnhancedTreeMap.Entry<K,V> subHigher(K key);
        abstract EnhancedTreeMap.Entry<K,V> subFloor(K key);
        abstract EnhancedTreeMap.Entry<K,V> subLower(K key);

        /** Returns ascending iterator from the perspective of this submap */
        abstract Iterator<K> keyIterator();

        abstract Spliterator<K> keySpliterator();

        /** Returns descending iterator from the perspective of this submap */
        abstract Iterator<K> descendingKeyIterator();

        public boolean isEmpty() {
            return (fromStart && toEnd) ? m.isEmpty() : entrySet().isEmpty();
        }
        public int size() {
            return (fromStart && toEnd) ? m.size() : entrySet().size();
        }
        public final boolean containsKey(Object key) {
            return inRange(key) && m.containsKey(key);
        }
        public final V put(K key, V value) {
            if (!inRange(key))
                throw new IllegalArgumentException("key out of range");
            return m.put(key, value);
        }

        public final V get(Object key) {
            return !inRange(key) ? null :  m.get(key);
        }

        public final V remove(Object key) {
            return !inRange(key) ? null : m.remove(key);
        }

        public final Map.Entry<K,V> ceilingEntry(K key) {
            return exportEntry(subCeiling(key));
        }

        public final K ceilingKey(K key) {
            return keyOrNull(subCeiling(key));
        }

        public final Map.Entry<K,V> higherEntry(K key) {
            return exportEntry(subHigher(key));
        }

        public final K higherKey(K key) {
            return keyOrNull(subHigher(key));
        }

        public final Map.Entry<K,V> floorEntry(K key) {
            return exportEntry(subFloor(key));
        }

        public final K floorKey(K key) {
            return keyOrNull(subFloor(key));
        }

        public final Map.Entry<K,V> lowerEntry(K key) {
            return exportEntry(subLower(key));
        }

        public final K lowerKey(K key) {
            return keyOrNull(subLower(key));
        }

        public final K firstKey() {
            return key(subLowest());
        }

        public final K lastKey() {
            return key(subHighest());
        }

        public final Map.Entry<K,V> firstEntry() {
            return exportEntry(subLowest());
        }

        public final Map.Entry<K,V> lastEntry() {
            return exportEntry(subHighest());
        }

        public final Map.Entry<K,V> pollFirstEntry() {
            EnhancedTreeMap.Entry<K,V> e = subLowest();
            Map.Entry<K,V> result = exportEntry(e);
            if (e != null)
                m.deleteEntry(e);
            return result;
        }

        public final Map.Entry<K,V> pollLastEntry() {
            EnhancedTreeMap.Entry<K,V> e = subHighest();
            Map.Entry<K,V> result = exportEntry(e);
            if (e != null)
                m.deleteEntry(e);
            return result;
        }

        transient NavigableMap<K,V> descendingMapView;
        transient EntrySetView entrySetView;
        transient KeySet<K> navigableKeySetView;

        public final NavigableSet<K> navigableKeySet() {
            KeySet<K> nksv = navigableKeySetView;
            return (nksv != null) ? nksv :
                    (navigableKeySetView = new EnhancedTreeMap.KeySet<>(this));
        }

        public final Set<K> keySet() {
            return navigableKeySet();
        }

        public NavigableSet<K> descendingKeySet() {
            return descendingMap().navigableKeySet();
        }

        public final SortedMap<K,V> subMap(K fromKey, K toKey) {
            return subMap(fromKey, true, toKey, false);
        }

        public final SortedMap<K,V> headMap(K toKey) {
            return headMap(toKey, false);
        }

        public final SortedMap<K,V> tailMap(K fromKey) {
            return tailMap(fromKey, true);
        }


        abstract class EntrySetView extends AbstractSet<Map.Entry<K,V>> {
            private transient int size = -1, sizeModCount;

            public int size() {
                if (fromStart && toEnd)
                    return m.size();
                if (size == -1 || sizeModCount != m.modCount) {
                    sizeModCount = m.modCount;
                    size = 0;
                    for (Entry<K, V> ignored : this) size++;
                }
                return size;
            }

            public boolean isEmpty() {
                EnhancedTreeMap.Entry<K,V> n = absLowest();
                return n == null || tooHigh(n.key);
            }

            public boolean contains(Object o) {
                if (!(o instanceof Map.Entry<?, ?> entry)) return false;
                Object key = entry.getKey();
                if (!inRange(key)) return false;
                EnhancedTreeMap.Entry<?,?> node = m.getEntry(key);
                return node != null && valEquals(node.getValue(), entry.getValue());
            }

            public boolean remove(Object o) {
                if (!(o instanceof Map.Entry<?, ?> entry)) return false;
                Object key = entry.getKey();
                if (!inRange(key))
                    return false;
                EnhancedTreeMap.Entry<K,V> node = m.getEntry(key);
                if (node!=null && valEquals(node.getValue(),
                        entry.getValue())) {
                    m.deleteEntry(node);
                    return true;
                }
                return false;
            }
        }

        /**
         * Iterators for SubMaps
         */
        abstract class SubMapIterator<T> implements Iterator<T> {
            EnhancedTreeMap.Entry<K,V> lastReturned;
            EnhancedTreeMap.Entry<K,V> next;
            final Object fenceKey;
            int expectedModCount;

            SubMapIterator(EnhancedTreeMap.Entry<K,V> first,
                           EnhancedTreeMap.Entry<K,V> fence) {
                expectedModCount = m.modCount;
                lastReturned = null;
                next = first;
                fenceKey = fence == null ? UNBOUNDED : fence.key;
            }

            public final boolean hasNext() {
                return next != null && next.key != fenceKey;
            }

            final EnhancedTreeMap.Entry<K,V> nextEntry() {
                EnhancedTreeMap.Entry<K,V> e = next;
                if (e == null || e.key == fenceKey)
                    throw new NoSuchElementException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                next = successor(e);
                lastReturned = e;
                return e;
            }

            final EnhancedTreeMap.Entry<K,V> prevEntry() {
                EnhancedTreeMap.Entry<K,V> e = next;
                if (e == null || e.key == fenceKey)
                    throw new NoSuchElementException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                next = predecessor(e);
                lastReturned = e;
                return e;
            }

            final void removeAscending() {
                if (lastReturned == null)
                    throw new IllegalStateException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                if (lastReturned.left != null && lastReturned.right != null)
                    next = lastReturned;
                m.deleteEntry(lastReturned);
                lastReturned = null;
                expectedModCount = m.modCount;
            }

            final void removeDescending() {
                if (lastReturned == null)
                    throw new IllegalStateException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                m.deleteEntry(lastReturned);
                lastReturned = null;
                expectedModCount = m.modCount;
            }

        }

        final class SubMapEntryIterator extends SubMapIterator<Map.Entry<K,V>> {
            SubMapEntryIterator(EnhancedTreeMap.Entry<K,V> first,
                                EnhancedTreeMap.Entry<K,V> fence) {
                super(first, fence);
            }
            public Map.Entry<K,V> next() {
                return nextEntry();
            }
            public void remove() {
                removeAscending();
            }
        }

        final class DescendingSubMapEntryIterator extends SubMapIterator<Map.Entry<K,V>> {
            DescendingSubMapEntryIterator(EnhancedTreeMap.Entry<K,V> last,
                                          EnhancedTreeMap.Entry<K,V> fence) {
                super(last, fence);
            }

            public Map.Entry<K,V> next() {
                return prevEntry();
            }
            public void remove() {
                removeDescending();
            }
        }

        final class SubMapKeyIterator extends SubMapIterator<K>
                implements Spliterator<K> {
            SubMapKeyIterator(EnhancedTreeMap.Entry<K,V> first,
                              EnhancedTreeMap.Entry<K,V> fence) {
                super(first, fence);
            }
            public K next() {
                return nextEntry().key;
            }
            public void remove() {
                removeAscending();
            }
            public Spliterator<K> trySplit() {
                return null;
            }
            public void forEachRemaining(Consumer<? super K> action) {
                while (hasNext())
                    action.accept(next());
            }
            public boolean tryAdvance(Consumer<? super K> action) {
                if (hasNext()) {
                    action.accept(next());
                    return true;
                }
                return false;
            }
            public long estimateSize() {
                return Long.MAX_VALUE;
            }
            public int characteristics() {
                return Spliterator.DISTINCT | Spliterator.ORDERED |
                        Spliterator.SORTED;
            }
            public Comparator<? super K>  getComparator() {
                return NavigableSubMap.this.comparator();
            }
        }

        final class DescendingSubMapKeyIterator extends SubMapIterator<K>
                implements Spliterator<K> {
            DescendingSubMapKeyIterator(EnhancedTreeMap.Entry<K,V> last,
                                        EnhancedTreeMap.Entry<K,V> fence) {
                super(last, fence);
            }
            public K next() {
                return prevEntry().key;
            }
            public void remove() {
                removeDescending();
            }
            public Spliterator<K> trySplit() {
                return null;
            }
            public void forEachRemaining(Consumer<? super K> action) {
                while (hasNext())
                    action.accept(next());
            }
            public boolean tryAdvance(Consumer<? super K> action) {
                if (hasNext()) {
                    action.accept(next());
                    return true;
                }
                return false;
            }
            public long estimateSize() {
                return Long.MAX_VALUE;
            }
            public int characteristics() {
                return Spliterator.DISTINCT | Spliterator.ORDERED;
            }
        }
    }


    static final class AscendingSubMap<K,V> extends NavigableSubMap<K,V> {
        @Serial
        private static final long serialVersionUID = 912986545866124060L;

        AscendingSubMap(EnhancedTreeMap<K,V> m,
                        boolean fromStart, K lo, boolean loInclusive,
                        boolean toEnd,     K hi, boolean hiInclusive) {
            super(m, fromStart, lo, loInclusive, toEnd, hi, hiInclusive);
        }

        public Comparator<? super K> comparator() {
            return m.comparator();
        }

        public NavigableMap<K,V> subMap(K fromKey, boolean fromInclusive,
                                        K toKey,   boolean toInclusive) {
            if (!inRange(fromKey, fromInclusive))
                throw new IllegalArgumentException("fromKey out of range");
            if (!inRange(toKey, toInclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new AscendingSubMap<>(m,
                    false, fromKey, fromInclusive,
                    false, toKey,   toInclusive);
        }

        public NavigableMap<K,V> headMap(K toKey, boolean inclusive) {
            if (!inRange(toKey, inclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new AscendingSubMap<>(m,
                    fromStart, lo,    loInclusive,
                    false,     toKey, inclusive);
        }

        public NavigableMap<K,V> tailMap(K fromKey, boolean inclusive) {
            if (!inRange(fromKey, inclusive))
                throw new IllegalArgumentException("fromKey out of range");
            return new AscendingSubMap<>(m,
                    false, fromKey, inclusive,
                    toEnd, hi,      hiInclusive);
        }

        public NavigableMap<K,V> descendingMap() {
            NavigableMap<K,V> mv = descendingMapView;
            return (mv != null) ? mv :
                    (descendingMapView =
                            new DescendingSubMap<>(m,
                                    fromStart, lo, loInclusive,
                                    toEnd,     hi, hiInclusive));
        }

        Iterator<K> keyIterator() {
            return new SubMapKeyIterator(absLowest(), absHighFence());
        }

        Spliterator<K> keySpliterator() {
            return new SubMapKeyIterator(absLowest(), absHighFence());
        }

        Iterator<K> descendingKeyIterator() {
            return new DescendingSubMapKeyIterator(absHighest(), absLowFence());
        }

        final class AscendingEntrySetView extends EntrySetView {
            public Iterator<Map.Entry<K,V>> iterator() {
                return new SubMapEntryIterator(absLowest(), absHighFence());
            }
        }

        public Set<Map.Entry<K,V>> entrySet() {
            EntrySetView es = entrySetView;
            return (es != null) ? es : (entrySetView = new AscendingEntrySetView());
        }

        EnhancedTreeMap.Entry<K,V> subLowest()       { return absLowest(); }
        EnhancedTreeMap.Entry<K,V> subHighest()      { return absHighest(); }
        EnhancedTreeMap.Entry<K,V> subCeiling(K key) { return absCeiling(key); }
        EnhancedTreeMap.Entry<K,V> subHigher(K key)  { return absHigher(key); }
        EnhancedTreeMap.Entry<K,V> subFloor(K key)   { return absFloor(key); }
        EnhancedTreeMap.Entry<K,V> subLower(K key)   { return absLower(key); }
    }


    static final class DescendingSubMap<K,V>  extends NavigableSubMap<K,V> {
        @Serial
        private static final long serialVersionUID = 912986545866120460L;
        DescendingSubMap(EnhancedTreeMap<K,V> m,
                         boolean fromStart, K lo, boolean loInclusive,
                         boolean toEnd,     K hi, boolean hiInclusive) {
            super(m, fromStart, lo, loInclusive, toEnd, hi, hiInclusive);
        }

        private final Comparator<? super K> reverseComparator =
                Collections.reverseOrder(m.comparator);

        public Comparator<? super K> comparator() {
            return reverseComparator;
        }

        public NavigableMap<K,V> subMap(K fromKey, boolean fromInclusive,
                                        K toKey,   boolean toInclusive) {
            if (!inRange(fromKey, fromInclusive))
                throw new IllegalArgumentException("fromKey out of range");
            if (!inRange(toKey, toInclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new DescendingSubMap<>(m,
                    false, toKey,   toInclusive,
                    false, fromKey, fromInclusive);
        }

        public NavigableMap<K,V> headMap(K toKey, boolean inclusive) {
            if (!inRange(toKey, inclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new DescendingSubMap<>(m,
                    false, toKey, inclusive,
                    toEnd, hi,    hiInclusive);
        }

        public NavigableMap<K,V> tailMap(K fromKey, boolean inclusive) {
            if (!inRange(fromKey, inclusive))
                throw new IllegalArgumentException("fromKey out of range");
            return new DescendingSubMap<>(m,
                    fromStart, lo, loInclusive,
                    false, fromKey, inclusive);
        }

        public NavigableMap<K,V> descendingMap() {
            NavigableMap<K,V> mv = descendingMapView;
            return (mv != null) ? mv :
                    (descendingMapView =
                            new AscendingSubMap<>(m,
                                    fromStart, lo, loInclusive,
                                    toEnd,     hi, hiInclusive));
        }

        Iterator<K> keyIterator() {
            return new DescendingSubMapKeyIterator(absHighest(), absLowFence());
        }

        Spliterator<K> keySpliterator() {
            return new DescendingSubMapKeyIterator(absHighest(), absLowFence());
        }

        Iterator<K> descendingKeyIterator() {
            return new SubMapKeyIterator(absLowest(), absHighFence());
        }

        final class DescendingEntrySetView extends EntrySetView {
            public Iterator<Map.Entry<K,V>> iterator() {
                return new DescendingSubMapEntryIterator(absHighest(), absLowFence());
            }
        }

        public Set<Map.Entry<K,V>> entrySet() {
            EntrySetView es = entrySetView;
            return (es != null) ? es : (entrySetView = new DescendingEntrySetView());
        }

        EnhancedTreeMap.Entry<K,V> subLowest()       { return absHighest(); }
        EnhancedTreeMap.Entry<K,V> subHighest()      { return absLowest(); }
        EnhancedTreeMap.Entry<K,V> subCeiling(K key) { return absFloor(key); }
        EnhancedTreeMap.Entry<K,V> subHigher(K key)  { return absLower(key); }
        EnhancedTreeMap.Entry<K,V> subFloor(K key)   { return absCeiling(key); }
        EnhancedTreeMap.Entry<K,V> subLower(K key)   { return absHigher(key); }
    }




    private static final boolean RED = false;
    private static final boolean BLACK = true;


    public static final class Entry<K, V> implements Map.Entry<K, V> {
        K key;
        V value;
        Entry<K, V> left = null;
        Entry<K, V> right = null;
        Entry<K, V> parent;
        int size;
        boolean color = BLACK;

        void mutSize(int change) {
            mutApparentSize(change);
            Entry<K, V> p = this;
            while ((p = p.parent) != null) p.size += change;
        }
        void mutApparentSize(int change) {
            this.size += change;
        }
        void incApparentSize() {
            mutApparentSize(1);
        }
        void incSize() {
            mutSize(1);
        }
        void decApparentSize() {
            mutApparentSize(-1);
        }
        void decSize() {
            mutSize(-1);
        }

        Entry(K key, V value, Entry<K, V> parent) {
            this.key = key;
            this.value = value;
            this.parent = parent;
            this.size = 1;
        }

        public K getKey() {
            return key;
        }
        public V getValue() {
            return value;
        }
        public V setValue(V value) {
            V oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e)) return false;
            return valEquals(key, e.getKey()) && valEquals(value, e.getValue());
        }
        public int hashCode() {
            return (key == null ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
        }
        public String toString() {
            return key + "=" + value;
        }
    }

    final Entry<K, V> getFirstEntry() {
        if (firstEntry != null) return firstEntry;
        Entry<K, V> p = root;
        if (p != null) while (p.left != null) p = p.left;
        return (firstEntry = p);
    }

    final Entry<K, V> getLastEntry() {
        if (lastEntry != null) return lastEntry;
        Entry<K, V> p = root;
        if (p != null) while (p.right != null) p = p.right;
        return (lastEntry = p);
    }

    public static <K, V> EnhancedTreeMap.Entry<K, V> successor(Entry<K, V> t) {
        if (t == null) return null;
        else if (t.right != null) {
            Entry<K, V> p = t.right;
            while (p.left != null) p = p.left;
            return p;
        } else {
            Entry<K, V> p = t.parent, ch = t;
            while (p != null && ch == p.right) {
                ch = p;
                p = p.parent;
            }
            return p;
        }
    }

    public static <K, V> Entry<K, V> predecessor(Entry<K, V> t) {
        if (t == null) return null;
        else if (t.left != null) {
            Entry<K, V> p = t.left;
            while (p.right != null) p = p.right;
            return p;
        } else {
            Entry<K, V> p = t.parent, ch = t;
            while (p != null && ch == p.left) {
                ch = p;
                p = p.parent;
            }
            return p;
        }
    }

    public static <K, V> Entry<K, V> leftmost(Entry<K,V> node) {
        if (node == null) return null;
        while (node.left != null) node = node.left;
        return node;
    }

    public static <K, V> Entry<K,V> rightmost(Entry<K,V> node) {
        if (node == null) return null;
        while (node.right != null) node = node.right;
        return node;
    }


    private static <K, V> boolean colorOf(Entry<K, V> p) {
        return (p == null ? BLACK : p.color);
    }
    private static <K, V> Entry<K, V> parentOf(Entry<K, V> p) {
        return (p == null ? null : p.parent);
    }

    private static <K, V> void setColor(Entry<K, V> p, boolean c) {
        if (p != null) p.color = c;
    }

    private static <K, V> Entry<K, V> leftOf(Entry<K, V> p) {
        return p == null ? null : p.left;
    }
    private static <K, V> Entry<K, V> rightOf(Entry<K, V> p) {
        return p == null ? null : p.right;
    }
    private static <K, V> int sizeOf(Entry<K, V> p) {
        return p == null ? 0 : p.size;
    }

    private void rotateLeft(Entry<K, V> p) {
        if (p != null) {
            Entry<K, V> r = p.right;

            int diff = sizeOf(r.left) - sizeOf(p.right);
            p.right = r.left;
            p.mutSize(diff);

            if (r.left != null) r.left.parent = p;
            r.parent = p.parent;


            if (p.parent == null) {
                root = r;
            } else if (p.parent.left == p) {
                diff = sizeOf(r) - sizeOf(p.parent.left);
                p.parent.left = r;
                p.parent.mutSize(diff);
            } else {
                diff = sizeOf(r) - sizeOf(p.parent.right);
                p.parent.right = r;
                p.parent.mutSize(diff);
            }

            diff = sizeOf(p) - sizeOf(r.left);
            r.left = p;
            r.mutSize(diff);

            p.parent = r;
        }
    }

    private void rotateRight(Entry<K, V> p) {
        if (p != null) {
            Entry<K, V> l = p.left;

            int diff = sizeOf(l.right) - sizeOf(p.left);
            p.left = l.right;
            p.mutSize(diff);


            if (l.right != null) {
                l.right.parent = p;
            }

            l.parent = p.parent;

            if (p.parent == null) {
                root = l;
            } else if (p.parent.right == p) {
                diff = sizeOf(l) - sizeOf(p.parent.right);
                p.parent.right = l;
                p.parent.mutSize(diff);
            } else {
                diff = sizeOf(l) - sizeOf(p.parent.left);
                p.parent.left = l;
                p.parent.mutSize(diff);
            }

            diff = sizeOf(p) - sizeOf(l.right);
            l.right = p;
            l.mutSize(diff);

            p.parent = l;
        }
    }

    private void fixAfterInsertion(Entry<K, V> x) {
        x.color = RED;

        while (x != null && x != root && x.parent.color == RED) {
            if (parentOf(x) == leftOf(parentOf(parentOf(x)))) {
                Entry<K, V> y = rightOf(parentOf(parentOf(x)));
                if (colorOf(y) == RED) {
                    setColor(parentOf(x), BLACK);
                    setColor(y, BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    x = parentOf(parentOf(x));
                } else {
                    if (x == rightOf(parentOf(x))) {
                        x = parentOf(x);
                        rotateLeft(x);
                    }
                    setColor(parentOf(x), BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    rotateRight(parentOf(parentOf(x)));
                }
            } else {
                Entry<K, V> y = leftOf(parentOf(parentOf(x)));
                if (colorOf(y) == RED) {
                    setColor(parentOf(x), BLACK);
                    setColor(y, BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    x = parentOf(parentOf(x));
                } else {
                    if (x == leftOf(parentOf(x))) {
                        x = parentOf(x);
                        rotateRight(x);
                    }
                    setColor(parentOf(x), BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    rotateLeft(parentOf(parentOf(x)));
                }
            }
        }
        root.color = BLACK;
    }
    public void deleteEntry(Entry<K, V> p) {
        modCount++;
        size--;

        if (p.left != null && p.right != null) {
            Entry<K, V> s = successor(p);
            p.key = s.key;
            p.value = s.value;
            p = s;
        }
        if (p.getKey().equals(getHeldK())) heldEntry = null;
        if (p.getKey().equals(firstKey())) firstEntry = null;
        if (p.getKey().equals(lastKey())) lastEntry = null;

        Entry<K, V> replacement = (p.left != null ? p.left : p.right);

        if (replacement != null) {
            replacement.parent = p.parent;
            if (p.parent == null) {
                root = replacement;
            } else if (p == p.parent.left) {
                int diff = sizeOf(replacement) - sizeOf(p.parent.left);
                p.parent.left = replacement;
                p.parent.mutSize(diff);
            } else {
                int diff = sizeOf(replacement) - sizeOf(p.parent.right);
                p.parent.right = replacement;
                p.parent.mutSize(diff);
            }

            // Null out links so they are OK to use by fixAfterDeletion.
            p.left = p.right = p.parent = null;

            // Fix replacement
            if (p.color == BLACK)
                fixAfterDeletion(replacement);
        } else if (p.parent == null) { // return if we are the only node.
            root = null;
        } else { //  No children. Use self as phantom replacement and unlink.
            if (p.color == BLACK)
                fixAfterDeletion(p);
            if (p.parent != null) {
                if (p == p.parent.left) {
                    p.parent.left = null;
                } else if (p == p.parent.right) {
                    p.parent.right = null;
                }
                p.parent.decSize();
                p.parent = null;
            }
        }
    }

    private void fixAfterDeletion(Entry<K, V> x) {
        while (x != root && colorOf(x) == BLACK) {
            if (x == leftOf(parentOf(x))) {
                Entry<K, V> sib = rightOf(parentOf(x));

                if (colorOf(sib) == RED) {
                    setColor(sib, BLACK);
                    setColor(parentOf(x), RED);
                    rotateLeft(parentOf(x));
                    sib = rightOf(parentOf(x));
                }

                if (colorOf(leftOf(sib)) == BLACK &&
                        colorOf(rightOf(sib)) == BLACK) {
                    setColor(sib, RED);
                    x = parentOf(x);
                } else {
                    if (colorOf(rightOf(sib)) == BLACK) {
                        setColor(leftOf(sib), BLACK);
                        setColor(sib, RED);
                        rotateRight(sib);
                        sib = rightOf(parentOf(x));
                    }
                    setColor(sib, colorOf(parentOf(x)));
                    setColor(parentOf(x), BLACK);
                    setColor(rightOf(sib), BLACK);
                    rotateLeft(parentOf(x));
                    x = root;
                }
            } else { // symmetric
                Entry<K, V> sib = leftOf(parentOf(x));

                if (colorOf(sib) == RED) {
                    setColor(sib, BLACK);
                    setColor(parentOf(x), RED);
                    rotateRight(parentOf(x));
                    sib = leftOf(parentOf(x));
                }

                if (colorOf(rightOf(sib)) == BLACK &&
                        colorOf(leftOf(sib)) == BLACK) {
                    setColor(sib, RED);
                    x = parentOf(x);
                } else {
                    if (colorOf(leftOf(sib)) == BLACK) {
                        setColor(rightOf(sib), BLACK);
                        setColor(sib, RED);
                        rotateLeft(sib);
                        sib = leftOf(parentOf(x));
                    }
                    setColor(sib, colorOf(parentOf(x)));
                    setColor(parentOf(x), BLACK);
                    setColor(leftOf(sib), BLACK);
                    rotateRight(parentOf(x));
                    x = root;
                }
            }
        }

        setColor(x, BLACK);
    }


    @Serial
    private void writeObject(ObjectOutputStream s)
            throws IOException {
        s.defaultWriteObject();
        s.writeInt(size);
        for (Map.Entry<K, V> e : entrySet()) {
            s.writeObject(e.getKey());
            s.writeObject(e.getValue());
        }
    }

    @Serial
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        int size = s.readInt();
        buildFromSorted(size, null, s, null);
    }


    void readTreeSet(int size, ObjectInputStream s, V defaultVal)
            throws IOException, ClassNotFoundException {
        buildFromSorted(size, null, s, defaultVal);
        if (root != null) {
            mutSize(root);
        }
    }

    void addAllForTreeSet(SortedSet<? extends K> set, V defaultVal) {
        try {
            buildFromSorted(set.size(), set.iterator(), null, defaultVal);
        } catch (IOException | ClassNotFoundException cannotHappen) {}
    }


    private void buildFromSorted(int size, Iterator<?> it,
                                 ObjectInputStream str,
                                 V defaultVal)
            throws IOException, ClassNotFoundException {
        this.size = size;
        root = buildFromSorted(0, 0, size - 1, computeRedLevel(size),
                it, str, defaultVal);
    }

    private Entry<K, V> buildFromSorted(int level, int lo, int hi,
                                              int redLevel,
                                              Iterator<?> it,
                                              ObjectInputStream str,
                                              V defaultVal)
            throws IOException, ClassNotFoundException {

        if (hi < lo) return null;

        int mid = (lo + hi) / 2;

        Entry<K, V> left = null;
        if (lo < mid)
            left = buildFromSorted(level + 1, lo, mid - 1, redLevel,
                    it, str, defaultVal);

        K key;
        V value;
        if (it != null) {
            if (defaultVal == null) {
                Map.Entry<K, V> entry = (Map.Entry<K, V>) it.next();
                key = entry.getKey();
                value = entry.getValue();
            } else {
                key = (K) it.next();
                value = defaultVal;
            }
        } else {
            key = (K) str.readObject();
            value = (defaultVal != null ? defaultVal : (V) str.readObject());
        }

        Entry<K, V> middle = new Entry<>(key, value, null);

        if (level == redLevel)
            middle.color = RED;

        if (left != null) {
            middle.left = left;
            left.parent = middle;
        }

        if (mid < hi) {
            Entry<K, V> right = buildFromSorted(level + 1, mid + 1, hi, redLevel,
                    it, str, defaultVal);
            middle.right = right;
            right.parent = middle;
        }

        return middle;
    }

    @SuppressWarnings("unchecked")
    private Entry<K, V> buildFromSorted(int level,
                                        int lo,
                                        int hi,
                                        int redLevel,
                                        Iterator<? extends Map.Entry<? extends K, ? extends V>> it,
                                        ObjectInputStream str,
                                        V defaultVal,
                                        Function<? super K, ? extends K> keyCloner,
                                        Function<? super V, ? extends V> valueCloner)
            throws IOException, ClassNotFoundException {

        if (hi < lo) {
            return null;
        }

        int mid = (lo + hi) >>> 1;

        Entry<K, V> left = null;
        if (lo < mid) {
            left = buildFromSorted(
                    level + 1,
                    lo,
                    mid - 1,
                    redLevel,
                    it,
                    str,
                    defaultVal,
                    keyCloner,
                    valueCloner
            );
        }

        K key;
        V value;

        if (it != null) {
            Map.Entry<? extends K, ? extends V> entry = it.next();

            key = (K) entry.getKey();
            value = (defaultVal != null ? defaultVal : (V) entry.getValue());
        } else {
            key = (K) str.readObject();
            V readVal = (defaultVal != null ? defaultVal : (V) str.readObject());
            value = readVal;
        }

        // Clone hooks. If no cloner is provided we leave the reference alone.
        if (keyCloner != null) {
            key = keyCloner.apply(key);
        }
        if (valueCloner != null) {
            value = valueCloner.apply(value);
        }

        Entry<K, V> middle = new Entry<K, V>(key, value, null);

        // Red-black coloring logic keeps the same rule:
        // nodes at redLevel get marked RED to ensure proper black height.
        if (level == redLevel) {
            middle.color = RED;
        } else {
            middle.color = BLACK;
        }

        if (left != null) {
            middle.left = left;
            left.parent = middle;
        }

        Entry<K, V> right = null;
        if (mid < hi) {
            right = buildFromSorted(
                    level + 1,
                    mid + 1,
                    hi,
                    redLevel,
                    it,
                    str,
                    defaultVal,
                    keyCloner,
                    valueCloner
            );

            middle.right = right;
            right.parent = middle;
        }

        // EnhancedTreeMap addition:
        // maintain subtree size for order-statistic operations.
        // Each node stores how many nodes are in its subtree.
        int leftCount = (left != null ? left.size : 0);
        int rightCount = (right != null ? right.size : 0);
        middle.size = 1 + leftCount + rightCount;

        return middle;
    }

    private void buildFromSorted(int size,
                                 Iterator<? extends Map.Entry<? extends K, ? extends V>> it,
                                 ObjectInputStream str,
                                 V defaultVal,
                                 Function<? super K, ? extends K> keyCloner,
                                 Function<? super V, ? extends V> valueCloner)
            throws IOException, ClassNotFoundException {

        this.size = size;

        // computeRedLevel(size) is the same logic TreeMap uses:
        // it computes the level at which nodes will be marked red to get perfect-ish balance.
        int redLevel = computeRedLevel(size);

        this.root = buildFromSorted(
                0,
                0,
                size - 1,
                redLevel,
                it,
                str,
                defaultVal,
                keyCloner,
                valueCloner
        );

        // root is black by TreeMap invariant. If your code already forces BLACK on root elsewhere, keep that.
        if (root != null) {
            root.color = BLACK;
        }
    }


    private static int computeRedLevel(int sz) {
        int level = 0;
        for (int m = sz - 1; m >= 0; m = m / 2 - 1)
            level++;
        return level;
    }
}
