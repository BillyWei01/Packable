package io.packable

/**
 * byte数组池
 *
 * 复用基础byte数组，减少GC
 */
internal object ByteArrayPool {
    private const val ARRAY_SIZE = 4096
    private const val CAPACITY = 4
    private var count = 0
    private val arrays = arrayOfNulls<ByteArray>(ARRAY_SIZE)

    fun acquire(): ByteArray {
        if (count > 0) {
            synchronized(arrays) {
                arrays[--count]?.let {
                    arrays[count] = null
                    return it
                }
            }
        }
        return ByteArray(ARRAY_SIZE)
    }

    fun recycle(bytes: ByteArray) {
        if (bytes.size == ARRAY_SIZE) {
            synchronized(arrays) {
                if (count < CAPACITY) {
                    arrays[count++] = bytes
                }
            }
        }
    }
}
