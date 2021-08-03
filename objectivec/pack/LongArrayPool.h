
#import <Foundation/Foundation.h>

#ifndef LongArrayPool_h
#define LongArrayPool_h

static const int LONG_ARRAY_DEFAULT_SIZE = 32;

@interface LongArrayPool : NSObject{
}

+(uint64_t*)getLongArray:(int)size;

+(void)recycleLongArray:(uint64_t*)a withLen:(int)len;

@end

#endif /* LongArrayPool_h */
