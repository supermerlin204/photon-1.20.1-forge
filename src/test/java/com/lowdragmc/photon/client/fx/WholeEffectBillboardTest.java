package com.lowdragmc.photon.client.fx;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WholeEffectBillboardTest {
    @Test void defaultBillboardKeepsCameraOrientationWhilePositionStillRotates() {
        var camera = new Quaternionf().rotateY(0.4f).rotateX(-0.3f);
        var cameraBefore = new Quaternionf(camera);
        var animation = new Vector3f(0, 0, 0.6f);
        var expected = new Quaternionf(camera).rotateXYZ(animation.x, animation.y, animation.z);
        for (float yaw : new float[]{0, 0.8f, 1.57f, 3.14f}) {
            var q = new Quaternionf().rotateY(yaw).rotateZ(0.5f);
            var whole = transform(q, new Vector3f());
            var actual = whole.applyAnimatedBillboardRotation(camera, animation, true);
            for (var axis : new Vector3f[]{new Vector3f(1,0,0),new Vector3f(0,1,0),new Vector3f(0,0,1)}) {
                assertTrue(expected.transform(new Vector3f(axis)).distance(actual.transform(new Vector3f(axis))) < 0.0001f);
            }
            assertTrue(q.transform(new Vector3f(2,1,3)).distance(whole.applyWholeEffectPosition(new Vector3f(2,1,3))) < 0.0001f);
            assertEquals(whole.applyAnimatedBillboardRotation(camera, animation),
                    whole.applyAnimatedBillboardRotation(camera, animation, false));
        }
        assertEquals(cameraBefore, camera);
    }
    @Test void localModelYSlashFollowsTiltedWholeAxes() {
        var authoredSpace = new Quaternionf().rotateX(0.2f).rotateZ(-0.15f);
        for (float yaw : new float[]{0, 0.8f, 1.57f, 3.14f}) {
            var q = new Quaternionf().rotateY(yaw).rotateZ((float)Math.toRadians(30));
            var whole = transform(q, new Vector3f());
            var inheritedSpace = new Quaternionf(q).mul(authoredSpace);
            for (float angle : new float[]{0, 0.4f, 1.2f, 2.4f, 4.5f}) {
                var animation = new Vector3f(0, angle, 0);
                var baseline = new Quaternionf().rotateY(angle).mul(authoredSpace);
                var local = whole.applyAnimatedModelRotation(inheritedSpace, animation, true);
                var world = whole.applyAnimatedModelRotation(authoredSpace, animation, false);
                for (var vertex : new Vector3f[]{new Vector3f(1,0,0),new Vector3f(0,1,0),new Vector3f(0,0,1)}) {
                    var expected = q.transform(baseline.transform(new Vector3f(vertex)));
                    assertTrue(expected.distance(local.transform(new Vector3f(vertex))) < 0.0001f);
                    assertTrue(expected.distance(world.transform(new Vector3f(vertex))) < 0.0001f);
                }
            }
            assertEquals(new Quaternionf(q).mul(authoredSpace), inheritedSpace, "shared simulation transform must not mutate");
            var wrong = new Quaternionf().rotateY(1.2f).mul(inheritedSpace).transform(new Vector3f(0,1,0));
            var correct = whole.applyAnimatedModelRotation(inheritedSpace,new Vector3f(0,1.2f,0),true).transform(new Vector3f(0,1,0));
            assertTrue(wrong.distance(correct) > 0.1f, "fixture must detect old animation-before-whole bug");
        }
    }
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
