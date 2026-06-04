package io.github.naimjeg.obeliskdepths.mixin;

import io.github.naimjeg.obeliskdepths.registry.ModWoodTypes;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WoodType.class)
abstract class WoodTypeMixin {
    @Inject(
            method = "<clinit>",
            at = @At("TAIL"),
            require = 1
    )
    private static void obeliskdepths$registerWoodTypes(
            CallbackInfo callbackInfo
    ) {
        ModWoodTypes.registerFromVanillaClinit();
    }
}
