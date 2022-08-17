
#ifndef PACK_PACK_ENCODER_H
#define PACK_PACK_ENCODER_H

#include "PackBuffer.h"
#include "TagFormat.h"
#include "PackConfig.h"
#include "CompactCoder.h"

#include <string>
#include <list>
#include <map>
#include <vector>

using namespace std;

static const int BUFFER_DEFAULT_CAPACITY = 4096;

class PackEncoder;

class Packable {
public:
    virtual void encode(PackEncoder &encoder) = 0;
};

class PackEncoder {
public:
    PackEncoder() : buffer(defaultBytes, 0, BUFFER_DEFAULT_CAPACITY) {
    }

    ~PackEncoder() {
        if (buffer.hb != defaultBytes) {
            delete[] buffer.hb;
        }
    }

    static char *marshal(Packable *packable, int &outCount) {
        PackEncoder encoder = PackEncoder();
        packable->encode(encoder);
        return encoder.getBytes(outCount);
    }

    char *getBytes(int &outCount) {
        int n = buffer.position;
        outCount = n;
        char *bytes = new char[n];
        memcpy(bytes, buffer.hb, n);
        return bytes;
    }

    /**
     * To call this function,
     * be sure to access the buffer before the object destroy
     */
    PackBuffer &getBuffer() {
        return buffer;
    }
    
    PackBuffer* putCustom(int index, int len);

    PackEncoder &putByte(uint8_t index, char value);

    PackEncoder &putBool(uint8_t index, bool value);

    PackEncoder &putShort(uint8_t index, short value);

    PackEncoder &putInt(uint8_t index, int value);

    PackEncoder &putInt64(uint8_t index, int64_t value);

    PackEncoder &putFloat(uint8_t index, float value);

    PackEncoder &putDouble(uint8_t index, double value);

    PackEncoder &putString(uint8_t index, string *value);

    PackEncoder &putStringArray(uint8_t index, string **value, int n);

    PackEncoder &putPackable(uint8_t index, Packable *value);

    PackEncoder &putPackableArray(uint8_t index, Packable **value, int n);

    PackEncoder &putByteArray(uint8_t index, char *value, int n);

    PackEncoder &putIntArray(uint8_t index, int *value, int n);

    PackEncoder &putInt64Array(uint8_t index, int64_t *value, int n);

    PackEncoder &putFloatArray(uint8_t index, float *value, int n);

    PackEncoder &putDoubleArray(uint8_t index, double *value, int n);

    PackEncoder &putStrList(uint8_t index, std::list<string> &value);

    PackEncoder &putStrVector(uint8_t index, std::vector<string> &value);

    PackEncoder &putPackList(uint8_t index, std::list<Packable *> *value);

    PackEncoder &putPackVector(uint8_t index, std::vector<Packable *> *value);

    PackEncoder &putStr2Str(uint8_t index, std::map<string, string> &value);

    PackEncoder &putStr2Pack(uint8_t index, std::map<string, Packable *> *value);

    PackEncoder &putStr2Int(uint8_t index, std::map<string, int> &value);

    PackEncoder &putStr2Int64(uint8_t index, std::map<string, int64_t> &value);

    PackEncoder &putStr2Float(uint8_t index, std::map<string, float> &value);

    PackEncoder &putStr2Double(uint8_t index, std::map<string, double> &value);

    PackEncoder &putInt2Int(uint8_t index, std::map<int, int> &value);

    PackEncoder &putInt2Str(uint8_t index, std::map<int, string> &value);

    PackEncoder &putBooleanArray(uint8_t index, const bool *value, int n);

    PackEncoder &putEnumArray(uint8_t index, const int *value, int n);

    PackEncoder &putCompactIntArray(uint8_t index, int *value, int n);

    PackEncoder &putCompactInt64Array(uint8_t index, int64_t *value, int n);

    PackEncoder &putCompactDoubleArray(uint8_t index, double *value, int n);

private:
    friend class CompactCoder;

    static int sMaxAllocated;

    PackBuffer buffer;
    char defaultBytes[BUFFER_DEFAULT_CAPACITY];

    void checkCapacity(int expandSize);

    void putLen(int pKey, int pValue);
    
    void wrapTagAndLength(uint8_t index, int len);

    int wrapObjectArrayTag(uint8_t index, int size);

    void wrapString(string *str);

    void wrapPackable(Packable *pack);

    void putIndex(uint8_t index);
};

#endif
