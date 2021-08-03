package io.packable;

import java.util.HashMap;
import java.util.Map;

/**
 * PackEncoder/PackDecoder implements several map's encoding/decoding methods.
 * But map has many kinds of keys and values, we can't implement all of them.
 * So we provide {@link PackEncoder#putMap(int, int, PackEncoder.MapPacker)} to wrap key and value,
 * and provide {@link PackDecoder#getSize(int)} to get size of map.
 * You are free to use your own type,
 * even other dictionary data structure, like TreeMap, SparseArray (in Android SDK).<p/>
 * <p>
 * Note:
 * Be sure to call {@link PackEncoder#checkCapacity(int)} before putting data to {@link EncodeBuffer},
 * except to use {@link PackEncoder#wrapPackable(Packable)} and {@link PackEncoder#wrapString(String)},
 * they have implemented capacity check.
 */
public final class CustomMapCoder {
    public static void putLong2Str(final PackEncoder encoder, int index, final Map<Long, String> map) {
        if (map == null) return;
        final int size = map.size();
        encoder.putMap(index, size, () -> {
            EncodeBuffer buffer = encoder.getBuffer();
            encoder.checkCapacity(size << 3);
            for (Map.Entry<Long, String> entry : map.entrySet()) {
                buffer.writeLong(entry.getKey());
                encoder.wrapString(entry.getValue());
            }
        });
    }

    public static Map<Long, String> getLong2Str(PackDecoder decoder, int index) {
        int n = decoder.getSize(index);
        if (n < 0) return null;
        Map<Long, String> map = new HashMap<>((n << 2) / 3 + 1);
        DecodeBuffer buffer = decoder.getBuffer();
        for (int i = 0; i < n; i++) {
            map.put(buffer.readLong(), decoder.takeString());
        }
        return map;
    }
}
