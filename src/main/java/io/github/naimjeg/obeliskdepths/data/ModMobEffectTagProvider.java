package io.github.naimjeg.obeliskdepths.data;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskEquipmentEffectTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

import java.util.concurrent.CompletableFuture;

/** Generates the reloadable MobEffect categories used by unique equipment. */
public final class ModMobEffectTagProvider
        extends IntrinsicHolderTagsProvider<MobEffect> {
    public ModMobEffectTagProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider
    ) {
        super(
                output,
                Registries.MOB_EFFECT,
                lookupProvider,
                effect -> BuiltInRegistries.MOB_EFFECT
                        .getResourceKey(effect)
                        .orElseThrow(),
                ObeliskDepths.MOD_ID
        );
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(ObeliskEquipmentEffectTags.UNSTOPPABLE_EFFECTS)
                .add(MobEffects.RESISTANCE.value());
        this.tag(ObeliskEquipmentEffectTags.CURSE_EFFECTS)
                .add(
                        MobEffects.WEAKNESS.value(),
                        MobEffects.WITHER.value(),
                        MobEffects.BAD_OMEN.value()
                );
        this.tag(ObeliskEquipmentEffectTags.CROWD_CONTROLLED_EFFECTS)
                .add(
                        MobEffects.SLOWNESS.value(),
                        MobEffects.BLINDNESS.value(),
                        MobEffects.DARKNESS.value(),
                        MobEffects.LEVITATION.value(),
                        MobEffects.WEAKNESS.value()
                );
    }
}
