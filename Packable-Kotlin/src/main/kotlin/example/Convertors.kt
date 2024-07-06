package example

import io.packable.*


enum class Season(val value: Int) {
    SPRING(1),
    SUMMER(2),
    AUTUMN(3),
    WINTER(4);

    companion object {
        fun toType(value: Int): Season {
            return Season.entries.find { it.value == value }
                ?: throw IllegalArgumentException("invalid value:$value")
        }
    }
}

object SeasonConvertor : IntConvertor<Season> {
    override fun toType(value: Int): Season {
        return Season.toType(value)
    }

    override fun toInt(target: Season): Int {
        return target.value
    }
}

class TestData(
    val x: Int = 100,
    val season: Season
) {
    override fun toString(): String {
        return "TestData(x=$x, season=$season)"
    }
}

object TestAdapter: TypeAdapter<TestData> {
    override fun encode(encoder: PackEncoder, target: TestData) {
        target.run {
            encoder
                .putInt(0, x)
                .putWithConvertor(1, season, SeasonConvertor)
        }
    }

    override fun decode(decoder: PackDecoder): TestData {
        return decoder.run {
            TestData(
                x = getInt(0),
                season = getByConvertor(1, SeasonConvertor) ?: Season.SPRING
            )
        }
    }
}

enum class Charset(val value: Int) {
    UNKNOWN(0),
    ASCII(1),
    UTF8(2),
    UTF16(3)
}

object CharsetConvertor : IntConvertor<Charset> {
    override fun toType(value: Int): Charset {
        return Charset.entries.find { it.value == value } ?: Charset.UNKNOWN
    }

    override fun toInt(target: Charset): Int {
        return target.value
    }
}

class Text(val charset: Charset, val content: ByteArray) {
    override fun toString(): String {
        return "Text(charset=$charset, length=${content.size})"
    }
}

object TextAdapter : TypeAdapter<Text> {
    override fun encode(encoder: PackEncoder, target: Text) {
        target.run {
            encoder
                .putWithConvertor(0, charset, CharsetConvertor)
                .putByteArray(1, content)
        }
    }

    override fun decode(decoder: PackDecoder): Text {
        return decoder.run {
            Text(
                charset = getByConvertor(0, CharsetConvertor) ?: Charset.UTF8,
                content = getByteArray(1) ?: ByteArray(0)
            )
        }
    }
}
