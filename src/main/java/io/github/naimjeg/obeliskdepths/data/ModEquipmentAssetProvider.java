package io.github.naimjeg.obeliskdepths.data;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.registry.ModEquipmentAssets;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Generates client equipment asset definitions for Obelisk Depths armor. */
public final class ModEquipmentAssetProvider implements DataProvider {
    private final PackOutput.PathProvider pathProvider;

    public ModEquipmentAssetProvider(PackOutput output) {
        this.pathProvider = output.createPathProvider(
                PackOutput.Target.RESOURCE_PACK,
                "equipment"
        );
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        EquipmentClientInfo exile = EquipmentClientInfo.builder()
                .addLayers(
                        EquipmentClientInfo.LayerType.HUMANOID,
                        new EquipmentClientInfo.Layer(
                                Identifier.fromNamespaceAndPath(
                                        ObeliskDepths.MOD_ID,
                                        "exile/outer"
                                ),
                                Optional.empty(),
                                false
                        )
                )
                .addLayers(
                        EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                        new EquipmentClientInfo.Layer(
                                Identifier.fromNamespaceAndPath(
                                        ObeliskDepths.MOD_ID,
                                        "exile/inner"
                                ),
                                Optional.empty(),
                                false
                        )
                )
                .build();

        return DataProvider.saveAll(
                cache,
                EquipmentClientInfo.CODEC,
                key -> this.pathProvider.json(key),
                Map.of(ModEquipmentAssets.EXILE, exile)
        );
    }

    @Override
    public String getName() {
        return "Obelisk Depths Equipment Assets";
    }
}
