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
        int a = 100;
        String msg = "message";
        int b = 200;

        PackEncoder encoder = new PackEncoder();
        encoder.putInt(0, a)
                .putString(63+3, msg)
                .putInt(2, b);
        byte[] bytes = encoder.toBytes();

        PackDecoder decoder2 = PackDecoder.newInstance(bytes);
        int dA = decoder2.getInt(0);
        String dMsg = decoder2.getString(63+3);
        int dB = decoder2.getInt(2);
        String emptyMsg = decoder2.getString(3);

        boolean equal = msg.equals(dMsg) && (a == dA) && (b == dB);
        Assert.assertTrue(equal);

        Assert.assertNull(emptyMsg);
    }
}
