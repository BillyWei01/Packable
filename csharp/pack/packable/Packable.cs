namespace pack.packable
{
    public interface Packable
    {
        void Encode(PackEncoder encoder);
    }

    public delegate T PackCreator<T>(PackDecoder decoder);
}
