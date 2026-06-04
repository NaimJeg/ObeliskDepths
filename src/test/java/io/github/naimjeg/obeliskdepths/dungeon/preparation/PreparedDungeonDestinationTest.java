package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class PreparedDungeonDestinationTest {
    private static final Path MAIN = Path.of("src", "main", "java");

    private PreparedDungeonDestinationTest() {
    }

    public static void main(String[] args) throws IOException {
        DungeonAsyncTestSupport.bootstrapMinecraft();

        retainsExactPreparedPosition();
        rejectsInvalidPositions();
        preparedAndWorldDestinationsHaveDistinctContracts();
        portalEntryAddsPlayerOrientationAtTeleportResolution();
        teleporterUsesSupportedVanillaTransition();
        ambiguousPreparationResolutionTypeIsAbsent();
    }

    private static void retainsExactPreparedPosition() {
        Vec3 position = new Vec3(1.25D, 64.5D, -9.75D);
        PreparedDungeonDestination destination =
                new PreparedDungeonDestination(position);

        check(destination.position() == position,
                "prepared destination must retain the exact immutable Vec3");
    }

    private static void rejectsInvalidPositions() {
        try {
            new PreparedDungeonDestination(null);
            check(false, "null position must be rejected");
        } catch (NullPointerException expected) {
            check("position".equals(expected.getMessage()),
                    "null rejection should identify position");
        }

        for (Vec3 invalid : List.of(
                new Vec3(Double.NaN, 64.0D, 0.0D),
                new Vec3(0.0D, Double.POSITIVE_INFINITY, 0.0D),
                new Vec3(0.0D, 64.0D, Double.NEGATIVE_INFINITY)
        )) {
            try {
                new PreparedDungeonDestination(invalid);
                check(false, "non-finite position must be rejected: " + invalid);
            } catch (IllegalArgumentException expected) {
                check(expected.getMessage().contains("finite"),
                        "non-finite rejection should explain the invariant");
            }
        }
    }

    private static void preparedAndWorldDestinationsHaveDistinctContracts() {
        RecordComponent[] preparedComponents =
                PreparedDungeonDestination.class.getRecordComponents();
        check(preparedComponents.length == 1,
                "preparation should carry position only");
        check(preparedComponents[0].getName().equals("position")
                        && preparedComponents[0].getType() == Vec3.class,
                "prepared component should be Vec3 position");

        RecordComponent[] worldComponents =
                io.github.naimjeg.obeliskdepths.world.ResolvedDungeonEntry.class
                        .getRecordComponents();
        check(Arrays.stream(worldComponents).map(RecordComponent::getName).toList()
                        .equals(List.of("targetLevel", "destination", "yaw", "pitch")),
                "world resolution should retain level, position, yaw, and pitch");
        check(worldComponents[0].getType() == ServerLevel.class,
                "world resolution should own the target level");
    }

    private static void portalEntryAddsPlayerOrientationAtTeleportResolution()
            throws IOException {
        String source = Files.readString(MAIN.resolve(
                "io/github/naimjeg/obeliskdepths/dungeon/interaction/DungeonPortalEntryService.java"
        ));

        check(source.contains("expectedPrepared.destination().position()"),
                "portal entry should consume the prepared position");
        check(source.contains("player.getYRot()")
                        && source.contains("player.getXRot()"),
                "portal entry should add current player orientation");
        check(source.contains("new ResolvedDungeonEntry("),
                "portal entry should construct the final world resolution");
    }

    private static void teleporterUsesSupportedVanillaTransition()
            throws IOException {
        String source = Files.readString(MAIN.resolve(
                "io/github/naimjeg/obeliskdepths/world/ObeliskDepthsTeleporter.java"
        ));
        check(source.contains("player.teleport(new TeleportTransition("),
                "teleporter should use ServerPlayer.teleport(TeleportTransition)");
        check(source.contains("targetLevel,")
                        && source.contains("Vec3.ZERO,")
                        && source.contains("TeleportTransition.DO_NOTHING"),
                "transition should carry target level, absolute movement, and post callback");
        check(!source.contains("ClientboundRespawnPacket")
                        && !source.contains("setServerLevel"),
                "mod teleporter must leave dimension protocol and level mutation to vanilla");
    }

    private static void ambiguousPreparationResolutionTypeIsAbsent() {
        Path ambiguousType = MAIN.resolve(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/ResolvedDungeonEntry.java"
        );
        check(!Files.exists(ambiguousType),
                "preparation must not define a second ResolvedDungeonEntry");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
