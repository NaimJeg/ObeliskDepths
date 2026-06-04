package io.github.naimjeg.obeliskdepths.worldgen.structure.generation;

import io.github.naimjeg.obeliskdepths.dungeon.content.DungeonContentException;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonTemplateGeometry;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonTemplateGeometryResolver;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.Identifier;

public record DungeonTemplateGeometryCatalog(
        Map<Identifier, DungeonTemplateGeometry> geometries
) {
    public DungeonTemplateGeometryCatalog {
        Objects.requireNonNull(geometries, "geometries");
        Map<Identifier, DungeonTemplateGeometry> copy = new LinkedHashMap<>();
        geometries.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(Identifier::toString)
                ))
                .forEach(entry -> copy.put(
                        Objects.requireNonNull(
                                entry.getKey(),
                                "template geometry id"
                        ),
                        Objects.requireNonNull(
                                entry.getValue(),
                                "template geometry for " + entry.getKey()
                        )
                ));
        geometries = Collections.unmodifiableMap(copy);
    }

    public static DungeonTemplateGeometryCatalog fromResolver(
            Set<Identifier> templateIds,
            DungeonTemplateGeometryResolver resolver,
            String context
    ) {
        Objects.requireNonNull(templateIds, "templateIds");
        Objects.requireNonNull(resolver, "resolver");

        Map<Identifier, DungeonTemplateGeometry> geometries =
                new LinkedHashMap<>();
        for (Identifier templateId : templateIds) {
            if (templateId == null) {
                throw new IllegalArgumentException(
                        "Template geometry catalog cannot include null id"
                );
            }
            try {
                geometries.put(templateId, resolver.resolve(templateId));
            } catch (DungeonContentException exception) {
                throw new DungeonContentException(
                        templateId,
                        "missing selected template geometry category=template_geometry context="
                                + context,
                        exception
                );
            } catch (RuntimeException exception) {
                throw new DungeonContentException(
                        templateId,
                        "missing selected template geometry category=template_geometry context="
                                + context,
                        exception
                );
            }
        }
        return new DungeonTemplateGeometryCatalog(geometries);
    }

    public DungeonTemplateGeometry resolve(Identifier templateId) {
        if (templateId == null) {
            throw new IllegalArgumentException("Template id is required");
        }
        DungeonTemplateGeometry geometry = this.geometries.get(templateId);
        if (geometry == null) {
            throw new DungeonContentException(
                    templateId,
                    "missing template geometry category=template_geometry"
            );
        }
        return geometry;
    }
}
