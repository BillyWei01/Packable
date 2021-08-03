package pack

import (
	"errors"
	"fmt"
	"reflect"
	"unsafe"
)

type Encoder struct {
	buffer *Buffer
}

type Packable interface {
	Encode(encoder *Encoder)
}

func NewEncoder() *Encoder {
	return &Encoder{
		buffer: newEncodeBuffer(getByteArray(minByteArraySize)),
	}
}

func Marshal(p Packable) ([]byte, error) {
	var err error = nil
	bytes := marshal(p, &err)
	return bytes, err
}

func marshal(p Packable, err *error) []byte {
	defer func() {
		if e := recover(); e != nil {
			*err = errors.New(fmt.Sprintf("encode error,%v", e))
		}
	}()
	encoder := NewEncoder()
	p.Encode(encoder)
	return encoder.GetBytes()
}

func (e *Encoder) GetBytes() []byte {
	e.checkBufferState()
	buffer := e.buffer
	bytes := make([]byte, buffer.position)
	copy(bytes, buffer.hb)
	e.Recycle()
	return bytes
}

func (e *Encoder) Recycle() {
	recycleByteArray(e.buffer.hb)
	e.buffer.hb = nil
}

func (e *Encoder) checkBufferState() {
	if e.buffer.hb == nil {
		panic("Encoder had been recycled")
	}
}

var maxAllocated int32 = 1 << 20

func (e *Encoder) checkCapacity(expandSize int32) {
	buffer := e.buffer
	capacity := buffer.limit
	desSize := buffer.position + expandSize

	if desSize <= 0 {
		panic("desire capacity overflow")
	}
	if desSize > capacity {
		if desSize > max_buffer_size {
			panic("desire capacity over limit")
		}
		newSize := capacity << 1
		for desSize > newSize {
			newSize = newSize << 1
		}
		doubleLimit := maxAllocated
		if doubleLimit > double_buffer_limit {
			doubleLimit = double_buffer_limit
		}
		if newSize < doubleLimit {
			newSize = newSize << 1
		}
		if newSize > maxAllocated {
			maxAllocated = newSize
		}
		oldArray := buffer.hb
		newArray := getByteArray(newSize)
		copy(newArray, oldArray)
		buffer.hb = newArray
		buffer.limit = newSize
		recycleByteArray(oldArray)
	}
}

func (e *Encoder) putIndex(index byte) {
	if index >= little_index_bound {
		e.buffer.WriteByte(big_index_mask)
	}
	e.buffer.WriteByte(index)
}

func (e *Encoder) PutByte(index byte, value byte) {
	e.checkCapacity(3)
	if value == 0 {
		e.putIndex(index)
	} else {
		buffer := e.buffer
		if index < little_index_bound {
			buffer.WriteByte(index | type_num_8)
		} else {
			buffer.WriteByte(big_index_mask | type_num_8)
			buffer.WriteByte(index)
		}
		buffer.WriteByte(value)
	}
}

func (e *Encoder) PutBool(index byte, value bool) {
	if value {
		e.PutByte(index, 1)
	} else {
		e.PutByte(index, 0)
	}
}

func (e *Encoder) PutInt16(index byte, value int16) {
	e.checkCapacity(4)
	if value == 0 {
		e.putIndex(index)
	} else {
		buffer := e.buffer
		pos := buffer.position
		e.putIndex(index)
		if (value >> 8) == 0 {
			buffer.hb[pos] |= type_num_8
			buffer.WriteByte(byte(value))
		} else {
			buffer.hb[pos] |= type_num_16
			buffer.WriteInt16(value)
		}
	}
}

func (e *Encoder) PutInt32(index byte, value int32) {
	e.checkCapacity(6)
	if value == 0 {
		e.putIndex(index)
	} else {
		buffer := e.buffer
		pos := buffer.position
		e.putIndex(index)
		if (value >> 8) == 0 {
			buffer.hb[pos] |= type_num_8
			buffer.WriteByte(byte(value))
		} else if (value >> 16) == 0 {
			buffer.hb[pos] |= type_num_16
			buffer.WriteInt16(int16(value))
		} else {
			buffer.hb[pos] |= type_num_32
			buffer.WriteInt32(value)
		}
	}
}

// Put int value with zigzag encoding.
//
// Zigzag encoding equivalent to:
// n = n >= 0 ? n * 2 : (-n) * 2 - 1;
//  Positive effect:
//  Make little negative integer to be little positive integer.
//  Side effect:
//  Double positive integer, some times it makes integer to cost more space than before.
//
// For example:
//  Numbers belong [128, 255], cost one byte,
//  after zigzag encode, to [256, 510], cost two bytes.
//
// So if the value is high probability to be little negative number, using zigzag encoding could be helpful,
// otherwise just use PutInt32(byte, int) will be more effective.
func (e *Encoder) PutSInt32(index byte, value int32) {
	e.PutInt32(index, (value<<1)^(value>>31))
}

func (e *Encoder) PutInt64(index byte, value int64) {
	e.checkCapacity(10)
	if value == 0 {
		e.putIndex(index)
	} else {
		buffer := e.buffer
		pos := buffer.position
		e.putIndex(index)
		if (value >> 32) != 0 {
			buffer.hb[pos] |= type_num_64
			buffer.WriteInt64(value)
		} else if (value >> 8) == 0 {
			buffer.hb[pos] |= type_num_8
			buffer.WriteByte(byte(value))
		} else if (value >> 16) == 0 {
			buffer.hb[pos] |= type_num_16
			buffer.WriteInt16(int16(value))
		} else {
			buffer.hb[pos] |= type_num_32
			buffer.WriteInt32(int32(value))
		}
	}
}

func (e *Encoder) PutSInt64(index byte, value int64) {
	e.PutInt64(index, (value<<1)^(value>>63))
}

func (e *Encoder) PutFloat32(index byte, value float32) {
	e.checkCapacity(6)
	if value == 0 {
		e.putIndex(index)
	} else {
		buffer := e.buffer
		if index < little_index_bound {
			buffer.WriteByte(index | type_num_32)
		} else {
			buffer.WriteByte(big_index_mask | type_num_32)
			buffer.WriteByte(index)
		}
		buffer.WriteFloat32(value)
	}
}

func (e *Encoder) PutFloat64(index byte, value float64) {
	e.checkCapacity(10)
	if value == 0 {
		e.putIndex(index)
	} else {
		buffer := e.buffer
		if index < little_index_bound {
			buffer.WriteByte(index | type_num_64)
		} else {
			buffer.WriteByte(big_index_mask | type_num_64)
			buffer.WriteByte(index)
		}
		buffer.WriteFloat64(value)
	}
}

// Put double value in a compact way.
//
// If the number in binary has few of '1' (significant bits), it can be compressed to two or four bytes.
// Double has 1 Sign bit, 11 exponent bits, and 52 significand bits.
// Because the significant bits always in high bit of significant part,
// and in most case exponent bits will not be all zeros,
// when significand bits is less than 4 bits, can compressed to 2 bytes,
// when significand bits is less than 20 bits,  can compressed to 4 bytes.
//
// Normally, we could just focus on if the number is little integer (no fractional part),
// when the integer is less then 1 << 21 (about two million), the lowest 4 bytes of double value must be zero.
// We can use this to save four bytes.
// For efficiency, we handle the 4 significand bits case as same as 20 significand bits case.
func (e *Encoder) PutCFloat64(index byte, value float64) {
	e.checkCapacity(10)
	if value == 0 {
		e.putIndex(index)
	} else {
		buffer := e.buffer
		pos := buffer.position
		e.putIndex(index)
		i := *(*uint64)(unsafe.Pointer(&value))
		i32 := i << 32
		if i32 == 0 {
			buffer.hb[pos] |= type_num_32
			buffer.WriteInt32(int32(i >> 32))
		} else {
			buffer.hb[pos] |= type_num_64
			buffer.WriteInt64(int64((i >> 32) | i32))
		}
	}
}

func (e *Encoder) PutStringRef(index byte, value *string) {
	if value == nil {
		return
	}
	size := int32(len(*value))
	e.wrapTagAndLength(index, size)
	if size > 0 {
		data := *(*[]byte)(unsafe.Pointer(value))
		e.buffer.WriteBytes(data)
	}
}

func (e *Encoder) PutString(index byte, value string) {
	e.PutStringRef(index, &value)
}

func (e *Encoder) PutStringArray(index byte, value []string) {
	if value == nil {
		return
	}
	n := int32(len(value))
	tagValue := e.wrapObjectArrayHeader(index, n)
	if tagValue < 0 {
		return
	}
	for i := int32(0); i < n; i++ {
		e.wrapString(&value[i])
	}
	e.putLen(int32(tagValue>>32), int32(tagValue))
}

func (e *Encoder) PutStringRefArray(index byte, value []*string) {
	if value == nil {
		return
	}
	n := int32(len(value))
	tagValue := e.wrapObjectArrayHeader(index, n)
	if tagValue < 0 {
		return
	}
	for i := int32(0); i < n; i++ {
		e.wrapString(value[i])
	}
	e.putLen(int32(tagValue>>32), int32(tagValue))
}

func (e *Encoder) wrapString(str *string) {
	buffer := e.buffer
	if str == nil {
		e.checkCapacity(5)
		buffer.WriteVarintNegative1()
	} else {
		size := int32(len(*str))
		e.checkCapacity(5 + size)
		buffer.WriteVarint32(size)
		if size > 0 {
			buffer.WriteBytes(*(*[]byte)(unsafe.Pointer(str)))
		}
	}
}

func (e *Encoder) PutPackableArray(index byte, value []Packable) {
	if value == nil {
		return
	}
	n := int32(len(value))
	tagValue := e.wrapObjectArrayHeader(index, n)
	if tagValue < 0 {
		return
	}
	for i := int32(0); i < n; i++ {
		e.wrapPackable(value[i])
	}
	e.putLen(int32(tagValue>>32), int32(tagValue))
}

func (e *Encoder) PutPackable(index byte, value Packable) {
	if value == nil {
		return
	}
	e.checkCapacity(6)
	buffer := e.buffer
	pTag := buffer.position
	e.putIndex(index)
	buffer.position += 4
	pValue := buffer.position
	value.Encode(e)
	if pValue == buffer.position {
		buffer.position -= 4
	} else {
		e.putLen(pTag, pValue)
	}
}

func (e *Encoder) wrapPackable(pack Packable) {
	e.checkCapacity(2)
	buffer := e.buffer
	if pack == nil {
		buffer.WriteInt16(null_packable)
	} else {
		pLen := buffer.position
		buffer.position += 2
		pPack := buffer.position
		pack.Encode(e)
		size := buffer.position - pPack
		if size <= 0x7fff {
			buffer.WriteInt16At(pLen, int16(size))
		} else {
			e.checkCapacity(2)
			buffer.move(pPack, size, 2)
			buffer.WriteInt16At(pLen, int16((uint32(size)>>16)|0x8000))
			buffer.WriteInt16At(pLen+2, int16(size&0xffff))
		}
	}
}

func (e *Encoder) putLen(pTag int32, pValue int32) {
	buffer := e.buffer
	size := buffer.position - pValue
	if size <= trim_size_limit {
		buffer.hb[pTag] |= type_var_8
		buffer.hb[pValue-4] = byte(size)
		buffer.move(pValue, size, -3)
	} else {
		buffer.hb[pTag] |= type_var_32
		buffer.WriteInt32At(pValue-4, size)
	}
}

func (e *Encoder) GetBuffer() *Buffer {
	return e.buffer
}

func (e *Encoder) wrapTagAndLength(index byte, size int32) {
	e.checkCapacity(6 + size)
	if size == 0 {
		e.putIndex(index)
	} else {
		buffer := e.buffer
		pos := buffer.position
		e.putIndex(index)
		if size <= 0xff {
			buffer.hb[pos] |= type_var_8
			buffer.WriteByte(byte(size))
		} else if size <= 0xffff {
			buffer.hb[pos] |= type_var_16
			buffer.WriteInt16(int16(size))
		} else {
			buffer.hb[pos] |= type_var_32
			buffer.WriteInt32(size)
		}
	}
}

func (e *Encoder) PutCustom(index byte, size int32) *Buffer {
	e.wrapTagAndLength(index, size)
	return e.buffer
}

func (e *Encoder) wrapObjectArrayHeader(index byte, size int32) int64 {
	if size > max_object_array_size {
		panic("object array size out of limit")
	}
	e.checkCapacity(11)
	buffer := e.buffer
	pTag := buffer.position
	e.putIndex(index)
	if size <= 0 {
		return -1
	}
	buffer.position += 4
	pValue := int64(buffer.position)
	buffer.WriteVarint32(size)
	return (int64(pTag) << 32) | pValue
}

func (e *Encoder) PutByteArray(index byte, value []byte) {
	if value == nil {
		return
	}
	n := int32(len(value))
	e.wrapTagAndLength(index, n)
	if n > 0 {
		e.buffer.WriteBytes(value)
	}
}

func (e *Encoder) PutInt32Array(index byte, value []int32) {
	if value == nil {
		return
	}
	buffer := e.buffer
	n := len(value)
	size := n << 2
	e.wrapTagAndLength(index, int32(size))
	if n >= 4 && isLittleEnd {
		pValue := (*reflect.SliceHeader)(unsafe.Pointer(&value))
		pValue.Len = size
		buffer.WriteBytes(*(*[]byte)(unsafe.Pointer(pValue)))
		pValue.Len = n
	} else {
		for i := 0; i < n; i++ {
			buffer.WriteInt32(value[i])
		}
	}
}

func (e *Encoder) PutInt64Array(index byte, value []int64) {
	if value == nil {
		return
	}
	buffer := e.buffer
	n := len(value)
	size := n << 3
	e.wrapTagAndLength(index, int32(size))
	if n >= 2 && isLittleEnd {
		pValue := (*reflect.SliceHeader)(unsafe.Pointer(&value))
		pValue.Len = size
		buffer.WriteBytes(*(*[]byte)(unsafe.Pointer(pValue)))
		pValue.Len = n
	} else {
		for i := 0; i < n; i++ {
			buffer.WriteInt64(value[i])
		}
	}
}

func (e *Encoder) PutFloat32Array(index byte, value []float32) {
	if value == nil {
		return
	}
	buffer := e.buffer
	n := len(value)
	size := n << 2
	e.wrapTagAndLength(index, int32(size))
	if n >= 4 && isLittleEnd {
		pValue := (*reflect.SliceHeader)(unsafe.Pointer(&value))
		pValue.Len = size
		buffer.WriteBytes(*(*[]byte)(unsafe.Pointer(pValue)))
		pValue.Len = n
	} else {
		for i := 0; i < n; i++ {
			buffer.WriteFloat32(value[i])
		}
	}
}

func (e *Encoder) PutFloat64Array(index byte, value []float64) {
	if value == nil {
		return
	}
	buffer := e.buffer
	n := len(value)
	size := n << 3
	e.wrapTagAndLength(index, int32(size))
	if n >= 2 && isLittleEnd {
		pValue := (*reflect.SliceHeader)(unsafe.Pointer(&value))
		pValue.Len = size
		buffer.WriteBytes(*(*[]byte)(unsafe.Pointer(pValue)))
		pValue.Len = n
	} else {
		for i := 0; i < n; i++ {
			buffer.WriteFloat64(value[i])
		}
	}
}

func (e *Encoder) PutStr2Str(index byte, value map[string]string) {
	if value == nil {
		return
	}
	tagValue := e.wrapObjectArrayHeader(index, int32(len(value)))
	if tagValue < 0 {
		return
	}
	for k, v := range value {
		e.wrapString(&k)
		e.wrapString(&v)
	}
	e.putLen(int32(tagValue >> 32), int32(tagValue))
}

func (e *Encoder)PutStr2Pack(index byte, value map[string]Packable){
	if value == nil {
		return
	}
	tagValue := e.wrapObjectArrayHeader(index, int32(len(value)))
	if tagValue < 0 {
		return
	}
	for k, v := range value {
		e.wrapString(&k)
		e.wrapPackable(v)
	}
	e.putLen(int32(tagValue >> 32), int32(tagValue))
}
