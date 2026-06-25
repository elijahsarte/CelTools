package com.elijahsarte.celtools.main.util.structures.collections;

import com.elijahsarte.celtools.main.util.CollectionsEx;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.OptionalEx;
import com.elijahsarte.celtools.main.util.function.blocks.switchloop.ConditionalCase;
import com.elijahsarte.celtools.main.util.function.blocks.switchloop.SwitchIf;
import com.elijahsarte.celtools.main.util.function.fnvals.BiTuple;
import com.elijahsarte.celtools.main.util.structures.bounds.IntegerBounds;

import java.awt.*;
import java.lang.reflect.Array;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.*;

public final class IntList implements List<Integer> {

    private final List<IntegerBounds> list = new ArrayList<>();

    private int firstElem;
    private int lastElem;

    private int size;

    private boolean raw = false;


    public IntList(IntList list) {
        CollectionsEx.copy(this.list, list.list);
        this.firstElem = list.firstElem;
        this.lastElem = list.lastElem;
        this.size = list.size;
    }
    public IntList(List<IntegerBounds> list) {
        for (IntegerBounds bds : list) {
            this.list.add(bds);
            this.size += bds.getLength();
        }
//        CollectionsEx.copy(this.list, list);
        this.firstElem = list.get(0).getLowerBound();
        this.lastElem = CollectionsEx.lastElem(list).getUpperBound();
    }
    public IntList(Collection<? extends Integer> list) {
        this(CollectionsEx.toPrimitiveInt(list));
    }
    public IntList(int... providedNums) {

        int firstElem = INVALID_INT;
        int lastElem = INVALID_INT;
        if (providedNums.length == 0) return;
        int[] nums = Arrays.stream(providedNums).sorted().toArray();
        this.firstElem = nums[0];

        List<Double> sizes = new ArrayList<>(nums.length);
        double lastSize = 0;
        for (int num : nums) {

            if (invalid(firstElem)) {
                firstElem = num;
                lastElem = num;
                continue;
            }
            if (num - lastElem > 1) {
                list.add(new IntegerBounds(firstElem, lastElem));
                sizes.add(lastSize += (lastElem - firstElem + 1));
                firstElem = num;
                lastElem = num;
                continue;
            }
            lastElem = num;

        }
        list.add(new IntegerBounds(firstElem, lastElem));
        sizes.add(lastSize + (lastElem - firstElem + 1));

        this.lastElem = lastElem;
        this.size = nums.length;

    }
    public IntList(IntegerBounds initialBd) {
        this.list.add(initialBd);
        this.firstElem = initialBd.getLowerBound();
        this.lastElem = initialBd.getUpperBound();
        this.size = 1;
    }


    private BiTuple<IntegerBounds, Integer> getBd(int idx) {
        outOfBounds(idx);
        int offset = 0;
        for (IntegerBounds bds : list) {
            if (bds.getLength() == 0 && offset == idx) return BiTuple.of(bds, 0);
            if (idx >= offset && idx <= (offset + bds.getLength())) return BiTuple.of(bds, idx - offset);
            offset += Math.max(bds.getLength() + 1, 1);
        }
        throwOutOfBounds(idx);
        return null;
    }
    public Integer get(int idx) {
        outOfBounds(idx);
        if (idx == 0) return firstElem;
        if (idx == size() - 1) return lastElem;
        return varOper(getBd(idx),
                bdRes -> bdRes.first().getLowerBound() + bdRes.second());
    }
    public IntList discontinuities() {
        return list.size() <= 1 ? new IntList() : Stream.concat(
                Stream.of(list.get(0).getUpperBound(), CollectionsEx.lastElem(list).getLowerBound()),
                list.stream().skip(1).flatMap(IntegerBounds::streamBds).filter(i -> i != CollectionsEx.lastElem(list).getUpperBound())
        ).collect(toIntList());
    }

    @Override
    public Integer set(int index, Integer element) {
        throw new UnsupportedOperationException("Cannot insert number at index");
    }
    @Override
    public void add(int index, Integer element) {
        throw new UnsupportedOperationException("Cannot insert number at index");
    }

    private int lastSize = size, lastFirstElem = firstElem, lastLastElem = lastElem;
    private double probableRatio;
    private int probableIndex(int num) {
        if (lastSize != size || lastFirstElem != firstElem || lastLastElem != lastElem) {
            lastSize = size;
            lastFirstElem = firstElem;
            lastLastElem = lastElem;
            probableRatio = MathEx.divide(lastSize, lastLastElem - lastFirstElem + 1);
        }
        return MathEx.roundInt(MathEx.divide(num, probableRatio));
    }

    private int bdsIndexOf(int num) {
        if (list.isEmpty()) return -1;
        return CollectionsEx.binarySearchBds(list, num, probableIndex(num));
    }
    public int indexOf(int num) {
        if (list.isEmpty()) return -1;
        if (num < firstElem || num > lastElem) return -1;
        int bdsIndex = bdsIndexOf(num);
        if (bdsIndex == -1) return -1;
        return IntStream.range(0, bdsIndex).mapToObj(list::get).mapToInt(IntegerBounds::getLengthInc).sum() + varOper(list.get(bdsIndex), bds -> num - bds.getLowerBound());
//        return bdsIndex + varOper(list.get(bdsIndex), bds -> num - bds.getLowerBound());
    }
    public boolean contains(int num) {
        return indexOf(num) != -1;
    }


    private int ceilingBdsIndex(int num) {
        int low = 0, high = list.size() - 1, ans = list.size();
        while (low <= high) {
            int mid = (low + high) >>> 1;
            IntegerBounds bds = list.get(mid);
            if (bds.getUpperBound() >= num) {
                ans = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return ans;
    }

    private int[] bdsIndexOf(int... nums) {
        int[] out = new int[nums == null ? 0 : nums.length];
        Arrays.fill(out, -1);

        if (nums == null || nums.length == 0 || list.isEmpty()) return out;

        int fallbackFrom = -1;
        int cursor = ceilingBdsIndex(nums[0]);

        for (int i = 0; i < nums.length; i++) {
            int num = nums[i];

            if (i > 0 && nums[i - 1] > num) {
                fallbackFrom = i;
                break;
            }

            if (num < firstElem || num > lastElem) {
                out[i] = -1;
                continue;
            }

            while (cursor < list.size() && list.get(cursor).getUpperBound() < num) {
                cursor++;
            }

            if (cursor >= list.size()) {
                out[i] = -1;
                continue;
            }

            IntegerBounds bds = list.get(cursor);
            out[i] = (num >= bds.getLowerBound() && num <= bds.getUpperBound()) ? cursor : -1;
        }

        if (fallbackFrom != -1) {
            for (int i = fallbackFrom; i < nums.length; i++) {
                out[i] = bdsIndexOf(nums[i]);
            }
        }

        return out;
    }

    public int[] indexOf(int... nums) {
        int[] out = new int[nums == null ? 0 : nums.length];
        Arrays.fill(out, -1);

        if (nums == null || nums.length == 0 || list.isEmpty()) return out;

        int fallbackFrom = -1;
        int cursor = ceilingBdsIndex(nums[0]);
        int offset = 0;

        for (int i = 0; i < cursor; i++) {
            offset += list.get(i).getLengthInc();
        }

        for (int i = 0; i < nums.length; i++) {
            int num = nums[i];

            if (i > 0 && nums[i - 1] > num) {
                fallbackFrom = i;
                break;
            }

            if (num < firstElem || num > lastElem) {
                out[i] = -1;
                continue;
            }

            while (cursor < list.size() && list.get(cursor).getUpperBound() < num) {
                offset += list.get(cursor).getLengthInc();
                cursor++;
            }

            if (cursor >= list.size()) {
                out[i] = -1;
                continue;
            }

            IntegerBounds bds = list.get(cursor);
            out[i] = (num >= bds.getLowerBound() && num <= bds.getUpperBound())
                    ? offset + (num - bds.getLowerBound())
                    : -1;
        }

        if (fallbackFrom != -1) {
            for (int i = fallbackFrom; i < nums.length; i++) {
                out[i] = indexOf(nums[i]);
            }
        }

        return out;
    }

    public boolean[] contains(int... nums) {
        int[] idxs = indexOf(nums);
        boolean[] out = new boolean[idxs.length];
        for (int i = 0; i < idxs.length; i++) {
            out[i] = idxs[i] != -1;
        }
        return out;
    }



    private boolean add(int num, boolean reconstruct) {
        if (isEmpty()) {
            this.firstElem = num;
            this.lastElem = num;
            return OptionalEx.ofCond(list.add(new IntegerBounds(num, num))).thenRun(() -> {
                size++;
            }).get();
        }
        if (num > lastElem) {
            if (num - lastElem == 1) {
                CollectionsEx.lastElem(list).setUpperBound(num);
                size++;
            } else {
                return OptionalEx.ofCond(list.add(new IntegerBounds(num, num))).thenRun(() -> {
                    size++;
                    this.lastElem = num;
                }).get();
            }
            this.lastElem = num;
            return true;
        }
        if (num < firstElem) {
            if (num - firstElem == -1) {
                list.get(0).setLowerBound(num);
            } else {
                list.add(0, new IntegerBounds(num, num));
            }
            size++;
            this.firstElem = num;
            return true;
        }

        int[] res = CollectionsEx.closestBinarySearchBds(list, num, probableIndex(num));
        // 1 length indicates actual result, meaning num does not have to be added
        if (res.length == 1) {
            return false;
        }

        int setUpper = INVALID_INT, setLower = INVALID_INT;
        for (int loc : res) {
            IntegerBounds bds = list.get(loc);
            if (num - bds.getUpperBound() == 1) {
                bds.setUpperBound(num);
                setUpper = loc;
                if (!invalid(setLower)) break;
            }
            if (num - bds.getLowerBound() == -1) {
                bds.setLowerBound(num);
                setLower = loc;
                if (!invalid(setUpper)) break;
            }
        }

        if (!invalid(setUpper) && !invalid(setLower)) {
            int newLow = list.get(setUpper).getLowerBound();
            int newUpper = list.get(setLower).getUpperBound();
            list.remove(setUpper);
            list.remove(setLower - 1);
            list.add(setUpper, new IntegerBounds(newLow, newUpper));
        }
        else if (invalid(setUpper) && invalid(setLower)) {
            list.add(res[1], new IntegerBounds(num, num));
        }
        size++;
        return true;


    }
    public boolean add(int num) {
        return add(num, true);
    }
/*
    public boolean addAll(int... nums) {
        return Arrays.stream(nums).allMatch(n -> add(n, false));
    }
    public boolean addAll(Collection<? extends Integer> nums) {
        return nums.stream().allMatch(n -> add(n, false));
    }*/
    // Replace IntList.addAll(int... nums) with this version,
// and (optionally) update addAll(Collection) to delegate to it.

    public boolean addAll(int... arr) {
        if (arr == null || arr.length == 0) return true;

//        int[] arr = Arrays.copyOf(nums, nums.length);
        Arrays.sort(arr);

        boolean allNew = true;

        // De-dup (sorted). Keep a flag if duplicates were present in the input.
        int write = 0;
        int prev = INVALID_INT;
        for (int v : arr) {
            if (write == 0 || v != prev) {
                arr[write++] = v;
                prev = v;
            } else {
                allNew = false;
            }
        }
        if (write == 0) return true;
        arr = Arrays.copyOf(arr, write);

        // Empty list fast-path: build bounds in one pass (like the constructor does) [15].
        if (isEmpty()) {
            list.clear();

            int runLo = arr[0], runHi = runLo;
            for (int i = 1; i < arr.length; i++) {
                int v = arr[i];
                if (v == runHi + 1) {
                    runHi = v;
                } else {
                    list.add(new IntegerBounds(runLo, runHi));
                    runLo = runHi = v;
                }
            }
            list.add(new IntegerBounds(runLo, runHi));

            firstElem = arr[0];
            lastElem = arr[arr.length - 1];
            size = arr.length;
            return allNew;
        }

        // Start sweep near where the minimum "should" land [15].
        int cursor = probableIndex(arr[0]);
        if (cursor < 0) cursor = 0;
        if (cursor >= list.size()) cursor = list.size() - 1;

        // Nudge cursor locally so list[cursor] is "near" arr[0] without a full binary search.
        while (cursor > 0 && list.get(cursor).getLowerBound() > arr[0]) cursor--;
        while (cursor < list.size() - 1 && list.get(cursor).getUpperBound() < arr[0]) cursor++;

        // Process in contiguous runs to minimize bound operations.
        int i = 0;
        while (i < arr.length) {
            int runLo = arr[i], runHi = runLo;
            while (i + 1 < arr.length && arr[i + 1] == runHi + 1) {
                runHi = arr[++i];
            }
            i++;

            final int runLen = runHi - runLo + 1;

            // Advance cursor until the current bounds could overlap/attach (upper >= runLo-1).
            while (cursor < list.size() && list.get(cursor).getUpperBound() < runLo - 1) {
                cursor++;
            }

            // Past end => append (remaining runs are even larger).
            if (cursor >= list.size()) {
                list.add(new IntegerBounds(runLo, runHi));
                size += runLen;
                continue;
            }

            IntegerBounds b = list.get(cursor);
            int bLo = b.getLowerBound();
            int bHi = b.getUpperBound();

            // Entire run is strictly before b => insert new bounds here.
            if (runHi < bLo - 1) {
                list.add(cursor, new IntegerBounds(runLo, runHi));
                size += runLen;
                cursor++; // skip inserted bounds
                continue;
            }

            // Overlaps or attaches: merge run into b and absorb any following bounds that now touch.
            int mergedLo = Math.min(runLo, bLo);
            int mergedHi = Math.max(runHi, bHi);

            int overlap = overlapLen(runLo, runHi, bLo, bHi);
            if (overlap != 0) allNew = false;

            int j = cursor + 1;
            while (j < list.size() && list.get(j).getLowerBound() <= mergedHi + 1) {
                IntegerBounds nb = list.get(j);
                int nbLo = nb.getLowerBound();
                int nbHi = nb.getUpperBound();

                int ov = overlapLen(runLo, runHi, nbLo, nbHi);
                if (ov != 0) {
                    overlap += ov;
                    allNew = false;
                }

                if (nbLo < mergedLo) mergedLo = nbLo;
                if (nbHi > mergedHi) mergedHi = nbHi;
                j++;
            }

            // Remove absorbed bounds (cursor+1 .. j-1).
            if (j > cursor + 1) {
                list.subList(cursor + 1, j).clear();
            }

            // Mutate the kept bounds in place.
            b.setLowerBound(mergedLo);
            b.setUpperBound(mergedHi);

            // Only count truly-new integers from the run.
            int added = runLen - overlap;
            if (added > 0) size += added;
        }

        // Re-sync tracked endpoints [15].
        firstElem = list.get(0).getLowerBound();
        lastElem = list.get(list.size() - 1).getUpperBound();

        return allNew;
    }

    private static int overlapLen(int aLo, int aHi, int bLo, int bHi) {
        int lo = Math.max(aLo, bLo);
        int hi = Math.min(aHi, bHi);
        return (hi >= lo) ? (hi - lo + 1) : 0;
    }

    // Optional: make Collection version use the same fast path [1][15].
    public boolean addAll(Collection<? extends Integer> nums) {
        return addAll(CollectionsEx.toPrimitiveInt(nums));
    }

    @Override
    public boolean addAll(int index, Collection<? extends Integer> c) {
        throw new UnsupportedOperationException("Cannot add int at any index");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return c.stream().allMatch(cN -> remove(cN, false));
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return c.stream().filter(Predicate.not(c::contains)).allMatch(n -> remove(n, false));
    }

    public boolean addAll(IntList nums) {
        return nums.stream().allMatch(n -> add(n, false));
    }


    public Integer remove(int idx) {
        if (idx >= size()) throw new ArrayIndexOutOfBoundsException("Index " + idx + " out of bounds for length " + size());
        return SwitchIf.ofDefault(
                () -> removeElem(get(idx)),
                ConditionalCase.of(idx == 0, () -> removeElem(firstElem)),
                ConditionalCase.of(idx == size() - 1, () -> removeElem(lastElem))
        ).evaluate() ? -1 : null;
    }


    @Override
    public int indexOf(Object o) {
        if (!(o instanceof Integer oI)) return -1;
        return indexOf(oI.intValue());
    }
    @Override
    public int lastIndexOf(Object o) {
        return indexOf(o);
    }

    @Override
    public ListIterator<Integer> listIterator() {
        return listIterator(0);
    }

    @Override
    public ListIterator<Integer> listIterator(int startIndex) {
        outOfBounds(startIndex);
        return new ListIterator<>() {
            private int cursor = startIndex;
            private int lastRet = -1;

            @Override
            public boolean hasNext() { return cursor < size; }
            @Override
            public Integer next() {
                if (!hasNext()) throw new NoSuchElementException();
                Integer value = get(cursor);
                lastRet = cursor++;
                return value;
            }

            @Override
            public boolean hasPrevious() { return cursor > 0; }
            @Override
            public Integer previous() {
                if (!hasPrevious()) throw new NoSuchElementException();
                Integer value = get(--cursor);
                lastRet = cursor;
                return value;
            }

            @Override
            public int nextIndex() { return cursor; }
            @Override
            public int previousIndex() { return cursor - 1; }

            @Override
            public void remove() {
                if (lastRet < 0) throw new IllegalStateException();
                IntList.this.removeElem(get(lastRet));
                if (lastRet < cursor) cursor--;
                lastRet = -1;
            }

            @Override
            public void set(Integer e) {
                if (lastRet < 0) throw new IllegalStateException();
                throw new UnsupportedOperationException("set not supported");
            }

            @Override
            public void add(Integer e) {
                throw new UnsupportedOperationException("add not supported");
            }
        };
    }

    @Override
    public List<Integer> subList(int fromIndex, int toIndex) {
        outOfBounds(fromIndex);
        outOfBounds(toIndex);
        return stream().skip(fromIndex).limit(toIndex - fromIndex).toList();
    }

    private boolean removeElem(int num, boolean reconstruct) {

        if (num == this.firstElem) {
            IntegerBounds firstBds = list.get(0);
            if (firstBds.getLength() == 0) {
                list.remove(0);
                if (!list.isEmpty()) this.firstElem = list.get(0).getLowerBound();
            } else {
                this.firstElem = firstBds.incLowerBound();
            }
            size--;
            return true;
        }
        else if (num == this.lastElem) {
            IntegerBounds lastBds = list.get(list.size() - 1);
            int oldLen = CollectionsEx.lastElem(list).getLength();
            if (lastBds.getLength() == 0) {
                list.remove(list.size() - 1);
                if (!list.isEmpty()) {
                    this.lastElem = list.get(list.size() - 1).getUpperBound();
                }
            } else {
                this.lastElem = lastBds.decUpperBound();
            }
            size--;
            return true;
        }

        int numIndex = bdsIndexOf(num);
        if (numIndex == -1) return false;

        IntegerBounds bds = list.get(numIndex);
        if (bds.getLength() == 0) {
            list.remove(numIndex);
            size--;
            return true;
        }
        // dont remove it as soon as we get it just in case the function fails
        if (bds.getLowerBound() == num) {
            list.set(numIndex, new IntegerBounds(num + 1, bds.getUpperBound()));
            size--;
            return true;
        }
        if (bds.getUpperBound() == num) {
            list.set(numIndex, new IntegerBounds(bds.getLowerBound(), num - 1));
            size--;
            return true;
        }
        int lower = bds.getLowerBound();
        int upper = bds.getUpperBound();

        list.remove(numIndex);
        list.add(numIndex, new IntegerBounds(num + 1, upper));
        list.add(numIndex, new IntegerBounds(lower, num - 1));
        size--;
        return true;

    }
    public boolean removeElem(int num) {
        return removeElem(num, true);
    }

    public void clear() {
        this.list.clear();
        this.size = 0;
        this.firstElem = 0;
        this.lastElem = 0;
    }

    private void outOfBounds(int idx) {
        if (idx < 0 || idx >= size) throwOutOfBounds(idx);
    }
    private void throwOutOfBounds(int idx) {
        throw new ArrayIndexOutOfBoundsException("Index " + idx + " is out of bounds for length " + size);
    }
/*
    public void reassembleIdx() {
        boolean clr = listIdx.size() != list.size();
        if (clr) listIdx.clear();
        globalIdxCipher = 0;
        idxCiphers.clear();
        ignoreList.clear();

        AtomicInteger idxOffset = new AtomicInteger(0);
        for (int index = 0; index < list.size(); index++) {
            varExec(index,
            fIndex -> OptionalEx.ofCond(new IntegerBounds(idxOffset.get(), idxOffset.addAndGet(list.get(fIndex).getLength() + 1) - 1), clr)
                    .then(bds ->  listIdx.add(fIndex, bds))
                    .orElse((Consumer<IntegerBounds>) bds -> listIdx.set(fIndex, bds)));
        }
    }*/

    public int[] closest(int num) {
        return CollectionsEx.closestBinarySearchBds(list, num, probableIndex(num));
    }

    public int first() {
        return this.firstElem;
    }
    public int last() {
        return this.lastElem;
    }

    public int size() {
        return this.size;
    }
    public boolean isEmpty() {
        return this.size() <= 0 || list.isEmpty();
    }


    @Override
    public boolean contains(Object o) {
        if (!(o instanceof Integer oI)) return false;
        return contains(oI.intValue());
    }

    @Override
    public void forEach(Consumer<? super Integer> fn) {
        list.forEach(bds -> bds.forEach(fn));
    }
    public void forEachRaw(Consumer<IntegerBounds> fn) {
        if (!raw) throw new IllegalStateException("Cannot access bounds of IntList without raw access enabled");
        list.forEach(fn);
        offRaw();
    }

    public Stream<Integer> stream() {
        return list.stream().flatMap(IntegerBounds::stream);
    }

    public static Collector<Integer, ?, IntList> toIntList() {
        return Collector.of(
                IntList::new,
                IntList::add,
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                Collector.Characteristics.UNORDERED
        );
    }


    public List<Integer> toList() {
        return stream().toList();
    }
/*
    @Override
    public Iterator<Integer> iterator() {
        Function<Integer, Boolean> removeParent = this::remove;
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < size();
            }
            @Override
            public Integer next() {
                if (!hasNext()) throw new NoSuchElementException();
                // TODO: make this more performant by getting entire list at once
                // rather than calling get each time
                return get(index++);
            }
            @Override
            public void remove() {
                removeParent.apply(index);
            }
        };
    }*/


    public IntListIterator iterator() {
        if (isEmpty()) return new IntListIterator(true);
        return new IntListIterator(false);
    }

    public class IntListIterator implements Iterator<Integer> {
        private int globalIndex = 0; // next element index for forward element iteration
        private int bdsIndex = 0;
        private IntegerBounds bds;
        private int curr;
        private int upper;

        private int nextBdCursor = 0;
        private int nextBdGlobalIndex = 0;
        private int currentBdStartIndex = -1;

        private boolean canRemove = false;
        private int lastReturned;
        private final boolean empty;

        // cached "previous" values from forward traversal
        private boolean hasPrevElemCache = false;
        private int prevElemCache;

        private boolean hasPrevBdCache = false;
        private IntegerBounds prevBdCache;
        private int prevBdStartIndex = -1;

        public IntListIterator(boolean empty) {
            this.empty = empty;
            if (!empty) {
                this.bds = list.get(0);
                this.curr = bds.getLowerBound();
                this.upper = bds.getUpperBound();
            }
        }

        @Override
        public boolean hasNext() {
            return !empty && globalIndex < IntList.this.size;
        }

        @Override
        public Integer next() {
            if (!hasNext()) throw new NoSuchElementException();

            final int out = curr;

            lastReturned = out;
            canRemove = true;

            // cache for peekPrevious()
            prevElemCache = out;
            hasPrevElemCache = true;

            globalIndex++;

            if (curr < upper) {
                curr++;
            } else {
                bdsIndex++;
                if (bdsIndex < list.size()) {
                    bds = list.get(bdsIndex);
                    curr = bds.getLowerBound();
                    upper = bds.getUpperBound();
                }
            }
            return out;
        }

        public boolean hasPrevious() {
            return !empty && globalIndex > 0;
        }

        public Integer previous() {
            if (!hasPrevious()) throw new NoSuchElementException();

            globalIndex--;
            seekToGlobalIndex(globalIndex);

            int out = curr;
            lastReturned = out;
            canRemove = true;

            // cache for peekPrevious()
            prevElemCache = out;
            hasPrevElemCache = true;

            return out;
        }

        public Integer peekNext() {
            if (!hasNext()) throw new NoSuchElementException();
            return curr;
        }

        public Integer peekPrevious() {
            if (!hasPrevElemCache) throw new NoSuchElementException("No previous element has been traversed");
            return prevElemCache;
        }

        public boolean hasNextBd() {
            return raw && nextBdCursor < list.size();
        }

        public IntegerBounds nextBd() {
            if (!raw) {
                throw new IllegalStateException("Cannot access bounds of IntList without raw access enabled");
            }
            if (nextBdCursor >= list.size()) {
                currentBdStartIndex = -1;
                offRaw();
                return null;
            }

            IntegerBounds bd = list.get(nextBdCursor);
            currentBdStartIndex = nextBdGlobalIndex;

            // cache for peekPreviousBd()
            prevBdCache = bd;
            prevBdStartIndex = currentBdStartIndex;
            hasPrevBdCache = true;

            nextBdCursor++;
            nextBdGlobalIndex += bd.getLengthInc();

            return bd;
        }

        public boolean hasPreviousBd() {
            return raw && !list.isEmpty() && nextBdCursor > 0;
        }

        public IntegerBounds previousBd() {
            if (!raw) {
                throw new IllegalStateException("Cannot access bounds of IntList without raw access enabled");
            }
            if (!hasPreviousBd()) {
                throw new NoSuchElementException();
            }

            nextBdCursor--;
            IntegerBounds bd = list.get(nextBdCursor);
            nextBdGlobalIndex -= bd.getLengthInc();
            currentBdStartIndex = nextBdGlobalIndex;

            // cache for peekPreviousBd()
            prevBdCache = bd;
            prevBdStartIndex = currentBdStartIndex;
            hasPrevBdCache = true;

            return bd;
        }

        public IntegerBounds peekNextBd() {
            if (!raw) {
                throw new IllegalStateException("Cannot access bounds of IntList without raw access enabled");
            }
            if (!hasNextBd()) {
                throw new NoSuchElementException();
            }
            return list.get(nextBdCursor);
        }

        public IntegerBounds peekPreviousBd() {
            if (!hasPrevBdCache) throw new NoSuchElementException("No previous bounds have been traversed");
            return prevBdCache;
        }

        public boolean hasCurrent() {
            return canRemove;
        }
        public Integer current() {
            if (!hasCurrent()) {
                throw new NoSuchElementException("No current element is set");
            }
            return lastReturned;
        }

        public int currentBdStartIndex() {
            return currentBdStartIndex;
        }

        public int peekPreviousBdStartIndex() {
            if (!hasPrevBdCache) throw new NoSuchElementException("No previous bounds have been traversed");
            return prevBdStartIndex;
        }

        @Override
        public void remove() {
            if (!canRemove) throw new IllegalStateException("next() must be called before remove()");
            canRemove = false;
            IntList.this.removeElem(lastReturned);
            globalIndex--;
            seekToGlobalIndex(globalIndex);
        }

        public boolean contains(int target) {
            if (empty || list.isEmpty()) return false;

            if (prevElemCache == target) return true;
            // If we still have a forward position, use it to decide search direction.
            if (hasNext()) {
                int current = peekNext();

                if (current == target) {
                    return true; // already positioned on target
                }

                if (target > current) {
                    // Walk forward until we reach or pass target.
                    while (hasNext() && peekNext() < target) {
                        next();
                    }

                    // If we landed on target, leave iterator positioned there.
                    if (hasNext() && peekNext() == target) {
                        return true;
                    }

                    // We passed target or ran off the end:
                    // move back to the previous actual element, if any.
                    if (hasPrevious()) {
                        previous();
                    }
                    return false;
                }
            }

            // Either:
            // 1) target < current forward element, or
            // 2) we're already at the end and must search backward.
            while (hasPrevious()) {
                int prev = previous();

                if (prev == target) {
                    return true; // positioned on target
                }

                if (prev < target) {
                    return false; // positioned on greatest element < target
                }
            }

            return false;
        }

        private void seekToGlobalIndex(int idx) {
            if (IntList.this.size <= 0 || list.isEmpty()) return;

            int offset = 0;
            for (int i = 0; i < list.size(); i++) {
                IntegerBounds bb = list.get(i);
                int len = bb.getLengthInc();
                int nextOffset = offset + len;

                if (idx < nextOffset) {
                    bdsIndex = i;
                    bds = bb;
                    curr = bb.getLowerBound() + (idx - offset);
                    upper = bb.getUpperBound();
                    return;
                }
                offset = nextOffset;
            }

            bdsIndex = list.size();
        }
    }

    @Override
    public Object[] toArray() {
        Object[] arr = new Object[size()];
        int i = 0;
        for (Integer v : this) arr[i++] = v;
        return arr;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        int sz = size();
        T[] out = a.length >= sz ? a
                : (T[]) Array.newInstance(a.getClass().getComponentType(), sz);
        int i = 0;
        for (Integer v : this) out[i++] = (T) v;
        if (out.length > sz) out[sz] = null;
        return out;
    }

    @Override
    public boolean add(Integer integer) {
        return add(integer.intValue());
    }

    private boolean remove(Object o, boolean reconstruct) {
        if (!(o instanceof Integer oI)) return false;
        return removeElem(oI, reconstruct);
    }
    @Override
    public boolean remove(Object o) {
        return remove(o, true);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return c.stream().allMatch(this::contains);
    }

    @Override
    public Spliterator<Integer> spliterator() {
        return Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED);
    }

    @Override
    public String toString() {
        StringJoiner elems = new StringJoiner(", ");
        forEach(i -> elems.add(i.toString()));
        return "[" + elems + "]";
    }

    @Override
    public boolean equals(Object intListObj) {
        if (!(intListObj instanceof IntList intList)) return false;
        if (intList.size() != size()) return false;
        for (int i = 0; i < intList.size(); i++) {
            if (!Objects.equals(intList.get(i), get(i))) return false;
        }
        return true;
    }

    public void onRaw() {
        this.raw = true;
    }
    public void offRaw() {
        this.raw = false;
    }




    public static void main(String[] args) {
        /*
        // default size: 13
        IntList l = new IntList(3, 5,6,7,8,10,11,19,21,28,29,30,37);

        l.add(31);
        l.add(20);
        l.add(9);
        l.add(1);
        l.add(98);
        l.add(55);
        l.add(23);
        l.add(22);

        l.remove(23);*/

        System.out.println("=== Testing IntList ===");

        // 1. Test Construction
        IntList l = new IntList(3, 5, 6, 7, 8, 10, 11, 19, 21, 28, 29, 30, 37);
        System.out.println("Initial list: " + l);
        // Expected: [3, 5, 6, 7, 8, 10, 11, 19, 21, 28, 29, 30, 37]

        // 2. Test add (inserts in sorted position)
        l.add(42);
        l.add(1);
        l.add(9);
        System.out.println("After additions: " + l);
        // Expected: [1, 3, 5, 6, 7, 8, 9, 10, 11, 19, 21, 28, 29, 30, 37, 42]

        // 3. Test get
        System.out.println("Element at index 0: " + l.get(0)); // Expected: 1
        System.out.println("Element at index 5: " + l.get(5)); // Expected: 8
        System.out.println("Element at last index: " + l.get(l.size() - 1)); // Expected: 42

        // 4. Test remove
        l.remove(0); // remove 1
        l.remove(4); // originally 8, now 9
        l.remove(l.size() - 1); // remove 42
        System.out.println("After removals: " + l);
        // Expected: [3, 5, 6, 7, 9, 10, 11, 19, 21, 28, 29, 30, 37]

        // 5. Verify final contents
        int[] expected = {3, 5, 6, 7, 9, 10, 11, 19, 21, 28, 29, 30, 37};
        boolean success = true;
        for (int i = 0; i < expected.length; i++) {
            if (l.get(i) != expected[i]) {
                success = false;
                System.out.println("Mismatch at index " + i + ": expected " + expected[i] + ", got " + l.get(i));
            }
        }

        if (success) {
            System.out.println("All tests passed!");
        } else {
            System.out.println("Some tests failed.");
        }

        l.add(89);
        l.add(100);
        l.add(101);
        l.add(102);
        l.add(25);
        l.add(26);
        l.add(62);
        l.add(0);
        l.add(78);
        l.add(80);


//        l.add(38);
//        l.remove()


//        l.add(22);
//        l.remove(22);

        // total 19
        for (Integer integer : l) {
            System.out.println(integer);
        }
        System.out.println("a");
    }


}

