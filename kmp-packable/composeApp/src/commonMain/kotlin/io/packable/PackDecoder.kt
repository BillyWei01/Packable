package io.packable

import kotlin.reflect.KClass

/**
 * PackDecoder
 *
 * 作用：将byte数组解码为目标对象。
 *
 * 如果要解码的数组是不完整的或者被损坏的，则解码过程可能会抛出异常，
 * 所以解码时最好留意数据源的可靠性，如果数据源不可靠，请注意 catch 异常。
 */
class PackDecoder private constructor(
    private val buffer: DecodeBuffer,
    private val pool: DecoderPool
) {
    companion object {
        private const val NULL_FLAG: Long = 0L.inv()
        private const val INT_MASK: Long = 0xffffffffL
        private const val POOL_CAPACITY: Int = 8

        fun <T> decode(bytes: ByteArray, adapter: TypeAdapter<T>): T {
            return adapter.decode(PackDecoder(bytes))
        }

        fun decodeIntArray(bytes: ByteArray): IntArray {
            if (bytes.isEmpty()) return IntArray(0)
            val buffer = DecodeBuffer(bytes, 0, bytes.size)
            val size = buffer.readVarInt32()
            return wrapIntArray(buffer, size)
        }

        fun decodeLongArray(bytes: ByteArray): LongArray {
            if (bytes.isEmpty()) return LongArray(0)
            val buffer = DecodeBuffer(bytes, 0, bytes.size)
            val size = buffer.readVarInt32()
            return wrapLongArray(buffer, size)
        }

        fun decodeStringList(bytes: ByteArray): List<String> {
            if (bytes.isEmpty()) return emptyList()
            val decoder = PackDecoder(bytes)
            val buffer = decoder.buffer
            val size = buffer.readVarInt32()
            val value = mutableListOf<String>()
            for (i in 0 until size) {
                decoder.takeString()?.let {
                    value.add(it)
                }
            }
            return value
        }

        fun <T> decodeList(bytes: ByteArray, adapter: TypeAdapter<T>): List<T> {
            if (bytes.isEmpty()) return emptyList()
            val decoder = PackDecoder(bytes)
            val buffer = decoder.buffer
            val size = buffer.readVarInt32()
            val value = mutableListOf<T>()
            for (i in 0 until size) {
                decoder.takeObject(adapter)?.let {
                    value.add(it)
                }
            }
            return value
        }

        private fun wrapIntArray(buffer: DecodeBuffer, n: Int): IntArray {
            val value = IntArray(n)
            for (i in 0 until n) {
                value[i] = buffer.readInt()
            }
            return value
        }

        private fun wrapLongArray(buffer: DecodeBuffer, n: Int): LongArray {
            val value = LongArray(n)
            for (i in 0 until n) {
                value[i] = buffer.readLong()
            }
            return value
        }
    }

    constructor(bytes: ByteArray) : this(bytes, 0, bytes.size)

    constructor(bytes: ByteArray, offset: Int, len: Int) :
            this(DecodeBuffer(bytes, offset, len), DecoderPool(bytes)) {
        if (bytes.size > PackConfig.MAX_BUFFER_SIZE) {
            throw IllegalArgumentException("buffer size over limit")
        }
        if (offset + len > bytes.size) {
            throw IllegalArgumentException("out of range, size:${bytes.size} offset:$offset length:$len")
        }
    }

    // 记录各字段的value，或者位置和长度
    private var infoArray = LongArray(32)

    // 标记 infoArray 最大的有效下标
    private var maxIndex: Int = -1

    /**
     * 解析子对象时（包括递归调用或者解析对象数组）需要子 Decoder;
     * 为了避免频繁创建对象，实现了这个 Decoder 池。
     */
    private class DecoderPool(val bytes: ByteArray) {
        private var decoderArray: Array<PackDecoder?>? = null
        private var count: Int = 0

        fun getDecoder(offset: Int, len: Int): PackDecoder {
            val array = decoderArray
            if (count > 0 && array != null) {
                array[--count]?.let { decoder ->
                    array[count] = null
                    decoder.buffer.position = offset
                    decoder.buffer.limit = offset + len
                    decoder.maxIndex = -1
                    return decoder
                }
            }
            // 对象池为空，则创建新 Decoder
            return PackDecoder(DecodeBuffer(bytes, offset, len), this)
        }

        fun recycle(decoder: PackDecoder) {
            // 如果 decoderArray 没满容量，则将 Decoder 放入池中，以便再用
            // 否则，回收其infoArray，并标记 recycled 为true
            if (count < POOL_CAPACITY) {
                val array = decoderArray ?: arrayOfNulls<PackDecoder>(POOL_CAPACITY).also {
                    decoderArray = it
                }
                array[count++] = decoder
            }
        }
    }

    private fun getInfoArraySize(expected: Int): Int {
        if (expected <= 64) return 64
        return if (expected <= 128) 128 else 256
    }

    private fun parseBuffer() {
        var existFlag: Long = 0
        var existFlags: LongArray? = null

        while (buffer.hasRemaining()) {
            val tag: Int = buffer.readByte().toInt()
            val index: Int =
                if ((tag and TagFormat.BIG_INDEX_MASK) == 0)
                    tag and TagFormat.INDEX_MASK
                else
                    buffer.readByte().toInt() and 0xff
            if (index > maxIndex) {
                maxIndex = index
            }
            if (index < 64) {
                existFlag = existFlag or (1L shl index)
            } else {
                if (existFlags == null) {
                    existFlags = LongArray(4)
                }
                val idx = index shr 6
                existFlags[idx] = existFlags[idx] or (1L shl (index and 0x3f))
            }

            if (index >= infoArray.size) {
                val oldArray = infoArray
                infoArray = LongArray(getInfoArraySize(index + 1))
                oldArray.copyInto(infoArray, 0, 0, oldArray.size)
            }

            val type: Int = tag and TagFormat.TYPE_MASK
            if (type <= TagFormat.TYPE_NUM_64) {
                if (type == TagFormat.TYPE_0) {
                    infoArray[index] = 0L
                } else if (type == TagFormat.TYPE_NUM_8) {
                    infoArray[index] = (buffer.readByte().toLong()) and 0xffL
                } else if (type == TagFormat.TYPE_NUM_16) {
                    infoArray[index] = (buffer.readShort().toLong()) and 0xffffL
                } else if (type == TagFormat.TYPE_NUM_32) {
                    infoArray[index] = (buffer.readInt().toLong()) and 0xffffffffL
                } else {
                    // 我们用 long[] 来装载 number 的，value，用 NULL_FLAG 来标记位于index的位置不存在value。
                    // 为了区分 infoArray[index] 保存的是 NULL_FLAG 还是 value == NULL_FLAG,
                    // 我们用最高的 1 bit 来标识 infoArray[index] 所保存是 “value” 还是 “value的位置”
                    // 1. 最高bit为0，则 infoArray[index] 保存的是 value
                    // 2. 最高bit为1, 则 infoArray[index] 保存的是 value 的位置

                    // 当前用的时小端编码，高8位在 number 的最后一个字节
                    val b8: Byte = buffer.hb[buffer.position + 7]
                    if ((b8.toInt() and TagFormat.BIG_INDEX_MASK) == 0) {
                        infoArray[index] = buffer.readLong()
                    } else {
                        infoArray[index] = (buffer.position.toLong() or Long.MIN_VALUE)
                        buffer.position += 8
                    }
                }
            } else {
                val size: Int = when (type) {
                    TagFormat.TYPE_VAR_8 -> {
                        buffer.readByte().toInt() and 0xff
                    }

                    TagFormat.TYPE_VAR_16 -> {
                        buffer.readShort().toInt() and 0xffff
                    }

                    else -> {
                        buffer.readInt()
                    }
                }
                infoArray[index] = (buffer.position.toLong() shl 32) or size.toLong()
                buffer.position += size
            }
        }

        if (buffer.position != buffer.limit) {
            throw IllegalArgumentException("invalid packed data")
        }

        if (maxIndex <= 0) return

        // 如果 maxIndex > 0, 则需要标记 [0, maxIndex) 区间内不存在value的位置
        if (maxIndex <= 63) {
            val bits = 63 - maxIndex
            var flippedFlag = existFlag.inv() shl bits
            // 如果 [0, maxIndex] 全都有值，则 flippedFlag 会等于 0
            // 举例:
            // 假设 maxIndex = 7, 则 bit = 56;
            // 如果 existFlag = 0xFF， 则 flippedFlag = (～0xFF) >> 56 = 0
            // 对于 [0, maxIndex] 全都有值的情况，不需要作额外的检查。
            if (flippedFlag == 0L) {
                return
            }
            flippedFlag = flippedFlag ushr bits
            var i = 0
            do {
                if (flippedFlag and 1L != 0L) {
                    infoArray[i] = NULL_FLAG
                }
                i++
                flippedFlag = flippedFlag ushr 1
            } while (flippedFlag != 0L)
        } else {
            for (i in 0..63) {
                if (existFlag and 1L == 0L) {
                    infoArray[i] = NULL_FLAG
                }
                existFlag = existFlag ushr 1
            }
            if (existFlags != null) {
                for (i in 64 until maxIndex) {
                    if (existFlags[i shr 6] and (1L shl (i and 0x3F)) == 0L) {
                        infoArray[i] = NULL_FLAG
                    }
                }
            }
        }
    }

    private fun getInfo(index: Int): Long {
        if (maxIndex < 0) {
            parseBuffer()
        }
        if (index > maxIndex) {
            return NULL_FLAG
        }
        return infoArray[index]
    }

    fun contains(index: Int): Boolean {
        return getInfo(index) != NULL_FLAG
    }

    fun getBoolean(index: Int, defValue: Boolean = false): Boolean {
        val info: Long = getInfo(index)
        return if (info == NULL_FLAG) defValue else info == 1L
    }

    fun getNullableBoolean(index: Int): Boolean? {
        val info: Long = getInfo(index)
        return if (info == NULL_FLAG) null else info == 1L
    }

    fun getByte(index: Int, defValue: Byte = 0.toByte()): Byte {
        val info: Long = getInfo(index)
        return if (info == NULL_FLAG) defValue else info.toByte()
    }

    fun getNullableByte(index: Int): Byte? {
        val info: Long = getInfo(index)
        return if (info == NULL_FLAG) null else info.toByte()
    }

    fun getShort(index: Int, defValue: Short = 0.toShort()): Short {
        val info: Long = getInfo(index)
        return if (info == NULL_FLAG) defValue else info.toShort()
    }

    fun getNullableShort(index: Int): Short? {
        val info: Long = getInfo(index)
        return if (info == NULL_FLAG) null else info.toShort()
    }

    fun getInt(index: Int, defValue: Int = 0): Int {
        val info: Long = getInfo(index)
        return if (info == NULL_FLAG) defValue else info.toInt()
    }

    fun getNullableInt(index: Int): Int? {
        val info: Long = getInfo(index)
        return if (info == NULL_FLAG) null else info.toInt()
    }

    fun getLong(index: Int, defValue: Long = 0L): Long {
        val info: Long = getInfo(index)
        if (info == NULL_FLAG) {
            return defValue
        }
        return if (info >= 0) info else buffer.readLong((info and INT_MASK).toInt())
    }

    fun getNullableLong(index: Int): Long? {
        val info: Long = getInfo(index)
        if (info == NULL_FLAG) {
            return null
        }
        return if (info >= 0) info else buffer.readLong((info and INT_MASK).toInt())
    }

    fun getFloat(index: Int, defValue: Float = 0f): Float {
        val info: Long = getInfo(index)
        return if (info == NULL_FLAG) defValue else Float.fromBits(info.toInt())
    }

    fun getNullableFloat(index: Int): Float? {
        val info: Long = getInfo(index)
        return if (info == NULL_FLAG) null else Float.fromBits(info.toInt())
    }

    fun getDouble(index: Int, defValue: Double = 0.0): Double {
        return getNullableDouble(index) ?: defValue
    }

    fun getNullableDouble(index: Int): Double? {
        val info: Long = getInfo(index)
        if (info == NULL_FLAG) {
            return null
        }
        val x: Long = if (info >= 0) info else buffer.readLong((info and INT_MASK).toInt())
        return Double.fromBits(x)
    }

    fun getString(index: Int, defValue: String = ""): String {
        return getNullableString(index) ?: defValue
    }

    fun getNullableString(index: Int): String? {
        val info: Long = getInfo(index)
        if (info == NULL_FLAG) {
            return null
        }
        val len: Int = (info and INT_MASK).toInt()
        if (len == 0) {
            return ""
        }
        val offset: Int = (info ushr 32).toInt()
        // Kotlin Multiplatform 的 String 没有基于 ByteArray 的构造函数
        // 所以不能使用 String(buffer.hb, offset, len)
        return buffer.hb.decodeToString(offset, offset + len)
    }

    fun <T> getObject(index: Int, adapter: TypeAdapter<out T?>): T? {
        val info: Long = getInfo(index)
        if (info == NULL_FLAG) {
            return null
        }
        val offset = (info ushr 32).toInt()
        val len = (info and INT_MASK).toInt()
        val decoder = pool.getDecoder(offset, len)
        val obj = adapter.decode(decoder)
        pool.recycle(decoder)
        return obj
    }

    fun <T> getByConvertor(index: Int, convertor: IntConvertor<T>): T? {
        val value = getNullableInt(index) ?: return null
        return convertor.toType(value)
    }

    fun <T> getByConvertor(index: Int, convertor: LongConvertor<T>): T? {
        val value = getNullableLong(index) ?: return null
        return convertor.toType(value)
    }

    fun <T> getByConvertor(index: Int, convertor: StringConvertor<T>): T? {
        val value = getNullableString(index) ?: return null
        return convertor.toType(value)
    }

    fun <T> getByConvertor(index: Int, convertor: ByteArrayConvertor<T>): T? {
        val value = getByteArray(index) ?: return null
        return convertor.toType(value)
    }

    fun getBooleanArray(index: Int): BooleanArray? {
        val info: Long = getInfo(index)
        if (info == NULL_FLAG) {
            return null
        }
        val len: Int = (info and INT_MASK).toInt()
        if (len == 0) {
            return BooleanArray(0)
        }
        buffer.position = (info ushr 32).toInt()
        val a: BooleanArray
        if (len == 1) {
            var b: Byte = buffer.readByte()
            val n: Int = (b.toInt() and 0xFF) ushr 5
            a = BooleanArray(n)
            for (i in 0 until n) {
                a[i] = (b.toInt() and 0x1) != 0
                b = (b.toInt() shr 1).toByte()
            }
        } else {
            val remain: Int = buffer.readByte().toInt()
            if ((remain shr 3) != 0) {
                throw IllegalStateException("remain overflow")
            }
            val byteCount: Int = len - 1
            val n: Int = (byteCount shl 3) - (if (remain > 0) 8 - remain else 0)
            a = BooleanArray(n)
            var i = 0
            while (i < n) {
                var b: Int = buffer.readByte().toInt() and 0xFF
                var j: Int = i
                while (b != 0) {
                    a[j++] = (b and 0x1) != 0
                    b = b shr 1
                }
                i += 8
            }
        }
        return a
    }

    fun getByteArray(index: Int): ByteArray? {
        val info: Long = getInfo(index)
        if (info == NULL_FLAG) {
            return null
        }
        buffer.position = (info ushr 32).toInt()
        val len: Int = (info and INT_MASK).toInt()
        val bytes = ByteArray(len)
        buffer.readBytes(bytes)
        return bytes
    }

    // 将位置定位到数组的起点，并返回数组的占用空间
    private fun setPosAndGetLen(info: Long, mask: Int): Int {
        buffer.position = (info ushr 32).toInt()
        val len: Int = (info and INT_MASK).toInt()
        // 检查数组的字节长度是否合法
        // len & 7 等价于 len % 8，Long 和 Double 长度为8字节，余数为0说明长度合法。
        // 同理，len & 3 等价 len % 4, 可检测 Int 和 Float
        if ((len and mask) != 0) {
            throw IllegalStateException("invalid array length")
        }
        return len
    }

    fun getCharArray(index: Int): CharArray? {
        val info: Long = getInfo(index)
        if (info == NULL_FLAG) {
            return null
        }
        // 通过占用空间除以元素大小，得到数组长度
        val n: Int = setPosAndGetLen(info, 0x1) shr 1
        val value = CharArray(n)
        for (i in 0 until n) {
            value[i] = buffer.readShort().toInt().toChar()
        }
        return value
    }

    fun getShortArray(index: Int): ShortArray? {
        val info: Long = getInfo(index)
        if (info == NULL_FLAG) {
            return null
        }
        // 通过占用空间除以元素大小，得到数组长度
        val n: Int = setPosAndGetLen(info, 0x1) shr 1
        val value = ShortArray(n)
        for (i in 0 until n) {
            value[i] = buffer.readShort()
        }
        return value
    }

    fun getIntArray(index: Int): IntArray? {
        val info: Long = getInfo(index)
        if (info == NULL_FLAG) {
            return null
        }
        // 通过占用空间除以元素大小，得到数组长度
        val n: Int = setPosAndGetLen(info, 0x3) shr 2
        return wrapIntArray(buffer, n)
    }

    fun getIntList(index: Int): List<Int>? {
        return getIntArray(index)?.toList()
    }

    fun getLongArray(index: Int): LongArray? {
        val info: Long = getInfo(index)
        if (info == NULL_FLAG) {
            return null
        }
        val n: Int = setPosAndGetLen(info, 0x7) shr 3
        return wrapLongArray(buffer, n)
    }

    fun getLongList(index: Int): List<Long>? {
        return getLongArray(index)?.toList()
    }

    fun getFloatArray(index: Int): FloatArray? {
        val info: Long = getInfo(index)
        if (info == NULL_FLAG) {
            return null
        }
        val n: Int = setPosAndGetLen(info, 0x3) shr 2
        val value = FloatArray(n)
        for (i in 0 until n) {
            value[i] = buffer.readFloat()
        }
        return value
    }

    fun getFloatList(index: Int): List<Float>? {
        return getFloatArray(index)?.toList()
    }

    fun getDoubleArray(index: Int): DoubleArray? {
        val info: Long = getInfo(index)
        if (info == NULL_FLAG) {
            return null
        }
        val n: Int = setPosAndGetLen(info, 0x7) shr 3
        val value = DoubleArray(n)
        for (i in 0 until n) {
            value[i] = buffer.readDouble()
        }
        return value
    }

    fun getDoubleList(index: Int): List<Double>? {
        return getDoubleArray(index)?.toList()
    }

    fun getStringArray(index: Int): Array<String>? {
        val array = getNullableStringArray(index) ?: return null
        return if (array.all { it != null }) {
            @Suppress("UNCHECKED_CAST")
            array as Array<String>
        } else {
            array.mapNotNull { it }.toTypedArray()
        }
    }

    fun getNullableStringArray(index: Int): Array<String?>? {
        val n: Int = getSize(index)
        if (n < 0) return null
        val value: Array<String?> = arrayOfNulls(n)
        for (i in 0 until n) {
            value[i] = takeString()
        }
        return value
    }

    fun getStringList(index: Int): List<String>? {
        return getStringArray(index)?.toList()
    }

    fun getNullableStringList(index: Int): List<String?>? {
        return getNullableStringArray(index)?.toList()
    }

    inline fun <reified T> getObjectArray(index: Int, adapter: TypeAdapter<T>): Array<T>? {
        val array = getNullableObjectArray(index, adapter) ?: return null
        return if (array.all { it != null }) {
            @Suppress("UNCHECKED_CAST")
            array as Array<T>
        } else {
            array.mapNotNull { it }.toTypedArray()
        }
    }

    inline fun <reified T> getNullableObjectArray(index: Int, adapter: TypeAdapter<T>): Array<T?>? {
        return getNullableObjectArray(index, adapter) { arrayOfNulls(it) }
    }

    inline fun <reified T> getObjectList(index: Int, adapter: TypeAdapter<T>): List<T>? {
        return getObjectArray(index, adapter)?.toList()
    }

    inline fun <reified T> getNullableObjectList(index: Int, adapter: TypeAdapter<T>): List<T?>? {
        return getNullableObjectArray(index, adapter)?.toList()
    }

    fun <T> getNullableObjectArray(
        index: Int,
        adapter: TypeAdapter<T>,
        createArray: (n: Int) -> Array<T?>
    ): Array<T?>? {
        val n: Int = getSize(index)
        if (n < 0) return null
        val value: Array<T?> = createArray(n)
        for (i in 0 until n) {
            value[i] = takeObject(adapter)
        }
        return value
    }

    /**
     * 获取Map
     *
     * @param index 字段编号
     * @param keyAdapter key的编码器
     * @param valueAdapter value编码器
     * @param acceptNullValue 是否接收null对象（默认false)。
     * 虽然通过inline可以获取到K和V的类型，但是由于可空类型和非可空类型是由相同的 Class 对象表示（Class 对象不包含可空性信息）。
     * 为了避免调用者范型传了非空类型，但是Map还是放入null对象，所以我们默认过滤null对象。
     * 如果调用者明确要获取包含null对象的key-value, 可以通过传 acceptNullValue = true 来实现。
     */
    inline fun <reified K, reified V> getMap(
        index: Int,
        keyAdapter: TypeAdapter<K>? = null,
        valueAdapter: TypeAdapter<V>? = null,
        acceptNullValue: Boolean = false
    ): Map<K, V>? {
        return unpackMap(index, K::class, V::class, keyAdapter, valueAdapter, acceptNullValue)
    }

    @Suppress("UNCHECKED_CAST")
    fun <K, V> unpackMap(
        index: Int,
        keyType: KClass<*>?,
        valueType: KClass<*>?,
        keyAdapter: TypeAdapter<K>?,
        valueAdapter: TypeAdapter<V>?,
        acceptNullValue: Boolean
    ): Map<K, V>? {
        val n: Int = getSize(index)
        if (n < 0) return null
        val map = mutableMapOf<K, V>()
        for (i in 0 until n) {
            val key = if (keyAdapter != null) {
                takeObject(keyAdapter) as K
            } else if (keyType == String::class) {
                takeString() as K
            } else if (keyType == Int::class) {
                buffer.readInt() as K
            } else if (keyType == Long::class) {
                buffer.readLong() as K
            } else {
                if (PackConfig.ignoreUnknownType) {
                    return null
                }
                throw IllegalArgumentException("Unsupported type of key: ${keyType?.simpleName}")
            }

            val value = if (valueAdapter != null) {
                takeObject(valueAdapter) as V
            } else if (valueType == String::class) {
                takeString() as V
            } else if (valueType == Int::class) {
                buffer.readInt() as V
            } else if (valueType == Long::class) {
                buffer.readLong() as V
            } else if (valueType == Double::class) {
                buffer.readDouble() as V
            } else if (valueType == Float::class) {
                buffer.readFloat() as V
            } else if (valueType == Boolean::class) {
                (buffer.readByte().toInt() == 1) as V
            } else {
                if (PackConfig.ignoreUnknownType) {
                    return null
                }
                throw IllegalArgumentException("Unsupported type of value: ${valueType?.simpleName}")
            }
            if (acceptNullValue || (key != null && value != null)) {
                map[key] = value
            }
        }
        return map
    }

    private fun takeString(): String? {
        val len: Int = buffer.readVarInt32()
        if (len < 0) {
            return null
        }
        if (len == 0) {
            return ""
        }
        val offset: Int = buffer.position
        buffer.checkBound(offset, len)
        buffer.position += len
        return buffer.hb.decodeToString(offset, offset + len)
    }

    private fun <T> takeObject(adapter: TypeAdapter<T>): T? {
        val t: T?
        val a: Short = buffer.readShort()
        if (a == PackConfig.NULL_OBJECT_FLAG) {
            t = null
        } else {
            val len =
                if (a >= 0) a.toInt()
                else ((a.toInt() and 0x7fff) shl 16) or (buffer.readShort().toInt() and 0xffff)
            val offset: Int = buffer.position
            buffer.checkBound(offset, len)
            val decoder = pool.getDecoder(offset, len)
            t = adapter.decode(decoder)
            pool.recycle(decoder)
            buffer.position += len
        }
        return t
    }

    /**
     * 定位到对应的位置，并返回对象相关信息。
     *
     * @return -1: null对象；
     *          0: 空对象（比如空字符串，空数组等）
     *          else: 对象大小（字节）
     */
    private fun getSize(index: Int): Int {
        val info: Long = getInfo(index)
        if (info == NULL_FLAG) {
            return -1
        }
        val len: Int = (info and INT_MASK).toInt()
        if (len == 0) {
            return 0
        }
        buffer.position = (info ushr 32).toInt()
        val n: Int = buffer.readVarInt32()
        if (n < 0 || n > PackConfig.maxObjectArraySize) {
            throw IllegalStateException("invalid size of object array")
        }
        return n
    }
}
