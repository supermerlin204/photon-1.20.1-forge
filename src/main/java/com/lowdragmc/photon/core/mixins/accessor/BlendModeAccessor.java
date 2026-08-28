package com.lowdragmc.photon.core.mixins.accessor;

import com.mojang.blaze3d.shaders.BlendMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlendMode.class)
public interface BlendModeAccessor {
    @Accessor("lastApplied")
    static BlendMode photon$getLastApplied() {
        throw new AssertionError();
    }

    @Accessor("lastApplied")
    static void photon$setLastApplied(BlendMode blendMode) {
        throw new AssertionError();
    }
}
