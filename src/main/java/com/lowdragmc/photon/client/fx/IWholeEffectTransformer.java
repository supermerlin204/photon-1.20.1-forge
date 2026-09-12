package com.lowdragmc.photon.client.fx;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Optional render-time transform used only by standalone whole-FX executors. */
public interface IWholeEffectTransformer {
    Quaternionf applyWholeEffectRotation(Quaternionf particleRotation);

    /** Preserve authored animation in the frame carried by the whole effect. */
    default Quaternionf applyAnimatedBillboardRotation(Quaternionf facing, Vector3f animation) {
        return applyWholeEffectRotation(new Quaternionf(facing).rotateXYZ(animation.x, animation.y, animation.z));
    }

    Vector3f applyWholeEffectPosition(Vector3f worldPosition);

    /** Rotate a direction without applying the effect's pivot/translation. */
    default Vector3f applyWholeEffectDirection(Vector3f worldDirection) {
        return applyWholeEffectRotation(new Quaternionf()).transform(worldDirection);
    }

    /** Undo the rigid render transform, including its pivot translation. */
    default Vector3f removeWholeEffectPosition(Vector3f renderedPosition) {
        var translation = applyWholeEffectPosition(new Vector3f());
        return applyWholeEffectRotation(new Quaternionf()).invert().transform(renderedPosition.sub(translation));
    }
}
