package io.packable;

/**
 * Helper to encode to buffer and decode from buffer, in a compact way, which could compress data size.
 * Generally it takes more time than code directly, and sometimes could take more space when putting number array.
 * It depends on the data,
 * if the array has many compressible elements (like 0, and little integer),
 * it will take less time than code directly, and save a lot of space.
 */
final class CompactCoder {
    static void putIntArray(PackEncoder encoder, int index, int[] value) {
        if (value != null) {
            putNumberArray(encoder, index, new ArrayWrapper(value), NumberType.INT, value.length);
        }
    }

    static int[] getIntArray(PackDecoder decoder, int index) {
        ArrayWrapper wrapper = getNumberArray(decoder, index, NumberType.INT);
        return wrapper != null ? wrapper.intArray : null;
    }

    static void putLongArray(PackEncoder encoder, int index, long[] value) {
        if (value != null) {
            putNumberArray(encoder, index, new ArrayWrapper(value), NumberType.LONG, value.length);
        }
    }

    static long[] getLongArray(PackDecoder decoder, int index) {
        ArrayWrapper wrapper = getNumberArray(decoder, index, NumberType.LONG);
        return wrapper != null ? wrapper.longArray : null;
    }

    static void putDoubleArray(PackEncoder encoder, int index, double[] value) {
        if (value != null) {
            putNumberArray(encoder, index, new ArrayWrapper(value), NumberType.DOUBLE, value.length);
        }
    }

    static double[] getDoubleArray(PackDecoder decoder, int index) {
        ArrayWrapper wrapper = getNumberArray(decoder, index, NumberType.DOUBLE);
        return wrapper != null ? wrapper.doubleArray : null;
    }

    private static void putNumberArray(PackEncoder encoder, int index, ArrayWrapper wrapper, int type, int n) {
        if (n < 0) {
            return;
        }
        if (n == 0) {
            encoder.wrapTagAndLength(index, 0);
            return;
        }

        // calculate spaces
        int sizeOfN = EncodeBuffer.getVarint32Size(n);
        int flagByteCount = getByteCount(n << 1);

        // wrap tag and reserve space for len
        int shift = type == NumberType.INT ? 2 : 3;
        int maxSize = sizeOfN + flagByteCount + (n << shift);
        encoder.checkCapacity(6 + maxSize);
        EncodeBuffer buffer = encoder.getBuffer();
        int pTag = buffer.position;
        encoder.putIndex(index);
        int pLen = buffer.position;
        int sizeOfLen;
        if (maxSize <= 0xff) {
            buffer.hb[pTag] |= TagFormat.TYPE_VAR_8;
            sizeOfLen = 1;
        } else if (maxSize <= 0xffff) {
            buffer.hb[pTag] |= TagFormat.TYPE_VAR_16;
            sizeOfLen = 2;
        } else {
            buffer.hb[pTag] |= TagFormat.TYPE_VAR_32;
            sizeOfLen = 4;
        }
        buffer.position += sizeOfLen;

        buffer.writeVarint32(n);
        int pFlag = buffer.position;
        // move position to values
        buffer.position += flagByteCount;

        wrapArray(buffer, n, pFlag, wrapper, type);

        // wrap len
        // maxSize must be larger than len, so it's safe to put len in position pLen
        int len = buffer.position - (pLen + sizeOfLen);
        if (sizeOfLen == 1) {
            buffer.hb[pLen] = (byte) len;
        } else if (sizeOfLen == 2) {
            buffer.writeShort(pLen, (short) len);
        } else {
            buffer.writeInt(pLen, len);
        }
    }

    private static void wrapArray(EncodeBuffer buffer, int n, int pFlag, ArrayWrapper wrapper, int type) {
        int i = 0;
        while (i < n) {
            int end = Math.min(i + 4, n);
            int flags = 0;
            if (type == NumberType.DOUBLE) {
                for (int j = i; j < end; j++) {
                    double d = wrapper.doubleArray[j];
                    if (d == 0D) {
                        continue;
                    }
                    int shift = ((j & 0x3) << 1);
                    long e = Double.doubleToRawLongBits(d);
                    if ((e << 16) == 0L) {
                        buffer.writeShort((short) (e >> 48));
                        flags |= 1 << shift;
                    } else if ((e << 32) == 0L) {
                        buffer.writeInt((int) (e >> 32));
                        flags |= 2 << shift;
                    } else {
                        buffer.writeLong(e);
                        flags |= 3 << shift;
                    }
                }
            } else {
                for (int j = i; j < end; j++) {
                    long e = type == NumberType.INT ? wrapper.intArray[j] : wrapper.longArray[j];
                    if (e == 0L) {
                        continue;
                    }
                    int shift = (j & 0x3) << 1;
                    if ((e >> 8) == 0L) {
                        buffer.writeByte((byte) e);
                        flags |= 1 << shift;
                    } else if ((e >> 16) == 0L) {
                        buffer.writeShort((short) e);
                        flags |= 2 << shift;
                    } else {
                        if (type == NumberType.INT) {
                            buffer.writeInt((int) e);
                        } else {
                            buffer.writeLong(e);
                        }
                        flags |= 3 << shift;
                    }
                }
            }
            buffer.hb[pFlag + (i >> 2)] = (byte) flags;
            i = end;
        }
    }

    private static ArrayWrapper getNumberArray(PackDecoder decoder, int index, int type) {
        long info = decoder.getInfo(index);
        if (info == PackDecoder.NULL_FLAG) {
            return null;
        }
        int len = (int) (info & PackDecoder.INT_MASK);
        if (len == 0) {
            return new ArrayWrapper(type, 0);
        }

        DecodeBuffer buffer = decoder.getBuffer();
        buffer.position = (int) (info >>> 32);
        int n = buffer.readVarint32();
        if (n < 0) {
            throw new IllegalStateException("invalid array size");
        }
        int pFlag = buffer.position;
        int byteCount = getByteCount(n << 1);
        buffer.checkBound(buffer.position, byteCount);
        buffer.position += byteCount;

        ArrayWrapper wrapper = new ArrayWrapper(type, n);
        takeArray(buffer, n, pFlag, wrapper, type);
        return wrapper;
    }

    private static void takeArray(DecodeBuffer buffer, int n, int pFlag, ArrayWrapper wrapper, int type) {
        for (int i = 0; i < n; i += 4) {
            int b = buffer.hb[pFlag + (i >> 2)] & 0xFF;
            int j = i;
            while (b != 0) {
                int flag = b & 0x3;
                if (flag != 0) {
                    if (type == NumberType.DOUBLE) {
                        long x;
                        if (flag == 1) {
                            x = ((long) buffer.readShort()) << 48;
                        } else if (flag == 2) {
                            x = ((long) buffer.readInt()) << 32;
                        } else {
                            x = buffer.readLong();
                        }
                        wrapper.doubleArray[j] = Double.longBitsToDouble(x);
                    } else if (type == NumberType.INT) {
                        int x;
                        if (flag == 1) {
                            x = buffer.readByte() & 0xff;
                        } else if (flag == 2) {
                            x = buffer.readShort() & 0xffff;
                        } else {
                            x = buffer.readInt();
                        }
                        wrapper.intArray[j] = x;
                    } else {
                        long x;
                        if (flag == 1) {
                            x = buffer.readByte() & 0xff;
                        } else if (flag == 2) {
                            x = buffer.readShort() & 0xffff;
                        } else {
                            x = buffer.readLong();
                        }
                        wrapper.longArray[j] = x;
                    }
                }
                j++;
                b >>= 2;
            }
        }
    }

    private static int getByteCount(int totalBits) {
        int byteCount = (totalBits >> 3);
        if ((totalBits & 0x7) != 0) {
            byteCount++;
        }
        return byteCount;
    }

    private static class ArrayWrapper {
        private int[] intArray;
        private long[] longArray;
        private double[] doubleArray;

        ArrayWrapper(int type, int n) {
            if (type == NumberType.INT) {
                intArray = new int[n];
            } else if (type == NumberType.LONG) {
                longArray = new long[n];
            } else {
                doubleArray = new double[n];
            }
        }

        ArrayWrapper(int[] a) {
            intArray = a;
        }

        ArrayWrapper(long[] a) {
            longArray = a;
        }

        ArrayWrapper(double[] a) {
            doubleArray = a;
        }
    }

    static void putBooleanArray(PackEncoder encoder, int index, boolean[] value) {
        if (value == null) return;
        int n = value.length;
        if (n == 0) {
            encoder.wrapTagAndLength(index, 0);
            return;
        }

        EncodeBuffer buffer = encoder.getBuffer();
        if (n <= 5) {
            byte b = (byte) (n << 5);
            for (int i = 0; i < n; i++) {
                if (value[i]) {
                    b |= 1 << i;
                }
            }
            encoder.wrapTagAndLength(index, 1);
            buffer.writeByte(b);
        } else {
            int remain = n & 0x7;
            int byteCount = (n >> 3) + (remain == 0 ? 1 : 2);
            encoder.wrapTagAndLength(index, byteCount);
            buffer.writeByte((byte) remain);
            int i = 0;
            while (i < n) {
                int end = Math.min(i + 8, n);
                byte b = 0;
                for (int j = i; j < end; j++) {
                    if (value[j]) {
                        b |= 1 << (j & 0x7);
                    }
                }
                buffer.writeByte(b);
                i = end;
            }
        }
    }

    static boolean[] getBooleanArray(PackDecoder decoder, int index) {
        long info = decoder.getInfo(index);
        if (info == PackDecoder.NULL_FLAG) {
            return null;
        }
        int len = (int) (info & PackDecoder.INT_MASK);
        if (len == 0) {
            return new boolean[0];
        }

        DecodeBuffer buffer = decoder.getBuffer();
        buffer.position = (int) (info >>> 32);
        boolean[] a;
        if (len == 1) {
            byte b = buffer.readByte();
            int n = (b & 0xFF) >>> 5;
            a = new boolean[n];
            for (int i = 0; i < n; i++) {
                a[i] = (b & 0x1) != 0;
                b >>= 1;
            }
        } else {
            int remain = buffer.readByte();
            if ((remain >> 3) != 0) {
                throw new IllegalStateException("remain overflow");
            }
            int byteCount = len - 1;
            int n = (byteCount << 3) - (remain > 0 ? 8 - remain : 0);
            a = new boolean[n];
            for (int i = 0; i < n; i += 8) {
                int b = buffer.readByte() & 0xFF;
                int j = i;
                while (b != 0) {
                    a[j++] = (b & 0x1) != 0;
                    b >>= 1;
                }
            }
        }
        return a;
    }
}
