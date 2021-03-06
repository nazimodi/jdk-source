/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 */

/*
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.atomic;

/**
 * An {@code AtomicStampedReference} maintains an object reference
 * along with an integer "stamp", that can be updated atomically.
 * <p>
 * <p>Implementation note: This implementation maintains stamped
 * references by creating internal objects representing "boxed"
 * [reference, integer] pairs.
 *
 * @param <V> The type of object referred to by this reference
 * @author Doug Lea
 * @since 1.5
 */
//引入时间戳解决CAS的ABA问题。
//目标是A, 期望值也是A, 那么CAS操作会成功。但是这时候目标A可能不是原来的那个A了，
// 它可能是A变成了B，再变成了A。所以叫ABA问题
public class AtomicStampedReference<V> {

    //利用Pair类来记录对象引用和时间戳信息，时间戳应该自增。

    //不可变对象，其所有属性都为final，类似String。
    private static class Pair<T> {
        final T reference;
        final int stamp;

        //私有构造函数
        private Pair(T reference, int stamp) {
            this.reference = reference;
            this.stamp = stamp;
        }

        static <T> Pair<T> of(T reference, int stamp) {
            return new Pair<T>(reference, stamp);
        }
    }

    private volatile Pair<V> pair;

    /**
     * Creates a new {@code AtomicStampedReference} with the given
     * initial values.
     *
     * @param initialRef   the initial reference
     * @param initialStamp the initial stamp
     */
    public AtomicStampedReference(V initialRef, int initialStamp) {
        pair = Pair.of(initialRef, initialStamp);
    }

    /**
     * Returns the current value of the reference.
     *
     * @return the current value of the reference
     */
    public V getReference() {
        return pair.reference;
    }

    /**
     * Returns the current value of the stamp.
     *
     * @return the current value of the stamp
     */
    public int getStamp() {
        return pair.stamp;
    }

    /**
     * Returns the current values of both the reference and the stamp.
     * Typical usage is {@code int[1] holder; ref = v.get(holder); }.
     *
     * @param stampHolder an array of size of at least one.  On return,
     *                    {@code stampholder[0]} will hold the value of the stamp.
     * @return the current value of the reference
     */
    //这个用法有点特别，传入stampHolder，将stamp的值放入这个stampHolder数组中。
    public V get(int[] stampHolder) {
        Pair<V> pair = this.pair;
        stampHolder[0] = pair.stamp;
        return pair.reference;
    }

    /**
     * Atomically sets the value of both the reference and stamp
     * to the given update values if the
     * current reference is {@code ==} to the expected reference
     * and the current stamp is equal to the expected stamp.
     * <p>
     * <p>May <a href="package-summary.html#Spurious">fail spuriously</a>
     * and does not provide ordering guarantees, so is only rarely an
     * appropriate alternative to {@code compareAndSet}.
     *
     * @param expectedReference the expected value of the reference
     * @param newReference      the new value for the reference
     * @param expectedStamp     the expected value of the stamp
     * @param newStamp          the new value for the stamp
     * @return true if successful
     */
    public boolean weakCompareAndSet(V expectedReference,
                                     V newReference,
                                     int expectedStamp,
                                     int newStamp) {
        return compareAndSet(expectedReference, newReference,
                expectedStamp, newStamp);
    }

    /**
     * Atomically sets the value of both the reference and stamp
     * to the given update values if the
     * current reference is {@code ==} to the expected reference
     * and the current stamp is equal to the expected stamp.
     *
     * @param expectedReference the expected value of the reference
     * @param newReference      the new value for the reference
     * @param expectedStamp     the expected value of the stamp
     * @param newStamp          the new value for the stamp
     * @return true if successful
     */
    public boolean compareAndSet(V expectedReference,
                                 V newReference,
                                 int expectedStamp,
                                 int newStamp) {
        Pair<V> current = pair;
        //先检查期望的reference和stamp是否相等，相等的话，
        // 再检查新的reference和stamp是否相等，相等则直接返回true，但是Pair没有改变。
        //若新的reference和stamp不相等，则更创建新的Pair对象。
        return
                expectedReference == current.reference &&
                        expectedStamp == current.stamp &&
                        ((newReference == current.reference &&
                                newStamp == current.stamp) ||
                                casPair(current, Pair.of(newReference, newStamp)));
    }


    /**
     * Unconditionally sets the value of both the reference and stamp.
     *
     * @param newReference the new value for the reference
     * @param newStamp     the new value for the stamp
     */
    public void set(V newReference, int newStamp) {
        Pair<V> current = pair;
        if (newReference != current.reference || newStamp != current.stamp)
            this.pair = Pair.of(newReference, newStamp);
    }

    /**
     * Atomically sets the value of the stamp to the given update value
     * if the current reference is {@code ==} to the expected
     * reference.  Any given invocation of this operation may fail
     * (return {@code false}) spuriously, but repeated invocation
     * when the current value holds the expected value and no other
     * thread is also attempting to set the value will eventually
     * succeed.
     *
     * @param expectedReference the expected value of the reference
     * @param newStamp          the new value for the stamp
     * @return true if successful
     */
    //只改变stamp
    public boolean attemptStamp(V expectedReference, int newStamp) {
        Pair<V> current = pair;
        return
                expectedReference == current.reference &&
                        (newStamp == current.stamp ||
                                casPair(current, Pair.of(expectedReference, newStamp)));
    }

    // Unsafe mechanics

    private static final sun.misc.Unsafe UNSAFE = sun.misc.Unsafe.getUnsafe();
    private static final long pairOffset =
            objectFieldOffset(UNSAFE, "pair", AtomicStampedReference.class);

    private boolean casPair(Pair<V> cmp, Pair<V> val) {
        return UNSAFE.compareAndSwapObject(this, pairOffset, cmp, val);
    }

    static long objectFieldOffset(sun.misc.Unsafe UNSAFE,
                                  String field, Class<?> klazz) {
        try {
            return UNSAFE.objectFieldOffset(klazz.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            // Convert Exception to corresponding Error
            NoSuchFieldError error = new NoSuchFieldError(field);
            error.initCause(e);
            throw error;
        }
    }
}
