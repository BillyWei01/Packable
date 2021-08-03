#import <Foundation/Foundation.h>

#ifndef PACK_TAGFORMAT_H
#define PACK_TAGFORMAT_H

static const uint8 TYPE_SHIFT = 4;
static const uint8 BIG_INDEX_MASK = (uint8) (1 << 7);
static const uint8 TYPE_MASK = 7 << TYPE_SHIFT;
static const uint8 INDEX_MASK = 0xF;
static const uint8 LITTLE_INDEX_BOUND = 1 << TYPE_SHIFT;

static const uint8 TYPE_0 = 0;
static const uint8 TYPE_NUM_8 = 1 << TYPE_SHIFT;
static const uint8 TYPE_NUM_16 = 2 << TYPE_SHIFT;
static const uint8 TYPE_NUM_32 = 3 << TYPE_SHIFT;
static const uint8 TYPE_NUM_64 = 4 << TYPE_SHIFT;
static const uint8 TYPE_VAR_8 = 5 << TYPE_SHIFT;
static const uint8 TYPE_VAR_16 = 6 << TYPE_SHIFT;
static const uint8 TYPE_VAR_32 = 7 << TYPE_SHIFT;

#endif
