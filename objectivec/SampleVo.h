

#import <Foundation/Foundation.h>
#import "PackEncoder.h"
#import "PackDecoder.h"

NS_ASSUME_NONNULL_BEGIN

@interface SampleVo : NSObject<Packable>{
    int x;
}
@property(nonatomic, readwrite) int x;

@end

@interface SampleVoCreator : NSObject<PackCreator>{
}
+(id)getIntance;
@end


NS_ASSUME_NONNULL_END
