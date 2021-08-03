
#ifndef PACK_TAGFORMAT_H
#define PACK_TAGFORMAT_H

/*
 * Tag takes one byte or two bytes.
 *
 * The first byte:
 * from low to high (bit):
 * [1-4] : index (when index is less than 16)
 * [5-7] : type define
 * [8] : index compress flag
 *
 * If the index bigger than 16, use the second byte to place the index,
 * and the compress flag will be 1, the [1-4] bit of the first index will be 0.
 * Now the tag support index range in [0,255].
 */
namespace TagFormat {
    const char TYPE_SHIFT = 4;
    const char BIG_INDEX_MASK = (char) (1 << 7);
    const char TYPE_MASK = 7 << TYPE_SHIFT;
    const char INDEX_MASK = 0xF;
    const uint8_t LITTLE_INDEX_BOUND = 1 << TYPE_SHIFT;

    const char TYPE_0 = 0;
    const char TYPE_NUM_8 = 1 << TYPE_SHIFT;
    const char TYPE_NUM_16 = 2 << TYPE_SHIFT;
    const char TYPE_NUM_32 = 3 << TYPE_SHIFT;
    const char TYPE_NUM_64 = 4 << TYPE_SHIFT;
    const char TYPE_VAR_8 = 5 << TYPE_SHIFT;
    const char TYPE_VAR_16 = 6 << TYPE_SHIFT;
    const char TYPE_VAR_32 = 7 << TYPE_SHIFT;
}

#endif
