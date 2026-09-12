package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.BufferUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL42.glDrawElementsInstancedBaseInstance;

/** Hidden-window driver check using the production particle.glsl and sorting planner. */
public class ModelOrderGpuProbe {
    static int shader(int kind, String source) {
        int shader = glCreateShader(kind);
        glShaderSource(shader, source); glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) throw new AssertionError(glGetShaderInfoLog(shader));
        return shader;
    }
    static void attributes(int base) {
        int offset = base * 15 * 4;
        int[] sizes = {3,3,4,4,1};
        for (int i=0;i<5;i++) {
            if (i==4) glVertexAttribIPointer(4+i,1,GL_UNSIGNED_INT,60,offset);
            else glVertexAttribPointer(4+i,sizes[i],GL_FLOAT,false,60,offset);
            glEnableVertexAttribArray(4+i); glVertexAttribDivisor(4+i,1);
            offset += sizes[i]*4;
        }
    }
    static void textureBuffer(int program, String name, int unit, float[] data) {
        int buffer = glGenBuffers(); glBindBuffer(GL_TEXTURE_BUFFER,buffer);
        glBufferData(GL_TEXTURE_BUFFER,data,GL_STATIC_DRAW);
        glActiveTexture(GL_TEXTURE0+unit);
        int tex = glGenTextures(); glBindTexture(GL_TEXTURE_BUFFER,tex);
        glTexBuffer(GL_TEXTURE_BUFFER,GL_RGBA32F,buffer);
        glUniform1i(glGetUniformLocation(program,name),unit);
    }
    static float[] pixels() {
        float[] out = new float[32*32*4];
        glReadPixels(0,0,32,32,GL_RGBA,GL_FLOAT,out);
        return out;
    }
    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW init");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        boolean request42 = args.length > 0 && args[0].equals("--gl42");
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,request42 ? 4 : 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,request42 ? 2 : 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(32,32,"Photon sorting regression",0,0);
        if(window==0) throw new AssertionError("GLFW window");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            System.out.println("GL: " + glGetString(GL_VERSION) + "; " + glGetString(GL_RENDERER));
            String include = Files.readString(Path.of("src/main/resources/assets/photon/shaders/include/particle.glsl"));
            String vertex = "#version 330 core\n#define PARTICLE_MODEL_INSTANCE\n" + include + "\nout vec2 uv; out vec4 tint; void main(){ParticleData p=getParticleData();gl_Position=vec4(p.Position.xy,-p.Position.z,1);uv=p.UV;tint=photon_custom_data(0)*p.Color;tint.rgb*=photon_data_random();}\n";
            String fragment = "#version 330 core\nin vec2 uv;in vec4 tint;uniform sampler2D colorTex;uniform sampler2D alphaTex;out vec4 fragColor;void main(){fragColor=vec4(texture(colorTex,uv).rgb*tint.rgb,texture(alphaTex,uv).r*tint.a);}";
            int program=glCreateProgram(); glAttachShader(program,shader(GL_VERTEX_SHADER,vertex));glAttachShader(program,shader(GL_FRAGMENT_SHADER,fragment));glLinkProgram(program);
            if(glGetProgrami(program,GL_LINK_STATUS)==0) throw new AssertionError(glGetProgramInfoLog(program));
            glUseProgram(program);
            int framebuffer=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);
            int target=glGenTextures();glBindTexture(GL_TEXTURE_2D,target);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,32,32,0,GL_RGBA,GL_FLOAT,0L);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,target,0);
            if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("FBO");
            for(int unit=0;unit<2;unit++) {
                glActiveTexture(GL_TEXTURE0+unit);int tex=glGenTextures();glBindTexture(GL_TEXTURE_2D,tex);
                glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,2,2,0,GL_RGBA,GL_FLOAT,new float[]{1,.6f,.2f,1,.5f,1,.3f,1,.8f,.2f,1,1,1,1,1,1});
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
                glUniform1i(glGetUniformLocation(program,unit==0?"colorTex":"alphaTex"),unit);
            }
            float[] custom=new float[32];custom[0]=1;custom[3]=.35f;custom[18]=1;custom[19]=.7f;
            float[] data=new float[40];data[0]=.8f;data[20]=.6f;
            textureBuffer(program,"PhotonCustomData",13,custom);textureBuffer(program,"PhotonData",14,data);
            int vao=glGenVertexArrays();glBindVertexArray(vao);
            int vbo=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,vbo);
            glBufferData(GL_ARRAY_BUFFER,new float[]{-1,-1,0, 0,0, 0,0,1,1, 1,-1,0, 1,0, 0,0,1,1, 0,1,0, .5f,1, 0,0,1,1},GL_STATIC_DRAW);
            int[] sizes={3,2,3,1};int offset=0;
            for(int i=0;i<4;i++){glVertexAttribPointer(i,sizes[i],GL_FLOAT,false,36,offset);glEnableVertexAttribArray(i);offset+=sizes[i]*4;}
            FloatBuffer instances=BufferUtils.createFloatBuffer(30);
            instances.put(new float[]{0,0,-.2f,1,1,1,0,0,0,1,1,1,1,1,0, .25f,0,-.7f,1,1,1,0,0,0,1,.7f,.5f,1,.8f,0}).flip();
            int instanceVbo=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,instanceVbo);glBufferData(GL_ARRAY_BUFFER,instances,GL_STATIC_DRAW);attributes(0);
            int ebo=glGenBuffers();glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,ebo);glBufferData(GL_ELEMENT_ARRAY_BUFFER,new int[]{0,1,2},GL_STATIC_DRAW);
            glViewport(0,0,32,32);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glEnable(GL_BLEND);glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ZERO);glClearColor(0,0,0,0);
            int uniform=glGetUniformLocation(program,"PhotonInstanceBase");if(uniform<0)throw new AssertionError("Instance base optimized away unexpectedly");
            glClear(GL_COLOR_BUFFER_BIT);glUniform1i(uniform,0);glDrawElementsInstanced(GL_TRIANGLES,3,GL_UNSIGNED_INT,0L,2);float[] old=pixels();
            glClear(GL_COLOR_BUFFER_BIT);
            for(int id:new int[]{1,0}){attributes(id);glUniform1i(uniform,id);glDrawElementsInstanced(GL_TRIANGLES,3,GL_UNSIGNED_INT,0L,1);}
            float[] expected=pixels();
            var plan=ModelTriangleOrder.build(new float[]{0,-1f/3,0},new int[]{0,1,2},instances,15,2,new Vector3f(),new Matrix4f());
            glBufferData(GL_ELEMENT_ARRAY_BUFFER,plan.indices(),GL_STREAM_DRAW);glClear(GL_COLOR_BUFFER_BIT);
            for(var run:plan.runs()){attributes(run.instance());glUniform1i(uniform,run.instance());glDrawElementsInstanced(GL_TRIANGLES,run.indexCount(),GL_UNSIGNED_INT,(long)run.firstIndex()*4,1);}
            float[] sorted=pixels();float error=0,difference=0;
            for(int i=0;i<sorted.length;i++){error=Math.max(error,Math.abs(sorted[i]-expected[i]));difference=Math.max(difference,Math.abs(old[i]-expected[i]));}
            if(error>1e-6 || difference<.01)throw new AssertionError("Sorted error="+error+", original difference="+difference);
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("OpenGL error");
            System.out.println("PASS GPU: production particle.glsl, two color/alpha textures, distinct CustomData and PhotonData per instance; sorted vs known back-to-front reference max error="+error+", original-order error="+difference);
            if (GL.getCapabilities().OpenGL42) {
                // Same offsets as the optimized backend, using unchanged divisor attributes.
                glClear(GL_COLOR_BUFFER_BIT);
                attributes(0);
                for (var run : plan.runs()) {
                    glUniform1i(uniform, run.instance());
                    glDrawElementsInstancedBaseInstance(GL_TRIANGLES, run.indexCount(), GL_UNSIGNED_INT,
                            (long)run.firstIndex()*4, 1, run.instance());
                }
                float[] fast = pixels(); error = 0;
                for (int i = 0; i < fast.length; i++) error = Math.max(error, Math.abs(fast[i]-expected[i]));
                if (error > 1e-6 || glGetError() != GL_NO_ERROR) throw new AssertionError("Base instance error=" + error);
                System.out.println("PASS GPU base-instance fast path: distinct position/color and both TBOs, error=" + error);
            } else System.out.println("SKIP base-instance fast path: GL 4.2 unavailable");
            // A sloping face's centre is farther away, but its overlapping tip is nearer.
            // Vertex-centre order is wrong here even for ONE model instance.
            float[] points={0,0,-.7f, 1,0,-.7f, 0,1,-.7f, .1f,.1f,-.6f, .6f,.1f,-.6f, 10,10,-.99f};
            float[] packed=new float[54];
            for(int i=0;i<6;i++) {
                System.arraycopy(points,i*3,packed,i*9,3);
                packed[i*9+3]=i<3?.1f:.9f;packed[i*9+4]=i<3?.1f:.9f;
                packed[i*9+7]=1;packed[i*9+8]=1;
            }
            glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,packed,GL_STATIC_DRAW);
            instances.clear();instances.put(new float[]{0,0,0,1,1,1,0,0,0,1,1,1,1,1,0}).flip();
            glBindBuffer(GL_ARRAY_BUFFER,instanceVbo);glBufferData(GL_ARRAY_BUFFER,instances,GL_STATIC_DRAW);attributes(0);glUniform1i(uniform,0);
            custom[0]=custom[1]=custom[2]=1;textureBuffer(program,"PhotonCustomData",13,custom);
            int[] faceIndices={0,1,2,3,4,5};
            glBufferData(GL_ELEMENT_ARRAY_BUFFER,faceIndices,GL_STREAM_DRAW);glClear(GL_COLOR_BUFFER_BIT);
            glDrawElementsInstanced(GL_TRIANGLES,6,GL_UNSIGNED_INT,0L,1);float[] overlapReference=pixels();
            float[] centres={1f/3,1f/3,-.7f,10.7f/3,10.2f/3,-.73f};
            var centrePlan=ModelTriangleOrder.build(centres,faceIndices,instances,15,1,new Vector3f(),new Matrix4f());
            glBufferData(GL_ELEMENT_ARRAY_BUFFER,centrePlan.indices(),GL_STREAM_DRAW);glClear(GL_COLOR_BUFFER_BIT);glDrawElementsInstanced(GL_TRIANGLES,6,GL_UNSIGNED_INT,0L,1);float[] centrePixels=pixels();
            var overlapPlan=ModelTriangleOrder.build(centres,faceIndices,points,instances,15,1,new Vector3f(),new Matrix4f(),new Matrix4f().scaling(1,1,-1));
            glBufferData(GL_ELEMENT_ARRAY_BUFFER,overlapPlan.indices(),GL_STREAM_DRAW);glClear(GL_COLOR_BUFFER_BIT);glDrawElementsInstanced(GL_TRIANGLES,6,GL_UNSIGNED_INT,0L,1);float[] overlapPixels=pixels();
            error=0;difference=0;
            for(int i=0;i<overlapPixels.length;i++){error=Math.max(error,Math.abs(overlapPixels[i]-overlapReference[i]));difference=Math.max(difference,Math.abs(centrePixels[i]-overlapReference[i]));}
            if(error>1e-6 || difference<.01)throw new AssertionError("Overlap error="+error+", centre error="+difference);
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("Overlap GL error");
            System.out.println("PASS GPU sloped overlap: corrected error="+error+", centre-sort error="+difference);
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
}
