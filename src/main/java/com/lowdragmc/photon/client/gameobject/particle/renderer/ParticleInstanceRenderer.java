package com.lowdragmc.photon.client.gameobject.particle.renderer;

import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleRendererSetting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL42.glDrawElementsInstancedBaseInstance;

/**
 * GL-resource backend of {@link TileParticleRenderer}: billboard-quad or baked-model base
 * geometry plus the tile per-instance layout (pos/size/scale/rot/color/uv/light + custom data).
 * Buffer management and the draw call live in {@link InstancedRenderBackend}.
 */
class ParticleInstanceRenderer extends InstancedRenderBackend {

    private final ParticleConfig config;
    /** Effective renderer runtime (slot-or-config per field); drives render-mode-dependent geometry +
     *  layout. Custom GPU data still comes from the config. */
    private final ParticleRendererSetting.Runtime renderer;
    /** Mesh baked into the current static VBO, for hot-reload staleness checks (identity compare). */
    @Nullable
    private PhotonMesh builtMesh;
    /** Whether the current static geometry/layout was baked for Model mode (vs billboard family); a
     *  runtime renderMode override crossing this boundary forces a rebuild. */
    private boolean builtModelMode;
    /** The emitter's Tangent renderer setting, refreshed per frame by the pass. */
    private boolean wantsTangent;
    /** Whether the current static geometry carries a tangent; a change against {@link #wantsTangent}
     *  forces a rebuild (the mesh vertex layout differs). */
    private boolean builtWithTangent;
    private float[] triangleCenters = new float[0];
    private float[] modelPositions = new float[0];
    private int[] triangleIndices = new int[0];
    private ModelTriangleOrder.Workspace sortingWorkspace;
    private FloatBuffer sortingTransforms;
    private final Matrix4f sortingView = new Matrix4f(), sortingProjection = new Matrix4f();
    private final Vector3f sortingPivot = new Vector3f();
    private boolean sortingPending;
    private IntBuffer sortedIndexBuffer;
    private long uploadedIndexRevision = -1;
    private ShaderInstance lastBaseShader;
    private int lastBaseProgram = -1, lastBaseUniform = -1;
    private int instanceAttributeBase;
    private boolean reportedUnsortableOverlap;

    public ParticleInstanceRenderer(ParticleConfig config, ParticleRendererSetting.Runtime renderer) {
        this.config = config;
        this.renderer = renderer;
    }

    @Nullable
    PhotonMesh getBuiltMesh() {
        return builtMesh;
    }

    boolean wasBuiltForModel() {
        return builtModelMode;
    }

    boolean wasBuiltWithTangent() {
        return builtWithTangent;
    }

    boolean wantsTangent() {
        return wantsTangent;
    }

    void setWantsTangent(boolean wantsTangent) {
        this.wantsTangent = wantsTangent;
    }

    @Override
    protected int initialInstanceCapacity() {
        return config.getMaxParticles();
    }

    @Override
    protected void createStaticGeometry(InstanceResource resource) {
        this.builtModelMode = renderer.getRenderMode() == ParticleRendererSetting.Mode.Model;
        this.builtWithTangent = wantsTangent;
        if (renderer.getRenderMode() == ParticleRendererSetting.Mode.Model) {
            var source = renderer.getModelSource();
            var mesh = source.getMesh();
            var remapUV = source.hasAtlasUV() && !renderer.isUseBlockUV();
            var shade = renderer.isShade();

            // With tangents: pos 3, uv 2, normal+brightness 4, tangent+handedness 4 — brightness and
            // handedness ride in the w of the two vec4s so the tangent costs no EXTRA attribute location
            // (per-instance attributes stay at 4..8, TILE_MODEL's channel base stays at 9).
            // Without: pos 3, uv 2, normal 3, brightness 1 — byte-identical to the pre-tangent layout.
            // MIRRORED FROM the PARTICLE_MODEL_INSTANCE block of particle.glsl (keep in lockstep).
            int floatsPerVertex = wantsTangent ? 3 + 2 + 4 + 4 : 3 + 2 + 3 + 1;
            int quadCount = mesh.quadCount();
            var vertexBuffer = BufferUtils.createFloatBuffer(quadCount * 4 * floatsPerVertex);
            var indexBuffer = BufferUtils.createIntBuffer(quadCount * 6);
            var vertexBase = 0;
            var pivotPoint = renderer.getModelPivot();
            var vertices = mesh.vertices();
            modelPositions = new float[quadCount * 4 * 3];
            for (int vertex = 0; vertex < quadCount * 4; vertex++) {
                System.arraycopy(vertices, PhotonMesh.vertexOffset(vertex / 4, vertex % 4), modelPositions, vertex * 3, 3);
            }
            // only touched when the emitter asked for tangents — the mesh generates them on first access
            var tangents = wantsTangent ? mesh.tangents() : null;
            var bounds = mesh.spriteBounds();
            var centers = new ArrayList<Float>();
            var triangles = new ArrayList<Integer>();

            for (int quad = 0; quad < quadCount; quad++) {
                var brightness = shade ? mesh.shadeBrightness(quad) : 1f;
                float u0 = 0, v0 = 0, uw = 1, vh = 1;
                if (remapUV) {
                    u0 = bounds[quad * 4];
                    v0 = bounds[quad * 4 + 1];
                    uw = bounds[quad * 4 + 2] - u0;
                    vh = bounds[quad * 4 + 3] - v0;
                }

                for (int corner = 0; corner < 4; corner++) {
                    int off = PhotonMesh.vertexOffset(quad, corner);
                    int tan = PhotonMesh.tangentOffset(quad, corner);
                    var u = vertices[off + 3];
                    var v = vertices[off + 4];
                    if (remapUV) {
                        u = (u - u0) / uw;
                        v = (v - v0) / vh;
                    }

                    vertexBuffer.put(vertices[off] + pivotPoint.x)
                            .put(vertices[off + 1] + pivotPoint.y)
                            .put(vertices[off + 2] + pivotPoint.z); // pos
                    vertexBuffer.put(u).put(v); // uv
                    vertexBuffer.put(vertices[off + 5]).put(vertices[off + 6]).put(vertices[off + 7]); // normal
                    vertexBuffer.put(brightness); // brightness (aNormal.w when tangents are on)
                    if (wantsTangent) {
                        // tangent.xyz + handedness in w. The atlas->sprite UV remap above is a positive
                        // per-axis scale, so it can't rotate the tangent — no remap needed here.
                        vertexBuffer.put(tangents[tan]).put(tangents[tan + 1]).put(tangents[tan + 2])
                                .put(tangents[tan + 3]);
                    }
                }

                // index (triangles are degenerate quads — the second triangle has zero area)
                indexBuffer.put(vertexBase).put(vertexBase + 1).put(vertexBase + 2);
                indexBuffer.put(vertexBase + 2).put(vertexBase + 3).put(vertexBase);
                for (int[] corners : new int[][]{{0, 1, 2}, {2, 3, 0}}) {
                    int a = PhotonMesh.vertexOffset(quad, corners[0]);
                    int b = PhotonMesh.vertexOffset(quad, corners[1]);
                    int c = PhotonMesh.vertexOffset(quad, corners[2]);
                    float ux = vertices[b] - vertices[a], uy = vertices[b + 1] - vertices[a + 1], uz = vertices[b + 2] - vertices[a + 2];
                    float vx = vertices[c] - vertices[a], vy = vertices[c + 1] - vertices[a + 1], vz = vertices[c + 2] - vertices[a + 2];
                    if (uy * vz - uz * vy == 0 && uz * vx - ux * vz == 0 && ux * vy - uy * vx == 0) continue;
                    for (int axis = 0; axis < 3; axis++) centers.add((vertices[a + axis] + vertices[b + axis] + vertices[c + axis]) / 3f);
                    for (int corner : corners) triangles.add(vertexBase + corner);
                }
                vertexBase += 4;
            }

            vertexBuffer.flip();
            indexBuffer.flip();

            resource.modelVbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, resource.modelVbo);
            glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_DYNAMIC_DRAW);
            int stride = floatsPerVertex * Float.BYTES;
            int offset = 0;

            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, offset); // position
            glEnableVertexAttribArray(0);
            offset += 3 * Float.BYTES;

            glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, offset); // uv
            glEnableVertexAttribArray(1);
            offset += 2 * Float.BYTES;

            // with tangents location 2 widens to hold brightness in its w, freeing location 3 for the
            // tangent; without, it is the original vec3 normal + float brightness pair
            int normalSize = wantsTangent ? 4 : 3;
            glVertexAttribPointer(2, normalSize, GL_FLOAT, false, stride, offset); // normal (+ brightness in w)
            glEnableVertexAttribArray(2);
            offset += normalSize * Float.BYTES;

            glVertexAttribPointer(3, wantsTangent ? 4 : 1, GL_FLOAT, false, stride, offset); // tangent | brightness
            glEnableVertexAttribArray(3);

            resource.modelEbo = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, resource.modelEbo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL_DYNAMIC_DRAW);
            modelEboSize = 6 * quadCount;
            builtMesh = mesh;
            triangleCenters = new float[centers.size()];
            for (int i = 0; i < centers.size(); i++) triangleCenters[i] = centers.get(i);
            triangleIndices = triangles.stream().mapToInt(Integer::intValue).toArray();

        } else {
            // particle quad
            float[] quadVertices = {
                    // x, y, z
                    -1f, -1f, 0f,
                    -1f, 1f, 0f,
                    1f, 1f, 0f,
                    1f, -1f, 0f,
            };
            int[] quadIndices = {
                    0, 1, 2, 2, 3, 0
            };

            // bind vertex data
            resource.modelVbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, resource.modelVbo);
            glBufferData(GL_ARRAY_BUFFER, quadVertices, GL_STATIC_DRAW);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);

            // create ebo
            resource.modelEbo = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, resource.modelEbo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, quadIndices, GL_STATIC_DRAW);
            modelEboSize = 6;
        }
    }

    @Override
    protected int instanceFloats() {
        var custom = config.additionalGPUDataSetting.attribFloats();
        return custom + (renderer.getRenderMode() == ParticleRendererSetting.Mode.Model
                ? 3 + 3 + 4 + 4 + 1        // pos scale rotation color light
                : 3 + 2 + 3 + 4 + 4 + 4 + 1); // pos size scale rotation color uv light
    }

    @Override
    protected int dataTexelsPerInstance() {
        return config.additionalGPUDataSetting.dataTexels();
    }

    @Override
    protected int customTexelsPerInstance() {
        return config.additionalGPUDataSetting.hasCustomRecord()
                ? config.additionalGPUDataSetting.customDataTexels() : 0;
    }

    @Override
    protected void defineInstanceAttributes(int stride) {
        int attribIndex;
        int offset = instanceAttributeBase;

        if (renderer.getRenderMode() == ParticleRendererSetting.Mode.Model) {
            attribIndex = 4;
            offset = floatInstanceAttrib(attribIndex++, 3, stride, offset); // pos vec3
            offset = floatInstanceAttrib(attribIndex++, 3, stride, offset); // scale vec3
            offset = floatInstanceAttrib(attribIndex++, 4, stride, offset); // rotation vec4
            offset = floatInstanceAttrib(attribIndex++, 4, stride, offset); // color vec4
            offset = intInstanceAttrib(attribIndex++, stride, offset);      // light int
        } else {
            attribIndex = 1;
            offset = floatInstanceAttrib(attribIndex++, 3, stride, offset); // pos vec3
            offset = floatInstanceAttrib(attribIndex++, 2, stride, offset); // size vec2
            offset = floatInstanceAttrib(attribIndex++, 3, stride, offset); // scale vec3
            offset = floatInstanceAttrib(attribIndex++, 4, stride, offset); // rotation vec4
            offset = floatInstanceAttrib(attribIndex++, 4, stride, offset); // color vec4
            offset = floatInstanceAttrib(attribIndex++, 4, stride, offset); // uv vec4
            offset = intInstanceAttrib(attribIndex++, stride, offset);      // light int
        }

        config.additionalGPUDataSetting.layoutAttribs(offset, stride);
    }

    @Override
    void endUpload(FloatBuffer buffer, int count) {
        super.endUpload(buffer, count);
        sortingPending = builtModelMode && count > 0 && renderer.getVertexSortingMode() != RendererSetting.SortMode.NONE;
        if (sortingPending) {
            // Upload staging is shared between renderers. Keep only the geometry-relevant record,
            // not a reference to that buffer. Defer expensive work until material/pass state is known.
            int required = Math.multiplyExact(count, 10);
            if (sortingTransforms == null || sortingTransforms.capacity() < required) {
                sortingTransforms = FloatBuffer.allocate(Math.max(required,
                        sortingTransforms == null ? 0 : sortingTransforms.capacity() + sortingTransforms.capacity()/2));
            }
            for (int i = 0; i < count; i++) for (int c = 0; c < 10; c++)
                sortingTransforms.put(i*10+c, buffer.get(i*instanceDataSize+c));
            sortingView.set(RenderSystem.getModelViewMatrix());
            sortingProjection.set(RenderSystem.getProjectionMatrix());
            sortingPivot.set(renderer.getModelPivot());
        }
    }

    @Override
    protected void drawGeometry(ShaderInstance shader, int count) {
        var pipeline = RenderPassPipeline.getCurrent();
        if (!sortingPending || !glIsEnabled(GL_BLEND)
                || (pipeline != null && (pipeline.isMaskSubPass() || pipeline.isWireframeSubPass()))) {
            super.drawGeometry(shader, count);
            return;
        }
        if (sortingWorkspace == null) sortingWorkspace = new ModelTriangleOrder.Workspace();
        var triangleOrder = sortingWorkspace.build(triangleCenters, triangleIndices, modelPositions,
                sortingTransforms, 10, count, sortingPivot, sortingView, sortingProjection);
        if (!reportedUnsortableOverlap && (triangleOrder.crossingPairs() > 0 || triangleOrder.cycleBreaks() > 0)) {
            reportedUnsortableOverlap = true;
            com.lowdragmc.photon.Photon.LOGGER.warn("GPU Model sorting: {} crossing triangle pairs, {} cyclic constraints. Whole-triangle ordering cannot fully resolve these overlaps (reported once per renderer).",
                    triangleOrder.crossingPairs(), triangleOrder.cycleBreaks());
        }
        if (shader != lastBaseShader || shader.getId() != lastBaseProgram) {
            lastBaseShader = shader;
            lastBaseProgram = shader.getId();
            lastBaseUniform = glGetUniformLocation(lastBaseProgram, "PhotonInstanceBase");
        }
        int baseUniform = lastBaseUniform;
        int oldBase = baseUniform >= 0 ? glGetUniformi(shader.getId(), baseUniform) : 0;
        boolean baseInstance = GL.getCapabilities().OpenGL42;
        int oldArrayBuffer = baseInstance ? 0 : glGetInteger(GL_ARRAY_BUFFER_BINDING);
        try {
            if (resource.sortedModelEbo == -1) resource.sortedModelEbo = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, resource.sortedModelEbo);
            if (uploadedIndexRevision != sortingWorkspace.indexRevision()) {
                int size = triangleOrder.indices().length;
                if (sortedIndexBuffer == null || sortedIndexBuffer.capacity() < size) {
                    sortedIndexBuffer = BufferUtils.createIntBuffer(Math.max(size,
                            sortedIndexBuffer == null ? 0 : sortedIndexBuffer.capacity() + sortedIndexBuffer.capacity()/2));
                }
                sortedIndexBuffer.clear().put(triangleOrder.indices()).flip();
                // Orphan on actual changes so an in-flight draw need not stall the CPU. Stable
                // orders reuse the previous GPU contents, including across camera/animation updates.
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, (long) sortedIndexBuffer.capacity()*Integer.BYTES, GL_STREAM_DRAW);
                glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, 0, sortedIndexBuffer);
                uploadedIndexRevision = sortingWorkspace.indexRevision();
            }
            if (!baseInstance) glBindBuffer(GL_ARRAY_BUFFER, resource.instanceVbo);
            for (var run : triangleOrder.runs()) {
                if (baseUniform >= 0) glUniform1i(baseUniform, run.instance());
                if (baseInstance) {
                    // Offsets divisor attributes, but NOT gl_InstanceID: keep PhotonInstanceBase
                    // for PhotonData/CustomData TBO indexing. No shader-version requirement added.
                    glDrawElementsInstancedBaseInstance(GL_TRIANGLES, run.indexCount(), GL_UNSIGNED_INT,
                            (long) run.firstIndex() * Integer.BYTES, 1, run.instance());
                } else {
                    instanceAttributeBase = Math.multiplyExact(run.instance(), instanceDataSize * Float.BYTES);
                    defineInstanceAttributes(instanceDataSize * Float.BYTES);
                    glDrawElementsInstanced(GL_TRIANGLES, run.indexCount(), GL_UNSIGNED_INT,
                            (long) run.firstIndex() * Integer.BYTES, 1);
                }
            }
        } finally {
            if (!baseInstance) {
                instanceAttributeBase = 0;
                glBindBuffer(GL_ARRAY_BUFFER, resource.instanceVbo);
                defineInstanceAttributes(instanceDataSize * Float.BYTES);
                glBindBuffer(GL_ARRAY_BUFFER, oldArrayBuffer);
            }
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, resource.modelEbo);
            if (baseUniform >= 0) glUniform1i(baseUniform, oldBase);
        }
    }

    @Override
    public void dispose() {
        sortingWorkspace = null;
        sortingTransforms = null;
        sortingPending = false;
        sortedIndexBuffer = null;
        uploadedIndexRevision = -1;
        lastBaseShader = null;
        lastBaseProgram = lastBaseUniform = -1;
        triangleCenters = new float[0];
        modelPositions = new float[0];
        triangleIndices = new int[0];
        instanceAttributeBase = 0;
        reportedUnsortableOverlap = false;
        super.dispose();
    }
}
