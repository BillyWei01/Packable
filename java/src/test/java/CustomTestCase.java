import io.packable.*;
import org.junit.Assert;
import org.junit.Test;

import java.awt.*;
import java.util.Objects;

public class CustomTestCase {
    public static class Info implements Packable {
        public long id;
        public String name;
        public Rectangle rect;

        @Override
        public void encode(PackEncoder encoder) {
            encoder.putLong(0, id)
                    .putString(1, name);
            EncodeBuffer buf = encoder.putCustom(2, 16);
            buf.writeInt(rect.x);
            buf.writeInt(rect.y);
            buf.writeInt(rect.width);
            buf.writeInt(rect.height);
        }

        public static final PackCreator<Info> CREATOR = decoder -> {
            Info info = new Info();
            info.id = decoder.getLong(0);
            info.name = decoder.getString(1);
            DecodeBuffer buf = decoder.getCustom(2);
            if (buf != null) {
                info.rect = new Rectangle(
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt());
            }
            return info;
        };

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Info)) return false;
            Info info = (Info) o;
            return id == info.id &&
                    name.equals(info.name) &&
                    rect.equals(info.rect);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name, rect);
        }
    }

    @Test
    public void testCustomEncode() {
        Info info = new Info();
        info.id = 1234;
        info.name = "rect_1";
        info.rect = new Rectangle(100, 200, 300, 400);
        byte[] bytes = PackEncoder.marshal(info);
        Info dInfo = PackDecoder.unmarshal(bytes, Info.CREATOR);
        Assert.assertEquals(info, dInfo);
    }
}
