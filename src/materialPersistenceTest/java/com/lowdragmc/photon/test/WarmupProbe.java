package com.lowdragmc.photon.test;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXWarmup;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.postfx.runtime.PostEffectStack;
import com.lowdragmc.photon.client.PhotonParticleManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Opt-in real GL smoke test, no user world/assets. */
final class WarmupProbe {
    static void verify() throws Exception {
        var fx = new FX();
        var emitter = new ParticleEmitter();
        var rate = emitter.config.emission.getClass().getDeclaredField("emissionRate");
        rate.setAccessible(true);
        rate.set(emitter.config.emission, com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction.constant(2));
        fx.getFxData().objects().add(emitter);
        var before = fx.serializeNBT(null).copy();
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int priorError = GL11.glGetError();
        if (priorError != 0) throw new AssertionError("pre-existing GL error " + priorError);
        var result = FXWarmup.warmup(fx);
        if (result.ticks() != 1 || result.particles() <= 0) throw new AssertionError("no first-tick particles: " + result);
        FXWarmup.warmup(fx, 3);
        if (!before.equals(fx.serializeNBT(null))) throw new AssertionError("definition mutated");
        if (draw != GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                || read != GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)) throw new AssertionError("FBO leaked");
        int[] after = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, after);
        if (!java.util.Arrays.equals(viewport, after)) throw new AssertionError("viewport leaked");
        if (PhotonParticleManager.getRenderingManager() != null || PostEffectStack.currentSink() != PostEffectStack.GLOBAL)
            throw new AssertionError("warmup context leaked");
        if (GL11.glGetError() != 0) throw new AssertionError("warmup GL error");
        try {
            FXWarmup.warmup(fx, 0);
            throw new AssertionError("invalid ticks accepted");
        } catch (IllegalArgumentException expected) { }
        FXWarmup.warmup(new FX());
        verifyGraph(emitter);
    }

    private static void verifyGraph(ParticleEmitter emitter) throws Exception {
        var resources = com.lowdragmc.photon.gui.editor.resource.ShaderGraphResource.INSTANCE;
        var instance = resources.getResourceInstance();
        var provider = new com.lowdragmc.lowdraglib2.editor.resource.BuiltinResourceProvider<net.minecraft.nbt.CompoundTag>("warmup-test", instance);
        var path = provider.createSubPath("graph");
        provider.addResource(path, resources.serializeGraph(new com.lowdragmc.photon.client.shadergraph.ShaderGraph()));
        instance.addBuiltinProvider(provider);
        try {
            emitter.config.renderer.getMaterials().clear();
            emitter.config.renderer.getMaterials().add(new com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting(
                    new com.lowdragmc.photon.client.gameobject.emitter.data.material.ShaderGraphMaterial(path)));
            var fx = new FX();
            fx.getFxData().objects().add(emitter);
            FXWarmup.warmup(fx);
            var entry = com.lowdragmc.photon.client.shadergraph.runtime.ShaderGraphRuntime.get(path);
            var field = entry.getClass().getDeclaredField("variants");
            field.setAccessible(true);
            var variants = (java.util.Map<?, ?>) field.get(entry);
            if (variants.isEmpty() || variants.containsValue(null)) throw new AssertionError("runtime shader not drawn/compiled");
            int cpuVariants = variants.size();
            emitter.config.renderer.setUseGPUInstance(true);
            FXWarmup.warmup(fx);
            if (variants.size() <= cpuVariants || variants.containsValue(null)) throw new AssertionError("GPU variant not warmed");
            var saved = new java.util.HashMap<>(variants);
            FXWarmup.warmup(fx);
            if (!saved.equals(variants)) throw new AssertionError("warmup did not reuse shader cache");
            if (GL11.glGetError() != 0) throw new AssertionError("graph/GPU warmup GL error");
        } finally {
            com.lowdragmc.photon.client.shadergraph.runtime.ShaderGraphRuntime.invalidate(path);
            instance.removeBuiltinProvider(provider);
        }
    }
}
