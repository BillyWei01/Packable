package main

import (
	"../pack"
	"bytes"
	"fmt"
	"io/ioutil"
	"log"
	"os"
	"time"
	"unsafe"
)

const (
	SUCCESS  int32 = 0
	FAILED_1 int32 = 1
	FAILED_2 int32 = 2
	FAILED_3 int32 = 3
)

type Category struct {
	name         string
	level        int32
	i_column     int64
	d_column     float64
	des          *string
	sub_category []*Category
}

func (c *Category) Encode(e *pack.Encoder) {
	e.PutStringRef(0, &c.name)
	e.PutInt32(1, c.level)
	e.PutInt64(2, c.i_column)
	e.PutCFloat64(3, c.d_column)
	e.PutStringRef(4, c.des)
	if c.sub_category != nil {
		p := make([]pack.Packable, len(c.sub_category))
		for i, v := range c.sub_category {
			p[i] = v
		}
		e.PutPackableArray(5, p)
	}
}

func (c *Category) parseBasicData(decoder *pack.Decoder) {
	c.name = decoder.GetString(0)
	c.level = decoder.GetInt32(1)
	c.i_column = decoder.GetInt64(2)
	c.d_column = decoder.GetCFloat64(3)
	c.des = decoder.GetStringRef(4)
}

var decodeCategory = func(decoder *pack.Decoder) interface{} {
	c := new(Category)
	c.parseBasicData(decoder)
	c.sub_category = *(*[]*Category)(unsafe.Pointer(
		decoder.GetPackableArray(5, decodeSubCategory, newCategoryArray)))
	return c
}

var decodeSubCategory = func(decoder *pack.Decoder) *pack.Packable {
	c := new(Category)
	c.parseBasicData(decoder)
	if decoder.Contains(5) {
		c.sub_category = make([]*Category, 0)
	} else {
		c.sub_category = nil
	}
	return (*pack.Packable)(unsafe.Pointer(c))
}

var newCategoryArray = func(size int) *[]*pack.Packable {
	t := make([]*Category, size)
	return (*[]*pack.Packable)(unsafe.Pointer(&t))
}

type Data struct {
	d_bool   bool
	d_float  float32
	d_double float64

	string_1 string

	int_1 int32
	int_2 int32
	int_3 int32
	int_4 int32
	int_5 int32

	long_1 int64
	long_2 int64
	long_3 int64
	long_4 int64
	long_5 int64

	d_category *Category

	bool_array   []bool
	int_array    []int32
	long_array   []int64
	float_array  []float32
	double_array []float64
	string_array []*string
}

func (d *Data) Encode(encoder *pack.Encoder) {
	encoder.PutBool(0, d.d_bool)
	encoder.PutFloat32(1, d.d_float)
	encoder.PutFloat64(2, d.d_double)
	encoder.PutStringRef(3, &d.string_1)
	encoder.PutInt32(4, d.int_1)
	encoder.PutInt32(5, d.int_2)
	encoder.PutInt32(6, d.int_3)
	encoder.PutSInt32(7, d.int_4)
	encoder.PutInt32(8, d.int_5)
	encoder.PutInt64(9, d.long_1)
	encoder.PutInt64(10, d.long_2)
	encoder.PutInt64(11, d.long_3)
	encoder.PutSInt64(12, d.long_4)
	encoder.PutInt64(13, d.long_5)
	encoder.PutPackable(14, d.d_category)
	encoder.PutBoolArray(15, d.bool_array)
	encoder.PutInt32Array(16, d.int_array)
	encoder.PutInt64Array(17, d.long_array)
	encoder.PutFloat32Array(18, d.float_array)
	encoder.PutFloat64Array(19, d.double_array)
	encoder.PutStringRefArray(20, d.string_array)
}

var decodeData = func(decoder *pack.Decoder) *pack.Packable {
	d := new(Data)
	d.d_bool = decoder.GetBool(0)
	d.d_float = decoder.GetFloat32(1)
	d.d_double = decoder.GetFloat64(2)
	d.string_1 = decoder.GetString(3)
	d.int_1 = decoder.GetInt32(4)
	d.int_2 = decoder.GetInt32(5)
	d.int_3 = decoder.GetInt32(6)
	d.int_4 = decoder.GetSInt32(7)
	d.int_5 = decoder.GetInt32(8)
	d.long_1 = decoder.GetInt64(9)
	d.long_2 = decoder.GetInt64(10)
	d.long_3 = decoder.GetInt64(11)
	d.long_4 = decoder.GetSInt64(12)
	d.long_5 = decoder.GetInt64(13)
	d.d_category = decoder.GetPackable(14, decodeCategory).(*Category)
	d.bool_array = decoder.GetBoolArray(15)
	d.int_array = decoder.GetInt32Array(16)
	d.long_array = decoder.GetInt64Array(17)
	d.float_array = decoder.GetFloat32Array(18)
	d.double_array = decoder.GetFloat64Array(19)
	d.string_array = decoder.GetStringRefArray(20)
	return (*pack.Packable)(unsafe.Pointer(d))
}

var newDataArray = func(size int) *[]*pack.Packable {
	t := make([]*Data, size)
	return (*[]*pack.Packable)(unsafe.Pointer(&t))
}

type Response struct {
	code   int32
	detail string
	date   []*Data
}

func (r *Response) Encode(encoder *pack.Encoder) {
	encoder.PutInt32(0, r.code)
	encoder.PutStringRef(1, &r.detail)
	if r.date != nil {
		p := make([]pack.Packable, len(r.date))
		for i, v := range r.date {
			p[i] = v
		}
		encoder.PutPackableArray(2, p)
	}
}

var decodeResponse = func(decoder *pack.Decoder) interface{} {
	r := new(Response)
	r.code = decoder.GetInt32(0)
	r.detail = decoder.GetString(1)
	r.date = *(*[]*Data)(unsafe.Pointer(decoder.GetPackableArray(2, decodeData, newDataArray)))
	return r
}

// -------- test code --------

func readFileToBytes(filePth string) ([]byte, error) {
	f, err := os.Open(filePth)
	if err != nil {
		return nil, err
	}
	return ioutil.ReadAll(f)
}

func bytesEqual(b1 []byte, b2 []byte) bool {
	m := len(b1)
	n := len(b2)
	if m != n {
		fmt.Printf("len not equal m:%d n:%d\n", m, n)
		return false
	}
	for i := 0; i < m; i++ {
		if b1[i] != b2[i] {
			fmt.Printf("pos:%d\n", i)
			return false
		}
	}
	return true
}

func testPackVo() {
	// TODO replace with your path here
	path := "/Users/john/github/Packable/test_data/packable_2000.data"
	x, err := readFileToBytes(path)
	if err != nil {
		fmt.Println(err)
	} else {
		var dt time.Duration = 0
		var et time.Duration = 0
		n := 1
		for i := 0; i < n; i++ {
			t := time.Now()
			p, err := pack.Unmarshal(x, decodeResponse)
			if err != nil {
				log.Printf("Unmarshal failed: %v\n", err)
				break
			}
			r := p.(*Response)
			dt += time.Since(t)
			t = time.Now()
			y, err := pack.Marshal(r)
			if err != nil {
				log.Printf("Marshal failed: %v\n", err)
				break
			}
			et += time.Since(t)

			if !bytesEqual(x, y) {
				break
			}
			if i == (n - 1) {
				et /= time.Duration(n)
				dt /= time.Duration(n)
				fmt.Printf("\nencode:%v\ndecode:%v\n", et, dt)
				fmt.Printf("x len:%d\ny len:%d\n", len(x), len(y))
				fmt.Printf("equal: %t\n", bytes.Equal(x, y))
			}
		}
	}
}
