

#import <Foundation/Foundation.h>
#import "Packable.h"

NS_ASSUME_NONNULL_BEGIN

@class PackDecoder;

@protocol PackCreator <NSObject>
@required
-(id<Packable>)decode:(PackDecoder *)decoder;
@end

NS_ASSUME_NONNULL_END
