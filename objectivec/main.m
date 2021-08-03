
#import <Foundation/Foundation.h>

#import "PackEncoder.h"
#import "PackDecoder.h"
#import "SampleVo.h"
#import "PackVo.h"
#include "Info.h"


void testPackVo(){
    // TODO replace with you path
    NSString* path = @"/Users/john/github/Packable/test_data/packable_2000.data";
    NSData* fileData =[NSData dataWithContentsOfFile:path];
    if([fileData length] <= 0){
        NSLog(@"test data file no exists");
        return;
    }
    int count = (int)fileData.length;
    uint8_t* bytes = (uint8_t *)[fileData bytes];
    
    for(int i = 0; i < 3;i++){
        NSDate* t1 = [NSDate date];
        Response* response = [PackDecoder unmarshal:bytes length:count withCreator:[ResponseCreator getIntance]];
        NSLog(@"decode = %f", [[NSDate date] timeIntervalSinceDate:t1] * 1000);
        
        NSDate* t2 = [NSDate date];
        int count2;
        uint8* buffer2 = [PackEncoder marshal:response outCount:&count2];
        NSLog(@"encode = %f", [[NSDate date] timeIntervalSinceDate:t2] * 1000);
        
        int equal = count == count2 && memcmp(bytes, buffer2, count) == 0;
        if(!equal){
            NSLog(@"not equal, count:%d count2:%d\n", count, count2);
        }else{
            NSLog(@"data equal, count:%d\n", count);
        }
        free(buffer2);
    }
}

void testCustomEncode(){
    Info* info = [[Info alloc] init];
    info.infoId = 1234;
    info.name = @"rect_1";
    struct Rectangle rt;
    rt.x = 100;
    rt.y = 200;
    rt.width = 300;
    rt.height = 400;
    info.rect = rt;

    int count;
    uint8* bytes = [PackEncoder marshal:info outCount:&count];
    Info* dInfo = [PackDecoder unmarshal:bytes length:count withCreator:[InfoCreator getIntance]];
    
    bool equal = [info equals:dInfo];
    NSLog(@"testCustomEncode:%@\n", equal ? @"YES" : @"NO");
    
}

int main(int argc, const char * argv[]) {
    @autoreleasepool {
        @try {
            SampleVo*  s = [[SampleVo alloc]init];
            s.x = 12345;
            
            NSMutableArray<SampleVo*> *arr = [NSMutableArray<SampleVo*> arrayWithCapacity:1];
            [arr addObject:s];
            [arr addObject:[NSNull null]];
            
            NSArray *strArray = @[@"jack",@"rose",[NSNull null],@"lili"];
            
            float floatArray[] = {-1.0, 0, 12345.6};
            
            bool ba1[] = {true, false};
            bool ba2[] = {true, false, true, false,true, false,true, false,true, false};
            int en1[] = {0, 1, 0, 1, 1};
            int en2[] = {0, 1, 2, 3, 0};
            int en3[] = {0, 1, 2, 3, 4, 5, 7, 7, 6};
            
            int ci32[] = {0, 0xff, 0xffff, 0xffffff};
            int64_t ci64[] =  {0, 0xff, 0xffff, 0xffffff, 0xffffffffffL};
            double cdouble[] = {0, 1, 1.5 , 2, 2.2};
            
            
            NSMutableDictionary *dic = [NSMutableDictionary dictionaryWithObjectsAndKeys:
                                        @"value1", @"key1",
                                        @"value2", @"key2", nil];
            
            NSMutableDictionary *dic2 = [NSMutableDictionary dictionaryWithObjectsAndKeys:
                                         s, @"simple_vo", nil];
            
            
            PackEncoder *en = [[PackEncoder alloc]init];
            [en putPackableMutArray:0 withValue:arr];
            [en putSInt:1 withValue:1234];
            [en putSInt64:2 withValue:987654321];
            [en putFloat:3 withValue:3.14];
            double d =1.2345678901F;
            [en putCDouble:4 withValue:d];
            [en putString:5 withValue:@"你好 haha!"];
            [en putStringNSArray:6 withValue:strArray];
            [en putFloatArray:7 withValue:floatArray count:3];
            [en putBoolArray:8 withValue:ba1 count:2];
            [en putBoolArray:9 withValue:ba2 count:10];
            [en putEnumArray:10 withValue:en1 count:5];
            [en putEnumArray:11 withValue:en2 count:5];
            [en putEnumArray:12 withValue:en3 count:10];
            [en putCompactIntArray:13 andValue:ci32 count:4];
            [en putCompactInt64Array:14 andValue:ci64 count:5];
            [en putCompactDoubleArray:15 andValue:cdouble count:5];
            [en putStr2Str:16 withValue:dic];
            [en putStr2Pack:17 withValue:dic2];
            
            int c;
            uint8* bs = [en getBytes:&c];
            
            
            PackDecoder *de = [[PackDecoder alloc]initWithBytes:bs andOffset:0 andLen:c];
            //SampleVo* s1 = (SampleVo*)[de getPackable:0 withCreator:[SampleVoCreator getIntance]];
            NSMutableArray<SampleVo*>* a1 = [de getPackableArray:0 withCreator:[SampleVoCreator getIntance]];
            int i1 = [de getSInt:1];
            int64_t l1 = [de getSInt64:2];
            float f1 = [de getFloat:3];
            double d1 = [de getCDouble:4];
            NSString* str = [de getString:5];
            NSArray* strArr = [de getStringArray:6];
            int faCount;
            float* fa = [de getFloatArray:7 outCount:&faCount];
            
            int ba1Count, ba2Count;
            bool* dba1 = [de getBoolArray:8 outCount:&ba1Count];
            bool* dba2 = [de getBoolArray:9 outCount:&ba2Count];
            
            int en1Count, en2Count, en3Count;
            int* den1 = [de getEnumArray:10 outCount:&en1Count];
            int* den2 = [de getEnumArray:11 outCount:&en2Count];
            int* den3 = [de getEnumArray:12 outCount:&en3Count];
            
            int ci32Count, ci64Count, cDoubleCount;
            int* dci32 = [de getCompactIntArray:13 outCount:&ci32Count];
            int64_t* dci64 = [de getCompactInt64Array:14 outCount:&ci64Count];
            double* dcDouble = [de getCompactDoubleArray:15 outCount:&cDoubleCount];
            
            NSMutableDictionary *ddic = [de getStr2Str:16];
            NSMutableDictionary *ddic2 = [de getStr2Pack:17 withCreator:[SampleVoCreator getIntance]];
            
            
            NSLog(@"a1: %@", a1);
            NSLog(@"i1: %d l1:%lld \n", i1, l1);
            NSLog(@"f1: %f d1:%f %d\n", f1, d1, d == d1);
            NSLog(@"str: %@", str);
            NSLog(@"strArray: %@", strArr);
            
            bool e1 = faCount == 3;
            for (int j = 0; j < faCount; j++) {
                e1 &= floatArray[j] == fa[j];
            }
            NSLog(@"e1:%@\n", e1 ? @"YES" : @"NO");
            
            e1 = ba1Count == 2;
            for (int j = 0; j < ba1Count; j++) {
                e1 &= ba1[j] == dba1[j];
            }
            
            bool e2 = ba2Count == 10;
            for (int j = 0; j < ba2Count; j++) {
                e2 &= ba2[j] == dba2[j];
            }
            
            NSLog(@"e1:%@ e2:%@\n", e1 ? @"YES" : @"NO", e2 ? @"YES" : @"NO");
            
            e1 = en1Count == 5;
            for(int j = 0; j < en1Count;j++){
                e1 &= en1[j] == den1[j];
            }
            e2 = en1Count == 5;
            for(int j = 0; j < en1Count;j++){
                e2 &= en2[j] == den2[j];
            }
            bool e3 = en1Count == 5;
            for(int j = 0; j < en1Count;j++){
                e3 &= en3[j] == den3[j];
            }
            NSLog(@"e1:%@ e2:%@ e3:%@\n", e1 ? @"YES" : @"NO", e2 ? @"YES" : @"NO", e3 ? @"YES" : @"NO");
            
            e1 = ci32Count == 4;
            for(int j = 0;j < ci32Count;j++){
                e1 &= ci32[j] == dci32[j];
            }
            e2 = ci64Count == 5;
            for(int j = 0;j < ci64Count;j++){
                e2 &= ci64[j] == dci64[j];
            }
            e3 = cDoubleCount == 5;
            for(int j = 0;j < cDoubleCount;j++){
                e3 &= cdouble[j] == dcDouble[j];
            }
            NSLog(@"e1:%@ e2:%@ e3:%@\n", e1 ? @"YES" : @"NO", e2 ? @"YES" : @"NO", e3 ? @"YES" : @"NO");
            
            
            NSLog(@"ddic: %@\nddic2: %@\n", ddic, ddic2);
            
            testCustomEncode();
            
            free(fa);
            free(dba1);
            free(dba2);
            free(den1);
            free(den2);
            free(den3);
            free(dci32);
            free(dci64);
            free(dcDouble);
            free(bs);
            
            NSLog(@"testPackVo:\n");
            testPackVo();
            
        } @catch (NSException *e) {
            NSLog(@"Exception: %@", e);
        }
    }
    
    NSLog(@"end");
    return 0;
}
