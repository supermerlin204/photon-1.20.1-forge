package com.lowdragmc.photon.client.gameobject.particle.renderer;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;

/** Port-specific: painter constraints from the intersection of projected triangles, not their centres.
 * No vertices are changed. Actual crossings and cyclic overlap still require splitting or OIT. */
final class ProjectedTriangleOrder {
    record Result(int[] order, int crossingPairs, int cycleBreaks) {}
    static final class Triangle {
        final double[] xy = new double[6];
        final double[] edges = new double[6];
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        double minSum, maxSum, minDifference, maxDifference;
        double zx, zy, z0;

        static Triangle of(double... xyz) {
            return new Triangle().set(xyz);
        }

        private Triangle set(double[] xyz) {
            Triangle t = this;
            minX = minY = Double.POSITIVE_INFINITY;
            maxX = maxY = Double.NEGATIVE_INFINITY;
            minSum = minDifference = Double.POSITIVE_INFINITY;
            maxSum = maxDifference = Double.NEGATIVE_INFINITY;
            for (int i=0;i<3;i++) {
                double x=xyz[i*3], y=xyz[i*3+1];
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(xyz[i*3+2])) return null;
                t.xy[i*2]=x; t.xy[i*2+1]=y;
                t.minX=Math.min(t.minX,x);t.maxX=Math.max(t.maxX,x);
                t.minY=Math.min(t.minY,y);t.maxY=Math.max(t.maxY,y);
                minSum = Math.min(minSum, x+y); maxSum = Math.max(maxSum, x+y);
                minDifference = Math.min(minDifference, x-y); maxDifference = Math.max(maxDifference, x-y);
            }
            // Conservative extra separating axes for long, oblique faces. Expand by one ULP
            // to enclose addition/subtraction rounding; uncertain/touching pairs still get clipped.
            minSum = Math.nextDown(minSum); maxSum = Math.nextUp(maxSum);
            minDifference = Math.nextDown(minDifference); maxDifference = Math.nextUp(maxDifference);
            double dx1=xyz[3]-xyz[0], dy1=xyz[4]-xyz[1], dz1=xyz[5]-xyz[2];
            double dx2=xyz[6]-xyz[0], dy2=xyz[7]-xyz[1], dz2=xyz[8]-xyz[2];
            double determinant=dx1*dy2-dx2*dy1;
            if (Math.abs(determinant)<1e-14) return null;
            t.zx=(dz1*dy2-dz2*dy1)/determinant;
            t.zy=(dx1*dz2-dx2*dz1)/determinant;
            t.z0=xyz[2]-t.zx*xyz[0]-t.zy*xyz[1];
            if(determinant<0) {
                double x=t.xy[2],y=t.xy[3];t.xy[2]=t.xy[4];t.xy[3]=t.xy[5];t.xy[4]=x;t.xy[5]=y;
            }
            for (int edge = 0; edge < 3; edge++) {
                int next = (edge+1)%3;
                edges[edge*2] = xy[next*2]-xy[edge*2];
                edges[edge*2+1] = xy[next*2+1]-xy[edge*2+1];
            }
            return t;
        }
    }

    /** 1 = a behind b; -1 = b behind a; 0 = no constraint; 2 = true depth crossing. */
    static int relation(Triangle a, Triangle b, double[] scratchA, double[] scratchB) {
        if (a==null || b==null || a.maxX<=b.minX || b.maxX<=a.minX || a.maxY<=b.minY || b.maxY<=a.minY) return 0;
        if (a.maxSum < b.minSum || b.maxSum < a.minSum
                || a.maxDifference < b.minDifference || b.maxDifference < a.minDifference) return 0;
        int count=3;
        double[] input=a.xy, output=scratchA;
        // Sutherland-Hodgman: intersection of two convex projected triangles, at most six corners.
        for(int edge=0;edge<3;edge++) {
            double x=b.xy[edge*2],y=b.xy[edge*2+1],dx=b.edges[edge*2],dy=b.edges[edge*2+1];
            int written=0;
            double px=input[(count-1)*2],py=input[(count-1)*2+1];
            double previous=dx*(py-y)-dy*(px-x);
            for(int i=0;i<count;i++) {
                double cx=input[i*2],cy=input[i*2+1],current=dx*(cy-y)-dy*(cx-x);
                if((current>=0)!=(previous>=0)) {
                    double f=previous/(previous-current);
                    output[written*2]=px+(cx-px)*f;output[written*2+1]=py+(cy-py)*f;written++;
                }
                if(current>=0){output[written*2]=cx;output[written*2+1]=cy;written++;}
                px=cx;py=cy;previous=current;
            }
            if(written<3)return 0;
            count=written;input=output;output=input==scratchA?scratchB:scratchA;
        }
        double area=0,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(int i=0;i<count;i++) {
            int next=i+1==count?0:i+1;
            area+=input[i*2]*input[next*2+1]-input[next*2]*input[i*2+1];
            double difference=(a.zx-b.zx)*input[i*2]+(a.zy-b.zy)*input[i*2+1]+a.z0-b.z0;
            min=Math.min(min,difference);max=Math.max(max,difference);
        }
        if(Math.abs(area)<1e-14)return 0; // shared edges alone impose no painter constraint
        double epsilon=1e-9;
        if(min < -epsilon && max > epsilon)return 2;
        if(max>epsilon)return 1;
        if(min < -epsilon)return -1;
        return 0;
    }

    static Result refine(Triangle[] triangles,int[] fallback) {
        return new Workspace().refine(triangles, fallback, triangles.length);
    }

    /** Render-thread-owned scratch storage. Results are borrowed until the next call.
     * Capacity is retained across particle-count changes, and released with the renderer. */
    static final class Workspace {
        private int[] sweep = new int[0], rank = new int[0], indegree = new int[0], head = new int[0];
        private int[] edgeTo = new int[0], edgeNext = new int[0], result = new int[0];
        private boolean[] emitted = new boolean[0];
        private final double[] scratchA = new double[20], scratchB = new double[20], corners = new double[9];
        private final IntHeapPriorityQueue ready = new IntHeapPriorityQueue((a,b) -> Integer.compare(rank[a], rank[b]));
        private Triangle[] projected = new Triangle[0], pool = new Triangle[0];
        private final Quaternionf q = new Quaternionf();
        private final Vector3f p = new Vector3f();
        private final Vector4f clip = new Vector4f();
        private float[] sourceVertices, uniqueVertices = new float[0];
        private int[] sourceIndices, uniqueIndices = new int[0];
        private double[] positions = new double[0];
        private boolean[] usable = new boolean[0];

        private void ensureCapacity(int n) {
            if (sweep.length < n) {
                int capacity = Math.max(n, sweep.length + (sweep.length >> 1));
                sweep = new int[capacity]; rank = new int[capacity]; indegree = new int[capacity];
                head = new int[capacity]; emitted = new boolean[capacity];
                projected = new Triangle[capacity]; pool = Arrays.copyOf(pool, capacity);
            }
            // Public Result has no active-length field; exact size only changes with particle count.
            if (result.length != n) result = new int[n];
        }

        Result refine(Triangle[] triangles, int[] fallback, int n) {
            ensureCapacity(n);
            Arrays.fill(indegree, 0, n, 0);
            Arrays.fill(head, 0, n, -1);
            Arrays.fill(emitted, 0, n, false);
            ready.clear();
            for(int i=0;i<n;i++){sweep[i]=i;rank[fallback[i]]=i;}
            IntArrays.quickSort(sweep,0,n,(a,b)->Double.compare(triangles[a]==null?Double.POSITIVE_INFINITY:triangles[a].minX,
                    triangles[b]==null?Double.POSITIVE_INFINITY:triangles[b].minX));
            int crossings=0, edges=0;
            for(int i=0;i<n;i++) {
                int a=sweep[i];if(triangles[a]==null)break;
                for(int j=i+1;j<n;j++) {
                    int b=sweep[j];
                    if(triangles[b]==null || triangles[b].minX>=triangles[a].maxX)break;
                    int relation=relation(triangles[a],triangles[b],scratchA,scratchB);
                    if(relation==0)continue;
                    if(relation==2){crossings++;continue;}
                    int far=relation==1?a:b, near=relation==1?b:a;
                    if (edges == edgeTo.length) {
                        int capacity = Math.max(64, edges + (edges >> 1));
                        edgeTo = Arrays.copyOf(edgeTo, capacity);
                        edgeNext = Arrays.copyOf(edgeNext, capacity);
                    }
                    edgeTo[edges] = near; edgeNext[edges] = head[far]; head[far] = edges++;
                    indegree[near]++;
                }
            }
            for(int i=0;i<n;i++)if(indegree[i]==0)ready.enqueue(i);
            int cycles=0,cursor=0;
            for(int i=0;i<n;i++) {
                if(ready.isEmpty()) {
                    while(emitted[fallback[cursor]])cursor++;
                    ready.enqueue(fallback[cursor]);cycles++;
                }
                int id=ready.dequeueInt();emitted[id]=true;result[i]=id;
                for (int edge = head[id]; edge != -1; edge = edgeNext[edge]) {
                    int next = edgeTo[edge];
                    if (!emitted[next] && --indegree[next] == 0) ready.enqueue(next);
                }
            }
            return new Result(result,crossings,cycles);
        }

        // Exact float-bit keys: never merge nearby vertices or +/- zero. UV and normals do not
        // affect projected ordering; their original VBO records and EBO indices remain untouched.
        private record Position(int x, int y, int z) {}

        private void geometry(float[] vertices, int[] indices) {
            if (vertices == sourceVertices && indices == sourceIndices) return;
            sourceVertices = vertices; sourceIndices = indices;
            var ids = new HashMap<Position, Integer>();
            float[] unique = new float[vertices.length];
            uniqueIndices = new int[indices.length];
            for (int i = 0; i < indices.length; i++) {
                int offset = indices[i] * 3;
                var key = new Position(Float.floatToRawIntBits(vertices[offset]),
                        Float.floatToRawIntBits(vertices[offset + 1]), Float.floatToRawIntBits(vertices[offset + 2]));
                Integer id = ids.get(key);
                if (id == null) {
                    id = ids.size(); ids.put(key, id);
                    System.arraycopy(vertices, offset, unique, id * 3, 3);
                }
                uniqueIndices[i] = id;
            }
            uniqueVertices = Arrays.copyOf(unique, ids.size() * 3);
            positions = new double[uniqueVertices.length];
            usable = new boolean[ids.size()];
        }

        Result refine(float[] vertices,int[] indices,FloatBuffer instances,int stride,int count,
                         Vector3f pivot,Matrix4fc view,Matrix4fc projection,int[] fallback) {
            int perInstance=indices.length/3;
            int total = Math.multiplyExact(perInstance, count);
            ensureCapacity(total);
            geometry(vertices, indices);
            for(int instance=0;instance<count;instance++) {
                int base=instance*stride;
                q.set(instances.get(base+6),instances.get(base+7),instances.get(base+8),instances.get(base+9));
                for (int vertex = 0; vertex < usable.length; vertex++) {
                    int index = vertex * 3;
                    // Keep the original float arithmetic order, including view then projection.
                    p.set(uniqueVertices[index], uniqueVertices[index+1], uniqueVertices[index+2]).add(pivot)
                            .mul(instances.get(base+3), instances.get(base+4), instances.get(base+5));
                    q.transform(p); p.add(instances.get(base), instances.get(base+1), instances.get(base+2)).mulPosition(view);
                    clip.set(p, 1).mul(projection);
                    usable[vertex] = !(clip.w <= 0 || clip.z < -clip.w);
                    positions[index] = (double)clip.x / clip.w;
                    positions[index+1] = (double)clip.y / clip.w;
                    positions[index+2] = (double)clip.z / clip.w;
                }
                for(int t=0;t<perInstance;t++) {
                    boolean valid=true;
                    for(int corner=0;corner<3;corner++) {
                        // Near-plane crossings need polygon clipping. Keep the conservative fallback
                        // for those, never manufacture an ordering from a negative perspective divisor.
                        int vertex = uniqueIndices[t*3+corner];
                        if (!usable[vertex]) { valid=false; break; }
                        System.arraycopy(positions, vertex*3, corners, corner*3, 3);
                    }
                    int id = instance*perInstance+t;
                    if (valid) {
                        if (pool[id] == null) pool[id] = new Triangle();
                        projected[id] = pool[id].set(corners);
                    } else projected[id] = null;
                }
            }
            return refine(projected,fallback,total);
        }
    }

    static Result refine(float[] vertices,int[] indices,FloatBuffer instances,int stride,int count,
                         Vector3f pivot,Matrix4fc view,Matrix4fc projection,int[] fallback) {
        return new Workspace().refine(vertices, indices, instances, stride, count, pivot, view, projection, fallback);
    }
}
