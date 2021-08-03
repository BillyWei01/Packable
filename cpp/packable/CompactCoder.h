
#ifndef PACK_COMPACTCODER_H
#define PACK_COMPACTCODER_H

#include "PackEncoder.h"
#include "PackDecoder.h"

class PackEncoder;

class PackDecoder;

union ArrayWrapper;

class CompactCoder {
public:
    static void putBooleanArray(PackEncoder &encoder, uint8_t index, const bool *value, int n);

    static bool *getBooleanArray(PackDecoder &decoder, int index, int &outCount);

    static void putEnumArray(PackEncoder &encoder, uint8_t index, const int *value, int n);

    static int *getEnumArray(PackDecoder &decoder, int index, int &outCount);

    static void putCompactIntArray(PackEncoder &encoder, uint8_t index, int *value, int n);

    static int *getCompactIntArray(PackDecoder &decoder, int index, int &outCount);

    static void putCompactInt64Array(PackEncoder &encoder, uint8_t index, int64_t *value, int n);

    static int64_t *getCompactInt64Array(PackDecoder &decoder, int index, int &outCount);

    static void putCompactDoubleArray(PackEncoder &encoder, uint8_t index, double *value, int n);

    static double *getCompactDoubleArray(PackDecoder &decoder, int index, int &outCount);

private:
    static void getNumberArray(PackDecoder &decoder, int index, ArrayWrapper &wrapper, int type, int &outCount);

    static void putNumberArray(PackEncoder &encoder, uint8_t index, ArrayWrapper &wrapper, int type, int n);
};

#endif //PACK_COMPACTCODER_H
