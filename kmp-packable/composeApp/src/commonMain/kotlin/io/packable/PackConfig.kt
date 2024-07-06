package io.packable

object PackConfig {
    /**
     * 遇到未知类型时，忽略还是抛异常：
     * true: 忽略 （默认）
     * false: 抛异常
     *
     * 建议 debug 时设置为 false, release 时设置为true。
     */
    var ignoreUnknownType = true

    /**
     * 对象数组最大size
     *
     * 用于编解码对象（字符串和自定义对象）时，限制数组元素的个数，
     * 默认 1M （一百万），可以自行设定。
     *
     * 解码对象数组时，会先检查要创建的数组的大小，再执行创建数组。
     * 这样做时为了防止因为数据异常而创建超大的数组，进而引发OOM。
     */
    var maxObjectArraySize: Int = 1 shl 20

    /**
     * 最大buffer容量，1G
     *
     * 限制 buffer 容量大小主要是为了安全考虑，buffer 过大容易发生OOM。
     * 对象的占用空间通常比buffer大，假设 buffer 有1G了，对象 + buffer 占用空间会在2G以上。
     */
    const val MAX_BUFFER_SIZE: Int = 1 shl 30

    /**
     * 用于标记 NULL 对象
     */
    const val NULL_OBJECT_FLAG: Short = 0xffff.toShort()
}
