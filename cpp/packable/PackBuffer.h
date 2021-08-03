
#ifndef PACK_PACKBUFFER_H
#define PACK_PACKBUFFER_H

#include <stdint.h>
#include <string>

#define throw_out_of_bound  throw "buffer out of bound";

static int check_end_number = 1;
static bool is_little_end = (*(char *) &check_end_number) == 1;

class PackBuffer {
public:
    char *hb;
    int position;
    int limit;

    PackBuffer(char *array, int offset, int length) {
        hb = array;
        position = offset;
        limit = offset + length;
    }

    bool hasRemaining() {
        return position < limit;
    }


    inline void checkBound(int offset, int len) {
        if (offset + len > limit) {
            throw_out_of_bound
        }
    }

    void writeByte(char x) {
        hb[position++] = x;
    }

    char readByte(int i) {
        if (i >= limit) {
            throw_out_of_bound
        }
        return hb[i];
    }

    char readByte() {
        if (position >= limit) {
            throw_out_of_bound
        }
        return hb[position++];
    }

    void writeShort(int i, short x) {
        hb[i] = x;
        hb[i + 1] = x >> 8;
    }

    void writeShort(short x) {
        hb[position++] = x;
        hb[position++] = x >> 8;
    }

    short readShort() {
        if (position + 2 > limit) {
            throw_out_of_bound
        }
        short t;
        if (is_little_end) {
            t = *((short *) (hb + position));
        } else {
            uint8_t *ch = (uint8_t *) hb;
            t = (short) (((ch[position])) |
                            ((ch[position+1]) << 8));
        }
        position += 2;
        return t;
    }

    void writeInt(int x) {
        writeInt(position, x);
        position += 4;
    }

    void writeInt(int i, int x) {
        if (is_little_end) {
            *((int *) (hb + i)) = x;
        } else {
            hb[i] = x;
            hb[i + 1] = x >> 8;
            hb[i + 2] = x >> 16;
            hb[i + 3] = x >> 24;
        }
    }

    int readInt() {
        int i = position;
        if (i + 4 > limit) {
            throw_out_of_bound
        }
        int t;
        if (is_little_end) {
            t = *((int *) (hb + i));
        } else {
            uint8_t *ch = (uint8_t *) hb;
            t = (((int32_t) ch[i]) |
                 ((int32_t) ch[i + 1] << 8) |
                 ((int32_t) ch[i + 2] << 16) |
                 ((int32_t) ch[i + 3] << 24));
        }
        position += 4;
        return t;
    }

    static int getVarint32Size(uint32_t x) {
        if (x <= 0x7f) {
            return 1;
        } else if (x <= 0x3fff) {
            return 2;
        } else if (x <= 0x1fffff) {
            return 3;
        } else if (x <= 0xfffffff) {
            return 4;
        }
        return 5;
    }

    void writeVarintNegative1() {
        *((int *) (hb + position)) = 0xffffffff;
        hb[position+4] = 0xf;
        position += 5;
    }

    void writeVarint32(uint32_t x) {
        while (x > 0x7f) {
            hb[position++] = (char) ((x & 0x7f) | 0x80);
            x >>= 7;
        }
        hb[position++] = (char) x;
    }

    int readVarint32() {
        u_int8_t *p = (u_int8_t *) hb;
        uint32_t x = p[position++];
        if (x <= 0x7f) goto end;
        x = (x & 0x7f) | (p[position++] << 7);
        if (x <= 0x3fff) goto end;
        x = (x & 0x3fff) | (p[position++] << 14);
        if (x <= 0x1fffff) goto end;
        x = (x & 0x1fffff) | (p[position++] << 21);
        if (x <= 0xfffffff) goto end;
        x = (x & 0xfffffff) | (p[position++] << 28);
        end:
        if (position > limit) throw_out_of_bound
        return x;
    }

    void writeInt64(int64_t x) {
        if (is_little_end) {
            *((int64_t *) (hb + position)) = x;
        } else {
            int i = position;
            hb[i] = x;
            hb[i + 1] = x >> 8;
            hb[i + 2] = x >> 16;
            hb[i + 3] = x >> 24;
            hb[i + 4] = x >> 32;
            hb[i + 5] = x >> 40;
            hb[i + 6] = x >> 48;
            hb[i + 7] = x >> 56;
        }
        position += 8;
    }

    int64_t readInt64(int i) {
        if (i + 8 > limit) {
            throw_out_of_bound
        }
        if (is_little_end) {
            return *((int64_t *) (hb + i));
        } else {
            uint8_t *ch = (uint8_t *) hb;
            return (((int64_t) ch[i]) |
                    ((int64_t) ch[i + 1] << 8) |
                    ((int64_t) ch[i + 2] << 16) |
                    ((int64_t) ch[i + 3] << 24) |
                    ((int64_t) ch[i + 4] << 32) |
                    ((int64_t) ch[i + 5] << 40) |
                    ((int64_t) ch[i + 6] << 48) |
                    ((int64_t) ch[i + 7] << 56));
        }
    }

    int64_t readInt64() {
        int64_t t = readInt64(position);
        position += 8;
        return t;
    }

    void writeFloat(float x) {
        if (is_little_end) {
            *((float *) (hb + position)) = x;
            position += 4;
        } else {
            writeInt(*((int *) (&x)));
        }
    }

    float readFloat() {
        int t = readInt();
        return *((float *) (&t));
    }

    void writeDouble(double x) {
        if (is_little_end) {
            *((double *) (hb + position)) = x;
            position += 8;
        } else {
            writeInt64(*((int64_t *) (&x)));
        }
    }

    double readDouble() {
        int64_t t = readInt64();
        return *((double *) (&t));
    }

    void writeBytes(char *src, int n) {
        if (n > 0) {
            memcpy(hb + position, src, n);
            position += n;
        }
    }

    void readBytes(char *bytes, int n) {
        memcpy(bytes, hb + position, n);
        position += n;
    }
};

#endif
