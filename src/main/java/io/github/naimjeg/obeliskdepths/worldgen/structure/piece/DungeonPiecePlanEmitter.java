package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPiece;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPieceRole;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public final class DungeonPiecePlanEmitter {
    private DungeonPiecePlanEmitter() {
    }

    public static void emit(
            StructurePiecesBuilder builder,
            DungeonPiecePlan plan
    ) {
        builder.addPiece(new ObeliskDungeonPiece(
                ObeliskDungeonPieceRole.SITE,
                "site",
                plan.layoutOrigin(),
                plan.siteBounds(),
                false
        ));

        for (DungeonPieceMetadata piece : plan.pieces()) {
            builder.addPiece(new ObeliskDungeonPiece(
                    piece.role(),
                    piece.id(),
                    piece.anchor(),
                    piece.bounds(),
                    piece.primaryEntry(),
                    piece.definitionId(),
                    piece.templateId(),
                    piece.rotation(),
                    piece.mirror(),
                    piece.templateOrigin(),
                    piece.templateBacked()
            ));
        }
    }
}
