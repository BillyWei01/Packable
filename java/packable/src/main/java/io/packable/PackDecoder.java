package io.packable;


import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PackDecoder, to decode bytes data to target object.
 * <p>
 * After getting all values,
 * it's recommended to call {@link #recycle()} to recycle objects.
 */
public final class PackDecoder {
    static final long NULL_FLAG = ~0;
    static final long INT_MASK = 0xffffffffL;
    static final int DECODER_POOL_CAPACITY = 8;

    private static class DecoderPool {
        private PackDecoder[] decoderArray;
        private int count = 0;
        private char[] charBuffer;

        // root decoder buffer
        final DecodeBuffer buffer;

        DecoderPool(DecodeBuffer buffer) {
            this.buffer = buffer;
        }

        private PackDecoder getDecoder(int offset, int len) {
            PackDecoder decoder;
            if (count > 0) {
                decoder = decoderArray[--count];
                decoder.buffer.position = offset;
                decoder.buffer.limit = offset + len;
                decoder.maxIndex = -1;
            } else {
                decoder = new PackDecoder();
                decoder.buffer = new DecodeBuffer(buffer.hb, offset, len);
                decoder.pool = this;
            }
            return decoder;
        }

        private void recycleDecoder(PackDecoder decoder) {
            if (count >= DECODER_POOL_CAPACITY) {
                LongArrayPool.recycleArray(decoder.infoArray);
                decoder.infoArray = null;
                return;
            }
            if (decoderArray == null) {
                decoderArray = new PackDecoder[DECODER_POOL_CAPACITY];
            }
            decoderArray[count++] = decoder;
        }

        private void releaseDecoders() {
            for (int i = 0; i < count; i++) {
                PackDecoder decoder = decoderArray[i];
                LongArrayPool.recycleArray(decoder.infoArray);
                decoder.pool = null;
            }
            decoderArray = null;
        }
    }

    private DecoderPool pool;
    private DecodeBuffer buffer;
    private long[] infoArray;
    private int maxIndex = -1;

    public static <T> T unmarshal(byte[] bytes, PackCreator<T> creator) {
        return unmarshal(bytes, 0, bytes.length, creator);
    }

    public static <T> T unmarshal(byte[] bytes, int offset, int len, PackCreator<T> creator) {
        PackDecoder decoder = newInstance(bytes, offset, len);
        T t = creator.decode(decoder);
        decoder.recycle();
        return t;
    }

    public static PackDecoder newInstance(byte[] bytes) {
        return newInstance(bytes, 0, bytes.length);
    }

    public static PackDecoder newInstance(byte[] bytes, int offset, int len) {
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

        PackDecoder decoder = new PackDecoder();
        decoder.buffer = new DecodeBuffer(bytes, offset, len);
        decoder.pool = new DecoderPool(decoder.buffer);
        return decoder;
    }

    private PackDecoder() {
    }

    DecodeBuffer getBuffer() {
        return buffer;
    }

    public void recycle() {
        if (pool == null) {
            return;
        }
        if (this.buffer == pool.buffer) {
            // current decoder is root decoder
            CharArrayPool.recycleArray(pool.charBuffer);
            LongArrayPool.recycleArray(this.infoArray);
            pool.releaseDecoders();
            pool = null;
            buffer = null;
            maxIndex = -1;
        } else {
            pool.recycleDecoder(this);
        }
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
                existFlags[index >> 8] |= 1L << (index & 0x3f);
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
                    // In case of not able to tell value missing (which infoArray[index] = NULL_FLAG) or value = NULL_FLAG,
                    // We use the highest bit of long value to indicate that the infoArray[index]
                    // is actually value (when positive) or position of value (mask the highest bit to be one 1)

                    // little end, the high 8 bits at the last byte
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

        // should be equal
        if (buffer.position != buffer.limit) {
            throw new IllegalArgumentException("invalid pack data");
        }

        if (maxIndex <= 0)
            return;

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

        if (existFlags != null) {
            for (i = 64; i < maxIndex; i++) {
                if ((existFlags[i >> 8] & (1L << (i & 0x3F))) == 0) {
                    infoArray[i] = NULL_FLAG;
                }
            }
        }
    }

    private char[] getCharBuffer(int len) {
        if (pool.charBuffer == null) {
            int size = Math.max(len, PackConfig.CHAR_BUFFER_SIZE);
            pool.charBuffer = CharArrayPool.getArray(size);
        } else if (pool.charBuffer.length < len) {
            CharArrayPool.recycleArray(pool.charBuffer);
            pool.charBuffer = CharArrayPool.getArray(len);
        }
        return pool.charBuffer;
    }

    long getInfo(int index) {
        if (maxIndex < 0) {
            if (buffer == null) {
                throw new IllegalStateException("Decoder had been recycled");
            }
            parseBuffer();
        }
        if (index > maxIndex) {
            return NULL_FLAG;
        }
        return infoArray[index];
    }

    /**
     * Get custom data.
     *
     * This method does not supply too much info(like data length),
     * so it's only use for fixed format data.
     *
     * @param index index of data
     * @return DecodeBuffer to read data.
     * If data is empty, call {@link DecodeBuffer#hasRemaining()} will return false;
     * otherwise DecodeBuffer's position will locate at the right offset.
     * Return null if data not exist.
     * @see PackEncoder#putCustom(int, int)
     */
    public DecodeBuffer getCustom(int index) {
        long info = getInfo(index);
        if (info == NULL_FLAG) {
            return null;
        }
        int len = (int) (info & INT_MASK);
        buffer.position = (len == 0) ? buffer.limit : (int) (info >>> 32);
        return buffer;
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
     *
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
                buf[j++] = (char) (0xD7C0 + (cp >>> 10));
                buf[j++] = (char) (0xDC00 + (cp & 0x3FF));
            }
        }
        if (i > limit) {
            throw new IllegalArgumentException("Invalid String");
        }
        return new String(buf, 0, j);
    }

    public <T> T getPackable(int index, PackCreator<T> creator) {
        return getPackable(index, creator, null);
    }

    public <T> T getPackable(int index, PackCreator<T> creator, T defValue) {
        long info = getInfo(index);
        if (info == NULL_FLAG) {
            return defValue;
        }
        int offset = (int) (info >>> 32);
        int len = (int) (info & INT_MASK);
        PackDecoder decoder = pool.getDecoder(offset, len);
        T object = creator.decode(decoder);
        decoder.recycle();
        return object;
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
        int[] value = new int[n];
        for (int i = 0; i < n; i++) {
            value[i] = buffer.readInt();
        }
        return value;
    }

    public long[] getLongArray(int index) {
        long info = getInfo(index);
        if (info == NULL_FLAG) {
            return null;
        }
        int n = setPosAndGetLen(info, 0x7) >> 3;
        long[] value = new long[n];
        for (int i = 0; i < n; i++) {
            value[i] = buffer.readLong();
        }
        return value;
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
        int len = buffer.readVarint32();
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

    public <T> T[] getPackableArray(int index, PackArrayCreator<T> creator) {
        int n = getSize(index);
        if (n < 0) return null;
        T[] value = creator.newArray(n);
        for (int i = 0; i < n; i++) {
            value[i] = takePackable(creator);
        }
        return value;
    }

    <T> T takePackable(PackCreator<T> creator) {
        T t;
        short a = buffer.readShort();
        if (a == PackConfig.NULL_PACKABLE) {
            t = null;
        } else {
            int len = a >= 0 ? a : ((a & 0x7fff) << 16) | (buffer.readShort() & 0xffff);
            int offset = buffer.position;
            buffer.checkBound(offset, len);
            PackDecoder decoder = pool.getDecoder(offset, len);
            t = creator.decode(decoder);
            decoder.recycle();
            buffer.position += len;
        }
        return t;
    }

    /**
     * To reuse memory, it's highly recommended calling {@link PackDecoder#recycle()} after reading data.
     * @param index index of value.
     * @return the PackDecoder.
     */
    public PackDecoder getDecoder(int index) {
        long info = getInfo(index);
        if (info == NULL_FLAG) {
            return null;
        }
        int offset = (int) (info >>> 32);
        int len = (int) (info & INT_MASK);
        return pool.getDecoder(offset, len);
    }

    public final class DecoderArray {
        private final int count;
        private int index = 0;
        private PackDecoder decoder = null;

        private DecoderArray(int n) {
            count = n;
        }

        public int getCount() {
            return count;
        }

        public boolean hasNext() {
            if (index < count) {
                return true;
            } else {
                if (decoder != null) {
                    decoder.recycle();
                }
                return false;
            }
        }

        public PackDecoder next() {
            if (index >= count) {
                return null;
            }
            index++;
            short a = buffer.readShort();
            if (a == PackConfig.NULL_PACKABLE) {
                return null;
            } else {
                int len = a >= 0 ? a : ((a & 0x7fff) << 16) | (buffer.readShort() & 0xffff);
                int offset = buffer.position;
                buffer.checkBound(offset, len);
                if (decoder == null) {
                    decoder = pool.getDecoder(offset, len);
                } else {
                    decoder.buffer.position = offset;
                    decoder.buffer.limit = offset + len;
                    decoder.maxIndex = -1;
                }
                buffer.position += len;
                return decoder;
            }
        }
    }

    public DecoderArray getDecoderArray(int index) {
        int n = getSize(index);
        if (n < 0) return null;
        return new DecoderArray(n);
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

    public <T> List<T> getPackableList(int index, PackArrayCreator<T> creator) {
        T[] a = getPackableArray(index, creator);
        return a == null ? null : Arrays.asList(a);
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
        int n = buffer.readVarint32();
        if (n < 0 || n > PackConfig.MAX_OBJECT_ARRAY_SIZE) {
            throw new IllegalStateException("invalid size of object array");
        }
        return n;
    }

    private static int getInitCapacity(int n) {
        // HashMap's DEFAULT_LOAD_FACTOR = 0.75f
        // so set initialCapacity to be n/0.75+1 (n*4/3+1) could make HashMap not to extend capacity.
        return (n << 2) / 3 + 1;
    }

    public Map<String, String> getStr2Str(int index) {
        int n = getSize(index);
        if (n < 0) return null;
        Map<String, String> map = new HashMap<>(getInitCapacity(n));
        for (int i = 0; i < n; i++) {
            map.put(takeString(), takeString());
        }
        return map;
    }

    public <T> Map<String, T> getStr2Pack(int index, PackCreator<T> creator) {
        int n = getSize(index);
        if (n < 0) return null;
        Map<String, T> map = new HashMap<>(getInitCapacity(n));
        for (int i = 0; i < n; i++) {
            map.put(takeString(), takePackable(creator));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Integer> getStr2Int(int index) {
        return getStr2Number(index, NumberType.INT);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Long> getStr2Long(int index) {
        return getStr2Number(index, NumberType.LONG);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Double> getStr2Double(int index) {
        return getStr2Number(index, NumberType.DOUBLE);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Float> getStr2Float(int index) {
        return getStr2Number(index, NumberType.FLOAT);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map getStr2Number(int index, int type) {
        int n = getSize(index);
        if (n < 0) return null;
        Map m = new HashMap<String, Object>(getInitCapacity(n));
        if (type == NumberType.INT) {
            for (int i = 0; i < n; i++) {
                m.put(takeString(), buffer.readInt());
            }
        } else if (type == NumberType.LONG) {
            for (int i = 0; i < n; i++) {
                m.put(takeString(), buffer.readLong());
            }
        } else if (type == NumberType.FLOAT) {
            for (int i = 0; i < n; i++) {
                m.put(takeString(), buffer.readFloat());
            }
        } else if (type == NumberType.DOUBLE) {
            for (int i = 0; i < n; i++) {
                m.put(takeString(), buffer.readDouble());
            }
        } else {
            throw new IllegalArgumentException("wrong type:" + type);
        }
        return m;
    }

    public Map<Integer, Integer> getInt2Int(int index) {
        int n = getSize(index);
        if (n < 0) return null;
        Map<Integer, Integer> map = new HashMap<>(getInitCapacity(n));
        for (int i = 0; i < n; i++) {
            map.put(buffer.readInt(), buffer.readInt());
        }
        return map;
    }

    public Map<Integer, String> getInt2Str(int index) {
        int n = getSize(index);
        if (n < 0) return null;
        Map<Integer, String> map = new HashMap<>(getInitCapacity(n));
        for (int i = 0; i < n; i++) {
            map.put(buffer.readInt(), takeString());
        }
        return map;
    }

    public boolean[] getBooleanArray(int index) {
        return CompactCoder.getBooleanArray(this, index);
    }

    public int[] getEnumArray(int index) {
        return CompactCoder.getEnumArray(this, index);
    }

    public int[] getCompactIntArray(int index) {
        return CompactCoder.getIntArray(this, index);
    }

    public long[] getCompactLongArray(int index) {
        return CompactCoder.getLongArray(this, index);
    }

    public double[] getCompactDoubleArray(int index) {
        return CompactCoder.getDoubleArray(this, index);
    }
}
