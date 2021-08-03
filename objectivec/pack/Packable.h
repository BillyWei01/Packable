
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@class PackEncoder;

@protocol Packable <NSObject>
@required
-(void)encode:(PackEncoder *_Nonnull)encoder;
@end

NS_ASSUME_NONNULL_END
