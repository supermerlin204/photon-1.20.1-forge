package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.nio.FloatBuffer;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ModelOrderOptimizationTest {
    private final Vector3f pivot = new Vector3f(.1f, -.2f, .03f);
    private final Matrix4f projection = new Matrix4f().perspective(1.1f, 1.3f, .1f, 100);

    private ModelTriangleOrder.Plan compare(ModelTriangleOrder.Workspace workspace, ModelOrderFixture mesh,
                                           FloatBuffer instances, int count, Matrix4f view, Matrix4f projection) {
        float[] original = instances.array().clone();
        var expected = ReferenceModelTriangleOrder.build(mesh.centers(), mesh.triangles(), mesh.vertices(),
                instances, 15, count, pivot, view, projection);
        var actual = workspace.build(mesh.centers(), mesh.triangles(), mesh.vertices(),
                instances, 15, count, pivot, view, projection);
        assertArrayEquals(expected.indices(), actual.indices());
        assertEquals(expected.crossingPairs(), actual.crossingPairs());
        assertEquals(expected.cycleBreaks(), actual.cycleBreaks());
        assertEquals(expected.runs().size(), actual.runs().size());
        for (int i = 0; i < expected.runs().size(); i++) {
            var a = actual.runs().get(i); var e = expected.runs().get(i);
            assertEquals(e.instance(), a.instance()); assertEquals(e.firstIndex(), a.firstIndex());
            assertEquals(e.indexCount(), a.indexCount());
        }
        assertArrayEquals(original, instances.array()); assertEquals(0, instances.position());
        return actual;
    }

    @Test void animatedViewsCountsScalesAndRotationsMatchFrozenReferenceExactly() {
        var workspace = new ModelTriangleOrder.Workspace(); var mesh = ModelOrderFixture.shell();
        for (int frame = 0; frame < 120; frame++) {
            int count = frame % 5; var instances = ModelOrderFixture.instances(count, frame%2 == 0);
            if (count > 0) instances.put(3, frame%3 == 0 ? -1 : .7f).put(4, frame%7 == 0 ? 0 : 1.5f);
            compare(workspace, mesh, instances, count, ModelOrderFixture.view(frame), projection);
        }
    }

    @Test void nearPlaneNonFiniteDegenerateDuplicatePositionsAndProjectionChangesMatch() {
        var workspace = new ModelTriangleOrder.Workspace(); var random = new Random(928345);
        for (int frame = 0; frame < 120; frame++) {
            float[] vertices = new float[90]; int[] indices = new int[30];
            for (int i = 0; i < vertices.length; i++) vertices[i] = random.nextFloat()*4-2;
            for (int i = 0; i < indices.length; i++) indices[i] = i;
            System.arraycopy(vertices, 0, vertices, 9, 9); // exact duplicate positions
            if (frame%3 == 0) vertices[0] = Float.NaN;
            if (frame%4 == 0) vertices[8] = Float.POSITIVE_INFINITY;
            if (frame%5 == 0) System.arraycopy(vertices, 18, vertices, 21, 3);
            var mesh = ModelOrderFixture.of(vertices, indices);
            var instances = ModelOrderFixture.instances(3, false);
            compare(workspace, mesh, instances, 3, new Matrix4f().translate(0,0,-frame*.03f),
                    frame%3 == 0 ? null : frame%3 == 1 ? projection : new Matrix4f().ortho(-3,3,-3,3,.1f,10));
        }
    }

    @Test void onlyExactGeometryInputsHitCacheAndUnchangedIndicesDoNotRequireUpload() {
        var workspace = new ModelTriangleOrder.Workspace(); var mesh = ModelOrderFixture.shell();
        var instances = ModelOrderFixture.instances(1, false); var view = ModelOrderFixture.view(0);
        var first = compare(workspace, mesh, instances, 1, view, projection);
        long revision = workspace.indexRevision();
        instances.put(10, .1f).put(13, .2f).put(14, 42); // color, alpha and light never affect order
        assertSame(first, compare(workspace, mesh, instances, 1, view, projection));
        assertEquals(revision, workspace.indexRevision());
        for (int c = 0; c < 10; c++) {
            instances.put(c, instances.get(c)+.01f);
            var next = compare(workspace, mesh, instances, 1, view, projection);
            assertNotSame(first, next); first = next;
        }
        view.rotateY(.2f);
        assertNotSame(first, first = compare(workspace, mesh, instances, 1, view, projection));
        pivot.x += .1f;
        assertNotSame(first, first = compare(workspace, mesh, instances, 1, view, projection));
        projection.m00(projection.m00()+.1f);
        assertNotSame(first, first = compare(workspace, mesh, instances, 1, view, projection));
        var reloaded = ModelOrderFixture.of(mesh.vertices().clone(), mesh.triangles().clone());
        assertNotSame(first, compare(workspace, reloaded, instances, 1, view, projection));
        long before = workspace.indexRevision();
        compare(workspace, reloaded, instances, 1, view, new Matrix4f(projection).m00(projection.m00()*1.001f));
        assertEquals(before, workspace.indexRevision(), "unchanged index data must not upload again");
    }

    @Test void changingInstanceOrderStillUpdatesRunsWhenIndexBytesStayIdentical() {
        var workspace = new ModelTriangleOrder.Workspace();
        var mesh = ModelOrderFixture.of(new float[]{0,0,0, 1,0,0, 0,1,0}, new int[]{0,1,2});
        var instances = ModelOrderFixture.instances(2, false);
        instances.put(2, -2).put(17, -4);
        var first = compare(workspace, mesh, instances, 2, new Matrix4f(), projection);
        assertEquals(1, first.runs().get(0).instance());
        long revision = workspace.indexRevision();
        instances.put(2, -6);
        var second = compare(workspace, mesh, instances, 2, new Matrix4f(), projection);
        assertEquals(0, second.runs().get(0).instance());
        assertEquals(revision, workspace.indexRevision());
    }

    @Test void conservativeDiagonalBoundsPreserveClippingResultsAcrossScalesAndWindings() {
        var random = new Random(481024);
        double[] a = new double[9], b = new double[9], scratchA = new double[20], scratchB = new double[20];
        for (int trial = 0; trial < 50_000; trial++) {
            double scale = Math.pow(10, trial%11-5);
            for (int i = 0; i < 9; i++) {
                a[i] = (random.nextDouble()*2-1) * (i%3 == 2 ? 1 : scale);
                b[i] = (random.nextDouble()*2-1) * (i%3 == 2 ? 1 : scale);
            }
            if (trial%3 == 0) System.arraycopy(a, 0, b, 0, 6); // shared edges and almost coplanar cases
            int expected = ReferenceProjectedTriangleOrder.relation(ReferenceProjectedTriangleOrder.Triangle.of(a),
                    ReferenceProjectedTriangleOrder.Triangle.of(b), scratchA, scratchB);
            int actual = ProjectedTriangleOrder.relation(ProjectedTriangleOrder.Triangle.of(a),
                    ProjectedTriangleOrder.Triangle.of(b), scratchA, scratchB);
            assertEquals(expected, actual, "trial=" + trial);
        }
    }
}
