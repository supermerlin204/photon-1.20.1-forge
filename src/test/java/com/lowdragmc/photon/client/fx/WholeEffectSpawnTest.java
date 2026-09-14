package com.lowdragmc.photon.client.fx;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WholeEffectSpawnTest {
    @Test void worldSpawnShapeAndVelocityAreNotTransformedAgainAtRenderTime() {
        var pivot = new Vector3f(10, 2, -3);
        var authored = new Matrix4f().translation(2, 1, -1).rotateX(0.3f).scale(2, 1, 0.5f);
        for (float angle : new float[]{0, 0.5f, (float)Math.PI / 2, (float)Math.PI}) {
            var whole = new Quaternionf().rotateY(angle).rotateZ(0.4f);
            var emitterToWorld = new Matrix4f().translation(pivot).rotate(whole).mul(authored);
            for (var point : new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(0, 2, 0), new Vector3f(-1, 0, 3)}) {
                // The same emitterToWorld conversion used by TileParticle.setup for World space.
                var spawn = emitterToWorld.transformPosition(new Vector3f(point));
                var velocity = emitterToWorld.transformDirection(new Vector3f(point));
                var expected = whole.transform(authored.transformPosition(new Vector3f(point))).add(pivot);
                assertTrue(expected.distance(WholeEffectRenderSpace.position(null, spawn)) < 0.0001f);
                assertEquals(velocity, WholeEffectRenderSpace.direction(null, new Vector3f(velocity)));
                // Linear trajectories keep the whole-rotated birth velocity, without Q^2.
                var later = new Vector3f(spawn).fma(3f, velocity);
                assertEquals(later, WholeEffectRenderSpace.position(null, new Vector3f(later)));
                assertEquals(expected.x, spawn.x, 0.0001f);
            }
        }
    }
}
