import example.*
import io.packable.*
import io.packable.AnyAdapter
import io.packable.ConvertorWrapper
import io.packable.AdapterWrapper

fun main(args: Array<String>) {
    testEncodeObject()

    testEncodeObjectList()

    testSimpleMap()

    testComplicatedMap()

    testAdapter()
}

private fun testEncodeObject() {
    val person = Person("Tom", 20)

    val encoded = PersonAdapter.encode(person)
    val decoded = PersonAdapter.decode(encoded)

    println("Person: ${person == decoded}")
}

private fun testEncodeObjectList() {
    val list = listOf(
        Person("Tom", 20),
        Person("Mary", 18)
    )

    val encoded = PersonAdapter.encodeObjectList(list)
    val decoded = PersonAdapter.decodeObjectList(encoded)

    println("Person list: ${list == decoded}")
}

private fun testSimpleMap() {
    val map = mapOf(
        Integer.valueOf(1) to Integer.valueOf("100"),
        Integer.valueOf(2) to Integer.valueOf(200),
    )
    val encoded = PackEncoder().putMap(0, map).toBytes()
    val decoded = PackDecoder(encoded).getMap<Int, Int>(0)
    println("testSimpleMap 1, decoded:$decoded")

    val map2 = mapOf(
        1L to 100,
        2L to 200,
    )
    val encoded2 = PackEncoder().putMap(0, map2).toBytes()
    val decoded2 = PackDecoder(encoded2).getMap<Long, Int>(0)
    println("testSimpleMap 2, decoded:$decoded2")

    val map3 = mapOf(
        1 to "100",
        2 to "200",
    )
    val encoded3 = PackEncoder().putMap(0, map3).toBytes()
    val decoded3 = PackDecoder(encoded3).getMap<Int, String>(0)
    println("testSimpleMap 3, decoded:$decoded3")
}

private fun testComplicatedMap() {
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
        800 to Text(Charset.UTF8, "hello".toByteArray())
    )
    val encoded = PackEncoder().putMap(0, map, valueAdapter = AnyAdapter).toBytes()
    val decoded = PackDecoder(encoded).getMap<Int, Any?>(0,  valueAdapter = AnyAdapter)
    println("testComplicatedMap, decoded:$decoded")
}

private fun testAdapter() {
    val response = DataGenerator.generateResponse(400)

    val bytes = ResponseAdapter.encode(response)
    println("bytes size: ${bytes.size}")

    val newResponse = ResponseAdapter.decode(bytes)
    println("equal: ${response == newResponse}")
}

