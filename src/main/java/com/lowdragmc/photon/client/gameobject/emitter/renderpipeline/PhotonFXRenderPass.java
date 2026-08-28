package com.lowdragmc.photon.client.gameobject.emitter.renderpipeline;

 import com.lowdragmc.photon.Photon;
 import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
 import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
 import com.lowdragmc.photon.client.gameobject.emitter.data.material.*;
 import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author KilaBash
 * @date 2023/6/5
 * @implNote IPhotonParticleRenderType
 */
@OnlyIn(Dist.CLIENT)
@ParametersAreNonnullByDefault
public abstract class PhotonFXRenderPass {
    public final static CustomShaderMaterial INVERSE = new CustomShaderMaterial(Photon.id("inverse"));
    protected static final MaterialSetting WIREFRAME_MATERIAL = new MaterialSetting();
    /** The mask value (0..1) of the pass currently drawing in the mask sub-pass — staged into the
     *  shared mask shader by {@link #MASK}'s begin() (covers the CPU and every instanced path). */
    private static float CURRENT_MASK_VALUE = 0f;
    /** Alpha-clip cutoff of the current mask draw (0 = flat geometry mask). */
    private static float CURRENT_MASK_CUTOFF = 0f;
    /** The texture the alpha clip samples (the pass's first texture material), null = none. */
    @Nullable
    private static net.minecraft.resources.ResourceLocation CURRENT_MASK_TEXTURE = null;
    /** The mask sub-pass material: flat {@code MaskValue} output over the shared particle vertex
     *  transform ({@code getParticleData()}), variant-selected per render path like INVERSE. */
    public final static CustomShaderMaterial MASK = new CustomShaderMaterial(Photon.id("mask")) {
        @Override
        public net.minecraft.client.renderer.ShaderInstance begin(MaterialContext context) {
            var shader = super.begin(context);
            shader.safeGetUniform("MaskValue").set(CURRENT_MASK_VALUE);
            shader.safeGetUniform("AlphaCutoff").set(CURRENT_MASK_CUTOFF);
            if (CURRENT_MASK_TEXTURE != null) {
                // Sampler0 rides RenderSystem's shader-texture slot: the CPU path pulls it in
                // drawWithShader, the instanced path in setDefaultUniforms — same as TextureMaterial
                RenderSystem.setShaderTexture(0, CURRENT_MASK_TEXTURE);
            }
            return shader;
        }
    };
    protected static final MaterialSetting MASK_MATERIAL = new MaterialSetting();
    static {
        WIREFRAME_MATERIAL.setMaterial(INVERSE);
        WIREFRAME_MATERIAL.setCull(false);
        WIREFRAME_MATERIAL.setDepthMask(false);
        WIREFRAME_MATERIAL.setDepthTest(false);
        // mask draws depth-test against the scene depth pre-copied into MASK_TARGET and WRITE their
        // own depth there (= custom depth) — the main depth buffer is never touched
        MASK_MATERIAL.setMaterial(MASK);
        MASK_MATERIAL.setCull(false);
        MASK_MATERIAL.setDepthMask(true);
        MASK_MATERIAL.setDepthTest(true);
    }

    /** The per-emitter render-override runtime this pass draws with (config.renderer's default runtime for
     *  the shared pass, an emitter's overriding runtime for an override pass). Its <b>effective</b> values
     *  (slot-or-config) are the batching key — see {@link #equals}/{@link #hashCode}. */
    public final RendererSetting.Runtime renderer;
    public final VertexFormat.Mode mode;
    public final VertexFormat format;

    public PhotonFXRenderPass(RendererSetting.Runtime renderer, VertexFormat.Mode mode, VertexFormat format) {
        this.renderer = renderer;
        this.mode = mode;
        this.format = format;
    }

    public void prepareStatus(@Nonnull RenderPassPipeline pipeline) {
        Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
    }

    public BufferBuilder begin(@Nonnull Tesselator tesselator) {
        var buffer = tesselator.getBuilder();
        buffer.begin(mode, format);
        return buffer;
    }

    /**
     * Draw this pass's queued particles. Returns whether anything was actually drawn
     * (used by the pipeline to decide whether the scene sampler became stale).
     */
    public final boolean drawParticles(RenderPassPipeline pipeline, Collection<IParticle> particles, Camera camera, float partialTicks) {
        var materials = getMaterials(pipeline);
        if (materials.isEmpty()) return false;
        if (pipeline.isMaskSubPass()) {
            CURRENT_MASK_VALUE = com.lowdragmc.photon.client.postfx.runtime.MaskGroups
                    .idOf(renderer.getMaskGroup()) / 255f;
            var cutoff = renderer.getMaskAlphaCutoff();
            CURRENT_MASK_TEXTURE = cutoff > 0 ? findMaskClipTexture() : null;
            // no clippable texture on the pass -> fall back to the flat geometry mask
            CURRENT_MASK_CUTOFF = CURRENT_MASK_TEXTURE != null ? cutoff : 0f;
        }
        return drawParticlesInternal(materials, pipeline, particles, camera, partialTicks);
    }

    protected final boolean drawParticlesInternal(List<MaterialSetting> materials, RenderPassPipeline pipeline, Collection<IParticle> particles, Camera camera, float partialTicks) {
        if (useInstancing()) {
            return drawInstanced(materials, pipeline, particles, camera, partialTicks);
        }

        // prepare mesh data
        var buffer = begin(Tesselator.getInstance());
        renderQueue(buffer, particles, camera, partialTicks);
        var sorting = getSorting();
        if (sorting != null && mode == VertexFormat.Mode.QUADS) buffer.setQuadSorting(sorting);
        var renderedBuffer = buffer.endOrDiscardIfEmpty();
        if (renderedBuffer == null) {
            return false;
        }
        if (sorting != null && mode != VertexFormat.Mode.QUADS) {
            sortIndependentPrimitives(renderedBuffer, sorting);
        }

        // upload to vbo
        var vbo = uploadFormatVbo(renderedBuffer);

        // render materials
        for (var materialSetting : materials) {
            renderWithMaterial(materialSetting, MaterialContext.NORMAL, shader -> {
                // Material.begin() and custom uniform setup may touch the VAO.  Re-bind the
                // uploaded format before every material so later materials cannot draw from a
                // stale immediate buffer binding.
                vbo.bind();
                if (shader.LINE_WIDTH != null
                        && (mode == VertexFormat.Mode.LINES || mode == VertexFormat.Mode.LINE_STRIP)) {
                    shader.LINE_WIDTH.set(RenderSystem.getShaderLineWidth());
                }
                vbo.draw();
            });
        }

        // invalidate cache
        BufferUploader.invalidate();
        return true;
    }

    /**
     * Geometry-emission seam of the CPU path: emit vertices for the queued particles
     * (all of this pass's particle type) into the tesselator buffer. Implementations
     * delegate to their particle-type renderer.
     */
    protected abstract void renderQueue(VertexConsumer buffer, Collection<IParticle> particles, Camera camera, float partialTicks);

    /**
     * Whether this pass draws via GPU instancing this frame. Per-frame decision;
     * MUST NOT participate in equals/hashCode (the batching key stays
     * rendererSetting + mode + format).
     */
    protected boolean useInstancing() {
        return false;
    }

    /**
     * Instanced draw path; only called when {@link #useInstancing()}. Returns whether
     * anything was drawn. Default: no instancing support.
     */
    protected boolean drawInstanced(List<MaterialSetting> materials, RenderPassPipeline pipeline, Collection<IParticle> particles, Camera camera, float partialTicks) {
        return false;
    }

    /**
     * Tear down this pass's instanced GL resources (render mode / model / instance layout
     * changed). No-op for passes without an instanced path.
     */
    public void clearInstance() {
    }

    /**
     * Union of the additional-data channels required by the pass's shadergraph materials —
     * fed into {@code AdditionalGPUDataSetting.setMaterialMask} so instanced passes auto-enable
     * whatever their graphs read (hand-written shader materials toggle channels manually).
     */
    protected static long shaderGraphChannelMask(List<MaterialSetting> materials) {
        long mask = 0;
        for (var materialSetting : materials) {
            if (getRawMaterial(materialSetting.getMaterial()) instanceof ShaderGraphMaterial shaderGraphMaterial) {
                mask |= shaderGraphMaterial.getUsedChannelMask();
            }
        }
        return mask;
    }

    /**
     * Whether any shadergraph material on the pass reads user custom data (a {@code CustomDataNode}) —
     * fed into {@code AdditionalGPUDataSetting.setCustomDataMaterialUsed} so instanced passes upload the
     * {@code PhotonCustomData} buffer texture only when needed (custom shaders read custom data through
     * their appended vertex attributes instead).
     */
    protected static boolean shaderGraphUsesCustomData(List<MaterialSetting> materials) {
        for (var materialSetting : materials) {
            if (getRawMaterial(materialSetting.getMaterial()) instanceof ShaderGraphMaterial shaderGraphMaterial
                    && shaderGraphMaterial.usesCustomData()) {
                return true;
            }
        }
        return false;
    }

    /** The texture the mask alpha clip samples: the pass's first texture material's texture. */
    @Nullable
    private net.minecraft.resources.ResourceLocation findMaskClipTexture() {
        for (var materialSetting : renderer.getMaterials()) {
            if (getRawMaterial(materialSetting.getMaterial()) instanceof TextureMaterial textureMaterial) {
                return textureMaterial.getTexture();
            }
        }
        return null;
    }

    private static IMaterial getRawMaterial(IMaterial material) {
        if (material instanceof UIResourceMaterial uiResourceMaterial) {
            return uiResourceMaterial.getRawMaterial();
        }
        return material;
    }

    protected List<MaterialSetting> getMaterials(RenderPassPipeline pipeline) {
        if (pipeline.isMaskSubPass()) {
            // only flagged passes participate in the mask sub-pass (empty = skipped entirely)
            return renderer.isWriteCustomMask() ? List.of(MASK_MATERIAL) : List.of();
        }
        var materials = renderer.getMaterials();
        if (pipeline.isWireframeSubPass()) {
            materials = List.of(WIREFRAME_MATERIAL);
        }
        return materials;
    }

    protected static VertexBuffer uploadFormatVbo(BufferBuilder.RenderedBuffer renderedBuffer) {
        var vbo = renderedBuffer.drawState().format().getImmediateDrawVertexBuffer();
        vbo.bind();
        vbo.upload(renderedBuffer);
        return vbo;
    }

    /** 1.20.1 only sorts quads natively; preserve 1.21's triangle/line primitive sorting by
     * reordering complete vertex records before the rendered buffer is uploaded. */
    private static void sortIndependentPrimitives(BufferBuilder.RenderedBuffer renderedBuffer,
                                                  VertexSorting sorting) {
        var state = renderedBuffer.drawState();
        int verticesPerPrimitive = switch (state.mode()) {
            case TRIANGLES -> 3;
            case LINES, DEBUG_LINES -> 2;
            default -> 0;
        };
        if (verticesPerPrimitive == 0 || state.vertexCount() < verticesPerPrimitive) return;

        var format = state.format();
        int positionOffset = -1;
        var elements = format.getElements();
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).isPosition()) {
                positionOffset = format.getOffset(i);
                break;
            }
        }
        if (positionOffset < 0) return;

        int stride = format.getVertexSize();
        int primitiveBytes = stride * verticesPerPrimitive;
        int primitiveCount = state.vertexCount() / verticesPerPrimitive;
        ByteBuffer vertices = renderedBuffer.vertexBuffer();
        byte[] original = new byte[primitiveCount * primitiveBytes];
        ByteBuffer source = vertices.duplicate();
        source.position(0).limit(original.length);
        source.get(original);
        ByteBuffer values = ByteBuffer.wrap(original).order(vertices.order());
        Vector3f[] centers = new Vector3f[primitiveCount];
        for (int primitive = 0; primitive < primitiveCount; primitive++) {
            float x = 0, y = 0, z = 0;
            int base = primitive * primitiveBytes + positionOffset;
            for (int vertex = 0; vertex < verticesPerPrimitive; vertex++) {
                int offset = base + vertex * stride;
                x += values.getFloat(offset);
                y += values.getFloat(offset + 4);
                z += values.getFloat(offset + 8);
            }
            float inverse = 1f / verticesPerPrimitive;
            centers[primitive] = new Vector3f(x * inverse, y * inverse, z * inverse);
        }

        int[] order = sorting.sort(centers);
        ByteBuffer destination = vertices.duplicate();
        destination.position(0);
        for (int primitive : order) {
            destination.put(original, primitive * primitiveBytes, primitiveBytes);
        }
    }

    /**
     * Apply a Photon material and draw with it.
     *
     * <p>Forge 1.20.1's {@link ShaderInstance#apply()} also applies the blend mode from the
     * shader JSON. Photon materials expose their blend/depth/cull state separately and most of
     * their shader JSONs intentionally omit a fixed blend mode. Applying {@link MaterialSetting}
     * before {@code shader.apply()} therefore gets overwritten. Keep the order explicit here:
     * uniforms, shader apply, Photon state, draw, shader clear, material cleanup.</p>
     */
    protected final void renderWithMaterial(MaterialSetting materialSetting, MaterialContext context,
                                            Consumer<ShaderInstance> drawCall) {
        var material = materialSetting.getMaterial();
        boolean began = false;
        boolean applied = false;
        ShaderInstance shader = null;
        final ShaderInstance[] shaderRef = new ShaderInstance[1];
        try {
            shader = material.begin(context);
            shaderRef[0] = shader;
            began = true;
            RenderSystem.setShader(() -> shaderRef[0]);
            // Forge 1.20.1 does not expose ShaderInstance#setDefaultUniforms.  VertexBuffer's
            // vanilla draw path fills these values for the built-in particle shader, but custom
            // Photon materials use their own ShaderInstance and otherwise retain the JSON defaults
            // (notably Sampler2=0 and FogEnd=1), which makes the whole particle transparent.
            setupDefaultUniforms(shader);
            shader.apply();
            applied = true;
            materialSetting.pre();
            drawCall.accept(shader);
        } finally {
            if (applied && shader != null) {
                shader.clear();
            }
            if (began) {
                material.end(context);
            }
            if (applied) {
                materialSetting.post();
            }
        }
    }

    /** Mirror the default uniform setup performed by vanilla's particle draw path. */
    private static void setupDefaultUniforms(ShaderInstance shader) {
        for (int i = 0; i < 12; i++) {
            shader.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        }
        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        }
        if (shader.PROJECTION_MATRIX != null) {
            shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        }
        if (shader.INVERSE_VIEW_ROTATION_MATRIX != null) {
            shader.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
        }
        if (shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }
        if (shader.GLINT_ALPHA != null) {
            shader.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
        }
        if (shader.FOG_START != null) {
            shader.FOG_START.set(RenderSystem.getShaderFogStart());
        }
        if (shader.FOG_END != null) {
            shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        }
        if (shader.FOG_COLOR != null) {
            shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }
        if (shader.FOG_SHAPE != null) {
            shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        }
        if (shader.TEXTURE_MATRIX != null) {
            shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        }
        if (shader.GAME_TIME != null) {
            shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
        }
        if (shader.SCREEN_SIZE != null) {
            var window = Minecraft.getInstance().getWindow();
            shader.SCREEN_SIZE.set((float) window.getWidth(), (float) window.getHeight());
        }
        RenderSystem.setupShaderLights(shader);
    }

    /**
     * restore opengl environment.
     */
    public void releaseStatus(@Nonnull RenderPassPipeline pipeline) {
    }

    /**
     * Retrieves the rendering layer order associated with this render pass.
     * The layer order is used to determine the rendering sequence of different layers.
     *
     * @return the order of the layer as an integer, where lower values typically indicate earlier rendering.
     */
    public int layerOrder() {
        return renderer.getOrderInLayer();
    }

    /**
     * Retrieves the vertex sorting configuration for the current rendering pass.
     * The vertex sorting defines the order in which vertices are rendered,
     * which can influence visual effects and rendering performance.
     *
     * @return the VertexSorting configuration, or null if no sorting is defined.
     */
    public @Nullable VertexSorting getSorting() {
        return renderer.getVertexSortingMode().getVertexSorting();
    }

    /**
     * The batching key: two passes merge iff same {@code mode} + {@code format} and equal <b>effective</b>
     * renderer values ({@link RendererSetting.Runtime#effectiveEquals}). Hand-written (was lombok over the
     * config {@code RendererSetting}) so the key reflects the runtime's slot-or-config values. Concrete
     * passes further gate on their own type via {@code o instanceof RenderPass && super.equals}.
     */
    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof PhotonFXRenderPass that)) return false;
        return mode == that.mode && format.equals(that.format) && renderer.effectiveEquals(that.renderer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(renderer.effectiveHashCode(), mode, format);
    }
}
