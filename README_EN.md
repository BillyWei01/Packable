# Packable

[![Maven Central](https://img.shields.io/maven-central/v/io.github.billywei01/packable-kotlin)](https://search.maven.org/artifact/io.github.billywei01/packable) 

## 1. Overview
Packable is a well-designed serialization framework.  <br>
It has been implemented multiple languages, it's effective and easy to use. <br>
It can be used for object serialization/deserialization and message pack up, to storage or RPC.

Packable has the following advantages:
1. Fast encoding/decoding.
2. Encoded message is compact (with small size).
3. Easy to use.
4. The code is light.
5. Support multiple types and compression strategies.
6. Supports multiple languages and cross-platforms.

Packable currently implements version of Java, Kotlin, C++, C#, Objective-C and Go.

## How to use
Here is usage of Kotlin version,  and APIs on other platform are similar to Kotlin.

The jar has been published to Maven Central：

java:
```gradle
dependencies {
    implementation 'io.github.billywei01:packable-java:2.1.2'
}
```

kotlin:
```gradle
dependencies {
    implementation 'io.github.billywei01:packable-kotlin:2.1.2'
}
```

Suppose there is a class like this:

```kotlin
data class Person(
    val name: String,
    val age: Int
)
```

Make an adapter like this：

```kotlin
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
```

Serialization / Deserialization：

```kotlin

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

    val encoded = PackEncoder.encodeObjectList(list, PersonAdapter)
    val decoded = PackDecoder.decodeObjectList(encoded, PersonAdapter)

    println("Person list: ${list == decoded}")
}
```


## 3. Benchmark

Testing object：
- Packable
- Protobuf
- Gson

Testing Device: Macbook Pro

Testing Code：[Main](https://github.com/BillyWei01/Packable/blob/main/java/src/main/java/Main.java)

Testing result：

|          | 数据大小(byte)     | 序列化（ms) | 反序列化(ms) |
|----------|----------------|---------|----------|
| packable | 2564756 (56%)  | 8       | 8        |
| protobuf | 2627081 (59%)  | 16      | 17       |
| gson     | 4427344 (100%) | 58      | 50       |

# 4. Format
See: [Packable Format](format.md)

## License
See the [LICENSE](LICENSE) file for license rights and limitations.
