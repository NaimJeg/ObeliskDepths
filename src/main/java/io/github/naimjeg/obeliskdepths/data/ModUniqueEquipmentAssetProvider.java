package io.github.naimjeg.obeliskdepths.data;

import io.github.naimjeg.obeliskdepths.equipment.ObeliskEquipmentSlot;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskUniqueEquipmentCatalog;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskUniqueEquipmentDefinition;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Generates vanilla equipment appearance assets for registered unique armor. */
public final class ModUniqueEquipmentAssetProvider implements DataProvider {
    private final PackOutput.PathProvider equipment;

    public ModUniqueEquipmentAssetProvider(PackOutput output) {
        this.equipment = output.createPathProvider(
                PackOutput.Target.RESOURCE_PACK,
                "equipment"
        );
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (ObeliskUniqueEquipmentDefinition definition
                : ObeliskUniqueEquipmentCatalog.all()) {
            if (definition.slot() != ObeliskEquipmentSlot.WEAPON) {
                writes.add(DataProvider.saveStable(
                        cache,
                        EquipmentClientInfo.CODEC,
                        equipmentInfo(definition),
                        this.equipment.json(definition.assetId())
                ));
            }
        }
        return CompletableFuture.allOf(
                writes.toArray(CompletableFuture[]::new)
        );
    }

    @Override
    public String getName() {
        return "Obelisk Depths Unique Equipment Client Assets";
    }

    private static EquipmentClientInfo equipmentInfo(
            ObeliskUniqueEquipmentDefinition definition
    ) {
        EquipmentClientInfo.LayerType layerType = switch (definition.slot()) {
            case ARMOR_HEAD, ARMOR_CHEST, ARMOR_FEET ->
                    EquipmentClientInfo.LayerType.HUMANOID;
            case ARMOR_LEGS -> EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS;
            case WEAPON -> throw new IllegalArgumentException(
                    "Weapon cannot define equipment layers: " + definition.templateId()
            );
        };
        return EquipmentClientInfo.builder()
                .addLayers(layerType, new EquipmentClientInfo.Layer(definition.assetId()))
                .build();
    }

}
