package com.lowdragmc.photon.client.fx;

import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Positions/velocities already include the root pose, either at spawn or via simulation space. */
public final class WholeEffectRenderSpace {
    private WholeEffectRenderSpace() {}

    private static IWholeEffectTransformer transformer(TileParticle particle) {
        return particle.getConfig().getSimulationSpace() == ParticleConfig.Space.World
                && particle.getEmitter().getEffectExecutor() instanceof IWholeEffectTransformer whole
                ? whole : null;
    }

    public static Vector3f position(TileParticle particle, Vector3f position) {
        // World particles bake emitterToWorld at birth. Local/custom particles use simToWorld.
        // Applying the whole rotation here again rotates the spawn shape twice.
        return position;
    }

    public static Vector3f direction(TileParticle particle, Vector3f direction) {
        return direction;
    }

    public static Quaternionf rotation(TileParticle particle, Quaternionf rotation) {
        var whole = transformer(particle);
        return whole == null ? rotation : whole.applyWholeEffectRotation(new Quaternionf(rotation));
    }

    // Facing is authored in the pre-whole-effect frame. Solve it there, then rotate ALL
    // resulting geometry (including animated rotation and stretch offsets) exactly once.
    public static Vector3f referencePosition(TileParticle particle, Vector3f worldPosition) {
        var rendered = position(particle, worldPosition);
        return particle.getEmitter().getEffectExecutor() instanceof IWholeEffectTransformer whole
                ? whole.removeWholeEffectPosition(new Vector3f(rendered)) : rendered;
    }

    public static Vector3f referenceDirection(TileParticle particle, Vector3f worldDirection) {
        var rendered = direction(particle, worldDirection);
        return particle.getEmitter().getEffectExecutor() instanceof IWholeEffectTransformer whole
                ? whole.applyWholeEffectRotation(new Quaternionf()).invert().transform(new Vector3f(rendered)) : rendered;
    }

    public static Quaternionf referenceRotation(TileParticle particle, Quaternionf worldRotation) {
        var rendered = rotation(particle, worldRotation);
        return particle.getEmitter().getEffectExecutor() instanceof IWholeEffectTransformer whole
                ? whole.applyWholeEffectRotation(new Quaternionf()).invert().mul(rendered) : rendered;
    }
}
