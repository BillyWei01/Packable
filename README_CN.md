# Packbele

## 1.  概述
Packable是一种高效易用的数据交换格式。<br>
可以用于对象序列化/反序列化，消息封装等，从而方便本地存储或网络传输。

Packable有以下优点：
- 1、编码/解码快速
- 2、编码紧凑，体积小
- 3、使用方便, 方法灵活
- 4、代码轻量
- 5、支持多种类型
- 6、支持多种的压缩策略
- 7、支持多种语言，可跨平台传输

Packable目前实现了Java、C++、C#、Objective-C、Go等版本。

## 2. 使用方法
以下以JAVA平台的用法举例，其他平台用法类似。

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
    Data data = new Data();

    // 编码
    PackEncoder encoder = new PackEncoder();
    encoder.putString(0, data.msg);
    encoder.putPackableArray(1, data.items);
    byte[] bytes = encoder.getBytes();

    // 解码
    PackDecoder decoder = PackDecoder.newInstance(bytes);
    Data data_2 = new Data();
    data_2.msg = decoder.getString(0);
    data_2.items = decoder.getPackableArray(1, Item.CREATOR);
    decoder.recycle();
}
```

甚至，如果没有定义Item.CREATOR, 也可以自行解码items：
```java
PackDecoder.DecoderArray da = decoder.getDecoderArray(1);
if (da != null) {
    Item[] items = new Item[da.getCount()];
    int i = 0;
    while (da.hasNext()) {
        PackDecoder d = da.next();
        if (d == null) {
            items[i++] = null;
        } else {
            items[i++] = new Item(d.getInt(0), d.getLong(1));
        }
    }
    data_2.items = items;
}
```

### 2.3 自定义编码
比方说下面这样一个类：
```java
class Info  {
    public long id;
    public String name;
    public Rectangle rect;
}
```

Rectangle是JDK的一个类，有四个字段：

![](https://upload-images.jianshu.io/upload_images/1166341-cd89c228fa0a8be5.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

我们不能去改JDK代码让Rectangle实现Packable。<br>
这时候可以定义Wrapper类，传入Rectangle作为成员，
然后 Wrapper实现Packable，encode接口实际上编码Rectangle的字段；
或者用一个数组装下Rectangle的字段，再用putArray的方法将其写入。<br>

packable提供的一种效率更高的方法：

```java
public static class Info implements Packable {
    public long id;
    public String name;
    public Rectangle rect;

    @Override
    public void encode(PackEncoder encoder) {
        encoder.putLong(0, id)
                .putString(1, name);
        EncodeBuffer buf = encoder.putCustom(2, 16);
        buf.writeInt(rect.x);
        buf.writeInt(rect.y);
        buf.writeInt(rect.width);
        buf.writeInt(rect.height);
    }

    public static final PackCreator<Info> CREATOR = decoder -> {
        Info info = new Info();
        info.id = decoder.getLong(0);
        info.name = decoder.getString(1);
        DecodeBuffer buf = decoder.getCustom(2);
        if (buf != null) {
            info.rect = new Rectangle(
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt());
        }
        return info;
    };
}
```
由于Rectanle的四个字段都是固定长度，如果不考虑压缩，其占用空间是可知的（16字节）。<br>
所以，此方式有点类似于编码数组，但是更加灵活，因为Buffer类可以写入各种类型，而数组要求类型相同，
此外，putCustom() 返回的实际上就是PackEncoder的buffer, 所以不需要而外创建数组对象，数据也不需要多周转一趟（用数据的方案需要先将值赋值给数组）。<br>
相比于Wrapper的方案，此方案减少了递归层次，同时不用编码多个index, 效率更高。

更多的用法，可以到对应平台的的项目代码中查看。

## 3. 性能测试
除了Protobuf之外，还选择了Gson (json协议的序列化框架之一，java平台）来做下比较。

数据定义如下：

```
enum Result {
    SUCCESS = 0;
    FAILED_1 = 1;
    FAILED_2 = 2;
    FAILED_3 = 3;
}

message Category {  
    string name = 1;
    int32 level = 2;
    int64 i_column = 3;
    double d_column = 4;
    optional string des = 5;
    repeated Category sub_category = 6;
} 

message Data {  
    bool d_bool  = 1;
    float d_float = 2;
    double d_double = 3;
    string string_1 = 4;
    int32 int_1 = 5;
    int32 int_2 = 6;
    int32 int_3 = 7;
    sint32 int_4 = 8;
    sfixed32 int_5 = 9;
    int64 long_1 = 10;
    int64 long_2 = 11;
    int64 long_3 = 12;
    sint64 long_4 = 13;
    sfixed64 long_5 = 14;
    Category d_categroy = 15;
    repeated bool bool_array = 16;
    repeated int32 int_array = 17;
    repeated int64 long_array  = 18;
    repeated float float_array = 19;
    repeated double double_array = 20;
    repeated string string_array = 21;
}

message Response {                 
    Result code = 1;
    string detail = 2;
    repeated Data data = 3;
}
```

三种类型的嵌套，主数据为Data类，声明了多个类型的字段。

测试数据是用按一定的规则随机生成的，测试中控制Data的数量从少到多，各项指标和Data的数量成正相关。<br>
所以这里只展示特定数量（2000个Data）的结果。

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

- packable比protobuf快不少，比gson快很多；
- 以上测试结果是先各跑几轮编解码之后再执行的测试，如果只跑一次的话都会比如上结果慢（JIT优化等因素所致），但对比的结果是一致的。

需要说明的是，数据特征，测试平台等因素都会影响结果，以上测试结果仅供参考。


## License
See the [LICENSE](LICENSE.md) file for license rights and limitations.