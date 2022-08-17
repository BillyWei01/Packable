
#import "PackEncoder.h"

#import "TagFormat.h"
#import "PackConfig.h"
#import "NumberType.h"
#import <os/lock.h>


static int sMaxAllocated = 0;

static const int DEFAULT_BUFFER_LEN = 8192;
static const int DEFAULT_BUFFER_CAPACITY = 8;
static uint8* defaultBuffer[DEFAULT_BUFFER_CAPACITY];
static int bufferCount = 0;
static os_unfair_lock bufferLock;


@implementation PackEncoder
-(id)init{
    self = [super init];
    if(self != nil){
        uint8_t* bytes = [PackEncoder getDefaultArray];
        buffer = [[PackBuffer alloc] initWithBytes:bytes andOffset:0 andLimit:DEFAULT_BUFFER_LEN];
    }
    return self;
}

-(void)dealloc  {
    if(buffer.limit == DEFAULT_BUFFER_LEN){
        [PackEncoder recycleDefaultArray:buffer.hb];
    }else{
        free(buffer.hb);
    }
    // buffer = nil;
}

+(uint8*)marshal:(id<Packable>)packable outCount:(int*)count{
    PackEncoder* encoder = [[PackEncoder alloc]init];
    [packable encode:encoder];
    return [encoder getBytes:count];
}

-(uint8*)getBytes:(int*)count{
    int n = buffer.position;
    *count = n;
    uint8* bytes = (uint8*)malloc(n);
    memcpy(bytes, buffer.hb, n);
    return bytes;
}

-(PackBuffer*)getBuffer{
    return buffer;
}

+(uint8*)getDefaultArray{
    os_unfair_lock_lock(&bufferLock);
    uint8* a;
    if(bufferCount > 0){
        a = defaultBuffer[--bufferCount];
    }else{
        a =(uint8*)malloc(DEFAULT_BUFFER_LEN);
    }
    os_unfair_lock_unlock(&bufferLock);
    return a;
}

+(void)recycleDefaultArray:(uint8*)bytes{
    os_unfair_lock_lock(&bufferLock);
    if(bufferCount < DEFAULT_BUFFER_CAPACITY){
        defaultBuffer[bufferCount++] = bytes;
    }else{
        free(bytes);
    }
    os_unfair_lock_unlock(&bufferLock);
}

-(void)throwException:(NSString*)msg  {
    @throw([NSException exceptionWithName:@"PackEncoder" reason:msg userInfo:nil]);
}

-(void)checkCapacity:(int)expandSize{
    int capacity = buffer.limit;
    int desSize = buffer.position + expandSize;
    if (desSize > capacity) {
        if (desSize > MAX_BUFFER_SIZE) {
            [self throwException:@"desire capacity over limit"];
        }
        int newSize = capacity << 1;
        while (desSize > newSize) {
            newSize = newSize << 1;
        }
        int doubleLimit = MIN(sMaxAllocated, DOUBLE_BUFFER_LIMIT);
        if (newSize < doubleLimit) {
            newSize = newSize << 1;
        }
        if (newSize > sMaxAllocated) {
            sMaxAllocated = newSize;
        }
        uint8 *oldArray = buffer.hb;
        uint8 *newArray = (uint8*)malloc(newSize);
        memcpy(newArray, buffer.hb, buffer.position);
        buffer.hb = newArray;
        buffer.limit = newSize;
        if(capacity == DEFAULT_BUFFER_LEN){
            [PackEncoder recycleDefaultArray:oldArray];
        }else{
            free(oldArray);
        }
    }
}

-(void)putIndex:(uint8)index{
    if (index >= LITTLE_INDEX_BOUND) {
        [buffer writeByte:BIG_INDEX_MASK];
    }
    [buffer writeByte:index];
}

-(void)wrapTagAndLength:(uint8_t)index length:(int)len{
    [self checkCapacity:6+len];
    if(len == 0){
        [self putIndex:index];
    }else{
        int pos = buffer.position;
        [self putIndex:index];
        if (len <= 0xff) {
            buffer.hb[pos] |= TYPE_VAR_8;
            [buffer writeByte:len];
        } else if (len <= 0xffff) {
            buffer.hb[pos] |= TYPE_VAR_16;
            [buffer writeShort:len];
        } else {
            buffer.hb[pos] |= TYPE_VAR_32;
            [buffer writeInt:len];
        }
    }
}

-(PackBuffer*)putCustom:(uint8)index withLen:(uint8)len{
    [self wrapTagAndLength:index length:len];
    return buffer;
}

-(int64_t)wrapObjectArrayHeader:(uint8_t)index andSize:(int)size{
    if(size > MAX_OBJECT_ARRAY_SIZE){
        [self throwException:@"object array size out of limit"];
    }
    [self checkCapacity:11];
    int64_t pTag = buffer.position;
    [self putIndex:index];
    if(size <= 0){
        return -1;
    }
    buffer.position += 4;
    int64_t pValue = buffer.position;
    [buffer writeVarint32:size];
    return (pTag << 32) | pValue;
}

-(void)putLen:(int)pTag andPValue:(int)pValue{
    int len = buffer.position - pValue;
    if(len <= TRIM_SIZE_LIMIT){
        buffer.hb[pTag] |= TYPE_VAR_8;
        buffer.hb[pValue - 4] = (char) len;
        memmove(buffer.hb + (pValue - 3), buffer.hb + pValue, len);
        buffer.position -= 3;
    } else {
        buffer.hb[pTag] |= TYPE_VAR_32;
        [buffer writeInt:pValue-4 withValue:len];
    }
}

-(void)putByte:(uint8)index withValue:(uint8)value{
    [self checkCapacity:3];
    if(value == 0){
        [self putIndex:index];
    }else{
        if(index < LITTLE_INDEX_BOUND){
            [buffer writeByte:index | TYPE_NUM_8];
        }else{
            [buffer writeByte:BIG_INDEX_MASK|TYPE_NUM_8];
            [buffer writeByte:index];
        }
        [buffer writeByte:value];
    }
}

-(void)putBool:(uint8)index withValue:(bool)value{
    [self putByte:index withValue:value];
}

-(void)putShort:(uint8)index withValue:(short)value{
    [self checkCapacity:4];
    if(value == 0){
        [self  putIndex:index];
    }else{
        int pos = buffer.position;
        [self putIndex:index];
        if((value >> 8) == 0){
            buffer.hb[pos] |= TYPE_NUM_8;
            [buffer writeByte:value];
        }else{
            buffer.hb[pos] |= TYPE_NUM_16;
            [buffer writeShort:value];
        }
    }
}

-(void)putInt:(uint8)index withValue:(int)value{
    [self checkCapacity:6];
    if(value == 0){
        [self putIndex:index];
    }else{
        int pos = buffer.position;
        [self putIndex:index];
        if((value>>8)==0){
            buffer.hb[pos] |= TYPE_NUM_8;
            [buffer writeByte:value];
        }else if((value >> 16) == 0){
            buffer.hb[pos] |= TYPE_NUM_16;
            [buffer writeShort:value];
        }else{
            buffer.hb[pos] |= TYPE_NUM_32;
            [buffer writeInt:value];
        }
    }
}


-(void)putInt64:(uint8)index withValue:(int64_t)value{
    [self checkCapacity:10];
    if(value == 0){
        [self putIndex:index];
    }else{
        int pos = buffer.position;
        [self putIndex:index];
        if((value >> 32) != 0){
            buffer.hb[pos] |= TYPE_NUM_64;
            [buffer writeInt64:value];
        } else if((value>>8)==0){
            buffer.hb[pos] |= TYPE_NUM_8;
            [buffer writeByte:value];
        }else if((value >> 16) == 0){
            buffer.hb[pos] |= TYPE_NUM_16;
            [buffer writeShort:(short)value];
        }else {
            buffer.hb[pos] |= TYPE_NUM_32;
            [buffer writeInt:(int)value];
        }
    }
}

-(void)putFloat:(uint8)index withValue:(float)value{
    [self checkCapacity:6];
    if(value == 0){
        [self putIndex:index];
    }else{
        if(index < LITTLE_INDEX_BOUND){
            [buffer writeByte:(index | TYPE_NUM_32)];
        }else{
            [buffer writeByte:(BIG_INDEX_MASK | TYPE_NUM_32)];
            [buffer writeByte:index];
        }
        [buffer writeFloat:value];
    }
}

-(void)putDouble:(uint8)index withValue:(double)value{
    [self checkCapacity:10];
    if(value == 0){
        [self putIndex:index];
    }else{
        if(index < LITTLE_INDEX_BOUND){
            [buffer writeByte:(index | TYPE_NUM_64)];
        }else{
            [buffer writeByte:(BIG_INDEX_MASK | TYPE_NUM_64)];
            [buffer writeByte:index];
        }
        [buffer writeDouble:value];
    }
}

-(void)putString:(uint8)index withValue:(NSString*)value{
    if(value == nil){
        return;
    }
    NSData* data = [value dataUsingEncoding:NSUTF8StringEncoding];
    int len = (int)[data length];
    [self wrapTagAndLength:index length:len];
    if(len != 0){
        [buffer writeBytes:(Byte *)[data bytes] withSize:len];
    }
}

-(void)putStringNSArray:(uint8)index withValue:(NSArray<NSString*>*)value{
    if(value == nil){
        return;
    }
    int n = (int)[value count];
    long tagValue = [self wrapObjectArrayHeader:index andSize:n];
    if(tagValue < 0){
        return;
    }
    for(int i = 0; i < n;i++){
        [self wrapString:value[i]];
    }
    [self putLen:(tagValue >> 32) andPValue:(int)tagValue];
}

-(void)putStringMutArray:(uint8)index withValue:(NSMutableArray<NSString*>*)value{
    if(value == nil){
        return;
    }
    int n = (int)[value count];
    long tagValue = [self wrapObjectArrayHeader:index andSize:n];
    if(tagValue < 0){
        return;
    }
    for(int i = 0; i < n;i++){
        [self wrapString:value[i]];
    }
    [self putLen:(tagValue >> 32) andPValue:(int)tagValue];
}

-(void)putPackable:(uint8)index withValue:(id<Packable>)value{
    if(value == nil){
        return;
    }
    [self checkCapacity:6];
    int pTag = buffer.position;
    [self putIndex:index];
    buffer.position += 4;
    int pValue = buffer.position;
    [value encode:self];
    if (pValue == buffer.position) {
        buffer.position -= 4;
    } else {
        [self putLen:pTag andPValue:pValue];
    }
}

-(void)wrapString:(NSString*) str{
    if(str == nil || [str isKindOfClass:[NSNull class]]){
        [self checkCapacity:5];
        [buffer writeVarintNegatie1];
    }else{
        NSData* data = [str dataUsingEncoding:NSUTF8StringEncoding];
        int len = (int)[data length];
        [self checkCapacity:5+len];
        [buffer writeVarint32:len];
        [buffer writeBytes:(Byte *)[data bytes] withSize:len];
    }
}

-(void)putPackableNSArray:(uint8)index withValue:(NSArray*)value{
    if(value == nil){
        return;
    }
    int n = (int)[value count];
    long tagValue = [self wrapObjectArrayHeader:index andSize:n];
    if(tagValue < 0){
        return;
    }
    for(int i = 0; i < n;i++){
        [self wrapPackable:value[i]];
    }
    [self putLen:(tagValue >> 32) andPValue:(int)tagValue];
}

-(void)putPackableMutArray:(uint8)index withValue:(NSMutableArray*)value{
    if(value == nil){
        return;
    }
    int n = (int)[value count];
    long tagValue = [self wrapObjectArrayHeader:index andSize:n];
    if(tagValue < 0){
        return;
    }
    for(int i = 0; i < n;i++){
        [self wrapPackable:value[i]];
    }
    [self putLen:(tagValue >> 32) andPValue:(int)tagValue];
}

-(void)wrapPackable:(id<Packable>)pack{
    [self checkCapacity:2];
    if(pack == nil || [pack isKindOfClass:[NSNull class]]){
        [buffer writeShort:NULL_PACKABLE];
    }else{
        int pLen = buffer.position;
        buffer.position += 2;
        int pPack = buffer.position;
        [pack encode:self];
        int len = buffer.position - pPack;
        if(len <= 0x7fff){
            [buffer writeShort:pLen withValue:len];
        }else{
            [self checkCapacity:2];
            memmove(buffer.hb + (pPack + 2), buffer.hb + pPack, len);
            buffer.position += 2;
            [buffer writeShort:pLen withValue:(short) ((len >> 16) | 0x8000)];
            [buffer writeShort:(pLen +2) withValue:len];
        }
    }
}


-(void)putByteArray:(uint8)index withValue:(uint8*)value count:(int)n{
    if(value == nil || n < 0){
        return;
    }
    [self wrapTagAndLength:index length:n];
    [buffer writeBytes:value withSize:n];
}


-(void)putIntArray:(uint8)index withValue:(int*)value count:(int)n{
    uint8* b = (uint8*)value;
    [self putByteArray:index withValue:b count:n<<2];
}

-(void)putInt64Array:(uint8)index withValue:(int64_t*)value count:(int)n{
    uint8* b = (uint8*)value;
    [self putByteArray:index withValue:b count:n<<3];
}

-(void)putFloatArray:(uint8)index withValue:(float*)value count:(int)n{
    uint8* b = (uint8*)value;
    [self putByteArray:index withValue:b count:n<<2];
}

-(void)putDoubleArray:(uint8)index withValue:(double*)value count:(int)n{
    uint8* b = (uint8*)value;
    [self putByteArray:index withValue:b count:n<<3];
}

//int min(int a, int b){
//    return (a < b) ? a : b;
//}

-(void)putBoolArray:(uint8)index withValue:(bool*)value count:(int)n{
    if(value == nil || n < 0){
        return;
    }
    if(n == 0){
        [self wrapTagAndLength:index length:n];
        return;
    }
    
    if (n <= 5) {
        uint8 b = (char) (n << 5);
        for (int i = 0; i < n; i++) {
            if (value[i]) {
                b |= 1 << i;
            }
        }
        [self wrapTagAndLength:index length:1];
        [buffer writeByte:b];
    } else {
        int remain = n & 0x7;
        int byteCount = (n >> 3) + (remain == 0 ? 1 : 2);
        [self wrapTagAndLength:index length:byteCount];
        [buffer writeByte:remain];
        int i = 0;
        while (i < n) {
            int end = MIN(i + 8, n);
            uint8 b = 0;
            for (int j = i; j < end; j++) {
                if (value[j]) {
                    b |= 1 << (j & 0x7);
                }
            }
            [buffer writeByte:b];
            i = end;
        }
    }
}

-(void)putEnumArray:(uint8)index withValue:(int*)value count:(int)n{
    if(value == nil || n < 0){
        return;
    }
    if(n == 0){
        [self wrapTagAndLength:index length:n];
        return;
    }
    
    int sum = 0;
    for(int i = 0; i < n;i++){
        sum |= value[i];
    }
    int bitShift;
    if ((sum >> 1) == 0) {
        bitShift = 0;
    } else if ((sum >> 2) == 0) {
        bitShift = 1;
    } else if ((sum >> 4) == 0) {
        bitShift = 2;
    } else if ((sum >> 8) == 0) {
        bitShift = 3;
    } else {
        [self throwException:@"only accept values less than 255"];
        return;
    }
    // the first byte: 000- 2 bits 'bitShift' - 3 bits remain
    int byteCount;
    if (bitShift == 3) {
        byteCount = n + 1;
        [self wrapTagAndLength:index length:byteCount];
        int pos = buffer.position;
        buffer.hb[pos++] = (char) (bitShift << 3);
        for (int i = 0; i < n; i++) {
            buffer.hb[pos + i] = (char) value[i];
        }
    } else {
        int totalBits = n << bitShift;
        int remain = totalBits & 0x7;
        byteCount = (totalBits >> 3) + (remain == 0 ? 1 : 2);
        [self wrapTagAndLength:index length:byteCount];
        int pos = buffer.position;
        buffer.hb[pos++] = (char) ((bitShift << 3) | remain);
        
        // bitShift=0, indexShift=3, indexMask=0x7
        // bitShift=1, indexShift=2, indexMask=0x3
        // bitShift=2, indexShift=1, indexMask=0x1
        int indexShift = 3 - bitShift;
        int indexMask = ~(0xffffffff << indexShift);
        int step = 1 << indexShift;
        int i = 0;
        while (i < n) {
            int end = MIN(i + step, n);
            char b = 0;
            for (int j = i; j < end; j++) {
                b |= value[j] << ((j & indexMask) << bitShift);
            }
            buffer.hb[pos + (i >> indexShift)] = b;
            i = end;
        }
    }
    buffer.position += byteCount;
}

-(void)wrapArray:(int)n flagPosition:(int)pFlag andWrapper:(union ArrayWrapper*)wrapper andType:(int)type{
    int i = 0;
    while (i < n) {
        int end = MIN(i + 4, n);
        int flags = 0;
        if (type == DOUBLE) {
            for (int j = i; j < end; j++) {
                double d = wrapper->doubleArray[j];
                if (d == 0) {
                    continue;
                }
                int shift = ((j & 0x3) << 1);
                int64_t e = *(int64_t *) (&d);
                if ((e << 16) == 0L) {
                    [buffer writeShort:(short) (e >> 48)];
                    flags |= 1 << shift;
                } else if ((e << 32) == 0L) {
                    [buffer writeInt:(int) (e >> 32)];
                    flags |= 2 << shift;
                } else {
                    [buffer writeInt64:e];
                    flags |= 3 << shift;
                }
            }
        } else {
            for (int j = i; j < end; j++) {
                int64_t e = type == INT ? wrapper->intArray[j] : wrapper->int64Array[j];
                if (e == 0L) {
                    continue;
                }
                int shift = ((j & 0x3) << 1);
                if ((e >> 8) == 0L) {
                    [buffer writeByte:(char) e];
                    flags |= 1 << shift;
                } else if ((e >> 16) == 0L) {
                    [buffer writeShort: (short) e];
                    flags |= 2 << shift;
                } else {
                    if (type == INT) {
                        [buffer writeInt: (int) e];
                    } else {
                        [buffer writeInt64: e];
                    }
                    flags |= 3 << shift;
                }
            }
        }
        buffer.hb[pFlag + (i >> 2)] = (char) flags;
        i = end;
    }
}


-(void)putNumberArray:(uint8)index andWrapper:(union ArrayWrapper*)wrapper andType:(int)type count:(int)n{
    if (n < 0) {
        return;
    }
    if (n == 0) {
        [self wrapTagAndLength:index length:0];
        return;
    }
    
    // calculate spaces
    int sizeOfN = [PackBuffer getVarint32Size:n];
    int totalBits = n << 1;
    int byteCount = (totalBits >> 3) + ((totalBits & 0x7) != 0 ? 1 : 0);
    
    // wrap tag and reserve space for len
    int shift = type == INT ? 2 : 3;
    int maxSize = sizeOfN + byteCount + (n << shift);
    [self checkCapacity:(6 + maxSize)];
    
    int pTag = buffer.position;
    [self putIndex:(index)];
    int pLen = buffer.position;
    int sizeOfLen;
    if (maxSize <= 0xff) {
        buffer.hb[pTag] |= TYPE_VAR_8;
        sizeOfLen = 1;
    } else if (maxSize <= 0xffff) {
        buffer.hb[pTag] |= TYPE_VAR_16;
        sizeOfLen = 2;
    } else {
        buffer.hb[pTag] |= TYPE_VAR_32;
        sizeOfLen = 4;
    }
    buffer.position += sizeOfLen;
    
    [buffer writeVarint32:n];
    int pFlag = buffer.position;
    // move position to values
    buffer.position += byteCount;
    
    [self wrapArray:n flagPosition:pFlag andWrapper:wrapper andType:type];
    
    // wrap len
    // maxSize must be large than len, so it's safe to put len in position pLen
    int len = buffer.position - (pLen + sizeOfLen);
    if (sizeOfLen == 1) {
        buffer.hb[pLen] = (char) len;
    } else if (sizeOfLen == 2) {
        [buffer writeShort:pLen withValue:len];
    } else {
        [buffer writeInt:pLen withValue:len];
    }
}

-(void)putCompactIntArray:(uint8)index andValue:(int*)value count:(int)n {
    if (value != nil && n >= 0) {
        union ArrayWrapper wrapper;
        wrapper.intArray = value;
        [self putNumberArray:index  andWrapper:&wrapper andType:(int)INT count: n];
    }
}

-(void)putCompactInt64Array:(uint8)index andValue:(int64_t*)value count:(int)n {
    if (value != nil && n >= 0) {
        union ArrayWrapper wrapper;
        wrapper.int64Array = value;
        [self putNumberArray:index  andWrapper:&wrapper andType:(int)INT64 count: n];
    }
}

-(void)putCompactDoubleArray:(uint8)index andValue:(double*)value count:(int)n {
    if (value != nil && n >= 0) {
        union ArrayWrapper wrapper;
        wrapper.doubleArray = value;
        [self putNumberArray:index  andWrapper:&wrapper andType:(int)DOUBLE count: n];
    }
}

-(void)putStr2Str:(uint8)index withValue:(NSMutableDictionary<NSString*,NSString*>*)value{
    if(value == nil){
        return;
    }
    int n = (int)[value count];
    long tagValue = [self wrapObjectArrayHeader:index andSize:n];
    if(tagValue < 0){
        return;
    }
    for(id key in value) {
        [self wrapString:key];
        [self wrapString:[value objectForKey:key]];
    }
    [self putLen:(tagValue >> 32) andPValue:(int)tagValue];
}

-(void)putStr2Pack:(uint8)index withValue:(NSMutableDictionary<NSString*, id<Packable>>*)value{
    if(value == nil){
        return;
    }
    int n = (int)[value count];
    long tagValue = [self wrapObjectArrayHeader:index andSize:n];
    if(tagValue < 0){
        return;
    }
    for(id key in value) {
        [self wrapString:key];
        [self wrapPackable:[value objectForKey:key]];
    }
    [self putLen:(tagValue >> 32) andPValue:(int)tagValue];
}

@end


