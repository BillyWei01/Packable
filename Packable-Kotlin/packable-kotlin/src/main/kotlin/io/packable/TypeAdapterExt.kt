package io.packable

fun <T> TypeAdapter<T>.encode(target: T): ByteArray {
    return PackEncoder.encode(target, this)
}

fun <T> TypeAdapter<T>.decode(bytes: ByteArray): T {
    return PackDecoder.decode(bytes, this)
}

fun <T> TypeAdapter<T>.encodeObjectList(value: List<T>): ByteArray {
    return PackEncoder.encodeObjectList(value, this)
}

fun <T> TypeAdapter<T>.decodeObjectList(bytes: ByteArray): List<T> {
    return PackDecoder.decodeObjectList(bytes, this)
}
