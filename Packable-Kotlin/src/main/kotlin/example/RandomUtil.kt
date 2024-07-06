package example

import kotlin.math.abs
import kotlin.random.Random


object RandomUtil {
    private const val ALL_ASCII = false
    private val NOT_ASCII = arrayOf("\uD842\uDFB7", "一", "二", "三", "四", "五")
    private val SPECIAL_CHARACTERS = "~@#$%^&*(){}[]<>:\"?;',./".toCharArray()
    private val random = Random(1)
    private const val ARRAY_SIZE = 15
    private const val P = 5
    fun randomBoolean(): Boolean {
        return random.nextBoolean()
    }

    fun randomFloat(): Float {
        return random.nextFloat() * random.nextInt(10000)
    }

    fun randomDouble(): Double {
        return random.nextDouble() * random.nextInt(1000000)
    }

    fun randomSmallDouble(): Double {
        val i = random.nextInt()
        val flag = i % 4
        if (flag == 0) {
            return 0.0
        }
        if (flag == 1) {
            return (i and 0x1F).toDouble()
        }
        return if (flag == 2) {
            (i and 0xFFFFF).toDouble()
        } else (i and 0xFFFFF) / 10.0
    }

    fun randomCount(n: Int): Int {
        return random.nextInt(n)
    }

    fun randomInt(): Int {
        val i = random.nextInt()
        if (i % P == 0) {
            return 0
        }
        val shift = i and 3 shl 3
        return i ushr shift
    }

    fun randomLong(): Long {
        val l = random.nextLong()
        if (l % P == 0L) {
            return 0
        }
        val shift = (l and 7L).toInt() shl 3
        return l ushr shift
    }

    fun randomString(): String {
        val i = random.nextInt()
        return if (i % P == 0) {
            ""
        } else randomStr(i % 300)
    }

    fun randomNullableString(): String? {
        return if (random.nextInt() and 7 == 0) {
            null
        } else {
            randomString()
        }
    }

    fun randomShortString(): String {
        val i = random.nextInt()
        return if (i % 10 == 0) {
            randomStr(random.nextInt(100))
        } else {
            randomStr(random.nextInt(50))
        }
    }

    private fun randomStr(maxLen: Int): String {
        val builder = StringBuilder()
        for (i in 0 until maxLen) {
            val a = random.nextInt(Int.MAX_VALUE) % 50
            val r = random.nextInt(Int.MAX_VALUE)
            if (!ALL_ASCII && a < 4) {
                builder.append(NOT_ASCII[r % NOT_ASCII.size])
            } else if (a < 10) {
                builder.append(SPECIAL_CHARACTERS[r % SPECIAL_CHARACTERS.size])
            } else if (a < 20) {
                builder.append(('0'.code + r % 10).toChar())
            } else if (a < 30) {
                builder.append(('A'.code + r % 26).toChar())
            } else {
                builder.append(('a'.code + r % 26).toChar())
            }
        }
        return builder.toString()
    }

    fun randomBoolList(): BooleanArray {
        val n = random.nextInt(ARRAY_SIZE)
        val a = BooleanArray(n)
        for (i in 0 until n) {
            a[i] = random.nextBoolean()
        }
        return a
    }

    fun randomIntList(): IntArray {
        val n = random.nextInt(ARRAY_SIZE)
        val a = IntArray(n)
        for (i in 0 until n) {
            a[i] = abs(random.nextInt())
        }
        return a
    }

    fun randomLongList(): LongArray {
        val n = random.nextInt(ARRAY_SIZE)
        val a = LongArray(n)
        for (i in 0 until n) {
            a[i] = abs(random.nextLong())
        }
        return a
    }

    fun randomFloatList(): FloatArray {
        val n = random.nextInt(ARRAY_SIZE)
        val a = FloatArray(n)
        for (i in 0 until n) {
            a[i] = random.nextFloat()
        }
        return a
    }

    fun randomDoubleList(): DoubleArray {
        val n = random.nextInt(ARRAY_SIZE)
        val a = DoubleArray(n)
        for (i in 0 until n) {
            a[i] = random.nextDouble() * randomInt()
        }
        return a
    }

    fun randomStringList(): Array<String> {
        val n = random.nextInt(ARRAY_SIZE)
        val a = arrayOfNulls<String>(n)
        for (i in 0 until n) {
            a[i] = randomShortString()
        }
        return a.mapNotNull { it }.toTypedArray()
    }
}

