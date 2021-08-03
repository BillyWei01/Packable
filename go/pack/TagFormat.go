package pack

// Tag takes one byte or two bytes.
//
// The first byte:
// from low to high (bit):
//  -[1-4] : index (when index is less than 16)
//  -[5-7] : type define
//  -[8] :   index compress flag
//
// If the index bigger than 16, use the second byte to place the index,
// and the compress flag will be 1, the [1-4] bit of the first index will be 0.
// Now the tag support index range in [0,255].
const (
	type_shift         byte = 4
	big_index_mask     byte = 1 << 7
	type_mask          byte = 7 << type_shift
	index_mask         byte = 0xF
	little_index_bound byte = 1 << type_shift

	type_0      byte = 0
	type_num_8  byte = 1 << type_shift
	type_num_16 byte = 2 << type_shift
	type_num_32 byte = 3 << type_shift
	type_num_64 byte = 4 << type_shift
	type_var_8  byte = 5 << type_shift
	type_var_16 byte = 6 << type_shift
	type_var_32 byte = 7 << type_shift
)
