package pack

import (
	"errors"
	"fmt"
	"reflect"
	"unsafe"
)

const (
	NULL_FLAG             uint64 = 0xffffffffffffffff
	int_64_min_value      uint64 = 0x8000000000000000
	int_mask              uint64 = 0xffffffff
	decoder_pool_capacity        = 8
)

type Decoder struct {
	pool      *decoderPool
	buffer    *Buffer
	infoArray []uint64
	maxIndex  int32
}

func Unmarshal(bytes []byte, decode func(*Decoder) interface{}) (interface{}, error) {
	var err error = nil
	t := decodeBytes(bytes, decode, &err)
	return t, err
}

func Parse(bytes []byte, decode func(*Decoder)) error {
	var err error = nil
	parseBytes(bytes, decode, &err)
	return err
}

func decodeBytes(bytes []byte, decode func(*Decoder) interface{}, err *error) interface{} {
	defer func() {
		if e := recover(); e != nil {
			*err = errors.New(fmt.Sprintf("decode error,%v", e))
		}
	}()
	var p decoderPool
	decoder := createDecoder(bytes, &p)
	t := decode(decoder)
	decoder.Recycle()
	return t
}

func parseBytes(bytes []byte, decode func(*Decoder), err *error) {
	defer func() {
		if e := recover(); e != nil {
			*err = errors.New(fmt.Sprintf("decode error,%v", e))
		}
	}()
	var p decoderPool
	decoder := createDecoder(bytes, &p)
	decode(decoder)
	decoder.Recycle()
}

func NewDecoder(bytes []byte) *Decoder {
	return createDecoder(bytes, new(decoderPool))
}

func createDecoder(bytes []byte, pool *decoderPool) *Decoder {
	if bytes == nil {
		panic("bytes is null")
	}
	length := len(bytes)
	if length > int(max_buffer_size) {
		panic("buffer size over limit")
	}
	var d Decoder
	d.buffer = newDecodeBuffer(bytes, 0, int32(length))
	d.pool = pool
	d.pool.rootBuffer = d.buffer
	d.infoArray = nil
	d.maxIndex = -1
	return &d
}

func (d *Decoder) Recycle() {
	r := d.pool
	if r == nil {
		return
	}
	if d.buffer == r.rootBuffer {
		recycleLongArray(d.infoArray)
		r.releaseLongArray()
		d.pool = nil
		d.buffer = nil
		d.maxIndex = -1
	} else {
		r.recycleDecoder(d)
	}
}

func (d *Decoder) parseBuffer() {
	var existFlag uint64 = 0
	var existFlags []uint64 = nil
	if d.infoArray == nil {
		d.infoArray = getLongArray(default_size)
	}
	infoArray := d.infoArray
	infoLen := int32(len(infoArray))
	buffer := d.buffer

	for buffer.hasRemaining() {
		tag := buffer.ReadByte()
		var index int32
		if (tag & big_index_mask) == 0 {
			index = int32(tag & index_mask)
		} else {
			index = int32(buffer.ReadByte())
		}
		if index > d.maxIndex {
			d.maxIndex = index
		}
		if index < 64 {
			existFlag |= 1 << index
		} else {
			if existFlags == nil {
				existFlags = make([]uint64, 4)
			}
			existFlags[index>>8] |= 1 << (index & 0x3f)
		}

		if index >= infoLen {
			oldArray := infoArray
			infoArray = getLongArray(index + 1)
			infoLen = int32(len(infoArray))
			copy(infoArray, oldArray)
			recycleLongArray(oldArray)
		}

		t := tag & type_mask
		if t <= type_num_64 {
			if t == type_0 {
				infoArray[index] = 0
			} else if t == type_num_8 {
				infoArray[index] = uint64(buffer.ReadByte())
			} else if t == type_num_16 {
				infoArray[index] = uint64(uint16(buffer.ReadInt16()))
			} else if t == type_num_32 {
				infoArray[index] = uint64(uint32(buffer.ReadInt32()))
			} else {
				b8 := buffer.hb[buffer.position+7]
				if b8 < 0x80 {
					infoArray[index] = uint64(buffer.ReadInt64())
				} else {
					infoArray[index] = uint64(buffer.position) | int_64_min_value
					buffer.position += 8
				}
			}
		} else {
			var size int32
			if t == type_var_8 {
				size = int32(buffer.ReadByte())
			} else if t == type_var_16 {
				size = int32(uint16(buffer.ReadInt16()))
			} else {
				size = buffer.ReadInt32()
			}
			infoArray[index] = (uint64(buffer.position) << 32) | uint64(size)
			buffer.position += size
		}
	}

	if buffer.position != buffer.limit {
		panic("invalid pack data")
	}

	maxIndex := d.maxIndex

	if maxIndex <= 0 {
		return
	}

	bits := 63 - maxIndex
	flippedFlag := (^existFlag) << bits
	if flippedFlag == 0 {
		return
	}
	flippedFlag >>= bits
	var i int32 = 0
	for flippedFlag != 0 {
		if (flippedFlag & 1) != 0 {
			infoArray[i] = NULL_FLAG
		}
		i++
		flippedFlag >>= 1
	}
	if existFlags != nil {
		for i = 64; i < maxIndex; i++ {
			if (existFlags[i>>8] & (uint64(1) << (i & 0x3F))) == 0 {
				infoArray[i] = NULL_FLAG
			}
		}
	}
}

func (d *Decoder) GetCustom(index int32) *Buffer {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return nil
	}
	size := int32(info & int_mask)
	if size == 0 {
		d.buffer.position = d.buffer.limit
	}else{
		d.buffer.position =	(int32) (info>> 32)
	}
	return d.buffer
}

func (d *Decoder) GetInfo(index int32) uint64 {
	if d.maxIndex < 0 {
		if d.buffer == nil {
			panic("Decoder had been recycled")
		}
		d.parseBuffer()
	}
	if index > d.maxIndex {
		return NULL_FLAG
	}
	return d.infoArray[index]
}

func (d *Decoder) Contains(index int32) bool {
	return d.GetInfo(index) != NULL_FLAG
}

func (d *Decoder) GetBoolOrDefault(index int32, defValue bool) bool {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return defValue
	} else {
		return info == 1
	}
}

func (d *Decoder) GetBool(index int32) bool {
	return d.GetInfo(index) == 1
}

func (d *Decoder) GetByte(index int32) byte {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return 0
	} else {
		return byte(info)
	}
}

func (d *Decoder) GetInt16(index int32) int16 {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return 0
	} else {
		return int16(info)
	}
}

func (d *Decoder) GetInt32OrDefault(index int32, defValue int32) int32 {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return defValue
	} else {
		return int32(info)
	}
}

func (d *Decoder) GetInt32(index int32) int32 {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return 0
	} else {
		return int32(info)
	}
}

func (d *Decoder) GetSInt32OrDefault(index int32, defValue int32) int32 {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return defValue
	} else {
		n := int32(info)
		return int32(uint32(n)>>1) ^ -(n & 1)
	}
}

func (d *Decoder) GetSInt32(index int32) int32 {
	return d.GetSInt32OrDefault(index, 0)
}

func (d *Decoder) GetInt64OrDefault(index int32, defValue int64) int64 {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return defValue
	} else {
		if info < int_64_min_value {
			return int64(info)
		} else {
			return d.buffer.ReadInt64At(int32(info & int_mask))
		}
	}
}

func (d *Decoder) GetInt64(index int32) int64 {
	return d.GetInt64OrDefault(index, 0)
}

func (d *Decoder) GetSInt64OrDefault(index int32, defValue int64) int64 {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return defValue
	} else {
		var n int64
		if info < int_64_min_value {
			n = int64(info)
		} else {
			n = d.buffer.ReadInt64At(int32(info & int_mask))
		}
		return int64(uint64(n)>>1) ^ -(n & 1)
	}
}

func (d *Decoder) GetSInt64(index int32) int64 {
	return d.GetSInt64OrDefault(index, 0)
}

func (d *Decoder) GetFloat32OrDefault(index int32, defValue float32) float32 {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return defValue
	} else {
		t := int32(info)
		return *(*float32)(unsafe.Pointer(&t))
	}
}

func (d *Decoder) GetFloat32(index int32) float32 {
	return d.GetFloat32OrDefault(index, 0)
}

func (d *Decoder) GetFloat64OrDefault(index int32, defValue float64) float64 {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return defValue
	} else {
		var n int64
		if info < int_64_min_value {
			n = int64(info)
		} else {
			n = d.buffer.ReadInt64At(int32(info & int_mask))
		}
		return *(*float64)(unsafe.Pointer(&n))
	}
}

func (d *Decoder) GetFloat64(index int32) float64 {
	return d.GetFloat64OrDefault(index, 0)
}

func (d *Decoder) GetCFloat64OrDefault(index int32, defValue float64) float64 {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return defValue
	} else {
		var n uint64
		if info < int_64_min_value {
			n = info
		} else {
			n = uint64(d.buffer.ReadInt64At(int32(info & int_mask)))
		}
		n = (n << 32) | (n >> 32)
		return *(*float64)(unsafe.Pointer(&n))
	}
}

func (d *Decoder) GetCFloat64(index int32) float64 {
	return d.GetCFloat64OrDefault(index, 0)
}

func (d *Decoder) GetStringRef(index int32) *string {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return nil
	}
	size := int32(info & int_mask)
	if size == 0 {
		str := ""
		return &str
	}
	offset := int32(info >> 32)
	str := string(d.buffer.hb[offset : offset+size])
	return &str
}

func (d *Decoder) GetStringOrDefault(index int32, defValue string) string {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return defValue
	}
	size := int32(info & int_mask)
	if size == 0 {
		return ""
	}
	offset := int32(info >> 32)
	return string(d.buffer.hb[offset : offset+size])
}

func (d *Decoder) GetString(index int32) string {
	return d.GetStringOrDefault(index, "")
}

func (d *Decoder) GetStringArray(index int32) []string {
	n := d.getSize(index)
	if n < 0 {
		return nil
	}
	value := make([]string, n)
	for i := 0; i < n; i++ {
		value[i] = d.takeString()
	}
	return value
}

func (d *Decoder) GetStringRefArray(index int32) []*string {
	n := d.getSize(index)
	if n < 0 {
		return nil
	}
	value := make([]*string, n)
	for i := 0; i < n; i++ {
		value[i] = d.takeStringRef()
	}
	return value
}

func (d *Decoder) takeString() string {
	ref := d.takeStringRef()
	if ref == nil {
		return ""
	} else {
		return *ref
	}
}

func (d *Decoder) takeStringRef() *string {
	buffer := d.buffer
	size := buffer.ReadVarint32()
	if size < 0 {
		return nil
	}
	offset := buffer.position
	buffer.checkBound(offset, size)
	if size == 0 {
		str := ""
		return &str
	}
	str := string(buffer.hb[offset : offset+size])
	buffer.position += size
	return &str
}

func (d *Decoder) GetPackable(index int32, decode func(*Decoder) interface{}) interface{} {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return nil
	}
	offset := int32(info >> 32)
	size := int32(info & int_mask)
	td := d.pool.getDecoder(offset, size)
	t := decode(td)
	td.Recycle()
	return t
}

func (d *Decoder) GetPackableArray(index int32,
	decode func(decoder *Decoder) *Packable,
	newArray func(size int) *[]*Packable) *[]*Packable {
	n := d.getSize(index)
	if n < 0 {
		return nil
	}
	value := newArray(n)
	v := *value
	for i := 0; i < n; i++ {
		v[i] = d.takePackable(decode)
	}
	return value
}

func (d *Decoder) takePackable(decode func(decoder *Decoder) *Packable) *Packable {
	buffer := d.buffer
	a := buffer.ReadInt16()
	if a == null_packable {
		return nil
	}
	var size int32
	if a >= 0 {
		size = int32(a)
	} else {
		size = ((int32(a) & 0x7fff) << 16) | int32(uint16(buffer.ReadInt16()))
	}
	offset := buffer.position
	buffer.checkBound(offset, size)
	td := d.pool.getDecoder(offset, size)
	result := decode(td)
	td.Recycle()
	buffer.position += size
	return result
}

func (d *Decoder) getSize(index int32) int {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return -1
	}
	size := int32(info & int_mask)
	if size == 0 {
		return 0
	}
	buffer := d.buffer
	buffer.position = int32(info >> 32)
	n := buffer.ReadVarint32()
	if n < 0 || n > max_object_array_size {
		panic("invalid size of object array")
	}
	return int(n)
}

func (d *Decoder) GetByteArray(index int32) []byte {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return nil
	}
	buffer := d.buffer
	buffer.position = int32(info >> 32)
	size := int32(info & int_mask)
	bytes := make([]byte, size)
	buffer.ReadBytes(bytes)
	return bytes
}

func (d *Decoder) setPosAndGetLen(info uint64, mask int) int {
	d.buffer.position = int32(info >> 32)
	size := int(info & int_mask)
	if (size & mask) != 0 {
		panic("invalid array length")
	}
	return size
}

func (d *Decoder) GetInt32Array(index int32) []int32 {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return nil
	}
	size := d.setPosAndGetLen(info, 0x3)
	n := size >> 2
	value := make([]int32, n)
	buffer := d.buffer
	if n >= 4 && isLittleEnd {
		pValue := (*reflect.SliceHeader)(unsafe.Pointer(&value))
		pValue.Len = size
		buffer.ReadBytes(*(*[]byte)(unsafe.Pointer(pValue)))
		pValue.Len = n
		buffer.position += int32(size)
	} else {
		for i := 0; i < n; i++ {
			value[i] = buffer.ReadInt32()
		}
	}
	return value
}

func (d *Decoder) GetInt64Array(index int32) []int64 {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return nil
	}
	size := d.setPosAndGetLen(info, 0x7)
	n := size >> 3
	value := make([]int64, n)
	buffer := d.buffer
	if n >= 2 && isLittleEnd {
		pValue := (*reflect.SliceHeader)(unsafe.Pointer(&value))
		pValue.Len = size
		buffer.ReadBytes(*(*[]byte)(unsafe.Pointer(pValue)))
		pValue.Len = n
		buffer.position += int32(size)
	} else {
		for i := 0; i < n; i++ {
			value[i] = buffer.ReadInt64()
		}
	}
	return value
}

func (d *Decoder) GetFloat32Array(index int32) []float32 {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return nil
	}
	size := d.setPosAndGetLen(info, 0x3)
	n := size >> 2
	value := make([]float32, n)
	buffer := d.buffer
	if n >= 4 && isLittleEnd {
		pValue := (*reflect.SliceHeader)(unsafe.Pointer(&value))
		pValue.Len = size
		buffer.ReadBytes(*(*[]byte)(unsafe.Pointer(pValue)))
		pValue.Len = n
		buffer.position += int32(size)
	} else {
		for i := 0; i < n; i++ {
			value[i] = buffer.ReadFloat32()
		}
	}
	return value
}

func (d *Decoder) GetFloat64Array(index int32) []float64 {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return nil
	}
	size := d.setPosAndGetLen(info, 0x7)
	n := size >> 3
	value := make([]float64, n)
	buffer := d.buffer
	if n >= 2 && isLittleEnd {
		pValue := (*reflect.SliceHeader)(unsafe.Pointer(&value))
		pValue.Len = size
		buffer.ReadBytes(*(*[]byte)(unsafe.Pointer(pValue)))
		pValue.Len = n
		buffer.position += int32(size)
	} else {
		for i := 0; i < n; i++ {
			value[i] = buffer.ReadFloat64()
		}
	}
	return value
}

func (d *Decoder) GetStr2Str(index int32) map[string]string {
	n := d.getSize(index)
	if n < 0 {
		return nil
	}
	value := make(map[string]string)
	for i := 0; i < n; i++ {
		k := d.takeString()
		v := d.takeStringRef()
		if v != nil {
			value[k] = *v
		}
	}
	return value
}

func (d *Decoder) GetStr2Pack(index int32, decode func(decoder *Decoder) *Packable) map[string]*Packable {
	n := d.getSize(index)
	if n < 0 {
		return nil
	}
	value := make(map[string]*Packable)
	for i := 0; i < n; i++ {
		k := d.takeString()
		v := d.takePackable(decode)
		if v != nil {
			value[k] = v
		}
	}
	return value
}
