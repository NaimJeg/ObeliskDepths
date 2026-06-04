package io.github.naimjeg.obeliskdepths.dungeon.site;

import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.dungeon.site.reader.GeneratedDungeonSiteReader;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class DungeonSiteProjectionCache {
    private static final Map<ServerLevel, Map<DungeonSiteKey, ResolvedDungeonSite>> CACHE =
            new WeakHashMap<>();

    private DungeonSiteProjectionCache() {
    }

    /*
     * Read a site projection in authoritative order.
     *
     * Priority:
     * 1. Actual generated StructureStart projection.
     * 2. In-memory authoritative cache.
     * 3. Saved snapshot that was originally derived from generated structure data.
     * 4. Empty.
     *
     * Do not use planned prototype projection here. Planned projections are
     * debug/preview only and must not silently enter runtime reservation.
     */
    public static Optional<ResolvedDungeonSite> read(
            ServerLevel level,
            DungeonSiteKey key
    ) {
        Optional<ResolvedDungeonSite> generated =
                GeneratedDungeonSiteReader.readGeneratedSite(level, key)
                        .map(site -> new ResolvedDungeonSite(
                                site,
                                DungeonSiteProjectionSource.GENERATED_STRUCTURE_START
                        ));

        if (generated.isPresent()) {
            putAuthoritative(level, generated.get());
            return generated;
        }

        Map<DungeonSiteKey, ResolvedDungeonSite> byKey = CACHE.get(level);

        if (byKey != null) {
            ResolvedDungeonSite cached = byKey.get(key);

            if (cached != null) {
                return Optional.of(cached);
            }
        }

        Optional<ResolvedDungeonSite> snapshot =
                DungeonManagerSavedData.get(level)
                        .sites()
                        .snapshot(key)
                        .map(site -> new ResolvedDungeonSite(
                                site,
                                DungeonSiteProjectionSource.SAVED_SNAPSHOT
                        ));

        snapshot.ifPresent(resolved -> putAuthoritative(level, resolved));

        return snapshot;
    }

    /*
     * Put only authoritative projections into this cache.
     * If a caller has a planned prototype projection, keep it local to debug UI.
     */
    public static void putAuthoritative(
            ServerLevel level,
            DungeonSite site,
            DungeonSiteProjectionSource source
    ) {
        putAuthoritative(level, new ResolvedDungeonSite(site, source));
    }

    public static void putAuthoritative(
            ServerLevel level,
            ResolvedDungeonSite resolved
    ) {
        requireAuthoritativeSource(resolved.source());
        CACHE.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(resolved.site().key(), resolved);
    }

    public static DungeonSiteProjectionSource requireAuthoritativeSource(
            DungeonSiteProjectionSource source
    ) {
        if (source == null || !source.authoritative()) {
            throw new IllegalArgumentException(
                    "Only authoritative dungeon site projections may be cached: " + source
            );
        }

        return source;
    }

    public static void invalidate(
            ServerLevel level,
            DungeonSiteKey key
    ) {
        Map<DungeonSiteKey, ResolvedDungeonSite> byKey = CACHE.get(level);

        if (byKey != null) {
            byKey.remove(key);
        }
    }

    public static void clear(ServerLevel level) {
        CACHE.remove(level);
    }
}
