package com.lowdragmc.photon.client.fx;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Standalone FX executor that applies one orientation to a complete FX instance.
 *
 * <p>The executor may be bound to an entity for lifetime cleanup, but it deliberately does not
 * follow the entity after startup. Position and orientation are sampled once when the executor is
 * constructed. Local particles use the normal root transform; world-space particles bake it into
 * their spawn position and velocity. Render-time orientation preserves model animation axes, but
 * must not apply the pivot transform to those already-transformed positions a second time.</p>
 */
@OnlyIn(Dist.CLIENT)
public class WholeFXEffectExecutor extends EntityEffectExecutor implements IWholeEffectTransformer {
    /** Euler composition space for the standalone whole-FX rotation. */
    public enum RotationMode {
        /** X, Y and Z rotate around the fixed world axes. */
        GLOBAL,
        /** Y rotates first; X/Z then rotate around the resulting local axes. */
        LOCAL
    }

    private final Vector3f origin;
    private final Quaternionf baseRotation;

    /**
     * @param entity entity used for the initial world-space pivot and lifetime binding
     * @param wholeRotation orientation applied to the FX as a whole
     */
    public WholeFXEffectExecutor(FX fx, Level level, Entity entity, Quaternionf wholeRotation) {
        super(fx, level, entity, AutoRotate.NONE);
        this.origin = entity.getEyePosition().toVector3f();
        this.baseRotation = new Quaternionf(wholeRotation);
    }

    /**
     * Creates an executor from Euler angles in degrees.
     *
     * @param eulerDegrees X/Y/Z angles in degrees
     * @param mode         global fixed-axis or local rotated-axis composition
     */
    public WholeFXEffectExecutor(FX fx, Level level, Entity entity, Vector3f eulerDegrees,
                                 RotationMode mode) {
        this(fx, level, entity, composeEuler(eulerDegrees, mode));
    }

    private static Quaternionf composeEuler(Vector3f degrees, RotationMode mode) {
        float x = (float) Math.toRadians(degrees.x);
        float y = (float) Math.toRadians(degrees.y);
        float z = (float) Math.toRadians(degrees.z);
        if (mode == RotationMode.GLOBAL) {
            // Extrinsic order: apply X, then Y, then Z around unchanged world axes.
            return new Quaternionf().rotateZ(z).rotateY(y).rotateX(x);
        }
        // Intrinsic order: apply Y first, then X/Z around the axes carried by that rotation.
        return new Quaternionf().rotateY(y).rotateX(x).rotateZ(z);
    }

    private Quaternionf wholeRotation() {
        return new Quaternionf(baseRotation).mul(rotation);
    }

    private Vector3f pivot() {
        return new Vector3f(origin).add(offset);
    }

    @Override
    public Quaternionf applyWholeEffectRotation(Quaternionf particleRotation) {
        return wholeRotation().mul(particleRotation);
    }

    @Override
    public Vector3f applyWholeEffectPosition(Vector3f worldPosition) {
        return wholeRotation().transform(worldPosition.sub(pivot())).add(pivot());
    }

    @Override
    public void start() {
        if (!entity.isAlive()) return;
        var effects = CACHE.computeIfAbsent(entity, p -> new java.util.ArrayList<>());
        if (shouldSkipStart(effects)) return;
        resetFinishedNotification();
        runtime = fx.createRuntime();
        var root = runtime.getRoot();
        root.updatePos(pivot());
        root.updateRotation(wholeRotation());
        root.updateScale(scale);
        runtime.emit(this, delay);
        effects.add(this);
    }

    /** Keep the initial entity anchor fixed; only the inherited death/cache handling remains. */
    @Override
    public void updateFXObjectFrame(com.lowdragmc.photon.client.gameobject.IFXObject fxObject,
                                    float partialTicks) {
    }
}
