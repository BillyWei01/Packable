package io.packable;

import java.util.*;

public class NullableCoder {
    public static void putIntList(PackEncoder encoder, int index, Collection<Integer> value) {
        int n = encoder.getListSize(index, value);
        if (n <= 0) return;
        if (!value.contains(null)) {
            EncodeBuffer buffer = encoder.putCustom(index, (n << 2) + 1);
            buffer.writeByte((byte) 0);
            for (Integer x : value) {
                buffer.writeInt(x);
            }
        } else {
            int placeholder = findNotUsedInt(value);
            EncodeBuffer buffer = encoder.putCustom(index, (n << 2) + 5);
            buffer.writeByte((byte) 1);
            buffer.writeInt(placeholder);
            for (Integer x : value) {
                buffer.writeInt(x != null ? x : placeholder);
            }
        }
    }

    public static List<Integer> getIntList(PackDecoder decoder, int index) {
        long info = decoder.getInfo(index);
        if (info == PackDecoder.NULL_FLAG) {
            return null;
        }
        int len = (int) (info & PackDecoder.INT_MASK);
        if (len == 0) {
            return new ArrayList<>();
        }
        DecodeBuffer buffer = decoder.getBuffer();
        buffer.position = (int) (info >>> 32);
        List<Integer> a;
        if (buffer.readByte() == 0) {
            int n = (len - 1) >> 2;
            a = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                a.add(buffer.readInt());
            }
        } else {
            int placeholder = buffer.readInt();
            int n = (len - 5) >> 2;
            a = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                int x = buffer.readInt();
                if (x != placeholder) {
                    a.add(x);
                } else {
                    a.add(null);
                }
            }
        }
        return a;
    }

    public static void putLongList(PackEncoder encoder, int index, Collection<Long> value) {
        int n = encoder.getListSize(index, value);
        if (n <= 0) return;
        if (!value.contains(null)) {
            EncodeBuffer buffer = encoder.putCustom(index, (n << 3) + 1);
            buffer.writeByte((byte) 0);
            for (Long x : value) {
                buffer.writeLong(x);
            }
        } else {
            long placeholder = findNotUsedLong(value);
            EncodeBuffer buffer = encoder.putCustom(index, (n << 3) + 9);
            buffer.writeByte((byte) 1);
            buffer.writeLong(placeholder);
            for (Long x : value) {
                buffer.writeLong(x != null ? x : placeholder);
            }
        }
    }

    public static List<Long> getLongList(PackDecoder decoder, int index) {
        long info = decoder.getInfo(index);
        if (info == PackDecoder.NULL_FLAG) {
            return null;
        }

        int len = (int) (info & PackDecoder.INT_MASK);
        if (len == 0) {
            return new ArrayList<>();
        }

        DecodeBuffer buffer = decoder.getBuffer();
        buffer.position = (int) (info >>> 32);
        List<Long> a;
        if (buffer.readByte() == 0) {
            int n = (len - 1) >> 3;
            a = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                a.add(buffer.readLong());
            }
        } else {
            long placeholder = buffer.readLong();
            int n = (len - 9) >> 3;
            a = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                long x = buffer.readLong();
                if (x != placeholder) {
                    a.add(x);
                } else {
                    a.add(null);
                }
            }
        }
        return a;
    }

    public static void putFloatList(PackEncoder encoder, int index, Collection<Float> value) {
        int n = encoder.getListSize(index, value);
        if (n <= 0) return;
        if (!value.contains(null)) {
            EncodeBuffer buffer = encoder.putCustom(index, (n << 2) + 1);
            buffer.writeByte((byte) 0);
            for (Float x : value) {
                buffer.writeFloat(x);
            }
        } else {
            float placeholder = Float.NaN;
            EncodeBuffer buffer = encoder.putCustom(index, (n << 2) + 5);
            buffer.writeByte((byte) 1);
            buffer.writeFloat(placeholder);
            for (Float x : value) {
                buffer.writeFloat(x != null ? x : placeholder);
            }
        }
    }

    public static List<Float> getFloatList(PackDecoder decoder, int index) {
        long info = decoder.getInfo(index);
        if (info == PackDecoder.NULL_FLAG) {
            return null;
        }
        int len = (int) (info & PackDecoder.INT_MASK);
        if (len == 0) {
            return new ArrayList<>();
        }
        DecodeBuffer buffer = decoder.getBuffer();
        buffer.position = (int) (info >>> 32);
        List<Float> a;
        if (buffer.readByte() == 0) {
            int n = (len - 1) >> 2;
            a = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                a.add(buffer.readFloat());
            }
        } else {
            int placeholder = buffer.readInt();
            int n = (len - 5) >> 2;
            a = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                int x = buffer.readInt();
                if (x != placeholder) {
                    a.add(Float.intBitsToFloat(x));
                } else {
                    a.add(null);
                }
            }
        }
        return a;
    }

    public static void putDoubleList(PackEncoder encoder, int index, Collection<Double> value) {
        int n = encoder.getListSize(index, value);
        if (n <= 0){
            return;
        }
        if (!value.contains(null)) {
            EncodeBuffer buffer = encoder.putCustom(index, (n << 3) + 1);
            buffer.writeByte((byte) 0);
            for (Double x : value) {
                buffer.writeDouble(x);
            }
        } else {
            double placeholder = Double.NaN;
            EncodeBuffer buffer = encoder.putCustom(index, (n << 3) + 9);
            buffer.writeByte((byte) 1);
            buffer.writeDouble(placeholder);
            for (Double x : value) {
                buffer.writeDouble(x != null ? x : placeholder);
            }
        }
    }

    public static List<Double> getDoubleList(PackDecoder decoder, int index) {
        long info = decoder.getInfo(index);
        if (info == PackDecoder.NULL_FLAG){
            return null;
        }
        int len = (int) (info & PackDecoder.INT_MASK);
        if (len == 0){
            return new ArrayList<>();
        }
        DecodeBuffer buffer = decoder.getBuffer();
        buffer.position = (int) (info >>> 32);
        List<Double> a;
        if (buffer.readByte() == 0) {
            int n = (len - 1) >> 3;
            a = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                a.add(buffer.readDouble());
            }
        } else {
            long placeholder = buffer.readLong();
            int n = (len - 9) >> 3;
            a = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                long x = buffer.readLong();
                if (x != placeholder) {
                    a.add(Double.longBitsToDouble(x));
                } else {
                    a.add(null);
                }
            }
        }
        return a;
    }

    private static int findNotUsedInt(final Collection<Integer> value) {
        // probably not contains MAX_VALUE, just search MAX_VALUE at first
        if (!value.contains(Integer.MAX_VALUE)) {
            return Integer.MAX_VALUE;
        }

        HashSet<Integer> set = value instanceof HashSet ? (HashSet<Integer>) value : new HashSet<>(value);
        if (!value.contains(Integer.MIN_VALUE)) {
            return Integer.MIN_VALUE;
        }

        int n = set.size();
        int[] a = new int[n];
        int c = 0;
        for (Integer e : set) {
            if (e != null) {
                a[c++] = e;
            }
        }

        // We could find the top k and search the number between the element gap before sort all,
        // but in most case the code upper would return,
        // so for simple code, we just sort them all
        Arrays.sort(a);
        for (int i = 1; i < n; i++) {
            int start = a[i - 1] + 1;
            int end = a[i];
            for (int j = start; j < end; j++) {
                if (!set.contains(j)) {
                    return j;
                }
            }
        }

        // Should not be here.
        // The elements contain MIN_VALUE and MAX_VALUE,
        // from MIN_VALUE to MAX_VALUE, there must be a number not in 'value'
        // because the 'value' could not contain all int number
        throw new IllegalStateException("can't find not used number");
    }

    private static long findNotUsedLong(final Collection<Long> value) {
        if (!value.contains(Long.MAX_VALUE)) {
            return Long.MAX_VALUE;
        }

        HashSet<Long> set = value instanceof HashSet ? (HashSet<Long>) value : new HashSet<>(value);
        if (!value.contains(Long.MIN_VALUE)) {
            return Integer.MIN_VALUE;
        }

        int n = set.size();
        long[] a = new long[n];
        int c = 0;
        for (Long e : set) {
            if (e != null) {
                a[c++] = e;
            }
        }
        Arrays.sort(a);
        for (int i = 1; i < n; i++) {
            long start = a[i - 1] + 1;
            long end = a[i];
            for (long j = start; j < end; j++) {
                if (!set.contains(j)) {
                    return j;
                }
            }
        }

        throw new IllegalStateException("can't find not used number");
    }
}
