
#import <Foundation/Foundation.h>
#import "PackBuffer.h"


@implementation PackBuffer

@synthesize hb;
@synthesize position;
@synthesize limit;

-(id)initWithBytes:(uint8_t*)array andOffset:(int)offset andLimit:(int)length {
    self = [super init];
    if(self != nil){
        hb = array;
        position = offset;
        limit = offset +length;
    }
    return self;
}

-(bool)hasRemaining{
    return position < limit;
}

-(void)checkBould:(int)offset andLen:(int)len{
    if(offset + len > limit){
        @throw([NSException exceptionWithName:@"CheckBouldFailed" reason:@"buffer out of bound" userInfo:nil]);
    }
}

-(void)throwOutOfBound:(NSString*)info atPos:(int)pos {
    NSString* msg = [[NSString alloc] initWithFormat:@"Reading buffer out of bound %@ pos: %d, limit:%d", info, pos, limit];
    @throw([NSException exceptionWithName:@"OutOfBound" reason:msg userInfo:nil]);
}

-(void)writeByte:(uint8) x {
    hb[position++] = x;
}

-(uint8)readByte:(int) i{
    if(i >= limit){
        [self throwOutOfBound:@"readByte at " atPos:i];
    }
    return hb[i];
}

-(uint8)readByte{
    if(position >= limit){
        [self throwOutOfBound:@"readByte" atPos:position];
    }
    return hb[position++];
}

-(void)writeShort:(int)i withValue:(short)x{
    hb[i] = x;
    hb[i+1] = x >> 8;
}

-(void)writeShort:(int)x{
    hb[position++] = x;
    hb[position++] = x >> 8;
}

-(short)readShort{
    if (position + 2 > limit) {
        [self throwOutOfBound:@"readShort" atPos:position];
    }
    short value = *((short *) (hb + position));
    position += 2;
    return value;
}

-(void)writeInt:(int)x{
    *((int *) (hb + position)) = x;
    position += 4;
}

-(void)writeInt:(int)i withValue:(int)x{
    *((int *) (hb + i)) = x;
}

-(int)readInt{
    if(position + 4 > limit){
        [self throwOutOfBound:@"readInt" atPos:position];
    }
    int t =  *((int *) (hb + position));
    position += 4;
    return t;
}


+(int)getVarint32Size:(int)x{
    uint32 t = x;
    if(t <= 0x7f){
        return 1;
    }else if(x <= 0x3fff){
        return 2;
    }else if(x <= 0x1fffff){
        return 3;
    }else if (x <= 0xfffffff) {
        return 4;
    }
    return 5;
}

-(void)writeVarintNegatie1{
    *((int *) (hb + position)) = 0xffffffff;
    hb[position+4] = 0xf;
    position += 5;
}

-(void)writeVarint32:(int)x{
    uint32 t = x;
    while (t > 0x7f) {
        hb[position++] = (uint8) ((t & 0x7f) | 0x80);
        t >>= 7;
    }
    hb[position++] = (uint8) t;
}

-(int)readVarint32{
    uint32 x = hb[position++];
    if (x <= 0x7f) goto end;
    x = (x & 0x7f) | (hb[position++] << 7);
    if (x <= 0x3fff) goto end;
    x = (x & 0x3fff) | (hb[position++] << 14);
    if (x <= 0x1fffff) goto end;
    x = (x & 0x1fffff) | (hb[position++] << 21);
    if (x <= 0xfffffff) goto end;
    x = (x & 0xfffffff) | (hb[position++] << 28);
end:
    if (position > limit){
        [self throwOutOfBound:@"readVarint32" atPos:position];
    }
    return x;
}

-(void)writeInt64:(int64_t)x{
    *((int64_t *) (hb + position)) = x;
    position += 8;
}

-(int64_t)readInt64:(int)i{
    if(i + 8 > limit){
        [self throwOutOfBound:@"readInt64 at " atPos:i];
    }
    return *((int64_t *) (hb + i));
}

-(int64_t)readInt64{
    if(position + 8 > limit){
        [self throwOutOfBound:@"readInt64  " atPos:position];
    }
    int64_t t = *((int64_t *) (hb + position));
    position += 8;
    return t;
}

-(void)writeFloat:(float)x{
    *((float *) (hb + position)) = x;
    position += 4;
}

-(float)readFloat{
    int t = [self readInt];
    return *((float *) (&t));
}

-(void)writeDouble:(double)x{
    *((double *) (hb + position)) = x;
     position += 8;
}

-(double)readDouble{
    int64_t t = [self readInt64];
    return *((double *) (&t));
}

-(void)writeBytes:(uint8*)src withSize:(int)n{
    if (n > 0) {
        memcpy(hb + position, src, n);
        position += n;
    }
}
-(void)readBytes:(uint8*) bytes withSize:(int)n{
    memcpy(bytes, hb + position, n);
    position += n;
}

@end
