import com.example.RandomUtil;
import io.packable.PackDecoder;
import io.packable.PackEncoder;
import org.junit.Assert;
import org.junit.Test;

public class SpecialCodeTest {
    @Test
    public void testCDouble() {
        PackEncoder encoder = new PackEncoder();
        double[] a = new double[]{-2, -1, 0, 0.5D, 1, 2, 3, 4, 3.98D,
                31, 32, 33, 63, 64, 1999,
                (1 << 21) - 1, 1 << 21, (1 << 21) + 1};
        testCDouble(encoder, a);

        for (int i = 10; i < 1000; i += 100) {
            a = RandomUtil.randomDoubleArray(i);
            testCDouble(encoder, a);
        }
        encoder.recycle();
    }

    private void testCDouble(PackEncoder encoder, double[] a) {
        for (double x : a) {
            encoder.putCDouble(0, x);
            PackEncoder.Result result = encoder.getResult();
            encoder.clear();

            PackDecoder decoder = PackDecoder.newInstance(result.bytes, 0, result.length);
            double y = decoder.getCDouble(0, Double.MAX_VALUE);
            decoder.recycle();

            Assert.assertEquals("x=" + x, Double.doubleToRawLongBits(x), Double.doubleToRawLongBits(y));
        }
    }

    @Test
    public void testZigzag() {
        PackEncoder encoder = new PackEncoder();
        for (int i = 0; i <= 1000; i += 200) {
            testZigzag32(encoder, RandomUtil.randomSingedIntArray(i));
            testZigzag64(encoder, RandomUtil.randomSignedLongArray(i));
        }
        encoder.recycle();
    }

    private void testZigzag32(PackEncoder encoder, int[] a) {
        for (int x : a) {
            encoder.putSInt(0, x);
            PackEncoder.Result result = encoder.getResult();
            encoder.clear();

            PackDecoder decoder = PackDecoder.newInstance(result.bytes, 0, result.length);
            int y = decoder.getSInt(0);
            decoder.recycle();
            Assert.assertEquals("x=" + x, x, y);
        }
    }

    private void testZigzag64(PackEncoder encoder, long[] a) {
        for (long x : a) {
            encoder.putSLong(0, x);
            PackEncoder.Result result = encoder.getResult();
            encoder.clear();

            PackDecoder decoder = PackDecoder.newInstance(result.bytes, 0, result.length);
            long y = decoder.getSLong(0);
            decoder.recycle();
            Assert.assertEquals("x=" + x, x, y);
        }
    }
}
