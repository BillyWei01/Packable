package io.packable;

public interface PackCreator<T> {
    /**
     * Create a new instance of the Packable class, instantiating it
     * from the given {@link PackDecoder} whose data had previously been written by
     * {@link Packable#encode}.
     *
     * @param decoder the object data decoder.
     * @return Returns a new instance of the Packable class.
     */
    T decode(PackDecoder decoder);
}

