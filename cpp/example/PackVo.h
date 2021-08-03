

#ifndef PACK_PACKVO_H
#define PACK_PACKVO_H

#include "../packable/PackEncoder.h"
#include "../packable/PackDecoder.h"

#include <string>

using namespace std;

enum Result {
    SUCCESS,
    FAILED_1,
    FAILED_2,
    FAILED_3
};

class Category : public Packable {
public:
    ~Category() {
        if (des != nullptr) {
            delete des;
        }
        if (sub_category != nullptr) {
            for (int i = 0; i < subCount; i++) {
                delete sub_category[i];
            }
            delete[] sub_category;
        }
    }

    string name;
    int level;
    long i_column;
    double d_column;
    string *des;

    int subCount;
    Category **sub_category;

    void encode(PackEncoder &encoder);

    static Category *decode(PackDecoder &decoder);
};

class Data : public Packable {
public:
    ~Data() {
        if (d_category != nullptr) {
            delete d_category;
        }
        if (bool_array != nullptr) {
            delete[] bool_array;
        }
        if (int_array != nullptr) {
            delete[] int_array;
        }
        if (long_array != nullptr) {
            delete[] long_array;
        }
        if (float_array != nullptr) {
            delete[] float_array;
        }
        if (double_array != nullptr) {
            delete[] double_array;
        }
        if (string_array != nullptr) {
            for (int i = 0; i < stringCount; i++) {
                delete string_array[i];
            }
            delete[] string_array;
        }
    }

    bool d_bool;
    float d_float;
    double d_double;

    string string_1;

    int int_1;
    int int_2;
    int int_3;
    int int_4;
    int int_5;

    int64_t long_1;
    int64_t long_2;
    int64_t long_3;
    int64_t long_4;
    int64_t long_5;

    Category *d_category;

    int boolCount;
    bool *bool_array;

    int intCount;
    int *int_array;

    int longCount;
    int64_t *long_array;

    int floatCount;
    float *float_array;

    int doubleCount;
    double *double_array;

    int stringCount;
    string **string_array;

    void encode(PackEncoder &encoder);

    static Data *decode(PackDecoder &decoder);
};

class Response : public Packable {
public:
    ~Response() {
        if (data != nullptr) {
            for (int i = 0; i < dataCount; i++) {
                delete data[i];
            }
            delete[] data;
        }
    }

    Result code;
    string detail;

    int dataCount;
    Data **data;

    void encode(PackEncoder &encoder);

    static Response *decode(PackDecoder &decoder);
};


#endif
