package io.packable;

public interface PackArrayCreator<T> extends PackCreator<T> {
    /**
     * Create a new array of the Packable class.
     *
     * @param size Size of the array.
     * @return Returns an array of the Packable class, with every entry
     * initialized to null.
     */
    T[] newArray(int size);
}
