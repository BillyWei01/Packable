#import "Info.h"

@implementation Info

@synthesize infoId;
@synthesize name;
@synthesize rect;


-(void)encode:(PackEncoder*)encoder{
    [encoder putInt64:0 withValue:infoId];
    [encoder putString:1 withValue:name];
    PackBuffer* buf = [encoder putCustom:2 withLen:16];
    [buf writeInt:rect.x];
    [buf writeInt:rect.y];
    [buf writeInt:rect.width];
    [buf writeInt:rect.height];
    // NSLog(@"Printing encode Info");
}

-(bool)equals:(Info*)other{
    return infoId == other.infoId &&
    [name isEqualToString:other.name] &&
    rect.x == other.rect.x &&
    rect.y == other.rect.y &&
    rect.width == other.rect.width &&
    rect.height == other.rect.height;
}

//- (NSString *)description {
//    return [NSString stringWithFormat:@"Info: name=%s",name];
//}

@end

static InfoCreator *infoCreator = nil;

@implementation InfoCreator

+(id)getIntance{
    @synchronized(self){
        if(infoCreator  ==  nil){
            infoCreator = [[self alloc] init];
        }
    }
    return infoCreator;
}

-(id<Packable>)decode:(PackDecoder *)decoder{
    Info*  t = [[Info alloc]init];
    t.infoId = [decoder getInt64:0];
    t.name = [decoder getString:1];
    PackBuffer* buf = [decoder getCustom:2];
    if(buf != nil){
        struct Rectangle rt;
        rt.x = [buf readInt];
        rt.y = [buf readInt];
        rt.width = [buf readInt];
        rt.height = [buf readInt];
        t.rect = rt;
    }
    return t;
}


@end
