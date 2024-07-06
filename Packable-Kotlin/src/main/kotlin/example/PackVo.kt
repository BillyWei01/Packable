package example

@Suppress("EqualsOrHashCode", "PropertyName")
class PackVo {
    enum class Result {
        SUCCESS,
        FAILED_1,
        FAILED_2,
        FAILED_3;

        companion object {
            val ARRAY = arrayOf(SUCCESS, FAILED_1, FAILED_2, FAILED_3)
        }
    }

    class Category(
        var name: String?,
        var level: Int,
        var i_column: Long,
        var d_column: Double,
        var des: String? = null,
        var sub_category: Array<Category?>?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Category
            if (name != other.name) return false
            if (level != other.level) return false
            if (i_column != other.i_column) return false
            if (d_column != other.d_column) return false
            if (des != other.des) return false
            if (sub_category != null) {
                if (other.sub_category == null) return false
                if (!sub_category.contentEquals(other.sub_category)) return false
            } else if (other.sub_category != null) return false
            return true
        }
    }

    class Data {
        var d_bool = false
        var d_float = 0f
        var d_double = 0.0
        var string_1: String? = null
        var int_1 = 0
        var int_2 = 0
        var int_3 = 0
        var int_4 = 0
        var int_5 = 0
        var long_1: Long = 0
        var long_2: Long = 0
        var long_3: Long = 0
        var long_4: Long = 0
        var long_5: Long = 0
        var d_category: Category? = null
        var bool_array: BooleanArray? = null
        var int_array: IntArray? = null
        var long_array: LongArray? = null
        var float_array: FloatArray? = null
        var double_array: DoubleArray? = null
        var string_array: Array<String>? = null

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Data

            if (d_bool != other.d_bool) return false
            if (d_float != other.d_float) return false
            if (d_double != other.d_double) return false
            if (string_1 != other.string_1) return false
            if (int_1 != other.int_1) return false
            if (int_2 != other.int_2) return false
            if (int_3 != other.int_3) return false
            if (int_4 != other.int_4) return false
            if (int_5 != other.int_5) return false
            if (long_1 != other.long_1) return false
            if (long_2 != other.long_2) return false
            if (long_3 != other.long_3) return false
            if (long_4 != other.long_4) return false
            if (long_5 != other.long_5) return false
            if (d_category != other.d_category) return false
            if (bool_array != null) {
                if (other.bool_array == null) return false
                if (!bool_array.contentEquals(other.bool_array)) return false
            } else if (other.bool_array != null) return false
            if (int_array != null) {
                if (other.int_array == null) return false
                if (!int_array.contentEquals(other.int_array)) return false
            } else if (other.int_array != null) return false
            if (long_array != null) {
                if (other.long_array == null) return false
                if (!long_array.contentEquals(other.long_array)) return false
            } else if (other.long_array != null) return false
            if (float_array != null) {
                if (other.float_array == null) return false
                if (!float_array.contentEquals(other.float_array)) return false
            } else if (other.float_array != null) return false
            if (double_array != null) {
                if (other.double_array == null) return false
                if (!double_array.contentEquals(other.double_array)) return false
            } else if (other.double_array != null) return false
            if (string_array != null) {
                if (other.string_array == null) return false
                if (!string_array.contentEquals(other.string_array)) return false
            } else if (other.string_array != null) return false

            return true
        }
    }

    class Response(
        val code: Result,
        val detail: String,
        val data: Array<Data>?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Response

            if (code != other.code) return false
            if (detail != other.detail) return false
            if (data != null) {
                if (other.data == null) return false
                if (!data.contentEquals(other.data)) return false
            } else if (other.data != null) return false

            return true
        }
    }
}
