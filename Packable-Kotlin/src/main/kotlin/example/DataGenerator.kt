package example

object DataGenerator {
    fun generateResponse(n: Int): PackVo.Response {
        return PackVo.Response(
            code = randomResult(),
            detail = RandomUtil.randomShortString(),
            data = makeDataList(n).toTypedArray<PackVo.Data>()
        )
    }

    private fun randomResult(): PackVo.Result {
        return when (RandomUtil.randomCount(4)) {
            1 -> PackVo.Result.FAILED_1
            2 -> PackVo.Result.FAILED_2
            3 -> PackVo.Result.FAILED_3
            else -> PackVo.Result.SUCCESS
        }
    }

    private fun randomCategory(level: Int): PackVo.Category {
        return PackVo.Category(
            name = RandomUtil.randomShortString(),
            level = level,
            i_column = RandomUtil.randomLong(),
            d_column = RandomUtil.randomSmallDouble(),
            des = RandomUtil.randomNullableString(),
            sub_category = null
        )
    }

    private fun makeCategory(): PackVo.Category {
        val subCount = RandomUtil.randomCount(12)
        val subCategoryArray = arrayOfNulls<PackVo.Category>(subCount)
        for (i in 0 until subCount) {
            subCategoryArray[i] = randomCategory(2)
        }
        val category = randomCategory(1)
        category.sub_category = subCategoryArray
        return category
    }

    private fun makeDataList(n: Int): List<PackVo.Data> {
        val list: MutableList<PackVo.Data> = ArrayList(n)
        for (i in 0 until n) {
            val data = PackVo.Data()
            data.d_bool = RandomUtil.randomBoolean()
            data.d_float = RandomUtil.randomFloat()
            data.d_double = RandomUtil.randomDouble()
            data.string_1 = RandomUtil.randomString()
            data.int_1 = RandomUtil.randomInt()
            data.int_2 = RandomUtil.randomInt()
            data.int_3 = RandomUtil.randomInt()
            data.int_4 = RandomUtil.randomInt()
            data.int_5 = RandomUtil.randomInt()
            data.long_1 = RandomUtil.randomLong()
            data.long_2 = RandomUtil.randomLong()
            data.long_3 = RandomUtil.randomLong()
            data.long_4 = RandomUtil.randomLong()
            data.long_5 = RandomUtil.randomLong()
            data.d_category = makeCategory()
            data.bool_array = RandomUtil.randomBoolList()
            data.int_array = RandomUtil.randomIntList()
            data.long_array = RandomUtil.randomLongList()
            data.float_array = RandomUtil.randomFloatList()
            data.double_array = RandomUtil.randomDoubleList()
            data.string_array = RandomUtil.randomStringList()
            list.add(data)
        }
        return list
    }
}
