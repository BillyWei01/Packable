package io.packable

internal class EncodeBuffer(var hb: ByteArray) {
    var position: Int = 0

    fun writeByte(v: Byte) {
        hb[position++] = v
    }

    fun writeShort(index: Int, v: Short) {
        var i: Int = index
        hb[i++] = v.toByte()
        hb[i] = (v.toInt() shr 8).toByte()
    }

    fun writeShort(v: Short) {
        hb[position++] = v.toByte()
        hb[position++] = (v.toInt() shr 8).toByte()
    }

    fun writeInt(v: Int) {
        hb[position++] = v.toByte()
        hb[position++] = (v shr 8).toByte()
        hb[position++] = (v shr 16).toByte()
        hb[position++] = (v shr 24).toByte()
    }

    fun writeInt(index: Int, v: Int) {
        var i: Int = index
        hb[i++] = v.toByte()
        hb[i++] = (v shr 8).toByte()
        hb[i++] = (v shr 16).toByte()
        hb[i] = (v shr 24).toByte()
    }

    fun writeVarIntNegative1() {
        hb[position++] = -1
        hb[position++] = -1
        hb[position++] = -1
        hb[position++] = -1
        hb[position++] = 15
    }

    private fun writeVarInt32(index: Int, value: Int): Int {
        var i: Int = index
        var v: Int = value
        while ((v and -0x80) != 0) {
            hb[i++] = ((v and 0x7f) or 0x80).toByte()
            v = v ushr 7
        }
        hb[i++] = v.toByte()
        return i
    }

    fun writeVarInt32(v: Int) {
        position = writeVarInt32(position, v)
    }

    fun writeLong(v: Long) {
        hb[position++] = v.toByte()
        hb[position++] = (v shr 8).toByte()
        hb[position++] = (v shr 16).toByte()
        hb[position++] = (v shr 24).toByte()
        hb[position++] = (v shr 32).toByte()
        hb[position++] = (v shr 40).toByte()
        hb[position++] = (v shr 48).toByte()
        hb[position++] = (v shr 56).toByte()
    }

    fun writeFloat(v: Float) {
        writeInt(v.toRawBits())
    }

    fun writeDouble(v: Double) {
        writeLong(v.toRawBits())
    }

    fun writeBytes(src: ByteArray) {
        val len: Int = src.size
        if (len > 0) {
            src.copyInto(hb, position, 0, len)
            position += len
        }
    }

    fun checkCapacity(expandSize: Int) {
        val capacity: Int = hb.size
        val expectedSize: Int = position + expandSize
        if (expectedSize <= 0) {
            throw IllegalStateException("expected capacity overflow")
        }
        if (expectedSize > capacity) {
            if (expectedSize > PackConfig.MAX_BUFFER_SIZE) {
                throw IllegalStateException("expected capacity over limit")
            }

            // 一直 double newSize，直到大于 expectedSize 为止。
            var newSize: Int = capacity shl 1
            while (expectedSize > newSize) {
                newSize = newSize shl 1
            }

            /*
             * 扩容之后需要拷贝内容到新的字节数组，需要一定消耗，
             * 因此，在 buffer 比较小的时候，我们扩容激进一些。
             * 当扩容大小超过达到阈值(64K)，则不再激进扩容。
             */
            if (newSize < (1 shl 16)) {
                newSize = newSize shl 1
            }

            val oldArray: ByteArray = hb
            val newArray = ByteArray(newSize)
            oldArray.copyInto(newArray, 0, 0, position)
            ByteArrayPool.recycle(oldArray)
            hb = newArray
        }
    }

    companion object {
        fun getVarInt32Size(v: Int): Int {
            if ((v shr 7) == 0) {
                return 1
            } else if ((v shr 14) == 0) {
                return 2
            } else if ((v shr 21) == 0) {
                return 3
            } else if ((v shr 28) == 0) {
                return 4
            }
            return 5
        }
    }
}
