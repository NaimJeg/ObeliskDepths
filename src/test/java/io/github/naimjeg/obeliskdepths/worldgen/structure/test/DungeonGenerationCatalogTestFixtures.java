package io.github.naimjeg.obeliskdepths.worldgen.structure.test;

import io.github.naimjeg.obeliskdepths.dungeon.content.DevelopmentDungeonContent;
import io.github.naimjeg.obeliskdepths.dungeon.content.DungeonContentSnapshot;
import io.github.naimjeg.obeliskdepths.dungeon.template.BuiltinDungeonTemplates;
import io.github.naimjeg.obeliskdepths.dungeon.theme.BuiltinDungeonThemes;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationCatalog;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonTemplateGeometryCatalog;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonTemplateGeometry;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class DungeonGenerationCatalogTestFixtures {
    private DungeonGenerationCatalogTestFixtures() {
    }

    public static DungeonGenerationCatalog catalog() {
        DungeonContentSnapshot snapshot =
                DevelopmentDungeonContent.createBuiltinSnapshot();
        return new DungeonGenerationCatalog(
                BuiltinDungeonThemes.GREAT_SWAMP,
                snapshot.themes().get(BuiltinDungeonThemes.GREAT_SWAMP),
                snapshot.rooms(),
                snapshot.corridors(),
                new DungeonTemplateGeometryCatalog(geometryByTemplate())
        );
    }

    public static Map<Identifier, DungeonTemplateGeometry> geometryByTemplate() {
        Map<Identifier, DungeonTemplateGeometry> geometry =
                new LinkedHashMap<>();
        geometry.put(
                BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_START_OPEN_PAVILION_01,
                new DungeonTemplateGeometry(8, 10, 8)
        );
        geometry.put(
                BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_COMBAT_OPEN_PAVILION_01,
                new DungeonTemplateGeometry(8, 10, 8)
        );
        geometry.put(
                BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_TREASURE_OBELISK_SANCTUM_01,
                new DungeonTemplateGeometry(32, 40, 32)
        );
        geometry.put(
                BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_BOSS_ALTAR_01,
                new DungeonTemplateGeometry(54, 26, 54)
        );
        BuiltinDungeonTemplates.ALL_SUPPLIED_TEMPLATES.stream()
                .filter(template -> template.getPath()
                        .contains("/corridor/straight/"))
                .forEach(template -> geometry.put(
                        template,
                        new DungeonTemplateGeometry(4, 1, 4)
                ));
        geometry.put(
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_CORNER_01,
                new DungeonTemplateGeometry(6, 1, 6)
        );
        geometry.put(
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_TEE_01,
                new DungeonTemplateGeometry(8, 1, 6)
        );
        return Map.copyOf(geometry);
    }
}
