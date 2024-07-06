package example

import io.packable.PackDecoder
import io.packable.PackEncoder
import io.packable.TypeAdapter

object CategoryAdapter : TypeAdapter<PackVo.Category?> {
    override fun encode(encoder: PackEncoder, target: PackVo.Category?) {
        target?.run {
            encoder
                .putString(0, name)
                .putInt(1, level)
                .putLong(2, i_column)
                .putDouble(3, d_column)
                .putString(4, des)
                .putObjectArray(5, sub_category, CategoryAdapter)
        }
    }

    override fun decode(decoder: PackDecoder): PackVo.Category {
        return decoder.run {
            PackVo.Category(
                name = getNullableString(0),
                level = getInt(1),
                i_column = getLong(2),
                d_column = getDouble(3),
                des = getNullableString(4),
                sub_category = getNullableObjectArray<PackVo.Category?>(5, CategoryAdapter)
            )
        }
    }
}

object DataAdapter : TypeAdapter<PackVo.Data> {
    override fun encode(encoder: PackEncoder, target: PackVo.Data) {
        target.run {
            encoder
                .putBoolean(0, d_bool)
                .putFloat(1, d_float)
                .putDouble(2, d_double)
                .putString(3, string_1)
                .putInt(4, int_1)
                .putInt(5, int_2)
                .putInt(6, int_3)
                .putInt(7, int_4)
                .putInt(8, int_5)
                .putLong(9, long_1)
                .putLong(10, long_2)
                .putLong(11, long_3)
                .putLong(12, long_4)
                .putLong(13, long_5)
                .putObject(14, d_category, CategoryAdapter)
                .putBooleanArray(15, bool_array)
                .putIntArray(16, int_array)
                .putLongArray(17, long_array)
                .putFloatArray(18, float_array)
                .putDoubleArray(19, double_array)
                .putStringArray(20, string_array)
        }
    }

    override fun decode(decoder: PackDecoder): PackVo.Data {
        val d = PackVo.Data()
        d.d_bool = decoder.getBoolean(0)
        d.d_float = decoder.getFloat(1)
        d.d_double = decoder.getDouble(2)
        d.string_1 = decoder.getNullableString(3)
        d.int_1 = decoder.getInt(4)
        d.int_2 = decoder.getInt(5)
        d.int_3 = decoder.getInt(6)
        d.int_4 = decoder.getInt(7)
        d.int_5 = decoder.getInt(8)
        d.long_1 = decoder.getLong(9)
        d.long_2 = decoder.getLong(10)
        d.long_3 = decoder.getLong(11)
        d.long_4 = decoder.getLong(12)
        d.long_5 = decoder.getLong(13)
        d.d_category = decoder.getObject(14, CategoryAdapter)
        d.bool_array = decoder.getBooleanArray(15)
        d.int_array = decoder.getIntArray(16)
        d.long_array = decoder.getLongArray(17)
        d.float_array = decoder.getFloatArray(18)
        d.double_array = decoder.getDoubleArray(19)
        d.string_array = decoder.getStringArray(20)
        return d
    }
}

object ResponseAdapter : TypeAdapter<PackVo.Response> {
    override fun encode(encoder: PackEncoder, target: PackVo.Response) {
        target.run {
            encoder
                .putInt(0, code.ordinal)
                .putString(1, detail)
                .putObjectArray(2, data, DataAdapter)
        }
    }

    override fun decode(decoder: PackDecoder): PackVo.Response {
        return decoder.run {
            PackVo.Response(
                code = PackVo.Result.ARRAY[getInt(0)],
                detail = getString(1),
                data = getObjectArray<PackVo.Data>(2, DataAdapter)
            )
        }
    }
}

