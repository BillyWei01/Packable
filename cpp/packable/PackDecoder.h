
#ifndef PACK_PACKDECODER_H
#define PACK_PACKDECODER_H

#include "PackBuffer.h"
#include "TagFormat.h"
#include "CompactCoder.h"
#include "PackConfig.h"

#include <string>
#include <map>
#include <vector>

using namespace std;

class DecoderArray;

class PackDecoder {
public:
    PackDecoder(char *bytes, int offset, int len);

    ~PackDecoder() {
        if (infoArray != nullptr && infoArray != defaultInfoArray) {
            delete[] infoArray;
        }
    }

    template<typename T>
    static T *unmarshal(char *bytes, int count, T *(*decode)(PackDecoder &decoder)) {
        PackDecoder decoder = PackDecoder(bytes, 0, count);
        return decode(decoder);
    }
    
    PackBuffer* getCustom(int index);

    bool getBool(int index, bool defValue);

    bool getBool(int index);

    char getByte(int index, char defValue);

    char getByte(int index);

    short getShort(int index, short defValue);

    short getShort(int index);

    int getInt(int index, int defValue);

    int getInt(int index);

    int getSInt(int index, int defValue);

    int getSInt(int index);

    float getFloat(int index, float defValue);

    float getFloat(int index);

    int64_t getInt64(int index, int64_t defValue);

    int64_t getInt64(int index);

    int64_t getSInt64(int index, int64_t defValue);

    int64_t getSInt64(int index);

    double getDouble(int index, double defValue);

    double getDouble(int index);

    double getCDouble(int index, double defValue);

    double getCDouble(int index);

    string getString(int index);

    void getString(int index, string &out);

    string *getStringPtr(int index);

    string **getStringArray(int index, int &outCount);

    string *takeStringPtr();

    string takeString();

    template<typename T>
    T *getPackable(int index, T *(*decode)(PackDecoder &decoder)) {
        int64_t info = getInfo(index);
        if (info == NULL_FLAG) {
            return nullptr;
        }
        int offset = ((uint64_t) info) >> 32;
        int len = (int) (info & INT_MASK);
        PackDecoder decoder = PackDecoder(buffer.hb, offset, len);
        return decode(decoder);
    }

    template<typename T>
    T **getPackableArray(int index, T *(*decode)(PackDecoder &decoder), int &outCount) {
        int n = getSize(index);
        if (n < 0) {
            outCount = 0;
            return nullptr;
        }
        outCount = n;
        T **value = new T *[n];
        for (int i = 0; i < n; i++) {
            value[i] = takePackable(decode);
        }
        return value;
    }

    template<typename T>
    T *takePackable(T *(*decode)(PackDecoder &decoder)) {
        short a = buffer.readShort();
        if (a == PackConfig::NULL_PACKABLE) {
            return nullptr;
        } else {
            int len = a >= 0 ? a : ((a & 0x7fff) << 16) | (buffer.readShort() & 0xffff);
            int offset = buffer.position;
            buffer.checkBound(offset, len);
            PackDecoder decoder = PackDecoder(buffer.hb, offset, len);
            T *pack = decode(decoder);
            buffer.position += len;
            return pack;
        }
    }

    PackDecoder *getDecoder(int index);

    DecoderArray *getDecoderArray(int index, int &outCount);

    char *getByteArray(int index, int &outCount);

    int *getIntArray(int index, int &outCount);

    int64_t *getLongArray(int index, int &outCount);

    float *getFloatArray(int index, int &outCount);

    double *getDoubleArray(int index, int &outCount);

    bool contains(int index);

    void getStrVector(int index, std::vector<string> &value);

    /**
     * Get pointers to vector.
     * The elements of vector are pointers of object,
     * be sure to delete the objects after using.
     */
    template<typename T>
    void getPackPtrVector(int index, std::vector<T *> &value,
                          T *(*decode)(PackDecoder &decoder)) {
        int n = getSize(index);
        if (n < 0) return;
        if (!value.empty()) value.clear();
        for (int i = 0; i < n; i++) {
            value.push_back(takePackable(decode));
        }
    }

    void getStr2Str(int index, map<string, string> &value);

    /**
     * Get map of string to pointer.
     * The values of map are pointers of object,
     * be sure to delete the objects after using.
     */
    template<typename T>
    void getStr2PackPtr(int index, map<string, T *> &value,
                        T *(*decode)(PackDecoder &decoder)) {
        int n = getSize(index);
        if (n < 0) return;
        if (!value.empty()) value.clear();
        for (int i = 0; i < n; i++) {
            value.insert(pair<string, T *>(takeString(), takePackable(decode)));
        }
    }

    void getStr2Int(int index, map<string, int> &value);

    void getStr2Int64(int index, map<string, int64_t> &value);

    void getStr2Float(int index, map<string, float> &value);

    void getStr2Double(int index, map<string, double> &value);

    void getInt2Int(int index, map<int, int> &value);

    void getInt2Str(int index, map<int, string> &value);

    bool *getBooleanArray(int index, int &outCount);

    int *getEnumArray(int index, int &outCount);

    int *getCompactIntArray(int index, int &outCount);

    int64_t *getCompactInt64Array(int index, int &outCount);

    double *getCompactDoubleArray(int index, int &outCount);

private:
    friend class CompactCoder;
    friend class DecoderArray;

    static const int64_t NULL_FLAG = ~0L;

    static const int64_t INT_64_MIN_VALUE = 0x8000000000000000L;
    static const int64_t INT_MASK = 0xffffffffL;
    static const int DEFAULT_INFO_LEN = 32;


    int64_t *infoArray;
    int infoLength;
    int maxIndex;
    PackBuffer buffer;
    int64_t defaultInfoArray[DEFAULT_INFO_LEN];

    void parseBuffer();

    int64_t getInfo(int index);

    int getSize(int index);
};

class DecoderArray {
public:
    DecoderArray(PackBuffer &parentBuffer, int n) :
            count(n),
            index(0),
            decoderPtr(nullptr),
            buffer(parentBuffer) {
    }

    ~DecoderArray() {
        if (decoderPtr != nullptr) {
            delete decoderPtr;
        }
    }

    int getCount() {
        return count;
    }

    bool hasNext() {
        return index < count;
    }

    PackDecoder *next() {
        if (index >= count) {
            return nullptr;
        }
        index++;
        short a = buffer.readShort();
        if (a == PackConfig::NULL_PACKABLE) {
            return nullptr;
        } else {
            int len = a >= 0 ? a : ((a & 0x7fff) << 16) | (buffer.readShort() & 0xffff);
            int offset = buffer.position;
            buffer.checkBound(offset, len);
            if (decoderPtr == nullptr) {
                decoderPtr = new PackDecoder(buffer.hb, offset, len);
            } else {
                decoderPtr->buffer.position = offset;
                decoderPtr->buffer.limit = offset + len;
                decoderPtr->maxIndex = -1;
            }
            buffer.position += len;
            return decoderPtr;
        }
    }

private:
    int count;
    int index;
    PackDecoder *decoderPtr;
    PackBuffer &buffer;
};

#endif
