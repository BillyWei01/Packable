package io.packable;

/**
 * Tag takes one byte or two bytes.
 * <p>
 * The first byte: <br>
 * from low to high (bit):<br>
 * [1-4] : index (when index is less than 16)<br>
 * [5-7] : type define<br>
 * [8] : index compress flag<br>
 * <p>
 * If the index bigger than 16, use the second byte to place the index,
 * and the compress flag will be 1, the [1-4] bit of the first index will be 0.
 * Now the tag support max index range in [0,255].
 */
final class TagFormat {
    private static final byte TYPE_SHIFT = 4;
    static final byte BIG_INDEX_MASK = (byte) (1 << 7);
    static final byte TYPE_MASK = 7 << TYPE_SHIFT;
    static final byte INDEX_MASK = 0xF;
    static final int LITTLE_INDEX_BOUND = 1 << TYPE_SHIFT;

    static final byte TYPE_0 = 0;
    static final byte TYPE_NUM_8 = 1 << TYPE_SHIFT;
    static final byte TYPE_NUM_16 = 2 << TYPE_SHIFT;
    static final byte TYPE_NUM_32 = 3 << TYPE_SHIFT;
    static final byte TYPE_NUM_64 = 4 << TYPE_SHIFT;
    static final byte TYPE_VAR_8 = 5 << TYPE_SHIFT;
    static final byte TYPE_VAR_16 = 6 << TYPE_SHIFT;
    static final byte TYPE_VAR_32 = 7 << TYPE_SHIFT;
}
