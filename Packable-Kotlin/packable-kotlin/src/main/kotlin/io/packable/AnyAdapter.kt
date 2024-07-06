package io.packable


import kotlin.reflect.KClass


/**
 * Any 类型打包器
 *
 * 当容器范型是 Any 时，如：List<Any>, Map<String, Any> 等，可以此 Adapter 编解码。
 * 用法举例：
 *
 * ```
 * val map = mutableMapOf<String, Any>()
 * val encoded = PackEncoder().putMap(0, map, valueAdapter = AnyAdapter).bytes
 * val decoded = PackDecoder.newInstance(encoded).getMap<Int, Any?>(0, valueAdapter = AnyAdapter)
 * ```
 *
 * 关于 [AnyAdapter] 的功能和用法，简单说明如下：
 * 1. 内置支持了大部分基本类型，以及基本类型的数组；
 * 2. 支持通过 [registerAdapters] 注入对象类型的 Adapter, 以实现指定类型的编解码。 <br>
 * 3. 支持 Array 以及 List, Set, Map 等容器编解码。
 *    但是需要注意的时，编码阶段，并不记录容器的具体类型信息，
 *    所以反编译时只能用基本的 listOf(), setOf(), mapOf()等去装载容器中的元素。
 *    在JVM平台，以上函数背后所创建的对象为 ArrayList，LinkedHashSet， LinkedHashMap 等。
 *    如果原本容器是一些特殊功能的容器（例如线程安全容器，有序容器等），
 *    可通第2点提到的方法，实现在反序列化阶段还原具体类型容器。
 *
 * Note：
 * 对于明确了范型类型(非Any类型)的容器对象，建议创建具体类型的 Adapter 来编解码，
 * 而不是用 [AnyAdapter] 来编码，因为 [AnyAdapter] 除了编码 value 本身的内容外，还会编码注入“类型”等额外信息。
 */
object AnyAdapter : TypeAdapter<Any?> {
    private const val IDX_TYPE = 0
    private const val IDX_VALUE = 1
    private const val IDX_TAG = 2

    private const val TYPE_NULL = -1
    private const val TYPE_PACKABLE = 0     // 通过 Adapter 编解码的对象类型
    private const val TYPE_CONVERTER = 1    // 通过 Convertor 转换的基本类型
    private const val TYPE_ARRAY = 2        // Array 容器
    private const val TYPE_LIST = 3         // List 容器
    private const val TYPE_SET = 4          // Set 容器
    private const val TYPE_MAP = 5          // Map 容器

    // [6,7] 预留

    // 基本类型，以及基本类型的数组
    private const val TYPE_BOOLEAN = 8
    private const val TYPE_INT = 9
    private const val TYPE_FLOAT = 10
    private const val TYPE_LONG = 11
    private const val TYPE_DOUBLE = 12
    private const val TYPE_STRING = 13

    private const val TYPE_BYTE_ARRAY = 14
    private const val TYPE_INT_ARRAY = 15
    private const val TYPE_FLOAT_ARRAY = 16
    private const val TYPE_LONG_ARRAY = 17
    private const val TYPE_DOUBLE_ARRAY = 18

    private const val TYPE_BYTE = 19
    private const val TYPE_CHAR = 20
    private const val TYPE_SHORT = 21
    private const val TYPE_CHAR_ARRAY = 22
    private const val TYPE_SHORT_ARRAY = 23

    // 基本类型的适配器
    private val classToBasicAdapter = mutableMapOf<KClass<*>, BasicAdapter>()
    private val typeToBasicAdapter = mutableMapOf<Int, BasicAdapter>()

    // 自定义类型适配器
    private var classToAdapter = mapOf<KClass<*>, AdapterWrapper<*>>()
    private var tagToAdapter = mapOf<String, AdapterWrapper<*>>()

    // 类型转换器
    private var classToConvertor = mapOf<KClass<*>, ConvertorWrapper<*>>()
    private var tagToConvertor = mapOf<String, ConvertorWrapper<*>>()

    init {
        setBasicAdapters()
    }

    fun registerAdapters(wrapperList: List<AdapterWrapper<*>>) {
        val classMap = classToAdapter.toMutableMap()
        val tagMap = tagToAdapter.toMutableMap()
        wrapperList.forEach { wrapper ->
            classMap[wrapper.kClass] = wrapper
            tagMap[wrapper.tag] = wrapper
        }
        classToAdapter = classMap
        tagToAdapter = tagMap
    }

    fun registerConvertors(wrapperList: List<ConvertorWrapper<*>>) {
        val classMap = classToConvertor.toMutableMap()
        val tagMap = tagToConvertor.toMutableMap()
        wrapperList.forEach { wrapper ->
            checkConvertorType(wrapper.convertor)
            classMap[wrapper.kClass] = wrapper
            tagMap[wrapper.tag] = wrapper
        }
        classToConvertor = classMap
        tagToConvertor = tagMap
    }

    private fun checkConvertorType(convertor: TypeConvertor<*>) {
        if (convertor !is IntConvertor &&
            convertor !is LongConvertor &&
            convertor !is StringConvertor &&
            convertor !is ByteArrayConvertor
        ) {
            throw IllegalArgumentException("Not support convertor type:${convertor::class}")
        }
    }

    override fun encode(encoder: PackEncoder, target: Any?) {
        if (target == null) {
            // 框架中调用 pack 方法的地方都拦截 null 对象的处理，所以一般不会走到这里。
            // 仅当 AnyPacker 被直接调用时，可能会走到这。
            encoder.putInt(IDX_TYPE, TYPE_NULL)
            return
        }

        val clazz = target::class

        classToAdapter[clazz]?.let { wrapper ->
            encoder.putInt(IDX_TYPE, TYPE_PACKABLE)
            encoder.putString(IDX_TAG, wrapper.tag)
            @Suppress("UNCHECKED_CAST")
            encoder.putObject(IDX_VALUE, target, wrapper.adapter as TypeAdapter<Any>)
            return
        }

        classToConvertor[clazz]?.let { wrapper ->
            encoder.putInt(IDX_TYPE, TYPE_CONVERTER)
            encoder.putString(IDX_TAG, wrapper.tag)
            wrapper.pack(encoder, IDX_VALUE, target)
            return
        }

        classToBasicAdapter[clazz]?.let { packer ->
            encoder.putInt(IDX_TYPE, packer.type)
            packer.putValue(encoder, target)
            return
        }

        packContainer(encoder, target)
    }

    override fun decode(decoder: PackDecoder): Any? {
        val type = decoder.getInt(IDX_TYPE)
        if (type == TYPE_NULL) {
            return null
        }

        if (type == TYPE_PACKABLE) {
            val tag = decoder.getNullableString(IDX_TAG)
            val wrapper = tagToAdapter[tag]
            if (wrapper != null) {
                return decoder.getObject(IDX_VALUE, wrapper.adapter)
            }
            if (!PackConfig.ignoreUnknownType) {
                throw IllegalArgumentException("Miss packer for tag:${tag}")
            }
            return null
        }

        if (type == TYPE_CONVERTER) {
            val tag = decoder.getNullableString(IDX_TAG)
            val wrapper = tagToConvertor[tag]
            if (wrapper != null) {
                return wrapper.unpack(decoder, IDX_VALUE)
            }
            if (!PackConfig.ignoreUnknownType) {
                throw IllegalArgumentException("Miss convertor for tag:${tag}")
            }
            return null
        }

        val basicType = typeToBasicAdapter[type]
        if (basicType != null) {
            return basicType.getValue(decoder)
        }

        return unpackContainer(decoder, type)
    }

    private fun packContainer(encoder: PackEncoder, target: Any) {
        when (target) {
            is Map<*, *> -> {
                encoder.putInt(IDX_TYPE, TYPE_MAP)
                encoder.packMap(IDX_VALUE, target, null, null, this, this)
            }

            is Set<*> -> {
                encoder.putInt(IDX_TYPE, TYPE_SET)
                encoder.putObjectList(IDX_VALUE, target, this)
            }

            is Collection<*> -> {
                encoder.putInt(IDX_TYPE, TYPE_LIST)
                encoder.putObjectList(IDX_VALUE, target, this)
            }

            is Iterable<*> -> {
                encoder.putInt(IDX_TYPE, TYPE_LIST)
                encoder.putObjectList(IDX_VALUE, target.toList(), this)
            }

            is Array<*> -> {
                encoder.putInt(IDX_TYPE, TYPE_ARRAY)
                encoder.putObjectArray(IDX_VALUE, target, this)
            }

            else -> {
                // 可通过调用 registerPackers 方法解决未支持类型的问题。
                if (!PackConfig.ignoreUnknownType) {
                    throw IllegalArgumentException("Not support type:${target::class}")
                }
            }
        }
    }

    private fun unpackContainer(decoder: PackDecoder, type: Int): Any? {
        return when (type) {
            TYPE_MAP -> decoder.unpackMap(IDX_VALUE, null, null, this, this, true)
            TYPE_SET -> decoder.getObjectList(IDX_VALUE, this)?.toSet()
            TYPE_LIST -> decoder.getObjectList(IDX_VALUE, this)
            TYPE_ARRAY -> decoder.getObjectArray(IDX_VALUE, this)
            else -> null
        }
    }

    private fun setBasicAdapters() {
        val packers = arrayOf(
            BasicAdapter(
                TYPE_BOOLEAN, Boolean::class,
                { encoder, target ->
                    encoder.putBoolean(IDX_VALUE, target as Boolean)
                },
                { decoder ->
                    decoder.getBoolean(IDX_VALUE)
                }
            ),
            BasicAdapter(
                TYPE_INT, Int::class,
                { encoder, target ->
                    encoder.putInt(IDX_VALUE, target as Int)
                },
                { decoder ->
                    decoder.getInt(IDX_VALUE)
                }
            ),
            BasicAdapter(
                TYPE_FLOAT, Float::class,
                { encoder, target ->
                    encoder.putFloat(IDX_VALUE, target as Float)
                },
                { decoder ->
                    decoder.getFloat(IDX_VALUE)
                }
            ),
            BasicAdapter(
                TYPE_LONG, Long::class,
                { encoder, target ->
                    encoder.putLong(IDX_VALUE, target as Long)
                },
                { decoder ->
                    decoder.getLong(IDX_VALUE)
                }
            ),
            BasicAdapter(
                TYPE_DOUBLE, Double::class,
                { encoder, target ->
                    encoder.putDouble(IDX_VALUE, target as Double)
                },
                { decoder ->
                    decoder.getDouble(IDX_VALUE)
                }
            ),
            BasicAdapter(
                TYPE_STRING, String::class,
                { encoder, target ->
                    encoder.putString(IDX_VALUE, target as String)
                },
                { decoder ->
                    decoder.getString(IDX_VALUE)
                }
            ),
            BasicAdapter(
                TYPE_BYTE_ARRAY, ByteArray::class,
                { encoder, target ->
                    encoder.putByteArray(IDX_VALUE, target as ByteArray)
                },
                { decoder ->
                    decoder.getByteArray(IDX_VALUE) ?: ByteArray(0)
                }
            ),
            BasicAdapter(
                TYPE_INT_ARRAY, IntArray::class,
                { encoder, target ->
                    encoder.putIntArray(IDX_VALUE, target as IntArray)
                },
                { decoder ->
                    decoder.getIntArray(IDX_VALUE) ?: IntArray(0)
                }
            ),
            BasicAdapter(
                TYPE_FLOAT_ARRAY, FloatArray::class,
                { encoder, target ->
                    encoder.putFloatArray(IDX_VALUE, target as FloatArray)
                },
                { decoder ->
                    decoder.getFloatArray(IDX_VALUE) ?: FloatArray(0)
                }
            ),
            BasicAdapter(
                TYPE_LONG_ARRAY, LongArray::class,
                { encoder, target ->
                    encoder.putLongArray(IDX_VALUE, target as LongArray)
                },
                { decoder ->
                    decoder.getLongArray(IDX_VALUE) ?: LongArray(0)
                }
            ),
            BasicAdapter(
                TYPE_DOUBLE_ARRAY, DoubleArray::class,
                { encoder, target ->
                    encoder.putDoubleArray(IDX_VALUE, target as DoubleArray)
                },
                { decoder ->
                    decoder.getDoubleArray(IDX_VALUE) ?: DoubleArray(0)
                }
            ),
            BasicAdapter(
                TYPE_BYTE, Byte::class,
                { encoder, target ->
                    encoder.putByte(IDX_VALUE, target as Byte)
                },
                { decoder ->
                    decoder.getByte(IDX_VALUE)
                }
            ),
            BasicAdapter(
                TYPE_CHAR, Char::class,
                { encoder, target ->
                    encoder.putInt(IDX_VALUE, (target as Char).code and 0xFFFF)
                },
                { decoder ->
                    decoder.getInt(IDX_VALUE).toChar()
                }
            ),
            BasicAdapter(
                TYPE_SHORT, Short::class,
                { encoder, target ->
                    encoder.putShort(IDX_VALUE, target as Short)
                },
                { decoder ->
                    decoder.getShort(IDX_VALUE)
                }
            ),
            BasicAdapter(
                TYPE_CHAR_ARRAY, CharArray::class,
                { encoder, target ->
                    encoder.putCharArray(IDX_VALUE, target as CharArray)
                },
                { decoder ->
                    decoder.getCharArray(IDX_VALUE) ?: CharArray(0)
                }
            ),
            BasicAdapter(
                TYPE_SHORT_ARRAY, ShortArray::class,
                { encoder, target ->
                    encoder.putShortArray(IDX_VALUE, target as ShortArray)
                },
                { decoder ->
                    decoder.getShortArray(IDX_VALUE) ?: ShortArray(0)
                }
            ),
        )
        packers.forEach { packer ->
            classToBasicAdapter[packer.kClass] = packer
            typeToBasicAdapter[packer.type] = packer
        }
    }
}

/**
 * 基础类型的适配器
 */
private class BasicAdapter(
    val type: Int,
    val kClass: KClass<*>,
    val putValue: (encoder: PackEncoder, target: Any) -> Unit,
    val getValue: (decoder: PackDecoder) -> Any
)

/**
 * Adapter 包装类
 *
 * [tag] 标签，编码时会同时打包标签，解码时通过标签寻找 Packer 来解码。
 * [kClass] 对象类型，用于编码时匹配对象所对应的 Packer。
 * [adapter] 类型适配器
 */
class AdapterWrapper<T>(
    val tag: String,
    val kClass: KClass<*>,
    val adapter: TypeAdapter<T>
) {
    companion object {
        inline fun <reified T> create(tag: String, adapter: TypeAdapter<T>): AdapterWrapper<T> {
            return AdapterWrapper(tag, T::class, adapter)
        }
    }
}

/**
 * Convertor 包装类
 *
 * [tag] 标签，编码时会同时打包标签，解码时通过标签寻找 Convertor 来解码。
 * [kClass] 对象类型，用于编码时匹配对象所对应的 Convertor。
 * [convertor] 对象打包器
 */
class ConvertorWrapper<T>(
    val tag: String,
    val kClass: KClass<*>,
    val convertor: TypeConvertor<T>
) {
    companion object {
        inline fun <reified T> create(tag: String, convertor: TypeConvertor<T>): ConvertorWrapper<T> {
            return ConvertorWrapper(tag, T::class, convertor)
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun pack(encoder: PackEncoder, index: Int, target: Any) {
        when (convertor) {
            is IntConvertor -> encoder.putInt(index, convertor.toInt(target as T))
            is LongConvertor -> encoder.putLong(index, convertor.toLong(target as T))
            is StringConvertor -> encoder.putString(index, convertor.toString(target as T))
            is ByteArrayConvertor -> encoder.putByteArray(index, convertor.toByteArray(target as T))
        }
    }

    internal fun unpack(decoder: PackDecoder, index: Int): Any {
        return when (convertor) {
            is IntConvertor -> convertor.toType(decoder.getInt(index)) as Any
            is LongConvertor -> convertor.toType(decoder.getLong(index)) as Any
            is StringConvertor -> convertor.toType(decoder.getString(index)) as Any
            is ByteArrayConvertor -> convertor.toType(decoder.getByteArray(index) ?: ByteArray(0)) as Any
            else -> throw IllegalStateException("Unexpected convertor:${convertor::class}")
        }
    }
}
