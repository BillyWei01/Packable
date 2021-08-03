namespace pack.packable
{
    static class TagFormat
    {
        internal const int LITTLE_INDEX_BOUND = 1 << 4;
        internal const int MAX_INDEX_BOUND = 1 << 8;

        // 0xffff
        internal const short NULL_MASK = -1;

        internal const int TYPE_SHIFT = 4;
        internal const byte BIG_INDEX_MASK = (byte)(1 << 7);
        internal const byte TYPE_MASK = 7 << TYPE_SHIFT;
        internal const byte INDEX_MASK = 0xF;

        internal const byte TYPE_0 = 0;
        internal const byte TYPE_NUM_8 = 1 << TYPE_SHIFT;
        internal const byte TYPE_NUM_16 = 2 << TYPE_SHIFT;
        internal const byte TYPE_NUM_32 = 3 << TYPE_SHIFT;
        internal const byte TYPE_NUM_64 = 4 << TYPE_SHIFT;
        internal const byte TYPE_VAR_8 = 5 << TYPE_SHIFT;
        internal const byte TYPE_VAR_16 = 6 << TYPE_SHIFT;
        internal const byte TYPE_VAR_32 = 7 << TYPE_SHIFT;
    }
}
