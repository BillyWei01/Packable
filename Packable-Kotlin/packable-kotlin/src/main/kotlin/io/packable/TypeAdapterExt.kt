package io.packable

fun <T> TypeAdapter<T>.encode(target: T): ByteArray {
    return PackEncoder.encode(target, this)
}

fun <T> TypeAdapter<T>.decode(bytes: ByteArray): T {
    return PackDecoder.decode(bytes, this)
}
