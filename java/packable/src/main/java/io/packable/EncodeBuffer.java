package io.packable;

final class EncodeBuffer {
    private volatile static int sMaxAllocated = 4096;

    byte[] hb;
    int position;

    EncodeBuffer(byte[] array) {
        hb = array;
        position = 0;
    }

    public void writeByte(byte v) {
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

    public static int getVarInt32Size(int v) {
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

    public void writeVarIntNegative1() {
        hb[position++] = -1;
        hb[position++] = -1;
        hb[position++] = -1;
        hb[position++] = -1;
        hb[position++] = 15;
    }

    public int writeVarInt32(int i, int v) {
        while ((v & 0xffffff80) != 0) {
            hb[i++] = (byte) ((v & 0x7f) | 0x80);
            v >>>= 7;
        }
        hb[i++] = (byte) v;
        return i;
    }

    public void writeVarInt32(int v) {
        position = writeVarInt32(position, v);
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

    void checkCapacity(int expandSize) {
        int capacity = hb.length;
        int desSize = position + expandSize;
        if (desSize <= 0) {
            throw new IllegalStateException("desire capacity overflow");
        }
        if (desSize > capacity) {
            if (desSize > PackConfig.MAX_BUFFER_SIZE) {
                throw new IllegalStateException("desire capacity over limit");
            }
            int newSize = capacity << 1;
            while (desSize > newSize) {
                newSize = newSize << 1;
            }
            /*
             * 扩容之后需要拷贝内容到新的字节数组，需要一定消耗，
             * 因此，在 buffer 比较小的时候，我们扩容激进一些。
             * 当扩容大小超过达到阈值(当前设定为1M)，则不再激进扩容。
             */
            int doubleLimit = Math.min(sMaxAllocated, (1 << 20));
            if (newSize < doubleLimit) {
                newSize = newSize << 1;
            }
            if (newSize > sMaxAllocated) {
                sMaxAllocated = newSize;
            }
            byte[] oldArray = hb;
            byte[] newArray = ByteArrayPool.getArray(newSize);
            System.arraycopy(oldArray, 0, newArray, 0, position);
            hb = newArray;
            ByteArrayPool.recycleArray(oldArray);
        }
    }
}
