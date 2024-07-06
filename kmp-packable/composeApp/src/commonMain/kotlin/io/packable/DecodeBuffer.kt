package io.packable

internal class DecodeBuffer(
    val hb: ByteArray,
    var position: Int,
    length: Int
) {
    var limit: Int = position + length

    fun checkBound(offset: Int, len: Int) {
        if (offset + len > limit) {
            throw IndexOutOfBoundsException("buffer out of bound")
        }
    }

    fun hasRemaining(): Boolean {
        return position < limit
    }

    fun readByte(): Byte {
        return hb[position++]
    }

    fun readShort(): Short {
        return (((hb[position++].toInt() and 0xff)) or
                ((hb[position++]).toInt() shl 8)).toShort()
    }

    fun readInt(): Int {
        return ((((hb[position++].toInt() and 0xff)) or
                ((hb[position++].toInt() and 0xff) shl 8) or
                ((hb[position++].toInt() and 0xff) shl 16) or
                ((hb[position++]).toInt() shl 24)))
    }

    fun readVarInt32(): Int {
        var x: Int = hb[position++].toInt()
        if ((x shr 7) == 0) return x
        x = (x and 0x7f) or (hb[position++].toInt() shl 7)
        if ((x shr 14) == 0) return x
        x = (x and 0x3fff) or (hb[position++].toInt() shl 14)
        if ((x shr 21) == 0) return x
        x = (x and 0x1fffff) or (hb[position++].toInt() shl 21)
        if ((x shr 28) == 0) return x
        x = (x and 0xfffffff) or (hb[position++].toInt() shl 28)
        return x
    }

    fun readLong(index: Int): Long {
        var i: Int = index
        return (((hb[i++].toLong() and 0xffL) or
                ((hb[i++].toLong() and 0xffL) shl 8) or
                ((hb[i++].toLong() and 0xffL) shl 16) or
                ((hb[i++].toLong() and 0xffL) shl 24) or
                ((hb[i++].toLong() and 0xffL) shl 32) or
                ((hb[i++].toLong() and 0xffL) shl 40) or
                ((hb[i++].toLong() and 0xffL) shl 48) or
                ((hb[i].toLong()) shl 56)))
    }

    fun readLong(): Long {
        val value: Long = readLong(position)
        position += 8
        return value
    }

    fun readFloat(): Float {
        return Float.fromBits(readInt())
    }

    fun readDouble(): Double {
        return Double.fromBits(readLong())
    }

    fun readBytes(bytes: ByteArray) {
        val len: Int = bytes.size
        hb.copyInto(bytes, 0, position, position + len)
        position += len
    }
}
