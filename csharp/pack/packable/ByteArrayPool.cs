using System;
using System.Collections.Generic;

namespace pack.packable
{
    class ByteArrayPool
    {
        // max buffer size: 1G
        private const int MAX_ARRAY_SHIFT = 30;

        // core array length : 4K
        private const int DEFAULT_ARRAY_SIZE_SHIFT = 12;
        internal const int DEFAULT_ARRAY_SIZE = 1 << DEFAULT_ARRAY_SIZE_SHIFT;
        private const int DEFAULT_ARRAY_CAPACITY = 12;
        private static int defaultCount = 0;
        private static readonly byte[][] defaultArrays = new byte[DEFAULT_ARRAY_CAPACITY][];


        private const int TEMP_ARRAYS_CAPACITY = MAX_ARRAY_SHIFT - DEFAULT_ARRAY_SIZE_SHIFT;
        private static readonly LinkedList<WeakReference>[] tempArraysList = new LinkedList<WeakReference>[TEMP_ARRAYS_CAPACITY];

        internal static byte[] GetArray(int len)
        {
            if (len > PackConfig.MAX_BUFFER_SIZE)
            {
                throw new ArgumentOutOfRangeException("desire capacity over limit, len:" + len);
            }
            int index = GetIndex(len);
            if (index == 0)
            {
                return GetCoreArray();
            }
            else
            {
                return GetTempArray(index);
            }
        }

        private static int GetIndex(int len)
        {
            if (len <= DEFAULT_ARRAY_SIZE)
            {
                return 0;
            }
            int n = 0;
            int a = (len - 1) >> DEFAULT_ARRAY_SIZE_SHIFT;
            while (a != 0)
            {
                a >>= 1;
                n++;
            }
            return n;
        }

        internal static void RecycleArray(byte[] bytes)
        {
            if (bytes == null)
            {
                return;
            }
            int len = bytes.Length;
            if (len == DEFAULT_ARRAY_SIZE)
            {
                RecycleCoreArray(bytes);
            }
            else
            {
                int index = GetIndex(len);
                if ((1 << (index + DEFAULT_ARRAY_SIZE_SHIFT)) == len)
                {
                    RecycleTempArray(index, bytes);
                }
                // reject bytes which size is not power of two
            }
        }

        private static byte[] GetCoreArray()
        {
            lock (defaultArrays)
            {
                if (defaultCount > 0)
                {
                    byte[] a = defaultArrays[--defaultCount];
                    defaultArrays[defaultCount] = null;
                    return a;
                }
            }
            return new byte[DEFAULT_ARRAY_SIZE];
        }

        private static void RecycleCoreArray(byte[] bytes)
        {
            lock (defaultArrays)
            {
                if (defaultCount < DEFAULT_ARRAY_CAPACITY)
                {
                    defaultArrays[defaultCount++] = bytes;
                }
            }
        }

        private static byte[] GetTempArray(int index)
        {
            lock (tempArraysList)
            {
                int start = index - 1;
                int end = Math.Min(start + 3, TEMP_ARRAYS_CAPACITY);
                for (int j = start; j < end; j++)
                {
                    LinkedList<WeakReference> list = tempArraysList[j];
                    if (list == null || list.Count == 0)
                    {
                        continue;
                    }
                    var node = list.First;
                    while (node != null)
                    {
                        list.Remove(node);
                        if (node.Value.Target is byte[] a)
                        {
                            return a;
                        }
                        node = node.Next;
                    }
                }
            }
            return new byte[1 << (index + DEFAULT_ARRAY_SIZE_SHIFT)];
        }

        private static void RecycleTempArray(int index, byte[] bytes)
        {
            lock (tempArraysList)
            {
                int i = index - 1;
                if (i < TEMP_ARRAYS_CAPACITY)
                {
                    LinkedList<WeakReference> list = tempArraysList[i];
                    if (list == null)
                    {
                        list = new LinkedList<WeakReference>();
                        tempArraysList[i] = list;
                    }
                    list.AddLast(new WeakReference(bytes));
                }
            }
        }
    }
}
