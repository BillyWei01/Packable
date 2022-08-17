
#import "PackDecoder.h"
#import "PackConfig.h"
#import "NumberType.h"
#import "LongArrayPool.h"
#import "TagFormat.h"


static const uint64_t NULL_FLAG = ~0L;
static const uint64_t INT_64_MIN_VALUE = 0x8000000000000000;
static const uint64_t INT_MASK = 0xffffffffL;

@implementation PackDecoder

+(id<Packable>)unmarshal:(uint8*)bytes length:(int)len withCreator:(id<PackCreator>)creator{
    PackDecoder* decoder = [[PackDecoder alloc] initWithBytes:bytes andOffset:0 andLen:len];
    return [creator decode:decoder];
}

-(id)initWithBytes:(uint8*)bytes andOffset:(int)offset andLen:(int)len{
    self = [super init];
    if(self != nil){
        infoArray = NULL;
        infoLength = 0;
        maxIndex = -1;
        buffer = [[PackBuffer alloc]initWithBytes:bytes andOffset:offset andLimit:len];
    }
    return self;
}

-(void)dealloc {
    if(infoArray != NULL){
        [LongArrayPool recycleLongArray:infoArray withLen:infoLength];
    }
}

-(void)throwException:(NSString*)msg  {
    @throw([NSException exceptionWithName:@"PackDecoder" reason:msg userInfo:nil]);
}

-(void)parseBuffer{
    uint64_t existFlag = 0;
    uint64_t *existFlags = NULL;
    
    if(infoArray == NULL){
        infoArray = [LongArrayPool getLongArray:LONG_ARRAY_DEFAULT_SIZE];
        infoLength = LONG_ARRAY_DEFAULT_SIZE;
    }
    
    while([buffer hasRemaining]){
        uint8 tag = [buffer readByte];
        int index = (tag & BIG_INDEX_MASK) == 0 ? tag & INDEX_MASK : [buffer readByte];
        if(index > maxIndex){
            maxIndex = index;
        }
        if(index < 64){
            existFlag |= 1 << index;
        }else{
            if(existFlags == NULL){
                existFlags = (uint64_t*)calloc(4, 8);
            }
            existFlags[index >> 8] |= 1 << (index &0x3f);
        }
        
        if(index >= infoLength){
            uint64_t* oldArray = infoArray;
            int newLength = infoLength << 1;
            while(index >= newLength){
                newLength = newLength << 1;
            }
            infoArray = [LongArrayPool getLongArray:newLength];
            memcpy(infoArray, oldArray, infoLength << 3);
            [LongArrayPool recycleLongArray:oldArray withLen:infoLength];
            infoLength = newLength;
        }
        
        uint8 type = tag & TYPE_MASK;
        if (type <= TYPE_NUM_64) {
            if (type == TYPE_0) {
                infoArray[index] = 0L;
            } else if (type == TYPE_NUM_8) {
                infoArray[index] = [buffer readByte];
            } else if (type == TYPE_NUM_16) {
                infoArray[index] = (uint16_t) [buffer readShort];
            } else if (type == TYPE_NUM_32) {
                infoArray[index] = (uint32_t) [buffer readInt];
            } else {
                // In case of not able to tell value missing (which infoArray[index] = NULL_FLAG) or value = NULL_FLAG,
                // We use the highest bit of int64_t value to indicate that the infoArray[index]
                // is actually value (when positive) or position of value (mask highest bit to be one 1)
                uint64_t x = [buffer readInt64];
                if ((x & INT_64_MIN_VALUE) == 0) {
                    infoArray[index] = x;
                } else {
                    infoArray[index] = (buffer.position - 8) | INT_64_MIN_VALUE;
                }
            }
        } else {
            int size;
            if (type == TYPE_VAR_8) {
                size = [buffer readByte];
            } else if (type == TYPE_VAR_16) {
                size = (uint16_t) [buffer readShort];
            } else {
                size = [buffer readInt];
            }
            infoArray[index] = (((uint64_t) buffer.position) << 32) | (uint64_t) size;
            buffer.position += size;
        }
    }
    
    if(buffer.position != buffer.limit){
        [self throwException:@"invalid pack data"];
    }
    
    if(maxIndex <= 0){
        return;
    }
    
    int bits = 63 - maxIndex;
    uint64_t flippedFlag = (~existFlag) << bits;
    if(flippedFlag == 0){
        return;
    }
    flippedFlag >>= bits;
    uint64_t *pInfo = infoArray;
    do {
        if ((flippedFlag & 1) != 0) {
            *pInfo = NULL_FLAG;
        }
        pInfo++;
        flippedFlag >>= 1;
    } while (flippedFlag != 0);
    
    if (maxIndex >= 64) {
        for (int i = 64; i < maxIndex; i++) {
            if ((existFlags[i >> 8] & (1 << (i & 0x3F))) == 0) {
                infoArray[i] = NULL_FLAG;
            }
        }
        free(existFlags);
    }
}

-(uint64_t)getInfo:(int)index{
    if(maxIndex < 0){
        [self parseBuffer];
    }
    if(index > maxIndex){
        return NULL_FLAG;
    }
    return infoArray[index];
}

-(int)getSize:(int)index{
    uint64_t info = [self getInfo:index];
    if(info == NULL_FLAG){
        return -1;
    }
    int total = (int)(info & INT_MASK);
    if(total == 0){
        return 0;
    }
    buffer.position = info >> 32;
    int n = [buffer readVarint32];
    if(n < 0 || n > MAX_OBJECT_ARRAY_SIZE){
        [self throwException:@"invalid size of object array"];
    }
    return n;
}

-(bool)contains:(int)index{
    return [self getInfo:index] != NULL_FLAG;
}

-(PackBuffer*) getCustom:(int)index{
    uint64_t info = [self getInfo:index];
    if(info == NULL_FLAG){
        return nil;
    }
    int len = (int) (info & INT_MASK);
    if (len == 0){
        buffer.position = buffer.limit;
    }else{
        buffer.position = info >> 32;
    }
    return buffer;
}

-(bool)getBool:(int)index withDefault:(bool)defValue{
    uint64_t info = [self getInfo:index];
    return info == NULL_FLAG ? defValue : info == 1;
}

-(bool)getBool:(int)index{
    return [self getInfo:index] == 1;
}

-(uint8)getByte:(int)index withDefault:(uint8)defValue{
    uint64_t info = [self getInfo:index];
    return info == NULL_FLAG ? defValue : (uint8) info;
}

-(uint8)getByte:(int)index{
    return [self getByte:index withDefault:0];
}

-(short)getShort:(int)index withDefault:(short)defValue{
    uint64_t info = [self getInfo:index];
    return info == NULL_FLAG ? defValue : (short) info;
}

-(short)getShort:(int)index{
    return [self getShort:index withDefault:0];
}

-(int)getInt:(int)index withDefault:(int)defValue{
    uint64_t info = [self getInfo:index];
    return info == NULL_FLAG ? defValue : (int) info;
}
-(int)getInt:(int)index{
    return [self getInt:index withDefault:0];
}

-(int64_t)getInt64:(int)index withDefault:(int64_t)defValue{
    uint64_t info = [self getInfo:index];
    if(info == NULL_FLAG){
        return defValue;
    }
    return info < INT_64_MIN_VALUE ? info: [buffer readInt64:(int)(info & INT_MASK)];
}

-(int64_t)getInt64:(int)index{
    return [self getInt64:index withDefault:0];
}

-(float)getFloat:(int)index withDefault:(float)defValue{
    uint64_t info = [self getInfo:index];
    if(info == NULL_FLAG){
        return defValue;
    }
    int x = (int)info;
    return *((float*)&x);
}

-(float)getFloat:(int)index{
    return [self getFloat:index withDefault:0];
}

-(double)getDouble:(int)index withDefault:(double)defValue{
    uint64_t info = [self getInfo:index];
    if(info == NULL_FLAG){
        return defValue;
    }
    uint64_t x = info < INT_64_MIN_VALUE ? info : [buffer readInt64:(int)(info & INT_MASK)];
    return *((double *) &x);
}

-(double)getDouble:(int)index {
    return [self getDouble:index withDefault:0];
}

-(NSString*)getString:(int)index withDefault:(NSString*)defValue{
    uint64_t info = [self getInfo:index];
    if(info == NULL_FLAG){
        return defValue;
    }
    int len = (int) (info & INT_MASK);
    if (len == 0) {
        return @"";
    }
    int offset = info >> 32;
    return [[NSString alloc] initWithBytes:(buffer.hb+offset) length:len encoding:NSUTF8StringEncoding];
}

-(NSString*)getString:(int)index{
    return [self getString:index withDefault:nil];
}

-(NSMutableArray<NSString*>*)getStringArray:(int)index{
    int n = [self getSize:index];
    if(n < 0){
        return nil;
    }
    NSMutableArray<NSString*> *value = [NSMutableArray<NSString*> arrayWithCapacity:n];
    for(int i = 0; i < n;i++){
        NSString* t = [self takeString];
        if(t == nil){
            [value addObject:[NSNull null]];
        }else{
            [value addObject:t];
        }
    }
    return value;
}

-(NSString*)takeString{
    int len = [buffer readVarint32];
    if(len < 0){
        return nil;
    }else{
        if(len == 0){
            return @"";
        }
        int offset = buffer.position;
        [buffer checkBould:offset andLen:len];
        NSString* str = [[NSString alloc] initWithBytes:(buffer.hb+offset) length:len encoding:NSUTF8StringEncoding];
        buffer.position += len;
        return str;
    }
}

-(id<Packable>)getPackable:(int)index withCreator:(id<PackCreator>)creator{
    uint64_t info = [self getInfo:index];
    if(info == NULL_FLAG){
        return nil;
    }
    int offset =  info >> 32;
    int len = (int) (info & INT_MASK);
    PackDecoder* decoder = [[PackDecoder alloc]initWithBytes:buffer.hb andOffset:offset andLen:len];
    id<Packable> t = [creator decode:decoder];
    return t;
}

-(NSMutableArray<Packable>*)getPackableArray:(int)index withCreator:(id<PackCreator>)creator{
    int n = [self getSize:index];
    if(n < 0){
        return nil;
    }
    NSMutableArray<Packable> *value = [NSMutableArray<Packable> arrayWithCapacity:n];
    for(int i = 0; i < n;i++){
        id<Packable> t = [self takePackbale:creator];
        if(t == nil){
            [value addObject:[NSNull null]];
        }else{
            [value addObject:t];
        }
    }
    return value;
}

-(id<Packable>)takePackbale:(id<PackCreator>)creator{
    short a = [buffer readShort];
    if(a == NULL_PACKABLE){
        return nil;
    }else{
        int len = a >= 0 ? a : ((a & 0x7fff) << 16) | ([buffer readShort] & 0xffff);
        int offset = buffer.position;
        [buffer checkBould:offset andLen:len];
        PackDecoder* decoder =[[PackDecoder alloc]initWithBytes:buffer.hb andOffset:offset andLen:len];
        id<Packable> t = [creator decode:decoder];
        buffer.position +=  len;
        return t;
    }
}

-(uint8*) getByteArray:(int)index outCount:(int*)count{
    uint64_t info = [self getInfo:index];
    if(info == NULL_FLAG){
        *count = 0;
        return nil;
    }
    buffer.position = info >> 32;
    int len = (int) (info & INT_MASK);
    *count = len;
    uint8* bytes = (uint8*)malloc(len);
    [buffer readBytes:bytes withSize:len];
    return bytes;
}

-(int*) getIntArray:(int)index outCount:(int*)count{
    uint64_t info = [self getInfo:index];
    if(info == NULL_FLAG){
        *count = 0;
        return nil;
    }
    buffer.position = info >> 32;
    int len = (int) (info & INT_MASK);
    if((len & 0x3) != 0){
        [self throwException:@"invalid array lenght"];
    }
    *count = len >> 2;
    int *value = (int*)malloc(len);
    memcpy(value, buffer.hb + buffer.position, len);
    buffer.position += len;
    return value;
}

-(int64_t*) getInt64Array:(int)index outCount:(int*)count{
    uint64_t info = [self getInfo:index];
    if(info == NULL_FLAG){
        *count = 0;
        return nil;
    }
    buffer.position = info >> 32;
    int len = (int) (info & INT_MASK);
    if((len & 0x7) != 0){
        [self throwException:@"invalid array lenght"];
    }
    *count = len >> 3;
    int64_t *value = (int64_t*)malloc(len);
    memcpy(value, buffer.hb + buffer.position, len);
    buffer.position += len;
    return value;
}

-(float*) getFloatArray:(int)index outCount:(int*)count{
    return (float*)[self getIntArray:index outCount:count];
}

-(double*) getDoubleArray:(int)index outCount:(int*)count{
    return (double*)[self getInt64Array:index outCount:count];
}

-(bool*)getBoolArray:(int)index outCount:(int*)count{
    uint64_t info = [self getInfo:index];
    if (info == NULL_FLAG) {
        *count = 0;
        return nil;
    }
    int len = (int) (info & INT_MASK);
    if (len == 0) {
        *count = 0;
        return (bool*)malloc(0);
    }
    
    buffer.position = ((uint64_t) info) >> 32;
    bool *a;
    if (len == 1) {
        uint8_t b = [buffer readByte];
        int n = b >> 5;
        a = (bool*)malloc(n);
        *count = n;
        for (int i = 0; i < n; i++) {
            a[i] = b & 0x1;
            b >>= 1;
        }
    } else {
        int remain = [buffer readByte];
        if ((remain >> 3) != 0) {
            [self throwException:@"remain overflow"];
        }
        int byteCount = len - 1;
        int n = (byteCount << 3) - (remain > 0 ? 8 - remain : 0);
        a = (bool*)malloc(n);
        *count = n;
        int i = 0;
        while (true) {
            uint8_t b = (uint8_t) buffer.hb[buffer.position++];
            int j = i;
            i += 8;
            if (i < n) {
                while (b != 0) {
                    a[j++] = b & 1;
                    b >>= 1;
                }
                while (j < i) {
                    a[j++] = false;
                }
            } else {
                while (j < n) {
                    a[j++] = b & 1;
                    b >>= 1;
                }
                break;
            }
        }
    }
    return a;
}

-(int*)getEnumArray:(int)index outCount:(int*)count{
    uint64_t info = [self getInfo:index];
    if (info == NULL_FLAG) {
        *count = 0;
        return nil;
    }
    int len = (int) (info & INT_MASK);
    if (len == 0) {
        *count = 0;
        return (int*)malloc(0);
    }
    
    buffer.position = ((uint64_t) info) >> 32;
    int bitInfo = [buffer readByte];
    if((bitInfo >> 5) != 0){
        [self throwException:@"bit info overflow"];
    }
    
    int bitShift = bitInfo >> 3;
    int byteCount = len - 1;
    int *a;
    if (bitShift == 3) {
        a = (int*)malloc(byteCount * 4);
        *count = byteCount;
        uint8_t *pB = (uint8_t *) (buffer.hb + buffer.position);
        for (int i = 0; i < byteCount; i++) {
            a[i] = pB[i];
        }
    } else {
        int remain = bitInfo & 0x7;
        int indexShift = 3 - bitShift;
        int n = (byteCount << indexShift);
        if (remain > 0) {
            n -= ((8 - remain) >> bitShift);
        }
        int pos = buffer.position;
        int byteShirt = 1 << bitShift;
        int valueMask = ~(0xffffffff << byteShirt);
        int step = 1 << indexShift;
        a =  (int*)malloc(n * 4);
        *count = n;
        int i = 0;
        while (true) {
            int b = (uint8_t) buffer.hb[pos + (i >> indexShift)];
            int j = i;
            i += step;
            if (i < n) {
                while (b != 0) {
                    a[j++] = b & valueMask;
                    b >>= byteShirt;
                }
                while (j < i) {
                    a[j++] = 0;
                }
            } else {
                while (j < n) {
                    a[j++] = b & valueMask;
                    b >>= byteShirt;
                }
                break;
            }
        }
    }
    buffer.position += byteCount;
    return a;
}

+(void)allocateArray: (union ArrayWrapper *) wrapper andType:(int)type count:(int) n {
    if (type == INT) {
        wrapper->intArray = (int*)malloc(n*4);
    } else if (type == INT64) {
        wrapper->int64Array = (int64_t*)malloc(n*8);
    } else {
        wrapper->doubleArray = (double*)malloc(n*8);
    }
}

-(void)takeArray:(int) n flagPosition:(int) pFlag  andWrapper:(union ArrayWrapper*)wrapper andType:(int) type {
    int j = 0;
    for (int i = 0; i < n;) {
        int b = (uint8_t) buffer.hb[pFlag + (i >> 2)];
        j = i;
        i = MIN(i + 4, n);
        if (type == DOUBLE) {
            while (b != 0) {
                int flag = b & 0x3;
                int64_t x;
                if (flag == 0) {
                    x = 0;
                } else if (flag == 1) {
                    x = ((int64_t) [buffer readShort]) << 48;
                } else if (flag == 2) {
                    x = ((int64_t) [buffer readInt]) << 32;
                } else {
                    x = [buffer readInt64];
                }
                wrapper->doubleArray[j++] = *(double *) (&x);
                if (j == i) {
                    break;
                }
                b >>= 2;
            }
            while (j < i) {
                wrapper->doubleArray[j++] = 0;
            }
        } else {
            while (b != 0) {
                int flag = b & 0x3;
                if (flag < 3) {
                    int64_t x;
                    if (flag == 0) {
                        x = 0;
                    } else if (flag == 1) {
                        x = (uint8_t) [buffer readByte];
                    } else  {
                        x = (uint16_t) [buffer readShort];
                    }
                    if (type == INT) {
                        wrapper->intArray[j++] = (int) x;
                    } else {
                        wrapper->int64Array[j++] = x;
                    }
                } else {
                    if (type == INT) {
                        wrapper->intArray[j++] = [buffer readInt];
                    } else {
                        wrapper->int64Array[j++] = [buffer readInt64];
                    }
                }
                if (j == i) {
                    break;
                }
                b >>= 2;
            }
            if (type == INT) {
                while (j < i) wrapper->intArray[j++] = 0;
            } else {
                while (j < i) wrapper->int64Array[j++] = 0;
            }
        }
    }
}

-(void)getNumberArray:(int)index andWrapper:(union ArrayWrapper*)wrapper andType:(int)type outCount:(int*)count {
    long info =  [self getInfo:index];
    if (info == NULL_FLAG) {
        *count = 0;
        return;
    }
    int len = (int) (info & INT_MASK);
    if (len == 0) {
        *count = 0;
        [PackDecoder allocateArray:wrapper andType:type count:0];
        return;
    }
    
    buffer.position = ((uint64_t) info) >> 32;
    int n = [buffer readVarint32];
    if (n < 0) {
        [self throwException:@"invalid array size"];
        return;
    }
    int pFlag = buffer.position;
    int totalBits = n << 1;
    int byteCount = (totalBits >> 3) + ((totalBits & 0x7) != 0 ? 1 : 0);
    [buffer checkBould: buffer.position andLen:byteCount];
    buffer.position += byteCount;
    
    *count = n;
    [PackDecoder allocateArray:wrapper andType:type count: n];
    [self takeArray:n flagPosition:pFlag andWrapper:wrapper andType:type];
}

-(int*)getCompactIntArray:(int)index outCount:(int*)count {
    union ArrayWrapper wrapper;
    wrapper.intArray = nil;
    [self getNumberArray:index andWrapper:&wrapper andType:INT outCount:count];
    return wrapper.intArray;
}

-(int64_t *)getCompactInt64Array:(int)index outCount:(int*)count {
    union ArrayWrapper wrapper;
    wrapper.int64Array = nil;
    [self getNumberArray:index andWrapper:&wrapper andType:INT64 outCount:count];
    return wrapper.int64Array;
}


-(double*)getCompactDoubleArray:(int)index outCount:(int*)count {
    union ArrayWrapper wrapper;
    wrapper.doubleArray = nil;
    [self getNumberArray:index andWrapper:&wrapper andType:DOUBLE outCount:count];
    return wrapper.doubleArray;
}


-(NSMutableDictionary<NSString*,NSString*>*)getStr2Str:(int)index{
    int n = [self getSize:index];
    if(n < 0){
        return nil;
    }
    NSMutableDictionary<NSString*,NSString*> *value = [NSMutableDictionary<NSString*,NSString*> new];
    for(int i = 0; i < n;i++){
        NSString* k = [self takeString];
        NSString* v = [self takeString];
        [value setObject:v forKey:k];
    }
    return value;
}

-(NSMutableDictionary<NSString*,id<Packable>>*)getStr2Pack:(int)index withCreator:(id<PackCreator>)creator{
    int n = [self getSize:index];
    if(n < 0){
        return nil;
    }
    NSMutableDictionary<NSString*,id<Packable>> *value = [NSMutableDictionary<NSString*,id<Packable>> new];
    for(int i = 0; i < n;i++){
        NSString* k = [self takeString];
        id<Packable> v = [self takePackbale:creator];
        if(v != nil){
            [value setObject:v forKey:k];
        }
    }
    return value;
}

@end
