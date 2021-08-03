package pack


type decoderPool struct {
	arr        [decoder_pool_capacity]*Decoder
	count      int
	rootBuffer *Buffer
}

func (p *decoderPool) getDecoder(offset int32, size int32) *Decoder {
	var d *Decoder
	if p.count > 0 {
		p.count--
		d = p.arr[p.count]
		d.buffer.position = offset
		d.buffer.limit = offset + size
	} else {
		d = new(Decoder)
		d.buffer = newDecodeBuffer(p.rootBuffer.hb, offset, size)
		d.pool = p
	}
	d.maxIndex = -1
	return d
}

func (p *decoderPool) recycleDecoder(decoder *Decoder) {
	if p.count < decoder_pool_capacity {
		p.arr[p.count] = decoder
		p.count++
	} else {
		recycleLongArray(decoder.infoArray)
		decoder.infoArray = nil
	}
}

func (p *decoderPool) releaseLongArray() {
	n := p.count
	for i := 0; i < n; i++ {
		decoder := p.arr[i]
		recycleLongArray(decoder.infoArray)
		decoder.pool = nil
	}
}
