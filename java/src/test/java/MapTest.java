import com.example.RandomUtil;
import io.packable.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MapTest {
    @Test
    public void testStrToStr() {
        Map<String, String> a = new HashMap<>();
        a.put("test", "");
        for (int i = 0; i < 100; i += 3) {
            a.put(RandomUtil.randomShortString(), RandomUtil.randomNullableString());
        }

        PackEncoder encoder = new PackEncoder();
        byte[] bytes = encoder.putMap(0, a).toBytes();

        PackDecoder decoder = new PackDecoder(bytes);
        Map<String, String> b = decoder.getMap(0, String.class ,String.class);

        Assert.assertEquals(a, b);
    }

    @Test
    public void testStr2Pack() {
        Map<String, TestVo> a = new HashMap<>();
        a.put("test", new TestVo(1, 2));
        for (int i = 0; i < 100; i += 3) {
            a.put(RandomUtil.randomShortString(), new TestVo(RandomUtil.randomInt(), RandomUtil.randomLong()));
        }

        PackEncoder encoder = new PackEncoder();
        byte[] bytes = encoder.putMap(0, a, null, TEST_VO_PACKER).toBytes();

        PackDecoder decoder = new PackDecoder(bytes);
        Map<String, TestVo> b = decoder.getMap(0, String.class, TestVo.class, null, TEST_VO_PACKER);

        Assert.assertEquals(a, b);
}

    @Test
    public void testStr2Number() {
        testStr2Integer();
        testStr2Long();
        testStr2Float();
        testStr2Double();
    }

    private void testStr2Integer() {
        Map<String, Integer> a = new HashMap<>();
        for (int i = 0; i < 100; i += 3) {
            a.put(RandomUtil.randomShortString(), i);
        }

        PackEncoder encoder = new PackEncoder();
        byte[] bytes = encoder.putMap(0, a).toBytes();

        PackDecoder decoder = new PackDecoder(bytes);
        Map<String, Integer> b = decoder.getMap(0, String.class, Integer.class);

        Assert.assertEquals(a, b);
    }

    private void testStr2Long() {
        Map<String, Long> a = new HashMap<>();
        for (int i = 0; i < 100; i += 3) {
            a.put(RandomUtil.randomShortString(), RandomUtil.randomLong());
        }

        PackEncoder encoder = new PackEncoder();
        byte[] bytes = encoder.putMap(0, a).toBytes();

        PackDecoder decoder = new PackDecoder(bytes);
        Map<String, Long> b = decoder.getMap(0, String.class, Long.class);

        Assert.assertEquals(a, b);
    }

    private void testStr2Float() {
        Map<String, Float> a = new HashMap<>();
        for (int i = 0; i < 100; i += 3) {
            a.put(RandomUtil.randomShortString(), RandomUtil.randomFloat());
        }

        PackEncoder encoder = new PackEncoder();
        byte[] bytes = encoder.putMap(0, a).toBytes();

        PackDecoder decoder = new PackDecoder(bytes);
        Map<String, Float> b = decoder.getMap(0, String.class, Float.class);

        Assert.assertEquals(a, b);
    }

    private void testStr2Double() {
        Map<String, Double> a = new HashMap<>();
        for (int i = 0; i < 100; i += 3) {
            a.put(RandomUtil.randomShortString(), RandomUtil.randomDouble());
        }

        PackEncoder encoder = new PackEncoder();
        byte[] bytes = encoder.putMap(0, a).toBytes();

        PackDecoder decoder = new PackDecoder(bytes);
        Map<String, Double> b = decoder.getMap(0, String.class, Double.class);

        Assert.assertEquals(a, b);
    }

    @Test
    public void testInt2Int() {
        Map<Integer, Integer> a = new HashMap<>();
        for (int i = 0; i < 100; i += 3) {
            a.put(RandomUtil.randomInt(), RandomUtil.randomInt());
        }

        PackEncoder encoder = new PackEncoder();
        byte[] bytes = encoder.putMap(0, a).toBytes();

        PackDecoder decoder = new PackDecoder(bytes);
        Map<Integer, Integer> b = decoder.getMap(0, Integer.class, Integer.class);

        Assert.assertEquals(a, b);
    }

    @Test
    public void testInt2String() {
        Map<Integer, String> a = new HashMap<>();
        for (int i = 0; i < 100; i += 3) {
            a.put(RandomUtil.randomInt(), RandomUtil.randomShortString());
        }

        PackEncoder encoder = new PackEncoder();
        byte[] bytes = encoder.putMap(0, a).toBytes();

        PackDecoder decoder = new PackDecoder(bytes);
        Map<Integer, String> b = decoder.getMap(0, Integer.class, String.class);

        Assert.assertEquals(a, b);
    }

    private static final Packer<TestVo> TEST_VO_PACKER = new Packer<TestVo>() {
        @Override
        public void pack(PackEncoder encoder, TestVo target) {
            encoder.putInt(0, target.a)
                    .putLong(1, target.b);
        }

        @Override
        public TestVo unpack(PackDecoder decoder) {
            return new TestVo(
                    decoder.getInt(0),
                    decoder.getLong(1)
            );
        }
    };

    private static class TestVo  {
        int a;
        long b;

        TestVo(int a, long b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestVo)) return false;
            TestVo testVo = (TestVo) o;
            return a == testVo.a &&
                    Double.compare(testVo.b, b) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(a, b);
        }
    }
}
