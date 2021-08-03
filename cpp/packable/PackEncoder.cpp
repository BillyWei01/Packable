
#include "PackEncoder.h"


int PackEncoder::sMaxAllocated = BUFFER_DEFAULT_CAPACITY;

using namespace TagFormat;

void PackEncoder::checkCapacity(int expandSize) {
    int capacity = buffer.limit;
    int desSize = buffer.position + expandSize;
    if (desSize <= 0) {
        throw "desire capacity overflow";
    }
    if (desSize > capacity) {
        if (desSize > PackConfig::MAX_BUFFER_SIZE) {
            throw "desire capacity over limit";
        }
        int newSize = capacity << 1;
        while (desSize > newSize) {
            newSize = newSize << 1;
        }
        /*
         * If the init buffer is small and the final content length is large,
         * it's not effective to double buffer on every time that capacity not enough.
         * So we mark the sMaxAllocated to indicate the level of memory occupied,
         * when buffer is lower than sMaxAllocated, it's high probability to grow up to that level too,
         * and we double it again if the size still less than doubleLimit.
         * Example:
         * If desSize = 5K, newSize will be 8K,
         * If sMaxAllocated is larger then 8K, double size again to 16K, otherwise just to 8K.
         */
        int doubleLimit = min(sMaxAllocated, PackConfig::DOUBLE_BUFFER_LIMIT);
        if (newSize < doubleLimit) {
            newSize = newSize << 1;
        }
        if (newSize > sMaxAllocated) {
            sMaxAllocated = newSize;
        }
        char *oldArray = buffer.hb;
        char *newArray = new char[newSize];
        memcpy(newArray, buffer.hb, buffer.position);
        buffer.hb = newArray;
        buffer.limit = newSize;
        if (oldArray != defaultBytes) {
            delete[] oldArray;
        }
    }
}

void PackEncoder::putIndex(uint8_t index) {
    if (index >= LITTLE_INDEX_BOUND) {
        buffer.writeByte(BIG_INDEX_MASK);
    }
    buffer.writeByte((char) (index));
}

void PackEncoder::wrapTagAndLength(uint8_t index, int len) {
    checkCapacity(6 + len);
    if (len == 0) {
        putIndex(index);
    } else {
        int pos = buffer.position;
        putIndex(index);
        if (len <= 0xff) {
            buffer.hb[pos] |= TYPE_VAR_8;
            buffer.writeByte((char) len);
        } else if (len <= 0xffff) {
            buffer.hb[pos] |= TYPE_VAR_16;
            buffer.writeShort((short) len);
        } else {
            buffer.hb[pos] |= TYPE_VAR_32;
            buffer.writeInt(len);
        }
    }
}

PackBuffer* PackEncoder::putCustom(int index, int len){
    wrapTagAndLength(index, len);
    return &buffer;
}

PackEncoder &PackEncoder::putByte(uint8_t index, char value) {
    checkCapacity(3);
    if (value == 0) {
        putIndex(index);
    } else {
        if (index < LITTLE_INDEX_BOUND) {
            buffer.writeByte((char) (index | TYPE_NUM_8));
        } else {
            buffer.writeByte((char) (BIG_INDEX_MASK | TYPE_NUM_8));
            buffer.writeByte((char) index);
        }
        buffer.writeByte(value);
    }
    return *this;
}

PackEncoder &PackEncoder::putBool(uint8_t index, bool value) {
    return putByte(index, (char) value);
}

PackEncoder &PackEncoder::putShort(uint8_t index, short value) {
    checkCapacity(4);
    if (value == 0) {
        putIndex(index);
    } else {
        int pos = buffer.position;
        putIndex(index);
        if ((value >> 8) == 0) {
            buffer.hb[pos] |= TYPE_NUM_8;
            buffer.writeByte((char) value);
        } else {
            buffer.hb[pos] |= TYPE_NUM_16;
            buffer.writeShort(value);
        }
    }
    return *this;
}

PackEncoder &PackEncoder::putInt(uint8_t index, int value) {
    checkCapacity(6);
    if (value == 0) {
        putIndex(index);
    } else {
        int pos = buffer.position;
        putIndex(index);
        if ((value >> 8) == 0) {
            buffer.hb[pos] |= TYPE_NUM_8;
            buffer.writeByte((char) value);
        } else if ((value >> 16) == 0) {
            buffer.hb[pos] |= TYPE_NUM_16;
            buffer.writeShort((short) value);
        } else {
            buffer.hb[pos] |= TYPE_NUM_32;
            buffer.writeInt(value);
        }
    }
    return *this;
}

/*
 * Put int value with zigzag encoding.
 *
 * Zigzag encoding equivalent to:
 * n = n >= 0 ? n * 2 : (-n) * 2 - 1;
 * Positive effect:
 * Make little negative integer to be little positive integer.
 * Side effect:
 * Double positive integer, some times it makes integer to cost more space than before.
 * For example:
 * Numbers belong [128, 255], cost one byte,
 * after zigzag encode, to [256, 510], cost two bytes.
 *
 * So if the value is high probability to be little negative number, using zigzag encoding could be helpful,
 * otherwise just use putInt(int, int) will be more effective.
 */
PackEncoder &PackEncoder::putSInt(uint8_t index, int value) {
    return putInt(index, (value << 1) ^ (value >> 31));
}

PackEncoder &PackEncoder::putInt64(uint8_t index, int64_t value) {
    checkCapacity(10);
    if (value == 0) {
        putIndex(index);
    } else {
        int pos = buffer.position;
        putIndex(index);
        if ((value >> 32) != 0) {
            buffer.hb[pos] |= TYPE_NUM_64;
            buffer.writeInt64(value);
        } else if ((value >> 8) == 0) {
            buffer.hb[pos] |= TYPE_NUM_8;
            buffer.writeByte((char) value);
        } else if ((value >> 16) == 0) {
            buffer.hb[pos] |= TYPE_NUM_16;
            buffer.writeShort((short) value);
        } else {
            buffer.hb[pos] |= TYPE_NUM_32;
            buffer.writeInt((int) value);
        }
    }
    return *this;
}

PackEncoder &PackEncoder::putSInt64(uint8_t index, int64_t value) {
    return putInt64(index, (value << 1) ^ (value >> 63));
}

PackEncoder &PackEncoder::putFloat(uint8_t index, float value) {
    checkCapacity(6);
    if (value == 0) {
        putIndex(index);
    } else {
        if (index < LITTLE_INDEX_BOUND) {
            buffer.writeByte((char) (index | TYPE_NUM_32));
        } else {
            buffer.writeByte((char) (BIG_INDEX_MASK | TYPE_NUM_32));
            buffer.writeByte((char) index);
        }
        buffer.writeFloat(value);
    }
    return *this;
}

PackEncoder &PackEncoder::putDouble(uint8_t index, double value) {
    checkCapacity(10);
    if (value == 0) {
        putIndex(index);
    } else {
        if (index < LITTLE_INDEX_BOUND) {
            buffer.writeByte((char) (index | TYPE_NUM_64));
        } else {
            buffer.writeByte((char) (BIG_INDEX_MASK | TYPE_NUM_64));
            buffer.writeByte((char) index);
        }
        buffer.writeDouble(value);
    }
    return *this;
}

/*
 * Put double value in compact way.
 *
 * If the number in binary has few of '1' (significant bits), it can be compressed to two or four bytes,
 * because the significant bits always in highest bit of double.
 * For example,
 * integers less than 1<<21 (two million) can be compressed to four bytes,
 * integers less than 1<<5 (32) can be compressed to two bytes.
 * Besides, some other integer has few of significant bits also can be well compressed,
 * like 1/4 (0.25), 1/2 (0.5), 1+1/2 (1.5), ...
 *
 * Normally, we could just focus on if the number is little integer (less then two million)
 * For efficiency (time/space balance), we just compressed to 4 bytes (if it could),
 * even the number has two significant bytes.
 */
PackEncoder &PackEncoder::putCDouble(uint8_t index, double value) {
    checkCapacity(10);
    if (value == 0) {
        putIndex(index);
    } else {
        int pos = buffer.position;
        putIndex(index);
        uint64_t i = *((uint64_t *) &value);
        // To reuse the decode process (padding zero at high bytes),
        // we need to reverse the double value
        uint64_t i32 = i << 32;
        if (i32 == 0L) {
            buffer.hb[pos] |= TYPE_NUM_32;
            buffer.writeInt(i >> 32);
        } else {
            buffer.hb[pos] |= TYPE_NUM_64;
            buffer.writeInt64((i >> 32) | i32);
        }
    }
    return *this;
}

PackEncoder &PackEncoder::putString(uint8_t index, string *value) {
    if (value != nullptr) {
        int len = value->length();
        wrapTagAndLength(index, len);
        if (len != 0) {
            buffer.writeBytes((char *) value->data(), len);
        }
    }
    return *this;
}

int PackEncoder::wrapObjectArrayTag(uint8_t index, int size) {
    if (size > PackConfig::MAX_OBJECT_ARRAY_SIZE) {
        throw "object array size out of limit";
    }
    // at most case: 2 bytes index, 4 bytes len, 5 bytes size
    checkCapacity(11);
    int pTag = buffer.position;
    putIndex(index);
    return size <= 0 ? -1 : pTag;
}

PackEncoder &PackEncoder::putStringArray(uint8_t index, string **value, int n) {
    if (value == nullptr || n < 0) return *this;
    int pTag = wrapObjectArrayTag(index, n);
    if (pTag < 0) return *this;
    buffer.position += 4;
    int pValue = buffer.position;
    buffer.writeVarint32(n);
    for (int i = 0; i < n; i++) {
        wrapString(value[i]);
    }
    putLen(pTag, pValue);
    return *this;
}

void PackEncoder::wrapString(string *str) {
    if (str == nullptr) {
        checkCapacity(5);
        buffer.writeVarintNegative1();
    } else {
        u_int32_t len = str->length();
        checkCapacity(5 + len);
        buffer.writeVarint32(len);
        buffer.writeBytes((char *) str->data(), len);
    }
}

PackEncoder &PackEncoder::putPackable(uint8_t index, Packable *value) {
    if (value == nullptr) {
        return *this;
    }
    checkCapacity(6);
    int pTag = buffer.position;
    putIndex(index);
    buffer.position += 4;
    int pValue = buffer.position;
    value->encode(*this);
    if (pValue == buffer.position) {
        buffer.position -= 4;
    } else {
        putLen(pTag, pValue);
    }
    return *this;
}

PackEncoder &PackEncoder::putPackableArray(uint8_t index, Packable **value, int n) {
    if (value == nullptr || n < 0) return *this;
    int pTag = wrapObjectArrayTag(index, n);
    if (pTag < 0) return *this;
    buffer.position += 4;
    int pValue = buffer.position;
    buffer.writeVarint32(n);
    for (int i = 0; i < n; i++) {
        wrapPackable(value[i]);
    }
    putLen(pTag, pValue);
    return *this;
}

void PackEncoder::wrapPackable(Packable *pack) {
    checkCapacity(2);
    if (pack == nullptr) {
        buffer.writeShort(PackConfig::NULL_PACKABLE);
    } else {
        int pLen = buffer.position;
        buffer.position += 2;
        int pPack = buffer.position;
        pack->encode(*this);
        int len = buffer.position - pPack;
        if (len <= 0x7FFF) {
            buffer.writeShort(pLen, (short) len);
        } else {
            checkCapacity(2);
            memmove(buffer.hb + (pPack + 2), buffer.hb + pPack, len);
            buffer.position += 2;
            // if len >= 0x7FFF0000, the first two bytes will be 0xffff (means the object is null),
            // but the PackConfig.MAX_BUFFER_SIZE is 1 << 30,
            // so it's not impossible to be len >= 0x7FFF0000
            buffer.writeShort(pLen, (short) ((len >> 16) | 0x8000));
            buffer.writeShort(pLen + 2, (short) len);
        }
    }
}

void PackEncoder::putLen(int pTag, int pValue) {
    int len = buffer.position - pValue;
    if (len <= PackConfig::TRIM_SIZE_LIMIT) {
        buffer.hb[pTag] |= TYPE_VAR_8;
        buffer.hb[pValue - 4] = (char) len;
        memmove(buffer.hb + (pValue - 3), buffer.hb + pValue, len);
        buffer.position -= 3;
    } else {
        buffer.hb[pTag] |= TYPE_VAR_32;
        buffer.writeInt(pValue - 4, len);
    }
}

PackEncoder &PackEncoder::putByteArray(uint8_t index, char *value, int n) {
    if (value != nullptr && n >= 0) {
        wrapTagAndLength(index, n);
        buffer.writeBytes(value, n);
    }
    return *this;
}

PackEncoder &PackEncoder::putIntArray(uint8_t index, int *value, int n) {
    if (is_little_end && n > 4) {
        return putByteArray(index, (char *) value, n << 2);
    } else {
        if (value != nullptr && n >= 0) {
            wrapTagAndLength(index, n << 2);
            for (int i = 0; i < n; i++) {
                buffer.writeInt(value[i]);
            }
        }
        return *this;
    }
}

PackEncoder &PackEncoder::putInt64Array(uint8_t index, int64_t *value, int n) {
    if (is_little_end && n > 2) {
        return putByteArray(index, (char *) value, n << 3);
    } else {
        if (value != nullptr && n >= 0) {
            wrapTagAndLength(index, n << 3);
            for (int i = 0; i < n; i++) {
                buffer.writeInt64(value[i]);
            }
        }
        return *this;
    }
}

PackEncoder &PackEncoder::putFloatArray(uint8_t index, float *value, int n) {
    return putIntArray(index, (int *) value, n);
}

PackEncoder &PackEncoder::putDoubleArray(uint8_t index, double *value, int n) {
    return putInt64Array(index, (int64_t *) value, n);
}

#define PUT_ARRAY_PTR(WRAP) \
    int size = value->size();\
    int pTag = wrapObjectArrayTag(index, size);\
    if (pTag < 0) return *this;\
    buffer.position += 4;\
    int pValue = buffer.position;\
    buffer.writeVarint32(size);\
    for (auto &e : *value) {\
        WRAP\
    }\
    putLen(pTag, pValue); \
    return *this;\

#define WRAP_PACK wrapPackable(e);

PackEncoder &PackEncoder::putPackList(uint8_t index, std::list<Packable *> *value) {
    PUT_ARRAY_PTR(WRAP_PACK)
}

PackEncoder &PackEncoder::putPackVector(uint8_t index, std::vector<Packable *> *value) {
    PUT_ARRAY_PTR(WRAP_PACK)
}

#define WRAP_STR_2_PACK wrapString(const_cast<string *>(&(e.first)));\
                        wrapPackable(e.second);

PackEncoder &PackEncoder::putStr2Pack(uint8_t index, std::map<string, Packable *> *value) {
    PUT_ARRAY_PTR(WRAP_STR_2_PACK)
}

#define PUT_ARRAYS(WRAP) int size = value.size();\
     int pTag  = wrapObjectArrayTag(index, size);\
     if(pTag < 0) return *this;\
     buffer.position += 4;\
     int pValue = buffer.position;\
     buffer.writeVarint32(size);\
     for (auto& e : value) {\
         WRAP\
     }\
     putLen(pTag, pValue);\
     return *this;

#define WRAP_STR  wrapString(&e);

PackEncoder &PackEncoder::putStrList(uint8_t index, std::list<string> &value) {
    PUT_ARRAYS(WRAP_STR)
}

PackEncoder &PackEncoder::putStrVector(uint8_t index, std::vector<string> &value) {
    PUT_ARRAYS(WRAP_STR)
}

#define WRAP_STR_2_STR  wrapString(const_cast<string *>(&(e.first)));\
                        wrapString(const_cast<string *>(&(e.second)));

PackEncoder &PackEncoder::putStr2Str(uint8_t index, std::map<string, string> &value) {
    PUT_ARRAYS(WRAP_STR_2_STR)
}

#define WRAP_STR_2_INT  wrapString(const_cast<string *>(&(e.first)));\
                        buffer.writeInt(e.second);

PackEncoder &PackEncoder::putStr2Int(uint8_t index, std::map<string, int> &value) {
    PUT_ARRAYS(WRAP_STR_2_INT)
}

#define WRAP_STR_2_INT64  wrapString(const_cast<string *>(&(e.first)));\
                          buffer.writeInt64(e.second);

PackEncoder &PackEncoder::putStr2Int64(uint8_t index, std::map<string, int64_t> &value) {
    PUT_ARRAYS(WRAP_STR_2_INT64)
}

#define WRAP_STR_2_FLOAT  wrapString(const_cast<string *>(&(e.first)));\
                          buffer.writeFloat(e.second);

PackEncoder &PackEncoder::putStr2Float(uint8_t index, std::map<string, float> &value) {
    PUT_ARRAYS(WRAP_STR_2_FLOAT)
}

#define WRAP_STR_2_DOUBLE  wrapString(const_cast<string *>(&(e.first)));\
                           buffer.writeDouble(e.second);

PackEncoder &PackEncoder::putStr2Double(uint8_t index, std::map<string, double> &value) {
    PUT_ARRAYS(WRAP_STR_2_DOUBLE)
}

#define WRAP_INT_2_INT  buffer.writeInt(e.first);\
                        buffer.writeInt(e.second);

PackEncoder &PackEncoder::putInt2Int(uint8_t index, std::map<int, int> &value) {
    PUT_ARRAYS(WRAP_INT_2_INT)
}

#define WRAP_INT_2_STR  buffer.writeInt(e.first);\
                       wrapString(const_cast<string *>(&(e.second)));

PackEncoder &PackEncoder::putInt2Str(uint8_t index, std::map<int, string> &value) {
    PUT_ARRAYS(WRAP_INT_2_STR)
}

PackEncoder &PackEncoder::putBooleanArray(uint8_t index, const bool *value, int n) {
    CompactCoder::putBooleanArray(*this, index, value, n);
    return *this;
}

PackEncoder &PackEncoder::putEnumArray(uint8_t index, const int *value, int n) {
    CompactCoder::putEnumArray(*this, index, value, n);
    return *this;
}

PackEncoder &PackEncoder::putCompactIntArray(uint8_t index, int *value, int n) {
    CompactCoder::putCompactIntArray(*this, index, value, n);
    return *this;
}

PackEncoder &PackEncoder::putCompactInt64Array(uint8_t index, int64_t *value, int n) {
    CompactCoder::putCompactInt64Array(*this, index, value, n);
    return *this;
}

PackEncoder &PackEncoder::putCompactDoubleArray(uint8_t index, double *value, int n) {
    CompactCoder::putCompactDoubleArray(*this, index, value, n);
    return *this;
}
