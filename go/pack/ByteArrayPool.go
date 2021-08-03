package pack

import (
	"sync"
)

const (
	arraySize              = 6
	arrayCapacity          = 8
	minArrayShift    int32 = 12
	minByteArraySize int32 = 1 << minArrayShift
	maxByteArraySize       = 1 << (((arraySize - 1) << 1) + minArrayShift)
)

var byteArrayPool [arraySize][arrayCapacity][]byte
var byteArrayCounts [arraySize]int

var bMutex [arraySize]sync.Mutex

func getByteArrayIndex(size int32) int32 {
	if size <= minByteArraySize {
		return 0
	}
	var n int32 = 0
	a := (size - 1) >> minArrayShift
	for a != 0 {
		a >>= 2
		n++
	}
	return n
}

func getByteArray(size int32) []byte {
	if size > maxByteArraySize {
		return make([]byte, size)
	}
	i := getByteArrayIndex(size)
	var bytes []byte
	bMutex[i].Lock()
	count := byteArrayCounts[i]
	if count > 0 {
		count--
		bytes = byteArrayPool[i][count]
		byteArrayCounts[i] = count
	}
	bMutex[i].Unlock()
	if bytes == nil {
		return make([]byte, size)
	}
	return bytes
}

func recycleByteArray(bytes []byte) {
	size := int32(len(bytes))
	if size > maxByteArraySize {
		return
	}
	i := getByteArrayIndex(size)
	var capacity int32 = 1 << ((i << 1) + minArrayShift)
	if size == capacity {
		bMutex[i].Lock()
		count := byteArrayCounts[i]
		if count < arrayCapacity {
			byteArrayPool[i][count] = bytes
			byteArrayCounts[i] = count + 1
		}
		bMutex[i].Unlock()
	}
}
