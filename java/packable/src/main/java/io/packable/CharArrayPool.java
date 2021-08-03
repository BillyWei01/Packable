package io.packable;

class CharArrayPool {
    private static final int CAPACITY = 10;
    private static int count = 0;
    private static final char[][] arrays = new char[CAPACITY][];

    static char[] getArray(int len) {
        if (len <= PackConfig.CHAR_BUFFER_SIZE) {
            synchronized (arrays) {
                if (count > 0) {
                    char[] a = arrays[--count];
                    arrays[count] = null;
                    return a;
                }
            }
            return new char[PackConfig.CHAR_BUFFER_SIZE];
        } else {
            return new char[len];
        }
    }

    static void recycleArray(char[] a) {
        // drop other sizes, just keep default size char array
        if (a == null || a.length != PackConfig.CHAR_BUFFER_SIZE) {
            return;
        }
        synchronized (arrays) {
            if (count < CAPACITY) {
                arrays[count++] = a;
            }
        }
    }
}
