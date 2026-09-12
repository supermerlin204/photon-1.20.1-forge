package com.lowdragmc.photon.client.gameobject.particle.renderer;

import it.unimi.dsi.fastutil.ints.IntArrays;
import org.joml.Matrix4fc;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Port-specific: view-space painter order, refined by projected overlap when geometry is available.
 * Original instance record IDs are retained. Actual intersections still need splitting or OIT. */
final class ModelTriangleOrder {
    record Run(int instance, int firstIndex, int indexCount) {}
    record Plan(int[] indices, List<Run> runs, int crossingPairs, int cycleBreaks) {}

    static Plan build(float[] centers, int[] triangles, FloatBuffer instances, int stride,
                      int count, Vector3f pivot, Matrix4fc modelView) {
        return build(centers, triangles, null, instances, stride, count, pivot, modelView, null);
    }

    static Plan build(float[] centers, int[] triangles, float[] vertices, FloatBuffer instances, int stride,
                      int count, Vector3f pivot, Matrix4fc modelView, Matrix4fc projection) {
        return new Workspace().build(centers, triangles, vertices, instances, stride, count, pivot, modelView, projection);
    }

    /** One workspace per renderer, render thread only. Geometry arrays must be immutable for their
     * lifetime (the renderer replaces them on mesh reload). Plans are borrowed until the next build.
     * No camera quantization/frame skipping: only exactly identical geometry inputs reuse a plan. */
    static final class Workspace {
        private final ProjectedTriangleOrder.Workspace projected = new ProjectedTriangleOrder.Workspace();
        private int[] order = new int[0], indices = new int[0];
        private float[] depth = new float[0], transforms = new float[0];
        private final Vector3f point = new Vector3f(), lastPivot = new Vector3f();
        private final Quaternionf rotation = new Quaternionf();
        private final Matrix4f lastView = new Matrix4f(), lastProjection = new Matrix4f();
        private float[] lastCenters, lastVertices;
        private int[] lastTriangles;
        private int lastCount = -1;
        private boolean hadProjection;
        private Plan plan;
        private final ArrayList<Run> runs = new ArrayList<>();
        private final List<Run> readOnlyRuns = Collections.unmodifiableList(runs);
        private long indexRevision;

        long indexRevision() { return indexRevision; }

        private boolean unchanged(float[] centers, int[] triangles, float[] vertices, FloatBuffer instances,
                                  int stride, int count, Vector3f pivot, Matrix4fc view, Matrix4fc projection) {
            if (plan == null || centers != lastCenters || triangles != lastTriangles || vertices != lastVertices
                    || count != lastCount || !lastPivot.equals(pivot) || !lastView.equals(view)
                    || (projection != null) != hadProjection
                    || (projection != null && !lastProjection.equals(projection))) return false;
            for (int i = 0; i < count; i++) for (int c = 0; c < 10; c++) {
                if (Float.floatToRawIntBits(transforms[i*10+c]) != Float.floatToRawIntBits(instances.get(i*stride+c))) return false;
            }
            return true;
        }

        private void run(int slot, int instance, int start, int length) {
            if (slot == runs.size()) runs.add(new Run(instance, start, length));
            else {
                Run old = runs.get(slot);
                if (old.instance != instance || old.firstIndex != start || old.indexCount != length)
                    runs.set(slot, new Run(instance, start, length));
            }
        }

        Plan build(float[] centers, int[] triangles, float[] vertices, FloatBuffer instances, int stride,
                   int count, Vector3f pivot, Matrix4fc modelView, Matrix4fc projection) {
            if (unchanged(centers, triangles, vertices, instances, stride, count, pivot, modelView, projection)) return plan;
            int perInstance = centers.length / 3;
            int total = Math.multiplyExact(perInstance, count);
            if (order.length < total) {
                int capacity = Math.max(total, order.length + (order.length >> 1));
                order = new int[capacity]; depth = new float[capacity];
            }
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
            IntArrays.quickSort(order, 0, total, (a, b) -> {
                int comparison = Float.compare(depth[a], depth[b]);
                return comparison != 0 ? comparison : Integer.compare(a, b);
            });
            int[] sorted = order;
            int crossingPairs = 0, cycleBreaks = 0;
            if (vertices != null && projection != null) {
                var refined = projected.refine(vertices, triangles, instances, stride, count,
                        pivot, modelView, projection, order);
                sorted = refined.order();
                crossingPairs = refined.crossingPairs();
                cycleBreaks = refined.cycleBreaks();
            }
            int indexCount = Math.multiplyExact(total, 3);
            boolean indicesChanged = indices.length != indexCount || plan == null;
            if (indices.length != indexCount) indices = new int[indexCount];
            int runCount = 0;
            int previousInstance = -1;
            int start = 0;
            for (int i = 0; i < total; i++) {
                int instance = sorted[i] / perInstance;
                if (instance != previousInstance) {
                    if (previousInstance >= 0) run(runCount++, previousInstance, start, i * 3 - start);
                    previousInstance = instance;
                    start = i * 3;
                }
                int source = sorted[i] % perInstance * 3;
                for (int c = 0; c < 3; c++) {
                    int value = triangles[source+c], target = i*3+c;
                    indicesChanged |= indices[target] != value;
                    indices[target] = value;
                }
            }
            if (previousInstance >= 0) run(runCount++, previousInstance, start, total * 3 - start);
            while (runs.size() > runCount) runs.remove(runs.size()-1);
            if (indicesChanged) indexRevision++;
            if (transforms.length < count*10) transforms = new float[count*10];
            for (int i = 0; i < count; i++) for (int c = 0; c < 10; c++) transforms[i*10+c] = instances.get(i*stride+c);
            lastCenters = centers; lastTriangles = triangles; lastVertices = vertices; lastCount = count;
            lastPivot.set(pivot); lastView.set(modelView); hadProjection = projection != null;
            if (projection != null) lastProjection.set(projection);
            plan = new Plan(indices, readOnlyRuns, crossingPairs, cycleBreaks);
            return plan;
        }
    }
}
