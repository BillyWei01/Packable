package io.packable;

/**
 * LongArrayPool for {@link PackDecoder}
 */
class LongArrayPool {
    private static final int DEFAULT_SIZE = 32;
    private static final int DEFAULT_CAPACITY = 16;
    private static int defaultCount = 0;
    private static final long[][] defaultArrays = new long[DEFAULT_CAPACITY][];

    private static final int SECOND_SIZE = 64;
    private static final int SECOND_CAPACITY = 4;
    private static int secondCount = 0;
    private static final long[][] secondArrays = new long[SECOND_CAPACITY][];

    static long[] getArray(int size) {
        if (size <= DEFAULT_SIZE) {
            return getDefaultArray();
        } else if (size <= SECOND_SIZE) {
            return getSecondArray();
        } else {
            return new long[(size <= 128) ? 128 : 256];
        }
    }

    static void recycleArray(long[] a) {
        if (a == null) {
            return;
        }
        int size = a.length;
        if (size == DEFAULT_SIZE) {
            recycleDefaultArray(a);
        } else if (size == SECOND_SIZE) {
            recycleSecondArray(a);
        }
        // else, drop it
    }

    static long[] getDefaultArray() {
        synchronized (defaultArrays) {
            if (defaultCount > 0) {
                long[] a = defaultArrays[--defaultCount];
                defaultArrays[defaultCount] = null;
                return a;
            }
        }
        return new long[DEFAULT_SIZE];
    }

    private static void recycleDefaultArray(long[] a) {
        synchronized (defaultArrays) {
            if (defaultCount < DEFAULT_CAPACITY) {
                defaultArrays[defaultCount++] = a;
            }
        }
    }

    private static long[] getSecondArray() {
        synchronized (secondArrays) {
            if (secondCount > 0) {
                long[] a = secondArrays[--secondCount];
                secondArrays[secondCount] = null;
                return a;
            }
        }
        return new long[SECOND_SIZE];
    }

    private static void recycleSecondArray(long[] a) {
        synchronized (secondArrays) {
            if (secondCount < SECOND_CAPACITY) {
                secondArrays[secondCount++] = a;
            }
        }
    }
}
