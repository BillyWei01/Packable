package model;

import java.util.List;
import java.util.Objects;

public class Data {
    public String msg;
    public List<Item> items;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Data data = (Data) o;
        return Objects.equals(msg, data.msg) && Objects.equals(items, data.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg);
    }
}
