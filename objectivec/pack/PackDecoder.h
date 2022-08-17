
#import <Foundation/Foundation.h>
#import "PackBuffer.h"
#import "Packable.h"
#import "PackCreator.h"

#ifndef PackDecoder_h
#define PackDecoder_h

@interface PackDecoder : NSObject{
    PackBuffer* buffer;
    uint64_t* infoArray;
    int infoLength;
    int maxIndex;
}

+(id<Packable>)unmarshal:(uint8*)bytes length:(int)len withCreator:(id<PackCreator>)creator;

-(id)initWithBytes:(uint8*)bytes andOffset:(int)offset andLen:(int)len;

-(bool)contains:(int)index;

-(PackBuffer*) getCustom:(int)index;

-(bool)getBool:(int)index withDefault:(bool)defValue;
-(bool)getBool:(int)index;

-(uint8)getByte:(int)index withDefault:(uint8)defValue;
-(uint8)getByte:(int)index;

-(short)getShort:(int)index withDefault:(short)defValue;
-(short)getShort:(int)index;

-(int)getInt:(int)index withDefault:(int)defValue;
-(int)getInt:(int)index;

-(int64_t)getInt64:(int)index withDefault:(int64_t)defValue;
-(int64_t)getInt64:(int)index;

-(float)getFloat:(int)index withDefault:(float)defValue;
-(float)getFloat:(int)index;

-(double)getDouble:(int)index withDefault:(double)defValue;
-(double)getDouble:(int)index;


-(NSString*)getString:(int)index withDefault:(NSString*)defValue;
-(NSString*)getString:(int)index;

-(NSMutableArray<NSString*>*)getStringArray:(int)index;

-(id<Packable>)getPackable:(int)index withCreator:(id<PackCreator>)creator;

-(NSMutableArray<Packable>*)getPackableArray:(int)index withCreator:(id<PackCreator>)creator;

-(uint8*)getByteArray:(int)index outCount:(int*)count;

-(int*)getIntArray:(int)index outCount:(int*)count;

-(int64_t*)getInt64Array:(int)index outCount:(int*)count;

-(float*)getFloatArray:(int)index outCount:(int*)count;

-(double*)getDoubleArray:(int)index outCount:(int*)count;

-(bool*)getBoolArray:(int)index outCount:(int*)count;

-(int*)getEnumArray:(int)index outCount:(int*)count;

-(int*)getCompactIntArray:(int)index outCount:(int*)count;

-(int64_t *)getCompactInt64Array:(int)index outCount:(int*)count;

-(double*)getCompactDoubleArray:(int)index outCount:(int*)count;

-(NSMutableDictionary<NSString*,NSString*>*)getStr2Str:(int)index;

-(NSMutableDictionary<NSString*,id<Packable>>*)getStr2Pack:(int)index withCreator:(id<PackCreator>)creator;

@end


#endif /* PackDecoder_h */
