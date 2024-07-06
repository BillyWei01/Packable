package test

import io.packable.*

object TestHelper {

    fun getInfo(): String{
        val person = Person("Tom", Gender.MALE, 20)
        val bytes = PersonAdapter.encode(person)
        val decoded = PersonAdapter.decode(bytes)
        return decoded.toString()
    }

    fun getSimpleMap(): String {
        val map1 = mapOf(
            1L to 100,
            2L to 200,
        )
        val encoded1 = PackEncoder().putMap(0, map1).toBytes()
        val decoded1 = PackDecoder(encoded1).getMap<Long, Int>(0)
        val str1 = "SimpleMap 1, decoded:$decoded1"

        val map2 = mapOf(
            1 to "你好",
            2 to "世界"
        )
        val encoded2 = PackEncoder().putMap(0, map2).toBytes()
        val decoded2 = PackDecoder(encoded2).getMap<Int, String>(0)
        val str2 = "SimpleMap 2, decoded:$decoded2"

        return str1 + "\n" + str2
    }

    fun getComplicatedMap(): String {
        val adapters = listOf(
            AdapterWrapper.create("Test", TestAdapter),
            AdapterWrapper.create("Text", TextAdapter)
        )
        AnyAdapter.registerAdapters(adapters)

        val convertors = listOf(
            ConvertorWrapper.create("Season",  SeasonConvertor),
            ConvertorWrapper.create("Charset", CharsetConvertor)
        )
        AnyAdapter.registerConvertors(convertors)

        val map = mapOf(
            5000 to true,
            1 to 1.23,
            2 to 'x',
            3 to 1000L,
            100 to "hello",
            400 to TestData(200, Season.SUMMER),
            500 to Season.AUTUMN,
            600 to listOf(1, 2, 3),
            700 to Charset.UTF16,
            800 to Text(Charset.UTF8, "hello".encodeToByteArray())
        )
        val encoded = PackEncoder().putMap(0, map, valueAdapter = AnyAdapter).toBytes()
        val decoded = PackDecoder(encoded).getMap<Int, Any?>(0,  valueAdapter = AnyAdapter)
        return "ComplicatedMap, decoded:$decoded"
    }

}