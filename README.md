# Packbele

[![Maven Central](https://img.shields.io/maven-central/v/io.github.billywei01/packable)](https://search.maven.org/artifact/io.github.billywei01/packable) | [English](README_EN.md)

## 1.  概述
Packable是一个高效易用的序列化框架。<br>
可以用于对象序列化/反序列化，消息封装等，从而方便本地存储或网络传输。

Packable有以下优点：
- 1、编码/解码快速
- 2、编码紧凑，体积小
- 3、使用方便, 方法灵活
- 4、代码轻量
- 5、支持多种类型和压缩策略
- 6、支持多种语言，可跨平台传输

Packable目前实现了Java、C++、C#、Objective-C、Go等版本。

## 2. 使用方法
以下以JAVA平台的用法举例，其他平台用法类似。

Java代码已发布到Maven仓库，路径如下：
```gradle
dependencies {
    implementation 'io.github.billywei01:packable:1.1.0'
}
```

假设有下面这样的两个对象：

```java
class Data {
    String msg;
    Item[] items;
}

class Item {
    int a;
    long b;
}
```

### 2.1 常规用法
序列化/反序列化用例如下：

```java
static class Data implements Packable {
    String msg;
    Item[] items;

    @Override
    public void encode(PackEncoder encoder) {
        encoder.putString(0, msg)
                .putPackableArray(1, items);
    }

    public static final PackCreator<Data> CREATOR = decoder -> {
        Data data = new Data();
        data.msg = decoder.getString(0);
        data.items = decoder.getPackableArray(1, Item.CREATOR);
        return data;
    };
}

static class Item implements Packable {
    int a;
    long b;

    Item(int a, long b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public void encode(PackEncoder encoder) {
        encoder.putInt(0, a);
        encoder.putLong(1, b);
    }

    static final PackArrayCreator<Item> CREATOR = new PackArrayCreator<Item>() {
        @Override
        public Item[] newArray(int size) {
            return new Item[size];
        }

        @Override
        public Item decode(PackDecoder decoder) {
            return new Item(
                    decoder.getInt(0),
                    decoder.getLong(1)
            );
        }
    };
}

static void test() {
    Data data = new Data();
    // 序列化
    byte[] bytes = PackEncoder.marshal(data);
    // 反序列化
    Data data_2 = PackDecoder.unmarshal(bytes, Data.CREATOR);
}

```

#### 序列化
- 1、声明 implements Packable 接口；<br>
- 2、实现encode()方法，编码各个字段（PackEncoder提供了各种类型的API）；<br>
- 3、调用PackEncoder.marshal()方法，传入对象， 得到字节数组。

#### 反序列化
- 1、创建一个静态对象，该对象为PackCreator的实例；<br>
- 2、实现decode()方法，解码各个字段，赋值给对象；<br>
- 3、调用PackDecoder.unmarshal(), 传入字节数组以及PackCreator实例，得到对象。

如果需要反序列化一个对象数组, 需要创建PackArrayCreator的实例（Java版本如此，其他版本不需要）。<br>
PackArrayCreator继承于PackCreator，多了一个newArray方法，简单地创建对应类型对象数组返回即可。

### 2.2 直接编码
上面的举例只是范例之一，具体使用过程中，可以灵活运用。<br>
- 1、PackCreator不一定要在需要反序列化的类中创建，在其他地方也可以，可任意命名。<br>
- 2、如果只需要序列化（发送方），则只实现Packable即可，不需要实现PackCreator，反之亦然。<br>
- 3、如果没有类定义，或者不方便改写类，也可以直接编码/解码。

```java
static void test2() {
    String msg = "message";
    int a = 100;
    int b = 200;

    PackEncoder encoder = new PackEncoder();
    encoder.putString(0, msg)
                .putInt(1, a)
                .putInt(2, b);
    byte[] bytes = encoder.getBytes();

    PackDecoder decoder = PackDecoder.newInstance(bytes);
    String dMsg = decoder.getString(0);
    int dA = decoder.getInt(1);
    int dB = decoder.getInt(2);
    decoder.recycle();

    boolean equal = msg.equals(dMsg) && (a == dA) && (b == dB);
    Assert.assertTrue(equal);
}
```


## 3. 性能测试
除了protobuf之外，还选择了gson (json协议的序列化框架之一，java平台）来做下比较。

空间方面，序列化后数据大小：

| | 数据大小(byte) 
---|---
packable | 2537191 (57%)
protobuf  | 2614001 (59%)
gson       | 4407901 (100%)

packable和protobuf大小相近（packable略小），约为gson的57%。


耗时方面，分别在PC和手机上测试了两组数据：

1. Macbook Pro

| |序列化耗时 (ms)| 反序列化耗时(ms)
---|---|---
packable |9|8
protobuf |19 |11
gson     | 67 |46

2. 荣耀20S

| |序列化耗时 (ms)| 反序列化耗时(ms)
---|---|---
packable |32 | 21
protobuf  | 81 | 38
gson    | 190 | 128

需要说明的是，数据特征，测试平台等因素都会影响结果，以上测试结果仅供参考。

## 4. 关联文档
https://juejin.cn/post/6992380683977130015/


## License
See the [LICENSE](LICENSE) file for license rights and limitations.
