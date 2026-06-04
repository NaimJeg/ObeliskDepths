package io.github.naimjeg.obeliskdepths.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class ExportStructureCommand {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(ExportStructureCommand.class);

    /*
     * These limits are intentionally conservative because fillFromWorld runs
     * synchronously on the server thread.
     *
     * Your 54 x 26 x 54 altar is only 75,816 blocks.
     */
    private static final int MAX_AXIS_SIZE = 256;
    private static final long MAX_VOLUME = 4_194_304L;

    private ExportStructureCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(
                Commands.literal("od_export_structure")
                        // Permission level 2: operators and cheats-enabled
                        // single-player users.
                        .then(
                                Commands.argument(
                                                "name",
                                                StringArgumentType.word()
                                        )
                                        .then(
                                                Commands.argument(
                                                                "corner1",
                                                                BlockPosArgument.blockPos()
                                                        )
                                                        .then(
                                                                Commands.argument(
                                                                                "corner2",
                                                                                BlockPosArgument.blockPos()
                                                                        )
                                                                        .executes(
                                                                                ExportStructureCommand::export
                                                                        )
                                                        )
                                        )
                        )
        );
    }

    private static int export(
            CommandContext<CommandSourceStack> context
    ) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        String rawName =
                StringArgumentType.getString(context, "name");

        Identifier structureId = parseStructureId(rawName);

        if (structureId == null) {
            source.sendFailure(
                    Component.literal(
                            "Invalid structure name: " + rawName
                    )
            );
            return 0;
        }

        BlockPos first =
                BlockPosArgument.getBlockPos(context, "corner1");
        BlockPos second =
                BlockPosArgument.getBlockPos(context, "corner2");

        // Allow the corners to be supplied in either order.
        BlockPos minimum = new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ())
        );

        BlockPos maximum = new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ())
        );

        long sizeX =
                (long) maximum.getX() - minimum.getX() + 1L;
        long sizeY =
                (long) maximum.getY() - minimum.getY() + 1L;
        long sizeZ =
                (long) maximum.getZ() - minimum.getZ() + 1L;

        if (sizeX > MAX_AXIS_SIZE
                || sizeY > MAX_AXIS_SIZE
                || sizeZ > MAX_AXIS_SIZE) {
            source.sendFailure(
                    Component.literal(
                            "Export rejected: maximum size per axis is "
                                    + MAX_AXIS_SIZE
                                    + ". Requested "
                                    + sizeX + " x "
                                    + sizeY + " x "
                                    + sizeZ + "."
                    )
            );
            return 0;
        }

        if (minimum.getY() < level.getMinY()
                || maximum.getY() > level.getMaxY()) {
            source.sendFailure(
                    Component.literal(
                            "Export rejected: selection extends outside "
                                    + "the dimension build height. Selected Y "
                                    + minimum.getY()
                                    + " through "
                                    + maximum.getY()
                                    + "."
                    )
            );
            return 0;
        }

        long volume = sizeX * sizeY * sizeZ;

        if (volume > MAX_VOLUME) {
            source.sendFailure(
                    Component.literal(
                            "Export rejected: selected volume is "
                                    + volume
                                    + " blocks; maximum is "
                                    + MAX_VOLUME
                                    + "."
                    )
            );
            return 0;
        }

        Vec3i structureSize = new Vec3i(
                Math.toIntExact(sizeX),
                Math.toIntExact(sizeY),
                Math.toIntExact(sizeZ)
        );

        try {
            /*
             * ignoreEntities = true
             * saveToDisk = true
             * ignoreBlocks = empty
             */
            boolean saved = StructureBlockEntity.saveStructure(
                    level,
                    structureId,
                    minimum,
                    structureSize,
                    true,
                    "ObeliskDepths export command",
                    true,
                    List.of()
            );

            if (!saved) {
                source.sendFailure(
                        Component.literal(
                                "Minecraft failed to save structure "
                                        + structureId
                                        + ". Check the server log."
                        )
                );
                return 0;
            }

            source.sendSuccess(
                    () -> Component.literal(
                            "Exported "
                                    + structureId
                                    + " from "
                                    + formatPosition(minimum)
                                    + " to "
                                    + formatPosition(maximum)
                                    + " with size "
                                    + sizeX + " x "
                                    + sizeY + " x "
                                    + sizeZ
                                    + " ("
                                    + volume
                                    + " blocks)."
                    ),
                    true
            );

            return 1;
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Failed to export structure {} from {} to {}",
                    structureId,
                    minimum,
                    maximum,
                    exception
            );

            source.sendFailure(
                    Component.literal(
                            "Structure export threw "
                                    + exception.getClass().getSimpleName()
                                    + ": "
                                    + safeMessage(exception)
                    )
            );

            return 0;
        }
    }

    private static Identifier parseStructureId(String rawName) {
        String qualifiedName = rawName.contains(":")
                ? rawName
                : ObeliskDepths.MOD_ID + ":" + rawName;

        return Identifier.tryParse(qualifiedName);
    }

    private static String formatPosition(BlockPos position) {
        return "["
                + position.getX() + ", "
                + position.getY() + ", "
                + position.getZ() + "]";
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? "no additional information"
                : message;
    }
}