// Frozen, user-validated pre-optimization implementation. Test oracle only.
package com.lowdragmc.photon.client.gameobject.particle.renderer;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.FloatBuffer;
import java.util.PriorityQueue;

/** Painter constraints from the intersection of projected triangles, not their centres.
 * No vertices are changed. Actual crossings and cyclic overlap still require splitting or OIT. */
final class ReferenceProjectedTriangleOrder {
    record Result(int[] order, int crossingPairs, int cycleBreaks) {}
    static final class Triangle {
        final double[] xy = new double[6];
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        double zx, zy, z0;

        static Triangle of(double... xyz) {
            Triangle t = new Triangle();
            for (int i=0;i<3;i++) {
                double x=xyz[i*3], y=xyz[i*3+1];
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(xyz[i*3+2])) return null;
                t.xy[i*2]=x; t.xy[i*2+1]=y;
                t.minX=Math.min(t.minX,x);t.maxX=Math.max(t.maxX,x);
                t.minY=Math.min(t.minY,y);t.maxY=Math.max(t.maxY,y);
            }
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
            return t;
        }
    }

    /** 1 = a behind b; -1 = b behind a; 0 = no constraint; 2 = true depth crossing. */
    static int relation(Triangle a, Triangle b, double[] scratchA, double[] scratchB) {
        if (a==null || b==null || a.maxX<=b.minX || b.maxX<=a.minX || a.maxY<=b.minY || b.maxY<=a.minY) return 0;
        System.arraycopy(a.xy,0,scratchA,0,6);
        int count=3;
        double[] input=scratchA, output=scratchB;
        // Sutherland-Hodgman: intersection of two convex projected triangles, at most six corners.
        for(int edge=0;edge<3;edge++) {
            int next=(edge+1)%3;
            double x=b.xy[edge*2],y=b.xy[edge*2+1],dx=b.xy[next*2]-x,dy=b.xy[next*2+1]-y;
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
            count=written;double[] swap=input;input=output;output=swap;
        }
        double area=0,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(int i=0;i<count;i++) {
            int next=(i+1)%count;
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
        int n=triangles.length;
        int[] sweep=new int[n],rank=new int[n],indegree=new int[n];
        IntArrayList[] outgoing=new IntArrayList[n];
        for(int i=0;i<n;i++){sweep[i]=i;rank[fallback[i]]=i;}
        IntArrays.quickSort(sweep,(a,b)->Double.compare(triangles[a]==null?Double.POSITIVE_INFINITY:triangles[a].minX,
                triangles[b]==null?Double.POSITIVE_INFINITY:triangles[b].minX));
        double[] scratchA=new double[20],scratchB=new double[20];
        int crossings=0;
        for(int i=0;i<n;i++) {
            int a=sweep[i];if(triangles[a]==null)break;
            for(int j=i+1;j<n;j++) {
                int b=sweep[j];
                if(triangles[b]==null || triangles[b].minX>=triangles[a].maxX)break;
                int relation=relation(triangles[a],triangles[b],scratchA,scratchB);
                if(relation==0)continue;
                if(relation==2){crossings++;continue;}
                int far=relation==1?a:b, near=relation==1?b:a;
                if(outgoing[far]==null)outgoing[far]=new IntArrayList();
                outgoing[far].add(near);indegree[near]++;
            }
        }
        PriorityQueue<Integer> ready=new PriorityQueue<>((a,b)->Integer.compare(rank[a],rank[b]));
        boolean[] emitted=new boolean[n];int[] result=new int[n];
        for(int i=0;i<n;i++)if(indegree[i]==0)ready.add(i);
        int cycles=0,cursor=0;
        for(int i=0;i<n;i++) {
            if(ready.isEmpty()) {
                while(emitted[fallback[cursor]])cursor++;
                ready.add(fallback[cursor]);cycles++;
            }
            int id=ready.remove();emitted[id]=true;result[i]=id;
            if(outgoing[id]!=null)for(int next:outgoing[id])if(!emitted[next] && --indegree[next]==0)ready.add(next);
        }
        return new Result(result,crossings,cycles);
    }

    static Result refine(float[] vertices,int[] indices,FloatBuffer instances,int stride,int count,
                         Vector3f pivot,Matrix4fc view,Matrix4fc projection,int[] fallback) {
        int perInstance=indices.length/3;
        Triangle[] projected=new Triangle[perInstance*count];
        Quaternionf q=new Quaternionf();Vector3f p=new Vector3f();Vector4f clip=new Vector4f();
        for(int instance=0;instance<count;instance++) {
            int base=instance*stride;
            q.set(instances.get(base+6),instances.get(base+7),instances.get(base+8),instances.get(base+9));
            for(int t=0;t<perInstance;t++) {
                double[] corners=new double[9];boolean usable=true;
                for(int corner=0;corner<3;corner++) {
                    int index=indices[t*3+corner]*3;
                    p.set(vertices[index],vertices[index+1],vertices[index+2]).add(pivot)
                            .mul(instances.get(base+3),instances.get(base+4),instances.get(base+5));
                    q.transform(p);p.add(instances.get(base),instances.get(base+1),instances.get(base+2)).mulPosition(view);
                    clip.set(p,1).mul(projection);
                    // Near-plane crossings need polygon clipping. Keep the conservative fallback
                    // for those, never manufacture an ordering from a negative perspective divisor.
                    if(clip.w<=0 || clip.z < -clip.w){usable=false;break;}
                    corners[corner*3]=(double)clip.x/clip.w;corners[corner*3+1]=(double)clip.y/clip.w;corners[corner*3+2]=(double)clip.z/clip.w;
                }
                if(usable)projected[instance*perInstance+t]=Triangle.of(corners);
            }
        }
        return refine(projected,fallback);
    }
}
