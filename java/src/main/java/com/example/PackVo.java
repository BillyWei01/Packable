package com.example;

import java.util.*;

public class PackVo {
    public enum Result {
        SUCCESS, FAILED_1, FAILED_2, FAILED_3;
        static final Result[] ARRAY = new Result[]{ SUCCESS, FAILED_1, FAILED_2, FAILED_3 };
    }

    public static class Category  {
        String name;
        int level;
        long i_column;
        double d_column;
        String des;
        Category[] sub_category;

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

    public static class Data {
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

    public static class Response {
        Result code;
        String detail;
        Data[] data;

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
