package io.packable

import kotlin.reflect.KClass

/**
 * PackEncoder
 *
 * 作用：将对象编码成字节数组
 */
class PackEncoder {
    companion object {
        private val EMPTY_ARRAY = ByteArray(0)

        fun <T> encode(target: T, adapter: TypeAdapter<T>): ByteArray {
            if (target == null) return ByteArray(0)
            val encoder = PackEncoder()
            adapter.encode(encoder, target)
            return encoder.toBytes()
        }

        fun encodeIntArray(value: IntArray): ByteArray {
            if (value.isEmpty()) return EMPTY_ARRAY
            val encoder = PackEncoder()
            val totalSize = EncodeBuffer.getVarInt32Size(value.size) + value.size * 4
            encoder.buffer.checkCapacity(totalSize)
            encoder.buffer.writeVarInt32(value.size)
            for (e in value) {
                encoder.buffer.writeInt(e)
            }
            return encoder.toBytes()
        }

        fun encodeLongArray(value: LongArray): ByteArray {
            if (value.isEmpty()) return EMPTY_ARRAY
            val encoder = PackEncoder()
            val totalSize = EncodeBuffer.getVarInt32Size(value.size) + value.size * 8
            encoder.buffer.checkCapacity(totalSize)
            encoder.buffer.writeVarInt32(value.size)
            for (e in value) {
                encoder.buffer.writeLong(e)
            }
            return encoder.toBytes()
        }

        fun encodeStringList(value: List<String>): ByteArray {
            if (value.isEmpty()) return EMPTY_ARRAY
            val encoder = PackEncoder()
            encoder.buffer.writeVarInt32(value.size)
            for (e: String in value) {
                encoder.wrapString(e)
            }
            return encoder.toBytes()
        }

        fun <T> encodeObjectList(value: List<T>, adapter: TypeAdapter<T>): ByteArray {
            if (value.isEmpty()) return EMPTY_ARRAY
            val encoder = PackEncoder()
            encoder.buffer.writeVarInt32(value.size)
            for (e: T in value) {
                encoder.wrapObject(e, adapter)
            }
            return encoder.toBytes()
        }
    }

    private val buffer = EncodeBuffer(ByteArrayPool.acquire())
    private var recycled: Boolean = false

    /**
     * 获取编码的字节数组
     *
     * 当调用此方法后，[buffer] 会被回收，请勿再次调用
     */
    fun toBytes(): ByteArray {
        checkBufferState()
        val bytes = buffer.hb.copyOf(buffer.position)
        recycle()
        return bytes
    }

    private fun recycle() {
        checkBufferState()
        ByteArrayPool.recycle(buffer.hb)
        recycled = true
    }

    private fun checkBufferState() {
        if (recycled) {
            throw IllegalStateException("Encoder had been recycled")
        }
    }

    private fun putIndex(index: Int) {
        if (index < 0 || index > 0xFF) {
            throw IndexOutOfBoundsException("index:$index out of bound")
        }
        if (index >= TagFormat.LITTLE_INDEX_BOUND) {
            buffer.writeByte(TagFormat.BIG_INDEX_MASK.toByte())
        }
        buffer.writeByte((index).toByte())
    }

    fun putBoolean(index: Int, value: Boolean): PackEncoder {
        return putByte(index, if (value) 1 else 0)
    }

    fun putByte(index: Int, value: Byte): PackEncoder {
        buffer.checkCapacity(3)
        if (value.toInt() == 0) {
            putIndex(index)
        } else {
            if (index < TagFormat.LITTLE_INDEX_BOUND) {
                buffer.writeByte((index or TagFormat.TYPE_NUM_8).toByte())
            } else {
                buffer.writeByte((TagFormat.BIG_INDEX_MASK or TagFormat.TYPE_NUM_8).toByte())
                buffer.writeByte(index.toByte())
            }
            buffer.writeByte(value)
        }
        return this
    }

    fun putShort(index: Int, value: Short): PackEncoder {
        buffer.checkCapacity(4)
        if (value.toInt() == 0) {
            putIndex(index)
        } else {
            val pos: Int = buffer.position
            putIndex(index)
            if ((value.toInt() shr 8) == 0) {
                (buffer.hb[pos].toInt() or TagFormat.TYPE_NUM_8).toByte().also { buffer.hb[pos] = it }
                buffer.writeByte(value.toByte())
            } else {
                buffer.hb[pos] = (buffer.hb[pos].toInt() or TagFormat.TYPE_NUM_16).toByte()
                buffer.writeShort(value)
            }
        }
        return this
    }

    fun putInt(index: Int, value: Int): PackEncoder {
        buffer.checkCapacity(6)
        if (value == 0) {
            putIndex(index)
        } else {
            val pos: Int = buffer.position
            putIndex(index)
            if ((value shr 8) == 0) {
                buffer.hb[pos] = (buffer.hb[pos].toInt() or TagFormat.TYPE_NUM_8).toByte()
                buffer.writeByte(value.toByte())
            } else if ((value shr 16) == 0) {
                buffer.hb[pos] = (buffer.hb[pos].toInt() or TagFormat.TYPE_NUM_16).toByte()
                buffer.writeShort(value.toShort())
            } else {
                buffer.hb[pos] = (buffer.hb[pos].toInt() or TagFormat.TYPE_NUM_32).toByte()
                buffer.writeInt(value)
            }
        }
        return this
    }

    fun putLong(index: Int, value: Long): PackEncoder {
        buffer.checkCapacity(10)
        if (value == 0L) {
            putIndex(index)
        } else {
            val pos: Int = buffer.position
            putIndex(index)
            if ((value shr 32) != 0L) {
                buffer.hb[pos] = (buffer.hb[pos].toInt() or TagFormat.TYPE_NUM_64).toByte()
                buffer.writeLong(value)
            } else if ((value shr 8) == 0L) {
                buffer.hb[pos] = (buffer.hb[pos].toInt() or TagFormat.TYPE_NUM_8).toByte()
                buffer.writeByte(value.toByte())
            } else if ((value shr 16) == 0L) {
                buffer.hb[pos] = (buffer.hb[pos].toInt() or TagFormat.TYPE_NUM_16).toByte()
                buffer.writeShort(value.toShort())
            } else {
                buffer.hb[pos] = (buffer.hb[pos].toInt() or TagFormat.TYPE_NUM_32).toByte()
                buffer.writeInt(value.toInt())
            }
        }
        return this
    }

    fun putFloat(index: Int, value: Float): PackEncoder {
        buffer.checkCapacity(6)
        if (value == 0f) {
            putIndex(index)
        } else {
            if (index < TagFormat.LITTLE_INDEX_BOUND) {
                buffer.writeByte((index or TagFormat.TYPE_NUM_32).toByte())
            } else {
                buffer.writeByte((TagFormat.BIG_INDEX_MASK or TagFormat.TYPE_NUM_32).toByte())
                buffer.writeByte(index.toByte())
            }
            buffer.writeFloat(value)
        }
        return this
    }

    fun putDouble(index: Int, value: Double): PackEncoder {
        buffer.checkCapacity(10)
        if (value == 0.0) {
            putIndex(index)
        } else {
            if (index < TagFormat.LITTLE_INDEX_BOUND) {
                buffer.writeByte((index or TagFormat.TYPE_NUM_64).toByte())
            } else {
                buffer.writeByte((TagFormat.BIG_INDEX_MASK or TagFormat.TYPE_NUM_64).toByte())
                buffer.writeByte(index.toByte())
            }
            buffer.writeDouble(value)
        }
        return this
    }

    fun putString(index: Int, value: String?): PackEncoder {
        if (value == null) {
            return this
        }
        if (value.isEmpty()) {
            buffer.checkCapacity(2)
            putIndex(index)
        } else {
            val bytes: ByteArray = value.encodeToByteArray()
            wrapTagAndLength(index, bytes.size)
            buffer.writeBytes(bytes)
        }
        return this
    }


    fun <T> putObject(index: Int, value: T?, adapter: TypeAdapter<T>): PackEncoder {
        if (value == null) {
            return this
        }
        buffer.checkCapacity(6)
        val pTag: Int = buffer.position
        putIndex(index)
        // 预留 4 字节来存放长度，如果最终用不了那么多，则回退
        buffer.position += 4
        val pValue: Int = buffer.position
        adapter.encode(this, value)
        if (pValue == buffer.position) {
            buffer.position -= 4
        } else {
            putLen(pTag, pValue)
        }
        return this
    }

    fun <T> putWithConvertor(index: Int, value: T?, convertor: IntConvertor<T>): PackEncoder {
        if (value != null) {
            putInt(index, convertor.toInt(value))
        }
        return this
    }

    fun <T> putWithConvertor(index: Int, value: T?, convertor: LongConvertor<T>): PackEncoder {
        if (value != null) {
            putLong(index, convertor.toLong(value))
        }
        return this
    }

    fun <T> putWithConvertor(index: Int, value: T?, convertor: StringConvertor<T>): PackEncoder {
        if (value != null) {
            putString(index, convertor.toString(value))
        }
        return this
    }

    fun <T> putWithConvertor(index: Int, value: T?, convertor: ByteArrayConvertor<T>): PackEncoder {
        if (value != null) {
            putByteArray(index, convertor.toByteArray(value))
        }
        return this
    }

    /**
     * 打包String。
     * 用于数组或者Map。
     * 与直接编码字段不同，数组中的对象需要自己管理长度，不能像字段那样借助 Tag 来指示长度有多少个字节。
     * 因此，需要能自行解析的编码。
     * 比如 String 这里就用的 varint 编码，因为数组中的字符串通常不会太长，用 varint 表示通常一到两个字节就足够了。
     */
    private fun wrapString(str: String?) {
        if (str == null) {
            buffer.checkCapacity(5)
            buffer.writeVarIntNegative1()
        } else if (str.isEmpty()) {
            buffer.checkCapacity(1)
            buffer.writeByte(0)
        } else {
            val bytes: ByteArray = str.encodeToByteArray()
            val len = bytes.size
            val sizeOfLen = EncodeBuffer.getVarInt32Size(len)
            buffer.checkCapacity(sizeOfLen + len)
            buffer.writeVarInt32(len)
            buffer.writeBytes(bytes)
        }
    }

    /**
     * 编码对象
     *
     * 此方法用于编码放入容器（Array, List, Map等）的对象。
     * [putObject] 编码对象，可以从 tag 中判断对象大小占用的空间；
     * 而 [wrapObject] 编码对象，需要自己判断。
     * 直接设定4个字节太浪费，用 varint 编码则难以预留空间（当前采用先编码后写长度的方式）。
     * 因此，这里我们借用 varint 编码的思想，但是单位长度用两个字节：
     * 1. 编码前先预留两个字节，编码后计算对象大小。
     * 2. 如果小于 0x7fff，则直接填写长度；否则，往后挪两个字节，再填写长度。
     * 3. 如果对象为空，则填充 0xffff。
     *
     * 由于预留两个字节，所以当对象占用空间少于 32768 时，不需要移动位置。
     * 编码 Object 和 String 的长度采用不同的方式，是综合权衡时间和空间的结果。
     */
    private fun <T> wrapObject(value: T?, adapter: TypeAdapter<T>) {
        buffer.checkCapacity(2)
        if (value == null) {
            buffer.writeShort(PackConfig.NULL_OBJECT_FLAG)
        } else {
            val pLen: Int = buffer.position
            buffer.position += 2
            val pObj: Int = buffer.position
            adapter.encode(this, value)
            val len: Int = buffer.position - pObj
            if (len <= 0x7fff) {
                buffer.writeShort(pLen, len.toShort())
            } else {
                buffer.checkCapacity(2)
                buffer.hb.copyInto(buffer.hb, pObj + 2, pObj, pObj + len)
                buffer.position += 2
                // 因为 MAX_BUFFER_SIZE = 1 << 30，所以长度不会大于等于 0x7fff0000。
                // 因此:
                // len 的最高位不会是 1，我们可以用最高位来标记长度是两个字节；
                // len 的高16bit也不会等于 0x7fff, 所以执行 “or 0x8000” 不会等于 0xffff (NULL_OBJECT_FLAG)
                buffer.writeShort(pLen, ((len ushr 16) or 0x8000).toShort())
                buffer.writeShort(pLen + 2, len.toShort())
            }
        }
    }

    private fun wrapObjectArrayHeader(index: Int, size: Int): Long {
        if (size > PackConfig.maxObjectArraySize) {
            throw IllegalStateException("object array size out of limit")
        }
        // Header最多占用11字节: 2 bytes index, 4 bytes len, 5 bytes size
        buffer.checkCapacity(11)
        val pTag: Long = buffer.position.toLong()
        putIndex(index)
        if (size <= 0) return -1
        // 预留 4 字节用于存放长度
        buffer.position += 4
        val pValue: Long = buffer.position.toLong()
        buffer.writeVarInt32(size)
        return (pTag shl 32) or pValue
    }

    fun putStringArray(index: Int, value: Array<String>?): PackEncoder {
        if (value == null) return this
        val tagValue: Long = wrapObjectArrayHeader(index, value.size)
        if (tagValue < 0) return this
        for (str: String in value) {
            wrapString(str)
        }
        putLen((tagValue ushr 32).toInt(), tagValue.toInt())
        return this
    }

    fun putNullableStringArray(index: Int, value: Array<String?>?): PackEncoder {
        if (value == null) return this
        val tagValue: Long = wrapObjectArrayHeader(index, value.size)
        if (tagValue < 0) return this
        for (str: String? in value) {
            wrapString(str)
        }
        putLen((tagValue ushr 32).toInt(), tagValue.toInt())
        return this
    }

    fun putStringList(index: Int, value: Collection<String>?): PackEncoder {
        putNullableStringList(index, value)
        return this
    }

    fun putNullableStringList(index: Int, value: Collection<String?>?): PackEncoder {
        if (value == null) return this
        val tagValue: Long = wrapObjectArrayHeader(index, value.size)
        if (tagValue < 0) return this
        for (str: String? in value) {
            wrapString(str)
        }
        putLen((tagValue ushr 32).toInt(), tagValue.toInt())
        return this
    }

    fun <T> putObjectArray(index: Int, value: Array<out T?>?, adapter: TypeAdapter<T>): PackEncoder {
        if (value == null) return this
        val tagValue: Long = wrapObjectArrayHeader(index, value.size)
        if (tagValue < 0) return this
        for (target: T? in value) {
            wrapObject(target, adapter)
        }
        putLen((tagValue ushr 32).toInt(), tagValue.toInt())
        return this
    }

    fun <T> putObjectList(index: Int, value: Collection<T?>?, adapter: TypeAdapter<T>): PackEncoder {
        if (value == null) return this
        val size: Int = value.size
        val tagValue: Long = wrapObjectArrayHeader(index, size)
        if (tagValue < 0) return this
        for (target: T? in value) {
            wrapObject(target, adapter)
        }
        putLen((tagValue ushr 32).toInt(), tagValue.toInt())
        return this
    }

    fun putBooleanArray(index: Int, value: BooleanArray?): PackEncoder {
        if (value == null) return this
        val n: Int = value.size
        if (n == 0) {
            wrapTagAndLength(index, 0)
            return this
        }
        if (n <= 5) {
            var b: Byte = (n shl 5).toByte()
            for (i in 0 until n) {
                if (value[i]) {
                    b = (b.toInt() or (1 shl i)).toByte()
                }
            }
            wrapTagAndLength(index, 1)
            buffer.writeByte(b)
        } else {
            val remain: Int = n and 0x7
            val byteCount: Int = (n shr 3) + (if (remain == 0) 1 else 2)
            wrapTagAndLength(index, byteCount)
            buffer.writeByte(remain.toByte())
            var i = 0
            while (i < n) {
                val end: Int = (i + 8).coerceAtMost(n)
                var b: Byte = 0
                for (j in i until end) {
                    if (value[j]) {
                        b = (b.toInt() or (1 shl (j and 0x7))).toByte()
                    }
                }
                buffer.writeByte(b)
                i = end
            }
        }
        return this
    }

    fun putByteArray(index: Int, value: ByteArray?): PackEncoder {
        if (value != null) {
            wrapTagAndLength(index, value.size)
            buffer.writeBytes(value)
        }
        return this
    }

    fun putShortArray(index: Int, value: ShortArray?): PackEncoder {
        if (value != null) {
            wrapTagAndLength(index, value.size shl 1)
            for (e: Short in value) {
                buffer.writeShort(e)
            }
        }
        return this
    }

    fun putCharArray(index: Int, value: CharArray?): PackEncoder {
        if (value != null) {
            wrapTagAndLength(index, value.size shl 1)
            for (e: Char in value) {
                buffer.writeShort(e.code.toShort())
            }
        }
        return this
    }

    fun putIntArray(index: Int, value: IntArray?): PackEncoder {
        if (value != null) {
            wrapTagAndLength(index, value.size shl 2)
            for (e: Int in value) {
                buffer.writeInt(e)
            }
        }
        return this
    }

    fun putIntList(index: Int, value: Collection<Int>): PackEncoder {
        val n: Int = getListSize(index, value)
        if (n <= 0) return this
        putIntArray(index, value.toIntArray())
        return this
    }

    fun putLongArray(index: Int, value: LongArray?): PackEncoder {
        if (value != null) {
            wrapTagAndLength(index, value.size shl 3)
            for (e: Long in value) {
                buffer.writeLong(e)
            }
        }
        return this
    }

    fun putLongList(index: Int, value: Collection<Long>): PackEncoder {
        val n: Int = getListSize(index, value)
        if (n <= 0) return this
        putLongArray(index, value.toLongArray())
        return this
    }

    fun putFloatArray(index: Int, value: FloatArray?): PackEncoder {
        if (value != null) {
            wrapTagAndLength(index, value.size shl 2)
            for (e: Float in value) {
                buffer.writeFloat(e)
            }
        }
        return this
    }

    fun putFloatList(index: Int, value: Collection<Float>): PackEncoder {
        val n: Int = getListSize(index, value)
        if (n <= 0) return this
        putFloatArray(index, value.toFloatArray())
        return this
    }

    fun putDoubleArray(index: Int, value: DoubleArray?): PackEncoder {
        if (value != null) {
            wrapTagAndLength(index, value.size shl 3)
            for (e: Double in value) {
                buffer.writeDouble(e)
            }
        }
        return this
    }

    fun putDoubleList(index: Int, value: Collection<Double>): PackEncoder {
        val n: Int = getListSize(index, value)
        if (n <= 0) return this
        putDoubleArray(index, value.toDoubleArray())
        return this
    }

    /**
     * 编码Map
     *
     * key：内置支持[Int, Long, String]类型，可通过传 [keyAdapter] 支持其他类型。
     * value: 内置支持[Boolean, Int, Long, Float, Double, String]类型，可通过传 [valueAdapter] 支持其他类型。
     *
     * @param index 字段编号
     * @param map 待编码的map
     * @param keyAdapter key的编码器
     * @param valueAdapter value编码器
     */
    inline fun <reified K, reified V> putMap(
        index: Int,
        map: Map<K, V>?,
        keyAdapter: TypeAdapter<K>? = null,
        valueAdapter: TypeAdapter<V>? = null
    ): PackEncoder {
        if (map != null) {
            packMap(index, map, K::class, V::class, keyAdapter, valueAdapter)
        }
        return this
    }

    /**
     * 作用和 [putMap] 一致，皆为编码Map。
     * 因为 [putMap] 是 inline 方法，要求其调用的方法对外部可见(public)，故而 public 此方法。
     * 一般而言调用 [putMap] 接口，更方便一些。
     */
    fun <K, V> packMap(
        index: Int,
        map: Map<out K?, V?>,
        keyType: KClass<*>?,
        valueType: KClass<*>?,
        keyAdapter: TypeAdapter<K>?,
        valueAdapter: TypeAdapter<V>?
    ) {
        val mark = buffer.position
        val tagValue: Long = wrapObjectArrayHeader(index, map.size)
        if (tagValue < 0) return
        for (entry: Map.Entry<K?, V?> in map.entries) {
            buffer.checkCapacity(8)
            if (keyAdapter != null) {
                wrapObject(entry.key, keyAdapter)
            } else if (keyType == String::class) {
                wrapString(entry.key as String?)
            } else if (keyType == Int::class) {
                buffer.writeInt(entry.key as Int)
            } else if (keyType == Long::class) {
                buffer.writeLong(entry.key as Long)
            } else {
                if (PackConfig.ignoreUnknownType) {
                    // 不识别的类型，位置复原（当没有写入这个map）
                    buffer.position = mark
                    return
                }
                throw IllegalArgumentException("Unsupported type of key: ${keyType?.simpleName}")
            }

            buffer.checkCapacity(8)
            if (valueAdapter != null) {
                wrapObject(entry.value, valueAdapter)
            } else if (valueType == String::class) {
                wrapString(entry.value as String?)
            } else if (valueType == Int::class) {
                buffer.writeInt(entry.value as Int)
            } else if (valueType == Long::class) {
                buffer.writeLong(entry.value as Long)
            } else if (valueType == Double::class) {
                buffer.writeDouble(entry.value as Double)
            } else if (valueType == Float::class) {
                buffer.writeFloat(entry.value as Float)
            } else if (valueType == Boolean::class) {
                buffer.writeByte(if (entry.value as Boolean) 1 else 0)
            } else {
                if (PackConfig.ignoreUnknownType) {
                    buffer.position = mark
                    return
                }
                throw IllegalArgumentException("Unsupported type of value: ${valueType?.simpleName}")
            }
        }
        putLen((tagValue ushr 32).toInt(), tagValue.toInt())
    }

    private fun wrapTagAndLength(index: Int, len: Int) {
        buffer.checkCapacity(6 + len)
        if (len == 0) {
            putIndex(index)
        } else {
            val pos: Int = buffer.position
            putIndex(index)
            if (len <= 0xff) {
                buffer.hb[pos] = (buffer.hb[pos].toInt() or TagFormat.TYPE_VAR_8).toByte()
                buffer.writeByte(len.toByte())
            } else if (len <= 0xffff) {
                buffer.hb[pos] = (buffer.hb[pos].toInt() or TagFormat.TYPE_VAR_16).toByte()
                buffer.writeShort(len.toShort())
            } else {
                buffer.hb[pos] = (buffer.hb[pos].toInt() or TagFormat.TYPE_VAR_32).toByte()
                buffer.writeInt(len)
            }
        }
    }

    private fun getListSize(index: Int, value: Collection<*>?): Int {
        if (value == null) {
            return 0
        }
        if (value.isEmpty()) {
            wrapTagAndLength(index, 0)
            return 0
        }
        return value.size
    }

    /**
     * 填充长度
     *
     * 长度本可设定 1 字节，2 字节，4 字节三种，
     * 但是拷贝数组太多会影响性能，
     * 所以这里我们仅当长度少于等于 128 时作回退处理，否则就直接再预留的4个字节处填写长度。
     */
    private fun putLen(pTag: Int, pValue: Int) {
        val len: Int = buffer.position - pValue
        if (len <= 128) {
            buffer.hb[pTag] = (buffer.hb[pTag].toInt() or TagFormat.TYPE_VAR_8).toByte()
            buffer.hb[pValue - 4] = len.toByte()
            buffer.hb.copyInto(buffer.hb, pValue - 3, pValue, pValue + len)
            buffer.position -= 3
        } else {
            buffer.hb[pTag] = (buffer.hb[pTag].toInt() or TagFormat.TYPE_VAR_32).toByte()
            buffer.writeInt(pValue - 4, len)
        }
    }
}
