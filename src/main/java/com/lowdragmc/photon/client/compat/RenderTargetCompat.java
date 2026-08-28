package com.lowdragmc.photon.client.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.Deque;

/** Tracks LDLib2 visual-layer targets, falling back to Minecraft's main target. */
@OnlyIn(Dist.CLIENT)
public final class RenderTargetCompat {
    private static final Deque<RenderTarget> TARGETS = new ArrayDeque<>();

    private RenderTargetCompat() {
    }

    public static RenderTarget current() {
        var target = TARGETS.peek();
        return target != null ? target : Minecraft.getInstance().getMainRenderTarget();
    }

    public static void push(RenderTarget target) {
        if (target != null) TARGETS.push(target);
    }

    public static void pop(RenderTarget target) {
        if (target != null && TARGETS.peek() == target) TARGETS.pop();
    }
}
