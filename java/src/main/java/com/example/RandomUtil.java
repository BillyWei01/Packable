package com.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class RandomUtil {
    private static final boolean ALL_ASCII = false;
    private static final String[] NOT_ASCII = new String[]{"\uD842\uDFB7", "一", "二", "三", "四", "五"};
    private static final char[] SPECIAL_CHARACTERS = "~@#$%^&*(){}[]<>:\"?;',./".toCharArray();
    public static final Random random = new Random(1);
    private static final int ARRAY_SIZE = 15;
    private static final int P = 5;

    public static boolean randomBoolean() {
        return random.nextBoolean();
    }

    public static float randomFloat() {
        int i = random.nextInt(10000);
        if (i % P == 0) {
            return 0f;
        }
        return random.nextFloat() * i;
    }

    public static double randomDouble() {
        int i = random.nextInt(1000000);
        if (i % P == 0) {
            return 0D;
        }
        return random.nextDouble() * random.nextInt(1000000);
    }

    public static double randomSmallDouble() {
        int i = random.nextInt();
        int flag = i % 4;
        if (flag == 0) {
            return 0D;
        }
        if (flag == 1) {
            return i & 0x1F;
        }
        if (flag == 2) {
            return i & 0xFFFFF;
        }
        return (i & 0xFFFFF) / 10.D;
    }

    public static int randomCount(int n) {
        return random.nextInt(n);
    }

    public static int randomInt() {
        int i = random.nextInt();
        if (i % P == 0) {
            return 0;
        }
        int shift = (i & 3) << 3;
        return i >>> shift;
    }

    public static int randomSignedInt() {
        int i = randomInt();
        return (i & 1) == 0 ? i : -i;
    }

    public static long randomLong() {
        long l = random.nextLong();
        if (l % P == 0) {
            return 0;
        }
        int shift = ((int) (l & 7)) << 3;
        return l >>> shift;
    }

    public static long randomSignedLong() {
        long l = randomLong();
        return (l & 1) == 1 ? l : -l;
    }

    public static String randomString() {
        int i = random.nextInt();
        if (i % P == 0) {
            return "";
        }
        return randomStr(i % 300);
    }

    public static String randomNullableString() {
        if ((random.nextInt() & 7) == 0) {
            return null;
        } else {
            return randomString();
        }
    }

    public static String randomShortString() {
        int i = random.nextInt();
        if(i % 10 == 0){
            return randomStr(random.nextInt(100));
        }else{
            return randomStr(random.nextInt(50));
        }
    }

    private static String randomStr(int maxLen) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < maxLen; i++) {
            int a =  random.nextInt(Integer.MAX_VALUE) % 50;
            int r = random.nextInt(Integer.MAX_VALUE);
            if (!ALL_ASCII && a < 4) {
                builder.append(NOT_ASCII[r % NOT_ASCII.length]);
            } else if (a < 10) {
                builder.append(SPECIAL_CHARACTERS[r % SPECIAL_CHARACTERS.length]);
            } else if (a < 20) {
                builder.append((char) ('0' + r % 10));
            } else if (a < 30) {
                builder.append((char) ('A' + r % 26));
            } else {
                builder.append((char) ('a' + r % 26));
            }
        }
        return builder.toString();
    }

    public static List<Boolean> randomBoolList() {
        int n = random.nextInt(ARRAY_SIZE);
        List<Boolean> a = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            a.add(random.nextBoolean());
        }
        return a;
    }

    public static int[] randomSingedIntArray(int n){
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = randomSignedInt();
        }
        return a;
    }

    public static List<Integer> randomIntList() {
        int n = random.nextInt(ARRAY_SIZE);
        List<Integer> a = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            a.add(Math.abs(random.nextInt()));
        }
        return a;
    }

    public static long[] randomSignedLongArray(int n){
        long[] a = new long[n];
        for (int i = 0; i < n; i++) {
            a[i] = randomSignedLong();
        }
        return a;
    }

    public static List<Long> randomLongList() {
        int n = random.nextInt(ARRAY_SIZE);
        List<Long> a = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            a.add(Math.abs(random.nextLong()));
        }
        return a;
    }

    public static List<Float> randomFloatList() {
        int n = random.nextInt(ARRAY_SIZE);
        List<Float> a = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            a.add(random.nextFloat());
        }
        return a;
    }

    public static double[] randomDoubleArray(int n) {
        double[] a = new double[n];
        for (int i = 0; i < n; i++) {
            a[i] = randomSmallDouble();
        }
        return a;
    }

    public static List<Double> randomDoubleList() {
        int n = random.nextInt(ARRAY_SIZE);
        List<Double> a = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            a.add(random.nextDouble() * randomInt());
        }
        return a;
    }

    public static List<String> randomStringList() {
        int n = random.nextInt(ARRAY_SIZE);
        String[] a = new String[n];
        for (int i = 0; i < n; i++) {
            a[i] = randomShortString();
        }
        return Arrays.asList(a);
    }

    public static boolean[] toBooleanList(List<Boolean> list) {
        int n = list.size();
        Boolean[] a = new Boolean[n];
        boolean[] b = new boolean[n];
        a = list.toArray(a);
        for (int i = 0; i < n; i++) {
            b[i] = a[i];
        }
        return b;
    }

    public static int[] toIntList(List<Integer> list) {
        int n = list.size();
        Integer[] a = new Integer[n];
        int[] b = new int[n];
        a = list.toArray(a);
        for (int i = 0; i < n; i++) {
            b[i] = a[i];
        }
        return b;
    }

    public static float[] toFloatList(List<Float> list) {
        int n = list.size();
        Float[] a = new Float[n];
        float[] b = new float[n];
        a = list.toArray(a);
        for (int i = 0; i < n; i++) {
            b[i] = a[i];
        }
        return b;
    }

    public static long[] toLongList(List<Long> list) {
        int n = list.size();
        Long[] a = new Long[n];
        long[] b = new long[n];
        a = list.toArray(a);
        for (int i = 0; i < n; i++) {
            b[i] = a[i];
        }
        return b;
    }

    public static double[] toDoubleList(List<Double> list) {
        int n = list.size();
        Double[] a = new Double[n];
        double[] b = new double[n];
        a = list.toArray(a);
        for (int i = 0; i < n; i++) {
            b[i] = a[i];
        }
        return b;
    }

    public static String[] toStringList(List<String> list) {
        int n = list.size();
        String[] a = new String[n];
        a = list.toArray(a);
        return a;
    }
}
