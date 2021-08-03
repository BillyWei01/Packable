#import <Foundation/Foundation.h>
#import "PackEncoder.h"
#import "PackDecoder.h"

NS_ASSUME_NONNULL_BEGIN

struct Rectangle{
    int x;
    int y;
    int width;
    int height;
};

@interface Info : NSObject<Packable>{
    long infoId;
    NSString* name;
    struct Rectangle rect;
}
@property(nonatomic, readwrite) long infoId;
@property(nonatomic, readwrite) NSString*  name;
@property(nonatomic, readwrite) struct Rectangle rect;

-(bool)equals:(Info*)other;

@end



@interface InfoCreator : NSObject<PackCreator>{
}
+(id)getIntance;
@end


NS_ASSUME_NONNULL_END
