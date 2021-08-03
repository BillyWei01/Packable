using System;


namespace pack.packable
{
    public class PackBuffer
    {
        internal byte[] hb;
        internal int position;
        internal int limit;

        public PackBuffer(byte[] array, int offset, int length)
        {
            hb = array;
            position = offset;
            limit = offset + length;
        }

        public PackBuffer(byte[] array) : this(array, 0, array.Length)
        {
        }

        internal bool HasRemaining()
        {
            return position < limit;
        }

        internal void CheckBound(int offset, int len)
        {
            if (offset + len > limit)
            {
                throw new IndexOutOfRangeException("buffer out of bound");
            }
        }

        public void WriteByte(byte x)
        {
            hb[position++] = x;
        }

        public byte ReadByte()
        {
            return hb[position++];
        }

        public static int GetVarint32Size(int value)
        {
            uint x = (uint)value;
            if (x <= 0x7f)
            {
                return 1;
            }
            else if (x <= 0x3fff)
            {
                return 2;
            }
            else if (x <= 0x1fffff)
            {
                return 3;
            }
            else if (x <= 0xfffffff)
            {
                return 4;
            }
            return 5;
        }

        public void WriteVarint32(int value)
        {
            uint x = (uint)value;
            while (x > 0x7f)
            {
                hb[position++] = (byte)((x & 0x7f) | 0x80);
                x >>= 7;
            }
            hb[position++] = (byte)x;
        }

        public int ReadVarint32()
        {
            uint x = hb[position++];
            if (x <= 0x7f) return (int)x;
            x = x & 0x7f | ((uint)hb[position++] << 7);
            if (x <= 0x3fff) return (int)x;
            x = (x & 0x3fff) | ((uint)hb[position++] << 14);
            if (x <= 0x1fffff) return (int)x;
            x = (x & 0x1fffff) | ((uint)hb[position++] << 21);
            if (x <= 0xfffffff) return (int)x;
            x = (x & 0xfffffff) | ((uint)hb[position++] << 28);
            return (int)x;
        }

        public void WriteBytes(byte[] bytes)
        {
            int count = bytes.Length;
            Buffer.BlockCopy(bytes, 0, hb, position, count);
            position += count;
        }

        public void ReadBytes(byte[] bytes)
        {
            int count = bytes.Length;
            Buffer.BlockCopy(hb, position, bytes, 0, count);
            position += count;
        }

        public void WriteShort(int i, short x)
        {
            hb[i++] = (byte)x;
            hb[i] = (byte)(x >> 8);
        }

        public void WriteShort(short x)
        {
            hb[position++] = (byte)x;
            hb[position++] = (byte)(x >> 8);
        }

        public short ReadShort()
        {
            return (short)(hb[position++] | (hb[position++] << 8));
        }

        public void WriteInt(int x)
        {
            hb[position++] = (byte)x;
            hb[position++] = (byte)(x >> 8);
            hb[position++] = (byte)(x >> 16);
            hb[position++] = (byte)(x >> 24);
        }

        public void WriteInt(int i, int x)
        {
            hb[i++] = (byte)x;
            hb[i++] = (byte)(x >> 8);
            hb[i++] = (byte)(x >> 16);
            hb[i] = (byte)(x >> 24);
        }

        public int ReadInt()
        {
            return hb[position++] |
                (hb[position++] << 8) |
                (hb[position++] << 16) |
                (hb[position++] << 24);
        }

        public void WriteLong(long x)
        {
            if (BitConverter.IsLittleEndian)
            {
                unsafe
                {
                    fixed (byte* pB = &hb[position])
                    {
                        position += 8;
                        *(long*)pB = x;
                    }
                }
            }
            else
            {
                hb[position++] = (byte)x;
                hb[position++] = (byte)(x >> 8);
                hb[position++] = (byte)(x >> 16);
                hb[position++] = (byte)(x >> 24);
                hb[position++] = (byte)(x >> 32);
                hb[position++] = (byte)(x >> 40);
                hb[position++] = (byte)(x >> 48);
                hb[position++] = (byte)(x >> 56);
            }
        }

        public long ReadLong(int i)
        {
            if (BitConverter.IsLittleEndian)
            {
                if (i + 8 > limit) throw new IndexOutOfRangeException("reading out of range");
                unsafe
                {
                    fixed (byte* pB = &hb[i])
                    {
                        return *(long*)pB;
                    }
                }
            }
            else
            {
                return hb[i++] |
                (((long)hb[i++]) << 8) |
                (((long)hb[i++]) << 16) |
                (((long)hb[i++]) << 24) |
                (((long)hb[i++]) << 32) |
                (((long)hb[i++]) << 40) |
                (((long)hb[i++]) << 48) |
                (((long)hb[i]) << 56);
            }

        }

        public long ReadLong()
        {
            long value = ReadLong(position);
            position += 8;
            return value;
        }

        public void WriteFloat(float x)
        {
            unsafe
            {
                WriteInt(*(int*)&x);
            }
        }

        public float ReadFloat()
        {
            int x = ReadInt();
            unsafe
            {
                return *(float*)&x;
            }
        }

        public void WriteDouble(double x)
        {
            unsafe
            {
                WriteLong(*(long*)&x);
            }
        }

        public double ReadDouble()
        {
            long x = ReadLong();
            unsafe
            {
                return *(double*)&x;
            }
        }
    }
}
