package pack

import (
	"unsafe"
)

const (
	// It's safety to limit the capacity.
	// Besides, it's not effective if the buffer is too large.
	max_buffer_size int32 = 1 << 30

	// Object size limit, one million.
	// In case of error message to allocate too much memory.
	// You could adjust the size according to your situation.
	max_object_array_size int32 = 1 << 20

	// Limit of double memory (double again).
	// See Encoder.checkCapacity(int)
	double_buffer_limit int32 = 1 << 22

	// Before putting object and object array to buffer, we reserve 4 bytes to place the 'length',
	// When accomplish, we know the exactly 'length',
	// if the 'length' is less or equal than TRIM_SIZE_LIMIT,
	// we retrieved 3 bytes (by moving bytes forward).
	//
	// We could set TRIM_SIZE_LIMIT up to 255, but it's not effective to move too many bytes to save 3 bytes.
	// Besides, object recursion might make moving bytes grow up,
	// set a little limit could make the recursion moving stop soon.
	trim_size_limit int32 = 127

	// Use to mark null packable object
	null_packable int16 = -1
)

var checkEndNumber = 1
var isLittleEnd = *(*uint8)(unsafe.Pointer(&checkEndNumber)) == 1
