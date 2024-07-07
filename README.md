# Packbele

[![Maven Central](https://img.shields.io/maven-central/v/io.github.billywei01/packable-kotlin)](https://search.maven.org/artifact/io.github.billywei01/packable) | [English](README_EN.md)

## 1.  概述
Packable是一个高效易用的序列化框架。<br>
可以用于对象序列化/反序列化，消息封装等，从而方便本地存储或网络传输。

Packable有以下优点：
- 1、编码/解码快速。
- 2、编码紧凑，体积小。
- 3、支持版本兼容（增删字段不影响整体的解析）。
- 4、代码轻量。
- 5、支持多种类型。
- 6、支持多种语言，可跨平台传输。


Packable目前实现了Java、Kotlin、C++、C#、Objective-C、Go等版本。

## 2. 使用方法

Java和Kotlin版本代码已发布到Maven仓库，路径如下：

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

以下以Kotlin版本的用法为例。

假设类型定义如下：

```kotlin
data class Person(
    val name: String,
    val age: Int
)
```

可以定义类型适配器如下：

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

序列化/反序列化：

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

## 3. 性能测试

测试对象：
- Packable
- Protobuf
- Gson

测试设备: Macbook Pro

测试代码：[Main](https://github.com/BillyWei01/Packable/blob/main/java/src/main/java/Main.java)

测试结果：

|          | 数据大小(byte)     | 序列化（ms) | 反序列化(ms) |
|----------|----------------|---------|----------|
| packable | 2564756 (56%)  | 8       | 8        |
| protobuf | 2627081 (59%)  | 16      | 17       |
| gson     | 4427344 (100%) | 58      | 50       |

## 4. 关联文档
https://juejin.cn/post/6992380683977130015/


## License
See the [LICENSE](LICENSE) file for license rights and limitations.
