package test

import io.packable.IntConvertor
import io.packable.PackDecoder
import io.packable.PackEncoder
import io.packable.TypeAdapter

enum class Gender(val value: Int) {
    UNKNOWN(0),
    MALE(1),
    FEMALE(2)
}

data class Person(
    val name: String,
    val gender: Gender,
    val age: Int
) {
    override fun toString(): String {
        return "Person: name='$name', gender=$gender, age=$age"
    }
}

object PersonAdapter : TypeAdapter<Person> {
    override fun encode(encoder: PackEncoder, target: Person) {
        target.run {
            encoder
                .putString(0, name)
                .putWithConvertor(1, gender, GenderConvertor)
                .putInt(2, age)
        }
    }

    override fun decode(decoder: PackDecoder): Person {
        return decoder.run {
            Person(
                name = getString(0),
                gender = getByConvertor(1, GenderConvertor) ?: Gender.UNKNOWN,
                age = getInt(2)
            )
        }
    }
}

object GenderConvertor : IntConvertor<Gender> {
    override fun toType(value: Int): Gender {
        return Gender.entries.find { it.value == value } ?: Gender.UNKNOWN
    }

    override fun toInt(target: Gender): Int {
        return target.value
    }
}
