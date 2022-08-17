#import <Foundation/Foundation.h>
#import "PackBuffer.h"
#import "Packable.h"

#ifndef PackEncoder_h
#define PackEncoder_h

@interface PackEncoder : NSObject{
    PackBuffer* buffer;
}

+(uint8*)marshal:(id<Packable>)packable outCount:(int*)count;

/**
 * Get bytes  from buffer with new memory
 */
-(uint8*)getBytes:(int*)count;

-(PackBuffer*)putCustom:(uint8)index withLen:(uint8)len;

/**
 * Get PackBuffer of Encoder, you can read bytes directly with PackBuffer,
 * Be sure to read bytes before PackEncoder dealloc ( PackEncoder recycle or free bytes when dealloc)
 */
-(PackBuffer*)getBuffer;

-(void)putByte:(uint8)index withValue:(uint8)value;

-(void)putBool:(uint8)index withValue:(bool)value;

-(void)putShort:(uint8)index withValue:(short)value;

-(void)putInt:(uint8)index withValue:(int)value;

-(void)putInt64:(uint8)index withValue:(int64_t)value;

-(void)putFloat:(uint8)index withValue:(float)value;

-(void)putDouble:(uint8)index withValue:(double)value;

-(void)putString:(uint8)index withValue:(NSString*)value;

-(void)putStringNSArray:(uint8)index withValue:(NSArray<NSString*>*)value;

-(void)putStringMutArray:(uint8)index withValue:(NSMutableArray<NSString*>*)value;

-(void)putPackable:(uint8)index withValue:(id<Packable>)value;

-(void)putPackableNSArray:(uint8)index withValue:(NSArray*)value;

-(void)putPackableMutArray:(uint8)index withValue:(NSMutableArray*)value;

-(void)putByteArray:(uint8)index withValue:(uint8*)value count:(int)n;

-(void)putIntArray:(uint8)index withValue:(int*)value count:(int)n;

-(void)putInt64Array:(uint8)index withValue:(int64_t*)value count:(int)n;

-(void)putFloatArray:(uint8)index withValue:(float*)value count:(int)n;

-(void)putDoubleArray:(uint8)index withValue:(double*)value count:(int)n;

-(void)putBoolArray:(uint8)index withValue:(bool*)value count:(int)n;

-(void)putEnumArray:(uint8)index withValue:(int*)value count:(int)n;

-(void)putCompactIntArray:(uint8)index andValue:(int*)value count:(int)n;

-(void)putCompactInt64Array:(uint8)index andValue:(int64_t*)value count:(int)n;

-(void)putCompactDoubleArray:(uint8)index andValue:(double*)value count:(int)n;

-(void)putStr2Str:(uint8)index withValue:(NSMutableDictionary<NSString*,NSString*>*)value;

-(void)putStr2Pack:(uint8)index withValue:(NSMutableDictionary<NSString*, id<Packable>>*)value;

@end

#endif /* PackEncoder_h */
