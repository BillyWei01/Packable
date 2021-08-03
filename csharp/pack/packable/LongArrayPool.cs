using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace pack.packable
{
    class LongArrayPool
    {
        private const int DEFAULT_SIZE = 32;
        private const int DEFAULT_CAPACITY = 16;
        private static int defaultCount = 0;
        private static readonly ulong[][] defaultArrays = new ulong[DEFAULT_CAPACITY][];

        private const int SECOND_SIZE = 64;
        private const int SECOND_CAPACITY = 8;
        private static int seondCount = 0;
        private static readonly ulong[][] secondArrays = new ulong[SECOND_CAPACITY][];

        internal static ulong[] GetArray(int size)
        {
            if (size > TagFormat.MAX_INDEX_BOUND)
            {
                // should never happen
                throw new ArgumentOutOfRangeException("request size must less than:" + TagFormat.MAX_INDEX_BOUND);
            }

            if (size <= DEFAULT_SIZE)
            {
                return GetDefaultArray();
            }
            else if (size <= SECOND_SIZE)
            {
                return GetSecondArray();
            }
            else
            {
                return new ulong[(size <= 128) ? 128 : 256];
            }
        }

        internal static void RecycleArray(ulong[] a)
        {
            if (a == null)
            {
                return;
            }
            int size = a.Length;
            if (size == DEFAULT_SIZE)
            {
                RecycleDefaultArray(a);
            }
            else if (size == SECOND_SIZE)
            {
                RecycleSecondArray(a);
            }
            // else, drop it
        }

       internal static ulong[] GetDefaultArray()
        {
            lock (defaultArrays)
            {
                if (defaultCount > 0)
                {
                    ulong[] a = defaultArrays[--defaultCount];
                    defaultArrays[defaultCount] = null;
                    return a;
                }
            }
            return new ulong[DEFAULT_SIZE];
        }

        private static void RecycleDefaultArray(ulong[] a)
        {
            lock (defaultArrays)
            {
                if (defaultCount < DEFAULT_CAPACITY)
                {
                    defaultArrays[defaultCount++] = a;
                }
            }
        }

        private static ulong[] GetSecondArray()
        {
            lock (secondArrays)
            {
                if (seondCount > 0)
                {
                    ulong[] a = secondArrays[--seondCount];
                    secondArrays[seondCount] = null;
                    return a;
                }
            }
            return new ulong[SECOND_SIZE];
        }

        private static void RecycleSecondArray(ulong[] a)
        {
            lock (secondArrays)
            {
                if (seondCount < SECOND_CAPACITY)
                {
                    secondArrays[seondCount++] = a;
                }
            }
        }
    }
}
