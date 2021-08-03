namespace pack.packable
{
    public class PackConfig
    {
        /*
         * Object size limit, one million in default.
         * In case of error message to allocate too much memory.
         * You could adjust the size according to your situation.
         */
        public static int MAX_OBJECT_ARRAY_SIZE = 1 << 20;

        /*
         * Buffer size limit, 1G.
         * It's safety to limit the capacity.
         * Besides, it's not effective if the buffer is too large.
         */
        internal const int MAX_BUFFER_SIZE = 1 << 30;

        /*
         * Limit of double (again) memory, 4M.
         * See PackEncoder#checkCapacity(int)
         */
        internal const int LARGE_ARRAY_SIZE = 1 << 22;

        /*
         * Before putting object and object array to buffer, we reserve 4 bytes to place the 'length',
         * When accomplish, we know the exactly 'length',
         * if the 'length' is less or equal than TRIM_SIZE_LIMIT, we retrieved 3 bytes (by moving bytes forward).</p>
         * <p>
         * We could set TRIM_SIZE_LIMIT up to 255, but it's not effective to move too many bytes to save 3 bytes.
         * Besides, object recursion might make moving bytes grow up,
         * set a little limit could make the recursion moving stop soon.
         */
        internal const int TRIM_SIZE_LIMIT = 127;
    }
}
