

#include "PackDecoder.h"

#define throw_invalid_array_length  throw "invalid array length";

using namespace TagFormat;

PackDecoder::PackDecoder(char *bytes, int offset, int len) :
        infoArray(nullptr),
        infoLength(0),
        maxIndex(-1),
        buffer(bytes, offset, len) {
    if (len > PackConfig::MAX_BUFFER_SIZE) {
        throw "buffer size over limit";
    }
}

void PackDecoder::parseBuffer() {
    uint64_t existFlag = 0;
    uint64_t *existFlags = nullptr;

    if (infoArray == nullptr) {
        infoArray = defaultInfoArray;
        infoLength = DEFAULT_INFO_LEN;
    }

    while (buffer.hasRemaining()) {
        char tag = buffer.readByte();
        int index = (tag & BIG_INDEX_MASK) == 0 ? tag & INDEX_MASK : buffer.readByte() & 0xff;
        if (index > maxIndex) {
            maxIndex = index;
        }
        if (index < 64) {
            existFlag |= 1 << index;
        } else {
            if (existFlags == nullptr) {
                existFlags = new uint64_t[4];
                for(int i = 0; i < 4; i++){
                    existFlags[i] = 0;
                }
            }
            existFlags[index >> 8] |= 1 << (index & 0x3f);
        }

        if (index >= infoLength) {
            int64_t *oldArray = infoArray;
            int newLength = infoLength << 1;
            while (index >= newLength) {
                newLength = newLength << 1;
            }
            infoArray = new int64_t[newLength];
            memcpy(infoArray, oldArray, infoLength << 3);
            infoLength = newLength;
            if (oldArray != defaultInfoArray) {
                delete[] oldArray;
            }
        }

        char type = tag & TYPE_MASK;
        if (type <= TYPE_NUM_64) {
            if (type == TYPE_0) {
                infoArray[index] = 0L;
            } else if (type == TYPE_NUM_8) {
                infoArray[index] = (uint8_t) buffer.readByte();
            } else if (type == TYPE_NUM_16) {
                infoArray[index] = (uint16_t) buffer.readShort();
            } else if (type == TYPE_NUM_32) {
                infoArray[index] = (uint32_t) buffer.readInt();
            } else {
                // In case of not able to tell value missing (which infoArray[index] = NULL_FLAG) or value = NULL_FLAG,
                // We use the highest bit of int64_t value to indicate that the infoArray[index]
                // is actually value (when positive) or position of value (mask highest bit to be one 1)
                int64_t x = buffer.readInt64();
                if ((x & INT_64_MIN_VALUE) == 0) {
                    infoArray[index] = x;
                } else {
                    infoArray[index] = (buffer.position - 8) | INT_64_MIN_VALUE;
                }
            }
        } else {
            int size;
            if (type == TYPE_VAR_8) {
                size = (uint8_t) buffer.readByte();
            } else if (type == TYPE_VAR_16) {
                size = (uint16_t) buffer.readShort();
            } else {
                size = buffer.readInt();
            }
            infoArray[index] = (((int64_t) buffer.position) << 32) | (int64_t) size;
            buffer.position += size;
        }
    }

    // should be equal
    if (buffer.position != buffer.limit) {
        throw "invalid pack data";
    }

    if (maxIndex <= 0){
        return;
    }

    int bits = 63 - maxIndex;
    uint64_t flippedFlag = (~existFlag) << bits;
    if (flippedFlag == 0) {
        return;
    }
    flippedFlag >>= bits;
    int64_t *pInfo = infoArray;
    do {
        if ((flippedFlag & 1) != 0) {
            *pInfo = NULL_FLAG;
        }
        pInfo++;
        flippedFlag >>= 1;
    } while (flippedFlag != 0);

    if (maxIndex >= 64) {
        for (int i = 64; i < maxIndex; i++) {
            if ((existFlags[i >> 8] & (1 << (i & 0x3F))) == 0) {
                infoArray[i] = NULL_FLAG;
            }
        }
        delete[] existFlags;
    }
}

int64_t PackDecoder::getInfo(int index) {
    if (maxIndex < 0) {
        parseBuffer();
    }
    if (index > maxIndex) {
        return NULL_FLAG;
    }
    return infoArray[index];
}

PackBuffer* PackDecoder::getCustom(int index){
    long info = getInfo(index);
    if (info == NULL_FLAG) {
        return nullptr;
    }
    int len = (int) (info & INT_MASK);
    buffer.position = (len == 0) ? buffer.limit : (int) (((uint64_t) info) >> 32);
    return &buffer;
}

bool PackDecoder::contains(int index) {
    return getInfo(index) != NULL_FLAG;
}

bool PackDecoder::getBool(int index, bool defValue) {
    int64_t info = getInfo(index);
    return info == NULL_FLAG ? defValue : info == 1;
}

bool PackDecoder::getBool(int index) {
    return getInfo(index) == 1;
}

char PackDecoder::getByte(int index, char defValue) {
    int64_t info = getInfo(index);
    return info == NULL_FLAG ? defValue : (char) info;
}

char PackDecoder::getByte(int index) {
    return getByte(index, 0);
}

short PackDecoder::getShort(int index, short defValue) {
    int64_t info = getInfo(index);
    return info == NULL_FLAG ? defValue : (short) info;
}

short PackDecoder::getShort(int index) {
    return getShort(index, 0);
}

int PackDecoder::getInt(int index, int defValue) {
    int64_t info = getInfo(index);
    return info == NULL_FLAG ? defValue : (int) info;
}

int PackDecoder::getInt(int index) {
    return getInt(index, 0);
}

int PackDecoder::getSInt(int index, int defValue) {
    int64_t info = getInfo(index);
    if (info == NULL_FLAG) {
        return defValue;
    }
    auto n = (int32_t) info;
    return (((uint32_t) n) >> 1) ^ -(n & 1);
}

int PackDecoder::getSInt(int index) {
    return getSInt(index, 0);
}

int64_t PackDecoder::getInt64(int index, int64_t defValue) {
    int64_t info = getInfo(index);
    if (info == NULL_FLAG) {
        return defValue;
    }
    return info >= 0 ? info : buffer.readInt64((int) (info & INT_MASK));
}

int64_t PackDecoder::getInt64(int index) {
    return getInt64(index, 0);
}

int64_t PackDecoder::getSInt64(int index, int64_t defValue) {
    int64_t info = getInfo(index);
    if (info == NULL_FLAG) {
        return defValue;
    }
    int64_t n = info >= 0 ? info : buffer.readInt64((int) (info & INT_MASK));
    return (((uint64_t) n) >> 1) ^ -(n & 1);
}

int64_t PackDecoder::getSInt64(int index) {
    return getSInt64(index, 0);
}

float PackDecoder::getFloat(int index, float defValue) {
    int64_t info = getInfo(index);
    if (info == NULL_FLAG) {
        return defValue;
    }
    int x = (int) info;
    return *((float *) &x);
}

float PackDecoder::getFloat(int index) {
    return getFloat(index, 0);
}

double PackDecoder::getDouble(int index, double defValue) {
    int64_t info = getInfo(index);
    if (info == NULL_FLAG) {
        return defValue;
    }
    int64_t x = info >= 0 ? info : buffer.readInt64((int) (info & INT_MASK));
    return *((double *) &x);
}

double PackDecoder::getDouble(int index) {
    return getDouble(index, 0);
}

double PackDecoder::getCDouble(int index, double defValue) {
    int64_t info = getInfo(index);
    if (info == NULL_FLAG) {
        return defValue;
    }
    uint64_t x = info >= 0 ? info : buffer.readInt64((int) (info & INT_MASK));
    uint64_t y = (x << 32) | (x >> 32);
    return *((double *) &y);
}

double PackDecoder::getCDouble(int index) {
    return getCDouble(index, 0);
}

string PackDecoder::getString(int index) {
    int64_t info = getInfo(index);
    if (info == NULL_FLAG) {
        return "";
    }
    int len = (int) (info & INT_MASK);
    if (len == 0) {
        return "";
    }
    int offset = ((uint64_t) info) >> 32;
    return string(buffer.hb + offset, len);
}

void PackDecoder::getString(int index, string &out) {
    int64_t info = getInfo(index);
    if (info == NULL_FLAG) {
        return;
    }

    if (!out.empty()) {
        out.clear();
    }
    int len = (int) (info & INT_MASK);
    if (len == 0) {
        return;
    }
    int offset = ((uint64_t) info) >> 32;
    out.append(buffer.hb + offset, len);
}

string *PackDecoder::getStringPtr(int index) {
    int64_t info = getInfo(index);
    if (info == NULL_FLAG) {
        return nullptr;
    }
    int len = (int) (info & INT_MASK);
    if (len == 0) {
        return new string();
    }
    int offset = ((uint64_t) info) >> 32;
    return new string(buffer.hb + offset, len);
}

string **PackDecoder::getStringArray(int index, int &outCount) {
    int n = getSize(index);
    if (n < 0) {
        outCount = 0;
        return nullptr;
    }
    outCount = n;
    string **value = new string *[n];
    for (int i = 0; i < n; i++) {
        value[i] = takeStringPtr();
    }
    return value;
}

string *PackDecoder::takeStringPtr() {
    int len = buffer.readVarint32();
    if (len < 0) {
        return nullptr;
    } else {
        if (len == 0) {
            return new string();
        }
        int offset = buffer.position;
        buffer.checkBound(offset, len);
        auto *str = new string(buffer.hb + offset, len);
        buffer.position += len;
        return str;
    }
}

string PackDecoder::takeString() {
    int len = buffer.readVarint32();
    if (len < 0) {
        return string();
    } else {
        int offset = buffer.position;
        buffer.checkBound(offset, len);
        string str = len != 0 ? string(buffer.hb + offset, len) : string();
        buffer.position += len;
        return str;
    }
}

PackDecoder *PackDecoder::getDecoder(int index) {
    int64_t info = getInfo(index);
    if (info == NULL_FLAG) {
        return nullptr;
    }
    int offset = ((uint64_t) info) >> 32;
    int len = (int) (info & INT_MASK);
    return new PackDecoder(buffer.hb, offset, len);
}

DecoderArray *PackDecoder::getDecoderArray(int index, int &outCount) {
    int n = getSize(index);
    if (n < 0) {
        outCount = 0;
        return nullptr;
    }
    outCount = n;
    return new DecoderArray(buffer, n);
}


char *PackDecoder::getByteArray(int index, int &outCount) {
    int64_t info = getInfo(index);
    if (info == NULL_FLAG) {
        outCount = 0;
        return nullptr;
    }
    buffer.position = ((uint64_t) info) >> 32;
    int len = (int) (info & INT_MASK);
    outCount = len;
    char *bytes = new char[len];
    buffer.readBytes(bytes, len);
    return bytes;
}

int *PackDecoder::getIntArray(int index, int &outCount) {
    int64_t info = getInfo(index);
    if (info == NULL_FLAG) {
        outCount = 0;
        return nullptr;
    }

    buffer.position = ((uint64_t) info) >> 32;
    int len = (int) (info & INT_MASK);
    if ((len & 0x3) != 0) {
        throw_invalid_array_length
    }
    int n = len >> 2;
    outCount = n;
    int *value = new int[n];
    if (is_little_end && n >= 4) {
        memcpy(value, buffer.hb + buffer.position, len);
        buffer.position += len;
    } else {
        for (int i = 0; i < n; i++) {
            value[i] = buffer.readInt();
        }
    }
    return value;
}

int64_t *PackDecoder::getLongArray(int index, int &outCount) {
    int64_t info = getInfo(index);
    if (info == NULL_FLAG) {
        outCount = 0;
        return nullptr;
    }
    buffer.position = ((uint64_t) info) >> 32;
    int len = (int) (info & INT_MASK);
    if ((len & 0x7) != 0) {
        throw_invalid_array_length
    }
    int n = len >> 3;
    outCount = n;
    int64_t *value = new int64_t[n];
    if (is_little_end && n >= 2) {
        memcpy(value, buffer.hb + buffer.position, len);
        buffer.position += len;
    } else {
        for (int i = 0; i < n; i++) {
            value[i] = buffer.readInt64();
        }
    }
    return value;
}

float *PackDecoder::getFloatArray(int index, int &outCount) {
    return (float *) getIntArray(index, outCount);
}

double *PackDecoder::getDoubleArray(int index, int &outCount) {
    return (double *) getLongArray(index, outCount);
}

int PackDecoder::getSize(int index) {
    int64_t info = getInfo(index);
    if (info == NULL_FLAG) {
        return -1;
    }
    int total = (int) (info & INT_MASK);
    if (total == 0) {
        return 0;
    }
    buffer.position = ((uint64_t) info) >> 32;
    int n = buffer.readVarint32();
    if (n < 0 || n > PackConfig::MAX_OBJECT_ARRAY_SIZE) {
        throw "invalid size of object array";
    }
    return n;
}

#define GET_CONTAINER_VALUE     int n = getSize(index);\
                                if (n < 0) return;\
                                if (!value.empty()) value.clear();\
                                for (int i = 0; i < n; i++)\


void PackDecoder::getStrVector(int index, std::vector<string> &value) {
    GET_CONTAINER_VALUE value.push_back(takeString());
}

void PackDecoder::getStr2Str(int index, map<string, string> &value) {
    GET_CONTAINER_VALUE value.insert(std::pair<string, string>(takeString(), takeString()));
}

void PackDecoder::getStr2Int(int index, map<string, int> &value) {
    GET_CONTAINER_VALUE value.insert(std::pair<string, int>(takeString(), buffer.readInt()));
}

void PackDecoder::getStr2Int64(int index, map<string, int64_t> &value) {
    GET_CONTAINER_VALUE value.insert(std::pair<string, int64_t>(takeString(), buffer.readInt64()));
}

void PackDecoder::getStr2Float(int index, map<string, float> &value) {
    GET_CONTAINER_VALUE value.insert(std::pair<string, float>(takeString(), buffer.readFloat()));
}

void PackDecoder::getStr2Double(int index, map<string, double> &value) {
    GET_CONTAINER_VALUE value.insert(std::pair<string, double>(takeString(), buffer.readDouble()));
}

void PackDecoder::getInt2Int(int index, map<int, int> &value) {
    GET_CONTAINER_VALUE value.insert(std::pair<int, int>(buffer.readInt(), buffer.readInt()));
}

void PackDecoder::getInt2Str(int index, map<int, string> &value) {
    GET_CONTAINER_VALUE value.insert(std::pair<int, string>(buffer.readInt(), takeString()));
}

bool *PackDecoder::getBooleanArray(int index, int &outCount) {
    return CompactCoder::getBooleanArray(*this, index, outCount);
}

int *PackDecoder::getEnumArray(int index, int &outCount) {
    return CompactCoder::getEnumArray(*this, index, outCount);
}

int *PackDecoder::getCompactIntArray(int index, int &outCount) {
    return CompactCoder::getCompactIntArray(*this, index, outCount);
}

int64_t *PackDecoder::getCompactInt64Array(int index, int &outCount) {
    return CompactCoder::getCompactInt64Array(*this, index, outCount);
}

double *PackDecoder::getCompactDoubleArray(int index, int &outCount) {
    return CompactCoder::getCompactDoubleArray(*this, index, outCount);
}
