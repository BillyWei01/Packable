# Packbele

[![Maven Central](https://img.shields.io/maven-central/v/io.github.billywei01/packable)](https://search.maven.org/artifact/io.github.billywei01/packable) 

## 1. Overview
Packable is a well-designed serialization framework.  <br>
It has been implemented multiple languages , it's effective and easy to use. <br>
It can be used for object serialization/deserialization and message pack up, to storage or RPC.

Packable has the following advantages:
1. Fast encoding/decoding.
2. Encoded message is compact (with small size).
3. Easy to use.
4. The code is light.
5. Support multiple types and compression strategies.
6. Supports multiple languages and cross-platforms.

Packable currently implements version of Java, C++, C#, Objective-C, Go.

## 2. Example
Here is usage of Java version,  and APIs on other platform are similar to Java.

The jar has been published to Maven Central：
```gradle
dependencies {
    implementation 'io.github.billywei01:packable:2.0.1'
}
```

Suppose there are two objects like this:

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
        encoder.putString(0, msg).putPackableArray(1, items);
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
2. Implement the "encode()" method,  to encode each field (PackEncoder provides various types of API) <br>
3. Call the PackEncoder.marshal() method, transfer the object, and get the byte array.

#### Deserialization
1. Create a static object, which is an instance of PackCreator<br>
2. Implement the "decode()" method, decode each field, and assign them to the object;<br>
3. Call PackDecoder.unmarshal(), transfer the byte array and PackCreator instance.

If you need to deserialize an array of objects, you need to create an instance of PackArrayCreator (Only Java version need to do this,  other platform no need to do this).  <br>

### 2.2 Coded Directly
The  example above is only one of the examples. It can be used flexibly.
1. PackCreator does not have to be created in the class which implements Packable. It can be created anywhere else.
2. If you only need serialization, you just need to implement Packable. In that case, it's unnecessary to create instance of PackCreator.
3. You can directly encode / decode the message, no need to create Class which implement Packable.

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



## 3. Benchmark

Space of serialization bytes:

| | Space (byte) 
---|---
packable | 2564756 (56%)
protobuf  | 2627081 (59%)
gson       | 4427344 (100%)

Process time:

1. Macbook Pro

| |Serialization (ms)| Deserialization (ms)
---|---|---
packable | 8 | 8
protobuf |16 | 17
gson     | 58 | 50

# 4. Format
See: [Packable Format](format.md)


## License
See the [LICENSE](LICENSE) file for license rights and limitations.
