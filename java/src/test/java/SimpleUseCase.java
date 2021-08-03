import io.packable.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Objects;

public class SimpleUseCase {
    static class Data implements Packable {
        String msg;
        Item[] items;

        @Override
        public void encode(PackEncoder encoder) {
            encoder.putString(0, msg)
                    .putPackableArray(1, items);
        }

        public static final PackCreator<Data> CREATOR = decoder -> {
            Data data = new Data();
            data.msg = decoder.getString(0);
            data.items = decoder.getPackableArray(1, Item.CREATOR);
            return data;
        };

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Data)) return false;
            Data data = (Data) o;
            return Objects.equals(msg, data.msg) &&
                    Arrays.equals(items, data.items);
        }

        @Override
        public int hashCode() {
            return Objects.hash(msg);
        }
    }

    static class Item implements Packable {
        int a;
        long b;

        Item(int a, long b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void encode(PackEncoder encoder) {
            encoder.putInt(0, a);
            encoder.putLong(1, b);
        }

        static final PackArrayCreator<Item> CREATOR = new PackArrayCreator<Item>() {
            @Override
            public Item[] newArray(int size) {
                return new Item[size];
            }

            @Override
            public Item decode(PackDecoder decoder) {
                return new Item(
                        decoder.getInt(0),
                        decoder.getLong(1)
                );
            }
        };

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

    @Test
    public void test1() {
        Data data = new Data();
        data.msg = "message";
        data.items = new Item[]{new Item(100, 200)};

        byte[] bytes = PackEncoder.marshal(data);

        Data data_2 = PackDecoder.unmarshal(bytes, Data.CREATOR);

        Assert.assertEquals(data, data_2);
    }

    @Test
    public void test2() {
        Data data = new Data();
        data.msg = "message";
        data.items = new Item[]{new Item(100, 200)};

        PackEncoder encoder = new PackEncoder();
        encoder.putString(0, data.msg);
        encoder.putPackableArray(1, data.items);
        byte[] bytes = encoder.getBytes();

        PackDecoder decoder = PackDecoder.newInstance(bytes);
        Data data_2 = new Data();
        data_2.msg = decoder.getString(0);
        data_2.items = decoder.getPackableArray(1, Item.CREATOR);
        decoder.recycle();

        Assert.assertEquals(data, data_2);
    }

    // If Item has not implement PackArrayCreator,
    // is also ok to decode by DecoderArray.
/*
        PackDecoder.DecoderArray da = decoder.getDecoderArray(1);
        if (da != null) {
            Item[] items = new Item[da.getCount()];
            int i = 0;
            while (da.hasNext()) {
                PackDecoder d = da.next();
                if (d == null) {
                    items[i++] = null;
                } else {
                    items[i++] = new Item(d.getInt(0), d.getLong(1));
                }
            }
            data_2.items = items;
        }
*/
}
