package com.elijahsarte.celtools.main.util.datastructures;

import com.elijahsarte.celtools.main.util.OptionalEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;

import java.io.*;
import java.util.*;

// https://github.com/openjdk/jdk14/blob/master/src/java.base/share/classes/java/util/TreeSet.java
@SuppressWarnings("unchecked")
public class EnhancedTreeSet<E> extends AbstractSet<E> implements NavigableSet<E>, Cloneable, Serializable {

    private transient EnhancedTreeMap<E, Object> m;
    private static final Object PRESENT = new Object();

    @Serial
    private static final long serialVersionUID = -2479143000061671589L;


    public EnhancedTreeSet(NavigableMap<E, Object> m) {
        this.m = m instanceof EnhancedTreeMap ? (EnhancedTreeMap<E, Object>) m : new EnhancedTreeMap<>(m);
    }
    public EnhancedTreeSet() {
        this(new EnhancedTreeMap<>());
    }
    public EnhancedTreeSet(Comparator<? super E> comparator) {
        this(new EnhancedTreeMap<>(comparator));
    }
    public EnhancedTreeSet(SortedSet<E> s) {
        this(s.comparator());
        addAll(s);
    }
    public EnhancedTreeSet(Collection<? extends E> c) {
        this();
        this.addAll(c);
    }


    public E get(int index) {
        return OptionalEx.ofNonNullable(m.getIdx(index)).then(Map.Entry::getKey).getVal();
    }
    public int indexOf(E e) {
        return m.indexOf(e);
    }

    public boolean contains(Object o) {
        return m.containsKey(o);
    }
    public boolean add(E e) {
        return m.put(e, PRESENT) == null;
    }
    public boolean remove(Object o) {
        return m.remove(o) == PRESENT;
    }
    public void clear() {
        m.clear();
    }

    public boolean addAll(Collection<? extends E> c) {
        if (m.isEmpty() && !c.isEmpty() &&
                c instanceof SortedSet<? extends E> set &&
                Objects.equals(set.comparator(), m.comparator())) {
            m.addAllForTreeSet(set, PRESENT);
            return true;
        }
        return super.addAll(c);
    }

    public NavigableSet<E> descendingSet() {
        return new EnhancedTreeSet<>(m.descendingMap());
    }

    public Iterator<E> iterator() {
        return m.navigableKeySet().iterator();
    }
    public Iterator<E> descendingIterator() {
        return m.descendingKeySet().iterator();
    }

    public E pollFirst() {
        return Optional.ofNullable(m.pollFirstEntry()).map(Map.Entry::getKey).orElse(null);
    }
    public E pollLast() {
        return Optional.ofNullable(m.pollLastEntry()).map(Map.Entry::getKey).orElse(null);
    }


    public int size() {
        return m.size();
    }
    public boolean isEmpty() {
        return m.isEmpty();
    }



    public NavigableSet<E> subSet(E fromElement, boolean fromInclusive,
                                  E toElement, boolean toInclusive) {
        return new EnhancedTreeSet<>(m.subMap(fromElement, fromInclusive,
                toElement, toInclusive));
    }
    public NavigableSet<E> headSet(E toElement, boolean inclusive) {
        return new EnhancedTreeSet<>(m.headMap(toElement, inclusive));
    }
    public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
        return new EnhancedTreeSet<>(m.tailMap(fromElement, inclusive));
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
    public Comparator<? super E> comparator() {
        return m.comparator();
    }
    public E first() {
        return m.firstKey();
    }
    public E last() {
        return m.lastKey();
    }
    public E lower(E e) {
        return m.lowerKey(e);
    }
    public E floor(E e) {
        return m.floorKey(e);
    }
    public E ceiling(E e) {
        return m.ceilingKey(e);
    }
    public E higher(E e) {
        return m.higherKey(e);
    }





    public Object clone() {
        EnhancedTreeSet<E> clone = ProgrammingEx.tryCat(() -> (EnhancedTreeSet<E>) super.clone(), InternalError::new);
        clone.m = new EnhancedTreeMap<>(m);
        return clone;
    }

    @Serial
    private void writeObject(ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();
        s.writeObject(m.comparator());
        s.writeInt(m.size());
        for (E e : m.keySet()) s.writeObject(e);
    }

    @Serial
    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        EnhancedTreeMap<E, Object> tm = (EnhancedTreeMap<E, Object>) Optional.ofNullable((Comparator<? super E>) s.readObject()).map(EnhancedTreeMap::new).orElse(new EnhancedTreeMap<>());
        m = tm;
        int size = s.readInt();
        tm.readTreeSet(size, s, PRESENT);
    }

}
