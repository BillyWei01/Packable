

#import <Foundation/Foundation.h>
#import <os/lock.h>
#import "LongArrayPool.h"


static const int DEFAULT_CAPACITY = 16;
static const int SECOND_SIZE = 64;
static const int SECOND_CAPACITY = 4;

static int defaultCount = 0;
static uint64_t* defaultArrays[DEFAULT_CAPACITY];

static int secondCount = 0;
static uint64_t* secondArrays[SECOND_CAPACITY];

static os_unfair_lock lock1;
static os_unfair_lock lock2;

@implementation LongArrayPool

+(uint64_t*)getLongArray:(int)size{
    if(size <= LONG_ARRAY_DEFAULT_SIZE){
        uint64_t* a;
        os_unfair_lock_lock(&lock1);
        if(defaultCount > 0){
            defaultCount--;
            a = defaultArrays[defaultCount];
        }else{
            a = (uint64_t*)malloc(32<<3);
        }
        os_unfair_lock_unlock(&lock1);
        return a;
    }
    if(size <= SECOND_SIZE){
        uint64_t* a;
        os_unfair_lock_lock(&lock2);
        if(secondCount > 0){
            secondCount--;
            a = secondArrays[secondCount];
        }else{
            a = (uint64_t*)malloc(64<<3);
        }
        os_unfair_lock_unlock(&lock2);
        return a;
    }
    if(size <= 128){
        return (uint64_t*)malloc(128<<3);
    }
    return (uint64_t*)malloc(256<<3);
}

+(void)recycleLongArray:(uint64_t*)a withLen:(int)len{
    if(a == NULL){
        return;
    }
    if(len == LONG_ARRAY_DEFAULT_SIZE){
        os_unfair_lock_lock(&lock1);
        if(defaultCount < DEFAULT_CAPACITY){
            defaultArrays[defaultCount] =a;
            defaultCount++;
        }else{
            free(a);
        }
        os_unfair_lock_unlock(&lock1);
    }else if(len == SECOND_SIZE){
        os_unfair_lock_lock(&lock2);
        if(secondCount < SECOND_CAPACITY){
            secondArrays[secondCount] =a;
            secondCount++;
        }else{
            free(a);
        }
        os_unfair_lock_unlock(&lock2);
    }else{
        free(a);
    }
}

@end
