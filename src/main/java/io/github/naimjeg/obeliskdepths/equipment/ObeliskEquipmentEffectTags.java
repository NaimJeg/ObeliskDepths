package io.github.naimjeg.obeliskdepths.equipment;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;

/** Public, reloadable MobEffect categories consumed by unique equipment rules. */
public final class ObeliskEquipmentEffectTags {
    public static final TagKey<MobEffect> UNSTOPPABLE_EFFECTS =
            create("equipment/unstoppable");
    public static final TagKey<MobEffect> CURSE_EFFECTS =
            create("equipment/curses");
    public static final TagKey<MobEffect> CROWD_CONTROLLED_EFFECTS =
            create("equipment/crowd_controlled");

    private ObeliskEquipmentEffectTags() {
    }

    private static TagKey<MobEffect> create(String path) {
        return TagKey.create(
                Registries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, path)
        );
    }
}
