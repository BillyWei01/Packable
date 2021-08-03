package main

import (
	"../pack"
	"fmt"
	"log"
	"math/rand"
	"reflect"
	"unsafe"
)

type SimpleVo struct {
	code    int32
	detail  string
	strArr  []string
	intArr1 []int32
	intArr2 []int32
}

func (r *SimpleVo) equal(other SimpleVo) bool {
	return r.code == other.code &&
		r.detail == other.detail &&
		reflect.DeepEqual(r.strArr, other.strArr) &&
		reflect.DeepEqual(r.intArr1, other.intArr1) &&
		reflect.DeepEqual(r.intArr2, other.intArr2)
}

func (r *SimpleVo) Encode(e *pack.Encoder) {
	e.PutInt32(0, r.code)
	e.PutStringRef(1, &r.detail)
	e.PutStringArray(2, r.strArr)
	e.PutInt32Array(3, r.intArr1)
	e.PutInt32Array(4, r.intArr2)
}

func (r *SimpleVo) parseData(d *pack.Decoder) {
	r.code = d.GetInt32(0)
	r.detail = d.GetString(1)
	r.strArr = d.GetStringArray(2)
	r.intArr1 = d.GetInt32Array(3)
	r.intArr2 = d.GetInt32Array(4)
}

var decodeSimpleVo = func(decoder *pack.Decoder) interface{} {
	r := new(SimpleVo)
	r.parseData(decoder)
	return r
}

var decodeSimpleVoPtr = func(decoder *pack.Decoder) *pack.Packable {
	r := new(SimpleVo)
	r.parseData(decoder)
	return (*pack.Packable)(unsafe.Pointer(r))
}

func testSimpleVo() {
	var r SimpleVo
	r.code = FAILED_1
	r.detail = "success"
	r.strArr = []string{"abc", "pack"}
	r.intArr1 = []int32{100, 500}
	r.intArr2 = []int32{1, 2, 3, 4, 5, 6, 7, 8}

	bytes, err := pack.Marshal(&r)
	if err != nil {
		log.Printf("Marshal SimpleVo failed: %v\n", err)
		return
	}

	p, err := pack.Unmarshal(bytes, decodeSimpleVo)
	if err != nil {
		fmt.Printf("Unmarshal SimpleVo failed, %v\n", err)
		return
	}
	r1 := p.(*SimpleVo)
	fmt.Printf("\nr1: %+v\n", *r1)

	var r2 SimpleVo
	err = pack.Parse(bytes, r2.parseData)
	if err != nil {
		fmt.Printf("Parse SimpleVo failed, %v\n", err)
		return
	}
	fmt.Printf("r2: %+v\nr1==r2:%t\n\n", r2, r1.equal(r2))

}

func testEnumArray() {
	maskArray := []int32{1, 3, 0xf, 0xff}
	for _, mask := range maskArray {
		for n := 31; n < 50; n += 3 {
			a := make([]int32, n)
			for i := 0; i < n; i++ {
				a[i] = mask & rand.Int31()
			}
			encoder := pack.NewEncoder()
			encoder.PutEnumArray(0, a)
			result := encoder.GetBytes()

			decoder := pack.NewDecoder(result)
			b := decoder.GetEnumArray(0)
			decoder.Recycle()
			if !reflect.DeepEqual(a, b) {
				fmt.Printf("testEnumArray failed at n=%d \n", n)
				return
			}
		}
	}
	fmt.Printf("testEnumArray pass\n")
}

func testNumberArray() {
	var equal = true
	for n := int32(31); n < 200; n += 30 {
		equal = equal && testCompactInt32Array(n)
		equal = equal && testCompactInt64Array(n)
		equal = equal && testCompactFloat64Array(n)
		if !equal {
			break
		}
	}
	if equal {
		fmt.Printf("testNumberArray pass\n")
	} else {
		fmt.Printf("testNumberArray failed\n")
	}
}

func testCompactInt32Array(n int32) bool {
	a := make([]int32, n)
	for i := int32(0); i < n; i++ {
		x := rand.Int31()
		if ((x >> 16) & 3) == 1 {
			x = 0
		}
		a[i] = x
	}
	encoder := pack.NewEncoder()
	encoder.PutCompactInt32Array(0, a)
	result := encoder.GetBytes()

	decoder := pack.NewDecoder(result)
	b := decoder.GetCompactInt32Array(0)
	decoder.Recycle()
	if !reflect.DeepEqual(a, b) {
		fmt.Printf("testCompactIntArray failed at n=%d \n", n)
		return false
	}
	return true
}

func testCompactInt64Array(n int32) bool {
	a := make([]int64, n)
	for i := int32(0); i < n; i++ {
		x := rand.Int63()
		if ((x >> 16) & 3) == 1 {
			x = 0
		}
		a[i] = x
	}
	encoder := pack.NewEncoder()
	encoder.PutCompactInt64Array(0, a)
	result := encoder.GetBytes()

	decoder := pack.NewDecoder(result)
	b := decoder.GetCompactInt64Array(0)
	decoder.Recycle()
	if !reflect.DeepEqual(a, b) {
		fmt.Printf("testCompactInt64Array failed at n=%d \n", n)
		return false
	}
	return true
}

func testCompactFloat64Array(n int32) bool {
	a := make([]float64, n)
	for i := int32(0); i < n; i++ {
		x := rand.Float64()
		a[i] = x
	}
	encoder := pack.NewEncoder()
	encoder.PutCompactFloat64Array(0, a)
	result := encoder.GetBytes()

	decoder := pack.NewDecoder(result)
	b := decoder.GetCompactFloat64Array(0)
	decoder.Recycle()
	if !reflect.DeepEqual(a, b) {
		fmt.Printf("testCompactFloat64Array failed at n=%d \n", n)
		return false
	}
	return true
}

func testMap() {
	a := make(map[string]string)
	a["k1"] = "v1"
	a["k2"] = "v2"
	a["k3"] = ""

	var simpleVo SimpleVo
	simpleVo.code = FAILED_1
	simpleVo.detail = "success"
	simpleVo.strArr = []string{"ok", "pack"}
	simpleVo.intArr1 = []int32{100, 500}
	simpleVo.intArr2 = []int32{1, 2, 3, 4, 5}

	b := make(map[string]pack.Packable)
	b["simple_vo"] = &simpleVo

	e := pack.NewEncoder()
	e.PutStr2Str(0, a)
	e.PutStr2Pack(1, b)
	result := e.GetBytes()

	d := pack.NewDecoder(result)
	a1 := d.GetStr2Str(0)
	b1 := d.GetStr2Pack(1, decodeSimpleVoPtr)
	d.Recycle()

	b2 := make(map[string]SimpleVo)

	for k,v := range b1{
		b2[k] = *(*SimpleVo)(unsafe.Pointer(v))
	}

	e1 := reflect.DeepEqual(a, a1)

	fmt.Printf("testMap eqaul:%t\n", e1)
	fmt.Printf("a1:%v\nb2:%v\n", a1, b2)
}


type Rectangle struct {
	x int32
	y int32
	width int32
	height int32
}

func (r *Rectangle) equal(other Rectangle) bool {
	return r.x == other.x &&
		r.y == other.y &&
		r.width == other.width &&
		r.height == other.height
}

type Info struct {
	id int64
	name string
	rect Rectangle
}

func (r *Info) equal(other Info) bool {
	return r.id == other.id &&
		r.name == other.name &&
		r.rect.equal(other.rect)
}

func (r *Info) Encode(e *pack.Encoder) {
	e.PutInt64(0, r.id)
	e.PutString(1, r.name)
	buf := e.PutCustom(2, 16)
	rt := r.rect
	buf.WriteInt32(rt.x)
	buf.WriteInt32(rt.y)
	buf.WriteInt32(rt.width)
	buf.WriteInt32(rt.height)
}

var decodeInfo = func(d *pack.Decoder) interface{} {
	r := new(Info)
	r.id = d.GetInt64(0)
	r.name = d.GetString(1)
	buf := d.GetCustom(2)
	if buf != nil {
		rt := new(Rectangle)
		rt.x = buf.ReadInt32()
		rt.y = buf.ReadInt32()
		rt.width = buf.ReadInt32()
		rt.height = buf.ReadInt32()
		r.rect = *rt
	}
	return r
}

func testCustomEncode() {
	var rt Rectangle
	rt.x = 100
	rt.y = 200
	rt.width = 300
	rt.height = 400
	var info  Info
	info.id = 1234
	info.name = "rect_1"
	info.rect = rt
	bytes, err := pack.Marshal(&info)
	if err != nil {
		log.Printf("Marshal SimpleVo failed: %v\n", err)
		return
	}

	r1, err := pack.Unmarshal(bytes, decodeInfo)
	if err != nil {
		fmt.Printf("Unmarshal SimpleVo failed, %v\n", err)
		return
	}

	dInfo := r1.(*Info)

	fmt.Printf("testCustomEncode: %t", info.equal(*dInfo))
}

