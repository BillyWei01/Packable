package io.packable;

public final class DecodeBuffer {
    byte[] hb;
    int position;
    int limit;

    DecodeBuffer(byte[] array, int offset, int length) {
        hb = array;
        position = offset;
        limit = offset + length;
    }

    void checkBound(int offset, int len) {
        if (offset + len > limit) {
            throw new IndexOutOfBoundsException("buffer out of bound");
        }
    }

    public boolean hasRemaining() {
        return position < limit;
    }

    public byte readByte() {
        return hb[position++];
    }

    public short readShort() {
        return (short) (((hb[position++] & 0xff)) |
                ((hb[position++]) << 8));
    }

    public int readInt() {
        return (((hb[position++] & 0xff)) |
                ((hb[position++] & 0xff) << 8) |
                ((hb[position++] & 0xff) << 16) |
                ((hb[position++]) << 24));
    }

    public int readVarint32() {
        int x = hb[position++];
        if ((x >> 7) == 0) return x;
        x = (x & 0x7f) | (hb[position++] << 7);
        if ((x >> 14) == 0) return x;
        x = (x & 0x3fff) | (hb[position++] << 14);
        if ((x >> 21) == 0) return x;
        x = (x & 0x1fffff) | (hb[position++] << 21);
        if ((x >> 28) == 0) return x;
        x = (x & 0xfffffff) | (hb[position++] << 28);
        return x;
    }

    public long readLong(int i) {
        return (((long) hb[i++] & 0xff) |
                (((long) hb[i++] & 0xff) << 8) |
                (((long) hb[i++] & 0xff) << 16) |
                (((long) hb[i++] & 0xff) << 24) |
                (((long) hb[i++] & 0xff) << 32) |
                (((long) hb[i++] & 0xff) << 40) |
                (((long) hb[i++] & 0xff) << 48) |
                (((long) hb[i]) << 56));
    }

    public long readLong() {
        long value = readLong(position);
        position += 8;
        return value;
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public void readBytes(byte[] bytes) {
        int len = bytes.length;
        System.arraycopy(hb, position, bytes, 0, len);
        position += len;
    }
}
