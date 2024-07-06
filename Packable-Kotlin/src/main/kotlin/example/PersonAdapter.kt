package example

import io.packable.PackDecoder
import io.packable.PackEncoder
import io.packable.TypeAdapter

object PersonAdapter : TypeAdapter<Person> {
    override fun encode(encoder: PackEncoder, target: Person) {
        encoder
            .putString(0, target.name)
            .putInt(1, target.age)
    }

    override fun decode(decoder: PackDecoder): Person {
        return Person(
            name = decoder.getString(0),
            age = decoder.getInt(1)
        )
    }
}