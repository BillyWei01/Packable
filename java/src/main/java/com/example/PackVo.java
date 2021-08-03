package com.example;

import io.packable.*;
import java.util.*;

public class PackVo {
    public enum Result {
        SUCCESS, FAILED_1, FAILED_2, FAILED_3;
        static final Result[] ARRAY = new Result[]{ SUCCESS, FAILED_1, FAILED_2, FAILED_3 };
    }

    public static class Category implements Packable {
        String name;
        int level;
        long i_column;
        double d_column;
        String des;
        Category[] sub_category;

        @Override
        public void encode(PackEncoder encoder) {
            encoder.putString(0, name)
                    .putInt(1, level)
                    .putLong(2, i_column)
                    .putCDouble(3, d_column)
                    .putString(4, des)
                    .putPackableArray(5, sub_category);
        }

        public static final PackArrayCreator<Category> CREATOR = new PackArrayCreator<Category>() {
            @Override
            public Category decode(PackDecoder decoder) {
                Category c = new Category();
                c.name = decoder.getString(0);
                c.level = decoder.getInt(1);
                c.i_column = decoder.getLong(2);
                c.d_column = decoder.getCDouble(3);
                c.des = decoder.getString(4);
                c.sub_category = decoder.getPackableArray(5, CREATOR);
                return c;
            }

            @Override
            public Category[] newArray(int size) {
                return new Category[size];
            }
        };

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Category)) return false;
            Category category = (Category) o;
            return level == category.level &&
                    i_column == category.i_column &&
                    Double.compare(category.d_column, d_column) == 0 &&
                    Objects.equals(name, category.name) &&
                    Objects.equals(des, category.des) &&
                    Arrays.equals(sub_category, category.sub_category);
        }
    }

    public static class Data implements Packable {
        boolean d_bool;
        float d_float;
        double d_double;
        String string_1;
        int int_1;
        int int_2;
        int int_3;
        int int_4;
        int int_5;
        long long_1;
        long long_2;
        long long_3;
        long long_4;
        long long_5;
        Category d_category;
        boolean[] bool_array;
        int[] int_array;
        long[] long_array;
        float[] float_array;
        double[] double_array;
        String[] string_array;

        @Override
        public void encode(PackEncoder encoder) {
            encoder.putBoolean(0, d_bool)
                    .putFloat(1, d_float)
                    .putDouble(2, d_double)
                    .putString(3, string_1)
                    .putInt(4, int_1)
                    .putInt(5, int_2)
                    .putInt(6, int_3)
                    .putSInt(7, int_4)
                    .putInt(8, int_5)
                    .putLong(9, long_1)
                    .putLong(10, long_2)
                    .putLong(11, long_3)
                    .putSLong(12, long_4)
                    .putLong(13, long_5)
                    .putPackable(14, d_category)
                    .putBooleanArray(15, bool_array)
                    .putIntArray(16, int_array)
                    .putLongArray(17, long_array)
                    .putFloatArray(18, float_array)
                    .putDoubleArray(19, double_array)
                    .putStringArray(20, string_array);
        }

        public static final PackArrayCreator<Data> CREATOR = new PackArrayCreator<Data>() {
            @Override
            public Data decode(PackDecoder decoder) {
                Data d = new Data();
                d.d_bool = decoder.getBoolean(0);
                d.d_float = decoder.getFloat(1);
                d.d_double = decoder.getDouble(2);
                d.string_1 = decoder.getString(3);
                d.int_1 = decoder.getInt(4);
                d.int_2 = decoder.getInt(5);
                d.int_3 = decoder.getInt(6);
                d.int_4 = decoder.getSInt(7);
                d.int_5 = decoder.getInt(8);
                d.long_1 = decoder.getLong(9);
                d.long_2 = decoder.getLong(10);
                d.long_3 = decoder.getLong(11);
                d.long_4 = decoder.getSLong(12);
                d.long_5 = decoder.getLong(13);
                d.d_category = decoder.getPackable(14, Category.CREATOR);
                d.bool_array = decoder.getBooleanArray(15);
                d.int_array = decoder.getIntArray(16);
                d.long_array = decoder.getLongArray(17);
                d.float_array = decoder.getFloatArray(18);
                d.double_array = decoder.getDoubleArray(19);
                d.string_array = decoder.getStringArray(20);
                return d;
            }

            @Override
            public Data[] newArray(int size) {
                return new Data[size];
            }
        };

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Data)) return false;
            Data data = (Data) o;
            return d_bool == data.d_bool &&
                    Float.compare(data.d_float, d_float) == 0 &&
                    Double.compare(data.d_double, d_double) == 0 &&
                    int_1 == data.int_1 &&
                    int_2 == data.int_2 &&
                    int_3 == data.int_3 &&
                    int_4 == data.int_4 &&
                    int_5 == data.int_5 &&
                    long_1 == data.long_1 &&
                    long_2 == data.long_2 &&
                    long_3 == data.long_3 &&
                    long_4 == data.long_4 &&
                    long_5 == data.long_5 &&
                    Objects.equals(string_1, data.string_1) &&
                    Objects.equals(d_category, data.d_category) &&
                    Arrays.equals(bool_array, data.bool_array) &&
                    Arrays.equals(int_array, data.int_array) &&
                    Arrays.equals(long_array, data.long_array) &&
                    Arrays.equals(float_array, data.float_array) &&
                    Arrays.equals(double_array, data.double_array) &&
                    Arrays.equals(string_array, data.string_array);
        }
    }

    public static class Response implements Packable {
        Result code;
        String detail;
        Data[] data;

        @Override
        public void encode(PackEncoder encoder) {
            encoder.putInt(0, code.ordinal())
                    .putString(1, detail)
                    .putPackableArray(2, data);
        }

        public static final PackCreator<Response> CREATOR = new PackCreator<Response>() {
            @Override
            public Response decode(PackDecoder decoder) {
                Response r = new Response();
                r.code = Result.ARRAY[decoder.getInt(0)];
                r.detail = decoder.getString(1);
                r.data = decoder.getPackableArray(2, Data.CREATOR);
                return r;
            }
        };

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Response)) return false;
            Response response = (Response) o;
            return code == response.code &&
                    Objects.equals(detail, response.detail) &&
                    Arrays.equals(data, response.data);
        }
    }
}
