# Packbele

[中文文档](README_CN.md)

## 1. Overview
Packable is an effective and convinient data interchange format. <br>
It can be used for object serialization/deserialization, message encapsulation, etc. for local storage or network transmission.Packable is a well designed data interchange format.  <br>
It has been impleamented multiple languages , which is effective and easy to use. <br>
It can be used for object serialization/deserialization, message encapsulation, etc. for local storage or network transmission.

Packable has the following advantages:
1. Fast encoding/decoding
2. Compact coding and small size
3. Easy to use,  flexiblely
4. The code is light
5. Support multiple types
6. Support multiple compression strategies
7. Supports multiple languages and cross-platforms

Packable currently implements version of Java, C++, C#, Objective-C, Golang.

## 2. Usage
Suppose there are two objects like the following:

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

### 2.1 General usage

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
    
    byte[] bytes = PackEncoder.marshal(data);
   
    Data data_2 = PackDecoder.unmarshal(bytes, Data.CREATOR);
}
```

#### Serialization
1. Implements the interface **Packable** <br>
2. Implement the encode() method,  to encode each field (PackEncoder provides various types of API) <br>
3. Call the PackEncoder.marshal() method, tranfer the object, and get the byte array.

#### Deserialization
1. Create a static object, which is an instance of PackCreator<br>
2. Implement the decode() method, decode each field, and assign it to the object;<br>
3. Call PackDecoder.unmarshal(), pass in the byte array and PackCreator instance, and get the object.

If you need to deserialize an array of objects, you need to create an instance of PackArrayCreator (this is the case for the Java version,  other versions no need to do this). <br>

### 2.2 Coded Directly
The  example above is only one of the examples. It can be used flexibly.< br>
1. PackCreator does not have to be created in the class that needs to be deserialized. It can also be named anywhere else.
2. If you only need serialization (sender), you only need to implement Packable.
3. If there is no class definition or it is inconvenient to rewrite the class, you can also directly encode / decode the message.

```java
static void test2() {
    Data data = new Data();

    PackEncoder encoder = new PackEncoder();
    encoder.putString(0, data.msg);
    encoder.putPackableArray(1, data.items);
    byte[] bytes = encoder.getBytes();

    PackDecoder decoder = PackDecoder.newInstance(bytes);
    Data data_2 = new Data();
    data_2.msg = decoder.getString(0);
    data_2.items = decoder.getPackableArray(1, Item.CREATOR);
    decoder.recycle();
}
```

If Item.CREATOR has not beed defined, you could decode items by yourself like this:

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

### 2.3 Custom Coding
For example, there is a Class like this:
```java
class Info  {
    public long id;
    public String name;
    public Rectangle rect;
}
```

Rectangle has four fields：

![](https://upload-images.jianshu.io/upload_images/1166341-cd89c228fa0a8be5.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

For some reason we can't change the code of Rectangle, so how to do?
There's a effetive way supplied by packable:

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

## 3. Benchmark
Data schema like this:
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

Generate 'Response' with 2000 'Data':

Space of serialization bytes:

| | Space (byte) 
---|---
packable | 2537191 (57%)
protobuf  | 2614001 (59%)
gson       | 4407901 (100%)

Process time:

1. Macbook Pro

| |Serialization (ms)| Deserialization (ms)
---|---|---
packable |9|8
protobuf |19 |11
gson     | 67 |46

2. Huawei Honor 20S

| |Serialization (ms)| Deserialization (ms)
---|---|---
packable |32 | 21
protobuf  | 81 | 38
gson    | 190 | 128

It should be noted that data characteristics, test platform and other factors will affect the results. 


## License
See the [LICENSE](LICENSE) file for license rights and limitations.