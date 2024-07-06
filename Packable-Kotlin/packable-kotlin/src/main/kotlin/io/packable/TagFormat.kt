package io.packable

/**
 * Packable 协议采用 TLV (Tag-Length-Value) 结构
 * 其中，Tag占用 1 到 2 byte。
 *
 * 第1个byte:
 * 从低位(bit)到高位：
 * [1-4] : index (when index 小于16时)
 * [5-7] : 类型定义
 * [8] : index 压缩标记
 *
 * 如果 index 小于 16，则用第2个 byte 来保存index，
 * 此时，index 压缩标记等于 1，第一个byte的 [1-4] 位为0。
 * 当前的实现，所支持的index范围为[0, 255] （基于效率考虑）。
 * 如果未来有更大index的需求，是可以通过扩展实现的。
 */
internal object TagFormat {
    private const val TYPE_SHIFT: Int = 4
    const val BIG_INDEX_MASK: Int = 1 shl 7
    const val TYPE_MASK: Int = 7 shl TYPE_SHIFT
    const val INDEX_MASK: Int = 0xF
    const val LITTLE_INDEX_BOUND: Int = 1 shl TYPE_SHIFT

    /*
     * 这里所定义的“类型”，并非对应语言的类型，仅用于标记 value 所占用的空间。
     * 例如，
     * TYPE_0: 表示value占用 0 byte, 可以对应 false, 0, 0.0, 空字符串，空对象等。
     * TYPE_NUM_8: 表示value是个Number或者Boolean, 占用 8 bit (1 byte)。
     * TYPE_VAR_8: 表示value是个对象，其占用空间用 1 byte 保存。
     *             读取时，先读取1个字节获得对象长度n，然后读取随后的n个字节，用于解析对象。
     * 其他依此类推。
     */
    const val TYPE_0: Int = 0
    const val TYPE_NUM_8: Int = 1 shl TYPE_SHIFT
    const val TYPE_NUM_16: Int = 2 shl TYPE_SHIFT
    const val TYPE_NUM_32: Int = 3 shl TYPE_SHIFT
    const val TYPE_NUM_64: Int = 4 shl TYPE_SHIFT
    const val TYPE_VAR_8: Int = 5 shl TYPE_SHIFT
    const val TYPE_VAR_16: Int = 6 shl TYPE_SHIFT
    const val TYPE_VAR_32: Int = 7 shl TYPE_SHIFT
}
