# Packable Format

## 1. Basic Encoding

The basic structure of message:

```
[key-values|key-values|...]
```

The structure of key-value:

```
<flag> <type> <index> [length] [data]
```

```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  flag  | type  |    index    |            value           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  1bit  | 3bit  |   4~12 bit  |                            |
```

- **index**: Tag of key-values.
- **flag**: Indicate the range of index.<br>
When flag = 0, index range from 0 to 15, [flag|type|index] takes one byte;
When flag = 1, index range from 16 to 255, [flag|type|0000] takes one byte, index takes another byte.

So now Packable support 256 fields for one level (message can nest), 256 is enough for most of case, if we need more fields one day in the future, we could use the last four bits of the first byte.

- **type**:

Type |Meaning|User For
---|---|---
0 |TYPE_0      |  false, 0，empty object
1 |TYPE_NUM_8  |  boolean, byte, short, int, long
2 |TYPE_NUM_16 |  short, int, long
3 |TYPE_NUM_32 |  int, long, float
4 |TYPE_NUM_64 |  long,  double
5 |TYPE_VAR_8   | object with variable length, lenght in [1,255]
6 |TYPE_VAR_16  | object with variable length, lenght in [256,65535]
7 |TYPE_VAR_32  | object with variable length, lenght > 65535

Rule of type:
1. If the value is [false, 0, object that lenght is 0], type value is 0, no value part.
2. If the value is primary type, type range from 1 to 4, decided by range of value. For example, if value range from 1 to 255, type will be 1(TYPE_NUM_8), and values takes one byte, and so on.
3. If the value is object with variable length, like string, array, or custom object, the value part will contain "value_lenght" and "value_content". <br>
The type decides the range of "value_lenght" and how many bytes it takes.<br>
If lenght of object is 0, type will be TYPE_0 (rule 1).
4. If the value is string, encode with utf-8.


## 2. Array Encoding

To simpify the description, we make a definition:
```
key = <flag> <type> <index>
```

### 2.1 Array of Primary Type

```
<key> [length] [v1 v2 ...]
```
- The elements of array encode with little-endian.
- Because of the size of primary value is fixed, we could caculate the size of array. <br>
For example, if type of elements  is int/loat, sizeOf(int/float) = 4, sizeOf(array) = lenght / 4.

### 2.2 Array of String
```
<key> [length] [size] [len1 v1 len2 v2 ...]
```
- If size=0, that says the array is empty, then the type will be 0, and no need to encode the value part.
- Otherwise size will positive. Because the size is small in most of case, we use [varint](https://en.wikipedia.org/wiki/Variable-length_quantity) encoding to encode the size.
- And we use varint encoding to encode the lenght of element.
- If the element(a string) is null, we make len=-1.

### 2.3 Array of Custom Object
```
<key> [length] [size] [len1 v1 len2 v2 ...]
```
The structure of custom object array is same as string array.
But the encoding of len(lenght of element) is different.
- when element=null, len=0xFFFF
- when len<=0x7FFF, len takes 2 bytes
- when len>0x7FFF, len takes 4 bytes

Why not use varint encoding like string array?
Because we don't know many bytes the object will takes before we encode it. <br>
One solution is make an interface for object to implement, to calculate the lenght, but that will increase works and complexity, especially when there're nested objects(recursive). <br>
Another solution: reserve some spaces for 'len', fill it after encoding. But how many bytes should we reserve? <br>
4 bytes? It's a waste of space.<br>
Use varint encoding? Too hard to predict how many bytes to reserve.<br>
So we can make a compromise: we reserve 2 bytes before encoding, if the len of object less then 32768(15K), it could be saved in that 2 byets, otherwise we move the encoded bytes to reserve 4 bytes for the len. <br>
In most of case object's lenght is less then 15K, in that case we don't need move bytes.

### 2.4 Map (Dictionary)
```
<key> [length] [size] [k1 v1 k2 v2 ...]
```
We can regard the map as a specail array, the structure of map is similar to obejct array.
Key and value could be any type, when it's primary type, encodes with little-endian, when it's object, encodes with object rule.

## 3. Compressing Encoding
Some kind of data can use compressing strategy to make message shorter.
It should be noted the strategys can't always make message shorter, they take effect only if the data feature match.
The compressing strategys are optional, you could decide to use them or not by data feature.

#### 3.1 Zigzag
For negative number, the highest bit is 1, so they need to take max bytes(4 or 8) to save the value.
In some case the value is small negative number, can it be saved with less bytes?
Zigzag encoding might be helpful: 

 
```java
(n << 1) ^ (n >> 31) // encode
(n >>> 1) ^ -(n & 1) // decode
```


|positive | negative | zigzag 
|---|---|---
|0 | | 0
||-1|1
|1| | 2|
| | -2 | 3
|...|| 
|2147483647| | 4294967294|
| |-2147483647| 4294967295|

After zigzag encode, numbers reorder by absolute value.
So if the absolute value of value is small, it can be saved with less bytes(review the rule of type), otherwise there's no benifit for space, and waste of time.


#### 3.2 Double
Can double value be compressed?
Here is some double values present in binary:

```
a:-2.0         1 1000000-0000 0000-00000000-00000000-00000000-00000000-00000000-00000000
a:-1.0         1 0111111-1111 0000-00000000-00000000-00000000-00000000-00000000-00000000
a:0.0          0 0000000-0000 0000-00000000-00000000-00000000-00000000-00000000-00000000
a:0.5          0 0111111-1110 0000-00000000-00000000-00000000-00000000-00000000-00000000
a:1.0          0 0111111-1111 0000-00000000-00000000-00000000-00000000-00000000-00000000
a:1.5          0 0111111-1111 1000-00000000-00000000-00000000-00000000-00000000-00000000
a:2.0          0 1000000-0000 0000-00000000-00000000-00000000-00000000-00000000-00000000
a:3.98         0 1000000-0000 1111-11010111-00001010-00111101-01110000-10100011-11010111
a:31.0         0 1000000-0011 1111-00000000-00000000-00000000-00000000-00000000-00000000
a:32.0         0 1000000-0100 0000-00000000-00000000-00000000-00000000-00000000-00000000
a:33.0         0 1000000-0100 0000-10000000-00000000-00000000-00000000-00000000-00000000
a:2097151.0    0 1000001-0011 1111-11111111-11111111-00000000-00000000-00000000-00000000
a:2097152.0    0 1000001-0100 0000-00000000-00000000-00000000-00000000-00000000-00000000
a:2097153.0    0 1000001-0100 0000-00000000-00000000-10000000-00000000-00000000-00000000
```

- To some values can be composed by 2^n, the low bytes is 0.
- If the value is is 'integer' and less then 2^21(2997152), the lowest 4 bytes is 0.

So we supply the optional encode: swap the highest 4 bytes and the lowest four bytes, then encode with the rule of long value.
For the double value that match such feature, the strategy can save 4 byets.

#### 3.3 Boolean Array
```
<key> [length] [remain] [v1 v2  ...]
```
It's a waste to save one boolean value with one byte, actually we can save one value with one bit.
The size of array is not always times of 8, so we need to record the size.
we can record the remain of size (size % 8), and calculate the size by lenght and remain when decoding.
No matter how large the size is, remain always can save with 3 bits.

#### 3.4 Enum Array
- When the enum has 2 options, we can use 1 bit for one value;
- When the enum has 4 options, 2 bits for one value;
- And so on.
- If the enum has more than 255 options, just use array of interger.

The structure of Enun Array is same as boolean array.


#### 3.5 Int/Long/Double Array
If most of values in array are small and match the compressing strategy, 
we can use 2 bits to indicate how many bytes the value takes. <br>
Here is the bytes to save:

bits | 0| 1| 2| 3
---|---|---|---|---
int |-|[0,7]|[0,15]|[0,31]
long |-|[0,7]|[0,15]|[0,63]
double |-|[48-63]|[32,63]|[0,63]

The structure:
```
<key> [length] [size] [bits] [v1 v2  ...]
```
- size: Size of array, encode with varint.
- bits: The sequences of extra bits.


