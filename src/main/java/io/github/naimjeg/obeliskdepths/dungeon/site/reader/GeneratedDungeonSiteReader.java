package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/*
 * Reads dungeon site metadata from actual generated vanilla structure data.
 *
 * This is the authoritative bridge between worldgen and runtime:
 *
 *   chunk STRUCTURE_STARTS
 *     -> StructureStart
 *     -> ObeliskDungeonPiece metadata
 *     -> DungeonSite
 *
 * This class must not fabricate planned rooms or bounds. If no valid
 * StructureStart exists, it returns Optional.empty().
 */
public final class GeneratedDungeonSiteReader {
    private GeneratedDungeonSiteReader() {
    }

    public static Optional<DungeonSite> findNearestGeneratedSite(
            ServerLevel level,
            BlockPos origin,
            Predicate<DungeonSiteKey> canUseSite
    ) {
        return findNearestGeneratedSite(
                level,
                origin,
                (Function<DungeonSiteKey, String>) key ->
                        canUseSite.test(key) ? "candidate_accepted" : "candidate_predicate_rejected"
        );
    }

    public static Optional<DungeonSite> findNearestGeneratedSite(
            ServerLevel level,
            BlockPos origin,
            Function<DungeonSiteKey, String> eligibilityReason
    ) {
        for (DungeonSiteKey key : DungeonStructureLocator.findNearestStarts(level, origin, eligibilityReason)) {
            try {
                Optional<DungeonSite> site = readGeneratedSite(level, key);

                if (site.isEmpty()) {
                    ObeliskDepths.LOGGER.debug(
                            "[OD locator] rejected candidate reason=missing_generated_structure key={}",
                            key
                    );
                    continue;
                }

                if (!isValidGeneratedSite(site.get())) {
                    ObeliskDepths.LOGGER.warn(
                            "[OD locator] rejected candidate reason=incomplete_generated_metadata key={} rooms={} start={}",
                            key,
                            site.get().rooms().size(),
                            site.get().startPos()
                    );
                    continue;
                }

                return site;
            } catch (RuntimeException exception) {
                ObeliskDepths.LOGGER.warn(
                        "[OD locator] rejected candidate reason=invalid_generated_projection key={} message={}",
                        key,
                        exception.getMessage()
                );
            }
        }

        return Optional.empty();
    }

    public static Optional<DungeonSite> readGeneratedSite(
            ServerLevel level,
            DungeonSiteKey key
    ) {
        return DungeonStructureStartReader.read(level, key)
                .map(start -> GeneratedDungeonSiteProjector.project(key, start));
    }

    public static boolean isValidGeneratedSite(DungeonSite site) {
        return site != null
                && !site.rooms().isEmpty()
                && site.primaryEntryRoom().isPresent()
                && site.primaryEntryRoom().get().contains(site.startPos());
    }
}
