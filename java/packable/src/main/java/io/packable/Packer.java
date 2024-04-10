package io.packable;

/**
 * 打包器
 * 可按需实现打包和解包功能
 */
public interface Packer<T> {
    /**
     * 打包目标对象到encoder
     *
     * @param encoder 编码器
     * @param target 目标对象
     */
    void pack(PackEncoder encoder, T target);

    /**
     * 从decoder解包目标对象
     *
     * @param decoder 解码器.
     * @return 返回对象实例
     */
    T unpack(PackDecoder decoder);
}
