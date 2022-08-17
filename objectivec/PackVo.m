#import "PackVo.h"

@implementation Category

- (void)encode:(PackEncoder * _Nonnull)encoder {
    [encoder putString:0 withValue:name];
    [encoder putInt:1 withValue:level];
    [encoder putInt64:2 withValue:i_column];
    [encoder putDouble:3 withValue:d_column];
    [encoder putString:4 withValue:des];
    [encoder putPackableMutArray:5 withValue:sub_category];
}
@end

static CategoryCreator *categoryCreator = nil;

@implementation CategoryCreator

+(id)getIntance{
    @synchronized(self){
        if(categoryCreator  ==  nil){
            categoryCreator = [[self alloc] init];
        }
    }
    return categoryCreator;
}

-(id<Packable>)decode:(PackDecoder *)decoder{
    Category*  t = [[Category alloc]init];
    t->name = [decoder getString:0];
    t->level = [decoder getInt:1];
    t->i_column = [decoder getInt64:2];
    t->d_column = [decoder getDouble:3];
    t->des = [decoder getString:4];
    t->sub_category = [decoder getPackableArray:5 withCreator:[CategoryCreator getIntance]];
    return t;
}

@end


@implementation Data

- (void)encode:(PackEncoder * _Nonnull)encoder {
    [encoder putBool:0 withValue:d_bool];
    [encoder putFloat:1 withValue:d_float];
    [encoder putDouble:2 withValue:d_double];
    [encoder putString:3 withValue:string_1];
    [encoder putInt:4 withValue:int_1];
    [encoder putInt:5 withValue:int_2];
    [encoder putInt:6 withValue:int_3];
    [encoder putInt:7 withValue:int_4];
    [encoder putInt:8 withValue:int_5];
    [encoder putInt64:9 withValue:long_1];
    [encoder putInt64:10 withValue:long_2];
    [encoder putInt64:11 withValue:long_3];
    [encoder putInt64:12 withValue:long_4];
    [encoder putInt64:13 withValue:long_5];
    [encoder putPackable:14 withValue:d_category];
    [encoder putBoolArray:15 withValue:bool_array count:boolCount];
    [encoder putIntArray:16 withValue:int_array count:intCount];
    [encoder putInt64Array:17 withValue:long_array count:longCount];
    [encoder putFloatArray:18 withValue:float_array count:floatCount];
    [encoder putDoubleArray:19 withValue:double_array count:doubleCount];
    [encoder putStringMutArray:20 withValue:string_array];
}

@end

static DataCreator* dataCreator = nil;

@implementation DataCreator

+(id)getIntance{
    @synchronized(self){
        if(dataCreator  ==  nil){
            dataCreator = [[self alloc] init];
        }
    }
    return dataCreator;
}

- (nonnull id<Packable>)decode:(nonnull PackDecoder *)decoder {
    Data* t = [[Data alloc] init];
    t->d_bool = [decoder getBool:0];
    t->d_float = [decoder getFloat:1];
    t->d_double = [decoder getDouble:2];
    t->string_1 = [decoder getString:3];
    t->int_1 = [decoder getInt:4];
    t->int_2 = [decoder getInt:5];
    t->int_3 = [decoder getInt:6];
    t->int_4 = [decoder getInt:7];
    t->int_5 = [decoder getInt:8];
    t->long_1 = [decoder getInt64:9];
    t->long_2 = [decoder getInt64:10];
    t->long_3 = [decoder getInt64:11];
    t->long_4 = [decoder getInt64:12];
    t->long_5 = [decoder getInt64:13];
    t->d_category = [decoder getPackable:14 withCreator:[CategoryCreator getIntance]];
    t->bool_array = [decoder getBoolArray:15 outCount:&(t->boolCount)];
    t->int_array = [decoder getIntArray:16 outCount:&(t->intCount)];
    t->long_array = [decoder getInt64Array:17 outCount:&(t->longCount)];
    t->float_array = [decoder getFloatArray:18 outCount:&(t->floatCount)];
    t->double_array = [decoder getDoubleArray:19 outCount:&(t->doubleCount)];
    t->string_array = [decoder getStringArray:20];
    return t;
}

@end

@implementation Response

- (void)encode:(PackEncoder * _Nonnull)encoder {
    [encoder putInt:0 withValue:code];
    [encoder putString:1 withValue:detail];
    [encoder putPackableMutArray:2 withValue:data];
}

@end

static ResponseCreator* responseCreator = nil;

@implementation ResponseCreator

+(id)getIntance{
    @synchronized(self){
        if(responseCreator  ==  nil){
            responseCreator = [[self alloc] init];
        }
    }
    return responseCreator;
}

- (nonnull id<Packable>)decode:(nonnull PackDecoder *)decoder {
    Response* t = [[Response alloc]init];
    t->code = [decoder getInt:0];
    t->detail = [decoder getString:1];
    t->data = [decoder getPackableArray:2 withCreator:[DataCreator getIntance]];
    return t;
}

@end
