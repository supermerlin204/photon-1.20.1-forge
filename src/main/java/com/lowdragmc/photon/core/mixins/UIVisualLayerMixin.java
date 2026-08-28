package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.UIVisualLayer;
import com.lowdragmc.photon.client.compat.RenderTargetCompat;
import com.mojang.blaze3d.pipeline.MainTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UIVisualLayer.class, remap = false)
public class UIVisualLayerMixin {
    @Shadow
    private MainTarget target;

    @Inject(method = "bind", at = @At("TAIL"))
    private void photon$trackTarget(GUIContext context, CallbackInfo ci) {
        RenderTargetCompat.push(target);
    }

    @Inject(method = "unbind", at = @At("HEAD"))
    private void photon$untrackTarget(CallbackInfo ci) {
        RenderTargetCompat.pop(target);
    }
}
