package io.packable;

class CharArrayPool {
    private static final int CAPACITY = 8;
    private static int count = 0;
    private static final char[][] arrays = new char[CAPACITY][];
    static final int CHAR_BUFFER_SIZE = 2048;

    static char[] getArray(int len) {
        if (len <= CHAR_BUFFER_SIZE) {
            synchronized (arrays) {
                if (count > 0) {
                    char[] a = arrays[--count];
                    arrays[count] = null;
                    return a;
                }
            }
            return new char[CHAR_BUFFER_SIZE];
        } else {
            return new char[len];
        }
    }

    static void recycleArray(char[] a) {
        // drop other sizes, just keep default size char array
        if (a == null || a.length != CHAR_BUFFER_SIZE) {
            return;
        }
        synchronized (arrays) {
            if (count < CAPACITY) {
                arrays[count++] = a;
            }
        }
    }
}
