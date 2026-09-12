package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;

/** MC-free fixtures shared by equivalence tests and the opt-in performance probe. */
record ModelOrderFixture(float[] vertices, int[] triangles, float[] centers) {
    static ModelOrderFixture of(float[] vertices, int[] indices) {
        float[] centers = new float[indices.length];
        for (int t = 0; t < indices.length; t += 3) for (int axis = 0; axis < 3; axis++)
            centers[t+axis] = (vertices[indices[t]*3+axis] + vertices[indices[t+1]*3+axis]
                    + vertices[indices[t+2]*3+axis]) / 3f;
        return new ModelOrderFixture(vertices, indices, centers);
    }

    static ModelOrderFixture shell() {
        int rings = 7, segments = 32;
        float[] vertices = new float[rings*segments*3];
        int[] indices = new int[(rings-1)*segments*6];
        for (int r = 0; r < rings; r++) for (int s = 0; s < segments; s++) {
            double a = s * Math.PI * 2 / segments;
            float radius = .35f + .15f*r;
            int v = (r*segments+s)*3;
            vertices[v] = radius*(float)Math.cos(a); vertices[v+1] = r*.16f;
            vertices[v+2] = radius*(float)Math.sin(a);
            if (r == rings-1) continue;
            int a0 = r*segments+s, b = r*segments+(s+1)%segments;
            int c = b+segments, d = a0+segments, i = (r*segments+s)*6;
            indices[i] = a0; indices[i+1] = b; indices[i+2] = c;
            indices[i+3] = c; indices[i+4] = d; indices[i+5] = a0;
        }
        return of(vertices, indices);
    }

    /** Benchmark-only OBJ reader; not an alternative production importer. */
    static ModelOrderFixture obj(Path path) throws IOException {
        var vertices = new ArrayList<Float>(); var indices = new ArrayList<Integer>();
        for (String line : Files.readAllLines(path)) {
            String[] s = line.trim().split("\\s+");
            if (s[0].equals("v")) for (int i = 1; i <= 3; i++) vertices.add(Float.parseFloat(s[i]));
            if (s[0].equals("f")) for (int i = 2; i < s.length-1; i++)
                for (int corner : new int[]{1, i, i+1}) {
                    int id = Integer.parseInt(s[corner].split("/")[0]);
                    indices.add(id > 0 ? id-1 : vertices.size()/3+id);
                }
        }
        float[] xyz = new float[vertices.size()];
        for (int i = 0; i < xyz.length; i++) xyz[i] = vertices.get(i);
        return of(xyz, indices.stream().mapToInt(Integer::intValue).toArray());
    }

    static FloatBuffer instances(int count, boolean separated) {
        var buffer = FloatBuffer.allocate(count*15);
        for (int i = 0; i < count; i++) {
            var q = new Quaternionf().rotateXYZ(i*.13f, i*.31f, i*.09f);
            buffer.put(new float[]{separated ? (i-count/2)*3f : i*.03f, 0, 0,
                    1,1,1, q.x,q.y,q.z,q.w, 1,1,1,1,0});
        }
        return buffer.flip();
    }

    static Matrix4f view(int frame) {
        double yaw = Math.toRadians(frame*3), elevation = Math.toRadians(25 + 30*Math.sin(frame*.1));
        return new Matrix4f().lookAt(new Vector3f((float)(5*Math.cos(elevation)*Math.sin(yaw)),
                        (float)(5*Math.sin(elevation)), (float)(5*Math.cos(elevation)*Math.cos(yaw))),
                new Vector3f(), new Vector3f(0,1,0));
    }
}
