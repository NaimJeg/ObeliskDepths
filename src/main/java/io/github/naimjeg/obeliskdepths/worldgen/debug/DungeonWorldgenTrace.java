package io.github.naimjeg.obeliskdepths.worldgen.debug;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.world.level.ChunkPos;

public final class DungeonWorldgenTrace {
    public static final String PREFIX = "[OD worldgen-trace]";

    private static final String TRACE_PROPERTY = "obeliskdepths.worldgen.trace";
    private static final String TRACE_PLACEMENT_PROPERTY = "obeliskdepths.worldgen.tracePlacement";
    private static final String TRACE_START_CHUNK_PROPERTY = "obeliskdepths.worldgen.traceStartChunk";
    private static final String TRACE_ENV = "OBELISKDEPTHS_WORLDGEN_TRACE";
    private static final String TRACE_PLACEMENT_ENV = "OBELISKDEPTHS_WORLDGEN_TRACE_PLACEMENT";
    private static final String TRACE_START_CHUNK_ENV = "OBELISKDEPTHS_WORLDGEN_TRACE_START_CHUNK";

    private DungeonWorldgenTrace() {
    }

    public static boolean enabled() {
        return booleanOption(TRACE_PROPERTY, TRACE_ENV);
    }

    public static boolean placementEnabled() {
        return booleanOption(TRACE_PLACEMENT_PROPERTY, TRACE_PLACEMENT_ENV);
    }

    public static Context context(ChunkPos startChunk) {
        if (!enabled() || !matchesStartChunk(startChunk)) {
            return Context.disabled(startChunk);
        }

        return new Context(true, startChunk, startChunk.x() + "," + startChunk.z());
    }

    public static void debug(Context context, Supplier<String> message) {
        if (context.enabled()) {
            ObeliskDepths.LOGGER.info("{} trace={} {}", PREFIX, context.traceId(), message.get());
        }
    }

    public static void warn(Context context, Supplier<String> message) {
        if (context.enabled()) {
            ObeliskDepths.LOGGER.warn("{} trace={} {}", PREFIX, context.traceId(), message.get());
        }
    }

    public static void warnAlways(Context context, Supplier<String> message) {
        ObeliskDepths.LOGGER.warn("{} trace={} {}", PREFIX, context.traceId(), message.get());
    }

    public static String bounded(Object value) {
        String text = String.valueOf(value);
        if (text.length() <= 320) {
            return text;
        }

        return text.substring(0, 300) + "...<truncated:" + text.length() + ">";
    }

    private static boolean booleanOption(String property, String environment) {
        String raw = System.getProperty(property);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(environment);
        }
        if (raw == null || raw.isBlank()) {
            return false;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("true")
                || normalized.equals("1")
                || normalized.equals("yes")
                || normalized.equals("on");
    }

    private static boolean matchesStartChunk(ChunkPos startChunk) {
        String raw = System.getProperty(TRACE_START_CHUNK_PROPERTY);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(TRACE_START_CHUNK_ENV);
        }
        if (raw == null || raw.isBlank()) {
            return true;
        }

        String[] parts = raw.trim().split(",");
        if (parts.length != 2) {
            ObeliskDepths.LOGGER.warn(
                    "{} malformed {}={} expected=x,z",
                    PREFIX,
                    TRACE_START_CHUNK_PROPERTY,
                    raw
            );
            return true;
        }

        try {
            int x = Integer.parseInt(parts[0].trim());
            int z = Integer.parseInt(parts[1].trim());
            return startChunk.x() == x && startChunk.z() == z;
        } catch (NumberFormatException exception) {
            ObeliskDepths.LOGGER.warn(
                    "{} malformed {}={} expected integer x,z",
                    PREFIX,
                    TRACE_START_CHUNK_PROPERTY,
                    raw
            );
            return true;
        }
    }

    public record Context(
            boolean enabled,
            ChunkPos startChunk,
            String traceId
    ) {
        public static Context disabled(ChunkPos startChunk) {
            String traceId = startChunk == null
                    ? "disabled"
                    : startChunk.x() + "," + startChunk.z();
            return new Context(false, startChunk, traceId);
        }
    }
}
