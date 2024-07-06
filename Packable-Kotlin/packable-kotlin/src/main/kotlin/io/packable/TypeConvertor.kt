package io.packable

/**
 * 类型转换器
 *
 * 通常用于枚举或简单的类型。
 *
 * 通过[TypeConvertor]，
 * 可以直接将类型的实例转换成某种 [PackEncoder] 所支持的类型（如Int/Long/String/ByteArray等)，
 * 然后直接作为一个字段写入 [PackEncoder],
 * 另一方面，可以将通过 [PackDecoder] 读取基本类型（如Int/Long/String/ByteArray等)，然后转换成原本的类型实例。
 *
 * [TypeConvertor] 和 [TypeAdapter] 的不同之处：
 * - 通过 [TypeAdapter] 写入对象，会多一层对象嵌套（多一层递归）；
 * - 而 [TypeConvertor] 则是写入当前对象的一个字段。
 *
 * @see [PackEncoder.putWithConvertor]
 * @see [PackDecoder.getByConvertor]
 */
interface TypeConvertor<T>

interface IntConvertor<T> : TypeConvertor<T> {
    fun toType(value: Int): T
    fun toInt(target: T): Int
}

interface LongConvertor<T> : TypeConvertor<T> {
    fun toType(value: Long): T
    fun toLong(target: T): Long
}

interface StringConvertor<T> : TypeConvertor<T> {
    fun toType(value: String): T
    fun toString(target: T): String
}

interface ByteArrayConvertor<T> : TypeConvertor<T> {
    fun toType(value: ByteArray): T
    fun toByteArray(target: T): ByteArray
}
