package model;

import java.util.Objects;

public class Item {
    public int a;
    public long b;

    public Item(int a, long b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Item)) return false;
        Item item = (Item) o;
        return a == item.a &&
                b == item.b;
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b);
    }
}
