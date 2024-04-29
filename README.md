# Packbele

[![Maven Central](https://img.shields.io/maven-central/v/io.github.billywei01/packable)](https://search.maven.org/artifact/io.github.billywei01/packable) | [English](README_EN.md)

## 1.  概述
Packable是一个高效易用的序列化框架。<br>
可以用于对象序列化/反序列化，消息封装等，从而方便本地存储或网络传输。

Packable有以下优点：
- 1、编码/解码快速。
- 2、编码紧凑，体积小。
- 3、支持版本兼容（增删字段不影响整体的解析）。
- 4、代码轻量（Java版本仅20+K）。
- 5、支持多种类型。
- 6、支持多种语言，可跨平台传输。


Packable目前实现了Java、C++、C#、Objective-C、Go等版本。

## 2. 使用方法
以下以JAVA平台的用法举例。

Java代码已发布到Maven仓库，路径如下：
```gradle
dependencies {
    implementation 'io.github.billywei01:packable:2.0.1'
}
```


### 2.1 常规用法

假设类型定义如下：

```java
public class Person {
    public String name;
    public int age;

    public Person(String name, int age){
        this.name = name;
        this.age = age;
    }
}
```


使用前，可以定义解码方法如下：

```java
public static final Packer<Person> PERSON_PACKER = new Packer<Person>() {
    // 打包目标对象到encoder
    @Override
    public void pack(PackEncoder encoder, Person target) {
        // 0, 1等编号，类似json的key, 用于标记字段。
        encoder.putString(0, target.name)
                .putInt(1, target.age);
    }

    // 从decoder解包目标对象
    @Override
    public Person unpack(PackDecoder decoder) {
        // 根据编号取出对应的字段
        return new Person(
                decoder.getString(0),
                decoder.getInt(1)
        );
    }
};

```


解码对象：

```java
public void test() {
    Person person = new Person("Tom", 20);
    
    // 序列化
    byte[] encoded = PackEncoder.encode(person, PERSON_PACKER);
    
    // 反序列化
    Person decoded = PackDecoder.decode(encoded, PERSON_PACKER);
}
```

解码列表：

```java
public void test4() {
    List<Person> personList = new ArrayList<>();
    personList.add(new Person("Tom", 20));
    personList.add(new Person("Jerry", 19));

    // 序列化
    byte[] encoded = PackEncoder.encodeList(personList, PERSON_PACKER);
    
    // 反序列化
    List<Person> decoded = PackDecoder.decodeList(encoded, PERSON_PACKER);
}
```



### 2.2 直接编码
上面的举例只是范例之一，具体使用过程中，可以灵活运用。<br>
比如，可以直接编解码

```java
public void test2() {
    String message = "hello";
    int a = 100;
    int b = 200;

    PackEncoder encoder = new PackEncoder();
    encoder.putString(0, message)
            .putInt(1, a)
            .putInt(2, b);
    byte[] bytes = encoder.toBytes();

    PackDecoder decoder = new PackDecoder(bytes);
    String decodeMessage = decoder.getString(0);
    int decodedA = decoder.getInt(1);
    int decodedB = decoder.getInt(2);
}
```


## 3. 性能测试
除了protobuf之外，还选择了gson (json协议的序列化框架之一，java平台）来做下比较。

空间方面，序列化后数据大小：

| | 数据大小(byte) 
---|---
packable | 2564756 (56%)
protobuf  | 2627081 (59%)
gson       | 4427344 (100%)

packable和protobuf大小相近（packable略小），约为gson的57%。


耗时方面，分别在PC和手机上测试了两组数据：

1. Macbook Pro

| |序列化耗时 (ms)| 反序列化耗时(ms)
---|---|---
packable | 8 | 8
protobuf |16 | 17
gson     | 58 | 50



## 4. 关联文档
https://juejin.cn/post/6992380683977130015/


## License
See the [LICENSE](LICENSE) file for license rights and limitations.
