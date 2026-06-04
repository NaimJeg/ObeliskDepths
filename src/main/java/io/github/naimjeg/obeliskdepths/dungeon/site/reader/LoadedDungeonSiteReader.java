package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonStructureDistanceReport;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonStructureDistanceValidator;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Objects;

/*
 * Projects dungeon site metadata from a StructureStart that preparation has
 * already loaded through its start-chunk lease.
 *
 * Enforces structure-reference distance safety as a hard acceptance gate.
 * Non-compliant starts are rejected with a structured failure diagnostic.
 */
public final class LoadedDungeonSiteReader {
    private LoadedDungeonSiteReader() {
    }

    /**
     * Projects a loaded structure start into a dungeon site, enforcing
     * structure-reference distance safety. A non-compliant start
     * produces a structured failure instead of a site.
     */
    public static LoadedDungeonSiteProjectionResult projectValidatedStart(
            DungeonSiteKey key,
            StructureStart start
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(start, "start");

        DungeonStructureDistanceReport report =
                DungeonStructureDistanceValidator.analyze(start);

        // Hard gate: reject starts that fail the canonical acceptance predicate.
        if (!report.hasPieces()) {
            return LoadedDungeonSiteProjectionResult.rejected(
                    LoadedDungeonSiteProjectionFailure.NO_PIECES,
                    report
            );
        }
        if (!report.hasExactlyOnePrimaryEntry()) {
            return LoadedDungeonSiteProjectionResult.rejected(
                    LoadedDungeonSiteProjectionFailure.INVALID_PRIMARY_ENTRY_COUNT,
                    report
            );
        }
        if (!report.withinVanillaReferenceDistance()) {
            return LoadedDungeonSiteProjectionResult.rejected(
                    LoadedDungeonSiteProjectionFailure.OUTSIDE_VANILLA_REFERENCE_DISTANCE,
                    report
            );
        }

        DungeonSite site = GeneratedDungeonSiteProjector.project(key, start);
        if (!isValidLoadedSite(site)) {
            return LoadedDungeonSiteProjectionResult.rejected(
                    LoadedDungeonSiteProjectionFailure.INCOMPLETE_PROJECTED_METADATA,
                    report
            );
        }

        return LoadedDungeonSiteProjectionResult.accepted(site, report);
    }

    public static boolean isValidLoadedSite(DungeonSite site) {
        return site != null
                && !site.rooms().isEmpty()
                && site.primaryEntryRoom().isPresent()
                && site.primaryEntryRoom().get().contains(site.startPos());
    }
}