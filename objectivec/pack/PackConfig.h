

#ifndef PackConfig_h
#define PackConfig_h

/**
 * It's safety to limit the capacity.
 * Besides, it's not effective if the buffer is too large.
 */
static const int MAX_BUFFER_SIZE = 1 << 30;

/**
 * Object size limit, one million.
 * In case of error message to allocate too much memory.
 * You could adjust the size according to your situation.
 */
static const int MAX_OBJECT_ARRAY_SIZE = 1 << 20;

/**
 * Limit of double memory (double again).
 * See PackEncoder#checkCapacity(int)
 */
static const int DOUBLE_BUFFER_LIMIT = 1 << 22;

/**
 * Before putting object and object array to buffer, we reserve 4 bytes to place the 'length',
 * When accomplish, we know the exactly 'length',
 * if the 'length' is less or equal than TRIM_SIZE_LIMIT,
 * we retrieved 3 bytes (by moving bytes forward).
 *
 * We could set TRIM_SIZE_LIMIT up to 255, but it's not effective to move too many bytes to save 3 bytes.
 * Besides, object recursion might make moving bytes grow up,
 * set a little limit could make the recursion moving stop soon.
 */
static const int TRIM_SIZE_LIMIT = 127;

/**
 * use to mark null packable object
 */
static const short NULL_PACKABLE = (short) 0xffff;


#endif /* PackConfig_h */
