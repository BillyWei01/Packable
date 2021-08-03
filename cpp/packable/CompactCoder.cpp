
#include "CompactCoder.h"

enum NumberType {
    INT,
    INT64,
    DOUBLE
};

union ArrayWrapper {
    int *intArray;
    int64_t *int64Array;
    double *doubleArray;
};

void allocateArray(ArrayWrapper &wrapper, int type, int n) {
    if (type == INT) {
        wrapper.intArray = new int[n];
    } else if (type == INT64) {
        wrapper.int64Array = new int64_t[n];
    } else {
        wrapper.doubleArray = new double[n];
    }
}

void CompactCoder::putCompactIntArray(PackEncoder &encoder, uint8_t index, int *value, int n) {
    if (value != nullptr && n >= 0) {
        ArrayWrapper wrapper;
        wrapper.intArray = value;
        putNumberArray(encoder, index, wrapper, INT, n);
    }
}

int *CompactCoder::getCompactIntArray(PackDecoder &decoder, int index, int &outCount) {
    ArrayWrapper wrapper;
    wrapper.intArray = nullptr;
    getNumberArray(decoder, index, wrapper, INT, outCount);
    return wrapper.intArray;
}

void
CompactCoder::putCompactInt64Array(PackEncoder &encoder, uint8_t index, int64_t *value, int n) {
    if (value != nullptr && n >= 0) {
        ArrayWrapper wrapper;
        wrapper.int64Array = value;
        putNumberArray(encoder, index, wrapper, INT64, n);
    }
}

int64_t *CompactCoder::getCompactInt64Array(PackDecoder &decoder, int index, int &outCount) {
    ArrayWrapper wrapper;
    wrapper.int64Array = nullptr;
    getNumberArray(decoder, index, wrapper, INT64, outCount);
    return wrapper.int64Array;
}

void CompactCoder::putCompactDoubleArray(PackEncoder &encoder, uint8_t index, double *value, int n) {
    if (value != nullptr && n >= 0) {
        ArrayWrapper wrapper;
        wrapper.doubleArray = value;
        putNumberArray(encoder, index, wrapper, DOUBLE, n);
    }
}

double *CompactCoder::getCompactDoubleArray(PackDecoder &decoder, int index, int &outCount) {
    ArrayWrapper wrapper;
    wrapper.doubleArray = nullptr;
    getNumberArray(decoder, index, wrapper, DOUBLE, outCount);
    return wrapper.doubleArray;
}

int getByteCount(int totalBits) {
    int byteCount = (totalBits >> 3);
    if ((totalBits & 0x7) != 0) {
        byteCount++;
    }
    return byteCount;
}

void wrapArray(PackBuffer &buffer, int n, int pFlag, ArrayWrapper &wrapper, int type) {
    int i = 0;
    while (i < n) {
        int end = min(i + 4, n);
        int flags = 0;
        if (type == DOUBLE) {
            for (int j = i; j < end; j++) {
                double d = wrapper.doubleArray[j];
                if (d == 0) {
                    continue;
                }
                int shift = ((j & 0x3) << 1);
                int64_t e = *(int64_t *) (&d);
                if ((e << 16) == 0L) {
                    buffer.writeShort((short) (e >> 48));
                    flags |= 1 << shift;
                } else if ((e << 32) == 0L) {
                    buffer.writeInt((int) (e >> 32));
                    flags |= 2 << shift;
                } else {
                    buffer.writeInt64(e);
                    flags |= 3 << shift;
                }
            }
        } else {
            for (int j = i; j < end; j++) {
                int64_t e = type == INT ? wrapper.intArray[j] : wrapper.int64Array[j];
                if (e == 0L) {
                    continue;
                }
                int shift = ((j & 0x3) << 1);
                if ((e >> 8) == 0L) {
                    buffer.writeByte((char) e);
                    flags |= 1 << shift;
                } else if ((e >> 16) == 0L) {
                    buffer.writeShort((short) e);
                    flags |= 2 << shift;
                } else {
                    if (type == INT) {
                        buffer.writeInt((int) e);
                    } else {
                        buffer.writeInt64(e);
                    }
                    flags |= 3 << shift;
                }
            }
        }
        buffer.hb[pFlag + (i >> 2)] = (char) flags;
        i = end;
    }
}

void CompactCoder::putNumberArray(PackEncoder &encoder, uint8_t index,
                                  ArrayWrapper &wrapper, int type, int n) {
    if (n < 0) {
        return;
    }
    if (n == 0) {
        encoder.wrapTagAndLength(index, 0);
        return;
    }

    // calculate spaces
    int sizeOfN = PackBuffer::getVarint32Size(n);
    int flagByteCount = getByteCount(n << 1);

    // wrap tag and reserve space for len
    int shift = type == INT ? 2 : 3;
    int maxSize = sizeOfN + flagByteCount + (n << shift);
    encoder.checkCapacity(6 + maxSize);
    PackBuffer &buffer = encoder.buffer;
    int pTag = buffer.position;
    encoder.putIndex(index);
    int pLen = buffer.position;
    int sizeOfLen;
    if (maxSize <= 0xff) {
        buffer.hb[pTag] |= TagFormat::TYPE_VAR_8;
        sizeOfLen = 1;
    } else if (maxSize <= 0xffff) {
        buffer.hb[pTag] |= TagFormat::TYPE_VAR_16;
        sizeOfLen = 2;
    } else {
        buffer.hb[pTag] |= TagFormat::TYPE_VAR_32;
        sizeOfLen = 4;
    }
    buffer.position += sizeOfLen;

    buffer.writeVarint32(n);
    int pFlag = buffer.position;
    // move position to values
    buffer.position += flagByteCount;

    wrapArray(buffer, n, pFlag, wrapper, type);

    // wrap len
    // maxSize must be large than len, so it's safe to put len in position pLen
    int len = buffer.position - (pLen + sizeOfLen);
    if (sizeOfLen == 1) {
        buffer.hb[pLen] = (char) len;
    } else if (sizeOfLen == 2) {
        buffer.writeShort(pLen, (short) len);
    } else {
        buffer.writeInt(pLen, len);
    }
}

void takeArray(PackBuffer &buffer, int n, int pFlag, ArrayWrapper &wrapper, int type) {
    int j = 0;
    for (int i = 0; i < n;) {
        int b = (uint8_t) buffer.hb[pFlag + (i >> 2)];
        j = i;
        i = min(i + 4, n);
        if (type == DOUBLE) {
            while (b != 0) {
                int flag = b & 0x3;
                int64_t x;
                if (flag == 0) {
                    x = 0;
                } else if (flag == 1) {
                    x = ((int64_t) buffer.readShort()) << 48;
                } else if (flag == 2) {
                    x = ((int64_t) buffer.readInt()) << 32;
                } else {
                    x = buffer.readInt64();
                }
                wrapper.doubleArray[j++] = *(double *) (&x);
                if (j == i) {
                    break;
                }
                b >>= 2;
            }
            while (j < i) {
                wrapper.doubleArray[j++] = 0;
            }
        } else {
            while (b != 0) {
                int flag = b & 0x3;
                if (flag < 3) {
                    int64_t x;
                    if (flag == 0) {
                        x = 0;
                    } else if (flag == 1) {
                        x = (uint8_t) buffer.readByte();
                    } else {
                        x = (uint16_t) buffer.readShort();
                    }
                    if (type == INT) {
                        wrapper.intArray[j++] = (int) x;
                    } else {
                        wrapper.int64Array[j++] = x;
                    }
                } else {
                    if (type == INT) {
                        wrapper.intArray[j++] = buffer.readInt();
                    } else {
                        wrapper.int64Array[j++] = buffer.readInt64();
                    }
                }
                if (j == i) {
                    break;
                }
                b >>= 2;
            }
            if (type == INT) {
                while (j < i) wrapper.intArray[j++] = 0;
            } else {
                while (j < i) wrapper.int64Array[j++] = 0;
            }
        }
    }
}

void CompactCoder::getNumberArray(PackDecoder &decoder, int index,
                                  ArrayWrapper &wrapper, int type, int &outCount) {
    long info = decoder.getInfo(index);
    if (info == PackDecoder::NULL_FLAG) {
        outCount = 0;
        return;
    }
    int len = (int) (info & PackDecoder::INT_MASK);
    if (len == 0) {
        outCount = 0;
        allocateArray(wrapper, type, 0);
        return;
    }

    PackBuffer &buffer = decoder.buffer;
    buffer.position = ((uint64_t) info) >> 32;
    int n = buffer.readVarint32();
    if (n < 0) {
        throw "invalid array size";
    }
    int pFlag = buffer.position;
    int byteCount = getByteCount(n << 1);
    buffer.checkBound(buffer.position, byteCount);
    buffer.position += byteCount;

    outCount = n;
    allocateArray(wrapper, type, n);
    takeArray(buffer, n, pFlag, wrapper, type);
}

void CompactCoder::putEnumArray(PackEncoder &encoder, uint8_t index, const int *value, int n) {
    if (value == nullptr || n < 0) {
        return;
    }
    if (n == 0) {
        encoder.wrapTagAndLength(index, 0);
        return;
    }

    int sum = 0;
    for (int i = 0; i < n; i++) {
        sum |= value[i];
    }
    int bitShift;
    if ((sum >> 1) == 0) {
        bitShift = 0;
    } else if ((sum >> 2) == 0) {
        bitShift = 1;
    } else if ((sum >> 4) == 0) {
        bitShift = 2;
    } else if ((sum >> 8) == 0) {
        bitShift = 3;
    } else {
        throw "only accept values less than 255";
    }

    PackBuffer &buffer = encoder.buffer;

    // the first byte: 000- 2 bits 'bitShift' - 3 bits remain
    int byteCount;
    if (bitShift == 3) {
        byteCount = n + 1;
        encoder.wrapTagAndLength(index, byteCount);
        int pos = buffer.position;
        buffer.hb[pos++] = (char) (bitShift << 3);
        for (int i = 0; i < n; i++) {
            buffer.hb[pos + i] = (char) value[i];
        }
    } else {
        int totalBits = n << bitShift;
        int remain = totalBits & 0x7;
        byteCount = (totalBits >> 3) + (remain == 0 ? 1 : 2);
        encoder.wrapTagAndLength(index, byteCount);
        int pos = buffer.position;
        buffer.hb[pos++] = (char) ((bitShift << 3) | remain);

        // bitShift=0, indexShift=3, indexMask=0x7
        // bitShift=1, indexShift=2, indexMask=0x3
        // bitShift=2, indexShift=1, indexMask=0x1
        int indexShift = 3 - bitShift;
        int indexMask = ~(0xffffffff << indexShift);
        int step = 1 << indexShift;
        int i = 0;
        while (i < n) {
            int end = min(i + step, n);
            char b = 0;
            for (int j = i; j < end; j++) {
                b |= value[j] << ((j & indexMask) << bitShift);
            }
            buffer.hb[pos + (i >> indexShift)] = b;
            i = end;
        }
    }
    buffer.position += byteCount;
}

int *CompactCoder::getEnumArray(PackDecoder &decoder, int index, int &outCount) {
    int64_t info = decoder.getInfo(index);
    if (info == PackDecoder::NULL_FLAG) {
        outCount = 0;
        return nullptr;
    }
    int len = (int) (info & PackDecoder::INT_MASK);
    if (len == 0) {
        outCount = 0;
        return new int[0];
    }

    PackBuffer &buffer = decoder.buffer;

    buffer.position = ((uint64_t) info) >> 32;
    int bitInfo = buffer.readByte();
    if ((bitInfo >> 5) != 0) {
        throw "bit info overflow";
    }
    int bitShift = bitInfo >> 3;
    int byteCount = len - 1;
    int *a;
    if (bitShift == 3) {
        a = new int[byteCount];
        outCount = byteCount;
        uint8_t *pB = (uint8_t *) (buffer.hb + buffer.position);
        for (int i = 0; i < byteCount; i++) {
            a[i] = pB[i];
        }
    } else {
        int remain = bitInfo & 0x7;
        int indexShift = 3 - bitShift;
        int n = (byteCount << indexShift);
        if (remain > 0) {
            n -= ((8 - remain) >> bitShift);
        }
        int pos = buffer.position;
        int byteShirt = 1 << bitShift;
        int valueMask = ~(0xffffffff << byteShirt);
        int step = 1 << indexShift;
        a = new int[n];
        outCount = n;
        int i = 0;
        while (true) {
            int b = (uint8_t) buffer.hb[pos + (i >> indexShift)];
            int j = i;
            i += step;
            if (i < n) {
                while (b != 0) {
                    a[j++] = b & valueMask;
                    b >>= byteShirt;
                }
                while (j < i) {
                    a[j++] = 0;
                }
            } else {
                while (j < n) {
                    a[j++] = b & valueMask;
                    b >>= byteShirt;
                }
                break;
            }
        }
    }
    buffer.position += byteCount;
    return a;
}

void CompactCoder::putBooleanArray(PackEncoder &encoder, uint8_t index, const bool *value, int n) {
    if (value == nullptr || n < 0) {
        return;
    }

    if (n == 0) {
        encoder.wrapTagAndLength(index, 0);
        return;
    }

    PackBuffer &buffer = encoder.buffer;
    if (n <= 5) {
        char b = (char) (n << 5);
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
        buffer.writeByte((char) remain);
        int i = 0;
        while (i < n) {
            int end = min(i + 8, n);
            char b = 0;
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

bool *CompactCoder::getBooleanArray(PackDecoder &decoder, int index, int &outCount) {
    int64_t info = decoder.getInfo(index);
    if (info == PackDecoder::NULL_FLAG) {
        outCount = 0;
        return nullptr;
    }
    int len = (int) (info & PackDecoder::INT_MASK);
    if (len == 0) {
        outCount = 0;
        return new bool[0];
    }
    PackBuffer &buffer = decoder.buffer;
    buffer.position = ((uint64_t) info) >> 32;
    bool *a;
    if (len == 1) {
        uint8_t b = (uint8_t) buffer.readByte();
        int n = b >> 5;
        a = new bool[n];
        outCount = n;
        for (int i = 0; i < n; i++) {
            a[i] = b & 0x1;
            b >>= 1;
        }
    } else {
        int remain = buffer.readByte();
        if ((remain >> 3) != 0) {
            throw "remain overflow";
        }
        int byteCount = len - 1;
        int n = (byteCount << 3) - (remain > 0 ? 8 - remain : 0);
        a = new bool[n];
        outCount = n;
        int i = 0;
        while (true) {
            uint8_t b = (uint8_t) buffer.hb[buffer.position++];
            int j = i;
            i += 8;
            if (i < n) {
                while (b != 0) {
                    a[j++] = b & 1;
                    b >>= 1;
                }
                while (j < i) {
                    a[j++] = false;
                }
            } else {
                while (j < n) {
                    a[j++] = b & 1;
                    b >>= 1;
                }
                break;
            }
        }
    }
    return a;
}
