package pack

import (
	"unsafe"
)

type Buffer struct {
	hb       []byte
	position int32
	limit    int32
}

func newEncodeBuffer(bytes []byte) *Buffer {
	return &Buffer{
		hb:       bytes,
		position: 0,
		limit:    int32(len(bytes)),
	}
}

func newDecodeBuffer(bytes []byte, offset int32, length int32) *Buffer {
	return &Buffer{
		hb:       bytes,
		position: offset,
		limit:    offset + length,
	}
}

func (b *Buffer) hasRemaining() bool {
	return b.position < b.limit
}

func (b *Buffer) checkBound(offset int32, size int32) {
	if offset+size > b.limit {
		panic("b out of bound")
	}
}

func (b *Buffer) WriteByte(x byte) {
	b.hb[b.position] = x
	b.position++
}

func (b *Buffer) ReadByte() byte {
	t := b.hb[b.position]
	b.position++
	return t
}

func (b *Buffer) WriteInt16At(i int32, x int16) {
	b.hb[i] = uint8(x)
	b.hb[i+1] = uint8(x >> 8)
}

func (b *Buffer) WriteInt16(x int16) {
	b.hb[b.position] = uint8(x)
	b.hb[b.position+1] = uint8(x >> 8)
	b.position += 2
}

func (b *Buffer) ReadInt16() int16 {
	i := b.position
	b.position += 2
	return int16(b.hb[i]) | int16(b.hb[i+1])<<8
}

func (b *Buffer) WriteInt32At(i int32, x int32) {
	b.hb[i] = uint8(x)
	b.hb[i+1] = uint8(x >> 8)
	b.hb[i+2] = uint8(x >> 16)
	b.hb[i+3] = uint8(x >> 24)
}

func (b *Buffer) WriteInt32(x int32) {
	b.WriteInt32At(b.position, x)
	b.position += 4
}

func (b *Buffer) ReadInt32() int32 {
	i := b.position
	b.position += 4
	return int32(b.hb[i]) |
		(int32(b.hb[i+1]) << 8) |
		(int32(b.hb[i+2]) << 16) |
		(int32(b.hb[i+3]) << 24)
}

func GetVarint32Size(x uint32) int32 {
	if x <= 0x7f {
		return 1
	} else if x <= 0x3fff {
		return 2
	} else if x <= 0x1fffff {
		return 3
	} else if x <= 0xfffffff {
		return 4
	}
	return 5
}

func (b *Buffer) WriteVarintNegative1() {
	i := b.position
	p := b.hb
	p[i] = 0xff
	p[i+1] = 0xff
	p[i+2] = 0xff
	p[i+3] = 0xff
	p[i+4] = 0xf
	b.position += 5
}

func (b *Buffer) WriteVarint32(value int32) {
	x := uint32(value)
	i := b.position
	for x > 0x7f {
		b.hb[i] = uint8((x & 0x7f) | 0x80)
		i++
		x >>= 7
	}
	b.hb[i] = uint8(x)
	b.position = i + 1
}

func (b *Buffer) ReadVarint32() int32 {
	p := b.hb
	var x = uint32(p[b.position])
	b.position++

	if x <= 0x7f {
		return int32(x)
	}
	x = (x & 0x7f) | (uint32(p[b.position]) << 7)
	b.position++

	if x <= 0x3fff {
		return int32(x)
	}
	x = (x & 0x3fff) | (uint32(p[b.position]) << 14)
	b.position++

	if x <= 0x1fffff {
		return int32(x)
	}
	x = (x & 0x1fffff) | (uint32(p[b.position]) << 21)
	b.position++

	if x <= 0xfffffff {
		return int32(x)
	}
	x = (x & 0xfffffff) | (uint32(p[b.position]) << 28)
	b.position++

	return int32(x)
}

func (b *Buffer) WriteInt64(x int64) {
	i := b.position
	p := b.hb
	p[i] = uint8(x)
	p[i+1] = uint8(x >> 8)
	p[i+2] = uint8(x >> 16)
	p[i+3] = uint8(x >> 24)
	p[i+4] = uint8(x >> 32)
	p[i+5] = uint8(x >> 40)
	p[i+6] = uint8(x >> 48)
	p[i+7] = uint8(x >> 56)
	b.position += 8
}

func (b *Buffer) ReadInt64At(i int32) int64 {
	p := b.hb
	return int64(p[i]) |
		(int64(p[i+1]) << 8) |
		(int64(p[i+2]) << 16) |
		(int64(p[i+3]) << 24) |
		(int64(p[i+4]) << 32) |
		(int64(p[i+5]) << 40) |
		(int64(p[i+6]) << 48) |
		(int64(p[i+7]) << 56)
}

func (b *Buffer) ReadInt64() int64 {
	t := b.ReadInt64At(b.position)
	b.position += 8
	return t
}

func (b *Buffer) WriteFloat32(x float32) {
	b.WriteInt32(*((*int32)(unsafe.Pointer(&x))))
}

func (b *Buffer) ReadFloat32() float32 {
	t := b.ReadInt32()
	return *(*float32)(unsafe.Pointer(&t))
}

func (b *Buffer) WriteFloat64(x float64) {
	b.WriteInt64(*(*int64)(unsafe.Pointer(&x)))
}

func (b *Buffer) ReadFloat64() float64 {
	t := b.ReadInt64()
	return *(*float64)(unsafe.Pointer(&t))
}

func (b *Buffer) WriteBytes(data []byte) {
	size := int32(len(data))
	pos := b.position
	copy(b.hb[pos : pos+size], data)
	b.position += size
}

func (b *Buffer) ReadBytes(data []byte)  {
	size := int32(len(data))
	if size > 0 {
		copy(data, b.hb[b.position : b.position+size])
		b.position += size
	}
}

func (b *Buffer) move(pos int32, size int32, diff int32) {
	desPos := pos + diff
	src := b.hb[pos : pos+size]
	dst := b.hb[desPos : desPos+size]
	copy(dst, src)
	b.position += diff
}
