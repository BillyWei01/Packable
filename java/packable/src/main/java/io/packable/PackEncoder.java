package io.packable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * PackEncoder
 * 作用：将对象编码成字节数组
 */
public final class PackEncoder {
    private static final byte ZERO = 0;
    private static final byte ONE = 1;
    private static final byte[] EMPTY_ARRAY = new byte[0];

    private static final String NOT_SUPPORT_EMPTY_ELEMENT = "not support empty element";

    private final EncodeBuffer buffer;
    private boolean allASCII = true;
    private boolean recycled = false;

    public PackEncoder() {
        byte[] array = ByteArrayPool.getArray();
        buffer = new EncodeBuffer(array);
    }

    public static <T> byte[] encode(T target, Packer<T> packer) {
        PackEncoder encoder = new PackEncoder();
        packer.pack(encoder, target);
        return encoder.toBytes();
    }

    public static <T> byte[] encodeList(List<T> value, Packer<T> packer) {
        if (value.isEmpty()) return EMPTY_ARRAY;
        PackEncoder encoder = new PackEncoder();
        encoder.buffer.writeVarInt32(value.size());
        for (T e : value) {
            encoder.wrapObject(e, packer);
        }
        return encoder.toBytes();
    }

    public static byte[] encodeIntArray(int[] value) {
        if (value == null || value.length == 0) return EMPTY_ARRAY;
        PackEncoder encoder = new PackEncoder();
        int totalSize = EncodeBuffer.getVarInt32Size(value.length) + value.length * 4;
        encoder.buffer.checkCapacity(totalSize);
        encoder.buffer.writeVarInt32(value.length);
        for (int e : value) {
            encoder.buffer.writeInt(e);
        }
        return encoder.toBytes();
    }

    public static byte[] encodeLongArray(long[] value) {
        if (value == null || value.length == 0) return EMPTY_ARRAY;
        PackEncoder encoder = new PackEncoder();
        int totalSize = EncodeBuffer.getVarInt32Size(value.length) + value.length * 8;
        encoder.buffer.checkCapacity(totalSize);
        encoder.buffer.writeVarInt32(value.length);
        for (long e : value) {
            encoder.buffer.writeLong(e);
        }
        return encoder.toBytes();
    }

    /**
     * 获取编码的字节数组。<br>
     * 当调用此方法后，[buffer] 会被回收，请勿再次调用
     */
    public byte[] toBytes() {
        checkBufferState();
        byte[] bytes = Arrays.copyOf(buffer.hb, buffer.position);
        recycle();
        return bytes;
    }

    private void recycle() {
        checkBufferState();
        ByteArrayPool.recycleArray(buffer.hb);
        buffer.hb = null;
        recycled = true;
    }

    private void checkBufferState() {
        if (recycled) {
            throw new IllegalStateException("Encoder had been recycled");
        }
    }

    private void putIndex(int index) {
        if (index >= TagFormat.LITTLE_INDEX_BOUND) {
            buffer.writeByte(TagFormat.BIG_INDEX_MASK);
        }
        buffer.writeByte((byte) (index));
    }

    public PackEncoder putByte(int index, byte value) {
        buffer.checkCapacity(3);
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
        buffer.checkCapacity(4);
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
        buffer.checkCapacity(6);
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
        buffer.checkCapacity(10);
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
        buffer.checkCapacity(6);
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
        buffer.checkCapacity(10);
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
            buffer.checkCapacity(2);
            putIndex(index);
            return this;
        }
        int n = value.length();
        if (n <= 85) {
            // Decoded data's max length will be 85*3=255 bytes
            buffer.checkCapacity(258);
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
                // 当所有的字符都是ascii码时，直接强转char成byte, 更有效率
                //noinspection deprecation
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
     * 打包String。
     * 用于数组或者Map。
     * 与直接编码字段不同，数组中的对象需要自己管理长度，不能像字段那样借助 Tag 来指示长度有多少个字节。
     * 因此，需要能自行解析的编码。
     * 比如 String 这里就用的 varint 编码，因为数组中的字符串通常不会太长，用 varint 表示通常一到两个字节就足够了。
     */
    private void wrapString(String str) {
        if (str == null) {
            buffer.checkCapacity(5);
            buffer.writeVarIntNegative1();
        } else {
            int n = str.length();
            if (n == 0) {
                buffer.checkCapacity(1);
                buffer.hb[buffer.position++] = 0;
                return;
            }
            // 按照字符串全都是 ascii 字符来预留size的空间
            int sizeOfLen = EncodeBuffer.getVarInt32Size(n);
            int pLen = buffer.position;
            buffer.position += sizeOfLen;

            // 按照最大占用空间确保buffer的容量
            buffer.checkCapacity(n * 3);
            int start = buffer.position;
            encodeStr(str);
            int len = buffer.position - start;

            // 编码结束，得到真正的占用空间大小，和预估的大小对，不想等则移动value腾出空间
            int realSizeOfLen = EncodeBuffer.getVarInt32Size(len);
            if (realSizeOfLen != sizeOfLen) {
                int diff = realSizeOfLen - sizeOfLen;
                buffer.checkCapacity(diff);
                System.arraycopy(buffer.hb, start, buffer.hb, start + diff, len);
                buffer.position += diff;
            }
            buffer.writeVarInt32(pLen, len);
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

                // 代理对转码点（CodePoint)
                // 合并了一些计算项目，得到如今这条转换公式
                int cp = (c << 10) + c2 + 0xFCA02400;
                buf[j++] = (byte) (0xF0 | (cp >>> 18));
                buf[j++] = (byte) (0x80 | (0x3F & (cp >>> 12)));
                buf[j++] = (byte) (0x80 | (0x3F & (cp >>> 6)));
                buf[j++] = (byte) (0x80 | (0x3F & cp));
            }
        }
        buffer.position = j;
    }

    public <T> PackEncoder putObject(int index, T value, Packer<T> packer) {
        if (value == null) {
            return this;
        }
        buffer.checkCapacity(6);
        int pTag = buffer.position;
        putIndex(index);
        // 预留 4 字节来存放长度，如果最终用不了那么多，则回退
        buffer.position += 4;
        int pValue = buffer.position;
        packer.pack(this, value);
        if (pValue == buffer.position) {
            buffer.position -= 4;
        } else {
            putLen(pTag, pValue);
        }
        return this;
    }

    private long wrapObjectArrayHeader(int index, int size) {
        if (size > PackConfig.maxObjectArraySize) {
            throw new IllegalStateException("object array size out of limit");
        }
        // at most case: 2 bytes index, 4 bytes len, 5 bytes size
        buffer.checkCapacity(11);
        long pTag = buffer.position;
        putIndex(index);
        if (size <= 0) return -1;
        buffer.position += 4;
        long pValue = buffer.position;
        buffer.writeVarInt32(size);
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

    public <T> PackEncoder putObjectArray(int index, T[] value, Packer<T> packer) {
        if (value == null) return this;
        long tagValue = wrapObjectArrayHeader(index, value.length);
        if (tagValue < 0) return this;
        for (T e : value) {
            wrapObject(e, packer);
        }
        putLen((int) (tagValue >>> 32), (int) tagValue);
        return this;
    }

    /**
     * 编码对象
     * <br>
     * 此方法用于编码放入容器（Array, List, Map等）的对象。
     * [putObject] 编码对象，可以从 tag 中判断对象大小占用的空间；
     * 而 [wrapObject] 编码对象，需要自己判断。
     * 直接设定4个字节太浪费，用 varint 编码则难以预留空间（当前采用先编码后写长度的方式）。
     * 因此，这里我们借用 varint 编码的思想，但是单位长度用两个字节：
     * <br>
     * 1. 编码前先预留两个字节，编码后计算对象大小。<br>
     * 2. 如果小于 0x7fff，则直接填写长度；否则，往后挪两个字节，再填写长度。<br>
     * 3. 如果对象为空，则填充 0xffff。<br>
     * <p>
     * 由于预留两个字节，所以当对象占用空间少于 32768 时，不需要移动位置。
     * 编码 Object 和 String 的长度采用不同的方式，是综合权衡时间和空间的结果。
     */
    private <T> void wrapObject(T target, Packer<T> packer) {
        buffer.checkCapacity(2);
        if (target == null) {
            buffer.writeShort(PackConfig.NULL_OBJECT_FLAG);
        } else {
            int pLen = buffer.position;
            buffer.position += 2;
            int pPack = buffer.position;
            packer.pack(this, target);
            int len = buffer.position - pPack;
            if (len <= 0x7fff) {
                buffer.writeShort(pLen, (short) len);
            } else {
                buffer.checkCapacity(2);
                System.arraycopy(buffer.hb, pPack, buffer.hb, pPack + 2, len);
                buffer.position += 2;
                // 因为 MAX_BUFFER_SIZE = 1 << 30，所以长度不会大于等于 0x7fff0000。
                // 因此:
                // len 的最高位不会是 1，我们可以用最高位来标记长度是两个字节；
                // len 的高16bit也不会等于 0x7fff, 所以执行 “or 0x8000” 不会等于 0xffff (NULL_OBJECT_FLAG)
                buffer.writeShort(pLen, (short) ((len >>> 16) | 0x8000));
                buffer.writeShort(pLen + 2, (short) len);
            }
        }
    }

    private void putLen(int pTag, int pValue) {
        int len = buffer.position - pValue;
        if (len <= 128) {
            buffer.hb[pTag] |= TagFormat.TYPE_VAR_8;
            buffer.hb[pValue - 4] = (byte) len;
            System.arraycopy(buffer.hb, pValue, buffer.hb, pValue - 3, len);
            buffer.position -= 3;
        } else {
            buffer.hb[pTag] |= TagFormat.TYPE_VAR_32;
            buffer.writeInt(pValue - 4, len);
        }
    }

    public PackEncoder putBooleanArray(int index, boolean[] value) {
        if (value == null) return this;
        int n = value.length;
        if (n == 0) {
            wrapTagAndLength(index, 0);
            return this;
        }

        if (n <= 5) {
            byte b = (byte) (n << 5);
            for (int i = 0; i < n; i++) {
                if (value[i]) {
                    b |= 1 << i;
                }
            }
            wrapTagAndLength(index, 1);
            buffer.writeByte(b);
        } else {
            int remain = n & 0x7;
            int byteCount = (n >> 3) + (remain == 0 ? 1 : 2);
            wrapTagAndLength(index, byteCount);
            buffer.writeByte((byte) remain);
            int i = 0;
            while (i < n) {
                int end = Math.min(i + 8, n);
                byte b = 0;
                for (int j = i; j < end; j++) {
                    if (value[j]) {
                        b |= 1 << (j & 0x7);
                    }
                }
                buffer.writeByte(b);
                i = end;
            }
        }
        return this;
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
        buffer.checkCapacity(6 + len);
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

    public <T> PackEncoder putObjectList(int index, Collection<? extends T> value, Packer<T> packer) {
        if (value == null) return this;
        int size = value.size();
        long tagValue = wrapObjectArrayHeader(index, size);
        if (tagValue < 0) return this;
        for (T e : value) {
            wrapObject(e, packer);
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

    /**
     * 编码Map
     *
     * @param <K> 支持[Int, Long, String]类型。
     * @param <V> 支持[Boolean, Int, Long, Float, Double, String]类型。
     */
    public <K, V> PackEncoder putMap(int index, Map<K, V> map) {
        return putMap(index, map, null, null);
    }

    /**
     * 编码Map
     *
     * @param <K> 支持[Int, Long, String]类型。
     * @param <V> 支持[Boolean, Int, Long, Float, Double, String]类型。 可通过传 [valuePacker] 支持其他类型。
     */
    public <K, V> PackEncoder putMap(int index, Map<K, V> map, Packer<V> valuePacker) {
        return putMap(index, map, null, valuePacker);
    }

    /**
     * 编码Map
     *
     * @param <K> 支持[Int, Long, String]类型。 可通过传 [keyPacker] 支持其他类型。
     * @param <V> 支持[Boolean, Int, Long, Float, Double, String]类型。 可通过传 [valuePacker] 支持其他类型。
     */
    @SuppressWarnings("unchecked")
    public <K, V> PackEncoder putMap(
            int index,
            Map<K, V> map,
            Packer<K> keyPacker,
            Packer<V> valuePacker
    ) {
        if (map == null) return this;
        map.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        int size = map.size();
        if (size == 0) {
            wrapObjectArrayHeader(index, size);
        } else {
            Class<K> keyType = null;
            Class<V> valueType = null;
            Map.Entry<K, V> entry = map.entrySet().iterator().next();
            // 当Packer为null时，从element中获取类型。
            if (keyPacker == null) {
                final Class<K> type = (Class<K>) entry.getKey().getClass();
                if (!map.keySet().stream().allMatch(key -> key.getClass() == type)) {
                    throw new IllegalArgumentException("The key not support multiply types");
                }
                keyType = type;
            }
            if (valuePacker == null) {
                final Class<V> type = (Class<V>) entry.getValue().getClass();
                if (!map.values().stream().allMatch(value -> value.getClass() == type)) {
                    throw new IllegalArgumentException("The value not support multiply types");
                }
                valueType = type;
            }
            return putMap(index, map, keyType, valueType, keyPacker, valuePacker);
        }
        return this;
    }

    public <K, V> PackEncoder putMap(
            int index,
            Map<K, V> map,
            Class<K> keyType,
            Class<V> valueType,
            Packer<K> keyPacker,
            Packer<V> valuePacker
    ) {
        if (map == null) return this;
        int size = map.size();
        long tagValue = wrapObjectArrayHeader(index, size);
        if (tagValue < 0) return this;

        int mark = buffer.position;

        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (keyPacker != null) {
                wrapObject(entry.getKey(), keyPacker);
            } else if (keyType == String.class) {
                wrapString((String) entry.getKey());
            } else {
                Object key = entry.getKey();
                if (key == null) {
                    continue;
                }
                buffer.checkCapacity(8);
                if (keyType == Integer.class) {
                    buffer.writeInt((Integer) key);
                } else if (keyType == Long.class) {
                    buffer.writeLong((Long) key);
                } else {
                    if (PackConfig.ignoreUnknownType) {
                        // 不识别的类型，位置复原（当没有写入这个map）
                        buffer.position = mark;
                        return this;
                    }
                    String keyTypeName = (keyType == null) ? "null" : keyType.getSimpleName();
                    throw new IllegalArgumentException("Unsupported type of key: " + keyTypeName);
                }
            }

            if (valuePacker != null) {
                wrapObject(entry.getValue(), valuePacker);
            } else if (valueType == String.class) {
                wrapString((String) entry.getValue());
            } else {
                Object value = entry.getValue();
                if (value == null) {
                    throw new IllegalArgumentException("The values is null");
                }
                buffer.checkCapacity(8);
                if (valueType == Integer.class) {
                    buffer.writeInt((Integer) value);
                } else if (valueType == Long.class) {
                    buffer.writeLong((Long) value);
                } else if (valueType == Double.class) {
                    buffer.writeDouble((Double) value);
                } else if (valueType == Float.class) {
                    buffer.writeFloat((Float) value);
                } else if (valueType == Boolean.class) {
                    buffer.writeByte(((Boolean) value) ? ONE : ZERO);
                } else {
                    if (PackConfig.ignoreUnknownType) {
                        buffer.position = mark;
                        return this;
                    }
                    String valueTypeName = (valueType == null) ? "null" : valueType.getSimpleName();
                    throw new IllegalArgumentException("Unsupported type of value: " + valueTypeName);
                }
            }
        }
        putLen((int) (tagValue >>> 32), (int) tagValue);
        return this;
    }
}
