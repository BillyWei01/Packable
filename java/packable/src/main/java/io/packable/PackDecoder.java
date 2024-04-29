package io.packable;

import java.util.*;

/**
 * PackDecoder
 * 作用：将byte数组解码为目标对象。
 * 如果要解码的数组是不完整的或者被损坏的，则解码过程可能会抛出异常，
 * 所以解码时最好留意数据源的可靠性，如果数据源不可靠，请注意 catch 异常。
 */
public final class PackDecoder {
    private static final long NULL_FLAG = ~0L;
    private static final long INT_MASK = 0xffffffffL;
    private static final int DECODER_POOL_CAPACITY = 8;

    /**
     * 解析子对象时（包括递归调用或者解析对象数组）需要子 Decoder;
     * 为了避免频繁创建对象，实现了这个 Decoder 池。
     */
    private static class DecoderPool {
        private PackDecoder[] decoderArray;
        private int count = 0;
        private char[] charBuffer;

        final byte[] bytes;

        DecoderPool(byte[] bytes) {
            this.bytes = bytes;
        }

        private PackDecoder getDecoder(int offset, int len) {
            if (count > 0) {
                PackDecoder decoder = decoderArray[--count];
                decoderArray[count] = null;
                decoder.buffer.position = offset;
                decoder.buffer.limit = offset + len;
                decoder.maxIndex = -1;
                return decoder;
            }
            // 对象池为空，则创建新 Decoder
            return new PackDecoder(new DecodeBuffer(bytes, offset, len), this);
        }

        private void recycleDecoder(PackDecoder decoder) {
            if (count >= DECODER_POOL_CAPACITY) {
                LongArrayPool.recycleArray(decoder.infoArray);
                decoder.infoArray = null;
                decoder.recycled = true;
                decoder.maxIndex = -1;
                return;
            }
            if (decoderArray == null) {
                decoderArray = new PackDecoder[DECODER_POOL_CAPACITY];
            }
            decoderArray[count++] = decoder;
        }

        private void release() {
            for (int i = 0; i < count; i++) {
                PackDecoder decoder = decoderArray[i];
                LongArrayPool.recycleArray(decoder.infoArray);
                decoderArray[i] = null;
            }
            decoderArray = null;
        }
    }

    private final DecoderPool pool;
    private final DecodeBuffer buffer;

    // 记录各字段的value，或者位置和长度
    private long[] infoArray;

    // 标记 infoArray 最大的有效下标
    private int maxIndex = -1;

    // 标记是否已回收
    private boolean recycled = false;



    public PackDecoder(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    public PackDecoder(byte[] bytes, int offset, int len) {
        this(new DecodeBuffer(bytes, offset, len), new DecoderPool(bytes));

        if (bytes == null) {
            throw new IllegalArgumentException("bytes is null");
        }
        if (bytes.length > PackConfig.MAX_BUFFER_SIZE) {
            throw new IllegalArgumentException("buffer size over limit");
        }
        if (offset + len > bytes.length) {
            throw new IllegalArgumentException("out of range, " +
                    "size:" + bytes.length + " offset:" + offset + " length:" + len);
        }
    }

    private PackDecoder(DecodeBuffer buffer, DecoderPool pool) {
        this.buffer = buffer;
        this.pool = pool;
    }

    public static <T> T decode(byte[] bytes, Packer<T> packer) {
        return decode(bytes, 0, bytes.length, packer);
    }

    public static <T> T decode(byte[] bytes, int offset, int len, Packer<T> packer) {
        PackDecoder decoder = new PackDecoder(bytes, offset, len);
        T t = packer.unpack(decoder);
        decoder.recycle();
        return t;
    }

    public static int[] decodeIntArray(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return new int[0];
        DecodeBuffer buffer = new DecodeBuffer(bytes, 0, bytes.length);
        int size = buffer.readVarInt32();
        return wrapIntArray(buffer, size);
    }

    public static long[] decodeLongArray(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return new long[0];
        DecodeBuffer buffer = new DecodeBuffer(bytes, 0, bytes.length);
        int size = buffer.readVarInt32();
        return wrapLongArray(buffer, size);
    }

    public static  List<String> decodeStringList(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            return new ArrayList<>();
        PackDecoder decoder = new PackDecoder(bytes);
        DecodeBuffer buffer = decoder.buffer;
        int size = buffer.readVarInt32();
        List<String> value = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            value.add(decoder.takeString());
        }
        decoder.recycle();
        return value;
    }

    public static <T> List<T> decodeObjectList(byte[] bytes, Packer<T> packer) {
        if (bytes == null || bytes.length == 0)
            return new ArrayList<>();
        PackDecoder decoder = new PackDecoder(bytes);
        DecodeBuffer buffer = decoder.buffer;
        int size = buffer.readVarInt32();
        List<T> value = new ArrayList<T>(size);
        for (int i = 0; i < size; i++) {
            value.add(decoder.takeObject(packer));
        }
        decoder.recycle();
        return value;
    }


    private static int[] wrapIntArray(DecodeBuffer buffer , int n) {
        int[] value = new int[n];
        for (int i = 0; i < n; i++) {
            value[i] = buffer.readInt();
        }
        return value;
    }

    private static long[] wrapLongArray(DecodeBuffer buffer, int n) {
        long[] value = new long[n];
        for (int i = 0; i < n; i++) {
            value[i] = buffer.readLong();
        }
        return value;
    }

    private void checkState() {
        if (recycled) {
            throw new IllegalStateException("Decoder had been recycled");
        }
    }

    private void recycle() {
        checkState();
        CharArrayPool.recycleArray(pool.charBuffer);
        LongArrayPool.recycleArray(this.infoArray);
        pool.release();
        maxIndex = -1;
        recycled = true;
    }

    private void parseBuffer() {
        long existFlag = 0;
        long[] existFlags = null;

        if (infoArray == null) {
            infoArray = LongArrayPool.getDefaultArray();
        }

        while (buffer.hasRemaining()) {
            byte tag = buffer.readByte();
            int index = (tag & TagFormat.BIG_INDEX_MASK) == 0 ? tag & TagFormat.INDEX_MASK : buffer.readByte() & 0xff;
            if (index > maxIndex) {
                maxIndex = index;
            }
            if (index < 64) {
                existFlag |= 1L << index;
            } else {
                if (existFlags == null) {
                    existFlags = new long[4];
                }
                existFlags[index >> 6] |= 1L << (index & 0x3f);
            }

            if (index >= infoArray.length) {
                long[] oldArray = infoArray;
                infoArray = LongArrayPool.getArray(index + 1);
                System.arraycopy(oldArray, 0, infoArray, 0, oldArray.length);
                LongArrayPool.recycleArray(oldArray);
            }

            byte type = (byte) (tag & TagFormat.TYPE_MASK);
            if (type <= TagFormat.TYPE_NUM_64) {
                if (type == TagFormat.TYPE_0) {
                    infoArray[index] = 0L;
                } else if (type == TagFormat.TYPE_NUM_8) {
                    infoArray[index] = ((long) buffer.readByte()) & 0xffL;
                } else if (type == TagFormat.TYPE_NUM_16) {
                    infoArray[index] = ((long) buffer.readShort()) & 0xffffL;
                } else if (type == TagFormat.TYPE_NUM_32) {
                    infoArray[index] = ((long) buffer.readInt()) & 0xffffffffL;
                } else {
                    // 我们用 long[] 来装载 number 的，value，用 NULL_FLAG 来标记位于index的位置不存在value。
                    // 为了区分 infoArray[index] 保存的是 NULL_FLAG 还是 value == NULL_FLAG,
                    // 我们用最高的 1 bit 来标识 infoArray[index] 所保存是 “value” 还是 “value的位置”
                    // 1. 最高bit为0，则 infoArray[index] 保存的是 value
                    // 2. 最高bit为1, 则 infoArray[index] 保存的是 value 的位置

                    // 当前用的时小端编码，高8位在 number 的最后一个字节
                    byte b8 = buffer.hb[buffer.position + 7];
                    if ((b8 & TagFormat.BIG_INDEX_MASK) == 0) {
                        infoArray[index] = buffer.readLong();
                    } else {
                        infoArray[index] = buffer.position | Long.MIN_VALUE;
                        buffer.position += 8;
                    }
                }
            } else {
                int size;
                if (type == TagFormat.TYPE_VAR_8) {
                    size = buffer.readByte() & 0xff;
                } else if (type == TagFormat.TYPE_VAR_16) {
                    size = buffer.readShort() & 0xffff;
                } else {
                    size = buffer.readInt();
                }
                infoArray[index] = ((long) buffer.position << 32) | (long) size;
                buffer.position += size;
            }
        }

        if (buffer.position != buffer.limit) {
            throw new IllegalArgumentException("invalid pack data");
        }

        if (maxIndex <= 0)
            return;

        if (maxIndex <= 63) {
            int bits = 63 - maxIndex;
            long flippedFlag = (~existFlag) << bits;
            if (flippedFlag == 0) {
                return;
            }
            flippedFlag >>>= bits;
            int i = 0;
            do {
                if ((flippedFlag & 1) != 0) {
                    infoArray[i] = NULL_FLAG;
                }
                i++;
                flippedFlag >>>= 1;
            } while (flippedFlag != 0);
        } else {
            for (int i = 0; i < 64; i++) {
                if ((existFlag & 1) == 0) {
                    infoArray[i] = NULL_FLAG;
                }
                existFlag >>>= 1;
            }
            if (existFlags != null) {
                for (int i = 64; i < maxIndex; i++) {
                    if ((existFlags[i >> 6] & (1L << (i & 0x3F))) == 0) {
                        infoArray[i] = NULL_FLAG;
                    }
                }
            }
        }
    }

    private char[] getCharBuffer(int len) {
        if (pool.charBuffer == null) {
            int size = Math.max(len, CharArrayPool.CHAR_BUFFER_SIZE);
            pool.charBuffer = CharArrayPool.getArray(size);
        } else if (pool.charBuffer.length < len) {
            CharArrayPool.recycleArray(pool.charBuffer);
            pool.charBuffer = CharArrayPool.getArray(len);
        }
        return pool.charBuffer;
    }

    private long getInfo(int index) {
        checkState();
        if (maxIndex < 0) {
            parseBuffer();
        }
        if (index > maxIndex) {
            return NULL_FLAG;
        }
        return infoArray[index];
    }

    public boolean contains(int index) {
        return getInfo(index) != NULL_FLAG;
    }

    public boolean getBoolean(int index, boolean defValue) {
        long info = getInfo(index);
        return info == NULL_FLAG ? defValue : info == 1;
    }

    public boolean getBoolean(int index) {
        return getInfo(index) == 1;
    }

    public byte getByte(int index, byte defValue) {
        long info = getInfo(index);
        return info == NULL_FLAG ? defValue : (byte) info;
    }

    public byte getByte(int index) {
        return getByte(index, (byte) 0);
    }

    public short getShort(int index, short defValue) {
        long info = getInfo(index);
        return info == NULL_FLAG ? defValue : (short) info;
    }

    public short getShort(int index) {
        return getShort(index, (short) 0);
    }

    public int getInt(int index, int defValue) {
        long info = getInfo(index);
        return info == NULL_FLAG ? defValue : (int) info;
    }

    public int getInt(int index) {
        long info = getInfo(index);
        return info == NULL_FLAG ? 0 : (int) info;
    }

    public long getLong(int index, long defValue) {
        long info = getInfo(index);
        if (info == NULL_FLAG) {
            return defValue;
        }
        return info >= 0 ? info : buffer.readLong((int) (info & INT_MASK));
    }

    public long getLong(int index) {
        return getLong(index, 0L);
    }

    public float getFloat(int index, float defValue) {
        long info = getInfo(index);
        return info == NULL_FLAG ? defValue : Float.intBitsToFloat((int) info);
    }

    public float getFloat(int index) {
        return getFloat(index, 0f);
    }

    public double getDouble(int index, double defValue) {
        long info = getInfo(index);
        if (info == NULL_FLAG) {
            return defValue;
        }
        long x = info >= 0 ? info : buffer.readLong((int) (info & INT_MASK));
        return Double.longBitsToDouble(x);
    }

    public double getDouble(int index) {
        return getDouble(index, 0D);
    }

    public String getString(int index, String defValue) {
        long info = getInfo(index);
        if (info == NULL_FLAG) {
            return defValue;
        }
        int len = (int) (info & INT_MASK);
        if (len == 0) {
            return "";
        }
        int offset = (int) (info >>> 32);
        return decodeStr(offset, len);
    }

    public String getNotNullString(int index) {
        return getString(index, "");
    }

    public String getString(int index) {
        return getString(index, null);
    }

    /**
     * Decode utf8 bytes to char array(uft16).
     * Transfer char array to String constructor, JDK will just call
     * 'this.value = Arrays.copyOfRange(value, offset, offset+count);',
     * in this way we could avoid allocating char array buffer, comparing with 'new String(byte[])'.
     * And we could reuse char array buffer by {@link DecoderPool#charBuffer} and {@link CharArrayPool}
     */
    private String decodeStr(int offset, int len) {
        char[] buf = getCharBuffer(len);
        byte[] src = buffer.hb;
        int i = offset;
        int j = 0;
        int limit = offset + len;
        while (i < limit) {
            byte b1 = src[i++];
            if (b1 > 0) {
                buf[j++] = (char) b1;
            } else if (b1 < (byte) 0xE0) {
                byte b2 = src[i++];
                buf[j++] = (char) (((b1 & 0x1F) << 6) | (b2 & 0x3F));
            } else if (b1 < (byte) 0xF0) {
                byte b2 = src[i++];
                byte b3 = src[i++];
                buf[j++] = (char) (((b1 & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F));
            } else {
                byte b2 = src[i++];
                byte b3 = src[i++];
                byte b4 = src[i++];
                int cp = ((b1 & 0x07) << 18) | ((b2 & 0x3F) << 12) | ((b3 & 0x3F) << 6) | (b4 & 0x3F);
                /*
                 high = (char)(MIN_HIGH_SURROGATE  + (codePoint - MIN_SUPPLEMENTARY_CODE_POINT) >>> 10))
                      = (char)((MIN_HIGH_SURROGATE - (MIN_SUPPLEMENTARY_CODE_POINT >>> 10)) + (codePoint >>> 10))
                      = (char) (0xD7C0 + (cp >>> 10))

                 low  = (char) (MIN_LOW_SURROGATE + (codePoint & 0x3ff))
                      = (char) (0xDC00 + (cp & 0x3FF))
                 */
                buf[j++] = (char) (0xD7C0 + (cp >>> 10));
                buf[j++] = (char) (0xDC00 + (cp & 0x3FF));
            }
        }
        if (i > limit) {
            throw new IllegalArgumentException("Invalid String");
        }
        return new String(buf, 0, j);
    }

    public <T> T getObject(int index, Packer<T> packer) {
        return getObject(index, packer, null);
    }

    public <T> T getObject(int index, Packer<T> packer, T defValue) {
        long info = getInfo(index);
        if (info == NULL_FLAG) {
            return defValue;
        }
        int offset = (int) (info >>> 32);
        int len = (int) (info & INT_MASK);
        PackDecoder decoder = pool.getDecoder(offset, len);
        T object = packer.unpack(decoder);
        pool.recycleDecoder(decoder);
        return object;
    }

    public boolean[] getBooleanArray(int index) {
        long info = getInfo(index);
        if (info == PackDecoder.NULL_FLAG) {
            return null;
        }
        int len = (int) (info & PackDecoder.INT_MASK);
        if (len == 0) {
            return new boolean[0];
        }

        buffer.position = (int) (info >>> 32);
        boolean[] a;
        if (len == 1) {
            byte b = buffer.readByte();
            int n = (b & 0xFF) >>> 5;
            a = new boolean[n];
            for (int i = 0; i < n; i++) {
                a[i] = (b & 0x1) != 0;
                b >>= 1;
            }
        } else {
            int remain = buffer.readByte();
            if ((remain >> 3) != 0) {
                throw new IllegalStateException("remain overflow");
            }
            int byteCount = len - 1;
            int n = (byteCount << 3) - (remain > 0 ? 8 - remain : 0);
            a = new boolean[n];
            for (int i = 0; i < n; i += 8) {
                int b = buffer.readByte() & 0xFF;
                int j = i;
                while (b != 0) {
                    a[j++] = (b & 0x1) != 0;
                    b >>= 1;
                }
            }
        }
        return a;
    }

    public byte[] getByteArray(int index) {
        long info = getInfo(index);
        if (info == NULL_FLAG) {
            return null;
        }
        buffer.position = (int) (info >>> 32);
        int len = (int) (info & INT_MASK);
        byte[] bytes = new byte[len];
        buffer.readBytes(bytes);
        return bytes;
    }

    private int setPosAndGetLen(long info, int mask) {
        buffer.position = (int) (info >>> 32);
        int len = (int) (info & INT_MASK);
        if ((len & mask) != 0) {
            throw new IllegalStateException("invalid array length");
        }
        return len;
    }

    public int[] getIntArray(int index) {
        long info = getInfo(index);
        if (info == NULL_FLAG) {
            return null;
        }
        int n = setPosAndGetLen(info, 0x3) >> 2;
        return wrapIntArray(buffer, n);
    }

    public long[] getLongArray(int index) {
        long info = getInfo(index);
        if (info == NULL_FLAG) {
            return null;
        }
        int n = setPosAndGetLen(info, 0x7) >> 3;
        return wrapLongArray(buffer, n);
    }

    public float[] getFloatArray(int index) {
        long info = getInfo(index);
        if (info == NULL_FLAG) {
            return null;
        }
        int n = setPosAndGetLen(info, 0x3) >> 2;
        float[] value = new float[n];
        for (int i = 0; i < n; i++) {
            value[i] = buffer.readFloat();
        }
        return value;
    }

    public double[] getDoubleArray(int index) {
        long info = getInfo(index);
        if (info == NULL_FLAG) {
            return null;
        }
        int n = setPosAndGetLen(info, 0x7) >> 3;
        double[] value = new double[n];
        for (int i = 0; i < n; i++) {
            value[i] = buffer.readDouble();
        }
        return value;
    }

    public String[] getStringArray(int index) {
        int n = getSize(index);
        if (n < 0) return null;
        String[] value = new String[n];
        for (int i = 0; i < n; i++) {
            value[i] = takeString();
        }
        return value;
    }

    String takeString() {
        int len = buffer.readVarInt32();
        if (len < 0) {
            return null;
        }
        int offset = buffer.position;
        buffer.checkBound(offset, len);
        if (len == 0) {
            return "";
        }
        String str = decodeStr(offset, len);
        buffer.position += len;
        return str;
    }

    /**
     * 获取对象数组
     *
     * @param index 编哈
     * @param packer 解码器
     * @param array 可以传一个空数组，因为 List 转 Array 需要类型信息。
     */
    public <T> T[] getObjectArray(int index, Packer<T> packer, T[] array) {
        List<T> list = getObjectList(index, packer);
        return (list == null) ? null : list.toArray(array);
    }

    <T> T takeObject(Packer<T> packer) {
        short a = buffer.readShort();
        if (a == PackConfig.NULL_OBJECT_FLAG) {
            return null;
        } else {
            int len = a >= 0 ? a : ((a & 0x7fff) << 16) | (buffer.readShort() & 0xffff);
            int offset = buffer.position;
            buffer.checkBound(offset, len);
            PackDecoder decoder = pool.getDecoder(offset, len);
            T t = packer.unpack(decoder);
            pool.recycleDecoder(decoder);
            buffer.position += len;
            return t;
        }
    }

    public List<Integer> getIntList(int index) {
        int[] a = getIntArray(index);
        if (a == null) return null;
        int n = a.length;
        Integer[] b = new Integer[n];
        for (int i = 0; i < n; i++) {
            b[i] = a[i];
        }
        return Arrays.asList(b);
    }

    public List<Long> getLongList(int index) {
        long[] a = getLongArray(index);
        if (a == null) return null;
        int n = a.length;
        Long[] b = new Long[n];
        for (int i = 0; i < n; i++) {
            b[i] = a[i];
        }
        return Arrays.asList(b);
    }

    public List<Float> getFloatList(int index) {
        float[] a = getFloatArray(index);
        if (a == null) return null;
        int n = a.length;
        Float[] b = new Float[n];
        for (int i = 0; i < n; i++) {
            b[i] = a[i];
        }
        return Arrays.asList(b);
    }

    public List<Double> getDoubleList(int index) {
        double[] a = getDoubleArray(index);
        if (a == null) return null;
        int n = a.length;
        Double[] b = new Double[n];
        for (int i = 0; i < n; i++) {
            b[i] = a[i];
        }
        return Arrays.asList(b);
    }

    public <T> List<T> getObjectList(int index, Packer<T> packer) {
        int n = getSize(index);
        if (n < 0) return null;
        List<T> value = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            value.add(takeObject(packer));
        }
        return value;
    }

    public List<String> getStringList(int index) {
        String[] a = getStringArray(index);
        return a == null ? null : Arrays.asList(a);
    }

    int getSize(int index) {
        long info = getInfo(index);
        if (info == NULL_FLAG) {
            return -1;
        }
        int len = (int) (info & INT_MASK);
        if (len == 0) {
            return 0;
        }
        buffer.position = (int) (info >>> 32);
        int n = buffer.readVarInt32();
        if (n < 0 || n > PackConfig.maxObjectArraySize) {
            throw new IllegalStateException("invalid size of object array");
        }
        return n;
    }

    /**
     * getMap 不能像putMap那样根据map中的元素获取类型，所以只能手动传入了。
     */
    public <K, V> Map<K, V> getMap(int index, Class<K> keyType, Class<V> valueType) {
        return getMap(index, keyType, valueType, null, null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public <K, V> Map<K, V> getMap(
            int index,
            Class<K> keyType,
            Class<V> valueType,
            Packer<K> keyPacker,
            Packer<V> valuePacker
    ) {
        int n = getSize(index);
        if (n < 0) return null;

        // HashMap's DEFAULT_LOAD_FACTOR = 0.75f
        // so set initialCapacity to be n/0.75+1 (n*4/3+1) could make HashMap not to extend capacity.
        int initCapacity = (n << 2) / 3 + 1;
        Map map = new HashMap<K, V>(initCapacity);
        for (int i = 0; i < n; i++) {
            Object key;
            if (keyPacker != null) {
                key = takeObject(keyPacker);
            } else if (keyType == String.class) {
                key = takeString();
            } else if (keyType == Integer.class) {
                key = buffer.readInt();
            } else if (keyType == Long.class) {
                key = buffer.readLong();
            } else {
                if (PackConfig.ignoreUnknownType) {
                    return null;
                }
                String keyTypeName = (keyType == null) ? "null" : keyType.getSimpleName();
                throw new IllegalArgumentException("Unsupported type of key: " + keyTypeName);
            }

            Object value;
            if (valuePacker != null) {
                value = takeObject(valuePacker);
            } else if (valueType == String.class) {
                value = takeString();
            } else if (valueType == Integer.class) {
                value = buffer.readInt();
            } else if (valueType == Long.class) {
                value = buffer.readLong();
            } else if (valueType == Double.class) {
                value = buffer.readDouble();
            } else if (valueType == Float.class) {
                value = buffer.readFloat();
            } else if (valueType == Boolean.class) {
                value = buffer.readByte() == 1;
            } else {
                if (PackConfig.ignoreUnknownType) {
                    return null;
                }
                String valueTypeName = (valueType == null) ? "null" : valueType.getSimpleName();
                throw new IllegalArgumentException("Unsupported type of value: " + valueTypeName);
            }
            map.put(key, value);
        }
        return map;
    }
}

