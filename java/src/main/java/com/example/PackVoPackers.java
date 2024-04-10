package com.example;

import io.packable.PackDecoder;
import io.packable.PackEncoder;
import io.packable.Packer;

public class PackVoPackers {
    public static final Packer<PackVo.Category> CATEGORY_PACKER = new Packer<PackVo.Category>() {
        @Override
        public void pack(PackEncoder encoder, PackVo.Category target) {
            encoder.putString(0, target.name)
                    .putInt(1, target.level)
                    .putLong(2, target.i_column)
                    .putDouble(3, target.d_column)
                    .putString(4, target.des)
                    .putObjectArray(5, target.sub_category, CATEGORY_PACKER);
        }

        @Override
        public PackVo.Category unpack(PackDecoder decoder) {
            PackVo.Category c = new PackVo.Category();
            c.name = decoder.getString(0);
            c.level = decoder.getInt(1);
            c.i_column = decoder.getLong(2);
            c.d_column = decoder.getDouble(3);
            c.des = decoder.getString(4);
            c.sub_category = decoder.getObjectArray(5, CATEGORY_PACKER, new PackVo.Category[0]);
            return c;
        }
    };

    public static final Packer<PackVo.Data> DATA_PACKER = new Packer<PackVo.Data>() {
        @Override
        public void pack(PackEncoder encoder, PackVo.Data target) {
            encoder.putBoolean(0, target.d_bool)
                    .putFloat(1, target.d_float)
                    .putDouble(2, target.d_double)
                    .putString(3, target.string_1)
                    .putInt(4, target.int_1)
                    .putInt(5, target.int_2)
                    .putInt(6, target.int_3)
                    .putInt(7, target.int_4)
                    .putInt(8, target.int_5)
                    .putLong(9, target.long_1)
                    .putLong(10, target.long_2)
                    .putLong(11, target.long_3)
                    .putLong(12, target.long_4)
                    .putLong(13, target.long_5)
                    .putObject(14, target.d_category, CATEGORY_PACKER)
                    .putBooleanArray(15, target.bool_array)
                    .putIntArray(16, target.int_array)
                    .putLongArray(17, target.long_array)
                    .putFloatArray(18, target.float_array)
                    .putDoubleArray(19, target.double_array)
                    .putStringArray(20, target.string_array);
        }

        @Override
        public PackVo.Data unpack(PackDecoder decoder) {
            PackVo.Data d = new PackVo.Data();
            d.d_bool = decoder.getBoolean(0);
            d.d_float = decoder.getFloat(1);
            d.d_double = decoder.getDouble(2);
            d.string_1 = decoder.getString(3);
            d.int_1 = decoder.getInt(4);
            d.int_2 = decoder.getInt(5);
            d.int_3 = decoder.getInt(6);
            d.int_4 = decoder.getInt(7);
            d.int_5 = decoder.getInt(8);
            d.long_1 = decoder.getLong(9);
            d.long_2 = decoder.getLong(10);
            d.long_3 = decoder.getLong(11);
            d.long_4 = decoder.getLong(12);
            d.long_5 = decoder.getLong(13);
            d.d_category = decoder.getObject(14, CATEGORY_PACKER);
            d.bool_array = decoder.getBooleanArray(15);
            d.int_array = decoder.getIntArray(16);
            d.long_array = decoder.getLongArray(17);
            d.float_array = decoder.getFloatArray(18);
            d.double_array = decoder.getDoubleArray(19);
            d.string_array = decoder.getStringArray(20);
            return d;
        }
    };

    public static final Packer<PackVo.Response> RESPONSE_PACKER = new Packer<PackVo.Response>() {
        @Override
        public void pack(PackEncoder encoder, PackVo.Response target) {
            encoder.putInt(0, target.code.ordinal())
                    .putString(1, target.detail)
                    .putObjectArray(2, target.data, DATA_PACKER);
        }

        @Override
        public PackVo.Response unpack(PackDecoder decoder) {
            PackVo.Response r = new PackVo.Response();
            r.code = PackVo.Result.ARRAY[decoder.getInt(0)];
            r.detail = decoder.getString(1);
            r.data = decoder.getObjectArray(2, DATA_PACKER, new PackVo.Data[0]);
            return r;
        }
    };
}
