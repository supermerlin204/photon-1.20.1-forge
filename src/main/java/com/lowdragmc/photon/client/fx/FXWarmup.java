package com.lowdragmc.photon.client.fx;

import com.lowdragmc.lowdraglib2.client.UIRenderStateScope;
import com.lowdragmc.lowdraglib2.client.scene.FBOWorldSceneRenderer;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.DummyWorld;
import com.lowdragmc.photon.client.FXSceneOptions;
import com.lowdragmc.photon.client.PhotonParticleManager;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.postfx.runtime.PostEffectStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import java.util.Objects;

/** Port-only, synchronous offscreen first-play simulation. No disk cache or automatic scheduling. */
@OnlyIn(Dist.CLIENT)
public final class FXWarmup {
    private FXWarmup() {}
    private static boolean running;

    /** Particle count is sampled after simulation, not proof that every shader variant was drawn. */
    public record Result(int ticks, int particles, long elapsedNanos) {}

    public static Result warmup(ResourceLocation id) {
        return warmup(id, 1);
    }

    public static Result warmup(ResourceLocation id, int ticks) {
        checkReady(ticks);
        var fx = FXHelper.getFX(Objects.requireNonNull(id));
        if (fx == null) throw new IllegalArgumentException("FX not found: " + id);
        return warmup(fx, ticks);
    }

    public static Result warmup(FX fx) {
        return warmup(fx, 1);
    }

    /**
     * Call on the render thread AFTER resource reload has completed, outside any world/UI scene
     * draw (e.g. a client-tick loading queue). Runs ticks, then draws the final state to a 64x64 FBO.
     * A queue-registration step is NOT counted as a simulated tick. Audio and timeline signals are
     * suppressed. The definition and live particle engine are untouched. GPU/resource caches survive
     * cleanup, but only paths reached by these ticks/draw are warmed: delayed, conditional or later
     * variants may require another tick count. World/entity-dependent behavior uses an empty dummy
     * world, not the player's world. No guarantee of eliminating all first-play stalls.
     */
    public static Result warmup(FX fx, int ticks) {
        checkReady(ticks);
        Objects.requireNonNull(fx);
        long start = System.nanoTime();
        float oldGameTime = RenderSystem.getShaderGameTime() * 24000f;
        int particles;
        running = true;
        try (var state = UIRenderStateScope.capture()) {
            var level = new DummyWorld(Minecraft.getInstance().level == null
                    ? MenuRegistries.ACCESS : Minecraft.getInstance().level.registryAccess());
            var manager = new PhotonParticleManager(FXSceneOptions.DEFAULT) {
                @Override
                public void render(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.Camera camera,
                                   float partial, java.util.function.Predicate<net.minecraft.client.particle.ParticleRenderType> filter) {
                    super.render(pose, camera, 1f, filter);
                }
            };
            var renderer = new FBOWorldSceneRenderer(level, 64, 64);
            var sink = new PostEffectStack();
            FXRuntime runtime = null;
            try (var isolated = PostEffectStack.pushIsolatedSink(sink)) {
                renderer.setParticleManager(manager);
                renderer.setCameraLookAt(new Vector3f(), 8, Math.toRadians(45), Math.toRadians(15));
                // Same shared render-pass/resource caches as normal playback, independent live state.
                runtime = fx.createRuntime();
                runtime.timelinePlayer.setSignalDispatch(false);
                runtime.timelinePlayer.setAudioDispatch(false);
                runtime.emit(new IEffectExecutor() {
                    @Override public net.minecraft.world.level.Level getLevel() { return level; }
                    @Override public boolean allowTimelineEvents() { return false; }
                    @Override public PostEffectStack postEffectSink() { return sink; }
                });
                manager.tickInternal(); // LDLib drains newly emitted objects AFTER ticking its queues.
                manager.setTime(0);
                for (int i = 0; i < ticks; i++) manager.tickInternal();
                manager.play(); // interpolate to the current state, rather than the pre-tick snapshot.
                renderer.drawScene(0, 0, 64, 64, -1, -1); // no blit to the user's screen
                particles = runtime.objects.values().stream()
                        .filter(ParticleEmitter.class::isInstance).map(ParticleEmitter.class::cast)
                        .mapToInt(ParticleEmitter::getParticleAmount).sum();
            } finally {
                try {
                    if (runtime != null) runtime.destroy(true);
                } finally {
                    manager.clear();
                    sink.onFrameEnd();
                    renderer.releaseResource();
                }
            }
        } finally {
            RenderSystem.setShaderGameTime((long) oldGameTime, oldGameTime - (long) oldGameTime);
            running = false;
        }
        return new Result(ticks, particles, System.nanoTime() - start);
    }

    private static void checkReady(int ticks) {
        RenderSystem.assertOnRenderThread();
        if (ticks < 1 || ticks > 10_000) throw new IllegalArgumentException("ticks must be in [1, 10000]");
        if (running || PhotonParticleManager.getRenderingManager() != null)
            throw new IllegalStateException("Warmup must not run inside another FX/scene render");
        if (Minecraft.getInstance().getOverlay() instanceof LoadingOverlay || GameRenderer.getParticleShader() == null)
            throw new IllegalStateException("Warmup requires completed client resource loading");
    }

    /** Main-menu previews need dimension/biome registries before a server has sent dynamic data. */
    private static final class MenuRegistries {
        private static final net.minecraft.core.RegistryAccess ACCESS = create();

        private static net.minecraft.core.RegistryAccess create() {
            var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
            var registries = new java.util.ArrayList<net.minecraft.core.Registry<?>>();
            net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY)
                    .registries().forEach(entry -> registries.add(entry.value()));
            registries.add(copy(lookup, net.minecraft.core.registries.Registries.DIMENSION_TYPE));
            registries.add(copy(lookup, net.minecraft.core.registries.Registries.BIOME));
            registries.add(copy(lookup, net.minecraft.core.registries.Registries.DAMAGE_TYPE));
            return new net.minecraft.core.RegistryAccess.ImmutableRegistryAccess(registries).freeze();
        }

        private static <T> net.minecraft.core.Registry<T> copy(net.minecraft.core.HolderLookup.Provider lookup,
                net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> key) {
            var registry = new net.minecraft.core.MappedRegistry<T>(key, com.mojang.serialization.Lifecycle.stable());
            lookup.lookupOrThrow(key).listElements().forEach(holder ->
                    registry.register(holder.key(), holder.value(), com.mojang.serialization.Lifecycle.stable()));
            return registry.freeze();
        }
    }
}
