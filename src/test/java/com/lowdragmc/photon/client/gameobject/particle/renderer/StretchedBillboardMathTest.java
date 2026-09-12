package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StretchedBillboardMathTest {
    private static final float EPS = 0.0001f;

    @Test void rotatedVelocityKeepsFrameAndTrailAlignedForDifferentCameras() {
        for (float yaw : new float[]{0, 0.8f, 1.57f, 3.14f}) {
            var rotation = new Quaternionf().rotateY(yaw).rotateX(0.4f).rotateZ(0.6f);
            var velocity = rotation.transform(new Vector3f(2, 0.3f, 0.1f));
            var position = rotation.transform(new Vector3f(3, 2, 1));
            for (var camera : new Vector3f[]{new Vector3f(9, 5, 7), new Vector3f(-5, 2, 4)}) {
                var frame = StretchedBillboardMath.compute(velocity, position, camera.x, camera.y, camera.z,
                        new Vector3f(1), new Vector3f(1), 2, 0.5f);
                var right = frame.rotation().transform(new Vector3f(1, 0, 0));
                assertTrue(right.distance(new Vector3f(velocity).normalize()) < EPS);
                var offset = new Vector3f(frame.offsetX(), frame.offsetY(), frame.offsetZ());
                assertTrue(offset.distance(new Vector3f(right).mul(frame.stretchedSizeX() - 1)) < EPS);
                // Camera projection is perpendicular to the stretch axis and aligned to face normal.
                var toCamera = new Vector3f(camera).sub(position);
                var projected = new Vector3f(toCamera).sub(new Vector3f(right).mul(toCamera.dot(right))).normalize();
                var normal = frame.rotation().transform(new Vector3f(0, 0, 1));
                assertTrue(normal.distance(projected) < EPS);
            }
        }
    }

    @Test void rigidRotationPreservesLengthAndRotatesOffsetWithGeometry() {
        var q = new Quaternionf().rotateY(1.2f).rotateZ(0.5f);
        var v = new Vector3f(3, 1, 0); var p = new Vector3f(1, 2, 3); var c = new Vector3f(8, 4, -2);
        var a = StretchedBillboardMath.compute(v, p, c.x, c.y, c.z, new Vector3f(2), new Vector3f(1), 2, 1);
        var rotatedCamera = q.transform(new Vector3f(c));
        var b = StretchedBillboardMath.compute(q.transform(new Vector3f(v)), q.transform(new Vector3f(p)),
                rotatedCamera.x, rotatedCamera.y, rotatedCamera.z, new Vector3f(2), new Vector3f(1), 2, 1);
        assertEquals(a.stretchedSizeX(), b.stretchedSizeX(), EPS);
        assertTrue(q.transform(new Vector3f(a.offsetX(), a.offsetY(), a.offsetZ()))
                .distance(new Vector3f(b.offsetX(), b.offsetY(), b.offsetZ())) < EPS);
    }

    @Test void stationaryAndParallelCameraCasesRemainFinite() {
        for (var velocity : new Vector3f[]{new Vector3f(), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0)}) {
            var f = StretchedBillboardMath.compute(velocity, new Vector3f(), velocity.x, velocity.y, velocity.z,
                    new Vector3f(1), new Vector3f(1), 2, 1);
            assertTrue(Float.isFinite(f.rotation().x) && Float.isFinite(f.rotation().y)
                    && Float.isFinite(f.rotation().z) && Float.isFinite(f.rotation().w));
            assertEquals(1, f.rotation().lengthSquared(), EPS);
        }
    }
}
