package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.content.DungeonContentException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Per-generation cache for exact NBT template dimensions.
 */
public final class DungeonTemplateGeometryResolver {
    private final StructureTemplateManager templateManager;
    private final Map<Identifier, DungeonTemplateGeometry> cache =
            new LinkedHashMap<>();

    public DungeonTemplateGeometryResolver(
            StructureTemplateManager templateManager
    ) {
        if (templateManager == null) {
            throw new IllegalArgumentException(
                    "StructureTemplateManager is required for exact dungeon geometry"
            );
        }

        this.templateManager = templateManager;
    }

    public DungeonTemplateGeometry resolve(Identifier templateId) {
        if (templateId == null) {
            throw new IllegalArgumentException("Template id is required");
        }

        DungeonTemplateGeometry cached = this.cache.get(templateId);
        if (cached != null) {
            return cached;
        }

        StructureTemplate template = this.templateManager.get(templateId)
                .orElseThrow(() -> new DungeonContentException(
                        templateId,
                        "missing structure template"
                ));
        Vec3i size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            throw new DungeonContentException(
                    templateId,
                    "template dimensions must be positive: "
                            + size.getX()
                            + "x"
                            + size.getY()
                            + "x"
                            + size.getZ()
            );
        }
        DungeonTemplateGeometry geometry = new DungeonTemplateGeometry(
                size.getX(),
                size.getY(),
                size.getZ()
        );
        this.cache.put(templateId, geometry);
        return geometry;
    }
}
