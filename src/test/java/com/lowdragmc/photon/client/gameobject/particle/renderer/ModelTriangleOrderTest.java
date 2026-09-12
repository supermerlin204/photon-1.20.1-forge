package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.nio.FloatBuffer;
import static org.junit.jupiter.api.Assertions.*;

class ModelTriangleOrderTest {
    private static FloatBuffer instances(float... z) {
        var b = FloatBuffer.allocate(z.length * 15);
        for (float depth : z) b.put(new float[]{0,0,depth, 1,1,1, 0,0,0,1, 1,1,1,1, 0});
        return b.flip();
    }

    @Test void wideNearFaceMustNotSortBehindNarrowFarFace() {
        var b = instances(0);
        var plan = ModelTriangleOrder.build(new float[]{10,0,-2, 0,0,-5}, new int[]{0,1,2, 4,5,6}, b,15,1,new Vector3f(),new Matrix4f());
        assertArrayEquals(new int[]{4,5,6,0,1,2}, plan.indices());
        assertEquals(0, b.position());
    }

    @Test void differentInstancesInterleaveWithoutChangingOriginalRecordIds() {
        var b = instances(0, -2);
        var original = b.array().clone();
        var plan = ModelTriangleOrder.build(new float[]{0,0,-1, 0,0,-4},new int[]{0,1,2,4,5,6},b,15,2,new Vector3f(),new Matrix4f());
        assertEquals(java.util.List.of(1,0,1,0),plan.runs().stream().map(ModelTriangleOrder.Run::instance).toList());
        assertArrayEquals(new int[]{4,5,6,4,5,6,0,1,2,0,1,2},plan.indices());
        assertArrayEquals(original,b.array());
    }

    @Test void usesActualScaleRotationPivotAndEditorViewMatrix() {
        var b = instances(0);
        b.put(3,2f);
        var q = new Quaternionf().rotateY((float)Math.PI / 2);
        b.put(6,q.x).put(7,q.y).put(8,q.z).put(9,q.w);
        float[] centers = {-1,0,0, 1,0,0};
        int[] indices = {0,1,2,4,5,6};
        var pivot = new Vector3f(0.3f,0,0);
        var plan = ModelTriangleOrder.build(centers,indices,b,15,1,pivot,new Matrix4f());
        assertArrayEquals(new int[]{4,5,6,0,1,2},plan.indices());
        var reversed = ModelTriangleOrder.build(centers,indices,b,15,1,pivot,new Matrix4f().rotateY((float)Math.PI));
        assertArrayEquals(indices,reversed.indices());
        assertEquals(new Vector3f(0.3f,0,0),pivot);
    }

    @Test void emptyGeometryAndEqualDepthAreDeterministic() {
        assertTrue(ModelTriangleOrder.build(new float[0],new int[0],instances(0),15,1,new Vector3f(),new Matrix4f()).runs().isEmpty());
        int[] indices = {0,1,2,4,5,6};
        assertArrayEquals(indices,ModelTriangleOrder.build(new float[]{0,0,-1,0,0,-1},indices,instances(0),15,1,new Vector3f(),new Matrix4f()).indices());
    }
}
