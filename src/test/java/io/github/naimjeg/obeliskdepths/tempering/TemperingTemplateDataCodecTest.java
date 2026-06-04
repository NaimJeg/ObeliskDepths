package io.github.naimjeg.obeliskdepths.tempering;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class TemperingTemplateDataCodecTest {
    @Test
    void constructorRejectsNonFiniteAndOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new TemperingTemplateData(0, 0.5F));
        assertThrows(IllegalArgumentException.class,
                () -> new TemperingTemplateData(5, 0.5F));
        assertThrows(IllegalArgumentException.class,
                () -> new TemperingTemplateData(1, Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new TemperingTemplateData(1, Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> new TemperingTemplateData(1, -0.01F));
        assertThrows(IllegalArgumentException.class,
                () -> new TemperingTemplateData(1, 1.01F));
    }

    @Test
    void codecRejectsNonFiniteAndOutOfRangeValues() {
        assertTrue(parse("{\"tier\":0,\"weight\":0.5}").error().isPresent());
        assertTrue(parse("{\"tier\":5,\"weight\":0.5}").error().isPresent());
        assertTrue(parse("{\"tier\":1,\"weight\":-0.1}").error().isPresent());
        assertTrue(parse("{\"tier\":1,\"weight\":1.1}").error().isPresent());
        assertEquals(
                new TemperingTemplateData(4, 1.0F),
                parse("{\"tier\":4,\"weight\":1.0}").getOrThrow()
        );
    }

    @Test
    void streamCodecRoundTripsAndRejectsInvalidWireValues() {
        RegistryFriendlyByteBuf valid = buffer();
        TemperingTemplateData expected = new TemperingTemplateData(3, 0.75F);
        TemperingTemplateData.STREAM_CODEC.encode(valid, expected);
        assertEquals(expected, TemperingTemplateData.STREAM_CODEC.decode(valid));

        RegistryFriendlyByteBuf invalid = buffer();
        invalid.writeInt(1);
        invalid.writeFloat(Float.NaN);
        assertThrows(
                IllegalArgumentException.class,
                () -> TemperingTemplateData.STREAM_CODEC.decode(invalid)
        );
    }

    private static com.mojang.serialization.DataResult<TemperingTemplateData> parse(
            String json
    ) {
        return TemperingTemplateData.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(json)
        );
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.EMPTY,
                ConnectionType.OTHER
        );
    }
}
