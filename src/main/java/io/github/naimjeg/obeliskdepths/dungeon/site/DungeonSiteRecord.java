package io.github.naimjeg.obeliskdepths.dungeon.site;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;

import java.util.Optional;

/*
 * Permanent history for a worldgen dungeon site.
 *
 * Absence from DungeonManagerSavedData.siteRecords means:
 * - this site is still unreached;
 * - it may be selected by an obelisk.
 *
 * Presence means:
 * - the site has been reserved/reached before;
 * - normal obelisk allocation must not pick it again.
 */
public record DungeonSiteRecord(
        DungeonSiteKey siteKey,
        DungeonSiteUsageStatus status,
        Optional<DungeonInstanceId> activeInstanceId,
        long firstReservedGameTime,
        long lastUpdatedGameTime
) {
    public static final Codec<DungeonSiteRecord> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DungeonSiteKey.CODEC.fieldOf("site_key")
                            .forGetter(DungeonSiteRecord::siteKey),
                    DungeonSiteUsageStatus.CODEC.fieldOf("status")
                            .forGetter(DungeonSiteRecord::status),
                    DungeonInstanceId.CODEC.optionalFieldOf("active_instance_id")
                            .forGetter(DungeonSiteRecord::activeInstanceId),
                    Codec.LONG.fieldOf("first_reserved_game_time")
                            .forGetter(DungeonSiteRecord::firstReservedGameTime),
                    Codec.LONG.fieldOf("last_updated_game_time")
                            .forGetter(DungeonSiteRecord::lastUpdatedGameTime)
            ).apply(instance, DungeonSiteRecord::new));

    public static DungeonSiteRecord reserved(
            DungeonSiteKey siteKey,
            DungeonInstanceId instanceId,
            long gameTime
    ) {
        return new DungeonSiteRecord(
                siteKey,
                DungeonSiteUsageStatus.RESERVED,
                Optional.of(instanceId),
                gameTime,
                gameTime
        );
    }

    public DungeonSiteRecord retire(
            DungeonSiteUsageStatus finalStatus,
            long gameTime
    ) {
        if (finalStatus == DungeonSiteUsageStatus.RESERVED) {
            throw new IllegalArgumentException("Cannot retire a site as RESERVED.");
        }

        return new DungeonSiteRecord(
                this.siteKey,
                finalStatus,
                Optional.empty(),
                this.firstReservedGameTime,
                gameTime
        );
    }

    public boolean isReservedFor(DungeonInstanceId instanceId) {
        return this.status == DungeonSiteUsageStatus.RESERVED
                && this.activeInstanceId.map(instanceId::equals).orElse(false);
    }
}