package io.packable;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * ByteArrayPool for {@link PackEncoder}
 */
class ByteArrayPool {
    // default array size : 4K
    private static final int DEFAULT_ARRAY_LEN_SHIFT = 12;
    private static final int DEFAULT_ARRAY_SIZE = 1 << DEFAULT_ARRAY_LEN_SHIFT;
    private static final int DEFAULT_CAPACITY = 8;
    private static int defaultCount = 0;
    private static final byte[][] defaultArrays = new byte[DEFAULT_CAPACITY][];

    // max pool array size: 4M
    private static final int MAX_ARRAY_SHIFT = 22;
    private static final int MAX_ARRAY_SIZE  = 1 << MAX_ARRAY_SHIFT;

    private static final int TEMP_ARRAYS_CAPACITY = MAX_ARRAY_SHIFT - DEFAULT_ARRAY_LEN_SHIFT;
    @SuppressWarnings("unchecked")
    private static final LinkedList<WeakReference<byte[]>>[] tempArrays = new LinkedList[TEMP_ARRAYS_CAPACITY];

    static byte[] getArray() {
        return getArray(DEFAULT_ARRAY_SIZE);
    }

    static byte[] getArray(int len) {
        if (len > PackConfig.MAX_BUFFER_SIZE) {
            throw new IllegalArgumentException("desire capacity over limit, len:" + len);
        }

        if (len <= DEFAULT_ARRAY_SIZE) {
            return getDefaultArray();
        }
        if (len > MAX_ARRAY_SIZE) {
            return new byte[len];
        }
        return getTempArray(getIndex(len));
    }

    private static int getIndex(int len) {
        if (len <= DEFAULT_ARRAY_SIZE) {
            return 0;
        }
        int n = 0;
        int a = (len - 1) >> DEFAULT_ARRAY_LEN_SHIFT;
        while (a != 0) {
            a >>>= 1;
            n++;
        }
        return n;
    }

    static void recycleArray(byte[] bytes) {
        if (bytes == null) {
            return;
        }
        int len = bytes.length;
        if (len == DEFAULT_ARRAY_SIZE) {
            recycleDefaultArray(bytes);
        } else if (len > DEFAULT_ARRAY_SIZE && len <= MAX_ARRAY_SIZE) {
            int index = getIndex(len);
            int capacity = 1 << (index + DEFAULT_ARRAY_LEN_SHIFT);
            if (len == capacity) {
                recycleTempArray(index, bytes);
            }
        }
    }

    private static byte[] getDefaultArray() {
        synchronized (defaultArrays) {
            if (defaultCount > 0) {
                byte[] a = defaultArrays[--defaultCount];
                defaultArrays[defaultCount] = null;
                return a;
            }
        }
        return new byte[DEFAULT_ARRAY_SIZE];
    }

    private static void recycleDefaultArray(byte[] bytes) {
        synchronized (defaultArrays) {
            if (defaultCount < DEFAULT_CAPACITY) {
                defaultArrays[defaultCount++] = bytes;
            }
        }
    }

    private static byte[] getTempArray(int index) {
        synchronized (tempArrays) {
            int start = index - 1;
            int end = Math.min(start + 3, TEMP_ARRAYS_CAPACITY);
            for (int j = start; j < end; j++) {
                LinkedList<WeakReference<byte[]>> list = tempArrays[j];
                if (list == null || list.isEmpty()) {
                    continue;
                }
                Iterator<WeakReference<byte[]>> it = list.iterator();
                while (it.hasNext()) {
                    WeakReference<byte[]> ref = it.next();
                    it.remove();
                    byte[] bytes = ref.get();
                    if (bytes != null) {
                        return bytes;
                    }
                }
            }
        }
        return new byte[1 << (index + DEFAULT_ARRAY_LEN_SHIFT)];
    }

    private static void recycleTempArray(int index, byte[] bytes) {
        int i = index - 1;
        if (i < TEMP_ARRAYS_CAPACITY) {
            synchronized (tempArrays) {
                LinkedList<WeakReference<byte[]>> list = tempArrays[i];
                if (list == null) {
                    list = new LinkedList<>();
                    tempArrays[i] = list;
                }
                list.add(new WeakReference<>(bytes));
            }
        }
    }
}
