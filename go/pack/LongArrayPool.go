package pack

import "sync"

const (
	default_size     int32 = 32
	default_capacity int32 = 24
	second_size      int32 = 64
	second_capacity  int32 = 8
)

var defaultCount int32 = 0
var defaultArrays [default_capacity][]uint64

var secondCount int32 = 0
var secondArrays [second_capacity][]uint64

var lMutex1 sync.Mutex
var lMutex2 sync.Mutex

func getLongArray(size int32) []uint64 {
	if size <= default_size {
		var a []uint64
		lMutex1.Lock()
		if defaultCount > 0 {
			defaultCount--
			a = defaultArrays[defaultCount]
			defaultArrays[defaultCount] = nil

		} else {
			a = make([]uint64, default_size)
		}
		lMutex1.Unlock()
		return a
	}
	if size <= second_size {
		var a []uint64
		lMutex2.Lock()
		if secondCount > 0 {
			secondCount--
			a = secondArrays[secondCount]
			secondArrays[secondCount] = nil
		} else {
			a = make([]uint64, second_size)
		}
		lMutex2.Unlock()
		return a
	}
	if size <= 128 {
		return make([]uint64, 128)
	}
	return make([]uint64, 256)
}

func recycleLongArray(a []uint64) {
	if a == nil {
		return
	}
	size := int32(len(a))

	if size == default_size {
		lMutex1.Lock()
		if defaultCount < default_capacity {
			defaultArrays[defaultCount] = a
			defaultCount++
		}
		lMutex1.Unlock()
	} else if size == second_size {
		lMutex2.Lock()
		if secondCount < second_capacity {
			secondArrays[secondCount] = a
			secondCount++
		}
		lMutex2.Unlock()
	}
}
