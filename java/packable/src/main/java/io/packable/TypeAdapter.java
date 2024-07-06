package io.packable;

/**
 * 类型适配器
 * <br>
 * 用于桥接编码器和对象，
 * 可按需实现编码和解码功能。
 */
public interface TypeAdapter<T> {
    /**
     * 将目标对象编码到到encoder
     *
     * @param encoder 编码器
     * @param target 目标对象
     */
    void encode(PackEncoder encoder, T target);

    /**
     * 从decoder中解码出目标对象
     *
     * @param decoder 解码器.
     * @return 返回对象实例
     */
    T decode(PackDecoder decoder);
}
