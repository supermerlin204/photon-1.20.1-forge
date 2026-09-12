// Frozen, user-validated pre-optimization implementation. Test oracle only.
package com.lowdragmc.photon.client.gameobject.particle.renderer;

import it.unimi.dsi.fastutil.ints.IntArrays;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/** View-space painter order, refined by projected overlap when geometry is available.
 * Original instance record IDs are retained. Actual intersections still need splitting or OIT. */
final class ReferenceModelTriangleOrder {
    record Run(int instance, int firstIndex, int indexCount) {}
    record Plan(int[] indices, List<Run> runs, int crossingPairs, int cycleBreaks) {}

    static Plan build(float[] centers, int[] triangles, FloatBuffer instances, int stride,
                      int count, Vector3f pivot, Matrix4fc modelView) {
        return build(centers, triangles, null, instances, stride, count, pivot, modelView, null);
    }

    static Plan build(float[] centers, int[] triangles, float[] vertices, FloatBuffer instances, int stride,
                      int count, Vector3f pivot, Matrix4fc modelView, Matrix4fc projection) {
        int perInstance = centers.length / 3;
        int total = Math.multiplyExact(perInstance, count);
        int[] order = new int[total];
        float[] depth = new float[total];
        Vector3f point = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        for (int instance = 0; instance < count; instance++) {
            int base = instance * stride;
            rotation.set(instances.get(base + 6), instances.get(base + 7),
                    instances.get(base + 8), instances.get(base + 9));
            for (int t = 0; t < perInstance; t++) {
                point.set(centers[t * 3], centers[t * 3 + 1], centers[t * 3 + 2]).add(pivot);
                point.mul(instances.get(base + 3), instances.get(base + 4), instances.get(base + 5));
                rotation.transform(point);
                point.add(instances.get(base), instances.get(base + 1), instances.get(base + 2));
                point.mulPosition(modelView);
                int id = instance * perInstance + t;
                order[id] = id;
                depth[id] = Float.isFinite(point.z) ? point.z : 0;
            }
        }
        // Camera looks down -Z. Squared radius is NOT depth: a wide near triangle can
        // have a larger radius than a narrow far triangle, particularly in the editor.
        IntArrays.quickSort(order, (a, b) -> {
            int comparison = Float.compare(depth[a], depth[b]);
            return comparison != 0 ? comparison : Integer.compare(a, b);
        });
        int crossingPairs = 0, cycleBreaks = 0;
        if (vertices != null && projection != null) {
            var refined = ReferenceProjectedTriangleOrder.refine(vertices, triangles, instances, stride, count,
                    pivot, modelView, projection, order);
            order = refined.order();
            crossingPairs = refined.crossingPairs();
            cycleBreaks = refined.cycleBreaks();
        }
        int[] indices = new int[Math.multiplyExact(total, 3)];
        List<Run> runs = new ArrayList<>();
        int previousInstance = -1;
        int start = 0;
        for (int i = 0; i < total; i++) {
            int instance = order[i] / perInstance;
            if (instance != previousInstance) {
                if (previousInstance >= 0) runs.add(new Run(previousInstance, start, i * 3 - start));
                previousInstance = instance;
                start = i * 3;
            }
            System.arraycopy(triangles, order[i] % perInstance * 3, indices, i * 3, 3);
        }
        if (previousInstance >= 0) runs.add(new Run(previousInstance, start, total * 3 - start));
        return new Plan(indices, List.copyOf(runs), crossingPairs, cycleBreaks);
    }
}
