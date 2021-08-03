package io.packable;

public class PackConfig {
    /**
     * {@link CharArrayPool} used to buffer String's char[].
     * CharArrayPool only provide buffers which length = CHAR_BUFFER_SIZE,<br>
     * if the String's length(in utf8 encoding) is large than CHAR_BUFFER_SIZE, new char array will be allocated.<br>
     * So if your server/application need to decode long length String,
     * you could set CHAR_BUFFER_SIZE to a optimal size.
     */
    public static int CHAR_BUFFER_SIZE = 2048;

    /**
     * Object size limit, one million in default.
     * In case of error message to allocate too much memory.
     * You could adjust the size according to your situation.
     */
    public static int MAX_OBJECT_ARRAY_SIZE = 1 << 20;

    /**
     * Buffer size limit, 1G.
     * It's safety to limit the capacity.
     * Besides, it's not effective if the buffer is too large.
     */
    static final int MAX_BUFFER_SIZE = 1 << 30;

    /**
     * Limit of double memory (double again).
     * See {@link PackEncoder#checkCapacity(int)}
     */
    static final int DOUBLE_BUFFER_LIMIT = 1 << 22;

    /**
     * Before putting object and object array to buffer, we reserve 4 bytes to place the 'length',
     * When accomplish, we know the exactly 'length',
     * if the 'length' is less or equal than TRIM_SIZE_LIMIT, we retrieved 3 bytes (by moving bytes forward).</p>
     * <p>
     * We could set TRIM_SIZE_LIMIT up to 255, but it's not effective to move too many bytes to save 3 bytes.
     * Besides, object recursion might make moving bytes grow up,
     * set a little limit could make the recursion moving stop soon.
     */
    static final int TRIM_SIZE_LIMIT = 127;

    /**
     * use to mark null packable object
     */
    static final short NULL_PACKABLE = (short) 0xffff;
}
