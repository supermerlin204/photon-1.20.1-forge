package com.lowdragmc.photon.client.fx;

import com.lowdragmc.photon.client.gameobject.FXObject;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import java.util.Map;
import java.util.Queue;

/** Port-specific: collect registered living roots before drawing; no extra runtime ownership/cache. */
public final class FXPostProcessPreparation {
    private FXPostProcessPreparation() {}

    public static void prepare(Map<ParticleRenderType, Queue<Particle>> particles, float partialTicks) {
        var roots = particles.get(FXObject.NO_RENDER_RENDER_TYPE);
        if (roots == null) return;
        for (var particle : roots) {
            if (particle instanceof FXObject object && object.isAlive()
                    && object.getScene() instanceof FXRuntime runtime && runtime.root == object) {
                runtime.timelinePlayer.preparePostProcess(partialTicks);
            }
        }
    }
}
