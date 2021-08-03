
#include "PackVo.h"

void Category::encode(PackEncoder &encoder) {
    encoder.putString(0, &name)
            .putInt(1, level)
            .putInt64(2, i_column)
            .putCDouble(3, d_column)
            .putString(4, des)
            .putPackableArray(5, (Packable **) sub_category, subCount);
}

Category *Category::decode(PackDecoder &decoder) {
    Category *c = new Category();
    // c->name = decoder.getString(0);
    decoder.getString(0, c->name);
    c->level = decoder.getInt(1);
    c->i_column = decoder.getInt64(2);
    c->d_column = decoder.getCDouble(3);
    c->des = decoder.getStringPtr(4);
    c->sub_category = decoder.getPackableArray(5, Category::decode, c->subCount);
    return c;
}

void Data::encode(PackEncoder &encoder) {
    encoder.putBool(0, d_bool)
            .putFloat(1, d_float)
            .putDouble(2, d_double)
            .putString(3, &string_1)
            .putInt(4, int_1)
            .putInt(5, int_2)
            .putInt(6, int_3)
            .putSInt(7, int_4)
            .putInt(8, int_5)
            .putInt64(9, long_1)
            .putInt64(10, long_2)
            .putInt64(11, long_3)
            .putSInt64(12, long_4)
            .putInt64(13, long_5)
            .putPackable(14, d_category)
            .putBooleanArray(15, bool_array, boolCount)
            .putIntArray(16, int_array, intCount)
            .putInt64Array(17, long_array, longCount)
            .putFloatArray(18, float_array, floatCount)
            .putDoubleArray(19, double_array, doubleCount)
            .putStringArray(20, string_array, stringCount);
}

Data *Data::decode(PackDecoder &decoder) {
    Data *d = new Data();
    d->d_bool = decoder.getBool(0);
    d->d_float = decoder.getFloat(1);
    d->d_double = decoder.getDouble(2);
    d->string_1 = decoder.getString(3);
    d->int_1 = decoder.getInt(4);
    d->int_2 = decoder.getInt(5);
    d->int_3 = decoder.getInt(6);
    d->int_4 = decoder.getSInt(7);
    d->int_5 = decoder.getInt(8);
    d->long_1 = decoder.getInt64(9);
    d->long_2 = decoder.getInt64(10);
    d->long_3 = decoder.getInt64(11);
    d->long_4 = decoder.getSInt64(12);
    d->long_5 = decoder.getInt64(13);
    d->d_category = decoder.getPackable(14, Category::decode);
    d->bool_array = decoder.getBooleanArray(15, d->boolCount);
    d->int_array = decoder.getIntArray(16, d->intCount);
    d->long_array = decoder.getLongArray(17, d->longCount);
    d->float_array = decoder.getFloatArray(18, d->floatCount);
    d->double_array = decoder.getDoubleArray(19, d->doubleCount);
    d->string_array = decoder.getStringArray(20, d->stringCount);
    return d;
}

void Response::encode(PackEncoder &encoder) {
    encoder.putInt(0, code)
            .putString(1, &detail)
            .putPackableArray(2, (Packable **) data, dataCount);
}

Response *Response::decode(PackDecoder &decoder) {
    Response *r = new Response();
    r->code = Result(decoder.getInt(0));
    // both method is ok
    // decoder.getString(1, r->detail);
    r->detail = decoder.getString(1);
    r->data = decoder.getPackableArray(2, Data::decode, r->dataCount);
    return r;
}
