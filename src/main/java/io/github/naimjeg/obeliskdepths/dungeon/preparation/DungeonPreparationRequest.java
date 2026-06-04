package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.StableComponentSnapshot;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.TributeFingerprint;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public record DungeonPreparationRequest(
        UUID playerId,
        ResourceKey<Level> sourceDimension,
        BlockPos obeliskPos,
        ResolvedTribute expectedTribute,
        int sourceContainerId,
        TributeFingerprint tributeFingerprint
) {
    public DungeonPreparationRequest {
        if (playerId == null) {
            throw new IllegalArgumentException("Preparation player id must be present.");
        }
        if (sourceDimension == null) {
            throw new IllegalArgumentException("Preparation source dimension must be present.");
        }
        if (obeliskPos == null) {
            throw new IllegalArgumentException("Preparation obelisk position must be present.");
        }
        if (expectedTribute == null || !expectedTribute.valid()) {
            throw new IllegalArgumentException("Preparation tribute must be valid.");
        }
        if (tributeFingerprint == null) {
            throw new IllegalArgumentException("Preparation tribute fingerprint must be present.");
        }
        obeliskPos = obeliskPos.immutable();
    }

    /**
     * Convenience factory for tests that creates a pass-through fingerprint.
     * Production code should always use the canonical constructor with a real
     * {@link TributeFingerprint}.
     */
    public static DungeonPreparationRequest forTests(
            UUID playerId,
            ResourceKey<Level> sourceDimension,
            BlockPos obeliskPos,
            ResolvedTribute expectedTribute,
            int sourceContainerId
    ) {
        ResolvedTribute fingerprintTribute =
                expectedTribute != null && expectedTribute.valid()
                        ? expectedTribute
                        : new ResolvedTribute(true, 1, 1, 0.0F, 1.0F, 1);
        return new DungeonPreparationRequest(
                playerId,
                sourceDimension,
                obeliskPos,
                expectedTribute,
                sourceContainerId,
                new TributeFingerprint(
                        Identifier.fromNamespaceAndPath("test", "dummy"),
                        fingerprintTribute.amount(),
                        StableComponentSnapshot.EMPTY,
                        fingerprintTribute
                )
        );
    }
}
