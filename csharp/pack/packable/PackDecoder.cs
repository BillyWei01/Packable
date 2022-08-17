using System;
using System.Collections.Generic;
using System.Text;

namespace pack.packable
{
    public class PackDecoder
    {
        internal const ulong NULL_FLAG = ~0uL;//0xffffffffffffffffL;
        internal const ulong INT_MASK = 0xffffffffuL;
        private const ulong INT_64_MIN_VALUE = 0x8000000000000000uL;

        private const string INVALID_ARRAY_LENGTH = "invalid array length";

        private class Root
        {
            // local decoder pool
            private PackDecoder[] decoderArray;
            private int count = 0;

            internal readonly PackBuffer buffer;


            internal Root(PackBuffer buffer)
            {
                this.buffer = buffer;
            }

            internal PackDecoder GetDecoder(int offset, int len)
            {
                PackDecoder decoder;
                if (count > 0)
                {
                    decoder = decoderArray[--count];
                    decoder.buffer.position = offset;
                    decoder.buffer.limit = offset + len;
                    decoder.maxIndex = -1;
                }
                else
                {
                    decoder = new PackDecoder();
                    decoder.buffer = new PackBuffer(buffer.hb, offset, len);
                    decoder.root = this;
                }
                return decoder;
            }

            internal void RecycleDecoder(PackDecoder decoder)
            {
                if (count >= 24)
                {
                    return;
                }
                if (decoderArray == null)
                {
                    decoderArray = new PackDecoder[6];
                }
                else if (count >= decoderArray.Length)
                {
                    PackDecoder[] newArray = new PackDecoder[count << 1];
                    Array.Copy(decoderArray, 0, newArray, 0, count);
                    decoderArray = newArray;
                }
                decoderArray[count++] = decoder;
            }
        }

        private Root root;
        private PackBuffer buffer;
        private ulong[] infoArray;
        private int maxIndex = -1;

        public static T Unmarshal<T>(byte[] bytes, PackCreator<T> creator)
        {
            PackDecoder decoder = NewInstance(bytes, 0, bytes.Length);
            T t = creator(decoder);
            decoder.Recycle();
            return t;
        }

        public static PackDecoder NewInstance(byte[] bytes)
        {
            return NewInstance(bytes, 0, bytes.Length);
        }

        public static PackDecoder NewInstance(byte[] bytes, int offset, int len)
        {
            if (bytes == null)
            {
                throw new ArgumentNullException("bytes is null");
            }
            if (bytes.Length > PackConfig.MAX_BUFFER_SIZE)
            {
                throw new ArgumentOutOfRangeException("buffer size over limit");
            }
            if (offset + len > bytes.Length)
            {
                throw new ArgumentOutOfRangeException("out of range, " +
                        "size:" + bytes.Length + " offset:" + offset + " length:" + len);
            }

            PackDecoder decoder = new PackDecoder();
            decoder.buffer = new PackBuffer(bytes, offset, len);
            decoder.root = new Root(decoder.buffer);
            return decoder;
        }

        private PackDecoder()
        {
        }

        public PackBuffer GetBuffer()
        {
            if (buffer == null)
            {
                throw new InvalidOperationException("Decoder had been recycled");
            }
            return buffer;
        }

        private void ParseBuffer()
        {
            ulong existFlag = 0uL;
            ulong[] existFlags = null;

            if (infoArray == null)
            {
                infoArray = LongArrayPool.GetDefaultArray();
            }

            while (buffer.HasRemaining())
            {
                byte tag = buffer.ReadByte();
                int index = (tag & TagFormat.BIG_INDEX_MASK) == 0 ? tag & TagFormat.INDEX_MASK : buffer.ReadByte();
                if (index > maxIndex)
                {
                    maxIndex = index;
                }
                if (index < 64)
                {
                    existFlag |= 1uL << index;
                }
                else
                {
                    if (existFlags == null)
                    {
                        existFlags = new ulong[4];
                    }
                    existFlags[index >> 8] |= 1uL << (index & 0x3F);
                }

                if (index >= infoArray.Length)
                {
                    ulong[] oldArray = infoArray;
                    infoArray = LongArrayPool.GetArray(index + 1);
                    Buffer.BlockCopy(oldArray, 0, infoArray, 0, oldArray.Length);
                    LongArrayPool.RecycleArray(oldArray);
                }

                byte type = (byte)(tag & TagFormat.TYPE_MASK);
                if (type <= TagFormat.TYPE_NUM_64)
                {
                    if (type == TagFormat.TYPE_0)
                    {
                        infoArray[index] = 0uL;
                    }
                    else if (type == TagFormat.TYPE_NUM_8)
                    {
                        infoArray[index] = buffer.ReadByte();
                    }
                    else if (type == TagFormat.TYPE_NUM_16)
                    {
                        infoArray[index] = (ushort)buffer.ReadShort();
                    }
                    else if (type == TagFormat.TYPE_NUM_32)
                    {
                        infoArray[index] = (uint)buffer.ReadInt();
                    }
                    else
                    {
                        // In case of not able to tell value missing (which infoArray[index] = NULL_FLAG) or value = NULL_FLAG,
                        // We use the highest bit of long value to indicate that the infoArray[index]
                        // is actually value (when positive) or position of value (mask highest bit to be one 1)

                        // little end, the high 8 bits at the last byte
                        byte b8 = buffer.hb[buffer.position + 7];
                        if ((b8 & TagFormat.BIG_INDEX_MASK) == 0)
                        {
                            infoArray[index] = (ulong)buffer.ReadLong();
                        }
                        else
                        {
                            infoArray[index] = (uint)buffer.position | INT_64_MIN_VALUE;
                            buffer.position += 8;
                        }
                    }
                }
                else
                {
                    int size;
                    if (type == TagFormat.TYPE_VAR_8)
                    {
                        size = buffer.ReadByte();
                    }
                    else if (type == TagFormat.TYPE_VAR_16)
                    {
                        size = (ushort)buffer.ReadShort();
                    }
                    else
                    {
                        size = buffer.ReadInt();
                    }
                    infoArray[index] = ((ulong)buffer.position << 32) | (uint)size;
                    buffer.position += size;
                }
            }
            // should be equal
            if (buffer.position != buffer.limit)
            {
                throw new IndexOutOfRangeException("invalid pack data");
            }
            if (maxIndex <= 0)
                return;

            int bits = 63 - maxIndex;
            ulong flippedFlag = (~existFlag) << bits;
            if (flippedFlag == 0)
            {
                return;
            }
            flippedFlag >>= bits;
            int i = 0;
            do
            {
                if ((flippedFlag & 1uL) != 0)
                {
                    infoArray[i] = NULL_FLAG;
                }
                i++;
                flippedFlag >>= 1;
            } while (flippedFlag != 0);

            if (maxIndex >= 64)
            {
                for (i = 64; i < maxIndex; i++)
                {
                    if ((existFlags[i >> 8] & (1uL << (i & 0x3F))) == 0)
                    {
                        infoArray[i] = NULL_FLAG;
                    }
                }
            }
        }

        public void Recycle()
        {
            if (root == null)
            {
                return;
            }

            if (buffer == root.buffer)
            {
                // current decoder is root decoder
                //CharArrayPool.recycleArray(root.charBuffer);
                root = null;
                buffer = null;
            }
            else
            {
                root.RecycleDecoder(this);
            }

            LongArrayPool.RecycleArray(infoArray);
            infoArray = null;
        }

        internal ulong GetInfo(int index)
        {
            if (maxIndex < 0)
            {
                if (buffer == null)
                {
                    throw new InvalidOperationException("Decoder had been recycled");
                }
                ParseBuffer();
            }
            if (index > maxIndex)
            {
                return NULL_FLAG;
            }
            return infoArray[index];
        }

        public PackBuffer GetCustom(int index)
        {
            ulong info = GetInfo(index);
            if (info == NULL_FLAG)
            {
                return null;
            }
            int len = (int)(info & INT_MASK);
            buffer.position = (len == 0) ? buffer.limit : (int)(info >> 32);
            return buffer;
        }

        public bool Contains(int index)
        {
            return GetInfo(index) != NULL_FLAG;
        }

        public bool GetBoolean(int index, bool defValue)
        {
            ulong info = GetInfo(index);
            return info == NULL_FLAG ? defValue : info == 1;
        }

        public bool GetBoolean(int index)
        {
            return GetInfo(index) == 1;
        }

        public byte GetByte(int index, byte defValue)
        {
            ulong info = GetInfo(index);
            return info == NULL_FLAG ? defValue : (byte)info;
        }

        public byte GetByte(int index)
        {
            return GetByte(index, (byte)0);
        }

        public short GetShort(int index, short defValue)
        {
            ulong info = GetInfo(index);
            return info == NULL_FLAG ? defValue : (short)info;
        }

        public short GetShort(int index)
        {
            return GetShort(index, (short)0);
        }

        public int GetInt(int index, int defValue)
        {
            ulong info = GetInfo(index);
            return info == NULL_FLAG ? defValue : (int)info;
        }

        public int GetInt(int index)
        {
            ulong info = GetInfo(index);
            return info == NULL_FLAG ? 0 : (int)info;
        }


        public long GetLong(int index, long defValue)
        {
            ulong info = GetInfo(index);
            if (info == NULL_FLAG)
            {
                return defValue;
            }
            return info < INT_64_MIN_VALUE ? (long)info : buffer.ReadLong((int)(info & INT_MASK));
        }

        public long GetLong(int index)
        {
            return GetLong(index, 0L);
        }

        public float GetFloat(int index, float defValue)
        {
            ulong info = GetInfo(index);
            if(info == NULL_FLAG)
            {
                return defValue;
            }
            else
            {
                int t = (int)info;
                unsafe
                {
                    return *(float*)&t;
                }
            }
        }

        public float GetFloat(int index)
        {
            return GetFloat(index, 0f);
        }

        public double GetDouble(int index, double defValue)
        {
            ulong info = GetInfo(index);
            if (info == NULL_FLAG)
            {
                return defValue;
            }
            long x = info < INT_64_MIN_VALUE ? (long)info : buffer.ReadLong((int)(info & INT_MASK));
            unsafe
            {
                return *(double*)&x;
            }
        }

        public double GetDouble(int index)
        {
            return GetDouble(index, 0D);
        }


        public string GetString(int index, string defValue)
        {
            ulong info = GetInfo(index);
            if (info == NULL_FLAG)
            {
                return defValue;
            }
            int len = (int)(info & INT_MASK);
            if (len == 0)
            {
                return "";
            }
            int offset = (int)(info >> 32);
            return Encoding.UTF8.GetString(buffer.hb, offset, len);
        }

        public string GetNotNullString(int index)
        {
            return GetString(index, "");
        }

        public string GetString(int index)
        {
            return GetString(index, null);
        }

        public T GetPackable<T>(int index, PackCreator<T> creator)
        {
            T t = default(T);
            return GetPackable(index, creator, t);
        }

        public T GetPackable<T>(int index, PackCreator<T> creator, T defValue)
        {
            ulong info = GetInfo(index);
            if (info == NULL_FLAG)
            {
                return defValue;
            }
            int offset = (int)(info >> 32);
            int len = (int)(info & INT_MASK);
            PackDecoder decoder = root.GetDecoder(offset, len);
            T result = creator(decoder);
            decoder.Recycle();
            return result;
        }

        public byte[] GetByteArray(int index)
        {
            ulong info = GetInfo(index);
            if (info == NULL_FLAG)
            {
                return null;
            }
            buffer.position = (int)(info >> 32);
            int len = (int)(info & INT_MASK);
            byte[] bytes = new byte[len];
            buffer.ReadBytes(bytes);
            return bytes;
        }

        private int SetPosAndGetLen(ulong info, int mask)
        {
            buffer.position = (int)(info >> 32);
            int len = (int)(info & INT_MASK);
            if ((len & mask) != 0)
            {
                throw new SystemException(INVALID_ARRAY_LENGTH);
            }
            return len;
        }

        public int[] GetIntArray(int index)
        {
            ulong info = GetInfo(index);
            if (info == NULL_FLAG)
            {
                return null;
            }
            int count = SetPosAndGetLen(info, 0x3);
            int n = count >> 2;
            int[] value = new int[n];
            if(n == 0)
            {
                return value;
            }
            if (BitConverter.IsLittleEndian)
            {
                Buffer.BlockCopy(buffer.hb, buffer.position, value, 0, count);
                buffer.position += count;
            }
            else
            {
                for (int i = 0; i < n; i++)
                {
                    value[i] = buffer.ReadInt();
                }
            }
            return value;
        }

        public long[] GetLongArray(int index)
        {
            ulong info = GetInfo(index);
            if (info == NULL_FLAG)
            {
                return null;
            }
            int count = SetPosAndGetLen(info, 0x7);
            int n = count >> 3;
            long[] value = new long[n];
            if(n == 0)
            {
                return value;
            }
            if (BitConverter.IsLittleEndian)
            {
                Buffer.BlockCopy(buffer.hb, buffer.position, value, 0, count);
                buffer.position += count;
            }
            else
            {
                for (int i = 0; i < n; i++)
                {
                    value[i] = buffer.ReadLong();
                }
            }
            return value;
        }

        public float[] GetFloatArray(int index)
        {
            ulong info = GetInfo(index);
            if (info == NULL_FLAG)
            {
                return null;
            }
            int count = SetPosAndGetLen(info, 0x3);
            int n = count >> 2;
            float[] value = new float[n];
            if (n == 0)
            {
                return value;
            }
            if (BitConverter.IsLittleEndian)
            {
                Buffer.BlockCopy(buffer.hb, buffer.position, value, 0, count);
                buffer.position += count;
            }
            else
            {
                for (int i = 0; i < n; i++)
                {
                    value[i] = buffer.ReadFloat();
                }
            }
            return value;
        }

        public double[] GetDoubleArray(int index)
        {
            ulong info = GetInfo(index);
            if (info == NULL_FLAG)
            {
                return null;
            }
            int count = SetPosAndGetLen(info, 0x7);
            int n = count >> 3;
            double[] value = new double[n];
            if (n == 0)
            {
                return value;
            }
            if (BitConverter.IsLittleEndian)
            {
                Buffer.BlockCopy(buffer.hb, buffer.position, value, 0, count);
                buffer.position += count;
            }
            else
            {
                for (int i = 0; i < n; i++)
                {
                    value[i] = buffer.ReadDouble();
                }
            }
            return value;
        }

        public string[] GetStringArray(int index)
        {
            int n = GetSize(index);
            if (n < 0) return null;
            string[] value = new string[n];
            for (int i = 0; i < n; i++)
            {
                value[i] = TakeString();
            }
            return value;
        }

        string TakeString()
        {
            string str;
            int len = buffer.ReadVarint32();
            if (len < 0)
            {
                str = null;
            }
            else
            {
                int offset = buffer.position;
                buffer.CheckBound(offset, len);
                str = len == 0 ? "" : Encoding.UTF8.GetString(buffer.hb, offset, len);
                buffer.position += len;
            }
            return str;
        }
   
        public T[] GetPackableArray<T>(int index, PackCreator<T> creator)
        {
            int n = GetSize(index);
            if (n < 0) return null;
            T[] value = new T[n];
            for (int i = 0; i < n; i++)
            {
                value[i] = TakePackable(creator);
            }
            return value;
        }

     T TakePackable<T>(PackCreator<T> creator)
        {
            T t;
            short a = buffer.ReadShort();
            if (a == TagFormat.NULL_MASK)
            {
                t = default(T);
            }
            else
            {
                int len = a >= 0 ? a : ((a & 0x7fff) << 16) | (buffer.ReadShort() & 0xffff);
                int offset = buffer.position;
                buffer.CheckBound(offset, len);
                PackDecoder decoder = root.GetDecoder(offset, len);
                t = creator(decoder);
                decoder.Recycle();
                buffer.position += len;
            }
            return t;
        }

        /*
         * To reuse memory, it's highly recommended to
         * call {@link PackDecoder#recycle()} after reading data.
         */
        public PackDecoder GetDecoder(int index)
        {
            ulong info = GetInfo(index);
            if (info == NULL_FLAG)
            {
                return null;
            }
            int offset = (int)(info >> 32);
            int len = (int)(info & INT_MASK);
            return root.GetDecoder(offset, len);
        }

        public class DecoderArray
        {
            private int count;
            private int index = 0;
            private PackDecoder decoder = null;
            private PackBuffer buffer;
            private Root root;

            internal DecoderArray(PackDecoder parentDecoder, int n)
            {
                count = n;
                buffer = parentDecoder.buffer;
                root = parentDecoder.root;
            }

            public int GetCount()
            {
                return count;
            }

            public bool HasNext()
            {
                if (index < count)
                {
                    return true;
                }
                else
                {
                    if (decoder != null)
                    {
                        decoder.Recycle();
                    }
                    return false;
                }
            }

            public PackDecoder Next()
            {
                if (index >= count)
                {
                    return null;
                }
                index++;
                short a = buffer.ReadShort();
                if (a == TagFormat.NULL_MASK)
                {
                    return null;
                }
                else
                {
                    int len = a >= 0 ? a : ((a & 0x7fff) << 16) | (buffer.ReadShort() & 0xffff);
                    int offset = buffer.position;
                    buffer.CheckBound(offset, len);
                    if (decoder == null)
                    {
                        decoder = root.GetDecoder(offset, len);
                    }
                    else
                    {
                        decoder.buffer.position = offset;
                        decoder.buffer.limit = offset + len;
                        decoder.maxIndex = -1;
                    }
                    buffer.position += len;
                    return decoder;
                }
            }
        }

        public DecoderArray GetDecoderArray(int index)
        {
            int n = GetSize(index);
            if (n < 0) return null;
            return new DecoderArray(this, n);
        }

        int GetSize(int index)
        {
            ulong info = GetInfo(index);
            if (info == NULL_FLAG)
            {
                return -1;
            }
            int len = (int)(info & INT_MASK);
            if (len == 0)
            {
                return 0;
            }
            buffer.position = (int)(info >> 32);
            int n = buffer.ReadVarint32();
            if (n < 0 || n > PackConfig.MAX_OBJECT_ARRAY_SIZE)
            {
                throw new IndexOutOfRangeException("invalid size of object array");
            }
            return n;
        }

        public Dictionary<string, string> GetStr2Str(int index)
        {
            int n = GetSize(index);
            if (n < 0) return null;
            Dictionary<string, string> map = new Dictionary<string, string>();
            for (int i = 0; i < n; i++)
            {
                map.Add(TakeString(), TakeString());
            }
            return map;
        }

        public Dictionary<string, T> GetStr2Pack<T>(int index, PackCreator<T> creator) where T : Packable
        {
            int n = GetSize(index);
            if (n < 0) return null;
            Dictionary<string, T> map = new Dictionary<string, T>();
            for (int i = 0; i < n; i++)
            {
                map.Add(TakeString(), TakePackable(creator));
            }
            return map;
        }

        public Dictionary<string, int> GetStr2Int(int index) 
        {
            int n = GetSize(index);
            if (n < 0) return null;
            Dictionary<string, int> map = new Dictionary<string, int>();
            for (int i = 0; i < n; i++)
            {
                map.Add(TakeString(), buffer.ReadInt());
            }
            return map;
        }

        public Dictionary<string, long> GetStr2Long(int index)
        {
            int n = GetSize(index);
            if (n < 0) return null;
            Dictionary<string, long> map = new Dictionary<string,long>();
            for (int i = 0; i < n; i++)
            {
                map.Add(TakeString(), buffer.ReadLong());
            }
            return map;
        }

        public Dictionary<string, float> GetStr2Float(int index)
        {
            int n = GetSize(index);
            if (n < 0) return null;
            Dictionary<string, float> map = new Dictionary<string, float>();
            for (int i = 0; i < n; i++)
            {
                map.Add(TakeString(), buffer.ReadFloat());
            }
            return map;
        }

        public Dictionary<string, double> GetStr2Double(int index)
        {
            int n = GetSize(index);
            if (n < 0) return null;
            Dictionary<string, double> map = new Dictionary<string, double>();
            for (int i = 0; i < n; i++)
            {
                map.Add(TakeString(), buffer.ReadInt());
            }
            return map;
        }

        public Dictionary<int, int> GetInt2Int(int index)
        {
            int n = GetSize(index);
            if (n < 0) return null;
            Dictionary<int, int> map = new Dictionary<int, int>();
            for (int i = 0; i < n; i++)
            {
                map.Add(buffer.ReadInt(), buffer.ReadInt());
            }
            return map;
        }

        public Dictionary<int, string> GetInt2Str(int index)
        {
            int n = GetSize(index);
            if (n < 0) return null;
            Dictionary<int, string> map = new Dictionary<int, string>();
            for (int i = 0; i < n; i++)
            {
                map.Add(buffer.ReadInt(), TakeString());
            }
            return map;
        }

        internal bool[] GetBooleanArray(int index)
        {
            return CompactCoder.GetBooleanArray(this, index);
        }

        public int[] GetEnumArray(int index)
        {
            return CompactCoder.GetEnumArray(this, index);
        }

        public int[] GetCompactIntArray(int index)
        {
            return CompactCoder.GetIntArray(this, index);
        }

        public long[] GetCompactLongArray(int index)
        {
            return CompactCoder.GetLongArray(this, index);
        }

        public double[] GetCompactDoubleArray(int index)
        {
            return CompactCoder.getDoubleArray(this, index);
        }

    }
}
