package io.packable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public final class PackEncoder {
    private static final byte ZERO = 0;
    private static final byte ONE = 1;

    private static final String NOT_SUPPORT_EMPTY_ELEMENT = "not support empty element";

    private volatile static int sMaxAllocated = ByteArrayPool.DEFAULT_ARRAY_SIZE;

    private final EncodeBuffer buffer;
    private boolean allASCII = true;

    public PackEncoder() {
        this(ByteArrayPool.DEFAULT_ARRAY_SIZE);
    }

    public PackEncoder(int budgetSize) {
        byte[] array = ByteArrayPool.getArray(budgetSize);
        buffer = new EncodeBuffer(array);
    }

    public static byte[] marshal(Packable packable) {
        return marshal(packable, ByteArrayPool.DEFAULT_ARRAY_SIZE);
    }

    public static byte[] marshal(Packable packable, int budgetSize) {
        PackEncoder encoder = new PackEncoder(budgetSize);
        packable.encode(encoder);
        return encoder.getBytes();
    }

    /**
     * After calling this method, don't call any method(including this) of current PackEncoder instance.
     * because the {@link #buffer} had been recycled.
     *
     * @return bytes
     */
    public byte[] getBytes() {
        checkBufferState();
        byte[] bytes = Arrays.copyOf(buffer.hb, buffer.position);
        recycle();
        return bytes;
    }

    public static class Result {
        public final byte[] bytes;
        public final int length;

        Result(byte[] b, int pos) {
            bytes = b;
            length = pos;
        }
    }

    /**
     * Use this function could read result bytes directly,
     * less memory allocate and copy compare with {@link #getBytes()}.
     *
     * @return Result
     */
    public Result getResult() {
        checkBufferState();
        return new Result(buffer.hb, buffer.position);
    }

    /**
     * Set buffer position to 0, to reuse PackEncoder
     */
    public void clear() {
        checkBufferState();
        buffer.position = 0;
    }

    public void recycle() {
        ByteArrayPool.recycleArray(buffer.hb);
        buffer.hb = null;
    }

    private void checkBufferState() {
        if (buffer.hb == null) {
            throw new IllegalStateException("Encoder had been recycled");
        }
    }

    EncodeBuffer getBuffer() {
        return buffer;
    }

    void checkCapacity(int expandSize) {
        int capacity = buffer.hb.length;
        int desSize = buffer.position + expandSize;
        if (desSize <= 0) {
            throw new IllegalStateException("desire capacity overflow");
        }
        if (desSize > capacity) {
            if (desSize > PackConfig.MAX_BUFFER_SIZE) {
                throw new IllegalStateException("desire capacity over limit");
            }
            int newSize = capacity << 1;
            while (desSize > newSize) {
                newSize = newSize << 1;
            }
            /*
             * If the init buffer is small and the final content length is large,
             * it's not effective to double buffer on every time that capacity not enough.
             * So we mark the sMaxAllocated to indicate the level of memory occupied,
             * when buffer is lower than sMaxAllocated, it's high probability to grow up to that level too,
             * and we double it again if the size still less than doubleLimit.
             * Example:
             * If desSize = 5K, newSize will be 8K,
             * If sMaxAllocated is larger than 8K, double size again to 16K, otherwise just to 8K.
             */
            int doubleLimit = Math.min(sMaxAllocated, PackConfig.DOUBLE_BUFFER_LIMIT);
            if (newSize < doubleLimit) {
                newSize = newSize << 1;
            }
            if (newSize > sMaxAllocated) {
                sMaxAllocated = newSize;
            }
            byte[] oldArray = buffer.hb;
            byte[] newArray = ByteArrayPool.getArray(newSize);
            System.arraycopy(oldArray, 0, newArray, 0, buffer.position);
            buffer.hb = newArray;
            ByteArrayPool.recycleArray(oldArray);
        }
    }

    final void putIndex(int index) {
        if (index >= TagFormat.LITTLE_INDEX_BOUND) {
            buffer.writeByte(TagFormat.BIG_INDEX_MASK);
        }
        buffer.writeByte((byte) (index));
    }

    public PackEncoder putByte(int index, byte value) {
        checkCapacity(3);
        if (value == 0) {
            putIndex(index);
        } else {
            if (index < TagFormat.LITTLE_INDEX_BOUND) {
                buffer.writeByte((byte) (index | TagFormat.TYPE_NUM_8));
            } else {
                buffer.writeByte((byte) (TagFormat.BIG_INDEX_MASK | TagFormat.TYPE_NUM_8));
                buffer.writeByte((byte) index);
            }
            buffer.writeByte(value);
        }
        return this;
    }

    public PackEncoder putBoolean(int index, boolean value) {
        return putByte(index, value ? ONE : ZERO);
    }

    public PackEncoder putShort(int index, short value) {
        checkCapacity(4);
        if (value == 0) {
            putIndex(index);
        } else {
            int pos = buffer.position;
            putIndex(index);
            if ((value >> 8) == 0) {
                buffer.hb[pos] |= TagFormat.TYPE_NUM_8;
                buffer.writeByte((byte) value);
            } else {
                buffer.hb[pos] |= TagFormat.TYPE_NUM_16;
                buffer.writeShort(value);
            }
        }
        return this;
    }

    public PackEncoder putInt(int index, int value) {
        checkCapacity(6);
        if (value == 0) {
            putIndex(index);
        } else {
            int pos = buffer.position;
            putIndex(index);
            if ((value >> 8) == 0) {
                buffer.hb[pos] |= TagFormat.TYPE_NUM_8;
                buffer.writeByte((byte) value);
            } else if ((value >> 16) == 0) {
                buffer.hb[pos] |= TagFormat.TYPE_NUM_16;
                buffer.writeShort((short) value);
            } else {
                buffer.hb[pos] |= TagFormat.TYPE_NUM_32;
                buffer.writeInt(value);
            }
        }
        return this;
    }

    public PackEncoder putLong(int index, long value) {
        checkCapacity(10);
        if (value == 0L) {
            putIndex(index);
        } else {
            int pos = buffer.position;
            putIndex(index);
            if ((value >> 32) != 0) {
                buffer.hb[pos] |= TagFormat.TYPE_NUM_64;
                buffer.writeLong(value);
            } else if ((value >> 8) == 0) {
                buffer.hb[pos] |= TagFormat.TYPE_NUM_8;
                buffer.writeByte((byte) value);
            } else if ((value >> 16) == 0) {
                buffer.hb[pos] |= TagFormat.TYPE_NUM_16;
                buffer.writeShort((short) value);
            } else {
                buffer.hb[pos] |= TagFormat.TYPE_NUM_32;
                buffer.writeInt((int) value);
            }
        }
        return this;
    }

    public PackEncoder putFloat(int index, float value) {
        checkCapacity(6);
        if (value == 0f) {
            putIndex(index);
        } else {
            if (index < TagFormat.LITTLE_INDEX_BOUND) {
                buffer.writeByte((byte) (index | TagFormat.TYPE_NUM_32));
            } else {
                buffer.writeByte((byte) (TagFormat.BIG_INDEX_MASK | TagFormat.TYPE_NUM_32));
                buffer.writeByte((byte) index);
            }
            buffer.writeFloat(value);
        }
        return this;
    }

    public PackEncoder putDouble(int index, double value) {
        checkCapacity(10);
        if (value == 0D) {
            putIndex(index);
        } else {
            if (index < TagFormat.LITTLE_INDEX_BOUND) {
                buffer.writeByte((byte) (index | TagFormat.TYPE_NUM_64));
            } else {
                buffer.writeByte((byte) (TagFormat.BIG_INDEX_MASK | TagFormat.TYPE_NUM_64));
                buffer.writeByte((byte) index);
            }
            buffer.writeDouble(value);
        }
        return this;
    }

    public PackEncoder putString(int index, String value) {

        if (value == null) {
            return this;
        }
        if (value.isEmpty()) {
            checkCapacity(2);
            putIndex(index);
            return this;
        }
        int n = value.length();
        if (n <= 85) {
            // Decoded data's max length will be 85*3=255 bytes
            checkCapacity(258);
            int pos = buffer.position;
            putIndex(index);
            buffer.hb[pos] |= TagFormat.TYPE_VAR_8;
            pos = buffer.position;
            // reserve one byte for store length of data
            buffer.position++;
            encodeStr(value);
            buffer.hb[pos] = (byte) (buffer.position - pos - 1);
        } else {
            if (allASCII && !isAllASCII(value)) {
                allASCII = false;
            }
            if (allASCII) {
                wrapTagAndLength(index, n);
                value.getBytes(0, n, buffer.hb, buffer.position);
                buffer.position += n;
            } else {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                wrapTagAndLength(index, bytes.length);
                buffer.writeBytes(bytes);
            }
        }
        return this;
    }

    /**
     * Wrap a String to buffer, used in array or container.
     */
    void wrapString(String str) {
        if (str == null) {
            checkCapacity(5);
            buffer.writeVarintNegative1();
        } else {
            int n = str.length();
            if (n == 0) {
                checkCapacity(1);
                buffer.hb[buffer.position++] = 0;
                return;
            }
            int sizeOfLen = EncodeBuffer.getVarint32Size(n);
            int pLen = buffer.position;
            buffer.position += sizeOfLen;
            checkCapacity(n * 3);
            int start = buffer.position;
            encodeStr(str);
            int len = buffer.position - start;
            int realSizeOfLen = EncodeBuffer.getVarint32Size(len);
            if (realSizeOfLen != sizeOfLen) {
                int diff = realSizeOfLen - sizeOfLen;
                checkCapacity(diff);
                System.arraycopy(buffer.hb, start, buffer.hb, start + diff, len);
                buffer.position += diff;
            }
            buffer.writeVarint32(pLen, len);
        }
    }

    private boolean isAllASCII(String s) {
        int len = s.length();
        for (int i = 0; i < len; i++) {
            if (s.charAt(i) >= 0x80) {
                return false;
            }
        }
        return true;
    }

    private void encodeStr(String s) {
        byte[] buf = buffer.hb;
        int j = buffer.position;
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i++);
            if (c < 0x80) {
                // 0xxxxxxx
                buf[j++] = (byte) c;
            } else if (c < 0x800) {
                // 110xxxxx 10xxxxxx
                buf[j++] = (byte) (0xC0 | (c >>> 6));
                buf[j++] = (byte) (0x80 | (0x3F & c));
            } else if ((c < 0xD800 || c > 0xDFFF)) {
                // 1110xxxx 10xxxxxx 10xxxxxx
                buf[j++] = (byte) (0xE0 | (c >>> 12));
                buf[j++] = (byte) (0x80 | (0x3F & (c >>> 6)));
                buf[j++] = (byte) (0x80 | (0x3F & c));
            } else {
                // 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                char c2 = s.charAt(i++);
                int cp = (c << 10) + c2 + 0xFCA02400;
                buf[j++] = (byte) (0xF0 | (cp >>> 18));
                buf[j++] = (byte) (0x80 | (0x3F & (cp >>> 12)));
                buf[j++] = (byte) (0x80 | (0x3F & (cp >>> 6)));
                buf[j++] = (byte) (0x80 | (0x3F & cp));
            }
        }
        buffer.position = j;
    }

    public PackEncoder putPackable(int index, Packable value) {
        if (value == null) {
            return this;
        }
        checkCapacity(6);
        int pTag = buffer.position;
        putIndex(index);
        // reserve 4 bytes to place length, it could be retrieved if not used
        buffer.position += 4;
        int pValue = buffer.position;
        value.encode(this);
        if (pValue == buffer.position) {
            buffer.position -= 4;
        } else {
            putLen(pTag, pValue);
        }
        return this;
    }

    private long wrapObjectArrayHeader(int index, int size) {
        if (size > PackConfig.MAX_OBJECT_ARRAY_SIZE) {
            throw new IllegalStateException("object array size out of limit");
        }
        // at most case: 2 bytes index, 4 bytes len, 5 bytes size
        checkCapacity(11);
        long pTag = buffer.position;
        putIndex(index);
        if (size <= 0) return -1;
        buffer.position += 4;
        long pValue = buffer.position;
        buffer.writeVarint32(size);
        return (pTag << 32) | pValue;
    }

    public PackEncoder putStringArray(int index, String[] value) {
        if (value == null) return this;
        long tagValue = wrapObjectArrayHeader(index, value.length);
        if (tagValue < 0) return this;
        for (String str : value) {
            wrapString(str);
        }
        putLen((int) (tagValue >>> 32), (int) tagValue);
        return this;
    }

    public PackEncoder putPackableArray(int index, Packable[] value) {
        if (value == null) return this;
        long tagValue = wrapObjectArrayHeader(index, value.length);
        if (tagValue < 0) return this;
        for (Packable pack : value) {
            wrapPackable(pack);
        }
        putLen((int) (tagValue >>> 32), (int) tagValue);
        return this;
    }

    /**
     * Wrap a object (implement Packable) to buffer, used in array or container.
     * <p>
     * When in {@link #putPackable}, we have tag to mark size of 'len',
     * but int array or container, we have to mark size by self.  <br>
     * The most simple way is using fixed 4 bytes(int) to store the 'len', but that wastes space.
     * We could use varint, but we don't know how many bytes the object will take,
     * calculate size before write? That costs more time, and complicated to implement.  <br>
     * So we could estimate size and write to buffer at first, after writing, we know the size.
     * If the estimated size is not enough to store the 'len' of object, move bytes.  <br>
     * Varint is too easy to miss match the size of 'len',  <br>
     * so we make two bytes as a unit (just call it wide varint ?)  <br>
     * In most case, 'len' of object in array is less than  0x7FFF (32767),
     * so it could generally avoid moving bytes, and save two bytes compare with the way of using fixed 4 bytes.
     */
    void wrapPackable(Packable pack) {
        checkCapacity(2);
        if (pack == null) {
            buffer.writeShort(PackConfig.NULL_PACKABLE);
        } else {
            int pLen = buffer.position;
            buffer.position += 2;
            int pPack = buffer.position;
            pack.encode(this);
            int len = buffer.position - pPack;
            if (len <= 0x7fff) {
                buffer.writeShort(pLen, (short) len);
            } else {
                checkCapacity(2);
                System.arraycopy(buffer.hb, pPack, buffer.hb, pPack + 2, len);
                buffer.position += 2;
                // if len >= 0x7FFF0000, the first two bytes will be 0xffff (means the object is null),
                // but the PackConfig.MAX_BUFFER_SIZE is 1 << 30,
                // so it's not impossible to be len >= 0x7fff0000
                buffer.writeShort(pLen, (short) ((len >>> 16) | 0x8000));
                buffer.writeShort(pLen + 2, (short) len);
            }
        }
    }

    private void putLen(int pTag, int pValue) {
        int len = buffer.position - pValue;
        if (len <= PackConfig.TRIM_SIZE_LIMIT) {
            buffer.hb[pTag] |= TagFormat.TYPE_VAR_8;
            buffer.hb[pValue - 4] = (byte) len;
            System.arraycopy(buffer.hb, pValue, buffer.hb, pValue - 3, len);
            buffer.position -= 3;
        } else {
            buffer.hb[pTag] |= TagFormat.TYPE_VAR_32;
            buffer.writeInt(pValue - 4, len);
        }
    }

    public PackEncoder putByteArray(int index, byte[] value) {
        if (value != null) {
            wrapTagAndLength(index, value.length);
            buffer.writeBytes(value);
        }
        return this;
    }

    public PackEncoder putIntArray(int index, int[] value) {
        if (value != null) {
            wrapTagAndLength(index, value.length << 2);
            for (int e : value) {
                buffer.writeInt(e);
            }
        }
        return this;
    }

    public PackEncoder putLongArray(int index, long[] value) {
        if (value != null) {
            wrapTagAndLength(index, value.length << 3);
            for (long e : value) {
                buffer.writeLong(e);
            }
        }
        return this;
    }

    public PackEncoder putFloatArray(int index, float[] value) {
        if (value != null) {
            wrapTagAndLength(index, value.length << 2);
            for (float e : value) {
                buffer.writeFloat(e);
            }
        }
        return this;
    }

    public PackEncoder putDoubleArray(int index, double[] value) {
        if (value != null) {
            wrapTagAndLength(index, value.length << 3);
            for (double e : value) {
                buffer.writeDouble(e);
            }
        }
        return this;
    }

    void wrapTagAndLength(int index, int len) {
        checkCapacity(6 + len);
        if (len == 0) {
            putIndex(index);
        } else {
            int pos = buffer.position;
            putIndex(index);
            if (len <= 0xff) {
                buffer.hb[pos] |= TagFormat.TYPE_VAR_8;
                buffer.writeByte((byte) len);
            } else if (len <= 0xffff) {
                buffer.hb[pos] |= TagFormat.TYPE_VAR_16;
                buffer.writeShort((short) len);
            } else {
                buffer.hb[pos] |= TagFormat.TYPE_VAR_32;
                buffer.writeInt(len);
            }
        }
    }

    /**
     * This method is used for putting custom data.
     * <p>
     * You could use this method to put your custom value combine with PackBuffer, which could obtain by {@link #getBuffer()}.
     * Be sure bytes putting by PackBuffer equal the param 'len'.
     *
     * @param index index of data
     * @param len   length of data
     * @return EncodeBuffer to write data.
     * @see PackDecoder#getCustom(int)
     */
    public EncodeBuffer putCustom(int index, int len) {
        wrapTagAndLength(index, len);
        return buffer;
    }

    int getListSize(int index, Collection<?> value) {
        if (value == null) {
            return 0;
        }
        if (value.isEmpty()) {
            wrapTagAndLength(index, 0);
            return 0;
        }
        return value.size();
    }

    public PackEncoder putIntList(int index, Collection<Integer> value) {
        int n = getListSize(index, value);
        if (n <= 0) return this;
        int[] a = new int[n];
        int i = 0;
        for (Integer x : value) {
            if (x == null) throw new IllegalArgumentException(NOT_SUPPORT_EMPTY_ELEMENT);
            a[i++] = x;
        }
        putIntArray(index, a);
        return this;
    }

    public PackEncoder putLongList(int index, Collection<Long> value) {
        int n = getListSize(index, value);
        if (n <= 0) return this;
        long[] a = new long[n];
        int i = 0;
        for (Long x : value) {
            if (x == null) throw new IllegalArgumentException(NOT_SUPPORT_EMPTY_ELEMENT);
            a[i++] = x;
        }
        putLongArray(index, a);
        return this;
    }

    public PackEncoder putFloatList(int index, Collection<Float> value) {
        int n = getListSize(index, value);
        if (n <= 0) return this;
        float[] a = new float[n];
        int i = 0;
        for (Float x : value) {
            if (x == null) throw new IllegalArgumentException(NOT_SUPPORT_EMPTY_ELEMENT);
            a[i++] = x;
        }
        putFloatArray(index, a);
        return this;
    }

    public PackEncoder putDoubleList(int index, Collection<Double> value) {
        int n = getListSize(index, value);
        if (n <= 0) return this;
        double[] a = new double[n];
        int i = 0;
        for (Double x : value) {
            if (x == null) throw new IllegalArgumentException(NOT_SUPPORT_EMPTY_ELEMENT);
            a[i++] = x;
        }
        putDoubleArray(index, a);
        return this;
    }

    public PackEncoder putPackableList(int index, Collection<? extends Packable> value) {
        if (value == null) return this;
        int size = value.size();
        long tagValue = wrapObjectArrayHeader(index, size);
        if (tagValue < 0) return this;
        for (Packable pack : value) {
            wrapPackable(pack);
        }
        putLen((int) (tagValue >>> 32), (int) tagValue);
        return this;
    }

    public PackEncoder putStringList(int index, Collection<String> value) {
        if (value == null) return this;
        long tagValue = wrapObjectArrayHeader(index, value.size());
        if (tagValue < 0) return this;
        for (String str : value) {
            wrapString(str);
        }
        putLen((int) (tagValue >>> 32), (int) tagValue);
        return this;
    }

    public PackEncoder putStr2Str(int index, Map<String, String> map) {
        if (map == null) return this;
        long tagValue = wrapObjectArrayHeader(index, map.size());
        if (tagValue < 0) return this;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            wrapString(entry.getKey());
            wrapString(entry.getValue());
        }
        putLen((int) (tagValue >>> 32), (int) tagValue);
        return this;
    }

    public PackEncoder putStr2Pack(int index, Map<String, ? extends Packable> map) {
        if (map == null) return this;
        long tagValue = wrapObjectArrayHeader(index, map.size());
        if (tagValue < 0) return this;
        for (Map.Entry<String, ? extends Packable> entry : map.entrySet()) {
            wrapString(entry.getKey());
            wrapPackable(entry.getValue());
        }
        putLen((int) (tagValue >>> 32), (int) tagValue);
        return this;
    }

    /**
     * Value of map only support Long, Integer, Double, Float,
     * and value can't be null.
     *
     * @param index index of value
     * @param map   the map
     * @return PackEncoder
     */
    public PackEncoder putStr2Number(int index, Map<String, ? extends Number> map) {
        if (map == null) return this;
        long tagValue = wrapObjectArrayHeader(index, map.size());
        if (tagValue < 0) return this;
        for (Map.Entry<String, ? extends Number> entry : map.entrySet()) {
            wrapString(entry.getKey());
            checkCapacity(8);
            Number x = entry.getValue();
            if (x instanceof Long) {
                buffer.writeLong((Long) x);
            } else if (x instanceof Integer) {
                buffer.writeInt((Integer) x);
            } else if (x instanceof Double) {
                buffer.writeDouble((Double) x);
            } else if (x instanceof Float) {
                buffer.writeFloat((Float) x);
            } else {
                if (x == null) {
                    throw new IllegalArgumentException(PackEncoder.NOT_SUPPORT_EMPTY_ELEMENT);
                }
                throw new IllegalArgumentException("value of map only support Long, Integer, Double, Float");
            }
        }
        putLen((int) (tagValue >>> 32), (int) tagValue);
        return this;
    }

    public PackEncoder putInt2Int(int index, Map<Integer, Integer> map) {
        if (map == null) return this;
        int size = map.size();
        long tagValue = wrapObjectArrayHeader(index, size);
        if (tagValue < 0) return this;
        checkCapacity(size << 3);
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            buffer.writeInt(entry.getKey());
            buffer.writeInt(entry.getValue());
        }
        putLen((int) (tagValue >>> 32), (int) tagValue);
        return this;
    }

    public PackEncoder putInt2Str(int index, Map<Integer, String> map) {
        if (map == null) return this;
        long tagValue = wrapObjectArrayHeader(index, map.size());
        if (tagValue < 0) return this;
        for (Map.Entry<Integer, String> entry : map.entrySet()) {
            checkCapacity(6);
            buffer.writeInt(entry.getKey());
            wrapString(entry.getValue());
        }
        putLen((int) (tagValue >>> 32), (int) tagValue);
        return this;
    }

    /**
     * PackEncoder/PackDecoder implements several map's encoding/decoding methods.  <br>
     * But map has many kinds of keys and values, we can't implement all of them.  <br>
     * So we provide {@link PackEncoder#putMap(int, int, PackEncoder.MapPacker)} to wrap key and value,
     * and provide {@link PackDecoder#getSize(int)} to get size of map.  <br>
     * You are free to use your own type,
     * even other dictionary data structure, like TreeMap, SparseArray (in Android SDK). <br>
     * <p>
     * Note: <br>
     * Be sure to call {@link PackEncoder#checkCapacity(int)} before putting data to {@link EncodeBuffer},
     * except to use {@link PackEncoder#wrapPackable(Packable)} and {@link PackEncoder#wrapString(String)},
     * they have implemented capacity check.
     */
    public interface MapPacker {
        void wrapMap();
    }

    public PackEncoder putMap(int index, int size, MapPacker packer) {
        long tagValue = wrapObjectArrayHeader(index, size);
        if (tagValue < 0) return this;
        packer.wrapMap();
        putLen((int) (tagValue >>> 32), (int) tagValue);
        return this;
    }

    public PackEncoder putBooleanArray(int index, boolean[] value) {
        CompactCoder.putBooleanArray(this, index, value);
        return this;
    }

    /**
     * Put an enum array (map to int value) to buffer, in a compact way.
     * Only accept values not larger than 255, otherwise will throw IllegalArgumentException.
     * More detail see {@link CompactCoder#putEnumArray(PackEncoder, int, int[])}
     *
     * @param index index of key
     * @param value bytes
     * @return PackEncoder
     */
    public PackEncoder putEnumArray(int index, int[] value) {
        CompactCoder.putEnumArray(this, index, value);
        return this;
    }

    /**
     * If many elements of array are many 0 or little integer,
     * use this method could save space.
     *
     * @param index index of key
     * @param value int array
     * @return PackEncoder
     */
    public PackEncoder putCompactIntArray(int index, int[] value) {
        CompactCoder.putIntArray(this, index, value);
        return this;
    }

    public PackEncoder putCompactLongArray(int index, long[] value) {
        CompactCoder.putLongArray(this, index, value);
        return this;
    }

    public PackEncoder putCompactDoubleArray(int index, double[] value) {
        CompactCoder.putDoubleArray(this, index, value);
        return this;
    }
}
