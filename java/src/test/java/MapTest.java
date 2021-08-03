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
        byte[] bytes = encoder.putStr2Str(0, a).getBytes();

        PackDecoder decoder = PackDecoder.newInstance(bytes);
        Map<String, String> b = decoder.getStr2Str(0);

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
        byte[] bytes = encoder.putStr2Pack(0, a).getBytes();

        PackDecoder decoder = PackDecoder.newInstance(bytes);
        Map<String, TestVo> b = decoder.getStr2Pack(0, TestVo.CREATOR);
        decoder.recycle();

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
        byte[] bytes = encoder.putStr2Number(0, a).getBytes();

        PackDecoder decoder = PackDecoder.newInstance(bytes);
        Map<String, Integer> b = decoder.getStr2Int(0);

        Assert.assertEquals(a, b);
    }

    private void testStr2Long() {
        Map<String, Long> a = new HashMap<>();
        for (int i = 0; i < 100; i += 3) {
            a.put(RandomUtil.randomShortString(), RandomUtil.randomLong());
        }

        PackEncoder encoder = new PackEncoder();
        byte[] bytes = encoder.putStr2Number(0, a).getBytes();

        PackDecoder decoder = PackDecoder.newInstance(bytes);
        Map<String, Long> b = decoder.getStr2Long(0);

        Assert.assertEquals(a, b);
    }

    private void testStr2Float() {
        Map<String, Float> a = new HashMap<>();
        for (int i = 0; i < 100; i += 3) {
            a.put(RandomUtil.randomShortString(), RandomUtil.randomFloat());
        }

        PackEncoder encoder = new PackEncoder();
        byte[] bytes = encoder.putStr2Number(0, a).getBytes();

        PackDecoder decoder = PackDecoder.newInstance(bytes);
        Map<String, Float> b = decoder.getStr2Float(0);

        Assert.assertEquals(a, b);
    }

    private void testStr2Double() {
        Map<String, Double> a = new HashMap<>();
        for (int i = 0; i < 100; i += 3) {
            a.put(RandomUtil.randomShortString(), RandomUtil.randomDouble());
        }

        PackEncoder encoder = new PackEncoder();
        byte[] bytes = encoder.putStr2Number(0, a).getBytes();

        PackDecoder decoder = PackDecoder.newInstance(bytes);
        Map<String, Double> b = decoder.getStr2Double(0);

        Assert.assertEquals(a, b);
    }

    @Test
    public void testInt2Int() {
        Map<Integer, Integer> a = new HashMap<>();
        for (int i = 0; i < 100; i += 3) {
            a.put(RandomUtil.randomInt(), RandomUtil.randomInt());
        }

        PackEncoder encoder = new PackEncoder();
        byte[] bytes = encoder.putInt2Int(0, a).getBytes();

        PackDecoder decoder = PackDecoder.newInstance(bytes);
        Map<Integer, Integer> b = decoder.getInt2Int(0);

        Assert.assertEquals(a, b);
    }

    @Test
    public void testInt2String() {
        Map<Integer, String> a = new HashMap<>();
        for (int i = 0; i < 100; i += 3) {
            a.put(RandomUtil.randomInt(), RandomUtil.randomShortString());
        }

        PackEncoder encoder = new PackEncoder();
        byte[] bytes = encoder.putInt2Str(0, a).getBytes();

        PackDecoder decoder = PackDecoder.newInstance(bytes);
        Map<Integer, String> b = decoder.getInt2Str(0);

        Assert.assertEquals(a, b);
    }

    private static class TestVo implements Packable {
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

        @Override
        public void encode(PackEncoder encoder) {
            encoder.putInt(0, a);
            encoder.putLong(1, b);
        }

        static final PackCreator<TestVo> CREATOR = new PackCreator<TestVo>() {
            @Override
            public TestVo decode(PackDecoder decoder) {
                return new TestVo(
                        decoder.getInt(0),
                        decoder.getLong(1)
                );
            }
        };
    }

}
