//
//  NumberType.h
//  pack
//
//  Created by John Smith on 2021/4/9.
//

#ifndef NumberType_h
#define NumberType_h

enum NumberType {
    INT,
    INT64,
    DOUBLE
};

union ArrayWrapper {
    int *intArray;
    int64_t *int64Array;
    double *doubleArray;
};


#endif /* NumberType_h */
