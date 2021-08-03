package io.packable;

public final class EncodeBuffer {
    byte[] hb;
    int position;

    EncodeBuffer(byte[] array) {
        hb = array;
        position = 0;
    }

    public final void writeByte(byte v) {
        hb[position++] = v;
    }

    public void writeShort(int i, short v) {
        hb[i++] = (byte) v;
        hb[i] = (byte) (v >> 8);
    }

    public void writeShort(short v) {
        hb[position++] = (byte) v;
        hb[position++] = (byte) (v >> 8);
    }

    public void writeInt(int v) {
        hb[position++] = (byte) v;
        hb[position++] = (byte) (v >> 8);
        hb[position++] = (byte) (v >> 16);
        hb[position++] = (byte) (v >> 24);
    }

    public void writeInt(int i, int v) {
        hb[i++] = (byte) v;
        hb[i++] = (byte) (v >> 8);
        hb[i++] = (byte) (v >> 16);
        hb[i] = (byte) (v >> 24);
    }

    public static int getVarint32Size(int v) {
        if ((v >> 7) == 0) {
            return 1;
        } else if ((v >> 14) == 0) {
            return 2;
        } else if ((v >> 21) == 0) {
            return 3;
        } else if ((v >> 28) == 0) {
            return 4;
        }
        return 5;
    }

    public void writeVarintNegative1() {
        hb[position++] = -1;
        hb[position++] = -1;
        hb[position++] = -1;
        hb[position++] = -1;
        hb[position++] = 15;
    }

    public int writeVarint32(int i, int v) {
        while ((v & 0xffffff80) != 0) {
            hb[i++] = (byte) ((v & 0x7f) | 0x80);
            v >>>= 7;
        }
        hb[i++] = (byte) v;
        return i;
    }

    public void writeVarint32(int v) {
        position = writeVarint32(position, v);
    }

    public void writeLong(long v) {
        hb[position++] = (byte) v;
        hb[position++] = (byte) (v >> 8);
        hb[position++] = (byte) (v >> 16);
        hb[position++] = (byte) (v >> 24);
        hb[position++] = (byte) (v >> 32);
        hb[position++] = (byte) (v >> 40);
        hb[position++] = (byte) (v >> 48);
        hb[position++] = (byte) (v >> 56);
    }

    public void writeFloat(float v) {
        writeInt(Float.floatToRawIntBits(v));
    }

    public void writeDouble(double v) {
        writeLong(Double.doubleToRawLongBits(v));
    }

    public void writeBytes(byte[] src) {
        int len = src.length;
        if (len > 0) {
            System.arraycopy(src, 0, hb, position, len);
            position += len;
        }
    }
}
