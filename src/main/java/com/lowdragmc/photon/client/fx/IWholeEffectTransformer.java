package com.lowdragmc.photon.client.fx;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Optional render-time transform used only by standalone whole-FX executors. */
public interface IWholeEffectTransformer {
    Quaternionf applyWholeEffectRotation(Quaternionf particleRotation);

    Vector3f applyWholeEffectPosition(Vector3f worldPosition);
}
