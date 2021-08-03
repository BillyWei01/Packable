
#import "SampleVo.h"

@implementation SampleVo

@synthesize x;

-(void)encode:(PackEncoder*)encoder{
    [encoder putInt:0 withValue:x];
}

- (NSString *)description {
   return [NSString stringWithFormat:@"SampleVo: x=%d",x];
}

@end

static SampleVoCreator *sampleVoCreator = nil;

@implementation SampleVoCreator

+(id)getIntance{
    @synchronized(self){
        if(sampleVoCreator  ==  nil){
            sampleVoCreator = [[self alloc] init];
        }
    }
    return sampleVoCreator;
}

-(id<Packable>)decode:(PackDecoder *)decoder{
        SampleVo*  t = [[SampleVo alloc]init];
        t.x = [decoder getInt:0];
        return t;
}

@end
