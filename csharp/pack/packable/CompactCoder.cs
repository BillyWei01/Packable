using System;
using System.Runtime.InteropServices;

namespace pack.packable
{
    class CompactCoder
    {
        enum NumberType
        {
            INT ,
            LONG ,
            DOUBLE 
        }

        [StructLayout(LayoutKind.Explicit)]
        struct ArrayWrapper
        {
            [FieldOffset(0)]
            internal int[] intArray;
            [FieldOffset(0)]
            internal long[] longArray;
            [FieldOffset(0)]
            internal double[] doubleArray;
        }

        static void AllocateArray(ref ArrayWrapper wrapper, NumberType type, int n)
        {
            if (type == NumberType.INT)
            {
                wrapper.intArray = new int[n];
            }
            else if (type == NumberType.LONG)
            {
                wrapper.longArray = new long[n];
            }
            else
            {
                wrapper.doubleArray = new double[n];
            }
        }


        internal static void PutIntArray(PackEncoder encoder, int index, int[] value)
        {
            if (value != null)
            {
                ArrayWrapper wrapper = new ArrayWrapper();
                wrapper.intArray = value;
                PutNumberArray(encoder, index, ref wrapper, NumberType.INT, value.Length);
            }
        }

        internal static int[] GetIntArray(PackDecoder decoder, int index)
        {
            ArrayWrapper wrapper = new ArrayWrapper();
            GetNumberArray(decoder, index, ref wrapper, NumberType.INT);
            return wrapper.intArray;
        }

        internal static void PutLongArray(PackEncoder encoder, int index, long[] value)
        {
            if (value != null)
            {
                ArrayWrapper wrapper = new ArrayWrapper();
                wrapper.longArray = value;
                PutNumberArray(encoder, index, ref wrapper, NumberType.LONG, value.Length);
            }
        }

        internal static long[] GetLongArray(PackDecoder decoder, int index)
        {
            ArrayWrapper wrapper = new ArrayWrapper();
            GetNumberArray(decoder, index, ref wrapper, NumberType.LONG);
            return wrapper.longArray;
        }

        internal static void putDoubleArray(PackEncoder encoder, int index, double[] value)
        {
            if (value != null)
            {
                ArrayWrapper wrapper = new ArrayWrapper();
                wrapper.doubleArray = value;
                PutNumberArray(encoder, index, ref wrapper, NumberType.DOUBLE, value.Length);
            }
        }

        internal static double[] getDoubleArray(PackDecoder decoder, int index)
        {
            ArrayWrapper wrapper = new ArrayWrapper();
            GetNumberArray(decoder, index, ref wrapper, NumberType.DOUBLE);
            return wrapper.doubleArray;
        }

        private static void PutNumberArray(PackEncoder encoder, int index, ref ArrayWrapper wrapper, NumberType type, int n)
        {
            if (n < 0)
            {
                return;
            }
            if (n == 0)
            {
                encoder.WrapTagAndLength(index, 0);
                return;
            }
            encoder.CheckIndex(index);

            // calculate spaces
            int sizeOfN = PackBuffer.GetVarint32Size(n);
            int flagByteCount = GetByteCount(n << 1);

            // wrap tag and reserve space for len
            int shift = type == NumberType.INT ? 2 : 3;
            int maxSize = sizeOfN + flagByteCount + (n << shift);
            encoder.CheckCapacity(6 + maxSize);
            PackBuffer buffer = encoder.GetBuffer();
            int pTag = buffer.position;
            encoder.PutIndex(index);
            int pLen = buffer.position;
            int sizeOfLen;
            if (maxSize <= 0xff)
            {
                buffer.hb[pTag] |= TagFormat.TYPE_VAR_8;
                sizeOfLen = 1;
            }
            else if (maxSize <= 0xffff)
            {
                buffer.hb[pTag] |= TagFormat.TYPE_VAR_16;
                sizeOfLen = 2;
            }
            else
            {
                buffer.hb[pTag] |= TagFormat.TYPE_VAR_32;
                sizeOfLen = 4;
            }
            buffer.position += sizeOfLen;

            buffer.WriteVarint32(n);
            int pFlag = buffer.position;
            // move position to values
            buffer.position += flagByteCount;

            WrapArray(buffer, n, pFlag, ref wrapper, type);

            // wrap len
            // maxSize must be large than len, so it's safe to put len in position pLen
            int len = buffer.position - (pLen + sizeOfLen);
            if (sizeOfLen == 1)
            {
                buffer.hb[pLen] = (byte)len;
            }
            else if (sizeOfLen == 2)
            {
                buffer.WriteShort(pLen, (short)len);
            }
            else
            {
                buffer.WriteInt(pLen, len);
            }
        }

        private static void WrapArray(PackBuffer buffer, int n, int pFlag, ref ArrayWrapper wrapper, NumberType type)
        {
            int i = 0;
            while (i < n)
            {
                int end = Math.Min(i + 4, n);
                int flags = 0;
                if (type == NumberType.DOUBLE)
                {
                    for (int j = i; j < end; j++)
                    {
                        double d = wrapper.doubleArray[j];
                        if (d == 0D)
                        {
                            continue;
                        }
                        int shift = ((j & 0x3) << 1);
                        long e;
                        unsafe
                        {
                            e =   *(long*)&d;
                        }
                        if ((e << 16) == 0L)
                        {
                            buffer.WriteShort((short)(e >> 48));
                            flags |= 1 << shift;
                        }
                        else if ((e << 32) == 0L)
                        {
                            buffer.WriteInt((int)(e >> 32));
                            flags |= 2 << shift;
                        }
                        else
                        {
                            buffer.WriteLong(e);
                            flags |= 3 << shift;
                        }
                    }
                }
                else
                {
                    for (int j = i; j < end; j++)
                    {
                        long e = type == NumberType.INT ? wrapper.intArray[j] : wrapper.longArray[j];
                        if (e == 0L)
                        {
                            continue;
                        }
                        int shift = ((j & 0x3) << 1);
                        if ((e >> 8) == 0L)
                        {
                            buffer.WriteByte((byte)e);
                            flags |= 1 << shift;
                        }
                        else if ((e >> 16) == 0L)
                        {
                            buffer.WriteShort((short)e);
                            flags |= 2 << shift;
                        }
                        else
                        {
                            if (type == NumberType.INT)
                            {
                                buffer.WriteInt((int)e);
                            }
                            else
                            {
                                buffer.WriteLong(e);
                            }
                            flags |= 3 << shift;
                        }
                    }
                }
                buffer.hb[pFlag + (i >> 2)] = (byte)flags;
                i = end;
            }
        }

        private static void GetNumberArray(PackDecoder decoder, int index, ref ArrayWrapper wrapper, NumberType type)
        {
            ulong info = decoder.GetInfo(index);
            if (info == PackDecoder.NULL_FLAG)
            {
                return;
            }
            int len = (int)(info & PackDecoder.INT_MASK);
            if (len == 0)
            {
                AllocateArray(ref wrapper, type, 0);
                return;
            }

            PackBuffer buffer = decoder.GetBuffer();
            buffer.position = (int)(info >> 32);
            int n = buffer.ReadVarint32();
            if (n < 0)
            {
                throw new InvalidOperationException("invalid array size");
            }
            int pFlag = buffer.position;
            int byteCount = GetByteCount(n << 1);
            buffer.CheckBound(buffer.position, byteCount);
            buffer.position += byteCount;

            AllocateArray(ref wrapper, type, n);
            TakeArray(buffer, n, pFlag, wrapper, type);
        }

        private static void TakeArray(PackBuffer buffer, int n, int pFlag, ArrayWrapper wrapper, NumberType type)
        {
            for (int i = 0; i < n; i += 4)
            {
                int b = buffer.hb[pFlag + (i >> 2)] & 0xFF;
                int j = i;
                while (b != 0)
                {
                    int flag = b & 0x3;
                    if (flag == 0)
                    {
                        // In C#, elements of new array are initialized to zeros,
                        // so here we just skip to next
                        j++;
                    }
                    else
                    {
                        if (type == NumberType.DOUBLE)
                        {
                            long x;
                            if (flag == 1)
                            {
                                x = ((long)(ushort)buffer.ReadShort()) << 48;
                            }
                            else if (flag == 2)
                            {
                                x = ((long)(uint)buffer.ReadInt()) << 32;
                            }
                            else
                            {
                                x = buffer.ReadLong();
                            }
                            double y;
                            unsafe
                            {
                                y = *(double*)&x;
                            }
                            wrapper.doubleArray[j++] = y;
                        }
                        else
                        {
                            if (flag < 3)
                            {
                                long x = flag == 1 ? buffer.ReadByte() : buffer.ReadShort() & 0xffff;
                                if (type == NumberType.INT)
                                {
                                    wrapper.intArray[j++] = (int)x;
                                }
                                else
                                {
                                    wrapper.longArray[j++] = x;
                                }
                            }
                            else
                            {
                                if (type == NumberType.INT)
                                {
                                    wrapper.intArray[j++] = buffer.ReadInt();
                                }
                                else
                                {
                                    wrapper.longArray[j++] = buffer.ReadLong();
                                }
                            }
                        }
                    }
                    b >>= 2;
                }
            }
        }

        private static int GetByteCount(int totalBits)
        {
            int byteCount = (totalBits >> 3);
            if ((totalBits & 0x7) != 0)
            {
                byteCount++;
            }
            return byteCount;
        }


        /*
        * Put an enum array (map to int value) to PackEncoder's buffer.<p/>
        * <p>
        * The function will compress the enum values to byte buffer.
        * For example, if there is enum like [S1, S2, S3, S4], map to [0,1,2,3],
        * then every enum value takes 2 bits, and one byte can store 4 value.<p/>
        * <p>
        * For convenient to align values to byte,<br/>
        * we only map value to bits unit of [1, 2, 4, 8],<br/>
        * - if enum has 2 cases,  map value to 1 bit <br/>
        * - else if enum less than 4 cases, map value to 2 bits  <br/>
        * - else if enum less than 16 cases, map value to 4 bits <br/>
        * - else if enum less than 256 cases, map value to 8 bits <br/>
        * - other wise , throws IllegalArgumentException <p/>
        *
        * @param value array of enum's int value, <br/>
        *              you can use ordinal(), <br/>
        *              or map them to int by yourself (more safety). <br/>
        *              values must less than 256  <p/>
        */
        internal static void PutEnumArray(PackEncoder encoder, int index, int[] value)
        {
            if (value == null)
            {
                return;
            }
            int n = value.Length;
            if (n == 0)
            {
                encoder.WrapTagAndLength(index, 0);
                return;
            }

            int sum = 0;
            foreach (int e in value)
            {
                sum |= e;
            }
            int bitShift;
            if ((sum >> 1) == 0)
            {
                bitShift = 0;
            }
            else if ((sum >> 2) == 0)
            {
                bitShift = 1;
            }
            else if ((sum >> 4) == 0)
            {
                bitShift = 2;
            }
            else if ((sum >> 8) == 0)
            {
                bitShift = 3;
            }
            else
            {
                throw new ArgumentOutOfRangeException("only accept values less than 255");
            }

            PackBuffer buffer = encoder.GetBuffer();
            int byteCount;
            if (bitShift == 3)
            {
                byteCount = n + 1;
                encoder.WrapTagAndLength(index, byteCount);
                int pos = buffer.position;
                buffer.hb[pos++] = (byte)(bitShift << 3);
                for (int i = 0; i < n; i++)
                {
                    buffer.hb[pos + i] = (byte)value[i];
                }
            }
            else
            {
                int totalBits = n << bitShift;
                int remain = totalBits & 0x7;
                byteCount = (totalBits >> 3) + (remain == 0 ? 1 : 2);
                encoder.WrapTagAndLength(index, byteCount);
                int pos = buffer.position;
                buffer.hb[pos++] = (byte)((bitShift << 3) | remain);

                // bitShift=0, indexShift=3, indexMask=0x7
                // bitShift=1, indexShift=2, indexMask=0x3
                // bitShift=2, indexShift=1, indexMask=0x1
                int indexShift = 3 - bitShift;
                int indexMask = ~((~0) << indexShift);
                int step = 1 << indexShift;
                int i = 0;
                while (i < n)
                {
                    int end = Math.Min(i + step, n);
                    int b = 0;
                    for (int j = i; j < end; j++)
                    {
                        b |= value[j] << ((j & indexMask) << bitShift);
                    }
                    buffer.hb[pos + (i >> indexShift)] = (byte)b;
                    i = end;
                }
            }
            buffer.position += byteCount;
        }

        public static int[] GetEnumArray(PackDecoder decoder, int index)
        {
            ulong info = decoder.GetInfo(index);
            if (info == PackDecoder.NULL_FLAG)
            {
                return null;
            }
            int len = (int)(info & PackDecoder.INT_MASK);
            if (len == 0)
            {
                return new int[0];
            }

            PackBuffer buffer = decoder.GetBuffer();
            buffer.position = (int)(info >> 32);
            int bitInfo = buffer.ReadByte();
            if ((bitInfo >> 5) != 0)
            {
                throw new InvalidOperationException("bit info overflow");
            }
            int bitShift = bitInfo >> 3;
            int byteCount = len - 1;
            if (bitShift == 3)
            {
                int[] a = new int[byteCount];
                for (int i = 0; i < byteCount; i++)
                {
                    a[i] = buffer.ReadByte();
                }
                return a;
            }
            else
            {
                int remain = bitInfo & 0x7;
                int indexShift = 3 - bitShift;
                int n = (byteCount << indexShift);
                if (remain > 0)
                {
                    n -= ((8 - remain) >> bitShift);
                }
                int[] a = new int[n];
                int pos = buffer.position;
                int byteShirt = 1 << bitShift;
                int valueMask = ~((~0) << byteShirt);
                int step = 1 << indexShift;
                for (int i = 0; i < n; i += step)
                {
                    int b = buffer.hb[pos + (i >> indexShift)] & 0xFF;
                    int j = i;
                    while (b != 0)
                    {
                        a[j++] = b & valueMask;
                        b >>= byteShirt;
                    }
                }
                buffer.position += byteCount;
                return a;
            }
        }

        internal static void PutBooleanArray(PackEncoder encoder, int index, bool[] value)
        {
            if (value == null) return;
            int n = value.Length;
            if (n == 0)
            {
                encoder.WrapTagAndLength(index, 0);
                return;
            }

            PackBuffer buffer = encoder.GetBuffer();
            if (n <= 5)
            {
                int b = n << 5;
                for (int i = 0; i < n; i++)
                {
                    if (value[i])
                    {
                        b |= 1 << i;
                    }
                }
                encoder.WrapTagAndLength(index, 1);
                buffer.WriteByte((byte)b);
            }
            else
            {
                int remain = n & 0x7;
                int byteCount = (n >> 3) + (remain == 0 ? 1 : 2);
                encoder.WrapTagAndLength(index, byteCount);
                buffer.WriteByte((byte)remain);
                int i = 0;
                while (i < n)
                {
                    int end = Math.Min(i + 8, n);
                    int b = 0;
                    for (int j = i; j < end; j++)
                    {
                        if (value[j])
                        {
                            b |= 1 << (j & 0x7);
                        }
                    }
                    buffer.WriteByte((byte)b);
                    i = end;
                }
            }
        }

        internal static bool[] GetBooleanArray(PackDecoder decoder, int index)
        {
            ulong info = decoder.GetInfo(index);
            if (info == PackDecoder.NULL_FLAG)
            {
                return null;
            }
            int len = (int)(info & PackDecoder.INT_MASK);
            if (len == 0)
            {
                return new bool[0];
            }

            PackBuffer buffer = decoder.GetBuffer();
            buffer.position = (int)(info >> 32);
            bool[] a;
            if (len == 1)
            {
                byte b = buffer.ReadByte();
                int n = b >> 5;
                a = new bool[n];
                for (int i = 0; i < n; i++)
                {
                    a[i] = (b & 0x1) != 0;
                    b >>= 1;
                }
            }
            else
            {
                int remain = buffer.ReadByte();
                if ((remain >> 3) != 0)
                {
                    throw new SystemException("remain overflow");
                }
                int byteCount = len - 1;
                int n = (byteCount << 3) - (remain > 0 ? 8 - remain : 0);
                a = new bool[n];
                for (int i = 0; i < n; i += 8)
                {
                    uint b = buffer.ReadByte();
                    int j = i;
                    while (b != 0)
                    {
                        a[j++] = (b & 0x1) != 0;
                        b >>= 1;
                    }
                }
            }
            return a;
        }
    }

}
