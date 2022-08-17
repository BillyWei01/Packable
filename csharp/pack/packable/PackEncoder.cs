using System;
using System.Collections.Generic;
using System.Text;

namespace pack.packable
{
    public class PackEncoder
    {
        private const byte ZERO = 0;
        private const byte ONE = 1;

        private static readonly byte[] VARINT_NEGATIVE_1 = new byte[] { 0xff, 0xff, 0xff, 0xff, 15 };
        public const string NOT_SUPPORT_EMPTY_ELEMENT = "not support empty element";

        private volatile static int sMaxAllocated = ByteArrayPool.DEFAULT_ARRAY_SIZE;

        private readonly PackBuffer buffer;

        public PackEncoder() : this(ByteArrayPool.DEFAULT_ARRAY_SIZE)
        {
        }

        public PackEncoder(int budgetSize)
        {
            byte[] array = ByteArrayPool.GetArray(budgetSize);
            buffer = new PackBuffer(array);
        }

        public static byte[] Marshal(Packable packable)
        {
            return ToByteArray(packable, ByteArrayPool.DEFAULT_ARRAY_SIZE);
        }

        public static byte[] ToByteArray(Packable packable, int budgetSize)
        {
            PackEncoder encoder = new PackEncoder(budgetSize);
            packable.Encode(encoder);
            return encoder.GetBytes();
        }

        /*
         * After calling this method, don't call any method(including this) of current PackEncoder instance.
         * because the {@link #buffer} had been recycled.
         */
        public byte[] GetBytes()
        {
            CheckBufferState();
            int len = buffer.position;
            byte[] bytes = new byte[len];
            Buffer.BlockCopy(buffer.hb, 0, bytes, 0, len);
            // Array.Copy(buffer.hb, bytes, buffer.position);
            Recycle();
            return bytes;
        }

        /*
         * Use this function could read result bytes directly,
         * less memory allocate and copy compare with {@link #getBytes()}.
         */
        public Result GetResult()
        {
            CheckBufferState();
            return new Result(buffer.hb, buffer.position);
        }

        /*
         * Set buffer position to 0, to reuse PackEncoder
         */
        public void Clear()
        {
            CheckBufferState();
            buffer.position = 0;
        }

        public void Recycle()
        {
            ByteArrayPool.RecycleArray(buffer.hb);
            buffer.hb = null;
        }

        private void CheckBufferState()
        {
            if (buffer.hb == null)
            {
                throw new InvalidOperationException("Encoder had been recycled");
            }
        }

        internal PackBuffer GetBuffer()
        {
            CheckBufferState();
            return buffer;
        }

        internal void CheckCapacity(int expandSize)
        {
            int capacity = buffer.limit;
            int desSize = buffer.position + expandSize;
            if (desSize <= 0)
            {
                throw new OutOfMemoryException("desire capacity overflow");
            }
            if (desSize > capacity)
            {
                if (desSize > PackConfig.MAX_BUFFER_SIZE)
                {
                    throw new OutOfMemoryException("desire capacity over limit");
                }
                int newSize = capacity << 1;
                while (desSize > newSize)
                {
                    newSize = newSize << 1;
                }
                /*
                 * If the init buffer is small and the final content length is large,
                 * it's not effective to double buffer on every time that capacity not enough.
                 * So we mark the sMaxAllocated to indicate the level of memory occupied,
                 * when buffer is lower than sMaxAllocated, it's high probability to grow up to that level too,
                 * and we double it again if the size still less than doubleLimit.
                 * Example:
                 * If desSize = 5K, newSize will be 8K,
                 * If sMaxAllocated is larger then 8K, double size again to 16K, otherwise just to 8K.
                 */
                int doubleLimit = Math.Min(sMaxAllocated, PackConfig.LARGE_ARRAY_SIZE);
                if (newSize < doubleLimit)
                {
                    newSize = newSize << 1;
                }
                if (newSize > sMaxAllocated)
                {
                    sMaxAllocated = newSize;
                }
                byte[] oldArray = buffer.hb;
                byte[] newArray = ByteArrayPool.GetArray(newSize);
                Buffer.BlockCopy(oldArray, 0, newArray, 0, buffer.position);
                buffer.hb = newArray;
                buffer.limit = newSize;
                ByteArrayPool.RecycleArray(oldArray);
            }
        }

        internal void CheckIndex(int index)
        {
            if ((index & 0xFFFFFF00) != 0)
            {
                throw new ArgumentOutOfRangeException("index must between 0 to 255");
            }
        }

        internal void PutIndex(int index)
        {
            if (index >= TagFormat.LITTLE_INDEX_BOUND)
            {
                buffer.WriteByte(TagFormat.BIG_INDEX_MASK);
            }
            buffer.WriteByte((byte)(index));
        }

        public PackEncoder PutByte(int index, byte value)
        {
            CheckIndex(index);
            CheckCapacity(3);
            if (value == 0)
            {
                PutIndex(index);
            }
            else
            {
                if (index < TagFormat.LITTLE_INDEX_BOUND)
                {
                    buffer.WriteByte((byte)(index | TagFormat.TYPE_NUM_8));
                }
                else
                {
                    buffer.WriteByte((byte)(TagFormat.BIG_INDEX_MASK | TagFormat.TYPE_NUM_8));
                    buffer.WriteByte((byte)index);
                }
                buffer.WriteByte(value);
            }
            return this;
        }

        public PackEncoder PutBoolean(int index, bool value)
        {
            return PutByte(index, value ? ONE : ZERO);
        }

        public PackEncoder PutShort(int index, short value)
        {
            CheckIndex(index);
            CheckCapacity(4);
            if (value == 0)
            {
                PutIndex(index);
            }
            else
            {
                int pos = buffer.position;
                PutIndex(index);
                if ((value >> 8) == 0)
                {
                    buffer.hb[pos] |= TagFormat.TYPE_NUM_8;
                    buffer.WriteByte((byte)value);
                }
                else
                {
                    buffer.hb[pos] |= TagFormat.TYPE_NUM_16;
                    buffer.WriteShort(value);
                }
            }
            return this;
        }

        public PackEncoder PutInt(int index, int value)
        {
            CheckIndex(index);
            CheckCapacity(6);
            if (value == 0)
            {
                PutIndex(index);
            }
            else
            {
                int pos = buffer.position;
                PutIndex(index);
                if ((value >> 8) == 0)
                {
                    buffer.hb[pos] |= TagFormat.TYPE_NUM_8;
                    buffer.WriteByte((byte)value);
                }
                else if ((value >> 16) == 0)
                {
                    buffer.hb[pos] |= TagFormat.TYPE_NUM_16;
                    buffer.WriteShort((short)value);
                }
                else
                {
                    buffer.hb[pos] |= TagFormat.TYPE_NUM_32;
                    buffer.WriteInt(value);
                }
            }
            return this;
        }


        public PackEncoder PutLong(int index, long value)
        {
            CheckIndex(index);
            CheckCapacity(10);
            if (value == 0L)
            {
                PutIndex(index);
            }
            else
            {
                int pos = buffer.position;
                PutIndex(index);
                if ((value >> 32) != 0)
                {
                    buffer.hb[pos] |= TagFormat.TYPE_NUM_64;
                    buffer.WriteLong(value);
                }
                else if ((value >> 8) == 0)
                {
                    buffer.hb[pos] |= TagFormat.TYPE_NUM_8;
                    buffer.WriteByte((byte)value);
                }
                else if ((value >> 16) == 0)
                {
                    buffer.hb[pos] |= TagFormat.TYPE_NUM_16;
                    buffer.WriteShort((short)value);
                }
                else
                {
                    buffer.hb[pos] |= TagFormat.TYPE_NUM_32;
                    buffer.WriteInt((int)value);
                }
            }
            return this;
        }


        public PackEncoder PutFloat(int index, float value)
        {
            CheckIndex(index);
            CheckCapacity(6);
            if (value == 0f)
            {
                PutIndex(index);
            }
            else
            {
                if (index < TagFormat.LITTLE_INDEX_BOUND)
                {
                    buffer.WriteByte((byte)(index | TagFormat.TYPE_NUM_32));
                }
                else
                {
                    buffer.WriteByte(TagFormat.BIG_INDEX_MASK | TagFormat.TYPE_NUM_32);
                    buffer.WriteByte((byte)index);
                }
                buffer.WriteFloat(value);
            }
            return this;
        }

        public PackEncoder PutDouble(int index, double value)
        {
            CheckIndex(index);
            CheckCapacity(10);
            if (value == 0D)
            {
                PutIndex(index);
            }
            else
            {
                if (index < TagFormat.LITTLE_INDEX_BOUND)
                {
                    buffer.WriteByte((byte)(index | TagFormat.TYPE_NUM_64));
                }
                else
                {
                    buffer.WriteByte((byte)(TagFormat.BIG_INDEX_MASK | TagFormat.TYPE_NUM_64));
                    buffer.WriteByte((byte)index);
                }
                buffer.WriteDouble(value);
            }
            return this;
        }


        public PackEncoder PutString(int index, string value)
        {
            if (value == null)
            {
                return this;
            }
            if (value.Length == 0)
            {
                CheckIndex(index);
                CheckCapacity(2);
                PutIndex(index);
                return this;
            }
            byte[] bytes = Encoding.UTF8.GetBytes(value);
            WrapTagAndLength(index, bytes.Length);
            buffer.WriteBytes(bytes);
            return this;
        }

        /*
         * Wrap a String to buffer, used in array or container.
         */
        void WrapString(string str)
        {
            if (str == null)
            {
                CheckCapacity(5);
                buffer.WriteBytes(VARINT_NEGATIVE_1);
            }
            else
            {
                byte[] bytes = Encoding.UTF8.GetBytes(str);
                int len = bytes.Length;
                CheckCapacity(5 + len);
                buffer.WriteVarint32(len);
                buffer.WriteBytes(bytes);
            }
        }

        public PackEncoder PutPackable(int index, Packable value)
        {
            CheckIndex(index);
            if (value == null)
            {
                return this;
            }
            CheckCapacity(6);
            int pTag = buffer.position;
            PutIndex(index);
            // reserve 4 bytes to place length, it could be retrieved if not used
            buffer.position += 4;
            int pValue = buffer.position;
            value.Encode(this);
            if (pValue == buffer.position)
            {
                buffer.position = pValue - 4;
            }
            else
            {
                PutLen(pTag, pValue);
            }
            return this;
        }

        private long WrapObjectArrayHeader(int index, int size)
        {
            CheckIndex(index);
            if (size > PackConfig.MAX_OBJECT_ARRAY_SIZE)
            {
                throw new OutOfMemoryException("object array size out of limit");
            }
            // at most case: 2 bytes index, 4 bytes len, 5 bytes size
            CheckCapacity(11);
            int pTag = buffer.position;
            PutIndex(index);
            if (size <= 0) return -1;
            buffer.position += 4;
            int pValue = buffer.position;
            buffer.WriteVarint32(size);
            return ((long)pTag << 32) | (uint)pValue;
        }

        public PackEncoder PutStringArray(int index, string[] value)
        {
            if (value == null) return this;
            long tagValue = WrapObjectArrayHeader(index, value.Length);
            if (tagValue < 0) return this;
            foreach (string str in value)
            {
                WrapString(str);
            }
            PutLen((int)((ulong)tagValue >> 32), (int)tagValue);
            return this;
        }


        public PackEncoder PutPackableArray(int index, Packable[] value)
        {
            if (value == null) return this;
            long tagValue = WrapObjectArrayHeader(index, value.Length);
            if (tagValue < 0) return this;
            foreach (Packable pack in value)
            {
                WrapPackable(pack);
            }
            PutLen((int)((ulong)tagValue >> 32), (int)tagValue);
            return this;
        }

        /*
         * Wrap a object (implement Packable) to buffer, used in array or container.
         * <p>
         * When in {@link #putPackable}, we have tag to mark size of 'len', <br/>
         * but int array or container, we have to mark size by self.<br/>
         * The most simple way is using fixed 4 bytes(int) to store the 'len', but that wastes space.<br/>
         * We could use varint, but we don't know how many bytes the object will take,<br/>
         * calculate size before write? That costs more time, and complicated to implement.<br/>
         * So we could estimate size and write to buffer at first, after writing, we know the size.<br/>
         * If the estimated size is not enough to store the 'len' of object, move bytes.<br/>
         * Varint is too easy to miss match the size of 'len',<br/>
         * so we make two bytes as a unit (just call it wide varint ?)<br/>
         * In most case, 'len' of object in array is less than  0x7FFF (32767),<br/>
         * so it could generally avoid moving bytes, and save two bytes compare with the way of using fixed 4 bytes.
         */
        void WrapPackable(Packable pack)
        {
            CheckCapacity(2);
            if (pack == null)
            {
                buffer.WriteShort(TagFormat.NULL_MASK);
            }
            else
            {
                int pLen = buffer.position;
                buffer.position += 2;
                int pPack = buffer.position;
                pack.Encode(this);
                int len = buffer.position - pPack;
                if (len <= 0x7FFF)
                {
                    buffer.WriteShort(pLen, (short)len);
                }
                else
                {
                    CheckCapacity(2);
                    Buffer.BlockCopy(buffer.hb, pPack, buffer.hb, pPack + 2, len);
                    buffer.position += 2;
                    // if len >= 0x7FFF0000, the first two bytes will be 0xffff (means the object is null),
                    // but the PackConfig.MAX_BUFFER_SIZE is 1 << 30,
                    // so it's not impossible to be len >= 0x7FFF0000
                    buffer.WriteShort(pLen, (short)(((uint)len >> 16) | 0x8000));
                    buffer.WriteShort(pLen + 2, (short)(len & 0xffff));
                }
            }
        }

        private void PutLen(int pTag, int pValue)
        {
            int len = buffer.position - pValue;
            if (len <= PackConfig.TRIM_SIZE_LIMIT)
            {
                buffer.hb[pTag] |= TagFormat.TYPE_VAR_8;
                buffer.hb[pValue - 4] = (byte)len;
                Buffer.BlockCopy(buffer.hb, pValue, buffer.hb, pValue - 3, len);
                buffer.position -= 3;
            }
            else
            {
                buffer.hb[pTag] |= TagFormat.TYPE_VAR_32;
                buffer.WriteInt(pValue - 4, len);
            }
        }

        public PackEncoder PutByteArray(int index, byte[] value)
        {
            if (value != null)
            {
                WrapTagAndLength(index, value.Length);
                buffer.WriteBytes(value);
            }
            return this;
        }

        public PackEncoder PutIntArray(int index, int[] value)
        {
            if (value == null)
            {
                return this;
            }
            int n = value.Length;
            int count = n << 2;
            WrapTagAndLength(index, count);
            if (BitConverter.IsLittleEndian)
            {
                Buffer.BlockCopy(value, 0, buffer.hb, buffer.position, count);
                buffer.position += count;
            }
            else
            {
                foreach (int e in value)
                {
                    buffer.WriteInt(e);
                }
            }
            return this;
        }

        public PackEncoder PutLongArray(int index, long[] value)
        {
            if (value == null)
            {
                return this;
            }
            int n = value.Length;
            int count = n << 3;
            WrapTagAndLength(index, count);
            if (BitConverter.IsLittleEndian)
            {
                Buffer.BlockCopy(value, 0, buffer.hb, buffer.position, count);
                buffer.position += count;
            }
            else
            {
                foreach (long e in value)
                {
                    buffer.WriteLong(e);
                }
            }
            return this;
        }

        public PackEncoder PutFloatArray(int index, float[] value)
        {
            if (value == null)
            {
                return this;
            }
            int n = value.Length;
            int count = n << 2;
            WrapTagAndLength(index, count);
            if (BitConverter.IsLittleEndian)
            {
                Buffer.BlockCopy(value, 0, buffer.hb, buffer.position, count);
                buffer.position += count;
            }
            else
            {
                foreach (float e in value)
                {
                    buffer.WriteFloat(e);
                }
            }
            return this;
        }

        public PackEncoder PutDoubleArray(int index, double[] value)
        {
            if (value == null)
            {
                return this;
            }
            int n = value.Length;
            int count = n << 3;
            WrapTagAndLength(index, count);
            if (BitConverter.IsLittleEndian)
            {
                Buffer.BlockCopy(value, 0, buffer.hb, buffer.position, count);
                buffer.position += count;
            }
            else
            {
                foreach (double e in value)
                {
                    buffer.WriteDouble(e);
                }
            }
            return this;
        }

        internal void WrapTagAndLength(int index, int len)
        {
            CheckIndex(index);
            CheckCapacity(6 + len);
            if (len == 0)
            {
                PutIndex(index);
            }
            else
            {
                int pos = buffer.position;
                PutIndex(index);
                if (len <= 0xFF)
                {
                    buffer.hb[pos] |= TagFormat.TYPE_VAR_8;
                    buffer.WriteByte((byte)len);
                }
                else if (len <= 0xffff)
                {
                    buffer.hb[pos] |= TagFormat.TYPE_VAR_16;
                    buffer.WriteShort((short)len);
                }
                else
                {
                    buffer.hb[pos] |= TagFormat.TYPE_VAR_32;
                    buffer.WriteInt(len);
                }
            }
        }


        public PackBuffer PutCustom(int index, int len)
        {
            WrapTagAndLength(index, len);
            return buffer;
        }

        public PackEncoder PutIntList(int index, ICollection<int> value)
        {
            int n = GetListSize(index, value);
            if (n <= 0) return this;
            int[] a = new int[n];
            int i = 0;
            foreach (int x in value)
            {
                a[i++] = x;
            }
            PutIntArray(index, a);
            return this;
        }

        public PackEncoder PutLongList(int index, ICollection<long> value)
        {
            int n = GetListSize(index, value);
            if (n <= 0) return this;
            long[] a = new long[n];
            int i = 0;
            foreach (long x in value)
            {
                a[i++] = x;
            }
            PutLongArray(index, a);
            return this;
        }

        public PackEncoder PutFloatList(int index, ICollection<float> value)
        {
            int n = GetListSize(index, value);
            if (n <= 0) return this;
            float[] a = new float[n];
            int i = 0;
            foreach (float x in value)
            {
                a[i++] = x;
            }
            PutFloatArray(index, a);
            return this;
        }

        public PackEncoder PutDoubleList(int index, ICollection<double> value)
        {
            int n = GetListSize(index, value);
            if (n <= 0) return this;
            double[] a = new double[n];
            int i = 0;
            foreach (double x in value)
            {
                a[i++] = x;
            }
            PutDoubleArray(index, a);
            return this;
        }

        int GetListSize<T>(int index, ICollection<T> value)
        {
            if (value == null)
            {
                return 0;
            }
            int count = value.Count;
            if (count == 0)
            {
                WrapTagAndLength(index, 0);
                return 0;
            }
            return count;
        }

        public PackEncoder PutPackableList<T>(int index, ICollection<T> value) where T : Packable
        {
            if (value == null) return this;
            long tagValue = WrapObjectArrayHeader(index, value.Count);
            if (tagValue < 0) return this;
            foreach (Packable pack in value)
            {
                WrapPackable(pack);
            }
            PutLen((int)((ulong)tagValue >> 32), (int)tagValue);
            return this;
        }

        public PackEncoder PutStringList(int index, ICollection<string> value)
        {
            if (value == null) return this;
            long tagValue = WrapObjectArrayHeader(index, value.Count);
            if (tagValue < 0) return this;
            foreach (string str in value)
            {
                WrapString(str);
            }
            PutLen((int)((ulong)tagValue >> 32), (int)tagValue);
            return this;
        }

        public PackEncoder PutStr2Str(int index, Dictionary<string, string> map)
        {
            if (map == null) return this;
            long tagValue = WrapObjectArrayHeader(index, map.Count);
            if (tagValue < 0) return this;
            foreach (var entry in map)
            {
                WrapString(entry.Key);
                WrapString(entry.Value);
            }
            PutLen((int)((ulong)tagValue >> 32), (int)tagValue);
            return this;
        }

        public PackEncoder PutStr2Pack<T>(int index, Dictionary<string, T> map) where T : Packable
        {
            if (map == null) return this;
            long tagValue = WrapObjectArrayHeader(index, map.Count);
            if (tagValue < 0) return this;
            foreach (var entry in map)
            {
                WrapString(entry.Key);
                WrapPackable(entry.Value);
            }
            PutLen((int)((ulong)tagValue >> 32), (int)tagValue);
            return this;
        }

        public PackEncoder PutStr2Int(int index, Dictionary<string, int> map)
        {
            if (map == null) return this;
            long tagValue = WrapObjectArrayHeader(index, map.Count);
            if (tagValue < 0) return this;
            foreach (var entry in map)
            {
                WrapString(entry.Key);
                buffer.WriteInt(entry.Value);
            }
            PutLen((int)((ulong)tagValue >> 32), (int)tagValue);
            return this;
        }

        public PackEncoder PutStr2Long(int index, Dictionary<string, long> map)
        {
            if (map == null) return this;
            long tagValue = WrapObjectArrayHeader(index, map.Count);
            if (tagValue < 0) return this;
            foreach (var entry in map)
            {
                WrapString(entry.Key);
                buffer.WriteLong(entry.Value);
            }
            PutLen((int)((ulong)tagValue >> 32), (int)tagValue);
            return this;
        }

        public PackEncoder PutStr2Float(int index, Dictionary<string, float> map)
        {
            if (map == null) return this;
            long tagValue = WrapObjectArrayHeader(index, map.Count);
            if (tagValue < 0) return this;
            foreach (var entry in map)
            {
                WrapString(entry.Key);
                buffer.WriteFloat(entry.Value);
            }
            PutLen((int)((ulong)tagValue >> 32), (int)tagValue);
            return this;
        }

        public PackEncoder PutStr2Double(int index, Dictionary<string, double> map)
        {
            if (map == null) return this;
            long tagValue = WrapObjectArrayHeader(index, map.Count);
            if (tagValue < 0) return this;
            foreach (var entry in map)
            {
                WrapString(entry.Key);
                buffer.WriteDouble(entry.Value);
            }
            PutLen((int)((ulong)tagValue >> 32), (int)tagValue);
            return this;
        }


        public PackEncoder PutBooleanArray(int index, bool[] value)
        {
            CompactCoder.PutBooleanArray(this, index, value);
            return this;
        }

        /*
     * Put an enum array (map to int value) to buffer, in a compact way.
     * Only accept values not large than 255, otherwise will throw IllegalArgumentException.
     * More detail see {@link CompactCoder#putEnumArray(PackEncoder, int, int[])}
     */
        public PackEncoder PutEnumArray(int index, int[] value)
        {
            CompactCoder.PutEnumArray(this, index, value);
            return this;
        }

        /*
         * If many elements of array are many 0 or little integer,
         * use this method could save space.
         */
        public PackEncoder PutCompactIntArray(int index, int[] value)
        {
            CompactCoder.PutIntArray(this, index, value);
            return this;
        }

        public PackEncoder putCompactLongArray(int index, long[] value)
        {
            CompactCoder.PutLongArray(this, index, value);
            return this;
        }

        /*
         * Compression way similar to {@link #putCDouble(int, double)},
         * What different is this method takes two bits to make flag,
         * and for those values with 4 significand bits, can compact to 2 bytes.
         */
        public PackEncoder PutCompactDoubleArray(int index, double[] value)
        {
            CompactCoder.putDoubleArray(this, index, value);
            return this;
        }
    }

    public class Result
    {
        public readonly byte[] bytes;
        public readonly int length;

        public Result(byte[] b, int pos)
        {
            bytes = b;
            length = pos;
        }
    }

}
