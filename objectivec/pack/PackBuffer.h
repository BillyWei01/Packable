

#ifndef PackBuffer_h
#define PackBuffer_h

@interface PackBuffer : NSObject{
    uint8 *hb;
    int position;
    int limit;
}

@property(nonatomic, readwrite) uint8 *hb;
@property(nonatomic, readwrite) int position;
@property(nonatomic, readwrite) int limit;

-(id)initWithBytes:(uint8*)array andOffset:(int)offset andLimit:(int)length;
-(bool)hasRemaining;
-(void)checkBould:(int)offset andLen:(int)len;

-(void)writeByte:(uint8)x;
-(uint8)readByte:(int) i;
-(uint8)readByte;

-(void)writeShort:(int)i withValue:(short)x;
-(void)writeShort:(int)x;
-(short)readShort;

-(void)writeInt:(int)x;
-(void)writeInt:(int)i withValue:(int)x;
-(int)readInt;

+(int)getVarint32Size:(int)x;
-(void)writeVarintNegatie1;
-(void)writeVarint32:(int)x;
-(int)readVarint32;

-(void)writeInt64:(int64_t)x;
-(int64_t)readInt64:(int)i;
-(int64_t)readInt64;

-(void)writeFloat:(float)x;
-(float)readFloat;

-(void)writeDouble:(double)x;
-(double)readDouble;

-(void)writeBytes:(uint8*)src withSize:(int)n;
-(void)readBytes:(uint8*) bytes withSize:(int)n;



@end

#endif /* PackBuffer_h */
