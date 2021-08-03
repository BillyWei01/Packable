#import <Foundation/Foundation.h>
#import "PackEncoder.h"
#import "PackDecoder.h"

NS_ASSUME_NONNULL_BEGIN

enum Result {
    SUCCESS,
    FAILED_1,
    FAILED_2,
    FAILED_3
};

@interface Category : NSObject<Packable>{
@public
    NSString* name;
    int level;
    long i_column;
    double d_column;
    NSString *des;
    NSMutableArray<Category*>* sub_category;
}
@end


@interface CategoryCreator : NSObject<PackCreator>{
}
+(id)getIntance;
@end


@interface Data :NSObject<Packable>{
@public
    bool d_bool;
    float d_float;
    double d_double;
    
    NSString* string_1;
    
    int int_1;
    int int_2;
    int int_3;
    int int_4;
    int int_5;
    
    int64_t long_1;
    int64_t long_2;
    int64_t long_3;
    int64_t long_4;
    int64_t long_5;
    
    Category* d_category;
    
    int boolCount;
    bool *bool_array;
    
    int intCount;
    int *int_array;
    
    int longCount;
    int64_t *long_array;
    
    int floatCount;
    float *float_array;
    
    int doubleCount;
    double *double_array;
    
    NSMutableArray<NSString*>* string_array;
}
@end

@interface DataCreator : NSObject<PackCreator>{
}
+(id)getIntance;
@end


@interface Response :NSObject<Packable>{
@public
    enum Result code;
    NSString* detail;
    NSMutableArray<Data*>* data;
}
@end

@interface ResponseCreator : NSObject<PackCreator>{
}
+(id)getIntance;
@end

NS_ASSUME_NONNULL_END
