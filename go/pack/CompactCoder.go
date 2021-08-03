package pack

import "unsafe"

const (
	num_type_int32   = 0
	num_type_int64   = 1
	num_type_float64 = 2
)

type arrayWrapper struct {
	int32Array   []int32
	int64Array   []int64
	float64Array []float64
}

func (e *Encoder) PutCompactInt32Array(index byte, value []int32) {
	if value != nil {
		wrapper := new(arrayWrapper)
		wrapper.int32Array = value
		e.putNumberArray(index, wrapper, num_type_int32, int32(len(value)))
	}
}

func (e *Encoder) PutCompactInt64Array(index byte, value []int64) {
	if value != nil {
		wrapper := new(arrayWrapper)
		wrapper.int64Array = value
		e.putNumberArray(index, wrapper, num_type_int64, int32(len(value)))
	}
}

func (e *Encoder) PutCompactFloat64Array(index byte, value []float64) {
	if value != nil {
		wrapper := new(arrayWrapper)
		wrapper.float64Array = value
		e.putNumberArray(index, wrapper, num_type_float64, int32(len(value)))
	}
}

func (d *Decoder) GetCompactInt32Array(index int32) []int32 {
	wrapper := d.getNumberArray(index, num_type_int32)
	if wrapper != nil {
		return wrapper.int32Array
	}
	return nil
}

func (d *Decoder) GetCompactInt64Array(index int32) []int64 {
	wrapper := d.getNumberArray(index, num_type_int64)
	if wrapper != nil {
		return wrapper.int64Array
	}
	return nil
}

func (d *Decoder) GetCompactFloat64Array(index int32) []float64 {
	wrapper := d.getNumberArray(index, num_type_float64)
	if wrapper != nil {
		return wrapper.float64Array
	}
	return nil
}

func newArrayWrapper(numberType int, n int32) *arrayWrapper {
	wrapper := new(arrayWrapper)
	if numberType == num_type_int32 {
		wrapper.int32Array = make([]int32, n)
	} else if numberType == num_type_int64 {
		wrapper.int64Array = make([]int64, n)
	} else {
		wrapper.float64Array = make([]float64, n)
	}
	return wrapper
}

func getByteCount(totalBits int32) int32 {
	byteCount := totalBits >> 3
	if (totalBits & 0x7) != 0 {
		byteCount++
	}
	return byteCount
}

func wrapArray(buffer *Buffer, n int32, pFlag int32, wrapper *arrayWrapper, numberType int) {
	i := int32(0)
	for i < n {
		end := min(i+int32(4), n)
		flags := int32(0)
		if numberType == num_type_float64 {
			for j := i; j < end; j++ {
				d := wrapper.float64Array[j]
				if d == 0 {
					continue
				}
				shift := (j & 3) << 1
				e := *(*int64)(unsafe.Pointer(&d))
				if (e << 16) == 0 {
					buffer.WriteInt16(int16(e >> 48))
					flags |= 1 << shift
				} else if (e << 32) == 0 {
					buffer.WriteInt32(int32(e >> 32))
					flags |= 2 << shift
				} else {
					buffer.WriteInt64(e)
					flags |= 3 << shift
				}
			}
		} else {
			for j := i; j < end; j++ {
				var e int64
				if numberType == num_type_int32 {
					e = int64(wrapper.int32Array[j])
				} else {
					e = wrapper.int64Array[j]
				}
				if e == 0 {
					continue
				}
				shift := (j & 3) << 1
				if (e >> 8) == 0 {
					buffer.WriteByte(byte(e))
					flags |= 1 << shift
				} else if (e >> 16) == 0 {
					buffer.WriteInt16(int16(e))
					flags |= 2 << shift
				} else {
					if numberType == num_type_int32 {
						buffer.WriteInt32(int32(e))
					} else {
						buffer.WriteInt64(e)
					}
					flags |= 3 << shift
				}
			}
		}
		buffer.hb[pFlag+(i>>2)] = byte(flags)
		i = end
	}
}

func (e *Encoder) putNumberArray(index byte, wrapper *arrayWrapper, numberType int, n int32) {
	if n < 0 {
		return
	}
	if n == 0 {
		e.wrapTagAndLength(index, 0)
		return
	}

	// calculate spaces
	sizeOfN := GetVarint32Size(uint32(n))
	flagByteCount := getByteCount(n << 1)

	// wrap tag and reserve space for len
	var shift int32
	if numberType == num_type_int32 {
		shift = 2
	} else {
		shift = 3
	}
	maxSize := sizeOfN + flagByteCount + (n << shift)
	e.checkCapacity(6 + maxSize)
	buffer := e.buffer
	pTag := buffer.position
	e.putIndex(index)
	pLen := buffer.position
	var sizeOfLen int32
	if maxSize <= 0xff {
		buffer.hb[pTag] |= type_var_8
		sizeOfLen = 1
	} else if maxSize <= 0xffff {
		buffer.hb[pTag] |= type_var_16
		sizeOfLen = 2
	} else {
		buffer.hb[pTag] |= type_var_32
		sizeOfLen = 4
	}
	buffer.position += sizeOfLen

	buffer.WriteVarint32(n)
	pFlag := buffer.position
	// move position to values
	buffer.position += flagByteCount

	wrapArray(buffer, n, pFlag, wrapper, numberType)

	// wrap size
	// maxSize must be large than len, so it's safe to put size in position pLen
	size := buffer.position - (pLen + sizeOfLen)
	if sizeOfLen == 1 {
		buffer.hb[pLen] = byte(size)
	} else if sizeOfLen == 2 {
		buffer.WriteInt16At(pLen, int16(size))
	} else {
		buffer.WriteInt32At(pLen, size)
	}
}

func takeArray(buffer *Buffer, n int32, pFlag int32, wrapper *arrayWrapper, numberType int) {
	for i := int32(0); i < n; i += 4 {
		b := int32(buffer.hb[pFlag+(i>>2)] & 0xFF)
		j := i
		for b != 0 {
			flag := b & int32(3)
			if flag != 0 {
				if numberType == num_type_float64 {
					var x int64
					if flag == 1 {
						x = (int64(buffer.ReadInt16())) << 48
					} else if flag == 2 {
						x = (int64(buffer.ReadInt32())) << 32
					} else {
						x = buffer.ReadInt64()
					}
					wrapper.float64Array[j] = *(*float64)(unsafe.Pointer(&x))

				} else if numberType == num_type_int32 {
					var x int32
					if flag == 1 {
						x = int32(buffer.ReadByte()) & 0xff
					} else if flag == 2 {
						x = int32(buffer.ReadInt16()) & 0xffff
					} else {
						x = buffer.ReadInt32()
					}
					wrapper.int32Array[j] = x
				} else {
					var x int64
					if flag == 1 {
						x = int64(buffer.ReadByte()) & 0xff
					} else if flag == 2 {
						x = int64(buffer.ReadInt16()) & 0xffff
					} else {
						x = buffer.ReadInt64()
					}
					wrapper.int64Array[j] = x
				}
			}
			j++
			b >>= 2
		}
	}
}

func (d *Decoder) getNumberArray(index int32, numberType int) *arrayWrapper {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return nil
	}
	size := int(info & int_mask)
	if size == 0 {
		return newArrayWrapper(numberType, 0)
	}

	buffer := d.buffer
	buffer.position = int32(info >> 32)
	n := buffer.ReadVarint32()
	if n < 0 {
		panic("invalid array size")
	}
	pFlag := buffer.position
	byteCount := getByteCount(n << 1)
	buffer.checkBound(buffer.position, byteCount)
	buffer.position += byteCount

	wrapper := newArrayWrapper(numberType, n)
	takeArray(buffer, n, pFlag, wrapper, numberType)
	return wrapper
}

func (e *Encoder) PutBoolArray(index byte, value []bool) {
	if value == nil {
		return
	}
	n := len(value)
	if n == 0 {
		e.wrapTagAndLength(index, 0)
		return
	}

	buffer := e.buffer
	if n <= 5 {
		b := n << 5
		for i := 0; i < n; i++ {
			if value[i] {
				b |= 1 << i
			}
		}
		e.wrapTagAndLength(index, 1)
		buffer.WriteByte(byte(b))
	} else {
		remain := n & 7
		byteCount := n >> 3
		if remain == 0 {
			byteCount += 1
		} else {
			byteCount += 2
		}
		e.wrapTagAndLength(index, int32(byteCount))
		buffer.WriteByte(byte(remain))
		for i := 0; i < n; {
			end := i + 8
			if end > n {
				end = n
			}
			b := 0
			for j := i; j < end; j++ {
				if value[j] {
					b |= 1 << (j & 7)
				}
			}
			buffer.WriteByte(byte(b))
			i = end
		}
	}
}

func (d *Decoder) GetBoolArray(index int32) []bool {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return nil
	}
	size := int(info & int_mask)
	if size == 0 {
		return make([]bool, 0)
	}

	buffer := d.buffer
	buffer.position = int32(info >> 32)
	var a []bool
	if size == 1 {
		b := int(buffer.ReadByte())
		n := b >> 5
		a = make([]bool, n)
		for i := 0; i < n; i++ {
			a[i] = (b & 1) != 0
			b >>= 1
		}
	} else {
		remain := int(buffer.ReadByte())
		if (remain >> 3) != 0 {
			panic("remain overflow")
		}
		byteCount := size - 1
		n := byteCount << 3
		if remain > 0 {
			n -= 8 - remain
		}
		a = make([]bool, n)
		for i := 0; i < n; i += 8 {
			b := buffer.ReadByte()
			j := i
			for b != 0 {
				a[j] = (b & 1) != 0
				j++
				b >>= 1
			}
		}
	}
	return a
}

func min(a int32, b int32) int32 {
	if a < b {
		return a
	} else {
		return b
	}
}

func (e *Encoder) PutEnumArray(index byte, value []int32) {
	if value == nil {
		return
	}
	n := int32(len(value))
	if n == 0 {
		e.wrapTagAndLength(index, 0)
		return
	}
	sum := int32(0)
	for _, e := range value {
		sum |= e
	}
	var bitShift int32
	if (sum >> 1) == 0 {
		bitShift = 0
	} else if (sum >> 2) == 0 {
		bitShift = 1
	} else if (sum >> 4) == 0 {
		bitShift = 2
	} else if (sum >> 8) == 0 {
		bitShift = 3
	} else {
		panic("only accept values less than 255")
	}

	var byteCount int32
	buffer := e.buffer
	if bitShift == 3 {
		byteCount = n + 1
		e.wrapTagAndLength(index, byteCount)
		pos := buffer.position
		buffer.hb[pos] = (byte)(bitShift << 3)
		pos++
		for i := int32(0); i < n; i++ {
			buffer.hb[pos+i] = byte(value[i])
		}
	} else {
		totalBits := n << bitShift
		remain := totalBits & 0x7
		r := int32(1)
		if remain != 0 {
			r++
		}
		byteCount = int32((totalBits >> int32(3)) + r)
		e.wrapTagAndLength(index, byteCount)
		pos := buffer.position
		buffer.hb[pos] = (byte)((bitShift << 3) | remain)
		pos++

		// bitShift=0, indexShift=3, indexMask=0x7
		// bitShift=1, indexShift=2, indexMask=0x3
		// bitShift=2, indexShift=1, indexMask=0x1

		indexShift := int32(3) - bitShift

		indexMask := int32(^((^0) << indexShift))
		step := int32(1) << indexShift

		i := int32(0)
		for i < n {
			end := min(i+step, n)
			b := int32(0)
			for j := i; j < end; j++ {
				b |= value[j] << ((j & indexMask) << bitShift)
			}
			buffer.hb[pos+(i>>indexShift)] = byte(b)
			i = end
		}
	}
	buffer.position += byteCount
}

func (d *Decoder) GetEnumArray(index int32) []int32 {
	info := d.GetInfo(index)
	if info == NULL_FLAG {
		return nil
	}
	size := int(info & int_mask)
	if size == 0 {
		return make([]int32, 0)
	}

	buffer := d.buffer
	buffer.position = int32(info >> 32)

	bitInfo := int32(buffer.ReadByte())
	if (bitInfo >> 5) != 0 {
		panic("bit info overflow")
	}
	bitShift := bitInfo >> 3
	byteCount := int32(size - 1)
	if bitShift == 3 {
		a := make([]int32, byteCount)
		for i := int32(0); i < byteCount; i++ {
			a[i] = int32(buffer.ReadByte() & 0xff)
		}
		return a
	} else {
		remain := bitInfo & 0x7
		indexShift := 3 - bitShift
		n := byteCount << indexShift
		if remain > 0 {
			n -= (int32(8) - remain) >> bitShift
		}
		a := make([]int32, n)
		pos := buffer.position
		byteShirt := 1 << bitShift
		valueMask := int32(^((^0) << byteShirt))
		step := int32(1) << indexShift
		for i := int32(0); i < n; i += step {
			b := int32(buffer.hb[pos+(i>>indexShift)]) & 0xFF
			j := i
			for b != 0 {
				a[j] = b & valueMask
				j++
				b >>= byteShirt
			}
		}
		buffer.position += byteCount
		return a
	}
}
