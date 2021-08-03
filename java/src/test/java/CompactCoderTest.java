import com.example.RandomUtil;
import io.packable.PackDecoder;
import io.packable.PackEncoder;
import org.junit.Assert;
import org.junit.Test;
import java.util.Random;

public class CompactCoderTest {
    private static int seed = (int) (System.currentTimeMillis() & 0xffff);
    private static Random random = new Random(seed);

    @Test
    public void testEnumArray() {
        System.out.println("seed:" + seed);
        int[] maskArray = new int[]{0x1, 0x3, 0xf, 0xff};
        PackEncoder encoder = new PackEncoder();

        for (int mask : maskArray) {
            for (int n = 31; n < 50; n++) {
                int[] a = new int[n];
                for (int i = 0; i < n; i++) {
                    a[i] = Math.abs(random.nextInt() & mask);
                }

                PackEncoder.Result result = encoder.putEnumArray(0, a).getResult();
                encoder.clear();

                PackDecoder decoder = PackDecoder.newInstance(result.bytes, 0, result.length);
                int[] b = decoder.getEnumArray( 0);
                decoder.recycle();

                Assert.assertArrayEquals("n=" + n + " mask:" + mask, a, b);
            }
        }

        encoder.recycle();
    }

    @Test
    public void testBooleanArray() {
        PackEncoder encoder = new PackEncoder();
        System.out.println("seed:" + seed);
        for (int i = 0; i < 10; i++) {
            testBoolArray(encoder, i);
        }
        for (int n = 31; n < 34; n++) {
            testBoolArray(encoder, n);
        }
        encoder.recycle();
    }

    private void testBoolArray(PackEncoder encoder, int n) {
        boolean[] a = new boolean[n];
        for (int i = 0; i < n; i++) {
            a[i] = random.nextBoolean();
        }

        PackEncoder.Result result = encoder.putBooleanArray(0, a).getResult();
        encoder.clear();

        PackDecoder decoder = PackDecoder.newInstance(result.bytes, 0, result.length);
        boolean[] b = decoder.getBooleanArray(0);
        decoder.recycle();

        Assert.assertArrayEquals("n=" + n, a, b);
    }

    @Test
    public void testNumberArray() {
        System.out.println("seed:" + seed);

        PackEncoder encoder = new PackEncoder();

        for (int n = 31; n < 200; n += 20) {
            testIntArray(encoder, n);
        }
        testIntArray(encoder, 10001);
        testIntArray(encoder, 30000);

        for (int n = 31; n < 200; n += 20) {
            testLongArray(encoder, n);
        }
        testLongArray(encoder, 10001);
        testLongArray(encoder, 30000);

        double[] a = new double[]{-2, -1, 0, 0.5D, 1, 2, 3, 4, 3.98D,
                31, 32, 33, 63, 64, 1999,
                (1 << 21) - 1, 1 << 21, (1 << 21) + 1};
        testDoubleArray(encoder, a);

        for (int n = 31; n < 200; n += 50) {
            testDoubleArray(encoder, n);
        }
        testDoubleArray(encoder, 10001);
        testDoubleArray(encoder, 30000);

        encoder.recycle();
    }

    private void testIntArray(PackEncoder encoder, int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            int x = RandomUtil.randomInt();
            if (((x >> 16) & 0x3) == 1) {
                x = 0;
            }
            a[i] = x;
        }

        PackEncoder.Result result = encoder.putCompactIntArray(0, a).getResult();
        encoder.clear();

        PackDecoder decoder = PackDecoder.newInstance(result.bytes, 0, result.length);
        int[] b = decoder.getCompactIntArray( 0);
        decoder.recycle();
        Assert.assertArrayEquals("n=" + n, a, b);
    }

    private void testLongArray(PackEncoder encoder, int n) {
        long[] a = new long[n];
        for (int i = 0; i < n; i++) {
            long x = RandomUtil.randomLong();
            if (((x >> 16) & 0x3) == 1) {
                x = 0;
            }
            a[i] = x;
        }

        PackEncoder.Result result = encoder.putCompactLongArray(0, a).getResult();
        encoder.clear();

        PackDecoder decoder = PackDecoder.newInstance(result.bytes, 0, result.length);
        long[] b = decoder.getCompactLongArray(0);
        decoder.recycle();
        Assert.assertArrayEquals("n=" + n, a, b);
    }

    private void testDoubleArray(PackEncoder encoder, int n) {
        double[] a = new double[n];
        for (int i = 0; i < n; i++) {
            double x = RandomUtil.randomDouble();
            a[i] = x;
        }
        testDoubleArray(encoder, a);
    }

    private void testDoubleArray(PackEncoder encoder, double[] a) {
        PackEncoder.Result result = encoder.putCompactDoubleArray(0, a).getResult();
        encoder.clear();

        PackDecoder decoder = PackDecoder.newInstance(result.bytes, 0, result.length);
        double[] b = decoder.getCompactDoubleArray(0);
        decoder.recycle();

        Assert.assertEquals("n=" + a.length, a.length, b.length);
        for (int i = 0; i < a.length; i++) {
            Assert.assertEquals("i=" + i, Double.doubleToRawLongBits(a[i]), Double.doubleToRawLongBits(b[i]));
        }
    }
}

