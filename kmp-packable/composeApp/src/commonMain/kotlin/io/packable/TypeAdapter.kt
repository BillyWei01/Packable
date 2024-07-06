package io.packable

/**
 * 类型适配器
 *
 * 用于桥接 Encoder/Decoder 和 Object。
 */
interface TypeAdapter<T> {
    /**
     * 将目标对象编码到到encoder
     *
     * @param encoder 编码器
     * @param target 目标对象
     */
    fun encode(encoder: PackEncoder, target: T)

    /**
     * 从decoder中解码出目标对象
     *
     * @param decoder 解码器.
     * @return 返回对象实例
     */
    fun decode(decoder: PackDecoder): T
}

