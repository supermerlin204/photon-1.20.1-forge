package com.lowdragmc.photon.client.fx;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WholeEffectBillboardTest {
    private static IWholeEffectTransformer transform(Quaternionf q, Vector3f pivot) {
        return new IWholeEffectTransformer() {
            public Quaternionf applyWholeEffectRotation(Quaternionf rotation) { return new Quaternionf(q).mul(rotation); }
            public Vector3f applyWholeEffectPosition(Vector3f pos) { return q.transform(pos.sub(pivot)).add(pivot); }
        };
    }

    @Test void animatedBladeAxesFollowTheWholeRotationAtEverySample() {
        for (float yaw : new float[]{0, 0.7f, 1.57f, 3.14f}) {
            var q = new Quaternionf().rotateY(yaw).rotateZ(0.6f);
            var whole = transform(q, new Vector3f(4, 2, -7));
            var camera = new Quaternionf().rotateY(0.4f).rotateX(-0.3f);
            var originalCamera = new Quaternionf(camera);
            for (float t : new float[]{0, 0.25f, 0.5f, 0.75f, 1}) {
                var animation = new Vector3f(t * 2, t * 3, t * 6);
                var authored = new Quaternionf(camera).rotateXYZ(animation.x, animation.y, animation.z);
                var actual = whole.applyAnimatedBillboardRotation(camera, animation);
                for (var axis : new Vector3f[]{new Vector3f(1,0,0),new Vector3f(0,1,0),new Vector3f(0,0,1)}) {
                    var expected = q.transform(authored.transform(new Vector3f(axis)));
                    assertTrue(expected.distance(actual.transform(new Vector3f(axis))) < 0.0001f);
                }
            }
            assertEquals(originalCamera, camera, "shared camera orientation must not mutate");
        }
    }

    @Test void referenceSpaceRoundtripAndStretchOffsetUseSameRigidTransform() {
        var q = new Quaternionf().rotateY(1.1f).rotateX(0.4f).rotateZ(0.8f);
        var whole = transform(q, new Vector3f(123, -9, 32));
        var p = new Vector3f(124, -7, 30);
        assertTrue(p.distance(whole.removeWholeEffectPosition(whole.applyWholeEffectPosition(new Vector3f(p)))) < 0.0001f);
        var frame = com.lowdragmc.photon.client.gameobject.particle.renderer.StretchedBillboardMath.compute(
                new Vector3f(2,1,0), p, 130, 2, 50, new Vector3f(1), new Vector3f(1), 2, 1);
        var rotatedRight = whole.applyWholeEffectRotation(frame.rotation()).transform(new Vector3f(1,0,0));
        var rotatedOffset = whole.applyWholeEffectDirection(new Vector3f(frame.offsetX(),frame.offsetY(),frame.offsetZ()));
        assertTrue(rotatedOffset.distance(rotatedRight.mul(frame.stretchedSizeX()-1)) < 0.0001f);
    }
}
