package com.lowdragmc.photon.client.gameobject.particle.renderer;

import com.sun.management.ThreadMXBean;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/** Opt-in, MC-free CPU/allocation probe, NOT an FPS benchmark or a timing-sensitive unit test.
 * Pass the blade OBJ path to use the user's real model; otherwise uses a 384-triangle shell. */
public class ModelOrderPerformanceProbe {
    private static volatile int consumed;

    public static void main(String[] args) throws Exception {
        var mesh = args.length == 0 ? ModelOrderFixture.shell() : ModelOrderFixture.obj(Path.of(args[0]));
        System.out.println("Geometry: " + mesh.vertices().length/3 + " vertices, " + mesh.triangles().length/3 + " triangles");
        System.out.println("Scenario, implementation, median_us, p95_us, allocated_bytes/call");
        for (int scenario = 0; scenario < 4; scenario++) {
            int count = scenario == 0 ? 1 : scenario == 2 ? 20 : 7;
            boolean separated = scenario == 2, stationary = scenario == 3;
            String name = count + (stationary ? " static" : separated ? " separated moving" : " overlapping moving");
            var instances = ModelOrderFixture.instances(count, separated);
            var views = new Matrix4f[120];
            for (int i = 0; i < views.length; i++) views[i] = ModelOrderFixture.view(stationary ? 0 : i);
            var projection = new Matrix4f().perspective(1.1f, 1.3f, .1f, 100);
            var pivot = new Vector3f(); var workspace = new ModelTriangleOrder.Workspace();
            // Check the actual benchmark input set, including run boundaries and diagnostic counts.
            for (var view : views) {
                var ref = ReferenceModelTriangleOrder.build(mesh.centers(), mesh.triangles(), mesh.vertices(), instances,15,count,pivot,view,projection);
                var opt = workspace.build(mesh.centers(), mesh.triangles(), mesh.vertices(), instances,15,count,pivot,view,projection);
                if (!Arrays.equals(ref.indices(), opt.indices()) || ref.crossingPairs() != opt.crossingPairs()
                        || ref.cycleBreaks() != opt.cycleBreaks() || ref.runs().size() != opt.runs().size())
                    throw new AssertionError("Order mismatch: " + name);
                for (int i = 0; i < ref.runs().size(); i++) {
                    var a = ref.runs().get(i); var b = opt.runs().get(i);
                    if (a.instance() != b.instance() || a.firstIndex() != b.firstIndex() || a.indexCount() != b.indexCount())
                        throw new AssertionError("Run mismatch: " + name);
                }
            }
            java.util.function.IntConsumer reference = frame -> {
                var p = ReferenceModelTriangleOrder.build(mesh.centers(),mesh.triangles(),mesh.vertices(),instances,15,count,pivot,views[frame%120],projection);
                consumed = p.indices()[0] ^ p.runs().size();
            };
            java.util.function.IntConsumer optimized = frame -> {
                var p = workspace.build(mesh.centers(),mesh.triangles(),mesh.vertices(),instances,15,count,pivot,views[frame%120],projection);
                consumed = p.indices()[0] ^ p.runs().size();
            };
            for (int i = 0; i < 360; i++) { reference.accept(i); optimized.accept(i); }
            var bean = (ThreadMXBean)ManagementFactory.getThreadMXBean();
            bean.setThreadAllocatedMemoryEnabled(true); long thread = Thread.currentThread().getId();
            long[][] samples = new long[2][600]; long[] allocated = new long[2];
            // Alternate measurement order, so neither implementation always benefits from first slot.
            for (int frame = 0; frame < 600; frame++) for (int slot = 0; slot < 2; slot++) {
                int which = (frame+slot)%2;
                long bytes = bean.getThreadAllocatedBytes(thread), start = System.nanoTime();
                (which == 0 ? reference : optimized).accept(frame);
                samples[which][frame] = System.nanoTime()-start;
                allocated[which] += bean.getThreadAllocatedBytes(thread)-bytes;
            }
            for (int i = 0; i < 2; i++) {
                Arrays.sort(samples[i]);
                System.out.printf(Locale.ROOT, "%s, %s, %.3f, %.3f, %.1f%n", name, i == 0 ? "reference" : "optimized",
                        samples[i][300]/1000.0, samples[i][569]/1000.0, allocated[i]/600.0);
            }
        }
    }
}
