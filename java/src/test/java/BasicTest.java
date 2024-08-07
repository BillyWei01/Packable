import io.packable.*;
import model.Data;
import model.Item;
import org.junit.Assert;
import org.junit.Test;
import model.Person;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BasicTest {
    public static final TypeAdapter<Person> PERSON_ADAPTER = new TypeAdapter<Person>(){
        @Override
        public void encode(PackEncoder encoder, Person target) {
            encoder.putString(0, target.name)
                    .putInt(1, target.age);
        }

        @Override
        public Person decode(PackDecoder decoder) {
            return new Person(
                    decoder.getString(0),
                    decoder.getInt(1)
            );
        }
    };

    @Test
    public void testPackSimpleObject() {
        Person person = new Person("Tom", 20);
        byte[] encoded = PackEncoder.encode(person, PERSON_ADAPTER);
        Person decoded = PackDecoder.decode(encoded, PERSON_ADAPTER);
        Assert.assertEquals(person, decoded);
    }


    private static final TypeAdapter<Data> DATA_ADAPTER = new TypeAdapter<Data>() {
        @Override
        public void encode(PackEncoder encoder, Data target) {
            encoder.putString(0, target.msg)
                    .putObjectList(1, target.items, ITEM_ADAPTER);
        }

        @Override
        public Data decode(PackDecoder decoder) {
            Data data = new Data();
            data.msg = decoder.getString(0);
            data.items = decoder.getObjectList(1, ITEM_ADAPTER);
            return data;
        }
    };

    private static final TypeAdapter<Item> ITEM_ADAPTER = new TypeAdapter<Item>() {
        @Override
        public void encode(PackEncoder encoder, Item target) {
            encoder.putInt(0, target.a);
            encoder.putLong(1, target.b);
        }

        @Override
        public Item decode(PackDecoder decoder) {
            return new Item(
                    decoder.getInt(0),
                    decoder.getLong(1)
            );
        }
    };

    @Test
    public void testPackComplexObject() {
        List<Item> itemList = new ArrayList<>();
        itemList.add(new Item(1, 2));
        itemList.add(new Item(100, 200));

        Data data = new Data();
        data.msg = "message";
        data.items = itemList;

        byte[] bytes = PackEncoder.encode(data, DATA_ADAPTER);

        Data decoded = PackDecoder.decode(bytes, DATA_ADAPTER);

        Assert.assertEquals(data, decoded);
    }

    @Test
    public void testPackBasicValue() {
        String message = "hello";
        int a = 100;
        int b = 200;

        PackEncoder encoder = new PackEncoder();
        encoder.putString(0, message)
                .putInt(1, a)
                .putInt(2, b);
        byte[] bytes = encoder.toBytes();

        PackDecoder decoder = new PackDecoder(bytes);
        String decodeMessage = decoder.getString(0);
        int decodedA = decoder.getInt(1);
        int decodedB = decoder.getInt(2);

        boolean equal = message.equals(decodeMessage) && (a == decodedA) && (b == decodedB);
        Assert.assertTrue(equal);
    }

    @Test
    public void testObjectList() {
        List<Person> personList = new ArrayList<>();
        personList.add(new Person("Tom", 20));
        personList.add(new Person("Jerry", 19));

        byte[] encoded = PackEncoder.encodeObjectList(personList, PERSON_ADAPTER);
        List<Person> decoded = PackDecoder.decodeObjectList(encoded, PERSON_ADAPTER);

        personList.toArray(new Person[4]);

        Assert.assertEquals(personList, decoded);
    }

    @Test
    public void testIntArray() {
        int[] a = new int[]{100, 200, 300};
        byte[] encoded = PackEncoder.encodeIntArray(a);
        int[] decoded = PackDecoder.decodeIntArray(encoded);
        Assert.assertArrayEquals(a, decoded);
    }

    @Test
    public void testLongArray() {
        long[] a = new long[]{100, 200, 300};
        byte[] encoded = PackEncoder.encodeLongArray(a);
        long[] decoded = PackDecoder.decodeLongArray(encoded);
        Assert.assertArrayEquals(a, decoded);
    }

    @Test
    public void testStringArray() {
        String[] a = new String[]{"hello", "world"};
        List<String> list = Arrays.asList(a);
        byte[] encoded = PackEncoder.encodeStringList(list);
        List<String> decoded = PackDecoder.decodeStringList(encoded);
        Assert.assertEquals(list, decoded);
    }
}
